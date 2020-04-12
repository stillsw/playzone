package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;

public class RepeatsButton extends android.support.v7.widget.AppCompatButton {

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+RepeatsButton.class.getSimpleName();

	private static final CharSequence EMPTY_STRING = "";

	private RepeatOption mRepeatOption;
	private int mNTimes;
	private PlayRhythmFragment mPlayRhythmFragment;

	private Drawable mImage;
	private Rect mDrawRect;
	private float mPadding;
	
	public RepeatsButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public RepeatsButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public RepeatsButton(Context context) {
		super(context);
		init();
	}

	private void init() {
		mDrawRect = new Rect();
		mRepeatOption = RepeatOption.Infinite; // default
		mNTimes = -1;
	}

	/**
	 * Called during init, but also if the repeat option changes
	 * @param repeatOption
	 * @param nTimes
	 */
	public void setRepeatOption(RepeatOption repeatOption, int nTimes) {
		this.mRepeatOption = repeatOption;
		this.mNTimes = nTimes;
		
		Resources res = getContext().getResources();
		mImage = res.getDrawable(R.drawable.ic_repeat_infinite);
		switch (mRepeatOption) {
		case NoRepeat :
			mImage = res.getDrawable(R.drawable.ic_repeat_none);
			break;
		case RepeatNtimes :
			mImage = res.getDrawable(R.drawable.ic_repeat_ntimes);
			break;
		default:
			break;
		}
		
		// ensure a draw
		postInvalidate();
	}

	/**
	 * Called as the view is initialised
     * @param playRhythmFragment
     * @param repeatOption
     * @param nTimes
     */
	public void setPlayRhythmFragment(PlayRhythmFragment playRhythmFragment,
                                      RepeatOption repeatOption, int nTimes) {
		this.mPlayRhythmFragment = playRhythmFragment;
		setRepeatOption(repeatOption, nTimes);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

        int w = right - left;

        Resources res = getResources();
        mPadding = res.getDimension(R.dimen.play_controls_button_margin);
        mDrawRect.top = (int) mPadding;

        float minW = res.getDimension(R.dimen.repeats_button_width);
        if (w > minW) {
            mPadding += (w - minW) / 2.f;
        }
		mDrawRect.left = (int) mPadding;
		mDrawRect.right = (int) (mDrawRect.left + w - mPadding * 2);
		int h = bottom - top;
		mDrawRect.bottom = (int) (h - mDrawRect.top);
    }

	@Override
	protected void onDraw(Canvas canvas) {
        try {
            // get the value of the current iteration and play state
            if (mRepeatOption == RepeatOption.RepeatNtimes) {
                BeatTrackerBinder btb = mPlayRhythmFragment.getBeatTrackerBinder();
                if (btb != null && !btb.isStopped()) { // ie. playing or paused

                    setText(String.format("%s/%s", btb.getRhythmIterations()+1, mNTimes));
                }
                else {
                    setText(Integer.toString(mNTimes));
                }
            }
            else {
                setText(EMPTY_STRING);
            }

            super.onDraw(canvas);

            if (mImage != null && mDrawRect != null) {
                mImage.setBounds(mDrawRect);
                mImage.draw(canvas);
            }
        } catch (Exception e) { // have seen this a few mins after UI left idle and trimmed, not sure why but hoping catching is enough
            AndroidResourceManager.loge(LOG_TAG, "onDraw: unexpected error, possibly UI is shutdown?", e);
        }
    }
}
