package com.courseracapstone.android;

import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.courseracapstone.android.model.Gift;
import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.android.service.DownloadBinder.AuthTokenStaleException;
import com.courseracapstone.android.service.PartialPagedList;
import com.courseracapstone.common.GiftsSectionType;
import com.courseracapstone.common.PagedCollection;

/**
 * The UI fragment that displays a gifts list There are several of these
 * displaying the various gifts types
 * 
 * @author xxx xxx
 * 
 */
public class GiftsFragment extends Fragment implements LoaderCallbacks<PagedCollection<Gift>> {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();
	private static final String STATIC_LOG_TAG = "Potlatch-"+GiftsFragment.class.getSimpleName();
	
	protected static final String KEY_GIFTS_SECTION_NAME = "KEY_GIFTS_SECTION_NAME";
	protected static final String KEY_INDICATOR_COLOR = "KEY_INDICATOR_COLOR";

	public static GiftsFragment newInstance(GiftsSectionType giftsSectionType, int indicatorColor) {
		Log.d(STATIC_LOG_TAG, "create fragment for "+giftsSectionType);

		Bundle bundle = new Bundle();
		bundle.putString(KEY_GIFTS_SECTION_NAME, giftsSectionType.name());
		bundle.putInt(KEY_INDICATOR_COLOR, indicatorColor);

		GiftsFragment fragment = new GiftsFragment();
		fragment.setArguments(bundle);

		return fragment;
	}

	protected GiftsSectionType mGiftsSectionType;
	protected GiftListAdapter mAdapter;
	protected ListView mListView;
	protected ProgressBar mTopProgress;
	protected ProgressBar mBottomProgress;
	protected TextView mEmptyLabel;
	protected DownloadBinder mDownloadBinder;
	protected boolean mHasLoaded = false;
	private AbstractGiftsListActivity mActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	return inflater.inflate(R.layout.gifts_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();

        if (args != null) {
        	mGiftsSectionType = GiftsSectionType.valueOf(args.getString(KEY_GIFTS_SECTION_NAME));
            TextView empty = (TextView) view.findViewById(android.R.id.empty);
            int indicatorColor = args.getInt(KEY_INDICATOR_COLOR);
            empty.setTextColor(indicatorColor);

        }

		mListView = (ListView) view.findViewById(android.R.id.list);		
		mTopProgress = (ProgressBar) view.findViewById(R.id.top_progress_bar);
		mBottomProgress = (ProgressBar) view.findViewById(R.id.bottom_progress_bar);
		mEmptyLabel = (TextView) getView().findViewById(android.R.id.empty);
		
		// init to the activity, note this will cause a callback to set a binder if already set
		// see setDownloadBinder() and setInternetAvailable()
		
		try {
			mActivity = (AbstractGiftsListActivity) getActivity();

			// there are 2 activities that create gifts fragments, the main one with
			// tabs, and then the gifts chain which is specialized in that it uses a different
			// row height and a different layout for the list. The gifts chain is straightforward
	        // in its binding, but gifts activity keeps the fragments in a non-ui data
	        // fragment
	        if (mActivity instanceof GiftsActivity) {
				((GiftsActivity)mActivity).linkGiftsFragmentFromTab(this);
	        }
		} 
		catch (ClassCastException e) {
			Log.e(LOG_TAG, "program error, GiftsFragment must be in GiftsActivity");
		}
    }

	@Override
	public void onStart() {
		super.onStart();

		Log.d(LOG_TAG, String.format("onStart: have binder and internet, requesting refresh for %s", 
				mGiftsSectionType));
		
		// this is here specifically to deal with the tab sliding/choosing problem that as the
		// tabs move more than 2 away, the fragment is stopped and the listview is cleared of
		// its adapter by android (I guess that's intentional)
		// so here, where it's starting the fragment up as it is coming back into view, need to 
		// ensure it gets its data
		if (mDownloadBinder != null 
				&& mActivity instanceof GiftsActivity) {
			((GiftsActivity)mActivity).checkOnlineAndRefreshData(false);
        }
	}

	/**
	 * Call notify data set changed as a matter of course, to make sure there's no errors about it changing
	 * without notification. There's a chance that the backend could change while this fragment is in a paused/stop
	 * state
	 */
	@Override
	public void onResume() {
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
		super.onResume();
	}

	/**
     * For tabbed gifts fragments called by the non-ui fragment which retains it for each of the instances.
	 * Called after the non-UI fragment is linked by the call in
	 * onViewCreated() to the activity's linkGiftsFragmentFromTab() and then passes
	 * the binder.
	 * For other gifts fragments, this is called directly from the activity when it gets its binder
	 *  
     * @param downloadBinder
	 * @param refreshGiftsNow 
     */
    void setDownloadBinder(DownloadBinder downloadBinder, boolean hasInternet, boolean refreshGiftsNow) {
    	mDownloadBinder = downloadBinder;
    	
 		setInternetAvailable(hasInternet, refreshGiftsNow);
    }

    /**
     * Changes the text of the no matches field depending on the internet
     * availability. That way, if there are rows, there's no problem and the
     * user can continue viewing them.
     * Also tests for a binder, if it's there and the adapter is null, can immediately
     * test for data availability and download it
     * 
     * @param isAvailable
     * @param refreshGiftsNow 
     */
	public void setInternetAvailable(boolean isAvailable, boolean refreshGiftsNow) {
		mEmptyLabel.setText(isAvailable ? R.string.list_no_gifts : R.string.not_online_gifts_msg);

		
    	// not safe to refresh because add gift dialog is open 
		if (!refreshGiftsNow) {
			return;
		}
		
		// no data yet
		if (mAdapter == null) {
			
			if (mDownloadBinder != null) {
				
				if (mDownloadBinder.hasGiftsData(mGiftsSectionType)) {
					initAdapter();
				}
				else if (isAvailable && !mActivity.isUserResetting()) {
					Log.d(LOG_TAG, String.format("setInternetAvailable: have binder and internet, requesting refresh for %s", mGiftsSectionType));
					try {
						mDownloadBinder.refreshOrNewGifts(mActivity.getSearchCriteria(), mGiftsSectionType);
					}
					catch (AuthTokenStaleException e) {
						// abort the current task, handleBinderMessages() will deal with it asap
					}
				}
				else {
					Log.d(LOG_TAG, String.format("setInternetAvailable: have binder but no internet, %s waiting...", mGiftsSectionType));
				}
			}
			else {
				//TODO remove this debug
				Log.d(LOG_TAG, String.format("setInternetAvailable: no binder yet, %s waiting...", mGiftsSectionType));				
			}
		}
	}

	/**
	 * It appears if the listview's adapter is kept after onStop() when it comes back to onStart()
	 * when a tab is slid or pressed, the listview doesn't show anything, somehow the listview is
	 * losing its adapter.. is is an android 'feature'? So, if there's an adapter and the listview
	 * lacks one, assign it
	 */
	void notifyAnotherPageHasLoaded(boolean onTime) {
		
		if (mAdapter != null) {
			
			if (mListView.getAdapter() == null) {
				setAdapterToListView();
			}
			
			Log.d(LOG_TAG, "notifyAnotherPageHasLoaded: more rows of same gifts ready onTime type="+mGiftsSectionType);
			mAdapter.notifyAnotherPageHasLoaded();
		}
		else if (mHasLoaded) {
			Log.d(LOG_TAG, "notifyAnotherPageHasLoaded: strange... hasLoaded is set, but no adapter type="+mGiftsSectionType);
			getLoaderManager().restartLoader(0, null, this);
		}
		else {
			// first load for this type
			initAdapter();
		}
	}
	
	/**
	 * When this fragment links to the activity (after the views are created)
	 * the activity calls this method to link it up to the non-UI fragment
	 * @return
	 */
	public GiftsSectionType getGiftsSectionType() {
		return mGiftsSectionType;
	}

	/**
	 * Init the data loader for a new user instance (ie. when sign-in has just completed)
	 * Probably only gets called for the fragment which does All Gifts...
	 */
	void initAdapter() {
		if (isDetached() || getActivity() == null) {
			Log.i(LOG_TAG, "initAdapter: init loader, not ready (isDetached), will init when views create type="+mGiftsSectionType);
			return;
		}
		
		Log.i(LOG_TAG, "initAdapter: init loader, ready (attached) type="+mGiftsSectionType);
		getLoaderManager().initLoader(mGiftsSectionType.getIndex(), null, this);
		
		// call the activity's factory method to init the correct adaptor, pass it the binder
		// because it may not even have it yet, if this is a config change
		mAdapter = mActivity.getListAdapter(this, 
				mDownloadBinder.getNewUserGiftsList(mGiftsSectionType), mGiftsSectionType, mEmptyLabel);
		setAdapterToListView();
	}

	private void setAdapterToListView() {
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(mAdapter);
	}
	
	// ----------------------- LoaderManager.callbacks
	
	@Override
	public Loader<PagedCollection<Gift>> onCreateLoader(int id, Bundle args) {
		mHasLoaded = true;
		return new PagedCollectionAsyncLoader<Gift>(getActivity().getBaseContext(), mDownloadBinder);
	}

	@Override
	public void onLoadFinished(Loader<PagedCollection<Gift>> loader, PagedCollection<Gift> data) {
		Log.d(LOG_TAG, "onLoadFinished: swap the data in the loader... somehow?");
		
	}

	@Override
	public void onLoaderReset(Loader<PagedCollection<Gift>> arg0) {
		Log.w(LOG_TAG, "onLoaderReset: somehow lost the data in a custom loader!!");
	}

	// ----------------------- 3 different row layouts depending on the type (created by the activity's factory method)

	protected abstract class GiftListAdapter extends PagedListAdapter<Gift> implements OnItemClickListener {

		protected float mListRowHeight;
		protected int mRowLayoutResId;
		
		protected GiftListAdapter(PartialPagedList<Gift> partialPagedList, TextView emptyLabel) {
			super(partialPagedList, emptyLabel);
		}
		
		@Override
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();
			setEmptyLabel(mEmptyLabel);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			mEmptyLabel.setVisibility(View.GONE);
			
			handlePageLoading(position, mListView, mListRowHeight, mTopProgress, mBottomProgress);
			
			RelativeLayout layout = null;
			
			try {
				// after the header row in a chain is removed, android still thinks it's the same
				// type, a chain top... but it isn't, so don't try to convert the text view, create a new layout
				layout = convertView == null || convertView instanceof TextView 
						? (RelativeLayout) mActivity.getLayoutInflater().inflate(mRowLayoutResId, parent, false)
						: (RelativeLayout) convertView;

				Gift gift = mPagedList.getRow(position);
				
				// since the timing of the calls within the adapter is down to android and scrolling
				// by the user, it's not possible to completely make sure at the backend that a refresh
				// has happened and then, before this adapter gets the notification of the data changes
				// (backend changes -> sendMessage -> handleMessage -> fire dataSetChanged)
				// this method is firing on a now non-existent row (would have to be a deletion from
				// the end, I think...).
				// the chances are very small of getting a null gift here, but possible. there's
				// considerable effort to keep the window of visible change very short to clients by
				// keeping a copy of the list and only updating it at the end of refreshes
				
				// handle the possible null with a message and leave the row empty... effect of this,
				// we'll see if it even happens, could put a message in there but if dataSetChanged is
				// imminent, it probably wouldn't even be seen
				if (gift == null) {
					Log.w(LOG_TAG, "initNewUserGiftData.adapter: gift row is gone");
				}
				else {
					layoutRow(layout, gift);
				}
			} 
			catch (Exception e) {
				// have encountered null from getLayoutInflater() locally, since using the activity
				// it hasn't happened yet... but trap exceptions just in case!
				Log.e(LOG_TAG, "adapter.getView: error inflating the layout", e);
			}

			return layout;
		}

		protected void layoutRow(RelativeLayout layout, Gift gift) {
			TextView title = (TextView) layout.findViewById(R.id.list_gift_title);
			title.setText(gift.getTitle());
			
			ThumbView thumb = (ThumbView) layout.findViewById(R.id.thumb_view);				
			thumb.setGiftId(gift.getId(), gift.getThumbnailPath(), mDownloadBinder);
			
			Resources res = getResources();
			
			ImageView heart = (ImageView) layout.findViewById(R.id.list_heart_image);
			heart.setBackground(res.getDrawable(gift.getUserTouchedBy()
						? R.drawable.heart : R.drawable.heart_off));

			// smaller devices these might be optional, test for non-null

			TextView touchedBy = (TextView) layout.findViewById(R.id.list_touched_by);	
			
			if (touchedBy != null) {
				// make the right string for touches
				int intTouches = (int) gift.getTouchCount();
				touchedBy.setText(res.getQuantityString(
						R.plurals.gift_list_touched_by, intTouches, intTouches));
			}
			
			ImageView monkey = (ImageView) layout.findViewById(R.id.list_monkey);
			
			if (monkey != null) { 
				monkey.setVisibility(gift.getInappropriateCount() + gift.getObsceneCount() > 0
						? View.VISIBLE : View.INVISIBLE);
			}
		}
		
		@Override
		public void addPrevPage() {
			try {
				mDownloadBinder.loadGiftsPrevPage(mGiftsSectionType);
			}
			catch (AuthTokenStaleException e) {
				// abort the current task, handleBinderMessages() will deal with it asap
			}
		}
		
		@Override
		public void addNextPage() {
			try {
				mDownloadBinder.loadGiftsNextPage(mGiftsSectionType);
			}
			catch (AuthTokenStaleException e) {
				// abort the current task, handleBinderMessages() will deal with it asap
			}
		}			
	}
	
	/**
	 * Normal gifts lists on the main tabs (apart from chained tab)
	 */
	class TabbedGiftListAdapter extends GiftListAdapter {

		public TabbedGiftListAdapter(PartialPagedList<Gift> partialPagedList, TextView emptyLabel) {
			super(partialPagedList, emptyLabel);
			
			mListRowHeight = getResources().getDimension(R.dimen.gifts_lists_row_height);
			mRowLayoutResId = R.layout.gift_list_row;
		}
		
	    /**
	     * Respond to clicks in the list, view the gift
	     */
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Intent i = new Intent(getActivity(), ViewGiftActivity.class);
			i.putExtra(ViewGiftActivity.PARAM_GIFT_ID, id);
			i.putExtra(ViewGiftActivity.PARAM_GIFTS_SECTION_TYPE, mGiftsSectionType.name());
			i.putExtra(ViewGiftActivity.PARAM_LIST_POSITION, position);
			
			ImageButton fabBtn = mActivity.getFabBtn();
			if (fabBtn != null) {
				i.putExtra(ViewGiftActivity.PARAM_FAB_X, fabBtn.getX());
				i.putExtra(ViewGiftActivity.PARAM_FAB_Y, fabBtn.getY());			
			}

			ThumbView thumb = (ThumbView) view.findViewById(R.id.thumb_view);
			Bitmap image = thumb.getImage();

			// put the image onto the binder for sharing (it's not properly scaled but it's ok)
			Bundle bundle = new Bundle();
			bundle.putBinder(GiftsActivity.PARAM_BINDER, mDownloadBinder);
			mDownloadBinder.putTransitionThumb(image);
			i.putExtra(ViewGiftActivity.PARAM_BUNDLE, bundle);
			
			if (image != null) {
				
				ActivityOptions activityOptions = ActivityOptions.makeThumbnailScaleUpAnimation(
						thumb, image, (int)thumb.getX(), (int)thumb.getY());
				mActivity.startActivity(i, activityOptions.toBundle());
			}
			else {
				mActivity.startActivity(i);
			}
		}
	}

	/**
	 * Chain gifts list on the main tabs
	 */
	class ChainGiftListAdapter extends TabbedGiftListAdapter {

		public ChainGiftListAdapter(PartialPagedList<Gift> partialPagedList, TextView emptyLabel) {
			super(partialPagedList, emptyLabel);
			
			mListRowHeight = getResources().getDimension(R.dimen.all_chains_gifts_lists_row_height);
			mRowLayoutResId = R.layout.all_chains_gift_list_row;
		}
		
		@Override
		protected void layoutRow(RelativeLayout layout, Gift gift) {
			super.layoutRow(layout, gift);
			
			TextView chainCount = (TextView) layout.findViewById(R.id.list_chain_count);	
			chainCount.setText(getResources().getString(
					R.string.chain_gift_list_count, gift.getChainCount()));
		}
		
	    /**
	     * Respond to clicks in the list, view the gift
	     */
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Intent i = new Intent(getActivity(), OneChainGiftsActivity.class);
			i.putExtra(ViewGiftActivity.PARAM_GIFT_ID, id);
			
			ImageButton fabBtn = mActivity.getFabBtn();
			if (fabBtn != null) {
				i.putExtra(ViewGiftActivity.PARAM_FAB_X, fabBtn.getX());
				i.putExtra(ViewGiftActivity.PARAM_FAB_Y, fabBtn.getY());			
			}

			// these rows only appear because they are also the top of the chain
			// so can use the thumb

			ThumbView thumb = (ThumbView) view.findViewById(R.id.thumb_view);
			Bitmap image = thumb.getImage();

			// put the image onto the binder for sharing (it's not properly scaled but it's ok)
			Bundle bundle = new Bundle();
			bundle.putBinder(GiftsActivity.PARAM_BINDER, mDownloadBinder);
			mDownloadBinder.putTransitionThumb(image);
			i.putExtra(ViewGiftActivity.PARAM_BUNDLE, bundle);
			
			if (image != null) {
				
				ActivityOptions activityOptions = ActivityOptions.makeThumbnailScaleUpAnimation(
						thumb, image, (int)thumb.getX(), (int)thumb.getY());
				mActivity.startActivity(i, activityOptions.toBundle());
			}
			else {
				mActivity.startActivity(i);
			}
		}
	}
	
	/**
	 * One Chain gifts list selected from the chains list tab or by viewing a gift's chain
	 * from it's view menu, the difference here is the first row (it it's the chain Top)
	 * is not displayed in the list
	 */
	class OneChainGiftListAdapter extends TabbedGiftListAdapter {

		private static final int CHILD_ROW = 0, HEADER_ROW = 1;
		
		private final boolean mContainsChainTop;
		
		public OneChainGiftListAdapter(PartialPagedList<Gift> partialPagedList, TextView emptyLabel) {
			super(partialPagedList, emptyLabel);
			
			mRowLayoutResId = R.layout.one_chain_gift_list_row;

			Gift g = partialPagedList.getRow(0);
			mContainsChainTop = g != null && g.getChainTop();
		}
		
		/**
		 * 2 types of rows, don't want to display the header
		 */
		@Override
		public int getViewTypeCount() {
			return mContainsChainTop ? 2 : 1;
		}
		
		@Override
		public boolean isEnabled(int position) {
			return getItemViewType(position) == CHILD_ROW;
		}
		
		/**
		 * Header row is row 0, but only if it's chain top too
		 */
		@Override
		public int getItemViewType(int position) {
			if (position == 0) {
				Gift g = mPagedList.getRow(0);
				if (g != null && g.getChainTop()) {
					return HEADER_ROW;
				}
			}
			
			return CHILD_ROW;
		}
		
		@Override
		public Gift getItem(int position) {
			return getItemViewType(position) == CHILD_ROW 
					? mPagedList.getRow(position)
	                : null;
		}
		
		/**
		 * header row doesn't go into the layout at all
		 */
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			if (getItemViewType(position) == CHILD_ROW) {
				return super.getView(position, convertView, parent);
			}
			else {
				// create a view with height 1px 
				TextView tv = new TextView(mActivity);
				tv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, 1));
				return tv;
			}
		}
		
		@Override
		protected void layoutRow(RelativeLayout layout, Gift gift) {
			super.layoutRow(layout, gift);

			ImageView endChain = (ImageView) layout.findViewById(R.id.bottom_chain_image);

			if (mListStats.hasLastPage() 
				&& gift.equals(mPagedList.getRow(mListStats.getListSize() -1))) {
				endChain.setVisibility(View.INVISIBLE);
			}
			else {
				endChain.setVisibility(View.VISIBLE);
			}

		}
		
	}
}
