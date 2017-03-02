package hr.hrg.watch.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;

public class CopyTask implements Runnable{

	Logger log = LoggerFactory.getLogger(CopyTask.class);
	
	protected GlobWatcher fromGlob;
	protected Path toPath;
	protected boolean compareBytes = true;
	private long burstDelay = 50;

	public static final int BUFFER_SIZE = 4096;

	public CopyTask(GlobWatcher from, Path to){
		this.fromGlob = from;
		this.toPath = to;
	}
	
	public void start(boolean watch){
		this.fromGlob.init(watch);
		Collection<Path> files = fromGlob.getMatchedFiles();
		for (Path file : files) {
			Path toFile = toPath.resolve(fromGlob.relativize(file));
			copyFile(file, toFile);
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
				copyFile(path, toFile);
			}
		}
	}

	
	protected boolean copyFile(Path from, Path to){
		File fromFile = from.toFile();
		File toFile = to.toFile();
		boolean shouldCopy = !toFile.exists() || fromFile.lastModified() > toFile.lastModified();

		byte[] newBytes = null;;

		if(shouldCopy){
			try {
				newBytes = Files.readAllBytes(from);
			} catch (IOException e1) {
				e1.printStackTrace();
				return false; // something was wrong while reading
			}			
		}

		if(shouldCopy && TaskUtils.writeFile(to, newBytes, compareBytes)){			
			log.info("copy:\t  "+from+"\t TO "+to+" "+fromFile.lastModified());
			return true;
		}else{
			log.trace("skip identical: "+to);		
			return false;
		}
	}
	
	public void setCompareBytes(boolean compareBytes) {
		this.compareBytes = compareBytes;
	}
	
	public boolean isCompareBytes() {
		return compareBytes;
	}

}
