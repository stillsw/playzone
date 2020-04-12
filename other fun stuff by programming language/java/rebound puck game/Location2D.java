package firstReboundGame;

import java.awt.geom.*;

public class Location2D
{
	public static final int X = 0;
	public static final int Y = 1;
	
	private double[] point = new double[2];
	
	public Location2D(double x, double y) {
		point[X] = x;
		point[Y] = y;
	}
	public Location2D(Point2D point2d) {
		point[X] = point2d.getX();
		point[Y] = point2d.getY();
	}
	
	public Point2D.Double getPoint2D() { return new Point2D.Double(point[X], point[Y]); } 
	public double getX() { return point[X];	}
	public double getY() { return point[Y];	}
	public void setLocation(double x, double y) { point[X] = x; point[Y] = y; }
	public void addVector(Vector2D v2) { point[X] += v2.getVx(); point[Y] += v2.getVy(); }
	public Location2D getAddition(Vector2D v2) { return new Location2D(point[X] + v2.getVx(), point[Y] + v2.getVy()); }
	/*
	 * adds the vector to this location and puts the result in the location passed as l2
	 */
	public void getAddition(Vector2D v2, Location2D l2) {
		l2.setLocation(point[X] + v2.getVx(), point[Y] + v2.getVy()); 
	}
	public Location2D getSubtraction(Vector2D v2) { return new Location2D(point[X] - v2.getVx(), point[Y] - v2.getVy()); }
	public String toString() {
		return ""+point[X]+","+point[Y];
	}
	public Location2D clone() { return new Location2D(point[X], point[Y]); }
	
	
/// temporarily probably, compute distance with Point2D
//	public double distance(Location2D l2) {
//		return getPoint2D().distance(l2.getPoint2D());
//	}
	
	public double getDistance(double x, double y) {
		double xLen = x - point[X];
		double yLen = y - point[Y];		
		return Math.sqrt(xLen * xLen + yLen * yLen);
	}
	public double getDistance(Location2D l2) {
		return getDistance(l2.getX(), l2.getY());
	}
	public double getDistanceSquared(double x, double y) {
		double xLen = x - point[X];
		double yLen = y - point[Y];		
		return xLen * xLen + yLen * yLen;
	}
	public double getDistanceSquared(Location2D l2) {
		return getDistanceSquared(l2.getX(), l2.getY());
	}
	
	
	
	
	
	
	
}