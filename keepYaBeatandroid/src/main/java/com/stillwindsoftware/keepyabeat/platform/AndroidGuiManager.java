package com.stillwindsoftware.keepyabeat.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.TreeMap;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.GenericAsyncAndUiTaskPair;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.DbBackedUndoableCommandController;
import com.stillwindsoftware.keepyabeat.db.KybContentProvider;
import com.stillwindsoftware.keepyabeat.gui.EditBeatDialogActivity;
import com.stillwindsoftware.keepyabeat.gui.EditRhythmActivity;
import com.stillwindsoftware.keepyabeat.gui.SetRhythmNameAndTagsActivity;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.model.transactions.Transaction;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.gui.PlayRhythmsActivity;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation.Key;
import com.stillwindsoftware.keepyabeat.player.BeatEditActionHandler;
import com.stillwindsoftware.keepyabeat.player.EditedBeat.EditedFullBeat;

public class AndroidGuiManager implements GuiManager {

	private static final String LOG_TAG = "KYB-"+AndroidGuiManager.class.getSimpleName();
	
	// animations on android will go the speed requested, not actual screen refresh rate
	private static final float DEFAULT_REFRESH_RATE = 60.f;
    private static final float MIN_SWIPE_UP = -8.0f; // based on twl, maybe not enough here

    private volatile long mExclusiveOperationStarted = -1L;

	// updated in UI thread by KybActivity.onTouchEvent(), read by core classes
	// through getPointerLocation() and getPointerRelativeLocation()
	private volatile Location2Df mLastKnownPointerLocation = new Location2Df(0, 0);
    private boolean mIsPointerDown;

	private AndroidResourceManager mResourceManager;
	private KybApplication mApplication;
    private DbBackedUndoableCommandController mEditorUndoController;

    // fps calculated in <init>
    private float mFps;
    private float mMinSwipeUpY, mMaxSwipeUpY;
    private int mScreenWidth, mScreenHeight;

    private AndroidGuiManager(AndroidResourceManager resourceManager) {
		mResourceManager = resourceManager;
		mApplication = resourceManager.getApplication();
		setExpectedFps();
	}

	static AndroidGuiManager getNewInstance(AndroidResourceManager resourceManager) {
		return new AndroidGuiManager(resourceManager);
	}

    public AndroidResourceManager getResourceManager() {
        return mResourceManager;
    }

    @Override
    public boolean isNavigateOnUndo() {
        return false;
    }

    @Override
	public PendingTask assertVolumesOpen(Rhythm rhythm) {
        // only used in undo, so this won't actually be called
        return null;
	}

	/**
	 * Tests flag on resource manager. See PlayRhythmsActivity.onStart()/onStop()
	 * 
	 * @throws IllegalThreadStateException if it's run on the UI thread as
	 * it does block for an answer
	 */
	@Override
	public boolean isPlayerVisible() {
		return isActivityVisible(PlayRhythmsActivity.class);
	}

	/**
	 */
	private boolean isActivityVisible(Class<? extends KybActivity> activityClass) {
		
        Activity latestActivity = mResourceManager.getLatestActivity();
        return (latestActivity != null && latestActivity.getClass().equals(activityClass));
	}

	/**
	 * Many operations must not be called in the UI thread
	 * @return true if so
	 */
    @Override
	public boolean isUiThread() {
		return Looper.getMainLooper().equals(Looper.myLooper());
	}

	@Override
	public void openPlayerWithRhythm(Rhythm openRhythm) {
        // only used in undo, so this won't actually be called
		AndroidResourceManager.logd(LOG_TAG, "openPlayerWithRhythm: use rm="+mResourceManager+" to get latest activity");
			Context context = mResourceManager.getLatestActivity();
			context = context != null ? context : mApplication;
			AndroidResourceManager.logd(LOG_TAG, "openPlayerWithRhythm: starting activity to open rhythm UIthread="
                    + Looper.getMainLooper().equals(Looper.myLooper()) + " context=" + context);
			Intent intent = PlayRhythmsActivity.getIntentToOpenRhythm(
					context, openRhythm.getKey());
			mApplication.startActivity(intent);
	}

	@Override
	public void openRhythmsList() {
		// only used in undo, so this won't actually be called
		Intent intent = new Intent(mApplication, PlayRhythmsActivity.class);
		intent.putExtra(PlayRhythmsActivity.OPEN_RHYTHMS_LIST_PARAM, true);
		mApplication.startActivity(intent);
		AndroidResourceManager.logd(LOG_TAG, "openRhythmsList: started intent to open rhythms dialog_simple_list");
	}

	@Override
	public boolean doesRhythmsSearchContainTag(Tag tag) {
		return ((SettingsManager)mResourceManager.getPersistentStatesManager()).getRhythmSearchTagKeysAsSet().contains(tag.getKey());
	}

	/**
	 * Send it to preferences for saving.
	 * Then depends on Player visibility, send it the tag key anyway, doesn't matter if 
	 * it's received or not here.
	 */
	@Override
	public void addTagToRhythmsSearch(Tag tag) {
        SettingsManager settingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager();
        HashSet<String> rhythmSearchTagKeys = settingsManager.getRhythmSearchTagKeysAsSet();
        String key = tag.getKey();
        if (!rhythmSearchTagKeys.contains(key)) {
            AndroidResourceManager.logd(LOG_TAG, "addTagToRhythmsSearch: add tag to search = "+tag.getName());
            rhythmSearchTagKeys.add(key);
            settingsManager.setRhythmSearchTagKeys(rhythmSearchTagKeys);
            Transaction.endTransactionNoSave(mResourceManager, ListenerSupport.NON_SPECIFIC, settingsManager);
        }
	}

	@Override
	public void showMasterTagsList() {
		// undo will show a toast and not try to show the tags
	}

	@Override
	public void showPlayerPopupMenu(Rhythm rhythm) {
		// requesting a popup menu to show "edit rhythm"
		// depends how going to implement that... if pass long press to draughter via the canvas view
		// then will route back to here... another easier way is probably to just allow an edit button
		// press instead, and then ignore this
	}

	/**
	 * Called from undo() on various beat/sound modification commands. 
	 */
	@Override
	public PendingTask assertBeatsAndSoundsOpen() {
        // undo only shows a toast
        return null;
	}

	/**
	 * Called by BeatEditActionHandler.handlePointerSelect()'s
	 * Means rhythm editor must be active.
	 */
	@Override
	public void showEditBeatDialog(BeatEditActionHandler beatActionHandler) {

        EditRhythmActivity rhythmEditorModule = getEditRhythmCurrentActivity();

        if (rhythmEditorModule != null) {
            rhythmEditorModule.showEditBeatDialog(beatActionHandler);
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "showEditBeatDialog: selection with no rhythm editor (means latest activity not currently set)");
        }
	}

    /**
     * Called by BeatEditActionHandler.handlePointerSelect() to show a popop dialog
     * to change the beat numbering
     * Means rhythm editor must be active.
     */
    @Override
    public void showEditBeatNumberingDialog(BeatEditActionHandler beatActionHandler) {

        EditRhythmActivity rhythmEditorModule = getEditRhythmCurrentActivity();

        if (rhythmEditorModule != null) {
            rhythmEditorModule.showEditBeatNumberingDialog(beatActionHandler);
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "showEditBeatNumberingDialog: selection with no rhythm editor (means latest activity not currently set)");
        }
    }

    /**
     * Called by BeatEditActionHandler.handlePointerSelect() to show a popop dialog
     * to allow the beat to be copied to multi-select other beats
     * Means rhythm editor must be active.
     */
    @Override
    public void showEditBeatCopyDialog(BeatEditActionHandler beatActionHandler) {

        EditRhythmActivity rhythmEditorModule = getEditRhythmCurrentActivity();

        if (rhythmEditorModule != null) {
            rhythmEditorModule.showEditBeatCopyDialog(beatActionHandler);
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "showEditBeatCopyDialog: selection with no rhythm editor (means latest activity not currently set)");
        }
    }

    /**
     * Called by BeatEditActionHandler.handlePointerSelect() to show a popop dialog
     * to change the beat type value (full length or fractional).
     * Means rhythm editor must be active.
     */
    @Override
    public void showChangeFullBeatValueDialog(EditedFullBeat editedFullBeat, FullBeatValueChangedCallback callback) {

        EditRhythmActivity rhythmEditorModule = getEditRhythmCurrentActivity();

        if (rhythmEditorModule != null) {
            rhythmEditorModule.showChangeFullBeatValueDialog(editedFullBeat, callback);
        }
        else {
            Log.w(LOG_TAG, "showChangeFullBeatValueDialog: selection with no rhythm editor (means latest activity not currently set)");
        }
    }


    /**
     * Not needed
	 */
	@Override
	public void stopPlay() {
	}

	/**
     * Not needed
	 */
	@Override
	public void playIterationsChanged(int iterations) {
	}

	/**
	 * Idea is to show a widget that displays the number of the iteration currently playing.
	 * Currently this isn't used
	 */
	@Override
	public void setPanelDisplayNumber(int num) {
		// nothing to do
	}

	/**
	 * DragStoppedHelper calls this when a beat is dropped off. The releaseResources() methods in
	 * PlatformResourceManager also.
	 */
	@Override
	public boolean isRhythmPlaying() {
		return mResourceManager.isRhythmPlaying();
	}

    @Override
    public boolean isRhythmEditorCancelled() {

        KybActivity a = mResourceManager.getLatestActivity();
        if (a == null) {
            AndroidResourceManager.logw(LOG_TAG, "isRhythmEditorCancelled: unable to determine latest activity, likely mid-switch, need a better way");
            return true;
        }

        if (a instanceof EditRhythmActivity) {
            return ((EditRhythmActivity)a).isCancelled();
        }
        else {
            return !a.isAnEditorActivity(); // if it's one of the edit beat dialogs, then the editor is not cancelled
        }

    }

	private EditRhythmActivity getEditRhythmCurrentActivity() {
		Activity a = mResourceManager.getLatestActivity();
        if (a == null) {
            AndroidResourceManager.logw(LOG_TAG, "getEditRhythmCurrentActivity: unable to determine latest activity, likely mid-switch, need a better way");
            return null;
        }
        else if (a instanceof EditRhythmActivity) {
            return (EditRhythmActivity) a;
        }
        else {
            AndroidResourceManager.logw(LOG_TAG, "getEditRhythmCurrentActivity: called, but latest activity is not a rhythm editor, should not happen");
            return null;
        }
	}

	@Override
    public RhythmEditor getRhythmEditor() {
	    return getEditRhythmCurrentActivity();
    }

	@Override
	public void setRhythmEditor(RhythmEditor rhythmEditorModule) {
		mEditorUndoController = null; // don't need to set it because using latest activity, however is the moment to reset the undo controller
	}

	/**
	 * Means rhythm editor must be active, but some commands use this method to determine that
     * (which is a mistake that should be rectified at some point), so need to check latest activity
     * before creating it
	 */
	@Override
	public UndoableCommandController getRhythmEditorUndoableCommandController() {
        return getRhythmEditorUndoableCommandController(false);
	}

    /**
     * Override to provide guaranteed way to get the controller for the undo menu item
     * otherwise some failure to get the latest activity disables the menu item, which is very poor
     * @param noActivityCheck
     * @return
     */
    public UndoableCommandController getRhythmEditorUndoableCommandController(boolean noActivityCheck) {
        KybActivity latestActivity = mResourceManager.getLatestActivity();
        if (noActivityCheck || (latestActivity != null && latestActivity.isAnEditorActivity())) {
            if (mEditorUndoController == null) {
                mEditorUndoController = new DbBackedUndoableCommandController(mResourceManager, mResourceManager.getUndoHistoryLimit(), KybContentProvider.MEMENTO_STACK_KEY_EDITOR);
            }
            return mEditorUndoController;
        }
        else { // return null means it's not in that stack (eg. BeatTypesAndSoundsCommand can be in either)
            AndroidResourceManager.logw(LOG_TAG, String.format("getRhythmEditorUndoableCommandController: latest activity is not a rhythm editor (%s)", latestActivity));
            return null;
        }
    }

    /**
	 * Means rhythm editor must be active.
	 */
	@Override
	public void showRhythmEditorPopupMenu(BeatEditActionHandler beatActionHandler, TreeMap<String, Runnable> menuOptionsMap) {

        EditRhythmActivity rhythmEditorModule = getEditRhythmCurrentActivity();

        if (rhythmEditorModule != null) {
            rhythmEditorModule.showSelectedBeatPopupMenu(beatActionHandler, menuOptionsMap);
		}
        else {
            AndroidResourceManager.logw(LOG_TAG, "showRhythmEditorPopupMenu: selection with no rhythm editor (means latest activity not currently set)");
        }
	}

	/**
	 * UndoablePendingTask.undo() calls this to enable blocking behaviour (ie. so only one
	 * undo can be actioned at a time)
	 * If already exclusive does not re-make it, instead logs an error
	 */
	@Override
	public void setExclusiveOperationInProcess() {

        if (isUiThread()) {
            final String msg = "setExclusiveOperationInProcess: call from UI thread not allowed";
            AndroidResourceManager.loge(LOG_TAG, msg);
            throw new IllegalStateException(msg);
        }

		if (isExclusiveOperationInProcess()) {
			AndroidResourceManager.loge(LOG_TAG, "setExclusiveOperationInProcess: ignoring call when already set");
			return;
		}

        mExclusiveOperationStarted = System.currentTimeMillis();

	}
	
	/**
	 * Normal termination of exclusive operations
	 */
	@Override
	public void unsetExclusiveOperationInProcess() {
		mExclusiveOperationStarted = -1L;		
	}
	
	@Override
	public boolean isExclusiveOperationInProcess() {
		return mExclusiveOperationStarted != -1L;
	}

	/**
	 * Use an asyncTask to post the runnable to the ui thread
	 */
	@Override
	public void runInGuiThread(final Runnable runner) {
	    if (isUiThread()) {
	        runner.run();
        }
        else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    try {
                        runner.run();
                    } catch (Exception e) {
                        if (mResourceManager.isInTransaction()) {
                            AndroidResourceManager.logw(LOG_TAG, "runInGuiThread: produced exception, and already in a transaction, undo it", e);
                            mResourceManager.rollbackTransaction();
                        } else {
                            AndroidResourceManager.logw(LOG_TAG, "runInGuiThread: produced exception", e);
                        }
                    }
                }
            }.execute((Void) null);
        }
	}

	@Override
	public MutableRectangle getInnerDrawingDimensions() {
		// not called anywhere atm
		AndroidResourceManager.loge(LOG_TAG, "getInnerDrawingDimensions: not implemented");
		return null;
	}

	@Override
	public int getScreenWidth() {
		return mScreenWidth;
	}

	@Override
	public int getScreenHeight() {
		return mScreenHeight;
	}

	/**
	 * Called from DragBeatAnimator which means it's only used while editing rhythms.
	 */
	@Override
	public Location2Df getPointerLocation() {
		return mLastKnownPointerLocation;
	}

	/**
	 * Called from baseRhythmDraughter when drawing sub zones, and also to give the pointer
	 * location to the beat action handler.
	 */
	@Override
	public Location2Df getPointerRelativeLocation(MutableRectangle toRectangle) {

		return Location2Df.getTransportLocation(mLastKnownPointerLocation.getX() - toRectangle.getX()
				, mLastKnownPointerLocation.getY() - toRectangle.getY());
	}

    public void setIsPointerDown(boolean val) {
        this.mIsPointerDown = val;
    }

    @Override
    public boolean isPointerDown() {
        return mIsPointerDown;
    }

    /**
	 * Called by KybActivity.onTouchEvent()
	 * @param x axis
	 * @param y axis
	 */
	public void setLastKnownPointerLocation(float x, float y) {
		mLastKnownPointerLocation.setLocation(x, y);
        mIsPointerDown = true;
	}

    /**
     * Called from EditRhythmActivity and EditBeatDialogActivity .onCreate()
     */
    public void setEditingDimensions() {
        DisplayMetrics displayMetrics = mApplication.getResources().getDisplayMetrics();
        mMinSwipeUpY = displayMetrics.density * MIN_SWIPE_UP;
        mMaxSwipeUpY = mMinSwipeUpY * 1.5f;
        mScreenWidth = displayMetrics.widthPixels;
        mScreenHeight = displayMetrics.heightPixels;
    }

	@Override
	public float getMinSwipeUpY() {
		return mMinSwipeUpY;
	}

	@Override
	public float getMaxSwipeUpY() {
        return mMaxSwipeUpY;
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		// not used
	}

	@Override
	public void popClip() {
        // not used
	}

	/**
	 * Android version hasn't revealed anything I'd say needs abort. Changed
	 * the wordings to offer to report bug instead.
	 * Note: only ever called with same coreId = UNEXPECTED_WRITE_FORMAT_ERROR
	 * It would be serious if this happened, but it should never.
	 */
	@Override
	public void abortWithMessage(Key coreId) {
		warnOnErrorMessage(coreId, true, null);
	}

	@Override
	public void warnOnErrorMessage(Key coreId, boolean offerReportBug, Exception e) {
		if (mResourceManager == null || mApplication == null) {
			AndroidResourceManager.loge(LOG_TAG, String.format("warnOnErrorMessage: unable to warn because lost a needed ref (resourceManager=%s, application=%s)", mResourceManager, mApplication));
			return;
		}
		warnOnErrorMessage(mResourceManager.getLocalisedString(coreId)
                , mApplication.getResources().getString(R.string.unexpectedErrorTitle)
                , offerReportBug, e);
	}

	@Override
	public void warnOnErrorMessage(final String message, final String title, final boolean offerReportBug, Exception e) {
		// add a chooser that allows the user to send
        final String logTrace = offerReportBug ? logTraceToReportString(e) : null;

        if (!isUiThread()) {
            if (mResourceManager.isInTransaction()) {
                mResourceManager.rollbackTransaction();
            }

            runInGuiThread(new Runnable() {
                @Override
                public void run() {
                    showAlertMessage(title, message, logTrace, offerReportBug);
                }
            });
        }
        else {
            // is UI thread
            if (mResourceManager.isInTransaction()) {
                runAsyncProtectedTaskThenUiTask(new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        mResourceManager.rollbackTransaction();
                    }
                });
            }

            showAlertMessage(title, message, logTrace, offerReportBug);
        }
	}

    private void showAlertMessage(final String title, final String message, final String logTrace, final boolean offerReport) {
        try {
            final KybActivity activity = mResourceManager.getLatestActivity();
            if (activity == null) {
                throw new Exception("showAlertMessage: activity is null");
            }

            AlertDialog.Builder bldr = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok_button, null)
                    .setIcon(android.R.drawable.ic_dialog_alert);

            if (offerReport) {
                final Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[] { activity.getString(R.string.APP_EMAIL) });
                intent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.bugReportTitle));

                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    bldr.setNeutralButton(R.string.email_bug_report, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final KybActivity a = mResourceManager.getLatestActivity();
                            if (a != null) {
                                runAsyncProtectedTaskThenUiTask(new ParameterizedCallback(true) {
                                    @Override
                                    public Object runForResult() {
                                        return getLogcat();
                                    }
                                }, new ParameterizedCallback(false) {
                                    @Override
                                    public void run() {
                                        intent.putExtra(Intent.EXTRA_TEXT, String.format("%s \n\n\n title = %s \n\n KYB version = %s \n\n message = %s \n\n trace = %s \n\n logs = %s",
                                                activity.getString(R.string.bugReportSubmitDesc), title,
                                                mResourceManager.getVersionInfo(),
                                                message, logTrace, (String)mParam));

                                        a.startActivity(intent);
                                    }
                                });
                            }
                            else {
                                AndroidResourceManager.loge(LOG_TAG, "warnOnErrorMessage: could not show alert because no latest activity available");
                            }

                            dialog.dismiss();
                        }
                    });
                }
                else {
                    AndroidResourceManager.loge(LOG_TAG, "warnOnErrorMessage: could not offer email as package manager is null");
                }
            }

            bldr.create().show();

        } catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "warnOnErrorMessage: exception on showing alert dialog", e);
            AndroidResourceManager.logw(LOG_TAG, "warnOnErrorMessage: message that was not displayed="+message);
        }
    }

    /**
     * In cases where want to display a message before there's an activity, but have the context, or because
     * can bypass and want to send a specific data string
     * @param context in kyb
     * @param title alert title
     * @param message text
     * @param logTrace trace if available
     */
    public void showAlertMessage(KybActivity context, final String title, final String message, final String logTrace) {
        try {
            final KybActivity activity = context == null ? mResourceManager.getLatestActivity() : context;
            if (activity == null) {
                throw new Exception("showAlertMessage: activity is null");
            }

            AlertDialog.Builder bldr = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok_button, null)
                    .setIcon(android.R.drawable.ic_dialog_alert);

            if (logTrace != null) {
                final Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:"));
                intent.putExtra(Intent.EXTRA_EMAIL, new String[] { activity.getString(R.string.APP_EMAIL) });
                intent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.bugReportTitle));
                intent.putExtra(Intent.EXTRA_TEXT, String.format("%s \n\n\n title = %s \n\n KYB version = %s \n\n message = %s \n\n%s",
                        activity.getString(R.string.bugReportSubmitDesc), title,
                        mResourceManager.getVersionInfo(),
                        message, logTrace));

                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    bldr.setNeutralButton(R.string.email_bug_report, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            KybActivity a = mResourceManager.getLatestActivity();
                            if (a != null) {
                                a.startActivity(intent);
                            }
                            else {
                                AndroidResourceManager.loge(LOG_TAG, "warnOnErrorMessage: could not show alert because no latest activity available");
                            }

                            dialog.dismiss();
                        }
                    });
                }
                else {
                    AndroidResourceManager.loge(LOG_TAG, "warnOnErrorMessage: could not offer email as package manager is null");
                }
            }

            bldr.create().show();

        } catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "warnOnErrorMessage: exception on showing alert dialog", e);
            AndroidResourceManager.logw(LOG_TAG, "warnOnErrorMessage: message that was not displayed="+message);
        }
    }

    /**
	 * Best way appears to be just use the default 60fps
	 */
	private void setExpectedFps() {
//	    final WindowManager wm = (WindowManager) mApplication.getSystemService(Context.WINDOW_SERVICE);
//	    final Display display = wm.getDefaultDisplay();
//	    // due to a bug reported here: 
//	    // https://stackoverflow.com/questions/6024483/display-getrefreshrate-giving-me-different-values-in-different-devices
//	    // using this workaround by Lennart Rolland
//	    mFps = display.getRefreshRate();
//	    if (mFps < 10.f) {
//	    	float defFps = 60.f;
//	    	AndroidResourceManager.logw(LOG_TAG, String.format("setExpectedFps: got low value from Display (%s), defaulting to %s", mFps, defFps));
//	    	mFps = defFps;
//	    }

		mFps = DEFAULT_REFRESH_RATE;
	}

	public float getExpectedFps() {
	    return mFps;
	}

	/**
	 * Used to ensure that when something is popped up in front of a rhythm it doesn't
	 * continue to process things like motion events.
	 * KybActivity removes itself from resource manager in onPause(), so if it's there it
	 * isn't blocking. 
	 */
	@Override
	public boolean isBlocking() {
        KybActivity a = mResourceManager.getLatestActivity();
		return a == null || !a.isActive() || a.hasOpenDialog() || a instanceof EditBeatDialogActivity;
	}

	@Override
	public void showNotification(String message) {
		Toast.makeText(mApplication, message, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void releaseResources() {
		// nothing of significance to release
	}

    /**
     * A pair of run-ables that represent a background task that must be followed by a foreground task.
     * @param params generic
     */
    @Override
    public void runAsyncProtectedTaskThenUiTask(ParameterizedCallback... params) {
        new GenericAsyncAndUiTaskPair(this).execute(params);
    }

    private static String logTraceToReportString(Exception e) {
        StringBuilder sb = new StringBuilder("wrapping trace:");

        StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        for (int i = 5; i < ste.length - 5; i++) {
            StackTraceElement e1 = ste[i];
            sb.append(String.format("\n %s.%s", e1.getClassName(), e1.getMethodName()));
        }

        if (e != null) {

            sb.append("\n\nembedded trace: ");
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }

        return sb.toString();
    }

    /**
     * Called off the UI thread from showAlertMessage()
     * @return
     */
    public String getLogcat() {
        try {
            final StringBuilder log = new StringBuilder();
            Process logcat = Runtime.getRuntime().exec("logcat -d -v time");
            BufferedReader br = new BufferedReader(new InputStreamReader(logcat.getInputStream()),4*1024);
            String line;
            String separator = System.getProperty("line.separator");
            while ((line = br.readLine()) != null) {
                log.append(line);
                log.append(separator);
            }

            return log.toString();
        } catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "getLogcat: not able to get logcat output", e);
            return "no logs because of "+e.getClass().getSimpleName();
        }
    }

/*
	public static final String TO_IMPLEMENT = "not implemented -";
    private static final String DONT_EXPECT_THIS_METHOD_TO_BE_CALLED = "call to method not expected to use -";
	public static void logTrace(String msg, String caller) {
		StackTraceElement[] ste = Thread.currentThread().getStackTrace(); 
		for (int i = 3; i < ste.length - 3; i++) {
			StackTraceElement e = ste[i];
			if (i == 3) {
				AndroidResourceManager.loge(LOG_TAG, String.format("%s tracing from (%s) method=%s", msg, caller, e.getMethodName()));
			}
			else {
				AndroidResourceManager.loge(LOG_TAG, String.format("\t el=%s method=%s.%s", i, e.getClassName(), e.getMethodName()));
			}
		}
	}
*/
}
