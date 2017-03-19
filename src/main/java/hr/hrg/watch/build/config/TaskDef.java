package hr.hrg.watch.build.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TaskDef extends ConfDef{
	public List<TaskOption> options = new ArrayList<>();
	
	public TaskDef(Path confFile,int lineNumber, String ...params) {
		this.confFile = confFile;
		this.lineNumber = lineNumber;
		type = params[0].toLowerCase();
		this.params = params[1];
	}
}