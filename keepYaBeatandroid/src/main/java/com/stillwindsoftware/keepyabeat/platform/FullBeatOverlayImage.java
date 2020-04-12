package com.stillwindsoftware.keepyabeat.platform;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Build;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;

/**
 * A special case where a 2nd image is needed to be drawn over the first
 */
public class FullBeatOverlayImage extends AndroidBeatShapedImage {

	private static final String LOG_TAG = "KYB-"+FullBeatOverlayImage.class.getSimpleName();

	// draw angles for shine
	private final int mRightShineStartDegrees = 290;
	private final int mRightShineAngleInDegrees = 50;
	private final int mLeftShineStartDegrees = 185;
	private final int mLeftShineAngleInDegrees = 75;
    private final float mDragDotStroke, mHalfDotStroke;

    // paint for shine
	private Paint mPaint;
	
	// rectangle used in drawing, create once
	private RectF mRect;
	private float mTopShineMargin, mBottomShineMargin;
	private float mRightShineStrokeW;

    public FullBeatOverlayImage(AndroidResourceManager resourceManager) {
		super(resourceManager);
		mPaint = new Paint();
		mPaint.setColor(Color.WHITE);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setAntiAlias(true);

        mDragDotStroke = resourceManager.getApplication().getResources().getDimension(R.dimen.drag_dot_stroke);
        mHalfDotStroke = mDragDotStroke / 2f;
    }

	/**
	 * Set fields for drawing shine
	 */
	@Override
	public void makeDrawable() {
		super.makeDrawable();

		// margin inside stroke, min 1
		mTopShineMargin = Math.max(mStrokeWidth * .8f, 1f);
		mBottomShineMargin = Math.max(mStrokeWidth * .7f, 1f);
		// set stroke width, min 1
		mRightShineStrokeW = Math.max(mStrokeWidth * .3f, 1f);
		// radius for inner arc shine
		float r = mCornerRadius - mTopShineMargin;
		// rect for drawing (will take part of it for arc)
		mRect = new RectF(0, 0, r, r);
	}

	@Override
	public void draw(Canvas canvas, float left, float top, float right, float bottom) {
		super.draw(canvas, left, top, right, bottom);
		
		// draw a tiny highlight at the top right corner
		mPaint.setAlpha(200);

		// the vectors from the centre point (of the radius) to both
		// ends of the arcs was stored in makeDrawable()
		
		mPaint.setStrokeWidth(mRightShineStrokeW);
        mPaint.setStrokeCap(Cap.ROUND);

		mRect.left = right - mCornerRadius*2 + mTopShineMargin;
		mRect.right = right - mTopShineMargin;
		mRect.top = top + mTopShineMargin;
		mRect.bottom = top + mCornerRadius*2 - mTopShineMargin;
		
		canvas.drawArc(mRect, mRightShineStartDegrees, mRightShineAngleInDegrees, false, mPaint);
		
		// top left corner
		mPaint.setAlpha(60);
		
		mRect.left = left + mTopShineMargin;
		mRect.right = left + mCornerRadius*2 - mTopShineMargin;
		mRect.top = top + mTopShineMargin;
		mRect.bottom = top + mCornerRadius*2 - mTopShineMargin;
		
		canvas.drawArc(mRect, mLeftShineStartDegrees, mLeftShineAngleInDegrees, false, mPaint);
		
		// bottom only when wider than corner radius * 2
        float w = right - left;
        if (w > mCornerRadius * 2.4f) {
			mPaint.setAlpha(40);
			canvas.drawLine(left + mCornerRadius*1.2f, bottom - mBottomShineMargin
				, right - mCornerRadius*1.2f, bottom - mBottomShineMargin, mPaint);
		}

        // drag handles only on editor
        if (AndroidResourceManager.sIsEditorCurrent) {

            // ideal width is horizontal gaps same width as stroke...

            int numDotsH = 4;
            int numGapsH = numDotsH -1;
            float strokeGapV = mDragDotStroke * 2;
            float strokeGapH = strokeGapV;
            float dragIconW = mDragDotStroke +                      // whole thing is half stroke each side
                    strokeGapH * numGapsH;                          // plus number of gaps


            if (dragIconW < w / 2f) {                               // if it leaves too much gap at the sides, stretch it out a bit
                strokeGapH += mDragDotStroke;                       // double the gap
                dragIconW = mDragDotStroke +                        // redo
                        strokeGapH * numGapsH;
            }

            // or if it's too small reduce the number of dots
            else {
                while (dragIconW > w - strokeGapH * 3f              // make the min edge a strokeGap (plus 1/2 each side for the actual drawing)
                        && numDotsH > 1) {                          // min of 1 dot
                    numDotsH--;
                    numGapsH--;

                    dragIconW = mDragDotStroke +                    // redo
                            strokeGapH * numGapsH;
                }
            }

            if (dragIconW >= w) {
                return;                                             // can't draw it at all, it's too wide
            }


            float marginH = (w - dragIconW) / 2f;
            float midY = (top + (bottom - top) / 2f);
            float startY = midY - strokeGapV;
            float l = left + marginH + mHalfDotStroke;

            mPaint.setAlpha(200);
            mPaint.setStrokeWidth(mDragDotStroke);
            mPaint.setStrokeCap(Cap.SQUARE);

            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < numDotsH; x++) {
                    canvas.drawPoint(l + strokeGapH * x, startY + strokeGapV * y, mPaint);
                }
            }
        }
	}

}

