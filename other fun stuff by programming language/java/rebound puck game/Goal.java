package firstReboundGame;

import java.awt.*;
import java.awt.geom.*;

public class Goal extends StrikeableGamePiece
{	
	public static int GOAL_SHAPE_ROUND = 0;
	public static int GOAL_SHAPE_RECTANGULAR = 1;
//	public static int GOAL_SCORED_BY_PUCK = 0;
//	public static int GOAL_SCORED_BY_TARGET = 1;
	// ratios for how difficult it is to score in this goal... ie. how close the centre
	// of the moving piece has to come to the goal's centre for it to be inside the goal
//	public static double GOAL_DIFFICULTY_EASY = 0;
//	protected static double GOAL_DIFFICULTY_EASY_RATIO = 50.0;
	
	protected double width;
	protected double height;
	protected int shape;
//	protected int goalDifficulty;
	protected Shape goalArea;
	protected boolean isReactingToGoal = false;
	protected boolean isReactingToPenalty = false;
	protected int reactUntilGameTick = -1;
	protected boolean flashOn;
	protected AffineTransform affineTransform;
	protected double transformAngleInRadians = Math.toRadians(0.0); 
	
	public Goal(Location2D placement, double width, double height, int shape, boolean partOfPerimeter, ColorArrangement colors) {
		super(placement, partOfPerimeter, colors);
		this.width = width;
		this.height = height;
		this.shape = shape;
	}

	protected void assignTransformation() {
		// subclasses can set the angle to their own version
		affineTransform = AffineTransform.getRotateInstance(transformAngleInRadians, this.getCurrPosn().getX(), this.getCurrPosn().getY());		
	}
	
	public AffineTransform getAffineTransform() { return this.affineTransform; }
	
	public void makePieces() {
		this.assignTransformation();
		if (shape == Goal.GOAL_SHAPE_RECTANGULAR)
			goalArea = new Rectangle((int)(this.getCurrPosn().getX() - width / 2), (int)(this.getCurrPosn().getY() - height), (int)width, (int)height);
		else
			goalArea = new Ellipse2D.Double(this.getCurrPosn().getX() - width / 2, this.getCurrPosn().getY() - height, width, height);
		
		goalArea = affineTransform.createTransformedShape(goalArea);
	}
	
	public double getRadius() {	return (width > height ? width : height); }
	public boolean isStatic() {	return true; }
	public double getWidth() {	return width; }
	public double getHeight() {	return height; }
	public int getShape() {	return shape; }
	public Area getGoalArea() { return new Area(this.goalArea); }

	protected boolean drawPiece(Graphics2D g2) {
		if (super.drawPiece(g2)) {		
	
			if (this.getFillColor() != null) {
				g2.setColor(this.getFillColor());
				g2.fill(this.goalArea);
			}
			// don't draw a shape around if it's a gated type goal
			if (!(this instanceof GatedGoal))
				if (this.getStrokeColor() != null) {
					g2.setColor(this.getStrokeColor());
					g2.draw(this.goalArea);
				}
			return true;
		}
		else
			return false;
		// for debugging... see where the currposn is
//		g2.setColor(Color.black);
//		g2.drawOval((int)this.getCurrPosn().getX() - 5, (int)this.getCurrPosn().getY() - 5, 10, 10);
	}  // end of draw()

}  // end of class

