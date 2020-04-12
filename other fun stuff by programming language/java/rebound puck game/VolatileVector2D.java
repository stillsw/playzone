package firstReboundGame;


/*
 * Class which does not implement length, so is quick to use
 */
public class VolatileVector2D extends Vector2D
{
	protected double length = Double.NaN;
	
	public VolatileVector2D(double x, double y) {
		direction[VX] = x;
		direction[VY] = y;
	}
	public VolatileVector2D(Location2D start, Location2D end) {
		this(end.getX() - start.getX(), end.getY() - start.getY());
	}
	public VolatileVector2D(Vector2D vector) {
		this(vector.direction[VX], vector.direction[VY]);
	}
	public void setLength(double len) {throw new RuntimeException("VolatileVector2D.setLength(len): error attempt to use length"); }
	public double getVx() { return direction[VX];	}
	public double getVy() { return direction[VY];	}
	public double getLength() { throw new RuntimeException("VolatileVector2D.getLength: error attempt to use length");  }
	public VolatileVector2D getReversedVector() { 
		VolatileVector2D reversed = this.clone();
		reversed.reverseDirection();
		return reversed;
	}
	public void changeDirection(double x, double y) { 
		direction[VX] = x; 
		direction[VY] = y; 
	}
	public VolatileVector2D getUnitVector() { throw new RuntimeException("VolatileVector2D.getUnitVector: error attempt to use length"); }
	public VolatileVector2D getRightNormal() { return new VolatileVector2D(-direction[VY], direction[VX]); }
	public VolatileVector2D getLeftNormal() { return new VolatileVector2D(direction[VY], -direction[VX]); }
	public VolatileVector2D getAddition(Vector2D v2) { return new VolatileVector2D(direction[VX] + v2.direction[VX], direction[VY] + v2.direction[VY]); }
	public VolatileVector2D getSubtraction(Vector2D v2) { return new VolatileVector2D(direction[VX] - v2.direction[VX], direction[VY] - v2.direction[VY]); }
	public void scale(double scalar) { throw new RuntimeException("VolatileVector2D.scale: error attempt to use length"); }
	public VolatileVector2D getScaledVector(double scalar) {throw new RuntimeException("VolatileVector2D.getScaledVector: error attempt to use length");}
	public VolatileVector2D getScaledVector(Vector2D ofUnitVector, double scalar) {throw new RuntimeException("VolatileVector2D.getScaledVector: error attempt to use length");	}
	public double getDotProduct(Vector2D v2) { 
		return direction[VX] * v2.direction[VX] + direction[VY] * v2.direction[VY]; 
	}	
	public VolatileVector2D getProjectionOntoVector(Vector2D ontoVector) { throw new RuntimeException("VolatileVector2D.getProjectionOntoVector: error attempt to use length");}
	public double getPerpProduct(Vector2D v2) { 
		return -direction[VY] * v2.direction[VX] + direction[VX] * v2.direction[VY]; 
	}
	public double getIntersectionRatio(Vector2D v2, Vector2D v3) {
		double pp1 = getPerpProduct(v2);
		double pp2 = v3.getPerpProduct(v2);
		return pp2/pp1;
	}
	public boolean areParallel(Vector2D compareWith) {throw new RuntimeException("VolatileVector2D.areParallel: error attempt to use length");}
	public VolatileVector2D getBounceVector(Vector2D v2, Vector2D normalV) {throw new RuntimeException("VolatileVector2D.getBounceVector: error attempt to use length");}
	public VolatileVector2D clone() { return new VolatileVector2D(direction[VX], direction[VY]); }
	
	public String toString()
	{
		return ""+direction[VX]+","+direction[VY];
	}
	
	
	
	
	
	
	
	
	
	
}