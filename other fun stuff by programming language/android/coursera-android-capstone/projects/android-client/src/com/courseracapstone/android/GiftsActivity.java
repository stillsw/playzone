package com.courseracapstone.android;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.courseracapstone.android.GiftsFragment.GiftListAdapter;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.UserNotice;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.PartialPagedList;
import com.courseracapstone.common.GiftsSectionType;

/**
 * The main entry point to the app. Shows the 3 main tabs of gifts (4 for admin user)
 * 
 * @author xxx xxx
 */
public class GiftsActivity extends AbstractGiftsListActivity {

	private final String LOG_TAG = "Potlatch-" + getClass().getSimpleName();

	// boolean extra that is added to the intent when the user has changed
	// see onNewIntent()
	public static final String LOGGED_IN_NEW_USER = "LOGGED_IN_NEW_USER";
	
	// millis that is ok to call a broadcast received when sent (longer than
	// this implies getting it late, eg. after resume)
	private static final long ACCEPTABLE_BROADCAST_RECEIVE_DELAY = 100;

	// allows the binder to be passed in a bundle
	public static final String PARAM_BINDER = "com.courseracapstone.android.paramBinder";

	// the tabs menu
	private SlidingTabsColorsFragment mTabsFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

//		// because of a bug in the transitions for L, this needs to be in both activities
//		// see stackoverflow.com/questions/24517620/activityoptions-makescenetransitionanimation-doesnt-seem-to-exist
//		getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
//		Transition transition = TransitionInflater.from(this)
//				.inflateTransition(R.transition.view_gift_transition);
//		getWindow().setSharedElementEnterTransition(transition);
//		getWindow().setSharedElementExitTransition(transition);

		
		setContentView(R.layout.activity_gifts);

		// the data fragment
		attachToNonUiFragment();

		Runnable onSuccessfulLoginCallback = new Runnable() {
			
			@Override
			public void run() {
				makeSlidingTabs();
				fillUserDetails();
				fillSearchDetails();
			}
		};
		
		// might have to go elsewhere do it first, test returns true
		// when the user is signed in
		if (testForLoginRedirect(onSuccessfulLoginCallback)) {
			// true means user is already signed in, so just go
			// ahead and add the tabs now
			onSuccessfulLoginCallback.run();
		}
		
	}

	/**
	 * Set at login, when search changes, and also when user prefs are received
	 * or changed
	 */
	protected void fillSearchDetails() {
		TextView searchDetsVw = (TextView) findViewById(R.id.search_details);
		Resources res = getResources();
		
		String filter = res.getStringArray(R.array.content_filter_applied)
				[mIsFilterContent == null 
				? CONTENT_FILTER_UNKNOWN : 
					mIsFilterContent 
						? CONTENT_FILTER_ON : CONTENT_FILTER_OFF];
		
		searchDetsVw.setText(TextUtils.isEmpty(mGiftsFramentComposite.getSearchString())
				? res.getString(R.string.current_no_search_label,
						mGiftsFramentComposite.getSearchString(), filter)
				: res.getString(R.string.current_search_label,
						mGiftsFramentComposite.getSearchString(), filter));
	}

	/**
	 * When login is successful the current user's details are added to the bottom right.
	 * (removed now, since this info can easily be gotten from menu.changeUser)
	 */
	protected void fillUserDetails() {
//		PotlatchApplication app = (PotlatchApplication) getApplication();
//		User user = app.getCurrentUser();
//		Resources res = getResources();
//		
//		String adminStr = user.isAdmin()
//				? String.format("(%s) ", res.getString(R.string.administrator_label))
//				: "";
//
//		TextView loginDetsVw = (TextView) findViewById(R.id.login_details);
//		loginDetsVw.setText(res.getString(R.string.current_login_label, 
//				user.getUsername(), adminStr, user.getServer()));

	}

	private void makeSlidingTabs() {
		FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();
		mTabsFragment = new SlidingTabsColorsFragment();
		transaction.replace(R.id.gifts_content_fragment, mTabsFragment);
		transaction.commitAllowingStateLoss();
	}

	/**
	 * Attaches to the non-ui fragment if it's in the stack, otherwise creates
	 * one
	 */
	private void attachToNonUiFragment() {
		FragmentManager fragmentManager = getFragmentManager();
		mGiftsFramentComposite = (NonUIGiftsDataFragment) fragmentManager
				.findFragmentByTag(NonUIGiftsDataFragment.NON_UI_FRAGMENT_TAG);

		if (mGiftsFramentComposite == null) {
			// create a data holder for ui fragment instances instances
			mGiftsFramentComposite = new NonUIGiftsDataFragment();

			fragmentManager
					.beginTransaction()
					.add((Fragment) mGiftsFramentComposite,
							NonUIGiftsDataFragment.NON_UI_FRAGMENT_TAG)
					.commit();

			fragmentManager.executePendingTransactions();
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// see if there's a search query
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			mGiftsFramentComposite.setSearchString(query);
			fillSearchDetails();
			refreshData(true);
		}
		else if (intent.getBooleanExtra(LOGGED_IN_NEW_USER, false)) {
			
			// user has logged in again, handle it as though it's a new start
			PotlatchApplication app = (PotlatchApplication) getApplication();
			boolean isAdmin = app.isUserSignedIn() && app.getCurrentUser().isAdmin();
			
			Log.d(LOG_TAG, "onNewIntent: signin as new user "+app.getCurrentUser().getUsername()
					+ " admin="+isAdmin);
			
			// is there a fragment that shouldn't be
			if (!isAdmin) {
				((NonUIGiftsDataFragment) mGiftsFramentComposite).clearFragment(getFragmentManager(), GiftsSectionType.OBSCENE_GIFTS);
			}
			
			// reset tabs		
			makeSlidingTabs();
			
			// refresh data (as new user sign in)
			testForLoginRedirect(null);
		}
		else if (!Intent.ACTION_MAIN.equals(intent.getAction())) { 
			Log.d(LOG_TAG, "got an unknown intent action="+intent.getAction());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.gifts, menu);
		mUserNoticeMenuitem = menu.findItem(R.id.show_user_notice_menuitem);
		mUserNoticeMenuitem.setEnabled(false); // on binding may enable it

		// Get the SearchView and set the searchable configuration
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
				.getActionView();

		searchView.setSearchableInfo(searchManager
				.getSearchableInfo(getComponentName()));
		searchView.setIconifiedByDefault(false);
		searchView.setSubmitButtonEnabled(true);
		searchView.setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				return false;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (TextUtils.isEmpty(newText)
						&& !TextUtils.isEmpty(mGiftsFramentComposite.getSearchString())) {
					// special case where the user entered a query but now
					// removed all
					// the chars.. to make it more intuitive, query now for all
					// rows
					mGiftsFramentComposite.setSearchString("");
					fillSearchDetails();
					refreshData(true);
				}
				return false;
			}
		});

		return true;
	}

	
	/**
	 * When a sliding tabs gifts fragment has attached to the activity
	 * and created its views it calls this method to get linked up with the non-UI fragment,
	 * to get any existing adapter and a binder 
	 */
	void linkGiftsFragmentFromTab(GiftsFragment frag) {
		String debug = "linkGiftsFragmentFromTab: type="+frag.getGiftsSectionType();
		Log.d(LOG_TAG, debug);
		
		((NonUIGiftsDataFragment) mGiftsFramentComposite).setGiftsFragment(frag.getGiftsSectionType(), frag);
	}
	
	/**
	 * Sent a message from the data download service or binder, interpret what
	 * the message is and act accordingly
	 */
	@SuppressLint("InflateParams")
	@Override
	protected void handleBroadcastParams(Intent intent) {

		if (intent.getBooleanExtra(DownloadBinder.USER_DATA_READY, false)) {
			Log.d(LOG_TAG, "handleBroadcastParams: user data ready, new user=" + mInitUserReset);
			
			mInitUserReset = false;
			mIsFilterContent = mDownloadBinder.isUserFilterContent();
			fillSearchDetails();
			
			// get the user data back from the binder and display it
			if (mDownloadBinder.isResetUserDataReady()) {
				// make sure the menu option is turned off
				if (mUserNoticeMenuitem != null) {
					// could be a dev env thing, running after a crash there's
					// still an ordered
					// broadcast waiting, otherwise no reason for the menu item
					// to not already be created
					mUserNoticeMenuitem.setEnabled(false);
				}

				// just signed in, update the application (so don't do this
				// again)
				((PotlatchApplication) getApplication()).setUserReset(false);

				// only do something if there's news... ie. nothing happened
				UserNotice userNotice = null;
				try {
					userNotice = mDownloadBinder.getUserNotice(true);
				} 
				catch (AuthTokenStaleException e) {
					// let the next statement deal with null, not much else required here
				}

				if (userNotice == null || userNotice.isEmpty()) {
					return;
				}

				// get the notice, and clear it (since it's showing now)
				final Resources res = getResources();
				final LinearLayout ll = (LinearLayout) getLayoutInflater()
						.inflate(R.layout.notices_alert, null);

				ArrayList<String> messages = assembleUserNoticeMessages(res,
						userNotice);
				String[] msgsArray = new String[messages.size()];
				assembleUserNoticeMessages(res, userNotice).toArray(msgsArray);
				ListView lv = (ListView) ll.findViewById(R.id.listview);
				lv.setAdapter(new ArrayAdapter<String>(this,
						android.R.layout.simple_list_item_1, msgsArray));

				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						PotlatchApplication app = (PotlatchApplication) getApplication();
						String title = res.getString(
								R.string.fresh_sign_in_title, app
										.getCurrentUser().getUsername());

						new AlertDialog.Builder(GiftsActivity.this)
								.setTitle(title).setView(ll).create().show();
					}
				});
			} 
			else {
				// how long ago was it sent
				boolean sentJustNow = System.currentTimeMillis()
						- intent.getLongExtra(DownloadBinder.TIME_SENT,
								System.currentTimeMillis()) < ACCEPTABLE_BROADCAST_RECEIVE_DELAY;

				// only do something if there's news... ie. nothing happened
				UserNotice userNotice = null;
				try {
					userNotice = mDownloadBinder.getUserNotice(false);
				} 
				catch (AuthTokenStaleException e) {
					// not actually thrown when param is false
				}
				
				if (userNotice != null && !userNotice.isEmpty()) {
					// it's just an update, show in the action bar
					enableUserNoticeActionItem(sentJustNow);
				}
			}

		} else {
			// the backend is just wanting to see if the front end is awake
			super.handleBroadcastParams(intent);
		}
	}
	
	@Override
	protected void fabButtonPressed() {
		addGift(null, null);
	}

	@Override
	GiftListAdapter getListAdapter(GiftsFragment giftsFragment, PartialPagedList<Gift> partialPagedList, 
			GiftsSectionType giftsSectionType, TextView emptyLabel) {

		if (GiftsSectionType.ALL_GIFT_CHAINS != giftsSectionType) {
			return giftsFragment.new TabbedGiftListAdapter(partialPagedList, emptyLabel);
		}
		else {
			return giftsFragment.new ChainGiftListAdapter(partialPagedList, emptyLabel);
		}
	}

}
