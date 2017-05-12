package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import wrm.libsass.SassCompiler.InputSyntax;
import wrm.libsass.SassCompiler.OutputStyle;

public class SassConfig {
	public String input;
	public String output;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	public OutputStyle outputStyle = OutputStyle.expanded;

	public boolean embedSourceMapInCSS = false;
	public boolean embedSourceContentsInSourceMap = false;
	public boolean generateSourceComments = false;
	public boolean generateSourceMap = false;
	public InputSyntax inputSyntax = InputSyntax.scss;
	public boolean omitSourceMapingURL = false;
	public int precision = 5;
	
}