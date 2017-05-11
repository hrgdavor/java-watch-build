package hr.hrg.watch.build.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.LangOutputConfig;

public class LangOutputTaskFactory extends AbstractTaskFactory{
	
	private YAMLMapper yamlMapper;
	
	public LangOutputTaskFactory(WatchBuild core, JsonMapper mapper, YAMLMapper yamlMapper){
		super(core, mapper);
		this.yamlMapper = yamlMapper;
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		LangOutputConfig config = mapper.convertValue(root, LangOutputConfig.class);

		if(lang == null) throw new ConfigException("Language task can not run without 'lang' in the enviroment", null); 
		LangTask langTask = (LangTask) core.getTask(lang);
		if(langTask == null) {
			throw new ConfigException("language task for "+lang+" not found. You must start a task for "+lang+" first",null);
		}

		LangOutputTask task = new LangOutputTask(config, langTask, core, core.getOutputRoot(),yamlMapper, mapper);
	
		task.start(watch);

		if(watch && config.codeList != null)
			core.addThread(new Thread(task,"LanguageOutput:"+config.output));

	}

	@Override
	public String getDefaultOptionParser() {
		return "YamlPerLanguage";
	}	
	
}