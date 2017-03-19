package hr.hrg.watch.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hr.hrg.watchsass.Compiler;
import hr.hrg.watchsass.CompilerOptions;

public class SassTaskFactory extends AbstractTaskFactory {

	public SassTaskFactory(Main core, ObjectMapper mapper){
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
		
		Compiler compiler = new Compiler(options);

		compiler.init(watch);
		compiler.compile();

		if(watch)
			core.addThread(new Thread(compiler,"Sass:"+config.input+"-to-"+config.output));

	}
}
