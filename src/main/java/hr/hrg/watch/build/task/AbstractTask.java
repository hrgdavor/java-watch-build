package hr.hrg.watch.build.task;

import java.nio.file.Path;

import hr.hrg.watch.build.FileMatchGlob2;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.CopyConfig;

public abstract class AbstractTask<T> extends FileMatchGlob2{
	
	protected T config;
	protected WatchBuild core;

	public AbstractTask(T config, WatchBuild core, Path root, boolean recursive) {
		super(root, recursive);
		if(config == null) throw new NullPointerException("config");
		if(core == null) throw new NullPointerException("core");

		this.config = config;
		this.core = core;
	}

	public AbstractTask(T config, WatchBuild core) {
		if(config == null) throw new NullPointerException("config");
		if(core == null) throw new NullPointerException("core");

		this.config = config;
		this.core = core;
	}
	
	public AbstractTask(T config, WatchBuild core, String root, boolean recursive) {
		this(config, core, core.getBasePath().resolve(root), recursive);
	}

	public void start(boolean watch) {
		if(watch && this instanceof Runnable) 
			core.addThread(new Thread((Runnable) this, this.toString()));
	}
	
	public abstract void init(boolean watch);
	public abstract boolean needsThread();
}
