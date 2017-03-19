package hr.hrg.watch.build;

import java.util.ArrayList;
import java.util.List;

class LangConfig extends StepConfig{
	public String input;
	public String varName = "TRANS";
	public boolean compareBytes = true;
	public List<String> output = new ArrayList<>();
}