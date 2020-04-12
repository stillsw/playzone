package com.courseracapstone.android.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.courseracapstone.android.AbstractPotlatchActivity;
import com.courseracapstone.android.GiftsActivity;
import com.courseracapstone.android.R;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * A threaded download hybrid start/bound service which handles a number of things
 * through its binder, see DownloadBinder.
 * 
 * @author xxx xxx
 *
 */
public class DataDownloadService extends Service {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	// single id to be able to update notifications
	private static final int NOTIFICATION_ID = 1;

	// the binder that does most of the work
	private DownloadBinder mBinder;

	/**
	 * Make an intent that will start this service if supplied to startService()
	 * as a parameter.
	 * 
	 * @param context
	 * @param handler
	 */
	public static Intent makeIntent(Context context) {
		return new Intent(context, DataDownloadService.class);
	}

	/**
	 * Utility method that checks for online
	 * @return
	 */
	public boolean isNetworkOnline() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected()) {
			return true;
		}
		return false;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		mBinder = new DownloadBinder(this);
	}

	/**
	 * A call to start should be followed by a bind
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	/**
	 * The binder calls this when a user notice update has happened and there's no activity
	 * open to receive it (the broadcast was not caught). 
	 * @param text
	 */
	public void sendNotification(ArrayList<String> texts) {
		
		// intent to re-open potlatch that also sends intent flag to clear the notification
		Intent openPotlatch = new Intent(getApplicationContext(), GiftsActivity.class);
		openPotlatch.putExtra(DownloadBinder.CLEAR_NOTICES, true);
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, openPotlatch, Intent.FLAG_ACTIVITY_NEW_TASK);
		
		String title = (String) getResources().getText(R.string.user_notice_msg);
		
		NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

		// title and list for big view
		inboxStyle.setBigContentTitle(title);
		for (String line : texts) {
		    inboxStyle.addLine(line);
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
			.setOngoing(true)
			.setWhen(System.currentTimeMillis())
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle(title)
			.setTicker(title)
			.setStyle(inboxStyle)
			.setContentIntent(pendingIntent);

		NotificationManager notMan = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notMan.notify(NOTIFICATION_ID, builder.build());
	}

	
	/**
	 * Called during onBind()/onRebind()onDestroy()
	 * to close any open notifications
	 */
	void closeNotifications() {
		NotificationManager notMan = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notMan.cancelAll();
	}

	/**
	 * Overloaded utility method that just tries to get a response
	 * @param timeout
	 * @return
	 */
	boolean sendBroadcastTestUiPresence(int timeout) {
		return sendBroadcastTestUiPresence(DownloadBinder.TEST_FOR_AWAKE, true, timeout);
	}
	/**
	 * Utility method to send a broadcast to the ui, waits for a response and returns the result
	 * if passed. It's always called in a non-ui thread, so no problem that it waits for a result (which
	 * is expected to be fairly fast). A timeout ensures it doesn't block for any reason for too long.
	 * 
	 * @param broadCastParam
	 * @param paramValue
	 * @param timeout
	 * @return
	 */
	boolean sendBroadcastTestUiPresence(String broadCastParam, boolean paramValue, int timeout) {
		
		final CountDownLatch latch = new CountDownLatch(1);
		final AwaitResult result = new AwaitResult();
		
		// send an ordered broadcast, and if get result ok, assume the activity will handle it
		// with a call to getUserNotice() when it wants it
		Intent intent = new Intent(AbstractPotlatchActivity.DATA_RECEIVER_ACTION);
		intent.putExtra(broadCastParam, paramValue);
		intent.putExtra(DownloadBinder.TIME_SENT, System.currentTimeMillis());
		intent.setPackage(getPackageName());
		sendOrderedBroadcast(
				intent,
				null,
				new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						
						result.isOk = (getResultCode() == Activity.RESULT_OK);
						Log.d(LOG_TAG, "sendBroadcastTestUiPresence: onReceive at ("+(new Date())+"), res="+result.isOk);
						latch.countDown();
					}
				}, 
				null, 0, null, null);
		
		// don't wait long for it
		try {
			if (latch.await(timeout, TimeUnit.MILLISECONDS)) { 
				Log.d(LOG_TAG, "sendBroadcastTestUiPresence: latch has counted down ("+(new Date())+"), res="+result.isOk);
				return result.isOk;
			}
			else {
				Log.w(LOG_TAG, "sendBroadcastTestUiPresence: timeout expired ("+(new Date())+"), have to set a longer time for it");
				return false;
			}
		} catch (InterruptedException e) {
			Log.w(LOG_TAG, "sendBroadcastTestUiPresence: await was interrupted unexpectedly ("+(new Date())+"), return false this time");
			return false;
		}
	}

	
	@Override
	public IBinder onBind(Intent intent) {
		closeNotifications();
		return mBinder;
	}

	@Override
	public void onRebind(Intent intent) {
		closeNotifications();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		return true; // so onRebind it called
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mBinder.destroy();
		closeNotifications();
	}

	/**
	 * Android says it's low on memory, use it as a signal to remove unnecessary data
	 */
	@Override
	public void onTrimMemory(int level) {
		mBinder.stripdownCaches(level);
		super.onTrimMemory(level);
	}

	/**
	 * Used for sending the ordered broadcast, to receive the result in the calling thread
	 *
	 */
	private static class AwaitResult {
		private boolean isOk = false;
	}
}
