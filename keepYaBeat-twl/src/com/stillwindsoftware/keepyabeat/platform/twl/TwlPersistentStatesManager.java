package com.stillwindsoftware.keepyabeat.platform.twl;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.prefs.Preferences;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.gui.BeatsAndSoundsModule;
import com.stillwindsoftware.keepyabeat.gui.PauseableModule;
import com.stillwindsoftware.keepyabeat.gui.PlayerModule;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.xml.RhythmsXmlLoader;
import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;
import com.stillwindsoftware.keepyabeat.platform.PersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder.UnrecognizedFormatException;

import de.matthiasmann.twl.Color;


/** 
 * Manages saving and loading of the state of modules/panels/values
 */
public class TwlPersistentStatesManager extends PersistentStatesManager implements LibraryListener.LibraryListenerTarget {

	private static final String PREFS_NODE = "keepTheBeat.platform.PersistentStates";
	private static String ENC_ELEMENT_SEPARATOR = RhythmEncoder.ENC_ELEMENT_SEPARATOR;

	// modules	
	public static final String PLAYER = "player";
	public static final String BEATS_AND_SOUNDS = "beatsSounds";
	// the encoded editRhythm is kept as a preference, its existence indicates RhythmEditor was 
	// active on exit (when starting up)
	public static final String RHYTHM_EDITOR = "editRhythm";
	
	private static final String OPEN_MODULE = "openModule";
	private static final String RHYTHM_LIST_SEARCH_TAGS = "rhythmListSearchTags";
	private static final String RHYTHM_LIST_SEARCH_TEXT = "rhythmListSearchText";
	// licence, deliberately less obvious name
	private static final String KYB_STATE = "kybState";
	private static final String WELCOME_DIALOG = "welcome";
	private static final String ENABLE_DEBUGGING = "debug";
	private static final String HIDE_RHYTHMS_LIST_ON_OPEN = "hideRhythmsListOnOpen";
	private static final String SEEN_AUTO_HELP_KEYS = "seenAutoHelpKeys";
	private static final String AUTO_HELP_KEY_SEPARATOR = RhythmEncoder.ENC_SUB_ELEMENT_SEPARATOR;
	// language
	private static final String LANGUAGE = "language";
	
	private TwlResourceManager resourceManager;
	private Library library;
	protected String kybState;
	private String seenAutoHelpKeys;
	private String language;
	protected String lastOpenModule;
	protected EditRhythm editRhythm;

	public TwlPersistentStatesManager(Library library) {
		this.resourceManager = TwlResourceManager.getInstance();
		this.library = library;
		loadFromStore();
		resourceManager.log(LOG_TYPE.debug, this, String.format("TwlPersistentStatesManager.<init>: debugEnabled=%s", enableDebugging));
	}
	
	public void loadFromStore() {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

		lastOpenModule = readStateFromStore(prefs, OPEN_MODULE, null);
		
		String repeatOptionStr = readStateFromStore(prefs, REPEAT, RepeatOption.Infinite.name());
		RepeatOption repeatOption = RepeatOption.valueOf(repeatOptionStr);
		String nTimesStr = readStateFromStore(prefs, N_TIMES, "2");
		int nTimes = Integer.parseInt(nTimesStr);
		String openRhythmKey = readStateFromStore(prefs, PLAYED_RHYTHM_KEY, null);
		boolean volumesActive = readStateFromStore(prefs, PLAYER_VOLUMES, true);
		boolean rhythmsListOpen = readStateFromStore(prefs, RHYTHM_LIST_OPEN, true);
		String selectedBeatTypeKey = readStateFromStore(prefs, SELECTED_BEAT_TYPE, BeatTypes.DefaultBeatTypes.normal.getKey());
		BeatType selectedBeatType = (BeatType) library.getBeatTypes().lookup(selectedBeatTypeKey);
		playerState = new PlayerState(this, library, volumesActive, repeatOption, nTimes, openRhythmKey, rhythmsListOpen, selectedBeatType);
		
		// edit rhythm if there is one saved
		String encodedEditRhythm = readStateFromStore(prefs, RHYTHM_EDITOR, null);
		if (encodedEditRhythm != null) {
			editRhythm = RhythmsXmlLoader.loadEditRhythm(resourceManager, encodedEditRhythm);
		}
		
		// licence if there is one
		kybState = readStateFromStore(prefs, KYB_STATE, NULL);
		
		// language is null if not set specifically (on the welcome screen)
		language = readStateFromStore(prefs, LANGUAGE, null);
		
		// user modifiable setttings from settings page
		readSettings(prefs);
	}

	/**
	 * Used by TwlFileSelector
	 * @return
	 */
	public Preferences getPreferences() {
		return Preferences.userRoot().node(PREFS_NODE);
	}
	
	/**
	 * Write everything out to the buffer. Called from DiagnosticFileCompiler
	 */
	@Override
	public StringBuffer getDump() {
		StringBuffer out = new StringBuffer();
		final String EQUALS = " = ";
		final String NEW_LINE = "\n";
		out.append(OPEN_MODULE); out.append(EQUALS); out.append(lastOpenModule); out.append(NEW_LINE);
		out.append(RHYTHM_EDITOR); out.append(EQUALS); out.append(editRhythm == null ? NULL : editRhythm); out.append(NEW_LINE);
		out.append(KYB_STATE); out.append(EQUALS); out.append(kybState); out.append(NEW_LINE);
		out.append("-----------------------Player state------------------------"); out.append(NEW_LINE);
		out.append(PLAYER_VOLUMES); out.append(EQUALS); out.append(playerState.isVolumesActive()); out.append(NEW_LINE);
		out.append(REPEAT); out.append(EQUALS); out.append(playerState.getRepeatOption()); out.append(NEW_LINE);
		out.append(N_TIMES); out.append(EQUALS); out.append(playerState.getNTimes()); out.append(NEW_LINE);
		out.append(PLAYED_RHYTHM_KEY); out.append(EQUALS); out.append(playerState.getRhythmKey()); out.append(NEW_LINE);
		out.append(RHYTHM_LIST_OPEN); out.append(EQUALS); out.append(playerState.isRhythmsListOpen()); out.append(NEW_LINE);
		out.append(SELECTED_BEAT_TYPE); out.append(EQUALS); out.append(playerState.getBeatType().getKey()); out.append(NEW_LINE);
		out.append("-----------------------Settings------------------------"); out.append(NEW_LINE);
		out.append(WELCOME_DIALOG); out.append(EQUALS); out.append(showWelcomeDialog); out.append(NEW_LINE);
		out.append(ENABLE_DEBUGGING); out.append(EQUALS); out.append(enableDebugging); out.append(NEW_LINE);
		out.append(HIDE_RHYTHMS_LIST_ON_OPEN); out.append(EQUALS); out.append(hideListOnOpenRhythm); out.append(NEW_LINE);
		out.append(ANIMATE_PLAYED_BEATS); out.append(EQUALS); out.append(animatePlayedBeats); out.append(NEW_LINE);
		out.append(PLAYED_BEATS_MOVE_UPWARDS); out.append(EQUALS); out.append(playedBeatMoveUpwards); out.append(NEW_LINE);
		out.append(PLAYED_BEATS_KEEP_BOUNCING); out.append(EQUALS); out.append(playedBeatsKeepBouncing); out.append(NEW_LINE);
		out.append(PLAYED_BEATS_MOVE_DISTANCE); out.append(EQUALS); out.append(playedBeatMoveDistance); out.append(NEW_LINE);
		out.append(PLAYED_BEATS_MOVE_SPEED); out.append(EQUALS); out.append(playedBeatMoveSpeed); out.append(NEW_LINE);
		out.append(SHOW_SOUND_RIPPLES); out.append(EQUALS); out.append(showSoundRipples); out.append(NEW_LINE);
		out.append(SHOW_PROGRESS_INDICATOR); out.append(EQUALS); out.append(showProgressIndicator); out.append(NEW_LINE);
		out.append(DRAW_BEAT_NUMBERS); out.append(EQUALS); out.append(drawBeatNumbers); out.append(NEW_LINE);
		out.append(DRAW_BEAT_NUMBERS_ABOVE_BEATS); out.append(EQUALS); out.append(drawBeatNumbersAboveBeats); out.append(NEW_LINE);
		out.append(SHOW_PLAYING_FULL_BEAT_COLOUR); out.append(EQUALS); out.append(showPlayingFullBeatColour); out.append(NEW_LINE);
		out.append(SHOW_PLAYING_SUB_BEAT_COLOUR); out.append(EQUALS); out.append(showPlayingSubBeatColour); out.append(NEW_LINE);
		out.append(PLAYING_FULL_BEAT_COLOUR); out.append(EQUALS); out.append(playingFullBeatColour.getHexString()); out.append(NEW_LINE);
		out.append(PLAYING_SUB_BEAT_COLOUR); out.append(EQUALS); out.append(playingSubBeatColour.getHexString()); out.append(NEW_LINE);
		out.append(SEEN_AUTO_HELP_KEYS); out.append(EQUALS); out.append(seenAutoHelpKeys); out.append(NEW_LINE);
		out.append("-----------------------Theme------------------------"); out.append(NEW_LINE);
		out.append("Current theme"); out.append(EQUALS); out.append(KeepYaBeat.root.getCurrentThemeXmlFileName()); out.append(NEW_LINE);
		return out;
	}

	public void readSettings() {
		readSettings(Preferences.userRoot().node(PREFS_NODE));
	}

	private void readSettings(Preferences prefs) {
		// settings
		showWelcomeDialog = readStateFromStore(prefs, WELCOME_DIALOG, true);
		enableDebugging = readStateFromStore(prefs, ENABLE_DEBUGGING, false);
		hideListOnOpenRhythm = readStateFromStore(prefs, HIDE_RHYTHMS_LIST_ON_OPEN, false);
		animatePlayedBeats = readStateFromStore(prefs, ANIMATE_PLAYED_BEATS, true);
		playedBeatMoveUpwards = readStateFromStore(prefs, PLAYED_BEATS_MOVE_UPWARDS, false);
		playedBeatsKeepBouncing = readStateFromStore(prefs, PLAYED_BEATS_KEEP_BOUNCING, false);
		playedBeatMoveDistance = Integer.parseInt(readStateFromStore(prefs, PLAYED_BEATS_MOVE_DISTANCE, DEFAULT_PLAYED_BEATS_MOVE_DISTANCE));
		playedBeatMoveSpeed = Integer.parseInt(readStateFromStore(prefs, PLAYED_BEATS_MOVE_SPEED, DEFAULT_PLAYED_BEATS_MOVE_SPEED));
		showSoundRipples = readStateFromStore(prefs, SHOW_SOUND_RIPPLES, true);
		showProgressIndicator = readStateFromStore(prefs, SHOW_PROGRESS_INDICATOR, true);
		drawBeatNumbers = readStateFromStore(prefs, DRAW_BEAT_NUMBERS, true);
		drawBeatNumbersAboveBeats = readStateFromStore(prefs, DRAW_BEAT_NUMBERS_ABOVE_BEATS, true);
		showPlayingFullBeatColour = readStateFromStore(prefs, SHOW_PLAYING_FULL_BEAT_COLOUR, true);
		showPlayingSubBeatColour = readStateFromStore(prefs, SHOW_PLAYING_SUB_BEAT_COLOUR, true);
		playingFullBeatColour = new TwlColour(Color.parserColor(
				readStateFromStore(prefs, PLAYING_FULL_BEAT_COLOUR, DEFAULT_PLAYING_FULL_BEATS_COLOUR)));
		playingSubBeatColour = new TwlColour(Color.parserColor(
				readStateFromStore(prefs, PLAYING_SUB_BEAT_COLOUR, DEFAULT_PLAYING_SUB_BEATS_COLOUR)));
		seenAutoHelpKeys = readStateFromStore(prefs, SEEN_AUTO_HELP_KEYS, null);
	}

	@Override
	public void redefaultSettings() {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		
		prefs.remove(WELCOME_DIALOG);
		prefs.remove(ENABLE_DEBUGGING);
		prefs.remove(HIDE_RHYTHMS_LIST_ON_OPEN);
		prefs.remove(ANIMATE_PLAYED_BEATS);
		prefs.remove(PLAYED_BEATS_MOVE_UPWARDS);
		prefs.remove(PLAYED_BEATS_KEEP_BOUNCING);
		prefs.remove(PLAYED_BEATS_MOVE_DISTANCE);
		prefs.remove(PLAYED_BEATS_MOVE_SPEED);
		prefs.remove(SHOW_SOUND_RIPPLES);
		prefs.remove(SHOW_PROGRESS_INDICATOR);
		prefs.remove(DRAW_BEAT_NUMBERS);
		prefs.remove(DRAW_BEAT_NUMBERS_ABOVE_BEATS);
		prefs.remove(SHOW_PLAYING_FULL_BEAT_COLOUR);
		prefs.remove(SHOW_PLAYING_SUB_BEAT_COLOUR);
		prefs.remove(PLAYING_FULL_BEAT_COLOUR);
		prefs.remove(PLAYING_SUB_BEAT_COLOUR);
		//TODO decide whether to keep reset of auto help or once seen is forever
		prefs.remove(SEEN_AUTO_HELP_KEYS);
		
		// language only bother if there is a setting (otherwise it's just the default)
		if (language != null) {
			prefs.remove(LANGUAGE);
			((TwlResourceManager)resourceManager).switchLanguage(null);
		}
		
		readSettings(prefs);
		
		KeepYaBeat.root.resetDefaultDisplayAndTheme();
	}

	private String readStateFromStore(Preferences prefs, String stateName, String defaultStr) {
		return prefs.get(stateName, defaultStr);
	}
	
	private boolean readStateFromStore(Preferences prefs, String stateName, boolean defaultBool) {
		return prefs.getBoolean(stateName, defaultBool); 
	}
	
	private void writeStateToStore(Preferences prefs, String stateName, String stringRepresentation) {
		if (stringRepresentation == null) {
			prefs.remove(stateName);
		}
		else {
			prefs.put(stateName, stringRepresentation);
		}
	}

	private void writeStateToStore(Preferences prefs, String stateName, boolean boolValue) {
		prefs.putBoolean(stateName, boolValue);
	}

	@Override
	public void playerStateChanged() {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

		writeStateToStore(prefs, SELECTED_BEAT_TYPE, playerState.getBeatType().getKey());
		writeStateToStore(prefs, REPEAT, playerState.getRepeatOption().name());
		writeStateToStore(prefs, N_TIMES, Integer.toString(playerState.getNTimes()));
		writeStateToStore(prefs, PLAYER_VOLUMES, playerState.isVolumesActive());
		writeStateToStore(prefs, PLAYED_RHYTHM_KEY, playerState.getRhythmKey());
		writeStateToStore(prefs, RHYTHM_LIST_OPEN, playerState.isRhythmsListOpen());
		
		resourceManager.getListenerSupport().fireItemChanged(this.getListenerKey());
	}

	public void moduleClosed(String moduleName) {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		
		lastOpenModule = null;
		writeStateToStore(prefs, OPEN_MODULE, null);
	}

	public void moduleOpened(String moduleName) {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		
		lastOpenModule = moduleName;
		writeStateToStore(prefs, OPEN_MODULE, moduleName);
	}

	public void moduleClosed() {
		moduleClosed(null);
	}
	
	public void moduleOpened(PauseableModule module) {
		if (module instanceof PlayerModule) {
			moduleOpened(PLAYER);
		}
		else if (module instanceof BeatsAndSoundsModule) {
			moduleOpened(BEATS_AND_SOUNDS);
		}
		else {
			resourceManager.log(LOG_TYPE.error, this, "TwlPersistentStatesManager.moduleOpened: Module opened not recognized: "+module.getClass());
			moduleClosed();
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + PREFS_NODE.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		return true;
	}

	public void setEditRhythm(EditRhythm editRhythm) {
		this.editRhythm = editRhythm;
		
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

		// null means remove it
		if (editRhythm == null) {
			writeStateToStore(prefs, RHYTHM_EDITOR, null);
			//TODO remove any undo commands saved too (currently not implemented, but maybe needed for Android)
		}
		else {
			try {
				writeStateToStore(prefs, RHYTHM_EDITOR, RhythmEncoder.getFullWeightRhythmEncoder(Function.getBlankContext(resourceManager), 
						editRhythm.getLibrary()).codifyRhythm(editRhythm));
			} catch (UnsupportedEncodingException e) {
				resourceManager.log(LOG_TYPE.error, this, "TwlPersistentStatesManager.setEditRhythm: unexpected encoding exception");
				e.printStackTrace();
			} catch (UnrecognizedFormatException e) {
				resourceManager.log(LOG_TYPE.error, this, "TwlPersistentStatesManager.setEditRhythm: unexpected format exception");
				e.printStackTrace();
			}
		}
		
	}

	public EditRhythm getEditRhythm() {
		return editRhythm;
	}

	/**
	 * Called from the RhythmSearchModel to store the currently selected tags and search text in searching.
	 * Stored as an encoded string
	 */
	@Override
	public void setRhythmSearchCriteria(String searchText, HashSet<Tag> tags) {

		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		writeStateToStore(prefs, RHYTHM_LIST_SEARCH_TEXT, searchText);		
		
		StringBuffer sb = new StringBuffer();
		for (Iterator<Tag> it = tags.iterator(); it.hasNext(); ) {
			sb.append(it.next().getKey());
			sb.append(ENC_ELEMENT_SEPARATOR);
		}
		
		writeStateToStore(prefs, RHYTHM_LIST_SEARCH_TAGS, sb.toString());		
	}

	/**
	 * Called by the rhythmsList when starting up to retrieve the last tags used in searching
	 */
	@Override
	public String[] getRhythmSearchTagKeys() {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		String encodedTags = readStateFromStore(prefs, RHYTHM_LIST_SEARCH_TAGS, null);

		if (encodedTags != null) {
			return encodedTags.split(ENC_ELEMENT_SEPARATOR);
		}
		else {
			return null;
		}
	}

	@Override
	public String getRhythmSearchText() {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		return readStateFromStore(prefs, RHYTHM_LIST_SEARCH_TEXT, null);
	}

	@Override
	public String getKybState() {
		return kybState;
	}

	@Override
	public void setKybState(String licState) {
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, KYB_STATE, licState);
	}

	@Override
	public void setShowWelcomeDialog(boolean showWelcomeDialog) {
		this.showWelcomeDialog = showWelcomeDialog;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, WELCOME_DIALOG, showWelcomeDialog);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setShowWelcomeDialog: showWelcome=%s", showWelcomeDialog));
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language, boolean switchNow) {
		this.language = language;
		// user is setting, so switch out what is current value or perhaps remove it if new value is the default locale
		if (Locale.getDefault().getLanguage().equals(new Locale(language, "", "").getLanguage())) {
			language = null;
		}
		
		if (switchNow) {
			((TwlResourceManager)resourceManager).switchLanguage(language);
		}

		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		if (language == null) {
			prefs.remove(LANGUAGE);
			resourceManager.log(LOG_TYPE.info, this, "TwlPersistentStatesManager.setLanguage: language reset to default");
		}
		else {
			this.writeStateToStore(prefs, LANGUAGE, language);
			resourceManager.log(LOG_TYPE.info, this
				, String.format("TwlPersistentStatesManager.setLanguage: language=%s", language));
		}
	}

	public void setEnableDebugging(boolean enableDebugging) {
		this.enableDebugging = enableDebugging;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, ENABLE_DEBUGGING, enableDebugging);
		resourceManager.setMinLogLevel(
				enableDebugging ? PlatformResourceManager.DEBUG_LOGGING_LEVEL : PlatformResourceManager.NORMAL_LOGGING_LEVEL);
		resourceManager.log(LOG_TYPE.info, this
				, String.format("TwlPersistentStatesManager.setEnableDebugging: set=%s", enableDebugging));
		resourceManager.log(LOG_TYPE.debug, this, "TwlPersistentStatesManager.setEnableDebugging: test debug level");
	}

	public void setHideListOnOpenRhythm(boolean hideListOnOpenRhythm) {
		this.hideListOnOpenRhythm = hideListOnOpenRhythm;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, HIDE_RHYTHMS_LIST_ON_OPEN, hideListOnOpenRhythm);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setHideListOnOpenRhythm: set=%s", hideListOnOpenRhythm));
	}

	@Override
	public void setAnimatePlayedBeats(boolean animatePlayedBeats) {
		this.animatePlayedBeats = animatePlayedBeats;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, ANIMATE_PLAYED_BEATS, animatePlayedBeats);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setAnimatePlayedBeats: set=%s", animatePlayedBeats));
	}

	@Override
	public void setPlayedBeatMoveUpwards(boolean playedBeatMoveUpwards) {
		this.playedBeatMoveUpwards = playedBeatMoveUpwards;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, PLAYED_BEATS_MOVE_UPWARDS, playedBeatMoveUpwards);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setPlayedBeatMoveUpwards: set=%s", playedBeatMoveUpwards));
	}

	@Override
	public void setPlayedBeatsKeepBouncing(boolean playedBeatsKeepBouncing) {
		this.playedBeatsKeepBouncing = playedBeatsKeepBouncing;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, PLAYED_BEATS_KEEP_BOUNCING, playedBeatsKeepBouncing);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setPlayedBeatsKeepBouncing: set=%s", playedBeatsKeepBouncing));
	}

	@Override
	public void setPlayedBeatMoveDistance(int playedBeatMoveDistance) {
		this.playedBeatMoveDistance = playedBeatMoveDistance;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, PLAYED_BEATS_MOVE_DISTANCE, Integer.toString(playedBeatMoveDistance));
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setPlayedBeatMoveDistance: set=%s", playedBeatMoveDistance));
	}

	@Override
	public void setPlayedBeatMoveSpeed(int playedBeatMoveSpeed) {
		this.playedBeatMoveSpeed = playedBeatMoveSpeed;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, PLAYED_BEATS_MOVE_SPEED, Integer.toString(playedBeatMoveSpeed));
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setPlayedBeatMoveSpeed: set=%s", playedBeatMoveSpeed));
	}

	@Override
	public void setShowSoundRipples(boolean showSoundRipples) {
		this.showSoundRipples = showSoundRipples;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, SHOW_SOUND_RIPPLES, showSoundRipples);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setShowSoundRipples: set=%s", showSoundRipples));
	}

	@Override
	public void setShowProgressIndicator(boolean showProgressIndicator) {
		this.showProgressIndicator = showProgressIndicator;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, SHOW_PROGRESS_INDICATOR, showProgressIndicator);
		resourceManager.log(LOG_TYPE.info, this
				, String.format("TwlPersistentStatesManager.setShowProgressIndicator: set=%s", showProgressIndicator));
	}

	@Override
	public void setDrawBeatNumbers(boolean drawBeatNumbers) {
		this.drawBeatNumbers = drawBeatNumbers;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, DRAW_BEAT_NUMBERS, drawBeatNumbers);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setDrawBeatNumbers: set=%s", drawBeatNumbers));
	}
	
	@Override
	public void setDrawBeatNumbersAboveBeats(boolean drawBeatNumbersAboveBeats) {
		this.drawBeatNumbersAboveBeats = drawBeatNumbersAboveBeats;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, DRAW_BEAT_NUMBERS_ABOVE_BEATS, drawBeatNumbersAboveBeats);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setDrawBeatNumbersAboveBeats: set=%s", drawBeatNumbersAboveBeats));
	}

	@Override
	public void setShowPlayingFullBeatColour(boolean showPlayingFullBeatColour) {
		this.showPlayingFullBeatColour = showPlayingFullBeatColour;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, SHOW_PLAYING_FULL_BEAT_COLOUR, showPlayingFullBeatColour);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setShowPlayingFullBeatColour: set=%s", showPlayingFullBeatColour));
	}

	@Override
	public void setShowPlayingSubBeatColour(boolean showPlayingSubBeatColour) {
		this.showPlayingSubBeatColour = showPlayingSubBeatColour;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, SHOW_PLAYING_SUB_BEAT_COLOUR, showPlayingSubBeatColour);
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setShowPlayingSubBeatColour: set=%s", showPlayingSubBeatColour));
	}

	@Override
	public void setPlayingFullBeatColour(Colour playingFullBeatColour) {
		this.playingFullBeatColour = playingFullBeatColour;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, PLAYING_FULL_BEAT_COLOUR, playingFullBeatColour.getHexString());
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setPlayingFullBeatColour: set=%s", playingFullBeatColour.getHexString()));
	}

	@Override
	public void setPlayingSubBeatColour(Colour playingSubBeatColour) {
		this.playingSubBeatColour = playingSubBeatColour;
		Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
		this.writeStateToStore(prefs, PLAYING_SUB_BEAT_COLOUR, playingSubBeatColour.getHexString());
		resourceManager.log(LOG_TYPE.debug, this
				, String.format("TwlPersistentStatesManager.setPlayingSubBeatColour: set=%s", playingSubBeatColour.getHexString()));
	}

	/**
	 * Called from various screens (such as player) when started to see if auto help should be
	 * shown, tests to see if it's been seen already
	 * @param autoHelpKey
	 * @return
	 */
	public boolean isAutoHelpSeen(String autoHelpKey) {
		return seenAutoHelpKeys != null && seenAutoHelpKeys.contains(autoHelpKey);
	}
	
	/**
	 * Adds this key to the list of seen keys
	 * @param autoHelpKey
	 */
	public void setAutoHelpSeen(String autoHelpKey) {
		if (!isAutoHelpSeen(autoHelpKey)) {
			resourceManager.log(LOG_TYPE.debug, this
					, String.format("TwlPersistentStatesManager.setAutoHelpSeen: current=%s, adding=%s", seenAutoHelpKeys, autoHelpKey));

			if (seenAutoHelpKeys == null) {
				seenAutoHelpKeys = autoHelpKey;
			}
			else {
				seenAutoHelpKeys = seenAutoHelpKeys.concat(AUTO_HELP_KEY_SEPARATOR);
				seenAutoHelpKeys = seenAutoHelpKeys.concat(autoHelpKey);
			}

			Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
			writeStateToStore(prefs, SEEN_AUTO_HELP_KEYS, seenAutoHelpKeys);
		}
	}
	
	public String getLastOpenModule() {
		return lastOpenModule;
	}

	@Override
	public String getListenerKey() {
		return "settings";
	}


}
