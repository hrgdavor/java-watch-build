package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ExtConfig {
	public String type="Ext";

	public String cmd;
	public String[] params = {};
	
	public String input = "./";
	public String output = "./";

	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	
	public ObjectNode options;
	public String srcRoot;
	
}