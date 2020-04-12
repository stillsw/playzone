package com.stillwindsoftware.keepyabeat.gui;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;

import java.io.File;

public class BeatsAndSoundsActivity extends KybActivity {
	
	private static final String LOG_TAG = "KYB-"+BeatsAndSoundsActivity.class.getSimpleName();
	public static final String IS_BEATS_AND_SOUNDS_OPEN_ACTION = "com.stillwindsoftware.keepyabeat.gui.BEATS_AND_SOUNDS_OPEN";

	private BeatTypesListFragment mBeatTypesListFragment;
	private SoundsListFragment mSoundsListFragment;
	
	private ViewPager mPager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.beats_and_sounds);

        // add the toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.kyb_toolbar);
        setSupportActionBar(toolbar);

		// this call, together with declarations in manifest causes kyb icon to be a home button
		// to play rhythms activity
        ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.appMenuBeatsSounds);

		// test for existing fragment in case of restart or 2 pane mode
		FragmentManager fm = getSupportFragmentManager();
		
		// pager swiped tabs, needed for config changes, even when both fragments are visible
		mPager = (ViewPager)findViewById(R.id.pager);

		mBeatTypesListFragment = (BeatTypesListFragment) fm.findFragmentById(R.id.beat_types_frag);
		mSoundsListFragment = (SoundsListFragment) fm.findFragmentById(R.id.sounds_frag);

		// pager is used, needs an adapter
		if (mPager.getVisibility() != View.GONE) {//mBeatTypesListFragment == null && mSoundsListFragment == null) {
			
			mPager.setAdapter(new FragmentPagerAdapter(fm) {
				@Override
				public Fragment getItem(int position) {
					if (position == 0) {
						mBeatTypesListFragment = (BeatTypesListFragment) Fragment.instantiate(BeatsAndSoundsActivity.this, BeatTypesListFragment.class.getName());
						return mBeatTypesListFragment;
					}
					else {
						mSoundsListFragment = (SoundsListFragment) Fragment.instantiate(BeatsAndSoundsActivity.this, SoundsListFragment.class.getName());
						return mSoundsListFragment;
					}
				}
				@Override
				public int getCount() {
					return 2;
				}
				@Override
				public CharSequence getPageTitle(int position) {
					return getResources().getString(position == 0 ? R.string.titleBeatTypes : R.string.titleSounds);
				}
			});
		}
	}

	@Override
	protected String getReceiverFilterName() {
		return IS_BEATS_AND_SOUNDS_OPEN_ACTION;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.beats_and_sounds_menu, menu);
		initCommonMenuItems(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
            case R.id.menu_help:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(getString(R.string.beatsSoundsLink)));
                startActivity(intent);
                return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Defined in the row layout for onClick on a sound button
     * both for beat types list and sounds list, hence finding the parent is dependent
	 * @param playBtn
	 */
	public void playSound(final View playBtn) {
        ViewParent viewParent = playBtn.getParent();
        final RelativeLayout parent = viewParent instanceof RelativeLayout
                ? (RelativeLayout) viewParent                       // in sounds list play button is a child in the row level layout
                : (RelativeLayout) viewParent.getParent();          // in beat types list it's a child of the linear layout for the buttons
		TextView urlView = (TextView) parent.findViewById(R.id.sound_url);
		TextView typeView = (TextView) parent.findViewById(R.id.sound_type);
        final TextView soundNameView = (TextView) parent.findViewById(R.id.sound_name);
		final String urlStr = urlView.getText().toString();
		final String typeStr = typeView.getText().toString();
		
        try {
            new AsyncTask<Void, Void, Void>() {

                @Override
                protected Void doInBackground(Void... params) {
                    MediaPlayer mp = null;
                    try {
                        if (KybSQLiteHelper.INTERNAL_TYPE.equals(typeStr)) {
                            final int resId = getResources().getIdentifier(urlStr, "raw", AndroidResourceManager.PACKAGE_NAME);
                            mp = MediaPlayer.create(BeatsAndSoundsActivity.this, resId);
                        }
                        else {
                            File f = BeatsAndSoundsActivity.this.getFileStreamPath(urlStr);
                            if (f.exists()) {
                                mp = MediaPlayer.create(BeatsAndSoundsActivity.this, Uri.fromFile(f));
                            }
                            else {
                                // same activity for beat types and for sounds, the id of the play button determines which it is
                                playBtn.post(new Runnable() {
                                         @Override
                                         public void run() {
                                             String soundKey = null;
                                             AddOrRepairSoundDialog dialog = null;
                                             FragmentManager fragmentManager = getSupportFragmentManager();

                                             try {
                                                 String name = soundNameView.getText().toString();
                                                 soundKey = (String) playBtn.getTag(); // sounds dialog_simple_list tags it
                                                 dialog = AddOrRepairSoundDialog.newInstance(true, soundKey, name);
                                                 dialog.show(fragmentManager, AddOrRepairSoundDialog.LOG_TAG);
                                             }
                                             catch (Exception e) {                  // have seen NPE from this, but shouldn't anymore since fixed it
                                                 AndroidResourceManager.loge(LOG_TAG, String.format(
                                                         "playSound: error showing dialog, url=%s, soundView=%s, playBtn=%s, soundKey=%s, dialog=%s, fm=%s",
                                                         urlStr, soundNameView != null, playBtn != null, soundKey, dialog != null,fragmentManager != null), e);
                                             }
                                         }
                                    });
                            }
                        }
                    } catch (Exception e) {
                        AndroidResourceManager.loge(LOG_TAG, "playSound: media player creation problem url="+urlStr, e);
                    }

                    if (mp != null) {

                        mp.setOnErrorListener(new OnErrorListener() {
                            @Override
                            public boolean onError(MediaPlayer mp, int what, int extra) {
                                AndroidResourceManager.loge(LOG_TAG, "playSound: media player onError what=" + what + " extra=" + extra);
                                return false;
                            }
                        });
                        mp.setOnCompletionListener(new OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                AndroidResourceManager.logd(LOG_TAG, "playSound: media player onCompletion");
                                mp.reset();
                                mp.release();
                            }
                        });
                        mp.start();
                    }

                    else { // null mp
                        AndroidResourceManager.loge(LOG_TAG, "playSound: media player failed to create for " + urlStr);
                    }

                    return null;
                }

            }.execute((Void)null);
        }
        catch (Exception e) {
            AndroidResourceManager.loge(LOG_TAG, "playSound: media player", e);
        }
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == AddOrRepairSoundDialog.CHOOSE_SOUND_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                AddOrRepairSoundDialog dlg = (AddOrRepairSoundDialog) getSupportFragmentManager().findFragmentByTag(AddOrRepairSoundDialog.LOG_TAG);
                if (dlg != null) {
                    dlg.passActivityResult(data);
                } else {
                    AndroidResourceManager.logd(LOG_TAG, "onActivityResult: add sound dialog fragment not found to receive file");
                }
            } else {
                AndroidResourceManager.logd(LOG_TAG, "onActivityResult: didn't return a file req=" + requestCode + " res=" + resultCode + " data=" + data);
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        AddOrRepairSoundDialog dlg = (AddOrRepairSoundDialog) getSupportFragmentManager().findFragmentByTag(AddOrRepairSoundDialog.LOG_TAG);
        if (dlg != null) {
            dlg.clearLoadedSound(true); // delete file
        }
        super.onBackPressed();
    }

/**
	 * Called from onCreate() when no fragments found
	 * @param tabListener 
	 * /
	private void setupTabs(TabListener tabListener) {
		
		//TODO remove this, keeping it for now as an example of how to do tabs

		// don't show kyb title in the action bar
		ActionBar actionBar = getSupportActionBar();
		
		// this call, together with declarations in manifest causes kyb icon to be a home button
		// to play rhythms activity
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
//		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setTitle(R.string.appMenuBeatsSounds);
		
		// make 2 tabs, 1 is for this activity so does nothing here
		// the other goes to beats and sounds activity
		mBeatTypesTab = actionBar.newTab()
				.setText(R.string.titleBeatTypes)
				.setTabListener(tabListener);
		actionBar.addTab(mBeatTypesTab);
		
		mSoundsTab = actionBar.newTab()
				.setText(R.string.titleSounds)
				.setTabListener(tabListener);
		actionBar.addTab(mSoundsTab);
		
	}


	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		// do nothing
	}

	@Override 
	public void onTabSelected(Tab tab, FragmentTransaction ft) {

		if (tab.equals(mBeatTypesTab)) {
			if (mBeatTypesListFragment == null) {
				mBeatTypesListFragment = (BeatTypesListFragment) Fragment.instantiate(this, BeatTypesListFragment.class.getName());;
				ft.add(R.id.fragment_container, mBeatTypesListFragment, TAG_BEAT_TYPES);
			}
			else {
				ft.attach(mBeatTypesListFragment);
			}
		}
		else {
			if (mSoundsListFragment == null) {
				mSoundsListFragment = (SoundsListFragment) Fragment.instantiate(this, SoundsListFragment.class.getName());
				ft.add(R.id.fragment_container, mSoundsListFragment, TAG_SOUNDS);
			}
			else {
				ft.attach(mSoundsListFragment);
			}
		}
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		if (tab.equals(mBeatTypesTab)) {
			ft.detach(mBeatTypesListFragment);
		}
		else {
			ft.detach(mSoundsListFragment);
		}
	}
*/

}
