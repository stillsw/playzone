package com.stillwindsoftware.keepyabeat.gui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.TagSqlImpl;
import com.stillwindsoftware.keepyabeat.db.TagsContentProvider;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.transactions.TagsCommand;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

/**
 * Use the {@link AddNewOrRenameTagDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AddNewOrRenameTagDialog extends KybDialogFragment
        implements DialogInterface.OnShowListener {

    static final String LOG_TAG = "KYB-"+AddNewOrRenameTagDialog.class.getSimpleName();

    // the fragment initialization parameters
    private static final String ARG_IS_TO_ADD_TO_RHYTHM = "argIsAddToRhythm";
    private static final String ARG_TAG_KEY = "argTagKey";
    private static final String ARG_TAG_NAME = "argTagName";

    private String mKey;
    private String mName;

    private AlertDialog mDialog;
    private EditText mTagName;
    private boolean mCalledToAddTagToRhythm = false;

    /**
     * @return A new instance of fragment AddNewOrRenameTagDialog
     */
    public static AddNewOrRenameTagDialog newInstance(boolean isAddingToRhythm) {
        AddNewOrRenameTagDialog fragment = new AddNewOrRenameTagDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_TO_ADD_TO_RHYTHM, isAddingToRhythm);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param key Parameter 1.
     * @param name Parameter 2.
     * @return
     */
    public static AddNewOrRenameTagDialog newInstance(String key, String name) {
        AddNewOrRenameTagDialog fragment = new AddNewOrRenameTagDialog();
        Bundle args = new Bundle();
        args.putString(ARG_TAG_KEY, key);
        args.putString(ARG_TAG_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    public AddNewOrRenameTagDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args.containsKey(ARG_TAG_KEY)) {        // only when changing existing
            mKey = args.getString(ARG_TAG_KEY);
            mName = args.getString(ARG_TAG_NAME);
        }
        mCalledToAddTagToRhythm = args.getBoolean(ARG_IS_TO_ADD_TO_RHYTHM, false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.single_edittext_dialog, null);
        mTagName = (EditText) dlgLayout.findViewById(R.id.name);
        trackTextLength(mTagName, (TextView)dlgLayout.findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.maxTagNameLen));
        mTagName.setText(mName); // will be null if called to add new

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setTitle(mName == null ? R.string.addNewTag : R.string.changeTagTitle)
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
        mDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(this);
        if (mTagName.getText().toString().isEmpty()) {
            forceShowKeypad(mTagName);
        }
    }

    @Override
    public void onClick(View view) {
        hideKeypad(view);
        if (view instanceof Button && getString(R.string.ok_button).equals(((Button)view).getText().toString())) {
            validateAndSaveChanges();
        }
        else {
            dismiss();
        }
    }

    private void validateAndSaveChanges() {
        // validation on ok button

        final String newName = RhythmEncoder.sanitizeString(mResourceManager, mTagName.getText().toString(), RhythmEncoder.SANITIZE_STRING_REPLACEMENT_CHAR);

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
                            final TagsContentProvider tags = (TagsContentProvider) library.getTags();

                            // validate a name change (where not changing the first test will fail)
                            String excludeKey = mKey == null ? "xx" : mKey;
                            if (!newName.equals(mName) && tags.nameExists(newName, excludeKey)) {
                                return getString(R.string.nameUsed);
                            }

                            // valid and there's something to do, make the db changes

                            if (mKey == null) { // adding as there's no existing key
                                mResourceManager.startTransaction(); // handling own transaction and background task
                                TagsCommand.AddTag cmd = new TagsCommand.AddTag(mResourceManager, newName);
                                cmd.execute();

                                // add the new tag to the rhythm, but it has to be via another mechanism otherwise there's a leak error
                                if (mCalledToAddTagToRhythm) {
                                    return cmd.getTag();
                                }
                            }
                            else { // changing existing tag name
                                new TagsCommand.ChangeTagName(tags.lookup(mKey), newName).execute();
                            }

                            return null; // null means success as there's no error message to display
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam == null) { // no param means success
                                dismiss();
                            }
                            else if (mParam instanceof Tag) {
                                ((SetRhythmNameAndTagsActivity) mActivity).newTagAddedForRhythm((TagSqlImpl) mParam);
                                dismiss();
                            }
                            else { // the param is a string, invalid because the name exists
                                Toast.makeText(mActivity, (String)mParam, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            );
        }
    }

}
