package model;

import io.*;
import java.util.*;

public class ModelLoader {
	private static ModelLoader instance = new ModelLoader();
	private static String loadError = null;
	
	private boolean modelLoaded = false;
	
	private ArrayList<Category> categories;
	private ArrayList<Recipient> recipients;
	private EmailSettings emailSettings;
	
	private ModelLoader() {}
	
	public static ModelLoader getInstance() {
		return instance;
	}
	
	public boolean isModelLoaded() {
		return modelLoaded;
	}
	
	public void loadModel() {
		// load db
		if (modelLoaded) {
			//scrap existing set
		}
		try {
			DbLoader.getInstance().loadDb(this);
	 		modelLoaded = true;
		}
		catch (Exception e) {
			modelLoaded = false;
			loadError = e.getMessage();
		}
	}
	
	public void loadCategories(ArrayList<Category> categories) {
		this.categories = categories;
	}

	public void loadRecipients(ArrayList<Recipient> recipients) {
		this.recipients = recipients;
	}

	public void loadEmailSettings(EmailSettings emailSettings) {
		this.emailSettings = emailSettings;
	}

	public ArrayList<Category> getCategories() {
		return categories;
	}

	public ArrayList<Recipient> getRecipients() {
		return recipients;
	}

	public EmailSettings getEmailSettings() {
		return emailSettings;
	}

	public static String getLoadError() {
		return loadError;
	}
	
	
}

