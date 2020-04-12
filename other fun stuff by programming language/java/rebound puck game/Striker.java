package firstReboundGame;

import firstReboundGame.gameplay.*;
import firstReboundGame.gui.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

import firstReboundGame.sound.SoundManager;

public class Striker implements DrawableGameElement
{
	static final int MIN_STRENGTH = 1;
	static final int MAX_STRENGTH = 100;
	private static final int AIM_INCREMENT = 3;
	private static final double STRIKER_OUTSIDE_RADIUS = Puck.MOUSE_DETECT_DISTANCE;
	private static final double MAX_STRIKER_TRAVEL = ReboundGamePanel.PUCK_DIAMETER / 2.0;
	private static final int FEEDBACK_MARGIN = 10;

	// aiming constants
	static final int AIM_LEFT = 0;
	static final int AIM_RIGHT = 1;
	private static final int STRAIGHT_UP = 0; // 'north' = the top of the circle... 360 degrees
	  
	// which way the piece is going (based on angle in a circle)
	public static final double NORTH = 0.0;
	public static final double EAST = 90.0;
	public static final double SOUTH = 180.0;
	public static final double WEST = 270.0;

	private ReboundGamePanel panel = ReboundGamePanel.getInstance();
	private SoundManager soundFileReader = SoundManager.getInstance();
	
	// strength to apply to striker
	private int strength = MIN_STRENGTH;
	private int lastStrength = strength;
	
	// states
	private boolean isStriking = false;
	private boolean isAiming = false;
	private boolean showTargeting = false;
	private boolean aimingWithMouse = false;
	private boolean settingStrengthWithMouse = false;
	private boolean showAimers = false;
	
	// direction of the striker on a 178 degree from almost left to almost right
	private double aimAngle;
	private double lastAngle;
	private LineVector2D lastMouseDragRelativePuckCentreLV = LineVector2D.ZERO_LINE_VECTOR.clone();
	private int targettingLen = ReboundGamePanel.PANEL_HEIGHT * 2;
	private ArrayList<Collision> collisions = new ArrayList<Collision>();
	
	private Location2D puckStartPosn;

	// drawing objects for aiming - all of these are used either for constructing an image or for just drawing on the fly
	// depending on global setting to make images
	private Arc2D rightSideArc;
	private Arc2D leftSideArc;
	private Arc2D rightArrowArc;
	private Arc2D leftArrowArc;
	private Ellipse2D aimersPerimeter;
	private Shape topRightArrowHead;
	private Shape bottomRightArrowHead;
	private Shape topLeftArrowHead;
	private Shape bottomLeftArrowHead;

	// images
	private BufferedImage aimersImage;
	private BufferedImage boltImage;
	private int boltImageYoffset;
	private int boltImageXoffset;
	
	// ref the puck
	private Puck puck;
	// gui components in the feedback area
	private StrikerStrengthBar strikerStrengthBar;
	private StrikerAngleIndicator strikerAngleIndicator;
	
	// striker state listening
	StrikerValueChangeListener[] strikerValueChangeListeners = new StrikerValueChangeListener[0];

	public Striker(Puck puck) {
		this.puck = puck;
		this.puckStartPosn = puck.getCurrPosn().clone();
		this.resetAim();
		this.init();
	} // end of Striker()

	// initialise drawing objects
	private void init() {

		double puckRadius = puck.getRadius();
		double arcRadius = Puck.MOUSE_DETECT_DISTANCE;
		int arcDiameter = (int)(arcRadius * 2.0);
		double arrowRadius = (puckRadius + arcRadius) / 2.0;
		double arrowDiameter = arrowRadius * 2.0;
		// striker bolt as an pie arc of the aiming circle... distance from centre is relative to strength
		// centre is the radius of the whole arc, this is then translated to puck posn at draw time
		double currXpos = arcRadius, currYpos = arcRadius;
		Location2D currPosn = new Location2D(currXpos, currYpos);
		
		// arcs for the gray aimer shape
		rightSideArc = new Arc2D.Double(currXpos - arcRadius, currYpos - arcRadius, arcDiameter, arcDiameter, 285.0, 150.0, Arc2D.PIE);
		leftSideArc = new Arc2D.Double(currXpos - arcRadius, currYpos - arcRadius, arcDiameter, arcDiameter, 105.0, 150.0, Arc2D.PIE);

		aimersPerimeter = new Ellipse2D.Double(currXpos - arcRadius, currYpos - arcRadius, arcDiameter, arcDiameter);

		rightArrowArc = new Arc2D.Double(currXpos - arrowRadius, currYpos - arrowRadius, arrowDiameter, arrowDiameter, 335.0, 50.0, Arc2D.OPEN);
		leftArrowArc = new Arc2D.Double(currXpos - arrowRadius, currYpos - arrowRadius, arrowDiameter, arrowDiameter, 155.0, 50.0, Arc2D.OPEN);
			
		// arrow heads - top right
		Arc2D.Double arc3BaseOfArrowHeads = new Arc2D.Double(currXpos - arrowRadius, currYpos - arrowRadius, arrowDiameter, arrowDiameter, 345.0, 30.0, Arc2D.OPEN);						
		Point2D topRightArrowEnd = rightArrowArc.getEndPoint();
		Point2D topRightArrowHeadBase = arc3BaseOfArrowHeads.getEndPoint();
		LineVector2D lineBaseTopRightArrowThroughCentreLv = new LineVector2D(new Location2D(topRightArrowHeadBase), currPosn);
		Location2D topRightArrowHeadInner = lineBaseTopRightArrowThroughCentreLv.getPointOnLine(5.0);
		Location2D topRightArrowHeadOuter = lineBaseTopRightArrowThroughCentreLv.getPointOnLine(-5.0);
		int[] xPoints = new int[] { (int)topRightArrowEnd.getX(), (int)topRightArrowHeadInner.getX(), (int)topRightArrowHeadOuter.getX() };
		int[] yPoints = new int[] { (int)topRightArrowEnd.getY(), (int)topRightArrowHeadInner.getY(), (int)topRightArrowHeadOuter.getY() };
		topRightArrowHead = new Polygon(xPoints, yPoints, xPoints.length);

		// arrow heads - bottom right
		Point2D bottomRightArrowEnd = rightArrowArc.getStartPoint();
		Point2D bottomRightArrowHeadBase = arc3BaseOfArrowHeads.getStartPoint();
		LineVector2D lineBaseBottomRightArrowThroughCentreLv = new LineVector2D(new Location2D(bottomRightArrowHeadBase), currPosn);
		Location2D bottomRightArrowHeadInner = lineBaseBottomRightArrowThroughCentreLv.getPointOnLine(5.0);
		Location2D bottomRightArrowHeadOuter = lineBaseBottomRightArrowThroughCentreLv.getPointOnLine(-5.0);
		xPoints = new int[] { (int)bottomRightArrowEnd.getX(), (int)bottomRightArrowHeadInner.getX(), (int)bottomRightArrowHeadOuter.getX() };
		yPoints = new int[] { (int)bottomRightArrowEnd.getY(), (int)bottomRightArrowHeadInner.getY(), (int)bottomRightArrowHeadOuter.getY() };
		bottomRightArrowHead = new Polygon(xPoints, yPoints, xPoints.length);

		// arrow heads - bottom left
		Point2D bottomLeftArrowEnd = leftArrowArc.getEndPoint();
		Location2D bottomLeftArrowHeadInner = lineBaseTopRightArrowThroughCentreLv.getPointOnLine(arrowDiameter - 5.0);
		Location2D bottomLeftArrowHeadOuter = lineBaseTopRightArrowThroughCentreLv.getPointOnLine(arrowDiameter + 5.0);
		xPoints = new int[] { (int)bottomLeftArrowEnd.getX(), (int)bottomLeftArrowHeadInner.getX(), (int)bottomLeftArrowHeadOuter.getX() };
		yPoints = new int[] { (int)bottomLeftArrowEnd.getY(), (int)bottomLeftArrowHeadInner.getY(), (int)bottomLeftArrowHeadOuter.getY() };
		bottomLeftArrowHead = new Polygon(xPoints, yPoints, xPoints.length);

		// arrow heads - top left
		Point2D topLeftArrowEnd = leftArrowArc.getStartPoint();
		Location2D topLeftArrowHeadInner = lineBaseBottomRightArrowThroughCentreLv.getPointOnLine(arrowDiameter - 5.0);
		Location2D topLeftArrowHeadOuter = lineBaseBottomRightArrowThroughCentreLv.getPointOnLine(arrowDiameter + 5.0);
		xPoints = new int[] { (int)topLeftArrowEnd.getX(), (int)topLeftArrowHeadInner.getX(), (int)topLeftArrowHeadOuter.getX() };
		yPoints = new int[] { (int)topLeftArrowEnd.getY(), (int)topLeftArrowHeadInner.getY(), (int)topLeftArrowHeadOuter.getY() };
		topLeftArrowHead = new Polygon(xPoints, yPoints, xPoints.length);
		
		if (ReboundGamePanel.MAKE_IMAGES)
			this.makeImages(arcDiameter);
	}

	private void makeImages(int aimersImageSize) {

		// first the aimers image
		{
	        // make an image the full width and fill it with the shading excluding a clip to define the aimers circle
	        this.aimersImage = new BufferedImage(aimersImageSize, aimersImageSize, BufferedImage.TYPE_INT_ARGB);
	        Graphics2D g2 = this.aimersImage.createGraphics();
			// global decides if hints are to be applied
	        panel.applyRenderingHints(g2, true);
		    // arcs forming the gray aimers and a circle around the whole thing
			Area clip = new Area(this.rightSideArc);
			clip.add(new Area(this.leftSideArc));
			Area perimeter = new Area(this.aimersPerimeter);
			perimeter.subtract(new Area(new Ellipse2D.Double(this.aimersPerimeter.getX() + 4, this.aimersPerimeter.getY() + 4, this.aimersPerimeter.getWidth() - 8, this.aimersPerimeter.getHeight() - 8)));
			clip.add(perimeter);
	        g2.setClip(clip);
	        GradientPaint paint = new GradientPaint(0, 0, ReboundGamePanel.lightShadowColour, 0, aimersImageSize / 2, ReboundGamePanel.midShadowColour, true);
	        g2.setPaint(paint);
			g2.fillRect(0, 0, aimersImageSize, aimersImageSize);
			// arcs for the aiming arrows 
			g2.setColor(Color.white);
			g2.draw(rightArrowArc);
			g2.draw(leftArrowArc);
			// arrow heads
		    g2.fill(topRightArrowHead);
		    g2.fill(bottomRightArrowHead);
		    g2.fill(bottomLeftArrowHead);
		    g2.fill(topLeftArrowHead);
	        g2.dispose();
		}

        // striker bolt image
		{
		double arcRadius = Puck.MOUSE_DETECT_DISTANCE;
		int puckRadius = (int)puck.getRadius();
		int startXpos = 10; // start pos is related to the width of the arrow head and striker bolt
		this.boltImageXoffset = -startXpos;
		double calcStartYpos = arcRadius;
		double arrowRadius = (puck.getRadius() + arcRadius) / 2.0;
		double arrowDiameter = arrowRadius * 2.0;
		// striker bolt as an pie arc of the aiming circle... distance from centre is relative to strength
		double maxStrikerTravel = puck.getRadius();
		// at this point strength is at default, so this gives the offset for the image size... it's a -tve number on y-axis
		double strikerDistanceOfTravel = (maxStrikerTravel * this.strength / 100.0);
		int arrowYpos = 0;
		this.boltImageYoffset = (int) -(calcStartYpos - puckRadius * 3 + strikerDistanceOfTravel);
		// adjust the ypos by the offset
		double startYpos = calcStartYpos + boltImageYoffset;

		// start to assemble the clip 
		Arc2D strikerBolt = new Arc2D.Double(startXpos - arrowRadius, startYpos - arrowRadius + strikerDistanceOfTravel, arrowDiameter, arrowDiameter, 254.0, 32.0, Arc2D.PIE);
        Area clip = new Area(strikerBolt);
        // make a triangle for the striker aim arrow head
		int[] xPoints = new int[] { (int)startXpos, (int)startXpos + 10, (int)startXpos - 10 };
		int[] yPoints = new int[] { arrowYpos, arrowYpos + 10, arrowYpos + 10 };
		Area arrowHead = new Area(new Polygon(xPoints, yPoints, xPoints.length));
		clip.add(arrowHead);
		// make a thin rect for the shaft of the arrow
		clip.add(new Area(new Rectangle(startXpos - 2, arrowYpos + 10, 4, (int) startYpos)));
//		g2.drawLine(startXpos, arrowYpos, startXpos, startYpos);
        
        
        
		int boltImageWidth = startXpos * 2;
		int boltImageHeight = aimersImageSize + (int)boltImageYoffset;
        this.boltImage = new BufferedImage(boltImageWidth, boltImageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = this.boltImage.createGraphics();
        // global decides if hints are to be applied
        panel.applyRenderingHints(g2, true);

        
		g2.setClip(clip);
		// fill the clip with a shaded gradient
        GradientPaint paint = new GradientPaint(0, 0, ReboundGamePanel.midShadowColour, 0, boltImageHeight / 2, ReboundGamePanel.lightShadowColour, true);
        g2.setPaint(paint);
		g2.fillRect(0, 0, boltImageWidth, boltImageHeight);

		
		
		g2.setColor(ReboundGamePanel.darkShadowColour);
		g2.fill(strikerBolt);
		g2.setColor(Color.white);
		// arrow head - on striker
		Location2D strikerArrowEnd = new Location2D(startXpos, startYpos + arrowRadius + strikerDistanceOfTravel);
		Location2D strikerArrowHeadInner = new Location2D(startXpos - 5.0, strikerArrowEnd.getY() - 5.0);
		Location2D strikerArrowHeadOuter = new Location2D(startXpos + 5.0, strikerArrowEnd.getY() - 5.0);
		xPoints = new int[] { (int)strikerArrowEnd.getX(), (int)strikerArrowHeadInner.getX(), (int)strikerArrowHeadOuter.getX() };
		yPoints = new int[] { (int)strikerArrowEnd.getY(), (int)strikerArrowHeadInner.getY(), (int)strikerArrowHeadOuter.getY() };
		Shape strikerArrowHead = new Polygon(xPoints, yPoints, xPoints.length);
	    g2.fill(strikerArrowHead);

        g2.dispose();
		}        
        
	}
	
	
	public void setStrengthBar(StrikerStrengthBar strikerStrengthBar) { this.strikerStrengthBar = strikerStrengthBar; }
	public void setAngleIndicator(StrikerAngleIndicator strikerAngleIndicator) { this.strikerAngleIndicator = strikerAngleIndicator; }
	
	public void addStrikerValueChangeListener(StrikerValueChangeListener listener) {
		StrikerValueChangeListener[] newStrikerValueChangeListeners = new StrikerValueChangeListener[this.strikerValueChangeListeners.length + 1];
		System.arraycopy(this.strikerValueChangeListeners, 0, newStrikerValueChangeListeners, 0, this.strikerValueChangeListeners.length);
		newStrikerValueChangeListeners[newStrikerValueChangeListeners.length - 1] = listener;
		this.strikerValueChangeListeners = newStrikerValueChangeListeners;
	}
	
	public void notifyStrikerValueChangeListeners() {
		for (int i = 0; i < this.strikerValueChangeListeners.length; i++) {
			strikerValueChangeListeners[i].valuesChanged(this.strength, this.aimAngle);
		}
	}
	
	
	private void incrementAim(int direction, int amount) {
		
		// aiming right means rotate clockwise, left means anti-clockwise
		if (direction == Striker.AIM_LEFT)
		{
			aimAngle -= amount;
			if (aimAngle < 0)
				aimAngle += 360;
		}
		else // aim right
		{
			aimAngle += amount;
			if (aimAngle > 359)
				aimAngle %= 360;
		}
		this.lastAngle = aimAngle;
		this.notifyStrikerValueChangeListeners();
//		System.out.println("Striker.incrementAim: "+(direction == Striker.AIM_LEFT ? "left" : "right")+ " amount="+amount+" newangle="+aimAngle);
	}
	
	/*
	 * aim the strike, but also store info of collisions for debugging and targetting
	 */
	public void aim(int aimChangeDirection) {
		
		//aimWithMouse = false;
		if (!isAiming) {
			  isAiming = true;
//			  this.aimAngle = STRAIGHT_UP;
		}
		// aiming right means rotate clockwise, left means anti-clockwise
		incrementAim(aimChangeDirection, Striker.AIM_INCREMENT);
		
//		plotDebuggingCollisions();
// this isn't working... needs to be revisited		
	}

	public void showAimers(boolean value) { this.showAimers = value; }
	
	public void stopMouseDrag() {
		this.lastMouseDragRelativePuckCentreLV.changeLocation(0.0, 0.0);
		this.settingStrengthWithMouse = false;
	}
	
	public void startMouseDrag(int xpos, int ypos) {
		startMouseDrag(new Location2D(xpos, ypos));
	}
	public void startMouseDrag(Location2D testPosn) {
		setAiming(true, true);
		this.lastMouseDragRelativePuckCentreLV.changeLocation(puck.getCurrPosn());
		this.lastMouseDragRelativePuckCentreLV.changeDirectionToLocation(testPosn);
//		System.out.println("Striker.startMouseDrag: set last mouse lineV = "+lastMouseDragLV);
	}
	
	public void aimWithMouse(int xpos, int ypos) {
		Location2D puckPosn = puck.getCurrPosn();
		Location2D testPosn = new Location2D(xpos, ypos);
		// can only do this if know where dragging started, detect if last point is realistic, and use if so
		if (Double.compare(this.lastMouseDragRelativePuckCentreLV.getLocation().getX(), puckPosn.getX()) == Constants.DOUBLE_COMPARE_EQUAL 
			&& Double.compare(this.lastMouseDragRelativePuckCentreLV.getLocation().getY(), puckPosn.getY()) == Constants.DOUBLE_COMPARE_EQUAL) {
			// get angle from difference between vectors
			Vector2D newDragFromCentreV = new Vector2D(puckPosn, testPosn);
			double dp = this.lastMouseDragRelativePuckCentreLV.getUnitsDotProduct(newDragFromCentreV);
			double angleChange = Math.toDegrees(Math.acos(dp));
			// have the angle of change, but which way is it travelling...
			Vector2D dragDirectionV = new Vector2D(this.lastMouseDragRelativePuckCentreLV.getLocation(), new Location2D(xpos, ypos));
			// dp with right normal travelling right (same direction = clockwise) will be +tive 
			double dirDp = dragDirectionV.getDotProduct(this.lastMouseDragRelativePuckCentreLV.getRightNormal());
//			System.out.println("Striker.aimWithMouse: dragging to aim dp ="+dp+" angle change="+angleChange);//" dp with right normal="+dirDp + " double compare 0.0 = "+Double.compare(dirDp, 0.0));
			this.incrementAim((Double.compare(dirDp, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? Striker.AIM_LEFT : Striker.AIM_RIGHT), (int)angleChange);
		}
//		
//		// set for next time
//		startMouseDrag(testPosn);
	}
	
	public void strikeWithMouse(int xpos, int ypos) {
		// once started dragging with the mouse, don't need to have the mouse stay inside the bolt area
		Location2D testPosn = new Location2D(xpos, ypos);
		Location2D puckPosn = puck.getCurrPosn();
		// can only do this if know where dragging started, detect if last point is realistic, and use if so
		if (Double.compare(this.lastMouseDragRelativePuckCentreLV.getLocation().getX(), puckPosn.getX()) == Constants.DOUBLE_COMPARE_EQUAL 
				&& Double.compare(this.lastMouseDragRelativePuckCentreLV.getLocation().getY(), puckPosn.getY()) == Constants.DOUBLE_COMPARE_EQUAL) {
			double currXpos = puckPosn.getX();
			double currYpos = puckPosn.getY();
			// moving towards or away from centre, lastMouseDragRelativePuckCentreLV end location is the posn of the last testPosn
			Vector2D directionOfDragV = new Vector2D(this.lastMouseDragRelativePuckCentreLV.getEndLocation(), testPosn);
			AffineTransform affineTransform = AffineTransform.getRotateInstance(Math.toRadians(getStrikeAngle()), currXpos, currYpos);
			Location2D pointTowardsStriker = new Location2D(affineTransform.transform(new Point2D.Double(currXpos, currYpos + puck.getRadius()), null));
			Vector2D towardsStrikeStartPosnV = new Vector2D(puckPosn, pointTowardsStriker);
			double strikeDirDp = towardsStrikeStartPosnV.getUnitsDotProduct(directionOfDragV); // striker is directly below currPosn
			Vector2D strikerToMouseV = new Vector2D(pointTowardsStriker, testPosn);
			double strikePositionDp = towardsStrikeStartPosnV.getUnitsDotProduct(strikerToMouseV);
			boolean movingInwards = Double.compare(strikeDirDp, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND;
			// the outside limit of the striker is the perimeter of the strikers
			if (Double.compare(strikePositionDp, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND // provided outwards relative to striker start pos
					&& puck.getRadius() + Striker.MAX_STRIKER_TRAVEL < puckPosn.getDistance(testPosn)) {
				// already at limit of movement
				if (this.strength < Striker.MAX_STRENGTH)
					this.strength = Striker.MAX_STRENGTH;
			}
			else if (Double.compare(strikePositionDp, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) {
				// already before the movement
				if (this.strength > Striker.MIN_STRENGTH)
					this.strength = Striker.MIN_STRENGTH;
			}
			else {
				double lengthOfTravel = directionOfDragV.getLength();
				double propertionOfTravel = (lengthOfTravel / Striker.MAX_STRIKER_TRAVEL) * 100.0;
				double strengthIncrement = propertionOfTravel * (movingInwards ? -1.0 : 1.0);
				this.setStrength(strengthIncrement);
			}
			this.settingStrengthWithMouse = true;
//				System.out.println("striker bolt moved "+(Double.compare(strikeDirDp, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND ? "towards middle" : "away from middle")+ " (distance travelled="+lengthOfTravel+" strengthIncrement="+strengthIncrement+" propertionOfTravel="+propertionOfTravel+")");
		}
//		
//		// set for next time
//		startMouseDrag(testPosn);
	}
	
	
	public boolean positionIsInsideStrikerBolt(int xpos, int ypos) {
		double currXpos = puck.getCurrPosn().getX();
		double currYpos = puck.getCurrPosn().getY();
		AffineTransform affineTransform = AffineTransform.getRotateInstance(Math.toRadians(getStrikeAngle()), currXpos, currYpos);
		return positionIsInsideStrikerBolt(xpos, ypos, currXpos, currYpos, affineTransform);
	}
	public boolean positionIsInsideStrikerBolt(int xpos, int ypos, double currXpos, double currYpos, AffineTransform affineTransform) {
		double arcRadius = Striker.STRIKER_OUTSIDE_RADIUS;
		double arcDiameter = arcRadius * 2.0;
		double distanceOfTravel = (Striker.MAX_STRIKER_TRAVEL * this.strength / 100.0);
		Arc2D strikerBoltToArcEdge = new Arc2D.Double(currXpos - arcRadius, currYpos - arcRadius + distanceOfTravel, arcDiameter, arcDiameter, 254.0, 32.0, Arc2D.PIE);
		Shape transShape = affineTransform.createTransformedShape(strikerBoltToArcEdge);
		Rectangle boltRect = transShape.getBounds();
			
		return (boltRect.contains(xpos, ypos));
	}
	
	public boolean isAiming() { return isAiming; }
	public boolean settingStrengthWithMouse() { return this.settingStrengthWithMouse; }
	public void setAiming(boolean aiming, boolean withMouse) { 
		this.isAiming = aiming; 
		this.aimingWithMouse = withMouse;
		this.showAimers = (aiming || withMouse);
	}

	public void plotDebuggingCollisions() {
		
		if (ReboundGamePanel.showDebugging) {
		  	double angleInRadians = Math.toRadians(getStrikeAngle());
			double currXpos = puck.getCurrPosn().getX();
			double currYpos = puck.getCurrPosn().getY();
			// get a trajectory draw a line twice the height of the panel... should be long enough
			double sinVal = Math.sin(angleInRadians);
			double cosVal = Math.cos(angleInRadians);
		  	double xChange = sinVal * this.targettingLen;
		  	double yChange = cosVal * this.targettingLen;
			// y-axis goes downwards, so reverse the sign of y
		  	LineVector2D originalBearing = new LineVector2D(currXpos, currYpos, xChange, -yChange);
			this.collisions.clear();
//			MoveableGamePiece.detectCollisions(puck.getCurrPosn(), originalBearing, puck, true, true, this.collisions);			
		}
		
	}

	public void resetAim() { aimAngle = STRAIGHT_UP; plotDebuggingCollisions(); }
	
	public void startStriking() {
		//isAiming = false;
		isStriking = true;
		strength = MIN_STRENGTH;
		this.collisions.clear();
	}

	public void stopStriking(boolean puckIsHit) { 
		isStriking = false; 
		isAiming = false;
		if (puckIsHit)
			this.lastStrength = this.strength;
	}
	  
	public boolean isStriking() { return isStriking; }
	  
	public void toggleTargetting() { this.showTargeting = !this.showTargeting; }
	  
	public void reset() {	
		this.strength = Striker.MIN_STRENGTH; 
		this.resetAim();
		isStriking = false;
		isAiming = false;
		aimingWithMouse = false;
		settingStrengthWithMouse = false;
		Point mouse = panel.getMousePosition();
		this.showAimers(mouse != null && this.puckStartPosn.getDistance(mouse.getX(), mouse.getY()) <= Puck.MOUSE_DETECT_DISTANCE);
	};
	  
	public void strikePuck() {
		this.playSound(SoundManager.STRIKER_STRIKE);
		stopStriking(true);
		puck.struckByStriker();
	}
	
	protected void playSound(int soundIdx) {
		soundFileReader.playSound(soundIdx);
	}

	public int increaseStrength() {
		if (this.strength < Striker.MAX_STRENGTH) {
			this.strength++;
			this.notifyStrikerValueChangeListeners();
		}
		  
		return strength;
	}

	public int decreaseStrength() {
		if (this.strength > Striker.MIN_STRENGTH) {
			this.strength--;
			this.notifyStrikerValueChangeListeners();
		}
		  
		return strength;
	}

	public int setStrength(double amount) { // called by mouse dragging the striker bolt
		double tryInc = this.strength + amount;
		if (tryInc > Striker.MAX_STRENGTH) 
			this.strength = Striker.MAX_STRENGTH;
		else if (tryInc < Striker.MIN_STRENGTH)
			this.strength = Striker.MIN_STRENGTH;
		else
			this.strength = (int)tryInc;
		  
		this.notifyStrikerValueChangeListeners();
		return this.strength;
	}

	public double getStrikeAngle() 
	{
		double angle = aimAngle;
		if (angle < 0.0) // either straight up or pointing left
			angle %= 360.0;
		return angle;
	}

	public double getStrikeStrength() { return strength; }	
	
	private void drawAimers(Graphics2D g2) {
	    
	    if (this.aimersImage != null && ReboundGamePanel.MAKE_IMAGES) {
			g2.drawImage(this.aimersImage, 0, 0, null);			
	    }
	    else {
		    // arcs forming the gray aimers and a circle around the whole thing
		    g2.setColor(Color.lightGray);
			g2.fill(this.rightSideArc);
			g2.fill(this.leftSideArc);
			g2.draw(this.aimersPerimeter);

		    // arcs for the aiming arrows 
			g2.setColor(Color.white);
			g2.draw(rightArrowArc);
			g2.draw(leftArrowArc);
			
			// arrow heads
		    g2.fill(topRightArrowHead);
		    g2.fill(bottomRightArrowHead);
		    g2.fill(bottomLeftArrowHead);
		    g2.fill(topLeftArrowHead);
	    }
	    
	}
	
	private void drawStrikerBoltArrow(Graphics2D g2, double arcRadius, int puckRadius, int startXpos, int startYpos) {
	    
		// striker bolt as an pie arc of the aiming circle... distance from centre is relative to strength
		double maxStrikerTravel = puckRadius;
		double strikerDistanceOfTravel = (maxStrikerTravel * this.strength / 100.0);
		
	    if (this.boltImage != null && ReboundGamePanel.MAKE_IMAGES) {
			g2.drawImage(this.boltImage, startXpos + this.boltImageXoffset, (int)strikerDistanceOfTravel - this.boltImageYoffset, null);	
	    }
	    else {
			double arrowRadius = (puck.getRadius() + arcRadius) / 2.0;
			double arrowDiameter = arrowRadius * 2.0;
			
			// arrow along aim
			g2.setColor(Color.black);
			int arrowYpos = (int)(startYpos - puckRadius * 3 + strikerDistanceOfTravel);
			g2.drawLine(startXpos, arrowYpos, startXpos, startYpos);
			g2.drawLine(startXpos, arrowYpos, startXpos + 10, arrowYpos + 10);
			g2.drawLine(startXpos, arrowYpos, startXpos - 10, arrowYpos + 10);

			// striker bolt as a pie arc of the aiming circle... distance from centre is relative to strength
			Arc2D strikerBolt = new Arc2D.Double(startXpos - arrowRadius, startYpos - arrowRadius + strikerDistanceOfTravel, arrowDiameter, arrowDiameter, 254.0, 32.0, Arc2D.PIE);
			g2.fill(strikerBolt);
			
			g2.setColor(Color.white);
			// arrow head - on striker
			Location2D strikerArrowEnd = new Location2D(startXpos, startYpos + arrowRadius + strikerDistanceOfTravel);
			Location2D strikerArrowHeadInner = new Location2D(startXpos - 5.0, strikerArrowEnd.getY() - 5.0);
			Location2D strikerArrowHeadOuter = new Location2D(startXpos + 5.0, strikerArrowEnd.getY() - 5.0);
			int[] xPoints = new int[] { (int)strikerArrowEnd.getX(), (int)strikerArrowHeadInner.getX(), (int)strikerArrowHeadOuter.getX() };
			int[] yPoints = new int[] { (int)strikerArrowEnd.getY(), (int)strikerArrowHeadInner.getY(), (int)strikerArrowHeadOuter.getY() };
			Shape strikerArrowHead = new Polygon(xPoints, yPoints, xPoints.length);
		    g2.fill(strikerArrowHead);
	    }
	    
	}
	
	public void draw(Graphics2D g2) {
		// draw a striking object... it may spin around the puck according to the aim angle
		GamePiece.panel.applyRenderingHints(g2, true);
		double angleInRadians = Math.toRadians(getStrikeAngle());
	
		if (!puck.isMoving() && !puck.isWaitingToRestart())
		{
			if (this.isStriking || this.showAimers || this.isAiming || this.showTargeting || ReboundGamePanel.showDebugging)
		    {
				int puckRadius = (int)puck.getRadius();
				int puckDiameter = (int)puck.getDiameter();
				int currXpos = (int)puck.getCurrPosn().getX();
				int currYpos = (int)puck.getCurrPosn().getY();

				if (ReboundGamePanel.showDebugging && (this.isStriking || this.isAiming)) // draw a test trajectory
				{
					for (Iterator<Collision> it = this.collisions.iterator(); it.hasNext(); ) {
						// if there's a collision with the walls, so that will take care of drawing
						// the trajectory too
						Collision collision = it.next();
// can't yet show the wall as a StrikeableGamePiece
						Location2D intersectionPoint = collision.getIntersectionPoint();
						LineVector2D originalBearing = collision.getOriginalBearing();
						g2.setColor(Color.gray);
						int startX = (int)originalBearing.getLocation().getX();
						int startY = (int)originalBearing.getLocation().getY();
						Location2D endPoint = originalBearing.getEndLocation();
						int endX = (int)endPoint.getX();
						int endY = (int)endPoint.getY();
						g2.drawLine(startX, startY, endX, endY);
						g2.setColor(Color.red);
						g2.drawOval((int)(intersectionPoint.getX() - puckRadius), (int)(intersectionPoint.getY() - puckRadius), puckDiameter, puckDiameter);
						// draw the normal in red too
						LineVector2D lineNormal = collision.getNormal();
						startX = (int)lineNormal.getLocation().getX();
						startY = (int)lineNormal.getLocation().getY();
						endPoint = lineNormal.getEndLocation();
						endX = (int)endPoint.getX();
						endY = (int)endPoint.getY();
						g2.drawLine(startX, startY, endX, endY);
						// draw the bounce in blue
						LineVector2D newBearing = collision.getNewBearing();
						startX = (int)newBearing.getLocation().getX();
						startY = (int)newBearing.getLocation().getY();
						endPoint = newBearing.getEndLocation();
						endX = (int)endPoint.getX();
						endY = (int)endPoint.getY();
						g2.setColor(Color.blue);
						g2.drawLine(startX, startY, endX, endY);
						g2.setColor(Color.gray);
					}
				}
				if (this.showTargeting) {
					if (collisions.size() == 0) { // nothing got drawn so
						// draw a line twice the height of the panel... should be long enough
						double sinVal = Math.sin(angleInRadians);
						double cosVal = Math.cos(angleInRadians);
					  	double xChange = sinVal * this.targettingLen;
					  	double yChange = cosVal * this.targettingLen;
					  	int yPos = (int)(currYpos - yChange);
					  	int xPos = (int)(currXpos + xChange);
				
						g2.setColor(Color.gray);
						g2.drawLine(currXpos, currYpos, xPos, yPos);
					}

				}

				// all these pieces are rotated at the origin and the translated to the current position
				// because the arcs and arrows are pre-made based on the start position
				if (this.isStriking || this.isAiming || this.showAimers) {
					double arcRadius = Puck.MOUSE_DETECT_DISTANCE;
					int startXpos = (int)arcRadius;// (int)this.puckStartPosn.getX();
					int startYpos = (int)arcRadius;//this.puckStartPosn.getY();
			    	
			    	// rotate the striker based on the aim
					AffineTransform oriAt = g2.getTransform();
					AffineTransform rotateTranslate = AffineTransform.getTranslateInstance(currXpos - startXpos, currYpos - startYpos);
					// translate to current position
					rotateTranslate.rotate(angleInRadians, startXpos, startYpos);
					g2.setTransform(rotateTranslate);

					// make the aiming radius shape transparent
				    Composite originalComposite = g2.getComposite();
				    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8F));
					
					if (aimingWithMouse || this.showAimers)
						this.drawAimers(g2);
					
					this.drawStrikerBoltArrow(g2, arcRadius, puckRadius, startXpos, startYpos);
					// reset to non transparent
					g2.setComposite(originalComposite);

					// restore the original transform to g2
					g2.setTransform(oriAt);

// show where the mouse drag is being made from
//					if(this.lastMouseDragLV != null){
//						g2.drawLine((int)this.lastMouseDragLV.getLocation().getX(),
//								(int)this.lastMouseDragLV.getLocation().getY(), 
//								(int)this.lastMouseDragLV.getEndLocation().getX(), 
//								(int)this.lastMouseDragLV.getEndLocation().getY());
//					}
				}
						        
		    }
		}

		// strength indicator in feedback area
		double useStrength = (this.isStriking() || this.settingStrengthWithMouse() ? this.strength : this.lastStrength);
		strikerStrengthBar.setStrengthPerc(useStrength);
		double useAngle = (this.isAiming() || this.aimingWithMouse ? this.aimAngle : this.lastAngle);
		this.strikerAngleIndicator.setStrikeAngle(useAngle);
		
		GamePiece.panel.applyRenderingHints(g2, false);
	}  // end of draw()

	public void drawAfterShading(Graphics2D g2) {}

	@Override
	public Color getFillColor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Color getStrokeColor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isStatic() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Location2D getCurrPosn() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getRadius() {
		// TODO Auto-generated method stub
		return 0;
	}
}  // end of Striker class

