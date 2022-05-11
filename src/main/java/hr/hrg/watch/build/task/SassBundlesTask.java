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

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.SassBundlesConfig;
import hr.hrg.watch.build.task.SassBundlesTaskFactory.PathWithWeight;

class SassBundlesTask extends AbstractTask<SassBundlesConfig> implements Runnable{

	private Path rootPath;
	GlobWatcher<Object> watcher;

	SassBundlesTask(SassBundlesConfig config, WatchBuild core){
		super(config, core);
	}

	public void init(boolean watch) {
		
		if(config.root == null) {
			rootPath = Paths.get(config.output).getParent();
		}else
			rootPath = Paths.get(config.root);
		
		watcher = new GlobWatcher<>(rootPath,true);

		File root = watcher.getRootPath().toFile();
		for(String inc:config.include){
			if(inc.indexOf('*') == -1){
				//exact path, check existence and report error if not present (very useful if it is a typo)
				if(! new File(root,inc).exists()) hr.hrg.javawatcher.Main.logError("SassBundle:"+config.output+" File not found: "+inc, null);
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
	public boolean needsThread() {
		return true;
	}

	@Override
	public void run() {

		try {
			Collection<FileChangeEntry<Object>> changed = null;
			while(!Thread.interrupted()){
				
				changed = watcher.takeBatch(core.getBurstDelay());
				if(changed == null) break; // interrupted, thread should stop, stop the loop
				
				// clear changed files from cache
				for (FileChangeEntry<Object> changeEntry : changed) {
					if(hr.hrg.javawatcher.Main.isInfoEnabled())	hr.hrg.javawatcher.Main.logInfo("changed: "+changeEntry+" "+changeEntry.getPath().toFile().lastModified());
				}
				
				genBundle();
			}				
		} finally {
			watcher.close();
		}
	}
	
	private void genBundle() {
		
		List<PathWithWeight> paths = new ArrayList<SassBundlesTaskFactory.PathWithWeight>();
		
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
		
		List<PathWithWeight> pathsToBuild = new ArrayList<SassBundlesTaskFactory.PathWithWeight>();
		
		
		for(PathWithWeight pw: paths){
			if(pw.path.getFileName().toString().endsWith(".json")) {
				fillFromBundle(pathsToBuild, pw.path, pw.weight);
			}else {
				pathsToBuild.add(pw);
			}
		}
		
		
		writeSass(pathsToBuild);	
	}

	private void fillFromBundle(List<PathWithWeight> pathsToBuild, Path bundleFile, int weight) {
		try {
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
			hr.hrg.javawatcher.Main.logError("Error loading js bundle "+bundleFile.toAbsolutePath(),null);
		}		
	}

	
	private void writeSass(List<PathWithWeight> paths) {
		
		PrintWriter writer = null;
		try {
			ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
			writer = new PrintWriter(new OutputStreamWriter(byteOutput));

			long maxLastModify = 0;
			Path rootPath = watcher.getRootPath();
			for(PathWithWeight pw:paths){
				long mod = pw.path.toFile().lastModified();
				if(mod > maxLastModify) maxLastModify = mod;
				writer.println("@import \""+rootPath.relativize(pw.path).toString().replace('\\', '/').replaceAll("\"", "\\\"")+"\";");
			}
			writer.close();

			if(TaskUtils.writeFile(Paths.get(config.output), byteOutput.toByteArray(), config.compareBytes, maxLastModify)){
				if(hr.hrg.javawatcher.Main.isInfoEnabled())	hr.hrg.javawatcher.Main.logInfo("Generating "+config.output);
			}else{
				if(hr.hrg.javawatcher.Main.isInfoEnabled())	hr.hrg.javawatcher.Main.logInfo("skip identical: "+config.output);
			}
		} catch (Exception e) {
			hr.hrg.javawatcher.Main.logError("unable to write "+config.output,e);
			if(writer != null) writer.close();
		}
	}
	
	@Override
	public String toString() {
		return "SassBundle:"+config.output;
	}
}