package hr.hrg.watch.build;

import java.io.File;
import java.nio.file.Paths;

import hr.hrg.javawatcher.GlobWatcher;

class CopyRunner implements Runnable{
	private CopyTask task;

	public CopyRunner(CopyConfig.Item config, File outputRoot){

		File f = new File(config.input);
		int i=0;
		while(!f.exists() && i<config.altFolder.size()){
			f = new File(config.altFolder.get(i));
			i++;
		}
		if(!f.exists()) throw new RuntimeException("Folder and alternatives do not exist "+config.input+" "+f.getAbsolutePath());
		
		GlobWatcher watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
		
		watcher.includes(config.include);
		watcher.excludes(config.exclude);

		task = new CopyTask(watcher, Paths.get(new File(outputRoot,config.output).getPath()));
		task.setCompareBytes(config.compareBytes);
	}
	
	public void start(boolean watch){
		task.start(watch);		
	}

	@Override
	public void run() {
		task.run();
	}
}