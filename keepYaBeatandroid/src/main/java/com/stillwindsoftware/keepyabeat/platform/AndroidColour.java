package com.stillwindsoftware.keepyabeat.platform;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;

/**
 * Wrapper for the android implementation of a colour
 * @author tomas stubbs
 *
 */
public class AndroidColour implements Colour {

	private int mColorInt;

	public AndroidColour(int color) {
		mColorInt = color;
	}

	@Override
	public String getHexString() {
		return Integer.toHexString(mColorInt);
	}

	public int getColorInt() {
		return mColorInt;
	}

	
}
