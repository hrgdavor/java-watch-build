package hr.hrg.watch.build.config;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class JsBundlesConfig{
	public String type="JsBundles";

	public boolean compareBytes = true;

	public String root;
	public boolean outputText;
	public List<BundleEntry> bundles = new ArrayList<>();
	
	public static class BundleEntry{
		public String name;
		@JsonIgnore(false)
		public List<String> include = new ArrayList<>();
		@JsonIgnore(false)
		public List<String> exclude = new ArrayList<>();
		
		@JsonIgnore
		public BundleEntry include(String ...arr) { addAll(include, arr); return this; }
		@JsonIgnore
		public BundleEntry include(List<String> list) { include.addAll(list); return this; }
		@JsonIgnore
		public BundleEntry exclude(String ...arr) { addAll(exclude, arr); return this; }
		@JsonIgnore
		public BundleEntry exclude(List<String> list) { exclude.addAll(list); return this; }
	}
}

