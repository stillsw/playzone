package com.stillwindsoftware.keepyabeat.gui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ListView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;

/**
 * Super class for the 2 list views that swipe on and off and have a dark background
 * Created by tomas on 08/07/16.
 */

public abstract class SwipeableListFragment  extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnTouchListener, Animator.AnimatorListener, LibraryListener, Runnable {

    private static final String LOG_TAG = "KYB-"+SwipeableListFragment.class.getSimpleName();

    private static final int BUNDLE_KEY_LIST_CLOSED = 0;
    private static final int BUNDLE_KEY_LIST_OPEN = 1;
    private static final String BUNDLE_KEY_LIST_STATE = "list_state";

    // flag set after first cursor load so onResume() can reload as needed
    boolean mHasLoaded = false, mHasLoadStarted = false, // for binder service connection, may need to reload when have it
            mIsListStateOpen = false, mIsWaitingForDataToSlideOpen = false, mIsLeftSlide;

    private GestureDetector mSwipeOffGestureDetector;

    protected AndroidResourceManager mResourceManager;
    SettingsManager mSettingsManager;

    protected KybActivity mActivity;
    private ImageView mDarkBackground;
    private View mBaseView;

    // resources
    private float mEndSlideDarkBackgroundAlpha;
    private int mSlideTotalDuration;

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);

        // store for during load/reload
        mActivity = (KybActivity) activity;
        KybApplication mApplication = ((KybApplication) mActivity.getApplication());
        mResourceManager = mApplication.getResourceManager();
        mSettingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager();
        Resources res = getResources();
        mSlideTotalDuration = res.getInteger(R.integer.fragment_slide_in_duration);
        mEndSlideDarkBackgroundAlpha = res.getFraction(R.fraction.fragment_drop_back_alpha, 1, 1);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final ListView lv = getListView();
        lv.setOnTouchListener(this);

        makeSwipeOffGesture();

        mIsListStateOpen = savedInstanceState != null
                && savedInstanceState.getInt(BUNDLE_KEY_LIST_STATE, BUNDLE_KEY_LIST_CLOSED) == BUNDLE_KEY_LIST_OPEN;

        if (mIsListStateOpen) {
            showOpen(false);
        }
        else {
            showClosed(false);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        stopListening();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopListening();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_KEY_LIST_STATE, mIsListStateOpen ? BUNDLE_KEY_LIST_OPEN : BUNDLE_KEY_LIST_CLOSED);
    }

    /**
     * Communicate upwards to the activity providing not scrolling
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (v.getId() == R.id.dark_background) { // first touch on the dark background just close
            AndroidResourceManager.logd(LOG_TAG, "onTouch: on dark backgrd, closing");
            showClosed(true);
            return true;
        }

        // notify the activity, if scrolling is happening
        // don't pass the movement events
        if (mSwipeOffGestureDetector != null
                && mSwipeOffGestureDetector.onTouchEvent(event)) {
            AndroidResourceManager.logd(LOG_TAG, "onTouch: swipe off gesture detected, closing");
            showClosed(true);
            return true;
        }

        return false; // don't prevent propagation of event
    }

    protected void showOpen(boolean slide) {

        startListening();

        mIsWaitingForDataToSlideOpen = slide && !hasListAdapter();

        if (!hasListAdapter()) {
            fillData();
        }

        if (slide) {
//            AndroidResourceManager.logw(LOG_TAG, "showOpen: slide deferred = "+mIsWaitingForDataToSlideOpen);
            if (!mIsWaitingForDataToSlideOpen) {
                slideOpen();
            }
        }
        else {
//            AndroidResourceManager.logw(LOG_TAG, "showOpen: no slide");

            mBaseView.setVisibility(View.VISIBLE);
            mDarkBackground.setVisibility(View.VISIBLE);
            mDarkBackground.setOnTouchListener(this);

            mBaseView.setTranslationX(0f);
            mDarkBackground.setAlpha(getResources().getFraction(R.fraction.fragment_drop_back_alpha, 1, 1));

            View toolbarCustomView = getToolbarCustomView();
            if (toolbarCustomView != null) {
                toolbarCustomView.setTranslationX(0f);
            }
        }

        mIsListStateOpen = true;
    }

    protected abstract void fillData();

    void slideOpen() {
//        AndroidResourceManager.logw(LOG_TAG, "slideOpen:");

        if (mIsWaitingForDataToSlideOpen) {
            mIsWaitingForDataToSlideOpen = false;
        }

        mBaseView.setVisibility(View.VISIBLE);

        float distToGo = Math.abs(mBaseView.getTranslationX());                     // abs so works for both sides
        float deltaAnimation = distToGo / mBaseView.getWidth();
        int durationRemaining = (int) (mSlideTotalDuration * deltaAnimation);
        float startAlpha = mEndSlideDarkBackgroundAlpha - (mEndSlideDarkBackgroundAlpha * deltaAnimation);

        mDarkBackground.setAlpha(startAlpha);

        ObjectAnimator mover = ObjectAnimator.ofFloat(mBaseView, "translationX", mBaseView.getTranslationX(), 0f);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mDarkBackground, "alpha", startAlpha, mEndSlideDarkBackgroundAlpha);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(durationRemaining);

        View toolbarCustomView = getToolbarCustomView();
        if (toolbarCustomView != null) {
            ObjectAnimator menuMover = ObjectAnimator.ofFloat(toolbarCustomView, "translationX", mBaseView.getTranslationX(), 0f);
            animatorSet.playTogether(mover, alphaAnimator, menuMover);
        }
        else {
            animatorSet.playTogether(mover, alphaAnimator);
        }

        mDarkBackground.setVisibility(View.VISIBLE);
        mDarkBackground.setOnTouchListener(this);

        animatorSet.start();
    }

    protected void showClosed(boolean slide) {

        stopListening();

        int width = mBaseView.getWidth();
        int targetX = mIsLeftSlide ? -width : width;

        slide = slide && width > 0;             // not sliding if no width

        int durationRemaining = 0;              // nor if no duration
        float deltaAnimation = 0f;

        if (slide) {
            float distToGo = mIsLeftSlide
                    ? Math.abs(mBaseView.getTranslationX() + width)  // slides to left, so transX can only be <= 0
                    : width - mBaseView.getTranslationX();           // to right

            deltaAnimation = distToGo / width;
            durationRemaining = mBaseView.getTranslationX() != 0f ? (int) (mSlideTotalDuration * deltaAnimation) : mSlideTotalDuration;
        }

        slide = slide && durationRemaining >= 0;

        if (slide) {
            float startAlpha = mEndSlideDarkBackgroundAlpha * deltaAnimation;

            ObjectAnimator mover = ObjectAnimator.ofFloat(mBaseView, "translationX", mBaseView.getTranslationX(), targetX);
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(mDarkBackground, "alpha", startAlpha, 0.f);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.setInterpolator(new AccelerateInterpolator());
            animatorSet.setDuration(durationRemaining);

            View toolbarCustomView = getToolbarCustomView();
            if (toolbarCustomView != null) {
                ObjectAnimator menuMover = ObjectAnimator.ofFloat(toolbarCustomView, "translationX", mBaseView.getTranslationX(), targetX);
                animatorSet.playTogether(mover, alphaAnimator, menuMover);
            }
            else {
                animatorSet.playTogether(mover, alphaAnimator);
            }

            animatorSet.addListener(this);
            animatorSet.start();
        }
        else {
            //noinspection ResourceType
            mBaseView.setTranslationX(targetX); // note width = 0 when created before a layout has happened
            mBaseView.setVisibility(View.INVISIBLE);
            mDarkBackground.setVisibility(View.GONE);

            View toolbarCustomView = getToolbarCustomView();
            if (toolbarCustomView != null) {
                //noinspection ResourceType
                toolbarCustomView.setTranslationX(targetX);
            }
        }

        mDarkBackground.setOnTouchListener(null);
        mIsListStateOpen = false;
    }

    protected View getToolbarCustomView() {
        return null;
    }

    /**
     * Called from Activity.doEdgeDiscovery() because a touch has happened
     * by the edge, so need to make sure the view is populated and ready
     */
    protected void prepareForOpening() {

        if (!hasListAdapter()) {
            fillData();
        }

        mDarkBackground.setAlpha(0.f);
        mDarkBackground.setVisibility(View.VISIBLE);
        mDarkBackground.setOnTouchListener(this);

        //noinspection ResourceType
        mBaseView.setTranslationX(mIsLeftSlide ? -mBaseView.getWidth() : mBaseView.getWidth());
        mBaseView.setVisibility(View.VISIBLE);
    }

    void followDrag(float desiredX) {

        int left = mBaseView.getLeft();
        float distToGo = mIsLeftSlide ? Math.abs(desiredX) : desiredX - left;
        float transX = mIsLeftSlide ? desiredX : distToGo;
        float deltaAnimation = distToGo / mBaseView.getWidth();
        float startAlpha = mEndSlideDarkBackgroundAlpha - (mEndSlideDarkBackgroundAlpha * deltaAnimation);

        mDarkBackground.setAlpha(startAlpha);
        //noinspection ResourceType
        mBaseView.setTranslationX(transX);

        View toolbarCustomView = getToolbarCustomView();
        if (toolbarCustomView != null) {
            //noinspection ResourceType
            toolbarCustomView.setTranslationX(transX);
        }
    }

    /**
     * Either slide to open or closed depending on where closest
     */
    void stopDragging() {
        float halfW = mBaseView.getWidth() / 2.f;
        if (mBaseView.getWidth() - Math.abs(mBaseView.getTranslationX()) > halfW) {
            showOpen(true);
        }
        else {
            showClosed(true);
        }
    }

    View getTrackView() {
        return mBaseView;
    }

    boolean isListStateOpen() {
        return mIsListStateOpen;
    }

    protected abstract boolean hasListAdapter();

    boolean isLeftSlide() {
        return mIsLeftSlide;
    }

    void assignSlideControlViews(View listContainer) {
        mDarkBackground = (ImageView) listContainer.findViewById(R.id.dark_background);
        mBaseView = listContainer.findViewById(R.id.top_relative_layout);
    }

    private void makeSwipeOffGesture() {
        mSwipeOffGestureDetector = new GestureDetector(mActivity,
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD = 100;
                    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                        if (e2 == null || e1 == null) {
                            AndroidResourceManager.logw(LOG_TAG, "onFling: can't evaluate since e1 or e2 is null");
                            return false;
                        }
                        float distanceX = e2.getX() - e1.getX();
                        float distanceY = e2.getY() - e1.getY();
                        boolean val = Math.abs(distanceX) > Math.abs(distanceY)        // further on x than y
                                && Math.abs(distanceX) > SWIPE_THRESHOLD            // over threshold
                                && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD   // and speed
                                && ((mIsLeftSlide && distanceX < 0                       // and it's to the left
                                || (!mIsLeftSlide && distanceX > 0)));
                        if (val) {
                            AndroidResourceManager.logv(LOG_TAG, String.format("makeSwipeOffGesture.onFling: detected, dist=(x=%.2f, y=%.2f), evX=(1=%.2f, 2=%.2f), evY=(1=%.2f, 2=%.2f), abs=(x%.2f, y=%.2f), velocityX=x%.2f"
                                    ,distanceX,distanceY,e1.getX(),e2.getX(), e1.getY(),e2.getY(),Math.abs(distanceX),Math.abs(distanceY),velocityX));
                        }
                        return val;                             // or the right
                    }
                });
    }

    protected void startListening() {
    }

    private void stopListening() {
        mResourceManager.getListenerSupport().removeListener(this);
    }

    /**
     * Tag search buttons could be out of sync (eg. one got deleted)
     */
    @Override
    public void itemChanged(int changeId, String key, int natureOfChange, LibraryListenerTarget[] listenerTargets) {
        mResourceManager.getGuiManager().runInGuiThread(this);
    }

    /**
     * itemChanged() is called off the ui thread, so to avoid another class this class implements Runnable
     */
    @Override
    public void run() {
    }

    // listen to animations to hide the dark background at the end of closing

    @Override
    public void onAnimationStart(Animator animation) {

    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if (!mIsListStateOpen) {
            mDarkBackground.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        if (!mIsListStateOpen) {
            mDarkBackground.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAnimationRepeat(Animator animation) {

    }

}
