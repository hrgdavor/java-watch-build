package hr.hrg.watch.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class Main {

	protected ObjectMapper mapper   = new ObjectMapper();
	protected YAMLMapper yamlMapper = new YAMLMapper();

	protected Path confFile;
	protected HashMap<Path, Path> included = new LinkedHashMap<>();
	
	protected HashMap<String, TaskFactory> runners = new LinkedHashMap<>();
	protected HashMap<String, OptionParser> parsers = new LinkedHashMap<>();
	
	protected Map<String, Object> namedTasks = new ConcurrentHashMap<>();	
	protected List<Thread> threads = new ArrayList<>();

	private boolean watch;
	private boolean dryRun;
	private Path outputRoot;
	private long burstDelay = 50;
	protected VarMap vars = new VarMap();
	
	String lang;
	String[] langs;
	
	public static void main(String[] args) {
		if(args.length == 0) printHelp();
		
		if(args[0].endsWith(".js")){
			ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
			String script = "var hrhrgwatchbuildMain = Java.type('hr.hrg.watch.build.MainOld');" 
					+"function alert(x){print(x);};\n"
					+"function runBuild(conf,profile,lang,watch){\n"
					+"	hrhrgwatchbuildMain.runBuild(conf,profile,lang,watch);\n"
					+"}\n"
					+"\n"
					;
			try {
				engine.eval(script);
				engine.eval(new FileReader(new File(args[0])));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else if(args[0].endsWith(".yml") || args[0].endsWith(".yaml")){
			new Main().runFromArgs(args);			
			
		}else{
			System.out.println("Supporting only script(*.js) or YAML configuration (*.yml,*.yaml)");
		}

	}
	
	public Main(){
		addOptionParser("lines", new LinesOptionParser());
		addOptionParser("Yaml", new YamlOptionParser(this,yamlMapper, false));
		addOptionParser("YamlPerLanguage", new YamlOptionParser(this,yamlMapper, true));

		addRunner("var", new VarTaskFactory(this,true));
		addRunner("defvar", new VarTaskFactory(this,false));
		addRunner("import", new ImportTaskFactory(this));
		addRunner("jsbundles", new JsBundlesTaskFactory(this,mapper));
		addRunner("sass", new SassTaskFactory(this,mapper));
		addRunner("copy", new CopyTaskFactory(this,mapper));
		addRunner("gzip", new GzipTaskFactory(this,mapper));
		addRunner("gzip", new GzipTaskFactory(this,mapper));
		addRunner("language", new LangTaskFactory(this,mapper, yamlMapper));
		addRunner("jscomp", new JsCompTaskFactory(this,mapper));
		addRunner("HtmlScriptAndCss", new HtmlScriptAndCssRunner(this,mapper));
	}
	
	public void addRunner(String code, TaskFactory runner){
		runners.put(code.toLowerCase(), runner);
	}
	
	public TaskFactory getRunner(String code) {
		return runners.get(code);
	}

	public void addOptionParser(String code, OptionParser runner){
		parsers.put(code.toLowerCase(), runner);
	}
	
	public OptionParser getOptionParser(String code) {
		return parsers.get(code);
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
	
	protected void runFromArgs(String[] args) {
		watch = false;
		dryRun = false;

		for(int i=1; i<args.length; i++) {
			if("--watch".equals(args[i])) watch = true;
			else if("--dry-run".equals(args[i])) dryRun = true;
		}
		
		runBuild(args[0]);
	}

	public void runBuild(String file) {
		try {
			
			confFile = Paths.get(file);
			outputRoot = confFile.toAbsolutePath().getParent();
			
			loadFile(confFile);
			
			if(threads.size() >0) System.out.println("Starting "+threads.size()+" watch threads");
			for(Thread thread: threads){
				System.out.println("THREAD: "+thread.getName());
				thread.start();
			}
			
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	enum Token {START,TASK,OPTION}
	
	public void loadFile(Path confFile){
	
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
					option.lines.add(TaskUtils.emptyOrcomment(trimmed) ? trimmed:line);
				}
			}
			finishTaskProcessing(task,hasNonEmptyLines);
			
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			System.out.println("Errro loading "+confFile.toAbsolutePath()+" "+e.getMessage());
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	protected void finishTaskProcessing(TaskDef task, boolean hasNonEmptyLines) {
		try {
			if(dryRun) System.out.println("TASK: "+task.type);
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
			TaskFactory taskFactory = runners.get(task.type);
			if(taskFactory == null) {
				throw new RuntimeException(" Task runner type: "+task.type+" not found "+task.confFile.toAbsolutePath()+":"+task.lineNumber);
			}else {
				if(task.options.get(0).type == null) {
					if(dryRun) System.out.println("Using default OptionParser "+taskFactory.getDefaultOptionParser()+" for task "+task.confFile.toAbsolutePath()+":"+task.options.get(0).lineNumber);
					task.options.get(0).type = taskFactory.getDefaultOptionParser();
				}
				if(!dryRun || taskFactory.alwaysRun()) {
					ArrayList<Object> options = new ArrayList<>();
					for(TaskOption op: task.options) {
						OptionParser optionParser = parsers.get(op.type);
						if(optionParser == null)
							throw new RuntimeException(" OptionParser type: "+op.type+" not found "+op.confFile.toAbsolutePath()+":"+op.lineNumber);
						
						options.add(optionParser.parse(op));
					}
					taskFactory.start(task.params, options, watch);
					if(taskFactory instanceof VarTaskFactory) {
						updateLang();
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
	
	private void updateLang(){
		lang = vars.get("lang");
		if(lang == null ) lang = "";
		langs = new String[]{lang};
		if(lang.indexOf(',') != -1) {
			langs = lang.split(",");
			lang = langs[0];
		}
	}

	public VarMap getVars() {
		return vars;
	}

	static class ConfDef{
		public Path confFile;
		public int lineNumber;		
		public String type;
		public String params;
	}

	static class TaskDef extends ConfDef{
		public List<TaskOption> options = new ArrayList<>();
		
		public TaskDef(Path confFile,int lineNumber, String ...params) {
			this.confFile = confFile;
			this.lineNumber = lineNumber;
			type = params[0].toLowerCase();
			this.params = params[1];
		}
	}
	
	static class TaskOption extends ConfDef{
		public List<String> lines = new ArrayList<>();

		public TaskOption(Path confFile, int lineNumber, String ...params) {
			this.confFile = confFile;
			this.lineNumber = lineNumber;
			if(params.length >0) type = params[0].toLowerCase();
			if(params.length >1) this.params = params[1];
		}
	}

	public static void printHelp(){
		System.out.println("Usage: conf|script ");
		System.out.println("\t conf      - configuration file in yml format (requires profile and lang)");
		System.out.println("\t script    - javascript script that will be executed using nashorn");
		System.out.println("\t --watch   - continue watching after build");
		System.out.println("\t --dry-run - show final configuration for checking the end result is what was intended");
		System.exit(0);
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
