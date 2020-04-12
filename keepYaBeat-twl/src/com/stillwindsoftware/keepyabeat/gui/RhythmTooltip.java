package com.stillwindsoftware.keepyabeat.gui;

import org.lwjgl.input.Mouse;

import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;

import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Container;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Widget;

public class RhythmTooltip extends BoxLayout {

	private Rhythm rhythm;
	private Label name;
	private TagsFlowList tagsFlowList;
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private TwlGuiManager guiManager = (TwlGuiManager) resourceManager.getGuiManager();
	private DraughtTargetGoverner tooltipTargetGoverner;
	private boolean manageOwnPosition;
	
	public RhythmTooltip(Rhythm rhythm, DraughtTargetGoverner tooltipTargetGoverner, boolean manageOwnPosition) {
		super(BoxLayout.Direction.VERTICAL);
		this.rhythm = rhythm;
		this.tooltipTargetGoverner = tooltipTargetGoverner;
		this.manageOwnPosition = manageOwnPosition;
		setTheme("rhythmTooltip");
		name = new Label(rhythm.getName());
		name.setTheme("rhythmName");
		add(name);
		add(new TooltipDrawing());

		// no point putting inside a scrollpane, because this is a tooltip that disappears
		// when the mouse moves, but need to put it inside a container
		Container tagsBox = new Container();
		tagsBox.setTheme("tagsBox");
		tagsBox.setClip(true);// can't scroll, so cut off when it's too much
		add(tagsBox);
		tagsFlowList = new TagsFlowList(rhythm.getLibrary().getTags().getRhythmTags(rhythm));
		tagsFlowList.setTheme("tagsListFlow");
		tagsBox.add(tagsFlowList);
	}
	
	@Override
	protected void layout() {
		super.layout();
		// adjust the flow list size first, so it can influence this size
		tagsFlowList.adjustSize();
		adjustSize();
		
		if (manageOwnPosition) {
			int windowHeight = guiManager.getScreenHeight();
			int height = getHeight();			
			int y = windowHeight - Mouse.getY()-45; // about halfway into the rhythm drawing
			y = Math.min(y, windowHeight - height);
			setPosition(Mouse.getX()+5, y);
		}
		else {
			// it pops up in a tooltip window
			if (getParent().getClass().getName().contains("TooltipWindow")) {
				getParent().adjustSize();
			}
		}
	}

	/**
	 * Return the tooltip window to its normal theme
	 */
	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		super.beforeRemoveFromGUI(gui);
		if (getParent().getClass().getName().contains("TooltipWindow")) {
			getParent().setTheme("tooltipwindow");
			getParent().reapplyTheme();
		}

	}

	/**
	 * Change the tooltip window to a theme without a border, it's needed for this widget only
	 * because the background shows around the edge and it's ugly.
	 */
	@Override
	protected void afterAddToGUI(GUI gui) {
		super.afterAddToGUI(gui);
		if (getParent().getClass().getName().contains("TooltipWindow")) {
			getParent().setTheme("backlesstooltipwindow");
			getParent().reapplyTheme();
		}
	}

	/**
     * A tooltip that shows a larger rhythm
     */
    private class TooltipDrawing extends Widget implements DrawingSurface {

    	private BaseRhythmDraughter tooltipRhythmDraughter;
    	private boolean tooltipDraughterIsInitialised = false;
    	
		public TooltipDrawing() {
			setTheme("rhythm");
		}
		
		@Override
		protected void layout() {
			super.layout();
			// have what's needed to create the draughter, set it all up and do a first arrange
			if (tooltipDraughterIsInitialised) {
				// already set up, just rearrange since layout is invalid
				tooltipRhythmDraughter.arrangeRhythm();
			}
			else {
		        if (tooltipRhythmDraughter == null) {
		        	tooltipRhythmDraughter = new BaseRhythmDraughter(rhythm, false);
		        }
		        else {
		        	tooltipRhythmDraughter.initModel(rhythm, false);
		        }

				// since the target is shared, make sure the numbers cache is re-initialised
		        tooltipRhythmDraughter.rhythmUpdated();

				tooltipRhythmDraughter.initTarget(tooltipTargetGoverner, this);
				tooltipRhythmDraughter.initDrawing();
				tooltipDraughterIsInitialised = true;
			}
		}

		@Override
		protected void paint(GUI gui) {
			tooltipRhythmDraughter.drawRhythm();
		}

		@Override
		public MutableRectangle getDrawingRectangle() {
			return MutableRectangle.getTransportDimension(getInnerX(), getInnerY(), getInnerWidth(), getInnerHeight());
		}
		
		@Override
		public void drawImage(PlatformImage image, float x, float y, float w, float h) {
			image.draw((int)(this.getX() + x), (int)(this.getY() + y), (int)w, (int)h);
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
			font.drawText(guiManager.getAnimationState(), (int)(this.getX() + x), (int)(this.getY() + y), Integer.toString(number));
		}

		@Override
		public void registerForEvents(PlatformEventListener eventListener) {
		}
		@Override
		public boolean triggeredBackEvent() {
			return false;
		}

		@Override
		public void drawText(PlatformFont font, String text, float x, float y) {
			// not used here			
		}

		@Override
		public void pushTransform(float rotation, float transX, float transY) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void popTransform() {
			// TODO Auto-generated method stub
			
		}
    }
}
