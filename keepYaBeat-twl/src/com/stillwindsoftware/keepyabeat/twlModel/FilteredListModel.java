package com.stillwindsoftware.keepyabeat.twlModel;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.model.LibraryItem;
import com.stillwindsoftware.keepyabeat.model.LibraryListItem;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;


/**
 * @author Tomas Stubbs
 * Simplied version of a list model written by Matthias Mann as part of his TWL toolkit
 */
public class FilteredListModel<T extends LibraryItem> extends ArrayList<T> implements LibraryListListener, LibraryListener.LibraryListenerTarget {

	private static final long serialVersionUID = 3423508004725126732L;

	public interface Filter<T> {
        public boolean isVisible(LibraryItem element);
    }

    private final LibraryListItem<? extends LibraryItem> model;
//    private final ArrayList<T> rows;
    private Filter<T> filter;
    private boolean isListeningForChanges;
    
    /**
     * Allow a skeletal model, needs to have setFilter called to work
     * @param list
     * @param filter
     */
    public FilteredListModel(LibraryListItem<? extends LibraryItem> list) {
    	this(list, null, true);
    }

    /**
     * By default the list model will listen for future changes
     * @param list
     * @param filter
     */
    public FilteredListModel(LibraryListItem<? extends LibraryItem> list, Filter<T> filter) {
    	this(list, filter, true);
    }

    /**
     * When a model is only needed to provide a one time view of the list, it can set the param to false, no 
     * listening will happen then.
     * @param list
     * @param filter
     * @param listenForChanges
     */
    public FilteredListModel(LibraryListItem<? extends LibraryItem> list, Filter<T> filter, boolean listenForChanges) {
        this.model = list;
        this.filter = filter;
//        this.rows = new ArrayList<T>();

        if (filter != null) {
        	updateList();
        }
        
        this.isListeningForChanges = listenForChanges;
        if (isListeningForChanges) {
            list.addListener(this);
        }
    }

    public void dispose() {
//    	ListenerSupport.getInstance().removeItem(this);
//    	if (isListeningForChanges) {
    		TwlResourceManager.getInstance().getListenerSupport().removeListener(this);
//    	}
	}

	public Filter<T> getFilter() {
        return filter;
    }

    public void setFilter(Filter<T> filter) {
        this.filter = filter;
        updateList();
    }

//	public LibraryListListener getListener() {
//		return listener;
//	}

//	public void reapplyFilter() {
//		updateList();
//	}
	
	@SuppressWarnings("unchecked")
	private void updateList() {
//        rows.clear();
		clear();
        for (int i = 0; i < model.getSize(); i++) {
        	if (filter.isVisible(model.get(i)))
//        		rows.add((T) model.get(i));
        		add((T) model.get(i));
        }
        TwlResourceManager.getInstance().getListenerSupport().fireItemChanged(this.getListenerKey());
    }
	
    public void addListener(LibraryListListener listListener) {
    	TwlResourceManager.getInstance().getListenerSupport().addListener(this, listListener);
    }
    
    public void removeListener(LibraryListListener listListener) {
    	TwlResourceManager.getInstance().getListenerSupport().removeListener(this, listListener);
    }

	@Override
	public void itemChanged() {
        if (filter != null) {
        	updateList();
        }
	}

	/**
	 * Almost certain that any LibraryItem object that is used as a target of a LibraryListener will already have a key set
	 * and then the hashCode is cached for future calls. The following 2 methods are here to fulfil the hashCode contract
	 */
	private int hashCode = -1;
	@Override
	public int hashCode() {
    	if (hashCode != -1) {
    		return hashCode;
    	}
		final int prime = 31;
		int result = super.hashCode();
		String key = null;
		if (model != null) {
			key = model.getKey();
		}
		result = prime * result + ((model == null) ? 0 : (key == null ? model.hashCode() : key.hashCode()));
		if (key != null) {
			hashCode = result;
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		FilteredListModel<?> other = (FilteredListModel<?>) obj;
		if (model == null) {
			if (other.model != null)
				return false;
		} else if (!model.equals(other.model))
			return false;
		return true;
	}

	@Override
	public String getListenerKey() {
		return String.format("filteredlist-%s", model.getListenerKey());
	}
}
