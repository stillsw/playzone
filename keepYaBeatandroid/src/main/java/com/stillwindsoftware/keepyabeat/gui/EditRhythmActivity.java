package com.stillwindsoftware.keepyabeat.gui;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.db.EditRhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.RhythmEditor;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.BeatEditActionHandler;
import com.stillwindsoftware.keepyabeat.player.DrawnBeat;
import com.stillwindsoftware.keepyabeat.player.EditRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.EditedBeat;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

import java.util.TreeMap;

/**
 * Shown after the user chooses to edit a rhythm, and thereafter until the editing is
 * finished, whether in the same session or later.
 * Used PlayRhythmsActivity as the start point for this class, there's quite a lot of
 * overlap which could be refactored later.
 */
public class EditRhythmActivity extends KybActivity
        implements RhythmEditor, View.OnClickListener, DialogInterface.OnShowListener {

    private static final int EDIT_BEAT_ACTIVITY_REQ_CODE = 2000;
    private static final int COPY_BEAT_ACTIVITY_REQ_CODE = 2001;

    private static final String LOG_TAG = "KYB-"+EditRhythmActivity.class.getSimpleName();

    private static final String RHYTHM_EDIT_COMMAND_ERROR = "error from rhythm edit command";
    public static final int UNEXPECTED_MISSING_DATA_ERROR = -999; // used as a result code in onActivityResult() so avoiding the normal Ok/Cancelled (-1/0)


    private GestureDetector mFlingOrLongPressGestureDetector;

    // other fragments
    private AlertDialog mCancelEditingDialog; // cancel edits, see onBackPressed()
    private boolean mIsCancelled = false; // see isCancelled()
    private ChooseBeatTypeDialog mChooseBeatTypeDialogFragment;

    // set during initiateNewDragBeat(), see comments there
    private boolean mIsAwaitingNewBeatDrag;

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.edit_rhythm_layout);

        initPlayerEditorToolbar();

        initPlayerEditorFragments();
        mPlayRhythmFragment.initData(mRhythmPlayingHolder);     // if can satisfy w/o reading db, will immediately call back to dataLoaded()

        if (!divertToWelcomeScreen()) { // will start the activity over top of this one
            // test for an error from rhythm edit command, show an alert
            // see resetRhythmAfterError()
            if (getIntent().getBooleanExtra(RHYTHM_EDIT_COMMAND_ERROR, false)) {

                getIntent().removeExtra(RHYTHM_EDIT_COMMAND_ERROR); // only want this one time

                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.unexpectedErrorTitle))
                        .setMessage(getString(R.string.UNEXPECTED_SAVE_ERROR))
                        .setPositiveButton(R.string.ok_button, null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .create().show();

            }
        }

        initAds();
	}

    @Override
    public void onSaveInstanceState(Bundle outState) {
//        AndroidResourceManager.logd(LOG_TAG, String.format("onSaveInstanceState: choose beat type fragment is present = %s", mChooseBeatTypeDialogFragment));
        if (mChooseBeatTypeDialogFragment != null && mRhythmPlayingHolder != null) {
            mRhythmPlayingHolder.putBeatTypeChooserWasOpenOnClose();
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * add calc of swipe up gesture for BeatEditActionHandler to touch capabilities
     */
    @Override
    protected void initTouchCapabilities() {
        super.initTouchCapabilities();

        mFlingOrLongPressGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

                        if (mPlayRhythmFragment != null && mPlayRhythmFragment.mEventListener != null
                                && e1 != null && e2 != null
                                && isFlingVerticalOnDragBeat(e1, e2, velocityX, velocityY, true, true, ((BeatEditActionHandler)mPlayRhythmFragment.mEventListener).getDragBeat())) {
                            AndroidResourceManager.logd(LOG_TAG, "onFling: test vertical fling returned true, so swiping it off");
                            mPlayRhythmFragment.mEventListener.handleSwipeOff(velocityY);
                            return true;
                        }

                        // not handled
                        return false;

                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        handleLongPress(e);
                    }
                });

        mGuiManager.setEditingDimensions();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.edit_rhythm_menu, menu);
		initCommonMenuItems(menu);
        MenuItem addMenuItem = menu.findItem(R.id.menu_add_change_beat_type);
        addMenuItem.getIcon().setAlpha(255); // it uses the same icon as new player menu which can have the icon alpha set down
		return true;
	}

    /**
     * Convoluted way to get a reference to the view of the action toolbar, setting up a view and then hiding it
     * @param menu
     * @return
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean res = super.onPrepareOptionsMenu(menu);
        ActionBar toolbar = getSupportActionBar();
        toolbar.setCustomView(R.layout.rhythms_search_bar);
        toolbar.setDisplayShowCustomEnabled(true);
        View toolbarCustomView = toolbar.getCustomView();
        setOnTouchOnToolbarParentView(toolbarCustomView.getParent());
        toolbarCustomView.setVisibility(View.GONE);
        return res;
    }

    @Override
	public boolean onOptionsItemSelected(final MenuItem item) {

		// if a subzone is active (open) and the user presses a menu item, the way to
		// let the player framework know is to tell it where the screen coordinates are.
		// these are approximate though, so keep x as before, but set y to where
		// actionbar is
		mGuiManager.setLastKnownPointerLocation(mLastTouchX, getSupportActionBar().getHeight());

        // cause a redraw so active sub zone is closed, during undo it won't probably complete as
        // the rhythm is reset
        if (mPlayRhythmFragment != null) {
            mPlayRhythmFragment.causeRhythmRedraw();
        }

        // closing the rbt list if open
        int itemId = item.getItemId();
        if (mRhythmBeatTypesFragment != null
                && mRhythmBeatTypesFragment.isListStateOpen()
                && itemId != R.id.menu_rhythm_beat_types
                && itemId != R.id.menu_undo) {
            mRhythmBeatTypesFragment.showClosed(true);
        }

		switch (itemId) {
            case R.id.menu_rhythm_beat_types:
                toggleRhythmBeatTypes();
                return true;
//            case R.id.menu_save_rhythm:
//                saveRhythm();
//                return true;
            case R.id.menu_add_change_beat_type:
                showBeatTypePopupLov(null); // no callback
                return true;
            case R.id.menu_magic_wand:
                showMagicWandDialog();
                return true;
            case R.id.menu_undo:
            case R.id.menu_remove_ads:
                return super.onOptionsItemSelected(item);
            case R.id.menu_help:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.rhythmEditorLink)));
                startActivity(intent);
                return true;
            default:
                AndroidResourceManager.loge(LOG_TAG, "onOptionsItemSelected: menu item selected which isn't coded for!!");
                return false;
		}
	}

    private void showMagicWandDialog() {
        new MagicWandDialog().show(getSupportFragmentManager(), MagicWandDialog.LOG_TAG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PlayRhythmsActivity.CHANGE_RHYTHM_DETAILS_ACTIVITY_REQ_CODE) {

            if (resultCode == RESULT_OK) {
                closeEditorAndDeleteEditRhythm();
            }
        }
        else if (requestCode == EDIT_BEAT_ACTIVITY_REQ_CODE || requestCode == COPY_BEAT_ACTIVITY_REQ_CODE) {

            if (resultCode == UNEXPECTED_MISSING_DATA_ERROR) { // seen it happen where no cache on edit rhythm after overnight left in activity
                resetRhythmAfterError();
                return; // calls finish() itself but just to make sure no more of this happens
            }

            if (mPlayRhythmFragment != null && mPlayRhythmFragment.mEventListener != null) { // expect always there
                BeatEditActionHandler beatHandler = (BeatEditActionHandler) mPlayRhythmFragment.mEventListener;
                beatHandler.clearSelection();
                mPlayRhythmFragment.causeRhythmRedraw();
            }

            if (resultCode == RESULT_OK) {

                if (mPlayRhythmFragment == null || mPlayRhythmFragment.getRhythm() == null) {
                    AndroidResourceManager.logd(LOG_TAG, "onActivityResult: holder not ready (null or no rhythm) = "+mRhythmPlayingHolder);
                    return;
                }

                EditRhythmSqlImpl editRhythm = (EditRhythmSqlImpl) mPlayRhythmFragment.getRhythm();

                RhythmEditCommand cmd = editRhythm.takeCommand();

                if (cmd != null) {  // make the changes here
                    cmd.execute();
                }
                else {
                    AndroidResourceManager.logd(LOG_TAG, "onActivityResult: no command present on rhythm to execute");
                }
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, "onActivityResult: cancelled out edit beat changes");
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    void saveRhythm() {
        final EditRhythm editRhythm = (EditRhythm) getRhythm();
        if (editRhythm.getCurrentState() == EditRhythm.RhythmState.New
                || editRhythm.getCurrentState() == EditRhythm.RhythmState.Unsaved) {

            // new rhythm pop up dialog to get name/tags
            // only on successful save will the editor close by calling the method here
            Intent intent = SetRhythmNameAndTagsActivity.newInstance(this, null, null, true);
            startActivityForResult(intent, PlayRhythmsActivity.CHANGE_RHYTHM_DETAILS_ACTIVITY_REQ_CODE);
        }
        else {
            // existing rhythm just save directly
            mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(

                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            new RhythmCommandAdaptor.RhythmSaveEditCommand(editRhythm).execute();
                        }
                    },
                    new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            closeEditorAndDeleteEditRhythm();
                        }
                    }
            );
        }
    }

    /**
     * Don't allow edge to find rbt if dragging
     * @param event
     * @return
     */
    @Override
    protected boolean doEdgeDiscovery(MotionEvent event) {

        if ((mPlayRhythmFragment != null
				&& mPlayRhythmFragment.mEventListener != null
				&& ((BeatEditActionHandler)mPlayRhythmFragment.mEventListener).getDragBeat() != null)) {
            return false;
        }

        if (hasOpenDialog()) {
            return false;
        }

        return super.doEdgeDiscovery(event);
    }

    /**
	 * Look for an event listener to handle the back press
	 * if it handles it, don't pass it up to the kybActivity
	 */
	@Override
	public void onBackPressed() {
		if (mPlayRhythmFragment != null
				&& mPlayRhythmFragment.mEventListener != null
				&& mPlayRhythmFragment.mEventListener.handleBack()) {

            if (mPlayRhythmFragment.mPlayRhythmView != null) {
                mPlayRhythmFragment.mPlayRhythmView.invalidate(); // want it to redraw
            }
			return;
		}

        if (mRhythmBeatTypesFragment != null &&
                mRhythmBeatTypesFragment.isListStateOpen()) { // close open dialog_simple_list
            mRhythmBeatTypesFragment.showClosed(true);
            return;
        }

        if (mChooseBeatTypeDialogFragment != null) {
            popChooseBeatTypeDialog();
            return;
        }

        AlertDialog.Builder bld = new AlertDialog.Builder(this)
                .setTitle(R.string.confirmCancelEditsTitle)
                .setMessage(R.string.confirmCancelEditsBody)
                .setPositiveButton(R.string.ok_button, null)
                .setNegativeButton(R.string.cancel_button, null);

        mCancelEditingDialog = bld.create();
        mCancelEditingDialog.setCanceledOnTouchOutside(false);  // to prevent keyboard open when click outside
        mCancelEditingDialog.setOnShowListener(this);
        mCancelEditingDialog.show();
	}

    /**
     * Called from ChooseBeatTypeDialog.onLongPress() to tell this class that a selection has taken
     * place so it can initiate dragging already (ie. so it doesn't record try to start a new drag
     * again)
     * @param beatTypeKey
     * @param drawnDimension
     * @param lastDownEventPointerId
     */
    protected void initiateNewDragBeat(final String beatTypeKey, final MutableRectangle drawnDimension, int lastDownEventPointerId) {
        mIsAwaitingNewBeatDrag = true;

        mMotionEventsPointer = lastDownEventPointerId; // handles as though onTouch() here recorded a down event
        popChooseBeatTypeDialog(); // close the (pretend) dialog

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {
                        return mResourceManager.getLibrary().getBeatTypes().lookup(beatTypeKey);
                    }
                },
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        BeatEditActionHandler actionHandler = (BeatEditActionHandler) mPlayRhythmFragment.mEventListener;
                        actionHandler.dragNewBeat((BeatType) mParam, new Location2Df(mLastTouchX, mLastTouchY), mLastTouchX, mLastTouchY, drawnDimension);
                        mPlayRhythmFragment.causeRhythmRedraw();
                        mIsAwaitingNewBeatDrag = false;
                    }
                });
    }

    private void popChooseBeatTypeDialog() {
        if (mChooseBeatTypeDialogFragment == null) {
            AndroidResourceManager.loge(LOG_TAG, "popChooseBeatTypeDialog: dialog not found to pop");
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().remove(mChooseBeatTypeDialogFragment).commit();
        fm.executePendingTransactions();

        View darkBgrd = findViewById(R.id.dark_behind_dialog);
        darkBgrd.setVisibility(View.GONE);
        darkBgrd.setOnClickListener(null);

        mChooseBeatTypeDialogFragment = null;
//        getSupportActionBar().show();
    }

    /**
     * Called from onCancel() and onDismiss() in the dialog. When it's a normal dialog
     * handled by the OS, need to know it's closed. After config change it can get
     * screwed up.
     */
    public void chooseBeatTypeClosed() {
        mChooseBeatTypeDialogFragment = null;
    }

    /**
     * Allow public access so ChooseBeatTypeDialog can also set the coords
     * @param ev
     */
    @Override
    public void captureTouchCoords(MotionEvent ev) {
        super.captureTouchCoords(ev);
    }

    /**
     * While mIsAwaitingNewBeatDrag is set (see initiateNewDragBeat()), this method will ignore
     * the motion events.
     * @param event
     */
    @Override
    public void recordMotionEvent(MotionEvent event) {
        if (mIsAwaitingNewBeatDrag) {
            AndroidResourceManager.logd(LOG_TAG, "recordMotionEvent: ignore event as awaiting new beat drag to complete (in onLongClick of dialog)");
            // don't act on motion events until ready, see initiateNewDragBeat()
        } else {
            // continue with the motion event
            super.recordMotionEvent(event);
        }
    }

//    @Override
//    protected boolean popFragmentWithTag(String tag, boolean isPartial) {
//        // bit crap, as this if... happens in the superclass as well, perhaps will refactor it to be better
//        if (!mCustomManagedFragmentHolder.isFragmentTransactionPermitted()) {
//            AndroidResourceManager.logw(LOG_TAG, String.format("popFragmentWithTag: unable to complete for tag=%s because fragment holder reports already doing something", tag));
//            return false;
//        }
//
//        if (ChooseBeatTypeDialog.LOG_TAG.equals(tag) && mChooseBeatTypeDialogFragment != null) {
//            resetTag(tag); // as the superclass also does for other tags
//
//            boolean success = (mCustomManagedFragmentHolder.popFragment(ChooseBeatTypeDialog.LOG_TAG,
//                    this, mPlayRhythmFragment, mChooseBeatTypeDialogFragment, R.id.centrefragmentcontainer
//                    , null) != null);
//
//            if (success) {
//                mChooseBeatTypeDialogFragment = null;
//                //setHasOpenDialog(false); // should be unset in the fragment's onDetach(), but it's not happening when the touch is down
//                                         // not sure if that's just a problem with this fragment, or all of the custom managed ones
//                                         // any bugs related to this will tell, although at the moment it's not causing any other issues
////                findViewById(R.id.centrefragmentcontainer).setVisibility(View.GONE);
//            }
//
//            return success;
//        }
//        else {
//            return super.popFragmentWithTag(tag, isPartial);
//        }
//    }

    /**
     * Dialog for confirm cancel editting
     * @param dialogInterface
     */
    @Override
    public void onShow(DialogInterface dialogInterface) {
        mCancelEditingDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this);
        mCancelEditingDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        // not sure these won't become menu items again, so some duplication of code with onOptionsItemsSelected()
        int viewId = view.getId();
        if (/*view.getId() == R.viewId.menu_save_rhythm ||*/
                viewId == R.id.menu_add_change_beat_type|| viewId == R.id.menu_magic_wand) {
            mGuiManager.setLastKnownPointerLocation(view.getX(), view.getY());

            // cause a redraw so active sub zone is closed, during undo it won't probably complete as
            // the rhythm is reset
            if (mPlayRhythmFragment != null) {
                mPlayRhythmFragment.causeRhythmRedraw();
            }

            // don't go further if closing the open beat type dialog (which is a fragment behaving like a dialog)
            if (mRhythmBeatTypesFragment.isListStateOpen()) {
                return;
            }
        }

        if (viewId == R.id.menu_add_change_beat_type) {
            showBeatTypePopupLov(null); // no callback
        }
        else if (viewId == R.id.menu_magic_wand) {
            showMagicWandDialog();
        }
        else if (viewId == R.id.dark_behind_dialog) {
            popChooseBeatTypeDialog();
        }

        // trap the ok and cancel buttons on the dialog that's shown to confirm cancel
        else if (view instanceof Button && getString(R.string.ok_button).equals(((Button) view).getText().toString())) {
            mCancelEditingDialog.dismiss();
            closeEditorAndDeleteEditRhythm();
        }
        else if (view instanceof Button && getString(R.string.cancel_button).equals(((Button) view).getText().toString())) {
            mCancelEditingDialog.dismiss();
        }
    }

    public void closeEditorAndDeleteEditRhythm() {
        mIsCancelled = true; // used for implementing RhythmEditor (see isCancelled())

        ((SettingsManager)mResourceManager.getPersistentStatesManager()).setEditorActive(false);
        if (mPlayRhythmFragment.isPlaying()) {
            mPlayRhythmFragment.stopPlaying();
        }

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(

                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        // remove the edit rhythm in the db, note commit param means transaction is handled
                        ((RhythmsContentProvider) mResourceManager.getLibrary().getRhythms()).deleteEditRhythmToDb(true);
                        getUndoableCommandController().clearStack();
                    }
                },
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        Intent i = new Intent(EditRhythmActivity.this, PlayRhythmsActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_NO_ANIMATION);

                        i.putExtra(PlayRhythmsActivity.OPEN_ANIMATE_FAB, true);
                        View fabBtn = mPlayRhythmFragment.getFab2();

                        if (fabBtn != null) {
                            int[] coords = new int[2];
                            fabBtn.getLocationOnScreen(coords);
                            i.putExtra(PlayRhythmsActivity.FAB_X_POS, coords[0]);
                            i.putExtra(PlayRhythmsActivity.FAB_Y_POS, coords[1]);
                            AndroidResourceManager.logd(LOG_TAG, String.format("closeEditorAndDeleteEditRhythm: animate the fab %s, %s", coords[0], coords[1]));
                        }
                        startActivity(i);
                        finish();
                    }
                }
        );
    }

    @Override
    public void dataLoaded() {

        if (mGuiManager.isUiThread()) {
            testForOpenBeatTypeChooser();
        }
        else {
            mGuiManager.runInGuiThread(new Runnable() {
                @Override
                public void run() {
                    testForOpenBeatTypeChooser();
                }
            });
        }
    }

    private void testForOpenBeatTypeChooser() {
        try {
            // use this chance to setup the pretend dialog if needed, isChooseBeatTypeDialogActiveAfterConfigChange()
            // is already called in the setup
            if (mRhythmPlayingHolder != null && mRhythmPlayingHolder.takeBeatTypeChooserWasOpenOnClose()) {
                AndroidResourceManager.logd(LOG_TAG, "testForOpenBeatTypeChooser: re-establish the chooser");
                showChooseBeatDialogAsFragment(getSupportFragmentManager(), true);
            }
        } catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "testForOpenBeatTypeChooser: have seen NPE, possibly expecting things to be there... so ordering bug", e);
        }
    }

    private boolean handleLongPress(MotionEvent ev) {
        if (mTrackedView != null) {
            AndroidResourceManager.logd(LOG_TAG, "handleLongPress: abort long press, something is tracking, likely edge discovery");
            return false;
        }

        if (hasOpenDialog()) {
            AndroidResourceManager.logd(LOG_TAG, "handleLongPress: abort long press, dialog already open");
            return false;
        }

        if (mPlayRhythmFragment != null && mPlayRhythmFragment.mEventListener != null) {

            float x = ev.getRawX() - mPlayRhythmFragment.mViewOnScreenCoords[0];
            float y = ev.getRawY() - mPlayRhythmFragment.mViewOnScreenCoords[1];

            mPlayRhythmFragment.mEventListener.handlePointerSelect(x, y);

            // there should be a selected beat if the long press was on one
            // then need to clear out move settings
            if (((BeatEditActionHandler)mPlayRhythmFragment.mEventListener).getSelectedBeat() != null) {

                AndroidResourceManager.logd(LOG_TAG, "handleLongPress: selection on a beat");
                if (mPlayRhythmFragment.mPlayRhythmView != null) {
                    mPlayRhythmFragment.mPlayRhythmView.invalidate(); // want it to redraw
                    return true;
                }
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, String.format("handleLongPress: selection was not on a beat (x/y=%.2f/%.2f, last x/y=%.2f/%.2f, view x/y=%d/%d", x, y, ev.getRawX(), ev.getRawY()
                        , mPlayRhythmFragment.mViewOnScreenCoords[0], mPlayRhythmFragment.mViewOnScreenCoords[1]));
//                ((BeatEditActionHandler)mRhythmPlayingHolder.mEventListener).debugBeatPosition(0);
            }
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "handleLongPress: long press no listener to handle it");
        }

        return false;
    }

    @Override
    protected boolean detectGesture(MotionEvent event) {
        return (mFlingOrLongPressGestureDetector != null
                && mFlingOrLongPressGestureDetector.onTouchEvent(event));
    }

    /**
     * Called from handleIfFling to detect that the movement is up or down, it also
     * ensures that there is not any significant horizontal movement as well.
     */
    protected boolean isFlingVerticalOnDragBeat(MotionEvent e1, MotionEvent e2,
                                      float velocityX, float velocityY, boolean up, boolean down, EditedBeat.EditedFullBeat dragBeat) {

        if (dragBeat != null) {
            float distX = e2.getX() > e1.getX() ? e2.getX() - e1.getX() : e1.getX() - e2.getX();
            float maxHoriShift = dragBeat.getCurrentDrawn()[DrawnBeat.W_DIMEN] / 2f;
            if (/*Math.abs(velocityX) > maxHoriShift ||*/ distX > maxHoriShift) { // velocity screws it up
                AndroidResourceManager.logd(LOG_TAG, String.format("isFlingVerticalOnDragBeat: too much horizontal movement for swipe up/down, velocityX=%.2f distX=%.2f max=%.2f",
                        velocityX, distX, maxHoriShift));
                return false;
            }

            float distY = e2.getY() > e1.getY() ? e2.getY() - e1.getY() : e1.getY() - e2.getY();
            float minFlingDistance = dragBeat.getCurrentDrawn()[DrawnBeat.H_DIMEN] / 3f; // make the distance dependent on the height of the beat
                                                                             // to avoid a small movement on a high beat becoming a delete
            float minFlingVelocity = minFlingDistance * 20f; // try this
            if (Math.abs(velocityY) < minFlingVelocity || distY < minFlingDistance) {
                AndroidResourceManager.logd(LOG_TAG, String.format("isFlingVerticalOnDragBeat: too small/slow movement for swipe up/down, velocityY=%.2f distY=%.2f", velocityY, distY));
                return false;
            }

            if (up && velocityY < 0 || down && velocityY > 0) {
                AndroidResourceManager.logd(LOG_TAG, String.format("isFlingVerticalOnDragBeat: swipe up or down and allowed, velocityY=%.2f (min=%.2f), distY=%.2f (min=%.2f)", velocityY, minFlingVelocity, distY, minFlingDistance));
                return true;
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, String.format("isFlingVerticalOnDragBeat: swipe up/down not allowed, up=%s down=%s upAllowed=%s downAllowed=%s",
                        (velocityY < 0), (velocityY > 0), up, down));
                return false;
            }
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "isFlingVerticalOnDragBeat: drag beat is null anyway");
        }

        return false;
//        return (velocityX > mMinFlingVelocity
//                && e2.getX() - e1.getX() > mMinFlingDistance);
    }

    /**
     * Use to detect dragging
     * @param ev
     * @return
     */
    @Override
    protected boolean doTouchMove(MotionEvent ev) {
        if (super.doTouchMove(ev)) {
            return true; // superclass tests for dragging beat types otherwise returns false
        }

        // event listener is edit beat handler, check for dragging
        if (mPlayRhythmFragment != null && mPlayRhythmFragment.mEventListener != null) {

            float x = ev.getRawX() - mPlayRhythmFragment.mViewOnScreenCoords[0];
            float y = ev.getRawY() - mPlayRhythmFragment.mViewOnScreenCoords[1];

            // this test may not be needed since handlePointerDrag() only inits dragging anyway,
            // but keeping it for now for the return true
            if (((BeatEditActionHandler)mPlayRhythmFragment.mEventListener).getDragBeat() == null) {
                // will init dragging if over a valid target
                mPlayRhythmFragment.mEventListener.handlePointerDrag(x, y);
                boolean startedDragging = ((BeatEditActionHandler) mPlayRhythmFragment.mEventListener).getDragBeat() != null;

                if (mPlayRhythmFragment.mPlayRhythmView != null) {
                    mPlayRhythmFragment.mPlayRhythmView.invalidate(); // want it to redraw
                }

                return startedDragging;
            }
        }

        return false;
    }

//--------------------------- RhythmEditor methods used by core classes

    @Override
    public UndoableCommandController getUndoableCommandController() {
        return ((AndroidGuiManager)mResourceManager.getGuiManager()).getRhythmEditorUndoableCommandController(true);
    }

    @Override
    public PendingTask openRhythmBeatTypes() {
//        AndroidGuiManager.logTrace("Not implemented and should not be called", this.getClass().getSimpleName());
        return null;
    }

    @Override
    public EditRhythm getEditRhythm() {
        return (EditRhythm) getRhythm();
    }

    @Override
    public EditRhythmDraughter getEditRhythmDraughter() {
        return (EditRhythmDraughter) mPlayRhythmFragment.mPlayRhythmView.getRhythmDraughter();
    }

    @Override
    public void refreshRhythmBeatTypes() {
        notifyRhythmChanged();
    }

    @Override
    public boolean isCancelled() {
        return mIsCancelled;
    }

    /**
     * Rhythm edit command failure causes this to fire, it means the data on the db has been rolled back and
     * the changes shown in the editor are out of sync and must be reset again
     */
    @Override
    public void resetRhythmAfterError() {
        Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();

        intent.putExtra(RHYTHM_EDIT_COMMAND_ERROR, true);
        overridePendingTransition(0, 0);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    /**
     * Called from gui manager showRhythmEditorPopupMenu() which is called from BeatEditActionHandler.handlePointerSelect()
     * when long pressed on a beat
     * @param beatActionHandler
     * @param menuOptionsMap
     */
    public void showSelectedBeatPopupMenu(BeatEditActionHandler beatActionHandler, TreeMap<String, Runnable> menuOptionsMap) {
        mRhythmPlayingHolder.putBeatPopupMenuOptions(beatActionHandler, menuOptionsMap);
        new EditBeatOptionsListDialog().show(getSupportFragmentManager(), EditBeatOptionsListDialog.LOG_TAG);
    }

    /**
     * Called from gui manager method of the same name when the popup menu option on the beat is chosen
     * when long pressed on a beat
     * @param beatActionHandler
     */
    public void showEditBeatDialog(BeatEditActionHandler beatActionHandler) {
        Intent intent = EditBeatDialogActivity.newInstance(this, beatActionHandler.getSelectedBeat().getPosition());
        startActivityForResult(intent, EditRhythmActivity.EDIT_BEAT_ACTIVITY_REQ_CODE);
    }

    /**
     * Called from gui manager method of the same name when the popup menu option on the beat is chosen
     * when long pressed on a beat
     * @param beatActionHandler
     */
    public void showEditBeatNumberingDialog(BeatEditActionHandler beatActionHandler) {
        DrawnBeat.DrawnFullBeat selectedBeat = beatActionHandler.getSelectedBeat();
        int fullBeatNum = selectedBeat.getFullBeatNum();
        String displayLabel = selectedBeat.getDisplayFullBeatNum();
        int position = selectedBeat.getPosition();
        EditBeatNumberingDialog.newInstance(fullBeatNum, displayLabel == null ? Integer.toString(fullBeatNum) : displayLabel, position)
                .show(getSupportFragmentManager(), AddNewOrRenameTagDialog.LOG_TAG);
    }

    /**
     * Called from gui manager method of the same name when the popup menu option on the beat is chosen
     * when long pressed on a beat
     * @param beatActionHandler
     */
    public void showEditBeatCopyDialog(BeatEditActionHandler beatActionHandler) {
        Intent intent = CopyBeatDialogActivity.newInstance(this, beatActionHandler.getSelectedBeat().getPosition());
        startActivityForResult(intent, EditRhythmActivity.COPY_BEAT_ACTIVITY_REQ_CODE);
    }

    /**
     * Called from EditBeatOptionsListDialog when constructing its array adapter
     * @return
     */
    public RhythmPlayingFragmentHolder getRhythmPlayingHolder() {
        return mRhythmPlayingHolder;
    }

    /**
     * Called from gui manager popupBeatTypeLov() which is called from [[either selection of an existing beat and choosing
     * change type] - not from here anymore], or from the add a new beat button
     * @param callback
     */
    public void showBeatTypePopupLov(GuiManager.BeatTypeSelectedCallback callback) {

        mRhythmPlayingHolder.putBeatTypeSelectedCallback(callback);
        FragmentManager fragmentManager = getSupportFragmentManager();

        if (callback != null) {
            mChooseBeatTypeDialogFragment = new ChooseBeatTypeDialog();
            mChooseBeatTypeDialogFragment.show(fragmentManager, ChooseBeatTypeDialog.LOG_TAG);
        }

        else { // it's just a pop up, but to drag it have to make it a fragment not a real dialog
            showChooseBeatDialogAsFragment(fragmentManager, false);
        }
    }

    private void showChooseBeatDialogAsFragment(FragmentManager fragmentManager, boolean existsAlready) {

        if (!existsAlready && mChooseBeatTypeDialogFragment != null) {
            AndroidResourceManager.logw(LOG_TAG, "showChooseBeatDialogAsFragment: already have choose beat type fragment");
        }

        try {
            if (mChooseBeatTypeDialogFragment == null) {
                mChooseBeatTypeDialogFragment = (ChooseBeatTypeDialog) getSupportFragmentManager().findFragmentByTag(ChooseBeatTypeDialog.LOG_TAG);
            }

            if (mChooseBeatTypeDialogFragment == null) {
                if (existsAlready) {
                    AndroidResourceManager.logw(LOG_TAG, "showChooseBeatDialogAsFragment: said already have choose beat type fragment, none found, will try to instantiate");
                }
                mChooseBeatTypeDialogFragment = (ChooseBeatTypeDialog) Fragment.instantiate(this, ChooseBeatTypeDialog.class.getName());
            }

            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.add(R.id.centrefragmentcontainer, mChooseBeatTypeDialogFragment, ChooseBeatTypeDialog.LOG_TAG);
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .addToBackStack(null).commit();

            fragmentManager.executePendingTransactions();
        }
        catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "showChooseBeatDialogAsFragment: exception adding dialog", e);
        }

        // don't put up the dark background if there's a callback (that means it's been created
        // automatically as a real dialog in config change

        if (mRhythmPlayingHolder.getBeatTypeSelectedCallback() == null) {
            findViewById(R.id.centrefragmentcontainer).setVisibility(View.VISIBLE);
            View darkBgrd = findViewById(R.id.dark_behind_dialog);
            darkBgrd.setVisibility(View.VISIBLE);
            darkBgrd.setOnClickListener(this);
        }
    }

    /**
     * Called from gui manager showChangeFullBeatValueDialog() which is called from selection of a beat option to change
     * to irregular length
     * @param callback
     */
    public void showChangeFullBeatValueDialog(EditedBeat.EditedFullBeat editedFullBeat, GuiManager.FullBeatValueChangedCallback callback) {
        mRhythmPlayingHolder.putChangeFullBeatValueParams(new Object[] { editedFullBeat.getValueLevel(), callback });
        new ChooseIrregularBeatLengthDialog().show(getSupportFragmentManager(), ChooseIrregularBeatLengthDialog.LOG_TAG);
    }

    public boolean isAnEditorActivity() {
        return true;
    }

}
