package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import firstReboundGame.gameplay.ScoringManager;

public class StaticCircle extends StrikeableGamePiece
{
	protected double radius;
	protected double diameter;
	
	public StaticCircle(Location2D placement, double radius, boolean partOfPerimeter, ColorArrangement colors) {
		super(placement, partOfPerimeter, colors);
		this.radius = radius;
		this.diameter = radius * 2.0;
		this.isStatic = true;
		this.area = new Area(new Ellipse2D.Double(placement.getX() - radius, placement.getY() - radius, radius * 2, radius * 2));
	} // end of constructor

	public StaticCircle(Location2D placement, double radius, boolean partOfPerimeter, ColorArrangement colors, boolean isClipped) {
		this(placement, radius, partOfPerimeter, colors);
		if (isClipped) {
			Area clipZone = GameBounds.getInstance().getClippingVisibleZone();
			area.intersect(clipZone);
		}
	} // end of constructor

	public double getRadius() {	return this.radius; }
	public double getDiameter() { return this.diameter; }
	
	protected boolean drawPiece(Graphics2D g2) {

		if (super.drawPiece(g2))
		{
			int xPos = (int)(this.getCurrPosn().getX() - radius);
			int yPos = (int)(this.getCurrPosn().getY() - radius);
			int diameter = (int)((StaticCircle)this).getDiameter();
			
			GamePiece.panel.applyRenderingHints(g2, true);
			
			Shape drawShape = this.getArea();
			if (drawShape != null) { // clipped goal posts for example have clipped shapes (when they are drawn, which they aren't right now)
				if (this.getFillColor() != null) {
					g2.setColor(this.getFillColor());
					g2.fill(drawShape);
				}
				if (this.getStrokeColor() != null) {
					g2.setColor(this.getStrokeColor());
					g2.draw(drawShape);
				}
			} 
			else
			{
				if (this.getFillColor() != null) {
					g2.setColor(this.getFillColor());
					g2.fillOval(xPos, yPos, diameter, diameter);
				}
				if (this.getStrokeColor() != null) {
					g2.setColor(this.getStrokeColor());
					g2.drawOval(xPos, yPos, diameter, diameter);
				}
			}				
			
			GamePiece.panel.applyRenderingHints(g2, false);
						
			return true;
		}
		else
			return false;
					  
	}  // end of draw()
	
}  // end of class

