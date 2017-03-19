package hr.hrg.watch.build.option;

import hr.hrg.watch.build.config.TaskOption;

public class LinesOptionParser implements OptionParser{

	@Override
	public Object parse(TaskOption option){
		return option.lines;
	}
	
}
