
CartesianController aStarController = new CartesianController(); // a* evasion/hiding for drones 
Quad[][] quadMap;

// called during setup
void initMap() {
  quadMap = new Quad[numQuadsX][numQuadsY]; 

  for (int qx = 0; qx < quadMap.length; qx++) {
    for (int qy = 0; qy < quadMap[qx].length; qy++) {
      quadMap[qx][qy] = new Quad(qx, qy);
      if ((qx == 0 || qx == quadMap.length -1) && (qy == 0 || qy == quadMap[qx].length -1)) {
        quadMap[qx][qy].setCorner();
      }
    }
  }
  
  // configure the controller
  Heuristic h = new ManhattenHeuristic();
//  h.setAllowDiagonals(true);
  aStarController.setHeuristic(h);
  FringeStrategy fs = new MemoryEfficientFringe(aStarController);
  fs.setCostSensitiveClosedSet(true);
  aStarController.setFringeStrategy(fs);
}

// called when planets config is changed, pause everything while do this
void resetPlanets() {
  if (!isPaused) {
    pauseToggle.set(true);
    isPaused = true;
  }
  
  for (int qx = 0; qx < quadMap.length; qx++) {
    for (int qy = 0; qy < quadMap[qx].length; qy++) {
      quadMap[qx][qy].setNormalInclPlanet(); // resets everything incl start/goal/planet
      
      for (Planet planet : planets) {
        
        /*
        Can use these lines if want to just put all planet quads as high cost, although the used idea is if the centre of the quad is outside the radius, it's ok
        
        if (qy >= planet.getQuadRowStart()
        && qy <= planet.getQuadRowEnd()
        && qx >= planet.getQuadColStart()
        && qx <= planet.getQuadColEnd()) {
          quadMap[qx][qy].setPlanet();
        }
        */
        
        Location2D planetLoc = planet.getOrigin();
        float allowedRad = planet.getRadius() +  QUAD_SIZE / 2;
        float radSq = allowedRad * allowedRad;

        // any planet square is reset, including start/goal, distance is to the centre of each quad, so if it's less than that
        // still allow it to be used (for now anyway)
        if (getLenSquaredFromCoords((float)planetLoc.getX(), (float)planetLoc.getY(), qx * QUAD_SIZE + QUAD_SIZE / 2, qy * QUAD_SIZE + QUAD_SIZE / 2) < radSq) {
          quadMap[qx][qy].setPlanet();
        }
      }
    }
  }
}

void drawCompletedPath(Path completedPath) {
  pushStyle();
  strokeWeight(3);
  stroke(0, 255, 0);
  textSize(10);
  
  AStarNode front = completedPath.front();
  for (MoveToNode moveToNode : completedPath.getStack()) {
    pushMatrix();
    translate(((Quad)moveToNode.getToNode()).getCol() * QUAD_SIZE, ((Quad)moveToNode.getToNode()).getRow() * QUAD_SIZE); 
    if (moveToNode.getToNode().equals(front)) {
      stroke(255, 0, 0);
    }
    noFill();
    rect(0, 0, QUAD_SIZE, QUAD_SIZE);
    fill(255, 255, 255, 120);
    text(String.format("%.2f", ((Quad)moveToNode.getToNode()).getH()), 2, 8);
    text(String.format("%.2f", moveToNode.getCost()), 2, 20);
    text(String.format("%.2f", moveToNode.getG()), 2, 32);

    popMatrix();
  }
  popStyle();
}

Quad[] getPathReverseArray(Path path) {
  Stack<MoveToNode> stack = (Stack<MoveToNode>)path.getStack().clone(); // so can pop it and still debug the candidate paths
  Quad[] res = new Quad[stack.size()];
  for (int i = 0; i < res.length; i++) {
    MoveToNode moveToNode = stack.pop();
    res[i] = (Quad)moveToNode.getToNode();
  }
  return res;
}

void drawQuads() {
  textSize(10);
  for (int qx = 0; qx < quadMap.length; qx++) {
    for (int qy = 0; qy < quadMap[qx].length; qy++) {
      quadMap[qx][qy].draw();
    }
  }
  // and the lines
  stroke(WIDGET_LINES);
  for (int i = 1; i < numQuadsX / QUAD_SIZE; i++) {
    line(i * QUAD_SIZE, 0, i * QUAD_SIZE, numQuadsY * QUAD_SIZE);
  }
  for (int i = 1; i < numQuadsY / QUAD_SIZE; i++) {
    line(0, i * QUAD_SIZE, numQuadsX * QUAD_SIZE, i * QUAD_SIZE);
  }
}

public class Quad extends CartesianNode {
  private int stateFlag = QUAD_STATE_NORMAL;
  private boolean isGoal, isStart, isCorner;
  private int harshLevel;
  
  public Quad(int col, int row) {
    super(aStarController, col, row);
  }
  
  public void draw() {
    pushMatrix();
    translate(col * QUAD_SIZE, row * QUAD_SIZE); 
    setFill();
    rect(0, 0, QUAD_SIZE, QUAD_SIZE);
    fill(255, 255, 255, 120);
    text(String.format("%.2f", getBaseCost()), 2, 42);
    popMatrix();
  }
  
  @Override
  public float getBaseCost() {
    if (stateFlag == QUAD_STATE_PLANET) return 1000;
    else if (isStart) return 0;
    else if (stateFlag == QUAD_STATE_HARSH && !isGoal) return 5 * harshLevel; // in case a goal is in reach but within the pursue range 
    else if (isCorner) return 10;
    else return 1;
  }
  
  @Override
  public void reset() {
    if (!isGoal && !isStart && stateFlag != QUAD_STATE_PLANET) {
      setNormal();
    }
  }
  
  // level is 1 for within visible range of attacker or 2 for within pursue range of attacker
  public void setHarshUnlessPlanet(boolean on, int level) {
    if (!isGoal && !isStart && stateFlag != QUAD_STATE_PLANET) {
      if (on) {
        harshLevel = level;
        stateFlag = QUAD_STATE_HARSH;
      }
      else if (stateFlag == QUAD_STATE_HARSH) {
        stateFlag = QUAD_STATE_NORMAL;
        harshLevel = 0;
      }
    }
  }
  
  public void setCorner() {
    isCorner = true; // discourage running here over near planets
  }
  
  public void setPlanet() {
    stateFlag = QUAD_STATE_PLANET;
  }
  
  public boolean isPlanet() {
    return stateFlag == QUAD_STATE_PLANET;
  }

  public void setNormalInclPlanet() { // setNormal() called when setStart/Goal() is called does not reset planets, but when building quads need to set those too
    stateFlag = QUAD_STATE_NORMAL;
    isGoal = isStart = false;
    H = -1f;
  }
  
  @Override
  public void setNormal() {
    if (isPlanet()) {
      isGoal = isStart = false;
      H = -1f;
    }
    else {
      setNormalInclPlanet();
    }
  }
  
  @Override
  public void setStart() {
    isStart = true;
  }
  
  @Override
  public void setGoal() {
    isGoal = true;
  }
  
  @Override
  public void setExpanded() {
    if (!isGoal && !isStart && stateFlag != QUAD_STATE_PLANET && stateFlag != QUAD_STATE_HARSH) {
      stateFlag = QUAD_STATE_EXPANDED;
    }
  }
  
  @Override
  public void setFringed() {
    if (!isGoal && !isStart && stateFlag != QUAD_STATE_EXPANDED && stateFlag != QUAD_STATE_PLANET && stateFlag != QUAD_STATE_HARSH) {
      stateFlag = QUAD_STATE_FRINGE;
    }
  }
  
  private void setFill() {
    if (isStart) { fill(0, 255, 0, 100); return; }
    if (isGoal) { fill(255, 0, 0, 100); return; }
    switch (stateFlag) {
      case QUAD_STATE_HARSH : fill(255, 255, 0, 20 + 25 * harshLevel); break;
      case QUAD_STATE_EXPANDED : 
      case QUAD_STATE_FRINGE : fill(0, 0, 100, 40); break;
      case QUAD_STATE_PLANET : fill(255, 255, 255, 100); break;
      default : fill(255, 0, 0, 0); stroke(0, 0, 0, 100); break;
    }
  }
}

