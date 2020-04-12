package com.courseracapstone.serverside.repository;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import com.courseracapstone.common.model.IGift;

/**
 * A simple object to represent a gift and its image urls (full & thumbnail)
 * 
 * @author xxx xxx
 *
 * Data:
 * id (pk) : auto generated
 * Name : String
 * Image : url path
 * Thumbnail : url path
 * Additional Text (optional) : string
 * chain Id : (fk to a top-level gift – ie. One with no chainId)
 * created By UserId : some actions not allowed by user, eg. touch own gift
 */
@Entity
public class Gift implements IGift, Comparable<Gift> {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	private String title;
	private String extraText;
	private long giftChainId;
	private boolean chainTop; 
	private long forceOrderForChainTop; // ordering by the boolean same results asc/desc so
										// this is used purely to ensure correct order
	private long chainCount;
	private String userName;
	private long touchCount;
	private long inappropriateCount;
	private long obsceneCount;
	private long updatedTimeMillis;
	private String imagePath;
	private String imageFormat;
	private String thumbnailPath;
	// dependent on the user
	private boolean userTouchedBy;
	private boolean userInappropriateFlag;
	private boolean userObsceneFlag;
	// protect the gift from update when given a stale copy for update
	// see controller
	private long downloadTimeMillis;

	public Gift() {
		giftChainId = -1L;
		forceOrderForChainTop = 0;
		updatedTimeMillis = System.currentTimeMillis();
	}

	public Gift(String title, String extraText, long giftChainId, String userName) {
		this();
		this.title = title;
		this.extraText = extraText;
		this.giftChainId = giftChainId;
		this.userName = userName;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
	}

	@Override
	public String getExtraText() {
		return extraText;
	}

	@Override
	public void setExtraText(String extraText) {
		this.extraText = extraText;
	}

	@Override
	public long getGiftChainId() {
		return giftChainId;
	}

	@Override
	public void setGiftChainId(long giftChainId) {
		this.giftChainId = giftChainId;
	}

	@Override
	public boolean getChainTop() {
		return chainTop;
	}

	@Override
	public void setChainTop(boolean chainTop) {
		this.chainTop = chainTop;
	}

	@Override
	public long getForceOrderForChainTop() {
		return forceOrderForChainTop;
	}

	@Override
	public void setForceOrderForChainTop(long forceOrderForChainTop) {
		this.forceOrderForChainTop = forceOrderForChainTop;
	}

	@Override
	public long getChainCount() {
		return chainCount;
	}

	@Override
	public void setChainCount(long chainCount) {
		this.chainCount = chainCount;
	}

	@Override
	public String getUserName() {
		return userName;
	}

	@Override
	public void setUserName(String userName) {
		this.userName = userName;
	}

	@Override
	public long getTouchCount() {
		return touchCount;
	}

	@Override
	public void setTouchCount(long touchCount) {
		this.touchCount = touchCount;
	}

	@Override
	public long getInappropriateCount() {
		return inappropriateCount;
	}

	@Override
	public void setInappropriateCount(long inappropriateCount) {
		this.inappropriateCount = inappropriateCount;
	}

	@Override
	public long getObsceneCount() {
		return obsceneCount;
	}

	@Override
	public void setObsceneCount(long obsceneCount) {
		this.obsceneCount = obsceneCount;
	}

	@Override
	public boolean getUserTouchedBy() {
		return userTouchedBy;
	}

	@Override
	public void setUserTouchedBy(boolean userTouchedBy) {
		this.userTouchedBy = userTouchedBy;
	}

	@Override
	public boolean getUserInappropriateFlag() {
		return userInappropriateFlag;
	}

	@Override
	public void setUserInappropriateFlag(boolean userInappropriateFlag) {
		this.userInappropriateFlag = userInappropriateFlag;
	}

	@Override
	public boolean getUserObsceneFlag() {
		return userObsceneFlag;
	}

	@Override
	public void setUserObsceneFlag(boolean userObsceneFlag) {
		this.userObsceneFlag = userObsceneFlag;
	}

	@Override
	public long getId() {
		return id;
	}

	public long getUpdatedTimeMillis() {
		return updatedTimeMillis;
	}

	public void setUpdatedTimeMillis(long updatedTimeMillis) {
		this.updatedTimeMillis = updatedTimeMillis;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public String getThumbnailPath() {
		return thumbnailPath;
	}

	public void setThumbnailPath(String thumbnailPath) {
		this.thumbnailPath = thumbnailPath;
	}

	public String getImageFormat() {
		return imageFormat;
	}

	public void setImageFormat(String imageFormat) {
		this.imageFormat = imageFormat;
	}

	public long getDownloadTimeMillis() {
		return downloadTimeMillis;
	}

	public void setDownloadTimeMillis(long downloadTimeMillis) {
		this.downloadTimeMillis = downloadTimeMillis;
	}


	@Override
	public String toString() {
		return "Gift [id=" + id + ", title=" + title + ", giftChainId="
				+ giftChainId + ", chainTop=" + chainTop + ", userName="
				+ userName + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((title == null) ? 0 : title.hashCode());
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
		Gift other = (Gift) obj;
		if (id != other.id)
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		if (userName == null) {
			if (other.userName != null)
				return false;
		} else if (!userName.equals(other.userName))
			return false;
		return true;
	}

	// return gifts in reverse create order, note on the server the 
	// ordering is done by the repo
	@Override
	public int compareTo(Gift other) {
		return (int) (other.id - id);
	}
}
