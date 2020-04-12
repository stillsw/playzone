package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup.ConfirmPopupWindow;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.twl.DiagnosticFileCompiler;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy.Key;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Widget;

/**
 * Screen that explains the bug report process, and offers link to do that online
 * plus a button to generate the dump file
 * @author tomas stubbs
 */
public class BugReportDialog extends DialogLayout {

	private TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	private Button generateDumpFileButton;
	private boolean bugFileGenerated = false;
	private boolean submitLinkPressed = false;
	private ConfirmPopupWindow confirmPopupWindow;
	
	public BugReportDialog() {
		setTheme("bugReportDialog");
		initLayout();
		popup();
		popupRepeatItDialog();
	}

	/**
	 * Puts a 2nd dialog over the first to get the user to repeat the problem if they can
	 */
	private void popupRepeatItDialog() {
		// need a new popup window, child of the main one
		final ConfirmPopupWindow repeatPopupWindow = new ConfirmPopupWindow(confirmPopupWindow, true, true);
		
		// make a layout 
		DialogLayout repeatDlgLayout = new DialogLayout();
		repeatDlgLayout.setTheme("dialoglayout");

		Label bugReportCheck = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_CHECK));
		bugReportCheck.setTheme("label");
		
		Label bugRepeatBody = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPEAT_BODY));
		Button bugRepeatYesBtn = new Button(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPEAT_YES_LABEL));
		bugRepeatYesBtn.setTheme("genericBtn");
		bugRepeatYesBtn.addCallback(new Runnable() {
			@Override
			public void run() {
				popupDoingRepeatConfirmation();
			}
		});
		
		Runnable otherBtnsCallback = new Runnable() {
			@Override
			public void run() {
				// either skipping or done already, either way just close this popup
				repeatPopupWindow.closePopup();
				BugReportDialog.this.requestKeyboardFocus();
			}
		};
		
		Button bugRepeatDoneBtn = new Button(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPEAT_DONE_LABEL));
		bugRepeatDoneBtn.setTheme("genericBtn");
		bugRepeatDoneBtn.addCallback(otherBtnsCallback);
		Button bugRepeatNoBtn = new Button(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPEAT_NO_LABEL));
		bugRepeatNoBtn.setTheme("genericBtn");
		bugRepeatNoBtn.addCallback(otherBtnsCallback);
		
		Button helpBtn = makeOnlineHelpButton();
		
		repeatDlgLayout.setHorizontalGroup(repeatDlgLayout.createParallelGroup(
				bugReportCheck, bugRepeatBody, bugRepeatYesBtn, bugRepeatDoneBtn, bugRepeatNoBtn)
				.addGroup(repeatDlgLayout.createSequentialGroup().addWidget(helpBtn).addGap()));
		repeatDlgLayout.setVerticalGroup(repeatDlgLayout.createSequentialGroup()
				.addWidget(bugReportCheck)
				.addGap(MEDIUM_GAP)
				.addWidget(bugRepeatBody)
				.addGap(MEDIUM_GAP)
				.addWidget(bugRepeatYesBtn)
				.addWidget(bugRepeatDoneBtn)
				.addWidget(bugRepeatNoBtn)
				.addGroup(repeatDlgLayout.createSequentialGroup().addWidget(helpBtn).addGap())
				);
		
		ConfirmationPopup.showDialogConfirm(repeatPopupWindow, null, null, null, null
        		, resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_TITLE)
        		, ConfirmationPopup.NO_BUTTON
        		, null, null, null
        		, true, null, ConfirmationPopup.NO_BUTTON, false, repeatDlgLayout);		
	}

	private Button makeOnlineHelpButton() {
		Button helpBtn = new Button(TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.HELP_ONLINE_LABEL));
		helpBtn.setTheme("genericPanelBtn");

		helpBtn.setCanAcceptKeyboardFocus(false);
		helpBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						UserBrowserProxy.showWebPage(UserBrowserProxy.Key.bugReportHelp);
					}			
				});
		helpBtn.setTooltipContent(TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.HELP_ONLINE_TOOLTIP));
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		
		return helpBtn;
	}

	/**
	 * Called when the user says they will repeat the bug now
	 */
	private void popupDoingRepeatConfirmation() {
		// turn on max debugging
		resourceManager.startExtraDebugging(null);
		
		// close other popups
		confirmPopupWindow.closePopup();
		
		// popup message
		ConfirmationPopup.showAlert(null, resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPEAT_YES_RESULT_TEXT));
	}			

	private void popup() {
		
		makeGenerateDumpFileButton();
		
		ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
			@Override
			public boolean isValid(Widget byWidget) { 
				this.errorText = null;
				
				if (!submitLinkPressed) { 
					this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NO_SUBMIT_BUG_REPORT_VALIDATION);
				}
				else if (!bugFileGenerated) { 
					this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NO_GENERATED_BUG_FILE_VALIDATION);
				}

				return errorText == null;
			}
		};

		confirmPopupWindow = new ConfirmPopupWindow(true);
		ConfirmationPopup.showDialogConfirm(confirmPopupWindow, validation, null, null, null
        		, resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_TITLE)
        		, ConfirmationPopup.OK_AND_CANCEL_BUTTONS | ConfirmationPopup.EXTRA_BUTTON
        		, generateDumpFileButton, null, null
        		, true, null, ConfirmationPopup.NO_BUTTON, false, this);
	}

	/**
	 * Creates the layout to include in the dialog popup
	 */
	private void initLayout() {
		Label bugReportCheck = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_CHECK));
		Label bugReportBody2 = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_BODY2)
						, resourceManager.getVersionInfo()
						));
		Label bugReportBody3 = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_BODY3));
		Label bugReportPleaseEmail = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_PLEASE_EMAIL_TEXT)
						, resourceManager.getLocalisedString(CoreLocalisation.Key.APP_EMAIL)
						));
		Label bugReportBody4 = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_BODY4));
		bugReportCheck.setTheme("label");
		bugReportBody2.setTheme("label");
		bugReportBody3.setTheme("label");
		bugReportPleaseEmail.setTheme("label");
		bugReportBody4.setTheme("label");

		Button copyToClipboardBtn = GuiUtils.makeCopyToClipButton(resourceManager.getLocalisedString(CoreLocalisation.Key.APP_EMAIL));
		
		Button submitFormButton = makeSubmitFormButton();
		Label submitDesc = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.BUG_REPORT_SUBMIT_DESCRIPTION)
						));
		submitDesc.setTheme("label");

        setHorizontalGroup(createParallelGroup()
           		.addWidget(bugReportCheck)
        		.addGroup(createSequentialGroup().addWidget(submitDesc).addGap(MEDIUM_GAP).addWidget(submitFormButton).addGap())
           		.addWidget(bugReportBody2)
           		.addWidget(bugReportBody3)
           		.addWidget(bugReportBody4)
           		.addGroup(createSequentialGroup().addWidget(bugReportPleaseEmail).addGap(MEDIUM_GAP).addWidget(copyToClipboardBtn).addGap())
        		);
            setVerticalGroup(createSequentialGroup()
            	.addWidget(bugReportCheck)
        		.addGroup(createParallelGroup().addWidget(submitDesc).addWidget(submitFormButton))
            	.addWidget(bugReportBody2)
            	.addGap(LARGE_GAP)
            	.addGap(LARGE_GAP)
        		.addWidget(bugReportBody3)
            	.addGap(LARGE_GAP)
            	.addGap(LARGE_GAP)
           		.addWidget(bugReportBody4)
        		.addGroup(createParallelGroup().addWidget(bugReportPleaseEmail).addWidget(copyToClipboardBtn))
        		);
           
            adjustSize();
	}
	
	private void makeGenerateDumpFileButton() {
		generateDumpFileButton = new Button();
		generateDumpFileButton.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.GENERATE_BUG_REPORT_BUTTON_LABEL));
		generateDumpFileButton.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.GENERATE_BUG_REPORT_BUTTON_TOOLTIP));
		generateDumpFileButton.setTheme("genericBtn");
		generateDumpFileButton.addCallback(new Runnable() {
			@Override
			public void run() {
				bugFileGenerated = true;
				DiagnosticFileCompiler.generateBugReport(resourceManager);
			}			
		});
	}

	private Button makeSubmitFormButton() {
		Button submitFormButton = new Button();
		submitFormButton.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.SUBMIT_BUG_REPORT_BUTTON_LABEL));
		submitFormButton.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.SUBMIT_BUG_REPORT_BUTTON_TOOLTIP));
		submitFormButton.setTheme("genericBtn");
		submitFormButton.addCallback(new Runnable() {
			@Override
			public void run() {
				submitLinkPressed = true;
				UserBrowserProxy.showWebPage(Key.bugReport);
			}			
		});
		return submitFormButton;
	}

}
