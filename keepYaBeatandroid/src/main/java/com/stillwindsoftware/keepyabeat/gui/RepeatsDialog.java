package com.stillwindsoftware.keepyabeat.gui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.PlayerStateCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;

/**
 * A dialog that pops up when the user presses the repeats button and which updates the player state
 * with a new value 
 */
public class RepeatsDialog extends KybDialogFragment {

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+RepeatsDialog.class.getSimpleName();

	private RadioButton mContinuousRadio;
	private RadioButton mOnceRadio;
	private RadioButton mNTimesRadio;

	private SettingsManager mSettings;
	private PlayerState mPlayerState;

	private Rhythm mRhythm;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		
		mRhythm = mActivity.getRhythm();
		AndroidResourceManager rm = ((KybApplication)mActivity.getApplication()).getResourceManager();
		mSettings = (SettingsManager) rm.getPersistentStatesManager();
		mPlayerState = mSettings.getPlayerState();
		RepeatOption ro = mPlayerState.getRepeatOption();

		final ScrollView sv = (ScrollView) mActivity.getLayoutInflater().inflate(R.layout.repeats_dialog_layout, null);

		initRepeatOption(ro, (RelativeLayout) sv.getChildAt(0));
		
		AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
			.setView(sv)
			.setTitle(R.string.changeRepeatsTitle)
			.setPositiveButton(R.string.ok_button, this)
			.setNegativeButton(R.string.cancel_button, this);
		
		Dialog dlg = bld.create();
		dlg.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
		return dlg;
	}

	private void initRepeatOption(RepeatOption ro, RelativeLayout rl) {

		mContinuousRadio = (RadioButton) rl.findViewById(R.id.repeats_option_infinite);
		mOnceRadio = (RadioButton) rl.findViewById(R.id.repeats_option_once);
		mNTimesRadio = (RadioButton) rl.findViewById(R.id.repeats_option_ntimes);

        initSeekBarAndManualEdit(rl.findViewById(R.id.seek_repeats),
                rl.findViewById(R.id.repeats_value), R.id.repeats_value,
                mPlayerState.getNTimes(),
                getResources().getInteger(R.integer.nTimesRepeatsMin),
                getResources().getInteger(R.integer.nTimesRepeatsMax));

		setRepeatRadioOption(ro);
		
		mContinuousRadio.setOnClickListener(this);
		mOnceRadio.setOnClickListener(this);
		mNTimesRadio.setOnClickListener(this);
		
	}

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.repeats_option_infinite) {
            setRepeatRadioOption(RepeatOption.Infinite);
        }
        else if (v.getId() == R.id.repeats_option_once) {
            setRepeatRadioOption(RepeatOption.NoRepeat);
        }
        else if (v.getId() == R.id.repeats_option_ntimes) {
            setRepeatRadioOption(RepeatOption.RepeatNtimes);
        }
        else {
            super.onClick(v);
        }
    }

    private void setRepeatRadioOption(RepeatOption ro) {
		
		switch (ro) {
		case Infinite : 
			mContinuousRadio.setChecked(true); 
			break;
		case NoRepeat : 
			mOnceRadio.setChecked(true);
			break;
		case RepeatNtimes : 
			mNTimesRadio.setChecked(true);
			break;
		}

		setNTimesFields(mNTimesRadio.isChecked());
	}

	protected void setNTimesFields(boolean checked) {
		mManualEntryEdit.setEnabled(checked);
		mSeekBar.setEnabled(checked);
	}

	/**
	 * Update the value on the player state
	 */
	@Override
	public void onClick(DialogInterface dialog, int which) {
        hideKeypad();

		if (which == Dialog.BUTTON_POSITIVE) {

			int min = getResources().getInteger(R.integer.nTimesRepeatsMin);

			// check for no changes and get out fast if so
			switch (mPlayerState.getRepeatOption()) {
			case Infinite : 
				if (mContinuousRadio.isChecked()) {
					return;
				}
				break;
			case NoRepeat : 
				if (mOnceRadio.isChecked()) {
					return;
				}
				break;
			case RepeatNtimes : 
				if (mNTimesRadio.isChecked() && mPlayerState.getNTimes() == min + mSeekBar.getProgress()) {
					return;
				}
				break;
			}
			
			new PlayerStateCommand.ChangeRepeatsCommand(
					((KybApplication) mActivity.getApplication()).getResourceManager(), mRhythm, 
					mContinuousRadio.isChecked(), mOnceRadio.isChecked(), min + mSeekBar.getProgress()).execute();

		}
		else {
			// cancel
			// just closes anyway, nothing to undo
		}
	}

}
