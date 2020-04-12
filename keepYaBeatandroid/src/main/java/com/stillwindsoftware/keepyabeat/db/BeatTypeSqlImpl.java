package com.stillwindsoftware.keepyabeat.db;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

public class BeatTypeSqlImpl extends AbstractSqlImpl implements BeatType {

	private static final String LOG_TAG = "KYB-"+BeatTypeSqlImpl.class.getSimpleName();

    private AndroidColour mColour;
    // keep the float values of the colour for faster drawing
    private float mVolume;

    private SoundSqlImpl mSound;
    private SoundSqlImpl mFallbackSound;

    // lazily populate sound, fallback sound, colour, rgba so keep the values for all
    private String mSoundKey;
    private String mFallbackSoundKey;
    private int mColorInt = Library.INT_NOT_CHANGED;

	/**
	 * Create a beat type from a database row
	 * @param context 
	 * @param context 
	 * @param library
	 * @param csr
	 */
	public BeatTypeSqlImpl(Context context, KybSQLiteHelper library, Cursor csr) {
		super(library
			, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY))
			, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME))
			, csr.getLong(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));

		// name: use the localised string if there's an id for it, otherwise name (already set in super)
		String resName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
		if (resName != null) {
			int resId = getLocalisedResIdFromName(resName, context.getResources());
			if (resId > 0) {
				mName = context.getResources().getString(resId);
			}
		}
		
		AndroidResourceManager.logd(LOG_TAG, String.format("<init>: %s", mName));
		
		mSoundKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_FK));
		mFallbackSoundKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_FALLBACK_SOUND_FK));
		mVolume = csr.getFloat(csr.getColumnIndex(KybSQLiteHelper.COLUMN_VOLUME));
		
		// colour has to be converted from int (see getColour() & getRGBA())
		mColorInt = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_COLOUR));

		getSound(); // cause sound to be loaded always (so no lookups get triggered in case where hasn't been)
	}

	@Override
	public Sound getSound() {
		if (mSound == null) {
			mSound = (SoundSqlImpl) ((SoundsContentProvider)mLibrary.getSounds()).lookup(mSoundKey);
		}
		return mSound;
	}

	@Override
	public Sound getFallbackSound() {
		if (mFallbackSound == null) {
			mFallbackSound = (SoundSqlImpl) ((SoundsContentProvider)mLibrary.getSounds()).lookup(mFallbackSoundKey);
		}
		return mFallbackSound;
	}

	@Override
	public Colour getColour() {
		if (mColour == null) {
			mColour = new AndroidColour(mColorInt);
		}
		return mColour;
	}

	@Override
	public float getVolume() {
		return mVolume;
	}

	@Override
	public void setSound(Function context, Sound sound) {
		mSound = (SoundSqlImpl) sound;
	}

	@Override
	public void setFallbackSound(Function context, Sound fallbackSound) {
		mFallbackSound = (SoundSqlImpl) fallbackSound;
	}

	/**
	 * Allows BeatTypesContentProvider to test the key directly instead of querying the db
	 * to find it
	 * @return
	 */
	public String getFallbackSoundKey() {
		return mFallbackSoundKey;
	}

	@Override
	public void setColour(Function context, Colour colour) {
		mColour = (AndroidColour) colour;
	}

	@Override
	public void setVolume(Function context, float volume) {
		mVolume = volume;
	}

    @Override
    public String getListenerKey() {
        return getKey();
    }
}
