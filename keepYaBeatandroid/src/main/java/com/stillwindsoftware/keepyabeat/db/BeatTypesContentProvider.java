package com.stillwindsoftware.keepyabeat.db;

import java.util.HashSet;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper.KybDataException;
import com.stillwindsoftware.keepyabeat.gui.LocaleUtils;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;
import com.stillwindsoftware.keepyabeat.platform.PersistentStatesManager;

/**
 * Content provider to access the Beat Types database table
 */
public class BeatTypesContentProvider extends KybContentProvider implements BeatTypes {

    private static final String LOG_TAG = "KYB-"+BeatTypesContentProvider.class.getSimpleName();

    public static final String BEAT_TYPE_MISSING_NAME_STRING_KEY = "beatTypeMissingName";

	// constants for content providers
	private static final String AUTHORITY = "com.stillwindsoftware.keepyabeat.beattypes.contentprovider"; // matches manifest
	private static final String BASE_PATH = "beattypes";
	private static final String SINGLE_ROW_CONTENT_STR = "content://" + AUTHORITY + "/" + BASE_PATH + "/%s";
	private static final String ORDERED_LIST = "orderedlist";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);
	public static final Uri CONTENT_URI_ORDERED_LIST = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH + "/" + ORDERED_LIST);

	public static final String LIST_ORDER_BY = KybSQLiteHelper.COLUMN_SOURCE_TYPE
			+ ", " + KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME;
	
	// uri matching
	private static final int URI_MATCH_SINGLE_ROW = 1;
	private static final int URI_MATCH_BASE = 10;
	private static final int URI_MATCH_ORDERED_LIST = 20;
    private final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	{
		sURIMatcher.addURI(AUTHORITY, BASE_PATH, URI_MATCH_BASE);
		sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", URI_MATCH_SINGLE_ROW);
		sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/" + ORDERED_LIST, URI_MATCH_ORDERED_LIST);
	}

	public static final String[] BEAT_TYPE_COLUMNS = new String[] {
		KybSQLiteHelper.COLUMN_ID
		,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
		,KybSQLiteHelper.COLUMN_SOURCE_TYPE
		,KybSQLiteHelper.COLUMN_SOUND_FK
		,KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME
		,KybSQLiteHelper.COLUMN_COLOUR
		,KybSQLiteHelper.COLUMN_VOLUME
		,KybSQLiteHelper.COLUMN_FALLBACK_SOUND_FK
		,KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME
		// does not include this column as is rarely needed see repairSoundsIfMissing()
		// COLUMN_BEAT_TYPE_MISSING_SOUND_EXTERNAL_FK
	};
	  

	@Override
	public boolean onCreate() {
		super.onCreate();
		mLibrary.setBeatTypes(this);
		return false;
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
		String viewToUse = KybSQLiteHelper.TABLE_BEAT_TYPES; 
		String orderBy = sortOrder;
		
		// ignore other params for library - one row, just check the uri is correct
		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
		else if (uriType == URI_MATCH_ORDERED_LIST) {
			// use a different view with extra ordering and blank rows for controls (collapse buttons)
			// used for the beat types dialog_simple_list shown in that module (not for other places in the rhythm modules)
			viewToUse = KybSQLiteHelper.VIEW_BEAT_TYPES_ORDERED_LIST;
			orderBy = LIST_ORDER_BY;
		}
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to query with unknown URI: " + uri);
		}
		
		Cursor cursor = query(viewToUse, projection, selection, selectionArgs, orderBy);
		
		// make sure that potential listeners are getting notified
	    cursor.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
	    
	    return cursor;
	}
	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		
		int uriType = sURIMatcher.match(uri);

		if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to insert with unknown URI: " + uri);
		}
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		long id = db.insert(KybSQLiteHelper.TABLE_BEAT_TYPES, null, values);
		
		if (id == -1) {
			throw new KybDataException(String.format("insert(%s): error occurred, row not inserted values = %s", LOG_TAG, values));
		}

        //noinspection ConstantConditions
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
	    return Uri.parse(BASE_PATH + "/" + id);
	}

	@Override
	public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {

		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to update with unknown URI: " + uri);
		}
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		int rowsUpdated = db.update(KybSQLiteHelper.TABLE_BEAT_TYPES,
                values, selection, selectionArgs);

        //noinspection ConstantConditions
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
	    return rowsUpdated;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {

		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to delete with unknown URI: " + uri);
		}
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		int rowsDeleted = db.delete(KybSQLiteHelper.TABLE_BEAT_TYPES, selection, selectionArgs);
		
		if (rowsDeleted == 0) {
			throw new KybDataException(String.format("delete(%s): error occurred, row not deleted selection = %s", LOG_TAG, selection));
		}

        //noinspection ConstantConditions
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
	    return rowsDeleted;
	}

    /**
     * Called from delete(), just in case the beat type was referenced by player state, make sure that gets
     * updated if so.
     */
    private void ensurePlayerStateIntegrity() {
        PersistentStatesManager settingsManager = mLibrary.getResourceManager().getPersistentStatesManager();
        PlayerState playerState = settingsManager.getPlayerState();
        if (lookup(playerState.getBeatType().getKey()) == null) {
            playerState.setBeatType(lookup(DefaultBeatTypes.normal.getKey()));
        }
    }

    @Override
	public BeatType lookup(String key) {

        AbstractSqlImpl cached = mLibrary.getFromCache(key);
        if (cached != null) {
            try {
                return (BeatType) cached;
            }
            catch (ClassCastException e) {
                throw new KybDataException(String.format("lookup(%s): error occurred, row row in cache is wrong type = %s (type=)", LOG_TAG, key, cached.getClass().getName()));
            }
        }

		Cursor csr = query(KybSQLiteHelper.TABLE_BEAT_TYPES, BEAT_TYPE_COLUMNS,
                String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key), null, null);
		
		try {
			if (csr.getCount() == 0) {
				return null;
			}
			else {
				csr.moveToFirst();
				BeatTypeSqlImpl beatType = new BeatTypeSqlImpl(getContext(), mLibrary, csr);
                mLibrary.putIntoCache(key, beatType);
				return beatType;
			}
		} 
		finally {
			csr.close();
		}
	}

	/**
	 * Test out possible source names until a valid one is returned
	 * @param sourceName
	 * @return
	 */
	@Override
	public String getCloneBeatTypeName(String sourceName) {
		// one query is enough, chop the part that will distinguish and do a like comparison
		// max beat type name, less 4 for "(n)", PLUS 3 more if it's too long (for "...")
		int SUFFIX_LEN = 4;
		int ELLIPSES_LEN = 3; //"..."
		String likeCompare = sourceName.length() + SUFFIX_LEN > BeatType.MAX_BEAT_TYPE_NAME_LEN
				? sourceName.substring(0, BeatType.MAX_BEAT_TYPE_NAME_LEN -SUFFIX_LEN -ELLIPSES_LEN)
				: sourceName;

		Cursor csr = query(KybSQLiteHelper.TABLE_BEAT_TYPES, new String[]{KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME},
                String.format(COLUMN_LIKE_STRING_FORMAT, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, likeCompare, "%")
                , null, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME); // order by beat type name
   
		try {
			// no rows with similar name
			if (csr.getCount() == 0) {
				return sourceName;
			}

			for (int i = 2; true; i++) {
				String suffix = String.format("(%s)", i);
				String proposedName = String.format("%s%s", sourceName, suffix); 
				if (proposedName.length() > BeatType.MAX_BEAT_TYPE_NAME_LEN) {
					proposedName = String.format("%s%s%s"
							, sourceName.substring(0, BeatType.MAX_BEAT_TYPE_NAME_LEN -suffix.length() -ELLIPSES_LEN)
							, "...", suffix);
				}

				// iterate the cursor
				csr.moveToFirst();
				// this way is more explicit than doing a continue in the do..while to the
				// outer loop but dropping through when no match 
				boolean foundMatch = false;
				do {
					if (proposedName.equals(csr.getString(0))) {
						foundMatch = true;
						break; // do..while
					}
				} while (csr.moveToNext());


				// no matches found for this proposed name, safe to return it
				if (!foundMatch) {
					return proposedName;
				}
			}
		} finally {
			csr.close();
		}
	}

	@Override
	public void changeBeatType(Function context, BeatType beatType,
			int whichParts, String name, Colour colour, float volume,
			Sound sound, Sound fallbackSound) {
		// which has changed
		boolean nameChanged = (whichParts & NAME_CHANGED) == NAME_CHANGED;
		boolean colourChanged = (whichParts & COLOUR_CHANGED) == COLOUR_CHANGED;
		boolean volumeChanged = (whichParts & VOLUME_CHANGED) == VOLUME_CHANGED;
		boolean soundChanged = (whichParts & SOUND_CHANGED) == SOUND_CHANGED;
		boolean fallbackSoundChanged = (whichParts & FALLBACK_SOUND_CHANGED) == FALLBACK_SOUND_CHANGED;
		
		// update the bean, and also provide a value for db update
		ContentValues values = new ContentValues();
		
		if (nameChanged) {
			beatType.setName(context, name);
			values.put(KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, name);

            // there's a fix for missing beat types which puts a localised string in for a missing name when inserting
            if (!isDefaultBeatType(beatType)) {
                values.putNull(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME);
            }
		}
		
		if (colourChanged) {
			beatType.setColour(context, colour);
			values.put(KybSQLiteHelper.COLUMN_COLOUR, ((AndroidColour)colour).getColorInt());
		}
		
		if (volumeChanged) {
			beatType.setVolume(context, volume);
			values.put(KybSQLiteHelper.COLUMN_VOLUME, volume);
		}
		
		if (soundChanged) {
			beatType.setSound(context, sound);
			values.put(KybSQLiteHelper.COLUMN_SOUND_FK, ((SoundSqlImpl)sound).getKey());
			// non system sound, default a fallback sound unless already set or one is also supplied 
			SoundsContentProvider sounds = (SoundsContentProvider) mLibrary.getSounds();
			if (!sounds.isSystemSound(sound) 
					&& ((BeatTypeSqlImpl) beatType).getFallbackSoundKey() == null 
							&& !fallbackSoundChanged) {
				SoundSqlImpl tick = (SoundSqlImpl) sounds.getTick();
				beatType.setFallbackSound(context, tick);
				values.put(KybSQLiteHelper.COLUMN_FALLBACK_SOUND_FK, tick.getKey());
			}
		}
		
		if (fallbackSoundChanged) {
			beatType.setFallbackSound(context, fallbackSound);
			values.put(KybSQLiteHelper.COLUMN_FALLBACK_SOUND_FK, ((SoundSqlImpl)fallbackSound).getKey());
		}

		// update only if it's a db set id
		Uri updateUri = Uri.parse(String.format(SINGLE_ROW_CONTENT_STR, ((BeatTypeSqlImpl)beatType).getInternalId()));
		int num = update(updateUri, values, null, null);
		
		if (num != 1) {
			throw new KybDataException(String.format("changeBeatType(%s): error occurred, single row (%s) not updated values = %s id=%s"
					, LOG_TAG, num, values, ((BeatTypeSqlImpl)beatType).getInternalId()));
		}
		
	}

	@Override
	public BeatType addBeatType(Function context, String nextKey, String name,
			Colour colour, float volume, Sound sound, Sound fallbackSound) {

		// Columns left out because this is only for user beat types:
		// KybSQLiteHelper.COLUMN_LOCALISED_RES_ID

		ContentValues contentValues = new ContentValues();
		contentValues.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, nextKey);
		contentValues.put(KybSQLiteHelper.COLUMN_SOURCE_TYPE, KybSQLiteHelper.USER_ENTERED_TYPE);
		contentValues.put(KybSQLiteHelper.COLUMN_SOUND_FK, sound.getKey());
        if (name == null) {
            name = BEAT_TYPE_MISSING_NAME_STRING_KEY;
            contentValues.put(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME, name);
        }
		contentValues.put(KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, name);
		contentValues.put(KybSQLiteHelper.COLUMN_COLOUR, ((AndroidColour)colour).getColorInt());
		contentValues.put(KybSQLiteHelper.COLUMN_VOLUME, volume);

        if (fallbackSound == null) {
            fallbackSound = mLibrary.getSounds().getTick();
        }

		contentValues.put(KybSQLiteHelper.COLUMN_FALLBACK_SOUND_FK, fallbackSound.getKey());

		insert(CONTENT_URI, contentValues);
		
		return lookup(nextKey);
	}

	@Override
	public void removeBeatType(Function context, BeatType beatType) {
		delete(CONTENT_URI, 
				String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, beatType.getKey())
				, null);

        mLibrary.removeFromCache(beatType.getKey());

        ensurePlayerStateIntegrity();
    }

	@Override
	public boolean isDefaultBeatType(BeatType beatType) {
		return isDefaultBeatType(beatType.getKey());
	}

	@Override
	public boolean isDefaultBeatType(String key) {
        try {
            return DefaultBeatTypes.valueOf(key) != null;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

	@Override
	public void repairSoundsIfMissing(Function context, Sound sound) {
		// the column currently exists, but not populated anywhere
//		COLUMN_BEAT_TYPE_MISSING_SOUND_EXTERNAL_FK
		String msg = "repairSoundsIfMissing is not implemented for sqlite version";
		AndroidResourceManager.logv(LOG_TAG, msg);
	}

	@Override
	public void addDefaultBeatTypes(Function context, Sounds sounds,
			DefaultBeatTypes... defaultBeatTypes) {
		// nothing to do, this is handled in db onCreate		
	}

	@Override
	public BeatType copyImportBeatType(Function context, BeatType beatType,
			Sound baseSound, Sound baseFbSound) {

		return addBeatType(context, mLibrary.getNextKey()
				, getCloneBeatTypeName(beatType.getName())
				, beatType.getColour()
				, beatType.getVolume()
				, baseFbSound
				, baseFbSound);
	}

	@Override
	public void removeByKey(Function context, HashSet<String> keys) {

		StringBuilder sb = makeExternalKeyInClauseFromSet(keys);
		if (sb != null) {
            String key = sb.toString();
			delete(CONTENT_URI, key, null);
            mLibrary.removeFromCache(key);

            ensurePlayerStateIntegrity();
		}
	}

    @Override
    public String getListenerKey() {
        return "beattypes";
    }

    /**
     * Checks for the name exists as one of the default beat types or any custom one.
     * It also checks the default names for each translated language.
     * It's not perfect this way, the user could create a name that is a dupe in another
     * language that is yet to be translated, and when that does happen there could
     * be a clash but can't do anything about that
     * @param name
     * @param excludeKey
     * @return
     */
    public boolean nameExists(String name, String excludeKey) {

        if (LocaleUtils.stringExistsInAnyLanguage(name, mLibrary.getApplication().getResources(),
                R.string.NORMAL_BEAT_TYPE_NAME,
                R.string.ACCENT_BEAT_TYPE_NAME,
                R.string.ALTERNATE_BEAT_TYPE_NAME,
                R.string.FIRST_BEAT_TYPE_NAME,
                R.string.SILENT_BEAT_TYPE_NAME)) {
            return true;
        }

        String[] args = new String[] { name.toUpperCase(), excludeKey };

        Cursor csr = query(KybSQLiteHelper.TABLE_BEAT_TYPES, BEAT_TYPE_COLUMNS,
                String.format("UPPER(%s) = ? AND %s != ?", KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY), args, null);

        try {
            return csr.getCount() > 0;
        }
        finally {
            csr.close();
        }
    }

    /**
     * Called by SoundsListFragment when delete is picked on the menu to find
     * beat types referenced
     * @param soundKey
     * @return
     */
    public int getReferencedBeatTypesCount(String soundKey) {
        Cursor csr = getReferencedBeatTypes(soundKey);
        try {
            return csr.getCount();
        } finally {
            csr.close();
        }
    }

    /**
     * Called by DeleteSoundListDialog when delete is confirmed and need the references
     * for the sound memento
     * @param soundKey
     * @return
     */
    public Cursor getReferencedBeatTypes(String soundKey) {
        return query(CONTENT_URI,
                new String[] { KybSQLiteHelper.COLUMN_EXTERNAL_KEY, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME },
                String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_SOUND_FK, soundKey),
                null, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME);
    }

    /**
     * Called from SoundsContentProvider.removeSound() to ensure a fk reference doesn't remain
     * (being super cautious because the whole delete/undo delete process is so tricky)
     * @param soundKey
     */
    public void resetBeatTypesToFallbackSound(String soundKey) {

        ContentValues values = new ContentValues();
        values.put(KybSQLiteHelper.COLUMN_SOUND_FK, Sounds.InternalSound.tock_shallow.name());
        int num = update(CONTENT_URI, values, String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_SOUND_FK, soundKey), null);

        if (num != 0) {
            AndroidResourceManager.logw(LOG_TAG, String.format("resetBeatTypesToFallbackSound: a fk error was corrected (%s rows updated)", num));
        }
    }
}
