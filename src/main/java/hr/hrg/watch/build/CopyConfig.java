package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class CopyConfig{
	public String input;
	public List<String> altFolder = new ArrayList<>();
	public String output;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	public boolean compareBytes = true;
}