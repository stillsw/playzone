package com.courseracapstone.android;

import java.lang.ref.WeakReference;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.courseracapstone.android.accountsetup.PotlatchAccountConstants;
import com.courseracapstone.android.accountsetup.User;
import com.courseracapstone.android.service.DownloadBinder.DownloadBinderRecipient;
import com.courseracapstone.common.GiftsSectionType;

/**
 * Keeps the current user for all places where that would be needed,
 * ContentProviders/Services/Activities
 * 
 * @author xxx xxx
 *
 */
public class PotlatchApplication extends Application {

	private static final String LOG_TAG = "Potlatch-"+PotlatchApplication.class.getSimpleName();
	private User mCurrentUser;
	// after a new user login (anytime), this flag is set and the GiftsActivity
	// will init a new data refresh (user and gifts)
	private boolean mIsUserReset = true;
	// handler to pass to the binder to receive messages
	protected BinderHandler mBinderHandler;

	public User getCurrentUser() {
		return mCurrentUser;
	}
	
	/**
	 * When the current user account is no longer found on the device
	 */
	public void currentUserRemoved() {
		mCurrentUser = null;
	}
	
	public boolean isUserSignedIn() {
		return mCurrentUser != null && !TextUtils.isEmpty(mCurrentUser.getAuthToken());
	}
	
	/**
	 * Make sure once signed in and got data that this value is reset 
	 * @return
	 */
	public boolean isUserReset() {
		return mIsUserReset;
	}

	public void setUserReset(boolean isUserReset) {
		this.mIsUserReset = isUserReset;
	}
	
	public void signUserOut() {
		setUserReset(true);
		mCurrentUser = null;
	}

	public AccountManager getAccountManager() {
		return AccountManager.get(this);
	}
	
	public Account[] getAppAccounts(AccountManager am) {
		return am.getAccountsByType(PotlatchAccountConstants.ACCOUNT_TYPE);
	}
	
	public Account findAccountForUser(User user) {
		AccountManager am = getAccountManager();
		return findAccountForUser(am, getAppAccounts(am), user.getUsername(), user.getServer());
	}
	
	/**
	 * Overloaded to just use name/server
	 * @param am
	 * @param accounts
	 * @param username
	 * @param server
	 * @return
	 */
	public Account findAccountForUser(AccountManager am, Account[] accounts,
			String username, String server) {
		
		for (Account a : accounts) {
			if (a.name.equals(username)
					&& am.getUserData(a,
							PotlatchAccountConstants.ACCOUNT_SERVER_KEY)
							.equals(server)) {
				return a;
			}
		}
		// should not happen
		return null;
	}

	/**
	 * Overloaded version that doesn't need accounts
	 * @param username
	 * @param server
	 * @return
	 */
	public Account findAccountForUser(AccountManager am, String username, String server) {
		return findAccountForUser(am, getAppAccounts(am), username, server);
	}
	
	/**
	 * Stores the user in this process, but also in shared prefs for next time
	 * (note only the name and server are stored in prefs, the other details 
	 * come from Account Manager)
	 * @param username
	 * @param server
	 * @param authToken
	 */
	public void setCurrentUser(String username, String server, String authToken) {
		// get the account from the manager for the details
		AccountManager am = getAccountManager();
		Account ac = findAccountForUser(am, username, server);
		String adminData = am.getUserData(ac, PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY);
		boolean isAdmin = adminData != null && adminData.equals("true");

		if (mCurrentUser == null) {
			mCurrentUser = new User(username, server, authToken, isAdmin);
		}
		else {
			mCurrentUser.setUser(username, server, authToken, isAdmin);
		}
		
		Log.d(LOG_TAG, "setCurrentUser: "+mCurrentUser);
		
		// cause data refreshes (see GiftsActivity)
		mIsUserReset = true;
		
		// and clear last binder messages 
//		mBinderHandler.removeCallbacksAndMessages(null);
//		mBinderHandler.resetWhatHappenedWhileAway();
		
		// store the values for next time
		SharedPreferences prefs = getSharedPreferences(PotlatchAccountConstants.SHARED_PREFS_KEY, Context.MODE_PRIVATE);
		Editor editor = prefs.edit();
		editor.putString(PotlatchAccountConstants.USERNAME_PREF_KEY, username);
		editor.putString(PotlatchAccountConstants.SERVER_PREF_KEY, server);
		editor.commit();
	}

	/**
	 * Looks up the user that was last used to see if it still works
	 * @param am
	 * @param accountsByType
	 * @return
	 */
	public Account getLastUsedAccountIfStillGood(final AccountManager am, Account[] accountsByType) {
		// failing that see what account was last used, and see if it's still on the 
		// device
		SharedPreferences prefs = getSharedPreferences(PotlatchAccountConstants.SHARED_PREFS_KEY, Context.MODE_PRIVATE);
		String username = prefs.getString(PotlatchAccountConstants.USERNAME_PREF_KEY, null);
		String server = prefs.getString(PotlatchAccountConstants.SERVER_PREF_KEY, null);
		
		if (username == null || server == null) {
			Log.d(LOG_TAG, "no previous login found");			
			return null;
		}
		
		for (Account a : accountsByType) {
			if (a.name.equals(username) &&  
				am.getUserData(a, PotlatchAccountConstants.ACCOUNT_SERVER_KEY).equals(server)) {
				boolean isAdmin = am.getUserData(a, PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY).equals("true");
				Log.d(LOG_TAG, "last login username="+username+" isAdmin="+isAdmin
						+", server="+server+" is still functional");
				return a;
			}
		}
		
		Log.d(LOG_TAG, "Last login is stale");
		return null;
	}

	/**
	 * Lazily init a binder handler (should be in the main thread)
	 * @return
	 */
	public BinderHandler getBinderHandler() {
		if (mBinderHandler == null) {
			mBinderHandler = new BinderHandler();
		}
		return mBinderHandler;
	}

	// the handler to pass to the binder
	public static class BinderHandler extends Handler {
		
		static final int NOTHING_HAPPENED = -1;
		
		private WeakReference<DownloadBinderRecipient> mRecipient;
		// when the activity is paused/stopped don't process messages
		// instead store the last one, and then react onResume()
		private boolean mProcessMessagesWhenReceived = true;
		private int mWhatHappenedWhileAway = NOTHING_HAPPENED;
		private GiftsSectionType mHappenedToGiftsSectionType;

		public void setRecipient(DownloadBinderRecipient recipient) {
			Log.d(LOG_TAG, "BinderHandler.setRecipient: registering="+recipient.getClass().getSimpleName());
			mRecipient = new WeakReference<DownloadBinderRecipient>(recipient);
		}

		@Override
		public void handleMessage(Message msg) {
			
			DownloadBinderRecipient recipient = mRecipient.get();

			if (mProcessMessagesWhenReceived) {
				mWhatHappenedWhileAway = NOTHING_HAPPENED;
				mHappenedToGiftsSectionType = null;
				
				Log.d(LOG_TAG, "BinderHandler.handleMessage: got message what="+msg.what+" for="+msg.obj);

				if (recipient != null) {
					try {
						GiftsSectionType giftsSectionType = (GiftsSectionType) msg.obj;
						recipient.handleBinderMessage(msg.what, true, giftsSectionType);
					} 
					catch (ClassCastException e) {
						Log.e(LOG_TAG, "handleMessage: what="+msg.what+" msg.obj is not a gifts section type : "+msg.obj);
					}
				}
				else {
					Log.w(LOG_TAG, "no recipient to handle the message... should not see this");
				}
			}
			else {
				// store the what for when onResume() runs
				mWhatHappenedWhileAway = msg.what;
				if (msg.obj != null) {
					try {
						mHappenedToGiftsSectionType = (GiftsSectionType) msg.obj;
					} 
					catch (ClassCastException e) {
						Log.e(LOG_TAG, "handleMessage: what="+msg.what+" msg.obj is not a gifts section type : "+msg.obj);
					}
				}
				Log.d(LOG_TAG, "BinderHandler.handleMessage: got message but not processing it now, what="+msg.what);
			}
		}

		public boolean isProcessMessagesWhenReceived() {
			return mProcessMessagesWhenReceived;
		}

		public void setProcessMessagesWhenReceived(boolean processMessagesWhenReceived) {
			Log.d(LOG_TAG, "BinderHandler.setProcessMessagesWhenReceived="+processMessagesWhenReceived);
			this.mProcessMessagesWhenReceived = processMessagesWhenReceived;
		}

		public int getWhatHappenedWhileAway() {
			return mWhatHappenedWhileAway;
		}

		public GiftsSectionType getHappenedToGiftsSectionType() {
			return mHappenedToGiftsSectionType;
		}

		public void resetWhatHappenedWhileAway() {
			this.mWhatHappenedWhileAway = NOTHING_HAPPENED;
			mHappenedToGiftsSectionType = null;
		}
		
		
	}


}
