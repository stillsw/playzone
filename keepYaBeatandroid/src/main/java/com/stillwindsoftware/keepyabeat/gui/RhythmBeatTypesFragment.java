package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.AbstractSqlImpl;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.SoundSqlImpl;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.model.BeatTree;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmBeatTypeCommand;
import com.stillwindsoftware.keepyabeat.platform.AndroidColour;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.MultiEditModel;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

import yuku.ambilwarna.AmbilWarnaDialog;

import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;

/**
 * Rhythm beat types fragment
 *
 * Thanks to https://github.com/yukuku/ambilwarna for the colour dialog
 */
public class RhythmBeatTypesFragment extends SwipeableListFragment {

	private static final String LOG_TAG = "KYB-"+RhythmBeatTypesFragment.class.getSimpleName();
    static final double COLOUR_CONTRAST_RATIO_THRESHOLD = 2.0; // used to compare the colour of the beat type to the beat type text that is on top of it

    protected RhythmBeatTypesAdapter mAdapter;
	private SimpleCursorAdapter mSoundsSpinnerAdapter;
	
    private volatile long mRbtDbRefreshedNano = -1L; // keep track of the data updates, so don't refresh the list more than needed
    private boolean mRbtRefreshIsPending = false; // set in notifyRhythmChanged when Non-UI thread notices needs to update in the UI thread
    private PlayRhythmFragment mPlayRhythmFragment;

    /**
	 * Set the rhythm data holder to have access to the draughter for the model
     * @param rhythmPlayingHolder
     */
	void setPlayRhythmFragment(PlayRhythmFragment rhythmPlayingHolder) {
		this.mPlayRhythmFragment = rhythmPlayingHolder;
		fillData(); // ignored unless both the dialog_simple_list view and the rhythm data holder are present
	}
	
    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup listContainer = (ViewGroup) inflater.inflate(R.layout.rhythm_beat_types_list, container, false);
        assignSlideControlViews(listContainer);
		return listContainer;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	}

    @Override
    protected boolean hasListAdapter() {
        return mAdapter != null;
    }

    // --------------- OnTouchListener method

	/**
	 * Communicate upwards to the activity providing not scrolling
	 */
	@Override
	public boolean onTouch(View v, MotionEvent event) {

        if (!super.onTouch(v, event)) {

            // notify the activity for movements
            //TODO test this is necessary
            mActivity.recordMotionEvent(event);
        }

        return false; // don't prevent propagation of event
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

    protected void refreshAllRbtData() {
        // the rhythmBeatTypes may have changed (eg. returning from soundsList and have just deleted a sound)
        RhythmSqlImpl rhythm = mPlayRhythmFragment.getRhythm();
        if (rhythm != null) {
//            if (rhythm.getDbUpdatedNano() > mDataRefreshedNano) {
                AndroidResourceManager.logw(LOG_TAG, "refreshAllRbtData: rhythm db updated refreshing rbt list");

                try {
                    notifyRhythmChanged(); // updates the rbt from the draughter via the holder
                }
                catch (Exception e) {
                    AndroidResourceManager.logw(LOG_TAG, "refreshAllRbtData: failed to refresh rbt list", e);
                }
        }
    }

    @Override
    protected void fillData() {
        AndroidResourceManager.logv(LOG_TAG, "fillData: mHasLoaded="+mHasLoaded+" mHasLoadStarted="+mHasLoadStarted);

		if (mHasLoadStarted) {
            if (!hasListAdapter()) {
                initListAdapter();
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, "fillDataIfReady: ignoring repeated call to load data");
            }
			return;
		}		
		
		
		mHasLoadStarted = true;
		
		String[] dbColumnsForLayout = new String[] { KybSQLiteHelper.COLUMN_SOUND_NAME };
		int[] toLayoutViewIds = new int[] { android.R.id.text1 };
		
		mSoundsSpinnerAdapter = new SimpleCursorAdapter(mActivity, android.R.layout.simple_spinner_item, null, dbColumnsForLayout, toLayoutViewIds, 0) {

					/**
					 * although just a spinner, because often need to get the name from resource
					 * need to override the bind view
					 */
					@Override
					public void bindView(View v, Context context, Cursor cursor) {

						super.bindView(v, context, cursor);

						TextView tv = (TextView) v;
						
						String soundName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_SOUND_NAME));
						// get the localised string for the name of internal sounds
						String sndResName = cursor.getString(cursor.getColumnIndex(KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME));
						if (sndResName != null) {
							int resId = AbstractSqlImpl.getLocalisedResIdFromName(sndResName, context.getResources());
							if (resId > 0) {
								soundName = context.getResources().getString(resId);
							}
						}
						
						tv.setText(soundName);
					}
			};
			
		mSoundsSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		// cause the sounds cursor to be populated
		getLoaderManager().initLoader(0, null, this);
	}

    /**
     * RhythmPlayingFragmentHolder is listening for rhythm and/or beat type changes, and it passes that info
     * to the activity which tests for this fragment open and calls this method from its own notifyRhythmChanged().
     */
    void notifyRhythmChanged() {
        boolean uiThread = mResourceManager.getGuiManager().isUiThread();
        final String threadName = uiThread ? "UI" : "Non-UI"; // just for the debug msgs

        if (mRbtRefreshIsPending) {
            AndroidResourceManager.logd(LOG_TAG, String.format("notifyRhythmChanged: already pending refresh which should be coming in UI thread (thread=%s id=%s)",
                    threadName, Thread.currentThread().getId()));
            return; // don't do it again while still waiting for first call to complete in UI thread
        }

        if (mPlayRhythmFragment != null) {

            final RhythmSqlImpl rhythm = mPlayRhythmFragment.getRhythm();
            if (rhythm == null) {
                return; // already reported program error if this happens
            }

            if (mAdapter != null) {
                try {
                    mAdapter.updateData(rhythm.getBeatsAllData().getKey()); // type doesn't matter, picking up the existing one
                } catch (Rhythm.NoCurrentCacheException e) {
                    AndroidResourceManager.loge(LOG_TAG, "notifyRhythmChanged: should only be called when there's already an updated cache");
                    mActivity.restartActivityWithFailureMessage(R.string.non_critical_error_restart_activity);
                }
            }
        }
    }

    private class RhythmBeatTypesAdapter extends BaseAdapter
			implements AdapterView.OnItemSelectedListener, View.OnClickListener, CompoundButton.OnCheckedChangeListener {//, View.OnLayoutChangeListener {

		private ArrayList<RhythmBeatType> mData;
		private SpinnerAdapter mSoundsAdapter;
		private KybActivity mActivity;
		private AndroidResourceManager mResourceManager;

        /**
		 * Converts the tree set into an array dialog_simple_list, so changes to the underlying data
		 * can be notified and controlled (ie. on ui thread)
		 */
		RhythmBeatTypesAdapter(TreeSet<RhythmBeatType> rbts, SpinnerAdapter soundsAdapter, Activity activity, AndroidResourceManager resourceManager) {
			this.mActivity = (KybActivity) activity;
			mData = new ArrayList<>();
			for (RhythmBeatType rbt : rbts) {
				mData.add(rbt);
			}
			mSoundsAdapter = soundsAdapter;
			mResourceManager = resourceManager;
		}

        void updateData(TreeSet<RhythmBeatType> rbts) {
            // not pretty to do it this way, but the data rows are the same objs and have the same ids,
            // bit easier than syncing them
            mData.clear();
            for (RhythmBeatType rbt : rbts) {
                mData.add(rbt);
            }

            notifyDataSetChanged();
        }

		@Override
		public int getCount() {
			return mData.size();
		}

		@Override
		public Object getItem(int position) {
			return mData.get(position);
		}

		@Override
		public long getItemId(int position) {
			return mData.get(position).getId();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			
			final RhythmBeatType rbt = mData.get(position);
			
			RelativeLayout layout = convertView == null
					? (RelativeLayout) mActivity.getLayoutInflater().inflate(R.layout.rhythm_beat_type_row, parent, false)
					: (RelativeLayout) convertView;

			BeatType bt = rbt.getBeatType();

            // colour button background
			int colour = ((AndroidColour)bt.getColour()).getColorInt();

            TextView btName = (TextView) layout.findViewById(R.id.beat_type_name);
			btName.setText(bt.getName());
            if (colour != 0) {
                btName.setBackgroundColor(colour);
            }
            else {
                btName.setBackgroundResource(0); // causes remove backgrd
            }
            btName.setOnClickListener(this);

            double contrast = ColorUtils.calculateContrast(WHITE, colour);
            int foregrdColour = contrast < COLOUR_CONTRAST_RATIO_THRESHOLD ? BLACK : WHITE;
            btName.setTextColor(foregrdColour);

            TextView btKey = (TextView) layout.findViewById(R.id.beat_type_key);
			btKey.setText(bt.getKey());

            SoundSqlImpl sound = (SoundSqlImpl) rbt.getSound();

			Spinner soundSpinner = (Spinner) layout.findViewById(R.id.sound_spinner);
            if (mHasLoaded) {
                soundSpinner.setAdapter(mSoundsAdapter);
                setSpinnerItemById(soundSpinner, sound.getInternalId());
                soundSpinner.setOnItemSelectedListener(this);
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, "getView set sounds spinner adapter not ready yet");
            }
			SeekBar volumeBar = layout.findViewById(R.id.volume);
			final float originalVol = rbt.getVolume();
            int progress = (int) (originalVol * 100);
            volumeBar.setProgress(progress);
            final TextView numText = layout.findViewById(R.id.volume_num);
            numText.setText(Integer.toString(progress));

			volumeBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

                VolumeModel mVolumeChangeModel;

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    // set the model multi edit to false
                    mVolumeChangeModel.mSlidingComplete = true;
                    int progress = (int) (originalVol * 100.f);
                    float vol = mVolumeChangeModel.getValue()/100.0f;
                    new RhythmBeatTypeCommand.ChangeVolumeCommand(rbt, progress, mVolumeChangeModel).execute();

                    // allow for another go
                    mVolumeChangeModel = null;
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
//                    mResourceManager.startTransaction();
                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        if (mVolumeChangeModel == null) { // first touch
                            mVolumeChangeModel = new VolumeModel(progress);
                        } else {
                            mVolumeChangeModel.setCurrentValue(progress);
                        }

                        numText.setText(Integer.toString(progress));

                        // the value to set on the sound, this is changed so that the beat tracker (if playing now) picks up the changes
                        float vol = mVolumeChangeModel.getValue()/100.0f;
                        rbt.setVolume(vol);
                    }
                }
            });

            // hide the picker if silent beat type
            boolean isSilentBt = BeatTypes.DefaultBeatTypes.silent.name().equals(bt.getKey());
            soundSpinner.setVisibility(isSilentBt ? View.GONE : View.VISIBLE);

            // set invisible if silent sound
            boolean isNoSound = Sounds.InternalSound.noSound.name().equals(sound.getKey());
            boolean isHideVolume = isSilentBt || isNoSound;
            volumeBar.setVisibility(isHideVolume ? View.INVISIBLE : View.VISIBLE);
            numText.setVisibility(isHideVolume ? View.INVISIBLE : View.VISIBLE);

			// tuck the id away for finding updated rows when a sound is updated
			TextView rbtId = (TextView) layout.findViewById(R.id.rhythm_beat_type_id);
			rbtId.setText(Integer.toString(rbt.getId()));

            final CompoundButton toggleDisplayNumsBtn = (CompoundButton) layout.findViewById(R.id.display_numbers_btn);
            toggleDisplayNumsBtn.setOnCheckedChangeListener(null);
            toggleDisplayNumsBtn.setChecked(rbt.isDisplayNumbers());
            toggleDisplayNumsBtn.setOnCheckedChangeListener(this);

//            AndroidResourceManager.logw(LOG_TAG, String.format("getView: rbt name = %s (sound = %s)", // TODO REMOVE
//                    btName.getText(),
//                    (sound == null ? "no sound" : sound.getName())));

            // custom sound that is broken shows a warning button
            ImageButton warnBtn = (ImageButton) layout.findViewById(R.id.broken_sound_warning_id);
            PlatformResourceManager.SoundResource soundResource = sound.getSoundResource();
            if (!isSilentBt && !isNoSound && !sound.isPlayable() &&
                    (soundResource == null || soundResource.getStatus() != Sound.SoundStatus.LOADED_OK)) {
                AndroidResourceManager.logv(LOG_TAG, String.format("getView: broken sound, show warning button (%s:%s)",
                        sound.getName(), (soundResource == null ? "no resource" : soundResource.getStatus().name())));
                warnBtn.setVisibility(View.VISIBLE);
                warnBtn.setOnClickListener(this);
            }
            else {
                warnBtn.setVisibility(View.GONE);
            }

			return layout;
		}
	
		// https://stackoverflow.com/questions/26412767/how-to-position-select-a-spinner-on-specific-item-by-id-with-simplecursoradapt
		void setSpinnerItemById(Spinner spinner, long _id) {
			int spinnerCount = spinner.getCount();
			for (int i = 0; i < spinnerCount; i++) {
				Cursor value = (Cursor) spinner.getItemAtPosition(i);
				if (value.getLong(value.getColumnIndex(KybSQLiteHelper.COLUMN_ID)) == _id) {
					spinner.setSelection(i);
				}
			}
		}

		//------------------------- implementing the adapterview.onItemSelected
		// NOTE: these refer to the sound spinner, not the dialog_simple_list adapter. so position is in
		// that dialog_simple_list not this one. Uses the parent spinner's parent to find the
		// correct rbt

        /**
         * Method is called automatically when returning from another module if something changes in the selection
         * This is an issue when have just come back from beats & sounds activity and deleted a sound that is selected
         * in the list... it fires this method and it *looks* as though the user has selected another sound.
         * So have to test for the current sound's existence before changing the sound, if it doesn't exist
         * trigger a refresh of the rbt list
         *
         * @param parent
         * @param view
         * @param position
         * @param id
         */
		@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

			// parent is the spinner, its parent should be the rbt's relative layout
			Spinner spinner = (Spinner) parent;
			Cursor value = (Cursor) spinner.getItemAtPosition(position);
			final String soundKey = value.getString(value.getColumnIndex(KybSQLiteHelper.COLUMN_EXTERNAL_KEY));

            final RelativeLayout layout = (RelativeLayout) parent.getParent();
            TextView rbtId = (TextView) layout.findViewById(R.id.rhythm_beat_type_id);

            final RhythmBeatType rbt = getCorrespondingRow(Integer.parseInt((String) rbtId.getText()));

            if (rbt != null) {
                // ignore this if not user selection
                // easy enough, test for sound changed...
                final Sound rbtSound = rbt.getSound();
                if (!rbtSound.getKey().equals(soundKey)) {
                    final SoundsContentProvider sounds = (SoundsContentProvider) mResourceManager.getLibrary().getSounds();

                    AndroidResourceManager.logv(LOG_TAG, "onItemSelected: sound doesn't match, soundkeyatpos="+soundKey+" getSound key="+ rbtSound.getKey()+" name="+ rbtSound.getName());

                    mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(new ParameterizedCallback(true) {
                        @Override
                        public Object runForResult() {
                            Sound replacedSound = sounds.lookup(rbtSound.getKey());
                            AndroidResourceManager.logv(LOG_TAG, "onItemSelected.runForResult: sound doesn't match,  exists="+ replacedSound);
                            if (replacedSound == null) {
                                if (!mRbtRefreshIsPending) { // in notifyRhythmChanged this is set when notice have to update but need to do it in the UI thread
                                                             // so this method might get inserted in between those 2 paths (concurrency issue)
                                                             // this should mean that the call to refresh rbt data is actually not needed here, and this
                                                             // is purely belt and braces
                                    AndroidResourceManager.logw(LOG_TAG, "onItemSelected.runForResult: existing rbt sound doesn't exist, update the rbt data");
                                    refreshAllRbtData();
                                }
                                return null; // see comments to method, the sound doesn't exist so need to refresh the rbt list (it's old)
                            }
                            else {
                                return sounds.lookup(soundKey);
                            }
                        }
                    }, new ParameterizedCallback(false) {
                        @Override
                        public void run() {
                            if (mParam != null) {
                                Sound newSound = (Sound) mParam;
                                // set invisible if silent sound or visible if not (since it could be silent sound changed
                                SeekBar volumeBar = layout.findViewById(R.id.volume);
                                TextView numText = layout.findViewById(R.id.volume_num);
                                boolean isHideVolume = sounds.isSystemSound(newSound)
                                        && Sounds.InternalSound.noSound == Sounds.InternalSound.valueOf(soundKey);
                                volumeBar.setVisibility(isHideVolume ? View.INVISIBLE : View.VISIBLE);
                                numText.setVisibility(isHideVolume ? View.INVISIBLE : View.VISIBLE);

                                new RhythmBeatTypeCommand.ChangeBeatSound(rbt, newSound).execute();
                            }
                        }
                    });

                }
                else {
                    AndroidResourceManager.logv(LOG_TAG, String.format("ignore sound (%s) selected, rbt=%s just loading data", soundKey, rbt.getBeatType().getName()));
                }
			}
            else {
				AndroidResourceManager.logd(LOG_TAG, String.format("could not update sound ((%s) as no rbt found, list needs refresh", soundKey));
			}
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// not an optional dialog_simple_list
		}

		@Override
		public void onClick(final View view) {

            int viewId = view.getId();

            if (/*viewId == R.id.colour_btn ||*/ viewId == R.id.beat_type_name) {
                final RelativeLayout rl = (RelativeLayout) view.getParent();
                final TextView btKey = (TextView) rl.findViewById(R.id.beat_type_key);
                try {
                    final BeatType bt = mResourceManager.getLibrary().getBeatTypes().lookup((String) btKey.getText());
                    final int oriColour = ((AndroidColour) bt.getColour()).getColorInt();
                    final TextView rbtId = (TextView) rl.findViewById(R.id.rhythm_beat_type_id);

                    // see class comments for acknowledgements
                    // 3rd param = supports alpha (will use for overlay playing beat, not for here though)
                    AmbilWarnaDialog dialog = new AmbilWarnaDialog(mActivity, oriColour, false, new AmbilWarnaDialog.OnAmbilWarnaListener() {
                        @Override
                        public void onOk(AmbilWarnaDialog dialog, int color) {

                            RhythmBeatType rbt = getCorrespondingRow(Integer.parseInt((String) rbtId.getText()));

                            if (rbt != null) {
                                // color is the color selected by the user.
    //                        mResourceManager.startTransaction();
                                new RhythmBeatTypeCommand.ChangeBeatColour(rbt, new AndroidColour(color)).execute();
                            } else {
                                AndroidResourceManager.logw(LOG_TAG, "could not update colour for beat type(" + bt.getKey() + ") as no rbt found, this shouldn't ever happen");
                            }
                        }

                        @Override
                        public void onCancel(AmbilWarnaDialog dialog) {
                        }
                    });

                    dialog.show();
                }
                catch (Exception e) {
                    AndroidResourceManager.loge(LOG_TAG, "onClick: unexpected error", e);
                    mResourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_SAVE_ERROR, true, e);
                }
            }

            else if (viewId == R.id.broken_sound_warning_id) {
                new AlertDialog.Builder(mActivity)
                        .setMessage(R.string.rbtTableSoundError)
                        .setPositiveButton(R.string.ok_button, null)
                        .create().show();
            }
            else {
                AndroidResourceManager.logw(LOG_TAG, "onClick: something clicked, don't know what");
            }
		}

        private RhythmBeatType getCorrespondingRow(int rbtId) {

            for (int i = 0; i < mData.size(); i++) {
                RhythmBeatType rbt = mData.get(i);
                if (rbt.getId() == rbtId) {
                    return rbt;
                }
            }

            return null; // none found, handled by calling methods
        }

        @Override
        public void onCheckedChanged(CompoundButton view, boolean b) {

            final RelativeLayout rl = (RelativeLayout) view.getParent();
            final TextView btKey = (TextView) rl.findViewById(R.id.beat_type_key);
            final TextView rbtId = (TextView) rl.findViewById(R.id.rhythm_beat_type_id);

            RhythmBeatType rbt = getCorrespondingRow(Integer.parseInt((String) rbtId.getText()));

            if (rbt != null) {

                if (b != rbt.isDisplayNumbers()) { // when open this will likely fire and shouldn't
                    new RhythmBeatTypeCommand.ChangeDisplayNumbersCommand(rbt, b).execute();
                }
            }
            else {
                AndroidResourceManager.logw(LOG_TAG, "could not update display numbers for beat type("+btKey.getText()+") as no rbt found, this shouldn't ever happen");
            }
        }

//debugging
//        public void debugData() {
//            String threadName = mResourceManager.getGuiManager().isUiThread() ? "UI" : "Non-UI";
//            long threadId = Thread.currentThread().getId();
//            AndroidResourceManager.logd(LOG_TAG, String.format("debugData: thread=%s id=%s", threadName, threadId));
//            for (Iterator<RhythmBeatType> iterator = mData.iterator(); iterator.hasNext(); ) {
//                RhythmBeatType rhythmBeatType = iterator.next();
//                AndroidResourceManager.logd(LOG_TAG, String.format("debugData: rbt in updated set (bt name=%s thread=%s id=%s)"
//                        , rhythmBeatType.getBeatType().getName(), threadName, threadId));
//            }
//        }

        boolean isDataMismatched(RhythmSqlImpl rhythm) {
            return rhythm.isDataMismatched(mData);
        }
    }

	// ------------------------ LoaderCallbacks
	
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		String[] projection = { 
				KybSQLiteHelper.COLUMN_ID, KybSQLiteHelper.COLUMN_EXTERNAL_KEY
				, KybSQLiteHelper.COLUMN_SOUND_NAME, KybSQLiteHelper.COLUMN_LOCALISED_RES_NAME };

		// don't want the extra toggle selections rows
		String selection = String.format("%s >= 0", KybSQLiteHelper.COLUMN_ID);
		return new CursorLoader(mActivity, SoundsContentProvider.CONTENT_URI_ORDERED_LIST, projection, selection , null, null);
	}
	
	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		AndroidResourceManager.logd(LOG_TAG, "onLoadFinished sounds spinner adapter load finished, data="+data.getCount());
        mSoundsSpinnerAdapter.swapCursor(data);

        if (!hasListAdapter()) {
            initListAdapter();
        }
        mHasLoaded = true;
	}

    void initListAdapter() {
        if (mPlayRhythmFragment != null && mPlayRhythmFragment.getRhythmDraughter() != null
                && mHasLoaded) {

            mAdapter = new RhythmBeatTypesAdapter(
                    mPlayRhythmFragment.getRhythmDraughter().getRhythmBeatTypes(),
                    mSoundsSpinnerAdapter,
                    mActivity, mResourceManager);
            setListAdapter(mAdapter);

            mRbtDbRefreshedNano = mPlayRhythmFragment.getRhythm().getDbUpdatedNano();
            AndroidResourceManager.logd(LOG_TAG, "initListAdapter: update local nano time to "+mRbtDbRefreshedNano);

            if (mIsWaitingForDataToSlideOpen) {
                slideOpen();
            }
        }
    }

    @Override
	public void onLoaderReset(Loader<Cursor> loader) {
//		AndroidResourceManager.logd(LOG_TAG, "onLoadReset");
		// we lost the data
		mSoundsSpinnerAdapter.swapCursor(null);
	}
	
	/**
	 * Implementation that wraps the value for the volume seek bar for the change
	 * volume command
	 */
	private static class VolumeModel implements MultiEditModel {
		
		private int mCurrentValue = -1;
		private boolean mSlidingComplete = false; // when seek bar stop is called, this is set to true

		
		VolumeModel(int mCurrentValue) {
			this.mCurrentValue = mCurrentValue;
		}

		@Override
		public boolean isMultiEdit() {
			return !mSlidingComplete;
		}

		@Override
		public int getValue() {
			return mCurrentValue;
		}

		void setCurrentValue(int val) {
			this.mCurrentValue = val;
		}
		
	}
}
