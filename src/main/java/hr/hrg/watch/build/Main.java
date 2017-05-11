package hr.hrg.watch.build;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.option.LinesOptionParser;
import hr.hrg.watch.build.option.OptionParser;
import hr.hrg.watch.build.option.YamlOptionParser;
import hr.hrg.watch.build.task.CopyTaskFactory;
import hr.hrg.watch.build.task.EnvTaskFactory;
import hr.hrg.watch.build.task.GzipTaskFactory;
import hr.hrg.watch.build.task.HtmlScriptAndCssTaskFactory;
import hr.hrg.watch.build.task.ImportTaskFactory;
import hr.hrg.watch.build.task.JsBundlesTaskFactory;
import hr.hrg.watch.build.task.JsCompTaskFactory;
import hr.hrg.watch.build.task.LangOutputTaskFactory;
import hr.hrg.watch.build.task.LangTaskFactory;
import hr.hrg.watch.build.task.NoOpTaskFactory;
import hr.hrg.watch.build.task.SassBundlesTaskFactory;
import hr.hrg.watch.build.task.SassTaskFactory;
import hr.hrg.watch.build.task.ScriptTaskFactory;
import hr.hrg.watch.build.task.TaskFactory;
import hr.hrg.watch.build.task.VarTaskFactory;

public class Main {

	public static void main(String[] args) {
		if(args.length == 0) printHelp();
		
//		ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
//		if(loggerFactory instanceof LoggerContext) {
//			LoggerContext context = (LoggerContext) loggerFactory;
//			Logger logger = context.getLogger(Logger.ROOT_LOGGER_NAME);
//			
//			AppenderBase<ILoggingEvent> appender = new AppenderBase<ILoggingEvent>() {
//				@Override
//				protected void append(ILoggingEvent eventObject) {
//					System.err.println(eventObject);
//				}
//			};
//			appender.setContext(context);
//			appender.setName("ws-log");
//			logger.setLevel(Level.DEBUG);
//			logger.addAppender(appender);
//			System.err.println("ROOT "+logger);
//		}
		
		
//		setDefaultProperty("org.slf4j.simpleLogger.logFile", "System.out");
//		setDefaultProperty("org.slf4j.simpleLogger.cacheOutputStream", "true");
//		setDefaultProperty("org.slf4j.simpleLogger.showDateTime", "true");
//		setDefaultProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy.MM.dd HH:mm:ss.SSS");
		
		if(args[0].endsWith(".js")){
			ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
			String script = "var hrhrgwatchbuildMain = Java.type('hr.hrg.watch.build.WatchBuild');" 
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
			WatchBuild watchBuild = make();			
			watchBuild.runFromArgs(args);
			
		}else{
			System.out.println("Supporting only script(*.js) or YAML configuration (*.yml,*.yaml)");
		}

	}

	public static WatchBuild make() {
		YAMLMapper yamlMapper = new YAMLMapper();
		JsonMapper mapper = new JsonMapper();
		WatchBuild watchBuild = new WatchBuild();
		
		addDefaults(yamlMapper, mapper, watchBuild);

		return watchBuild;
	}

	private static void addDefaults(YAMLMapper yamlMapper, JsonMapper mapper, WatchBuild watchBuild) {
		HashMap<String, OptionParser> parsers = new HashMap<>();
		HashMap<String, TaskFactory> factories = new HashMap<>();

		// configuartion parsing
		parsers.put("lines", new LinesOptionParser());
		parsers.put("Yaml", new YamlOptionParser(watchBuild,yamlMapper, false));
		parsers.put("YamlPerLanguage", new YamlOptionParser(watchBuild,yamlMapper, true));
		
		// disable tasks by defining a disable task instad of the original
		factories.put("disable", new NoOpTaskFactory());
		// import
		factories.put("import", new ImportTaskFactory(watchBuild));
		
		// variable handling
		factories.put("var", new VarTaskFactory(watchBuild,true));
		factories.put("env", new EnvTaskFactory(watchBuild));
		factories.put("defvar", new VarTaskFactory(watchBuild,false));
		
		// basic
		factories.put("Copy", new CopyTaskFactory(watchBuild,mapper));
		factories.put("Gzip", new GzipTaskFactory(watchBuild,mapper));
		factories.put("Script", new ScriptTaskFactory(watchBuild,mapper));
		
		// advanced tasks (in mini shaded bundle:  sass is not included at all, jsbundles works, but can not compile js)
		factories.put("JsBundles", new JsBundlesTaskFactory(watchBuild,mapper));
		factories.put("Sass", new SassTaskFactory(watchBuild,mapper));
		factories.put("SassBundles", new SassBundlesTaskFactory(watchBuild,mapper));
		
		// misc personally used for joining javascript and template into one file
		factories.put("JsComp", new JsCompTaskFactory(watchBuild,mapper));
		factories.put("Language", new LangTaskFactory(watchBuild,mapper, yamlMapper));
		factories.put("LanguageOutput", new LangOutputTaskFactory(watchBuild,mapper, yamlMapper));
		factories.put("HtmlScriptAndCss", new HtmlScriptAndCssTaskFactory(watchBuild,mapper));
		

		watchBuild.setFactories(factories);
		watchBuild.setParsers(parsers);
	}	
	
	public static final void setDefaultProperty(String key, String value){
		if(System.getProperty(key) == null)
			System.setProperty(key,value);		
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
