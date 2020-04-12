public abstract class DroneSteeringGoal {

  protected Vector2D thisTickGoalV = new Vector2D(0, 0);
  protected int thisTickInsistence;

  public Vector2D getVectorThisTick() {
    return thisTickGoalV;
  }
  
  public int getInsistenceThisTick() {
    return thisTickInsistence;
  }
  
  public void steerByDegrees(LineVector2D lv, float deg) {
      steerByDegrees(lv, deg, thisTickGoalV, droneSpeed);
  }
  
  public void steerByDegrees(LineVector2D lv, float deg, Vector2D destV, float velocity) {
/* previous way     
      // what's the current angle in radians
      float a = atan2((float)lv.getVy(), (float)lv.getVx());
      
      float r = droneRandomGoalRadius;
      float rx = r * cos(a + radians(deg));
      float ry = r * sin(a + radians(deg));
*/      
      float rads = radians(deg);
      float rx = (float)lv.getVx() * cos(rads) - (float)lv.getVy() * sin(rads);
      float ry = (float)lv.getVx() * sin(rads) + (float)lv.getVy() * cos(rads);
    
      destV.changeDirection(rx, ry);
      destV.setLength(velocity);
  }
  
  public abstract void draw(Drone drone);
  public void drawNotTransformed(Drone drone) {}
  /**
   * sets the insistence this tick, if > 0 also the vector for this goal
   */
  public abstract void proposeDirectionChange(Drone drone, Sensor sensor);
  /**
   * override to get an appropriate sensor for the goal, which could be dynamic set (eg. wall feeler is basic, another sensor could be plugged-in instead)
   */
  public Class getSensorType() {
    return null;
  }
}

/**
 * Goal creates a random place on a circle to steer towards, the size of circle and distance
 * from origin determine the randomness of the direction
 */
public class RandomSteering extends DroneSteeringGoal {
  
  private float d, rx, ry, offset, x, y;
  private Vector2D offsetV = new Vector2D(0,0);
  
  public void draw(Drone drone) {
    stroke(DRONE_STEERING);
    ellipse(x, y, d, d);
    stroke(DRONE_HEADING);
    ellipse(rx, ry, 3, 3);
    line(rx, ry, 0, 0);  
  }

  public void proposeDirectionChange(Drone drone, Sensor sensor) {
    
    // only returns an insistence > 0 once every random move delay
    if (tick % RANDOM_DRONE_CHANGE_DELAY != 0) {
      thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;
      return;
    }
    
    float r = droneRandomGoalRadius;
    d = r * 2;
    float deg = new Random().nextInt(360);  
    offset = droneRandomGoalOffset;
    LineVector2D lv = drone.getLineVector();
    if (lv == null) {
      throw new IllegalStateException("drone has no line vector, should by now!");
    }
    // get the point offset dist along drone's current vector and that's where the
    // steering circle's origin should be
    offsetV.changeDirection(lv.getVx(), lv.getVy());
    offsetV.setLength(offset);
    x = (float)offsetV.getVx();
    y = (float)offsetV.getVy();
    rx = x + (r * cos(radians(deg)));
    ry = y + (r * sin(radians(deg)));
    
    thisTickGoalV.changeDirection(rx, ry);
    thisTickGoalV.setLength(droneSpeed);
    thisTickInsistence = DRONE_GOAL_INSISTENCE_RANDOM_DIRECTION;
  }
}

/**
 * Goal tries to adjust steering away from the lines of walls
 */
public class AvoidWalls extends DroneSteeringGoal {
  
  public Class getSensorType() {
    return WallFeeler.class;
  }

  public void draw(Drone drone) {
    if (thisTickInsistence != DRONE_GOAL_INSISTENCE_NONE) {
      stroke(DRONE_HEADING);
      line((float)thisTickGoalV.getVx(), (float)thisTickGoalV.getVy(), 0, 0); // translated to drone centre already 
    }
  }

  public void proposeDirectionChange(Drone drone, Sensor s) {
    
    WallFeeler sensor = (WallFeeler) s;

    thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;
    
    // broad phase, quick out 
    if (!inWallRangeQuad(drone)) { // also checks if the world has walls at the moment at all
      return;
    }

    // could be touching somewhere... test each wall, broad phase first, meaning quads per wall
    if ((drone.getQuadRow() == 0 && changedDirectionToAvoidWall(drone, sensor, wallLines[WALL_T_IDX])) 
    || (drone.getQuadRow() >= numQuadsY -1 && changedDirectionToAvoidWall(drone, sensor, wallLines[WALL_B_IDX])) 
    || (drone.getQuadCol() == 0 && changedDirectionToAvoidWall(drone, sensor, wallLines[WALL_L_IDX])) 
    || (drone.getQuadCol() >= numQuadsX -1 && changedDirectionToAvoidWall(drone, sensor, wallLines[WALL_R_IDX]))) {

      thisTickInsistence = DRONE_GOAL_INSISTENCE_AVOID_WALLS;
    }  
  }
  
  private boolean changedDirectionToAvoidWall(Drone drone, WallFeeler sensor, LineVector2D wall) {
    
    if (sensor.isTouchingWall(drone, wall)) {
    
      if (sensor.isTouchingWallAtFront(drone, wall)) {
        // for simplicity just bounce off the wall given
        if (sensor.isHorizontalWall(wall)) {
          thisTickGoalV.changeDirection(drone.getLineVector().getVx(), drone.getLineVector().getVy() * -1);
        }
        else {
          thisTickGoalV.changeDirection(drone.getLineVector().getVx() * -1, drone.getLineVector().getVy());
        }
      }
      else {
        // move 30 degrees away from whichever prong touched
        float deg = sensor.isTouchingWallAtRight(drone, wall) ? -30 : 30;
    
        LineVector2D lv = drone.getLineVector();
        if (lv == null) {
          throw new IllegalStateException("AvoidWalls.adjustSteering: drone has no line vector, should by now!");
        }
    
        steerByDegrees(lv, deg);
      }
    
      return true;
    }
    else {
      return false;
    }
  }
  
  private boolean inWallRangeQuad(Drone drone) {
    // walls is the easier case
    if (hasWalls) {
      // ignore it if it's not in a wall quad
      if (drone.getQuadRow() == 0 || drone.getQuadCol() == 0 || drone.getQuadRow() >= numQuadsY -1 || drone.getQuadCol() >= numQuadsX -1) {

        if (drawSteering) {
//          updateWallDebug(drone);
        }
      
        return true;
      } 
      else if (drawSteering) {
        drone.debugObstacles = "";
      }
    }
  
    return false;
  }

/*
  // crappy method that should go into Drone probably
  private void updateWallDebug(Drone drone) {
    // big if... but easier this way
    if (drone.getQuadRow() == 0 && drone.getQuadCol() == 0) {
      drone.debugObstacles = " w[t,l]";
    }
    else if (drone.getQuadRow() == 0 && drone.getQuadCol() >= numQuadsW) {
      drone.debugObstacles = " w[t,r]";
    }
    else if (drone.getQuadRow() >= numQuadsH && drone.getQuadCol() >= numQuadsW) {
      drone.debugObstacles = " w[b,r]";
    }
    else if (drone.getQuadRow() >= numQuadsH && drone.getQuadCol() == 0) {
      drone.debugObstacles = " w[b,l]";
    }
    else if (drone.getQuadRow() == 0) {
      drone.debugObstacles = " w[t]";
    }
    else if (drone.getQuadRow() >= numQuadsH) {
      drone.debugObstacles = " w[b]";
    }
    else if (drone.getQuadCol() == 0) {
      drone.debugObstacles = " w[l]";
    }
    else if (drone.getQuadCol() >= numQuadsW) {
      drone.debugObstacles = " w[r]";
    }
  }
*/
}

/**
 * Goal tries to adjust steering away from the planets using the detection box
 */
public class AvoidPlanets extends DroneSteeringGoal {
  
  public Class getSensorType() {
    return DetectionBox.class;
  }

  public void draw(Drone drone) {
    if (thisTickInsistence != DRONE_GOAL_INSISTENCE_NONE) {
      stroke(DRONE_HEADING);
      line((float)thisTickGoalV.getVx(), (float)thisTickGoalV.getVy(), 0, 0); // translated to drone centre already 
    }
  }

  public void proposeDirectionChange(Drone drone, Sensor s) {
    
    thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;
    
    if (changedDirectionToAvoidPlanet(drone, (DetectionBox) s)) {
      thisTickInsistence = DRONE_GOAL_INSISTENCE_AVOID_OBSTACLES;
    }
  }
  
  private boolean changedDirectionToAvoidPlanet(Drone drone, DetectionBox sensor) {

    sensor.resetFlags();
    
    for (Planet planet : planets) {

      // ignore it if it's not in a quad that's near enough to a planet to be touching (broad phase)
      
      if (sensor.droneOutOfPlanetQuadRange(drone, planet, quadsToPlanets)) {
        continue;
      }

      // ignore not if the drone is already moving away from the planet, (negative dot)
      LineVector2D lv = drone.getLineVector();
      if (getDotProductFromVectorAndLocation(lv, (float)lv.getLocation().getX(), (float)lv.getLocation().getY(), (float)planet.getOrigin().getX(), (float)planet.getOrigin().getY()) < 0) {
        continue;
      }
      
      // narrow phase, look for either right or left side or the detection box is inside the planet's radius
      
      // update the vertices for the sensor's corners
      sensor.updateVertices(drone);
      
      if (sensor.detectTouchingRightSide(drone, planet)) {
        // move 30 degrees away 
        steerByDegrees(lv, -30);
        return true;
      }
      else if (sensor.detectTouchingLeftSide(drone, planet)) {
        // move 30 degrees away 
        steerByDegrees(lv, 30);
        return true;
      }
    }
  
    return false;
  }
}

/**
 * Goal using global settings to change direction according to the drones around.
 * Uses the detection box sensor for collision/personal space (separation)
 * There are 3 parts to this goal that act together relative to the strength of the global settings
 * (user sliders):
 * - Separation (personal space)
 * - Cohesion (pull to centre of the group)
 * - Alignment (same direction as group)
 * An additional slider determines the size of the group (in quads)
 * To make things clear when showing all the steering:
 * - detection box is red when a collision is detected
 *   and a red line shows the vector for avoidance
 * - a yellow line shows the pull to the centre of the group
 * - an orange line shows the alignment to the group average heading
 */
public class DroneGrouping extends DroneSteeringGoal {
  
  private int collisionTick = Integer.MIN_VALUE;
  private Vector2D avoidCollisionV = new Vector2D(0, 0), groupAlignV = new Vector2D(0, 0), groupCohesionV = new Vector2D(0, 0), 
          groupCohesionDebugV = new Vector2D(0, 0);
  
  public Class getSensorType() {
    return DetectionBox.class;
  }

  public void draw(Drone drone) {
    if (thisTickInsistence != DRONE_GOAL_INSISTENCE_NONE) {
      stroke(DRONE_HEADING);
      line((float)thisTickGoalV.getVx(), (float)thisTickGoalV.getVy(), 0, 0); // translated to drone centre already 
      
      if (droneAlignment != 0) {
        stroke(DRONE_ALIGNMENT);
        line((float)groupAlignV.getVx(), (float)groupAlignV.getVy(), 0, 0);
      }

      if (droneCohesion != 0) {
        stroke(DRONE_COHESION);
        line((float)groupCohesionV.getVx(), (float)groupCohesionV.getVy(), 0, 0);
      }
    }
    
    if (collisionTick > tick - 60) { // show the vector for one second or 
      stroke(DRONE_ALERT);
      line((float)avoidCollisionV.getVx(), (float)avoidCollisionV.getVy(), 0, 0);
    }
  }

  public void proposeDirectionChange(Drone drone, Sensor s) {
    
    // test for the case where the drone has already sensed the first part of the grouping behaviours (separation)
    // this is for when a previous drone's check produced a collision with this one, and so this one already
    // recorded the collision
    if (collisionTick == tick) {
      thisTickInsistence = DRONE_GOAL_INSISTENCE_GROUP_BEHAVIOURS;
    }
    else {
      thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;
          
      if (droneSeparation != 0// global setting from slider
        && changedDirectionToMaintainSpace(drone, (DetectionBox) s)) {
        thisTickInsistence = DRONE_GOAL_INSISTENCE_GROUP_BEHAVIOURS;
      }
    }
    
    // non-zero for alignment and cohesion means anything from weak force to max
    // is 1-10 and defines how strong the pull is relative to other
    // group behaviours
    if ((droneAlignment != 0 || droneCohesion != 0) && isGroupAlignmentAndCohesionTick()) {
      calcGroupAlignmentAndCohesion(drone);
      thisTickInsistence = DRONE_GOAL_INSISTENCE_GROUP_BEHAVIOURS;
    }
    
    
    // make a weighted average from the grouping behaviours 
    if (thisTickInsistence == DRONE_GOAL_INSISTENCE_GROUP_BEHAVIOURS) {
      calcWeightedAverage();
    }
    
  }
  
  private boolean isGroupAlignmentAndCohesionTick() {
    return tick % GROUP_DRONE_CHANGE_DELAY == 0;
  }
  
  private void calcWeightedAverage() {

    int sumInsistence = 0;
    thisTickGoalV.changeDirection(0, 0);
    
    if (collisionTick == tick) {
      sumInsistence += DRONE_MAX_GROUP_SETTINGS; // collision always at max
      thisTickGoalV.changeDirection(
          avoidCollisionV.getVx() * DRONE_MAX_GROUP_SETTINGS + thisTickGoalV.getVx(), 
          avoidCollisionV.getVy() * DRONE_MAX_GROUP_SETTINGS + thisTickGoalV.getVy());
    }

    // only once every so often are the align/cohesion goals included (otherwise they dominate too much)
    if (isGroupAlignmentAndCohesionTick()) {
      if (droneAlignment != 0) {
        sumInsistence += droneAlignment; // alignment on slider
        thisTickGoalV.changeDirection(
            groupAlignV.getVx() * droneAlignment + thisTickGoalV.getVx(), 
            groupAlignV.getVy() * droneAlignment + thisTickGoalV.getVy());
      }
  
      if (droneCohesion != 0) {
        sumInsistence += droneCohesion; // on slider      
        thisTickGoalV.changeDirection(
            groupCohesionV.getVx() * droneCohesion + thisTickGoalV.getVx(), 
            groupCohesionV.getVy() * droneCohesion + thisTickGoalV.getVy());
      }
    }

    // average to the weights
    thisTickGoalV.changeDirection(
        thisTickGoalV.getVx() / sumInsistence, 
        thisTickGoalV.getVy() / sumInsistence);
    thisTickGoalV.setLength(droneSpeed);
  }
  
  private void calcGroupAlignmentAndCohesion(Drone drone) {
    // just uses a range of quads around the drone's quad to find the group's alignment to a heading
    // and pull to centre of the group by making an average
    
    int count = 0;
    float totalVx = 0f, totalVy = 0f, totalX = 0f, totalY = 0f;
    
    for (int i = 0; i < drones.length; i++) {
  
      if (!drones[i].isAlive()) {
        continue;
      }

      // don't exclude the current drone from the average
      // ignore it if it's not in a quad that applies (broad phase)
      if (drone.getQuadRow() < drones[i].getQuadRow() - DRONE_GROUP_RANGE_QUADS
      || drone.getQuadRow() > drones[i].getQuadRow() + DRONE_GROUP_RANGE_QUADS
      || drone.getQuadCol() < drones[i].getQuadCol() - DRONE_GROUP_RANGE_QUADS
      || drone.getQuadCol() > drones[i].getQuadCol() + DRONE_GROUP_RANGE_QUADS) {
        continue;
      }
      
      count++;
      LineVector2D lv = drones[i].getLineVector();
      totalVx += lv.getVx();
      totalVy += lv.getVy();
      totalX += lv.getLocation().getX();
      totalY += lv.getLocation().getY();
    }

    groupAlignV.changeDirection(totalVx / count, totalVy / count);
    groupAlignV.setLength(DRONE_GROUP_VECTORS_LEN);
    
    // cohesion is slightly different, it's a point, so make the vector to get there
    if (droneCohesion != 0) {
      Location2D loc = drone.getLineVector().getLocation();
      groupCohesionV.changeDirection(totalX / count - loc.getX(), totalY / count - loc.getY());
      
      // problem here is that cohesion is an irregular length vector to the middle of the group
      // use the debug vector to show that, but the real cohesion vector is the same length
      // as the other group vectors
      groupCohesionDebugV.changeDirection(groupCohesionV.getVx(), groupCohesionV.getVy());
      groupCohesionV.setLength(DRONE_GROUP_VECTORS_LEN);
    }
  }
  
  private boolean changedDirectionToMaintainSpace(Drone drone, DetectionBox sensor) {

    sensor.updateVertices(drone);
    
    // test against other drones (only lower down in the array)

    for (int i = drone.getId() + 1; i < drones.length; i++) {

      // ignore it if it's not in a quad that applies (broad phase)
      if (drone.getQuadRow() < drones[i].getQuadRow() - quadsBetweenDrones
      || drone.getQuadRow() > drones[i].getQuadRow() + quadsBetweenDrones
      || drone.getQuadCol() < drones[i].getQuadCol() - quadsBetweenDrones
      || drone.getQuadCol() > drones[i].getQuadCol() + quadsBetweenDrones) {
        continue;
      }
      
      //------------------- narrow phase, look for ways to eliminate the check for this drone
      
      // make sure the corner vertices for the drone's detection box sensor are up to date (only does it once per tick)
      sensor.updateVertices(drones[i]);

      // axis-aligned bounding boxes must coincide or there's no collision
      if (!sensor.isOverlappingAABB(drones[i].getDetectionBoxSensor())) {
        continue;
      }

      // ignore drones that are moving away from each other (ie. the other is behind and also travelling in the other direction)
      // use 2 dot products 
      // test current drone's direction and the vector from its centre compared to the other one's *detection box* centre 
      LineVector2D lv = drone.getLineVector();
      float dpFrontOrBack = getDotProductFromVectorAndLocation(lv, lv.getLocation(), drones[i].getDetectionBoxSensor().getBoxVertices()[VERTEX_CENTRE]);
      boolean isBehind = dpFrontOrBack < 0;

      if (isBehind) {
       
        // if collider is behind, get another dot to see if it's going away from this one, if so can ignore it
        float dpDir = (float)lv.getDotProduct(drones[i].getLineVector());
        if (dpDir < dpDirForwardsThreshold) {
          // the centre of the other drone is behind this one, and also its trajectory is likely to miss it
          // so no avoidance needed
          continue;
        }
      }
      
      // make sure there is really a collision
      if (!sensor.isOverlapping(drones[i].getDetectionBoxSensor())) {
          continue;
      }

      boolean isAvoiding = calcAvoidanceVector(isBehind, drone, drones[i]);
      if (isAvoiding) {
        collisionTick = tick;
        sensor.setCollided();
      }
      
      // also let the other drone react to the collision
      drones[i].reactToCollision(drone);
      
      return isAvoiding;      
    }
  
    return false;
  }
  
  private boolean calcAvoidanceVector(boolean isColliderBehind, Drone drone, Drone collider) {
    // assume the mover that is planning a trajectory is drone and is trying to avoid collider  
    // already compared drone's origin to collider's detection box centre to determine if it's behind
    // NOTE : if this is done every move, there could be > 1 steerings per collision... meaning either make them
    // less severe or don't check every move
    
    DetectionBox box = drone.getDetectionBoxSensor();
    DetectionBox colliderBox = collider.getDetectionBoxSensor();
    LineVector2D dir = drone.getLineVector();
    LineVector2D colliderDir = collider.getLineVector();
    Vector2D vNormalOfDir = box.getRightNormal();

    // use a variety of dots to figure out booleans for final calc    
    float dpDir = (float)dir.getDotProduct(colliderDir);
    boolean isColliderSameDir = dpDir > dpDirForwardsThreshold; // either to right or left but roughly same dir
    float dpRightOrLeft = getDotProductFromVectorAndLocation(vNormalOfDir, dir.getLocation(), colliderBox.getBoxVertices()[VERTEX_CENTRE]);
    boolean isColliderRight = dpRightOrLeft > 0;
    float dpOriginRightOrLeft = getDotProductFromVectorAndLocation(vNormalOfDir, dir.getLocation(), colliderDir.getLocation()); 

    float dpTravellingRightOrLeft = (float)vNormalOfDir.getDotProduct(colliderDir);
    
//    println("vec to avoid from rect1 to rect2 = "+vOrgnToCentre+" dpPlace1="+dpFrontOrBack+" dpDir="+dpDir);
    boolean isColliderTravelingLeft = isColliderBehind ? dpTravellingRightOrLeft > 0 : dpTravellingRightOrLeft < 0;
    boolean willCrossInFront = !isColliderSameDir && 
              // either (centre) is on the right and travelling left, or on the left and travelling right            
              ((isColliderRight && isColliderTravelingLeft) || (!isColliderRight && !isColliderTravelingLeft)
              // or the collider has origin one side and centre the other (ie. directly crossing the dir)
              || (dpRightOrLeft > 0 && dpOriginRightOrLeft < 0) || (dpRightOrLeft < 0 && dpOriginRightOrLeft > 0)  
              );
              

    int collisionAvoidance = COLLISION_AVOID_NONE;
  
    if (!isColliderBehind) {
      if (willCrossInFront) {
        collisionAvoidance = isColliderRight ? COLLISION_AVOID_STEER_HARD_LEFT : COLLISION_AVOID_STEER_HARD_RIGHT;
      }
  //    else if (isColliderSameDir) {
      else { 
        collisionAvoidance = isColliderRight ? COLLISION_AVOID_STEER_GENTLE_LEFT : COLLISION_AVOID_STEER_GENTLE_RIGHT;
      }
    }
    
    String collisionAvoidDebug = "";
    switch (collisionAvoidance) {
    case COLLISION_AVOID_STEER_GENTLE_RIGHT : 
        collisionAvoidDebug = "Steer gently right"; 
        steerByDegrees(dir, 20, avoidCollisionV, DRONE_GROUP_VECTORS_LEN);
        break;
    case COLLISION_AVOID_STEER_GENTLE_LEFT : 
        collisionAvoidDebug = "Steer gently left"; 
        steerByDegrees(dir, -20, avoidCollisionV, DRONE_GROUP_VECTORS_LEN);
        break;
    case COLLISION_AVOID_STEER_HARD_RIGHT : 
        collisionAvoidDebug = "Steer hard right"; 
        steerByDegrees(dir, 45, avoidCollisionV, DRONE_GROUP_VECTORS_LEN);
        break;
    case COLLISION_AVOID_STEER_HARD_LEFT : 
        collisionAvoidDebug = "Steer hard_left"; 
        steerByDegrees(dir, -45, avoidCollisionV, DRONE_GROUP_VECTORS_LEN);
        break;
    default : return false; // no avoidance
    }
/*    
    // only for debugging message              
    boolean isColliderOrthoDir = !isColliderSameDir && dpDir > dpDirForwardsThreshold;
              
    println("Collision strategy: "+collisionAvoidDebug+" (collider(#"+collider.getId()+" centre is "
        +(isColliderBehind ? "BEHIND" : "IN FRONT")
        +(isColliderRight ? " ON RIGHT SIDE" : " ON LEFT SIDE")
        +(isColliderSameDir ? " GOING FORWARDS" : isColliderOrthoDir ? " GOING SIDEWAYS" :  " GOING BACKWARDS")
        +(isColliderTravelingLeft ? " RIGHT->LEFT" : " LEFT->RIGHT")
        +(willCrossInFront ? " HEADING RIGHT AT US" : "")
        +(willCrossInFront && isColliderOrthoDir ? " POSSIBLE T-BONE" : " NOT A T-BONE")
        +" of #"+drone.getId()+" origin)");
*/     
    return true;
  }

  /**
   * Called from the method of the same name in Drone when another drone
   * has already found a collision with this one
   */
  public void reactToCollision(Drone drone, Drone otherDrone, DetectionBox sensor) {
      
      // figure out the vector similarly to the above 
      // for now not attempting to synthesize multiple collisions with different drones in a single tick
      // although it could be a next logical step
      LineVector2D lv = drone.getLineVector();
      float dpFrontOrBack = getDotProductFromVectorAndLocation(lv, lv.getLocation(), otherDrone.getDetectionBoxSensor().getBoxVertices()[VERTEX_CENTRE]);
      boolean isBehind = dpFrontOrBack < 0;
      if (calcAvoidanceVector(isBehind, drone, otherDrone)) {
        collisionTick = tick;
        sensor.setCollided();
      }
  }
}

/**
 * The attacker uses this goal to find its direction to the next waypoint
 */
public class SteerToWaypoint extends DroneSteeringGoal {
  
  public void draw(Drone drone) {}
  
  public void drawNotTransformed(Drone drone) {
    if (showAllEvasionDebugs) {
      stroke(DRONE_HEADING);
      Location2D wp = ((Attacker)drone).getWaypoint();
      line((float)wp.getX(), (float)wp.getY(), (float)drone.getLineVector().getLocation().getX(), (float)drone.getLineVector().getLocation().getY());
    }  
  }

  public void proposeDirectionChange(Drone drone, Sensor sensor) {
    
    Attacker attackDrone = (Attacker)drone;

    thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;
    
    // only returns an insistence > 0 if the state is not an attacking state and needs to turn
    if (attackDrone.getCurrentState() != ATTACKER_STATE_TURN && attackDrone.getCurrentState() != ATTACKER_STATE_GO_TO_WAYPOINT) {
      return;
    }

    LineVector2D lv = attackDrone.getLineVector();    
    Location2D loc = lv.getLocation();
    Location2D waypoint = attackDrone.getWaypoint();
    // use squares to avoid square root
    float distSq = getLenSquaredFromCoords((float)loc.getX(), (float)loc.getY(), (float)waypoint.getX(), (float)waypoint.getY());
    
    // has the attacker already reached the way point?
    if (attackDrone.getCurrentState() == ATTACKER_STATE_GO_TO_WAYPOINT && attackDrone.hasReachedWayPoint(distSq)) {
      waypoint = attackDrone.advanceWaypoint();
      attackDrone.setTurning();
//      distSq = getLenSquaredFromCoords((float)loc.getX(), (float)loc.getY(), (float)waypoint.getX(), (float)waypoint.getX());
//      println("attacker turn to next waypoint");
    }
    
//    float dpAttackerForwardsThreshold = distSq * .80f; // almost exactly forwards (for attacker towards a waypoint)
//    float dpToWaypoint = getDotProductFromVectorAndLocation(lv, loc, waypoint);
    boolean isFacingWp = attackDrone.getDetectionBoxSensor().isVectorFromLocFacingLoc2(lv, loc, waypoint, distSq, .9f);
//    println("dp="+dpToWaypoint+" forwardsThreshold="+dpAttackerForwardsThreshold+" forwards = "+(dpToWaypoint > dpAttackerForwardsThreshold));
    
    if (attackDrone.getCurrentState() == ATTACKER_STATE_TURN) {
      // is it now pointing at the waypoint?
      
      if (!isFacingWp) {
        // not finishing turning yet, need the normal to see which way to turn, the detection box has one
        Vector2D vNormalOfDir = attackDrone.getDetectionBoxSensor().getRightNormal();
        float dpRightOrLeft = getDotProductFromVectorAndLocation(vNormalOfDir, loc, waypoint);
        boolean isRight = dpRightOrLeft > 0;
        steerByDegrees(lv, isRight ? 5 : -5); // slow turn
        thisTickInsistence = ATTACKER_GOAL_INSISTENCE_GO_TO_WAYPOINT;
//        println("attacker turning not yet facing waypoint");
      }
      else {
        // facing way point, can stop turning 
        attackDrone.setToWaypoint();
//        println("attacker facing waypoint keeping going");
      }
    }
    
    else {
      // on the way to the next waypoint, check if there's a steering adjustment needed
      //TODO this code is same as above, can refactor if needs it turns out they can be that way
      if (!isFacingWp) {
        // not finishing turning yet, need the normal to see which way to turn, the detection box has one
        Vector2D vNormalOfDir = attackDrone.getDetectionBoxSensor().getRightNormal();
        float dpRightOrLeft = getDotProductFromVectorAndLocation(vNormalOfDir, loc, waypoint);
        boolean isRight = dpRightOrLeft > 0;
        steerByDegrees(lv, isRight ? 2 : -2); // slow turn
        thisTickInsistence = ATTACKER_GOAL_INSISTENCE_GO_TO_WAYPOINT;
//        println("attacker on way to waypoint but needed a bit of an adjustment");
      }
    }
  }
  
}

/**
 * The attacker uses this goal to pursue drones
 */
public class PursueDrones extends DroneSteeringGoal {
  
  private int pursueTick = Integer.MIN_VALUE, killTick = Integer.MIN_VALUE;
  
  public Class getSensorType() {
    return LineOfSight.class;
  }

  public void draw(Drone drone) {
    if (pursueTick > tick - 30 || killTick > tick - 30) { // show the vector for 1/2 sec
      stroke(killTick > tick - 30 ? DRONE_ALERT : DRONE_COHESION); // red if fired, else yellow
      strokeWeight(3);
      line((float)thisTickGoalV.getVx(), (float)thisTickGoalV.getVy(), 0, 0);
      strokeWeight(1);
    }
  }

  public void proposeDirectionChange(Drone drone, Sensor s) {
    
    Attacker attackDrone = (Attacker)drone;
    LineOfSight sensor = (LineOfSight) s;

    thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;

    Drone pursuableDrone = sensor.findNearestVisibleDroneInRange(attackDrone);
    
    if (pursuableDrone != null && killed(attackDrone, pursuableDrone)) {
      pursuableDrone = null;
    }
    
    if (pursuableDrone == null) {
      if (attackDrone.getCurrentState() == ATTACKER_STATE_GO_AFTER_DRONE) {
        attackDrone.setTurning();
      }
      // nothing to do
      return;
    }

    // got a drone in its sights
    
    thisTickInsistence = ATTACKER_GOAL_INSISTENCE_PURSUE_DRONE;
    pursueTick = tick;
    
    if (attackDrone.getCurrentState() != ATTACKER_STATE_GO_AFTER_DRONE) {
      attackDrone.setPursueDrone();
    }

    Location2D loc = attackDrone.getLineVector().getLocation();
    Location2D loc2 = pursuableDrone.getLineVector().getLocation();
    
    thisTickGoalV.changeDirection(loc2.getX() - loc.getX(), loc2.getY() - loc.getY());
  }
  
  private boolean killed(Attacker attackDrone, Drone drone) {
    // can only fire once a second
    if (killTick > tick - TICKS_BETWEEN_ATTACK_FIRING) {
      return false;
    }
    
    // is it in firing range?
    Location2D loc = attackDrone.getLineVector().getLocation();
    Location2D droneLoc = drone.getLineVector().getLocation();
    float distSq = getLenSquaredFromCoords((float)loc.getX(), (float)loc.getY(), (float)droneLoc.getX(), (float)droneLoc.getY());
    if (distSq > attackerFireRangeSq) {
      return false;
    }
    
    killTick = tick;
    attackDrone.fire();
    drone.kill();
    playExplosion(loc, droneLoc);
    return true;
  }
}

/**
 * The drone uses this to evade attacker(s)
 */
public class EvadeAttacker extends DroneSteeringGoal {
  
  private boolean onEvasivePath = false;
  private Quad[] bestPath;
  private Location2D[] possibleHideSpots;
  private Path[] candidatePaths;
  private int evadeTick = Integer.MIN_VALUE;
  
  public Class getSensorType() {
    return DetectAttacker.class;
  }

  public void draw(Drone drone) {}
  
  public void drawNotTransformed(Drone drone) {
    if (onEvasivePath) {
      Location2D loc = drone.getLineVector().getLocation();
      stroke(DRONE_ALERT);
      strokeWeight(5);
      float prevx = (float)loc.getX(), prevy = (float)loc.getY();
      for (int i = bestPath.length -1; i >= 0; i--) {
        Quad l = bestPath[i];
        float x = l.getCol() * QUAD_SIZE + QUAD_SIZE / 2;
        float y = l.getRow() * QUAD_SIZE + QUAD_SIZE / 2;
        line(prevx, prevy, x, y);
        prevx = x;
        prevy = y;
      }
      strokeWeight(1);

      if (showAllEvasionDebugs && evadeTick > tick - 30) { // show the lines for 1/2 sec
      
        if (lastTickQuadsDebugDrawn < tick) { // in case > 1 drone is drawing evasion this tick don't also draw the quads > 1
          lastTickQuadsDebugDrawn = tick;
          drawQuads();
        }
        if (possibleHideSpots != null) {
          stroke(DRONE_COHESION); // yellow
          for (Location2D spot : possibleHideSpots) {
            line((float)spot.getX(), (float)spot.getY(), (float)loc.getX(), (float)loc.getY());
          }
        }
        
        if (candidatePaths != null) {
          for (Path path : candidatePaths) {
            if (path != null) {
              drawCompletedPath(path);
            }
          }
        }
      }
    }
  }

  public void proposeDirectionChange(Drone drone, Sensor s) {

    thisTickInsistence = DRONE_GOAL_INSISTENCE_NONE;
    
    Attacker attackDrone = attacker;
    if (attackDrone == null) {
      return;
    }
    
    DetectAttacker sensor = (DetectAttacker) s;

    if (onEvasivePath) {
      // keep going until either out of range or got to destination or simply too long since the path was evaluated (ie. could be being pursued and need a new dest)
      if (evasionPathIsOld() || reachedFinalGoal(drone) || !sensor.inVisibleHideDistanceOfAttacker(drone, attackDrone)) {
        onEvasivePath = false; 
        possibleHideSpots = null;
        candidatePaths = null;  // essentially debug draw info
        // could still need to hide again, but do it next tick, for simplicity
        return;
      }
      else {
        setVectorFromEvasivePath(drone);
        thisTickInsistence = DRONE_GOAL_INSISTENCE_EVADE_ATTACKER; // could still need to hide again, but do it next tick, for simplicity
        return;
      }
    }
    
    // not previously on evasion, so check if need to be
    if (!sensor.inVisibleHideDistanceOfAttacker(drone, attackDrone)) {
      return;
    }

    // find a planet if there is one to hide behind, otherwise the furthest points from the attacker in 4 directions
    thisTickInsistence = DRONE_GOAL_INSISTENCE_EVADE_ATTACKER;
    evadeTick = tick;
    onEvasivePath = true;

    Location2D attackLoc = attackDrone.getLineVector().getLocation();
    
    // fills the hide spots array
    findHidePoints(attackLoc);
    if (possibleHideSpots.length == 0) { // should have found > 1 but in case none are found set the vector to the same as the attacker, so goes away from it
      println("drone "+drone+" has problem evading attacker, no hiding places found"); 
      thisTickGoalV.changeDirection(drone.getLineVector().getVx(), drone.getLineVector().getVy());
      return;
    }

    findCandidateEscapePaths(attackLoc, drone); 
    chooseEscapePath();

    if (!setVectorFromEvasivePath(drone)) { // the nearest hiding spot was so close as to be useless
      println("drone "+drone+" has problem evading attacker, nearest spot ineffective"); 
      thisTickGoalV.changeDirection(drone.getLineVector().getVx(), drone.getLineVector().getVy());
    }
  }
  
  private boolean evasionPathIsOld() {
    return evadeTick < tick - EVASION_PATH_RECALC_TICKS;
  }
  
  private void chooseEscapePath() {
    // find the path with the least G value 
    float leastG = Float.MAX_VALUE;
    Path chosenPath = null;
    for (Path path : candidatePaths) {
      if (path != null && path.getG() < leastG) {
        leastG = path.getG();
        chosenPath = path;
      }
    }
    
    // walk backwards through the path to fill the array so as each node is reached it can be removed
    bestPath = getPathReverseArray(chosenPath);
  }
  
  private void findCandidateEscapePaths(Location2D attackDroneLoc, Drone drone) {
    
    aStarController.setStart(quadMap[drone.getQuadCol()][drone.getQuadRow()]);
    aStarController.getFringeStrategy().setMaxG(Float.MAX_VALUE); // reset the max, will read them in order of nearness, so it should cut down the amount of work
    
    // skirt around the attacker's visible and pursue ranges
    if (lastTickAttackerRangeTranslatedToQuads < tick) {
      lastTickAttackerRangeTranslatedToQuads = tick;
      float attackX = (float)attackDroneLoc.getX(), attackY = (float)attackDroneLoc.getY();
      for (int qx = 0; qx < quadMap.length; qx++) {
        for (int qy = 0; qy < quadMap[qx].length; qy++) {
          
          float distSq = getLenSquaredFromCoords(attackX, attackY, qx * QUAD_SIZE + QUAD_SIZE / 2, qy * QUAD_SIZE + QUAD_SIZE / 2);
          quadMap[qx][qy].setHarshUnlessPlanet(distSq < droneHideRangeSq, distSq < attackerPursueRangeSq ? QUAD_HARSH_LEVEL_PURSUE_RANGE : QUAD_HARSH_LEVEL_VISIBLE_RANGE);
        }
      }
    }
    
    // create all the candidates for assessment
    candidatePaths = new Path[possibleHideSpots.length];
    for (int i = 0; i < candidatePaths.length; i++) {
//      println("goal = "+((int)(possibleHideSpots[i].getX() / QUAD_SIZE))+" / "+((int)(possibleHideSpots[i].getY() / QUAD_SIZE)));
      aStarController.setGoal(quadMap[(int)(possibleHideSpots[i].getX() / QUAD_SIZE)][(int)(possibleHideSpots[i].getY() / QUAD_SIZE)]);
      
      // the harsh terrain (the attacker pursue range) is preserved by not allowing the resetSearch() to reset the map quads
      aStarController.resetSearch(quadMap, false);
      
      try { // keep going till either reach goal or max G is exceeded
        aStarController.processAll();
        candidatePaths[i] = aStarController.getCompletedPath();
        aStarController.getFringeStrategy().setMaxG(candidatePaths[i].getG()); // if get this far, the value of G is the least so far
      }
      catch (MaxGException e) {
        //println("candidate path (i="+i+") aborted: "+e.getMessage());
      }
    }
  }
  
  private void findHidePoints(Location2D attackDroneLoc) {
    ArrayList<Location2D> goodHidingPlaces = new ArrayList<Location2D>();
    findPointsOppositePlanets(attackDroneLoc, goodHidingPlaces); // the 2 nearest planets
    findGoodCornerPoints(attackDroneLoc, goodHidingPlaces); // the 2 most distant corners from the attacker
    
    possibleHideSpots = new Location2D[goodHidingPlaces.size()];
    possibleHideSpots = goodHidingPlaces.toArray(possibleHideSpots);
  }
  
  private boolean setVectorFromEvasivePath(Drone drone) {
    if (bestPath == null || bestPath.length == 0) {
      throw new IllegalStateException("unable to set vector from best path as it has no values");
    }
    
    // test for already in the next quad
    if (drone.getQuadCol() == bestPath[bestPath.length -1].getCol() && drone.getQuadRow() == bestPath[bestPath.length -1].getRow()) {
      // pop the last one off 
      Quad[] shorterBestPath = new Quad[bestPath.length -1];
      System.arraycopy(bestPath, 0, shorterBestPath, 0, shorterBestPath.length);
      bestPath = shorterBestPath;

      // if left with none, can't set it
      if (bestPath.length == 0) {
        return false;
      }
    }
    
    // set the vector
    Location2D loc = drone.getLineVector().getLocation();
    thisTickGoalV.changeDirection((float)(bestPath[bestPath.length -1].getCol() * QUAD_SIZE + QUAD_SIZE / 2 - loc.getX()), 
                                  (float)(bestPath[bestPath.length -1].getRow() * QUAD_SIZE + QUAD_SIZE / 2 - loc.getY()));

    return true;
  }

  private boolean reachedFinalGoal(Drone drone) {
    if (bestPath == null || bestPath.length == 0) { // probably can happen when the first spot was so close it got popped off right away, next loop in fails here
      println("unable to evaluate reached goal from best path as it has no values");
      return true;
    }
    
    if (bestPath.length > 1) {
      return false;
    }
    
    // test for already in the next quad
    return (drone.getQuadCol() == bestPath[0].getCol() && drone.getQuadRow() == bestPath[0].getRow());
  }
  
  private void findPointsOppositePlanets(Location2D attackDroneLoc, ArrayList<Location2D> points) {
    if (planets == null) {
      return;
    }

    // find up to 2 nearest planets    
    Location2D nearest = null, nextNearest = null;
    float nearestDist = Float.MAX_VALUE, nextNearestDist = Float.MAX_VALUE;      

    for (Planet pl : planets) {
      // find the point that is one quad distance on the other side of the planet from the attacker
      Vector2D v = new Vector2D(attackDroneLoc, pl.getOrigin());
      float len = (float)v.getLength() + pl.getRadius() + QUAD_SIZE;
      v.setLength(len);
      
      // check the candidate location is on the screen
      Location2D cand = v.getEndLocation(attackDroneLoc);      
      if (cand.getX() > 0 && cand.getX() < quadsw && cand.getY() > 0 && cand.getY() < quadsh) {

        if (len < nearestDist) {
          nextNearest = nearest;
          nearest = cand;
          nextNearestDist = nearestDist;
          nearestDist = len;
        }
        else if (len < nextNearestDist) {
          nextNearest = cand;
          nextNearestDist = len;
        }
      }
    }

    if (nearest != null) { // only need to test one dimension
      points.add(nearest);
    }
    if (nextNearest != null) {
      points.add(nextNearest);
    }
  }
  
  private void findGoodCornerPoints(Location2D attackDroneLoc, ArrayList<Location2D> points) {
    // want a waypoint near the 2 corners furthest from the attacker
    Location2D furthest = new Location2D(0, 0), nextFurthest = new Location2D(0, 0);
    float furthestDistSq = Float.MIN_VALUE, nextFurthestDistSq = Float.MIN_VALUE;      
    
    // loop 4 times to get a corner from each, that must also not be inside a planet
    for (int i = 0; i < 4; i++) {
      float x = i == 0 || i == 3 ? QUAD_SIZE / 2 : quadsw - QUAD_SIZE / 2;
      float y = i < 2 ? QUAD_SIZE / 2 : quadsh - QUAD_SIZE / 2;
      
      if (isInsidePlanet(x, y)) {
        continue;
      }
      
      float distSq = getLenSquaredFromCoords(x, y, (float)attackDroneLoc.getX(), (float)attackDroneLoc.getY());
      if (distSq > furthestDistSq) {
        nextFurthest.setLocation(furthest.getX(), furthest.getY());
        furthest.setLocation(x, y);
        nextFurthestDistSq = furthestDistSq;
        furthestDistSq = distSq;
      }
      else if (distSq > nextFurthestDistSq) {
        nextFurthest.setLocation(x, y);
        nextFurthestDistSq = distSq;
      }
    }   
    
    if (furthest.getX() != 0) { // only need to test one dimension
      points.add(furthest);
    }
    if (nextFurthest.getX() != 0) {
      points.add(nextFurthest);
    }
  }

  private boolean isInsidePlanet(float x, float y) {

    for (Planet pl : planets) {
      float radSq = pl.getRadius() * pl.getRadius();
      float distLocToPlanetSq = getLenSquaredFromCoords(x, y, (float)pl.getOrigin().getX(), (float)pl.getOrigin().getY());
      
      if (distLocToPlanetSq < radSq) {
        return true;
      } 
    }

    return false;
  }
  
}


