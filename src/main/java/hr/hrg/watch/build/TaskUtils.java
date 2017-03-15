package hr.hrg.watch.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

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
	
}
