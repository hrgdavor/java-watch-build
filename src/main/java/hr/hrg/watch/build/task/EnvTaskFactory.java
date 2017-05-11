package hr.hrg.watch.build.task;

import java.util.List;

import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.VarMap;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.option.OptionException;

public class EnvTaskFactory implements TaskFactory{

	private WatchBuild core;
	
	public EnvTaskFactory(WatchBuild core) {
		this.core = core;
	}
	
	@Override
	public String getDefaultOptionParser() {
		return "lines";
	}

	@Override
	public void start(String inlineParam,List<Object> config, boolean watch) {
		List<?> list = TaskUtils.checkOption(config, 0, List.class);
		if(list.size() == 0) return;
		
		if(!(list.get(0)instanceof String)) throw new OptionException(0,"List of strings expected");
		VarMap vars = core.getVars();
		for(Object obj:list){
			String str = (String) obj;
			if(TaskUtils.emptyOrcomment(str)) continue;
			int idx = str.indexOf("->");
			String name = str;
			String varName = str;
			if(idx != -1){
				name = str.substring(0,idx).trim();
				varName = str.substring(idx+2).trim();
				if(varName.charAt(0) == '"' && varName.charAt(varName.length()-1) == '"') varName = varName.substring(1,varName.length()-1);
			}
			String envValue = System.getenv(name);
			if(envValue != null)
				addVar(vars,varName, envValue);
		}
	}

	private void addVar(VarMap vars, String name, String value) {
		vars.put(name, vars.expand(value));
	}
	
	@Override
	public boolean alwaysRun() {
		return true;
	}
}
