package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.geom.*;

public class StrikerStrengthBar extends GuiComponentAdapter
{
	private double strengthPerc;
	private GuiText strikerStrengthText;
	
	public StrikerStrengthBar(GuiText strikerStrengthText, ColorArrangement colorArrangement) {
		super(colorArrangement);
		this.strikerStrengthText = strikerStrengthText;
	}
	
	public void setStrengthPerc(double value) { 
		this.strengthPerc = value;
		this.strikerStrengthText.setText(""+(int)value);
	}
	
	public boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
			g2.setColor(this.colorArrangement.getFillColor());
			double fillHeight = (this.placementRectangle.getHeight() / 100.00) * this.strengthPerc;
	    	Rectangle2D fillRect = new Rectangle2D.Double(this.placementRectangle.getX()
	    			, this.placementRectangle.getY() + this.placementRectangle.getHeight() - fillHeight
	    			, this.placementRectangle.getWidth()
	    			, fillHeight);
	    	g2.fill(fillRect);
	    	g2.setColor(this.colorArrangement.getStrokeColor());
        	Stroke oriStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(3));
	        g2.draw(this.placementRectangle);
	    	g2.setStroke(oriStroke);
	
//			g2.drawString("angle="+getStrikeAngle(), currXpos +10, currYpos -10);
//			g2.drawString(""+useStrength, strengthRect.x - 5, strengthRect.y + strengthRect.height + 15);
	    }
		return true; 
	}

}