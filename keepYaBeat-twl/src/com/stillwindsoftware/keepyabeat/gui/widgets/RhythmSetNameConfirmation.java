package com.stillwindsoftware.keepyabeat.gui.widgets;

import java.util.ArrayList;

import org.lwjgl.input.Mouse;

import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup;
import com.stillwindsoftware.keepyabeat.gui.TagsFlowList;
import com.stillwindsoftware.keepyabeat.gui.TagsListDialog;
import com.stillwindsoftware.keepyabeat.gui.TagsPickList;
import com.stillwindsoftware.keepyabeat.gui.TagsPickList.PickedTagReceiver;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.RhythmsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.Tags;
import com.stillwindsoftware.keepyabeat.model.TagsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor.RhythmNewSaveEditCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor.RhythmSaveCommand;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Widget;

/**
 * Show a dialog to set/change a rhythm's name and tags.
 * Called by rhythms list from popup menu, and from player module when pressing save on a new rhythm
 * and from edit rhythm when saving changes (from sub-class, since needs some extra processing to 
 * also save the other data that is in the edit rhythm)
 * @author tomas stubbs
 */
public class RhythmSetNameConfirmation extends NameAndTagsBox implements PickedTagReceiver, LibraryListener {

	protected ArrayList<Tag> chosenTags;
	protected Rhythm rhythm;
	private Widget caller;
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	public RhythmSetNameConfirmation(final Rhythm rhythm, Widget caller) {
		this.rhythm = rhythm;
		this.caller = caller;
        resourceManager.getListenerSupport().addListener(resourceManager.getLibrary().getTags(), this);
	}

	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		super.beforeRemoveFromGUI(gui);
		resourceManager.getListenerSupport().removeListener(this);
	}

	@Override
	public void itemChanged() {
		// tags changed, test which
		Tags tags = resourceManager.getLibrary().getTags();
		
		for (int i = chosenTags.size()-1; i >= 0; i--) {
			Tag tag = chosenTags.get(i);

			if (tags.lookup(tag.getKey()) == null) {
				// deleted
				tagsList.tagRemoved(tag);
			}
			else {
				// act as though name changed (ie. change all of them)
				tagsList.tagChanged(tag);
			}
		}		
	}

	@Override
	public void tagPicked(final Tag tag) {
		chosenTags.add(tag);
		tagsList.addTag(tag);

		((TagsFlowList)tagsList).invalidateLayout();
		tagsListScrollPane.invalidateLayout();
		invalidateLayout();
	}
	
	@Override
	protected void doLayout() {
		((TagsFlowList)tagsList).adjustSize();
		tagsListScrollPane.adjustSize();
		super.doLayout();
		adjustScrollPaneSize();
	}

	/** 2 problems here
	 * a. for some reason after deleting tags, occasionally the scroll pane is left with empty space
	 * b. when adding, the tags list is always 2 pixels higher than the scroll pane, and this isn't the case on delete
	 * it's a bit of a hack, but just adjust scroll pane to same size as tags list up to max height 
	 * the invalidateLayout() after just tells this class to do another layout next time so it adjusts 
	 * again to the resized scroll pane
	 */
	protected void adjustScrollPaneSize() {
		int tagsListHeight = ((TagsFlowList)tagsList).getHeight();
		int scrollHeight = tagsListScrollPane.getHeight();
		
		if (scrollHeight != tagsListHeight
				&& tagsListHeight >= tagsListScrollPane.getMinHeight() 
				&& tagsListHeight <= tagsListScrollPane.getMaxHeight()) {
			// take care that each condition can only be true until it's correction is made, otherwise the invalidateLayout()
			// causes a loop. eg. if first condition is true, there are no tags then set to min height, so next time
			// in, it won't be true again, because the && will fail
			if (tagsListHeight < tagsListScrollPane.getMinHeight() && scrollHeight != tagsListScrollPane.getMinHeight()) {
				tagsListScrollPane.setSize(tagsListScrollPane.getWidth(), tagsListScrollPane.getMinHeight());
				invalidateLayout();
			}
			else if (tagsListHeight > tagsListScrollPane.getMaxHeight() && scrollHeight != tagsListScrollPane.getMaxHeight()) {
				tagsListScrollPane.setSize(tagsListScrollPane.getWidth(), tagsListScrollPane.getMaxHeight());
				invalidateLayout();
			}
			else {
				tagsListScrollPane.setSize(tagsListScrollPane.getWidth(), tagsListHeight);
				invalidateLayout();
			}
		}
	}
	
	@Override
	public TagsPickList getTagsPickList() {
		return new TagsPickList(getTagsNotSelected(rhythm), this);
	}

	/**
	 * Compare all tags with the ones selected already and return the set remaining
	 * @param rhythm
	 * @return
	 */
	private ArrayList<Tag> getTagsNotSelected(Rhythm rhythm) {
		
		// the list to return is all tags except those in the chosen list
		ArrayList<Tag> showTags = new ArrayList<Tag>();
		showTags.addAll(((TagsXmlImpl)rhythm.getLibrary().getTags()).getRawDataList());
		showTags.removeAll(chosenTags);
		
		return showTags;
	}
	
	@Override
	protected String getThemeName() {
		return "setNameTagsPopupBox";
	}

	@Override
	protected String getTagsSearchLabelText() {
		return resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_ADD_TO_RHYTHM_LABEL);
	}

	@Override
	public void initTagsList() {
		tagsList = new TagsFlowList(chosenTags) {
			@Override
			protected String getButtonTheme() {
				return "selectedTagBtn";
			}

			@Override
			protected void showEmptyLabel() {
				if (getChildIndex(tagsSearchLabel) == -1) {
					add(tagsSearchLabel);				
				}
			}

			@Override
			protected void removeEmptyLabel() {
				if (getChildIndex(tagsSearchLabel) != -1) {
					removeChild(tagsSearchLabel);				
				}
			}

			@Override
			protected Runnable makeTagButtonClickedCallback(final TagListButton tagButton, final Tag tag) {
				return new Runnable() {
						@Override
						public void run() {
							chosenTags.remove(tag);
							tagsList.removeTagButton(tagButton);
							invalidateLayout();
							RhythmSetNameConfirmation.this.invalidateLayout();
						}			
					};
			}
			
		};
		
		((TagsFlowList)tagsList).setTheme("tagsListFlow");
	}

	/**
	 * Set name on the rhythm if confirmed
	 */
	public void saveChangesIfConfirmed() {
		// have to have chosen tags set up before calling init() as this method
		// will initialise the flow list with the chosen tags
		chosenTags = new ArrayList<Tag>();	
		init(false, this);		
		
		// just mimic picking each one, easiest way to do it
		ArrayList<Tag> rhythmTags = rhythm.getLibrary().getTags().getRhythmTags(rhythm);		
		for (int i = 0; i < rhythmTags.size(); i++) {
			tagPicked(rhythmTags.get(i));
		}

		// now prefill the fields
		boolean isSavedRhythm = rhythm.getLibrary().getRhythms().lookup(rhythm.getKey()) != null;
		if (isSavedRhythm) {
			nameField.setText(rhythm.getName());
		}
		nameField.setMaxTextLength(Rhythms.MAX_RHYTHM_NAME_LEN);

        ConfirmationPopup.Validation validation = new ConfirmationPopup.Validation() {
				@Override
				public boolean isValid(Widget byWidget) {
					// set errorText null each time
					errorText = null;
					
					if (nameField.getTextLength() == 0) {
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.ENTER_UNIQUE_NAME);
					}
					else if (((RhythmsXmlImpl)rhythm.getLibrary().getRhythms()).isNameInUse(nameField.getText(), rhythm)) {
						// check no other rhythm by same name
						errorText = resourceManager.getLocalisedString(TwlLocalisationKeys.NAME_USED);
					}
					return errorText == null;
				}
			};

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
	            public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
	            		executeSaveCommand();
	            	}
	            }
	        };

	    int buttons = ConfirmationPopup.OK_AND_CANCEL_BUTTONS | ConfirmationPopup.EXTRA_BUTTON;

	    // set the position, try to open so the mouse is somewhere close to the name field
	    Location2Df openAt = Location2Df.getTransportLocation(
	    		Math.max(Mouse.getX() - 10, 0f), 
	    		Math.max(resourceManager.getGuiManager().getScreenHeight() - Mouse.getY() - 55, 0f));
	    
	    ConfirmationPopup.showDialogConfirm(validation, callback, caller, 
	    		resourceManager.getLocalisedString(TwlLocalisationKeys.SAVE_RHYTHM_TITLE)
	    		, buttons,
	    		new String[] {
	    			resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_MODULE_TITLE)
	    			, resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_MODULE_TOOLTIP)}
			    , new Runnable() {
					@Override
					public void run() {
						TagsListDialog.popupTagsList(null, RhythmSetNameConfirmation.this);
					}
			    }
	    		, openAt, false, this);
	}

	/**
	 * Allow sub-classes to override. When used to finalise the editing of a rhythm, this will include
	 * conversion of the editRhythm to a normal rhythm, so the command is a modification of this one.
	 */
	protected void executeSaveCommand() {
  		new RhythmSaveCommand(rhythm, nameField.getText(), chosenTags).execute();
	}
	
	/**
	 * Called when pressing done on rhythm editor and the rhythm is new, so needs name/tags added
	 */
	public static class SaveNewEditConfirmation extends RhythmSetNameConfirmation {

		private Runnable callbackOnConfirmed; 
		
		public SaveNewEditConfirmation(EditRhythm editRhythm, Widget caller, Runnable callbackOnConfirmed) {
			super(editRhythm, caller);
			this.callbackOnConfirmed = callbackOnConfirmed;
		}

		@Override
		protected void executeSaveCommand() {
			new RhythmNewSaveEditCommand((EditRhythm)rhythm, nameField.getText(), chosenTags).execute();
			if (callbackOnConfirmed != null) {
				callbackOnConfirmed.run();
			}
		}
	}

}
