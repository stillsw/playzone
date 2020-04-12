package com.courseracapstone.android;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.Toast;

import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;

/**
 * Handles the ability to launch a dialog from a fab button which will
 * add a new gift via an AddGiftDialogFragment. To protect against config
 * changes it also manages the creation of a non-ui fragment for it, which
 * is then used to determine if the dialog is retaining control.
 * 
 * @author xxx xxx
 *
 */
public abstract class AbstractGiftAddingActivity extends AbstractPotlatchActivity {

	private static final String LOG_TAG = "Potlatch-"+ AbstractGiftAddingActivity.class.getSimpleName();

	// at least a couple of sub classes use these in their menu 
	// (ViewGiftActivity & OneChainGiftsActivity)
	protected MenuItem mCropMenuItem, mFitMenuItem;

	/**
	 * Instantiate the non-ui data holder for adding gifts, it could already be there
	 * after config change
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		FragmentManager fm = getFragmentManager();
		FragmentTransaction ft1 = fm.beginTransaction();
		
		// create the non-ui data fragment if needed
		NonUIAddGiftDataFragment nonUiFrag = (NonUIAddGiftDataFragment) fm
				.findFragmentByTag(NonUIAddGiftDataFragment.NON_UI_ADD_FRAGMENT_TAG);

		if (nonUiFrag == null) {
			Log.d(LOG_TAG, "onCreate: no non-ui fragment found, making one");

			// create a non-ui holder for ui fragment data
			nonUiFrag = new NonUIAddGiftDataFragment();

			ft1.add(nonUiFrag, NonUIAddGiftDataFragment.NON_UI_ADD_FRAGMENT_TAG);
			ft1.commit();

			fm.executePendingTransactions();
		}
		else {
			Log.d(LOG_TAG, "onCreate: non-ui fragment already there, must be config change");
		}
			
	}

	/**
	 * Seems necessary to get the image properly filling the view when first load that page.
	 * The scaled size should be correct, so not sure why get start/end margins without doing this.
	 * @param imageVw
	 * @param binder 
	 */
	protected void setImageViewScaleType(ImageView imageVw, DownloadBinder binder) {
		imageVw.setScaleType(
				ScalingLogic.CROP == binder.getScalingLogic()
					? ScaleType.FIT_XY : ScaleType.CENTER);
		imageVw.invalidate();
	}
	
	/**
	 * Waits for all necessary items before trying to set the menu items
	 */
	protected void setImageScalingMenuItems() {
		if (mFitMenuItem == null || mCropMenuItem == null || mDownloadBinder == null) {
			return;
		}
		else if (!mFitMenuItem.isChecked() && !mCropMenuItem.isChecked()) {

			// set the correct menu item checked, only if neither is already
			ScalingLogic currentLogic = mDownloadBinder.getScalingLogic();
			if (currentLogic == null) {
				currentLogic = ScalingLogic.CROP;
			}

			if (currentLogic == ScalingLogic.CROP) {
				mCropMenuItem.setChecked(true);
			}
			else {
				mFitMenuItem.setChecked(true);
			}
		}
	}

	/**
	 * Called on click for fab add gift button (used in both giftsActivty & viewGiftActivity)
	 */
	void addGift(Gift chainToGift, Bitmap transitionImage) {
		addGift(
				chainToGift != null ? chainToGift.getGiftChainId() : -1L,
				chainToGift != null ? chainToGift.getTitle() : "",
				transitionImage);
	}

	/**
	 * Called on click for fab add gift button but have no gift, only an id for the chain
	 * (used in OneChainGiftsActivity)
	 */
	void addGift(long giftChainId, String giftChainTitle, Bitmap transitionImage) {
		
		// the network was available, but could have
		// been lost in the meantime
		
		if (isNetworkOnlineOtherwiseRegisterForBroadcast()) {

			// put the image here as the link image via the binder, if null it's ok, it'll just
			// reset anything there (it should've been cleaned up anyway)
			mDownloadBinder.putTransitionThumb(transitionImage);
			
			AddGiftDialogFragment frag = giftChainId == -1L
					? AddGiftDialogFragment.newInstance(mDownloadBinder)
					: AddGiftDialogFragment.newInstance(mDownloadBinder, giftChainId, giftChainTitle);

			
			FragmentManager fm = getFragmentManager();

			// create the ui fragment in a backstack transaction
			Fragment prev = fm.findFragmentByTag(AbstractPotlatchActivity.DIALOG_FRAGMENT_TAG);
			FragmentTransaction ft2 = fm.beginTransaction();

			// replace any occurrence
			if (prev != null) {
				ft2.remove(prev);
			}
			
			ft2.addToBackStack(null);
			
			frag.show(ft2, AbstractPotlatchActivity.DIALOG_FRAGMENT_TAG);
		}
		else {
			// show a toast that the action can't be done now
			Toast.makeText(this, getResources().getText(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Detects the add dialog save in progress
	 */
	@Override
	protected final boolean bindToHandler() {

		FragmentManager fm = getFragmentManager();
		NonUIAddGiftDataFragment dialogFrag = 
				(NonUIAddGiftDataFragment) fm.findFragmentByTag(
						NonUIAddGiftDataFragment.NON_UI_ADD_FRAGMENT_TAG);

		// there should always be one, since it's instantiated in onCreate()
		if (dialogFrag == null) {
			Log.w(LOG_TAG, "bindToHandler: dialogFrag test, none found");			
			return true;
		}
		
		// found one so check what it's up to
		boolean isInProgress = dialogFrag.isWorkInProgress();

		Log.d(LOG_TAG, String.format("bindToHandler: AddGiftDialogFragment current in progress=%s",
				isInProgress));
		
		return !isInProgress;

	}

}
