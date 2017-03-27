package hr.hrg.watch.build.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.config.HtmlScriptAndCssConfig;

public class HtmlScriptAndCssTaskFactory extends AbstractTaskFactory{

	Logger log = LoggerFactory.getLogger(HtmlScriptAndCssTaskFactory.class);
	
	@Inject
	public HtmlScriptAndCssTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(String inlineParam, String lang, JsonNode root, boolean watch) {
		HtmlScriptAndCssConfig config = mapper.convertValue(root, HtmlScriptAndCssConfig.class);

		Task task = new Task(config, core.getOutputRoot());
		task.start(watch);
		if(watch)
			core.addThread(new Thread(task,"HtmlAndScript:"+config.input+" to "+config.output));

	}
	
	class Task implements Runnable {	
		private GlobWatcher watcher;
		private HtmlScriptAndCssConfig config;
		private Collection<Path> htmlFiles;
	
		List<Object> scripts = new ArrayList<>();
		List<ScriptEntry> cssScripts = new ArrayList<>();
	
		private GlobWatcher scriptsToWatch;
	
		private File scriptRoot;
		
		public Task(HtmlScriptAndCssConfig config, Path outputRoot){
			this.config = config;
			if(outputRoot == null) throw new NullPointerException("outputRoot can not be null");
			
			scriptRoot = TaskUtils.getFolder(outputRoot.resolve(config.output).toFile());
			
			
			File f = new File(config.input);
			if(!f.exists()) throw new RuntimeException("Input file does not exist "+config.input+" "+f.getAbsolutePath());
	
			watcher = new GlobWatcher(f.getParentFile().toPath(), false);
			watcher.includes(f.getName());
			
			scriptsToWatch = new GlobWatcher(scriptRoot.toPath(),true);
			
			// we will generate script links this.scripts list to enable caching js-bundle data
			for(String script:config.scripts){
				scriptsToWatch.includes(script);
				if(script.endsWith("js")){
					scripts.add(new ScriptEntry(script, new File(scriptRoot,script)));
				}else if(script.endsWith("json")) {
					scripts.add(new BundleEntry(new File(scriptRoot,script)));
				}else {
					log.warn("unsupportd script type "+script);
				}
			}
	
			for(String script:config.css){
				scriptsToWatch.includes(script);
				cssScripts.add(new ScriptEntry(script, new File(scriptRoot,script)));
			}
		}
	
		public void start(boolean watch){
			watcher.init(watch);
			scriptsToWatch.init(watch);
	
			htmlFiles = watcher.getMatchedFilesUnique();
			
			for (Path file : htmlFiles) {
				genHtml(file);
			}
		}
	
		private void genHtml(Path file) {
	
			StringBuilder bScript = new StringBuilder();
			StringBuilder bCss = new StringBuilder();
			
			
			String html;
			try {
				html = new String(Files.readAllBytes(file));
			} catch (IOException e) {
				log.error("Error reading file "+file+" "+e.getMessage(),e);
				return;
			}
			
			log.info("Generating "+config.output);
	
			int idxScript = html.indexOf(config.scriptReplace);
			int idxCss = html.indexOf(config.cssReplace);
			
			if(idxScript != -1) {
				for(Object entry:scripts) {
					if(entry instanceof ScriptEntry) {
						ScriptEntry scriptEntry = (ScriptEntry) entry;
						bScript.append("<script src=\"").append(scriptEntry.script)
							.append("?__mt__=").append(scriptEntry.file.lastModified()).append("\"></script>\n");
	
					}else if(entry instanceof BundleEntry) {
						BundleEntry bundle = (BundleEntry) entry;
						if(bundle.lastModified == 0 || bundle.lastModified < bundle.bundleFile.lastModified()) {
							loadBundle(bundle);
						}
						for(JsInBundle js:bundle.scripts) {
							bScript.append("<script src=\"").append(js.script)
							.append("?__mt__=").append(js.modified).append("\"></script>\n");
						}
					}else {
						log.error("Unsupported script entry "+entry.getClass().getName());
					}
				}
			}
	
			if(idxCss != -1) {
				for(ScriptEntry js:cssScripts) {
					bCss.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(js.script)
					.append("?__mt__=").append(js.file.lastModified()).append("\"></script>\n");
				}
			}
			
			
			Integer[] idx = new Integer[]{idxScript,idxCss};
			Integer[] length = new Integer[]{config.scriptReplace.length(), config.cssReplace.length()};
			String[] replace = new String[] {bScript.toString(), bCss.toString()};
			
			if(idxScript>idxCss) {
				flipArrays(idx,length,replace);
			}
			
			int offset = 0;
			StringBuilder bHtml = new StringBuilder();
			
			for(int i=0; i<idx.length; i++) {
				if(idx[i] == -1) continue;
				bHtml.append(html,offset,idx[i]);
				bHtml.append(replace[i]);
				offset = idx[i]+length[i];
			}
			
			if(offset <html.length()) bHtml.append(html,offset,html.length());
			if(!TaskUtils.writeFile(Paths.get(config.output), bHtml.toString().getBytes(), config.compareBytes)) {
				log.trace("skip identical: "+config.output);			
			}
		}
	
		private void loadBundle(BundleEntry bundle) {
			try {
				JsonNode node = mapper.readTree(bundle.bundleFile);
				JsonNode filesNode = node.get("files");
				bundle.scripts.clear();
				
				if(filesNode != null && !filesNode.isNull() && filesNode.isArray()) {
					int count = filesNode.size();
					for(int i=0; i<count; i++) {
						JsInBundle jsInBundle = mapper.convertValue(filesNode.get(i), JsInBundle.class);
						bundle.scripts.add(jsInBundle);
					}
				}
			} catch (IOException e){
				log.error("Error reading bundle "+bundle.bundleFile.getAbsolutePath() + " "+e.getMessage(),e);
			}
		}
	
		private void flipArrays(Object[] ...arrays) {
			for(Object[] arr:arrays){
				Object tmp = arr[0];
				arr[0] = arr[1];
				arr[1] = tmp;
			}
		}
	
		@Override
		public void run() {
			
			Thread includesThread = new Thread(new Runnable() {
				public void run() {
					try {
						while(!Thread.interrupted()){
							Collection<Path> changes = scriptsToWatch.takeBatchFiles(core.getBurstDelay());
							if(changes == null) break; // null means interrupted, and we should end this loop
							
							
							for (Path file : htmlFiles) {
								if(log.isInfoEnabled())	log.info("includes changed for: "+file);
								genHtml(file);
							}
						}						
					} finally {
						scriptsToWatch.close();
					}
				}
			},Thread.currentThread().getName()+"-includes");

			includesThread.start();
			
			try {
				while(!Thread.interrupted()){
					Collection<Path> changes = watcher.takeBatchFiles(core.getBurstDelay());
					if(changes == null) break; // null means interrupted, and we should end this loop
					
					HashSet<Path> todo = new HashSet<>(changes);
					
					for(Path p:todo) {				
						if(log.isInfoEnabled())	log.info("changed: "+p+" "+p.toFile().lastModified());
						genHtml(p);
					}
				}
				
			} finally {
				watcher.close();
				includesThread.interrupt();
				try {
					includesThread.join(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}
	}
	static class BundleEntry{
		public long lastModified;
		public File bundleFile;
		List<JsInBundle> scripts = new ArrayList<>();

		public BundleEntry(File bundleFile) {
			this.bundleFile = bundleFile;
		}
	}
	static class ScriptEntry{
		public String script;
		public File file;
		public ScriptEntry(String script, File file) {
			super();
			this.script = script;
			this.file = file;
		}

	}
	
	public static class JsInBundle{
		public String script;
		public long modified;
		public long size;
	}
}