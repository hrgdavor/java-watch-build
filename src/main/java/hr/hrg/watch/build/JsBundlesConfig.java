package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class JsBundlesConfig{
	public String name;
	public boolean compareBytes = true;
	public String root;
	public String suffix;
	public boolean outputJS;
	public boolean outputText;
	public String compilationLevel = "SIMPLE";
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
}