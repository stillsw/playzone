package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupMenu;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.TagsContentProvider;
import com.stillwindsoftware.keepyabeat.model.transactions.TagsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * TagsList module invoked from the options menu. Can rename or delete a tag or add a new one.
 */
public class TagsListDialog extends KybDialogFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, DialogInterface.OnShowListener, AdapterView.OnItemLongClickListener, PopupMenu.OnMenuItemClickListener {

    static final String LOG_TAG = "KYB-"+TagsListDialog.class.getSimpleName();

    private AlertDialog mDialog;
    private ListView mTagsListView;
    private SimpleCursorAdapter mAdapter;
    private String mName;
    private String mKey;

    public TagsListDialog() {
        // Required empty public constructor
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.tags_list, null);
        mTagsListView = (ListView) dlgLayout.findViewById(android.R.id.list);
        mTagsListView.setOnItemLongClickListener(this);

        fillList();

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.moduleTags)
                .setView(dlgLayout)
                .setNeutralButton(R.string.addNewTag, null);

        mDialog = bld.create();
        mDialog.setCancelable(true);
        mDialog.setOnShowListener(this);
        return mDialog;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        AndroidResourceManager.logw(LOG_TAG, "onContextItemSelected:");

        // get the position in the dialog_simple_list so can find the data from the cursor
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        Cursor csr = mAdapter.getCursor();
        csr.moveToPosition(info.position);
        final String key = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
        String name = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_TAG_NAME));

        // and the option selected
        switch (item.getItemId()) {
            case R.id.menu_edit:
                //TODO
                AndroidResourceManager.logw(LOG_TAG, "onContextItemSelected: EDIT key=" + key + " name=" + name + " NOT IMPLEMENTED YET");
                break;
            case R.id.menu_delete:
                AndroidResourceManager.logw(LOG_TAG, "onContextItemSelected: DELETE key=" + key + " name=" + name + " NOT IMPLEMENTED YET");
//                mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
//                        new ParameterizedCallback(false) {
//                            @Override
//                            public void run() {
//                                mResourceManager.startTransaction();
//                                Rhythm rhythm = mResourceManager.getLibrary().getRhythms().lookup(key);
//                                new RhythmCommandAdaptor.RhythmDeleteCommand(rhythm).execute();
//                            }
//                        });
                break;
            default:
                AndroidResourceManager.loge(LOG_TAG, "onContextItemSelected: should never see this, a menu option selected that isn't on the menu?");
                return super.onContextItemSelected(item);
        }
        return true;
    }

    private void fillList() {

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_TAG_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
        int[] toLayoutViewIds = new int[] { R.id.tag_name, R.id.external_key };

        mAdapter = new SimpleCursorAdapter(mActivity, R.layout.tag_row, null, dbColumnsForLayout, toLayoutViewIds, 0);

        mTagsListView.setAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onClick(View view) {
        // add a new tag, popup a dialog to get a name and confirm click
        AddNewOrRenameTagDialog.newInstance(false) // no, not adding to a rhythm
                .show(mActivity.getSupportFragmentManager(), AddNewOrRenameTagDialog.LOG_TAG);
    }

    // ------------------------ LoaderCallbacks

    // creates a new loader after the initLoader() call
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        Uri uri = TagsContentProvider.CONTENT_URI;
        String[] selectArgs = null;
        String selection = null;
        CursorLoader cursorLoader = new CursorLoader(mActivity, uri
                , TagsContentProvider.TAG_COLUMNS, selection , selectArgs, KybSQLiteHelper.COLUMN_TAG_NAME);

        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    //-------------------- dialog on show listener allows override onClick for new tag button (neutral button)

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(this);
    }

    // ------------------- to get pop up menu for items in the dialog_simple_list, since register for context menu doesn't work here

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {

        Cursor csr = mAdapter.getCursor();
        if (csr == null || csr.getCount() == 0) {
            AndroidResourceManager.loge(LOG_TAG, "onItemLongClick: could not popup menu as unexpectedly no cursor");
            return true;
        }

        csr.moveToPosition(position);
        // no easy way to pass the context to the popup, just set into member variables (bit hacky)
        mName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_TAG_NAME));
        mKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));

        PopupMenu popup = new PopupMenu(mActivity, view);
        popup.getMenuInflater().inflate(R.menu.tags_list_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popup.show();

        return true;
    }

    //-------------- notify on popup menu click

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();

        if (id == R.id.menu_edit) {
            AddNewOrRenameTagDialog.newInstance(mKey, mName).show(mActivity.getSupportFragmentManager(), AddNewOrRenameTagDialog.LOG_TAG);
            return true;
        }
        else if (id == R.id.menu_delete) {
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            final KybSQLiteHelper library = (KybSQLiteHelper) mResourceManager.getLibrary();
                            final TagsContentProvider tags = (TagsContentProvider) library.getTags();

                            new TagsCommand.DeleteTag(tags.lookup(mKey)).execute();
                        }
                    });
            return true;
        }
        return false;
    }
}
