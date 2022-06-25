package hr.hrg.watch.build.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Collection;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.javawatcher.WatchUtil;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ExtConfig;

public class ExtTask extends AbstractTask<ExtConfig> implements Runnable{
	
	
	protected GlobWatcher watcher;
	private Process proc;
	private Path toFolderPath;
	private PrintWriter procOut;
	private BufferedReader procIn;
	private long burstDelay;
	private Path relativeToFolderPath;

	public ExtTask(WatchBuild core, ExtConfig config) {
		super(config, core);

		this.config = config;
		this.burstDelay = core.getBurstDelay();		
	}
	
	public void init(boolean willWatch) {
		if(!config.runOnly) {
			Path inputRoot = core.getBasePath().resolve(config.input);
			toFolderPath = core.getOutputRoot().resolve(config.output).toAbsolutePath().normalize();
			relativeToFolderPath = core.getOutputRoot().resolve(config.srcRoot != null ? config.srcRoot:config.output);
			relativeToFolderPath = relativeToFolderPath.toAbsolutePath().normalize();
			
			watcher = new GlobWatcher(inputRoot, true);
			File f = inputRoot.toFile();
			if(!f.exists()) throw new RuntimeException("Input folder does not exist "+inputRoot+" : "+f.getAbsolutePath());
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);
			watcher.init(willWatch);
		}

		String[] arr = config.params;
		if(willWatch) {
			if(config.watchParams.length > 0) {
				arr = config.watchParams; 
			}else if(config.watchOption != null && !config.watchOption.isEmpty()){
				arr = new String[arr.length+1];
				System.arraycopy(config.params, 0, arr, 0, config.params.length-1);
				arr[arr.length-1] = config.watchOption;
			}
		}
		String[] params = new String[arr.length+1];
		System.arraycopy(arr, 0, params, 1, arr.length);
		params[0] = config.cmd;
		if(System.getProperty("os.name").toLowerCase().contains("win") && config.winCmd != null && !config.winCmd.isEmpty()) {
			params[0] = config.winCmd;
		}
		if(WatchUtil.isLinux()){
			params = new String[]{ "/bin/sh","-c", WatchUtil.join(" ", params)};
		}
		try {
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("params: " +WatchUtil.join(" ", params));
			proc = Runtime.getRuntime().exec(params);
			
			StreamGlobber glob = new StreamGlobber(proc.getErrorStream());
			new Thread(glob,"ext-"+params[0]+"-err-read").start();
			
			
			OutputStream out = proc.getOutputStream();
			OutputStreamWriter out2 = new OutputStreamWriter(out);
			procOut = new PrintWriter(out2);
			InputStream in = proc.getInputStream();
			InputStreamReader inr = new InputStreamReader(in);
			procIn = new BufferedReader(inr);
			

			if(config.runOnly) {
				if(willWatch) {					
					new Thread(new Runnable() {
						public void run() {
							String line = null;
							try {
								while((line = procIn.readLine())!= null) {
									hr.hrg.javawatcher.Main.logInfo(line);
								}
							} catch (IOException e) {
								hr.hrg.javawatcher.Main.logError("error runnig task "+this, e);
							}
							
						}
					}).start();
				}else {
					String line = null;
					while((line = procIn.readLine())!= null) {
						hr.hrg.javawatcher.Main.logInfo(line);
					}					
				}
			} else {
				Collection<Path> files = watcher.getMatchedFiles(); 
				String line = null;
				procOut.println(core.getMapper().writeValueAsString(config.options));
				line = procIn.readLine(); // ignore for now, later could be used as tool options for us to consider
				System.err.println("INIT "+this);
				for (Path path : files){
					if(line == null) break;
					line = sendPath(path, true);
				}
			}
			
			if(!willWatch){
				closeProc(true);
				watcher.close();
			}

		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(),e);
		}
		
	}

	@Override
	public boolean needsThread() {
		return !config.runOnly;
	}
	
	private String sendPath(Path path, boolean initial) throws IOException {
		path = watcher.getRootPathAbs().resolve(path).normalize();
		if(!path.isAbsolute()) path = path.toAbsolutePath().normalize();
		Path relative = watcher.relativize(path);
		Path toPath = toFolderPath.resolve(relative).toAbsolutePath().normalize();
//		System.err.println("root   "+watcher.getRootPathAbs());
//		System.err.println("to     "+toFolderPath);
//		System.err.println("relTo  "+relativeToFolderPath);
//		System.err.println("path   "+path);
//		System.err.println("toPath "+toPath);
//		System.err.println("rel    "+relative);
//		System.err.println("rel2   "+relativeToFolderPath.relativize(path));
		
		String str = path.toFile()
				+"\t"+toPath.toFile()
				+"\t"+relativeToFolderPath.relativize(path)
				+"\t"+initial;
		if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("changed: "+str);
//		System.err.println("STR: "+str);
		procOut.println(str);
		procOut.flush();
		return procIn.readLine();
	}

	private void closeProc(boolean wait) {
		try {
			if(proc != null) {	
				proc.getInputStream().close();
				proc.getOutputStream().close();
				if(wait) proc.waitFor();
				proc.destroy();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		if(config.runOnly) return;
		try {
			while(!Thread.interrupted()){
				Collection<Path> files = watcher.takeBatchFilesUnique(burstDelay);
				
				if(files == null) break; // interrupted
				for (Path fileChanged : files) {
					sendPath(fileChanged, false);
				}
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(watcher != null) watcher.close();
			closeProc(false);
		}
	}
	
	@Override
	public String toString() {
		return "Ext:"+config.cmd+" "+WatchUtil.join(" ", config.params)+": "+config.input+" to "+config.output;
	}
}