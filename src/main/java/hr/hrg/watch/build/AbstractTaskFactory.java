package hr.hrg.watch.build;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractTaskFactory implements TaskFactory {
	
	protected Main core;
	protected ObjectMapper mapper;

	public AbstractTaskFactory(Main core, ObjectMapper mapper) {
		this.core = core;
		this.mapper = mapper;
	}

	@Override
	public void start(String inlineParam, List<Object> config, boolean watch) {
		JsonNode root = TaskUtils.checkOption(config, 0, JsonNode.class);
		
		if(root.isArray()){
			int i=0;
			try {				
				for(JsonNode tmp: root){
					startOne(inlineParam, tmp, watch);
					i++;
				}
			} catch (Exception e) {
				System.out.println(core.getJson(root.get(i)));
				throw new ConfigException("problem starting config object#"+i+" "+e.getMessage(), e);
			}
		}else{
			startOne(inlineParam, root, watch);
		}
	}
	
	public abstract void startOne(String inlineParam, JsonNode root, boolean watch);

	@Override
	public String getDefaultOptionParser() {
		return "yaml";
	}

	@Override
	public boolean alwaysRun() {
		return false;
	}

}
