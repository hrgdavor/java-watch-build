package hr.hrg.watch.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsCompTaskFactory extends AbstractTaskFactory{
	
	public JsCompTaskFactory(Main core, ObjectMapper mapper){
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