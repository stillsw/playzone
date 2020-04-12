package com.stillwindsoftware.keepyabeat.platform.twl.openal;

import org.lwjgl.openal.AL10;

/**
 * A sound that can be played through OpenAL
 * Chopped out of the WaveAudio class by Kevin Glass & Nathan Sweet
 * Made specific to KyB by prefix padding silence.
 */
public class SilentStartAudio {

	protected SoundStore store;
	protected int buffer;
	// The index of the source being used to play this sound 
	private int index = -1;
	
	// playback attributes
	protected float length;
	protected int samplesOfSilence;
	protected int sampleRate;
	
	/**
	 * Create a new sound
	 * 
	 * @param store The sound store from which the sound was created
	 * @param buffer The buffer containing the sound data
	 */
	SilentStartAudio(SoundStore store, int buffer, int leftPadSilenceMillis) {
		this.store = store;
		this.buffer = buffer;
		
		int bytes = AL10.alGetBufferi(buffer, AL10.AL_SIZE);
		int bits = AL10.alGetBufferi(buffer, AL10.AL_BITS);
		int channels = AL10.alGetBufferi(buffer, AL10.AL_CHANNELS);
		sampleRate = AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY);
		
		int samples = bytes / (bits / 8);
		length = (samples / (float) sampleRate) / channels;
		
		samplesOfSilence = (int) (sampleRate
					* (leftPadSilenceMillis / 1000.f));
	}
	
	public float getLength() {
		return length;
	}

	/**
	 * Get the ID of the OpenAL buffer holding this data (if any). This method
	 * is not valid with streaming resources.
	 * 
	 * @return The ID of the OpenAL buffer holding this data 
	 */
	public int getBufferID() {
		return buffer;
	}
	
	/**
	 * Needs to play when the seconds elapses, so send the silent wave too which will 
	 * queue first for that number of seconds
	 * @param pitch
	 * @param gain
	 * @param seconds
	 * @return
	 */
	public int playSoon(float pitch, float gain, float secondsOfSilence) {
		index = store.playSilentStartAudio(this, pitch, gain, secondsOfSilence, samplesOfSilence, sampleRate);
	    return store.getSource(index);
	}
}