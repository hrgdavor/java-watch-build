package hr.hrg.watch.build;

import java.nio.file.Path;

public class OutputEntry {
	boolean simple;
	private Path output;
	private Path input;
	private String descr;
	private FileMatchGlob2 task;
	
	public OutputEntry(FileMatchGlob2 task, Path output, String descr){
		this.task = task;
		this.output = output;
		this.descr = descr;
	}
	
	public OutputEntry simple(Path in){
		simple = true;
		this.input = in;
		return this;
	}
	
	public FileMatchGlob2 getTask() {
		return task;
	}
	
	public Path getInput() {
		return input;
	}
	
	public Path getOutput() {
		return output;
	}
	
	public boolean isSimple() {
		return simple;
	}

	public String getDescr() {
		return descr;
	}
}

