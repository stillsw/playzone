package com.courseracapstone.android.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

import android.util.Log;

import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.SearchCriteria;
import com.courseracapstone.android.model.SyncableOverImmutableFields;
import com.courseracapstone.common.PagedCollection;

/**
 * Encapsulate data about the whole collection list, not just the partial
 * page last returned. The idea is that when scrolling and the next page is returned
 * the data gets added to the list, but once there's a few pages loaded, then there are
 * old rows which are aged out, so this class will prune them. When the user scrolls back
 * to the previous pages, the data is re-queried, so updates are automatically incorporated.
 * This way there's only ever a few pages loaded and huge tables can be queried without
 * any problem.
 * 
 * Drawback of the approach is the fine management needed to keep track of the exact position
 * in the list and how many rows are on screen. It would seem optimal to keep 2 whole pages in
 * the list since that's always enough to show everything that needs to be shown. 3 keeps more
 * data, needs large updates and is harder to manage, but still seems it might be worth it.
 * 
 * Since it's not possible to tell when the user will scroll, and they may scroll
 * while one of the 3 main refresh list methods is already happening, they must all block each
 * other. They are all called in the background and send a message to the gui class
 * when data is ready, so there's no problem. As well as that, the client facing data has
 * synchronized access and is updated in a very short call.
 * 
 * @author xxx xxx
 */
public class PartialPagedList<T extends SyncableOverImmutableFields<T>> {
	
	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	private static final int MAX_PAGES = 3;
	
	// the set of data that is currently held
	private ArrayList<T> mCachedList = new ArrayList<T>();
	
	// a copy of the list which is is the one through which clients access the
	// data. A data lock is acquired to prevent access to a row
	// during the short time it takes to update
	// note all the client accessible data (see getters) are based on this
	// list and around its update, this keeps everything consistent as much
	// as possible. If there are problems still because between calling
	// one getter and another, the data changes, may have to be even more careful
	private volatile ArrayList<T> mClientList = new ArrayList<T>();
	private volatile ClientDataStats mClientDataStats = new ClientDataStats();
	
	// the page size used for querying (important, for management to keep this constant
	// if there's a config/screen orientation change, then the page size needs to be
	// recalculated and the correct position in the list reset, but leave that to the
	// gui classes to make happen (simple refresh will work)
	private int mPageSize;
	
	// the total number of pages and rows in the model (ie. the source reports this on each query)
	private int mTotalPages, mTotalRows;
	
	// the top and bottom pages currently held in the cached list
	private int mTopPageInCache, mBottomPageInCache;
	
	// flags to indicate if the cached list contains the first/last pages
	private boolean mHasFirstPage, mHasLastPage;
	
	// useless unless data has been loaded
	private boolean mIsInitialized = false;
	
	// protect changes to the lists with a lock
	private ReentrantLock mDataLock = new ReentrantLock();
	// ensure updates don't mess up by taking the last update time when achieve a lock
	private long mLastUpdatedAtMillis;
	
	// keep the search criteria being used for this list (not used in this class, just
	// convenient to keep it with the rest of the results sets)
	private SearchCriteria<T> mSearchCriteria;
	
	/**
	 * Returns a row from the client list, note if that row isn't there anymore (because
	 * a refresh has happened since the client got the data but before the client has
	 * reacted to a notify data set changed), then null is returned... the client would 
	 * then know to refresh the data immediately
	 * 
	 * These 3 methods are synchronized to prevent inconsistent access, although it could
	 * happen. If it's necessary another technique could be employed, but this seems
	 * good enough for now.
	 *  
	 * @param idx
	 * @return
	 */
	public synchronized T getRow(int idx) {
		try {
			return mClientList.get(idx);
		} 
		catch (IndexOutOfBoundsException e) {
			Log.w(LOG_TAG, "getRow: out of bounds, data must have changed - refresh it");
			return null;
		}
	}

	/**
	 * Returns the client facing stats that the data has, these only change rapidly at the
	 * end of the updates.
	 * 
	 * These 3 methods are synchronized to prevent inconsistent access, although it could
	 * happen. If it's necessary another technique could be employed, but this seems
	 * good enough for now.
	 *  
	 * @return
	 */
	public synchronized ClientDataStats getDataStats() {
		return mClientDataStats;
	}

	/**
	 * Called from the UI thread once it has received notification that there is a change
	 * to the lists. Note the copy is shallow, to use the least memory for it, yes
	 * it could mean the data row is concurrently updated and retrieved and the client
	 * could see something inconsistent. But this has been designed for data like Gifts
	 * where the immutable fields are the important ones, the others don't matter so much.
	 * In any case, the chances are fairly small (I imagine) that between one of the update
	 * methods updating just one of the rows and the end of this update, the client
	 * would request data from it.
	 * 
	 * These 3 methods are synchronized to prevent inconsistent access, although it could
	 * happen. If it's necessary another technique could be employed, but this seems
	 * good enough for now.
	 *  
	 */
	synchronized void updateClientList() {
		mClientList.clear();
		mClientList.addAll(mCachedList);
		mClientDataStats.setClientDataStats(
				mSearchCriteria, mCachedList.size(), mPageSize, mTotalPages, 
				mTotalRows, mHasFirstPage, mHasLastPage);

		// sanity check, should be enough here to assess
		logSanityCheck();
	}

	// backend methods (package access only)
	
	int getPageSize() {
		return mPageSize;
	}

	int getTotalPages() {
		return mTotalPages;
	}


	long getLastUpdatedAtMillis() {
		return mLastUpdatedAtMillis;
	}


	int getTopPageInCache() {
		return mTopPageInCache;
	}

	int getBottomPageInCache() {
		return mBottomPageInCache;
	}

	SearchCriteria<T> getSearchCriteria() {
		return mSearchCriteria;
	}

	// TODO remove these once happy it works
	void logSanityCheck() {
		try {
			Log.d(LOG_TAG, 
					String.format("logSanityCheck: sanity check cachePages=%s, totalPages=%s totalRows%s"+
							"\n\t topCache=%s, hasFirst=%s"+
							"\n\t bottomCache=%s, hasLast=%s"+
							"\n\t cacheRows=%s"
//							"\n\t filledPages=%s"+
//							"\n\t partial(end)page rows=%s"
							, (mBottomPageInCache - mTopPageInCache) +1, mTotalPages, mTotalRows
							, mTopPageInCache, mHasFirstPage
							, mBottomPageInCache, mHasLastPage
							, mCachedList.size()
//							, mCachedList.size() / (((mBottomPageInCache - mTopPageInCache) +1) * mPageSize) 
//							, mCachedList.size() % mPageSize 
							));
		} catch (Exception e) {
			Log.e(LOG_TAG, "messed up the sanity log call", e);
		}
	}

	void printlists(String which, ArrayList<T> w) {
		StringBuilder sb = new StringBuilder("logOrderingOf"+which+"List:\n\t");
		for (T g : w) {
			Gift gift = (Gift) g;
			sb.append(gift.getId());
			sb.append(", ");
		}

		Log.d(LOG_TAG, sb.toString());
	}
	
	/**
	 * Called from GiftsBinderDelegate.resetToNewListsLockForegroundTasks *before* it has the
	 * new list data, and it's about to call refresh all data, so the search criteria must be
	 * adjusted first
	 * @param searchCriteria
	 */
	void resetSearchCriteria(SearchCriteria<T> searchCriteria) {
		Log.d(LOG_TAG, "resetSearchCriteria: called to set to "+searchCriteria);
		mSearchCriteria = searchCriteria;
	}
	
	/**
	 * Removes any data held and starts afresh with the new list
	 * @param pagedList
	 */
	synchronized void resetToNewList(SearchCriteria<T> searchCriteria, 
			@SuppressWarnings("unchecked") PagedCollection<T> ... pagedLists) {
		
		// grab when try to update the list in case another thread
		// updates it in the meantime
		long calledAtMillis = System.currentTimeMillis();
		
		mDataLock.lock();
		
		try {
			// make sure something else hasn't changed the list since waiting on the lock
			if (mLastUpdatedAtMillis > calledAtMillis) {
				String msg = String.format("resetToNewList: Cache updated (at %s) while waiting (since %s)"
						, mLastUpdatedAtMillis, calledAtMillis);
				Log.e(LOG_TAG, msg);
				throw new IllegalStateException(msg);
			}
			
			mSearchCriteria = searchCriteria;
			mLastUpdatedAtMillis = System.currentTimeMillis();
			
			if (mIsInitialized) {
				mCachedList.clear();
			}
			else {
				mIsInitialized = true;
			}
			
			// update the variables so all is consistent
			mHasFirstPage = pagedLists[0].getIsFirstPage();
			mTotalPages = pagedLists[0].getTotalPages();
			mTotalRows = (int) pagedLists[0].getTotalSize();
			mPageSize = pagedLists[0].getPageSize();

			// the last 2 vars can be assessed from the data passed
			int bottomPageInRefresh = mTopPageInCache;
			boolean refreshContainsLastPage = false;
			
			for (PagedCollection<T> pagedList : pagedLists) {
				mCachedList.addAll(pagedList.getDataList());
				
				bottomPageInRefresh = pagedList.getPageNum();
				refreshContainsLastPage |= pagedList.getIsLastPage();
			}

			mBottomPageInCache = bottomPageInRefresh;
			mHasLastPage = refreshContainsLastPage;
			
			// sort the results
			if (mSearchCriteria == null || mSearchCriteria.getComparator() == null) {
				Collections.sort(mCachedList);
			}
			else {
				Collections.sort(mCachedList, mSearchCriteria.getComparator());
			}

		}
		finally {
			mDataLock.unlock();
		}
	}
	
	/**
	 * The result of a user action to
	 * scroll up or down and finding more rows are needed. Rows in the
	 * other direction are pruned if the scroll causes it to go over max pages.
	 * 
	 * Note: This method changes meta data, and removes rows! 
	 * It prepares the way for the same locked foreground thread to do an immediate 
	 * refresh of the data. It doesn't happen right here though.
	 * 
	 * @param addToTop
	 * @return false means it's a 'spurious' request (probably because of data changes)
	 */
	boolean prepareToAddOnePage(boolean addToTop) {
		
		if (!mIsInitialized) {
			String msg =  "prepareToAddOnePage: called for an uninitialised list";
			Log.e(LOG_TAG, msg);
			throw new IllegalStateException(msg);
		}

		// sanity checks fail
		if (addToTop && (mHasFirstPage || mTopPageInCache == 0) ) { // should not have to make both these checks
																	// just being super careful
			Log.w(LOG_TAG, "prepareToAddOnePage: request to add to top when already have the first page");
			return false;
		}
		
		if (!addToTop && (mHasLastPage || mBottomPageInCache >= mTotalPages)) { // ditto
			Log.w(LOG_TAG, "prepareToAddOnePage: request to add to botom when already have the last page");
			return false;
		}
		
		// grab when try to update the list in case another thread
		// updates it in the meantime, this would be an error, because
		// all the updating methods lock on the foreground tasks lock
		long calledAtMillis = System.currentTimeMillis();
		
		mDataLock.lock();
		
		try {
			// not such a problem if something else changes the list because
			// this method will be followed by a full refresh asap
			if (mLastUpdatedAtMillis > calledAtMillis) {
				String msg = String.format(
						"prepareToAddOnePage: need better lock around this, Cache updated (at %s) while waiting (since %s)"
						, mLastUpdatedAtMillis, calledAtMillis);
				Log.w(LOG_TAG, msg);
			}
			
			mLastUpdatedAtMillis = System.currentTimeMillis();
			
			// check how many pages we now have
			int numPages = (mBottomPageInCache - mTopPageInCache) +1;
			numPages++; // since one is now being added
			
			// need to evaluate if have first/last pages here because it will be used to calc the max pages
			// will need to re-evaluate if there's a prune though, for the opposite ends
			int numPagesToKeep = MAX_PAGES;
			
			if (addToTop) {
				// reduce the top page by one (that's the add)
				mTopPageInCache--;
				
				// so could now be the top page
				mHasFirstPage = mTopPageInCache == 0;
				if (mHasFirstPage) { // if so, reduce number to keep by one
					numPagesToKeep--;
				}

				// have too many, re-calc bottom page, means it can't be the bottom
				if (numPages > numPagesToKeep) {
					mBottomPageInCache = mTopPageInCache + numPagesToKeep - 1;
					mHasLastPage = false;
				}
			}
			else {
				// vice versa
				mBottomPageInCache++;
				
				mHasLastPage = mBottomPageInCache >= mTotalPages -1; // again super careful because == would do it,
																	 // but if the sanity check was to ever change...
				if (mHasLastPage) {
					numPagesToKeep--;
				}

				// have too many, re-calc top page, means it can't be the top
				if (numPages > numPagesToKeep) {
					mTopPageInCache = (mBottomPageInCache - numPagesToKeep) + 1;
					mHasFirstPage = false;
				}
			}
			
			return true; // changes are prepared
		}
		finally {
			mDataLock.unlock();
		}
	}

	/**
	 * A sequential array of pages that should replace all the data in the list.
	 * Rather than remove all the rows, walk through the old list and remove
	 * any missing, update any there, and add any needed.
	 * 
	 * A list of added rows is passed back for additional processing
	 * (like downloading images to the cache)
	 * 
	 * Note: rows are compared to each other in this method, it's important
	 * that the lists do have robust hashcode() and equals(), and also can
	 * be compared.
	 * 
	 * @param pagedLists
	 */
	synchronized void refreshAllPages(@SuppressWarnings("unchecked") PagedCollection<T> ... pagedLists) {
		
		// some sanity checking
		int numPagesInCache = ((mBottomPageInCache - mTopPageInCache) +1);

		if (numPagesInCache == 0 || !mIsInitialized) {
			String msg = "cache is empty, can't do a refresh on it, for new list do a reset";
			Log.e(LOG_TAG, msg);
			throw new IllegalArgumentException(msg);
		}

		// got this far, can assume the data sets match:
		// 		1) they start with same page in both sets
		//		2) they have the same number of pages
		//		3) the input set are sequential pages with no gaps
		//			(not tested here, but the caller will have to be sure about it... GIGO)
		
		// grab when try to update the list in case another thread
		// updates it in the meantime
		long calledAtMillis = System.currentTimeMillis();
		
		mDataLock.lock();
		
		try {
			if (mLastUpdatedAtMillis > calledAtMillis) {
				String msg = String.format("refreshAllPages: Cache updated (at %s) while waiting (since %s)"
						, mLastUpdatedAtMillis, calledAtMillis);
				Log.e(LOG_TAG, msg);
				throw new IllegalStateException(msg);
			}
			
			mLastUpdatedAtMillis = System.currentTimeMillis();
			
			// pull all the rows into a combined list, need to do this
			// as page by page it's hard to know if a row has been removed
			ArrayList<T> combinedRefreshList = new ArrayList<T>();
			
			// lastly update the variables so all is consistent
			// mTopPageInCache is unchanged
			mHasFirstPage = pagedLists[0].getIsFirstPage();
			mTotalPages = pagedLists[0].getTotalPages();
			mTotalRows = (int) pagedLists[0].getTotalSize();
			mPageSize = pagedLists[0].getPageSize();

			// the last 2 vars can be assessed from the data passed
			int bottomPageInRefresh = mTopPageInCache;
			boolean refreshContainsLastPage = false;
			
			for (PagedCollection<T> pagedList : pagedLists) {
				combinedRefreshList.addAll(pagedList.getDataList());
				
				bottomPageInRefresh = pagedList.getPageNum();
				refreshContainsLastPage |= pagedList.getIsLastPage();
			}

			// included empty pages (if there's a lot of deletions)
			mBottomPageInCache = bottomPageInRefresh;
			mHasLastPage = refreshContainsLastPage;
			
			// walk through the combined list once to pull out additions and
			// do updates
			ArrayList<T> addedRows = new ArrayList<T>();			
			for (int i = 0; i < combinedRefreshList.size(); i++) {
				
				T row = combinedRefreshList.get(i);
				
				// pull out new rows for insertion as a last step					
				if (!mCachedList.contains(row)) {
					addedRows.add((T) row);
				}
				else {
					// update the cached row 
					T cachedMatch = mCachedList.get(mCachedList.indexOf(row));
					((SyncableOverImmutableFields<T>)cachedMatch).syncMutableFields(row);
				}
			}

			// delete any in cached list not also in the refresh list
			// walk backwards so no side-effects
			for (int j = mCachedList.size() -1; j >= 0; j--) {
				
				T row = mCachedList.get(j);
				
				if (!combinedRefreshList.contains(row)) {
					mCachedList.remove(row);
				}
			}

			// now add the new ones in and sort the results
			if (!addedRows.isEmpty()) {
				mCachedList.addAll(addedRows);
				if (mSearchCriteria.getComparator() == null) {
					Collections.sort(mCachedList);
				}
				else {
					Collections.sort(mCachedList, mSearchCriteria.getComparator());
				}
			}

		}
		finally {
			mDataLock.unlock();
		}
	}
	
	/**
	 * Encapsulates the stats of the cache so they can all be accessed together
	 * and therefore are all consistent with each other at the time
	 */
	public class ClientDataStats {
		private SearchCriteria<T> mSearchCriteria;
		private int mClienListSize;
		private int mClientPageSize;
		private int mClientTotalPages;
		private boolean mClientHasFirstPage;
		private boolean mClientHasLastPage;
		private int mClientTotalRows;
		// give the index of first and last rows in the total list, so if they're not in this
		// sub-list, they have nonsense values
		private int mFirstRowIdx;
		private int mLastRowIdx;
		
		public void setClientDataStats(SearchCriteria<T> searchCriteria, int listSize, int pageSize,
				int totalPages, int clientTotalRows, boolean hasFirstPage, boolean hasLastPage) {
			
			this.mSearchCriteria = searchCriteria;
			this.mClienListSize = listSize;
			this.mClientPageSize = pageSize;
			this.mClientTotalPages = totalPages;
			this.mClientTotalRows = clientTotalRows;
			this.mClientHasFirstPage = hasFirstPage;
			this.mClientHasLastPage = hasLastPage;
			
			mFirstRowIdx = mClientHasFirstPage ? 0 : Integer.MIN_VALUE;
			mLastRowIdx = mClientHasLastPage ? listSize -1 : Integer.MIN_VALUE;
		}

		public SearchCriteria<T> getSearchCriteria() {
			return mSearchCriteria;
		}

		public int getListSize() {
			return mClienListSize;
		}

		public int getPageSize() {
			return mClientPageSize;
		}

		public int getTotalPages() {
			return mClientTotalPages;
		}

		public boolean hasFirstPage() {
			return mClientHasFirstPage;
		}

		public boolean hasLastPage() {
			return mClientHasLastPage;
		}

		public int getTotalRows() {
			return mClientTotalRows;
		}

		public boolean isEmpty() {
			return mClientTotalRows == 0;
		}
		
		public boolean isFirstOfTotalRows(int idx) {
			return idx == mFirstRowIdx;
		}

		public boolean isLastOfTotalRows(int idx) {
			return idx == mLastRowIdx;
		}
		
		public boolean isTimeToRequestPrevPage(int idx) {
			return !mClientHasFirstPage && idx == 0;
		}
		
		public boolean isTimeToRequestNextPage(int idx) {
			return !mClientHasLastPage && idx == mClienListSize -1;
		}
		
	}

}
