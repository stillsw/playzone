package com.courseracapstone.android;


import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.View;

import com.courseracapstone.android.service.DownloadBinder;

/**
 * A dialog shown to offer the preferences settings
 * 
 * @author xxx xxx
 * 
 */
public class SettingsFragment extends PreferenceFragment {

	private static final String LOG_TAG = "Potlatch-"+SettingsFragment.class.getSimpleName();
	
	/**
	 * Called from any activity
	 * 
	 * @param downloadBinder
	 * @return
	 */
	public static SettingsFragment newInstance(DownloadBinder downloadBinder) {
		Bundle bundle = new Bundle();
		bundle.putBinder(GiftsActivity.PARAM_BINDER, downloadBinder);

		SettingsFragment fragment = new SettingsFragment();
		fragment.setArguments(bundle);

		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			@SuppressWarnings("unused")
			AbstractPotlatchActivity a = (AbstractPotlatchActivity) getActivity();
		} catch (ClassCastException e) {
			Log.e(LOG_TAG, "onAttach: what is this activity? "+activity.getClass());
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		
		Resources res = getResources();
		updateIntervalPrefSummary(null, res);
		
		super.onViewCreated(view, savedInstanceState);
	}
	
	/**
	 * Set once when loaded, and then updated if there's a change
	 * @param val
	 * @param res
	 */
	public void updateIntervalPrefSummary(String val, Resources res) {

		ListPreference intervalPref = (ListPreference) findPreference(res.getString(R.string.prefkey_interval));

		// set the summary, look for the matching entry for the key
		intervalPref.setSummary(res.getString(R.string.pref_interval_summary,
				intervalPref.getEntry()));
		
//		getView().invalidate();
	}


}
