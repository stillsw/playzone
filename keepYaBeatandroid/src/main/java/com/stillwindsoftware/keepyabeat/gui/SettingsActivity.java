package com.stillwindsoftware.keepyabeat.gui;

import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.google.ads.consent.ConsentInformation;
import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerService;

/**
 * Created by tomas on 13/07/15.
 */
public class SettingsActivity extends KybActivity {

    private static final String LOG_TAG = "KYB-"+SettingsActivity.class.getSimpleName();
    public static final String IS_SETTINGS_OPEN_ACTION = "com.stillwindsoftware.keepyabeat.gui.SETTINGS_OPEN";

    SettingsManager mSettingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_layout);

        // add the toolbar
        Toolbar toolbar = findViewById(R.id.kyb_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.settingsTitle);

        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragmentcontainer, new SettingsFragment())
                .commit();
        fragmentManager.executePendingTransactions();

        mSettingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager();
    }

    @Override
    protected String getReceiverFilterName() {
        return IS_SETTINGS_OPEN_ACTION;
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener/*, ServiceConnection*/ {

        private static final String REDEFAULT_KEY = "redefault_btn";
        private static final String CONSENT_KEY = "consent_btn";

//        private BeatTrackerBinder mBeatTrackerComm;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // use the named prefs
            getPreferenceManager().setSharedPreferencesName(SettingsManager.PREFS_NAME);
            addPreferencesFromResource(R.xml.settings);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
            Preference button = findPreference(REDEFAULT_KEY);
            button.setOnPreferenceClickListener(this);

            button = findPreference(CONSENT_KEY);

            SettingsActivity activity = (SettingsActivity) getActivity();
            KybApplication application = activity.getResourceManager().getApplication();

            if (application.isPremium()
                    || !ConsentInformation.getInstance(activity).isRequestLocationInEeaOrUnknown()) {
                button.setEnabled(false);
                AndroidResourceManager.logd(LOG_TAG, "onResume: either premium or location not EU so don't need consent");
            }
            else {
                button.setOnPreferenceClickListener(this);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            // most prefs are for how to draw rhythms, their values are cached by the settings
            // manager so let it know to re-read them
            SettingsActivity activity = (SettingsActivity) getActivity();
            activity.mSettingsManager.readSettings();
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            // onResume() puts this class as a listener for the reset defaults click
            AndroidResourceManager.logd(LOG_TAG, "onPreferenceClick: for "+preference.getKey());
            if (preference.getKey().equals(REDEFAULT_KEY)) {
                ((SettingsActivity) getActivity()).mSettingsManager.redefaultSettings();
                LocaleUtils.reload(getActivity()); // restarts the activity and reloads the prefs
            }
            else if (preference.getKey().equals(CONSENT_KEY)) {
                ((SettingsActivity) getActivity()).showAdsConsentForm();
            }
            return true;
        }

    }
}
