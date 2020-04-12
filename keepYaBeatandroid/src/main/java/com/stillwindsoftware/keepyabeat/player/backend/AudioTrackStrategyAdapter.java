package com.stillwindsoftware.keepyabeat.player.backend;

import android.app.Service;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidSoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceLoadError;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AudioTrackStrategyAdapter implements AndroidAudioStrategy {

    private static final String LOG_TAG = "KYB-"+AudioTrackStrategyAdapter.class.getSimpleName();

    protected int mLatency = DEFAULT_LATENCY;

    protected AudioTrack mAudioTrack;

    protected final Service mBeatTrackerService;
    protected final AndroidResourceManager mResourceManager;

    // an empty buffer to send 0 to the track to fill up the buffer size
    protected byte[] mEmptyBytes = new byte[MIN_MONO_16_BUFFER_SIZE];

    // implement a threaded consumer/producer pattern where the main thread (BeatTracker) puts sounds
    // to the queue to be picked up by the play sound thread
    protected Thread mPlayThread;
    protected ReentrantLock mLock;
    protected Condition mNoData, mQueueNotReady;
    protected static final int RING_BUFFER_LEN = 10;
    protected volatile int mNextToReadFromQueue = 0, mNextToWriteToQueue = 0;

    AudioTrackStrategyAdapter(Service beatTrackerService) {
        mBeatTrackerService = beatTrackerService;
        mResourceManager = ((KybApplication)mBeatTrackerService.getApplication()).getResourceManager();

        // create the audio track and the map for sounds
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC
                , SAMPLE_RATE_22050
                , AudioFormat.CHANNEL_OUT_MONO
                , AudioFormat.ENCODING_PCM_16BIT
                , MIN_MONO_16_BUFFER_SIZE
                , AudioTrack.MODE_STREAM);

        determineAudioLatency(beatTrackerService);

        initQueues();
        createSoundPool();

        // set it playing immediately, hopefully this will take care of warming up the circuitry
        mAudioTrack.play();

        // init the threading
        initPlayThreading();
    }

    protected abstract void createSoundPool();

    protected abstract void initQueues();

    private void determineAudioLatency(Service beatTrackerService) {
        // get the latency from hidden method
        AudioManager am = (AudioManager) beatTrackerService.getSystemService(Context.AUDIO_SERVICE);
        try {
            Method m = am.getClass().getMethod("getOutputLatency", int.class);
            mLatency = (Integer)m.invoke(am, AudioManager.STREAM_MUSIC);
            String frames = "not determinable";
            String samples = "not determinable";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                frames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                samples = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            }

            AndroidResourceManager.logd(LOG_TAG, String.format("determineAudioLatency: audio manager reports latency = %s, frames per buffer = %s, sample rate = %s"
                    , mLatency, frames, samples));
        } catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "determineAudioLatency: audio manager could not get latency, using default", e);
        }

    }

    public long getLatency() {
        return mLatency;
    }

    /**
     * Called by loadSoundPool() for each sound resource to attempt to load
     * @param soundRes
     */
    protected final Object loadSoundResource(AndroidSoundResource soundRes, boolean returnShortArray) {

        AndroidResourceManager.logd(LOG_TAG, String.format("loadSoundResource: load sound %s", soundRes.getUriPath()));
        InputStream is = null;

        try {
            if (PlatformResourceManager.SoundResourceType.INT == soundRes.getType()) {
                is = mBeatTrackerService.getResources().openRawResource(soundRes.getResId());
            }
            else {
                // local uri only
                is = mBeatTrackerService.openFileInput(soundRes.getUriPath());
            }

            return mResourceManager.loadAndTestSoundFromInputStream(mBeatTrackerService, is, true, returnShortArray);

        }
        catch (IOException e) {
            AndroidResourceManager.loge(LOG_TAG, String.format("Unable to load sound %s", soundRes.getUriPath()), e);
            return null;
        }
        catch (IllegalArgumentException e) {
            AndroidResourceManager.loge(LOG_TAG, String.format("Invalid format for sound %s", soundRes.getUriPath()), e);
            return null;
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
    }

    /**
     * Should not occur often if the wake up value is good. ie. mostly should
     * find sleep() wakes up accurately about the sleep margin and then it's far enough
     * ahead to write silence before the sound plays.
     */
    @Override
    public float getTooLongPassedThreshold() {
        return 2.f;
    }

    private void initPlayThreading() {
        mLock = new ReentrantLock();
        mNoData = mLock.newCondition();
        mQueueNotReady = mLock.newCondition(); // only used for streamed version

        mPlayThread = new Thread() {

            private boolean isRunning = true;

            @Override
            public void run() {

//                mAudioTrack.write(mEmptyBytes, 0, mEmptyBytes.length); // during warm up, send an empty buffer to get things moving

                while (isRunning) {
                    initTakeTempStructures();

                    // await() can be interrupted, if so the thread will exit on the next go round
                    mLock.lock();
                    try {
                        while (noQueuedDataReady()) {
                            mNoData.await();
                        }

                        reportQueue(false, "Reading from queue");
                        takeFromQueue();

                        if (++mNextToReadFromQueue >= RING_BUFFER_LEN) { // ring buffering, have read last in the array, start at the beginning again
                            mNextToReadFromQueue = 0;
                        }

                        mQueueNotReady.signal(); // signal the streaming thread

                    } catch (InterruptedException e) {
                        // means the binder is exiting and called releaseResources()
                        isRunning = false;
                        continue;
                    } finally {
                        mLock.unlock();
                    }

                    try {
                        writeTakenData();
                    }
                    catch (Exception e) {
                        AndroidResourceManager.loge(LOG_TAG, "Play thread unexpected exception on write, continuing", e);
                    }
                }

                AndroidResourceManager.logd(LOG_TAG, "Play thread has been interrupted and will exit");
            }

        };
        mPlayThread.start();
    }

    /**
     * According to implementation initialize the data structures to hold temporary queue data
     */
    protected abstract void initTakeTempStructures();

    /**
     * Having taken the data from the queue, now write it out to the audio track
     */
    protected abstract void writeTakenData();

    /**
     * Move queued data from queue to temporary data structures for writing
     */
    protected abstract void takeFromQueue();

    /**
     * The conditions that mean no data is ready in the queue, differs by
     * implementation
     * @return
     */
    protected abstract boolean noQueuedDataReady();

//    private void writeSilence(long nanos) {
//        long startWriteAtNanos = System.nanoTime();
//
//        int totalBytesWritten = 0;
//        int millisOfSilence = (int) (nanos / BeatTracker.NANOS_PER_MILLI);
//
//        if (millisOfSilence > 0) {
//            final int millisToSamples = (int) (SAMPLES_PER_MILLI * millisOfSilence);
//            final int bytesOfSilence = millisToSamples * SUPPORTED_BIT_DEPTH_TO_BYTES;
//
//            while (totalBytesWritten < bytesOfSilence) {
//                int bytesToGo = bytesOfSilence - totalBytesWritten;
//                int bytesThisIteration = Math.min(bytesToGo, mEmptyBytes.length);
//                mAudioTrack.write(mEmptyBytes, 0, bytesThisIteration);
//                totalBytesWritten += bytesThisIteration;
//
////                            // attempt to debug the timing, what's the log sequence at this time
////                            AndroidResourceManager.logd(LOG_TAG, String.format("playThread: seq=%s", ++mAudioLogSeq));
//
////							AndroidResourceManager.logd(LOG_TAG,
////									String.format(
////											"\t iterating advance silence = %s, total samples = %s, total bytes to write = %s, written so far = %s, SAMPLES_PER_MILLI=%s",
////											millisOfSilence,
////											millisToSamples, bytesOfSilence,
////											totalBytesWritten, SAMPLES_PER_MILLI));
//            }
//
//            // TODO REMOVE THIS DEBUGGING
//            long allWrittenAtNanos = System.nanoTime();// how long elapsed since the sound was sent to the track
//            float samplesWritten = (float)totalBytesWritten / SUPPORTED_BIT_DEPTH_TO_BYTES;
//            float secsWritten = samplesWritten / SUPPORTED_SAMPLE_RATE;
//            float millisWritten = secsWritten * 1000f;
//            AndroidResourceManager.logd(LOG_TAG, String.format("\twriteSilence: writing took = %s, total bytes = %s, playing time of data = %.2f, cross checks tally = %s",
//                    (allWrittenAtNanos - startWriteAtNanos) / BeatTracker.NANOS_PER_MILLI,
//                    totalBytesWritten,
//                    millisWritten,
//                    samplesWritten == millisToSamples && millisWritten == millisOfSilence));
//        }
//        else { // should never be no silence to write
//            AndroidResourceManager.logw(LOG_TAG, String.format("writeSilence: none to write (%s)", millisOfSilence));
//        }
//    }

    /**
     * Only to show what the queue looks like for debugging... on play thread, don't leave uncommented
     * @param atWrite
     * @param whereAt
     */
    protected void reportQueue(boolean atWrite, String whereAt) {
//        AndroidResourceManager.logv(LOG_TAG, String.format("reportQueue: |%s|%s|%s|%s|%s|%s|%s|%s|%s|%s| at %s",
//                getQueueReportValueAt(0, atWrite),
//                getQueueReportValueAt(1, atWrite),
//                getQueueReportValueAt(2, atWrite),
//                getQueueReportValueAt(3, atWrite),
//                getQueueReportValueAt(4, atWrite),
//                getQueueReportValueAt(5, atWrite),
//                getQueueReportValueAt(6, atWrite),
//                getQueueReportValueAt(7, atWrite),
//                getQueueReportValueAt(8, atWrite),
//                getQueueReportValueAt(9, atWrite),
//                whereAt
//                ));
    }

    protected abstract String getQueueReportValueAt(int i, boolean atWrite);


    @Override
    public void releaseResources() {
        if (mPlayThread != null) {
            mPlayThread.interrupt();
        }
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }

        clearSoundPool();
    }

    protected abstract void clearSoundPool();

    @Override
    public void notifyPlayStopped() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.flush();
        }
    }

}
