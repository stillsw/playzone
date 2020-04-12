package com.stillwindsoftware.keepyabeat.db;

import java.io.UnsupportedEncodingException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.gui.PlayRhythmsActivity;
import com.stillwindsoftware.keepyabeat.model.Beat.FullBeat;
import com.stillwindsoftware.keepyabeat.model.Beat.SubBeat;
import com.stillwindsoftware.keepyabeat.model.BeatFactory;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.RhythmMemento.ClonedBeatsMemento;
import com.stillwindsoftware.keepyabeat.model.RhythmMemento.ClonedRhythmMemento;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DragStoppedHelper;
import com.stillwindsoftware.keepyabeat.player.DrawnBeat;
import com.stillwindsoftware.keepyabeat.player.EditedBeat;
import com.stillwindsoftware.keepyabeat.player.PlayedBeat;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder.UnrecognizedFormatException;

/**
 * Sql implementation of Rhythm. Beats/Rhythm Beat Types are encoded in a string and
 * decoded when needed, obviating the need to store them all as entities in the db, and
 * keeping this implementation very similar to the xml implementation. 
 * This means there's quite a bit of very similar and duplicated code, which could possibly
 * be refactored with that. The good side is the code is tried and tested in RhythmXmlImpl
 * and those parts have a high confidence level. It's still possible to convert to a more
 * structured db data set (use tables instead of encoding the string), so it's too early
 * to refactor now. 
 * @author tomas stubbs
 *
 */
public class RhythmSqlImpl extends AbstractSqlImpl implements Rhythm {

	private static final String LOG_TAG = "KYB-"+RhythmSqlImpl.class.getSimpleName();

	protected String mEncodedBeatTypes;
	protected int mFullBeatNumber1;
	protected int mBpm;
	protected SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> mBeatDataCache;
    private String mDenormalizedTagsList;
	private long mBeatDataCacheNano = -1L;
    private long mDbNano = -1L; //
    private BaseRhythmDraughter mRhythmDraughter;

    public RhythmSqlImpl(Context context, KybSQLiteHelper library, Cursor csr) {
		super(library
				, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY))
				, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_RHYTHM_NAME))
				, csr.getLong(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));

			readRhythmColumns(csr);

        updateDbNanoTime(); // set the current db sync time
	}
	
	/**
	 * Called from the constructor to read the rhythm columns from the cursor
	 * @param csr
	 */
	protected void readRhythmColumns(Cursor csr) {
		mEncodedBeatTypes = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ENCODED_BEATS));
		mFullBeatNumber1 = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_FULL_BEAT_1));
		mBpm = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_BPM));

        int viewTagsList = csr.getColumnIndex(KybSQLiteHelper.VIEW_COLUMN_TAGS_LIST);
        if (viewTagsList != -1) {
            mDenormalizedTagsList = csr.getString(viewTagsList);
        }
	}

	public RhythmSqlImpl(KybSQLiteHelper library, String key, String name) {
		super(library, key, name);
        updateDbNanoTime(); // set the current db sync time
	}

	/**
	 * Special constructor called by RhythmsContentProvider.makeExactCopy()
	 * needs to default the null Strings (name and encoded beat types) because
	 * they will be completed a few steps down and the row has to exist in the db
	 * first)
	 * @param context
	 * @param library
	 * @param key
	 * @param defaultNullValues
	 */
	public RhythmSqlImpl(Function context, KybSQLiteHelper library, String key, boolean defaultNullValues) {
		this(context, library, key);
		if (defaultNullValues) {
			mName = mEncodedBeatTypes = "";
		}
        updateDbNanoTime(); // set the current db sync time
	}
	
	public RhythmSqlImpl(Function context, KybSQLiteHelper library, String key) {
		super(library, key, null);
		setFullBeatNumber1(context, 0);
		mBpm = Rhythms.DEFAULT_BPM;
        updateDbNanoTime(); // set the current db sync time
	}

    /**
     * Called from rhythms.requeryPlayerEditorRhythm() it should already be certain to have
     * the correct rhythm and only updating the fields on it (ie. fk data etc)
     * It's for the player/editor so beat tree must be created.
     * @param rhythm
     * @param includeBeats
     */
    public void requeryFromRhythm(RhythmSqlImpl rhythm, boolean includeBeats) {
        if (!rhythm.mKey.equals(mKey)) {
            throw new IllegalArgumentException(String.format("requery keys must match (this=%s, param=%s", mKey, rhythm.mKey));
        }

        mId = rhythm.mId;
        mName = rhythm.mName;
        mEncodedBeatTypes = rhythm.mEncodedBeatTypes;
        mFullBeatNumber1 = rhythm.mFullBeatNumber1;
        mBpm = rhythm.mBpm;
        mDenormalizedTagsList = rhythm.mDenormalizedTagsList;
        if (includeBeats) {
            mBeatDataCache = null;
            getBeatsAllData(mRhythmDraughter, true, BeatTree.PLAYED);
        }
    }

    public String getDenormalizedTagsList() {
        return mDenormalizedTagsList;
    }

    /**
	 * Called by RhythmEncoder.codifyRhythm and by ClonedBeatsMemento
	 * Lazy init the string as needed 
	 */
	@Override
	public String getEncodedBeatTypes() {
		return mEncodedBeatTypes;
	}

	/**
	 * Called from function DeleteBeatType, called once for each rhythm that
	 * is getting a replacement beat type. This method updates the db
	 * for each one which isn't the best way to do it... if it turns out to be
	 * too slow changing the function to tell the Rhythms impl to do the
	 * update might be better.
	 * NOTE: this method must update the db for existing rhythm or defer that to insert
	 * it calls updateRhythmFromCache to do that
	 * 
	 * The processing is the same as for RhythmXmlImpl
	 * Used in delete beat type processing to clear out references to the original (deleted) beat type.
	 * Several steps required:
	 * 1) use current cache if there is one
	 * 2) use existing occurrence of rhythm beat types for the swap beat type if there is one
	 * 3) change all beats using old beat type to new one
	 * 4) update the encoded beat types string with the changes 
	 * 5) clear cache if made just for this operation 
	 */
	@Override
	public void swapBeatTypes(Function context, BeatType originalBeatType,
			BeatType swapBeatType) throws NoCurrentCacheException {
		boolean startedWithCache = hasBeatDataCache();
		
		// no cache so cause it to have one (this is needed for the final update from cache to work,
		// and then will be deleted
		if (!startedWithCache) {
			getBeatsAllData(null, true, BeatTree.BASIC);
		}
		
		TreeSet<RhythmBeatType> rhythmBeatTypes = mBeatDataCache.getKey();
		RhythmBeatType originalRbt = RhythmBeatType.getRhythmBeatType(originalBeatType, rhythmBeatTypes);
		if (originalRbt != null) {
			// remove the rhythm beat type for the original beat type
			rhythmBeatTypes.remove(originalRbt);
			
			// get the existing occurrence for the swap beat type, or make one
			if (RhythmBeatType.getRhythmBeatType(swapBeatType, rhythmBeatTypes) == null) {
				rhythmBeatTypes.add(new RhythmBeatType(this, swapBeatType));
			}
			
			// walk the beats (full and sub) update all that use the original to the new
			batchSwapBeatTypes(originalBeatType, swapBeatType);
			
			// no current cache is thrown from here, but can only be a programming bug, because cache is created in this method
			updateRhythmFromCache(context);
			
			// no cache before this, so remove it again
			if (!startedWithCache) {
				clearCache();
			}
			
		}
		else { // originalRbt == null
			// nothing to do since the original beat type isn't in the dialog_simple_list, this is a programming bug
			// because this method should only be called when this is already established
			AndroidResourceManager.loge(LOG_TAG, String.format("swapBeatTypes: no rhythmBeatType for beat type (%s) in rhythm (%s)"
					, originalBeatType.getName(), getName()));
		}
	}

	/**
	 * Change every beat that has the beat type passed
	 * called from swapBeatTypes, a data cache has already been created if needed
	 * @param fromBeatType
	 * @param toBeatType
	 */
	private void batchSwapBeatTypes(BeatType fromBeatType, BeatType toBeatType) {
		
		BeatTree beatTree = mBeatDataCache.getValue();
		
		for (int i = 0; i < beatTree.size(); i++) {
			FullBeat fullBeat = beatTree.getFullBeatAt(i);

			if (fullBeat.getBeatType().equals(fromBeatType)) {
				fullBeat.setBeatType(toBeatType);
			}

            if (fullBeat.getNumPartBeats() > 1) {
                SubBeat[] partBeats = fullBeat.getPartBeats();
                for (int j = 0; j < partBeats.length; j++) {
                    SubBeat partBeat = partBeats[j];
                    if (partBeat == null) continue;

                    if (partBeat.getBeatType().equals(fromBeatType)) {
                        partBeat.setBeatType(toBeatType);
                    }
                }
            }
		}
	}

	/**
	 * Called from BeatTypeFunction.AddBeatType in restoration of a beat type
	 * and by RhythmFunction.restoreBeatsFromMemento()
	 * NOTE: this method must update the db for existing rhythm or defer that to insert,
	 * it does this in setEncodedBeatTypes()
	 */
	@Override
	public void restoreBeatsFromMemento(Function context,
			ClonedBeatsMemento clonedBeatsMemento, BeatFactory beatFactory) {

		// will have to recreate the data cache if there's data there, so can be reused
		boolean cacheExisting = hasBeatDataCache();

		setEncodedBeatTypes(context, clonedBeatsMemento.getEncodedBeatsString());
		
		// recreate a cache of data
		if (cacheExisting) {
			clearCache();
			getBeatsAllData(beatFactory, true, BeatTree.BASIC);
		}
	}

	/**
	 * Called from RhythmFuction.restoreFromMemento() and from 
	 * RhythmImporter.importRhythmsAndReference and .undo() 
	 * It removes all the current data and replaces it from the input
	 * NOTE: this method must update the db for existing rhythm or defer that to insert
	 */
	@Override
	public void restoreFromClonedMemento(Function context, ClonedRhythmMemento clonedMemento)
			throws UnrecognizedFormatException {

		// will have to recreate the data cache if there's data there, so can be reused
		boolean cacheExisting = hasBeatDataCache();

		try {
			RhythmEncoder.getLightWeightRhythmEncoder(context, mLibrary)
								.resetRhythmFromString(clonedMemento.getEncodedRhythmString(), this);

			// update the db, likely some overlap with the setters called in the previous
			// method call
			if (existsInDb()) {
				updateDb(false);
			}
			
			// recreate a cache of data
			if (cacheExisting) {
                AndroidResourceManager.logd(LOG_TAG, "restoreFromClonedMemento: cached, so need to recreate it");
                FullBeat beat1 = getBeatTree(-1 /* not used in this context */).getFullBeatAt(0); // get first beat
                int typeOfBeat = beat1 instanceof EditedBeat.EditedFullBeat
                        ? BeatTree.EDIT
                        : beat1 instanceof PlayedBeat.PlayedFullBeat
                        ? BeatTree.PLAYED
                        : BeatTree.DRAWN;
				clearCache();
				getBeatsAllData(mRhythmDraughter, true, typeOfBeat); // use existing draughter, which could be null
			}
			
		} catch (UnsupportedEncodingException e) {
			// can't happen
			AndroidResourceManager.loge(LOG_TAG, "restoreFromClonedMemento: this should never happen", e);
		} catch (UnrecognizedFormatException e) {
			// hope can't happen either, since took the memento just before...
			// throw it all the way up to the undo command and deal with it there
			throw e;
		}
	}

    public boolean existsInDb() {
        return mId != Library.INT_NOT_CHANGED;
    }

    /**
	 * For twl, the beats data (beats and rhythm beat types) are not extracted
	 * from the encoded string unless required. For android the same can be true
	 * only a playable rhythm (current played or edited) needs to have all the data
	 * in this structure. The difference is that it is extracted from the db not
	 * an encoded string.
	 */
	@Override
	public boolean hasBeatDataCache() {
		return mBeatDataCache != null;
	}

	/**
	 * Called from RhythmBeatFunction  (for edit rhythm) after already checking
	 * there is a cache, to return that cached tree. For this reason, it doesn't 
	 * need to create the cached tree itself.
	 * NOTE: the xmlImpl does test for null and create if needed, looks like that
	 * is not required so will first try this way
	 */
	@Override
	public BeatTree getBeatTree(int typeOfBeat) {
        if (mBeatDataCache == null) {
            return getBeatTree(null, typeOfBeat);
        }
        else {
            return mBeatDataCache.getValue();
        }
	}

	/**
	 * Make and populate a beat tree beats of the type made by the factory
	 * @return
	 */
	private BeatTree getBeatTree(BeatFactory beatFactory, int typeOfBeat) {
		if (mEncodedBeatTypes != null) {
			AndroidResourceManager.logd(LOG_TAG, "getBeatTree: called");

            SQLiteDatabase db = mLibrary.getWritableDatabase();
            db.beginTransaction(); // just for this, may be changes if missing data needs to be inserted (eg. beat type)

			try {
                BeatTree tree = RhythmEncoder.getLightWeightRhythmEncoder(
                        Function.getBlankContext(mLibrary.getResourceManager()), mLibrary)
                            .decodeBeats(this, mEncodedBeatTypes, beatFactory, typeOfBeat);

                db.setTransactionSuccessful();

                return tree;

			} catch (UnrecognizedFormatException e) {
				// bug error, failed somewhere in the middle, can't save now, so have to abort
				AndroidResourceManager.loge(LOG_TAG, "getBeatTree: format error in the encoding string, this should never happen!", e);
				mLibrary.getResourceManager().getGuiManager().abortWithMessage(
						CoreLocalisation.Key.UNEXPECTED_WRITE_FORMAT_ERROR);
				return null; // won't reach this, previous method will throw exception
			}
            finally {
                db.endTransaction();
            }
        }
		else {
			AndroidResourceManager.loge(LOG_TAG, "getBeatTree: coding bug, attempt to get beat tree with no encoded string, means it's a new rhythm, should use getBeatsAllData");
			return null;
		}
	}

	@Override
	public int getFullBeatNumber1() {
		return mFullBeatNumber1;
	}

	/**
	 * Called from RhythmEditCommand.resetFullBeatNumbers and .batchUndo to 
	 * make a systematic update of the beats as a way of guaranteeing consistency.
	 * Also from RhythmFunction.changeBeatNumberOne
	 * NOTE: this method must update the db for existing rhythm or defer that to insert
	 */
	@Override
	public void setFullBeatNumber1(Function context, int fullBeat1) {
		mFullBeatNumber1 = fullBeat1;

		if (existsInDb()) {
			ContentValues values = new ContentValues();
			values.put(KybSQLiteHelper.COLUMN_FULL_BEAT_1, mFullBeatNumber1);
			updateDb(values);
		}
	}

	/**
	 * When editing a rhythm this is the point at which the changes are
	 * saved (ie. in twl saved to an encoded string). Called from RhythmEditcommand
	 * .saveExecuteTransation() and RhythmFunction for the following where rhythm
	 * beat types are changed : changeDisplayNumbers/Sound/Volume
	 * NOTE: this method must update the db, it is the point when that happens,
	 * any prior changes to the beat tree are not saved individually as they are
	 * processed, but only in this call.
	 * Duplicate of RhythmXmlImpl (almost - added the updateCache() call because the rhythm draughter seems to ignore rbt display numbers changes)
	 */
	@Override
	public void updateRhythmFromCache(Function context) {

        boolean madeTemporaryCache = false;

		if (mBeatDataCache == null) {
			AndroidResourceManager.logw(LOG_TAG, String.format("updateRhythmFromCache: attempt to update rhythm beat data without a current cache, creating a temporary one (name=%s, key=%s)", mName, mKey));
            madeTemporaryCache = true;
            getBeatsAllData(null, true, BeatTree.BASIC);
		}

        // record the nano time of the update so rhythm draughter sees it
        updateCache(mBeatDataCache);
		setEncodedBeatTypes(context, mBeatDataCache);

        if (madeTemporaryCache) {
            clearCache();
        }
	}

    @Override
    public void updateCacheNanoTime() throws NoCurrentCacheException {
        if (mBeatDataCache == null) {
            AndroidResourceManager.loge(LOG_TAG, String.format("updateCacheNanoTime: attempt to update rhythm beat nanos without a current cache name=%s, key=%s", mName, mKey));
            throw new NoCurrentCacheException();
        }
        mBeatDataCacheNano = System.nanoTime();
    }

    /**
     * When the rhythm gets updated to the db set this time stamp
     * Particular use for RBT when returning from another activity (eg. updating beat types or sounds) and if the rhythm was
     * cached, this will indicate something changed and a refresh is needed
     */
    public void updateDbNanoTime() {
        mDbNano = System.nanoTime();
    }

    /**
	 * Called from RhythmFunction.changeBpm
	 * NOTE: this method must update the db for existing rhythm or defer that to insert
	 */
	@Override
	public void setBpm(Function context, int bpm) {
		mBpm = bpm;

		if (existsInDb()) {
			ContentValues values = new ContentValues();
			values.put(KybSQLiteHelper.COLUMN_BPM, mBpm);
			updateDb(values);
		}
        else {
            // must be the new rhythm?
            AndroidResourceManager.logi(LOG_TAG, "setBpm: interpreting not in db as have to update the new rhythm data in the db");
            ((RhythmsContentProvider) mLibrary.getRhythms()).insertOrUpdateNewRhythm(this);
            updateDbNanoTime();
        }
	}

	@Override
	public int getBpm() {
		return mBpm;
	}

	/**
	 * Called by RhythmFunction.saveRhythm(x2) when an edit rhythm is saved 
	 * either with or without name/tags 
	 * (in the case of the latter, the rhythm is new and the name/tags are 
	 * updated after this call)
	 * This method copies everything including header/beats data/tags
	 */
	@Override
	public void replaceFromRhythm(Function context, Rhythm copyRhythm)
			throws UnrecognizedFormatException {
		restoreFromClonedMemento(context, new ClonedRhythmMemento(copyRhythm));
	}

	@Override
	public void clearCache() {
		mBeatDataCache = null;
	}

    /**
     * Overloaded version that returns the cache w/o making any changes
     * @return
     */
    public SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> getBeatsAllData() throws NoCurrentCacheException {

        if (mBeatDataCache != null) {
            return mBeatDataCache;
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, String.format("getBeatsAllData: called overloaded version w/o there being any data, name=%s, key=%s", mName, mKey));
            throw new NoCurrentCacheException();
        }
    }

	/**
	 * Called from BaseRhythmDraughter.updateBeatsData when it needs a new or updated
	 * view on the beats and rhythm beat types. Update would come if undo has triggered
	 * a change through the listener.
	 * Called from RhythmImporter to extract from an imported (xml) rhythm all the data
	 * for import.
	 * Called from RhythmFunction.makeTemporaryCacheIfNeeded during undo processing where
	 * the cache from the original update is no longer on the rhythm and needs to be
	 * recreated in order to perform the undo.
	 * 
	 * Currently duplicate of RhythmXmlImpl...
	 * Used when need beats and also the volume/sounds by beat type for this rhythm, so can all be read in
	 * one hit. Unlikely to be used except by player or edit rhythm components, so beatFactory is always
	 * expected. If the rhythm is new, supplies a default set of beats.
	 * This method causes the rhythm to cache the data too, for updates and undos, the caller must clear
	 * that cache when finished with the rhythm to avoid building up objects in memory that aren't needed.
	 * @param beatFactory
	 * @param cacheData indicates the data will be used to receive updates and should be kept until
     * @param typeOfBeat when creating new beats because the factory is null, what type to create
	 * explicitly cleared (see clearCache())
	 */
	@Override
	public SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> getBeatsAllData(
			BeatFactory beatFactory, boolean cacheData, int typeOfBeat) {

		if (mBeatDataCache != null && cacheData) {
//			AndroidResourceManager.logd(LOG_TAG, String.format("getBeatsAllData:realigning cached data already created, name=%s, key=%s", mName, mKey));
//
//            if (!mLibrary.getResourceManager().getGuiManager().isUiThread()) { // take the opportunity to update the data as it's not on the ui thread
//                try {
//                    realignRhythmBeatTypes(); // updates the cached rbts itself
//                } catch (Exception e) {
//                    AndroidResourceManager.loge(LOG_TAG, "getBeatsAllData: error very unlikely here!", e);
//                }
//            }
            return mBeatDataCache;
		}
		
		SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> beatData = null;

        SQLiteDatabase db = mLibrary.getWritableDatabase();
        db.beginTransaction(); // just for this, may be changes if missing data needs to be inserted (eg. beat type)

        try {
            // test that rhythm has an encodedBeatTypes string, if not it must be new and unsaved
            if (mEncodedBeatTypes == null) {
                AndroidResourceManager.logd(LOG_TAG, "getBeatsAllData:make a default set");
                // it's a new rhythm
                // make a default set of beats
                BeatType beatType = (BeatType) mLibrary.getBeatTypes().lookup(BeatTypes.DefaultBeatTypes.normal.getKey());

                TreeSet<RhythmBeatType> rhythmBeatTypes = new TreeSet<RhythmBeatType>();
                rhythmBeatTypes.add(new RhythmBeatType(this, beatType));

                BeatTree beatTree = (beatFactory == null ? new BeatTree(this, typeOfBeat) : new BeatTree(this, beatFactory));
                for (int i = 0; i < Rhythms.DEFAULT_RHYTHM_BEATS; i++) {
                    beatTree.addFullBeatToEnd(Function.getBlankContext(mLibrary.getResourceManager()), beatType);
                }

                beatData = new SimpleEntry<>(rhythmBeatTypes, beatTree);

                // set the encoded string
                setEncodedBeatTypes(Function.getBlankContext(mLibrary.getResourceManager()), beatData);
                db.setTransactionSuccessful();
            }
            else { // exists
                AndroidResourceManager.logd(LOG_TAG, "getBeatsAllData:decoding data");
                try {
                    RhythmEncoder encoder = RhythmEncoder.getLightWeightRhythmEncoder(Function.getBlankContext(mLibrary.getResourceManager()), mLibrary);

                    TreeSet<RhythmBeatType> rhythmBeatTypes = encoder.decodeRhythmBeatTypes(this, mEncodedBeatTypes);
                    BeatTree beatTree = encoder.decodeBeats(this, mEncodedBeatTypes, beatFactory, typeOfBeat);

                    beatData = new SimpleEntry<>(rhythmBeatTypes, beatTree);
                    db.setTransactionSuccessful();

                } catch (UnrecognizedFormatException e) {
                    // bug error, failed somewhere in the middle, can't save now, so have to abort
                    AndroidResourceManager.loge(LOG_TAG, "getBeatTree: format error in the encoding string, this should never happen!", e);
                    mLibrary.getResourceManager().getGuiManager().abortWithMessage(
                            CoreLocalisation.Key.UNEXPECTED_WRITE_FORMAT_ERROR);
                } catch (UnsupportedEncodingException e) {
                }
            }

            // cache data for updates
            if (cacheData) {
                updateCache(beatData);
            }
        }
        finally {
            db.endTransaction(); // commit or rollback, depends if successful
        }

        return beatData;
	}

    private void updateCache(SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> beatData) {
        AndroidResourceManager.logv(LOG_TAG, String.format("updateCache: storing a current cache name=%s, key=%s", mName, mKey));
        mBeatDataCache = beatData;
        final BeatTree beatTree = mBeatDataCache.getValue();
        FullBeat beat1 = beatTree.getFullBeatAt(0);
        if (beat1 instanceof DrawnBeat.DrawnFullBeat) {
            beatTree.calcBeatsDeltaRanges();
        }

        try {
            updateCacheNanoTime();
        }
        catch (NoCurrentCacheException e) {
            // can't happen as this method is called only when there's a cache already
        }
    }

    /**
	 * For rhythm draughting to check when the
	 * cache being used was created, for instance after RhythmFunction.restoreBeatsFromMemento() is called
	 * (as it is after undo delete beat type), the underlying beats string could be inconsistent with the
	 * cache. Clients of the cache will listen to changes (eg. Beat Types as above) and 
	 * read the cache nano time to see if an update to the model is needed. 
	 * @return
	 */
	@Override
	public long getBeatDataCacheNano() {
		return mBeatDataCacheNano;
	}

    /**
     * Existed already for editRhythm (via interface), but now moved here because there is a case where
     * the rbt data is updated to the db but the existing cache is not yet up to date (see updateDbNanoTime())
     * @throws NoCurrentCacheException
     * @throws UnsupportedEncodingException
     * @throws UnrecognizedFormatException
     */
    public void realignRhythmBeatTypes()
            throws NoCurrentCacheException, UnsupportedEncodingException,
            UnrecognizedFormatException {

        if (mBeatDataCache == null) {
            AndroidResourceManager.logw(LOG_TAG, "realignRhythmBeatTypes: attempt to update rhythm beat data without a current cache");
            throw new NoCurrentCacheException();
        }

        TreeSet<RhythmBeatType> existingRbts = mBeatDataCache.getKey();

        RhythmEncoder encoder = RhythmEncoder.getLightWeightRhythmEncoder(Function.getBlankContext(mLibrary.getResourceManager()), mLibrary);
        TreeSet<RhythmBeatType> changedRbts = encoder.decodeRhythmBeatTypes(this, mEncodedBeatTypes);

        // rather than overwriting the whole set, delete the current rows and replace them
        existingRbts.clear();
        existingRbts.addAll(changedRbts);
    }

    public long getDbUpdatedNano() {
        return mDbNano;
    }

    public boolean isDataMismatched(ArrayList<RhythmBeatType> testAgainstRbts) {
        String threadName = mLibrary.getResourceManager().getGuiManager().isUiThread() ? "UI" : "Non-UI";
        long threadId = Thread.currentThread().getId();
        if (mBeatDataCache == null) {
            AndroidResourceManager.logd(LOG_TAG, String.format("isDataMismatched: no current cache (thread=%s id=%s)", threadName, threadId));
            return true;
        }

        TreeSet<RhythmBeatType> cachedRbts = mBeatDataCache.getKey();
        int rbtCountLocal = cachedRbts == null ? 0 : cachedRbts.size();
        if (testAgainstRbts.size() != rbtCountLocal) {
            AndroidResourceManager.logd(LOG_TAG, String.format("isDataMismatched: count local=%s, passed in=%s  (thread=%s id=%s)", rbtCountLocal, testAgainstRbts.size(), threadName, threadId));
            return true;
        }

        // same number of records, now check same data in both
        // only need to check one way since if same count and all found in one direction, inverse must also be true
        for (Iterator<RhythmBeatType> iterator = cachedRbts.iterator(); iterator.hasNext(); ) {
            RhythmBeatType cachedRhythmBeatType = iterator.next();

            boolean found = false;

            final BeatType cachedRbtBeatType = cachedRhythmBeatType.getBeatType();

            for (int i = 0; i < testAgainstRbts.size(); i++) {
                RhythmBeatType testAgainstRbt = testAgainstRbts.get(i);

                final BeatType testAgainstRbtBeatType = testAgainstRbt.getBeatType();

                if (testAgainstRbtBeatType.getKey().equals(cachedRbtBeatType.getKey())) {    // match on keys

                    found = testAgainstRbt.getSound().getKey().equals(cachedRhythmBeatType.getSound().getKey())     // checks each of the bits
                            && Float.compare(testAgainstRbt.getVolume(), cachedRhythmBeatType.getVolume()) == 0
                            && testAgainstRbt.isDisplayNumbers() == cachedRhythmBeatType.isDisplayNumbers()
                            && ((AndroidColour)testAgainstRbtBeatType.getColour()).getColorInt() ==
                                ((AndroidColour)cachedRbtBeatType.getColour()).getColorInt();

                    if (!found) {
                        AndroidResourceManager.logd(LOG_TAG, String.format("isDataMismatched: testAgainstRbt matched on beat type but something changed (bt name=%s sound=%s volume=%.2f, colour=%s, dispNums=%s)"
                                , cachedRbtBeatType.getName(), cachedRhythmBeatType.getSound().getKey(), cachedRhythmBeatType.getVolume()
                                , cachedRbtBeatType.getColour().getHexString(), cachedRhythmBeatType.isDisplayNumbers()));
                        return true;                                                                                // already found a mismatch no need to go on
                    }
                    else {
                        break;      // found = true, don't search for more
                    }
                }
            }

            if (!found) {
                AndroidResourceManager.logd(LOG_TAG, String.format("isDataMismatched: rbt match not found in passed set (bt name=%s thread=%s id=%s)"
                        , cachedRbtBeatType.getName(), threadName, threadId));
                return true;
            }
        }

        return false; // got this far all must be the same
    }

    /**
	 * Called from RhythmEncoder.decodeRhythm during unpacking of encoded data
	 * NOTE: this method must update the db for existing rhythm or defer that to insert
	 */
	@Override
	public void setBeatOneAndBpm(Function context, String beatOne, String bpm) {
		mFullBeatNumber1 = Integer.parseInt(beatOne);
		mBpm = Integer.parseInt(bpm);

		if (existsInDb()) {
			ContentValues values = new ContentValues();
			values.put(KybSQLiteHelper.COLUMN_FULL_BEAT_1, mFullBeatNumber1);
			values.put(KybSQLiteHelper.COLUMN_BPM, mBpm);
			updateDb(values);
		}
	}

	/**
	 * Called from RhythmEncoder.decodeRhythm during unpacking of encoded data
	 * For xml impl this means store the encoded string here, but not for sql impl
	 * where it should trigger a db update of beats/rhythm beat types
	 * NOTE: this method must update the db for existing rhythm or defer that to insert
	 */
	@Override
	public void setEncodedBeatTypes(Function context, String encodedBeatTypes) {
		mEncodedBeatTypes = encodedBeatTypes;

		if (existsInDb()) {
			ContentValues values = new ContentValues();
			values.put(KybSQLiteHelper.COLUMN_ENCODED_BEATS, mEncodedBeatTypes);
			updateDb(values);
		}
        else {
            // must be the new rhythm?
            AndroidResourceManager.logd(LOG_TAG, "setEncodedBeatTypes: interpreting not in db as have to update the new rhythm data in the db");
            ((RhythmsContentProvider) mLibrary.getRhythms()).insertOrUpdateNewRhythm(this);
            updateDbNanoTime();
        }
	}

    @Override
    public void setRhythmDraughter(BaseRhythmDraughter draughter) {
        mRhythmDraughter = draughter;
    }

    @Override
    public BaseRhythmDraughter getRhythmDraughter() {
        return mRhythmDraughter;
    }

    /**
	 * Any update to the rhythm's beat types data is encoded for saving
	 * Duplicate of RhythmXmlImpl
	 * @param context
	 * @param beatTreeAllData
	 */
	private void setEncodedBeatTypes(Function context, SimpleEntry<TreeSet<RhythmBeatType>, BeatTree> beatTreeAllData) {

		try {
			setEncodedBeatTypes(context, 
					RhythmEncoder.getLightWeightRhythmEncoder(context, mLibrary)
						.codifyRhythmBeatTypes(beatTreeAllData.getValue(), beatTreeAllData.getKey(), this));
		} catch (UnsupportedEncodingException e) {
		}
	}
	
	/**
	 * Called from each of the set methods if there is an id on the rhythm 
	 * (ie. it exists already in the db)
	 * @param values
	 */
	protected void updateDb(ContentValues values) {
		RhythmsContentProvider rhythms = (RhythmsContentProvider) mLibrary.getRhythms();
		Uri updateUri = Uri.parse(String.format(RhythmsContentProvider.SINGLE_ROW_CONTENT_STR, mId));
		rhythms.update(updateUri, values, null, null);
        updateDbNanoTime();
	}
	
	/**
	 * Overloaded version to update all values
	 * This is also called by RhythmsContentProvider.makeExactCopy() in the rhythms importing
	 * process.
	 * @param includeExternalKey in the case of copy or replace from rhythm will also overwrite this column
	 */
	public void updateDb(boolean includeExternalKey) {
		ContentValues values = new ContentValues();
		if (includeExternalKey) {
			values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, mKey);
		}
		values.put(KybSQLiteHelper.COLUMN_RHYTHM_NAME, mName);
		values.put(KybSQLiteHelper.COLUMN_FULL_BEAT_1, mFullBeatNumber1);
		values.put(KybSQLiteHelper.COLUMN_BPM, mBpm);
		values.put(KybSQLiteHelper.COLUMN_ENCODED_BEATS, mEncodedBeatTypes);
		updateDb(values);
	}

    @Override
    public String getListenerKey() {
        return getKey();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RhythmSqlImpl rhythmSql = (RhythmSqlImpl) o;

        if (mKey != null ? !mKey.equals(rhythmSql.mKey) : rhythmSql.mKey != null) {
            return false;
        }

        if (mName != null ? !mName.equals(rhythmSql.mName) : rhythmSql.mName != null) {
            return false;
        }

        if (mFullBeatNumber1 != rhythmSql.mFullBeatNumber1) {
            return false;
        }
        if (mBpm != rhythmSql.mBpm) {
            return false;
        }
        if (mEncodedBeatTypes != null ? !mEncodedBeatTypes.equals(rhythmSql.mEncodedBeatTypes) : rhythmSql.mEncodedBeatTypes != null) {
            return false;
        }
        return true;

    }

    @Override
    public int hashCode() {
        int result = mKey != null ? mKey.hashCode() : 0;
        result = 31 * result + (mName != null ? mName.hashCode() : 0);
        result = 31 * result + (mEncodedBeatTypes != null ? mEncodedBeatTypes.hashCode() : 0);
        result = 31 * result + mFullBeatNumber1;
        result = 31 * result + mBpm;
        result = 31 * result + (mBeatDataCache != null ? mBeatDataCache.hashCode() : 0);
        result = 31 * result + (int) (mBeatDataCacheNano ^ (mBeatDataCacheNano >>> 32));
        return result;
    }

}
