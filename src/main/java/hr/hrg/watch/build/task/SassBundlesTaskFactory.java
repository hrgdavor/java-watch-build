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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.SassBundlesConfig;

public class SassBundlesTaskFactory extends AbstractTaskFactory {

	Logger log = LoggerFactory.getLogger(SassBundlesTaskFactory.class);

	public SassBundlesTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core,mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch){
		SassBundlesConfig config = mapper.convertValue(root, SassBundlesConfig.class);
		
		Task task = new Task(config, lang);
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"SassBundle:"+config.output));
	}

	class Task implements Runnable{

		private SassBundlesConfig config;
		private Path rootPath;
		GlobWatcher watcher;

		Task(SassBundlesConfig config2, String lang){
			this.config = config2;			
		}
		
		public void start(boolean watch) {
			if(TaskUtils.isNoOutput(config.output)) return;
			
			if(config.root == null) {
				rootPath = Paths.get(config.output).getParent();
			}else
				rootPath = Paths.get(config.root);
			
			watcher = new GlobWatcher(rootPath,true);
	
			File root = watcher.getRootPath().toFile();
			for(String inc:config.include){
				if(inc.indexOf('*') == -1){
					//exact path, check existence and report error if not present (very useful if it is a typo)
					if(! new File(root,inc).exists()) log.error("SassBundle:"+config.output+" File not found: "+inc);
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
					}
					
					genBundle();
				}				
			} finally {
				watcher.close();
			}
		}
		
		private void genBundle() {
			if(TaskUtils.isNoOutput(config.root)) return;
			
			
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
					log.info("Generating "+config.output);
				}else{
					log.trace("skip identical: "+config.output);
				}
			} catch (Exception e) {
				log.error("unable to write "+config.output,e);
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
