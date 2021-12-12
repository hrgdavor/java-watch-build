package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;

public class CopyTaskFactory extends AbstractTaskFactory<CopyTask2,CopyConfig>{
		
	public CopyTaskFactory(WatchBuild core){
		super(core, new CopyConfig());
	}
	
	public CopyTaskFactory(WatchBuild core, String input, String output) {
		super(core, new CopyConfig());
		config.input = input;
		config.output = output;
	}

	@Override
	public CopyTask2 build() {
		return new CopyTask2(config, core);
	}

	public CopyTaskFactory compareBytes(boolean val) { config.compareBytes = val; return this; }
	public CopyTaskFactory compareBytes() { config.compareBytes = true; return this; }
	
	public CopyTaskFactory reverseSyncModified(boolean val) { config.reverseSyncModified = val; return this; }
	public CopyTaskFactory reverseSyncModified() { config.reverseSyncModified = true; return this; }
	
	public CopyTaskFactory include(String ...arr) { addAll(config.include, arr); return this; }
	public CopyTaskFactory exclude(String ...arr) { addAll(config.exclude, arr); return this; }
	public CopyTaskFactory include(List<String> list) { config.include.addAll(list); return this; }
	public CopyTaskFactory exclude(List<String> list) { config.exclude.addAll(list); return this; }
	
}
