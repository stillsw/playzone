package firstReboundGame;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

import firstReboundGame.gameplay.ScoringManager;
import firstReboundGame.gui.GuiComponent;
import firstReboundGame.gui.GuiComponentMouseManager;
import firstReboundGame.gui.TemporalPopupBox;

public class Puck extends TargetDisk implements GuiComponent
{
	public static final double MOUSE_DETECT_DISTANCE = ReboundGamePanel.PUCK_DIAMETER;
	public static final double MOUSE_DETECT_DISTANCE_SQUARED = MOUSE_DETECT_DISTANCE * MOUSE_DETECT_DISTANCE;
	private static final boolean STRIKE_AIMS_TOO = true;	
	private static final int CHANGE_POS_INCR = 10;
	private boolean struckByStriker = false;
	private boolean draggingStriker = false;
	private boolean draggingAiming = false;
	private boolean draggingPuck = false;
	private GameEventsManager gameEventsManager = GameEventsManager.getInstance();
	private double radiusSquared;
	
	private Striker striker;
  
	public Puck(ScoringManager scoringManager, PlayManager playManager, Location2D placement, double radius, ColorArrangement colors) {
		super(scoringManager, playManager, placement, radius, colors);
		this.radiusSquared = radius * radius;
		// set bitwise flags to receive all mouse events except moved
		// when over the puck ask the puck which cursor to use (if in the middle want the move cursor not the hand cursor)
		// require that the mouse manager asks the puck to determine if it should receive the mouse event ('cos it doesn't do anything when moving)
		GuiComponentMouseManager.getInstance().addGuiComponent(this, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ALL_EXCEPT_MOVE 
																		| GuiComponentMouseManager.MOUSE_EVENT_MOUSE_OVER_ASK_FOR_CURSOR
																		| GuiComponentMouseManager.MOUSE_EVENT_ASK_FOR_STATE);
	} // end constructor

	public void setStriker(Striker striker) { this.striker = striker; }
	
	public void struckByStriker() { 
		this.struckByStriker = true; 
		this.setMoving(true);
		// register with scoring manager
		this.scoringManager.registerStrike(true);
	}

	public void setMoving(boolean isMoving)  {  
		super.setMoving(isMoving);
	
		if (this.struckByStriker) {
			struckByStriker = false; // just do this if hit by the striker, not by anything else that might collide
			  
			// now get the angle and strength of the strike... need do something like calcreSetPositionAndTrajectory
			double trajectoryAngle = striker.getStrikeAngle();
			//System.out.println("angle from striker="+trajectoryAngle);	
			double angleInRadians = Math.toRadians(trajectoryAngle);
			double newVelocity = MoveableGamePiece.defaultVelocity * (striker.getStrikeStrength() / 25.0);
			// current position
			double currXpos = this.getCurrPosn().getX();
			double currYpos = this.getCurrPosn().getY();
			  if (!ReboundGamePanel.SCREEN_AREA.contains(this.directionLv.getLocation().getX(), this.directionLv.getLocation().getY()))
				  this.setToWaitToRestart();
	
			// use rotation on y-axis to get the point that we can use to determine the vector
			AffineTransform at = AffineTransform.getRotateInstance(angleInRadians, currXpos, currYpos);
			Point2D newPoint = at.transform(new Point2D.Double(currXpos, currYpos + newVelocity), null);
			double xChange = newPoint.getX() - currXpos;
			double yChange = newPoint.getY() - currYpos;
	
			this.directionLv.changeDirection(xChange, yChange);
//			System.out.println("new direction = "+direction+" and length="+direction.getLength());	  
			
		}
	}
  
	protected void reset() {
		striker.reset();
		this.draggingAiming = this.draggingPuck = this.draggingStriker = false;		
	}
	
	public void changeStaticPosition(int direction) {
		// allowed to move left and right atm
		int increment = Puck.CHANGE_POS_INCR;
		if (direction == KeyEvent.VK_LEFT || direction == KeyEvent.VK_UP)
			increment *= -1;
		
		Location2D currPosn = this.directionLv.getLocation();
		double newXpos = currPosn.getX();
		double newYpos = currPosn.getY();

		if (direction == KeyEvent.VK_LEFT || direction == KeyEvent.VK_RIGHT)
			newXpos += increment;
		else
			newYpos += increment;
		
		if (this.gameBounds.gameZoneIsInStrikerArea(newXpos, newYpos, this.radius))
			this.directionLv.changeLocation(newXpos, newYpos);
		else 
		{
			Rectangle bounds = this.gameBounds.getStrikeBounds();
			// adjust as necessary to go to the edge
			if (direction == KeyEvent.VK_LEFT)
				this.directionLv.changeLocation(bounds.x + this.radius, newYpos);
			else if (direction == KeyEvent.VK_RIGHT)
				this.directionLv.changeLocation(bounds.x + bounds.width - this.radius, newYpos);
			else if (direction == KeyEvent.VK_DOWN)
				this.directionLv.changeLocation(newXpos, bounds.y + bounds.height - this.radius);
			else
				this.directionLv.changeLocation(newXpos, bounds.y + this.radius);
		}
		
		striker.plotDebuggingCollisions();

		  
	}
	// mouse control by dragging
	public void changeStaticPosition(int xpos, int ypos) {
		// 	make a rectangle of how the box has moved so overlapping pieces can be redrawn
		this.makeMovementBounds(xpos, ypos, this.getCurrPosn().getX(), this.getCurrPosn().getY(), Puck.MOUSE_DETECT_DISTANCE);
		
		if (this.gameBounds.gameZoneIsInStrikerArea(xpos, ypos, this.radius))
			this.directionLv.changeLocation(xpos, ypos);
		else 
		{
			Rectangle bounds = this.gameBounds.getStrikeBounds();
			// adjust as necessary to go to the edge
			if (bounds.x + this.radius > xpos) // gone past left
				xpos = (int)(bounds.x + this.radius);
			else if (bounds.x + bounds.width - this.radius < xpos) // gone past right
				xpos = (int)(bounds.x + bounds.width - this.radius);
			
			if (bounds.y + bounds.height - this.radius < ypos) // gone past bottom
				ypos = (int)(bounds.y + bounds.height - this.radius);
			else if (bounds.y + this.radius > ypos) // gone past top
				ypos = (int)(bounds.y + this.radius);
			
			this.directionLv.changeLocation(xpos, ypos);
		}
		
		// might have to redraw a clip exclusion zone, use the radius that the striker uses to draw aimers
//		Puck.panel.redrawExclusionClipZones(this.getCurrPosn(), (int)Puck.MOUSE_DETECT_DISTANCE);
		
		striker.plotDebuggingCollisions();
	}
	
	protected boolean drawPiece(Graphics2D g2) {
	    striker.draw(g2);
		return super.drawPiece(g2);
	}

	//-------------------- GuiComponent methods --------------------\\ 

	@Override
	public boolean canRespondToMouse() {
		return !this.isMoving;
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {
		striker.showAimers(true);
	}

	@Override
	public void mouseExited(MouseEvent e) {
		striker.showAimers(false);
		if (striker.isAiming())
			striker.setAiming(false, false);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		int xpos = e.getX(), ypos = e.getY();
		double distanceFromPuckCentreSq = this.getCurrPosn().getDistanceSquared(xpos, ypos);

		// what kind of drag is happening?
		this.draggingPuck = distanceFromPuckCentreSq <= this.radiusSquared;
		this.draggingStriker = (!draggingPuck
							&& distanceFromPuckCentreSq <= Puck.MOUSE_DETECT_DISTANCE_SQUARED 
							&& striker.positionIsInsideStrikerBolt(xpos, ypos));
		this.draggingAiming = (!draggingPuck && !draggingStriker 
							&& distanceFromPuckCentreSq > this.radiusSquared
							&& distanceFromPuckCentreSq <= Puck.MOUSE_DETECT_DISTANCE_SQUARED);
		
		if (draggingStriker || draggingAiming) {
			striker.startMouseDrag(xpos, ypos);
		}
		else 
			striker.stopMouseDrag();
		
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (striker.settingStrengthWithMouse()) { // fire!
			if (striker.getStrikeStrength() > Striker.MIN_STRENGTH) {
				striker.strikePuck();
			}
			else
				striker.stopStriking(false);
		}

		striker.stopMouseDrag();
		this.draggingAiming = false;
		this.draggingPuck = false;
		this.draggingStriker = false;
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		int xpos = e.getX(), ypos = e.getY();
		double distanceFromPuckCentreSq = this.getCurrPosn().getDistanceSquared(xpos, ypos);

		// what kind of drag is happening?
		if (this.draggingStriker) {
			striker.strikeWithMouse(xpos, ypos);
// THIS WILL BE A USER TOGGLED SETTING
			if (Puck.STRIKE_AIMS_TOO && distanceFromPuckCentreSq > this.radiusSquared)
				striker.aimWithMouse(xpos, ypos);
			
			striker.startMouseDrag(xpos, ypos);
		}
		else if (this.draggingAiming) {
			if (distanceFromPuckCentreSq > this.radiusSquared)
				striker.aimWithMouse(xpos, ypos);

			striker.startMouseDrag(xpos, ypos);
		}
		else {
			if (this.draggingPuck) {
				this.changeStaticPosition(xpos, ypos);
				striker.showAimers(true);
			}
			else
				striker.setAiming(false, false);
	
		}
		
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	/*
	 * want to respond to mouse events if already dragging too
	 */
	@Override
	public boolean mouseOverComponent(Point2D mouse) {
		double distanceFromPuckCentreSq = this.getCurrPosn().getDistanceSquared(mouse.getX(), mouse.getY());
		return distanceFromPuckCentreSq <= Puck.MOUSE_DETECT_DISTANCE_SQUARED
				|| this.draggingAiming
				|| this.draggingPuck
				|| this.draggingStriker;
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {}

	@Override
	public void setTemporalPopupBox(TemporalPopupBox tpb) {}

	@Override
	public boolean drawGuiComponent(Graphics2D g2) { return false;	}

	@Override
	public Cursor mouseOverGetCursor(Point2D mouse) {
		double distanceFromPuckCentreSq = this.getCurrPosn().getDistanceSquared(mouse.getX(), mouse.getY());
		if (distanceFromPuckCentreSq <= this.radiusSquared && !this.draggingStriker)
			return GuiComponentMouseManager.MOVE_CURSOR;
		else
			return GuiComponentMouseManager.HAND_CURSOR;
	}
	  
}  // end of Puck class

