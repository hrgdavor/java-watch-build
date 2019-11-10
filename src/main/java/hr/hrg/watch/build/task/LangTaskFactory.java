package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.LangConfig;

public class LangTaskFactory extends AbstractTaskFactory<LangTask, LangConfig>{

	public LangTaskFactory(WatchBuild core){
		super(core,new LangConfig());
	}
	
	public LangTaskFactory(WatchBuild core, String ...input) {
		super(core,new LangConfig());
		TaskUtils.addAll(config.input, input);
	}

	@Override
	public LangTask build() {
		return new LangTask(config, core);
	}
	
	public LangTaskFactory varName(String varName) { config.varName = varName; return this; }

	public LangTaskFactory output(String ...arr) { addAll(config.output, arr); return this; }
	public LangTaskFactory output(List<String> list) { config.output.addAll(list); return this; }
	
	public LangTaskFactory compareBytes(boolean val) { config.compareBytes = val; return this; }
	public LangTaskFactory compareBytes() { config.compareBytes = true; return this; }
	
	
}