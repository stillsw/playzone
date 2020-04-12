package com.courseracapstone.android;

import java.util.HashMap;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;

import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringSearchCriteria;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.common.GiftsSectionType;

/**
 * Keeps the gifts data references and the adapters for the instances
 * of the ui fragments in GiftsActivity. 
 * It's a retained instance to persist if there's config changes
 * 
 * @author xxx xxx
 */
public class NonUIGiftsDataFragment extends Fragment implements GiftsFragmentComposite {

	public static final String NON_UI_FRAGMENT_TAG = "Potlatch-"+NonUIGiftsDataFragment.class.getSimpleName();

	private GiftsActivity mActivity;	
	private HashMap<GiftsSectionType, GiftsFragment> mGiftsFragments = 
			new HashMap<GiftsSectionType, GiftsFragment>();
	private DownloadBinder mDownloadBinder;

	private boolean mInternetAvailable;

	private SimpleStringSearchCriteria<Gift> mSearch = new SimpleStringSearchCriteria<Gift>(null);
	
	/**
	 * Constructor, init the data structures for all the gifts types screens
	 */
	public NonUIGiftsDataFragment() {
		
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			mActivity = (GiftsActivity) activity;

			// Keep this Fragment around even during config changes
			setRetainInstance(true);
			
		} catch (ClassCastException e) {
			throw new IllegalArgumentException();
		}
	}

	@Override
	public void onDestroy() {
		mGiftsFragments.clear();
		super.onDestroy();
	}
	
	/**
	 * Caller should be careful that there is one
	 * 
	 * @param giftsSectionType
	 * @return
	 */
	@Override
	public GiftsFragment getGiftsFragment(GiftsSectionType giftsSectionType) {
		return mGiftsFragments.get(giftsSectionType);
	}
	
	/**
	 * Setter for the fragment for the type, if none there yet, it creates it
	 * 
	 * @param giftsSectionType
	 * @return
	 */
	void setGiftsFragment(GiftsSectionType giftsSectionType, GiftsFragment fragment) {
		mGiftsFragments.put(giftsSectionType, fragment);
		
		// give it the binder if possible
		if (mDownloadBinder != null) {
			fragment.setDownloadBinder(mDownloadBinder, mInternetAvailable, !mActivity.isDialogOpen());
		}
	}

	/**
	 * Called when the user is reset and they don't have admin (so need to remove
	 * a specific fragment - which would be for obscene gifts
	 * 
	 * @param giftsSectionType
	 */
	void clearFragment(final FragmentManager fm, final GiftsSectionType giftsSectionType) {
		
		// remove reference to it, allow it to get recycled by fragment manager
		// ran into problems trying to remove it manually, even on ui thread, idk why
		mGiftsFragments.remove(giftsSectionType);
	}
	
	/**
	 * Stores the binder, and passes it to each of the fragments that have already loaded
	 * 
	 * @param downloadBinder
	 * @param refreshGiftsNow 
	 */
	@Override
	public void setDownloadBinder(DownloadBinder downloadBinder, boolean refreshGiftsNow) {
    	mDownloadBinder = downloadBinder;
    	
    	for (GiftsFragment frg : mGiftsFragments.values()) {
    		frg.setDownloadBinder(mDownloadBinder, mInternetAvailable, refreshGiftsNow);
    	}
	}

	/**
	 * Called by the activity that is monitoring internet, pass it to the fragments so they
	 * can feedback, and not make calls
	 * 
	 * @param isAvailable
	 * @param refreshNow 
	 */
	@Override
	public void setInternetAvailable(boolean isAvailable, boolean refreshNow) {
		mInternetAvailable = isAvailable;
    	for (GiftsFragment frg : mGiftsFragments.values()) {
    		frg.setInternetAvailable(mInternetAvailable, refreshNow);
    	}
	}
	
	/* (non-Javadoc)
	 * @see com.courseracapstone.android.GiftsFragmentComposite#isInternetAvailable()
	 */
	@Override
	public boolean isInternetAvailable() {
		return mInternetAvailable;
	}

	@Override
	public String getSearchString() {
		return mSearch.getSearchString();
	}

	/* (non-Javadoc)
	 * @see com.courseracapstone.android.GiftsFragmentComposite#setSearchString(java.lang.String)
	 */
	@Override
	public void setSearchString(String query) {
		mSearch.setSearchString(query);
	}

	/* (non-Javadoc)
	 * @see com.courseracapstone.android.GiftsFragmentComposite#getSearch()
	 */
	@Override
	public SimpleStringSearchCriteria<Gift> getSearch() {
		return mSearch;
	}



}
