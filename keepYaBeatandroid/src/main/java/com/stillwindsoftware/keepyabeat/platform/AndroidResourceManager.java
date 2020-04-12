package com.stillwindsoftware.keepyabeat.platform;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.text.ParseException;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.stillwindsoftware.keepyabeat.BuildConfig;
import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.android.KybApplication;
import com.stillwindsoftware.keepyabeat.control.PendingTasksScheduler;
import com.stillwindsoftware.keepyabeat.control.UndoableCommand;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.db.DbBackedUndoableCommandController;
import com.stillwindsoftware.keepyabeat.db.KybContentProvider;
import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.db.RhythmSqlImpl;
import com.stillwindsoftware.keepyabeat.db.SoundsContentProvider;
import com.stillwindsoftware.keepyabeat.gui.EditRhythmActivity;
import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.gui.PlayRhythmsActivity;
import com.stillwindsoftware.keepyabeat.model.BeatTypes.DefaultBeatTypes;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation.Key;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore.BucketImage;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore.DraughtImageType;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;

public class AndroidResourceManager extends PlatformResourceManager implements ColourManager {

	private static final String LOG_PREFIX = "KYB-";
	private static final String CORE_LOG_TAG = LOG_PREFIX+"%s";
	
	private static final String LOG_TAG = LOG_PREFIX+AndroidResourceManager.class.getSimpleName();

	// when Resources.getIdentifier() is used, needs the package name
	public static final String PACKAGE_NAME = "com.stillwindsoftware.keepyabeat";
	public static final String DIALOG_FRAGMENT_TAG = "fragDialog";

    // used by super class constructor, so make static... otherwise it runs before the instance initialization and is 0
    private static final int MAX_UNDO_HISTORY = 1000; // 1000 each of general and rhythm edits
    private static final int MAX_LOW_MEM_HISTORY = 50; // low memory device >= KITKAT (see constructor)

    // loading of sounds from file/uri/input stream
    private static final float MAX_SOUND_LEN = .4f;
    private static final int SAMPLE_RATE_22050 = 22050;

    private static final long ONE_HOUR_MILLIS = 1000 * 60 * 60;
    private static final long FIVE_MINUTES_MILLIS = 1000 * 60 * 5;

    // used by full beat overlay image in draw() for that reason allowing public access
    static boolean sIsEditorCurrent;

    private int mMaxUndoHistory;

    private final AndroidColour DEFAULT_COLOUR;
	
	private KybApplication mApplicationContext;
	private WeakReference<KybActivity> mLatestActivity;

	private LOG_TYPE mMinLogLevel = LOG_TYPE.warning;

	private AndroidGuiManager mGuiManager;
	private SettingsManager mSettingsManager;
	
	// cache the color filters for reuse (every beat in every draw needs a colour)
	private SparseArray<ColorFilter> mCachedColorFilters;

    private boolean mIsRhythmPlaying, mIsLowMemory;

    public AndroidResourceManager(KybApplication applicationContext) {
		super(null);
		mApplicationContext = applicationContext;
		DEFAULT_COLOUR = new AndroidColour(Color.parseColor(mApplicationContext.getResources().getString(R.string.normalColour)));
        mMaxUndoHistory = MAX_UNDO_HISTORY;
        // undos will be kept in the db, so limit them for low memory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            shrinkUndoLimitForLowMemoryDevice(applicationContext);
        }
	}

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void shrinkUndoLimitForLowMemoryDevice(KybApplication applicationContext) {
        ActivityManager activityManager = (ActivityManager) applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
        mIsLowMemory = activityManager.isLowRamDevice();
        if (mIsLowMemory) {
            AndroidResourceManager.logd(LOG_TAG, "shrinkUndoLimitForLowMemoryDevice: detected low memory, undo limit set to "+MAX_LOW_MEM_HISTORY);
            mMaxUndoHistory = MAX_LOW_MEM_HISTORY;
        }
    }

    public boolean isLowMemoryDevice() {
        return mIsLowMemory;
    }

    @Override
    public UndoableCommandController getUndoableCommandController() {
        if (undoableCommandController == null) {
            undoableCommandController = new DbBackedUndoableCommandController(this, getUndoHistoryLimit(), KybContentProvider.MEMENTO_STACK_KEY_GENERAL);
        }
        return undoableCommandController;
    }

    @Override
	public void initSounds() {
	}

	/**
	 * The superclass just returns what's in the member variable. However, if memory is
	 * low the imageStore is destroy()'ed and the reference set to null. So, this class
	 * has to be able to re-initialise a new image store.
	 */
	@Override
	public RhythmDraughtImageStore getRhythmDraughtImageStore() {
		
		if (rhythmDraughtImageStore == null) {
			final int IMAGE_1_MIN_SIZE = 1;
			final int IMAGE_6_MIN_SIZE = 25; // one larger than what's used for images in dialog_simple_list
			
			// init and load images, these are all resource id images, but it may be
			// necessary to have this happen asyncronously
			rhythmDraughtImageStore = new RhythmDraughtImageStore();
			
			long start = System.currentTimeMillis();
			
			final Resources res = mApplicationContext.getResources();
			float density = res.getDisplayMetrics().density;
			
			// beat images
			AndroidBeatShapedImage fullBeatBase = new AndroidBeatShapedImage(this)
				.setColour(Color.WHITE)
				.setStroke(Color.BLACK, .02f)
				.setCornerRadiusFraction(.18f);
			AndroidBeatShapedImage fullBeatBaseSml = new AndroidBeatShapedImage(this)
				.setColour(Color.WHITE)
				.setStroke(Color.BLACK, .02f)
				.setCornerRadiusFraction(.18f);
			AndroidBeatShapedImage fullBeatOverlay = new FullBeatOverlayImage(this)
				.setStroke(res.getColor(R.color.fullbeat_overlay_edge), .09f);
			AndroidBeatShapedImage fullBeatOverlaySml = new FullBeatOverlayImage(this)
				.setStroke(res.getColor(R.color.fullbeat_overlay_edge), .09f);
            AndroidBeatShapedImage fullBeatSelected = new AndroidBeatShapedImage(this)
                    .setColour(res.getColor(R.color.android_highlight_blue_with_transparency))
                    .setStroke(res.getColor(R.color.android_highlight_blue), .02f)
                    .setCornerRadiusFraction(.18f);
			AndroidBeatShapedImage numbersBackgrd = new AndroidBeatShapedImage(this)
				.setColour(res.getColor(R.color.numbers_bgd_fill))
				.setStroke(res.getColor(R.color.numbers_bgd_stroke), .1f)
				.setCornerRadiusFraction(.36f);

			AndroidImage beatDivisionLeft3x3 = new AndroidDrawableImage(this, res.getDrawable(R.drawable.beat_division_left), 3, 3);
			AndroidImage beatDivisionRight3x3 = new AndroidDrawableImage(this, res.getDrawable(R.drawable.beat_division_right), 3, 3);
			AndroidImage beatDivisionHoriLarge = new AndroidDrawableImage(this, res.getDrawable(R.drawable.beat_division_top), 11, 9);
			AndroidImage beatDivisionHori1x1 = new AndroidDrawableImage(this, res.getDrawable(R.drawable.beat_division_min), 1, 1);
            AndroidImage beatExplosion = new AndroidDrawableImage(this, res.getDrawable(R.drawable.beat_explosion), 160, 160);

			// playing images
			int progW = (int) (7 * density); 
			ProgressImage progImg = new ProgressImage(progW, -1);
			
			rhythmDraughtImageStore.loadImages(
					new BucketImage(DraughtImageType.FULL_BEAT, fullBeatBase, IMAGE_6_MIN_SIZE)
					, new BucketImage(DraughtImageType.FULL_BEAT_OVERLAY, fullBeatOverlay, IMAGE_6_MIN_SIZE)
					, new BucketImage(DraughtImageType.FULL_BEAT, fullBeatBaseSml, IMAGE_1_MIN_SIZE)
					, new BucketImage(DraughtImageType.FULL_BEAT_OVERLAY, fullBeatOverlaySml, IMAGE_1_MIN_SIZE)
                    , new BucketImage(DraughtImageType.SELECTED_FULL_BEAT_OVERLAY, fullBeatSelected, IMAGE_6_MIN_SIZE)
					, new BucketImage(DraughtImageType.BEAT_DIVISION_LEFT, beatDivisionLeft3x3, 3)
					, new BucketImage(DraughtImageType.BEAT_DIVISION_RIGHT, beatDivisionRight3x3, 3)
					, new BucketImage(DraughtImageType.BEAT_DIVISION_HORI, beatDivisionHoriLarge, 11)
					, new BucketImage(DraughtImageType.BEAT_DIVISION_HORI, beatDivisionHori1x1, IMAGE_1_MIN_SIZE)
					, new BucketImage(DraughtImageType.NUMBERS_BACKGRD, numbersBackgrd, 7)
					, new BucketImage(DraughtImageType.PROGRESS_INDICATOR, progImg, IMAGE_1_MIN_SIZE)
                    , new BucketImage(DraughtImageType.EXPLODE_BEAT_EFFECT, beatExplosion, 1)
			);
			
			// reset the flag
			imageStoreState = ImageStoreState.READY;

			long elapsed = System.currentTimeMillis() - start;
			AndroidResourceManager.logd(LOG_TAG, String.format("getRhythmDraughtImageStore: loaded images in %s millis", elapsed));
		}
		
		return rhythmDraughtImageStore;
	}
	
	/**
	 * Called by any activity that is coming into the foreground in onResume(), it must also 
	 * recall with null in onPause().
	 * Used for convenience eg. to provide a UI thread to provide a runnable to show a dialog in guiManager
	 * at which point a short as possible lock should be taken to avoid possible NPE if set to null.
	 * Really intended for core APIs that lack direct contact with a context, any use of it should first
	 * ensure that the activity is in a resumed state.
     * @param latestActivity
     */
	public synchronized WeakReference<KybActivity> setLatestActivity(KybActivity latestActivity) {
		this.mLatestActivity = new WeakReference<>(latestActivity);
        sIsEditorCurrent = latestActivity != null && latestActivity.isAnEditorActivity();
        return mLatestActivity;
	}

	/**
	 * So notes on setLatestActivity()
	 * @return
	 */
	public KybActivity getLatestActivity() {
        if (mLatestActivity != null)
    		return mLatestActivity.get();

        return null;
	}

	@Override
	public String getVersionInfo() {

		return mApplicationContext.getResources().getString(R.string.appVersion, BuildConfig.VERSION_NAME);
	}

	@Override
	protected boolean isLegalSoundForPlatform(SoundResourceType resourceType, String fileName) {

//		//TODO
//		if (!SoundResourceType.INT.equals(resourceType)) {
//			Toast.makeText(mApplicationContext, LOG_TAG+".isLegalSoundForPlatform: needs to do something for "+resourceType, Toast.LENGTH_SHORT).show();
//		}
		return SoundResourceType.INT.equals(resourceType);
	}

	@Override
	public SoundResource getSoundResourceFromName(SoundResourceType resourceType, String urlStr, float duration,
			boolean loadSoundFile, boolean isLegal) {

		AndroidSoundResource soundResource = new AndroidSoundResource(resourceType);

		// internal type will need to return a resource when loading from xml
		// although this is not read
		// other cases, this method is not called in Android implementation
		if (SoundResourceType.INT.equals(resourceType)) {
			if (loadSoundFile) {
				throw new UnsupportedOperationException("AndroidResourceManager internal sound resources not init here");
			}
			else {
				return soundResource;
			}
		}
		
		soundResource.setUriPath(urlStr);
		
		// the rest is similar to the twl implementation... for now anyway
		File soundFile = null;
		if (isLegal) {
			// non-INT files have full uri path name
			try {
                soundFile = mApplicationContext.getFileStreamPath(urlStr);
				soundResource.setSoundFile(soundFile);

				// not exists
				if (soundFile.exists()) {
                    soundResource.setStatus(Sound.SoundStatus.LOADED_OK);
                }
                else {
					throw new Exception("Sound file not found");
				}
				
			} catch (Exception e) {
				// keep the uri on the resource so it will be preserved in the library
				soundResource.setLoadError(SoundResourceLoadError.UNREACHABLE_CORRECTABLE);
				AndroidResourceManager.loge(LOG_TAG, String.format("getSoundResourceFromName: invalid URI loading Sound file for %s"
						, urlStr));

				soundResource.setLoadErrorLocalisedMessage(
						mApplicationContext.getResources()
							.getString(R.string.MISSING_SOUND_FILE_LIBRARY_ERROR, urlStr));

				// loading from a rhythms import file, there'll be an xml loader to collate the errors
				if (libraryXmlLoader != null) {
					libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.MISSING_SOUND_FILE_LIBRARY_ERROR, urlStr);
				}
			}
		}
		else {
			// is not legal, ie. not supported on this platform 
			soundResource.setLoadError(SoundResourceLoadError.UNREACHABLE_TERMINAL);
			AndroidResourceManager.loge(LOG_TAG, String.format("getSoundResourceFromName: illegal Sound file for this platform %s"
					, urlStr));
			
			soundResource.setLoadErrorLocalisedMessage(
					mApplicationContext.getResources()
						.getString(R.string.ILLEGAL_SOUND_FILE_LIBRARY_ERROR, urlStr));

			// loading from a rhythms import file, there'll be an xml loader to collate the errors
			if (libraryXmlLoader != null) {
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
					CoreLocalisation.Key.ILLEGAL_SOUND_FILE_LIBRARY_ERROR, urlStr);
			}
		}
		
		return soundResource;
	}

	@Override
	public Sound convertToSound(ProposedSoundResource proposedSoundResource,
			boolean loadSoundFile) throws Exception {
//		AndroidGuiManager.logTrace(AndroidGuiManager.TO_IMPLEMENT, this.getClass().getSimpleName());
		return null;
	}

	@Override
	public boolean isSoundFileInLibrary(File testFile) {
		return ((SoundsContentProvider)library.getSounds()).isUriInLibrary(Uri.fromFile(testFile));
	}

	@Override
	public ColourManager getColourManager() {
		return this;
	}

	/**
	 * Lazy init the android version: SettingsManager
	 */
	@Override
	public PersistentStatesManager getPersistentStatesManager() {
		if (mSettingsManager == null) {
			mSettingsManager = new SettingsManager(mApplicationContext);
		}
		return mSettingsManager;
	}

	@Override
	public Library getLibrary() {
		return library;
	}

	/**
	 * Lazy init so that if cleared down in releaseResources() it can re-create itself 
	 * when next needed
	 */
	@Override
	public synchronized GuiManager getGuiManager() {

		if (mGuiManager == null) {
			mGuiManager = AndroidGuiManager.getNewInstance(this);
		}
		
		return mGuiManager;
	}

	/**
	 * Override for synchronizing access, as it's lazy init
	 */
	@Override
	public synchronized ListenerSupport getListenerSupport() {

		if (listenerSupport == null) {
			listenerSupport = ListenerSupport.getNewInstance(this);
		}

		return listenerSupport;
	}
	
	@Override
	protected void setListenerSupport() {
		// lazy init for android, so nothing to do		
	}
	
	/**
	 * tells the pending tasks scheduler to create its own threads
	 * which it must destroy itself too. (see releaseResources())
	 */
	@Override
	public boolean isPendingTasksSchedulerSelfThreading() {
		return true;
	}
	
	/**
	 * Override for synchronizing access, as it's lazy init
	 */
	@Override
	public PendingTasksScheduler getPendingTasksScheduler() {
        AndroidResourceManager.loge(LOG_TAG, "getPendingTasksScheduler should not be called in Android version, see stack trace for offending method");
        return null;
	}
	
	@Override
	protected void setPendingTasksScheduler() {
		// not using the scheduler for android
	}

	@Override
    public void startTransaction() {
        if (library != null) {
            AndroidResourceManager.logd(LOG_TAG, "startTransaction: start db transaction");
//            AndroidGuiManager.logTrace("startTransaction: start db transaction", LOG_TAG);

            final SQLiteDatabase db = ((SQLiteOpenHelper) library).getWritableDatabase();
            if (db.inTransaction()) {
                AndroidResourceManager.loge(LOG_TAG, "startTransaction: attempt to start a transaction when already in one");
//                AndroidGuiManager.logTrace("startTransaction: ", this.getClass().getSimpleName());
            }
            else {
                db.beginTransaction();
            }
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "startTransaction: attempt to start transaction without a current library");
        }
    }

    public boolean isInTransaction() {
        if (library != null) {

            final SQLiteDatabase db = ((KybSQLiteHelper) library).getWritableDatabase(false);
            return db.inTransaction();
        }

        return false;
    }

    /**
     * Returns true to indicate it was necessary to start a new transaction
     * @return
     */
    public boolean startTransactionUnlessAlreadyInOne() {
        if (library != null) {

            final SQLiteDatabase db = ((SQLiteOpenHelper) library).getWritableDatabase();
            if (db.inTransaction()) {
                AndroidResourceManager.logd(LOG_TAG, "startTransactionUnlessAlreadyInOne: not started as already in one");
                return false;
            }
            else {
                AndroidResourceManager.logd(LOG_TAG, "startTransactionUnlessAlreadyInOne: start db transaction");
                db.beginTransaction();
                return true;
            }
        }
        else {
            AndroidResourceManager.loge(LOG_TAG, "startTransaction: attempt to start transaction without a current library");
            return false;
        }
    }

    @Override
	public boolean rollbackTransaction() {
		if (library != null) {
			SQLiteDatabase db = ((SQLiteOpenHelper)library).getWritableDatabase();
			if (!db.inTransaction()) {
				AndroidResourceManager.loge(LOG_TAG, "rollbackTransaction: attempt to rollback not in a transaction");
//				AndroidGuiManager.logTrace("rollbackTransaction: rollback transaction", this.getClass().getSimpleName());
			}
			else {
				((SQLiteOpenHelper)library).getWritableDatabase().endTransaction();
				AndroidResourceManager.logd(LOG_TAG, "rollbackTransaction: rollback db transaction");
				return true;
			}
		}
		else {
			AndroidResourceManager.logw(LOG_TAG, "rollbackTransaction: attempt to rollback transaction without a current library");
		}
		
		return false;
	}
	
	@Override
	public void saveToStore() {
		if (library != null) {
			SQLiteDatabase db = ((SQLiteOpenHelper)library).getWritableDatabase();
			if (!db.inTransaction()) {
				AndroidResourceManager.loge(LOG_TAG, "saveToStore: attempted to save but not in a transaction");
//				AndroidGuiManager.logTrace("saveToStore: end db transaction", this.getClass().getSimpleName());
			}
			else {
				AndroidResourceManager.logd(LOG_TAG, "saveToStore: end db transaction");
//                AndroidGuiManager.logTrace("saveToStore: end db transaction", LOG_TAG);
				db.setTransactionSuccessful();
				db.endTransaction();
			}
		}
		else {
			AndroidResourceManager.loge(LOG_TAG, "saveToStore: attempt to save without a current library");
		}
	}

	@Override
	public void releaseSounds() {
		// not needed, sounds are released by the beat tracker service binder
	}

	@Override
	public void removeOldFiles() {
		// nothing to do here yet
	}

	@Override
	public boolean isLogging(LOG_TYPE type) {
		return BuildConfig.LOG_DEBUG || mMinLogLevel.compareTo(type) <= 0;
	}

	@Override
	public String getLocalisedString(Key key) {
		return getLocalisedString(key, (Object[])null);
	}

	public String getLocalisedString(Key key, Object ... params) {
		Resources r = mApplicationContext.getResources();
		int id = r.getIdentifier(key.name(), "string", "com.stillwindsoftware.keepyabeat");
		if (id != 0) {
			if (params == null) {
				return r.getString(id);
			}
			else {
				return r.getString(id, params);
			}
		}
		else {
			AndroidResourceManager.loge(LOG_TAG, "getLocalisedString: could not find resId for core key="+key);
			return key.name();
		}
	}

    public static void logd(String logTag, String message) {
        if (BuildConfig.LOG_DEBUG) {
            Log.d(logTag, message);
        }
    }

    public static void logd(String logTag, String message, Throwable e) {
        if (BuildConfig.LOG_DEBUG) {
            Log.d(logTag, message, e);
        }
    }

    public static void logv(String logTag, String message) {
        if (BuildConfig.LOG_DEBUG) {
            Log.v(logTag, message);
        }
    }

    public static void logi(String logTag, String message) {
        if (BuildConfig.LOG_DEBUG) {
            Log.i(logTag, message);
        }
    }

    public static void logw(String logTag, String message) {
        Log.w(logTag, message);
    }

    public static void logw(String logTag, String message, Throwable e) {
        Log.w(logTag, message, e);
    }

    public static void loge(String logTag, String message) {
        Log.e(logTag, message);
    }

    public static void loge(String logTag, String message, Throwable e) {
        Log.e(logTag, message, e);
    }

    /**
     * Core classes will call this to log, android classes will use the above static methods
     * @param type
     * @param origin
     * @param message
     */
	@Override
	public void log(LOG_TYPE type, Object origin, String message) {
		// do nothing if not logging this type
		if (!isLogging(type)) {
			return;
		}
		
		// static methods might call with null
		if (origin == null) {
			origin = this;
		}

		String logTag = String.format(CORE_LOG_TAG, origin.getClass() == String.class ? origin : origin.getClass().getSimpleName());

		// translate to log types
		switch (type) {
		case error : AndroidResourceManager.loge(logTag, message); break;
		case warning : AndroidResourceManager.logw(logTag, message); break;
		case info : AndroidResourceManager.logi(logTag, message); break;
		case debug : AndroidResourceManager.logd(logTag, message); break;
		case verbose : AndroidResourceManager.logv(logTag, message); break; // verbose always to that logger
		}
	}

	@Override
	public int getUndoHistoryLimit() {
		return mMaxUndoHistory;
	}

	/**
	 * Called by persistent states manager at startup, and after settings dialog changes it
	 * @param minLogLevel
	 */
	@Override
	public void setMinLogLevel(LOG_TYPE minLogLevel) {
		mMinLogLevel = minLogLevel;
	}

    /**
     * Where device is pre ICS the newer level based method isn't called, but still need to release the resources that were
     * to be released for non-critical calls
     */
    @Override
    public void releaseResources() {
        AndroidResourceManager.logd(LOG_TAG, "releaseResource() called without param - either from param method, or pre-ICS, calling super()");
        releaseAlways();

        super.releaseResources();
    }

    /**
     * Test for an activity, only called from super.releaseResources()
     * @return
     */
    @Override
    protected boolean isOkToDestroyForegroundResources() {
        return getLatestActivity() == null;
    }

    /**
     * Small stuff that can be released in
     */
    private void releaseAlways() {
        // should be null, but make sure
        libraryXmlLoader = null;
        // always release color filters, they might not be needed, or at least not all
        mCachedColorFilters = null;
    }

    /**
	 * Overloads the superclass method, which is the most drastic
	 * and called when Application.onLowMemory() is triggered. 
     *
     * note: the beat tracking/playing service is already handled
	 * @param level
	 */
	@SuppressLint("NewApi") // only called in the application >= ICS
	public void releaseResources(int level) {

        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            AndroidResourceManager.logd(LOG_TAG, "releaseResource(int) < TRIM_MEMORY_UI_HIDDEN so just release always");
            releaseAlways();
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "releaseResource(int) >= TRIM_MEMORY_UI_HIDDEN so clearing lots of stuff");
			releaseResources(); // tests for playing now, and releases as it can
		}
	}

	/**
	 * Called from DataLoaderFragment.seedRhythmsData()
	 * @param libraryXmlLoader
	 */
	public void setLibraryXmlLoader(LibraryXmlLoader libraryXmlLoader) {
		this.libraryXmlLoader = libraryXmlLoader;
	}
	
	/**
	 * Called from the activity (currently PlayRhythmsActivity) that loads the seed rhythms data.
	 * It can then be the place where feedback is handled, this happens once only which is why
	 * it is a detach method.
	 */
	public LibraryXmlLoader detachXmlLoader() {
		LibraryXmlLoader loader = this.libraryXmlLoader;
		libraryXmlLoader = null;
		return loader;
	}

	@Override
	public void initLogging() {
		// n/a
	}

	@Override
	public void closeLogging() {
		// n/a
	}

	public KybApplication getApplication() {
		return mApplicationContext;
	}

	//---------------------- ColourManager methods
	
	@Override
	public Colour getDefaultColour(DefaultBeatTypes defaultBeatType) {
		return DEFAULT_COLOUR; // same for all, the actual defaults have already been 
							// made in library create
	}

	@Override
	public Colour getColour(String hexString) {
        try {
            return new AndroidColour(Color.parseColor(hexString));
        }
        catch (Exception e) {
            // fix to get over bad format
            if (hexString.charAt(0) != '#') {
                hexString = "#"+hexString;
                AndroidResourceManager.logw(LOG_TAG, "getColour: prepending # to colour hex string "+hexString);
            }
            return new AndroidColour(Color.parseColor(hexString));
        }
    }

	@Override
	public boolean hasTransparency(Colour colour) {
		return Color.alpha(((AndroidColour)colour).getColorInt()) < 255;
	}
	
	/**
	 * Method for dynamic adding and retrieval of colour modes
	 * @param colour
	 * @return
	 */
	public ColorFilter getCachedColorFilter(int colour) {
		
		ColorFilter filter = null;
		
		// lazy init new array, release will clear it out when Android asks for memory
		if (mCachedColorFilters == null) {
			// capacity is likely quite small in most cases, 
			// so starting at 4 is not unreasonable
			mCachedColorFilters = new SparseArray<ColorFilter>(4);
		}
		
		// otherwise try to get the filter from it
		else {
			filter = mCachedColorFilters.get(colour);
		}
		
		// none found, make it and store for another lookup
		if (filter == null) {
			filter = new PorterDuffColorFilter(colour, PorterDuff.Mode.MULTIPLY);
			mCachedColorFilters.put(colour, filter);
		}
				
		return filter;
	}


    public void setRhythmPlaying(boolean rhythmPlaying, boolean selfStopped) {
        this.mIsRhythmPlaying = rhythmPlaying;

        // let the player know when it stops
        if (!rhythmPlaying) {
            KybActivity activity = getLatestActivity();
            if (activity != null) {
                activity.updateUiWhenRhythmStops(!selfStopped);
            }
        }
    }

    public boolean isRhythmPlaying() {
        return mIsRhythmPlaying;
    }

    @Override
    public Rhythm getLatestOpenCopyOfRhythmIfAvailable(Rhythm rhythm) {

        Activity a = getLatestActivity();

        if (a != null && (a instanceof PlayRhythmsActivity || a instanceof EditRhythmActivity)) {

            Rhythm testRhythm = ((KybActivity) (a)).getRhythm();
            if (testRhythm.getKey().equals(rhythm.getKey())) {
                return testRhythm;
            }

            // any other rhythm don't return it, looking for copy of the rhythm passed in, not the open rhythm which could be another entirely
        }
        return super.getLatestOpenCopyOfRhythmIfAvailable(rhythm);
    }

    @Override
    public String getUndoneFeedbackContextualDesc(UndoableCommand command) {

        Object obj = command.getContextualObject();
        Resources r = mApplicationContext.getResources();

        if (obj != null) {

            // test for context needed, currently only rhythm is supported

            if (obj instanceof RhythmSqlImpl) {

                Activity a = getLatestActivity();
                if (a != null && a instanceof PlayRhythmsActivity) {
                    Rhythm currRhythm = ((PlayRhythmsActivity) a).getRhythm();

                    if (currRhythm != null && ((RhythmSqlImpl)obj).getKey().equals(currRhythm.getKey())) {
                        // same rhythm, don't need extra context info, so drop through
                    }
                    else {
                        // rhythm no longer in context, need to show extra info
                        return r.getString(R.string.UNDONE_FEEDBACK_WITH_CONTEXT, command.getDesc(), ((RhythmSqlImpl)obj).getName());
                    }
                }
            }
        }

        // no contextual data found or not needed
        return r.getString(R.string.UNDONE_FEEDBACK, command.getDesc());
    }

    /**
     * From BySoundAudioTrackStrategy wants just the audio data from the input stream (ie. ignore 1st 36 bytes),
     * from AddOrRepairSoundDialog wants the entire sound file for writing to local file.
     * @param context
     * @param is
     * @param justData
     * @param returnShortArray
     * @return
     * @throws IOException
     */
    public Object loadAndTestSoundFromInputStream(Context context, InputStream is, boolean justData, boolean returnShortArray) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(is);
        if (!justData) {
            bis.mark(36); // after reading the 1st 36 bytes, if all good will reset to here
        }

        byte[] byteBuff = new byte[4];

        // skip to 20 bytes to get file format
        bis.skip(20);
        bis.read(byteBuff, 0, 2); // read 2 so we are at 22 now
        boolean isPCM = ((short)byteBuff[0]) == 1;
        AndroidResourceManager.logd(LOG_TAG, "loadAndTestSoundFromInputStream: File isPCM "+isPCM);

        // at 22 bytes to get # channels
        bis.read(byteBuff, 0, 2);// read 2 so we are at 24 now
        int channels = (short)byteBuff[0];
        AndroidResourceManager.logd(LOG_TAG, "loadAndTestSoundFromInputStream: #channels " + channels + " (byteBuff=" + byteBuff[0] + ")");

        // at 24 bytes to get sampleRate
        bis.read(byteBuff, 0, 4); // read 4 so now we are at 28
        int sampleRate = bytesToInt(byteBuff, 4);
        AndroidResourceManager.logd(LOG_TAG, "loadAndTestSoundFromInputStream: Sample rate " + sampleRate);

        // skip to 34 bytes to get bits per sample
        bis.skip(6); // we were at 28...
        bis.read(byteBuff, 0, 2);// read 2 so we are at 36 now
        int bitDepth = (short)byteBuff[0];
        AndroidResourceManager.logd(LOG_TAG, "bit depth " + bitDepth);

        if (!isPCM || sampleRate != SAMPLE_RATE_22050 || bitDepth != 16 || channels != 1) {
            throw new IllegalArgumentException(context.getString(R.string.sound_file_compat_error_format));
        }

        // number of bytes per sample either 1 (8 bits) or 2 (16 bits)
        int bytesPerSample = bitDepth / 8;
        AndroidResourceManager.logd(LOG_TAG, "loadAndTestSoundFromInputStream: bytesPerSample " + bytesPerSample);

        // test not too many samples, might as well now read those 36 bytes in too, since it's a legal file
        byte[] bytes = new byte[(int)(
                (justData ? 0 : 36) + SAMPLE_RATE_22050 * bytesPerSample * MAX_SOUND_LEN)];
        ByteArrayOutputStream result = null;

        result = new ByteArrayOutputStream(bytes.length);
        int bytesRead = 0,
                totalSampleBytesRead = (justData ? 0 : -36), // start with total offset for the first 36 bytes
                totalBytesWritten = 0;
        if (!justData) {
            bis.reset(); // reset to get the whole file (might still abort if too long)
        }

        float numSamples = 0.f;
        float duration = 0.f;

        while (bytesRead != -1) {
            // read() returns -1, 0, or more :
            bytesRead = bis.read(bytes);
            if (bytesRead > 0) {
                totalSampleBytesRead += bytesRead;
                numSamples = totalSampleBytesRead / (float) bytesPerSample;
                duration = numSamples / sampleRate;

                // trap too long file length
                if (duration > MAX_SOUND_LEN) {
                    AndroidResourceManager.logd(LOG_TAG, String.format(
                            "loadAndTestSoundFromInputStream: Trapped too many bytes before finished reading read=%d #samples=%.2f len=%.2f",
                            totalSampleBytesRead, numSamples, duration));
                    throw new IllegalArgumentException(context.getString(R.string.errorSoundFileLength));
                }

                AndroidResourceManager.logd(LOG_TAG, String.format(
                        "loadAndTestSoundFromInputStream: about to write to bytes, read=%d, total read=%d, already written=%d, bytes len=%d #samples=%.2f len=%.2f",
                        bytesRead, totalSampleBytesRead, totalBytesWritten, bytes.length, numSamples, duration));

                result.write(bytes, totalBytesWritten, bytesRead);
                totalBytesWritten += bytesRead;
            }
        }

        // got this far, must be a good wav file
        AndroidResourceManager.logd(LOG_TAG, String.format("loadAndTestSoundFromInputStream: Read sound, total bytes read=%d written=%d #samples=%.2f len=%.2f",
                totalSampleBytesRead, totalBytesWritten, numSamples, duration));

        if (justData) {
            Buffer buffer = convert16BitWaveAudioBytes(bytes, returnShortArray);
            if (returnShortArray) {
                short[] sa = new short[totalBytesWritten / 2];
                final int beforeAssignLen = sa.length;
                ((ShortBuffer)buffer).get(sa);
                final int afterAssignLen = sa.length;
                AndroidResourceManager.logd(LOG_TAG, String.format("loadAndTestSoundFromInputStream: returning short array len(before)=%d len(after)=%d", beforeAssignLen, afterAssignLen));
                return sa;
            }
            else {
                return buffer.array();
            }
        }
        else {
            return new AndroidSoundResource(SoundResourceType.URI, duration, bytes);
        }
    }

    private Buffer convert16BitWaveAudioBytes(byte[] data, boolean returnShortArray) {
        ByteBuffer dest = ByteBuffer.wrap(new byte[data.length]);
        dest.order(ByteOrder.nativeOrder());
        ByteBuffer src = ByteBuffer.wrap(data);
        src.order(ByteOrder.LITTLE_ENDIAN);

        ShortBuffer dest_short = dest.asShortBuffer();
        ShortBuffer src_short = src.asShortBuffer();

        while (src_short.hasRemaining()) {
            // note the short is a signed value
            dest_short.put(src_short.get());
        }

        Buffer result = returnShortArray ? dest_short : dest;
        result.rewind();

        return result;
    }

    /**
     *convert the sent byte array into an int. Assumes little endian byte ordering.
     *@param bytes - the byte array containing the data
     *@param wordSizeBytes - the number of bytes to read from bytes array
     *@return int - the byte array as an int
     */
    private int bytesToInt(byte[] bytes, int wordSizeBytes) {
        int val = 0;
        for (int i=wordSizeBytes-1; i>=0; i--) {
            val <<= 8;
            val |= (int)bytes[i] & 0xFF;
        }
        return val;
    }

    /**
     * Called from SoundsContentProvider.removeSound() which is either after addSound.undo
     * or when the user actually deletes a custom sound (which can't be undone because of the
     * removal of the sound file here)
     * @param soundResource
     */
    public void deleteSoundFile(SoundResource soundResource) {
        mApplicationContext.deleteFile(soundResource.getUriPath());
    }

    @Override
    public boolean isSoundDeletionUndoable() {
        return true;
    }

    public boolean hasAdsConsentIntervalElapsed() {
        long consentRequested = mSettingsManager.getAdConsentRequestedTime();
        long now = System.currentTimeMillis();

        if (consentRequested == -1L) { // never before shown (except if settings redefault used)
            mSettingsManager.setAdConsentRequestedTime(now); // make it as though ad has just been shown so the first one will be in an hour
            AndroidResourceManager.logd(LOG_TAG, "hasAdsConsentIntervalElapsed: ad consent not requested before (or redefaulted)");
            return false;
        }

        long elapsed = now - consentRequested;
        long interval = BuildConfig.SHOW_REAL_ADS ? ONE_HOUR_MILLIS : FIVE_MINUTES_MILLIS;

        final boolean over = elapsed > interval;

        AndroidResourceManager.logd(LOG_TAG, String.format("hasAdsConsentIntervalElapsed: interval over=%s (elapsed=%s)", over, elapsed));

        return over;
    }
}
