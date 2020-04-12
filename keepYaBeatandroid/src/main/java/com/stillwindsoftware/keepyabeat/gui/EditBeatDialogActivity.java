package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.EditRhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.db.SoundSqlImpl;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.Beat;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidBeatShapedImage;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.FullBeatOverlayImage;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.DrawnBeat;
import com.stillwindsoftware.keepyabeat.player.EditedBeat;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.BT_TYPE;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.RBT_TYPE;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.SOUNDS_LIST_HEADER;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.VOLUMES_LIST_HEADER;

public class EditBeatDialogActivity extends KybActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, DialogInterface.OnClickListener, View.OnClickListener, GuiManager.BeatTypeSelectedCallback, AdapterView.OnItemSelectedListener, SeekBar.OnSeekBarChangeListener {

    static final String LOG_TAG = "KYB-"+EditBeatDialogActivity.class.getSimpleName();

    static final String EDIT_BEAT_SELECTED_BEAT_POSITION = "selected beat";
    private static final String STATE_SELECTED_PART = "STATE_SELECTED_PART";
    private static final String STATE_BEAT_TYPE_KEYS = "STATE_BEAT_TYPE_KEYS";
    private static final String STATE_BEAT_TYPE_SOUND_KEYS = "STATE_BEAT_TYPE_SOUND_KEYS";
    private static final String STATE_BEAT_TYPE_SOUND_VOLUMES = "STATE_BEAT_TYPE_SOUND_VOLS";
    private static final String STATE_SHOWN_SOUND_WARNING = "STATE_SHOWN_SOUND_WARNING";

    private SimpleCursorAdapter mSoundsSpinnerAdapter;
    private Button mBeatTypeBtn;
    private PartBeatButton mSelectedPartBtn;
    private TextView mBeatPartsTv;
    private SeekBar mPartsSeekBar;
    private Spinner mSoundsSpinner;
    private BeatType mSelectedButtonBeatType, mSilentBeatType;
    private EditRhythmSqlImpl mEditRhythm;
    private TreeSet<RhythmBeatType> mRhythmBeatTypes;
    private SeekBar mVolumeSeekBar;
    private TextView mVolumeNumText;
    private PartBeatButton[] mPartsButtons;

    private int mSelectedPartIdx = 0; // always begins as the first one
    private HashMap<String, Float> mBeatTypeKeyVolumeChanges = new HashMap<>();
    private HashMap<String, String> mBeatTypeKeySoundKeyChanges = new HashMap<>();
    private TextView mTitle;
    private int mNumParts;
    private boolean mSeenSoundWarning = false;
    private SettingsManager mSettingsManager;
    private int mSelectedBeatPosition;
    private boolean mHideDetailViews = false; // true when very small screen

    /**
     * Use this factory method to create a new instance of the intent to launch this activity
     *
     * @param context
     * @param position
     * @return
     */
    public static Intent newInstance(Activity context, int position) {
        Intent intent = new Intent(context, EditBeatDialogActivity.class);
        intent.putExtra(EditBeatDialogActivity.EDIT_BEAT_SELECTED_BEAT_POSITION, position);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.edit_beat_dialog_activity);

        setResult(RESULT_CANCELED); // defaults to cancelled so nothing happens unless delete or ok is pressed

        Intent callingIntent = getIntent();
        mSelectedBeatPosition = callingIntent.getIntExtra(EditBeatDialogActivity.EDIT_BEAT_SELECTED_BEAT_POSITION, -1);
        if (mSelectedBeatPosition == -1) {
            AndroidResourceManager.loge(LOG_TAG, "onCreate: no selected beat number available, abort dialog activity");
            finish();
        }

        DrawnBeat.DrawnFullBeat selectedBeat = null;
        final RhythmsContentProvider rhythms = (RhythmsContentProvider) mResourceManager.getLibrary().getRhythms();
        try {

            while (!rhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) {
                AndroidResourceManager.logd(LOG_TAG, "onCreate: waiting for lock on player rhythm");
            }

            mEditRhythm = rhythms.getOpenEditRhythm();
            if (mEditRhythm == null) {
                throw new NullPointerException("onCreate: no edit rhythm available, abort dialog activity");
            }

            if (!mEditRhythm.hasBeatDataCache()) {
                throw new NullPointerException("onCreate: edit rhythm has no cache, abort dialog activity");
            }

            selectedBeat = getSelectedBeatFromPosition(mEditRhythm, mSelectedBeatPosition);
            if (selectedBeat == null) {
                throw new NullPointerException("onCreate: no selected beat found, abort dialog activity");
            }

            if (!mEditRhythm.hasBeatDataCache()) {
                throw new NullPointerException("onCreate: editRhythm does not have beat data cache, abort dialog activity");
            }

            mRhythmBeatTypes = mEditRhythm.getCachedRhythmBeatTypes();

        } catch (NullPointerException e) {
            AndroidResourceManager.loge(LOG_TAG, e.getMessage(), e);
            setResult(EditRhythmActivity.UNEXPECTED_MISSING_DATA_ERROR);
            finish();
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "onCreate: program error", e);
        } finally {
            rhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        String displayFullBeatNum = selectedBeat.getDisplayFullBeatNum();
        String beatLabel = displayFullBeatNum == null ? Integer.toString(selectedBeat.getFullBeatNum()) : displayFullBeatNum;
        ((TextView)findViewById(R.id.beatTitle)).setText(getString(R.string.beat_dialog_title, beatLabel));
        mTitle = findViewById(R.id.beatTitle2);

        mBeatPartsTv = findViewById(R.id.beat_parts);

        mPartsButtons = new PartBeatButton[] { findViewById(R.id.button0), findViewById(R.id.button1), findViewById(R.id.button2), findViewById(R.id.button3),
                findViewById(R.id.button4), findViewById(R.id.button5), findViewById(R.id.button6), findViewById(R.id.button7) };

        mPartsSeekBar = findViewById(R.id.parts_seekbar);

        mBeatTypeBtn = findViewById(R.id.beat_type_btn);
        mSoundsSpinner = findViewById(R.id.soundSpinner);
        mVolumeSeekBar = findViewById(R.id.soundVolume);
        mVolumeNumText = findViewById(R.id.volume_num);

        int numPartBeats = initPartsButtonBeatTypes(selectedBeat, savedInstanceState);
        mPartsSeekBar.setProgress(numPartBeats - 1);
        setNumParts(numPartBeats);

        findViewById(R.id.ok_btn).setOnClickListener(this);
        findViewById(R.id.cancel_btn).setOnClickListener(this);

        // when the screen is very small don't show the additional bits that can be edited elsewhere
        if (getResources().getBoolean(R.bool.very_small_width)) {
            mHideDetailViews = true;
            mSoundsSpinner.setVisibility(View.GONE);
            mVolumeSeekBar.setVisibility(View.GONE);
            mVolumeNumText.setVisibility(View.GONE);
        }


        fillSpinnerData(); // do this in all cases, as on load finished there's some other stuff happening

        sizeWindow();
        readSettings(savedInstanceState);
    }

    private void readSettings(Bundle savedInstanceState) {
        mSettingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager(); // could be needed if have to show warning after config change
        if (savedInstanceState == null) { // otherwise not needed (see loadInstanceState())
            mSeenSoundWarning = mSettingsManager.hasSeenSoundModifyInPartsDialogWarning();
        }
    }

    private void sizeWindow() {
        // fill the screen height with the dialog and make a decent width
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());

        if (getResources().getBoolean(R.bool.very_small_width)) {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        }
        else {
            mGuiManager.setEditingDimensions();
            int screenWidth = mGuiManager.getScreenWidth();
            lp.width = (int) (screenWidth * (mGuiManager.getScreenHeight() < screenWidth ? .6f : .75f));
        }
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(lp);

    }

    private boolean loadInstanceState(Bundle savedInstanceState) {

        mSelectedPartIdx = savedInstanceState.getInt(STATE_SELECTED_PART, -1);
        if (mSelectedPartIdx == -1) { // if there's a state saved assume have everything
            return false;
        }

        AndroidResourceManager.logd(LOG_TAG,"loadInstanceState: restoring state");

        mSeenSoundWarning = savedInstanceState.getBoolean(STATE_SHOWN_SOUND_WARNING, false);

        // changes from prev state where a new sound has been chosen for a beat type

        String[] beatTypeSoundKeys = savedInstanceState.getStringArray(STATE_BEAT_TYPE_SOUND_KEYS);
        if (beatTypeSoundKeys != null) {
            for (String beatTypeSoundKey : beatTypeSoundKeys) {
                AndroidResourceManager.logd(LOG_TAG, String.format("loadInstanceState: restoring beat type/sound change %s", beatTypeSoundKey));
                String[] split = beatTypeSoundKey.split(RhythmEncoder.ENC_ELEMENT_SEPARATOR);
                String beatTypeKey = split[0];
                String soundKey = split[1];
                mBeatTypeKeySoundKeyChanges.put(beatTypeKey, soundKey);
            }
        }

        // changes from prev state where a new volume has been set for a beat type

        String[] beatTypeSoundVolumes = savedInstanceState.getStringArray(STATE_BEAT_TYPE_SOUND_VOLUMES);
        if (beatTypeSoundVolumes != null) {
            for (String beatTypeSoundVolume : beatTypeSoundVolumes) {
                AndroidResourceManager.logd(LOG_TAG, String.format("loadInstanceState: restoring beat type/volume change %s", beatTypeSoundVolume));
                String[] split = beatTypeSoundVolume.split(RhythmEncoder.ENC_ELEMENT_SEPARATOR);
                String beatTypeKey = split[0];
                float volume = Float.parseFloat(split[1]);
                mBeatTypeKeyVolumeChanges.put(beatTypeKey, volume);
            }
        }

        // beat types on each part button from prev state, as they may have been changed

        String[] beatTypeKeys = savedInstanceState.getStringArray(STATE_BEAT_TYPE_KEYS);
        if (beatTypeKeys == null) {
            AndroidResourceManager.loge(LOG_TAG,"loadInstanceState: something amiss, no beat type keys");
            return false;
        }

        for (int i = 0; i < mPartsButtons.length; i++) {
            Button partsBtn = mPartsButtons[i];
            partsBtn.setTag(beatTypeKeys[i]);
        }

        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(STATE_SHOWN_SOUND_WARNING, mSeenSoundWarning);

        outState.putInt(STATE_SELECTED_PART, mSelectedPartIdx);

        String[] beatTypeKeys = new String[mPartsButtons.length];

        for (int i = 0; i < mPartsButtons.length; i++) {
            Button partsButton = mPartsButtons[i];
            Object tag = partsButton.getTag();
            if (tag != null)
                beatTypeKeys[i] = tag instanceof BeatType ? ((BeatType)tag).getKey() : (String)tag;
        }

        outState.putStringArray(STATE_BEAT_TYPE_KEYS, beatTypeKeys);

        String[] beatTypeSoundKeys = new String[mBeatTypeKeySoundKeyChanges.size()];
        int soundIdx = 0;

        for (Map.Entry<String, String> entry : mBeatTypeKeySoundKeyChanges.entrySet()) {
            String beatTypeKey = entry.getKey();
            String soundKey = entry.getValue();
            beatTypeSoundKeys[soundIdx++] = beatTypeKey + RhythmEncoder.ENC_ELEMENT_SEPARATOR + soundKey;
            AndroidResourceManager.logd(LOG_TAG,String.format("onSaveInstanceState: storing beat type (%s) sound (%s) change", beatTypeKey, soundKey));
        }

        outState.putStringArray(STATE_BEAT_TYPE_SOUND_KEYS, beatTypeSoundKeys);

        String[] beatTypeSoundVolumes = new String[mBeatTypeKeyVolumeChanges.size()];
        int volumeIdx = 0;

        for (Map.Entry<String, Float> entry : mBeatTypeKeyVolumeChanges.entrySet()) {
            String beatTypeKey = entry.getKey();
            float volume = entry.getValue();
            beatTypeSoundVolumes[volumeIdx++] = beatTypeKey + RhythmEncoder.ENC_ELEMENT_SEPARATOR + volume;
            AndroidResourceManager.logd(LOG_TAG,String.format("onSaveInstanceState: storing beat type (%s) volume (%.2f) change", beatTypeKey, volume));
        }

        outState.putStringArray(STATE_BEAT_TYPE_SOUND_VOLUMES, beatTypeSoundVolumes);
    }

    /**
     * Called once during onCreate() to make the corresponding part beat buttons visible and set their beat types as tag data on them.
     * If the silent beat type is found in the rhythm beat types, the colour etc can be set immediately to any parts buttons not visible,
     * otherwise it will be lazily looked up when/if a new part is revealed. Existing parts simply set the beat type from the part beats.
     * Note: on config change, the saved state already contains the tag beat type, so no need to save it or look it up.
     *
     * @param selectedBeat
     * @param savedInstanceState
     */
    private int initPartsButtonBeatTypes(DrawnBeat.DrawnFullBeat selectedBeat, Bundle savedInstanceState) {

        // create images to pass to each button, so can do the selected shape and have a border
        Resources res = getResources();

        AndroidBeatShapedImage normalPartOverlay = new AndroidBeatShapedImage(mResourceManager)
                .setStroke(res.getColor(R.color.cardview_dark_background), .12f);
        AndroidBeatShapedImage selectedPartOverlay = new AndroidBeatShapedImage(mResourceManager).setStroke(Color.WHITE, .5f);
        normalPartOverlay.initSize(3, 3);
        selectedPartOverlay.initSize(5, 5);

        int numParts = 0; // if have saved data, this'll be the number of
        boolean haveSavedData = savedInstanceState != null && loadInstanceState(savedInstanceState);
        if (haveSavedData) {
            numParts = savedInstanceState.getStringArray(STATE_BEAT_TYPE_KEYS).length;
        } else {                      // part idx already set
            mSelectedPartIdx = 0;
            numParts = selectedBeat.getNumPartBeats();
        }

        // can at least try to get the silent beat type from the rhythm, if there's any silent beats already it'll be in there
        RhythmBeatType silentRbt = RhythmBeatType.getRhythmBeatType(BeatTypes.DefaultBeatTypes.silent.name(), mRhythmBeatTypes);
        if (silentRbt != null)
            mSilentBeatType = silentRbt.getBeatType();

        Beat.SubBeat[] partBeats = selectedBeat.getPartBeats();
        setSelectedPart(mPartsButtons[mSelectedPartIdx], mSelectedPartIdx);

        boolean needLookupSilent = false, needLookupOther = false;

        for (int i = 0; i < mPartsButtons.length; i++) {
            PartBeatButton partsBtn = mPartsButtons[i];
            partsBtn.setOnClickListener(this);

            partsBtn.init(normalPartOverlay, selectedPartOverlay);

            // check for a tag, the only way it's there is there was a saved instance state

            Object beatTypeKey = partsBtn.getTag();                             // could be there after restoring from config change
            boolean haveBeatTypeKey = beatTypeKey != null && beatTypeKey instanceof String;

            boolean beatTypeSet = false;

            // if there's a part beat on the existing rhythm it could be that it's still the same beat type

            if (i == 0 ||                                                       // first one is the full beat
                    (partBeats != null && i > 0 && i <= partBeats.length)) {    // existing part beats on rhythm's beat, i = 0 = full beat NOT first part beat

                BeatType existingBeatType = i == 0 ? selectedBeat.getBeatType() // full beat has the type on it
                        : partBeats[i - 1].getBeatType();                       // otherwise it's on the array of part beats, one back from i, eg. 2nd button (i = 1) = part 2 = partBeats[0]

                if ((haveBeatTypeKey                                            // must be from saved data
                        && existingBeatType.getKey().equals(beatTypeKey))       // and beat type matches existing data
                        || !haveSavedData) {                                    // OR no saved data, just take the beat type
                    beatTypeSet = true;
                    setPartsButtonBeatType(partsBtn, existingBeatType);
//                        AndroidResourceManager.logd(LOG_TAG, String.format(
//                                "initPartsButtonBeatTypes: partBtn=%s key not there or matches existing beatType (bt=%s)", i, existingBeatType.getName()));
                }
            } else if (!haveSavedData) {                                          // no saved data, and not an existing part beat
//                AndroidResourceManager.logd(LOG_TAG, String.format(
//                        "initPartsButtonBeatTypes: partBtn=%s outside part beats making silent beat invisible", i));

                if (mSilentBeatType != null) {
                    beatTypeSet = true;
                    setPartsButtonBeatType(partsBtn, mSilentBeatType);
                } else {
                    partsBtn.setTag(BeatTypes.DefaultBeatTypes.silent.name());
                }

                partsBtn.setVisibility(View.GONE);                              // outside the existing parts
            }

            // got this far visibility of the button is set and tags are set

            if (!beatTypeSet                                                    // haven't found it yet
                    && haveBeatTypeKey                                          // (should only be the case)
                    && partsBtn.getVisibility() == View.VISIBLE) {              // and need it

                if (beatTypeKey.equals(
                        BeatTypes.DefaultBeatTypes.silent.name())) {
//                    AndroidResourceManager.logd(LOG_TAG, String.format(
//                            "initPartsButtonBeatTypes: partBtn=%s not there silent beatType (silent needs lookup =%s)", i, (mSilentBeatType == null)));

                    if (mSilentBeatType != null) {
                        beatTypeSet = true;
                        setPartsButtonBeatType(partsBtn, mSilentBeatType);
                    } else {
                        needLookupSilent = true;
                    }
                } else {                                                          // try to find it in existing set
                    RhythmBeatType rbt = RhythmBeatType.getRhythmBeatType((String) beatTypeKey, mRhythmBeatTypes);
//                    AndroidResourceManager.logd(LOG_TAG, String.format(
//                            "initPartsButtonBeatTypes: partBtn=%s key not there beatType needs lookup (key=%s, lookup=%s)", i, beatTypeKey, (rbt==null)));
                    if (rbt != null) {
                        beatTypeSet = true;
                        setPartsButtonBeatType(partsBtn, rbt.getBeatType());
                    } else {
                        needLookupOther = true;
                    }
                }
            }

            // the selected parts button with beat type found get all the details set up

            if (beatTypeSet && i == mSelectedPartIdx) {
//                AndroidResourceManager.logd(LOG_TAG, String.format(
//                        "initPartsButtonBeatTypes: partBtn=%s is selected and have beatType, set to beat type", i));
                mSelectedButtonBeatType = (BeatType) partsBtn.getTag();
            }
        }

        // get the missing data in background thread
        if (needLookupSilent || needLookupOther) {
            AndroidResourceManager.logd(LOG_TAG, String.format(
                    "initPartsButtonBeatTypes: have to lookup either silent (%s) or other (%s)", needLookupSilent, needLookupOther));
            lookupPartsButtonsBeatTypes(needLookupOther);
        }

        return numParts;
    }

    /**
     * Try to get the sound from the rhythm beat types, if not there use beat type itself
     * @param beatType
     * @return
     */
    private SoundSqlImpl getBeatTypeSound(BeatType beatType) {

        RhythmBeatType rbt = RhythmBeatType.getRhythmBeatType(beatType, mRhythmBeatTypes);
        if (rbt != null) {
            return (SoundSqlImpl) rbt.getSound();
        }

        return (SoundSqlImpl) beatType.getSound();
    }

    private void setPartsButtonBeatType(Button partsBtn, BeatType beatType) {
        int colour = ((AndroidColour)beatType.getColour()).getColorInt();
        partsBtn.setBackgroundColor(colour);
        partsBtn.setTag(beatType);
    }

    private void setNumParts(int numParts) {
        mNumParts = numParts;
        setBeatTypeButtonText();

        Resources resources = getResources();
        mBeatPartsTv.setText(resources.getQuantityString(R.plurals.beat_dialog_parts_plural, numParts, numParts));
        mTitle.setText(resources.getQuantityString(R.plurals.beat_dialog_title_plurals, numParts, numParts));

        if (mSelectedPartIdx >= numParts) {                    // now reducing the number
            setSelectedPart(mPartsButtons[numParts -1], numParts -1);
            setBeatType((BeatType) mSelectedPartBtn.getTag(), false); // must be already set because it's a reduction of the number which means it was already showing
        }

        boolean needLookupSilent = false, needLookupOther = false;

        for (int i = 1; i < mPartsButtons.length; i++) {
            Button partsBtn = mPartsButtons[i];
            int partNum = i+1;

            if (partNum <= numParts) {                          // this should be showing

                if (partsBtn.getVisibility() == View.GONE) {    // but it's not yet

                    partsBtn.setVisibility(View.VISIBLE);

                    Object tag = partsBtn.getTag();
                    if (tag instanceof String) {                // since tag is a string, it must be a key

                        if (BeatTypes.DefaultBeatTypes.silent.name().equals(tag)) {

                            if (mSilentBeatType != null) {
                                setPartsButtonBeatType(partsBtn, mSilentBeatType);
                            } else {
                                needLookupSilent = true;
                            }
                        }
                        else {                                  // not silent and still a string key
                            needLookupOther = true;             // must then need to be looked up
                        }
                    }
                }
            }
            else {
                partsBtn.setVisibility(View.GONE);
            }
        }

        // in the case where there's no silent rbt on the rhythm initially, when increase the number of parts
        // might have to lookup the beat type
        if (needLookupSilent || needLookupOther) {
            AndroidResourceManager.logd(LOG_TAG, String.format(
                    "setNumParts: have to lookup either silent (%s) or other (%s)", needLookupSilent, needLookupOther));
            lookupPartsButtonsBeatTypes(needLookupOther);
        }
    }

    private void lookupPartsButtonsBeatTypes(final boolean needOther) {
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {

                        try {
                            HashMap<String, BeatType> bts = new HashMap<>();

                            Library library = mResourceManager.getLibrary();
                            BeatTypesContentProvider beatTypes = (BeatTypesContentProvider) library.getBeatTypes();
                            if (mSilentBeatType == null) {
                                mSilentBeatType = beatTypes.lookup(BeatTypes.DefaultBeatTypes.silent.name());
                            }

                            bts.put(mSilentBeatType.getKey(), mSilentBeatType);

                            if (needOther) {

                                for (int i = 0; i < mPartsButtons.length; i++) {
                                    Button partsBtn = mPartsButtons[i];
                                    Object tag = partsBtn.getTag();
                                    if (tag instanceof BeatType)
                                        continue;  // got beat type for this one already

                                    if (tag == null || !(tag instanceof String)) {
                                        AndroidResourceManager.loge(LOG_TAG, "initPartsButtonsToSilentBeatType: got a tag that isn't beat type or string! tag=" + tag);
                                        continue;
                                    }

                                    if (bts.containsKey(tag)) {             // looked it up already
                                        continue;
                                    }

                                    BeatType bt = beatTypes.lookup((String) tag);
                                    if (bt == null) {
                                        AndroidResourceManager.loge(LOG_TAG, "initPartsButtonsToSilentBeatType: unable to find beat type key=" + bt);
                                    } else {
                                        bts.put(bt.getKey(), bt);
                                    }
                                }
                            }

                            return bts;

                        }
                        catch (Exception e) {
                            AndroidResourceManager.loge(LOG_TAG, "initPartsButtonsToSilentBeatType: exception looking up silent beat type", e);
                            return getString(R.string.UNEXPECTED_PROGRAM_ERROR);
                        }
                    }
                },
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        if (mParam != null && mParam instanceof HashMap) { // no error message means success
                            HashMap<String, BeatType> bts = (HashMap<String, BeatType>) mParam;

                            for (int i = 0; i < mPartsButtons.length; i++) {
                                Button partsBtn = mPartsButtons[i];

                                Object tag = partsBtn.getTag();
                                if (tag instanceof String) {                 // since tag is a string, it must be needing init

                                    if (bts.containsKey(tag)) {
//                                        AndroidResourceManager.logd(LOG_TAG, String.format(
//                                                "lookupPartsButtonsBeatTypes: parts btn=%s read from db (%s)", i, tag));
                                        BeatType beatType = bts.get(tag);
                                        setPartsButtonBeatType(partsBtn, beatType);
                                        if (i == mSelectedPartIdx) {
                                            setBeatType(beatType, false);
                                        }
                                    }
                                    else {
                                        AndroidResourceManager.logw(LOG_TAG, String.format(
                                                "setNumParts: parts button[%s] beat type not in lookup list(it's = %s)", i, tag));
                                    }
                                }
                            }
                        } else if (mParam instanceof String) { // any error
                            Toast.makeText(EditBeatDialogActivity.this, (String) mParam, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    /**
     * Sets up the items in the master detail arrangement, beat type button's colour, sounds spinner and volume
     * @param beatType
     */
    private void setBeatType(BeatType beatType, boolean isClick) {
        mSelectedButtonBeatType = beatType;
        setBeatTypeButtonText();
        int colour = ((AndroidColour)beatType.getColour()).getColorInt();
        mBeatTypeBtn.setBackgroundColor(colour);
        if (!isClick) {
            if (mSelectedPartBtn != null) {
                mSelectedPartBtn.setTag(beatType);
                mSelectedPartBtn.setBackgroundColor(colour);
            }
            else {
                AndroidResourceManager.logw(LOG_TAG, "setBeatType: no selected part button to set beat type/colour to");
            }
        }
        double contrast = ColorUtils.calculateContrast(WHITE, colour);
        int foregrdColour = contrast < RhythmBeatTypesFragment.COLOUR_CONTRAST_RATIO_THRESHOLD ? BLACK : WHITE;
        mBeatTypeBtn.setTextColor(foregrdColour);

        if (!mHideDetailViews) {
            // cache the beat type sound
            SoundSqlImpl beatTypeSound = getBeatTypeSound(beatType);

            // method as used in RhythmBeatTypesFragment
            RhythmBeatType rbt = RhythmBeatType.getRhythmBeatType(beatType, mRhythmBeatTypes);
            boolean isSilentBt = BeatTypes.DefaultBeatTypes.silent.name().equals(beatType.getKey());

            if (isSilentBt) {
                mSilentBeatType = beatType; // useful when showing the other parts
                mSoundsSpinner.setVisibility(View.INVISIBLE);
                mVolumeSeekBar.setVisibility(View.INVISIBLE);
                mVolumeNumText.setVisibility(View.INVISIBLE);
            }
            else {   // they show at all
                String soundKey = mBeatTypeKeySoundKeyChanges.containsKey(mSelectedButtonBeatType.getKey())
                        ? mBeatTypeKeySoundKeyChanges.get(mSelectedButtonBeatType.getKey())         // already have put a changed sound on this one
                        : beatTypeSound.getKey();

                mSoundsSpinner.setVisibility(View.VISIBLE);

                showVolumeIfNeeded(false, soundKey);

                // volume seekbar

                float vol = mBeatTypeKeyVolumeChanges.containsKey(mSelectedButtonBeatType.getKey()) // changed already in this dialog, put the changed value back
                        ? mBeatTypeKeyVolumeChanges.get(mSelectedButtonBeatType.getKey())
                        : rbt != null ? rbt.getVolume() : beatType.getVolume();                 // otherwise use rbt if there, and if not the beattype

                int progress = (int) (vol * 100);
                mVolumeSeekBar.setProgress(progress);
                mVolumeNumText.setText(Integer.toString(progress));

                // sound spinner

                alignSpinnerToSound(soundKey);
            }
        }
    }

    private void setBeatTypeButtonText() {
        if (mSelectedButtonBeatType != null) {
            mBeatTypeBtn.setText(getString(R.string.beat_dialog_beat_type_label,
                    getResources().getQuantityString(R.plurals.beat_dialog_beat_type_label_plurals, mNumParts, mSelectedPartIdx + 1),
                    mSelectedButtonBeatType.getName()));
        }
    }

    private void alignSpinnerToSound(String soundKey) {
        int spinnerCount = mSoundsSpinner.getCount();

        for (int i = 0; i < spinnerCount; i++) {
            Cursor value = (Cursor) mSoundsSpinner.getItemAtPosition(i);
            String csrKey = value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
            if (csrKey.equals(soundKey)) {
                mSoundsSpinner.setSelection(i);
                break;
            }
        }
    }

    private DrawnBeat.DrawnFullBeat getSelectedBeatFromPosition(EditRhythmSqlImpl editRhythm, int position) {
        BeatTree beatTree = editRhythm.getBeatTree(BeatTree.EDIT);
        return (DrawnBeat.DrawnFullBeat) beatTree.getFullBeatAt(position);
    }

    /**
     */
    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onClick(View view) {

        // not sure these won't become menu items again, so some duplication of code with onOptionsItemsSelected()
        int viewId = view.getId();

        if (viewId == R.id.beat_type_btn) {
            showBeatTypePopupLov();
        }
        else if (viewId == R.id.ok_btn) {
            saveChanges();
        }
        else if (viewId == R.id.cancel_btn) {
            finish();
        }

        else {
            for (int i = 0; i < mPartsButtons.length; i++) {
                PartBeatButton partsButton = mPartsButtons[i];
                if (partsButton.getId() == viewId) {
                    setSelectedPart(partsButton, i);
                    setBeatType((BeatType) partsButton.getTag(), true);
                    return; // done, bypass the toast
                }
            }

            Toast.makeText(this, "what button is this", Toast.LENGTH_LONG).show();
        }
    }

    private void saveChanges() {
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(

                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {
                        try {
                            BeatType fullBeatType = (BeatType) mPartsButtons[0].getTag();   // should not fail, but class cast will be caught as unexpected error
                            ArrayList<BeatType> partBeatTypes = new ArrayList<>();

                            for (int i = 1; i < mPartsButtons.length; i++) {    // bypass the first one which is the full beat
                                Button partsButton = mPartsButtons[i];

                                if (partsButton.getVisibility() == View.VISIBLE) {
                                    partBeatTypes.add((BeatType)partsButton.getTag());
                                }
                            }

                            Library library = mResourceManager.getLibrary();
                            BeatTypesContentProvider beatTypes = (BeatTypesContentProvider) library.getBeatTypes();

                            String soundsUndoMemento = makeSoundChangesUndoString(beatTypes);
                            String volumesUndoMemento = makeVolumeChangesUndoString(beatTypes);

                            mEditRhythm.putCommand(
                                    new RhythmEditCommand.RhythmEditBeatPartsCommand(mEditRhythm, mSelectedBeatPosition,
                                            fullBeatType, partBeatTypes, mBeatTypeKeyVolumeChanges, mBeatTypeKeySoundKeyChanges,
                                            soundsUndoMemento, volumesUndoMemento));

                            return null;
                        }
                        catch (Exception e) {
                            AndroidResourceManager.loge(LOG_TAG, "saveChanges: exception ", e);
                            return getString(R.string.UNEXPECTED_PROGRAM_ERROR);
                        }
                    }

                    private String makeSoundChangesUndoString(BeatTypes beatTypes) {

                        StringBuilder undoSounds = new StringBuilder(SOUNDS_LIST_HEADER);

                        Set<Map.Entry<String, String>> soundEntries = mBeatTypeKeySoundKeyChanges.entrySet();
                        for (Map.Entry<String, String> entry : soundEntries) {
                            String beatTypeKey = entry.getKey();

                            RhythmBeatType rbt = RhythmBeatType.getRhythmBeatType(beatTypeKey, mRhythmBeatTypes); // try to get the beat type from the rhythm

                            undoSounds.append(rbt == null ? BT_TYPE : RBT_TYPE);                            // encode the string with the source, beat types or rhythm
                            undoSounds.append(RhythmEncoder.ENC_SUB_ELEMENT_SEPARATOR);
                            undoSounds.append(beatTypeKey);
                            undoSounds.append(RhythmEncoder.ENC_SUB_ELEMENT_SEPARATOR);

                            if (rbt != null) {                                                              // have the beat type on the rhythm
                                undoSounds.append(rbt.getSound().getKey());                                 // store the sound currently on there for undo
                            }
                            else {
                                BeatType beatType = beatTypes.lookup(beatTypeKey);                          // not on the rhythm, go to the beat type
                                Sound oriSound = beatType.getSound();                                       // for the original sound
                                undoSounds.append(oriSound.getKey());                                       // store that for undo
                            }

                            undoSounds.append(RhythmEncoder.ENC_ELEMENT_SEPARATOR);                         // splitting entries in the undo string
                        }

                        return undoSounds.toString();
                    }

                    private String makeVolumeChangesUndoString(BeatTypes beatTypes) {

                        StringBuilder undoVolumes = new StringBuilder(VOLUMES_LIST_HEADER);

                        Set<Map.Entry<String, Float>> volumeEntries = mBeatTypeKeyVolumeChanges.entrySet();
                        for (Map.Entry<String, Float> entry : volumeEntries) {
                            String beatTypeKey = entry.getKey();

                            RhythmBeatType rbt = RhythmBeatType.getRhythmBeatType(beatTypeKey, mRhythmBeatTypes); // try to get the beat type from the rhythm

                            undoVolumes.append(rbt == null ? BT_TYPE : RBT_TYPE);                               // encode the string with the source, beat types or rhythm
                            undoVolumes.append(RhythmEncoder.ENC_SUB_ELEMENT_SEPARATOR);
                            undoVolumes.append(beatTypeKey);
                            undoVolumes.append(RhythmEncoder.ENC_SUB_ELEMENT_SEPARATOR);

                            if (rbt != null) {
                                undoVolumes.append(rbt.getVolume());                                            // store the volume currently on there for undo
                            }
                            else {
                                BeatType beatType = beatTypes.lookup(beatTypeKey);                              // not on the rhythm, go to the beat type
                                undoVolumes.append(beatType.getVolume());                                       // store that for undo
                            }

                            undoVolumes.append(RhythmEncoder.ENC_ELEMENT_SEPARATOR);                            // splitting entries in the undo string
                        }

                        return undoVolumes.toString();
                    }

                },
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        if (mParam == null) { // no error message means success
                            setResult(RESULT_OK);
                            finish();
                        }
                        else if (mParam instanceof String) { // any error
                            Toast.makeText(EditBeatDialogActivity.this, (String) mParam, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    private void showBeatTypePopupLov() {
        ChooseBeatTypeDialog chooseBeatTypeDialogFragment = new ChooseBeatTypeDialog();
        chooseBeatTypeDialogFragment.show(getSupportFragmentManager(), ChooseBeatTypeDialog.LOG_TAG);
    }

    /**
     * This class implements BeatTypeSelectedCallback for changing the type
     * @param beatType
     */
    @Override
    public void beatTypeSelected(BeatType beatType) {
        setBeatType(beatType, false);
    }

    protected void fillSpinnerData() {
        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_SOUND_NAME };
        int[] toLayoutViewIds = new int[] { android.R.id.text1 };

        mSoundsSpinnerAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_spinner_item, null, dbColumnsForLayout, toLayoutViewIds, 0) {

            /**
             * although just a spinner, because often need to get the name from resource
             * need to override the bind view
             */
            @Override
            public void bindView(View v, Context context, Cursor cursor) {

                super.bindView(v, context, cursor);

                TextView tv = (TextView) v;

                String soundName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_NAME));
                // get the localised string for the name of internal sounds
                String sndResName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
                if (sndResName != null) {
                    int resId = AbstractSqlImpl.getLocalisedResIdFromName(sndResName, context.getResources());
                    if (resId > 0) {
                        soundName = context.getResources().getString(resId);
                    }
                }

                tv.setText(soundName);
            }
        };

        mSoundsSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // cause the sounds cursor to be populated
        getSupportLoaderManager().initLoader(0, null, this);
    }

    // ------------------------ LoaderCallbacks

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        String[] projection = {
                KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                , KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME };

        // don't want the extra toggle selections rows
        String selection = String.format("%s >= 0", KybSQLiteHelper.COLUMN_ID);
        return new CursorLoader(this, SoundsContentProvider.CONTENT_URI_ORDERED_LIST, projection, selection , null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        //AndroidResourceManager.logd(LOG_TAG, "onLoadFinished sounds spinner adapter load finished, data="+data.getCount());
        mSoundsSpinnerAdapter.swapCursor(data);
        mSoundsSpinner.setAdapter(mSoundsSpinnerAdapter);
        // have needed data to complete set up of details items
        if (mSelectedButtonBeatType != null) {
            setBeatType(mSelectedButtonBeatType, false);
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "onLoadFinished: not able to set beat type yet (should be looking it up in background thread, will be set when that completes)");
        }
        mPartsSeekBar.setOnSeekBarChangeListener(this);
        mBeatTypeBtn.setOnClickListener(this);

        if (!mHideDetailViews) {
            mSoundsSpinner.setOnItemSelectedListener(this);
            mVolumeSeekBar.setOnSeekBarChangeListener(this);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // we lost the data
        mSoundsSpinnerAdapter.swapCursor(null);
    }

    // spinner listener

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long rowId) {

        // store for any config change and for the final save
        Cursor value = (Cursor) mSoundsSpinner.getItemAtPosition(position);
        String soundKey = value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        // because this method is triggered even when just loading, check there's actually a different value first
        if (mSelectedButtonBeatType != null         // when loading there might not yet be one (selected beat after config change refers to beat type not in the rhythm
                                                    // and it didn't yet get looked up (should be doing that)
                && !getBeatTypeSound(mSelectedButtonBeatType).getKey().equals(soundKey)
                && !BeatTypes.DefaultBeatTypes.silent.name().equals(mSelectedButtonBeatType.getKey())) { // during load if the beat type is silent it would trigger here

            AndroidResourceManager.logd(LOG_TAG, String.format("onItemSelected: store sound key button beat type %s (sound %s) doesn't match spinner %s",
            mSelectedButtonBeatType.getName(), getBeatTypeSound(mSelectedButtonBeatType).getKey(), soundKey));
            mBeatTypeKeySoundKeyChanges.put(mSelectedButtonBeatType.getKey(), soundKey);

            showModifyBeatTypeSoundVolumeWarningIfApplicable();
        }

        if (!mHideDetailViews) {
            showVolumeIfNeeded(mSelectedButtonBeatType != null && BeatTypes.DefaultBeatTypes.silent.name().equals(mSelectedButtonBeatType.getKey()), soundKey);
        }
    }

    private void showVolumeIfNeeded(boolean isSilentBeatType, String soundKey) {
        if (isSilentBeatType || Sounds.InternalSound.noSound.name().equals(soundKey)) {
            mVolumeSeekBar.setVisibility(View.INVISIBLE);
            mVolumeNumText.setVisibility(View.INVISIBLE);
        }
        else if (!mHideDetailViews) {
            mVolumeSeekBar.setVisibility(View.VISIBLE);
            mVolumeNumText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    // seekbar listener

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar.getId() == R.id.parts_seekbar) {
            setNumParts(progress+1);
        }
        else if (fromUser) {
            // same technique to get the sound as in setBeatType, but here only using the part for volume
            RhythmBeatType rbt = RhythmBeatType.getRhythmBeatType(mSelectedButtonBeatType, mRhythmBeatTypes);

            final float originalVol = rbt != null ? rbt.getVolume() : mSelectedButtonBeatType.getVolume();
            float changedVol = progress / 100.f;

            if (Float.compare(originalVol, changedVol) != 0) {
                mBeatTypeKeyVolumeChanges.put(mSelectedButtonBeatType.getKey(), changedVol);
                mVolumeNumText.setText(Integer.toString(progress));
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar.getId() == R.id.soundVolume) {
            showModifyBeatTypeSoundVolumeWarningIfApplicable();
        }
    }

    private void showModifyBeatTypeSoundVolumeWarningIfApplicable() {
        if (!mSeenSoundWarning) {
            AlertDialog.Builder bld = new AlertDialog.Builder(this)
                    .setMessage(R.string.beat_dialog_change_sound_volume_warning)
                    .setPositiveButton(R.string.ok_button, null)
                    .setNeutralButton(R.string.beat_dialog_dont_show_warning, this);

            bld.create().show();

            mSeenSoundWarning = true;
        }
    }

    public void setSelectedPart(PartBeatButton selectedPart, int i) {
        mSelectedPartIdx = i;

        if (mSelectedPartBtn != null) {
            mSelectedPartBtn.setSelected(false);
        }
        mSelectedPartBtn = selectedPart;
        mSelectedPartBtn.setSelected(true);
    }

    /**
     * Only dialog button using this class as a listener is the one to not show the warning again
     * @param dialogInterface
     * @param i
     */
    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        mSettingsManager.setSeenSoundModifyInPartsDialogWarning();
    }

    public boolean isAnEditorActivity() {
        return true;
    }

}
