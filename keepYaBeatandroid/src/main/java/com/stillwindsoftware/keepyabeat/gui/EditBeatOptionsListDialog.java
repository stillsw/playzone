package com.stillwindsoftware.keepyabeat.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.player.BeatEditActionHandler;

/**
 * TagsList module invoked from the options menu. Can rename or delete a tag or add a new one.
 */
public class EditBeatOptionsListDialog extends KybDialogFragment {

    static final String LOG_TAG = "KYB-"+EditBeatOptionsListDialog.class.getSimpleName();

    private AlertDialog mDialog;
    private ListView mOptionsListView;
    private ArrayAdapter<String> mAdapter;
    private Object[] mBeatPopupMenuParams;

    public EditBeatOptionsListDialog() {
        // Required empty public constructor
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.mimic_dialog_list, null);
        mOptionsListView = (ListView) dlgLayout.findViewById(android.R.id.list);

        fillList();

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setAdapter(mAdapter, this);

        mDialog = bld.create();
        mDialog.setCancelable(true);
        mDialog.setOnCancelListener(this);
        return mDialog;
    }

    private void fillList() {
        mBeatPopupMenuParams = ((EditRhythmActivity)mActivity).getRhythmPlayingHolder().getBeatPopupMenuOptions();

         // the second object is the tree map of options
        mAdapter = new ArrayAdapter<String>(mActivity, android.R.layout.simple_list_item_1, (String[]) mBeatPopupMenuParams[1]);

        mOptionsListView.setAdapter(mAdapter);
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        AndroidResourceManager.logv(LOG_TAG, "onClick option="+i+" param="+((String[])this.mBeatPopupMenuParams[1])[i]);
        ((Runnable[])mBeatPopupMenuParams[2])[i].run();
        clearSelection();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        clearSelection();
    }

    /**
     * Called after clicking on an option, and also after cancelling the popup
     */
    private void clearSelection() {
        BeatEditActionHandler beatHandler = (BeatEditActionHandler) mBeatPopupMenuParams[0];
        beatHandler.clearSelection();
        mResourceManager.getGuiManager().runInGuiThread(new CauseEditorRedrawAndDismissCallback());
    }
}
