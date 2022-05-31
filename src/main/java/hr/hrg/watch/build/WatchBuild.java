package hr.hrg.watch.build;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.task.AbstractTask;
import hr.hrg.watch.build.task.AbstractTaskFactory;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeEvent.EventType;
import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;
import io.methvin.watcher.DirectoryWatcher.Builder;

public class WatchBuild {
	
	
	public static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss.SSS ");
	protected JsonMapper mapper   = new JsonMapper();
	protected YAMLMapper yamlMapper = new YAMLMapper();

	protected HashMap<Path, Path> included = new LinkedHashMap<>();
	
	protected HashMap<String, AbstractTaskFactory<?,?>> factories = new LinkedHashMap<>();
	
	protected Map<String, Object> namedTasks = new ConcurrentHashMap<>();	
	protected List<Thread> threads = new ArrayList<>();

	protected List<ErrorEntry> errors = new ArrayList<>();
	
	protected HashMap<Path, OutputEntry> outputs = new LinkedHashMap<>();

	private Path outputRoot;
	private Path basePath;
	private long burstDelay = 50;
	
	String lang;
	String[] langs;

	static Builder builder = DirectoryWatcher.builder();
	private Set<Path> watchPaths = new HashSet<>();
	private Map<Path,List<FileMatchGlob2>> watchers = new HashMap<>();
	private List<FileMatchGlob2> watchersList = new ArrayList<>();
	private Set<Path> watchersParents = new HashSet<>();
	private HashMap<Path,FileDef> fileCache = new HashMap<>();
	private DirectoryWatcher directoryWatcher;

	private boolean initial;
	public Object notifyLock = new Object();
	public Set<FileDef> notifyCandidates = new HashSet<>();
	public Set<FileDef> notifyList = new HashSet<>();
	private long lastMessage;
	public long startTime = System.currentTimeMillis();
	
	public WatchBuild(){
		basePath = Paths.get("./").toAbsolutePath().normalize();
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
		logMessage("Starting");
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
		
		tasks = new ArrayList<>();
		
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
					
					AbstractTask<?> task = factory.task(conf);
					if(!tasks.contains(task)) tasks.add(task);
//					logMessage("Created task "+task);
				} catch (Exception e) {
					throw new RuntimeException("problem with item#"+i+": "+conf, e);
				}
			}
				
			runBuild(watch);

			System.out.println();
			System.out.println();
			long now = System.currentTimeMillis();
			logMessage("READY (started in "+((now-startTime)/1000d)+"s)");
			System.out.println();
			System.out.println();

			if(!watch) {
				System.exit(errors.size() == 0 ? 0 : 1);
				return;
			}
			
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

	public void logMessage(String message) {
		lastMessage = System.currentTimeMillis();
		System.out.print(LOG_FORMAT.format(LocalDateTime.now()));
		System.out.println(message);
	}
	
	public void logMessageTime(String message) {
		long now = System.currentTimeMillis();
		System.out.print(LOG_FORMAT.format(LocalDateTime.now()));
		System.out.print(message);
		System.out.print(" / ");
		System.out.println(now - lastMessage);
		lastMessage = now;
	}

	public boolean stopped(){
		for(Thread thread: threads){
			if(thread.isAlive()) return false;
		}
		return true;		
	}

	public void stopBuild(){
		clearWatcherTasks();

		if(threads.size() >0) logMessage("Stopping "+threads.size()+" watch threads");
		for(Thread thread: threads){
			System.out.println("THREAD: "+thread.getName());
			if(thread.isAlive()) thread.interrupt();
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
		logMessage("Stopped "+threads.size()+" threads in "+(System.currentTimeMillis() - stopTime)+" ms");
		System.out.println("\n\n\n");
		threads.clear();
		namedTasks.clear();
		included.clear();
	}
	
	public void runBuild(boolean watch) {
		
		logMessage("Run build");
		errors.clear();
		
		try {
			List<Path>paths = new ArrayList<>();
			paths.addAll(watchPaths);
			Collections.sort(paths);
			for(Path path:paths) System.out.println(path);
			if(watch) directoryWatcher = builder.paths(paths).listener(listener).build();
			logMessageTime("Init DirectoryWatcher with "+paths.size()+" paths");
			
			HashSet<Path> done = new HashSet<>();
			for(Path path:paths) initWatchers2(path, path.toFile(), new ArrayList<>(), true, done);
			logMessageTime("Init watchers file lists");
						
			if(watch) {				
				threads.add(new Thread(new Runnable() {
					public void run() {					
						directoryWatcher.watch();
					}
				},"Main Watcher thread"));

				threads.add(new Thread(new Worker(),"Watch files worker"));
			}

			logMessage("start tasks");
			for(AbstractTask<?> task:tasks) {
				Path path = task.getRootPath();
				if(path == null) {
					System.err.println("null root for "+task.id+" " +task);
				}else {					
					initRecursive(task, path, path.toFile());
				}
				logMessageTime("start task "+task.id+" " +task);				
				task.init(watch);
				task.start(watch);
			}
			
			if(threads.size() >0) logMessage("Starting "+threads.size()+" watch threads");
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

	class Worker implements Runnable{
		@Override
		public void run() {
			while(!Thread.interrupted()) {
				
				synchronized (notifyLock) {
					
					Set<FileDef> tmp = notifyCandidates;
					
					boolean hasNotifyList = notifyList.size() > 0;
					boolean hasNotifyCandidates = tmp.size() > 0;
					
					if(hasNotifyList || hasNotifyCandidates){						
						notifyCandidates = new HashSet<>();						
					}

					if(hasNotifyList) {
						for(FileDef def:notifyList){
							long lastModified = def.file.lastModified();
							long length = def.file.length();
							
							if(def.lastModified == lastModified && def.length == length) {
								// finally, no changes after a delay, we can notify listeners
								Path pathKey = def.path.getParent();
								do{
									List<FileMatchGlob2> list = watchers.get(pathKey);
									if(list != null) for(FileMatchGlob2 watcher:list){
										watcher.fileEvent(def, false);
									}
									pathKey = pathKey.getParent();
								}while(pathKey != null && !pathKey.equals(basePath));

							}else {
//								System.err.println("back as candidate "+def.path+" "+(lastModified - def.lastModified)+" "+(length - def.length));
								// return back to notify candidate
								def.lastModified = lastModified;
								def.length = length;
								notifyCandidates.add(def);
							}
						}
						
						// either notification done, or moved back to candidates
						notifyList.clear();	
					}
					
					if(tmp.size() > 0){ // previous notifyCandidates
						for(FileDef def:tmp){
							long lastModified = def.file.lastModified();
							long length = def.file.length();
							
							if(def.lastModified == lastModified && def.length == length) {
								// has not changed, make notification next time
								notifyList.add(def);
							}else {
//								System.err.println("Keep as candidate "+def.path+" "+(lastModified - def.lastModified)+" "+(length - def.length));
								// keep as candidate because it has changed, so we wait a bit more
								def.lastModified = lastModified;
								def.length = length;
								notifyCandidates.add(def);
							}
						}
					}
				}

				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {
					e.printStackTrace();
					break;
				}
			}
		}
	}
	
	public void initWatchers2(Path folderPath, File folder, List<FileMatchGlob2> watchersCurrent, boolean skip, Set<Path> done){
		if(done.contains(folderPath)) {
			logMessage("Skin double init "+folderPath);
			return;
		}

		List<FileMatchGlob2> list = watchers.get(folderPath);
		if(list != null && list.size() > 0) {
			List<FileMatchGlob2> tmp = watchersCurrent;
			watchersCurrent = new ArrayList<>();
			watchersCurrent.addAll(tmp);
			watchersCurrent.addAll(list);
		}
		
		File[] files = folder.listFiles();
		
		if(watchersCurrent.size() > 0){
			done.add(folderPath);
			for(File f:files) {
				Path childPath = f.toPath();
				if(f.isDirectory()) {
					initWatchers2(childPath, f, watchersCurrent, false, done);
				}else {
					FileDef def = fileCache.get(childPath);
					if(def == null) {
						def = new FileDef(childPath, EventType.CREATE);
					}else {
						def.update(EventType.CREATE);
					}
					for(FileMatchGlob2 watcher:watchersCurrent){
						//watcher.offer(def.path);
						watcher.fileEvent(def, true);
					}
				}
			}
		}else{
			for(File f:files) {
				Path childPath = f.toPath();
				if(f.isDirectory()) {
					if(!skip || watchersParents.contains(childPath))
						initWatchers2(childPath, f, watchersCurrent, skip, done);
				}
			}
		}
	}

	public void initRecursive(FileMatchGlob2 watcher, Path folderPath, File folder){
		File[] files = folder.listFiles();
		
		for(File f:files) {
			Path childPath = f.toPath();
			if(f.isDirectory()) {
				initRecursive(watcher,childPath, f);
			}else {
				FileDef def = fileCache.get(childPath);
				if(def == null) {
					def = new FileDef(childPath, EventType.CREATE);
				}else {
					def.update(EventType.CREATE);
				}
				watcher.fileEvent(def, true);
			}
		}
	}
	
	class BuildDirectoryChangeListener implements DirectoryChangeListener{
		@Override
		public void onEvent(DirectoryChangeEvent event) throws IOException {
			Path path = event.path();
			if(path == null) return;
			path = path.toAbsolutePath();
			synchronized (notifyLock) {
				FileDef def = fileCache.get(path);
				if(def == null) {
					def = new FileDef(path, event.eventType());
				}else {
					def.update(event.eventType());
				}
				notifyCandidates.add(def);				
			}			
		}
	}
	BuildDirectoryChangeListener listener = new BuildDirectoryChangeListener();
	List<AbstractTask<?>> tasks = new ArrayList<>();

	public void logSkipIdentical(int taskId, Path to) {
		hr.hrg.javawatcher.Main.logSkipIdentical(taskId, to);
	}

	public void logSkipOlder(int taskId, Path to) {
		hr.hrg.javawatcher.Main.logSkipOlder(taskId, to);
	}
	
	public void logUpdateSourceTimestamp(FileMatchGlob2 task, File fromFile, long lastModifiedTo) {
		if(initial) {
			if(hr.hrg.javawatcher.Main.isInfoEnabled()) 
				hr.hrg.javawatcher.Main.logInfo("Update TS:\t "+fromFile+" "+fromFile.lastModified());
		}else {
			if(hr.hrg.javawatcher.Main.isWarnEnabled())
				hr.hrg.javawatcher.Main.logWarn("Update TS:\t "+fromFile+" "+fromFile.lastModified());			
		}
	}

	public void registerSimpleOutputOnce(FileMatchGlob2 task, String descr, Path from, Path to) {
		OutputEntry outputEntry = outputs.get(to);
		if(outputEntry == null) {
			outputs.put(to, new OutputEntry(task, to, descr).simple(from));
		}else {
			if(outputEntry.getTask() != task) {
				// TODO notify conflict
				throw new RuntimeException("Multiple tasks generating same file. Task: "+outputEntry.getTask().getId()+" and Task: "+task.getId());
			}
			if(outputEntry.getDescr() != descr) {
				// TODO notify conflict				
				throw new RuntimeException("Multiple description for generating same file. Task: "+task.getId()+" Descr: "+outputEntry.getDescr()+" Descr: "+descr);
			}
		}
	}

	public void logSimpleOutput(Path to, long lastModified){
		OutputEntry outputEntry = outputs.get(to);
		if(outputEntry == null) throw new RuntimeException("Unable to find in/out definition for "+to);
		hr.hrg.javawatcher.Main.logInfo(outputEntry.getDescr()+":\t  "+outputEntry.getInput()+"\t TO "+to+" "+lastModified+" after: "+(System.currentTimeMillis()-lastModified));
	}
	
	
	public void clearWatcherTasks() {
		try {
			builder = DirectoryWatcher.builder();
			watchPaths = new HashSet<>();
			watchers = new HashMap<>();
			watchersList = new ArrayList<>();
			fileCache = new HashMap<>();
			watchersParents = new HashSet<>();
			if(directoryWatcher != null) directoryWatcher.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}		
	}
	
	public <T> void addWatcherTask(AbstractTask<T> task){
		Path rootPath = task.getRootPath();
		if(!rootPath.toFile().isDirectory()) throw new RuntimeException("Not a directory "+rootPath);
		if(rootPath == null) {
			throw new NullPointerException("rootPath");
		}

		if(!tasks.contains(task)) {
			tasks.add(task);
		}else {
			System.err.println("skipping add of existing task "+task);
		}
		watchPaths.add(rootPath);
		watchersList.add(task);
		Path pathKey = rootPath;
		do{
			if(!watchersParents.contains(pathKey)) {
				watchersParents.add(pathKey);
			}
			pathKey = pathKey.getParent();
		}while(pathKey != null && !pathKey.equals(basePath));

		List<FileMatchGlob2> list = watchers.get(rootPath);
		if(list == null) {
			list = new ArrayList<>();
			watchers.put(rootPath, list);
		}
		list.add(task);
	}
}
