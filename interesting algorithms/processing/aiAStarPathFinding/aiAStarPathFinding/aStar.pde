public class MaxGException extends Exception {
  public MaxGException(float maxG) {
    super("Maximum G ("+maxG+") has been surpaseed, causing abort");
  }
}

public abstract class AStarController {
  public final int STATS_NUM_PROCESSES_TO_SOLUTION = 0, STATS_NUM_NODES_EXPANDED = 1, STATS_MAX_PATHS_IN_MEMORY = 2;
  private FringeStrategy fringeStrategy;
  private Heuristic heuristic;
  private Path completedPath;
  private HashSet<Path> fringe = new HashSet<Path>();
  private boolean isComplete = false;
  private AStarNode start, goal;
  private AStarNode[][] map;
  
  // stats
  int[] stats = new int[3]; ;
  
  public AStarNode getStart() {
    return start;
  }
  
  // before starting a search both start and goal must be set, doesn't matter what order
  public void setStart(AStarNode node) {
    if (start != null) {
      start.setNormal();
    }
    start = node;
    start.setStart();
    if (goal != null) {
      start.setH(heuristic.getValue(start, goal));
    }
  }
  
  public AStarNode getGoal() {
    return goal;
  }
  
  // before starting a search both start and goal must be set, doesn't matter what order
  public void setGoal(AStarNode node) {
    if (goal != null) {
      goal.setNormal();
    }
    goal = node;
    goal.setGoal();
    if (start != null) {
      start.setH(heuristic.getValue(start, goal));
    }
  }
  
  public HashSet<Path> getFringe() {
    return fringe;
  }

  public void setHeuristic(Heuristic hs) {
    if (!(this instanceof CartesianController)) {
      throw new IllegalArgumentException("heuristic and controller only support cartesian setup, so far");
    }
    heuristic = hs;
  }
  
  public Heuristic getHeuristic() {
    return heuristic;
  }
  
  public void setFringeStrategy(FringeStrategy fs) {
    fringeStrategy = fs;
  }
  
  public FringeStrategy getFringeStrategy() {
    return fringeStrategy;
  }
  
  public void processAll() throws MaxGException {
    while (!isComplete) {
      processNext();
    }
  }
  
  public boolean processNext() throws MaxGException {
    stats[STATS_NUM_PROCESSES_TO_SOLUTION]++;
    
    Path expandPath = fringeStrategy.removeFromFringe();
    AStarNode expandNode = expandPath.front();
    
    if (expandNode.equals(goal)) {
      goalSuccess(expandPath);
      return false; // no more to process
    }
    else if (fringeStrategy.shouldExpandQuad(expandNode, expandPath.getG())) {
      stats[STATS_NUM_NODES_EXPANDED]++;
      fringeStrategy.putNodeInClosed(expandNode, expandPath.getG());
      expandNode.setExpanded();
      expandPath.addSuccessorsToFringe(expandNode);
      stats[STATS_MAX_PATHS_IN_MEMORY] = Math.max(stats[STATS_MAX_PATHS_IN_MEMORY], fringe.size());
      return true;
    }
    else {
      // didn't expand a node, rather than wait, go again recursively
      return processNext();
    }
  }
  
  public void goalSuccess(Path path) {
    isComplete = true;
    completedPath = path;
  }
  
  public boolean isComplete() {
    return isComplete;
  }
  
  public Path getCompletedPath() {
    return completedPath;
  }

  public void resetSearch(AStarNode[][] map, boolean resetMap) {
    completedPath = null;
    isComplete = false;
    fringeStrategy.reset();
    fringe.clear();
    start.setH(heuristic.getValue(start, goal));
    fringeStrategy.addToFringe(new MoveToNode(this, null, start));
    for (int i = 0; i < stats.length; i++) stats[i] = 0;
    
    this.map = map;
    if (resetMap) {
      for (int qx = 0; qx < map.length; qx++) {
        for (int qy = 0; qy < map[qx].length; qy++) {
          map[qx][qy].reset();
        }
      }
    }
  }
  
  public AStarNode[][] getMap() {
    return map;
  }
  
  public int[] getStats() {
    return stats;
  }
}

public class CartesianController extends AStarController {
  final Vector[] DIRECTIONS = new Vector[] { new Vector(0,-1), new Vector(1,0), new Vector(0,1), new Vector(-1,0),
                    new Vector(1,-1), new Vector(1,1), new Vector(-1,1), new Vector(-1,-1) };

}

public class CartesianNode extends AStarNode {
  protected int col, row;
  public CartesianNode(CartesianController controller, int col, int row) {
    super(controller);
    this.col = col;
    this.row = row;
  }
  
  public int getCol() {
    return col;
  }
  
  public int getRow() {
    return row;
  }
}

public abstract class AStarNode {
  private AStarController controller;
  protected float H = -1f;
  
  public AStarNode(AStarController controller) {
    this.controller = controller; 
  }

  public float getBaseCost() {
    return 1;
  }
  
  public void setH(float h) {
    H = h;
  }
  
  public float getH() {
    return H;
  }
  
  public void reset() {
    H = -1f;
  }
  
  public void setNormal() {
    reset();
  }
  
  public void setStart() {
  }
  
  public void setGoal() {
  }
  
  public void setExpanded() {
  }
  
  public void setFringed() {
  }
}

public class MoveToNode {
  private AStarNode fromNode, toNode;
  private float cost, G;

  public MoveToNode(AStarController controller, AStarNode fromNode, AStarNode toNode) {
    this.fromNode = fromNode;
    this.toNode = toNode;
    cost = controller.getHeuristic().getCost(this);
  }
  
  public AStarNode getFromNode() {
    return fromNode;
  }
  
  public AStarNode getToNode() {
    return toNode;
  }
  
  public float getCost() {
    return cost;
  }
  
  public void setG(float g) {
    G = g;
  }
  
  public float getG() {
    return G;
  }
}

public class FringeStrategy {
  protected HashMap<AStarNode, Float> closed = new HashMap<AStarNode, Float>();
  protected AStarController controller;
  private boolean costSensitiveClosedSet = true;
  protected float maxG = Float.MAX_VALUE;

  public FringeStrategy(AStarController controller) {
    this.controller = controller; 
  }
  
  public void reset() {
    closed.clear();
  } 

  // optimize the situation where a goal over a certain value is no good (eg. multiple possible goals)
  public void setMaxG(float maxG) {
    this.maxG = maxG;
  }
  
  public void setCostSensitiveClosedSet(boolean b) {
    costSensitiveClosedSet = b;
  }
  
  public boolean getCostSensitiveClosedSet() {
    return costSensitiveClosedSet;
  }

  public void putNodeInClosed(AStarNode expandNode, float G) {
    closed.put(expandNode, G);
  }
    
  public boolean shouldExpandQuad(AStarNode candNode, float G) {
  
    // don't expand past the max G, but still do want to add to the closed set if that happens
    
    if (closed.keySet().contains(candNode)) {
  
      if (!costSensitiveClosedSet) {
        return false;
      }
      else if (G < maxG) {
        // cscs means if the cost of this path is less than that on the path already, it should expand
        return G < closed.get(candNode);
      }
      else { // blown G
        return false;
      } 
    }
    else if (G < maxG) {
      return true;
    }
    else { // blown G, add it to closed set
      putNodeInClosed(candNode, G);
      return false;
    }
  }  
  
  public Path removeFromFringe() throws MaxGException {
    HashSet<Path> fringe = controller.getFringe();
    if (fringe.isEmpty()) {
      if (maxG == Float.MAX_VALUE) {
        throw new IllegalStateException("empty fringe");
      }
      else {
        throw new MaxGException(maxG);
      }
    }
    
    // find the quad with the lowest F in the set, if there's a tie break, choose lower H
    Path chosen = null;
    for (Path path : fringe) {
      if (chosen == null) {
        chosen = path;
      }
      else if (Float.compare(path.getF(), chosen.getF()) < 0) {
        chosen = path;
      }
      else if (Float.compare(path.getF(), chosen.getF()) == 0 && path.front().getH() < chosen.front().getH()) {
        chosen = path;
      }
    } 

    // remove it from the fringe and return it    
    fringe.remove(chosen);
    return chosen;
  }
  
  // only the first node will be added without an ancester path
  public void addToFringe(MoveToNode moveToNode) {
    controller.getFringe().add(new Path(controller, moveToNode)); 
  }
  
  public void addToFringe(Path copyPath, MoveToNode moveToNode) {
    controller.getFringe().add(new Path(controller, copyPath, moveToNode));
  }
  
}

public class MemoryEfficientFringe extends FringeStrategy {

  public MemoryEfficientFringe(AStarController controller) {
    super(controller);
  }
  
  @Override
  public void addToFringe(Path copyPath, MoveToNode moveToNode) {
    // test for fringe containing a path with the same quad already at the front
    // if so, only keep the one with the cheaper cost
    
    Path removePath = null;
    HashSet<Path> fringe = controller.getFringe();
    for (Path path : fringe) {
      if (path.front().equals(moveToNode.getToNode())) {
        if (copyPath.getG() + moveToNode.getCost() + moveToNode.getToNode().getH() < path.getF()) {
          removePath = path;
          break;
        }
      }
    } 
    
    if (removePath != null) {
      fringe.remove(removePath);
    }
    
    super.addToFringe(copyPath, moveToNode);
  }
}

public abstract class AbstractPath {
  protected AStarController controller;
  protected Stack<MoveToNode> stack;

  private AbstractPath(AStarController controller) {
    this.controller = controller;
  }
  
  public AbstractPath(AStarController controller, MoveToNode moveToNode) {
    this(controller);
    stack = new Stack<MoveToNode>();
    stack.push(moveToNode);
    moveToNode.setG(moveToNode.getCost());
  }

  public AbstractPath(AStarController controller, Path copyPath, MoveToNode moveToNode) {
    this(controller);
    stack = (Stack<MoveToNode>)copyPath.stack.clone();
    stack.push(moveToNode);
    moveToNode.setG(copyPath.getG() + moveToNode.getCost()); 
  }
  
  public AStarNode front() {
    return stack.peek().getToNode();
  }
  
  public Stack<MoveToNode> getStack() {
    return stack;
  }

  public boolean isAncestor(AStarNode node) {
    for (MoveToNode moveToNode : stack) {
      if (node.equals(moveToNode.getFromNode()) || node.equals(moveToNode.getToNode())) {
        return true;
      }
    }
    
    return false;
  }
  
  public float getG() {
    return stack.peek().getG();
  }
  
  public float getF() {
    return getG() + front().getH();
  }
  
  public abstract void addSuccessorsToFringe(AStarNode node);
}

public class Path extends AbstractPath {

  public Path(AStarController controller, MoveToNode moveToNode) {
    super(controller, moveToNode);
  }

  public Path(AStarController controller, Path copyPath, MoveToNode moveToNode) {
    super(controller, copyPath, moveToNode);
  }
  
  public void addSuccessorsToFringe(AStarNode n) {
    CartesianNode node = (CartesianNode)n;
    AStarNode[][] map = controller.getMap();
    Vector[] DIRECTIONS = ((CartesianController)controller).DIRECTIONS;
    Heuristic heuristic = controller.getHeuristic();
    AStarNode[] successors = new AStarNode[heuristic.allowsDiagonals() ? 8 : 4];
    for (int i = 0; i < successors.length; i++) {
      try {
        successors[i] = map[node.getCol() + DIRECTIONS[i].vx][node.getRow() + DIRECTIONS[i].vy];  
      }
      catch (ArrayIndexOutOfBoundsException e) {}
    }
    
    FringeStrategy strategy = controller.getFringeStrategy();
    for (int i = 0; i < successors.length; i++) {
      // at edges there might be one missing, and also don't double back to a node already on this path
      if (successors[i] != null && !isAncestor(successors[i])) {
        strategy.addToFringe(this, new MoveToNode(controller, node, successors[i]));
        successors[i].setH(heuristic.getValue(successors[i], controller.getGoal()));
        successors[i].setFringed();
      }
    }
    
  }
}

public abstract class Heuristic {
  protected boolean allowDiagonals;
  
  public boolean allowsDiagonals() {
    return allowDiagonals;
  }
  
  public void setAllowDiagonals(boolean b) {
    allowDiagonals = b;
  }
  
  public abstract float getValue(AStarNode fromNode, AStarNode toNode);
  
  public float getCost(MoveToNode moveToNode) {
    CartesianNode fromQuad = (CartesianNode)moveToNode.getFromNode(), destQuad = (CartesianNode)moveToNode.getToNode();
    if (fromQuad == null) {// at start only
      return destQuad.getBaseCost();
    }
    
    int moves = Math.abs(fromQuad.getCol() - destQuad.getCol()) + Math.abs(fromQuad.getRow() - destQuad.getRow());
    
    if (moves == 1) {
      return destQuad.getBaseCost();
    }
    else if (allowDiagonals && moves == 2) {
      return destQuad.getBaseCost() == 1f ? 1.414214f : destQuad.getBaseCost(); // a shade over square root of 2
    }
    else {
      throw new IllegalStateException("heuristic can only evaluate cost for a move of 1 unless diagonals allowed");
    } 
  }  
}

public class ManhattenHeuristic extends Heuristic {
  public float getValue(AStarNode fromNode, AStarNode toNode) {
    CartesianNode fromQuad = (CartesianNode)fromNode, destQuad = (CartesianNode)toNode;
    int cols = Math.abs(fromQuad.getCol() - destQuad.getCol());
    int rows = Math.abs(fromQuad.getRow() - destQuad.getRow());
    
    if (allowDiagonals) {
      // break the heuristic into a diagonal part and a straight part and add them together
      int diagSize = Math.min(cols, rows);
      float diagonalPart = diagSize == 0 
        ? 0 // just a straight line, so nothing here 
        : distance(0, 0, diagSize, diagSize);
      return diagonalPart + Math.max(cols, rows) - diagSize;
    }
    else {
      return cols + rows;
    }
  }
}

public class EuclidianHeuristic extends Heuristic {
  public float getValue(AStarNode fromNode, AStarNode toNode) {
    CartesianNode fromQuad = (CartesianNode)fromNode, destQuad = (CartesianNode)toNode;
    //TODO this is a bit incomplete, it does more or less work now, but much better with euclidian if diagonals are allowed
    return distance(fromQuad.getCol(), fromQuad.getRow(), destQuad.getCol(), destQuad.getRow());
  }
}

// processing has problems with enums, so make a vector class for it instead
class Vector {
  int vx, vy;
  Vector(int vx, int vy) { this.vx = vx; this.vy = vy; }
}

float distance(float x1, float y1, float x2, float y2) {
  float vx = x2 - x1;
  float vy = y2 - y1;
  return (float)Math.sqrt(vx * vx + vy * vy);
}



