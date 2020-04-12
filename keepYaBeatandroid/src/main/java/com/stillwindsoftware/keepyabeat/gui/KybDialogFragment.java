package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.TagsContentProvider;
import com.stillwindsoftware.keepyabeat.model.transactions.TagsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * Superclass for any dialog in the application, although the setting of hasOpenDialog is principally for
 * rhythm editing, for other activities this class is optional
 */
public abstract class KybDialogFragment extends DialogFragment
    implements DialogInterface.OnClickListener, SeekBar.OnSeekBarChangeListener, TextWatcher, View.OnClickListener,
        View.OnFocusChangeListener{

    static final String LOG_TAG = "KYB-"+KybDialogFragment.class.getSimpleName();

    protected KybActivity mActivity;
    protected AndroidResourceManager mResourceManager;

    // common views used in some sub-classes
    protected EditText mManualEntryEdit;
    protected SeekBar mSeekBar;
    protected int mInitialSeekBarValue;
    protected int mMinSeekBarValue;
    protected int mMaxSeekBarValue;
    private int mManualEntryResId;


    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mActivity = (KybActivity) activity;
        mResourceManager = ((KybApplication)mActivity.getApplication()).getResourceManager();
        mActivity.setHasOpenDialog(true, this.getClass().getSimpleName());
    }

    /**
     * onDetach() seems unreliable as wasn't always called
     */
    @Override
    public void onDestroyView() {
        mActivity.setHasOpenDialog(false, this.getClass().getSimpleName());
        super.onDestroyView();
    }

    protected void initSeekBarAndManualEdit(View seekBar, View manualEdit, int manualEditResId, int initialVal, int minVal, int maxVal) {
        mSeekBar = (SeekBar) seekBar;
        mInitialSeekBarValue = initialVal;
        mMinSeekBarValue = minVal;
        mMaxSeekBarValue = maxVal;

        if (manualEdit != null) {
            mManualEntryEdit = (EditText) manualEdit;
            mManualEntryResId = manualEditResId;
        }

        if (initialVal >= 0 && initialVal <= maxVal) {
            if (manualEdit != null) {
                mManualEntryEdit.setText(Integer.toString(initialVal));
                mManualEntryEdit.addTextChangedListener(this);
                mManualEntryEdit.setOnClickListener(this);
                mManualEntryEdit.setOnFocusChangeListener(this);
            }
            mSeekBar.setMax(maxVal - minVal);
            mSeekBar.setProgress(initialVal - minVal);
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, String.format("initSeekBarAndManualEdit: didn't set seekbar progress/max (initial=%s, min=%s, max=%s)", initialVal, minVal, maxVal));
        }

        // reduce number of classes by implementing all the listeners
        mSeekBar.setOnSeekBarChangeListener(this);
    }

    //------------------------- View.OnFocusChangeListener

    /**
     * Use on manual edit entry view
     * @param v
     * @param hasFocus
     */
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (hasFocus) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        } else {
            // lose focus doesn't do anything, but clicking on the edit text again hides it
            imm.hideSoftInputFromWindow(mManualEntryEdit.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        }
    }

    //----------------------- OnSeekBarChangeListener

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            this.mSeekBar.requestFocus();
            hideKeypad();
            if (mManualEntryEdit != null) {
                mManualEntryEdit.setText(Integer.toString(progress + mMinSeekBarValue));
            }
        }
    }

    //------------------------ TextWatcher

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }
    @Override
    public void afterTextChanged(Editable s) {

        if (s.length() != 0) {
            int val = Integer.parseInt(s.toString());
            if (val < mMinSeekBarValue) {
                val = mMinSeekBarValue;
            }
            else if (val > mMaxSeekBarValue) {
                val = mMaxSeekBarValue;
            }

            mSeekBar.setProgress(val - mMinSeekBarValue);
            manualEditUpdated(val);
        }
    }

    /**
     * Sub-classes can implement to add more functionality to updates of the manual edit (on top of the seekbar updates)
     * @param val
     */
    protected void manualEditUpdated(int val) {
    }

    /**
     * Called when user clicks outside or hits back button, but not when clicks on cancel button
     */
    public void onCancel(DialogInterface dialog) {
        mActivity.setHasOpenDialog(false, this.getClass().getSimpleName());
        hideKeypad();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mActivity.setHasOpenDialog(false, this.getClass().getSimpleName());
        super.onDismiss(dialog);
    }

    /**
     * Variety of places that the dialog can close, or the input field loses focus, make sure the keypad closes too
     */
    protected void hideKeypad(View view) {
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    protected void hideKeypad() {
        if (mManualEntryEdit != null) {
            hideKeypad(mManualEntryEdit);
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "hideKeypad: needs a view, perhaps called from onCancel(), if so may need to override this method");
        }
    }

    protected void forceShowKeypad(View view) {
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED);
    }

    /**
     * Sub-classes should add any extra onClick() tests, and then call this method if not handled
     * @param view
     */
    @Override
    public void onClick(View view) {
        if (view.getId() == mManualEntryResId) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /**
     * Update the value on the player state
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        hideKeypad();

        if (which == Dialog.BUTTON_POSITIVE) {
            int newVal = mMinSeekBarValue + mSeekBar.getProgress();
            boolean changed = newVal != mInitialSeekBarValue;

            if (changed) {
                okClickedWithChangedSeekValue(newVal);
            }
            else {
                AndroidResourceManager.logv(LOG_TAG, "onClick(dialog): OK pressed but no changes, nothing to do");
            }
        }
        else {
            // cancel
            // just closes anyway, nothing to undo
        }
    }

    /**
     * OnClick on the dialog interface (ie. the OK button) has been clicked and there's a seekbar which
     * has a changed value. Sub-classes can override this method, or bypass that onClick altogether
     * @param newVal
     */
    protected void okClickedWithChangedSeekValue(int newVal) {

    }

    protected static void trackTextLength(EditText editText, final TextView textLenIndicator, final int maxLength) {
        if (editText == null || textLenIndicator == null) {
            AndroidResourceManager.logw(LOG_TAG, "trackTextLength: missing layout views");
            return;
        }

        textLenIndicator.setText(String.format("%s/%s", 0, maxLength)); // init with 0 so even without setting a value it's correct

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                textLenIndicator.setText(String.format("%s/%s", s.length(), maxLength));
            }
        });
    }

    /**
     * Commonly need to cause a redraw after doing a background task
     */
    protected class CauseEditorRedrawAndDismissCallback extends ParameterizedCallback implements Runnable {

        private boolean mDismissManually = true;

        public CauseEditorRedrawAndDismissCallback() {
            super(false);
        }

        /**
         * For when the dialog is dismissing itself, but want to have the redraw happen in the UI thread
         * @param dismissManually
         */
        public CauseEditorRedrawAndDismissCallback(boolean dismissManually) {
            super(false);
            mDismissManually = dismissManually;
        }

        @Override
        public void run() {
            if (mDismissManually) {
                dismiss();
            }
            if (mActivity instanceof EditRhythmActivity) {
                mActivity.getPlayRhythmFragment().causeRhythmRedraw();
            }
        }
    }
}
