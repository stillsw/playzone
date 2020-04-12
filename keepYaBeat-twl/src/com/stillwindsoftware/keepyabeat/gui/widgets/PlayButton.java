package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.gui.StandardTableList;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sounds;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlSoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.openal.SilentStartAudio;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.TableBase.CellWidgetCreator;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.TableSingleSelectionModel;

/**
 * subclass of Button to contain the listeners
 */
public class PlayButton extends Button implements Runnable {
	private BeatType beatType;
	private Sound sound;
	private int row = -1;
	private TableSingleSelectionModel tableSelectionModel;
	
    public PlayButton(TableSingleSelectionModel tableSelectionModel) {
        this.tableSelectionModel = tableSelectionModel;
        setTheme("playBtn");   // keep default theme name
        setCanAcceptKeyboardFocus(false);
        setTooltipContent(TwlResourceManager.getInstance().getLocalisedString(
        		TwlLocalisationKeys.PLAY_SOUND_TOOLTIP));
        addCallback(this);
    }
    
    @Override
	protected boolean handleEvent(Event evt) {
    	// all button down events need to set the selection on the list if there is one
    	if (evt.getType() == Event.Type.MOUSE_BTNDOWN) {
    		StandardTableList.handleMouseClickOnRow(this, evt, row);
    	}
    	
		return super.handleEvent(evt);
	}

    void setData(PlayButtonValue data) {
        this.row = data.getValue();
        this.beatType = data.getBeatType();
        this.sound = data.getSound();
    }

    public void run() {
    	if (tableSelectionModel != null && row != -1) {
    		tableSelectionModel.setSelection(0, row);
    	}

    	if (sound != null) {
    		SilentStartAudio audioEffect = ((TwlSoundResource)sound.getSoundResource()).getAudioEffect();
    		
			// play as a one off sound effect
			if (audioEffect != null) { 
				// playing the sound with no delay, as it's padded at the start with silence, set seconds to that length
				audioEffect.playSoon(1.0f, (beatType == null ? 1.0f : beatType.getVolume()), Sounds.LEFT_PAD_SILENT_MILLIS / (float)BeatTracker.MILLIS_PER_SECOND);
			}
    	}
    }
    
    /**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class PlayButtonCellWidgetCreator implements CellWidgetCreator {
        private int cellHeight;
        private int cellWidth;
        private PlayButtonValue data;
    	private TableSingleSelectionModel tableSelectionModel;
        
        public PlayButtonCellWidgetCreator(TableSingleSelectionModel tableSelectionModel) {
			this.tableSelectionModel = tableSelectionModel;
		}

		public void applyTheme(ThemeInfo themeInfo) {
        	cellHeight = themeInfo.getParameter("minHeight", 50);
        	cellWidth = themeInfo.getParameter("maxWidth", 1000);
        }

        public String getTheme() {
        	return "buttoncellrenderer";
        }

        /**
         * Update or create the PlayButton widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	PlayButton playBtn = (PlayButton)existingWidget;
            if(playBtn == null) {
                playBtn = new PlayButton(tableSelectionModel);
            }

            playBtn.setData(data);
            return playBtn;
        }

        public void positionWidget(Widget widget, int x, int y, int w, int h) {
            // this method will size and position the button
            // If the widget should be centered (like a check box) then this
            // would be done here
        	int realW = Math.min(w, cellWidth);
            widget.setPosition((int)(x + (w - realW) / 2.0f), y);
            widget.setSize(realW, h);
        }

        public void setCellData(int row, int column, Object data) {
            // we have to remember the cell data for the next call of updateWidget
           this.data = (PlayButtonValue)data;
        }

        public Widget getCellRenderWidget(int x, int y, int width, int height, boolean isSelected) {
            // this cell does not render anything itself
            return null;
        }

        public int getColumnSpan() {
            // no column spanning
            return 1;
        }

        public int getPreferredHeight() {
        	return cellHeight;
        }
    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class PlayButtonValue {
    	private int value;
    	private Sound sound;
    	private BeatType beatType;
    	
        public PlayButtonValue() {
        }

        public void setValues(int value, Sound sound, BeatType beatType) {
        	this.value = value;
        	this.sound = sound;
        	this.beatType = beatType;
        }

		public int getValue() {
			return value;
		}

		public Sound getSound() {
			return sound;
		}

		public BeatType getBeatType() {
			return beatType;
		}
        
    }


}

