package com.courseracapstone.android;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.courseracapstone.android.GiftsFragment.GiftListAdapter;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.SearchCriteria;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.PartialPagedList;
import com.courseracapstone.android.utils.Utils;
import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PotlatchConstants;

/**
 * Superclass for the 2 activities which handle gifts fragment lists
 * 
 * @author xxx xxx
 *
 */
public abstract class AbstractGiftsListActivity extends AbstractGiftAddingActivity {

	private static final String LOG_TAG = "Potlatch-"+ AbstractGiftsListActivity.class.getSimpleName();

	// values to access content strings
	protected static final int CONTENT_FILTER_OFF = 0, CONTENT_FILTER_ON = 1, CONTENT_FILTER_UNKNOWN = 2;

	// the persistent fragment (over config changes) that hold the gifts ui
	// fragment datas
	protected GiftsFragmentComposite mGiftsFramentComposite;

	// set when the user signs in
	protected Boolean mIsFilterContent = false;

	// default the behaviour when connecting to the binder (onStart)
	protected boolean mOnBindSearchBehaviourIsNewSearch = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// disable title for Gifts lists
		getActionBar().setDisplayShowTitleEnabled(false);
	}
	
	/**
	 * Note. Big gotcha with fragments though (dialog at least), if expecting to fire onActivityResult() themselves
	 * it will be intercepted here... so check for that. Note, too that the requestCode won't be what they're
	 * expecting either... is this a bug in Android... it's horrible?!
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		// is this a result from the camera?
		FragmentManager fm = getFragmentManager();

		// check for the dialog open first
		Fragment dialogFrag = fm.findFragmentByTag(AbstractPotlatchActivity.DIALOG_FRAGMENT_TAG);

		Log.d(LOG_TAG, String.format("onActivityResult: returned requestCode=%s, result=%s add gifts dialog exists=%s"
				, requestCode, resultCode == RESULT_OK, dialogFrag != null));

		if (dialogFrag != null) {
			// check to see if camera is also in progress
			NonUIAddGiftDataFragment nonUiFrag = (NonUIAddGiftDataFragment) fm.findFragmentByTag(
					NonUIAddGiftDataFragment.NON_UI_ADD_FRAGMENT_TAG);
	
			if (nonUiFrag.isCameraInProgress() || nonUiFrag.isChooserInProgress()) {
				// forward to the dialog
				Log.d(LOG_TAG, "onActivityResult: camera or chooser in progress, forwarding to dialog");
				
				dialogFrag.onActivityResult(requestCode, resultCode, data);
			}
		}
	}

	/**
	 * Connected to the service that will provide downloads, binds during
	 * onStart()
	 */
	@Override
	protected void onBindToService() {
		super.onBindToService();

		boolean dialogIsOpen = isDialogOpen();

		// have any data, if there's a config change while the dialog was open (ie. like when
		// the camera swaps to landscape in the emulator)
		mGiftsFramentComposite.setDownloadBinder(mDownloadBinder, !dialogIsOpen);

		if (!dialogIsOpen) {
			checkOnlineAndRefreshData(true);
		}
		else {
			Log.d(LOG_TAG, "onBindToService: dialog fragment is there, not refreshing");
		}		
	}

	/**
	 * When a resume has not involved refresh to the data it gets done here
	 */
	@Override
	protected void onResume() {
		super.onResume();
		
		if (mWillNeedDataRefresh && !isDialogOpen()) {
			refreshDataOnResumeOrDialogDismissed(false);		
		}
	}

	/**
	 * Called from onResume() and also from AddGiftDialogFragment.onDismiss()
	 */
	void refreshDataOnResumeOrDialogDismissed(boolean isOnDismiss) {
		
		if (isOnDismiss || !isDialogOpen()) {
			// the idea here is to capture the need to refresh all the data because
			// add dialog is just closing (which prevented it onStart()... this all happens
			// because invoking the camera first time from portrait closes and restarts
			// the activity)
			
			if (mDownloadBinder != null) {
				Log.d(LOG_TAG, "refreshDataOnResumeOrDialogDismissed: refresh data as it's not been done");
				
				checkOnlineAndRefreshData(false);
			}
			else {
				// no binder, set flag that need a refresh				
				Log.d(LOG_TAG, "refreshDataOnResumeOrDialogDismissed: no binder yet - setting flag, current="
						+mWillNeedDataRefresh);
				
				if (!mWillNeedDataRefresh) {
					mWillNeedDataRefresh = true;
				}
			}
		}
	}
	
	/**
	 * 
	 * @return
	 */
	boolean isDialogOpen() {
		// the only time don't want to refresh is if the new gift dialog is open
		FragmentManager fm = getFragmentManager();
		AddGiftDialogFragment dialogFrag = 
				(AddGiftDialogFragment) fm.findFragmentByTag(AbstractPotlatchActivity.DIALOG_FRAGMENT_TAG);

		return dialogFrag != null;
	}
	
	/**
	 * Called by onBindToService(), and onStart() from GiftsFragments... that's when
	 * they already have a binder, but here there might not be one, so ignore that
	 * call and let the refresh happen in the onBind timing
	 * 
	 * @param isFromOnBind
	 */
	void checkOnlineAndRefreshData(boolean isFromOnBind) {
		
		if (mDownloadBinder == null) {
			Log.d(LOG_TAG, "checkOnlineAndRefreshData: called before onBind, must be config change from a fragment");
			return;
		}
		
		if (!isFromOnBind && mInitUserReset) {
			Log.d(LOG_TAG, "checkOnlineAndRefreshData: ignoring call from outside while user is resetting");
			return;
		}
		
		if (isNetworkOnlineOtherwiseRegisterForBroadcast()) {

			mGiftsFramentComposite.setInternetAvailable(true, !isDialogOpen());
			
			Log.d(LOG_TAG, "checkOnlineAndRefreshData: have internet, call to refreshData");
			refreshData(isFromOnBind && mOnBindSearchBehaviourIsNewSearch);
		} 
		else {
			Log.d(LOG_TAG, "onBindToService: no internet");
			// disable actions
			noInternetAvailable();
		}
	}

	/**
	 * Called when need to update data but there's no internet connection, and
	 * when internet is lost. The test for isNetworkOnline() also registers a
	 * receiver to notify when the internet is available
	 */
	private void noInternetAvailable() {
		mGiftsFramentComposite.setInternetAvailable(false, !isDialogOpen());
	}

	/**
	 * Previously unable to get internet, and now got it
	 */
	@Override
	protected void onGainInternet() {
		super.onGainInternet();

		mGiftsFramentComposite.setInternetAvailable(true, !isDialogOpen());
		
		// call the binder
		refreshData(mOnBindSearchBehaviourIsNewSearch);
	}

	/**
	 * Test for need to refresh the user and also refresh the gifts. Called from
	 * onBindToService() only if there's a network connection, and also when a
	 * network connection is acquired in onGainOnline(), 
	 * and by AddGiftDialogFragment only when it's directly called
	 * from this activity's fab button and after it saves a new gift.
	 * 
	 * onStart() -> bind to service
	 *  | 
	 *  onServiceConnection()
	 *   \ 
	 *   onBindToService()
	 *    \ 
	 *    isNetworkOnlineOtherwiseRegisterForBroadcast() -> refreshData()
	 *     | 
	 *     |-> no? -> register receiver 
	 *     | 
	 *     onGainInternet() -> refreshData()
	 */
	 protected void refreshData(boolean isNewSearch) {

		// up to the sub-class to test the value BEFORE it calls onResume() if it cares
		// about this. See GiftsActivity.onResume()
		mWillNeedDataRefresh = false;
		Log.d(LOG_TAG, "refreshData: willNeedDataRefresh reset to false");


		// update the screen filtering content flag
		boolean prevFilterContent = mIsFilterContent;
		try {
			mIsFilterContent = mDownloadBinder.isUserFilterContent();
			
			// filter changes are handled as new search
			if (mIsFilterContent != prevFilterContent) {
				isNewSearch = true;
			}
			
			fillSearchDetails();
		} 
		catch (Exception e) {
			// first time in there is no value, quick and dirty solution trap the error
			Log.d(LOG_TAG, "exception trying to get filter content pref="+e.getMessage());
		}

		if (mInitUserReset) {
			// check if the user is currently signed in
			PotlatchApplication app = (PotlatchApplication) getApplication();
			
			if (app.isUserSignedIn()) {
				Log.d(LOG_TAG, "refreshData: new user signin (resetUser: all tabs)");
				mDownloadBinder.resetUser(app.getCurrentUser(), mGiftsFramentComposite.getSearch());
			}
			else {
				Log.w(LOG_TAG, "refreshData: reset not possible until user is signed in");
			}
		}
		
		else if (mGiftsFramentComposite.isInternetAvailable()) {
			
			// subclasses can define how to handle a new search
			handleRefresh(isNewSearch);
		}
		else {
			Toast.makeText(this, getResources().getString(R.string.not_online_search_toast_msg), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Called to complete refreshData when the simple decision has been already assessed
	 * if it represents a brand new search or not. The default behaviour here is to reset all
	 * the gifts data (for gift section types that appear in the menu)
	 * for a new search, and otherwise refresh all those types.
	 * 
	 * Sub-classes will override as needed OneChainGiftsActivity
	 * @param isNewSearch
	 */
	protected void handleRefresh(boolean isNewSearch) {
		
		try {
			if (isNewSearch) { // means all the fragments need an updated set
				Log.d(LOG_TAG, "refreshData: for a new search");
	
				mDownloadBinder.resetGifts(mGiftsFramentComposite.getSearch());
			}
			else {
				Log.d(LOG_TAG, "refreshData: not new search");
	
				for (int i = 0; i < PotlatchConstants.NUM_GIFT_SECTION_MENU_TYPES; i++) {
					GiftsSectionType giftsType = Utils.getGiftSectionTypeForIndex(i); // first one is all gifts, so feed back is quick
					
					GiftsFragment frag = mGiftsFramentComposite.getGiftsFragment(giftsType);
	
					if (frag != null) {
						mDownloadBinder.refreshOrNewGifts(mGiftsFramentComposite.getSearch(), giftsType);
					}
				}
			}
		}
		catch (AuthTokenStaleException e) {
			// abort the current task, handleBinderMessages() will deal with it asap
		}
	}

	/**
	 * Called directly from the binder while the activity is visible (not
	 * stopped) and if not, then onResume() tests for a message (in the
	 * superclass) and calls this method if anything is pending
	 */
	@Override
	public void handleBinderMessage(int what, boolean onTime, GiftsSectionType giftsType) {

		switch (what) {
		case DownloadBinder.NEW_GIFTS_DATA_READY: {
			Log.d(LOG_TAG, "handleBinderMessage: gifts ready");

			// all the adapters are out of date, if any there, they'll be
			// re-created when needed
			GiftsFragment frag = mGiftsFramentComposite.getGiftsFragment(giftsType);
			
			if (frag != null) {

				// way we need to update the client list here in the ui thread
				mDownloadBinder.rebuildGiftsAdapterList(giftsType);
				
				frag.notifyAnotherPageHasLoaded(onTime); // causes initAdapter() if none exists
			}
			else {
				// the pager doesn't load a fragment if it's not next to the one
				// showing currently, so this isn't a problem... it'll be loaded on demand
				Log.d(LOG_TAG, "handleBinderMessage: no fragment loaded for type="
						+ giftsType + " unable to handle NEW_GIFTS_DATA_READY");
			}

			break;
		}
		case DownloadBinder.MORE_SAME_TYPE_GIFTS_DATA_READY: {

			GiftsFragment frag = mGiftsFramentComposite.getGiftsFragment(giftsType);
			if (frag != null) {

				// way we need to update the client list here in the ui thread
				mDownloadBinder.rebuildGiftsAdapterList(giftsType);
				
				frag.notifyAnotherPageHasLoaded(onTime);
			} 
			else {
				Log.e(LOG_TAG, "handleBinderMessage: no fragment loaded for type="
								+ giftsType + " unable to handle MORE_SAME_TYPE_GIFTS_DATA_READY");
			}

			break;
		}
		default:
			Log.e(LOG_TAG, "handleBinderMessage: got a message to handle, and passing to superclass=" + what);
			super.handleBinderMessage(what, onTime, giftsType);
			break;
		}
	}

	protected void fillSearchDetails() {}

	@Override
	protected SearchCriteria<Gift> getSearchCriteria() {
		return mGiftsFramentComposite.getSearch();
	}
	
	/**
	 * Each fragment inits its list adapter by calling this factory method. There
	 * are 2 kinds of gifts list adapters, see GiftsFragment
	 * 
	 * @param giftsFragment
	 * @param partialPagedList 
	 * @param giftsSectionType
	 * @param emptyLabel
	 * @return
	 */
	GiftListAdapter getListAdapter(GiftsFragment giftsFragment, PartialPagedList<Gift> partialPagedList, 
			GiftsSectionType giftsSectionType, TextView emptyLabel) {
		return null;
	}

}
