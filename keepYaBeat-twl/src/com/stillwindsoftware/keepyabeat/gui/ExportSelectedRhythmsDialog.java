package com.stillwindsoftware.keepyabeat.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.twlModel.RhythmsSearchModel;
import com.stillwindsoftware.keepyabeat.utils.RhythmImporter;
import com.stillwindsoftware.keepyabeat.utils.RhythmSharer;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.TableSelectionModel;

/**
 * Creates a dialog layout which collects a description for including in the data export of rhythms
 * in a confirmation popup. Added extra message if there are non-default sounds in the selection.
 * @author tomas stubbs
 */
public class ExportSelectedRhythmsDialog extends DialogLayout {

	private static final int MAX_EXPORT_RHYTHMS_DESCRIPTION_LEN = 100;
	
	private final TableSelectionModel selectionModel;
	private final RhythmsList rhythmsList;
	
	private EditField userDescription;
	
	private TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	/**
	 * Initialise a layout for confirming share (export) rhythms to file
	 * @param selectionModel
	 * @param rhythmsList
	 */
	public ExportSelectedRhythmsDialog(TableSelectionModel selectionModel, RhythmsList rhythmsList) {
		this.selectionModel = selectionModel;
		this.rhythmsList = rhythmsList;
		setTheme("plainPanelDialogLayout");
		
		initLayout();
		popupConfirmation();
	}

	/**
	 * Show help for this page (there's no auto help for this one)
	 */
	private void showHelp() {
		// send the whole lot to the controller
		new HelpTipsController(UserBrowserProxy.Key.shareImport
				, TwlLocalisationKeys.HELP_SHARER_EXTRA_TEXT
				, true // allow outside click to close it
				, new Runnable() {
						@Override
						public void run() {
							ExportSelectedRhythmsDialog.this.requestKeyboardFocus();
						}
					}
				, (HelpTipModel[]) null);
	}

	private Button makeHelpButton() {
		Button helpBtn = new Button();
		helpBtn.setTheme("panelHelpBtn");			
		helpBtn.setCanAcceptKeyboardFocus(false);
		helpBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						showHelp();
					}			
				});
		helpBtn.setTooltipContent(TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.HELP_BUTTON_TOOLTIP));
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		
		return helpBtn;
	}

	/**
	 * Creates the layout to include in the dialog popup
	 */
	private void initLayout() {
        Button helpBtn = makeHelpButton();

		Label descLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.RHYTHMS_SET_DESCRIPTION_LABEL));
        userDescription = new EditField();
        userDescription.setTheme("changeName");
        userDescription.setMaxTextLength(MAX_EXPORT_RHYTHMS_DESCRIPTION_LEN);

        setHorizontalGroup(createParallelGroup()
        		.addGroup(createSequentialGroup().addGap().addWidget(helpBtn))
        		.addGroup(createSequentialGroup(descLabel, userDescription))
        		);
        setVerticalGroup(createSequentialGroup()
        		.addWidget(helpBtn)
        		.addGroup(createParallelGroup(descLabel, userDescription))
        		);        
	}
	
	/**
	 * Show the dialog to the user to confirm export
	 */
	public void popupConfirmation() {
        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) {
					// set errorText null each time
					errorText = null;
					if (userDescription.getTextLength() == 0) {
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_RHYTHMS_SET_DESCRIPTION);
					}
					
					return errorText == null;
				}
  			};

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
  	            public void callback(ConfirmationPopup.CallbackReason reason) {
  	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
  	            		Runnable exporter = new Runnable() {
  	            			@Override
  	            			public void run() {
	  	  						RhythmSharer sharer = null;
	  	  						try {
	  	  							RhythmsSearchModel rhythmsSearchModel = rhythmsList.getRhythmsSearchModel();
	  	  							int[] selectedRows = selectionModel.getSelection();
	  	  							ArrayList<Rhythm> selectedRhythms = new ArrayList<Rhythm>();
									for (int i = 0; i < selectedRows.length; i++) {
										Rhythm rhythm = rhythmsSearchModel.get(selectedRows[i]);
										selectedRhythms.add(rhythm);
									}

	  	  							sharer = new RhythmSharer(TwlResourceManager.getInstance(), selectedRhythms, userDescription.getText());
	  	  							final String fileName = String.format("kyb rhythms %s."+RhythmImporter.RHYTHMS_FILE_EXTENSION, sharer.getTimestamp());
	  	  							File homeFolder = new File(System.getProperty("user.home"));
	  	  							final File outputFile = new File(homeFolder, fileName);
	  	  							sharer.writeToFile(outputFile);
	  	  							resourceManager.log(LOG_TYPE.info, this, String.format("ExportSelectedRhythmsDialog.popupConfirmation: shared rhythms file written %s", outputFile.getCanonicalPath()));
	  	  							resourceManager.getGuiManager().runInGuiThread(new Runnable() {
										@Override
										public void run() {
			  	  							popupFileSavedOk(fileName);
			  								try {
												resourceManager.getGuiManager().showNotification(String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.SHARE_RHYTHMS_FILE_WRITTEN), outputFile.getCanonicalPath()));
											} catch (IOException e) {
												// if this really fails, don't worry was only an extra notification
				  	  							resourceManager.log(LOG_TYPE.error, this, String.format("ExportSelectedRhythmsDialog.popupConfirmation: exception showing notification %s", e.getClass()));
												e.printStackTrace();
											}
										}
	  	  							});
	  	  						} 
	  	  						catch (Exception e) {
	  	  							resourceManager.log(LOG_TYPE.error, this, String.format("ExportSelectedRhythmsDialog.popupConfirmation: exception encoding %s", e.getClass()));
	  	  							e.printStackTrace();
	  	  							resourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_PROGRAM_ERROR, true, e);
	  	  						}
  	            			}
  	            		};
  	            		
  	            		// do the export in a non-gui thread
  	            		new Thread(exporter).start();
  	            	}
  	            }
  	        };

	    ConfirmationPopup.showDialogConfirm(validation, callback, ExportSelectedRhythmsDialog.this, 
	    		resourceManager.getLocalisedString(TwlLocalisationKeys.EXPORT_RHYTHMS_SET_TITLE), 
	    		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, this);
	}
	
	/**
	 * Called when saved file ok to explain where the file is and what to do with it
	 * @param outputFile
	 */
	public void popupFileSavedOk(String fileName) {
		DialogLayout savedLayout = new DialogLayout();
		savedLayout.setTheme("dialoglayout");
		
		Label fileSavedLabel = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.EXPORT_RHYTHMS_SUCCESS_LABEL)
						, fileName, System.getProperty("user.home")));
		Label pleaseEmailLabel = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.EXPORT_RHYTHMS_EMAIL_IT_LABEL)
						, resourceManager.getLocalisedString(CoreLocalisation.Key.APP_EMAIL)
						));
		Label sendToFriendsLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.EXPORT_RHYTHMS_SEND_TO_FRIENDS_LABEL));
		fileSavedLabel.setTheme("label");
		pleaseEmailLabel.setTheme("label");
		sendToFriendsLabel.setTheme("label");

		Button copyEmailToClipboardBtn = GuiUtils.makeCopyToClipButton(resourceManager.getLocalisedString(CoreLocalisation.Key.APP_EMAIL));
		Button copyKybIntroToClipboardBtn = GuiUtils.makeCopyToClipButton(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.EXPORT_RHYTHMS_SEND_TO_FRIENDS_TEXT)
						, resourceManager.getLocalisedString(TwlLocalisationKeys.KYB_HOME_LINK)
						));

		savedLayout.setHorizontalGroup(savedLayout.createParallelGroup()
           		.addWidget(fileSavedLabel)
        		.addGroup(savedLayout.createSequentialGroup().addWidget(pleaseEmailLabel).addGap(MEDIUM_GAP).addWidget(copyEmailToClipboardBtn).addGap())
        		.addGroup(savedLayout.createSequentialGroup().addWidget(sendToFriendsLabel).addGap(MEDIUM_GAP).addWidget(copyKybIntroToClipboardBtn).addGap())
        		);
		savedLayout.setVerticalGroup(savedLayout.createSequentialGroup()
            	.addWidget(fileSavedLabel)
        		.addGroup(savedLayout.createParallelGroup().addWidget(pleaseEmailLabel).addWidget(copyEmailToClipboardBtn))
        		.addGroup(savedLayout.createParallelGroup().addWidget(sendToFriendsLabel).addWidget(copyKybIntroToClipboardBtn))
        		);
           
		savedLayout.adjustSize();

	    ConfirmationPopup.showDialogConfirm(null, null, ExportSelectedRhythmsDialog.this, 
	    		resourceManager.getLocalisedString(TwlLocalisationKeys.EXPORT_RHYTHMS_SUCCESS_TITLE), 
	    		ConfirmationPopup.OK_BUTTON, null, null, false, savedLayout);

	}
	
}
