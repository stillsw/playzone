package io;

import java.io.*;
import java.util.Date;
import java.text.*;

public class Logger {

	private static Logger instance;
	private FileWriter logFile;
	
	public static Logger getInstance() {
		try {
			if (instance == null)
				instance = new Logger();
			return instance;
		} catch (IOException e) {
			// this would be unexpected, not much can do, let it return null and everything can fall apart :)
			e.printStackTrace();
			return null;
		}
	}
	
	private Logger()
		throws IOException
	{
		super();
		String fileSeparator = System.getProperty("file.separator");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd' at 'HH-mm-ss");
		String runDir = System.getProperty("user.dir");
		String logDirectory = runDir + fileSeparator + "bulkEmailer" + fileSeparator + "logs";
		File directory = new File(logDirectory);
		directory.mkdirs();
		String fullFileName = logDirectory + fileSeparator + format.format(new Date()) + ".txt";
		logFile = new FileWriter(fullFileName);
		append("This log file written to " +fullFileName);
	}

	public synchronized void append(String text) throws IOException {
		logFile.write(text);
		logFile.flush();
	}

	public synchronized void append(Exception e) throws IOException {

		ByteArrayOutputStream errorText = new ByteArrayOutputStream();
		
		PrintStream s = new PrintStream(errorText);
		e.printStackTrace(s);
		
		logFile.write(errorText.toString());
		logFile.flush();
	}

	@Override
	protected void finalize() throws Throwable {
		// TODO Auto-generated method stub
		super.finalize();
		
		if (logFile != null)
			logFile.close();
	}
	
	
	
}
