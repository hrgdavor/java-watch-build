package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.javawatcher.WatchUtil;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.SassConfig;
import wrm.libsass.SassCompiler.InputSyntax;
import wrm.libsass.SassCompiler.OutputStyle;

public class SassTaskFactory extends AbstractTaskFactory<SassTask, SassConfig>{

	public SassTaskFactory(WatchBuild core){
		super(core, new SassConfig());
		if(!WatchUtil.classAvailable("hr.hrg.watchsass.Compiler")) {
			throw new RuntimeException("Sass compiling task is not avaiable due to missing dependecy hr.hrg:java-watch-sass (download full shaded version to fix or remove the @sass task)",null);
		}
	}
	
	public SassTaskFactory(WatchBuild core, String input, String output, OutputStyle outputStyle, boolean generateSourceMap,boolean generateSourceComments) {
		super(core, new SassConfig());
		config.input = input;
		config.output = output;
		config.outputStyle = outputStyle;
	}

	@Override
	public SassTask build() {
		return new SassTask(config, core);
	}

	public SassTaskFactory include(String ...arr) { addAll(config.include, arr); return this; }
	public SassTaskFactory include(List<String> list) { config.include.addAll(list); return this; }
	
	public SassTaskFactory embedSourceContentsInSourceMap(boolean val) { config.embedSourceContentsInSourceMap = val; return this; }
	public SassTaskFactory embedSourceContentsInSourceMap() { config.embedSourceContentsInSourceMap = true; return this; }
	
	public SassTaskFactory embedSourceMapInCSS(boolean val) { config.embedSourceMapInCSS = val; return this; }
	public SassTaskFactory embedSourceMapInCSS() { config.embedSourceMapInCSS = true; return this; }
	
	public SassTaskFactory generateSourceComments(boolean val) { config.generateSourceComments = val; return this; }
	public SassTaskFactory generateSourceComments() { config.generateSourceComments = true; return this; }
	
	public SassTaskFactory generateSourceMap(boolean val) { config.generateSourceMap = val; return this; }
	public SassTaskFactory generateSourceMap() { config.generateSourceMap = true; return this; }
	
	public SassTaskFactory omitSourceMapingURL(boolean val) { config.omitSourceMapingURL = val; return this; }
	public SassTaskFactory omitSourceMapingURL() { config.omitSourceMapingURL = true; return this; }
	
	public SassTaskFactory precision(int val) { config.precision = val; return this; }
	
	public SassTaskFactory inputSyntax(InputSyntax val) { config.inputSyntax = val; return this; }

	public SassTaskFactory outputStyle(OutputStyle val) { config.outputStyle = val; return this; }


}
