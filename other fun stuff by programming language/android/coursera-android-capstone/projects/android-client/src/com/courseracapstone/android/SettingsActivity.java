package com.courseracapstone.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.common.GiftsSectionType;

/**
 * The activity that launches the preferences fragment
 * @author xxx xxx
 *
 */
public class SettingsActivity extends AbstractPotlatchActivity implements OnSharedPreferenceChangeListener {

	private static final String LOG_TAG = "Potlatch-"+SettingsActivity.class.getSimpleName();
	
	private SettingsFragment mSettingsFragment;
	
	@Override
	protected void onBindToService() {
		super.onBindToService();
		
		setContentView(R.layout.activity_settings);
		
		mSettingsFragment = SettingsFragment.newInstance(mDownloadBinder);
		
		// Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mSettingsFragment)
                .commit();
	}
	
	/**
	 * Called directly from the binder while the activity is visible (not
	 * stopped) and if not, then onResume() tests for a message (in the
	 * superclass) and calls this method if anything is pending
	 */
	@Override
	public void handleBinderMessage(int what, boolean onTime, GiftsSectionType giftsType) {

		String errm = null;
		
		switch (what) {
		case DownloadBinder.USER_PREFS_UPDATED: 
			Log.d(LOG_TAG, "handleBinderMessage: changed ok");
			break;
			
		case DownloadBinder.ERROR_BAD_REQUEST: 
			Log.d(LOG_TAG, "handleBinderMessage: bad request - probably the user changed the value on another device!!");
			errm = getResources().getString(R.string.pref_error_bad_request);
			
		case DownloadBinder.ERROR_AUTH_TOKEN_STALE: 
		case DownloadBinder.ERROR_NETWORK: 
		case DownloadBinder.ERROR_NOT_FOUND: 
		case DownloadBinder.ERROR_UNKNOWN: 
			
			// all falling through to here, don't overwrite bad request
			if (what != DownloadBinder.ERROR_BAD_REQUEST) {
				Log.d(LOG_TAG, "handleBinderMessage: an error forces reset and reload = "+what);
				errm = getResources().getString(R.string.pref_error_other);
			}
			
			Toast.makeText(this, errm, Toast.LENGTH_LONG).show();

			resetAndReload();
			
			break;
			
		default:
			Log.e(LOG_TAG, "handleBinderMessage: got a message to handle, and passing to superclass=" + what);
			super.handleBinderMessage(what, onTime, giftsType);
			break;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(this)
			.registerOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(this)
			.unregisterOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
		
		Resources res = getResources();
		
		if (AbstractPotlatchActivity.checkOnlineFeedbackIfNot(this)) {
			
			try {
				// filter content
				if (key.equals(res.getString(R.string.prefkey_filter_content))) {
					Log.d(LOG_TAG, String.format("preferenceChange: key=%s, value=%s", key,
							sharedPrefs.getBoolean(key, false)));
					
					mDownloadBinder.setUserFilterContent(sharedPrefs.getBoolean(key, false));
					
				}
				// update interval
				else if (key.equals(res.getString(R.string.prefkey_interval))) {
					
					String intervalStr = sharedPrefs.getString(key, Long.toString(-1L));
					Log.d(LOG_TAG, String.format("preferenceChange: key=%s, value=%s", key, intervalStr));
	
					mDownloadBinder.setUserUpdateInterval(Long.valueOf(intervalStr));
					
					// put the new value into the pref
					mSettingsFragment.updateIntervalPrefSummary(key, res);				
				}
				else {
					Log.e(LOG_TAG, "preferenceChange: unknown key="+key);
				}
			}
			catch (AuthTokenStaleException e) {
				// handleBinderMessages() will deal with it asap, in the meantime, reset the values
				resetAndReload();
			}
		}
		else {
			// reload the activity
			resetAndReload();
		}
	}

	/**
	 * reset the changes (after failure to update, perhaps network error)
	 */
	private void resetAndReload() {

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		// de-register so don't get any callbacks
		sharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
		
		mDownloadBinder.setSharedPrefsToDownloadedValues();
		
	    Intent intent = getIntent();
	    // possibly this isn't the right place for it, it comes after the finish... this one is superflous 
	    // perhaps
	    overridePendingTransition(0, 0);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
	    finish();

	    overridePendingTransition(0, 0);
	    startActivity(intent);
	    overridePendingTransition(0, 0);
	}

	@Override
	protected void fabButtonPressed() {}

}
