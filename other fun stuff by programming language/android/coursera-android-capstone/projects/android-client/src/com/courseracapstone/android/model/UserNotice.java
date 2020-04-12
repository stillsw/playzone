package com.courseracapstone.android.model;

import com.courseracapstone.common.model.IUserNotice;

/**
 * Android side version, class is identical to server-side version except javax.persistence 
 * packages and JPA annotations removed. 
 * If structure changes, simply copy over the existing class from the server-side and edit out those
 * bits.
 * 
 * @author xxx xxx
 *
 */
public class UserNotice implements IUserNotice {

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

	public boolean isEmpty() {
		return touchCount == 0 
				&& unTouchCount == 0
				&& inappropriateCount == 0 
				&& unInappropriateCount == 0
				&& obsceneCount == 0 
				&& unObsceneCount == 0
				&& removedCount == 0;
	}

}
