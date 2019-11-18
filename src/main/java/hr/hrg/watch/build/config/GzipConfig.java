package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class GzipConfig{
	public String type="Gzip";

	public String input;

	public String output;

	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	
}