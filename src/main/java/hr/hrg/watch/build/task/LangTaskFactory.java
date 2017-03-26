package hr.hrg.watch.build.task;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.LangConfig;

public class LangTaskFactory extends AbstractTaskFactory{
	
	private YAMLMapper yamlMapper;
	
	@Inject
	public LangTaskFactory(WatchBuild core, JsonMapper mapper, YAMLMapper yamlMapper){
		super(core, mapper);
		this.yamlMapper = yamlMapper;
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		LangConfig config = mapper.convertValue(root, LangConfig.class);

		if(lang == null) throw new ConfigException("Language task can not run without 'lang' in the enviroment", null); 
		LangTask task = new LangTask(config, core, core.getOutputRoot(),yamlMapper, mapper);

		core.registerTask(lang, task);
	
		task.start(watch);

		if(watch)
			core.addThread(new Thread(task,"Language:"+config.input+" to "+config.output));

	}

	@Override
	public String getDefaultOptionParser() {
		return "YamlPerLanguage";
	}	
	
}