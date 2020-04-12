package com.courseracapstone.android;

import java.io.File;
import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.utils.ScalingUtilities;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;

/**
 * Keeps the data used in adding a gift (media file params etc)
 * It's a retained instance to persist if there's config changes
 * 
 * @author xxx xxx
 */
public class NonUIAddGiftDataFragment extends Fragment {

	public static final String NON_UI_ADD_FRAGMENT_TAG = "Potlatch-"+NonUIAddGiftDataFragment.class.getSimpleName();

	static final String KEY_LINK_TO_ID = "KEY_LINK_TO_ID";
	static final String KEY_LINK_TO_TITLE = "KEY_LINK_TO_TITLE";

	// passed as args
	private long mLinkToGiftId;
	private String mLinkToTitle;
	private Bitmap mLinkToBitmap;
	private DownloadBinder mDownloadBinder;
	private boolean mIsLinked;

	// these persist over config changes (as camera may cause change to landscape.. at least on 
	// the current L-preview emulator), so they will be saved on instance bundle (as well as the args)
	private File mCameraFile;
	private Uri mChosenUri; // chosen file
	private boolean mCameraInProgress;
	private boolean mChooserInProgress;
	private boolean mSaveInProgress;

	private Bitmap mBitmap;

	private WeakReference<AbstractGiftAddingActivity> mActivity;

	private LoadImageTask mLoadImageTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Keep this Fragment around during config changes
		setRetainInstance(true);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mActivity = new WeakReference<AbstractGiftAddingActivity>((AbstractGiftAddingActivity) getActivity());
		} catch (ClassCastException e) {
			Log.e(NON_UI_ADD_FRAGMENT_TAG, "onAttach: what is this activity? "+activity.getClass());
		}
	}

	/**
	 * When the ui AddGiftDialogFragment creates, it will detect if this fragment
	 * is already created (ie. on config change), only if it isn't does it pass
	 * its arguments through.
	 */
	@Override
	public void setArguments(Bundle args) {

		mLinkToGiftId = args.getLong(KEY_LINK_TO_ID, -1L);
		mIsLinked = mLinkToGiftId != -1;
		mLinkToTitle = !mIsLinked ? "" : args.getString(KEY_LINK_TO_TITLE);
		
		mDownloadBinder = (DownloadBinder) args.getBinder(GiftsActivity.PARAM_BINDER);
		mLinkToBitmap = mDownloadBinder.getSharedImage();
	}
	
	@Override
	public void onDestroy() {
		// clean up, should not fail, if it does it's not that important... as it
		// will be overwritten by the next camera shot
		if (mCameraFile != null && mCameraFile.exists()) {
			Log.w(NON_UI_ADD_FRAGMENT_TAG, "onDestroy: media file was not cleaned up... removing it");
			removeExistingTempFile();
		}
		super.onDestroy();
	}
	

	/**
	 * Called by the activity.bindToHandler() to assess if a config change happened and the dialog is
	 * in progress
	 * 
	 * @return
	 */
	boolean isWorkInProgress() {
		return mCameraInProgress
				|| mChooserInProgress
				|| mSaveInProgress 
				|| mBitmap != null 
				|| mLoadImageTask != null;
	}

	/**
	 * Called before capturing a camera file to the temp location, and also after
	 * reading the captured image, to immediately free the space used
	 * 
	 * @return true if the space is freed up, false if there's some error
	 */
	boolean removeExistingTempFile() {
		if (mCameraFile != null && mCameraFile.exists()) {
			return mCameraFile.delete();
		}
		
		return true;
	}


	public File getCameraFile() {
		return mCameraFile;
	}

	public void setCameraFile(File file) {
		this.mCameraFile = file;
	}

	public Uri getChosenUri() {
		return mChosenUri;
	}

	public void setmChosenUri(Uri chosenUri) {
		this.mChosenUri = chosenUri;
	}

	public boolean isChooserInProgress() {
		return mChooserInProgress;
	}

	public void setChooserInProgress(boolean inProgress) {
		mChooserInProgress = inProgress;
		removeExistingTempFile();
		mCameraFile = null;
	}

	public boolean isCameraInProgress() {
		return mCameraInProgress;
	}

	public void setCameraInProgress(boolean inProgress) {
		this.mCameraInProgress = inProgress;
		mChosenUri = null;
	}

	public boolean isSaveInProgress() {
		return mSaveInProgress;
	}

	public void setSaveInProgress(boolean mSaveInProgress) {
		this.mSaveInProgress = mSaveInProgress;
	}

	public long getLinkToGiftId() {
		return mLinkToGiftId;
	}

	public String getLinkToTitle() {
		return mLinkToTitle;
	}

	public Bitmap getLinkToBitmap() {
		return mLinkToBitmap;
	}

	public DownloadBinder getDownloadBinder() {
		return mDownloadBinder;
	}

	public boolean isLinked() {
		return mIsLinked;
	}

	/**
	 * Called after the camera has taken a picture, the media file is already known
	 * @param imageVw
	 */
	public void loadImage(TextView imageVw) { 
		// could throw IllegalArgument, happens if config change is in the process of destroying
		// the activity/fragment and the view is 0 w/h
		mLoadImageTask = new LoadImageTask(imageVw);
		mLoadImageTask.execute();
	}
	
	/**
	 * Called after choosing a new file from the device, the uri should point to it
	 * @param mImageVw
	 * @param uri
	 * @param contentResolver 
	 */
	public void loadImage(TextView imageVw, Uri uri, ContentResolver contentResolver) {
		mChosenUri = uri;
		mLoadImageTask = new LoadImageTask(imageVw, contentResolver);
		mLoadImageTask.execute();
	}

	/**
	 * Called when the ui fragment is closing, either because save success or canceling
	 * Either way remove the temporary file and reset everything
	 * @param onSave
	 */
	public void cleanup(boolean onSave) {
		removeExistingTempFile();
		mCameraFile = null;
		mChosenUri = null;
		mCameraInProgress = false;
		mChooserInProgress = false;
		mSaveInProgress = false;
		mBitmap = null;
		mLoadImageTask = null;
	}

	/** 
	 * Called because some error has happened trying to upload the picture. The user is
	 * still on the screen, so don't remove the files, just clear the flags so they can
	 * continue with something.
	 */
	public void clearProgressOnly() {
		mCameraInProgress = false;
		mChooserInProgress = false;
		mSaveInProgress = false;
	}

	/**
	 * Called by the load image task when it completes. If the view is still active, update it
	 * on success or feedback to user if failure.
	 * Otherwise, rely on the caller to call loadImage() again
	 */
	private void reactToLoadResults() {
		
		if (mLoadImageTask.mSuccess) {
			TextView imageVw = mLoadImageTask.mImageVw.get();
			if (imageVw != null) {
				try {
					imageVw.setHint(""); // it has a prompt in it until now
					imageVw.setBackground(new BitmapDrawable(getResources(), mBitmap));					
				}
				catch (Exception e) {
					Log.d(NON_UI_ADD_FRAGMENT_TAG, "reactToLoadResults: success but failed updating view, probably it's destroyed");
				}
			}
		}
		else {
			Activity activity = mActivity.get();
			if (activity != null && activity.getWindow().isActive()) {
				Toast.makeText(activity, getResources().getString(R.string.scale_error), Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * An async task to read the camera image file and scale it
	 */
	private class LoadImageTask extends AsyncTask<Void, Void, Bitmap> {

		private int mWidth;
		private int mHeight;
		
		private WeakReference<TextView> mImageVw;
		
		private boolean mSuccess = false;
		private ContentResolver mContentResolver;

		public LoadImageTask(TextView imageVw) {
			mWidth = imageVw.getWidth();
			mHeight = imageVw.getHeight();
			
			mImageVw = new WeakReference<TextView>(imageVw);
			
			if (mWidth == 0 || mHeight == 0) {
				// can happen if config change overlaps with creation
				throw new IllegalArgumentException("can't scale a view with no dimensions");
			}
		}

		public LoadImageTask(TextView imageVw, ContentResolver contentResolver) {
			this(imageVw);
			mContentResolver = contentResolver;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {

			// use the scaling utility to avoid loading big file for small destination
			if (mCameraFile != null) {
				return ScalingUtilities.createScaledBitmap(
					mCameraFile.getAbsoluteFile(), mWidth, mHeight, ScalingLogic.FIT);
			}
			else if (mChosenUri != null && mContentResolver != null) {
				return ScalingUtilities.createScaledBitmap(mContentResolver, mChosenUri,
						mWidth, mHeight, ScalingLogic.FIT);
			}
			else {
				Log.e(NON_UI_ADD_FRAGMENT_TAG, "LoadImageTask: missing either camera file or chosen file");
				return null;
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {

			mBitmap = result;
			mSuccess = result != null;

			if (result != null) {
				Log.d(NON_UI_ADD_FRAGMENT_TAG, "LoadImageTask.onPostExecute: success");
			}
			else {
				Log.d(NON_UI_ADD_FRAGMENT_TAG, "LoadImageTask.onPostExecute: failed somewhere in the scaling stuff");
				
				// since it has failed, make sure the media file is removed (to prevent save/upload)
				removeExistingTempFile();
				mCameraFile = null;
				mChosenUri = null;
			}
			
			reactToLoadResults();
		}
	}

}
