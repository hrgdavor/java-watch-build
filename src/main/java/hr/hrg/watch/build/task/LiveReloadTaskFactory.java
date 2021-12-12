package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.LiveReloadConfig;

public class LiveReloadTaskFactory extends AbstractTaskFactory<LiveReloadTask2,LiveReloadConfig>{
		
	public LiveReloadTaskFactory(WatchBuild core){
		super(core, new LiveReloadConfig());
	}
	
	public LiveReloadTaskFactory(WatchBuild core, String input) {
		super(core, new LiveReloadConfig());
		config.input = input;
	}

	@Override
	public LiveReloadTask2 build() {
		return new LiveReloadTask2(config, core);
	}
	
	public LiveReloadTaskFactory port(int val) { config.port = val; return this; }
	public LiveReloadTaskFactory pauseAfterCss(long val) { config.pauseAfterCss = val; return this; }
	public LiveReloadTaskFactory liveReloadScript(String val) { config.liveReloadScript = val; return this; }

	public LiveReloadTaskFactory include(String ...arr) { addAll(config.include, arr); return this; }
	public LiveReloadTaskFactory exclude(String ...arr) { addAll(config.exclude, arr); return this; }
	public LiveReloadTaskFactory include(List<String> list) { config.include.addAll(list); return this; }
	public LiveReloadTaskFactory exclude(List<String> list) { config.exclude.addAll(list); return this; }
	
}
