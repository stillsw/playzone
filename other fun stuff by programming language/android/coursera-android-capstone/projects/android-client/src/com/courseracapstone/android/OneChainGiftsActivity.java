package com.courseracapstone.android;

import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.courseracapstone.android.GiftsFragment.GiftListAdapter;
import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.PartialPagedList;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;
import com.courseracapstone.common.GiftsSectionType;

/**
 * A simplified version of GiftsActivity that only shows one Gifts Fragment
 * but a specialized layout that shows the top gift (starter) in the chain
 * as an un-moving header.
 * 
 * TODO There's a few methods here very similar to ViewGiftActivity for using a gift
 * image as a header. Not exactly the same, but enough that a refactor would be 
 * a good idea.
 * 
 * @author xxx xxx
 *
 */
public class OneChainGiftsActivity extends AbstractGiftsListActivity {

	private final String LOG_TAG = "Potlatch-" + getClass().getSimpleName();
	private static final String ONE_CHAIN_FRAGMENT_TAG = "one chain fragment";

	// passed as param
	private long mGiftChainId;

	private Gift mChainTopGift;

	// various flags to ensure the proper things happen at the proper times, see
	// handleBinderMessages() for full discussion
	private boolean mHaveRequestedFullScaledImage;
	private boolean mHaveRequestedAnyScaledImage;
	private boolean mHaveSizing;
	private boolean mFullSizeImageIsAvailable;
	private boolean mHaveFullScaledImage;

	private Bitmap mThumbBitmap;
	private Bitmap mScaledImage;
	private ImageView mImageVw;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// override the normal behaviour to always treat onBind (ie. start)
		// as a new search point, (and the search always contains gift chain id)
		mOnBindSearchBehaviourIsNewSearch = true;
		
		setContentView(R.layout.activity_one_chain_gifts);

		View v = findViewById(R.id.top_layout);
		v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
	            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		
		// this one we want a title
		getActionBar().setDisplayShowTitleEnabled(true);

		Intent i = getIntent();
		mGiftChainId = i.getLongExtra(ViewGiftActivity.PARAM_GIFT_ID, -1);
		
		// validate the input
		if (mGiftChainId == -1 ) {
			Log.e(LOG_TAG, "onCreate: invalid call missing chain gift id");
			finish();
		}
		
		mGiftsFramentComposite = OneChainGiftsFragment.newInstance(mGiftChainId); 
		
		FragmentManager fragmentManager = getFragmentManager();
		fragmentManager
			.beginTransaction()
			.replace(R.id.fragment_container, (Fragment) mGiftsFramentComposite, ONE_CHAIN_FRAGMENT_TAG)
			.commit();
		
		// get the views from the layout
		mImageVw = (ImageView) findViewById(R.id.image_view);
		alignManualFabToView(mImageVw);
		
		// if the view clicked on is also the chain top already, the thumb for
		// it is passed in the binder
		// use the thumb supplied (in testing there might not be one, other cases should)
		
		Bundle b = i.getBundleExtra(ViewGiftActivity.PARAM_BUNDLE);
		if (b != null) {
			// don't keep the binder, will get one fair-and-square from onBind...
			DownloadBinder binder = (DownloadBinder) b.getBinder(GiftsActivity.PARAM_BINDER);
			mThumbBitmap = binder.getSharedImage();
			if (mThumbBitmap != null) {
				mImageVw.setImageBitmap(mThumbBitmap);
				setImageViewScaleType(mImageVw, binder);
				mImageVw.setAlpha(.5f);
				
				setMissingImageFields(false);
			}
		}
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
	 * The first row in the new list is the header if it's a chain top, but it could also
	 * be missing (either deleted or filtered out)
	 * @param chainTopGift
	 */
	private void setupHeader(Gift chainTopGift) {
		
		// is the first row in the list the correct header?
		if (chainTopGift != null && !chainTopGift.getChainTop()) {
			chainTopGift = null;
		}

		// the only way this call will return the top gift is if the last viewed gift is
		// also the one that's at the top of the chain
		try {
			mChainTopGift = mDownloadBinder.getGiftAndPrepareFullImage(mGiftChainId, 
					GiftsSectionType.ONE_CHAIN, // not actually used in this case, but use correct value for completeness :) 
					-1); // invalid index will prevent lookup of the list, it's not needed because already established not there

		}
		catch (AuthTokenStaleException e) {
			// abort the current task, handleBinderMessages() will deal with it asap
		}
		
		if (mChainTopGift != null) {
			displayGiftFields();
		}
		else {
			Log.d(LOG_TAG, "setupHeader: chain gift not found in cache, but binder will attempt to download it anyway");
			
			// start off with a not visible message in the title, if it's found to be actually deleted
			// that will be changed
			getActionBar().setTitle(R.string.one_chain_title_not_visible);
		}
		
		// as the binder may come in after already have the sizing info (see onWindowFocus())
		// check if need to request scaled images immediately
		
		if (mHaveSizing) {
			requestScaledImages();
		}
	}

	/**
	 * Called in 3 places:
	 * From setupHeader() if onWindowFocusChanged() has previously been called with focus = true
	 * From onWindowFocusChanged() if setupHeader() has run already and it hasn't already called this
	 * From handleBinderMessage : FULL_IMAGE_READY when have sizing and was just waiting for this 
	 */
	private void requestScaledImages() {
		mHaveRequestedAnyScaledImage = true;
		mDownloadBinder.loadScaledImagesForGift(mGiftChainId, mImageVw.getWidth(), mImageVw.getHeight());
	}

	/**
	 * Test for filtered content
	 */
	private void displayGiftFields() {
		
		if (isTopGiftFilteredOutOrRemoved()) {
			
			getActionBar().setTitle(getResources().getString(R.string.one_chain_title_not_visible));
			mImageVw.setImageBitmap(null);
			
			setMissingImageFields(false);
		}
		else {
			getActionBar().setTitle(getResources().getString(R.string.one_chain_title, mChainTopGift.getTitle()));
			setImageScalingMenuItems();
			
			mImageVw.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Intent i = new Intent(OneChainGiftsActivity.this, ViewGiftActivity.class);
					i.putExtra(ViewGiftActivity.PARAM_GIFT_ID, mGiftChainId);
					i.putExtra(ViewGiftActivity.PARAM_GIFTS_SECTION_TYPE, GiftsSectionType.ONE_CHAIN.name());
					i.putExtra(ViewGiftActivity.PARAM_LIST_POSITION, 0); // not sure what happens if the list advances
																		 // I guess ViewGiftActivity will get the proper value
																		 // when it actually looks up the gift anyway
					
					ImageButton fabBtn = getFabBtn();
					if (fabBtn != null) {
						i.putExtra(ViewGiftActivity.PARAM_FAB_X, fabBtn.getX());
						i.putExtra(ViewGiftActivity.PARAM_FAB_Y, fabBtn.getY());			
					}

					// the image is way too big for the thumbnail
					startActivity(i);
				}
			});
		}
	}

	protected boolean isTopGiftFilteredOutOrRemoved() {
		return mChainTopGift == null
			|| (mDownloadBinder.isUserFilterContent() 
				&& (mChainTopGift != null 
					&& (mChainTopGift.getUserInappropriateFlag() 
							|| mChainTopGift.getUserObsceneFlag())));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.one_chain_gifts, menu);

		mFitMenuItem = menu.findItem(R.id.image_fit_menu);
		mCropMenuItem = menu.findItem(R.id.image_crop_menu); 
		
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
		else {
			return super.onMenuItemSelected(featureId, item);
		}
	}

	/**
	 * Handling search here is quite different, new or refresh it only acts
	 * on the one gift section type, and leaves other data alone. 
	 */
	@Override
	protected void handleRefresh(boolean isNewSearch) {
		
		try {
			if (isNewSearch) { 
				Log.d(LOG_TAG, "refreshData: for a new search");
	
				mDownloadBinder.resetGifts(mGiftsFramentComposite.getSearch()
						, GiftsSectionType.ONE_CHAIN);
			}
			else {
				Log.d(LOG_TAG, "refreshData: not new search");
	
				mDownloadBinder.refreshOrNewGifts(mGiftsFramentComposite.getSearch(), GiftsSectionType.ONE_CHAIN);
			}
		}
		catch (AuthTokenStaleException e) {
			// abort the current task, handleBinderMessages() will deal with it asap
		}
	}

	/**
	 * Very similar to the same method in ViewGiftActivity... needs a refactor
	 * @param what
	 * @param onTime
	 * @param giftsSectionType
	 */
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
			
			mChainTopGift = mDownloadBinder.getGift(mGiftChainId);
			if (mChainTopGift != null) {
				displayGiftFields();
				
				break;
			}
			else {
				Log.e(LOG_TAG, "handleBinderMessage: FRESH_GIFT_READY returned no gift!!, fall through to ERROR_NOT_FOUND");
			}
			
		case DownloadBinder.ERROR_NOT_FOUND :
			// ERROR_NOT_FOUND : gift is removed on server, fall through to set title
		case DownloadBinder.GIFT_REMOVED :
			// GIFT_REMOVED : just got deleted here, if still have an image for it just keep that, but set title

			getActionBar().setTitle(R.string.one_chain_title_removed);
			
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
				
				// provided user has not filtered the content
				if (!isTopGiftFilteredOutOrRemoved()) {
					mHaveRequestedFullScaledImage = true;
					requestScaledImages();
				}
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

			// don't show filtered content
			if (!isTopGiftFilteredOutOrRemoved()) {
				// it better be there!
				Bitmap image = mDownloadBinder.getScaledImageForGift(mGiftChainId);
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
			}
			
			break;
			
		case DownloadBinder.SCALED_INTERIM_IMAGE_READY : 
			// SCALED_INTERIM_IMAGE_READY : a scaled image has been made from the thumb, it may or may not come
			//					  in after SCALED_FULL_IMAGE_READY (since they're both async calls happening
			//					  independently, if already have full image then ignore this message

			// don't show filtered content
			if (!isTopGiftFilteredOutOrRemoved()) {
				if (!mHaveFullScaledImage) {
					// it better be there!
					Bitmap interimImage = mDownloadBinder.getScaledImageForGift(mGiftChainId);
					setMissingImageFields(interimImage == null);
					if (interimImage != null) {
						mScaledImage = interimImage;
						mImageVw.setImageBitmap(interimImage);
						setImageViewScaleType(mImageVw, mDownloadBinder);
						mImageVw.setAlpha(.5f); // make it faded
					}
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
	protected void fabButtonPressed() {
		if (mChainTopGift != null) {
			addGift(mChainTopGift, mScaledImage);
		}
		else {
			// whatever is already set on the title bar is good as the title 
			addGift(mGiftChainId, (String)getActionBar().getTitle(), mScaledImage); 
		}
	}

	@Override
	GiftListAdapter getListAdapter(GiftsFragment giftsFragment, PartialPagedList<Gift> partialPagedList, 
			GiftsSectionType giftsSectionType, TextView emptyLabel) {
		
		// examine the first row, check to see it is the top of the chain
		setupHeader(partialPagedList.getRow(0));
		
		return giftsFragment.new OneChainGiftListAdapter(partialPagedList, emptyLabel);
	}


}
