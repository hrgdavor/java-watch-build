package hr.hrg.watch.build.option;

import javax.inject.Inject;

import hr.hrg.watch.build.config.TaskOption;

public class LinesOptionParser implements OptionParser{
	
	@Inject
	public LinesOptionParser() {
	}
	
	@Override
	public Object parse(TaskOption option){
		return option.lines;
	}
	
}
