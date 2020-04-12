package com.courseracapstone.common.model;

public interface IUserNotice {

	public String getUserName();
	public long getTouchCount();
	public long getInappropriateCount();
	public long getObsceneCount();
	public long getRemovedCount();
	public void setUserName(String name);
	public void setTouchCount(long count);
	public void setInappropriateCount(long count);
	public void setObsceneCount(long count);
	public void setRemovedCount(long count);	
	public long getUnTouchCount();
	public void setUnTouchCount(long unTouchCount);
	public long getUnInappropriateCount();
	public void setUnInappropriateCount(long unInappropriateCount);
	public long getUnObsceneCount();
	public void setUnObsceneCount(long unObsceneCount);
}
