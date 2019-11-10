package hr.hrg.watch.build.task;

import java.util.List;

import hr.hrg.watch.build.TaskUtils;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.JsBundlesConfig;
import hr.hrg.watch.build.config.JsBundlesConfig.BundleEntry;

public class JsBundlesTaskFactory extends AbstractTaskFactory<JsBundlesTask, JsBundlesConfig> {


	public JsBundlesTaskFactory(WatchBuild core){
		super(core,new JsBundlesConfig());
	}
	
	public JsBundlesTaskFactory(WatchBuild core, String root) {
		super(core,new JsBundlesConfig());
		config.root = root;
	}

	@Override
	public JsBundlesTask build() {
		return new JsBundlesTask(config, core);
	}

	public JsBundlesTaskFactory compareBytes(boolean val) { config.compareBytes = val; return this; }
	public JsBundlesTaskFactory compareBytes() { config.compareBytes = true; return this; }
	
	public JsBundlesTaskFactory outputText(boolean val) { config.outputText = val; return this; }
	public JsBundlesTaskFactory outputText() { config.outputText = true; return this; }
	
	
	public BundleEntry add(String name, String ...include) {
		
		BundleEntry entry = new JsBundlesConfig.BundleEntry();
		
		entry.name = name;
		TaskUtils.addAll(entry.include, include);
		
		config.bundles.add(entry);
		
		return entry;
	}	

	public BundleEntry add(String name, List<String> include) {
		BundleEntry entry = new JsBundlesConfig.BundleEntry();
		entry.include.addAll(include);
		return entry;
	}	
}
