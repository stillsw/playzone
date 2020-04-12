package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.model.BeatTypes.DefaultBeatTypes;
import com.stillwindsoftware.keepyabeat.platform.ColourManager;

import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.model.ColorSpaceHSL;

public class TwlColourManager implements ColourManager {

	public static final int IDX_RED = 0;
	public static final int IDX_GREEN = 1;
	public static final int IDX_BLUE = 2;
	public static final int IDX_ALPHA = 3;
	public static final float OPAQUE_COLOUR = 1.0f;
	
	// default colours from localisation
	private static final Colour NORMAL_BEAT_COLOUR = 
			new TwlColour(Color.parserColor(TwlResourceManager.getInstance()
					.getLocalisedString(TwlLocalisationKeys.NORMAL_COLOUR_HEX)));
	private static final Colour ACCENT_BEAT_COLOUR = 
			new TwlColour(Color.parserColor(TwlResourceManager.getInstance()
					.getLocalisedString(TwlLocalisationKeys.ACCENT_COLOUR_HEX)));
	private static final Colour ALTERNATE_BEAT_COLOUR = 
			new TwlColour(Color.parserColor(TwlResourceManager.getInstance()
					.getLocalisedString(TwlLocalisationKeys.ALTERNATE_COLOUR_HEX)));
	private static final Colour QUIET_BEAT_COLOUR = 
			new TwlColour(Color.parserColor(TwlResourceManager.getInstance()
					.getLocalisedString(TwlLocalisationKeys.QUIET_COLOUR_HEX)));
	private static final Colour FIRST_BEAT_COLOUR = 
			new TwlColour(Color.parserColor(TwlResourceManager.getInstance()
					.getLocalisedString(TwlLocalisationKeys.FIRST_COLOUR_HEX)));
	private static final Colour MISSING_BEAT_COLOUR = new TwlColour(Color.LIGHTCYAN);

	@Override
	public Colour getDefaultColour(DefaultBeatTypes defaultBeatType) {
		switch (defaultBeatType) {
		case normal : return NORMAL_BEAT_COLOUR;
		case accent : return ACCENT_BEAT_COLOUR;
		case alternate : return ALTERNATE_BEAT_COLOUR;
		case silent : return QUIET_BEAT_COLOUR;
		case first : return FIRST_BEAT_COLOUR;
		default : return MISSING_BEAT_COLOUR;
		}
	}

	@Override
	public Colour getColour(String hexString) {
		return new TwlColour(Color.parserColor(hexString));
	}

	@Override
	public boolean hasTransparency(Colour colour) {
		return ((TwlColour)colour).getRGBA()[IDX_ALPHA] != OPAQUE_COLOUR;
	}

}
