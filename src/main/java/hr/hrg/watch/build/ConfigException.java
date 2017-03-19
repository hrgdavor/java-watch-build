package hr.hrg.watch.build;

import hr.hrg.watch.build.Main.ConfDef;
import hr.hrg.watch.build.Main.TaskOption;;

public class ConfigException extends RuntimeException {
	
	boolean withConfigInfo;
	
	public ConfigException(String message, Throwable cause){
		super(message,cause);
	}

	public ConfigException(ConfDef def, String message, Throwable cause){
		super(def.confFile.toAbsolutePath()+":"+def.lineNumber+(def instanceof  TaskOption? "@@":"@")+def.type+" "+message, cause);
		withConfigInfo = true;
	}
	
	public boolean isWithConfigInfo() {
		return withConfigInfo;
	}
}
