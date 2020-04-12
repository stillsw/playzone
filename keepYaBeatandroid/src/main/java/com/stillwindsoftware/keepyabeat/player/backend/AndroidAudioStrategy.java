package com.stillwindsoftware.keepyabeat.player.backend;

import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.TreeSet;

import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;

/**
 * Defines the visible methods for the pluggable audio strategy used by BeatTrackerBinder to play sounds
 */
public interface AndroidAudioStrategy {

    int SAMPLE_RATE_22050 = 22050;
    int MIN_MONO_16_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE_22050, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    int SUPPORTED_MIN_BUFFER_SIZE = MIN_MONO_16_BUFFER_SIZE;
    int SUPPORTED_CHANNELS = 1;
    int SUPPORTED_SAMPLE_RATE = SAMPLE_RATE_22050;
    float SAMPLES_PER_MILLI = SUPPORTED_SAMPLE_RATE / 1000.f;
    int SUPPORTED_BIT_DEPTH = 16;
    int SUPPORTED_BIT_DEPTH_TO_BYTES = SUPPORTED_BIT_DEPTH / 8;
    int DEFAULT_LATENCY = 40;

	// config for android system to change how beat tracker works
	public long getWakeUpFromSleepMargin();
	public float getTooLongPassedThreshold();

	public void initSounds(TreeSet<RhythmBeatType> rhythmBeatTypes);
	public void releaseResources();

    public long playSound(SoundResource soundRes, float secondsOfSilence, float volume, long playAtNanosTime);
    public long playMix(short[] mix); // for streaming only

    public long getLatency();

    public void notifyPlayStopped();
    public void notifyPlayPaused();
    public void notifyPlayResumed();
    public void notifyStructureChanged();
}
