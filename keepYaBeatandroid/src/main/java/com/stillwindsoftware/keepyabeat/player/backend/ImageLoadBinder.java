package com.stillwindsoftware.keepyabeat.player.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.os.Binder;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.platform.AndroidFont;
import com.stillwindsoftware.keepyabeat.platform.AndroidImage;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;

/**
 * Consumer/producer pattern running a thread that creates images and caches them
 * until the Service exits
 */
public class ImageLoadBinder extends Binder implements DrawingSurface {

	private static final String LOG_TAG = "KYB-"+ImageLoadBinder.class.getSimpleName();

	private RhythmImageService mService;
    private Rhythms mRhythms;
	
	private DraughtTargetGoverner mTargetGoverner;
	private BaseRhythmDraughter mRhythmDraughter;
	private MutableRectangle mCanvasDimension;
//	private int mBgdColour;

	private Thread mImageMakerThread;
	private ReentrantLock mLock;
	private Condition mNoData;
	
	// guarded by the lock
	private volatile HashMap<String, Bitmap> mRhythmImages;
	private volatile HashMap<String, String> mRhythmEncodedBeats;
	private volatile ArrayList<String> mRhythmRequests;
	private volatile Canvas mCanvas;

	ImageLoadBinder(RhythmImageService service) {
		mService = service;
		
		mRhythmImages = new HashMap<>();
		mRhythmEncodedBeats = new HashMap<>();
		mRhythmRequests = new ArrayList<>();
	}

    public boolean isServiceAlive() {
        return !mService.isDead();
    }

	/**
	 * In case of a resume() and not sure if the binder is ready
	 * @return true if there is one
	 */
	@SuppressWarnings("SpellCheckingInspection")
    public boolean hasTargetGoverner() {
		return mTargetGoverner != null;
	}
	
	/**
	 * Called when first set up and before any requests are made for images
	 * @param targetGoverner the shared target governor
	 */
	@SuppressWarnings("SpellCheckingInspection")
    public void init(AndroidResourceManager resourceManager, DraughtTargetGoverner targetGoverner) {
		mTargetGoverner = targetGoverner;
		mRhythms = resourceManager.getLibrary().getRhythms();
		
		// dimensions for the image
		Resources res = mService.getResources();
		mCanvasDimension = new MutableRectangle(0f, 0f
				, res.getDimension(R.dimen.rhythms_list_rhythm_img_width)
				, res.getDimension(R.dimen.rhythms_list_rhythm_img_height));
		
		mRhythmDraughter = new BaseRhythmDraughter(resourceManager);
    	mRhythmDraughter.initTarget(mTargetGoverner, ImageLoadBinder.this);

//		mBgdColour = res.getColor(R.color.rhythms_list_rhythm_bgd_fill);
		
		makeThread();
	}

    /**
     * RhythmsList is listening for changes to beat types, when that happens, clear the cache
     * so if any colours change the images will be up-to-date
     */
    public void clearImageCache() {
        AndroidResourceManager.logd(LOG_TAG, "clearImageCache: attempt to acquire");
        mLock.lock();
        try {
            AndroidResourceManager.logd(LOG_TAG, "clearImageCache: acquired lock, clearing cache");
            mRhythmEncodedBeats.clear();
            mRhythmImages.clear();

            // allow any waiters to check for data requests
            mNoData.signal();

        } finally {
            mLock.unlock();
        }
    }

	/**
	 * The put() method for getting an image from either the cache if it's not out of date
	 * or to request an image be made and cached for another read in the next onDraw()
	 * @param externalKey the rhythm key
	 * @param encodedBeats the encoded string
	 * @return the drawn image
	 */
	public Bitmap getBitmapImage(String externalKey, String encodedBeats) {

        if (mService.isDead()) {
            String msg = "getBitmapImage: make service has been killed (by unBind probably), return what we have, this binder is about to be unreferenced";
            AndroidResourceManager.loge(LOG_TAG, msg);
            return mRhythmImages.get(externalKey);
        }

		mLock.lock();
		try {
			// check it's not already requested first
			if (mRhythmRequests.contains(externalKey)) {
				AndroidResourceManager.logd(LOG_TAG, String.format("getBitmapImage: make already processing %s", externalKey));
				return null;
			}
				
			// got the beats, means also have the bitmap
			String cachedBeats = mRhythmEncodedBeats.get(externalKey);

			if (cachedBeats != null) {
				// still matches, then the bitmap is good
				if (cachedBeats.equals(encodedBeats)) {
//					AndroidResourceManager.logv(LOG_TAG, String.format("getBitmapImage: make cached image found for %s", externalKey));
					return mRhythmImages.get(externalKey);
				}
				else {
					AndroidResourceManager.logd(LOG_TAG, String.format("getBitmapImage: make have out of date image for %s", externalKey));
					// have a cached image but it's out of date, so remove both 
					mRhythmEncodedBeats.remove(externalKey);
					mRhythmImages.remove(externalKey);
				}
			}
			
			// got this far means need to request an image to be drawn
//			AndroidResourceManager.logd(LOG_TAG, String.format("getBitmapImage: make request image for %s, and signal data is ready", externalKey));
			mRhythmRequests.add(externalKey);
			mNoData.signal();
			
		} finally {
			mLock.unlock();
		}

		return null;
	}

	
	private void makeThread() {
		mLock = new ReentrantLock();
		mNoData = mLock.newCondition();

		mImageMakerThread = new Thread() {

			private boolean mIsRunning = true;
			
			@Override
			public void run() {
				while (mIsRunning) {
				
					String firstKey;
					
					// await() can be interrupted, if so the thread will exit on the next go round
					mLock.lock();
					try {
						while (mRhythmRequests.isEmpty()) {
							mNoData.await();
						}

						// got the data, keep on the queue till done
						firstKey = mRhythmRequests.remove(0);

						Rhythm rhythm = mRhythms.lookup(firstKey);
                        AndroidResourceManager.logd(LOG_TAG, String.format("make Image thread: start processing %s %s", firstKey, rhythm.getName()));
						mRhythmDraughter.initModel(rhythm, false);
				    	mRhythmDraughter.initDrawing();

				    	// make a bitmap and other objs to draw 
				    	Bitmap bitmap = Bitmap.createBitmap((int) mCanvasDimension.getW(), (int) mCanvasDimension.getH(), Config.ARGB_8888);

					    mCanvas = new Canvas(bitmap);
//				    	mCanvas.drawColor(mBgdColour);
				    	
				    	// draw rhythm will call the drawingSurface methods in this class
				    	mRhythmDraughter.drawRhythm();

				    	// put the data into the guarded maps
				    	mRhythmEncodedBeats.put(firstKey, rhythm.getEncodedBeatTypes());
				    	mRhythmImages.put(firstKey, bitmap);
  						AndroidResourceManager.logd(LOG_TAG, String.format("make Image thread: made image for %s", firstKey));
				    	
					}
					catch (InterruptedException e) {
						mIsRunning = false;
					}
                    catch (Exception e) {
                        AndroidResourceManager.loge(LOG_TAG, "makeThread.while(mIsRunning): unexpected error in loop. caught but allowed to keep looping", e);
                    }
					finally {
						mLock.unlock();
					}					
				}
				
				AndroidResourceManager.logd(LOG_TAG, "image make thread is exiting");
			}
			
			
		};
		mImageMakerThread.start();		
	}

	void destroy() {
		AndroidResourceManager.logd(LOG_TAG, "destroy called from Service (which is also dead)");
		if (mImageMakerThread != null) {
			mImageMakerThread.interrupt();
		}
	}
	
	//------------------- DrawingSurface methods, called in image maker thread in drawRhythm()
	
	@Override
	public MutableRectangle getDrawingRectangle() {
		return mCanvasDimension;
	}

	@Override
	public void drawImage(PlatformImage image, float x, float y, float w, float h) {

		if (mCanvas != null) {
			((AndroidImage) image).draw(mCanvas, x, y, x + w, y + h);
		}
		else {
			AndroidResourceManager.logw(LOG_TAG, "drawImage: no canvas, is this during onDraw()?");
		}
	}

	@SuppressWarnings("SpellCheckingInspection")
    @Override
	public void drawImage(PlatformImage image, float x, float y, float w,
			float h, float clipx, float clipy, float clipw, float cliph) {

		if (mCanvas != null) {
			mCanvas.save();

			mCanvas.clipRect(clipx, clipy, clipx + clipw, clipy + cliph);
			drawImage(image, x, y, w, h);
			
			mCanvas.restore();
		}
	}

	@Override
	public void drawText(PlatformFont font, int number, float x, float y) {
		drawText(font, Integer.toString(number), x, y);
	}

	@Override
	public void drawText(PlatformFont font, String text, float x, float y) {
		if (mCanvas != null) {
			((AndroidFont) font).drawText(mCanvas, (int) x, (int) y, text);
		}
		else {
			AndroidResourceManager.logw(LOG_TAG, "drawText: no canvas, is this during onDraw()?");
		}
	}

	//----------------- the following methods aren't used for the basic image
	
	@Override
	public void registerForEvents(PlatformEventListener eventListener) {
	}

	@Override
	public boolean triggeredBackEvent() {
		return false;
	}

    @Override
    public void pushTransform(float rotation, float transX, float transY) {
    }

    @Override
    public void popTransform() {

    }


}
