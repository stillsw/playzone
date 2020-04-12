package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.platform.PlatformAnimationState;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;

import de.matthiasmann.twl.renderer.AnimationState;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLFont;

public class TwlFont implements PlatformFont {

	private LWJGLFont font;

	public TwlFont(LWJGLFont font) {
		this.font = font;
	}
	
	@Override
	public float computeTextWidth(String text) {
		return font.computeTextWidth(text);
	}

	@Override
	public void drawText(PlatformAnimationState animationState, int x, int y, String text) {
		font.drawText((AnimationState) animationState, x, y, text);
	}

	@Override
	public float getNumbersHeight() {
		return font.getLineHeight();
	}

	@Override
	public boolean yIsBaseline() {
		return false;
	}

}
