package com.stillwindsoftware.keepyabeat.platform;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

/**
 * Superclass for android versions of platform image
 */
public abstract class AndroidImage implements PlatformImage {
	
	protected int mW = -1;
	protected int mH = -1;

    public AndroidImage() {}

	public AndroidImage(int w, int h) {
		this.mW = w;
		this.mH = h;
	}

	@Override
	public int getWidth() {
		return mW;
	}

	@Override
	public int getHeight() {
		return mH;
	}

	@Override
	public void draw(int x, int y, int w, int h) {
		// twl only
	}

	/**
	 * Android version is a bit different because of the need to pass the canvas to 
	 * the draw method
	 * @param canvas
	 * @param left
	 * @param top
	 * @param right
	 * @param bottom
	 */
	public abstract void draw(Canvas canvas, float left, float top, float right, float bottom);
	
	@Override
	public void initSize(int w, int h) {
	}

	@Override
	public void initSize(PlatformImage rulesSourceImg) {
	}

	/**
	 * stroke width only used for dynamic drawable images (AndroidBeatShapedImage) 
	 */
	@Override
	public float getStrokeWidth() {
		return 0;
	}

	/**
	 * stroke width only used for dynamic drawable images (AndroidBeatShapedImage) 
	 */
	@Override
	public void setStrokeWidth(float w) {
	}
	
	@Override
	public void release() {
		// n/a
	}

	@Override
	public boolean isReleased() {
		return false;
	}

}
