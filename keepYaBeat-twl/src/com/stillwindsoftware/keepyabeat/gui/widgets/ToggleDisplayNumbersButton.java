package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmBeatTypeCommand;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.TableSingleSelectionModel;

/**
 * subclass of Button to contain the listeners
 */
public class ToggleDisplayNumbersButton extends ToggleButton implements Runnable {
	private int row = -1;
	private RhythmBeatType rBeatType;
	private TableSingleSelectionModel tableSelectionModel;
	
    public ToggleDisplayNumbersButton(TableSingleSelectionModel tableSelectionModel) {
        this.tableSelectionModel = tableSelectionModel;
        setTheme("displayNumbersToggleBtn");   // keep default theme name
        addCallback(this);
        setCanAcceptKeyboardFocus(false);
        setTooltipContent(TwlResourceManager.getInstance()
        		.getLocalisedString(TwlLocalisationKeys.TOGGLE_BEAT_NUMBERS_TOOLTIP));
    }
    
    void setData(DisplayNumbersButtonValue data) {
        this.row = data.getValue();
        this.rBeatType = data.getRhythmBeatType();
        setActive(rBeatType.isDisplayNumbers());
    }

    public void run() {
    	if (tableSelectionModel != null && row != -1)
    		tableSelectionModel.setSelection(0, row);
    	
    	if (rBeatType != null && rBeatType.isDisplayNumbers() != isActive()) {
			new RhythmBeatTypeCommand.ChangeDisplayNumbersCommand(rBeatType, isActive()).execute();
			TwlResourceManager resourceManager = TwlResourceManager.getInstance();
			if (!resourceManager.getPersistentStatesManager().isDrawBeatNumbers()) {
				KeepYaBeat.root.showNotification(resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_WARNING_DRAW_BEAT_NUMS));
			}
    	}
    }
    
    /**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class DisplayNumbersCellWidgetCreator extends BorderedCellWidgetCreator {
        private DisplayNumbersButtonValue data;
    	private TableSingleSelectionModel tableSelectionModel;
        
        public DisplayNumbersCellWidgetCreator(TableSingleSelectionModel tableSelectionModel) {
			this.tableSelectionModel = tableSelectionModel;
		}

        public String getTheme() {
        	return "toggledisplaycellrenderer";
        }

        /**
         * Update or create the PlayButton widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	ToggleDisplayNumbersButton displayNumbersBtn = (ToggleDisplayNumbersButton)existingWidget;
            if(displayNumbersBtn == null) {
            	displayNumbersBtn = new ToggleDisplayNumbersButton(tableSelectionModel);
            }

            displayNumbersBtn.setData(data);
            return displayNumbersBtn;
        }

        public void setCellData(int row, int column, Object data) {
            // we have to remember the cell data for the next call of updateWidget
           this.data = (DisplayNumbersButtonValue)data;
        }

    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class DisplayNumbersButtonValue {
    	private int value;
    	private RhythmBeatType rBeatType;
    	
        public DisplayNumbersButtonValue() {
        }

        public void setValues(int value, RhythmBeatType rBeatType) {
        	this.value = value;
        	this.rBeatType = rBeatType;
        }

		public int getValue() {
			return value;
		}

		public RhythmBeatType getRhythmBeatType() {
			return rBeatType;
		}
        
    }


}

