package com.courseracapstone.android.service;

import retrofit.RetrofitError;
import android.util.Log;

import com.courseracapstone.android.AbstractPotlatchActivity;
import com.courseracapstone.android.model.PotlatchSvcApi;
import com.courseracapstone.android.model.UserNotice;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.common.PotlatchConstants;

/**
 * Handles the interactions with the server for user notices on behalf of the binder
 * which is the only class that knows about this delegate.
 * The main purpose is to keep that class manageable, as it has several of these.
 * 
 * @author xxx xxx
 *
 */
public class UserNoticesBinderDelegate {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	// how long prepared to wait in the background thread for the broadcast to be received
	// by the activity... try long timeout because it's a background thread anyway
	protected static final int AUTO_UPDATE_BROADCAST_TIMEOUT = 1000; //ms
	// running in the foreground thread (not the ui thread), so also ok to wait a bit
	// and also it's after the gifts data download, so no delays to user
	protected static final int RESET_USER_BROADCAST_TIMEOUT = 500; //ms

	private DownloadBinder mDownloadBinder;
	private DataDownloadService mService;
	
	// read from the server, and left here until the activity says it's shown it to the user
	private UserNotice mUserNotice;

	UserNoticesBinderDelegate(DownloadBinder downloadBinder, DataDownloadService service) {
		mDownloadBinder = downloadBinder;
		mService = service;
	}

	void setUserNotice(UserNotice userNotice) {
		this.mUserNotice = userNotice;
	}

	/**
	 * Happens in the auto-updates thread, so already locked.
	 * Returns true if the call to handleUserNotice() results in a response from the activity
	 * 
	 * @param noticesSvcApi
	 */
	boolean downloadUserNotice(PotlatchSvcApi noticesSvcApi) {
		try {
			// should already be set to no errors
			if (mDownloadBinder.getCurrentError() != DownloadBinder.NO_ERRORS) {
				Log.w(LOG_TAG, "downloadUserNotice: running when there's a current error, could mask it if another happens");
			}

			mUserNotice = noticesSvcApi.getNotices();
			return handleUserNotice(AUTO_UPDATE_BROADCAST_TIMEOUT);
		} 
		catch (RetrofitError e) {
			// since this runs in background, it's possible the server has gone down and not been noticed
			// inform the ui if it's listening it can restart
			try {
				if (mDownloadBinder.processServerError(e, false) == DownloadBinder.ERROR_AUTH_TOKEN_STALE) {
					Log.w(LOG_TAG, "downloadUserNotice: send message to handler that token is stale");
					mDownloadBinder.sendMessage(DownloadBinder.ERROR_AUTH_TOKEN_STALE, null);
				}
			} 
			catch (AuthTokenStaleException e1) {} // false means don't throw it
			
			return false;
		}
	}

	/**
	 * Called when the downloadUserNotice().run() completes
	 * NOTE: this is in its own thread 
	 * Returns true if the call to handleUserNotice() results in a response from the activity
	 * @param timeout 
	 */
	boolean handleUserNotice(int timeout) {
		
		// send an ordered broadcast, and if get result ok, assume the activity will handle it
		// with a call to getUserNotice() when it wants it
		boolean isActivityResponding = mService.sendBroadcastTestUiPresence(DownloadBinder.USER_DATA_READY, true, timeout);
		if (!isActivityResponding) {
			Log.d(LOG_TAG, "refreshUser.broadcastreceiver.onReceive: activity didn't respond");
			// put up a notification
			if (mUserNotice != null && !mUserNotice.isEmpty()) {
				mService.sendNotification(AbstractPotlatchActivity.assembleUserNoticeMessages(
					mService.getResources(), mUserNotice));
			}
		}
		
		return isActivityResponding;
	}

	/**
	 * Called by the activity when it receives the broadcast to grab the user data
	 */
	public UserNotice getUserNotice() {
		return mUserNotice;
	}

	/**
	 * Called during getUserNotice() when passed to clear it, also when
	 * the user presses the notification action showing the message.
	 */
	void clearUserNoticeLockForegroundTasks(final PotlatchSvcApi apiInstance) {

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
					
					try {
						Log.d(LOG_TAG, "clearUserNoticeLockForegroundTasks: remove on server");
						mUserNotice = null;
						apiInstance.removeNotices();
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
					catch (Exception e) {
						Log.e(LOG_TAG, "Unexpected error: removing notices", e);
					}					
				}
				finally {
					mDownloadBinder.signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * This is run by the binder during creation, and just after creating this class.
	 * It's a continuous thread that handles updates
	 * @return
	 */
	ContinuousRunner makeContinuousRunningUpdater() {
		return new ContinuousRunner(mDownloadBinder) {
			
			@Override
			protected void doRepetitiveTask() throws InterruptedException {
				
				// acquire a lock on the switching user lock
				mDownloadBinder.mUserSwitchingLock.lock();
				
				// tests that are needed for both updates
				boolean didActivityRespondToNotice = false, isNetworkOnline = false;
				
				try {
					while (mDownloadBinder.mIsUserBeingReset) {
						mDownloadBinder.mNotSwitchingUser.awaitUninterruptibly();
					}
					
					isNetworkOnline = mService.isNetworkOnline();
					if (isNetworkOnline && mDownloadBinder.mHaveUserPrefs) {
						
						Log.d(LOG_TAG, "ContinuousRunner: scheduled UserNotice download");
						didActivityRespondToNotice = downloadUserNotice(mDownloadBinder.mNoticesSvcApi);
					}
					else {
						Log.d(LOG_TAG, "ContinuousRunner: scheduled updates abort either no network or something needed");
					}
					
					mDownloadBinder.mNotSwitchingUser.signalAll();
				}
				finally {
					mDownloadBinder.mUserSwitchingLock.unlock();
				}
				
				// default value that suggests to sleep for the max amount of time if the following tests
				// don't result in a run to the refreshGifts method
				long latestRefreshTime = System.currentTimeMillis();
				
				// try for a gifts refresh too (it will lock the foreground tasks)
				// don't bother if no network, no api instance, or no ui
				if (didActivityRespondToNotice && isNetworkOnline) {
					latestRefreshTime = mDownloadBinder.mGiftsDelegate.refreshGiftsFromBackgroundTask(mDownloadBinder.mNoticesSvcApi);
				}
				
				// allow for the (hopefully not likely) event that don't have an update interval yet
				long sleepTime = mDownloadBinder.mUpdateInterval == 0 
						? PotlatchConstants.DEFAULT_UPDATE_INVERVAL : mDownloadBinder.mUpdateInterval;
				
				// however if there's been a refresh more recent than the interval this thread might not have
				// done a gifts refresh
				// adjustment is the interval minus (now minus last refresh time)
				// eg. interval = 100, now = 200, last refresh = 50
				//    adjust = 100 - (200 - 50) = -50
				//    result = 100 + -50 = sleep for 50
				long sleepAdjust = sleepTime - (System.currentTimeMillis() - latestRefreshTime);
				if (sleepAdjust <= 0L) {
					sleepTime += sleepAdjust;
				}
				else {
					sleepTime = sleepAdjust;
				}
				
				Log.d(LOG_TAG, String.format("auto-interval updates sleep=%s, adjust=%s", sleepTime, sleepAdjust));
				Thread.sleep(sleepTime);
			}
			
		};

	}


}
