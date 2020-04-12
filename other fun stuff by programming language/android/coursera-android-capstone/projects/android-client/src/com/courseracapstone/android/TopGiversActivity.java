package com.courseracapstone.android;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.courseracapstone.android.model.PotlatchUser;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PagedCollection;

public class TopGiversActivity extends AbstractPotlatchActivity
	implements LoaderCallbacks<PagedCollection<PotlatchUser>> {

	private final String LOG_TAG = "Potlatch-" + getClass().getSimpleName();

	protected PagedListAdapter<PotlatchUser> mAdapter;
	protected ListView mListView;
	protected ProgressBar mTopProgress;
	protected ProgressBar mBottomProgress;
	protected TextView mEmptyLabel;
	protected float mListRowHeight;

	@SuppressWarnings("unused")
	private boolean mInternetAvailable;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_topgivers);

        // use the previously created adapter if there was set by the non-ui fragment
		mListView = (ListView) findViewById(android.R.id.list);
		
		mTopProgress = (ProgressBar) findViewById(R.id.top_progress_bar);
		mBottomProgress = (ProgressBar) findViewById(R.id.bottom_progress_bar);
		mEmptyLabel = (TextView) findViewById(android.R.id.empty);

		Resources res = getResources();
        mListRowHeight = res.getDimension(R.dimen.gifts_lists_row_height);

        getActionBar().setTitle(res.getString(R.string.title_top_givers));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.topgivers, menu);
		mUserNoticeMenuitem = menu.findItem(R.id.show_user_notice_menuitem);
		mUserNoticeMenuitem.setEnabled(false); // on binding may enable it
		return true;
	}

	/**
	 * Connected to the service that will provide downloads, binds during
	 * onStart()
	 */
	@Override
	protected void onBindToService() {
		super.onBindToService();		
		checkOnlineAndRefreshData();
	}

	/**
	 * Called by onBindToService()
	 */
	void checkOnlineAndRefreshData() {
		if (isNetworkOnlineOtherwiseRegisterForBroadcast()) {

			Log.d(LOG_TAG, "checkOnlineAndRefreshData: have internet, call to refreshData");
			refreshData();
		} 
		else {
			Log.d(LOG_TAG, "onBindToService: no internet");
			// disable actions
			mInternetAvailable = false;
		}
	}

	/**
	 * Previously unable to get internet, and now got it
	 */
	@Override
	protected void onGainInternet() {
		super.onGainInternet();

		mInternetAvailable = true;
		
		// call the binder
		refreshData();
	}

	/**
	 * Test for need to refresh the user and also refresh the gifts. Called from
	 * onBindToService() only if there's a network connection, and also when a
	 * network connection is acquired in onGainOnline()
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
	 public void refreshData() {

		Log.d(LOG_TAG, "refreshData: ");
		
		try {
			mDownloadBinder.loadNewTopGivers();
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
		case DownloadBinder.NEW_TOP_GIVERS_DATA_READY : {
			Log.d(LOG_TAG, "handleBinderMessage: top givers ready");

			// way we need to update the client list here in the ui thread
			mDownloadBinder.rebuildTopGiversAdapterList();
			
			if (mAdapter == null) {
				initAdapter();
			}
			else {
				mAdapter.notifyDataSetChanged();
			}

			break;
		}
		case DownloadBinder.MORE_TOP_GIVERS_DATA_READY : {

			// way we need to update the client list here in the ui thread
			mDownloadBinder.rebuildTopGiversAdapterList();
			
			if (mAdapter == null) {
				initAdapter();
			}
			else {
				mAdapter.notifyAnotherPageHasLoaded();
			}

			break;
		}
		default:
			Log.e(LOG_TAG, "handleBinderMessage: got a message to handle, and passing to superclass=" + what);
			super.handleBinderMessage(what, onTime, giftsType);
			break;
		}
	}

	@Override
	protected void fabButtonPressed() {}
	
	/**
	 * Init the data loader for a new user instance (ie. when sign-in has just completed)
	 * Probably only gets called for the fragment which does All Gifts...
	 */
	void initAdapter() {
		Log.d(LOG_TAG, "initAdapter: init loader, ready (attached)");
		getLoaderManager().initLoader(0, null, this);
		
		mAdapter = new PagedListAdapter<PotlatchUser>(mDownloadBinder.getNewTopGiversList(), mEmptyLabel) {

			@Override
			public void notifyDataSetChanged() {
				super.notifyDataSetChanged();
				setEmptyLabel(mEmptyLabel);
			}

			@Override
			public View getView(int position, View convertView, ViewGroup parent) {

				mEmptyLabel.setVisibility(View.GONE);
				
				handlePageLoading(position, mListView, mListRowHeight, mTopProgress, mBottomProgress);
				
				RelativeLayout layout = null;
				
				try {
					layout = convertView == null 
							? (RelativeLayout) getLayoutInflater().inflate(R.layout.topgiver_list_row, parent, false)
							: (RelativeLayout) convertView;

					PotlatchUser user = mPagedList.getRow(position);
					
					if (user == null) {
						// just in case
						Log.w(LOG_TAG, "initAdapter: user row is gone");
					}
					else {
						TextView name = (TextView) layout.findViewById(R.id.user_name);
						name.setText(user.getName());
						
						Resources res = getResources();
						
						TextView touches = (TextView) layout.findViewById(R.id.list_touch_count);	
						
						// make the right string for touches
						int intTouches = (int) user.getTouchCount();
						touches.setText(res.getQuantityString(
								R.plurals.gift_list_touched_by, intTouches, intTouches));
					}
				} 
				catch (Exception e) {
					// have encountered null from getLayoutInflater() locally, since using the activity
					// it hasn't happened yet... but trap exceptions just in case!
					Log.e(LOG_TAG, "adapter.getView: error inflating the layout", e);
				}

				return layout;
			}
			
			@Override
			public void addPrevPage() {
				try {
					mDownloadBinder.loadTopGiversPrevPage();
				}
				catch (AuthTokenStaleException e) {
					// abort the current task, handleBinderMessages() will deal with it asap
				}
			}
			
			@Override
			public void addNextPage() {
				try {
					mDownloadBinder.loadTopGiversNextPage();
				}
				catch (AuthTokenStaleException e) {
					// abort the current task, handleBinderMessages() will deal with it asap
				}
			}			
		};
		
		mListView.setAdapter(mAdapter);
		
	}

	// ----------------------- LoaderManager.callbacks
	
	@Override
	public Loader<PagedCollection<PotlatchUser>> onCreateLoader(int id, Bundle args) {
		return new PagedCollectionAsyncLoader<PotlatchUser>(this, mDownloadBinder);
	}

	@Override
	public void onLoadFinished(Loader<PagedCollection<PotlatchUser>> loader, PagedCollection<PotlatchUser> data) {
		Log.d(LOG_TAG, "onLoadFinished: swap the data in the loader... somehow?");
		
	}

	@Override
	public void onLoaderReset(Loader<PagedCollection<PotlatchUser>> arg0) {
		Log.w(LOG_TAG, "onLoaderReset: somehow lost the data in a custom loader!!");
	}


}
