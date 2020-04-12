package com.stillwindsoftware.keepyabeat.gui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor.RhythmChangeBpmCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.MultiEditModel;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;

/**
 * A dialog that pops up when the user presses the BPM button and which updates the rhythm
 * with a new value 
 */
public class BpmDialog extends KybDialogFragment {

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+BpmDialog.class.getSimpleName();

	// for tapping out bpm changes
    private long lastTapNano = -1L;

	private Rhythm mRhythm;
	private int mUpdatedBpm;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		
		mActivity = (KybActivity) getActivity();
		mRhythm = mActivity.getRhythm();
		final ScrollView sv = (ScrollView) mActivity.getLayoutInflater().inflate(R.layout.bpm_dialog_layout, null);
		final RelativeLayout rl = (RelativeLayout) sv.getChildAt(0);
		final TextView minVw = (TextView) rl.findViewById(R.id.min_bpm);
		minVw.setText(Integer.toString(Rhythms.MIN_BPM));
		final TextView maxVw = (TextView) rl.findViewById(R.id.max_bpm);
		maxVw.setText(Integer.toString(Rhythms.MAX_BPM));

		if (mRhythm != null) {

            initSeekBarAndManualEdit(rl.findViewById(R.id.seek_bpm),
                    rl.findViewById(R.id.bpm_value), R.id.bpm_value,
                    mRhythm.getBpm(),
                    Rhythms.MIN_BPM,
                    Rhythms.MAX_BPM);

			ImageButton drumTapper = rl.findViewById(R.id.bpm_drum_tapper);
            drumTapper.setOnClickListener(this);
		}
		else {
            AndroidResourceManager.loge(LOG_TAG, "onCreateDialog: no rhythm returned to init dialog");
            dismiss();
        }
		
		AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
			.setView(sv)
			.setTitle(R.string.changeBpmTitle)
			.setPositiveButton(R.string.ok_button, this)
			.setNegativeButton(R.string.cancel_button, this);
		
		Dialog dlg = bld.create();
		dlg.setCanceledOnTouchOutside(false);
		return dlg;
	}

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bpm_drum_tapper) {
            updateDrumtapping();
        }
        else {
            super.onClick(v);
        }
    }
	protected void updateDrumtapping() {
        hideKeypad();
		long nanoTime = System.nanoTime();
		
		// first tap do nothing
		if (lastTapNano != -1L) {
			float diff = nanoTime - lastTapNano;
			float diffSeconds = diff / BeatTracker.NANOS_PER_SECOND;

			mUpdatedBpm = (int) (60.0f / diffSeconds);
			mUpdatedBpm = Math.min(Math.max(mUpdatedBpm, Rhythms.MIN_BPM), Rhythms.MAX_BPM);
			mSeekBar.setProgress(mUpdatedBpm - Rhythms.MIN_BPM);
			mManualEntryEdit.setText(Integer.toString(mUpdatedBpm));
			
			updateBeatTracking();
		}

		lastTapNano = nanoTime;
	}

    @Override
    protected void manualEditUpdated(int val) {
        // re-init in case tapping is interrupted by this
        lastTapNano = -1L;
        mUpdatedBpm = val;
        mUpdatedBpm = Math.min(Math.max(mUpdatedBpm, Rhythms.MIN_BPM), Rhythms.MAX_BPM);
        updateBeatTracking();
    }

	protected void updateBeatTracking() {
		BeatTrackerBinder beatTrackerBinder = mActivity.getBeatTrackerBinder();
		if (beatTrackerBinder != null) {
			beatTrackerBinder.updateTracking(mUpdatedBpm);
		}
	}

	/**
	 * Update the bpm for the rhythm
	 */
	@Override
	public void onClick(DialogInterface dialog, int which) {
        hideKeypad();
		if (which == Dialog.BUTTON_POSITIVE) {
			if (mUpdatedBpm != mRhythm.getBpm()) {
//				((KybApplication) mActivity.getApplication()).getResourceManager().startTransaction();
				new RhythmChangeBpmCommand(mRhythm,
					new MultiEditModel() {
						@Override
						public boolean isMultiEdit() {
							return false;
						}

						@Override
						public int getValue() {
							return mUpdatedBpm;
						}

					}, mRhythm.getBpm()).execute();
			}
		}
		else {
			// cancel
			mUpdatedBpm = mRhythm.getBpm();
			updateBeatTracking();
		}
	}

	/**
	 * Called when user clicks outside or hits back button, but not when clicks on cancel button
	 */
	public void onCancel(DialogInterface dialog) {
        hideKeypad();
		mUpdatedBpm = mRhythm.getBpm();
		updateBeatTracking();
	}
}
