package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;
import java.util.HashMap;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton.ColourButtonValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo.SoundComboValue;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypeXmlImpl;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.LibraryItem;
import com.stillwindsoftware.keepyabeat.model.LibraryListItem;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.RhythmXmlImpl;
import com.stillwindsoftware.keepyabeat.model.RhythmsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.twlModel.BeatTypesTableModel;
import com.stillwindsoftware.keepyabeat.twlModel.DecoratedText;
import com.stillwindsoftware.keepyabeat.twlModel.FilteredListModel;
import com.stillwindsoftware.keepyabeat.twlModel.TwlFilteredListModel;

import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuAction;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.Fixed;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.TableSingleSelectionModel;
import de.matthiasmann.twl.renderer.Font;

public class BeatTypesList extends StandardTableList implements LibraryListener {

    private final BeatTypesXmlImpl beatTypes;

	public BeatTypesList() {
		beatTypes = (BeatTypesXmlImpl) resourceManager.getLibrary().getBeatTypes();
	}
	
	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		resourceManager.getListenerSupport().removeListener(table);
		resourceManager.getListenerSupport().removeListener(this);
	}

	@Override
	protected String getLayoutThemeName() {
		return "beatTypesList";
	}

	@Override
	protected void initTopBox() {
		topBox = new DialogLayout();
		topBox.setTheme("topBox");
	}

	@Override
	protected String getTopboxTitle() {
		return resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_TYPES_TITLE);
	}

	@Override
	protected void initAddButton() {
        // add and change beat are the most complex of the functions and they share nearly all the same things, create the whole panel
        addBtn = addButton("newBtn", TwlLocalisationKeys.ADD_BEAT_TYPE_TOOLTIP, new Runnable() {
				@Override
				public void run() {
					new AddEditBeatTypePanel(true, null, BeatTypesList.this).run(); // adding
				}
	        });        		
	}

	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(ColourButtonValue.class, new ColourButton.ColourButtonCellWidgetCreator(this, false));
        table.registerCellRenderer(SoundComboValue.class, new SoundCombo.SoundComboCellWidgetCreator(this, (TableSingleSelectionModel) selectionModel));
		super.installTableCellRenderers();
	}

	@Override
	protected void initScrollPaneToModel() {
        tableModel = new BeatTypesTableModel();
        table = new SimpleDataSizedTable(tableModel);

        // sounds name changes affect the list layout
        ((SoundsXmlImpl)beatTypes.getLibrary().getSounds()).addListener(this);
        // the table listens to beat types changes already, but for laying out correctly after a change
        // this module also needs to know
        beatTypes.addListener(this);
        ((SoundsXmlImpl)beatTypes.getLibrary().getSounds()).addListener(table);

		initScrollPaneToModel(table, "beatTypesTable", "beatTypesScrollpane");
	}

	@Override
	protected void showPopupMenu(int row) {
		// super-class selects the row
		super.showPopupMenu(row);
		final BeatTypeXmlImpl beatType = beatTypes.get(row);
		if (beatType != null) {
			Menu menu = new Menu();
			menu.setTheme("menumanager");

			// default beat types can't be modified, but can be cloned
			if (beatTypes.isDeletePermitted((BeatType) beatTypes.get(row))) {
				menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_CHANGE_BEAT_TYPE), new Runnable() {
					@Override
					public void run() {
						new AddEditBeatTypePanel(
								false, (TableSingleSelectionModel) selectionModel, BeatTypesList.this).run();
					}
				}));
				
				menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_DELETE_BEAT_TYPE), new Runnable() {
					@Override
					public void run() {
						deleteBeatType(beatType);
					}
				}));
			}
			
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_CLONE_BEAT_TYPE), new Runnable() {
				@Override
				public void run() {
					new AddEditBeatTypePanel(beatType).run();
				}
			}));
			
			doOpenPopupMenu(menu);
		}
	}
	
	/**
	 * Called from the popup menu, if there are references (rhythms) then show a dialog so the user
	 * can select another beat type to replace the deleted one. No references, just go ahead and delete,
	 * undo is always possible.
	 * @param beatType
	 */
	private void deleteBeatType(final BeatType beatType) {
		
        // panel that uses a filter to show any BeatTypes which reference this sound file so they can be dealt with
        final DeleteDialog refRhythms = new DeleteDialog(beatType);

        if (!refRhythms.hasData()) {
            // do the delete command, no fk references so those 2 params are null
    		new BeatTypesAndSoundsCommand.DeleteBeatType(beatType, null, null).execute();
        }
        else {
            ArrayList<Widget> widgets = new ArrayList<Widget>();
            widgets.add(new Label(String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DELETE_PARAM), beatType.getName())));
            
        	widgets.add(new Label(resourceManager.getLocalisedString((TwlLocalisationKeys.DELETE_BEAT_TYPE_REFERENCES))));
        	widgets.add(refRhythms);
            
            ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
    			@Override
    			public boolean isValid(Widget byWidget) {
    				errorText = null;
    				BeatType newBeatType = refRhythms.getSelectedBeatType();
    				if (refRhythms.hasData() && newBeatType == null) {
    					errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.DELETE_BEAT_TYPE_PROMPT_ANOTHER);
    					return false;
    				}
    				else
    					return true;
    			}
    		};
            
    		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
                public void callback(ConfirmationPopup.CallbackReason reason) {
                	if (reason == ConfirmationPopup.CallbackReason.OK) {
                		Rhythm[] changeRhythms = null;
            			BeatType newBeatType = null;
            			
            			// already tested above, so this is always true
                		if (refRhythms.hasData()) {
                			// update the beats to the new sound
                			newBeatType = refRhythms.getSelectedBeatType();
                			FilteredListModel<RhythmXmlImpl> listRhythms = refRhythms.getReferencedRhythms();
                			
                			// have to copy the rhythms out of the filtered list because as they are updated they will
                			// fail to be visible and model changes, so the following for loop is going to miss entries
                			// later in the list 
                			changeRhythms = new RhythmXmlImpl[listRhythms.size()];
                			for (int i = 0; i < listRhythms.size(); i++) {
                				changeRhythms[i] = listRhythms.get(i);
                			}

                		}
                		
                		// do the delete command
                		new BeatTypesAndSoundsCommand.DeleteBeatType(beatType, changeRhythms, newBeatType).execute();
                	}
                	refRhythms.dispose();
            		invalidateLayout();
                }
            };
    				
            ConfirmationPopup.showDialogConfirm(validation, callback, BeatTypesList.this
            		, resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DELETE_TITLE), 
            		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, widgets.toArray(new Widget[widgets.size()]));
        }

	}

	// default is that StandardTableList will compute the dimensions, but this list is laid out by 
	// BeatAndSoundsModule to share with another module, so override this
	@Override
	boolean computeDimensions() {
		return true;
	}

	@Override
	public void itemChanged() {
		scrollPane.invalidateLayout();
		table.invalidateLayout();
		invalidateLayout();
	}


    /**
     */
    class DeleteDialog extends DialogLayout {
    	
        private ScrollPane rhythmsScrollPane;
        private FilteredListModel<RhythmXmlImpl> filteredListModel;
        private SimpleDataSizedTable referencingRhythmsTable;
        private ReferencedRhythmsTableModel rhythmsTableModel;
        private ComboBox<BeatTypeXmlImpl> changeBeatTypeCombo;
        private Label beatTypeLabel;
        private TwlFilteredListModel<BeatTypeXmlImpl> beatTypeComboModel;
        private final RhythmsXmlImpl rhythms;
        
    	DeleteDialog(BeatType deleteBeatType) {
    		rhythms = (RhythmsXmlImpl) resourceManager.getLibrary().getRhythms();
    		
    		setTheme("deleteBeatTypePanel");
    		initModel(deleteBeatType);
    		
    		Group hGroup = createParallelGroup()
            	.addGroup(
    				createParallelGroup().addGroup(createSequentialGroup().addWidget(rhythmsScrollPane)))
    				.addGroup(createParallelGroup().addGroup(createSequentialGroup(beatTypeLabel, changeBeatTypeCombo))
         	);

            Group vGroup = createSequentialGroup()
            	.addGroup(createParallelGroup(rhythmsScrollPane))
            	.addGroup(createParallelGroup().addGroup(createParallelGroup(beatTypeLabel, changeBeatTypeCombo)));
    		
    		if (hasData()) {
        		setHorizontalGroup(hGroup);
        		setVerticalGroup(vGroup);
        	}
    	}
    	
    	public void dispose() {
//    		filteredListModel.removeListener(referencingRhythmsTable);
    		filteredListModel.dispose();
    		rhythmsTableModel.dispose();
    	}
    	
    	@Override
    	public int getMinWidth() {
    		if (referencingRhythmsTable == null || referencingRhythmsTable.getModel() == null)
    			return super.getMinWidth();
    		else
    			return referencingRhythmsTable.getPreferredInnerWidth() + getBorderHorizontal();
    	}

    	@Override
		public int getMaxHeight() {
			return (int)(resourceManager.getGuiManager().getScreenHeight() * .75f);
		}

    	boolean hasData() {
    		return filteredListModel.size() != 0;
    	}
    	
    	public FilteredListModel<RhythmXmlImpl> getReferencedRhythms() {
			return filteredListModel;
		}

		BeatType getSelectedBeatType() {
    		int selection = changeBeatTypeCombo.getSelected();

    		if (selection == Library.NO_SELECTION)
    			return null;
    		else
    			return beatTypeComboModel.getEntry(selection);
    	}
    	
		@SuppressWarnings("unchecked")
		private void initModel(final BeatType deleteBeatType) {
			// filter for the list of references: only want to show beatTypes that are included in a rhythm
			FilteredListModel.Filter<RhythmXmlImpl> filter = new FilteredListModel.Filter<RhythmXmlImpl>() {
				@Override
				public boolean isVisible(LibraryItem rhythm) {
					return ((RhythmXmlImpl)rhythm).includesBeatType(deleteBeatType);
				}
    		};
			
			// filter for the combo list of what beatType to change to, excludes the one being deleted
			TwlFilteredListModel.Filter<BeatTypeXmlImpl> beatTypesFilter = new TwlFilteredListModel.Filter<BeatTypeXmlImpl>() {
				@Override
				public boolean isVisible(LibraryItem beatType) {
					return !deleteBeatType.equals(beatType);
				}
    		};
			
    		filteredListModel = new FilteredListModel<RhythmXmlImpl>(rhythms, filter, false); // don't listen for changes
       		rhythmsTableModel = new ReferencedRhythmsTableModel(filteredListModel);
    		referencingRhythmsTable = new SimpleDataSizedTable(rhythmsTableModel);
	        referencingRhythmsTable.setTheme("rhythmsTable");
	        referencingRhythmsTable.registerCellRenderer(DecoratedText.class, new DecoratedTextRenderer());
       		filteredListModel.addListener(referencingRhythmsTable);
       		
        	beatTypeLabel = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_REFERENCED_BEAT_TYPE_LABEL));
    		beatTypeLabel.setTheme("changeBeatTypeLabel");    		

    		beatTypeComboModel = new TwlFilteredListModel<BeatTypeXmlImpl>((LibraryListItem<? extends LibraryItem>) rhythms.getLibrary().getBeatTypes(), beatTypesFilter);
    		changeBeatTypeCombo = new ComboBox<BeatTypeXmlImpl>(beatTypeComboModel);
    		changeBeatTypeCombo.setTheme("changeBeatTypeCombo");
    		changeBeatTypeCombo.setComputeWidthFromModel(true);
       		
	        rhythmsScrollPane = new ScrollPane(referencingRhythmsTable);
	        rhythmsScrollPane.setTheme("rhythmsScrollpane");
	        rhythmsScrollPane.setFixed(Fixed.HORIZONTAL);
    	}
    	
    	class ReferencedRhythmsTableModel extends DataSizingTableModel implements LibraryListListener {
    		
//    		private static final String RHYTHM_NAME = "Rhythms that use this beat type";
    		private HashMap<Rhythm, DecoratedText> decorations = new HashMap<Rhythm, DecoratedText>();

    		private FilteredListModel<RhythmXmlImpl> rhythmsModel;
    		
    		public ReferencedRhythmsTableModel(FilteredListModel<RhythmXmlImpl> rhythmsModel) {
    			this.rhythmsModel = rhythmsModel;
    			rhythmsModel.addListener(this);
    		}

    		void dispose() {
    			rhythmsModel.removeListener(this);
    		}

    	    public int getNumRows() {
    	        return rhythmsModel.size();
    	    }

    	    public int getNumColumns() {
    	        return 1;
    	    }

    	    public Object getCell(int row, int column) {
    	    	Rhythm rhythm = rhythmsModel.get(row);
    	    	switch (column) {
    	        case 0: {
    	        		String rhythmName = rhythm.getName();
	    	        	DecoratedText dec = decorations.get(rhythm);
	    	        	if (dec == null) {	
	    	        		dec = (DecoratedText) DecoratedText.apply(rhythmName, DecoratedText.ERROR);
	    	        		decorations.put(rhythm, dec);
	    	        	}
		        		return dec;
    	        	}
    	        default: return "OBJECT MISSING FOR "+column;
    	        }
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
				
				for (int i = 0; i < rhythmsModel.size(); i++) {
					maxTextWidth = Math.max(maxTextWidth, 
							useFont.computeTextWidth(rhythmsModel.get(i).getName()));
				}

				return maxTextWidth;
			}
    	}

    }


    /**
     * Access to widget for Help target
     * @return
     */
	public Label getTopboxTitleLabel() {
		return topBoxLabel;
	}


}

    
    



