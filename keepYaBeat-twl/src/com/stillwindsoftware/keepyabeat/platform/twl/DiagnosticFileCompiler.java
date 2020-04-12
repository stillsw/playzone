package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation.Key;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.utils.StringUtils;

/**
 * Called from the report a bug page, compiles all the data into a jar file, including
 * library file, log files, stored preferences, system props and env
 * 
 * @author tomas stubbs
 */
public class DiagnosticFileCompiler {

	private final TwlResourceManager resourceManager;
	private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
	
	public DiagnosticFileCompiler(TwlResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	/**
	 * Generate a diagnostic report as a zipped up file and message to the user its name
	 */
	public static void generateBugReport(final TwlResourceManager resourceManager) {
		Thread genReportThread = new Thread() {
			@Override
			public void run() {
				new DiagnosticFileCompiler(resourceManager).makeZipFile();
			}			
		};
		genReportThread.start();
	}

	/**
	 * Creates a zip file in the home folder and adds the various data to it
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void makeZipFile() {
		
		ZipOutputStream zipOut = null;
		
		File home = new File(System.getProperty("user.home"));
		if (home.isDirectory() && home.canWrite()) {
			// create a new zip file
			try {
				String outFileName = String.format("kyb diagnostics %s.kbz", StringUtils.getFileNameTimestamp());
				File outFile = new File(home, outFileName);
				zipOut = new ZipOutputStream(new FileOutputStream(outFile));

				// add library file
				addFile(resourceManager.getLibraryFile(), true, zipOut);
				
				// add all files from kybHome, these are log files, so don't close them
				// TODO this isn't going to work for android
				for (File file : resourceManager.getKybHome().listFiles()) {
					if (file.isFile()) {
						addFile(file, false, zipOut);
					}
				}

				// add stored preferences
				StringBuffer prefsDump = resourceManager.getPersistentStatesManager().getDump();
				addDump(prefsDump, "prefs.txt", zipOut);

				// add system properties, putting into try in case of any security issues on other platforms
				try {
					addDump(dumpVars(new HashMap(System.getProperties())), "props.txt", zipOut);
				} catch (Exception e) {
					resourceManager.log(LOG_TYPE.error, this, 
							"DiagnosticFileCompiler.makeZipFile: unexpected error adding system properties");
					e.printStackTrace();
				}

				// add env
				try {
					addDump(dumpVars(System.getenv()), "env.txt", zipOut);
				} catch (Exception e) {
					resourceManager.log(LOG_TYPE.error, this, 
							"DiagnosticFileCompiler.makeZipFile: unexpected error adding env");
					e.printStackTrace();
				}

				// message to user
				resourceManager.getGuiManager().showNotification(String.format(
						resourceManager.getLocalisedString(Key.BUG_REPORT_GENERATED) 
							, outFileName
							, home.getAbsolutePath()
							, resourceManager.getLocalisedString(Key.APP_EMAIL)));
				
			} catch (Exception e) {
				resourceManager.log(LOG_TYPE.error, this, "DiagnosticFileCompiler.makeZipFile: unexpected error");
				e.printStackTrace();
				reportError(e.getMessage());
			}
			finally {
				if (zipOut != null) {
					try {
						zipOut.close();
					} catch (IOException e) {
						resourceManager.log(LOG_TYPE.error, this, "DiagnosticFileCompiler.makeZipFile: error closing zip");
						e.printStackTrace();
					}
				}
			}
		}
		else {
			resourceManager.log(LOG_TYPE.error, this, "DiagnosticFileCompiler.makeZipFile: no write access to user.home");
			reportError(resourceManager.getLocalisedString(Key.ERROR_NO_HOME_WRITE));
		}
	}

	/**
	 * Used to dump the env/system props to a stringbuffer
	 * @param m
	 * @return
	 */
	private StringBuffer dumpVars(Map<String, ?> m) {
		StringBuffer sb = new StringBuffer();
		
		List<String> keys = new ArrayList<String>(m.keySet());
		Collections.sort(keys);
		for (String k : keys) {
			sb.append(k); sb.append(" = "); sb.append(m.get(k)); sb.append("\n");
		}
		
		return sb;
	}
	
	/**
	 * Add the contents of the string buffer to the zip with the specified file name
	 * @param sb
	 * @param fileName
	 * @param zipOut
	 * @throws IOException
	 */
	private void addDump(StringBuffer sb, String fileName, ZipOutputStream zipOut) throws IOException {
		byte[] input = sb.toString().getBytes("UTF-8");
		zipOut.putNextEntry(new ZipEntry(fileName));
		writeInputStream(new ByteArrayInputStream(input), true, zipOut);
	}

	/**
	 * Standard format of an error reported to the user
	 * @param key
	 */
	private void reportError(String msg) {
		resourceManager.getGuiManager().warnOnErrorMessage(String.format(
				resourceManager.getLocalisedString(Key.GENERATE_BUG_REPORT_ERROR), msg)
			, resourceManager.getLocalisedString(Key.GENERATE_BUG_REPORT_ERROR_TITLE), false, null);
	}

	/**
	 * Adds the file to a new zip entry, naming it with its last modified date included
	 * @param fileToAdd
	 * @param zipOut
	 * @throws IOException
	 */
	private void addFile(File fileToAdd, boolean closeIn, ZipOutputStream zipOut) {
		try {
			// assumes library.kbl is there, it might not be if no manual changes have happened yet
			if (fileToAdd.exists()) {
				zipOut.putNextEntry(new ZipEntry(String.format("%s %s.%s"
						, StringUtils.getTruncatedName(fileToAdd.getName())
						, dateTimeFormat.format(fileToAdd.lastModified())
						, StringUtils.getExtension(fileToAdd.getName()))));
				
				writeInputStream(new FileInputStream(fileToAdd), closeIn, zipOut);
			}
		} catch (IOException e) {
			// windows throws errors on the .lck files (created by logger), don't notify user, it's too unimportant
			// have to assume all else is good though...
			resourceManager.log(LOG_TYPE.warning, this, "DiagnosticFileCompiler.addFile: failed "+fileToAdd.getName()+" reason="+e.getMessage());
		}
	}

	/**
	 * Write the contents of the input stream to the zip output stream
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	private void writeInputStream(InputStream in, boolean closeIn, ZipOutputStream out) throws IOException {
		// buffer size
        byte[] b = new byte[1024];
        int count;

        try {
			while ((count = in.read(b)) > 0) {
			    out.write(b, 0, count);
			}
		} 
        finally {
	        if (closeIn) {
	        	in.close();
	        }
		}
	}
}
