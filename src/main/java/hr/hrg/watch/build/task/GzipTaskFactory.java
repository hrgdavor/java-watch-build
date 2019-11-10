package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;
import hr.hrg.watch.build.config.GzipConfig;

public class GzipTaskFactory extends AbstractTaskFactory<GzipTask, GzipConfig>{

	
	public GzipTaskFactory(WatchBuild core){
		super(core, new GzipConfig());
	}

	public GzipTaskFactory(WatchBuild core, String input, String output) {
		super(core, new GzipConfig());
		config.input = input;
		config.output = output;
	}
	
	@Override
	public GzipTask build() {
		return new GzipTask(config, core);
	}

	public GzipTaskFactory include(String ...arr) { addAll(config.include, arr); return this; }
	public GzipTaskFactory exclude(String ...arr) { addAll(config.exclude, arr); return this; }
	public GzipTaskFactory include(List<String> list) { config.include.addAll(list); return this; }
	public GzipTaskFactory exclude(List<String> list) { config.exclude.addAll(list); return this; }

}

