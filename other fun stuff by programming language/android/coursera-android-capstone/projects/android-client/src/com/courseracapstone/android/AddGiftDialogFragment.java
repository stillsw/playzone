package com.courseracapstone.android;

import java.io.File;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorInflater;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.courseracapstone.android.accountsetup.User;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.DownloadBinder.DownloadBinderRecipient;
import com.courseracapstone.android.service.ImageDownloadBinderDelegate;
import com.courseracapstone.android.service.ImageDownloadBinderDelegate.ImageFailedToGoException;
import com.courseracapstone.common.GiftsSectionType;

/**
 * A dialog shown to add a new gift
 * 
 * @author xxx xxx
 * 
 */
public class AddGiftDialogFragment extends DialogFragment implements
		OnCheckedChangeListener, OnClickListener, DownloadBinderRecipient,
		OnLayoutChangeListener {

	private static final String LOG_TAG = "Potlatch-"
			+ AddGiftDialogFragment.class.getSimpleName();

	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private static final int CHOOSE_IMAGE_ACTIVITY_REQUEST_CODE = 200;

	private static final String CAMERA_TEMP_FILE_NAME = File.separator
			+ "~temp_camera.jpg";

	@SuppressWarnings("serial")
	private static class ExternalStorageException extends Exception {

		public ExternalStorageException(String detailMessage) {
			super(detailMessage);
		}
	}

	private AbstractPotlatchActivity mActivity;

	// re-created if config change
	private CheckBox mLinkChainChk;
	private ImageView mThumbVw;
	private ImageButton mFabBtn;
	private Button mCancelBtn;
	private Button mSaveBtn;
	private TextView mImageVw;
	private EditText mEditTitle;
	private EditText mEditAddedText;
	private ProgressBar mProgressBar;

	// slave non-ui fragment to hold data needed if config changes
	private NonUIAddGiftDataFragment mNonUIFragment;

	private boolean mIsHideThumb;

	// persistent fragment

	/**
	 * Called from view gift activity, so assumes to link to the same chain
	 * 
	 * @param downloadBinder
	 * @param linkToGiftId
	 * @param linkToGiftTitle
	 * @return
	 */
	public static AddGiftDialogFragment newInstance(
			DownloadBinder downloadBinder, long linkToGiftId,
			String linkToGiftTitle) {

		Bundle bundle = new Bundle();
		bundle.putLong(NonUIAddGiftDataFragment.KEY_LINK_TO_ID, linkToGiftId);
		bundle.putString(NonUIAddGiftDataFragment.KEY_LINK_TO_TITLE,
				linkToGiftTitle);
		bundle.putBinder(GiftsActivity.PARAM_BINDER, downloadBinder);

		AddGiftDialogFragment fragment = new AddGiftDialogFragment();
		fragment.setArguments(bundle);

		return fragment;
	}

	/**
	 * Called from gifts list activity, there's no chain to link to here
	 * 
	 * @param downloadBinder
	 * @return
	 */
	public static AddGiftDialogFragment newInstance(
			DownloadBinder downloadBinder) {
		Bundle bundle = new Bundle();
		bundle.putBinder(GiftsActivity.PARAM_BINDER, downloadBinder);

		AddGiftDialogFragment fragment = new AddGiftDialogFragment();
		fragment.setArguments(bundle);

		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setCancelable(false);
		// setStyle(STYLE_NORMAL, R.style.Dialogs);

		// find the instance of the non-ui fragment created by the activity the
		// first time in

		FragmentManager fragmentManager = getFragmentManager();
		mNonUIFragment = (NonUIAddGiftDataFragment) fragmentManager
				.findFragmentByTag(NonUIAddGiftDataFragment.NON_UI_ADD_FRAGMENT_TAG);

		if (mNonUIFragment == null) {
			Log.e(LOG_TAG,
					"onCreate: calling activity has not created the non-ui fragment");
			dismissAllowingStateLoss();
		} else {
			Log.d(LOG_TAG,
					String.format(
							"onCreate: got non-ui frag, forward args, progressing save=%s, camera=%s",
							mNonUIFragment.isSaveInProgress(),
							mNonUIFragment.isCameraInProgress()));

			mNonUIFragment.setArguments(getArguments());
		}
		
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			mActivity = (AbstractGiftAddingActivity) getActivity();
		} catch (ClassCastException e) {
			Log.e(LOG_TAG,
					"onAttach: what is this activity? " + activity.getClass());
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog d = super.onCreateDialog(savedInstanceState);
		d.setTitle(getResources().getString(R.string.add_gift_dialog_title));
		return d;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		final LinearLayout layout = (LinearLayout) mActivity
				.getLayoutInflater().inflate(R.layout.new_gift, container);

		initViews(layout);

		Animator animator = AnimatorInflater.loadAnimator(mActivity, R.animator.rotate_left_in);
		animator.setTarget(layout);
		animator.start();
		
		return layout;
	}

	protected void initViews(LinearLayout layout) {
		mLinkChainChk = (CheckBox) layout.findViewById(R.id.link_chain_chk);
		mThumbVw = (ImageView) layout.findViewById(R.id.thumb_view);
		mImageVw = (TextView) layout.findViewById(R.id.add_image_view);

		// add click listening to the image for starting a file chooser
		mImageVw.setOnClickListener(this);

		// already a media file means a drawable can be made to display, but
		// needs
		// the view to be laid out first
		if (mNonUIFragment.getCameraFile() != null) {
			mImageVw.addOnLayoutChangeListener(this);
		}

		mEditTitle = (EditText) layout.findViewById(R.id.edit_title);
		mEditAddedText = (EditText) layout.findViewById(R.id.added_text);

		mFabBtn = (ImageButton) layout.findViewById(R.id.fabbutton);
		mCancelBtn = (Button) layout.findViewById(R.id.cancel_btn);
		mSaveBtn = (Button) layout.findViewById(R.id.save_btn);

		if (haveCameraAndSdCard()) {
			mFabBtn.setOnClickListener(this);
		} else {
			Log.i(LOG_TAG,
					"onCreateView: either no camera or sdCard, hiding camera fab");
			mFabBtn.setVisibility(View.GONE);
		}
		mCancelBtn.setOnClickListener(this);
		mSaveBtn.setOnClickListener(this);

		DisplayMetrics metrics = getResources().getDisplayMetrics();
		boolean isLandscape = metrics.heightPixels < metrics.widthPixels;
		boolean isPortrait = !isLandscape;
		mIsHideThumb = isPortrait && getResources().getBoolean(R.bool.small_height);
		
		// small enough to hide the thumb, also best shrink the added text
		if (mIsHideThumb) {
			mEditAddedText.setLines(2);
		}
		
		// what did we get from the args (could be either standalone or linked
		// in chain)
		if (mNonUIFragment.isLinked()) {
			mLinkChainChk.setChecked(true);
			mLinkChainChk.setText(getResources().getString(
					R.string.link_chain_to, mNonUIFragment.getLinkToTitle()));
			mLinkChainChk.setOnCheckedChangeListener(this);

			// small screen portrait, it's too cramped for the optional thumb
			if (mIsHideThumb) {
				mThumbVw.setVisibility(View.GONE);
			}
			else if (mNonUIFragment.getLinkToBitmap() != null) {
				mThumbVw.setImageBitmap(mNonUIFragment.getLinkToBitmap());
			}
		} else {
			// not linked
			mLinkChainChk.setVisibility(View.GONE);

			// landscape needs the extra space for the buttons, but portrait it
			// looks weird
			mThumbVw.setVisibility(isLandscape ? View.INVISIBLE : View.GONE);
		}

		mProgressBar = (ProgressBar) layout.findViewById(R.id.progress_bar);
				
	}

	/**
	 * Camera needs both
	 * 
	 * @return
	 */
	private boolean haveCameraAndSdCard() {

		PackageManager pm = mActivity.getPackageManager();

		// Must have a targetSdk >= 9 defined in the AndroidManifest
		boolean frontCam = pm
				.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
		boolean rearCam = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
		boolean sdCard = Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED);

		Log.d(LOG_TAG, String.format(
				"haveCameraAndSdCard: cameras front=%s, back=%s, sdcard=%s",
				frontCam, rearCam, sdCard));

		return (frontCam || rearCam) && sdCard;
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

		if (!mIsHideThumb) {
			// only one checkbox here
			if (isChecked) {
				mThumbVw.setVisibility(View.VISIBLE);
			} else {
				mThumbVw.setVisibility(View.INVISIBLE);
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(LOG_TAG, "onActivityResult: result returned in progress, camera="
				+ mNonUIFragment.isCameraInProgress() + " chooser="
				+ mNonUIFragment.isChooserInProgress());

		if (mNonUIFragment.isCameraInProgress()) {
			// reset the onClick() flag
			mNonUIFragment.setCameraInProgress(false);

			if (resultCode == Activity.RESULT_OK) {
				Log.d(LOG_TAG, "onActivityResult: mediaFile exists="
						+ mNonUIFragment.getCameraFile().exists());

				// run the task to load up the file
				if (mNonUIFragment.getCameraFile().exists()) {
					try {
						mNonUIFragment.loadImage(mImageVw);
					} catch (IllegalArgumentException e) {
						// the view had 0 dimensions, must be because its
						// recreating from config change
						// thanks to the camera, log it, wait till the first
						// opportunity
						// to start it off again probably in onCreateView()
						Log.d(LOG_TAG,
								"onActivityResult: failed to start loadImage()... will retry when view created");
					}
				} else {
					Toast.makeText(mActivity,
							getResources().getString(R.string.camera_error),
							Toast.LENGTH_LONG).show();
				}
			} else if (resultCode == Activity.RESULT_CANCELED) {
				Log.d(LOG_TAG,
						"onActivityResult: user cancelled camera : mediaFile exists="
								+ mNonUIFragment.getCameraFile().exists());
				mNonUIFragment.setCameraFile(null);
				mImageVw.setBackgroundResource(R.drawable.new_gift_image_backgrd);
				mImageVw.setHint(R.string.add_image_hint);
			} else {
				Log.w(LOG_TAG,
						"onActivityResult: unknown error from camera, mediaFile exists="
								+ mNonUIFragment.getCameraFile().exists());
				Toast.makeText(mActivity,
						getResources().getString(R.string.camera_error),
						Toast.LENGTH_LONG).show();
				mNonUIFragment.setCameraFile(null);
			}
		} else if (mNonUIFragment.isChooserInProgress()) {
			// reset the onClick() flag
			mNonUIFragment.setChooserInProgress(false);

			if (resultCode == Activity.RESULT_OK) {

				Uri uri = data.getData();
				if (uri == null) {
					Log.e(LOG_TAG,
							"onActivityResult: got a result, but uri no good="
									+ uri);
				} else {
					Log.d(LOG_TAG, "URI = " + uri);

					mNonUIFragment.loadImage(mImageVw, uri,
							mActivity.getContentResolver());
				}
			} else if (resultCode == Activity.RESULT_CANCELED) {

				Log.d(LOG_TAG, "onActivityResult: user cancelled chooser");
				mImageVw.setBackgroundResource(R.drawable.new_gift_image_backgrd);
				mImageVw.setHint(R.string.add_image_hint);
			}
		}
	}



	/**
	 * Either save, cancel or FAB take a picture
	 */
	@Override
	public void onClick(View v) {
		// check a button is not already in progress... perhaps on real device
		// the
		// response will be fast enough to not need this, on the emulator it
		// takes an
		// age to start the camera. So, don't want to accept more input yet
		if (mNonUIFragment.isCameraInProgress()) {
			Toast.makeText(mActivity,
					getResources().getString(R.string.camera_be_patient),
					Toast.LENGTH_SHORT).show();
			return;
		} else if (mNonUIFragment.isChooserInProgress()) {
			Toast.makeText(mActivity,
					getResources().getString(R.string.chooser_be_patient),
					Toast.LENGTH_SHORT).show();
			return;
		} else if (mNonUIFragment.isSaveInProgress()) {
			Toast.makeText(mActivity,
					getResources().getString(R.string.save_be_patient),
					Toast.LENGTH_SHORT).show();
			return;
		}

		// cancel, remove temp file and leave
		if (v.equals(mCancelBtn)) {

			animateOutAndExit();
			
			mNonUIFragment.cleanup(false); // cancel
		}

		else if (v.equals(mSaveBtn)) {
			// validate input and save

			boolean isValid = 
					// must have a title
					!TextUtils.isEmpty(mEditTitle.getText())
					// and either a camera file
					&& ((mNonUIFragment.getCameraFile() != null
							&& mNonUIFragment.getCameraFile().exists())
						// or a contentUri
						|| mNonUIFragment.getChosenUri() != null);
					
			if (!isValid) {
				Toast.makeText(mActivity,
						getResources().getString(R.string.add_gift_invalid),
						Toast.LENGTH_SHORT).show();

				return;
			}

			// have to be online
			if (!AbstractPotlatchActivity.checkOnlineFeedbackIfNot(mActivity)) {
				return;
			}

			PotlatchApplication app = (PotlatchApplication) mActivity
					.getApplication();
			User user = app.getCurrentUser();

			// have to be signed in (should be, unless oath token has failed in
			// the meantime)
			if (user == null || !app.isUserSignedIn()) {
				Log.d(LOG_TAG, "onClick.save: user not signed in, user=" + user);
				Toast.makeText(
						mActivity,
						getResources().getString(
								R.string.user_not_signed_in_error),
						Toast.LENGTH_SHORT).show();
				return;
			}

			// don't allow other other actions, save should result in leaving
			// the activity
			// but if there's an error it will be handled in this dialog
			mNonUIFragment.setSaveInProgress(true);

			// hook up to get binder calls
			((PotlatchApplication) mActivity.getApplication())
					.getBinderHandler().setRecipient(this);

			// upload the gift
			Gift gift = new Gift(mEditTitle.getText().toString(),
					mEditAddedText.getText().toString(),
					mLinkChainChk.isChecked() ? mNonUIFragment
							.getLinkToGiftId() : -1L, user.getUsername());

			Log.d(LOG_TAG,
					"onClick: saving new gift, chainId = "
							+ gift.getGiftChainId());

			// show a progress bar
			mProgressBar.setVisibility(View.VISIBLE);

			try {
				mNonUIFragment.getDownloadBinder().addGift(gift,
						mNonUIFragment.getCameraFile(), mNonUIFragment.getChosenUri());
			} catch (AuthTokenStaleException e) {
				// abort the current task, handleBinderMessages() will deal with
				// it asap
			}

		} else if (v.equals(mFabBtn)) {

			// take a picture
			Uri fileUri = null;
			try {
				fileUri = getTempCameraMediaFile();
			} 
			catch (ExternalStorageException e) {
				Log.e(LOG_TAG, "onClick.camera: errored with ", e);
				Toast.makeText(mActivity, getResources().getString(R.string.sdcard_error_no_dir),
						Toast.LENGTH_SHORT).show();
				return;
			}

			if (fileUri != null) {
				// don't allow other actions until camera returns
				mNonUIFragment.setCameraInProgress(true);

				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
				startActivityForResult(intent,
						CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
			} else {
				Log.w(LOG_TAG,
						"onClick.camera: no file to save to, aborting piccie");
			}
		}
		else if (v.equals(mImageVw)) {
			Log.d(LOG_TAG, "onClick.chooser: ");

			// make sure the directory is available
			try {
				ImageDownloadBinderDelegate.getMediaStorageDir();
			} 
			catch (ImageFailedToGoException e) {
				Log.e(LOG_TAG, "onClick.camera: errored with ", e);
				Toast.makeText(mActivity, getResources().getString(R.string.sdcard_error_no_dir),
						Toast.LENGTH_SHORT).show();
				return;
			}

			// don't allow other actions until camera returns
			mNonUIFragment.setChooserInProgress(true);

			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(
					Intent.createChooser(intent,
							getResources().getString(R.string.add_image_hint)),
							CHOOSE_IMAGE_ACTIVITY_REQUEST_CODE);
		}
	}

	protected void animateOutAndExit() {
		Animator animator = AnimatorInflater.loadAnimator(mActivity, R.animator.rotate_left_out);
		animator.setTarget(getView());
		animator.start();
		animator.addListener(new AnimatorListener() {

			@Override
			public void onAnimationCancel(Animator animation) {
				dismissAllowingStateLoss();
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				dismissAllowingStateLoss();
			}

			@Override
			public void onAnimationRepeat(Animator animation) {}
			@Override
			public void onAnimationStart(Animator animation) {}
		});
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		
		super.onDismiss(dialog);

		if (mActivity instanceof GiftsActivity) {
			Log.d(LOG_TAG, "onDismiss: calling gifts activity to refresh");
			((GiftsActivity) mActivity)
					.refreshDataOnResumeOrDialogDismissed(true); // is on
																	// dismiss
		}
	}

	/**
	 * When upload button (save) is pressed, the dialog registers for binder
	 * messages so it can receive the results. When save completes (success or
	 * failure) it de-registers and registers the activity again.
	 */
	@Override
	public void handleBinderMessage(int what, boolean onTime,
			GiftsSectionType giftsSectionType) {

		// hand back the binder messages to the activity
		((PotlatchApplication) mActivity.getApplication()).getBinderHandler()
				.setRecipient(mActivity);

		switch (what) {

		case DownloadBinder.GIFT_ADDED:
			// GIFT_ADDED : following press save, the gift was added on the
			// server

			Log.d(LOG_TAG, "handleBinderMessage: successful upload");

			// reset the non-ui fragment
			mNonUIFragment.cleanup(true); // on save

			// feedback success
			Toast.makeText(mActivity,
					getResources().getString(R.string.add_gift_success),
					Toast.LENGTH_LONG).show();

			// and leave
			animateOutAndExit();
			
			break;

		case DownloadBinder.GIFT_ADD_IMAGE_FAILED:
			// GIFT_ADD_IMAGE_FAILED feedback failure
			// lots of logging around this error

			Log.w(LOG_TAG,
					"handleBinderMessage: gift not added because of image errors");

			Toast.makeText(mActivity,
					getResources().getString(R.string.add_gift_image_error),
					Toast.LENGTH_LONG).show();

			mNonUIFragment.clearProgressOnly();
			
			break;

		case DownloadBinder.GIFT_ADD_FAILED:
			// GIFT_ADD_FAILED : feed back to user, not much can do

			Log.w(LOG_TAG, "handleBinderMessage: gift not added");

			// feedback failure (if didn't already)
			if (what == DownloadBinder.GIFT_ADD_FAILED) {
				Toast.makeText(
						mActivity,
						getResources().getString(
								R.string.add_gift_error,
								mNonUIFragment.getDownloadBinder()
										.getCurrentError()), Toast.LENGTH_LONG)
						.show();
			}

			mNonUIFragment.clearProgressOnly();
			
			break;

		default:
			Log.d(LOG_TAG,
					"handleBinderMessage: not handled here, passing to activity");
			mActivity.handleBinderMessage(what, onTime, giftsSectionType);
		}
	}

	private Uri getTempCameraMediaFile() throws ExternalStorageException {

		Resources res = getResources();

		// check some kind of SDCard is mounted
		if (!Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			Log.e(LOG_TAG, "getTempCameraMediaFile: storage state="
					+ Environment.getExternalStorageState());
			throw new ExternalStorageException(
					res.getString(R.string.camera_error_no_sd));
		}

		// Create the storage directory if it does not exist
		File mediaStorageDir;
		try {
			mediaStorageDir = ImageDownloadBinderDelegate.getMediaStorageDir();
		} 
		catch (ImageFailedToGoException e) {
			throw new ExternalStorageException(
					res.getString(R.string.sdcard_error_no_dir));
		}

		// Create a media file, using the same name every time (not saving these
		// locally)
		mNonUIFragment.setCameraFile(new File(mediaStorageDir.getPath()
				+ CAMERA_TEMP_FILE_NAME));

		// there should not be one already, but just in case, remove it
		if (!mNonUIFragment.removeExistingTempFile()) {
			throw new ExternalStorageException(
					res.getString(R.string.camera_error_file_delete));
		}

		return Uri.fromFile(mNonUIFragment.getCameraFile());
	}

	/**
	 * Set on the image view only when there's an image that's needed to be
	 * drawn to it following config change. Once have non-zero values, remove
	 * the listening
	 */
	@Override
	public void onLayoutChange(View v, int left, int top, int right,
			int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

		if (v.equals(mImageVw) && right - left > 0 && bottom - top > 0) {
			mNonUIFragment.loadImage(mImageVw);
			mImageVw.removeOnLayoutChangeListener(this);
		}
	}

}
