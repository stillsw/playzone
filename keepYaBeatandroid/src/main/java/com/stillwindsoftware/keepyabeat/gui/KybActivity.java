package com.stillwindsoftware.keepyabeat.gui;

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.ads.consent.ConsentForm;
import com.google.ads.consent.ConsentFormListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.stillwindsoftware.keepyabeat.BuildConfig;
import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;

/**
 * Common superclass for most Kyb activities.
 * 1) Handles language code manual setting and detection of activity open with message receiving.
 * 		Sub classes must implement:
 *	 		getReceiverFilterName()
 *		And to react to params that may be broadcast (receive messages):
 * 			handleBroadcastParams()
 * 2) Common code for gesture swiping/flinging and dragging
 * 		Sub class the following methods to implement:
 * 			onWindowFocusChanged() - to initTouchCapabilities (see PlayRhythmsActivity for example)
 * 			doTouchStartGestures() - standard code to start drag/move functionality
 * 							shouldn't need to be overridden, rather override getDragStartView() if needed
 * 			doTouchMoveAllVersions() - override to handle, for instance for rhythm editing
 * 			doTouchMoveHoneycomb() - depending on what a move should do for the tracked view/fragment
 * 							Either create one off needs, or make a new method and add to this
 * 							class for reuse.
 * 							Methods so far available:
 * 								followTouchLimitLeftMovement()
 * 								followTouchLimitRightMovement()
 * 			doTouchStopAllVersions() - override to handle, for instance for rhythm editing
 * 			doTouchStopGestures() - override
 * 							Either create one off needs, or make a new method and add to this
 * 							class for reuse.
 * 							Methods so far available:
 * 								animateToOriginOrPopOffRightSlide()
 * 			handleTapOffTrackableViews() - handle if dark layer will be partially visible and need to react to it
 * 							or want to react to other touches.
 * 			doEdgeDiscovery() - override to test and react to touches near the edge that happen when there
 * 							is no other view being tracked.
 * 			hookupEdgeDiscovery() - attaches touch variables to the fragment/view that is discovered at the edge
 * 							or aborts (pops) the fragment if cancelled in the meantime (as it has to wait for animation)
 * 		Override isFilterOnlyIncludeFirstPointerDownEvents() to have > first touch down tracked
 *
 * @author tomas stubbs
 *
 */
public abstract class KybActivity extends AppCompatActivity implements Runnable, View.OnTouchListener { // runnable is to make ui changes

	private static final String LOG_TAG = "KYB-"+KybActivity.class.getSimpleName();

    public static final String PLAYER_NOTIFICATION_ACTION = "com.stillwindsoftware.keepyabeat.gui.PLAYER_OPEN";
    // params that could be passed in the registerKybReceiver flag value, see superclass
    public static final int RECEIVE_MESSAGE_FLAG_NOTIFICATION_ACTED_ON = 0;

    protected static final String RHYTHM_BEAT_TYPES_FRAG_TAG = "rbt dialog_simple_list frag";
    protected static final String RHYTHMS_LIST_FRAG_TAG = "rhythms dialog_simple_list frag";
    public static final String OPEN_WITH_RHYTHM_KEY_PARAM = "openWithRhythmkey";

    public static final String RECEIVE_MESSAGE_FLAG = "receiveMessageFlag";

	// DP constants, scaled by density (160 is 1" mdpi)
	protected static final int TOUCH_MARGIN_DP_TO_EDGE = 20; // 1/8 inch

    private static final String INTENT_EXTRA_START_TOAST_MSG_RES_ID = "startToastResId";

    protected static final String TEST_BANNER_ADUNIT = "ca-app-pub-3940256099942544/6300978111";
    protected static final String REAL_BANNER_ADUNIT = "ca-app-pub-1327712413378636/3192233576";
    private static final String KYB_DATA_CONSENT_POLICY_URL = "https://sites.google.com/site/keepyabeat/home/data-policy";

    private boolean mIsMenuInitialised = false; // to know if menu needs to be re-created when billing detects premium
    private boolean mShowThankYouForUpgrading = false; // set when updating UI from application after purchase made
    protected KybApplication mApplication;

    private boolean mRhythmJustStoppedPlaying = false; // set when resource manager receives that beat tracker stopped playing at end of iterations

	protected BroadcastReceiver mCommunicationReceiver;
	protected String mLangCode;

	// used to update last known location in onTouchEvent
	protected AndroidGuiManager mGuiManager;
    protected AndroidResourceManager mResourceManager;

	// constant value of non specified pointer id (or reset)
	protected static final int INVALID_POINTER_ID = -1;

	protected float mTouchMarginToEdge;

//	protected float mMinFlingDistance;
//	protected float mMinFlingVelocity;
	// for edge discovery
	int mScaledEdgeSlop, mWidthPixels;
	// edge discoveries are defaulted to happen
	protected int mEdgeDiscoveryPending;// = EDGE_DISCOVERY_READY;

	// used to track MotionEvents (for updating guiManager and for dragging/swiping)
	protected int mMotionEventsPointer = INVALID_POINTER_ID;
	protected float mLastTouchY;
	protected float mLastTouchX;
	protected View mTrackedView;
	// coordinates on the screen for the view that is tracked,
	// and updated on each down/move after that (if dragging is used)
	protected int[] mTrackedViewScreenCoords = new int[2];
	protected SwipeableListFragment mTrackedFragment;
	protected float mTouchRelativeToViewHorizontal;
	@SuppressWarnings("unused")
	private float mTouchRelativeToViewTop;

    // onResume() records the activity as it comes in as a weak ref in the resource manager
    // to make sure this ref isn't GC'd during the activity's foreground lifecycle
    // reference it here in itself
    private WeakReference<KybActivity> mOwnWeakRef;
    private MenuItem mUndoMenuItem;
    private Runnable mUndoCommandCallback;

    // rhythm playing data used for both PlayRhythmsActivity and EditRhythmActivity
    protected PlayRhythmFragment mPlayRhythmFragment;
    protected RhythmPlayingFragmentHolder mRhythmPlayingHolder;
    protected RhythmBeatTypesFragment mRhythmBeatTypesFragment;

    protected boolean mIsActive; // set onPause(), unset onResume(), to determine if the activity is actively in the foreground
    protected ArrayList<String> mOpenDialogs;

    // ads related
    protected boolean mIsShowingAnAd; // set in initAds
    private AdView mAdView;
    private boolean mIsAdViewInitialised;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        mApplication = (KybApplication) getApplication();
        mResourceManager = mApplication.getResourceManager();
		mGuiManager = (AndroidGuiManager) mResourceManager.getGuiManager();
		
		// set the locale to the saved value if required, store whatever the language code used is
		mLangCode = LocaleUtils.setActivityLocale(this);		
		super.onCreate(savedInstanceState);

        handleIntentMsg(getIntent());

        // for the activities that draw rhythms, want to know when there's a blocking dialog (to not react to touches)
        if (this instanceof EditRhythmActivity || this instanceof PlayRhythmsActivity) {
            mOpenDialogs = new ArrayList<String>();
        }

        // premium is set after first lookup on the billing service
        if (!mApplication.isPremium()
            || BuildConfig.ALLOW_CONSUME_PREMIUM) {     // only debug build allows this
            mApplication.listenForBillingChanges();
        }
	}

    /**
     * Called from PlayRhythmsActivity/EditRhythmActivity onCreate(), if premium has been purchased ads are disabled
     * otherwise load the first ad read to show
     */
    protected void initAds() {
        // ads
        if (mApplication.isPremium()) {
            AndroidResourceManager.logd(LOG_TAG, "initAds: premium, so no ads");
        }
        else {
            mIsShowingAnAd = true;
            FrameLayout adParent = findViewById(R.id.ad_view_parent);
            mAdView = new AdView(this);
            mAdView.setAdSize(AdSize.BANNER);
            mAdView.setAdUnitId(BuildConfig.SHOW_REAL_ADS
                    ? REAL_BANNER_ADUNIT
                    : TEST_BANNER_ADUNIT);
            adParent.addView(mAdView);
        }
    }

    void notifyDrawingStarted() { // placeholder for playRhythmView (or anything) to let the activity know it can do do other actions (see PlayRhythmsActivity)
        if (mIsShowingAnAd && mAdView != null) {

            if (mApplication.isAwaitingConsentInfoRequest()) {
                return; // can't yet do anything
            }

            // test have consent to show ads
            if (mApplication.hasConsentToShowAdsOrNotRequired()) {
                mAdView.loadAd(makeAdRequestBuilder().build());
                mIsAdViewInitialised = true;
            }
        }
    }

    /**
     * called by the play button press
     * @return true if the dialog is shown to request consent, in which case the button can decide whether play should be aborted
     */
    boolean isCheckingForConsentForAds() { //

        if (mIsShowingAnAd && !mIsAdViewInitialised && mAdView != null) {

            if (mApplication.isAwaitingConsentInfoRequest()) {
                return false; // can't yet do anything
            }

            if (mApplication.hasConsentToShowAdsOrNotRequired()) {               // won't need to do this again
                mAdView.loadAd(makeAdRequestBuilder().build());
                mIsAdViewInitialised = true;
                return false;
            }

            // consent not given, build dialog and return true that we're asking for it

            if (mResourceManager.hasAdsConsentIntervalElapsed()) {
                showAdsConsentForm();
                return true;
            }
        }

        return false; // either not showing ads anyway, or already handled
    }

    private ConsentForm mConsentForm;
    void showAdsConsentForm() {
        try {

            final URL policyURL = new URL(KYB_DATA_CONSENT_POLICY_URL);
            mConsentForm = new ConsentForm.Builder(this, policyURL)
                .withListener(new ConsentFormListener() {
                    @Override
                    public void onConsentFormLoaded() {
                        AndroidResourceManager.logd(LOG_TAG, String.format("showAdsConsentForm.onConsentFormLoaded: loaded successfully, attempt to show=%s", (mConsentForm != null)));
                        if (mConsentForm != null) { // an error will null it out
                            mConsentForm.show();
                            ((SettingsManager)mResourceManager.getPersistentStatesManager()).setAdConsentRequestedTime(System.currentTimeMillis());
                        }
                    }

                    @Override
                    public void onConsentFormOpened() {
                        // Consent form was displayed.
                    }

                    @Override
                    public void onConsentFormClosed(ConsentStatus consentStatus, Boolean userPrefersAdFree) {
                        boolean prevHadConsent = mApplication.hasConsentToShowAdsOrNotRequired();
                        mConsentForm = null;
                        AndroidResourceManager.logd(LOG_TAG, String.format("showAdsConsentForm.onConsentFormClosed: prevHadConsent=%s", prevHadConsent));

                        if (userPrefersAdFree) {                                    // user requests upgrade, cause the payment dialog to show
                            mApplication.sellPremium(KybActivity.this);
                        }

                        mApplication.handleConsentStatus(consentStatus); // regardless, as they may then cancel the payment dialog, and be downgrading their consent etc

                        if (mApplication.isPremium()) {         // nothing to do (unlikely this could be the case here)
                        }
                        else if (!prevHadConsent                // didn't have it before, but has now been given
                                && mApplication.hasConsentToShowAdsOrNotRequired()) {
                            // the case where consent is now given would mean ads should start showing
                            if (mAdView != null) { // not on settings
                                mAdView.loadAd(makeAdRequestBuilder().build());
                            }
                            mIsAdViewInitialised = true;
                            updateUI();                         // menu items may need update
                        }
                        else {
                            // where there were ads before and now there aren't (they've withdrawn consent)
                            mIsAdViewInitialised = false;       // cause the checking next time play is hit
                            if (findViewById(R.id.ad_view_parent) != null) {
                                stopAdsCompletely();            // removes the current adview
                                initAds();                      // creates a new adview where adrequest hasn't yet been called, ie. needs consent
                            }
                            updateUI();                         // menu items may need update
                        }
                    }

                    @Override
                    public void onConsentFormError(String errorDescription) {
                        AndroidResourceManager.loge(LOG_TAG, "showAdsConsentForm.onConsentFormError: not able to process form error="+errorDescription);
                        mConsentForm = null;
                    }
                })
                .withPersonalizedAdsOption()
                .withNonPersonalizedAdsOption()
                .withAdFreeOption()
                .build();

            mConsentForm.load();

        } catch (MalformedURLException e) {
            AndroidResourceManager.loge(LOG_TAG, "showAdsConsentForm: ", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntentMsg(intent);
    }

    public AndroidResourceManager getResourceManager() {
        return mResourceManager;
    }

    protected void handleIntentMsg(Intent intent) {
        if (intent.hasExtra(INTENT_EXTRA_START_TOAST_MSG_RES_ID)) {
            int msgResId = intent.getIntExtra(INTENT_EXTRA_START_TOAST_MSG_RES_ID, -1);
            if (msgResId != -1) {
                intent.removeExtra(INTENT_EXTRA_START_TOAST_MSG_RES_ID);
                Toast.makeText(this, getString(msgResId), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
	protected void onResume() {
		// set the locale to the saved value if required, store whatever the language code used is
		String langCode = LocaleUtils.setActivityLocale(this);
		if (!langCode.equals(mLangCode)) {
			AndroidResourceManager.logd(LOG_TAG, "onResume: langCode has changed, reloading activity");
			LocaleUtils.reload(this);
		}

		// keep the resource manager up to date with which activity is loaded
		// bit of a complication in that if there's a dialog activity over this one, it will already have set it, and don't want to unset it here
        // which would also be set to null afterwards in onPause(), so same check there
        if (!isDialogActivityInFrontOfThisActivity()) {
            mOwnWeakRef = mResourceManager.setLatestActivity(this);
        }
        mIsActive = true;

		super.onResume();

        // make sure undo menu item is up to date
        if (mUndoMenuItem != null) {
            final UndoableCommandController ucc = isAnEditorActivity()
                    ? ((AndroidGuiManager)mResourceManager.getGuiManager()).getRhythmEditorUndoableCommandController(true)
                    : mResourceManager.getUndoableCommandController();
            ucc.setStackChangeCallback(mUndoCommandCallback);
            updateUndoMenuItemEnabled(ucc);
        }

        // detect that premium has just been bought to remove existing ads
        if (mApplication.isPremium() && mIsShowingAnAd) {
            AndroidResourceManager.logd(LOG_TAG, "onResume: removing ads as premium is now bought");
            stopAdsCompletely();

            // check for showing a thank you alert for purchase
            if (mResourceManager.getApplication().takeAwaitingPurchaseThankYou()) {
                AndroidResourceManager.logd(LOG_TAG, "onResume: show thank you for purchase");
                showThankYouForUpgradingAlert();
            }
        }
	}

    private boolean isDialogActivityInFrontOfThisActivity() {
        KybActivity latestActivity = mResourceManager.getLatestActivity();
        return latestActivity != null
                && (this instanceof EditRhythmActivity
                        && (latestActivity instanceof CopyBeatDialogActivity        // the only dialog activities in the project so far
                            || latestActivity instanceof EditBeatDialogActivity));
    }

    /**
     * Only used by rhythms activities to detect a touch in the toolbar so can close an open sub-zone
     * Made final so any change to this strategy won't break it
     * @param v
     * @param event
     * @return
     */
    @Override
    public final boolean onTouch(View v, MotionEvent event) {
        return this.onTouchEvent(event); // pass on the toolbar's onTouch
    }

    /**
     * Sets up the view for the onTouch() to fire with
     * @param parent
     */
    protected void setOnTouchOnToolbarParentView(ViewParent parent) {
        if (parent != null && parent instanceof Toolbar) {
            ((Toolbar)parent).setOnTouchListener(this);
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "setOnTouchOnToolbarParentView: toolbar widget either not present, or not a toolbar view = " + parent);
        }
    }

    /**
	 * Called by subclasses when they init the menu in onCreateOptionsMenu().
	 * Grab the item for undo and set it up appropriately.
	 * Also puts a callback runnable on the undo stack to be able to update
	 * the undo button as there are changes to the stack, since this is happening
	 * during menu setup, in onCreate() the reverse of it happens in onDestroy(). If that doesn't
	 * get called, it's ok, because the menuitem is a weakReference, but trap any exceptions 
	 * anyway.
	 * @param menu
	 */
	protected void initCommonMenuItems(Menu menu) {
        mIsMenuInitialised = true;

        // undo menu item

		mUndoMenuItem = menu.findItem(R.id.menu_undo);
		
		// for edit rhythms use a different controller
		final UndoableCommandController ucc = isAnEditorActivity()
                        ? ((AndroidGuiManager)mResourceManager.getGuiManager()).getRhythmEditorUndoableCommandController(true)
                        : mResourceManager.getUndoableCommandController();
//        AndroidResourceManager.logd(LOG_TAG, "initCommonMenuItems: get undoable controller = "+ucc);

		if (mUndoMenuItem != null) {
			final WeakReference<MenuItem> weaKMi = new WeakReference<MenuItem>(mUndoMenuItem);

            mUndoCommandCallback = new Runnable() {
                @Override
                public void run() {
                    final MenuItem weakUndoMi = weaKMi.get();
                    if (weakUndoMi != null) {
                        mGuiManager.runInGuiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateUndoMenuItem(ucc, weakUndoMi, false);
                            }
                        });
                    }
                    else {
                        AndroidResourceManager.logw(LOG_TAG, "initCommonMenuItems: callback ran, but weak ref for menu item is gone");
                    }
                }
            };

			updateUndoMenuItem(ucc, mUndoMenuItem, true);
		}
		else {
			AndroidResourceManager.logd(LOG_TAG, "initCommonMenuItems: no undo menu item in menu");
		}
	}

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem testWarn = menu.findItem(R.id.menu_test_warn_message);
        if (testWarn != null) { // actually only exists on player
            testWarn.setVisible(BuildConfig.DEBUG); // don't show in release
        }

        MenuItem consume = menu.findItem(R.id.menu_consume_premium); // for testing only
        if (consume != null) { // actually only exists on player
            consume.setVisible(
                    BuildConfig.ALLOW_CONSUME_PREMIUM // ie. debug build
                            && mResourceManager.getApplication().isPremium());
        }

        MenuItem removeAdsMenuItem = menu.findItem(R.id.menu_remove_ads);
        if (removeAdsMenuItem != null) {
            removeAdsMenuItem.setVisible(!mResourceManager.getApplication().isPremium());
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "onPrepareOptionsMenu: no remove ads menu item in menu");
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @SuppressLint("InflateParams")
    private void updateUndoMenuItem(final UndoableCommandController ucc, final MenuItem undoMi, boolean isInit) {

        if (ucc == null) {
            AndroidResourceManager.logw(LOG_TAG, "updateUndoMenuItem: ucc is null, probably during init ("+isInit+") and out of scope now");
            return;
        }

        try {
            updateUndoMenuItemEnabled(ucc);
            ucc.setStackChangeCallback(mUndoCommandCallback);
            if (ucc.hasCommands()) {
                // not on init and only when an undo is command is added
                if (!isInit && ucc.wasLastActionAddition()) {
                    ImageButton undoVw = (ImageButton) getLayoutInflater().inflate(R.layout.undo_menu_button, null);
                    undoVw.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ucc.undoLastCommand();
                        }
                    });
                }
            }
        }
        catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "updateUndoMenuItem: failed, probably out of scope now", e);
        }
    }

    private void updateUndoMenuItemEnabled(UndoableCommandController ucc) {

        try {
            if (ucc != null && ucc.hasCommands()) {
                mUndoMenuItem.setEnabled(true);
                mUndoMenuItem.getIcon().setAlpha(255);
                mUndoMenuItem.setTitle(ucc.getUndoDesc());
            }
            else {
                mUndoMenuItem.setEnabled(false);
                mUndoMenuItem.getIcon().setAlpha(getResources().getInteger(R.integer.disabled_alpha));
                mUndoMenuItem.setTitle(R.string.NO_UNDO_DESC); // disabled so shouldn't ever see this
            }

        }
        catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "updateUndoMenuItemEnabled: failed, probably out of scope now", e);
        }
    }

    protected boolean divertToWelcomeScreen() {

        // Show welcome fragment (only if it hasn't been shown yet this session)
        SettingsManager sm = (SettingsManager) mResourceManager.getPersistentStatesManager();

        if (sm.isShowWelcomeDialog() && ((KybApplication) getApplication()).isShowWelcomeScreen()) {

            // don't show again this session
            ((KybApplication) getApplication()).setShowWelcomeScreenDone();

            Intent welcomeIntent = new Intent(this, WelcomeScreenActivity.class);
            startActivity(welcomeIntent);
            return true;
        }

        return false;
    }

    /**
	 * Each of the subclasses always call this superclass method if they don't
	 * already handle the action themselves, so go ahead and test for undo
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_undo :
            final UndoableCommandController ucc = this instanceof EditRhythmActivity
                    ? ((AndroidGuiManager)mResourceManager.getGuiManager()).getRhythmEditorUndoableCommandController(true)
                    : mResourceManager.getUndoableCommandController();
			if (ucc.hasCommands()) {
                String undoneText = ucc.getUndoDesc(true);
				ucc.undoLastCommand(); // will run callback that is init'd in initCommonMenuItems() above

                // feedback the undo happened
                Toast.makeText(this, undoneText, Toast.LENGTH_SHORT).show();
			}
			return true;

            case R.id.menu_tags:
                new TagsListDialog().show(getSupportFragmentManager(), TagsListDialog.LOG_TAG);
                return true;

            case R.id.menu_remove_ads:
                mApplication.sellPremium(this);
                return true;

            case R.id.menu_consume_premium:
                mApplication.consumePremium();
                return true;

            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.menu_test_warn_message:
                mResourceManager.getGuiManager().warnOnErrorMessage("show a test warning", "show a test warning", true, null);
                return true;
//            case R.id.menu_test_warn_not_ui_message:
//                mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(new ParameterizedCallback(false) {
//                                                    @Override
//                                                    public void run() {
//                                                        mResourceManager.getGuiManager().warnOnErrorMessage("show off UI test warning", "show a test warning", true, null);
//                                                    }
//                                                });
//                return true;

        }

		return super.onOptionsItemSelected(item);
	}

    /**
     * Initialise the non-UI fragment that holds data to survive config changes
     * @param fm
     */
    protected final void initRhythmPlayingHolder(FragmentManager fm) {

        mRhythmPlayingHolder = (RhythmPlayingFragmentHolder) fm.findFragmentByTag(RhythmPlayingFragmentHolder.LOG_TAG);

        if (mRhythmPlayingHolder == null) {
            mRhythmPlayingHolder = RhythmPlayingFragmentHolder.getInstance();

            fm.beginTransaction()
                    .add(mRhythmPlayingHolder, RhythmPlayingFragmentHolder.LOG_TAG)
                    .commit();

            fm.executePendingTransactions();
        }
    }

    /**
     * Called by onNewIntent(), and also from showInitialFragment()
     */
    String checkIntentForRhythm() {
        return getIntent().getStringExtra(OPEN_WITH_RHYTHM_KEY_PARAM);
    }

    /**
     * Set in onPause()/onResume(), tested by guiManager.isBlocking()
     * @return
     */
    public boolean isActive() {
        return mIsActive;
    }

    @Override
	protected void onPause() {
		// keep the resource manager up to date with which activity is loaded
        // bit of a complication in that if there's a dialog activity over this one, it will have set it, and don't want to unset it here
        // similar onResume()
        if (!isDialogActivityInFrontOfThisActivity()) {
            ((KybApplication) getApplication()).getResourceManager().setLatestActivity(null);
        }
		mIsActive = false;
		super.onPause();
	}

	/**
	 * Register the receiver to allow various communications from core classes
	 * via the gui manager. See GuiManager.isPlayerVisible()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		registerKybReceiver();
	}
	
	@Override
	protected void onStop() {
		unregisterKybReceiver();
        super.onStop();
    }

    /**
     * To initialise a gesture detector for swipe/fling functionality
     * override onWindowFocusChanged() as follows:
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // fling and dragging can only be used on the red fragment in single pane layout
        initTouchCapabilities();
    }

    /**
	 * Communication protocol that allows core classes to detect if this module
	 * is up and running by registering a receiver that returns RESULT_OK when
	 * it's up.
	 */
	protected void registerKybReceiver() {

		if (mCommunicationReceiver == null) {
			mCommunicationReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
	
					// lets the caller know this activity is here by answering the call
					if (isOrderedBroadcast()) {
						setResultCode(RESULT_OK);
					}
	
					// check for any messages to handle
					handleBroadcastParams(intent);
				}

			};
		}
		
		IntentFilter filter = new IntentFilter(getReceiverFilterName());
		registerReceiver(mCommunicationReceiver, filter);
	}

    protected void initPlayerEditorToolbar() {
        // add the toolbar so it shows first even if there is a welcome screen to show
        Toolbar toolbar = findViewById(R.id.kyb_toolbar);
        setSupportActionBar(toolbar);
        ActionBar supportActionBar = getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setTitle(""); // set to blank here so holder can update it when the data is loaded for the rhythm
        }
    }

    protected String getReceiverFilterName() {
        return PlayRhythmsActivity.PLAYER_NOTIFICATION_ACTION;
    }

    /**
	 * Subclass to handle params, commented example as in PlayRhythmsActivity
	 * @param intent
	 */
    protected void handleBroadcastParams(Intent intent) {
        switch (intent.getIntExtra(RECEIVE_MESSAGE_FLAG, -1)) {
            case RECEIVE_MESSAGE_FLAG_NOTIFICATION_ACTED_ON :
            {
                AndroidResourceManager.logd(LOG_TAG, "handleBroadcastParams: got notice that notification was acted on");
                if (mPlayRhythmFragment != null) {
                    mPlayRhythmFragment.updateUI();
                }
                break;
            }
            default :
                AndroidResourceManager.logw(LOG_TAG, "handleBroadcastParams: got broadcast, but not clear what it was");
                break;
        }
    }


    protected void unregisterKybReceiver() {
		if (mCommunicationReceiver != null) {
			unregisterReceiver(mCommunicationReceiver);
		}
	}
	
	/**
	 * Called by the set bpm dialog, for instance
	 * @return
	 */
	public final Rhythm getRhythm() {
        if (mPlayRhythmFragment != null) {
            return mPlayRhythmFragment.getRhythm();
        }
        else {
            return null;
        }
	}

    /**
     * RhythmPlayingFragmentHolder is listening for rhythm and/or beat type changes, now it got one.
     * If the fragment is open, it needs updating too.
     */
    public final void notifyRhythmChanged() {
        if (mRhythmBeatTypesFragment != null) {
            mRhythmBeatTypesFragment.notifyRhythmChanged();
        }
    }

	/**
	 * Called by the set bpm dialog, for instance
	 * @return
	 */
    public final BeatTrackerBinder getBeatTrackerBinder() {
        if (mPlayRhythmFragment != null) {
            return mPlayRhythmFragment.getBeatTrackerBinder();
        }
        else {
            return null;
        }
    }
	
	/**
	 * Record the last touch event so it can be fed to core classes that need it.
	 * Also handles drag events for swipe/fling etc.
	 */
	public boolean onTouchEvent(MotionEvent event) {
//        AndroidResourceManager.logv(LOG_TAG, "onTouchEvent: doing a touch of some kind");
		recordMotionEvent(event);
		return true;
	}

    /**
	 * By default only the first pointer down will be included for recording events
	 * @return
	 */
	protected boolean isFilterOnlyIncludeFirstPointerDownEvents() {
		return true;
	}

    @NonNull
    protected FragmentManager initPlayerEditorFragments() {
        FragmentManager fm = getSupportFragmentManager();
        initRhythmPlayingHolder(fm);
        mPlayRhythmFragment = (PlayRhythmFragment) fm.findFragmentById(R.id.play_rhythm_fragment);
        mRhythmBeatTypesFragment = (RhythmBeatTypesFragment) fm.findFragmentById(R.id.rbt_list_fragment);
        mRhythmBeatTypesFragment.setPlayRhythmFragment(mPlayRhythmFragment);
        return fm;
    }

    protected void initTouchCapabilities() {
		
		// called from onWindowFocusChanged, only want to do this once
        // need a margin to the left of touches to the edge of the view being dragged
        // make it about 1/4 inch (40px @ 160dpi)
        mTouchMarginToEdge = getResources().getDisplayMetrics().density * TOUCH_MARGIN_DP_TO_EDGE;
        mWidthPixels = getResources().getDisplayMetrics().widthPixels;

        ViewConfiguration config = ViewConfiguration.get(this);
        mScaledEdgeSlop = config.getScaledEdgeSlop();

        // try min fling 3/4 inch
//        mMinFlingVelocity = mTouchMarginToEdge * 4;
//        mMinFlingDistance = mMinFlingVelocity;
	}

    /**
	 * Override to react to a touch down outside either a view that is tracked or trackable
	 * @return
	 */
	protected boolean handleTapOffTrackableViews() {
        AndroidResourceManager.logv(LOG_TAG, "handleTapOffTrackableViews: not stacked in front, do nada");
        return false;
	}

    /**
	 * onTouchEvent is not called if the event is handled lower down, to ensure
	 * events do get recorded, when something handles it it can also call this
	 * method directly
	 * @param event
	 */
	public void recordMotionEvent(MotionEvent event) {

		captureTouchCoords(event);

		boolean retVal = false;

        detectGesture(event); // probably always returns false

		// use the first pointer down, unless it's
		// taken up and the pointer moves to another one (determined by isFilterOnlyIncludeFirstPointerDownEvents())
		if (event.getAction() == MotionEvent.ACTION_DOWN) {

			mMotionEventsPointer = event.getPointerId(0);
			
			// only if action down on a draggable place, eg. a fragment
			retVal = doTouchStart(event);
            AndroidResourceManager.logv(LOG_TAG, String.format("recordMotionEvent: down at %s/%s (retVal=%s)", mLastTouchX, mLastTouchY, retVal));
		}

		// detect ignore it because not the down pointer
		if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_POINTER_UP) {
			// Extract the index of the pointer that left the touch sensor
			final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) 
					>> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			final int pointerId = event.getPointerId(pointerIndex);

			// ignore it as it wasn't the pointer that went down first and flag
			// indicates only interested in first pointer down
			if (pointerId != mMotionEventsPointer) {
				if (isFilterOnlyIncludeFirstPointerDownEvents()) {
                    AndroidResourceManager.logd(LOG_TAG, "recordMotionEvent: wrong pointer up, ignore event");
					return;
				}
				else { // not the same pointer, but we want to switch to the new one now
                    AndroidResourceManager.logd(LOG_TAG, "recordMotionEvent: wrong pointer up, switching it");
					mMotionEventsPointer = pointerId;
				}
			}
		}

		// drag/touch moving or stopping moving
		
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		
		// react to move
		case MotionEvent.ACTION_MOVE: {

            AndroidResourceManager.logv(LOG_TAG, "recordMotionEvent: move");
			doTouchMove(event);
			
		}
		case MotionEvent.ACTION_DOWN: {
			// move or down, if view is null do edge discovery 
			// not recording, nothing can do
            AndroidResourceManager.logv(LOG_TAG, "recordMotionEvent: move or down retval="+retVal
                    +" got view="+(mTrackedView != null)+" pendingEdge="+mEdgeDiscoveryPending);
			if (mTrackedView == null) {

//				if (mEdgeDiscoveryPending == EDGE_DISCOVERY_READY) { // allow discovery provided there isn't one processing already
					retVal = doEdgeDiscovery(event);
					if (retVal) {
                        AndroidResourceManager.logv(LOG_TAG, "recordMotionEvent: doEdgeDiscovery opened a fragment");
						// found something to animate in, have now to wait for it
						// see hookupEdgeDiscovery()
//                        if (mEdgeDiscoveryPending != EDGE_DISCOVERY_READY) { // rhythmslist is hooked up immediately)
//                            mEdgeDiscoveryPending = EDGE_DISCOVERY_PENDING_HOOKUP;
//                        }
					}
                    else {
                        AndroidResourceManager.logv(LOG_TAG, "recordMotionEvent: doEdgeDiscovery didn't find anything discoveryPending="+mEdgeDiscoveryPending);
                    }
//				}
			}
			break;
		}
		
		// reset the pointer if end of movement
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
            AndroidResourceManager.logv(LOG_TAG, "recordMotionEvent: up event, discoveryPending="+mEdgeDiscoveryPending);
			doTouchStop(event);
			break;
		}
	}

    protected boolean detectGesture(MotionEvent event) {
        return false;
    }

    /**
	 * Called when a touch down/move event is not handled for any
	 * dragging processing to see if there's any edge discoveries to make
	 * @param event
	 * @return
	 */
	protected boolean doEdgeDiscovery(MotionEvent event) {
        if (mRhythmBeatTypesFragment != null
                && !mRhythmBeatTypesFragment.isListStateOpen()
                && mWidthPixels - mLastTouchX <= mTouchMarginToEdge + mScaledEdgeSlop) {      // right side
            mRhythmBeatTypesFragment.prepareForOpening();
            hookupEdgeDiscovery(RHYTHM_BEAT_TYPES_FRAG_TAG, mRhythmBeatTypesFragment, mRhythmBeatTypesFragment.getTrackView());
            return true;
        }

        return false;
    }

    /**
     * When a fragment is discovered at the edge and after its incoming
     * animation completes, a callback must be assigned to call this method.
     * Hooks up the variables for dragging etc
     */
	protected void hookupEdgeDiscovery(String tag, SwipeableListFragment fragment, View trackedView) {

		boolean isHookedup = false;

		mTrackedFragment = fragment;
		mTrackedView = trackedView;
		
		if (mTrackedView != null) {
			
			mTrackedView.getLocationOnScreen(mTrackedViewScreenCoords);
			
			setInitialTouchRelativeToView();
			
			isHookedup = true;			
		}

		// either aborted before start or something made impossible to complete hookup
		// make sure the fragment is popped so all is tidy and consistent
		if (!isHookedup) {
			// abort the hookup... ie. close the fragment again
			mTrackedFragment = null;
			mTrackedView = null;
		}

	}
	
	/**
	 * Override to implement action on movement of a tracked touch
	 * @param ev
	 * @return true if handled
	 */
	protected boolean doTouchMove(MotionEvent ev) {
        if (mRhythmBeatTypesFragment != null && mRhythmBeatTypesFragment.equals(mTrackedFragment)) {
            followTouchLimitLeftMovement(ev);
            return true;
        }
        else {
            return false;
        }
	}

	protected void captureTouchCoords(MotionEvent ev) {
		mLastTouchX = ev.getRawX();
		mLastTouchY = ev.getRawY();
		mGuiManager.setLastKnownPointerLocation(mLastTouchX, mLastTouchY);
		
		if (mTrackedView != null) {
			mTrackedView.getLocationOnScreen(mTrackedViewScreenCoords);
		}
	}

	/**
	 * Call from doTouchMove() to have the tracked view follow the movement
	 * but limit the movement to the left so it does not go beyond the 
	 * origin. Meaning only movement to the right and then back left is allowed.
	 * @param ev
	 */
	protected void followTouchLimitLeftMovement(MotionEvent ev) {

        if (!mRhythmBeatTypesFragment.getTrackView().equals(mTrackedView)) {
            AndroidResourceManager.logw(LOG_TAG, "followTouchLimitRightMovement: unknown view, call super ("+mTrackedView+")");
            return;
        }

        // place the view relative to the touch

        // get the screen relative distance from the touch to the left of the view
        // as set from the initial touch (to max width - margin)
        float screenRelativeX = mLastTouchX - mTouchRelativeToViewHorizontal;
        // subtract from that the placement of the view on the screen plus the translation
        // (to get its parent relative placement)
        float left = mTrackedView.getLeft();

        // find the new position for the view based on keeping the relation
        // with the touch to the left side, but don't allow it to go beyond
        // the right limit
        float desiredX =
                Math.max(
                        // left is the getX() layout position which stays the same regardless of translation
                        left
                        // eg. if left = 0, the movement will never reduce past 0
                        , screenRelativeX);
        AndroidResourceManager.logv(LOG_TAG, String.format(
                "followTouchLimitRightMovement: desiredX=%s, left=%s, width=%s, screenRelativex=%s, touch=%s, startrelativeHori=%s, screenwidth=%s",
                desiredX, left, mTrackedView.getWidth(), screenRelativeX, mLastTouchX, mTouchRelativeToViewHorizontal, mWidthPixels));

        mRhythmBeatTypesFragment.followDrag(desiredX);
	}

	/**
	 * @param ev
	 * @return
	 */
	private boolean doTouchStart(MotionEvent ev) {
		boolean result = doTouchStartGestures(ev);

		// not handled draughter may have to react
		if (!result) {
            if (mPlayRhythmFragment != null && mPlayRhythmFragment.mEventListener != null) {
                float x = ev.getRawX() - mPlayRhythmFragment.mViewOnScreenCoords[0];
                float y = ev.getRawY() - mPlayRhythmFragment.mViewOnScreenCoords[1];

                mPlayRhythmFragment.mEventListener.handlePointerContact(x, y);
                if (mPlayRhythmFragment.mPlayRhythmView != null) {
                    mPlayRhythmFragment.mPlayRhythmView.invalidate(); // want it to redraw
                }
            }
            else {
                AndroidResourceManager.logw(LOG_TAG, "doTouchStart: doTouchStartGestures() returned false, but no holder/listener, so nothing was sent to beatActionHandler");
            }
		}
        else {
            AndroidResourceManager.logw(LOG_TAG, "doTouchStart: doTouchStartGestures() returned true, so nothing was sent to beatActionHandler");
        }

		return result;
	}

    /**
	 * Standardised set up for starting an action on one of the fragment
	 * @param ev
	 * @return
	 */
	private boolean doTouchStartGestures(MotionEvent ev) {

		mTrackedView = null;
		// no view setup, and no discovery happening at the edge, so treat like an off
        return handleTapOffTrackableViews();
	}

	/**
	 * It's possible could be moved/moving already, so not at its origin
	 * keep relative touch distance to both left and top edges
	 *  (so can drag the object with the same relationship as it moves)
	 * Called when a view is being set up for dragging, either on initial touch down
	 * because the view is under the touch, or after hooking up a view that is 'edge
	 * discovered'.
	 * The mTrackedView and its relative to screen coords should be preset before calling
	 * this module.
	 */
	private final void setInitialTouchRelativeToView() {
		// the max value is the view's width minus margin, so take that if it's the lesser
        mTouchRelativeToViewHorizontal = mTrackedFragment.isLeftSlide()
				? Math.min(
						mLastTouchX - mTrackedViewScreenCoords[0]
						, mTrackedView.getWidth() - mTouchMarginToEdge)
                : mTouchMarginToEdge; // right side, take the margin

//            AndroidResourceManager.logv(LOG_TAG, "setInitTouch: set="+mTouchRelativeToViewLeft+" width="+mTrackedView.getWidth()
//                    +" last="+mLastTouchX+" track="+mTrackedViewScreenCoords[0]+" trans="+mTrackedView.getTranslationX());
		// the max value is the view's height, so take that if it's the lesser
		mTouchRelativeToViewTop =
				Math.min(
                        mLastTouchY - mTrackedViewScreenCoords[1]
                        , mTrackedView.getHeight() - mTouchMarginToEdge);
	}

	/**
	 * Calls honeycomb onwards code for dragging etc, and if not handled
	 * then calls all versions code for reacting to touches... such as rhythm editing
	 * @param event 
	 * @return
	 */
	private boolean doTouchStop(final MotionEvent event) {

        // don't process the drag stop immediately if there's a protected transaction
        // in progress, instead supply as a callback for when it completes
        boolean result = doTouchStopGestures(event);
        mTrackedView = null;
        mTrackedFragment = null;

		// not handled already, draughter might need to react
		if (!result) {
            if (mPlayRhythmFragment != null && mPlayRhythmFragment.mEventListener != null) {
                float x = event.getRawX() - mPlayRhythmFragment.mViewOnScreenCoords[0];
                float y = event.getRawY() - mPlayRhythmFragment.mViewOnScreenCoords[1];
                mPlayRhythmFragment.mEventListener.handlePointerRelease(x, y);
                if (mPlayRhythmFragment.mPlayRhythmView != null) {
                    mPlayRhythmFragment.mPlayRhythmView.invalidate(); // want it to redraw
                }
            }
		}

		mMotionEventsPointer = INVALID_POINTER_ID;
        mGuiManager.setIsPointerDown(false);

		return result;

	}

    /**
	 * Sub-class to do more things
	 * @param event 
	 * @return true if handled
	 */
	protected boolean doTouchStopGestures(MotionEvent event) {
        if (mRhythmBeatTypesFragment != null && mRhythmBeatTypesFragment.equals(mTrackedFragment)) {
            mRhythmBeatTypesFragment.stopDragging();
            return true;
        }
        else {
            return false;
        }
	}

	/**
	 * If user presses back button and is in red (right : detail) fragment would
	 * not want to go back to that fragment next time around, it's effectively
	 * dismissed, so reset its timing Any other time back button is pressed
	 * would be exiting the app anyway so doesn't matter
	 */
	@Override
	public void onBackPressed() {
        if (mRhythmBeatTypesFragment != null && mRhythmBeatTypesFragment.isListStateOpen()) { // close open dialog_simple_list
            mRhythmBeatTypesFragment.showClosed(true);
            return;
        }

        if (this instanceof PlayRhythmsActivity) { // will be exiting kyb, so want it to stop playing

            if (mPlayRhythmFragment != null) {
                if (mResourceManager.isRhythmPlaying() || mPlayRhythmFragment.isPlaying()) {
                    mPlayRhythmFragment.stopPlaying();
                }
            }
            else if (mResourceManager.isRhythmPlaying()) {
                AndroidResourceManager.logw(LOG_TAG, "onBackPressed: resource mgr says rhythm is playing, but no holder available to stop it with");
            }
        }

        super.onBackPressed();
	}

    /**
     * Open the rhythm beat types settings if not currently open, or close if not
     */
    protected void toggleRhythmBeatTypes() {
        // detect protected transaction and don't react if so
        if (mRhythmBeatTypesFragment.isListStateOpen()) {
            mRhythmBeatTypesFragment.showClosed(true);
        }
        else {
            mRhythmBeatTypesFragment.showOpen(true);
        }
    }

    /**
     * Push or pop fragment has failed unexpectedly, this method attempts to recover the situation
     * by restarting the activity so the fragment can be used again
     * Also RhythmPlayingFragmentHolder.onStart() will call this if there are extreme problems with the data (eg. no edit rhythm on db)
     * @param stringId
     */
    protected void restartActivityWithFailureMessage(int stringId) {
        AndroidResourceManager.logw(LOG_TAG, "restartActivityWithFailureMessage: "+getString(stringId));

        Intent intent = getIntent();
        // possibly this isn't the right place for it, it comes after the finish... this one is superflous
        // perhaps
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();

        intent.putExtra(INTENT_EXTRA_START_TOAST_MSG_RES_ID, stringId);
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    protected void stopAdsCompletely() {
        mIsShowingAnAd = false;
        FrameLayout adParent = findViewById(R.id.ad_view_parent);
        if (adParent != null) { // eg. from settings activity there won't be one
            adParent.removeAllViews();
        }
    }

    @NonNull
    protected AdRequest.Builder makeAdRequestBuilder() {
        AdRequest.Builder adRequestBuilder = new AdRequest.Builder();

        if (!BuildConfig.SHOW_REAL_ADS) { // ie. debug build
            adRequestBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                .addTestDevice(KybApplication.NEXUS_7_DEVICE)
                .addTestDevice(KybApplication.ZTE_DEVICE);
        }

        if (mApplication.isConsentForNonPersonalizedAds()) {
            Bundle extras = new Bundle();
            extras.putString("npa", "1");
            adRequestBuilder.addNetworkExtrasBundle(AdMobAdapter.class, extras);
        }

        return adRequestBuilder;
    }

    /**
     * A general purpose callback for any sub-class to use in conjunction with a load utility (eg. data load fragment)
     * The idea is to avoid the need for extra classes. See PlayRhythmsActivity for example.
     */
    public void dataLoaded() {}

    /**
     * Set during KybDialogFragment onAttach()/onDetach()
     * @param hasOpenDialog
     */
    public void setHasOpenDialog(boolean hasOpenDialog, String id) {
        if (mOpenDialogs != null) {
            AndroidResourceManager.logv(LOG_TAG, String.format("setHasOpenDialog: called with %s from %s", hasOpenDialog, id));

            if (hasOpenDialog) {
                if (!mOpenDialogs.contains(id)) {
                    mOpenDialogs.add(id);
                }
            }
            else {
                mOpenDialogs.remove(id);
            }
        }
    }

    public boolean hasOpenDialog() {
        if (mOpenDialogs != null && !mOpenDialogs.isEmpty()) {
            AndroidResourceManager.logv(LOG_TAG, String.format("hasOpenDialog: unmanaged dialog recorded as open=%s", mOpenDialogs.get(0)));
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Called when the resource manager receives rhythm stopped, or the application calls back that premium bought (via broadcast or in first setup)
     */
    private final void updateUI() {
        if (mGuiManager.isUiThread()) {
            run();
        }
        else {
            mGuiManager.runInGuiThread(this);
        }
    }

    public void updateUI(boolean showThankYouForUpgrading) {
        mShowThankYouForUpgrading = showThankYouForUpgrading;
        updateUI();
    }

    /**
     * Called when the resource manager receives rhythm stopped
     * @param isNormalPlayStop after self-check the service will stop, but don't want to trigger an ad for that
     */
    public void updateUiWhenRhythmStops(boolean isNormalPlayStop) {
        AndroidResourceManager.logd(LOG_TAG, "updateUiWhenRhythmStops:");
        mRhythmJustStoppedPlaying = isNormalPlayStop;
        updateUI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!mResourceManager.getApplication().handleOnActivityResult(requestCode, resultCode, data)) {
            AndroidResourceManager.logw(LOG_TAG, "onActivityResult: unhandled result(" + requestCode + "," + resultCode + "," + data +")");
        }
    }

    /**
     * UI changes made from within UI thread
     */
    @Override
    public void run() {
        if (mPlayRhythmFragment != null) {
            mPlayRhythmFragment.updateUI();
        }

        // remove ad stuff

        if (mIsMenuInitialised) {
            AndroidResourceManager.logd(LOG_TAG, "run: recreate menu in case need to for ads/purchase premium option");
            invalidateOptionsMenu();
        }

        if (mShowThankYouForUpgrading) {
            AndroidResourceManager.logd(LOG_TAG, "run: show thank you for upgrading alert");
            showThankYouForUpgradingAlert();
        }

        mRhythmJustStoppedPlaying = false; // only used in the above test, but for accuracy always unset it after run() completes
    }

    protected void showThankYouForUpgradingAlert() {
        mShowThankYouForUpgrading = false;

        AlertDialog.Builder bldr = new AlertDialog.Builder(this)
                .setTitle(R.string.thanks_for_upgrading_title)
                .setMessage(R.string.thanks_for_upgrading_msg);

        bldr.create().show();
    }

    public boolean isAnEditorActivity() {
        return false;
    }

    public RhythmPlayingFragmentHolder getRhythmPlayingHolder() {
        return mRhythmPlayingHolder;
    }

    public PlayRhythmFragment getPlayRhythmFragment() {
        return mPlayRhythmFragment;
    }

    /**
     * Thrown by RhythmsList in its dialog_simple_list adapter getView() when an IllegalStateException happens, which seems to be
     * untrappable since it's only inside Android calls. For this reason the Application traps this instead
     * and allows it to drop instead of crashing in a heap.
     */
    public static class CaughtUnstoppableException extends RuntimeException {
        public CaughtUnstoppableException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }

}
