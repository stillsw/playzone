import java.awt.geom.*;


public class Location2D
{
	private double x, y;
	
	public Location2D(double x, double y) {
		this.x = x;
		this.y = y;
	}
	public Location2D(Point2D point2d) {
		this.x = point2d.getX();
		this.y = point2d.getY();
	}
	
	public Point2D.Double getPoint2D() { return new Point2D.Double(this.x, this.y); } 
	public double getX() { return this.x;	}
	public double getY() { return this.y;	}
	public void setLocation(double x, double y) { this.x = x; this.y = y; }
	public void addVector(Vector2D v2) { this.x += v2.getVx(); this.y += v2.getVy(); }
	public Location2D getAddition(Vector2D v2) { return new Location2D(this.x + v2.getVx(), this.y + v2.getVy()); }
	/*
	 * adds the vector to this location and puts the result in the location passed as l2
	 */
	public void getAddition(Vector2D v2, Location2D l2) {
		l2.setLocation(this.x + v2.getVx(), this.y + v2.getVy()); 
	}
	public Location2D getSubtraction(Vector2D v2) { 
		return getSubtraction(v2.getVx(), v2.getVy()); 
	}
	public Location2D getSubtraction(double vx2, double vy2) { 
		return new Location2D(this.x - vx2, this.y - vy2); 
	}
	public String toString() {
		return ""+this.x+","+this.y;
	}
	public Location2D clone() { return new Location2D(this.x, this.y); }
	
	
/// temporarily probably, compute distance with Point2D
//	public double distance(Location2D l2) {
//		return getPoint2D().distance(l2.getPoint2D());
//	}
	
	public double getDistance(double x, double y) {
		double xLen = x - this.x;
		double yLen = y - this.y;		
		return Math.sqrt(xLen * xLen + yLen * yLen);
	}
	public double getDistance(Location2D l2) {
		return getDistance(l2.getX(), l2.getY());
	}
	public double getDistanceSquared(double x, double y) {
		double xLen = x - this.x;
		double yLen = y - this.y;		
		return xLen * xLen + yLen * yLen;
	}
	public double getDistanceSquared(Location2D l2) {
		return getDistanceSquared(l2.getX(), l2.getY());
	}
	
	
	
	
	
	
	
}
