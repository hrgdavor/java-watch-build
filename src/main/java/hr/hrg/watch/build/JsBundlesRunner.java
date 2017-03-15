package hr.hrg.watch.build;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceFile;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;

public class JsBundlesRunner implements Runnable{

	Logger log = LoggerFactory.getLogger(JsBundlesRunner.class);

	GlobWatcher watcher;

	Map<Path, SourceFile> codeCache = new HashMap<>();
	
	private JsBundlesConfig.Item config;
	private boolean compareBytes = true;

	// burst update wait time
	private int burstDelay = 50;

	private ObjectMapper mapper;

	public JsBundlesRunner(JsBundlesConfig.Item config, ObjectMapper mapper){
		this.config = config;
		this.mapper = mapper;
	}

	public void start(boolean watch) {
		watcher = new GlobWatcher(Paths.get(config.root),true);

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

	private void genBundle() {
		List<PathWithWeight> paths = new ArrayList<JsBundlesRunner.PathWithWeight>();
		
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
		
		List<PathWithWeight> pathsToBuild = new ArrayList<JsBundlesRunner.PathWithWeight>();
		
		
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
		if(BuildRunner.isNoOutput(config.outputJS)) return;
		long start = System.currentTimeMillis();

		PrintWriter writer = null;
		PrintWriter writer2 = null;
		try {			
			log.info("Generating "+config.outputJS);
			com.google.javascript.jscomp.Compiler compiler = new com.google.javascript.jscomp.Compiler();
			CompilerOptions options = new CompilerOptions();
			options.sourceMapOutputPath = config.outputJS+".map";

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
			writer.write("//# sourceMappingURL="+new File(options.sourceMapOutputPath).getName());
			writer.close();

			if(TaskUtils.writeFile(Paths.get(config.outputJS), byteOutput.toByteArray(), compareBytes)){
				log.info("Generating "+config.output+" DONE "+(System.currentTimeMillis()-start)+"ms");
				log.info("Generating "+options.sourceMapOutputPath);
				start = System.currentTimeMillis();
				writer2 = new PrintWriter(options.sourceMapOutputPath);
				result.sourceMap.appendTo(writer2, options.sourceMapOutputPath);
				writer2.close();
				log.info("Generating "+options.sourceMapOutputPath+" DONE "+(System.currentTimeMillis()-start)+"ms");				
			}else{
				log.trace("skip identical: "+config.outputJS);
			}
			
		} catch (Exception e) {
			log.error("unable to write "+config.outputJS,e);
			if(writer != null)  writer.close();
			if(writer2 != null) writer2.close();
		}
	}

	private void writeJson(List<PathWithWeight> paths) {
		if(BuildRunner.isNoOutput(config.output)) return;
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

			if(TaskUtils.writeFile(Paths.get(config.output), byteOutput.toByteArray(), compareBytes)){
				log.info("Generating "+config.output);
			}else{
				log.trace("skip identical: "+config.output);
			}

		} catch (Exception e) {
			log.error("unable to write "+config.output,e);
			if(writer != null) writer.close();
		}
	}

	private void writeText(List<PathWithWeight> paths) {
		if(BuildRunner.isNoOutput(config.outputText)) return;

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

			if(TaskUtils.writeFile(Paths.get(config.outputText), byteOutput.toByteArray(), compareBytes)){
				log.info("Generating "+config.outputText);
			}else{
				log.trace("skip identical: "+config.outputText);
			}
		} catch (Exception e) {
			log.error("unable to write "+config.outputText,e);
			if(writer != null) writer.close();
		}
	}
	
	@Override
	public void run() {

		// single thread version that uses FolderWatcher.takeBatch(burstDelay) to wait for more burst changes
		// before processing the changed files

		Collection<FileChangeEntry<FileMatchGlob>> changed = null;
		while(!Thread.interrupted()){

			changed = watcher.takeBatch(burstDelay);
			if(changed == null) break; // interrupted, thread should stop, stop the loop

			// clear changed files from cache
			for (FileChangeEntry<FileMatchGlob> changeEntry : changed) {
				if(log.isInfoEnabled())	log.info("changed: "+changeEntry+" "+changeEntry.getPath().toFile().lastModified());
				codeCache.remove(changeEntry.getPath());
			}

			genBundle();
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
	
	public void setCompareBytes(boolean compareBytes) {
		this.compareBytes = compareBytes;
	}
	
	public boolean isCompareBytes() {
		return compareBytes;
	}
	
}
