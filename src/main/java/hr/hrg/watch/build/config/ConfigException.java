package hr.hrg.watch.build.config;

import hr.hrg.watch.build.WatchBuild;;

public class ConfigException extends RuntimeException {
	
	boolean withConfigInfo;
	private ConfDef def;
	
	public ConfigException(String message, Throwable cause){
		super(message,cause);
	}

	public ConfigException(ConfDef def, String message, Throwable cause){
		super(message, cause);
		this.def = def;
		withConfigInfo = true;
	}
	
	public ConfDef getDef() {
		return def;
	}
	
	public String getInfoNl() {
		return def.confFile.toAbsolutePath()+":"+def.lineNumber+"\n"+(def instanceof  TaskOption? "@@":"@")+def.type;
	}
	
	public boolean isWithConfigInfo() {
		return withConfigInfo;
	}
}
