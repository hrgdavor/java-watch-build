package hr.hrg.watch.build;

import java.io.File;
import java.nio.file.Paths;

import hr.hrg.javawatcher.GlobWatcher;

class GzipRunner implements Runnable{
	private GzipTask task;

	public GzipRunner(GzipConfig.Item config, File outputRoot){

		File f = new File(config.input);
		if(!f.exists()) throw new RuntimeException("Folder does not exist "+config.input);
		
		if(config.output == null) config.output = config.input;
		GlobWatcher watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
		
		watcher.includes(config.include);
		watcher.excludes(config.exclude);

		task = new GzipTask(watcher, Paths.get(new File(outputRoot,config.output).getPath()));
	}
	
	public void start(boolean watch){
		task.start(watch);		
	}

	@Override
	public void run() {
		task.run();
	}
}