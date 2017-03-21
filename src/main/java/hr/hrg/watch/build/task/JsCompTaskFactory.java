package hr.hrg.watch.build.task;

import javax.inject.Inject;
import javax.inject.Singleton;

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
	public void startOne(String inlineParam, JsonNode root, boolean watch) {
		JsCompConfig config = mapper.convertValue(root, JsCompConfig.class);

		LangTask langTask = (LangTask) core.getTask(inlineParam);
		if(langTask == null) {
			throw new ConfigException("language task for "+inlineParam+" not found. You must start a task for "+inlineParam+" first",null);
		}
		
		JsCompTask task = new JsCompTask(core.getOutputRoot(), config, langTask);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"JsComp:"+config.input+" to "+config.output));
	}
		
}