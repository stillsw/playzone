package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

public class EmbeddedGoal extends GatedGoal
{	
	public static final int WALL_TYPE_STRAIGHT = 0;
	public static final int WALL_TYPE_CORNER = 1;
	
//	private double postRadius;
	
	private double postClip;
	private int wallType;
	private double leftWallLength;
	private double rightWallLength;
	private  int leftEndType;
	private int rightEndType;
	private double wallEndRadius;
	private int rightWallPlace;
	private Vector2D rightToEdgeV;
	private int leftWallPlace;
	private Vector2D leftToEdgeV;
	private LineVector2D leftPostFromCentreV;
	private LineVector2D rightPostFromCentreV;
	private boolean makeLeftWallPartOfGoal = false;
	private boolean makeRightWallPartOfGoal = false;
	
	// for corners only
	private Location2D newCentrePosn;
	
	public EmbeddedGoal(WallFactory.GoalSpec goalSpec) {
		this(goalSpec.getPlacement(), goalSpec.getLeftLength(), goalSpec.getRightLength(), goalSpec.getPostRadius(), goalSpec.getLeftWallEndType()
				, goalSpec.getRightWallEndType(), goalSpec.getRightWall(), goalSpec.getRightToEdgeVector(), goalSpec.getLeftWall(), goalSpec.getLeftToEdgeVector(), goalSpec.getWallEndRadius()
				, goalSpec.getOrientation(), goalSpec.getWidth(), goalSpec.getWidth()
				, Goal.GOAL_SHAPE_RECTANGULAR, goalSpec.getClipDepth(), goalSpec.getAddToPlayClipZone(), true, goalSpec.getColorArrangement());
	}
	public EmbeddedGoal(Location2D placement, double leftWallLength, double rightWallLength, double postRadius, int leftEndType, int rightEndType
			, int rightWallPlace, Vector2D rightToEdgeV, int leftWallPlace, Vector2D leftToEdgeV, double wallEndRadius
			, int orientation, double width, double height
			, int shape, double clipDepth, boolean addToPlayClipZone, boolean partOfPerimeter, ColorArrangement colors) {
		super(placement, postRadius, orientation, width, height, clipDepth, addToPlayClipZone, shape, partOfPerimeter, colors);
		
		// for now, it's just a matter of shaving off the 2 sides of the goal radii
		// when do the corner pieces though, the amount to shave off will be a bit more, so the total width
		// will also be more
		this.postClip = postRadius * 2;
		this.totalWidth = this.totalWidth + postClip + leftWallLength + rightWallLength;
		if (orientation == GatedGoal.GATED_GOAL_NORTH || orientation == GatedGoal.GATED_GOAL_EAST 
				|| orientation == GatedGoal.GATED_GOAL_SOUTH || orientation == GatedGoal.GATED_GOAL_WEST)
			this.wallType = EmbeddedGoal.WALL_TYPE_STRAIGHT;
		else
			this.wallType = EmbeddedGoal.WALL_TYPE_CORNER;
		this.leftWallLength = leftWallLength;
		this.rightWallLength = rightWallLength;
		this.leftEndType = leftEndType;
		this.rightEndType = rightEndType;
		this.wallEndRadius = wallEndRadius;
		this.rightWallPlace = rightWallPlace;
		this.rightToEdgeV = rightToEdgeV;
		this.leftWallPlace = leftWallPlace;
		this.leftToEdgeV = leftToEdgeV;		

		// embedded goals should not have goal posts... instead they should have wall end pieces either rounded or abrupt
		// set wall lengths to the minimum to make that work, which is distance from centre posn to post posn + radius
		double minimumWallLength = Constants.VERY_LARGE_NUMBER;
		if (this.wallType == EmbeddedGoal.WALL_TYPE_STRAIGHT) {
			minimumWallLength = this.width / 2.0 + this.postClip;
		}
		else { // type corner
			// compute the new centre posn relative to the placement 
			// need vectors from edges to find where the centre posn should be
			double currXpos = getCurrPosn().getX();
			double currYpos = getCurrPosn().getY();
			Vector2D leftPostV = new Vector2D(1, 1); // ie. 45 deg
			leftPostV.setLength(postRadius);
			this.leftPostEdgePosn = new Location2D(currXpos - (width / 2) - postRadius, currYpos - postRadius).getAddition(leftPostV);
			this.leftPostFromCentreV = new LineVector2D(this.leftPostEdgePosn, leftPostV.getRightNormal());
			// and the right side
			Vector2D rightPostV = new Vector2D(-1, 1); // ie. 45 deg
			rightPostV.setLength(postRadius);
			this.rightPostEdgePosn = new Location2D(currXpos + (width / 2) + postRadius, currYpos - postRadius).getAddition(rightPostV);
			this.rightPostFromCentreV = new LineVector2D(this.rightPostEdgePosn, rightPostV.getLeftNormal());
			// find the intersect
			this.newCentrePosn = this.rightPostFromCentreV.getIntersectionPoint(this.leftPostFromCentreV);

			minimumWallLength = this.newCentrePosn.getDistance(this.leftPostEdgePosn) + this.postRadius;
		}

		this.makeLeftWallPartOfGoal = (this.leftEndType != WallPiece.WALL_END_TYPE_CORNER_JOIN && Double.compare(this.leftWallLength, minimumWallLength) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND);
		if (this.makeLeftWallPartOfGoal)
			this.leftWallLength = minimumWallLength;
		this.makeRightWallPartOfGoal = (this.rightEndType != WallPiece.WALL_END_TYPE_CORNER_JOIN && Double.compare(this.rightWallLength, minimumWallLength) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND);
		if (this.makeRightWallPartOfGoal)
			this.rightWallLength = minimumWallLength;
		
	}

	public int getWallType() { return this.wallType; }
	
	/*
	 * This method is used specifically from WallFactory to adjust the length of a right side wall
	 * it expects there to be no end wall artifacts and no other complications... 
	 */
	public void appendWallLength(boolean isLeft, double appendLength) {
		if (isLeft && this.leftWall != null)
			this.leftWall.getLines()[0].setLength(this.leftWall.getLines()[0].getLength() + appendLength);
		if (!isLeft && this.rightWall != null)
			this.rightWall.getLines()[0].setLength(this.rightWall.getLines()[0].getLength() + appendLength);
		
	}
		
	public double getLengthPastEdgePoint(boolean leftSide) {

		double minimumWallLength = this.width / 2.0 + this.postRadius;
		if (this.wallType == EmbeddedGoal.WALL_TYPE_CORNER)
			minimumWallLength = this.newCentrePosn.getDistance(this.leftPostEdgePosn);

		if (leftSide)
			return this.leftWallLength - minimumWallLength;
		else
			return this.rightWallLength - minimumWallLength;
	}

	protected void makeGoalPost(boolean isLeftPost, Point2D postPsn, ColorArrangement postColors) {
		// handled here for embedded sides
		double drawDeg = (this.wallType == EmbeddedGoal.WALL_TYPE_CORNER ? 45.0 : 90); // corner arcs are 45 deg
		if (isLeftPost) {// && Double.compare(leftWallLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
			// corners need to start 45 deg further round
			double startDeg = (this.wallType == EmbeddedGoal.WALL_TYPE_CORNER ? 315.0 : 270.0);
			this.leftPost = new GoalPost(postPsn, postRadius, startDeg, drawDeg, this.partOfPerimeter, postColors, this.affineTransform);
			this.leftPost.setPartOf(this);
		}
		else if (!isLeftPost) {// && Double.compare(rightWallLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
			double startDeg = 180.0;
			this.rightPost = new GoalPost(postPsn, postRadius, startDeg, drawDeg, this.partOfPerimeter, postColors, this.affineTransform); // static
			this.rightPost.setPartOf(this);
		}
//		else {
//			super.makeGoalPost(isLeftPost, postPsn, postColors);
//		}
		


	}
	
	protected void assignTransformation() {
		// subclasses can set the angle to their own version
		super.assignTransformation();
		if (this.wallType == EmbeddedGoal.WALL_TYPE_CORNER) {
			Vector2D transCentrePosnV = new Vector2D(this.newCentrePosn, this.getCurrPosn());
			affineTransform.translate(transCentrePosnV.getVx(), transCentrePosnV.getVy());
		}
	}
		
	public void makePieces() {
		super.makePieces();
		this.constructWalls();
		if (addToPlayClipZone) {
//			if (leftWall != null)
//				this.appendArea(leftWall);
//			if (rightWall != null)
//				this.appendArea(rightWall);
		}
	}
	
	protected StrikeableGamePiece[] getAdditionalObstacles() {
		
		ArrayList<StrikeableGamePiece> pieces = new ArrayList<StrikeableGamePiece>();
		if (leftWall != null)
			leftWall.makePieces(pieces);
		if (rightWall != null)
			rightWall.makePieces(pieces);
		if (pieces.size() == 0)
			return null;
		else {
			StrikeableGamePiece[] piecesArray = new StrikeableGamePiece[pieces.size()];
			return pieces.toArray(piecesArray);
		}
	}

	/*
	 * Make the walls from vectors on the x-axis as though we're in south facing position
	 * ie. left means the view as we face the goal
	 * This will transform when the whole thing is turned as needed
	 * 
	 */
	
	private void constructWalls() {
		if (wallType == WALL_TYPE_STRAIGHT) {
			double diffLen = (this.width + this.postClip) / 2;
			if (Double.compare(leftWallLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
				Vector2D leftWallV = new Vector2D(-diffLen, 0.0);
				Location2D leftWallPosn = this.getCurrPosn().getAddition(leftWallV);
				leftWallV.setLength(leftWallLength - diffLen);
				Location2D leftWallEndPosn = leftWallV.getEndLocation(leftWallPosn);
				Point2D wallStart = leftWallPosn.getPoint2D();
				Point2D wallEnd = leftWallEndPosn.getPoint2D();
				leftWall = new WallPiece(new LineVector2D(new Location2D(affineTransform.transform(wallStart, null)), new Location2D(affineTransform.transform(wallEnd, null)))
					, WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT, this.leftWallPlace, this.leftToEdgeV, leftEndType, WallPiece.WALL_END_TYPE_GOAL, wallEndRadius, this.wallThickness, this.partOfPerimeter, colors);
			}
			if (Double.compare(rightWallLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
				Vector2D rightWallV = new Vector2D(diffLen, 0.0);
				Location2D rightWallPosn = this.getCurrPosn().getAddition(rightWallV);
				rightWallV.setLength(rightWallLength - diffLen);
				Location2D rightWallEndPosn = rightWallV.getEndLocation(rightWallPosn);
				Point2D wallStart = rightWallPosn.getPoint2D();
				Point2D wallEnd = rightWallEndPosn.getPoint2D();
				rightWall = new WallPiece(new LineVector2D(new Location2D(affineTransform.transform(wallStart, null)), new Location2D(affineTransform.transform(wallEnd, null)))
					, WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT, this.rightWallPlace, this.rightToEdgeV, WallPiece.WALL_END_TYPE_GOAL, rightEndType, wallEndRadius, this.wallThickness, this.partOfPerimeter, colors);
			}
		}
		else { // not straight... corner
			// find the point on left post that is 45 degrees out... easy enough, have hypotenuse
			// which is the radius, and the angle
			if (Double.compare(leftWallLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
				double diffLen = leftWallLength - this.newCentrePosn.getDistance(this.leftPostEdgePosn); // the distance from the new centre pos to the inner edge of the goal post (tangent)
//System.out.println("EmbeddedGoal.constructWalls difflen left side="+diffLen);				
				if (diffLen > 0.0) {
					Location2D leftWallEndPosn = leftPostFromCentreV.getPointOnLine(diffLen);
					Point2D wallStart = leftPostEdgePosn.getPoint2D();
					Point2D wallEnd = leftWallEndPosn.getPoint2D();
					Location2D transWallStart = new Location2D(affineTransform.transform(wallStart, null));
					alignPoints(this.getCurrPosn(), transWallStart);
					Location2D transWallEnd = new Location2D(affineTransform.transform(wallEnd, null));
					alignPoints(transWallStart, transWallEnd);
					leftWall = new WallPiece(new LineVector2D(transWallStart, transWallEnd)
					, WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT, this.leftWallPlace, this.leftToEdgeV, leftEndType, WallPiece.WALL_END_TYPE_GOAL, wallEndRadius, this.wallThickness, this.partOfPerimeter, colors);
				}
			}
			// right side (diffLen is almost certainly always going to be the same for each side, but recalc just in case in future the goal could be assymetrical
			if (Double.compare(rightWallLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
				double diffLen = rightWallLength - this.newCentrePosn.getDistance(this.rightPostEdgePosn); // the distance from the new centre pos to the inner edge of the goal post (tangent)
//System.out.println("EmbeddedGoal.constructWalls difflen right side="+diffLen);				
				if (diffLen > 0.0) {
					Location2D rightWallEndPosn = rightPostFromCentreV.getPointOnLine(diffLen);
					Point2D wallStart = rightPostEdgePosn.getPoint2D();
					Point2D wallEnd = rightWallEndPosn.getPoint2D();
					Location2D transWallStart = new Location2D(affineTransform.transform(wallStart, null));
					alignPoints(this.getCurrPosn(), transWallStart);
					Location2D transWallEnd = new Location2D(affineTransform.transform(wallEnd, null));
					alignPoints(transWallStart, transWallEnd);
					rightWall = new WallPiece(new LineVector2D(transWallStart, transWallEnd)
					, WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT, this.rightWallPlace, this.rightToEdgeV, WallPiece.WALL_END_TYPE_GOAL, rightEndType, wallEndRadius, this.wallThickness, this.partOfPerimeter, colors);
				}
			}
		}

		// default wall size, ie. minimal is made part of the goal
		if (leftWall != null && this.makeLeftWallPartOfGoal) {
			leftWall.setPartOf(this);
			this.appendArea(leftWall);
		}
		if (rightWall != null && this.makeRightWallPartOfGoal) {
			rightWall.setPartOf(this);
			this.appendArea(rightWall);
		}
	}
	/*
	 * It appears that tiny variances in the result of rotations can throw off the look of the thing when displayed
	 * This method taks p1 as the lead and if one of the axes of p2 is extremely close it changes it to be exactly the same
	 */
	private void alignPoints(Location2D p1, Location2D p2) {
		double diffx = p1.getX()-p2.getX();
		double diffy = p1.getY()-p2.getY();
//		System.out.println("trans wall x start="+p1+" end="+p2+" diff wall start/end x="+diffx+" start/end y="+diffy);
		if (diffx > -0.00001 && diffx < 0.00001)
			p2.setLocation(p1.getX(), p2.getY());
		if (diffy > -0.00001 && diffy < 0.00001)
			p2.setLocation(p2.getX(), p1.getY());
//		System.out.println("changed to start="+p1+" end="+p2+" diff wall start/end x="+diffx+" start/end y="+diffy);
	}

	/*
	 * 
	 */
//	public void addClipToZone(Shape clippingZone) {
//	
//		
//		
//	}
	
	protected final boolean drawPiece(Graphics2D g2) {
		return super.drawPiece(g2);		

//		if (this.newCentrePosn != null) {
//			Graphics2D g2 = (Graphics2D) g;
//			g2.setColor(Color.green);
//			g2.drawOval((int)affineTransform.transform(this.newCentrePosn.getPoint2D(), null).getX() - 5, (int)affineTransform.transform(this.newCentrePosn.getPoint2D(), null).getY() - 5, 10, 10);
//		}
	}  // end of draw()
	
}  // end of class

