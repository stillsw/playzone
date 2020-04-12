package com.stillwindsoftware.keepyabeat.db;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper.KybDataException;
import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.gui.PlayRhythmFragment;
import com.stillwindsoftware.keepyabeat.model.BeatFactory;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.RhythmMemento.ClonedRhythmMemento;
import com.stillwindsoftware.keepyabeat.model.RhythmXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder.UnrecognizedFormatException;

/**
 * Content provider to access the Beat Types database table
 */
public class RhythmsContentProvider extends KybContentProvider implements Rhythms, LibraryListener {

	private static final String LOG_TAG = "KYB-"+RhythmsContentProvider.class.getSimpleName();
    static final long WAIT_FOR_PLAYER_EDITOR_RHYTHM_DEFAULT_MILLIS = 5L;

    public static class RhythmReadLockNotHeldException extends Exception {
        public RhythmReadLockNotHeldException() {
            super();
        }
    }

    public static class RhythmWriteLockNotHeldException extends Exception {
        public RhythmWriteLockNotHeldException() {
            super();
        }
    }

    // constants for content providers
	private static final String AUTHORITY = "com.stillwindsoftware.keepyabeat.rhythms.contentprovider"; // matches manifest
	private static final String BASE_PATH = "rhythms";
    static final String SINGLE_ROW_CONTENT_STR = "content://" + AUTHORITY + "/" + BASE_PATH + "/%s";
    private static final String SEARCH = "/search";
    private static final String SEARCH_INCL_NEW = "/search_incl_new";
    public static final String SEARCH_CONTENT_STR = "content://" + AUTHORITY + "/" + BASE_PATH + SEARCH;
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);
    public static final Uri CONTENT_INCL_NEW_RHYTHM_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH + SEARCH_INCL_NEW);

	// uri matching
	private static final int URI_MATCH_SINGLE_ROW = 1;
	private static final int URI_MATCH_BASE = 10;
    private static final int URI_MATCH_SEARCH_LIST = 20;
    private static final int URI_MATCH_SEARCH_LIST_INCL_NEW_RHYTHM = 30;
    private final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    {
		sURIMatcher.addURI(AUTHORITY, BASE_PATH, URI_MATCH_BASE);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", URI_MATCH_SINGLE_ROW);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH + SEARCH, URI_MATCH_SEARCH_LIST);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH + SEARCH_INCL_NEW, URI_MATCH_SEARCH_LIST_INCL_NEW_RHYTHM);
	}

    public static final String[] RHYTHM_COLUMNS = new String[] {
            KybSQLiteHelper.COLUMN_ID
            ,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
            ,KybSQLiteHelper.COLUMN_RHYTHM_NAME
            ,KybSQLiteHelper.COLUMN_BPM
            ,KybSQLiteHelper.COLUMN_FULL_BEAT_1
            ,KybSQLiteHelper.COLUMN_ENCODED_BEATS
            //COLUMN_LOCALISED_RES_ID is on the table, but currently not used
    };

    public static final String[] RHYTHMS_LIST_COLUMNS = new String[] {
            KybSQLiteHelper.COLUMN_ID
            ,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
            ,KybSQLiteHelper.COLUMN_RHYTHM_NAME
            ,KybSQLiteHelper.COLUMN_BPM
            ,KybSQLiteHelper.COLUMN_FULL_BEAT_1
            ,KybSQLiteHelper.COLUMN_ENCODED_BEATS
            ,KybSQLiteHelper.VIEW_COLUMN_TAGS_LIST
    };

    public static final String[] RHYTHMS_INCL_NEW_COLUMNS = new String[] {
            KybSQLiteHelper.COLUMN_ID
            ,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
            ,KybSQLiteHelper.COLUMN_RHYTHM_NAME
            ,KybSQLiteHelper.COLUMN_ENCODED_BEATS
    };

    private static final String[] EDIT_RHYTHM_COLUMNS = new String[] {
		KybSQLiteHelper.COLUMN_BPM
		,KybSQLiteHelper.COLUMN_FULL_BEAT_1
		,KybSQLiteHelper.COLUMN_ENCODED_BEATS
		,KybSQLiteHelper.COLUMN_SOURCE_RHYTHM_FK
		,KybSQLiteHelper.COLUMN_CURRENT_STATE
	};

    // caching of beat tracker and player rhythms so this object receives the listening as a proxy
    // and can make sure whenever there's a change to the rhythm(s) they are kept up to date, regardless
    // of the listening of the beat tracker and or player/editor
    private ListenerSupport mListenerSupport;
    private RhythmSqlImpl mPlayerEditorRhythm;
    private BeatTrackerBinder mBeatTrackerBinder;
    private WeakReference<PlayRhythmFragment> mPlayRhythmFragment; // contains Android views so weak
    private int mLastListenerChangedId = -1;
    private ReentrantReadWriteLock mPlayerRhythmLock = new ReentrantReadWriteLock();

    @Override
	public boolean onCreate() {
		super.onCreate();
		mLibrary.setRhythms(this);
        mListenerSupport = mLibrary.getResourceManager().getListenerSupport();
        return false;
	}
	
	@Override
	public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
		
		// ignore other params for library - one row, just check the uri is correct
		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
        else if (uriType == URI_MATCH_SEARCH_LIST || uriType == URI_MATCH_SEARCH_LIST_INCL_NEW_RHYTHM) {
            // rhythms dialog_simple_list search, should have selection and args already, but will append to it if there are tags


        }
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to query with unknown URI: " + uri);
		}
		
		@SuppressWarnings("ArrayEquals")
        Cursor cursor = query( // if asking for the tags dialog_simple_list in the columns, use that view
                projection.equals(RHYTHMS_LIST_COLUMNS) ? KybSQLiteHelper.VIEW_RHYTHMS :
                        uriType == URI_MATCH_SEARCH_LIST_INCL_NEW_RHYTHM ? KybSQLiteHelper.VIEW_RHYTHMS_WITH_NEW_RHYTHM : KybSQLiteHelper.TABLE_RHYTHMS,
                projection, selection, selectionArgs, sortOrder);
		
		// make sure that potential listeners are getting notified
	    cursor.setNotificationUri(getContext().getContentResolver(), uri);
	    
	    return cursor;
	}
	
	@Override
	public Uri insert(Uri uri, ContentValues values) {

		int uriType = sURIMatcher.match(uri);

		if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to insert with unknown URI: " + uri);
		}
		
		// look for edit rhythm, to ensure it ISN'T accidentally written
		// to the rhythms table
		// TODO: remove this after testing
		String key = values.getAsString(KybSQLiteHelper.COLUMN_EXTERNAL_KEY);
		if (key == null || key.equals(EditRhythm.EDIT_RHYTHM_KEY)) {
			AndroidResourceManager.loge(LOG_TAG, "insert: cannot insert rhythm with null or editRhythm key = "+key);
			throw new IllegalArgumentException("cannot insert rhythm with null or editRhythm key = "+key);
		}
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		long id = db.insert(KybSQLiteHelper.TABLE_RHYTHMS, null, values);
		
		if (id == -1) {
			throw new KybDataException(String.format("insert(%s): error occurred, row not inserted values = %s", LOG_TAG, values));
		}
		
		getContext().getContentResolver().notifyChange(uri, null);
        if (!uri.equals(CONTENT_URI)) {
            getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        }
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
		
		// look for edit rhythm, to ensure it ISN'T accidentally written
		// to the rhythms table
		// TODO: remove this after testing
		String key = values.getAsString(KybSQLiteHelper.COLUMN_EXTERNAL_KEY);
		if (key != null && key.equals(EditRhythm.EDIT_RHYTHM_KEY)) {
			AndroidResourceManager.loge(LOG_TAG, "update: cannot update rhythm key to editRhythm key = "+key);
			throw new IllegalArgumentException("cannot update rhythm key to editRhythm key = "+key);
		} else if (selection != null && selection.contains(EditRhythm.EDIT_RHYTHM_KEY)){
			AndroidResourceManager.loge(LOG_TAG, "update: cannot update rhythm selecting on editRhythm key = ("+selection+")");
			throw new IllegalArgumentException("cannot update rhythm selecting on editRhythm key = ("+selection+")");
		} else if (selectionArgsContainsEditRhythmKey(selectionArgs)) {
			AndroidResourceManager.loge(LOG_TAG, "update: cannot update rhythm selecting on editRhythm key = (args="+selectionArgs+")");
			throw new IllegalArgumentException("cannot update rhythm selecting on editRhythm key = (args="+selectionArgs+")");
		}

		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		int rowsUpdated = db.update(KybSQLiteHelper.TABLE_RHYTHMS,
                values, selection, selectionArgs);

        getContext().getContentResolver().notifyChange(uri, null);
        if (!uri.equals(CONTENT_URI)) {
            getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        }
	    return rowsUpdated;
	}

	//TOOD remove after testing
	private boolean selectionArgsContainsEditRhythmKey(String[] selectionArgs) {
		if (selectionArgs == null || selectionArgs.length == 0) {
			return false;
		}
		
		for (String arg : selectionArgs) {
			if (arg != null && arg.contains(EditRhythm.EDIT_RHYTHM_KEY)) {
				return true;
			}
		}
		
		return false;
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
		
		// look for edit rhythm, to ensure it ISN'T accidentally written
		// to the rhythms table
		// TODO: remove this after testing
		if (selection != null && selection.contains(EditRhythm.EDIT_RHYTHM_KEY)){
			AndroidResourceManager.loge(LOG_TAG, "delete: cannot delete rhythm selecting on editRhythm key = ("+selection+")");
			throw new IllegalArgumentException("cannot delete rhythm selecting on editRhythm key = ("+selection+")");
		} else if (selectionArgsContainsEditRhythmKey(selectionArgs)) {
			AndroidResourceManager.loge(LOG_TAG, "delete: cannot delete rhythm selecting on editRhythm key = (args="+selectionArgs+")");
			throw new IllegalArgumentException("cannot delete rhythm selecting on editRhythm key = (args="+selectionArgs+")");
		}

		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		
		int rowsDeleted = db.delete(KybSQLiteHelper.TABLE_RHYTHMS, selection, selectionArgs);

		if (rowsDeleted == 0) {
			throw new KybDataException(String.format("delete(%s): error occurred, row not deleted selection = %s", LOG_TAG, selection));
		}

		getContext().getContentResolver().notifyChange(uri, null);
        if (!uri.equals(CONTENT_URI)) {
            getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        }
	    return rowsDeleted;
	}

	@Override
    public Rhythm lookup(String key) {
        return lookup(key, false);
    }

    /**
     * Override to query the view version of the cursor so the denormalized tags come back in the result.
     * Passing true to use that is only called from resolveRhythmKey() which is part of the reading of an
     * existing rhythm for the play rhythms activity, and from the onStart() method in RhythmPlayingFragmentHolder.
     * @param key
     * @param useView
     * @return
     */
    public Rhythm lookup(String key, boolean useView) {
        Cursor csr = query(useView ? KybSQLiteHelper.VIEW_RHYTHMS : KybSQLiteHelper.TABLE_RHYTHMS,
                useView ? RHYTHMS_LIST_COLUMNS : RHYTHM_COLUMNS,
                String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key), null, null);

        try {
            if (csr.getCount() == 0) {
                return null;
            }
            else {
                csr.moveToFirst();
                RhythmSqlImpl rhythm = new RhythmSqlImpl(getContext(), mLibrary, csr);
                return rhythm;
            }
        }
        finally {
            csr.close();
        }
    }

    public boolean nameExists(String name, String excludeKey) {
        String[] args = new String[] { name.toUpperCase(), excludeKey };

        Cursor csr = query(KybSQLiteHelper.TABLE_RHYTHMS, RHYTHM_COLUMNS,
                String.format("UPPER(%s) = ? AND %s != ?", KybSQLiteHelper.COLUMN_RHYTHM_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY), args, null);

        try {
            return csr.getCount() > 0;
        }
        finally {
            csr.close();
        }
    }

    public boolean existsInDb(String key) {
        Cursor csr = query(KybSQLiteHelper.TABLE_RHYTHMS, RHYTHM_COLUMNS,
                String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key), null, null);

        try {
            return (csr.getCount() != 0);
        }
        finally {
            csr.close();
        }
    }

    @Override
	public Rhythm makeNewRhythm(Function context) {
		return makeNewRhythm(context, mLibrary.getNextKey(), false); // don't insert immediately
	}

	/**
	 * Makes a rhythm object, it does not add it to the database
	 */
	@Override
	public Rhythm makeNewRhythm(Function context, String key, boolean insertNow) {
		RhythmSqlImpl rhythm = new RhythmSqlImpl(context, mLibrary, key);
		rhythm.setName(context, getContext().getResources().getString(R.string.NEW_RHYTHM_NAME));

        if (insertNow) {
            // temporary insert to allow decoding to continue, set encoded beats
            rhythm.setEncodedBeatTypes(context, "");
            addRhythm(context, rhythm);
        }

		return rhythm;
	}

    public RhythmSqlImpl makeNewRhythmAndSave(Function context, int typeOfBeat, boolean cacheData) {
        RhythmSqlImpl rhythm = (RhythmSqlImpl) makeNewRhythm(context);
        rhythm.getBeatsAllData(null, cacheData, typeOfBeat); // needed to create the beats string

        insertOrUpdateNewRhythm(rhythm);
        return rhythm;
    }

    /**
     * Called from PlayRhythmsActivity.onNewIntent() and PlayingRhythmFragmentHolder constructor/itemChanged()
     * to resolve a key which may not be found just with lookup() as it could be the 'new' rhythm. If it's not found
     * there either, it will be created as a new rhythm and passed back.
     * As this is always called to open a rhythm, if an existing rhythm is found via the lookup() call, the
     * current 'new rhythm' in the db is deleted so it will not be re-given in a future call for a new rhythm.
     *
     * @param rhythmKey
     * @return
     */
    public RhythmSqlImpl resolveRhythmKey(String rhythmKey, int typeOfBeat, boolean cacheData) {

        if (rhythmKey == null) {
            return makeNewRhythmAndSave(Function.getBlankContext(mLibrary.getResourceManager()), typeOfBeat, cacheData);
        }
        else {
            RhythmSqlImpl rhythm = (RhythmSqlImpl) lookup(rhythmKey, true);

            if (rhythm == null) {

                rhythm = getExistingNewRhythm(); // try for the last used new rhythm

                if (rhythm == null) {            // finally just create a new one
                    return makeNewRhythmAndSave(Function.getBlankContext(mLibrary.getResourceManager()), typeOfBeat, cacheData);
                }
            }
            else {
                // an existing rhythm is found, remove the 'new' one
                removeNewRhythm();
            }

            return rhythm;
        }
    }

    private void removeNewRhythm() {
        SQLiteDatabase db = mLibrary.getWritableDatabase(true);
        String selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_NEW_RHYTHM);
        db.delete(KybSQLiteHelper.TABLE_MEMENTOS, selection, null);
    }

    /**
     * Created in makeNewRhythmAndSave() after RhythmPlayingFragmentHolder constructor when there's no rhythm and a new one is created
     * and also from the new rhythm menu item, and then updated whenever the rhythm goes through an update but is not a saved
     * rhythm so that changes are kept for next time the user starts the app while still having a new rhythm
     * @param rhythm
     */
    void insertOrUpdateNewRhythm(RhythmSqlImpl rhythm) {

        try {
            String encodedRhythmString = RhythmEncoder.getLightWeightRhythmEncoder(
                    Function.getBlankContext(rhythm.getLibrary().getResourceManager()), rhythm.getLibrary())
                    .codifyRhythm(rhythm);

            SQLiteDatabase db = mLibrary.getWritableDatabase(true);
            ContentValues values = new ContentValues(2);
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_NEW_RHYTHM);
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, encodedRhythmString);
            String selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_NEW_RHYTHM);
            int rowsUpdated = db.update(KybSQLiteHelper.TABLE_MEMENTOS, values, selection, null);

            if (rowsUpdated == 0) { // doesn't already exist, so insert new
                db.insert(KybSQLiteHelper.TABLE_MEMENTOS, null, values);
//                AndroidResourceManager.logw(LOG_TAG, "insertOrUpdateNewRhythm: inserted new row: " + encodedRhythmString);
            }
            else {
//                AndroidResourceManager.logw(LOG_TAG, "insertOrUpdateNewRhythm: updated existing row: " + encodedRhythmString);
//                AndroidGuiManager.logTrace("updated new rhythm from where?", LOG_TAG);
            }
        } catch (UnrecognizedFormatException e) {
            // bug error, failed somewhere in the middle, can't save now, so have to abort
            AndroidResourceManager.loge(LOG_TAG, "insertOrUpdateNewRhythm: format error in the encoding string, this should never happen!");
        } catch (UnsupportedEncodingException e) {
        }
    }

    /**
     * Called from play rhythms activity when the app starts up and has not found the rhythm last used via lookup (meaning it's not a saved rhythm)
     * @return
     */
    @Override
    public RhythmSqlImpl getExistingNewRhythm() {

        Cursor csr = query(KybSQLiteHelper.TABLE_MEMENTOS, MEMENTO_COLUMNS,
                String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_NEW_RHYTHM), null, null);

        try {
            if (csr.getCount() == 0) {
                return null;
            }
            else {
                SimpleEntry<Rhythm, BeatTree> decodedRhythm;
                csr.moveToFirst();
                String encodedRhythm = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1));
                csr.close(); // probably not needed

                AndroidResourceManager arm = (AndroidResourceManager) mLibrary.getResourceManager();
                boolean isNewTrans = arm.startTransactionUnlessAlreadyInOne();

                try {

                    decodedRhythm = RhythmEncoder.getLightWeightRhythmEncoder(Function.getBlankContext(mLibrary.getResourceManager()), mLibrary)
                            .decodeRhythm(encodedRhythm, null, true);

                    RhythmSqlImpl rhythm = (RhythmSqlImpl) decodedRhythm.getKey();

                    return rhythm;

                }
                catch (UnrecognizedFormatException e) {
                    AndroidResourceManager.loge(LOG_TAG, "getExistingNewRhythm: format error in the encoding string, this should never happen!", e);
                }
                catch (UnsupportedEncodingException e) {
                    AndroidResourceManager.loge(LOG_TAG, "getExistingNewRhythm: encoding error, this should never happen!", e);
                }
                finally {
                    if (isNewTrans) {
                        arm.saveTransaction();
                    }
                }
                return null;
            }
        }
        finally {
            if (!csr.isClosed())
                csr.close();
        }
    }


    /**
	 * Xml impl simply removes the rhythm from the dialog_simple_list, here the rhythm and all
	 * dependent data have to be deleted from the database.
	 */
	@Override
	public void removeRhythm(Function context, Rhythm rhythm) {
		delete(CONTENT_URI, 
				String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, rhythm.getKey())
				, null);
	}

	@Override
	public void reinstateRhythm(Function context, ClonedRhythmMemento rhythmMemento)
			throws UnrecognizedFormatException {

		SimpleEntry<Rhythm, BeatTree> decodedRhythm;
		try {
			decodedRhythm = RhythmEncoder.getLightWeightRhythmEncoder(context, mLibrary).decodeRhythm(rhythmMemento.getEncodedRhythmString());
            ((RhythmSqlImpl)decodedRhythm.getKey()).updateDb(true); // the add rhythm during decode has already inserted it but with incomplete values

		} catch (UnrecognizedFormatException e) {
			AndroidResourceManager.loge(LOG_TAG, "reinstateRhythm: format error in the encoding string, this should never happen!", e);
			// hope can't happen either, since took the memento just before...
			// throw it all the way up to the undo command and deal with it there
			throw e;
		} catch (UnsupportedEncodingException e) {
			AndroidResourceManager.loge(LOG_TAG, "reinstateRhythm: encoding error, this should never happen!", e);
		}
	}

	@Override
	public Rhythm cloneRhythm(Function context, Rhythm rhythm)
			throws UnrecognizedFormatException {

		Rhythm copyRhythm = makeExactCopy(context, rhythm, mLibrary.getNextKey());
//		addRhythm(context, copyRhythm);
		
		return copyRhythm;
	}
	
	/**
	 * Called from both copyImportRhythm() and cloneRhythm() which do the same except with the key
	 * @param context
	 * @param rhythm
	 * @param useKey
	 * @return
	 * @throws UnrecognizedFormatException
	 */
	private RhythmSqlImpl makeExactCopy(Function context, Rhythm rhythm, String useKey) throws UnrecognizedFormatException {
		RhythmSqlImpl copyRhythm = new RhythmSqlImpl(context, mLibrary, useKey, true); // default null strings
		
		// replaceFromRhythm() is going to restore from cloned memento which could make db changes such as
		// saving to rhythm_tags table, so the rhythm must first be inserted
		addRhythm(context, copyRhythm);

		copyRhythm.replaceFromRhythm(context, rhythm);
		copyRhythm.setName(context, generateUniqueName(rhythm.getName(), MAX_RHYTHM_NAME_LEN, copyRhythm.getInternalId()));
		
		// and finally re-update the db (creates content values)
		copyRhythm.updateDb(false);
		
		return copyRhythm;
	}

	@Override
	public void addRhythm(Function context, Rhythm rhythm) {
		if (rhythm instanceof EditRhythm) {
			// don't add it
		}
		else {
			ContentValues values = new ContentValues();
			values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, rhythm.getKey());
			values.put(KybSQLiteHelper.COLUMN_RHYTHM_NAME, rhythm.getName());
			values.put(KybSQLiteHelper.COLUMN_FULL_BEAT_1, rhythm.getFullBeatNumber1());
			values.put(KybSQLiteHelper.COLUMN_BPM, rhythm.getBpm());
			values.put(KybSQLiteHelper.COLUMN_ENCODED_BEATS, rhythm.getEncodedBeatTypes());

			// insert using the uri to ensure clients get the update notified
			Uri insertUri = insert(CONTENT_URI, values);
			long id = Long.parseLong(insertUri.getLastPathSegment());
			
			if (id != -1) {
				((RhythmSqlImpl)rhythm).setInternalId(id);
			}
		}
	}

	@Override
	public String generateUniqueName(String name, int maxRhythmNameLen) {
		return generateUniqueName(name, maxRhythmNameLen, -1L);
	}

	/**
	 * Overloaded to allow ignore of a particular rhythm id which is probably the
	 * one being updated with a new name (see makeExactCopy())
	 * @param name
	 * @param maxRhythmNameLen
	 * @param ignoreId
	 * @return
	 */
	public String generateUniqueName(String name, int maxRhythmNameLen, long ignoreId) {
		// one query is enough, chop the part that will distinguish and do a like comparison
		// max beat type name, less 4 for "(n)", PLUS 3 more if it's too long (for "...")
		int SUFFIX_LEN = 4;
		int ELLIPSES_LEN = 3; //"..."
		String likeCompare = name.length() + SUFFIX_LEN > maxRhythmNameLen
				? name.substring(0, BeatType.MAX_BEAT_TYPE_NAME_LEN -SUFFIX_LEN -ELLIPSES_LEN)
				: name;

		Cursor csr = query(KybSQLiteHelper.TABLE_RHYTHMS, new String[]{KybSQLiteHelper.COLUMN_RHYTHM_NAME},
                String.format("%s like '%s%s' and %s != %s"
                        , KybSQLiteHelper.COLUMN_RHYTHM_NAME, likeCompare, "%", KybSQLiteHelper.COLUMN_ID, ignoreId)
                , null, KybSQLiteHelper.COLUMN_RHYTHM_NAME); // order by name

		try {
			// no rows with similar name
			if (csr.getCount() == 0) {
				return name;
			}

			for (int i = 2; true; i++) {
				String suffix = String.format("(%s)", i);
				String proposedName = String.format("%s%s", name, suffix); 
				if (proposedName.length() > maxRhythmNameLen) {
					proposedName = String.format("%s%s%s"
							, name.substring(0, maxRhythmNameLen -suffix.length() -ELLIPSES_LEN)
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
	public Rhythm copyImportRhythm(Function context, RhythmXmlImpl rhythm)
			throws UnrecognizedFormatException {
		Rhythm copyRhythm = makeExactCopy(context, rhythm, rhythm.getKey());
		
		return copyRhythm;
	}

	@Override
	public EditRhythm makeEditRhythm(Function context, String string) {
		String msg = "makeEditRhythm: RhythmEncoder is somehow calling makeEditRhythm - not allowed";
		AndroidResourceManager.loge(LOG_TAG, msg);
		throw new UnsupportedOperationException(msg);
	}

	@Override
	public void removeByKey(Function context, HashSet<String> keys) {

		StringBuilder sb = makeExternalKeyInClauseFromSet(keys);
		if (sb != null) {
			delete(CONTENT_URI, sb.toString(), null);
		}		
	}

	//----------------------- CRUD Edit rhythm

    /**
     * Either the player rhythm (new or existing) or a rhythm from the list has been chosen
     * to edit. It's passed in as the source.
     * There should not be an existing edit rhythm at this point. (if there was the user would have
     * been taken to the edit rhythm activity directly)
     * @param sourceRhythm
     * @return
     */
    public EditRhythmSqlImpl makeEditFromSourceRhythm(RhythmSqlImpl sourceRhythm) throws UnrecognizedFormatException, RhythmReadLockNotHeldException {

        if (getOpenEditRhythm() != null) { // will test has read lock
            String msg = "insertEditRhythmToDb: attempt to insert edit rhythm when one already exists";
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new IllegalAccessError(msg );
        }

        EditRhythmSqlImpl editRhythm = new EditRhythmSqlImpl(Function.getBlankContext(mLibrary.getResourceManager()), sourceRhythm);
        SQLiteDatabase db = mLibrary.getWritableDatabase(false);
        ContentValues values = getEditRhythmValues(editRhythm);
        long id = db.insert(KybSQLiteHelper.TABLE_EDIT_RHYTHM, null, values);

        if (id == -1) {
            throw new KybDataException(String.format("insertEditRhythmToDb(%s): error occurred, row not inserted values = %s", LOG_TAG, values));
        }

        return editRhythm;
    }

    /**
     * Get the current rhythm as edit rhythm, if none return null
     */
    public EditRhythmSqlImpl getOpenEditRhythm() throws RhythmReadLockNotHeldException {

        testHasReadLockOnPlayerEditorRhythm();

        if (mPlayerEditorRhythm != null && mPlayerEditorRhythm instanceof EditRhythmSqlImpl) {
            return (EditRhythmSqlImpl) getPlayerEditorRhythm();
        }
        else {
            return null;
        }
    }

//    /**
//     * Get the edit rhythm, if currently open return that or saved in the db, if none return null
//     */
//    public EditRhythmSqlImpl getEditRhythm() throws RhythmReadLockNotHeldException {
//        return getEditRhythm(false); // normally return cache if there is one
//    }

    /**
     * Get the edit rhythm saved in the db, if none return null
     * @param ignoreCached normally returns playerEditorRhythm if it's the right type, but when that needs to be re-queried itself ignore that
     */
    public EditRhythmSqlImpl getEditRhythm(boolean ignoreCached) throws RhythmWriteLockNotHeldException {

        testHasWriteLockOnPlayerEditorRhythm();

        if (!ignoreCached && mPlayerEditorRhythm != null && mPlayerEditorRhythm instanceof EditRhythmSqlImpl) {
            try {
                return (EditRhythmSqlImpl) getPlayerEditorRhythm();
            } catch (RhythmReadLockNotHeldException e) {
                // not possible from here, since it allows write lock instead and that's already been tested for on entry
            }
        }

        Cursor csr = getCursorForEditRhythm();

        try {
            if (csr.getCount() == 0) {
                return null;
            }

            csr.moveToFirst();
            return new EditRhythmSqlImpl(mLibrary, csr);
        }
        finally {
            csr.close();
        }
    }

    private Cursor getCursorForEditRhythm() {
        return query(KybSQLiteHelper.TABLE_EDIT_RHYTHM, EDIT_RHYTHM_COLUMNS, null, null, null);
    }

	protected ContentValues getEditRhythmValues(EditRhythmSqlImpl editRhythmSqlImpl) {
		ContentValues values = new ContentValues();
		values.put(KybSQLiteHelper.COLUMN_SOURCE_RHYTHM_FK, editRhythmSqlImpl.getSourceRhythm().getKey());
		values.put(KybSQLiteHelper.COLUMN_CURRENT_STATE, editRhythmSqlImpl.getCurrentState().name());
		values.put(KybSQLiteHelper.COLUMN_ENCODED_BEATS, editRhythmSqlImpl.getEncodedBeatTypes());
		values.put(KybSQLiteHelper.COLUMN_FULL_BEAT_1, editRhythmSqlImpl.getFullBeatNumber1());
		values.put(KybSQLiteHelper.COLUMN_BPM, editRhythmSqlImpl.getBpm());
		
		return values;
	}

	/**
	 * Called directly from editRhythm.updateDb() will be part of a transaction so no commit here.
	 * @param editRhythmSqlImpl
	 */
	public void updateEditRhythmToDb(EditRhythmSqlImpl editRhythmSqlImpl) {
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		db.update(KybSQLiteHelper.TABLE_EDIT_RHYTHM, getEditRhythmValues(editRhythmSqlImpl), null, null);
	}

	/**
	 * Called when closing the rhythm editor by way of save or cancel, it's not undo-able in itself
	 * but likely embedded in a command within a transaction 
	 */
	public void deleteEditRhythmToDb(boolean commit) {
		SQLiteDatabase db = mLibrary.getWritableDatabase(false);
		if (commit) {
			mLibrary.getResourceManager().startTransaction();
		}
		db.delete(KybSQLiteHelper.TABLE_EDIT_RHYTHM, null, null);	
		if (commit) {
			mLibrary.getResourceManager().saveTransaction();
		}
	}

    @Override
    public String getListenerKey() {
        return "rhythms";
    }

    /**
     * Called by BeatTypesListFragment when delete is picked on the menu to find
     * rhythms referenced by a beat type, and SoundsListFragment similarly for a sound
     * @param fk
     * @return
     */
    public int getReferencedRhythmsCount(String fk) {

        int count = 0;

        Cursor csr = query(RhythmsContentProvider.CONTENT_INCL_NEW_RHYTHM_URI,
                new String[] { KybSQLiteHelper.COLUMN_EXTERNAL_KEY, KybSQLiteHelper.COLUMN_RHYTHM_NAME },
                String.format("%s like '%s%s%s'", KybSQLiteHelper.COLUMN_ENCODED_BEATS, "%", fk, "%"),
                null, KybSQLiteHelper.COLUMN_RHYTHM_NAME);

        try {
            count = csr.getCount();
        } finally {
            csr.close();
        }

        return count;
    }

    /**
     * Called by BeatTypesListFragment when delete is picked on the menu to find
     * if the new rhythm is current and references a sound
     * @param fk
     * @return
     */
    public boolean isReferencedNewRhythm(String fk) {
        RhythmSqlImpl newRhythm = getExistingNewRhythm();
        return newRhythm != null && newRhythm.getEncodedBeatTypes().contains(fk);
    }

    public boolean getReadLockOnPlayerEditorRhythmOrWaitDefaultTime() {
        return getReadLockOnPlayerEditorRhythm(WAIT_FOR_PLAYER_EDITOR_RHYTHM_DEFAULT_MILLIS);
    }

    public boolean getReadLockOnPlayerEditorRhythmOrWaitMinimumTime() {
        return getReadLockOnPlayerEditorRhythm(1);
    }

    private boolean getReadLockOnPlayerEditorRhythm(long sleepTime) {
        boolean acquired = mPlayerRhythmLock.readLock().tryLock();
        if (!acquired && sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {}
        }
        return acquired;
    }

    public boolean getWriteLockOnPlayerEditorRhythm(long sleepTime) {
        releaseReadLockOnPlayerEditorRhythm(); // could be > 1 perhaps

        AndroidResourceManager.logd(LOG_TAG, String.format("getWriteLockOnPlayerEditorRhythm: released read lock, current thread has %s remaining read locks",
                mPlayerRhythmLock.getReadHoldCount()));

        boolean acquired = mPlayerRhythmLock.writeLock().tryLock();
        if (!acquired && sleepTime > 0) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {}
        }
        return acquired;
    }

    public void releaseReadLockOnPlayerEditorRhythm() {
        if (mPlayerRhythmLock.getReadHoldCount() > 0) {
            mPlayerRhythmLock.readLock().unlock();
        }
    }

    public void releaseWriteLockOnPlayerEditorRhythm() {
        if (mPlayerRhythmLock.isWriteLockedByCurrentThread()) {
            mPlayerRhythmLock.writeLock().unlock();
        }
    }

    private void testHasReadLockOnPlayerEditorRhythm() throws RhythmReadLockNotHeldException {
        if (mPlayerRhythmLock.getReadHoldCount() == 0) {
            throw new RhythmReadLockNotHeldException();
        }
    }

    private void testHasWriteLockOnPlayerEditorRhythm() throws RhythmWriteLockNotHeldException {
        if (!mPlayerRhythmLock.isWriteLockedByCurrentThread()) {
            throw new RhythmWriteLockNotHeldException();
        }
    }

    public RhythmSqlImpl getPlayerEditorRhythm() throws RhythmReadLockNotHeldException {
        try {
            testHasReadLockOnPlayerEditorRhythm();
        }
        catch (RhythmReadLockNotHeldException e) {
            if (!mPlayerRhythmLock.isWriteLockedByCurrentThread()) {        // permissible to have write lock instead ofc
                throw e;
            }
        }
        return mPlayerEditorRhythm;
    }

    /**
     * Not locked, so designed for fast access on UI thread... doesn't guarantee the rhythm will be available by the time
     * an attempt is made to use it though
     * @return
     */
    public boolean hasPlayerEditorRhythm() {
        return mPlayerEditorRhythm != null;
    }

    /**
     * The rhythm is held here as a cached object, this class listens for changes and
     * keeps it up-to-date until overwritten by another rhythm (only ever 1, so for faster activity loading
     * this class will apply changes from undos etc in the background
     * @param rhythm
     */
    public void setPlayerEditorRhythm(RhythmSqlImpl rhythm) throws RhythmReadLockNotHeldException {
        testHasReadLockOnPlayerEditorRhythm();

        boolean alreadyListeningToIt = rhythm != null && mPlayerEditorRhythm != null && mPlayerEditorRhythm.getKey().equals(rhythm.getKey());
        if (alreadyListeningToIt) {
            return;
        }

        try {
            while (!getWriteLockOnPlayerEditorRhythm(WAIT_FOR_PLAYER_EDITOR_RHYTHM_DEFAULT_MILLIS)) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "setPlayerEditorRhythm: waiting for write lock on player rhythm");
            }

            if (mPlayerEditorRhythm != null) {
                stopListening(mPlayerEditorRhythm);
            }

            mPlayerEditorRhythm = rhythm;

            if (mPlayerEditorRhythm != null) { // will be called with null when finished
                startListening(mPlayerEditorRhythm);
            }
            // downgrade by acquiring read lock
            getReadLockOnPlayerEditorRhythm(1); // as have write lock should be instant
        }
        finally {
            releaseWriteLockOnPlayerEditorRhythm();
        }
    }

    private void startListening(RhythmSqlImpl rhythm) {
        mListenerSupport.addListener(rhythm, this);
        mListenerSupport.addListener(mLibrary.getBeatTypes(), this);
        mListenerSupport.addListener(mLibrary.getSounds(), this);
        mListenerSupport.addListener(mLibrary.getTags(), this);
        mListenerSupport.addListener(this, this); // rhythms
    }

    public void registerBeatTracker(BeatTrackerBinder beatTrackerBinder) throws RhythmReadLockNotHeldException {
        testHasReadLockOnPlayerEditorRhythm();
        mBeatTrackerBinder = beatTrackerBinder;
    }

    public void deregisterBeatTracker() throws RhythmReadLockNotHeldException {
        testHasReadLockOnPlayerEditorRhythm();
        mBeatTrackerBinder = null;
    }

    public void registerRhythmGui(PlayRhythmFragment playRhythmFragment) throws RhythmReadLockNotHeldException {
        testHasReadLockOnPlayerEditorRhythm();
        mPlayRhythmFragment = new WeakReference<>(playRhythmFragment);
    }

    public void deregisterRhythmGui() throws RhythmReadLockNotHeldException {
        testHasReadLockOnPlayerEditorRhythm();
        mPlayRhythmFragment = null;
    }

    public int inspectRhythmGuiInstance() throws RhythmReadLockNotHeldException {
        testHasReadLockOnPlayerEditorRhythm();
        PlayRhythmFragment holder = mPlayRhythmFragment == null ? null : mPlayRhythmFragment.get();
        return holder == null ? -1 : holder.getFragmentInstance();
    }

    private void stopListening(RhythmSqlImpl rhythm) {
        mListenerSupport.removeListener(rhythm, this);

        if (mBeatTrackerBinder != null) {   // stop it playing by sending rhythm removed in changed
            mBeatTrackerBinder.itemChanged(-1, rhythm.getKey(), ListenerSupport.RHYTHM_REMOVED, null);
        }
    }
    /**
     * This class is acting as a proxy for changes to up to 2 rhythms, updating the data as needed and passing the changes on
     * @param changeId
     * @param key
     * @param natureOfChange
     * @param listenerTargets
     */
    @Override
    public void itemChanged(int changeId, String key, int natureOfChange, LibraryListenerTarget[] listenerTargets) {

        // filter duplicate calls

        boolean alreadySeenChangeId = mLastListenerChangedId == changeId;
        mLastListenerChangedId = changeId;

        final AndroidResourceManager resourceManager = (AndroidResourceManager) mLibrary.getResourceManager();

        if (alreadySeenChangeId || resourceManager.isLogging(PlatformResourceManager.LOG_TYPE.debug)) { // avoid the string build unless is debug build
            AndroidResourceManager.logd(LOG_TAG, String.format("itemChanged: id=%s, new=%s, UIthread=%s, key=%s, natureOfChange=%s",
                    changeId, !alreadySeenChangeId, resourceManager.getGuiManager().isUiThread(), key, mListenerSupport.debugNatureOfChange(natureOfChange)));
        }

        if (alreadySeenChangeId) {
            return; // reported in previous line
        }

        // update the rhythm if it's a match

        while (!getReadLockOnPlayerEditorRhythmOrWaitDefaultTime()) {
            AndroidResourceManager.logd(LOG_TAG, "itemChanged: waiting for lock on player rhythm");
        }

        try {
            if (mPlayerEditorRhythm == null) {
                return; // nothing to apply a change to
            }

            try {
                while (!getWriteLockOnPlayerEditorRhythm(WAIT_FOR_PLAYER_EDITOR_RHYTHM_DEFAULT_MILLIS)) { // wait the minimum for it
                    AndroidResourceManager.logd(LOG_TAG, "itemChanged: waiting for write lock on player rhythm");
                }

                final boolean isDirectChange = isDirectChangeToPlayerEditorRhythm(key, listenerTargets);

                if (isDirectChange) {
                    if (mListenerSupport.isRhythmRemoved(natureOfChange)) {
                        removeAndReplacePlayerEditorRhythm();
                    }
                    else {
                        processChangeForRhythm(mPlayerEditorRhythm, natureOfChange);
                    }

                    key = mPlayerEditorRhythm.getKey(); // it might not be because the previous test looks through all the targets, this way the order doesn't matter
                                                        // and the calls below to itemChanged() are only going to see this change once, so make sure they get the right data
                }
                else if (mListenerSupport.isRhythmRemoved(natureOfChange)) {            // not a direct change, but it's a removed rhythm, then no need to propagate
                    AndroidResourceManager.logd(LOG_TAG, "itemChanged: rhythm removed that isn't the playerEditorRhythm, nothing to do");
                    return;
                }
                else { // it's indirect, and could be anything other than the removal of another rhythm
                    requeryPlayerEditorRhythm(true);
                }

                // downgrade by acquiring read lock
                getReadLockOnPlayerEditorRhythm(1); // as have write lock there should be no delays

                // notify beat tracker and/or player/editor

                if (mBeatTrackerBinder != null) {
                    mBeatTrackerBinder.itemChanged(changeId, key, natureOfChange, listenerTargets);
                }

                PlayRhythmFragment playRhythmFragment = mPlayRhythmFragment == null ? null : mPlayRhythmFragment.get();

                if (playRhythmFragment == null) {
                    KybActivity activity = resourceManager.getLatestActivity();
                    playRhythmFragment = activity == null ? null : activity.getPlayRhythmFragment();
                    if (playRhythmFragment != null) {
                        AndroidResourceManager.logw(LOG_TAG,
                                String.format("itemChanged: no holder is listening, but found one (%s) sending change, if it's ready why isn't it listening?",
                                activity.getClass().getSimpleName()));
                    }
                }

                if (playRhythmFragment != null) {
                    playRhythmFragment.itemChanged(changeId, key, natureOfChange, listenerTargets);
                }
                else {
                    AndroidResourceManager.logd(LOG_TAG, "itemChanged: no holder is listening for this change");
                }

            } catch (Exception e) {
                AndroidResourceManager.loge(LOG_TAG, "itemChanged: unable to propagate change because of error", e);
            }
            finally {
                releaseWriteLockOnPlayerEditorRhythm();
                releaseReadLockOnPlayerEditorRhythm();
            }

        } finally {
            releaseReadLockOnPlayerEditorRhythm();
        }
    }

    /**
     * Either the key or any of the targets might match the player rhythm
     * @param key
     * @param listenerTargets
     * @return
     * @exception RhythmWriteLockNotHeldException does'nt need the write lock, but it's to do with where it's called... just to make sure a call to this method isn't moved and then unchecked
     */
    private boolean isDirectChangeToPlayerEditorRhythm(String key, LibraryListenerTarget[] listenerTargets) throws RhythmWriteLockNotHeldException {

        testHasWriteLockOnPlayerEditorRhythm();

        if (mPlayerEditorRhythm == null) {
            return false;
        }

        final String playerEditorKey = mPlayerEditorRhythm.getKey();
        if (playerEditorKey.equals(key)) {
            return true;
        }

        if (listenerTargets == null) {
            return false;
        }

        for (LibraryListenerTarget target : listenerTargets) {
            if (playerEditorKey.equals(target.getListenerKey())) {
                return true;
            }
        }

        return false;
    }

    private void removeAndReplacePlayerEditorRhythm() throws RhythmWriteLockNotHeldException {

        testHasWriteLockOnPlayerEditorRhythm();

        final boolean wasNewRhythm = !mPlayerEditorRhythm.existsInDb();

        stopListening(mPlayerEditorRhythm);
        mPlayerEditorRhythm = null;

        final AndroidResourceManager resourceManager = (AndroidResourceManager) mLibrary.getResourceManager();
        boolean startedTrans = resourceManager.startTransactionUnlessAlreadyInOne();

        try {
            final SettingsManager statesManager = (SettingsManager) resourceManager.getPersistentStatesManager();
            final PlayerState playerState = statesManager.getPlayerState();

            mPlayerEditorRhythm = getExistingNewRhythm(); // try for the last used new rhythm

            if (mPlayerEditorRhythm == null) {            // there isn't one, creating it also creates cache
                mPlayerEditorRhythm = makeNewRhythmAndSave(Function.getBlankContext(resourceManager), BeatTree.PLAYED, true);
            }
            else {                                        // otherwise as just read from db, needs cache created
                mPlayerEditorRhythm.getBeatsAllData(null, true, BeatTree.PLAYED); // needed to create the beats string
            }

            playerState.setRhythmKey(mPlayerEditorRhythm.getKey());
            startListening(mPlayerEditorRhythm);

            if (wasNewRhythm) { // replacing a new rhythm, assert undo commands can be undone
                resourceManager.getUndoableCommandController().assertConsistentStack();
            }
        } finally {
            if (startedTrans) { // don't use resource mgr saveToStore() because of notifications in there
                SQLiteDatabase db = mLibrary.getWritableDatabase();
                db.setTransactionSuccessful();
                db.endTransaction();
            }
        }
    }

    private void requeryPlayerEditorRhythm(boolean includeBeats) throws RhythmWriteLockNotHeldException, RhythmReadLockNotHeldException {

        testHasWriteLockOnPlayerEditorRhythm();

//        AndroidResourceManager.logd(LOG_TAG, String.format("requeryPlayerEditorRhythm: existing rhythm = %s, remove id = %s",
//                (mPlayerEditorRhythm != null ? mPlayerEditorRhythm.getKey() : "none"), prevIsObsolete));

        final AndroidResourceManager resourceManager = (AndroidResourceManager) mLibrary.getResourceManager();
        boolean startedTrans = resourceManager.startTransactionUnlessAlreadyInOne();

        try {
            if (mPlayerEditorRhythm instanceof EditRhythmSqlImpl) {
                RhythmSqlImpl rhythm = getEditRhythm(true);   // get from db regardless
                if (rhythm == null) {                       // don't ever expect this to happen
                    AndroidResourceManager.loge(LOG_TAG, "requeryPlayerEditorRhythm: bug as no edit rhythm in the db even though have one in editor");
                    mPlayerEditorRhythm = makeEditFromSourceRhythm((RhythmSqlImpl) ((EditRhythmSqlImpl)mPlayerEditorRhythm).getSourceRhythm());
                }
            }
            else {
                RhythmSqlImpl rhythm = (RhythmSqlImpl) lookup(mPlayerEditorRhythm.getKey(), true); // not safe to just check existsInDB() because it may have been inserted

                if (rhythm == null) {
                    rhythm = getExistingNewRhythm();
                }

                if (rhythm != null) {
                    mPlayerEditorRhythm.requeryFromRhythm(rhythm, includeBeats);
                } else {
                    AndroidResourceManager.logw(LOG_TAG, "requeryPlayerEditorRhythm: didn't find either existing rhythm or new one, nothing requeried");
                }
            }
        } catch (UnrecognizedFormatException e) { // not possible at this stage
        } finally {
            if (startedTrans) { // don't use resource mgr saveToStore() because of notifications in there
                SQLiteDatabase db = mLibrary.getWritableDatabase();
                db.setTransactionSuccessful();
                db.endTransaction();
            }
        }
    }

    private void processChangeForRhythm(RhythmSqlImpl rhythm, int natureOfChange) throws Exception {

        testHasWriteLockOnPlayerEditorRhythm();

        final boolean isRhythmEdit = mListenerSupport.isRhythmEdit(natureOfChange),
                isUndo = mListenerSupport.isUndo(natureOfChange);

        if (isRhythmEdit && !isUndo) {                                               // direct user change
            if (!mListenerSupport.isRhythmEditUiUpdatesBeatTree(natureOfChange)) {   // or the beat tree is up-to-date, arrange rhythm happens in the holder
                rhythm.getBeatsAllData().getValue().calcBeatsDeltaRanges();
            }
            updateRhythmBeatsCache(rhythm, false);                    // then update the alignment of the rbt
        }
        else if ((isRhythmEdit && isUndo)                                            // undo treat as structure change
                || mListenerSupport.isNonSoundVolumeRbtChange(natureOfChange)) {     // still needs to rebuild rbt
            updateRhythmBeatsCache(rhythm, true);
        }
        else if (mListenerSupport.isSoundChange(natureOfChange)
                || mListenerSupport.isVolumeChange(natureOfChange)) {
            updateRhythmBeatsCache(rhythm, false);
        }
        else if (mListenerSupport.isBpmChange(natureOfChange)
                || mListenerSupport.isRhythmEditNonStructural(natureOfChange)        // such as beat 1 change
                || mListenerSupport.isRhythmsChange(natureOfChange)
                ) {
            requeryPlayerEditorRhythm(false);
        }
        else if (mListenerSupport.isBeatTypesChange(natureOfChange)                  // beat type used in the rhythm could have been deleted
                || mListenerSupport.isNonSpecificChange(natureOfChange) ) {          // non-specific better be too
            requeryPlayerEditorRhythm(true);
        }
        else { // any other change requery
            AndroidResourceManager.logd(LOG_TAG, String.format("processChangeForRhythm: change went through untrapped name=%s, natureOfChange=%s", rhythm.getName(), mListenerSupport.debugNatureOfChange(natureOfChange)));
            requeryPlayerEditorRhythm(true);
        }
    }

    private void updateRhythmBeatsCache(RhythmSqlImpl rhythm, boolean structureChanged) throws Exception {

        if (structureChanged || !rhythm.hasBeatDataCache()) {
            final int typeOfBeat = rhythm instanceof EditRhythmSqlImpl ? BeatTree.EDIT : BeatTree.PLAYED;

            BeatFactory factory = rhythm.getBeatTree(typeOfBeat).getBeatFactory(); // use the same factory (ie. draughter)
            rhythm.clearCache();
            AbstractMap.SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> cache = rhythm.getBeatsAllData(factory, true, typeOfBeat);
        }
        else {
            rhythm.realignRhythmBeatTypes();
        }
    }

}
