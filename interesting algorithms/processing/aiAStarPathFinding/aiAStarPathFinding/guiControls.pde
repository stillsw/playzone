import java.util.ArrayList;

public class RadioGroup {
  private ArrayList<Radio> group = new ArrayList<Radio>();
  private Radio selected;
  
  public RadioGroup(Radio ... radios) {
    for (Radio radio : radios) {
      addRadio(radio);
    }
  }
  
  public void addRadio(Radio radio) {
    group.add(radio);
  }
  
  public void setSelected(Radio radio) {
    selected = radio;
    if (selected != null) {
      radio.setOn();
    }
  }
  
  public boolean clicked() {
    Radio clicked = null;
    for (Radio radio : group) {
      if (radio.clicked()) {
        clicked = radio;
        break;
      }
    }
    
    if (clicked == null) {
      return false;
    }

    if (clicked.equals(selected) && group.size() == 1) { // toggle button
      selected = null;
      clicked.setOff();
      return true;
    }
    
    selected = clicked;
    selected.setOn(); // also runs callback    
    return true;
  }
  
  public void drawGroup() {
    pushStyle();
    textAlign(RIGHT, CENTER);
    textSize(12);
    stroke(0);

    for (Radio radio : group) {
      fill(255);
      ellipse(radio.x, radio.y, radio.size, radio.size);
      fill(0);
      text(radio.title, radio.x - 20, radio.y);
    }

    if (selected != null) {
      fill(0);
      ellipse(selected.x, selected.y, 14, 14);
    }
    popStyle();
  }
}

public class Radio {
  float x, y, size;
  String title;
  Runnable callback, callbackOff;
  
  public Radio(String title, float x, float y, float size, Runnable callback, Runnable callbackOff) { // 2nd callback for when only one option (ie. toggle)
    this.title = title;
    this.x = x;
    this.y = y;
    this.size = size;
    this.callback = callback;
    this.callbackOff = callbackOff;
  }
  
  public boolean clicked() {
    return distance(mouseX, mouseY, x, y) < size / 2;
  }
  
  public void setOn() {
    callback.run();
  }
  
  public void setOff() {
    callbackOff.run();
  }
  
}

void initControls() {

  int lastColumnX = quadsw - 30;
  int firstLineY = quadsh + 18;
  int secondLineY = quadsh + 45;
  int thirdLineY = quadsh + 72;
  int forthLineY = quadsh + 99;
  int lastColumn2X = lastColumnX - 140;
  int lastColumn3X = lastColumn2X - 140;

  Radio manhatten = new Radio("manhatten", lastColumnX, firstLineY, radioW, 
                       new Runnable() { public void run() {controller.setHeuristic(new ManhattenHeuristic()); } }, null);
  final Radio euclidian = new Radio("euclidian", lastColumnX, secondLineY, radioW, new Runnable() { public void run() {controller.setHeuristic(new EuclidianHeuristic());} }, null);
  heuristicRadios = new RadioGroup(manhatten, euclidian);
  heuristicRadios.setSelected(manhatten);

  Radio diagonals = new Radio("allow diagonals", lastColumnX, thirdLineY, radioW,
                       new Runnable() { public void run() {controller.getHeuristic().setAllowDiagonals(true);} },   // toggle on
                       new Runnable() { public void run() {controller.getHeuristic().setAllowDiagonals(false);} }); // toggle off
  diagonalsRadios = new RadioGroup(diagonals);
  if (controller.getHeuristic().allowsDiagonals())
    diagonalsRadios.setSelected(diagonals);

  Radio memEff = new Radio("memory efficient", lastColumn2X, firstLineY, radioW, 
                       new Runnable() { public void run() {controller.setFringeStrategy(new MemoryEfficientFringe(controller));} },   // toggle on
                       new Runnable() { public void run() {controller.setFringeStrategy(new FringeStrategy(controller));} }); // toggle off
  strategyRadios = new RadioGroup(memEff);
  strategyRadios.setSelected(memEff);
  
  Radio cscs = new Radio("cost sensitive", lastColumn2X, secondLineY, radioW, 
                       new Runnable() { public void run() {controller.getFringeStrategy().setCostSensitiveClosedSet(true);} },   // toggle on
                       new Runnable() { public void run() {controller.getFringeStrategy().setCostSensitiveClosedSet(false);} }); // toggle off
  cscsRadios = new RadioGroup(cscs);
  if (controller.getFringeStrategy().getCostSensitiveClosedSet()) 
    cscsRadios.setSelected(cscs);

  Radio maxGr = new Radio("test max G", lastColumn2X, thirdLineY, radioW, 
                       new Runnable() { public void run() {controller.getFringeStrategy().setMaxG(controller.getStart().getH() / 2);} },    // toggle on
                       new Runnable() { public void run() {controller.getFringeStrategy().setMaxG(Float.MAX_VALUE);} }); // toggle off
  testMaxGRadios = new RadioGroup(maxGr);
  if (testMaxG) 
    testMaxGRadios.setSelected(maxGr);
      
  Radio animate = new Radio("animation on", lastColumn2X, forthLineY, radioW, 
                       new Runnable() { public void run() {isAnimated = true;} },    // toggle on
                       new Runnable() { public void run() {isAnimated = false;} }); // toggle off
  animateRadios = new RadioGroup(animate);
  if (isAnimated) 
    animateRadios.setSelected(animate);
      
  Radio walls = new Radio("dragging makes walls", lastColumn3X, firstLineY, radioW, new Runnable() { public void run() {toggleWalls = true;} }, null);
  Radio terrain = new Radio("or harsh terrain", lastColumn3X, secondLineY, radioW, new Runnable() { public void run() {toggleWalls = false;} }, null);
  terrainRadios = new RadioGroup(walls, terrain); 
  terrainRadios.setSelected(walls);

  Radio toggleClearTerrain = new Radio("clear terrain on reset", lastColumn3X, thirdLineY, radioW, 
                       new Runnable() { public void run() {keepTerrainBetweenRuns = false;} },    // toggle on
                       new Runnable() { public void run() {keepTerrainBetweenRuns = true;} }); // toggle off
  terrainToggleRadios = new RadioGroup(toggleClearTerrain);
  if (!keepTerrainBetweenRuns) 
    terrainToggleRadios.setSelected(toggleClearTerrain);
  
}

void drawControls() {
  pushStyle();
  fill(0);
  textSize(12);
  text("click green to start, red to cancel, other to pause", 15, quadsh + 12);
  text("when search is completed, click anywhere to reset", 15, quadsh + 32);

  if (controller.isComplete()) {
    int[] stats = controller.getStats();
    text(String.format("#nodes expanded = %d #total processes = %d", 
          stats[controller.STATS_NUM_NODES_EXPANDED], stats[controller.STATS_NUM_PROCESSES_TO_SOLUTION]), 15, quadsh + 55);
    text(String.format("#max paths in memory = %d", stats[controller.STATS_MAX_PATHS_IN_MEMORY]), 15, quadsh + 70);
  }
  
  heuristicRadios.drawGroup();
  terrainRadios.drawGroup();
  strategyRadios.drawGroup();
  cscsRadios.drawGroup();
  terrainToggleRadios.drawGroup();
  animateRadios.drawGroup();
  diagonalsRadios.drawGroup();
  testMaxGRadios.drawGroup();
}


