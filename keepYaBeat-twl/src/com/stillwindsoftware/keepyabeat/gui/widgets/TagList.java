package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.gui.widgets.NameAndTagsBox.TagListButton;
import com.stillwindsoftware.keepyabeat.model.Tag;


/**
 * Allows tag add/remove behaviour in different widgets, 
 * TagsFlowList is a specialised Tags container, but when showing
 * on one line, such as in the search box of RhythmsList, the
 * tags are shown in a BoxLayout that implements this class.
 * @author tomas
 *
 */
public interface TagList {

	public void addTag(Tag tag);
	public void addTagButton(TagListButton tagButton);
	public void removeTagButton(TagListButton tagButton);
	// callback methods when a tag is changed/deleted in the library, it's up to the owner
	// of the list to ensure that the listener is notified and calls these methods
	public void tagChanged(Tag tag);
	public void tagRemoved(Tag tag);
}
