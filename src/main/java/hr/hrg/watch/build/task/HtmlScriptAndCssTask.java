package hr.hrg.watch.build.task;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.HtmlScriptAndCssConfig;

class HtmlScriptAndCssTask extends AbstractTask<HtmlScriptAndCssConfig> implements Runnable {	

	private GlobWatcher watcher;
	private Collection<Path> htmlFiles;

	List<Object> scripts = new ArrayList<>();
	List<ScriptEntry> cssScripts = new ArrayList<>();

	private GlobWatcher scriptsToWatch;

	private File scriptRoot;
	long maxLastModified = 0;
	
	public HtmlScriptAndCssTask(HtmlScriptAndCssConfig config, WatchBuild core){
		super(config, core);
		this.config = config;
	}

	public void init(boolean watch){


		if(config.scriptVariable != null && config.scriptVariable.isEmpty()) config.scriptVariable = null;
		
		if(core.getOutputRoot() == null) throw new NullPointerException("outputRoot can not be null");
		
		scriptRoot = TaskUtils.getFolder(core.getOutputRoot().resolve(config.output).toFile());
		
		
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
				updateLastModified(mod);
			}else if(script.endsWith("json")) {
				scripts.add(new BundleEntry(file));
				updateLastModified(mod);
			}else {
				if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logWarn("unsupportd script type "+script);
			}
		}

		for(String script:config.css){
			scriptsToWatch.includes(script);
			cssScripts.add(new ScriptEntry(script, new File(scriptRoot,script)));
		}
		
		
		watcher.init(watch);
		scriptsToWatch.init(watch);

		htmlFiles = watcher.getMatchedFilesUnique();
		
		for (Path file : htmlFiles) {
			genHtml(file);
		}
	}

	private void appendScript(StringBuilder bScript, String script, long lastModified, List<List<Object>> pageScripts) {
		List<Object> entry = new ArrayList<>();
		entry.add(script);
		entry.add(lastModified);
		pageScripts.add(entry);
		
		if(config.scriptVariable == null) {
			bScript.append("<script src=\"").append(script)
				.append("?__mt__=").append(lastModified).append("\"></script>\n");
		}else {
			bScript.append("[\"").append(script).append("\",")
			.append(lastModified).append("]");
		}
	}


	private void genHtml(Path htmlPath) {

		StringBuilder bScript = new StringBuilder();
		StringBuilder bCss = new StringBuilder();
		StringBuilder bGlobal = new StringBuilder();
		StringBuilder bLastMod = new StringBuilder();
		
		updateLastModified(htmlPath.toFile().lastModified());
		
		String html;
		try {
			html = new String(Files.readAllBytes(htmlPath));
		} catch (IOException e) {
			hr.hrg.javawatcher.Main.logError("Error reading file "+htmlPath+" "+e.getMessage(),e);
			return;
		}
		
		if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("Generating "+config.output);

		int idxScript = html.indexOf(config.scriptReplace);
		int idxGlobals = html.indexOf(config.globalsReplace);
		int idxCss = html.indexOf(config.cssReplace);
		int idxLastMod = html.indexOf(config.lastModReplace);
				
		List<List<Object>> pageScripts = new ArrayList<>();
		
		if(config.scriptVariable != null) {
			bScript.append("<script>\nvar ").append(config.scriptVariable).append(" = [\n");
		}

		boolean first = true;		
		for(Object entry:scripts) {
			
			if(entry instanceof ScriptEntry) {
				ScriptEntry scriptEntry = (ScriptEntry) entry;
				if(!first) bScript.append(",\n");
				appendScript(bScript, scriptEntry.script, scriptEntry.file.lastModified(), pageScripts);
				first = false;

				updateLastModified(scriptEntry.file.lastModified());
				
			}else if(entry instanceof BundleEntry) {
				BundleEntry bundle = (BundleEntry) entry;
				if(bundle.lastModified < bundle.bundleFile.lastModified()) {
					loadBundle(bundle);
				}

				updateLastModified(bundle.bundleFile.lastModified());
				
				for(JsInBundle js:bundle.scripts) {
					
					StringBuffer src = new StringBuffer();
					if(bundle.jsRoot != null && !bundle.jsRoot.isEmpty()) src.append(bundle.jsRoot).append("/");
					src.append(js.script);
					
					if(!first) bScript.append(",\n");
					appendScript(bScript, src.toString(), js.modified, pageScripts);
					first = false;

					updateLastModified(js.modified);
				}
			}else {
				hr.hrg.javawatcher.Main.logError("Unsupported script entry "+entry.getClass().getName(), null);
			}
		}
		
		if(config.scriptVariable != null) {
			bScript.append("\n];\n</script>\n");
		}
		

		if(idxCss != -1) {
			for(ScriptEntry js:cssScripts) {
				bCss.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"").append(js.script)
				.append("?__mt__=").append(js.file.lastModified()).append("\"/>\n");
				updateLastModified(js.file.lastModified());
			}
		}
		
		bGlobal.append("<script>");
		if(config.globals != null && !config.globals.isNull()) {
			Iterator<String> fieldNames = config.globals.fieldNames();
			while(fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				bGlobal.append("var ").append(fieldName).append(" = ");
				bGlobal.append(core.getMapper().writeValueAsStringNoEx(config.globals.get(fieldName)));
				bGlobal.append(";\n");
			}
		}
		bGlobal.append("</script>\n");
		
		bLastMod.append("<script>").append("var APP_LAST_MODIFIED = ").append(maxLastModified).append(";</script>\n");
		
		ReplaceDef[] idx = new ReplaceDef[]{
				new ReplaceDef(idxGlobals, config.globalsReplace, bGlobal),
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
		
		// write html file
		if(!TaskUtils.writeFile(Paths.get(config.output), bHtml.toString().getBytes(), config.compareBytes, maxLastModified)){
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("skip identical: "+config.output);			
		}else {
			// if html is changed, write timestamp file also
			TaskUtils.writeFile(Paths.get(config.output+".lastmodified"), Long.toString(maxLastModified).getBytes(), config.compareBytes, maxLastModified);
			
			Map<String, Object> pageData = new HashMap<>();
			pageData.put("scripts", pageScripts);
			
			List<List<Object>> cssList = new ArrayList<>();
			for(ScriptEntry css:cssScripts) {
				List<Object> entry = new ArrayList<>();
				entry.add(css.script);
				entry.add(css.file.lastModified());
				cssList.add(entry);
			}
			pageData.put("css", cssList);
			
			pageData.put("lastmodified", maxLastModified);
			
			Map globals = new HashMap();
			if(config.globals != null && !config.globals.isNull()) {
				Iterator<String> fieldNames = config.globals.fieldNames();
				while(fieldNames.hasNext()) {
					String fieldName = fieldNames.next();
					globals.put(fieldName, config.globals.get(fieldName));
				}
			}
			pageData.put("globals", globals);
			
			ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
			try {
				core.getMapper().writeValue(byteOutput,pageData);
			}catch (Exception e) {
				e.printStackTrace();
			}
			
			TaskUtils.writeFile(Paths.get(config.output+".json"), byteOutput.toByteArray(), config.compareBytes, maxLastModified);
		}
	}

	private void updateLastModified(long modified) {
		maxLastModified = Math.max(maxLastModified, modified);
	}

	private void loadBundle(BundleEntry bundle) {
		try {
			JsonNode node = core.getMapper().readTree(bundle.bundleFile);
			JsonNode filesNode = node.get("files");
			bundle.scripts.clear();

			if(node.hasNonNull("jsRoot")){ bundle.jsRoot = node.get("jsRoot").asText();	}
			if(node.hasNonNull("srcRoot")){ bundle.srcRoot = node.get("srcRoot").asText(); }
			
			if(filesNode != null && !filesNode.isNull() && filesNode.isArray()) {
				int count = filesNode.size();
				for(int i=0; i<count; i++) {
					JsInBundle jsInBundle = core.getMapper().convertValue(filesNode.get(i), JsInBundle.class);
					bundle.scripts.add(jsInBundle);
				}
			}
			bundle.lastModified = bundle.bundleFile.lastModified();
		} catch (IOException e){
			hr.hrg.javawatcher.Main.logError("Error reading bundle "+bundle.bundleFile.getAbsolutePath() + " "+e.getMessage(),e);
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
							updateLastModified(p.toFile().lastModified());
						}
						
						for (Path file : htmlFiles) {
							if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("includes changed for: "+file);
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
					if(hr.hrg.javawatcher.Main.isInfoEnabled())	hr.hrg.javawatcher.Main.logInfo("changed: "+p+" "+p.toFile().lastModified());
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

	@Override
	public String toString() {
		return "HtmlAndScript:"+config.input+" to "+config.output;
	}		
	
	static class ReplaceDef{
		int idx;
		String search;
		StringBuilder replace;

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