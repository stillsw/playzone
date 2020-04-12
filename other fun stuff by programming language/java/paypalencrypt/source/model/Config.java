package model;

import java.util.HashMap;
import io.*;

public class Config {

	public static final String OUTPUT_FILE = "outputFile";
	public static final String CONTAINS_FULL_PATH = "containsFullPath";
	public static final String BUTTON_IMAGE = "buttonImage";
	public static final String BUTTON_IMAGE_NAME = "name";
	public static final String BUTTON_IMAGE_DONATE = "donate";
	public static final String BUTTON_IMAGE_VIEWCART = "viewcart";
	private String outputFileName;
	private Business business;
	private KeyDetailsLoader keyDetailsLoader;
	private HashMap<String, String> buttonImages;
	private FtpUploader ftpUploader;
	
	public Config(HashMap<String, String> buttonImages, String outputFileName, Business business, KeyDetailsLoader keyDetailsLoader, FtpUploader ftpUploader) {
		this.buttonImages = buttonImages;
		this.outputFileName = outputFileName;
		this.business = business;
		this.keyDetailsLoader = keyDetailsLoader;
		this.ftpUploader = ftpUploader;
	}

	public Business getBusiness() {
		return business;
	}

	public KeyDetailsLoader getKeyDetailsLoader() {
		return keyDetailsLoader;
	}

	public String getOutputFileName() {
		return outputFileName;
	}

	public HashMap<String, String> getButtonImages() {
		return buttonImages;
	}

	public FtpUploader getFtpUploader() {
		return ftpUploader;
	}
	
}
