package com.stillwindsoftware.keepyabeat.db;

import java.util.TreeSet;

import android.content.ContentValues;
import android.database.Cursor;

import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder.UnrecognizedFormatException;

public class EditRhythmSqlImpl extends RhythmSqlImpl implements EditRhythm {

	private static final String LOG_TAG = "KYB-"+EditRhythmSqlImpl.class.getSimpleName();

	// state of this rhythm
	protected RhythmState mCurrentState;
	private RhythmSqlImpl mSourceRhythm;
    private RhythmEditCommand mRhythmEditCommand; // put on here by EditBeatDialogActivity/CopyBeatDialogActivity, removed by EditRhythmActivity

    public EditRhythmSqlImpl(KybSQLiteHelper library, Cursor csr) {
		super(Function.getBlankContext(library.getResourceManager())
				, library, EDIT_RHYTHM_KEY);
        AndroidResourceManager.logv(LOG_TAG, "<init>: #1");
		
		// give the internal id a value, to let rhythmSqlImpl methods know it's a saved instance
		// even though it doesn't have that column on the db
		mId = Long.MAX_VALUE;
		
		readRhythmColumns((RhythmsContentProvider) mLibrary.getRhythms(), csr);
	}

	/**
	 * Get the rhythm columns and also the additional columns for edit rhythm
	 * @param rhythms
	 * @param csr
	 */
	protected void readRhythmColumns(RhythmsContentProvider rhythms, Cursor csr) {

        String sourceKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_SOURCE_RHYTHM_FK));
        if (sourceKey != null) {
            mSourceRhythm = (RhythmSqlImpl) rhythms.lookup(sourceKey);

            // no source rhythm from lookup, then it better be the existing new rhythm
            if (mSourceRhythm == null) {
                mSourceRhythm = rhythms.getExistingNewRhythm();

                if (mSourceRhythm == null) {
                    AndroidResourceManager.loge(LOG_TAG, "readRhythmColumns: source rhythm not found either old or new, creating a new one");
                    mSourceRhythm = rhythms.makeNewRhythmAndSave(Function.getBlankContext(mLibrary.getResourceManager()), BeatTree.BASIC, false);
                }
            }
        }

        // cause sounds to get looked up, in case

		super.readRhythmColumns(csr);

		mCurrentState = RhythmState.valueOf(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_CURRENT_STATE)));
	}

	/**
	 * Constructs an EditRhythm from a source rhythm which could be a New rhythm
	 * or an existing one. State is either New or Saved to start with, and when some
	 * change happens it progresses New -> Unsaved or Saved -> Changed
	 * @param context
	 * @param sourceRhythm
	 * @throws UnrecognizedFormatException 
	 */
	public EditRhythmSqlImpl(Function context, RhythmSqlImpl sourceRhythm) throws UnrecognizedFormatException {
		super(context, (KybSQLiteHelper) sourceRhythm.getLibrary(), EDIT_RHYTHM_KEY);
        AndroidResourceManager.logv(LOG_TAG, "<init>: #2");
		replaceFromRhythm(context, sourceRhythm);

        // give the internal id a value, to let rhythmSqlImpl methods know it's a saved instance
        // even though it doesn't have that column on the db
        mId = Long.MAX_VALUE;
	}
	
	@Override
	public String getName() {
        if (mSourceRhythm != null) {
            return mSourceRhythm.getName();
        }
        else {
            return null;
        }
	}

	@Override
	public void setCurrentState(RhythmState currentState) {
		this.mCurrentState = currentState;
	}

	@Override
	public RhythmState getCurrentState() {
		return mCurrentState;
	}

	@Override
	public void transitionState(Function context, boolean changed) {
		if (changed) {
			if (getCurrentState() == RhythmState.New || getCurrentState() == RhythmState.Unsaved)
				setCurrentState(RhythmState.Unsaved);
			else 
				setCurrentState(RhythmState.Changed);
		}
		else { // saving
			setCurrentState(RhythmState.Saved);
		}
	}

	@Override
	public Rhythm getSourceRhythm() {
		return mSourceRhythm;
	}

	@Override
	public void setCurrentStateAndSource(RhythmState currentState, String sourceKey) {
		String msg = "setCurrentStateAndSource: not for sql implementation";
		AndroidResourceManager.loge(LOG_TAG, msg);
		throw new UnsupportedOperationException(msg);
	}

	@Override
	public void replaceFromRhythm(Function context, Rhythm copyRhythm)
			throws UnrecognizedFormatException {

        mSourceRhythm = (RhythmSqlImpl) copyRhythm;

        mEncodedBeatTypes = mSourceRhythm.mEncodedBeatTypes;
        mFullBeatNumber1 = mSourceRhythm.mFullBeatNumber1;
        mBpm = mSourceRhythm.mBpm;

        // set state
        if (mLibrary.getRhythms().lookup(copyRhythm.getKey()) == null) {
            setCurrentState(RhythmState.New);
        }
        else {
            setCurrentState(RhythmState.Saved);
        }
	}

    /**
	 * Has its own special update method in the rhythms content provider
	 */
	@Override
	protected void updateDb(ContentValues values) {
		RhythmsContentProvider rhythms = (RhythmsContentProvider) mLibrary.getRhythms();
		rhythms.updateEditRhythmToDb(this);
        updateDbNanoTime();
	}

	@Override
	public void updateDb(boolean includeExternalKey) {
		updateDb(null);
	}

	@Override
	public TreeSet<RhythmBeatType> getCachedRhythmBeatTypes() {
	    if (!hasBeatDataCache()) {
	        throw new NullPointerException("getCachedRhythmBeatTypes: no current cache available");
        }

        return mBeatDataCache.getKey();
    }

    public void putCommand(RhythmEditCommand rhythmEditCommand) {
        mRhythmEditCommand = rhythmEditCommand;
    }

    public RhythmEditCommand takeCommand() {
        RhythmEditCommand rhythmEditCommand = mRhythmEditCommand;
        mRhythmEditCommand = null;
        return rhythmEditCommand;
    }



}
