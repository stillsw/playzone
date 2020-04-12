package com.courseracapstone.android.model;

import java.util.Comparator;

/**
 * Used by PartialPagedList to hold a generic search criteria for
 * the the list. Not essential for the list itself, more for keeping
 * all the data in one place, so can switch from one type of list to
 * another easily, and maintain several sets, if req'd. (eg. gifts,
 * my gifts, gift chains, obscene gifts)
 * 
 * @author xxx xxx
 *
 */
public interface SearchCriteria<T> {

	public Comparator<T> getComparator();
	
	public class SimpleStringSearchCriteria<T> implements SearchCriteria<T> {
		// don't use nulls, better for retrofit it seems
		private static final String EMPTY_STRING = "";
		
		protected String mSearchString;

		public SimpleStringSearchCriteria(String searchString) {
			setSearchString(searchString);
		}

		public String getSearchString() {
			return mSearchString;
		}

		public void setSearchString(String searchString) {
			mSearchString = searchString == null ? EMPTY_STRING : searchString;
		}

		@Override
		public String toString() {
			return "SimpleStringSearchCriteria [searchString=" + mSearchString + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((mSearchString == null) ? 0 : mSearchString.hashCode());
			return result;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleStringSearchCriteria other = (SimpleStringSearchCriteria) obj;
			if (mSearchString == null) {
				if (other.mSearchString != null)
					return false;
			} else if (!mSearchString.equals(other.mSearchString))
				return false;
			return true;
		}

		@Override
		public Comparator<T> getComparator() {
			return null;
		}
	}
	
	@SuppressWarnings("rawtypes")
	public class SimpleStringAndIdSearchCriteria<T> extends SimpleStringSearchCriteria<T> {
		private long mSearchId;

		@Override
		public String toString() {
			return "SimpleStringAndIdSearchCriteria [searchId=" + mSearchId
					+ ", searchString=" + mSearchString + "]";
		}

		public SimpleStringAndIdSearchCriteria(String searchString, long searchId) {
			super(searchString);
			this.mSearchId = searchId;
		}

		public long getSearchId() {
			return mSearchId;
		}

		public void setSearchId(long mSearchId) {
			this.mSearchId = mSearchId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + (int) (mSearchId ^ (mSearchId >>> 32));
			result = prime * result + ((mSearchString == null) ? 0 : mSearchString.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleStringAndIdSearchCriteria other = (SimpleStringAndIdSearchCriteria) obj;
			if (mSearchId != other.mSearchId)
				return false;
			if (mSearchString == null) {
				if (other.mSearchString != null)
					return false;
			} else if (!mSearchString.equals(other.mSearchString))
				return false;
			return true;
		}
		
	}
}
