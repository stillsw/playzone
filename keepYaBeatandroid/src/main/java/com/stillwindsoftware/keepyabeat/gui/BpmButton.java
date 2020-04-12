package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.FillType;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.Button;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.Rhythms;

public class BpmButton extends Button {

	private static final float ARC_LEN = 120.f;
	private static final float ARC_START_DEGREES = 210.f;
	private static final float BPM_RANGE = Rhythms.MAX_BPM - Rhythms.MIN_BPM;

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+BpmButton.class.getSimpleName();

	private int mBpm = Rhythms.DEFAULT_BPM;
	private Paint mPaint;
	private RectF mDrawRect;

	private float mPadding;
	private Path mTrianglePoints;

	private float mStrokeW;

	public BpmButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initPaint();
	}

	public BpmButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		initPaint();
	}

	public BpmButton(Context context) {
		super(context);
		initPaint();
	}

	public void setBpm(int bpm) {
		mBpm = bpm;
		setText(Integer.toString(bpm));
		postInvalidate(); // in case this is happening on another thread
	}

    private void initPaint() {
        Resources res = getResources();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPaint.setColor(res.getColor(R.color.play_controls_stroke, null));
        }
        else {
            //noinspection deprecation
            mPaint.setColor(res.getColor(R.color.play_controls_stroke));
        }
        mStrokeW = res.getDimension(R.dimen.play_controls_button_stroke_width);
        mPaint.setStrokeWidth(mStrokeW);
        mDrawRect = new RectF();
        mTrianglePoints = new Path();
        mTrianglePoints.setFillType(FillType.WINDING);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int w = right - left;

        // test if there's more than the minimum width available
        Resources res = getResources();
        mPadding = res.getDimension(R.dimen.bpm_button_img_padding)
                + res.getDimension(R.dimen.play_controls_button_stroke_width); // because stroke is on the line

        mDrawRect.top = mPadding;

        float minW = res.getDimension(R.dimen.bpm_button_width);
        if (w > minW) {
            mPadding += (w - minW) / 2.f;
        }

        mDrawRect.left = mPadding;
        mDrawRect.right = mDrawRect.left + w - mPadding * 2;
        //noinspection SuspiciousNameCombination
        float circleW = mDrawRect.right - mDrawRect.left;
        mDrawRect.bottom = mDrawRect.top + circleW; // so it's square

        float centreX = (mDrawRect.left + circleW / 2) - mDrawRect.centerX();
        float topY = (mDrawRect.top + mStrokeW) - mDrawRect.centerY();
        float altitude = mStrokeW * 2;
        mTrianglePoints.rewind();
        mTrianglePoints.moveTo(centreX, topY);
        mTrianglePoints.lineTo(centreX + altitude, topY + altitude);
        mTrianglePoints.lineTo(centreX - altitude, topY + altitude);
        mTrianglePoints.close();
    }

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		mPaint.setStyle(Style.STROKE);
		canvas.drawArc(mDrawRect, ARC_START_DEGREES, ARC_LEN, false, mPaint);
		mPaint.setStyle(Style.FILL);
		canvas.save();
		canvas.translate(mDrawRect.centerX(), mDrawRect.centerY());
		int diffMin = mBpm - Rhythms.MIN_BPM;
		float bpmDelta = diffMin / BPM_RANGE;
		float rotate = (360 - ARC_LEN / 2f) + ARC_LEN * bpmDelta;
		canvas.rotate(rotate);
		canvas.drawPath(mTrianglePoints, mPaint);
		canvas.restore();
	}
}
