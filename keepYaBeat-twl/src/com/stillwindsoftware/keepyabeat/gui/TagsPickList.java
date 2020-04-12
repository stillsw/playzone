package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;
import java.util.HashSet;

import com.stillwindsoftware.keepyabeat.gui.widgets.NameAndTagsBox.TagListButton;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.TagsXmlImpl;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.twlModel.RhythmsSearchModel;

import de.matthiasmann.twl.PopupWindow;

/**
 * A popup list of tags to choose one
 * @author tomas stubbs
 */
public class TagsPickList extends TagsFlowList {

	/**
	 * Implementing class will be able to receive tags when they are picked in the list
	 */
	public interface PickedTagReceiver {
		public void tagPicked(Tag tag);
		public TagsPickList getTagsPickList();
	}
	
	private PickedTagReceiver pickedTagReceiver;
	
	public TagsPickList(RhythmsSearchModel searchModel, PickedTagReceiver pickedTagReceiver) {
		this(initModel(searchModel), pickedTagReceiver);
	}

	public TagsPickList(ArrayList<Tag> showTags, PickedTagReceiver pickedTagReceiver) {
		super(showTags);
		this.pickedTagReceiver = pickedTagReceiver;
	}

	@Override
	protected String getNoTagsMessage() {
		return TwlResourceManager.getInstance()
				.getLocalisedString(TwlLocalisationKeys.NO_MORE_TAGS_MESSAGE);
	}

	@Override
	protected Runnable makeTagButtonClickedCallback(TagListButton tagButton, final Tag tag) {
        return new Runnable() {
			@Override
			public void run() {
				if (pickedTagReceiver != null) {
					pickedTagReceiver.tagPicked(tag);
					
					// great grand parent is the popup window
					((PopupWindow)getParent().getParent().getParent()).closePopup();
				}
			}	
        };
	}

	/**
	 * Include all tags not already in the chosen list
	 */
	private static ArrayList<Tag> initModel(RhythmsSearchModel searchModel) {
		HashSet<Tag> chosenTags = searchModel.getSearchTags();
		TagsXmlImpl tags = (TagsXmlImpl) TwlResourceManager.getInstance().getLibrary().getTags();
		ArrayList<Tag> remainingTags = new ArrayList<Tag>();
		
		for (int i = 0; i < tags.getSize(); i++) {
			if (!chosenTags.contains(tags.get(i))) {
				remainingTags.add(tags.get(i));
			}
		}
		
		return remainingTags;
	}
	
	
	

}
