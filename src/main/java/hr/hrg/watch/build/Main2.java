package hr.hrg.watch.build;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.methvin.watcher.DirectoryWatcher;
import io.methvin.watcher.hashing.FileHasher;

public class Main2 {

	public static void main(String[] args) {
		Path directoryToWatch = Paths.get("./target/testCopy").toAbsolutePath().normalize();
        try {
			DirectoryWatcher watcher = DirectoryWatcher.builder()
			        .path(directoryToWatch) // or use paths(directoriesToWatch)
			        .listener(event -> {
			            switch (event.eventType()) {
			                case CREATE: /* file created */;
			                System.out.println("Create: "+event.path());
			                System.out.println("      : "+directoryToWatch.relativize(event.path()));
			                break;
			                case MODIFY: /* file modified */; 
			                	System.out.println("Modify: "+event.path());
			                	System.out.println("      : "+directoryToWatch.relativize(event.path()));
			                break;
			                case DELETE: /* file deleted */; 
			                	System.out.println("Delete: "+event.path());
			                	System.out.println("      : "+directoryToWatch.relativize(event.path()));
			                break;
			            }
			        })
//			         .fileHashing(false) // defaults to true
			         .fileHasher(FileHasher.LAST_MODIFIED_TIME)
			        // .logger(logger) // defaults to LoggerFactory.getLogger(DirectoryWatcher.class)
			        // .watchService(watchService) // defaults based on OS to either JVM WatchService or the JNA macOS WatchService
			        .build();

			watcher.watch();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
