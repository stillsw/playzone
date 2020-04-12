package firstReboundGame;

import firstReboundGame.gameplay.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

import firstReboundGame.sound.SoundManager;

public abstract class MoveableGamePiece extends StrikeableGamePiece
{
	public int lastDebugPlace = 0;
	public String lastDebugMsg;
	public static final double defaultVelocity = -(ReboundGamePanel.PANEL_HEIGHT / 100); // in y axis
	private static double DEBUG_LINE_LEN = 100;
	protected boolean isMoving;
	protected boolean inGoal;
	protected LineVector2D directionLv = LineVector2D.ZERO_LINE_VECTOR.clone();
	protected int movedOnTick;
	protected double mass;
	protected PlayManager playManager;
	protected ScoringManager scoringManager;
	protected Rectangle2D lastMovementBounds;
	
	// collisions
	protected Collision collision;
	protected int collidedTick;
	  
	public MoveableGamePiece(ScoringManager scoringManager, PlayManager playManager, Location2D placement, ColorArrangement colors) {
		super(placement, false, colors);
		this.directionLv.changeLocation(placement.getX(), placement.getY());
		this.isStatic = false;
		this.playManager = playManager;
		this.scoringManager = scoringManager;
	} // end of constructor

	public void setStruckBy(MoveableGamePiece struckBy, int gameTick) {	
		super.setStruckBy(struckBy, gameTick);
		this.movedOnTick = gameTick;
	}
	public void setStatic(boolean isStatic) { this.isStatic = isStatic; }

	// set the direction first, otherwise it will be whatever it was last time this piece moved
	public void setMoving(boolean isMoving) {  
		this.isMoving = isMoving;
		if (isMoving) {
			this.isWaitingToRestart = false;
			this.inGoal = false;
//			System.out.println("MoveableGamePiece.setMoving: velocity="+this.directionLv + " bigger than radius = "+(this.directionLv.length>this.getRadius()));
		}
		else
			this.directionLv.changeDirection(0.0, 0.0);
	}
  
	public boolean isMoving() {	return this.isMoving; }
	public LineVector2D getDirection() { return this.directionLv; }
	public int getMovedOnTick() { return this.movedOnTick; }
	public double getMass() { return this.mass; }
	public Location2D getNextPosn() {
		if (this.isMoving())
			return directionLv.getEndLocation();
		else
			return directionLv.getLocation();
	}
	public void changeLocation(double x, double y) {
		directionLv.getLocation().setLocation(x, y);
	}
	public void setCollision(Collision collision, int gameTick) {
		this.collision = collision;
		this.collidedTick = gameTick;
	}
	public Collision getCollision(int gameTick) {
		if (gameTick == this.collidedTick)
			return collision;
		else
			return null;
	}
	public boolean canCollide() {
		return (!this.inGoal);
	}
	
	protected void bounce() { 
		LineVector2D newBearing = collision.getNewBearing();
//		System.out.println("MoveableGamePiece.bounce: "+this+" direction was = "+directionLv+ " set = "+newBearing);
		this.directionLv.changeDirection(newBearing.getVx(), newBearing.getVy());
		Location2D nextPosn = newBearing.getLocation();
		this.changeLocation(nextPosn.getX(), nextPosn.getY());
		
		// zero vector set to not moving, this will save collision checking 
		this.setMoving(!(Double.compare(Math.abs(newBearing.getVx()), 0.0) == Constants.DOUBLE_COMPARE_EQUAL && Double.compare(Math.abs(newBearing.getVy()), 0.0) == Constants.DOUBLE_COMPARE_EQUAL));
	}

	@Override
	public void update(int gameTick) {
		super.update(gameTick);
		this.redraw = true;
	}
	
	public void move(int gameTick) {
		this.update(gameTick);

		if (!ReboundGamePanel.SCREEN_AREA.contains(this.directionLv.getLocation().getX(), this.directionLv.getLocation().getY())) {
//			if (collision!=null) 
//				System.out.println("MoveableGamePiece.move: "+this+" off the map = "+collision.getNewBearing()+"\n\t last debug place="+this.lastDebugPlace+"\n\t msg="+this.lastDebugMsg);
			
			this.setToWaitToRestart(gameTick);
			// register with scoring manager
			if (this instanceof TargetDisk) // only these can move so far
				this.scoringManager.registerLeavePlayArea((TargetDisk)this);
		}
		else if (this.isMoving) {
			double oriXpos = this.getCurrPosn().getX();
			double oriYpos = this.getCurrPosn().getY();
			this.lastMovementBounds = null;
						
			if (gameTick > this.movedOnTick) { // could have been moved by another piece when bouncing off it
				this.movedOnTick = gameTick;
				// don't update the current position until tested for collisions
				if (MoveableGamePiece.detectCollision(this)) {
					if (ReboundGamePanel.showDebugging) {
						System.out.println("MoveableGamePiece.Move: prevent further movement so can see everything ");
						this.isMoving = false;
						if (collision != null && collision.getGamePiece() instanceof MoveableGamePiece) 
							((MoveableGamePiece)collision.getGamePiece()).setMoving(false);
					}
				}
				else {
					this.directionLv.advancePosition();
					// apply deceleration 
					if (gameTick % 20.0 == 0 
							&& Double.compare(ReboundGamePanel.DECELERATION_PER_20_TICKS, 0.0) != Constants.DOUBLE_COMPARE_EQUAL) {
						double preLen = this.directionLv.getLength();
						preLen += preLen * ReboundGamePanel.DECELERATION_PER_20_TICKS;
						if (preLen < ReboundGamePanel.STOP_VELOCITY) {
							this.setToWaitToRestart(gameTick); // actually stop it, but don't set to not moving... let manager do that
							// register with scoring manager
							if (this instanceof TargetDisk) // only these can move so far
								this.scoringManager.registerStoppedMoving((TargetDisk)this);
						}
						else
							this.directionLv.setLength(preLen);
						
						
					}
				}
				this.makeMovementBounds(oriXpos, oriYpos, this.getCurrPosn().getX(), this.getCurrPosn().getY(), this.getRadius());
			}
		}
		
		
	}  // end of move()

	/*
	 * make a bounding rectangle which encloses the last movement
	 */
	protected abstract void makeMovementBounds(double xpos1, double ypos1, double xpos2, double ypos2, double radius);
	
	public Rectangle2D getMovementBounds() { return this.lastMovementBounds; };
	
	public void setToWaitToRestart(int gameTick) {
		// super class just sets a timer and then starts the piece back in play when time has elapsed
		// for these want a co-ordinated method. Only reply pieces after all have stopped moving (incl leaving playing area) 
		if (this.playManager != null)
			playManager.waitToRestart(this, gameTick);
		else 
			System.out.println("MoveableGamePiece.setToWaitToRestart: No PlayManager registered cannot manage wait.");
	}
	
	public void resetToStart() {
		setMoving(false);
		this.inGoal = false;
		this.directionLv.getLocation().setLocation(startPos.getX(), startPos.getY());
	}
	  
	public Location2D getCurrPosn() { return this.directionLv.getLocation(); }
	
	// did collision come between 2 positions that might have missed it? have to account for that
	// returns the distance to impact
	private static double willDisksCollide(TargetDisk mover, TargetDisk collider, double combinedRadii)
	{
		boolean impact = false;
		double closestDistance = Vector2D.LONG_DISTANCE;
		Location2D moverPosn = mover.getCurrPosn();
		Location2D colliderPosn = collider.getCurrPosn();
		
		// fast out... if this distance is greater than distance that can be travelled, 
		// test no further avoid sqrt by testing squares
		double colliderLen = collider.getDirection().getLength();
		double originalMoverLen = mover.getDirection().getLength();
		double combinedVLensAndRadii = colliderLen + originalMoverLen + combinedRadii;
		double distanceSq = moverPosn.getDistanceSquared(colliderPosn);
		if (Double.compare(combinedVLensAndRadii*combinedVLensAndRadii, distanceSq) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) {
			mover.lastDebugPlace=collider.lastDebugPlace = 1;
			return closestDistance;
		}
		// take copies to protect original
		LineVector2D moverV = mover.getDirection().clone(); 
		VolatileVector2D colliderV = new VolatileVector2D(collider.getDirection());
		// both objects moving is more complicated, subtract movement vector of collidedWith 
		// from movement vector of mover and treat as collision with just one moving object
		if (collider.isMoving()) // new vector is mover vector less collidedDisk vector
			moverV = moverV.getSubtraction(colliderV);								
		// find vector between centres before movement
		Vector2D sPsV = new Vector2D(moverPosn, colliderPosn);
		// get projection onto movement vector
		Vector2D scaledMoverV = sPsV.getProjectionOntoVector(moverV);
		// projection point is on mover vector at closest point to obstacle centre posn
		Location2D projPoint = moverPosn.getAddition(scaledMoverV);
		// the vector from projPoint to the obstacle centre is normal to mover vector
		double normalLenSq = projPoint.getDistanceSquared(colliderPosn);
		// was there a collision already at this place... use squares to avoid unnecessary square roots
//			double diff = combinedRadii - normalLen;
		double combinedRadiiSq = combinedRadii*combinedRadii;
		double diffSq = combinedRadiiSq - normalLenSq;

		if (Double.compare(diffSq, 0.0) > Constants.DOUBLE_COMPARE_EQUAL) { // > 0
			mover.lastDebugPlace=collider.lastDebugPlace = 4;
			// find the point back from projPoint the distance of the combined radii from the collider
			// use right angle triangle to get the length to put the new point
			double moveBackLen = Math.sqrt(combinedRadii*combinedRadii - normalLenSq);
			// create a unit vector from the mover start posn to this new point
			// don't need the length, so create a Volatile version
			VolatileVector2D moverVu = moverV.getVolatileUnitVector();
			Vector2D moverForwardsV = new Vector2D(moverPosn, new Location2D(projPoint.getX() - moveBackLen*moverVu.getVx(), projPoint.getY() - moveBackLen*moverVu.getVy()));
			// work out the dot product of those 2 vectors (units) which is the cos of the angle,
			// a positive value means they are going in same direction
			double dp2 = moverV.getUnitsDotProduct(moverForwardsV);
			// point being on mover vector means collision happens in this movement
			double displacementLen = scaledMoverV.getLength() - moveBackLen;
			// unless negative dp2 (cos of angle) means moveBackV and moverV are going in opposite
			// directions... ie. impact was before this tick
			if (Double.compare(dp2, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) {
				mover.lastDebugPlace=collider.lastDebugPlace = 5;
				mover.lastDebugMsg=collider.lastDebugMsg="MoveableGamePiece.willDisksCollide: collision before move (diffPosn="+displacementLen+" distance between="+Math.sqrt(distanceSq)
					+"\n\t mover at/going="+mover.getDirection()
					+"\n\t collider at/going="+collider.getDirection()
					+"\n\t mover moveTick="+mover.getMovedOnTick()
					+"\n\t collider moveTick="+collider.getMovedOnTick()
//					+"\n\t mover struck last tick="+mover.getStruckAtGameTick()
//					+"\n\t collider struck last tick="+collider.getStruckAtGameTick()
					+")";
			}
			else if (Double.compare(displacementLen, moverV.getLength()) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
				mover.lastDebugPlace=collider.lastDebugPlace = 6;
				mover.lastDebugMsg=collider.lastDebugMsg="MoveableGamePiece.willDisksCollide: collision is after next move (diffPosn="+displacementLen+" distance="+Math.sqrt(distanceSq)+" distance new pos="+mover.getNextPosn().getDistance(collider.getCurrPosn())+")";
			}
			else {
				mover.lastDebugPlace=collider.lastDebugPlace = 7;
				impact = true;
			}
			if (impact) {
				mover.lastDebugPlace=collider.lastDebugPlace = 8;
				closestDistance = displacementLen;
				double elapsed = moverForwardsV.getLength() / moverV.getLength();
				// move pieces by the elapsed time
				LineVector2D originalMoverV = mover.getDirection().clone(); // we changed the previous value to include 2nd moving object
				VolatileVector2D replaceMoverV = new VolatileVector2D(elapsed * originalMoverV.getVx(), elapsed * originalMoverV.getVy());
				VolatileVector2D replaceColliderV = new VolatileVector2D(elapsed * colliderV.getVx(), elapsed * colliderV.getVy());
				Location2D moverImpactPosn = moverPosn.getAddition(replaceMoverV);
				Location2D colliderImpactPosn = colliderPosn.getAddition(replaceColliderV);				
//					System.out.println("MoveableGamePiece.willDisksCollide: time to impact = "+elapsed+
//							"\n\t Collision posns:\n\t\t mover = "+moverImpactPosn+
//							"\n\t\t mover originally at = "+moverPosn+
//							"\n\t\t mover vector = "+originalMoverV+
//							"\n\t\t mover vector back = "+replaceMoverV+
//							"\n\t\t collider = "+colliderImpactPosn+
//							"\n\t\t collider originally at = "+colliderPosn+
//							"\n\t\t collider vector = "+colliderV+
//							"\n\t\t collider vector back = "+replaceColliderV+
//							"\n\t distance between old positions = "+moverPosn.getDistance(colliderPosn)+
//							"\n\t distance between new positions = "+moverImpactPosn.getDistance(colliderImpactPosn)
//							);
				// new bearing is handled when from calling method
				LineVector2D moverBackLine = new LineVector2D(colliderImpactPosn, moverImpactPosn);
				double distanceBeyondImpact = moverImpactPosn.getDistance(originalMoverV.getEndLocation());
				Location2D obsStartPoint = moverBackLine.getPointOnLine(collider.getRadius());
				LineVector2D v2;	
				if (ReboundGamePanel.showDebugging)
					v2 = moverBackLine.getPrettyBisectedNormal(obsStartPoint, ReboundGamePanel.PANEL_WIDTH / 4);
				else
					v2 = moverBackLine.getBisectedNormal(obsStartPoint);
				// put a collision on the mover referring to the collider
				Collision collision = new Collision(collider, moverPosn, moverImpactPosn, originalMoverV, (LineVector2D)null, distanceBeyondImpact, 0.0, moverBackLine, v2);
				mover.setCollision(collision, mover.getMovedOnTick());
				LineVector2D colliderBackLine = new LineVector2D(colliderImpactPosn, moverImpactPosn);
				distanceBeyondImpact = colliderImpactPosn.getDistance(colliderV.getEndLocation(colliderPosn));
				// put a collision on the collider referring to the mover
				collision = new Collision(mover, colliderPosn, colliderImpactPosn, collider.getDirection(), (LineVector2D)null, distanceBeyondImpact, 0.0, colliderBackLine, v2);
				collider.setCollision(collision, mover.getMovedOnTick());
			}//impact
		}// diff > 0
		else if (Double.compare(diffSq,0.0) == Constants.DOUBLE_COMPARE_EQUAL) { // equal
			// NOT TOO SURE ABOUT THIS CASE... CAN i GENERATE IT IN TEST DATA... MAYBE IT IS CAUGHT IN PREVIOUS TICK??	
			// IF PROVES TO BE THE CASE, CAN CHANGE PREVIOUS IF... TO > INSTEAD OF >= AND REMOVE LAYER OF IFs				
			mover.lastDebugPlace=collider.lastDebugPlace = 3;
			mover.lastDebugMsg=collider.lastDebugMsg="MoveableGamePiece.willDisksCollide: Collision is exactly at this point "+projPoint+" distance between="+projPoint.getDistance(moverPosn);
		} 
		else {
			mover.lastDebugPlace=collider.lastDebugPlace = 9;
			mover.lastDebugMsg=collider.lastDebugMsg="MoveableGamePiece.willDisksCollide: collision nowhere on trajectory (diff="+Math.sqrt(diffSq)+" distance between="+Math.sqrt(distanceSq)+")";
		}
		return closestDistance;
	}
	
	public static boolean detectCollision(MoveableGamePiece mover)
	{
		LineVector2D originalBearing = mover.getDirection();
		Collision collision = null;
		int gameTick = mover.getMovedOnTick();
		double closestDistance = Vector2D.LONG_DISTANCE;
		double radius = mover.getRadius();
		LineVector2D moveBackLine = null;
		StrikeableGamePiece collidedWith = null;
		double combinedRadii;
		Location2D nextPosn = originalBearing.getEndLocation();
		
	  	// find any obstacle that collides... pick soonest one
		// first try mobile obstacles
		MoveableGamePiece[] mobileObstacles = panel.getMobileObstacles();
	    for (int i = 0; i < mobileObstacles.length; i++) {			
	    	if (mobileObstacles[i] == mover)
				continue; 
			if (mobileObstacles[i].getMovedOnTick() >= gameTick) 
				continue; 

			if (mover instanceof TargetDisk && mobileObstacles[i] instanceof TargetDisk
					&& mover.canCollide() && mobileObstacles[i].canCollide()) {
				combinedRadii = mobileObstacles[i].getRadius() + radius;
				double collisionDistance = willDisksCollide((TargetDisk)mover, (TargetDisk)mobileObstacles[i], combinedRadii);
				if (Double.compare(collisionDistance, closestDistance) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) {
					closestDistance = collisionDistance;
					collidedWith = mobileObstacles[i];

					TargetDisk collider = (TargetDisk)collidedWith;
					// the exact point is already known from the collision test
					collision = mover.getCollision(mover.getMovedOnTick());
					mover.setStruckBy(collider, gameTick);
					collider.setStruckBy(mover, gameTick);
					Collision colliderCollision = collider.getCollision(gameTick);
					collision.setStruckCollision(colliderCollision);
					colliderCollision.setStruckCollision(collision);
					collision.calculateNewBearings(collider, colliderCollision);
					mover.bounce();
					collider.bounce();	
					// register with scoring manager
					mover.scoringManager.registerBounce((TargetDisk) mover, collider);
					break;
				}
	
//TEMPORARY check to make sure that we do catch all collisions in the previous method 			
				if (Double.compare(collisionDistance, Vector2D.LONG_DISTANCE) == Constants.DOUBLE_COMPARE_EQUAL 
						&& nextPosn.getDistance(mobileObstacles[i].getNextPosn()) - combinedRadii <= 0.0)
					System.out.println("BUT THEY HAVE collided! distance between them="+nextPosn.getDistance(mobileObstacles[i].getNextPosn())+" combinedRadii="+combinedRadii+"\n\t last debug place="+mover.lastDebugPlace+"\n\t msg="+mover.lastDebugMsg);
			}
	    }

	    // only test for static collisions if didn't find a moving one
	    if (collidedWith == null) { 
	    	// test if piece is within touch of a wall
	    	boolean canHitPerimeter = mover.gameBounds.withinTouchOfBounds(nextPosn, radius);
	    	StrikeableGamePiece[] staticObstacles = panel.getStaticObstacles();
		    for (int i = 0; i < staticObstacles.length; i++) {
		    	// don't go any further if this obstacle is part of the wall and mover is not within reach of that
		    	if ((staticObstacles[i].isPartOfPerimeter() && canHitPerimeter) || !staticObstacles[i].isPartOfPerimeter()) {
					combinedRadii = staticObstacles[i].getRadius() + radius;
					
					// this does not expect LinearObstacles to be able to move,
					// if that happens there must be design changes
					if (staticObstacles[i] instanceof LinearObstacle) {
						LineVector2D[] lines = ((LinearObstacle)staticObstacles[i]).getLines();
						// obstacle could be one line like a wall, or several like a box, find the closest line to it 
						// by finding the closest point on any of the lines
						LineVector2D lineBack;
						for (int j = 0; j < lines.length; j++) {
							lineBack = lines[j].getShortestLineToPoint(nextPosn);
							double distance = nextPosn.getDistance(lineBack.getLocation()) - combinedRadii;
							if (Double.compare(distance, 0.0) <= Constants.DOUBLE_COMPARE_EQUAL 
									&& Double.compare(distance, closestDistance) < Constants.DOUBLE_COMPARE_EQUAL && lineBack != null) {
								closestDistance = distance;
								collidedWith = staticObstacles[i];
								moveBackLine = lineBack;
							}
						}
					}
					if (staticObstacles[i] instanceof TargetDisk) { // goal posts are static targetdisks
						LineVector2D lineBack = new LineVector2D(staticObstacles[i].getCurrPosn(), nextPosn);
						double distance = nextPosn.getDistance(lineBack.getLocation()) - combinedRadii;
						if (Double.compare(distance, 0.0) <= Constants.DOUBLE_COMPARE_EQUAL 
							&& Double.compare(distance, closestDistance) < Constants.DOUBLE_COMPARE_EQUAL) {
							closestDistance = distance;
							collidedWith = staticObstacles[i];
							moveBackLine = lineBack;
						}
					}
				} // part of perimeter and within touch or not part of it
			}// for loop

		    if (collidedWith != null) { // determined that we have hit something, so bounce off it
				// move back line is the normal with the angle that we are from the obstacle
				Location2D obsStartPoint = moveBackLine.getPointOnLine(collidedWith.getRadius());
				// v2 is the effective angle of contact with the obstacle
				LineVector2D v2;
				if (ReboundGamePanel.showDebugging)
					v2 = moveBackLine.getPrettyBisectedNormal(obsStartPoint, ReboundGamePanel.PANEL_WIDTH / 4);
				else
					v2 = moveBackLine.getBisectedNormal(obsStartPoint);
				// v2 (obstacle) vector normal on moveBackLine, length is long enough to not get missed
				if (!originalBearing.areParallel(v2)) // can't see how this could be ... but just in case
				{
					Location2D ip = originalBearing.getAdjustedIntersectionPoint(nextPosn, v2, moveBackLine, radius, closestDistance);
					if (ip != null) { // null would be an error since we already determined we've collided
						double distanceAfterIntersect = ip.getDistance(nextPosn);
						collision = new Collision(collidedWith, nextPosn, ip, originalBearing.clone(), (LineVector2D)null, distanceAfterIntersect, 0.0, moveBackLine, v2);
						collision.calculateNewBearing();
						collidedWith.setStruckBy(mover, gameTick);
						mover.setCollision(collision, gameTick);
						mover.playSound(SoundManager.WALL_BOUNCE);
						mover.bounce();
						// register with scoring manager
						mover.scoringManager.registerBounce((TargetDisk) mover, collidedWith);
					}// ip != null
					else 
						System.out.println("MoveableGamePiece.collidedWithObstacle: error: "+mover+" collision with "+collidedWith+" but no ip returned");
				}
				else // parallel lines
					System.out.println("MoveableGamePiece.collidedWithObstacle: error: "+mover+" collision with "+collidedWith+" but appears to be parallel with angle of impact");
			}//collidedWith != null
	    } // collidedWith == null
	    		
		return collision != null;
	}
	
	private void drawDebugLine(Graphics2D g2, LineVector2D line, boolean extendForwards, boolean showArrow) {
		if (!extendForwards) {
			line = line.clone();
			line.reverseLine();
		}
		if (line.getLength() < MoveableGamePiece.DEBUG_LINE_LEN)
			line.setLength(MoveableGamePiece.DEBUG_LINE_LEN);
		int startX = (int)line.getLocation().getX();
		int startY = (int)line.getLocation().getY();
		Location2D endPoint = line.getEndLocation();
		int endX = (int)endPoint.getX();
		int endY = (int)endPoint.getY();
		g2.drawLine(startX, startY, endX, endY);
		if (showArrow)
			g2.drawOval(endX - 2, endY - 2, 4, 4);
		
	}
	
	public void goalScored(int gameTick) {
		this.setToWaitToRestart(gameTick); 
		this.setMoving(false);
		
	}
	public void setInGoal(boolean inGoal) {
		this.inGoal = inGoal;
	}
	
	protected boolean drawPiece(Graphics2D g2) {

		super.drawPiece(g2);
		
		boolean canDraw = (playManager != null && playManager.canDrawPiece(this));
		if (!canDraw && playManager == null) {
			if (this.isWaitingToRestart && System.currentTimeMillis() - this.waitingSince > 600) // don't draw before time up
				this.isWaitingToRestart = false;

			canDraw = !this.isWaitingToRestart();
		}
			
		if (canDraw)
		{
			if (this instanceof TargetDisk) { //includes puck
				if (ReboundGamePanel.showDebugging && collision != null) // draw a test trajectory
				{
					double radius = this.getRadius();
					int diameter = (int)((TargetDisk)this).getDiameter();
					g2.setColor(Color.gray);
					drawDebugLine(g2, collision.getOriginalBearing(), false, true);
					Location2D intersectionPoint = collision.getIntersectionPoint();
					g2.setColor(Color.green);
					g2.drawOval((int)(intersectionPoint.getX() - radius), (int)(intersectionPoint.getY() - radius), diameter, diameter);
					// draw the normal in green too
					drawDebugLine(g2, collision.getNormal(), true, true);
					// draw the bounce in blue
					LineVector2D newBearing = collision.getNewBearing();
					int startX = (int)newBearing.getLocation().getX();
					int startY = (int)newBearing.getLocation().getY();
					g2.setColor(Color.blue);
					drawDebugLine(g2, newBearing, true, true);
					g2.drawOval((int)(startX - radius), (int)(startY - radius), diameter, diameter);
					g2.setColor(Color.red);
					drawDebugLine(g2, collision.getObstacleVector(), true, false);
					// draw where detection happened in red
					Location2D detectedAt = collision.getDetectedAt();
					g2.drawOval((int)(detectedAt.getX() - radius), (int)(detectedAt.getY() - radius), diameter, diameter);
					g2.setColor(Color.gray);
				}
			}
		
		}

		return canDraw;
		  
	}  // end of draw()

	protected abstract void setMass();

}  // end of class

