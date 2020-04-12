package com.stillwindsoftware.keepyabeat.platform;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

/**
 * Android images created on the fly... and which can be modified at
 * run time according to width (for corner radius and stroke width).
 * These don't have a drawable assigned in constructor, it's dynamically
 * created during the initWidth() processing.
 */
public class AndroidBeatShapedImage extends AndroidImage {

	private static final String LOG_TAG = "KYB-"+AndroidBeatShapedImage.class.getSimpleName();

	// fields initialised at create time (see builder methods)
	protected boolean mIsColourSet = false;
	protected boolean mIsStrokeSet = false;
	protected int mColour;
	protected int mStrokeColour;
	protected float mCornerRadiusFraction;
	protected float mStrokeWidthFraction;
	protected int mStrokeWidth;
	protected float mCornerRadius;

	// initialised when initSize(int, int) is called
	protected GradientDrawable mDrawable;
	
	// need access to resource manager for caching colour modes
	protected AndroidResourceManager mResourceManager;

	public AndroidBeatShapedImage(AndroidResourceManager resourceManager) {
		mResourceManager = resourceManager;
	}

	/**
	 * Builder method to set solid colour
	 * @param colour
	 * @return
	 */
	public AndroidBeatShapedImage setColour(int colour) {
		mColour = colour;
		mIsColourSet = true;
		return this;
	}
	
	/**
	 * Builder method to set stroke colour
	 * @param colour
	 * @return
	 */
	public AndroidBeatShapedImage setStroke(int colour, float fractionWidth) {
		mStrokeColour = colour;
		mStrokeWidthFraction = fractionWidth;
		mIsStrokeSet = true;
		return this;
	}
	
	/**
	 * Builder method to set corner radius fraction
	 * @param 
	 * @return
	 */
	public AndroidBeatShapedImage setCornerRadiusFraction(float fractionWidth) {
		mCornerRadiusFraction = fractionWidth;
		return this;
	}
	
	/**
	 * This is where the path drawn is created, only the top level in a composite
	 * set calls this method (ie. the full beat base), the other images will
	 * share that rectangle by calls to initSio(PlayformImage)
	 */
	@Override
	public void initSize(int w, int h) {
		
		mW = w;
		mH = h;
		
		makeDrawable();
	}

	/**
	 * Called during rhythm initialization by the initSize() methods
	 * (see DraughtTargetGoverner.preloadCaches())
	 * Applies rules for colours, corners and strokes. Note, the rules
	 * don't change as the objects resize during drawing, so objects
	 * are not created during rendering.
	 */
	protected void makeDrawable() {		

		mDrawable = new GradientDrawable();
		
		mCornerRadius = mW * Math.max(mCornerRadiusFraction, .1f);
		mDrawable.setCornerRadius(mCornerRadius );
		
		if (mIsColourSet) {
			mDrawable.setColor(mColour);
		}
		
		if (mIsStrokeSet) {
			// width for stroke must be a min of 1
			// round to nearest int
			mStrokeWidth = Math.max(1, (int) ((mStrokeWidthFraction * mW) + .5f));
			mDrawable.setStroke(mStrokeWidth, mStrokeColour);
		}
	}

	/**
	 * Only the base image (full beat base) would call initSize(int, int), others that
	 * have to have the same corner radius will call this method and reference
	 * the rectangle from the source.
	 */
	@Override
	public void initSize(PlatformImage rulesSourceImg) {
		
		if (rulesSourceImg instanceof AndroidBeatShapedImage) {
			AndroidBeatShapedImage img = (AndroidBeatShapedImage) rulesSourceImg;
			
			mW = img.mW;
			mH = img.mH;
			mCornerRadiusFraction = img.mCornerRadiusFraction;
			makeDrawable();
		}
		else {
			AndroidResourceManager.logd(LOG_TAG, "initSize: called with wrong type of image="+rulesSourceImg.getClass());
		}
	}

	@Override
	public float getStrokeWidth() {
		return mStrokeWidth;
	}

	@Override
	public void setStrokeWidth(float w) {
		mStrokeWidth = (int) w;
		mIsStrokeSet = true;
		
		// may have to reset the stroke on the drawable (colour SHOULD be set before this)
		if (mDrawable != null) {
			mDrawable.setStroke(mStrokeWidth, mStrokeColour);
		}
	}

	@Override
	public void pushTintColour(Colour colour) {
		if (mDrawable != null) {
			mDrawable.setColorFilter(mResourceManager.getCachedColorFilter(((AndroidColour)colour).getColorInt()));
		}
//WHITE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.DST);
//WHITE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.DST_ATOP);
//WHITE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.DST_IN);
//VANISHES		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.DST_OUT);
//WHITE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.DST_OVER);
//WHITE-REDSTROKE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.LIGHTEN);
//poss		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);
//WHITE-REDSTROKE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.SCREEN);
//ALLRED-INCLSTROKE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.SRC);
//DITTO		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
//VANISHES		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.SRC_OUT);
//ALLRED-INCLSTROKE		mDrawable.setColorFilter(Color.RED, PorterDuff.Mode.SRC_OVER);
	}

	@Override
	public void popTintColour() {
		if (mDrawable != null) {
			mDrawable.setColorFilter(null);
		}
	}

	@Override
	public void draw(Canvas canvas, float left, float top, float right, float bottom) {
		
		if (mDrawable != null) {
			mDrawable.setBounds((int)left, (int)top, (int)right, (int)bottom);
			mDrawable.draw(canvas);
		}
	}

	public GradientDrawable getDrawable() {
	    return mDrawable;
    }
}
