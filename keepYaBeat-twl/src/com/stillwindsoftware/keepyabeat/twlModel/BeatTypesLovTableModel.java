package com.stillwindsoftware.keepyabeat.twlModel;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for beat types lov 
 * @author tomas stubbs
 *
 */
public class BeatTypesLovTableModel extends DataSizingTableModel implements LibraryListListener {
	
	private final BeatTypesXmlImpl beatTypes;
	private ColourButton.ColourButtonValue cbValue = new ColourButton.ColourButtonValue();

	public BeatTypesLovTableModel() {
		beatTypes = (BeatTypesXmlImpl) TwlResourceManager.getInstance().getLibrary().getBeatTypes();
	}

	@Override
	public boolean isColumnDataSized(int column) {
		// all others are
		return true;
	}

	@Override
	public int getColumnTextWidth(int column, Font useFont) {
		int maxTextWidth = 0;
		
		switch (column) {
		case 0 : // beat type names
			for (int i = 0; i < beatTypes.getSize(); i++) {
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(beatTypes.get(i).getName()));
			}
			break;
		}
				
		return maxTextWidth;
	}


    public int getNumRows() {
        return beatTypes.getSize();
    }

    public int getNumColumns() {
        return 1;
    }

    public Object getCell(int row, int column) {
    	BeatType beatType = (BeatType) beatTypes.get(row);
    	switch (column) {
        case 0:
        	cbValue.setValues(row, beatType); return cbValue;
        default: return "OBJECT MISSING FOR "+column;
        }
    }

	@Override
	public void itemChanged() {
		// LOV only active while picking, so no listening
	}

}
