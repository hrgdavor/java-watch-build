package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class LangConfig{
	public String type="Lang";
	
	public List<String> input = new ArrayList<>();
	public List<String> output = new ArrayList<>();

	public String varName = "TRANS";
	public boolean compareBytes = true;
}


