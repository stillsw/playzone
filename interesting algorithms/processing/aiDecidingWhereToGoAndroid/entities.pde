
public class Drone {

  private int id, quadRow, quadCol;
  protected LineVector2D lv;
  protected int hw, hh; // half values
  protected DroneSteeringGoal[] steeringGoals = new DroneSteeringGoal[DRONE_NUM_GOALS];
  protected Sensor[] sensors = new Sensor[DRONE_NUM_SENSORS];
  public String debugObstacles = "";
  protected boolean isAlive = true;
	
  public Drone(int id, double x, double y, int w, int h) {
    this.id = id;
    initSensorsAndGoals();
    
    hw = (int) (w / 2);
    hh = (int) (h / 2);

    // make the initial direction randomly
    float r = droneRandomGoalRadius;
    float deg = new Random().nextInt(360);
    float rx = r * cos(radians(deg));
    float ry = r * sin(radians(deg));

    lv = new LineVector2D(x, y, rx, ry);
    lv.setLength(droneSpeed);
    setQuad();
    for (Sensor sensor : sensors) {
      sensor.directionChanged(this);
    }
  }

  protected void initSensorsAndGoals() {
    steeringGoals = new DroneSteeringGoal[DRONE_NUM_GOALS];
    sensors = new Sensor[DRONE_NUM_SENSORS];
    sensors[DRONE_SENSOR_IDX_WALL_FEELER] = new WallFeeler();
    sensors[DRONE_SENSOR_IDX_DETECTION_BOX] = new DetectionBox();
    sensors[DRONE_SENSOR_IDX_EVADE_ATTACKER] = new DetectAttacker();
    steeringGoals[DRONE_GOAL_IDX_RANDOM] = new RandomSteering();
    steeringGoals[DRONE_GOAL_IDX_AVOID_WALLS] = new AvoidWalls();
    steeringGoals[DRONE_GOAL_IDX_AVOID_PLANETS] = new AvoidPlanets();
    steeringGoals[DRONE_GOAL_IDX_GROUP_AWARENESS] = new DroneGrouping();
    steeringGoals[DRONE_GOAL_IDX_EVADE_ATTACKER] = new EvadeAttacker();
  }

  public int getId() {
    return id;
  }
  
  public LineVector2D getLineVector() {
    return lv;
  }

  public int getQuadRow() {
    return quadRow;
  }

  public int getQuadCol() {
    return quadCol;
  }

  public void kill() {
    isAlive = false;
  }
  
  public boolean isAlive() {
    return isAlive;
  }

  public void draw() {
    pushStyle();
    setDrawStyle();

    pushMatrix();
    translate((float)lv.getLocation().getX(), (float)lv.getLocation().getY());
    
    pushMatrix(); // so can undo the rotation before calling draw on the steering
    float a = atan2((float)lv.getVy(), (float)lv.getVx());    
    rotate(a);
    
    // 3 sets of points make a triangle, they start facing right?
    triangle(hh, 0, -hh, hw, -hh, -hw);
    popMatrix();
    noFill();
    
    // when toggled to show debug
    if (drawSteering) {
      textAlign(LEFT, CENTER);

      // centre and goal
      ellipse(0, 0, 3, 3);
      
      for (DroneSteeringGoal steeringGoal : steeringGoals) {
        steeringGoal.draw(this);
      }
      
      for (Sensor sensor : sensors) {
        sensor.draw(this);
      }
      
      fill(WIDGET_LINES);
      int textY = hh * 2, textX = hw;
      if (quadRow >= numQuadsY -1) {
        textY = 0;  
      }
      
      if (quadCol >= numQuadsX - 2) {
          textAlign(RIGHT, CENTER);
          textX = 0;
      }
      
      text(this.toString(), textX, textY);  
      noFill();
    }

    popMatrix();
    popStyle();	

    if (drawSteering) { // anything that needs drawing that couldn't be while transformed
      for (DroneSteeringGoal steeringGoal : steeringGoals) {
        steeringGoal.drawNotTransformed(this);
      }
      
      for (Sensor sensor : sensors) {
        sensor.drawNotTransformed(this);
      }
    }
  }
  
  protected void setDrawStyle() {
    stroke(DRONE_NORMAL);
    noFill();
  }
  
  /**
   * go through all the goals and do a sensing step 
   * which means each one that has something to do will set the insistence to a non-0 value
   * so the caller can gather all the goals' insistences and produce a weighted direction change
   */
  public DroneSteeringGoal[] sense() {
    
    // reset debug text beforehand if needed
    if (drawSteering) {
      debugObstacles = "";
    }
    
    for (DroneSteeringGoal steeringGoal : steeringGoals) {
      steeringGoal.proposeDirectionChange(this, WallFeeler.class.equals(steeringGoal.getSensorType()) 
                                                    ? sensors[DRONE_SENSOR_IDX_WALL_FEELER] 
                                                    : DetectionBox.class.equals(steeringGoal.getSensorType())
                                                        ? sensors[DRONE_SENSOR_IDX_DETECTION_BOX] 
                                                        : LineOfSight.class.equals(steeringGoal.getSensorType())
                                                            ? sensors[ATTACKER_SENSOR_IDX_LINE_OF_SIGHT]
                                                            : DetectAttacker.class.equals(steeringGoal.getSensorType())
                                                                ? sensors[DRONE_SENSOR_IDX_EVADE_ATTACKER] : null);
    }
    
    return steeringGoals;
  }
  
  /**
   * another drone has already detected a collision with this one
   * so there's a chance to do less work to find that out
   */
  public void reactToCollision(Drone otherDrone) {
    ((DroneGrouping)steeringGoals[DRONE_GOAL_IDX_GROUP_AWARENESS]).reactToCollision(this, otherDrone, (DetectionBox)sensors[DRONE_SENSOR_IDX_DETECTION_BOX]);
  }
  
  public void changeDirection(Vector2D v) {
    lv.changeDirection(v.getVx(), v.getVy());
    for (Sensor sensor : sensors) {
      sensor.directionChanged(this);
    }
  }
  
  public void move() {
    lv.advancePosition();
    if (!hasWalls) {
      wrapLocation(lv);
    }
    setQuad();
  }
  
  private void wrapLocation(LineVector2D loc) {   
    // wrap
    double x = loc.getLocation().getX();
    double y = loc.getLocation().getY();
    boolean wrapped = false;
    if (x < 0) {
      x += width;
      wrapped = true;
    }
    else if (x > width) {
      x %= width;
      wrapped = true;
    }
    if (y < 0) {
      y += height;
      wrapped = true;
    }
    else if (y > height) {
      y %= height;
      wrapped = true;
    }
    
    if (wrapped) {
      loc.changeLocation(x, y);
    }
  }
 
  public void setQuad() {
    quadRow = (int) (lv.getLocation().getY() / QUAD_SIZE);
    quadCol = (int) (lv.getLocation().getX() / QUAD_SIZE);
    if (quadRow < 0 || quadCol < 0 || quadCol >= numQuadsX || quadRow >= numQuadsY) {
      println("drone over quaded "+quadCol+"/"+quadRow+" killing");
      kill();
    }
  }
  
  public DetectionBox getDetectionBoxSensor() {
    return (DetectionBox)sensors[DRONE_SENSOR_IDX_DETECTION_BOX];
  }
 
  public String toString() {
    return String.format("#%s [%s,%s]%s", id, quadRow, quadCol, debugObstacles);
  }
  
}

public class Attacker extends Drone {
  
  private int attackerState = ATTACKER_STATE_TURN;
  private Location2D[] waypoints;
  private int currentWaypoint, firedTick = Integer.MIN_VALUE;
  
  public Attacker(int id, double x, double y, int w, int h) {
    super(id, x, y, w, h);
    initWaypoints();
  }

  protected void initSensorsAndGoals() {
    steeringGoals = new DroneSteeringGoal[ATTACKER_NUM_GOALS];
    sensors = new Sensor[ATTACKER_NUM_SENSORS];
    sensors[DRONE_SENSOR_IDX_WALL_FEELER] = new WallFeeler();
    sensors[DRONE_SENSOR_IDX_DETECTION_BOX] = new DetectionBox();
    sensors[ATTACKER_SENSOR_IDX_LINE_OF_SIGHT] = new LineOfSight();
    steeringGoals[DRONE_GOAL_IDX_RANDOM] = new RandomSteering();
    steeringGoals[DRONE_GOAL_IDX_AVOID_WALLS] = new AvoidWalls();
    steeringGoals[DRONE_GOAL_IDX_AVOID_PLANETS] = new AvoidPlanets();
    steeringGoals[ATTACKER_GOAL_IDX_GO_TO_WAYPOINT] = new SteerToWaypoint();
    steeringGoals[ATTACKER_GOAL_IDX_PURSUE_DRONE] = new PursueDrones();
  }
  
  private void initWaypoints() {
    // want a waypoint near every corner
    waypoints = new Location2D[4];
    for (int i = 0; i < waypoints.length; i++) {
      int randomX = ((new Random().nextInt(5)) + 1) * QUAD_SIZE;
      int randomY = ((new Random().nextInt(5)) + 1) * QUAD_SIZE;
      
      int x = (i % 2 == 0 ? randomX : width - randomX);
      int y = (i < 2 ? randomY : height - randomY);
      
      waypoints[i] = new Location2D(x, y);
    }   
    
    currentWaypoint = new Random().nextInt(4);
    
    verifyWaypoints();
  }
  
  public void fire() {
    firedTick = tick;
  }
  
  public int getCurrentState() {
    return attackerState;
  }
  
  public void setTurning() {
    attackerState = ATTACKER_STATE_TURN;
  }
  
  public void setPursueDrone() {
    attackerState = ATTACKER_STATE_GO_AFTER_DRONE;
  }
  
  public void setToWaypoint() {
    attackerState = ATTACKER_STATE_GO_TO_WAYPOINT;
  }
  
  public Location2D getWaypoint() {
    return waypoints[currentWaypoint];
  }
  
  public Location2D advanceWaypoint() {
    currentWaypoint = (currentWaypoint + 1) % 4;
    return getWaypoint();
  }
  
  public boolean hasReachedWayPoint(float distSq) {
    return (distSq < (droneSpeed * droneSpeed * 200)); // a reasonably close distance (ie. 200x the speed of the drone)
  }
  
  protected void setDrawStyle() {
    float tickDiff = tick - firedTick;
    if (tickDiff >= 0 && tickDiff < TICKS_BETWEEN_ATTACK_FIRING) {
      float delta = tickDiff / TICKS_BETWEEN_ATTACK_FIRING;
      int opacity = (int)(255 * delta);
      stroke(ATTACKER);
      fill(255, 165, 0, opacity);
    }
    else {
      noStroke();
      fill(ATTACKER);
    }
  }
  
  public void draw() {
    super.draw();
    if (drawSteering) {
      pushStyle();
      stroke(DRONE_HEADING);
      ellipse((float)waypoints[currentWaypoint].getX(), (float)waypoints[currentWaypoint].getY(), 6, 6); 
      popStyle();
    }
  }  
  
  public void verifyWaypoints() {
    // check no waypoint is inside a planet and can't be reached
    for (Location2D wp : waypoints) {
      int moves = 0;
      while (movedWaypoint(wp)) {
        if (++moves > 5) {
          // don't sweat it, planets probably very unusually placed
          throw new IllegalStateException("can't seem to put the waypoint in a legal position");
        }
      }
    }
  }
  
  private boolean movedWaypoint(Location2D wp) {
    // checks for this waypoint inside any planet and moves it if so
    for (Planet pl : planets) {
      Vector2D pv = new Vector2D(pl.getOrigin(), wp);
      if (pv.getLength() < pl.getRadius()) {
        print("moved waypoint ("+wp+") ");
        Vector2D cv = new Vector2D(pl.getOrigin(), width / 2, height / 2);
        cv.setLength(pl.getRadius());
        wp.addVector(cv);
        print("to ("+wp+") ");
        return true;
      } 
    }

    return false;
  }
}

/**
 * A simple round obstacle, size is the diameter
 */
public class Planet {
  
  private Location2D origin;
  private float radius, d;
  private int quadRowStart, quadRowEnd, quadColStart, quadColEnd;
  private String debug;
  
  public Planet(int quadX, int quadY, int sizeQuads) {
    origin = new Location2D(quadX * QUAD_SIZE, quadY * QUAD_SIZE);
    radius = sizeQuads / 2.0f * QUAD_SIZE;
    d = radius * 2;
//    println("origin="+origin+" d="+d);
    // for quick broad phase checks, store the min/max quads in both dimensions
    quadRowStart = (int) ((origin.getY() - radius) / QUAD_SIZE);// quadY - sizeQuads / 2;
    quadRowEnd = (int) ((origin.getY() + radius) / QUAD_SIZE) + (sizeQuads % 2 == 0 ? -1 : 0); //quadRowStart + sizeQuads;
    quadColStart = (int) ((origin.getX() - radius) / QUAD_SIZE);// quadX - sizeQuads / 2;
    quadColEnd = (int) ((origin.getX() + radius) / QUAD_SIZE) + (sizeQuads % 2 == 0 ? -1 : 0); // quadColStart + sizeQuads;
    debug = "qx["+quadColStart+","+quadColEnd+"],qy["+quadRowStart+","+quadRowEnd+"]";
  }

  public Location2D getOrigin() {
    return origin;
  }
  
  public float getRadius() {
    return radius;
  }

  public int getQuadColStart() {
    return quadColStart;
  }

  public int getQuadColEnd() {
    return quadColEnd;
  }

  public int getQuadRowStart() {
    return quadRowStart;
  }

  public int getQuadRowEnd() {
    return quadRowEnd;
  }

  public void draw() {
    pushStyle();
    noFill();
    
    if (drawSteering && showAllEvasionDebugs) {
      stroke(DRONE_STEERING);
      // draw the rectangle of quads around this planet
      rectMode(CORNER);
      rect(quadColStart * QUAD_SIZE, quadRowStart * QUAD_SIZE, (quadColEnd - quadColStart + 1) * QUAD_SIZE, (quadRowEnd - quadRowStart + 1) * QUAD_SIZE); 
      
      fill(WIDGET_LINES);
      text(debug, (float)origin.getX(), (float)origin.getY());  
      noFill();
    }
    
    stroke(DRONE_NORMAL);
    ellipse((float)origin.getX(), (float)origin.getY(), d, d);
    
    popStyle();
  }

  public String toString() {
    return debug;
  }
}

public void playExplosion(Location2D source, Location2D target) {
  // look for a free slot
  for(int i = 0; i < explosions.length; i++) {
    if (explosions[i].tick < tick + explosions.length) {
      explosions[i].tick = tick + 1;
      explosions[i].x = (int)target.getX();
      explosions[i].y = (int)target.getY();
      explosions[i].srcx = (int)source.getX();
      explosions[i].srcy = (int)source.getY();
    }
  }
}

/**
 * utility class to encapsulate an explosion
 */
class Explosion {
  float x, y, srcx, srcy;
  int tick = -10; // lower number than 0 minus explosions array length
}

