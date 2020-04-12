package com.stillwindsoftware.keepyabeat.gui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.player.EditedBeat;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * Use the {@link EditBeatNumberingDialog#newInstance} factory method to
 * create an instance of this fragment.
 */
public class EditBeatNumberingDialog extends KybDialogFragment
        implements DialogInterface.OnShowListener, CompoundButton.OnCheckedChangeListener {

    static final String LOG_TAG = "KYB-"+EditBeatNumberingDialog.class.getSimpleName();

    // the fragment initialization parameters
    private static final String ARG_BEAT_NUM = "argBeatNum";
    private static final String ARG_BEAT_NUM_LABEL = "argBeatDisplayLabel";
    private static final String ARG_BEAT_POSITION = "argBeatPosition";

    // passed as params
    private int mFullBeatNum, mOriFullBeatNum, mBeatPosition;
    private String mBeatNumLabel, mOriBeatNumLabel;

    private EditRhythmActivity mActivity;
    private AndroidResourceManager mResourceManager;

    private TextView mFullBeatNumTv;
    private EditText mBeatNumLabelEditText;
    private CheckBox mRenumberCheckbox;

    private AlertDialog mDialog;
    private boolean mLabelWasSameAsFullBeatNum;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param fullBeatNum Parameter 1.
     * @param displayLabel Parameter 2.
     * @return A new instance
     */
    public static EditBeatNumberingDialog newInstance(int fullBeatNum, String displayLabel, int position) {
        EditBeatNumberingDialog fragment = new EditBeatNumberingDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_BEAT_NUM, fullBeatNum);
        args.putString(ARG_BEAT_NUM_LABEL, displayLabel);
        args.putInt(ARG_BEAT_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    public EditBeatNumberingDialog() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // would be a program bug to not be there
        mOriFullBeatNum = mFullBeatNum = getArguments().getInt(ARG_BEAT_NUM);
        mOriBeatNumLabel = mBeatNumLabel = getArguments().getString(ARG_BEAT_NUM_LABEL);
        mLabelWasSameAsFullBeatNum = Integer.toString(mFullBeatNum).equals(mBeatNumLabel);
        mBeatPosition = getArguments().getInt(ARG_BEAT_POSITION);
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mActivity = (EditRhythmActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final RelativeLayout layout = (RelativeLayout) mActivity.getLayoutInflater().inflate(R.layout.edit_beat_numbering_dialog, null);

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setView(layout)
                .setTitle(R.string.beat_number_dialog_title)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mFullBeatNumTv = layout.findViewById(R.id.full_beat_num);
        mFullBeatNumTv.setText(Integer.toString(mFullBeatNum));
        mBeatNumLabelEditText = layout.findViewById(R.id.display_label);
        mBeatNumLabelEditText.setText(mBeatNumLabel);
        KybDialogFragment.trackTextLength(mBeatNumLabelEditText, (TextView)layout.findViewById(R.id.textLenIndicator), getResources().getInteger(R.integer.beatNumberLabelLen));
        mRenumberCheckbox = layout.findViewById(R.id.renumber_checkbox);
        TextView renumberCheckboxLabel = layout.findViewById(R.id.renumber_label);
        if (mFullBeatNum == 1) { // cannot be made to be 1
            mRenumberCheckbox.setVisibility(View.GONE);
            renumberCheckboxLabel.setVisibility(View.GONE);
        }
        else { // listen for changes to the checkbox
            mRenumberCheckbox.setOnCheckedChangeListener(this);
        }

        mDialog = bld.create();
        mDialog.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
        mDialog.setOnShowListener(this);           // use onShow() to set this as click listener, this prevents auto-close
        return mDialog;
    }

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
        hideKeypad(mBeatNumLabelEditText);
    }

    @Override
    public void onClick(View view) {

        if (view instanceof Button && getString(R.string.ok_button).equals(((Button)view).getText().toString())) {
            validateAndSaveChanges();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        String trimUserBeatNum = mBeatNumLabelEditText.getText().toString().trim();
        boolean isDisplaySync = trimUserBeatNum.length() > 0 && trimUserBeatNum.equals(Integer.toString(mFullBeatNum));

        if (checked) {
            // changing to be beat 1, first see if the display value is the same as current
            mFullBeatNum = 1;

            if (isDisplaySync) {
                mBeatNumLabelEditText.setText(Integer.toString(mFullBeatNum)); // keep in sync
            }
        }
        else {
            mFullBeatNum = mOriFullBeatNum;
            if (isDisplaySync && !trimUserBeatNum.equals(mOriBeatNumLabel)) { // not sync if the current value happens to be what was passed in (eg. 1)
                mBeatNumLabelEditText.setText(Integer.toString(mFullBeatNum));
            }
        }

        mFullBeatNumTv.setText(Integer.toString(mFullBeatNum));

    }

    private void validateAndSaveChanges() {
        // ok button pressed
        mBeatNumLabel = mBeatNumLabelEditText.getText().toString();
        // don't allow empty
        if (mBeatNumLabel.isEmpty()) {
            mBeatNumLabelEditText.setText(Integer.toString(mFullBeatNum));
            return;
        }

        final boolean fullBeatNumChanged = mFullBeatNum != mOriFullBeatNum;
        final boolean fullBeatNumNowMatchesLabel = Integer.toString(mFullBeatNum).equals(mBeatNumLabel);
        final boolean beatNumLabelChanged =
                (mLabelWasSameAsFullBeatNum                                         // started with beat label not manually changed
                        && fullBeatNumChanged                                       // and the count 1 full beat is now changed
                        && !fullBeatNumNowMatchesLabel)                             // but they're not in step, meaning beat label has been modified manually now
                ||                                                                  // OR
                !mBeatNumLabel.equals(mOriBeatNumLabel)                             // label is changed since came in
                        && (!mLabelWasSameAsFullBeatNum                             // and it wasn't the same as full beat num when came in either
                            || (mLabelWasSameAsFullBeatNum                          // OR it was same when started
                                && !fullBeatNumNowMatchesLabel));                   // but now it isn't

        final Rhythm rhythm = mActivity.getPlayRhythmFragment().getRhythm();

        if (rhythm == null) {
            AndroidResourceManager.loge(LOG_TAG, "validateAndSaveChanges: no rhythm present, can't do update(s)");
            Toast.makeText(mActivity, getString(R.string.UNEXPECTED_PROGRAM_ERROR), Toast.LENGTH_SHORT).show();
            return;
        }

        if (fullBeatNumChanged || beatNumLabelChanged) {

            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                    new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {

                            if (fullBeatNumChanged && !beatNumLabelChanged) { // single change as old way
                                new RhythmCommandAdaptor.RhythmNumberOneCommand(rhythm, mBeatPosition).execute();
                            }

                            if (beatNumLabelChanged) {
                                EditedBeat.EditedFullBeat beat = (EditedBeat.EditedFullBeat) rhythm.getBeatTree(BeatTree.EDIT).getFullBeatAt(mBeatPosition);
                                if (beat.getPosition() == mBeatPosition) {
                                    String newLabelValue = fullBeatNumNowMatchesLabel
                                            ? null                          // change to null, so just uses the full beat num and replaces manual override
                                            : mBeatNumLabel;
                                    new RhythmEditCommand.ChangeFullBeatDisplayedNumber(mResourceManager, beat, newLabelValue, fullBeatNumChanged).execute();
                                }
                                else {
                                    AndroidResourceManager.loge(LOG_TAG, "validateAndSaveChanges: beat at position="+mBeatPosition+" has another position="+beat.getPosition());
                                    return getString(R.string.UNEXPECTED_PROGRAM_ERROR);
                                }
                            }

                            return null; // null means success as there's no error message to display
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam == null) { // no error message means success
                                mActivity.getPlayRhythmFragment().causeRhythmRedraw(true, 500); // if the keyboard is open after it closes make sure enough redraws to relayout
                                mActivity.getPlayRhythmFragment().causeRhythmRedraw(true, 1000); // in case it was too soon the first time
                                dismiss();
                            }
                            else { // any error
                                Toast.makeText(mActivity, (String)mParam, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            );
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "validateAndSaveChanges: ok pressed but nothing to do");
        }

    }

}
