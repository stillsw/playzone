package com.stillwindsoftware.keepyabeat.gui;

import java.io.File;
import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup.ConfirmPopupWindow;
import com.stillwindsoftware.keepyabeat.model.transactions.ImportRhythmsCommand;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.utils.ImportRhythm;
import com.stillwindsoftware.keepyabeat.utils.RhythmImporter;
import com.stillwindsoftware.keepyabeat.utils.RhythmSharer;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Widget;

/**
 * Creates a dialog layout which collects a description for including in the data export of rhythms
 * in a confirmation popup. Added extra message if there are non-default sounds in the selection.
 * @author tomas stubbs
 */
public class ImportRhythmsDialog extends DialogLayout {

	private ConfirmPopupWindow confirmPopup;
    private FileSelectorEditField fileSelectorField;
    private File selectedFile;
	
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	/**
	 * Initialise a layout for confirming share (export) rhythms to file
	 */
	public ImportRhythmsDialog() {
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
				, TwlLocalisationKeys.HELP_IMPORT_EXTRA_TEXT
				, true // allow outside click to close it
				, new Runnable() {
						@Override
						public void run() {
							ImportRhythmsDialog.this.requestKeyboardFocus();
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
		Label descLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_LABEL));
		
		// the owner of the file selector is the confirm popup, so declare it here
        confirmPopup = new ConfirmationPopup.ConfirmPopupWindow(true);

        Button helpBtn = makeHelpButton();
        
		fileSelectorField = new FileSelectorEditField(confirmPopup);
		fileSelectorField.setTheme("changeName");
		fileSelectorField.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CHOOSE_IMPORT_RHYTHMS_FILE_TOOLTIP));
//		fileSelectorField.setFocusOnCloseWidget(helpBtn);

        setHorizontalGroup(createParallelGroup()
        		.addGroup(createSequentialGroup().addGap().addWidget(helpBtn))
        		.addGroup(createSequentialGroup(descLabel, fileSelectorField))
        		);
        setVerticalGroup(createSequentialGroup()
        		.addWidget(helpBtn)
        		.addGroup(createParallelGroup(descLabel, fileSelectorField))
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
					if (fileSelectorField.getTextLength() == 0) {
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_RHYTHMS_FILE_NAME);
					}
					
					return errorText == null;
				}
  			};

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
  	            public void callback(ConfirmationPopup.CallbackReason reason) {
  	            	if (reason == ConfirmationPopup.CallbackReason.OK) {

 	            		Runnable importer = new Runnable() {
  	            			@Override
  	            			public void run() {
		  						try {
		  							final RhythmSharer sharer = new RhythmSharer(resourceManager, selectedFile);
		
		  							// ok so far, go ahead and read the file, if abortive read a message has been warned to the user already
		  							if (sharer != null && !sharer.isAbortiveRead(false)) { // interactive mode
		  								// check for warnings, they will have to be displayed as well as showing the rhythms import dialog
		  	  							resourceManager.getGuiManager().runInGuiThread(new Runnable() {
		  									@Override
		  									public void run() {
		  										popupImportRhythms(sharer, sharer.getWarning());
		  									}
		  								});
		  							}
		  						} 
		  						catch (Exception e) {
		  							resourceManager.log(LOG_TYPE.error, this, String.format("ImportRhythmsDialog.popupConfirmation: exception %s", e.getClass()));
		  							e.printStackTrace();
		  							resourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_PROGRAM_ERROR, true, e);
		  						}
  	            			}
  	            		};
  	            		
  	            		// do the import in a non-gui thread
  	            		new Thread(importer).start();  						
  	            	}
  	            }
  	        };

        ConfirmationPopup.showDialogConfirm(confirmPopup, validation, callback, null, 
  	        		null, resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_TITLE),
  	        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null,
  	        		true, null, ConfirmationPopup.NO_BUTTON, false, this);
	}
	
	/**
	 * Called when saved file ok to explain where the file is and what to do with it
	 * @param outputFile
	 */
	public void popupImportRhythms(final RhythmSharer sharer, CoreLocalisation.Key warnKey) {
		ImportRhythmsList impRhythmsList = new ImportRhythmsList(sharer);
		impRhythmsList.init();
		
        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) {
					// set errorText null each time
					errorText = null;
					boolean foundSelected = false;
					
					ArrayList<ImportRhythm> impRhythms = sharer.getRhythmsFromInput();
					for (int i = 0; i < impRhythms.size(); i++) {
						if (impRhythms.get(i).isSelected()) {
							foundSelected = true;
							break;
						}
					}
					
					if (!foundSelected) {
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.VALIDATION_IMPORT_RHYTHMS_NONE_SELECTED);
					}
					
					return errorText == null;
				}
  			};

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
  	            public void callback(ConfirmationPopup.CallbackReason reason) {
  	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
  	            		final RhythmImporter importer = new RhythmImporter(TwlResourceManager.getInstance(), sharer);
  	            		final ImportRhythmsCommand cmd = new ImportRhythmsCommand(TwlResourceManager.getInstance(), importer);
 	            		Runnable importThread = new Runnable() {
  	            			@Override
  	            			public void run() {
  	            				boolean executeFinished = false;
		  						try {
		  							cmd.execute();
		  							executeFinished = true;
		  						} 
		  						catch (Exception e) {
		  							resourceManager.log(LOG_TYPE.error, this, String.format("ImportRhythmsDialog.popupConfirmation: exception %s", e.getClass()));
		  							e.printStackTrace();
		  							resourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_PROGRAM_ERROR, true, e);
		  						}
		  						finally {
		  							// always clear down the resources
		  							String messages = importer.getErrorMessagesAndClearImportData();

		  							// and show the errors
		  							if (executeFinished && messages != null) {
		  								resourceManager.getGuiManager().warnOnErrorMessage(messages
		  										, resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_COMPLETION_ERRORS_TITLE), false, null);
		  							}
		  						}
  	            			}
  	            		};
  	            		
  	            		// do the import in a non-gui thread
  	            		new Thread(importThread).start();  						
  	            	}
  	            	else {
  	            		// user pressed cancel, still need to clear the import data
  	            		sharer.dispose();
  	            	}
  	            }
  	        };

	    ConfirmationPopup.showDialogConfirm(validation, callback, ImportRhythmsDialog.this, 
	    		resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_SELECTION_TITLE), 
	    		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, impRhythmsList);

	    // popup an extra warning over the top of the rhythms
		if (warnKey != null) {
			resourceManager.getGuiManager().warnOnErrorMessage(warnKey, false, null);
		}
	}
	
	/**
	 * File selector widget for rhythm files
	 */
    class FileSelectorEditField extends EditField {
    	
    	private Widget owner;
    	private RhythmsImportFileSelector fileSelector;
    	private Widget focusOnCloseWidget;
    	
    	public FileSelectorEditField(Widget owner) {
    		this.owner = owner;
    	}
    	
		public RhythmsImportFileSelector getFileSelector() {
			return fileSelector;
		}

		public void setFocusOnCloseWidget(Widget focusOnCloseWidget) {
			this.focusOnCloseWidget = focusOnCloseWidget;
		}

		protected void keyboardFocusGained() {
			fileSelector = new RhythmsImportFileSelector(owner, new TwlFileSelector.Callback() {
				private void closeSelector() {
					fileSelector.closePopup();
					if (focusOnCloseWidget != null) {
						focusOnCloseWidget.requestKeyboardFocus();
					}
					else {
						try {
							if (getParent().getParent() instanceof ConfirmationPopup) {
								ConfirmationPopup confPopup = (ConfirmationPopup) getParent().getParent();
								confPopup.focusOnDefaultButton();
							}
						} catch (Exception e) {
							// make sure NPE can't happen, it shouldn't
						}
					}
				}
				@Override
				public void canceled() {
					closeSelector();
				}

				@Override
				public void filesSelected(Object[] files) {
					selectedFile = (File) files[0];
					
					if (selectedFile != null && selectedFile.exists()) {
						setText(selectedFile.getName());
					}
					
					closeSelector();
				}
			});
			fileSelector.openPopup();
		}
	}

}
