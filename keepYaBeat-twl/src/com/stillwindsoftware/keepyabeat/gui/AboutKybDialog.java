package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Label;

/**
 * Settings screen to allow user to toggle anything that can be toggled, such as whether to show
 * the welcome screen
 * @author tomas stubbs
 *
 */
public class AboutKybDialog extends DialogLayout {

	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();

	public AboutKybDialog() {
		setTheme("aboutKybDialog");
		initLayout();
		popup();
	}

	private void popup() {
        ConfirmationPopup.showDialogConfirm(new ConfirmationPopup.ConfirmPopupWindow(KeepYaBeat.root, true, false)
        		, null, null, null, null
        		, resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_TITLE)
        		, ConfirmationPopup.NO_BUTTON
        		, null, null, null
        		, true, null, ConfirmationPopup.NO_BUTTON, true, this);
	
	}

	/**
	 * Creates the layout to include in the dialog popup
	 */
	private void initLayout() {
		Label aboutVersion = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_VERSION_TEXT)
						, String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.APP_VERSION)
								, resourceManager.getLibrary().getVersion())));
		aboutVersion.setTheme("label");
		
		Label aboutLicense = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_LICENSE_TEXT));
		aboutLicense.setTheme("label");

		Button copyrightBtn = makeCopyrightButton();
		
		Label aboutSocial = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_SOCIAL_TEXT));
		aboutVersion.setTheme("label");
		
		Button socialBtn = WelcomeDialog.makeSocialButton();

		Label aboutDonate = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_DONATE_TEXT));
		aboutVersion.setTheme("label");

		setHorizontalGroup(createParallelGroup()
				.addGroup(createSequentialGroup().addGap().addWidget(aboutVersion))
				.addGroup(createSequentialGroup().addGap(128).addWidget(aboutSocial))
	       		.addGroup(createSequentialGroup().addGap().addWidget(socialBtn).addGap())
	       		.addWidget(aboutDonate)
	       		.addWidget(aboutLicense)
	       		.addGroup(createSequentialGroup().addGap().addWidget(copyrightBtn).addGap())
    		);
        setVerticalGroup(createSequentialGroup()
	       		.addWidget(aboutVersion)
	       		.addGap(LARGE_GAP)
	       		.addWidget(aboutSocial)
	       		.addGap(LARGE_GAP)
	       		.addWidget(socialBtn)
	       		.addGap(LARGE_GAP)
	       		.addWidget(aboutDonate)
	       		.addGap(LARGE_GAP)
	       		.addWidget(aboutLicense)
	       		.addGap(LARGE_GAP)
	       		.addWidget(copyrightBtn)
	       	);
       
        adjustSize();
	}

	private Button makeCopyrightButton() {
		Button copyrightBtn = new Button(resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_COPYRIGHT_NOTICE_LABEL));
		copyrightBtn.setTheme("genericBtn");
		copyrightBtn.setCanAcceptKeyboardFocus(false);
		copyrightBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						ConfirmationPopup.showAlert(AboutKybDialog.this, resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_COPYRIGHT_NOTICE_TEXT)
								, resourceManager.getLocalisedString(TwlLocalisationKeys.ABOUT_COPYRIGHT_NOTICE_LABEL));
					}			
				});
		
		return copyrightBtn;
	}

}
