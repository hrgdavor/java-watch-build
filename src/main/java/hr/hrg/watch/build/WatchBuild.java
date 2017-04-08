package hr.hrg.watch.build;

import java.io.BufferedReader;
import java.io.FileReader;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.config.ConfigException;
import hr.hrg.watch.build.config.TaskDef;
import hr.hrg.watch.build.config.TaskOption;
import hr.hrg.watch.build.option.OptionParser;
import hr.hrg.watch.build.task.TaskFactory;
import hr.hrg.watch.build.task.VarTaskFactory;

public class WatchBuild {

	Logger log = LoggerFactory.getLogger(WatchBuild.class);
	
	protected JsonMapper mapper   = new JsonMapper();
	protected YAMLMapper yamlMapper = new YAMLMapper();

	protected Path confFile;
	protected HashMap<Path, Path> included = new LinkedHashMap<>();
	
	protected HashMap<String, TaskFactory> factories = new LinkedHashMap<>();
	protected HashMap<String, OptionParser> parsers = new LinkedHashMap<>();
	
	protected Map<String, Object> namedTasks = new ConcurrentHashMap<>();	
	protected List<Thread> threads = new ArrayList<>();

	private boolean watch;
	private boolean dryRun;
	private Path outputRoot;
	private long burstDelay = 50;
	protected VarMap vars;
	
	String lang;
	String[] langs;
	
	public WatchBuild(){
		vars = new VarMap();
	}

	public WatchBuild(String varPattern){
		vars = new VarMap(varPattern);
	}

	public void setFactories(Map<String, TaskFactory> factories) {
		for(Entry<String, TaskFactory> e:factories.entrySet()) {
			this.factories.put(e.getKey().toLowerCase(), e.getValue());
		}
	}

	public void setParsers(Map<String, OptionParser> parsers) {
		for(Entry<String, OptionParser> e:parsers.entrySet()) {
			this.parsers.put(e.getKey().toLowerCase(), e.getValue());
		}
	}
	
	public void addRunner(Map.Entry<String, TaskFactory> e){
		factories.put(e.getKey().toLowerCase(), e.getValue());
	}

	public void addRunner(String code, TaskFactory runner){
		factories.put(code.toLowerCase(), runner);
	}
	
	public TaskFactory getRunner(String code) {
		return factories.get(code);
	}

	public void addOptionParser(String code, OptionParser runner){
		parsers.put(code.toLowerCase(), runner);
	}
	
	public OptionParser getOptionParser(String code) {
		return parsers.get(code.toLowerCase());
	}

	public void addThread(Thread thread){
		threads.add(thread);
	}
	
	public Path getOutputRoot() {
		return outputRoot;
	}
	
	public long getBurstDelay() {
		return burstDelay;
	}
	
	public void setBurstDelay(long burstDelay) {
		this.burstDelay = burstDelay;
	}
	
	protected void runFromArgs(String[] args) {
		watch = false;
		dryRun = false;

		for(int i=1; i<args.length; i++) {
			if("--watch".equals(args[i])) watch = true;
			else if("--dry-run".equals(args[i])) dryRun = true;
		}
		
		runBuild(args[0]);
		if(!watch) return;
		
		System.out.println();
		System.out.println("type: \"r\" and press <ENTER> to reload the script");
		System.out.println("type: \"q\" and press <ENTER> to quit");
		
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String line = null;
			while((line=br.readLine()) != null) {
				if(line.toLowerCase().equals("r")){
					restartBuild();
				}else if(line.toLowerCase().equals("s")){
					stopBuild();
				}else if(line.toLowerCase().equals("q")){
					stopBuild();
					System.exit(0);
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(),e);
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
	
	public void restartBuild() {
		stopBuild();
		startBuild();
	}
	public void runBuild(String file) {
		confFile = Paths.get(file);
		outputRoot = confFile.toAbsolutePath().getParent();
		startBuild();
	}

	public void startBuild() {
		try {
			
			loadFile(confFile);
			
			if(threads.size() >0) System.out.println("Starting "+threads.size()+" watch threads");
			for(Thread thread: threads){
				System.out.println("THREAD: "+thread.getName());
				thread.start();
			}
			
		} catch (ConfigException e) {
			log.error(e.getMessage(),e);
			log.error("Configuration Error ******************************************************************************************************** \n\n"
					+e.getInfoNl() + "\n"+e.getMessage()+"\n\n");
			log.error("Configuration Error ******************************************************************************************************** ");
		} catch (Exception e) {
			log.error(e.getMessage(),e);;
		}
	}

	enum Token {START,TASK,OPTION}
	
	public void loadFile(Path confFile){
		confFile = confFile.toAbsolutePath();
		
		if(included.containsKey(confFile)) {
			System.out.println("Skipping already included conf: "+confFile);
			return;
		}
		included.put(confFile, confFile);
		
		String newLine = System.getProperty("line.separator");

		String line = null;
		String trimmed;
		int lineNumber = 0;
		
		List<TaskDef> tasks = new ArrayList<>();
		// current task and option while parsing
		TaskDef task = null;
		TaskOption option = null;
		boolean hasNonEmptyLines = false;
		
		try(
				BufferedReader br = new BufferedReader(new FileReader(confFile.toFile()));
				){
			while((line=br.readLine()) != null) {
				lineNumber++;
				// fail if there are tabs (tabs would just cause problems if added accidentally)
				for(int i=0; i<line.length(); i++) {
					if(line.charAt(i) == '\t') {
						throw new Exception(confFile.toAbsolutePath()+":"+lineNumber+":"+(i+1)+" TAB character not allowed inside the script");
					}else if(line.charAt(i) != ' '){
						break;
					}
				}
				trimmed = line.trim();
				
				if(!trimmed.isEmpty() && trimmed.charAt(0) == '@'){// start: TASK|OPTION

					if(trimmed.charAt(1) == '@'){// start: OPTION
						if(task == null) throw new Exception(confFile.toAbsolutePath()+":"+lineNumber+" Option defined before any task was defined");
						
						// this make sense for the first option (for example if there is empty line or comment between task and firdt option)
						if(!hasNonEmptyLines && task.options.size() == 1) task.options.remove(0);
						
						if("@@end".equals(trimmed.toLowerCase())) {
							option = null;
						}else {
							option = new TaskOption(confFile,lineNumber, TaskUtils.parseDefLine(trimmed.substring(2).trim()));
						}

					}else{// start: TASK
						if(task != null) finishTaskProcessing(task,hasNonEmptyLines);
						if("@@end".equals(trimmed.toLowerCase())) {
							option = null;
						}else {							
							task = new TaskDef(confFile,lineNumber,TaskUtils.parseDefLine(trimmed.substring(1).trim()));
							tasks.add(task);
							
							option = new TaskOption(confFile,lineNumber);
						}
						hasNonEmptyLines = false;
					}
					task.options.add(option);
					
				}else{// lines for current token
					if(option == null && !TaskUtils.emptyOrcomment(trimmed)){
						throw new Exception(confFile.toAbsolutePath()+":"+lineNumber+" Only empty lines and comments allowed before any task is defined");
					}
					// to recognize empty space between task definition and first option
					if(!TaskUtils.emptyOrcomment(trimmed)) hasNonEmptyLines = true;

					// all comment lines are kept trimmed for easier checking later if the line is comment line
					// but we need to keep them so proper line numbers could be reported in case of errors
					if(option != null) // ignore initial comment and empty lines
						option.lines.add(TaskUtils.emptyOrcomment(trimmed) ? trimmed:line);
				}
			}
			finishTaskProcessing(task,hasNonEmptyLines);
			
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new ConfigException(e.getMessage(),e);
		}
	}

	protected void finishTaskProcessing(TaskDef task, boolean hasNonEmptyLines) {
		try {
			if(dryRun) System.out.println("TASK: "+task.type+" config lines: "+task.options.get(0).lines.size());
			int minIndent = -1;
			for(TaskOption option: task.options){
				for(String line: option.lines){
					if(TaskUtils.emptyOrcomment(line)) continue;
					for(int i=0; i<line.length(); i++) {
						if(line.charAt(i) != ' '){
							if(minIndent == -1 ||  i<minIndent) minIndent = i;
							break;
						}
					}
				}
				if(dryRun) System.out.println("OPTION: "+option.type+" indent: "+minIndent);
			}
			TaskFactory taskFactory = factories.get(task.type);
			if(taskFactory == null) {
				System.out.println("Please use one of available task factories: "+getJson(factories.keySet()));
				throw new RuntimeException(" Task runner type: "+task.type+" not found "+task.confFile.toAbsolutePath()+":"+task.lineNumber);
			}else {
				if(task.options.get(0).type == null) {
					if(dryRun) System.out.println("Using default OptionParser "+taskFactory.getDefaultOptionParser()+" for task "+task.confFile.toAbsolutePath()+":"+task.options.get(0).lineNumber);
					task.options.get(0).type = taskFactory.getDefaultOptionParser();
				}
				if(!dryRun || taskFactory.alwaysRun()) {
					ArrayList<Object> options = new ArrayList<>();
					for(TaskOption op: task.options) {
						OptionParser optionParser = getOptionParser(op.type);
						if(optionParser == null) {
							System.out.println("Please use one of available option parsers: "+getJson(parsers.keySet()));
							throw new RuntimeException(" OptionParser type: "+op.type+" not found "+op.confFile.toAbsolutePath()+":"+op.lineNumber);
						}
						
						options.add(optionParser.parse(op));
					}
					taskFactory.start(task.params, options, watch);
					if(taskFactory instanceof VarTaskFactory) {
						afterVars();
					}
				}
			}
			
		} catch (ConfigException e) {
			if(e.isWithConfigInfo()) throw e;
			throw new ConfigException(task,e.getMessage(),e);
		} catch (RuntimeException e) {
			throw new ConfigException(task,e.getMessage(),e);
		}
	}
	
	private void afterVars(){
		lang = vars.get("lang");
		if(lang == null ) lang = "";
		langs = new String[]{lang};
		if(lang.indexOf(',') != -1) {
			langs = lang.split(",");
			lang = langs[0];
		}
		try {
			if(vars.containsKey("burstDelay")) burstDelay = Long.parseLong(vars.get("burstDelay"));			
		} catch (Exception e) {
			log.error("Invalid value for burstDelay: "+vars.get("burstDelay"));
		}
	}

	public VarMap getVars() {
		return vars;
	}

	public String getLang() {
		return lang;
	}
	
	public String[] getLangs() {
		return langs;
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

}
