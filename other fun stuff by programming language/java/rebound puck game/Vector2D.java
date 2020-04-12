package firstReboundGame;

//import java.awt.geom.*;

public class Vector2D
{
	public static final double LONG_DISTANCE = 99999999.99;
	public static final Vector2D ZERO_VECTOR = new Vector2D(0,0);
	protected static final int VX = 0;
	protected static final int VY = 1;
	
	protected double[] direction = new double[2];
	protected double length = 0.0;

	/*
	 * allow VolatileVector2D to create without a length
	 */
	protected Vector2D() {}
	
	public Vector2D(double vx, double vy) {
		direction[VX] = vx;
		direction[VY] = vy;
		setLength();
	}
	// called by clone only, no need to calc the length
	protected Vector2D(double vx, double vy, double length) {
		direction[VX] = vx;
		direction[VY] = vy;
		this.length = length;
	}
	public Vector2D(Location2D start, Location2D end) {
		this(end.getX() - start.getX(), end.getY() - start.getY());
	}
	
	public Vector2D(Location2D start, double endx, double endy) {
		this(endx - start.getX(), endy - start.getY());
	}
	
	private void setLength() { length = Math.sqrt(direction[VX] * direction[VX] + direction[VY] * direction[VY]); }
	public void setLength(double len) {
		// don't make a unit vector internally
		double vxU = this.direction[VX] / this.length;
		double vyU = this.direction[VY] / this.length;
		this.direction[VX] = vxU * len;
		this.direction[VY] = vyU * len;
		this.length = len;
	}
	public double getVx() { return direction[VX];	}
	public double getVy() { return direction[VY];	}
	public double getLength() { return length; }
	public void reverseX() { direction[VX] = -direction[VX]; }
	public void reverseY() { direction[VY] = -direction[VY]; }
	public void reverseDirection() { direction[VX] = -direction[VX]; direction[VY] = -direction[VY]; }
	public Vector2D getReversedVector() { 
		Vector2D reversed = this.clone();
		reversed.reverseDirection();
		return reversed;
	}
	public void changeDirection(double x, double y) { 
		direction[VX] = x; 
		direction[VY] = y; 
		recalc();
	}
	protected void recalc() {
		// place holder for subclass to change and also update itself
		setLength();
	}
	public Vector2D getUnitVector() { return new Vector2D(direction[VX] / length, direction[VY] / length); }
	public VolatileVector2D getVolatileUnitVector() { return new VolatileVector2D(direction[VX] / length, direction[VY] / length); }
	public Vector2D getRightNormal() { return new Vector2D(-direction[VY], direction[VX]); }
	public Vector2D getLeftNormal() { return new Vector2D(direction[VY], -direction[VX]); }
	public Vector2D getAddition(Vector2D v2) { return new Vector2D(direction[VX] + v2.direction[VX], direction[VY] + v2.direction[VY]); }
	public Vector2D getSubtraction(Vector2D v2) { return new Vector2D(direction[VX] - v2.direction[VX], direction[VY] - v2.direction[VY]); }
	public Location2D getEndLocation(Location2D startLoc) {
		return new Location2D(direction[VX] + startLoc.getX(), direction[VY] + startLoc.getY());
	}
	public void scale(double scalar) {
		// don't make a unit vector internally
		double vxU = this.direction[VX] / this.length;
		double vyU = this.direction[VY] / this.length;		
		this.changeDirection(scalar * vxU, scalar * vyU);
	}
	public Vector2D getScaledVector(double scalar) {
		Vector2D scaledV = this.getUnitVector();
		scaledV.scale(scalar);
		return scaledV;
	}
	public Vector2D getScaledVector(Vector2D ofUnitVector, double scalar) {
		return new Vector2D(scalar * ofUnitVector.direction[VX], scalar * ofUnitVector.direction[VY]);
	}
	public double getDotProduct(Vector2D v2) { 
		return direction[VX] * v2.direction[VX] + direction[VY] * v2.direction[VY]; 
	}
	public double getUnitsDotProduct(Vector2D v2) { 
		// don't make unit vectors internally
		double vxU = this.direction[VX];
		double vyU = this.direction[VY];
		if (Double.compare(this.length, 1.0) != Constants.DOUBLE_COMPARE_EQUAL) {
			vxU /= this.length;
			vyU /= this.length;
		}

		double vx2U = v2.direction[VX];
		double vy2U = v2.direction[VY];
		if (Double.compare(v2.length, 1.0) != Constants.DOUBLE_COMPARE_EQUAL) {
			vx2U /= v2.length;
			vy2U /= v2.length;
		}

		return vxU * vx2U + vyU * vy2U; 
	}
	
	public Vector2D getProjectionOntoVector(Vector2D ontoVector) { 
		// Formula for projection is      (kV = |w| (Wu . Vu) Vu)
		// project w onto v means take the length of w and find the place on v that is perpendicular to w
		// so this method is called from the vector that you want to project and pass the vector you want
		// to project it onto

//		// here's some code to test the projection
//		// we'll say ball 1 is at 0,0 and travelling along x-axis 6,0
//		// ball 2 is at 3,2 and not moving atm 0,0
//		Location2D ball1 = new Location2D(0,0);
//		Location2D ball2 = new Location2D(3,2);
//		Vector2D ball1V = new Vector2D(6,0);
//		Vector2D startPointsVector = new Vector2D(ball1, ball2); //3 along x and 2 along y
//		// try manual projection:
//		// setup
//		System.out.println("ball1 vector (v1) = "+ball1V+" startPointsVector (w) = "+startPointsVector);
//		// 1 get lengths
//		double ball1VLen = ball1V.getLength();
//		double spsVLen = startPointsVector.getLength();
//		System.out.println("step 1. get lengths: ball1 vector = "+ball1VLen+" startPoints vector ="+spsVLen);
//		// 2 get unit vectors
//		Vector2D ball1uv = ball1V.getUnitVector();
//		Vector2D spsuv = startPointsVector.getUnitVector();
//		System.out.println("step 2. get unit vectors: ball1 uv = "+ball1uv+" startPoints uv = "+spsuv);
//		// 3 get cos of angle between them (dot product of unit vectors)
//		double dp = ball1uv.getDotProduct(spsuv);
//		System.out.println("step 3. get dot product (cos of angle): dp = "+dp);
//		// 4 assemble the projection which gives the scalar(k) vector of ball1Vector
//		//             (kV = |w| (Wu . Vu) Vu)
//		//   since we project startPoints vector onto ball 1 movement vector
//		//   means vectors startPoints = W and ball 1 = V
//		double kLen = spsVLen * dp;getDotProduct
//		System.out.println("step 4. assemble formula scalar to apply to V = "+kLen);
//		// 5 get the vector that arrives at the desired point
//		double pvx = kLen*ball1uv.getX();
//		double pvy = kLen*ball1uv.getY();
//		System.out.println("step 5. vector to point on ball1 vector = "+pvx+","+pvy);
//
//		Vector2D scalarVectorForBall1again = startPointsVector.getProjectionOntoVector(ball1uv, true);
//		System.out.println("Verify method getProjection works  = "+scalarVectorForBall1again);

		// don't make unit vectors internally
		double vxU = this.direction[VX] / this.length;
		double vyU = this.direction[VY] / this.length;

		double vx2U = ontoVector.direction[VX];
		double vy2U = ontoVector.direction[VY];
		if (Double.compare(ontoVector.length, 1.0) != Constants.DOUBLE_COMPARE_EQUAL) {
			vx2U = ontoVector.direction[VX] / ontoVector.length;
			vy2U = ontoVector.direction[VY] / ontoVector.length;
		}
		double dp = vxU * vx2U + vyU * vy2U;
		double scalar = this.length * dp;
		return ontoVector.getScaledVector(scalar); 
	}
//	public Vector2D getProjectionWithUnitVectors(double projectVectorLen, Vector2D ontoVectorU) {
//		Vector2D thisUnitVector = this;
//		if (Double.compare(this.getLength(), 1.0) != 0)
//			thisUnitVector = this.getUnitVector();
//		double dp = thisUnitVector.getDotProduct(ontoVectorU);
//		double scalar = projectVectorLen * dp;
//		return ontoVectorU.getScaledVector(scalar); 
//	}
	
	public double getPerpProduct(Vector2D v2) { 
		// use v1 normal
		// pp = -v1.vy*v2.vx + v1.vx*v2.vy;
		return -direction[VY] * v2.direction[VX] + direction[VX] * v2.direction[VY]; 
	}
//	public double getIntersectionRatio(Location2D p1, Location2D p2, Vector2D v2) {
////		Vector2D v3 = new Vector2D(p1.getX() - p2.getX(), p1.getY() - p2.getY());
//		Vector2D v3 = new Vector2D(p2.getX() - p1.getX(), p2.getY() - p1.getY());
//		double pp1 = getPerpProduct(v2);
//		double pp2 = v3.getPerpProduct(v2);
//		return pp2/pp1;
//	}
	public double getIntersectionRatio(Vector2D v2, Vector2D v3) {
		double pp1 = getPerpProduct(v2);
		double pp2 = v3.getPerpProduct(v2);
		return pp2/pp1;
	}
	
	
	public boolean areParallel(Vector2D compareWith) {
		// calc unit vectors
		double vxU = this.direction[VX] / this.length;
		double vyU = this.direction[VY] / this.length;

		double vx2U = compareWith.direction[VX];
		double vy2U = compareWith.direction[VY];
		if (Double.compare(compareWith.length, 1.0) != 0) {
// CONSTANT
			vx2U = compareWith.direction[VX] / compareWith.length;
			vy2U = compareWith.direction[VY] / compareWith.length;
		}
		double dp = vxU * vx2U + vyU * vy2U;
		return (Double.compare(dp, 1.0) == 0 && Double.compare(dp, -1.0) == 0);
	}
	
	/**
	 * 
	 */
	public Vector2D getBounceVector(Vector2D v2, Vector2D normalV) {
		// with our movement vector. v2 is the wall (obstacle) vector. Left hand normal of v2:

		// projection of movement vector on the v2:
		Vector2D proj1 = this.getProjectionOntoVector(v2);

		// projection of movement vector on the v2 between v1 and v2's normal unit vector
		Vector2D proj2 = this.getProjectionOntoVector(normalV);

		// reverse projection on the normal:
		proj2.reverseDirection();

		// find new movement vector by adding up projections:
		return new Vector2D(proj1.direction[VX] + proj2.direction[VX], proj1.direction[VY] + proj2.direction[VY]);		
	}
	
	public Vector2D clone() { return new Vector2D(direction[VX], direction[VY], length); }
	
	public String toString()
	{
		return ""+direction[VX]+","+direction[VY];
	}
	
	
	
	
	
	
	
	
	
	
}