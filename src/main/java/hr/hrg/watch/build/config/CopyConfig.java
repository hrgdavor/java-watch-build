package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("type")
public class CopyConfig{
	public String input;
	public String output;
	
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	
	public boolean compareBytes = true;

	public boolean reverseSyncModified;
}