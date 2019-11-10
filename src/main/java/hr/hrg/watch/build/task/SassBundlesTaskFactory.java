package hr.hrg.watch.build.task;

import java.nio.file.Path;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.SassBundlesConfig;

public class SassBundlesTaskFactory extends AbstractTaskFactory<SassBundlesTask,SassBundlesConfig> {


	public SassBundlesTaskFactory(WatchBuild core){
		super(core, new SassBundlesConfig());
	}
	
	@Override
	public SassBundlesTask build() {
		return new SassBundlesTask(config, core);
	}
	
	static class PathWithWeight implements Comparable<PathWithWeight>{

		Path path;
		int weight;

		public PathWithWeight(Path path, int weight) {
			this.path = path;
			this.weight = weight;
		}

		@Override
		public int compareTo(PathWithWeight o) {
			return weight - o.weight;
		}
	}
	
}
