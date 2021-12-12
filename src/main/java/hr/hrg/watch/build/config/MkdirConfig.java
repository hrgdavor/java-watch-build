package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

public class MkdirConfig {

	public String type="Mkdir";

	public List<String> dirs = new ArrayList<>();
	
	public MkdirConfig() {
	}
	
	public MkdirConfig(List<String> dirs) {
		this.dirs = dirs;
	}
}
