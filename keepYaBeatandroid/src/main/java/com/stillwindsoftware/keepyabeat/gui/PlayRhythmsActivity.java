package com.stillwindsoftware.keepyabeat.gui;

import java.util.HashSet;
import java.util.Iterator;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.BuildConfig;
import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.DataLoaderFragment;
import com.stillwindsoftware.keepyabeat.android.DataLoaderFragment.ProgressListener;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader.LoadProblem;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation.Key;

/**
 * Plays rhythms, the usual entry point for the app. At load time, a fragment is
 * used to load the data. - The same fragment may also need to install on first
 * run. The user might select to show the welcome screen on startup, and can
 * toggle that from the settings/preferences screen. - The welcome screen is
 * always shown on first run. If not showing the welcome screen it still might
 * be necessary to show a loading progress indicator as the data is read from
 * the database and a rhythm is displayed.
 */
public class PlayRhythmsActivity extends KybActivity implements ProgressListener {

	private static final String TAG_DATA_LOADER = "dataLoader";
	private static final String LOG_TAG = "KYB-"+PlayRhythmsActivity.class.getSimpleName();

	public static final String OPEN_RHYTHMS_LIST_PARAM = "openRhythmsList";
    public static final int CHANGE_RHYTHM_DETAILS_ACTIVITY_REQ_CODE = 1000;
    static final String OPEN_ANIMATE_FAB = "openAnimateFab";
    static final String FAB_X_POS = "fabXPos";
    static final String FAB_Y_POS = "fabYPos";

    // fragment and items for loading/install
	private DataLoaderFragment mDataLoaderFragment;
	private ProgressBar mProgressBar;
	private TextView mLoadingStatusText;

    // keep a ref to the new rhythm menu item so it can easily be enabled/disabled
    private MenuItem mNewRhythmMenuItem;
    private RhythmsListFragment mRhythmsListFragment;

    private View mToolbarCustomView;

    /**
	 * Cause data to be loaded (also installed on first run, or upgrade) using
	 * DataLoaderFragment
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// start loading data if needed
		boolean dataLoadNeeded = initDataLoader();

        boolean loadPlayer = dataLoadNeeded || !isEditorActive();

        if (loadPlayer) {
            divertToWelcomeScreen(); // if needed
            initAsPlayer(dataLoadNeeded);
            initAds();
        }
        else {
            startEditRhythmActivity(null, false); // not from the menu, don't animate the fab
        }
	}

    private boolean isEditorActive() {
        SettingsManager sm = (SettingsManager) mResourceManager.getPersistentStatesManager();
        return sm.isEditorActive();
    }

    private void initAsPlayer(boolean dataLoadNeeded) {
        setContentView(R.layout.initial_layout);

        initPlayerEditorToolbar();

        mProgressBar = findViewById(R.id.progress_bar);
        mLoadingStatusText = findViewById(R.id.load_status);

        FragmentManager fm = initPlayerEditorFragments();
        mRhythmsListFragment = (RhythmsListFragment) fm.findFragmentById(R.id.rhythms_list_fragment);

        // show first fragment only if no data is needed to be loaded (because
        // it's already doing it)
        // otherwise wait for it (update will come from setLoadStep())
        if (!dataLoadNeeded) {
            showInitialFragment(fm);
        }
    }

	/**
	 * Called when this activity receives a new intent, either in fore or background.
	 * Use to receive data, perhaps when user presses Undo and it needs to show a rhythm
	 * to show the undo.
	 * Note: launchMode is set to singleTask in the manifest to ensure correct behaviour.
	 * 
	 * The showInitialFragment() call will also test the intent by calling checkIntentForRhythm()
	 * in case the activity is restarted too.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		setIntent(intent);              // w/o this line double-click on a rhythm in dialog_simple_list fails w/ no tracked view error
		
		// reload player fragment
		final String rhythmKey = checkIntentForRhythm();
		if (rhythmKey != null) {
			
			// probably the dialog_simple_list is open, if so and it should close, close it
            if (mRhythmsListFragment.isListStateOpen()
                    && !getResources().getBoolean(R.bool.rhythms_list_coexists)) {
                mRhythmsListFragment.showClosed(true);
            }

            if (mRhythmBeatTypesFragment.isListStateOpen()) {
                mRhythmBeatTypesFragment.showClosed(true);
            }

            // look for transition elements on the intent, remove them if there
            final int[] rhythmImgCoords = intent.getIntArrayExtra(RhythmsListFragment.RHYTHMS_LIST_IMAGE_COORDS);
            final int[] rhythmNameCoords = intent.getIntArrayExtra(RhythmsListFragment.RHYTHMS_LIST_NAME_COORDS);
			if (rhythmImgCoords != null)
                intent.removeExtra(RhythmsListFragment.RHYTHMS_LIST_IMAGE_COORDS);
            if (rhythmNameCoords != null)
                intent.removeExtra(RhythmsListFragment.RHYTHMS_LIST_NAME_COORDS);

			if (mPlayRhythmFragment != null) {
                mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(

                        // background work to test for edit rhythm and do other stuff that involves db accesses
                        new ParameterizedCallback(true) {
                            @Override
                            public Object runForResult() {
                                Library library = mResourceManager.getLibrary();
                                RhythmsContentProvider rhythms = (RhythmsContentProvider) library.getRhythms();
                                mResourceManager.startTransaction();
                                final RhythmSqlImpl rhythm = rhythms.resolveRhythmKey(rhythmKey, BeatTree.PLAYED, true);
                                mPlayRhythmFragment.swapRhythm(rhythm);
                                mResourceManager.saveTransaction();
                                return rhythm;
                            }
                        },
                        new ParameterizedCallback(false) {
                            @Override
                            public void run() {
                                setNewRhythmMenuItem((RhythmSqlImpl) mParam);
                                if (rhythmImgCoords != null && rhythmNameCoords != null && mPlayRhythmFragment != null) {
                                    mPlayRhythmFragment.transitionInImageAndName(rhythmImgCoords, rhythmNameCoords);
                                }
                            }
                        }
                );
            }

		}
	}

    void setNewRhythmMenuItem(RhythmSqlImpl rhythm) {
	    if (mNewRhythmMenuItem == null) {
	        return; // not ready yet
        }

	    boolean exists = rhythm == null || rhythm.existsInDb(); // null should never happen, but if it did the new menu item would be enabled

        // the new rhythm menu item should be enabled once an existing rhythm is showing
        mNewRhythmMenuItem.setEnabled(exists);
        //noinspection Range
        mNewRhythmMenuItem.getIcon().setAlpha(exists ? 255 : getResources().getInteger(R.integer.disabled_alpha));
    }

    @Override
    protected void onStop() {
        setNewMenuItemFullAlpha();
        super.onStop();
    }

    /**
     * When might go to another activity call this as it affects the icon for editor and for beats and sounds lists
     */
    private void setNewMenuItemFullAlpha() {
        if (mNewRhythmMenuItem != null) {
            mNewRhythmMenuItem.getIcon().setAlpha(255);
        }
    }

    /**
	 * Factory method to return an intent that will open this activity with a 
	 * rhythm key
	 * @param key
	 * @return
	 */
	public static Intent getIntentToOpenRhythm(Context context, String key) {
		Intent intent = new Intent(context, PlayRhythmsActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(OPEN_WITH_RHYTHM_KEY_PARAM, key);
		return intent;
	}

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.play_rhythms_menu, menu);
		initCommonMenuItems(menu);
        initNewRhythmMenuItem(menu);
		return true;
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        // the toolbar is initialized after the rhythms dialog_simple_list fragment is created (it seems)
        // so detect if have to close it, but as nothing is laid out yet, just hide it
        initToolbarCustomView();

        MenuItem exportLibrary = menu.findItem(R.id.menu_library_export);
        if (exportLibrary != null) {
            exportLibrary.setVisible(BuildConfig.DEBUG);
        }

        //MenuItem mSearchAction = menu.findItem(R.id.menu_rhythms_list);
        return super.onPrepareOptionsMenu(menu);
    }

    private void initNewRhythmMenuItem(Menu menu) {
        mNewRhythmMenuItem = menu.findItem(R.id.menu_new_rhythm);
        setNewRhythmMenuItem((RhythmSqlImpl) getRhythm());
    }

    @Override
	public boolean onOptionsItemSelected(final MenuItem item) {

		// if a subzone is active (open) and the user presses a menu item, the way to
		// let the player framework know is to tell it where the screen coordinates are.
		// these are approximate though, so keep x as before, but set y to where 
		// actionbar is
		mGuiManager.setLastKnownPointerLocation(mLastTouchX, getSupportActionBar().getHeight());

        int itemId = item.getItemId();

        boolean isRhythmsListOpen = mRhythmsListFragment.isListStateOpen();

        // close list or undo while the list is open first
        if (isRhythmsListOpen
                && itemId != R.id.menu_undo
                && itemId != R.id.menu_rhythms_list
                && itemId != R.id.menu_help
                && itemId != R.id.menu_library_export) {
            mRhythmsListFragment.showClosed(true);
        }
        else if (mRhythmBeatTypesFragment.isListStateOpen()
                && itemId != R.id.menu_undo
                && itemId != R.id.menu_rhythm_beat_types) {
            mRhythmBeatTypesFragment.showClosed(true);

//            if (itemId != R.id.menu_new_rhythm) {       // allow new rhythm, just close the rbt
//                return true;
//            }
        }

        switch (itemId) {
            case R.id.menu_rhythms_list:
                toggleRhythmsList();
                return true;
            case R.id.menu_rhythm_beat_types:
                toggleRhythmBeatTypes();
                return true;
            case R.id.menu_beats_sounds:
                setNewMenuItemFullAlpha();
                Intent i = new Intent(this, BeatsAndSoundsActivity.class);
                startActivity(i);
                return true;
            case R.id.menu_new_rhythm:
                item.setEnabled(false);
                mNewRhythmMenuItem.getIcon().setAlpha(getResources().getInteger(R.integer.disabled_alpha));
                createAndOpenNewRhythm();
                return true;
/*
            case R.id.menu_save_rhythm:
                showChangeNameAndTagsDialog();
                return true;
*/
            case R.id.menu_help:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                int link = isRhythmsListOpen ? R.string.rhythmsListLink : R.string.rhythmPlayerLink;
                intent.setData(Uri.parse(getString(link)));
                startActivity(intent);
                return true;
            case R.id.menu_library_export:
                if (isRhythmsListOpen) {
                    mRhythmsListFragment.exportCurrentList();
                }
                else {
                    Toast.makeText(this, "Open rhythms list to export current selection", Toast.LENGTH_LONG).show();
                }
                return true;
		}
		return super.onOptionsItemSelected(item);
	}

    /**
     * Open the rhythms dialog_simple_list if not currently open, or close if not
     */
    public final void toggleRhythmsList() {
        if (mRhythmsListFragment.isListStateOpen()) {
            mRhythmsListFragment.showClosed(true);
        }
        else {
            mRhythmsListFragment.showOpen(true);
        }
    }

    private void initToolbarCustomView() {
        ActionBar toolbar = getSupportActionBar();
        //noinspection ConstantConditions
        toolbar.setCustomView(R.layout.rhythms_search_bar);
        toolbar.setDisplayShowCustomEnabled(true);
        mToolbarCustomView = toolbar.getCustomView();
        setOnTouchOnToolbarParentView(mToolbarCustomView.getParent());
        mToolbarCustomView.findViewById(R.id.close_search_btn).setOnClickListener(mRhythmsListFragment);

        if (!mRhythmsListFragment.isListStateOpen()) {
            mToolbarCustomView.setVisibility(View.INVISIBLE);
        }

        // by default the search items are visible
        SettingsManager settingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager();
        TextView searchText = (TextView) mToolbarCustomView.findViewById(R.id.searchText);

        if (!settingsManager.readStateFromStore(RhythmsListFragment.SETTING_RHYTHM_SEARCH_OPEN, false)) {
            searchText.setVisibility(View.GONE);
        }

        // add this as a text watcher so the filter can be changed as it is modified
        searchText.addTextChangedListener(mRhythmsListFragment);

        String text = settingsManager.getRhythmSearchText();
        if (text != null && !text.isEmpty())
            searchText.setText(text);

    }

    /**
     * Called from the rhythms dialog_simple_list open/close/follow drag methods.
     * During onPrepareOptionsMenu() it may have been hidden if the rhythms dialog_simple_list was closed,
     * so make it visible here if needed
     * @return
     */
    View getToolbarCustomView() {
        if (mToolbarCustomView != null && mToolbarCustomView.getVisibility() != View.VISIBLE) {
            mToolbarCustomView.setVisibility(View.VISIBLE);
        }
        return mToolbarCustomView;
    }

    /**
     * Launched by the button in the title bar via the fragment
     */
    void showChangeNameAndTagsDialog() {
        RhythmSqlImpl rhythm = (RhythmSqlImpl) getRhythm();
        final boolean isNew = !rhythm.existsInDb();
        final String name = isNew ? null : rhythm.getName();
        Intent intent = SetRhythmNameAndTagsActivity.newInstance(this, rhythm.getKey(), name, isNew);
        startActivityForResult(intent, CHANGE_RHYTHM_DETAILS_ACTIVITY_REQ_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CHANGE_RHYTHM_DETAILS_ACTIVITY_REQ_CODE) {

            if (resultCode == RESULT_CANCELED) {
                mGuiManager.showAlertMessage(this,
                        getString(R.string.unexpectedErrorTitle),
                        getString(R.string.error_calling_change_details),
                        "Error from SetRhythmNameAndTagsActivity unable to load a rhythm");
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Called from menu, from rhythmsList pop-up and from showInitial fragment when there's an edit rhythm found on the db
     * @param forRhythmKey
     */
    void startEditRhythmActivity(String forRhythmKey, boolean isFabVisible) {
        if (mPlayRhythmFragment != null && mPlayRhythmFragment.isPlaying()) {
            mPlayRhythmFragment.stopPlaying();
        }

        Intent ei = new Intent(this, EditRhythmActivity.class);
        if (forRhythmKey != null) {
            ei.putExtra(OPEN_WITH_RHYTHM_KEY_PARAM, forRhythmKey);
        }

        if (isFabVisible) {
            ei.putExtra(OPEN_ANIMATE_FAB, isFabVisible);
            View fabBtn = mPlayRhythmFragment != null ? mPlayRhythmFragment.getFab1() : null;

            if (fabBtn != null) {
                int[] coords = new int[2];
                fabBtn.getLocationOnScreen(coords);
                ei.putExtra(FAB_X_POS, coords[0]);
                ei.putExtra(FAB_Y_POS, coords[1]);
//                AndroidResourceManager.logw(LOG_TAG, String.format("startEditRhythmActivity: check animate the fab %s, %s, %s", isFabVisible, coords[0], coords[1]));
            }
        }

        // these flags don't seem to work on their own, so need call finish afterwards to replace this activity
        ei.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(ei);
        finish();
    }

    /**
     * Called from the new rhythm menu item
     */
    private void createAndOpenNewRhythm() {
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(new ParameterizedCallback(true) {
            @Override
            public Object runForResult() {
                mResourceManager.startTransaction();
                final RhythmsContentProvider rhythms = (RhythmsContentProvider) mResourceManager.getLibrary().getRhythms();
                RhythmSqlImpl rhythm = rhythms.makeNewRhythmAndSave(Function.getBlankContext(mResourceManager), BeatTree.PLAYED, true);
                mResourceManager.saveTransaction();
                return rhythm.getKey();
            }
        }, new ParameterizedCallback(false) {
            @Override
            public void run() {
                startActivity(getIntentToOpenRhythm(PlayRhythmsActivity.this, (String) mParam));
            }
        });
    }

    /**
	 * Look for an event listener to handle the back press
	 * if it handles it, don't pass it up to the kybActivity
	 */
	@Override
	public void onBackPressed() {
        if (mRhythmsListFragment.isListStateOpen()) { // close open dialog_simple_list
            mRhythmsListFragment.handleBackPressed();
            return;
        }

        if (mRhythmBeatTypesFragment.isListStateOpen()) { // close open dialog_simple_list
            mRhythmBeatTypesFragment.showClosed(true);
            return;
        }

        if (handleCloseExpandedTitleBar()) { // title bar first before anything else is considered
            return;
        }

		if (mPlayRhythmFragment != null
				&& mPlayRhythmFragment.mEventListener != null
				&& mPlayRhythmFragment.mEventListener.handleBack()) {

            if (mPlayRhythmFragment != null && mPlayRhythmFragment.mPlayRhythmView != null) {
                mPlayRhythmFragment.mPlayRhythmView.invalidate(); // want it to redraw
            }

			return;
		}
		else {
			super.onBackPressed();
		}
	}

    @Override
    protected boolean doTouchMove(MotionEvent ev) {
        if (mRhythmsListFragment.equals(mTrackedFragment)) {
            followTouchLimitRightMovement(ev);
            return true;
        }
        else if (super.doTouchMove(ev)) {
            return true; // superclass tests for dragging beat types otherwise returns false
        }
        else {
            return false;
        }
    }

    @Override
    protected boolean doTouchStopGestures(MotionEvent event) {

        if (super.doTouchStopGestures(event)) {
            return true; // super class checks for rbt
        }

        if (mRhythmsListFragment.equals(mTrackedFragment)) {
            mRhythmsListFragment.stopDragging();
            return true;
        }

        return false;
    }

    /**
     * Call from doTouchMove() to have the tracked view follow the movement
     * but limit the movement to the right so it does not go beyond the
     * origin. Meaning only movement to the left and then back right is allowed.
     * Specifically for rhythmsList, if not tracking that warn and call super method
     * @param ev
     */
    protected void followTouchLimitRightMovement(MotionEvent ev) {

        if (!mRhythmsListFragment.getTrackView().equals(mTrackedView)) {
            AndroidResourceManager.logw(LOG_TAG, "followTouchLimitRightMovement: unknown view, ("+mTrackedView+")");
            return;
        }

        // place the view relative to the touch

        // get the screen relative distance from the touch to the left of the view
        // as set from the initial touch (to max width - margin)
        float screenRelativeX = mLastTouchX - mTouchRelativeToViewHorizontal;
        // subtract from that the placement of the view on the screen plus the translation
        // (to get its parent relative placement)
        float left = mTrackedView.getLeft();
        float transX = mTrackedView.getTranslationX();
        float viewLeftRelativeToScreenX = transX - mTrackedViewScreenCoords[0] + left;
        float parentRelativeX = screenRelativeX - viewLeftRelativeToScreenX;

        // find the new position for the view based on keeping the relation
        // with the touch to the left side, but don't allow it to go beyond
        // the right limit
        float desiredX =
                Math.min(
                        // left is the getX() layout position which stays the same regardless of translation
                        left
                        // eg. if left = 0, the movement will never advance past 0
                        , parentRelativeX);

        mRhythmsListFragment.followDrag(desiredX);
    }

    /**
	 * Look for chance to pop out fragments from the edge
	 */
	@Override
	protected boolean doEdgeDiscovery(MotionEvent event) {

        boolean found = super.doEdgeDiscovery(event);

        if (!found) {
            if (!mRhythmsListFragment.isListStateOpen()
                    && mLastTouchX <= mTouchMarginToEdge + mScaledEdgeSlop) {       // left side
                mRhythmsListFragment.prepareForOpening();
                hookupEdgeDiscovery(RHYTHMS_LIST_FRAG_TAG, mRhythmsListFragment, mRhythmsListFragment.getTrackView());
                found = true;
            }
        }

        return found;
	}
	
	/**
	 * initial set up
	 */
	private void showInitialFragment(FragmentManager fm) {

		// hide the loading status views
		if (mProgressBar != null) {
			mProgressBar.setVisibility(View.GONE);
		}
		if (mLoadingStatusText != null) {
			mLoadingStatusText.setVisibility(View.GONE);
		}

        mPlayRhythmFragment.initData(mRhythmPlayingHolder);
	}

    @Override
    public void dataLoaded() {

        // called during showInitialFragment() directly if the data holder fragment is already ready with rhythm and draughter
        // already prepared (which is completed in the ui thread)
        // but the first time, there'll be no data holder fragment and its initialization calls this
        // method when it's done (which happens in a background thread)

        // the 2 calls *should* never overlap because the call in showInitialFragment() only happens if the data holder is prepared
        // and the call from the data fragment onAttach() only happens if the data holder has not previously been prepared

        if (mGuiManager.isUiThread()) {
            // check for the play rhythm fragment ready, if so can go ahead and reopen tags
            initRhythmFragments();
        }
        else {
            mGuiManager.runInGuiThread(new Runnable() {
                @Override
                public void run() {
                    // check for the play rhythm fragment ready, if so can go ahead and reopen tags
                    initRhythmFragments();
                }
            });
        }
    }

    void initRhythmFragments() {
        // complete the set up of the rbt if it's waiting
        if (mRhythmBeatTypesFragment != null && !mRhythmBeatTypesFragment.hasListAdapter()) {
            mRhythmBeatTypesFragment.initListAdapter();
        }
    }

    @Override
    protected boolean handleTapOffTrackableViews() {

        if (!super.handleTapOffTrackableViews()) {
            return handleCloseExpandedTitleBar();
        }

        return false;
    }

    /**
     * Called from handleTapOffTrackableViews() if no other view handles it first
     * and popStackedFragment() [itself onBackPressed()] likewise so the title bar is closed
     * @return
     */
    private boolean handleCloseExpandedTitleBar() {
        if (mPlayRhythmFragment != null && mPlayRhythmFragment.isTitleBarExpanded()) {
            mPlayRhythmFragment.toggleTitleLayoutExpanded();
            return true;
        }
        return false;
    }

    /**
	 * Get an update of load status in case the async task completed while this was not in the foreground.
	 * After that is completed, perhaps there are errors to feedback to the user.
	 * If RhythmsList is open it may be necessary to recreate the fragment (after config re-create)
	 */
	@Override
	protected void onResume() {

		super.onResume();

        // when complete the data loader fragment is set to null, its existence flags something still to do
		if (mDataLoaderFragment != null) {
			setLoadStep(mDataLoaderFragment.getCurrentLoadStep());
		}
		else {
			// done with loading, but there could be feedback for the user, this is handled in
			// setLoadStep() when complete also
			LibraryXmlLoader libraryXmlLoader = mResourceManager.detachXmlLoader();
			if (libraryXmlLoader != null) {
				reportLoadErrors(mResourceManager, libraryXmlLoader);
 			}
		}
	}

    /**
	 * First time in there is no data loader, simply create one. If there is
	 * one, re-reference it.
	 * 
	 * @return true if new DataLoaderFragment is created or already running
	 */
	private boolean initDataLoader() {
		// done already this session, don't do again
        KybApplication application = (KybApplication) getApplication();
        if (application.isInitialised()) {
			return false;
		}

        AndroidResourceManager.logd(LOG_TAG, "initDataLoader: application has not been initialized, meaning either install or upgrade is happening");

		// not already done, which means it's either in progress or needs to be
		// started
		FragmentManager fm = getSupportFragmentManager();
		mDataLoaderFragment = (DataLoaderFragment) fm.findFragmentByTag(TAG_DATA_LOADER);
		
		if (mDataLoaderFragment == null) {
			mDataLoaderFragment = new DataLoaderFragment();
			mDataLoaderFragment.startLoading((KybApplication) getApplicationContext());
            fm.beginTransaction().add(mDataLoaderFragment, TAG_DATA_LOADER)
					.commit();
		}
		
		return true;
	}

	/**
	 * Called at the end of seedRhythmsData to report any errors to the user
	 * @param libraryXmlLoader 
	 */
	private void reportLoadErrors(AndroidResourceManager resourceManager, LibraryXmlLoader libraryXmlLoader) {
		if (libraryXmlLoader != null && libraryXmlLoader.getLoadProblemSeverity() > LibraryXmlLoader.ERROR_SEVERITY_NONE) {

			final String NEW_LINE = "\n";

			// create a set to remove duplicate messages
			HashSet<String> errors = new HashSet<String>();
			for (Iterator<LoadProblem> it = libraryXmlLoader.getLoadProblems(); it.hasNext();) {
				LoadProblem problem = it.next();
				Object[] params = problem.getParams();
				String message = resourceManager.getLocalisedString(problem.getCoreId(), params);
				errors.add(message);
			}

			StringBuilder sb = new StringBuilder();
			for (String error : errors) {
				sb.append(error);
				sb.append(NEW_LINE);
			}
			
			AndroidResourceManager.loge(LOG_TAG, String.format("reportLoadErrors: show alert for errors \n%s", sb.toString()));
			
			new AlertDialog.Builder(this)
				.setTitle(getResources().getString(R.string.titleLoadingProblems))
				.setMessage(sb.toString())
				.setPositiveButton(R.string.ok_button, null)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.create().show();
		}
	}

	// --------------------- ProgressListener methods

	@Override
	public void setLoadStep(int step) {
		// update the views to feedback to the user
		if (mProgressBar != null) {
            mProgressBar.setVisibility(View.VISIBLE);
			mProgressBar.setProgress(step);
		}
		
		if (mLoadingStatusText != null) {
			switch (step) {
			case DataLoaderFragment.CHECKING_INSTALLATION: mLoadingStatusText.setText(R.string.checking_install); break;
			case DataLoaderFragment.INSTALLING: mLoadingStatusText.setText(R.string.load_installing); break;
			case DataLoaderFragment.LOADING_DATA: mLoadingStatusText.setText(R.string.loading); break;
			case DataLoaderFragment.COMPLETED: mLoadingStatusText.setText(R.string.loading_complete); break;
			}
		}
		
		// if finished, clear down the data load fragment
		if (step == DataLoaderFragment.COMPLETED) {
            FragmentManager fm = getSupportFragmentManager();

			if (mDataLoaderFragment != null) {
				fm.beginTransaction().remove(mDataLoaderFragment).commit();
				mDataLoaderFragment = null;
			}

			// any problems, need to report them back
			AndroidResourceManager resourceManager = ((KybApplication)getApplication()).getResourceManager();
			LibraryXmlLoader libraryXmlLoader = resourceManager.detachXmlLoader();

			try {
				showInitialFragment(fm);
			} catch (Exception e) {
				AndroidResourceManager.loge(LOG_TAG, "setLoadStep:unexpected error", e);
				if (libraryXmlLoader != null) {
					libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE
							, Key.UNEXPECTED_PROGRAM_ERROR, (String[])null);
				}
			}
			
			if (libraryXmlLoader != null) {
				reportLoadErrors(resourceManager, libraryXmlLoader);
			}
		}
	}

    /**
     * Gets the x position of the fab button (used by rhythmsList when opening Edit to see if the button is obscured by the list)
     * @return the x pos if there's a button, else positive inf
     */
    public float getFabXPosition() {
        View fab = mPlayRhythmFragment.getFab1();
        if (fab != null) {
            return fab.getX();
        }
        else {
            return Float.POSITIVE_INFINITY;
        }
    }

}
