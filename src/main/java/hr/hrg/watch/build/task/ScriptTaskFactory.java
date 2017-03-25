package hr.hrg.watch.build.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.javawatcher.Main;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;
import hr.hrg.watch.build.config.ScriptConfig;

public class ScriptTaskFactory extends AbstractTaskFactory{

	Logger log = LoggerFactory.getLogger(ScriptTaskFactory.class);
		
	@Inject
	public ScriptTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		ScriptConfig config = mapper.convertValue(root, ScriptConfig.class);
		 
		Task task = new Task(config, inlineParam);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"Script:"+inlineParam+" watching "+config.initLine));

	}
	
	class Task implements Runnable {

		private GlobWatcher watcher;
		private ScriptConfig config;
		private String command;


		public Task(ScriptConfig config, String command) {
			this.config = config;
			this.command = command;
			File f = new File(config.input);
			if(!f.exists()) throw new RuntimeException("Folder does not exist "+config.input+" "+f.getAbsolutePath());
			
			watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);
		}
		
		public void start(boolean watch){
			this.watcher.init(watch);
			if(config.initRun) {
				Collection<Path> changed = config.initSendAll ? watcher.getMatchedFiles() : new ArrayList<>();
				try {
					Main.runScript(log, command, config.params, changed, config.sendChanges, System.out, System.err);
				} catch (Exception e) {
					log.error("Error running script "+command, e);
				}
			}
		}
	
		public void run(){
			try { 
				while(!Thread.interrupted()){
					Collection<Path> changed = watcher.takeBatchFilesUnique(core.getBurstDelay());
					if(changed == null) break; // interrupted
					
					try {
						Main.runScript(log, command, config.params, changed, config.sendChanges, System.out, System.err);
					} catch (Exception e) {
						log.error("Error running script "+command, e);
					}
				}
			} finally {
				watcher.close();
			}
		}		
	}

}
