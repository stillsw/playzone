package com.stillwindsoftware.keepyabeat.android;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;

import com.stillwindsoftware.keepyabeat.db.KybSQLiteHelper;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.utils.ImportRhythm;
import com.stillwindsoftware.keepyabeat.utils.RhythmImporter;
import com.stillwindsoftware.keepyabeat.utils.RhythmSharer;

/**
 * Checks for installation (by reading version of library from db/content
 * provider), if none, install. 
 */
public class DataLoaderFragment extends Fragment {
	
	private static final String SEED_RHYTHMS_DATA_FILE_NAME = "seedRhythms.kbr";

	public interface DataInstaller {
		void dbOnCreateTriggered();
		void dbOnCreateFinished();
		void dbOnUpdateTriggered();
		void dbOnUpdateFinished();
	}

	private static final String LOG_TAG = "KYB-DataLoaderFragment";

	// as these will (potentially) cause update to progress bar, give approx values to 100
	public static final int CHECKING_INSTALLATION = 10;
	public static final int INSTALLING = 20;
	public static final int LOADING_DATA = 60;
	public static final int COMPLETED = 100;

	/**
	 * Calling activity must implement this interface (type checked in onAttach())
	 */
	public interface ProgressListener {
		/**
		 * One of the constants for loading
		 * @param step
		 */
		void setLoadStep(int step);
	}
	
	private int mCurrentLoadStep;

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);
		
		try {
			// Keep this Fragment around even during config changes
			setRetainInstance(true);
			
		} catch (ClassCastException e) {
			e.printStackTrace();
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Set from the Task, but in the UI thread, so safe to just read it
	 * from that thread
	 * @return
	 */
	public int getCurrentLoadStep() {
		return mCurrentLoadStep;
	}

	/**
	 * Called from the LoadingTask only, during progress publish
	 */
	public void setCurrentLoadStep(int currentLoadStep) {
		this.mCurrentLoadStep = currentLoadStep;
		
		if (isResumed()) {
			Activity caller = getActivity();
			if (caller != null) { // doubly sure
				((ProgressListener) caller).setLoadStep(currentLoadStep);
			}
		}
	}

	/**
	 * Starts loading the data
	 */
	public void startLoading(KybApplication application) {
		AndroidResourceManager.logd(LOG_TAG, "startLoading, application context class = "+application.getClass().getSimpleName());

		new LoadingTask(this, application).execute();
	}

	private static class LoadingTask extends AsyncTask<Void, Integer, Integer> implements DataInstaller {

        private DataLoaderFragment mLoaderFragment;
		private KybApplication mApplication;
		private boolean mInstalling = false;
		
		public LoadingTask(DataLoaderFragment dataLoaderFragment, KybApplication application) {
			this.mLoaderFragment = dataLoaderFragment;
			this.mApplication = application;
			mApplication.setDataInstaller(this);
		}

		@Override
		protected Integer doInBackground(Void ... params) {
			// this should be the first init() call of the library (db)
			// it has already set itself to the resource manager on creation (by content providers init)
			((KybSQLiteHelper)mApplication.getResourceManager().getLibrary()).init();

            // flag is set up if the onCreate() is triggered, when all is completed
			// now ready to install seed rhythms
			if (mInstalling) {
				publishProgress(LOADING_DATA);
				seedRhythmsData();
			}
			
			// this is where data installer is cleared from the application
			mApplication.setInitialised(true);

			// currently ignoring the return value, and just returning completed in post execute
			return COMPLETED;
		}

		@Override
		protected void onPreExecute() {
			mLoaderFragment.setCurrentLoadStep(CHECKING_INSTALLATION);
		}

		@Override
		protected void onPostExecute(Integer result) {
			mLoaderFragment.setCurrentLoadStep(COMPLETED);
		}

		@Override
		protected void onProgressUpdate(Integer ... values) {
			mLoaderFragment.setCurrentLoadStep(values[0]); 
		}

		//------------ DataInstaller methods
		
		@Override
		public void dbOnCreateTriggered() {
			mInstalling = true;
			publishProgress(INSTALLING);
		}

		@Override
		public void dbOnCreateFinished() {
			// database is created, progress is currently installing
			AndroidResourceManager.logd(LOG_TAG, "dbOnCreateFinished: ");
		}

		@Override
		public void dbOnUpdateTriggered() {
			publishProgress(CHECKING_INSTALLATION);			
		}

		@Override
		public void dbOnUpdateFinished() {
			// no need to publish progress here, it's part of doInBackground() anyway
			AndroidResourceManager.logd(LOG_TAG, "dbOnUpdateFinished: ");
		}

		/**
		 * Opens the default rhythms xml, imports them to the database
		 * Called from KybSQLiteHelper (the library) in its onCreate()
		 * Any problems are reported into the library xml loader, which will
		 * be read by the interested activity in onResume() (see PlayRhythmsActivity)
		 */
		public void seedRhythmsData() {

			AndroidResourceManager resourceManager = mApplication.getResourceManager();

			// need a loader for the duration
			LibraryXmlLoader libraryXmlLoader = new LibraryXmlLoader();
			resourceManager.setLibraryXmlLoader(libraryXmlLoader);
			
			try {
				InputStream is = mApplication.getResources().getAssets().open(SEED_RHYTHMS_DATA_FILE_NAME);

				int countSelected = 0;
				RhythmSharer sharer = null;
				
				try {
					// attempt to load the file
					sharer = new RhythmSharer(resourceManager, is);
					if (!sharer.isAbortiveRead(true)) { // batch mode
						CoreLocalisation.Key warnKey = sharer.getWarning();
						if (warnKey != null) {
							AndroidResourceManager.loge(LOG_TAG, String.format("AndroidResourceManager.seedRhythmsData: warning from sharer %s", warnKey));
							// not insisting the data file be updated every time there's a library update
							if (warnKey != CoreLocalisation.Key.VERSION_HIGHER_IMPORT_FILE_ERROR) {
								// must be the some failures error
								libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
										CoreLocalisation.Key.SOME_INVALID_IMPORT_FILE_ERROR_BATCH_MODE);
							}
						}
					}
				
					// check something is marked as selected (should actually be all of them as the library is empty)
					ArrayList<ImportRhythm> impRhythms = sharer.getRhythmsFromInput();
					for (int i = 0; i < impRhythms.size(); i++) {
						if (impRhythms.get(i).isSelected()) {
							countSelected++;
						}
					}
					
					// output a message if something isn't selected
					if (countSelected != impRhythms.size()) {
						AndroidResourceManager.loge(LOG_TAG, String.format("AndroidResourceManager.seedRhythmsData: import rhythms count(%s) but only selected %s"
								, impRhythms.size(), countSelected));
						libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
								CoreLocalisation.Key.SOME_INVALID_IMPORT_FILE_ERROR_BATCH_MODE);
					}

				} 
				catch (Exception e) {
					AndroidResourceManager.loge(LOG_TAG, String.format("AndroidResourceManager.seedRhythmsData: exception from sharer %s", e));
					libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
							CoreLocalisation.Key.UNREADABLE_INSTALL_RHYTHMS_DATA_ERROR);
				}
				
				// provided have something, import it
				if (countSelected > 0) {
					RhythmImporter importer = new RhythmImporter(resourceManager, sharer);
					try {
						resourceManager.startTransaction();
						importer.importRhythmsAndReferences();
						resourceManager.saveTransaction();
					} catch (Exception e) {
						resourceManager.rollbackTransaction();
						AndroidResourceManager.loge(LOG_TAG, String.format("AndroidResourceManager.seedRhythmsData: exception from importer %s", e.getMessage()));
						libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
								CoreLocalisation.Key.UNREADABLE_INSTALL_RHYTHMS_DATA_ERROR);
					}
					finally {
						String messages = importer.getErrorMessagesAndClearImportData();

						// and show the errors
						if (messages != null) {
							libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
									CoreLocalisation.Key.INSTALL_DEFAULT_RHYTHMS_DATA_WARNINGS_TITLE, messages);
						}
					}
				}
			}
//			else {
			catch (IOException e) {
				AndroidResourceManager.loge(LOG_TAG, String.format("seedRhythmsData: no rhythms data file %s"
						, SEED_RHYTHMS_DATA_FILE_NAME), e);
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.UNREADABLE_INSTALL_RHYTHMS_DATA_ERROR);				
			}
		}

	}

}