package hr.hrg.watch.build.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TaskOption extends ConfDef{
	public List<String> lines = new ArrayList<>();

	public TaskOption(Path confFile, int lineNumber, String ...params) {
		this.confFile = confFile;
		this.lineNumber = lineNumber;
		if(params.length >0) type = params[0].toLowerCase();
		if(params.length >1) this.params = params[1];
	}
}