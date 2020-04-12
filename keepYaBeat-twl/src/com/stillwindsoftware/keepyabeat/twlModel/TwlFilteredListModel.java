/*
 * Copyright (c) 2008-2010, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.stillwindsoftware.keepyabeat.twlModel;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.model.LibraryItem;
import com.stillwindsoftware.keepyabeat.model.LibraryListItem;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;

import de.matthiasmann.twl.model.SimpleListModel;

/**
 * 
 * @author Matthias Mann
 * @author Tomas Stubbs
 * Change to use only a regular SimpleListModel rather than Mathias' version using TreeTableModel
 */
public class TwlFilteredListModel<T extends LibraryItem> extends SimpleListModel<T> {

    public interface Filter<T> {
        public boolean isVisible(LibraryItem element);
    }

    private final LibraryListItem<? extends LibraryItem> model;
    private final ArrayList<T> rows;
    private LibraryListListener listener;
    private Filter<T> filter;

    public TwlFilteredListModel(LibraryListItem<? extends LibraryItem> list, Filter<T> filter) {
        this.model = list;
        this.filter = filter;
        this.rows = new ArrayList<T>();
        
        updateList();

        listener = new LibraryListListener() {
			@Override
			public void itemChanged() {
				updateList();
			}
        };
        list.addListener(listener);
    }

//    public ListModel.ChangeListener getListener() {
//    	return listener;
//    }
    
    public void dispose() {
		model.removeListener(listener);
	}

	public Filter<T> getFilter() {
        return filter;
    }

    public void setFilter(Filter<T> filter) {
        this.filter = filter;
        updateList();
    }

    @SuppressWarnings("unchecked")
	private void updateList() {
        rows.clear();
        for (int i = 0; i < model.getSize(); i++) {
        	if (filter.isVisible(model.get(i)))
        		rows.add((T) model.get(i));
        }
        fireAllChanged();
    }

	@Override
	public T getEntry(int index) {
		return rows.get(index);
	}

	@Override
	public int getNumEntries() {
		return rows.size();
	}

}
