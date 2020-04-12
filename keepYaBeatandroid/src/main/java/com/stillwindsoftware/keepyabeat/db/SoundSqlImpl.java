package com.stillwindsoftware.keepyabeat.db;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidSoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResourceType;

public class SoundSqlImpl extends AbstractSqlImpl implements Sound {

	private static final String LOG_TAG = "KYB-"+SoundSqlImpl.class.getSimpleName();

	private float mDuration;
	private AndroidSoundResource mSoundResource;
	// a temporary id allocated by SoundPool (see BeatTrackerBinder)
	private int mStreamId =-1;

	/**
	 * Create a sound from a database row
	 * @param context 
	 * @param library
	 * @param csr
	 */
	public SoundSqlImpl(Context context, KybSQLiteHelper library, Cursor csr) {
		super(library
			, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY))
			, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_NAME))
			, csr.getLong(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));

		// name: use the localised string if there's an id for it, otherwise name (already set in super)
		String resName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
		int resId = -1;
		if (resName != null) {
			resId = getLocalisedResIdFromName(resName, context.getResources());
			if (resId > 0) {
				mName = context.getResources().getString(resId);
			}
		}
		
		AndroidResourceManager.logv(LOG_TAG, String.format("<init>: %s", mName));
		
		mDuration = csr.getFloat(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_DURATION));
		
		String soundType = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOURCE_TYPE));
		boolean isInternal = KybSQLiteHelper.INTERNAL_TYPE.equals(soundType);
		AndroidResourceManager resourceManager = (AndroidResourceManager) library.getResourceManager();
		SoundResourceType resourceType = SoundResourceType.valueOf(soundType);
		
		if (isInternal) {			
			// sound resource only for playable sounds
			if (resId != R.string.NO_SOUND_NAME) {
				String rawResName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_RAW_RES_NAME));
				int rawResId = getRawResIdFromName(rawResName, context.getResources());
				SoundResource soundResource = new AndroidSoundResource(resourceType, rawResId, mDuration, rawResName);
				soundResource.setStatus(SoundStatus.LOADED_OK);
				setSoundResource(Function.getBlankContext(resourceManager), soundResource);
			}
		}
		else {
			String soundUri = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_URI));
			SoundResource soundResource = null;
			soundResource = resourceManager.getSoundResourceFromName(
					resourceType
					, soundUri
					, mDuration
					, true // at the moment Uri sounds are all privately held files, so attempt to load it
					, true); // coming from cursor, always will be legal 
			setSoundResource(Function.getBlankContext(resourceManager), soundResource);
		}
	}

	/**
	 * Base bones, called from SoundsContentProvider.makeSound()
	 * @param context
	 * @param library
	 * @param key
	 * @param name
	 */
	public SoundSqlImpl(Function context, KybSQLiteHelper library, String key, String name) {
		super(library, key, name);
	}

	/**
	 * Called from SoundsContentProvider.copyImportSound() to make a copy of the import, 
	 * needs to also copy the SoundResource
	 * @param context
	 * @param library
	 * @param sound
	 */
	public SoundSqlImpl(Function context, KybSQLiteHelper library, Sound sound) {
		super(library, sound.getKey(), sound.getName());
		mDuration = sound.getDuration();
		mSoundResource = (AndroidSoundResource) sound.getSoundResource();
	}

	@Override
	public float getDuration() {
		return mDuration;
	}

	@Override
	public SoundResource getSoundResource() {
		return mSoundResource;
	}

	@Override
	public boolean isPlayable() {
		return (mSoundResource != null && mSoundResource.isPlayable());
	}

	@Override
	public void setSoundResource(Function context, SoundResource soundResource) {
		context.recordFunctionStepBegin("setSoundResource");
		mSoundResource = (AndroidSoundResource) soundResource;

		// internal sounds are always loaded ok
		if (mSoundResource.getType() == SoundResourceType.INT) {
			mSoundResource.setStatus(SoundStatus.LOADED_OK);
		}
		else if (mSoundResource.getStatus() == SoundStatus.LOADED_OK) {
            // it's good, which should be normal for uri sound
        }
        else if (soundResource.getLoadError() == null) {
			mSoundResource.setStatus(SoundStatus.LOAD_PENDING); // sound resources are always pending here
			AndroidResourceManager.logd(LOG_TAG, String.format("setSoundResource: %s load pending", mName));
		}
		else {
			switch (soundResource.getLoadError()) {
			case INVALID_FORMAT : // fall through
			case CORRUPTED_TERMINAL : mSoundResource.setStatus(SoundStatus.LOAD_FAILED_ERROR); break;
			default : mSoundResource.setStatus(SoundStatus.LOAD_FAILED_FILE_NOT_FOUND); break;
			}
			AndroidResourceManager.logw(LOG_TAG, String.format("setSoundResource: sound error on %s (%s)", mName, mSoundResource.getStatus()));
		}

		context.recordFunctionStepEnd("setSoundResource");
	}

	@Override
	public void setDuration(Function context, float duration) {
		context.recordFunctionStepBegin("setDuration");
		mDuration = duration;
		context.recordFunctionStepEnd("setDuration");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (mId ^ (mId >>> 32));
		result = prime * result + ((mKey == null) ? 0 : mKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SoundSqlImpl other = (SoundSqlImpl) obj;
		if (mId != other.mId)
			return false;
		if (mKey == null) {
			if (other.mKey != null)
				return false;
		} else if (!mKey.equals(other.mKey))
			return false;
		return true;
	}

	public int getStreamId() {
		return mStreamId;
	}

	public void setStreamId(int streamId) {
		this.mStreamId = streamId;
	}

    @Override
    public String getListenerKey() {
        return getKey();
    }
}
