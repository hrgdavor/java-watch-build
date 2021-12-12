package hr.hrg.watch.build.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import hr.hrg.watch.build.FileDef;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;
import io.methvin.watcher.DirectoryChangeEvent.EventType;

public class CopyTask2 extends AbstractTask<CopyConfig>{

	protected Path toPath;

	boolean isSingleFile;
	boolean didExist;
	protected Path fromPath;
	
	public CopyTask2(CopyConfig config, WatchBuild core){
		super(config, core, config.input, true);
		
		this.config = config;
		this.core = core;
		fromPath = rootPath;
		File f = rootPath.toFile();
		didExist = f.exists();
		
		toPath = core.getOutputRoot().resolve(config.output);
		
		// copy single file, with possible rename
		if(f.exists() && f.isDirectory()) {
			includes(config.include);
			excludes(config.exclude);
		} else {
			isSingleFile = true;
			rootPath = rootPath.getParent();
			File f2 = toPath.toFile();
			if(f2.isDirectory()) toPath = new File(f2,f.getName()).toPath();
		}

		core.addWatcherTask(this);
	}
	
	@Override
	public void fileEvent(FileDef def, boolean initial){
		Path path = def.path;
		if(isSingleFile) {
			if(path.equals(fromPath)) copyFile(fromPath, toPath, initial);
		}else
			super.fileEvent(def, initial);
	}

	@Override
	protected void matched(FileDef def, Path relative, boolean initial) {
		String newName = config.rename.get(relative.toString());
		
		if(newName != null) {
			relative = Paths.get(newName);
		}

		Path toFile = toPath.resolve(relative);
		copyFile(def.path, toFile, false);		
	}

	protected boolean copyFile(Path from, Path to, boolean initial){
		File fromFile = from.toFile();
		File toFile = to.toFile();
		long lastModifiedTo = toFile.lastModified();
		boolean shouldCopy = 
				   !toFile.exists() 
				|| fromFile.lastModified() > lastModifiedTo
				|| fromFile.length() != toFile.length();

		byte[] newBytes = null;;

		if(shouldCopy){
			try {
				newBytes = Files.readAllBytes(from);
			} catch (IOException e1){
				hr.hrg.javawatcher.Main.logError(from, e1.getMessage(), e1);
				return false; // something was wrong while reading
			}
		}

		if(shouldCopy && TaskUtils.writeFile(to, newBytes, config.compareBytes, fromFile.lastModified())){
			core.registerSimpleOutputOnce(this,"copy", from, to);
			core.logSimpleOutput(to, fromFile.lastModified());
			return true;
		}else{
			if(shouldCopy) {
				core.logSkipIdentical(id,to);
				if(config.reverseSyncModified && config.compareBytes && fromFile.lastModified() > lastModifiedTo) {
					core.logUpdateSourceTimestamp(this,fromFile, lastModifiedTo);
					fromFile.setLastModified(lastModifiedTo);
				}
			}
			return false;
		}
	}
	
	@Override
	public void start(boolean watch) {
		// TODO Auto-generated method stub
		super.start(watch);
		File file = fromPath.toFile();
//		if(!file.exists()) throw new RuntimeException("Path does not exist "+config.input+" "+fromPath.toAbsolutePath());
//		if(file.isDirectory() && ! didExist) throw new RuntimeException("Folder did not exist during init"+config.input+" "+fromPath.toAbsolutePath());
		
	}

	@Override
	public void init(boolean watch) {
	}

	@Override
	public String toString() {
		return "Copy:"+config.input+" to "+config.output;
	}

}
