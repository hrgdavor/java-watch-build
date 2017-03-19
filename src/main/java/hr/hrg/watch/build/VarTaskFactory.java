package hr.hrg.watch.build;

import java.util.List;

public class VarTaskFactory implements TaskFactory{

	boolean override;
	private Main core;
	
	public VarTaskFactory(Main core, boolean override) {
		this.core = core;
		this.override = override;
	}
	
	@Override
	public String getDefaultOptionParser() {
		return "lines";
	}

	@Override
	public void start(String inlineParam,List<Object> config, boolean watch) {
		List list = TaskUtils.checkOption(config, 0, List.class);
		if(list.size() == 0) return;
		
		if(!(list.get(0)instanceof String)) throw new OptionException(0,"List of strings expected");
		VarMap vars = core.getVars();
		for(Object obj:list){
			String str = (String) obj;
			if(TaskUtils.emptyOrcomment(str)) continue;
			int idx = str.indexOf(':');
			if(idx != -1){
				String name = str.substring(0,idx).trim();
				String value = str.substring(idx+1).trim();
				if(value.charAt(0) == '"' && value.charAt(value.length()-1) == '"') value = value.substring(1,value.length()-1);
				addVar(vars,name,value);
			}
		}
	}

	private void addVar(VarMap vars, String name, String value) {

		if(override || !vars.containsKey(name))
			vars.put(name, vars.expand(value));
	}
	
	@Override
	public boolean alwaysRun() {
		return true;
	}
}
