package hr.hrg.watch.build.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.SassConfig;
import hr.hrg.watchsass.Compiler;
import hr.hrg.watchsass.CompilerOptions;

public class SassTaskFactory extends AbstractTaskFactory {

	@Inject
	public SassTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(String inlineParam, JsonNode root, boolean watch) {
		SassConfig config = mapper.convertValue(root, SassConfig.class);
		CompilerOptions options = new CompilerOptions(); 
		options.pathStrInput  = config.input;
		options.pathStrOutput = config.output;
		options.pathStrInclude = config.include;
		options.outputStyle    = config.outputStyle;

		options.embedSourceMapInCSS    = config.embedSourceMapInCSS;
		options.embedSourceContentsInSourceMap    = config.embedSourceContentsInSourceMap;
		options.generateSourceComments    = config.generateSourceComments;
		options.generateSourceMap    = config.generateSourceMap;
		options.inputSyntax    = config.inputSyntax;
		options.omitSourceMapingURL    = config.omitSourceMapingURL;
		options.precision    = config.precision;
		options.burstDelay    = core.getBurstDelay();
		
		Compiler compiler = new Compiler(options);

		compiler.init(watch);
		compiler.compile();

		if(watch)
			core.addThread(new Thread(compiler,"Sass:"+config.input+"-to-"+config.output));

	}
}
