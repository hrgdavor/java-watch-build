package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class HtmlScriptAndCssConfig{
	public String type="HtmlScriptAndCss";

	public String input;
	public String output;
	
	public boolean compareBytes = true; 
	public String scriptReplace = "<!--SCRIPT-FILES-->";
	public String scriptVariable = null;
	
	public String globalsReplace = "<!--GLOBAL-VARIABLES-->";
	public String cssReplace = "<!--CSS-FILES-->";
	public String lastModReplace = "<!--SCRIPT-LAST_MOD-->";

	public List<String> scripts = new ArrayList<>(); 
	public List<String> css = new ArrayList<>();
	public ObjectNode globals = null;

}