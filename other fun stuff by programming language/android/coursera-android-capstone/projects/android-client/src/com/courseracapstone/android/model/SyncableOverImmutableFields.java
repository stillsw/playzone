package com.courseracapstone.android.model;

/**
 * Used by PartialPagedList when refreshing a list from another
 * @author xxx xxx
 *
 * @param <T>
 */
public interface SyncableOverImmutableFields<T> extends Comparable<T> {

	public long getItemId();
	public void syncMutableFields(T other);
}
