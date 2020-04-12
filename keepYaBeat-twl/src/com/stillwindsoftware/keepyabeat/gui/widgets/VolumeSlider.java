package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmBeatTypeCommand.ChangeVolumeCommand;
import com.stillwindsoftware.keepyabeat.platform.MultiEditModel;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Scrollbar;
import de.matthiasmann.twl.Timer;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.ListModel.ChangeListener;
import de.matthiasmann.twl.model.SimpleIntegerModel;

public class VolumeSlider extends Scrollbar implements Runnable, MultiEditModel {
	private RhythmBeatType rBeatType;
	// while dragging is happening updates are too fast, defer
	// xml save until finished
	private boolean changesPending = false;
    private Timer changesTimer;
	private ChangeVolumeCommand changeCmd;
	private int originalVolume;

    public VolumeSlider() {
    	super(Scrollbar.Orientation.HORIZONTAL);
    	setTheme("volumeSlider");  
        addCallback(this);
        String tooltip = TwlResourceManager.getInstance()
        		.getLocalisedString(TwlLocalisationKeys.SET_VOLUME_TOOLTIP);
		setTooltipContent(tooltip);
		setThumbTooltipContent(tooltip);
    }
    
	@Override
	public boolean handleEvent(Event evt) {
		// catch NPE
		try {
			return super.handleEvent(evt);
		}
		catch (NullPointerException npe) {
			return evt.isMouseEvent();
		}
	}

	@Override
	public void run() {
		// tabular version populates data
		if (rBeatType != null) {

			// drag starts, set changes pending
			if (VolumeSlider.this.isThumbDragged() && !changesPending) {
				setChangesPending(true);
			}
			
			// create the command if needed, this will re-execute any number of times, but only save once per operation
			// see setChangesPending() for details
			if (changeCmd == null) {
				changeCmd = new ChangeVolumeCommand(rBeatType, originalVolume, VolumeSlider.this);
			}
			
			changeCmd.execute();
			
			// don't keep the command if not dragging
			if (!changesPending) {
				changeCmd = null;
			}
		}
	}

    void setData(VolumeSliderValue data) {
		rBeatType = data.getRhythmBeatType();
		originalVolume = (int)(rBeatType.getVolume()*100.0f); 
        setValue(originalVolume, false);
    }

	@Override
	public boolean isMultiEdit() {
		return changesPending;
	}

	/**
	 * When setting to pending (passing true) creates a timer to detect the end of dragging, and when it does end it
	 * recalls this method passing false.
	 * When setting to not pending (passing false) and was previously pending then save xml
	 * @param changesPending
	 */
	public void setChangesPending(boolean changesPending) {
		// stop maintaining a pending change... ie. save if needed
		if (!changesPending) {
			// previously was pending, otherwise nothing to do
			if (this.changesPending && changeCmd != null) {
				changeCmd.executeDeferredAction();
				// done with the command, reset
				changeCmd = null;
			}
		}
		else {
			// pending, set timer to detect end of dragging
			changesTimer = getGUI().createTimer();
			changesTimer.setCallback(new Runnable() {
	            public void run() {
	            	// dragging stopped, set changes not pending which will save the xml
	                if (!VolumeSlider.this.isThumbDragged()) {
	                	changesTimer.stop();
	                	setChangesPending(false);
	                }
	            }
	        });
			changesTimer.setContinuous(true);
			changesTimer.start();
		}

		this.changesPending = changesPending;
	}

    /**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class VolumeSliderCellWidgetCreator extends BorderedCellWidgetCreator {

        private VolumeSliderValue data;
        
		public VolumeSliderCellWidgetCreator() {
		}

        public String getTheme() {
        	return "volumecellrenderer";
        }

        /**
         * Update or create the ColourButton widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	VolumeSlider volumeSlider = (VolumeSlider)existingWidget;
            if(volumeSlider == null) {
            	volumeSlider = new VolumeSlider();
            }

            volumeSlider.setData(data);
            return volumeSlider;
        }

        public void setCellData(int row, int column, Object data) {
           this.data = (VolumeSliderValue)data;
        }

    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class VolumeSliderValue {
    	private RhythmBeatType rBeatType;
    	private int value;

    	public VolumeSliderValue() {
         }
    	
    	public void setValues(int value, RhythmBeatType rBeatType) {
    		this.value = value;
        	this.rBeatType = rBeatType;
         }
    	
    	RhythmBeatType getRhythmBeatType() {
    		return rBeatType;
    	}
    	
    	int getValue() {
    		return value;
    	}
    }
    
    public static class VolumeSliderModel extends SimpleIntegerModel implements ChangeListener {
    	
		public VolumeSliderModel(int value) {
			super(0, 100, value);
		}

		@Override
		public void allChanged() {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void entriesChanged(int first, int last) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void entriesDeleted(int first, int last) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void entriesInserted(int first, int last) {
			// TODO Auto-generated method stub
			
		}
		
    	
    }

}
	
