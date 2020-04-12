package com.stillwindsoftware.keepyabeat.gui;

import java.util.Locale;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.gui.SettingsDialog.LabelCheckBox;
import com.stillwindsoftware.keepyabeat.platform.PersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy.Key;

import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.model.SimpleChangableListModel;

/**
 * Welcome screen that gives some fast info to a new user, comes up until user cancels it and can
 * reset in settings (stored in prefs)
 * @author tomas stubbs
 *
 */
public class WelcomeDialog extends DialogLayout {

	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	private BoxLayout toggleShowBox;
	private LabelCheckBox toggleShow;
	private ComboBox<String> languagesCombo;

	private Label welcomeHeading;	
	private Label welcomeBody;
	private Label languageLabel;
	
	private Button helpBtn;
	private Button socialBtn;
	
	/**
	 * Opens the welcome dialog
	 * @param doNext Whatever has to happen when this dialog closes (either restart rhythm editor or last open module)
	 */
	public WelcomeDialog(Runnable doNext) {
		setTheme("welcomeDialog");
		initLayout();
		popup(doNext);
	}

	// makes a checkbox button for turning it off and calls the popup
	private void popup(final Runnable doNext) {
		
//        makeToggleShowButton();
        
		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
	            public void callback(ConfirmationPopup.CallbackReason reason) {
	            	KeepYaBeat.root.setupMenuBar();
	            	doNext.run();
//	            	resourceManager.getPersistentStatesManager().getPlayerState().setVolumesActive(true);
//	            	resourceManager.getGuiManager().openPlayerWithRhythm(null); 
	            }
	        };

        ConfirmationPopup.showDialogConfirm(new ConfirmationPopup.ConfirmPopupWindow(true), null, callback, null, null
        		, resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_TITLE)
        		, ConfirmationPopup.OK_BUTTON | ConfirmationPopup.EXTRA_BUTTON
        		, toggleShowBox, null, null
        		, true, null, ConfirmationPopup.NO_BUTTON, false, this);
	}

	/**
	 * Called during initLayout, and also if the user chooses to change the language, so all
	 * text can be changed
	 */
	private void setLocaleText() {
		welcomeHeading.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_HEADING));
		welcomeBody.setText(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_BODY)
						, resourceManager.getLocalisedString(TwlLocalisationKeys.COLOURS_TEXT)
						));
		
		languageLabel.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_LANGUAGE_LABEL));
		// this value is set once already in creation of the checkbox and label
		toggleShow.getLabel().setText(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_TOGGLE_BUTTON_LABEL));
		toggleShow.getLabel().setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_TOGGLE_BUTTON_TOOLTIP));

		toggleShowBox.adjustSize();

		helpBtn.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_HELP_BUTTON_LABEL));
		helpBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_HELP_BUTTON_TOOLTIP));

		// also already set the first time in
		socialBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_SOCIAL_BUTTON_TOOLTIP));
        
        adjustSize();
	}
	
	/**
	 * Creates the layout to include in the dialog popup
	 */
	private void initLayout() {
		welcomeHeading = new Label();
		welcomeHeading.setTheme("largeTitlesLabel");
		welcomeBody = new Label();
		welcomeBody.setTheme("whiteLabel");

		toggleShowBox = new BoxLayout();
		toggleShowBox.setTheme("welcomeToggleBox");

		makeLanguagesCombo();
		makeToggleShowButton();

		toggleShowBox.adjustSize();

		makeHelpButton();
		socialBtn = makeSocialButton();
		
		setLocaleText();
		
        setHorizontalGroup(createParallelGroup()
        		.addGroup(createSequentialGroup()
	        		.addGap()
	        		.addWidget(welcomeHeading)
	        		.addGap())
        		.addGap(LARGE_GAP)
        		.addGroup(createSequentialGroup()
	        		.addGap()
	        		.addWidget(welcomeBody)
	        		.addGap())
        		.addGroup(createSequentialGroup()
        			.addWidget(socialBtn)
	        		.addGap()
	        		.addWidget(helpBtn))
        		);
        setVerticalGroup(createSequentialGroup()
        		.addWidget(welcomeHeading)
        		.addWidget(welcomeBody)
        		.addGap(MEDIUM_GAP)
        		.addGroup(createParallelGroup(socialBtn, helpBtn))
        		);
	}
	
	/**
	 * Get the language to select as the shown language
	 * Complicated because if there's no setting in persistent manager needs to test default locale for being in the 
	 * possibles. If not, English would be set as default.
	 * @return
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
	
	private static final String[] LANGUAGES = { "en", "es" };
	
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
					((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()).setLanguage(language, true); // switch now
					setLocaleText();
					invalidateLayout();
				}
			}
		});		

		languageLabel = new Label();
		toggleShowBox.add(languageLabel);
		toggleShowBox.add(languagesCombo);
	}
	
	private void makeToggleShowButton() {
		toggleShow = new LabelCheckBox(new Runnable() {
			@Override
			public void run() {
				PersistentStatesManager persistenceManager = resourceManager.getPersistentStatesManager();
				persistenceManager.setShowWelcomeDialog(toggleShow.isActive());
			}			
		}, resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_TOGGLE_BUTTON_LABEL));
		toggleShow.setActive(true);
		toggleShow.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.WELCOME_TOGGLE_BUTTON_TOOLTIP));
		
		toggleShowBox.add(toggleShow.getLabel());		
		toggleShowBox.add(toggleShow);
		
//		toggleShowBox.adjustSize();
	}

	private Button makeHelpButton() {
		helpBtn = new Button();
		helpBtn.setTheme("genericBtn");
		helpBtn.setCanAcceptKeyboardFocus(false);
		helpBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						UserBrowserProxy.showWebPage(Key.tour);
					}			
				});
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		
		return helpBtn;
	}
	
	/**
	 * Called here and also from AboutKybDialog
	 * @return
	 */
	public static Button makeSocialButton() {
		Button socialBtn = new Button();
		socialBtn.setTheme("socialBtn");
		socialBtn.setCanAcceptKeyboardFocus(false);
		socialBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						UserBrowserProxy.showWebPage(Key.home);
					}			
				});
		socialBtn.setTooltipContent(TwlResourceManager.getInstance()
				.getLocalisedString(TwlLocalisationKeys.WELCOME_SOCIAL_BUTTON_TOOLTIP));
		socialBtn.setSize(socialBtn.getMaxWidth(), socialBtn.getMaxHeight());

		return socialBtn;
	}
}
