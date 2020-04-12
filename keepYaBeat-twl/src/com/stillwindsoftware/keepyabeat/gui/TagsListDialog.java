package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup.ConfirmPopupWindow;
import com.stillwindsoftware.keepyabeat.gui.widgets.TagButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.TagButton.TagButtonValue;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.TagXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Tags;
import com.stillwindsoftware.keepyabeat.model.TagsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.TagsCommand;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.twlModel.TagsTableModel;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuAction;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.Fixed;
import de.matthiasmann.twl.Widget;

public class TagsListDialog extends StandardTableList implements LibraryListener {

    private TagsXmlImpl tags;
//    private ConfirmPopupWindow parentPopup;

	// convenience fields
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
    private GuiManager guiManager = resourceManager.getGuiManager();

    public static void popupTagsList(ConfirmPopupWindow parentPopup, Widget focusOnCloseWidget) {
    	
    	// create a tags list widget
    	final TagsListDialog tagsList = new TagsListDialog();
    	tagsList.init();
    	
    	// use parent popup if already in one, otherwise create a new centred popup
    	if (parentPopup == null) {
    		parentPopup = new ConfirmationPopup.ConfirmPopupWindow(KeepYaBeat.root, true, false); // allow to close on outside event or ESC
    	}

//    	tagsList.parentPopup = parentPopup;
    	
    	ConfirmationPopup.showDialogConfirm(parentPopup, null, null, focusOnCloseWidget, null
    			, TwlResourceManager.getInstance()
    					.getLocalisedString(TwlLocalisationKeys.TAGS_MODULE_TITLE)
    			,0 // no buttons
    			, null, null, true, null, ConfirmationPopup.NO_BUTTON, false, tagsList);
    }
    
	public TagsListDialog() {
		tags = (TagsXmlImpl) resourceManager.getLibrary().getTags();
	}
	
	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		resourceManager.getListenerSupport().removeListener((LibraryListener) tableModel);
		resourceManager.getListenerSupport().removeListener(table);
		resourceManager.getListenerSupport().removeListener(this);
	}

	@Override
	protected String getLayoutThemeName() {
		return "tagsList";
	}

	/**
	 * Dialog contained in a confirmation popup, limit size of the list to half the height of the screen
	 */
	@Override
	boolean computeDimensions() {
		setMaxWidth((int)(guiManager.getScreenWidth() * .75f));
		setMaxHeight((int)(guiManager.getScreenHeight() * .5f));

		return super.computeDimensions();
	}

	@Override
	protected void doLayout() {
		// make sure dimensions are computed
		resetDimensionsComputed();
		super.doLayout();
	}

	@Override
	protected void initTopBox() {
		topBox = new DialogLayout();
		topBox.setTheme("topBox");
	}

	@Override
	protected void initAddButton() {
        
		addBtn = addButton("newBtn", TwlLocalisationKeys.ADD_TAG_TOOLTIP, new Runnable() {
				@Override
	        	public void run() {
					final EditField addTagName = new EditField();
	        		addTagName.setTheme("changeName");
	        		addTagName.setMaxTextLength(Tags.MAX_TAG_NAME_LEN);
	        		
	    	        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
							@Override
							public boolean isValid(Widget byWidget) { 
								this.errorText = null;
								
								// byWidget always null
								if (addTagName.getTextLength() == 0) 
									this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_UNIQUE_NAME);
								else if (tags.isNameInUse(addTagName.getText()))  
									this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_USED);

								return errorText == null;
							}
		    			}; 

	    			CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
				            public void callback(ConfirmationPopup.CallbackReason reason) {
				            	if (reason == ConfirmationPopup.CallbackReason.OK) {
			
				            		// all should be valid at this point
				            		new TagsCommand.AddTag(resourceManager, addTagName.getText()).execute();

			            			// redo size so no scrollbars
		    	            		invalidateLayout();
				            	}
				            }
	        	        };

	    	        ConfirmationPopup.showDialogConfirm(validation, callback, TagsListDialog.this, 
	    	        		resourceManager.getLocalisedString(TwlLocalisationKeys.ADD_TAG_TITLE), 
	    	        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, addTagName);
	        	}
	        });        		
	}

	/**
	 * Show help for this page (there's no auto help for this one)
	 */
	private void showHelp() {
		// send the whole lot to the controller
		new HelpTipsController(UserBrowserProxy.Key.tags
				, TwlLocalisationKeys.HELP_TAGS_EXTRA_TEXT
				, true // allow outside click to close it
				, new Runnable() {
						@Override
						public void run() {
							TagsListDialog.this.requestKeyboardFocus();
						}
					}
				, (HelpTipModel[]) null);
	}

	@Override
	protected Button makeHelpButton() {
		Button helpBtn = new Button();
		helpBtn.setTheme("panelHelpBtn");			
		helpBtn.setCanAcceptKeyboardFocus(false);
		helpBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						showHelp();
					}			
				});
		helpBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_BUTTON_TOOLTIP));
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		
		return helpBtn;
	}

	/**
	 * Called from the right-click option menu
	 * @param tag
	 */
	private void editTag(final Tag tag) {

        final EditField editName = new EditField();
        editName.setTheme("changeName");
        editName.setText(tag.getName());
        editName.setMaxTextLength(Tags.MAX_TAG_NAME_LEN);
        editName.setSelection(0, editName.getTextLength());

        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) {
					// set errorText null each time
					errorText = null;
					if (editName.getTextLength() == 0)
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_UNIQUE_NAME);
					else if (tags.isNameInUse(editName.getText(), tag))  
						this.errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_USED);

					return errorText == null;
				}
  			};

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
  	            public void callback(ConfirmationPopup.CallbackReason reason) {
  	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
  	            		new TagsCommand.ChangeTagName(tag, editName.getText()).execute();
            			// redo size so no scrollbars
    	            	invalidateLayout();
  	            	}
  	            }
  	        };

	    ConfirmationPopup.showDialogConfirm(validation, callback, TagsListDialog.this, 
	    		resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_TAG_TITLE), 
	    		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, editName);
	}

	/**
	 * Checks for referenced rhythms, if none goes ahead and deletes
	 * @param tag
	 */
	private void deleteTag(final TagXmlImpl tag) {

		if (tag.getSize() == 0) {
    		new TagsCommand.DeleteTag(tag).execute();			
		}
		else {
			String confirmDeleteTag = String.format(
	        		resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DELETE_PARAM), tag.getName());
	        Label deleteLabel = new Label(confirmDeleteTag);
	        Label deleteWarning = new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.TAG_IS_REFERENCED_WARNING));
	        
			CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
				public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {

	            		new TagsCommand.DeleteTag(tag).execute();

	            		// redo size so no scrollbars
	            		invalidateLayout();
	            	}
	            }
	        };
		        
		    ConfirmationPopup.showDialogConfirm(null, callback, TagsListDialog.this, 
		    		resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DELETE_TITLE), 
		    		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, deleteLabel, deleteWarning);
		}
	}

	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(TagButtonValue.class, new TagButton.TagButtonCellWidgetCreator(table));
	}

	@Override
	protected void initScrollPaneToModel() {
        tableModel = new TagsTableModel();
        table = new SimpleDataSizedTable(tableModel);

		initScrollPaneToModel(table, "tagsTable", "tagsScrollpane");
	}

	/**
	 * Make sure the outer dialog is validated again on layout, so it centres
	 */
	@Override
	protected ScrollPane makeScrollPane(SimpleDataSizedTable table, String scrollPaneTheme) {
        ScrollPane scrollPane = new ScrollPane(table) {

			@Override
			protected void layout() {
				super.layout();
				TagsListDialog.this.invalidateLayout();
//				parentPopup.invalidateLayout();
			}
        	
        };
        scrollPane.setTheme(scrollPaneTheme);
        scrollPane.setFixed(Fixed.HORIZONTAL);
        return scrollPane;
	}

	@Override
	protected void showPopupMenu(int row) {
		// super-class selects the row
		super.showPopupMenu(row);
		final TagXmlImpl tag = tags.get(row);
		if (tag != null) {
			Menu menu = new Menu();
			menu.setTheme("menumanager");

			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_TAG_MENU_OPTION), new Runnable() {
					@Override
					public void run() {
						editTag(tag);
					}
				}));
				
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.DELETE_TAG_MENU_OPTION), new Runnable() {
					@Override
					public void run() {
						deleteTag(tag);
					}
				}));
			
			doOpenPopupMenu(menu);
		}
	}	
	
	@Override
	public void itemChanged() {
		scrollPane.invalidateLayout();
		table.invalidateLayout();
		invalidateLayout();
	}


}

    
    



