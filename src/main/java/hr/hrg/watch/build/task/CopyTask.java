package hr.hrg.watch.build.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;

class CopyTask extends AbstractTask<CopyConfig> implements Runnable {

	private GlobWatcher watcher;
	protected Path toPath;

	public CopyTask(CopyConfig config, WatchBuild core) {
		super(config,core);		
	}
	
	public void init(boolean watch) {
		File f = core.getBasePath().resolve(config.input).toFile();
		if(!f.exists()) throw new RuntimeException("Folder do not exist "+config.input+" "+f.getAbsolutePath());
		
		watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
		
		watcher.includes(config.include);
		watcher.excludes(config.exclude);
		toPath = core.getOutputRoot().resolve(config.output);

		this.watcher.init(watch);
		Collection<Path> files = watcher.getMatchedFiles();
		for (Path file : files) {
			Path relative = watcher.relativize(file);
			
			String newName = config.rename.get(relative.toString());
			if(newName != null) relative = Paths.get(newName);
			
			Path toFile = toPath.resolve(relative);
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
					if(!path.isAbsolute())
						path = entry.getMatcher().getRootPath().resolve(path);

					if(path.toFile().isDirectory()) continue;
					
					if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("changed:"+entry+" "+path.toFile().lastModified());
					
					Path relative = watcher.relativize(path);
					String newName = config.rename.get(relative.toString());

					if(newName != null) relative = Paths.get(newName);
					
					Path toFile = toPath.resolve(relative);
					System.err.println("Copy from "+path);
					System.err.println("Copy to   "+toFile);
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
			if((initial && hr.hrg.javawatcher.Main.isInfoEnabled()) || (!initial && hr.hrg.javawatcher.Main.isWarnEnabled())) 
				hr.hrg.javawatcher.Main.logInfo("copy:\t  "+from+"\t TO "+to+" "+fromFile.lastModified());
			return true;
		}else{
			if(shouldCopy) {
				if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("skip identical: "+to);
				if(config.reverseSyncModified && config.compareBytes && fromFile.lastModified() > toFile.lastModified()) {
					fromFile.setLastModified(toFile.lastModified());
				}
			}
			return false;
		}
	}
	
	@Override
	public String toString() {
		return "Copy:"+config.input+" to "+config.output;
	}
}