package hr.hrg.watch.build.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.JsonMapper;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.HtmlScriptAndCssConfig;
import hr.hrg.watch.build.config.TaskDef;

public class HtmlScriptAndCssTaskFactory extends AbstractTaskFactory{

	Logger log = LoggerFactory.getLogger(HtmlScriptAndCssTaskFactory.class);
	
	public HtmlScriptAndCssTaskFactory(WatchBuild core, JsonMapper mapper){
		super(core, mapper);
	}
	
	@Override
	public void startOne(TaskDef taskDef, String lang, JsonNode root, boolean watch) {
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
		long maxLastModified = 0;
		
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
				File file = new File(scriptRoot,script);
				long mod = file.lastModified();
				if(script.endsWith("js")){
					scripts.add(new ScriptEntry(script, file));
					if(mod >maxLastModified) maxLastModified = mod;
				}else if(script.endsWith("json")) {
					scripts.add(new BundleEntry(file));
					if(mod >maxLastModified) maxLastModified = mod;
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
			StringBuilder bLastMod = new StringBuilder();
			
			
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
			int idxLastMod = html.indexOf(config.lastModReplace);
			
			long maxLastModifiedScript = 0;
			
			if(idxScript != -1) {
				for(Object entry:scripts) {
					if(entry instanceof ScriptEntry) {
						ScriptEntry scriptEntry = (ScriptEntry) entry;
						bScript.append("<script src=\"").append(scriptEntry.script)
							.append("?__mt__=").append(scriptEntry.file.lastModified()).append("\"></script>\n");
						
						maxLastModifiedScript = Math.max(maxLastModifiedScript, scriptEntry.file.lastModified());
						
					}else if(entry instanceof BundleEntry) {
						BundleEntry bundle = (BundleEntry) entry;
						if(bundle.lastModified < bundle.bundleFile.lastModified()) {
							loadBundle(bundle);
						}
						maxLastModifiedScript = Math.max(maxLastModifiedScript, bundle.bundleFile.lastModified());
						
						for(JsInBundle js:bundle.scripts) {
							bScript.append("<script src=\"");
							
							if(bundle.jsRoot != null && !bundle.jsRoot.isEmpty()) bScript.append(bundle.jsRoot).append("/");
							
							bScript.append(js.script);
							bScript.append("?__mt__=").append(js.modified).append("\"></script>\n");
							maxLastModifiedScript = Math.max(maxLastModifiedScript, js.modified);
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
					maxLastModifiedScript = Math.max(maxLastModifiedScript, js.file.lastModified());

				}
			}
			
			bLastMod.append("<script>").append("var APP_LAST_MODIFIED = ").append(maxLastModifiedScript).append(";</script>\n");
			
			ReplaceDef[] idx = new ReplaceDef[]{
					new ReplaceDef(idxScript, config.scriptReplace, bScript),
					new ReplaceDef(idxCss, config.cssReplace, bCss),
					new ReplaceDef(idxLastMod, config.lastModReplace, bLastMod),
					};
			
			Arrays.sort(idx, new Comparator<ReplaceDef>() {
				@Override
				public int compare(ReplaceDef o1, ReplaceDef o2) {
					return o1.idx - o2.idx;
				}
			});
			
			int offset = 0;
			StringBuilder bHtml = new StringBuilder();
			
			for(int i=0; i<idx.length; i++) {
				if(idx[i].idx == -1) continue;
				bHtml.append(html,offset,idx[i].idx);
				bHtml.append(idx[i].replace.toString());
				offset = idx[i].idx + idx[i].search.length();
			}
			
			if(offset <html.length()) bHtml.append(html,offset,html.length());
			if(!TaskUtils.writeFile(Paths.get(config.output), bHtml.toString().getBytes(), config.compareBytes, maxLastModified)){
				if(Main.VERBOSE > 1) log.trace("skip identical: "+config.output);			
			}else {
				TaskUtils.writeFile(Paths.get(config.output+".lastmodified"), Long.toString(maxLastModifiedScript).getBytes(), config.compareBytes, maxLastModified);
			}
		}
//	
//		private void sortArrays(int[] idx, int[] length, String[] replace) {
//			int[] pos = new int[idx.length];
//			for(int i=0; i<pos.length; i++) {
//				int min = idx[i];
//				int minPos = i;
//				for(int j=i; j<pos.length; j++) {
//					if(idx[j] < min) {
//						min = idx[j];
//					}
//				}
//			}
//			
//			int[] idx1 = new int[idx.length];
//			int[] length1 = new int[idx.length];
//			int[] replace1 = new int[idx.length];
//			for(int i=0; i<)
//		}

		private void loadBundle(BundleEntry bundle) {
			try {
				JsonNode node = mapper.readTree(bundle.bundleFile);
				JsonNode filesNode = node.get("files");
				bundle.scripts.clear();

				if(node.hasNonNull("jsRoot")){ bundle.jsRoot = node.get("jsRoot").asText();	}
				if(node.hasNonNull("srcRoot")){ bundle.srcRoot = node.get("srcRoot").asText(); }
				
				if(filesNode != null && !filesNode.isNull() && filesNode.isArray()) {
					int count = filesNode.size();
					for(int i=0; i<count; i++) {
						JsInBundle jsInBundle = mapper.convertValue(filesNode.get(i), JsInBundle.class);
						bundle.scripts.add(jsInBundle);
					}
				}
				bundle.lastModified = bundle.bundleFile.lastModified();
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
							
							for(Path p: changes){
								long mod = p.toFile().lastModified();
								if(mod > maxLastModified) maxLastModified = mod;								
							}
							
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
	
	static class ReplaceDef{
		private int idx;
		private String search;
		private StringBuilder replace;

		public ReplaceDef(int idx, String search, StringBuilder replace) {
			this.idx = idx;
			this.search = search;
			this.replace = replace;
		}
	}
	static class BundleEntry{
		public long lastModified;
		public String jsRoot;
		public String srcRoot;
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