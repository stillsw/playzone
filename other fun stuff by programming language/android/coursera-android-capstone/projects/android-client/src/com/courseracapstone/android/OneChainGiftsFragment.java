package com.courseracapstone.android;

import java.util.Comparator;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringAndIdSearchCriteria;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringSearchCriteria;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.common.GiftsSectionType;

/**
 * Specialization of GiftsFragment that implements the composite interface so 
 * it acts as its own proxy.
 * 
 * @author xxx xxx
 * 
 */
public class OneChainGiftsFragment extends GiftsFragment implements GiftsFragmentComposite {

	private static final String LOG_TAG = "Potlatch-"+OneChainGiftsFragment.class.getSimpleName();


	public static OneChainGiftsFragment newInstance(long giftChainId) {
		Log.d(LOG_TAG, "create fragment for "+giftChainId);

		Bundle bundle = new Bundle();
		bundle.putLong(ViewGiftActivity.PARAM_GIFT_ID, giftChainId);
		bundle.putString(KEY_GIFTS_SECTION_NAME, GiftsSectionType.ONE_CHAIN.name());
		bundle.putInt(KEY_INDICATOR_COLOR, Color.GREEN);// used for empty label 
														// same as used for all chains tabs

		OneChainGiftsFragment fragment = new OneChainGiftsFragment();
		fragment.setArguments(bundle);

		return fragment;
	}


	private boolean mIsInternetAvailable;
	private OneChainGiftSearchCriteria mSearchCriteria;
	private long mGiftChainId;

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		Bundle args = getArguments();
		mGiftChainId = args.getLong(ViewGiftActivity.PARAM_GIFT_ID, -1L);
		mSearchCriteria = new OneChainGiftSearchCriteria(mGiftChainId);
	}

	//------------------------ GiftsFragmentComposite methods

	@Override
	public boolean isInternetAvailable() {
		return mIsInternetAvailable;
	}

	/**
	 * Gotcha on this one, it's the same method signature on GiftsFragment and the 
	 * composite (refactoring accident). The composite pattern intention is to keep
	 * the value and also then to pass it to child fragments. So here, keep the value,
	 * and as this is also its own child fragment, call the superclass method too.
	 */
	@Override
	public void setInternetAvailable(boolean isAvailable, boolean refreshGiftsNow) {
		mIsInternetAvailable = isAvailable;
		super.setInternetAvailable(isAvailable, refreshGiftsNow);
	}

	@Override
	public void setSearchString(String query) {} // n/a


	@Override
	public SimpleStringSearchCriteria<Gift> getSearch() {
		return mSearchCriteria;
	}


	@Override
	public String getSearchString() {
		return mSearchCriteria.getSearchString();
	}

	/**
	 * Since it's a composite pattern, here it's the simple case, only one object and this is it.
	 * So calls superclass method rather than a bunch of child fragments
	 */
	@Override
	public void setDownloadBinder(DownloadBinder downloadBinder, boolean refreshGiftsNow) {
		setDownloadBinder(downloadBinder, mIsInternetAvailable, refreshGiftsNow);
	}


	@Override
	public GiftsFragment getGiftsFragment(GiftsSectionType giftsType) {
		return this;
	}

	/**
	 * Sorting at the client end is usually natural, but for one_chain type
	 * it's different to the others, provide a comparator for only that type
	 * 
	 * @return
	 */
	public class GiftComparator implements Comparator<Gift> {

		@Override
		public int compare(Gift first, Gift second) {
			//return (int) (other.id - id);
			int chainTopCompare = (int) (first.getForceOrderForChainTop() - second.getForceOrderForChainTop());
			if (chainTopCompare == 0) {
				return (int) (second.getId() - first.getId());
			}
			else {
				return chainTopCompare;
			}
		}
	}
	
	/**
	 * Specialized search criteria for one chain, uses the comparator
	 */
	public class OneChainGiftSearchCriteria extends SimpleStringAndIdSearchCriteria<Gift> {

		private GiftComparator mGiftComparator = new GiftComparator();
		
		public OneChainGiftSearchCriteria(long giftChainId) {
			super(null, giftChainId);
		}
		
		@Override
		public Comparator<Gift> getComparator() {
			return mGiftComparator;
		}
	}
}
