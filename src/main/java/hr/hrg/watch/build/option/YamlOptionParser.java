package hr.hrg.watch.build.option;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.VarMap;
import hr.hrg.watch.build.config.TaskOption;

public class YamlOptionParser implements OptionParser{

	private YAMLMapper yamlMapper;
	private boolean perLanguage;
	private Main core;

	public YamlOptionParser(Main core, YAMLMapper yamlMapper, boolean perLanguage) {
		this.core = core;
		this.yamlMapper = yamlMapper;
		this.perLanguage = perLanguage;
	}
	
	@Override
	public Object parse(TaskOption option) {
		try {			
			byte[] nl = System.lineSeparator().getBytes();
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			
			for(String line: option.lines) {
				bo.write(line.getBytes());
				bo.write(nl);
			}
			JsonNode node = yamlMapper.readTree(bo.toByteArray());
			if(perLanguage) return multiply(node);
			return TaskUtils.expandVars(node, core.getVars());
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	private Object multiply(JsonNode node) {
		String[] langs = core.getLangs();
		if(langs.length<2) 	return TaskUtils.expandVars(node, core.getVars());
		ArrayNode arr = yamlMapper.createArrayNode();
		VarMap vars = core.getVars();
		String oldLang = vars.get("lang");
		for(String lang: langs){
			vars.put("lang", lang);
			JsonNode copy = TaskUtils.expandVars(TaskUtils.copy(yamlMapper, node), vars);
			//if first level is array flatten that first level
			if(copy.isArray()){
				for(JsonNode tmp:copy) arr.add(tmp);
			}else {				
				arr.add(copy);
			}
		}
		vars.put("lang", oldLang);
		return arr;
	}
}
