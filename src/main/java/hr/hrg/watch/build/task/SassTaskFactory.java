package hr.hrg.watch.build.task;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.WatchUtil;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.SassConfig;
import hr.hrg.watchsass.Compiler;
import hr.hrg.watchsass.CompilerOptions;

public class SassTaskFactory extends AbstractTaskFactory {

	public SassTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		if(!WatchUtil.classAvailable("hr.hrg.watchsass.Compiler")) {
			throw new ConfigException("Sass compiling task is not avaiable due to missing dependecy hr.hrg:java-watch-sass (download full shaded version to fix or remove the @sass task)",null);
		}

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
