package com.stillwindsoftware.keepyabeat.gui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.TagsContentProvider;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;

/**
 * Called from the SetRhythmNameAndTagsActivity
 * Use the {@link ChooseRhythmTagsDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChooseRhythmTagsDialog extends KybDialogFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, DialogInterface.OnShowListener {

    static final String LOG_TAG = "KYB-"+ChooseRhythmTagsDialog.class.getSimpleName();

    private static final int LOADER_TAGS_ADAPTER_ID = 1;

    private SimpleCursorAdapter mTagsListAdapter;

    private AlertDialog mDialog;
    private ListView mListView;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment SetRhythmNameAndTagsDialog.
     */
    public static ChooseRhythmTagsDialog newInstance() {
        ChooseRhythmTagsDialog fragment = new ChooseRhythmTagsDialog();
        return fragment;
    }

    public ChooseRhythmTagsDialog() {
        // Required empty public constructor
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        mListView = (ListView) mActivity.getLayoutInflater().inflate(R.layout.dialog_simple_list, null);

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setView(mListView)
                .setNeutralButton(R.string.addNewTag, null);

        fillTagsList();

        mDialog = bld.create();
        mDialog.setOnShowListener(this);           // use onShow() to set this as click listener, this prevents auto-close
        return mDialog;
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(this);
    }

    /**
     * Almost the same as the method of the same name in RhythmsListFragment,
     * but not enough to put them together
     */
    private void fillTagsList() {

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_TAG_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY, KybSQLiteHelper.COLUMN_ID };
        int[] toLayoutViewIds = new int[] { R.id.tag_name, R.id.tag_key, R.id.internal_id };

        mTagsListAdapter = new SimpleCursorAdapter(mActivity, R.layout.tag_multi_choice_spinner_row, null, dbColumnsForLayout, toLayoutViewIds, 0) {
// NOTE if need to replace name with localized name see RhythmBeatTypesFragment (sound adapter)

            @Override
            public void bindView(View v, Context context, Cursor cursor) {

                try {
                    super.bindView(v, context, cursor);
                } catch (NullPointerException e) {
                    // somewhere in the android code an NPE is generated when this spinner is open, but everything still works
                    // so go ahead anyway
                    AndroidResourceManager.logd(LOG_TAG, "bindView: super generated NPE, presume because of screen change while spinner open", e);
                }

                // seems this method is called for the text view and also for the row layout
                RelativeLayout layout = (RelativeLayout) v;
                layout.setOnClickListener(null); // don't want to fire while setting up

                TextView tagKey = (TextView) layout.findViewById(R.id.tag_key);
                TextView tv = (TextView) layout.findViewById(R.id.tag_name);
                tv.setText(cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_TAG_NAME)));
                final String key = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
                tagKey.setText(key);

                CheckBox selectBox = (CheckBox) layout.findViewById(R.id.tag_checkbox);
                selectBox.setChecked(isSelectedRhythmTag(key));

                // click anywhere on the layout toggles the setting
                layout.setOnClickListener((SetRhythmNameAndTagsActivity)mActivity);
            }
        };

        mListView.setAdapter(mTagsListAdapter);

        getLoaderManager().initLoader(LOADER_TAGS_ADAPTER_ID, null, this);
   }

    /**
     * Called during bindView() to set the selected box
     * @param key
     * @return
     */
    private boolean isSelectedRhythmTag(String key) {
        return ((SetRhythmNameAndTagsActivity)mActivity).isSelectedTag(key);
    }

    // ------------------------ LoaderCallbacks

    // creates a new loader after the initLoader() call
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        String[] projection = {
                KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                , KybSQLiteHelper.COLUMN_TAG_NAME };

        String selection = null;
        CursorLoader cursorLoader = new CursorLoader(mActivity, TagsContentProvider.CONTENT_URI, projection, selection , null, null);

        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mTagsListAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mTagsListAdapter.swapCursor(null);
    }

    // ------------------------ OnClickListener

    @Override
    public void onClick(View view) {

        if (view instanceof Button && getString(R.string.addNewTag).equals(((Button)view).getText().toString())) {

            // add a new tag, popup a dialog to get a name and confirm click
            AddNewOrRenameTagDialog.newInstance(true) // yes, adding to rhythm
                    .show(mActivity.getSupportFragmentManager(), AddNewOrRenameTagDialog.LOG_TAG);
        }
        else { // must be the cancel button
            hideKeypad(view);
            dismiss();
        }
    }

}
