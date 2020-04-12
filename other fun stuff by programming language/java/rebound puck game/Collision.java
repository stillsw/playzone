package firstReboundGame;


/*
 * store the details of a collision, a) for debugging, b) for drawing
 */
public class Collision
{
	public static double elasticity = .6; // <=1, for elastic collision =1
	private StrikeableGamePiece struck;
	private Collision struckCollision;
	private Location2D detectedAt;
	private Location2D intersectionPoint;
	private LineVector2D originalBearing;
	private LineVector2D newBearing;
	private double distanceAfterIntersect;
	private double timeAfterIntersect;
	private LineVector2D normal;
	private LineVector2D v2; // the angle of impact on the obstacle

	public Collision(StrikeableGamePiece struck, Location2D detectedAt, Location2D intersectionPoint, LineVector2D originalBearing, 
			LineVector2D newBearing, double distanceAfterIntersect, double timeAfterIntersect, LineVector2D normal, LineVector2D v2) {
		this.struck = struck;
		this.detectedAt = detectedAt;
		this.intersectionPoint = intersectionPoint;
		this.originalBearing = originalBearing;
		this.newBearing = newBearing;
		this.distanceAfterIntersect = distanceAfterIntersect;
		this.timeAfterIntersect = timeAfterIntersect;
		this.normal = normal;	
		this.v2 = v2;
	}

	public StrikeableGamePiece getGamePiece() { return struck; }
	public Location2D getDetectedAt() { return detectedAt;	}
	public Location2D getIntersectionPoint() { return intersectionPoint; }
	public LineVector2D getOriginalBearing() { return originalBearing;	}
	public LineVector2D getNewBearing() { return newBearing; }
	public double getDistanceAfterIntersect() { return distanceAfterIntersect; }
	public double getTimeAfterIntersect() { return timeAfterIntersect; }
	public LineVector2D getNormal() { return normal; }
	public LineVector2D getObstacleVector() { return v2; }
	public Collision getStruckCollision() { return this.struckCollision; }

	public void setNewBearing(LineVector2D newBearing) { this.newBearing = newBearing; }
	public void setStruckCollision(Collision struckCollision) { this.struckCollision = struckCollision; }

	public void calculateNewBearings(MoveableGamePiece collider, Collision colliderCollision) {
		//this.tryGamasutraExample(collider, colliderCollision);
		this.fuKwunHwangMethod(collider, colliderCollision);
	}
	private void fuKwunHwangMethod(MoveableGamePiece collider, Collision colliderCollision) {
		// exchange velocities formula for this courtesy of post I found by Fu-Kwun Hwang
		double m1 = ((MoveableGamePiece)colliderCollision.getGamePiece()).getMass();
		double m2 = collider.getMass();
		LineVector2D moverOriginalBearingV = this.getOriginalBearing();
		LineVector2D colliderOriginalBearingV = colliderCollision.getOriginalBearing();
		Location2D moverImpactPosn = this.getIntersectionPoint();
		Location2D colliderImpactPosn = colliderCollision.getIntersectionPoint();
		
		// for formula from data
		double x1 = moverImpactPosn.getX(), y1 = moverImpactPosn.getY();
		double x2 = colliderImpactPosn.getX(), y2 = colliderImpactPosn.getY();
		double vx1 = moverOriginalBearingV.getVx();
		double vy1 = moverOriginalBearingV.getVy();
		double vx2 = colliderOriginalBearingV.getVx();
		double vy2 = colliderOriginalBearingV.getVy();
		// time to roll forwards at the end... should be the proportion of a tick to play out after collision
		double ddt = this.getDistanceAfterIntersect() / moverOriginalBearingV.getLength();
		
		// length of the vector between the points
		double r12=Math.sqrt((x1-x2)*(x1-x2)+(y1-y2)*(y1-y2));
		// the x and y components of this (ie. unit vector)
		double cs=(x2-x1)/r12;	double sc=(y2-y1)/r12;
		// Projection of the velocities in these axes
		double vp1=vx1*cs+vy1*sc;
		double vp2=vx2*cs+vy2*sc;
		// normal component 
		double vn1=vx1*sc-vy1*cs;
		double vn2=vx2*sc-vy2*cs;
		
		// now calculate bounce
		
		// New velocities in these axes (after collision): elasticity<=1,  for elastic collision elasticity=1
		double mcm = m1 + m2;
		if (elasticity == 0.0) { // doesn't right now, but in case...
			vx1=vx2=(m1*vx1+m2*vx2)/mcm;
			vy1=vy2=(m1*vy1+m2*vy2)/mcm;
		}
		else {
			double momentum=m1*vp1+m2*vp2;
			double Vrel=vp2-vp1;
			vp1=(momentum+elasticity*m2*Vrel)/mcm;
			vp2=(momentum-elasticity*m1*Vrel)/mcm;
			//w1+=2.*(vp1-vp1s)*friction/r1;
			//w2-=2.*(vp2-vp2s)*friction/r2;
			//vn1+=friction*(vp1-vp1s);
			//vn2+=friction*(vp2-vp2s);

			// translate backward
			vx1=vp1*cs+vn1*sc;
			vx2=vp2*cs+vn2*sc;
			vy1=vp1*sc-vn1*cs;
			vy2=vp2*sc-vn2*cs;
		}
		// centres of mass
		double vx0=0.;
		double vy0=0.;
		// start posn rolled forwards
		x1+=(vx1-vx0)*ddt;	y1+=(vy1-vy0)*ddt;
		x2+=(vx2-vx0)*ddt;	y2+=(vy2-vy0)*ddt;

		// convert to new bearings
		LineVector2D moverNewBearingV = new LineVector2D(x1, y1, vx1, vy1);
		LineVector2D colliderNewBearingV = new LineVector2D(x2, y2, vx2, vy2);
		this.setNewBearing(moverNewBearingV);
		colliderCollision.setNewBearing(colliderNewBearingV);
//				+moverOriginalBearingV.getLocation().getDistance(colliderOriginalBearingV.getLocation())
//				+" length was="+moverOriginalBearingV.length+" now="+moverNewBearingV.length);
//		System.out.println("\t collider distance between disks after="
//				+moverNewBearingV.getLocation().getDistance(colliderNewBearingV.getLocation())
//				+" length was="+colliderOriginalBearingV.length+" now="+colliderNewBearingV.length
//				+" ddt = "+ddt+" distanceAfterIntersect="+this.getDistanceAfterIntersect());

	}

	private void tryGamasutraExample(MoveableGamePiece collider, Collision colliderCollision) {
		double mass1 = ((MoveableGamePiece)colliderCollision.getGamePiece()).getMass();
		double mass2 = collider.getMass();
		double combinedRadii = collider.getRadius() + colliderCollision.getGamePiece().getRadius();

		LineVector2D moverOriginalBearingV = this.getOriginalBearing();
		LineVector2D colliderOriginalBearingV = colliderCollision.getOriginalBearing();
		// USE THESE JUST FOR NOW, TO GET A NEW START POINT
		this.calculateNewBearing();
		colliderCollision.calculateNewBearing();
		//// First, find the normalized vector n from the center of
		//// circle1 to the center of circle2
		//Vector n = circle1.center - circle2.center;
		//n.normalize();
		Vector2D n = new Vector2D(moverOriginalBearingV.getLocation(), colliderOriginalBearingV.getLocation()).getUnitVector();
	
		//// Find the length of the component of each of the movement
		//// vectors along n.
		//// a1 = v1 . n
		//// a2 = v2 . n
		//float a1 = v1.dot(n);
		//float a2 = v2.dot(n);
		double a1 = moverOriginalBearingV.getDotProduct(n);
		double a2 = colliderOriginalBearingV.getDotProduct(n);
		//Vector2D moverProjV = moverOriginalBearingV.getProjectionOntoVector(n, false);
		//double dpCheck = moverProjV.getUnitVector().getDotProduct(n.getUnitVector());
		//Vector2D colliderProjV = colliderOriginalBearingV.getProjectionOntoVector(n, false);
		//double a1 = moverProjV.getLength();
		//double a2 = colliderProjV.getLength();
		//System.out.println("#############check dp of moverProjV same as n dp="+dpCheck+" moverLen="+a1+" colliderLen="+a2);
		//// Using the optimized version,
		//// optimizedP =  2(a1 - a2)
	////	              -----------
	////	                m1 + m2
		//float optimizedP = (2.0 * (a1 - a2)) / (circle1.mass + circle2.mass);
		double optimizedP = (2.0 * (a1 - a2)) / (mass1 + mass2);
		//// Calculate v1', the new movement vector of circle1
		//// v1' = v1 - optimizedP * m2 * n
		//Vector v1' = v1 - optimizedP * circle2.mass * n;
		Location2D moverStartPosn = this.getNewBearing().getLocation(); // this is approx for now, to test formula
		double vx1 = moverOriginalBearingV.getVx() - optimizedP * mass2 * n.getVx();
		double vy1 = moverOriginalBearingV.getVy() - optimizedP * mass2 * n.getVy();
		LineVector2D moverNewBearingV = new LineVector2D(moverStartPosn, vx1, vy1);
		//// Calculate v1', the new movement vector of circle1
		//// v2' = v2 + optimizedP * m1 * n
		//Vector v2' = v2 + optimizedP * circle1.mass * n;
		Location2D colliderStartPosn = colliderCollision.getNewBearing().getLocation(); // this is approx for now, to test formula
		double vx2 = colliderOriginalBearingV.getVx() + optimizedP * mass1 * n.getVx();
		double vy2 = colliderOriginalBearingV.getVy() + optimizedP * mass1 * n.getVy();
		LineVector2D colliderNewBearingV = new LineVector2D(colliderStartPosn, vx2, vy2);
		
		this.setNewBearing(moverNewBearingV);
		colliderCollision.setNewBearing(colliderNewBearingV);
// BEFORE UNCOMMENT THE FOLLOWING TEST NEED TO COMMENT OUT THE ABOVE 2 LINES
		
		// check the direction is the same as previously worked out by testing the dot products
//		this.calculateNewBearing();
//		colliderCollision.calculateNewBearing();
//		System.out.println(
//				"new bearings:\n\t would've been:\n\t\t mover="+this.getNewBearing()
//				+" len="+this.getNewBearing().getLength()
//				+"\n\t\t collider="+colliderCollision.getNewBearing()
//				+" len="+colliderCollision.getNewBearing().getLength()
//				+"\n\t and now they are:\n\t\t mover="+moverNewBearingV
//				+" len="+moverNewBearingV.getLength()
//				+"\n\t\t collider="+colliderNewBearingV
//				+" len="+colliderNewBearingV.getLength()
//				+"\n\t dps:\n\t\t mover="+moverNewBearingV.getUnitVector().getDotProduct(this.getNewBearing().getUnitVector())
//				+"\n\t\t collider="+colliderNewBearingV.getUnitVector().getDotProduct(colliderCollision.getNewBearing().getUnitVector())
//				);
	}


	/*
	 * for bouncing off a static object, so easy compared to mobile object
	 */
	public void calculateNewBearing() {
		Vector2D bounceVector = originalBearing.getBounceVector(this.getObstacleVector(), this.getNormal()); 
		VolatileVector2D bounceUnitVector = bounceVector.getVolatileUnitVector();
		double distanceAfterIntersect = this.getDistanceAfterIntersect();
		Location2D ip = this.getIntersectionPoint();
		double adjustedNextX = ip.getX() + bounceUnitVector.getVx() * distanceAfterIntersect; 
		double adjustedNextY = ip.getY() + bounceUnitVector.getVy() * distanceAfterIntersect;
		// plot this distance against the bounce vector to get the next point
		double bounceVectorX = bounceUnitVector.getVx() * originalBearing.getLength(); 
		double bounceVectorY = bounceUnitVector.getVy() * originalBearing.getLength();
		// plot this distance against the bounce vector to get the next point
		this.setNewBearing(new LineVector2D(adjustedNextX, adjustedNextY, bounceVectorX, bounceVectorY));
	}
	
}  // end of class

