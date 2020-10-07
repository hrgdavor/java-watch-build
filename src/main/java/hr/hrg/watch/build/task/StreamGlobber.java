package hr.hrg.watch.build.task;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import hr.hrg.watch.build.Main;

class StreamGlobber implements Runnable{
//		StringBuffer b = new StringBuffer();
		private InputStream in;
		
		public StreamGlobber(InputStream in) {
			this.in = in;
		}
		
		@Override
		public void run() {
			try(	InputStreamReader inr = new InputStreamReader(in);
					BufferedReader br = new BufferedReader(inr)
					){
				String line = null;
				while((line = br.readLine()) != null){
					//b.append(line);
					if(line.startsWith("INFO "))
						hr.hrg.javawatcher.Main.logInfo(line.substring(5));
					else
						System.err.println(line);
				}
				in.close();
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
	}