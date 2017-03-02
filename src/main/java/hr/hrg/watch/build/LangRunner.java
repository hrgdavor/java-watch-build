package hr.hrg.watch.build;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.FolderWatcher;
import hr.hrg.javawatcher.GlobWatcher;

class LangRunner implements Runnable{
	private LangTask task;

	public LangRunner(LangConfig config, File outputRoot, YAMLMapper yamlMapper, ObjectMapper objectMapper){
		if(outputRoot == null) throw new NullPointerException("outputRoot can not be null");
		File f = new File(config.input);
		if(!f.exists()) throw new RuntimeException("Input file does not exist "+config.input+" "+f.getAbsolutePath());

		GlobWatcher watcher = new GlobWatcher(f.getParentFile().getAbsoluteFile().toPath(), false);
		watcher.includes(f.getName());
		

		this.task = new LangTask(watcher, outputRoot.toPath(), config.output,  yamlMapper, objectMapper, config.varName);
	}

	public void start(boolean watch){
		task.start(watch);		
	}
	
	@Override
	public void run() {
		task.run();
	}
	
	public void addLanguageChangeListener(LanguageChangeListener listener){
		this.task.addLanguageChangeListener(listener);
	}
	
	public LangTask getTask() {
		return task;
	}

}