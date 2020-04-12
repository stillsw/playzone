package com.stillwindsoftware.keepyabeat.gui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

/**
 * Use the {@link RenameSoundDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RenameSoundDialog extends DialogFragment
        implements View.OnClickListener, DialogInterface.OnShowListener {

    static final String LOG_TAG = "KYB-"+RenameSoundDialog.class.getSimpleName();

    // the fragment initialization parameters
    private static final String ARG_KEY = "argKey";
    private static final String ARG_NAME = "argName";

    private String mKey;
    private String mName;

    private KybActivity mActivity;
    private AndroidResourceManager mResourceManager;

    private AlertDialog mDialog;
    private EditText mSoundName;
    private boolean mCalledToAddTagToRhythm = false;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param key Parameter 1.
     * @param name Parameter 2.
     * @return
     */
    public static RenameSoundDialog newInstance(String key, String name) {
        RenameSoundDialog fragment = new RenameSoundDialog();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    public RenameSoundDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args.containsKey(ARG_KEY)) {        // only when changing existing
            mKey = args.getString(ARG_KEY);
            mName = args.getString(ARG_NAME);
        }
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mActivity = (KybActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.single_edittext_dialog, null);
        mSoundName = (EditText) dlgLayout.findViewById(R.id.name);
        KybDialogFragment.trackTextLength(mSoundName, (TextView)dlgLayout.findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.maxRhythmNameLen));
        mSoundName.setText(mName); // will be null if called to add new

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.changeSoundNameTitle)
                .setView(dlgLayout)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mDialog = bld.create();
        mDialog.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
        mDialog.setOnShowListener(this);           // use onShow() to set this as click listener, this prevents auto-close
        return mDialog;
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        if (view instanceof Button && getString(R.string.ok_button).equals(((Button)view).getText().toString())) {
            validateAndSaveChanges();
        }
    }

    private void validateAndSaveChanges() {
        // validation on ok button

        final String newName = RhythmEncoder.sanitizeString(mResourceManager, mSoundName.getText().toString(), RhythmEncoder.SANITIZE_STRING_REPLACEMENT_CHAR);

        if (newName.isEmpty()) {     // name cannot be null
            Toast.makeText(mActivity, R.string.enterUniqueName, Toast.LENGTH_SHORT).show();
        }
        else { // validate name is unique and make changes

            // db validation and changes in background thread, success or error is shown in ui thread
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {
                            final KybSQLiteHelper library = (KybSQLiteHelper) mResourceManager.getLibrary();
                            final SoundsContentProvider sounds = (SoundsContentProvider) library.getSounds();

                            // validate a name change (where not changing the first test will fail)
                            if (newName.equals(mName)) {
                                return null; // nothing to do, it hasn't changed
                            }

                            if (sounds.nameExists(newName, mKey)) {
                                return getString(R.string.nameUsed);
                            }

                            // valid and there's something to do, make the db changes

                            new BeatTypesAndSoundsCommand.ChangeSoundName(sounds.lookup(mKey), newName).execute();

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

}
