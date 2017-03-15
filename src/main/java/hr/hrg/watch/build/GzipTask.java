package hr.hrg.watch.build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;

public class GzipTask implements Runnable{

	Logger log = LoggerFactory.getLogger(GzipTask.class);
	
	protected GlobWatcher fromGlob;
	protected Path toPath;
	private long burstDelay = 50;

	public static final int BUFFER_SIZE = 4096;

	public GzipTask(GlobWatcher from, Path to){
		this.fromGlob = from;
		this.toPath = to;
	}
	
	public void start(boolean watch){
		this.fromGlob.init(watch);
		Collection<Path> files = fromGlob.getMatchedFiles();
		for (Path file : files) {
			Path toFile = toPath.resolve(fromGlob.relativize(file)).resolveSibling(file.getFileName()+".gz");
			compressFile(file, toFile);
		}
	}

	public void run(){
		while(!Thread.interrupted()){
			Collection<FileChangeEntry<FileMatchGlob>> changes = fromGlob.takeBatch(burstDelay);
			if(changes == null) break; // interrupted
			
			for (FileChangeEntry<FileMatchGlob> entry : changes){
				Path path = entry.getPath();
				
				if(path.toFile().isDirectory()) continue;
				
				log.info("changed:"+entry+" "+path.toFile().lastModified());
				
				Path toFile = toPath.resolve(fromGlob.relativize(path));
				compressFile(path, toFile);
			}
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
