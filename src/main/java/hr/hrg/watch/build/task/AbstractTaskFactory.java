package hr.hrg.watch.build.task;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.config.ConfigException;

public abstract class AbstractTaskFactory implements TaskFactory {
	
	protected WatchBuild core;
	protected ObjectMapper mapper;

	public AbstractTaskFactory(WatchBuild core, ObjectMapper mapper) {
		this.core = core;
		this.mapper = mapper;
	}

	@Override
	public void start(String inlineParam, List<Object> config, boolean watch) {
		JsonNode root = TaskUtils.checkOption(config, 0, JsonNode.class);
		
		String lang = core.getLang();
		
/*
configuration comes from yaml as JsonNode because jackson is used
there are 3 different ways the config can come
1) ObjectNode actual config object to run a task instance {...}
2) ArrayNode - each element is a config object to run a task instance [{...}...]
3) ArrayNode - each element is a language specific object {"lang":"xx", "items":[{...}...]} and each item is a config object to run a task instance

*/			
		if(root.isArray()){
			int i=0;
			try {				
				for(JsonNode tmp: root){
					
					if(tmp.hasNonNull("lang")) {// perLanguage configuration where each is packed with together lang information
						lang = tmp.get("lang").textValue();
						JsonNode items = tmp.get("items");
						for(JsonNode item: items){
							startOne(inlineParam, lang, item, watch);							
						}
					}else{// norma multiple configs to run mutliple tasks of same type
						startOne(inlineParam, lang, tmp, watch);						
					}
					i++;
				}
				
			} catch (Exception e) {
				System.out.println(core.getJson(root.get(i)));
				throw new ConfigException("problem starting config object#"+i+" "+e.getMessage(), e);
			}
		}else{
			startOne(inlineParam, lang, root, watch);
		}
	}
	
	public abstract void startOne(String inlineParam, String lang, JsonNode root, boolean watch);

	@Override
	public String getDefaultOptionParser() {
		return "yaml";
	}

	@Override
	public boolean alwaysRun() {
		return false;
	}

}
