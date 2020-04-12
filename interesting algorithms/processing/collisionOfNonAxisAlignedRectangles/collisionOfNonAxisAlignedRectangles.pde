static final int COLLISION_AVOID_STEER_GENTLE_RIGHT = 0;
static final int COLLISION_AVOID_STEER_GENTLE_LEFT = 1;
static final int COLLISION_AVOID_STEER_HARD_RIGHT = 2;
static final int COLLISION_AVOID_STEER_HARD_LEFT = 3;
static final int COLLISION_AVOID_NONE = 4;

// vector class for vectors and also points
class Vec2D {
  float x, y;
  Vec2D(float x, float y) {
    this.x = x;
    this.y = y;
  }  
  
  public String toString() {
    return "[x="+x+",y="+y+"]";
  }
}

class Poly2D {
  int id;
  Vec2D[] verts;
  Vec2D centre = new Vec2D(0f, 0f), 
        origin = new Vec2D(0f, 0f), // mimic drone centre on bottom edge of detection box
        dir = new Vec2D(0f, 0f),    // vector of direction of movement (to be able to calc deflect angles
        dirR = new Vec2D(0f, 0f),   // vector for the arrow right line
        dirL = new Vec2D(0f, 0f);   // left line
  Poly2D(int id, Vec2D ... corners) {
    this.id = id;
    if (corners.length != 4) { // only catering for finding centre by crossing diags
      throw new IllegalArgumentException("Poly2D is for 4 corners only");
    }
    verts = corners;
    calcCentre();
    calcOrigin();
  } 
  
  void calcCentre() { // based on diagonals intersecting
//    centre.x = (Math.min(verts[0].x, verts[2].x) + Math.max(verts[0].x, verts[2].x)) / 2f; 
//    centre.y = (Math.min(verts[1].y, verts[3].y) + Math.max(verts[1].y, verts[3].y)) / 2f; 
    centre.x = (verts[0].x + verts[2].x) / 2f; 
    centre.y = (verts[0].y + verts[2].y) / 2f; 
  }
  
  void calcOrigin() { // mid-point between verts 3 and 4
    origin.x = (verts[2].x + verts[3].x) / 2f; 
    origin.y = (verts[2].y + verts[3].y) / 2f; 
    
    dir.x = (centre.x - origin.x) / 2f;
    dir.y = (centre.y - origin.y) / 2f;
    dirR.x = (centre.x - verts[2].x) / 8f;
    dirR.y = (centre.y - verts[2].y) / 8f;
    dirL.x = (centre.x - verts[3].x) / 8f;
    dirL.y = (centre.y - verts[3].y) / 8f;
  }
  
  float getMinX() {
    float min = Float.MAX_VALUE;
    for (int i = 0; i < verts.length; i++) {
      if (verts[i].x < min) {
        min = verts[i].x;
      }
    }
    return min;
  }
  
  float getMaxX() {
    float max = Float.MIN_VALUE;
    for (int i = 0; i < verts.length; i++) {
      if (verts[i].x > max) {
        max = verts[i].x;
      }
    }
    return max;
  }
  
  float getMinY() {
    float min = Float.MAX_VALUE;
    for (int i = 0; i < verts.length; i++) {
      if (verts[i].y < min) {
        min = verts[i].y;
      }
    }
    return min;
  }
  
  float getMaxY() {
    float max = Float.MIN_VALUE;
    for (int i = 0; i < verts.length; i++) {
      if (verts[i].y > max) {
        max = verts[i].y;
      }
    }
    return max;
  }
  
  void draw() {
    int prev = verts.length -1;
    for (int i = 0; i < verts.length; i++) {
      line(verts[i].x, verts[i].y, verts[prev].x, verts[prev].y);
      prev = i;
    }
    ellipse(centre.x, centre.y, 5, 5);
    ellipse(origin.x, origin.y, 5, 5);
    line(origin.x, origin.y, origin.x + dir.x, origin.y + dir.y);
    
    // direction arrow
    line(origin.x + dir.x, origin.y + dir.y, origin.x + dir.x - dirR.x, origin.y + dir.y - dirR.y);
    line(origin.x + dir.x, origin.y + dir.y, origin.x + dir.x - dirL.x, origin.y + dir.y - dirL.y);
    
    fill(0);
    text("rect "+id, centre.x + 15, centre.y - 15);
    if (isColliding && id == 1) {
      text("steering to avoid collision : "+collisionAvoidDebug, centre.x + 15, centre.y + 15);
    }
    noFill();
  }
  
  public String toString() {
    String res = "rect orgn="+origin+" centre="+centre;
    for (int i = 0; i < verts.length; i++) {
      res = res + " vert "+i+" = "+verts[i];
    }
    return res;
  }
}

Poly2D[] rects;
boolean isDraggingPoint = false, isDragCentre, isColliding = false;
Poly2D rectForDrag;
Vec2D vertForDrag, dragOffset;
String collisionAvoidDebug;

void setup() {
  size(800, 800);
  rectMode(CENTER);
  noFill();
  rects = new Poly2D[2];
  rects[0] = new Poly2D(1, new Vec2D(20, 20), new Vec2D(200, 20), new Vec2D(200, 300), new Vec2D(20, 300));
  rects[1] = new Poly2D(2, new Vec2D(320, 20), new Vec2D(500, 20), new Vec2D(500, 300), new Vec2D(320, 300));  
}

void draw() {
  background(255);

  if (mousePressed && isDraggingPoint) {
    if (rectForDrag != null && vertForDrag != null) {
      calcPositionOfDrag();
    }
    else {
      println("unusable drag, even though have point");
    }
  }

  if (isColliding) {
    stroke(255, 0, 0);
  }
  else {
    stroke(0);
  }
  
  for (Poly2D rect : rects) {
    rect.draw();
  }
  
  if (vertForDrag != null) {
    color(255, 255, 0);
    ellipse(vertForDrag.x, vertForDrag.y, 20, 20);
  }
}

void mousePressed() {
  detectPointForPress();
  if (isDraggingPoint) {
    dragOffset = new Vec2D(mouseX - vertForDrag.x, mouseY - vertForDrag.y);
  }
}

void mouseReleased() {
  if (isDraggingPoint) {
    calcPositionOfDrag();
    isDraggingPoint = false;
    vertForDrag = null;
    rectForDrag = null;
    calcCollision();
  }
}

void calcPositionOfDrag() {
  if (isDragCentre) {
    dragRectToMouse();
  }
  else {
    dragRectToAngle();
  }
}

void dragRectToAngle() {
  // get angle of change and apply it to all the verts
  float oldAngle = atan2(vertForDrag.y - rectForDrag.centre.y, vertForDrag.x - rectForDrag.centre.x);  
  float newAngle = atan2(mouseY - dragOffset.y - rectForDrag.centre.y, mouseX - dragOffset.x - rectForDrag.centre.x);
  float diff = newAngle - oldAngle;
  
  for (Vec2D vert : rectForDrag.verts) {
    // get the current angle of the vert and calc the new angle 
    float vertOldAngle = atan2(vert.y - rectForDrag.centre.y, vert.x - rectForDrag.centre.x);
    float vertNewAngle = vertOldAngle + diff;
    // get the radius from the centre point, so can spin it
    float r = getLength(new Vec2D(vert.x - rectForDrag.centre.x, vert.y - rectForDrag.centre.y));
    float rx = r * cos(vertNewAngle);
    float ry = r * sin(vertNewAngle);
    vert.x = rectForDrag.centre.x + rx;
    vert.y = rectForDrag.centre.y + ry;
  }
  
  rectForDrag.calcOrigin();
}

float getLength(Vec2D v) {
  return (float)Math.sqrt(getDot(v, v));
}

float getDot(Vec2D a, Vec2D b) {
    return a.x*b.x + a.y*b.y;
}

void dragRectToMouse() {
  Vec2D oldCentreV = new Vec2D(vertForDrag.x, vertForDrag.y); // copy the centre location (vertForDrag is one of the centres)
  vertForDrag.x = mouseX - dragOffset.x; // move centre relative to mouse (dragRect only applies to centre)
  vertForDrag.y = mouseY - dragOffset.y;
  
  for (Vec2D vert : rectForDrag.verts) {
    vert.x = vertForDrag.x - oldCentreV.x + vert.x;
    vert.y = vertForDrag.y - oldCentreV.y + vert.y;
  }

  rectForDrag.calcOrigin();
}

void detectPointForPress() {
  
  for (Poly2D rect : rects) {
    for (Vec2D vert : rect.verts) {
      if (distToMouse(vert) < 20) {
        isDraggingPoint = true;
        isDragCentre = false;
        rectForDrag = rect;
        vertForDrag = vert;
        return;
      }
    }
    // not a corner, try centre
    if (distToMouse(rect.centre) < 20) {
      isDraggingPoint = true;
      isDragCentre = true;
      rectForDrag = rect;
      vertForDrag = rect.centre;        
      return;
    }
  }
}

float distToMouse(Vec2D vec) {
  return dist(vec.x, vec.y, mouseX, mouseY);
}

void calcCollision() {
  // the meat of the thing, broad phase then narrow
  
  // 1. in a quad based design, first check the quads are far enough apart that no chance of touching
  
  // 2. now try to rule out based on where they can't overlap
  
  // right-most point of one is left of the left-most point of the other, and vice versa
  if (!overlappingHorizontallyAABB(rects[0], rects[1])) {
    isColliding = false;
    println("no collision because AABBs don't coincide");
    return;
  }
  
  if (!overlappingVerticallyAABB(rects[0], rects[1])) {
    isColliding = false;
    println("no collision because AABBs don't coincide");
    return;
  }
  
  // got this far, this is really narrow phase, their bounding boxes do overlap, so need to find if
  // there is a separating line between them... this only works for non-concave shapes 
  // see : http://gamemath.com/2011/09/detecting-whether-two-convex-polygons-overlap/
  if (!convexPolygonOverlap(rects[0], rects[1])) {
    isColliding = false;
    return;
  }
  
  // anything left, must be colliding
  isColliding = true;
  
  // figure out where an avoiding vector should point
  if (isColliding) {
    calcAvoidVector();
  }
}

void calcAvoidVector() {
  // assume the mover that is planning a trajectory is rect 1 and is trying to avoid rect 2  
  // compare rect 1's origin to rect 2's centre (get dot product of the vector and the direction of rect 1)
  // NOTE : if this is done every move, there could be > 1 steerings per collision... meaning either make them
  // less severe or don't check every move

  Vec2D vOrgnToCentre = new Vec2D(rects[1].centre.x - rects[0].origin.x, rects[1].centre.y - rects[0].origin.y);
  Vec2D vOrgnToOrgn = new Vec2D(rects[1].origin.x - rects[0].origin.x, rects[1].origin.y - rects[0].origin.y);
  Vec2D vNormalOfDir = new Vec2D(-rects[0].dir.y, rects[0].dir.x);
  
  float dpFrontOrBack = getDot(rects[0].dir, vOrgnToCentre);
  float dpRightOrLeft = getDot(vNormalOfDir, vOrgnToCentre);
  float dpDir = getDot(rects[0].dir, rects[1].dir);
  float dpTravellingRightOrLeft = getDot(vNormalOfDir, rects[1].dir);
  float dpOriginRightOrLeft = getDot(vNormalOfDir, vOrgnToOrgn);
  
  println("vec to avoid from rect1 to rect2 = "+vOrgnToCentre+" dpPlace1="+dpFrontOrBack+" dpDir="+dpDir);
  boolean isColliderBehind = dpFrontOrBack < 0;
  boolean isColliderRight = dpRightOrLeft > 0;
  boolean isColliderSameDir = dpDir > 2000; // either to right or left but roughly same dir
  boolean isColliderOrthoDir = !isColliderSameDir && dpDir > -2000;
  boolean isColliderTravelingLeft = isColliderBehind ? dpTravellingRightOrLeft > 0 : dpTravellingRightOrLeft < 0;
  boolean willCrossInFront = !isColliderSameDir && 
            // either (centre) is on the right and travelling left, or on the left and travelling right            
            ((isColliderRight && isColliderTravelingLeft) || (!isColliderRight && !isColliderTravelingLeft)
            // or the collider has origin one side and centre the other (ie. directly crossing the dir)
            || (dpRightOrLeft > 0 && dpOriginRightOrLeft < 0) || (dpRightOrLeft < 0 && dpOriginRightOrLeft > 0)  
            );
  println("rect2 centre is "
      +(isColliderBehind ? "BEHIND" : "IN FRONT")
      +(isColliderRight ? " ON RIGHT SIDE" : " ON LEFT SIDE")
      +(isColliderSameDir ? " GOING FORWARDS" : isColliderOrthoDir ? " GOING SIDEWAYS" :  " GOING BACKWARDS")
      +(isColliderTravelingLeft ? " RIGHT->LEFT" : " LEFT->RIGHT")
      +(willCrossInFront ? " HEADING RIGHT AT US" : "")
      +(willCrossInFront && isColliderOrthoDir ? " POSSIBLE T-BONE" : " NOT A T-BONE")
      +" of rect1 origin");
      
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
  
  switch (collisionAvoidance) {
  case COLLISION_AVOID_STEER_GENTLE_RIGHT : collisionAvoidDebug = "Steer gently right"; break;
  case COLLISION_AVOID_STEER_GENTLE_LEFT : collisionAvoidDebug = "Steer gently left"; break;
  case COLLISION_AVOID_STEER_HARD_RIGHT : collisionAvoidDebug = "Steer hard right"; break;
  case COLLISION_AVOID_STEER_HARD_LEFT : collisionAvoidDebug = "Steer hard_left"; break;
  default : collisionAvoidDebug = "No avoidance";
  }
}


boolean convexPolygonOverlap(Poly2D rect1, Poly2D rect2) {
  // try each poly against the other to find a separating axis, there is one
  // it doesn't overlap
  
  println("testing for separating axis from rect 1 to rect 2");
  if (hasSeparatingAxis(rect1, rect2)) {
    println("\tfound separating axis");
    return false;
  }
  
  println("testing for separating axis from rect 2 to rect 1");
  if (hasSeparatingAxis(rect2, rect1)) {
    println("\tfound separating axis");
    return false;
  }
  
  return true;
}

boolean hasSeparatingAxis(Poly2D srcRect, Poly2D destRect) {
  Vec2D[] verts = srcRect.verts;
  int prev = verts.length -1;
  for (int i = 0; i < verts.length; i++) {
    println("\ttesting edge = "+i);
    Vec2D edge = new Vec2D(verts[i].x - verts[prev].x, verts[i].y - verts[prev].y);
    // the normal of the edge could be a separating axis
    Vec2D normal = new Vec2D(edge.y, -edge.x); 
    // project onto this normal from each rect to get the min/max 
    // onto itself - this can be stored for each movement rather than every compare 
    // eg. where in a single tick a poly is compared against many other polys
    // it changes by position though, so 
    float[] srcRectExtents = getProjectionExtents(srcRect, normal);
    println("\t\tsrcExtents min/max = "+srcRectExtents[0]+"/"+srcRectExtents[1]);
    // from the src edge to the dest rect, this has to be recalced each time
    float[] destRectExtents = getProjectionExtents(destRect, normal);
    // min is 0, max is 1
    if (srcRectExtents[1] < destRectExtents[0]
    || destRectExtents[1] < srcRectExtents[0]) {
      return true;
    }
    prev = i;
  }
  
  return false;
}

float[] getProjectionExtents(Poly2D poly, Vec2D normal) {

  // Initialize extents to a single point, the first vertex
  float outMin, outMax;
  outMin = outMax = getDot(normal, poly.verts[0]);
 
  // scan all the rest, growing extents to include them
  for (int i = 1; i < poly.verts.length; i++) {
    float d = getDot(normal, poly.verts[i]);
    if (d < outMin) {
      outMin = d;
    }
    else if (d > outMax) {
      outMax = d;
    }
  }
  
  return new float[] { outMin, outMax };
}

boolean overlappingHorizontallyAABB(Poly2D rect1, Poly2D rect2) {
  // axis-aligned bounding box overlaps horizontally
  return !(rect1.getMinX() > rect2.getMaxX() || rect1.getMaxX() < rect2.getMinX());
}

boolean overlappingVerticallyAABB(Poly2D rect1, Poly2D rect2) {
  // axis-aligned bounding box overlaps vertically
  return !(rect1.getMinY() > rect2.getMaxY() || rect1.getMaxY() < rect2.getMinY());
}

