package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class HtmlScriptAndCssConfig extends StepConfig{
	public String input;
	public String output;
	public boolean compareBytes = true; 
	public String scriptReplace = "<!--SCRIPT-FILES-->";
	public String cssReplace = "<!--CSS-FILES-->";
	public List<String> scripts = new ArrayList<>(); 
	public List<String> css = new ArrayList<>();
	public long burstDelay = 50;

}