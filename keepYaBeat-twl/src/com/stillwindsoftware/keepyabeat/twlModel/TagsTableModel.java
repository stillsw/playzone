package com.stillwindsoftware.keepyabeat.twlModel;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.TagButton;
import com.stillwindsoftware.keepyabeat.model.TagsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for tags
 * @author tomas stubbs
 *
 */
public class TagsTableModel extends DataSizingTableModel implements LibraryListListener {
	
	private final TagsXmlImpl tags;
	private TagButton.TagButtonValue tagButtonValue = new TagButton.TagButtonValue();
	
	public TagsTableModel() {
		tags = (TagsXmlImpl) TwlResourceManager.getInstance().getLibrary().getTags();
		tags.addListener(this);
	}

	public void dispose() {
		tags.removeListener(this);
	}
	
    public int getNumRows() {
        return tags.getSize();
    }

    public int getNumColumns() {
        return 1;
    }

    public Object getCell(int row, int column) {
    	tagButtonValue.setValue(row);
    	return tagButtonValue;
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
		
		for (int i = 0; i < tags.getSize(); i++) {
			maxTextWidth = Math.max(maxTextWidth, 
					useFont.computeTextWidth(tags.get(i).getName()));
		}

		return maxTextWidth;
	}

}
