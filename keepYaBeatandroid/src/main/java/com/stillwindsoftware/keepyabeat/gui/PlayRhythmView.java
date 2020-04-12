package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.EditRhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.platform.AndroidFont;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidImage;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.BeatBasicActionHandler;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;
import com.stillwindsoftware.keepyabeat.player.EditRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;


/**
 * The image on which rhythms are drawn.
 * When the image is on a fragment that is loaded onCreate(), the first time it is loaded
 * there is no data holder fragment for it, so the onLayout() can't set up the draughter.
 * To cope with this, onDraw() detects that setup is still needed and invalidates
 * delayed to do the setup.
 */
public class PlayRhythmView extends AppCompatImageView implements DrawingSurface {

	private static final String LOG_TAG = "KYB-"+PlayRhythmView.class.getSimpleName();
	
    private MutableRectangle mRect;

	// need a reference to the canvas in onDraw(), save for the duration of that
	// method only 
	private Canvas mOnDrawCanvas;

    private DraughtTargetGoverner mTargetGoverner;
    private AndroidResourceManager mResourceManager;
    private AndroidGuiManager mGuiManager;
    private PlayRhythmFragment mPlayRhythmFragment;
    private PlayedRhythmDraughter mRhythmDraughter;
    private RhythmsContentProvider mRhythms;

    private boolean mIsFirstDraw = true; // will be unset on first draw

    // constructors required by the framework
	
	public PlayRhythmView(Context context) {
		super(context);
	}

	public PlayRhythmView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public PlayRhythmView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	void init(AndroidResourceManager resourceManager, RhythmsContentProvider rhythms, boolean isEditor, PlayRhythmFragment playRhythmFragment, KybActivity activity) {
	    mResourceManager = resourceManager;
	    mGuiManager = (AndroidGuiManager) mResourceManager.getGuiManager();
	    mRhythms = rhythms;
	    mPlayRhythmFragment = playRhythmFragment;
        initTargetGoverner(isEditor, activity);
    }

    private void initTargetGoverner(boolean isEditor, KybActivity activity) {
        mTargetGoverner = new DraughtTargetGoverner(mResourceManager, false);

        // additional settings
        mTargetGoverner.setRhythmAlignment(DraughtTargetGoverner.RhythmAlignment.MIDDLE);

        Resources res = getResources();
        DisplayMetrics metrics = res.getDisplayMetrics();

        // settings depend on land/port config
        boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        int maxNormalBeats = isPortrait ? 6 : 10;
        int bottomMargin = getDrawnRhythmBottomMargin(res, !isPortrait);
        int topMargin = getDrawnRhythmTopMargin(res, activity, !isPortrait);
        int horizontalMargin = (int) res.getDimension(R.dimen.activity_horizontal_margin);

        mTargetGoverner.setHorizontalRhythmMargins(horizontalMargin, horizontalMargin);
        mTargetGoverner.setVerticalRhythmMargins(topMargin, bottomMargin);
        mTargetGoverner.setMaxBeatsAtNormalWidth(maxNormalBeats);

        mTargetGoverner.setPlayed(true);

        AndroidFont fullBeatsFontNormal = new AndroidFont(metrics, 18);
        AndroidFont fullBeatsFontSmall = new AndroidFont(metrics, 12);

        // where to draw numbers for the beats and what font to use (alternatives if the first doesn't fit)
        // settings can override this (see persistence states)
        mTargetGoverner.setDrawNumbers(
                isEditor ? DraughtTargetGoverner.DrawNumbersPlacement.INSIDE_TOP : DraughtTargetGoverner.DrawNumbersPlacement.ABOVE
                ,  isEditor ? res.getDimension(R.dimen.edit_draw_numbers_inside_top_margin) : DraughtTargetGoverner.DrawNumbersPlacement.ABOVE.getDefaultMargin()
                , res.getDimension(R.dimen.draw_numbers_image_margin)
                , fullBeatsFontNormal
                , fullBeatsFontSmall);

        mTargetGoverner.setHelpTipsFont(fullBeatsFontNormal);
    }

    /**
     * Called once have the data from the PlayRhythmFragment (may have to read it from db)
     * @param rhythm
     */
    void setupDraughter(RhythmSqlImpl rhythm) {
        if (mRhythmDraughter == null) {
            mRhythmDraughter = rhythm instanceof EditRhythmSqlImpl ? new EditRhythmDraughter((EditRhythm) rhythm) : new PlayedRhythmDraughter(rhythm);
            if (mRect != null) {    // onLayout already called
                AndroidResourceManager.logd(LOG_TAG, "setupDraughter: have rect, call initTarget on new draughter");
                mRhythmDraughter.initTarget(mTargetGoverner, this);
                mRhythmDraughter.initDrawing();
            }
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "setupDraughter: update existing draughter with new rhythm");
            mRhythmDraughter.initModel(rhythm, true);
            mRhythmDraughter.initDrawing();
        }

        if (mPlayRhythmFragment.checkBeatTrackerAvailable()) {
            mRhythmDraughter.setBeatTracker(mPlayRhythmFragment.getBeatTrackerBinder());
        }
    }



    @Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

        if (changed) {
            // store the on screen coordinates for touch presses (see PlayRhythmsActivity.doTouch...())
            getLocationOnScreen(mPlayRhythmFragment.mViewOnScreenCoords);

            // need the rect for onDraw() to set up the draughter
            if (mRect == null) {
                mRect = new MutableRectangle(mPlayRhythmFragment.mViewOnScreenCoords[0], mPlayRhythmFragment.mViewOnScreenCoords[1], right - left, bottom - top);
                if (mRhythmDraughter != null) { // setupDraughter already called
                    AndroidResourceManager.logd(LOG_TAG, "onLayout: just created rect, have draughter calling initTarget");
                    mRhythmDraughter.initTarget(mTargetGoverner, this);
                    mRhythmDraughter.initDrawing();
                }
            } else {
                AndroidResourceManager.logd(LOG_TAG, "onLayout: modified rect");
                mRect.setXYWH(mPlayRhythmFragment.mViewOnScreenCoords[0], mPlayRhythmFragment.  mViewOnScreenCoords[1], right - left, bottom - top);
            }
        }

	}
	
	@Override
	public MutableRectangle getDrawingRectangle() {
		return mRect;
	}

	/**
	 * Call the rhythm draughter and save the canvas for the calls that will come
	 * from DrawingSurface as the rhythm is drawn
	 */
	@Override
	protected void onDraw(Canvas canvas) {

		if (mRhythmDraughter != null && mRect != null) {
			mOnDrawCanvas = canvas;

			try {
                try {
                    boolean didDrawAnimate = drawRhythm();

                    if (didDrawAnimate) {
                        // returns true if animating, then get it to redraw as soon as ready
                        // otherwise some other trigger will cause it to draw again, such as play starting
                        // (tried postInvalidateDelayed() for 60fps but it caused bad tearing)
                        invalidate();
                    }
                } catch (NullPointerException e) {
                    // have seen where drawRhythm is in the middle and gets a NPE from apparently good obj
                    // I think it's to do with a context disappearing (eg. config change)
                    // anyway, catch it here and see if it sorts itself out
                    AndroidResourceManager.loge(LOG_TAG, "onDraw: probably config change or return from another app");
                }
            }
			finally {
				mOnDrawCanvas = null;
				if (mIsFirstDraw) {
				    mIsFirstDraw = false;
                    mPlayRhythmFragment.setUpFabBtn();
                    ((KybActivity)mPlayRhythmFragment.getActivity()).notifyDrawingStarted(); // initAds() called here
                }
			}
		}
        else {
            if (mTargetGoverner != null && mRect != null) {
                // the view needs setting up, but not during onDraw()... post to the UI thread aim for the next draw (16ms)
                AndroidResourceManager.logd(LOG_TAG, "onDraw: setting up delayed, waiting for rhythm");
                postInvalidateDelayed(16);
            }
            else {
                AndroidResourceManager.logv(LOG_TAG, "onDraw: not drawing rhythm, no target governer or rect");
                invalidate();
            }
        }
	}

    /**
     * Returns true if the current draw includes some animation, called from PlayRhythmView.onDraw()
     * Rather than call this method anywhere else, instead call causeRhythmRedraw() which will postInvalidate()
     * the view.
     * @return
     */
    boolean drawRhythm() {

        try {
            while (!mRhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) { // wait the minimum for it
                AndroidResourceManager.logd(LOG_TAG, "drawRhythm: waiting for lock on player rhythm");
            }

            final boolean rhythmPlaying = mResourceManager.isRhythmPlaying();
            RhythmSqlImpl rhythm = mRhythms.getPlayerEditorRhythm();

//        AndroidResourceManager.logw(LOG_TAG, "drawRhythm: "+rhythm.getName()+" key="+rhythm.getKey());

            if (mRhythmDraughter != null && rhythm != null && mTargetGoverner != null) {
                boolean isAnimating = mRhythmDraughter.drawRhythm();
                isAnimating |= mRhythmDraughter.drawAfterRhythm(); // draw anything not inline

                boolean retVal = isAnimating || mPlayRhythmFragment.isPendingServiceToPlay();

                // for efficiency don't check the keep drawing time if it's going to redraw anyway
                long keepRedrawingUntil = mPlayRhythmFragment.getKeepDrawingUntil();
                if (!retVal && keepRedrawingUntil > 0L) {
                    // but if it's been set check it's still > current time
                    if (keepRedrawingUntil > System.currentTimeMillis()) {
                        retVal = true;
                    }
                    else { // otherwise clear it
                        mPlayRhythmFragment.resetKeepRedrawingUntil();
                    }
                }

                retVal = retVal || rhythmPlaying;

                if (!retVal && mGuiManager.isBlocking()) {
                    // as still blocking best cause some redraw
                    mPlayRhythmFragment.causeRhythmRedraw(PlayRhythmFragment.KEEP_REDRAWING_RHYTHM_TIMING_MILLIS);
                    retVal = true;
                }

                return retVal;
            }
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "drawRhythm: program error", e);
        } finally {
            mRhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        return true; // the caller will invalidate because of this, causing another draw asap
    }
	@Override
	public void drawImage(PlatformImage image, float x, float y, float w, float h) {

		if (mOnDrawCanvas != null) {
//            if (mTransformMatrix != null && !mTransformMatrix.isIdentity()) {
//                mTransformSrc[0] = x; mTransformSrc[1] = y; mTransformSrc[2] = x+w; mTransformSrc[3] = y+h;
//                mTransformMatrix.mapPoints(mTransformDst, mTransformSrc);
//                ((AndroidImage) image).draw(mOnDrawCanvas, (int) mTransformDst[0], (int) mTransformDst[1], (int) mTransformDst[2], (int) mTransformDst[3]);
//            }
//            else {
                ((AndroidImage) image).draw(mOnDrawCanvas, (int) x, (int) y, (int) (x + w), (int) (y + h));
//            }
		}
		else {
            // NOTE: have seen this even when called from onDraw() when have a stale view, ie. after config change
			AndroidResourceManager.logw(LOG_TAG, "drawImage: no canvas, is this during onDraw()?");
		}
	}

	@Override
	public void drawImage(PlatformImage image, float x, float y, float w,
			float h, float clipx, float clipy, float clipw, float cliph) {

		if (mOnDrawCanvas != null) {
			mOnDrawCanvas.save();
			
			mOnDrawCanvas.clipRect(clipx, clipy, clipx + clipw, clipy + cliph);
			drawImage(image, x, y, w, h);
			
			mOnDrawCanvas.restore();
		}
	}

	@Override
	public void drawText(PlatformFont font, int number, float x, float y) {
		drawText(font, Integer.toString(number), x, y);
	}

	@Override
	public void drawText(PlatformFont font, String text, float x, float y) {
		if (mOnDrawCanvas != null) {
			((AndroidFont) font).drawText(mOnDrawCanvas, (int) x, (int) y, text);
		}
		else {
            // NOTE: have seen this even when called from onDraw() when have a stale view, ie. after config change
			AndroidResourceManager.logw(LOG_TAG, "drawText: no canvas, is this during onDraw()?");
		}
	}

	@Override
	public void registerForEvents(PlatformEventListener eventListener) {
        mPlayRhythmFragment.mEventListener = (BeatBasicActionHandler) eventListener;
	}

	@Override
	public boolean triggeredBackEvent() {
		return false;
	}

    /**
     * Apply transforms from SpinOffBeatAnimator and ExplodeBeatAnimator, but the centre coords
     * for the rotation adds the position of the view on the screen (works for TWL), for Android
     * have to take these off again so the rotation is correct for the canvas' own coordinate system
     * @param rotation
     * @param transX
     * @param transY
     */
    @Override
    public void pushTransform(float rotation, float transX, float transY) {
        transX -= mPlayRhythmFragment.mViewOnScreenCoords[0];
        transY -= mPlayRhythmFragment.mViewOnScreenCoords[1];
        if (mOnDrawCanvas != null) {
            mOnDrawCanvas.save();
            mOnDrawCanvas.translate(transX, transY);
            mOnDrawCanvas.rotate(rotation);
            mOnDrawCanvas.translate(-transX, -transY);
        }
    }

    @Override
    public void popTransform() {
        if (mOnDrawCanvas != null) {
            try {
                mOnDrawCanvas.restore();
            } catch (IllegalStateException e) {
                AndroidResourceManager.logw(LOG_TAG, "popTransform: probably overdid the restores", e);
            }
        }
    }

    static int getDrawnRhythmTopMargin(Resources res, KybActivity mActivity, boolean isLandscape) {

        int[] abSizeAttr = new int[] { android.R.attr.actionBarSize };
        int indexOfAttr = 0;
        TypedValue typedValue = new TypedValue();
        TypedArray a = mActivity.obtainStyledAttributes(typedValue.data, abSizeAttr);
        int actionBarHeight = a.getDimensionPixelSize(indexOfAttr, -1);
        a.recycle();

        float fabSize = res.getDimension(R.dimen.fab_size);
        float verticalSpace = res.getDimension(R.dimen.vertical_item_separation);

//        AndroidResourceManager resourceManager = mActivity.getResourceManager();
//        resourceManager.logd(LOG_TAG, String.format("getDrawnRhythmTopMargin: actionBarHeight=%s, fab=%.2f, vert=%.2f",
//                actionBarHeight, fabSize, verticalSpace));

        if (isLandscape && res.getBoolean(R.bool.very_small_width)) {
            return (int) (actionBarHeight
                    + fabSize / 2);                  // a bit less than portrait
        }
        else {
            return (int) (actionBarHeight
                    + (fabSize
                    + verticalSpace));
        }
    }

    static int getDrawnRhythmBottomMargin(Resources res, boolean isLandscape) {

        int defaultHeight = (int) res.getDimension(R.dimen.play_controls_button_height);

        if (isLandscape && res.getBoolean(R.bool.very_small_width)) {
            return defaultHeight / 2;
        }
        else {
            return defaultHeight;
        }
    }

    public PlayedRhythmDraughter getRhythmDraughter() {
        return mRhythmDraughter;
    }
}
