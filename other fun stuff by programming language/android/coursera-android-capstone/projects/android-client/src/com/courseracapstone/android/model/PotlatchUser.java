package com.courseracapstone.android.model;

import com.courseracapstone.common.PotlatchConstants;

/**
 * Android side version, class is identical to server-side version except javax.persistence 
 * packages and JPA annotations removed. 
 * If structure changes, simply copy over the existing class from the server-side and edit out those
 * bits.
 * 
 * @author xxx xxx
 *
 */
public class PotlatchUser implements SyncableOverImmutableFields<PotlatchUser>, Comparable<PotlatchUser> {

	private static long NEXT_ID = 0;
	
	private long id; // only used to satisfy the interface, needed by the adapter
	private String name;
	private long touchCount;
	private long updateInterval;
	private boolean filterContent;
	
	public PotlatchUser() {
		updateInterval = PotlatchConstants.DEFAULT_UPDATE_INVERVAL;
		id = NEXT_ID++;
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

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
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

	@Override
	public int compareTo(PotlatchUser other) {
		int touchComp = (int) (other.touchCount - touchCount);
		if (touchComp != 0) {
			return touchComp;
		}
		else {
			return name.compareToIgnoreCase(other.name);
		}
	}

	@Override
	public long getItemId() {
		return id;
	}

	@Override
	public void syncMutableFields(PotlatchUser other) {
		touchCount = other.touchCount;
		updateInterval = other.updateInterval;
		filterContent = other.filterContent;
	}

}
