package com.stillwindsoftware.keepyabeat.db;

import java.util.HashSet;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper.KybDataException;
import com.stillwindsoftware.keepyabeat.gui.LocaleUtils;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidSoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.ProposedSoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;

/**
 * Content provider to access the Sounds database table. It also implements Sounds, and so
 * can act as the data accessor for the core APIs too.
 */
public class SoundsContentProvider extends KybContentProvider implements Sounds {


	private static final String LOG_TAG = "KYB-"+SoundsContentProvider.class.getSimpleName();
	
	// constants for content providers
	private static final String AUTHORITY = "com.stillwindsoftware.keepyabeat.sounds.contentprovider"; // matches manifest
	private static final String BASE_PATH = "sounds";
	public static final String SINGLE_ROW_CONTENT_STR = "content://" + AUTHORITY + "/" + BASE_PATH + "/%s";
	private static final String ORDERED_LIST = "orderedlist";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);
	public static final Uri CONTENT_URI_ORDERED_LIST = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH + "/" + ORDERED_LIST);

	public static final String LIST_ORDER_BY = KybSQLiteHelper.COLUMN_SOURCE_TYPE
			+ ", " + KybSQLiteHelper.COLUMN_SOUND_NAME;

	// uri matching
	private static final int URI_MATCH_SINGLE_ROW = 1;
	private static final int URI_MATCH_BASE = 10;
	private static final int URI_MATCH_ORDERED_LIST = 20;
	private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		sURIMatcher.addURI(AUTHORITY, BASE_PATH, URI_MATCH_BASE);
		sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", URI_MATCH_SINGLE_ROW);
		sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/" + ORDERED_LIST, URI_MATCH_ORDERED_LIST);
	}
	
	private static final String[] SOUND_COLUMNS = new String[] {
		KybSQLiteHelper.COLUMN_ID
		,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
		,KybSQLiteHelper.COLUMN_SOURCE_TYPE
		,KybSQLiteHelper.COLUMN_SOUND_NAME
		,KybSQLiteHelper.COLUMN_SOUND_URI
		,KybSQLiteHelper.COLUMN_SOUND_DURATION
		,KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME
		,KybSQLiteHelper.COLUMN_SOUND_RAW_RES_NAME
	};

    private long mLastDeletion = 0L; // set everytime a sound gets deleted, this signals the player rhythm holder that it's time to get a complete refresh

    @Override
	public boolean onCreate() {
		super.onCreate();
		mLibrary.setSounds(this);
		return false;
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
		String viewToUse = KybSQLiteHelper.TABLE_SOUNDS; 
		String orderBy = sortOrder;

    	// ignore other params for library - one row, just check the uri is correct
		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
		else if (uriType == URI_MATCH_ORDERED_LIST) {
			// use a different view with extra ordering and blank rows for controls (collapse buttons)
			// used for the beat types dialog_simple_list shown in that module (not for other places in the rhythm modules)
			viewToUse = KybSQLiteHelper.VIEW_SOUNDS_ORDERED_LIST;
			orderBy = LIST_ORDER_BY;
		}
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to query with unknown URI: " + uri);
		}
		
		Cursor cursor = query(viewToUse, projection, selection, selectionArgs, orderBy);
		
		// make sure that potential listeners are getting notified
        cursor.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
        cursor.setNotificationUri(getContext().getContentResolver(), CONTENT_URI_ORDERED_LIST);

	    return cursor;
	}

    private void notifyChanges(Uri uri) {
        ContentResolver contentResolver = getContext().getContentResolver();
        contentResolver.notifyChange(uri, null);
        if (!uri.equals(CONTENT_URI)) {
            contentResolver.notifyChange(CONTENT_URI, null);
        }
        if (!uri.equals(CONTENT_URI_ORDERED_LIST)) {
            contentResolver.notifyChange(CONTENT_URI_ORDERED_LIST, null);
        }

        // also let anything listing beat types know
        contentResolver.notifyChange(BeatTypesContentProvider.CONTENT_URI_ORDERED_LIST, null);
    }

    @Override
	public Uri insert(Uri uri, ContentValues values) {
		
		int uriType = sURIMatcher.match(uri);

		if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to insert with unknown URI: " + uri);
		}
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		long id = db.insert(KybSQLiteHelper.TABLE_SOUNDS, null, values);
		
		if (id == -1) {
			AndroidResourceManager.loge(LOG_TAG, String.format("insert: error occurred, row not inserted values = %s", values));
		}

        notifyChanges(uri);
	    return Uri.parse(BASE_PATH + "/" + id);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to update with unknown URI: " + uri);
		}
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		int rowsUpdated = db.update(KybSQLiteHelper.TABLE_SOUNDS,
				values, selection, selectionArgs);

        notifyChanges(uri);
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
		int rowsDeleted = db.delete(KybSQLiteHelper.TABLE_SOUNDS, selection, selectionArgs);
		
		if (rowsDeleted == 0) {
			throw new KybDataException(String.format("delete(%s): error occurred, row not deleted selection = %s", LOG_TAG, selection));
		}

        // store the time for a deletion, just so player can reset (otherwise some data may get incorrectly updated...
        // happens when returning to the activity after deleting a sound, then the sounds spinner has changes which
        // are interpreted as legal user changes, but are just that the cursor is changed because of the delete)
        mLastDeletion = System.currentTimeMillis();

        notifyChanges(uri);
	    return rowsDeleted;
	}

	@Override
	public Sound lookup(String key) {
        AbstractSqlImpl cached = mLibrary.getFromCache(key);
        if (cached != null) {
            try {
                return (Sound) cached;
            }
            catch (ClassCastException e) {
                throw new KybDataException(String.format("lookup(%s): error occurred, row row in cache is wrong type = %s (type=)", LOG_TAG, key, cached.getClass().getName()));
            }
        }

        Cursor csr = query(KybSQLiteHelper.TABLE_SOUNDS, SOUND_COLUMNS,
				String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key), null, null);

		SoundSqlImpl sound = createSoundFromCursor(csr);
        return sound;
	}

//	/**
//	 * Overloads the standard lookup to provide lookup by internal id
//	 * @param id
//	 * @return
//	 */
//	public Sound lookup(long id) {
//		Cursor csr = query(KybSQLiteHelper.TABLE_SOUNDS, SOUND_COLUMNS, 
//				String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, id), null, null);
//		
//		return createSoundFromCursor(csr);
//	}

	private SoundSqlImpl createSoundFromCursor(Cursor csr) {
		try {
			if (csr.getCount() == 0) {
				return null;
			}
			else {
				csr.moveToFirst();
				SoundSqlImpl sound = new SoundSqlImpl(getContext(), mLibrary, csr);
                mLibrary.putIntoCache(sound.getKey(), sound);
				return sound;
			}
		} 
		finally {
			csr.close();
		}
	}
	

	@Override
	public Sound getTick() {
		return lookup(InternalSound.tick_middle.name());
	}

	@Override
	public Sound getTock() {
		return lookup(InternalSound.tock_shallow.name());
	}

	@Override
	public Sound getNoSound() {
		return lookup(InternalSound.noSound.name());
	}

	@Override
	public Sound copyImportSound(Function context, Sound impSound) {
		return addSound(context, new SoundSqlImpl(context, mLibrary, impSound));
	}

	@Override
	public Sound addProposedSoundResource(Function context, ProposedSoundResource proposedSoundResource) throws Exception {
        if (proposedSoundResource instanceof AndroidSoundResource) {
            AndroidSoundResource sr = (AndroidSoundResource) proposedSoundResource;
            return addSound(context, mLibrary.getNextKey(), sr.getName(), sr.getDuration(), sr);
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "addProposedSoundResource: not called with known type, what is this?");
            throw new IllegalArgumentException("unknown type="+proposedSoundResource);
        }
	}

	@Override
	public Sound makeSound(Function context, String key, String name) {
		SoundSqlImpl sound = new SoundSqlImpl(context, mLibrary, key, name);
        mLibrary.putIntoCache(key, sound);
        return sound;
	}

	@Override
	public Sound addSound(Function context, Sound sound) {

		// Columns left out because this is only for user sounds:
		// KybSQLiteHelper.COLUMN_LOCALISED_RES_ID
		// KybSQLiteHelper.COLUMN_SOUND_RAW_RES_ID
		
		SoundResource soundRes = sound.getSoundResource();
		
		ContentValues contentValues = new ContentValues();
		contentValues.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, sound.getKey());
		contentValues.put(KybSQLiteHelper.COLUMN_SOURCE_TYPE, soundRes.getType().name());
		contentValues.put(KybSQLiteHelper.COLUMN_SOUND_NAME, sound.getName());
		contentValues.put(KybSQLiteHelper.COLUMN_SOUND_URI, soundRes.getUriPath());
		contentValues.put(KybSQLiteHelper.COLUMN_SOUND_DURATION, sound.getDuration());
		
		// insert using the uri to ensure clients get the update notified
		// at least dialog_simple_list ones should...
		Uri insertUri = insert(CONTENT_URI, contentValues);
		long id = Long.parseLong(insertUri.getLastPathSegment());
		
		if (id != -1) {
			((SoundSqlImpl)sound).setInternalId(id);
		}

        mLibrary.putIntoCache(sound.getKey(), (SoundSqlImpl)sound);

		return sound;
	}

	@Override
	public Sound addSound(Function context, String key, String name,
			float duration, SoundResource soundResource) {
		SoundSqlImpl sound = (SoundSqlImpl) makeSound(context, key, name);
		sound.setDuration(context, duration);
		sound.setSoundResource(context, soundResource);
		return addSound(context, sound);
	}

	@Override
	public void removeSound(Function context, Sound sound) {

        // be defensive in case there's a beat type that references it, there shouldn't be one!
        ((BeatTypesContentProvider)mLibrary.getBeatTypes()).resetBeatTypesToFallbackSound(sound.getKey());

		delete(CONTENT_URI,
                String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, sound.getKey())
                , null);

        mLibrary.removeFromCache(sound.getKey());

        if (sound.getSoundResource().getType() == PlatformResourceManager.SoundResourceType.URI) {
            ((AndroidResourceManager)mLibrary.getResourceManager()).deleteSoundFile(sound.getSoundResource());
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "removeSound: should not be removing sound that is not URI type... "+sound.getSoundResource().getType());
        }
	}

	@Override
	public void changeSoundName(Function context, Sound sound, String newName) {
		((SoundSqlImpl)sound).setName(context, newName);
		
		// update only if it's a db set id
		long id = ((SoundSqlImpl)sound).getInternalId();
		
		if (id != Library.INT_NOT_CHANGED) {
			Uri updateUri = Uri.parse(String.format(SINGLE_ROW_CONTENT_STR, id));
			ContentValues contentValues = new ContentValues();
			contentValues.put(KybSQLiteHelper.COLUMN_SOUND_NAME, newName);
			int num = update(updateUri, contentValues, null, null);
			
			if (num != 1) {
				AndroidResourceManager.loge(LOG_TAG, String.format("changeSoundName: single update updated %s rows", num));
			}
		}
		
	}

	@Override
    public boolean isSystemSound(Sound sound) {
        return isSystemSound(sound.getKey());
    }

    public boolean isSystemSound(String soundKey) {
        try {
            return InternalSound.valueOf(soundKey) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Only called from the undo of the RhythmImporter (so not yet implemented in Android)
     * @param context
     * @param keys
     */
    @Override
	public void removeByKey(Function context, HashSet<String> keys) {
		
		StringBuilder sb = makeExternalKeyInClauseFromSet(keys);
		if (sb != null) {
            String key = sb.toString();
            delete(CONTENT_URI, key, null);
            mLibrary.removeFromCache(key);
		}
	}

	/**
	 * Called from AndroidResourceManager.isSoundFileInLibrary()
	 * @param uri
	 * @return
	 */
	public boolean isUriInLibrary(Uri uri) {

		Cursor csr = query(KybSQLiteHelper.TABLE_SOUNDS, SOUND_COLUMNS,
                String.format(COLUMN_MATCH_STRING_FORMAT
                        , KybSQLiteHelper.COLUMN_SOUND_URI, uri.getPath()), null, null);
		
		try {
			if (csr.getCount() == 0) {
				return false;
			}
			else {
				return true;
			}
		}
		finally {
			csr.close();
		}
	}

    @Override
    public String getListenerKey() {
        return "sounds";
    }

    /**
     * Checks for the name exists as one of the default sounds or any custom one.
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
                R.string.BEEP1_SOUND_NAME,
                R.string.BEEP2_SOUND_NAME,
                R.string.BEEP3_SOUND_NAME,
                R.string.CLAP_HIGH_SOUND_NAME,
                R.string.CLAP_LOW_SOUND_NAME,
                R.string.CLAP_DOUBLE_SOUND_NAME,
                R.string.MARACAS_SOUND_NAME,
                R.string.TAMBORINE_SOUND_NAME,
                R.string.TICK_MIDDLE_SOUND_NAME,
                R.string.TICK_WOODEN_SOUND_NAME,
                R.string.TOCK_LOW_SOUND_NAME,
                R.string.TOCK_SHALLOW_SOUND_NAME,
                R.string.TOCK_WOODEN_SOUND_NAME,
                R.string.NO_SOUND_NAME)) {
            return true;
        }

        String[] args = new String[] { name.toUpperCase(), excludeKey };

        Cursor csr = query(KybSQLiteHelper.TABLE_SOUNDS, SOUND_COLUMNS,
                    String.format("UPPER(%s) = ? AND %s != ?", KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY), args, null);

        try {
            return csr.getCount() > 0;
        }
        finally {
            csr.close();
        }
    }


    /**
     * new sound resource to put on the sound
     * @param soundKey
     * @param soundResource
     */
    public void repairSound(String soundKey, AndroidSoundResource soundResource) {

        SoundSqlImpl sound = (SoundSqlImpl) lookup(soundKey);
        if (sound == null) {
            AndroidResourceManager.loge(LOG_TAG, "repairSound: sound not in library, key="+soundKey);
            return;
        }

        sound.setSoundResource(Function.getBlankContext(mLibrary.getResourceManager()), soundResource);

        long id = ((SoundSqlImpl)sound).getInternalId();

        Uri updateUri = Uri.parse(String.format(SINGLE_ROW_CONTENT_STR, id));
        ContentValues contentValues = new ContentValues();
        contentValues.put(KybSQLiteHelper.COLUMN_SOUND_URI, soundResource.getUriPath());
        int num = update(updateUri, contentValues, null, null);

        if (num != 1) {
            AndroidResourceManager.loge(LOG_TAG, String.format("repairSound: single update updated %s rows", num));
        }
    }

    /**
     * See comments in delete()
     * @return
     */
    public long getLastDeletion() {
        return mLastDeletion;
    }
}
