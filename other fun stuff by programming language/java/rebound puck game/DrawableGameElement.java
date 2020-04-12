package firstReboundGame;

import java.awt.*;
import java.awt.geom.Point2D;

public interface DrawableGameElement 
{
	public boolean isStatic();
	public Location2D getCurrPosn();
	public double getRadius();
	// have draw return whether to continue drawing... for subclasses to be able to call super class
	public void draw(Graphics2D g2);	
	public void drawAfterShading(Graphics2D g2);	
	public Color getFillColor();
	public Color getStrokeColor();
	
}