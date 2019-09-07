package hr.hrg.watch.build.task;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.javawatcher.WatchUtil;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.JsBundlesConfig;
import hr.hrg.watch.build.config.TaskDef;

public class JsBundlesTaskFactory extends AbstractTaskFactory {

	Logger log = LoggerFactory.getLogger(JsBundlesTaskFactory.class);

	Map<Path, SourceFile> codeCache = new HashMap<>();	

	public JsBundlesTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core,mapper);
	}
	
	@Override
	public void startOne(TaskDef taskDef, String lang, JsonNode root, boolean watch){
		JsBundlesConfig config = mapper.convertValue(root, JsBundlesConfig.class);
		
//		if(config.compareBytes == null) config.compareBytes = config.defaults.compareBytes;
//		if(config.root == null) config.root = config.defaults.root;
//		if(config.jsRoot == null) config.jsRoot = config.defaults.jsRoot;
//		if(config.srcRoot == null) config.srcRoot = config.defaults.srcRoot;
//		if(config.outputJS == null) config.outputJS = config.defaults.outputJS;
//		if(config.outputText == null) config.outputText = config.defaults.outputText;
//		if(config.compilationLevel == null) config.compilationLevel = config.defaults.compilationLevel;
		
		Task task = new Task(config, lang);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"JsBundle:"+config.name+"-to-"+config.root));
	}

	class Task implements Runnable{

		private JsBundlesConfig config;
		private Path rootPath;
		GlobWatcher watcher;
		private String lang;
		private long maxLastModified;

		Task(JsBundlesConfig config, String lang){
			this.config = config;
			this.lang = lang;
			
		}
		
		public void start(boolean watch) {
			if(TaskUtils.isNoOutput(config.root)) return;
			
			rootPath = Paths.get(config.root);			
			watcher = new GlobWatcher(rootPath,true);
	
			File root = watcher.getRootPath().toFile();
			
			for(String inc:config.include){
				if(inc.indexOf('*') == -1){
					//exact path, check existence and report error if not present (very useful if it is a typo)
					File file =  new File(root,inc);
					if(! file.exists()) core.logError(log,"Bundle:"+config.name+" File not found: "+inc+" "+file.getAbsolutePath());
				}
			}
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);
			
			// when input watcher is started, all matchers that were added to it are 
			// filled with files found that are (included + not excluded)
			watcher.init(watch);
			
			genBundle();
		}
	
		
		@Override
		public void run() {
	
			try {
				Collection<Path> changed = null;
				while(!Thread.interrupted()){
					
					changed = watcher.takeBatchFilesUnique(core.getBurstDelay());
					if(changed == null) break; // interrupted, thread should stop, stop the loop
					
					// clear changed files from cache
					for (Path changeEntry : changed) {
						if(Main.VERBOSE > 1){
							long newModified = changeEntry.toFile().lastModified();
							log.info("changed: "+changeEntry+" "+newModified);
							if(newModified > maxLastModified) maxLastModified = newModified;
						}
						codeCache.remove(changeEntry);
					}
					
					genBundle();
				}				
			} finally {
				watcher.close();
			}
		}
		
		private void genBundle() {
			if(TaskUtils.isNoOutput(config.root)) return;
			
			List<PathWithWeight> paths = new ArrayList<JsBundlesTaskFactory.PathWithWeight>();
			
			List<PathMatcher> includes = watcher.getIncludes();
			
			// code relies on fact that config.excludes and glob.getExcludes() to have the rules on same index in the list 
			for(Path path: watcher.getMatchedFiles()){
				int weight = -1;
				for(int i=0;i<includes.size(); i++){
					if(includes.get(i).matches(path)){
						// weight is the first rule that matches the path
						// rule without wildcards (exact path) has priority to enable putting some files to end of the list
						if(weight == -1 || config.include.get(i).indexOf('*') == -1){
							weight = i;
						}
					}
				}
				paths.add(new PathWithWeight(path,weight));
			}
			
			Collections.sort(paths);
			
			List<PathWithWeight> pathsToBuild = new ArrayList<JsBundlesTaskFactory.PathWithWeight>();
			
			
			for(PathWithWeight pw: paths){
				if(pw.path.getFileName().toString().endsWith(".json")) {
					fillFromBundle(pathsToBuild, pw.path, pw.weight);
				}else {
					pathsToBuild.add(pw);
				}
			}
			
			for(PathWithWeight pw:pathsToBuild){
				File file = pw.path.toFile();
				if(file.lastModified() > maxLastModified) maxLastModified = file.lastModified();				
			}
			
			writeText(pathsToBuild);
			writeJson(pathsToBuild);
			writeJS(pathsToBuild);
	
		}
	
		private void fillFromBundle(List<PathWithWeight> pathsToBuild, Path bundleFile, int weight) {
			try {
				if(Main.VERBOSE  > 1) log.trace("Bundle file "+bundleFile.toFile().getCanonicalPath());
				JsonNode bundle = mapper.readTree(bundleFile.toFile());
				ArrayNode files = (ArrayNode) bundle.get("files");
				for(JsonNode scriptNode: files) {
					String scriptFile = scriptNode.get("script").asText();
					Path scriptPath = bundleFile.resolveSibling(scriptFile);
					if(scriptFile.endsWith(".json")){
						fillFromBundle(pathsToBuild, scriptPath, weight);
					}else {
						pathsToBuild.add(new PathWithWeight(scriptPath, weight));					
					}
				}
			} catch (Exception e) {
				log.error("Error loading js bundle "+bundleFile.toAbsolutePath());
			}		
		}
	
		private void writeJS(List<PathWithWeight> paths) {
			if(!config.outputJS) return;
			if(!WatchUtil.classAvailable("com.google.javascript.jscomp.Compiler")) {
				throw new ConfigException("JsBundles compiling javascript is not avaiable due to missing dependecy com.google.javascript:closure-compiler (download full shaded version to fix or set outputJS: false in the configuration)",null);
			}			
			long start = System.currentTimeMillis();
	
			String outputJS = buildFileName("js");
			
			PrintWriter writer = null;
			PrintWriter writer2 = null;
			try {			
				log.info("Generating "+outputJS);
				com.google.javascript.jscomp.Compiler compiler = new com.google.javascript.jscomp.Compiler();
				CompilerOptions options = new CompilerOptions();
				options.sourceMapOutputPath = outputJS+".map";
	
				CompilationLevel level = CompilationLevel.fromString(config.compilationLevel.toUpperCase());
				if(level == null){
					log.error("Unknown compilation level "+config.compilationLevel);
				}
				level.setOptionsForCompilationLevel(options);
				
				Path jsPath = watcher.getRootPath();
				
				// To get the complete set of externs, the logic in
			    // CompilerRunner.getDefaultExterns() should be used here.
			    // JSSourceFile extern = JSSourceFile.fromCode("externs.js","function alert(x) {}");			
	
				List<SourceFile> externs = new ArrayList<SourceFile>();
				List<SourceFile> inputs = new ArrayList<SourceFile>();
				
				for(PathWithWeight pw:paths){
					
					SourceFile sourceFile = codeCache.get(pw.path);
					if(sourceFile == null){					
						sourceFile = SourceFile.builder().withOriginalPath(pw.path.toString()).buildFromFile(pw.path.toFile());
						codeCache.put(pw.path, sourceFile);
					}
	
					inputs.add(sourceFile);
				}
	
				Result result = compiler.compile(externs, inputs, options);
	
				ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
				writer = new PrintWriter(new OutputStreamWriter(byteOutput));
	
				writer.write(compiler.toSource());
				writer.println();
				writer.write("//# sourceMappingURL="+options.sourceMapOutputPath);
				writer.close();
	
				if(TaskUtils.writeFile(rootPath.resolve(outputJS), byteOutput.toByteArray(), config.compareBytes, maxLastModified)){
					log.info("Generating "+outputJS+" DONE "+(System.currentTimeMillis()-start)+"ms");
					log.info("Generating "+options.sourceMapOutputPath);
					start = System.currentTimeMillis();
					writer2 = new PrintWriter(rootPath.resolve(options.sourceMapOutputPath).toFile());
					result.sourceMap.appendTo(writer2, options.sourceMapOutputPath);
					writer2.close();
					log.info("Generating "+options.sourceMapOutputPath+" DONE "+(System.currentTimeMillis()-start)+"ms");	
					
				}else{
					if(Main.VERBOSE > 1) log.trace("skip identical: "+outputJS);
				}

			} catch (Exception e) {
				log.error("unable to write "+outputJS,e);
				if(writer != null)  writer.close();
				if(writer2 != null) writer2.close();
			}
		}
	
		private void writeJson(List<PathWithWeight> paths) {
			String output = buildFileName("json");
			PrintWriter writer = null;
			try {			
				ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
				writer = new PrintWriter(new OutputStreamWriter(byteOutput));
	
				writer.write("{\"name\":\""+config.name+"\"");
				writer.write(",\"files\":[");
				boolean first = true;
				Path jsPath = watcher.getRootPath();
				long totalSize = 0;
				for(PathWithWeight pw:paths){
					if(!first){
						writer.write(',');
					}
					writer.write("{\"script\":\"");
					writer.write(jsPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
					writer.write("\"");
					File file = pw.path.toFile();
					writer.write(",\"modified\":"+file.lastModified());
					writer.write(",\"size\":"+file.length());
					writer.write("}");
					totalSize += file.length();
					first = false;
				}
				writer.write("]");
				writer.write(",\"size\":"+totalSize);
				writer.write("}");
				writer.close();
	
				Path outputPath = rootPath.resolve(output);
				if(TaskUtils.writeFile(outputPath, byteOutput.toByteArray(), config.compareBytes, maxLastModified)){
					log.info("Generating "+output);
					outputPath.toFile().setLastModified(maxLastModified);
				}else{
					if(Main.VERBOSE > 1) log.trace("skip identical: "+output);
				}
	
			} catch (Exception e) {
				log.error("unable to write "+output,e);
				if(writer != null) writer.close();
			}
		}
	
		private String buildFileName(String ext){
			StringBuilder sb = new StringBuilder("bundle.").append(config.name);
			if(config.perLanguage)
				sb.append(".").append(lang);
			sb.append(".").append(ext);
			return sb.toString();
		}
		
		private void writeText(List<PathWithWeight> paths) {
			if(!config.outputText) return;
			String outputText = buildFileName("txt");
			
			PrintWriter writer = null;
			try {
				ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
				writer = new PrintWriter(new OutputStreamWriter(byteOutput));
	
				Path jsPath = watcher.getRootPath();
				for(PathWithWeight pw:paths){
					writer.println(jsPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
				}
				writer.close();
	
				if(TaskUtils.writeFile(rootPath.resolve(outputText), byteOutput.toByteArray(), config.compareBytes, maxLastModified)){
					log.info("Generating "+outputText);
				}else{
					if(Main.VERBOSE > 1) log.trace("skip identical: "+outputText);
				}
			} catch (Exception e) {
				log.error("unable to write "+outputText,e);
				if(writer != null) writer.close();
			}
		}

	}

	static class PathWithWeight implements Comparable<PathWithWeight>{

		private Path path;
		private int weight;

		public PathWithWeight(Path path, int weight) {
			this.path = path;
			this.weight = weight;
		}

		@Override
		public int compareTo(PathWithWeight o) {
			return weight - o.weight;
		}
	}
	
}
