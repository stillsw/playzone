package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.ScoringManager;
import firstReboundGame.sound.SoundManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public class GuiComponentAdapter implements GuiComponent
{
	protected Rectangle2D placementRectangle;
	protected ColorArrangement colorArrangement;
	protected SoundManager soundFileReader = SoundManager.getInstance();
	protected ReboundGamePanel panel = ReboundGamePanel.getInstance();
	// only used by components that are placed into control of the temporal components manager (eg popups and animations)
	protected TemporalPopupBox temporalPopupBox;
	protected GuiComponentMouseManager guiComponentMouseManager = GuiComponentMouseManager.getInstance();
//	protected GameEventsManager gameEventsManager = GameEventsManager.getInstance();
	protected ScoringManager scoringManager = ScoringManager.getInstance();
	protected GameBounds gameBounds = GameBounds.getInstance();
	protected Point2D placementFromPanel;
	
	protected boolean mouseIsOver = false;
	
	protected GuiComponentAdapter(ColorArrangement colorArrangement) {
		this.colorArrangement = colorArrangement;
	}

	@Override
	public void update(int gameTick) {}

	public boolean drawGuiComponent(Graphics2D g2) {
	    if (ReboundGamePanel.showDebugging) {
	    	g2.setColor(Color.orange);
	    	g2.fill(placementRectangle);
	    	g2.fill(new Ellipse2D.Double(this.placementFromPanel.getX() - 2, this.placementFromPanel.getY() - 2, 4, 4));
	    }
		return true; 
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		this.placementRectangle = placementRectangle;
		this.placementFromPanel = placementFromPanel;
	}

	protected Point2D translatePointToPanel(Point2D point) {
		return this.translatePointToPanel(point.getX(), point.getY());
	}
	
	protected Point2D translatePointToPanel(double x, double y) {
		return new Point2D.Double(x - this.placementRectangle.getX() + this.placementFromPanel.getX(), y - this.placementRectangle.getY() + this.placementFromPanel.getY());
	}
	
	@Override
	public void setTemporalPopupBox(TemporalPopupBox tpb) {
		this.temporalPopupBox = tpb;		
	}

	@Override
	public boolean mouseOverComponent(Point2D mouse) {
		this.mouseIsOver = false;
		return this.mouseIsOver;
	}

	/*
	 * this method won't be called unless bitwise operator is set
	 * MOUSE_EVENT_STATE_GAME_PAUSED (applies to PauseButton)
	 */
	@Override
	public boolean canRespondToMouse() {
		return true;
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		this.gameBounds.triggerDrawFeedbackArea(); 
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		this.gameBounds.triggerDrawFeedbackArea(); 
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseMoved(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	/*
	 * default to hand cursor, though this method won't be called unless bitwise operator is set
	 * MOUSE_EVENT_MOUSE_OVER_ASK
	 */
	@Override
	public Cursor mouseOverGetCursor(Point2D mouse) {
		return GuiComponentMouseManager.HAND_CURSOR;
	}

}