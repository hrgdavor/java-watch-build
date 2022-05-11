package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ExtConfig;

public class ExtTaskFactory extends AbstractTaskFactory<ExtTask, ExtConfig> {

	
	public ExtTaskFactory(WatchBuild core){
		super(core, new ExtConfig());
	}

	public ExtTaskFactory(WatchBuild core, String cmd, String ...params) {
		super(core, new ExtConfig());
		config.cmd = cmd;
		config.params = params;
	}

	@Override
	public ExtTask build() {
		if(config.options == null) config.options = core.getMapper().createObjectNode();
		config.options.put("verbose", hr.hrg.javawatcher.Main.VERBOSE);
		return new ExtTask(core, config);
	}

	public ExtTaskFactory input(String val) { config.input = val; return this; }
	public ExtTaskFactory output(String val) { config.output = val; return this; }
	
	public ExtTaskFactory srcRoot(String srcRoot) { config.srcRoot = srcRoot; return this; }

	public ExtTaskFactory include(String ...arr) { addAll(config.include, arr); return this; }
	public ExtTaskFactory exclude(String ...arr) { addAll(config.exclude, arr); return this; }
	public ExtTaskFactory include(List<String> list) { config.include.addAll(list); return this; }
	public ExtTaskFactory exclude(List<String> list) { config.exclude.addAll(list); return this; }
	public ExtTaskFactory runOnly(boolean v) { config.runOnly = v; return this; }

	public ExtTaskFactory optionsPojo(Object options) { config.options = core.getMapper().convertValue(options, ObjectNode.class); return this; }
	public ExtTaskFactory options(Object ...options) { config.options = toObjectNode(config.options, core.getMapper(), options); return this; }
		
}
