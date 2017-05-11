package hr.hrg.watch.build.config;

public class ConfigException extends RuntimeException {
	
	private static final long serialVersionUID = 2624348181797788190L;

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
