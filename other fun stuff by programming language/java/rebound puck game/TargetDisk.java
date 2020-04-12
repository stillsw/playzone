package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import firstReboundGame.gameplay.ScoringManager;

public class TargetDisk extends MoveableGamePiece
{
	protected double radius;
	protected double diameter;
	protected BufferedImage image;
	
	public TargetDisk(ScoringManager scoringManager, PlayManager playManager, Location2D placement, double radius, ColorArrangement colors) {
		super(scoringManager, playManager, placement, colors);
		this.radius = radius;
		this.diameter = radius * 2.0;
		this.setMass();
		this.makeImage();
	} // end of constructor

	public double getRadius() {	return this.radius; }
	public double getDiameter() { return this.diameter; }
	
	protected void setMass() {
		// mass depends on relative size
		this.mass = this.radius * this.radius;
		
	}
	
	public void move(int gameTick) {
		super.move(gameTick);
		
// register goals scored - NOT LIKE THIS THOUGH... IMPLEMENT SOME KIND OF LISTENER INSTEAD
//		StrikeableGamePiece[] staticObstacles = panel.getStaticObstacles();
//		for (int i = 0; i < staticObstacles.length; i++) {
//			if (staticObstacles[i] instanceof Goal) {
//				if (((Goal)staticObstacles[i]).diskScored(this, false)) // already advanced to next position
//					;
//			}
//		}
	}

	private void makeImage() {
		double diameter = (int)this.getRadius() * 2;
		Area clip = new Area(new Ellipse2D.Double(0, 0, diameter, diameter));
		double lastDiameter = diameter;
		for (int i = 0; i < 3; i++) {
			double reducedDiameter = lastDiameter - lastDiameter / 3;
			Area smallerArea = new Area(new Ellipse2D.Double((diameter - reducedDiameter) / 2, (diameter - reducedDiameter) / 2, reducedDiameter, reducedDiameter));
			if (i % 2 == 0)
				clip.subtract(smallerArea);
			else
				clip.add(smallerArea);
			lastDiameter = reducedDiameter;
		}

		if (this.image == null) {
			int shadingHeight = (int)diameter;
	    	BufferedImage shadingImage = new BufferedImage(2, shadingHeight, BufferedImage.TYPE_INT_ARGB);
	        Graphics2D g2d = shadingImage.createGraphics();        
	        // fill a gradient into the image just 2 pixels wide
	        GradientPaint paint = new GradientPaint(0, 0, ReboundGamePanel.lightShadowColour, 0, shadingHeight / 2, ReboundGamePanel.midShadowColour, true);
	        g2d.setPaint(paint);
	        g2d.fillRect(0, 0, 2, shadingHeight);
	        // make an image the full width and fill it with the shading
	        this.image = new BufferedImage((int)diameter, (int)diameter, BufferedImage.TYPE_INT_ARGB);
	        Graphics2D g2 = this.image.createGraphics();
//	        g2.setBackground(this.getFillColor());
	        g2.setColor(this.getFillColor());
	        // draw the gradient for the back part of the net
			GamePiece.panel.applyRenderingHints(g2, true);
	        g2.fillOval(0, 0, shadingHeight, shadingHeight);
	        g2.setClip(clip);
			g2.drawImage(shadingImage, 0, 0, (int)diameter, shadingImage.getHeight(), null);
	        g2d.dispose();
	        g2.dispose();
		}
	}
	
	/*
	 * make a bounding rectangle which encloses the last movement
	 */
	@Override
	protected void makeMovementBounds(double xpos1, double ypos1, double xpos2, double ypos2, double radius) {
		double originXpos = (Double.compare(xpos1, xpos2) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? xpos1 : xpos2);
		double originYpos = (Double.compare(ypos1, ypos2) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? ypos1 : ypos2);
		double width = (Double.compare(xpos1, xpos2) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? xpos2 - xpos1 : xpos1 - xpos2);
		double height = (Double.compare(ypos1, ypos2) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? ypos2 - ypos1 : ypos1 - ypos2);
		this.lastMovementBounds = new Rectangle2D.Double(originXpos - radius, originYpos - radius, width + radius * 2, height + radius * 2);
	}

	protected boolean drawPiece(Graphics2D g2) {

		if (super.drawPiece(g2))
		{
			int xPos = (int)(this.directionLv.getLocation().getX() - radius);
			int yPos = (int)(this.directionLv.getLocation().getY() - radius);
			int diameter = (int)this.getDiameter();
			
			GamePiece.panel.applyRenderingHints(g2, true);
			
			if (this.image == null || !panel.MAKE_IMAGES) {
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
			}
			else {
				g2.drawImage(this.image, xPos, yPos, null);			
				if (this.getStrokeColor() != null) {
					g2.setColor(this.getStrokeColor());
					g2.drawOval(xPos, yPos, diameter, diameter);
				}
			}
			
			GamePiece.panel.applyRenderingHints(g2, false);
						
			if (panel.showDebugging && this.lastMovementBounds != null) {
				g2.setColor(Color.orange);
				g2.draw(this.lastMovementBounds);
			}
			
			return true;
		}
		else
			return false;
					  
	}  // end of draw()
	
	@Override
	protected void makeBounds() {}

	
}  // end of class

