package hr.hrg.watch.build;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class Main {
	static ObjectMapper mapper   = new ObjectMapper();
	static YAMLMapper yamlMapper = new YAMLMapper();
	
	public static void main(String[] args) {
		if(args.length == 0) printHelp();
		
		if(args[0].endsWith(".js")){
			ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
			String script = "var hrhrgwatchbuildMain = Java.type('hr.hrg.watch.build.Main');" 
					+"function alert(x){print(x);};\n"
					+"function runBuild(conf,profile,lang,watch){\n"
					+"	hrhrgwatchbuildMain.runBuild(conf,profile,lang,watch);\n"
					+"}\n"
					+"\n"
					;
			try {
				engine.eval(script);
				engine.eval(new FileReader(new File(args[0])));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else if(args[0].endsWith(".yml") || args[0].endsWith(".yaml")){
			boolean watch = args.length > 1 && "true".equalsIgnoreCase(args[1]);

			String profile = null;
			if(args.length > 2) profile = args[2];
			String lang = null;
			if(args.length > 3) profile = args[3];
			
			runBuild(args[0], profile, lang, watch);
			
			if(args.length > 4){
				String[] args2 = new String[args.length-4];
				System.arraycopy(args, 4, args2, 0, args.length-4);
				main(args2);
			}
		}else{
			System.out.println("Supporting only script(*.js) or YAML configuration (*.yml,*.yaml)");
		}

	}
	
	public static void runBuild(String file, String profile, String language, boolean watch) {
		HashMap<String, String> vars = new HashMap<>();
		if(language != null) vars.put("lang",    language);
		if(profile  != null) vars.put("profile", profile);

		BuildRunner buildRunner = new BuildRunner(yamlMapper, mapper);

		buildRunner.run(file, watch, vars);
	}

	public static void printHelp(){
		System.out.println("Usage: conf|script [watch profile lang]");
		System.out.println("\t conf - configuration file in yml format (requires profile and lang)");
		System.out.println("\t script - javascript script that will be executed using nashorn");
		System.out.println("\t lang - continue watching after build");
		System.out.println("\t profile - build profile (this can influence which steps are executed)");
		System.out.println("\t lang - build language");
		System.exit(0);
	}
}
