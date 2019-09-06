package hr.hrg.watch.build.task;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.LanguageChangeListener;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.LangOutputConfig;
import hr.hrg.watch.build.task.LangTask.Update;

public class LangOutputTask implements Runnable, LanguageChangeListener{

	Logger log = LoggerFactory.getLogger(LangOutputTask.class);

	protected GlobWatcher folderWatcher;
	
	public static final int BUFFER_SIZE = 4096;
	private Path root;
	
	private long maxLastModified;
	private long codeLastModified;
	private ObjectMapper objectMapper;
	private YAMLMapper yamlMapper;

	private LangOutputConfig config;
	private List<Path> codeFiles = new ArrayList<>();
	
	private List<String> prefixes = new ArrayList<>();
	private HashSet<String> codes = new HashSet<>();
	
	private WatchBuild core;

	private LangTask langTask;


	public LangOutputTask(LangOutputConfig config, LangTask langTask, WatchBuild core, Path root, YAMLMapper yamlMapper, ObjectMapper objectMapper){
		this.config = config;
		this.langTask = langTask;
		this.core = core;
		this.root = root;
		this.yamlMapper = yamlMapper;
		this.objectMapper = objectMapper;
		
		folderWatcher = new GlobWatcher(root, true);

		if(langTask != null) 
			langTask.addLanguageChangeListener(this);
		else 
			throw new NullPointerException("LangTask can not be null");
		
		maxLastModified = langTask.lastModified();
		
		if(config.codeList != null) {
			for(String fileName: config.codeList) {
				Path path = root.resolve(fileName);
				File f = path.toFile();
				if(!f.exists()) throw new RuntimeException("Input file does not exist "+fileName+" "+f.getAbsolutePath());
				long mod = f.lastModified();
				if(mod > maxLastModified) maxLastModified = mod;
				if(mod > codeLastModified) codeLastModified = mod;

				codeFiles.add(path);
				folderWatcher.includes(fileName);
			}
		}
		loadCodes();
	}

	private void loadCodes(){
		prefixes = new ArrayList<>();
		codes = new HashSet<String>();
		for(Path cf:codeFiles) {
			File file = cf.toFile();
			try ( FileReader fr = new FileReader(file);
					BufferedReader br = new BufferedReader(fr);
					){
				String line = null;
				
				while((line = br.readLine()) != null){
					line = line.trim();
					if(line.isEmpty()) continue;
					
					if(line.charAt(line.length()-1) == '*') {						
						prefixes.add(line.substring(0,line.length()-1));
					}else {
						codes.add(line);
					}
				}
			} catch (Exception e) {
				log.error("error reading "+cf.toAbsolutePath(),e);
			}
		}
	}

	public void start(boolean watch){
		folderWatcher.init(watch);		
		genFiles();
	}

	public void run(){
		try {			
			while(!Thread.interrupted()){
				Collection<Path> changes = folderWatcher.takeBatchFilesUnique(core.getBurstDelay());
				if(changes == null) break; // null means interrupted, and we should end this loop
				
				for (Path changeEntry : changes) {
					if(log.isInfoEnabled())	log.info("changed: "+changeEntry+" "+changeEntry.toFile().lastModified());
					long mod = changeEntry.toFile().lastModified();
					if(mod > maxLastModified) maxLastModified = mod;	
				}
				loadCodes();
				genFiles();
			}
		} finally {
			folderWatcher.close();
		}
	}

	protected boolean genFiles(){
		Map<String, String> trans = null;
		if(prefixes.isEmpty() && codes.isEmpty()) {
			trans = langTask.getTrans();
		}else {			
			trans = new HashMap<>();
			for(Entry<String, String> e:langTask.getTrans().entrySet()) {
				if(codes.contains(e.getKey())) {
					trans.put(e.getKey(), e.getValue());
				}else {
					for(String p:prefixes) {
						if(e.getKey().startsWith(p)) {
							trans.put(e.getKey(), e.getValue());							
							break;
						}
					}
				}
			}
		}

		if(config.output != null){
			for(String out: config.output){
				this.genFile(trans, root.resolve(out));
			}			
		}
		return true;
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
		
		if(TaskUtils.writeFile(to, byteOutput.toByteArray(), config.compareBytes, maxLastModified)){
			log.info("Generating "+to);
			return true;
		}else{
			if(Main.VERBOSE > 1) log.trace("skip identical: "+to);
			return false;
		}
	}

	@Override
	public void languageChanged(Update update) {
		if(update.getLastModified() > maxLastModified) maxLastModified = update.getLastModified();
		genFiles();
	}

}
