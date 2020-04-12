package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;

/*
 * A simple layout area for player feedback, and gui controls
 * Layout is tabular (rows/columns)
 */
public class PlayerFeedbackArea implements DrawableGameElement
{
	private BufferedImage canvas;
	private Graphics2D canvasContext2D;
	private ColorArrangement colors;
	private RectangleDivider rectangleDivider;
	private GuiComponent[] guiComponents = new GuiComponent[0];
	private Font font = new Font("SansSerif", Font.BOLD, 12);
	private ReboundGamePanel panel = ReboundGamePanel.getInstance();
	private Point2D placementFromPanel;

	public PlayerFeedbackArea(Rectangle enclosingRectangle, Point2D placementFromPanel, int rows, int columns, int border, int cellMargin, ColorArrangement colors) {
		this.rectangleDivider = new RectangleDivider(enclosingRectangle, rows, columns, border, cellMargin);
		this.colors = colors;
		this.placementFromPanel = placementFromPanel;
	}
	
	public RectangleDivider getRectangleDivider() { return this.rectangleDivider; }
	
	public void addGuiComponent(GuiComponent guiComponent, int row, int column) {
		this.addGuiComponent(guiComponent, row, column, 1, 1);
	}

	public void addGuiComponent(GuiComponent guiComponent, int row, int column, int numRows, int numColumns) {
		Rectangle2D placementRectangle = this.rectangleDivider.getCellRectangle2D(row, column, numRows, numColumns);
		Point2D guiPlacementFromPanel = new Point2D.Double(this.placementFromPanel.getX() + placementRectangle.getX(), this.placementFromPanel.getY() + placementRectangle.getY());
		guiComponent.setPlacement(placementRectangle, guiPlacementFromPanel);
		GuiComponent[] newGuiComponents = new GuiComponent[this.guiComponents.length + 1];
		System.arraycopy(this.guiComponents, 0, newGuiComponents, 0, this.guiComponents.length);
		newGuiComponents[newGuiComponents.length - 1] = guiComponent;
		this.guiComponents = newGuiComponents;
	}
	
	/*
	 * update the controls
	 */
	public void update(int gameTick) {

		// inform gui components that might need to know what the gameTick is
	    for (int i = 0; i < this.guiComponents.length; )
	    	this.guiComponents[i++].update(gameTick);
		
	}
	
	
	
	@Override
	public void draw(Graphics2D g2) {
	    if (canvas == null) {
	    	canvas = new BufferedImage((int)rectangleDivider.getEnclosingRectangle().getWidth(), (int)rectangleDivider.getEnclosingRectangle().getHeight(), BufferedImage.TYPE_INT_RGB);
	        if (canvas == null) {
	        	System.out.println("PlayerFeedbackArea.update: Canvas image is null");
	        }
	        else {
	        	canvasContext2D = (Graphics2D)canvas.createGraphics();
	        	canvasContext2D.setFont(font);
		    	this.canvasContext2D.setBackground(this.colors.getFillColor());
//	        	canvasContext2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); 
	        }
	    }

		if (this.canvasContext2D != null) {
		    
			int border = this.rectangleDivider.getBorder();
			int enclosedWidth = (int)this.rectangleDivider.getEnclosedWidth();
			int enclosedHeight = (int)this.rectangleDivider.getEnclosedHeight();
			Rectangle enclosedRect = this.rectangleDivider.getEnclosingRectangle();
			// this wipes the image to be drawn again, without this the following fill doesn't seem to do much
			// I don't get this, and hope the clearRect works on all platforms!
			this.canvasContext2D.clearRect(border, border, enclosedWidth, enclosedHeight);
	    	this.canvasContext2D.fill(enclosedRect);
		    Color fillColor = this.colors.getFillColor();
		    if (fillColor != null) {
		    	this.canvasContext2D.setColor(fillColor);
		    	this.canvasContext2D.fill(enclosedRect);
		    }
		    Color strokeColor = this.colors.getStrokeColor();
		    if (strokeColor != null) {
		    	this.canvasContext2D.setColor(strokeColor);
		    	this.canvasContext2D.drawRect(border, border, enclosedWidth, enclosedHeight);
		    }
		    
			panel.applyRenderingHints(g2, true);
		    for (int i = 0; i < this.guiComponents.length; )
		    	this.guiComponents[i++].drawGuiComponent(this.canvasContext2D);
			panel.applyRenderingHints(g2, false);
		    
		    if (ReboundGamePanel.showDebugging) {
		    	this.canvasContext2D.setColor(Color.green);
				
				double cellWidth = this.rectangleDivider.getCellWidth();
				double cellHeight = this.rectangleDivider.getCellHeight();
		    	this.canvasContext2D.setColor(Color.blue);
				for (int r = 0; r < this.rectangleDivider.getRows(); r++) {
					int ypos = (int)(border + r * cellHeight);
					this.canvasContext2D.drawLine(border, ypos, border + enclosedWidth, ypos);
				}
				for (int c = 0; c < this.rectangleDivider.getColumns(); c++) {
					int xpos = (int)(border + c * cellWidth);
					this.canvasContext2D.drawLine(xpos, border, xpos, enclosedHeight);
				}
		    	this.canvasContext2D.setColor(Color.pink);
				for (int r = 0; r < this.rectangleDivider.getRows(); r++) {
					for (int c = 0; c < this.rectangleDivider.getColumns(); c++) {
						this.canvasContext2D.draw(this.rectangleDivider.getCellRectangle2D(r, c));
					}
				}
		    }
		    
		    // not sure why, but the colour left set in the graphics context is filling the image after it is drawn
		    // to get around this, put the fill colour back there, and be careful not to use that colour for components
		    // until know why this is happening
//	    	this.canvasContext2D.setColor(fillColor);
		    
		    // draw the canvas onto the context passed in
//		    g2.drawImage(this.canvas, this.rectangleDivider.getEnclosingRectangle().x + border, this.rectangleDivider.getEnclosingRectangle().y + border, enclosedWidth, enclosedHeight, null);
		    g2.drawImage(this.canvas, (int)this.placementFromPanel.getX(), (int)this.placementFromPanel.getY(), this.canvas.getWidth(), this.canvas.getHeight(), null);
		   // canvasContext2D.dispose();
		}
		else {
			System.out.println("PlayerFeedbackArea.draw: null graphics context");
		}
	}

	@Override
	public void drawAfterShading(Graphics2D g2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Location2D getCurrPosn() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Color getFillColor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getRadius() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Color getStrokeColor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isStatic() {
		// TODO Auto-generated method stub
		return false;
	}
}