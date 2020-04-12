package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

import de.matthiasmann.twl.Color;

public class TwlColour implements Colour {

	private final Color twlColor;
	private final float[] RGBA = new float[4];
	
	public TwlColour(Color twlColor) {
		this.twlColor = twlColor;
		twlColor.getFloats(RGBA, 0);
	}

	public Color getTwlColor() {
		return twlColor;
	}

	@Override
	public String getHexString() {
		return twlColor.toString();
	}

	public float[] getRGBA() {
		return RGBA;
	}
}
