package com.stillwindsoftware.keepyabeat.twlModel;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.widgets.PlayButton;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.renderer.Font;

/**
 * Table model for sounds files
 * @author tomas
 *
 */
public class SoundsTableModel extends DataSizingTableModel implements LibraryListListener {
	
	private final SoundsXmlImpl sounds;
	private PlayButton.PlayButtonValue pbValue = new PlayButton.PlayButtonValue();
	
	public SoundsTableModel() {
		sounds = (SoundsXmlImpl) TwlResourceManager.getInstance().getLibrary().getSounds();
		sounds.addListener(this);
	}

	@Override
	public boolean isColumnDataSized(int column) {
		// play button
		if (column == 1) { 
			return false;
		}
		
		// all others are
		return true;
	}

	@Override
	public int getColumnTextWidth(int column, Font useFont) {
		int maxTextWidth = 0;
		
		switch (column) {
		case 0 : // sound names
			for (int i = 0; i < sounds.getSize(); i++) {
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(sounds.get(i).getName()));
			}
			break;
		case 2 : // text is duration if no errors, otherwise tooltip
			for (int i = 0; i < sounds.getSize(); i++) {
				SoundXmlImpl sound = sounds.get(i);
				SoundResource sr = sound.getSoundResource();
	        	String text = (sr != null && sr.getStatus() == SoundStatus.LOADED_OK ? 
	        			sound.getDurationFormatted() : sound.getTooltipText());
	        	
				maxTextWidth = Math.max(maxTextWidth, 
						useFont.computeTextWidth(text));
			}
			break;
		}
				
		return maxTextWidth;
	}


	public void dispose() {
		sounds.removeListener(this);
	}
	
    public int getNumRows() {
        return sounds.getSize();
    }

    public int getNumColumns() {
        return 3;
    }

    public Object getCell(int row, int column) {
    	SoundXmlImpl sound = (SoundXmlImpl) sounds.get(row);
    	SoundResource sr = sound.getSoundResource();
    	switch (column) {
        case 0:
        	if (sr == null || sr.getStatus() == SoundStatus.LOADED_OK) {
        		return sound.getName();
        	}
        	else {
        		return new DecoratedText(sound.getName(), DecoratedText.ERROR);
        	}
        case 1: {
        	if (sound.isPlayable()) {
        		pbValue.setValues(row, sound, null);
        		return pbValue;
        	}
        	else
        		return null;
        }
        case 2: 
        	if (sr != null && sr.getStatus() == SoundStatus.LOADED_OK) {
        		return sound.getDurationFormatted();
        	}
        	else {
        		// the tooltip contains the error detail
        		return new DecoratedText(sound.getTooltipText(), DecoratedText.ERROR);
        	}
        default: return "OBJECT MISSING FOR "+column;
        }
    }

    @Override
    public Object getTooltipContent(int row, int column) {
    	SoundXmlImpl sf = (SoundXmlImpl) sounds.get(row);
    	return sf.getTooltipText();
    }

	@Override
	public void itemChanged() {
		this.fireAllChanged();
	}

}
