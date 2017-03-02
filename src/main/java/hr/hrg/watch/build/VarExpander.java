package hr.hrg.watch.build;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VarExpander{
		public static final String PATTERN_VAR = "\\$\\{([a-zA-z_0-9]+)\\}";
		public static final String PATTERN_LANG = "\\[\\[([a-zA-z_0-9]+)\\]\\]";
		
		Pattern pattern;
		
		public VarExpander(){
			this(PATTERN_VAR);
		}
		public VarExpander(String pattern){
			this.pattern = Pattern.compile(pattern);
		}

		public String expand(String text, Map<String ,String> vars){
			Matcher matcher = pattern.matcher(text);
			//populate the replacements map ...
			StringBuilder builder = new StringBuilder();
			int i = 0;
			while (matcher.find()) {
			    String replacement = vars.get(matcher.group(1));
			    builder.append(text.substring(i, matcher.start()));
			    if (replacement == null)
			        builder.append(matcher.group(0));
			    else
			        builder.append(replacement);
			    i = matcher.end();
			}
			builder.append(text.substring(i, text.length()));
			return builder.toString();
		}
	}