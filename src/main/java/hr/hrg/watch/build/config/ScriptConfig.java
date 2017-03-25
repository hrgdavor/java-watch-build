package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

public class ScriptConfig{
	public String input;
	public String[] params;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	public boolean sendChanges = true;

	public boolean initRun = true; 
	public boolean initSendAll = true;
	public String initLine = null;
}