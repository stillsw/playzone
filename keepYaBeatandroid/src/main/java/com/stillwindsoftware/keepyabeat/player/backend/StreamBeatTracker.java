package com.stillwindsoftware.keepyabeat.player.backend;

import android.util.Log;

import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.player.PlayedBeat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;

/**
 * Streaming version of BeatTracker
 * Created by tomas on 16/12/16.
 */

class StreamBeatTracker extends BeatTracker {

    private static final String LOG_TAG = "KYB-"+StreamBeatTracker.class.getSimpleName();

    private final long mPlayingThreadSleepTime;
    private final long mMixBufferLenNanos;

    private final short[] mMixData;
    private final StreamAudioTrackStrategy mStreamStrategy;

    private long mMixBufferStartPointNanos = 0L;    // the point the current mix buffer is

    private int mStreamingIntoIteration; // distinct to current which is where the player is at this moment
    private ArrayList<PlayedBeat> mBeatsSpanningMixes = new ArrayList<>(10); // a beat is added here if it's being played but overflows
                                                                             // the buffer so needs to be in the next mix too
    private boolean mIsMixBufferComplete = false;


    // ie. the playing progress indicator follows current
                                        // this var instead follows up to which iteration streaming is happening
                                        // it indicates an iteration has started streaming, but there would still
                                        // be beats with < the value until it completes

    /**
     * Construct a BeatTracker with a PlayedRhythmDraughter which supplies the beat data structure
     *
     * @param btProxy
     */
    StreamBeatTracker(IBeatTrackerCommunication btProxy, StreamAudioTrackStrategy streamAudioTrackStrategy, boolean isLowMemoryDevice) {
        super(btProxy);

        // array for mixing the sounds to send to the stream
        mStreamStrategy = streamAudioTrackStrategy;
        int sampleRate = mStreamStrategy.getSampleRate();

        // if needed make a field
//        mNanosPerSample = BeatTracker.NANOS_PER_SECOND / sampleRate;

        // if needed samples per rhythm, could be calculated like this:
//        float secondsPerRhythm = (float)nanosPerRhythm / BeatTracker.NANOS_PER_SECOND;
//        float samplesPerRhythm = secondsPerRhythm * sampleRate;

        mMixData = new short[mStreamStrategy.getPlayBufferSize()];

        mMixBufferLenNanos = StreamAudioTrackStrategy.calcPlayingTimeNanos(mMixData);

        mPlayingThreadSleepTime = mMixBufferLenNanos / NANOS_PER_MILLI / 4;
        long mMixBufferLenMillis = mMixBufferLenNanos / NANOS_PER_MILLI;

        AndroidResourceManager.logd(LOG_TAG, String.format("<init>: mix buffer len = %sms, sleep between loops = %sms, latency = %s nanos",
                mMixBufferLenMillis, mPlayingThreadSleepTime, NANOS_LATENCY));
    }

    /**
     * Super class inits the beats and starts the playing thread, so init the
     * streaming progress before that
     */
    @Override
    protected synchronized void startPlay() {
        if (!isPaused) {
            mStreamingIntoIteration = 0;
            mMixBufferStartPointNanos = 0L;
            mBeatsSpanningMixes.clear(); // any beats that were waiting to complete playing aren't any more
        }

        super.startPlay();
    }

    /**
     * Similarly to its sister method atEndOfPlayingIterations(), detects
     * when there is no more data to stream
     * @return
     */
    private boolean atEndOfStreamingIterations() {
        // number of playback repeats set by the user dictates if should stop playing
        return ((playerState.getRepeatOption() == PlayerState.RepeatOption.NoRepeat && mStreamingIntoIteration != 0)
                || (playerState.getRepeatOption() == PlayerState.RepeatOption.RepeatNtimes
                && mStreamingIntoIteration >= playerState.getNTimes()));
    }

//    /**
//     * Save on a division during playback
//     * @param bpm
//     */
//    @Override
//    synchronized void updateTracking(int bpm) {
//        super.updateTracking(bpm);
//        // new value for nanos per rhythm, means new value of samples per rhythm too
//        float secondsPerRhythm = (float)nanosPerRhythm / BeatTracker.NANOS_PER_SECOND;
//        float samplesPerRhythm = secondsPerRhythm * mSampleRate;
//        AndroidResourceManager.logv(LOG_TAG, String.format("updateTracking: nanosPerRhythm=%s, secondsPerRhythm=%.2f, samplesPerRhythm=%.2f)",
//              nanosPerRhythm, secondsPerRhythm, mSamplesPerRhythm));
//    }

    protected synchronized long updateBeats() {

        try {
            updateIterationsAndPlayerState();
            final long limitForReadAheadNanos = mMixBufferStartPointNanos + mMixBufferLenNanos;

//        final long currentElapsedFromStartNanos = currentRhythmIteration * nanosPerRhythm + rhythmProgressNanos;
//        final int fromStartShorts = (int) (currentElapsedFromStartNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//        final int readAheadShorts = (int) (limitForReadAheadNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//        final int rhythmShorts = (int) (nanosPerRhythm / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//        final int rhythmMillis = (int) (nanosPerRhythm / BeatTracker.NANOS_PER_MILLI);
//        AndroidResourceManager.logv(LOG_TAG, String.format("updateBeats: iterations (%s) updated, streaming is ahead = %s, buffering is ahead = %s, (rhythmShorts=%s (%sms), limitForReadAheadNanos=%s, compareElapsed=%s)",
//                currentRhythmIteration, mMixBufferStartPointNanos > currentElapsedFromStartNanos, limitForReadAheadNanos > currentElapsedFromStartNanos,
//                rhythmShorts, rhythmMillis, readAheadShorts, fromStartShorts));

            // stop play altogether if at end (current, so the elapsed playing)

            if (atEndOfPlayingIterations()) {                                                       // for the player activity to keep progress, so works on current rhythm iteration
    //            AndroidResourceManager.logv(LOG_TAG, String.format("updateBeats: at end of playing iterations return 0"));
                return 0;                                                                           // irrelevant what value as ending stops the thread
            }

            // no need to read beats or play anything if already streamed everything (but have to keep calling this method to get current play updates)

            if (atEndOfStreamingIterations()) {             // stop playing beats, but keep playing for the current rhythm iteration to reach end
    //            AndroidResourceManager.logv(LOG_TAG, String.format("updateBeats: end of streaming iterations (current=%s) (streamed=%s) return sleepTime(%s)",
    //                    currentRhythmIteration, mStreamingIntoIteration, mPlayingThreadSleepTime));
                return mPlayingThreadSleepTime;
            }


            try {
                buildMix(limitForReadAheadNanos);

                if (mIsMixBufferComplete && isPlaying) {    // could have stopped/paused
                    sendMix();
                }

                return mPlayingThreadSleepTime;
            }
            catch (IllegalStateException e) {               // eg. no next playable beat found
                btProxy.notifyStopPlay(this);
                return 0;                                   // already stopping anyway
            }
        } catch (NullPointerException e) {                  // have seen this during changes
            AndroidResourceManager.loge(LOG_TAG, "updateBeats: possibly a change happened while playing", e);
            return 0; // get it to go round again
        }
    }

    /**
     * Send the mix out to the stream and reinitialize the local buffer
     */
    private void sendMix() {
//        AndroidResourceManager.logv(LOG_TAG, "sendMix: ");
        btProxy.playMix(mMixData);
        Arrays.fill(mMixData, (short)0);
        mIsMixBufferComplete = false; // reset flag
        mMixBufferStartPointNanos += mMixBufferLenNanos;
    }

    /**
     * Find all beats that should be in this mix
     * @param limitForReadAheadNanos
     */
    private void buildMix(long limitForReadAheadNanos) {

        // mix starts at mMixBufferStartPointNanos and continues up to limitForReadAheadNanos

        includeSpanningBeatsFromLastMix();

        while (isPlaying &&          // stop/pause basically aborts
            !mIsMixBufferComplete) { // keep going till have completed the buffer (can exit before if stream too far ahead)

            // get next beat regardless of how long till it plays

            PlayedBeat nextPlayableBeat = findNextUnplayedBeat();
            if (nextPlayableBeat == null) {                 // should never be null, error already logged, allow caller to exit gracefully
                String msg = String.format("buildMix: iterations (%s) no next beat, abort", currentRhythmIteration);
                AndroidResourceManager.logw(LOG_TAG, msg);
                throw new IllegalStateException(msg);
            }

            // mStreamingIntoIteration may have been incremented to find the next beat, perhaps now finished

            if (atEndOfStreamingIterations()) {             // stop playing beats, but keep playing for the current rhythm iteration to reach end
                AndroidResourceManager.logv(LOG_TAG, String.format("buildMix: gone past end of streaming iterations, set last buffer complete (current=%s) (streamed=%s)",
                        currentRhythmIteration, mStreamingIntoIteration));
                mIsMixBufferComplete = true;
                return;
            }

            long startTillNextPlaysAtNanos = getNanosTillPlays(nextPlayableBeat);   // how long from the beginning is it going to play at

            if (startTillNextPlaysAtNanos < mMixBufferStartPointNanos) {            // can happen if the model changes, reset to make sure it's not picked up again
                nextPlayableBeat.setLastPlayedIteration(mStreamingIntoIteration);
                AndroidResourceManager.logv(LOG_TAG, "buildMix: beat should have been streamed in before this iteration, ignoring (and resetting) it");
                continue;
            }

            if (startTillNextPlaysAtNanos > limitForReadAheadNanos) {               // at end of reading ahead
                mIsMixBufferComplete = true;
                continue;                                                           // return just as good
            }


            // include this beat

            includeBeatInMix(nextPlayableBeat, startTillNextPlaysAtNanos, true);                               // it's newly added here
        }
    }

    private void includeBeatInMix(PlayedBeat beat, long startTillNextPlaysAtNanos, boolean isNewThisMix) {

        if (isNewThisMix) {
            beat.setLastPlayedIteration(mStreamingIntoIteration);
        }

        PlatformResourceManager.SoundResource soundRes = beat.getSoundResource();

        boolean played = soundRes != null && soundRes.isPlayable();
        if (played) {
            final short[] soundData = mStreamStrategy.getSoundData(soundRes);

            final long mixOffsetNanos = isNewThisMix
                    ? startTillNextPlaysAtNanos - mMixBufferStartPointNanos // where in the buffer it begins to play
                    : 0L;                                                   // not new, overlaps at beginning

            long dataPlayingTimeNanos = soundRes.getPlayingTimeNanos();
            if (dataPlayingTimeNanos == -1L) {                              // lazily calc the time only first time needed
                dataPlayingTimeNanos = StreamAudioTrackStrategy.calcPlayingTimeNanos(soundData);
                soundRes.setPlayingTimeNanos(dataPlayingTimeNanos);
            }

            final long remainingToBePlayedNanos = isNewThisMix
                    ? dataPlayingTimeNanos
                    : dataPlayingTimeNanos - beat.getStreamedUptoPointNanos();  // streamed up to in the beat's sound data

            final long remainingAfterThisMixNanos =
                    remainingToBePlayedNanos - (mMixBufferLenNanos - mixOffsetNanos);

            final long dataOffsetNanos = isNewThisMix
                    ? 0L
                    : beat.getStreamedUptoPointNanos();

            final boolean isOverlapping = remainingAfterThisMixNanos > 0;

//            // debug stuff
//            final int mixOffsetShorts = (int) (mixOffsetNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//            final int mixLenShorts = (int) (mMixBufferLenNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//            final int dataOffsetShorts = (int) (dataOffsetNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//            final int remainsShorts = (int) (remainingAfterThisMixNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//            final int playsAtShorts = (int) (startTillNextPlaysAtNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
//            AndroidResourceManager.logv(LOG_TAG, String.format("includeBeatInMix: num=%s%s, samples=%s, new=%s, playsFromStart=%s, mixAt=%s, mixLen=%s, soundAt=%s, overlaps=%s%s%s",
//                    beat.getPosition(), (beat instanceof PlayedBeat.PlayedFullBeat ? "" : " (sub)"),
//                    soundData.length, isNewThisMix,
//                    (isNewThisMix ? playsAtShorts : "true"),
//                    mixOffsetShorts, mixLenShorts, dataOffsetShorts, isOverlapping,
//                    (isOverlapping ? ", remains=" : ""),
//                    (isOverlapping ? remainsShorts : "")));

            // mix the sound data into what's already there

            RhythmBeatType rbt = beat.getRhythmBeatType();
            float vol = rbt.getVolume();
            mixIn(soundData, vol, mixOffsetNanos, dataOffsetNanos);

            // set data on the beat and ready for next mix

            if (isOverlapping) {
                final long streamedUpToThisMixNanos = dataPlayingTimeNanos - remainingAfterThisMixNanos;
                beat.setStreamedUptoPointNanos(streamedUpToThisMixNanos);
                mBeatsSpanningMixes.add(beat);      // ready for next mix
            }
            else {
                beat.setStreamedUptoPointNanos(0L); // probably not needed, but tidy
            }

        }
    }

    private void mixIn(short[] soundData, float volume, long mixOffsetNanos, long dataOffsetNanos) {

        final int mixOffsetShorts = (int) (mixOffsetNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);
        int dataOffset = (int) (dataOffsetNanos / StreamAudioTrackStrategy.NANOS_PER_SHORT);

        if (soundData == null) {
            AndroidResourceManager.logw(LOG_TAG, "mixIn: no sound data, must be during a change");
            return;
        }

        if (dataOffset >= soundData.length) {
            AndroidResourceManager.logw(LOG_TAG, "mixIn: dataOffset off end of sound data, nothing to play, bug?");
            return;
        }

        if (mixOffsetShorts > mMixData.length) {
            AndroidResourceManager.logw(LOG_TAG, "mixIn: mixOffsetShorts off end of mix data, must be a bug");
            return;
        }

        if (mixOffsetShorts < 0) {
            AndroidResourceManager.loge(LOG_TAG, "mixIn: mixOffsetShorts < 0, must be a bug");
            return;
        }

//        AndroidResourceManager.logv(LOG_TAG, String.format("mixIn: soundDataOffset=%s, soundData len=%s, mixDataOffset=%s, mixData len=%s",
//                dataOffset, soundData.length, mixOffsetShorts, mMixData.length));

        for (int i = mixOffsetShorts; i < mMixData.length; i++) {

            // use int so can detect clipping to set min/max values
            int dataShort = (int) (soundData[dataOffset] * volume);
            int mixDataInt = mMixData[i] + dataShort;
            int mixShort = Math.min(
                                Math.max(mixDataInt,
                                    Short.MIN_VALUE),
                                Short.MAX_VALUE);

            mMixData[i] = (short) mixShort;

            if (++dataOffset == soundData.length) {         // done with sound data
//                AndroidResourceManager.logv(LOG_TAG, String.format("mixIn: done with sound data mixDataOffset=%s, mixData len=%s", i, mMixData.length));
                break;
            }
        }
    }

    /**
     * Beats that were picked up for the previous mix but whose data spanned over the end and so
     * need part of their data to be in the next mix
     */
    private void includeSpanningBeatsFromLastMix() {

//        AndroidResourceManager.logv(LOG_TAG, String.format("includeSpanningBeatsFromLastMix: num beats from prev iteration = %s", mBeatsSpanningMixes.size()));

        for (int i = mBeatsSpanningMixes.size()-1; i >= 0; i--) {   // backwards so deletes not a problem

            PlayedBeat beat = mBeatsSpanningMixes.remove(i);        // next method will re-add if necessary
            includeBeatInMix(beat, 0L, false);                      // not new, so 2nd param is redundant anyway
        }
    }

    /**
     * Returns the nanos elapsed from the start till next plays
     * @param beat
     * @return
     */
    @Override
    protected long getNanosTillPlays(PlayedBeat beat) {

        int lastPlayedIteration = beat.getLastPlayedIteration();
        long nanosSetToPlayAt = (long) (nanosPerRhythm * beat.getDeltaStart()); // when it plays

        long elapsedFromStartNextPlaysAtNanos = (lastPlayedIteration + 1) * nanosPerRhythm + nanosSetToPlayAt;

//        AndroidResourceManager.logv(LOG_TAG, String.format("getNanosTillPlays: beat plays next at nanosFromStart=%s", elapsedFromStartNextPlaysAtNanos));

        return elapsedFromStartNextPlaysAtNanos;
    }

    /**
     * Get the earliest occurrence of a beat to play regardless of the time
     * @return
     */
    @Override
    protected PlayedBeat findNextUnplayedBeat() {

        // find the lowest last played iteration in all the beats, play the beat that has that and is reached first
        // to do this keep the last beat streamed position (at nanos)

        // figure out which beats need to play soon enough to sound them
        for (int j = 0; j < 2; j++) {   // go max of twice through the beats
                                        // because the streamedUpToIteration counter is incremented inside the loop if needed
                                        // meaning any played beats for the prev value cannot fail the condition for the 2nd loop round

            if (j == 1) { // gone around all the beats once, nothing found with lower than streamed
                mStreamingIntoIteration++; // streaming up to the next iteration
            }

            for (int i = 0; i < mOrderedBeats.length; i++) {
                PlayedBeat beat = mOrderedBeats[i];
                int lastPlayedIteration = beat.getLastPlayedIteration();

                if (lastPlayedIteration < mStreamingIntoIteration) { // hasn't yet been played during the current iteration

                    if (lastPlayedIteration < mStreamingIntoIteration -1) {  // have seen this stop/start the rhythm in code (ie. immediately)
                        AndroidResourceManager.logw(LOG_TAG, "findNextUnplayedBeat: beat was not played in the previous to last iteration");
                    }

                    return beat;
                }
            }
        }

        AndroidResourceManager.loge(LOG_TAG, "findNextUnplayedBeat: no beat found even after incrementing streaming iteration, must be a bug...");
        return null;
    }

    /**
     * Reset streaming if paused because the buffer will be full and it will be
     * obsolete
     */
    @Override
    synchronized void initBeats() {

        if (!isStopped) {
            mStreamingIntoIteration = currentRhythmIteration;
            mMixBufferStartPointNanos = currentRhythmIteration * nanosPerRhythm + rhythmProgressNanos;
            mBeatsSpanningMixes.clear();
        }

        super.initBeats();
    }

    @Override
    protected synchronized void pausePlay() {
        super.pausePlay();
        initBeats();
    }

//    @Override
//    public synchronized void startPlayAt(boolean isPaused, int currentIteration, float playingDelta) {
//        mStreamingIntoIteration = currentIteration;
//        mMixBufferStartPointNanos = (long) (currentRhythmIteration * nanosPerRhythm +
//                rhythmProgressNanos * playingDelta);
//        super.startPlayAt(isPaused, currentIteration, playingDelta);
//    }

}
