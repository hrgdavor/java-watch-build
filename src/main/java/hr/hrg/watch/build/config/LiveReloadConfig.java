package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("type")
public class LiveReloadConfig{
	
	public String input;
	public String liveReloadScript;
	public int port = 35729;
	public long pauseAfterCss = 0;
	
	public List<String> include = new ArrayList<>();
	public List<String> exclude = new ArrayList<>();
	
}