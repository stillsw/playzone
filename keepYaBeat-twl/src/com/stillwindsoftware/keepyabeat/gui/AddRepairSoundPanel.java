package com.stillwindsoftware.keepyabeat.gui;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DecimalFormat;

import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup.ConfirmPopupWindow;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.Transaction;
import com.stillwindsoftware.keepyabeat.platform.twl.TestedSoundFile;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlSoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.openal.SilentStartAudio;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.DialogLayout.Group;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Widget;

/**
 * Dialog that allows both adding new sound, and also repair of an existing
 * sound which has an error.
 * @author tomas stubbs
 */
public class AddRepairSoundPanel {

    // add new sound panel variables needed
	private ConfirmPopupWindow confirmPopup;
	private DialogLayout addSoundPanel;
    private EditField addSoundPanelName;
    private Label addSoundPanelDurationField;
    private Button addSoundPanelPlayBtn;
    private SoundFileSelectorEditField fileSelectorField;

	private SoundXmlImpl repairSound;
	private Widget focusWidget;
	private boolean adding = true; // default
	
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();

	private String invalidErrorText;

	/**
	 * Usually this will be for adding a new sound
	 * @param focusWidget
	 */
	public AddRepairSoundPanel(Widget focusWidget) {
		this(null, focusWidget);
	}

	/**
	 * Sound in error that needs repair
	 * @param repairSound
	 * @param focusWidget
	 */
	public AddRepairSoundPanel(SoundXmlImpl repairSound, Widget focusWidget) {
		this.focusWidget = focusWidget;
		initPanel();
		
		if (repairSound != null) {
			this.repairSound = repairSound;
			adding = false;
			
			// preload the widgets from the sound
			addSoundPanelName.setText(repairSound.getName());
			addSoundPanelName.setEnabled(false);
			
			// since the name is disabled, have to cause focus to move explicitly
			fileSelectorField.setFocusOnCancelWidget(addSoundPanelName);
			
			TwlSoundResource soundResource = (TwlSoundResource) repairSound.getSoundResource();
			TestedSoundFile testedFile = soundResource.getTestedSoundFile();
			if (testedFile == null) {
				String decodedFileName = null;
				try {
					decodedFileName = URLDecoder.decode(soundResource.getFileName(), "UTF-8");
				} catch (UnsupportedEncodingException e) {
					decodedFileName = soundResource.getFileName();
				}
				fileSelectorField.setText(decodedFileName);
				invalidErrorText = resourceManager.getLocalisedString(TwlLocalisationKeys.SOUND_FILE_NOTFOUND_ERROR);
			}
			else {
				fileSelectorField.setText(testedFile.getFile().getAbsolutePath());
				if (testedFile.isValid() && resourceManager.isSoundFileInLibrary(testedFile.getFile())) {
					invalidErrorText = resourceManager.getLocalisedString(
							TwlLocalisationKeys.SELECT_SOUND_DUPLICATE_ERROR);
				}
				else {
					invalidErrorText = testedFile.getInvalidReason();
				}
			}
		}
		
		showDialog();
	}

	private void showDialog() {
        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) { 
					boolean ret = false;
					this.errorText = null;
					
					// byWidget would be the fileSelector so just do that one piece
					if (adding && byWidget == null && addSoundPanelName.getTextLength() == 0) 
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_UNIQUE_NAME);
					else if (adding && byWidget == null && ((SoundsXmlImpl)resourceManager.getLibrary().getSounds()).isNameInUse(addSoundPanelName.getText()))  
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_USED);
					else if (fileSelectorField.getTextLength() > 0 && fileSelectorField.getFileSelector() != null) {
						TestedSoundFile testedFile = fileSelectorField.getFileSelector().getTestedSoundFile();
						if (testedFile.isValid()) 
							ret = true;
						else
							this.errorText = testedFile.getInvalidReason();
					}
					else {
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.SELECT_FILE_ERROR);
					}
					return ret;
				}
			}; 
			
		// repair will mean there's already an error text
		if (!adding && invalidErrorText != null) {
			validation.setErrorText(invalidErrorText);
		}

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
	            public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
	            		// all should be valid at this point
	            		TestedSoundFile testedFile = fileSelectorField.getFileSelector().getTestedSoundFile();
	            		
	            		if (adding) {
		            		testedFile.setName(addSoundPanelName.getText());
		            		new BeatTypesAndSoundsCommand.AddCustomSound(resourceManager.getLibrary(), testedFile).execute();
	            		}
	            		else if (repairSound != null) {
	            			// must be true, but anyway test it
	            			TwlSoundResource soundResource = (TwlSoundResource) repairSound.getSoundResource();
	            			soundResource.setLoadError(null);
	            			soundResource.setSoundFile(testedFile.getFile());
	            			soundResource.setAudioEffect(testedFile.getAudio());
	            			soundResource.setStatus(SoundStatus.LOADED_OK);
	            			Transaction.saveTransaction(resourceManager, resourceManager.getLibrary().getSounds());
	            		}
	            		
            			// redo size of calling widget
	            		focusWidget.invalidateLayout();
	            	}
	            }
	        };

        ConfirmationPopup.showDialogConfirm(confirmPopup, validation, callback, focusWidget, 
        		fileSelectorField, resourceManager.getLocalisedString(
        				(adding ? TwlLocalisationKeys.ADD_NEW_SOUND_TITLE : TwlLocalisationKeys.REPAIR_SOUND_TITLE)),
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null,
        		true, null, ConfirmationPopup.NO_BUTTON, false, addSoundPanel);

	}

	/**
	 * Create the panel widgets
	 */
	private void initPanel() {
		// don't allow duplicate name or filename
		addSoundPanel = new DialogLayout();
		addSoundPanel.setTheme("addSoundPanel");
		
		final Label nameLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.SOUND_NAME_LABEL));
		nameLabel.setTheme("addSoundPanelNameLabel");
		
		addSoundPanelName = new EditField();
		addSoundPanelName.setTheme("addSoundPanelName");
		addSoundPanelName.setMaxTextLength(Sounds.MAX_SOUND_NAME_LEN);
		
		final Label fileLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.FILE_LABEL));
		fileLabel.setTheme("addSoundPanelFileLabel");

		// fileSelectorField needs to use playBtn and durationField
		final Label durationLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.DURATION_LABEL));
		durationLabel.setTheme("addSoundPanelDurationLabel");
		
		addSoundPanelDurationField = new Label();
		addSoundPanelDurationField.setTheme("addSoundPanelDurationField");
		addSoundPanelDurationField.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.DURATION_TOOLTIP));
		
		addSoundPanelPlayBtn = new Button();
		addSoundPanelPlayBtn.setTheme("addSoundPanelPlayButton");
		addSoundPanelPlayBtn.setEnabled(false);
		addSoundPanelPlayBtn.setCanAcceptKeyboardFocus(false);

		// the owner of the file selector is the confirm popup, so declare it here
        confirmPopup = new ConfirmationPopup.ConfirmPopupWindow(true);

		fileSelectorField = new SoundFileSelectorEditField(confirmPopup);
		fileSelectorField.setTheme("addSoundPanelFileField");
		fileSelectorField.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CHOOSE_SOUND_FILE_TOOLTIP));
		
		// play the button needs file selector
		addSoundPanelPlayBtn.addCallback(new Runnable() {
			public void run() {
				TestedSoundFile testedFile = fileSelectorField.getFileSelector().getTestedSoundFile();
        		SilentStartAudio audioEffect = testedFile.getAudio();
        		// play as a one off sound effect
        		if (audioEffect != null) { 
    				audioEffect.playSoon(1.0f, 1.0f, Sounds.LEFT_PAD_SILENT_MILLIS / (float)BeatTracker.MILLIS_PER_SECOND);
        		}
			}
		});
		
        Group hGroup = addSoundPanel.createParallelGroup()
        	.addGroup(addSoundPanel.createSequentialGroup(
				addSoundPanel.createParallelGroup(nameLabel, fileLabel, durationLabel),
				addSoundPanel.createParallelGroup(addSoundPanelName, fileSelectorField)
					.addGroup(addSoundPanel.createSequentialGroup().addWidget(addSoundPanelDurationField).addGap().addWidget(addSoundPanelPlayBtn).addGap()
        												)
        	));

        Group vGroup = addSoundPanel.createSequentialGroup()
        	.addGroup(addSoundPanel.createParallelGroup(nameLabel, addSoundPanelName))
        	.addGroup(addSoundPanel.createParallelGroup(fileLabel, fileSelectorField))
        	.addGroup(addSoundPanel.createParallelGroup(durationLabel)
    			.addGroup(addSoundPanel.createParallelGroup().addWidget(addSoundPanelDurationField).addWidget(addSoundPanelPlayBtn)));

        addSoundPanel.setHorizontalGroup(hGroup);
        addSoundPanel.setVerticalGroup(vGroup);
		
	}

	/**
	 * File selector widget for sound files
	 */
    class SoundFileSelectorEditField extends EditField implements ValidatingWidget {
    	
    	private Widget owner;
    	private LoadSoundFileSelector fileSelector;
    	private ConfirmationPopup confirmDlg;
    	private Widget focusOnCancelWidget;
    	
    	public SoundFileSelectorEditField(Widget owner) {
    		this.owner = owner;
    	}
    	
		public LoadSoundFileSelector getFileSelector() {
			return fileSelector;
		}

		public void setConfirmationDialog(ConfirmationPopup confirmDlg) {
			this.confirmDlg = confirmDlg;
		}

		public void setFocusOnCancelWidget(Widget focusOnCancelWidget) {
			this.focusOnCancelWidget = focusOnCancelWidget;
		}

		protected void keyboardFocusGained() {
			fileSelector = new LoadSoundFileSelector(owner, new LoadSoundFileSelector.SoundFileCallback() {
				@Override
				public void canceled() {
					if (focusOnCancelWidget != null) {
						focusOnCancelWidget.requestKeyboardFocus();
					}
					else {
						getParent().focusNextChild();
					}
				}

				@Override
				public void filesSelected(Object[] files) {
					TestedSoundFile testedFile = fileSelector.getTestedSoundFile();
					setText(testedFile.getFile().getAbsolutePath());
					
					// extra validation that the file isn't already in our library
					if (testedFile.isValid() && resourceManager.isSoundFileInLibrary(testedFile.getFile())) {
						testedFile.setValid(false);
						testedFile.setInvalidReason(resourceManager.getLocalisedString(
								TwlLocalisationKeys.SELECT_SOUND_DUPLICATE_ERROR));
					}
					
					if (testedFile.isValid()) {
						setText(testedFile.getFile().getAbsolutePath());
						addSoundPanelPlayBtn.setEnabled(true);
						DecimalFormat df = new DecimalFormat("0.###");
						addSoundPanelDurationField.setText(df.format(testedFile.getDuration()));
						addSoundPanelPlayBtn.requestKeyboardFocus();
					}
					else {
						addSoundPanelPlayBtn.setEnabled(false);
						addSoundPanelDurationField.setText("");
						addSoundPanelName.requestKeyboardFocus();
					}
					if (confirmDlg != null)
						confirmDlg.triggerValidation(SoundFileSelectorEditField.this);
				}
			});
			fileSelector.openPopup();
		}
	}

}
