package hr.hrg.watch.build.task;

import java.nio.file.Path;
import java.util.List;

import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.TaskDef;
import hr.hrg.watch.build.option.OptionException;

public class ImportTaskFactory implements TaskFactory{
	
	private WatchBuild core;

	public ImportTaskFactory(WatchBuild core){
		this.core = core;
	}
	
	@Override
	public String getDefaultOptionParser() {
		return "lines";
	}

	@Override
	public void start( TaskDef taskDef, List<Object> config, boolean watch){
		
		String inlineParam = taskDef.params;
		
		if(inlineParam != null && !TaskUtils.emptyOrcomment(inlineParam)) {
			core.loadFile(paramToPath(inlineParam, taskDef.confFile));
		}
		
		if(config.get(0) == null) return;
		List<?> list = TaskUtils.checkOption(config, 0, List.class);
		if(list.size() == 0) return;		
		
		if(!(list.get(0)instanceof String)) throw new OptionException(0,"List of strings expected");
		for(Object obj:list){
			String str = (String) obj;
			
			if(TaskUtils.emptyOrcomment(str)) continue;
						
			core.loadFile(paramToPath(str, taskDef.confFile));
		}
	}

	Path paramToPath(String str, Path parentScript){

		String expanded = core.getVars().expand(str);
		
		boolean relativeToScript = expanded.startsWith("../") || expanded.startsWith("./");
		
		Path root = relativeToScript ? parentScript.getParent() : core.getBasePath();
		
		return root.resolve(expanded);
	}
	
	@Override
	public boolean alwaysRun() {
		return true;
	}
}
