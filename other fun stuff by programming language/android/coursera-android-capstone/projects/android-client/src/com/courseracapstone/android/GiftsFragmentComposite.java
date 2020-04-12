package com.courseracapstone.android;

import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.model.SearchCriteria.SimpleStringSearchCriteria;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.common.GiftsSectionType;

/**
 * GiftsActivity uses it as a composite of several tabbed fragments. Elsewhere
 * it's a single fragment.
 * 
 * @author xxx xxx
 *
 */
public interface GiftsFragmentComposite {

	/**
	 * When a fragment wants to refresh its data (it has a binder already)
	 * it tests for internet before making the request
	 * 
	 * @return
	 */
	public abstract boolean isInternetAvailable();

	public abstract void setSearchString(String query);

	public abstract SimpleStringSearchCriteria<Gift> getSearch();

	public abstract String getSearchString();

	public void setDownloadBinder(DownloadBinder downloadBinder, boolean refreshGiftsNow);

	public void setInternetAvailable(boolean isAvailable, boolean refreshNow);

	public abstract GiftsFragment getGiftsFragment(GiftsSectionType giftsType);

}