package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public class NewGameButton extends MouseButton
{
	private static String TOOL_TIP_TEXT = "New Game";
	private ScoringManager scoringManager = ScoringManager.getInstance();
//	private Polygon star;
	private Area starArea;
	
	public NewGameButton(ColorArrangement colorArrangement) {
		super(TOOL_TIP_TEXT, colorArrangement, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS);
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		double circleMargin = this.radius / 4;
		double circleDiameter = this.diameter - circleMargin * 2;
		Ellipse2D circle = (Ellipse2D)this.buttonShape;
    	Arc2D arc1 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 90.0, 360/5, Arc2D.OPEN);
    	Arc2D arc2 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 90.0 + (360/5)*2, 360/5, Arc2D.OPEN);
    	Arc2D arc3 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 90.0 + (360/5)*4, 360/5, Arc2D.OPEN);

    	GeneralPath starPath = new GeneralPath();
    	starPath.moveTo(arc1.getStartPoint().getX(), arc1.getStartPoint().getY());
    	starPath.lineTo(arc2.getStartPoint().getX(), arc2.getStartPoint().getY());
    	starPath.lineTo(arc3.getStartPoint().getX(), arc3.getStartPoint().getY());
    	starPath.lineTo(arc1.getEndPoint().getX(), arc1.getEndPoint().getY());
    	starPath.lineTo(arc2.getEndPoint().getX(), arc2.getEndPoint().getY());
    	starPath.lineTo(arc1.getStartPoint().getX(), arc1.getStartPoint().getY());
    	
    	this.starArea = new Area(starPath);
    	
	}

	/*
	 * Override to disable when no game is being played
	 */
	@Override
	public boolean mouseOverComponent(Point2D mouse) {
		if (!this.scoringManager.inAGame())
			return false;
		else 
			return super.mouseOverComponent(mouse);
	}
	
	@Override
	public void mousePressed(MouseEvent arg0) {
		super.mousePressed(arg0);
		// initiate confirmation dialog and start new game
		scoringManager.requestNewGame(false, 0, this.colorArrangement);
	}

	@Override
	protected void drawButtonInterior(Graphics2D g2, Stroke originalStroke) {
 //   	g2.fill(this.star);
		g2.setStroke(originalStroke);
		g2.draw(this.starArea);
	}

}