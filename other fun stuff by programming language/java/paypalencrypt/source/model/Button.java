package model;

public class Button {

	private String command;
	private String encryptedText;
	private String imageName;
	private String actionUrl;
	
	public Button(String command, String encryptedText, String imageName, String actionUrl) {
		super();
		this.command = command;
		this.encryptedText = encryptedText;
		this.imageName = imageName;
		this.actionUrl = actionUrl;
	}

	public String getActionUrl() {
		return actionUrl;
	}

	public String getCommand() {
		return command;
	}

	public String getEncryptedText() {
		return encryptedText;
	}

	public String getImageName() {
		return imageName;
	}
	
	
}
