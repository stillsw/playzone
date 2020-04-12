package com.stillwindsoftware.keepyabeat.gui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.ListPopupWindow;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.db.TagsContentProvider;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.CloneRhythmCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor;
import com.stillwindsoftware.keepyabeat.platform.AndroidFont;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.backend.ImageLoadBinder;
import com.stillwindsoftware.keepyabeat.player.backend.RhythmImageService;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmImporter;
import com.stillwindsoftware.keepyabeat.utils.RhythmSharer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Rhythms dialog_simple_list fragment
 */
public class RhythmsListFragment extends SwipeableListFragment
		implements View.OnClickListener, TextWatcher, View.OnLayoutChangeListener {

    private static final String LOG_TAG = "KYB-"+RhythmsListFragment.class.getSimpleName();
    static final String SETTING_RHYTHM_SEARCH_OPEN = "RHYTHMS_SEARCH_OPEN";
    private static final int LOADER_LIST_ADAPTER_ID = 0;
    private static final int LOADER_TAGS_ADAPTER_ID = 1;
    static final String RHYTHMS_LIST_IMAGE_COORDS = "image_coords";
    static final String RHYTHMS_LIST_NAME_COORDS = "name_coords";

	protected SimpleCursorAdapter mAdapter;
    private SimpleCursorAdapter mTagsListAdapter;

    private GestureDetector mDoubleTapGestureDetector;

	// onStart() binds to the service that will provide images for the dialog_simple_list
	private ImageLoadBinder mImageLoadBinder;
    private ViewGroup mSearchTagsContainer;

    // local set of the data from settings manager (in prefs)
    private HashSet<String> mSelectedTagKeys;
    private boolean mImageLoadBinderCloseRequested;
    private PopupWindow mRemoveTagFromSearchPopup;
    private LayoutInflater mLayoutInflater;

    @Override
	public void onAttach(Context activity) {
		super.onAttach(activity);

        // keep the keys, updates are fed back to settings manager as they happen
        mSelectedTagKeys = mSettingsManager.getRhythmSearchTagKeysAsSet();
        if (mSelectedTagKeys == null) {
            mSelectedTagKeys = new HashSet<>();
        }
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mLayoutInflater = inflater; // cache for later as get errors now using getLayoutInflater()

		View listContainer = inflater.inflate(R.layout.rhythms_list, container, false);
        assignSlideControlViews(listContainer);

        mSearchTagsContainer = (ViewGroup) listContainer.findViewById(R.id.search_tags);
        mSearchTagsContainer.findViewById(R.id.choose_tags_btn).setOnClickListener(this);

        // show or hide the search views depending on the stored pref
        toggleShowSearchViews(mSettingsManager.readStateFromStore(SETTING_RHYTHM_SEARCH_OPEN, false));

        mIsLeftSlide = true;

        return listContainer;
	}

    @Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

        getListView().setTextFilterEnabled(true); // filter on search text via textwatcher (this class)
		
        mDoubleTapGestureDetector = new GestureDetector(mActivity,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        // great example of how to select a view from the position
//                        try {
//                            int pos = lv.pointToPosition((int) e.getX(), (int) e.getY());
//
//                            // this should return the relative layout at the position
//                            View layout = lv.getChildAt(pos - lv.getFirstVisiblePosition());
//
//                            if (layout != null && layout instanceof RelativeLayout) {
//                                ViewGroup card = (ViewGroup) layout.findViewById(R.id.options_card);
//
//                                if (card != null) {
//                                    closeOptionsCard(card, (ViewGroup) layout);
//                                    TextView keyVw = (TextView) layout.findViewById(R.id.external_key);
//                                    final String key = keyVw.getText().toString();
//                                    openRhythmForKey((ViewGroup) layout, key);
//                                    return true;
//                                }
//                            }
//
//                            // if got this far it means something didn't tie up above and so couldn't use that method
//                            // use the cursor to get the key but don't try to implement the animation
//                            AndroidResourceManager.logw(LOG_TAG, String.format("onDoubleTap: failed to prepare animation, opening directly, pos=%s, firstVisiblePos=%s, layout=%s",
//                                    pos, lv.getFirstVisiblePosition(), layout));
//
//                            Cursor csr = mAdapter.getCursor();
//                            csr.moveToPosition(pos);
//                            String key = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));
//
//                            Intent intent = PlayRhythmsActivity.getIntentToOpenRhythm(mActivity, key);
//                            startActivity(intent);
//
//                        } catch (Exception e1) {
//                            AndroidResourceManager.logw(LOG_TAG, "onDoubleTap: failed", e1);
//                        }
//
//                        return true;
//                    }
                        return true;
                    }
                });
	}

    @Override
    protected boolean hasListAdapter() {
        return mAdapter != null;
    }

    /**
     * Updates to tags need to be reflected in the dialog_simple_list and in the search tags
     */
    @Override
    protected void startListening() {
        AndroidResourceManager.logv(LOG_TAG, "registerBeatTracker: called");
        mResourceManager.getListenerSupport().addListener(mSettingsManager, this);
        mResourceManager.getListenerSupport().addListener(mResourceManager.getLibrary().getBeatTypes(), this); // for colour changes
    }

    /**
     * Tag search buttons could be out of sync (eg. one got deleted)
     */
    @Override
    public void itemChanged(int changeId, String key, int natureOfChange, LibraryListenerTarget[] listenerTargets) {
        AndroidResourceManager.logv(LOG_TAG, "itemChanged: called");
        mSelectedTagKeys = mSettingsManager.getRhythmSearchTagKeysAsSet();
        super.itemChanged(changeId, key, natureOfChange, listenerTargets);
    }

    /**
     * itemChanged() is called off the ui thread, so to avoid another class this class implements Runnable
     */
    @Override
    public void run() {
        //AndroidResourceManager.logv(LOG_TAG, "run: called, refresh images and update search to selected keys");
        refreshImages();
        updateTagSearchToSelectedKeys(false);           // will also cause restart of list loader, and changes to colours will show
    }

    @Override
    protected void showOpen(boolean slide) {

        super.showOpen(slide);

        if (mImageLoadBinder == null) {
            bindToImageMakingService();
        }

        //AndroidResourceManager.logd(LOG_TAG, "showOpen: test for tags searched any missing keys");
        checkForMissingTagsInSearch();
    }

    /**
     * Only called from playRhythmsActivity.doEdgeDiscovery() because a touch has happened
     * by the left edge, so need to make sure the view is populated and ready
     */
    @Override
    protected void prepareForOpening() {

        super.prepareForOpening();

        if (mImageLoadBinder == null) {
            bindToImageMakingService();
        }
    }

    @Override
    protected void showClosed(boolean slide) {

        super.showClosed(slide);

        if (mImageLoadBinder != null && !mImageLoadBinderCloseRequested) {
            // disconnect from beat tracker service
            mImageLoadBinderCloseRequested = true;
//            AndroidResourceManager.logw(LOG_TAG, "showClosed: closing so set mImageLoadBinderCloseRequested = "+mImageLoadBinderCloseRequested);
            unbindImageMakingService();
        }

        hideKeypadForSearch();
    }

    private void hideKeypadForSearch() {
        InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View toolbarCustomView = ((PlayRhythmsActivity)mActivity).getToolbarCustomView();
        if (toolbarCustomView != null) {
            View view = toolbarCustomView.findViewById(R.id.searchText);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

	// --------------- OnTouchListener method

	/**
	 * Communicate upwards to the activity providing not scrolling
	 */
	@Override
	public boolean onTouch(View v, MotionEvent event) {

        if (!super.onTouch(v, event)) {

            if (v.getId() == R.id.rhythm_row
                    && mDoubleTapGestureDetector != null
                    && mDoubleTapGestureDetector.onTouchEvent(event)) {

                // react to double-tap
                if (v instanceof RelativeLayout) {
                    ViewGroup card = (ViewGroup) v.findViewById(R.id.options_card);

                    if (card != null) {
                        closeOptionsCard(card, (ViewGroup) v);
                        TextView keyVw = (TextView) v.findViewById(R.id.external_key);
                        final String key = keyVw.getText().toString();
                        openRhythmForKey((ViewGroup) v, key);
                        return true;
                    }
                }
            }
        }
		
        return false;
	}

    private void refreshImages() {
        // reload only if there has previously been a load
        if (mHasLoaded) {
            if (mImageLoadBinder != null) {
                mImageLoadBinder.clearImageCache();
            }
            else {
                AndroidResourceManager.logw(LOG_TAG, "refreshListAndImages: no binder so image cache won't be touched");
            }
        }
    }

    /**
     * If a tag has been deleted while the list wasn't used and it happens to be in the search, it needs to be removed
     */
    private void checkForMissingTagsInSearch() {
        if (mSelectedTagKeys != null && mSelectedTagKeys.size() > 0) {

            ArrayList<String> removedTags = new ArrayList<>();
            HashSet<String> selectedTagKeys = mSettingsManager.getRhythmSearchTagKeysAsSet();
            if (selectedTagKeys.size() != mSelectedTagKeys.size()) {
                for (String tag : mSelectedTagKeys) {
                    if (!selectedTagKeys.contains(tag)) {
                        removedTags.add(tag);   // can't remove it from the list immediately (concurrent access)
                    }
                }
            }

            if (removedTags.size() > 0) {
                for (String tag : removedTags) {
                    mSelectedTagKeys.remove(tag);
                    AndroidResourceManager.logd(LOG_TAG, String.format("checkForMissingTagsInSearch: removed tag from search, presume has been deleted (%s)", tag));
                }

                updateTagSearchToSelectedKeys(false);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        AndroidResourceManager.logd(LOG_TAG, "onStart:");
        if (mIsListStateOpen && mHasLoadStarted) {
            bindToImageMakingService();
        }
    }

    @Override
	public void onStop() {
		super.onStop();
        if (mImageLoadBinder != null && !mImageLoadBinderCloseRequested) {
            unbindImageMakingService();

        }
	}

    @Override
    protected View getToolbarCustomView() {
        return ((PlayRhythmsActivity)mActivity).getToolbarCustomView();
    }

    private void unbindImageMakingService() {
        try {
            mActivity.unbindService(mImageMakingServiceConn);
            mImageLoadBinder = null;
            mImageLoadBinderCloseRequested = false;
        } catch (Exception ignored) {} // if there's a not bound exception don't need to have it crash
    }

    /**
     * Called by the rhythm view when it needs an image but it's binder is no good anymore
     * @return a good binder if there is one (meaning the service is alive)
     */
    ImageLoadBinder getImageLoaderBinder() {
        if (mImageLoadBinder != null && mImageLoadBinder.isServiceAlive()) {
            return mImageLoadBinder;
        }
        else if (mHasLoadStarted && !mImageLoadBinderCloseRequested) {  // don't bind while unbinding is still happening
            bindToImageMakingService();
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "getImageLoaderBinder: could not bind to service because mImageLoadBinderCloseRequested="+mImageLoadBinderCloseRequested);
        }

        return null;    // calling view will make another call when it attempts to draw again, if binding has completed it will be returned above
    }

    /**
	 * Called during onStart()
	 */
	private void bindToImageMakingService() {
        mImageLoadBinderCloseRequested = false;
		Intent srvIntent = RhythmImageService.makeIntent(mActivity);
		AndroidResourceManager.logd(LOG_TAG, "bindToImageMakingService: attempt to bind to service mImageLoadBinderCloseRequested reset = "+mImageLoadBinderCloseRequested);
		if (!mActivity.bindService(srvIntent, mImageMakingServiceConn, Context.BIND_AUTO_CREATE)) {
			AndroidResourceManager.loge(LOG_TAG, "bindToImageMakingService: unable to connect to service");
		}
	}
	
	private ServiceConnection mImageMakingServiceConn = new ServiceConnection() {
		
		@Override
        public void onServiceConnected(ComponentName name, IBinder service) {
			AndroidResourceManager.logd(LOG_TAG, "connected to service");
			mImageLoadBinder = (ImageLoadBinder) service;
			
			// lazily init the target governor
			if (!mImageLoadBinder.hasTargetGoverner()) {
				// set up the draught target governer that will be shared for all the rhythm instances
				final int MINIMAL_SUB_BEATS_WIDTH = 3;
				final DraughtTargetGoverner targetGoverner = 
						new DraughtTargetGoverner(mResourceManager
								, true // shared
								, MINIMAL_SUB_BEATS_WIDTH);
				// additional settings
				targetGoverner.setRhythmAlignment(DraughtTargetGoverner.RhythmAlignment.LEFT);
				targetGoverner.setMaxBeatsAtNormalWidth(6);
				targetGoverner.setVerticalRhythmMargins(1.0f, 1.0f);
                Resources res = getResources();
                DisplayMetrics metrics = res.getDisplayMetrics();
                AndroidFont fullBeatsFontNormal = new AndroidFont(metrics, 15);
                AndroidFont fullBeatsFontSmall = new AndroidFont(metrics, 12);
                targetGoverner.setDrawNumbers(DraughtTargetGoverner.DrawNumbersPlacement.INSIDE_TOP
                        , DraughtTargetGoverner.DrawNumbersPlacement.INSIDE_TOP.getDefaultMargin()
                        , res.getDimension(R.dimen.draw_numbers_image_margin)
                        , fullBeatsFontNormal
                        , fullBeatsFontSmall);
				mImageLoadBinder.init(mResourceManager, targetGoverner);
			}

			// reload only if a load has started, because some views may not then have the binder
			// (ie. there's no telling when this method will return the binder)
			if (mHasLoadStarted) {
//                AndroidResourceManager.logw(LOG_TAG, "onServiceConnected: restartLoader");
				getLoaderManager().restartLoader(0, null, RhythmsListFragment.this);
			}
		}

        /**
         * Note, this is not a regular callback to inform that a requested unbind has completed,
         * rather it's a disaster fallback that we got disconnected
         * @param name not used
         */
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mImageLoadBinder = null;
            mImageLoadBinderCloseRequested = false;
//            AndroidResourceManager.logw(LOG_TAG, "onServiceDisconnected: disconnected service mImageLoadBinderCloseRequested reset = "+mImageLoadBinderCloseRequested);
		}
	};

    @Override
	protected void fillData() {
        fillRhythmsList();
        fillTagsList();  // fill even if not showing at the moment, since it has an init
	}

    private void fillRhythmsList() {

        AndroidResourceManager.logd(LOG_TAG, "fillRhythmsList: called");

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_RHYTHM_NAME, KybSQLiteHelper.COLUMN_ENCODED_BEATS,
                KybSQLiteHelper.VIEW_COLUMN_TAGS_LIST, KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
        int[] toLayoutViewIds = new int[] { R.id.rhythm_name, R.id.encodedBeats, R.id.rhythm_tags, R.id.external_key };

        mAdapter = new SimpleCursorAdapter(mActivity, R.layout.rhythm_row, null, dbColumnsForLayout, toLayoutViewIds, 0) {

            /**
             * Binding the relative layout, so can set items that depend on other data in the cursor
             */
            @Override
            public void bindView(View v, Context context, Cursor cursor) {

                mHasLoadStarted = true; // so binder service connection knows it might have to reload

                super.bindView(v, context, cursor);

                RelativeLayout layout = (RelativeLayout) v;
                v.setOnTouchListener(RhythmsListFragment.this);

                // use the key view to get the rhythm
                TextView externalKeyView = (TextView) layout.findViewById(R.id.external_key);
                // pass it with the encoded beats to the rhythm image view
                TextView beatsView = (TextView) layout.findViewById(R.id.encodedBeats);

                BasicRhythmView rhythmView = (BasicRhythmView) layout.findViewById(R.id.rhythm_img);

                // if no binder yet (service connection is taking a bit long, so will have to reload the cursor... see
                // ServiceConnection above)
                rhythmView.setImageLoadBinder(RhythmsListFragment.this, mImageLoadBinder,
                        (String) externalKeyView.getText(), (String)beatsView.getText());

                // more button to open the menu
                ImageButton moreBtn = (ImageButton) layout.findViewById(R.id.more_btn);
                if (moreBtn == null) {
                    AndroidResourceManager.logw(LOG_TAG, "bindView: missing more button");
                }
                else {
                    moreBtn.setOnClickListener(RhythmsListFragment.this);
                    moreBtn.setVisibility(View.VISIBLE);
                }

                ImageButton closeCardBtn = (ImageButton) layout.findViewById(R.id.close_card_btn);
                if (closeCardBtn == null) {
                    AndroidResourceManager.logw(LOG_TAG, "bindView: missing close button");
                }
                else {
                    closeCardBtn.setOnClickListener(RhythmsListFragment.this);
                    // the card should always be closed when binding
                    ViewGroup card = (ViewGroup) closeCardBtn.getParent().getParent();
                    card.setVisibility(View.GONE);
                }

                // dialog_simple_list the tag names
                TextView noTagsVw = (TextView) layout.findViewById(R.id.rhythm_tags);
                ViewGroup formattedTagsContainer = (ViewGroup) layout.findViewById(R.id.formatted_tags);

                SetRhythmNameAndTagsActivity.setTagViews(noTagsVw, formattedTagsContainer, mLayoutInflater, null);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                try {
                    return super.getView(position, convertView, parent);
                }
                catch (IllegalStateException e) { // seen this cause a force close in a call stack completely Android (ie. no kyb classes to trap the error)
                                                  // could try implementing an uncaught exception handler in the application if the following restart fails
                                                  // see https://stackoverflow.com/questions/19897628/need-to-handle-uncaught-exception-and-send-log-file
                    AndroidResourceManager.loge(LOG_TAG, "SimpleCursorAdapter.getView: error", e);
                    mActivity.restartActivityWithFailureMessage(R.string.non_critical_error_restart_activity);
                    throw new KybActivity.CaughtUnstoppableException("SimpleCursorAdapter.getView: IllegalStateException", e);
                }
            }

        };

        setListAdapter(mAdapter);

//        AndroidResourceManager.logw(LOG_TAG, "fillRhythmsList: initLoader");
        getLoaderManager().initLoader(LOADER_LIST_ADAPTER_ID, null, this);
    }

    /**
     * Almost the same as the method of the same name in SetRhythmNameAndTagsDialog,
     * but not enough to put them together
     */
    private void fillTagsList() {

        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_TAG_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
        int[] toLayoutViewIds = new int[] { R.id.tag_name, R.id.tag_key };

        mTagsListAdapter = new SimpleCursorAdapter(mActivity, R.layout.tag_multi_choice_spinner_row, null, dbColumnsForLayout, toLayoutViewIds, 0) {
// NOTE if need to replace name with localized name see RhythmBeatTypesFragment (sound adapter)

            @Override
            public void bindView(View v, Context context, Cursor cursor) {

                super.bindView(v, context, cursor);

                RelativeLayout layout = (RelativeLayout) v;
                TextView tagKey = (TextView) layout.findViewById(R.id.tag_key);
                CheckBox selectBox = (CheckBox) layout.findViewById(R.id.tag_checkbox);
                final boolean selected = mSelectedTagKeys.contains(tagKey.getText().toString());
                selectBox.setChecked(selected);

                // click anywhere on the layout toggles the setting
                layout.setOnClickListener(RhythmsListFragment.this);
            }
        };

        getLoaderManager().initLoader(LOADER_TAGS_ADAPTER_ID, null, this);

    }

    /**
     * Called each time the selection is changed by the user, and once after the loading has completed,
     * handy to update the tags from the cursor because don't have to look up the names from the db
     */
    private void updateTagsSearchButtons() {

        if (mTagsListAdapter == null || mTagsListAdapter == null || mSelectedTagKeys == null || mSearchTagsContainer == null) {
            return;                                 // not ready yet
        }

        // all existing tags
        Cursor csr = mTagsListAdapter.getCursor();

        if (csr == null) {
            return;                                 // not ready yet
        }

        if (csr.getCount() != 0) {
            csr.moveToFirst();

            while (!csr.isAfterLast()) {
                String tagKey = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));

                if (mSelectedTagKeys.contains(tagKey)) {
                    String tagName = csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_TAG_NAME));
//                    AndroidResourceManager.logd(LOG_TAG, "updateTagsSearchButtons: add "+tagName);
                    SetRhythmNameAndTagsActivity.addTagViewToContainer(mSearchTagsContainer, tagKey, tagName, mLayoutInflater, R.layout.tag_label_large, this);
                }
                else {
                    SetRhythmNameAndTagsActivity.removeTagButtonFromContainer(mSearchTagsContainer, tagKey);
                }

                csr.moveToNext();
            }
        }

        // any non-existing (ie. buttons for which tag has been deleted)
        for (int i = mSearchTagsContainer.getChildCount() - 1; i >= 0; i--) {
            View childAt = mSearchTagsContainer.getChildAt(i);
            //noinspection SuspiciousMethodCalls
            if (childAt.getId() != R.id.choose_tags_btn
                && !mSelectedTagKeys.contains(childAt.getTag())) {
                AndroidResourceManager.logd(LOG_TAG, "updateTagsSearchButtons: remove tag search view with text = "+((TextView) childAt).getText());
                mSearchTagsContainer.removeViewAt(i);
            }
        }
    }

    private void toggleShowSearchViews(boolean show) {

        mSettingsManager.writeStateToStore(SETTING_RHYTHM_SEARCH_OPEN, show);
        View toolbarCustomView = ((PlayRhythmsActivity)mActivity).getToolbarCustomView();

        if (show) {
            mSearchTagsContainer.setVisibility(View.VISIBLE);

            if (toolbarCustomView != null) {
                toolbarCustomView.findViewById(R.id.searchText).setVisibility(View.VISIBLE);
            }
        }
        else {
            mSearchTagsContainer.setVisibility(View.GONE);

            if (toolbarCustomView != null) {
                toolbarCustomView.findViewById(R.id.searchText).setVisibility(View.GONE);
            }

            hideKeypadForSearch();
        }

        // first time in there's no adapter, but after that when toggle the search views need to have the dialog_simple_list react
        if (mAdapter != null) {
            getLoaderManager().restartLoader(LOADER_LIST_ADAPTER_ID, null, this);
        }
    }

    // ------------------------ LoaderCallbacks
	
	// creates a new loader after the initLoader() call
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        if (id == LOADER_LIST_ADAPTER_ID) {

            String[] selectArgs = null;
            String selection = null;
            Uri uri = RhythmsContentProvider.CONTENT_URI;

            if (mSettingsManager.readStateFromStore(RhythmsListFragment.SETTING_RHYTHM_SEARCH_OPEN, false)) {
                // at start all rows would be shown so selection null, if the user toggles those have to add selections
                StringBuilder selectBldr = new StringBuilder();
                String text = mSettingsManager.getRhythmSearchText();

                String nameArg = null;
                if (text != null && !text.isEmpty()) {
                    selectBldr.append(String.format("UPPER(%s) LIKE ?", KybSQLiteHelper.COLUMN_RHYTHM_NAME));
                    nameArg = String.format("%%%s%%", text.toUpperCase());
                }

                if (mSelectedTagKeys.size() > 0) {

                    if (nameArg != null) {              // already started selection string, add and
                        selectBldr.append(" AND ");
                    }

                    boolean isFirst = true;
                    for (String tagKey : mSelectedTagKeys) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            selectBldr.append(" AND ");
                        }

                        selectBldr.append("EXISTS (SELECT NULL FROM RHYTHM_TAGS RTGS WHERE RHYTHMS_VW.EXTERNAL_KEY=RTGS.RHYTHM_FK AND RTGS.TAG_FK = '");
                        selectBldr.append(tagKey);
                        selectBldr.append("')");
                    }
                }

                if (selectBldr.length() > 0) {
                    uri = Uri.parse(RhythmsContentProvider.SEARCH_CONTENT_STR);
                    selection = selectBldr.toString();
                    if (nameArg != null) {
                        selectArgs = new String[]{nameArg}; // using args prevents sql injection attacks
                    }
                }
            }

            AndroidResourceManager.logv(LOG_TAG, "onCreateLoader: selection=" + selection);

            return new CursorLoader(mActivity, uri
                    , RhythmsContentProvider.RHYTHMS_LIST_COLUMNS, selection , selectArgs, KybSQLiteHelper.COLUMN_RHYTHM_NAME);
        }

        else { // must be tags spinner adapter

            String[] projection = {
                    KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
                    , KybSQLiteHelper.COLUMN_TAG_NAME };

            return new CursorLoader(mActivity, TagsContentProvider.CONTENT_URI, projection, null , null, null);
        }
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == LOADER_LIST_ADAPTER_ID) {

            mHasLoaded = true;
            mAdapter.swapCursor(data);

            if (mIsWaitingForDataToSlideOpen) {
                slideOpen();
            }

        }
        else { // must be tags adapter
            mTagsListAdapter.swapCursor(data);
        }

        // may as well update tags in the search
        updateTagsSearchButtons();
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// we lost the data
        if (loader.getId() == LOADER_LIST_ADAPTER_ID) {
            mAdapter.swapCursor(null);
        }
        else { // must be tags spinner adapter
            mTagsListAdapter.swapCursor(null);
        }
	}

    // ------------------------ Search btn and spinner OnClickListener

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        hideKeypadForSearch();

        if (viewId == R.id.close_search_btn) {
            toggleShowSearchViews(!mSettingsManager.readStateFromStore(SETTING_RHYTHM_SEARCH_OPEN, false));
        }
        else if (viewId == R.id.choose_tags_btn) {
            chooseTagsFromList(view, mTagsListAdapter, mSearchTagsContainer);
        }
        else if (viewId == R.id.tag_choose_row) {
            reactToSearchTagSelection(view);
        }
        else if (viewId == R.id.more_btn) {
            expandCardOptions(view);    // inside the rhythms dialog_simple_list, means expand the menu card
        }
        else if (viewId == R.id.close_card_btn) {
            // inside the rhythms dialog_simple_list, means close the menu card
            ViewGroup card = (ViewGroup) view.getParent().getParent();
            ViewGroup parent = (ViewGroup) card.getParent(); // card layout within a constraint layout

            closeOptionsCard(card, parent);
        }
        else if (viewId == R.id.remove_tag_from_search) {
            removeTagFromSearchButtonClicked(view);
        }
        else //noinspection SuspiciousMethodCalls
            if (mSelectedTagKeys != null && mSelectedTagKeys.contains(view.getTag())) {    // each tag search button has its key as a tag
            offerRemoveTagFromSearch(view);
        }
        else if (!doCardAction(view)) { // handled
            AndroidResourceManager.loge(LOG_TAG, "onClick: not search button/tag spinner row/card btn... what is this? " + view);
        }
    }

    /**
     * Popup a dialog_simple_list of tags to choose from
     * Used by the tags search button.
     * @param anchorListToView the view to anchor to
     * @param tagsListAdapter the adapter
     * @param tagsContainer the group that is used to measure
     */
    private void chooseTagsFromList(View anchorListToView, ListAdapter tagsListAdapter, ViewGroup tagsContainer) {
        ListPopupWindow tagsList = new ListPopupWindow(mActivity, null, android.R.attr.popupMenuStyle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tagsList.setBackgroundDrawable(getResources().getDrawable(R.drawable.cardview_background, null));
        }
        else {
            //noinspection deprecation
            tagsList.setBackgroundDrawable(getResources().getDrawable(R.drawable.cardview_background));
        }
        tagsList.setAdapter(tagsListAdapter);
        tagsList.setAnchorView(anchorListToView);
        int horizontalOffset = anchorListToView.getWidth() / 2; // anchor offset from the left
        tagsList.setContentWidth(Math.min(measureContentWidth(tagsListAdapter, mActivity), tagsContainer.getWidth() - horizontalOffset)); // not wider than screen
        tagsList.setHorizontalOffset(horizontalOffset);
        tagsList.setModal(true); // so back button causes it to close
        tagsList.show();
    }

    /**
     * Measures the data, so only use this technique for small sets
     * thanks to alerant for the answer:
     * https://stackoverflow.com/questions/14200724/listpopupwindow-not-obeying-wrap-content-width-spec
     * @param adapter the adapter than contains the views
     * @param activity context
     * @return measurement
     */
    private static int measureContentWidth(ListAdapter adapter, Activity activity) {
        ViewGroup mMeasureParent = null;
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;

        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }

            if (mMeasureParent == null) {
                mMeasureParent = new FrameLayout(activity);
            }

            itemView = adapter.getView(i, itemView, mMeasureParent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);

            final int itemWidth = itemView.getMeasuredWidth();

            if (itemWidth > maxWidth) {
                maxWidth = itemWidth;
            }
        }

        return maxWidth;
    }


    private void offerRemoveTagFromSearch(View view) {
        // show option to remove the search tag
        mRemoveTagFromSearchPopup = new PopupWindow(mActivity, null, android.R.attr.popupMenuStyle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRemoveTagFromSearchPopup.setBackgroundDrawable(getResources().getDrawable(R.drawable.cardview_background, null));
        }
        mRemoveTagFromSearchPopup.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        mRemoveTagFromSearchPopup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        Button removeBtn = (Button) mLayoutInflater.inflate(R.layout.remove_single_tag_from_search_button, null);
        mRemoveTagFromSearchPopup.setContentView(removeBtn);
        removeBtn.setOnClickListener(this);
        removeBtn.setTag(view.getTag());
        mRemoveTagFromSearchPopup.setOutsideTouchable(true); // so it dismisses itself when something else is touched
        mRemoveTagFromSearchPopup.showAsDropDown(view);
    }

    private void removeTagFromSearchButtonClicked(View view) {
        // clicked to remove as above
        if (view.getTag() != null) {
            //noinspection SuspiciousMethodCalls
            mSelectedTagKeys.remove(view.getTag());
            updateTagSearchToSelectedKeys(true);
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "onClick: seems that tap on button to remove tag, but none is stored ");
        }
        mRemoveTagFromSearchPopup.dismiss();
        mRemoveTagFromSearchPopup = null;
    }

    private void reactToSearchTagSelection(View view) {
        // only for the tags rows, should be a relative layout
        RelativeLayout layout = (RelativeLayout) view;
        TextView tagKey = (TextView) layout.findViewById(R.id.tag_key);

        CheckBox selectBox = (CheckBox) layout.findViewById(R.id.tag_checkbox);
        final String key = tagKey.getText().toString();
        final boolean prevSelected = mSelectedTagKeys.contains(key);
        selectBox.setChecked(!prevSelected);

        if (prevSelected) {
            mSelectedTagKeys.remove(key);
        }
        else {
            mSelectedTagKeys.add(key);
        }
        updateTagSearchToSelectedKeys(true);
    }

    private void updateTagSearchToSelectedKeys(boolean updateSettings) {
        // update the settings
        if (updateSettings) {
            mSettingsManager.setRhythmSearchTagKeys(mSelectedTagKeys);
        }

        // cause a reload of the rhythms
        getLoaderManager().restartLoader(LOADER_LIST_ADAPTER_ID, null, this); // let loader handle the changed query (not using filter)
    }

    static void closeOptionsCard(final ViewGroup card, ViewGroup parent) {

        View moreBtn = parent.findViewById(R.id.more_btn);
        moreBtn.setVisibility(View.VISIBLE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // get the center for the clipping circle
            float cx = moreBtn.getX() + moreBtn.getWidth() / 2;
            float cy = moreBtn.getY() + moreBtn.getHeight() / 2;

            // get the final radius for the clipping circle
            float initialRadius = (float) Math.hypot(cx, cy);

            // create the animation (the final radius is zero)
            Animator anim = ViewAnimationUtils.createCircularReveal(card, (int) cx, (int) cy, initialRadius, 0);

            // make the view invisible when the animation is done
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    card.setVisibility(View.GONE);
                }
            });

            anim.start();
        }
        else {
            card.setVisibility(View.GONE);
        }

    }

    private boolean doCardAction(View view) {

        ViewGroup card = (ViewGroup) view.getParent().getParent();
        ViewGroup layout = (ViewGroup) card.getParent(); // 3 levels = constraint/card/relative
        TextView keyVw = (TextView) layout.findViewById(R.id.external_key);
        final String key = keyVw.getText().toString();

        switch (view.getId()) {
            case R.id.open_rhythm_btn:
            case R.id.open_rhythm:
                openRhythmForKey(layout, key);
                break;

            case R.id.edit_rhythm_btn:
            case R.id.edit_rhythm:
                ((PlayRhythmsActivity)mActivity).startEditRhythmActivity(key, !listObscuresFab());
                break;

            case R.id.save_rhythm_btn:
            case R.id.save_rhythm:
                TextView nameVw = (TextView) layout.findViewById(R.id.rhythm_name);
                String name = nameVw.getText().toString();
                Intent intent = SetRhythmNameAndTagsActivity.newInstance(mActivity, key, name, false);
                startActivityForResult(intent, PlayRhythmsActivity.CHANGE_RHYTHM_DETAILS_ACTIVITY_REQ_CODE);
                return true; // don't close card

            case R.id.dupe_rhythm_btn:
            case R.id.dupe_rhythm:
                mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                        new ParameterizedCallback(false) {
                            @Override
                            public void run() {
                                mResourceManager.startTransaction();
                                Rhythm rhythm = mResourceManager.getLibrary().getRhythms().lookup(key);
                                new CloneRhythmCommand(rhythm).execute();
                            }
                        });
                return true; // don't close card

            case R.id.delete_rhythm_btn:
            case R.id.delete_rhythm:
                mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                        new ParameterizedCallback(false) {
                            @Override
                            public void run() {
                                mResourceManager.startTransaction();
                                Rhythm rhythm = mResourceManager.getLibrary().getRhythms().lookup(key);
                                new RhythmCommandAdaptor.RhythmDeleteCommand(rhythm).execute();
                            }
                        });
                return true; // don't close card

            default:
                return false;
        }

        closeOptionsCard(card, layout);
        return true;
    }

    /**
     * Called when card option to edit the rhythm is selected
     * @return true if the list currently obsures the fab (completely)
     */
    private boolean listObscuresFab() {
        View baseView = getTrackView();
        return baseView.getRight() >= ((PlayRhythmsActivity) mActivity).getFabXPosition();
    }

    private void openRhythmForKey(ViewGroup layout, String key) {
        Intent intent = PlayRhythmsActivity.getIntentToOpenRhythm(mActivity, key);

        ImageView rhythmImg = (ImageView) layout.findViewById(R.id.rhythm_img);
        TextView rhythmName = (TextView) layout.findViewById(R.id.rhythm_name);

        int[] coords = new int[2];
        rhythmImg.getLocationOnScreen(coords);
        int[] rhythmImgCoords = new int[] { coords[0], coords[1], rhythmImg.getWidth(), rhythmImg.getHeight() };

        intent.putExtra(RHYTHMS_LIST_IMAGE_COORDS, rhythmImgCoords);

        rhythmName.getLocationOnScreen(coords);
        int[] rhythmNameCoords = new int[] { coords[0], coords[1], rhythmName.getWidth(), rhythmName.getHeight() };
        intent.putExtra(RHYTHMS_LIST_NAME_COORDS, rhythmNameCoords);

        startActivity(intent);
    }

    private void expandCardOptions(View moreBtn) {
        ViewGroup parent = (ViewGroup) moreBtn.getParent();
        View card = parent.findViewById(R.id.options_card);
        if (card == null) {
            AndroidResourceManager.loge(LOG_TAG, "onClick: missing options card for "+ moreBtn);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // get the center for the clipping circle
            float cx = moreBtn.getX() + moreBtn.getWidth() / 2;
            float cy = moreBtn.getY() + moreBtn.getHeight() / 2;

            // get the final radius for the clipping circle
            float finalRadius = (float) Math.hypot(cx, cy);

            // create the animator for this view (the start radius is zero)
            Animator anim = ViewAnimationUtils.createCircularReveal(card, (int)cx, (int)cy, 0, finalRadius);
            anim.start();
        }

        moreBtn.setVisibility(View.INVISIBLE);
        card.setVisibility(View.VISIBLE);
        card.addOnLayoutChangeListener(this);

        // need to listen on all the card options
        card.findViewById(R.id.open_rhythm_btn).setOnClickListener(this);
        card.findViewById(R.id.open_rhythm).setOnClickListener(this);
        card.findViewById(R.id.edit_rhythm_btn).setOnClickListener(this);
        card.findViewById(R.id.edit_rhythm).setOnClickListener(this);
        card.findViewById(R.id.save_rhythm_btn).setOnClickListener(this);
        card.findViewById(R.id.save_rhythm).setOnClickListener(this);
        card.findViewById(R.id.dupe_rhythm_btn).setOnClickListener(this);
        card.findViewById(R.id.dupe_rhythm).setOnClickListener(this);
        card.findViewById(R.id.delete_rhythm_btn).setOnClickListener(this);
        card.findViewById(R.id.delete_rhythm).setOnClickListener(this);
    }

    // ------------------------ Search text TextWatcher methods

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
    @Override
    public void afterTextChanged(Editable editable) {}

    @Override
    public void onTextChanged(CharSequence str, int start, int before, int count) {
        mSettingsManager.setRhythmSearchCriteria(str.toString(), null);
        AndroidResourceManager.logd(LOG_TAG, "onTextChanged: restartLoader text="+str);
        if (mAdapter != null) {
            getLoaderManager().restartLoader(0, null, this); // let loader handle the changed query (not using filter)
        }
    }

    /**
     * If there's not enough room to show the whole card, scroll the dialog_simple_list up to show it all
     * Only works >= kitkat
     */
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

        if (v.getId() != R.id.options_card) {
            AndroidResourceManager.logw(LOG_TAG, "onLayoutChange: on unknown view = "+v);
            return;
        }

        scrollListToShowCompleteCard(getListView(), v, getResources());
    }

    protected static void scrollListToShowCompleteCard(ListView listView, View v, Resources res) {
        // make sure the whole card is visible (in case the row was near the bottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            int[] coords = new int[2];
            v.getLocationOnScreen(coords);
            int screenHeight = res.getDisplayMetrics().heightPixels;
            int viewY = coords[1];
            int dist = v.getHeight() + viewY - screenHeight; // 2nd element = y

            if (dist > 0) {
                // it's a candidate for scrolling into view, but not if that would make the top disappear
                listView.getLocationOnScreen(coords);
                int listY = coords[1];
                int viewOffsetY = viewY - listY;

                if (viewOffsetY < dist) {
                    AndroidResourceManager.logd(LOG_TAG, ": can't scroll to bottom of card, only as far as top doesn't disappear");
                    dist = viewOffsetY;
                }

//                AndroidResourceManager.logw(LOG_TAG, String.format("expandCardOptions: scroll up loc=%s, height=%s, screen=%s, dist=%s"
//                        , coords[1], v.getHeight(), screenHeight, dist));

                listView.scrollListBy(dist);
            }
        }
    }

    /**
     * Called when the activity gets onBackPressed()
     * If there's a popup showing remove it, otherwise close the dialog_simple_list
     */
    void handleBackPressed() {
        boolean handled = false;

        if (mRemoveTagFromSearchPopup != null) {
            if (mRemoveTagFromSearchPopup.isShowing()) {
                mRemoveTagFromSearchPopup.dismiss();
                handled = true;
            }
            mRemoveTagFromSearchPopup = null;
        }

        if (!handled) {
            showClosed(true);
        }
    }

    /**
     * Called from PlayRhythmsActivity menu option, export the current list to file
     */
    public void exportCurrentList() {

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {

                        try {
                            ArrayList<Rhythm> rhythmsList = new ArrayList<>();
                            RhythmsContentProvider rhythms = (RhythmsContentProvider) mResourceManager.getLibrary().getRhythms();

                            Cursor csr = mAdapter.getCursor();

                            csr.moveToFirst();
                            while (!csr.isAfterLast()) {
                                rhythmsList.add(rhythms.lookup(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY))));
                                csr.moveToNext();
                            }

                            RhythmSharer sharer = new RhythmSharer(mResourceManager, rhythmsList, "export from android rhythms list");
                            final String fileName = String.format("kyb rhythms %s."+ RhythmImporter.RHYTHMS_FILE_EXTENSION, sharer.getTimestamp());

                            // open a file, pass to the sharer to output to
                            File file = new File(mActivity.getFilesDir(), fileName);
                            AndroidResourceManager.logd(LOG_TAG, "exportCurrentList: write list to file = "+file.getAbsolutePath());

                            sharer.writeToFile(file);

                            return fileName;
                        }
                        catch (Exception e) {
                            AndroidResourceManager.loge(LOG_TAG, "exportCurrentList: exception from sharer", e);
                            return e;
                        }
                    }
                }
                , new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        if (mParam instanceof String) {

                            Toast.makeText(mActivity, "Export success, file = "+mParam, Toast.LENGTH_LONG).show();
                        }
                        else {
                            if (mParam == null) {
                                Toast.makeText(mActivity, "Export failed, see log file for reason", Toast.LENGTH_LONG).show();
                            }
                            else {
                                Toast.makeText(mActivity, "Export failed, see log file for details, reason="+mParam.toString(), Toast.LENGTH_LONG).show();
                            }
                            AndroidResourceManager.loge(LOG_TAG, "exportCurrentList: failed to export, reason="+mParam);
                        }
                    }
                });
    }
}
