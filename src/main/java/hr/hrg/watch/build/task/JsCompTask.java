package hr.hrg.watch.build.task;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hr.hrg.javawatcher.FileChangeEntry;
import hr.hrg.javawatcher.FileMatchGlob;
import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.LanguageChangeListener;
import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.VarExpander;
import hr.hrg.watch.build.config.JsCompConfig;
import hr.hrg.watch.build.task.LangTask.Update;

public class JsCompTask implements LanguageChangeListener, Runnable{

	Logger log = LoggerFactory.getLogger(JsCompTask.class);
	
	public static final int BUFFER_SIZE = 4096;
	private long burstDelay = 20;

	VarExpander exp = new VarExpander(VarExpander.PATTERN_LANG);

	private LangTask langTask;
	private Path toPath;
	private GlobWatcher fromGlob;
	private JsCompConfig config;
	
	public JsCompTask(Path outputRoot, JsCompConfig config, LangTask langTask){
		this.config = config;
		this.langTask = langTask;

		
		fromGlob = new GlobWatcher(Paths.get(config.input));
		
		toPath = outputRoot.resolve(config.output);
		
		List<String> includeWithHtml = new ArrayList<>(config.include); 
		
		for(String pattern : config.include){
			if(pattern.endsWith(".js")){
				String pHtml = pattern.substring(0, pattern.length()-3)+".html";
				if(!includeWithHtml.contains(pHtml)) includeWithHtml.add(pHtml);
			}
		}
		
		fromGlob.includes(includeWithHtml);
		fromGlob.excludes(config.exclude);

		if(langTask != null) 
			langTask.addLanguageChangeListener(this);
		else throw new NullPointerException("LangTask can not be null");
	}
	
	public void start(boolean watch){
		fromGlob.init(watch);
		Collection<Path> files = fromGlob.getMatchedFiles(); 
		buildAll(files, true);
	}

	private synchronized void buildAll(Collection<Path> files, boolean skipHtml) {
		for (Path fileChanged : files) {
			Path file = forceJs(fileChanged);
			if(skipHtml && !file.equals(fileChanged)) continue;

			Path toFile = toPath.resolve(fromGlob.relativize(file));
			buildComp(file, toFile);
		}
	}

	public static Path forceJs(Path fileChanged) {
		String fileName = fileChanged.getFileName().toString();
		if(fileName.endsWith(".js")) return fileChanged;
		if(!fileName.endsWith(".html")){
			throw new RuntimeException("Only .js and .html allowed");
		}
		Path parent = fileChanged.getParent();
		fileName = fileName.substring(0, fileName.length()-5)+".js";
		if(parent == null){
			return Paths.get(fileName);
		}
		return parent.resolve(fileName);
	}

	public void run(){
		while(!Thread.interrupted()){
			Collection<FileChangeEntry<FileMatchGlob>> files = fromGlob.takeBatch(burstDelay);
			if(files == null) break; // interrupted
			for (FileChangeEntry<FileMatchGlob> fileChanged : files) {
				Path file = forceJs(fileChanged.getPath());
//				if(file.toFile().isDirectory()) continue;
				log.info("changed:"+fileChanged+" "+fileChanged.getPath().toFile().lastModified());
				Path toFile = toPath.resolve(fromGlob.relativize(file));
				synchronized(this){
					buildComp(file, toFile);					
				}
			}
		}
		fromGlob.stop();
	}

	
	protected boolean buildComp(Path from, Path to){
		File fromFile = from.toFile();
		File toFile = to.toFile();
		File tplFile = null;
		String fileName = from.getFileName().toString();

		if(fileName.endsWith(".js")){
			tplFile = new File(fromFile.getAbsoluteFile().getParentFile(), fileName.substring(0,fileName.length()-3)+".html");
			if(!tplFile.exists()) tplFile = null;
		}

		long lastModified = toFile.lastModified();
		boolean copy = 
					fromFile.lastModified() > lastModified
				|| (tplFile != null && tplFile.lastModified() > lastModified)
				|| langTask.lastModified() > lastModified;

		if(!copy){
			log.trace("Generated file is newer than all inputs "+toFile);
			return false;
		}
				
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		combineTemplate(from, tplFile,out);
		byte[] newBytes = out.toByteArray();			

		if(TaskUtils.writeFile(to, newBytes, config.compareBytes)){
			log.info("generated:      "+to);
			return true;
		}else{
			log.trace("skip identical: "+to);			
			return false;
		}
	}

	private void combineTemplate(Path from, File tplFile, OutputStream out) {
		try {
			BufferedReader br = null;
			String line = null;
			Map<String, String> trans = langTask.getTrans();
			String js = new String(Files.readAllBytes(from));
			if(tplFile != null){
				try {
					StringBuffer sw = new StringBuffer((int) tplFile.length());
					br = new BufferedReader(new FileReader(tplFile));
			        while( ( line = br.readLine() ) != null ){
			        	line = exp.expand(line, trans).trim();
			        	sw.append(line);
			        }
			        js = js.replace("<-TEMPLATE->", sw.toString().replace("'", "\\'"));
		        } catch (Exception e) {
			        e.printStackTrace();
		        }
			}
			out.write(js.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void languageChanged(Update update){
		new Thread(new Runnable(){
			public void run(){
				log.info("Rebuilding because of change in laguage file");
				buildAll(fromGlob.getMatchedFiles(), true);
			}
		}).start();
	}
}
