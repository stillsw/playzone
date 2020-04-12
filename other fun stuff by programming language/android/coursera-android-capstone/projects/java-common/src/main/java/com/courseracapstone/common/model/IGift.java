package com.courseracapstone.common.model;

public interface IGift {

	public long getId();
	public String getTitle();
	public void setTitle(String title);
	public String getExtraText();
	public void setExtraText(String extraText);
	public long getGiftChainId();
	public void setGiftChainId(long giftChainId);
	public boolean getChainTop();
	public void setChainTop(boolean chainTop);
	public long getForceOrderForChainTop();
	public void setForceOrderForChainTop(long forceOrderForChainTop);
	public long getChainCount();
	public void setChainCount(long chainCount);
	public String getUserName();
	public void setUserName(String userName);
	public long getTouchCount();
	public void setTouchCount(long touchCount);
	public long getInappropriateCount();
	public void setInappropriateCount(long inappropriateCount);
	public long getObsceneCount();
	public void setObsceneCount(long obsceneCount);
	public boolean getUserTouchedBy();
	public void setUserTouchedBy(boolean userTouchedBy);
	public boolean getUserInappropriateFlag();
	public void setUserInappropriateFlag(boolean userInappropriateFlag);
	public boolean getUserObsceneFlag();
	public void setUserObsceneFlag(boolean userObsceneFlag);
	public String getImageFormat();
	public void setImageFormat(String format);
	public String getImagePath();
	public void setImagePath(String imagePath);
	public String getThumbnailPath();
	public void setThumbnailPath(String thumbnailPath);
	public long getUpdatedTimeMillis();
	public void setUpdatedTimeMillis(long updatedTimeMillis);
	public long getDownloadTimeMillis();
	public void setDownloadTimeMillis(long downloadTimeMillis);
}
