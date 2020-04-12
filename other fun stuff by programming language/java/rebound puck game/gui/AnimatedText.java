package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;

public class AnimatedText extends GuiText
{
	public static final double SECONDS_TO_ANIMATE = 3;
	protected static final double SECONDS_TO_CYCLE = .5; 
	protected static final TemporalComponentsManager temporalComponentsManager = TemporalComponentsManager.getInstance();
	
	private int nextColourChangeTick;
	protected int cycle = -1;
	protected Color strokeColor;
	protected Color fillColor;
	protected Shape textShape;
	protected boolean keepXposFromFirstTime = false;
	protected int xPos = -1;
	
	public AnimatedText(boolean ignoreGameStates, Rectangle2D placementRectangle, ColorArrangement colorArrangement, ColorArrangement boxColorArrangement, String text) {
		super(colorArrangement, text);
		new TemporalPopupBox(this, false, ignoreGameStates, placementRectangle, boxColorArrangement);
		this.setPlacement(placementRectangle, null);
	}
	
	@Override
	public void setText(String text) {
		// ditch the previous shape if there was one and this text is different to the last
		if (this.text[0] == null || text == null || (this.text[0] != null && this.textShape != null && !this.text[0].equals(text))) {
			this.textShape = null;
		}
		super.setText(text); 
	}
	

	@Override
	public void update(int gameTick) {
		// will cycle through the 6 colours, set which one to show next
		if (this.nextColourChangeTick < gameTick) {
			this.nextColourChangeTick = temporalComponentsManager.getGameTickSecondsFromNow(AnimatedText.SECONDS_TO_CYCLE);
			this.cycle++;
			if (this.cycle > 5)
				this.cycle = 0;
			switch (this.cycle) { // NOTE: changes here need to be reflected in AnimatedPauseMessage too
			case 0: this.strokeColor = this.colorArrangement.getStrokeColor(); this.fillColor = this.colorArrangement.getFillColor(); break;
			case 1: this.strokeColor = this.colorArrangement.getFillColor(); this.fillColor = this.colorArrangement.getStruckStrokeColor(); break;
			case 2: this.strokeColor = this.colorArrangement.getStruckStrokeColor(); this.fillColor = this.colorArrangement.getStruckFillColor(); break;
			case 3: this.strokeColor = this.colorArrangement.getStruckFillColor(); this.fillColor = this.colorArrangement.getSecondaryFillColor(); break;
			case 4: this.strokeColor = this.colorArrangement.getSecondaryFillColor(); this.fillColor = this.colorArrangement.getStruckSecondaryFillColor(); break;
			case 5: this.strokeColor = this.colorArrangement.getStruckSecondaryFillColor(); this.fillColor = this.colorArrangement.getStrokeColor(); break;
			}
		}
		
	}

	@Override
	public boolean drawGuiComponent(Graphics2D g2) {
		if (this.text[0] != null) {
			// init the shape the first time in
			if (this.textShape == null) {
		     	FontRenderContext fontRenderContext = g2.getFontRenderContext();
		    	Font font = new Font("sansserif", Font.BOLD, (int)this.placementRectangle.getWidth() / 18);
		    	TextLayout textLayout = new TextLayout(this.text[0], font, fontRenderContext);
		    	float textWidth = (float) textLayout.getBounds().getWidth();
		    	float textHeight = (float) textLayout.getBounds().getHeight();
		    	AffineTransform transform = new AffineTransform();
		    	if (!this.keepXposFromFirstTime || this.xPos == -1) // some messages don't want to move around, like Pause...
		    		this.xPos = (int)(this.placementRectangle.getCenterX() - textWidth / 2);
		    	transform.setToTranslation(this.xPos, this.placementRectangle.getCenterY() - textHeight / 2);
		    	this.textShape = textLayout.getOutline(transform);
			}
			try { // timing of a key press could generate a change out of the animation loop
				g2.setColor(this.fillColor);
		    	g2.fill(this.textShape);
				g2.setColor(this.strokeColor);
		    	g2.draw(this.textShape);
			}
			catch (NullPointerException e) {}
		}
    	return true; 
	}

}