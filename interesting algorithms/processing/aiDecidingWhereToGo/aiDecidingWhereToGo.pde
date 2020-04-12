import java.util.*;

static final int REQUEST_FPS = 60, GUI_SPACING = 30,
                 DRONES_MIN = 1, DRONES_MAX = 100, DRONE_W = 14, DRONE_H = 24, RANDOM_DRONE_CHANGE_DELAY = 30, GROUP_DRONE_CHANGE_DELAY = 21, 
                 DRONE_DEFAULT_SPEED_SLIDER = 27, DRONE_RANDOM_GOAL_DEFAULT_RADIUS = DRONE_H, DRONE_MAX_GROUP_SETTINGS = 10,
                 DRONE_GOAL_INSISTENCE_NONE = 0, DRONE_GOAL_INSISTENCE_AVOID_WALLS = 10, DRONE_GOAL_INSISTENCE_AVOID_OBSTACLES = 8, 
                 DRONE_GOAL_INSISTENCE_RANDOM_DIRECTION = 3, DRONE_GOAL_INSISTENCE_GROUP_BEHAVIOURS = 2, DRONE_GOAL_INSISTENCE_EVADE_ATTACKER = 6,  
                 DRONE_NUM_GOALS = 5, DRONE_GOAL_IDX_RANDOM = 0, DRONE_GOAL_IDX_AVOID_WALLS = 1, DRONE_GOAL_IDX_AVOID_PLANETS = 2, 
                 DRONE_GOAL_IDX_GROUP_AWARENESS = 3, DRONE_GOAL_IDX_EVADE_ATTACKER = 4,
                 DRONE_NUM_SENSORS = 3, DRONE_SENSOR_IDX_WALL_FEELER = 0, DRONE_SENSOR_IDX_DETECTION_BOX = 1, DRONE_SENSOR_IDX_EVADE_ATTACKER = 2,
                 PLANETS_MIN = 0, PLANETS_MAX = 8, PLANETS_MIN_SIZE_QUADS = 1, PLANETS_MAX_SIZE_QUADS = 5, // carefull not to allow so many or so big planets that get infinite
                                                                                                           // loop when can't fit any more in (tests for overlap)
                 QUAD_SIZE = DRONE_H * 2,
                 WALL_T_IDX = 0, WALL_R_IDX = 1, WALL_B_IDX = 2, WALL_L_IDX = 3,
                 VERTEX_TOP_LEFT = 0, VERTEX_TOP_RIGHT = 1, VERTEX_BOTTOM_RIGHT = 2, VERTEX_BOTTOM_LEFT = 3, VERTEX_CENTRE = 4, // detection box
                 COLLISION_AVOID_STEER_GENTLE_RIGHT = 0, COLLISION_AVOID_STEER_GENTLE_LEFT = 1, COLLISION_AVOID_STEER_HARD_RIGHT = 2, 
                 COLLISION_AVOID_STEER_HARD_LEFT = 3, COLLISION_AVOID_NONE = 4, DRONE_GROUP_VECTORS_LEN = DRONE_H * 3,
                 DRONE_GROUP_RANGE_QUADS = 3, // the number of quads on each side of a drone to compare for group alignment and cohesion
                 ATTACKER_NUM_GOALS = 5, ATTACKER_NUM_SENSORS = 3, ATTACKER_STATE_GO_TO_WAYPOINT = 0, ATTACKER_STATE_TURN = 1, ATTACKER_STATE_GO_AFTER_DRONE = 2,
                 ATTACKER_GOAL_INSISTENCE_GO_TO_WAYPOINT = 5, ATTACKER_GOAL_IDX_GO_TO_WAYPOINT = 3,
                 ATTACKER_GOAL_INSISTENCE_PURSUE_DRONE = 6, ATTACKER_GOAL_IDX_PURSUE_DRONE = 4, ATTACKER_SENSOR_IDX_LINE_OF_SIGHT = 2,
                 ATTACKER_PURSUE_RANGE_QUADS = 5, ATTACKER_FIRE_RANGE_QUADS = 3, DRONE_HIDE_RANGE_QUADS = 7,
                 TICKS_BETWEEN_ATTACK_FIRING = 60, // about a second
                 // constants for a* classes (see aStarSpecific tab)
                 QUAD_STATE_NORMAL = 0, QUAD_STATE_EXPANDED = 3, QUAD_STATE_FRINGE = 4, QUAD_STATE_PLANET = 5, QUAD_STATE_HARSH = 6,
                 QUAD_HARSH_LEVEL_VISIBLE_RANGE = 1, QUAD_HARSH_LEVEL_PURSUE_RANGE = 2, EVASION_PATH_RECALC_TICKS = 20 // about 1/3rd secs
                 ; 

static color DRONE_NORMAL, DRONE_STEERING, DRONE_HEADING, DRONE_ALERT, DRONE_ALIGNMENT, DRONE_COHESION, ATTACKER;
static color widgetFills, WIDGET_LINES, WALL_COLOUR;

int quadsh, quadsw, numDrones = 0, numPlanets = 0, droneRandomGoalRadius = DRONE_RANDOM_GOAL_DEFAULT_RADIUS, droneRandomGoalOffset = droneRandomGoalRadius * 2,
                 numQuadsY, numQuadsX, droneSeparation = 0, droneAlignment = 0, droneCohesion = 0, quadsToPlanets, quadsBetweenDrones, lastTickQuadsDebugDrawn = -1,
                 lastTickAttackerRangeTranslatedToQuads = -1; // set when the attacker's range is translated so doesn't do it > 1 per tick
float droneSpeed, attackerSpeed, boxDetectionSensorLen, // Nb: setDroneSeparation() sets this size, and must always be called
      dpDirForwardsThreshold, // see discussion in setDroneSpeed()
      droneHideRange, droneHideRangeSq, attackerPursueRange, attackerPursueRangeSq, attackerFireRange, attackerFireRangeSq; // square to avoid square roots in comparisons

Drone[] drones;
Attacker attacker;
LineVector2D[] wallLines = new LineVector2D[4];
Planet[] planets;

Slider dronesSlider, planetsSlider, randomGoalOffsetSlider, droneSeparationSlider, droneSpeedSlider, droneAlignmentSlider, droneCohesionSlider;
Toggle drawSteeringToggle, wallsToggle, wallFeelerBounceFrontToggle, pauseToggle, attackerToggle, showAllEvasionDebugsToggle;

int tick;
boolean drawSteering = false, hasWalls = true, wallFeelerBounceFront = true, isPaused = false, hasAttacker = false, showAllEvasionDebugs;
Vector2D sumVectors = new Vector2D(0, 0); // utility vector for summing to avoid creating obj every draw()

PImage[] explosionImgs;
Explosion[] explosions = new Explosion[10]; // allow for max 10 concurrent explosions

void setup() {
  quadsh = (800 / QUAD_SIZE) * QUAD_SIZE; // truncates
  quadsw = (int)(quadsh * 1.25);
  numQuadsY = (quadsh / QUAD_SIZE);
  numQuadsX = (quadsw / QUAD_SIZE);
  
  attackerPursueRange = ATTACKER_PURSUE_RANGE_QUADS * QUAD_SIZE;
  attackerPursueRangeSq = attackerPursueRange * attackerPursueRange; // squared
  attackerFireRange = ATTACKER_FIRE_RANGE_QUADS * QUAD_SIZE;
  attackerFireRangeSq = attackerFireRange * attackerFireRange; // squared
  droneHideRange = DRONE_HIDE_RANGE_QUADS * QUAD_SIZE;
  droneHideRangeSq = droneHideRange * droneHideRange; // squared
  
  // make wall line vectors, clockwise start loc and vector to end
  wallLines[WALL_T_IDX] = new LineVector2D(0, 0, quadsw, 0);
  wallLines[WALL_R_IDX] = new LineVector2D(quadsw, 0, 0, quadsh);
  wallLines[WALL_B_IDX] = new LineVector2D(quadsw, quadsh, -quadsw, 0);
  wallLines[WALL_L_IDX] = new LineVector2D(0, quadsh, 0, -quadsh);
  
//  println("quads w = "+quadsw+" h = "+quadsh);
  size(quadsw, quadsh); // want even multiple of quadrants
  frameRate(REQUEST_FPS);
  textAlign(LEFT, CENTER);
  imageMode(CENTER);

  // a* path finding
  initMap();

  // set colours and constants
  widgetFills = color(150, 190, 255, 100);
  WIDGET_LINES = color(255, 255, 255, 100);
  DRONE_NORMAL = color(255);
  DRONE_STEERING = color(0, 255, 0, 150);
  DRONE_HEADING = color(0, 0 , 255, 150);
  DRONE_ALERT = color(255, 0 , 0, 150);
  WALL_COLOUR = color(0, 0, 255);
  DRONE_ALIGNMENT = color(255, 165, 0, 150);
  DRONE_COHESION = color(255, 255, 0, 150);
  ATTACKER = color(255, 165, 0);
 
  int sx = 10, sy = height - GUI_SPACING;
  
  droneSpeedSlider = new Slider("Drone spd", DRONE_DEFAULT_SPEED_SLIDER, 0, 100, sx, sy, 300, 20, HORIZONTAL);
  droneSpeedSlider.setInactiveColor(widgetFills);
  droneSpeedSlider.setLineColor(WIDGET_LINES);
  setDroneSpeed(DRONE_DEFAULT_SPEED_SLIDER);

  sy -= GUI_SPACING;
  droneAlignmentSlider = new Slider("Drone algn", droneAlignment, 0, DRONE_MAX_GROUP_SETTINGS, sx, sy, 300, 20, HORIZONTAL);
  droneAlignmentSlider.setInactiveColor(widgetFills);
  droneAlignmentSlider.setLineColor(WIDGET_LINES);
  setDroneAlignment(droneAlignment);

  sy -= GUI_SPACING;
  droneCohesionSlider = new Slider("Drone cohs", droneCohesion, 0, DRONE_MAX_GROUP_SETTINGS, sx, sy, 300, 20, HORIZONTAL);
  droneCohesionSlider.setInactiveColor(widgetFills);
  droneCohesionSlider.setLineColor(WIDGET_LINES);
  setDroneCohesion(droneCohesion);

  sy -= GUI_SPACING;
  droneSeparationSlider = new Slider("Drone spc", droneSeparation, 0, DRONE_MAX_GROUP_SETTINGS, sx, sy, 300, 20, HORIZONTAL);
  droneSeparationSlider.setInactiveColor(widgetFills);
  droneSeparationSlider.setLineColor(WIDGET_LINES);
  setDroneSeparation(droneSeparation);

  sy -= GUI_SPACING;
  planetsSlider = new Slider("Planets", numPlanets, PLANETS_MIN, PLANETS_MAX, sx, sy, 300, 20, HORIZONTAL);
  planetsSlider.setInactiveColor(widgetFills);
  planetsSlider.setLineColor(WIDGET_LINES);

  sy -= GUI_SPACING;
  randomGoalOffsetSlider = new Slider("Rdm Offset", droneRandomGoalOffset, 0, DRONE_H * 5, sx, sy, 300, 20, HORIZONTAL);
  randomGoalOffsetSlider.setInactiveColor(widgetFills);
  randomGoalOffsetSlider.setLineColor(WIDGET_LINES);
  
  sy -= GUI_SPACING;
  dronesSlider = new Slider("Drones", DRONES_MIN, DRONES_MIN, DRONES_MAX, sx, sy, 300, 20, HORIZONTAL);
  dronesSlider.setInactiveColor(widgetFills);
  dronesSlider.setLineColor(WIDGET_LINES);
  setNumDrones(numDrones);
  
  color notSelected = color(125, 100, 100, 0);
  
  sy -= GUI_SPACING;
  showAllEvasionDebugsToggle = new Toggle("                                  All evasive debugs", sx, sy, 10, 10);
  showAllEvasionDebugsToggle.set(showAllEvasionDebugs);
  showAllEvasionDebugsToggle.setActiveColor(widgetFills);
  showAllEvasionDebugsToggle.setInactiveColor(notSelected);
  showAllEvasionDebugsToggle.setLineColor(WIDGET_LINES);

  sy -= GUI_SPACING;
  drawSteeringToggle = new Toggle("                          Steering goal", sx, sy, 10, 10);
  drawSteeringToggle.set(drawSteering);
  drawSteeringToggle.setActiveColor(widgetFills);
  drawSteeringToggle.setInactiveColor(notSelected);
  drawSteeringToggle.setLineColor(WIDGET_LINES);

  sy -= GUI_SPACING;
  wallFeelerBounceFrontToggle = new Toggle("                                                     Wall bounce (off = steer away)", sx, sy, 10, 10);
  wallFeelerBounceFrontToggle.setActiveColor(widgetFills);
  wallFeelerBounceFrontToggle.setInactiveColor(notSelected);
  wallFeelerBounceFrontToggle.setLineColor(WIDGET_LINES);
  wallFeelerBounceFrontToggle.set(hasWalls);

  sy -= GUI_SPACING;
  wallsToggle = new Toggle("              Walls", sx, sy, 10, 10);
  wallsToggle.setActiveColor(widgetFills);
  wallsToggle.setInactiveColor(notSelected);
  wallsToggle.setLineColor(WIDGET_LINES);
  wallsToggle.set(hasWalls);
  
  sy -= GUI_SPACING;
  pauseToggle = new Toggle("              Pause", sx, sy, 10, 10);
  pauseToggle.setActiveColor(widgetFills);
  pauseToggle.setInactiveColor(notSelected);
  pauseToggle.setLineColor(WIDGET_LINES);
  pauseToggle.set(isPaused);

  sy -= GUI_SPACING;
  attackerToggle = new Toggle("                  Attacker", sx, sy, 10, 10);
  attackerToggle.setActiveColor(widgetFills);
  attackerToggle.setInactiveColor(notSelected);
  attackerToggle.setLineColor(WIDGET_LINES);
  attackerToggle.set(hasAttacker);

  // causes reset map which causes pause to be set to on, so do it after other stuff
  setNumPlanets(numPlanets);

  explosionImgs = loadImages("explosion/expl", ".png", 6);
  // init explosions for reuse
  for (int i = 0; i < explosions.length; i++) {
    explosions[i] = new Explosion();
  }

}

void draw() {
  rectMode(CORNER);
  fill(0);
  rect(0, 0, width, height);


  if (!handleGuiMousePressed()) {
	// anything to do with mouse pressing that isn't gui
  }

  rectMode(CORNER);
  droneSpeedSlider.display();
  droneCohesionSlider.display();
  droneAlignmentSlider.display();
  droneSeparationSlider.display();
  dronesSlider.display();
  planetsSlider.display();
  randomGoalOffsetSlider.display();
  drawSteeringToggle.display();
  wallsToggle.display();
  wallFeelerBounceFrontToggle.display();
  pauseToggle.display();
  attackerToggle.display();
  showAllEvasionDebugsToggle.display();
  
  // draw the world
  ellipseMode(CENTER);
  shapeMode(CENTER);
  
  if (!isPaused) {
    update();
  }
  
  // show steering, also need quadrants
  if (drawSteering) {
    if (hasWalls) {
      stroke(WALL_COLOUR);
      strokeWeight(3);
      for (int i = 0; i < wallLines.length; i++) {
        line((float)wallLines[i].getLocation().getX(), (float)wallLines[i].getLocation().getY(), (float)wallLines[i].getEndLocation().getX(), (float)wallLines[i].getEndLocation().getY());
      }
      strokeWeight(1);
    }
    
    /*
    stroke(WIDGET_LINES);
    for (int i = 1; i < width / QUAD_SIZE; i++) {
      line(i * QUAD_SIZE, 0, i * QUAD_SIZE, height);
    }
    for (int i = 1; i < height / QUAD_SIZE; i++) {
      line(0, i * QUAD_SIZE, width, i * QUAD_SIZE);
    }
    */
  }

  for (int i = 0; i < planets.length; i++) {
    planets[i].draw();
  }

  for (int i = 0; i < drones.length; i++) {
    if (drones[i].isAlive()) {
      drones[i].draw();
    }
  }
  
  if (hasAttacker) {
    attacker.draw();
  }
}

void update() {
  tick++;

  for (int i = 0; i < drones.length; i++) {
    if (drones[i].isAlive()) {
      updateDrone(drones[i], droneSpeed);
    }    
  }
  
  if (hasAttacker) {
    updateDrone(attacker, attackerSpeed);
  }
  
  // play any explosions 
  for(int i = 0; i < explosions.length; i++) {
    int tickDiff = tick - explosions[i].tick;
    if (tickDiff >= 0 && tickDiff < explosionImgs.length) {
      
      // if it's in the first few frames, also show the shot fired
      if (tickDiff < explosionImgs.length / 2) {
        pushStyle();
        stroke(255); 
        strokeWeight(3);
        line(explosions[i].srcx, explosions[i].srcy, explosions[i].x, explosions[i].y);
        popStyle();
      }
      
      image(explosionImgs[tickDiff], explosions[i].x, explosions[i].y);
    }
  }
}

void updateDrone(Drone drone, float speed) {
  // sense... gather all the vectors for goals that are insistent in this tick  

  DroneSteeringGoal[] goals = drone.sense();    
  int countInsistentGoals = 0, sumInsistenceGoals = 0;
  sumVectors.changeDirection(0, 0);
  
  for (DroneSteeringGoal goal : goals) {
    if (goal.getInsistenceThisTick() != DRONE_GOAL_INSISTENCE_NONE) {
      countInsistentGoals++;
      sumInsistenceGoals += goal.getInsistenceThisTick();        
      sumVectors.changeDirection(
          goal.getVectorThisTick().getVx() * goal.getInsistenceThisTick() + sumVectors.getVx(), 
          goal.getVectorThisTick().getVy() * goal.getInsistenceThisTick() + sumVectors.getVy());
    }
  }

  if (countInsistentGoals == 1) {
    sumVectors.setLength(speed);
    drone.changeDirection(sumVectors);
  }
  else if (countInsistentGoals > 1) {
    // more than one goal needs to be averaged to the weights
    sumVectors.changeDirection(
        sumVectors.getVx() / sumInsistenceGoals, 
        sumVectors.getVy() / sumInsistenceGoals);
    sumVectors.setLength(speed);
    drone.changeDirection(sumVectors);
  }
  else {
    // no change to direction this time
  }
  
  drone.move();
}

boolean handleGuiMousePressed() {

  if (mousePressed) {
    return updateNumDronesIfGui(true) || updateRandomOffsetIfGui(true) || updateNumPlanetsIfGui(true)
          || updateDroneSeparationIfGui(true) || updateDroneSpeedIfGui(true) || updateDroneAlignmentSliderIfGui(true)
          || updateDroneCohesionSliderIfGui(true);
  }
  
  return false;
}

boolean updateRandomOffsetIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = randomGoalOffsetSlider.mouseDragged();
  }
  else {
    set = randomGoalOffsetSlider.mousePressed();
  }  
  
  if (set) {
    int val = (int) randomGoalOffsetSlider.get();
    if (val != droneRandomGoalOffset) {
      droneRandomGoalOffset = val;
      return true;
    }
  }
  
  return false;
}

boolean updateNumDronesIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = dronesSlider.mouseDragged();
  }
  else {
    set = dronesSlider.mousePressed();
  }  
  
  if (set) {
    int val = (int) dronesSlider.get();
    if (val != numDrones) {
      setNumDrones(val);
      return true;
    }
  }
  
  return false;
}

void setNumDrones(int b) {
  numDrones = b;
  
  if (drones == null) {
    drones = new Drone[numDrones];
  }
  
  // fewer, just truncate the array
  if (b < drones.length) {
    Drone[] newDrones = new Drone[b];
    System.arraycopy(drones, 0, newDrones, 0, newDrones.length);
    drones = newDrones;
  }
  else if (b > drones.length) {
    Drone[] newDrones = new Drone[b];
    System.arraycopy(drones, 0, newDrones, 0, drones.length);
  
    // fill the new slots with random drones
    for (int i = drones.length; i < newDrones.length; i++) {
      newDrones[i] = new Drone(i, new Random().nextInt(width - DRONE_W) + DRONE_W / 2, new Random().nextInt(height - DRONE_H) + DRONE_H / 2, DRONE_W, DRONE_H);
    }

    drones = newDrones;
  }
}

boolean updateNumPlanetsIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = planetsSlider.mouseDragged();
  }
  else {
    set = planetsSlider.mousePressed();
  }  
  
  if (set) {
    int val = (int) planetsSlider.get();
    if (val != numPlanets) {
      setNumPlanets(val);
      return true;
    }
  }
  
  return false;
}

void setNumPlanets(int b) {
  numPlanets = b;
  
  if (planets == null) {
    planets = new Planet[numPlanets];
  }
  
  // fewer, just truncate the array
  if (b < planets.length) {
    Planet[] newPlanets = new Planet[b];
    System.arraycopy(planets, 0, newPlanets, 0, newPlanets.length);
    planets = newPlanets;
  }
  else if (b > planets.length) {
    Planet[] newPlanets = new Planet[b];
    System.arraycopy(planets, 0, newPlanets, 0, planets.length);
  
    // fill the new slots with random planets
    for (int i = planets.length; i < newPlanets.length; i++) {
      
      // loop and keep trying until can fit it in somewhere that doesn't overlap another planet within a quad distance
      boolean added = false;
      TRY_NEXT:
      while (!added) {
        int quadSize = (new Random().nextInt(PLANETS_MAX_SIZE_QUADS -1)) + 1;
        float radius = quadSize / 2.0f * QUAD_SIZE;
        int quadX = new Random().nextInt(numQuadsX -1);
        int quadY = new Random().nextInt(numQuadsY -1);

        // test previous planets for already in that place
        if (i > 0) { // only if have any previously
          for (int j = 0; j < i; j++) {
            
            // distance between them needs to be radius of both plus min space (1 quad)
            Vector2D distance = new Vector2D(newPlanets[j].getOrigin().getX() - quadX * QUAD_SIZE, newPlanets[j].getOrigin().getY() - quadY * QUAD_SIZE);
            float minDistance = newPlanets[j].getRadius() + radius + QUAD_SIZE;
            if (distance.getLength() < minDistance) {
              continue TRY_NEXT;
            }
          }
        }
        
        // got this far, must be ok to add it
        newPlanets[i] = new Planet(quadX, quadY, quadSize);
        added = true;
      }
    }

    planets = newPlanets;
    
    if (hasAttacker) {
      attacker.verifyWaypoints();
    }
  }
  else {
// only happens on initial creation    println("oops");
  }
  
  // a*
  resetPlanets(); 
}

boolean updateDroneSeparationIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = droneSeparationSlider.mouseDragged();
  }
  else {
    set = droneSeparationSlider.mousePressed();
  }  
  
  if (set) {
    int val = (int) droneSeparationSlider.get();
    if (val != droneSeparation) {
      setDroneSeparation(val);
      return true;
    }
  }
  
  return false;
}

void setDroneSeparation(int v) {
  if (droneSeparation != v) {
    droneSeparation = v;
  }

  // set detection box size
  boxDetectionSensorLen = DRONE_H * 2 +      // always at least 2 x drone h
            (droneSeparation * DRONE_H / 5); // plus 0 -> 10 1/5ths of drone h (ie. 0 -> 2x height) 

  // number of quads for broad phase
  quadsToPlanets = (int)(Math.round(boxDetectionSensorLen + 0.5f) / QUAD_SIZE);
  quadsBetweenDrones = (int)(Math.round(boxDetectionSensorLen * 2 + 0.5f) / QUAD_SIZE);
//  println("quadsToPlanets = "+quadsToPlanets+" between drones = "+quadsBetweenDrones);
}

boolean updateDroneAlignmentSliderIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = droneAlignmentSlider.mouseDragged();
  }
  else {
    set = droneAlignmentSlider.mousePressed();
  }  
  
  if (set) {
    setDroneAlignment((int) droneAlignmentSlider.get());
    return true;
  }
  
  return false;
}

void setDroneAlignment(int v) {
  droneAlignment = v;
}

boolean updateDroneCohesionSliderIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = droneCohesionSlider.mouseDragged();
  }
  else {
    set = droneCohesionSlider.mousePressed();
  }  
  
  if (set) {
    setDroneCohesion((int) droneCohesionSlider.get());
    return true;
  }
  
  return false;
}

void setDroneCohesion(int v) {
  droneCohesion = v;
}

boolean updateDroneSpeedIfGui(boolean isDraw) {
  boolean set = false;
  
  if (isDraw) {
    set = droneSpeedSlider.mouseDragged();
  }
  else {
    set = droneSpeedSlider.mousePressed();
  }  
  
  if (set) {
    int val = (int) droneSpeedSlider.get();
    setDroneSpeed(val);
    return true;
  }
  
  return false;
}

void setDroneSpeed(int v) {
  float min = 0.5f, max = 10.f;
  // delta is 1 -> 100
  float delta = (max - min) / 100;
  droneSpeed = min + v * delta;
  attackerSpeed = droneSpeed * .5f;
//  println("droneSpeed = "+droneSpeed+" val="+v);

  // when calculating the dot product of the directions of 2 drones, it's comparing the movement vectors
  // since they're always the same length for both drones, a nice short cut can be used to figure out forwards:
  // a) dp = len*len (of one of the vectors) if the vectors are identical (same direction)
  // b) dp = 0 if they're orthogonal (definition of dp)
  // c) anything > 0 indicates forwards, but that's we want something more forwards than that
  // d) say anything less than about 22.5deg (90deg * .25)
  // e) so "well forwards" can be: len*len*.25, and since len is the same for all the drones it can be calculated once
  // each time the speed is calculated 
  dpDirForwardsThreshold = droneSpeed * droneSpeed * .25f;
}

void mouseReleased() {
  if (drawSteeringToggle.mouseReleased()) {
    drawSteering = drawSteeringToggle.get();
  }
  
  if (wallsToggle.mouseReleased()) {
    hasWalls = wallsToggle.get();
  }
  
  if (wallFeelerBounceFrontToggle.mouseReleased()) {
    wallFeelerBounceFront = wallFeelerBounceFrontToggle.get();
  }
  
  if (pauseToggle.mouseReleased()) {
    isPaused = pauseToggle.get();
  }
  
  if (attackerToggle.mouseReleased()) {
    setAttacker(attackerToggle.get());
  }
  
  if (showAllEvasionDebugsToggle.mouseReleased()) {
    showAllEvasionDebugs = showAllEvasionDebugsToggle.get();
  }
}

void setAttacker(boolean b) {
  hasAttacker = b;
  if (hasAttacker) {
    // constructor same as drone, not using id for now...
    attacker = new Attacker(-1, new Random().nextInt(width - DRONE_W) + DRONE_W / 2, new Random().nextInt(height - DRONE_H) + DRONE_H / 2, DRONE_W, DRONE_H);
  }
  else {
    attacker = null;
  }
}

void mousePressed() {
  updateNumDronesIfGui(false);
  updateRandomOffsetIfGui(false);    
  updateNumPlanetsIfGui(false);    
  updateDroneSeparationIfGui(false);
  updateDroneSpeedIfGui(false);
  updateDroneAlignmentSliderIfGui(false);
  updateDroneCohesionSliderIfGui(false);
}

public float getDotProductFromVectorAndLocation(Vector2D v, Location2D a, Location2D b) { 
  return getDotProductFromVectorAndLocation(v, (float)a.getX(), (float)a.getY(), (float)b.getX(), (float)b.getY()); 
}

public float getDotProductFromVectorAndLocation(Vector2D v, float x1, float y1, float x2, float y2) { 
  float v2x = x2 - x1, v2y = y2 - y1;
  return (float) (v.getVx() * v2x + v.getVy() * v2y); 
}

public float getDotProductFromVectorAndCoords(Vector2D v, float v2x, float v2y) { 
  return (float) (v.getVx() * v2x + v.getVy() * v2y); 
}

public float getLenSquaredFromCoords(float x1, float y1, float x2, float y2) { 
  float vx = x2 - x1, vy = y2 - y1;
  return (float) (vx * vx + vy * vy); 
}

