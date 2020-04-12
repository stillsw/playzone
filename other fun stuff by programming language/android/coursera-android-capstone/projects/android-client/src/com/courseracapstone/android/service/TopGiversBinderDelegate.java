package com.courseracapstone.android.service;

import retrofit.RetrofitError;
import android.util.Log;

import com.courseracapstone.android.model.PotlatchUser;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.common.PagedCollection;

/**
 * Handles the interactions with the server for top givers on behalf of the binder
 * which is the only class that knows about this delegate.
 * 
 * Top givers is created on demand, since it isn't need at start up time. Once created
 * it sticks around.
 * 
 * @author xxx xxx
 *
 */
public class TopGiversBinderDelegate {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	private DownloadBinder mDownloadBinder;
	private GiftsBinderDelegate mGiftsBinderDelegate;
	private PartialPagedList<PotlatchUser> mPartialList;
	
	TopGiversBinderDelegate(DownloadBinder downloadBinder, GiftsBinderDelegate giftsBinderDelegate) {
		mDownloadBinder = downloadBinder;
		mGiftsBinderDelegate = giftsBinderDelegate;
	}

	/**
	 * Called from DownloadBinder.loadTopGivers() it downloads the top givers list from 
	 * the server and sends a message to the UI when done.
	 */
	void loadTopGiversLockForegroundTasks() {

		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				// lock on the foreground tasks 
				Log.d(LOG_TAG, "loadTopGiversLockForegroundTasks: attempt to lock");
				
				mDownloadBinder.mForegroundTaskLock.lock();
				try {
					mDownloadBinder.awaitLockForegroundTasks();

					PagedCollection<PotlatchUser> pagedTopGivers = 
							mDownloadBinder.mForegroundSvcApi.getTopGivers(0, mGiftsBinderDelegate.mPageSize);
					
					// create a new container if not already there
					if (mPartialList == null) {
						mPartialList = new PartialPagedList<PotlatchUser>();
					}

					// only one page of data
					if (pagedTopGivers.getIsLastPage()) {
						mPartialList.resetToNewList(null, pagedTopGivers);
					}
					// otherwise, download another page
					else {
						mPartialList.resetToNewList(null, pagedTopGivers, 
								mDownloadBinder.mForegroundSvcApi.getTopGivers(1, mGiftsBinderDelegate.mPageSize));
					}

					// let the ui know there's data
					mDownloadBinder.sendMessage(DownloadBinder.NEW_TOP_GIVERS_DATA_READY, null);
						
				} 
				catch (RetrofitError e) {
					// network error will cause dialog to user
					// no gift found will cause 404 to user
					try {
						mDownloadBinder.processServerError(e, true);
					} 
					catch (AuthTokenStaleException e1) {
						Log.w(LOG_TAG, "RetrofitError exception handler: stale, happened during this call", e);
					}
				}
				finally {
					mDownloadBinder.signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * Called as a result of the user scrolling
	 * invoking binder.loadTopGiversNextPage() or binder.loadTopGiversPrevPage().
	 * 
	 * @param addToTop
	 */
	void loadAnotherPageLockForegroundTasks(final boolean addToTop) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {

				// lock on the foreground tasks 
				mDownloadBinder.mForegroundTaskLock.lock();
				try {
					mDownloadBinder.awaitLockForegroundTasks();

					// when data changes it can look to the ui adapter as though it needs
					// to get another page, in which case the pagedList will return false
					// and no query is needed
					
					boolean isValid = false;
					
					if (mPartialList != null) {
						isValid = mPartialList.prepareToAddOnePage(addToTop);
						
						// refresh each page of results
						int numPagesInCache = ((mPartialList.getBottomPageInCache() - mPartialList.getTopPageInCache()) +1);
						
						@SuppressWarnings("unchecked") // can't directly create an array of a generic type... boo :(
						PagedCollection<PotlatchUser>[] pagedUsersArr 
							= (PagedCollection<PotlatchUser>[]) new PagedCollection[numPagesInCache];
						
						Log.d(LOG_TAG, "refreshAllCachedPages: count="+pagedUsersArr);		
						
						for (int i = 0; i < pagedUsersArr.length; i++) {
							//TODO remove this sanity check
							if (mPartialList.getTopPageInCache() + i > mPartialList.getBottomPageInCache()) {
								Log.e(LOG_TAG, String.format(
										"refreshGiftsLockForegroundTasks: error, asking too many pages (top=%s, bottom=%s, asking=%s"
										, mPartialList.getTopPageInCache(), mPartialList.getBottomPageInCache()
										, mPartialList.getTopPageInCache() + i));
							}

							// download one page
							pagedUsersArr[i] = mDownloadBinder.mForegroundSvcApi.getTopGivers(
									mPartialList.getTopPageInCache() + i,
											mGiftsBinderDelegate.mPageSize);
						}
						
						// have all the pages downloaded, get it all put together in the partial list
						mPartialList.refreshAllPages(pagedUsersArr);
					}
					else { // never should be null
						Log.e(LOG_TAG, "getAnotherPage: called when there's no previous page");
					}
					
					// notify the ui
					if (isValid) {
						mDownloadBinder.sendMessage(DownloadBinder.MORE_TOP_GIVERS_DATA_READY, null);
					}
				}
				catch (RetrofitError e) {
					// network error will cause dialog to user
					// no gift found will cause 404 to user
					try {
						mDownloadBinder.processServerError(e, true);
					} 
					catch (AuthTokenStaleException e1) {
						Log.w(LOG_TAG, "RetrofitError exception handler: stale, happened during this call", e);
					}
				}
				finally {
					mDownloadBinder.signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * Should be run immediately after load, when the ui has received the message that the list is ready
	 * 
	 * @return
	 */
	public PartialPagedList<PotlatchUser> getNewTopGiversList() {
		return mPartialList;
	}

	/**
	 * The UI has received the message that the Top Givers data list has changed, and now the adapter
	 * needs the update in the UI thread. This doesn't take a foreground lock, the method
	 * it's calling is synchronized with the 2 methods that might update the data it needs.
	 */
	void rebuildTopGiversUiList() {
		
		if (mPartialList != null) {
			mPartialList.updateClientList();
		}
		else {
			Log.e(LOG_TAG, "rebuildTopGiversUiList: no partialList found to update");
		}
	}

}
