package com.stillwindsoftware.keepyabeat.gui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * A dialog that pops up when the user presses the magic wand button and then allows a bunch of beats
 * to be added or removed from the rhythm
 */
public class MagicWandDialog extends KybDialogFragment
        implements GuiManager.BeatTypeSelectedCallback, Runnable {

	public static final String LOG_TAG = "KYB-"+MagicWandDialog.class.getSimpleName();

	private SettingsManager mSettings;
	private PlayerState mPlayerState;
    private AlertDialog mDialog;
    private BeatType mBeatType;
    private ImageButton mColourBtn;
    private TextView mBeatTypeName;

    @SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

        int initialNumBeats = mActivity.getPlayRhythmFragment().getRhythmDraughter().getBeatTree().size();
		mSettings = (SettingsManager) mResourceManager.getPersistentStatesManager();
		mPlayerState = mSettings.getPlayerState();
		final RelativeLayout rl = (RelativeLayout) mActivity.getLayoutInflater().inflate(R.layout.magic_wand_dialog_layout, null);

        mColourBtn = rl.findViewById(R.id.colour_btn);
        mBeatTypeName = rl.findViewById(R.id.beat_type_name);

        mBeatTypeName.setOnClickListener(this);
        mColourBtn.setOnClickListener(this);

        initSeekBarAndManualEdit(rl.findViewById(R.id.seek_num_beats),
                rl.findViewById(R.id.num_beats_value), R.id.num_beats_value,
                initialNumBeats,
                getResources().getInteger(R.integer.minNumBeats),
                getResources().getInteger(R.integer.maxNumBeats));

        // the player state may need to look up the beat type, updating the views has to happen in the ui thread after that

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        mBeatType = mPlayerState.getBeatType();
                    }
                }
                , new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        AndroidResourceManager.logd(LOG_TAG, "onCreateDialog.run: beatType=" + mBeatType);
                        updateBeatTypeViews(Integer.parseInt(mManualEntryEdit.getText().toString()), mBeatType.getName());
                    }
                });

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
			.setView(rl)
			.setTitle(R.string.quickEditRhythmTitle)
			.setPositiveButton(R.string.ok_button, this)
			.setNegativeButton(R.string.cancel_button, this);
		
		mDialog = bld.create();
		mDialog.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
		return mDialog;
    }

    @Override
    protected void manualEditUpdated(int val) {
        updateBeatTypeViews(val, null);
    }

    @Override
    protected void okClickedWithChangedSeekValue(int newVal) {
        final RhythmEditCommand.ChangeNumberOfFullBeats cmd = new RhythmEditCommand.ChangeNumberOfFullBeats(newVal, mBeatType);
        cmd.setUiUpdatesBeatTree();
        cmd.execute();
        mActivity.getPlayRhythmFragment().getRhythmDraughter().arrangeRhythm(true, true);
    }

    /**
     * Detect whether the views should be enabled (and allow change by user)
     * @param val
     */
    private void updateBeatTypeViews(int val, String beatTypeName) {
        if (beatTypeName != null) {
            mBeatTypeName.setText(beatTypeName);
        }

        if (val <= mInitialSeekBarValue) { // ie. removing, so don't need add beat type
            if (mBeatTypeName.isEnabled()) {
                mBeatTypeName.setEnabled(false);
                mColourBtn.setEnabled(false);
                mBeatTypeName.setAlpha(.5f);
                mColourBtn.setAlpha(.5f);
                mColourBtn.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
            }
        }
        else {
            if (beatTypeName != null || !mBeatTypeName.isEnabled()) {
                mColourBtn.setBackgroundColor(((AndroidColour) mBeatType.getColour()).getColorInt());
                mBeatTypeName.setEnabled(true);
                mColourBtn.setEnabled(true);
                mBeatTypeName.setAlpha(1.f);
                mColourBtn.setAlpha(1.f);
            }
        }
    }

    //------------------------- OnClickListener

    /**
     * Specific tests in this class, otherwise call super
     * @param v
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.beat_type_name || v.getId() == R.id.colour_btn) {
            hideKeypad();
            // when a beat type is picked, save it straightaway to the player state, this will save having to save instance state for config changes (I think)
            ((EditRhythmActivity)mActivity).showBeatTypePopupLov(this);
        }
        else {
            super.onClick(v);
        }
    }

    /**
     * This dialog implements the callback for selecting a beat type from the popup lov (called in onClick())
     * @param beatType
     */
    @Override
    public void beatTypeSelected(BeatType beatType) {
        // the callback is saved to the rhythm holder, it's not safe to put references to this class in there
        // directly in case of config changes, so have to find it
        EditRhythmActivity activity = (EditRhythmActivity) mResourceManager.getLatestActivity();
        MagicWandDialog currentFrag = (MagicWandDialog) activity.getSupportFragmentManager().findFragmentByTag(MagicWandDialog.LOG_TAG);
        currentFrag.mBeatType = beatType;
        currentFrag.mPlayerState.setBeatType(beatType);
        mResourceManager.getGuiManager().runInGuiThread(currentFrag);
    }

    /**
     * Save an inner class by having this class implement Runnable. The views updated after beat type changes in beatTypeSelected()
     * are themselves updated in the ui thread.
     */
    @Override
    public void run() {
        updateBeatTypeViews(Integer.parseInt(mManualEntryEdit.getText().toString()), mBeatType.getName());
    }
}
