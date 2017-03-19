package hr.hrg.watch.build.option;

import hr.hrg.watch.build.config.TaskOption;

public interface OptionParser {
	public Object parse(TaskOption option);
}
