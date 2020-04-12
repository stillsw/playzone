package com.stillwindsoftware.keepyabeat.player.backend;

import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;

/**
 * Proxy for communication between BeatTracker and the rest of the app
 * (see IBeatTrackerCommunication)
 * For TWL a BeatTracker is req'd only for the PlayControls gui which can
 * then be moved around. This class can just create the BeatTracker and
 * pass messages as needed.
 */
public class TwlBeatTrackerCommunication implements IBeatTrackerCommunication, LibraryListListener {

	private BeatTracker beatTracker;
	private final PlatformResourceManager resourceManager;
	private final PlayerState playerState;
	private Rhythm rhythm;
	private PlayedRhythmDraughter playedRhythmDraughter;

	public TwlBeatTrackerCommunication(PlayedRhythmDraughter playedRhythmDraughter) {
		resourceManager = playedRhythmDraughter.getResourceManager();
		beatTracker = new BeatTracker(this);
		playerState = resourceManager.getPersistentStatesManager().getPlayerState();
		setModel(playedRhythmDraughter);
	}
	
	/**
	 * Called by constructor and also by the user to set the rhythm (open rhythm or new)
	 * @param playedRhythmDraughter
	 */
	@Override
	public void setModel(PlayedRhythmDraughter playedRhythmDraughter) {

		ListenerSupport listenerSupport = resourceManager.getListenerSupport();
		
		// should always have a rhythm, but just in case
		if (this.rhythm != null) {
			listenerSupport.removeListener(rhythm, this);
		}
		
		// remove this from the current playedRhythmDraughter
		// if there is one, and set the beat tracker on the new one
		if (!playedRhythmDraughter.equals(this.playedRhythmDraughter)) {
			playedRhythmDraughter.setBeatTracker(this);
			if (this.playedRhythmDraughter != null) {
				this.playedRhythmDraughter.setBeatTracker(null);
			}
		}

		this.playedRhythmDraughter = playedRhythmDraughter;
		this.rhythm = playedRhythmDraughter.getRhythm();

		updateTracking();

		// now register this as a listener for changes
		listenerSupport.addListener(rhythm, this);
	}

	@Override
	public void initBeats() {
		beatTracker.initBeats();
	}

	@Override
	public void updateTracking() {
		beatTracker.setModel(playedRhythmDraughter.getBeatTree(), playedRhythmDraughter.getRhythmBeatTypes());
		updateTracking(rhythm.getBpm());
	}

	@Override
	public void updateTracking(int bpm) {
		beatTracker.updateTracking(bpm);
	}

	@Override
	public void stopPlay() {
		beatTracker.stopPlay();
	}

	@Override
	public void togglePlayPause(boolean play) {
		beatTracker.togglePlayPause(play);
	}

	@Override
	public boolean isStopped() {
		return beatTracker.isStopped();
	}

	@Override
	public float getPlayingDelta() {
		return beatTracker.getPlayingDelta();
	}

	@Override
	public int getRhythmIterations() {
		return beatTracker.getRhythmIterations();
	}

	@Override
	public void itemChanged() {
		updateTracking();
	}

	@Override
	public void notifyStopPlay(BeatTracker beatTracker) {
		resourceManager.getGuiManager().stopPlay();
	}

	@Override
	public void log(LOG_TYPE type, Object origin, String message) {
		resourceManager.log(type, origin, message);
	}

	@Override
	public void destroy() {
		beatTracker.destroy();
		
		if (this.rhythm != null) {
			resourceManager.getListenerSupport().removeListener(rhythm, this);
		}
	}

	@Override
	public float getSumFullBeatValues() {
		return playedRhythmDraughter.getSumFullBeatValues();
	}

	@Override
	public void notifyPlayIterationsChanged(int currentRhythmIteration) {
		resourceManager.getGuiManager().playIterationsChanged(currentRhythmIteration);
	}

	@Override
	public void updatePlayerState(PlayerState playerState) {
		// twl doesn't do it this way because runs in same process always
		// and player state is the same obj
	}

	@Override
	public PlayerState getPlayerState() {
		return playerState;
	}

	/**
	 * Called from BeatTracker.playSound(PlayedBeat, float) which is the normal operation during playback
	 * and also from startPlayingThread() when playing a very quiet sound to start a rhythm off
	 * 
	 * @param soundRes
	 * @param secondsOfSilence
	 * @param playAtNanosTime (not used in twl currently)
	 * @return 
	 */
	public long playSound(SoundResource soundRes, float secondsOfSilence, float volume, long playAtNanosTime) {
		
		try {
			soundRes.playSound(volume, secondsOfSilence);
		}
		catch (RuntimeException e) {
			// have seen unsatisfiedLinkError occasionally, don't fail because of that, just don't play it
			log(LOG_TYPE.error, this, "playSound: Recovered from unexpected error "+e);
		}		
		
		return 0L; // not used in twl version
	}
	
	// following methods not used in twl version
	@Override
	public boolean hasAdjustmentsToBeatTrackerConfig() {
		return false;
	}

	@Override
	public long getWakeUpFromSleepMargin() {
		return 0;
	}

	@Override
	public float getTooLongPassedThreshold() {
		return 0;
	}

	@Override
	public boolean isServiceAlive() {
		return true; // twl the service is always alive if the beat tracker is created
	}

	@Override
	public boolean isLoggingDebugMessages() {
		return resourceManager.isLogging(LOG_TYPE.debug);
	}

	@Override
	public void notifyPlayStarted() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long playMix(short[] data) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLatency(long latencyMillis) {
		return latencyMillis; // return the default passed in 
	}

}
