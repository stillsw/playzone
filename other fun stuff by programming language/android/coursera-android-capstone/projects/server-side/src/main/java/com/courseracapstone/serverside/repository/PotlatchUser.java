package com.courseracapstone.serverside.repository;

import javax.persistence.Entity;
import javax.persistence.Id;

import com.courseracapstone.common.PotlatchConstants;

/**
 * A simple object to represent user preference
 */
@Entity
public class PotlatchUser { //implements Comparable<PotlatchUser> {

	@Id
	private String name;
	private long touchCount;
	private long updateInterval;
	private boolean filterContent;
	
	public PotlatchUser() {
		updateInterval = PotlatchConstants.DEFAULT_UPDATE_INVERVAL;
	}

	public PotlatchUser(String name) {
		this();
		this.name = name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public long getTouchCount() {
		return touchCount;
	}

	public void setTouchCount(long touchCount) {
		this.touchCount = touchCount;
	}

	public void adjustTouchCount(long adjust) {
		this.touchCount += adjust;
	}

	public long getUpdateInterval() {
		return updateInterval;
	}

	public void setUpdateInterval(long millis) {
		this.updateInterval = millis;
	}

	public boolean isFilterContent() {
		return filterContent;
	}

	public void setFilterContent(boolean filterContent) {
		this.filterContent = filterContent;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		PotlatchUser other = (PotlatchUser) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

//	@Override
//	public int compareTo(PotlatchUser other) {
//		if (touchCount < other.touchCount) {
//			return 1;
//		}
//		else if (touchCount > other.touchCount) {
//			return -1;
//		}
//		else {
//			// not possible, but anyway compare on title
//			return name.compareTo(other.name);
//		}
//	}

}
