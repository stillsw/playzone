package com.stillwindsoftware.keepyabeat.player.backend;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.gui.PlayRhythmsActivity;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;

/**
 * Service that handles communication between BeatTracker's playing Thread and
 * the rest of the app. BeatTracker is itself decoupled from the rest of the
 * app and this provides access via the Binder that implements 
 * IBeatTrackerCommunication.
 * 
 * Notes: This service is a started service and also a bound service. Clients
 * that will bind to it are RhythmPlayingFragmentHolder and other guis that
 * display playing rhythms, such as the Notification which is started from this
 * Service only under certain conditions: a) BeatTracker is playing a rhythm b)
 * No other gui clients are connected This service will stop itself when: a) No
 * client is bound anymore b) The rhythm is not playing anymore c) A short time
 * has elapsed (to allow a another bind to happen) IMPORTANT: This class does
 * not currently run in a separate thread, nor in its own process. It does have
 * free access to shared memory to access the objects that it passes to/from
 * BeatTracker. BeatTracker runs its own Thread to play rhythms though,
 * and handles its own concurrency through synchronised methods.
 */
public class BeatTrackerService extends Service {

//	public static final String MESSENGER_KEY = "BeatTrackerService-Messenger";
	private static final String LOG_TAG = "KYB-" + BeatTrackerService.class.getSimpleName();

	// delay before service is killed when not in use (2 minutes)
	private static final long IDLE_DELAY = 120000;
	// pending intent action extras
	private static final String NOTIFICATION_ACTION_STOP_PLAYING = "stop playing";
    private static final String NOTIFICATION_ACTION_PAUSE_PLAYING = "pause playing";
    private static final String NOTIFICATION_ACTION_START_PLAYING = "start playing";
    private static final int FOREGROUND_NOTIFICATION_ID = 2;
    private static final String NOTIFICATION_CHANNEL_ID = "KYB player background";

    private Handler mSelfCheckHandler;
	private Runnable mSelfCheckRunner = new Runnable() {
		@Override
		public void run() {
			selfStopCheck();
		}
	};

	private boolean mServiceInUse;
	private BeatTrackerBinder mBinder;

	/**
	 * Make an intent that will start this service if supplied to startService()
	 * as a parameter.
	 * 
	 * @param context
	 */
	public static Intent makeIntent(Context context) {// , Handler handler) {
		// Messenger messenger = new Messenger(handler);
		Intent intent = new Intent(context, BeatTrackerService.class);
		// intent.putExtra(MESSENGER_KEY, messenger);
		return intent;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mBinder = new BeatTrackerBinder(this);
		mSelfCheckHandler = new Handler(Looper.getMainLooper());
	}

    private Notification makeNotification(String text, String title) {
        Intent notificationIntent = new Intent(this, PlayRhythmsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(Notification.PRIORITY_HIGH)         // foreground anyway so max just makes sure all the buttons always show w/o expanding it
                .setContentIntent(pendingIntent);


        // add a stop button


        Context context = getApplicationContext();
        Intent stopPlaying = new Intent(context, BeatTrackerService.class);
        stopPlaying.putExtra(NOTIFICATION_ACTION_STOP_PLAYING, true);
        PendingIntent pendingStopIntent = PendingIntent.getService(context, 0, stopPlaying, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.addAction(R.drawable.ic_notification_stop_playing, getResources().getText(R.string.notification_action_stop), pendingStopIntent);


        // and a play or pause button

        Intent playPauseIntent = new Intent(context, BeatTrackerService.class);
        boolean isPlaying = mBinder.isPlaying();
        playPauseIntent.putExtra(NOTIFICATION_ACTION_PAUSE_PLAYING, isPlaying);
        playPauseIntent.putExtra(NOTIFICATION_ACTION_START_PLAYING, !isPlaying);
        // NOTE: different request code here, otherwise uses the same intent as for stop and it doesn't work
        PendingIntent pendingPlayPauseIntent = PendingIntent.getService(context, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, getResources().getText(R.string.notification_action_pause), pendingPlayPauseIntent);
        }
        else {
            builder.addAction(android.R.drawable.ic_media_play, getResources().getText(R.string.notification_action_play), pendingPlayPauseIntent);
        }

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "createNotificationChannel: no notificationManager, unable to create channel");
        }

        return  NOTIFICATION_CHANNEL_ID;
    }

    /**
     * Called from the binder in initModel()
     * @param title
     * @param currentIteration
     * @param playerState
     * @param isPlaying
     */
    public void updateNotification(String title, int currentIteration, PlayerState playerState, boolean isPlaying) {
        NotificationManager notMan = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notMan.notify(FOREGROUND_NOTIFICATION_ID, makeNotification(getNotificationText(currentIteration, playerState, isPlaying), title));
    }

    /**
	 * A call to start should be followed by a bind, but in case it isn't
	 * a timed self check is initiated (so a selfStop can happen).
	 * The only time no bind is expected to happen is when called by the action on
	 * the notification to stop playing 
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

        boolean isRequestToStop = intent != null && intent.getBooleanExtra(NOTIFICATION_ACTION_STOP_PLAYING, false);
        boolean isRequestToPause = intent != null && intent.getBooleanExtra(NOTIFICATION_ACTION_PAUSE_PLAYING, false);
        boolean isRequestToPlay = intent != null && intent.getBooleanExtra(NOTIFICATION_ACTION_START_PLAYING, false);

        if (!isRequestToStop && !isRequestToPause && !isRequestToPlay) {
            initSelfCheck();
        }
        else {
            try {
                if (isRequestToStop) {
                    mBinder.stopPlay();
                } else if (isRequestToPause) {
                    mBinder.togglePlayPause(false);
                } else if (isRequestToPlay) {
                    mBinder.togglePlayPause(true);
                }
            } catch (NullPointerException e) {
                // cause it to stop, fix to bug where playing, receive a fongo call and it goes on pause,
                // then do some other stuff in fongo, come back to kyb and it restarts but the notification says it's playing
                // then hit pause in the notification and it crashes with NPE on null array (see getting it to work error trace)
			    // after restart it also instantly crashes again, and has to have 2nd restart
                AndroidResourceManager.loge(LOG_TAG, "onStartCommand: unexpected error, aborting service", e);

                // similar to onDestroy(), but non-conditional
                stopForeground(true);
                stopSelf();

                mSelfCheckHandler.removeCallbacksAndMessages(null);

                if (mBinder != null) {
                    mBinder.destroy(); // prevents further attempts to use the binder
                }

                return START_NOT_STICKY; // hopefully allows it to start without guaranteeing it sticks around
            }

            // send a broadcast for the activity to update the UI (play button)
            Intent broadcastIntent = new Intent(KybActivity.PLAYER_NOTIFICATION_ACTION);
            broadcastIntent.putExtra(KybActivity.RECEIVE_MESSAGE_FLAG, KybActivity.RECEIVE_MESSAGE_FLAG_NOTIFICATION_ACTED_ON);
            getApplication().sendBroadcast(broadcastIntent);
        }

		return START_STICKY;
	}

	/**
	 * Post a delayed check that something is bound to this service and if it
	 * isn't and player is not playing it should stop itself
	 */
	void initSelfCheck() {
		mSelfCheckHandler.postDelayed(mSelfCheckRunner, IDLE_DELAY);
	}

	/**
	 * The self check handler calls this after the specified delay
	 */
	private synchronized void selfStopCheck() {
		
		mSelfCheckHandler.removeCallbacksAndMessages(null);
		
        if (mBinder.isStopped()) {
            AndroidResourceManager.logd(LOG_TAG, "selfStopCheck: stopping service");
            mBinder.destroy(); // prevents further attempts to use the binder
            stopForeground(true);
            stopSelf();
        }
	}
	
//	/**
//	 * Creates the notification after the selfStopCheck() delayed runnable is invoked which indicates
//	 * something is playing or paused, and so needs a notification. Also called when playerState changes
//	 * between play/pause (not stop as that would just stop the service) or number of iterations changes
//	 * and the iteration type is not infinite loop, because the text needs to change.
//	 *
//	 */
//	private void initNotification(String text) {
//
//		if (!mServiceInUse && !mBinder.isStopped()) {
//			// intent to re-open kyb
//			Intent openKyb = new Intent(getApplicationContext(), PlayRhythmsActivity.class);
//			PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, openKyb, Intent.FLAG_ACTIVITY_NEW_TASK);
//
//			Intent stopPlaying = new Intent(getApplicationContext(), BeatTrackerService.class);
//			stopPlaying.putExtra(NOTIFICATION_ACTION_STOP_PLAYING, true);
//			PendingIntent pendingStopIntent = PendingIntent.getService(getApplicationContext(), 0, stopPlaying, PendingIntent.FLAG_UPDATE_CURRENT);
//
//			NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
//				.setPriority(NotificationCompat.PRIORITY_DEFAULT) // high will be over interrupting once api level +21
//				.setSmallIcon(R.drawable.ic_notification)
//				.setContentTitle(mBinder.getRhythmName())
//				.setContentText(text)
//				.setContentIntent(pendingIntent)
//				.setOngoing(true)
//				.addAction(R.drawable.ic_notification_stop_playing, getResources().getText(R.string.notification_action_stop), pendingStopIntent);
//
//			if (mBinder.isPlaying()) {
//				Intent pausePlaying = new Intent(getApplicationContext(), BeatTrackerService.class);
//				pausePlaying.putExtra(NOTIFICATION_ACTION_PAUSE_PLAYING, true);
//				// NOTE: different request code here, otherwise uses the same intent as for stop and it doesn't work
//				PendingIntent pendingPauseIntent = PendingIntent.getService(getApplicationContext(), 1, pausePlaying, PendingIntent.FLAG_UPDATE_CURRENT);
//				builder.addAction(android.R.drawable.ic_media_pause, getResources().getText(R.string.notification_action_pause), pendingPauseIntent);
//			}
//
//			NotificationManager notMan = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//			notMan.notify(FOREGROUND_NOTIFICATION_ID, builder.build());
//		}
//	}

	private String getNotificationText(int currentRhythmIteration, PlayerState playerState, boolean isPlaying) {
		if (playerState.getRepeatOption() == RepeatOption.Infinite) {
			return getString(isPlaying ? R.string.notification_playing : R.string.notification_paused);
		}
		else {
			return getString(isPlaying ? R.string.notification_playing_n_of_n : R.string.notification_paused_n_of_n,
					currentRhythmIteration+1,   // human readable
                    playerState.getNTimes());
		}
	}

	/**
	 * Called by the binder when BeatTracker stops itself because it
	 * got to the end of a set of iterations
	 */
	void onPlayStopped() {
        mServiceInUse = false;              // try to get ahead of anything that might query for alive
        stopForeground(true);
        stopSelf();
	}
	
	/**
	 * Called by the binder when a pause has happened (either from user action or loss of audio focus)
	 */
	void onPlayPaused(int currentRhythmIteration, PlayerState playerState) {
        updateNotification(mBinder.getRhythmName(), currentRhythmIteration, playerState, false);
	}

    void onPlayStarted(PlayerState playerState) {
        // see https://developer.android.com/guide/components/services.html "Running a service in the foreground"
        AndroidResourceManager.logd(LOG_TAG, "onPlayStarted: starting in foreground");
        startForeground(FOREGROUND_NOTIFICATION_ID, makeNotification(getNotificationText(0, mBinder.getPlayerState(), true), mBinder.getRhythmName())); // starting so assume playing

        updateNotification(mBinder.getRhythmName(), 0, playerState, true);
    }

    /**
	 * Called by the binder when BeatTracker informs the current iteration has incremented
	 * this class will init another notification if appropriate
	 * @param isPlaying 
	 * @param playerState 
	 */
	void onPlayIterationsChanged(int currentRhythmIteration, PlayerState playerState, boolean isPlaying) {
        updateNotification(mBinder.getRhythmName(), currentRhythmIteration, playerState, isPlaying);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
        AndroidResourceManager.logv(LOG_TAG, "onBind: called");
		mSelfCheckHandler.removeCallbacksAndMessages(null);
		mServiceInUse = true;
		return mBinder;
	}

	@Override
	public void onRebind(Intent intent) {
        AndroidResourceManager.logv(LOG_TAG, "onRebind: called");
		mSelfCheckHandler.removeCallbacksAndMessages(null);
		mServiceInUse = true;
	}

	@Override
	public boolean onUnbind(Intent intent) {
        AndroidResourceManager.logv(LOG_TAG, "onUnbind: called");
		mServiceInUse = false;

		// do a self check to see if should stop the service or throw up notifications
		// after a short delay to give stuff time to bind again
		initSelfCheck();

		return true;
	}

    @Override
    public void onTaskRemoved(Intent rootIntent) {  // called when app is removed from recents
        if (mBinder.isPlaying()) {
            AndroidResourceManager.logd(LOG_TAG, "onTaskRemoved: stop playing");
            mBinder.stopPlay();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
	public void onDestroy() {
        mServiceInUse = false;
        mBinder.onServiceDied();
		super.onDestroy();
		// TODO remove debug
        AndroidResourceManager.logd(LOG_TAG, "onDestroy:");
	}

	@Override
	public void onLowMemory() {
		// TODO remove warning
		AndroidResourceManager.logw(LOG_TAG, "onLowMemory: check to stop");
		selfStopCheck();
		super.onLowMemory();
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onTrimMemory(int level) {
		// TODO remove warning
		AndroidResourceManager.logw(LOG_TAG, "onTrimMemory: check to stop");
		selfStopCheck();
		super.onTrimMemory(level);
	}

}
