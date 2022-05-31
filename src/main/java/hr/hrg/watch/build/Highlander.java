package hr.hrg.watch.build;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Highlander {

	// how fast can can we accept new connection to cause the least delay for startup
	protected static final int CONNECT_TIMEOUT = 5;
	// give the other instance time to close port 
	protected static final long BIND_DELAY = 500;

	public static void main(String[] args) throws Exception{
		// Do this in your code before you start using resources that are unique (like binding TCP ports)
		// you must add a system property and should put it only in your run parameters in dev env
		// -DthereCanBeOnlyOne=1661
		// -DthereCanBeOnlyOne=1661,myPhrase
		long start = System.currentTimeMillis();
		thereCanBeOnlyOne();
		System.out.println("Time to check: "+(System.currentTimeMillis()-start));
		synchronized (args) {
			args.wait();			
		}
	}

	public static void thereCanBeOnlyOne(){
		
		String spec = System.getProperty("thereCanBeOnlyOne");
		if(spec == null || "".equals(spec)) return;
		String phrase = "thereCanBeOnlyOne";
		int port;
		int idx = spec.indexOf(",");
		
		if(idx !=-1) {
			port = Integer.valueOf(spec.substring(0,idx));
			phrase = spec.substring(idx+1);
		}else{
			port = Integer.valueOf(spec);			
		}
		
		checkThereIsOnlyOne(port,phrase);
	}

	public static void checkThereIsOnlyOne(final int port, final String phrase) {
		try {
			try {
				Socket socket = new Socket();
				socket.setTcpNoDelay(true);
//				socket.setSoTimeout(10000);
				socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT);

				OutputStream out = socket.getOutputStream();
				writeLine(out,phrase);
				InputStream in = socket.getInputStream();
				String line = readLine(in);// wait if other side wants to finish first
				socket.close();				
				// sleep a bit, just in case some resources take longer to release
				// not bulletproof, but helps :)
				Thread.sleep(100);
			} catch (Exception e) {
				System.out.println("You are the only one (port:"+port+", phrase:"+phrase+")"+e.getMessage());
			}
			
			new Thread(new Runnable() {
				public void run() {
					try {
						Thread.sleep(BIND_DELAY);						
						ServerSocket ss = new ServerSocket(port);
						while (!Thread.interrupted()){
							Socket tmp = ss.accept();
							System.out.println("There is someone here");
							String line = readLine(tmp.getInputStream());
							
							if(line.equals(phrase)) {
								System.out.println("Ending this instance. There can be only one !");
								// do something here before sending response if bit more graceful stop is needed
								writeLine(tmp.getOutputStream(), phrase);
							}else{
								System.out.println("Expected phrase "+phrase+" but received "+line);
								break;
							}
							System.exit(0);
							tmp.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static String readLine(InputStream in) throws Exception{
		return new BufferedReader(new InputStreamReader(in)).readLine();
	}
	
	private static void writeLine(OutputStream out, String line) throws Exception{
		out.write(line.getBytes());
		out.write("\n".getBytes());
		out.flush();
	}
	
}
