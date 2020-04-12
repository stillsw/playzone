package com.stillwindsoftware.keepyabeat.platform;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

/**
 * A wrapper around an android drawable
 */
public class AndroidDrawableImage extends AndroidImage {

	private static final String LOG_TAG = "KYB-"+AndroidDrawableImage.class.getSimpleName();

    protected boolean mIsSquaredTopRight = false; // for zoom image to be always in proportion
	protected Drawable mDrawable;
	// need access to resource manager for caching colour modes
	protected AndroidResourceManager mResourceManager;
	
	public AndroidDrawableImage(AndroidResourceManager resourceManager, Drawable drawable) {
		mResourceManager = resourceManager;
		mDrawable = drawable;
	}

    public AndroidDrawableImage(AndroidResourceManager resourceManager, Drawable drawable, int w, int h) {
        super(w, h);
        mResourceManager = resourceManager;
        mDrawable = drawable;
    }

    /**
     * Allows the drawing of the image to correct for any skewing in the size of the area, will always draw the image square
     * (used for zoom icon)
     * @param resourceManager
     * @param drawable
     * @param w
     * @param h
     * @param isSquaredTopRight
     */
    public AndroidDrawableImage(AndroidResourceManager resourceManager, Drawable drawable, int w, int h, boolean isSquaredTopRight) {
        this(resourceManager, drawable, w, h);
        mIsSquaredTopRight = isSquaredTopRight;
    }

    @Override
	public void draw(Canvas canvas, float left, float top, float right, float bottom) {
		
		if (mDrawable != null) {

            if (mIsSquaredTopRight) {                   // only used for zoom icon
                float w = right - left;
                float h = bottom - top;
                if (Float.compare(w, h) != 0) {
                    float smallest = Math.min(w, h);
                    bottom = top + smallest;
                    left = right - smallest;
                }
            }

			mDrawable.setBounds((int)left, (int)top, (int)right, (int)bottom);
			mDrawable.draw(canvas);
		}
	}
	
	@Override
	public void release() {
		mDrawable = null;
	}

	@Override
	public boolean isReleased() {
		return mDrawable == null;
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

}
