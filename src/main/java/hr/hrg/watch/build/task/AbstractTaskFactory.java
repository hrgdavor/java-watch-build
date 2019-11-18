package hr.hrg.watch.build.task;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.watch.build.WatchBuild;

public abstract class AbstractTaskFactory<T extends AbstractTask<?>,C> {
	
	protected WatchBuild core;
	protected C config;

	public AbstractTaskFactory(WatchBuild core, C conf) {
		this.core = core;
		this.config = conf;
	}

	@SuppressWarnings("unchecked")
	public T task(JsonNode configNode) {
		this.config = (C) core.getMapper().convertValue(configNode, config.getClass());
		return build();
	}
	
	public abstract T build();
	
	public C getConfig() {
		return config;
	}
}
