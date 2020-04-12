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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.platform.AndroidFont;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.BeatBasicActionHandler;
import com.stillwindsoftware.keepyabeat.player.BeatEditActionHandler;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.DrawNumbersPlacement;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.RhythmAlignment;
import com.stillwindsoftware.keepyabeat.player.EditRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerService;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import java.util.Map;
import java.util.TreeMap;


/**
 * Holds non ui data for drawing/playing fragments, acts as proxy between
 * Android UI elements and Kyb draughting framework so that the UI elements can 
 * reference in without carrying/creating Kyb objects themselves.
 * Used both by PlayRhythmsActivity and EditRhythmActivity, the method of creation
 * determines which it is, some methods are only for PlayRhythmsActivity.
 */
public class RhythmPlayingFragmentHolder extends Fragment {
	
	public static final String LOG_TAG = "KYB-"+RhythmPlayingFragmentHolder.class.getSimpleName();

    private AndroidResourceManager mResourceManager;
	private KybActivity mActivity;

    // data for selections in edit rhythm (selection on a beat)
    private BeatEditActionHandler mBeatActionHandler;
    private String[] mBeatMenuOptionsTitles;
    private Runnable[] mBeatMenuOptionsRunnables;
    private GuiManager.BeatTypeSelectedCallback mBeatTypeSelectedCallback;
    private Object[] mChangeFullBeatValueParams;

    private boolean mBeatTypeChooserWasOpenOnClose = false;

    public RhythmPlayingFragmentHolder() {}

    public static RhythmPlayingFragmentHolder getInstance() {
        return new RhythmPlayingFragmentHolder();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
	}

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mActivity = ((KybActivity) activity);
        mResourceManager = mActivity.mResourceManager;
    }

    /**
     * Each of the following methods places data in this class for passing to a dialog, to make sure as
     * little as possible is kept beyond that dialog's closing, this method clears it all out.
     * Called at the start of each of the method calls. Note: can't call this onDismiss() of a dialog
     * because that would remove the data on a config change.
     */
    private void clearParamsFromCoreActions() {
        mBeatMenuOptionsTitles = null;
        mBeatMenuOptionsRunnables = null;
        mBeatActionHandler = null;
        mBeatTypeSelectedCallback = null;
        mChangeFullBeatValueParams = null;
    }

    /**
     * Keep the data for selection in case of config changes
     * Called from EditRhythmActivity.showSelectedBeatPopupMenu()
     * @param beatActionHandler
     * @param menuOptionsMap
     */
    public void putBeatPopupMenuOptions(BeatEditActionHandler beatActionHandler, TreeMap<String, Runnable> menuOptionsMap) {
        clearParamsFromCoreActions();
        mBeatActionHandler = beatActionHandler;

        mBeatMenuOptionsTitles = new String[menuOptionsMap.size()];
        mBeatMenuOptionsRunnables = new Runnable[menuOptionsMap.size()];
        int i = 0;

        Map.Entry<String, Runnable> menuOption;
        while ((menuOption = menuOptionsMap.pollFirstEntry()) != null) {
            // substring to remove the single digit that guarantees ordering
            mBeatMenuOptionsTitles[i] = menuOption.getKey().substring(1);
            mBeatMenuOptionsRunnables[i++] = menuOption.getValue();
        }
    }

    /**
     * The options that have been held for the beat editing are returned in EditBeatOptionsListDialog.fillData()
     * @return
     */
    public Object[] getBeatPopupMenuOptions() {
        return new Object[] { mBeatActionHandler, mBeatMenuOptionsTitles, mBeatMenuOptionsRunnables };
    }

    /**
     * Keep the callback for selection in case of config changes
     * Called from EditRhythmActivity.showBeatTypePopupLov()
     * @param callback
     */
    public void putBeatTypeSelectedCallback(GuiManager.BeatTypeSelectedCallback callback) {
        clearParamsFromCoreActions();
        mBeatTypeSelectedCallback = callback;
    }

    /**
     * The callback that has been held for the beat editing are returned in ChooseBeatTypeDialog.fillList()
     * @return
     */
    public GuiManager.BeatTypeSelectedCallback getBeatTypeSelectedCallback() {
        return mBeatTypeSelectedCallback;
    }

    public void putChangeFullBeatValueParams(Object[] objects) {
        clearParamsFromCoreActions();
        mChangeFullBeatValueParams = objects;
    }

    public Object[] getChangeFullBeatValueParams() {
        return mChangeFullBeatValueParams;
    }

    /**
     * Called from editRhythmActivity.onSavedInstanceState() to indicate to save find it on config change.
     * It's no good using the bundle to save it as the onCreate() of the activity is called after this fragment
     * is re-initialized.
     */
    public void putBeatTypeChooserWasOpenOnClose() {
        mBeatTypeChooserWasOpenOnClose = true;
    }

    public boolean takeBeatTypeChooserWasOpenOnClose() {
        boolean val = mBeatTypeChooserWasOpenOnClose;
        mBeatTypeChooserWasOpenOnClose = false;
        return val;
    }

    public boolean isBeatTypeChooserWasOpenOnClose() {
        return mBeatTypeChooserWasOpenOnClose;
    }

}
