package hr.hrg.watch.build;

import java.io.File;
import java.nio.file.Path;

import io.methvin.watcher.DirectoryChangeEvent.EventType;

public class FileDef {
	public EventType eventType;
	public Path path;
	public File file;
	public long lastEventTime;
	public long lastModified;
	public long length;

	public FileDef(Path path, EventType eventType) {
		this.path = path;
		this.eventType = eventType;
		this.file = path.toFile();
		lastEventTime = System.currentTimeMillis();
		lastModified = file.lastModified();
		length = file.length();
	}

	public void update(EventType eventType) {
		this.eventType = eventType;
		lastEventTime = System.currentTimeMillis();
		lastModified = file.lastModified();
		length = file.length();		
	}
	
	@Override
	public int hashCode() {
		return path.hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof FileDef) {
			return path.equals(((FileDef)obj).path);
		}
		return false;
	}
}
