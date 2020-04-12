package com.courseracapstone.android.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import retrofit.RetrofitError;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import com.courseracapstone.android.R;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.PotlatchSvcApi;
import com.courseracapstone.android.model.SearchCriteria;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringAndIdSearchCriteria;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringSearchCriteria;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.ImageDownloadBinderDelegate.ImageFailedToGoException;
import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PagedCollection;
import com.courseracapstone.common.PotlatchConstants;

/**
 * Handles the interactions with the server for user notices on behalf of the binder
 * which is the only class that knows about this delegate.
 * The main purpose is to keep that class manageable, as it has several of these.
 * 
 * @author xxx xxx
 *
 */
public class GiftsBinderDelegate {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	// the least time update interval happens is 1 minute, so if a wake up from sleep is within
	// 1 second of that or longer, it's time to refresh again
	private static final long ACCEPTABLE_SLEEP_MARGIN = 1000; 

	final long mPageSize;
	
	private DownloadBinder mDownloadBinder;
	private DataDownloadService mService;
	
	// always keeps the same object, so adapters that use it will always see changes
	HashMap<GiftsSectionType, PartialPagedList<Gift>> mLists = new HashMap<GiftsSectionType, PartialPagedList<Gift>>();
	protected Gift mViewedGift;
	
	GiftsBinderDelegate(DownloadBinder downloadBinder, DataDownloadService service) {
		mDownloadBinder = downloadBinder;
		mService = service;
		mPageSize = estimatePageSize(mService.getResources());

	}

	/**
	 * Does the actual download on behalf of several methods, it takes care of 
	 * testing the gifts type and applying the search title to each type. All of the methods
	 * that call this have already acquired their locks and threads, so it just has to do the
	 * download, invoke the images caching, and pass back the results.
	 * 
	 * Called from:
	 * 1) resetToNewList() - brand new user/gift start, ie. when the fragment for that type is created
	 * 
	 * @param apiInstance
	 * @param searchCriteria
	 * @param giftsType
	 * @param page
	 * @return
	 */
	private PagedCollection<Gift> downloadData(PotlatchSvcApi apiInstance, SearchCriteria<Gift> searchCriteria, GiftsSectionType giftsType, int page) {

		Log.d(LOG_TAG, String.format("downloadData: type=%s, search=%s, page=%s, rows per page=%s api=%s"
				, giftsType, searchCriteria.toString(), page, mPageSize, apiInstance));
		
		PagedCollection<Gift> pagedGifts = null;
		
		// search is either a simple string or with an id for gifts type of chain
		String search = ((SimpleStringSearchCriteria<Gift>)searchCriteria).getSearchString();
		
		if (giftsType == GiftsSectionType.OBSCENE_GIFTS) {
			pagedGifts = apiInstance.findFlaggedGifts(search, page, mPageSize);
		}
		else {
			long chainId = searchCriteria instanceof SimpleStringAndIdSearchCriteria
					? ((SimpleStringAndIdSearchCriteria<Gift>)searchCriteria).getSearchId()
					: -1L;
			
			pagedGifts = apiInstance.getGifts(giftsType.name(), search, chainId, page, mPageSize);
		}
		
		// add the results thumb images to the cache
		mDownloadBinder.mImageDownloadDelegate.addNewImagesToDownloadQueue((ArrayList<Gift>) pagedGifts.getDataList());

		return pagedGifts;
	}
	
	/**
	 * Called when there's a switch of user from the dedicated foreground task thread. Also from 
	 * refreshGiftsLockForegroundTasks() when the gift type changes and it turns out it's the
	 * first time for that type.
	 * Doesn't need to acquire a foreground lock as it's already been taken care of by the caller.
	 * 
	 * @param apiInstance 
	 * @param searchCriteria
	 * @param giftsType 
	 */
	@SuppressWarnings("unchecked")
	void resetToNewList(PotlatchSvcApi apiInstance, SearchCriteria<Gift> searchCriteria, GiftsSectionType giftsType) {
		
		// do the download
		PagedCollection<Gift> pagedGifts = downloadData(apiInstance, searchCriteria, giftsType, 0);

		// the current gifts type is used per fragment that shows each type of gift, it's the basic
		// segment of gifts for display, so it's used as the key to the map
		PartialPagedList<Gift> partialList = mLists.get(giftsType);
		if (partialList == null) {
			partialList = new PartialPagedList<Gift>();
			mLists.put(giftsType, partialList);
		}
		
		// only one page of data
		if (pagedGifts.getIsLastPage()) {
			partialList.resetToNewList(searchCriteria, pagedGifts);
		}
		// otherwise, download another page
		else {
			partialList.resetToNewList(searchCriteria, pagedGifts, downloadData(apiInstance, searchCriteria, giftsType, 1));
		}

		// let the ui know there's data
		mDownloadBinder.sendMessage(DownloadBinder.NEW_GIFTS_DATA_READY, giftsType);
		
	}

	/**
	 * Called from the update thread, so it's a straight refresh of the page(s) already loaded. Check for each
	 * gift type there hasn't been a refresh more recently first.
	 * 
	 * For each gift type list, run the foreground locking download task. If at any time a more recent refresh
	 * has happened stop there.
	 * 
	 * @param apiInstance
	 */
	long refreshGiftsFromBackgroundTask(PotlatchSvcApi apiInstance) {
		
		long latestRefreshTime = -1L;
		for (Map.Entry<GiftsSectionType, PartialPagedList<Gift>> pagedListEntry : mLists.entrySet()) {
			
			PartialPagedList<Gift> pagedList = pagedListEntry.getValue();
			latestRefreshTime = Math.max(pagedList.getLastUpdatedAtMillis(), latestRefreshTime);
			
			if (System.currentTimeMillis() - ACCEPTABLE_SLEEP_MARGIN - latestRefreshTime < mDownloadBinder.mUpdateInterval) {
				Log.d(LOG_TAG, String.format(
						"refreshGiftsFromBackgroundTask: latest refresh (%s) is newer (%s) than update interval (%s), abort"
							, latestRefreshTime, System.currentTimeMillis() - latestRefreshTime, mDownloadBinder.mUpdateInterval));
				return latestRefreshTime;
			}
			
			Log.d(LOG_TAG, String.format("refreshGiftsFromBackgroundTask: refreshing list for %s", pagedListEntry.getKey()));

			refreshGiftsLockForegroundTasks(pagedListEntry.getKey());			
		}
		
		// didn't bomb out so return now as the latest time
		return System.currentTimeMillis();
	}

	/**
	 * Called by refreshGiftsFromBackgroundTask() and also from the ui via binder.refreshGifts()
	 * refreshes one list type. From the ui it'll be the one that's needed, but from the update thread
	 * it's not easy to know (imagine the user is swiping and 2 lists are visible at once!) so it just
	 * starts at the first available and keep going unless the latest update time is later

	 * @param giftsSectionType 
	 */
	void refreshGiftsLockForegroundTasks(final GiftsSectionType giftsSectionType) {
		
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
					
					PartialPagedList<Gift> pagedList = mLists.get(giftsSectionType);
					refreshAllCachedPages(mDownloadBinder.mForegroundSvcApi,
							giftsSectionType, pagedList.getSearchCriteria(), pagedList);
					
					// update the ui
					mDownloadBinder.sendMessage(DownloadBinder.MORE_SAME_TYPE_GIFTS_DATA_READY, giftsSectionType);

				}
				catch (RetrofitError e) {
					
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
	 * Called when the ui is refreshing a list, but it doesn't know if there's data for it already 
	 * or not (for instance, when a new fragment is loaded by swiping to a tab that didn't get
	 * initialized yet)
	 * 
	 * @param giftsType
	 * @param searchCriteria
	 */
	void refreshOrNewGiftsLockForegroundTasks(final GiftsSectionType giftsType, final SearchCriteria<Gift> searchCriteria) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		PartialPagedList<Gift> pagedList = mLists.get(giftsType);
		
		if (pagedList != null) {
			// exists just refresh it
			refreshGiftsLockForegroundTasks(giftsType);
		}
		else {
			// not already there, need to make a new list
			mDownloadBinder.mExecutor.execute(new Runnable() {

				@Override
				public void run() {

					// lock on the foreground tasks 
					mDownloadBinder.mForegroundTaskLock.lock();
					try {
						mDownloadBinder.awaitLockForegroundTasks();

						resetToNewList(mDownloadBinder.mForegroundSvcApi, searchCriteria, giftsType);
						
						// update the ui
						mDownloadBinder.sendMessage(DownloadBinder.NEW_GIFTS_DATA_READY, giftsType);

					}
					catch (RetrofitError e) {
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
	}


	/**
	 * Called as a result of the user scrolling
	 * invoking binder.loadGiftsNextPage() or binder.loadGiftsPrevPage().
	 * 
	 * @param addToTop
	 * @param giftsSectionType
	 */
	void loadAnotherPageLockForegroundTasks(final boolean addToTop, final GiftsSectionType giftsSectionType) {
		
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

					PartialPagedList<Gift> pagedList = mLists.get(giftsSectionType);
					
					// when data changes it can look to the ui adapter as though it needs
					// to get another page, in which case the pagedList will return false
					// and no query is needed
					
					boolean isValid = false;
					
					if (pagedList != null) {
						isValid = pagedList.prepareToAddOnePage(addToTop);
						
						refreshAllCachedPages(mDownloadBinder.mForegroundSvcApi, giftsSectionType,
								(SimpleStringSearchCriteria<Gift>) pagedList.getSearchCriteria(), pagedList);
					}
					else { // never should be null
						Log.e(LOG_TAG, "getAnotherPage: called when there's no previous page");
					}
					
					// notify the ui
					if (isValid) {
						mDownloadBinder.sendMessage(DownloadBinder.MORE_SAME_TYPE_GIFTS_DATA_READY, giftsSectionType);
					}
				}
				catch (RetrofitError e) {
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
	 * Called by getGiftAndPrepareFullImage() and also from the ui via binder.refreshGifts()
	 * refreshes one list type. From the ui it'll be the one that's needed, but from the update thread
	 * it's not easy to know (imagine the user is swiping and 2 lists are visible at once!) so it just
	 * starts at the first available and keep going unless the latest update time is later
	 * 
	 * @param giftId
	 */
	void refreshGiftInForegroundTask(final long giftId) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {

				mViewedGift = null;
				
				// lock on the foreground tasks 
				mDownloadBinder.mForegroundTaskLock.lock();
				try {
					mDownloadBinder.awaitLockForegroundTasks();

					mViewedGift = mDownloadBinder.mForegroundSvcApi.getGiftById(giftId);
					
					mDownloadBinder.sendMessage(DownloadBinder.FRESH_GIFT_READY, null);
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
	 * Attempts to delete the gift, sends message to user when complete, does no
	 * validation here, both the ui and the server do validate the user has permission
	 * to delete the gift
	 * 
	 * @param giftId
	 */
	public void deleteGiftInForegroundTask(final long giftId, final boolean isOwner) {
		
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

					// method of delete depends on ownership
					if (isOwner) {
						Log.d(LOG_TAG, "deleteGiftInForegroundTask: owner deletes");
						mDownloadBinder.mForegroundSvcApi.removeGiftById(giftId);
					}
					else {
						Log.d(LOG_TAG, "deleteGiftInForegroundTask: admin deletes");
						mDownloadBinder.mForegroundSvcApi.removeObsceneGiftById(giftId);
					}
					
					mDownloadBinder.sendMessage(DownloadBinder.GIFT_REMOVED, null);
					
					// update all pages
					refreshAllGiftsAlreadyLockedOnForegroundTask();
					
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
	 * User is uploading a gift and has supplied a file with the image. There are 4 steps
	 * 1) test the size of the file, if it's too large reduce it to within the max
	 * 2) upload the gift
	 * 3) upload the image
	 * 4) feedback to the user how it went
	 * 5) on success, refresh pages
	 * 
	 * @param gift
	 * @param mediaFile
	 * @param contentUri
	 */
	public void addGiftInForegroundTask(final Gift gift, final File mediaFile, final Uri contentUri) {

		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				
				// lock on the foreground tasks 
				mDownloadBinder.mForegroundTaskLock.lock();

				// trap problems so don't try to add image to a missing gift
				Gift addedGift = null;
				
				try {
					mDownloadBinder.awaitLockForegroundTasks();
					
					try {
						 addedGift = mDownloadBinder.mForegroundSvcApi.addGift(gift);
					} 
					catch (RetrofitError e) {
						Log.e(LOG_TAG, "addGiftInForegroundTask: error adding gift", e);
						throw e;
					}
					
					// scales the image if needed
					if (mediaFile != null) {
						mDownloadBinder.mImageDownloadDelegate
							.uploadImageForGiftInSameTask(mDownloadBinder.mForegroundSvcApi,
									addedGift.getId(), mediaFile);
					}
					else {
						mDownloadBinder.mImageDownloadDelegate
							.uploadImageForGiftInSameTask(mDownloadBinder.mForegroundSvcApi,
								addedGift.getId(), contentUri);
					}

					// feedback success
					mDownloadBinder.sendMessage(DownloadBinder.GIFT_ADDED, null);
					
					// update all pages
					refreshAllGiftsAlreadyLockedOnForegroundTask();
					
				}
				catch (RetrofitError e) {
					// network error will cause dialog to user
					// no gift found will cause 404 to user
					try {
						mDownloadBinder.sendMessage(DownloadBinder.GIFT_ADD_FAILED, null);
						mDownloadBinder.processServerError(e, true);
					} 
					catch (AuthTokenStaleException e1) {
						Log.w(LOG_TAG, "RetrofitError exception handler: stale, happened during this call", e);
					}
				}
				catch (ImageFailedToGoException e) {
					Log.e(LOG_TAG, "addGiftInForegroundTask: errored adding image, removing gift", e);
					
					// something failed, remove the gift and feedback
					if (addedGift != null) {
						try {
							mDownloadBinder.mForegroundSvcApi.removeGiftById(addedGift.getId());
						} 
						catch (Exception e1) {
							// gotta stop somewhere, just log it
							Log.e(LOG_TAG, "addGiftInForegroundTask: error to remove gift after image failed", e1);
						}
					}
					
					mDownloadBinder.sendMessage(DownloadBinder.GIFT_ADD_IMAGE_FAILED, null);
				}
				finally {
					mDownloadBinder.signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * Called when another task needs to refresh all existing gifts.
	 * Very similar to the background refresh except this one already has a lock on the
	 * foreground task.
	 */
	private void refreshAllGiftsAlreadyLockedOnForegroundTask() {
		
		for (Map.Entry<GiftsSectionType, PartialPagedList<Gift>> pagedListEntry : mLists.entrySet()) {
			
			PartialPagedList<Gift> pagedList = pagedListEntry.getValue();
			GiftsSectionType giftsSectionType = pagedListEntry.getKey();
			
			refreshAllCachedPages(mDownloadBinder.mForegroundSvcApi,
					giftsSectionType, pagedList.getSearchCriteria(), pagedList);
			
			// update the ui
			mDownloadBinder.sendMessage(DownloadBinder.MORE_SAME_TYPE_GIFTS_DATA_READY, giftsSectionType);
		}
	}

	/**
	 * Called when there's a new search happening and the lists need to begin from
	 * scratch again. Either : 
	 * 1) User has entered search criteria
	 * 2) User filter content changed (the ui will decide when to call this though, since the user
	 * 		could be toggling the setting on/off repeatedly.
	 * 
	 * @param searchCriteria
	 * @param singleSectionType 
	 */
	void resetToNewListsLockForegroundTasks(final SearchCriteria<Gift> searchCriteria, final GiftsSectionType singleSectionType) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		Log.d(LOG_TAG, String.format("resetToNewListsLockForegroundTasks: called for section type (%s), search=%s", 
				singleSectionType == null ? "ALL" : singleSectionType.name(), searchCriteria));
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {

				mViewedGift = null;
				
				// lock on the foreground tasks 
				mDownloadBinder.mForegroundTaskLock.lock();
				try {
					mDownloadBinder.awaitLockForegroundTasks();

					for (Map.Entry<GiftsSectionType, PartialPagedList<Gift>> pagedListEntry : mLists.entrySet()) {
						
						GiftsSectionType giftsSectionType = pagedListEntry.getKey();
						
						// acting on a single section type or all depends on existence of the param
						
						if (singleSectionType == null || giftsSectionType == singleSectionType) {
						
							PartialPagedList<Gift> pagedList = pagedListEntry.getValue();
							pagedList.resetSearchCriteria(searchCriteria);
							
							refreshAllCachedPages(mDownloadBinder.mForegroundSvcApi,
									giftsSectionType, searchCriteria, pagedList);
							
							// update the ui
							mDownloadBinder.sendMessage(DownloadBinder.NEW_GIFTS_DATA_READY, giftsSectionType);
						}
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
	 * Called when constructing the adapter to show Gifts lists.
	 * Note, only for first time into the page, updates don't need an extra call
	 * as the activity can simply notify the adapter to reload
	 * @return
	 */
	PartialPagedList<Gift> getNewGiftsTypeList(GiftsSectionType giftsSectionType) {
		PartialPagedList<Gift> pagedList = mLists.get(giftsSectionType);
		if (pagedList == null) {
			Log.e(LOG_TAG, "getNewUserGiftsList: error, no data for the type="+giftsSectionType.name());
		}
		return pagedList;
	}

	/**
	 * Called when viewing a gift, if it's not in the list anymore return null.
	 * In the meantime, try to get an update from the server, (which could return not found).
	 * 
	 * Also called when grabbing the header for a one chain list. The header may or may not be
	 * in the list, and it may or may not even be a) on the server, b) available under the current
	 * filtering. In this case the listIdx is always set to 0, since for one chain list it has just
	 * retrieved it from that list anyway. But it's also quite unlikely the giftId corresponds to
	 * the viewedGift in that case, so check ids match before assuming it's the correct one. 
	 * 
	 * @param giftId
	 * @param giftsSectionType
	 * @param listIdx
	 * @return
	 */
	Gift getGiftLocallyAndRefreshInForegroundTask(long giftId, GiftsSectionType giftsSectionType, int listIdx) {
		
		Gift localGift = mViewedGift != null && mViewedGift.getId() == giftId 
				? mViewedGift : null;
		
		// if have a copy, return it, but still get a fresh one
		if (localGift == null && listIdx >= 0) { 
			
			PartialPagedList<Gift> pagedList = mLists.get(giftsSectionType);
	
			if (pagedList == null) {
				Log.e(LOG_TAG, "getGiftFromCache: error, no data for the type="+giftsSectionType.name());
			}
			else {
				// it would be nice if it's still in the same row
				Gift g = pagedList.getRow(listIdx);
				if (g == null || g.getId() != giftId) {
					Log.w(LOG_TAG, "getGiftFromCache: giftId has moved, must have been a data refresh");
			
					// otherwise see if it's still in the list
					for (int i = 0; i < pagedList.getDataStats().getListSize(); i++) {
						g = pagedList.getRow(i);
						if (g.getId() == giftId) {
							localGift = g;
						}
					}
				}
			}
		}
		
		if (localGift == null) {
			// no luck
			Log.w(LOG_TAG, "getGiftFromCache: giftId not in the cache, gonna get from server (may not be there)");
		}
		
		// abort if no auth token
		if (mDownloadBinder.checkAuthTokenIsValid()) {
			// start a fresh download anyway
			refreshGiftInForegroundTask(giftId);
		}
		
		return localGift;
	}

	/**
	 * User has toggled a gift response, makes the update and assumes success unless get an 
	 * error response. The gift is then immediately re-queried, and that's what updates the page.
	 * This way any other update on the server will also be incorporated.
	 * 
	 * If the response has already been made on the server from another session, there'll be an error.
	 * 
	 * @param giftId
	 * @param giftResponseType
	 * @param isChecked
	 */
	void setGiftResponseInForegroundTask(final long giftId,
			final int giftResponseType, final boolean isChecked) {

		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				
				// want to specifically test for bad request, and get a fresh update anyway
				int errorCode = DownloadBinder.NO_ERRORS;
				
				// lock on the foreground tasks 
				mDownloadBinder.mForegroundTaskLock.lock();
				try {
					mDownloadBinder.awaitLockForegroundTasks();

					// bit complex, could refactor and optimize to one call at the server
					
					switch (giftResponseType) {					
					case PotlatchConstants.GIFT_RESPONSE_TYPE_TOUCHED_BY :
						if (isChecked) {
							mDownloadBinder.mForegroundSvcApi.touchedByGift(giftId);
						}
						else {
							mDownloadBinder.mForegroundSvcApi.untouchedByGift(giftId);
						}
						break;

					case PotlatchConstants.GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT :
						if (isChecked) {
							mDownloadBinder.mForegroundSvcApi.inappropriateGift(giftId);
						}
						else {
							mDownloadBinder.mForegroundSvcApi.notInappropriateGift(giftId);
						}
						break;

					case PotlatchConstants.GIFT_RESPONSE_TYPE_OBSCENE_CONTENT :
						if (isChecked) {
							mDownloadBinder.mForegroundSvcApi.obsceneGift(giftId);
						}
						else {
							mDownloadBinder.mForegroundSvcApi.notObsceneGift(giftId);
						}
						break;
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
					
					// unless got an error which is NOT specifically bad request
					// get an update
					if (errorCode == DownloadBinder.NO_ERRORS
							|| errorCode == DownloadBinder.ERROR_BAD_REQUEST) {
						
						refreshGiftInForegroundTask(giftId);
					}
				}
			}
		});
		
	}
	
	/**
	 * The gift should have been downloaded for this to be called
	 * 
	 * @param giftId
	 * @return
	 */
	public Gift getGift(long giftId) {
		if (mViewedGift != null && mViewedGift.getId() == giftId) {
			return mViewedGift;
		}
		else {
			return null;
		}
	}


	/**
	 * Called from binder.hasGiftsData()
	 * @param giftsSectionType
	 * @return
	 */
	boolean hasGiftsTypeList(GiftsSectionType giftsSectionType) {
		return mLists.get(giftsSectionType) != null;
	}
	
	/**
	 * The UI has received the message that the Gifts data list has changed, and now the adapter
	 * needs the update in the UI thread. This doesn't take a foreground lock, the method
	 * it's calling is synchronized with the 2 methods that might update the data it needs.
	 * 
	 * @param giftsSectionType
	 */
	void rebuildGiftsUiList(GiftsSectionType giftsSectionType) {
		PartialPagedList<Gift> partialList = mLists.get(giftsSectionType);
		
		if (partialList != null) {
			partialList.updateClientList();
		}
		else {
			Log.e(LOG_TAG, "rebuildGiftsUiList: no partialList found to update for "+giftsSectionType);
		}
	}

	/**
	 * Called by refreshGiftsLockForegroundTasks() when called from the ui with binder.refreshGifts()
	 * and when refreshGiftsFromBackgroundTask() calls it to refresh all the pages.
	 * It is also called by loadAnotherPageLockForegroundTasks() which has been called as a result of the user scrolling
	 * invoking binder.loadGiftsNextPage() or binder.loadGiftsPrevPage().
	 * In all cases, the methods lock on the foreground tasks and run in their own thread.
	 * 
	 * @param apiInstance
	 * @param giftsSectionType
	 * @param searchCriteria
	 * @param pagedList
	 */
	private void refreshAllCachedPages(PotlatchSvcApi apiInstance, GiftsSectionType giftsSectionType,
			SearchCriteria<Gift> searchCriteria, PartialPagedList<Gift> pagedList) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		// refresh each page of results for the gift type
		int numPagesInCache = ((pagedList.getBottomPageInCache() - pagedList.getTopPageInCache()) +1);
		
		@SuppressWarnings("unchecked") // can't directly create an array of a generic type... boo :(
		PagedCollection<Gift>[] pagedGiftsArr = (PagedCollection<Gift>[]) new PagedCollection[numPagesInCache];
		Log.d(LOG_TAG, "refreshAllCachedPages: count="+pagedGiftsArr);		
		
		for (int i = 0; i < pagedGiftsArr.length; i++) {
			//TODO remove this sanity check
			if (pagedList.getTopPageInCache() + i > pagedList.getBottomPageInCache()) {
				Log.e(LOG_TAG, String.format(
						"refreshGiftsLockForegroundTasks: error, asking too many pages (top=%s, bottom=%s, asking=%s"
						, pagedList.getTopPageInCache(), pagedList.getBottomPageInCache()
						, pagedList.getTopPageInCache() + i));
			}

			// download one page
			pagedGiftsArr[i] = downloadData(apiInstance, searchCriteria, giftsSectionType, pagedList.getTopPageInCache() + i); 
		}
		
		// have all the pages downloaded, get it all put together in the partial list
		pagedList.refreshAllPages(pagedGiftsArr);

	}

	/**
	 * Since it's not possible to know exactly, have to use some resources to get a rough
	 * estimate of the size of a row, and the size of the available screen layout, and then
	 * how many will fit on it. Use the portrait height just for less work to do, it means
	 * more rows than needed will be returned in landscape, but they're also a bit narrower
	 * so it should balance out. This way it's not necessary to refresh all the lists every
	 * time there a config change.
	 * @return
	 */
	private static long estimatePageSize(Resources res) {
		
		float rh = res.getDimension(R.dimen.gifts_lists_row_height);
		float sh = Math.max(res.getDisplayMetrics().widthPixels, res.getDisplayMetrics().heightPixels);
		
		// using a number of rows based on the total height is inaccurate, multiply it
		// up anyway, easier for config changes that way
		
		return (long) (Math.round(sh / rh));
	}

}
