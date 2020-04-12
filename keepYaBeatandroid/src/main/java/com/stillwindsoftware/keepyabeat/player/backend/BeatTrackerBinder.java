package com.stillwindsoftware.keepyabeat.player.backend;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Binder;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;

import java.util.AbstractMap;
import java.util.TreeSet;

public class BeatTrackerBinder extends Binder implements IBeatTrackerCommunication, OnAudioFocusChangeListener, LibraryListener {

	private static final String LOG_TAG = "KYB-"+BeatTrackerBinder.class.getSimpleName();

    private final BeatTrackerService mBeatTrackerService;
	private final AndroidResourceManager mResourceManager;
	private final RhythmsContentProvider mRhythms;
    private final PlayerState mPlayerState;
	private final BeatTracker mBeatTracker;
    private final AndroidAudioStrategy mAudioStrategy;
	private volatile boolean mAreSoundsOn = true
            , mIsServiceAlive = true; // will be set to false when the service auto stops, callers can test this to check their referenced binder is useable

    private boolean mIsLoggingDebugs;
    private float mSumFullBeatValues; // taken at the moment the beat tree is taken during updateData()
    private String mRhythmName;

    public BeatTrackerBinder(BeatTrackerService beatTrackerService) {
		mBeatTrackerService = beatTrackerService;
		mResourceManager = ((KybApplication) beatTrackerService.getApplication()).getResourceManager();
        SettingsManager mStatesManager = (SettingsManager) mResourceManager.getPersistentStatesManager();
        mPlayerState = mStatesManager.getPlayerState();
        mIsLoggingDebugs = mResourceManager.isLogging(LOG_TYPE.debug);
        mAudioStrategy = new StreamAudioTrackStrategy(mBeatTrackerService);
        mBeatTracker = new StreamBeatTracker(this, (StreamAudioTrackStrategy)mAudioStrategy, mResourceManager.isLowMemoryDevice());
        mRhythms = (RhythmsContentProvider) mResourceManager.getLibrary().getRhythms();
	}

	public String getRhythmName() {
        return mRhythmName;
	}
	
	/**
	 * Called by constructor and also by the user to set the rhythm (open rhythm or new)
	 * @param rhythm
	 */
	@Override
	public void setModel(Rhythm rhythm) {

        boolean isPlayingNow = isPlaying() || isPaused();

        listenForRhythmChanges(true);

        if (isPlayingNow) {                            // don't cause a notification if there isn't one already
            mBeatTrackerService.updateNotification(rhythm.getName(), 0, mPlayerState, isPlaying());
        }

        try {
            updateData((RhythmSqlImpl) rhythm, true, true, true); // notifyStructureChanged() happens lower down
        } catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "setModel: error setting up the rhythm's data, there must be a cache already to call this", e);
            if (isPlayingNow)
                stopPlay();
            return;     // in case anything added in future below this
        }
    }

	private void updateData(RhythmSqlImpl rhythm, boolean modelChanged, boolean soundChanged, boolean volumeChanged)
            throws Exception {

	    mRhythmName = rhythm.getName();
        AbstractMap.SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> cache = rhythm.getBeatsAllData(); // overload which doesn't make any changes
        final TreeSet<RhythmBeatType> rhythmBeatTypes = cache.getKey();

	    boolean restart = false;// modelChanged && (isPlaying() || isPaused()); // don't restart for sound or volume changes
	    boolean wasPaused = isPaused();
	    if (restart) {
	        mBeatTracker.stopPlay();
        }

        AndroidResourceManager.logd(LOG_TAG, String.format("updateData: model (%s) or sound (%s) or volume (%s)", modelChanged, soundChanged, volumeChanged));
        BeatTree beatTree = cache.getValue();
        mSumFullBeatValues = beatTree.getSumFullBeatValues();
        mBeatTracker.setModel(beatTree, rhythmBeatTypes);
        mAudioStrategy.initSounds(rhythmBeatTypes);  // model change could mean a new beat type

        if (modelChanged) {
            updateTracking(rhythm.getBpm()); // notifyStructureChanged() happens at end
        }

        if (restart) {
            if (wasPaused) {
                mBeatTracker.pausePlay();
            }
            else {
                mBeatTracker.startPlay();
            }
        }
	}

//    public static void debugRbts(String logTag, TreeSet<RhythmBeatType> rhythmBeatTypes) {
//
//        for (Iterator<RhythmBeatType> it = rhythmBeatTypes.iterator(); it.hasNext(); ) {
//            RhythmBeatType rbType = it.next();
//            AndroidResourceManager.logd(logTag,
//                    String.format("debugRbts: name=%s, sound=%s, volume=%.2f",
//                            rbType.getBeatType().getName(), rbType.getSound().getName(), rbType.getVolume()));
//        }
//    }

    public void updateTracking(int bpm) {
	    AndroidResourceManager.logd(LOG_TAG, "updateData(bpm): calc beats deltas");
        mBeatTracker.updateTracking(bpm);
        mAudioStrategy.notifyStructureChanged(); // important this happens after updateData, so playhead position changes don't affect the calcs in that method
	}

	@Override
	public void stopPlay() {
        // clear listening
        removeAudioListening((AudioManager) mBeatTrackerService.getSystemService(Context.AUDIO_SERVICE));
        // actually stop the playing
        mAudioStrategy.notifyPlayStopped();
        mBeatTracker.stopPlay();
        listenForRhythmChanges(false);
        // let manager know so any kyb core parts querying play state can get it, more robust method to notify through
        // resource mgr and not gui manager since the latter can be destroyed/recreated
        mResourceManager.setRhythmPlaying(false, false);
        // the service might close itself if nothing is bound to it
        mBeatTrackerService.onPlayStopped();
	}

    private void listenForRhythmChanges(boolean isOn) {
        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "listenForRhythmChanges: waiting for lock on player rhythm");
            }

            if (isOn) {
                mRhythms.registerBeatTracker(this);
            }
            else {
                mRhythms.deregisterBeatTracker();
            }
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "listenForRhythmChanges: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }
    }

    /**
	 * when pausing or stopping play (either manually, via audio focus listener, or because finished iterations)
	 * @param am
	 */
	private void removeAudioListening(AudioManager am) {
		// de-register audio focus
        AndroidResourceManager.logd(LOG_TAG, "removeAudioListening: audio de-registered" );
		am.abandonAudioFocus(this);
		// de-register media button
		// ?
	}

	/**
	 * either granted audio focus immediately, or was gained via listener, register media buttons too
	 */
	private void gotAudioListening(AudioManager am) {
        AndroidResourceManager.logd(LOG_TAG, "gotAudioListening: audio registered" );
//		mMastervolume = am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float)am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		//TODO RemoteControlReceiver is supposed to be a broadcastreceiver declared in the manifest...?
//		am.registerMediaButtonEventReceiver(RemoteControlReceiver);
	}
	
	/**
	 * Called from some control that can... either a media button on the play/edit rhythms activity
	 * or perhaps on the notification
	 */
	@Override
	public void togglePlayPause(final boolean play) {

        mResourceManager.setRhythmPlaying(true, false);    // true because both play/pause count, false = not a self check stop
		
		final AudioManager am = (AudioManager) mBeatTrackerService.getSystemService(Context.AUDIO_SERVICE);

		// abandon audio focus if pausing...
		if (!play) {
			removeAudioListening(am);
            mAudioStrategy.notifyPlayPaused();
			mBeatTracker.togglePlayPause(false);
			mBeatTrackerService.onPlayPaused(mBeatTracker.getRhythmIterations(), mPlayerState);
		}
		
		else {
			// request to play
			
			int result = am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				mAreSoundsOn = true;
                mAudioStrategy.notifyPlayResumed();
				mBeatTracker.togglePlayPause(true);
				gotAudioListening(am);
			}

			// didn't get audio focus, but since this method is manually called, first check to see
			// if it's paused or stopped... if it's paused play silently (listening to audio focus as well)
			// if it's stopped the user is presumably on the screen, so pop up a dialog and if they want
			// to continue silently do that, otherwise don't play

			else if (mBeatTracker.isPaused()) {
				mAreSoundsOn = false;
                mAudioStrategy.notifyPlayResumed();
				mBeatTracker.togglePlayPause(true);
			}
			else {
				KybActivity activity = mResourceManager.getLatestActivity();
				
				if (activity != null && activity.hasWindowFocus()) {
					Resources res = activity.getResources();
					
					OnClickListener buttonListener = new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								if (whichButton == DialogInterface.BUTTON_POSITIVE) {
									//TODO remove debug
									AndroidResourceManager.logd(LOG_TAG, "togglePlayPause: audio debug playing but setting sounds off" );
									mAreSoundsOn = false;
                                    mAudioStrategy.notifyPlayResumed();
									mBeatTracker.togglePlayPause(true);
								}
								else {
									// stop listening
									removeAudioListening(am);
                                    mAudioStrategy.notifyPlayPaused();
                                    mResourceManager.setRhythmPlaying(false, false); // weird case, don't cause ad to show... may never trigger
								}
							}
						};

					AlertDialog.Builder alert = new AlertDialog.Builder(activity)
						.setTitle(res.getString(R.string.audio_focus_unavailable_title))
						.setMessage(res.getString(R.string.audio_focus_unavailable_body))
						.setPositiveButton(res.getString(R.string.ok_button), buttonListener )	
						.setNegativeButton(res.getString(R.string.cancel_button), null); // nothing to do
					alert.show();
				}
			}
		}
	}

	@Override
	public boolean isStopped() {
		return mBeatTracker.isStopped();
	}

	public boolean isPlaying() {
		return mBeatTracker.isPlaying();
	}

	@Override
	public float getPlayingDelta() {
        return mBeatTracker.getPlayingDelta();
	}

	@Override
	public int getRhythmIterations() {
		return mBeatTracker.getRhythmIterations();
	}
//TODO not sure why this is marked...
//	@Override
//	public void itemChanged() {
//		updateData();
//	}

	/**
	 * In TWL this caused a round trip where BeatTracker.stopPlay() was eventually called. 
	 * Not a nice solution, so call back to that straightaway, in addition to other actions.
     * @param beatTracker
     */
	@Override
	public void notifyStopPlay(BeatTracker beatTracker) {
        AndroidResourceManager.logd(LOG_TAG, String.format("notifyStopPlay: beatTracker is stopping (matches current beatTracker=%s)", mBeatTracker == beatTracker));
        if (beatTracker != mBeatTracker) {
            AndroidResourceManager.loge(LOG_TAG, "notifyStopPlay: beatTracker calling is not the one stored, this is a bug");
            beatTracker.stopPlay();
        }

        stopPlay();
	}

    @Override
    public void notifyPlayStarted() {
        mBeatTrackerService.onPlayStarted(mPlayerState);
        listenForRhythmChanges(true);
    }

    @Override
	public void log(LOG_TYPE type, Object origin, String message) {
		mResourceManager.log(type, origin, message);
	}

    @Override
    public boolean isLoggingDebugMessages() {
        return mIsLoggingDebugs;
    }

    @Override
	public void destroy() {
        mIsServiceAlive = false;
        listenForRhythmChanges(false);
        mResourceManager.setRhythmPlaying(false, true);// belt and braces, just make sure it's unset
		mBeatTracker.destroy();
        if (mAudioStrategy != null) { // may never had played yet
            mAudioStrategy.releaseResources();
        }
	}

	@Override
	public float getSumFullBeatValues() {
		return mSumFullBeatValues;
	}

	@Override
	public void notifyPlayIterationsChanged(int currentRhythmIteration) {
		mResourceManager.getGuiManager().playIterationsChanged(currentRhythmIteration);
		mBeatTrackerService.onPlayIterationsChanged(currentRhythmIteration, mPlayerState, mBeatTracker.isPlaying());
	}

	@Override
	public PlayerState getPlayerState() {
		return mPlayerState;
	}

	/**
	 * Implement the onAudioFocusChangeListener
	 * @param focusChange
	 */
	@Override
	public void onAudioFocusChange(int focusChange) {
		
		AudioManager am = (AudioManager) mBeatTrackerService.getSystemService(Context.AUDIO_SERVICE);
		
		// audio focus gain, if playing silently allow to continue again with sounds
		// otherwise do nothing (let the user notice and start play if req'd)
		if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
			if (mBeatTracker.isPlaying()) {
				//TODO remove debug
				AndroidResourceManager.logd(LOG_TAG, "onAudioFocusChange: audio debug setting sounds on again" );
				mAreSoundsOn = true;
				gotAudioListening(am);
			}
			else {
				// should not ever happen, because not listening when not playing, but in case...
				AndroidResourceManager.logw(LOG_TAG, "onAudioFocusChange: audio debug gained audio but wasn't even playing" );
				removeAudioListening(am);
			}
		}
		else {
			// audio focus lost, pause playing
			//TODO remove debug
			AndroidResourceManager.logd(LOG_TAG, "onAudioFocusChange: audio debug pause play because lost focus" );
			removeAudioListening(am);
			// don't expect to be stopped here, but check anyway
			if (mBeatTracker.isPlaying()) {
				mBeatTracker.togglePlayPause(false);
				mBeatTrackerService.onPlayPaused(mBeatTracker.getRhythmIterations(), mPlayerState);
			}
		}
	}

	/**
	 * Indicates config to the BeatTracker (during init)  
	 */
	@Override
	public boolean hasAdjustmentsToBeatTrackerConfig() {
		return true;
	}

	@Override
	public long getWakeUpFromSleepMargin() {
		return mAudioStrategy.getWakeUpFromSleepMargin();
	}

	@Override
	public float getTooLongPassedThreshold() {
		return mAudioStrategy.getTooLongPassedThreshold();
	}


	/**
	 * Test sounds are on, and system volume, and passed volume. All good then
	 * play the sound. Ignores seconds of silence, it should be tiny or already
	 * caught by BeatTracker.
	 */
	@Override
	public long playSound(SoundResource soundRes, float secondsOfSilence, float volume, long playAtNanosTime) {
        String msg = "playSound: method not supported for streaming strategy";
        AndroidResourceManager.loge(LOG_TAG, msg);
        throw new IllegalStateException(msg);
	}

    @Override
    public long playMix(short[] data) {
        if (!(mAudioStrategy instanceof StreamAudioTrackStrategy)) {
            String msg = "playMix: method only supported for Streaming strategy";
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new IllegalStateException(msg);
        }

        // send it regardless of sounds on or master volume, should be ok
        return mAudioStrategy.playMix(data);
    }

    @Override
    public void itemChanged(int changeId, String key, int natureOfChange, LibraryListenerTarget[] listenerTargets) {

        ListenerSupport listenerSupport = mResourceManager.getListenerSupport();

        if (listenerSupport.isRhythmRemoved(natureOfChange)) {
            if (mBeatTracker.isPlaying() || mBeatTracker.isPaused()) {
                AndroidResourceManager.logd(LOG_TAG, String.format("itemChanged: id=%s, rhythm removed while playing (key=%s), natureOfChange=%s",
                        changeId, key, listenerSupport.debugNatureOfChange(natureOfChange)));
                stopPlay();
            }
        }
        else {
            try {
                while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                    AndroidResourceManager.logd(LOG_TAG, "itemChanged: waiting for lock on player rhythm");
                }

                RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();

                if (listenerSupport.isBpmChange(natureOfChange)) {
                    updateTracking(rhythm.getBpm());
                } else if (listenerSupport.isSoundChange(natureOfChange)
                        || listenerSupport.isVolumeChange(natureOfChange)) {
                    updateData(rhythm, false, listenerSupport.isSoundChange(natureOfChange), listenerSupport.isVolumeChange(natureOfChange));   // much more full on change than bpm above (incl model/structure)
                } else if (listenerSupport.isNonSoundVolumeRbtChange(natureOfChange)
                        || listenerSupport.isRhythmEditNonStructural(natureOfChange)) {
                    // rbt changes don't change the structure and the sound/volumes are picked up when they play, so nothing to do
                    // same with things like change to beat numbering
                } else if (listenerSupport.isRhythmEdit(natureOfChange)) {
                    updateData(rhythm, true, false, false);   // much more full on change than bpm above (incl model/structure)
                } else if (listenerSupport.isNonSpecificChange(natureOfChange)) {           // rhythm edits all really expecting, non-specific must be for undo?
                    AndroidResourceManager.logd(LOG_TAG, String.format("itemChanged: id=%s, should not be a non-specific change (key=%s, rhythm=%s), natureOfChange=%s",
                            changeId, key, rhythm.getListenerKey(), listenerSupport.debugNatureOfChange(natureOfChange)));
                    updateData(rhythm, true, false, false);   // much more full on change than bpm above (incl model/structure)
                } else {
                    AndroidResourceManager.logd(LOG_TAG, String.format("itemChanged: id=%s, change ignored, is it applicable to beat tracker? (key=%s, rhythm=%s), natureOfChange=%s",
                            changeId, key, rhythm.getListenerKey(), listenerSupport.debugNatureOfChange(natureOfChange)));
                }
            } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
                AndroidResourceManager.loge(LOG_TAG, "itemChanged: program error", e);
            } catch (Exception e) {
                AndroidResourceManager.loge(LOG_TAG, "itemChanged: error, stop play if playing", e);
                stopPlay();
            } finally {
                mRhythms.releaseReadLockOnPlayerEditorRhythm();
            }
        }
    }

    @Override
    public boolean isServiceAlive() {
        return mIsServiceAlive;
    }

    @Override
    public long getLatency(long latencyMillis) { // ignore the default since in android version apply own default
        return mAudioStrategy.getLatency();
    }

    /**
     * Called from SettingsActivity fragment when the preference changes to toggle use of streaming beat tracker
     * Change to the new value
    public boolean resetBeatTracker() {
        if (!mIsServiceAlive) {
            AndroidResourceManager.logd(LOG_TAG, "resetBeatTracker: service is dead, nothing to do");
            return true;            // nothing to do
        }

        // check for currently playing, if so will have to reset the beat tracker to the same spot
        boolean isPlaying = mBeatTracker.isPlaying();
        boolean isPaused = mBeatTracker.isPaused();
        int currentIteration = mBeatTracker.getRhythmIterations();
        float playingDelta = mBeatTracker.getPlayingDeltaForUi();

        if (isPlaying || isPaused) {
            AndroidResourceManager.logd(LOG_TAG, String.format("resetBeatTracker: stopping existing beat tracker/audio strategy (isPlaying=%s isPaused=%s)", isPlaying, isPaused));
            mBeatTracker.stopPlay();
            mAudioStrategy.notifyPlayStopped();     // stops and flushes
        }

        setBeatTracker();           // sets up the audio strategy and the beat tracker
        updateData();           // applies the model

        // set up playing at the same place
        if (isPlaying || isPaused) {
            AndroidResourceManager.logd(LOG_TAG, String.format("resetBeatTracker: starting new beat tracker/audio strategy (isPlaying=%s, isPaused=%s, iteration=%s, delta=%.2f)",
                    isPlaying, isPaused, currentIteration, playingDelta));
            mBeatTracker.startPlayAt(isPaused, currentIteration, playingDelta);
        }

        return true;
    }
     */

    /**
     * Called from service onDestroy(), belt and braces in case it dies behind the scenes
     */
    public void onServiceDied() {
        mIsServiceAlive = false;
    }

    public boolean isPaused() {
        return mBeatTracker.isPaused();
    }
}
