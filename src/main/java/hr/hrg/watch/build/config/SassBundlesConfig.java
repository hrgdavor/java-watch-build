package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

public class SassBundlesConfig{
	public boolean compareBytes = true;
	public String root;
	public String output;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();

}