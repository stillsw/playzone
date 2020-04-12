package com.courseracapstone.serverside.repository;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * Entity that links a gift to a user and a response (touch, inappropriate, obscene)
 * 
 * @author xxx
 */
@Entity
public class UserGiftResponse {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;
	private String userName;
	private long giftId;
	private long responseType;

	public UserGiftResponse() {
	}

	public UserGiftResponse(String userName, long giftId,
			long responseType) {
		super();
		this.userName = userName;
		this.giftId = giftId;
		this.responseType = responseType;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getUserName() {
		return userName;
	}

	public long getGiftId() {
		return giftId;
	}

	public long getResponseType() {
		return responseType;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (giftId ^ (giftId >>> 32));
		result = prime * result + (int) (responseType ^ (responseType >>> 32));
		result = prime * result
				+ ((userName == null) ? 0 : userName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UserGiftResponse other = (UserGiftResponse) obj;
		if (giftId != other.giftId)
			return false;
		if (responseType != other.responseType)
			return false;
		if (userName == null) {
			if (other.userName != null)
				return false;
		} else if (!userName.equals(other.userName))
			return false;
		return true;
	}


}
