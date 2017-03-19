package hr.hrg.watch.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class TaskUtils {
	public static final int BUFFER_SIZE = 4096;

	public static final void closeStream(InputStream in) {
		if (in != null)
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	public static final  void closeStream(OutputStream out) {
		if (out != null)
			try {
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	public static final boolean compareBytes(File toFile, byte[] newBytes){
		if(toFile.length() == newBytes.length){
			boolean same = true;
			byte[] buff = new byte[BUFFER_SIZE];
			int len=0, i=0;
			InputStream in = null;
			try {
				in = new FileInputStream(toFile);
				while((len = in.read(buff)) != -1){
					for(int j=0; j<len; j++){
						if(buff[j] != newBytes[i]){
							same = false;
							break;
						}
						i++;// still same, continue checking
					}
					if(!same) break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}finally{
				TaskUtils.closeStream(in);
			}
			return same;
		}
		return false;
	}
	
	public static final boolean writeFile(Path to, byte[] newBytes, boolean compareBytes){

		File toFile = to.toFile();

		if(compareBytes && toFile.exists()){
			if(TaskUtils.compareBytes(toFile, newBytes)){
				return false;
			}
		}
		
		writeFile(to, newBytes);
		
		return true;
	}

	public static final void writeFile(Path to, byte[] newBytes){
		File toFileFolder = to.toFile().getAbsoluteFile().getParentFile();
		if(!toFileFolder.exists()) toFileFolder.mkdirs();

		try {
			Files.write(to, newBytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Resolve current input. If the passed file is input use that, else use parent input of the file.*/
	public static File getFolder(File file) {
		if(file.isDirectory()) return file;
		return file.getParentFile();
	}

	public static Path getFolder(Path file) {
		if(file.toFile().isDirectory()) return file;
		return file.getParent();
	}

	public static boolean isNoOutput(String dest) {
		return dest == null || "".equals(dest) || dest.startsWith("/dev/null");
	}

	protected static boolean emptyOrcomment(String trimmed) {
		return trimmed.isEmpty() || trimmed.charAt(0) == '#';
	}

	protected static String[] parseDefLine(String trimmed) {
		String[] ret = new String[] {trimmed,""};
		int idx = trimmed.indexOf(' ');
		if(idx != -1) {
			ret[1] = ret[0].substring(idx+1).trim();
			ret[0] = ret[0].substring(0,idx);
		}
		return ret;
	}

	public static final JsonNode expandVars(JsonNode node, VarMap vars) {
		
		if(node.isArray()){
			int count = node.size();
			ArrayNode arr = (ArrayNode) node;
			for(int i=0; i<count; i++){
				if(node.get(i).isTextual()){
					arr.set(i, new TextNode(vars.expand(arr.get(i).asText())));
				}else	
					expandVars(node.get(i), vars);
			}
		}else if(node.isObject()){
			ObjectNode obj = (ObjectNode) node;
			Iterator<Entry<String, JsonNode>> fields = obj.fields();
			
			while(fields.hasNext()){
				Entry<String, JsonNode> next = fields.next();
				if(next.getValue().isTextual()){
					next.setValue(new TextNode(vars.expand(next.getValue().asText())));
				}else	
					expandVars(next.getValue(), vars);
			}
		}
		return node;
	}

	public static <T extends JsonNode> T copy(ObjectMapper mapper, T node) {
		try {
			return (T) mapper.readTree(node.traverse());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static <T> T checkOption(List<Object> config, int i, Class<T> class1) {
		Object object = config.get(i);
		try {
			if(object == null) return null;
			
			return (T)object;
		}catch (ClassCastException e) {
			throw new OptionException(i,"Invalid parsed option type. Expected "+class1.getName()+" but type is"+object.getClass().getName());
		}
		
	}	
}
