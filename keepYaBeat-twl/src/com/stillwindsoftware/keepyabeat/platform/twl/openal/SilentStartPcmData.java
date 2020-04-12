package com.stillwindsoftware.keepyabeat.platform.twl.openal;


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.lwjgl.openal.AL10;

import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

/**
 * Mostly chopped out of Brian Matzon's class for loading wave and aif audio data,
 * but made very specific in that it must be data that has been prefixed by
 * silence of a globally set length (by SoundStore). The silence is added on the fly
 * when the data is loaded from source.
 * Made into an abstract super-class since both wave and aif are essentially the
 * same except for minor differences in the conversion of the bytes.
 * @author Brian Matzon <brian@matzon.dk>
 * @author tomas stubbs
 */
public class SilentStartPcmData {

	// actual pcm data 
	private final ByteBuffer data;
	private final int format;
	private final int sampleRate;

	/**
	 * Creates a new SilentStartWaveData
	 * 
	 * @param data actual data
	 * @param format format of data
	 * @param samplerate sample rate of data
	 */
	protected SilentStartPcmData(ByteBuffer data, int format, int sampleRate) {
		this.data = data;
		this.format = format;
		this.sampleRate = sampleRate;
	}

	public ByteBuffer getData() {
		return data;
	}

	public int getFormat() {
		return format;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * Disposes the SilentStartWaveData
	 */
	public void dispose() {
		data.clear();
	}

	/**
	 * Creates a SilentPrefixedWaveData container from the specified inputstream
	 * Called by SoundStore
	 * @param is InputStream to read from 
	 * @param leftPadSilentMillis 
	 * @return SilentPrefixedWaveData containing data, or null if a failure occured
	 * @throws UnsupportedAudioFileException 
	 */
	static SilentStartPcmData create(String type, InputStream is, int leftPadSilenceMillis) 
			throws IOException, UnsupportedAudioFileException {

		// sound store is already doing this check, but just to make sure nothing gets through
		if (!(type.equals(SoundStore.WAV_FILES) || type.equals(SoundStore.AIF_FILES))) {
			TwlResourceManager.getInstance().log(LOG_TYPE.error, null, String.format(
					"SilentStartPcmData.create: only handles WAV or AIF files, requested=%s", type));
			throw new UnsupportedAudioFileException(String.format("Invalid type, only handles WAV or AIF files, requested=%s", type));
		}

		AudioInputStream ais = AudioSystem.getAudioInputStream(is);
		
		//get format of data
		AudioFormat audioformat = ais.getFormat();
		
		// get channels
		int channels = 0;
		if (audioformat.getChannels() == 1) {
			if (audioformat.getSampleSizeInBits() == 8) {
				channels = AL10.AL_FORMAT_MONO8;
			} else if (audioformat.getSampleSizeInBits() == 16) {
				channels = AL10.AL_FORMAT_MONO16;
			} else {
				throw new RuntimeException("Illegal sample size");
			}
		} else if (audioformat.getChannels() == 2) {
			if (audioformat.getSampleSizeInBits() == 8) {
				channels = AL10.AL_FORMAT_STEREO8;
			} else if (audioformat.getSampleSizeInBits() == 16) {
				channels = AL10.AL_FORMAT_STEREO16;
			} else {
				throw new RuntimeException("Illegal sample size");
			}
		} else {
			throw new UnsupportedAudioFileException("Only mono or stereo is supported");
		}
				
		int leftPadFrameLength = (int) (audioformat.getFrameRate()
				* (leftPadSilenceMillis / 1000.f));
		int leftPadBytes = (audioformat.getChannels()
				* leftPadFrameLength
				* audioformat.getSampleSizeInBits()
				/ 8);

		//read data into buffer
		byte[] buf =
			new byte[(audioformat.getChannels()
					* (int) ais.getFrameLength()
					* audioformat.getSampleSizeInBits()
					/ 8) + leftPadBytes];
		// skip the left padding (for 2 byte samples anyway)
		int read = 0, total = leftPadBytes;//0;
		try {
			while ((read = ais.read(buf, total, buf.length - total)) != -1
				&& total < buf.length) {
				total += read;
			}
		} catch (IOException ioe) {
			TwlResourceManager.getInstance().log(LOG_TYPE.error, null, "SilentStartPcmData.create(2): IO error reading bytes");
			ioe.printStackTrace();
			throw ioe;
		}
		
		TwlResourceManager.getInstance().log(LOG_TYPE.info, null, String.format(
				"SilentStartPcmData.create(2): channels=%s frameLen=%s sampleSize(bits)=%s bufferSize=%s sampleRate=%s leftPadSilenceMillis=%s leftPadFrameLength=%s leftPadBytes=%s"
					, audioformat.getChannels(), (int) ais.getFrameLength(), audioformat.getSampleSizeInBits()
					, buf.length, (int)audioformat.getSampleRate(), leftPadSilenceMillis, leftPadFrameLength, leftPadBytes));
		
		//insert data into bytebuffer
		ByteBuffer buffer = (type.equals(SoundStore.WAV_FILES) 
				? convertWaveAudioBytes(buf, audioformat.getSampleSizeInBits() == 16) 
						: convertAiffAudioBytes(audioformat, buf, audioformat.getSampleSizeInBits() == 16));
		
		//create our result
		SilentStartPcmData pcmData = new SilentStartPcmData(buffer, channels, (int) audioformat.getSampleRate());
		
		//close stream
		try {
			ais.close();
		} catch (IOException ioe) {}
	
		return pcmData;
	}

	/**
	 * Convert the audio bytes into the stream
	 * 
	 * @param audio_bytes The audio bytes
	 * @param two_bytes_data True if we using double byte data
	 * @return The byte buffer of data
	 */
	private static ByteBuffer convertWaveAudioBytes(byte[] audio_bytes, boolean two_bytes_data) {
		ByteBuffer dest = ByteBuffer.allocateDirect(audio_bytes.length);
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
			while (src.hasRemaining())
				dest.put(src.get());
		}
		dest.rewind();
		return dest;
	}
	
	/**
	 * Slightly different version for Aiff
	 * @param format
	 * @param audio_bytes
	 * @param two_bytes_data
	 * @return
	 */
	private static ByteBuffer convertAiffAudioBytes(AudioFormat format, byte[] audio_bytes, boolean two_bytes_data) {
		ByteBuffer dest = ByteBuffer.allocateDirect(audio_bytes.length);
		dest.order(ByteOrder.nativeOrder());
		ByteBuffer src = ByteBuffer.wrap(audio_bytes);
		src.order(ByteOrder.BIG_ENDIAN);
		if (two_bytes_data) {
			ShortBuffer dest_short = dest.asShortBuffer();
			ShortBuffer src_short = src.asShortBuffer();
			while (src_short.hasRemaining())
				dest_short.put(src_short.get());
		} 
		else {
			while (src.hasRemaining()) {
				byte b = src.get();
				if (format.getEncoding() == Encoding.PCM_SIGNED) {
					b = (byte) (b + 127);
				}
				dest.put(b);
			}
		}
		dest.rewind();
		return dest;
	}
}