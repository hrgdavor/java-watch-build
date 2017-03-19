package hr.hrg.watch.build.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.config.LangConfig;

public class LangTaskFactory extends AbstractTaskFactory{
	
	private YAMLMapper yamlMapper;

	public LangTaskFactory(Main core, ObjectMapper mapper, YAMLMapper yamlMapper){
		super(core, mapper);
		this.yamlMapper = yamlMapper;
	}
	
	@Override
	public void startOne(String inlineParam, JsonNode root, boolean watch) {
		LangConfig config = mapper.convertValue(root, LangConfig.class);

		LangTask task = new LangTask(config, inlineParam,core, core.getOutputRoot(),yamlMapper, mapper);
	
		task.start(watch);

		if(watch)
			core.addThread(new Thread(task,"Language:"+config.input+" to "+config.output));

	}

}