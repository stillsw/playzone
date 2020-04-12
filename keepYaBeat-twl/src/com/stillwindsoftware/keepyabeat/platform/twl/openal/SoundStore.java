package com.stillwindsoftware.keepyabeat.platform.twl.openal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map.Entry;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.OpenALException;

import com.stillwindsoftware.keepyabeat.model.LibraryXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Sounds.InternalSound;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TestedSoundFile;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlSoundResource;
import com.stillwindsoftware.keepyabeat.utils.StringUtils;

/**
 * Based on SoundStore written by Kevin Glass and Rockstar
 * Basic structure and source pool is the same, but modified 
 * to handle silent pre-fixing of sounds for use in Keep Ya Beat,
 * complex sound management (turning on streaming and music etc) removed. 
 * Merged also with SoundFileManager which doesn't do that much, so added at the end
 * @author tomas stubbs
 */
public class SoundStore {
	public static final String WAV_FILES = "wav";
	private static final String WAVE_FILES = "wave";
	public static final String AIF_FILES = "aif";
	private static final String AIFF_FILES = "aiff";
//	private static final String OGG_FORMAT = "OGG";
//	private static final String OGG_FILES = "ogg";
	public static final float MAX_SOUND_DURATION = .6f;

	private FileListFilter legalExtensions = new FileListFilter(WAV_FILES, WAVE_FILES, AIF_FILES, AIFF_FILES);
	
	private boolean soundWorks;
	private int sourceCount;
	// The map of references to IDs of previously loaded sounds 
	private HashMap<String, Integer> loadedBuffers = new HashMap<String, Integer>();

	// The OpenGL AL sound sources in use
	private IntBuffer sources;
	// True if the sound system has been initialised 
	private boolean inited = false;
	// The global sound volume setting 
	private float soundVolume = 1.0f;

	// velocity and position buffers (set in init() and reused)
    private FloatBuffer sourceVel;
    private FloatBuffer sourcePos;
    
    // The maximum number of sources, probably don't need many as sounds play so quick
    private int maxSources = 16;

    private TwlResourceManager resourceManager;

	/**
	 * Create a new sound store
	 */
	public SoundStore() {
		resourceManager = TwlResourceManager.getInstance();
	}
	
	/**
	 * Set the sound volume
	 * 
	 * @param volume The volume for sound fx
	 */
	public void setSoundVolume(float volume) {
		if (volume < 0.f) {
			volume = 0.f;
		}
		soundVolume = volume;
	}
	
	/**
	 * Check if sound works at all
	 * 
	 * @return True if sound works at all
	 */
	public boolean soundWorks() {
		return soundWorks;
	}
	
	/**
	 * Get the volume for sounds
	 * 
	 * @return The volume for sounds
	 */
	public float getSoundVolume() {
		return soundVolume;
	}
	
	/**
	 * Get the ID of a given source
	 * 
	 * @param index The ID of a given source
	 * @return The ID of the given source
	 */
	public int getSource(int index) {
		if (!soundWorks) {
			return -1;
		}
		if (index < 0) {
			return -1;
		}
		return sources.get(index);
	}
	
	/**
	 * Set the maximum number of concurrent sound effects that will be 
	 * attempted
	 * 
	 * @param max The maximum number of sound effects/music to mix
	 */
	public void setMaxSources(int max) {
		this.maxSources = max;
	}
	
	/**
	 * Initialise the sound effects stored. This must be called
	 * before anything else will work
	 */
	public void init() {
		if (inited) {
			return;
		}
		resourceManager.log(LOG_TYPE.info, this, "Initialising sounds..");
		inited = true;
		
		AccessController.doPrivileged(new PrivilegedAction<Object>() {
		    public Object run() {
					try {
						AL.create();
						soundWorks = true;
						resourceManager.log(LOG_TYPE.info, this, "SoundStore.init: Sound works");
					} catch (Exception e) {
						resourceManager.log(LOG_TYPE.error, this, "SoundStore.init: Sound initialisation failure.");
						e.printStackTrace();
						soundWorks = false;
					}
					
					return null;
		    }});
		
		if (soundWorks) {
			sourceCount = 0;
			sources = BufferUtils.createIntBuffer(maxSources);
			while (AL10.alGetError() == AL10.AL_NO_ERROR) {
				IntBuffer temp = BufferUtils.createIntBuffer(1);
				
				try {
					AL10.alGenSources(temp);
				
					if (AL10.alGetError() == AL10.AL_NO_ERROR) {
						sourceCount++;
						sources.put(temp.get(0));
						if (sourceCount > maxSources-1) {
							break;
						}
					} 
				} catch (OpenALException e) {
					// expected at the end
					break;
				}
			}
			resourceManager.log(LOG_TYPE.info, this, String.format("SoundStore.init: %s OpenAL sources available", sourceCount));
		
			if (AL10.alGetError() != AL10.AL_NO_ERROR) {
				soundWorks = false;
				resourceManager.log(LOG_TYPE.error, this, "SoundStore.init: AL init failed");
			} 
			else {
				FloatBuffer listenerOri = BufferUtils.createFloatBuffer(6).put(
						new float[] { 0.0f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f });
				FloatBuffer listenerVel = BufferUtils.createFloatBuffer(3).put(
						new float[] { 0.0f, 0.0f, 0.0f });
				FloatBuffer listenerPos = BufferUtils.createFloatBuffer(3).put(
						new float[] { 0.0f, 0.0f, 0.0f });
				listenerPos.flip();
				listenerVel.flip();
				listenerOri.flip();
				AL10.alListener(AL10.AL_POSITION, listenerPos);
				AL10.alListener(AL10.AL_VELOCITY, listenerVel);
				AL10.alListener(AL10.AL_ORIENTATION, listenerOri);
			 
			    sourceVel = BufferUtils.createFloatBuffer(3).put(new float[] { 0.0f, 0.0f, 0.0f });
			    sourcePos = BufferUtils.createFloatBuffer(3).put(new float[] { 0.0f, 0.0f, 0.0f });
				sourcePos.flip();
				sourceVel.flip();

				resourceManager.log(LOG_TYPE.info, this, "SoundStore.init: Sounds source generated");
			}
		}
	}

	/**
	 * Stop a particular sound source
	 * 
	 * @param index The index of the source to stop
	 */
	void stopSource(int index) {
		AL10.alSourceStop(sources.get(index));
	}
	
//	/**
//	 * Play the specified buffer as a sound effect with the specified
//	 * pitch and gain.
//	 * 
//	 * @param buffer The ID of the buffer to play
//	 * @param pitch The pitch to play at
//	 * @param gain The gain to play at
//	 * @param loop True if the sound should loop
//	 * @return source The source that will be used
//	 */
//	int playAsSound(int buffer,float pitch,float gain,boolean loop) {
//		return playAsSoundAt(buffer, pitch, gain, loop, 0, 0, 0);
//	}
//	
//	/**
//	 * Play the specified buffer as a sound effect with the specified
//	 * pitch and gain.
//	 * 
//	 * @param buffer The ID of the buffer to play
//	 * @param pitch The pitch to play at
//	 * @param gain The gain to play at
//	 * @param loop True if the sound should loop
//	 * @param x The x position to play the sound from
//	 * @param y The y position to play the sound from
//	 * @param z The z position to play the sound from
//	 * @return source The source that will be used
//	 */
//	int playAsSoundAt(int buffer,float pitch,float gain,boolean loop,float x, float y, float z) {
//		gain *= soundVolume;
//		if (gain == 0) {
//			gain = 0.001f;
//		}
//		if (soundWorks) {
//			int nextSource = findFreeSource();
//			if (nextSource == -1) {
//				return -1;
//			}
//			
//			AL10.alSourceStop(sources.get(nextSource));
//			
//			AL10.alSourcei(sources.get(nextSource), AL10.AL_BUFFER, buffer);
//			AL10.alSourcef(sources.get(nextSource), AL10.AL_PITCH, pitch);
//			AL10.alSourcef(sources.get(nextSource), AL10.AL_GAIN, gain); 
//		    AL10.alSourcei(sources.get(nextSource), AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
//		    
//		    sourcePos.clear();
//		    sourceVel.clear();
//			sourceVel.put(new float[] { 0, 0, 0 });
//			sourcePos.put(new float[] { x, y, z });
//		    sourcePos.flip();
//		    sourceVel.flip();
//		    AL10.alSource(sources.get(nextSource), AL10.AL_POSITION, sourcePos);
//			AL10.alSource(sources.get(nextSource), AL10.AL_VELOCITY, sourceVel);
//			    
//			AL10.alSourcePlay(sources.get(nextSource)); 
//			
//			return nextSource;
//		}
//		
//		return -1;
//	}
	
	/**
	 * Check if a particular source is playing
	 * 
	 * @param index The index of the source to check
	 * @return True if the source is playing
	 */
	boolean isPlaying(int index) {
		int state = AL10.alGetSourcei(sources.get(index), AL10.AL_SOURCE_STATE);
		return (state == AL10.AL_PLAYING);
	}
	
	/**
	 * Find a free sound source
	 * 
	 * @return The index of the free sound source
	 */
	private int findFreeSource() {
		for (int i = 0; i < sourceCount; i++) {
			int state = AL10.alGetSourcei(sources.get(i), AL10.AL_SOURCE_STATE);
			
			if ((state != AL10.AL_PLAYING) && (state != AL10.AL_PAUSED)) {
				return i;
			}
		}
		
		return -1;
	}


//	/**
//	 * Get the Sound based on a specified AIF file
//	 * 
//	 * @param ref The reference to the AIF file in the classpath
//	 * @return The Sound read from the AIF file
//	 * @throws IOException Indicates a failure to load the AIF
//	 */
//	public Audio getAIF(String ref) throws IOException {
//		return getAIF(ref, ResourceLoader.getResourceAsStream(ref));
//	}
//	
//
//	/**
//	 * Get the Sound based on a specified AIF file
//	 * 
//	 * @param in The stream to the MOD to load
//	 * @return The Sound read from the AIF file
//	 * @throws IOException Indicates a failure to load the AIF
//	 */
//	public Audio getAIF(InputStream in) throws IOException {
//		return getAIF(in.toString(), in);
//	}
//	
//	/**
//	 * Get the Sound based on a specified AIF file
//	 * 
//	 * @param ref The reference to the AIF file in the classpath
//	 * @param in The stream to the AIF to load
//	 * @return The Sound read from the AIF file
//	 * @throws IOException Indicates a failure to load the AIF
//	 */
//	public Audio getAIF(String ref, InputStream in) throws IOException {
//		in = new BufferedInputStream(in);
//
//		if (!soundWorks) {
//			return new NullAudio();
//		}
//		if (!inited) {
//			throw new RuntimeException("Can't load sounds until SoundStore is init(). Use the container init() method.");
//		}
//		if (deferred) {
//			return new DeferredSound(ref, in, DeferredSound.AIF);
//		}
//		
//		int buffer = -1;
//		
//		if (loaded.get(ref) != null) {
//			buffer = ((Integer) loaded.get(ref)).intValue();
//		} else {
//			try {
//				IntBuffer buf = BufferUtils.createIntBuffer(1);
//				
//				AiffData data = AiffData.create(in);
//				AL10.alGenBuffers(buf);
//				AL10.alBufferData(buf.get(0), data.format, data.data, data.samplerate);
//				
//				loaded.put(ref,new Integer(buf.get(0)));
//				buffer = buf.get(0);
//			} catch (Exception e) {
//				Log.error(e);
//				IOException x = new IOException("Failed to load: "+ref);
//				x.initCause(e);
//				
//				throw x;
//			}
//		}
//		
//		if (buffer == -1) {
//			throw new IOException("Unable to load: "+ref);
//		}
//		
//		return new AudioImpl(this, buffer);
//	}
	
	/**
	 * Get the Sound based on a specified WAV or AIF file prefixed with the global length of silence
	 * 
	 * @param ref The reference to the WAV or AIF file in the classpath
	 * @param in The stream to the WAV or AIF to load
	 * @return The Sound read from the WAV or AIF file
	 * @throws IOException Indicates a failure to load
	 */
	public SilentStartAudio getSilentStartPcmAudio(String type, String ref, InputStream in) throws IOException {
		resourceManager.log(LOG_TYPE.info, this, String.format("SoundStore.getSilentStartPcmAudio: for ref=%s type=%s", ref, type));
		
		if (!(type.equals(WAV_FILES) || type.equals(AIF_FILES))) {
			resourceManager.log(LOG_TYPE.error, this, String.format("SoundStore.getSilentStartPcmAudio: only handles WAV or AIF files, requested=%s", type));
			throw new IOException(String.format("Invalid type, only handles WAV or AIF files, requested=%s", type));
		}
		
		int buffer = -1;
		
		if (loadedBuffers.get(ref) != null) {
			buffer = ((Integer) loadedBuffers.get(ref)).intValue();
		} 
		else {
			try {
				IntBuffer buf = BufferUtils.createIntBuffer(1);
				
				SilentStartPcmData data = SilentStartPcmData.create(type, in, SoundsXmlImpl.LEFT_PAD_SILENT_MILLIS);
				AL10.alGenBuffers(buf);
				AL10.alBufferData(buf.get(0), data.getFormat(), data.getData(), data.getSampleRate());

				loadedBuffers.put(ref, new Integer(buf.get(0)));
				buffer = buf.get(0);
			} 
			catch (Exception e) {
				resourceManager.log(LOG_TYPE.error, this, String.format("SoundStore.getSilentStartPcmAudio: %s", e.getMessage()));
				IOException x = new IOException("Failed to load: "+ref);
				x.initCause(e);
				
				throw x;
			}
		}
		
		if (buffer == -1) {
			throw new IOException("Unable to load: "+ref);
		}
		
		return new SilentStartAudio(this, buffer, SoundsXmlImpl.LEFT_PAD_SILENT_MILLIS);
	}

	/**
	 * play audio with some silent padding before it
	 * @param samplesOfSilence 
	 * @param sampleRate 
	 */
	int playSilentStartAudio(SilentStartAudio wav, float pitch, float gain, float secondsToPadLeft, int samplesOfSilence, int sampleRate) {
		
		gain *= soundVolume;
		if (gain == 0) {
			gain = 0.001f;
		}
		
		if (soundWorks) {
			int nextSource = findFreeSource();
			if (nextSource == -1) {
				resourceManager.log(LOG_TYPE.error, this, "SoundStore.playSilentStartAudio: can't find a free source");
				return -1;
			}
			
			int bufferId = wav.getBufferID();

			// should already be stopped or initial
			AL10.alSourceStop(sources.get(nextSource));
			printErrorCode("playSilentStartAudio got error @1 stop");

			// set params
			AL10.alSourcei(sources.get(nextSource), AL10.AL_BUFFER, bufferId);
			printErrorCode("playSilentStartAudio got error @2 set buffer");
			AL10.alSourcef(sources.get(nextSource), AL10.AL_PITCH, pitch);
			printErrorCode("playSilentStartAudio got error @3 set pitch");
			AL10.alSourcef(sources.get(nextSource), AL10.AL_GAIN, gain); 
			printErrorCode("playSilentStartAudio got error @4 set gain");
			AL10.alSourcei(sources.get(nextSource), AL10.AL_LOOPING, AL10.AL_FALSE);
			printErrorCode("playSilentStartAudio got error @5 set looping");
			
			AL10.alSource(sources.get(nextSource), AL10.AL_POSITION, sourcePos);
			printErrorCode("playSilentStartAudio got error @6 set position");
			AL10.alSource(sources.get(nextSource), AL10.AL_VELOCITY, sourceVel);
			printErrorCode("playSilentStartAudio got error @7 set velocity");

			// advance by the difference of how many samples of silence there are less the requested padding
			if (secondsToPadLeft != 0f && samplesOfSilence != 0) {
				int samplesToAdvance = (int) (sampleRate * secondsToPadLeft);
				// log it at the lowest level
				resourceManager.log(LOG_TYPE.verbose, this, String.format(
						"SoundStore.playSilentStartAudio: volume=%f samplesToAdvance=%s sampleRate=%s samplesOfSilence=%s" 
						, gain, samplesToAdvance, sampleRate, samplesOfSilence));

				if (samplesToAdvance > samplesOfSilence) {
					resourceManager.log(LOG_TYPE.error, this, String.format(
							"SoundStore.playSilentStartAudio: can't pad that much (samplesToAdvance=%s), it's beyond the silence samplesOfSilence=%s"
								, samplesToAdvance, samplesOfSilence));
				}
				else {
					// advance the source by that amount
					AL10.alSourcei(sources.get(nextSource), AL11.AL_SAMPLE_OFFSET, samplesToAdvance);
					printErrorCode("playSilentStartAudio got error @8 with AL_SAMPLE_OFFSET");
				}
			}
			else {
				// log it at the lowest level, even though it's really a warning don't want to see this output
				// filling the logs when playing
				resourceManager.log(LOG_TYPE.verbose, this, String.format(
						"SoundStore.playSilentStartAudio: volume=%f playing sound straight, no silence adjustment" 
						, gain));
			}

	        AL10.alSourcePlay(sources.get(nextSource));
	        printErrorCode("playSilentStartAudio: got error @9 play");

	        return nextSource;
		}
		
		return -1;
	}

//	/**
//	 * Get the Sound based on a specified OGG file
//	 * 
//	 * @param ref The reference to the OGG file in the classpath
//	 * @return The Sound read from the OGG file
//	 * @throws IOException Indicates a failure to load the OGG
//	 */
//	public Audio getOggStream(String ref) throws IOException {
//		if (!soundWorks) {
//			return new NullAudio();
//		}
//		
//		setMOD(null);
//		setStream(null);
//		
//		if (currentMusic != -1) {
//			AL10.alSourceStop(sources.get(0));
//		}
//		
//		getMusicSource();
//		currentMusic = sources.get(0);
//		
//		return new StreamSound(new OpenALStreamPlayer(currentMusic, ref));
//	}
//
//	/**
//	 * Get the Sound based on a specified OGG file
//	 * 
//	 * @param ref The reference to the OGG file in the classpath
//	 * @return The Sound read from the OGG file
//	 * @throws IOException Indicates a failure to load the OGG
//	 */
//	public Audio getOggStream(URL ref) throws IOException {
//		if (!soundWorks) {
//			return new NullAudio();
//		}
//		
//		setMOD(null);
//		setStream(null);
//		
//		if (currentMusic != -1) {
//			AL10.alSourceStop(sources.get(0));
//		}
//		
//		getMusicSource();
//		currentMusic = sources.get(0);
//		
//		return new StreamSound(new OpenALStreamPlayer(currentMusic, ref));
//	}
//	
//	/**
//	 * Get the Sound based on a specified OGG file
//	 * 
//	 * @param ref The reference to the OGG file in the classpath
//	 * @return The Sound read from the OGG file
//	 * @throws IOException Indicates a failure to load the OGG
//	 */
//	public Audio getOgg(String ref) throws IOException {
//		return getOgg(ref, ResourceLoader.getResourceAsStream(ref));
//	}
//	
//	/**
//	 * Get the Sound based on a specified OGG file
//	 * 
//	 * @param in The stream to the OGG to load
//	 * @return The Sound read from the OGG file
//	 * @throws IOException Indicates a failure to load the OGG
//	 */
//	public Audio getOgg(InputStream in) throws IOException {
//		return getOgg(in.toString(), in);
//	}
//	
//	/**
//	 * Get the Sound based on a specified OGG file
//	 * 
//	 * @param ref The reference to the OGG file in the classpath
//	 * @param in The stream to the OGG to load
//	 * @return The Sound read from the OGG file
//	 * @throws IOException Indicates a failure to load the OGG
//	 */
//	public Audio getOgg(String ref, InputStream in) throws IOException {
//		if (!soundWorks) {
//			return new NullAudio();
//		}
//		if (!inited) {
//			throw new RuntimeException("Can't load sounds until SoundStore is init(). Use the container init() method.");
//		}
//		if (deferred) {
//			return new DeferredSound(ref, in, DeferredSound.OGG);
//		}
//		
//		int buffer = -1;
//		
//		if (loaded.get(ref) != null) {
//			buffer = ((Integer) loaded.get(ref)).intValue();
//		} else {
//			try {
//				IntBuffer buf = BufferUtils.createIntBuffer(1);
//				
//				OggDecoder decoder = new OggDecoder();
//				OggData ogg = decoder.getData(in);
//				
//				AL10.alGenBuffers(buf);
//				AL10.alBufferData(buf.get(0), ogg.channels > 1 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16, ogg.data, ogg.rate);
//				
//				loaded.put(ref,new Integer(buf.get(0)));
//						     
//				buffer = buf.get(0);
//			} catch (Exception e) {
//				Log.error(e);
//				Sys.alert("Error","Failed to load: "+ref+" - "+e.getMessage());
//				throw new IOException("Unable to load: "+ref);
//			}
//		}
//		
//		if (buffer == -1) {
//			throw new IOException("Unable to load: "+ref);
//		}
//		
//		return new AudioImpl(this, buffer);
//	}
	
	/**
	 * Retrieve the number of OpenAL sound sources that have been
	 * determined at initialisation.
	 * 
	 * @return The number of sources available
	 */
	public int getSourceCount() {
		return sourceCount;
	}
	
	/**
	 * Checks the OpenAL error and logs it if there was one
	 * @param whereAt
	 * @return
	 */
	boolean printErrorCode(String whereAt) {
		int error = AL10.alGetError();
		
		if (error == 0) {
			return true;
		}
		else {
			String errorText = null;
			if (error == AL10.AL_INVALID_NAME) {
				errorText = "AL_INVALID_NAME - Invalid Name parameter";
			}
			else if (error == AL10.AL_INVALID_ENUM) {
				errorText = "AL_INVALID_ENUM - Invalid parameter";
			}
			else if (error == AL10.AL_INVALID_VALUE) {
				errorText = "AL_INVALID_VALUE - Invalid enum parameter value";
			}
			else if (error == AL10.AL_INVALID_OPERATION) {
				errorText = "AL_INVALID_OPERATION - Illegal call";
			}
			else if (error == AL10.AL_OUT_OF_MEMORY) {
				errorText = "AL_OUT_OF_MEMORY - Unable to allocate memory";
			}
			
			resourceManager.log(LOG_TYPE.error, this, String.format(
					"SoundStore openAL Error %s %s", whereAt, errorText == null ? Integer.toString(error) : errorText));
			return false;
		}
	}

	public void killALData() {
		resourceManager.log(LOG_TYPE.info, this, "SoundStore.killALData: ");
		for (int i = 0; i < sourceCount; i++) {
			// make double sure it is stopped
			AL10.alSourceStop(sources.get(i));
			printErrorCode(String.format("killALData stopSource %s", i));
			AL10.alDeleteSources(sources.get(i));
			printErrorCode(String.format("killALData deleteSources %s", i));
		}
//		AL10.alDeleteSources(sources);
//		this.printErrorCode("killALData deleteSources");
		for (Entry<String, Integer> bufferRefId : loadedBuffers.entrySet()) {
			// sometimes get an error deleting buffers, if it happens wait and retry
			int errorCount = 0;
			do {
				AL10.alDeleteBuffers(bufferRefId.getValue());
				if (printErrorCode(String.format("killALData deleteBuffers (%s) attempt=%s"
						, bufferRefId.getKey(), errorCount))) {
					break;
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (errorCount++ < 2); // try 3 times at most
		}
	}
		
		/**
		 * 
		02.* void killALData()
		03.*
		04.*  We have allocated memory for our buffers and sources which needs
		05.*  to be returned to the system. This function frees that memory.
		06.*
		07.void killALData() {
		08.AL10.alDeleteSources(source);
		09.AL10.alDeleteBuffers(buffer);
		
		
		multiple sources, here's killing them from
			http://www.lwjgl.org/wiki/index.php?title=OpenAL_Tutorial_5_-_Sources_Sharing_Buffers
		void killALData() {
		08.// set to 0, num_sources
		09.int position = source.position();
		10.source.position(0).limit(position);
		11.AL10.alDeleteSources(source);
		12.AL10.alDeleteBuffers(buffer);
		13.}
		10.}	
		
		and even more advanced perhaps from
			http://www.lwjgl.org/wiki/index.php?title=OpenAL_Tutorial_6_-_Advanced_Loading_and_Error_Handles
		public static void killALData() {
			06.IntBuffer scratch = BufferUtils.createIntBuffer(1);
			07. 
			08.// Release all buffer data.
			09.for (Iterator iter = buffers.iterator(); iter.hasNext();) {
			10.scratch.put(0, ((Integer) iter.next()).intValue());
			11.AL10.alDeleteBuffers(scratch);
			12.}
			13. 
			14.// Release all source data.
			15.for (Iterator iter = sources.iterator(); iter.hasNext();) {
			16.scratch.put(0, ((Integer) iter.next()).intValue());
			17.AL10.alDeleteSources(scratch);
			18.}
			19. 
			20.// Destroy the lists.
			21.buffers.clear();
			22.sources.clear();
			23.}

		*/

	//----------------------------------- methods from previous SoundFileManager
	
	public Sound convertSoundFile(TestedSoundFile testedFile, LibraryXmlImpl library) {
		boolean isInternalSound = false;
		InternalSound internalSound = null;
		try {
			internalSound = SoundsXmlImpl.InternalSound.valueOf(testedFile.getName());
			if (internalSound != null) {
				isInternalSound = true;
			}
		} catch (IllegalArgumentException e) {
		}
		String key = (isInternalSound ? testedFile.getName() : library.getNextKey());
		
		TwlSoundResource soundResource = new TwlSoundResource();
		soundResource.setSoundFile(testedFile.getFile());
		soundResource.setAudioEffect(testedFile.getAudio());
		if (isInternalSound) {
			soundResource.setType(PlatformResourceManager.SoundResourceType.INT);
		}
		else {
			soundResource.setType(PlatformResourceManager.SoundResourceType.URI);
		}
		
		// name will start with a localised indicator if it's a system sound
		String name = (isInternalSound 
				? resourceManager.getLocalisedString(internalSound.getLocalisedKey()) 
						: testedFile.getName());
		SoundXmlImpl sound = new SoundXmlImpl(library, key, name);
		sound.setDuration(Function.getBlankContext(resourceManager), soundResource.getDuration());
		sound.setSoundResource(Function.getBlankContext(resourceManager), soundResource);

		return sound;
	}

	public TestedSoundFile getTestedSoundFile(File file, float duration) {
		return new TestedSoundFile(file, duration);
	}

    public SilentStartAudio getAudio(File file) throws FileNotFoundException, IOException {
    	
    	SilentStartAudio audio = null;
    	
    	if (file != null) {
			String extension = StringUtils.getExtension(file.getName());
			if (extension.equalsIgnoreCase(WAV_FILES) || extension.equalsIgnoreCase(WAVE_FILES)) {
				audio = getSilentStartPcmAudio(WAV_FILES, file.getAbsolutePath(), new BufferedInputStream(new FileInputStream(file)));
			}
			else if (extension.equalsIgnoreCase(AIF_FILES) || extension.equalsIgnoreCase(AIFF_FILES)) {
				audio = getSilentStartPcmAudio(AIF_FILES, file.getAbsolutePath(), new BufferedInputStream(new FileInputStream(file)));
			}
//			else if (extension.equalsIgnoreCase(OGG_FILES)) {
//				audio = AudioLoader.getAudio(OGG_FORMAT, new BufferedInputStream(new FileInputStream(file)));
//			}
    	}
    	
    	return audio;
    }
    
    public boolean isLegalFileExtension(String fileName) {
    	return legalExtensions.accept(fileName);
    }
    
    /**
     * Filter to find files by extension, and optionally within a time frame (twlResourceManager
     * uses that to find .png files older than a week for deletion)
     * @author tomas stubbs
     *
     */
	public static class FileListFilter implements FileFilter {
	    private final String[] extensions;
	    private boolean useTime = false;
	    private long timeThreshHoldMillis = -1L; 
	    private boolean returnOlder = false; // default to newer, so not set returns everything
	    
	    public FileListFilter(String ... extensions) {
	        this.extensions = extensions;
	    }

	    public FileListFilter(long timeThreshHoldMillis, boolean returnOlder, String ... extensions) {
	        this.extensions = extensions;
	        this.timeThreshHoldMillis = timeThreshHoldMillis;
	        this.returnOlder = returnOlder;
	        this.useTime = true;
	    }
	    
		@Override
		public boolean accept(File file) {
	        String name = file.getName().toLowerCase();
	        return accept(name, file.lastModified());
		}

		public boolean accept(String fileName) {
			return accept(fileName, 0L);
		}

		public boolean accept(String fileName, long timestamp) {
	        for(String extension : extensions) {
	            if(fileName.endsWith(extension)) {
	            	if (!useTime) {
	            		return true;
	            	}
	            	else if ((returnOlder && timestamp < timeThreshHoldMillis) 
		    	            || (!returnOlder && timestamp >= timeThreshHoldMillis)) {
	            		// check timestamp too
		            	return true;
	            	}
	            }
	        }
	        return false;
		}
	}

}