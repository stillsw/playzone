package model;

import email.WebUtil;

public class EmailSettings {

	private String smtpServer;
	private String fromEmail;
	private String replyTo;
	private String sendTo;
	private int maxInBatch;
	private float delayBetweenEmails;
	private boolean authenticate;
	private String username;
	private String password;
	private boolean debug;
	
	public EmailSettings(String smtpServer, String fromEmail, String replyTo, String sendTo, 
			int maxInBatch, float delayBetweenEmails, boolean authenticate, String username) {
		this.smtpServer = smtpServer;
		this.fromEmail = fromEmail;
		this.replyTo = replyTo;
		this.sendTo = sendTo;
		this.maxInBatch = maxInBatch;
		this.delayBetweenEmails = delayBetweenEmails;
		this.authenticate = authenticate;
		this.username = username;
	}

	public float getDelayBetweenEmails() {
		return delayBetweenEmails;
	}

	public String getFromEmail() {
		return fromEmail;
	}

	public int getMaxInBatch() {
		return maxInBatch;
	}

	public String getReplyTo() {
		return replyTo;
	}

	public String getSendTo() {
		return sendTo;
	}

	public String getSmtpServer() {
		return smtpServer;
	}

	@Override
	public String toString() {

		return "smtpServer="+
				smtpServer+
				", sendTo="+
				sendTo+
				", fromEmail="+
				fromEmail+
				", replyTo="+
				replyTo+
				", maxInBatch="+
				maxInBatch+
				", delayBetweenEmails="+
				delayBetweenEmails +
				", authenticate="+
				authenticate +
				", username="+
				username
				;
		
	}

	public void setSmtpServer(String smtpServer) throws Exception {
		if (smtpServer == null || smtpServer.length() == 0 || smtpServer.indexOf(' ') >= 0) {
			throw new Exception("Smtp Server must be a valid Smtp address");
		}
		this.smtpServer = smtpServer;
	}

	public void setFromEmail(String fromEmail) throws Exception {
		if (fromEmail == null || fromEmail.length() == 0 || !WebUtil.isValidEmailString(fromEmail)) {
			throw new Exception("From Email must be a valid email address");
		}
		this.fromEmail = fromEmail;
	}

	public void setReplyTo(String replyTo) throws Exception {
		if (replyTo != null && replyTo.length() > 0 && !WebUtil.isValidEmailString(replyTo)) {
			throw new Exception("Reply To must be a valid email address if entered");
		}
		this.replyTo = replyTo;
	}

	public void setSendTo(String sendTo) throws Exception {
		if (sendTo == null || sendTo.length() == 0 || !WebUtil.isValidEmailString(sendTo)) {
			throw new Exception("Send To must be a valid email address");
		}
		this.sendTo = sendTo;
	}

	public void setMaxInBatch(String maxInBatch) throws Exception {
		if (maxInBatch == null || maxInBatch.length() == 0) {
			throw new Exception("Max Batch must be entered");
		}
		try {
			this.maxInBatch = Integer.parseInt(maxInBatch);
			if (this.maxInBatch < 0)
				throw new Exception("Max Batch cannot be a negative number");
		}
		catch (NumberFormatException e) {
			throw new Exception("Max Batch must be a valid integer");
		}
	}

	public void setDelayBetweenBatches(String delayBetweenBatches) throws Exception {
		if (delayBetweenBatches == null || delayBetweenBatches.length() == 0) {
			throw new Exception("Delay must be entered");
		}
		try {
			float delay = Float.parseFloat(delayBetweenBatches);
			if (delay < 0.0)
				throw new Exception("Delay cannot be a negative number");
			if (delay > 2.0)
				throw new Exception("Delay greater than 2 seconds is too long");
			this.delayBetweenEmails = delay;
		}
		catch (NumberFormatException e) {
			throw new Exception("Delay must be a valid number");
		}
	}

	public boolean isAuthenticate() {
		return authenticate;
	}

	public void setAuthenticate(boolean authenticate) {
		this.authenticate = authenticate;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) throws Exception {
		if (authenticate && (username == null || username.length() == 0)) {
			throw new Exception("Username must be entered for authentication");
		}

		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) throws Exception {
		if (authenticate && (password == null || password.length() == 0)) {
			throw new Exception("Password must be entered for authentication");
		}

		this.password = password;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	
}
