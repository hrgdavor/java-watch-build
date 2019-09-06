package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import wrm.libsass.SassCompiler.InputSyntax;
import wrm.libsass.SassCompiler.OutputStyle;

public class ExtConfig {

	public String input;
	public String output;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	
	public String cmd;
	public String[] params = {};
	
	public ObjectNode options;
	public String relative;
	
}