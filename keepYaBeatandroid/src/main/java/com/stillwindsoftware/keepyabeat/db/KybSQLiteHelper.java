package com.stillwindsoftware.keepyabeat.db;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.DataLoaderFragment.DataInstaller;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.BeatTypes.DefaultBeatTypes;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.Sounds.InternalSound;
import com.stillwindsoftware.keepyabeat.model.Tags;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;

/**
 * Helper for keep ya beat library database. It is the library for kyb too.
 */
public class KybSQLiteHelper extends SQLiteOpenHelper implements Library {

	private static final String LOG_TAG = "KYB-"+KybSQLiteHelper.class.getSimpleName();

    /**
	 * A runtime exception to be caught at the top level (command, undo, dataloading)
	 * when a data inconsistency error is found in the processing of database 
	 * operations (eg. insert returns -1 for an error)
	 */
	public static class KybDataException extends RuntimeException {

		public KybDataException(String detailMessage, Throwable throwable) {
			super(detailMessage, throwable);
			AndroidResourceManager.loge(LOG_TAG, String.format("KybDataException thrown msg=%s", detailMessage), throwable);
		}

		public KybDataException(String detailMessage) {
			super(detailMessage);
			AndroidResourceManager.loge(LOG_TAG, String.format("KybDataException thrown msg=%s", detailMessage));
		}
		
	}
	
	// when a new version of the database is installed, make sure to 
	// inc the versions here... so that onUpgrade() is called
    private static String LIBRARY_VERSION = "1.0.0";
	private static final int DATABASE_VERSION = 1;

	// files
	private static final String DATABASE_NAME = "library.db";

	// portable key constructions (every row has a unique key on top of the internal primary _ID)
	static final String KEY_SPLITTER = "-";
	// arbitrary start key offset to keep keys smaller
	static final long FRESH_STARTER = 1314838857767L;

	// general column constants
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_EXTERNAL_KEY = "external_key";
	public static final String COLUMN_LOCALISED_RES_NAME = "localised_name";
	public static final String COLUMN_SOURCE_TYPE = "source_type";
	// general data constants (internal for default sounds/beat types)
	public static final String INTERNAL_TYPE = PlatformResourceManager.SoundResourceType.INT.name();
	public static final String URI_ENTERED_TYPE = PlatformResourceManager.SoundResourceType.URI.name();
	public static final String USER_ENTERED_TYPE = "USER"; // for beat types
	// these are only used to provide extra rows in beat types/sounds ordered views, 
	// the values are important because want control1 to come before INT and control2
	// to come after it but before and user created stuff (uri)
	public static final String INTERNAL_TYPE_CONTROL1 = "AA";
	public static final String INTERNAL_TYPE_CONTROL2 = INTERNAL_TYPE+"ZZ";
	
	// library table constants
	static final String TABLE_LIBRARY = "library";
//	static final String COLUMN_LIBRARY_SYS_ID = "sys_id";
	static final String COLUMN_LIBRARY_SYS_KEY = "sys_text_key";
	static final String COLUMN_LIBRARY_VERSION = "library_version";

	// sounds table constants
	static final String TABLE_SOUNDS = "sounds";
	public static final String COLUMN_SOUND_NAME = "sound_name";
	public static final String COLUMN_SOUND_DURATION = "sound_duration";
	public static final String COLUMN_SOUND_URI = "sound_uri";
	public static final String COLUMN_SOUND_RAW_RES_NAME = "sound_raw_res_name";
	static final String VIEW_SOUNDS_ORDERED_LIST = "sounds_ordered_vw";
	// sounds data constants
	public static final String NO_SOUND = InternalSound.noSound.name();

	// beat types table constants
	static final String TABLE_BEAT_TYPES = "beat_types";
	public static final String COLUMN_SOUND_FK = "sound_fk";
	public static final String COLUMN_FALLBACK_SOUND_FK = "fallback_sound_fk";
	public static final String COLUMN_BEAT_TYPE_NAME = "beat_type_name";
	public static final String COLUMN_BEAT_TYPE_MISSING_SOUND_EXTERNAL_FK = "missing_sound_external_key";
	public static final String COLUMN_COLOUR = "colour";
	static final String COLUMN_VOLUME = "volume";
	static final String VIEW_BEAT_TYPES = "beat_types_vw";
	static final String VIEW_BEAT_TYPES_ORDERED_LIST = "beat_types_ordered_vw";
	public static final String VIEW_COLUMN_SOUND_LOCALISED_RES_NAME = "sound_"+COLUMN_LOCALISED_RES_NAME;
	public static final String VIEW_COLUMN_SOUND_TYPE = "sound_"+COLUMN_SOURCE_TYPE;

	// rhythms table constants
    static final String TABLE_RHYTHMS = "rhythms";
    static final String VIEW_RHYTHMS = "rhythms_vw";
    static final String VIEW_RHYTHMS_WITH_NEW_RHYTHM = "rhythms_new_rhythm_vw";
    public static final String NEW_RHYTHM_STRING_KEY = "newRhythm";
    public static final String NEW_RHYTHM_ORDERING_NAME = "000";
    public static final String VIEW_COLUMN_TAGS_LIST = "rhythm_tags_list";
	public static final String COLUMN_RHYTHM_NAME = "rhythm_name";
	static final String COLUMN_BPM = "bpm";
	static final String COLUMN_FULL_BEAT_1 = "full_beat_1";
	public static final String COLUMN_ENCODED_BEATS = "encoded_beats";
	static final String COLUMN_BEAT_TYPE_FK = "beat_type_fk";

	// edit rhythm
	static final String TABLE_EDIT_RHYTHM = "edit_rhythm";
	static final String COLUMN_SOURCE_RHYTHM_FK = "source_rhythm_fk";
	static final String COLUMN_CURRENT_STATE = "current_state";
	
	// tags constants
	static final String TABLE_TAGS = "tags";
	public static final String COLUMN_TAG_NAME = "tag_name";
	public static final String COLUMN_RHYTHM_FK = "rhythm_fk";

	// rhythm tags constants
	public static final String TABLE_RHYTHM_TAGS = "rhythm_tags";
    private static final String VIEW_RHYTHM_TAGS = "rhythm_tags_vw";
	public static final String COLUMN_TAG_FK = "tag_fk";

    // mementos table, several columns can be stored as required
    // used to store a new rhythm, and for undo ops etc
    static final String TABLE_MEMENTOS = "mementos";
    static final String TABLE_MEMENTO_CHILDREN = "memento_children";
    static final String COLUMN_MEMENTO_TYPE = "memento_type";
    static final String COLUMN_MEMENTO_STACK_TYPE= "stack_key";
    static final String COLUMN_MEMENTO_STRING1 = "string_1";
    static final String COLUMN_MEMENTO_STRING2 = "string_2";
    static final String COLUMN_MEMENTO_STRING3 = "string_3";
    static final String COLUMN_MEMENTO_INTEGER1 = "int_1";
    static final String COLUMN_MEMENTO_INTEGER2 = "int_2";
    static final String COLUMN_MEMENTO_NUMBER3 = "number_3";
    static final String COLUMN_MEMENTO_FK = "memento_id";

	private KybApplication mApplication;
	private SQLiteDatabase mOpenDb;
	
	// library data
	private String mSysKeyPart;
    private long mLastKeyTime = 0L;
	private long mSysId;

    // lru cache for fk ref objs (beat types, sounds, tags)
    private LruCache<String, AbstractSqlImpl> mRefDataCache;

	// core implementations
	private SoundsContentProvider mSounds;
	private BeatTypesContentProvider mBeatTypes;
	private RhythmsContentProvider mRhythms;
	private TagsContentProvider mTags;

	public KybSQLiteHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		mApplication = (KybApplication) context;
		mApplication.getResourceManager().setLibrary(this);

        mSysId = -1L; // tested for when next key requested

        mRefDataCache = new LruCache<String, AbstractSqlImpl>(
                            5 * // standard beat types
                            14 * // standard sounds
                            10 * // guess # tags might need
                            3);  // reasonable multiple
	}

	/**
	 * onCreate() is already called if needed, so load the creation key from the db
	 */
	public void init() {
		// sets up mOpenDb
		getWritableDatabase();
		
		Cursor cursor = mOpenDb.query(KybSQLiteHelper.TABLE_LIBRARY
				, new String[] { KybSQLiteHelper.COLUMN_LIBRARY_VERSION, KybSQLiteHelper.COLUMN_LIBRARY_SYS_KEY }
				, null, null, null, null, null);

		if (cursor.moveToFirst()) {
			mSysKeyPart = cursor.getString(1);
			mSysId = Long.parseLong(mSysKeyPart, Character.MAX_RADIX);
			AndroidResourceManager.logd(LOG_TAG, String.format("library version=%s sys_key=%s", cursor.getString(0), mSysKeyPart));
		} 
		else {
			AndroidResourceManager.loge(LOG_TAG, "library has no rows");
		}
		cursor.close();
	}

    public KybApplication getApplication() {
        return mApplication;
    }

    @Override
	public void onCreate(SQLiteDatabase db) {
		// callback on the data installer
		DataInstaller dataInstaller = mApplication.getDataInstaller();
		if (dataInstaller != null) {
			dataInstaller.dbOnCreateTriggered();
		}
		
		long sysId = System.currentTimeMillis() - FRESH_STARTER;
		String sysKeyPart = Long.toString(sysId, Character.MAX_RADIX);
		AndroidResourceManager.logi(LOG_TAG, String.format("onCreate: new database sys_text_key = %s context= %s", sysKeyPart, mApplication));
		
	    createDDL(db);
	    
		db.beginTransaction();
	    try {
	    	// insert single row for library
			createLibrary(db, sysKeyPart);
			
			// insert rows for internal (default) sounds
			createInternalSounds(db);
			
			// insert rows for default beat types
			createDefaultBeatTypes(db);

			db.setTransactionSuccessful();
		} 
	    catch (Exception e) {
			AndroidResourceManager.loge(LOG_TAG, "onCreate: exception trying to insert data... rolling back", e);
		}
	    finally {
			db.endTransaction();
		}

		// notify the installer
		if (dataInstaller != null) {
			dataInstaller.dbOnCreateFinished();
		}

	}

    /**
	 * Called from onCreate(), inserts a row for each default (internal) sound
	 * @param db
	 */
	private void createInternalSounds(SQLiteDatabase db) {
		Resources resources = mApplication.getResources();

		insertSound(resources, db, InternalSound.beep_1); 
		insertSound(resources, db, InternalSound.beep_2);
		insertSound(resources, db, InternalSound.beep_3);
		insertSound(resources, db, InternalSound.clap_high);
		insertSound(resources, db, InternalSound.clap_low);
		insertSound(resources, db, InternalSound.clap_double);
		insertSound(resources, db, InternalSound.maracas);
		insertSound(resources, db, InternalSound.tamborine);
		insertSound(resources, db, InternalSound.tick_middle);
		insertSound(resources, db, InternalSound.tick_wooden);
		insertSound(resources, db, InternalSound.tock_low);
		insertSound(resources, db, InternalSound.tock_shallow);
		insertSound(resources, db, InternalSound.tock_wooden);
        insertSound(resources, db, InternalSound.noSound);
        insertSound(resources, db, InternalSound.high_hat);
        insertSound(resources, db, InternalSound.bass_drum);
	}

	private void insertSound(Resources resources, SQLiteDatabase db, InternalSound internalSound) {
		String name = internalSound.name();
		

		ContentValues contentValues = new ContentValues();
		contentValues.put(COLUMN_EXTERNAL_KEY, name);
		contentValues.put(COLUMN_SOURCE_TYPE, INTERNAL_TYPE);
		contentValues.put(COLUMN_SOUND_NAME, name);
		contentValues.put(COLUMN_SOUND_URI, name);
		contentValues.put(COLUMN_SOUND_DURATION, internalSound.duration);
		contentValues.put(COLUMN_LOCALISED_RES_NAME, internalSound.getLocalisedKey().name());

		if (!internalSound.equals(InternalSound.noSound)) {
			contentValues.put(COLUMN_SOUND_RAW_RES_NAME, name);
		}
		
		db.insert(TABLE_SOUNDS, null, contentValues);		
	}

	private void insertBeatType(SQLiteDatabase db, String name, String soundExternalKey, int colour, String resName) {
		ContentValues contentValues = new ContentValues();
		contentValues.put(COLUMN_EXTERNAL_KEY, name);
		contentValues.put(COLUMN_SOURCE_TYPE, INTERNAL_TYPE);
		contentValues.put(COLUMN_SOUND_FK, soundExternalKey);
		contentValues.put(COLUMN_BEAT_TYPE_NAME, name);
		contentValues.put(COLUMN_COLOUR, colour);
		contentValues.put(COLUMN_VOLUME, BeatTypes.DEFAULT_VOLUME);
		// fallback sound for internal beat types will initially be invisible, but should be the same as default sound
		// manually changing it will keep the sound the same as before when the main sound is not found
		contentValues.put(COLUMN_FALLBACK_SOUND_FK, soundExternalKey);
		contentValues.put(COLUMN_LOCALISED_RES_NAME, resName);
		db.insert(TABLE_BEAT_TYPES, null, contentValues);
	}
	
	private void createDefaultBeatTypes(SQLiteDatabase db) {
		Resources res = mApplication.getResources();
		insertBeatType(db, DefaultBeatTypes.normal.name(), InternalSound.tock_shallow.name(), Color.parseColor(res.getString(R.string.normalColour)), "NORMAL_BEAT_TYPE_NAME");
		insertBeatType(db, DefaultBeatTypes.accent.name(), InternalSound.tick_middle.name(), Color.parseColor(res.getString(R.string.accentColour)), "ACCENT_BEAT_TYPE_NAME");
		insertBeatType(db, DefaultBeatTypes.alternate.name(), InternalSound.tock_shallow.name(), Color.parseColor(res.getString(R.string.alternateColour)), "ALTERNATE_BEAT_TYPE_NAME");
		insertBeatType(db, DefaultBeatTypes.silent.name(), InternalSound.noSound.name(), Color.parseColor(res.getString(R.string.quietColour)), "SILENT_BEAT_TYPE_NAME");
        insertBeatType(db, DefaultBeatTypes.first.name(), InternalSound.tick_middle.name(), Color.parseColor(res.getString(R.string.firstColour)), "FIRST_BEAT_TYPE_NAME");
        insertBeatType(db, DefaultBeatTypes.ghost.name(), InternalSound.high_hat.name(), Color.parseColor(res.getString(R.string.ghostColour)), "GHOST_BEAT_TYPE_NAME");
	}

	private void createLibrary(SQLiteDatabase db, String sysKeyPart) {
		db.execSQL("insert into " + TABLE_LIBRARY
                + "(" + COLUMN_LIBRARY_SYS_KEY
                + "," + COLUMN_LIBRARY_VERSION
                + ") VALUES ("
                + "'" + sysKeyPart + "'"
                + ",'" + LIBRARY_VERSION + "');");
		
	}

	/**
	 * DDL statements to create the database from scratch
	 * @param db
	 */
	private void createDDL(SQLiteDatabase db) {
		final String CREATE_LIBRARY = 
				"create table " + TABLE_LIBRARY 
				+ "(" + COLUMN_ID 				+ " integer primary key autoincrement"
//				+ "," + COLUMN_LIBRARY_SYS_ID 	+ " integer not null"
				+ "," + COLUMN_LIBRARY_SYS_KEY 	+ " text not null"
				+ "," + COLUMN_LIBRARY_VERSION 	+ " text not null);";
		
		final String CREATE_SOUNDS = 
				"create table " + TABLE_SOUNDS 
				+ "(" + COLUMN_ID 				+ " integer primary key autoincrement "
				+ "," + COLUMN_EXTERNAL_KEY 	+ " text not null unique "
				+ "," + COLUMN_SOURCE_TYPE 		+ " text not null "
				+ "," + COLUMN_SOUND_NAME 		+ " text not null "
				+ "," + COLUMN_SOUND_URI 		+ " text not null "
				+ "," + COLUMN_SOUND_DURATION 	+ " real "
				+ "," + COLUMN_LOCALISED_RES_NAME + " text "
				+ "," + COLUMN_SOUND_RAW_RES_NAME + " text );";

		final String CREATE_SOUNDS_ORDERED_LIST_VIEW = 
				"create view " + VIEW_SOUNDS_ORDERED_LIST
				+ " as select "
				+ COLUMN_ID
				+ ", " + COLUMN_SOURCE_TYPE
				+ ", " + COLUMN_EXTERNAL_KEY
				+ ", " + COLUMN_SOUND_NAME
				+ ", " + COLUMN_SOUND_URI
				+ ", " + COLUMN_SOUND_DURATION
				+ ", " + COLUMN_LOCALISED_RES_NAME
				+ ", " + COLUMN_SOUND_RAW_RES_NAME
				+ " from " + TABLE_SOUNDS 
				+ " union select -1, '" + INTERNAL_TYPE_CONTROL1 + "', null, null, null, null, null, null from library"
				+ " union select -2, '" + INTERNAL_TYPE_CONTROL2 + "', null, null, null, null, null, null from library";
		
		final String CREATE_BEAT_TYPES = 
				"create table " + TABLE_BEAT_TYPES
				+ "(" + COLUMN_ID 					+ " integer primary key autoincrement "
				+ "," + COLUMN_EXTERNAL_KEY 		+ " text not null unique "
				+ "," + COLUMN_SOURCE_TYPE 			+ " text not null "
				+ "," + COLUMN_SOUND_FK 			+ " text not null "
				+ "," + COLUMN_BEAT_TYPE_NAME	 	+ " text not null "
				+ "," + COLUMN_COLOUR			 	+ " integer not null "
				+ "," + COLUMN_VOLUME	 			+ " real not null "
				+ "," + COLUMN_FALLBACK_SOUND_FK 	+ " text "
				+ "," + COLUMN_LOCALISED_RES_NAME 	+ " text"
				+ "," + COLUMN_BEAT_TYPE_MISSING_SOUND_EXTERNAL_FK + " text"
						+ " );";
		
		final String CREATE_BEAT_TYPES_VIEW = 
				"create view " + VIEW_BEAT_TYPES
				+ " as select bt.*, s." + COLUMN_SOUND_NAME
				+ ", s." + COLUMN_LOCALISED_RES_NAME + " " + VIEW_COLUMN_SOUND_LOCALISED_RES_NAME
				+ ", s." + COLUMN_SOURCE_TYPE + " " + VIEW_COLUMN_SOUND_TYPE
				+ ", s." + COLUMN_SOUND_URI
				+ " from " + TABLE_BEAT_TYPES + " bt, " + TABLE_SOUNDS 
				+ " s where bt." + COLUMN_SOUND_FK + " = s." + COLUMN_EXTERNAL_KEY + ";";
		
		final String CREATE_BEAT_TYPES_ORDERED_LIST_VIEW = 
				"create view " + VIEW_BEAT_TYPES_ORDERED_LIST
				+ " as select "
				+ "bt." + COLUMN_ID
                + ",bt." + COLUMN_EXTERNAL_KEY
                + ",bt." + COLUMN_SOURCE_TYPE
				+ ",bt." + COLUMN_SOUND_FK
				+ ",bt." + COLUMN_BEAT_TYPE_NAME
				+ ",bt." + COLUMN_COLOUR
				+ ",bt." + COLUMN_FALLBACK_SOUND_FK
				+ ",bt." + COLUMN_LOCALISED_RES_NAME
				+ ", s." + COLUMN_SOUND_NAME
				+ ", s." + COLUMN_LOCALISED_RES_NAME + " " + VIEW_COLUMN_SOUND_LOCALISED_RES_NAME
				+ ", s." + COLUMN_SOURCE_TYPE + " " + VIEW_COLUMN_SOUND_TYPE
				+ ", s." + COLUMN_SOUND_URI
				+ " from " + TABLE_BEAT_TYPES + " bt, " + TABLE_SOUNDS 
				+ " s where bt." + COLUMN_SOUND_FK + " = s." + COLUMN_EXTERNAL_KEY
				+ " union select -1, '', '" + INTERNAL_TYPE_CONTROL1 + "', 0, '', 0, null, '', '', '', '', '' from library"
				+ " union select -2, '', '" + INTERNAL_TYPE_CONTROL2 + "', 0, '', 0, null, '', '', '', '', '' from library";

        final String CREATE_RHYTHMS =
                "create table " + TABLE_RHYTHMS
                        + "(" + COLUMN_ID 					+ " integer primary key autoincrement "
                        + "," + COLUMN_EXTERNAL_KEY 		+ " text not null unique "
                        + "," + COLUMN_RHYTHM_NAME 			+ " text not null "
                        + "," + COLUMN_BPM 					+ " integer not null "
                        + "," + COLUMN_FULL_BEAT_1 			+ " integer not null "
                        + "," + COLUMN_ENCODED_BEATS 		+ " text not null "
                        + "," + COLUMN_LOCALISED_RES_NAME	+ " text );";

        final String CREATE_EDIT_RHYTHM =
				"create table " + TABLE_EDIT_RHYTHM
				+ "(" + COLUMN_BPM 					+ " integer not null "
				+ "," + COLUMN_FULL_BEAT_1 			+ " integer not null "
				+ "," + COLUMN_ENCODED_BEATS 		+ " text not null"
				+ "," + COLUMN_CURRENT_STATE 		+ " text not null"
				+ "," + COLUMN_SOURCE_RHYTHM_FK		+ " text )";

		final String CREATE_TAGS = 
				"create table " + TABLE_TAGS
				+ "(" + COLUMN_ID 					+ " integer primary key autoincrement "
				+ "," + COLUMN_EXTERNAL_KEY 		+ " text not null unique "
				+ "," + COLUMN_TAG_NAME 			+ " text not null "
				+ "," + COLUMN_LOCALISED_RES_NAME	+ " text );";

		final String CREATE_RHYTHM_TAGS = 
				"create table " + TABLE_RHYTHM_TAGS
				+ "(" + COLUMN_ID 					+ " integer primary key autoincrement "
				+ "," + COLUMN_RHYTHM_FK 			+ " text not null "
						+ "references "+TABLE_RHYTHMS+"("+COLUMN_EXTERNAL_KEY+") on delete cascade "
				+ "," + COLUMN_TAG_FK				+ " text not null "
						+ "references "+TABLE_TAGS+"("+COLUMN_EXTERNAL_KEY+") on delete cascade "
						+ ");";

        final String CREATE_INDEX_RHYTHM_TAGS =
				"create unique index rt_idx on " + TABLE_RHYTHM_TAGS
				+ "(" + COLUMN_TAG_FK
				+ "," + COLUMN_RHYTHM_FK	+ ");";

        final String CREATE_MEMENTOS =
                "create table " + TABLE_MEMENTOS
                        + "(" + COLUMN_ID 					+ " integer primary key autoincrement "
                        + "," + COLUMN_MEMENTO_TYPE 		+ " integer not null "
                        + "," + COLUMN_MEMENTO_STACK_TYPE	+ " integer " // ie. which undoable controller
                        + "," + COLUMN_EXTERNAL_KEY 		+ " text "
                        + "," + COLUMN_MEMENTO_STRING1 		+ " text "
                        + "," + COLUMN_MEMENTO_STRING2 		+ " text "
                        + "," + COLUMN_MEMENTO_STRING3 		+ " text "
                        + "," + COLUMN_MEMENTO_INTEGER1 	+ " integer "
                        + "," + COLUMN_MEMENTO_INTEGER2 	+ " integer "
                        + "," + COLUMN_MEMENTO_NUMBER3 		+ " real );";

        final String CREATE_MEMENTO_CHILDREN =
                "create table " + TABLE_MEMENTO_CHILDREN
                        + "(" + COLUMN_ID 					+ " integer primary key autoincrement "
                        + "," + COLUMN_MEMENTO_TYPE 		+ " integer not null "
                        + "," + COLUMN_EXTERNAL_KEY 		+ " text "
                        + "," + COLUMN_MEMENTO_STRING1 		+ " text "
                        + "," + COLUMN_MEMENTO_STRING2 		+ " text "
                        + "," + COLUMN_MEMENTO_INTEGER1 	+ " integer "
                        + "," + COLUMN_MEMENTO_NUMBER3 		+ " real "
                        + "," + COLUMN_MEMENTO_FK			+ " integer not null "
                        + "references "+TABLE_MEMENTOS+"("+COLUMN_ID+") on delete cascade "
                        + ");";

        final String CREATE_RHYTHM_TAGS_VIEW =
                "create view " + VIEW_RHYTHM_TAGS
                        + " as select "
                        + TABLE_RHYTHM_TAGS + "." + COLUMN_ID
                        + "," + TABLE_RHYTHM_TAGS + "." + COLUMN_RHYTHM_FK
                        + "," + TABLE_RHYTHM_TAGS + "." + COLUMN_TAG_FK
                        + "," + TABLE_TAGS + "." + COLUMN_TAG_NAME
                        + " from " + TABLE_TAGS + " join " + TABLE_RHYTHM_TAGS
                        + " on " + TABLE_RHYTHM_TAGS + "." + COLUMN_TAG_FK  + " = " + TABLE_TAGS + "." + COLUMN_EXTERNAL_KEY + ";";

        final String CREATE_RHYTHMS_VIEW =
                "create view " + VIEW_RHYTHMS
                        + " as select "
                        + TABLE_RHYTHMS + "." + COLUMN_ID
                        + "," + TABLE_RHYTHMS + "." + COLUMN_EXTERNAL_KEY
                        + "," + TABLE_RHYTHMS + "." + COLUMN_RHYTHM_NAME
                        + "," + TABLE_RHYTHMS + "." + COLUMN_BPM
                        + "," + TABLE_RHYTHMS + "." + COLUMN_FULL_BEAT_1
                        + "," + TABLE_RHYTHMS + "." + COLUMN_ENCODED_BEATS
                        + "," + TABLE_RHYTHMS + "." + COLUMN_LOCALISED_RES_NAME
                        + ",GROUP_CONCAT(" + VIEW_RHYTHM_TAGS + "." + COLUMN_TAG_FK
                        + " || ',' || REPLACE(" + VIEW_RHYTHM_TAGS + "." + COLUMN_TAG_NAME + ", ',', ',,')) " // any commas doubled (these are separators)
                        + VIEW_COLUMN_TAGS_LIST
                        + " from " + TABLE_RHYTHMS + " left outer join " + VIEW_RHYTHM_TAGS
                        + " on " + VIEW_RHYTHM_TAGS + "." + COLUMN_RHYTHM_FK  + " = " + TABLE_RHYTHMS + "." + COLUMN_EXTERNAL_KEY
                        + " group by " + TABLE_RHYTHMS + "." + COLUMN_ID
                        + "," + TABLE_RHYTHMS + "." + COLUMN_EXTERNAL_KEY
                        + "," + TABLE_RHYTHMS + "." + COLUMN_RHYTHM_NAME
                        + "," + TABLE_RHYTHMS + "." + COLUMN_BPM
                        + "," + TABLE_RHYTHMS + "." + COLUMN_FULL_BEAT_1
                        + "," + TABLE_RHYTHMS + "." + COLUMN_ENCODED_BEATS
                        + "," + TABLE_RHYTHMS + "." + COLUMN_LOCALISED_RES_NAME + ";";

        final String CREATE_RHYTHMS_WITH_NEW_RHYTHM_VIEW =
                "create view " + VIEW_RHYTHMS_WITH_NEW_RHYTHM
                        + " as select "
                        + TABLE_RHYTHMS + "." + COLUMN_ID
                        + "," + TABLE_RHYTHMS + "." + COLUMN_EXTERNAL_KEY
                        + "," + TABLE_RHYTHMS + "." + COLUMN_RHYTHM_NAME
                        + "," + TABLE_RHYTHMS + "." + COLUMN_ENCODED_BEATS
                        + " from " + TABLE_RHYTHMS
                        + " union select -1, "
                        + "'" + NEW_RHYTHM_STRING_KEY + "'"                         // string resource key, use as key to new rhythm
                        + ", '" + NEW_RHYTHM_ORDERING_NAME + "'"                    // to get first in the list when ordered, rhythm name not used here
                        + "," + COLUMN_MEMENTO_STRING1                              // the whole rhythm is here, but contains the encoded beats which will be used for lookup
                        + " from " + TABLE_MEMENTOS
                        + " where "
                        + COLUMN_MEMENTO_TYPE + " = " + KybContentProvider.MEMENTO_TYPE_NEW_RHYTHM + ";";


        db.execSQL(CREATE_LIBRARY);
	    db.execSQL(CREATE_SOUNDS);
	    db.execSQL(CREATE_BEAT_TYPES);
	    db.execSQL(CREATE_RHYTHMS);
	    db.execSQL(CREATE_EDIT_RHYTHM);
	    db.execSQL(CREATE_TAGS);
	    db.execSQL(CREATE_RHYTHM_TAGS);
	    db.execSQL(CREATE_INDEX_RHYTHM_TAGS);
	    db.execSQL(CREATE_SOUNDS_ORDERED_LIST_VIEW);
	    db.execSQL(CREATE_BEAT_TYPES_VIEW);
        db.execSQL(CREATE_BEAT_TYPES_ORDERED_LIST_VIEW);
        db.execSQL(CREATE_MEMENTOS);
        db.execSQL(CREATE_MEMENTO_CHILDREN);
        db.execSQL(CREATE_RHYTHM_TAGS_VIEW);
        db.execSQL(CREATE_RHYTHMS_VIEW);
        db.execSQL(CREATE_RHYTHMS_WITH_NEW_RHYTHM_VIEW);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		AndroidResourceManager.logw(LOG_TAG, String.format("onUpgrade: no upgrade specified from version %s to %s", oldVersion, newVersion));
		// callback on the data installer
		DataInstaller dataInstaller = mApplication.getDataInstaller();
		if (dataInstaller != null) {
			dataInstaller.dbOnUpdateTriggered();
			dataInstaller.dbOnUpdateFinished();
		}

	}

	/**
	 * Called from lots of places in the core code to resolve the resource manager.
	 * The application context will lazily create one, but once this method is called (see constructor)
	 * it will also initialise the library to the resource manager, which is needed for core
	 * initialisation.
	 * @return
	 */
	@Override
	public PlatformResourceManager getResourceManager() {
		return mApplication.getResourceManager();
	}

	@Override
	public Sounds getSounds() {
		return mSounds;
	}

	public void setSounds(SoundsContentProvider soundsContentProvider) {
		mSounds = soundsContentProvider;
	}

	@Override
	public BeatTypes getBeatTypes() {
		return mBeatTypes;
	}

	public void setBeatTypes(BeatTypesContentProvider beatTypesContentProvider) {
		mBeatTypes = beatTypesContentProvider;
	}

	@Override
	public Rhythms getRhythms() {
		return mRhythms;
	}

	public void setRhythms(RhythmsContentProvider rhythmsContentProvider) {
		mRhythms = rhythmsContentProvider;
	}

	@Override
	public Tags getTags() {
		return mTags;
	}

	public void setTags(TagsContentProvider tagsContentProvider) {
		mTags = tagsContentProvider;
	}

	/**
	 * Engineer not to have super long ids, for a while anyway ;)
	 * @return
	 */
	@Override
	public String getNextKey() {
        if (mSysId == -1L) { // not been set yet, presumably no data was installed at startup, and now something needs a new key
            init();
        }

		long nextTime = (System.currentTimeMillis() - mSysId - FRESH_STARTER);
		if (nextTime <= mLastKeyTime)
			nextTime = mLastKeyTime + 1;
		String key = Long.toString(mSysId, Character.MAX_RADIX) + KEY_SPLITTER + Long.toString(nextTime, Character.MAX_RADIX);
		mLastKeyTime = nextTime;
		return key;
	}
	
	@Override
	public String getVersion() {
		return LIBRARY_VERSION;
	}

	/**
	 * get a writeable db, and also update the library's ref
	 * @return
	 */
	@Override
	public SQLiteDatabase getWritableDatabase() {

        //TODO remove this altogether once know everything is correct
        if (Looper.getMainLooper().equals(Looper.myLooper())) {
            throw new UiThreadDbAccessException();
        }

        mOpenDb = getUsableWritableDatabase();
		return mOpenDb;
	}

    public static class UiThreadDbAccessException extends RuntimeException {

    }

    /**
	 * Overloaded version for operations about to perform a change (insert/update/delete)
	 * and want to make sure there's a transaction already started.
	 * The param is there to decide whether no transaction reports a log error only
	 * or actually throws a runtime exception.
	 * @param exceptionNoTransaction
	 * @return
	 */
	public SQLiteDatabase getWritableDatabase(boolean exceptionNoTransaction) {
		mOpenDb = getUsableWritableDatabase();
		if (exceptionNoTransaction && !mOpenDb.inTransaction()) {
			String msg = "getWritableDatabase#2: not in a transaction";
			AndroidResourceManager.loge(LOG_TAG, msg);
			if (exceptionNoTransaction) {
				throw new IllegalStateException(msg);
			}
		}
		return mOpenDb;
	}
	
	/**
	 * Called by the prior 2 methods
	 * Always tries to use the current open db if there is one. Synchronized to ensure
     * only one db is opened
	 * @return
	 */
	private synchronized SQLiteDatabase getUsableWritableDatabase() {

		if (mOpenDb == null
				|| !mOpenDb.isOpen()) {
			mOpenDb = super.getWritableDatabase();
		}

		return mOpenDb;

	}
	
	/**
	 * Called from AndroidResourceManager when the application receives low/trim memory 
	 */
	@Override
	public void releaseResources() {
		if (mOpenDb != null) {
			if (mOpenDb.isOpen()) {

                if (mOpenDb.inTransaction()) {
                    AndroidResourceManager.logw(LOG_TAG, "releaseResources: in a transaction, unable to release");
                }
                else {
                    mOpenDb.close();
                    mOpenDb = null;
                    mRefDataCache.evictAll();
                }
			}
		}
	}
	
	/**
	 * Add a fk pragma to allow the on delete cascade to work on tag rhythms
	 */
	@Override
	public void onOpen(SQLiteDatabase db) {
	    super.onOpen(db);
	    if (!db.isReadOnly()) {
	        // Enable foreign key constraints
            final String turnOnFks = "PRAGMA foreign_keys=ON;";
            db.execSQL(turnOnFks);
	    }
	}

    public AbstractSqlImpl getFromCache(String key) {
        synchronized (mRefDataCache) {
            return mRefDataCache.get(key);
        }
    }

    public AbstractSqlImpl removeFromCache(String key) {
        synchronized (mRefDataCache) {
            return mRefDataCache.remove(key);
        }
    }

    public void putIntoCache(String key, AbstractSqlImpl sqlDataObj) {
        AndroidResourceManager.logv(LOG_TAG, String.format("putIntoCache: key=%s obj=%s", key, sqlDataObj));
        synchronized (mRefDataCache) {
            mRefDataCache.put(key, sqlDataObj);
        }
    }
}
