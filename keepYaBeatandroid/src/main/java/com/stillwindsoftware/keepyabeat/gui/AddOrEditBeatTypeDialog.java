package com.stillwindsoftware.keepyabeat.gui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.SoundSqlImpl;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import yuku.ambilwarna.AmbilWarnaDialog;

/**
 * Use the {@link AddOrEditBeatTypeDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AddOrEditBeatTypeDialog extends DialogFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener, DialogInterface.OnShowListener, AdapterView.OnItemSelectedListener {

    static final String LOG_TAG = "KYB-"+AddOrEditBeatTypeDialog.class.getSimpleName();

    private static final int LOADER_SPINNER_ADAPTER_ID = 1;
    private static final int LOADER_SPINNER_INTERNAL_ONLY_ADAPTER_ID = 2;

    // the fragment initialization parameters
    private static final String ARG_BEAT_TYPE_KEY = "argBeatTypeKey";
    private static final String ARG_BEAT_TYPE_NAME = "argBeatTypeName";
    private static final String ARG_BEAT_TYPE_IS_NEW = "argIsNew";
    private static final String ARG_BEAT_TYPE_IS_INTERNAL = "argIsInternal";
    private static final String SAVED_STATE_CHOSEN_COLOUR = "stateChosenColour";

    // passed as params
    private String mKey;
    private String mName;
    private boolean mIsNew;
    private boolean mIsInternal;

    private BeatsAndSoundsActivity mActivity;
    private AndroidResourceManager mResourceManager;
    private BeatType mBeatType;
    private boolean mIsSilentBeatType;
    private int mOriColour;
    private int mChosenColour;
    private SoundSqlImpl mOriSound;
    private SoundSqlImpl mOriFallbackSound;

    private EditText mNameEditText;
    private TextView mFallbackLabel;
    private Button mColourBtn;
    private Spinner mSoundsSpinner;
    private SimpleCursorAdapter mSoundsSpinnerAdapter;
    private Spinner mFallbackSoundsSpinner;
    private SimpleCursorAdapter mFallbackSoundsSpinnerAdapter;

    private AlertDialog mDialog;
    private View mBrokenSoundRow;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param key Parameter 1.
     * @param name Parameter 2.
     * @param isNew Parameter 3. used to detect the need for the new rhythm title
     * @param isInternal
     * @return A new instance
     */
    public static AddOrEditBeatTypeDialog newInstance(String key, String name, boolean isNew, boolean isInternal) {
        AddOrEditBeatTypeDialog fragment = new AddOrEditBeatTypeDialog();
        Bundle args = new Bundle();
        args.putString(ARG_BEAT_TYPE_KEY, key);
        args.putString(ARG_BEAT_TYPE_NAME, name);
        args.putBoolean(ARG_BEAT_TYPE_IS_NEW, isNew);
        args.putBoolean(ARG_BEAT_TYPE_IS_INTERNAL, isInternal);
        fragment.setArguments(args);
        return fragment;
    }

    public AddOrEditBeatTypeDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // would be a program bug to not be there
        mKey = getArguments().getString(ARG_BEAT_TYPE_KEY);
        mName = getArguments().getString(ARG_BEAT_TYPE_NAME);
        mIsNew = getArguments().getBoolean(ARG_BEAT_TYPE_IS_NEW);
        mIsInternal = getArguments().getBoolean(ARG_BEAT_TYPE_IS_INTERNAL);

        // screen changes will save state
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(SAVED_STATE_CHOSEN_COLOUR)) {
                mChosenColour = savedInstanceState.getInt(SAVED_STATE_CHOSEN_COLOUR);
            }
        }
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mActivity = (BeatsAndSoundsActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final ScrollView sv = (ScrollView) mActivity.getLayoutInflater().inflate(R.layout.add_or_edit_beat_type_dialog, null);

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setView(sv)
                .setTitle(mIsNew ? R.string.addNewBeatTypeTitle : R.string.changeBeatTypeTitle)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mNameEditText = (EditText) sv.findViewById(R.id.beat_type_name);
        mSoundsSpinner = (Spinner) sv.findViewById(R.id.sound_spinner);
        mFallbackSoundsSpinner = (Spinner) sv.findViewById(R.id.fallback_sound_spinner);
        mFallbackLabel = (TextView) sv.findViewById(R.id.fallback_sound_label);
        mColourBtn = (Button) sv.findViewById(R.id.colour_btn);
        mColourBtn.setOnClickListener(this);
        mBrokenSoundRow = sv.findViewById(R.id.broken_sound_warning_row);

        mSoundsSpinner.setOnItemSelectedListener(this);

        if (mIsNew) {
            KybDialogFragment.trackTextLength(mNameEditText, (TextView)sv.findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.maxRhythmNameLen));
            mOriColour = Color.parseColor(getString(R.string.normalColour));
            if (mChosenColour == 0) { // keep any changed colour from saved state
                mChosenColour = mOriColour;
            }
            mColourBtn.setBackgroundColor(mChosenColour);
            // just load the spinners, and hide the fallback
            setFallbackVisibility(false);
            fillSoundsSpinner(LOADER_SPINNER_ADAPTER_ID);
            fillSoundsSpinner(LOADER_SPINNER_INTERNAL_ONLY_ADAPTER_ID);
        }

        else {
            if (mIsInternal) { // don't allow edit of the name if internal type
                mNameEditText.setEnabled(false);
                sv.findViewById(R.id.textLenIndicator).setVisibility(View.GONE);
            }
            else {
                KybDialogFragment.trackTextLength(mNameEditText, (TextView)sv.findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.maxRhythmNameLen));
            }

            // load the beat type, then kick off loading the spinner tags
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {
                            final Library library = mResourceManager.getLibrary();
                            mBeatType = library.getBeatTypes().lookup(mKey);
                            if (BeatTypesContentProvider.BEAT_TYPE_MISSING_NAME_STRING_KEY.equals(mName)) {
                                mName = mBeatType.getName(); // fix for when a beat type was inserted because was missing and no name is available
                            }
                            mOriSound = (SoundSqlImpl) mBeatType.getSound();
                            mOriFallbackSound = (SoundSqlImpl) mBeatType.getFallbackSound();

                            return library.getSounds().isSystemSound(mOriSound);
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if ((Boolean)mParam) { // hide fallback when not using custom sound
                                setFallbackVisibility(false);
                            }
                            else if (!mOriSound.isPlayable() && mOriSound.getSoundResource().getStatus() != Sound.SoundStatus.LOADED_OK) { // fallback showing, means it's a custom sound... check for breakage
                                mBrokenSoundRow.setVisibility(View.VISIBLE);
                            }

                            mIsSilentBeatType = mKey.equals(BeatTypes.DefaultBeatTypes.silent.name());
                            if (mIsSilentBeatType) { // hide the spinner
                                mSoundsSpinner.setVisibility(View.GONE);
                                sv.findViewById(R.id.sound_label).setVisibility(View.GONE);
                            }

                            mNameEditText.setText(mName);
                            mOriColour = ((AndroidColour) mBeatType.getColour()).getColorInt();
                            if (mChosenColour == 0) { // keep any changed colour from saved state
                                mChosenColour = mOriColour;
                            }
                            mColourBtn.setBackgroundColor(mChosenColour);

                            if (!mIsSilentBeatType) {
                                fillSoundsSpinner(LOADER_SPINNER_ADAPTER_ID);
                                fillSoundsSpinner(LOADER_SPINNER_INTERNAL_ONLY_ADAPTER_ID);
                            }
                        }
                    }
            );
        }

        mDialog = bld.create();
        mDialog.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
        mDialog.setOnShowListener(this);           // use onShow() to set this as click listener, this prevents auto-close
        return mDialog;
    }

    private void setFallbackVisibility(boolean show) {
        mFallbackSoundsSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
        mFallbackLabel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SAVED_STATE_CHOSEN_COLOUR, mChosenColour);
        super.onSaveInstanceState(outState);
    }

    private void fillSoundsSpinner(int which) {

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_SOUND_NAME };
        int[] toLayoutViewIds = new int[] { android.R.id.text1 };

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(mActivity, android.R.layout.simple_spinner_item, null, dbColumnsForLayout, toLayoutViewIds, 0) {

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

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        if (which == LOADER_SPINNER_ADAPTER_ID) {
            mSoundsSpinnerAdapter = adapter;
            mSoundsSpinner.setAdapter(adapter);
        }
        else {
            mFallbackSoundsSpinnerAdapter = adapter;
            mFallbackSoundsSpinner.setAdapter(adapter);
        }

        getLoaderManager().initLoader(which, null, this);
    }

    // ------------------------ LoaderCallbacks

    // creates a new loader after the initLoader() call
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        String[] projection = {
                KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, KybSQLiteHelper.COLUMN_SOURCE_TYPE
                , KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME, KybSQLiteHelper.COLUMN_SOUND_URI };

        String selection = null;
        if (id == LOADER_SPINNER_INTERNAL_ONLY_ADAPTER_ID) { // just the internal type rows
            selection = String.format("%s='%s'", KybSQLiteHelper.COLUMN_SOURCE_TYPE, KybSQLiteHelper.INTERNAL_TYPE);
        }
        else { // or all except the control rows
            selection = String.format("%s >= 0", KybSQLiteHelper.COLUMN_ID);
        }

        CursorLoader cursorLoader = new CursorLoader(mActivity, SoundsContentProvider.CONTENT_URI_ORDERED_LIST, projection, selection , null, null);

        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == LOADER_SPINNER_ADAPTER_ID) {
            mSoundsSpinnerAdapter.swapCursor(data);
            if (!mIsNew) {
                setSpinnerItemById(mSoundsSpinner, mOriSound.getInternalId());
                mSoundsSpinner.setOnItemSelectedListener(this);
            }
        }
        else {
            mFallbackSoundsSpinnerAdapter.swapCursor(data);
            if (!mIsNew) {
                setSpinnerItemById(mFallbackSoundsSpinner, mOriFallbackSound.getInternalId());
            }
        }
    }

    // https://stackoverflow.com/questions/26412767/how-to-position-select-a-spinner-on-specific-item-by-id-with-simplecursoradapt
    public void setSpinnerItemById(Spinner spinner, long _id) {
        int spinnerCount = spinner.getCount();
        for (int i = 0; i < spinnerCount; i++) {
            Cursor value = (Cursor) spinner.getItemAtPosition(i);
            if (value.getLong(value.getColumnIndex(KybSQLiteHelper.COLUMN_ID)) == _id) {
                spinner.setSelection(i);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == LOADER_SPINNER_ADAPTER_ID) {
            mSoundsSpinnerAdapter.swapCursor(null);
        }
        else {
            mFallbackSoundsSpinnerAdapter.swapCursor(null);
        }
    }

    // ------------------------ spinner OnClickListener

    @Override
    public void onClick(View view) {

        if (view.getId() == R.id.colour_btn) {
            pickColour();
        }

        else if (view instanceof Button && getString(R.string.ok_button).equals(((Button)view).getText().toString())) {
            validateAndSaveChanges();
        }
    }

    private void pickColour() {
        // see class comments for acknowledgements
        // 3rd param = supports alpha (will use for overlay playing beat, not for here though)
        AmbilWarnaDialog dialog = new AmbilWarnaDialog(mActivity, mChosenColour, false, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                mChosenColour = color;
                mColourBtn.setBackgroundColor(mChosenColour);
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {}
        });

        dialog.show();
    }

    private void validateAndSaveChanges() {
        // validation on ok button

        final KybSQLiteHelper library = (KybSQLiteHelper) mResourceManager.getLibrary();
        final BeatTypesContentProvider beatTypes = (BeatTypesContentProvider) library.getBeatTypes();
        final SoundsContentProvider sounds = (SoundsContentProvider) library.getSounds();

        // get values and changes
        final String newName = RhythmEncoder.sanitizeString(mResourceManager, mNameEditText.getText().toString(), RhythmEncoder.SANITIZE_STRING_REPLACEMENT_CHAR);
        final long newSoundId = mSoundsSpinner.getSelectedItemId();
        final long newFallbackId = mFallbackSoundsSpinner.getSelectedItemId();
        final boolean isSoundChanged = mIsNew || (!mIsSilentBeatType && newSoundId != mOriSound.getInternalId());
        final boolean isFallbackChanged = mIsNew || (!mIsSilentBeatType
                                                    && mFallbackSoundsSpinner.getVisibility() == View.VISIBLE
                                                    && newFallbackId != mOriFallbackSound.getInternalId());

        final boolean isColourChanged = mChosenColour != mOriColour;

        if (newName.isEmpty()) {     // name cannot be null
            Toast.makeText(mActivity, R.string.enterUniqueName, Toast.LENGTH_SHORT).show();
        }
        else { // validate name is unique and make changes
            // db validation and changes in background thread, success or error is shown in ui thread
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {

                            boolean isNameChanged = !newName.equals(mName);

                            if (isNameChanged) {
                                String excludeKey = mIsNew ? "xx" : mBeatType.getKey();
                                boolean exists = beatTypes.nameExists(newName, excludeKey);

                                if (exists) {
                                    return getString(R.string.nameUsed);
                                }
                            }

                            if (!isNameChanged && !isSoundChanged && !isFallbackChanged && !isColourChanged) {
                                AndroidResourceManager.logd(LOG_TAG, "onClick.runForResult: nothing changed, no db changes made");
                                return null; // do nothing, neither name nor tags have changed
                            }

                            // valid and there's something to do, make the db changes

                            if (mIsNew) { // insert a new beat type
                                // get key from spinner
                                final String soundKey = getSoundKeyFromSpinner(mSoundsSpinner);
                                Sound fallbackSound = mFallbackSoundsSpinner.getVisibility() == View.GONE ?
                                        BeatTypes.DefaultBeatTypes.normal.getDefaultSound(sounds) :
                                        sounds.lookup(getSoundKeyFromSpinner(mFallbackSoundsSpinner));
                                new BeatTypesAndSoundsCommand.AddBeatType(mResourceManager,
                                        getString(R.string.UNDO_ADD_BEAT_TYPE), newName,
                                        sounds.lookup(soundKey), fallbackSound,
                                        new AndroidColour(mChosenColour)).execute();
                            }

                            else { // update the existing beat type
                                AndroidResourceManager.logd(LOG_TAG, String.format("onClick.runForResult: update changes: name=%s, sound=%s, fallback=%s, colour=%s, isSilentBt=%s"
                                        , isNameChanged, isSoundChanged, isFallbackChanged, isColourChanged, mIsSilentBeatType));

                                int whichParts = 0;
                                if (isColourChanged) whichParts = BeatTypes.COLOUR_CHANGED;
                                if (isNameChanged) whichParts |= BeatTypes.NAME_CHANGED;
                                if (isSoundChanged) whichParts |= BeatTypes.SOUND_CHANGED;
                                if (isFallbackChanged) whichParts |= BeatTypes.FALLBACK_SOUND_CHANGED;
                                new BeatTypesAndSoundsCommand.ChangeBeatNameAndSound(mBeatType, whichParts,
                                        isNameChanged ? newName : null,
                                        isSoundChanged ? sounds.lookup(getSoundKeyFromSpinner(mSoundsSpinner)) : null,
                                        isFallbackChanged ? sounds.lookup(getSoundKeyFromSpinner(mFallbackSoundsSpinner)) : null,
                                        isColourChanged ? new AndroidColour(mChosenColour) : null)
                                        .execute();
                            }

                            return null; // null means success as there's no error message to display
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam == null) { // no error message means success
                                dismiss();
                            }
                            else { // invalid because the name exists
                                Toast.makeText(mActivity, (String)mParam, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            );
        }
    }

    private String getSoundKeyFromSpinner(Spinner spinner) {
        Cursor value = (Cursor) spinner.getSelectedItem();
        return value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
    }

    // --------------------- sound spinner selection

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        Spinner spinner = (Spinner) parent;
        Cursor value = (Cursor) spinner.getItemAtPosition(position);
        final String type = value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_SOURCE_TYPE));
        setFallbackVisibility(!type.equals(KybSQLiteHelper.INTERNAL_TYPE)); // hide fallback when not using custom sound

        // check for broken sound
        mBrokenSoundRow.setVisibility(type.equals(KybSQLiteHelper.INTERNAL_TYPE)
                || mActivity.getFileStreamPath(value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_URI))).exists()
                ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) { // not an optional dialog_simple_list
    }
}
