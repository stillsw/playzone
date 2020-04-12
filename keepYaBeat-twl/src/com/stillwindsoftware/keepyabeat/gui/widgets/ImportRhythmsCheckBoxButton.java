package com.stillwindsoftware.keepyabeat.gui.widgets;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.gui.ImportRhythmsList;
import com.stillwindsoftware.keepyabeat.utils.ImportRhythm;

import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;

/**
 * subclass of Button to contain the listeners
 */
public class ImportRhythmsCheckBoxButton extends ToggleButton implements Runnable {
	private ImportRhythmsCheckBoxButtonValue data;
	private ArrayList<ImportRhythm> importRhythmsModel;
	private ImportRhythmsList importRhythmsList;
	
    public ImportRhythmsCheckBoxButton(ArrayList<ImportRhythm> importRhythmsModel, ImportRhythmsList importRhythmsList) {
        this.importRhythmsModel = importRhythmsModel;
		this.importRhythmsList = importRhythmsList;
        setTheme("checkbox");  
        addCallback(this);
        setCanAcceptKeyboardFocus(false);
    }
    
    void setData(ImportRhythmsCheckBoxButtonValue data) {
        this.data = data;
        setActive(importRhythmsModel.get(data.getValue()).isSelected());
        setEnabled(importRhythmsList.getImportModeCombo().getSelected() == 0); // only selected option
    }

    public void run() {
        if(data != null) {
        	if (importRhythmsModel.get(data.getValue()).isSelected() != isActive()) {
        		importRhythmsModel.get(data.getValue()).setSelected(isActive());
        	}
        }
    }
    
    /**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class ImportRhythmsCheckBoxButtonCellWidgetCreator extends BorderedCellWidgetCreator {
        private ImportRhythmsCheckBoxButtonValue data;
    	private ArrayList<ImportRhythm> importRhythmsModel;
    	private ImportRhythmsList importRhythmsList;
        
        public ImportRhythmsCheckBoxButtonCellWidgetCreator(ArrayList<ImportRhythm> importRhythmsModel, ImportRhythmsList importRhythmsList) {
			this.importRhythmsModel = importRhythmsModel;
			this.importRhythmsList = importRhythmsList;
		}

        public String getTheme() {
        	return "checkboxcellrenderer";
        }

        /**
         * Update or create the widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	ImportRhythmsCheckBoxButton checkboxBtn = (ImportRhythmsCheckBoxButton)existingWidget;
            if(checkboxBtn == null) {
            	checkboxBtn = new ImportRhythmsCheckBoxButton(importRhythmsModel, importRhythmsList);
            }

            checkboxBtn.setData(data);
            return checkboxBtn;
        }

        public void setCellData(int row, int column, Object data) {
            // we have to remember the cell data for the next call of updateWidget
           this.data = (ImportRhythmsCheckBoxButtonValue)data;
        }

    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class ImportRhythmsCheckBoxButtonValue {
    	private int value;
    	
        public ImportRhythmsCheckBoxButtonValue() {
        }

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
		}
    }


}

