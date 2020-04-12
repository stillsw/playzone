package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An OutputStream that writes contents to a Logger upon each call to flush()
 * Borrowed from this blog : https://blogs.oracle.com/nickstephen/entry/java_redirecting_system_out_and
 * 
 */
public class LoggingOutputStream extends ByteArrayOutputStream {
    
    private String lineSeparator;
    
    private Logger logger;
    private Level level;
    
    private OutputStream originalOutputStream;
    
    /**
     * Constructor
     * @param logger Logger to write to
     * @param level Level at which to write the log message
     */
    public LoggingOutputStream(Logger logger, Level level, OutputStream originalOutputStream) {

        this.logger = logger;
        this.level = level;
        lineSeparator = System.getProperty("line.separator");
        this.originalOutputStream = originalOutputStream;
    }
    
    /**
     * upon flush() write the existing contents of the OutputStream to the logger as 
     * a log record.
     * @throws java.io.IOException in case of error
     */
    public void flush() throws IOException {

        String record;
        synchronized(this) {
            super.flush();
            record = this.toString();
            super.reset();
        }

        // write out to stdout/err as well
        if (this.originalOutputStream != null) {
	        originalOutputStream.write(record.getBytes());
	    	originalOutputStream.flush();
        }
        
        if (record.length() == 0 || record.equals(lineSeparator)) {
            // avoid empty records
            return;
        }

        logger.logp(level, "", "", record);
        
    }
}