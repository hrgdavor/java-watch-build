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
import hr.hrg.watch.build.Main;
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
		Path inputRoot = core.getBasePath().resolve(config.input);
		toFolderPath = core.getOutputRoot().resolve(config.output);
		relativeToFolderPath = core.getOutputRoot().resolve(config.srcRoot != null ? config.srcRoot:config.output);
		
		watcher = new GlobWatcher(inputRoot, true);
		File f = inputRoot.toFile();
		if(!f.exists()) throw new RuntimeException("Input folder does not exist "+inputRoot+" : "+f.getAbsolutePath());
		
		watcher.includes(config.include);
		watcher.excludes(config.exclude);

		watcher.init(willWatch);

		String[] params = new String[config.params.length+1];
		System.arraycopy(config.params, 0, params, 1, config.params.length);
		params[0] = config.cmd;
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
			
			Collection<Path> files = watcher.getMatchedFiles(); 

			String line = null;
			procOut.println(core.getMapper().writeValueAsString(config.options));
			line = procIn.readLine(); // ignore for now, later could be used as tool options for us to consider
			
			for (Path path : files){
				if(line == null) break;
				line = sendPath(path, true);
			}
			
			if(!willWatch){
				closeProc();
				watcher.close();
			}

		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(),e);
		}
		
	}

	private String sendPath(Path path, boolean initial) throws IOException {
		Path toPath = toFolderPath.resolve(watcher.relativize(path));
		String str = path.toFile().getAbsolutePath()+"\t"+toPath.toFile().getAbsolutePath()+"\t"+relativeToFolderPath.relativize(path)+"\t"+initial;
		if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("changed: "+str);
		procOut.println(str);
		procOut.flush();
		return procIn.readLine();
	}

	private void closeProc() {
		try {
			if(proc != null) {					
				proc.destroy();
				proc.getInputStream().close();
				proc.getOutputStream().close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
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
			watcher.close();
			closeProc();
		}
	}
	
	@Override
	public String toString() {
		return "Ext:"+config.cmd+" "+WatchUtil.join(" ", config.params)+": "+config.input+" to "+config.output;
	}
}