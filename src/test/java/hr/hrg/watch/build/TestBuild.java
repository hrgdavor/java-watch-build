package hr.hrg.watch.build;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.task.JsBundlesTaskFactory;
import wrm.libsass.SassCompiler.OutputStyle;

public class TestBuild {
	static JavaWatchBuild wb = new JavaWatchBuild();

	public static void main(String[] args) {
		
		String gen_root = "deploy/_gen";
		String collect_root =  "deploy/collect";
		String dev_root=  "deploy/dev";
		String dev_www =  dev_root+"/www";
		String[] languages = new String[] {"en"};
		
		List<String> componentLibs = toList("tdp","util","parts","props");
		List<String> bundles = toList("lib","app"); 
		bundles.addAll(componentLibs);

		wb.doCopy("src/js", collect_root+"/js");

		wb.doExt("node","ext/babel-transform.js")
		  .input(collect_root+"/js")
		  .output(gen_root+"/js")
		  .srcRoot("src")
		  .options("minify",true)
		  .include("**.js", "**/*.tpl");
		
		for(String lang:languages) {
			wb.doLang("src/lang/"+lang+".yml")
				.output(gen_root+"/js/"+lang+".js")
				.output(gen_root+"/js/"+lang+".json");
		}

		JsBundlesTaskFactory bundlesTask = wb.doJsBundles(gen_root);

		bundlesTask.add("lib",
		    "js/mi2/mi2.js",
		    "js/mi2/NWGroup.js",
		    "js/mi2/*.js",
		    "js/mi2/Base.js",
		    "js/mi2/base/**.js"
		).exclude(
		    "js/mi2/base/TabPane.js",
		    "js/mi2/base/RenderTable.js",
		    "js/mi2/base/Pager.js",
		    "js/mi2/base/MultiInput.js"
		);
		bundlesTask.add("app","js/site.js");

		for(String name: componentLibs) {
		  bundlesTask.add(name,"js/"+name+"/*.js"); 
		}

		wb.doScss("src/scss/style.scss",dev_www, OutputStyle.expanded, false, true)
		  .include("src/scss", "node_modules/bourbon/core");
		
		wb.doExt("node","ext/concat.js")
			.input(gen_root+"/")
			.output(gen_root+"/")
			.options("minify",false)
			.include("bundle.*.json");
		
		wb.doCopy("src/js",    dev_www+"/src");
		wb.doCopy("src/html",  dev_www).exclude("index.html");
		wb.doCopy("src/font/fonts", dev_www+"/fonts").exclude("**/placeholder.txt");
 
		List<String> bundleLibs = map(bundles, lib -> "bundle."+lib+".js");

		wb.doCopy(gen_root, dev_www)
		  .include(map(languages, lang -> "js/"+lang+".js"))
		  .include(bundleLibs)
		  .include(map(bundleLibs, bundle -> bundle+".map"));
		

		wb.doHtmlScriptAndCss("src/html/index.html",dev_www+"/index.html")
		  .scriptVariable("SCRIPTS")
		  .scripts(bundleLibs)
		  .css("style.css");
		
		wb.doLiveReload(dev_www).include("index.html","style.css")
			.pauseAfterCss(200);
		
		wb.setVerbose(2);
		wb.start(true);
	}
	
}
