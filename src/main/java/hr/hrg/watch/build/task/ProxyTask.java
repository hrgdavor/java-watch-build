package hr.hrg.watch.build.task;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.ProxyConfig;
import hr.hrg.watch.build.config.ProxyConfig.Item;
import hr.hrg.watch.build.proxy.AddExpiresHeaderResponse;
import hr.hrg.watch.build.proxy.ProxyServletWithHeaders;

public class ProxyTask extends AbstractTask<ProxyConfig>{

	public ProxyTask(ProxyConfig config, WatchBuild core) {
		super(config, core);
	}

	public static final String REGISTRY_CONTEXT_NAME = "hr.apache.tapestry5.application-registry";
	
	@Override
	public void init(boolean watch) {
		if(!watch) return;
		
		hr.hrg.javawatcher.Main.logInfo("Starting ");
		long start = System.currentTimeMillis();
        
		ServletContextHandler  context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		SessionHandler sessionHandler = new SessionHandler();
		
		sessionHandler.getSessionManager().getSessionCookieConfig().setName("JSESSIONID_"+config.port);

		context.setSessionHandler(sessionHandler);
		context.setContextPath("/");
		context.setResourceBase(config.wwwRoot);

		ServletHolder holdDef;

		int i=0;
		for(Item item:config.items) {			
			holdDef = new ServletHolder("dev-proxy-"+i, ProxyServletWithHeaders.class);
			holdDef.setInitParameter("proxyTo", item.proxyTo);
			holdDef.setInitParameter("prefix", item.prefix);
			holdDef.setInitParameter("proxyHeaders", "true");
			context.addServlet(holdDef,item.path);
			i++;
		}

		// default servlet, should be last
		holdDef = new ServletHolder("default",DefaultServlet.class);
//		holdDef.setInitParameter("cacheControl", "max-age=31104000,public");
		holdDef.setInitParameter("precompressed", "gzip=.gz");
		holdDef.setInitParameter("maxCacheSize", "256000000");

		holdDef.setInitParameter("dirAllowed", "false");
		holdDef.setInitParameter("useFileMappedBuffer", "false");
		context.addServlet(holdDef,"/*");

		FilterHolder filterHolder = new FilterHolder(AddExpiresHeaderResponse.class);
//		filterHolder.setInitParameter(AddExpiresHeaderResponse.NO_CACHE_URLS, "/,/webRTC/");
		i=1;
		for(String url:config.noCache) {
			filterHolder.setInitParameter("noCacheUrl"+i, "/");
			i++;
		}
		context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
		
		Server server = new Server(config.port);
		ServerConnector connector = null;

	    HttpConfiguration httpConfig = new HttpConfiguration();
	    httpConfig.addCustomizer( new org.eclipse.jetty.server.ForwardedRequestCustomizer() );
	    
	    if(config.httpsKeyStore != null && !config.httpsKeyStore.isEmpty()) {
	    	httpConfig.setSecureScheme("https");
	    	httpConfig.setSecurePort(config.port);

	    	HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
	    	httpsConfig.addCustomizer(new SecureRequestCustomizer());
	    	
	    	final SslContextFactory sslContextFactory = new SslContextFactory(config.httpsKeyStore);
	    	sslContextFactory.setKeyStorePassword(config.httpsKeyStorePass);	    	
	    	
	    	SslConnectionFactory connectionFactory = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
	    	connector = new ServerConnector(server, connectionFactory, new HttpConnectionFactory( httpsConfig ));
	    }else {
	    	HttpConnectionFactory connectionFactory = new HttpConnectionFactory( httpConfig );
	    	connector = new ServerConnector(server, connectionFactory);
	    }
	    
	    connector.setPort( config.port );
	    server.setConnectors( new ServerConnector[] { connector } );		
		
		server.setHandler(context);

		try {
			server.start();			
		} catch (Exception e) {
			hr.hrg.javawatcher.Main.logError("problem satarting jetty on port "+config.port, e);
		}

		hr.hrg.javawatcher.Main.logInfo(" Started jetty on port "+config.port);

	}

	@Override
	public String toString() {
		return "Proxy:"+config.host+":"+config.port+" from "+config.wwwRoot;
	}
	
}
