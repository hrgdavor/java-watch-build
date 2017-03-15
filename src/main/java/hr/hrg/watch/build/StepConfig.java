package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

public class StepConfig {
	public List<String> profiles = new ArrayList<>();
	public boolean perLanguage;
	public String type;
	public String name;

	public String getName() {
		return name == null ? type:name;
	}
	
}
