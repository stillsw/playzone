package com.courseracapstone.android.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedFile;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.LongSparseArray;

import com.courseracapstone.android.R;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.PotlatchSvcApi;
import com.courseracapstone.android.service.DownloadBinder.ApiInstanceWrapper;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.utils.ScalingUtilities;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;
import com.courseracapstone.android.utils.UriToFileUtils;
import com.courseracapstone.common.PotlatchConstants;

/**
 * Handles downloading and caching of images
 * 
 * @author xxx xxx
 *
 */
public class ImageDownloadBinderDelegate {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();

	private static final String SCALED_TEMP_FILE_NAME = File.separator + "~temp_scaled_file";

	@SuppressWarnings("serial")
	public class ImageFailedInDownloadException extends Exception {};
	
	@SuppressWarnings("serial")
	public static class ImageFailedToGoException extends Exception {
		public ImageFailedToGoException(String errm) {
			super(errm);
		}
	};
	
	private DownloadBinder mDownloadBinder;
	private DataDownloadService mService;

	// a queue of images to download, protected by a lock
	private ArrayList<ImageQueuedThumb> mQueuedImages = new ArrayList<ImageQueuedThumb>(); 
	private ReentrantLock mImagesQueueLock = new ReentrantLock();
	private Condition mEmpty = mImagesQueueLock.newCondition();
	
	// a cache of downloaded images, protected by a lock
	private LongSparseArray<CachedThumbImage> mThumbCache = new LongSparseArray<CachedThumbImage>();
	private ReentrantLock mImageCacheLock = new ReentrantLock();

	private final float mThumbImageViewSize;
	
	// all for download/scaling of the full size image for gift view
	// protect the scaled image with a lock
	private ReentrantLock mScaledImageLock = new ReentrantLock();
	private Bitmap mFullGiftImage, mScaledGiftImage;
	private long mFullImageGiftId, mScaledImageGiftId;
	private boolean mIsScaledFromFullImage;
	
	ScalingLogic mScalingLogic = ScalingLogic.CROP;
	
	ImageDownloadBinderDelegate(DownloadBinder downloadBinder, DataDownloadService service) {
		mDownloadBinder = downloadBinder;
		mService = service;
		mThumbImageViewSize = mService.getResources().getDimension(R.dimen.gifts_lists_thumb_size);
	}

	/**
	 * Called by the binder when the image view wants the image, if it's been cached it's returned 
	 * and if there was a problem downloading it, an exception is thrown so the
	 * caller can feedback and stop asking for it.
	 * 
	 * @param giftId
	 * @return
	 * @throws ImageFailedInDownloadException 
	 */
	Bitmap getThumbImageFromCache(long giftId) throws ImageFailedInDownloadException {

		Log.d(LOG_TAG, String.format("getThumbImageFromCache : locking cache (gift=%s)", giftId));
		
		mImageCacheLock.lock();
		
		try {
			CachedThumbImage img = mThumbCache.get(giftId);
			if (img != null) {
				if (img.mThumbImage == null) {
					Log.d(LOG_TAG, "getThumbImageFromCache : image failed to download");
					throw new ImageFailedInDownloadException();
				}
				
				// update the image with the access time, in the future might want
				// to age out old cached images, perhaps prune the ones used least recently
				img.mLastAccessed = System.currentTimeMillis();
				
				Log.v(LOG_TAG, "getThumbImageFromCache : done, return image");
				return img.mThumbImage;
			}
			
			Log.v(LOG_TAG, "getThumbImageFromCache : done, no image found yet");
			return null;
		}
		finally {
			mImageCacheLock.unlock();
		}
	}

	/**
	 * Called by the GiftsBinderDelegage on refreshGifts, the list should be
	 * only new gifts, but this method will test they're not 
	 * a) in the queue already
	 * b) already cached
	 *  
	 * @param giftsList
	 */
	void addNewImagesToDownloadQueue(ArrayList<Gift> giftsList) {
		
		Log.v(LOG_TAG, "addNewImagesToDownloadQueue: attempt to lock");
		
		mImagesQueueLock.lock();
		
		try {
			Log.v(LOG_TAG, "addNewImagesToDownloadQueue: got lock, adding queued images");
		
			// process each row in the list, just add to the queue
			for (Gift gift : giftsList) {
				mQueuedImages.add(new ImageQueuedThumb(gift.getId()));
			}
			
			Log.v(LOG_TAG, "addNewImagesToDownloadQueue: done queueing images, signal empty and release lock");

			mEmpty.signal();
		}
		finally {
			mImagesQueueLock.unlock();
		}
	}
	
	/**
	 * This is run by the binder during creation, and just after creating this class.
	 * It's a continuous thread that waits for requests for images and then downloads
	 * them
	 * @return
	 */
	ContinuousRunner makeContinuousRunningDownloader() {
		return new ContinuousRunner(mDownloadBinder) {

			@Override
			protected void doRepetitiveTask() throws InterruptedException {

				ArrayList<ImageQueuedThumb> processList = new ArrayList<ImageQueuedThumb>();

				// check for requests for images
				// acquire lock on the blocking queue
				
				mImagesQueueLock.lock();
				
				try {
					while (mQueuedImages.size() == 0) {
						mEmpty.awaitUninterruptibly();
					}
					
					Log.v(LOG_TAG, "image downloader : has images to process");
				
					// take the whole list so addNewImagesToDownloadQueue()
					// blocks for the least possible time
					processList.addAll(mQueuedImages);					
					mQueuedImages.clear();
				}
				finally {
					mImagesQueueLock.unlock();
				}
		
				Log.v(LOG_TAG, "image downloader : processing list");
				
				// now can process them one by one
				for (ImageQueuedThumb thumb : processList) {

					// nothing else updates the cache, so first see if it's
					// already in there without locking
					if (mThumbCache.indexOfKey(thumb.mGiftId) >= 0) {
						Log.v(LOG_TAG, String.format("image downloader : gift (id=%s) cached already", thumb.mGiftId));
						continue;
					}
					
					downloadImageInOwnThread(thumb);
					
				}
				
			}
		};

	}
	
	/**
	 * Already called from the continuous run thread, called for each thumb
	 * in the queue. Grabs another thread and adds the downloaded image to
	 * the cache.
	 * @param thumb
	 */
	protected void downloadImageInOwnThread(final ImageQueuedThumb thumb) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				Log.v(LOG_TAG, String.format("downloadImageInOwnThread : caching thumb for gift (id=%s)"
						, thumb.mGiftId));
				
				// need a api instance, this call can block on the semaphore
				ApiInstanceWrapper wrapper = mDownloadBinder.getApiInstanceForImageDownload();
				InputStream imageData = null;
				Bitmap image = null;
				
				try {
					PotlatchSvcApi apiInstance = wrapper.mApiInstance;
					Response response = apiInstance.getGiftThumbnailData(thumb.mGiftId);
					imageData = response.getBody().in();
					image = BitmapFactory.decodeStream(imageData);
				}
				catch (RetrofitError e) {
					// log only - probably 404 not found
					Log.v(LOG_TAG, "downloadImageInOwnThread: unable to download image"); 

					try {
						if (mDownloadBinder.processServerError(e, false) == DownloadBinder.ERROR_AUTH_TOKEN_STALE) {
							Log.w(LOG_TAG, "downloadImageInOwnThread: send message to handler that token is stale");
							mDownloadBinder.sendMessage(DownloadBinder.ERROR_AUTH_TOKEN_STALE, null);
						}
					} 
					catch (AuthTokenStaleException e1) {} // false means don't throw it
				}
				catch (IOException e) {
					Log.e(LOG_TAG, "downloadImageInOwnThread: Error getting retrieving file", e);
				}
				finally {
					if (imageData != null) {
						try {
							imageData.close();
						} catch (Exception e) {
							Log.e(LOG_TAG, "downloadImageInOwnThread: Error closing the input stream "+e.getMessage());
						}
					}
					
					// give the wrapper back
					mDownloadBinder.releaseApiInstanceFromImageDownload(wrapper);
				}
				
				// from now on a cached image will be put on the cache, but if there's
				// any error the actual bitmap will be null, this is to prevent
				// repeated calls from the image view to an errored image

				CachedThumbImage cacheImg = new CachedThumbImage(image, mThumbImageViewSize);
				
				// lock the cache list, gives the other threads (and the ui)
				// a chance to grab any it needs in between each addition 
				Log.v(LOG_TAG, String.format("downloadImageInOwnThread : locking cache for img=%s", cacheImg));
				
				mImageCacheLock.lock();
				
				try {
					mThumbCache.put(thumb.mGiftId, cacheImg);
				}
				finally {
					Log.v(LOG_TAG, String.format("downloadImageInOwnThread : done, unlock cache (has %s images)"
							, mThumbCache.size()));
					mImageCacheLock.unlock();
				}
			}
			
		});
	}

	/**
	 * Called from binder.getGiftAndPrepareFullImage() when viewing an image, it it matches
	 * the last image downloaded can return that, otherwise try to get it.
	 * 
	 * Note: this is where the full image is downloaded, it could be too slow to use for big
	 * images on the internet... plus that might be overkill when the device can only show a 
	 * small image anyway, a refinement might be to pass the max size of image this particular
	 * device can display as a parameter and have the server scale it before sending.
	 * 
	 * @param giftId
	 */
	protected void downloadImageInOwnThread(final long giftId) {
		
		// abort if no auth token
		if (!mDownloadBinder.checkAuthTokenIsValid()) {
			return;
		}
		
		// check not already holding the image (from previous download)
		if (mFullImageGiftId == giftId) {
			Log.d(LOG_TAG, String.format("downloadImageInOwnThread : already have full image for gift (id=%s)"
					, giftId));
			mDownloadBinder.sendMessage(DownloadBinder.FULL_IMAGE_READY, null);
			return;
		}
		
		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				
				Log.v(LOG_TAG, String.format("downloadImageInOwnThread : get full image for gift (id=%s)"
						, giftId));
				
				// need a api instance, this call can block on the semaphore
				ApiInstanceWrapper wrapper = mDownloadBinder.getApiInstanceForImageDownload();
				InputStream imageData = null;
				mFullGiftImage = null;
				mFullImageGiftId = -1L;
				
				try {
					PotlatchSvcApi apiInstance = wrapper.mApiInstance;
					Response response = apiInstance.getGiftImageData(giftId);
					imageData = response.getBody().in();
					
					// in case the scaling thread is currently making a thumb scaled image
					// wait for it with a lock before doing this
					mScaledImageLock.lock();
					try {
						mFullGiftImage = BitmapFactory.decodeStream(imageData);
						mFullImageGiftId = giftId;

						mDownloadBinder.sendMessage(DownloadBinder.FULL_IMAGE_READY, null);
					}
					finally {
						mScaledImageLock.unlock();
					}
				}
				catch (RetrofitError e) {
					// log only - probably 404 not found
					Log.v(LOG_TAG, "downloadImageInOwnThread: unable to download image"); 

					try {
						if (mDownloadBinder.processServerError(e, false) == DownloadBinder.ERROR_AUTH_TOKEN_STALE) {
							Log.w(LOG_TAG, "downloadImageInOwnThread: send message to handler that token is stale");
							mDownloadBinder.sendMessage(DownloadBinder.ERROR_AUTH_TOKEN_STALE, null);
						}
					} 
					catch (AuthTokenStaleException e1) {} // false means don't throw it
				}
				catch (IOException e) {
					Log.e(LOG_TAG, "downloadImageInOwnThread: Error getting retrieving file", e);
				}
				finally {
					if (imageData != null) {
						try {
							imageData.close();
						} catch (Exception e) {
							Log.e(LOG_TAG, "downloadImageInOwnThread: Error closing the input stream "+e.getMessage());
						}
					}
					else {
						mDownloadBinder.sendMessage(DownloadBinder.FULL_IMAGE_NOT_AVAILABLE, null);
					}
					
					// give the wrapper back
					mDownloadBinder.releaseApiInstanceFromImageDownload(wrapper);
				}				
			}
		});
	}
	
	/**
	 * Called by the gifts binder delegate after it has uploaded a new gift, to upload
	 * the image that goes with it. The file might need to be scaled down to size.
	 * 
	 * Already running in a thread.
	 * 
	 * @param apiInstance
	 * @param giftId
	 * @param imageFile
	 * @throws ImageFailedToGoException
	 * @throws RetrofitError
	 */
	public void uploadImageForGiftInSameTask(PotlatchSvcApi apiInstance, long giftId, File imageFile)
		throws ImageFailedToGoException, RetrofitError {
		
		long fileSize = imageFile.length();
		boolean isOverSized = fileSize > PotlatchConstants.MAX_IMAGE_SIZE;

		float scaleProp = isOverSized 
				? PotlatchConstants.MAX_IMAGE_SIZE / (float)fileSize : 1.f; // default to not scale
		
		// find out what we have, and the dimensions
		Options options = ScalingUtilities.getScalingOptionsForFile(imageFile);
		if (options == null) {
			throw new ImageFailedToGoException("Unrecognized image file type");
		}
		
		String mime = options.outMimeType;
		String format = PotlatchConstants.MIME_FORMATS.get(options.outMimeType);
		
		Log.d(LOG_TAG, String.format(
				"uploadImageForGiftInSameTask: file (%s), mime=%s, format=%s, size=%s, over limit=%s, scaleBy=%s",
					imageFile.getName(), options.outMimeType, format, fileSize, isOverSized, scaleProp));

		// if oversized, create a scaled version of the file to the max size allowed by the server
		// (it might be rough because will use png for the output compression type)
		File scaledFile = null;
		FileOutputStream fos = null;
		
		
		if (isOverSized) {
			// test for jpeg, it's being compressed to png, it'll be much larger after compression
			if (options.outMimeType.equals(PotlatchConstants.MIME_JPG)) {
				scaleProp *= .1f;
			}
				
			mime = PotlatchConstants.MIME_PNG;
			format = PotlatchConstants.MIME_FORMATS.get(mime);
			
			// get the bitmap and scale it to the proportion to make the max (if it's smaller it
			// will be same size as scaling = 1f
			Bitmap bitmap = ScalingUtilities.createScaledBitmap(imageFile, scaleProp, ScalingLogic.FIT, options);

			if (bitmap == null) {
				throw new ImageFailedToGoException("Error scaling image");
			}
			
			// make a new file, note at this point the storage has already been checked, so can just write
			// another file there and remove it when done
			File mediaStorageDir = getMediaStorageDir();
			
			scaledFile = new File(mediaStorageDir, SCALED_TEMP_FILE_NAME);
			
			try {
				fos = new FileOutputStream(scaledFile);
			} 
			catch (FileNotFoundException e) {
				// should be impossible here, since already asked for private storage
				throw new ImageFailedToGoException("Failed to get permission for temp file");
			}
			
			bitmap.compress(CompressFormat.PNG, 100, fos); // fos will be closed in finally {}
		}
		
		TypedFile tf = new TypedFile(mime, isOverSized ? scaledFile : imageFile);

		try {
			apiInstance.setGiftImageById(giftId, format, tf);
		} 
		catch (RetrofitError e) {
			Log.e(LOG_TAG, "uploadImageForGiftInSameTask: error in image upload");
			throw e; // caught in caller
		}
		finally {
			if (scaledFile != null) {
				// clean up streams
				try {
					if (fos != null) {
						fos.close();
					}
				}
				catch (IOException e) {}
				finally {
					// remove the file
					if (scaledFile.exists() && !scaledFile.delete()) {
						Log.w(LOG_TAG, "Clean up error removing temporary file");
					}
				}
				
			}
		}
	}

	/**
	 * Called by the gifts binder delegate after it has uploaded a new gift, to upload
	 * the image that goes with it. The file might need to be scaled down to size.
	 * 
	 * Overloaded version for Uri chosen by user
	 * 
	 * Already running in a thread.
	 * 
	 * @param apiInstance
	 * @param giftId
	 * @param contentUri
	 * @throws ImageFailedToGoException
	 * @throws RetrofitError
	 */
	public void uploadImageForGiftInSameTask(PotlatchSvcApi apiInstance, long giftId, Uri contentUri)
			throws ImageFailedToGoException, RetrofitError {
			
		long fileSize = ScalingUtilities.getFileLenFromUri(mService.getContentResolver(), contentUri);
		Log.d(LOG_TAG, "uploadImageForGiftInSameTask: tested fileSize = "+fileSize);
		boolean isOverSized = fileSize > PotlatchConstants.MAX_IMAGE_SIZE;

		float scaleProp = isOverSized 
				? PotlatchConstants.MAX_IMAGE_SIZE / (float)fileSize : 1.f; // default to not scale
		
		// find out what we have, and the dimensions
		Options options = ScalingUtilities.getScalingOptionsForContentUri(mService.getContentResolver(), contentUri);
		if (options == null) {
			throw new ImageFailedToGoException("Unrecognized image file type");
		}
		
		String mime = options.outMimeType;
		String format = PotlatchConstants.MIME_FORMATS.get(options.outMimeType);
		
		Log.d(LOG_TAG, String.format(
				"uploadImageForGiftInSameTask: uri (%s), mime=%s, format=%s, size=%s, over limit=%s, scaleBy=%s",
					contentUri, options.outMimeType, format, fileSize, isOverSized, scaleProp));

		// may make a new file, note at this point the storage has already been checked, so can just write
		// another file there and remove it when done
		File mediaStorageDir = getMediaStorageDir();
		
		// if oversized, create a scaled version of the file to the max size allowed by the server
		// (it might be rough because will use png for the output compression type)
		File tempFile = null, imageFile = null;
		FileOutputStream fos = null;
		
		if (isOverSized) {
			// test for jpeg, it's being compressed to png, it'll be much larger after compression
			if (options.outMimeType.equals(PotlatchConstants.MIME_JPG)) {
				scaleProp *= .1f;
			}
				
			mime = PotlatchConstants.MIME_PNG;
			format = PotlatchConstants.MIME_FORMATS.get(mime);				
				
	        int dstWidth = (int) (options.outWidth * scaleProp);
	        int dstHeight = (int) (options.outHeight * scaleProp);
			
			Bitmap bitmap = ScalingUtilities.createScaledBitmap(mService.getContentResolver(), 
					contentUri, dstWidth, dstHeight, ScalingLogic.FIT);

			if (bitmap == null) {
				throw new ImageFailedToGoException("Error scaling image");
			}
			
			tempFile = new File(mediaStorageDir, SCALED_TEMP_FILE_NAME);
			
			try {
				fos = new FileOutputStream(tempFile);
			} 
			catch (FileNotFoundException e) {
				// should be impossible here, since already asked for private storage
				throw new ImageFailedToGoException("Failed to get permission for temp file");
			}
			
			bitmap.compress(CompressFormat.PNG, 100, fos); // fos will be closed in finally {}
		}
		
		// not scaling, make the image file from the uri
		else {
			imageFile = UriToFileUtils.getFileForUri(contentUri, mService);
			
			if (imageFile == null) {
				// assign the imageFile to the tempFile
				// this way the finally block only needs to close the tempFile
				imageFile = tempFile = new File(mediaStorageDir, SCALED_TEMP_FILE_NAME);
				
				try {
					UriToFileUtils.writeUriToFile(contentUri, mService.getContentResolver(), mService, imageFile);
				} 
				catch (IOException e) {
					Log.e(LOG_TAG, "uploadImageForGiftInSameTask: error writing uri to temp file", e);
					throw new ImageFailedToGoException("Error writing uri to temp file");
				}
			}
		}
		
		TypedFile tf = new TypedFile(mime, isOverSized ? tempFile : imageFile);

		try {
			apiInstance.setGiftImageById(giftId, format, tf);
		} 
		catch (RetrofitError e) {
			Log.e(LOG_TAG, "uploadImageForGiftInSameTask: error in image upload");
			throw e; // caught in caller
		}
		finally {
			if (tempFile != null) {
				// clean up streams
				try {
					if (fos != null) {
						fos.close();
					}
				}
				catch (IOException e) {}
				finally {
					// remove the file
					if (tempFile.exists() && !tempFile.delete()) {
						Log.w(LOG_TAG, "Clean up error removing temporary file");
					}
				}
				
			}
		}
	}

	/**
	 * Opens and creates the storage directory if needed
	 * @return
	 */
	public static File getMediaStorageDir() throws ImageFailedToGoException {
		File mediaStorageDir = new File(Environment.getExternalStorageDirectory(),
				PotlatchConstants.APP_SHORT_NAME);
		
		if (!mediaStorageDir.exists()) {
			mediaStorageDir.mkdir();
		}
		
		if (!mediaStorageDir.isDirectory()) {
			throw new ImageFailedToGoException("failed to create media storage directory");
		}
		return mediaStorageDir;
	}

	/**
	 * Called from binder.loadScaledImagesForGift(), the view now has set its size and it's possible
	 * to provide scaled images. If the full image has been downloaded, scale that and send a message
	 * otherwise do it for the cached thumbnail (the full image is coming... we hope!)
	 *
	 * @param giftId
	 * @return
	 */
	void scaleImageInOwnThread(final long giftId, final int w, final int h) {

		mDownloadBinder.mExecutor.execute(new Runnable() {

			@Override
			public void run() {
				
				// check not already holding the image (from previous download)
				if (mFullImageGiftId == giftId && mFullGiftImage != null) {
					
					// is there a scaled image, and is it from the full image or the thumb?
					if (mScaledImageGiftId == giftId
							&& mScaledGiftImage != null
							&& mIsScaledFromFullImage
							// and it's the right size
							&& mFullGiftImage.getWidth() == w 
							&& mFullGiftImage.getHeight() == h) {
						
						Log.d(LOG_TAG, String.format("downloadImageInOwnThread : already have scaled full image for gift (id=%s)"
								, mFullImageGiftId));
					}
					else {
						Log.d(LOG_TAG, String.format("downloadImageInOwnThread : have full image for gift, need to scale it (id=%s)"
								, mFullImageGiftId));

						makeScaledImage(mFullGiftImage, giftId, w, h);
						mIsScaledFromFullImage = true;
					}

					mDownloadBinder.sendMessage(DownloadBinder.SCALED_FULL_IMAGE_READY, null);

				}
				
				// same check now if have a scaled image already, though it could be the thumb
				// is there a scaled image, and is it from the full image or the thumb?
				else if (mScaledImageGiftId == giftId
						&& mScaledGiftImage != null
						// and it's the right size
						&& mScaledGiftImage.getWidth() == w 
						&& mScaledGiftImage.getHeight() == h) {
					
					Log.d(LOG_TAG, String.format("downloadImageInOwnThread : already have scaled thumb image for gift (id=%s)"
							, mFullImageGiftId));

					mDownloadBinder.sendMessage(DownloadBinder.SCALED_INTERIM_IMAGE_READY, null);
				}
				else {
					mScaledImageGiftId = -1L;
					mScaledGiftImage = null;

					// get from the cache image, if there is one
					try {
						Bitmap thumb = getThumbImageFromCache(giftId);
						if (thumb != null) { // should have it, if not don't message the ui at all yet
							Log.d(LOG_TAG, String.format("downloadImageInOwnThread : have thumb for gift, need to scale it (id=%s)"
									, mFullImageGiftId));

							// right at this point it's possible to overwrite a full size image that's coming in on another
							// thread... so take a lock and check that before carrying on
							mScaledImageLock.lock();
							try {
								// have the lock, now make sure nothing changed before write the scaled image
								if (mFullImageGiftId != giftId
										&& mScaledImageGiftId != giftId) {								
								
									makeScaledImage(thumb, giftId, w, h);
									mIsScaledFromFullImage = false;
			
									mDownloadBinder.sendMessage(DownloadBinder.SCALED_INTERIM_IMAGE_READY, null);
								}
								else {
									Log.i(LOG_TAG, "downloadImageInOwnThread: it appears the full image came in, so abort thumb scaling");
								}
							}
							finally {
								mScaledImageLock.unlock();
							}
						}
					}
					catch (ImageFailedInDownloadException e) {
						Log.d(LOG_TAG, String.format("downloadImageInOwnThread : no thumb image for gift (id=%s)"
								, mFullImageGiftId));

						// use the same key that denotes no full image, if a full image does get
						// downloaded it will notify the activity again
						mDownloadBinder.sendMessage(DownloadBinder.FULL_IMAGE_NOT_AVAILABLE, null);
					}
				}
			}
		});
	}

	/**
	 * Called when the user toggles the full image scaling mode
	 * @param w
	 * @param h
	 */
	void changeScalingLogic(int w, int h, ScalingLogic scalingLogic) {
		
		if (scalingLogic == mScalingLogic) {
			// nothing to do
			return;
		}
		
		if (mScalingLogic == ScalingLogic.CROP) {
			mScalingLogic = ScalingLogic.FIT;
		}
		else {
			mScalingLogic = ScalingLogic.CROP;
		}
		
		// should be one, so clear it out and cause rescale
		if (mFullGiftImage != null) {
			mScaledImageGiftId = -1L;
			mScaledGiftImage = null;
			scaleImageInOwnThread(mFullImageGiftId, w, h);
		}
	}
	
	void makeScaledImage(Bitmap image, long giftId, int w, int h) {
		mScaledImageGiftId = giftId;
		mScaledGiftImage = ScalingUtilities.createScaledBitmap(image, w, h, mScalingLogic);
	}

	/**
	 * Called after loadScaledImagesForGift() has previously notified the ui
	 * that there's an image ready  
	 * @param giftId
	 * @return
	 */
	Bitmap getScaledImageForGift(long giftId) {
		if (mScaledImageGiftId == giftId) {
			return mScaledGiftImage;
		}
		else {
			Log.w(LOG_TAG, "getScaledImageForGift: called with a different giftId previously for scaling");
			return null;
		}
	}
	

	private static class ImageQueuedThumb {
		private long mGiftId;
		
		public ImageQueuedThumb(long giftId) {
			this.mGiftId = giftId;
		}
		
	}

	private static class CachedThumbImage {
//		private final String LOG_TAG = "Potlatch-"+CachedThumbImage.class.getSimpleName();
		
		private long mLastAccessed = System.currentTimeMillis();
		private final Bitmap mThumbImage;
		
		public CachedThumbImage(Bitmap thumbImage, float iwh) {

			// scale it to the destination size
			if (thumbImage == null) {
				mThumbImage = null;
			}
			else {
				// draw it to fit, scale so the 
				float w = thumbImage.getWidth();
				float h = thumbImage.getHeight();

				// image dimensions the same (square)
				float viewToImgRatio = Math.max(h, w) / iwh;
				
				float nw = w / viewToImgRatio;
				float nh = h / viewToImgRatio;

				mThumbImage = Bitmap.createScaledBitmap(thumbImage, (int)nw, (int)nh, false);
			}
		}

		@Override
		public String toString() {
			return String.format("[hasImage=%s, lastAccessed=%s]", mThumbImage != null, mLastAccessed);
		}
		
		
	}


}
