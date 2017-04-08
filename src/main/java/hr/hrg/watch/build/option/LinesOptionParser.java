package hr.hrg.watch.build.option;

import hr.hrg.watch.build.config.TaskOption;

public class LinesOptionParser implements OptionParser{
	
	public LinesOptionParser() {
	}
	
	@Override
	public Object parse(TaskOption option){
		return option.lines;
	}
	
}
