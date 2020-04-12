package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.geom.*;

public class StrikerAngleIndicator extends GuiComponentAdapter
{
	private double strikeAngle;
	private GuiText strikerAngleText;
	private double diameter;
	
	public StrikerAngleIndicator(GuiText strikerAngleText, ColorArrangement colorArrangement) {
		super(colorArrangement);
		this.strikerAngleText = strikerAngleText;
	}
	
	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		this.diameter = (Double.compare(this.placementRectangle.getHeight(), placementRectangle.getWidth()) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND 
				? placementRectangle.getHeight() : placementRectangle.getWidth());
	}

	public void setStrikeAngle(double value) { 
		this.strikeAngle = value;
		this.strikerAngleText.setText(""+(int)value);
	}
	
	public boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
			g2.setColor(this.colorArrangement.getFillColor());
	    	Ellipse2D oval = new Ellipse2D.Double(this.placementRectangle.getX()
	    			, this.placementRectangle.getY() + this.placementRectangle.getHeight() - this.diameter
	    			, this.diameter
	    			, this.diameter);
	    	g2.fill(oval);
			// draw a line at the angle
	    	double angleInRadians = Math.toRadians(this.strikeAngle);
			double sinVal = Math.sin(angleInRadians);
			double cosVal = Math.cos(angleInRadians);
			double radius = this.diameter / 2;
		  	double xChange = sinVal * radius;
		  	double yChange = cosVal * radius;
		  	int yPos = (int)(oval.getCenterY() - yChange);
		  	int xPos = (int)(oval.getCenterX() + xChange);
	
	    	g2.setColor(this.colorArrangement.getStrokeColor());
        	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); 
        	Stroke oriStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(3));
	        g2.draw(oval);
			g2.setColor(Color.gray);
			g2.drawLine((int)oval.getCenterX(), (int)oval.getCenterY(), xPos, yPos);
	    	g2.setStroke(oriStroke);
        	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF); 
	
//			g2.drawString("angle="+getStrikeAngle(), currXpos +10, currYpos -10);
//			g2.drawString(""+useStrength, strengthRect.x - 5, strengthRect.y + strengthRect.height + 15);
	    }
		return true; 
	}

}