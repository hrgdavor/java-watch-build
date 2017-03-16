package hr.hrg.watch.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watchsass.Compiler;
import hr.hrg.watchsass.CompilerOptions;

public class BuildRunner{
	
	private YAMLMapper yamlMapper;
	private ObjectMapper objectMapper;

	Logger log = LoggerFactory.getLogger(BuildRunner.class);
	
	public BuildRunner(YAMLMapper yamlMapper, ObjectMapper objectMapper){
		this.yamlMapper = yamlMapper;
		this.objectMapper = objectMapper;
	}

	public static void main(String[] args) {
		boolean watch  = false;
		boolean dryRun = false;
		for(int i=1; i<args.length; i++) {
			if("--watch".equals(args[i])) watch = true;
			else if("--dry-run".equals(args[i])) dryRun = true;
		}
		new BuildRunner(new YAMLMapper(), new ObjectMapper()).run(args[0], watch, dryRun , null);
	}
	
	public void run(String confFilePath, boolean watch, boolean dryRun, Map<String, String> overrides) {
		try {
			VarMap vars = new VarMap();
			List<JsonNode> steps = new ArrayList<>();
			File confFile = new File(confFilePath);
			HashMap<File, File> included = new LinkedHashMap<>();
			
			loadFile(included,steps,vars, confFile, new File("./"));
			
			File outputRoot = confFile.getAbsoluteFile().getParentFile();
			
			if(overrides != null){
				for(Entry<String, String> entry:overrides.entrySet()){
					vars.put(entry.getKey(), entry.getValue());
				}
			}

			// multiply steps that are perLanguage
			List<JsonNode> tmpSteps = new ArrayList<>();
			String lang = vars.get("lang");
			if(lang == null ) lang = "";
			String[] langs = new String[]{lang};
			if(lang.indexOf(',') != -1) {
				langs = lang.split(",");
				lang = langs[0];
			}

			for(JsonNode step: steps){
				if(langs.length >1 && step.get("perLanguage") != null && step.get("perLanguage").booleanValue()){
					for(String tmpLang:langs) {
						log.debug("Multiply step "+step.get("type").textValue()+" for lang:"+tmpLang);
						vars.put("lang", tmpLang);
						tmpSteps.add(expandVars(copy(objectMapper,step), vars));
					}
					// restore default lang
					vars.put("lang", lang);
				}else{
					expandVars(step, vars);
					tmpSteps.add(step);
				}
			}
			steps = tmpSteps;

			if(dryRun) {
				System.out.println("Config files used");
				System.out.println(confFile.getAbsolutePath());
				for(File path: included.keySet()) {
					System.out.println(path.getAbsolutePath());
				}
				System.out.println("languages: "+objectMapper.writeValueAsString(langs));
				System.out.println("Final values for variables after all includded files");
				System.out.println(yamlMapper.writeValueAsString(vars.getVars()));
				System.out.println("Final configuration after all files included and variables expanded");
				System.out.println(yamlMapper.writeValueAsString(steps));
				System.exit(0);
			}
			
			List<Thread> threads = new ArrayList<>();
			int count = steps.size();
			
			HashMap<String, LangTask> langTaskMap = new HashMap<>();  
			for(int i=0; i<count; i++){
				JsonNode step = steps.get(i);
				StepConfig stepConfig = buildStepConfig(step);

				if(stepConfig instanceof CopyConfig){
					CopyConfig copyStep = (CopyConfig) stepConfig;
					
					for (CopyConfig.Item item : copyStep.sets){
						if(isNoOutput(item.output)){
							log.info("Skipping copy step "+item.input+" because of disabled output");
							continue;
						}
						CopyRunner copyRunner = new CopyRunner(item, outputRoot);
						
						copyRunner.start(watch);
					
						if(watch)
							threads.add(new Thread(copyRunner,"Copy:"+item.input+"-to-"+item.output));
					}
					
				}else if(stepConfig instanceof GzipConfig){
					GzipConfig gzipStep = (GzipConfig) stepConfig;
					
					for (GzipConfig.Item item : gzipStep.sets){
						if(item.output == null) item.output = item.input;
						if(isNoOutput(item.output)){
							log.info("Skipping copy step "+item.input+" because of disabled output");
							continue;
						}
						GzipRunner gzipRunner = new GzipRunner(item, outputRoot);
						
						gzipRunner.start(watch);
					
						if(watch)
							threads.add(new Thread(gzipRunner,"Gzip:"+item.input+"-to-"+item.output));
					}
				}else if(stepConfig instanceof HtmlScriptAndCssConfig){
					HtmlScriptAndCssConfig htmlStep = (HtmlScriptAndCssConfig) stepConfig;
					
					if(isNoOutput(htmlStep.output)) continue;
					
					HtmlScriptAndCssRunner htmlRunner = new HtmlScriptAndCssRunner(htmlStep, outputRoot, objectMapper);

					htmlRunner.start(watch);

					if(watch)
						threads.add(new Thread(htmlRunner,"HtmlScriptAndCssConfig:"+htmlStep.input+"->"+htmlStep.output));
					
				}else if(stepConfig instanceof LangConfig){
					LangConfig langStep = (LangConfig)stepConfig;
					boolean skip = false;
					for(String tmp:langStep.output){
						if(isNoOutput(tmp)) skip = true;
					}
					if(skip){
						log.info("Skipping lang step "+langStep.input+" because of disabled output");
						continue;
					}

					
					LangRunner langRunner = new LangRunner(langStep, outputRoot, yamlMapper, objectMapper);
					langTaskMap.put(langStep.input, langRunner.getTask());
					langRunner.start(watch);

					if(watch)
						threads.add(new Thread(langRunner,"GenLangFrom:"+langStep.input));

				}else if(stepConfig instanceof CompConfig){
					CompConfig compStep = (CompConfig) stepConfig;
					LangTask langTask = langTaskMap.get(compStep.lang);
					if(langTask == null){
						throw new RuntimeException("You must run a Language task that reads "+compStep.lang+" before JsComp that uses that language file");
					}
					
					for (CompConfig.Item item : compStep.sets){
						CompRunner compRunner = new CompRunner(item, outputRoot,langTask);
						if(isNoOutput(item.output)){
							log.info("Skipping JsComp step "+item.input+" because of disabled output");
							continue;
						}
						
						compRunner.start(watch);
						
						if(watch)
							threads.add(new Thread(compRunner,"JsComp:"+item.input+"-to-"+item.output));
					}
				}else if(stepConfig instanceof JsBundlesConfig){
					JsBundlesConfig jsBundlesConfig = (JsBundlesConfig) stepConfig;
					
					for(JsBundlesConfig.Item item: jsBundlesConfig.sets){
						JsBundlesRunner jbr = new JsBundlesRunner(item, objectMapper);
						
						jbr.start(watch);
						
						if(watch)
							threads.add(new Thread(jbr,"JsBundle:"+item.name+"-to-"+item.root));
						
					}
					
				}else if(stepConfig instanceof SassConfig){
					SassConfig sassStep = (SassConfig) stepConfig;
					for (SassConfig.Item item : sassStep.sets){
						CompilerOptions options = new CompilerOptions(); 
						options.pathStrInput  = item.input;
						options.pathStrOutput = item.output;
						options.pathStrInclude = item.include;
						options.outputStyle    = item.outputStyle;

						options.embedSourceMapInCSS    = item.embedSourceMapInCSS;
						options.embedSourceContentsInSourceMap    = item.embedSourceContentsInSourceMap;
						options.generateSourceComments    = item.generateSourceComments;
						options.generateSourceMap    = item.generateSourceMap;
						options.inputSyntax    = item.inputSyntax;
						options.omitSourceMapingURL    = item.omitSourceMapingURL;
						options.precision    = item.precision;
						
						Compiler compiler = new Compiler(options);

						compiler.init(watch);
						compiler.compile();

						if(watch)
							threads.add(new Thread(compiler,"Sass:"+item.input+"-to-"+item.output));
					}
				}else{
					throw new RuntimeException("Unsupported step config "+stepConfig.getClass().getName());
				}
			}

			if(threads.size() >0) System.out.println("Starting "+threads.size()+" watch threads");
			for(Thread thread: threads){
				System.out.println("THREAD: "+thread.getName());
				thread.start();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	public static boolean isNoOutput(String dest) {
		return dest == null || "".equals(dest) || dest.startsWith("/dev/null");
	}

	private void loadFile(HashMap<File, File> included, List<JsonNode> steps, VarMap vars, File confFile, File file) throws JsonProcessingException, IOException {
		try {
			JsonNode conf = yamlMapper.readTree(confFile);
			loadVars(vars, conf.get("defVars"), false);
			loadVars(vars, conf.get("vars"), true);

			int count =0;
			
			JsonNode includesNode = conf.get("include");
			if(includesNode != null && !includesNode.isNull() && includesNode.isArray()){
				count = includesNode.size();
				for(int i=0; i<count; i++){
					File inc = new File(confFile.getParentFile(), includesNode.get(i).asText()).getAbsoluteFile();
					if(included.containsKey(inc)){
						log.warn("Incude defined in "+confFile.getAbsolutePath()+" was already included in "+included.get(inc));
					}else{
						included.put(inc, confFile);
						loadFile(included, steps, vars, inc, file);
					}
				}
			}

			JsonNode stepsNode = conf.get("steps");
			if(stepsNode != null && !stepsNode.isNull() && stepsNode.isArray()){			
				count = stepsNode.size();
				for(int i=0; i<count; i++){
					steps.add(stepsNode.get(i));
				}
			}
			
		} catch (Exception e) {
			log.error("Errro loading file "+confFile.getAbsolutePath(),e);
		}
	}

	private StepConfig buildStepConfig(JsonNode step) {
		String type = step.get("type").asText().intern();
		if(type == "Copy"){
			return yamlMapper.convertValue(step, CopyConfig.class);
		}else if(type == "Language"){
			return  yamlMapper.convertValue(step, LangConfig.class);
		}else if(type == "JsBundles"){
			return  yamlMapper.convertValue(step, JsBundlesConfig.class);			
		}else if(type == "JsComp"){
			return yamlMapper.convertValue(step, CompConfig.class);
		}else if(type == "Gzip"){
			return yamlMapper.convertValue(step, GzipConfig.class);
		}else if(type == "HtmlScriptAndCss"){
			return yamlMapper.convertValue(step, HtmlScriptAndCssConfig.class);
		}else if(type == "Sass"){
			return yamlMapper.convertValue(step, SassConfig.class);
		}else{
			throw new RuntimeException("Unsupported step type: "+type);
		}
	}

	public static final JsonNode expandVars(JsonNode node, VarMap vars) {
		
		if(node.isArray()){
			int count = node.size();
			ArrayNode arr = (ArrayNode) node;
			for(int i=0; i<count; i++){
				if(node.get(i).isTextual()){
					arr.set(i, new TextNode(vars.expand(arr.get(i).asText())));
				}else	
					expandVars(node.get(i), vars);
			}
		}else if(node.isObject()){
			ObjectNode obj = (ObjectNode) node;
			Iterator<Entry<String, JsonNode>> fields = obj.fields();
			
			while(fields.hasNext()){
				Entry<String, JsonNode> next = fields.next();
				if(next.getValue().isTextual()){
					next.setValue(new TextNode(vars.expand(next.getValue().asText())));
				}else	
					expandVars(next.getValue(), vars);
			}
		}
		return node;
	}
	
	
	public static <T extends JsonNode> T copy(ObjectMapper mapper, T node) {
		try {
			return (T) mapper.readTree(node.traverse());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static void loadVars(VarMap map, JsonNode node, boolean replace){
		if(node == null || node.isNull()) return;
		
		Iterator<Entry<String, JsonNode>> fields = node.fields();
		
		while(fields.hasNext()){
			Entry<String, JsonNode> next = fields.next();
			String key = next.getKey();
			// if the new variable has some expression for expanding, do it now, so variables can also be combined
			if(replace || !map.containsKey(key)) map.put(key, map.expand(next.getValue().asText()));
		}		
	}

}
