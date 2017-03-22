package hr.hrg.watch.build.task;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.GzipConfig;

public class GzipTaskFactory extends AbstractTaskFactory{

	Logger log = LoggerFactory.getLogger(GzipTaskFactory.class);
	
	@Inject
	public GzipTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		GzipConfig config = mapper.convertValue(root, GzipConfig.class);

		Task task = new Task(config);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"Gzip:"+config.input+" to "+config.output));

	}
	
	class Task implements Runnable {

		private GlobWatcher watcher;
		protected Path toPath;
		private GzipConfig config;


		public Task(GzipConfig config) {
			this.config = config;
			if(config.output == null) config.output = config.input;

			File f = new File(config.input);
			if(!f.exists()) throw new RuntimeException("Folder does not exist "+config.input);
			
			watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);
			
			toPath = core.getOutputRoot().resolve(config.output);
		}
		
		public void start(boolean watch){
			this.watcher.init(watch);
			Collection<Path> files = watcher.getMatchedFiles();
			for (Path file : files) {
				Path toFile = toPath.resolve(watcher.relativize(file)).resolveSibling(file.getFileName()+".gz");
				compressFile(file, toFile);
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
						
						log.info("changed:"+entry+" "+path.toFile().lastModified());
						
						Path toFile = toPath.resolve(watcher.relativize(path));
						compressFile(path, toFile);
					}
				}
				
			} finally {
				watcher.close();
			}
		}
	
		
		protected boolean compressFile(Path from, Path to){
			File fromFile = from.toFile();
			File toFile = to.toFile();
			boolean shouldCopy = !toFile.exists() || fromFile.lastModified() > toFile.lastModified();
	
			if(shouldCopy){
				try {				
					GZIPOutputStream gzo = new GZIPOutputStream(new FileOutputStream(toFile));
					Files.copy(from, gzo);
					gzo.close();
				} catch (Exception e) {
					log.error("ERROR generating gzip ",e);
				}
				log.info("gzip:\t  "+from+"\t TO "+to+" "+fromFile.lastModified());		
				return true;
			}else{
				log.trace("skip already generated: "+to);		
				return false;
			}
		}
	}
}
