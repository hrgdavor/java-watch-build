package hr.hrg.watch.build.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;
import hr.hrg.watch.build.config.TaskDef;

public class CopyTaskFactory extends AbstractTaskFactory{

	Logger log = LoggerFactory.getLogger(CopyTaskFactory.class);
		
	public CopyTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(TaskDef taskDef, String lang, JsonNode root, boolean watch) {
		CopyConfig config = mapper.convertValue(root, CopyConfig.class);
		
		Task task = new Task(config);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"Copy:"+config.input+" to "+config.output));

	}
	
	class Task implements Runnable {

		private GlobWatcher watcher;
		protected Path toPath;
		private CopyConfig config;


		public Task(CopyConfig config) {
			this.config = config;
			File f = core.getBasePath().resolve(config.input).toFile();
			int i=0;
			while(!f.exists() && i<config.altFolder.size()){
				
				String alt = config.altFolder.get(i);
				if(alt.startsWith("classpath:")) {
					f = new File(alt);
				}else {					
					f = core.getBasePath().resolve(alt).toFile();
				}
				i++;
			}
			if(!f.exists()) throw new RuntimeException("Folder and alternatives do not exist "+config.input+" "+f.getAbsolutePath());
			
			watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);
			toPath = core.getOutputRoot().resolve(config.output);
		}
		
		public void start(boolean watch){
			this.watcher.init(watch);
			Collection<Path> files = watcher.getMatchedFiles();
			for (Path file : files) {
				Path toFile = toPath.resolve(watcher.relativize(file));
				copyFile(file, toFile, true);
			}
		}
	
		public void run(){
			try {
				while(!Thread.interrupted()){
					Collection<FileChangeEntry<FileMatchGlob>> changes = watcher.takeBatch(core.getBurstDelay());
					if(changes == null) break; // interrupted
					
					for (FileChangeEntry<FileMatchGlob> entry : changes){
						Path path = entry.getPath();
						
						if(path.toFile().isDirectory()) continue;
						
						if(Main.VERBOSE > 1) log.info("changed:"+entry+" "+path.toFile().lastModified());
						
						Path toFile = toPath.resolve(watcher.relativize(path));
						copyFile(path, toFile, false);
					}
				}
				
			} finally {
				watcher.close();
			}
		}
	
		
		protected boolean copyFile(Path from, Path to, boolean initial){
			File fromFile = from.toFile();
			File toFile = to.toFile();
			boolean shouldCopy = 
					   !toFile.exists() 
					|| fromFile.lastModified() > toFile.lastModified()
					|| fromFile.length() != toFile.length();
	
			byte[] newBytes = null;;
	
			if(shouldCopy){
				try {
					newBytes = Files.readAllBytes(from);
				} catch (IOException e1) {
					e1.printStackTrace();
					return false; // something was wrong while reading
				}
			}
	
			if(shouldCopy && TaskUtils.writeFile(to, newBytes, config.compareBytes, fromFile.lastModified())){
				// initially log only iv VERBOSE 2+ ... on change print if VERBOSE 1+
				if((initial && Main.VERBOSE > 1) || (!initial && Main.VERBOSE > 0)) 
					log.info("copy:\t  "+from+"\t TO "+to+" "+fromFile.lastModified());
				return true;
			}else{
				if(shouldCopy) {
					if(Main.VERBOSE > 1) log.info("skip identical: "+to);
					if(config.reverseSyncModified && config.compareBytes && fromFile.lastModified() > toFile.lastModified()) {
						fromFile.setLastModified(toFile.lastModified());
					}
				}
				return false;
			}
		}
	}

}
