package com.stillwindsoftware.keepyabeat.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.BeatTypesContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmsContentProvider;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

public class SoundsListFragment extends StandardAndCustomListFragment implements View.OnClickListener {

	private static final String LOG_TAG = "KYB-"+SoundsListFragment.class.getSimpleName();
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.sounds_list, container, false);
	}

    protected boolean isStandardToggle(CompoundButton toggleBtn) {
		ViewGroup layout = (ViewGroup) toggleBtn.getParent();
		TextView sType = (TextView) layout.findViewById(R.id.sound_type);
		boolean isStandard = sType.getText().toString().equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL1);
		return isStandard;
	}
	
	public void fillData() {
		getLoaderManager().initLoader(0, null, this);
		
		String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_SOURCE_TYPE, KybSQLiteHelper.COLUMN_SOUND_URI, KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
		int[] toLayoutViewIds = new int[] { R.id.sound_type, R.id.sound_url, R.id.external_key };
		
		mAdapter = new SimpleCursorAdapter(mActivity, R.layout.sound_row, null, dbColumnsForLayout, toLayoutViewIds, 0) {

					/**
					 * Binding the relative layout, so can set items that depend on other data in the cursor
					 */
					@Override
					public void bindView(View v, Context context, Cursor cursor) {

						super.bindView(v, context, cursor);
						
						RelativeLayout layout = (RelativeLayout) v;
                        Resources res = context.getResources();

                        String cursorSoundName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_NAME));
                        String soundName = cursorSoundName;
						// get the localised string for the name of internal sounds
						String sndResName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
						if (sndResName != null) {
                            int resId = AbstractSqlImpl.getLocalisedResIdFromName(sndResName, res);
							if (resId > 0) {
								soundName = res.getString(resId);
							}
						}

                        TextView soundNameView = (TextView) layout.findViewById(R.id.sound_name);
                        ImageButton playBtn = (ImageButton) layout.findViewById(R.id.play_sound_btn);
						final CompoundButton toggleListBtn = (CompoundButton) layout.findViewById(R.id.toggle_list_btn);
                        final ImageButton addNewBtn = (ImageButton) layout.findViewById(R.id.add_new_btn);
                        final ImageButton editBtn = (ImageButton) layout.findViewById(R.id.edit_btn);
                        final ImageButton deleteBtn = (ImageButton) layout.findViewById(R.id.delete_btn);
                        final View extraBtns = layout.findViewById(R.id.extra_buttons);

                        addNewBtn.setVisibility(View.GONE);

						// reused don't want to fire listeners
						toggleListBtn.setOnCheckedChangeListener(null);
                        addNewBtn.setOnClickListener(null);
                        editBtn.setOnClickListener(SoundsListFragment.this);
                        deleteBtn.setOnClickListener(SoundsListFragment.this);

						// set it to the view if found, otherwise just use the name from the db
						if (soundNameView != null) {
							soundNameView.setText(soundName);
						}

						// get the beat type's source type, could be actual data rows, or one of the 2 control rows
						// (for handling sub sections of the dialog_simple_list)
						TextView sType = (TextView) layout.findViewById(R.id.sound_type);
						boolean isControl = false;
						if (sType.getText().toString().equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL1)) {
							soundNameView.setText(R.string.sounds_internal_data);
							toggleListBtn.setChecked(mShowStandard);
                            addNewBtn.setVisibility(View.INVISIBLE); // not gone, so keeps the same layout as other control
							isControl = true;
						}
						else if (sType.getText().toString().equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL2)) {
							soundNameView.setText(R.string.sounds_user_data);
							toggleListBtn.setChecked(mShowCustom);
							isControl = true;

                            addNewBtn.setVisibility(View.VISIBLE);
                            addNewBtn.setOnClickListener(SoundsListFragment.this);
						}

						// a row that's just to control the dialog_simple_list only uses 2 of the views
						// the button for toggling its section's visibility, and the name of the section
						// all else is invisible
						if (isControl) {
							toggleListBtn.setVisibility(View.VISIBLE);
							toggleListBtn.setOnCheckedChangeListener(SoundsListFragment.this);
							playBtn.setVisibility(View.INVISIBLE);
                            extraBtns.setVisibility(View.GONE);
							return;
						}


						// got this far, so it's a normal data row
						toggleListBtn.setVisibility(View.GONE);
                        editBtn.setVisibility(View.VISIBLE);
                        String soundType = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_SOURCE_TYPE));
                        boolean isInternal = soundType.equals(KybSQLiteHelper.INTERNAL_TYPE);
                        extraBtns.setVisibility(isInternal ? View.INVISIBLE : View.VISIBLE);

                        // these are the sounds that might be broken
                        TextView urlView = (TextView) layout.findViewById(R.id.sound_url);

                        if (!isInternal && !mActivity.getFileStreamPath(urlView.getText().toString()).exists()) {
                            AndroidResourceManager.logv(LOG_TAG, "getView: broken sound, show warning icon "+soundName);
                            playBtn.setImageDrawable(res.getDrawable(R.drawable.ic_warning_white_24dp));
                            playBtn.setVisibility(View.VISIBLE);
                            playBtn.setTag(cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)));
                        }
                        else if (playBtn != null) {
                            // no sound internal doesn't get a play button
                            boolean isHideBtn = KybSQLiteHelper.NO_SOUND.equals(cursorSoundName);

                            playBtn.setImageDrawable(res.getDrawable(android.R.drawable.ic_media_play));
                            playBtn.setVisibility(isHideBtn ? View.INVISIBLE : View.VISIBLE);
						}
					}

			};
		
		setListAdapter(mAdapter);
	}

	// ------------------------ LoaderCallbacks
	
	// creates a new loader after the initLoader() call
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		String[] projection = { 
				KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_SOURCE_TYPE, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
				, KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.COLUMN_SOUND_URI
				, KybSQLiteHelper.COLUMN_SOUND_DURATION, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME };

		// at start all rows would be shown so selection null, if the user toggles those have to add selections
		String selection = null;
		if (!mShowStandard || !mShowCustom) {
			if (mShowStandard != mShowCustom) {
				// only one of them
				selection = KybSQLiteHelper.COLUMN_SOURCE_TYPE + " != '" + (mShowStandard ? KybSQLiteHelper.URI_ENTERED_TYPE : KybSQLiteHelper.INTERNAL_TYPE)  + "'";
			}
			else {
				// both closed
				selection = KybSQLiteHelper.COLUMN_SOURCE_TYPE + " != '" + KybSQLiteHelper.URI_ENTERED_TYPE + "' and "
						  + KybSQLiteHelper.COLUMN_SOURCE_TYPE + " != '" + KybSQLiteHelper.INTERNAL_TYPE  + "'";
			}
		}

		
		CursorLoader cursorLoader = new CursorLoader(mActivity, SoundsContentProvider.CONTENT_URI_ORDERED_LIST, projection, selection , null, null);
		
		return cursorLoader;
	}

    @Override
    public void onClick(View view) {

        ViewParent viewParent = view.getParent();
        final RelativeLayout parent = viewParent instanceof RelativeLayout
                ? (RelativeLayout) viewParent                       // in sounds list play button is a child in the row level layout
                : (RelativeLayout) viewParent.getParent();          // in beat types list it's a child of the linear layout for the buttons
        final TextView keyView = (TextView) parent.findViewById(R.id.external_key);
        final TextView soundNameView = (TextView) parent.findViewById(R.id.sound_name);
        final String key = keyView.getText().toString();
        final String name = soundNameView.getText().toString();

        int viewId = view.getId();
        if (viewId == R.id.add_new_btn) {
            AddOrRepairSoundDialog.newInstance().show(mActivity.getSupportFragmentManager(), AddOrRepairSoundDialog.LOG_TAG);
        }
        else if (viewId == R.id.edit_btn) {
            RenameSoundDialog.newInstance(key, name)
                    .show(mActivity.getFragmentManager(), RenameSoundDialog.LOG_TAG);
        }
        else if (viewId == R.id.delete_btn) {
            deleteSound(key, name);
        }

    }

    private void deleteSound(final String key, final String name) {
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {
                        // bit complicated, check if any rhythms and/or beat types are referencing the sound before deleting it
                        final Library library = mResourceManager.getLibrary();
                        final RhythmsContentProvider rhythms = (RhythmsContentProvider) library.getRhythms();
                        final BeatTypesContentProvider beatTypes = (BeatTypesContentProvider) library.getBeatTypes();
                        int refRhythms = rhythms.getReferencedRhythmsCount(key);
                        int refBeatTypes = beatTypes.getReferencedBeatTypesCount(key);

                        if (refRhythms == 0 && refBeatTypes == 0) {
                            new BeatTypesAndSoundsCommand.DeleteSound(library.getSounds().lookup(key), null, null, null).execute();
                            return null;
                        }
                        else {
                            return new int[] { refRhythms, refBeatTypes };
                        }
                    }
                }, new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        if (mParam != null) {
                            DeleteSoundListDialog.newInstance(key, name, (int[])mParam).show(mActivity.getFragmentManager(), DeleteSoundListDialog.LOG_TAG);
                        }
                        else { // nothing referencing it, but still need to warn about file deletion and undo
                            AlertDialog dlg = new AlertDialog.Builder(mActivity).setMessage(R.string.customSoundDeleteConfirm).create();
                            dlg.show();
                        }
                    }
                });
    }
}
