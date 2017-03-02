package hr.hrg.watch.build;


import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import hr.hrg.watch.build.BuildRunner;

public class TestWatcher {
	static ObjectMapper mapper   = new ObjectMapper();
	static YAMLMapper yamlMapper = new YAMLMapper();

	public static void main(String[] args) {
		runBuild("dev.yml","prod","en", true);
		runBuild("dev.yml","prod_lang,lang","fr", true);
	}

	public static void runBuild(String file, String profile, String language, boolean watch) {
		HashMap<String, String> vars = new HashMap<>();
		vars.put("lang", language);
		vars.put("profile", profile);

		BuildRunner buildRunner = new BuildRunner(yamlMapper, mapper);
		buildRunner.run(file, true, vars);
	}

}
