package hr.hrg.watch.build.task;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.LangConfig;

public class LangTask extends AbstractTask<LangConfig> implements Runnable{


	protected List<GlobWatcher> folderWatchers = new ArrayList<>();
	
	public static final int BUFFER_SIZE = 4096;
	private Path root;
	
	private long maxLastModified;

	private Map<String, Map<String,String>> cache = new HashMap<>();
	
	private List<File> inputs = new ArrayList<>();

	public LangTask(LangConfig config, WatchBuild core){
		super(config, core);
		this.root = core.getOutputRoot();
		
	}
	
	public void init(boolean watch){
		for(String inputName:config.input) {			
			Path inputPath = root.resolve(inputName);
			GlobWatcher folderWatcher = new GlobWatcher(inputPath.getParent(), true);
			
			folderWatcher.includes(inputPath.getFileName().toString());
			
			for(String output: config.output) {
				File file = root.resolve(output).toFile();
				if(file.exists()){
					maxLastModified = Math.max(file.lastModified(), maxLastModified);
				}
			}
			
			for(String input: config.input) {
				inputs.add(root.resolve(input).toFile());
			}
			
			folderWatcher.init(watch);
			folderWatchers.add(folderWatcher);
		}
		genFiles();
	}

	@Override
	public boolean needsThread() {
		return true;
	}

	public void run(){
		try {			
			while(!Thread.interrupted()){
				boolean changed = false;
				for(GlobWatcher folderWatcher: folderWatchers) {	
					Collection<Path> changes = folderWatcher.takeBatchFilesUnique(core.getBurstDelay());
					if(changes == null) break; // null means interrupted, and we should end this loop
					
					for (Path changeEntry : changes) {
						File file = changeEntry.toFile();
						if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("changed: "+changeEntry+" "+file.lastModified());
						cache.remove(file.getAbsolutePath());
						long mod = file.lastModified();
						if(mod > maxLastModified) maxLastModified = mod;	
					}
					changed = true;
				}
				if(changed) genFiles();
			}
		} finally {
			for(GlobWatcher folderWatcher: folderWatchers) {				
				folderWatcher.close();
			}
		}
	}

	protected boolean genFiles(){
		Map<String, String> trans = new HashMap<>();
		
		for(File input:inputs) {
			Map<String, String> tmp = getTrans(input);
			for(Entry<String, String> entry:tmp.entrySet()) {
				trans.put(entry.getKey(), entry.getValue());
			}
		}

		if(config.output != null){
			for(String out: config.output){
				this.genFile(trans, root.resolve(out));
			}			
		}
		return true;
	}

	private Map<String, String> getTrans(File input) {
		Map<String, String> trans = cache.get(input.getAbsolutePath());
		if(trans == null) {
			trans = new HashMap<>();
			String name = input.getName();
			if(name.endsWith(".yml") || name.endsWith(".yaml")) {
				fromYaml(trans, input);
			}else if(name.endsWith(".properties")) {
				fromProperties(trans, input);
			}else if(name.endsWith(".json")) {
				fromJson(trans, input);
			}
		}
		return trans;
	}

	private void genProperties(Map<String, String> newTrans, OutputStream out) {
		Properties prop = new Properties();
		try {
			for(Entry<String, String> e: newTrans.entrySet()){
				prop.setProperty(e.getKey(), e.getValue());
			}
			prop.store(out,null);
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void genJson(Map<String, String> newTrans, OutputStream out) {
		try {
			core.getMapper().writeValue(out,newTrans);			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void genJs(Map<String, String> newTrans, OutputStream out) {
		try {
			out.write(("var "+config.varName+" = ").getBytes());
			core.getMapper().writeValue(out,newTrans);	
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	protected boolean genFile(Map<String, String> newTrans, Path to){
		String fileName = to.getFileName().toString();

		File file = to.toFile();
		if(file.exists() && file.lastModified() > maxLastModified) {
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("skip older: "+to);			
		}

		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		
		if(fileName.endsWith(".properties")){
			genProperties(newTrans,byteOutput);
		}else if(fileName.endsWith(".json")){
			genJson(newTrans, byteOutput);
		}else if(fileName.endsWith(".js")){
			genJs(newTrans, byteOutput);
		}else{
			throw new RuntimeException("File type not supported "+to);
		}
		
		if(TaskUtils.writeFile(to, byteOutput.toByteArray(), config.compareBytes, maxLastModified)){
			hr.hrg.javawatcher.Main.logInfo("Generating "+to);
			return true;
		}else{
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("skip identical: "+to);
			return false;
		}
	}

	
	private void fromProperties(Map<String, String> newTrans, File from) {
		Properties prop = new Properties();
		try {
			maxLastModified = Math.max(maxLastModified, from.lastModified());
			prop.load(new FileReader(from));
			for(String propName: prop.stringPropertyNames()){
				newTrans.put(propName, prop.getProperty(propName));
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void fromJson(Map<String, String> newTrans, File from) {
		try {
			maxLastModified = Math.max(maxLastModified, from.lastModified());
			JsonNode tree = core.getMapper().readTree(from);
			
			Iterator<Entry<String, JsonNode>> fields = tree.fields();
			while(fields.hasNext()){
				Entry<String, JsonNode> e = fields.next();
				newTrans.put(e.getKey(), e.getValue().asText());
			}
			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void fromYaml(Map<String, String> newTrans, File from) {
		try {
			maxLastModified = Math.max(maxLastModified, from.lastModified());
			JsonNode tree = core.getYamlMapper().readTree(from);
			
			Iterator<Entry<String, JsonNode>> fields = tree.fields();
			while(fields.hasNext()){
				Entry<String, JsonNode> e = fields.next();
				newTrans.put(e.getKey(), e.getValue().asText());
			}
			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public String toString() {
		return "LanguageOutput:"+config.output;
	}
}
