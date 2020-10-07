package hr.hrg.watch.build;


import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.task.AbstractTask;
import hr.hrg.watch.build.task.AbstractTaskFactory;
import hr.hrg.watchsass.Compiler;

public class WatchBuild {
	
	protected JsonMapper mapper   = new JsonMapper();
	protected YAMLMapper yamlMapper = new YAMLMapper();

	protected HashMap<Path, Path> included = new LinkedHashMap<>();
	
	protected HashMap<String, AbstractTaskFactory<?,?>> factories = new LinkedHashMap<>();
	
	protected Map<String, Object> namedTasks = new ConcurrentHashMap<>();	
	protected List<Thread> threads = new ArrayList<>();

	protected List<ErrorEntry> errors = new ArrayList<>();
	
	private Path outputRoot;
	private Path basePath;
	private long burstDelay = 50;
	
	String lang;
	String[] langs;
	
	public WatchBuild(){
		basePath = Paths.get("./");
		outputRoot = basePath;
	}

	public void setFactories(Map<String, AbstractTaskFactory<?,?>> factories) {
		for(Entry<String, AbstractTaskFactory<?,?>> e:factories.entrySet()) {
			this.factories.put(e.getKey().toLowerCase(), e.getValue());
		}
	}

	public void addRunner(Map.Entry<String, AbstractTaskFactory<?,?>> e){
		factories.put(e.getKey().toLowerCase(), e.getValue());
	}

	public void addRunner(String code, AbstractTaskFactory<?,?> runner){
		factories.put(code.toLowerCase(), runner);
	}
	
	public AbstractTaskFactory<?,?> getRunner(String code) {
		return factories.get(code);
	}

	public void addThread(Thread thread){
		threads.add(thread);
	}
	
	public Path getOutputRoot() {
		return outputRoot;
	}

	public Path getBasePath() {
		return basePath;
	}
	
	public long getBurstDelay() {
		return burstDelay;
	}
	
	public JsonMapper getMapper() {
		return mapper;
	}
	
	public YAMLMapper getYamlMapper() {
		return yamlMapper;
	}
	
	public void setBurstDelay(long burstDelay) {
		this.burstDelay = burstDelay;
	}
	
	protected void runFromArgs(String[] args) {
		boolean watch = false;
		String confFile = null;

		for(int i=0; i<args.length; i++) {
			if("--watch".equals(args[i]) || "-w".equals(args[i])) 
				watch = true;
			else  if("-v".equals(args[i])) 
				hr.hrg.javawatcher.Main.VERBOSE = 1;
			else  if("-vv".equals(args[i])) 
				hr.hrg.javawatcher.Main.VERBOSE = 2;
			else
				confFile = args[i];
		}
		
		List<AbstractTask<?>> tasks = new ArrayList<>();
		
		try {
			InputStream inputStream = confFile == null ? System.in : new FileInputStream(confFile);
			
			JsonNode node = null;
			if(confFile != null && ( confFile.endsWith(".yml") || confFile.endsWith(".yaml")))
				node = yamlMapper.readTree(inputStream);
			else
				node = mapper.readTree(inputStream);

			int size = node.size();
			for(int i=0; i<size; i++) {
				
				ObjectNode conf = (ObjectNode) node.get(i);
				
				try {
					
					if(!conf.hasNonNull("type")) throw new RuntimeException("type null for item#"+i+": "+conf);
					
					String type = conf.get("type").asText().toLowerCase();
					
					
					AbstractTaskFactory<?,?> factory = factories.get(type);
					if(factory == null) throw new RuntimeException("Factory not found for: "+type);
					
					tasks.add(factory.task(conf));
				} catch (Exception e) {
					throw new RuntimeException("problem with item#"+i+": "+conf, e);
				}
			}
				
			runBuild(tasks, watch);
			
			if(!watch || confFile == null) return;
						
			System.out.println();
			System.out.println("type: \"q\" and press <ENTER> to quit");
		
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String line = null;
			while((line=br.readLine()) != null) {
				if(line.toLowerCase().equals("q")){
					stopBuild();
					System.exit(0);
				}
			}
		} catch (Exception e) {
			hr.hrg.javawatcher.Main.logError(e.getMessage(),e);
		}
	}

	public boolean stopped(){
		for(Thread thread: threads){
			if(thread.isAlive()) return false;
		}
		return true;		
	}

	public void stopBuild(){
		if(threads.size() >0) System.out.println("Stopping "+threads.size()+" watch threads");
		for(Thread thread: threads){
			System.out.println("THREAD: "+thread.getName());
			thread.interrupt();
		}
		long stopTime = System.currentTimeMillis();
		while(!Thread.interrupted()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(stopped()) break;
		}
		System.out.println("Stopped "+threads.size()+" threads in "+(System.currentTimeMillis() - stopTime)+" ms");
		System.out.println("\n\n\n");
		threads.clear();
		namedTasks.clear();
		included.clear();
	}
	
	public void runBuild(List<AbstractTask<?>> tasks, boolean watch) {
		
		errors.clear();
		
		try {
			
			for(AbstractTask<?> task:tasks) {
				task.start(watch);
			}

			if(threads.size() >0) System.out.println("Starting "+threads.size()+" watch threads");
			for(Thread thread: threads){
				System.out.println("THREAD: "+thread.getName());
				thread.start();
			}
			
			if(errors.size() >0) {
				System.out.println();
				System.out.println();
				System.out.println(errors.size()+" Errors reported during initialization ");
				for(ErrorEntry err:errors) {
					System.out.println("ERROR: "+err.message);
				}
				System.out.println();
			}
		} catch (Exception e) {
			hr.hrg.javawatcher.Main.logError(e.getMessage(),e);;
		}
	}
	
	public void registerTask(String code, Object task) {
		this.namedTasks.put(code, task);
	}

	public void unregisterTask(String code, Object task) {
		this.namedTasks.remove(code);
	}

	public Object getTask(String code) {
		return this.namedTasks.get(code);
	}

	public String getJson(Object o) {
		try {
			return mapper.writeValueAsString(o);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return "";
	}

	public List<ErrorEntry> getErrors() {
		return errors;
	}
	
	public void logError(String string) {
		errors.add(new ErrorEntry(string));
	}

	public void logError(String string, Throwable e) {
		errors.add(new ErrorEntry(string,e));
	}

	class ErrorEntry{

		private String message;
		private Throwable err;

		public ErrorEntry(String message, Throwable err) {
			this.message = message;
			this.err = err;
		}
		public ErrorEntry(String message) {
			this.message = message;
		}
		
	}
}
