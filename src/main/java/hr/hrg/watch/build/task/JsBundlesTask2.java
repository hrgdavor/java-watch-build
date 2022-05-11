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
import hr.hrg.watch.build.FileDef;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.JsBundlesConfig;
import hr.hrg.watch.build.config.JsBundlesConfig.BundleEntry;
import io.methvin.watcher.DirectoryChangeEvent.EventType;

class JsBundlesTask2 extends AbstractTask<JsBundlesConfig>{

	public JsBundlesTask2(JsBundlesConfig config, WatchBuild core) {
		super(config, core);
		for(BundleEntry bundle:config.bundles) {			
			JsBundlesTask2.Task task = new Task(config, bundle, core);
		}			
	}

	@Override
	public void init(boolean watch) {
	}
	
	@Override
	public String toString() {
		return "JsBundlesTask:"+config.root;
	}
	
	@Override
	public boolean needsThread() {
		return false;
	}
	static class Task extends AbstractTask<JsBundlesConfig>{

	private long maxLastModified;
	private BundleEntry bundle;

	Task(JsBundlesConfig config, BundleEntry bundle, WatchBuild core){
		super(config, core, config.root, true);
		this.bundle = bundle;

		File root = rootPath.toFile();
		
		for(String inc:bundle.include){
			if(inc.indexOf('*') == -1){
				//exact path, check existence and report error if not present (very useful if it is a typo)
				File file =  new File(root,inc);
				if(! file.exists()) core.logError("Bundle:"+bundle.name+" File not found: "+inc+" "+file.getAbsolutePath());
			}
		}
		
		includes(bundle.include);
		excludes(bundle.exclude);
		
		core.addWatcherTask(this);
	}

	@Override
	public boolean needsThread() {
		return false;
	}

	@Override
	public void init(boolean watch) {
		genBundle(true);
	}

	@Override
	protected void matched(FileDef def, Path relative, boolean initial) {
//		super.matched(eventType, path, relative, initial);
		if(initial) return;
		long newModified = def.lastModified;
		hr.hrg.javawatcher.Main.logInfo("changed: "+def.path+" "+newModified);
		if(newModified > maxLastModified) maxLastModified = newModified;
		genBundle(false);
	}
	
	private void genBundle(boolean init) {
		
		List<JsBundlesTask2.PathWithWeight> paths = new ArrayList<>();
		
		List<PathMatcher> includes = getIncludes();
		
		// code relies on fact that config.excludes and glob.getExcludes() to have the rules on same index in the list 
		for(Path path: getMatched()){
			Path relative = rootPath.relativize(path);
			int weight = -1;
			for(int i=0;i<includes.size(); i++){
				if(includes.get(i).matches(relative)){
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
		
		List<JsBundlesTask2.PathWithWeight> pathsToBuild = new ArrayList<>();
		
		
		for(JsBundlesTask2.PathWithWeight pw: paths){
			if(pw.path.getFileName().toString().endsWith(".json")) {
				fillFromBundle(pathsToBuild, pw.path, pw.weight);
			}else {
				pathsToBuild.add(pw);
			}
		}
		
		for(JsBundlesTask2.PathWithWeight pw:pathsToBuild){
			File file = pw.path.toFile();
			if(!file.exists()) throw new RuntimeException("File not found "+file);
			if(file.lastModified() > maxLastModified) maxLastModified = file.lastModified();				
		}
		
		writeText(pathsToBuild, init);
		writeJson(pathsToBuild, init);

	}

	private void fillFromBundle(List<JsBundlesTask2.PathWithWeight> pathsToBuild, Path bundleFile, int weight) {
		try {
			System.err.println("Bundle file "+bundleFile.toFile().getCanonicalPath());
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("Bundle file "+bundleFile.toFile().getCanonicalPath());
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
			hr.hrg.javawatcher.Main.logError("Error loading js bundle "+bundleFile.toAbsolutePath()+" "+e.getMessage(), null);
		}		
	}

	private void writeJson(List<JsBundlesTask2.PathWithWeight> paths, boolean init) {
		String output = buildFileName("json");
		PrintWriter writer = null;
		try {			
			ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(byteOutput));

			writer.write("{\"name\":\""+bundle.name+"\"");
			writer.write(",\"files\":[");
			boolean first = true;
			Path jsPath = rootPath;
			long totalSize = 0;
			for(JsBundlesTask2.PathWithWeight pw:paths){
				if(!first){
					writer.write(',');
				}
				writer.write("{\"script\":\"");
				writer.write(jsPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
				writer.write("\"");
				File file = pw.path.toFile();
				if(!file.exists()) throw new RuntimeException("File not found "+file);
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
			if(TaskUtils.writeFile(outputPath, byteOutput.toByteArray(), config.compareBytes, maxLastModified, !init)){
				hr.hrg.javawatcher.Main.logInfo("Generating "+output);
				outputPath.toFile().setLastModified(maxLastModified);
			}else{
				if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("skip identical: "+output);
			}

		} catch (Exception e) {
			hr.hrg.javawatcher.Main.logError("unable to write "+output,e);
			if(writer != null) writer.close();
		}
	}

	private String buildFileName(String ext){
		StringBuilder sb = new StringBuilder("bundle.").append(bundle.name);
		sb.append(".").append(ext);
		return sb.toString();
	}
	
	private void writeText(List<JsBundlesTask2.PathWithWeight> paths, boolean init) {
		if(!config.outputText) return;
		String outputText = buildFileName("txt");
		
		PrintWriter writer = null;
		try {
			ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(byteOutput));

			Path jsPath = rootPath;
			for(JsBundlesTask2.PathWithWeight pw:paths){
				writer.println(jsPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\""));
			}
			writer.close();

			if(TaskUtils.writeFile(rootPath.resolve(outputText), byteOutput.toByteArray(), config.compareBytes, maxLastModified, !init)){
				hr.hrg.javawatcher.Main.logInfo("Generating "+outputText);
			}else{
				if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("skip identical: "+outputText);
			}
		} catch (Exception e) {
			hr.hrg.javawatcher.Main.logError("unable to write "+outputText,e);
			if(writer != null) writer.close();
		}
	}

	@Override
	public String toString() {
		return "JSBundle:"+bundle.name;
	}
}

static class PathWithWeight implements Comparable<JsBundlesTask2.PathWithWeight>{

	private Path path;
	private int weight;

	public PathWithWeight(Path path, int weight) {
		this.path = path;
		this.weight = weight;
	}

	@Override
	public int compareTo(JsBundlesTask2.PathWithWeight o) {
		return weight - o.weight;
	}
	
	@Override
	public String toString() {
		return "["+weight+"]"+path;
	}
}
}