package com.stillwindsoftware.keepyabeat.twlModel;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmImage;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for rhythms
 * @author tomas stubbs
 *
 */
public class RhythmsTableModel extends DataSizingTableModel implements LibraryListListener {
	
	private final RhythmsSearchModel rhythmsSearchModel;
    private RhythmImage.RhythmImageValue rhythmImageValue = new RhythmImage.RhythmImageValue();
	
	public RhythmsTableModel(RhythmsSearchModel rhythmsSearchModel) {
		this.rhythmsSearchModel = rhythmsSearchModel;
		rhythmsSearchModel.addListener(this);
	}

	public Rhythm getRhythm(int row) {
    	return rhythmsSearchModel.get(row);
	}
	
	protected void dispose() {
		rhythmsSearchModel.removeListener(this);
	}

    public int getNumRows() {
        return rhythmsSearchModel.size();
    }

    public int getNumColumns() {
        return 2;
    }

    public Object getCell(int row, int column) {
    	Rhythm rhythm = rhythmsSearchModel.get(row);

    	switch (column) {
        case 0: rhythmImageValue.setValue(row); return rhythmImageValue;
        case 1: return rhythm.getName();
        default: return "OBJECT MISSING FOR "+column;
        }
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
		return false;
	}

	@Override
	public int getColumnTextWidth(int column, Font useFont) {
		return 0;
	}

}
