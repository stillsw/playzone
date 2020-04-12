package com.stillwindsoftware.keepyabeat.player.backend;

import android.app.Service;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidSoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * Most of the same restrictions as BySoundAudioTrackStrategy, but here streaming is assumed to work non-blocking
 * Uses StreamingBeatTracker too, as the two have a tied relationship in that the sounds data also includes
 * the silence between sounds, which isn't the case for the other audio strategies
 *
 */
public class StreamAudioTrackStrategy extends AudioTrackStrategyAdapter //implements AudioTrack.OnPlaybackPositionUpdateListener
{

	private static final String LOG_TAG = "KYB-"+StreamAudioTrackStrategy.class.getSimpleName();

    public static final long NANOS_PER_SHORT =
            (long) (BeatTracker.NANOS_PER_MILLI /                   // nanos is time, how many in a milli
                                SAMPLES_PER_MILLI);                 // by how many samples (1 sample = 1 short)

    protected HashMap<AndroidSoundResource, short[]> mSoundPool;
    protected volatile short[][] mQueuedMix;
    protected volatile boolean[] mIsQueued;
    private short[] mTempMix;

//    // keep track of the head position so can report on the number of frames passing
//    private int mLastReadHeadPosition, mFramesPassedSinceReported;

    StreamAudioTrackStrategy(Service beatTrackerService) {
        super(beatTrackerService);
//        none of this works
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//            int bufferFrames = mAudioTrack.getBufferSizeInFrames();
//            AndroidResourceManager.loge(LOG_TAG, "init: bufferframes="+bufferFrames);
//        }
//        int setResult = mAudioTrack.setPositionNotificationPeriod(1548/2); // every buffer size
//        if (setResult != AudioTrack.SUCCESS) {
//            AndroidResourceManager.loge(LOG_TAG, "init: playing progress bar won't work as failed to set AudioTrack.PositionNotificationPeriod result="+setResult);
//        }
//        mAudioTrack.setPlaybackPositionUpdateListener(this);
	}

//    @Override
//    public void onMarkerReached(AudioTrack track) {
//        // not used
//    }
//
//    @Override
//    public void onPeriodicNotification(AudioTrack track) {
//        mFramesPassedSinceReported+=1548;
//        AndroidResourceManager.logw(LOG_TAG, String.format("onPeriodicNotification: track mFramesPassedSinceReported=%s)", mFramesPassedSinceReported));
//    }

    @Override
    protected void createSoundPool() {
        mSoundPool = new HashMap<>();
    }

    public int getPlayBufferSize() {
        return SUPPORTED_MIN_BUFFER_SIZE * 2; // used as the size for the mix buffer
    }

    protected void initQueues() {
        mQueuedMix = new short[RING_BUFFER_LEN][];
        mIsQueued = new boolean[RING_BUFFER_LEN];
        Arrays.fill(mIsQueued, false);

        int bufferSize = getPlayBufferSize();
        for (int i = 0; i < mQueuedMix.length; i++) {
            mQueuedMix[i] = new short[bufferSize];
        }

        mTempMix = new short[bufferSize]; // used in take and write from queue
    }

    @Override
    protected void initTakeTempStructures() {
        Arrays.fill(mTempMix, (short)0);
    }

    @Override
    protected void writeTakenData() {
//        long startWriteAtNanos = System.nanoTime();

        mAudioTrack.write(mTempMix, 0, mTempMix.length); // now write the actual sound data

//        long allWrittenAtNanos = System.nanoTime();// how long elapsed since the sound was sent to the track
//        float samplesWritten = mTempMix.length;
//        float secsWritten = samplesWritten / SUPPORTED_SAMPLE_RATE;
//        float millisWritten = secsWritten * 1000f;
//        AndroidResourceManager.logd(LOG_TAG, String.format("\twriteTakenData: writing took = %s, total samples = %s, playing time of data = %.2f",
//                (allWrittenAtNanos - startWriteAtNanos) / BeatTracker.NANOS_PER_MILLI,
//                samplesWritten,
//                millisWritten));

    }

    @Override
    protected void takeFromQueue() {
        for (int i = 0; i < mQueuedMix[mNextToReadFromQueue].length; i++) {
            mTempMix[i] = mQueuedMix[mNextToReadFromQueue][i];
            mQueuedMix[mNextToReadFromQueue][i] = (short)0; // reset
        }

        mIsQueued[mNextToReadFromQueue] = false;
    }

    @Override
    protected boolean noQueuedDataReady() {
        return !mIsQueued[mNextToReadFromQueue];
    }

    @Override
    protected String getQueueReportValueAt(int i, boolean atWrite) {
        if (mIsQueued[i]) {      // with data
            return atWrite && i == mNextToWriteToQueue ? "W" : !atWrite && i == mNextToReadFromQueue ? "R" : "X";
        }

        return " ";                     // nothing
    }

    @Override
    protected void clearSoundPool() {
        mSoundPool.clear();
    }

    /**
     * 1) writing to the queue acquires a lock, and also awaits the condition that
     * there is space in the ring buffer to actually write to... so nothing is ever missed or overwritten
     * 2) sounds and silence are all combined already, therefore the passed mixed sounds data must be
     * copied out to the queue buffers and reset (so the caller can re-use it)
     * @param mix
     * @return
     */
    @Override
    public long playMix(short[] mix) {

        // put the data on the queue for the play thread
        mLock.lock();
        try {
            int j = 0;
            while (mIsQueued[mNextToWriteToQueue]) { // sound on there that hasn't been taken
//                AndroidResourceManager.logv(LOG_TAG, String.format("playMix: awaiting, try=%s", j++));
                mQueueNotReady.await();
            }

            mIsQueued[mNextToWriteToQueue] = true;

            // copy the data from the mix queue to the buffer for writing
            // needs to copy as the mix array will go on to be re-used for the next mix
            for (int i = 0; i < mQueuedMix[mNextToWriteToQueue].length; i++) {
                mQueuedMix[mNextToWriteToQueue][i] = mix[i];
            }

            reportQueue(true, "Written to queue (streamed)");

            if (++mNextToWriteToQueue >= mIsQueued.length) { // ring buffering, have written to the last in the array, start at the beginning again
                mNextToWriteToQueue = 0;
            }

            // signal the play thread
            mNoData.signal();

        } catch (InterruptedException e) {
            // could be user is on other apps and android is either allocated resources elsewhere or perhaps in gc
            AndroidResourceManager.logw(LOG_TAG, "playMix: interrupted and will not be played");
        } finally {
            mLock.unlock();
        }

        long playingTimeNanos = calcPlayingTimeNanos(mix);
//        AndroidResourceManager.logv(LOG_TAG, String.format("playMix: done, playingTimeNanos=%s, androidTrack.getPlaybackHeadPosition=%s", playingTimeNanos, mAudioTrack.getPlaybackHeadPosition()));
        return playingTimeNanos;
    }

    /**
     * Calculates the elapsed time to play the data
     * @param data
     * @return
     */
    public static long calcPlayingTimeNanos(short[] data) {

        final float samples = data.length;    // number of samples is the number of shorts
        final float millis = samples / SAMPLES_PER_MILLI;
        final long nanos = (long)(millis * BeatTracker.NANOS_PER_MILLI);

        return nanos;
    }

    @Override
    public long playSound(SoundResource soundRes, float secondsOfSilence, float volume, long nanos) {
        String msg = "playSound: method not supported for Stream strategy";
        AndroidResourceManager.loge(LOG_TAG, msg);
        throw new IllegalStateException(msg);
    }

    @Override
    public void notifyPlayPaused() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.flush();
//            mAudioTrack.pause();
        }
    }

    @Override
    public void notifyPlayResumed() {
        if (mAudioTrack != null) {
            mAudioTrack.play();
        }
    }

    @Override
    public void notifyPlayStopped() {
        super.notifyPlayStopped();
        //mLastReadHeadPosition =
//        mFramesPassedSinceReported = 0;   // reset frame tracking
    }

    @Override
    public synchronized void notifyStructureChanged() {
        if (mAudioTrack != null) {
//            mFramesPassedSinceReported +=   // accumulate across multiple changes
//                    getDiffLastHeadPositionToNow();
            mAudioTrack.stop();
            mAudioTrack.flush();
            mAudioTrack.play();
            //mLastReadHeadPosition = 0;      // head position is 0 after stop/flush
//AndroidResourceManager.logd(LOG_TAG, String.format("notifyStructureChanged: playhead before change = %s, after = %s", pre, mAudioTrack.getPlaybackHeadPosition()));
        }
    }

    /**
//     * Keep track of how many frames passed since starting, and since the last call to this
//     * method. This keeps track across flush() which is called during structure changes.
     *
     * @return
     */
//    public synchronized int getFramesPosition() {
//        return mAudioTrack.getPlaybackHeadPosition();
//        int totalFramesToReport = //getDiffLastHeadPositionToNow() +
//                mFramesPassedSinceReported;
//        mFramesPassedSinceReported = 0;     // reset every time reported
//        return totalFramesToReport;
//    }

//    public int getDiffLastHeadPositionToNow() {
//        int headPos = mAudioTrack.getPlaybackHeadPosition();
//        int diff = headPos - mLastReadHeadPosition;
//
//        mLastReadHeadPosition = headPos;    // reset the position
//
//        if (diff < 0) {                     // should only be flushing during structure change, which should be ensuring this can't happen
//            AndroidResourceManager.loge(LOG_TAG, "getDiffLastHeadPositionToNow: bug, diff is less than 0, must be a reset/flush somehow not accounted for");
//            return 0;                       // or throw some exception, not sure how would deal with it
//        }
//
//        return diff;
//    }

    // for non-streamed impl

    @Override
    public long getWakeUpFromSleepMargin() { // not used for streaming version
        return 0;
    }

    /**
     * Except for type of data, this method is almost identical to that in BySoundAudioTrackingStrategy
     * would be good to refactor what's common
     * TODO
     * @param rhythmBeatTypes
     */
    @Override
    public void initSounds(TreeSet<RhythmBeatType> rhythmBeatTypes) {
        long started = System.currentTimeMillis();

        HashMap<AndroidSoundResource, short[]> newSounds = new HashMap<>();

        RhythmBeatType rbt = rhythmBeatTypes.first();

        // loop through reading until none left
        while (rbt != null) {
            AndroidSoundResource soundRes = (AndroidSoundResource) rbt.getSoundResource();

            if (soundRes == null) {                                     // silent sound would have no resource
            }

            else if (mSoundPool.containsKey(soundRes)) {               // already in the pool, add it from there to the new map
                AndroidResourceManager.logd(LOG_TAG, String.format("initSounds: sound in pool is being included again (%s)", soundRes.getUriPath()));
                newSounds.put(soundRes, mSoundPool.get(soundRes));
            }

            else if (!newSounds.containsKey(soundRes)) {                // don't try to load same resource > 1

//				AndroidResourceManager.logd(LOG_TAG, String.format("initSounds: trying sound %s", soundRes.getUriPath()));

                short[] audioData = null;

                // try to load the sound, it must be the correct format, if it fails with an exception
                // (reported in the method) it will return null
                if (soundRes.getStatus() == Sound.SoundStatus.LOADED_OK && soundRes.isPlayable()) {
                    AndroidResourceManager.logd(LOG_TAG, String.format("initSounds: new sound add to pool (%s)", soundRes.getUriPath()));
                    audioData = (short[])loadSoundResource(soundRes, true); // as short array

                    // update the status
                    if (audioData == null) {
                        soundRes.setStatus(Sound.SoundStatus.LOAD_FAILED_ERROR);
                        soundRes.setLoadError(PlatformResourceManager.SoundResourceLoadError.INVALID_FORMAT);
                        soundRes.setLoadErrorLocalisedMessage(mBeatTrackerService.getResources()
                                .getString(R.string.LOAD_FAILED_INVALID_FORMAT));
                    }
                }

                if (audioData == null) {
                    AndroidSoundResource fbSoundRes = (AndroidSoundResource) rbt.getFallbackSoundResource();
                    AndroidResourceManager.logd(LOG_TAG, String.format("initSounds: unable to load sound %s, using fallback %s"
                            , soundRes.getFileName(), fbSoundRes.getFileName()));

                    if (fbSoundRes != null && fbSoundRes.getStatus() == Sound.SoundStatus.LOADED_OK && fbSoundRes.isPlayable()) {
                        soundRes = fbSoundRes;
                        audioData = (short[])loadSoundResource(soundRes, true); // as short array
                    }
                }

                // got data, add to pool and to set (which indicates what is loaded this time)
                if (audioData != null) {
                    // in case using fb sound, make sure it's not already there again
                    if (!newSounds.containsKey(soundRes)) {
                        newSounds.put(soundRes, audioData);
                    }
                }
                else {
                    AndroidResourceManager.logd(LOG_TAG, String.format("initSounds: sound (%s) not playable", soundRes.getUriPath()));
                }

            }

            // get next one if any left loop
            rbt = rhythmBeatTypes.higher(rbt);
        }

        // re-pop the pool
        synchronized(mSoundPool) {
            mSoundPool.clear();
            mSoundPool.putAll(newSounds);
        }

        long elapsed = System.currentTimeMillis() - started;
        AndroidResourceManager.logd(LOG_TAG, String.format("initSounds: completed in %sms", elapsed));
    }

    public short[] getSoundData(SoundResource soundRes) {
        return mSoundPool.get(soundRes);
    }

    public int getSampleRate() {
        return mAudioTrack.getSampleRate();
    }

}
