package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

public class JsBundlesConfig{

	public boolean compareBytes = true;
	public String root;
	public boolean outputJS;
	public boolean outputText;
	public String compilationLevel = "SIMPLE";
	public boolean perLanguage = true;
	public List<BundleEntry> bundles = new ArrayList<>();
	
	public static class BundleEntry{
		public String name;
		public List<String> include = new ArrayList<>();
		public List<String> exclude = new ArrayList<>();
		
	}
}


/*

package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

public class JsBundlesConfig{

	public String name;
	public Boolean compareBytes;
	public String root;
	public Boolean outputJS;
	public Boolean outputText;
	public String compilationLevel;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();

	public boolean perLanguage = true;
	
	public Defaults defaults = new Defaults(); 
	
	public static class Defaults{
		public boolean compareBytes = true;
		public String root;
		public String jsRoot = "js";	
		public String srcRoot = "src";	
		public boolean outputJS;
		public boolean outputText;
		public String compilationLevel = "SIMPLE";
		
	}
}
*/