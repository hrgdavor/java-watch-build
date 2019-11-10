package hr.hrg.watch.build.task;

import hr.hrg.watch.build.WatchBuild;

public abstract class AbstractTask<T> {
	
	protected T config;
	protected WatchBuild core;

	public AbstractTask(T config, WatchBuild core) {
		if(config == null) throw new NullPointerException("config");
		if(core == null) throw new NullPointerException("core");

		this.config = config;
		this.core = core;
	}
	
	public void start(boolean watch) {
		this.init(watch);
		if(watch && this instanceof Runnable) 
			core.addThread(new Thread((Runnable) this, this.toString()));
	}
	
	public abstract void init(boolean watch);
}
