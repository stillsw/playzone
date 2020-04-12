package com.courseracapstone.android.service;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpStatus;

import retrofit.RetrofitError;
import retrofit.client.ApacheClient;
import retrofit.client.Response;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.courseracapstone.android.PotlatchApplication;
import com.courseracapstone.android.R;
import com.courseracapstone.android.accountsetup.User;
import com.courseracapstone.android.https.AuthTokenSecuredRestBuilder;
import com.courseracapstone.android.https.EasyHttpClient;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.PotlatchSvcApi;
import com.courseracapstone.android.model.PotlatchUser;
import com.courseracapstone.android.model.SearchCriteria;
import com.courseracapstone.android.model.UserNotice;
import com.courseracapstone.android.service.ImageDownloadBinderDelegate.ImageFailedInDownloadException;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;
import com.courseracapstone.android.utils.Utils;
import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PotlatchConstants;

/**
 * The binder that provides the methods to download all the data from the server,
 * created by DataDownloadService and passed to all activities that bind.
 * 
 * 3 things this binder does (through its delegates):
 * 
 * 1) Periodically gets an update from the server, based on the user's settings, which
 *    are read from the server when the user first logs in.
 * 
 * 2) Provide a threaded download of images (thumbnails automatically instantiated
 *    when gifts data is received, and full images by request from the views that 
 *    display them)
 * 
 * 3) Download all the other data
 * 
 * 
 * @author xxx xxx
 *
 */
public class DownloadBinder extends Binder {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	// key to params for broadcast receiver
	public static final String USER_DATA_READY = "potlatch.userdata.ready";
	public static final String TEST_FOR_AWAKE = "potlatch.testawake";
	public static final String TIME_SENT = "potlatch.broadcast.time";

	// key for intent param to clear user notices
	public static final String CLEAR_NOTICES = "potlatch.userdata.clear";
		
	// keys for messages for binder handler
	public static final int ERROR_AUTH_TOKEN_STALE = HttpStatus.SC_UNAUTHORIZED;
	public static final int ERROR_NOT_FOUND = HttpStatus.SC_NOT_FOUND;
	public static final int ERROR_BAD_REQUEST = HttpStatus.SC_BAD_REQUEST;
	
	public static final int NO_ERRORS = 100000;
	public static final int ERROR_NETWORK = 100001;
	public static final int ERROR_UNKNOWN = 100002;
	public static final int USER_PREFS_RECEIVED = 100003;
	public static final int NEW_GIFTS_DATA_READY = 100004;
	public static final int MORE_SAME_TYPE_GIFTS_DATA_READY = 100005;
	public static final int FRESH_GIFT_READY = 100006;
	public static final int SCALED_INTERIM_IMAGE_READY = 100007;
	public static final int FULL_IMAGE_READY = 100008;
	public static final int SCALED_FULL_IMAGE_READY = 100009;
	public static final int FULL_IMAGE_NOT_AVAILABLE = 100010;
	public static final int GIFT_REMOVED = 100011;
	public static final int GIFT_ADDED = 100012;
	public static final int GIFT_ADD_FAILED = 100013;
	public static final int GIFT_ADD_IMAGE_FAILED = 100014;
	public static final int NEW_TOP_GIVERS_DATA_READY = 100015;
	public static final int MORE_TOP_GIVERS_DATA_READY = 100016;
	public static final int USER_PREFS_UPDATED = 100017;

	// keep a thread pool executor for server async calls
	private static final int MAX_THREADS = 12;
	// see below for discussion on why
	protected static final int NUM_API_INSTANCES = MAX_THREADS - 4;
	// leaves 6 threads for downloading images
	protected static final int NUM_API_INSTANCES_FOR_IMAGES = NUM_API_INSTANCES - 2;

	@SuppressWarnings("serial")
	public class AuthTokenStaleException extends Exception {

		public AuthTokenStaleException() {
			Log.e(LOG_TAG, "AuthTokenStaleException thrown, setting flag to prevent further calls");
			mIsAuthTokenStale = true;
		}
	}

	/**
	 * Anything that wants to receive the binder handler messages implements this
	 * interface and gets registered in PotlatchApplication. Typically that's just the
	 * Activities, and they register during onServiceConnected. Another place is when
	 * the upload button is pressed in the new gift dialog, then it registers to get
	 * the messages, as it needs to know of success/failure.
	 */
	public static interface DownloadBinderRecipient {
		public void handleBinderMessage(int what, boolean onTime, GiftsSectionType giftsSectionType);		
	}
	
    ExecutorService mExecutor;
	private DataDownloadService mService;

	// delegate classes that handle each entity
	private UserNoticesBinderDelegate mUserNoticesDelegate;
	GiftsBinderDelegate mGiftsDelegate;
	ImageDownloadBinderDelegate mImageDownloadDelegate;
	private TopGiversBinderDelegate mTopGiversBinderDelegate;
	
	// user prefs
//	private volatile Boolean mIsUserFilterContent; // not used by binder, only for ui
	protected volatile long mUpdateInterval;

	// the retrofit apis, one less than the number of threads, just a precaution so that
	// there will always be enough threads, so a job can grab a thread and then wait on
	// an api instance if necessary, not the other way around
	private ArrayList<ApiInstanceWrapper> mPotlatchSvcUsers = new ArrayList<ApiInstanceWrapper>();
	// two special cases which keep hold of their instances so never have to be blocked
	// if a bunch of images are downloading at once
	PotlatchSvcApi mNoticesSvcApi;
	PotlatchSvcApi mForegroundSvcApi;
	
	// a couple of locks and conditions for each of those, for the times when a changeover
	// of users is happening and so the old is no good and (especially the notices) has
	// to stop and wait for a new instance to be created
	ReentrantLock mUserSwitchingLock = new ReentrantLock();
	Condition mNotSwitchingUser = mUserSwitchingLock.newCondition();
	ReentrantLock mForegroundTaskLock = new ReentrantLock();
	Condition mNotRunningForeground = mForegroundTaskLock.newCondition();
	// each time an api instance is acquired it grabs a permit, protect that with another lock/condition
	private ReentrantLock mImageSemaLock = new ReentrantLock();
	private Condition mPermitsLocked = mImageSemaLock.newCondition();
	private Semaphore mImageDownloadsSema = new Semaphore(NUM_API_INSTANCES_FOR_IMAGES);
	
	// when a user signs in (reset), everything waits until it completes so that 
	// any connect errors can be processed first (especially auth token errors that can't be
	// rectified)
	volatile boolean mIsUserBeingReset = false;
	volatile boolean mIsAuthTokenStale = false; // set by the escalation exception, must be
												// reset by a call to reset the user 
	volatile long mUserSwitchAtMillis = -1L; // the time a switch takes place, for downloading images
	volatile boolean mIsForegroundTaskRunning = false;
	volatile boolean mHaveUserPrefs = false;
	volatile boolean mResetUserDataReady = false;
	volatile int mCurrentError = NO_ERRORS; // throw errors back to the caller

	// a handler to pass messages back to the activity
	private Handler mActivityHandler;
	
	// a continuously running runnable allocated a thread from the
	// pool which handles server scheduled notice updates
	private ContinuousRunner mNoticesRunner;
	// similarly for the image downloading master thread
	private ContinuousRunner mDownloadImagesRunner;

	// acts as a go-between, saving on extra work
	private Bitmap mSharedImage;
	private Boolean mIsFilterContent;
	
	public DownloadBinder(DataDownloadService service) {
		mService = service;
		mExecutor = Executors.newFixedThreadPool(MAX_THREADS);
		mActivityHandler = ((PotlatchApplication) service.getApplication()).getBinderHandler();

		// create the delegates
		mGiftsDelegate = new GiftsBinderDelegate(this, mService);
		mUserNoticesDelegate = new UserNoticesBinderDelegate(this, mService);

		// make a continuously running thread for updates 
		mNoticesRunner = mUserNoticesDelegate.makeContinuousRunningUpdater();
		
		// and allocate it a thread
		mExecutor.execute(mNoticesRunner);
		
		// same for image downloading
		mImageDownloadDelegate = new ImageDownloadBinderDelegate(this, mService);
		mDownloadImagesRunner = mImageDownloadDelegate.makeContinuousRunningDownloader();
		mExecutor.execute(mDownloadImagesRunner);
	}

	/**
	 * This method could block on the semaphore lock, so should only be called after an executor has
	 * provided a thread and from within that thread's run.
	 * @param idx
	 * @return
	 */
	ApiInstanceWrapper getApiInstanceForImageDownload() {
		
		Log.v(LOG_TAG, "getApiInstanceForImageDownload: try to acquire semaphore lock");

		mImageSemaLock.lock();
		
		try {
			while (mImageDownloadsSema.availablePermits() == 0) {
				mPermitsLocked.awaitUninterruptibly();
			}

			Log.v(LOG_TAG, "getApiInstanceForImageDownload: got semaphore lock, permits="
					+mImageDownloadsSema.availablePermits());
			
			mImageDownloadsSema.acquireUninterruptibly();

			// give another thread a chance to grab a permit
			mPermitsLocked.signalAll();
		}
		finally {
			Log.v(LOG_TAG, "getApiInstanceForImageDownload: released semaphore lock");
			mImageSemaLock.unlock();
		}
		
		
		Log.v(LOG_TAG, "getApiInstanceForImageDownload: try to acquire switching lock");
		// acquire a lock on the switching user lock
		// necessary because switch over re-creates the instances and there's
		// a moment when they're all cleared out
		mUserSwitchingLock.lock();
		
		try {
			while (mIsUserBeingReset) {
				mNotSwitchingUser.awaitUninterruptibly();
			}

			Log.v(LOG_TAG, "getApiInstanceForImageDownload: got switching lock #apiInstances="+
					mPotlatchSvcUsers.size());
			
			ApiInstanceWrapper apiInstance = mPotlatchSvcUsers.remove(0);
			
			mNotSwitchingUser.signalAll();
			
			return apiInstance;
		}
		finally {
			Log.v(LOG_TAG, "getApiInstanceForImageDownload: release switching lock");
			mUserSwitchingLock.unlock();
		}
	}
	
	/**
	 * Returns an instance to the pool provided it's not stale (user changed)
	 * Even so, it releases its permit
	 * @return
	 */
	void releaseApiInstanceFromImageDownload(ApiInstanceWrapper apiInstance) {
		
		Log.v(LOG_TAG, "releaseApiInstanceFromImageDownload: try to acquire switching lock");
		// acquire a lock on the switching user lock
		// necessary because switch over re-creates the instances and there's
		// a moment when they're all cleared out, it also is the lock that's
		// used to acquire the instances, so protects the arraylist from
		// concurrent access
		mUserSwitchingLock.lock();
		
		try {
			while (mIsUserBeingReset) {
				mNotSwitchingUser.awaitUninterruptibly();
			}

			Log.v(LOG_TAG, "releaseApiInstanceFromImageDownload: got switch lock, try to acquire semaphore lock");
			mImageSemaLock.lock();
			
			try {
				// test the time on the instance, if it's old don't put it back in the pool
				if (apiInstance.mInstanceCreatedAt >= mUserSwitchAtMillis) {

					// just release it, no condition needs to be tested
					mImageDownloadsSema.release();

					Log.v(LOG_TAG, "releaseApiInstanceFromImageDownload: released semaphore and put api back to pool, permits="
						+mImageDownloadsSema.availablePermits());
					
					mPotlatchSvcUsers.add(apiInstance); 
					
					// give another thread a chance to grab a permit
					mPermitsLocked.signalAll();
				}
				else {
					//TODO remove this
					Log.v(LOG_TAG, "releaseApiInstanceFromImageDownload: api is STALE (millis="+mUserSwitchAtMillis+") semaphore NOT released, permits="
							+mImageDownloadsSema.availablePermits());
				}
			}
			finally {
				mImageSemaLock.unlock();
				mNotSwitchingUser.signalAll();
			}			
		}
		finally {
			Log.v(LOG_TAG, "releaseApiInstanceFromImageDownload: release switching lock, semaphore permits="
					+mImageDownloadsSema.availablePermits());
			mUserSwitchingLock.unlock();
		}
	}
	
	public int getCurrentError() {
		return mCurrentError;
	}

	/**
	 * When an error is trapped, it will prevent new server interactions until dealt with.
	 * This method clears the error so things can run smoothly again.
	 */
	public void clearErrors() {
		Log.d(LOG_TAG, "clearErrors: ");
		mCurrentError = NO_ERRORS;
	}

	/**
	 * Called when the user changes, setup the connection and query the user
	 * prefs and the user notices since last login, and start gifts downloads
	 * @param currentUser
	 * @param searchCriteria
	 */
	public void resetUser(User currentUser, SearchCriteria<Gift> searchCriteria) {
		Log.d(LOG_TAG, "resetUser: ");
		
		mIsAuthTokenStale = false;
		
		// need user notice first time, and user preferences
		downloadUserNoticeThenPrefsAndGifts(currentUser, searchCriteria);
		
	}

	/**
	 * Set when the user signs in and notified to the ui at same time as the notices
	 * 
	 * @return
	 */
	public boolean isUserFilterContent() {
		return mHaveUserPrefs && mIsFilterContent;
	}
	
	/**
	 * Called from the SettingsFragment, if there's a failure the last known good value
	 * is reset here. Either way a message is sent back.
	 * @param value
	 * @throws AuthTokenStaleException 
	 */
	public void setUserFilterContent(final boolean value) throws AuthTokenStaleException {

		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				// unset the foreground tasks lock
				mForegroundTaskLock.lock();
				try {
					awaitLockForegroundTasks();
					
					if (value) {
						mForegroundSvcApi.filterContent();
					}
					else {
						mForegroundSvcApi.unfilterContent();
					}
					
					mIsFilterContent = value;
					
					// feed back
					sendMessage(USER_PREFS_UPDATED, null);

				}
				catch (RetrofitError e) {
					// bad request, meaning already set (could happen if same user sets
					// the filter from another device) will have same affect as an error
					// essentially the setting is reset to last known good value by
					// the activity calling setSharedPrefsToDownloadedValues()
					
					try {
						processServerError(e, true);
					} 
					catch (AuthTokenStaleException e1) {
						Log.w(LOG_TAG, "RetrofitError exception handler: stale, but no escalation to happen here", e);
					}
				}
				catch (Exception e) {
					Log.e(LOG_TAG, "Unexpected error: setting filter", e);
				}
				finally {
					signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * Called from the SettingsFragment, if there's a failure the last known good value
	 * is reset here. Either way a message is sent back.
	 * @param value
	 * @throws AuthTokenStaleException 
	 */
	public void setUserUpdateInterval(final long value) throws AuthTokenStaleException {
		
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				// unset the foreground tasks lock
				mForegroundTaskLock.lock();
				try {
					awaitLockForegroundTasks();
					
					mForegroundSvcApi.setUpdateInterval(value);
					
					mUpdateInterval = value;
					
					// feed back
					sendMessage(USER_PREFS_UPDATED, null);

					// and interrupt the notices thread which may be sleeping... so it doesn't mess up the interval
					mNoticesRunner.resetByInterrupting();

				}
				catch (RetrofitError e) {
					try {
						processServerError(e, true);
					} 
					catch (AuthTokenStaleException e1) {
						Log.w(LOG_TAG, "RetrofitError exception handler: stale, but no escalation to happen here", e);
					}
				}
				finally {
					signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * When called during user sign on in downloadUserNoticeThenPrefsAndGifts()
	 * it's already running in an executor thread, so no need to grab another one.
	 * Doesn't send a message then because that method does it
	 * 
	 * @param apiInstance
	 * @param sendMsg
	 */
	private void downloadUserPrefs(PotlatchSvcApi apiInstance, final boolean sendMsg) 
		throws RetrofitError {
		
		// only not own thread during user sign in, so need to setup schedule
		// for auto update
		try {
			mUpdateInterval = apiInstance.getUpdateInterval();
			mIsFilterContent = apiInstance.isUserFilterContent();
			
			setSharedPrefsToDownloadedValues();

			mHaveUserPrefs = true;
			// and interrupt the notices thread which may be sleeping... so it doesn't mess up the interval
			mNoticesRunner.resetByInterrupting();

			if (sendMsg) {
				mActivityHandler.sendEmptyMessage(USER_PREFS_RECEIVED);
			}
		}
		catch (RetrofitError e) {
			Log.e(LOG_TAG, "downloadUserPrefs: ", e);
		}
	}

	/**
	 * Each of the user prefs are reset locally to the last good values from the server.
	 * This is needed for when a failure happens trying to update the server.
	 */
	public void setSharedPrefsToDownloadedValues() {
		Resources res = mService.getResources();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mService);
		Editor editor = prefs.edit();
		editor.putString(res.getString(R.string.prefkey_interval), Long.toString(mUpdateInterval));
		editor.putBoolean(res.getString(R.string.prefkey_filter_content),
				mIsFilterContent);
		editor.commit();
	}

	/**
	 * Called by the delegates when they've done something the front end needs to know about
	 * @param which
	 * @param giftsSectionType 
	 */
	void sendMessage(int which, GiftsSectionType giftsSectionType) {
		if (giftsSectionType == null) {
			mActivityHandler.sendEmptyMessage(which);
		}
		else {
			Message msg = mActivityHandler.obtainMessage(which, giftsSectionType);
			mActivityHandler.sendMessage(msg);
		}
	}
	
	/**
	 * Called during resetUser (new user sign in)
	 */
	private void downloadUserNoticeThenPrefsAndGifts(final User currentUser, final SearchCriteria<Gift> searchCriteria) {
		mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					// this is running in the 'foreground' thread (not the ui thread), meaning it's
					// a task that has been initiated by the user doing something and so has mega
					// high priority
					
					// acquire lock on the switching user lock
					// interruption is ok, it will keep going in the outer
					// loop unless the stop flag has been set too
					Log.v(LOG_TAG, "user switch: getting switch lock");
					mUserSwitchingLock.lock();
					
					try {
						// flag will be unset when successful communication with the server happens
						// this will prevent the waking notices update thread from attempting to 
						// get an api instance and doing its work
						mIsUserBeingReset = true;
						// any image download threads will know from this not to return their api instances to the pool
						mUserSwitchAtMillis = System.currentTimeMillis();
						mHaveUserPrefs = false;
					}
					finally {
						Log.v(LOG_TAG, "user switch: setup done, releasing switch lock, getting foreground lock");
						mUserSwitchingLock.unlock();
					}

					// acquire lock on the foreground task lock
					// the previous lock stops anything else running while switching is happening
					// this one means this thread won't continue while another foreground task completes
					mForegroundTaskLock.lock();
					
					try {
						awaitLockForegroundTasks();

						// at this point, the only tasks that should possibly be running
						// would be the image downloaders... it doesn't really matter
						// if they're from a previous user's api instance as everyone
						// would download exactly the same bits, when they complete, they'll
						// release their permits and test will indicate not to put the instance back

						// create the retrofit api instances for this user, see comment above for
						// reason for having one less than the number of threads
						Log.v(LOG_TAG, "user switch: got foregrd lock, resetting apiInstances");
						
						mPotlatchSvcUsers.clear();
						for (int i = 0; i < NUM_API_INSTANCES; i++) {
							mPotlatchSvcUsers.add(new ApiInstanceWrapper(mUserSwitchAtMillis,
									new AuthTokenSecuredRestBuilder()
										.setClient(new ApacheClient(new EasyHttpClient()))//new AcceptSelfSignedCertificatesHttpClient(mService)))
										.setAccessToken(currentUser.getAuthToken())
										.setEndpoint("https://"+currentUser.getServer())
										.build()
										.create(PotlatchSvcApi.class)));	
						}
						
						// this part is important, remove 2 instances from the pool and set them
						// to the special cases
						mNoticesSvcApi = mPotlatchSvcUsers.remove(0).mApiInstance;
						mForegroundSvcApi = mPotlatchSvcUsers.remove(0).mApiInstance;
						
						if (mPotlatchSvcUsers.size() != NUM_API_INSTANCES_FOR_IMAGES) {
							Log.w(LOG_TAG, "Program bug, created uneven number of instances ("+mPotlatchSvcUsers.size()
									+") for image downloads"+NUM_API_INSTANCES_FOR_IMAGES);
						}

						// have just created a whole new set of apiInstances, it's possible there are 
						// image download threads holding onto stale apiInstances now, but they can't drop
						// them while there's a semaphore lock on, plus they WON'T release a permit to the
						// pool, so here the number of permits must be brought back up to 6
						
						Log.v(LOG_TAG, "user switch: acquire sema lock, apiInstances created (millis="+mUserSwitchAtMillis+"), #for images="+mPotlatchSvcUsers.size());

						mImageSemaLock.lock();
						
						try {
							int availablePermits = mImageDownloadsSema.availablePermits();
							int neededPermits = mPotlatchSvcUsers.size() - availablePermits;
							
							Log.v(LOG_TAG, "user switch: got sema lock, permits="+availablePermits
									+", permits to add="+neededPermits);
							
							if (neededPermits > 0) {
								mImageDownloadsSema.release(neededPermits);
								// give another thread a chance to grab a permit
								mPermitsLocked.signalAll();
							}							
						}
						finally {
							mImageSemaLock.unlock();
							Log.v(LOG_TAG, "user switch: sema locked, permits="+mImageDownloadsSema.availablePermits());
						}
						
						// should already be set to no errors
						mCurrentError = NO_ERRORS;

						try {
							mUserNoticesDelegate.setUserNotice(mForegroundSvcApi.getNotices());
						} 
						catch (Exception e) {
							Log.e(LOG_TAG, "user switch: From set user notice", e);
							throw e;
						}						
					}
					catch (Exception e) {
						Log.e(LOG_TAG, "user switch: From setup apiInstances block", e);
						throw e;
					}
					finally {
						mForegroundTaskLock.unlock();
						
						Log.v(LOG_TAG, "user switch: unlocked foregrd (not released cond), re-acquiring switch lock");
						
						// switching the user over has finished, so can release the lock
						// note, the foreground task lock won't be released until this 
						// whole method completes
						mUserSwitchingLock.lock();
						try {
							mResetUserDataReady = true;
							mIsUserBeingReset = false;
							mNotSwitchingUser.signalAll();
						}
						finally {
							Log.v(LOG_TAG, "user switch: unlock switch lock, set isUserBeingReset = false");
							mUserSwitchingLock.unlock();
						}
					}
					
					// get gifts data, each one sends a message to the ui when done
					for (int i = 0; i < PotlatchConstants.NUM_GIFT_SECTION_MENU_TYPES; i++) {
						GiftsSectionType giftsType = Utils.getGiftSectionTypeForIndex(i); // first one is all gifts, so feed back is quick
						
						// only admin can see obscene gifts
						if (giftsType != GiftsSectionType.OBSCENE_GIFTS
								|| currentUser.isAdmin()) {

							Log.v(LOG_TAG, "user switch: getting gifts for "+giftsType.name());
							
							mGiftsDelegate.resetToNewList(mForegroundSvcApi, searchCriteria, giftsType);
						}
					}
					
					Log.v(LOG_TAG, "user switch: getting user prefs");

					// get the user preferences for future updates, and for filtering setting
					downloadUserPrefs(mForegroundSvcApi, false); // not own thread, don't send msg

					Log.v(LOG_TAG, "user switch: handle user notice");
					
					// waits a short while for timeout on the broadcast, so do it after getting the gifts data
					mUserNoticesDelegate.handleUserNotice(UserNoticesBinderDelegate.RESET_USER_BROADCAST_TIMEOUT);
				} 
				catch (RetrofitError e) {
					try {
						processServerError(e, true);
					} 
					catch (AuthTokenStaleException e1) {
						Log.e(LOG_TAG, "RetrofitError exception handler: stale, user not reset, why called before auth token reset?", e);
					}
				}
				finally {
					Log.v(LOG_TAG, "user switch: acquire foregrd lock and release, all complete");

					// unset the foreground tasks lock
					mForegroundTaskLock.lock();
					signalAndUnlockForegroundTasks();
				}
			}
		});
	}

	/**
	 * Called during getUserNotice() when passed to clear it, also when
	 * the user presses the notification action showing the message.
	 * @throws AuthTokenStaleException 
	 */
	public void clearUserNotice() throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mUserNoticesDelegate.clearUserNoticeLockForegroundTasks(mForegroundSvcApi);
	}

	/**
	 * Called as a result of the user scrolling to the bottom of a list that
	 * can still load more pages.
	 * 
	 * @param giftsSectionType
	 * @throws AuthTokenStaleException 
	 */
	public void loadGiftsNextPage(GiftsSectionType giftsSectionType) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.loadAnotherPageLockForegroundTasks(false, giftsSectionType);
	}

	/**
	 * Called as a result of the user scrolling to the top of a list that
	 * can still load more pages.
	 * 
	 * @param giftsSectionType
	 * @throws AuthTokenStaleException 
	 */
	public void loadGiftsPrevPage(GiftsSectionType giftsSectionType) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.loadAnotherPageLockForegroundTasks(true, giftsSectionType);
	}

	/**
	 * Called by the ui (giftsFragments) to refresh the data manually when they are
	 * permitted because they have internet/binder/not blocked by add gift dialog open,
	 * and by gifts list activities either onResume() or when that blocking dialog is closed.
	 * 
	 * @param searchCriteria
	 * @param giftsType
	 * @throws AuthTokenStaleException 
	 */
	public void refreshOrNewGifts(SearchCriteria<Gift> searchCriteria, GiftsSectionType giftsType) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.refreshOrNewGiftsLockForegroundTasks(giftsType, searchCriteria);
	}

	/**
	 * Overloaded version that only acts on a single gifts section type. 
	 * Called from OneChainGiftsActivity.handleRefresh()
	 * 
	 * @param searchCriteria
	 * @param singleSectionType 
	 * @throws AuthTokenStaleException 
	 */
	public void resetGifts(SearchCriteria<Gift> searchCriteria, GiftsSectionType singleSectionType) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.resetToNewListsLockForegroundTasks(searchCriteria, singleSectionType);
	}

	/**
	 * Called by the ui to reset the list when a new search criteria is entered.
	 * 
	 * @param searchCriteria
	 * @throws AuthTokenStaleException 
	 */
	public void resetGifts(SearchCriteria<Gift> searchCriteria) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.resetToNewListsLockForegroundTasks(searchCriteria, null);
	}

	/**
	 * The UI has received the message that the Gifts data list has changed, and now the adapter
	 * needs the update in the UI thread
	 * 
	 * @param giftsSectionType
	 */
	public void rebuildGiftsAdapterList(GiftsSectionType giftsSectionType) {
		mGiftsDelegate.rebuildGiftsUiList(giftsSectionType);
	}

	/**
	 * Called by the activity when it receives the broadcast to grab the user data
	 * @throws AuthTokenStaleException 
	 */
	public UserNotice getUserNotice(boolean clearIt) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		UserNotice un = mUserNoticesDelegate.getUserNotice();
		
		if (clearIt) {
			mResetUserDataReady = false;
			mUserNoticesDelegate.clearUserNoticeLockForegroundTasks(mForegroundSvcApi);
		}
		
		return un;
	}
	
	/**
	 * True only until the user notice that is read when sign-in happens gets cleared
	 * by a read with clearIt = true;
	 * @return
	 */
	public boolean isResetUserDataReady() {
		// take an opportunity to make sure there are no notifications in the system tray
		mService.closeNotifications();
		
		return mResetUserDataReady;
	}

	/**
	 * Service is dying, kill this too
	 */
	public void destroy() {
		mNoticesRunner.stopRunning();
		mExecutor.shutdown();
	}

	/**
	 * Called when Android is low on memory
	 * @param level
	 */
	public void stripdownCaches(int level) {
		// TODO 
		
	}

	/**
	 * Utility method called in many places.
	 * When updateCurrentError is set to true, if get unauthorized an exception is thrown
	 * and this prevents further accesses until the user is resigned in
	 * @param e
	 * @param updateCurrentError
	 * @return
	 * @throws AuthTokenStaleException 
	 */
	int processServerError(RetrofitError e, boolean updateCurrentError) throws AuthTokenStaleException {
		int error = NO_ERRORS;
		
		if (e.isNetworkError()) {
			Log.w(LOG_TAG, "processServerError: Network error="+e.getCause());
			error = ERROR_NETWORK;
		}
		else {
			Response resp = e.getResponse();
			if (resp != null) {

				// check for a stale auth token in particular
				if (e.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
					Log.w(LOG_TAG, "processServerError: unauthorized (stale token)");
					error = ERROR_AUTH_TOKEN_STALE;
				}
				else if (e.getResponse().getStatus() == HttpStatus.SC_NOT_FOUND) {
					Log.w(LOG_TAG, "processServerError: 404 - not found");
					error = ERROR_NOT_FOUND;
				}
				else {
					Log.e(LOG_TAG, "processServerError: status="+resp.getStatus(), e);
					// for now just pass the response code, as they get known will give local
					// identifiers
					error = resp.getStatus();
				}
			}
			else {
				Log.e(LOG_TAG, "processServerError: "+e.getCause(), e);
				error = ERROR_UNKNOWN;
			}
		}

		// if have a handler grab reference (don't know when it might get reset)
		if (error == NO_ERRORS) {
			Log.e(LOG_TAG, "Huh, why has nothing set the current error?");
		}
		else if (error == 0) {
			Log.e(LOG_TAG, "something has set the error code to 0, which is kinda meaningless");
		}
		else if (updateCurrentError) {
			mCurrentError = error;			
			sendMessage(mCurrentError, null);
			
			if (error == ERROR_AUTH_TOKEN_STALE) {
				throw new AuthTokenStaleException();
			}
		}
		
		return error;
	}

	/**
	 * Called when constructing the adapter to show Gifts lists
	 * Note, only for first time into the page, updates don't return
	 * 
	 * @param giftsSectionType
	 * @return
	 */
	public PartialPagedList<Gift> getNewUserGiftsList(GiftsSectionType giftsSectionType) {
		return mGiftsDelegate.getNewGiftsTypeList(giftsSectionType);
	}

	/**
	 * Called when constructing the adapter to show TopGivers lists
	 * 
	 * @return
	 */
	public PartialPagedList<PotlatchUser> getNewTopGiversList() {		
		return mTopGiversBinderDelegate.getNewTopGiversList();
	}

	/**
	 * The UI has received the message that the Top Givers data list has changed, and now the adapter
	 * needs the update in the UI thread
	 */
	public void rebuildTopGiversAdapterList() {
		mTopGiversBinderDelegate.rebuildTopGiversUiList();
	}

	/**
	 * Called when first invoking the top givers page
	 * @throws AuthTokenStaleException 
	 */
	public void loadNewTopGivers() throws AuthTokenStaleException {

		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		if (mTopGiversBinderDelegate == null) {
			mTopGiversBinderDelegate = new TopGiversBinderDelegate(this, mGiftsDelegate);
		}
		
		mTopGiversBinderDelegate.loadTopGiversLockForegroundTasks();
	}

	/**
	 * Called as a result of the user scrolling to the bottom of a list that
	 * can still load more pages.
	 * @throws AuthTokenStaleException 
	 */
	public void loadTopGiversNextPage() throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mTopGiversBinderDelegate.loadAnotherPageLockForegroundTasks(false);
	}

	/**
	 * Called as a result of the user scrolling to the top of a list that
	 * can still load more pages.
	 * @throws AuthTokenStaleException 
	 */
	public void loadTopGiversPrevPage() throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mTopGiversBinderDelegate.loadAnotherPageLockForegroundTasks(true);
	}

	/**
	 * Called during init of the views for GiftsFragments. If available a subsequent
	 * call will be made for the cached data, and if not, then a new request to refresh.
	 * This called all be collapsed into a more intelligent method that returns the data if
	 * there and if not refreshes it, but this is way more explicit and obvious what it's 
	 * doing.
	 * 
	 * @param giftsSectionType
	 * @return
	 */
	public boolean hasGiftsData(GiftsSectionType giftsSectionType) {
		return mGiftsDelegate.hasGiftsTypeList(giftsSectionType);
	}


	/**
	 * Tries to get the image from the cache, if it's loaded already
	 * @param giftId
	 * @return
	 * @throws ImageFailedInDownloadException 
	 */
	public Bitmap getThumbImageForGift(long giftId) throws ImageFailedInDownloadException {
		return mImageDownloadDelegate.getThumbImageFromCache(giftId);
	}

	/**
	 * Called when viewing a single gift, it is almost certain that the gift is in the
	 * list, but if it was deleted on the server, and there's been an update it's possible
	 * it's gone in the meantime. Try to pass back what's local, and at the same time
	 * get an update from the server, if possible.
	 * Also starts to download the full image from the server.
	 *
	 * @param giftId
	 * @param giftsSectionType
	 * @param listIdx
	 * @return
	 * @throws AuthTokenStaleException 
	 */
	public Gift getGiftAndPrepareFullImage(long giftId, GiftsSectionType giftsSectionType, int listIdx) throws AuthTokenStaleException {
		
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mImageDownloadDelegate.downloadImageInOwnThread(giftId);
		return mGiftsDelegate.getGiftLocallyAndRefreshInForegroundTask(giftId, giftsSectionType, listIdx);
	}

	/**
	 * Related to getGiftAndPrepareFullImage(), which has downloaded the gift anew and sent a message, now the
	 * activity is receiving that message and asking for it.
	 *
	 * @param giftId
	 * @return
	 */
	public Gift getGift(long giftId) {
		return mGiftsDelegate.getGift(giftId);
	}

	public void deleteGift(long giftId, boolean isOwner) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.deleteGiftInForegroundTask(giftId, isOwner);
	}

	/**
	 * Related to getGiftAndPrepareFullImage(), the view now has set its size and it's possible
	 * to provide scaled images.
	 *
	 * @param giftId
	 * @param w
	 * @param h
	 */
	public void loadScaledImagesForGift(long giftId, int w, int h) {
		mImageDownloadDelegate.scaleImageInOwnThread(giftId, w, h);
	}

	/**
	 * Called after loadScaledImagesForGift() has previously notified the ui
	 * that there's an image ready  
	 * @param giftId
	 * @return
	 */
	public Bitmap getScaledImageForGift(long giftId) {
		return mImageDownloadDelegate.getScaledImageForGift(giftId);
	}
	
	/**
	 * Called when the user toggles the full image scaling mode
	 * @param w
	 * @param h
	 * @param scalingLogic 
	 */
	public void changeScalingLogic(int w, int h, ScalingLogic scalingLogic) {
		mImageDownloadDelegate.changeScalingLogic(w, h, scalingLogic);
	}
	
	/**
	 * Allow visibility for menu setting
	 * @return
	 */
	public ScalingLogic getScalingLogic() {
		return mImageDownloadDelegate.mScalingLogic;
	}
	
	/**
	 * Wraps the api instance in a class with a timestamp for when it was created
	 * to allow the return to pool method to know if it should just be discarded
	 * @author 
	 *
	 */
	static class ApiInstanceWrapper {
		long mInstanceCreatedAt;
		PotlatchSvcApi mApiInstance;

		public ApiInstanceWrapper(long userSwitchAtMillis, PotlatchSvcApi apiInstance) {
			this.mInstanceCreatedAt = userSwitchAtMillis;
			this.mApiInstance = apiInstance;
		}
		
	}

	/**
	 * To facilitate transition of the thumb images to full view
	 * @param image
	 */
	public void putTransitionThumb(Bitmap image) {
		mSharedImage = image;
	}

	public Bitmap getSharedImage() {
		return mSharedImage;
	}

	/**
	 * User toggles one of the 3 possible responses
	 * 
	 * @param giftId
	 * @param giftResponseType
	 * @param isChecked
	 * @throws AuthTokenStaleException 
	 */
	public void setGiftResponse(long giftId, int giftResponseType, boolean isChecked) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.setGiftResponseInForegroundTask(giftId, giftResponseType, isChecked);
	}

	/**
	 * Called from AddGiftDialogFragment when the user presses upload/save
	 * @param gift
	 * @param mediaFile
	 * @param contentUri 
	 * @throws AuthTokenStaleException 
	 */
	public void addGift(Gift gift, File mediaFile, Uri contentUri) throws AuthTokenStaleException {
		// abort if no auth token
		if (!checkAuthTokenIsValid()) {
			throw new AuthTokenStaleException();
		}
		
		mGiftsDelegate.addGiftInForegroundTask(gift, mediaFile, contentUri);
	}

	/**
	 * Utility method used in many places where a foreground task is needed to wait for it uninterruptibly
	 */
	void awaitLockForegroundTasks() {
		while (mIsForegroundTaskRunning) {
			mNotRunningForeground.awaitUninterruptibly();
		}
		mIsForegroundTaskRunning = true;
	}

	/**
	 * Utility method used in many places where a foreground task is locked to unlock it again
	 */
	void signalAndUnlockForegroundTasks() {
		try {
			mNotRunningForeground.signalAll();
		}
		finally {
			mIsForegroundTaskRunning = false;
			mForegroundTaskLock.unlock();
		}
	}

	/**
	 * All methods check this before attempting to go through, the UI methods *should* also
	 * check it before trying to make further calls
	 * @return
	 */
	public boolean checkAuthTokenIsValid() {
		if (mIsAuthTokenStale) {
			Log.w(LOG_TAG, "checkAuthTokenIsValid: returning false, flag must be reset by resetUser");
		}
		return !mIsAuthTokenStale;
	}

}
