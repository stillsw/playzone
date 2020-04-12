package com.stillwindsoftware.keepyabeat.player.backend;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;

/**
 * Service that rhythms dialog_simple_list uses to make images, it also caches them
 * so that reloads of the cursor don't need images to be created again.
 * It's a bound service.
 */
public class RhythmImageService extends Service {

	private static final String LOG_TAG = "KYB-" + RhythmImageService.class.getSimpleName();
	private ImageLoadBinder mBinder;
    private boolean mIsDead = false;

	/**
	 * Make an intent that will start this service if supplied to startService()
	 * as a parameter.
	 * 
	 * @param context
	 */
	public static Intent makeIntent(Context context) {
		return new Intent(context, RhythmImageService.class);
	}

	@Override
	public IBinder onBind(Intent intent) {
        AndroidResourceManager.logd(LOG_TAG, "onBind:");
        if (mBinder == null) {
            mBinder = new ImageLoadBinder(this);
        }
		return mBinder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
        mIsDead = true;
        AndroidResourceManager.logd(LOG_TAG, "onDestroy:");
		mBinder.destroy();
	}

    public boolean isDead() {
        return mIsDead;
    }
}
