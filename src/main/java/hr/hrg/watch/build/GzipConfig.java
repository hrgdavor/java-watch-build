package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class GzipConfig extends StepConfig{
	public List<Item> sets = new ArrayList<>();

	public static class Item{
		public String input;
		public String output;
		public List<String> include = new ArrayList<>();
		public List<String> exclude = new ArrayList<>();
	}	
}