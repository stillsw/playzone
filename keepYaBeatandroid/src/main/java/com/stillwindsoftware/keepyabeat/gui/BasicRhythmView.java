package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.stillwindsoftware.keepyabeat.player.backend.ImageLoadBinder;

/**
 * The image on which rhythms are drawn in a rhythms dialog_simple_list, and anywhere where
 * only the most basic image of the rhythm is needed, without playing or active
 * sub-zone.
 */
public class BasicRhythmView extends ImageView {

	@SuppressWarnings("unused")
    private static final String LOG_TAG = "KYB-"+BasicRhythmView.class.getSimpleName();
	
	// store a ref to the shared draughter for all rhythms in the dialog_simple_list
	private String mExternalKey;
	// the string of encoded beats at the last bind time, if it changed 
	// it means the cached image is dirty and needs to be re-drawn
	private String mEncodedBeats;

	private ImageLoadBinder mImageLoadBinder;
    private Bitmap mBitmap;
    private RhythmsListFragment mRhythmsList; // caller

    // constructors required by the framework
	
	public BasicRhythmView(Context context) {
		super(context);
	}

	public BasicRhythmView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public BasicRhythmView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * Called in bindView() for the cursor adapter
	 */
	void setImageLoadBinder(RhythmsListFragment rhythmsList, ImageLoadBinder imageLoadBinder, String externalKey, String encodedBeats) {
        mRhythmsList = rhythmsList;
		mImageLoadBinder = imageLoadBinder;
		mEncodedBeats = encodedBeats;
		mExternalKey = externalKey;

        if (!encodedBeats.equals(mEncodedBeats)) {
            mBitmap = null; // make sure if had an image and it's not for this rhythm that it's cleared out
        }


        // could be a cached image available already, and if not this will
        // trigger its creation so it will be ready for onDraw()
        if (mImageLoadBinder != null) {
            mBitmap = mImageLoadBinder.getBitmapImage(mExternalKey, mEncodedBeats);
        }

        invalidate(); // reused views might need to be drawn again
	}

	/**
	 * See if binder has an image ready for drawing
	 */
	@Override
	protected void onDraw(Canvas canvas) {

        // check for a dead service, and grab whatever image may be there if so
        if (mImageLoadBinder != null && !mImageLoadBinder.isServiceAlive()) {
            Bitmap bitmap = mImageLoadBinder.getBitmapImage(mExternalKey, mEncodedBeats);
            if (bitmap != null) {
                mBitmap = bitmap; // overwrite what we already have
            }

            mImageLoadBinder = null; // it's dead
//            AndroidResourceManager.logw(LOG_TAG, "onDraw: service died "+mExternalKey);
        }

        if (mImageLoadBinder == null) {                             // ask for a binder if it's died (may not be ready)
            mImageLoadBinder = mRhythmsList.getImageLoaderBinder();
//            AndroidResourceManager.logw(LOG_TAG, "onDraw: still waiting for binder "+mExternalKey);
        }

        if (mImageLoadBinder != null) {                             // got an alive binder, request image
            Bitmap bitmap = mImageLoadBinder.getBitmapImage(mExternalKey, mEncodedBeats);
            if (bitmap != null) {                                   // only if get an update
                mBitmap = bitmap;                                   // overwrite what we already have
            }
        }

        if (mBitmap != null) {
            canvas.drawBitmap(mBitmap, 0f, 0f, null);
        }
        else {
            invalidate();                                           // either no binder, or no image, make sure this method is called again soon
        }
	}
	

}
