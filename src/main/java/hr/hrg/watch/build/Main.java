package hr.hrg.watch.build;

import java.util.HashMap;

import hr.hrg.watch.build.config.MkdirConfig;
import hr.hrg.watch.build.task.AbstractTaskFactory;
import hr.hrg.watch.build.task.CopyTaskFactory;
import hr.hrg.watch.build.task.ExtTaskFactory;
import hr.hrg.watch.build.task.GzipTaskFactory;
import hr.hrg.watch.build.task.HtmlScriptAndCssTaskFactory;
import hr.hrg.watch.build.task.JsBundlesTaskFactory;
import hr.hrg.watch.build.task.LangTaskFactory;
import hr.hrg.watch.build.task.LiveReloadTaskFactory;
import hr.hrg.watch.build.task.MkdirTaskFactory;
import hr.hrg.watch.build.task.ProxyTaskFactory;
import hr.hrg.watch.build.task.SassBundlesTaskFactory;
import hr.hrg.watch.build.task.SassTaskFactory;

public class Main {

	public static int VERBOSE = 0;
	
	public static void main(String[] args) {
		if(args.length == 0) printHelp();
		System.setProperty("file.encoding","UTF-8");
		WatchBuild watchBuild = make();
		watchBuild.runFromArgs(args);
	}

	public static WatchBuild make() {
		WatchBuild watchBuild = new WatchBuild();
		addDefaults(watchBuild);
		return watchBuild;
	}

	private static void addDefaults(WatchBuild watchBuild) {
		HashMap<String, AbstractTaskFactory<?,?>> factories = new HashMap<>();

		// basic
		factories.put("Mkdir", new MkdirTaskFactory(watchBuild));
		factories.put("Copy", new CopyTaskFactory(watchBuild));
		factories.put("Gzip", new GzipTaskFactory(watchBuild));
		factories.put("Ext", new ExtTaskFactory(watchBuild));
		factories.put("Proxy", new ProxyTaskFactory(watchBuild));
		factories.put("LiveReload", new LiveReloadTaskFactory(watchBuild));
		
		// advanced tasks (in mini shaded bundle:  sass is not included at all, jsbundles works, but can not compile js)
		factories.put("JsBundles", new JsBundlesTaskFactory(watchBuild));
		factories.put("Sass", new SassTaskFactory(watchBuild));
		factories.put("Scss", new SassTaskFactory(watchBuild));
		factories.put("SassBundles", new SassBundlesTaskFactory(watchBuild));
		
		// misc personally used for joining javascript and template into one file
		factories.put("Lang", new LangTaskFactory(watchBuild));
		factories.put("HtmlScriptAndCss", new HtmlScriptAndCssTaskFactory(watchBuild));
		
		watchBuild.setFactories(factories);
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
		System.out.println("\t -v        - verbose level 1");
		System.out.println("\t -vv       - verbose level 2");
		System.out.println("\t --dry-run - show final configuration for checking the end result is what was intended");
		System.exit(0);
	}

	public static void logError(String message, Throwable e) {
		System.err.println(message);
		if(e != null) e.printStackTrace();
	}

	public static void logInfo(String string) {
		System.out.println(string);
	}

	public static void logWarn(String string) {
		System.out.println(string);		
	}

	
}
