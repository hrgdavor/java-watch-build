package hr.hrg.watch.build.task;

import static hr.hrg.watch.build.TaskUtils.*;

import java.util.List;

import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ProxyConfig;
import hr.hrg.watch.build.config.ProxyConfig.Item;

public class ProxyTaskFactory extends AbstractTaskFactory<ProxyTask, ProxyConfig>{

	
	public ProxyTaskFactory(WatchBuild core){
		super(core, new ProxyConfig());
	}

	public ProxyTaskFactory(WatchBuild core, int port) {
		super(core, new ProxyConfig());
		config.port = port;
	}
	
	@Override
	public ProxyTask build() {
		return new ProxyTask(config, core);
	}

	public ProxyTaskFactory noCache(String ...arr) { addAll(config.noCache, arr); return this; }
	public ProxyTaskFactory noCache(List<String> list) { config.noCache.addAll(list); return this; }
	
	public ProxyTaskFactory host(String val) { config.host = val; return this; }
	public ProxyTaskFactory httpsKeyStore(String val) { config.httpsKeyStore = val; return this; }
	public ProxyTaskFactory wwwRoot(String val) { config.wwwRoot = val; return this; }
	public ProxyTaskFactory proxyHeaders(boolean val) { config.proxyHeaders = val ? "true":"false"; return this; }

	public ProxyTaskFactory paths(String proxyTo, String ...paths) {
		for(String path:paths) {			
			config.items.add(new Item(proxyTo, path)); 
		}
		return this;
	}

	public ProxyTaskFactory path(String proxyTo, String path, String prefix) {
		config.items.add(new Item(proxyTo, path, prefix)); 
		return this;
	}

}

