package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("type")
public class SassBundlesConfig{
	public boolean compareBytes = true;
	public String root;
	public String output;
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();

}