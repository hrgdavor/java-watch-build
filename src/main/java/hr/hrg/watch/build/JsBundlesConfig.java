package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class JsBundlesConfig extends StepConfig{
	public List<Item> sets = new ArrayList<>();

	public static class Item {
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
}