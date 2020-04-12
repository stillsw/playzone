package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public class PauseButton extends MouseButton
{
	private static String TOOL_TIP_TEXT_PAUSE = "Pause";
	private static String TOOL_TIP_TEXT_RESUME = "Resume";
	private GameEventsManager gameEventsManager;
	private Polygon arrow;
	private boolean isPaused;
	
	public PauseButton(ColorArrangement colorArrangement) {
		super(TOOL_TIP_TEXT_PAUSE, colorArrangement, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS
								| GuiComponentMouseManager.MOUSE_EVENT_STATE_GAME_PAUSED);
		this.gameEventsManager = GameEventsManager.getInstance();
		this.listenForGameEvents();
	}

	private void listenForGameEvents() {
		gameEventsManager.addGameEventsListener(
				new GameEventsAdapter() {
					public void gamePaused() {
						PauseButton.this.isPaused = true;
						PauseButton.this.setToolTipText(PauseButton.TOOL_TIP_TEXT_RESUME);
					}
					public void gameResumed() {
						PauseButton.this.isPaused = false;
						PauseButton.this.setToolTipText(PauseButton.TOOL_TIP_TEXT_PAUSE);
					}
				});
	}
	
	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		double circleMargin = this.radius / 4;
		double circleDiameter = this.diameter - circleMargin * 2;
		Ellipse2D circle = (Ellipse2D)this.buttonShape;
    	Arc2D arc1 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 0.0, 360/3, Arc2D.OPEN);
    	Arc2D arc2 = new Arc2D.Double(circle.getX() + circleMargin, circle.getY() + circleMargin, circleDiameter, circleDiameter, 360/3, 360/3, Arc2D.OPEN);
    	this.arrow = new Polygon(new int[] {(int)arc1.getStartPoint().getX(), (int)arc1.getEndPoint().getX(), (int)arc2.getEndPoint().getX()}
    							, new int[] {(int)arc1.getStartPoint().getY(), (int)arc1.getEndPoint().getY(), (int)arc2.getEndPoint().getY()}, 3);
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		super.mousePressed(arg0);
		if (this.gameEventsManager.isPaused())
			this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_RESUME_GAME, true);
		else
			this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_PAUSE_GAME, true);
	}

	@Override
	protected void drawButtonInterior(Graphics2D g2, Stroke originalStroke) {
        if (this.isPaused) {
        	g2.fill(this.arrow);
        }
        else {
    		Ellipse2D circle = (Ellipse2D)this.buttonShape;
	        int xpos = (int)circle.getCenterX() - 5; // gap of 4 plus width of line 3 drawn in the middle
	        int ypos = (int)circle.getCenterY() - 4; // make lines 8 long
			g2.drawLine(xpos, ypos, xpos, ypos + 8);
			g2.drawLine(xpos + 10, ypos, xpos + 10, ypos + 8);
        }
	}

}