package hr.hrg.watch.build;

import hr.hrg.watch.build.Main.TaskOption;

public class LinesOptionParser implements OptionParser{

	@Override
	public Object parse(TaskOption option){
		return option.lines;
	}
	
}
