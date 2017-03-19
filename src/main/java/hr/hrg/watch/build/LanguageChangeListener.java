package hr.hrg.watch.build;

import hr.hrg.watch.build.task.LangTask.Update;

public interface LanguageChangeListener {
	public void languageChanged(Update update);
}
