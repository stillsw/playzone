package com.stillwindsoftware.keepyabeat.twlModel;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.gui.ImportRhythmsList;
import com.stillwindsoftware.keepyabeat.gui.RhythmTooltip;
import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.ImportRhythmsCheckBoxButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmImage;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ImportRhythm;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for import rhythms
 * @author tomas stubbs
 *
 */
public class ImportRhythmsTableModel extends DataSizingTableModel {
	
	private final ArrayList<ImportRhythm> importRhythmsModel;
	private final ImportRhythmsList importRhythmsList;
	private TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private Library baseLibrary = resourceManager.getLibrary();
	private Rhythms baseRhythms = baseLibrary.getRhythms();
	
	// shared data value objs
    private RhythmImage.RhythmImageValue rhythmImageValue = new RhythmImage.RhythmImageValue();
    private ImportRhythmsCheckBoxButton.ImportRhythmsCheckBoxButtonValue importCheckBoxValue = 
    		new ImportRhythmsCheckBoxButton.ImportRhythmsCheckBoxButtonValue();
    
	public ImportRhythmsTableModel(ArrayList<ImportRhythm> importRhythmsModel, ImportRhythmsList importRhythmsList) {
		this.importRhythmsModel = importRhythmsModel;
		this.importRhythmsList = importRhythmsList;
	}

	public Rhythm getRhythm(int row) {
    	return importRhythmsModel.get(row).getRhythm();
	}

    public int getNumRows() {
        return importRhythmsModel.size();
    }

    public int getNumColumns() {
        return 3;
    }

    private String getRhythmNameText(int row) {
    	ImportRhythm impRhythm = importRhythmsModel.get(row);
    	Rhythm rhythm = getRhythm(row);
    	if (impRhythm.isAlreadyInLibrary()) {
    		Rhythm baseRhythm = (Rhythm) baseRhythms.lookup(rhythm.getKey());
    		if (baseRhythm != null && !baseRhythm.getName().equalsIgnoreCase(rhythm.getName())) {
    			return String.format(
    					resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHM_NAME_CONFLICT_TEXT)
    						, rhythm.getName(), baseRhythm.getName());
    		}
    	}
    	return rhythm.getName();
    }

    public Object getCell(int row, int column) {
    	switch (column) {
        case 0: importCheckBoxValue.setValue(row); return importCheckBoxValue;
        case 1: rhythmImageValue.setValue(row); return rhythmImageValue;
        case 2: return getRhythmNameText(row);
        default: return "OBJECT MISSING FOR "+column;
        }
    }

    @Override
    public Object getTooltipContent(int row, int column) {
    	if (column == 0) {
    		return null;
    	}
    	else {
    		return new RhythmTooltip(getRhythm(row), importRhythmsList.getTooltipTargetGoverner(), false);
    	}
    }

	@Override
	public boolean isColumnDataSized(int column) {
		return column == 2;
	}

	@Override
	public int getColumnTextWidth(int column, Font useFont) {
		int maxTextWidth = 0;
		
		for (int i = 0; i < importRhythmsModel.size(); i++) {
			maxTextWidth = Math.max(maxTextWidth, 
					useFont.computeTextWidth(getRhythmNameText(i)));
		}

		return maxTextWidth;
	}

	public void dataChanged() {
		this.fireAllChanged();
	}

}
