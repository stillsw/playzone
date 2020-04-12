package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public class ExitGameButton extends MouseButton
{
	private static String TOOL_TIP_TEXT = "Exit Game";
	private GameEventsManager gameEventsManager = GameEventsManager.getInstance();
	private Arc2D arc1;
	private Arc2D arc2;
	
	public ExitGameButton(ColorArrangement colorArrangement) {
		super(TOOL_TIP_TEXT, colorArrangement, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS);
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		double circleMargin = this.radius / 1.75;
		double circleDiameter = this.diameter - circleMargin * 2;
		Ellipse2D circle = (Ellipse2D)this.buttonShape;
    	this.arc1 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 45.0, 90.0, Arc2D.OPEN);
    	this.arc2 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 225.0, 90.0, Arc2D.OPEN);
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		super.mousePressed(arg0);
		// initiate confirmation dialog and start new game
		gameEventsManager.requestCloseGame(this.colorArrangement);
	}

	@Override
	protected void drawButtonInterior(Graphics2D g2, Stroke originalStroke) {
		g2.drawLine((int)this.arc1.getStartPoint().getX(), (int)this.arc1.getStartPoint().getY(), (int)this.arc2.getStartPoint().getX(), (int)this.arc2.getStartPoint().getY());
		g2.drawLine((int)this.arc1.getEndPoint().getX(), (int)this.arc1.getEndPoint().getY(), (int)this.arc2.getEndPoint().getX(), (int)this.arc2.getEndPoint().getY());
		g2.setStroke(originalStroke);
	}

}