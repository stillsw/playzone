package com.courseracapstone.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.ImageDownloadBinderDelegate.ImageFailedInDownloadException;

public class ThumbView extends ImageView {
	
	private final static String LOG_TAG = "Potlatch-"+ThumbView.class.getSimpleName();

	// constructors required by the framework

	private DownloadBinder mDownloadBinder;
	private Bitmap mBitmap;
	private boolean mHasPath;
	private long mGiftId;
	private RelativeLayout mParent;

	private TextView mMissingVw;

	private View mProgressVw;

	private boolean mIsDownloadFailure = false;

	public ThumbView(Context context) {
		super(context);
	}

	public ThumbView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public ThumbView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public Bitmap getImage() {
		return mBitmap;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		
		boolean drawn = false;
		
		// draw the image provided it hasn't already failed to download
		// and has a path
		if (mHasPath && !mIsDownloadFailure) {
			
			// try to get the image from the binder's cache
			if (mBitmap == null) {
				tryForImageFromCache();
			}
			
			// not there, if not an error, try again soon
			if (mBitmap == null) {
				if (!mIsDownloadFailure) {
					invalidate();
				}
			}
			else {
				float w = mBitmap.getWidth();
				float h = mBitmap.getHeight();

				// image dimensions the same (square)
				int iwh = getWidth();
				float half = iwh / 2;
				
				canvas.drawBitmap(mBitmap, half - w / 2, half - h / 2, null);
				drawn = true;
			}
		}
		
		if (!drawn) {
			super.onDraw(canvas);
		}
		
	}

	public void setGiftId(long giftId, String thumbnailPath, DownloadBinder downloadBinder) {
		mGiftId = giftId;
		mHasPath = !TextUtils.isEmpty(thumbnailPath);
		
		// clear all variables that need it since the view will be re-used
		mIsDownloadFailure = false;
		mBitmap = null;
		mDownloadBinder = downloadBinder;

		mParent = (RelativeLayout) getParent();

		mMissingVw = (TextView) mParent.findViewById(R.id.thumb_missing);
		mProgressVw = mParent.findViewById(R.id.thumb_progress);
		mProgressVw.setVisibility(View.VISIBLE);

		if (!mHasPath) {
			mProgressVw.setVisibility(View.INVISIBLE);
			mMissingVw.setVisibility(View.VISIBLE);
			// reset the text for now, if it fails the text will be different
			mMissingVw.setText(R.string.thumb_missing);
		}
		else {
			mMissingVw.setVisibility(View.INVISIBLE);
			//TODO remove this
			Log.d(LOG_TAG, "setGiftId: end of thumb path :"+thumbnailPath.substring(thumbnailPath.lastIndexOf('/')));
			tryForImageFromCache();
		}
		
	}

	private void tryForImageFromCache() {
		
		try {
			mBitmap = mDownloadBinder.getThumbImageForGift(mGiftId);

			// image read hide progress 
			if (mBitmap != null) {
				mProgressVw.setVisibility(View.INVISIBLE);
			}
			
		} catch (ImageFailedInDownloadException e) {
			Log.w(LOG_TAG, "tryForImageFromCache: failed, so won't try again for this view");
			mProgressVw.setVisibility(View.INVISIBLE);
			mIsDownloadFailure = true;
			mMissingVw.setText(R.string.image_download_error);
		}
	}

}
