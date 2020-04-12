package com.courseracapstone.android;

import java.io.IOException;
import java.util.ArrayList;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.courseracapstone.android.PotlatchApplication.BinderHandler;
import com.courseracapstone.android.accountsetup.PotlatchAccountConstants;
import com.courseracapstone.android.accountsetup.User;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.SearchCriteria;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringSearchCriteria;
import com.courseracapstone.android.model.UserNotice;
import com.courseracapstone.android.service.DataDownloadService;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.DownloadBinder.DownloadBinderRecipient;
import com.courseracapstone.common.GiftsSectionType;

/**
 * Superclass of all Potlatch activities to provide common functionality
 * 
 * @author xxx xxx
 * 
 */
public abstract class AbstractPotlatchActivity extends Activity implements DownloadBinderRecipient {

	private static final String LOG_TAG = "Potlatch-"+ AbstractPotlatchActivity.class.getSimpleName();

	// time for the animation of the fab button
	private static final long ROLL_IN_FAB_MILLIS = 600;

	private static final String INTERNET_CONNECT_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
	public static final String DATA_RECEIVER_ACTION = "potlatch.data.receiver";

	// a tag for all dialog fragments, keeps it simple
	public static final String DIALOG_FRAGMENT_TAG = "potlatch.dialog.fragment";
	// params for passing fab location from caller
	public static final String PARAM_FAB_X = "PARAM_FAB_X";
	public static final String PARAM_FAB_Y = "PARAM_FAB_Y";

	// set when the app has signed in to a new user and first enters this
	// activity will be unset after binding to the service is done
	// right now, this activity is always the destination after new login
	// if that changes, have the superclass deal with it instead so
	// same functionality is everywhere
	protected boolean mInitUserReset;

	// binder for server interactions
	protected DownloadBinder mDownloadBinder;
	
	// will be set by any action that requires internet in isNetworkOnline
	protected boolean mIsWaitingForInternet = false;
	// set by onPause() and unset in onStop() and onResume(), sub-classes
	// can assess in their own onResume() if that's the sequence of events by
	// testing the value BEFORE calling super.onResume()
	protected boolean mWillNeedDataRefresh;

	protected MenuItem mUserNoticeMenuitem;

	// fab button and the location passed in from previous activity (for future use in animations)
	protected ImageButton mFabBtn;
	protected float mPrevFabx, mPrevFaby;
	private boolean mHasInitFab; // set once in onWindowFocusChanged()
	private boolean mIsFabPositioned;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getActionBar().setDisplayShowHomeEnabled(true);		
	}

	/**
	 * Call from onCreate with the view that the fab will be attached to
	 * @param v
	 */
	protected void alignManualFabToView(View v) {
		v.addOnLayoutChangeListener(new OnLayoutChangeListener() {
			
			@Override
			public void onLayoutChange(View v, int left, final int top, int right,
					final int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

				// the image view layout gets a 2nd change with a smaller height soon after
				// the first one (which puts it in the wrong place if called first time)
				// so, call the following on the 2nd go

				// trouble is, what if it's an android 'feature' that changes and there's no 
				// 2nd call in the future... so, put into a runnable that runs after a delay
				// and just uses the first value if no more has been passed

				Resources res = getResources();
				int fabSize = res.getDimensionPixelSize(R.dimen.fab_size);
				final int fabMargin = fabSize / 2;
				
				final float startX = res.getDisplayMetrics().widthPixels - (fabSize + fabMargin);
				final float startY = top - fabSize;
				
				// there's a margin on the view for the view gift activity, remove that
				final float endY = bottom - fabSize;

				// run it right away
				if (oldBottom != 0) {
					if (mIsFabPositioned) {
						return;
					}
					mIsFabPositioned = true;
					// there's a margin on the image container at the moment (for view gift)
					// size is fab margin
					setupManualFab(startX, startY, startX, endY, ROLL_IN_FAB_MILLIS);			
				}
				else {
					// run it with a start delay of a half sec, if the previous test gets there first, this
					// will abort (as it sets a flag)
					v.postDelayed(new Runnable() {

						@Override
						public void run() {
							if (mIsFabPositioned) {
								return;
							}
							mIsFabPositioned = true;
							setupManualFab(startX, startY, startX, endY, ROLL_IN_FAB_MILLIS);			
						}
					}, 1500);
				}
			}
		});
	}

	/**
	 * For when the fab is laid out in such a way that makes auto inclusion
	 * difficult. Sub-classes can override to have it create/animate to position 
	 * 
	 * The params point to the centre of the button, to save callers the trouble
	 * of lining it up. 
	 * 
	 * @param startX
	 * @param startY
	 * @param endX
	 * @param endY
	 * @param duration 
	 */
	protected void setupManualFab(float startX, float startY, float endX, float endY, long duration) {
		
		ImageButton fabBtn = (ImageButton) findViewById(R.id.fabbutton);
		fabBtn.setVisibility(View.VISIBLE);
		
		int fabHalfSize = fabBtn.getRight() - fabBtn.getLeft();
		startX -= fabHalfSize;
		startY -= fabHalfSize;
		endX -= fabHalfSize;
		endY -= fabHalfSize;
		
		
		// animate it to full position
        PropertyValuesHolder moveX =  PropertyValuesHolder.ofFloat("x", startX, endX);
        PropertyValuesHolder moveY =  PropertyValuesHolder.ofFloat("y", startY, endY);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(fabBtn, moveX, moveY);

        animator.setDuration(duration);

		// leave duration and interpolator to default
		animator.start();
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		if (this instanceof LoginActivity) {
			return false;
		}
		return true;
	}
	
	/**
	 * Sets up the fab if there is one, plus its location in the caller (if it was passed)
	 */
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		
		if (hasFocus && !mHasInitFab) {
			mHasInitFab = true;
			
			Intent i = getIntent();
			mPrevFabx = i.getFloatExtra(PARAM_FAB_X, -1f);
			mPrevFaby = i.getFloatExtra(PARAM_FAB_Y, -1f);
			
			mFabBtn = (ImageButton) findViewById(R.id.fabbutton);
			if (mFabBtn != null) {
				mFabBtn.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						fabButtonPressed();
					}
					
				});
			}
		}
	}

	protected abstract void fabButtonPressed();

	/**
	 * Called from handleBroadcastParams() and also onBindService() if a non-empty user notice
	 * is waiting 
	 */
	protected void enableUserNoticeActionItem(boolean isJustNow) {
		// TODO if just now, do something fancy... need icon
		
		// login activity doesn't have a menu, so test for null
		if (mUserNoticeMenuitem != null) {
			Log.d(LOG_TAG, "enableUserNoticeActionItem: show user notice in action bar");
			mUserNoticeMenuitem.setEnabled(true);
		}
	}

	/**
	 * Access to fab for passing its coords to add gift, or wherever wants to animate
	 * it
	 * @return
	 */
	public ImageButton getFabBtn() {
		return mFabBtn;
	}

	@SuppressLint("InflateParams")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			
			// needs internet
			if (checkOnlineFeedbackIfNot(this)) {
				
				Intent i = new Intent(this, SettingsActivity.class);
				startActivity(i);				
			}
			else {
				// show a toast that the action can't be done now
				Toast.makeText(this, getResources().getText(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
			}

			return true;
		}
		else if (id == R.id.top_givers_menuitem) {
			
			// needs internet
			if (isNetworkOnlineOtherwiseRegisterForBroadcast()) {

				Intent i = new Intent(this, TopGiversActivity.class);
				startActivity(i);
			}
			else {
				// show a toast that the action can't be done now
				Toast.makeText(this, getResources().getText(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
			}

			return true;
			
		}
		else if (id == R.id.show_user_notice_menuitem) {
			mUserNoticeMenuitem.setEnabled(false);

			UserNotice userNotice = null;
			try {
				userNotice = mDownloadBinder.getUserNotice(true);
			} 
			catch (AuthTokenStaleException e) {
				// let the next statement deal with null, not much else required here
			}
			
			if (userNotice == null || userNotice.isEmpty()) {
				Log.e(LOG_TAG, "onOptionsItemSelected: action bar item was enabled, but notice was empty");
				return true;
			}
			
			final LinearLayout ll = (LinearLayout) getLayoutInflater().inflate(R.layout.notices_alert, null);

			Resources res = getResources();
			ArrayList<String> messages = assembleUserNoticeMessages(res, userNotice);
			String[] msgsArray = new String[messages.size()];
			assembleUserNoticeMessages(res, userNotice).toArray(msgsArray);
			ListView lv = (ListView) ll.findViewById(R.id.listview);
			lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, msgsArray));

			String title = res.getString(R.string.user_notice_msg);
			
			new AlertDialog.Builder(this)
				.setTitle(title)
				.setView(ll)
				.create().show();
		}
		else {

			// many actions need internet
			if (isNetworkOnlineOtherwiseRegisterForBroadcast()) {
				if (id == R.id.change_login_menuitem) {
					promptUserForLogin(true);
				}
			}
			else {
				// show a toast that the action can't be done now
				Toast.makeText(this, getResources().getText(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
			}
		}
		return super.onOptionsItemSelected(item);
	}

	boolean isUserResetting() {
		return mInitUserReset;
	}

	protected void resetStaleUserIfPossible() {
		// check there is a user, otherwise will have to wait... (authenticator will get one)
		PotlatchApplication app = (PotlatchApplication) getApplication();

//		// check is user is already being reset
//		if (mInitUserReset) {
//			Log.d(LOG_TAG, "resetStaleUserIfPossible: user has been reset, nothing to do");
//			return;
//		}
//		else {
//			mInitUserReset = true; 
//		}
			
		User currentUser = app.getCurrentUser();
		String name = currentUser == null ? null : currentUser.getUsername();
		String token = currentUser == null ? null : currentUser.getAuthToken();
		
		if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(token)) {
			Log.d(LOG_TAG, "handleBinderMessage.userAuthTokenIsStale: clear errors and resetUser");

			// make sure no exceptions
			mDownloadBinder.clearErrors();

			// and can now request data again
			mDownloadBinder.resetUser(currentUser, getSearchCriteria());
		}
		else {
			Log.w(LOG_TAG, String.format(
					"handleBinderMessage.userAuthTokenIsStale: either no user (%s) or token (%s)"
						, currentUser == null ? "null" : currentUser.getUsername()
						, currentUser == null ? "null" : currentUser.getAuthToken()));
		}
	}
	
	/**
	 * Sub-classes that are able to supply a search will do so
	 * @return
	 */
	protected SearchCriteria<Gift> getSearchCriteria() {
		return new SimpleStringSearchCriteria<Gift>(null);
	}
	
	/**
	 * Called when the binder receives a message to process now, or in onResume()
	 * when there's one waiting
	 * @param what
	 * @param onTime 
	 * @param giftsSectionType 
	 */
	public void handleBinderMessage(int what, boolean onTime, GiftsSectionType giftsSectionType) {
		
		Resources res = getResources();
		
		switch (what) {
		case DownloadBinder.ERROR_AUTH_TOKEN_STALE:
			
			// test 
			
			// auth token stale, need to deauthorize it, provide callback that will
			// tell the binder to clear the error on success
			userAuthTokenIsStale(new Runnable() {

				@Override
				public void run() {
					resetStaleUserIfPossible();
				}
			});

			break;

		case DownloadBinder.NO_ERRORS : 
			Log.w(LOG_TAG, "handleBinderMessage: got no errors message");
			break;
			
		case DownloadBinder.ERROR_NOT_FOUND : 
			Log.w(LOG_TAG, "handleBinderMessage: got 404 not found from something");
			break;
			
		case DownloadBinder.ERROR_NETWORK : {
			Log.d(LOG_TAG, "handleBinderMessage: network error, throw up dialog to check and retry");

			try {
				new AlertDialog.Builder(this)
					.setTitle(res.getString(R.string.network_error_title))
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setMessage(res.getString(R.string.connect_error))
					.setPositiveButton(res.getString(R.string.retry_connect_button), 
							new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
							    // clear the errors so can go around again
							    mDownloadBinder.clearErrors();
 
							    // try to reload
							    Intent intent = getIntent();

							    overridePendingTransition(0, 0);
							    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
							    finish();

							    overridePendingTransition(0, 0);
							    startActivity(intent);
							    overridePendingTransition(0, 0);
							}
						})
					.setNegativeButton(R.string.menu_login, 
							new DialogInterface.OnClickListener() {
							
							@Override
							public void onClick(DialogInterface dialog, int which) {
							    // clear the errors
							    mDownloadBinder.clearErrors();
							    promptUserForLogin(false); // no return
							}
						})
					.setCancelable(false)
					.create().show();
				
			}
			catch (Exception e) {
				// after laptop sleep while app is running and then open, found an error where
				// android force closes the app trying to display this error after a re-query because
				// it wants to display network closed, but the window's connections are all broken.
				// It's possible this is only because of the situation where the emulator is put to 
				// sleep by the laptop os... however, FC is ugly so trap it here, and see if it can
				// ever happen on a real device!
				Log.w(LOG_TAG, "handleBinderMessage: failed to show dialog (is in emulator?)", e);
			}

			break;
		}	

		case DownloadBinder.ERROR_UNKNOWN :
		default :
			Log.e(LOG_TAG, "handleBinderMessage: unknown error");
			if (mDownloadBinder != null) {
				mDownloadBinder.clearErrors();
			}
			break;
		}
		
	}

	/**
	 * Service connection that grabs the binder
	 */
	private ServiceConnection mDownloadServiceConn = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
			mDownloadBinder = (DownloadBinder) serviceBinder;
			onBindToService();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mDownloadBinder = null;
		}
	};

	/**
	 * The receiver that listens for internet when it has been lost and has been
	 * registered in isNetworkOnline().
	 */
	protected BroadcastReceiver mReceiverInternet = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			// tests for no connectivity, so false means there is connection
			if (!intent.getBooleanExtra(
					ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
				Log.d(LOG_TAG,
						"BroadcastReceiver.onReceive: gained internet, stop receiving");
				// unregister this
				unregisterReceiver(this);

				mIsWaitingForInternet = false;
				onGainInternet();
			}
		}
	};

	/**
	 * Receiver that listens for updates from the data download service/binder
	 */
	private BroadcastReceiver mDataReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			// lets the caller know this activity is here by answering the call
			if (isOrderedBroadcast()) {
				setResultCode(RESULT_OK);
			}

			// check for any messages to handle
			handleBroadcastParams(intent);
		}
	};

	/**
	 * Checks for online, doesn't register for broadcast though, just feeds back to the user
	 */
	 static boolean checkOnlineFeedbackIfNot(Context context) {

		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			Log.d(LOG_TAG, "checkOnlineFeedbackIfNot: have internet");
			return true;
		}
		else {
			Log.d(LOG_TAG, "checkOnlineFeedbackIfNot: don't have internet, feedback to user");
			Toast.makeText(context, context.getResources().getString(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
			return false;
		}
	}

	/**
	 * Utility method that checks for online and registers to receive broadcasts
	 * if not
	 * 
	 * @return
	 */
	public boolean isNetworkOnlineOtherwiseRegisterForBroadcast() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {

			// make sure if was waiting for internet, that the receiver is
			// stopped
			// normally it wouldn't go this way because the receiver would get
			// the
			// notification and switch off the flag, but if the user presses an
			// action, eg. menu item that needs internet, this method would be
			// called
			// and maybe it got here before the broadcast receiver got the
			// message
			// so, unregister and call the online gained callback
			if (mIsWaitingForInternet) {
				Log.d(LOG_TAG,
						"isNetworkOnline: got internet, stop registering for broadcasts");
				unregisterReceiver(mReceiverInternet);
				onGainInternet();
			}
			return true;
		}

		// probably not already waiting, but check before registering receiver
		// twice
		if (!mIsWaitingForInternet) {
			Log.d(LOG_TAG,
					"isNetworkOnline: no internet, registering for broadcasts");
			registerReceiver(mReceiverInternet, new IntentFilter(
					INTERNET_CONNECT_ACTION));
			mIsWaitingForInternet = true;
		}

		return false;
	}

	/**
	 * If there's no account for potlatch on the device, go to welcome screen,
	 * there's a button to set up the login. On first startup, it needs to 
	 * verify login *before* creating the tabs, as it could be an admin user.
	 * So, pass the callback to create the tabs when auth complete.
	 * 
	 * @param onSuccessfulLoginCallback
	 * @return true if the user is signed in
	 */
	protected boolean testForLoginRedirect(Runnable onSuccessfulLoginCallback) {

		// if there's a current user set up, then nothing more to do
		PotlatchApplication app = (PotlatchApplication) getApplication();
		if (app.isUserSignedIn()) {
			Log.d(LOG_TAG, "testForLogin: got user=" + app.getCurrentUser()
					+ " first sign in=" + app.isUserReset());

			if (app.isUserReset()) {
				// reset the user because it's first sign in
				mInitUserReset = true;
			}

			return true;
		}

		// all processing should proceed as for reset user, since they're not
		// logged in yet
		mInitUserReset = true;

		AccountManager am = AccountManager.get(app);
		Account[] accounts = am.getAccountsByType(PotlatchAccountConstants.ACCOUNT_TYPE);

		// no accounts, treat it like first ever login
		if (accounts.length == 0) {
			Log.d(LOG_TAG, "redirecting to welcome");
			Intent i = new Intent(this, LoginActivity.class);
			startActivity(i);

			// don't allow a back press to navigate here without an account
			finish();
			return false;
		} 
		else {
			Account useAcc = app.getLastUsedAccountIfStillGood(am, accounts);
			if (useAcc != null) {
				getToken(am, useAcc, onSuccessfulLoginCallback);
				return false;
				
			} else if (accounts.length == 1) {
				// not able to find the last used account
				// if there's only one account use it, otherwise have to prompt
				// the user to choose one
				Log.d(LOG_TAG, "last used account isn't around anymore, but can still use: "
								+ accounts[0].name
								+ " @ "
								+ am.getUserData(accounts[0], PotlatchAccountConstants.ACCOUNT_SERVER_KEY));
				
				getToken(am, accounts[0], onSuccessfulLoginCallback);
				return false;
			} else {
				promptUserForLogin(false);
				return false;
			}
		}
	}

	/**
	 * Called from anywhere that attempts to access the server with the auth
	 * token and gets a 401 unauthorized response
	 * 
	 * @param user
	 */
	public void userAuthTokenIsStale(Runnable onSuccessAddedCallback) {
		// if there's a current user set up, then nothing more to do
		PotlatchApplication app = (PotlatchApplication) getApplication();
		User user = app.getCurrentUser();
		
		if (user != null) {
			String staleToken = user.getAuthToken();
			
			if (staleToken == null) {
				Log.w(LOG_TAG, "userAuthTokenIsStale: auth token is already null, has this been called already?");
				return;
			}
			
			user.setAuthToken(null);
	
			Account account = app.findAccountForUser(user);
			AccountManager am = app.getAccountManager();
	
			if (account != null) {
				// invalidate it, account manager will cause authenticator to
				// attempt to login
				// again for a new auth token, if that fails, it will present the
				// sign in screen
				// for the user to re-enter the password for this user
				Log.d(LOG_TAG, "User account is stale, telling account manager, onSuccessCallback="+onSuccessAddedCallback);
				am.invalidateAuthToken(PotlatchAccountConstants.ACCOUNT_TYPE, staleToken);
				
				// try to get the token again
				getToken(am, account, onSuccessAddedCallback);
				
				return;
			} 
		}

		// either account is removed, or there's no current user in the app
		Log.w(LOG_TAG, "User account is not available, clear it and prompt for new login");
		app.currentUserRemoved();
		promptUserForLogin(false); // don't let them back here without it
	}

	protected void promptUserForLogin(boolean canReturn) {
		Log.d(LOG_TAG, "redirecting to login for user to choose");
		
		if (!canReturn) {
			// sign out first, no going back
			((PotlatchApplication)getApplication()).signUserOut();
		}
		
		Intent i = new Intent(this, LoginActivity.class);
		i.putExtra(PotlatchAccountConstants.PROMPT_FOR_LOGIN, true);
		startActivity(i);

		// don't allow a back press to navigate here without an account
		if (!canReturn) {
			finish();
		}
	}

	/**
	 * Called from chooseLogin when the user has pressed the login to an
	 * existing user button, and from GiftsActivity when first entering and not
	 * yet logged in but after evaluating there is a usable account. Gets the
	 * auth token from the account manager, on successful completion the user is
	 * saved to the application as 'signed in', and the user is redirected to
	 * GiftsActivity only if currently in the login activity (see
	 * getAccountManagerCallback() below)
	 * 
	 * When special handling is required on success, the optional added callback
	 * should be used. Note, this is not used when the user is at the login
	 * screen, rather for extraneous events such as authorization failed
	 * 
	 * @param am
	 * @param account
	 * @param onSuccessAddedCallback
	 */
	protected void getToken(AccountManager am, Account account, Runnable onSuccessAddedCallback) {
		
		Log.d(LOG_TAG, "getToken: called, callback="+onSuccessAddedCallback);
		
		setButtons(false);

		String server = am.getUserData(account,
				PotlatchAccountConstants.ACCOUNT_SERVER_KEY);

		am.getAuthToken(account,
				PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH, null, this,
				getAccountManagerCallback(server, onSuccessAddedCallback),
				new Handler());
	}

	/**
	 * Same callback is used whether user calls set up new or login to existing
	 * 
	 * @return
	 */
	protected AccountManagerCallback<Bundle> getAccountManagerCallback(
			final String server, final Runnable onSuccessAddedCallback) {
		return new AccountManagerCallback<Bundle>() {

			@Override
			public void run(AccountManagerFuture<Bundle> future) {
				try {
					Bundle b = future.getResult();
					String authServer = b
							.getString(PotlatchAccountConstants.ACCOUNT_SERVER_KEY);
					if (authServer == null) {
						authServer = server; // should only happen because
												// called by
												// getAuthToken() is called and
												// didn't need to
												// get the server key because
												// Account Manager
												// was satisfied it had all it
												// needed
						b.putString(
								PotlatchAccountConstants.ACCOUNT_SERVER_KEY, authServer);
					}

					// not sure if it's a bug or what, but Account Manager seems to
					// consume KEY_AUTHTOKEN for a new account but not when it's a get
					// auth token call, hence need to keep own current auth token,
					// put the auth token key on as the current selected token
					if (b.getString(PotlatchAccountConstants.CURRENT_AUTHTOKEN_KEY) == null) {
						b.putString(
								PotlatchAccountConstants.CURRENT_AUTHTOKEN_KEY,
								b.getString(AccountManager.KEY_AUTHTOKEN));
					}

					Log.d(LOG_TAG,
							"sign-in : NAME="
									+ b.getString(AccountManager.KEY_ACCOUNT_NAME)
									+ " AUTHTOKEN="
									+ !TextUtils.isEmpty(b.getString(AccountManager.KEY_AUTHTOKEN))
									+ " CURRENT_AUTHTOKEN="
									+ !TextUtils.isEmpty(b.getString(
											PotlatchAccountConstants.CURRENT_AUTHTOKEN_KEY))
									+ " SERVER=" + authServer);

					// the valid user can now be setup in the application
					((PotlatchApplication) getApplication())
							.setCurrentUser(
									b.getString(AccountManager.KEY_ACCOUNT_NAME),
									authServer,
									b.getString(PotlatchAccountConstants.CURRENT_AUTHTOKEN_KEY));

					// current activity is login activity, go to Gifts otherwise done
					// go to the main gifts activity
					if (AbstractPotlatchActivity.this instanceof LoginActivity) {
						Intent i = new Intent(AbstractPotlatchActivity.this, GiftsActivity.class);
						i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
								| Intent.FLAG_ACTIVITY_NEW_TASK);
						
						// because the search needs singleTop, the previous flags don't cause it to
						// restart, which means that onNewIntent() will fire instead, so this
						// extra tells the activity it's a new login and it should reset stuff
						i.putExtra(GiftsActivity.LOGGED_IN_NEW_USER, true);
						
						startActivity(i);
						// don't allow a back press to navigate here once logged
						// in
						finish();
					} 
					else {
						setButtons(true);

						if (onSuccessAddedCallback != null) {
							onSuccessAddedCallback.run();
						}
					}
				} 
				catch (OperationCanceledException e) {
					// do nothing, user probably cancelled, can retry
					// if already logged in and cancelled choosing another user
					// it's ok
					// they remain logged in
					Log.w(LOG_TAG, "operation cancelled");
					// reset the buttons so can try again (won't matter if
					// succeeded)
					setButtons(true);
				}
				catch (AuthenticatorException | IOException e) {
					Log.e(LOG_TAG, "got error", e);
					Toast.makeText(
							getBaseContext(),
							getResources().getString(
									R.string.setup_problem_toast,
									e.getLocalizedMessage()),
							Toast.LENGTH_SHORT).show();
					// reset the buttons so can try again (won't matter if succeeded)
					setButtons(true);
					// failure means the current user is not signed in, even if
					// they were before
					PotlatchApplication app = (PotlatchApplication) getApplication();
					if (app.isUserSignedIn()) {
						User currentUser = app.getCurrentUser();
						currentUser.setAuthToken(null);
						
						Log.d(LOG_TAG, "auth error means have to invalidate user: "
										+ currentUser);
					}
				}

			}
		};
	}

	/**
	 * Convenience method to allow the activity to disable or otherwise
	 * restrict/change the ui while getting the token
	 * 
	 * @param enabled
	 */
	protected void setButtons(boolean enabled) {
	}

	@Override
	protected void onStart() {
		super.onStart();

		// attempt to bind to the download service
		Intent srvIntent = DataDownloadService.makeIntent(getApplication());

		// start it first to make sure it stays around, it's a hybrid service
		startService(srvIntent);
		if (!bindService(srvIntent, mDownloadServiceConn, 0)) {
			Log.e(LOG_TAG, "onStart: unable to connect to service");
		}

		// start listening for data
		registerDataReceiver();
	}

	@Override
	protected void onStop() {
		super.onStop();

		// it went further than a pause, so next time onResume() is called it knows that
		mWillNeedDataRefresh = false;
		Log.d(LOG_TAG, "onStop: called");
		
		// stop listening for broadcasts
		unregisterDataReceiver();

		// stop handler messages in the pipeline and disconnect from service
		((PotlatchApplication) getApplication()).getBinderHandler().removeCallbacksAndMessages(null);
		unbindService(mDownloadServiceConn);
	}

	/**
	 * Sub-classes can override to add, so call this first
	 */
	protected void onBindToService() {
		
		// sub-classes may not always want to bind to get messages, see comments on bindToHandler()
		if (bindToHandler()) {
			((PotlatchApplication) getApplication()).getBinderHandler().setRecipient(this);
		}
		
		try {
			// test for being opened by the notification tray item, which means
			// need to remove the usernotice
			if (getIntent().getBooleanExtra(DownloadBinder.CLEAR_NOTICES, false)) {
				Log.d(LOG_TAG, "onBindToService: have internet, opened from notification, clear notices");
					mDownloadBinder.clearUserNotice();
			}
			else {
				UserNotice un = mDownloadBinder.getUserNotice(false);
				if (un != null && !un.isEmpty()) {
					enableUserNoticeActionItem(false); // not just broadcast now
				}
			}		
		}
		catch (AuthTokenStaleException e) {
			// abort the current task, handleBinderMessages() will deal with it asap
		}
	}

	/**
	 * By default the activity will bind to the handler to receive messages, but that may not always
	 * be desired... eg. if there's a dialog open and there's a config change, so allow override
	 * by sub-classes
	 * 
	 * @return
	 */
	protected boolean bindToHandler() {
		return true;
	}

	/**
	 * Test for internet (which registers receiver if needed)
	 */
	@Override
	protected void onResume() {
		super.onResume();
		
		// test for connection, will register for broadcasts if not
		isNetworkOnlineOtherwiseRegisterForBroadcast();
		
		// make sure do get messages as they occur
		BinderHandler binderHandler = ((PotlatchApplication) getApplication()).getBinderHandler();
		binderHandler.setProcessMessagesWhenReceived(true);
		
		// handle anything outstanding
		if (binderHandler.getWhatHappenedWhileAway() != PotlatchApplication.BinderHandler.NOTHING_HAPPENED) {
			Log.w(LOG_TAG, "onResume: got a message to handle");
			handleBinderMessage(binderHandler.getWhatHappenedWhileAway(), false, binderHandler.getHappenedToGiftsSectionType());
			binderHandler.resetWhatHappenedWhileAway(); 
		}
	}

	/**
	 * Stop receiving broadcasts
	 */
	@Override
	protected void onPause() {
		super.onPause();

		// don't keep waiting for broadcasts
		if (mIsWaitingForInternet) {
			Log.d(LOG_TAG, "onPause: stop registering for broadcasts");
			mIsWaitingForInternet = false;
			unregisterReceiver(mReceiverInternet);
		}
		
		((PotlatchApplication) getApplication()).getBinderHandler().setProcessMessagesWhenReceived(false);

		// set flag that is cleared after a data refresh so we know when onResume() runs whether it needs
		// to do anything that onStart() didn't do
		mWillNeedDataRefresh = true;
		Log.d(LOG_TAG, "onPause: called");
	}

	/**
	 * Sub-classes can do something with this
	 */
	protected void onGainInternet() {
	}

	/**
	 * Communication protocol that allows the data service to know there is
	 * something to receive messages that returns RESULT_OK when it's up.
	 */
	protected void registerDataReceiver() {
		IntentFilter filter = new IntentFilter(DATA_RECEIVER_ACTION);
		registerReceiver(mDataReceiver, filter);
	}

	protected void unregisterDataReceiver() {
		unregisterReceiver(mDataReceiver);
	}

	/**
	 * Subclass to handle params from broadcasts from the DataReceiver
	 * 
	 * @param intent
	 */
	protected void handleBroadcastParams(Intent intent) {		
	}

	/**
	 * Called when handleBroadcastParams() gets the user notice and is
	 * about to display a dialog. Also to get the text for the notification.
	 * Interpret the notice and build a list of strings
	 * @param res 
	 * @param userNotice
	 * @return
	 */
	public static ArrayList<String> assembleUserNoticeMessages(Resources res, UserNotice userNotice) {

		ArrayList<String> list = new ArrayList<String>();
		
		if (userNotice.getTouchCount() != 0 || userNotice.getUnTouchCount() != 0) {
			list.add(userNotice.getTouchCount() < userNotice.getUnTouchCount()
					? res.getString(R.string.user_notice_touches_down)
					: res.getString(R.string.user_notice_touches_up));
		}
		
		if (userNotice.getInappropriateCount() != 0 || userNotice.getUnInappropriateCount() != 0
			|| userNotice.getObsceneCount() != 0 || userNotice.getUnObsceneCount() != 0) {
			list.add(res.getString(R.string.user_notice_flagged));
		}
		
		if (userNotice.getRemovedCount() != 0) {
			list.add(res.getString(R.string.user_notice_removed, userNotice.getRemovedCount()));
		}

		return list;
	}

}
