package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.io.File;
import java.util.Arrays;

import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.MkdirConfig;

public class MkdirTaskFactory extends AbstractTaskFactory<MkdirTaskFactory.Task,MkdirConfig>{
		
	
	public MkdirTaskFactory(WatchBuild core){
		super(core, new MkdirConfig());
	}
	
	public MkdirTaskFactory(WatchBuild core, String ...dirs) {
		super(core, new MkdirConfig(Arrays.asList(dirs)));
	}

	@Override
	public Task build() {
		for(String name:config.dirs){
			File dir = core.getBasePath().resolve(name).toFile();
			if(!dir.exists()) dir.mkdirs();
		}
		return new Task(config, core);
	}
	
	public MkdirTaskFactory dirs(String ...arr) { addAll(config.dirs, arr); return this; }
	
	public static class Task extends AbstractTask<MkdirConfig>{
		String dirStr = null;

		public Task(MkdirConfig config, WatchBuild core) {
			super(config, core);
			StringBuffer b = new StringBuffer();
			for(String name:config.dirs){
				b.append(name).append(",");
			}
			dirStr = b.toString();
		}

		@Override
		public void init(boolean watch) {
			for(String name:config.dirs){
				File dir = core.getBasePath().resolve(name).toFile();
				if(!dir.exists()) dir.mkdirs();
			}
		}
		@Override
		public String toString() {
			return "Mkdir:"+dirStr;
		}
	}

}
