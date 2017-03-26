package hr.hrg.watch.build.task;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.JsCompConfig;

public class JsCompTaskFactory extends AbstractTaskFactory{
	
	@Inject
	public JsCompTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		JsCompConfig config = mapper.convertValue(root, JsCompConfig.class);
		
		LangTask langTask = (LangTask) core.getTask(lang);
		if(langTask == null) {
			throw new ConfigException("language task for "+lang+" not found. You must start a task for "+lang+" first",null);
		}
		
		JsCompTask task = new JsCompTask(core.getOutputRoot(), config, langTask, core.getBurstDelay());
		
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"JsComp:"+config.input+" to "+config.output));
	}

	@Override
	public String getDefaultOptionParser() {
		return "YamlPerLanguage";
	}	
}