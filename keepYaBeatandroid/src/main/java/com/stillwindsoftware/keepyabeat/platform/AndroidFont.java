package com.stillwindsoftware.keepyabeat.platform;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Android font defaults to Robotium (currently), not yet providing
 * any other fonts.
 */
public class AndroidFont implements PlatformFont {

	private final Paint mPaint;
	private int mNumbersHeight;
	
	
	/**
	 * Apply scaling to the text size
	 * @param metrics
	 * @param textSize
	 */
	public AndroidFont(DisplayMetrics metrics, int textSize) {
		mPaint = new Paint();
		mPaint.setTextSize(
				TypedValue.applyDimension(
						TypedValue.COMPLEX_UNIT_SP, textSize, metrics));
		
		mPaint.setColor(Color.BLACK);
		
		Rect bounds = new Rect();
		mPaint.getTextBounds("1234567890", 0, 10, bounds );
		mNumbersHeight = bounds.bottom - bounds.top;
	}
	
	@Override
	public boolean yIsBaseline() {
		return true;
	}

	@Override
	public float computeTextWidth(String text) {
		return mPaint.measureText(text);
	}

	@Override
	public float getNumbersHeight() {
		return mNumbersHeight;
	}

	@Override
	public void drawText(PlatformAnimationState animationState, int x, int y, String text) {
	}

	/**
	 * Android version differs in that the canvas is passed to draw onto
	 * @param canvas
	 * @param x
	 * @param y
	 * @param text
	 */
	public void drawText(Canvas canvas, int x, int y, String text) {
		canvas.drawText(text, x, y, mPaint);
	}

}
