package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.EditRhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.db.SoundSqlImpl;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.Beat;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidBeatShapedImage;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.FullBeatOverlayImage;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DrawnBeat;
import com.stillwindsoftware.keepyabeat.player.EditedBeat;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.BT_TYPE;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.RBT_TYPE;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.SOUNDS_LIST_HEADER;
import static com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand.RhythmEditBeatPartsCommand.VOLUMES_LIST_HEADER;

public class CopyBeatDialogActivity extends KybActivity
        implements View.OnClickListener {

    static final String LOG_TAG = "KYB-"+CopyBeatDialogActivity.class.getSimpleName();

    public static final String EDIT_BEAT_SELECTED_BEAT_POSITION = "selected beat";
    static final int CREATED_BUTTONS_BASE_ID = 40000;
    private static final String STATE_SELECTED_POSITIONS = "STATE_SELECTED_POSITIONS";

    private int mSelectedBeatPosition;
    private EditRhythmSqlImpl mEditRhythm;
    private EditedBeat.EditedFullBeat mSelectedBeat = null;

    /**
     * Use this factory method to create a new instance of the intent to launch this activity
     *
     * @param context
     * @param position
     * @return
     */
    public static Intent newInstance(Activity context, int position) {
        Intent intent = new Intent(context, CopyBeatDialogActivity.class);
        intent.putExtra(EDIT_BEAT_SELECTED_BEAT_POSITION, position);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.copy_beat_dialog_activity);

        setResult(RESULT_CANCELED); // defaults to cancelled so nothing happens unless delete or ok is pressed

        Intent callingIntent = getIntent();
        mSelectedBeatPosition = callingIntent.getIntExtra(EDIT_BEAT_SELECTED_BEAT_POSITION, -1);
        if (mSelectedBeatPosition == -1) {
            AndroidResourceManager.loge(LOG_TAG, "onCreate: no selected beat number available, abort dialog activity");
            finish();
        }

        // NOTE this try/catch block is almost exactly the same as EditBeatDialogActivity has
        final RhythmsContentProvider rhythms = (RhythmsContentProvider) mResourceManager.getLibrary().getRhythms();
        try {

            while (!rhythms.getReadLockOnPlayerEditorRhythmOrWaitMinimumTime()) {
                AndroidResourceManager.logd(LOG_TAG, "onCreate: waiting for lock on player rhythm");
            }

            mEditRhythm = rhythms.getOpenEditRhythm();
            if (mEditRhythm == null) {
                throw new NullPointerException("onCreate: no edit rhythm available, abort dialog activity");
            }

            if (!mEditRhythm.hasBeatDataCache()) {
                throw new NullPointerException("onCreate: edit rhythm has no cache, abort dialog activity");
            }

            mSelectedBeat = (EditedBeat.EditedFullBeat) getSelectedBeatFromPosition(mEditRhythm, mSelectedBeatPosition);
            if (mSelectedBeat == null) {
                throw new NullPointerException("onCreate: no selected beat found, abort dialog activity");
            }

            if (!mEditRhythm.hasBeatDataCache()) {
                throw new NullPointerException("onCreate: editRhythm does not have beat data cache, abort dialog activity");
            }

        } catch (NullPointerException e) {
            AndroidResourceManager.loge(LOG_TAG, e.getMessage(), e);
            setResult(EditRhythmActivity.UNEXPECTED_MISSING_DATA_ERROR);
            finish();
        } catch (RhythmsContentProvider.RhythmReadLockNotHeldException e) {
            AndroidResourceManager.loge(LOG_TAG, "onCreate: program error", e);
        } finally {
            rhythms.releaseReadLockOnPlayerEditorRhythm();
        }

        String displayFullBeatNum = mSelectedBeat.getDisplayFullBeatNum();
        String beatLabel = displayFullBeatNum == null ? Integer.toString(mSelectedBeat.getFullBeatNum()) : displayFullBeatNum;
        ((TextView)findViewById(R.id.beatTitle)).setText(getString(R.string.copy_beat_dialog_title, beatLabel));

        findViewById(R.id.ok_btn).setOnClickListener(this);
        findViewById(R.id.cancel_btn).setOnClickListener(this);

        ArrayList<Integer> selectedPositions = savedInstanceState != null ? savedInstanceState.getIntegerArrayList(STATE_SELECTED_POSITIONS) : null;
        int rhythmSpace = initDrawRhythm(mSelectedBeat, selectedPositions);
        sizeWindow(rhythmSpace);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        final ArrayList<Integer> selectedPositions = getSelectedPositions();

        if (!selectedPositions.isEmpty()) {
            outState.putIntegerArrayList(STATE_SELECTED_POSITIONS, selectedPositions);
        }
    }

    private int initDrawRhythm(EditedBeat.EditedFullBeat selectedBeat, ArrayList<Integer> selectedPositions) {

        BeatTree beatTree = mEditRhythm.getBeatTree(BeatTree.EDIT);
        BaseRhythmDraughter draughter = mEditRhythm.getRhythmDraughter();

        float maxBeatWidth = draughter.getWidestBeatDrawnWidth();
        float maxWidthAvailable = getMaxAvailableWidth(); // note: side-effects if move this call as it causes gui manager to record the size first

        int numFullBeats = beatTree.size();

        // calc the max space for the rhythm, if it's more than the window has available, then all the beats'
        // widths have to be scaled down to that

        float maxWidthRhythmCouldNeed = maxBeatWidth * numFullBeats;
        float widthDelta = maxWidthRhythmCouldNeed <= maxWidthAvailable             // if the max might need is less than available
                ? 1.f                                                               // just use the width given, else
                : 1.f / (maxWidthRhythmCouldNeed / maxWidthAvailable);              // 1.0 / (wanted / available) (eg. wanted=1500, available=2000, delta=1.3, so 1 / 1.3 = .75)

        int actualMaxRhythmWidth = (int) (maxWidthRhythmCouldNeed * widthDelta);    // this value is returned for the sizing of the window

//        AndroidResourceManager.logd(LOG_TAG, String.format("initDrawRhythm: maxWidthAvailable=%.2f, screenWidth=%s, maxWidthRhythmCouldNeed=%.2f, widthDelta=%.2f, actualMaxRhythmWidth=%s",
//                maxWidthAvailable, mGuiManager.getScreenWidth(), maxWidthRhythmCouldNeed, widthDelta, actualMaxRhythmWidth));

        AndroidBeatShapedImage normalOverlay = new AndroidBeatShapedImage(mResourceManager)
                .setColour(getResources().getColor(R.color.black_transparency));

        int overlayBeatWidth = (int) draughter.getSingleBeatWidth(); // for initing the overlay image, not the buttons
        RhythmDraughtImageStore imageStore = mResourceManager.getRhythmDraughtImageStore();
        PlatformImage fullBeatImg = imageStore.getBestFitImage(overlayBeatWidth, RhythmDraughtImageStore.DraughtImageType.FULL_BEAT);
        normalOverlay.initSize(fullBeatImg);

        LinearLayout layout = findViewById(R.id.buttonLayout);

        int selectedBeatWidth = (int) (((EditedBeat.EditedFullBeat)beatTree.getFullBeatAt(mSelectedBeatPosition)).getAnchor()[DrawnBeat.W_DIMEN] * widthDelta);

        for (int i = 0; i < numFullBeats; i++) {
            EditedBeat.EditedFullBeat beat = (EditedBeat.EditedFullBeat) beatTree.getFullBeatAt(i);

            CopyBeatButton beatButton = new CopyBeatButton(this);
            int ownWidth = (int) (beat.getAnchor()[DrawnBeat.W_DIMEN] * widthDelta);
            boolean isSelected = isButtonSelected(i, selectedPositions);

            beatButton.setLayoutParams(new LinearLayout.LayoutParams(isSelected ? selectedBeatWidth : ownWidth, LinearLayout.LayoutParams.MATCH_PARENT));
            beatButton.setId(CREATED_BUTTONS_BASE_ID + i);
            layout.addView(beatButton);

            String displayFullBeatNum = beat.getDisplayFullBeatNum();
            beatButton.init(i, ownWidth, mSelectedBeatPosition, selectedBeatWidth,
                    displayFullBeatNum != null ? displayFullBeatNum : Integer.toString(beat.getFullBeatNum()),
                    normalOverlay, draughter);

            if (beat.getPosition() != mSelectedBeatPosition) {
                beatButton.setOnClickListener(this);
                beatButton.setSelected(isSelected);
            }
            else {
                beatButton.setSelected(true);
            }
        }

        return actualMaxRhythmWidth;
    }

    private boolean isButtonSelected(int position, ArrayList<Integer> selectedPositions) {
        if (selectedPositions == null || selectedPositions.isEmpty()) {
            return false;
        }

        for (int i = 0; i < selectedPositions.size(); i++) {
            if (position == selectedPositions.get(i)) {
                return true;
            }
        }

        return false;
    }

    private float getMaxAvailableWidth() {
        mGuiManager.setEditingDimensions();
        return mGuiManager.getScreenWidth() * .85f; // saw somewhere that a dialog's max width would be .9 of the window, so give a little bit more leeway
    }

    /**
     * Unless small screen (when always max) height depends on the beat buttons, width on portrait/landscape v number of beats
     */
    private void sizeWindow(int rhythmWidth) {

        // this is how the holder works out the heights for the play rhythm view, subtract the same margins
        int screenWidth = mGuiManager.getScreenWidth();
//        AndroidResourceManager.logd(LOG_TAG, String.format("sizeWindow: width for rhythm=%s, screen=%s", rhythmWidth, screenWidth));
        int screenHeight = mGuiManager.getScreenHeight();
        boolean isPortrait = screenHeight > screenWidth;
        Resources res = getResources();

        // fill the screen height with the dialog and make a decent width
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());

        // width calcs

        if (res.getBoolean(R.bool.very_small_width)) {
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        }
        else {
            lp.width = (int) Math.max(
                    screenWidth * (isPortrait ? .75f : .6f),                    // if the rhythm is small enough, just make it a nice dialog width
                    rhythmWidth * 1.1f);                                        // otherwise fit the rhythm and hopefully have some spare 'cos the dialog can only be about 90% of the screen
        }

        // height calcs

        if (res.getBoolean(R.bool.very_small_width)) {                          // very small
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        }
        else if (isPortrait) {
            lp.height = (int) (screenHeight * .75f);                            // portrait would be too high, make it a proportion of the screen
        }
        else {                                                                  // landscape match it to the rhythm's drawing
            int bottomMargin = PlayRhythmView.getDrawnRhythmBottomMargin(res, true); // can't be portrait
            int topMargin = PlayRhythmView.getDrawnRhythmTopMargin(res, this, true);
            lp.height = screenHeight - (topMargin + bottomMargin);
        }

        getWindow().setAttributes(lp);
    }

    private DrawnBeat.DrawnFullBeat getSelectedBeatFromPosition(EditRhythmSqlImpl editRhythm, int position) {
        BeatTree beatTree = editRhythm.getBeatTree(BeatTree.EDIT);
        return (DrawnBeat.DrawnFullBeat) beatTree.getFullBeatAt(position);
    }

    /**
     */
    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onClick(View view) {

        int viewId = view.getId();

        if (viewId >= CREATED_BUTTONS_BASE_ID && viewId < CREATED_BUTTONS_BASE_ID + Rhythms.MAX_RHYTHM_LEN) {
            view.setSelected(!view.isSelected());
            CopyBeatButton button = (CopyBeatButton) view;

            // the only way to get the linear layout to adjust to the new button size seems to be to recreate all the buttons

            if (button.hasWidthChanged()) {
                AndroidResourceManager.logd(LOG_TAG, String.format("saveChanges: width for beat changed, replacing all buttons"));
                ArrayList<Integer> selectedPositions = getSelectedPositions();
                LinearLayout layout = findViewById(R.id.buttonLayout);
                layout.removeAllViews();
                initDrawRhythm(mSelectedBeat, selectedPositions);
            }
        }
        else if (viewId == R.id.ok_btn) {
            saveChanges();
        }
        else if (viewId == R.id.cancel_btn) {
            finish();
        }
    }

    private void saveChanges() {
        // make the list of selected buttons, if none, just finish... same as cancel
        ArrayList<Integer> selectedPositions = getSelectedPositions();
        if (selectedPositions.isEmpty()) {
            AndroidResourceManager.logd(LOG_TAG, "saveChanges: aborting since no beats selected for copy");
        }
        else {
            mEditRhythm.putCommand(new RhythmEditCommand.CopyFullBeat(mEditRhythm, mSelectedBeatPosition, selectedPositions));
            setResult(RESULT_OK);
        }
        finish();
    }

    @NonNull
    private ArrayList<Integer> getSelectedPositions() {
        final ArrayList<Integer> selectedPositions = new ArrayList<>();

        LinearLayout layout = findViewById(R.id.buttonLayout);
        for (int i = 0; i < layout.getChildCount(); i++) {
            CopyBeatButton beatButton = (CopyBeatButton) layout.getChildAt(i);
            if (beatButton.isSelected() && i != mSelectedBeatPosition) {
                selectedPositions.add(i);
            }
        }
        return selectedPositions;
    }

    public boolean isAnEditorActivity() {
        return true;
    }

}
