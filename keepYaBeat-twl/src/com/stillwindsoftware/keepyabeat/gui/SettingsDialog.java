package com.stillwindsoftware.keepyabeat.gui;

import java.util.Locale;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlColour;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Scrollbar;
import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.model.SimpleChangableListModel;
import de.matthiasmann.twl.model.SimpleIntegerModel;

/**
 * Settings screen to allow user to toggle anything that can be toggled, such as whether to show
 * the welcome screen
 * @author tomas stubbs
 *
 */
public class SettingsDialog extends DialogLayout {

	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private TwlPersistentStatesManager persistenceManager = (TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();

	private static final String[] LANGUAGES = { "en", "es" };

	private LabelCheckBox toggleWelcome;
	private LabelCheckBox toggleDebug;
	private LabelCheckBox toggleExtraDebug;
	private ComboBox<String> themesCombo;
	private LabelCheckBox toggleOpenDyslexicFont;
	private LabelCheckBox toggleHideRhythmsOnOpen;
	private LabelCheckBox toggleAnimatePlayedBeats;
	private LabelCheckBox togglePlayedBeatsMoveUpwards;
	private LabelSlider beatMovementSlider;
	private LabelSlider beatSpeedSlider;
	private LabelCheckBox togglePlayedBeatsKeepBouncing;
	private LabelCheckBox toggleShowSoundRipples;
	private LabelCheckBox toggleShowProgressIndicator;
	private LabelCheckBox toggleDrawBeatNumbers;
	private LabelCheckBox toggleDrawBeatNumbersAbove;
	private LabelCheckBox toggleColourPlayedFullBeats;
	private LabelCheckBox toggleColourPlayedSubBeats;
	private ColourButton playedFullBeatsColourBtn;
	private ColourButton playedSubBeatsColourBtn;
	private Button redefaultSettingsBtn;
	private ComboBox<String> languagesCombo;
	private Label languageLabel;
	
	public SettingsDialog() {
		setTheme("settingsDialog");
		initLayout();
		popup();
	}

	private void popup() {
        ConfirmationPopup.showDialogConfirm(new ConfirmationPopup.ConfirmPopupWindow(KeepYaBeat.root, true, false)
        		, null, null, null, null
        		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TITLE)
        		, ConfirmationPopup.EXTRA_BUTTON
        		, redefaultSettingsBtn, null, null
        		, true, null, ConfirmationPopup.EXTRA_BUTTON, true, this);
	
	}

	private void makeLanguagesCombo() {
		// this list MUST match the order of the constant array LANGUAGES
		SimpleChangableListModel<String> languagesModel = new SimpleChangableListModel<String>(
				resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_LANGUAGE_ENGLISH)
				, resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_LANGUAGE_SPANISH, "es"));
		languagesCombo = new ComboBox<String>(languagesModel);
		languagesCombo.setTheme("combobox");
		languagesCombo.setComputeWidthFromModel(true);
		final int originalLanguageSelection = getLanguageSelection();
		languagesCombo.setSelected(originalLanguageSelection);
		languagesCombo.addCallback(new Runnable(){
			@Override
			public void run() {
				int selection = languagesCombo.getSelected();
				String language = LANGUAGES[selection];
				
				String prevlang = ((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()).getLanguage();
				if (prevlang == null) {
					prevlang = Locale.getDefault().getLanguage();
				}
				
				// test for a new setting
				if (!prevlang.equals(language)) {
					((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()).setLanguage(language, false);// don't switch now
				}
				
				// show a message if the newly selected language doesn't match the original one when loading the page
				if (selection != originalLanguageSelection) {
					resourceManager.getGuiManager().showNotification(
							resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_LANGUAGE_WARNING, language));
				}
			}
		});		

		languageLabel = new Label();
		languageLabel.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_LANGUAGE_LABEL));
	}

	/**
	 * Note: Copy of the method of the same name in WelcomeDialog
	 */
	private int getLanguageSelection() {
		String lang = ((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()).getLanguage();
		if (lang == null) {
			lang = Locale.getDefault().getLanguage();
		}

		// tests for one of the languages matching either default locale or what was saved in persistent state
		for (int i = 0; i < LANGUAGES.length; i++) {
			if (lang.equals(new Locale(LANGUAGES[i], "", "").getLanguage())) {
				return i;
			}
		}

		// got this far, have to just choose the first one
		return 0;
	}

	/**
	 * Creates the layout to include in the dialog popup
	 */
	private void initLayout() {
		Label generalHeading = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_GENERAL_HEADING_LABEL));
		generalHeading.setTheme("label");
		
		// -------- show welcome
		toggleWelcome = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setShowWelcomeDialog(toggleWelcome.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_WELCOME_LABEL));

		// -------- debug to log
		toggleDebug = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setEnableDebugging(toggleDebug.isActive());
				toggleExtraDebug.setEnabled(persistenceManager.isEnableDebugging());
				if (!persistenceManager.isEnableDebugging()) {
					toggleExtraDebug.setActive(false);
					setExtraDebugging(false);
				}
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_DEBUG_LABEL));
		
		makeLanguagesCombo();
		
		// -------- debug to log
		toggleExtraDebug = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				setExtraDebugging(toggleExtraDebug.isActive());
			}
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_EXTRA_DEBUG_LABEL));

		SimpleChangableListModel<String> themesModel = new SimpleChangableListModel<String>(KeepYaBeat.root.getAvailableThemeNames());
		themesCombo = new ComboBox<String>(themesModel);
		themesCombo.setTheme("combobox");
		themesCombo.setComputeWidthFromModel(true);
		themesCombo.addCallback(new Runnable(){
			@Override
			public void run() {
				KeepYaBeat.root.chooseTheme(themesCombo.getSelected());
			}
		});
		Label themesLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_THEME_LABEL));
		
		toggleOpenDyslexicFont = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				KeepYaBeat.root.chooseFont(toggleOpenDyslexicFont.isActive() ? 1 : 0);
			}
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_FONT_OPEN_DYSLEXIC_LABEL));
				
		Label rhythmPlayerHeading = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_RHYTHM_PLAYER_HEADING_LABEL)
						, resourceManager.getLocalisedString(TwlLocalisationKeys.APP_MENU_PLAYER)));
		rhythmPlayerHeading.setTheme("label");
		
		// -------- hide rhythms list
		toggleHideRhythmsOnOpen = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setHideListOnOpenRhythm(toggleHideRhythmsOnOpen.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_HIDE_RHYTHMS_LIST_LABEL));

		// -------- draw beat numbers
		toggleDrawBeatNumbers = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setDrawBeatNumbers(toggleDrawBeatNumbers.isActive());
				toggleDrawBeatNumbersAbove.setEnabled(persistenceManager.isDrawBeatNumbers());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_DRAW_BEAT_NUMBERS_LABEL));

		// -------- where to draw beat numbers
		toggleDrawBeatNumbersAbove = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setDrawBeatNumbersAboveBeats(toggleDrawBeatNumbersAbove.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_DRAW_BEAT_NUMBERS_ABOVE_LABEL));
		// enable only when animation set
		toggleDrawBeatNumbersAbove.setEnabled(persistenceManager.isDrawBeatNumbers());

		Label animationsHeading = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_PLAY_ANIMATIONS_HEADING_LABEL));
		animationsHeading.setTheme("label");

		// -------- animate beats
		toggleAnimatePlayedBeats = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setAnimatePlayedBeats(toggleAnimatePlayedBeats.isActive());
				togglePlayedBeatsMoveUpwards.setEnabled(persistenceManager.isAnimatePlayedBeats());
				togglePlayedBeatsKeepBouncing.setEnabled(persistenceManager.isAnimatePlayedBeats());
				toggleShowSoundRipples.setEnabled(persistenceManager.isAnimatePlayedBeats());
				beatMovementSlider.setEnabled(persistenceManager.isAnimatePlayedBeats());
				beatSpeedSlider.setEnabled(persistenceManager.isAnimatePlayedBeats());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_ANIMATE_BEATS_LABEL));
		
		// -------- movement direction
		togglePlayedBeatsMoveUpwards = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setPlayedBeatMoveUpwards(togglePlayedBeatsMoveUpwards.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_ANIMATE_BEATS_UPWARDS_LABEL));
		// enable only when animation set
		togglePlayedBeatsMoveUpwards.setEnabled(persistenceManager.isAnimatePlayedBeats());
		
		// -------- movement distance
		beatMovementSlider = new LabelSlider(new Runnable() {
				@Override
				public void run() {
					persistenceManager.setPlayedBeatMoveDistance(beatMovementSlider.getValue());
				}
			}
			, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_SET_BEAT_MOVEMENT_DISTANCE_TOOLTIP)
			, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_SET_BEAT_MOVEMENT_DISTANCE_LABEL));
		
		// -------- movement speed
		beatSpeedSlider = new LabelSlider(new Runnable() {
				@Override
				public void run() {
					// reverse the value from the slider, because want faster to be to the right
					persistenceManager.setPlayedBeatMoveSpeed(Math.abs(beatSpeedSlider.getValue()-99));
				}
			}
			, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_SET_BEAT_MOVEMENT_SPEED_TOOLTIP)
			, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_SET_BEAT_MOVEMENT_SPEED_LABEL));
	
		// -------- keep going while playing
		togglePlayedBeatsKeepBouncing = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setPlayedBeatsKeepBouncing(togglePlayedBeatsKeepBouncing.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_BEATS_KEEP_BOUNCING_LABEL));
		// enable only when animation set
		togglePlayedBeatsKeepBouncing.setEnabled(persistenceManager.isAnimatePlayedBeats());
		
		// -------- show play effects
		toggleShowSoundRipples = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setShowSoundRipples(toggleShowSoundRipples.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_BEAT_RIPPLES_LABEL));
		// enable only when animation set
		toggleShowSoundRipples.setEnabled(persistenceManager.isAnimatePlayedBeats());
		
		// -------- show progress
		toggleShowProgressIndicator = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setShowProgressIndicator(toggleShowProgressIndicator.isActive());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_PROGRESS_INDICATOR_LABEL));

		// -------- colour played full beats
		toggleColourPlayedFullBeats = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setShowPlayingFullBeatColour(toggleColourPlayedFullBeats.isActive());
				playedFullBeatsColourBtn.setEnabled(persistenceManager.isShowPlayingFullBeatColour());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_COLOUR_PLAYED_FULL_BEATS_LABEL));
		
		playedFullBeatsColourBtn = new ColourButton(this, (TwlColour)persistenceManager.getPlayingFullBeatColour()
			, new Runnable() {
					@Override
					public void run() {
						persistenceManager.setPlayingFullBeatColour(playedFullBeatsColourBtn.getColour());
					}			
				});

		// -------- colour played sub beats
		toggleColourPlayedSubBeats = new LabelCheckBox(new Runnable(){
			@Override
			public void run() {
				persistenceManager.setShowPlayingSubBeatColour(toggleColourPlayedSubBeats.isActive());
				playedSubBeatsColourBtn.setEnabled(persistenceManager.isShowPlayingSubBeatColour());
			}			
		}
		, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_TOGGLE_COLOUR_PLAYED_SUB_BEATS_LABEL));

		playedSubBeatsColourBtn = new ColourButton(this, (TwlColour)persistenceManager.getPlayingSubBeatColour()
			, new Runnable() {
					@Override
					public void run() {
						persistenceManager.setPlayingSubBeatColour(playedSubBeatsColourBtn.getColour());
					}			
				});

		// dialog layout seems to set size 0 if I don't set the sizes like this
		playedFullBeatsColourBtn.setMinSize(40, 28);
		playedSubBeatsColourBtn.setMinSize(40, 28);

		// set values from store
		initValues(false);
		
		makeResetButton();
		
        setHorizontalGroup(createParallelGroup()
       		.addWidget(generalHeading)
    		.addGroup(createSequentialGroup().addWidget(toggleWelcome).addGap(MEDIUM_GAP).addWidget(toggleWelcome.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(languagesCombo).addGap(MEDIUM_GAP).addWidget(languageLabel).addGap())
    		.addGroup(createSequentialGroup().addWidget(toggleDebug).addGap(MEDIUM_GAP).addWidget(toggleDebug.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addGap().addWidget(toggleExtraDebug).addWidget(toggleExtraDebug.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(themesCombo).addGap(MEDIUM_GAP).addWidget(themesLabel).addGap(LARGE_GAP)
    				.addWidget(toggleOpenDyslexicFont).addGap(MEDIUM_GAP).addWidget(toggleOpenDyslexicFont.getLabel()).addGap())
       		.addWidget(rhythmPlayerHeading)
    		.addGroup(createSequentialGroup().addWidget(toggleHideRhythmsOnOpen).addGap(MEDIUM_GAP).addWidget(toggleHideRhythmsOnOpen.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(toggleDrawBeatNumbers).addGap(MEDIUM_GAP).addWidget(toggleDrawBeatNumbers.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(toggleDrawBeatNumbersAbove).addGap(MEDIUM_GAP).addWidget(toggleDrawBeatNumbersAbove.getLabel()).addGap())
       		.addWidget(animationsHeading)
    		.addGroup(createSequentialGroup().addWidget(toggleAnimatePlayedBeats).addGap(MEDIUM_GAP).addWidget(toggleAnimatePlayedBeats.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(togglePlayedBeatsMoveUpwards).addGap(MEDIUM_GAP).addWidget(togglePlayedBeatsMoveUpwards.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(beatMovementSlider).addGap(MEDIUM_GAP).addWidget(beatMovementSlider.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(beatSpeedSlider).addGap(MEDIUM_GAP).addWidget(beatSpeedSlider.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(togglePlayedBeatsKeepBouncing).addGap(MEDIUM_GAP).addWidget(togglePlayedBeatsKeepBouncing.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(toggleShowSoundRipples).addGap(MEDIUM_GAP).addWidget(toggleShowSoundRipples.getLabel()).addGap())
    		.addGroup(createSequentialGroup().addWidget(toggleColourPlayedFullBeats).addGap(MEDIUM_GAP).addWidget(toggleColourPlayedFullBeats.getLabel())
    					.addGap(MEDIUM_GAP).addWidget(playedFullBeatsColourBtn)
    				.addGap(LARGE_GAP).addWidget(toggleColourPlayedSubBeats).addGap(MEDIUM_GAP).addWidget(toggleColourPlayedSubBeats.getLabel())
    					.addGap(MEDIUM_GAP).addWidget(playedSubBeatsColourBtn).addGap())
    		.addGroup(createSequentialGroup().addWidget(toggleShowProgressIndicator).addGap(MEDIUM_GAP).addWidget(toggleShowProgressIndicator.getLabel()).addGap())
    		);
        setVerticalGroup(createSequentialGroup()
        	.addWidget(generalHeading)
    		.addGroup(createParallelGroup(toggleWelcome, toggleWelcome.getLabel()))
    		.addGroup(createParallelGroup(languagesCombo, languageLabel))
    		.addGroup(createParallelGroup(toggleDebug, toggleDebug.getLabel()))
    		.addGroup(createParallelGroup().addGroup(createSequentialGroup().addWidget(toggleExtraDebug).addGap()).addWidget(toggleExtraDebug.getLabel()))
    		.addGroup(createParallelGroup(themesCombo, themesLabel, toggleOpenDyslexicFont, toggleOpenDyslexicFont.getLabel()))
    		.addWidget(rhythmPlayerHeading)
    		.addGroup(createParallelGroup(toggleHideRhythmsOnOpen, toggleHideRhythmsOnOpen.getLabel()))
    		.addGroup(createParallelGroup(toggleDrawBeatNumbers, toggleDrawBeatNumbers.getLabel()))
    		.addGroup(createParallelGroup(toggleDrawBeatNumbersAbove, toggleDrawBeatNumbersAbove.getLabel()))
       		.addWidget(animationsHeading)
    		.addGroup(createParallelGroup(toggleAnimatePlayedBeats, toggleAnimatePlayedBeats.getLabel()))
    		.addGroup(createParallelGroup(togglePlayedBeatsMoveUpwards, togglePlayedBeatsMoveUpwards.getLabel()))
    		.addGroup(createParallelGroup(beatMovementSlider, beatMovementSlider.getLabel()))
    		.addGroup(createParallelGroup(beatSpeedSlider, beatSpeedSlider.getLabel()))
    		.addGroup(createParallelGroup(togglePlayedBeatsKeepBouncing, togglePlayedBeatsKeepBouncing.getLabel()))
    		.addGroup(createParallelGroup(toggleShowSoundRipples, toggleShowSoundRipples.getLabel()))
    		.addGroup(createParallelGroup(toggleColourPlayedFullBeats, toggleColourPlayedFullBeats.getLabel(), playedFullBeatsColourBtn
    				, toggleColourPlayedSubBeats, toggleColourPlayedSubBeats.getLabel(), playedSubBeatsColourBtn))
    		.addGroup(createParallelGroup().addWidget(toggleShowProgressIndicator).addWidget(toggleShowProgressIndicator.getLabel()))
    		);
       
        adjustSize();
	}

	/**
	 * Calls resource manager to either start or cancel the extra debugging
	 * @param active
	 */
	private void setExtraDebugging(boolean active) {
		if (active) {
			// create a callback to turn off the extra debugging button if this dialog is still showing
			resourceManager.startExtraDebugging(new Runnable() {
				@Override
				public void run() {
					resourceManager.log(LOG_TYPE.debug, this, String.format(
							"SettingsDialog.setExtraDebugging: callback to disactivate toggle run still visible=%s",
								SettingsDialog.this.isVisible()));
					if (SettingsDialog.this.isVisible()) {
						toggleExtraDebug.setActive(false);
					}
				}
			});
		}
		else if (resourceManager.isExtraDebuggingEnabled()) {
			resourceManager.cancelExtraDebugging();
		}
	}			

	private void makeResetButton() {
		redefaultSettingsBtn = new Button(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_REDEFAULT_BUTTON_LABEL));
		redefaultSettingsBtn.setTheme("genericBtn");
		redefaultSettingsBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_REDEFAULT_BUTTON_TOOLTIP));
		
		// get confirmation to continue
		redefaultSettingsBtn.addCallback(new Runnable() {
				@Override
				public void run() {
					Label confirmMsg = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_REDEFAULT_CONFIRM_LABEL));
					CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
		  	            public void callback(ConfirmationPopup.CallbackReason reason) {
		  	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
		  	            		// reset the values at the store
		  	            		persistenceManager.redefaultSettings();
		  	            		// and reset the widgets here
		  	            		// wait 2 ticks first, so themes can be reset
		  	            		resourceManager.getPendingTasksScheduler().addTask(new PendingTask("wait 2 ticks to init prefs") {
		  	            			private int count = 0;
									@Override
									protected void updateComplete() {
										complete = ++count == 2;
										if (complete) {
					  	            		initValues(true);
										}
									}
		  	            		});
		  	            	}
		  	            }
		  	        };

			    ConfirmationPopup.showDialogConfirm(null, callback, SettingsDialog.this, 
			    		resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_REDEFAULT_CONFIRM_TITLE), 
			    		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, confirmMsg);
				}
			});
	}

	/**
	 * Called after setting up all the widgets to init their values from the saved state
	 */
	private void initValues(boolean onReset) {
		toggleWelcome.setActive(persistenceManager.isShowWelcomeDialog());
		toggleDebug.setActive(persistenceManager.isEnableDebugging());
		toggleExtraDebug.setEnabled(persistenceManager.isEnableDebugging());
		toggleExtraDebug.setActive(!onReset && resourceManager.isExtraDebuggingEnabled());
		if (onReset && resourceManager.isExtraDebuggingEnabled()) {
			setExtraDebugging(false);
		}
		toggleExtraDebug.setEnabled(persistenceManager.isEnableDebugging());
		themesCombo.setSelected(KeepYaBeat.root.getCurrentTheme());
		toggleOpenDyslexicFont.setActive(KeepYaBeat.root.usingOpenDyslexicFont());
		toggleHideRhythmsOnOpen.setActive(persistenceManager.isHideListOnOpenRhythm());
		toggleDrawBeatNumbers.setActive(persistenceManager.isDrawBeatNumbers());
		toggleDrawBeatNumbersAbove.setActive(persistenceManager.isDrawBeatNumbersAboveBeats());
		toggleDrawBeatNumbersAbove.setEnabled(persistenceManager.isDrawBeatNumbers());
		toggleAnimatePlayedBeats.setActive(persistenceManager.isAnimatePlayedBeats());
		togglePlayedBeatsMoveUpwards.setActive(persistenceManager.isPlayedBeatMoveUpwards());
		togglePlayedBeatsMoveUpwards.setEnabled(persistenceManager.isAnimatePlayedBeats());
		beatMovementSlider.initValue(persistenceManager.getPlayedBeatMoveDistance());
		beatMovementSlider.setEnabled(persistenceManager.isAnimatePlayedBeats());
		beatSpeedSlider.initValue(Math.abs(persistenceManager.getPlayedBeatMoveSpeed()-99));
		beatSpeedSlider.setEnabled(persistenceManager.isAnimatePlayedBeats());
		togglePlayedBeatsKeepBouncing.setActive(persistenceManager.isPlayedBeatsKeepBouncing());
		togglePlayedBeatsKeepBouncing.setEnabled(persistenceManager.isAnimatePlayedBeats());
		toggleShowSoundRipples.setActive(persistenceManager.isShowSoundRipples());
		toggleShowSoundRipples.setEnabled(persistenceManager.isAnimatePlayedBeats());
		toggleShowProgressIndicator.setActive(persistenceManager.isShowProgressIndicator());
		toggleColourPlayedFullBeats.setActive(persistenceManager.isShowPlayingFullBeatColour());
		toggleColourPlayedSubBeats.setActive(persistenceManager.isShowPlayingSubBeatColour());
		playedFullBeatsColourBtn.setEnabled(persistenceManager.isShowPlayingFullBeatColour());
		playedSubBeatsColourBtn.setEnabled(persistenceManager.isShowPlayingSubBeatColour());
		if (onReset) {
			playedFullBeatsColourBtn.setBackgroundColour(((TwlColour)persistenceManager.getPlayingFullBeatColour()).getTwlColor());
			playedSubBeatsColourBtn.setBackgroundColour(((TwlColour)persistenceManager.getPlayingSubBeatColour()).getTwlColor());
		}
	}
	
	/**
	 * Links the label and check box so clicking on either works
	 */
	public static class LabelCheckBox extends ToggleButton implements Runnable {
		private Button label;
		
		public LabelCheckBox(Runnable callback, String labelLocalisedString) {
			setTheme("labelCheckbox");
			addCallback(callback);
			label = new Button(labelLocalisedString);
			label.setTheme("label");
			label.setFocusKeyEnabled(false);
			label.addCallback(this);
		}

		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled);
			label.setEnabled(enabled);
		}

		public Button getLabel() {
			return label;
		}

		@Override
		public void run() {
			// the label has been clicked
			LabelCheckBox.this.setActive(!LabelCheckBox.this.isActive());
			LabelCheckBox.this.doCallback();
		}
	}
	
	/**
	 * Links the label to the scrollbar to make the code tidier 
	 */
	public static class LabelSlider extends Scrollbar {

		private Label label;
		private SimpleIntegerModel integerModel;
		
		public LabelSlider(Runnable callback, String tooltip, String labelLocalisedString) {
			super(Scrollbar.Orientation.HORIZONTAL);
			setTheme("persistenceSlider");
			setTooltipContent(tooltip);
			setThumbTooltipContent(tooltip);

			addCallback(callback);
			label = new Label(labelLocalisedString);
			label.setTheme("label");
		}

		@Override
		public void setEnabled(boolean enabled) {
			super.setEnabled(enabled);
			label.setEnabled(enabled);
		}

		public Label getLabel() {
			return label;
		}
		
		public void initValue(int value) {
			if (integerModel == null) {
				integerModel = new SimpleIntegerModel(1, 100, value);
				setModel(integerModel);
			}
			else {
				integerModel.setValue(value);
			}
		}

		public int getValue() {
			return integerModel.getValue();
		}
	}
}
