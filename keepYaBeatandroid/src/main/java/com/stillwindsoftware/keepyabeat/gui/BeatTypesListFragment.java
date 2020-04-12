package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

import java.io.File;
import java.util.ArrayList;

import yuku.ambilwarna.AmbilWarnaDialog;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

/**
 * Displays data in a dialog_simple_list fragment (both Standard and Custom)
 */
public class BeatTypesListFragment extends StandardAndCustomListFragment implements View.OnClickListener {

	private static final String LOG_TAG = "KYB-"+BeatTypesListFragment.class.getSimpleName();

    private SoundsContentProvider mSounds;

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        mSounds = (SoundsContentProvider) mResourceManager.getLibrary().getSounds();
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View listContainer = inflater.inflate(R.layout.beat_types_list, container, false);
		return listContainer;
	}

    protected boolean isStandardToggle(CompoundButton toggleBtn) {
		ViewGroup layout = (ViewGroup) toggleBtn.getParent();
		TextView btType = (TextView) layout.findViewById(R.id.beat_type_type);
		boolean isStandard = btType.getText().toString().equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL1);
		return isStandard;
	}
		
	public void fillData() {
		getLoaderManager().initLoader(0, null, this);

		String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_SOURCE_TYPE, KybSQLiteHelper.VIEW_COLUMN_SOUND_TYPE, KybSQLiteHelper.COLUMN_SOUND_URI, KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
		int[] toLayoutViewIds = new int[] { R.id.beat_type_type, R.id.sound_type, R.id.sound_url, R.id.beat_type_key };
		
		mAdapter = new SimpleCursorAdapter(mActivity, R.layout.beat_type_row, null, dbColumnsForLayout, toLayoutViewIds, 0) {

					/**
					 * Binding the relative layout, so can set items that depend on other data in the cursor
					 */
					@Override
					public void bindView(View v, Context context, Cursor cursor) {

						super.bindView(v, context, cursor);
						
						RelativeLayout layout = (RelativeLayout) v;
                        Resources res = context.getResources();

						// get the views needed for the rest of the layout
                        TextView fillerView = (TextView) layout.findViewById(R.id.filler_tv);
						TextView btNameView = (TextView) layout.findViewById(R.id.beat_type_name);
                        TextView soundNameView = (TextView) layout.findViewById(R.id.sound_name); // only used for repairing broken sound
						ImageButton playBtn = (ImageButton) layout.findViewById(R.id.play_beat_type_btn);
						final CompoundButton toggleListBtn = (CompoundButton) layout.findViewById(R.id.toggle_list_btn);
                        final ImageButton addNewBtn = (ImageButton) layout.findViewById(R.id.add_new_btn);
                        final ImageButton editBtn = (ImageButton) layout.findViewById(R.id.edit_btn);
                        final ImageButton deleteBtn = (ImageButton) layout.findViewById(R.id.delete_btn);
                        final View extraBtns = layout.findViewById(R.id.extra_buttons);

                        addNewBtn.setVisibility(View.GONE);

						// reused don't want to fire listeners
						toggleListBtn.setOnCheckedChangeListener(null);
                        addNewBtn.setOnClickListener(null);
                        btNameView.setOnClickListener(BeatTypesListFragment.this);
                        editBtn.setOnClickListener(BeatTypesListFragment.this);
                        deleteBtn.setOnClickListener(BeatTypesListFragment.this);

						// get the beat type's source type, could be actual data rows, or one of the 2 control rows
						// (for handling sub sections of the dialog_simple_list)
						TextView btType = (TextView) layout.findViewById(R.id.beat_type_type);
						boolean isControl = false;
                        String beatTypeType = btType.getText().toString();
                        if (beatTypeType.equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL1)) {
							btNameView.setText(R.string.beat_types_internal_data);
							toggleListBtn.setChecked(mShowStandard);
                            addNewBtn.setVisibility(View.INVISIBLE); // not gone, so keeps the same layout as other control
							isControl = true;
                        }
						else if (beatTypeType.equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL2)) {
							btNameView.setText(R.string.beat_types_user_data);
							toggleListBtn.setChecked(mShowCustom);
							isControl = true;

                            addNewBtn.setVisibility(View.VISIBLE);
                            addNewBtn.setOnClickListener(BeatTypesListFragment.this);
                        }
						
						// a row that's just to control the dialog_simple_list only uses 2 of the views
						// the button for toggling its section's visibility, and the name of the section
						// all else is invisible
						if (isControl) {
                            layout.setBackgroundResource(0); // causes remove backgrd
                            layout.setOnClickListener(null);
                            btNameView.setTextColor(WHITE);

							toggleListBtn.setVisibility(View.VISIBLE);
							toggleListBtn.setOnCheckedChangeListener(BeatTypesListFragment.this);

							playBtn.setVisibility(View.INVISIBLE);
                            fillerView.setVisibility(View.INVISIBLE);
                            extraBtns.setVisibility(View.GONE);
							return;
						}

						// got this far, so it's a normal data row
                        layout.setOnClickListener(BeatTypesListFragment.this);
                        fillerView.setVisibility(View.GONE);
						toggleListBtn.setVisibility(View.GONE);
						playBtn.setVisibility(View.VISIBLE);
                        editBtn.setVisibility(View.VISIBLE);
                        extraBtns.setVisibility(View.VISIBLE);
                        deleteBtn.setVisibility(beatTypeType.equals(KybSQLiteHelper.INTERNAL_TYPE) ? View.GONE : View.VISIBLE);

						String btName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME));
						// get the localised string for the name of internal beat types
						String resName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
                        if (resName != null) {
							int resId = AbstractSqlImpl.getLocalisedResIdFromName(resName, res);
							if (resId > 0) {
								btName = res.getString(resId);
							}
						}
						
						btNameView.setText(btName);

                        // colour button background
                        int colour = cursor.getInt(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_COLOUR));
                        if (colour != 0) {
                            layout.setBackgroundColor(colour);
                        }
                        else {
                            layout.setBackgroundResource(0); // causes remove backgrd
                        }

                        double contrast = ColorUtils.calculateContrast(WHITE, colour);
                        int foregrdColour = contrast < RhythmBeatTypesFragment.COLOUR_CONTRAST_RATIO_THRESHOLD ? BLACK : WHITE;
                        btNameView.setTextColor(foregrdColour);

                        // play button is visible by default, these tests for which might be made invisible

                        // a) chosen sound = silent, don't show play button

                        String soundKey = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_FK));
                        if (Sounds.InternalSound.noSound.name().equals(soundKey)) {
                            playBtn.setVisibility(View.INVISIBLE);
                        }

                        // b) is the silent beat type, don't show sound name either

                        // play button and sound name
                        if (cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY))
                                .equals(BeatTypes.DefaultBeatTypes.silent.name())) {
                        }
                        else {
                            String soundName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_NAME));
                            // get the localised string for the name of internal sounds
                            String sndResName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.VIEW_COLUMN_SOUND_LOCALISED_RES_NAME));
                            if (sndResName != null) {
                                int resId = AbstractSqlImpl.getLocalisedResIdFromName(sndResName, res);
                                if (resId > 0) {
                                    soundName = res.getString(resId);
                                }
                            }
                            soundNameView.setText(soundName);

                            // these are the sounds that might be broken, but only if custom
                            boolean isCustom = !mSounds.isSystemSound(soundKey);
                            TextView urlView = (TextView) layout.findViewById(R.id.sound_url);

                            if (isCustom && !isCustomFileExists(urlView.getText().toString())) {
                                AndroidResourceManager.logd(LOG_TAG, "getView: broken sound, show warning icon "+soundName+" key="+soundKey);
                                playBtn.setImageDrawable(res.getDrawable(R.drawable.ic_warning_white_24dp));
                                playBtn.setTag(soundKey);
                            }
                            else {
                                playBtn.setImageDrawable(res.getDrawable(android.R.drawable.ic_media_play));
                            }

                        }
						
					}

            private boolean isCustomFileExists(String soundUri) {
                File soundFile = mActivity.getFileStreamPath(soundUri);
                boolean e = soundFile.exists();
//                AndroidResourceManager.logw(LOG_TAG, "isCustomFileExists: soundFile "+soundFile.getPath()+" exists="+e);
                return e;
            }

        };
		
		setListAdapter(mAdapter);
		
	}

	// ------------------------ LoaderCallbacks
	
	// creates a new loader after the initLoader() call
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		String[] projection = {
				KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_SOURCE_TYPE, KybSQLiteHelper.VIEW_COLUMN_SOUND_TYPE, KybSQLiteHelper.COLUMN_SOUND_URI
				, KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.VIEW_COLUMN_SOUND_LOCALISED_RES_NAME, KybSQLiteHelper.COLUMN_COLOUR
				, KybSQLiteHelper.COLUMN_BEAT_TYPE_NAME, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME, KybSQLiteHelper.COLUMN_SOUND_FK
                , KybSQLiteHelper.COLUMN_EXTERNAL_KEY };
		
		// at start all rows would be shown so selection null, if the user toggles those have to add selections
		String selection = null;
		if (!mShowStandard || !mShowCustom) {
			if (mShowStandard != mShowCustom) {
				// only one of them
				selection = KybSQLiteHelper.COLUMN_SOURCE_TYPE + " != '" + (mShowStandard ? KybSQLiteHelper.USER_ENTERED_TYPE : KybSQLiteHelper.INTERNAL_TYPE)  + "'";
			}
			else {
				// both closed
				selection = KybSQLiteHelper.COLUMN_SOURCE_TYPE + " != '" + KybSQLiteHelper.USER_ENTERED_TYPE + "' and "
						  + KybSQLiteHelper.COLUMN_SOURCE_TYPE + " != '" + KybSQLiteHelper.INTERNAL_TYPE  + "'";
			}
		}
		
		CursorLoader cursorLoader = new CursorLoader(mActivity, BeatTypesContentProvider.CONTENT_URI_ORDERED_LIST, projection, selection , null, null);
		
		return cursorLoader;
	}

    /**
     * Colour button click
     * @param view
     */
    @Override
    public void onClick(View view) {

        int viewId = view.getId();

        if (viewId == R.id.add_new_btn) {
            addNewBeatType();
        }
        else if (viewId == R.id.edit_btn) {
            editBeatType(view);
        }
        else if (viewId == R.id.delete_btn) {
            deleteBeatType(view);
        }
        else {
            final RelativeLayout rl = (RelativeLayout) (view instanceof RelativeLayout ? view : view.getParent());
            final TextView btType = (TextView) rl.findViewById(R.id.beat_type_type);

            if (!toggledControlView(btType.getText().toString(), rl)) {
                final TextView btKey = (TextView) rl.findViewById(R.id.beat_type_key);
                changeColourClicked(btKey.getText().toString());
            }
        }
    }

    private void deleteBeatType(View view) {
        final RelativeLayout rl = (RelativeLayout) view.getParent().getParent();
        final TextView btKey = (TextView) rl.findViewById(R.id.beat_type_key);
        final TextView btName = (TextView) rl.findViewById(R.id.beat_type_name);

        final String key = btKey.getText().toString();
        final String name = btName.getText().toString();

        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {
                        // bit complicated, check if any rhythms are referencing the beat type before deleting it
                        final Library library = mResourceManager.getLibrary();
                        final RhythmsContentProvider rhythms = (RhythmsContentProvider) library.getRhythms();
                        int refRhythms = rhythms.getReferencedRhythmsCount(key);

                        if (refRhythms == 0) {
                            new BeatTypesAndSoundsCommand.DeleteBeatType(library.getBeatTypes().lookup(key), null, null).execute();
                            return null;
                        } else {
                            return refRhythms;
                        }
                    }
                }, new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        if (mParam != null) {
                            DeleteBeatTypeListDialog.newInstance(key, name).show(mActivity.getFragmentManager(), DeleteBeatTypeListDialog.LOG_TAG);
                        }
                    }
                });
    }

    private void editBeatType(View view) {
        final RelativeLayout rl = (RelativeLayout) view.getParent().getParent();
        final TextView btKey = (TextView) rl.findViewById(R.id.beat_type_key);
        final TextView btName = (TextView) rl.findViewById(R.id.beat_type_name);
        final TextView btType = (TextView) rl.findViewById(R.id.beat_type_type);

        final String key = btKey.getText().toString();
        final String name = btName.getText().toString();
        final String type = btType.getText().toString();

        AddOrEditBeatTypeDialog.newInstance(key, name, false, type.equals(KybSQLiteHelper.INTERNAL_TYPE))
                .show(mActivity.getSupportFragmentManager(), AddOrEditBeatTypeDialog.LOG_TAG);
    }

    private boolean toggledControlView(String btType, RelativeLayout rl) {
        if (btType.equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL1)
            || btType.equals(KybSQLiteHelper.INTERNAL_TYPE_CONTROL2)) {

            final CompoundButton toggleListBtn = (CompoundButton) rl.findViewById(R.id.toggle_list_btn);
            toggleListBtn.setChecked(!toggleListBtn.isChecked());

            return true;
        }

        return false;
    }

    private void addNewBeatType() {
        AddOrEditBeatTypeDialog.newInstance(null, null, true, false).show(mActivity.getSupportFragmentManager(), AddOrEditBeatTypeDialog.LOG_TAG);
    }

    private void changeColourClicked(final String key) {

        // find the corresponding row in the cursor for the original colour
        int oriColour = -1;
        Cursor csr = mAdapter.getCursor();
        if (csr.getCount() != 0) {
            csr.moveToFirst();

            while (!csr.isAfterLast()) {
                if (key.equals(csr.getString(csr.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY)))) {
                    oriColour = csr.getInt(csr.getColumnIndex(KybSQLiteHelper.COLUMN_COLOUR));
                    break;
                }

                csr.moveToNext();
            }
        }

        // see RhythmBeatTypesFragment.class comments for acknowledgements
        // 3rd param = supports alpha (not here)
        AmbilWarnaDialog dialog = new AmbilWarnaDialog(mActivity, oriColour, false, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, final int color) {

                mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {
                        return mResourceManager.getLibrary().getBeatTypes().lookup(key);
                    }
                }, new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        BeatType bt = (BeatType) mParam;

                        if (bt != null) {
                            AndroidColour newColour = new AndroidColour(color);
                            AndroidResourceManager.logd(LOG_TAG, "chose colour (" + newColour.getHexString() + ") ");
                            new BeatTypesAndSoundsCommand.ChangeBeatColour(bt, newColour, false).execute();
                        }
                        else {
                            AndroidResourceManager.logw(LOG_TAG, "could not update colour for beat type(" + key + ") as no rbt found, this shouldn't ever happen");
                        }
                    }
                });

            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {}
        });

        dialog.show();
    }
}
