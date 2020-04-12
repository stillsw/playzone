package model;

import email.WebUtil;

public class Recipient {
	
	private String name;
	private String email;
	private boolean moniContact;
	private boolean tomasContact;
	private boolean active;
	private String category;
	private boolean sendTo;
	private boolean emailAddrIsError = false;
	
	public Recipient(String name, String email, boolean moniContact, boolean tomasContact, boolean active, String category) {
		this.name = name;
		this.email = email;
		this.moniContact = moniContact;
		this.tomasContact = tomasContact;
		this.active = active;
		this.category = category;
		this.emailAddrIsError = !WebUtil.isValidEmailAddress(email);
	}
	
	public boolean isSendTo() {
		return sendTo;
	}

	public void setSendTo(boolean sendTo) {
		this.sendTo = sendTo;
	}

	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public String getCategory() {
		return (category == null ? "" : category);
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public boolean isMoniContact() {
		return moniContact;
	}
	public void setMoniContact(boolean moniContact) {
		this.moniContact = moniContact;
	}
	public String getName() {
		return (name == null || name.length() == 0 ? email : name);
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isOnlyMoniContact() {
		return this.isMoniContact() && !this.isTomasContact();
	}
	public boolean isOnlyTomasContact() {
		return !this.isMoniContact() && this.isTomasContact();
	}
	public boolean isTomasContact() {
		return tomasContact;
	}
	public void setTomasContact(boolean tomasContact) {
		this.tomasContact = tomasContact;
	}

	@Override
	public String toString() {
		return this.getName();
	}

	public boolean emailAddressIsError() {
		return emailAddrIsError;
	}

	
}