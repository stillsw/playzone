package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.font.*;
import java.awt.geom.*;
import java.text.AttributedString;

/*
 * pop up box with 2 options, OK and Cancel
 * functions as a dialog, all other input suspended until it is cleared
 * 
 */
public class DialogPopupBox extends GuiComponentAdapter
{
	private static int OK_KEY = KeyEvent.VK_O;
	private static int CANCEL_KEY = KeyEvent.VK_C;
	private static String OK_LABEL = "OK";
	private static String CANCEL_LABEL = "Cancel";
	private static int border = 5;
	private static int margin = 15; // inside the border
	private static int fontSize = 16;
	private Rectangle2D borderBox;
	private Rectangle2D marginBox;
	private String textMsg;
	private Action okAction;
	private Action cancelAction;
	private ColorArrangement guiColours;
	private TextButton okButton;
	private TextButton cancelButton;
	
	public DialogPopupBox(String textMsg, Action okAction, Action cancelAction) {
		super(ReboundGamePanel.popupColours);
		this.textMsg = textMsg;
		this.okAction = okAction;
		this.cancelAction = cancelAction;
		this.guiColours = ReboundGamePanel.guiColours;
		this.setPlacement(null, null);
		// tell the panel to place control into this popup box
		this.panel.setDialogPopupBox(this);
	}

	public void processKeyPressed(int keyCode) {
		// Ok
		if (keyCode == OK_KEY) {
			if (this.okAction != null)
				this.okAction.doAction();
			this.clearUp();
		}
		// Cancel
		else if (keyCode == CANCEL_KEY) {
			if (this.cancelAction != null)
				this.cancelAction.doAction();
			this.clearUp();
		}
	}
	
	private void clearUp() {
		this.panel.removeDialogPopupBox();
		// de-register the buttons from the mouse manager
		GuiComponentMouseManager gmm = GuiComponentMouseManager.getInstance();
		gmm.removeGuiComponent(this.okButton);
		gmm.removeGuiComponent(this.cancelButton);
	}
	
	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		Rectangle enclosedByRectangle = this.panel.getBounds();
		double width = enclosedByRectangle.getWidth() / 3;
		double height = enclosedByRectangle.getHeight() / 3;
		this.placementRectangle = new Rectangle2D.Double(enclosedByRectangle.getCenterX() - width / 2, enclosedByRectangle.getCenterY() - height / 2, width, height);
		this.placementFromPanel = new Point2D.Double(this.placementRectangle.getX(), this.placementRectangle.getY());
		if (DialogPopupBox.border > 0) 
			this.borderBox = new Rectangle2D.Double(this.placementRectangle.getX() + border, this.placementRectangle.getY() + border, this.placementRectangle.getWidth() - border * 2, this.placementRectangle.getHeight() - border * 2);
		else 
			this.borderBox = this.placementRectangle;
		if (DialogPopupBox.margin > 0)
			this.marginBox = new Rectangle2D.Double(this.borderBox.getX() + margin, this.borderBox.getY() + margin, this.borderBox.getWidth() - margin * 2, this.borderBox.getHeight() - margin * 2);
		else 
			this.marginBox = this.borderBox;

		// make the buttons
		this.okButton = new TextButton(OK_LABEL, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS 
												| GuiComponentMouseManager.MOUSE_EVENT_STATE_DIALOG_IN_CONTROL, this.guiColours) {
				public void mousePressed(MouseEvent arg0) {
					super.mousePressed(arg0);
					DialogPopupBox.this.processKeyPressed(OK_KEY);
				}
			};
		this.cancelButton = new TextButton(CANCEL_LABEL, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS 
												| GuiComponentMouseManager.MOUSE_EVENT_STATE_DIALOG_IN_CONTROL, this.guiColours) {
				public void mousePressed(MouseEvent arg0) {
					super.mousePressed(arg0);
					DialogPopupBox.this.processKeyPressed(CANCEL_KEY);
				}
			};

		// divide up the biggest box so to create a row of boxes for the buttons at the bottom
		// use the outermost box because it is relative to the enclosing rectangle (the panel itself)
		// need this to be in step so the mouse over coordinates coincide correctly
		RectangleDivider rectangleDivider = new RectangleDivider(this.placementRectangle.getBounds(), 7, 5, 0, 0);
		Rectangle2D placeRect = rectangleDivider.getCellRectangle2D(5, 1);
		Point2D panelPoint = new Point2D.Double(placeRect.getX() + this.placementFromPanel.getX(), placeRect.getY() + this.placementFromPanel.getY());
		Rectangle2D okPlaceRectTrans = new Rectangle2D.Double(panelPoint.getX(), panelPoint.getY(), placeRect.getWidth(), placeRect.getHeight());
		this.okButton.setPlacement(okPlaceRectTrans, panelPoint); 
		
		placeRect = rectangleDivider.getCellRectangle2D(5, 3);
		panelPoint = new Point2D.Double(placeRect.getX() + this.placementFromPanel.getX(), placeRect.getY() + this.placementFromPanel.getY());
		Rectangle2D cancelPlaceRectTrans = new Rectangle2D.Double(panelPoint.getX(), panelPoint.getY(), placeRect.getWidth(), placeRect.getHeight());
		this.cancelButton.setPlacement(cancelPlaceRectTrans, panelPoint); 
	}

	public boolean drawGuiComponent(Graphics2D g2) {
	    Color color = this.colorArrangement.getFillColor();
	    if (color != null) {
	    	g2.setColor(color);
	    	g2.fill(this.placementRectangle);
	    }
	    color = this.colorArrangement.getStrokeColor();
	    if (color != null) {
	    	g2.setColor(color);
	    	g2.draw(this.placementRectangle);
	    }
	    g2.draw(this.borderBox);

     	FontRenderContext fontRenderContext = g2.getFontRenderContext();
    	g2.setColor(this.guiColours.getStrokeColor());
    	this.panel.applyRenderingHints(g2, true);
	    		
		Point2D pen = new Point2D.Double(this.marginBox.getX(), this.marginBox.getY());
		AttributedString attribString = new AttributedString(this.textMsg);
		attribString.addAttribute(TextAttribute.FONT, new Font("sansserif", Font.PLAIN, fontSize));

		LineBreakMeasurer measurer = new LineBreakMeasurer(attribString.getIterator(), fontRenderContext);
		while (true) {
	  	   TextLayout layout = measurer.nextLayout((float)this.marginBox.getWidth());
	  	   if (layout == null) break;
    	   pen.setLocation(pen.getX(), pen.getY() + layout.getAscent()); 
    	   float dx = (float)(this.marginBox.getWidth() - layout.getVisibleAdvance()) / 2; // (for centre justify) layout.getAdvance(); // for right justify 
    	   layout.draw(g2, (float)pen.getX() + dx, (float)pen.getY()); 
    	   pen.setLocation(pen.getX(), pen.getY() + layout.getDescent() + layout.getLeading()); 
		}
		
		// draw the buttons
		this.okButton.drawGuiComponent(g2);
		this.cancelButton.drawGuiComponent(g2);
				
    	this.panel.applyRenderingHints(g2, false);
		
	    return true;
	}


}