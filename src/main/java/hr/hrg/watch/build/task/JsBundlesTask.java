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
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.JsBundlesConfig;
import hr.hrg.watch.build.config.JsBundlesConfig.BundleEntry;

class JsBundlesTask extends AbstractTask<JsBundlesConfig>{

	public JsBundlesTask(JsBundlesConfig config, WatchBuild core) {
		super(config, core);
	}

	@Override
	public void init(boolean watch) {
		for(BundleEntry bundle:config.bundles) {			
			JsBundlesTask.Task task = new Task(config, bundle, core);
			task.start(watch);
			if(watch)
				core.addThread(new Thread(task,"JsBundle:"+bundle.name+"-to-"+config.root));
		}			
	}


static class Task implements Runnable{

	private JsBundlesConfig config;
	private Path rootPath;
	GlobWatcher watcher;
	private long maxLastModified;
	private BundleEntry bundle;
	private WatchBuild core;

	Task(JsBundlesConfig config, BundleEntry bundle, WatchBuild core){
		this.config = config;
		this.bundle = bundle;
		this.core = core;
	}
	
	
	public void start(boolean watch) {
		
		rootPath = Paths.get(config.root);			
		watcher = new GlobWatcher(rootPath,true);

		File root = watcher.getRootPath().toFile();
		
		for(String inc:bundle.include){
			if(inc.indexOf('*') == -1){
				//exact path, check existence and report error if not present (very useful if it is a typo)
				File file =  new File(root,inc);
				if(! file.exists()) core.logError("Bundle:"+bundle.name+" File not found: "+inc+" "+file.getAbsolutePath());
			}
		}
		
		watcher.includes(bundle.include);
		watcher.excludes(bundle.exclude);
		
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
						Main.logInfo("changed: "+changeEntry+" "+newModified);
						if(newModified > maxLastModified) maxLastModified = newModified;
					}
				}
				
				genBundle();
			}				
		} finally {
			watcher.close();
		}
	}
	
	private void genBundle() {
		
		List<JsBundlesTask.PathWithWeight> paths = new ArrayList<>();
		
		List<PathMatcher> includes = watcher.getIncludes();
		
		// code relies on fact that config.excludes and glob.getExcludes() to have the rules on same index in the list 
		for(Path path: watcher.getMatchedFiles()){
			int weight = -1;
			for(int i=0;i<includes.size(); i++){
				if(includes.get(i).matches(path)){
					// weight is the first rule that matches the path
					// rule without wildcards (exact path) has priority to enable putting some files to end of the list
					if(weight == -1 || bundle.include.get(i).indexOf('*') == -1){
						weight = i;
					}
				}
			}
			paths.add(new PathWithWeight(path,weight));
		}
		
		Collections.sort(paths);
		
		List<JsBundlesTask.PathWithWeight> pathsToBuild = new ArrayList<>();
		
		
		for(JsBundlesTask.PathWithWeight pw: paths){
			if(pw.path.getFileName().toString().endsWith(".json")) {
				fillFromBundle(pathsToBuild, pw.path, pw.weight);
			}else {
				pathsToBuild.add(pw);
			}
		}
		
		for(JsBundlesTask.PathWithWeight pw:pathsToBuild){
			File file = pw.path.toFile();
			if(file.lastModified() > maxLastModified) maxLastModified = file.lastModified();				
		}
		
		writeText(pathsToBuild);
		writeJson(pathsToBuild);

	}

	private void fillFromBundle(List<JsBundlesTask.PathWithWeight> pathsToBuild, Path bundleFile, int weight) {
		try {
			if(Main.VERBOSE  > 1) Main.logInfo("Bundle file "+bundleFile.toFile().getCanonicalPath());
			JsonNode bundle = core.getMapper().readTree(bundleFile.toFile());
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
			Main.logError("Error loading js bundle "+bundleFile.toAbsolutePath(), null);
		}		
	}

	private void writeJson(List<JsBundlesTask.PathWithWeight> paths) {
		String output = buildFileName("json");
		PrintWriter writer = null;
		try {			
			ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(byteOutput));

			writer.write("{\"name\":\""+bundle.name+"\"");
			writer.write(",\"files\":[");
			boolean first = true;
			Path jsPath = watcher.getRootPath();
			long totalSize = 0;
			for(JsBundlesTask.PathWithWeight pw:paths){
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
				Main.logInfo("Generating "+output);
				outputPath.toFile().setLastModified(maxLastModified);
			}else{
				if(Main.VERBOSE > 1) Main.logInfo("skip identical: "+output);
			}

		} catch (Exception e) {
			Main.logError("unable to write "+output,e);
			if(writer != null) writer.close();
		}
	}

	private String buildFileName(String ext){
		StringBuilder sb = new StringBuilder("bundle.").append(bundle.name);
		sb.append(".").append(ext);
		return sb.toString();
	}
	
	private void writeText(List<JsBundlesTask.PathWithWeight> paths) {
		if(!config.outputText) return;
		String outputText = buildFileName("txt");
		
		PrintWriter writer = null;
		try {
			ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(byteOutput));

			Path jsPath = watcher.getRootPath();
			for(JsBundlesTask.PathWithWeight pw:paths){
				writer.println(jsPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
			}
			writer.close();

			if(TaskUtils.writeFile(rootPath.resolve(outputText), byteOutput.toByteArray(), config.compareBytes, maxLastModified)){
				Main.logInfo("Generating "+outputText);
			}else{
				if(Main.VERBOSE > 1) Main.logInfo("skip identical: "+outputText);
			}
		} catch (Exception e) {
			Main.logError("unable to write "+outputText,e);
			if(writer != null) writer.close();
		}
	}

}

static class PathWithWeight implements Comparable<JsBundlesTask.PathWithWeight>{

	private Path path;
	private int weight;

	public PathWithWeight(Path path, int weight) {
		this.path = path;
		this.weight = weight;
	}

	@Override
	public int compareTo(JsBundlesTask.PathWithWeight o) {
		return weight - o.weight;
	}
}
}