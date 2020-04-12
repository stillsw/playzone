package com.stillwindsoftware.keepyabeat.platform;

import java.io.File;

import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceLoadError;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceType;
import com.stillwindsoftware.keepyabeat.player.backend.StreamAudioTrackStrategy;

public class AndroidSoundResource implements SoundResource, PlatformResourceManager.ProposedSoundResource {

	private static final String LOG_TAG = "KYB-"+AndroidSoundResource.class.getSimpleName();

	private SoundResourceType mResourceType;
	private int mResId;
	private SoundResourceLoadError mLoadError;
	private String mLocalisedErrorMessage;
	private File mSoundFile;
    private byte[] mBytes;
	private float mDuration = Library.FLOAT_NOT_CHANGED;
	private String mUriPath;
	private SoundStatus mStatus;
    private String mName; // used during creation for proposed sound resource only
    private long mPlayingTimeNanos = -1L;

    public AndroidSoundResource(SoundResourceType resourceType) {
		mResourceType = resourceType;
	}

    public AndroidSoundResource(SoundResourceType resourceType, int resId, float duration, String uriPath) {
        this(resourceType);
        mResId = resId;
        mDuration = duration;
        mUriPath = uriPath;
    }

    /**
     * called from AddOrRepairSoundDialog, the sound is good at this point, so status is set to loaded ok
     * @param resourceType
     * @param duration
     * @param bytes
     */
    public AndroidSoundResource(SoundResourceType resourceType, float duration, byte[] bytes) {
        this(resourceType);
        mStatus = SoundStatus.LOADED_OK;
        mBytes = bytes;
        mDuration = duration;

    }

    /**
     * called from KybContentProvider to inflate the undo sound delete memento, the sound file
     * has gone, so it's not loaded or playable and needs repair
     * @param resourceType
     * @param duration
     * @param uriPath
     */
    public AndroidSoundResource(SoundResourceType resourceType, float duration, String uriPath) {
        this(resourceType);
        mDuration = duration;
        mUriPath = uriPath;
        mStatus = SoundStatus.LOAD_FAILED_FILE_NOT_FOUND;
    }

    public void setProposedSoundNameAndFileAndUriPath(String name, String uriPath, File soundFile) {
        mSoundFile = soundFile;
        mUriPath = uriPath;
        mName = name;
    }

    public int getResId() {
		return mResId;
	}

    public byte[] getBytes() {
        return mBytes;
    }

    public String getName() {
        return mName;
    }

    @Override
	public SoundResourceType getType() {
		return mResourceType;
	}

	@Override
	public SoundResourceLoadError getLoadError() {
		return mLoadError;
	}

	@Override
	public void setLoadError(SoundResourceLoadError errorType) {
		mLoadError = errorType;
	}

	public void setLoadErrorLocalisedMessage(String localisedErrorMesage) {
		mLocalisedErrorMessage = localisedErrorMesage;
	}

	@Override
	public String getLocalisedErrorMessage() {
		return mLocalisedErrorMessage;
	}

	@Override
	public boolean attemptRepair() {
		// TODO go around and try again
		return false;
	}

	@Override
	public boolean isPlayable() {
        return (mResourceType == SoundResourceType.INT && mResId > 0)
            || (mResourceType == SoundResourceType.URI && mStatus == SoundStatus.LOADED_OK);
	}

	@Override
	public float getDuration() {
		return mDuration;
	}

	@Override
	public String getFileName() {
		if (mSoundFile != null)
			return mSoundFile.getName();
		else
			return mUriPath;
	}

	@Override
	public void setUriPath(String uriPath) {
		mUriPath = uriPath;
	}

	@Override
	public String getUriPath() {
		return mUriPath;
	}

	@Override
	public void playSound(float volume, float secondsOfSilence) {
		// not used for android version
	}

	@Override
	public void release() {
		// not used for android version
	}

	/**
	 * Called from AndroidResourceManager.getSoundResourceFromName(), at this
	 * point existence has been tested, but not validity it's just putting the file on the resource
	 * to be used/tested later
	 * @param soundFile
	 */
	public void setSoundFile(File soundFile) {
		mSoundFile = soundFile;
	}

	@Override
	public SoundStatus getStatus() {
		return mStatus;
	}

	@Override
	public void setStatus(SoundStatus status) {
		mStatus = status;
	}

    @Override
    public void setPlayingTimeNanos(long nanos) {
        mPlayingTimeNanos = nanos;
    }

    @Override
    public long getPlayingTimeNanos() {
        return mPlayingTimeNanos;
    }

}
