package com.stillwindsoftware.keepyabeat.twlModel;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypeXmlImpl;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for beatTypes
 * @author tomas
 *
 */
public class BeatTypesTableModel extends DataSizingTableModel implements LibraryListListener {
		
	private TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private final Library library = resourceManager.getLibrary();
	private final BeatTypesXmlImpl beatTypes = (BeatTypesXmlImpl) library.getBeatTypes();
	private final SoundsXmlImpl sounds = (SoundsXmlImpl) library.getSounds();
	private final ColourButton.ColourButtonValue cbValue = new ColourButton.ColourButtonValue();
	private final SoundCombo.SoundComboValue scValue = new SoundCombo.SoundComboValue();

	public BeatTypesTableModel() {
		beatTypes.addListener(this);
	}

	@Override
	public boolean isColumnDataSized(int column) {
		// colour button
		if (column == 1) { 
			return false;
		}
		
		// all others are
		return true;
	}

	@Override
	public int getColumnTextWidth(int column, Font useFont) {
		int maxTextWidth = 0;
		
		// there are 3 sources for text
		switch (column) {
		case 0 : // beat types
			for (int i = 0; i < beatTypes.getSize(); i++) {
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(beatTypes.get(i).getName()));
			}
			break;
		case 2 : // all sounds
			for (int i = 0; i < sounds.getSize(); i++) {
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(sounds.get(i).getName()));
			}
			break;
		case 3 : // core sounds only
			for (int i = 0; i < sounds.getInternalSounds().size(); i++) {
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(sounds.getInternalSounds().get(i).getName()));
			}
			break;
		}
				
		return maxTextWidth;
	}

	public void dispose() {
		beatTypes.removeListener(this);
	}

    public int getNumRows() {
        return beatTypes.getSize();
    }

    public int getNumColumns() {
    	if (beatTypes.onlyUsesCoreSounds()) {
    		return 3;
    	}
    	else {
    		return 4;
    	}
    }

    public Object getCell(int row, int column) {
    	BeatTypeXmlImpl beatType = beatTypes.get(row);
    	Sound mainSound = beatType.getSound();
    	SoundResource sr = mainSound == null ? null : mainSound.getSoundResource();
    	
    	// silent beat type should not be modifiable
    	boolean soundModifiable = beatTypes.isSoundChangePermitted(beatType);

    	switch (column) {
    	case 0:
    		if (sr != null && sr.getStatus() == SoundStatus.LOADED_OK) {
        		return beatType.getName();
    		}
    		else {
    			return new DecoratedText(beatType.getName(), DecoratedText.ERROR);
    		}
        case 1: cbValue.setValues(row, beatType); return cbValue;
        case 2:
	    	if (soundModifiable) {
	    		scValue.setValues(row, new SoundCombo.BeatTypeSoundClient(beatType));
	    		return scValue;
		    }
	    	else {
	    		return "";// empty rather than saying 'no sound'
	    	}
        case 3:
        	if (soundModifiable && mainSound != null && !sounds.isSystemSound(mainSound) && beatType.getFallbackSound() != null) {
        		scValue.setValues(row, new SoundCombo.BeatTypeFallbackSoundClient(beatType));
        		return scValue;
        	}
        	else {
        		return "";// null mucks up the width
        	}
        default: return "";
        }
    }

    @Override
    public Object getTooltipContent(int row, int column) {
    	BeatType beatType = beatTypes.get(row);
    	Sound mainSound = beatType.getSound();
    	SoundResource sr = mainSound == null ? null : mainSound.getSoundResource();
		if (sr == null || sr.getStatus() != SoundStatus.LOADED_OK) {
			return String.format(
					resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_TYPES_TABLE_SOUND_PROBLEM)
					, (mainSound == null 
						? resourceManager.getLocalisedString(TwlLocalisationKeys.TABLES_SOUND_MISSING_ERROR) 
								: mainSound.getName()));
		}
		else {
			return null;
		}
    }

	@Override
	public void itemChanged() {
		this.fireAllChanged();
	}

}
