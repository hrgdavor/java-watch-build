package hr.hrg.watch.build.task;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.SassConfig;
import hr.hrg.watchsass.Compiler;
import hr.hrg.watchsass.CompilerOptions;

public class SassTask extends AbstractTask<SassConfig> implements Runnable {

	private Compiler compiler;

	public SassTask(SassConfig config, WatchBuild core) {
		super(config, core);
	}

	@Override
	public boolean needsThread() {
		return true;
	}

	@Override
	public void run() {
		compiler.run();
	}

	@Override
	public void init(boolean watch) {
		CompilerOptions options = new CompilerOptions(); 
		options.pathStrInput  = config.input;
		options.pathStrOutput = config.output;
		options.pathStrInclude = config.include;
		options.pathStrExclude = config.exclude;
		options.outputStyle    = config.outputStyle;

		options.embedSourceMapInCSS    = config.embedSourceMapInCSS;
		options.embedSourceContentsInSourceMap    = config.embedSourceContentsInSourceMap;
		options.generateSourceComments    = config.generateSourceComments;
		options.generateSourceMap    = config.generateSourceMap;
		options.inputSyntax    = config.inputSyntax;
		options.omitSourceMapingURL    = config.omitSourceMapingURL;
		options.precision    = config.precision;
		options.burstDelay    = core.getBurstDelay();
		
		compiler = new Compiler(options);

		compiler.init(watch);
		compiler.compile();
	}

	@Override
	public String toString() {
		return "Sass:"+config.input+"-to-"+config.output;
	}
}