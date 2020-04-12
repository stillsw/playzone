package com.stillwindsoftware.keepyabeat.platform;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

public class ProgressImage extends AndroidImage {

	private Paint mPaint;
	
	public ProgressImage(int w, int h) {
		super(w, h);
		mPaint = new Paint();
		mPaint.setColor(Color.WHITE);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeCap(Cap.ROUND);
		mPaint.setAntiAlias(true);
		mPaint.setStrokeWidth(w);
	}

	@Override
	public void pushTintColour(Colour colour) {
	}

	@Override
	public void popTintColour() {
	}

	@Override
	public void draw(Canvas canvas, float left, float top, float right, float bottom) {
		// TODO this is inaccurate
		float x = left + ((right - left) / 2);
		canvas.drawLine(x, top, x, bottom, mPaint);
	}

}
