
public class Vector2D
{
	public static final Vector2D ZERO_VECTOR = new Vector2D(0,0);
	protected double length = Double.NaN;
	protected double vx, vy;

	public Vector2D(double vx, double vy) {
		this.vx = vx;
		this.vy = vy;
		setLength();
	}
	// called by clone only, no need to calc the length
	protected Vector2D(double vx, double vy, double length) {
		this.vx = vx;
		this.vy = vy;
		this.length = length;
	}
	public Vector2D(Location2D start, Location2D end) {
		this(end.getX() - start.getX(), end.getY() - start.getY());
	}
	
	public Vector2D(Location2D start, double endx, double endy) {
		this(endx - start.getX(), endy - start.getY());
	}
	
	private void setLength() { length = Math.sqrt(this.vx * this.vx + this.vy * this.vy); }
	public void setLength(double len) {
		// don't make a unit vector internally
		double thisLen = this.getLength();
		double vxU = this.vx / thisLen;
		double vyU = this.vy / thisLen;
		this.vx = vxU * len;
		this.vy = vyU * len;
		this.length = len;
	}
	public double getVx() { return this.vx;	}
	public double getVy() { return this.vy;	}
	/**
	 * set length only when needed, many operations don't need it so avoid lots of sqrt calls for nothing
	 * @return
	 */
	public double getLength() {
		if (Double.isNaN(length))
			setLength();
		
		return length; 
	}
	public void reverseVx() { this.vx = -this.vx; recalc(); }
	public void reverseVy() { this.vy = -this.vy; recalc(); }
	public void reverseDirection() { this.vx = -this.vx; this.vy = -this.vy; }
	public Vector2D getReversedVector() { 
		Vector2D reversed = this.clone();
		reversed.reverseDirection();
		return reversed;
	}
	public void changeDirection(double x, double y) { 
		this.vx = x; 
		this.vy = y; 
		recalc();
	}
	protected void recalc() {
		// length is NaN until needed (see getLength())
		length = Double.NaN;
	}
	public Vector2D getUnitVector() { return new Vector2D(this.vx / getLength(), this.vy / getLength()); }
	public Vector2D getRightNormal() { return new Vector2D(-this.vy, this.vx); }
	public Vector2D getLeftNormal() { return new Vector2D(this.vy, -this.vx); }
	public Vector2D getAddition(Vector2D v2) { return new Vector2D(this.vx + v2.vx, this.vy + v2.vy); }
	public Vector2D getSubtraction(Vector2D v2) { return new Vector2D(this.vx - v2.vx, this.vy - v2.vy); }
	public Location2D getEndLocation(Location2D startLoc) {
		return new Location2D(this.vx + startLoc.getX(), this.vy + startLoc.getY());
	}
	public void scale(double scalar) {
		// don't make a unit vector internally
		double vxU = this.vx / this.getLength();
		double vyU = this.vy / this.getLength();		
		this.changeDirection(scalar * vxU, scalar * vyU);
	}
	public Vector2D getScaledVector(double scalar) {
		Vector2D scaledV = this.getUnitVector();
		scaledV.scale(scalar);
		return scaledV;
	}
	public static Vector2D getScaledVector(Vector2D ofUnitVector, double scalar) {
		return new Vector2D(scalar * ofUnitVector.vx, scalar * ofUnitVector.vy);
	}
	public double getDotProduct(Vector2D v2) { 
		return this.vx * v2.vx + this.vy * v2.vy; 
	}
	public double getUnitsDotProduct(Vector2D v2) { 
		// don't make unit vectors internally
		double vxU = this.vx;
		double vyU = this.vy;
		double len = getLength();
		if (Double.compare(len, 1.0) != 0) {
			vxU /= len;
			vyU /= len;
		}

		double vx2U = v2.vx;
		double vy2U = v2.vy;
		len = v2.getLength();
		if (Double.compare(len, 1.0) != 0) {
			vx2U /= len;
			vy2U /= len;
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
		double len = this.getLength();
		double vxU = this.vx / len;
		double vyU = this.vy / len;

		double vx2U = ontoVector.vx;
		double vy2U = ontoVector.vy;
		double otherlen = ontoVector.getLength();
		if (Double.compare(otherlen, 1.0) != 0) {
			vx2U = ontoVector.vx / otherlen;
			vy2U = ontoVector.vy / otherlen;
		}
		double dp = vxU * vx2U + vyU * vy2U;
		double scalar = len * dp;
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
		return -this.vy * v2.vx + this.vx * v2.vy; 
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
		double vxU = this.vx / this.getLength();
		double vyU = this.vy / this.getLength();

		double vx2U = compareWith.vx;
		double vy2U = compareWith.vy;
		if (Double.compare(compareWith.getLength(), 1.0) != 0) {
			vx2U = compareWith.vx / compareWith.getLength();
			vy2U = compareWith.vy / compareWith.getLength();
		}
		double dp = vxU * vx2U + vyU * vy2U;
		return (Double.compare(Math.abs(dp), 1.0) == 0);
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
		return new Vector2D(proj1.vx + proj2.vx, proj1.vy + proj2.vy);		
	}
	
	public Vector2D clone() { return new Vector2D(this.vx, this.vy, length); }
	
	public String toString()
	{
		return ""+this.vx+","+this.vy;
	}
	
	
	
	
	
	
	
	
	
	
}
