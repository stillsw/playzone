package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.ProposedSoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.openal.SilentStartAudio;
import com.stillwindsoftware.keepyabeat.platform.twl.openal.SoundStore;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;
import com.stillwindsoftware.keepyabeat.utils.PixelUtils;
import com.stillwindsoftware.keepyabeat.utils.StringUtils;



public class TestedSoundFile implements ProposedSoundResource {
	// don't even try to load a file longer than this
	private static final long MAX_BYTE_LENGTH = 250000L; // 1/4 MB should be big enough
	private float duration;
	private SilentStartAudio audio = null;
	private File file;
	private boolean valid = true;
	private String invalidReason;
	private String name;

	/**
	 * Constructor adds extra validation step... that the tested duration matches what's passed in
	 * @param file
	 * @param duration
	 */
	public TestedSoundFile(File file, float duration) {
		this(file);
		if (valid && !PixelUtils.floatsMatch(duration, this.duration)) {
			valid = false;
			TwlResourceManager.getInstance().log(LOG_TYPE.error, this, 
					String.format("TestedSoundFile.<init(1)>: durations don't match from audio=%f from param=%f"
							, this.duration, duration));
			invalidReason = TwlResourceManager.getInstance().getLocalisedString(
					TwlLocalisationKeys.SOUND_FILE_MATCH_ERROR);
		}
	}

	public TestedSoundFile(File file) {
		this.file = file;
		if (file.length() > MAX_BYTE_LENGTH) {
			valid = false;
			invalidReason = TwlResourceManager.getInstance().getLocalisedString(
					TwlLocalisationKeys.SOUND_FILE_SIZE_ERROR);
		} 
		else {
			try {
				audio = TwlResourceManager.getInstance().getSoundStore().getAudio(file);
			} catch (FileNotFoundException e) {
				valid = false;
				invalidReason = TwlResourceManager.getInstance().getLocalisedString(
						TwlLocalisationKeys.SOUND_FILE_RECOGNIZED_ERROR);
			} catch (IOException e) {
				valid = false;
				invalidReason = TwlResourceManager.getInstance().getLocalisedString(
						TwlLocalisationKeys.SOUND_FILE_INVALID_ERROR);
			}
		}
		
		if (audio != null) {
			// duration is the audio's length less the silent padding that's been added
			duration = audio.getLength() - Sounds.LEFT_PAD_SILENT_MILLIS / (float)BeatTracker.MILLIS_PER_SECOND;
			
			if (duration > SoundStore.MAX_SOUND_DURATION) {
				valid = false;
				invalidReason = String.format(
						TwlResourceManager.getInstance().getLocalisedString(
						TwlLocalisationKeys.SOUND_FILE_LENGTH_ERROR), duration, SoundStore.MAX_SOUND_DURATION);
			}
		}
		else if (valid) {
			valid = false;
			invalidReason = String.format(
					TwlResourceManager.getInstance().getLocalisedString(
					TwlLocalisationKeys.SOUND_FILE_FORMAT_ERROR), StringUtils.getExtension(file.getName()));
		}
	}

	public float getDuration() {
		return duration;
	}
	
	public boolean isValid() {
		return valid;
	}

	public void setValid(boolean valid) {
		this.valid = valid;
	}

	public String getInvalidReason() {
		return invalidReason;
	}

	public void setInvalidReason(String invalidReason) {
		this.invalidReason = invalidReason;
	}

	public File getFile() {
		return file;
	}
	
	public void setFile(File file) {
		this.file = file;
	}

	public SilentStartAudio getAudio() {
		return audio;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

