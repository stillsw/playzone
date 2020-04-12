package com.stillwindsoftware.keepyabeat.platform;

import java.util.Collection;
import java.util.HashSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;

import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListenerTarget;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;

public class SettingsManager extends PersistentStatesManager implements LibraryListenerTarget {

    private static final String LOG_TAG = "KYB-"+SettingsManager.class.getSimpleName();
	
	// constants needed in >1 android class
	public static final String LANGUAGE = "language";
	public static final String PREFS_NAME = "KybPrefs";

	private static final String SHOW_WELCOME_PREF_KEY = "welcome";

    private static final String RHYTHMS_LIST_SEARCH_TEXT = "rhytmsListSearchText";
    private static final String RHYTHM_LIST_SEARCH_TAGS = "rhytmsListSearchTags";

    private static final String IS_EDITOR_ACTIVE = "isEditorActive";
    private static final String GRADLE_VERSION_CODE = "gradleInstalledVersionCode";

    private static final String USE_STREAMING_BEAT_TRACKER_KEY = "useStreamingBeatTracker";

    private static final String SEEN_SOUND_MODIFY_WARNING_IN_PARTS_DIALOG_PREF_KEY = "hasSeenSoundModifyInPartsDialogWarning";
    private static final String KEEP_SCREEN_AWAKE_WHILE_PLAYING_KEY = "keepScreenAwake";

    private static final String AD_CONSENT_REQUESTED_TIME_KEY = "adConsentRequestedTime";

    private Library mLibrary;
	private SharedPreferences mSharedPrefs;
    private boolean mIsEditorActive;

    private int mGradleVersionCodeInstalled;
    private boolean hasSeenSoundModifyInPartsDialogWarning, keepScreenAwakeWhilePlaying;
    private long mAdConsentRequestedTime = -1L;

    public SettingsManager(Context context) {
		mSharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		mLibrary = ((KybApplication)context).getResourceManager().getLibrary();
		
		// read the user settings that can be changed
		readSettings();
        AndroidResourceManager.logd(LOG_TAG, "<init>: editor is active = "+mIsEditorActive);
    }

    /**
     * Settings that can be set from the preferences screen are re-read whenever a change
     * occurs and the values are kept for reading
     * Called by the constructor, and also from SettingsActivity to notify of a change
     *
     * Instead of removing values to default them, this method writes the defaults. This
     * way the settings screen gets the correct defaults without having to put those values
     * either there or in a resource.
     */
	public void readSettings() {
		// settings
		showWelcomeDialog = readStateFromStore(SHOW_WELCOME_PREF_KEY, true);
		animatePlayedBeats = readStateFromStore(ANIMATE_PLAYED_BEATS, true);
		playedBeatMoveUpwards = readStateFromStore(PLAYED_BEATS_MOVE_UPWARDS, false);
		playedBeatsKeepBouncing = readStateFromStore(PLAYED_BEATS_KEEP_BOUNCING, false);
		playedBeatMoveDistance = readStateFromStore(PLAYED_BEATS_MOVE_DISTANCE, Integer.parseInt(DEFAULT_PLAYED_BEATS_MOVE_DISTANCE));
        // speed is the inverse (so slider says faster to the right)
		playedBeatMoveSpeed = readStateFromStore(PLAYED_BEATS_MOVE_SPEED, Integer.parseInt(DEFAULT_PLAYED_BEATS_MOVE_SPEED));
		showSoundRipples = false; // readStateFromStore(SHOW_SOUND_RIPPLES, true);
		showProgressIndicator = readStateFromStore(SHOW_PROGRESS_INDICATOR, true);
		showPlayingFullBeatColour = readStateFromStore(SHOW_PLAYING_FULL_BEAT_COLOUR, true);
		showPlayingSubBeatColour = readStateFromStore(SHOW_PLAYING_SUB_BEAT_COLOUR, true);
        playingFullBeatColour = new AndroidColour(readStateFromStore(PLAYING_FULL_BEAT_COLOUR, Color.parseColor(DEFAULT_PLAYING_FULL_BEATS_COLOUR)));
        playingSubBeatColour = new AndroidColour(readStateFromStore(PLAYING_SUB_BEAT_COLOUR, Color.parseColor(DEFAULT_PLAYING_SUB_BEATS_COLOUR)));
		drawBeatNumbers = readStateFromStore(DRAW_BEAT_NUMBERS, true);
		drawBeatNumbersAboveBeats = readStateFromStore(DRAW_BEAT_NUMBERS_ABOVE_BEATS, true);
        mIsEditorActive = readStateFromStore(IS_EDITOR_ACTIVE, false);
        mGradleVersionCodeInstalled = readStateFromStore(GRADLE_VERSION_CODE, -1);
        hasSeenSoundModifyInPartsDialogWarning = readStateFromStore(SEEN_SOUND_MODIFY_WARNING_IN_PARTS_DIALOG_PREF_KEY, false);
        keepScreenAwakeWhilePlaying = readStateFromStore(KEEP_SCREEN_AWAKE_WHILE_PLAYING_KEY, false);
        silentPartBeatDrawn = readStateFromStore(SILENT_PART_BEAT_IS_DRAWN, SILENT_PART_BEAT_IS_DRAWN_ALWAYS);
        isDrawSilentPartBeats = SILENT_PART_BEAT_IS_DRAWN_ALWAYS.equals(silentPartBeatDrawn);
        isDrawPlayedSilentPartBeats = isDrawSilentPartBeats || SILENT_PART_BEAT_IS_DRAWN_PLAYED.equals(silentPartBeatDrawn);
        mAdConsentRequestedTime = readStateFromStore(AD_CONSENT_REQUESTED_TIME_KEY, -1L);
	}

    @Override
    public String getListenerKey() {
        return "settings";
    }

    /**
     * Returns the value, and if it doesn't exist it writes the default first
     * @param stateName the name
     * @param defaultStr a default
     * @return what's in the store or the default
     */
    private String readStateFromStore(String stateName, String defaultStr) {
        if (!mSharedPrefs.contains(stateName)) {
            writeStateToStore(stateName, defaultStr);
            return defaultStr;
        }
        return mSharedPrefs.getString(stateName, defaultStr);
    }

    /**
     * Returns the value, and if it doesn't exist it writes the default first
     * @param stateName the name
     * @param defaultInt a default
     * @return what's in the store or the default
     */
    private int readStateFromStore(String stateName, int defaultInt) {
        if (!mSharedPrefs.contains(stateName)) {
            writeStateToStore(stateName, defaultInt);
            return defaultInt;
        }
        return mSharedPrefs.getInt(stateName, defaultInt);
    }

    /**
     * Returns the value, and if it doesn't exist it writes the default first
     * @param stateName the name
     * @param defaultLong a default
     * @return what's in the store or the default
     */
    private long readStateFromStore(String stateName, long defaultLong) {
        if (!mSharedPrefs.contains(stateName)) {
            writeStateToStore(stateName, defaultLong);
            return defaultLong;
        }
        return mSharedPrefs.getLong(stateName, defaultLong);
    }

    /**
     * Returns the value, and if it doesn't exist it writes the default first
     * @param stateName the name
     * @param defaultBool a default
     * @return what's in the store or the default
     */
    public boolean readStateFromStore(String stateName, boolean defaultBool) {
        if (!mSharedPrefs.contains(stateName)) {
            writeStateToStore(stateName, defaultBool);
            return defaultBool;
        }
		return mSharedPrefs.getBoolean(stateName, defaultBool);
	}
	
	private void writeStateToStore(String stateName, String stringRepresentation) {
		Editor editor = mSharedPrefs.edit();
		if (stringRepresentation == null && mSharedPrefs.contains(stateName)) {
			editor.remove(stateName);
		}
		else {
			editor.putString(stateName, stringRepresentation);
		}
		editor.apply();
	}

    public void writeStateToStore(String stateName, boolean boolValue) {
        Editor editor = mSharedPrefs.edit();
        editor.putBoolean(stateName, boolValue);
        editor.apply();
    }

    private void writeStateToStore(String stateName, int intValue) {
        Editor editor = mSharedPrefs.edit();
        editor.putInt(stateName, intValue);
        editor.apply();
    }

    private void writeStateToStore(String stateName, long longValue) {
        Editor editor = mSharedPrefs.edit();
        editor.putLong(stateName, longValue);
        editor.apply();
    }

    /**
	 * Overrides super class to lazy init the playerstate
	 */
	@Override
	public PlayerState getPlayerState() {
		if (playerState == null) {
			String repeatOptionStr = readStateFromStore(REPEAT, RepeatOption.Infinite.name());
			RepeatOption repeatOption = RepeatOption.valueOf(repeatOptionStr);
			String nTimesStr = readStateFromStore(N_TIMES, "2");
			int nTimes = Integer.parseInt(nTimesStr);
			String openRhythmKey = readStateFromStore(PLAYED_RHYTHM_KEY, null);
			boolean volumesActive = readStateFromStore(PLAYER_VOLUMES, true);
			boolean rhythmsListOpen = readStateFromStore(RHYTHM_LIST_OPEN, true);
			String selectedBeatTypeKey = readStateFromStore(SELECTED_BEAT_TYPE, BeatTypes.DefaultBeatTypes.normal.getKey());
			playerState = new PlayerState(this, mLibrary, volumesActive, repeatOption, nTimes, openRhythmKey, rhythmsListOpen, selectedBeatTypeKey);
		}
		return super.getPlayerState();
	}
	
	@Override
	public void redefaultSettings() {

        Editor editor = mSharedPrefs.edit();
        editor.remove(SHOW_WELCOME_PREF_KEY);
        editor.remove(ANIMATE_PLAYED_BEATS);
        editor.remove(PLAYED_BEATS_MOVE_UPWARDS);
        editor.remove(PLAYED_BEATS_KEEP_BOUNCING);
        editor.remove(PLAYED_BEATS_MOVE_DISTANCE);
        editor.remove(PLAYED_BEATS_MOVE_SPEED);
        editor.remove(SHOW_SOUND_RIPPLES);
        editor.remove(SHOW_PROGRESS_INDICATOR);
        editor.remove(SHOW_PLAYING_FULL_BEAT_COLOUR);
        editor.remove(SHOW_PLAYING_SUB_BEAT_COLOUR);
        editor.remove(PLAYING_FULL_BEAT_COLOUR);
        editor.remove(PLAYING_SUB_BEAT_COLOUR);
        editor.remove(DRAW_BEAT_NUMBERS);
        editor.remove(DRAW_BEAT_NUMBERS_ABOVE_BEATS);
        editor.remove(USE_STREAMING_BEAT_TRACKER_KEY);
        editor.remove(SEEN_SOUND_MODIFY_WARNING_IN_PARTS_DIALOG_PREF_KEY);
        editor.remove(KEEP_SCREEN_AWAKE_WHILE_PLAYING_KEY);
        editor.remove(SILENT_PART_BEAT_IS_DRAWN);
        editor.remove(AD_CONSENT_REQUESTED_TIME_KEY); // not needed for user, but helpful for testing consent

        editor.apply();

        readSettings();
	}

	@Override
	public void playerStateChanged(boolean fireListeners) {

        // don't trigger a db lookup unnecessarily
        if (playerState.hasBeatType()) {
            writeStateToStore(SELECTED_BEAT_TYPE, playerState.getBeatType().getKey());
        }
		writeStateToStore(REPEAT, playerState.getRepeatOption().name());
		writeStateToStore(N_TIMES, Integer.toString(playerState.getNTimes()));
		writeStateToStore(PLAYER_VOLUMES, playerState.isVolumesActive());
		writeStateToStore(PLAYED_RHYTHM_KEY, playerState.getRhythmKey());
        writeStateToStore(RHYTHM_LIST_OPEN, playerState.isRhythmsListOpen());

        if (fireListeners) {
            ListenerSupport listenerSupport = mLibrary.getResourceManager().getListenerSupport();
            listenerSupport.fireItemChanged(listenerSupport.getNextId(), this.getListenerKey(), ListenerSupport.NON_SPECIFIC, null);
        }
	}

	@Override
	public void setRhythmSearchCriteria(String searchText, HashSet<Tag> tags) {
		// note, tag keys have their own method
        writeStateToStore(RHYTHMS_LIST_SEARCH_TEXT, searchText);
	}

    public void setRhythmSearchTagKeys(HashSet<String> tagKeys) {
        Editor editor = mSharedPrefs.edit();
        editor.putStringSet(RHYTHM_LIST_SEARCH_TAGS, tagKeys);
        editor.apply();
    }

    public HashSet<String> getRhythmSearchTagKeysAsSet() {
        Collection<? extends String> keys = mSharedPrefs.getStringSet(RHYTHM_LIST_SEARCH_TAGS, null);
        HashSet<String> ret = new HashSet<>();
        if (keys != null) {
            ret.addAll(keys);
        }
        return ret;
    }

    @Override
	public String[] getRhythmSearchTagKeys() {
		// prev method is used instead
		return null;
	}

	@Override
	public String getRhythmSearchText() {
		return readStateFromStore(RHYTHMS_LIST_SEARCH_TEXT, null);
	}

    public boolean isEditorActive() {
        return mIsEditorActive;
    }

    public void setEditorActive(boolean editorActive) {
        this.mIsEditorActive = editorActive;
        writeStateToStore(IS_EDITOR_ACTIVE, editorActive);
    }

    public int getGradleVersionCodeInstalled() {
        return mGradleVersionCodeInstalled;
    }

    public void setGradleVersionCodeInstalled(int versionInstalled) {
        this.mGradleVersionCodeInstalled = versionInstalled;
        writeStateToStore(GRADLE_VERSION_CODE, versionInstalled);
    }

    /**
     * Set from the welcome screen, the prefs screen sets the preference directly
     * @param showWelcomeDialog the setting
     */
	@Override
	public void setShowWelcomeDialog(boolean showWelcomeDialog) {
        this.showWelcomeDialog = showWelcomeDialog;
        writeStateToStore(SHOW_WELCOME_PREF_KEY, showWelcomeDialog);
	}

	@Override
	public StringBuffer getDump() {
//		AndroidGuiManager.logTrace(AndroidGuiManager.TO_IMPLEMENT, this.getClass().getSimpleName());
		return null;
	}

    @Override
    public String getKybState() {
//        AndroidGuiManager.logTrace(AndroidGuiManager.TO_IMPLEMENT, this.getClass().getSimpleName());
        return null;
    }

    @Override
    public void setKybState(String licState) {
//        AndroidGuiManager.logTrace(AndroidGuiManager.TO_IMPLEMENT, this.getClass().getSimpleName());
    }

    public boolean hasSeenSoundModifyInPartsDialogWarning() {
        return hasSeenSoundModifyInPartsDialogWarning;
    }

    public void setSeenSoundModifyInPartsDialogWarning() {
        this.hasSeenSoundModifyInPartsDialogWarning = true;
        writeStateToStore(SEEN_SOUND_MODIFY_WARNING_IN_PARTS_DIALOG_PREF_KEY, true);
    }

    public boolean isKeepScreenAwakeWhilePlaying() {
        return keepScreenAwakeWhilePlaying;
    }

    public void setKeepScreenAwakeWhilePlaying(boolean keepScreenAwakeWhilePlaying) {
        this.keepScreenAwakeWhilePlaying = keepScreenAwakeWhilePlaying;
        writeStateToStore(KEEP_SCREEN_AWAKE_WHILE_PLAYING_KEY, keepScreenAwakeWhilePlaying);
    }

    public Long getAdConsentRequestedTime() {
        return mAdConsentRequestedTime;
    }

    public void setAdConsentRequestedTime(long adConsentRequestedTime) {
        this.mAdConsentRequestedTime = adConsentRequestedTime;
        writeStateToStore(AD_CONSENT_REQUESTED_TIME_KEY, adConsentRequestedTime);
    }
}
