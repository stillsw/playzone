package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.geom.*;

public class GuiText extends GuiComponentAdapter
{
	protected String text[] = new String[1];
	
	public GuiText(ColorArrangement colorArrangement, String text) {
		super(colorArrangement);
		this.setText(text);
	}
	
	public GuiText(ColorArrangement colorArrangement, String[] texts) {
		super(colorArrangement);
		this.text = texts;
	}
	
	public void setText(String text) { this.text[0] = text; }
	
	public boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
	    	int ypos = (int)(this.placementRectangle.getY() + this.placementRectangle.getHeight());

			g2.setColor(this.colorArrangement.getStrokeColor());
        	for (int i = 0; i < this.text.length; i++) {
        		int yOffset = (this.text.length - 1 - i) * g2.getFontMetrics().getAscent();
        		g2.drawString(this.text[i], (int)this.placementRectangle.getX(), ypos - yOffset);
        	}
	    }
		return true; 
	}

}