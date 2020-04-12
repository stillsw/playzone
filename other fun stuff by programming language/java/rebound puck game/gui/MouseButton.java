package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;

public abstract class MouseButton extends GuiComponentAdapter
{
	public static final float DELAY_SECONDS_BEFORE_TOOLTIP = .8f;
	protected static final TemporalComponentsManager temporalComponentsManager = TemporalComponentsManager.getInstance();
	protected double diameter;
	protected double radius;
	protected double radiusSquared;
	protected Point2D centrePointRelativeToPanel;
	protected Shape buttonShape;
	private String toolTipText;
	private Rectangle2D toolTipBounds;
	private boolean mouseJustEntered = false;
	private int showToolTipFromGameTick = -1;
	private boolean showToolTip = false;
	
	public MouseButton(String toolTipText, ColorArrangement colorArrangement, int mouseOperations) {
		super(colorArrangement);
		if (ReboundGamePanel.ENABLE_MOUSE_CONTROL) {
			this.guiComponentMouseManager.addGuiComponent(this, mouseOperations);
			this.toolTipText = toolTipText;
		}
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		this.diameter = (Double.compare(this.placementRectangle.getHeight(), placementRectangle.getWidth()) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND 
				? placementRectangle.getHeight() : placementRectangle.getWidth());
		this.radius = this.diameter / 2;
		this.radiusSquared = this.radius * this.radius;
		Ellipse2D circle = new Ellipse2D.Double(this.placementRectangle.getX()
    			, this.placementRectangle.getY() + this.placementRectangle.getHeight() - this.diameter
    			, this.diameter
    			, this.diameter);
    	this.centrePointRelativeToPanel = this.translatePointToPanel(circle.getCenterX(), circle.getCenterY()); 
    	this.buttonShape = circle;
	}
	
	@Override
	public void update(int gameTick) {
		super.update(gameTick);
		if (this.mouseJustEntered) {
			this.showToolTipFromGameTick = temporalComponentsManager.getGameTickSecondsFromNow(DELAY_SECONDS_BEFORE_TOOLTIP);
			this.mouseJustEntered = false;
		}
		if (gameTick > this.showToolTipFromGameTick) {
			this.showToolTip = this.mouseIsOver;
			if (!this.showToolTip)
				this.showToolTipFromGameTick = -1;
			this.gameBounds.triggerDrawFeedbackArea(); 
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		super.mouseEntered(arg0);
		this.mouseJustEntered = true;
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		super.mouseExited(arg0); 
		if (this.showToolTip) {
			this.showToolTip = false;
			this.showToolTipFromGameTick = -1;
			this.gameBounds.triggerDrawFeedbackArea(); 
		}
	}
	
	@Override
	public void mousePressed(MouseEvent arg0) {
		this.gameBounds.triggerDrawFeedbackArea(); // just to be safe always allow drawing
		// treat a click as though the mouse just entered, ie. to restart the timing for tooltip
		this.mouseJustEntered = true;
		if (this.showToolTip) {
			this.showToolTip = false;
			this.showToolTipFromGameTick = -1;
		}
	}

	@Override
	public boolean mouseOverComponent(Point2D mouse) {
		this.mouseIsOver = Double.compare(this.radiusSquared, mouse.distanceSq(this.centrePointRelativeToPanel)) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND;
		return this.mouseIsOver;
	}

	protected abstract void drawButtonInterior(Graphics2D g2, Stroke originalStroke);
	
	protected void setToolTipText(String text) {
		this.toolTipText = text;
	}
	
	public final boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
			if (this.mouseIsOver) {
				g2.setColor(this.colorArrangement.getFillColor());
		    	g2.fill(this.buttonShape);
		    	int border = 2;
		    	if (this.showToolTip && this.toolTipText != null) {
			     	FontRenderContext fontRenderContext = g2.getFontRenderContext();
			    	Font font = new Font("sansserif", Font.PLAIN, 12);
			    	TextLayout textLayout = new TextLayout(this.toolTipText, font, fontRenderContext);
		    		if (this.toolTipBounds == null) {
				    	Rectangle2D rect = textLayout.getBounds();
				    	Rectangle2D buttonBounds = this.buttonShape.getBounds2D();
				    	this.toolTipBounds = new Rectangle2D.Double(buttonBounds.getX(), buttonBounds.getY() + buttonBounds.getHeight(), rect.getWidth() + border * 2, rect.getHeight() + border * 2);
		    		}
		    		g2.setColor(Color.LIGHT_GRAY);
		    		g2.fill(this.toolTipBounds);
		    		g2.setColor(Color.DARK_GRAY);
		    		g2.draw(this.toolTipBounds);
		    		textLayout.draw(g2, (float)this.toolTipBounds.getX() + border, (float)(this.toolTipBounds.getY() - border + this.toolTipBounds.getHeight()));
		    	}
			}
	    	g2.setColor(this.colorArrangement.getStrokeColor());
        	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); 
        	Stroke oriStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(3));
	        g2.draw(this.buttonShape);
	        this.drawButtonInterior(g2, oriStroke);
	    	g2.setStroke(oriStroke);
        	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF); 
	
	    }
		return true; 
	}

}