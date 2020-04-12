import java.util.*;

static final int QUAD_SIZE = 26, QUAD_STATE_NORMAL = 0, QUAD_STATE_START = 1, QUAD_STATE_GOAL = 2, QUAD_STATE_EXPANDED = 3, QUAD_STATE_FRINGE = 4, 
                QUAD_STATE_WALL = 5, QUAD_STATE_HARSH = 6 
                  ;

CartesianController controller = new CartesianController();
                  
int quadsw, quadsh, numQuadsH, numQuadsW, tick, radioW = 20;
Quad[][] quadMap;
boolean isRunning = false, isPaused = false, toggleWalls = true, keepTerrainBetweenRuns = true,
        isDraggingStart = false, isDraggingGoal = false, isAnimated = true, testMaxG = false;
Quad toggleQuad;

RadioGroup strategyRadios, cscsRadios, terrainRadios, heuristicRadios, terrainToggleRadios, animateRadios, diagonalsRadios, testMaxGRadios;

void setup() {
  quadsh = (600 / QUAD_SIZE) * QUAD_SIZE; // truncates
  quadsw = (800 / QUAD_SIZE) * QUAD_SIZE; // truncates
  numQuadsH = (quadsh / QUAD_SIZE);
  numQuadsW = (quadsw / QUAD_SIZE);

  size(quadsw, quadsh + 120); // even multiple of quads
  frameRate(60);
  rectMode(CORNER);
  
  initControls();
  initMap();
  resetAll();  
}

void draw() {
  // only draw once we're running, and one step per so many ticks
  if (!isPaused) {
    if (isRunning) {
      try {
        if (isAnimated) {
          if (tick++ % 2 == 0 || controller.getHeuristic() instanceof EuclidianHeuristic) { // that much slower so no delays for euclidian
            isRunning = controller.processNext();
          }
        }
        else { // not animated, do it all in one go
          controller.processAll();
          isRunning = false;
        }
      }
      catch (MaxGException e) {
        println(e.getMessage());
        isRunning = false; 
      }
    }
    else if (toggleQuad != null) {
      if (mousePressed) {
        Quad mouseQuad = getMouseQuad();
        if (mouseQuad != null && !mouseQuad.equals(toggleQuad)) {
          toggleQuad = mouseQuad;
          mouseQuad.toggleTerrain(toggleWalls);
        }
      }
      else {
        toggleQuad = null;
      }
    }
  }

  drawQuads();
  drawControls();
  
  if (controller.isComplete()) {
    drawCompletedPath();
  }
}

void mouseReleased() {
  if (controller.isComplete()) { // search has finished and it's showing now, so any key resets it
    resetAll();
    return;
  }
  
  Quad mouseQuad = getMouseQuad();
    
  if (isRunning) {
    if (controller.getGoal().equals(mouseQuad)) { // red square pressed while running = cancel
      isPaused = isRunning = false;
      resetAll();
    }
    else {
      isPaused = !isPaused; // any other key pressed while running, toggle pause
    }
    return;
  }

  // not running
  
  if (controller.getStart().equals(mouseQuad)) {
    isRunning = true;
    isDraggingGoal = isDraggingStart = false;
    return;
  }
  
  if (isDraggingStart) {
    isDraggingStart = false;
    if (mouseQuad != null) {
      controller.setStart(mouseQuad);
      resetAll();
    }
  }
  else if (isDraggingGoal) {
    isDraggingGoal = false;
    if (mouseQuad != null) {
      controller.setGoal(mouseQuad);
      resetAll();
    }
  }
}

void mousePressed() {
  if (isRunning || controller.isComplete()) {
    return;
  }
  
  // not running
  
  Quad mouseQuad = getMouseQuad();
  if (mouseQuad == null) {
    checkForRunToggles();
  }
  else if (mouseQuad.equals(controller.getStart())) {
    isDraggingStart = true;
  }
  else if (mouseQuad.equals(controller.getGoal())) {
    isDraggingGoal = true;
  }
  else if (toggleQuad == null) { 
    toggleQuad = mouseQuad;
    mouseQuad.toggleTerrain(toggleWalls);
  }
}

void checkForRunToggles() {
  if (heuristicRadios.clicked() || diagonalsRadios.clicked() || testMaxGRadios.clicked()) {
    resetAll();
  }
  else if (strategyRadios.clicked());
  else if (terrainRadios.clicked());
  else if (cscsRadios.clicked());
  else if (terrainToggleRadios.clicked());
  else if (animateRadios.clicked());
}

void resetAll() {
  isPaused = isRunning = false;
  controller.resetSearch(quadMap, true);
}

void initMap() {
  quadMap = new Quad[numQuadsW][numQuadsH]; 
  //println("quads = "+quads.length+" by "+quads[0].length);
  for (int qx = 0; qx < numQuadsW; qx++) {
    for (int qy = 0; qy < numQuadsH; qy++) {
      quadMap[qx][qy] = new Quad(qx, qy);
    }
  }

    // make the start and dest quads always the same
  controller.setStart(quadMap[6][6]);
  controller.setGoal(quadMap[22][15]);  
}

void drawQuads() {
  background(255);
  
  Quad mouseQuad = null;
  if (isDraggingStart || isDraggingGoal) {
    mouseQuad = getMouseQuad();
  }
  
  for (int qx = 0; qx < numQuadsW; qx++) {
    for (int qy = 0; qy < numQuadsH; qy++) {
      quadMap[qx][qy].draw();
      
      // dragging start or goal and mouse over another square, draw draggee with a transparent fill
      if ((isDraggingStart && controller.getStart().equals(quadMap[qx][qy]) && !controller.getStart().equals(mouseQuad)) 
       || (isDraggingGoal && controller.getGoal().equals(quadMap[qx][qy]) && !controller.getGoal().equals(mouseQuad))) {
        fill(255, 100);
        rect(quadMap[qx][qy].getCol() * QUAD_SIZE, quadMap[qx][qy].getRow() * QUAD_SIZE, QUAD_SIZE, QUAD_SIZE);
      }
    }
  }
  
  if (mouseQuad != null) {
    fill(isDraggingGoal ? 255 : 0, isDraggingStart ? 255 : 0, 0);
    rect(mouseQuad.getCol() * QUAD_SIZE, mouseQuad.getRow() * QUAD_SIZE, QUAD_SIZE, QUAD_SIZE);
  } 
}

void drawCompletedPath() {
  pushStyle();
  strokeWeight(3);
  stroke(0, 255, 0);
  textSize(8);
  
  for (MoveToNode moveToNode : controller.getCompletedPath().getStack()) {
    pushMatrix();
    translate(((Quad)moveToNode.getToNode()).getCol() * QUAD_SIZE, ((Quad)moveToNode.getToNode()).getRow() * QUAD_SIZE); 
    noFill();
    rect(0, 0, QUAD_SIZE, QUAD_SIZE);
    ((Quad)moveToNode.getToNode()).setText();
    text(String.format("%.2f", moveToNode.getCost()), 2, 8);
    text(String.format("%.2f", moveToNode.getG()), 2, 16);

    popMatrix();
  }
  popStyle();
}

Quad getMouseQuad() {
  int col = mouseX / QUAD_SIZE;
  int row = mouseY / QUAD_SIZE;
  if (row >= numQuadsH) {
    return null;
  }
  return quadMap[col][row];
}

public class Quad extends CartesianNode {
  int stateFlag = QUAD_STATE_NORMAL;
  
  public Quad(int col, int row) {
    super(controller, col, row);
  }
  
  public void draw() {
    pushMatrix();
    translate(col * QUAD_SIZE, row * QUAD_SIZE); 
    fill(255);
    rect(0, 0, QUAD_SIZE, QUAD_SIZE);
    setFill();
    rect(0, 0, QUAD_SIZE, QUAD_SIZE);
    setText();
    if (H == -1f) {
      text(String.format("%d/%d", col, row), 0, 0);
    }
    else {
      text(String.format("%.2f", H), 2, 0);
    }
    popMatrix();
  }
  
  @Override
  public float getBaseCost() {
    switch (stateFlag) {
    case QUAD_STATE_WALL : return 1000;
    case QUAD_STATE_HARSH : return 5;
    case QUAD_STATE_START : return 0;
    default : return 1;
    }
  }
  
  @Override
  public void reset() {
    if (stateFlag != QUAD_STATE_GOAL && stateFlag != QUAD_STATE_START) {
      if (!keepTerrainBetweenRuns || (stateFlag != QUAD_STATE_WALL && stateFlag != QUAD_STATE_HARSH)) {
        setNormal();
      }
    }
  }
  
  public void toggleTerrain(boolean isWallOn) {
    if (stateFlag == QUAD_STATE_GOAL || stateFlag == QUAD_STATE_START) {
      return;
    }
    
    if (stateFlag == QUAD_STATE_WALL || stateFlag == QUAD_STATE_HARSH) {
      stateFlag = QUAD_STATE_NORMAL;
    }
    else if (isWallOn) {
      stateFlag = QUAD_STATE_WALL;
    }
    else {
      stateFlag = QUAD_STATE_HARSH;
    }
  }
  
  @Override
  public void setNormal() {
    stateFlag = QUAD_STATE_NORMAL;
    H = -1f;
  }
  
  @Override
  public void setStart() {
    stateFlag = QUAD_STATE_START;
  }
  
  @Override
  public void setGoal() {
    stateFlag = QUAD_STATE_GOAL;
  }
  
  @Override
  public void setExpanded() {
    if (stateFlag != QUAD_STATE_GOAL && stateFlag != QUAD_STATE_START && stateFlag != QUAD_STATE_WALL && stateFlag != QUAD_STATE_HARSH) {
      stateFlag = QUAD_STATE_EXPANDED;
    }
  }
  
  @Override
  public void setFringed() {
    if (stateFlag != QUAD_STATE_EXPANDED && stateFlag != QUAD_STATE_GOAL && stateFlag != QUAD_STATE_START && stateFlag != QUAD_STATE_WALL && stateFlag != QUAD_STATE_HARSH) {
      stateFlag = QUAD_STATE_FRINGE;
    }
  }
  
  private void setFill() {
    switch (stateFlag) {
      case QUAD_STATE_START : fill(0, 255, 0); break;
      case QUAD_STATE_GOAL : fill(255, 0, 0); break;
      case QUAD_STATE_EXPANDED : fill(0, 0, 100, 150); break;
      case QUAD_STATE_FRINGE : fill(0, 0, 100, 50); break;
      case QUAD_STATE_WALL : fill(0); break;
      case QUAD_STATE_HARSH : fill(150, 0, 0, 150); break;
      default : fill(255); stroke(0, 0, 0, 100); break;
    }
  }

  private void setText() {
    textAlign(LEFT, TOP);
    textSize(8);
    switch (stateFlag) {
      case QUAD_STATE_START : fill(0); break;
      case QUAD_STATE_NORMAL : fill(0, 0, 0, 100); break;
      default : fill(255); break;
    }
  }
  
  public String toString() {
    return String.format("[%d,%d]", col, row);
  }
}



