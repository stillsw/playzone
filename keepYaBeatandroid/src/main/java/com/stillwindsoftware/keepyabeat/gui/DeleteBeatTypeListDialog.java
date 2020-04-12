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
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * Invoked from BeatTypesList when deleting a beat type referenced by
 * rhythms that need to be migrated to another beat type
 */
public class DeleteBeatTypeListDialog extends DialogFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener, DialogInterface.OnShowListener {

    static final String LOG_TAG = "KYB-"+DeleteBeatTypeListDialog.class.getSimpleName();
    private static final String ARG_BT_KEY = "argBtKey";
    private static final String ARG_BT_NAME = "argBtName";
    private static final int LOADER_FILL_RHYTHMS_LIST = 0;
    private static final int LOADER_FILL_BEAT_TYPES_SPINNER = 1;

    private KybActivity mActivity;
    private AndroidResourceManager mResourceManager;

    private String mName;
    private String mKey;

    private AlertDialog mDialog;
    private ListView mRhythmsListView;
    private SimpleCursorAdapter mAdapter;
    private Spinner mBeatTypesSpinner;
    private SimpleCursorAdapter mBeatTypesSpinnerAdapter;

    public static DeleteBeatTypeListDialog newInstance(String key, String name) {
        DeleteBeatTypeListDialog fragment = new DeleteBeatTypeListDialog();
        Bundle args = new Bundle();
        args.putString(ARG_BT_KEY, key);
        args.putString(ARG_BT_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    public DeleteBeatTypeListDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args.containsKey(ARG_BT_KEY)) {        // only when changing existing
            mKey = args.getString(ARG_BT_KEY);
            mName = args.getString(ARG_BT_NAME);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (KybActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.delete_beat_type_or_sound_migrate_list, null);
        mRhythmsListView = (ListView) dlgLayout.findViewById(android.R.id.list);
        mBeatTypesSpinner = (Spinner) dlgLayout.findViewById(R.id.spinner);
        dlgLayout.findViewById(R.id.delete_custom_sound_warning_id).setVisibility(View.GONE);

        fillList();
        fillSpinner();

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setTitle(getString(R.string.delete_beat_type_migrate_title, mName))
                .setView(dlgLayout)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mDialog = bld.create();
        mDialog.setCancelable(true);
        mDialog.setOnShowListener(this);
        return mDialog;
    }

    private void fillList() {

        int[] toLayoutViewIds = new int[] { android.R.id.text1 };

        mAdapter = DeleteSoundListDialog.makeRhythmsOrBeatTypesAdapter(mActivity, 1); // >0 indicates have rhythms

        mRhythmsListView.setAdapter(mAdapter);

        getLoaderManager().initLoader(LOADER_FILL_RHYTHMS_LIST, null, this);
    }

    private void fillSpinner() {

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME };
        int[] toLayoutViewIds = new int[] { android.R.id.text1 };

        mBeatTypesSpinnerAdapter = DeleteSoundListDialog.makeSpinnerAdapter(mActivity, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, dbColumnsForLayout);
        mBeatTypesSpinner.setAdapter(mBeatTypesSpinnerAdapter);

        getLoaderManager().initLoader(LOADER_FILL_BEAT_TYPES_SPINNER, null, this);
    }

    @Override
    public void onClick(View view) {
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        // bit complicated, the rhythms that are referencing the beat type before deleting it
                        final BeatTypes beatTypes = mResourceManager.getLibrary().getBeatTypes();

                        new BeatTypesAndSoundsCommand.DeleteBeatType(beatTypes.lookup(mKey),
                                DeleteSoundListDialog.makeRhythmsList(mResourceManager, mAdapter),
                                beatTypes.lookup(DeleteSoundListDialog.getDbKeyFromSpinner(mBeatTypesSpinner)))
                                .execute();
                    }
                }, new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        dismiss();
                    }
                });
    }

    // ------------------------ LoaderCallbacks

    // creates a new loader after the initLoader() call
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if (LOADER_FILL_RHYTHMS_LIST == id) {
            return DeleteSoundListDialog.getRhythmsCursorLoader(mActivity, mKey);
        }
        else {
            // beat types spinner
            String[] projection = {
                    KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                    , KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME };

            String selection = String.format("%s != '%s'", KybSQLiteHelper.COLUMN_EXTERNAL_KEY, mKey);

            CursorLoader cursorLoader = new CursorLoader(mActivity, BeatTypesContentProvider.CONTENT_URI,
                    projection, selection , null, BeatTypesContentProvider.LIST_ORDER_BY);

            return cursorLoader;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (LOADER_FILL_RHYTHMS_LIST == loader.getId()) {
            mAdapter.swapCursor(data);
        }
        else {
            mBeatTypesSpinnerAdapter.swapCursor(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (LOADER_FILL_RHYTHMS_LIST == loader.getId()) {
            mAdapter.swapCursor(null);
        }
        else {
            mBeatTypesSpinnerAdapter.swapCursor(null);
        }
    }

    //-------------------- dialog on show listener allows override onClick for new tag button (neutral button)

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
    }

}
