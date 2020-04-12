package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.platform.AndroidBeatShapedImage;
import com.stillwindsoftware.keepyabeat.platform.AndroidFont;
import com.stillwindsoftware.keepyabeat.platform.AndroidImage;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;


public class CopyBeatButton extends android.support.v7.widget.AppCompatButton implements DrawingSurface {

    private AndroidBeatShapedImage mNormalBeatOverlay;

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+CopyBeatButton.class.getSimpleName();
    private int mOriginalWidth, mWidth, mHeight;
    private BaseRhythmDraughter mDraughter;
    private int mPosition, mSelectedBeatWidth;
    private Canvas mOnDrawCanvas;
    private int mSelectedBeatPosition;
    private String mDisplayFullBeatNum;
    private boolean mHasWidthChanged = false;

    public CopyBeatButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

    public CopyBeatButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CopyBeatButton(Context context) {
		super(context);
	}

    public void init(int position, int width, int selectedBeatPosition, int selectedBeatWidth, String displayFullBeatNum, AndroidBeatShapedImage normalOverlay, BaseRhythmDraughter baseRhythmDraughter) {
        mPosition = position;
        mOriginalWidth = width;
        mSelectedBeatPosition = selectedBeatPosition;
        mSelectedBeatWidth = selectedBeatWidth;
        mDisplayFullBeatNum = displayFullBeatNum;
        mNormalBeatOverlay = normalOverlay;
        mDraughter = baseRhythmDraughter;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            mWidth = right - left;
            mHeight = bottom - top;
        }
        if (mSelectedBeatPosition != mPosition) {   // check the width is as expected
            if ((isSelected() && mWidth != mSelectedBeatWidth)
                || (!isSelected() && mWidth != mOriginalWidth)) {
                AndroidResourceManager.logd(LOG_TAG,
                        String.format("onLayout: computed width for beat not as expected given=%s, expected=%s, selected=%s",
                                mWidth, isSelected() ? mSelectedBeatWidth : mOriginalWidth, isSelected()));
            }
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (mSelectedBeatPosition != mPosition) {   // don't change if it's the original selected beat
            int newWidth = selected ? mSelectedBeatWidth : mOriginalWidth;
            setWidth(newWidth);
            mHasWidthChanged = mWidth != newWidth;
        }
    }

    public boolean hasWidthChanged() {
        return mHasWidthChanged;
    }

    @Override
	protected void onDraw(Canvas canvas) {

        mOnDrawCanvas = canvas;
        try { // draw the beat's image unless it's selected, then it gets the copy beat's images
            mDraughter.drawFullBeat(this, isSelected() ? mSelectedBeatPosition : mPosition, mWidth, mHeight, mDisplayFullBeatNum);

        } catch (NullPointerException e) {
            // have seen where drawRhythm is in the middle and gets a NPE from apparently good obj
            // I think it's to do with a context disappearing (eg. config change)
            // anyway, catch it here and see if it sorts itself out
            AndroidResourceManager.loge(LOG_TAG, "onDraw: probably config change or return from another app");
        }
        finally {
            mOnDrawCanvas = null;
        }

        if (!isSelected() && mNormalBeatOverlay != null) {
            mNormalBeatOverlay.draw(canvas, 0, 0, mWidth, mHeight);
        }
    }

    @Override
    public void drawImage(PlatformImage image, float x, float y, float w, float h) {

        if (mOnDrawCanvas != null) {
            ((AndroidImage) image).draw(mOnDrawCanvas, (int) x, (int) y, (int) (x + w), (int) (y + h));
        }
    }

    @Override
    public void drawImage(PlatformImage image, float x, float y, float w,
                          float h, float clipx, float clipy, float clipw, float cliph) {

        if (mOnDrawCanvas != null) {
            mOnDrawCanvas.save();

            mOnDrawCanvas.clipRect(clipx, clipy, clipx + clipw, clipy + cliph);
            drawImage(image, x, y, w, h);

            mOnDrawCanvas.restore();
        }
    }

    @Override
    public void drawText(PlatformFont font, int number, float x, float y) {
        drawText(font, Integer.toString(number), x, y);
    }

    @Override
    public void drawText(PlatformFont font, String text, float x, float y) {
        if (mOnDrawCanvas != null) {
            ((AndroidFont) font).drawText(mOnDrawCanvas, (int) x, (int) y, text);
        }
    }

    @Override
    public void registerForEvents(PlatformEventListener eventListener) {}

    @Override
    public boolean triggeredBackEvent() {
        return false;
    }

    @Override
    public void pushTransform(float rotation, float transX, float transY) {}

    @Override
    public void popTransform() {}

    @Override
    public MutableRectangle getDrawingRectangle() { return null; }

}
