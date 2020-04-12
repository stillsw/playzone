package com.stillwindsoftware.keepyabeat.gui;

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;

public class LocaleUtils {

	private static final String LOG_TAG = "KYB-"+LocaleUtils.class.getSimpleName();

	/**
	 * Look for the language code that's set in prefs. If none, see if the config locale
	 * is included in the languages we have, and if not return the default (English).
	 * @param activity
	 * @param prefs
	 * @return
	 */
	public static String getDefaultLanguageCode(Activity activity, SharedPreferences prefs) {

		String chosenLang = prefs.getString(SettingsManager.LANGUAGE, null);
		
		// no value saved look for default locale
		if (chosenLang == null) {
			Resources standardResources = activity.getBaseContext().getResources();
			chosenLang = standardResources.getConfiguration().locale.getLanguage();
		}
		
		// search the language codes defined for the app and if find a match update the spinner index
		String[] langCodes = activity.getResources().getStringArray(R.array.language_codes);
		for (int i = 0; i < langCodes.length; i++) {
			if (chosenLang.equals(new Locale(langCodes[i], "", "").getLanguage())) {
				return chosenLang;
			}
		}
		
		return langCodes[0];
	}
	
	/**
	 * If the language is changed, update the stored pref and restart the activity with
	 * the new value
	 * @param activity
	 * @param pos
	 * @param priorLangCode 
	 */
	public static void resetLanguage(Activity activity, int pos, String priorLangCode) {
		SharedPreferences prefs = activity.getSharedPreferences(SettingsManager.PREFS_NAME, Activity.MODE_PRIVATE);
		String selectedLang = activity.getResources().getStringArray(R.array.language_codes)[pos];
		
		if (!selectedLang.equals(priorLangCode)) { 
			Editor edit = prefs.edit(); 
			edit.putString(SettingsManager.LANGUAGE, selectedLang);
			edit.commit();
			
			// get the activity to reload with the changed lang
			reload(activity);
		}
	}

	public static void reload(Activity activity) {

	    Intent intent = activity.getIntent();
	    // possibly this isn't the right place for it, it comes after the finish... this one is superflous 
	    // perhaps
	    activity.overridePendingTransition(0, 0);
	    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
	    activity.finish();

	    activity.overridePendingTransition(0, 0);
	    activity.startActivity(intent);
	    activity.overridePendingTransition(0, 0);
	}

	/**
	 * Tests the current locale against the stored pref to see if it should
	 * be changed
	 * @param activity
	 */
	public static String setActivityLocale(Activity activity) {
		
		String langCode = getDefaultLanguageCode(activity, activity.getSharedPreferences(SettingsManager.PREFS_NAME, Activity.MODE_PRIVATE));
		
		Resources standardResources = activity.getBaseContext().getResources();
		String configLang = standardResources.getConfiguration().locale.getLanguage();
		if (!langCode.equals(configLang)) {
			AndroidResourceManager.logd(LOG_TAG, String.format("setActivityLocale: was=%s change to=%s", configLang, langCode));
			
		    AssetManager assets = standardResources.getAssets();
		    DisplayMetrics metrics = standardResources.getDisplayMetrics();
		    Configuration config = new Configuration(standardResources.getConfiguration());

		    config.locale = new Locale(langCode, config.locale.getCountry());

		    Resources defaultResources = new Resources(assets, metrics, config);
		    activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
		}
		
		return langCode;
	}

    /**
     * Returns true if the string exists in any language
     * @param str
     * @param resIds
     * @param defaultRes
     * @return
     */
    public static boolean stringExistsInAnyLanguage(String str, Resources defaultRes, int ... resIds) {
        final Configuration defaultConfig = defaultRes.getConfiguration();
        final String defaultCountry = defaultConfig.locale.getCountry();
        final String defaultLang = defaultConfig.locale.getLanguage();
        final AssetManager assets = defaultRes.getAssets();
        final DisplayMetrics metrics = defaultRes.getDisplayMetrics();
        String[] langCodes = defaultRes.getStringArray(R.array.language_codes);

        try {
            for (int i = 0; i < langCodes.length; i++) {

                Configuration config = new Configuration(defaultConfig);
                config.locale = new Locale(langCodes[i], config.locale.getCountry());

                defaultRes.updateConfiguration(config, metrics);

                for (int j = 0; j < resIds.length; j++) {
                    if (str.equalsIgnoreCase(defaultRes.getString(resIds[j]))) {
                        return true;
                    }
                }
            }
        } finally {
            // always reset the resources
            Configuration config = new Configuration(defaultConfig);
            config.locale = new Locale(defaultLang, defaultCountry);
            defaultRes.updateConfiguration(config, metrics);
        }
        return false;
    }
}
