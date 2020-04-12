public abstract class Sensor {  
  public abstract void directionChanged(Drone drone);
  public abstract void draw(Drone drone);
  public void drawNotTransformed(Drone drone) {}

  public boolean isVectorFromLocFacingLoc2(Vector2D dir, Location2D loc, Location2D loc2, float distSq, float accuracy) {
    // do a dot product comparison 
    // need to have the same length vectors though 
    float dirLen = (float)dir.getLength();
    float delta = (float)(Math.sqrt(distSq) / dirLen);
    float scaledDirVx = (float) dir.getVx() * delta;
    float scaledDirVy = (float) dir.getVy() * delta;
    float wayVx = (float)(loc2.getX() - loc.getX());
    float wayVy = (float)(loc2.getY() - loc.getY());

/*    
    // debug only
    float scaledLenSq = scaledDirVx * scaledDirVx + scaledDirVy * scaledDirVy;
    Vector2D sv = new Vector2D(scaledDirVx, scaledDirVy);
    Vector2D cl = new Vector2D(dir.getVx(), dir.getVy());
    cl.setLength(Math.sqrt(distSq));
*/    
    // now dot of the vectors that are the same length should be a good comparison
    return (scaledDirVx * wayVx + scaledDirVy * wayVy) > distSq * accuracy;
  }
  
  public boolean droneOutOfPlanetQuadRange(Drone drone, Planet planet, int rangeInQuads) {
    return (drone.getQuadRow() < planet.getQuadRowStart() - rangeInQuads
    || drone.getQuadRow() > planet.getQuadRowEnd() + rangeInQuads
    || drone.getQuadCol() < planet.getQuadColStart() - rangeInQuads
    || drone.getQuadCol() > planet.getQuadColEnd() + rangeInQuads);
  }  
  
  /**
   * very similar to the method in LineVector2D, but for some reason trying to create fewer objs
   */
  public float getShortestDistanceOnLineToPoint(Location2D startLoc, Location2D endLoc, Location2D p1) {
    Vector2D line = new Vector2D(startLoc, endLoc); 
    Vector2D v3 = new Vector2D(startLoc, p1);
    
    // neither end of the vector can be closer, but for completeness (since only dot products are calc'd)
    // also test for relation to ends of the line. This way the method can be re-used where that may not be the case
    
    // find dot product between vector v3 and the line obstacle
    double dp = v3.getDotProduct(line);
    // closer to the origin point if dp is -ve so use v3
    if(dp < 0.0) {
      println("closest to origin"); // should not see this because already checked that it's further than the radius to the planet centre
      return (float) v3.getLength();
    }
    // otherwise see if closer to the end point
    else {
      Vector2D v4 = new Vector2D(endLoc, p1);
      // again dp between v4 and line obstacle
      dp = v4.getDotProduct(line);
      // hits end point first if dp +ve
      if(dp > 0.0) {
        println("closest to end point"); // should not see this either
        return (float)v4.getLength();
      }

      Vector2D projV = v3.getProjectionOntoVector(line);
      Location2D pointOnProjection = startLoc.getAddition(projV);
      return (float) new Vector2D(pointOnProjection, p1).getLength();
    }
  }
}

public class DetectAttacker extends Sensor {  
  public void directionChanged(Drone drone) {}
  public void draw(Drone drone) {}
  
  public boolean inVisibleHideDistanceOfAttacker(Drone drone, Attacker attackDrone) {

    // pursue range is one ring out from direct attack range
    
    LineVector2D lv = attackDrone.getLineVector();    
    Location2D attackerLoc = lv.getLocation();
    
    // broad phase rule out if not within quads
    if (attackDrone.getQuadRow() < drone.getQuadRow() - DRONE_HIDE_RANGE_QUADS
    || attackDrone.getQuadRow() > drone.getQuadRow() + DRONE_HIDE_RANGE_QUADS
    || attackDrone.getQuadCol() < drone.getQuadCol() - DRONE_HIDE_RANGE_QUADS
    || attackDrone.getQuadCol() > drone.getQuadCol() + DRONE_HIDE_RANGE_QUADS) {
      return false;
    }
    
    // possible target, now check more accurately on distance
    Location2D droneLoc = drone.getLineVector().getLocation();
    float distSq = getLenSquaredFromCoords((float)attackerLoc.getX(), (float)attackerLoc.getY(), (float)droneLoc.getX(), (float)droneLoc.getY());
    if (distSq > droneHideRangeSq) {
      return false;
    }
    
    // check not obscured by any planets
    boolean foundObscuringPlanet = false;
    
    for (Planet planet : planets) {
      // planet must be in range of the attack drone
      if (droneOutOfPlanetQuadRange(attackDrone, planet, DRONE_HIDE_RANGE_QUADS)) {
        continue; // next planet
      }
      
      // get the dist (squared) from the attacker to the planet
      float distLocToPlanetSq = getLenSquaredFromCoords((float)attackerLoc.getX(), (float)attackerLoc.getY(), (float)planet.getOrigin().getX(), (float)planet.getOrigin().getY());

      // if the drone is nearer than that, then the planet can't be obscuring it
      if (distSq < distLocToPlanetSq) {
        continue; // next planet
      }

      float distDroneToPlanetSq = getLenSquaredFromCoords((float)droneLoc.getX(), (float)droneLoc.getY(), (float)planet.getOrigin().getX(), (float)planet.getOrigin().getY());
      
      // closer to the planet than to the attacker, needs examining, others can't be obscured by this planet
      if (distDroneToPlanetSq < distSq) {
        float shortestLine = getShortestDistanceOnLineToPoint(attackerLoc, droneLoc, planet.getOrigin());
        if (shortestLine < planet.getRadius()) {
          return false; // no need to try more planets
        }
      }        
    }
    
    return true; // not hidden by any planet
  }

}

public class LineOfSight extends Sensor {  
  public void directionChanged(Drone drone) {
  }
  
  public void draw(Drone drone) {
    stroke(DRONE_STEERING); // green
    ellipse(0, 0, droneHideRange * 2, droneHideRange * 2);
    stroke(DRONE_COHESION); // yellow
    ellipse(0, 0, attackerPursueRange * 2, attackerPursueRange * 2);
    stroke(DRONE_ALERT); // red
    ellipse(0, 0, attackerFireRange * 2, attackerFireRange * 2);
  }
  
  public Drone findNearestVisibleDroneInRange(Attacker attackDrone) {

    // pursue range is one ring out from direct attack range
    
    LineVector2D lv = attackDrone.getLineVector();    
    Location2D loc = lv.getLocation();
    
    Drone target = null;
    float targetDistSq = Float.MAX_VALUE;
    
    for (Drone drone : drones) {
      
      if (!drone.isAlive()) {
        continue;
      }
      
      // broad phase rule out if not within quads
      if (attackDrone.getQuadRow() < drone.getQuadRow() - ATTACKER_PURSUE_RANGE_QUADS
      || attackDrone.getQuadRow() > drone.getQuadRow() + ATTACKER_PURSUE_RANGE_QUADS
      || attackDrone.getQuadCol() < drone.getQuadCol() - ATTACKER_PURSUE_RANGE_QUADS
      || attackDrone.getQuadCol() > drone.getQuadCol() + ATTACKER_PURSUE_RANGE_QUADS) {
        continue;
      }
      
//      println("drone in quad range attacker("+attackDrone.getQuadCol()+"/"+attackDrone.getQuadRow()+") drone("+drone.getQuadCol()+"/"+drone.getQuadRow()+") range="+ATTACKER_PURSUE_RANGE_QUADS);
      
      // possible target, now check more accurately on distance
      Location2D droneLoc = drone.getLineVector().getLocation();
      float distSq = getLenSquaredFromCoords((float)loc.getX(), (float)loc.getY(), (float)droneLoc.getX(), (float)droneLoc.getY());
      if (distSq > targetDistSq || distSq > attackerPursueRangeSq) {
        continue;
      }
      
      // it's in range and nearer than any previously found drone
      
      // exclude it if it's not in the general direction the attacker is looking towards (accuracy range .25f = 1/4 * 90deg at either side, so 150deg vision)
      if (!isVectorFromLocFacingLoc2(lv, loc, droneLoc, distSq, .25f)) {
        continue;
      }

      // check not obscured by any planets
      boolean foundObscuringPlanet = false;
      
      for (Planet planet : planets) {
        // planet must be in range of the attack drone
        if (droneOutOfPlanetQuadRange(attackDrone, planet, ATTACKER_PURSUE_RANGE_QUADS)) {
          continue; // next planet
        }
        
        // get the dist (squared) from the attacker to the planet
        float distLocToPlanetSq = getLenSquaredFromCoords((float)loc.getX(), (float)loc.getY(), (float)planet.getOrigin().getX(), (float)planet.getOrigin().getY());

        // if the drone is nearer than that, then the planet can't be obscuring it
        if (distSq < distLocToPlanetSq) {
          continue; // next planet
        }

        float distDroneToPlanetSq = getLenSquaredFromCoords((float)droneLoc.getX(), (float)droneLoc.getY(), (float)planet.getOrigin().getX(), (float)planet.getOrigin().getY());
        
        // closer to the planet than to the attacker, needs examining, others can't be obscured by this planet
        if (distDroneToPlanetSq < distSq) {
          float shortestLine = getShortestDistanceOnLineToPoint(loc, droneLoc, planet.getOrigin());
          if (shortestLine < planet.getRadius()) {
            foundObscuringPlanet = true;
            break; // out of planets loop, plus set the flag which will also continue on the next drone
          }
        }        
      }
      
      if (foundObscuringPlanet) {
        continue; // next drone
      }
      
      // got this far, it's the best target so far
      target = drone;
      targetDistSq = distSq;
    }
    
    return target;
  }

}

public class DetectionBox extends Sensor {
  
  // tests further ahead, 2 vectors could be sufficient, but having 2 normals going each way is useful for collisions
  private Vector2D toRightNearCorner = new Vector2D(0, 0), toLeftNearCorner = new Vector2D(0, 0), boxLenVector = new Vector2D(0, 0);
  // for collisions with other drones, only updated when needed
  private Location2D[] verts = new Location2D[5]; // vertices 4 top left -> bottom left plus centre
  private int verticesUpdatedAtTick = -1, collisionTick = Integer.MIN_VALUE;
  private boolean isTouchingRight, isTouchingLeft;

  public DetectionBox() {
    for (int i = 0; i < verts.length; i++) {
      verts[i] = new Location2D(0, 0);
    }
  }
  
  public void resetFlags() {
    isTouchingRight = isTouchingLeft = false;
  }
  
  public boolean detectTouchingRightSide(Drone drone, Planet planet) {
    // not a good way to do this, many similar calcs for each side, when really the closest line to middle might be best
    // HOWEVER, by testing one side at a time, it does mean that side has to be touching, if use the middle line it's
    // more complex because have to include the 1/2 width of the box as a radius, but only directly to the sides...
    
    LineVector2D lv = drone.getLineVector();        
    if (getShortestDistanceOnLineToPoint(lv.getLocation(), verts[VERTEX_BOTTOM_RIGHT], verts[VERTEX_TOP_RIGHT], planet.getOrigin()) < planet.getRadius()) {
      isTouchingRight = true;
      return true;
    }
    else {
      return false;
    }
  }
  
  public boolean detectTouchingLeftSide(Drone drone, Planet planet) {
    LineVector2D lv = drone.getLineVector();        
    if (getShortestDistanceOnLineToPoint(lv.getLocation(), verts[VERTEX_BOTTOM_LEFT], verts[VERTEX_TOP_LEFT], planet.getOrigin()) < planet.getRadius()) {
      isTouchingRight = true;
      return true;
    }
    else {
      return false;
    }
  }
  
  public float getShortestDistanceOnLineToPoint(Location2D droneOrigin, Location2D sideStart, Location2D sideEnd, Location2D planetOrigin) {

    Vector2D v3 = new Vector2D(sideStart, planetOrigin);

    // find dot product between vector v3 and the box side vector
    double dp = v3.getDotProduct(boxLenVector);
    
    // closer to the starting point if dp is -ve so use v3
    if (dp < 0.0) {
      return (float)v3.getLength();
    }
    
    // otherwise see if closer to the end point
    else {
      Vector2D v4 = new Vector2D(sideEnd, planetOrigin);
                            
      // again dp between v4 and line 
      dp = v4.getDotProduct(boxLenVector);
      
      // hits end point first if dp +ve
      if (dp > 0.0) { 
        return (float)v4.getLength();
      }
      else {
        // most complicated, closest point is somewhere on the line (so somewhere near parallel to tangent of circle)
        // from getShortestLineToPoint() in LineVector2D (but fewer objs)
        // moveBackVector = new LineVector2D(startPoint.getAddition(v3.getProjectionOntoVector(this)), p1);
        
        // get projection of the box side line onto v3:
        Vector2D projV3 = v3.getProjectionOntoVector(boxLenVector);
        // add that to the start point to get a projection point
        Location2D projPoint = sideStart.getAddition(projV3);
        Vector2D res = new Vector2D(projPoint, planetOrigin);
        return (float)res.getLength();
      }
    }
  }

  public void directionChanged(Drone drone) {
    LineVector2D lv = drone.getLineVector();
    if (lv == null) {
      throw new IllegalStateException("drone has no line vector, should by now!");
    }
    boxLenVector.changeDirection(lv.getVx(), lv.getVy());
    boxLenVector.setLength(boxDetectionSensorLen);
    
    // left and right vectors are to the start of both the near corners of the box
    toRightNearCorner.changeDirection(-lv.getVy(), lv.getVx());
    toRightNearCorner.setLength(boxDetectionSensorLen / 4);
    toLeftNearCorner.changeDirection(lv.getVy(), -lv.getVx());
    toLeftNearCorner.setLength(boxDetectionSensorLen / 4);
  }

  /**
   * Tells this sensor to update the exact vertex position data because it's needed for collision detection
   */
  public void updateVertices(Drone drone) {
    // ignore if already up to date this tick (because this drone already got compared to another one)
    if (verticesUpdatedAtTick == tick) {
      return;
    }

    LineVector2D lv = drone.getLineVector();
    if (lv == null) {
      throw new IllegalStateException("drone has no line vector, should by now!");
    }
    Location2D droneLoc = lv.getLocation();
    
    verts[VERTEX_BOTTOM_RIGHT].setLocation(droneLoc.getX() + toRightNearCorner.getVx(), droneLoc.getY() + toRightNearCorner.getVy());
    verts[VERTEX_BOTTOM_LEFT].setLocation(droneLoc.getX() + toLeftNearCorner.getVx(), droneLoc.getY() + toLeftNearCorner.getVy());
    verts[VERTEX_TOP_RIGHT].setLocation(verts[VERTEX_BOTTOM_RIGHT].getX() + boxLenVector.getVx(), verts[VERTEX_BOTTOM_RIGHT].getY() + boxLenVector.getVy());
    verts[VERTEX_TOP_LEFT].setLocation(verts[VERTEX_BOTTOM_LEFT].getX() + boxLenVector.getVx(), verts[VERTEX_BOTTOM_LEFT].getY() + boxLenVector.getVy());
    verts[VERTEX_CENTRE].setLocation((verts[VERTEX_BOTTOM_RIGHT].getX() + verts[VERTEX_TOP_LEFT].getX()) / 2f,
                                     (verts[VERTEX_BOTTOM_RIGHT].getY() + verts[VERTEX_TOP_LEFT].getY()) / 2f);

    verticesUpdatedAtTick = tick;
  }

  public boolean isOverlappingAABB(DetectionBox otherBox) {
    return isOverlappingHorizontallyAABB(otherBox) 
        && isOverlappingVerticallyAABB(otherBox);
  }
  
  boolean isOverlappingHorizontallyAABB(DetectionBox otherBox) {
    // axis-aligned bounding box overlaps horizontally
    return !(getMinX() > otherBox.getMaxX() || getMaxX() < otherBox.getMinX());
  }
  
  boolean isOverlappingVerticallyAABB(DetectionBox otherBox) {
    // axis-aligned bounding box overlaps vertically
    return !(getMinY() > otherBox.getMaxY() || getMaxY() < otherBox.getMinY());
  }
  
  float getMinX() {
    float min = Float.MAX_VALUE;
    for (int i = 0; i < verts.length - 1; i++) {
      if (verts[i].getX() < min) {
        min = (float)verts[i].getX();
      }
    }
    return min;
  }
  
  float getMaxX() {
    float max = Float.MIN_VALUE;
    for (int i = 0; i < verts.length - 1; i++) {
      if (verts[i].getX() > max) {
        max = (float)verts[i].getX();
      }
    }
    return max;
  }
  
  float getMinY() {
    float min = Float.MAX_VALUE;
    for (int i = 0; i < verts.length - 1; i++) {
      if (verts[i].getY() < min) {
        min = (float)verts[i].getY();
      }
    }
    return min;
  }
  
  float getMaxY() {
    float max = Float.MIN_VALUE;
    for (int i = 0; i < verts.length - 1; i++) {
      if (verts[i].getY() > max) {
        max = (float)verts[i].getY();
      }
    }
    return max;
  }
  
  Vector2D getRightNormal() {
    return toRightNearCorner;
  }
  
  Location2D[] getBoxVertices() {
    return verts;
  }

  boolean isOverlapping(DetectionBox otherBox) {
    // try each poly against the other to find a separating axis, there is one
    // it doesn't overlap
    
//    println("testing for separating axis from rect 1 to rect 2");
    if (hasSeparatingAxis(this, otherBox)) {
//      println("\tfound separating axis");
      return false;
    }
    
//    println("testing for separating axis from rect 2 to rect 1");
    if (hasSeparatingAxis(otherBox, this)) {
//      println("\tfound separating axis");
      return false;
    }
    
    return true;
  }
  
  boolean hasSeparatingAxis(DetectionBox src, DetectionBox dest) {
    
    int prev = src.verts.length -2; // last is centre, which isn't used
    for (int i = 0; i < src.verts.length -1; i++) {
//      println("\ttesting edge = "+i);
      // VERTEX_TOP_LEFT = 0, VERTEX_TOP_RIGHT = 1, VERTEX_BOTTOM_RIGHT = 2, VERTEX_BOTTOM_LEFT = 3
      // from top left at 0, each edge is made from the previous point
      // 0 with 3, 1 with 0, 2 with 1, 3 with 2, therefore the vector to use is the one behind the point
      // ie. anti-clockwise to it, even though the points run clockwise around the box
      Vector2D edge = i % 2 == 0 ? toRightNearCorner : boxLenVector;
      // the normal of the edge could be a separating axis, it is the opposite vector to the edge
      Vector2D normal = i % 2 == 0 ? boxLenVector : toRightNearCorner;
      // project onto this normal from each rect to get the min/max 
      // onto itself - this can be stored for each movement rather than every compare 
      // eg. where in a single tick a poly is compared against many other polys
      // it changes by position though, so 
      float[] srcExtents = getProjectionExtents(src, normal);
//      println("\t\tsrcExtents min/max = "+srcRectExtents[0]+"/"+srcRectExtents[1]);
      // from the src edge to the dest, this has to be recalced each time
      float[] destExtents = getProjectionExtents(dest, normal);
      // min is 0, max is 1
      if (srcExtents[1] < destExtents[0]
      || destExtents[1] < srcExtents[0]) {
        return true;
      }
      prev = i;
    }
    
    return false;
  }
  
  float[] getProjectionExtents(DetectionBox box, Vector2D normal) {
  
    // Initialize extents to a single point, the first vertex
    float outMin, outMax;
    outMin = outMax = getDotProductFromVectorAndCoords(normal, (float)box.verts[0].getX(), (float)box.verts[0].getY());
   
    // scan all the rest, growing extents to include them
    for (int i = 1; i < box.verts.length -1; i++) {
      float d = getDotProductFromVectorAndCoords(normal, (float)box.verts[i].getX(), (float)box.verts[i].getY());
      if (d < outMin) {
        outMin = d;
      }
      else if (d > outMax) {
        outMax = d;
      }
    }
    
    return new float[] { outMin, outMax };
  }
  
  public void setCollided() {
    collisionTick = tick;
  }

  public void drawNotTransformed(Drone drone) {
/*    
    pushStyle();
    stroke(DRONE_ALERT);
    for (Location2D vert : verts) {
      ellipse((float)vert.getX(), (float)vert.getY(), 5, 5);
    }
    
    // draw the AABB
    line(getMinX(), getMinY(), getMaxX(), getMinY());
    line(getMinX(), getMinY(), getMinX(), getMaxY());
    line(getMinX(), getMaxY(), getMaxX(), getMaxY());
    line(getMaxX(), getMinY(), getMaxX(), getMaxY());
    
    popStyle();
*/
  }

  public void draw(Drone drone) {
    if (planets.length == 0 && droneSeparation == 0) {
      return;
    }
    
    pushStyle();
    stroke(isTouchingRight || isTouchingLeft || collisionTick > tick - 5 ? DRONE_ALERT : DRONE_STEERING);
    LineVector2D lv = drone.getLineVector();
    Location2D loc = lv.getLocation();
    line(0, 0, (float)toLeftNearCorner.getVx(), (float)toLeftNearCorner.getVy()); 
    line(0, 0, (float)toRightNearCorner.getVx(), (float)toRightNearCorner.getVy()); 
    line((float)toRightNearCorner.getVx(), (float)toRightNearCorner.getVy(), 
        (float)(toRightNearCorner.getVx() + boxLenVector.getVx()), (float)(toRightNearCorner.getVy() + boxLenVector.getVy()));
    line((float)toLeftNearCorner.getVx(), (float)toLeftNearCorner.getVy(), 
        (float)(toLeftNearCorner.getVx() + boxLenVector.getVx()), (float)(toLeftNearCorner.getVy() + boxLenVector.getVy()));
    line((float)(toRightNearCorner.getVx() + boxLenVector.getVx()), (float)(toRightNearCorner.getVy() + boxLenVector.getVy()), 
        (float)(toLeftNearCorner.getVx() + boxLenVector.getVx()), (float)(toLeftNearCorner.getVy() + boxLenVector.getVy()));
    popStyle();
  }
}

public class WallFeeler extends Sensor {
  // 3 lines from the centre of the Drone
  private Vector2D frontProng = new Vector2D(0, 0), rightProng = new Vector2D(0, 0), leftProng = new Vector2D(0, 0);
  private boolean touchFront, touchRight, touchLeft;
  
  public void directionChanged(Drone drone) {
    LineVector2D lv = drone.getLineVector();
    if (lv == null) {
      throw new IllegalStateException("drone has no line vector, should by now!");
    }
    // get the point offset dist along drone's current vector and that's where the
    // steering circle's origin should be
    frontProng.changeDirection(lv.getVx(), lv.getVy());
    frontProng.setLength(DRONE_H);
    
    // make the right prong the average of the right normal and the front, left prong vice versa
    rightProng.changeDirection((-frontProng.getVy() + frontProng.getVx()) / 2, (frontProng.getVx() + frontProng.getVy()) / 2);
    leftProng.changeDirection((frontProng.getVy() + frontProng.getVx()) / 2, (-frontProng.getVx() + frontProng.getVy()) / 2);
  }

  public boolean isTouchingWallAtFront(Drone drone, LineVector2D line) {
    touchFront = linesCross(drone.getLineVector().getLocation(), frontProng, line) && wallFeelerBounceFront;
    return touchFront;
  }
  
  public boolean isTouchingWallAtRight(Drone drone, LineVector2D line) {
    touchRight = linesCross(drone.getLineVector().getLocation(), rightProng, line);
    return touchRight;
  }
  
  public boolean isTouchingWallAtLeft(Drone drone, LineVector2D line) {
    touchLeft = linesCross(drone.getLineVector().getLocation(), leftProng, line);
    return touchLeft;
  }
  
  public boolean isTouchingWall(Drone drone, LineVector2D line) {
    // simplest to just test if the lines cross, rather than do any complex maths (which involve obj creations)
    // note, if call this method >1 time per drone/touching/update()
    touchFront = touchRight = touchLeft = false;
    return (
        isTouchingWallAtFront(drone, line)
        || isTouchingWallAtRight(drone, line)
        || isTouchingWallAtLeft(drone, line)
            );
  }
  
  private boolean linesCross(Location2D origin, Vector2D oriV, LineVector2D line) {
    // for now, only use this for wall lines (ie. where line is horizontal or vertical)
    if (line.getVx() != 0 && line.getVy() != 0) {
      throw new IllegalArgumentException("pronged sensor, linesCross only works for walls at the moment : "+line);
    } 
    
    return (isHorizontalWall(line) && isBetween(line.getLocation().getY(), origin.getY(), origin.getY() + oriV.getVy())) // horizontal
        || (!isHorizontalWall(line) && isBetween(line.getLocation().getX(), origin.getX(), origin.getX() + oriV.getVx())) // vertical
      ; 
  }
  
  public boolean isHorizontalWall(LineVector2D line) {
    return line.getVx() != 0;
  }
  
  private boolean isBetween(double a, double b, double c) {
    return (a >= b && a <= c) || (a >= c && a <= b);
  }
  
  public void draw(Drone drone) {
    if (!hasWalls) {
      return;
    }
    
    pushStyle();
    // messy debug lines, but going for minimum changes needed during run
    stroke(DRONE_STEERING);
    if (touchFront) {
      stroke(DRONE_ALERT);
    }
    line(0, 0, (float)frontProng.getVx(), (float)frontProng.getVy());
    if (touchRight) {
      stroke(DRONE_ALERT);
    }
    else if (touchFront) {
      stroke(DRONE_STEERING);
    }    
    line(0, 0, (float)rightProng.getVx(), (float)rightProng.getVy());
    if (touchLeft) {
      stroke(DRONE_ALERT);
    }
    else if (touchFront || touchRight) {
      stroke(DRONE_STEERING);
    }    
    line(0, 0, (float)leftProng.getVx(), (float)leftProng.getVy());
    popStyle();
  }
}


