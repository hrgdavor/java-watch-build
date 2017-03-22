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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.config.JsBundlesConfig;

public class JsBundlesTaskFactory extends AbstractTaskFactory {

	Logger log = LoggerFactory.getLogger(JsBundlesTaskFactory.class);

	Map<Path, SourceFile> codeCache = new HashMap<>();	

	@Inject
	public JsBundlesTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core,mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch){
		JsBundlesConfig config = mapper.convertValue(root, JsBundlesConfig.class);
		
		Task task = new Task(config);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"JsBundle:"+config.name+"-to-"+config.root));
	}

	class Task implements Runnable{

		private JsBundlesConfig config;
		private Path rootPath;
		GlobWatcher watcher;

		Task(JsBundlesConfig config){
			this.config = config;
			
		}
		
		public void start(boolean watch) {
			if(TaskUtils.isNoOutput(config.root)) return;
					
			rootPath = Paths.get(config.root);
			
			watcher = new GlobWatcher(rootPath,true);
	
			File root = watcher.getRootPath().toFile();
			for(String inc:config.include){
				if(inc.indexOf('*') == -1){
					//exact path, check existence and report error if not present (very useful if it is a typo)
					if(! new File(root,inc).exists()) log.error("Bundle:"+config.name+" File not found: "+inc);
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
				Collection<FileChangeEntry<FileMatchGlob>> changed = null;
				while(!Thread.interrupted()){
					
					changed = watcher.takeBatch(core.getBurstDelay());
					if(changed == null) break; // interrupted, thread should stop, stop the loop
					
					// clear changed files from cache
					for (FileChangeEntry<FileMatchGlob> changeEntry : changed) {
						if(log.isInfoEnabled())	log.info("changed: "+changeEntry+" "+changeEntry.getPath().toFile().lastModified());
						codeCache.remove(changeEntry.getPath());
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
			
			
			writeText(pathsToBuild);
			writeJson(pathsToBuild);
			writeJS(pathsToBuild);
	
		}
	
		private void fillFromBundle(List<PathWithWeight> pathsToBuild, Path bundleFile, int weight) {
			try {
				JsonNode bundle = mapper.readTree(bundleFile.toFile());
				ArrayNode files = (ArrayNode) bundle.get("files");
				for(JsonNode scriptNode: files) {
					String scriptFile = scriptNode.get("script").asText();
					Path scriptPath = bundleFile.resolveSibling(scriptFile);
					long modified = scriptNode.get("modified").asLong();
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
				
				Path rootPath = watcher.getRootPath();
				
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
	
				if(TaskUtils.writeFile(rootPath.resolve(outputJS), byteOutput.toByteArray(), config.compareBytes)){
					log.info("Generating "+outputJS+" DONE "+(System.currentTimeMillis()-start)+"ms");
					log.info("Generating "+options.sourceMapOutputPath);
					start = System.currentTimeMillis();
					writer2 = new PrintWriter(rootPath.resolve(options.sourceMapOutputPath).toFile());
					result.sourceMap.appendTo(writer2, options.sourceMapOutputPath);
					writer2.close();
					log.info("Generating "+options.sourceMapOutputPath+" DONE "+(System.currentTimeMillis()-start)+"ms");				
				}else{
					log.trace("skip identical: "+outputJS);
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
	
				writer.write("{\"name\":\""+config.name+"\",\"files\":[");
				boolean first = true;
				Path rootPath = watcher.getRootPath();
				for(PathWithWeight pw:paths){
					if(!first){
						writer.write(',');
					}
					writer.write("{\"script\":\"");
					writer.write(rootPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
					writer.write("\",\"modified\":"+pw.path.toFile().lastModified());
					writer.write("}");
					first = false;
				}
				writer.write("]}");
				writer.close();
	
				if(TaskUtils.writeFile(rootPath.resolve(output), byteOutput.toByteArray(), config.compareBytes)){
					log.info("Generating "+output);
				}else{
					log.trace("skip identical: "+output);
				}
	
			} catch (Exception e) {
				log.error("unable to write "+output,e);
				if(writer != null) writer.close();
			}
		}
	
		private String buildFileName(String ext){
			StringBuilder sb = new StringBuilder("bundle.").append(config.name);
			if(config.suffix != null && !"".equals(config.suffix)) {
				sb.append(".").append(config.suffix);
			}
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
	
				writer.println(config.name);
				Path rootPath = watcher.getRootPath();
				for(PathWithWeight pw:paths){
					writer.println(rootPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
				}
				writer.close();
	
				if(TaskUtils.writeFile(rootPath.resolve(outputText), byteOutput.toByteArray(), config.compareBytes)){
					log.info("Generating "+outputText);
				}else{
					log.trace("skip identical: "+outputText);
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
