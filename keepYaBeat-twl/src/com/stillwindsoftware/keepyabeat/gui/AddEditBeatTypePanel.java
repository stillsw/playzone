package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypeXmlImpl;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlListModel;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.DialogLayout.Group;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.ListModel;
import de.matthiasmann.twl.model.SimpleChangableListModel;
import de.matthiasmann.twl.model.TableSingleSelectionModel;

public class AddEditBeatTypePanel implements Runnable {
	
	
	private EditField beatTypePanelName;
    private ComboBox<SoundXmlImpl> beatTypePanelSoundCombo;
    private ComboBox<SoundXmlImpl> beatTypePanelFallbackSoundCombo;
    protected TableSingleSelectionModel selectionModel;
    private ColourButton colourButton;
    private Widget focusWidget;
    private BeatTypeXmlImpl sourceBeatType;
    
	private ConfirmationPopup.Validation validation;
	private CallbackWithReason<ConfirmationPopup.CallbackReason> callback;
	private boolean adding;
	
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private final Library library;
	private final SoundsXmlImpl sounds;
	private final BeatTypesXmlImpl beatTypes;
    
    private TwlListModel<SoundsXmlImpl, SoundXmlImpl> soundsListModel;
	private ListModel<SoundXmlImpl> internalSoundsListModel;

    /**
     * Called by cloning from the popup menu
     * Use the source to default values for the clone
     * @param sourceBeatType
     */
    AddEditBeatTypePanel(BeatTypeXmlImpl sourceBeatType) {
		this(true, null, null, sourceBeatType);
    }

    /**
	 * validation and callback are slightly different if adding or changing
	 * @param adding
	 */
	AddEditBeatTypePanel(boolean adding, final TableSingleSelectionModel selectionModel, final Widget focusWidget) {
		this(adding, selectionModel, focusWidget, null);
	}
	
	/**
	 * validation and callback are slightly different if adding or changing
	 * @param adding
	 */
	AddEditBeatTypePanel(boolean adding, final TableSingleSelectionModel selectionModel, final Widget focusWidget, final BeatTypeXmlImpl sourceBeatType) {
		library = resourceManager.getLibrary();
		sounds = (SoundsXmlImpl) library.getSounds();
		beatTypes = (BeatTypesXmlImpl) library.getBeatTypes();
		internalSoundsListModel = new SimpleChangableListModel<SoundXmlImpl>(sounds.getInternalSounds());

		this.adding = adding;
		this.selectionModel = selectionModel;
		this.focusWidget = focusWidget;
    	this.sourceBeatType = sourceBeatType;
		
		this.validation = new ConfirmationPopup.Validation() {
			@Override
			public boolean isValid(Widget byWidget) { 
				this.errorText = null;
				BeatTypeXmlImpl changeBeatType = null;
	    		if (!AddEditBeatTypePanel.this.adding) {
	    			changeBeatType = (BeatTypeXmlImpl) beatTypes.get(selectionModel.getSelection()[0]);
	    		}
				
				if (beatTypePanelName.getTextLength() == 0) 
					this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_UNIQUE_NAME);
				else if (beatTypes.isNameInUse(beatTypePanelName.getText(), changeBeatType))
					// when adding or changing a name it must not be same as another
					if (AddEditBeatTypePanel.this.adding || !changeBeatType.getName().equals(beatTypePanelName.getText()))
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_USED);

				return errorText == null;
			}
		};
		
		this.callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
            public void callback(ConfirmationPopup.CallbackReason reason) {
            	soundsListModel.destroy(); // clear the listener
            	if (reason == ConfirmationPopup.CallbackReason.OK) {
            		// all should be valid at this point
        			Sound mainSound = (Sound) sounds.get(beatTypePanelSoundCombo.getSelected());
        			Sound fallbackSound = (Sound) beatTypePanelFallbackSoundCombo.getModel().getEntry(beatTypePanelFallbackSoundCombo.getSelected());
        			
            		if (AddEditBeatTypePanel.this.adding) {
            			CoreLocalisation.Key desc = (sourceBeatType == null ? CoreLocalisation.Key.UNDO_ADD_BEAT_TYPE : CoreLocalisation.Key.UNDO_CLONE_BEAT_TYPE);
            			new BeatTypesAndSoundsCommand.AddBeatType(resourceManager, resourceManager.getLocalisedString(desc), beatTypePanelName.getText()
            					, mainSound , fallbackSound
            					, colourButton.getColour()).execute();
            		}
            		else {
            			BeatType changeBeatType = (BeatType) beatTypes.get(selectionModel.getSelection()[0]);
            			String panelName = beatTypePanelName.getText();
            			boolean nameChanged = !changeBeatType.getName().equals(panelName);
            			boolean soundChanged = !mainSound.equals(changeBeatType.getSound());
            			boolean fallbackSoundChanged = !fallbackSound.equals(changeBeatType.getFallbackSound());
            			int whichParts = BeatTypes.COLOUR_CHANGED;
            			if (nameChanged) whichParts |= BeatTypes.NAME_CHANGED;
            			if (soundChanged) whichParts |= BeatTypes.SOUND_CHANGED;
            			if (fallbackSoundChanged) whichParts |= BeatTypes.FALLBACK_SOUND_CHANGED;
            			new BeatTypesAndSoundsCommand.ChangeBeatNameAndSound(changeBeatType, whichParts, panelName, mainSound, fallbackSound, colourButton.getColour()).execute();
            		}
            		
            		// have parent re-lay it out
            		if (focusWidget != null)
            			focusWidget.invalidateLayout();
            	}
            }
        };
	}
	
	public void run() {
		// don't allow duplicate name
		DialogLayout addChangeBeatTypePanel = new DialogLayout();
		addChangeBeatTypePanel.setTheme("addChangeBeatTypePanel");
		
		// name, label and field
		final Label nameLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_LABEL));
		nameLabel.setTheme("addChangeBeatTypeNameLabel");
		
		beatTypePanelName = new EditField();
		beatTypePanelName.setTheme("addChangeBeatTypeName");
		beatTypePanelName.setMaxTextLength(BeatType.MAX_BEAT_TYPE_NAME_LEN);
		
		// default sound
		final Label soundLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.SOUND_LABEL));
		soundLabel.setTheme("addChangeBeatTypeSoundLabel");    		

		soundsListModel = new TwlListModel<SoundsXmlImpl, SoundXmlImpl>(sounds);
		beatTypePanelSoundCombo = new ComboBox<SoundXmlImpl>(soundsListModel);
		beatTypePanelSoundCombo.setTheme("addChangeBeatTypeSoundField");
		beatTypePanelSoundCombo.setComputeWidthFromModel(true);

		// changing means need the current values
		if (adding) {
			if (sourceBeatType == null) {
				beatTypePanelSoundCombo.setSelected(SoundCombo.getListIndexOf((SoundXmlImpl) sounds.getTick(), soundsListModel));
			}
			else {
				beatTypePanelName.setText(beatTypes.getCloneBeatTypeName(sourceBeatType.getName()));
				beatTypePanelSoundCombo.setSelected(sounds.indexOf((SoundXmlImpl) sourceBeatType.getSound()));    			
			}
		}
		else {
			int beatSelection = selectionModel.getSelection()[0];
			sourceBeatType = (BeatTypeXmlImpl) beatTypes.get(beatSelection);
			beatTypePanelName.setText(sourceBeatType.getName());
			SoundXmlImpl sound = (SoundXmlImpl) sourceBeatType.getSound();
			beatTypePanelSoundCombo.setSelected(SoundCombo.getListIndexOf(sound, soundsListModel));
		}
		
		// fallback sound
		final Label fallbackSoundLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.FALLBACK_SOUND_LABEL));
		fallbackSoundLabel.setTheme("addChangeBeatTypeSoundLabel");
		// tick is default if none defined, also if there's a source beat type, getxx() defaults to same
		SoundXmlImpl fallbackSound = (SoundXmlImpl) (sourceBeatType == null ? sounds.getTick() : sourceBeatType.getFallbackSound());

		beatTypePanelFallbackSoundCombo = new ComboBox<SoundXmlImpl>(internalSoundsListModel);
		beatTypePanelFallbackSoundCombo.setTheme("addChangeBeatTypeFallbackSoundField");
		beatTypePanelFallbackSoundCombo.setComputeWidthFromModel(true);
		beatTypePanelFallbackSoundCombo.setSelected(SoundCombo.getListIndexOf(fallbackSound, internalSoundsListModel));
		
		// set visibility based on main sound
		if (!isFallbackVisible()) {
			fallbackSoundLabel.setVisible(false);
			beatTypePanelFallbackSoundCombo.setVisible(false);
		}
		
		// but change that depending on the main sound
		beatTypePanelSoundCombo.addCallback(new Runnable() {
				@Override
				public void run() {
					// set visibility based on main sound
					boolean fallbackVisible = isFallbackVisible();
					fallbackSoundLabel.setVisible(fallbackVisible);
					beatTypePanelFallbackSoundCombo.setVisible(fallbackVisible);
				}	
			});
		
		// fallback sound
		final Label colourLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.COLOUR_LABEL));
		colourLabel.setTheme("label");
		colourButton = new ColourButton(addChangeBeatTypePanel, false, false);
		colourButton.setTheme("addChangeBeatTypeColour");
		colourButton.setUpdateBeatTypeOnOk(false);
		ColourButton.ColourButtonValue cbValue = new ColourButton.ColourButtonValue();
		cbValue.setValues(Library.NO_SELECTION, sourceBeatType);
        colourButton.setData(cbValue);
				
        Group hGroup = addChangeBeatTypePanel.createParallelGroup()
        	.addGroup(addChangeBeatTypePanel.createSequentialGroup(
				addChangeBeatTypePanel.createParallelGroup(nameLabel, soundLabel, fallbackSoundLabel, colourLabel),
				addChangeBeatTypePanel.createParallelGroup().addWidget(beatTypePanelName)
				.addGroup(addChangeBeatTypePanel.createSequentialGroup(colourButton).addGap())
				.addGroup(addChangeBeatTypePanel.createSequentialGroup(beatTypePanelSoundCombo).addGap())
				.addGroup(addChangeBeatTypePanel.createSequentialGroup(beatTypePanelFallbackSoundCombo).addGap())
     	));

        Group vGroup = addChangeBeatTypePanel.createSequentialGroup()
        	.addGroup(addChangeBeatTypePanel.createParallelGroup(nameLabel, beatTypePanelName))
        	.addGroup(addChangeBeatTypePanel.createParallelGroup(colourLabel, colourButton))
        	.addGroup(addChangeBeatTypePanel.createParallelGroup(soundLabel, beatTypePanelSoundCombo))
        	.addGroup(addChangeBeatTypePanel.createParallelGroup(fallbackSoundLabel, beatTypePanelFallbackSoundCombo))
        	;

        addChangeBeatTypePanel.setHorizontalGroup(hGroup);
        addChangeBeatTypePanel.setVerticalGroup(vGroup);
		
        ConfirmationPopup.showDialogConfirm(validation, callback, focusWidget, 
        		resourceManager.getLocalisedString(adding ? TwlLocalisationKeys.ADD_NEW_BEAT_TYPE_TITLE : TwlLocalisationKeys.CHANGE_BEAT_TYPE_TITLE), 
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, addChangeBeatTypePanel);
	}
	
	private boolean isFallbackVisible() {
		return !sounds.isSystemSound(beatTypePanelSoundCombo.getModel().getEntry(beatTypePanelSoundCombo.getSelected()));
	}
}

