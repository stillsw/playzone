package firstReboundGame;


import java.util.*;
import java.awt.*;
import java.awt.geom.*;

/*
 * Special kind of linearObstacle for walls
 */
public class WallPiece extends LinearObstacle
{
	public static final int WALL_END_TYPE_NONE = -1;
	public static final int WALL_END_TYPE_ABRUPT = 0;
	public static final int WALL_END_TYPE_ROUNDED = 1;
	public static final int WALL_END_TYPE_GOAL = 2;
	public static final int WALL_END_TYPE_CORNER_JOIN = 3;
	
	public static final int WALL_LINE_DIRECTION_LEFT_TO_RIGHT = 0;
	public static final int WALL_LINE_DIRECTION_RIGHT_TO_LEFT = 1;
	
	private int leftEndType = WALL_END_TYPE_NONE;
	private int rightEndType = WALL_END_TYPE_NONE;
	private double endRadius;
	private Vector2D toEdgeV;
	private double wallThickness;
	private int wall;
	private int lineDirection;
	private ColorArrangement colors;
//	private ColorArrangement linesColors; // 
	private Location2D wallOrigin;
	
	public WallPiece(LineVector2D line, int lineDirection, int wall, Vector2D toEdgeV, int leftEndType, int rightEndType, double endRadius, double wallThickness, boolean partOfPerimeter, ColorArrangement colors) {
		super(line, partOfPerimeter, colors, true); // always static

		this.leftEndType = leftEndType;
		this.rightEndType = rightEndType;
		this.endRadius = endRadius;
		this.toEdgeV = toEdgeV;
		this.wall = wall;
		this.lineDirection = lineDirection;
		this.wallThickness = wallThickness;
		this.colors = colors;
	} // end of constructor

	public void makePieces(ArrayList<StrikeableGamePiece> pieces) { 

		pieces.add(this);
		
		this.setWallOrigin();
		this.makeArea();
		
		// call once for each end
		this.fillWallEndArea(leftEndType, true);
		this.fillWallEndArea(rightEndType, false);
		this.makeWallEndArtifacts(leftEndType, true, pieces);
		this.makeWallEndArtifacts(rightEndType, false, pieces);		
		
		if (this.partOf != null)
			this.partOf.appendArea(this.getArea());
	}

	/*
	 * the wall piece is a rectangle formed by the line and the vector to the edge, for the area
	 */
	private void makeArea() {
		Location2D origin = this.wallOrigin.clone();
		double width = 0.0, height = 0.0;
		if (wall == WallFactory.WALL_N || wall == WallFactory.WALL_S) {
			width = this.lines[0].getLength();
			height = this.wallThickness;
			origin.setLocation(origin.getX(), origin.getY());
		}
		else {
			width = this.wallThickness;
			height = this.lines[0].getLength();
			origin.setLocation(origin.getX(), origin.getY());
		}

		this.area = new Area(new Rectangle2D.Double(origin.getX(), origin.getY(), width, height));
	}
	
	private void setWallOrigin() {
		if (wall == WallFactory.WALL_N || wall == WallFactory.WALL_S) {
			// for south wall xpos is the right end of the line as viewed clockwise from centre 
			// (in other words, the left as you look from the screen!)
			// for north it's the left end of the line plus the toEdgeV vector 			
			Location2D leftMostPosn = ((lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT && wall == WallFactory.WALL_N)
									|| (lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT && wall == WallFactory.WALL_S)
									? this.lines[0].getLocation() : this.lines[0].getEndLocation()); 
			LineVector2D toEdgeLineV = new LineVector2D(leftMostPosn, this.toEdgeV);
			this.wallOrigin = (wall == WallFactory.WALL_S ? leftMostPosn : toEdgeLineV.getPointOnLine(this.wallThickness));
		}
		else {
			// for east wall xpos is the left end of the line as viewed clockwise from centre 
			// for west it's the right end of the line plus the toEdgeV vector 			
			Location2D leftMostPosn = ((lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT && wall == WallFactory.WALL_E)
									|| (lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT && wall == WallFactory.WALL_W)
									? this.lines[0].getLocation() : this.lines[0].getEndLocation()); 
			LineVector2D toEdgeLineV = new LineVector2D(leftMostPosn, this.toEdgeV);
			this.wallOrigin = (wall == WallFactory.WALL_E ? leftMostPosn : toEdgeLineV.getPointOnLine(this.wallThickness));
		}
	}
	
	private void fillWallEndArea(int endType, boolean isLeftEnd) {
		// get the dimensions of the end piece which is a rectangle the width of the radius of the wall end that has to be
		// added to the end
		// either just as that dimension if abrupt or goal ended
		// or rounded
		// or extended to corner
		// endRadius = 0 means this is unnecessary anyway, the wall is already complete without end piece
		double originalLineLen = this.lines[0].getLength();
		boolean lengthLessThanRadius = Double.compare(originalLineLen, this.endRadius) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND;
		if (Double.compare(this.endRadius, 0.0) != Constants.DOUBLE_COMPARE_EQUAL && endType == WallPiece.WALL_END_TYPE_ROUNDED) {
			// get the point top/left corner for the piece
			double width = 0.0, height = 0.0;
			double diameter = this.endRadius * 2;
			double endPieceWidth = 0.0, endPieceHeight = 0.0;
			double endPieceLen = (lengthLessThanRadius ? originalLineLen : this.endRadius);

			if (wall == WallFactory.WALL_N || wall == WallFactory.WALL_S) {
				width = diameter;
				endPieceHeight = height = this.wallThickness;
				endPieceWidth = endPieceLen;
			}
			else {
				endPieceWidth = width = this.wallThickness;
				height = diameter;
				endPieceHeight = endPieceLen;
			}
			// confusing, but solution to find the correct place isn't that hard
			double xpos = this.wallOrigin.getX(); 
			double ypos = this.wallOrigin.getY();
			// make a 2nd piece which is a rectangle just of the actual size that needs to be made for the end
			// the intersect of this 2nd piece with the rounded rectangle will be added to the wall
			// after the 2nd piece is used to remove the end that is already put there from making the wall piece
			double endPieceXpos = xpos;
			double endPieceYpos = ypos;
			// the position of the end piece is determined by placement and diameter
			if ((wall == WallFactory.WALL_N 
					&& ((!isLeftEnd)))
			|| (wall == WallFactory.WALL_S
				&& ((isLeftEnd)))) {
					xpos += (originalLineLen - diameter);
					endPieceXpos += (originalLineLen - endPieceLen);
			}
			else if ((wall == WallFactory.WALL_E 
					&& ((!isLeftEnd)))
			|| (wall == WallFactory.WALL_W
				&& ((isLeftEnd)))) {
					ypos += (originalLineLen - diameter);
					endPieceYpos += (originalLineLen - endPieceLen);
			}

			Area rectArea = null;
//			if (endType == WALL_END_TYPE_ROUNDED)
				rectArea = new Area(new RoundRectangle2D.Double(xpos, ypos, width, height, diameter, diameter));
//			else
//				rectArea = new Area(new Rectangle2D.Double(xpos, ypos, width, height));
			
			// make the very end piece which is just the thickness of the radius, subtract that from the wall
			Area justEndPiece = new Area(new Rectangle2D.Double(endPieceXpos, endPieceYpos, endPieceWidth, endPieceHeight));
			if (this.area != null)
				this.area.subtract(justEndPiece);

			ra = (Area)rectArea.clone();
			jep = justEndPiece;
			
			// keep the part of the rounded rect which is under the end piece and append that to the area
			rectArea.intersect(justEndPiece);
			this.appendArea(rectArea);
		} // endRadius != 0
	}
Area jep;
Area ra;
	
	
/*
 *	// abrupt means just draw a line to the edge
	// rounded means clip the line end and the line to the edge by the radius
	// goal means same as abrupt but no line to edge
	// corner join also means abrupt with no line to edge

 */
	private void makeWallEndArtifacts(int endType, boolean isLeftEnd, ArrayList<StrikeableGamePiece> pieces) {
		// by now the superclass has created the line... so can access and modify it		
		// rounded means clip the line end and the line to the edge by the radius
		if (endType == WALL_END_TYPE_ROUNDED) {
			double newLength = this.lines[0].getLength() - this.endRadius;
			this.lines[0].setLength((Double.compare(newLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) ? 0.0 : newLength);
			
			if (isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT
					|| !isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT) {
				// find a point on the line to make the new start point
				Location2D newStartPlace = this.lines[0].getPointOnLine(this.endRadius);
				this.lines[0].changeLocation(newStartPlace);
			}
			
			// the position of the end piece is towards the edge by the amount of the radius
			double xpos = ((isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT
					|| !isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT)
					? this.lines[0].getLocation().getX() : this.lines[0].getEndLocation().getX()); 
			double ypos = ((isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT
					|| !isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT)
					? this.lines[0].getLocation().getY() : this.lines[0].getEndLocation().getY()); 
			
			if (wall == WallFactory.WALL_N) ypos -= this.endRadius;
			else if (wall == WallFactory.WALL_E) xpos += this.endRadius;
			else if (wall == WallFactory.WALL_S) ypos += this.endRadius;
			else if (wall == WallFactory.WALL_W) xpos -= this.endRadius;

			// and make a rounded wall end piece
			WallEndDiskArc wallEnd = new WallEndDiskArc(new Location2D(xpos, ypos), this.endRadius, this.wall, isLeftEnd, this.partOfPerimeter, this.colors);
			wallEnd.setPartOf(this);
			this.appendArea(wallEnd.getArea());
			pieces.add(wallEnd);

			LineVector2D lineToEdge = new LineVector2D(wallEnd.getOutsidePoint(), this.toEdgeV);
			lineToEdge.setLength(this.toEdgeV.getLength() - this.endRadius);
			LinearObstacle wallSide = new LinearObstacle(lineToEdge, this.partOfPerimeter, this.colors, true);
			wallSide.setPartOf(this);
			pieces.add(wallSide);
		}
		else if (endType == WALL_END_TYPE_ABRUPT) {
		     // abrupt means just make a line to the edge
			double xpos = ((isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT
					|| !isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT)
					? this.lines[0].getLocation().getX() : this.lines[0].getEndLocation().getX()); 
			double ypos = ((isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT
					|| !isLeftEnd && lineDirection == WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT)
					? this.lines[0].getLocation().getY() : this.lines[0].getEndLocation().getY()); 
			
			LineVector2D lineToEdge = new LineVector2D(new Location2D(xpos, ypos), this.toEdgeV);
			LinearObstacle wallSide = new LinearObstacle(lineToEdge, this.partOfPerimeter, this.colors, true);
			wallSide.setPartOf(this);
			pieces.add(wallSide);
		}
		else if (endType == WALL_END_TYPE_CORNER_JOIN && (wall == WallFactory.WALL_N || wall == WallFactory.WALL_S)) {
			// fill the area in the corner, no need to worry about mitring it because corner join means both sides 
			// are the same
			double toEdgeLen = this.wallThickness;
			// because it's a join, the other side of wall doesn't need to also do this... so only do for north and south
			double xOffset = (isLeftEnd ? -toEdgeLen : this.lines[0].getLength());
			if (wall == WallFactory.WALL_S) 
				xOffset = (isLeftEnd ? this.lines[0].getLength(): -toEdgeLen);
				
			this.area.add(new Area(new Rectangle2D.Double(this.wallOrigin.getX() + xOffset, this.wallOrigin.getY(), toEdgeLen, toEdgeLen)));

		}
		
	}
	
	protected final boolean drawPiece(Graphics2D g2) {
		if (super.drawPiece(g2))	{	
			
			if (this.area != null) { 
				g2.setColor(this.getStrokeColor());
//				g2.draw(this.area);
				g2.fill(this.area);
			}
			
			return true;
		}
		else
			return false;
		
	}  // end of draw()

//temp
//public void drawAfterShading(Graphics2D g2) {
//	if (this.area != null) {
//		g2.setColor(Color.red);
//		g2.fill(this.area);
//	}
//	if (this.jep != null) {
//		g2.setColor(Color.blue);
//		g2.draw(this.jep);
//	}
//	if (this.ra != null) {
//		g2.setColor(Color.yellow);
//		g2.draw(this.ra);
//	}
//if (this.wallOrigin != null) {
//	g2.setColor(Color.green);
//	g2.fillOval((int)wallOrigin.getX() - 2, (int)wallOrigin.getY() - 2, 4, 4);
//}
//}
	
}  // end of class

