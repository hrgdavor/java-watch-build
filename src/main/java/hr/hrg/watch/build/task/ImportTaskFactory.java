package hr.hrg.watch.build.task;

import java.nio.file.Paths;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.VarMap;
import hr.hrg.watch.build.option.OptionException;

public class ImportTaskFactory implements TaskFactory{
	
	private WatchBuild core;

	@Inject
	public ImportTaskFactory(WatchBuild core){
		this.core = core;
	}
	
	@Override
	public String getDefaultOptionParser() {
		return "lines";
	}

	@Override
	public void start( String inlineParam, List<Object> config, boolean watch){
		
		if(inlineParam != null && !TaskUtils.emptyOrcomment(inlineParam)) core.loadFile(Paths.get(inlineParam));
		
		if(config.get(0) == null) return;
		List list = TaskUtils.checkOption(config, 0, List.class);
		if(list.size() == 0) return;		
		
		if(!(list.get(0)instanceof String)) throw new OptionException(0,"List of strings expected");
		VarMap vars = core.getVars();
		for(Object obj:list){
			String str = (String) obj;
			if(TaskUtils.emptyOrcomment(str)) continue;
			core.loadFile(Paths.get(str));
		}
	}
	
	@Override
	public boolean alwaysRun() {
		return true;
	}
}