package hr.hrg.watch.build.task;

import java.util.List;

import hr.hrg.watch.build.config.TaskDef;

public class NoOpTaskFactory implements TaskFactory{
	

	@Override
	public String getDefaultOptionParser() {
		return "lines";// this one does no extra processing
	}

	@Override
	public void start(TaskDef taskDef,List<Object> config, boolean watch) {
		
	}
	
	@Override
	public boolean alwaysRun() {
		return false;
	}
}
