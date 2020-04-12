package com.courseracapstone.android;

import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.courseracapstone.android.model.SyncableOverImmutableFields;
import com.courseracapstone.android.service.PartialPagedList;
import com.courseracapstone.android.service.PartialPagedList.ClientDataStats;

/**
 * The adapter that will bind paged collection lists to a listview idea for scrolling
 * borrowed from http://guides.codepath.com/android/Endless-Scrolling-with-AdapterViews
 * at first, but completely changed.. doesn't use a scroll listener and now allows
 * 2 way scrolling, so the data model is free
 * to prune old rows from both ends of the list and keep its set small
 * 
 * @author xxx xxx
 * 
 */
public abstract class PagedListAdapter<T extends SyncableOverImmutableFields<T>> extends BaseAdapter {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	protected PartialPagedList<T> mPagedList;
	@SuppressWarnings("rawtypes")
	protected ClientDataStats mListStats;

	// set while waiting for the last set of data to load
	protected boolean mIsLoading = false;
	// set when a new set is actually loaded
	protected boolean mHasLoadChanged;

	// identifies the row that triggers a call to load more, by its position when
	// it triggered it, and by its id which is used to find it after the load has happened
	private long mTriggerId;
	private int mTriggerPosition;

	public PagedListAdapter(PartialPagedList<T> partialPagedList, TextView emptyLabel) {
		mPagedList = partialPagedList;
		mListStats = mPagedList.getDataStats();
		setEmptyLabel(emptyLabel);
	}

	@Override
	public int getCount() {
		return mListStats.getListSize();
	}

	@Override
	public T getItem(int position) {
		return mPagedList.getRow(position);
	}

	@Override
	public long getItemId(int position) {
		return mPagedList.getRow(position).getItemId();
	}

	/**
	 * Called by the activity that is monitoring the handler when the data
	 * is reported as ready. Sets the flag to allow the following steps to happen.
	 */
	public void notifyAnotherPageHasLoaded() {
		mHasLoadChanged = true;
		notifyDataSetChanged();
	}

	/**
	 * Called when a re-load has happened, and want to see if it also meant
	 * the triggering item changed position (because rows were pruned). If
	 * not the loading flag can safely be reset
	 * @return
	 */
	private boolean hasTriggerIdPositionChanged() {
		if (!mIsLoading && !mHasLoadChanged) {
			return false;
		}
		
		int posNow = getPositionForTriggerId();
		if (posNow > -1) {
			boolean ret = posNow != mTriggerPosition;
			if (ret) {
				Log.d(LOG_TAG, String.format("hasTriggerIdPositionChanged: POSITION CHANGED, DISABLING LOAD MORE was=%s, now=%s", mTriggerPosition, posNow));
				return true;
			}
		}
		
		mIsLoading = false;
		return false;		
	}
	
	private int getPositionForTriggerId() {
		for (int i = 0; i < mListStats.getListSize(); i++) {
			if (mPagedList.getRow(i).getItemId() == mTriggerId) {
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * Finds the position offset from old to new positions for the trigger item
	 * @return
	 */
	private int getPositionDiffForTriggerId() {

		for (int i = 0; i < mListStats.getListSize(); i++) {
			if (mPagedList.getRow(i).getItemId() == mTriggerId) {
				return i - mTriggerPosition;
			}
		}
		
		throw new IllegalStateException("can't ask for diff for trigger when there isn't one");
	}
	
	/**
	 * 1) Checks for loading happened, if so and rows were pruned (because the trigger item's
	 * position has changed, then post a runnable to scroll the view so it's as near as possible
	 * to the triggering position.
	 * 
	 * 2) Sets the progress bars at the top and bottom to show if they're applicable
	 * 
	 * 3) Triggers a load of more data if needed
	 * 
	 * @param position
	 * @param listView
	 * @param rowHeight
	 * @param topProgress 
	 * @param bottomProgress 
	 */
	protected void handlePageLoading(int position, final ListView listView, final float rowHeight,
			View topProgress, View bottomProgress) {

		// when a load happens and the position changes, further loads
		// are disabled until the scroll has changed  (in the post)
		if (mHasLoadChanged && hasTriggerIdPositionChanged()) {
			if (getPositionForTriggerId() != -1) {
				
				listView.setEnabled(false);
				
				listView.post(new Runnable() {
					
					@Override
					public void run() {
						int diff = getPositionDiffForTriggerId();
//						int pixelScroll = listView.getHeight() * (diff < 0 ? -1 : 1);
						int pixelScroll = (int) (diff * rowHeight);
						listView.scrollListBy(pixelScroll);
						listView.setEnabled(true);
						mIsLoading = false;
					}
				});
			}
		}

		if (mHasLoadChanged) {
			mHasLoadChanged = false;
		}
		
		boolean isFirstRowTheTop = mListStats.isFirstOfTotalRows(0);
		boolean isLastRowTheBottom = mListStats.isLastOfTotalRows(mListStats.getListSize() -1);
		
		// set the progress bars when a top/bottom item is not the first/last in the whole list
		if (position == 0) {
			topProgress.setVisibility(isFirstRowTheTop ? View.INVISIBLE : View.VISIBLE);
		}
		else {
			topProgress.setVisibility(View.INVISIBLE);
		}
		
		if (position == mListStats.getListSize() -1) {
			bottomProgress.setVisibility(isLastRowTheBottom ? View.INVISIBLE : View.VISIBLE);
		}
		else {
			bottomProgress.setVisibility(View.INVISIBLE);
		}
		
		// check have min data
		if (!isFirstRowTheTop 
				&& !mIsLoading 
				&& mListStats.isTimeToRequestPrevPage(position)) {
			
			mIsLoading = true;
			mTriggerId = getItemId(position);
			mTriggerPosition = position;
			Log.d(LOG_TAG, "requesting a prev page, trigger="+position+", id: "+mTriggerId);
			addPrevPage();
		}
		
		if (!isLastRowTheBottom 
				&& !mIsLoading 
				&& mListStats.isTimeToRequestNextPage(position)) {
			
			mIsLoading = true;
			mTriggerId = getItemId(position);
			mTriggerPosition = position;
			Log.d(LOG_TAG, "requesting a next page, trigger="+position+", id: "+mTriggerId);
			addNextPage();
		}
	}

	public abstract void addNextPage();
	public abstract void addPrevPage();

	public void setEmptyLabel(TextView emptyLabel) {
		emptyLabel.setVisibility(mPagedList.getRow(0) == null
				? View.VISIBLE : View.GONE);
	}
}
