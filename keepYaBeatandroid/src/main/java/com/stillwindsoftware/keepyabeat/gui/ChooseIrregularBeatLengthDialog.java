package com.stillwindsoftware.keepyabeat.gui;

import android.annotation.SuppressLint;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.model.Beat;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.model.transactions.PlayerStateCommand;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * A dialog that pops up when the user presses the repeats button and which updates the player state
 * with a new value 
 */
public class ChooseIrregularBeatLengthDialog extends KybDialogFragment {

	public static final String LOG_TAG = "KYB-"+ChooseIrregularBeatLengthDialog.class.getSimpleName();

	private EditRhythmActivity mEditorActivity;

    private int mStartValue = Beat.FullBeat.FULL_BEAT_VALUE_FULL; // will be set on input
    private int mNewValue;
    private GuiManager.FullBeatValueChangedCallback mCallback;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		mEditorActivity = (EditRhythmActivity) getActivity();
        Object[] params = ((EditRhythmActivity)mActivity).getRhythmPlayingHolder().getChangeFullBeatValueParams();
        mStartValue = (Integer) params[0];
        mCallback = (GuiManager.FullBeatValueChangedCallback) params[1];

		final ScrollView sv = (ScrollView) mEditorActivity.getLayoutInflater().inflate(R.layout.irregular_beat_lengths_dialog, null);

		initOptions((RadioGroup) sv.getChildAt(0));

		Builder bld = new Builder(mEditorActivity)
			.setView(sv)
            .setTitle(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_FULL ? getString(R.string.MENU_MAKE_IRREGULAR_BEAT) : getString(R.string.MENU_CHANGE_BEAT_VALUE));
		
		Dialog dlg = bld.create();
		return dlg;
	}

	private void initOptions(RadioGroup radioGroup) {

        RadioButton btn = radioGroup.findViewById(R.id.ro_beatLenFull);
        btn.setOnClickListener(this);
        btn.setChecked(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_FULL);
		btn = radioGroup.findViewById(R.id.ro_beatLenQuarter);
        btn.setOnClickListener(this);
        btn.setChecked(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_QUARTER);
        btn = radioGroup.findViewById(R.id.ro_beatLenThird);
        btn.setOnClickListener(this);
        btn.setChecked(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_THIRD);
        btn = radioGroup.findViewById(R.id.ro_beatLenHalf);
        btn.setOnClickListener(this);
        btn.setChecked(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_HALF);
        btn = radioGroup.findViewById(R.id.ro_beatLen2Thirds);
        btn.setOnClickListener(this);
        btn.setChecked(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_2_THIRDS);
        btn = radioGroup.findViewById(R.id.ro_beatLen3Quarters);
        btn.setOnClickListener(this);
        btn.setChecked(mStartValue == Beat.FullBeat.FULL_BEAT_VALUE_3_QUARTERS);
	}

    @Override
    public void onClick(final View v) {

        mNewValue = mStartValue;
        switch (v.getId()) {
            case R.id.ro_beatLenFull: mNewValue = Beat.FullBeat.FULL_BEAT_VALUE_FULL; break;
            case R.id.ro_beatLenQuarter: mNewValue = Beat.FullBeat.FULL_BEAT_VALUE_QUARTER; break;
            case R.id.ro_beatLenThird: mNewValue = Beat.FullBeat.FULL_BEAT_VALUE_THIRD; break;
            case R.id.ro_beatLenHalf: mNewValue = Beat.FullBeat.FULL_BEAT_VALUE_HALF; break;
            case R.id.ro_beatLen2Thirds: mNewValue = Beat.FullBeat.FULL_BEAT_VALUE_2_THIRDS; break;
            case R.id.ro_beatLen3Quarters: mNewValue = Beat.FullBeat.FULL_BEAT_VALUE_3_QUARTERS; break;
        }

        if (mNewValue == mStartValue) { // no change
            return;
        }

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        mCallback.valueSelected(mNewValue);
                    }
                },
                new CauseEditorRedrawAndDismissCallback());
    }
}
