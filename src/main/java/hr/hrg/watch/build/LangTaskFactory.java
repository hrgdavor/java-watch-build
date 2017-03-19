package hr.hrg.watch.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

class LangTaskFactory extends AbstractTaskFactory{
	
	private YAMLMapper yamlMapper;

	public LangTaskFactory(Main core, ObjectMapper mapper, YAMLMapper yamlMapper){
		super(core, mapper);
		this.yamlMapper = yamlMapper;
	}
	
	@Override
	public void startOne(String inlineParam, JsonNode root, boolean watch) {
		LangConfig config = mapper.convertValue(root, LangConfig.class);

		LangTask task = new LangTask(config, core.getOutputRoot(),yamlMapper, mapper);
		core.registerTask(inlineParam, task);
	
		task.start(watch);

		if(watch)
			core.addThread(new Thread(task,"Language:"+config.input+" to "+config.output));

	}

}