package hr.hrg.watch.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonMapper extends ObjectMapper{

	private static final long serialVersionUID = -5976433206356616288L;

	public JsonNode readTreeX(String content){
		try {
			return super.readTree(content);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}
	
	public JsonNode readTreeX(Reader content){
		try {
			return super.readTree(content);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	public JsonNode readTreeX(InputStream content){
		try {
			return super.readTree(content);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(),e);
		}
	}

	/**
	 * same as {@link #writeValueAsString(Object)} but without throwing error
	 * */
	public String writeValueAsStringNoEx(Object data){
		try {
			return writeValueAsString(data);
		} catch (JsonProcessingException e) {
			hr.hrg.javawatcher.Main.logError(e.getMessage(),e);
			return "";
		}
	}

}
