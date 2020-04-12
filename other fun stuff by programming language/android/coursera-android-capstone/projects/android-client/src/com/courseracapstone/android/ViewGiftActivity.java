package com.courseracapstone.android;

import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.courseracapstone.android.accountsetup.User;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;
import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PotlatchConstants;

/**
 * Activity started when clicking on a gift list item to view it
 * 
 * @author xxx xxx
 *
 */
public class ViewGiftActivity extends AbstractGiftAddingActivity implements OnCheckedChangeListener {

	// three params are supplied by the caller so that this activity
	// can quickly access the data while a background task gets the 
	// full image to display
	public static final String PARAM_GIFT_ID = "PARAM_GIFT_ID";
	public static final String PARAM_GIFTS_SECTION_TYPE = "PARAM_GIFTS_SECTION_TYPE";
	public static final String PARAM_LIST_POSITION = "PARAM_LIST_POSITION";
	public static final String PARAM_BUNDLE = "PARAM_BUNDLE";

//	public static final String VIEW_NAME_IMAGE = "detail:header:image";

	private final String LOG_TAG = "Potlatch-" + getClass().getSimpleName();

	// params passed, used in onBindToService() to get the gift
	private GiftsSectionType mGiftsSectionType;
	private int mListIdx;
	private long mGiftId;
	
	private Gift mGift;
	private ImageView mImageVw;
	
	// various flags to ensure the proper things happen at the proper times, see
	// handleBinderMessages() for full discussion
	private boolean mHaveRequestedFullScaledImage;
	private boolean mHaveRequestedAnyScaledImage;
	private boolean mHaveSizing;
	private boolean mFullSizeImageIsAvailable;
	private boolean mHaveFullScaledImage;

	private ToggleButton mInappropBtn;
	private ToggleButton mObsceneBtn;
	private TextView mTouchedByVw;
	private TextView mAddedTextVw;
	private TextView mFlaggedByVw;
	private ImageView mMonkeyVw;
	private ToggleButton mHeartBtn;
	private MenuItem mDeleteGiftMenuItem;
	private boolean mIsOwner;
	private ToggleButton mInfoBtn;
	private TextView mInfoVw;
	private Bitmap mThumbBitmap;
	private boolean mHasBound;
	private Bitmap mScaledImage;

    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		

		setContentView(R.layout.view_gift);

		// allow the base view to fill the screen		
		View v = findViewById(R.id.top_layout);
		v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
	            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		
		// this one we want a title
		getActionBar().setDisplayShowTitleEnabled(true);

		Intent i = getIntent();
		mGiftId = i.getLongExtra(PARAM_GIFT_ID, -1);
		String giftsSectionStr = i.getStringExtra(PARAM_GIFTS_SECTION_TYPE);
		mListIdx = i.getIntExtra(PARAM_LIST_POSITION, -1);
		
		// validate the input
		if (mGiftId == -1 || mListIdx == -1 || giftsSectionStr == null || GiftsSectionType.valueOf(giftsSectionStr) == null) {
			Log.e(LOG_TAG, "onCreate: invalid call missing gift id or list pos or section type");
		}
		
		// should be a valid gift, when the binder is connected the details
		// can be displayed
		mGiftsSectionType = GiftsSectionType.valueOf(giftsSectionStr);
		mImageVw = (ImageView) findViewById(R.id.image_view);
		alignManualFabToView(mImageVw);
		mHeartBtn = (ToggleButton) findViewById(R.id.heart_btn);				
		mInappropBtn = (ToggleButton) findViewById(R.id.inappropriate);				
		mObsceneBtn = (ToggleButton) findViewById(R.id.obscene);				
		mTouchedByVw = (TextView) findViewById(R.id.touched_by);				
		mFlaggedByVw = (TextView) findViewById(R.id.flagged_by);		
		mMonkeyVw = (ImageView) findViewById(R.id.ic_monkey);		
		mAddedTextVw = (TextView) findViewById(R.id.additional_text);	
		mInfoBtn = (ToggleButton) findViewById(R.id.info_btn);

		// small width make info invisible
		if (getResources().getBoolean(R.bool.small_width)) {
			mInfoBtn.setVisibility(View.GONE);
		}
		mInfoVw = (TextView) findViewById(R.id.info);				
		
		// use the thumb supplied (in testing there might not be one, other cases should)
		
		Bundle b = i.getBundleExtra(PARAM_BUNDLE);
		
		if (b != null) {

			mDownloadBinder = (DownloadBinder) b.getBinder(GiftsActivity.PARAM_BINDER);
			mThumbBitmap = mDownloadBinder.getSharedImage();
			if (mThumbBitmap != null) {
				mImageVw.setImageBitmap(mThumbBitmap);
				setImageViewScaleType(mImageVw, mDownloadBinder);
				mImageVw.setAlpha(.5f);
				
				setMissingImageFields(false);
			}
		}
	}
	
	/**
	 * Connected to the service that will provide server interaction
	 * initiated during onStart()
	 */
	@Override
	protected void onBindToService() {
		super.onBindToService();
		
		// called during onStart(), so it's possible already have all the data, and don't want
		// to do it all again
		if (mHasBound) {
			return;
		}
		
		mHasBound = true;
		
		
		// this will return the cached gift (directly if it's the last gift that was viewed) or
		// from the lists cache, and it will do 2 things asynchronously:
		// 1) download the latest updates to the gift
		// 2) download (or if already have it, notify) the full image version of the image
		//
		// several possible outcomes, all messaged separately, see handleBinderMessage()
		
		try {
			mGift = mDownloadBinder.getGiftAndPrepareFullImage(mGiftId, mGiftsSectionType, mListIdx);
		}
		catch (AuthTokenStaleException e) {
			// abort the current task, handleBinderMessages() will deal with it asap
		}
		
		if (mGift != null) {
			displayGiftFields();
		}
		else {
			Log.w(LOG_TAG, "onBindToService: gift not found in cache, but binder will download again anyway");
		}
		
		// as the binder may come in after already have the sizing info (see onWindowFocus())
		// check if need to request scaled images immediately
		
		if (mHaveSizing) {
			requestScaledImages();
		}
	}

	private void displayGiftFields() {
		// unset the listeners first, since this method may fire several times if the user
		// makes subsequent updates, don't want spurious checked changes
		mHeartBtn.setOnCheckedChangeListener(null);
		mInappropBtn.setOnCheckedChangeListener(null);
		mObsceneBtn.setOnCheckedChangeListener(null);
		mInfoBtn.setOnCheckedChangeListener(null);

		getActionBar().setTitle(mGift.getTitle());
		
		Resources res = getResources();
		
		// set the toggle buttons
		mHeartBtn.setChecked(mGift.getUserTouchedBy());
		mInappropBtn.setChecked(mGift.getUserInappropriateFlag());		
		mObsceneBtn.setChecked(mGift.getUserObsceneFlag());

		// make the right string for touches
		int intTouches = (int) mGift.getTouchCount();
		mTouchedByVw.setText(intTouches == 0
				// unfortunately there's a bug with plurals when it's zero :(, so have to do this
				? res.getString(R.string.gift_touched_by_none)
				: res.getQuantityString(R.plurals.gift_touched_by, intTouches, intTouches));

		// populate texts
		mAddedTextVw.setText(mGift.getExtraText());
		mInfoVw.setText(res.getString(R.string.gift_info, mGift.getUserName()));

		// set the flagged text
		// index to the strings is a bit complicated ...
		int whoIdx = -1;
		if (mGift.getInappropriateCount() > 0) {
			
			if (mGift.getObsceneCount() > 0) {
				whoIdx = 2;
			}
			else {
				whoIdx = 0;
			}
		}
		else if (mGift.getObsceneCount() > 0) {
			whoIdx = 1;
		}

		
		if (whoIdx != -1) {
			mFlaggedByVw.setVisibility(View.VISIBLE);
			mMonkeyVw.setVisibility(View.VISIBLE);
			mFlaggedByVw.setText(res.getStringArray(R.array.gift_flagged_by)[whoIdx]);
		}
		else {
			mFlaggedByVw.setVisibility(View.GONE);
			mMonkeyVw.setVisibility(View.GONE);
		}
		
		// allow delete to the user's own gifts or for the admin provided
		// it's an obscene flagged gift
		// owner or admin can delete gifts
		if (mDeleteGiftMenuItem != null) {
			PotlatchApplication app = (PotlatchApplication) getApplication();
			User currentUser = app.getCurrentUser();
			
			mIsOwner = currentUser.getUsername().equals(mGift.getUserName());
			boolean enableDelete = mIsOwner
					|| (currentUser.isAdmin() && mGift.getObsceneCount() > 0);
			
			mDeleteGiftMenuItem.setEnabled(enableDelete);
			
			Log.d(LOG_TAG, String.format("displayGiftFields: delete enabled=%s. gu=%s, cu=%s, admin=%s, obs=%s",
						enableDelete, mGift.getUserName(), currentUser.getUsername(), currentUser.isAdmin(),
						mGift.getObsceneCount()));
			
		}
		else {
			Log.d(LOG_TAG, "displayGiftFields: unable to set delete enabled, it's null");
		}
		
		// set the listeners 
		mHeartBtn.setOnCheckedChangeListener(this);
		mInappropBtn.setOnCheckedChangeListener(this);
		mObsceneBtn.setOnCheckedChangeListener(this);
		mInfoBtn.setOnCheckedChangeListener(this);

		setImageScalingMenuItems();
	}

	/**
	 * Called either because the image is missing or because the image is loaded (not missing)
	 * In both cases the progress bar is hidden
	 * 
	 * @param isMissing
	 */
	private void setMissingImageFields(boolean isMissing) {

		findViewById(R.id.thumb_progress).setVisibility(View.GONE);
		findViewById(R.id.image_missing).setVisibility(isMissing
				? View.VISIBLE : View.INVISIBLE);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if (hasFocus) {
			
			// one time notification, so keep track in case binding happens next
			mHaveSizing = true;

			// bound already
			if (mDownloadBinder != null && !mHaveFullScaledImage) {
				
				// previously already received a full image, and requested a scaled copy
				if (!mHaveRequestedFullScaledImage) {
					
					// one is already available, so request it
					// (see handleBinderMessage() : FULL_IMAGE_READY)
					if (mFullSizeImageIsAvailable) {
						mHaveRequestedFullScaledImage = true;
						requestScaledImages();
					}
					
					// full size image hasn't come in yet, so request any scaled image in
					// the meantime (which will first get a scaled thumb)
					else if (!mHaveRequestedAnyScaledImage) {
						mHaveRequestedAnyScaledImage = true;
						requestScaledImages();
					}
				}
			}
		}
	}

	/**
	 * Called in 3 places:
	 * From onBindToService() if onWindowFocusChanged() has previously been called with focus = true
	 * From onWindowFocusChanged() if onBindToService() has run already and it hasn't already called this
	 * From handleBinderMessage : FULL_IMAGE_READY when have sizing and was just waiting for this 
	 */
	private void requestScaledImages() {
		mHaveRequestedAnyScaledImage = true;
		mDownloadBinder.loadScaledImagesForGift(mGiftId, mImageVw.getWidth(), mImageVw.getHeight());
	}

	@Override
	public void handleBinderMessage(int what, boolean onTime, GiftsSectionType giftsSectionType) {

		// Quite complicated set of messages, see comments to each condition

		switch (what) {
		
		case DownloadBinder.FRESH_GIFT_READY :
			// FRESH_GIFT_READY : when startup the call to binder.getGiftAndPrepareFullImage()
			//					  results in this and also after an update to the toggled fields.
			//					  it would always expect to have a Gift from getGift() unless
			//					  someone removed it at the server between viewing it and 
			//					  the update, then same process as ERROR_NOT_FOUND (drop through case)
			
			mGift = mDownloadBinder.getGift(mGiftId);
			if (mGift != null) {
				displayGiftFields();
				
				break;
			}
			else {
				Log.e(LOG_TAG, "handleBinderMessage: FRESH_GIFT_READY returned no gift!!, fall through to ERROR_NOT_FOUND");
			}
			
		case DownloadBinder.ERROR_NOT_FOUND :
			// ERROR_NOT_FOUND : gift is removed on server, feedback and quit activity, pass
			//					 intent to the server to indicate success by dropping to next 
			//					 case
			
			Toast.makeText(this, getResources().getString(R.string.gift_went_missing), Toast.LENGTH_LONG).show();

		case DownloadBinder.GIFT_REMOVED :
			// GIFT_REMOVED : just got deleted here, fragments will re-query onResume()

			finishAfterTransition();
			break;

//		case DownloadBinder.ERROR_AUTH_TOKEN_STALE:
//			// ERROR_AUTH_TOKEN_STALE : hard to handle this here, output a toast 
//			// 					and let GiftsActivity handle it when it tries to get an update
//			//					server resetting in the middle of an app updating... I guess it could happen :)
//			
//			Toast.makeText(this, getResources().getString(R.string.auth_error_during_update), Toast.LENGTH_LONG).show();
//
//			finishAfterTransition();
//			break;

		case DownloadBinder.FULL_IMAGE_READY : 
			// FULL_IMAGE_READY : request a scaled copy from binder (async again), note: this could come in 
			//					  immediately if either the last viewed gift is this one or
			//					  after config(orientation) change, since the last full image is always cached 
			//					  This event will also cause a call to get a scaled version, (onWindowFocus()
			//					  tries to get that too, because the size of the image view is needed first)
			//					  Both these results cause another call to the binder to get the scaled image
			
			mFullSizeImageIsAvailable = true;
			
			if (!mHaveFullScaledImage 
					&& !mHaveRequestedFullScaledImage 
					&& mHaveSizing) { // presume onWindowFocusChanged() is about to get called!
				
				mHaveRequestedFullScaledImage = true;
				requestScaledImages();
			}

			break;
			
		case DownloadBinder.FULL_IMAGE_NOT_AVAILABLE : 
			// FULL_IMAGE_NOT_AVAILABLE : update fields to show missing image (should only happen in test data
			//					  but I guess if no internet, it would also show that way, as that isn't a quit
			//					  offence like missing gift above) 
			//					  Note: could get this, and then next thing it does come in because the thread
			//					  that downloads the image might be running while the thread that tries to 
			//					  scale images finds it doesn't yet have it to scale, and vice versa. 
			//					  So, do nothing if already have requested the scaled full image.
			
			mImageVw.setAlpha(1f);
			
			if (!mHaveFullScaledImage && !mFullSizeImageIsAvailable) {
				setMissingImageFields(true);
			}
			else {
				Log.d(LOG_TAG, "handleBinderMessage: FULL_IMAGE_NOT_AVAILABLE apparently happened between getting it already");
			}
			
			break;
			
		case DownloadBinder.SCALED_FULL_IMAGE_READY : 
			// SCALED_FULL_IMAGE_READY : the desired end result, display it (sets mHaveFullScaledImage so
			//					  no further actions interfere with it)
			
			mHaveFullScaledImage = true;
			
			// it better be there!
			Bitmap image = mDownloadBinder.getScaledImageForGift(mGiftId);
			setMissingImageFields(image == null);
			if (image != null) {
				mScaledImage = image;
				mImageVw.setImageBitmap(image);
				setImageViewScaleType(mImageVw, mDownloadBinder);

				// animate it to full colour, only if previously was showing the scaled thumb
				// which is half transparent
				if (Float.compare(mImageVw.getAlpha(), 1f) != 0) { 
					ObjectAnimator animator = ObjectAnimator.ofFloat(mImageVw, "alpha", .5f, 1f);
					// leave duration and interpolator to default
					animator.start();
				}
				
			}
			
			break;
			
		case DownloadBinder.SCALED_INTERIM_IMAGE_READY : 
			// SCALED_INTERIM_IMAGE_READY : a scaled image has been made from the thumb, it may or may not come
			//					  in after SCALED_FULL_IMAGE_READY (since they're both async calls happening
			//					  independently, if already have full image then ignore this message

			if (!mHaveFullScaledImage) {
				// it better be there!
				Bitmap interimImage = mDownloadBinder.getScaledImageForGift(mGiftId);
				setMissingImageFields(interimImage == null);
				if (interimImage != null) {
					mScaledImage = interimImage;
					mImageVw.setImageBitmap(interimImage);
					setImageViewScaleType(mImageVw, mDownloadBinder);
					mImageVw.setAlpha(.5f); // make it faded
				}
			}
			
			break;
			
		case DownloadBinder.ERROR_BAD_REQUEST :
			// ERROR_BAD_REQUEST : if the user has already flagged the gift, eg. in another
			// 					session out of sync with this one, then it will not double
			//					count it, this error will be thrown... not much needed
			//					it shows the correct value from the update, just log it
			//					and ignore

			Log.w(LOG_TAG, "handleBinderMessage: user response out of sync with server, is the user trying to cook the books?");
			break;

		default : 
			Log.d(LOG_TAG, "handleBinderMessage: not handled here, passing to superclass");
			super.handleBinderMessage(what, onTime, giftsSectionType);
		}
	}

	@Override
	public void onBackPressed() {
		finishAfterTransition();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.view_gift, menu);
		
		mFitMenuItem = menu.findItem(R.id.image_fit_menu);
		mCropMenuItem = menu.findItem(R.id.image_crop_menu); 
		
		mDeleteGiftMenuItem = menu.findItem(R.id.delete_gift);
		
		setImageScalingMenuItems();

		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		
		if (item.getItemId() == R.id.image_crop_menu) {

			boolean wasChecked = item.isChecked(); 
			if (!wasChecked) {
				item.setChecked(true);
				mDownloadBinder.changeScalingLogic(mImageVw.getWidth(), mImageVw.getHeight(), 
						ScalingLogic.CROP);
			}
			else {
				item.setChecked(false);
			}

			return true;
		}
		else if (item.getItemId() == R.id.image_fit_menu) {

			boolean wasChecked = item.isChecked(); 
			if (!wasChecked) {
				item.setChecked(true);
				mDownloadBinder.changeScalingLogic(mImageVw.getWidth(), mImageVw.getHeight(), 
						ScalingLogic.FIT);
			}
			else {
				item.setChecked(false);
			}
		
			return true;
		}
		else if (item.getItemId() == R.id.delete_gift) {
			
			Resources res = getResources();
			
			new AlertDialog.Builder(this)
				.setTitle(res.getString(R.string.delete_gift_confirm_title))
				.setMessage(res.getString(R.string.delete_gift_confirm))
				.setPositiveButton(res.getString(R.string.delete_button), new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (checkOnlineFeedbackIfNot(ViewGiftActivity.this)) {
								try {
									mDownloadBinder.deleteGift(mGiftId, mIsOwner);
								}
								catch (AuthTokenStaleException e) {
									// abort the current task, handleBinderMessages() will deal with it asap
								}
							}
							else {
								Log.d(LOG_TAG, "onMenuItemSelected: not online, couldn't delete");
							}
						}
					})
				.setNegativeButton(R.string.cancel_button, null)
				.create().show();
			
			return true;
		}
		else if (item.getItemId() == R.id.gift_chain) {
			
			Intent i = new Intent(this, OneChainGiftsActivity.class);
			i.putExtra(ViewGiftActivity.PARAM_GIFT_ID, mGift.getGiftChainId());
			
			ImageButton fabBtn = getFabBtn();
			if (fabBtn != null) {
				i.putExtra(ViewGiftActivity.PARAM_FAB_X, fabBtn.getX());
				i.putExtra(ViewGiftActivity.PARAM_FAB_Y, fabBtn.getY());			
			}

			// if this gift is also the top of the chain can use the image

			if (mGift != null && mGift.getChainTop() && mScaledImage != null) {
				
				// put the image onto the binder for sharing
				Bundle bundle = new Bundle();
				bundle.putBinder(GiftsActivity.PARAM_BINDER, mDownloadBinder);
				mDownloadBinder.putTransitionThumb(mScaledImage);
				i.putExtra(ViewGiftActivity.PARAM_BUNDLE, bundle);
				
				ActivityOptions activityOptions = ActivityOptions.makeThumbnailScaleUpAnimation(
						mImageVw, mScaledImage, (int)mImageVw.getX(), (int)mImageVw.getY());
				
				startActivity(i, activityOptions.toBundle());
			}
			else {
				startActivity(i);
			}
			
			return true;
		}
		else {
			return super.onMenuItemSelected(featureId, item);
		}
	}

	/**
	 * One of the 3 possible updates has been applied, double-count attempts will throw
	 * ERROR_BAD_REQUEST (see handleBinderMessages())
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		
		if (buttonView.equals(mInfoBtn)) {
			mInfoVw.setVisibility(isChecked ? View.VISIBLE : View.GONE);
			
//	reveal isn't working... sigh, another L preview feature lacking perhaps?		
//			if (isChecked) {
//				// do a reveal
//				
//				// get the center for the clipping circle
//				int cx = (mInfoVw.getLeft() + mInfoVw.getRight()) / 2;
//				int cy = (mInfoVw.getTop() + mInfoVw.getBottom()) / 2;
//
//				// get the initial radius for the clipping circle
//				int initialRadius = mInfoVw.getWidth();
//				
//				// create the animation (the final radius is zero)
//				ValueAnimator anim =
//				    ViewAnimationUtils.createCircularReveal(mInfoVw, cx, cy, initialRadius, 0);
//				
//				anim.start();
//			}
		}
		else if (checkOnlineFeedbackIfNot(this)) {
			
			// should only be one of these, but just in case...
			boolean isUpdate = false;
			
			try {
				if (buttonView.equals(mHeartBtn)) {
					Log.d(LOG_TAG, "onCheckedChanged: update touched by = "+isChecked);
					mDownloadBinder.setGiftResponse(mGiftId, 
							PotlatchConstants.GIFT_RESPONSE_TYPE_TOUCHED_BY, isChecked);
					isUpdate = true;
				}
				else if (buttonView.equals(mInappropBtn)) {
					Log.d(LOG_TAG, "onCheckedChanged: update inappropriate = "+isChecked);
					mDownloadBinder.setGiftResponse(mGiftId, 
							PotlatchConstants.GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT, isChecked);
					isUpdate = true;
				}
				else if (buttonView.equals(mObsceneBtn)) {
					Log.d(LOG_TAG, "onCheckedChanged: update obscene = "+isChecked);
					mDownloadBinder.setGiftResponse(mGiftId, 
							PotlatchConstants.GIFT_RESPONSE_TYPE_OBSCENE_CONTENT, isChecked);
					isUpdate = true;
				}

				// ignore multiple attempts if the response is slow from server
				if (isUpdate) {
					mHeartBtn.setOnCheckedChangeListener(null);
					mInappropBtn.setOnCheckedChangeListener(null);
					mObsceneBtn.setOnCheckedChangeListener(null);
				}
			}
			catch (AuthTokenStaleException e) {
				// abort the current task, handleBinderMessages() will deal with it asap
			}
		}
		else {
			// unset the listener to uncheck it, or it will come round again in a loop
			Log.w(LOG_TAG, "onCheckedChanged: not online, so uncheck it");
			buttonView.setOnCheckedChangeListener(null);
			buttonView.setChecked(!isChecked);
			buttonView.setOnCheckedChangeListener(this);
		}
	}

	@Override
	protected void fabButtonPressed() {
		addGift(mGift, mThumbBitmap);
	}

}
