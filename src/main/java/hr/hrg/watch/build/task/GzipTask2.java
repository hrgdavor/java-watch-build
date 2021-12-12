package hr.hrg.watch.build.task;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import hr.hrg.watch.build.FileDef;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.GzipConfig;
import io.methvin.watcher.DirectoryChangeEvent.EventType;

public class GzipTask2 extends AbstractTask<GzipConfig>{

	protected Path toPath;

	boolean isSingleFile;
	protected Path fromPath;
	
	public GzipTask2(GzipConfig config, WatchBuild core){
		super(config, core, config.input, true);
		
		this.config = config;
		if(config.output == null) config.output = config.input;
		this.core = core;
		File f = rootPath.toFile();
		if(!f.exists()) throw new RuntimeException("Path does not exist "+config.input+" "+f.getAbsolutePath());

		toPath = core.getOutputRoot().resolve(config.output);
		
		// copy single file, with possible rename
		if(f.isFile()) {
			isSingleFile = true;
			fromPath = rootPath;
			rootPath = rootPath.getParent();
			File f2 = toPath.toFile();
			if(f2.isDirectory()) toPath = new File(f2,f.getName()).toPath();
		}else {
			includes(config.include);
			excludes(config.exclude);
		}
		
		core.addWatcherTask(this);
	}
	
	@Override
	public void fileEvent(FileDef def, boolean initial){
		Path path = def.path;
		if(isSingleFile) {
			if(path.equals(fromPath)) gzipFile(fromPath, toPath, initial);
		}else
			super.fileEvent(def, initial);
	}

	@Override
	protected void matched(FileDef def,  Path relative, boolean initial) {
		Path toFile = toPath.resolve(relative);
		gzipFile(def.path, gzPath(toFile), false);		
	}

	private Path gzPath(Path path){
		return path.resolveSibling(path.getFileName().toString()+".gz");
	}

	protected boolean gzipFile(Path from, Path to, boolean initial){

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
			
			core.registerSimpleOutputOnce(this,"gzip", from, to);
			core.logSimpleOutput(to, fromFile.lastModified());
			toFile.setLastModified(fromFile.lastModified());
			return true;
		}else{
			core.logSkipOlder(id,to);
			return false;
		}
	}

	
	@Override
	public void init(boolean watch) {
		
	}

	@Override
	public String toString() {
		return "Gzip:"+config.input+" to "+config.output;
	}

}
