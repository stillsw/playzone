package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;

/*
 * specialised class to do the little corner goal posts, placement should not be used directly for drawing, but reuse the transform
 */
public class GoalPost extends StaticCircle
{
	protected Arc2D.Double arc;
	protected AffineTransform transform;
	
	public GoalPost(Point2D preTransformedPlacement, double radius, double startAngle, double extent, boolean partOfPerimeter, ColorArrangement colors, AffineTransform transform) {
		super(new Location2D(transform.transform(preTransformedPlacement, null)), radius, partOfPerimeter, colors, false); // not clipped
		this.transform = transform;
		this.arc = new Arc2D.Double(preTransformedPlacement.getX() - radius, preTransformedPlacement.getY() - radius, radius * 2, radius * 2, startAngle, extent, Arc2D.OPEN);
	} // end of constructor
	
	protected final boolean drawPiece(Graphics2D g2) {
		if (super.drawPiece(g2))
		{
			if (this.getStrokeColor() != null) {
				g2.setColor(this.getStrokeColor());
				AffineTransform originalTransform = g2.getTransform();
				g2.setTransform(transform);
				g2.draw(arc);
				g2.setTransform(originalTransform);
			}
			return true;
		}
		else 
			return false;
	}
}  // end of class

