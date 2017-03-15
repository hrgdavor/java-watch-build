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
			boolean watch  = false;
			boolean dryRun = false;

			for(int i=1; i<args.length; i++) {
				if("--watch".equals(args[i])) watch = true;
				else if("--dry-run".equals(args[i])) dryRun = true;
			}
			
			runBuild(args[0], watch, dryRun);
			
		}else{
			System.out.println("Supporting only script(*.js) or YAML configuration (*.yml,*.yaml)");
		}

	}
	
	public static void runBuild(String file, boolean watch, boolean dryRun) {
		HashMap<String, String> vars = new HashMap<>();

		BuildRunner buildRunner = new BuildRunner(yamlMapper, mapper);

		buildRunner.run(file, watch, dryRun, vars);
	}

	public static void printHelp(){
		System.out.println("Usage: conf|script ");
		System.out.println("\t conf      - configuration file in yml format (requires profile and lang)");
		System.out.println("\t script    - javascript script that will be executed using nashorn");
		System.out.println("\t --watch   - continue watching after build");
		System.out.println("\t --dry-run - show final configuration for checking the end result is what was intended");
		System.exit(0);
	}
}
