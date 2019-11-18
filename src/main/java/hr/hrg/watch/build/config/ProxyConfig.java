package hr.hrg.watch.build.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProxyConfig{
	public String type="Proxy";

	public int port=8080;
	public String host = "127.0.0.1";
	public String wwwRoot = "www";
	public String proxyHeaders = "true";

	public String httpsKeyStore = null;
	public String httpsKeyStorePass = null;

	public List<String> noCache = new ArrayList<>();

	public List<Item> items = new ArrayList<>();

	public static class Item{
		public String path = "/app/*";		
		public String proxyTo = "http://127.0.0.1";
		public String prefix = "/";
		
		public Item(String proxyTo, String path) {
			super();
			this.path = path;
			this.proxyTo = proxyTo;
		}

		@JsonCreator
		public Item(
				@JsonProperty("proxyTo") String proxyTo, 
				@JsonProperty("path") String path, 
				@JsonProperty("prefix") String prefix
				) {
			super();
			this.path = path;
			this.proxyTo = proxyTo;
			if(prefix == null) prefix = "/";
			this.prefix = prefix;
		}
	}

}