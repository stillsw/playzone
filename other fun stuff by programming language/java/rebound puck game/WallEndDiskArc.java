package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;

/*
 * specialised class to do the little corner goal posts, placement should not be used directly for drawing, but reuse the transform
 */
public class WallEndDiskArc extends StaticCircle
{
	protected Arc2D.Double arc;
	protected Location2D arcStartPoint;
	protected Location2D arcEndPoint;
	
	public WallEndDiskArc(Location2D placement, double radius, int wall, boolean isLeftEnd, boolean partOfPerimeter, ColorArrangement colors) {
		super(placement, radius, partOfPerimeter, colors);
		this.isStatic = true;
		
		// which wall and which end of wall dictates the start angle
		double extent = 90.0; // extent is always 90 degrees
		double startAngle = 180.0; // for left north
		if (wall == WallFactory.WALL_N && isLeftEnd) ; // leave as is
		else if (wall == WallFactory.WALL_N && !isLeftEnd) startAngle = 270.0;
		else if (wall == WallFactory.WALL_E && isLeftEnd) startAngle = 90.0;
		else if (wall == WallFactory.WALL_E && !isLeftEnd) startAngle = 180.0;
		else if (wall == WallFactory.WALL_S && isLeftEnd) startAngle = 0.0;
		else if (wall == WallFactory.WALL_S && !isLeftEnd) startAngle = 90.0;
		else if (wall == WallFactory.WALL_W && isLeftEnd) startAngle = 270.0;
		else if (wall == WallFactory.WALL_W && !isLeftEnd) startAngle = 0.0;

		this.arc = new Arc2D.Double(placement.getX() - radius, placement.getY() - radius, radius * 2, radius * 2, startAngle, extent, Arc2D.OPEN);
		this.area = new Area(new Arc2D.Double(placement.getX() - radius, placement.getY() - radius, radius * 2, radius * 2, startAngle, extent, Arc2D.PIE));
		
		if (isLeftEnd) {
			this.arcStartPoint = new Location2D(arc.getStartPoint());
			this.arcEndPoint = new Location2D(arc.getEndPoint());
		}
		else {
			this.arcStartPoint = new Location2D(arc.getEndPoint());
			this.arcEndPoint = new Location2D(arc.getStartPoint());
		}
	} // end of constructor
	
	public Location2D getOutsidePoint() { return this.arcStartPoint; }
	
	protected final boolean drawPiece(Graphics2D g2) {
		if (super.drawPiece(g2))	{	
			if (this.getStrokeColor() != null) {
				g2.setColor(this.getStrokeColor());
				g2.draw(arc);
			}
			return true;
		}
		else
			return false;
	}
}  // end of class

