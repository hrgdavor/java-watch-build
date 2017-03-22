package hr.hrg.watch.build.option;

import java.io.ByteArrayOutputStream;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.VarMap;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.TaskOption;

public class YamlOptionParser implements OptionParser{

	private YAMLMapper yamlMapper;
	private boolean perLanguage;
	private WatchBuild core;

	@Inject
	public YamlOptionParser(WatchBuild core, YAMLMapper yamlMapper, boolean perLanguage) {
		this.core = core;
		this.yamlMapper = yamlMapper;
		this.perLanguage = perLanguage;
	}
	
	@Override
	public Object parse(TaskOption option) {
		try {			
			byte[] nl = System.lineSeparator().getBytes();
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			
			// TRICK: add empty lines to mimic proper line numbers if yaml parsing error occurs :D
			for(int i=0; i<option.lineNumber; i++) {
				bo.write(nl);				
			}
			
			for(String line: option.lines) {
				bo.write(line.getBytes());
				bo.write(nl);
			}
			
			JsonNode node = yamlMapper.readTree(bo.toByteArray());
			if(perLanguage) return multiply(node);
			return TaskUtils.expandVars(node, core.getVars());
		} catch (Exception e) {
			throw new ConfigException(option, e.getMessage(), e);
		}
	}

	private Object multiply(JsonNode node) {
		String[] langs = core.getLangs();
		if(langs.length<2) 	return TaskUtils.expandVars(node, core.getVars());
		ArrayNode arr = yamlMapper.createArrayNode();
		VarMap vars = core.getVars();
		String oldLang = vars.get("lang");
		for(String lang: langs){
			
			ObjectNode langCopy = yamlMapper.createObjectNode();
			arr.add(langCopy);
			langCopy.put("lang", lang);
			ArrayNode items = yamlMapper.createArrayNode();
			langCopy.set("items", items);
			
			vars.put("lang", lang);
			JsonNode copy = TaskUtils.expandVars(TaskUtils.copy(yamlMapper, node), vars);
			//if first level is array flatten that first level
			if(copy.isArray()){
				for(JsonNode tmp:copy) items.add(tmp);
			}else {				
				items.add(copy);
			}
		}
		vars.put("lang", oldLang);
		return arr;
	}
}
