package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;
import java.util.HashMap;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.PlayButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.PlayButton.PlayButtonValue;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypeXmlImpl;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.LibraryItem;
import com.stillwindsoftware.keepyabeat.model.LibraryListItem;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.Transaction;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.twlModel.DecoratedText;
import com.stillwindsoftware.keepyabeat.twlModel.FilteredListModel;
import com.stillwindsoftware.keepyabeat.twlModel.SoundsTableModel;
import com.stillwindsoftware.keepyabeat.twlModel.TwlFilteredListModel;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuAction;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.Fixed;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.TableSingleSelectionModel;
import de.matthiasmann.twl.renderer.Font;

/**
 * A top level module that acts as a child module most of the time, but in a situation such as small resolution where
 * can't share with beat types it behaves more like a top level module that can swap in and out
 * @author tomas
 *
 */
public class SoundsList extends StandardTableList implements LibraryListener {
	
	// convenience fields
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
    private SoundsXmlImpl sounds = (SoundsXmlImpl) resourceManager.getLibrary().getSounds();
	private BeatTypesXmlImpl beatTypes = (BeatTypesXmlImpl) resourceManager.getLibrary().getBeatTypes();

    /**
     * Private access
     */
	public SoundsList() {
		// make sure layout happens if beat types changes (could make more room for this list)
        beatTypes.addListener(this);
	}
	
	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		sounds.removeListener(table);
		beatTypes.removeListener(this);
	}

	@Override
	protected void showPopupMenu(int row) {
		// super-class selects the row
		super.showPopupMenu(row);
		final SoundXmlImpl sound = sounds.get(row);
		if (sound != null) {
			Menu menu = new Menu();
			menu.setTheme("menumanager");

			// default sounds can't be modified
			if (sounds.isChangePermitted(sound)) {
				if (sound.getSoundResource().getStatus() == SoundStatus.LOADED_OK) {
					menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_CHANGE_SOUND_NAME), new Runnable() {
						@Override
						public void run() {
							editSound(sound);
						}
					}));
				}
				else {
					menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_REPAIR_SOUND), new Runnable() {
						@Override
						public void run() {
							repairSound(sound);
						}
					}));
				}
				
				menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_DELETE_SOUND), new Runnable() {
					@Override
					public void run() {
						deleteSound(sound);
					}
				}));
				
				doOpenPopupMenu(menu);
			}
		}
	}

	@Override
	protected void initTopBox() {
		topBox = new DialogLayout();
		topBox.setTheme("topBox");
	}

	@Override
	protected String getTopboxTitle() {
		return resourceManager.getLocalisedString(TwlLocalisationKeys.SOUNDS_TITLE);
	}

	@Override
	protected void initAddButton() {
        
        // add new sound is the most complex of the functions, create the whole panel
        addBtn = addButton("newBtn", TwlLocalisationKeys.ADD_SOUND_TOOLTIP, new Runnable() {
        	public void run() {
        		invokeAddSoundPanel();
        	}
        });
	}

	private void invokeAddSoundPanel() {
		new AddRepairSoundPanel(this);
	}
	
	/**
	 * Check again if sound file can be loaded, if it can refresh it, if not popup a dialog so a
	 * sound file can be set to it
	 * @param sound
	 */
	private void repairSound(SoundXmlImpl sound) {
		// try first to refresh the sound
		SoundResource soundResource = sound.getSoundResource();
		boolean needsRepair = soundResource != null && soundResource.getStatus() != SoundStatus.LOADED_OK; 
		if (needsRepair) {
			if (soundResource != null) {
				needsRepair = !soundResource.attemptRepair();
			}
		}
		
		// still not fixed
		if (needsRepair) {
			new AddRepairSoundPanel(sound, this);
		}
		else if (soundResource != null){
			// fixed, so reset the sound and propagate through listeners
			soundResource.setStatus(SoundStatus.LOADED_OK);
			Transaction.saveTransaction(resourceManager, sounds);
		}
	}	
	
	/**
	 * Only the name is editable
	 * @param sound
	 */
	private void editSound(final SoundXmlImpl sound) {
		// change the name
        final EditField editName = new EditField();
        editName.setTheme("changeName");
        editName.setText(sound.getName());
        editName.setMaxTextLength(Sounds.MAX_SOUND_NAME_LEN);
        editName.setSelection(0, editName.getTextLength());

        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) {
					// set errorText null each time
					errorText = null;
					if (editName.getTextLength() == 0)
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_NEW_NAME);
					else if (sounds.isNameInUse(editName.getText(), sound))  
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_USED);

					return errorText == null;
				}
			};

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
	            public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
	            		new BeatTypesAndSoundsCommand.ChangeSoundName(sound, editName.getText()).execute();
            			// redo size so no scrollbars
	            		invalidateLayout();
	            	}
	            }
	        };

        ConfirmationPopup.showDialogConfirm(validation, callback, SoundsList.this, 
        		resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_SOUND_NAME_TITLE), 
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, editName);
	}
			        
	private void deleteSound(final Sound sound) {
        // panel that uses a filter to show any BeatTypes which reference this sound file so they can be dealt with
        final DeleteDialog refBeatTypes = new DeleteDialog(sound);
        if (!refBeatTypes.hasData()) {
        	// no references so just go ahead and delete, it's undoable
    		new BeatTypesAndSoundsCommand.DeleteSound(sound, null, null, null).execute();
        }
        else {
        	// pop up the confirm dialog to change references
            ArrayList<Widget> widgets = new ArrayList<Widget>();
            widgets.add(new Label(String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DELETE_PARAM)
            		, sound.getName())));
        	widgets.add(new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.DELETE_SOUND_REFERENCES)));
        	widgets.add(refBeatTypes);

	        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) {
					errorText = null;
					Sound newSound = refBeatTypes.getSelectedSound();
					if (refBeatTypes.hasData() && newSound == null) {
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.SELECT_SOUND_ERROR);
						return false;
					}
					else
						return true;
				}
			};
			
			CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
				public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
	            		// the delete logic is implemented in an undoable command, however if hard-deletes (not default)
	            		// won't actually put the command on the execution stack
            			BeatType[] changeBeatTypes = null;
            			Sound newSound = null;
	            		if (refBeatTypes.hasData()) {
	            			// update the beats to the new sound
	            			newSound = refBeatTypes.getSelectedSound();
	            			FilteredListModel<BeatTypeXmlImpl> listBeatTypes = refBeatTypes.getReferencedBeatTypes();
	            			
	            			// have to copy the beats out of the filtered list because as they are updated they will
	            			// fail to be visible and model changes, so the following for loop is going to miss entries
	            			// later in the list 
	            			changeBeatTypes = new BeatType[listBeatTypes.size()];
	            			for (int i = 0; i < listBeatTypes.size(); i++) {
	            				changeBeatTypes[i] = listBeatTypes.get(i);
	            			}
	            		}

	            		// do the delete command, if no fk references those 2 params are null
	            		//TODO this is actually not correct, there may be referenced rhythms that are not in 
	            		// any beat types... this is correct in the android version
	            		new BeatTypesAndSoundsCommand.DeleteSound(sound, null, changeBeatTypes, newSound).execute();

            			// redo size so no scrollbars
	            		invalidateLayout();
	            	}
	            	refBeatTypes.dispose();
	            }
	        };
	        
	        ConfirmationPopup.showDialogConfirm(validation, callback, SoundsList.this, 
	        		resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DELETE_TITLE)
	        		, ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, widgets.toArray(new Widget[widgets.size()]));
        }
	}

	// default is that StandardTableList will compute the dimensions, but this list is laid out by 
	// BeatAndSoundsModule to share with another module, so override this
	@Override
	boolean computeDimensions() {
		return true;
	}

	@Override
	protected void initScrollPaneToModel() {
        tableModel = new SoundsTableModel();
        table = new SimpleDataSizedTable(tableModel);
        
        initScrollPaneToModel(table, "soundsTable", "soundsScrollpane");

	}
	
    
    /**
     *
     */
    class DeleteDialog extends DialogLayout {
    	
        private ScrollPane beatTypesScrollPane;
        private FilteredListModel<BeatTypeXmlImpl> filteredListModel;
        private SimpleDataSizedTable referencingBeatTypesTable;
        private ReferencedBeatTypesTableModel typesTableModel;
        private ComboBox<SoundXmlImpl> changeSoundCombo;
        private Label soundLabel;
        private TwlFilteredListModel<SoundXmlImpl> soundComboModel;
        
    	DeleteDialog(Sound deleteSound) {
    		
    		setTheme("deleteSoundPanel");
    		initModel(deleteSound);
    		
    		Group hGroup = createParallelGroup()
            	.addGroup(
    				createParallelGroup().addGroup(createSequentialGroup().addWidget(beatTypesScrollPane)))
    				.addGroup(createParallelGroup().addGroup(createSequentialGroup(soundLabel, changeSoundCombo))
         	);

            Group vGroup = createSequentialGroup()
            	.addGroup(createParallelGroup(beatTypesScrollPane))
            	.addGroup(createParallelGroup().addGroup(createParallelGroup(soundLabel, changeSoundCombo)));
    		
    		if (hasData()) {
        		setHorizontalGroup(hGroup);
        		setVerticalGroup(vGroup);
        	}
    	}
    	
    	public void dispose() {
    		filteredListModel.removeListener(referencingBeatTypesTable);
    		filteredListModel.dispose();
    		typesTableModel.dispose();
    	}
    	
    	@Override
    	public int getMinWidth() {
    		if (referencingBeatTypesTable == null || referencingBeatTypesTable.getModel() == null)
    			return super.getMinWidth();
    		else
    			return referencingBeatTypesTable.getPreferredInnerWidth() + getBorderHorizontal();
    	}

    	@Override
		public int getMaxHeight() {
			return (int)(resourceManager.getGuiManager().getScreenHeight() * .75f);
		}

		boolean hasData() {
    		return filteredListModel.size() != 0;
    	}
    	
    	public FilteredListModel<BeatTypeXmlImpl> getReferencedBeatTypes() {
			return filteredListModel;
		}

		public Sound getSelectedSound() {
    		int selection = changeSoundCombo.getSelected();

    		if (selection == Library.NO_SELECTION)
    			return null;
    		else
    			return soundComboModel.getEntry(selection);
    	}
    	
		@SuppressWarnings("unchecked")
		private void initModel(final Sound deleteSound) {
			// filter for the list of references: only want to show beats that use the sound
			FilteredListModel.Filter<BeatTypeXmlImpl> filter = new FilteredListModel.Filter<BeatTypeXmlImpl>() {
				@Override
				public boolean isVisible(LibraryItem beatType) {
					return deleteSound.equals(((BeatTypeXmlImpl) beatType).getSound());
				}
    		};
			
			// filter for the combo list of what sound to change to, excludes the one being deleted
    		TwlFilteredListModel.Filter<SoundXmlImpl> soundsFilter = new TwlFilteredListModel.Filter<SoundXmlImpl>() {
				@Override
				public boolean isVisible(LibraryItem sound) {
					return !deleteSound.equals(sound);
				}
    		};
			
    		filteredListModel = new FilteredListModel<BeatTypeXmlImpl>((LibraryListItem<? extends LibraryItem>) sounds.getLibrary().getBeatTypes(), filter);
       		typesTableModel = new ReferencedBeatTypesTableModel(filteredListModel);
    		referencingBeatTypesTable = new SimpleDataSizedTable(typesTableModel);
	        referencingBeatTypesTable.setTheme("beatTypesTable");
	        referencingBeatTypesTable.registerCellRenderer(DecoratedText.class, new DecoratedTextRenderer());
	        filteredListModel.addListener(referencingBeatTypesTable);
       		
        	soundLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_SOUND_REFS_LABEL));
    		soundLabel.setTheme("changeSoundLabel"); 

    		soundComboModel = new TwlFilteredListModel<SoundXmlImpl>(sounds, soundsFilter);
    		changeSoundCombo = new ComboBox<SoundXmlImpl>(soundComboModel);
    		changeSoundCombo.setTheme("changeSoundCombo");
    		changeSoundCombo.setComputeWidthFromModel(true);
       		
	        beatTypesScrollPane = new ScrollPane(referencingBeatTypesTable);
	        beatTypesScrollPane.setTheme("beatTypesScrollpane");
	        beatTypesScrollPane.setFixed(Fixed.HORIZONTAL);
    	}
    	
    	class ReferencedBeatTypesTableModel extends DataSizingTableModel implements LibraryListListener {
    		
    		private HashMap<BeatType, DecoratedText> decorations = new HashMap<BeatType, DecoratedText>();

    		private FilteredListModel<BeatTypeXmlImpl> beatTypesModel;
    		
    		public ReferencedBeatTypesTableModel(FilteredListModel<BeatTypeXmlImpl> beatTypesModel) {
    			this.beatTypesModel = beatTypesModel;
    			beatTypesModel.addListener(this);
    		}

    		void dispose() {
    			beatTypesModel.removeListener(this);
    		}

    	    public int getNumRows() {
    	        return beatTypesModel.size();
    	    }

    	    public int getNumColumns() {
    	        return 1;
    	    }

    	    public Object getCell(int row, int column) {
    	    	BeatType beatType = beatTypesModel.get(row);
    	    	switch (column) {
    	        case 0: {
	    	        	DecoratedText dec = decorations.get(beatType);
	    	        	if (dec == null) {	
	    	        		dec = (DecoratedText) DecoratedText.apply(
	    	        				beatType.getName(), DecoratedText.ERROR);
	    	        		decorations.put(beatType, dec);
	    	        	}
		        		return dec;
    	        	}
    	        default: return "OBJECT MISSING FOR "+column;
    	        }
    	    }

    	    @Override
    	    public Object getTooltipContent(int row, int column) {
    	    	return null;
    	    }

			@Override
			public void itemChanged() {
    			this.fireAllChanged();
			}

			@Override
			public boolean isColumnDataSized(int column) {
				return true;
			}

			@Override
			public int getColumnTextWidth(int column, Font useFont) {
				int maxTextWidth = 0;
				
				for (int i = 0; i < beatTypesModel.size(); i++) {
					maxTextWidth = Math.max(maxTextWidth, 
							useFont.computeTextWidth(beatTypesModel.get(i).getName()));
				}

				return maxTextWidth;
			}
    	}

    }

	@Override
	protected String getLayoutThemeName() {
		return "soundsList";
	}

	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(PlayButtonValue.class, new PlayButton.PlayButtonCellWidgetCreator((TableSingleSelectionModel) selectionModel));
		super.installTableCellRenderers();
	}

	@Override
	public void itemChanged() {
		invalidateLayout();
	}

	/**
	 * Allow access to add button for help target
	 * @return
	 */
	public Button getAddButton() {
		return addBtn;
	}

    /**
     * Access to widget for Help target
     * @return
     */
	public Label getTopboxTitleLabel() {
		return topBoxLabel;
	}
}


