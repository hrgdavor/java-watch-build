package hr.hrg.watch.build.task;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.LanguageChangeListener;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.LangConfig;

public class LangTask implements Runnable{

	Logger log = LoggerFactory.getLogger(LangTask.class);

	protected GlobWatcher folderWatcher;
	
	public static final int BUFFER_SIZE = 4096;
	private List<LanguageChangeListener> listeners = new ArrayList<>();
	private Path root;
	
	private Map<String , String> trans = new HashMap<>();
	private long lastModified;
	private ObjectMapper objectMapper;
	private YAMLMapper yamlMapper;

	private LangConfig config;
	private List<Path> langFiles = new ArrayList<>();
	
	private WatchBuild core;


	public LangTask(LangConfig config, WatchBuild core, Path root, YAMLMapper yamlMapper, ObjectMapper objectMapper){
		this.config = config;
		this.core = core;
		this.root = root;
		this.yamlMapper = yamlMapper;
		this.objectMapper = objectMapper;
		
		folderWatcher = new GlobWatcher(root, false);
		for(String fileName: config.input) {
			Path path = root.resolve(fileName);
			File f = path.toFile();
			if(!f.exists()) throw new RuntimeException("Input file does not exist "+config.input+" "+f.getAbsolutePath());
			langFiles.add(path);
			folderWatcher.includes(fileName);
		}
	}

	public void start(boolean watch){
		folderWatcher.init(watch);

		Collection<Path> files = folderWatcher.getMatchedFilesUnique();
		
		for (Path file : files) {
			genFiles(file);
		}
	}

	public Map<String, String> getTrans() {
		return trans;
	}

	public void run(){
		try {			
			while(!Thread.interrupted()){
				Collection<Path> changes = folderWatcher.takeBatchFilesUnique(core.getBurstDelay());
				if(changes == null) break; // null means interrupted, and we should end this loop
				
				for (Path changeEntry : changes) {
					if(log.isInfoEnabled())	log.info("changed: "+changeEntry+" "+changeEntry.toFile().lastModified());
					genFiles(changeEntry);
				}
			}
		} finally {
			folderWatcher.close();
		}
	}

	protected boolean genFiles(Path from){
		Update update = this.updateLanguage();
		
		if(update.changes.size() == 0 && update.removed.size() == 0){
			log.trace("No translations changed");
			return false;
		}
		this.lastModified = update.lastModified;

		if(config.output != null){
			for(String out: config.output){
				this.genFile(update.newTrans, root.resolve(out));
			}			
		}
		for(LanguageChangeListener listener: listeners){
			try {
				listener.languageChanged(update);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return true;
	}

	private Update updateLanguage() {
		Map<String, String> changes = new HashMap<>();
		Map<String, String> removed = new HashMap<>();
		Map<String, String> newTrans = new HashMap<>();
		long lastModified = 0;
		
		for(Path from:langFiles) {
			String fileName = from.getFileName().toString();
			
			// calc max lastModified
			long tmp = from.toFile().lastModified();
			if(tmp > lastModified) lastModified = tmp;
			
			if(fileName.endsWith(".properties")){
				fromProperties(newTrans,from);
			}else if(fileName.endsWith(".json")){
				fromJson(newTrans, from);
			}else if(fileName.endsWith(".yml") || fileName.endsWith(".yaml")){
				fromYaml(newTrans, from);
			}else{
				throw new RuntimeException("File type not supported "+fileName+" "+from);
			}			
		}
		
		for(Entry<String, String> e:newTrans.entrySet()){
			if(trans.containsKey(e.getKey())){
				if(!compStr(e.getValue(),trans.get(e.getKey()))){
					changes.put(e.getKey(), e.getValue());
				}
			}else{
				//added
				changes.put(e.getKey(), e.getValue());
			}
		}

		for(Entry<String, String> e:trans.entrySet()){
			if(!newTrans.containsKey(e.getKey())){
				removed.put(e.getKey(), e.getValue());
			}
		}

		trans = newTrans;
		
		return new Update(newTrans, changes, removed, lastModified);
	}

	private boolean compStr(String a, String b){
		if(a == null && b==null) return true;
		if(a==null || b == null) return false;
		return a.equals(b);
	}
	
	private void fromProperties(Map<String, String> newTrans, Path from) {
		Properties prop = new Properties();
		try {
			prop.load(new FileReader(from.toFile()));
			for(String propName: prop.stringPropertyNames()){
				newTrans.put(propName, prop.getProperty(propName));
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
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

	private void fromJson(Map<String, String> newTrans, Path from) {
		try {
			JsonNode tree = objectMapper.readTree(from.toFile());
			
			Iterator<Entry<String, JsonNode>> fields = tree.fields();
			while(fields.hasNext()){
				Entry<String, JsonNode> e = fields.next();
				newTrans.put(e.getKey(), e.getValue().asText());
			}
			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void genJson(Map<String, String> newTrans, OutputStream out) {
		try {
			objectMapper.writeValue(out,newTrans);			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void genJs(Map<String, String> newTrans, OutputStream out) {
		try {
			out.write(("var "+config.varName+" = ").getBytes());
			objectMapper.writeValue(out,newTrans);	
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void fromYaml(Map<String, String> newTrans, Path from) {
		try {
			JsonNode tree = yamlMapper.readTree(from.toFile());
			
			Iterator<Entry<String, JsonNode>> fields = tree.fields();
			while(fields.hasNext()){
				Entry<String, JsonNode> e = fields.next();
				newTrans.put(e.getKey(), e.getValue().asText());
			}
			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void genYaml(Map<String, String> newTrans, OutputStream out) {
		try {
			yamlMapper.writeValue(out,newTrans);			
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	protected boolean genFile(Map<String, String> newTrans, Path to){
		String fileName = to.getFileName().toString();

		ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
		
		if(fileName.endsWith(".properties")){
			genProperties(newTrans,byteOutput);
		}else if(fileName.endsWith(".json")){
			genJson(newTrans, byteOutput);
		}else if(fileName.endsWith(".js")){
			genJs(newTrans, byteOutput);
		}else if(fileName.endsWith(".yml") || fileName.endsWith(".yaml")){
			genYaml(newTrans, byteOutput);
		}else{
			throw new RuntimeException("File type not supported "+to);
		}
		
		if(TaskUtils.writeFile(to, byteOutput.toByteArray(), config.compareBytes)){
			log.info("Generating "+to);
			return true;
		}else{
			log.trace("skip identical: "+to);
			return false;
		}
	}

	protected void closeStream(InputStream in) {
		if (in != null)
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	protected void closeStream(OutputStream out) {
		if (out != null)
			try {
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}
	
	public static class Update{
		private Map<String, String> newTrans;
		private Map<String, String> changes;
		private Map<String, String> removed;
		private long lastModified;

		public Update(Map<String, String> newTrans, Map<String, String> changes, Map<String, String> removed, long lastModified) {
			this.newTrans = newTrans;
			this.changes = changes;
			this.removed = removed;
			this.lastModified = lastModified;
		}
		
		public long getLastModified() {
			return lastModified;
		}
		
		public Map<String, String> getNewTrans() {
			return newTrans;
		}

		public Map<String, String> getChanges() {
			return changes;
		}

		public Map<String, String> getRemoved() {
			return removed;
		}

	}

	public void addLanguageChangeListener(LanguageChangeListener listener) {
		listeners.add(listener);
	}
	
	public long lastModified() {
		return lastModified;
	}	
}
