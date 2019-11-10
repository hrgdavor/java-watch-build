package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.HtmlScriptAndCssConfig;

public class HtmlScriptAndCssTaskFactory extends AbstractTaskFactory<HtmlScriptAndCssTask, HtmlScriptAndCssConfig>{

	
	public HtmlScriptAndCssTaskFactory(WatchBuild core){
		super(core, new HtmlScriptAndCssConfig());
	}
	
	public HtmlScriptAndCssTaskFactory(WatchBuild core, String input, String output) {
		super(core, new HtmlScriptAndCssConfig());
		config.input = input;
		config.output = output;
	}	
	
	@Override
	public HtmlScriptAndCssTask build() {
		return new HtmlScriptAndCssTask(config, core);
	}
	
	public HtmlScriptAndCssTaskFactory compareBytes(boolean val) { config.compareBytes = val; return this; }
	public HtmlScriptAndCssTaskFactory compareBytes() { config.compareBytes = true; return this; }
	
	public HtmlScriptAndCssTaskFactory scriptReplace(String val) { config.scriptReplace = val; return this; }
	public HtmlScriptAndCssTaskFactory scriptVariable(String val) { config.scriptVariable = val; return this; }
	public HtmlScriptAndCssTaskFactory cssReplace(String val) { config.cssReplace = val; return this; }
	public HtmlScriptAndCssTaskFactory lastModReplace(String val) { config.lastModReplace = val; return this; }

	public HtmlScriptAndCssTaskFactory scripts(String ...arr) { addAll(config.scripts, arr); return this; }
	public HtmlScriptAndCssTaskFactory scripts(List<String> list) { config.scripts.addAll(list); return this; }
	
	public HtmlScriptAndCssTaskFactory css(String ...arr) { addAll(config.css, arr); return this; }
	public HtmlScriptAndCssTaskFactory css(List<String> list) { config.css.addAll(list); return this; }
	
}