package com.stillwindsoftware.keepyabeat.db;

import java.util.ArrayList;
import java.util.HashSet;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper.KybDataException;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.Tags;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.model.transactions.Transaction;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;

/**
 * Content provider to access the Beat Types database table
 */
public class TagsContentProvider extends KybContentProvider implements Tags {

	private static final String LOG_TAG = "KYB-"+TagsContentProvider.class.getSimpleName();
	
	// constants for content providers
	private static final String AUTHORITY = "com.stillwindsoftware.keepyabeat.tags.contentprovider"; // matches manifest
	private static final String BASE_PATH = "tags";
	private static final String SINGLE_ROW_CONTENT_STR = "content://" + AUTHORITY + "/" + BASE_PATH + "/%s";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);

	// uri matching
	private static final int URI_MATCH_SINGLE_ROW = 1;
	private static final int URI_MATCH_BASE = 10;
	private final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	{
		sURIMatcher.addURI(AUTHORITY, BASE_PATH, URI_MATCH_BASE);
		sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", URI_MATCH_SINGLE_ROW);
	}

	public static final String[] TAG_COLUMNS = new String[] {
		KybSQLiteHelper.COLUMN_ID
		,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
		,KybSQLiteHelper.COLUMN_TAG_NAME
	};

	private static final String[] RHYTHM_TAG_COLUMNS = new String[] {
		KybSQLiteHelper.COLUMN_ID
		,KybSQLiteHelper.COLUMN_RHYTHM_FK
		,KybSQLiteHelper.COLUMN_TAG_FK
	};

	@Override
	public boolean onCreate() {
		super.onCreate();
		mLibrary.setTags(this);
		return false;
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		
		int uriType = sURIMatcher.match(uri);
		if (uriType == URI_MATCH_SINGLE_ROW) {
			selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, uri.getLastPathSegment());
		}
		else if (uriType != URI_MATCH_BASE) {
			// all rows will just pass through the selection (in case there is one)
			throw new IllegalArgumentException("Attempt to query with unknown URI: " + uri);
		}
		
		Cursor cursor = query(KybSQLiteHelper.TABLE_TAGS, projection, selection, selectionArgs, KybSQLiteHelper.COLUMN_TAG_NAME);
		
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
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		long id = db.insert(KybSQLiteHelper.TABLE_TAGS, null, values);
		
		if (id == -1) {
			AndroidResourceManager.loge(LOG_TAG, String.format("insert: error occurred, row not inserted values = %s", values));
		}
		
		getContext().getContentResolver().notifyChange(uri, null);
        getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
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
		int rowsUpdated = db.update(KybSQLiteHelper.TABLE_TAGS,
                values, selection, selectionArgs);
		
		getContext().getContentResolver().notifyChange(uri, null);
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
		
		// delete the tag(s) and on cascade should take care of rhythm tags
		int rowsDeleted = db.delete(KybSQLiteHelper.TABLE_TAGS, selection, selectionArgs);

		if (rowsDeleted == 0) {
			throw new KybDataException(String.format("delete(%s): error occurred, row not deleted selection = %s", LOG_TAG, selection));
		}

		getContext().getContentResolver().notifyChange(uri, null);
	    return rowsDeleted;
	}

	@Override
	public Tag lookup(String key) {

        AbstractSqlImpl cached = mLibrary.getFromCache(key);
        if (cached != null) {
            try {
                return (Tag) cached;
            }
            catch (ClassCastException e) {
                throw new KybDataException(String.format("lookup(%s): error occurred, row row in cache is wrong type = %s (type=)", LOG_TAG, key, cached.getClass().getName()));
            }
        }

        Cursor csr = query(KybSQLiteHelper.TABLE_TAGS, TAG_COLUMNS,
				String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key), null, null);
		
		try {
			if (csr.getCount() == 0) {
				return null;
			}
			else {
				csr.moveToFirst();
				TagSqlImpl tag = new TagSqlImpl(getContext(), mLibrary, csr);
                mLibrary.putIntoCache(key, tag);
				return tag;
			}
		} 
		finally {
			csr.close();
		}
	}

	/**
	 * Ensure the tag exists and the rhythm is not already on it.
	 * Should be called inside a transaction, not committed here.
	 * @param tag
	 * @param rhythm
	 */
	public void addRhythmToTag(TagSqlImpl tag, RhythmSqlImpl rhythm) {
		if (tag.getInternalId() == Library.INT_NOT_CHANGED || rhythm.getInternalId() == Library.INT_NOT_CHANGED) {
			String msg = String.format("addRhythmToTag: must have inserted both tag(%s) and rhythm(%s) before adding rhythm to it", tag.getInternalId(), rhythm.getInternalId());
			AndroidResourceManager.loge(LOG_TAG, msg);
			throw new IllegalStateException(msg);
		}
		
		Cursor csr = query(KybSQLiteHelper.TABLE_RHYTHM_TAGS, RHYTHM_TAG_COLUMNS, 
				String.format("%s = '%s' and %s = '%s'"
						, KybSQLiteHelper.COLUMN_TAG_FK, tag.getKey()
						, KybSQLiteHelper.COLUMN_RHYTHM_FK, rhythm.getKey()), null, null);
		
		try {
			// there should not be an existing row
			if (csr.getCount() > 0) {
				String msg = "addRhythmToTag: rhythm already has this tag";
				AndroidResourceManager.loge(LOG_TAG, msg);
			}
			
			// go ahead and add it
			else {
				ContentValues values = new ContentValues();
				values.put(KybSQLiteHelper.COLUMN_TAG_FK, tag.getKey());
				values.put(KybSQLiteHelper.COLUMN_RHYTHM_FK, rhythm.getKey());
				SQLiteDatabase db = mLibrary.getWritableDatabase(true);
				long id = db.insert(KybSQLiteHelper.TABLE_RHYTHM_TAGS, null, values);

				if (id == -1) {
					AndroidResourceManager.loge(LOG_TAG, String.format("addRhythmToTag: error occurred, row not inserted values = %s", values));
				}
                else {
                    getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
                }
			}
			
		}
		finally {
			csr.close();
		}
	}

	@Override
	public ArrayList<Tag> getRhythmTags(Rhythm rhythm) {

		String selection = String.format("%s in (select %s from %s where %s = '%s')",
				KybSQLiteHelper.COLUMN_EXTERNAL_KEY, KybSQLiteHelper.COLUMN_TAG_FK,
				KybSQLiteHelper.TABLE_RHYTHM_TAGS, KybSQLiteHelper.COLUMN_RHYTHM_FK,
				rhythm.getKey());
		Cursor csr = query(KybSQLiteHelper.TABLE_TAGS, TAG_COLUMNS, selection, null, KybSQLiteHelper.COLUMN_TAG_NAME);

		ArrayList<Tag> tags = new ArrayList<Tag>(csr.getCount());

		if (csr.getCount() > 0) {
			csr.moveToFirst();
			
			do {
                TagSqlImpl tag = new TagSqlImpl(getContext(), mLibrary, csr);
				tags.add(tag);
                mLibrary.putIntoCache(tag.getKey(), tag);
			}
			while (csr.moveToNext());
		}
		
		csr.close();
		
		return tags;
	}

    public boolean nameExists(String name, String excludeKey) {
        String[] args = new String[] { name.toUpperCase(), excludeKey };

        Cursor csr = query(KybSQLiteHelper.TABLE_TAGS, TAG_COLUMNS,
            String.format("UPPER(%s) = ? AND %s != ?", KybSQLiteHelper.COLUMN_TAG_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY), args, null);

        try {
            return csr.getCount() > 0;
        }
        finally {
            csr.close();
        }
    }

    @Override
	public Tag addTag(Function context, String key, String name) {

        Tag chkTag = lookup(key);
        if (chkTag != null) {
            AndroidResourceManager.loge(LOG_TAG, String.format("addTag: abort attempt to add tag that already exists (key=%s, name=%s)", key, name));
            return chkTag;
        }

        ContentValues contentValues = new ContentValues();
		contentValues.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key);
		contentValues.put(KybSQLiteHelper.COLUMN_TAG_NAME, name);

		// insert using the uri to ensure clients get the update notified
		// at least dialog_simple_list ones should
		insert(CONTENT_URI, contentValues);
		
		Tag tag = lookup(key);
        return tag;
	}

	@Override
	public void changeTag(Function context, Tag tag, String name) {

        // the tag will have been looked up and will now be in the cache, for the next lookup to be consistent
        // the name of that tag must also be updated
        tag.setName(name);

		ContentValues values = new ContentValues();
		values.put(KybSQLiteHelper.COLUMN_TAG_NAME, name);

		// update only if it's a db set id
		long id = ((TagSqlImpl)tag).getInternalId();
		Uri updateUri = Uri.parse(String.format(SINGLE_ROW_CONTENT_STR, id));
		int num = update(updateUri, values, null, null);
		
		if (num != 1) {
			AndroidResourceManager.loge(LOG_TAG, String.format("changeTag: single update updated %s rows", num));
		}
	}

	@Override
	public void reinstateDeletedTag(Function context, Tag tag, String[] rhythmFks) {

		TagSqlImpl t = (TagSqlImpl) addTag(context, tag.getKey(), tag.getName());
		((TagSqlImpl)tag).setInternalId(t.getInternalId());

        // add back deleted rhythms (one at a time to trap any errors... which has happened in kitkat)
        if (rhythmFks != null && rhythmFks.length > 0) {

            try {
                String[] existingFks = getRhythmKeysForDeleteMemento(t); // shouldn't be any, but being defensive, see prev comment
                ArrayList<String> insertFks = new ArrayList<>();
                for (String proposeAddRhythmFk : rhythmFks) {
                    boolean dupe = false;
                    for (String existFk : existingFks) {
                        if (existFk.equals(proposeAddRhythmFk)) {
                            AndroidResourceManager.loge(LOG_TAG, String.format("reinstateDeletedTag: tag_fk, rhythm_fk already exist (key=%s, rhythm_key=%s)", t.getKey(), existFk));
                            dupe = true;
                        }
                    }

                    if (!dupe) {
                        insertFks.add(proposeAddRhythmFk);
                    }
                }

                if (insertFks.size() == 0) { // all were already present, return and allow finally to do the notify
                    return;
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < insertFks.size(); i++) {
                    if (i > 0) {
                        sb.append(",");
                    }
                    sb.append("'");
                    sb.append(insertFks.get(i));
                    sb.append("'");
                }

                SQLiteDatabase db = mLibrary.getWritableDatabase(true);

                final String insertRhythmTags =
                        "insert into " + KybSQLiteHelper.TABLE_RHYTHM_TAGS
                                + " (" + KybSQLiteHelper.COLUMN_RHYTHM_FK
                                + "," + KybSQLiteHelper.COLUMN_TAG_FK
                                + ") select " + KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                                + ", '" + tag.getKey() + "' from "
                                + KybSQLiteHelper.TABLE_RHYTHMS
                                + " where " + KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                                + " in (" + sb.toString() + ");";

                // not currently testing the correct number of rhythms are added back here
                // instead that's part of the system tests
                db.execSQL(insertRhythmTags);
            }
            finally {
                getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
            }
        }
	}

	@Override
	public void removeTag(Function context, Tag tag) {
        String key = tag.getKey();
        delete(CONTENT_URI,
				String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key)
				, null);

        mLibrary.removeFromCache(key);

        // make sure the tag is not in the settings anymore
        PlatformResourceManager resourceManager = mLibrary.getResourceManager();
        SettingsManager settingsManager = (SettingsManager) resourceManager.getPersistentStatesManager();
        HashSet<String> rhythmSearchTagKeys = settingsManager.getRhythmSearchTagKeysAsSet();
        if (rhythmSearchTagKeys != null && rhythmSearchTagKeys.contains(key)) {
            AndroidResourceManager.logd(LOG_TAG, "removeTag: is in search, remove it = "+tag.getName());
            rhythmSearchTagKeys.remove(key);
            settingsManager.setRhythmSearchTagKeys(rhythmSearchTagKeys);
            Transaction.endTransactionNoSave(resourceManager, ListenerSupport.NON_SPECIFIC, settingsManager);
        }
        else {
            // removeTag is the only place normally a tag will be deleted, so notify rhythms here instead
            // of delete (that way the search keys update happens first)
            // but because the above call to the listeners will take care of notification, no need to
            // do it here again)
            getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
        }
	}

	@Override
	public void removeRhythmTags(Function context, Rhythm rhythm) {
		
		SQLiteDatabase db = mLibrary.getWritableDatabase(true);
		db.delete(KybSQLiteHelper.TABLE_RHYTHM_TAGS,
                String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_RHYTHM_FK, rhythm.getKey()), null);
	}

	@Override
	public void synchRhythmTags(Function context, Rhythm rhythm, ArrayList<Tag> activeTags) {

        // called by RhythmFunction.SaveRhythm which calls setName() on the rhythm but doesn't save the changes
        //TODO possibly rework how a name is updated in rhythm, or just do the update here

        if (((RhythmSqlImpl)rhythm).existsInDb()) {
            ((RhythmSqlImpl)rhythm).updateDb(false);
        }

		// read tags for the rhythm
		Cursor csr = query(KybSQLiteHelper.TABLE_RHYTHM_TAGS, RHYTHM_TAG_COLUMNS
				, String.format(COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_RHYTHM_FK, rhythm.getKey())
				, null, null);
		
		// add any keys in the cursor that are not in the dialog_simple_list, they'll be deleted at the end
		HashSet<String> removeKeys = new HashSet<String>();
		
		// look for keys in the db
		if (csr.getCount() > 0) {
			csr.moveToFirst();
			
			do {
				String inDbKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_TAG_FK));
				boolean found = false;
				
				// when find a tag in the db for the one in the dialog_simple_list, can bypass it...
				// as it's already synched, so read the arraylist backwards
				for (int i = activeTags.size() - 1; i >= 0; i--) {
					Tag tag = activeTags.get(i);
					if (inDbKey.equals(tag.getKey())) {
						found = true;
						activeTags.remove(i);
					}
				}
				
				// didn't find the db key in the dialog_simple_list, add to remove dialog_simple_list
				if (!found) {
					removeKeys.add(inDbKey);
				}
			}
			while (csr.moveToNext());
		}
		
		csr.close();

		// remove keys not in the db
        if (!removeKeys.isEmpty()) {
            StringBuilder sb = makeInClauseFromSet(KybSQLiteHelper.COLUMN_TAG_FK, removeKeys);

            sb.append(" AND ");
            sb.append(KybSQLiteHelper.COLUMN_RHYTHM_FK);
            sb.append(" = '");
            sb.append(rhythm.getKey());
            sb.append("'");

            String selection = sb.toString();

            SQLiteDatabase db = mLibrary.getWritableDatabase(true);
            int rowsDeleted = db.delete(KybSQLiteHelper.TABLE_RHYTHM_TAGS, selection, null);

            if (rowsDeleted != removeKeys.size()) {
                AndroidResourceManager.loge(LOG_TAG, String.format("synchRhythmTags: descrepancy between rows to remove and the number actually deleted", removeKeys.size(), rowsDeleted));
            }
        }
		
		// any tags left in the dialog_simple_list are new ones to be added
		for (Tag tag : activeTags) {
			addRhythmToTag((TagSqlImpl)tag, (RhythmSqlImpl)rhythm);
		}

        // notify
        getContext().getContentResolver().notifyChange(CONTENT_URI, null);
        getContext().getContentResolver().notifyChange(RhythmsContentProvider.CONTENT_URI, null);
	}

	@Override
	public Tag getTagByName(String name) {
		Cursor csr = query(KybSQLiteHelper.TABLE_TAGS, TAG_COLUMNS, 
				String.format("upper(%s) = upper('%s')", KybSQLiteHelper.COLUMN_TAG_NAME, name), null, null);
		
		try {
			if (csr.getCount() == 0) {
				return null;
			}
			else {
				csr.moveToFirst();
				TagSqlImpl tag = new TagSqlImpl(getContext(), mLibrary, csr);

                // check if it exists in the cache
                AbstractSqlImpl cached = mLibrary.getFromCache(tag.getKey());
                if (cached != null) {
                    try {
                        return (Tag) cached;
                    }
                    catch (ClassCastException e) {
                        throw new KybDataException(String.format("getTagByName(%s): error occurred, row in cache is wrong type = %s (type=)", LOG_TAG, tag.getKey(), cached.getClass().getName()));
                    }
                }

                // not in cache, so add it
                mLibrary.putIntoCache(tag.getKey(), tag);
				return tag;
			}
		} 
		finally {
			csr.close();
		}
	}

	@Override
	public Tag getOrCreateTag(Function context, String key, String name) {
		
		TagSqlImpl tag = (TagSqlImpl) lookup(key);
		
		if (tag == null) {
			tag = (TagSqlImpl) addTag(context, key, name);
		}
		
		return tag;
	}

	@Override
	public void removeByKey(Function context, HashSet<String> keys) {

		StringBuilder sb = makeExternalKeyInClauseFromSet(keys);
		if (sb != null) {
            String inClause = sb.toString();
            delete(CONTENT_URI, inClause, null);
		}

        for (String key : keys) {
            mLibrary.removeFromCache(key);
        }
	}

    @Override
    public String[] getRhythmKeysForDeleteMemento(Tag tag) {

        String selection = String.format(COLUMN_MATCH_STRING_FORMAT,
                KybSQLiteHelper.COLUMN_TAG_FK, tag.getKey());
        Cursor csr = query(KybSQLiteHelper.TABLE_RHYTHM_TAGS, RHYTHM_TAG_COLUMNS, selection, null, null);

        String[] keys = new String[csr.getCount()];

        if (csr.getCount() > 0) {
            csr.moveToFirst();

            do {
                keys[csr.getPosition()] = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_RHYTHM_FK));
            }
            while (csr.moveToNext());
        }

        csr.close();

        return keys;
    }

    @Override
    public String getListenerKey() {
        return "tags";
    }
}
