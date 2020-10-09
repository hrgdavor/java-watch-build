package hr.hrg.watch.build.task;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.GzipConfig;

class GzipTask extends AbstractTask<GzipConfig> implements Runnable {

	private GlobWatcher watcher;
	protected Path toPath;

	public GzipTask(GzipConfig config, WatchBuild core) {
		super(config, core);
	}
	
	public void init(boolean watch){
		if(config.output == null) config.output = config.input;

		File f = new File(config.input);
		if(!f.exists()) throw new RuntimeException("Folder does not exist "+config.input);
		
		watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
		
		watcher.includes(config.include);
		watcher.excludes(config.exclude);
		
		toPath = core.getOutputRoot().resolve(config.output);

		this.watcher.init(watch);
		Collection<Path> files = watcher.getMatchedFiles();
		for (Path path : files) {
			compressFile(path, gzPath(path), true);
		}
	}

	private Path gzPath(Path path) {
		return toPath.resolve(watcher.relativize(path)).resolveSibling(path.getFileName().toString()+".gz");
	}

	public void run(){
		try {
			while(!Thread.interrupted()){
				Collection<FileChangeEntry<FileMatchGlob>> changes = watcher.takeBatch(core.getBurstDelay());
				if(changes == null) break; // interrupted
				
				for (FileChangeEntry<FileMatchGlob> entry : changes){
					Path path = entry.getPath();
					if(!path.isAbsolute())
						path = entry.getMatcher().getRootPath().resolve(path);
					
					if(path.toFile().isDirectory()) continue;
					
					if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("changed:"+entry+" "+path.toFile().lastModified());
					
					compressFile(path, gzPath(path), false);
				}
			}
			
		} finally {
			watcher.close();
		}
	}

	
	protected boolean compressFile(Path from, Path to, boolean initial){
		File fromFile = from.toFile();
		File toFile = to.toFile();
		boolean shouldCopy = !toFile.exists() || fromFile.lastModified() > toFile.lastModified();

		if(shouldCopy){
			try {				
				GZIPOutputStream gzo = new GZIPOutputStream(new FileOutputStream(toFile));
				Files.copy(from, gzo);
				gzo.close();
			} catch (Exception e) {
				hr.hrg.javawatcher.Main.logError("ERROR generating gzip ",e);
			}
			
			if((initial && hr.hrg.javawatcher.Main.isInfoEnabled()) || (!initial && hr.hrg.javawatcher.Main.isWarnEnabled())) 
				hr.hrg.javawatcher.Main.logInfo("gzip:\t  "+from+"\t TO "+to+" "+fromFile.lastModified());
			toFile.setLastModified(fromFile.lastModified());
			return true;
		}else{
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo(" skip already generated: "+to);
			return false;
		}
	}

	@Override
	public String toString() {
		return "Gzip:"+config.input+" to "+config.output;
	}
}