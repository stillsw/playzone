package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.model.LibraryItem;
import com.stillwindsoftware.keepyabeat.model.LibraryListItem;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;

import de.matthiasmann.twl.model.SimpleListModel;

@SuppressWarnings("rawtypes")
public class TwlListModel<T extends LibraryListItem, E extends LibraryItem> extends SimpleListModel<E> implements LibraryListListener {

	private T listItem;	
	
	public TwlListModel(T listItem) {
		this.listItem = listItem;
		listItem.addListener(this);
	}
	
	public void destroy() {
		listItem.removeListener(this);
	}

	@Override
	public int getNumEntries() {
		return listItem.getSize();
	}

	@SuppressWarnings("unchecked")
	@Override
	public E getEntry(int index) {
		return (E) listItem.get(index);
	}
	
	@SuppressWarnings("unchecked")
	public int indexOf(E libraryItem) {
		return listItem.indexOf(libraryItem);
	}

	@Override
	public void itemChanged() {
		this.fireAllChanged();
	}

}
