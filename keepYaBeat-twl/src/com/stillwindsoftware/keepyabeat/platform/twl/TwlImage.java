package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.platform.ColourManager.Colour;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;

import de.matthiasmann.twl.renderer.Image;

public class TwlImage implements PlatformImage {

	private Image image;
	private TwlGuiManager guiManager;
	
	public TwlImage(Image image) {
		this.image = image;
		guiManager = (TwlGuiManager) TwlResourceManager.getInstance().getGuiManager();
	}
	
	@Override
	public void draw(int x, int y, int w, int h) {
		image.draw(null, x, y, w, h);
	}

	@Override
	public int getWidth() {
		return image.getWidth();
	}

	@Override
	public int getHeight() {
		return image.getHeight();
	}

	@Override
	public void pushTintColour(Colour colour) {
		float[] RGBA = ((TwlColour) colour).getRGBA();
		guiManager.pushTintColour(RGBA[TwlColourManager.IDX_RED]
				, RGBA[TwlColourManager.IDX_GREEN]
				, RGBA[TwlColourManager.IDX_BLUE]
				, RGBA[TwlColourManager.IDX_ALPHA]);
	}

	@Override
	public void popTintColour() {
		guiManager.popTintColour();
	}
		
	//-------------------- not used in twl
	
	@Override
	public void release() {
	}

	@Override
	public boolean isReleased() {
		return true; // let it seem like it did it
	}

	@Override
	public void initSize(int w, int h) {
	}

	@Override
	public void initSize(PlatformImage rulesSourceImg) {
	}

	@Override
	public float getStrokeWidth() {
		return 0;
	}

	@Override
	public void setStrokeWidth(float w) {
	}

}
