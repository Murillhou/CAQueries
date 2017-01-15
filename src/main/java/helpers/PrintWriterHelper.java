package main.java.helpers;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

public class PrintWriterHelper {
	
	Collection<String> writtenLines = null;
	PrintWriter pw;
	
	public PrintWriterHelper(String filePath, String coding) throws FileNotFoundException, UnsupportedEncodingException {
		this.writtenLines = new HashSet<String>();
		this.pw = new PrintWriter(filePath, coding);
	}
	
	public boolean println(String line) {
		boolean res = false;
		if (!writtenLines.contains(line)){
			this.writtenLines.add(line);
			this.pw.println(line);
			res = true;
		}
		return res;
	}

	public void close() {
		this.pw.close();
		this.writtenLines = null;
	}
}
