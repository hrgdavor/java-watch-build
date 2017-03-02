package hr.hrg.watch.build;

import java.util.HashMap;

public class VarMap{
		HashMap<String, String> vars = new HashMap<>();
		VarExpander rep;

		public VarMap(){
			rep = new VarExpander();
		}
		
		public VarMap(String pattern){
			rep = new VarExpander(pattern);
		}
		public void put(String key, String value){
			vars.put(key, value);
		}
		public String get(String key){
			return vars.get(key);
		}
		public boolean containsKey(String key){
			return vars.containsKey(key);
		}
		public String expand(String text){
			return rep.expand(text, vars);
		}
		public HashMap<String, String> getVars() {
			return vars;
		}
	}