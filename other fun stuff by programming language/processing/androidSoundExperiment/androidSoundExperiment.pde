/*
 * See comments at start of the 3 main tests methods
 * Basically, SoundPool seems like the easiest method
 */
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import android.util.Log;
import android.media.*;
import android.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

static final String LOG_TAG = "androidSoundExperiment";
static final long NANOS_PER_SECOND = 1000000000L;
static final long NANOS_PER_MILLI = 1000000L;
static final String[] files = new String[] { "clap_high.wav", "clap_low.wav", "maracas.wav", "maracas-lowrate.wav",
                                  "tamborine.wav", "tick_wooden.wav", "tock_wooden.wav",
                                  "beep_1.wav", "beep_2.wav", "beep_3.wav",
                                  "clap_double.wav", "tick_middle.wav", "tock_shallow.wav" };
static final String MIDI_TEST_FILE = "morcheeba-be_yourself.mid", MIDI_TEMP_FILE = "testMidiTmp.mid";

AudioFileLoader audioLoader = new AudioFileLoader();
AudioPlayer audioPlayer = new AudioPlayer();

Rectangle timedBtn, streamedBtn, pooledBtn, midiBtn;
boolean testTimedCond = false, testStreamedCond = false, testPooledCond = false, midiBtnCond = false;

void setup() {
                                  
  textSize(40);
  strokeWeight(4);
  textAlign(CENTER, CENTER);
  
  timedBtn = new Rectangle(width / 5, height / 8, width / 2, height / 8);
  streamedBtn = new Rectangle(timedBtn.x, timedBtn.y + timedBtn.h * 2, timedBtn.w, timedBtn.h);
  pooledBtn = new Rectangle(timedBtn.x, timedBtn.y + timedBtn.h * 4, timedBtn.w, timedBtn.h);
  midiBtn = new Rectangle(timedBtn.x, timedBtn.y + timedBtn.h * 6, timedBtn.w, timedBtn.h);
}

void draw() {
  background(140);
  fill(255);
  stroke(0);
  rect(timedBtn.x, timedBtn.y, timedBtn.w, timedBtn.h);
  rect(streamedBtn.x, streamedBtn.y, streamedBtn.w, streamedBtn.h);
  rect(pooledBtn.x, pooledBtn.y, pooledBtn.w, pooledBtn.h);
  rect(midiBtn.x, midiBtn.y, midiBtn.w, midiBtn.h);
  
  fill(testStreamedCond || testPooledCond || midiBtnCond ? 140 : 0);
  if (testTimedCond) {
    text("stop timed test", timedBtn.x + timedBtn.w / 2, timedBtn.y + timedBtn.h / 2);
  }
  else {
    text("start timed test", timedBtn.x + timedBtn.w / 2, timedBtn.y + timedBtn.h / 2);
  }

  fill(testTimedCond || testPooledCond || midiBtnCond ? 140 : 0);
  if (testStreamedCond) {
    text("stop streamed test", streamedBtn.x + streamedBtn.w / 2, streamedBtn.y + streamedBtn.h / 2);
  }
  else {
    text("start streamed test", streamedBtn.x + streamedBtn.w / 2, streamedBtn.y + streamedBtn.h / 2);
  }

  fill(testTimedCond || testStreamedCond || midiBtnCond ? 140 : 0);
  if (testPooledCond) {
    text("stop pooled test", pooledBtn.x + pooledBtn.w / 2, pooledBtn.y + pooledBtn.h / 2);
  }
  else {
    text("start pooled test", pooledBtn.x + pooledBtn.w / 2, pooledBtn.y + pooledBtn.h / 2);
  }

  fill(testTimedCond || testStreamedCond || testPooledCond ? 140 : 0);
  if (midiBtnCond) {
    text("stop midi test", midiBtn.x + midiBtn.w / 2, midiBtn.y + midiBtn.h / 2);
  }
  else {
    text("start midi test", midiBtn.x + midiBtn.w / 2, midiBtn.y + midiBtn.h / 2);
  }
}

void loadWaveFiles() {
  for (int i = 0; i < files.length; i++) {
    try {
      audioLoader.loadWaveAudioFile(files[i]);
    }
    catch (IllegalArgumentException e) {
      logDebug("Failed to load file '"+files[i]+"', (note: only channels 1 or 2, PCM 16-bit supported)");
    }
  }
  
  // just to make sure it's clear what's loaded
  for (IWaveAudio audio : audioLoader.getAudios().values()) {
    logDebug("loaded : "+audio);
  }
}

void mouseReleased() {
  if (timedBtn.contains(mouseX, mouseY) && !testStreamedCond && !testPooledCond && !midiBtnCond) {
    testTimedCond = !testTimedCond;
  
    if (testTimedCond) {
      loadWaveFiles();
      audioPlayer.prepareAudioTracks(audioLoader.getAudios());
      audioPlayer.startTimeDelayTests(audioLoader.getAudios());
    }
    else {
      logDebug("stopping... release resources");
      audioPlayer.stopTests();
      audioPlayer.releaseResources();
      audioLoader.releaseResources();
    }  
  }
  else if (streamedBtn.contains(mouseX, mouseY) && !testTimedCond && !testPooledCond && !midiBtnCond) {
    testStreamedCond = !testStreamedCond;

    if (testStreamedCond) {
      loadWaveFiles();
      audioPlayer.prepareAudioTracks(audioLoader.getAudios());
      audioPlayer.startStreamedTests(audioLoader.getAudios());
    }
    else {
      logDebug("stopping... release resources");
      audioPlayer.stopTests();
      audioPlayer.releaseResources();
      audioLoader.releaseResources();
    }  
  }
  else if (pooledBtn.contains(mouseX, mouseY) && !testTimedCond && !testStreamedCond && !midiBtnCond) {
    testPooledCond = !testPooledCond;

    if (testPooledCond) {
      audioPlayer.startSoundPoolExactTimeTests(files);
    }
    else {
      logDebug("stopping... release resources");
      audioPlayer.stopTests();
    }  
  }
  else if (midiBtn.contains(mouseX, mouseY) && !testTimedCond && !testStreamedCond && !testPooledCond) {
    midiBtnCond = !midiBtnCond;

    if (midiBtnCond) {
      audioPlayer.startMidiTests(MIDI_TEST_FILE);
    }
    else {
      logDebug("stopping... release resources");
      audioPlayer.stopTests();
    }  
  }
  //
  
}



interface IWaveAudio {
  public int getChannels();
  public int getBitDepth();
  public int getSampleRate();
  public int getSamples();
  public float getMillis();
  public byte[] getAudioData();
}

class WaveAudio implements IWaveAudio {
  private final byte[] mAudioData;
  private final int mChannels, mSampleRate, mSamples, mBitDepth;
  private final float mSeconds;
  private final String mFilename;
  
  public WaveAudio(String filename, byte[] data, int channels, int bitDepth, int sampleRate, int samples, float seconds) {
    mFilename = filename;
    mAudioData = data;
    mChannels = channels;
    mBitDepth = bitDepth;
    mSampleRate = sampleRate;
    mSamples = samples;
    mSeconds = seconds;
  }
  
  @Override
  public int getChannels() {
    return mChannels;
  }
  
  @Override
  public int getBitDepth() {
    return mBitDepth;
  }
  
  @Override
  public int getSampleRate() {
    return mSampleRate;
  }
  
  @Override
  public int getSamples() {
    return mSamples;
  }
  
  @Override
  public float getMillis() {
    return mSeconds * 1000f;
  }
  
  @Override
  public byte[] getAudioData() {
    return mAudioData;
  }
  
  @Override
  public String toString() {
    return mFilename + " [channels=" + mChannels + ",bitDepth=" + mBitDepth + ",sampleRate=" + mSampleRate + ",secs=" + mSeconds + ",millis=" + getMillis() + "]";
  }
}

class Rectangle {
  float x, y, w, h;
  public Rectangle(float x, float y, float w, float h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
  }
  
  public boolean contains(float x, float y) {
    return (x >= this.x && x <= this.x + this.w
            && y >= this.y && y <= this.y + this.h);
  }
}

/**
 * Takes the audios and allocates the needed AudioTracks for the session, when play an audio
 * is called, it pushes the data to the appropriate track
 */
class AudioPlayer {
  private final int MONO_PCM_16_BIT = 0, STEREO_PCM_16_BIT = 1, MONO_PCM_8_BIT = 2, STEREO_PCM_8_BIT = 3;
  private final int SAMPLE_RATE_22050 = 22050;
  private final int SAMPLE_RATE_44100 = 44100;
  private final int MIN_MONO_16_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE_44100, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
  private final int MIN_STEREO_16_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE_44100, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
  private final int MIN_MONO_8_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE_44100, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_8BIT);
  private final int MIN_STEREO_8_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE_44100, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_8BIT);

  private HashMap<Integer, AudioTrack> mAudioTracks = new HashMap<Integer, AudioTrack>();
  // an empty buffer to send 0 to the track to fill up the buffer size
  private byte[] mEmptyBytes = new byte[MIN_STEREO_16_BUFFER_SIZE];
  private Thread mPlayThread;
  
  /**
   * Find and allocate audio tracks for the wave audios
   * Note. only 16 bit pcm supported at the moment
   */   
  public void prepareAudioTracks(HashMap<String, IWaveAudio> audios) {
    logDebug("preparing audio tracks: min buffer size mono16="+MIN_MONO_16_BUFFER_SIZE
            +" stereo16="+MIN_STEREO_16_BUFFER_SIZE
            +" mono8="+MIN_MONO_8_BUFFER_SIZE+" stereo8="+MIN_STEREO_8_BUFFER_SIZE);
    
    for (IWaveAudio audio : audios.values()) {
      try {
        getTrackForAudio(audio);
      } 
      catch (IllegalArgumentException e) {
        logException("prepareAudioTracks: File format not supported = "+audio, e);
      } 
    }
  }
  
  public AudioTrack getTrackForAudio(IWaveAudio audio) throws IllegalArgumentException {
    int trackKey = -1;
    int channelConfig = -1;
    int encoding = -1;
    int minBuffer = -1;
    
    if (audio.getChannels() == 1 && audio.getBitDepth() == 16) {
      trackKey = MONO_PCM_16_BIT;
      channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
      encoding = AudioFormat.ENCODING_PCM_16BIT;
      minBuffer = MIN_MONO_16_BUFFER_SIZE;
    }
    else if (audio.getChannels() == 2 && audio.getBitDepth() == 16) {
      trackKey = STEREO_PCM_16_BIT;
      channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
      encoding = AudioFormat.ENCODING_PCM_16BIT;
      minBuffer = MIN_STEREO_16_BUFFER_SIZE;
    }
    
    // TODO if can't use 8 bit stuff chop all of it out
    else if (audio.getChannels() == 1 && audio.getBitDepth() == 8) {
      trackKey = MONO_PCM_8_BIT;
      channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
      encoding = AudioFormat.ENCODING_PCM_8BIT;
      minBuffer = MIN_MONO_8_BUFFER_SIZE;
    }
    else if (audio.getChannels() == 2 && audio.getBitDepth() == 8) {
      trackKey = STEREO_PCM_8_BIT;
      channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
      encoding = AudioFormat.ENCODING_PCM_8BIT;
      minBuffer = MIN_STEREO_8_BUFFER_SIZE;
    }
    
    if (trackKey == -1 || channelConfig == -1 || encoding == -1) {
      throw new IllegalArgumentException("Unsupported channels/bitDepth combo");    
    }    
      
    AudioTrack track = mAudioTracks.get(trackKey);
    if (track == null) {
      track = new AudioTrack(AudioManager.STREAM_MUSIC
                            , SAMPLE_RATE_44100
                            , channelConfig
                            , encoding
                            , minBuffer
                            , AudioTrack.MODE_STREAM);
      mAudioTracks.put(trackKey, track);
//      track.play();
    }
    return track;
  }
  
  /**
   * Problems with AudioTrack solutions:
   * 1) Loading different bitDepth/sampleRate/format files is problematic. Especially, would have to manually decode anything other than PCM 16-bit
   * 2) Streaming would seem to be the way to get the best accuracy and not lose sounds, but it shares the same problem that AudioTrack blocks
   *    on write(), and sometimes at least as long as the playback... meaning a buffering strategy must be coded before playing starts.
   * 
   * Because of these problems this method tries to use the sound pool api instead. It does rely on reasonably accurate sleep() for accuracy,
   * but the upside is any format file that Android supports can be played out of the box.
   * So far trying on Nexus 7 the accuracy is easily good enough.
   */
  public void startSoundPoolExactTimeTests(final String[] filenames) {

    mPlayThread = new Thread() {
      @Override
      public void run() {
        boolean isPlaying = true;
        
        // waits for all the sounds to load
        SoundPool pool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        FilePoolMap[] fileStreamIds = null;
        try {
          fileStreamIds = initSoundPool(pool, filenames);
        }
        catch (Exception e) {
          logException("prevented test by exception loading pool", e);
          isPlaying = false; // will just drop through
        }
        
        final int millisBetweenSounds = 400;
        final int millisSleepToAdvance = 1; // how soon to wake up before a sound
        
        long nextSoundPlayTargetNanos = -1L;
        
        while (isPlaying) {
          
          for (FilePoolMap audio : fileStreamIds) {
            try {
              logDebug("test audio = "+audio.filename);
              
              long loopTimeNanos = System.nanoTime();
              
              // first time accuracy is irrelevant
              if (nextSoundPlayTargetNanos == -1L) {
                nextSoundPlayTargetNanos = loopTimeNanos + millisBetweenSounds * NANOS_PER_MILLI;
              }
              else {
                int accuracy = (int)((loopTimeNanos - nextSoundPlayTargetNanos) / NANOS_PER_MILLI);
                
                if (accuracy != 0) {
                  logDebug(String.format("\t accuracy = %s, amount = %s"
                                      , accuracy > 0 ? "late" : "early"
                                      , accuracy)); 
                }
                
                nextSoundPlayTargetNanos += millisBetweenSounds * NANOS_PER_MILLI;
              }
              
              pool.play(audio.streamId, 1f, 1f, 0, 0, 1f);
              
              loopTimeNanos = System.nanoTime();
              
              // want to play at the target time 
              // but have to take care that wake up might not be very accurate
              long nanosToGo = nextSoundPlayTargetNanos - loopTimeNanos;
              int millisToSleep = (int)(nanosToGo / NANOS_PER_MILLI) - millisSleepToAdvance;
              
              logDebug(String.format("\t next sound in %s, sleep for %s"
                                        , nanosToGo / NANOS_PER_MILLI
                                        , millisToSleep
                                        ));
              
              if (millisToSleep > 0) {
                sleep(millisToSleep);
              }
              else {
                logDebug(String.format("\t PROBLEM: it all took so long this iteration that we're already behind by %s", millisToSleep));
              }
            } 
            catch (InterruptedException e) {
              isPlaying = false;
              break;
            }
            catch (IllegalArgumentException e) {
              logException("startTests: File format not supported = "+audio, e);
            } 
            
            // stop tests when stopTests() called, sleep() means this is obsolete, but keep in for now in case remove sleep()
            if (isInterrupted()) {
              isPlaying = false;
              break;
            }
          }
        }        
        logDebug("play thread interrupted... quitting");
        pool.release();
      }
    };
    mPlayThread.start();
  }
  
  private Uri makeTempFileUriFromAsset(String assetFileName) throws IOException {
    // write the asset file to the temp file
    return makeTempFileUriFromIs(getAssets().open(assetFileName));
  }
  
  private Uri makeTempFileUriFromIs(InputStream is) throws IOException {
    
    FileOutputStream fout = null;
    
    try {
      File outputFile = getTempFile(MIDI_TEMP_FILE);
      fout = new FileOutputStream(outputFile);
      
      byte[] b = new byte[1024];
      int noOfBytes = 0;
      
      while( (noOfBytes = is.read(b)) != -1 ) {
        fout.write(b, 0, noOfBytes);
      }
                       
      return Uri.fromFile(outputFile);   
    }
    catch (FileNotFoundException e) {
      throw e;
    }
    catch (IOException e) {
      throw e;
    }
    finally {
      //close the streams
      if (is != null) is.close();
      if (fout != null) fout.close();   
    }
  }
  
  public File getTempFile(String filename) throws IOException {
    File outputDir = getFilesDir(); // private directory for writing files
    return new File(outputDir, filename);
  }
  
  /**
   * Midi
   * Problems with this approach : onPrepared/onCompletion fire, no other listeners
					how to know when the thing actually starts playing... seems long latency.
					no immediate midi streaming... using temporary file... how to put in own sounds, need access to banks info for the midi sequencer?
   */
  public void startMidiTests(final String ... filenames) {

    mPlayThread = new Thread() {
      @Override
      public void run() {
        boolean isPlaying = true;
        
        final int millisBetweenSounds = 400;
        
        long nextSoundPlayTargetNanos = -1L;
        
        MediaPlayer mp = null;
        
        // start the midi player with the test file
        try {
//          Uri tempFileUri = makeTempFileUriFromAsset(MIDI_TEST_FILE);
          File tempFile = getTempFile(MIDI_TEST_FILE);
          Uri tempFileUri = Uri.fromFile(tempFile);
          println("got temp uri = "+tempFileUri);
          mp = new MediaPlayer();
          mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
          MidiTest test = new MidiTest(tempFile);
          test.makeTestFile(1);
          mp.setDataSource(getApplicationContext(), tempFileUri);
          mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {                
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
              logDebug("playSound: media player onError what="+what+" extra="+extra);
              return false;
            }
          });
          mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {                
            @Override
            public void onCompletion(MediaPlayer mp) {
              logDebug("playSound: media player onCompletion");
              mp.reset();
              mp.release();
            }
          });
          mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
              logDebug("playSound: media player prepared... starting");
              mp.start();
            }
          });
          mp.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
              logDebug("playSound: media player buffering updated ... percent = "+percent);
            }
          });
          mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
              String interp = null;
              switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOWN : interp = "MEDIA_INFO_UNKNOWN"; break;
                case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING : interp = "MEDIA_INFO_VIDEO_TRACK_LAGGING"; break;
//                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START : interp = "MEDIA_INFO_VIDEO_RENDERING_START"; break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_START : interp = "MEDIA_INFO_BUFFERING_START"; break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END : interp = "MEDIA_INFO_BUFFERING_END"; break;
                case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING : interp = "MEDIA_INFO_BAD_INTERLEAVING"; break;
                case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE : interp = "MEDIA_INFO_NOT_SEEKABLE"; break;
                case MediaPlayer.MEDIA_INFO_METADATA_UPDATE : interp = "MEDIA_INFO_METADATA_UPDATE"; break;
//                case MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE : interp = "MEDIA_INFO_UNSUPPORTED_SUBTITLE"; break;
//                case MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT  : interp = "MEDIA_INFO_SUBTITLE_TIMED_OUT"; break;
              }
              logDebug("playSound: media player info ... "+interp);
              return false;
            }
          });
          mp.prepare();
        }
        catch (Exception e) {
          logException("error in test open media player", e);
          isPlaying = false;
        }
        
        // if previous test failed it won't enter this loop
        
        while (isPlaying) {
          try {
            println("playing...");
            sleep(millisBetweenSounds);
          }
          catch (InterruptedException e) {
            isPlaying = false;
          }
//          catch (IllegalArgumentException e) {
//            logException("startTests: File format not supported = "+audio, e);
//          } 
          
          // stop tests when stopTests() called, sleep() means this is obsolete, but keep in for now in case remove sleep()
          if (isInterrupted()) {
            isPlaying = false;
          }
        }  
  
        // got a value, make sure it's released
        if (mp != null) {
          try {
            if (mp.isPlaying()) 
              mp.stop();
              
            mp.reset();
            mp.release();
          }
          catch (IllegalStateException e) {
            // prob means complete happened already
            logException("illegal state probably means already completed", e);
          }
        }      
        logDebug("play thread interrupted... quitting");
      }
    };
    mPlayThread.start();
  }
  
  private FilePoolMap[] initSoundPool(SoundPool pool, String[] filenames) throws IOException, InterruptedException {
    // latch so the method will wait for all streams to load fully
    final CountDownLatch latch = new CountDownLatch(filenames.length);
    
    final FilePoolMap[] loadedFiles = new FilePoolMap[filenames.length];

    pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
      @Override
      public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {        
        if (status != 0) {
          synchronized(loadedFiles) {
            FilePoolMap fileMap = null;
            for (FilePoolMap mapping : loadedFiles) {
              if (mapping.streamId == sampleId) {
                mapping.status = status;
                fileMap = mapping;
              }
            }
            logDebug(String.format("failed to load sampleId = %s, file = '%s'",
                      sampleId,
                      fileMap == null ? "not in mappings!!" : fileMap.filename));
          }
        }
        latch.countDown();
      }
    });
    
    for (int i = 0; i < filenames.length; i++) {
      String filename = filenames[i];
      int streamId = pool.load(getAssets().openFd(filename), 1);
      synchronized(loadedFiles) {
        loadedFiles[i] = new FilePoolMap(filename, streamId);
      }
    }
    
    // wait on the latch, when all done construct the output map 
    latch.await();
    
    for (FilePoolMap mapping : loadedFiles) {
      logDebug(String.format("loaded soundpool: sampleId = %s, file = '%s', ok = %s",
                mapping.streamId, mapping.filename, mapping.status == 0));
    }
    
    return loadedFiles;
  }
  
  /**
   * An attractive method for timing, that does produce good results, but the drawbacks are:
   * 1) AudioTrack.write() blocks so that if sounds are played in quick succession, you reach a point where it can't keep up
   *    and you basically would be forced to buffer for streaming... (see streaming tests below)
   * 2) To use non PCM format files you'd have to decode the files too... and it would be quite a bit of work, given the
   *    limitation of 1) above, I can't see it's worth it.
   */
  public void startTimeDelayTests(final HashMap<String, IWaveAudio> audios) {

    mPlayThread = new Thread() {
      @Override
      public void run() {
        final int millisBetweenSounds = 400;
        final int millisSleepToAdvance = 20; // how soon to wake up before a sound
        
        long lastSoundPlayedAtNanos = -1L;
        long nextSoundPLayTargetNanos = -1L;
        
        boolean isPlaying = true;
        
        while (isPlaying) {
          for (IWaveAudio audio : audios.values()) {
            try {
              logDebug("test audio = "+audio);
              AudioTrack track = getTrackForAudio(audio);
              // not buffering, play immediately
              if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                track.play();
              }
              // remove any data still in the buffer... if all is correct at this point there shouldn't be any
              // but if there was it would postpone the sound until the buffer was played out
              track.flush();
              
              byte[] data = audio.getAudioData();
              int bytes = data.length;
              
              // TODO figure out about sample rate changes... separate audio tracks for each sample rate perhaps?
              int trackRate = track.getPlaybackRate();
              if (audio.getSampleRate() != trackRate) {
                try {
                  int res = track.setPlaybackRate(audio.getSampleRate());
                  logDebug(String.format("\t changed track's playback rate from %s to %s, result = %s", 
                                          trackRate, track.getPlaybackRate(), res));
                }
                catch (Exception e) {
                  logException("Not able to set playback rate", e);
                }
              }
              
              int totalBytesWritten = 0;
              
              // ready to play the sound, but should be here in advance of the actual time it's supposed to play
              if (nextSoundPLayTargetNanos != -1L) {
                long wokeUpAtNanos = System.nanoTime();
                int millisBeforeSound = (int)((nextSoundPLayTargetNanos - wokeUpAtNanos) / NANOS_PER_MILLI);
                if (millisBeforeSound > 0) {
                  final float samplesPerMilli = audio.getSampleRate() / 1000.f;
                  final int samplesBeforeSound = (int)(samplesPerMilli * millisBeforeSound);
                  final int bytesOfSilence = samplesBeforeSound * audio.getBitDepth() / 8;
                  while (totalBytesWritten < bytesOfSilence) {
                    int bytesToGo = bytesOfSilence - totalBytesWritten;
                    int bytesThisIteration = Math.min(bytesToGo, mEmptyBytes.length);
                    track.write(mEmptyBytes, 0, bytesThisIteration);
                    totalBytesWritten += bytesThisIteration;
                    logDebug(String.format("\t iterating advance silence = %s, total samples = %s, total bytes to write = %s, written so far = %s, samplesPerMilli=%s", 
                                        millisBeforeSound, samplesBeforeSound, bytesOfSilence, totalBytesWritten, samplesPerMilli
                                        ));
                  }
                }
                else if (millisBeforeSound == 0) {
                  // wow, very lucky, nothing to do, just play it
                  logDebug("\t Woken up right on time, no advance silence needed!");
                }
                else {
                  // bad, have lagged behind, too much sleep
                  logDebug(String.format("\t lagged behind by %s, need more sleep advance", millisBeforeSound));
                }
              }
              
              // when to time the sound, trying at the point of writing, as the method will block, and probably
              // the buffering of zeros after it will block too
              // if instead the time is taken after playing it would be no more accurate and mean additional work
              lastSoundPlayedAtNanos = System.nanoTime();
              // TODO : PROBLEM HERE WITH TIMING : 
              // this time is when all the bytes got streamed to the audio track, not the actual time the sound has
              // played, which seems to be indeterminate here, timing the next sound off this point will just
              // increase the error
              // 2 possible solutions:
              // first) time off a static point so all timings have the same chance of accuracy, but never being exact or knowing exactly
              // second) stream all the time without breaks, so the track is never starved of data, and therefore the audio equipment's
              //         take off the data times it accurately (see streaming tests below for why this isn't an easy proposition)
              
              nextSoundPLayTargetNanos = lastSoundPlayedAtNanos + millisBetweenSounds * NANOS_PER_MILLI;
              
              track.write(data, 0, data.length);
  
              // however many bytes remain, send the difference to fill up the min buffer
              int bytesBuffered = bytes % (audio.getChannels() == 1 
                                             ? audio.getBitDepth() == 16 ? MIN_MONO_16_BUFFER_SIZE : MIN_MONO_8_BUFFER_SIZE
                                             : audio.getBitDepth() == 16 ? MIN_STEREO_16_BUFFER_SIZE : MIN_STEREO_8_BUFFER_SIZE); 
              totalBytesWritten += bytesBuffered;
              
              if (bytesBuffered != 0) {
                track.write(mEmptyBytes, 0, mEmptyBytes.length - bytesBuffered);
                totalBytesWritten += (mEmptyBytes.length - bytesBuffered);
              }
              
              // how long elapsed since the sound was sent to the track
              long allWrittenAtNanos = System.nanoTime();
              
              // want to play at a target time = 2 secs after the last sound played
              // but have to take care that wake up well in advance of that because sleep isn't that accurate
              long nanosToGo = nextSoundPLayTargetNanos - allWrittenAtNanos;
              int millisToSleep = (int)(nanosToGo / NANOS_PER_MILLI) - millisSleepToAdvance;
              
              logDebug(String.format("\t buffering took = %s, extra bytes sent = %s, next sound in %s, sleep for %s"
                                        , (allWrittenAtNanos - lastSoundPlayedAtNanos) / NANOS_PER_MILLI
                                        , (bytesBuffered == 0 ? 0 : mEmptyBytes.length - bytesBuffered)
                                        , nanosToGo / NANOS_PER_MILLI
                                        , millisToSleep
                                        ));
              
              if (millisToSleep > 0) {
                sleep(millisToSleep);
              }
              else {
                logDebug("PROBLEM: it all took so long this iteration that we're already behind");
              }
            } 
            catch (InterruptedException e) {
              isPlaying = false;
              break;
            }
            catch (IllegalArgumentException e) {
              logException("startTests: File format not supported = "+audio, e);
            } 
            
            // stop tests when stopTests() called, sleep() means this is obsolete, but keep in for now in case remove sleep()
            if (isInterrupted()) {
              isPlaying = false;
              break;
            }
          }
        }        
        logDebug("play thread interrupted... quitting");
      }
    };
    mPlayThread.start();
  }
  
  /**
   * Streaming is not as easy as I imagined, as well as the drawbacks for using AudioTrack generally (see timedDelay tests above)
   * the fact that buffering is necessary makes it quite hard to do...
   * as a result this method is not fully worked out and will produce a steady delay each time a sound plays
   */  
  public void startStreamedTests(final HashMap<String, IWaveAudio> audios) {

    mPlayThread = new Thread() {
      @Override
      public void run() {
        final int millisBetweenSounds = 400;
        // 50ms is a rough number, could easily be smaller
        final int sleepTime = 50;
        final int bufferMs = 500;
        final int bitDepth = 16;
        final short[] streamingEmptyBuffer = new short[SAMPLE_RATE_44100 / 1000 // samples per milli 
                                                     * bufferMs                 // millis
                                                     * bitDepth / 8             // bytes per sample
                                                     / 2];                      // short not byte
        long playNextSoundAtNanos = -1L;
        long streamedUptoNanos = 0L; // will be initialised when first audio is read in  
        boolean playHasStarted = false; // set to true when buffering is complete and can play
        
        boolean isPlaying = true;
        
        while (isPlaying) {
          for (IWaveAudio audio : audios.values()) {
            //TODO include > 1 audiotrack : probably means a thread for each (unless there's time to fill all of them in one thread?)
            if (audio.getChannels() != 1 || audio.getSampleRate() != SAMPLE_RATE_44100) {
              continue;
            }
            
            try {
              AudioTrack track = getTrackForAudio(audio);
              logDebug(String.format("test audio = %s, track = %s", audio, track));
              // buffering track should not be playing
              if (!playHasStarted && track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                logDebug("\t PAUSE audio track, need to buffer");
                track.pause();
              }
              
              byte[] data = audio.getAudioData();
              int bytes = data.length;

              // TODO figure out about sample rate changes... separate audio tracks for each sample rate perhaps?
              // (unless sample rate change is local to the change... ie. doesn't affect anything already buffered at a different rate)
              int audioSampleRate = audio.getSampleRate();
              int trackRate = track.getPlaybackRate();
              long loopTimeNanos = System.nanoTime();
              if (audioSampleRate != trackRate) {
                try {
                  int res = track.setPlaybackRate(audioSampleRate);
                  long timeNow = System.nanoTime();
                  logDebug(String.format("\t changed track's playback rate from %s to %s, result = %s, took %s", 
                                          trackRate, track.getPlaybackRate(), res, (int)((timeNow - loopTimeNanos) / NANOS_PER_MILLI)));
                  loopTimeNanos = timeNow;
                  
                  if (res != AudioTrack.SUCCESS) {
                    throw new Exception("failed to set new playback rate");
                  }
                }
                catch (Exception e) {
                  logException("Not able to set playback rate", e);
                  // wrong for the sound, but right for the track... haven't seen this error though
                  audioSampleRate = track.getPlaybackRate();
                }
              }

              // test for buffering, don't advance play next sound until have started
/*
need to rethink this a bit
1) stream into buffer up to buffer limit (500ms)
  incl go round to get next sound(s) until full
2) start playing
3) watch playback head position (either with direct calls, or with a listener)
  have to match it to where the next sound it going to play
  if anytime it falls back so the sound will be late, advance it the amount needed for the sound to be on time
4) if a sound plays late and there's a buffer try to advance head position to correct the error
  if no buffer not much can do... log error, probably have to buffer up more 
*/
              if (playHasStarted) {
                if (streamedUptoNanos < loopTimeNanos) {
                  logDebug(String.format("\t PROBLEM: streaming fell behind, sound is late by %s Ms"
                                  , (int)((loopTimeNanos - streamedUptoNanos) / NANOS_PER_MILLI)));
                }

                // advance for the next sound
                playNextSoundAtNanos += millisBetweenSounds * NANOS_PER_MILLI;
              }
              else {
                // haven't played a sound yet, meaning the track is not yet playing and need to buffer to the next sound
                // init streamed up to time so it can start building the buffer
                streamedUptoNanos = loopTimeNanos;
              }
              
              // write the audio and update the written count
              track.write(data, 0, data.length);
              
              long nanosWritten = (long)(data.length          // number of bytes
                                  / audio.getBitDepth() / 8   // bytes per sample
                                  / audioSampleRate / 1000.f  // samples per milli
                                  * NANOS_PER_MILLI);
                                                
              streamedUptoNanos += nanosWritten;

              long timeNow = System.nanoTime();
              logDebug(String.format("\t audio data written %s bytes, took %s", data.length
                                        , (int)((timeNow - loopTimeNanos) / NANOS_PER_MILLI)));
              loopTimeNanos = timeNow;
              
              // fill empty sound until the audio is due
              while (!playHasStarted                                // haven't yet begun playing
                    || playNextSoundAtNanos > streamedUptoNanos) {  // or need to add silence

                // buffer up empty till have enough space to fill to the next sound                
                if (!playHasStarted) {
                  //TODO buffering, for now so it does work somehow, just get play started
                  playHasStarted = true;
                  track.play();
                  streamedUptoNanos = loopTimeNanos + nanosWritten;
                  playNextSoundAtNanos = loopTimeNanos + millisBetweenSounds * NANOS_PER_MILLI;
                }
                
                // loop until within the streaming timeframe (ie. don't buffer up too far ahead)
                while (streamedUptoNanos > loopTimeNanos + bufferMs * NANOS_PER_MILLI) {
                  // sleep could be interrupted, which will stop everything (see catch{} below)
                  logDebug(String.format("\t streaming is far ahead, sleep for %s", sleepTime));
                  sleep(sleepTime); 
                  
                  // keep advancing the loop time until coming into the time frame breaks out of the loop
                  loopTimeNanos = System.nanoTime();
                }
                
                // write empty samples, up to the soonest of the time to play the sound
                // or the end of the time frame, figure out how many nanos that is 
                long streamNanosTillAudio = (playNextSoundAtNanos - streamedUptoNanos);
                long streamNanosTillEndTimeFrame = (loopTimeNanos + bufferMs * NANOS_PER_MILLI) // nanos at the end of the time frame
                                              - streamedUptoNanos;
                long nanosToWrite = Math.min(streamNanosTillAudio, streamNanosTillEndTimeFrame);
                
                // convert to millis, and bytes
                int millisToWrite = (int)(nanosToWrite / NANOS_PER_MILLI);

                // write a bunch of empty samples, up to the soonest of the time to play the sound
                // or the end of the time frame 
                int bytesToWrite = (int)(millisToWrite
                                    * audioSampleRate / 1000.f   // samples per milli
                                    * audio.getBitDepth() / 8);   // bytes per sample
                                            
                while (bytesToWrite > 0) {
                  int bytesThisIteration = Math.min(bytesToWrite, streamingEmptyBuffer.length * 2 /* short array */);
//                  loopTimeNanos = System.nanoTime();
                  track.write(streamingEmptyBuffer, 0, bytesThisIteration / 2 /* half because short array */);
                  timeNow = System.nanoTime();
                  logDebug(String.format("\t adding advance silence = %s, bytes left to write = %s, written this time = %s, took %s", 
                                      millisToWrite, bytesToWrite, bytesThisIteration, (int)((timeNow - loopTimeNanos) / NANOS_PER_MILLI)
                                      ));
                  loopTimeNanos = timeNow;
                  bytesToWrite -= bytesThisIteration;
                }

                // update how far streaming has got
                streamedUptoNanos += nanosToWrite;
              }

            } 
            catch (InterruptedException e) {
              isPlaying = false;
              break;
            }
            catch (IllegalArgumentException e) {
              logException("startTests: File format not supported = "+audio, e);
            } 
            
            // stop tests when stopTests() called, sleep() means this is obsolete, but keep in for now in case remove sleep()
            if (isInterrupted()) {
              isPlaying = false;
              break;
            }
          }
        }        
        logDebug("play thread interrupted... quitting");
      }
    };
    mPlayThread.start();
  }
  
  public void playAudio(IWaveAudio audio) {
  }
  
  public void stopTests() {
    logDebug("stop tests");
    mPlayThread.interrupt();
    try {
      mPlayThread.join(); // wait for it (so don't go on to release resources before playing has stopped for sure)
    } catch (InterruptedException e) {}
    mPlayThread = null;
  }
  
  public void releaseResources() {
    for (AudioTrack track : mAudioTracks.values()) {
      track.stop();
      track.release();
    }
    
    mAudioTracks.clear();
  }
}

class AudioFileLoader {

  private HashMap<String, IWaveAudio> mAudios = new HashMap<String, IWaveAudio>();
  
  public HashMap<String, IWaveAudio> getAudios() {
    return mAudios;
  }
  
  public void releaseResources() {
    mAudios.clear();
  }

  public void loadWaveAudioFile(String filename) {
    try {
      // how long is the file in bytes?
      long byteCount = getAssets().openFd(filename).getLength();
      logDebug("loadWaveAudioFile: bytes in "+filename+" "+byteCount);

      // check is mono 16 bit wavs
      InputStream is = getAssets().open(filename); 
      BufferedInputStream bis = new BufferedInputStream(is);

      // chop!!

      byte[] byteBuff = new byte[4];

      // skip to 20 bytes to get file format
      bis.skip(20);
      bis.read(byteBuff, 0, 2); // read 2 so we are at 22 now
      boolean isPCM = ((short)byteBuff[0]) == 1; 
      logDebug("\t File isPCM "+isPCM);

      // at 22 bytes to get # channels
      bis.read(byteBuff, 0, 2);// read 2 so we are at 24 now
      int channels = (short)byteBuff[0];
      logDebug("\t #channels "+channels+" (byteBuff="+byteBuff[0]+")");

      // at 24 bytes to get sampleRate
      bis.read(byteBuff, 0, 4); // read 4 so now we are at 28
      int sampleRate = bytesToInt(byteBuff, 4);
      logDebug("\t Sample rate "+sampleRate);
      
      // skip to 34 bytes to get bits per sample
      bis.skip(6); // we were at 28...
      bis.read(byteBuff, 0, 2);// read 2 so we are at 36 now
      int bitDepth = (short)byteBuff[0];
      logDebug("\t bit depth "+bitDepth);
      
      if (bitDepth != 16 || (channels != 1 && channels != 2)) {
        throw new IllegalArgumentException("Unsupported channels/bitDepth combo");    
      }
      
      // number of bytes per sample either 1 (8 bits) or 2 (16 bits)
      int bytesPerSample = bitDepth / 8;
      logDebug("\t bytesPerSample "+bytesPerSample);
      // at 36 start processing the raw data
      int sampleCount = (int) ((byteCount - 36) / (bytesPerSample * channels));
      byte[] audioData = new byte[sampleCount * bytesPerSample];

      int read = 0, total = 0;
      while ((read = bis.read(audioData, total, audioData.length - total)) != -1
              && total < audioData.length) {
        total += read;
      }
      bis.close();
      
      //TODO don't assume little endian, use ByteBuffer to switch
      
      float realSampleCount = total / (float)bytesPerSample; 
      float secs = realSampleCount / sampleRate;
      logDebug("\t Read "+realSampleCount+" samples expected "+sampleCount+" time "+secs+" secs ");      
/*
test secs is correct, eg. old way : tick_middle.wav byteCount=7086, read 3525 samples 0.079931974 secs
*/
      ByteBuffer bb = convertWaveAudioBytes(audioData, bitDepth == 16);
      
//      mAudios.put(filename, new WaveAudio(filename, bb.array(), channels, bitDepth, sampleRate, (int)realSampleCount, secs));
      mAudios.put(filename, new WaveAudio(filename, bb.array(), channels, 16, sampleRate, (int)realSampleCount, secs));
    } 
    catch (FileNotFoundException e) {
      logException("loadWaveAudioFile: File not found = "+filename, e);
    }
    catch (IOException e) {
      logException("loadWaveAudioFile: IO exception", e);
    }
  }
  
  private ByteBuffer convertWaveAudioBytes(byte[] audio_bytes, boolean two_bytes_data) {
//    ByteBuffer dest = ByteBuffer.allocateDirect(audio_bytes.length);
    logDebug("dest byte order is little endian = "+(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN));
//    ByteBuffer dest = ByteBuffer.wrap(new byte[two_bytes_data ? audio_bytes.length : audio_bytes.length * 2]);
    ByteBuffer dest = ByteBuffer.wrap(new byte[two_bytes_data ? audio_bytes.length : audio_bytes.length * 2]);
    dest.order(ByteOrder.nativeOrder());
    ByteBuffer src = ByteBuffer.wrap(audio_bytes);
    src.order(ByteOrder.LITTLE_ENDIAN);
    if (two_bytes_data) {
      ShortBuffer dest_short = dest.asShortBuffer();
      ShortBuffer src_short = src.asShortBuffer();
      while (src_short.hasRemaining())
        dest_short.put(src_short.get());
    } 
    else {
      int c = 0;
      while (src.hasRemaining()) {
//        dest.put(src.get());
        byte b = src.get();
        short s = (short)(32768 * b / 128);
        if (c++ < 20) {
          logDebug("c="+c+": byte="+b+" short="+s);
        }
        dest.putShort(s);
      }
    }
    dest.rewind();
    return dest;
  }

/* short based version
  public void loadWaveAudioFile(String filename) {
    try {
      // how long is the file in bytes?
      long byteCount = getAssets().openFd(filename).getLength();
      logDebug("loadWaveAudioFile: bytes in "+filename+" "+byteCount);

      // check is mono 16 bit wavs
      InputStream is = getAssets().open(filename); 
      BufferedInputStream bis = new BufferedInputStream(is);

      // chop!!

      byte[] byteBuff = new byte[4];

      // skip to 20 bytes to get file format
      bis.skip(20);
      bis.read(byteBuff, 0, 2); // read 2 so we are at 22 now
      boolean isPCM = ((short)byteBuff[0]) == 1; 
      logDebug("\t File isPCM "+isPCM);

      // at 22 bytes to get # channels
      bis.read(byteBuff, 0, 2);// read 2 so we are at 24 now
      int channels = (short)byteBuff[0];
      logDebug("\t #channels "+channels+" (byteBuff="+byteBuff[0]+")");

      // at 24 bytes to get sampleRate
      bis.read(byteBuff, 0, 4); // read 4 so now we are at 28
      int sampleRate = bytesToInt(byteBuff, 4);
      logDebug("\t Sample rate "+sampleRate);
      
      // skip to 34 bytes to get bits per sample
      bis.skip(6); // we were at 28...
      bis.read(byteBuff, 0, 2);// read 2 so we are at 36 now
      int bitDepth = (short)byteBuff[0];
      logDebug("\t bit depth "+bitDepth);
      // number of bytes per sample either 1 (8 bits) or 2 (16 bits)
      int bytesPerSample = bitDepth /= 8;
      logDebug("\t bytesPerSample "+bytesPerSample);
      // at 36 start processing the raw data
      int sampleCount = (int) ((byteCount - 36) / (bytesPerSample * channels));
      short[] audioData = new short[sampleCount];
      int skip = (channels -1) * bytesPerSample;
      int sample = 0;
      // skip a few samples as it sounds like shit
//      bis.skip(bytesPerSample * 4);
      while (bis.available() >= (bytesPerSample + skip)) {
        bis.read(byteBuff, 0, bytesPerSample);
        // resample to 16 bit by casting to a short
        audioData[sample++] = (short) bytesToInt(byteBuff, bytesPerSample);
        bis.skip(skip);
      }

      float secs = (float)sample / (float)sampleRate;
      logDebug("\t Read "+sample+" samples expected "+sampleCount+" time "+secs+" secs ");      
      bis.close();

      mAudios.put(filename, new WaveAudio(filename, audioData, channels, sampleRate, sample, secs));
    } 
    catch (FileNotFoundException e) {
      logException("loadWaveAudioFile: File not found = "+filename, e);
    }
    catch (IOException e) {
      logException("loadWaveAudioFile: IO exception", e);
    }
  }
*/
  /** 
   *convert the sent byte array into an int. Assumes little endian byte ordering. 
   *@param bytes - the byte array containing the data
   *@param wordSizeBytes - the number of bytes to read from bytes array
   *@return int - the byte array as an int
   */
  private int bytesToInt(byte[] bytes, int wordSizeBytes) {
    int val = 0;
    for (int i=wordSizeBytes-1; i>=0; i--) {
      val <<= 8;
      val |= (int)bytes[i] & 0xFF;
    }
    return val;
  }
  
}

class FilePoolMap {
  String filename;
  int streamId;
  int status;
  
  public FilePoolMap(String filename, int streamId) {
    this.filename = filename;
    this.streamId = streamId;
  }
  
}

void logException(String msg, Exception e) {
  println(msg);
  e.printStackTrace();
  Log.e(LOG_TAG, msg, e);
}

void logDebug(String msg) {
  println(msg);
  Log.d(LOG_TAG, msg);
}

