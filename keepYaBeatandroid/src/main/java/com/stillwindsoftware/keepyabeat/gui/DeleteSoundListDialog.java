package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * Invoked from BeatTypesList when deleting a beat type referenced by
 * rhythms that need to be migrated to another beat type
 */
public class DeleteSoundListDialog extends DialogFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener, DialogInterface.OnShowListener {

    static final String LOG_TAG = "KYB-"+DeleteSoundListDialog.class.getSimpleName();
    private static final String ARG_KEY = "argKey";
    private static final String ARG_NAME = "argName";
    private static final String ARG_NUM_RHYTHMS = "argNumRhythms";
    private static final String ARG_NUM_BEAT_TYPES = "argNumBeatTypes";
    private static final int LOADER_FILL_RHYTHMS_LIST = 0;
    private static final int LOADER_FILL_SOUNDS_SPINNER = 1;

    private KybActivity mActivity;
    private AndroidResourceManager mResourceManager;

    private String mName;
    private String mKey;
    private int mRefRhythms, mRefBeatTypes;

    private AlertDialog mDialog;
    private ListView mRhythmsOrBeatTypesListView;
    private SimpleCursorAdapter mAdapter;
    private Spinner mSoundsSpinner;
    private SimpleCursorAdapter mSoundsSpinnerAdapter;
    private SoundsContentProvider mSounds;

    public static DeleteSoundListDialog newInstance(String key, String name, int[] refCounts) {
        DeleteSoundListDialog fragment = new DeleteSoundListDialog();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putString(ARG_NAME, name);
        args.putInt(ARG_NUM_RHYTHMS, refCounts[0]);
        args.putInt(ARG_NUM_BEAT_TYPES, refCounts[1]);
        fragment.setArguments(args);
        return fragment;
    }

    public DeleteSoundListDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args.containsKey(ARG_KEY)) {        // only when changing existing
            mKey = args.getString(ARG_KEY);
            mName = args.getString(ARG_NAME);
            mRefRhythms = args.getInt(ARG_NUM_RHYTHMS);
            mRefBeatTypes = args.getInt(ARG_NUM_BEAT_TYPES);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (KybActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
        mSounds = (SoundsContentProvider) mResourceManager.getLibrary().getSounds();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.delete_beat_type_or_sound_migrate_list, null);
        mRhythmsOrBeatTypesListView = (ListView) dlgLayout.findViewById(android.R.id.list);
        mSoundsSpinner = (Spinner) dlgLayout.findViewById(R.id.spinner);

        // delete a custom sound shows a warning about the sound file
        dlgLayout.findViewById(R.id.delete_custom_sound_warning_id).setVisibility(View.VISIBLE);

        fillList();
        fillSpinner();

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setTitle(getString(R.string.delete_sound_migrate_title, mName))
                .setView(dlgLayout)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mDialog = bld.create();
        mDialog.setCancelable(true);
        mDialog.setOnShowListener(this);
        return mDialog;
    }

    private void fillList() {

        mAdapter = makeRhythmsOrBeatTypesAdapter(mActivity, mRefRhythms);

        mRhythmsOrBeatTypesListView.setAdapter(mAdapter);

        getLoaderManager().initLoader(LOADER_FILL_RHYTHMS_LIST, null, this);
    }

    static SimpleCursorAdapter makeRhythmsOrBeatTypesAdapter(final KybActivity mActivity, final int refRhythms) {

        int[] toLayoutViewIds = new int[] { android.R.id.text1 };

        String[] dbColumnsForLayout = new String[] {
                refRhythms > 0 ? KybSQLiteHelper.COLUMN_RHYTHM_NAME
                        : KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME };


        return new SimpleCursorAdapter(mActivity, android.R.layout.simple_list_item_1, null, dbColumnsForLayout, toLayoutViewIds, 0) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                super.bindView(view, context, cursor);

                if (!(view instanceof TextView)) {
                    AndroidResourceManager.logw(LOG_TAG, "bindView: unable to change the name as wrong type = " + view);
                    return;
                }

                if (refRhythms > 0) {
                    String cursorName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_RHYTHM_NAME));
                    if (cursorName.equals(KybSQLiteHelper.NEW_RHYTHM_ORDERING_NAME)) {
                        ((TextView) view).setText(mActivity.getString(R.string.newRhythm));
                    }
                }
                else { // beat types
                    String btName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME));
                    // get the localised string for internal name
                    String resName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
                    if (resName != null) {
                        int resId = AbstractSqlImpl.getLocalisedResIdFromName(resName, context.getResources());
                        if (resId > 0) {
                            btName = mActivity.getString(resId);
                        }
                    }

                    ((TextView) view).setText(btName);
                }
            }
        };
    }

    static SimpleCursorAdapter makeSpinnerAdapter(final KybActivity mActivity, final String translatedColumnName, String[] dbColumnsForLayout) {
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

                String soundName = cursor.getString(cursor.getColumnIndex(translatedColumnName));
                // get the localised string for the name of internal sounds
                String resName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
                if (resName != null) {
                    int resId = AbstractSqlImpl.getLocalisedResIdFromName(resName, context.getResources());
                    if (resId > 0) {
                        soundName = context.getResources().getString(resId);
                    }
                }

                tv.setText(soundName);
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        return adapter;
    }

    private void fillSpinner() {

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_SOUND_NAME };
        int[] toLayoutViewIds = new int[] { android.R.id.text1 };

        mSoundsSpinnerAdapter = makeSpinnerAdapter(mActivity, KybSQLiteHelper.COLUMN_SOUND_NAME, dbColumnsForLayout);
        mSoundsSpinner.setAdapter(mSoundsSpinnerAdapter);

        getLoaderManager().initLoader(LOADER_FILL_SOUNDS_SPINNER, null, this);
    }

    @Override
    public void onClick(View view) {
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        // bit complicated, the rhythms that are referencing the beat type before deleting it
                        final Library library = mResourceManager.getLibrary();

                        new BeatTypesAndSoundsCommand.DeleteSound(mSounds.lookup(mKey),
                                mRefRhythms == 0 ? null : makeRhythmsList(mResourceManager, mAdapter),
                                makeBeatTypesList(library),
                                mSounds.lookup(getDbKeyFromSpinner(mSoundsSpinner)))
                                .execute();
                    }
                }, new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        dismiss();
                    }
                });
    }

    /**
     * Beat types is 2nd choice for the display dialog_simple_list, so test if have them in the cursor, and if not
     * need to get them from the db
     * @param library
     */
    private BeatType[] makeBeatTypesList(Library library) {

        if (mRefBeatTypes == 0) {
            return null;
        }

        final BeatTypesContentProvider beatTypes = (BeatTypesContentProvider) library.getBeatTypes();

        Cursor csr = mRefRhythms == 0 ? mAdapter.getCursor()
                : beatTypes.getReferencedBeatTypes(mKey);

        BeatType[] beatTypesArr = new BeatType[csr.getCount()];

        try {
            csr.moveToFirst();
            while (!csr.isAfterLast()) {
                beatTypesArr[csr.getPosition()] = beatTypes.lookup(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
                csr.moveToNext();
            }

            return beatTypesArr;
        }
        finally {
            if (mRefRhythms > 0) { // this cursor is not the adapter's
                csr.close();
            }
        }
    }

    static Rhythm[] makeRhythmsList(AndroidResourceManager resourceManager, SimpleCursorAdapter adapter) {

        final RhythmsContentProvider rhythms = (RhythmsContentProvider) resourceManager.getLibrary().getRhythms();

        Cursor csr = adapter.getCursor();
        int csrCount = csr.getCount();
        Rhythm[] rhythmsArr = new Rhythm[csrCount];

        csr.moveToFirst();
        while (!csr.isAfterLast()) {
            String key = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
            rhythmsArr[csr.getPosition()] = key.equals(KybSQLiteHelper.NEW_RHYTHM_STRING_KEY) ? rhythms.getExistingNewRhythm() : rhythms.lookup(key);
            csr.moveToNext();
        }

        return rhythmsArr;
    }

    static String getDbKeyFromSpinner(Spinner spinner) {
        Cursor value = (Cursor) spinner.getSelectedItem();
        return value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
    }


    // ------------------------ LoaderCallbacks

    // creates a new loader after the initLoader() call
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if (LOADER_FILL_RHYTHMS_LIST == id) {

            // either rhythms or beat types, default is rhythms unless there aren't any
            if (mRefRhythms > 0) {
                return getRhythmsCursorLoader(mActivity, mKey);
            }
            else {
                Uri uri = BeatTypesContentProvider.CONTENT_URI;
                String selection = String.format(KybContentProvider.COLUMN_MATCH_STRING_FORMAT, KybSQLiteHelper.COLUMN_SOUND_FK, mKey);
                return new CursorLoader(mActivity, uri
                        , BeatTypesContentProvider.BEAT_TYPE_COLUMNS, selection, null, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME);
            }
        }
        else {
            // sounds spinner
            String[] projection = {
                    KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                    , KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME };

            String selection = String.format("%s != '%s'", KybSQLiteHelper.COLUMN_EXTERNAL_KEY, mKey);

            CursorLoader cursorLoader = new CursorLoader(mActivity, SoundsContentProvider.CONTENT_URI,
                    projection, selection , null, SoundsContentProvider.LIST_ORDER_BY);

            return cursorLoader;
        }
    }

    @NonNull
    static Loader<Cursor> getRhythmsCursorLoader(Context activity, String refKey) {

        String selection = String.format("%s like '%s%s%s'", KybSQLiteHelper.COLUMN_ENCODED_BEATS, "%", refKey, "%");

        return new CursorLoader(activity, RhythmsContentProvider.CONTENT_INCL_NEW_RHYTHM_URI
                , RhythmsContentProvider.RHYTHMS_INCL_NEW_COLUMNS, selection, null, KybSQLiteHelper.COLUMN_RHYTHM_NAME);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (LOADER_FILL_RHYTHMS_LIST == loader.getId()) {
            mAdapter.swapCursor(data);
        }
        else {
            mSoundsSpinnerAdapter.swapCursor(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (LOADER_FILL_RHYTHMS_LIST == loader.getId()) {
            mAdapter.swapCursor(null);
        }
        else {
            mSoundsSpinnerAdapter.swapCursor(null);
        }
    }

    //-------------------- dialog on show listener allows override onClick for new tag button (neutral button)

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
    }

}
