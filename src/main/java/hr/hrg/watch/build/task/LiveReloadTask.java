package hr.hrg.watch.build.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hr.hrg.javawatcher.GlobWatcher;
import hr.hrg.watch.build.Main;
import hr.hrg.watch.build.WatchBuild;
import hr.hrg.watch.build.config.LiveReloadConfig;

public class LiveReloadTask extends AbstractTask<LiveReloadConfig> implements Runnable{
	
	EventSocket current = null;
	

	private Server server;

	private GlobWatcher watcher;
	private long pauseUntil = 0;

	private File liveReloadScript;
	
	public LiveReloadTask(LiveReloadConfig config, WatchBuild core) {
		super(config, core);
	}

	@Override
	public void init(boolean watch) {
		if(watch) {
			System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.Slf4jLog");
			
			try {
//				org.eclipse.jetty.util.log.StdErrLog log2 = new org.eclipse.jetty.util.log.StdErrLog();
//				log2.setLevel(org.eclipse.jetty.util.log.StdErrLog.LEVEL_INFO);
				
				Log.setLog(new org.eclipse.jetty.util.log.Slf4jLog());
			} catch (Exception e) {
				hr.hrg.javawatcher.Main.logError(e.getMessage(),e);
			}
			
			if(config.liveReloadScript != null) {
				this.liveReloadScript = core.getBasePath().resolve(config.liveReloadScript).toFile();
				if(!this.liveReloadScript.exists()) throw new RuntimeException("LiveReload script not found "+liveReloadScript.getAbsolutePath());
			}
			
			server = new Server();
	        ServerConnector connector = new ServerConnector(server);
	        connector.setPort(config.port);
	        server.addConnector(connector);

	        // Setup the basic application "context" for this application at "/"
	        // This is also known as the handler tree (in jetty speak)
	        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
	        context.setContextPath("/");
	        server.setHandler(context);
	        
	        // Add a websocket to a specific path spec
	        ServletHolder holderEvents = new ServletHolder("ws-events", new EventServlet());
	        context.addServlet(holderEvents, "/*");

	        try{
	            server.start();
	        }catch (Throwable t){
	            t.printStackTrace(System.err);
	        }
	        
			File f = core.getBasePath().resolve(config.input).toFile();
			if(!f.exists()) throw new RuntimeException("Folder do not exist "+config.input+" "+f.getAbsolutePath());
			
			watcher = new GlobWatcher(f.getAbsoluteFile().toPath());
			
			watcher.includes(config.include);
			watcher.excludes(config.exclude);

			this.watcher.init(watch);	        
		}
	}

	@Override
	public boolean needsThread() {
		return true;
	}

	@Override
	public void run() {
		try {
			while(!Thread.interrupted()){
				Collection<Path> changes = watcher.takeBatchFilesUnique(core.getBurstDelay());
				if(changes == null) break; // interrupted

				long now = System.currentTimeMillis();
				for (Path path : changes){
					
					File file = path.toFile();
					if(file.isDirectory()) continue;
					
					if(current != null) {
						String absolutePath = file.getAbsolutePath();
						if(absolutePath.endsWith(".css")) {
							pauseUntil = now+config.pauseAfterCss;
							current.sendReload(absolutePath);
						}else if(now > pauseUntil){
							current.sendReload(absolutePath);
						}else {
							System.err.println("wating pause to end "+absolutePath); System.err.flush();
						}
					}
					
					if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("changed:"+path+" "+file.lastModified());
					
				}
			}
			
		} finally {
			watcher.close();
			try {
				server.stop();
			} catch (Exception e) {
				hr.hrg.javawatcher.Main.logError("problem stopping jetty",e);
			}
		}
		
	}

	@Override
	public String toString() {
		return "Livereload:"+config.port;
	}
		  

	
	
	public class EventServlet extends WebSocketServlet{

		public EventServlet() {
			
		}
		
		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			String path = req.getRequestURI();
			if("/livereload.js".equals(path)) {
				InputStream livereload = null;
				if(liveReloadScript != null) {
					livereload = new FileInputStream(liveReloadScript);
				}else {
					livereload = EventServlet.class.getResourceAsStream("livereload.js");					
				}
				byte[] buf = new byte[4096];
				int len = 0;
				ServletOutputStream out = resp.getOutputStream();
				while((len = livereload.read(buf)) != -1) {
					out.write(buf, 0, len);
				}
				livereload.close();
			}else
				super.doGet(req, resp);
		}
		
	    @Override
	    public void configure(WebSocketServletFactory factory)
	    {
	        factory.setCreator(new WebSocketCreator() {
				
				@Override
				public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
					return new EventSocket();
				}
			});
	    }
	    
	    @Override
	    public void init() throws ServletException {
	    	super.init();
	    }
	}	

	public class EventSocket extends WebSocketAdapter
	{
	    @Override
	    public void onWebSocketConnect(Session sess)
	    {
	        super.onWebSocketConnect(sess);
	        current = this;
	        hr.hrg.javawatcher.Main.logInfo("LiveReload Socket Connected: " + sess);
	    }
	    
	    @Override
	    public void onWebSocketText(String str)
	    {
	        super.onWebSocketText(str);
	        JsonNode node = core.getMapper().readTreeX(str);
	        
	        if(isHello(node)) {
	        	sendHello();
	        }
	    }


	    public void send(Object ...arr) {
		    LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
	    	for(int i=1; i<arr.length; i++) {
	    		map.put(arr[i-1].toString(), arr[i]);
	    	}
		    sendText(core.getMapper().writeValueAsStringNoEx(map));
	    }

	    private void sendText(String str) {
			try {
				if(hr.hrg.javawatcher.Main.isInfoEnabled()) hr.hrg.javawatcher.Main.logInfo("WS send: "+str);
				getRemote().sendString(str);
			} catch (IOException e) {
				hr.hrg.javawatcher.Main.logError(e.getMessage(),e);
			}
		}

		public void sendHello() {
		    LinkedList<String> protocols = new LinkedList<String>();
		    protocols.add("http://livereload.com/protocols/official-7");

		    send(	"command","hello",
		    		"protocols", protocols,
		    		"serverName", "livereload-jvm"
		    		);
		}
		
		public boolean isHello(JsonNode node) {
			if (node.hasNonNull("command") && "hello".equals(node.get("command").asText()))
				return true;

			return false;
		}
		
		public void sendAlert(String msg){
			send("command", "alert",  "message", msg);
		}
		  
		public void sendReload(String path){
			send(	"command", "reload",
					"path", path,
					"liveCSS", true);
		}
		
		@Override
	    public void onWebSocketClose(int statusCode, String reason)
	    {
	        super.onWebSocketClose(statusCode,reason);
	        current = null;
	        hr.hrg.javawatcher.Main.logInfo("Lievereload Socket Closed: [" + statusCode + "] " + reason);
	    }
	    
	    @Override
	    public void onWebSocketError(Throwable cause){
	        super.onWebSocketError(cause);
	        hr.hrg.javawatcher.Main.logError(cause.getMessage(),cause);
	    }
	}

}
