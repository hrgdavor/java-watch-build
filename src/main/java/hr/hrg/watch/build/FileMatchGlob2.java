package hr.hrg.watch.build;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;

import hr.hrg.watch.build.FileDef;
import io.methvin.watcher.DirectoryChangeEvent.EventType;
import io.methvin.watcher.DirectoryWatcher;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

/** 
 * {@link FileMatcher} implementation that uses glob syntax by default. 
 * The rule can be a regex if prefixed with {@code regex:} (see: {@link #makeRule(String)}).<br>
 * The {@link #collectMatched}
 * 
 *  @see <a href="https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob">What is a glob</a>
 *  */
public class FileMatchGlob2{

	private static final String NOT_COLLECTING_EXCLUDED = "This matcher is not collecting excluded files. Call setCollectExcluded(true) or override this method if you do not want exception thrown. ";

	private static final String NOT_COLLECTING_MATCHES = "This matcher is not collecting matches. Call setCollectMatched(true) or override this method if you do not want exception thrown. ";

	//static final Logger log = LoggerFactory.getLogger(FileMatchGlob.class);

    protected String rootString;
    protected boolean recursive;
    protected DirectoryWatcher watcher;
    
	protected List<PathMatcher> includes = new ArrayList<>();
	protected List<PathMatcher> excludes = new ArrayList<>();

	protected volatile boolean started = false;
	protected Path rootPath;

	/** Default: TRUE. If this matcher will collect matched Files.  */
	protected boolean collectMatched = true;
	protected Set<Path> matched = new TreeSet<>();

	/** Default: FALSE. If this matcher will collect unmatched Files.  */
	protected boolean collectExcluded = false;
	protected Set<Path> excluded = new TreeSet<>();

	protected int id = hr.hrg.javawatcher.Main.nextId();

	public FileMatchGlob2() {
		// TODO Auto-generated constructor stub
	}
	
	public FileMatchGlob2(Path root, boolean recursive){
		if(!root.isAbsolute()) root = root.toAbsolutePath();
		this.rootPath = root.normalize();
		this.recursive = recursive;
		this.rootString = rootPath.toString().replace('\\', '/');		
	}
	
	/**
	 * Generate PathMatcher based on the rule. If the rule starts with {@code regex:} then it is used unchanged.
	 * The default is the glob syntax, and in that case prefix {@code glob:}+{@code root}+{@code /} is added so the glob
	 * works as expected. The default glob syntax is relative to root of the matcher, but {@code regex} must assume the 
	 * full path to the file. You can however add root to the {@code regex} where desired yourself. 
	 * 
	 * */
	public PathMatcher makeRule(String rule){
		if(rule.startsWith("regex:")) return FileSystems.getDefault().getPathMatcher(rule);
		return FileSystems.getDefault().getPathMatcher("glob:"+rule);
	}

	public FileMatchGlob2 includes(Collection<String> globs){
		for (String glob : globs) {
			includes.add(makeRule(glob));
		}
		return this;
	}

	public FileMatchGlob2 includes(String ... globs){
		for (String glob : globs) {
			includes.add(makeRule(glob));
		}
		return this;
	}

	public FileMatchGlob2 excludes(Collection<String> globs){
		for (String glob : globs) {
			excludes.add(makeRule(glob));
		}
		return this;
	}
	
	public FileMatchGlob2 excludes(String ... globs){
		for (String glob : globs) {
			excludes.add(makeRule(glob));
		}
		return this;
	}

	/**
	 * Remove all Paths that are in the specified directory.
	 * */
	public static final void removeAllFromDir(Path path, Collection<Path> collection){
		Iterator<Path> iterator = collection.iterator();
		Path p = null;
		while(iterator.hasNext()){
			p = iterator.next();
			if(path.equals(p.getParent())) iterator.remove();
		}		
	}
	
	public List<PathMatcher> getExcludes() {
		return excludes;
	}
	
	public List<PathMatcher> getIncludes() {
		return includes;
	}

	/** 
	 * {@inheritDoc}
	 */
	public Collection<Path> getMatched(){
		if(!collectMatched) throw new RuntimeException(NOT_COLLECTING_MATCHES+toString());
		return matched;
	}

	public int getMatchedCount(){
		if(!collectMatched) throw new RuntimeException(NOT_COLLECTING_MATCHES+toString());
		return matched.size();
	}

	/**
	 * Get the current collection of files offered but excluded based on the rules.
	 * You must {@link #setCollectExcluded(boolean)} during initialisation, or the list will be empty.
	 * */
	public Collection<Path> getExcluded(){
		if(!collectExcluded) throw new RuntimeException(NOT_COLLECTING_EXCLUDED+toString());
		return excluded;
	}
	
	public int getExcludedCount(){
		if(!collectExcluded) throw new RuntimeException(NOT_COLLECTING_EXCLUDED+toString());
		return excluded.size();
	}

	public boolean isCollectExcluded() {
		return collectExcluded;
	}
	
	public boolean isCollectMatched() {
		return collectMatched;
	}

	/**
	 * Are excluded files for later listing. Use when you want to know what files were excluded.
	 * */
	public void setCollectExcluded(boolean collectExcluded) {
		this.collectExcluded = collectExcluded;
	}
	
	/** 
	 * {@inheritDoc}
	 */
	public void setCollectMatched(boolean collectMatched) {
		this.collectMatched = collectMatched;
	}
	
	// -------------------------- implements FolderGlob ------- interface --------------------------
	
	/** {@inheritDoc} */
	public final boolean isMatch(Path path){
		if(!path.isAbsolute()) path = path.toAbsolutePath().normalize();
		return _isMatch(rootPath.relativize(path));
	}
	
	private final boolean _isMatch(Path path){
		if(includes.size() >0){
			boolean included = false;
			for (PathMatcher inc : includes) {
				if(inc.matches(path)){
					included = true;
					break;
				}
			}
			if(!included) return false;
		}
		for (PathMatcher ex : excludes) {
			if(ex.matches(path)) return false;
		}
		return true;
	}

	/** {@inheritDoc} */
	public boolean isExcluded(Path path){
		for (PathMatcher ex : excludes) {
			if(ex.matches(path)) return true;
		}
		return false;
	}
		
	/** {@inheritDoc} */
	public Path getRootPath() {
		return rootPath;
	}
	
	/** {@inheritDoc} */
	public boolean isRecursive() {
		return recursive;
	}
				
	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "FileMatchGlob2:"+rootPath+" "+getClass().getName();
	}

	public void fileEvent(FileDef def,boolean initial) {
		Path path = def.path;
		Path relative = rootPath.relativize(path);
		if(_isMatch(relative)) {
			if(initial && collectMatched) matched.add(path);
			this.matched(def, relative, initial);
		}else {
			if(initial && collectExcluded) excluded.add(path);			
		}
	}

	protected void matched(FileDef def, Path relative, boolean initial) {
		
	}
	public int getId() {
		return id;
	}
	
	/** init after files have been offered */
	public void init(boolean watch) {
		
	}
}
