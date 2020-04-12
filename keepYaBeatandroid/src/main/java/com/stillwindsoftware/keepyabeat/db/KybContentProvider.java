package com.stillwindsoftware.keepyabeat.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.control.UndoableCommand;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypeMemento;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.RhythmMemento;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.SoundMemento;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand.*;
import com.stillwindsoftware.keepyabeat.model.transactions.CloneRhythmCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.PlayerStateCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmBeatTypeCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor.*;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.TagsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidSoundResource;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

/**
 * Superclass for content providers, manages only having one instance of the library.
 * The first content provider to be initialised will also initialise the library.
 * @author tomas stubbs
 */
public abstract class KybContentProvider extends ContentProvider {

	private static final String LOG_TAG = "KYB-"+KybContentProvider.class.getSimpleName();

    // caller pops this immediately after it gets set
    private UnrecoverableUndoCommandDataError mPopFirstUndoDataError;

    // sometimes an error may be severe when trying to inflate an undo command and can't be undone
    // it has to be removed
    public static class UnrecoverableUndoCommandDataError extends Exception {
        public UnrecoverableUndoCommandDataError(String detailMessage) {
            super(detailMessage);
        }
    }

	// common format strings
	public static final String COLUMN_MATCH_STRING_FORMAT = "%s = '%s'";
	public static final String COLUMN_LIKE_STRING_FORMAT = "%s like '%s%s'";
	protected static final String COLUMN_MATCH_NON_STRING_FORMAT = "%s = %s";

    // memento types/keys
    public static final int MEMENTO_STACK_KEY_GENERAL = 0;
    public static final int MEMENTO_STACK_KEY_EDITOR = 1;
    protected static final int MEMENTO_TYPE_NEW_RHYTHM = 0;
    private static final int MEMENTO_TYPE_CHANGE_BEAT_NAME_AND_SOUND = 1;
    private static final int MEMENTO_TYPE_CHANGE_BEAT_TYPE_COLOUR = 2;
    private static final int MEMENTO_TYPE_CHANGE_BEAT_TYPE_SOUND = 3;
    private static final int MEMENTO_TYPE_CHANGE_BEAT_TYPE_FALLBACK_SOUND = 4;
    private static final int MEMENTO_TYPE_ADD_BEAT_TYPE = 5;
    private static final int MEMENTO_TYPE_DELETE_BEAT_TYPE = 6;
    private static final int MEMENTO_CHILD_TYPE_CLONED_BEATS = 7;
    private static final int MEMENTO_TYPE_ADD_SOUND = 8;
    private static final int MEMENTO_TYPE_CHANGE_SOUND_NAME = 9;
    private static final int MEMENTO_TYPE_DELETE_SOUND = 10;
    private static final int MEMENTO_CHILD_TYPE_BEAT_TYPE = 11;
    private static final int MEMENTO_TYPE_RHYTHM_NUMBER_1 = 12;
    private static final int MEMENTO_TYPE_RHYTHM_BPM = 13;
    private static final int MEMENTO_TYPE_SAVE_RHYTHM_AND_TAGS = 14;
    private static final int MEMENTO_TYPE_DELETE_RHYTHM = 15;
    private static final int MEMENTO_TYPE_SAVE_EDIT_NEW_RHYTHM = 16;
    private static final int MEMENTO_TYPE_SAVE_EDIT_RHYTHM = 17;
    private static final int MEMENTO_TYPE_CLONE_RHYTHM = 18;
    private static final int MEMENTO_TYPE_CHANGE_REPEATS = 19;
    private static final int MEMENTO_TYPE_RBT_CHANGE_VOLUME = 20;
    private static final int MEMENTO_TYPE_RBT_CHANGE_DISPLAY_NUMBERS = 21;
    private static final int MEMENTO_TYPE_RBT_CHANGE_SOUND = 22;
    private static final int MEMENTO_TYPE_RBT_CHANGE_COLOUR = 23;
    private static final int MEMENTO_TYPE_ADD_TAG = 24;
    private static final int MEMENTO_TYPE_CHANGE_TAG_NAME = 25;
    private static final int MEMENTO_TYPE_DELETE_TAG = 26;
    private static final int MEMENTO_CHILD_TYPE_RHYTHM_KEYS = 27;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_ChangeNumberOfFullBeats = 28;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatValue = 29;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_DeleteFullBeat = 32;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_MoveFullBeatInRhythm = 34;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_ReplaceFullBeatWithFullBeat = 35;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatType = 37;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_AddNewFullBeat = 38;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_CloneFullBeat = 44;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatDisplayedNumber = 45;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_RhythmEditBeatParts = 46;
    private static final int MEMENTO_TYPE_EDIT_RHYTHM_CopyFullBeat = 47;

    protected static final String[] MEMENTO_COLUMNS = new String[] {
            KybSQLiteHelper.COLUMN_ID
            ,KybSQLiteHelper.COLUMN_MEMENTO_TYPE
            ,KybSQLiteHelper.COLUMN_MEMENTO_STACK_TYPE
            ,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
            ,KybSQLiteHelper.COLUMN_MEMENTO_STRING1
            ,KybSQLiteHelper.COLUMN_MEMENTO_STRING2
            ,KybSQLiteHelper.COLUMN_MEMENTO_STRING3
            ,KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1
            ,KybSQLiteHelper.COLUMN_MEMENTO_INTEGER2
            ,KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3
    };

    protected static final String[] MEMENTO_CHILD_COLUMNS = new String[] {
            KybSQLiteHelper.COLUMN_MEMENTO_TYPE
            ,KybSQLiteHelper.COLUMN_EXTERNAL_KEY
            ,KybSQLiteHelper.COLUMN_MEMENTO_STRING1
            ,KybSQLiteHelper.COLUMN_MEMENTO_STRING2
            ,KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1
            ,KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3
    };

    // the helper is the library
	protected KybSQLiteHelper mLibrary;

	public KybContentProvider() {
		AndroidResourceManager.logd(LOG_TAG, "init");
		
	}
	
	@Override
	public boolean onCreate() {
		KybApplication application = ((KybApplication)getContext());
		AndroidResourceManager resourceManager = application.getResourceManager();
		if ((mLibrary = (KybSQLiteHelper) resourceManager.getLibrary()) == null) {
			AndroidResourceManager.logd(LOG_TAG, String.format("onCreate: library initialised in call (%s)", this.getClass().getSimpleName()));
			new KybSQLiteHelper(application);
			mLibrary = (KybSQLiteHelper) resourceManager.getLibrary();
		}

		return false;
	}

	public Library getLibrary() {
		return mLibrary;
	}

	@Override
	public String getType(Uri uri) {
		return null;
	}

	/**
	 * Overloads query, allowing other methods to call it without a uri
	 * @param view
	 * @param projection
	 * @param selection
	 * @param selectionArgs
	 * @param sortOrder
	 * @return
	 */
	protected Cursor query(String view, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

        SQLiteDatabase db = mLibrary.getWritableDatabase();
		Cursor cursor = db.query(view
				, projection, selection, selectionArgs
				, null, null, sortOrder);
		
	    return cursor;
	}

	/**
	 * Generalized version of next method
	 * @param columnName
     * @param values
	 * @return
	 */
    protected StringBuilder makeInClauseFromSet(String columnName, HashSet<String> values) {

        StringBuilder sb = new StringBuilder(columnName)
                .append(" in (");

        int i = 0;
        for (String value : values) {
            if (i++ > 0) {
                sb.append(',');
            }

            sb.append("'")
                .append(value)
                .append("'");
        }

        sb.append(')');
        return sb;
    }

    protected StringBuilder makeExternalKeyInClauseFromSet(HashSet<String> keys) {

        return makeInClauseFromSet(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, keys);
    }

    /**
     * Store the command in the mementos tables and cull and old commands in the stack that
     * are beyond the allowed number
     * @param stackKey
     * @param maxHistory
     * @param command
     */
    public void storeUndoableCommand(int stackKey, int maxHistory, UndoableCommand command) throws Exception {

        ContentValues values = new ContentValues();
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STACK_TYPE, stackKey);

        Object[] packed = command.packForStorage();

        ContentValues[] childValues = null;

        // populate the values for each type of command, return any additional child values needed
        if (command instanceof ChangeBeatTypeCommand) {
            makeChangeBeatTypeCommandContentValues(command, values, packed);
        }
        else if (command instanceof AddBeatType) {
            makeSimpleKeyContentValues(values, (String)packed[0], MEMENTO_TYPE_ADD_BEAT_TYPE);
        }
        else if (command instanceof DeleteBeatType) {
            childValues = makeDeleteBeatTypeCommandContentValues(values, packed);
        }
        else if (command instanceof AddCustomSound) {
            makeSimpleKeyContentValues(values, (String)packed[0], MEMENTO_TYPE_ADD_SOUND);
        }
        else if (command instanceof ChangeSoundName) {
            makeChangeSoundNameCommandContentValues(values, packed);
        }
        else if (command instanceof DeleteSound) {
            childValues = makeDeleteSoundCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmNumberOneCommand) {
            makeRhythmNumberOneCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmChangeBpmCommand) {
            makeRhythmChangeBpmCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmSaveCommand) {
            makeRhythmSaveCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmDeleteCommand) {
            makeRhythmDeleteCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmSaveEditCommand) {
            makeRhythmSaveEditCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmNewSaveEditCommand) {
            makeSimpleKeyContentValues(values, (String) packed[0], MEMENTO_TYPE_SAVE_EDIT_NEW_RHYTHM);
        }
        else if (command instanceof CloneRhythmCommand) {
            makeSimpleKeyContentValues(values, (String) packed[0], MEMENTO_TYPE_CLONE_RHYTHM);
        }
        else if (command instanceof PlayerStateCommand) {
            makeChangeRepeatsCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmBeatTypeCommand.ChangeVolumeCommand) {
            makeRbtChangeVolumeCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmBeatTypeCommand.ChangeDisplayNumbersCommand) {
            makeRbtChangeDisplayNumbersCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmBeatTypeCommand.ChangeBeatSound) {
            makeRbtChangeBeatSoundCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmBeatTypeCommand.ChangeBeatColour) {
            makeRbtChangeBeatColourCommandContentValues(values, packed);
        }
        else if (command instanceof TagsCommand.AddTag) {
            makeSimpleKeyContentValues(values, (String)packed[0], MEMENTO_TYPE_ADD_TAG);
        }
        else if (command instanceof TagsCommand.ChangeTagName) {
            makeChangeTagNameCommandContentValues(values, packed);
        }
        else if (command instanceof TagsCommand.DeleteTag) {
            childValues = makeDeleteTagCommandContentValues(values, packed);
        }
        else if (command instanceof RhythmEditCommand) { // superclass of all the possible editor changes
            makeRhythmEditCommandContentValues(values, packed, (RhythmEditCommand) command);
        }

        else {
            throw new IllegalArgumentException("storeUndoableCommand: command not implemented "+command);
        }

        // store the memento
        SQLiteDatabase db = mLibrary.getWritableDatabase();
        db.beginTransaction(); // just for these changes
        Cursor csr = null;

        try {
            long mementoId = db.insert(KybSQLiteHelper.TABLE_MEMENTOS, null, values);

            // store the children (if any) referencing the main memento
            if (childValues != null) {
                for (int i = 0; i < childValues.length; i++) {
                    ContentValues cv = childValues[i];
                    cv.put(KybSQLiteHelper.COLUMN_MEMENTO_FK, mementoId);
                    db.insert(KybSQLiteHelper.TABLE_MEMENTO_CHILDREN, null, cv);
                }
            }

            // test for need to remove LRU
            String selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_STACK_TYPE, stackKey);
            csr = query(KybSQLiteHelper.TABLE_MEMENTOS, new String[] { KybSQLiteHelper.COLUMN_ID }, selection, null, KybSQLiteHelper.COLUMN_ID + " DESC");
            if (csr.getCount() > maxHistory) {
                int cull = csr.getCount() - maxHistory;
                AndroidResourceManager.logd(LOG_TAG, "storeUndoableCommand: exceeded maxHistory, culling rows="+cull);
                csr.moveToFirst();
                while (cull-- > 0) {
                    long id = csr.getLong(0);
                    AndroidResourceManager.logd(LOG_TAG, "storeUndoableCommand: culling row with id="+id);
                    String del = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, id);
                    db.delete(KybSQLiteHelper.TABLE_MEMENTOS, del, null);
                    csr.moveToNext();
                }
            }

            db.setTransactionSuccessful();

        }
        catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "storeUndoableCommand: unexpected error", e);
        }
        finally {
            if (csr != null)
                csr.close();
            db.endTransaction(); // commit or rollback, depends if successful
        }
    }

    private void makeRhythmEditCommandContentValues(ContentValues values, Object[] packed, RhythmEditCommand command) throws Exception {
        // most of them have the same undo values
        // clonedMemento, originalFullBeat1, changedFullBeat1, prevEditRhythmState
        RhythmMemento.ClonedBeatsMemento clonedBeatsMemento = (RhythmMemento.ClonedBeatsMemento) packed[0];
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, clonedBeatsMemento.getEncodedBeatsString());

        int originalFullBeat1 = (Integer) packed[1];
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, originalFullBeat1);

        int changedFullBeat1 = (Integer) packed[2];
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER2, changedFullBeat1);

        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING2, ((EditRhythm.RhythmState) packed[3]).name());

        int mementoType;

        if (command instanceof RhythmEditCommand.ChangeNumberOfFullBeats) {
            // clonedMemento, originalFullBeat1, changedFullBeat1, prevEditRhythmState, prevBeatType
            String beatTypeKey = ((BeatType)packed[4]).getKey();
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, beatTypeKey);
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_ChangeNumberOfFullBeats;
        }
        else if (command instanceof RhythmEditCommand.ChangeFullBeatValue) {
            // clonedMemento, originalFullBeat1, changedFullBeat1, prevEditRhythmState, isMakeIrregular
            String isMakeIrregular = packed[4].toString();
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, isMakeIrregular);
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatValue;
        }
        else if (command instanceof RhythmEditCommand.ChangeFullBeatDisplayedNumber) {
            // clonedMemento, originalFullBeat1, position, prevEditRhythmState, isCountChanged
            String isCountChanged = packed[4].toString();
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, isCountChanged);
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatDisplayedNumber;
        }
        else if (command instanceof RhythmEditCommand.RhythmEditBeatPartsCommand) {
            // clonedMemento, originalFullBeat1, changedFullBeat1, prevEditRhythmState, undoSoundsMementoString, undoVolumesMementoString
            String undoSoundsMementoString = (String) packed[4];
            String undoVolumesMementoString = (String) packed[5];
            // concatenate the 2 strings since there's only one more memento string to play with
            String concatUndos = undoSoundsMementoString +
                    RhythmEncoder.ENC_LIST_SEPARATOR +
                    undoVolumesMementoString +
                    RhythmEncoder.ENC_LIST_SEPARATOR;
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, concatUndos);
            AndroidResourceManager.logd(LOG_TAG, "makeRhythmEditCommandContentValues: rhythmEditBeatParts undo sounds/volumes concat string="+concatUndos);

            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_RhythmEditBeatParts;
        }
        else if (command instanceof RhythmEditCommand.DeleteFullBeat) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_DeleteFullBeat;
        }
        else if (command instanceof RhythmEditCommand.MoveFullBeatInRhythm) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_MoveFullBeatInRhythm;
        }
        else if (command instanceof RhythmEditCommand.ReplaceFullBeatWithFullBeat) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_ReplaceFullBeatWithFullBeat;
        }
        else if (command instanceof RhythmEditCommand.ChangeFullBeatType) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatType;
        }
        else if (command instanceof RhythmEditCommand.AddNewFullBeat) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_AddNewFullBeat;
        }
        else if (command instanceof RhythmEditCommand.CloneFullBeat) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_CloneFullBeat;
        }
        else if (command instanceof RhythmEditCommand.CopyFullBeat) {
            mementoType = MEMENTO_TYPE_EDIT_RHYTHM_CopyFullBeat;
        }
        else {
            throw new Exception("makeRhythmEditCommandContentValues: command not implemented "+command);
        }

        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, mementoType);
    }

    private ContentValues[] makeDeleteTagCommandContentValues(ContentValues values, Object[] packed) {
        //tag, rhythmFks, isTagInRhythmsSearchList

        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_DELETE_TAG);

        Tag tag = (Tag) packed[0];

        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, tag.getKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, tag.getName());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING2, packed[2].toString());

        String[] rhythmFks = (String[]) packed[1];

        if (rhythmFks == null || rhythmFks.length == 0) {
            return null;
        }

        ContentValues[] childValues = new ContentValues[rhythmFks.length];

        // make the child values, one per rhythm
        for (int i = 0; i < rhythmFks.length; i++) {
            ContentValues cv = new ContentValues();
            cv.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_CHILD_TYPE_RHYTHM_KEYS);
            cv.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, rhythmFks[i]);
            childValues[i] = cv;
        }

        return childValues;
    }

    private void makeChangeTagNameCommandContentValues(ContentValues values, Object[] packed) {
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_CHANGE_TAG_NAME);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, (String)packed[0]);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, (String)packed[1]);
    }

    private void makeRbtChangeBeatColourCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm.getKey(), prevEditRhythmState, rBeatType, originalColour
        makeRbtCommandContentValues(values, packed, MEMENTO_TYPE_RBT_CHANGE_COLOUR);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, ((AndroidColour)packed[3]).getColorInt());
    }

    private void makeRbtChangeBeatSoundCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm.getKey(), prevEditRhythmState, rBeatType, originalSound
        makeRbtCommandContentValues(values, packed, MEMENTO_TYPE_RBT_CHANGE_SOUND);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, ((Sound)packed[3]).getKey());
    }

    private void makeRbtChangeDisplayNumbersCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm.getKey(), prevEditRhythmState, rBeatType, originalSetting
        makeRbtCommandContentValues(values, packed, MEMENTO_TYPE_RBT_CHANGE_DISPLAY_NUMBERS);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, packed[3].toString());
    }

    private void makeRbtChangeVolumeCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm.getKey(), prevEditRhythmState, rBeatType, originalVolume
        makeRbtCommandContentValues(values, packed, MEMENTO_TYPE_RBT_CHANGE_VOLUME);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, (Integer) packed[3]);
    }

    private void makeRbtCommandContentValues(ContentValues values, Object[] packed, int mementoType) {
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, mementoType);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, (String)packed[0]);
        if (packed[1] != null) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING2, ((EditRhythm.RhythmState) packed[1]).name());
        }
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, ((RhythmBeatType) packed[2]).getBeatType().getKey());
    }

    private void makeChangeRepeatsCommandContentValues(ContentValues values, Object[] packed) {
        // optionOld, nTimesOld
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_CHANGE_REPEATS);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, ((PlayerState.RepeatOption) packed[0]).name());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, (Integer) packed[1]);
    }

    private void makeRhythmSaveEditCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm.getKey(), rhythmMemento
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_SAVE_EDIT_RHYTHM);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, (String)packed[0]);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, ((RhythmMemento.ClonedRhythmMemento) packed[1]).getEncodedRhythmString());
    }

    private void makeRhythmDeleteCommandContentValues(ContentValues values, Object[] packed) {
        // rhythmMemento
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_DELETE_RHYTHM);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, ((RhythmMemento.ClonedRhythmMemento) packed[0]).getEncodedRhythmString());
    }

    private void makeRhythmSaveCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm, isNewRhythm, rhythmMemento
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_SAVE_RHYTHM_AND_TAGS);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, ((Rhythm)packed[0]).getKey());
        if (!((Boolean) packed[1])) { // not new, need to save the rhythm, otherwise it will just be a delete
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1,
                ((RhythmMemento.ClonedRhythmMemento) packed[2]).getEncodedRhythmString());
        }
    }

    private void makeRhythmChangeBpmCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm, oldBpm, and only for edit rhythm: prevEditRhythmState
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_RHYTHM_BPM);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, ((Rhythm)packed[0]).getKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, (Integer)packed[1]);
        if (packed.length >= 3) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, ((EditRhythm.RhythmState) packed[2]).name());
        }
    }

    private void makeRhythmNumberOneCommandContentValues(ContentValues values, Object[] packed) {
        // rhythm, prevEditRhythmState, oldNumberOne
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_RHYTHM_NUMBER_1);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, ((Rhythm)packed[0]).getKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, ((EditRhythm.RhythmState)packed[1]).name());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, (Integer)packed[2]);
    }

    private ContentValues[] makeDeleteSoundCommandContentValues(ContentValues values, Object[] packed) {

        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_DELETE_SOUND);

        SoundMemento soundMemento = (SoundMemento) packed[0];

        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, soundMemento.getKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, soundMemento.getName());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3, soundMemento.getDuration());
        AndroidSoundResource resource = (AndroidSoundResource) soundMemento.getSoundResource();
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING2, resource.getUriPath());

        ArrayList<String> beatFks = soundMemento.getFkBeatTypeKeys();
        HashMap<String, RhythmMemento.ClonedBeatsMemento> refRhythms = soundMemento.getChangedRhythmsMementos();

        if (beatFks == null && refRhythms == null) { // quick out if none of either
            return null;
        }

        ContentValues[] childValues = new ContentValues[(beatFks == null ? 0 : beatFks.size()) + (refRhythms == null ? 0 : refRhythms.size())];
        int sub = 0;

        // changed beat types, just have the keys
        if (beatFks != null) {
            for (int k = 0; k < beatFks.size(); k++) {
                ContentValues cv = new ContentValues();
                cv.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_CHILD_TYPE_BEAT_TYPE);
                cv.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, beatFks.get(k));
                childValues[sub++] = cv;
            }
        }

        // make the child values, one per rbt
        if (refRhythms != null) {
            for (Map.Entry<String, RhythmMemento.ClonedBeatsMemento> entry : refRhythms.entrySet()) {
                ContentValues cv = new ContentValues();
                cv.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_CHILD_TYPE_CLONED_BEATS);
                cv.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, entry.getKey());
                cv.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, entry.getValue().getEncodedBeatsString());
                childValues[sub++] = cv;
            }
        }

        return childValues;
    }

    private void makeChangeSoundNameCommandContentValues(ContentValues values, Object[] packed) {
        //undoSoundKey, originalName
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_CHANGE_SOUND_NAME);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, (String)packed[0]);
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, (String)packed[1]);
    }

    private void makeChangeBeatTypeCommandContentValues(UndoableCommand command, ContentValues values, Object[] packed) throws Exception {
        // beatType, whichPartsChanged, undoName, undoColour, undoVolume, undoSoundKey, undoFallbackSoundKey
        int whichParts = (Integer) packed[1];
        boolean nameChanged = (whichParts & BeatTypesContentProvider.NAME_CHANGED) == BeatTypesContentProvider.NAME_CHANGED;
        boolean colourChanged = (whichParts & BeatTypesContentProvider.COLOUR_CHANGED) == BeatTypesContentProvider.COLOUR_CHANGED;
        boolean volumeChanged = (whichParts & BeatTypesContentProvider.VOLUME_CHANGED) == BeatTypesContentProvider.VOLUME_CHANGED;
        boolean soundChanged = (whichParts & BeatTypesContentProvider.SOUND_CHANGED) == BeatTypesContentProvider.SOUND_CHANGED;
        boolean fallbackSoundChanged = (whichParts & BeatTypesContentProvider.FALLBACK_SOUND_CHANGED) == BeatTypesContentProvider.FALLBACK_SOUND_CHANGED;

        int type = (command instanceof ChangeBeatNameAndSound ? MEMENTO_TYPE_CHANGE_BEAT_NAME_AND_SOUND
                : command instanceof ChangeBeatColour ? MEMENTO_TYPE_CHANGE_BEAT_TYPE_COLOUR
                : command instanceof ChangeBeatSound ? MEMENTO_TYPE_CHANGE_BEAT_TYPE_SOUND
                : command instanceof ChangeBeatFallbackSound ? MEMENTO_TYPE_CHANGE_BEAT_TYPE_FALLBACK_SOUND
                : -1);
        if (type == -1) {
            throw new Exception("makeChangeBeatTypeCommandContentValues: command not implemented "+command);
        }

        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, type);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, ((BeatType)packed[0]).getKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, whichParts);
        if (nameChanged) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, (String) packed[2]);
        }
        if (colourChanged) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER2, ((AndroidColour) packed[3]).getColorInt());
        }
        if (volumeChanged) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3, (Float) packed[4]);
        }
        if (soundChanged) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING2, (String) packed[5]);
        }
        if (fallbackSoundChanged) {
            values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, (String) packed[6]);
        }
    }

    private void makeSimpleKeyContentValues(ContentValues values, String key, int mementoType) throws Exception {
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, mementoType);
        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, key);
    }

    private ContentValues[] makeDeleteBeatTypeCommandContentValues(ContentValues values, Object[] packed) throws Exception {
        // beatTypeMemento
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_TYPE_DELETE_BEAT_TYPE);

        BeatTypeMemento beatTypeMemento = (BeatTypeMemento) packed[0];

        values.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, beatTypeMemento.getKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, beatTypeMemento.getName());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING2, beatTypeMemento.getSoundKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING3, beatTypeMemento.getFallbackSoundKey());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1, ((AndroidColour)beatTypeMemento.getColour()).getColorInt());
        values.put(KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3, beatTypeMemento.getVolume());

        HashMap<String, RhythmMemento.ClonedBeatsMemento> rhythmBeatsMementos = beatTypeMemento.getRhythmBeatsMementos();

        if (rhythmBeatsMementos == null || rhythmBeatsMementos.isEmpty()) {
            return null;
        }

        ContentValues[] childValues = new ContentValues[rhythmBeatsMementos.size()];
        int sub = 0;

        // make the child values, one per rbt
        for (Iterator<String> it = rhythmBeatsMementos.keySet().iterator(); it.hasNext(); ) {
            String rhythmKey = it.next();

            ContentValues cv = new ContentValues();
            cv.put(KybSQLiteHelper.COLUMN_MEMENTO_TYPE, MEMENTO_CHILD_TYPE_CLONED_BEATS);
            cv.put(KybSQLiteHelper.COLUMN_EXTERNAL_KEY, rhythmKey);
            cv.put(KybSQLiteHelper.COLUMN_MEMENTO_STRING1, rhythmBeatsMementos.get(rhythmKey).getEncodedBeatsString());
            childValues[sub++] = cv;
        }

        return childValues;
    }

    /**
     * Gets the last stored command for the type of stack and removes it from the db
     * @param stackKey
     * @return
     */
    public UndoableCommand popUndoableCommand(int stackKey) throws Exception {
        return getNextUndoableCommand(stackKey, true, false); // do pop it too, but don't check for consistency
    }

    /**
     * Get the next command in the stack, but provided it's still consistent,
     * leave it there and only return the description.
     * @param stackKey
     * @return
     */
    public String peekNextUndoableCommand(int stackKey) throws Exception {
        UndoableCommand command = getNextUndoableCommand(stackKey, false, true); // don't pop it, but check for consistency
        if (command != null) {
            return command.getDesc();
        }

        return null;
    }

//    /**
//     * Get the next command in the stack, but provided it's still consistent,
//     * leave it there and only return the description.
//     * @param stackKey
//     * @param bypassInconsistent
//     * @return
//     */
//    public String peekNextUndoableCommand(int stackKey, boolean bypassInconsistent) throws Exception {
//        UndoableCommand command = getNextUndoableCommand(stackKey, false, bypassInconsistent); // don't pop it
//        if (command != null) {
//            return command.getDesc();
//        }
//
//        return null;
//    }

    private UndoableCommand getNextUndoableCommand(int stackKey, boolean popIt, boolean bypassInconsistent) throws Exception {

        UnrecoverableUndoCommandDataError firstDataError = null;
        if (mPopFirstUndoDataError != null) {
            AndroidResourceManager.logw(LOG_TAG, "getNextUndoableCommand: mPopFirstUndoDataError should be taken immediately, but persists");
        }

        SQLiteDatabase db = mLibrary.getWritableDatabase();
        db.beginTransaction(); // just for these changes

        String selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_STACK_TYPE, stackKey);
        Cursor csr = query(KybSQLiteHelper.TABLE_MEMENTOS, MEMENTO_COLUMNS, selection, null, KybSQLiteHelper.COLUMN_ID + " DESC");

        try {
            if (csr.getCount() == 0) {
                if (popIt) {
                    AndroidResourceManager.loge(LOG_TAG, "getNextUndoableCommand: there are no commands to undo (should be trapped before calling this)");
                }
                return null;
            }

            UndoableCommand command = null;

            boolean complete = false;
            csr.moveToFirst();

            do {
                int mementoType = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_TYPE));
                long mementoId = csr.getLong(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID));

                try {
                    switch (mementoType) {
                        case MEMENTO_TYPE_CHANGE_BEAT_NAME_AND_SOUND:
                        case MEMENTO_TYPE_CHANGE_BEAT_TYPE_COLOUR:
                        case MEMENTO_TYPE_CHANGE_BEAT_TYPE_SOUND:
                        case MEMENTO_TYPE_CHANGE_BEAT_TYPE_FALLBACK_SOUND:
                            command = inflateChangeBeatTypeCommand(csr, mementoType);
                            break;
                        case MEMENTO_TYPE_ADD_BEAT_TYPE:
                            command = inflateAddBeatTypeCommand(csr);
                            break;
                        case MEMENTO_TYPE_DELETE_BEAT_TYPE:
                            command = inflateDeleteBeatTypeCommand(csr);
                            break;
                        case MEMENTO_TYPE_ADD_SOUND:
                            command = inflateAddSoundCommand(csr);
                            break;
                        case MEMENTO_TYPE_CHANGE_SOUND_NAME:
                            command = inflateChangeSoundNameCommand(csr);
                            break;
                        case MEMENTO_TYPE_DELETE_SOUND:
                            command = inflateDeleteSoundCommand(csr);
                            break;
                        case MEMENTO_TYPE_RHYTHM_NUMBER_1:
                            command = inflateRhythmNumberOneCommand(csr);
                            break;
                        case MEMENTO_TYPE_RHYTHM_BPM:
                            command = inflateRhythmBpmCommand(csr);
                            break;
                        case MEMENTO_TYPE_SAVE_RHYTHM_AND_TAGS:
                            command = inflateSaveRhythmCommand(csr);
                            break;
                        case MEMENTO_TYPE_DELETE_RHYTHM:
                            command = inflateDeleteRhythmCommand(csr);
                            break;
                        case MEMENTO_TYPE_SAVE_EDIT_RHYTHM:
                            command = inflateRhythmSaveEditCommand(csr);
                            break;
                        case MEMENTO_TYPE_SAVE_EDIT_NEW_RHYTHM:
                            command = inflateRhythmNewSaveEditCommand(csr);
                            break;
                        case MEMENTO_TYPE_CLONE_RHYTHM:
                            command = inflateCloneRhythmCommand(csr);
                            break;
                        case MEMENTO_TYPE_CHANGE_REPEATS:
                            command = inflateChangeRepeatsCommand(csr);
                            break;
                        case MEMENTO_TYPE_RBT_CHANGE_VOLUME:
                        case MEMENTO_TYPE_RBT_CHANGE_DISPLAY_NUMBERS:
                        case MEMENTO_TYPE_RBT_CHANGE_SOUND:
                        case MEMENTO_TYPE_RBT_CHANGE_COLOUR:
                            command = inflateRbtCommand(csr, mementoType);
                            break;
                        case MEMENTO_TYPE_ADD_TAG:
                            command = inflateAddTagCommand(csr);
                            break;
                        case MEMENTO_TYPE_CHANGE_TAG_NAME:
                            command = inflateChangeTagNameCommand(csr);
                            break;
                        case MEMENTO_TYPE_DELETE_TAG:
                            command = inflateDeleteTagCommand(csr);
                            break;
                        case MEMENTO_TYPE_EDIT_RHYTHM_ChangeNumberOfFullBeats:
                        case MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatValue:
                        case MEMENTO_TYPE_EDIT_RHYTHM_DeleteFullBeat:
                        case MEMENTO_TYPE_EDIT_RHYTHM_MoveFullBeatInRhythm:
                        case MEMENTO_TYPE_EDIT_RHYTHM_ReplaceFullBeatWithFullBeat:
                        case MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatType:
                        case MEMENTO_TYPE_EDIT_RHYTHM_AddNewFullBeat:
                        case MEMENTO_TYPE_EDIT_RHYTHM_CloneFullBeat:
                        case MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatDisplayedNumber:
                        case MEMENTO_TYPE_EDIT_RHYTHM_RhythmEditBeatParts:
                        case MEMENTO_TYPE_EDIT_RHYTHM_CopyFullBeat:
                            command = inflateRhythmEditCommand(csr, mementoType);
                            break;

                        default:
                            throw new UnrecoverableUndoCommandDataError("getNextUndoableCommand: command not implemented " + mementoType);
                    }
                }
                catch (UnrecoverableUndoCommandDataError e) {
                    // command is currently null and will be dropped
                    if (firstDataError == null) { // warn the user of the error at the end
                        firstDataError = e;
                    }
                }

                // when peeking the next description, might have to ignore the current one and move to next
                boolean dropIt = bypassInconsistent && (command == null || !command.isUndoStillPossible());

                // cascade delete the memento (because popping or because found inconsistent)
                if (popIt || dropIt) {
                    String del = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_ID, mementoId);
                    db.delete(KybSQLiteHelper.TABLE_MEMENTOS, del, null); // child mementos cascade
                }

                complete = !dropIt; // if didn't drop it, must be complete

                if (!complete) {
                    AndroidResourceManager.logd(LOG_TAG, String.format("getNextUndoableCommand: bypassed the top command (%s), moving to next",
                            command == null ? "not available" : command.getDesc()));
                    csr.moveToNext();
                    complete = csr.isAfterLast();
                }
            }
            while (!complete);

            db.setTransactionSuccessful();

            if (firstDataError != null && popIt) { // ie. wanted to undo an error explicitly but unable
                AndroidResourceManager.logw(LOG_TAG, "getNextUndoableCommand: bypassed and deleted command, due to data error");
                mPopFirstUndoDataError = firstDataError;
            }

            return command;
        }
        catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "getNextUndoableCommand: unexpected error", e);
            throw e;
        }
        finally {
            if (csr != null)
                csr.close();
            db.endTransaction(); // commit or rollback, depends if successful
        }
    }

    public UnrecoverableUndoCommandDataError takeLastUndoDataError() {
        UnrecoverableUndoCommandDataError val = mPopFirstUndoDataError;
        mPopFirstUndoDataError = null;
        return val;
    }

    private UndoableCommand inflateRhythmEditCommand(Cursor csr, int mementoType) throws UnrecoverableUndoCommandDataError {

        // all have the same undo values
        // clonedMemento, originalFullBeat1, changedFullBeat1
        RhythmMemento.ClonedBeatsMemento clonedBeatsMemento =
                new RhythmMemento.ClonedBeatsMemento(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1)));

        int originalFullBeat1 = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1));
        int changedFullBeat1 = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER2));
        EditRhythm.RhythmState undoRhythmState = EditRhythm.RhythmState.valueOf(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING2)));

        // except the few that have extra fields
        if (mementoType == MEMENTO_TYPE_EDIT_RHYTHM_ChangeNumberOfFullBeats) {
            // clonedMemento, originalFullBeat1, changedFullBeat1, prevBeatType
            String beatTypeKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3));
            return new RhythmEditCommand.ChangeNumberOfFullBeats(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1,
                    undoRhythmState, mLibrary.getBeatTypes().lookup(beatTypeKey));
        }
        else if (mementoType == MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatValue) {
            // clonedMemento, originalFullBeat1, changedFullBeat1, isMakeIrregular
            boolean isMakeIrregular = Boolean.parseBoolean(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3)));
            return new RhythmEditCommand.ChangeFullBeatValue(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1,
                    undoRhythmState, isMakeIrregular);
        }
        else if (mementoType == MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatDisplayedNumber) {
            // clonedMemento, originalFullBeat1, position, prevEditRhythmState, isCountChanged
            boolean isCountChanged = Boolean.parseBoolean(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3)));
            return new RhythmEditCommand.ChangeFullBeatDisplayedNumber(clonedBeatsMemento, getEditRhythmForUndo(), isCountChanged, originalFullBeat1, undoRhythmState);
        }
        else if (mementoType == MEMENTO_TYPE_EDIT_RHYTHM_RhythmEditBeatParts) {
            // clonedMemento, originalFullBeat1, changedFullBeat1, prevEditRhythmState, undoSoundsMementoString, undoVolumesMementoString
            String concatedSoundVolumes = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3));
            AndroidResourceManager.logd(LOG_TAG, "inflateRhythmEditCommand: rhythmEditBeatParts undo sounds/volumes concat string="+concatedSoundVolumes);
            String[] soundsVolumes = concatedSoundVolumes.split(RhythmEncoder.ENC_LIST_SEPARATOR);
            AndroidResourceManager.logd(LOG_TAG, "inflateRhythmEditCommand: rhythmEditBeatParts undo sounds/volumes split string[]="+soundsVolumes.length);
            return new RhythmEditCommand.RhythmEditBeatPartsCommand(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, undoRhythmState, soundsVolumes[0], soundsVolumes[1]);
        }
        else {
            switch (mementoType) {
                case MEMENTO_TYPE_EDIT_RHYTHM_DeleteFullBeat:
                    return new RhythmEditCommand.DeleteFullBeat(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                case MEMENTO_TYPE_EDIT_RHYTHM_MoveFullBeatInRhythm:
                    return new RhythmEditCommand.MoveFullBeatInRhythm(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                case MEMENTO_TYPE_EDIT_RHYTHM_ReplaceFullBeatWithFullBeat:
                    return new RhythmEditCommand.ReplaceFullBeatWithFullBeat(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                case MEMENTO_TYPE_EDIT_RHYTHM_ChangeFullBeatType:
                    return new RhythmEditCommand.ChangeFullBeatType(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                case MEMENTO_TYPE_EDIT_RHYTHM_AddNewFullBeat:
                    return new RhythmEditCommand.AddNewFullBeat(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                case MEMENTO_TYPE_EDIT_RHYTHM_CloneFullBeat:
                    return new RhythmEditCommand.CloneFullBeat(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                case MEMENTO_TYPE_EDIT_RHYTHM_CopyFullBeat:
                    return new RhythmEditCommand.CopyFullBeat(clonedBeatsMemento, getEditRhythmForUndo(), originalFullBeat1, changedFullBeat1, undoRhythmState);
                default:
                    throw new UnrecoverableUndoCommandDataError("inflateRhythmEditCommand: command not implemented " + mementoType);
            }
        }

    }

    private UndoableCommand inflateDeleteTagCommand(Cursor csr) {

        String tagKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        String name = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1));
        boolean isTagInRhythmsSearchList = Boolean.parseBoolean(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING2)));

        Cursor childCsr = getMementoChildren(csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));
        String[] rhythmFks = null;

        try {
            if (childCsr.getCount() != 0) {
                AndroidResourceManager.logd(LOG_TAG, String.format("inflateDeleteTagCommand: found %s rhythms", childCsr.getCount()));

                rhythmFks = new String[childCsr.getCount()];
                int sub = 0;

                childCsr.moveToFirst();
                do {
                    int childType = childCsr.getInt(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_TYPE));
                    if (childType == MEMENTO_CHILD_TYPE_RHYTHM_KEYS) {
                        rhythmFks[sub++] = childCsr.getString(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
                    }
                    else {
                        AndroidResourceManager.loge(LOG_TAG, "expected rhythm keys only, found " + childType);
                    }

                    childCsr.moveToNext();
                }
                while (!childCsr.isAfterLast());
            }
        }
        finally {
            childCsr.close();
        }

        return new TagsCommand.DeleteTag(mLibrary.getResourceManager(), new TagSqlImpl(mLibrary, tagKey, name), isTagInRhythmsSearchList, rhythmFks);
    }

    private UndoableCommand inflateAddTagCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        Tag undoTag = mLibrary.getTags().lookup(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
        if (undoTag == null) {
            String msg = "inflateAddTagCommand: can't undo because tag not found for key " + csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new UnrecoverableUndoCommandDataError(msg);
        }

        return new TagsCommand.AddTag(mLibrary.getResourceManager(), undoTag);
    }

    private UndoableCommand inflateChangeTagNameCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        Tag undoTag = mLibrary.getTags().lookup(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
        if (undoTag == null) {
            String msg = "inflateChangeTagNameCommand: can't undo because tag not found for key " + csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new UnrecoverableUndoCommandDataError(msg);
        }

        return new TagsCommand.ChangeTagName(mLibrary.getResourceManager(),
                undoTag, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1)));
    }

    private UndoableCommand inflateRbtCommand(Cursor csr, int mementoType) throws UnrecoverableUndoCommandDataError {
        // this command can be on either stack, and be either a rhythm or edit rhythm
        String rhythmKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        Rhythm rhythm = EditRhythm.EDIT_RHYTHM_KEY.equals(rhythmKey)
                ? getEditRhythmForUndo() : getRhythmFromExternalKey(csr);

        BeatType beatType = mLibrary.getBeatTypes().lookup(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1)));
        if (beatType == null) {
            String msg = "inflateRbtCommand: can't undo because beatType not found for key " + csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1));
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new UnrecoverableUndoCommandDataError(msg);
        }

        String rhythmStateName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING2));
        EditRhythm.RhythmState undoRhythmState = null;
        if (rhythmStateName != null) { // only there when it's an edit rhythm
            undoRhythmState = EditRhythm.RhythmState.valueOf(rhythmStateName);
        }

        // this exact code is also in the command batchUndo(), so will happen again if it's undone...
        TreeSet<RhythmBeatType> rbts = rhythm.getBeatsAllData(null, true, BeatTree.BASIC).getKey();
        RhythmBeatType rBeatType = RhythmBeatType.getRhythmBeatType(beatType, rbts);
        if (rBeatType == null) {
            String msg = "inflateRbtCommand: can't undo because rbt not found for beat type " + beatType.getKey();
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new UnrecoverableUndoCommandDataError(msg);
        }

        if (mementoType == MEMENTO_TYPE_RBT_CHANGE_VOLUME) {
            int undoVolume = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1));
            return new RhythmBeatTypeCommand.ChangeVolumeCommand(rhythm, rBeatType, undoRhythmState, undoVolume);
        }
        else if (mementoType == MEMENTO_TYPE_RBT_CHANGE_DISPLAY_NUMBERS) {
            boolean undoSetting = Boolean.parseBoolean(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3)));
            return new RhythmBeatTypeCommand.ChangeDisplayNumbersCommand(rhythm, rBeatType, undoRhythmState, undoSetting);
        }
        else if (mementoType == MEMENTO_TYPE_RBT_CHANGE_SOUND) {
            String soundKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3));
            Sound undoSound = mLibrary.getSounds().lookup(soundKey);
            if (undoSound == null) {
                String msg = "inflateRbtCommand: can't undo because sound not found for key " + soundKey;
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new UnrecoverableUndoCommandDataError(msg);
            }
            return new RhythmBeatTypeCommand.ChangeBeatSound(rhythm, rBeatType, undoRhythmState, undoSound);
        }
        else if (mementoType == MEMENTO_TYPE_RBT_CHANGE_COLOUR) {
            int undoColourInt = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1));
            return new RhythmBeatTypeCommand.ChangeBeatColour(rhythm, rBeatType, undoRhythmState, new AndroidColour(undoColourInt));
        }
        else {
            throw new UnrecoverableUndoCommandDataError("inflateRbtCommand: unknown RBT undo type="+mementoType);
        }
    }

    private UndoableCommand inflateChangeRepeatsCommand(Cursor csr) {
        return new PlayerStateCommand.ChangeRepeatsCommand(mLibrary.getResourceManager(),
                PlayerState.RepeatOption.valueOf(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1))),
                csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1)));
    }

    private UndoableCommand inflateCloneRhythmCommand(Cursor csr) {
        return new CloneRhythmCommand(mLibrary, mLibrary.getResourceManager(), csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
    }

    private UndoableCommand inflateRhythmSaveEditCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        return new RhythmSaveEditCommand(getRhythmFromExternalKey(csr),
                new RhythmMemento.ClonedRhythmMemento(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1))));
    }

    private UndoableCommand inflateRhythmNewSaveEditCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        return new RhythmNewSaveEditCommand(getRhythmFromExternalKey(csr));
    }

    private UndoableCommand inflateDeleteRhythmCommand(Cursor csr) {
        return new RhythmDeleteCommand(mLibrary,
                new RhythmMemento.ClonedRhythmMemento(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1))));
    }

    private UndoableCommand inflateSaveRhythmCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        Rhythm rhythm = getRhythmFromExternalKey(csr);
        String encodedRhythm = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1));

        if (encodedRhythm != null) {
            RhythmMemento.ClonedRhythmMemento clonedRhythmMemento = new RhythmMemento.ClonedRhythmMemento(encodedRhythm);
            return new RhythmSaveCommand(rhythm, clonedRhythmMemento);
        }
        else {
            return new RhythmSaveCommand(rhythm);
        }
    }

    private UndoableCommand inflateRhythmBpmCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        // this command can be on either stack, and be either a rhythm or edit rhythm
        String rhythmKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        int undoBpm = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1));

        if (EditRhythm.EDIT_RHYTHM_KEY.equals(rhythmKey)) {
            return new RhythmChangeBpmCommand(getEditRhythmForUndo(), undoBpm,
                    EditRhythm.RhythmState.valueOf(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1))));
        }
        else {
            return new RhythmChangeBpmCommand(getRhythmFromExternalKey(csr), undoBpm);
        }
    }

    private UndoableCommand inflateRhythmNumberOneCommand(Cursor csr) throws UnrecoverableUndoCommandDataError {
        // this command can only be on the rhythm editor stack, so the key isn't really even needed
        // but if there's no edit rhythm it's an issue
        EditRhythm rhythm = getEditRhythmForUndo();

        int undoNumberOne = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1));
        EditRhythm.RhythmState undoRhythmState = EditRhythm.RhythmState.valueOf(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1)));

        return new RhythmNumberOneCommand(rhythm, undoNumberOne, undoRhythmState);
    }

    @NonNull
    private EditRhythm getEditRhythmForUndo() throws UnrecoverableUndoCommandDataError {
        final RhythmsContentProvider rhythms = (RhythmsContentProvider) mLibrary.getRhythms();
        try {
            while (!rhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "getEditRhythmForUndo: waiting for lock on player rhythm");
            }

            EditRhythm rhythm = rhythms.getOpenEditRhythm();
            if (rhythm == null) {
                String msg = "getEditRhythmForUndo: no edit rhythm open undo";
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new UnrecoverableUndoCommandDataError(msg);
            }

            return rhythm;

        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "getEditRhythmForUndo: program error", e);
            throw new UnrecoverableUndoCommandDataError("getEditRhythmForUndo: no lock held");
        } finally {
            rhythms.releaseReadLockOnPlayerEditorRhythm();
        }
    }

    @NonNull
    private Rhythm getRhythmFromExternalKey(Cursor csr) throws UnrecoverableUndoCommandDataError {
        String rhythmKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        final RhythmsContentProvider rhythms = (RhythmsContentProvider) mLibrary.getRhythms();

        try {
            while (!rhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "getRhythmFromExternalKey: waiting for lock on player rhythm");
            }

            Rhythm rhythm = rhythms.getPlayerEditorRhythm();
            if (rhythm != null && rhythm.getKey().equals(rhythmKey)) {
                return rhythm;
            }
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "getEditRhythmForUndo: program error", e);
            throw new UnrecoverableUndoCommandDataError("getEditRhythmForUndo: no lock held");
        } finally {
            rhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        Rhythm rhythm = rhythms.lookup(rhythmKey);

        if (rhythm == null) {
            // need to check it's not the new rhythm
            rhythm = rhythms.getExistingNewRhythm();

            if (rhythm == null || !rhythm.getKey().equals(rhythmKey)) {
                String msg = "getRhythmFromExternalKey: rhythm not found on db to undo " + rhythmKey;
                AndroidResourceManager.loge(LOG_TAG, msg);
                throw new UnrecoverableUndoCommandDataError(msg);
            }
        }

        return rhythm;
    }

    private UndoableCommand inflateDeleteSoundCommand(Cursor csr) {
        // SoundMemento
        String soundKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        String soundName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1));
        float duration = csr.getFloat(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3));
        String uriPath = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING2));

        AndroidSoundResource resource = new AndroidSoundResource(PlatformResourceManager.SoundResourceType.URI, duration, uriPath);

        ArrayList<String> beatFks = null;
        HashMap<String, RhythmMemento.ClonedBeatsMemento> rhythmBeatsMementos = null;

        Cursor childCsr = getMementoChildren(csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));

        try {
            if (childCsr.getCount() != 0) {
                AndroidResourceManager.logd(LOG_TAG, String.format("inflateDeleteSoundCommand: found %s rhythm cloned beats and/or beat types", childCsr.getCount()));

                childCsr.moveToFirst();
                do {
                    String key = childCsr.getString(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
                    int childType = childCsr.getInt(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_TYPE));
                    if (childType == MEMENTO_CHILD_TYPE_CLONED_BEATS) {
                        if (rhythmBeatsMementos == null) { // lazy init
                            rhythmBeatsMementos = new HashMap<>();
                        }
                        rhythmBeatsMementos.put(key,
                                new RhythmMemento.ClonedBeatsMemento(childCsr.getString(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1))));
                    }
                    else if (childType == MEMENTO_CHILD_TYPE_BEAT_TYPE) {
                        if (beatFks == null) {
                            beatFks = new ArrayList<>();
                        }
                        beatFks.add(key);
                    }
                    else {
                        AndroidResourceManager.loge(LOG_TAG, "expected cloned beats or beat type keys only, found "+childType);
                    }

                    childCsr.moveToNext();
                }
                while (!childCsr.isAfterLast());
            }
        }
        finally {
            childCsr.close();
        }

        return new DeleteSound(mLibrary, new SoundMemento(soundKey, soundName, resource, duration, beatFks, rhythmBeatsMementos));
    }

    private UndoableCommand inflateChangeSoundNameCommand(Cursor csr) {
        return new ChangeSoundName(mLibrary, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)),
                csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1)));
    }

    private UndoableCommand inflateChangeBeatTypeCommand(Cursor csr, int mementoType) throws UnrecoverableUndoCommandDataError {

        BeatType beatType = mLibrary.getBeatTypes().lookup(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
        if (beatType == null) {
            String msg = "inflateChangeBeatTypeCommand: can't undo because beatType not found for key " + csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new UnrecoverableUndoCommandDataError(msg);
        }
        int whichParts = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1));

        boolean nameChanged = (whichParts & BeatTypesContentProvider.NAME_CHANGED) == BeatTypesContentProvider.NAME_CHANGED;
        boolean colourChanged = (whichParts & BeatTypesContentProvider.COLOUR_CHANGED) == BeatTypesContentProvider.COLOUR_CHANGED;
//        boolean volumeChanged = (whichParts & BeatTypesContentProvider.VOLUME_CHANGED) == BeatTypesContentProvider.VOLUME_CHANGED;
        boolean soundChanged = (whichParts & BeatTypesContentProvider.SOUND_CHANGED) == BeatTypesContentProvider.SOUND_CHANGED;
        boolean fallbackSoundChanged = (whichParts & BeatTypesContentProvider.FALLBACK_SOUND_CHANGED) == BeatTypesContentProvider.FALLBACK_SOUND_CHANGED;

        String undoName = nameChanged ? csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1)) : null;
        AndroidColour undoColour = colourChanged ? new AndroidColour(csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER2))) : null;
//        float undoVolume = volumeChanged ? csr.getFloat(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3)) : -1f;
        String undoSoundKey = soundChanged ? csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING2)) : null;
        String undoFallbackSoundKey = fallbackSoundChanged ? csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3)) : null;

        ChangeBeatTypeCommand command = null;

        if (mementoType == MEMENTO_TYPE_CHANGE_BEAT_NAME_AND_SOUND) {
            command = new ChangeBeatNameAndSound(beatType, whichParts, undoName, undoSoundKey, undoFallbackSoundKey, undoColour);
        }
        else if (mementoType == MEMENTO_TYPE_CHANGE_BEAT_TYPE_COLOUR) {
            command = new ChangeBeatColour(beatType, whichParts, undoColour);

        }
        else if (mementoType == MEMENTO_TYPE_CHANGE_BEAT_TYPE_SOUND) {
            command = new ChangeBeatSound(beatType, whichParts, undoSoundKey);

        }
        else if (mementoType == MEMENTO_TYPE_CHANGE_BEAT_TYPE_FALLBACK_SOUND) {
            command = new ChangeBeatFallbackSound(beatType, whichParts, undoFallbackSoundKey);
        }

        return command;
    }

    private UndoableCommand inflateAddBeatTypeCommand(Cursor csr) {
        return new AddBeatType(mLibrary, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
    }

    private UndoableCommand inflateAddSoundCommand(Cursor csr) {
        return new AddCustomSound(mLibrary.getResourceManager(), mLibrary, csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
    }

    private UndoableCommand inflateDeleteBeatTypeCommand(Cursor csr) {

        String beatTypeKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        String name = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1));
        String soundKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING2));
        String fallbackSoundKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING3));
        AndroidColour colour = new AndroidColour(csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_INTEGER1)));
        float volume = csr.getFloat(csr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_NUMBER3));

        HashMap<String, RhythmMemento.ClonedBeatsMemento> rhythmBeatsMementos = new HashMap<>();

        Cursor childCsr = getMementoChildren(csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_ID)));

        try {
            if (childCsr.getCount() != 0) {
                AndroidResourceManager.logd(LOG_TAG, String.format("inflateDeleteBeatTypeCommand: found %s rhythm cloned beats", childCsr.getCount()));

                childCsr.moveToFirst();
                do {
                    int childType = childCsr.getInt(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_TYPE));
                    if (childType == MEMENTO_CHILD_TYPE_CLONED_BEATS) {
                        rhythmBeatsMementos.put(childCsr.getString(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)),
                                new RhythmMemento.ClonedBeatsMemento(childCsr.getString(childCsr.getColumnIndex(KybSQLiteHelper.COLUMN_MEMENTO_STRING1))));
                    }
                    else {
                        AndroidResourceManager.loge(LOG_TAG, "expected cloned beats only, found "+childType);
                    }

                    childCsr.moveToNext();
                }
                while (!childCsr.isAfterLast());
            }
        }
        finally {
            childCsr.close();
        }

        return new DeleteBeatType(mLibrary, new BeatTypeMemento(beatTypeKey, name, soundKey, fallbackSoundKey, colour, volume, rhythmBeatsMementos));
    }

    private Cursor getMementoChildren(int mementoId) {
        String selection = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_FK, mementoId);
        return query(KybSQLiteHelper.TABLE_MEMENTO_CHILDREN, MEMENTO_CHILD_COLUMNS, selection, null, null);
    }

    /**
     * Called from DbBackedUndoableCommandController.clearStack()
     * @param stackKey
     */
    public void clearUndoStack(int stackKey) {

        SQLiteDatabase db = mLibrary.getWritableDatabase();
        db.beginTransaction(); // just for these changes

        try {
            String del = String.format(COLUMN_MATCH_NON_STRING_FORMAT, KybSQLiteHelper.COLUMN_MEMENTO_STACK_TYPE, stackKey);
            db.delete(KybSQLiteHelper.TABLE_MEMENTOS, del, null);

            db.setTransactionSuccessful();
        }
        catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "clearUndoStack: unexpected error", e);
            throw e;
        }
        finally {
            db.endTransaction(); // commit or rollback, depends if successful
        }
    }
}
