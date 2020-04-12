package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlFont;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.DrawNumbersPlacement;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.RhythmAlignment;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.SubZoneActionable;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;

import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Event.Type;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLFont;

public class PlayerRhythmCanvas extends Widget implements DrawingSurface {// implements RhythmChangedCallback {

	// rhythm draughter needs dimension as a rectangle
	MutableRectangle canvasDimension = new MutableRectangle(0f, 0f, 0f, 0f);
	private boolean draughterIsInitialised = false;
	
	protected DraughtTargetGoverner targetGoverner;
    protected BaseRhythmDraughter rhythmDraughter;
	private PlatformEventListener eventListener;
	private TwlGuiManager guiManager;
	
	public PlayerRhythmCanvas(BaseRhythmDraughter rhythmDraughter) {
		setTheme("playerRhythmCanvas");
		this.rhythmDraughter = rhythmDraughter;
		guiManager = (TwlGuiManager) TwlResourceManager.getInstance().getGuiManager();
	}

	@Override
	protected void layout() {
		resetRhythmBounds();
	}

	// works out the bounds of each beat and puts the results into each drawn beat
	private void resetRhythmBounds() {
		float x = getX();
		float y = getY();
		float w = getWidth();
		float h = getHeight();

		canvasDimension.setXYWH(x, y, w, h);

		// have what's needed to create the draughter, set it all up and do a first arrange
		if (draughterIsInitialised) {
			// already set up, just rearrange since layout is invalid
			rearrangeRhythm();
		}
		else if (targetGoverner != null) {
			TwlResourceManager.getInstance().log(LOG_TYPE.debug, this, "PlayerRhythmCanvas.resetRhythmBounds: init rhythmDraughter");
			rhythmDraughter.initTarget(targetGoverner, this);
			rhythmDraughter.initDrawing();
			draughterIsInitialised = true;
		}
		else {
			invalidateLayout(); // have it go again 
		}

	}

	/**
	 * Allow EditRhythmCanvas to override as it only needs to call arrangeRhythm()
	 */
	protected void rearrangeRhythm() {
		// this class needs to set first time in because the beats don't anchor by themselves except when played,
		// needed if layout is because of a display resize
		rhythmDraughter.resetFirstTimeForRhythm();
		rhythmDraughter.arrangeRhythm();
	}
	
	@Override
	protected void paint(GUI gui) {
		super.paint(gui);
		if (draughterIsInitialised) {
			rhythmDraughter.drawRhythm();
		}
	}

	// try to get the canvas as big as possible, to the max size the parent will allow it
	@Override
	public int getPreferredInnerHeight() {
		return getParent().getInnerHeight();
	}

	@Override
	public int getPreferredInnerWidth() {
		return getParent().getWidth();
	}

	// in order to have it as large as possible but not larger than the space available, don't set a minimum
	@Override
	public int getMinWidth() {
		return 0;
	}

	@Override
	public int getMinHeight() {
		return 0;
	}

	@Override
	public void destroy() {
		if (targetGoverner != null) {
			targetGoverner.destroy();
		}
		super.destroy();
	}

	// get some defaults from the theme
	@Override
	protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
		applyThemeMakeTargetGoverner(themeInfo);
	}
	
	protected void applyThemeMakeTargetGoverner(ThemeInfo themeInfo) {
		// after a theme switch don't remake the target governer
		if (targetGoverner == null) {
			// make a non-sharing target governer
			targetGoverner = new DraughtTargetGoverner(TwlResourceManager.getInstance(), false, SubZoneActionable.ONLY_WITH_SUB_BEATS);
	
			// additional settings
			targetGoverner.setRhythmAlignment(RhythmAlignment.MIDDLE);
			targetGoverner.setHorizontalRhythmMargins(10, 10);
			targetGoverner.setVerticalRhythmMargins(45, 75);
			targetGoverner.setMaxBeatsAtNormalWidth(10);
			
			targetGoverner.setPlayed(true);
		}

		TwlFont fullBeatsFontNormal = new TwlFont(themeInfo.getParameterValue("fullBeatsFontNormal", false, LWJGLFont.class));
		TwlFont fullBeatsFontSmall = new TwlFont(themeInfo.getParameterValue("fullBeatsFontSmall", false, LWJGLFont.class));

		// where to draw numbers for the beats and what font to use (alternatives if the first doesn't fit)
		// settings can override this (see persistence states)
		targetGoverner.setDrawNumbers(DrawNumbersPlacement.ABOVE, DrawNumbersPlacement.ABOVE.getDefaultMargin(), 3.0f, fullBeatsFontNormal, fullBeatsFontSmall);		
	}

	@Override
	protected boolean handleEvent(Event evt) {
		// only interested in and mouse click, esc is coming direct from root, see triggeredBackEvent below
		if (eventListener != null && evt.isMouseEvent()) {
			float x = evt.getMouseX() - getX();
			float y = evt.getMouseY() - getY();

			if (evt.isMouseEvent()) {
				if (evt.getType() == Type.MOUSE_CLICKED) {
					// click happens if button down/up in same place (if any dragging there is no click event)
					// easier to handle the sequence manually inside the eventListener (no duplicates that way)
				}
				else if (evt.getType() == Type.MOUSE_BTNDOWN && (evt.getMouseButton() == Event.MOUSE_RBUTTON
						)) {
					eventListener.handlePointerSelect(x, y);
				}
				else if (evt.getType() == Type.MOUSE_BTNDOWN) {
					// could be the prelude to dragging or just a click happening
					eventListener.handlePointerContact(x, y);
				}
				else if (evt.getType() == Type.MOUSE_BTNUP) {
					// could be the end of dragging or just a click
					// this event fires before click
					eventListener.handlePointerRelease(x, y);
				}
				else if (evt.getType() == Type.MOUSE_DRAGGED) {
					// as soon as get one of these, click is not going to happen anymore, only up
					eventListener.handlePointerDrag(x, y);
				}
			}
		}
		
		return evt.isMouseEventNoWheel();
	}
	
	//---------------------- drawing surface methods

	@Override
	public MutableRectangle getDrawingRectangle() {
		return canvasDimension;
	}

	/**
	 * Got a x,y to draw the image, but it's relative to the canvas only, to draw here have to translate to 
	 * the display.
	 */
	@Override
	public void drawImage(PlatformImage image, float x, float y, float w, float h) {
		if (getY() < guiManager.getScreenHeight()) {
			image.draw((int)(this.getX() + x), (int)(this.getY() + y), (int)w, (int)h);
		}
	}

	/**
	 * Same considerations for clipping
	 */
	@Override
	public void drawImage(PlatformImage image, float x, float y, float w, float h, float clipx, float clipy, float clipw, float cliph) {

		if (getY() < guiManager.getScreenHeight()) {
			guiManager.pushClip(this.getX() + clipx, this.getY() + clipy, clipw, cliph);
			drawImage(image, x, y, w, h);
			guiManager.popClip();
		}
	}

	@Override
	public void drawText(PlatformFont font, int number, float x, float y) {
		drawText(font, Integer.toString(number), x, y);
	}

	@Override
	public void drawText(PlatformFont font, String text, float x, float y) {
		if (getY() < guiManager.getScreenHeight()) {
			font.drawText(guiManager.getAnimationState(), (int)(this.getX() + x), (int)(this.getY() + y), text);
		}
	}

	@Override
	public void registerForEvents(PlatformEventListener eventListener) {
		// interested in all events 
		this.eventListener = eventListener;
	}

	@Override
	public boolean triggeredBackEvent() {
		if (eventListener != null) {
			return eventListener.handleBack();
		}
		else {
			return false;
		}
	}

	@Override
	public void pushTransform(float rotation, float transX, float transY) {
		guiManager.pushTransform(rotation, transX, transY);
	}

	@Override
	public void popTransform() {
		guiManager.popTransform();
	}


}
