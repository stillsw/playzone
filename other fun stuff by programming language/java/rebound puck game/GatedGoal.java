package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import firstReboundGame.sound.SoundManager;

public class GatedGoal extends Goal
{	
	
	// don't change these values, they are used as angle multipliers to make the goal
	public static int GATED_GOAL_NORTH = 0;
	public static int GATED_CORNER_GOAL_NE = 1;
	public static int GATED_GOAL_EAST = 2;
	public static int GATED_CORNER_GOAL_SE = 3;
	public static int GATED_GOAL_SOUTH = 4;
	public static int GATED_CORNER_GOAL_SW = 5;
	public static int GATED_GOAL_WEST = 6;
	public static int GATED_CORNER_GOAL_NW = 7;

	// image to draw as a goal net - only need one
	private static BufferedImage goalNetImage;
	
	// reaction delays
	private static int REACT_TO_GOAL_TENTHS_OF_SECONDS = 18;
	private static int REACT_TO_PENALTY_TENTHS_OF_SECONDS = 6;
	private static int FLASH_REACTION_TIMES = 2; // flash on/off every this many tenths of second
	
	protected Location2D leftPostEdgePosn;
	protected Location2D rightPostEdgePosn;
	protected LineVector2D goalLine;
//	protected Vector2D goalLineNormal;
	protected LinearObstacle net;
	protected StaticCircle leftPost;
	protected StaticCircle rightPost;
	protected WallPiece leftWall; // walls belong to sub-class, but easier to have them here for clipping
	protected WallPiece rightWall;
	protected double totalWidth;
	protected double postRadius;
	protected double netHeight;
	protected double clipDepth;
//	protected StrikeableGamePiece[] allObstacles;
	// clipping isn't really for this class but for sub-class, easier to handle it here though
	protected boolean isClipping = false;
	protected Area clipArea;
	protected boolean addToPlayClipZone;
//	protected LineVector2D clipLine;
	// for making walls with thickness
	protected double wallThickness = ReboundGamePanel.WALL_CLIP_DEPTH * 2;
	// for making gap at front of goal
	protected double frontOfNetThickness = ReboundGamePanel.WALL_GOAL_FRONT_AREA_DEPTH;
	protected Area frontOfNetArea;
	protected Area backOfNetArea;
	
	/*
	 * Note: placement, centre pos for these is exactly in the middle at the front side of the goal
	 */
	public GatedGoal(Location2D placement, double postRadius, int orientation, double width, double height, double clipDepth, boolean addToPlayClipZone, int shape, boolean partOfPerimeter, ColorArrangement colors) {
		super(placement, width, height, shape, partOfPerimeter, colors);
		this.postRadius = postRadius;
		this.transformAngleInRadians = Math.toRadians(orientation * 45.0);
		this.clipDepth = clipDepth;
		this.isClipping = Double.compare(this.clipDepth, this.height) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND;
		this.netHeight = (this.isClipping ? this.clipDepth : this.height);	
		this.addToPlayClipZone = addToPlayClipZone;
		// the positions of the front post edges are used to make the goal line... embedded goals in corners will override them
		double currXpos = getCurrPosn().getX();
		double currYpos = getCurrPosn().getY();
		this.leftPostEdgePosn = new Location2D(currXpos - (width / 2) - postRadius, currYpos);
		this.rightPostEdgePosn = new Location2D(currXpos + (width / 2) + postRadius, currYpos);
	}

	public void makePieces() {
		super.makePieces();
		makeGoalNet();
	}

	protected void makeGoalPost(boolean isLeftPost, Point2D postPsn, ColorArrangement postColors) {
		if (isLeftPost) {
			this.leftPost = new StaticCircle(new Location2D(affineTransform.transform(postPsn, null)), postRadius, this.partOfPerimeter, postColors, this.addToPlayClipZone);
			this.leftPost.setPartOf(this);
		}
		else {
			this.rightPost = new StaticCircle(new Location2D(affineTransform.transform(postPsn, null)), postRadius, this.partOfPerimeter, postColors, this.addToPlayClipZone); 
			this.rightPost.setPartOf(this);
		}
	}
	
	private void applyClipping(Point2D leftClipPoint, Point2D rightClipPoint) {
		// for clipped goals, make a shape for clipping the drawing
		if (this.isClipping) {
			double clipLength = ReboundGamePanel.SCREEN_AREA.getHeight(); // just a really big area... the +3 on the ypos is because of an annoying transform margin error which sometimes leaves a tiny gap
			clipArea = new Area(affineTransform.createTransformedShape(new Rectangle2D.Double(leftClipPoint.getX() - clipLength / 2.0, leftClipPoint.getY() - clipLength, clipLength, clipLength)));
			if (addToPlayClipZone) {
				GameBounds.getInstance().subtractFromClippingVisibleZone(clipArea);
			}
		}
	}
	
	private void makeGoalNet() {

		double sideXOffset = 0.0, sideYOffset = -postRadius;
		double leftPostXOffset = -postRadius, leftPostYOffset = -postRadius, 
			rightPostXOffset = postRadius, rightPostYOffset = -postRadius;

		double currXpos = this.getCurrPosn().getX();
		double currYpos = this.getCurrPosn().getY();
		
		// make a 3 sided net with a linear obstacle
		int i = 0;
		Point2D.Double[] cornerPosns = new Point2D.Double[4]; // 4 points to make 3 sides
		cornerPosns[i++] = new Point2D.Double(currXpos - this.width / 2, currYpos);
		cornerPosns[i] = new Point2D.Double(currXpos - this.width / 2, currYpos - this.netHeight);
		Point2D leftClipPoint = cornerPosns[i++];
		cornerPosns[i] = new Point2D.Double(currXpos + this.width / 2, currYpos - this.netHeight);
		Point2D rightClipPoint = cornerPosns[i++];
		cornerPosns[i] = new Point2D.Double(currXpos + this.width / 2, currYpos);
		
		// apply the clipping - before make goal posts
		this.applyClipping(leftClipPoint, rightClipPoint);		
		
		// easy to make the goal posts now (left post is as we face the goal)
		ColorArrangement postColors = ColorArrangement.NULL_COLOURS;
		double leftPostX = cornerPosns[0].getX() + leftPostXOffset;
		double leftPostY = cornerPosns[0].getY() + leftPostYOffset;
		makeGoalPost(true, new Point2D.Double(leftPostX, leftPostY), postColors);
//		this.appendArea(this.leftPost);
		double rightPostX = cornerPosns[cornerPosns.length - 1].getX() + rightPostXOffset;
		double rightPostY = cornerPosns[cornerPosns.length - 1].getY() + rightPostYOffset;
		makeGoalPost(false, new Point2D.Double(rightPostX, rightPostY), postColors);
//		this.appendArea(this.rightPost);
		// adjust the first and last positions by x/y offsets for the lines of the net to sit back by the radius of the posts
		VolatileVector2D adjustV = new VolatileVector2D(sideXOffset, sideYOffset);
		Location2D corner0 = new Location2D(cornerPosns[0]);
		corner0.addVector(adjustV);
		cornerPosns[0] = new Point2D.Double(corner0.getX(), corner0.getY());
		Location2D corner3 = new Location2D(cornerPosns[cornerPosns.length - 1]);
		corner3.addVector(adjustV);
		cornerPosns[cornerPosns.length - 1] = new Point2D.Double(corner3.getX(), corner3.getY());

		// now transform all the points
		Point2D[] transformedPosns = new Point2D.Double[cornerPosns.length];
		affineTransform.transform(cornerPosns, 0, transformedPosns, 0, cornerPosns.length);
		LineVector2D[] lines = LinearObstacle.makeObstacleShape(transformedPosns, !this.isClipping, false); // get a 3 sided linear shape so don't join the ends
		this.net = new LinearObstacle(lines, this.partOfPerimeter, colors, true); // static
		this.net.setPartOf(this);
		
		// set total width depending on how much of the posts are in use
		this.totalWidth = this.width + postRadius * 4; // two posts, two diameters
		
		// with the corner points can make an area for the thickness of the goal
		Point2D areaCorner0 = new Point2D.Double(cornerPosns[0].getX(), currYpos);
		Point2D areaCorner3 = new Point2D.Double(cornerPosns[3].getX(), currYpos);
		this.addNetToArea(areaCorner0, cornerPosns[1], cornerPosns[2], areaCorner3);
		
		this.goalLine = new LineVector2D(new Location2D(affineTransform.transform(this.leftPostEdgePosn.getPoint2D(), null))
				, new Location2D(affineTransform.transform(this.rightPostEdgePosn.getPoint2D(), null)));
//		this.goalLineNormal = this.goalLine.getLeftNormal();

		// reducing the playing clip zone also means want the playing boundary that is within that to be clipped
		// eg. the goal line across the front of an embedded goal is part of the playing boundary and it's
		// note. this is not the zone where things are visible any more, the goal under the net is also visible now
		// (it is shaded)
		if (addToPlayClipZone) {
			this.subtractGoalLineFromPlayBoundary();
		}
		// make a goal net image to draw over the top of everything (by product also create the underneath net area and clip exclusion zone)
		this.makeGoalNetImage((int)(this.width + postRadius * 3));
	}

	private void makeGoalNetImage(int netWidth) {
		double barThickness = this.postRadius / 2.0;
		double backOfNetThickness = this.netHeight - this.frontOfNetThickness - barThickness;
        double nettingEdge = barThickness / 2.0;
        double arc = this.postRadius * 1.5;
		// set the area of the back of the net (which is added to the clipping visible area of the game)
        double backOfNetXpos = this.getCurrPosn().getX() - netWidth / 2 + this.postRadius + nettingEdge;
        double backOfNetYpos = this.getCurrPosn().getY() - this.netHeight + nettingEdge;
        double backOfNetWidth = netWidth - this.postRadius * 2 - nettingEdge * 2;
		this.backOfNetArea = new Area(this.affineTransform.createTransformedShape(new RoundRectangle2D.Double(backOfNetXpos, backOfNetYpos, backOfNetWidth, backOfNetThickness + barThickness * 2, arc, arc)));
		
		// make an exclusion zone for faster drawing (if nothing moves into the zone, no need to redraw)
		// find centre point by bisecting diagonal line across the middle using transformed points
//        double excludeClipXpos = this.getCurrPosn().getX() - netWidth / 2;
//        double excludeClipYpos = this.getCurrPosn().getY() - this.netHeight;
//		this.clipExclusionZone = new ClipExclusionZone(new Rectangle2D.Double(excludeClipXpos, excludeClipYpos, netWidth, netWidth));
//		this.clipExclusionZone = new ClipExclusionZone(this.backOfNetArea.getBounds2D());
		
		if (this.frontOfNetArea != null)
			this.backOfNetArea.subtract(this.frontOfNetArea); // back of net area is a little larger than needed because of 
															//  the rounded corner, which isn't actually wanted at the lower end (by crossbar)

		// make a clip that defines the shape (this is needed to create exclusion clip zone even if we don't need the image)
        // start with the top part of the goal, with bigger rounded corners
        double xpos = this.postRadius;
        double ypos = 0;
        double width = netWidth - xpos * 2.0;
        double height = this.netHeight - this.frontOfNetThickness / 2.0; // big enough to not see the lower rounded corner after clipping
        Area clip = new Area(new RoundRectangle2D.Double(xpos, ypos, width, height, arc, arc));
        // slightly bigger area for exclusion zone
        Area exclusionClip = new Area(new Rectangle2D.Double(xpos, ypos - 5, width, height));
        Area nettingArea = new Area(new RoundRectangle2D.Double(xpos + nettingEdge, ypos + nettingEdge, backOfNetWidth, backOfNetThickness + barThickness, arc, arc));
		// making a negative image of the net to subtract from the back piece
		double nettingThickness = (Double.compare(barThickness, 4.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? 1.0 : barThickness / 4.0);
		// loop a few times drawing circles 
		for (int i = 0; i < 8; i++) {
			double circleXpos = i * netWidth / 8 - backOfNetThickness / 2;
			double circleDiameter = backOfNetThickness * 2;
			double circleYpos = nettingEdge;
			for (int j = 0; j < 2; j++) {
				double yOffset = j * nettingEdge * 2.5;
				double xOffset = 0;//circleDiameter / 2;
				Area circle = new Area(new Ellipse2D.Double(circleXpos + xOffset, circleYpos + yOffset, circleDiameter, circleDiameter));
				circle.subtract(new Area(new Ellipse2D.Double(circleXpos + xOffset + nettingThickness, circleYpos + yOffset + nettingThickness, circleDiameter - nettingThickness * 2, circleDiameter - nettingThickness * 2)));
				nettingArea.subtract(circle);
//					Area debugArea = nettingArea;
			}
		}
        // add the left post
        ypos = netHeight - this.frontOfNetThickness;
        width = barThickness;
        height = this.frontOfNetThickness - width;
        arc = width;
        clip.add(new Area(new RoundRectangle2D.Double(xpos, ypos, barThickness, height, arc, arc)));
        // right post same apart from xpos
        clip.add(new Area(new RoundRectangle2D.Double(netWidth - xpos - barThickness, ypos, barThickness, height, arc, arc)));
        // subtract out the front of the goal
        clip.subtract(new Area(new RoundRectangle2D.Double(this.postRadius * 1.5, ypos, netWidth - this.postRadius * 3, netHeight, this.postRadius, this.postRadius)));
        // make the area that will fill for the posts & crossbar
		ypos -= width;
		arc = this.postRadius * 1.75;
		Area postsArea = new Area(new RoundRectangle2D.Double(xpos, ypos, netWidth - xpos * 2.0, netHeight, arc, arc));
		// subtract it from the netting area first
		nettingArea.subtract(postsArea);
		// now take the result away from the whole clip area (leaving holes for the netting)
		clip.subtract(nettingArea);

		// use the exclusion clip, which is just bigger than the bounds of the net image, to make the exclusion zone, have to translate it 
		exclusionClip.add(clip);
		AffineTransform tranx = AffineTransform.getTranslateInstance(this.getCurrPosn().getX() - netWidth / 2, this.getCurrPosn().getY() - this.netHeight);
		Area translatedClipArea = new Area(tranx.createTransformedShape(exclusionClip));
		// and transform it
		Area transformedClipArea = new Area(this.affineTransform.createTransformedShape(translatedClipArea));
			
		// no need to go on if already did this for another goal... assuming they are all the same size etc
		if (GatedGoal.goalNetImage == null) {
			int shadingHeight = (int)(this.netHeight - this.frontOfNetThickness);
	    	BufferedImage shadingImage = new BufferedImage(2, shadingHeight, BufferedImage.TYPE_INT_ARGB);
	        Graphics2D g2d = shadingImage.createGraphics();        
	        // fill a gradient into the image just 2 pixels wide
	        GradientPaint paint = new GradientPaint(0, 0, Color.orange, 0, shadingHeight, Color.white);
	        g2d.setPaint(paint);
	        g2d.fillRect(0, 0, 2, shadingHeight);
	        // make an image the full width and fill it with the shading
	        GatedGoal.goalNetImage = new BufferedImage(netWidth, (int)this.netHeight, BufferedImage.TYPE_INT_ARGB);
	        Graphics2D g2 = GatedGoal.goalNetImage.createGraphics();
			// and set this to the clip
	        g2.setClip(clip);
	        // draw the gradient for the back part of the net
			g2.drawImage(shadingImage, 0, 0, netWidth, shadingImage.getHeight(), null);
			// a rounded rectangle for the posts and crossbar to stand out
			g2.setColor(Color.white);
			g2.fill(postsArea);
//if (debugArea != null) {
//	g2.setColor(Color.red);
//	g2.fill(debugArea);
//}
	        g2d.dispose();
	        g2.dispose();
		}
	}

	public Area getFrontOfNetArea() { return this.frontOfNetArea; }
	public Area getBackOfNetArea() { return this.backOfNetArea; }
	public double getNetHeight() { return this.netHeight; }
	
	private void subtractGoalLineFromPlayBoundary() {
		// using height of rectangle inaccurately, but for this it isn't important - this way it's safer always to have it
		this.frontOfNetArea = new Area(affineTransform.createTransformedShape(
						new Rectangle2D.Double(this.leftPostEdgePosn.getX(), this.leftPostEdgePosn.getY() - this.frontOfNetThickness, this.leftPostEdgePosn.getDistance(this.rightPostEdgePosn), this.frontOfNetThickness)));
		gameBounds.subtractFromPlayArea(this.frontOfNetArea);
		// for complete accuracy when come to filling the colour of the outer boundary, make the front net area more exact
		this.frontOfNetArea.subtract(this.area);
	}

	private void addNetToArea(Point2D areaCorner0, Point2D areaCorner1, Point2D areaCorner2, Point2D areaCorner3) {
		// use the corners now to create the area that the goal takes up (post radius)
		// to form the net shape
		double postDiameter = this.postRadius * 2;
 		// easy way is to create 3 rectangles from the points we have
		Point2D topLeftPoint = new Point2D.Double(areaCorner1.getX() - postDiameter, areaCorner1.getY() - postDiameter);
		Point2D topNearRightPoint = new Point2D.Double(areaCorner2.getX(), areaCorner2.getY() - postDiameter);
		double height = topNearRightPoint.distance(areaCorner3); // from the very back of the wall to the last of the net points
		double arc = postDiameter;
		// first rectangle is the wall on the left side
		RoundRectangle2D rect1 = new RoundRectangle2D.Double(topLeftPoint.getX(), topLeftPoint.getY(), postDiameter, height, arc, arc);
		// 2nd rectangle is the wall across the back
		RoundRectangle2D rect2 = new RoundRectangle2D.Double(topLeftPoint.getX(), topLeftPoint.getY(), topLeftPoint.distance(topNearRightPoint) + postDiameter, postDiameter, arc, arc);
		// third rectangle is the wall on the right side
		RoundRectangle2D rect3 = new RoundRectangle2D.Double(topNearRightPoint.getX(), topNearRightPoint.getY(), postDiameter, height, arc, arc);
		this.appendArea(new Area(affineTransform.createTransformedShape(rect1)));
		this.appendArea(new Area(affineTransform.createTransformedShape(rect2)));
		this.appendArea(new Area(affineTransform.createTransformedShape(rect3)));
	}
	
	/*
	 * marker method for getObstacles to be able to return everything, including from sub-classes
	 * eg. walls around a gated goal
	 */
	protected StrikeableGamePiece[] getAdditionalObstacles() {
		return null;
	}
	/*
	 * after the gatedGoal is fully created, don't call this method again
	 * instead just use the array.. this method basically assembles the goal so also
	 * creates the area shape from the pieces
	 */
	public StrikeableGamePiece[] getObstacles() {
		StrikeableGamePiece[] addedObs = getAdditionalObstacles();
		int size = (addedObs == null ? 0 : addedObs.length);
		StrikeableGamePiece[] allObstacles = new StrikeableGamePiece[4 + size];
		int i = 0;
		if (addedObs != null) {
			for( ; i < addedObs.length; i++) {
				allObstacles[i] = addedObs[i];
				// don't make wall pieces part of the goal, nor any pieces that have already been made a part of another piece
				if (!(allObstacles[i] instanceof WallPiece) && !allObstacles[i].deferDrawing()) {
					allObstacles[i].setPartOf(this);
					this.appendArea(allObstacles[i]);
				}
			}
		}
		allObstacles[i++] = this;
		allObstacles[i++] = net;
		allObstacles[i++] = leftPost;
		allObstacles[i] = rightPost;

		return allObstacles;
			
	}
	
	public LineVector2D detachWall(boolean isLeft) {
		LineVector2D wall = null;
		if (isLeft && this.leftWall != null) {
			wall = this.leftWall.getLines()[0];
			this.leftWall = null;
		}
		if (!isLeft && this.rightWall != null) {
			wall = this.rightWall.getLines()[0];
			this.rightWall = null;
		}
		return wall;
	}
	
	public double getTotalWidth() { return totalWidth; }

	
	/*
	 * This method works on radius of the moving piece... assumption that goal scoring pieces are round!
	 * If that changes, this will need reworking.
	 */
	public boolean detectScoredGoal(MoveableGamePiece movingPiece) {
		boolean returnVal = false;
		Location2D currPosn = movingPiece.getCurrPosn();
		if (this.goalArea.contains(currPosn.getX(), currPosn.getY())) {
			LineVector2D lineFromGoalLine = this.goalLine.getShortestLineToPoint(currPosn);
			returnVal = (Double.compare(lineFromGoalLine.getLength(), movingPiece.getRadius()) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND);
			movingPiece.setInGoal(returnVal);				
		}
		return returnVal;
	}
	
	/*
	 * How the goal behaves to register a goal... either positively if scored, or punitively if not
	 */
	public void reactToGoal(MoveableGamePiece movingPiece, boolean scored, int gameTick) {
		this.reactUntilGameTick = gameTick + 
			FirstReboundGame.DEFAULT_FPS / 10 * (scored ? REACT_TO_GOAL_TENTHS_OF_SECONDS : REACT_TO_PENALTY_TENTHS_OF_SECONDS);
		this.redrawTillTick = this.reactUntilGameTick + 1;
		this.redraw = true;
		this.isReactingToGoal = scored;
		this.isReactingToPenalty = !scored;
		if (this.isReactingToGoal)
			this.playSound(SoundManager.SCORE_GOAL);
		if (this.isReactingToPenalty)
			this.playSound(SoundManager.PENALTY_GOAL);
	}
	
	public final void update(int gameTick) {
		super.update(gameTick);
		if (this.isReactingToGoal || this.isReactingToPenalty) {
			int tenthsOfSecondsToGo = (int)((this.reactUntilGameTick - gameTick) / (double)FirstReboundGame.DEFAULT_FPS * 10.0);
			if (gameTick > this.reactUntilGameTick) 
				this.isReactingToGoal = this.isReactingToPenalty = false;
			this.flashOn = (tenthsOfSecondsToGo % GatedGoal.FLASH_REACTION_TIMES == 0);
		}
		else
			this.flashOn = false;
	}

	/*
	 * subclasses (notable gated goal) should override to see if the radius is past their goal line
	 */
	@Override
	protected boolean radiusOverlapsOutOfPlay(Location2D posn, double radius) {
		Area circle = new Area(new Ellipse2D.Double(posn.getX() - radius, posn.getY() - radius, radius * 2, radius * 2));
		circle.intersect(this.frontOfNetArea);
		return !circle.isEmpty();
	}
	
	public Color getFillColor() { 
		if (!(this.isReactingToGoal || this.isReactingToPenalty))
			return super.getFillColor(); 
		else {
			if (this.flashOn)
				return this.colors.getStruckFillColor();
			else
				return this.colors.getFillColor(); 
		}
	}
	public Color getStrokeColor() { 
		if (!(this.isReactingToGoal || this.isReactingToPenalty))
			return super.getStrokeColor();
		else
			if (this.flashOn)
				return this.colors.getStruckStrokeColor();
			else
				return this.colors.getStrokeColor(); 
	}
	
	
	protected boolean drawPiece(Graphics2D g2) {
		
		if (super.drawPiece(g2)) {

			if (this.area != null) {
				g2.setColor(this.getStrokeColor()); // the area is the walls and posts etc... use stroke colour for this
				g2.draw(this.area);
				g2.fill(this.area);
			}

//			if (this.goalLine != null) {
//				g2.setColor(Color.orange);
//				g2.drawLine((int)this.goalLine.getLocation().getX(), (int)this.goalLine.getLocation().getY(), (int)this.goalLine.getEndLocation().getX(), (int)this.goalLine.getEndLocation().getY());
//			}

			return true;
		}
		else
			return false;

	}  // end of draw()
	
	protected void drawPieceAfterShading(Graphics2D g2) {
		boolean drawNetImage = GatedGoal.goalNetImage != null;
		if (drawNetImage) {
			AffineTransform origTransform = g2.getTransform();
			g2.setTransform(this.affineTransform);

			GamePiece.panel.applyRenderingHints(g2, true);
			g2.drawImage(GatedGoal.goalNetImage, (int)(this.getCurrPosn().getX() - GatedGoal.goalNetImage.getWidth() / 2.0), (int)this.getCurrPosn().getY() - GatedGoal.goalNetImage.getHeight() - 2, null);			
			GamePiece.panel.applyRenderingHints(g2, false);

			g2.setTransform(origTransform);
		}
		
		if (ReboundGamePanel.showDebugging) { 
//			if (this.area != null) {
//				g2.setColor(Color.pink);
//				g2.fill(this.area);
//			}
			super.drawPieceAfterShading(g2);
		}
	}
	
}  // end of class

