package com.stillwindsoftware.keepyabeat.twlModel;

import java.util.HashSet;

import com.stillwindsoftware.keepyabeat.model.LibraryItem;
import com.stillwindsoftware.keepyabeat.model.RhythmXmlImpl;
import com.stillwindsoftware.keepyabeat.model.RhythmsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.TagsXmlImpl;
import com.stillwindsoftware.keepyabeat.platform.PersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;


/**
 * A filtered view on the rhythms, allows restriction by searches
 * @author tomas stubbs
 *
 */
@SuppressWarnings("serial")
public class RhythmsSearchModel extends FilteredListModel<RhythmXmlImpl> {

	private TagsXmlImpl tags = (TagsXmlImpl) TwlResourceManager.getInstance().getLibrary().getTags();
	
	// search/filter on name to restrict the list
	private String searchRhythmText = new String();
	private HashSet<Tag> searchTags = new HashSet<Tag>();

	public RhythmsSearchModel() {
		super((RhythmsXmlImpl)TwlResourceManager.getInstance().getLibrary().getRhythms());
		
		setFilter(
			new FilteredListModel.Filter<RhythmXmlImpl>() {
				@Override
				public boolean isVisible(LibraryItem rhythm) {
					if (!searchRhythmText.isEmpty()
							&& !((RhythmXmlImpl) rhythm).getName().toLowerCase().contains(searchRhythmText.toLowerCase())) {
						return false;
					}
					else {
						// upto here then contains search text if any entered, now check if any tags
						// active in search that rhythm is in all of them
						if (searchTags.isEmpty()) {
							return true;
						}
						else {
							// should be able to check the whole collection
							return tags.getRhythmTags((RhythmXmlImpl) rhythm).containsAll(searchTags);
						}
					}
				}
			}
		);
		
		// listen for changes to tags
		tags.addListener(this);

	}

	public void setSearchRhythmText(String searchRhythmText) {
		this.searchRhythmText = searchRhythmText;
		itemChanged();
	}

	public void addSearchTag(Tag tag) {
		searchTags.add(tag);
		itemChanged();
	}

	public void removeSearchTag(Tag tag) {
		searchTags.remove(tag);
		itemChanged();
	}

	public HashSet<Tag> getSearchTags() {
		return searchTags;
	}

	@Override
	public void itemChanged() {
		// check all the tags in the search still exist before applying the filter
		if (!tags.dataList.containsAll(searchTags)) {
			// can't delete from the iterated collection (concurrency issue)
			// so make an array to walk 
			Object[] tagsArr = searchTags.toArray();
			for (int i = 0; i < tagsArr.length; i++) {
				if (!tags.dataList.contains(tagsArr[i])) {
					searchTags.remove(tagsArr[i]);
				}
			}
		}
		
		// store the results in the persistent state
		PersistentStatesManager stateManager = TwlResourceManager.getInstance().getPersistentStatesManager();
		stateManager.setRhythmSearchCriteria(searchRhythmText, searchTags);
		
		super.itemChanged();
	}
	
	
}
