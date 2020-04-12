package firstReboundGame;


import java.util.*;
import java.awt.*;
import java.awt.geom.*;

public class LinearObstacle extends StrikeableGamePiece
{
	ReboundGamePanel panel = ReboundGamePanel.getInstance();
	protected LineVector2D[] lines;
	private LineVector2D closestLineToPuck = null;
	
	public LinearObstacle(LineVector2D line, boolean partOfPerimeter, ColorArrangement colors, boolean isStatic) {
		super(line.getLocation(), partOfPerimeter, colors);
		lines = new LineVector2D[1];
		lines[0] = line;
		this.isStatic = isStatic;
	} // end of constructor

	public LinearObstacle(LineVector2D[] lines, boolean partOfPerimeter, ColorArrangement colors, boolean isStatic) {
		super(lines[0].getLocation(), partOfPerimeter, colors);
		this.lines = lines;
		this.isStatic = isStatic;
	} // end of constructor

	public static LineVector2D[] makeObstacleShape(Point2D[] point2ds, boolean joinLines, boolean joinEnds) {
		Location2D[] cornerPosns = new Location2D[point2ds.length];
		for (int i = 0; i < cornerPosns.length; i++)
			cornerPosns[i] = new Location2D(point2ds[i]);
		
		return makeObstacleShape(cornerPosns, joinLines, joinEnds);
	}
	public static LineVector2D[] makeObstacleShape(Location2D[] cornerPosns, boolean joinLines, boolean joinEnds) {
		
		// joinLines = false (pairs of points make separate lines) meaningless to have joinEnds = true
		int numberOfLines = (!joinLines ? (int)(cornerPosns.length / 2) :
			(joinEnds ? cornerPosns.length : cornerPosns.length - 1));
		
		LineVector2D[] lines = new LineVector2D[numberOfLines];
		int j = 0;
		for (int i = 1; i < cornerPosns.length; i++) {
			if (joinLines || i % 2 == 1) {
				lines[j++] = new LineVector2D(cornerPosns[i - 1], cornerPosns[i]);
				if (i == cornerPosns.length - 1 && joinEnds)
					lines[j++] = new LineVector2D(cornerPosns[i], cornerPosns[0]);
			}
		}
		return lines;
	}
	
	public LineVector2D[] getLines() { return this.lines; }
	
	/*
	 * make a bounding rectangle which encloses this piece
	 */
	@Override
	protected void makeBounds() {
		// first see if super class can do it because there's an area (there would be for a wall for instance)
		super.makeBounds();
		if (this.pieceBounds == null) {
			double leastXpos = Constants.VERY_LARGE_NUMBER, leastYpos = Constants.VERY_LARGE_NUMBER, mostXpos = -Constants.VERY_LARGE_NUMBER, mostYpos = -Constants.VERY_LARGE_NUMBER;
			for (int i = 0; i < this.lines.length; i++) {
				double xpos1 = this.lines[i].getLocation().getX();
				double ypos1 = this.lines[i].getLocation().getY();
				double xpos2 = this.lines[i].getEndLocation().getX();
				double ypos2 = this.lines[i].getEndLocation().getY();
				if (Double.compare(xpos1, leastXpos) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) leastXpos = xpos1;
				if (Double.compare(mostXpos, xpos1) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) mostXpos = xpos1;
				if (Double.compare(xpos2, leastXpos) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) leastXpos = xpos2;
				if (Double.compare(mostXpos, xpos2) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) mostXpos = xpos2;
				if (Double.compare(ypos1, leastYpos) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) leastYpos = ypos1;
				if (Double.compare(mostYpos, ypos1) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) mostYpos = ypos1;
				if (Double.compare(ypos2, leastYpos) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) leastYpos = ypos2;
				if (Double.compare(mostYpos, ypos2) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) mostYpos = ypos2;
			}
			double width = mostXpos - leastXpos;
			double height = mostYpos - leastYpos;
			this.pieceBounds = new Rectangle2D.Double(leastXpos, leastYpos, width, height);

		}
	}
	
	protected boolean drawPiece(Graphics2D g2) {

		if (super.drawPiece(g2)) {
			if (this.area == null) {
				g2.setColor(this.getStrokeColor());
				for (int i = 0; i < lines.length; i++) {
					int xPos = (int)lines[i].getLocation().getX();
					int yPos = (int)lines[i].getLocation().getY();
					int xEndPos = (int)lines[i].getEndLocation().getX();
					int yEndPos = (int)lines[i].getEndLocation().getY();
					g2.drawLine(xPos, yPos, xEndPos, yEndPos);
				}
			}
			
			if (ReboundGamePanel.showDebugging && closestLineToPuck != null) // draw nearest point to puck
			{
				// draw the closest point in orange
				Location2D start = closestLineToPuck.getLocation();
				Location2D end = closestLineToPuck.getEndLocation();
				int startX = (int)start.getX();
				int startY = (int)start.getY();
				int endX = (int)end.getX();
				int endY = (int)end.getY();
				g2.setColor(Color.ORANGE);
				g2.drawLine(startX, startY, endX, endY);
				g2.drawOval(endX - 5, endY -5, 10, 10);
			}
			return true;
		}
		else 
			return false;
		  
	}  // end of draw()
	
	public void update(int gameTick) {
		
		super.update(gameTick);
		
		if (ReboundGamePanel.showDebugging) // draw nearest point to puck
		{
			Location2D puckPosn = panel.puck.getCurrPosn();
			double closestDistance = Vector2D.LONG_DISTANCE;
			closestLineToPuck = null;
			for (int i = 0; i < lines.length; i++) {
				LineVector2D lineBack = lines[i].getShortestLineToPoint(puckPosn);
				if (lineBack != null && puckPosn.getDistance(lineBack.getLocation()) < closestDistance) {
					closestDistance = puckPosn.getDistance(lineBack.getLocation());
					closestLineToPuck = lineBack;
				}
			}
		}
	}
	
	@Override
	public double getRadius() {	return 0.0; }
	
}  // end of class

