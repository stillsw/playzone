package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;

public class TextButton extends MouseButton
{	
	private String label;
	
	public TextButton(String label, int mouseOperations, ColorArrangement colorArrangement) {
		super(label, colorArrangement, mouseOperations);
		this.label = label;
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		this.placementRectangle = placementRectangle;
		this.placementFromPanel = placementFromPanel;
    	this.buttonShape = this.placementRectangle;
	}

	@Override
	public boolean mouseOverComponent(Point2D mouse) {
		this.mouseIsOver = (mouse.getX() >= this.placementFromPanel.getX() 
							&& mouse.getX() <= this.placementFromPanel.getX() + this.placementRectangle.getWidth()
							&& mouse.getY() >= this.placementFromPanel.getY()
							&& mouse.getY() <= this.placementFromPanel.getY() + this.placementRectangle.getHeight());
			// this.placementRectangle.contains(mouse.getX() - this.placementFromPanel.getX(), mouse.getY() - this.placementFromPanel.getY());
		return this.mouseIsOver;
	}
	
	@Override
	protected void drawButtonInterior(Graphics2D g2, Stroke originalStroke) {
		g2.setStroke(originalStroke);
		Rectangle2D buttonBounds = buttonShape.getBounds2D();
     	FontRenderContext fontRenderContext = g2.getFontRenderContext();
    	Font font = new Font("sansserif", Font.PLAIN, 12);
    	TextLayout textLayout = new TextLayout(this.label, font, fontRenderContext);
    	Rectangle2D rect = textLayout.getBounds();

		textLayout.draw(g2, (float)(buttonBounds.getCenterX() - rect.getWidth() / 2), (float)(buttonBounds.getCenterY() + textLayout.getAscent() / 2));
	}

}