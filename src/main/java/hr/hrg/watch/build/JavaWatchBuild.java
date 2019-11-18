package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import hr.hrg.watch.build.task.AbstractTask;
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
import hr.hrg.watch.build.task.SassTaskFactory;
import hr.hrg.watchsass.Compiler;
import wrm.libsass.SassCompiler.OutputStyle;

public class JavaWatchBuild {

	List<AbstractTaskFactory<?, ?>> factories = new ArrayList<>();
	WatchBuild core;
	
	public JavaWatchBuild(){
		core = new WatchBuild();	
	}
	
	public void setVerbose(int verbose) {
		Main.VERBOSE = verbose;
		Compiler.VERBOSE = verbose;
	}
	
	public void start(boolean watch) {
		
		List<AbstractTask<?>> tasks = new ArrayList<>();
		
		for(AbstractTaskFactory<?, ?> factory:factories) {
			tasks.add(factory.build());
		}

		core.runBuild(tasks, watch);
	}

	private <T extends AbstractTaskFactory<?, ?>> T add(T factory) {
		factories.add(factory);
		return factory;
	}
	
	public LiveReloadTaskFactory doLiveReload(String input) {
		return add(new LiveReloadTaskFactory(core, input)); 
	}

	public MkdirTaskFactory doMkdir(String ...dirs) {
		return add(new MkdirTaskFactory(core, dirs)); 
	}
	
	public CopyTaskFactory doCopy(String input, String output) {
		return add(new CopyTaskFactory(core, input, output)); 
	}
	
	public GzipTaskFactory doGzip(String input, String output) {
		return add(new GzipTaskFactory(core, input, output)); 
	}

	public ExtTaskFactory doExt(String cmd, String ...params) {
		return add(new ExtTaskFactory(core, cmd, params)); 
	}

	public LangTaskFactory doLang(String input) {
		return add(new LangTaskFactory(core, input));
	}

	public JsBundlesTaskFactory doJsBundles(String root) {
		return add(new JsBundlesTaskFactory(core, root));
	}
	
	public ProxyTaskFactory doProxy(int port) {
		return add(new ProxyTaskFactory(core, port));
	}
	
	/** Same as doScss
	 * 
	 * @param input input 
	 * @param output output
	 * @return taskFactory
	 */
	public SassTaskFactory doSass(String input, String output) {
		return doScss(input, output);
	}
	
	public SassTaskFactory doScss(String input, String output) {
		return add(doScss(input, output, OutputStyle.expanded, false, true));
	}
	
	public SassTaskFactory doScss(String input, String output, OutputStyle outputStyle, boolean generateSourceMap, boolean generateSourceComments) {
		return add(new SassTaskFactory(core, input, output, outputStyle, generateSourceMap, generateSourceComments));
	}

	public HtmlScriptAndCssTaskFactory doHtmlScriptAndCss(String input, String output) {
		return add(new HtmlScriptAndCssTaskFactory(core, input, output));
	}
	
	public String dumpConfig() {
		List<Object> tasks = new ArrayList<>();
		
		for(AbstractTaskFactory<?, ?> factory:factories) {
			tasks.add(factory.getConfig());
		}
		
		try {
			return core.getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(tasks);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}
}
