package hr.hrg.watch.build.task;

import java.util.List;

public class NoOpTaskFactory implements TaskFactory{
	

	@Override
	public String getDefaultOptionParser() {
		return "lines";// this one does no extra processing
	}

	@Override
	public void start(String inlineParam,List<Object> config, boolean watch) {
		
	}
	
	@Override
	public boolean alwaysRun() {
		return false;
	}
}
