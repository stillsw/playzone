package io;

import java.io.*;
import java.net.URL;

public class KeyDetailsLoader {

	public static final String SANDBOX = "sandbox";
	public static final String NO_FILE = "";
	public static final String SANDBOX_FOLDER = "pp-sandbox";
	public static final String LIVE_FOLDER = "pp";
	public static final String FILE_SEPARATOR = System.getProperty("file.separator");
	private String folder;
	private String fullPathToFiles;
	private String privateKeyFileName;
	private String publicCertificateFileName;
	private String paypalCertificateFileName;
	private String privateKeyP12FileName;
	private File rootDir;

	public KeyDetailsLoader(File rootDir, boolean isSandbox) throws Exception {
		
		this.rootDir = rootDir;
		
		if (isSandbox)
			folder = SANDBOX_FOLDER;
		else
			folder = LIVE_FOLDER;

		readFiles();
	}

	private static String getTruncatedName(String fileName) {

		int dot = fileName.indexOf(".");
		if (dot == -1)
			return fileName;
		else
			return fileName.substring(0, dot);

	}

	private void readFiles() throws Exception {

        String[] fileNameList = null;
		try {
			if (rootDir != null) {
				File dataDir = new File(rootDir, folder);
				this.fullPathToFiles = dataDir.getPath() + FILE_SEPARATOR;
				fileNameList = dataDir.list();

				this.paypalCertificateFileName = NO_FILE;
				this.privateKeyFileName = NO_FILE;
				this.privateKeyP12FileName = NO_FILE;
				this.publicCertificateFileName = NO_FILE;

				for (int i = 0; i < fileNameList.length; i++)
					if (fileNameList[i].startsWith("paypal") && fileNameList[i].endsWith("cert.pem"))
						this.paypalCertificateFileName = fileNameList[i];
					else if (fileNameList[i].endsWith("prvkey.pem"))
						this.privateKeyFileName = fileNameList[i];
					else if (fileNameList[i].endsWith("pubcert.pem"))
						this.publicCertificateFileName = fileNameList[i];
					else if (fileNameList[i].endsWith("prvkey.p12"))
						this.privateKeyP12FileName = fileNameList[i];
			}
			else 
				throw new Exception("ERROR: no root folder defined");
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	public String getPrivateKeyFileName() {
		return privateKeyFileName;
	}

	public String getPrivateKeyP12FileName() {
		return privateKeyP12FileName;
	}

	public String getPublicCertificateFileName() {
		return publicCertificateFileName;
	}

	public String getPaypalCertificateFileName() {
		return paypalCertificateFileName;
	}

	public String getFullPathToFiles() {
		return fullPathToFiles;
	}

}
