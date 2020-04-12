package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.control.PendingTasksScheduler;
import com.stillwindsoftware.keepyabeat.data.DataFolder;
import com.stillwindsoftware.keepyabeat.model.BeatTypeXmlImpl;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.LibraryXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.model.transactions.ListenerSupport;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader;
import com.stillwindsoftware.keepyabeat.platform.ColourManager;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.LibraryLoadCallback;
import com.stillwindsoftware.keepyabeat.platform.PersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.RhythmEditor;
import com.stillwindsoftware.keepyabeat.platform.twl.openal.SoundStore;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;
import com.stillwindsoftware.keepyabeat.utils.ImportRhythm;
import com.stillwindsoftware.keepyabeat.utils.RhythmImporter;
import com.stillwindsoftware.keepyabeat.utils.RhythmSharer;


public class TwlResourceManager extends PlatformResourceManager {

	private static final String KYB_HOME = ".keepYaBeat";
	private static final String DATA_DIR = "data";
	
	private static final String XML_FILE_NAME = "library.kbl";
	private static final int MAX_UNDO_HISTORY = 100; // 100 each of general and rhythm edits, should be plenty
	private static final String SEED_RHYTHMS_DATA_FILE_NAME = "seedRhythms.kbr";

	// keep the directory and file of the main library for all new file operations
	private File kybHome;
	private File dir; 
	private File libraryXmlFile; 
	private ColourManager colourManager;
	private PersistentStatesManager persistentStatesManager;
	private SoundStore soundStore;
	private ResourceBundle messagesBundle;	

	// java default logging
	private Logger mainLogger, verboseLogger; // see initLogging()
	private boolean loggingEnabled;
	private FileHandler logFileHandler, verboseLogFileHandler;
	private HashMap<String, Logger> loggersSet;
	private LOG_TYPE minLogLevel; // reset in settings dialog

	protected static TwlResourceManager instance;

	public static TwlResourceManager getInstance() {
		return instance;
	}
	
	public static TwlResourceManager setNewInstance(LibraryXmlLoader libraryXmlLoader) {
		TwlResourceManager.instance = new TwlResourceManager(libraryXmlLoader);
		return instance;
	}

	private TwlResourceManager(LibraryXmlLoader libraryXmlLoader) {
		super(libraryXmlLoader);
		
		// android creates image store on the fly because it may have to destroy it and
		// recreate
		rhythmDraughtImageStore = new RhythmDraughtImageStore();
		imageStoreState = ImageStoreState.READY;
		
		// attempt to load the default locale and messages bundle
		try {
			messagesBundle = ResourceBundle.getBundle("com.stillwindsoftware.keepyabeat.messagesBundle");
		} catch (MissingResourceException e) {
			log(LOG_TYPE.error, this, "TwlResourceManager.<init>: failed to find messages bundle, no messages will be localised");
			e.printStackTrace();
		}
		
		// single instance setter, was a singleton... in case of unexpected errors surround with block
		try {
			setPendingTasksScheduler();
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Tests for a user-selected language stored in the persistent state, if so, and different from the default locale (loaded at startup)
	 * switches it out. Called from KeepYaBeat after the library has been loaded (so safe to load the persistent states manager.
	 */
	public void switchLanguageIfUserSelection() {
		String savedLang = ((TwlPersistentStatesManager)getPersistentStatesManager()).getLanguage();
		if (savedLang != null) {
			switchLanguage(savedLang);
		}
	}
	
	/**
	 * Called from the TwlPersistentStatesManager
	 * when resetting to default values, and after the user chooses another language option on the welcome dialog via 
	 * setLanguage() in TwlPersistentStatesManager 
	 * @param languageCode
	 */
	public void switchLanguage(String languageCode) {
		try {
			
			if (languageCode != null) {
				ResourceBundle newMessagesBundle = ResourceBundle.getBundle("com.stillwindsoftware.keepyabeat.messagesBundle", new Locale(languageCode));
				// no failures
				messagesBundle = newMessagesBundle;
			}
			else {
				ResourceBundle newMessagesBundle = ResourceBundle.getBundle("com.stillwindsoftware.keepyabeat.messagesBundle");
				// no failures
				messagesBundle = newMessagesBundle;
			}
			
			// change the beat types and sounds (set their names)
			for (SoundXmlImpl sound : ((SoundsXmlImpl)library.getSounds()).getInternalSounds()) {
				CoreLocalisation.Key localisedKey = SoundsXmlImpl.InternalSound.valueOf(sound.getKey()).getLocalisedKey();
				sound.setName(Function.getBlankContext(this), getLocalisedString(localisedKey));
			}
			
			BeatTypesXmlImpl beatTypes = (BeatTypesXmlImpl) library.getBeatTypes();
			for (int i = 0; i < beatTypes.getSize(); i++) {
				BeatTypeXmlImpl beatType = beatTypes.get(i);
				if (beatTypes.isDefaultBeatType(beatType)) {
					CoreLocalisation.Key localisedKey = BeatTypes.DefaultBeatTypes.valueOf(beatType.getKey()).getLocalisedKey();
					beatType.setName(Function.getBlankContext(this), getLocalisedString(localisedKey));
				}
			}
			
		} catch (MissingResourceException e) {
			log(LOG_TYPE.error, this, String.format("TwlResourceManager.switchLanguage: failed to find messages bundle for '%s'"
					, languageCode == null ? "default" : languageCode));
			e.printStackTrace();
		}	
	}
	
	@Override
	public void initSounds() {
		soundStore = new SoundStore();
		soundStore.init();
	}

	public SoundStore getSoundStore() {
		return soundStore;
	}

	/**
	 * Called during superclass initialisation
	 */
	@Override
	public void initLogging() {
		// ensure have kybhome set up
		openKeepYaBeatHome();
		
		loggingEnabled = false; // set again when successfully completed
		
		LogManager logManager = LogManager.getLogManager();
        logManager.reset();
        
		mainLogger = Logger.getLogger("");
		verboseLogger = Logger.getLogger("justverbose");
		
		// make a set to ensure loggers are not GC'd after instantiation
		loggersSet = new HashMap<String, Logger>();
		
		// set the level on the logger to begin with (it will be reset as soon as persistentStatesManager starts up
		// see getPersistentStatesManager below)
		setMinLogLevel(LOG_TYPE.debug);
		
		// file logging to the kyb folder under user.home with 3 rotating logs each max 100kb and append until full
		// overrides the publish method to not include verbose output (which goes to the other log instead)
		try {
			logFileHandler = new FileHandler("%h/"+KYB_HOME+"/kybLog_%g.txt", 100000, 3, true) {

				@Override
				public synchronized void publish(LogRecord record) {
					if (record.getLevel() == Level.FINER || record.getLevel() == Level.FINEST) {
						// do nothing
					}
					else {
						super.publish(record);
					}
				}				
			};
			
			logFileHandler.setFormatter(new LoggingFormatter(false)); // don't include verbose
			mainLogger.addHandler(logFileHandler);
			
			verboseLogFileHandler = new FileHandler("%h/"+KYB_HOME+"/kybVerboseLog_%g.txt", 100000, 3, true);
			verboseLogFileHandler.setFormatter(new LoggingFormatter(true)); // do include verbose
			verboseLogger.addHandler(verboseLogFileHandler);
			
			// get all system out/err (incl stack traces) to log
			redirectSystemOutToLogger();
			
			// ok to use the logger for output
			loggingEnabled = true;
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Called from initLogging
	 * Code and classes borrowed from this blog : https://blogs.oracle.com/nickstephen/entry/java_redirecting_system_out_and
	 */
	private void redirectSystemOutToLogger() {
    	// preserve old stdout/stderr streams in case they might be useful
//    	stdout = System.out;
//    	stderr = System.err;
    	
    	// now rebind stdout/stderr to logger
    	Logger logger = Logger.getLogger("stdout");
    	LoggingOutputStream los = new LoggingOutputStream(logger, StdOutErrLevel.STDOUT, null);

    	System.setOut(new PrintStream(los, true));
    	
    	logger = Logger.getLogger("stderr");
    	los = new LoggingOutputStream(logger, StdOutErrLevel.STDERR, System.err);
    	System.setErr(new PrintStream(los, true));

	}
	
	@Override
	public void closeLogging() {
		if (loggingEnabled) {
			logFileHandler.close();
			verboseLogFileHandler.close();
		}		
	}

	/**
	 * Handles copying internal sound files from the file system during development but 
	 * also from jar in deployment
	 * @param fileName
	 * @param soundFile
	 */
	private void copyInternalSoundFromInstallation(String fileName, File soundFile) {
		try {
			InputStream is = DataFolder.class.getResourceAsStream(fileName);
			OutputStream out = new FileOutputStream(soundFile, false);
			byte[] buf = new byte[1024];
	        int len;
	        while ((len = is.read(buf)) > 0) {
	            out.write(buf, 0, len);
	        }
		    is.close();
		    out.close(); 
	
		} catch (Exception e) {
			this.log(LOG_TYPE.error, this, 
					String.format("TwlResourceManager.copyInternalSoundFromInstallation: can't copy installation file %s \n%s %s"
						,fileName, e.getClass().getSimpleName(), e.getMessage()));
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
					CoreLocalisation.Key.UNREADABLE_INSTALL_DATA_ERROR, fileName);
			e.printStackTrace();
		}
	}
	
	/**
	 * Called during seeding of library, for TWL it attempts to find a data file of rhythms
	 * and if there, it loads it to the RhythmSharer and then RhythmImporter
	 */
	public void seedRhythmsData() {
		InputStream is = DataFolder.class.getResourceAsStream(SEED_RHYTHMS_DATA_FILE_NAME);
		if (is != null) {
			int countSelected = 0;
			RhythmSharer sharer = null;
			
			try {
				// attempt to load the file
				sharer = new RhythmSharer(TwlResourceManager.getInstance(), is);
				if (!sharer.isAbortiveRead(true)) { // batch mode
					CoreLocalisation.Key warnKey = sharer.getWarning();
					if (warnKey != null) {
						log(LOG_TYPE.error, this, String.format("TwlResourceManager.seedRhythmsData: warning from sharer %s", warnKey));
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
					log(LOG_TYPE.error, this, String.format("TwlResourceManager.seedRhythmsData: import rhythms count(%s) but only selected %s"
							, impRhythms.size(), countSelected));
					libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
							CoreLocalisation.Key.SOME_INVALID_IMPORT_FILE_ERROR_BATCH_MODE);
				}

			} 
			catch (Exception e) {
				log(LOG_TYPE.error, this, String.format("TwlResourceManager.seedRhythmsData: exception from sharer %s", e.getClass()));
				e.printStackTrace();
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.UNREADABLE_INSTALL_RHYTHMS_DATA_ERROR);
				e.printStackTrace();
			}
			
			// provided have something, import it
			if (countSelected > 0) {
				RhythmImporter importer = new RhythmImporter(this, sharer);
				try {
					importer.importRhythmsAndReferences();					
				} catch (Exception e) {
					log(LOG_TYPE.error, this, String.format("TwlResourceManager.seedRhythmsData: exception from importer %s", e.getClass()));
					e.printStackTrace();
					libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
							CoreLocalisation.Key.UNREADABLE_INSTALL_RHYTHMS_DATA_ERROR);
					e.printStackTrace();
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
		else {
			log(LOG_TYPE.error, this, String.format("TwlResourceManager.seedRhythmsData: no rhythms data file %s", SEED_RHYTHMS_DATA_FILE_NAME));
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
				CoreLocalisation.Key.MISSING_INSTALL_RHYTHMS_DATA_ERROR);
		}
	}

	protected boolean isLegalSoundForPlatform(SoundResourceType resourceType, String fileName) {
		if (resourceType == SoundResourceType.INT) {
			return true;
		}
		else {
			return soundStore.isLegalFileExtension(fileName);
		}
	}
	
	@Override
	public SoundResource getSoundResourceFromName(SoundResourceType resourceType, String fileName, float duration, boolean loadSoundFile, boolean isLegal) {

		File soundFile = null;
		TwlSoundResource soundResource = new TwlSoundResource();
		soundResource.setType(resourceType);

		if (!fileName.equals(LibraryXmlLoader.EMPTY_STRING)) {
			if (resourceType.equals(SoundResourceType.INT)) {
				// add the .wav suffix
				fileName += "."+SoundStore.WAV_FILES;
				soundFile = new File(dir, fileName);
				
				// always copy from the jar, allows new versions of sounds to supercede others
				log(LOG_TYPE.debug, this, 
						String.format("TwlResourceManager.getSoundResourceFromName: internal sound file adding from jar %s", fileName));
				boolean canCopy = true;
				if (soundFile.exists()) {
					if (soundFile.canWrite()) {
						// remove it to overwrite
						soundFile.delete();
					}
					else {
						// file is not deleteable
						log(LOG_TYPE.error, this, 
								String.format("TwlResourceManager.getSoundResourceFromName: internal sound cant be overwritten %s", fileName));
						canCopy = false;
					}
				}
				
				// always copy from the jar, allows new versions of sounds to supercede others
				// don't error check here, if it's still missing it will drop through and be caught in the next part
				if (canCopy) {
					copyInternalSoundFromInstallation(fileName, soundFile);
				}
			}
			else if (isLegal) {
				// non-INT files have full uri path name
				try {
					URI uri = new URI(fileName);
					soundFile = new File(uri);
					
				} catch (Exception e) {
					// keep the filename on the resource so it will be preserved in the library write to xml
					soundResource.setUriPath(fileName);
					this.log(LOG_TYPE.error, this, 
							String.format("TwlResourceManager.getSoundResourceFromName: Data incomplete, invalid URI loading Sound file for %s", fileName));
//					e.printStackTrace();
				}
			}
			else {
				// is not legal, ie. not supported on this platform 
				soundResource.setUriPath(fileName);
				this.log(LOG_TYPE.error, this, 
						String.format("TwlResourceManager.getSoundResourceFromName: Data incomplete, illegal Sound file for this platform %s", fileName));
				soundResource.setLoadErrorLocalisedKey(SoundResourceLoadError.UNREACHABLE_TERMINAL, // use this format to provide error to rhythm importer
						CoreLocalisation.Key.ILLEGAL_SOUND_FILE_LIBRARY_ERROR, fileName);
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.ILLEGAL_SOUND_FILE_LIBRARY_ERROR, fileName);
			}
			
			// create the resource with a file, whether valid and load-able or not
			if (soundFile != null) {
				soundResource.setSoundFile(soundFile);
			}
			
			if (soundFile != null && soundFile.exists()) {
				if (loadSoundFile) {
					TestedSoundFile testedFile = soundStore.getTestedSoundFile(soundFile, duration);
					if (testedFile.isValid()) {
						soundResource.setAudioEffect(testedFile.getAudio());
					}
					else {
						this.log(LOG_TYPE.error, this, 
								String.format("TwlResourceManager.getSoundResourceFromName: Data incomplete, error loading Sound file for %s [reason: %s]"
										, fileName, testedFile.getInvalidReason()));
						soundResource.setLoadError(SoundResourceLoadError.CORRUPTED_TERMINAL);
						libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
								CoreLocalisation.Key.MISSING_SOUND_FILE_LIBRARY_ERROR, soundFile.getAbsolutePath());
					}
				}
			}
			else if (soundResource.getLoadError() == null) { // could have been previously set already (see illegal sound above)
				this.log(LOG_TYPE.error, this, 
						String.format("TwlResourceManager.getSoundResourceFromName: Data incomplete, error loading Sound file for %s", fileName));
				soundResource.setLoadError(SoundResourceLoadError.UNREACHABLE_TERMINAL);
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.MISSING_SOUND_FILE_LIBRARY_ERROR, (soundFile == null ? fileName : soundFile.getAbsolutePath()));
			}			
		}
		else {
			// empty string
			this.log(LOG_TYPE.error, this, "TwlResourceManager.getSoundResourceFromName: Data incomplete, error missing Sound file name");
			soundResource.setLoadError(SoundResourceLoadError.UNREACHABLE_TERMINAL);
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
					CoreLocalisation.Key.MISSING_SOUND_FILE_LIBRARY_ERROR, getLocalisedString(TwlLocalisationKeys.ERROR_NO_FILE_NAME));
		}
		
		return soundResource;
	}

	/**
	 * check for file existing in the sounds library loaded from file store (data directory)
	 * @param file
	 * @return
	 */
	@Override
	public boolean isSoundFileInLibrary(File testFile) {
		SoundsXmlImpl sounds = (SoundsXmlImpl) library.getSounds();
		
		for (int i = 0; i < sounds.getSize(); i++) {
			TwlSoundResource twlSoundResource = (TwlSoundResource) sounds.get(i).getSoundResource();
			if (twlSoundResource != null && twlSoundResource.isSameResource(testFile)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Load the main (default) library which is to be used by the application
	 */
	public void loadXmlLibrary(LibraryLoadCallback callback) {
		// the application launcher should have checked before calling that the library exists
		// if it doesn't something is wrong
		if (libraryXmlFile == null) {
			this.log(LOG_TYPE.error, this, "TwlResourceManager.loadXmlLibrary: library xml file not initialised");
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT, 
					CoreLocalisation.Key.NOT_INIT_LIBRARY_ERROR);
		}
		
		if (!libraryXmlFile.exists()) {
			this.log(LOG_TYPE.error, this, "TwlResourceManager.loadXmlLibrary: library xml file missing, should seed new library");
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT, 
					CoreLocalisation.Key.MISSING_LIBRARY_ERROR);
		}
		
		if (dir.isDirectory() && dir.canRead()) {
			if (!dir.canWrite()) {
				((LibraryXmlImpl)library).setLibraryReadOnlyMode(true);
				this.log(LOG_TYPE.warning, this, "TwlResourceManager.loadXmlLibrary: data directory is read-only, no changes will be saved");
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_INFORMATION, 
						CoreLocalisation.Key.READ_ONLY_FOLDER_ERROR);
			}
			
			if (!libraryXmlFile.canWrite()) {
				((LibraryXmlImpl)library).setLibraryReadOnlyMode(true);
				this.log(LOG_TYPE.warning, this, "TwlResourceManager.loadXmlLibrary: library xml file is read-only, no changes will be saved");
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_INFORMATION, 
						CoreLocalisation.Key.READ_ONLY_LIBRARY_ERROR);
			}

			// load what's there, this starts a new thread to do the loading in the background
			loadXmlLibrary(callback, (LibraryXmlImpl) library, libraryXmlFile.toURI());
			
//			// getting the manager also reads the states
//			getPersistentStatesManager();
		}
		else if (dir.isDirectory()) {
			this.log(LOG_TYPE.error, this, "TwlResourceManager.loadXmlLibrary: Data directory cannot be read");
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT, 
					CoreLocalisation.Key.UNREADABLE_FOLDER_ERROR);
		}
		else {
			this.log(LOG_TYPE.error, this, "TwlResourceManager.loadXmlLibrary: Data directory is missing");
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT, 
					CoreLocalisation.Key.MISSING_FOLDER_ERROR);
		}
	}

	/**
	 * Load a library from any source
	 * @param uri
	 * @return
	 */
	public void loadXmlLibrary(final LibraryLoadCallback callback, final LibraryXmlImpl library, final URI uri) {
		
		// sound set up
		if (soundStore.soundWorks()) {
			// load the library in a separate thread
			new Thread(
				new Runnable() {
					public void run() {
						try {
							libraryXmlLoader.populateFromXml(TwlResourceManager.this, library, uri);
	
							// should be complete so check all the sounds that should be there are there
							library.testDefaultSoundsExist();
							library.testForErrorsInUsedSounds();
												
						}
						catch (Throwable e) {
							try {
								// throws error, so catch it
								
								e.printStackTrace();
								libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT, 
										CoreLocalisation.Key.CORRUPT_LIBRARY_ERROR, e);
							} catch (Exception e1) {}
	
						}
						
						// report results back to caller
						if (libraryXmlLoader.getLoadProblemSeverity() == LibraryXmlLoader.ERROR_SEVERITY_NONE) {
							library.setLoadedNoProblems(true);
							callback.loadCompletedOk(library);
						}
						else {
							callback.loadFailed(libraryXmlLoader);
						}
					}
				}
			).start();
		}
		else {
			try {
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT,
						CoreLocalisation.Key.NOT_INIT_SOUNDS_ERROR);
			} catch (RuntimeException e) { // thrown because of abort error
				callback.loadFailed(libraryXmlLoader);
			}			
		}
	}
	
	public void seedNewLibrary(final LibraryLoadCallback callback, final LibraryXmlImpl library) {
		
		// sound set up
		if (soundStore.soundWorks()) {
			// seed the library in a separate thread as it needs to read the file system and load the sound files
			new Thread(
				new Runnable() {
					public void run() {
						try {
							library.initialiseAsEmptyLibrary(TwlResourceManager.this);
							seedRhythmsData();
						}
						catch (Throwable e) {
							try {
								// throws error, so catch it
								e.printStackTrace();
								libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT, 
										CoreLocalisation.Key.CORRUPT_LIBRARY_ERROR, e);
							} catch (Exception e1) {}
	
						}
						
						// getting the manager also reads the states
						getPersistentStatesManager();
						
						// report results back to caller
						if (libraryXmlLoader.getLoadProblemSeverity() == LibraryXmlLoader.ERROR_SEVERITY_NONE) {
							callback.loadCompletedOk(library);
						}
						else {
							callback.loadFailed(libraryXmlLoader);
						}
					}
				}
			).start();
		}
		else {
			try {
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_ABORT,
						CoreLocalisation.Key.NOT_INIT_SOUNDS_ERROR);
			} catch (RuntimeException e) { // thrown because of abort error
				callback.loadFailed(libraryXmlLoader);
			}			
		}
	}

	/**
	 * A bug has happened on windows where FileNotFoundException is thrown when saving the file with a message
	 * "The requested operation cannot be performed on a file with a user-mapped section open"
	 * It appears to be when >1 changes to the file happen in succession, but before some resource has cleared down...
	 * perhaps as a result of double clicking and causing >1 changes quickly (I could not duplicate it on a fast linux box).
	 * To prevent this escalating to the user (unless unavoidable) the following control is implemented:
	 * 1) allow the write to make >1 attempts to successfully complete (up to max attempts = 5)
	 * 2) prevent new writes happening until the previous one has completed
	 * 3) if >1 new writes are requested, they queue and therefore can piggy-back (ie. one write will handle all of them)
	 * 4) if > max attempts is reached without success, all writes are abandoned and user gets feedback
	 */
	private static final int MAX_WRITE_ATTEMPTS = 5;
	private static final String WRITE_ATTEMPTS_LOCK_OBJECT = "write locker";
	private static final String QUEUE_WRITES_LOCK_OBJECT = "queue locker";
	private volatile boolean writeIsQueued = false;
	
	@Override
	public void saveToStore() {
		// clear down temp/backup files after successfully writing
		if (!((LibraryXmlImpl)library).isReadOnlyMode()) {
			
			// set the flag that a process wants to write the file (lock on test and set)
			synchronized (QUEUE_WRITES_LOCK_OBJECT) {
				// already have a write waiting, don't bother to try another, exit method
				if (writeIsQueued) {
					log(LOG_TYPE.info, TwlResourceManager.this, "TwlResourceManager.saveToStore: write piggy-backed, exit method");
					return;
				}
				else {
					writeIsQueued = true;
				}
			}
	
			new Thread(
				new Runnable() {
					public void run() {		
						// only 1 thread may attempt to write at a time
						synchronized (WRITE_ATTEMPTS_LOCK_OBJECT) {
							
							// since this thread is now in the exclusively locked code that attempts to write
							// it can release the queue here, if another process requeues it will wait to enter
							// here until this process completes
							
							// ** there is a small window between a process setting the queue to true and getting to this point
							// (involves a new thread starting and running) during this time other attempts to write would abort
							// because they would hit the writeIsQueued == true condition above
							// BUT if 2 processes run very close together and one is about to test writeIsQueued above and the other
							// is right here now about to lock on that, if one locks the other, 2 possible scenarios (depends which
							// executes the synchronised code first)
							// 1) this process unsets writeIsQueued and then the other process sets it
							//		a) this process will continue to do its writes
							//		b) the other process will reach this block where it will wait until it can continue
							// 2) the other process finds a queue is set and returns without continuing
							//		a) perfect, since this process hasn't yet written the 2 write requests can piggy back on each other
							synchronized (QUEUE_WRITES_LOCK_OBJECT) {
								writeIsQueued = false;
							}							

							// save the rhythm editor state
							RhythmEditor rhythmEditor = getGuiManager().getRhythmEditor();
							if (rhythmEditor != null && persistentStatesManager != null) {
								((TwlPersistentStatesManager) persistentStatesManager)
									.setEditRhythm(rhythmEditor.getEditRhythm());
							}							

							// loop to attempt to write up to max attempts
							for (int writeAttempts = 0; writeAttempts < MAX_WRITE_ATTEMPTS; writeAttempts++) {
								try {
									log(LOG_TYPE.info, TwlResourceManager.this, "TwlResourceManager.saveToStore: saving xml to file");
									writeXml();
									// no error encountered, finish by breaking out of the loop
									break;
								} catch (Exception e) {
									log(LOG_TYPE.error, TwlResourceManager.this, 
											String.format("TwlResourceManager.saveToStore: Failed to write (attempt %s) library to xml file: %s", writeAttempts, e));
									e.printStackTrace();
									// report to the user if already tried and failed up to the max attempts
									if (writeAttempts == MAX_WRITE_ATTEMPTS -1) {
										getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_SAVE_ERROR, true, null);
									}
									else {
										// sleep a short time to allow resources to be released before retrying
										try {
											Thread.sleep(250);
										} catch (InterruptedException e1) {}
									}
								}
								
							}
						}
					}
				}
			).start();
		}
	}
	
    private void writeXml() throws IOException {
    	Document document = libraryXmlLoader.makeXml((LibraryXmlImpl) library);
        Writer w = new OutputStreamWriter(new FileOutputStream(libraryXmlFile), "UTF8");
        new XMLOutputter(Format.getPrettyFormat()).output(document, w);
        w.flush();
    }

    /**
     * Attempt to open the kyb home directory under user home, returns true if it succeeds.
     * It also creates the directory if needed.
     * @return
     */
    private boolean openKeepYaBeatHome() {
		// library is written under home directory, initialise that first
		File home = new File(System.getProperty("user.home"));
		if (home.isDirectory() && home.canRead() && home.canWrite()) {
			
			// open or create kybHome
			kybHome = new File(home, KYB_HOME);
			if (!kybHome.exists()) {
				if (!kybHome.mkdir()) {
					this.log(LOG_TYPE.error, this, "TwlResourceManager.openKeepYaBeatHome: KYBHome directory cannot be created");
					libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
							CoreLocalisation.Key.UNWRITEABLE_HOME_ERROR, System.getProperty("user.home")
								+System.getProperty("file.separator")+KYB_HOME);
				}
			}
			
			// kybHome is good to go
			if (kybHome.isDirectory() && kybHome.canRead() && kybHome.canWrite()) {
				return true;
			}
			else {
				this.log(LOG_TYPE.error, this, "TwlResourceManager.openKeepYaBeatHome: KYBHome is not a read/write able directory");
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.UNWRITEABLE_HOME_ERROR, System.getProperty("user.home")
							+System.getProperty("file.separator")+KYB_HOME);
			}
		}
		else {
			this.log(LOG_TYPE.error, this, "TwlResourceManager.openKeepYaBeatHome: Home directory is missing or can't be read/written");
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
					CoreLocalisation.Key.UNWRITEABLE_HOME_ERROR, System.getProperty("user.home"));
		}

		// dropped through, if was opened ok it would have returned from that one place with true
		return false;    
    }

    /**
     * look for data directory, if not there create it and copy over data files from installation
     * @return
     */
    private boolean openDataDirectory() {
		dir = new File(kybHome, DATA_DIR);
		if (!dir.exists()) {
			if (!dir.mkdir()) {
				this.log(LOG_TYPE.error, this, "TwlResourceManager.openDataDirectory: Data directory cannot be created");
				libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
						CoreLocalisation.Key.UNWRITEABLE_DATA_ERROR, System.getProperty("user.home")
						+System.getProperty("file.separator")+KYB_HOME
						+System.getProperty("file.separator")+DATA_DIR);
			}
		}
		
		if (dir.isDirectory() && dir.canRead() && dir.canWrite()) {
			return true;
		}
		else {
			this.log(LOG_TYPE.error, this, "TwlResourceManager.openDataDirectory: Data directory is missing or can't be read/written");
			libraryXmlLoader.reportLoadError(LibraryXmlLoader.ERROR_SEVERITY_UNFORTUNATE, 
					CoreLocalisation.Key.UNWRITEABLE_DATA_ERROR, System.getProperty("user.home")
					+System.getProperty("file.separator")+KYB_HOME
					+System.getProperty("file.separator")+DATA_DIR);
		}

		// dropped through, if was opened ok it would have returned from that one place with true
		return false;    
    }

    @SuppressWarnings("resource")
	public static final void copyFile(File source, File destination) throws IOException {
        FileChannel sourceChannel = new FileInputStream( source ).getChannel();
		FileChannel targetChannel = new FileOutputStream( destination ).getChannel();
        sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
        sourceChannel.close();
        targetChannel.close();
      }
    
	public boolean isLibraryExists() {

		// open home
		if (!openKeepYaBeatHome()) {
			// failed to open home, errors already reported in that method
			((LibraryXmlImpl)library).setLibraryReadOnlyMode(true);
			return false;
		}
		
		// look for data directory, if not there create it and copy over data files from installation
		if (dir == null) {
			if (!openDataDirectory()) {
				((LibraryXmlImpl)library).setLibraryReadOnlyMode(true);
				return false;
			}
		}

		libraryXmlFile = new File(dir, XML_FILE_NAME);
		return libraryXmlFile.exists();
	}

	@Override
	public ColourManager getColourManager() {
		if (colourManager == null)
			colourManager = new TwlColourManager();
		
		return colourManager;
	}

	@Override
	public PersistentStatesManager getPersistentStatesManager() {
		if (persistentStatesManager == null) {
			persistentStatesManager = new TwlPersistentStatesManager(library);
			if (persistentStatesManager.isEnableDebugging()) {
				setMinLogLevel(DEBUG_LOGGING_LEVEL);
			}
			else {
				setMinLogLevel(NORMAL_LOGGING_LEVEL);
			}
		}
		
		return persistentStatesManager;
	}

	@Override
	public Library getLibrary() {
		return library;
	}

	@Override
	public Sound convertToSound(ProposedSoundResource proposedSoundResource, boolean loadSoundFile) throws Exception {
		TestedSoundFile testedFile = (TestedSoundFile) proposedSoundResource;
		if (testedFile == null || !testedFile.isValid())
			return null;
		
		return soundStore.convertSoundFile(testedFile, (LibraryXmlImpl)library);
	}

	@Override
	public GuiManager getGuiManager() {
		return TwlGuiManager.getInstance();
	}

	@Override
	public void log(LOG_TYPE type, Object origin, String message) {
		// set up for logging failure, try output to STDOUT
		if (!loggingEnabled) {
			System.out.println(message);
			return;
		}
		
		// get the logger from the class that originates it if there is one
		Logger logger = null;
		if (origin == null) {
			logger = mainLogger;
		}
		else {
			// get the logger that is held for the class, if none, create and store it
			// recommended to keep a strong reference for loggers to avoid GC
			String className = origin.getClass().getName();
			logger = loggersSet.get(className);
			if (logger == null) {
				logger = Logger.getLogger(className);
				loggersSet.put(className, logger);
			}
		}

		// translate to logger types
		switch (type) {
		case error : logger.severe(message); break;
		case warning : logger.warning(message); break;
		case info : logger.info(message); break;
		case debug : logger.fine(message); break;
		case verbose : verboseLogger.finer(message); break; // verbose always to that logger
		}
	}
	
	@Override
	public boolean isLogging(LOG_TYPE type) {
		return minLogLevel.compareTo(type) <= 0;
	}

	/**
	 * Called by persistent states manager at startup, and after settings dialog changes it
	 * @param minLogLevel
	 */
	@Override
	public void setMinLogLevel(LOG_TYPE minLogLevel) {
		this.minLogLevel = minLogLevel;
		
		// translate enum to logger levels
		Level loggerLevel = null;
		switch (minLogLevel) {
		case error : loggerLevel = Level.SEVERE; break;
		case warning : loggerLevel = Level.WARNING; break;
		case info : loggerLevel = Level.INFO; break;
		case debug : loggerLevel = Level.FINE; break;
		case verbose : loggerLevel = Level.FINEST; break;
		default : loggerLevel = Level.ALL;
		}
		
		// set the level on all the loggers
		mainLogger.setLevel(loggerLevel);
		verboseLogger.setLevel(loggerLevel);
		for (Logger logger : loggersSet.values()) {
			logger.setLevel(loggerLevel);
		}
	}

	/**
	 * Twl uses lookup from properties file backed message bundle, so simply call the 
	 * following method with the key name
	 */
	@Override
	public String getLocalisedString(CoreLocalisation.Key key) {
		return getLocalisedString(key.name());
	}

	/**
	 * Get a localised string from a key. Only for TWL where a string key is used to lookup
	 * from the messages bundle. Android uses auto-generated symbolic ints, and doesn't need lookup
	 * via the resource manager, except for the previous method to get them from Core ids 
	 */
	public String getLocalisedString(String key) {

		// try to lookup the key
		String localString = null;

		// try to lookup the key
		if (messagesBundle != null) {
			try {
				localString = messagesBundle.getString(key);
			} catch (MissingResourceException e) {
				log(LOG_TYPE.error, this, "TwlResourceManager.getLocalisedString: failed to find message in any resource for key="
						+ key);
				e.printStackTrace();
			}
		}
		
		try {
			if (localString != null) {
				// properties file is the wrong encoding for spanish chars
				return new String(localString.getBytes("ISO-8859-1"), "UTF-8");
			}
		}
		catch (UnsupportedEncodingException e) {
			log(LOG_TYPE.error, this, "TwlResourceManager.getLocalisedString: Unlikely (hopefully) encoding error="
					+ key);
			e.printStackTrace();
		}
		
		// either not found, or some problem converting to utf-8
		return key;
	}

	/**
	 * Get a localised string from a key for non-default language. 
	 * Only for TWL, used to get the correct text for the Spanish value of languages drop down,
	 * and also to get the new localised text for the language change warning from the 
	 * settings screen.
	 */
	public String getLocalisedString(String key, String languageCode) {

		// try to lookup the key
		String localString = null;

		ResourceBundle langBundle = ResourceBundle.getBundle("com.stillwindsoftware.keepyabeat.messagesBundle", new Locale(languageCode));
		
		// try to lookup the key
		if (langBundle != null) {
			try {
				localString = new String(langBundle.getString(key).getBytes("ISO-8859-1"), "UTF-8");
			} catch (MissingResourceException e) {
				log(LOG_TYPE.error, this, "TwlResourceManager.getLocalisedString#langVersion: failed to find message in any resource for key="
						+ key);
				e.printStackTrace();
			}
			catch (UnsupportedEncodingException e) {
				log(LOG_TYPE.error, this, "TwlResourceManager.getLocalisedString#langVersion: Unlikely (hopefully) encoding error="
						+ key);
				e.printStackTrace();
			}
		}
		
		if (localString != null) {
			return localString;
		}
		else {
			return getLocalisedString(key);
		}
	}

	@Override
	public void releaseSounds() {
		soundStore.killALData();
	}

	@Override
	public int getUndoHistoryLimit() {
		return MAX_UNDO_HISTORY;
	}

	@Override
	public String getVersionInfo() {
		return String.format(getLocalisedString(TwlLocalisationKeys.APP_VERSION), 
				library == null ? "?" : library.getVersion());
	}

	public File getLibraryFile() {
		return libraryXmlFile;
	}

	public File getKybHome() {
		return kybHome;
	}

	/**
	 * Put the take screenshot request into a pending task (so it is done just before Display.update())
	 */
	public void takeScreenShot() {
		log(LOG_TYPE.info, this, "TwlResourceManager.takeScreenshot: requested");
		pendingTasksScheduler.addTask(
				new PendingTask("take screenshot") {
					@Override
					protected void updateComplete() {
						complete = true;
						PNGWriter.takeScreenShot(kybHome);
					}});
	}

	/**
	 * Called when releasing resources. 
	 * Search for png files in kybHome older than a week and remove them
	 */
	@Override
	public void removeOldFiles() {
		long oneWeek = BeatTracker.MILLIS_PER_SECOND*60*60*24*7;
		long timeThreshHoldMillis = System.currentTimeMillis() - oneWeek;
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm");
		
		// filter to return files of the extension older than a week
		FileFilter filter = new SoundStore.FileListFilter(timeThreshHoldMillis, true, PNGWriter.PNG);
		File pngFile = null;
		
		try {
			File[] list = kybHome.listFiles(filter);

			for (int i = 0; i < list.length; i++) {
				pngFile = list[i];
				
				log(LOG_TYPE.info, this, String.format("TwlResourceManager.removeOldFiles: deleting file=%s lastModified=%s threshHold=%s"
						, pngFile.getName(), dateFormat.format(pngFile.lastModified()), dateFormat.format(timeThreshHoldMillis)));
				
				pngFile.delete();
			}
		}
		catch (Exception e) {
			log(LOG_TYPE.error, this, String.format("TwlResourceManager.removeOldFiles: error file=%s exception=%s"
					, pngFile == null ? "null" : pngFile.getName(), e.getMessage()));
			e.printStackTrace();
		}
	}

	@Override
	protected void setListenerSupport() {
		listenerSupport = ListenerSupport.getNewInstance(this);
	}

	@Override
	protected void setPendingTasksScheduler() {
		pendingTasksScheduler = PendingTasksScheduler.getNewInstance(this);
	}

}
