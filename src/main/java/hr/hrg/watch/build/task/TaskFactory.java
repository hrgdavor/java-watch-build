package hr.hrg.watch.build.task;

import java.util.List;

import hr.hrg.watch.build.config.TaskDef;

public interface TaskFactory {
	public String getDefaultOptionParser();
	
	public void start(TaskDef task, List<Object> config, boolean watch);

	public boolean alwaysRun();

}
