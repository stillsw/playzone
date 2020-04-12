package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import com.stillwindsoftware.keepyabeat.platform.AndroidBeatShapedImage;


public class PartBeatButton extends android.support.v7.widget.AppCompatButton {

    private AndroidBeatShapedImage mNormalPartOverlay, mSelectedPartOverlay;

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+PartBeatButton.class.getSimpleName();
    private int mWidth, mHeight;

    public PartBeatButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

    public PartBeatButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PartBeatButton(Context context) {
		super(context);
	}

    public void init(AndroidBeatShapedImage normalPartOverlay, AndroidBeatShapedImage selectedPartOverlay) {
        mNormalPartOverlay = normalPartOverlay;
        mSelectedPartOverlay = selectedPartOverlay;
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            mWidth = right - left;
            mHeight = bottom - top;
        }
    }

    @Override
	protected void onDraw(Canvas canvas) {

        super.onDraw(canvas); // super handles the colour

        if (isSelected() && mSelectedPartOverlay != null) {
            mSelectedPartOverlay.draw(canvas, 0, 0, mWidth, mHeight);
        }
        else if (mNormalPartOverlay != null) {
            mNormalPartOverlay.draw(canvas, 0, 0, mWidth, mHeight);
        }

    }
}
