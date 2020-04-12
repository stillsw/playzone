package com.courseracapstone.serverside.repository;

import javax.persistence.Entity;
import javax.persistence.Id;

import com.courseracapstone.common.model.IUserNotice;

/**
 * A simple object to represent a notice of various counts updated to the user
 * 
 * @author xxx xxx
 *
 */
@Entity
public class UserNotice implements IUserNotice {

	@Id
	private String userName;
	
	private long touchCount;
	private long inappropriateCount;
	private long obsceneCount;
	private long unTouchCount;
	private long unInappropriateCount;
	private long unObsceneCount;
	private long removedCount;

	public UserNotice() {
	}

	public UserNotice(String userName) {
		this.userName = userName;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public long getTouchCount() {
		return touchCount;
	}

	public void setTouchCount(long touchCount) {
		this.touchCount = touchCount;
	}

	public long getInappropriateCount() {
		return inappropriateCount;
	}

	public void setInappropriateCount(long inappropriateCount) {
		this.inappropriateCount = inappropriateCount;
	}

	public long getObsceneCount() {
		return obsceneCount;
	}

	public void setObsceneCount(long obsceneCount) {
		this.obsceneCount = obsceneCount;
	}

	public long getRemovedCount() {
		return removedCount;
	}

	public void setRemovedCount(long removedCount) {
		this.removedCount = removedCount;
	}

	public long getUnTouchCount() {
		return unTouchCount;
	}

	public void setUnTouchCount(long unTouchCount) {
		this.unTouchCount = unTouchCount;
	}

	public long getUnInappropriateCount() {
		return unInappropriateCount;
	}

	public void setUnInappropriateCount(long unInappropriateCount) {
		this.unInappropriateCount = unInappropriateCount;
	}

	public long getUnObsceneCount() {
		return unObsceneCount;
	}

	public void setUnObsceneCount(long unObsceneCount) {
		this.unObsceneCount = unObsceneCount;
	}

	@Override
	public String toString() {
		return "UserNotice [userName=" + userName + ", touchCount="
				+ touchCount + ", inappropriateCount=" + inappropriateCount
				+ ", obsceneCount=" + obsceneCount + ", unTouchCount="
				+ unTouchCount + ", unInappropriateCount="
				+ unInappropriateCount + ", unObsceneCount=" + unObsceneCount
				+ ", removedCount=" + removedCount + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
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
		UserNotice other = (UserNotice) obj;
		if (userName == null) {
			if (other.userName != null)
				return false;
		} else if (!userName.equals(other.userName))
			return false;
		return true;
	}

}
