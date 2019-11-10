package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("type")
public class ScriptConfig{
	public String[] params;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	public boolean sendChanges = true;

	public boolean initRun = true; 
	public boolean initSendAll = true;
	public String initLine = null;
}