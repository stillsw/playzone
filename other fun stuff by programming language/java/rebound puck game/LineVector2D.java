package firstReboundGame;

/*
 * simply a vector that has a location
 */
public class LineVector2D extends Vector2D
{
	protected static final LineVector2D ZERO_LINE_VECTOR = new LineVector2D(0.0, 0.0, 0.0, 0.0);

	private Location2D location;
	private Location2D endLocation;
	
	public LineVector2D(double x, double y, double vx, double vy) {
		super(vx, vy);
		location = new Location2D(x, y);
		endLocation = new Location2D(location.getX() + this.direction[VX], location.getY() + this.direction[VY]);
	}
	public LineVector2D(Location2D start, double vx, double vy) {
		super(vx, vy);
		location = start;
		endLocation = new Location2D(location.getX() + this.direction[VX], location.getY() + this.direction[VY]);
	}
	public LineVector2D(Location2D start, Location2D end) {
		super(start, end);
		location = start;
		endLocation = end;
	}
	public LineVector2D(Location2D start, Vector2D vector) {
		super(vector.getVx(), vector.getVy());
		location = start;
		endLocation = new Location2D(location.getX() + this.direction[VX], location.getY() + this.direction[VY]);
	}
	// called from clone
	private LineVector2D(LineVector2D vector, double length) {
		super(vector.getVx(), vector.getVy(), length);
		location = vector.getLocation().clone();
		endLocation = vector.getEndLocation().clone();
	}
	public LineVector2D getSubtraction(Vector2D v2) { 
		return new LineVector2D(location.getX(), location.getY(), direction[VX] - v2.direction[VX], direction[VY] - v2.direction[VY]); 
	}
	public LineVector2D clone() {
		return new LineVector2D(this, length);
	}
	public void setLength(double len) {
		super.setLength(len);
		recalc();
	}
	protected void recalc() {
		super.recalc();
		endLocation.setLocation(location.getX() + this.direction[VX], location.getY() + this.direction[VY]);
	}
	public void reverseLine() {
		super.reverseDirection();
		location.setLocation(endLocation.getX(), endLocation.getY());
		recalc();
	}
	// apply the vector to the line, ie. move it to end and update
	public void advancePosition() {
		location.setLocation(location.getX() + this.direction[VX], location.getY() + this.direction[VY]);
		recalc();
	}
	public void changeLocation(double newX, double newY) {
		location.setLocation(newX, newY);
		recalc();
	}
	public void changeLocation(Location2D newLoc) {
		location.setLocation(newLoc.getX(), newLoc.getY());
		recalc();
	}
	public void changeDirectionToLocation(Location2D toPos) { 
		direction[VX] = toPos.getX() - location.getX(); 
		direction[VY] = toPos.getY() - location.getY(); 
		recalc();
	}
	public Location2D getLocation() { return location; }
	public Location2D getEndLocation() { return endLocation; }

//	public LineVector2D getScaledVector(double scalar) {
//		// calc unit vectors
//		double vxU = this.direction[VX] / this.length;
//		double vyU = this.direction[VY] / this.length;
//		return new LineVector2D(this.location, scalar * vxU, scalar * vyU);
//	}

	public Location2D getPointOnLine(double distanceFromStart) {
		// calc unit vectors
		double vxU = this.direction[VX] / this.length;
		double vyU = this.direction[VY] / this.length;
		Location2D start = this.getLocation();
		return new Location2D(start.getX() + distanceFromStart * vxU, start.getY() + distanceFromStart * vyU);
	}
	public LineVector2D getBisectedNormal(Location2D atPoint) {
		Vector2D nV = this.getRightNormal();
		return new LineVector2D(atPoint, nV);
	}
	public LineVector2D getPrettyBisectedNormal(Location2D atPoint, double length) {
		LineVector2D nV = this.getBisectedNormal(atPoint);
		nV.scale(length / 2);
		nV.reverseLine();
		nV.scale(length);
		return nV;
	}
	
	/*
	 * To get the line nearest p1 on this vector
	 */
	public LineVector2D getShortestLineToPoint(Location2D p1) {
		Location2D startPoint = this.getLocation();
		Vector2D v3 = new Vector2D(startPoint, p1);
		LineVector2D moveBackVector = null;
		// find dot product between vector v3 and the line obstacle
		double dp = v3.getDotProduct(this);
		// closer to the starting point if dp is -ve so use v3
		if(dp < 0.0)
			moveBackVector = new LineVector2D(startPoint, v3);
		// otherwise see if closer to the end point
		else {
			Location2D endPoint = this.getEndLocation();
			Vector2D v4 = new Vector2D(endPoint, p1);
			// again dp between v4 and line obstacle
			dp = v4.getDotProduct(this);
			// hits end point first if dp +ve
			if(dp > 0.0) 
				moveBackVector = new LineVector2D(endPoint, v4);
			else 
				moveBackVector = new LineVector2D(startPoint.getAddition(v3.getProjectionOntoVector(this)), p1);
		}
		return moveBackVector;
	}
	
	/**
	 * called when have already collided and want to know where impact happened, 
	 * so ignore whether it's future or past
	 * @param p1
	 * @param v2 LineVector2D the Line of the obstacle we're colliding with
	 * @param v2NormalLine the normal of v2 to the moving object (puck or disk)
	 * 						note, it moves in that direction 
	 * @param includeForwards
	 * @param radius
	 * @return
	 */
	public Location2D getAdjustedIntersectionPoint(Location2D p1, LineVector2D v2, LineVector2D v2NormalLine, double radius, double impactDistance) {
		Location2D v2StartPoint = v2.getLocation();
		Vector2D v3 = new Vector2D(p1, v2StartPoint);
		double ratioT = getIntersectionRatio(v2, v3);
		Location2D ip = new Location2D(p1.getX() + direction[VX] * ratioT, p1.getY() + direction[VY] * ratioT);
		if (impactDistance < 0.0) { // gone beyond contact (most cases will be)
			// radius is the length of adjacent side of a right angle triangle
			// get cos of the angle between it and original bearing = dp of unit vectors 
			// divide cos by adjacent side to get hypotenuse
			Vector2D adjacentV = v2NormalLine.getUnitVector();
			// reverse direction of the normal to get the correct angle between it and the bearing ie. both going same way
			adjacentV.reverseDirection();
			Vector2D bearingV = this.getUnitVector();
			double dp = bearingV.getDotProduct(adjacentV);
			bearingV.reverseDirection();
			bearingV.setLength(radius / dp);
			ip.addVector(bearingV);
		}
		return ip;
	}
	
	public Location2D getIntersectionPoint(LineVector2D v2) {
		Location2D v2StartPoint = v2.getLocation();
		Vector2D v3 = new Vector2D(this.location, v2StartPoint);
		double ratioT = getIntersectionRatio(v2, v3);
		Location2D ip = new Location2D(this.location.getX() + direction[VX] * ratioT, this.location.getY() + direction[VY] * ratioT);
		return ip;
	}
	
	public String toString() {
		return "x,y vx,vy = "+location+" "+super.toString();
	}
	
}