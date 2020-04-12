package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;

import javax.swing.event.MouseInputListener;

public interface GuiComponent extends MouseInputListener
{
	public void update(int gameTick);
	public boolean drawGuiComponent(Graphics2D g2);
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel);
	public void setTemporalPopupBox(TemporalPopupBox tpb);
	// mouse switches on optionally
	public Cursor mouseOverGetCursor(Point2D mouse);
	public boolean mouseOverComponent(Point2D mouse);
	public boolean canRespondToMouse();
}
