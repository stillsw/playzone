package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.stillwindsoftware.keepyabeat.R;

/**
 * Thanks to https://github.com/afarber/android-newbie for a simplified version, and to
 * http://robobunny.com/wp/2013/08/24/android-seekbar-preference-v2/ for the fuller version.
 * This class uses elements of both (the first for the functionality, the 2nd to get the
 * required layout with title/summary too)
 */
public class SeekBarPreference extends Preference implements OnSeekBarChangeListener {

    private static final String LOG_TAG = "KYB-"+SeekBarPreference.class.getSimpleName();

    private SeekBar mSeekBar;
    private int mProgress;

    public SeekBarPreference(Context context) {
        this(context, null);
        initLayout(context, null);
    }
    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initLayout(context, attrs);
    }
    public SeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initLayout(context, attrs);
    }

    private void initLayout(Context context, AttributeSet attrs) {
        setWidgetLayoutResource(R.layout.preference_seekbar);
//        mSeekBar = new SeekBar(context, attrs);
//        mSeekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        // The basic preference layout puts the widget frame to the right of the title and summary,
        // so we need to change it a bit - the seekbar should be under them.
        LinearLayout layout = (LinearLayout) view;
        layout.setOrientation(LinearLayout.VERTICAL);

        return view;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mSeekBar = (SeekBar) view.findViewById(R.id.seekbar);
        mSeekBar.setProgress(mProgress);
        mSeekBar.setOnSeekBarChangeListener(this);

//        try {
//            // move our seekbar to the new view we've been given
//            ViewParent oldContainer = mSeekBar.getParent();
//            ViewGroup newContainer = (ViewGroup) view.findViewById(R.id.seekBarPrefBarContainer);
//
//            if (oldContainer != newContainer) {
//                // remove the seekbar from the old view
//                if (oldContainer != null) {
//                    ((ViewGroup) oldContainer).removeView(mSeekBar);
//                }
//                // remove the existing seekbar (there may not be one) and add ours
//                newContainer.removeAllViews();
//                newContainer.addView(mSeekBar, ViewGroup.LayoutParams.MATCH_PARENT,
//                        ViewGroup.LayoutParams.WRAP_CONTENT);
//            }
//        }
//        catch(Exception ex) {
//            AndroidResourceManager.loge(LOG_TAG, "onBindView: Error binding view: " + ex);
//        }
//
//        //if dependency is false from the beginning, disable the seek bar
//        if (view != null && !view.isEnabled()) {
//            mSeekBar.setEnabled(false);
//        }

//        updateView(view);

    }
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!fromUser)
            return;
        setValue(progress);
    }
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // not used
    }
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // not used
    }
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedInt(mProgress) : (Integer) defaultValue);
    }
    public void setValue(int value) {
        if (shouldPersist()) {
            persistInt(value);
        }
        if (value != mProgress) {
            mProgress = value;
            notifyChanged();
        }
    }
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }
}
