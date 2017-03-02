package hr.hrg.watch.build;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import hr.hrg.javawatcher.GlobWatcher;

class CompRunner implements Runnable{
	private CompConfig.Item config;
	private File outputRoot;
	private CompTask task;

	public CompRunner(CompConfig.Item config, File outputRoot, LangTask langTask){
		this.config = config;
		this.outputRoot = outputRoot;

		File f = new File(config.input);
		int i=0;
		while(!f.exists() && i<config.altFolder.size()){
			f = new File(this.config.altFolder.get(i));
			i++;
		}
		if(!f.exists()) throw new RuntimeException("Folder and alternatives do not exist "+config.input+" "+f.getAbsolutePath());

		GlobWatcher watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
		
		List<String> includeWithHtml = new ArrayList<>(config.include); 
		
		for(String pattern : config.include){
			if(pattern.endsWith(".js")){
				String pHtml = pattern.substring(0, pattern.length()-3)+".html";
				if(!includeWithHtml.contains(pHtml)) includeWithHtml.add(pHtml);
			}
		}
		
		watcher.includes(includeWithHtml);
		watcher.excludes(config.exclude);

		task = new CompTask(watcher, Paths.get(new File(this.outputRoot,config.output).getPath()), langTask);
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