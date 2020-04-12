package com.stillwindsoftware.keepyabeat.gui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

/**
 * LOV for Beat Types module
 * Can be called after selection of a beat in editor Options -> change type
 * and also when adding a new beat from the button (or menu depending if end up that way)
 * When from menu/button it's instantiated as an embedded fragment rather than a dialog, this is so
 * onTouch events can be passed up to the activity. As a dialog, once the dialog is dismissed, the events
 * cancel and can't do anything with it because the UI seems to wait for the finger to release before
 * starting to register touches again. (ie. finger left down produces nothing)
 */
public class ChooseBeatTypeDialog extends KybDialogFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, View.OnLongClickListener, View.OnTouchListener {

    static final String LOG_TAG = "KYB-"+ChooseBeatTypeDialog.class.getSimpleName();

    private AlertDialog mDialog;
    private ListView mListView;
    private SimpleCursorAdapter mAdapter;
    private GuiManager.BeatTypeSelectedCallback mBeatTypeSelectedCallback;
    private boolean mIsDragBeatType = true; // will be set to false in onCreateDialog if it's being called as dialog
    private int mLastDownEventPointerId;

    public ChooseBeatTypeDialog() {
        // Required empty public constructor
    }

    /**
     * So only maintain a ref to it from the activity when it is opened as a managed fragment
     * @return
     */
    public boolean isDragBeatType() {
        return mIsDragBeatType;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (mIsDragBeatType) {
            View listContainer = inflater.inflate(R.layout.mimic_dialog_list, container, false);
            AndroidResourceManager.logd(LOG_TAG, "onCreateView: returning dialog_simple_list container");
            return listContainer;
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "onCreateView: not drag type, don't do anything");
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mIsDragBeatType) {
            // the view has a set size for the framelayout which contains it, however, real dialogs don't behave that
            // way, the resource settings appear to create a dialog as close as possible to the real dialog dimensions
            Resources resources = getResources();
            DisplayMetrics dm = resources.getDisplayMetrics();
            float desiredWidth = dm.widthPixels > dm.heightPixels
                    ? dm.heightPixels * resources.getFraction(R.fraction.dialog_width_landscape, 1,1)   // use the height as the dialog width if the height is smaller dimension
                    : dm.widthPixels * resources.getFraction(R.fraction.dialog_width_portraint, 1,1);   // otherwise use the fraction of the width (portrait)
            FrameLayout parent = (FrameLayout) view.getParent();
            ViewGroup.LayoutParams params = parent.getLayoutParams();
            params.width = (int) desiredWidth;
            parent.setLayoutParams(params);

            mListView = (ListView) view.findViewById(android.R.id.list);
            AndroidResourceManager.logd(LOG_TAG, "onViewCreated: setting up listView");
            TextView titleVw = (TextView) view.findViewById(android.R.id.title);
            titleVw.setText(R.string.invokeDragBeatType);
            titleVw.setVisibility(View.VISIBLE);
            fillList();
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "onViewCreated: not drag type, don't do anything");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (mActivity instanceof EditRhythmActivity) {
            mBeatTypeSelectedCallback = ((EditRhythmActivity) mActivity).getRhythmPlayingHolder().getBeatTypeSelectedCallback();
        }
        else {
            mBeatTypeSelectedCallback = (GuiManager.BeatTypeSelectedCallback) mActivity; // only EditBeatDialogActivity implements this
        }
        mIsDragBeatType = mBeatTypeSelectedCallback == null;

        // add a new tag, popup a dialog to get a name and confirm click
        final View dlgLayout = mActivity.getLayoutInflater().inflate(R.layout.mimic_dialog_list, null);
        mListView = (ListView) dlgLayout.findViewById(android.R.id.list);

        fillList();

        AlertDialog.Builder bld = new AlertDialog.Builder(mActivity)
                .setAdapter(mAdapter, null) // not listening for events for the dialog (only the views in the dialog_simple_list)
                .setTitle(mIsDragBeatType ? R.string.invokeDragBeatType : R.string.lovChooseBeatType);

        mDialog = bld.create();
        mDialog.setCancelable(true);

        return mDialog;
    }

    private void fillList() {
        String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
        int[] toLayoutViewIds = new int[] { R.id.beat_type_name, R.id.external_key };

        mAdapter = new SimpleCursorAdapter(mActivity, R.layout.beat_type_lov_row, null, dbColumnsForLayout, toLayoutViewIds, 0) {
            /**
             * Binding the relative layout, so can set items that depend on other data in the cursor
             */
            @Override
            public void bindView(View v, Context context, Cursor cursor) {

                super.bindView(v, context, cursor);

                RelativeLayout layout = (RelativeLayout) v;

                // get the views needed for the rest of the layout
                TextView btNameView = (TextView) layout.findViewById(R.id.beat_type_name);
//                ImageButton colourBtn = (ImageButton) layout.findViewById(R.id.colour_btn);

                if (mIsDragBeatType) {
                    layout.setOnLongClickListener(ChooseBeatTypeDialog.this);
                    btNameView.setOnLongClickListener(ChooseBeatTypeDialog.this);
//                    colourBtn.setOnLongClickListener(ChooseBeatTypeDialog.this);
                    layout.setOnTouchListener(ChooseBeatTypeDialog.this);
                    btNameView.setOnTouchListener(ChooseBeatTypeDialog.this);
//                    colourBtn.setOnTouchListener(ChooseBeatTypeDialog.this);
                }
                else {
                    layout.setOnClickListener(ChooseBeatTypeDialog.this);
                    btNameView.setOnClickListener(ChooseBeatTypeDialog.this);
//                    colourBtn.setOnClickListener(ChooseBeatTypeDialog.this);
                }

                String btName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME));
                // get the localised string for the name of internal beat types
                String resName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
                if (resName != null) {
                    int resId = AbstractSqlImpl.getLocalisedResIdFromName(resName, context.getResources());
                    if (resId > 0) {
                        btName = context.getResources().getString(resId);
                    }
                }

                btNameView.setText(btName);

                // colour button background
                int colour = cursor.getInt(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_COLOUR));
                if (colour != 0) {
                    btNameView.setBackgroundColor(colour);
                }
                else {
                    btNameView.setBackgroundResource(0); // causes remove backgrd
                }

                double contrast = ColorUtils.calculateContrast(WHITE, colour);
                int foregrdColour = contrast < RhythmBeatTypesFragment.COLOUR_CONTRAST_RATIO_THRESHOLD ? BLACK : WHITE;
                btNameView.setTextColor(foregrdColour);
            }
        };

        mListView.setAdapter(mAdapter);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onClick(View view) {
        if (mIsDragBeatType) {
            AndroidResourceManager.loge(LOG_TAG, "onClick: choice is for a beat type to drag to the rhythm, should not listen for onClick");
            return;
        }

        RelativeLayout parent = (RelativeLayout) (view instanceof RelativeLayout ? view : view.getParent());
        TextView keyVw = (TextView) parent.findViewById(R.id.external_key);
        final String btKey = keyVw.getText().toString();

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {
                        BeatType beatType = mResourceManager.getLibrary().getBeatTypes().lookup(btKey);
                        AndroidResourceManager.logd(LOG_TAG, "lookup beat type = "+beatType);
                        if (mActivity instanceof EditRhythmActivity) { // not for beat dialog
                            mBeatTypeSelectedCallback.beatTypeSelected(beatType);
                        }
                        return beatType;
                    }
                },
                (mActivity instanceof EditRhythmActivity
                        ? new CauseEditorRedrawAndDismissCallback()
                        : new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        AndroidResourceManager.logd(LOG_TAG, " beat type = "+mParam);
                        mBeatTypeSelectedCallback.beatTypeSelected((BeatType) mParam);
                        dismiss();
                    }
                }));
    }

    /**
     * Only when the dialog is called as a dragBeatType
     * @param view
     */
    @Override
    public boolean onLongClick(View view) {
        RelativeLayout parent = (RelativeLayout) (view instanceof RelativeLayout ? view : view.getParent());
        TextView keyVw = (TextView) parent.findViewById(R.id.external_key);
        final String btKey = keyVw.getText().toString();

        // get a rectangle of the position of the beat type button
        int[] viewLoc = new int[2];
        parent.getLocationOnScreen(viewLoc);
        final MutableRectangle drawnDimension =
                new MutableRectangle(viewLoc[0], viewLoc[1], parent.getWidth(), parent.getHeight());

        // get the centre of the view to use as the click position
        Location2Df clickPos = mResourceManager.getGuiManager().getPointerLocation();

        AndroidResourceManager.logv(LOG_TAG, String.format("onLongClick: chose %s location of layout=%s click pos=%s", btKey, drawnDimension, clickPos));

        ((EditRhythmActivity) mActivity).initiateNewDragBeat(btKey, drawnDimension, mLastDownEventPointerId);

        return true;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        if (mIsDragBeatType) { // set up the coords on the activity and cause a redraw

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {

                boolean isFirstDown = (mLastDownEventPointerId == KybActivity.INVALID_POINTER_ID);
                if (isFirstDown) {
                    mLastDownEventPointerId = motionEvent.getPointerId(0);
                }
                else { // there's already been a touch, so check that lifted before resetting the pointer id
                    int pointerId = motionEvent.getPointerId(0); // first pointer would be the one we recorded before, if it's still there

                    if (pointerId != mLastDownEventPointerId) { // doesn't match, then grab whatever the pointer id is for this down event
                        final int pointerIndex = (motionEvent.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                        mLastDownEventPointerId = motionEvent.getPointerId(pointerIndex);
                    }
                }
            }

            // cancel sends 0,0 for the coords, otherwise send the coords back
            if (motionEvent.getAction() != MotionEvent.ACTION_CANCEL) {
                ((EditRhythmActivity) mActivity).captureTouchCoords(motionEvent);
            }
        }
        // allow continue for events to be processed (ie. long click)
        return false;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (mActivity instanceof EditRhythmActivity)
            ((EditRhythmActivity) mActivity).chooseBeatTypeClosed();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mActivity instanceof EditRhythmActivity)
            ((EditRhythmActivity) mActivity).chooseBeatTypeClosed();
    }

    // ------------------------ LoaderCallbacks

    // creates a new loader after the initLoader() call
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        Uri uri = BeatTypesContentProvider.CONTENT_URI;
        String[] selectArgs = null;
        String selection = null;

        // override loadInBackground() so can lookup the beat types off the UI thread
        CursorLoader cursorLoader = new CursorLoader(mActivity, uri
                , BeatTypesContentProvider.BEAT_TYPE_COLUMNS, selection , selectArgs, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME);

        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

}
