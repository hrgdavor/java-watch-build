package hr.hrg.watch.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
		boolean watch  = args.length >1 && "watch".equals(args[1]);
		new BuildRunner(new YAMLMapper(), new ObjectMapper()).run(args[0], watch, null);
	}
	
	public void run(String confFilePath, boolean watch, Map<String, String> overrides) {
		try {
			VarMap vars = new VarMap();
			List<JsonNode> steps = new ArrayList<>();
			File confFile = new File(confFilePath);
			HashMap<File, File> included = new HashMap<>();
			
			loadFile(included,steps,vars, confFile, new File("./"));
			
			File outputRoot = confFile.getAbsoluteFile().getParentFile();
			
			if(overrides != null){
				for(Entry<String, String> entry:overrides.entrySet()){
					vars.put(entry.getKey(), entry.getValue());
				}
			}
			
			for(JsonNode step: steps){
				expandVars(step, vars);				
			}

			String profileString = vars.get("profile");
			String[] profilesActive = profileString.split(",");
			if(profilesActive.length == 1 && "".equals(profilesActive[0])) profilesActive = null;
			
			List<Thread> threads = new ArrayList<>();
			int count = steps.size();
			
			LangRunner langRunner = null;
			for(int i=0; i<count; i++){
				JsonNode step = steps.get(i);
				StepConfig stepConfig = buildStepConfig(step);

				if(!isProfile(profilesActive,stepConfig.profiles)){
					System.out.println("Skipping step "+step.get("type").asText()+" because ti is not in the active profile "+profileString);
					continue;
				}

				if(stepConfig instanceof CopyConfig){
					CopyConfig copyStep = (CopyConfig) stepConfig;
					
					for (CopyConfig.Item item : copyStep.sets){
						if(isNoOutput(item.output)){
							log.info("Skipping copy step "+item.folder+" because of disabled output");
							continue;
						}
						CopyRunner copyRunner = new CopyRunner(item, outputRoot);
						
						copyRunner.start(watch);
					
						if(watch)
							threads.add(new Thread(copyRunner,"Copy:"+item.folder+"-to-"+item.output));
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

					
					langRunner = new LangRunner(langStep, outputRoot, yamlMapper, objectMapper);
					
					langRunner.start(watch);

					if(watch)
						threads.add(new Thread(langRunner,"GenLangFrom:"+langStep.input));

				}else if(stepConfig instanceof CompConfig){
					CompConfig compStep = (CompConfig) stepConfig;
					
					if(langRunner == null){
						throw new RuntimeException("You must run a Language task before JsComp");
					}
					
					for (CompConfig.Item item : compStep.sets){
						CompRunner compRunner = new CompRunner(item, outputRoot,langRunner.getTask());
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
						JsBundlesRunner jbr = new JsBundlesRunner(item);
						
						jbr.start(watch);
						
						if(watch)
							threads.add(new Thread(jbr,"JsBundle:"+item.name+"-to-"+item.output));
						
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
			
			JsonNode stepsNode = conf.get("steps");
			int count =0;
			if(stepsNode != null && !stepsNode.isNull() && stepsNode.isArray()){			
				count = stepsNode.size();
				for(int i=0; i<count; i++){
					steps.add(stepsNode.get(i));
				}
			}
			
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
		}else if(type == "HtmlScriptAndCss"){
			return yamlMapper.convertValue(step, HtmlScriptAndCssConfig.class);
		}else if(type == "Sass"){
			return yamlMapper.convertValue(step, SassConfig.class);
		}else{
			throw new RuntimeException("Unsupported step type: "+type);
		}
	}

	private boolean isProfile(String[] profilesGlobal, List<String> profiles) {
		// profile not defined (all can pass)
		if(profilesGlobal == null || profilesGlobal.length == 0) return true;
		if(profiles == null || profiles.isEmpty()) return true;
		for (String tmp : profiles){
			for (String profile : profilesGlobal){
				if(tmp.equals(profile)) return true;			
			}
		}
		return false;
	}

	public static final void expandVars(JsonNode node, VarMap vars) {
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
	}
	

	public static void loadVars(VarMap map, JsonNode node, boolean replace){
		if(node == null || node.isNull()) return;
		
		Iterator<Entry<String, JsonNode>> fields = node.fields();
		
		while(fields.hasNext()){
			Entry<String, JsonNode> next = fields.next();
			String key = next.getKey();
			if(replace || !map.containsKey(key)) map.put(key, next.getValue().asText());
		}		
	}

}
