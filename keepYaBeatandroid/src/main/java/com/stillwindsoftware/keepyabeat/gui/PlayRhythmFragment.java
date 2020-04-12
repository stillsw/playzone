package com.stillwindsoftware.keepyabeat.gui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.EditRhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.BeatBasicActionHandler;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerService;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import static com.stillwindsoftware.keepyabeat.gui.PlayRhythmsActivity.FAB_X_POS;
import static com.stillwindsoftware.keepyabeat.gui.PlayRhythmsActivity.FAB_Y_POS;

/**
 * Displays the rhythm currently loaded and provides controls for settings and playback.
 */
public class PlayRhythmFragment extends Fragment
    implements LibraryListener, View.OnClickListener, View.OnTouchListener, Runnable {

	private static final String LOG_TAG = "KYB-"+PlayRhythmFragment.class.getSimpleName();
    public static final int KEEP_REDRAWING_RHYTHM_TIMING_MILLIS = 500; // how long to redraw the rhythm after a change

    // rhythms keeps this class as a listener, but it'll start listening to a new one before the last is stopped
    // for example, go to editor, save from editor, the sequence is startListening(the new one), stopListening(the old one)
    // and the holder is no longer listening
    // to keep track, send this value is tested against the holder in rhythms (incremented in onAttach())
    private static int sFragmentInstance = 0;
    private int mFragmentInstance;

    private RhythmPlayingFragmentHolder mRhythmPlayingDataHolder;
    private AndroidResourceManager mResourceManager;
    private AndroidGuiManager mGuiManager;
    private SettingsManager mSettingsManager;
    private PlayerState mPlayerState;
    private RhythmsContentProvider mRhythms;
    private BeatTrackerBinder mBeatTrackerComm;
    private BeatTrackerServiceConnection mServiceConnection;

    private KybActivity mActivity;
    private LayoutInflater mLayoutInflater;     // for calling dialog
    private View mLayoutView;
    // allow direct access (used during touch event processing) to the activity (Play or Edit)
    BeatBasicActionHandler mEventListener;
    int[] mViewOnScreenCoords = new int[2];
    PlayRhythmView mPlayRhythmView;

    private PlayPauseButton mPlayBtn;
    private ImageButton mStopBtn;
    private BpmButton mBpmBtn;
    private RepeatsButton mRepeatsBtn;
    private FloatingActionButton mFabBtn1, mFabBtn2;
    private ViewGroup mTitleBar;
    private Button mChangeTagsBtn, mMoreInfoBtn;
    private TextView mNoTagsView;
    private ViewGroup mTagsContainer;

    // bunch of flags, names should be self explanatory
    private boolean mIsEditor;
    private boolean mIsStartedAndAwaitingInitComplete = false;
    private boolean mLastChangeWasSimpleEdit = false;
    private boolean mLastChangeUiUpdatedBeatTree = true; // goes hand-in-hand with mLastChangeWasSimpleEdit
    private boolean mIsPendingServiceToPlay = false; // set when request to play the rhythm, but has to start the service too, so don't do > once
    private boolean mWasBoundToService = false;    // set if bound to service onDetach() so a subsequent onAttach() knows to bind again
    private boolean mIsInitComplete = false;       // set after the first onAttach() and background db stuff finished
    private boolean mIsOpenAnimateFab;
    private boolean mAwaitingRearrangeRhythm;       // see causeRhythmRedraw()

    private int mTitleBarExpansion = View.GONE;
    private long mKeepRedrawingUntil; // see causeRhythmRedraw(int)
    private String mRhythmTagsText;
    private int mAnimateInFabX = -1, mAnimateInFabY = -1;

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mActivity = (KybActivity) getActivity();
        mIsEditor = mActivity instanceof EditRhythmActivity;
        mResourceManager = mActivity.mResourceManager;
        mGuiManager = (AndroidGuiManager) mResourceManager.getGuiManager();
        mSettingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager();
        mSettingsManager.setEditorActive(mIsEditor);
        mPlayerState = mSettingsManager.getPlayerState();
        mRhythms = (RhythmsContentProvider) mResourceManager.getLibrary().getRhythms();

        mLayoutInflater = inflater;
        int resource = mIsEditor ? R.layout.edit_rhythm_player_fragment : R.layout.play_rhythm;
        return inflater.inflate(resource, container, false);
	}

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mLayoutView = view;
        pullFabAnimationArgs();
        setupViews();
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mFragmentInstance = sFragmentInstance++; // see comments in declaration
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    void updateUI() {
        boolean isPlaying = mBeatTrackerComm == null
                ? mResourceManager != null && mResourceManager.isRhythmPlaying()        // manager hasn't been told it's stopped (but that's not the most accurate)
                : mBeatTrackerComm.isPlaying() && !mBeatTrackerComm.isPaused();         // if binder is present, it can also query the beatracker for paused

        AndroidResourceManager.logd(LOG_TAG, String.format("updateUI: isPlaying=%s (from binder=%s) playbtn=%s", isPlaying, (mBeatTrackerComm!=null), (mPlayBtn!=null)));

        if (mPlayBtn != null) {
            mPlayBtn.updateImage(isPlaying);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        AndroidResourceManager.logv(LOG_TAG, String.format("onStart: mIsInitComplete=%s)", mIsInitComplete));

        if (mIsInitComplete) {
            run();

            try {
                while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                    AndroidResourceManager.logd(LOG_TAG, "onStart: waiting for lock on player rhythm");
                }

                startListening(mRhythms.getPlayerEditorRhythm());
            } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
                AndroidResourceManager.loge(LOG_TAG, "onStart: program error", e);
            } finally {
                mRhythms.releaseReadLockOnPlayerEditorRhythm();
            }
        }
        else {
            mIsStartedAndAwaitingInitComplete = true;
            AndroidResourceManager.logw(LOG_TAG, "onStart: listening isn't started (mIsInitComplete=false)");
        }

        if (mWasBoundToService) {
            AndroidResourceManager.logd(LOG_TAG, "onStart: re-binding to service");
            mWasBoundToService = false;
            initService(false);
        }
        else if (mResourceManager.isRhythmPlaying()) {
            AndroidResourceManager.logd(LOG_TAG, "onStart: not previously bound to service but resMgr reports playing, so init service now");
            initService(false);
        }
    }

    @Override
    public void onStop() {
        // may have to disconnect the beat tracker
        if (mServiceConnection != null) {
            AndroidResourceManager.logd(LOG_TAG, "onStop: unbind from the beat tracker service");
            mWasBoundToService = true; // flag for onStart() to re-bind
            mActivity.unbindService(mServiceConnection);
            mServiceConnection = null;
        }

        super.onStop();

        stopListening();
        keepScreenAwakeWhilePlayingIfSet(false);
    }

    private void startListening(RhythmSqlImpl rhythm) {

        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "startListening: waiting for lock on player rhythm");
            }

            AndroidResourceManager.logd(LOG_TAG, String.format("startListening: rhythm=%s iseditor=%s invocation=%s", rhythm.getKey(), mIsEditor, mFragmentInstance));
            mRhythms.registerRhythmGui(this);
            mRhythms.setPlayerEditorRhythm(rhythm);
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "startListening: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        mResourceManager.getListenerSupport().addListener(mSettingsManager, this);
    }

    private void stopListening() {
        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "stopListening: waiting for lock on player rhythm");
            }

            final int inspectGuiInstance = mRhythms.inspectRhythmGuiInstance();
            if (inspectGuiInstance <= mFragmentInstance) {
                AndroidResourceManager.logd(LOG_TAG, String.format("stopListening: iseditor=%s invocation=%s", mIsEditor, mFragmentInstance));
                mRhythms.deregisterRhythmGui();
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, String.format("stopListening: holder (%s) on rhythms is already later than this one (%s), not stopping",
                        inspectGuiInstance, mFragmentInstance));
            }

        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "stopListening: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }
        mResourceManager.getListenerSupport().removeListener(this);
    }

    @Override
    public void itemChanged(int changeId, String key, int natureOfChange, LibraryListener.LibraryListenerTarget[] listenerTargets) {

        ListenerSupport listenerSupport = mResourceManager.getListenerSupport();

        if (mResourceManager.isLogging(PlatformResourceManager.LOG_TYPE.debug)) { // avoid the overhead in release
            AndroidResourceManager.logd(LOG_TAG, String.format("itemChanged: id=%s, key=%s, natureOfChange=%s",
                    changeId, key, listenerSupport.debugNatureOfChange(natureOfChange)));
        }

        if (!mIsInitComplete) {
            AndroidResourceManager.logw(LOG_TAG, "itemChanged: not ready for change, presume rhythms took a chance on it (any itemChanged() warnings in log?)");
            return;
        }

        if (listenerSupport.isRhythmRemoved(natureOfChange)) {
            Intent intent = PlayRhythmsActivity.getIntentToOpenRhythm(mActivity, key); // key is gone, so will trigger a new rhythm opened
            startActivity(intent);
        }
        else {
            if (listenerSupport.isRhythmEdit(natureOfChange)   // edits are on the current data beat tree
                    && !listenerSupport.isUndo(natureOfChange)){    // so unless it's an undo, there's no need to react
                AndroidResourceManager.logd(LOG_TAG, "itemChanged: change is simple edit");
                mLastChangeWasSimpleEdit = true;
                mLastChangeUiUpdatedBeatTree = listenerSupport.isRhythmEditUiUpdatesBeatTree(natureOfChange);
            }
            mResourceManager.getGuiManager().runInGuiThread(this);
        }
    }

    /**
     * Called by PlayRhythmsActivity.onNewIntent
     * @param rhythm
     */
    public void swapRhythm(RhythmSqlImpl rhythm) {
        if (mIsEditor) {
            throw new IllegalStateException("swapRhythm is not allowed for edit rhythm");
        }

        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "swapRhythm: waiting for lock on player rhythm");
            }

            RhythmSqlImpl playerEditorRhythm = mRhythms.getPlayerEditorRhythm();

            if (!rhythm.equals(playerEditorRhythm)) {
                boolean wasNewRhythm = !playerEditorRhythm.existsInDb(); // for undo stack

                stopListening(); // don't react to any changes while swapping the rhythm out

                mPlayerState.setRhythmKey(rhythm.getKey());
                startListening(rhythm);

                if (wasNewRhythm) { // replacing a new rhythm, assert undo commands can be undone
                    mResourceManager.getUndoableCommandController().assertConsistentStack();
                }
            }
            else {
                rhythm = playerEditorRhythm; // make sure have the same instance as this call comes from a resolve key call
            }

            mPlayRhythmView.setupDraughter(rhythm);

            // make changes to views in the ui thread
            mGuiManager.runInGuiThread(this);

        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "swapRhythm: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }
    }

    private void getRhythmTags(RhythmSqlImpl dbRhythm) {
        mRhythmTagsText = dbRhythm.getDenormalizedTagsList();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        AndroidResourceManager.logd(LOG_TAG, "onTouch: title_click_tracker got a touch, pass it on");
        return mActivity.onTouchEvent(event);
    }

    /**
     * Called from playPauseButton.onDraw()
     * @return
     */
    boolean isPlaying() {
        if (checkBeatTrackerAvailable() && !mBeatTrackerComm.isStopped()) {
            AndroidResourceManager.logv(LOG_TAG, "isPlaying: isRhythmPlaying binder says not stopped");
            return true;
        }

        // no binder, check if resource manager believes playing is happening
        AndroidResourceManager.logv(LOG_TAG, "isPlaying: isRhythmPlaying no binder so return what res mgr says");
        return mResourceManager.isRhythmPlaying();
    }

    void stopPlaying() {
        mPlayBtn.updateImage(false);
        if (checkBeatTrackerAvailable()) {
            mBeatTrackerComm.stopPlay();
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "stopPlaying: beat tracker not available to stop play");
        }
    }

    /**
     * Toggle the extra views in the layout, have the view animate into position
     * and the fab follow it
     */
    void toggleTitleLayoutExpanded() {
        mTitleBarExpansion = mChangeTagsBtn.getVisibility() == View.GONE ? View.VISIBLE : View.GONE;
        setTitleExpandedViewsVisibility();
    }

    private void setTitleExpandedViewsVisibility() {
        if (mNoTagsView.getText().length() == 0) {
            mNoTagsView.setVisibility(mTitleBarExpansion);
            mTagsContainer.setVisibility(View.GONE);
        }
        else {
            mNoTagsView.setVisibility(View.GONE);
            mTagsContainer.setVisibility(mTitleBarExpansion);
        }
        mChangeTagsBtn.setVisibility(mTitleBarExpansion);

        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "setTitleExpandedViewsVisibility: waiting for lock on player rhythm");
            }

            RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();
            if (rhythm != null) {
                mChangeTagsBtn.setText(rhythm.existsInDb() ? R.string.menuSetNameRhythm : R.string.saveRhythmLabel);
            }
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "setTitleExpandedViewsVisibility: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        if (mMoreInfoBtn != null) {
            boolean visible = mTitleBarExpansion == View.VISIBLE;
            mMoreInfoBtn.setText(getString(visible ? R.string.rhythmDetailsLess : R.string.rhythmDetailsMore));
        }

        AndroidResourceManager.logd(LOG_TAG, String.format(
                "setTitleExpandedViewsVisibility: expansion=%s, title back vis=%s"
                , mTitleBarExpansion, (mTitleBar.findViewById(R.id.title_background).getVisibility() == View.VISIBLE)));
    }

    private void initTitleExpandedViews() {
        if (mNoTagsView != null) {
            mNoTagsView.setText(mRhythmTagsText);
            try {
                SetRhythmNameAndTagsActivity.setTagViews(mNoTagsView, mTagsContainer, mLayoutInflater, null);
                setTitleExpandedViewsVisibility();
            } catch (NullPointerException e) {
                AndroidResourceManager.loge(LOG_TAG, "initTitleExpandedViews: did the restart after long period... seen NPE here before");
            }
        }
    }

    public int getFragmentInstance() {
        return mFragmentInstance;
    }

    public boolean isTitleBarExpanded() {
        return mTitleBarExpansion == View.VISIBLE;
    }

    public View getFab1() {
        return mFabBtn1;
    }

    public View getFab2() {
        return mFabBtn2;
    }

    public boolean isEditor() {
        return mIsEditor;
    }

    public RhythmSqlImpl getRhythm() {
        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "getRhythm: waiting for lock on player rhythm");
            }

            return mRhythms.getPlayerEditorRhythm();
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "getRhythm: program error", e);
            return null;
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }
    }

    /**
     * Called from the play button onClick() because there is no service available and it needs to be started
     * or from onStart() in which case just bind
     */
    private void initService(boolean play) {
        if (!mIsPendingServiceToPlay) { // don't do it > once

            if (play) {
                mIsPendingServiceToPlay = true;
            }

            // attempt to bind to the service, but send a new intent to start if first
            // (in case it has killed itself through idleness)
            // make sure the service is started ready to play, note the application's context is used
            // because it is designed to survive beyond this activity's lifecycle
            Application appl = mActivity.getApplication();
            Intent srvIntent = BeatTrackerService.makeIntent(appl);
            appl.startService(srvIntent);
            mServiceConnection = new BeatTrackerServiceConnection();
            if (!mActivity.bindService(srvIntent, mServiceConnection, 0)) {
                AndroidResourceManager.loge(LOG_TAG, "initService: unable to connect to service");
            }
        }
    }

    /**
     * no beat tracker
     * @return
     */
    boolean checkBeatTrackerAvailable() {
        if (mBeatTrackerComm != null) {
            if (mBeatTrackerComm.isServiceAlive()) {
                return true;
            }
            else {
                // the service has stopped itself on idle, null the binder and have the caller recreate it
                mBeatTrackerComm = null;
            }
        }

        return false;
    }

    private void pullFabAnimationArgs() {
        Intent intent = mActivity.getIntent();
        mIsOpenAnimateFab = intent.getBooleanExtra(PlayRhythmsActivity.OPEN_ANIMATE_FAB, false);
        if (mIsOpenAnimateFab) {
            intent.removeExtra(PlayRhythmsActivity.OPEN_ANIMATE_FAB);  // don't have it repeat the animation, eg. on config change
            mAnimateInFabX = intent.getIntExtra(FAB_X_POS, -1);
            mAnimateInFabY = intent.getIntExtra(FAB_Y_POS, -1);
        }
    }

    /**
     * When a popup dialog (such as beat options) cancels/closes we need to make sure
     * a redraw happens so that once the isBlocking condition is cleared the selected
     * beat is drawn w/o selection.
     */
    public void causeRhythmRedraw() {
        if (mPlayRhythmView != null) {
            mPlayRhythmView.postInvalidate(); // do it in post, as this method is called in the dialog's onCancel()
        }
    }

    /**
     * Overloaded version that will keep the redrawing happening for a guaranteed amount of time
     * by timing how many millis to keep it up for (see drawRhythm())
     * @param millis
     */
    public void causeRhythmRedraw(int millis) {
        mKeepRedrawingUntil = System.currentTimeMillis() + millis;
        causeRhythmRedraw();
    }

    /**
     * EditBeatNumberingDialog in portrait can save the rhythm with the keyboard open, make sure the rhythm is rearranged
     * @param rearrangeRhythm
     * @param delayMillis
     */
    public void causeRhythmRedraw(boolean rearrangeRhythm, int delayMillis) {
        mAwaitingRearrangeRhythm = true;
        if (mPlayRhythmView != null) {
            mPlayRhythmView.postDelayed(this, delayMillis);
        }
    }

    /**
     * Called during onCreateView(), and also from initData() if the view has already
     * been created (thought it was when screen orientation changes, but actually haven't seen it happen from then)
     */
    private void setupViews() {
        mPlayRhythmView = mLayoutView.findViewById(R.id.rhythm_view);
        mPlayRhythmView.init(mResourceManager, mRhythms, mIsEditor, this, mActivity);
        mLayoutView.findViewById(R.id.bpm_button_press).setOnClickListener(this); // fudge to get the background to look like the regular buttons
        mLayoutView.findViewById(R.id.repeats_button_press).setOnClickListener(this); // fudge to get the background to look like the regular buttons

        mPlayBtn = mLayoutView.findViewById(R.id.play_button);
        mPlayBtn.setOnClickListener(this);
        if (mBeatTrackerComm != null) {     // activity started when already playing, need to make sure button has correct image
            mPlayBtn.updateImage(mBeatTrackerComm.isPlaying());
        }

        mStopBtn = mLayoutView.findViewById(R.id.stop_button);
        mStopBtn.setOnClickListener(this);

        mBpmBtn = mLayoutView.findViewById(R.id.bpm_button);
        mBpmBtn.setOnClickListener(this);

        mRepeatsBtn = mLayoutView.findViewById(R.id.repeats_button);
        mRepeatsBtn.setPlayRhythmFragment(this, mPlayerState.getRepeatOption(), mPlayerState.getNTimes());
        mRepeatsBtn.setOnClickListener(this);

        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "setActivityViews: waiting for lock on player rhythm");
            }

            RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();
            if (rhythm != null) {
                mBpmBtn.setBpm(rhythm.getBpm());
            }
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "setActivityViews: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        mFabBtn1 = mLayoutView.findViewById(R.id.fab_1);
        mTitleBar = mLayoutView.findViewById(R.id.title_bar);

        if (mTitleBar != null) {

            mMoreInfoBtn = mTitleBar.findViewById(R.id.more_info_vw);

            if (!mIsEditor) {                                           // player only expands the title and allows click to see more
                mTitleBar.setOnClickListener(this);
                mMoreInfoBtn.setOnClickListener(this);
                mMoreInfoBtn.setVisibility(View.VISIBLE);

                // the transparent part of the title bar, make sure to pass clicks on to the activity
                View tableBarClickTracker = mTitleBar.findViewById(R.id.title_click_tracker);
                tableBarClickTracker.setOnTouchListener(this);
                tableBarClickTracker.setVisibility(View.VISIBLE);

                mTitleBar.findViewById(R.id.title_background).setVisibility(View.VISIBLE);
                mTagsContainer = mTitleBar.findViewById(R.id.formatted_tags);
                mNoTagsView = mTitleBar.findViewById(R.id.rhythm_tags);
                mChangeTagsBtn = mTitleBar.findViewById(R.id.change_name_tags_btn);
                mChangeTagsBtn.setOnClickListener(this);

                View tableBarTitle = mTitleBar.findViewById(R.id.rhythm_name);
                tableBarTitle.setVisibility(View.VISIBLE);

                initTitleExpandedViews();
            }
        }
    }

    /**
     * Called from onViewCreated()
     */
    private void initDataForPlayer(RhythmSqlImpl rhythmHeldByContentProvider) {

        // try to satisfy setup without going to the db

        final String rhythmKey = mActivity.checkIntentForRhythm();
        final boolean forSpecifiedRhythm = rhythmKey != null;
        final boolean needNewRhythm = !forSpecifiedRhythm
                && (rhythmHeldByContentProvider == null || rhythmHeldByContentProvider instanceof EditRhythmSqlImpl);

        if (!forSpecifiedRhythm && !needNewRhythm) {
            AndroidResourceManager.logd(LOG_TAG, "initDataForPlayer: completed in UI thread (db access not needed)");
            completeDataInit(rhythmHeldByContentProvider);
        }

        else { // complete the process off the ui thread
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(

                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {

                            // if it's for a new rhythm, it's possible need to insert a row in the db, so need a transaction
                            mResourceManager.startTransaction();
                            try {
                                final String playerStateRhythmKey = mPlayerState.getRhythmKey();
                                final String resolveKey = forSpecifiedRhythm ? rhythmKey : playerStateRhythmKey;

                                RhythmSqlImpl dbRhythm = mRhythms.resolveRhythmKey(resolveKey, BeatTree.PLAYED, true);

                                AndroidResourceManager.logv(LOG_TAG,
                                        String.format("initDataForPlayer: not using playerEditorRhythm, using key=%s, (state key=%s), returned key=%s",
                                                resolveKey, playerStateRhythmKey, dbRhythm.getKey()));

                                // update the state prefs if the keys don't match
                                if (!dbRhythm.getKey().equals(playerStateRhythmKey)) {
                                    mPlayerState.setRhythmKey(dbRhythm.getKey());
                                    AndroidResourceManager.logd(LOG_TAG, "initDataForPlayer: completed setup in background (db), set new playerStateKey");
                                }
                                else {
                                    AndroidResourceManager.logd(LOG_TAG, "initDataForPlayer: completed setup in background (db), found previously opened player state rhythm");
                                }

                                completeDataInit(dbRhythm);
                            } finally {
                                mResourceManager.saveTransaction();
                            }

                        }
                    });
        }
    }

    /**
     * Called from onViewCreated()
     */
    private void initDataForEditor(final RhythmSqlImpl rhythmHeldByContentProvider) {

        final boolean isBeatTypeChooserWaitingForInit = mRhythmPlayingDataHolder.isBeatTypeChooserWasOpenOnClose();

        final boolean lookupNotNeeded = rhythmHeldByContentProvider != null && rhythmHeldByContentProvider instanceof EditRhythmSqlImpl;

        if (lookupNotNeeded && !isBeatTypeChooserWaitingForInit) { // if there's a config change with the chooser open, the widgets won't yet be ready for it
            AndroidResourceManager.logd(LOG_TAG, "initDataForEditor: rhythms already as editRhythm available, so completed in UI thread (db access not needed)");
            completeDataInit(rhythmHeldByContentProvider);
            return;
        }

        final String intentKey = mActivity.checkIntentForRhythm();

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(

                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        if (lookupNotNeeded && isBeatTypeChooserWaitingForInit) {   // should be the only way can happen, keeping test in case future changes make it wrong
                            AndroidResourceManager.logd(LOG_TAG, "initDataForEditor: built in a delay to open the bt chooser so widgets are ready on UI thread");
                            completeDataInit(rhythmHeldByContentProvider);
                        }

                        else {
                            try {
                                while (!mRhythms.getWriteLockOnPlayerEditorRhythm(1)) {
                                    AndroidResourceManager.logd(LOG_TAG, "initDataForEditor: waiting for lock on player rhythm");
                                }
                                mResourceManager.startTransaction();
                                EditRhythmSqlImpl dbRhythm = mRhythms.getEditRhythm(true);  // return the existing edit rhythm if there is one
                                if (dbRhythm != null && intentKey != null
                                        && !dbRhythm.getSourceRhythm().getKey().equals(intentKey)) {
                                    AndroidResourceManager.logw(LOG_TAG, String.format("initDataForEditor: intentKey=%s does not match existing editRhythm source=%s, making new one",
                                            intentKey, dbRhythm.getSourceRhythm().getKey()));
                                    dbRhythm = null;
                                }

                                // downgrade by acquiring read lock
                                mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime();            // as have write lock there should be no delays

                                if (dbRhythm == null) {
                                    RhythmSqlImpl sourceRhythm = mRhythms.resolveRhythmKey(intentKey, BeatTree.BASIC, false);
                                    dbRhythm = mRhythms.makeEditFromSourceRhythm(sourceRhythm);
                                }

                                AndroidResourceManager.logd(LOG_TAG, "initDataForEditor: completed setup in background (db)");
                                completeDataInit(dbRhythm);
                            } catch (RhythmEncoder.UnrecognizedFormatException e) { // can't happen
                                AndroidResourceManager.loge(LOG_TAG, "initDataForEditor: this should never be seen", e);
                            } catch (RhythmsContentProvider.RhythmWriteLockNotHeldException e) {
                                AndroidResourceManager.loge(LOG_TAG, "initDataForEditor: program error", e);
                            } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
                                AndroidResourceManager.loge(LOG_TAG, "initDataForEditor: program error", e);
                            }
                            finally {
                                mRhythms.releaseWriteLockOnPlayerEditorRhythm();
                                mRhythms.releaseReadLockOnPlayerEditorRhythm();
                                mResourceManager.saveTransaction();
                            }
                        }
                    }
                });
    }

    private void completeDataInit(RhythmSqlImpl rhythm) {

        mPlayRhythmView.setupDraughter(rhythm);
        startListening(rhythm);

        mIsInitComplete = true; // ready to populate views on ui thread (see onStart())
        mActivity.dataLoaded();

        if (mIsStartedAndAwaitingInitComplete) { // onStart() tests for initComplete, if it isn't then call run() now so the title is setup
            mGuiManager.runInGuiThread(this);
        }
    }

    public BeatTrackerBinder getBeatTrackerBinder() {
		return mBeatTrackerComm;
	}

    /**
	 * called from the activity when established want to read a rhythm (could be after data loading)
	 * @param rhythmPlayingHolder
	 */
	void initData(RhythmPlayingFragmentHolder rhythmPlayingHolder) {
		this.mRhythmPlayingDataHolder = rhythmPlayingHolder;

        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "initData: waiting for lock on player rhythm");
            }

            RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();
            if (rhythm == null) {
                AndroidResourceManager.logv(LOG_TAG, "initData: playerEditorRhythm is null on rhythms");
            }
            else {
                AndroidResourceManager.logv(LOG_TAG, String.format("initData: playerEditorRhythm = %s, %s", rhythm.getKey(), rhythm.getName()));
            }

            if (mIsEditor) {
                initDataForEditor(rhythm);
            }
            else {
                initDataForPlayer(rhythm);
            }
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "initData: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }
	}

    void setUpFabBtn() {

        if (mFabBtn1 == null) {
            AndroidResourceManager.logw(LOG_TAG, "setUpFabBtn: unable to set up, no fab1");
            return;
        }

        mFabBtn2 = getActivity().findViewById(R.id.fab_2); // there might not be one

        // play buttons will animate or have a translation except where not editor and not animating
        RelativeLayout parent = (RelativeLayout) mTitleBar.getParent();
        View playControls = parent.findViewById(R.id.play_buttons_bar);
        playControls.setVisibility(View.VISIBLE);

        if (mIsEditor) {
            mFabBtn2.setOnClickListener(this);
            mFabBtn2.setVisibility(View.VISIBLE);

            if (mIsOpenAnimateFab) {
                float playControlsMoveFromY = (mGuiManager.getScreenHeight() / 2f       // mid screen
                        - playControls.getHeight() / 2f)                                // less half the height of the controls
                        - playControls.getY();                                          // subtract the actual pos for the translation
                animateFab(mFabBtn2, true, playControls, playControlsMoveFromY);
            }
            else { // just scale the button, and as not animating the title background, just hide it
                mFabBtn2.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fab_scale_up_anim));
                View titleBackgrd = mTitleBar.findViewById(R.id.title_background);
                titleBackgrd.setVisibility(View.INVISIBLE);
            }
        }

        else {
            mFabBtn1.setOnClickListener(this);
            mFabBtn1.setVisibility(View.VISIBLE);

            if (mIsOpenAnimateFab) {
                View adViewParent = parent.findViewById(R.id.ad_view_parent);
                float playControlsMoveFromY = (((adViewParent.getY()                    // the position of the ads (it's just above it)
                        - playControls.getHeight())                                     // less its own height
                        - getResources().getDimension(R.dimen.vertical_item_separation))// less spacing
                        - playControls.getY());                                         // subtract the actual pos for the translation
                animateFab(mFabBtn1, false, playControls, playControlsMoveFromY); // move fab2 to where fab1 needs to be
            }
            else {
                mFabBtn1.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.fab_scale_up_anim));
            }
        }
    }

    private void animateFab(FloatingActionButton fabBtn, boolean isEnteringEditor, View playControls, float playControlsMoveFromY) {

        mIsOpenAnimateFab = false; // don't want this triggered again

        // get the location of the other fab btn and animate from it
        int[] coords = new int[2];
        fabBtn.getLocationOnScreen(coords);
        String msg = String.format("animateFab: coords from getLocOnScreen = %s, %s", coords[0], coords[1]);
        AndroidResourceManager.logv(LOG_TAG, msg);

        int startX = mAnimateInFabX - coords[0];
        int startY = mAnimateInFabY - coords[1];

        msg = String.format("animateFab: animate the fab startx/y = %s, %s, args = %s, %s, coords = %s, %s", startX, startY, mAnimateInFabX, mAnimateInFabY, coords[0], coords[1]);
        AndroidResourceManager.logv(LOG_TAG, msg);

        fabBtn.setTranslationX(startX);
        fabBtn.setTranslationY(startY);

        Resources res = getResources();
        // duration depends on the distance, the larger the device the longer it takes
        long duration = res.getInteger(R.integer.open_rhythm_animation_duration);
        int startDelay = 250; // give the activity a chance to load fully otherwise the beginning gets lost

        ObjectAnimator moverX = ObjectAnimator.ofFloat(fabBtn, "translationX", startX, 0f);
        ObjectAnimator moverY = ObjectAnimator.ofFloat(fabBtn, "translationY", startY, 0f);

        AnimatorSet animatorSet = new AnimatorSet();
        DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
        AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();
        // also move the title background down
        View titleBackgrd = mTitleBar.findViewById(R.id.title_background);
        titleBackgrd.setVisibility(View.VISIBLE);
        ObjectAnimator titleBackY = null, playControlsY = ObjectAnimator.ofFloat(playControls, "translationY", playControlsMoveFromY, 0f);

        playControls.setTranslationY(playControlsMoveFromY); // starts off where moving from

        if (isEnteringEditor) {
            moverX.setInterpolator(decelerateInterpolator);
            moverY.setInterpolator(accelerateInterpolator);
            titleBackY = ObjectAnimator.ofFloat(titleBackgrd, "translationY", 0f, titleBackgrd.getHeight());
            AndroidResourceManager.logd(LOG_TAG, String.format("animateFab: entering editor, title trans y from 0 to %s", titleBackgrd.getHeight()));
            animatorSet.playTogether(moverX, moverY, titleBackY, playControlsY);
        }
        else { // reverse the movement, the whole reason for using 2 fab buttons is that
            // if just have one and translate it to the position wanted, it doesn't then
            // track the opening (upward) movement of the title bar (ie. where rhythm name is)
            // and so has to be a child of that title bar, and then the problem is that
            // when animating downwards it's hidden by parents and there seems no easy way to
            // avoid that
            moverX.setInterpolator(accelerateInterpolator);
            moverY.setInterpolator(decelerateInterpolator);
            titleBackgrd.setTranslationY(titleBackgrd.getHeight());
            titleBackY = ObjectAnimator.ofFloat(titleBackgrd, "translationY", titleBackgrd.getHeight(), 0f);
            AndroidResourceManager.logd(LOG_TAG, String.format("animateFab: exiting editor, title trans y from %s to 0", titleBackgrd.getHeight()));

            // the issue is that the fab button that is animating to position is clipped by
            // its parent layout, so have the other fab button (if exists) also
            // trace the same movement, but switch it out before the end

            if (fabBtn.equals(mFabBtn1) && mFabBtn2 != null) {
                // fab2 is already in the starting place, startX/Y are negative, so negating them should produce the correct spot
                mFabBtn2.setVisibility(View.VISIBLE);
                ObjectAnimator mover2X = ObjectAnimator.ofFloat(mFabBtn2, "translationX", 0f, -startX);
                ObjectAnimator mover2Y = ObjectAnimator.ofFloat(mFabBtn2, "translationY", 0f, -startY);
                mover2X.setInterpolator(accelerateInterpolator);
                mover2Y.setInterpolator(decelerateInterpolator);
                animatorSet.playTogether(moverX, moverY, mover2X, mover2Y, titleBackY, playControlsY);

                // add another animator to make the fab2 button disappear
                ObjectAnimator fab2Visible = ObjectAnimator.ofInt(mFabBtn2, "visibility", View.VISIBLE, View.INVISIBLE);
                fab2Visible.setStartDelay(startDelay + duration); // have it invisible at the end, so can click on fab1 under it!
                fab2Visible.setDuration(10); // 10 ms
                fab2Visible.start();
            }
            else {
                AndroidResourceManager.logw(LOG_TAG, "animateFab: #2 entering player, either got fab2 as param, or fab2 is null... not expecting it that way");
                animatorSet.playTogether(moverX, moverY, titleBackY, playControlsY);
            }
        }

        animatorSet.setStartDelay(startDelay); // wait a bit
        animatorSet.setDuration(duration);

        animatorSet.start();
    }

    /**
     * Make a smooth animation from the rhythmsList elements to the activity.
     * Called from onNewIntent() in the ui thread after have read the new rhythm from the db
     * @param rhythmImgCoords
     * @param rhythmNameCoords
     */
    public void transitionInImageAndName(int[] rhythmImgCoords, int[] rhythmNameCoords) {

        int[] coords = new int[2];
        mPlayRhythmView.getLocationOnScreen(coords);
        float offsetX = coords[0] - mPlayRhythmView.getX();
        float offsetY = coords[1] - mPlayRhythmView.getY();
        float startScaleX = (float)rhythmImgCoords[2] / mPlayRhythmView.getWidth();
        float startScaleY = (float)rhythmImgCoords[3] / mPlayRhythmView.getHeight();

        ObjectAnimator moverX = ObjectAnimator.ofFloat(mPlayRhythmView, "translationX", rhythmImgCoords[0] - offsetX, 0f);
        float imageMovementY = rhythmImgCoords[1] - offsetY;
        ObjectAnimator moverY = ObjectAnimator.ofFloat(mPlayRhythmView, "translationY", imageMovementY, 0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(mPlayRhythmView, "alpha", .2f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mPlayRhythmView, "scaleX", startScaleX, 1.f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mPlayRhythmView, "scaleY", startScaleY, 1.f);

        TextView rhythmName = (TextView) mLayoutView.findViewById(R.id.rhythm_name);
        rhythmName.getLocationOnScreen(coords);
        Resources res = getResources();
        float nameOffsetX = (float)coords[0] - rhythmName.getX() + res.getDimension(R.dimen.activity_horizontal_margin);
        float nameOffsetY = (float)coords[1] - rhythmName.getY() + res.getDimension(R.dimen.title_bar_name_top_margin);

        float nameMovementX = rhythmNameCoords[0] - nameOffsetX;
        ObjectAnimator nameMoverX = ObjectAnimator.ofFloat(rhythmName, "translationX", nameMovementX, 0f);
        float nameMovementY = rhythmNameCoords[1] - nameOffsetY;
        ObjectAnimator nameMoverY = ObjectAnimator.ofFloat(rhythmName, "translationY", nameMovementY, 0f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.playTogether(moverX, moverY, scaleX, scaleY, alpha, nameMoverX, nameMoverY);

        // duration depends on the distance, the larger the device the longer it takes
        long duration = res.getInteger(R.integer.open_rhythm_animation_duration);

//        AndroidResourceManager.logw(LOG_TAG, String.format("transitionInImageAndName: viewy=%.2f, listviewy=%s, offsety=%.2f, onscreeny=%s, disty=%.2f, density=%s, duration=%s"
//                , rhythmName.getY(), rhythmNameCoords[1], nameOffsetY, coords[1], imageMovementY, res.getDisplayMetrics().densityDpi, duration));

        animatorSet.setDuration(duration);
        animatorSet.start();
    }

    /**
     * Implements runnable to avoid creating a new class just to update the gui items when itemChanged() is called
     * in a non-ui thread (which it pretty much always is)
     */
    @Override
    public void run() {
        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "run: waiting for lock on player rhythm");
            }

            RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();

            if (!mIsEditor) {
                ((PlayRhythmsActivity)mActivity).setNewRhythmMenuItem(rhythm);
                getRhythmTags(rhythm);  // before setTitle() as there the views are made to show tags
            }

            setTitle(rhythm.getName()); // if no rhythm will throw NPE which is ok, it's an error but will cause the activity to restart with error

            AndroidResourceManager.logd(LOG_TAG, String.format("run: name=%s, bpm=%s", rhythm.getName(), rhythm.getBpm()));

            if (mBpmBtn != null) {
                mBpmBtn.setBpm(rhythm.getBpm());
            }

            if (mRepeatsBtn != null) {
                mRepeatsBtn.setRepeatOption(mPlayerState.getRepeatOption(), mPlayerState.getNTimes());
            }

            if (mIsStartedAndAwaitingInitComplete) {    // essentially was just waiting for initComplete, so nothing else is needed but the title setting
                mIsStartedAndAwaitingInitComplete = false;
                AndroidResourceManager.logd(LOG_TAG, String.format("run: done, just needed to set the title after start() in init not complete yet, name=%s", rhythm.getName()));
                return;
            }

            // this could conceivably fail if an undo has happened with restored the rhythm from a cloned memento (eg. undoing a save from editor)
            // if that does happen, how to recover gracefully...
            try {
                final PlayedRhythmDraughter rhythmDraughter = mPlayRhythmView.getRhythmDraughter();
                if (mLastChangeWasSimpleEdit || mAwaitingRearrangeRhythm) {
                    if (!mLastChangeUiUpdatedBeatTree || mAwaitingRearrangeRhythm) {
                        AndroidResourceManager.logd(LOG_TAG, "run: calling arrangeRhythm()");
                        rhythmDraughter.arrangeRhythm(true, false);
                    }
                }
                else if (rhythm.getBeatDataCacheNano() != rhythmDraughter.getDataCachedNano()) {
                    AndroidResourceManager.logd(LOG_TAG, "run: calling updateBeatsData()");
                    rhythmDraughter.updateBeatsData(true);
                }
                else {
                    AndroidResourceManager.logd(LOG_TAG, "run: beat data cache same, so not calling updateBeatsData()");
                }

                if (mPlayRhythmView != null) { // some change happens, make sure the draughter is redrawn
                    mPlayRhythmView.invalidate();
                }

                // notify the activity to update any open fragments (rhythmsList or rhythmBeatTypes)
                mActivity.notifyRhythmChanged();
            }
            catch (Exception e) {
                // the above condition has happened, best to allow the draughter to clear the cache and recreate it
                // because there'll be a db access can't init the model directly
                AndroidResourceManager.logw(LOG_TAG, "run: updateBeatsData() threw exception, perhaps after resume from recents, but recovering by re-init", e);
//                AndroidGuiManager.logTrace("run exception", LOG_TAG);
            }
            finally {
                mAwaitingRearrangeRhythm = mLastChangeWasSimpleEdit = false; // set in causeRhythmRedraw() & itemChanged()
            }

        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "startListening: program error", e);
        }
        catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "run: exception likely after return from recents menu and library/imagestore reset", e);
            mActivity.restartActivityWithFailureMessage(R.string.non_critical_error_restart_activity);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }
    }

    private void setTitle(RhythmSqlImpl rhythm) {
        setTitle(rhythm != null ? rhythm.getName() : null);
    }

    public void setTitle(String name) {

        TextView titleView = mActivity.findViewById(mIsEditor ? R.id.edit_rhythm_title : R.id.rhythm_name);
        if (name != null && titleView != null) {
            if (mIsEditor) {
                titleView.setText(getString(R.string.editRhythmLabel, name));
            }
            else {
                titleView.setText(name);
                initTitleExpandedViews();
            }
        }
    }

    private void showBpmDialog() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(AndroidResourceManager.DIALOG_FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }

        ft.addToBackStack(null);

        new BpmDialog().show(ft, AndroidResourceManager.DIALOG_FRAGMENT_TAG);
    }

    private void showRepeatsDialog() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(AndroidResourceManager.DIALOG_FRAGMENT_TAG);
        if (prev != null) {
            ft.remove(prev);
        }

        ft.addToBackStack(null);

        new RepeatsDialog().show(ft, AndroidResourceManager.DIALOG_FRAGMENT_TAG);
    }

    /**
     * Listen for clicks for playing rhythms
     * @param v
     */
    @Override
    public void onClick(View v) {
        try {
            int vId = v.getId();

            switch (vId) {
                case R.id.play_button : {
                    if (checkBeatTrackerAvailable()) {                      // already been playing, either play or pause, don't do the ad check
                        boolean newValue = !mBeatTrackerComm.isPlaying();
                        mBeatTrackerComm.togglePlayPause(newValue);
                        mPlayBtn.updateImage(newValue);
                    }
                    else if (!mActivity.isCheckingForConsentForAds()) {       // true means need consent AND asking for it now
                        initService(true);
                        mPlayBtn.updateImage(true); // starting it playing, so need to show a pause btn
                    }
                    break;
                }
                case R.id.stop_button : {
                    stopPlaying();
                    break;
                }
                case R.id.bpm_button_press :
                case R.id.bpm_button : {
                    showBpmDialog();
                    break;
                }
                case R.id.repeats_button_press :
                case R.id.repeats_button : {
                    showRepeatsDialog();
                    break;
                }
                case R.id.fab_1 : {
                    if (mActivity instanceof PlayRhythmsActivity) {
                        // fab is set to edit
                        try {
                            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                                AndroidResourceManager.logd(LOG_TAG, "onClick: waiting for lock on player rhythm");
                            }

                            final RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();
                            if (rhythm != null) {
                                ((PlayRhythmsActivity) mActivity).startEditRhythmActivity(rhythm.getKey(), true);
                            }
                            else {
                                AndroidResourceManager.logw(LOG_TAG, "onClick: fab button pressed, but no rhythm held to edit");
                            }
                        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
                            AndroidResourceManager.loge(LOG_TAG, "onClick: program error", e);
                        } finally {
                            mRhythms.releaseReadLockOnPlayerEditorRhythm();
                        }
                    }
                    else {
                        AndroidResourceManager.logw(LOG_TAG, "onClick: fab button pressed, but nothing implemented for it yet");
                    }
                    break;
                }
                case R.id.fab_2 :
                    ((EditRhythmActivity)mActivity).saveRhythm();
                    break;
                case R.id.title_bar:
                case R.id.more_info_vw:
                    toggleTitleLayoutExpanded();
                    break;

                case R.id.change_name_tags_btn : {
                    ((PlayRhythmsActivity) mActivity).showChangeNameAndTagsDialog();
                    break;
                }
                default : {
                    AndroidResourceManager.logw(LOG_TAG, "onClick for unknown view");
                }
            }

            if (mPlayRhythmView != null) { // make sure drawing begins
                mPlayRhythmView.invalidate();
            }
        } catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "onClick: unexpected error", e);
            mActivity.restartActivityWithFailureMessage(R.string.non_critical_error_restart_activity);
        }

    }

    public boolean isPendingServiceToPlay() {
        return mIsPendingServiceToPlay;
    }

    public long getKeepDrawingUntil() {
        return mKeepRedrawingUntil;
    }

    public void resetKeepRedrawingUntil() {
        mKeepRedrawingUntil = 0L;
    }

    public PlayedRhythmDraughter getRhythmDraughter() {
        return mPlayRhythmView.getRhythmDraughter();
    }

    private class BeatTrackerServiceConnection implements ServiceConnection {

        /**
         * Could be initiated either because the user pressed play, or because the activity is reattaching (see onAttach())
         * and a rebind is happening to an existing service
         * @param name
         * @param service
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            boolean isPlaying = false, isPaused;

            try {
                mBeatTrackerComm = (BeatTrackerBinder) service;

                isPlaying = mBeatTrackerComm.isServiceAlive() && mBeatTrackerComm.isPlaying(); // for setting the correct image, see next if...
                isPaused = mBeatTrackerComm.isServiceAlive() && mBeatTrackerComm.isPaused();

                if (mIsPendingServiceToPlay) { // initiated by pressing play button, so start playing
                    mIsPendingServiceToPlay = false;

                    while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                        AndroidResourceManager.logd(LOG_TAG, "onServiceConnected: waiting for lock on player rhythm");
                    }
                    final RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();
                    mBeatTrackerComm.setModel(rhythm);
                    mBeatTrackerComm.togglePlayPause(true);
                    isPlaying = true;
                }

                if (isPlaying || isPaused) {
                    keepScreenAwakeWhilePlayingIfSet(true);

                    final PlayedRhythmDraughter rhythmDraughter = mPlayRhythmView.getRhythmDraughter();
                    if (rhythmDraughter != null) {
                        rhythmDraughter.setBeatTracker(mBeatTrackerComm);
                    }
                }
                else { // could be when returning to the app after rhythm has stopped playing (via notification, or repeats limit reached)
                    AndroidResourceManager.logd(LOG_TAG, "onServiceConnected: beat tracker isn't playing or service is not alive, unbinding");
                    mActivity.unbindService(mServiceConnection);
                    mServiceConnection = null;
                    mBeatTrackerComm = null;
                }

            } catch (Exception e) {
                AndroidResourceManager.loge(LOG_TAG, "onServiceConnected: unexpected exception", e);
            } finally {
                mRhythms.releaseReadLockOnPlayerEditorRhythm();
            }

            if (mPlayBtn != null) { // during onStart() this could be called before play button assigned
                AndroidResourceManager.logd(LOG_TAG, String.format("onServiceConnected: update image playing=%s", isPlaying));
                mPlayBtn.updateImage(isPlaying);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AndroidResourceManager.logd(LOG_TAG, "onServiceDisconnected: called");

            mBeatTrackerComm = null;
            mWasBoundToService = false;
            mServiceConnection = null;

            keepScreenAwakeWhilePlayingIfSet(false);
        }

    }

    private void keepScreenAwakeWhilePlayingIfSet(boolean entering) {
        if (mSettingsManager.isKeepScreenAwakeWhilePlaying()) {
            if (entering) {
                AndroidResourceManager.logv(LOG_TAG, "keepScreenAwakeWhilePlayingIfSet: keep the screen on");
                mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            else {
                AndroidResourceManager.logv(LOG_TAG, "keepScreenAwakeWhilePlayingIfSet: turn it off");
//                AndroidGuiManager.logTrace("keepScreenAwakeWhilePlayingIfSet: turn it off", LOG_TAG);
                mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

}
