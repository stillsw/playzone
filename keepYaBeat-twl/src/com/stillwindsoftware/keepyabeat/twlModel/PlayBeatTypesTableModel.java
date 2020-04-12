package com.stillwindsoftware.keepyabeat.twlModel;

import java.util.TreeSet;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo;
import com.stillwindsoftware.keepyabeat.gui.widgets.ToggleDisplayNumbersButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.VolumeSlider;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for beatTypes that are shown in the play setting panel
 * adjustment for sound, volume and colour here.
 * It's a little bit complicated by the fact that the model is made on the fly
 * by the rhythm draughter, so the rhythm beat types used here can also be updated.
 * For that reason, this class does not listen directly to changes, but receives
 * news from the calling module (eg. player panel)
 * @author tomas
 *
 */
public class PlayBeatTypesTableModel extends DataSizingTableModel {
	

	private TreeSet<RhythmBeatType> rhythmBeatTypes; 
	private RhythmBeatType[] rhythmBeatTypesArray = new RhythmBeatType[0];
	private TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private BeatTypesXmlImpl beatTypes = (BeatTypesXmlImpl) resourceManager.getLibrary().getBeatTypes();
	private final SoundsXmlImpl sounds = (SoundsXmlImpl) resourceManager.getLibrary().getSounds();
	private final ColourButton.ColourButtonValue cbValue = new ColourButton.ColourButtonValue();
	private final ToggleDisplayNumbersButton.DisplayNumbersButtonValue tgValue = new ToggleDisplayNumbersButton.DisplayNumbersButtonValue();
	private final VolumeSlider.VolumeSliderValue vsValue = new VolumeSlider.VolumeSliderValue();
	private final SoundCombo.SoundComboValue scValue = new SoundCombo.SoundComboValue();
	
	/**
	 * Constructs the model from the data
	 * @param rhythmBeatTypes
	 */
	public PlayBeatTypesTableModel(TreeSet<RhythmBeatType> rhythmBeatTypes) {
		this.rhythmBeatTypes = rhythmBeatTypes;
		rhythmBeatTypesArray = rhythmBeatTypes.toArray(rhythmBeatTypesArray);
	}

	/**
	 * Cause the model to update, called from the player panel when its listener
	 * fires on opening a new rhythm.
	 */
	public void modelUpdated(TreeSet<RhythmBeatType> rhythmBeatTypes) {
		this.rhythmBeatTypes = rhythmBeatTypes;
		rhythmBeatTypesArray = new RhythmBeatType[0];
		rhythmBeatTypesArray = rhythmBeatTypes.toArray(rhythmBeatTypesArray);
		fireAllChanged();
	}
	
	/**
	 * Cause the model to update, called from the player panel when its listener
	 * fires on rhythm change.
	 */
	public void modelUpdated() {
		modelUpdated(rhythmBeatTypes);
		fireAllChanged();
	}
	
	public boolean rowContainsError(int row) {
    	RhythmBeatType rBeatType = rhythmBeatTypesArray[row];
    	Sound sound = rBeatType.getSound();
    	SoundResource sr = sound.getSoundResource();
    	return sr != null && sr.getStatus() != SoundStatus.LOADED_OK;
	}
	
    public int getNumRows() {
        return rhythmBeatTypesArray.length;
    }

    public int getNumColumns() {
        return 4;
    }

	@Override
	public boolean isColumnDataSized(int column) {
		return (column == 0 || column == 2);
	}

	@Override
	public int getColumnTextWidth(int column, Font useFont) {
		int maxTextWidth = 0;

		// there are 2 sources for text
		switch (column) {
		case 0 : // rhythm beat types
			for (int i = 0; i < rhythmBeatTypesArray.length; i++) {
				maxTextWidth = Math.max(maxTextWidth, 
					useFont.computeTextWidth(rhythmBeatTypesArray[i].getBeatType().getName()));
			}
			break;
		case 2 : // all sounds
			for (int i = 0; i < sounds.getSize(); i++) {
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(sounds.get(i).getName()));
			}
			break;
		}
		
		return maxTextWidth;
	}
 
    public Object getCell(int row, int column) {
    	RhythmBeatType rBeatType = rhythmBeatTypesArray[row];
 
    	switch (column) {
        case 0: cbValue.setValues(row, rBeatType.getBeatType()); return cbValue;
        case 1: 
           	Sound sound = rBeatType.getSound();
           	boolean retValue = false;
           	if (sound.isPlayable()) {
    			retValue = true;
    		}
        	else {
        		// look for fallback sound, if playable, show volume slider
    			SoundResource fallbackSr = rBeatType.getFallbackSoundResource();
        		if (fallbackSr != null) {
        			retValue = true;
        		}
        	}
        	
        	if (retValue) {
    			vsValue.setValues(row, rBeatType);
    			return vsValue;
        	}
    		else {
    			return "";
    		}
        case 2: 
        	// silent beat type should not be modifiable
        	if (beatTypes.isSoundChangePermitted(rBeatType.getBeatType())) {
	        	scValue.setValues(row, new SoundCombo.RhythmBeatTypeSoundClient(rBeatType));
	        	return scValue;
		    }
        	else {
        		return "";// empty rather than saying 'no sound'... resourceManager.getLocalisedString(rBeatType.getBeatType().getSound().getName());
        	}
        case 3: tgValue.setValues(row, rBeatType); return tgValue;
        default: return "OBJECT MISSING FOR "+column;
        }
    }

}
