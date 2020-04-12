package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.gui.BeatTypesListOfValues.BeatTypeSelectReceiver;
import com.stillwindsoftware.keepyabeat.gui.GuiUtils;
import com.stillwindsoftware.keepyabeat.gui.StandardTableList;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes.DefaultBeatTypes;
import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlAnimationState.TwlAnimationStateKey;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlColour;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlColourManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.ColorSpaceHSL;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;
import de.matthiasmann.twl.renderer.Image;

/**
 * subclass of Button to act as a colour selector
 */
public class ColourButton extends Button {
	
	private boolean updateBeatTypeOnOk = true;
	private boolean showText = false;
	private TwlColour colour;
	private Image originalImage;
	private static TwlColourManager twlColourManager = (TwlColourManager) TwlResourceManager.getInstance().getColourManager();
	private String tooltipLocalisedKey;
	protected BeatTypeSelectReceiver beatTypeSelectReceiver;
	protected BeatType beatType;
	private int row = -1;
	private Runnable setColourCallback;
	
	/**
	 * Always called by other constructors
	 * @param showText
	 */
	private ColourButton(boolean showText, String tooltipLocalisedKey) {
        this.showText = showText;
        this.tooltipLocalisedKey = tooltipLocalisedKey;
    	setTheme("colourSelectorButton");  
	}
	
	/**
	 * ColourButton is used to invoke a list of values of beat types, so not part of a list
	 * and the model class will not be created by the list cell renderer and therefore it's
	 * passed in.
	 * Caller handles what to do on clicking to launch the list of values.
	 * @param data
	 */
	public ColourButton(ColourButtonValue data) {
    	this(true, TwlLocalisationKeys.LOV_CHOOSE_BEAT_TYPE);
    	setData(data);
	}
	
	/**
	 * Created by the table cell creator (see above) when there's a beatType selection receiver passed
	 * which indicates to pass back the selection to that object rather than to change colour.
	 * @param parent
	 * @param beatTypeSelectReceiver
	 */
	public ColourButton(final Widget parent, final BeatTypeSelectReceiver beatTypeSelectReceiver, boolean addCallback, String tooltipLocalisedKey) {
    	this(true, tooltipLocalisedKey);
		this.beatTypeSelectReceiver = beatTypeSelectReceiver;
		
		if (addCallback) {
	        addCallback(new Runnable() {
				@Override
				public void run() {
					if (beatType != null) {
						beatTypeSelectReceiver.beatTypeSelected(beatType, ColourButton.this);
					}
					else {
						TwlResourceManager.getInstance().log(LOG_TYPE.error, this, "ColourButton.callback: no data set for lov button");
					}
				}
	        });
		}
	}
	
	/**
	 * ColourButton is used to select a colour either stand alone (AddEditBeatTypePanel) or
	 * in a list (PlayerRhythmBeatTypesList)
	 * Parent is the frame which should get the focus when the colour frame closes.
	 * @param parent
	 * @param updateSelf
	 */
    public ColourButton(final Widget parent, final boolean updateSelf, boolean showText) {
    	this(showText, TwlLocalisationKeys.CHANGE_DISPLAY_COLOUR);
    	
        addCallback(new Runnable() {
			@Override
			public void run() {
				GuiUtils.invokeColourSelector((beatType == null ? null : beatType), ColourButton.this, parent, updateBeatTypeOnOk);
			}
        });
    }
    
    /**
     * ColourButton which has nothing to do with beat types (SettingsDialog colours to set for playing beats)
     * @param parent
     * @param colour
     */
    public ColourButton(final Widget parent, TwlColour colour, Runnable setColourCallback) {
    	this(false, TwlLocalisationKeys.CHANGE_DISPLAY_COLOUR);
    	updateBeatTypeOnOk = false;
    	this.colour = colour;
    	this.setColourCallback = setColourCallback;
    	
        addCallback(new Runnable() {
			@Override
			public void run() {
				GuiUtils.invokeColourSelector(null, ColourButton.this, parent, false, true); //allows alpha
			}
        });
    }
    
	@Override
	protected boolean handleEvent(Event evt) {
    	// all button down events need to set the selection on the list if there is one
    	if (evt.getType() == Event.Type.MOUSE_BTNDOWN) {
    		StandardTableList.handleMouseClickOnRow(this, evt, row);
    	}
    	
		return super.handleEvent(evt);
	}

	public void setBackgroundColour(Color colour) {
    	this.colour = new TwlColour(colour);
    	super.setBackground(originalImage.createTintedVersion(colour));
    	
    	if (setColourCallback != null) {
    		setColourCallback.run();
    	}
    }
    
	@Override
	public void setBackground(Image background) {
		originalImage = background;
		setBackgroundColour(colour.getTwlColor());
	}

	public Color getTwlColor() {
		return colour.getTwlColor();
	}
	
	public Colour getColour() {
		return colour;
	}

	public BeatType getBeatType() {
		return beatType;
	}

	public void setUpdateBeatTypeOnOk(boolean updateBeatTypeOnOk) {
		this.updateBeatTypeOnOk = updateBeatTypeOnOk;
	}	

	private void setTooltipIfNotListError() {
		setTooltipContent(TwlResourceManager.getInstance().getLocalisedString(tooltipLocalisedKey));
		
	}
	
	@Override
	protected void layout() {
		super.layout();
		
        // the list reports errors on particular rows if needed, use it to set the text colour to error (red)
		StandardTableList listParent = StandardTableList.getStandardTableListParent(this);
		boolean standAlone = listParent == null;
		
		if (!standAlone && listParent.rowContainsError(row)) {
	     	getAnimationState().setAnimationState(StateKey.get("error"), true);
	     	setTooltipContent(listParent.getTooltipText(row));
		}
		else {
	     	getAnimationState().setAnimationState(StateKey.get("error"), false);
	     	setTooltipIfNotListError();
		}
	}

	public void setData(ColourButtonValue data) {
        beatType = data.getBeatType();
        row = data.getValue();
        if (showText && beatType != null) {
        	setText(beatType.getName());
        }
        
        colour = (beatType == null ? ((TwlColour)twlColourManager.getDefaultColour(DefaultBeatTypes.normal))
        							: ((TwlColour)data.getBeatType().getColour()));

        ColorSpaceHSL csh = new ColorSpaceHSL();
		// if quite dark
		// set the state to to get a reverse video (which is a light colour [actually white in default theme])
	    getAnimationState().setAnimationState(((TwlAnimationStateKey)TwlGuiManager.STATE_REVERSE_VIDEO)
	    			.getStateKey(), csh.fromRGB(colour.getTwlColor().toARGB())[2] < TwlGuiManager.REVERSE_VIDEO_THRESHOLD);
    }

	/**
	 * Specialised colour button that reacts to button down events to initiate dragging
	 * in a rhythm
	 */
	public static class DragBeatTypeColourButton extends ColourButton {
		
		// store the location of the button down before a drag is detected
		private Location2Df mouseButtonDownLoc;
		private boolean preparingDrag = false; 
		private Runnable dragStoppedCallback;
		
		public DragBeatTypeColourButton(Widget parent, BeatTypeSelectReceiver beatTypeSelectReceiver
				, Runnable dragStoppedCallback) {
			super(parent, beatTypeSelectReceiver, false, TwlLocalisationKeys.INVOKE_DRAG_BEAT_TYPE); // don't add a callback
			this.dragStoppedCallback = dragStoppedCallback;
		}
		
		@Override
		protected boolean handleEvent(Event evt) {
			
			if (evt.getType() == Event.Type.MOUSE_BTNDOWN) {
				mouseButtonDownLoc = TwlResourceManager.getInstance().getGuiManager().getPointerLocation();
				preparingDrag = true;			
			}
			else if (evt.getType() == Event.Type.MOUSE_DRAGGED && preparingDrag) {
				// start the dragging
				preparingDrag = false;
				
				// run the select receiver (doesn't run on click, only on drag)
				if (beatType != null && beatTypeSelectReceiver != null) {
					beatTypeSelectReceiver.beatTypeSelected(beatType, this);
				}
				
			}
			else if (evt.getType() == Event.Type.MOUSE_BTNUP) {
				// callback drag stopped regardless... should be dragging but let the caller evaluate that
				if (dragStoppedCallback != null) {
					dragStoppedCallback.run();
				}
				preparingDrag = false;
			}
			else {
				// clear any dragging
				preparingDrag = false;
			}
			return super.handleEvent(evt);
		}

		public Location2Df getMouseButtonDownLoc() {
			return mouseButtonDownLoc;
		}
	}
	
    /**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class ColourButtonCellWidgetCreator extends BorderedCellWidgetCreator {

        private ColourButtonValue data;
        private Widget parent;
    	private boolean showText;
    	private BeatTypeSelectReceiver beatTypeSelectReceiver;
    	private Runnable dragStoppedCallback;
        
		public ColourButtonCellWidgetCreator(Widget parent, boolean showText) {
			this(parent, showText, null, null);
		}

		public ColourButtonCellWidgetCreator(Widget parent, boolean showText
				, BeatTypeSelectReceiver beatTypeSelectReceiver) {
			this(parent, showText, beatTypeSelectReceiver, null);
		}

		public ColourButtonCellWidgetCreator(Widget parent, boolean showText
				, BeatTypeSelectReceiver beatTypeSelectReceiver, Runnable dragStoppedCallback) {
			this.showText = showText;
			this.parent = parent;
			this.beatTypeSelectReceiver = beatTypeSelectReceiver;
			this.dragStoppedCallback = dragStoppedCallback;

		}

        public String getTheme() {
        	return "colourcellrenderer";
        }

        /**
         * Update or create the ColourButton widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	ColourButton colourButton = (ColourButton)existingWidget;
            if(colourButton == null) {
            	if (beatTypeSelectReceiver == null) {
            		colourButton = new ColourButton(parent, false, showText); // don't update self, just update the underlying beat type
            	}
            	else if (dragStoppedCallback != null) {
            		colourButton = new DragBeatTypeColourButton(parent, beatTypeSelectReceiver, dragStoppedCallback);
            	}
            	else {
            		// it's a normal lov button
            		colourButton = new ColourButton(parent, beatTypeSelectReceiver, true, TwlLocalisationKeys.LOV_CHOOSE_BEAT_TYPE); // add a callback
            	}
            }

            colourButton.setData(data);
            return colourButton;
        }

        public void setCellData(int row, int column, Object data) {
            this.data = (ColourButtonValue)data;
        }
    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class ColourButtonValue {
    	private BeatType beatType;
    	private int value;

    	public ColourButtonValue() {
        }
    	
    	public void setValues(int value, BeatType beatType) {
    		this.value = value;
    		this.beatType = beatType;
        }
    	
		public BeatType getBeatType() {
    		return beatType;
    	}

		public int getValue() {
			return value;
		}

    }


}
	
