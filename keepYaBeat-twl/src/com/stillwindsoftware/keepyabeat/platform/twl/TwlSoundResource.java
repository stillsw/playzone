package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.File;
import java.net.URI;

import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceLoadError;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceType;
import com.stillwindsoftware.keepyabeat.platform.twl.openal.SilentStartAudio;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;


public class TwlSoundResource implements SoundResource {

	private SoundResourceType resourceType;
	private SoundResourceLoadError loadError;
    private SoundStatus status = SoundStatus.LOAD_PENDING;
	private String localisedErrorMessage;
	private File soundFile;
	private SilentStartAudio audioEffect;
	private float duration = Library.FLOAT_NOT_CHANGED;
	
	// if soundFile is loaded, this is ignored. Use for when there's a load error to preserve
	// the original path string from the xml
	private String uriPath = "";
	
	public File getSoundFile() {
		return soundFile;
	}
	
	public void setSoundFile(File soundFile) {
		this.soundFile = soundFile;
	}

	public void setAudioEffect(SilentStartAudio silentStartAudio) {
    	if (silentStartAudio != null) {
    		audioEffect = silentStartAudio;
			duration = silentStartAudio.getLength() - Sounds.LEFT_PAD_SILENT_MILLIS / (float)BeatTracker.MILLIS_PER_SECOND;
    	}
	}

	public File getFile() {
		return soundFile;
	}

	@Override
	public String getFileName() {
		if (soundFile != null)
			return soundFile.getName();
		else
			return uriPath;
	}

	@Override
	public String getUriPath() {
		if (soundFile != null) {
			return soundFile.toURI().toString();
		}
		else {
			return uriPath;
		}
	}

	@Override
	public void setUriPath(String uriPath) {
		this.uriPath = uriPath;
	}

	public boolean isPlayable() {
		return audioEffect != null;
	}
	
	public SilentStartAudio getAudioEffect() {
		return audioEffect;
	}

	public boolean isSameResource(SoundResource soundResource) {
		return isSameResource(((TwlSoundResource)soundResource).getSoundFile());
	}
	
	public boolean isSameResource(File file) {
		if (soundFile != null && file != null && file.getAbsolutePath().equals(soundFile.getAbsolutePath())) 
			return true;
		else
			return false;
	}

	@Override
	public float getDuration() {
		return duration;
	}

	@Override
	public void playSound(float volume, float secondsOfSilence) {
		if (audioEffect != null) {
			try {
				audioEffect.playSoon(1.0f, volume, secondsOfSilence);
			} catch (UnsatisfiedLinkError e) {
				// this sometimes happens when closing the app while it's playing
				// (not other times afaik)
				TwlResourceManager.getInstance().log(LOG_TYPE.warning, this, "TwlSoundResource.playSound: unsatisfied link error");
			}
		}
	}

	@Override
	public SoundResourceType getType() {
		return resourceType;
	}

	public void setType(SoundResourceType resourceType) {
		this.resourceType = resourceType;
	}
	@Override
	public SoundResourceLoadError getLoadError() {
		return loadError;
	}

	@Override
	public void setLoadError(SoundResourceLoadError errorType) {
		loadError = errorType;		
	}

	public void setLoadErrorLocalisedKey(SoundResourceLoadError errorType, CoreLocalisation.Key localisedKey, Object ... params) {
		this.localisedErrorMessage = String.format(TwlResourceManager.getInstance().getLocalisedString(localisedKey), params);
	}

	@Override
	public String getLocalisedErrorMessage() {
		return localisedErrorMessage;
	}


	@Override
	public void release() {
		// no need all buffers/sources will be removed on close of app
	}

	/**
	 * Sound file failed to load, now attempt a repair. Return true if successful, or if no repair needed.
	 */
	@Override
	public boolean attemptRepair() {
		if (loadError == null && getAudioEffect() != null) {
			return true;
		}

		TestedSoundFile newTestedFile = getTestedSoundFile();
		if (newTestedFile != null && newTestedFile.isValid()) {
			setLoadError(null);
			setAudioEffect(newTestedFile.getAudio());
			return true;
		}
		else {
			setLoadError(SoundResourceLoadError.CORRUPTED_TERMINAL);
			return false;
		}		
	}

	/**
	 * Try to make a tested sound file from the file/uri path
	 * @return
	 */
	public TestedSoundFile getTestedSoundFile() {
		// gets the path from the file if there's one defined, or if not from the uri path originally loaded
		String uriPath = getUriPath();
		if (uriPath == null || uriPath.isEmpty()) {
			// nothing to try
			return null;
		}
		
		// have a path so try again to load it (perhaps the user has made the file accessible)
		try {
			return new TestedSoundFile(new File(new URI(uriPath)));
		} catch (Exception e) {
			// no need to repeat the log here, failure just results in no file
		}
		
		return null;
	}

	@Override
	public SoundStatus getStatus() {
		return status;
	}

	@Override
	public void setStatus(SoundStatus status) {
		this.status = status;
	}

}
