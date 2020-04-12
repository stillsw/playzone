package com.stillwindsoftware.keepyabeat.platform.twl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Borrowed basic idea from Vogella tutorial for html formatter
 * @author tomas stubbs
 */
public class LoggingFormatter extends Formatter {
	
	private boolean includeVerbose;
	
	/**
	 * Only output records at the specified level (to cater for logging to verbose output)
	 * @param maxLevel
	 */
	public LoggingFormatter(boolean includeVerbose) {
		this.includeVerbose = includeVerbose;
	}

	// This method is called for every log records
	public String format(LogRecord rec) {

		if ((rec.getLevel() == Level.FINEST || rec.getLevel() == Level.FINER)
				&& !includeVerbose) {
			// the logger will not publish this anyway (see TwlResourceManager.initLogging())
			return null;
		}

		StringBuffer buf = new StringBuffer(1000);
		buf.append(rec.getLevel());
	    buf.append(' ');
	    buf.append(calcDate(rec.getMillis()));
	    buf.append(' ');
	    buf.append(rec.getSequenceNumber());
	    buf.append(' ');
//	    buf.append(rec.getLoggerName());
//	    buf.append(' ');
	    buf.append(formatMessage(rec));
	    buf.append("\n");
	    return buf.toString();
	  }

	  private String calcDate(long millisecs) {
	    SimpleDateFormat date_format = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
	    Date resultdate = new Date(millisecs);
	    return date_format.format(resultdate);
	  }

	  // This method is called just after the handler using this
	  // formatter is created
	  public String getHead(Handler h) {
	    return "Begin Log: " + calcDate(System.currentTimeMillis()) + "\n";
	  }

	  // This method is called just after the handler using this
	  // formatter is closed
	  public String getTail(Handler h) {
	    return "End Log -------------------------------------------------------------------------------------------------------------------------\n";
	  }
	} 