package hr.hrg.watch.build;

import java.util.List;

public interface TaskFactory {
	public String getDefaultOptionParser();
	
	public void start(String inlineParam, List<Object> config, boolean watch);

	public boolean alwaysRun();

}
