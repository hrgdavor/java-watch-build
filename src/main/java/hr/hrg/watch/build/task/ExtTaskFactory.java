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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.javawatcher.WatchUtil;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.ExtConfig;
import hr.hrg.watch.build.config.TaskDef;

public class ExtTaskFactory extends AbstractTaskFactory {


	private static final Logger log = LoggerFactory.getLogger(ExtTaskFactory.class);
	
	public ExtTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(TaskDef taskDef, String lang, JsonNode root, boolean watch) {
		if(!WatchUtil.classAvailable("hr.hrg.watchsass.Compiler")) {
			throw new ConfigException("Sass compiling task is not avaiable due to missing dependecy hr.hrg:java-watch-sass (download full shaded version to fix or remove the @sass task)",null);
		}

		ExtConfig config = mapper.convertValue(root, ExtConfig.class);
		if(config.options == null) config.options = mapper.createObjectNode();
		
		config.options.put("verbose", Main.VERBOSE);
		
		ExtTask task = new ExtTask(core, mapper, config);
		task.start(watch);

		if(watch)
			core.addThread(new Thread(task,"Ext:"+config.cmd+" "+WatchUtil.join(" ", config.params)+": "+config.input+" to "+config.output));
		
	}
	
	public static class ExtTask implements Runnable{

		
		private final Logger log = LoggerFactory.getLogger(ExtTaskFactory.ExtTask.class);
		
		private WatchBuild core;
		private ObjectMapper jsonMapper;
		protected GlobWatcher watcher;
		private ExtConfig config;
		private Process proc;
		private Path toFolderPath;
		private PrintWriter procOut;
		private BufferedReader procIn;
		private long burstDelay;
		private Path relativeToFolderPath;

		public ExtTask(WatchBuild core, ObjectMapper mapper, ExtConfig config) {
			this.core = core;
			this.jsonMapper = mapper;
			this.config = config;
			this.burstDelay = core.getBurstDelay();
			
			Path inputRoot = core.getBasePath().resolve(config.input);
			toFolderPath = core.getOutputRoot().resolve(config.output);
			relativeToFolderPath = core.getOutputRoot().resolve(config.relative != null ? config.relative:config.input);
			
			watcher = new GlobWatcher(inputRoot, true);
			File f = inputRoot.toFile();
			if(!f.exists()) throw new RuntimeException("Input folder does not exist "+inputRoot+" : "+f.getAbsolutePath());
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);

			
			
		}
		
		private void start(boolean willWatch) {
			watcher.init(willWatch);
	
			String[] params = new String[config.params.length+1];
			System.arraycopy(config.params, 0, params, 1, config.params.length);
			params[0] = config.cmd;
			if(WatchUtil.isLinux()){
				params = new String[]{ "/bin/sh","-c", WatchUtil.join(" ", params)};
			}
			try {
				if(Main.VERBOSE > 1) log.info("params: " +WatchUtil.join(" ", params));
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
				procOut.println(jsonMapper.writeValueAsString(config.options));
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
			if(Main.VERBOSE > 1) log.info("changed: "+str);
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
						String line = sendPath(fileChanged, false);
					}
				}			
			} catch (IOException e) {
				e.printStackTrace();
			}finally {
				watcher.close();
				closeProc();
			}
		}
	
	}

	static class StreamGlobber implements Runnable{
//		StringBuffer b = new StringBuffer();
		private InputStream in;
		
		public StreamGlobber(InputStream in) {
			this.in = in;
		}
		
		@Override
		public void run() {
			try(	InputStreamReader inr = new InputStreamReader(in);
					BufferedReader br = new BufferedReader(inr)
					){
				String line = null;
				while((line = br.readLine()) != null){
					//b.append(line);
					if(line.startsWith("INFO "))
						log.info(line.substring(5));
					else
						System.err.println(line);
				}
				in.close();
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
