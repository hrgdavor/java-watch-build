package hr.hrg.watch.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
	
	public static final boolean writeFile(Path to, byte[] newBytes, boolean compareBytes, long newLastModified){
		return writeFile(to, newBytes, compareBytes, newLastModified, true);
	}
	
	public static final boolean writeFile(Path to, byte[] newBytes, boolean compareBytes, long newLastModified, boolean checkTs){

		File toFile = to.toFile();

		if(toFile.exists()){
			if(toFile.lastModified() >= newLastModified && toFile.length() == newBytes.length) return false;

			if(compareBytes){
				if(TaskUtils.compareBytes(toFile, newBytes)){
					return false;
				}
			}
		}
		
		writeFile(to, newBytes);
		if(newLastModified != 0) toFile.setLastModified(newLastModified);
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

	public static boolean emptyOrcomment(String trimmed) {
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

	@SuppressWarnings("unchecked")
	public static <T extends JsonNode> T copy(ObjectMapper mapper, T node) {
		try {
			return (T) mapper.readTree(node.traverse());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@SafeVarargs
	public static <T> void addAll(List<T> list, T ...arr){
		for(T item:arr) list.add(item);
	}
	
	@SafeVarargs
	public static<T> List<T> toList(T ...arr){
		List<T> list = new ArrayList<T>();
		for(T item:arr) list.add(item);
		return list;
	}
	
	public static ObjectNode toObjectNode(ObjectNode ret, JsonMapper mapper, Object ...arr){
		if(ret == null) ret = mapper.createObjectNode();
		for(int i=1; i<arr.length; i+=2) {
			ret.putPOJO(arr[i-1].toString(), arr[i]);
		}
		return ret;
	}

	public static <T> List<T> map(List<T> list, Function<T, T> func){
		List<T> ret = new ArrayList<>();
		
		for(T tmp:list) {
			ret.add(func.apply(tmp));
		}
		return ret;
	}
	
	public static String[] map(String[] list, Function<String, String> func){
		String[] ret = new String[list.length];
		
		for(int i=0; i<list.length; i++) {
			ret[i] = func.apply(list[i]);
		}
		return ret;
	}
	
}
