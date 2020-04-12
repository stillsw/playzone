package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;

/**
 * Displays data in a dialog_simple_list fragment (both Standard and Custom)
 */
public abstract class StandardAndCustomListFragment extends ListFragment 
					implements LoaderManager.LoaderCallbacks<Cursor>, OnCheckedChangeListener {

	private static final String LOG_TAG = "KYB-"+StandardAndCustomListFragment.class.getSimpleName();
	
	// prefixes for the keys to preferences
	private static final String SHOW_STANDARD_PREF = "show-standard-";
	private static final String SHOW_CUSTOM_PREF = "show-custom-";
	
	protected KybActivity mActivity;
    protected AndroidResourceManager mResourceManager;
	protected SettingsManager mSettingsManager;
	protected SimpleCursorAdapter mAdapter;
	
	// sets visibility of the lists
	protected boolean mShowStandard = true;
	protected boolean mShowCustom = true;
	// keys for shared prefs
	private String mShowStandardKey;
	private String mShowCustomKey;
	
	// flag set after first cursor load so onResume() can reload as needed
	private boolean mHasLoaded = false;

    protected String getShowStandardKey() {
		if (mShowStandardKey == null) {
			mShowStandardKey = SHOW_STANDARD_PREF+getClass().getSimpleName();
		}
		return mShowStandardKey;
	}

	protected String getShowCustomKey() {
		if (mShowCustomKey == null) {
			mShowCustomKey = SHOW_CUSTOM_PREF+getClass().getSimpleName();
		}
		return mShowCustomKey;
	}

	// toggle navigation listening
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		toggleNavigation(buttonView, isChecked);
	}

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);

		// store for during load/reload
		mActivity = (KybActivity)activity;
		mResourceManager = mActivity.mResourceManager;
		mSettingsManager = (SettingsManager) mResourceManager.getPersistentStatesManager();

		mShowStandard = mSettingsManager.readStateFromStore(getShowStandardKey(), true);
		mShowCustom = mSettingsManager.readStateFromStore(getShowCustomKey(), true);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	    getListView().setDividerHeight(2);
	    fillData();
	}

	/**
	 * Called from the activity when the onclick is received on the navigation control button
	 * @param isChecked 
	 * @param toggleBtn
	 */
	public void toggleNavigation(CompoundButton toggleBtn, boolean isChecked) {
		// get the type of the data by finding the child in the parent for the beat type type
		boolean isStandard = isStandardToggle(toggleBtn);
		
		// set variable according to which was toggled and restart the loader (see onCreateLoader() below)
		// with an amended selection clause
		
		if (isStandard) {
			if (mShowStandard != isChecked) {
				mShowStandard = isChecked;
				mSettingsManager.writeStateToStore(getShowStandardKey(), mShowStandard);
				getLoaderManager().restartLoader(0, null, this);
			}
		}
		else {
			if (mShowCustom != isChecked) {
				mShowCustom = isChecked;
				mSettingsManager.writeStateToStore(getShowCustomKey(), mShowCustom);
				getLoaderManager().restartLoader(0, null, this);
			}
		}
	}

	/**
	 * Reloads the cursor in case visibility over the lists was changed by toggle
	 * navigation (eg. in other config layout)
	 */
	@Override
	public void onResume() {
		super.onResume();
		
		// reload only if there has previously been a load (ie. first time round don't)
		if (mHasLoaded) {
			getLoaderManager().restartLoader(0, null, this);
		}
	}
	
	protected abstract boolean isStandardToggle(CompoundButton toggleBtn);
	
	public abstract void fillData();
	
	// ------------------------ LoaderCallbacks
	
	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mHasLoaded = true;
		mAdapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// we lost the data
		mAdapter.swapCursor(null);
	}

}
