package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class CompConfig extends StepConfig{
	public String lang;
	public List<Item> sets = new ArrayList<>();

	public static class Item{
		public String input;
		public List<String> altFolder = new ArrayList<>();
		public String output;
		public List<String> include = new ArrayList<>();
		public List<String> exclude = new ArrayList<>();
		public boolean compareBytes = true;
	}
}