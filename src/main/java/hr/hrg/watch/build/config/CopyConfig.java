package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class CopyConfig{
	public String type="Copy";

	public String input;
	public String output;
	
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	public Map<String, String> rename = new HashMap<>();
	
	public boolean compareBytes = true;

	public boolean reverseSyncModified;
}