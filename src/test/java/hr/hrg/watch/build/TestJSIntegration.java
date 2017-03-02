package hr.hrg.watch.build;


import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;


import hr.hrg.watch.build.BuildRunner;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.ScriptUtils;
import jdk.nashorn.internal.runtime.Undefined;

public class TestJSIntegration {
	static ObjectMapper mapper = new ObjectMapper();
	static YAMLMapper yamlMapper = new YAMLMapper();


	public static void main(String[] args) {
		
//	runBuild("dev.yml","prod","en", true);
//	runBuild("dev.yml","prod_lang,lang","fr", true);
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		String script = "var TestJSIntegration = Java.type('hr.hrg.watch.build.TestJSIntegration');" 
				+"function alert(x){print(x); print(this)};\n"
				+"function alert(x){print(x); print(this)};\n"
				+"var ret = TestJSIntegration.testJson({x:'aaaa'});\n"
				+"var ret2 = JSON.parse(ret);\n"
//				+"print(window)\n"
//				+"TestJSIntegration.runBuild(\"dev.yml\",\"prod\",\"en\", true)\n"
//				+"TestJSIntegration.runBuild(\"dev.yml\",\"prod_lang,lang\",\"fr\", true)\n"
				+"\n"
				+"\n"
				+"\n"
				;
		try {
			engine.eval(script);
		} catch (ScriptException e) {
			e.printStackTrace();
		}		
	}
	
	public static JsonNode testJson(ScriptObjectMirror  param){
		JsonNode node = fromMirror(param);
		System.out.println(param.containsKey("x"));
		System.out.println(toJsonString(node));
		return node;
	}

	public static String toJsonString(Object value){
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static JsonNode fromMirror(ScriptObjectMirror param) {
		if(param == null){
			return NullNode.getInstance();
		}else if(ScriptObjectMirror.isUndefined(param)){
			return MissingNode.getInstance();
		}else if(param.isFunction()){
			return MissingNode.getInstance();
		}else if(param.isArray()){
            Collection<Object> values = param.values();
            ArrayNode node = mapper.createArrayNode();
            for (Object o: values){
                JsonNode e = toJson(o);
                if (e.isMissingNode()){
                    continue;
                }
                node.add(e);
            }
            return node;			
		}else {
            ObjectNode obj = mapper.createObjectNode();
            Set<Map.Entry<String, Object>> entries = param.entrySet();
            for (Map.Entry<String,Object> e:entries){
                Object obv = e.getValue();
                JsonNode jsonNode = toJson(obv);
                if (jsonNode.isMissingNode()){
                    continue;
                }
                obj.put(e.getKey(),jsonNode);
            }
            return obj;
        }
	}

    private static JsonNode toJson(Object obj){
        if (obj == null){
            return NullNode.getInstance();
        
        } else if (obj instanceof Boolean){
            return BooleanNode.valueOf((Boolean) obj);
        
        } else if (obj instanceof Undefined){
            return MissingNode.getInstance();
        
        } else if (obj instanceof Number){
            return fromNumber((Number)obj);
        
        } else if (obj instanceof String){
            return TextNode.valueOf((String)obj);
        
        } else if (obj instanceof ScriptObjectMirror){
            return fromMirror((ScriptObjectMirror)obj);

        } else {
            return MissingNode.getInstance();
        }
    }	

    private static JsonNode fromNumber(Number number){
        if (number instanceof Double||number instanceof Float){
            return DoubleNode.valueOf(number.doubleValue());
        } else {
            return LongNode.valueOf(number.longValue());
        }
    }


	public static void runBuild(String file, String profile, String language, boolean watch) {
		HashMap<String, String> vars = new HashMap<>();
		vars.put("lang", language);
		vars.put("profile", profile);

		BuildRunner buildRunner = new BuildRunner(yamlMapper, mapper);
		buildRunner.run(file, true, vars);
	}    

}
