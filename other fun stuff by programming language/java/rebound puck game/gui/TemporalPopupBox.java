package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.*;
import java.awt.geom.*;

/*
 * designed as simple pop up container for gui components
 * eg. short lived animations such as points scored, bonuses etc
 * 
 */
public class TemporalPopupBox extends GuiComponentAdapter
{
	private int drawUntilGameTick = -1;
	private boolean drawIt = false;
	private boolean ignoreGameStates = false;
	private GuiComponent guiComponent;
	private TemporalComponentsManager temporalComponentsManager = TemporalComponentsManager.getInstance();
	private GameEventsManager gameEventsManager = GameEventsManager.getInstance();
	
	/*
	 * probably not going to display the box itself
	 */
	public TemporalPopupBox(GuiComponent guiComponent, boolean drawImmediately, boolean ignoreGameStates, Rectangle2D placementRectangle, ColorArrangement colorArrangement) {
		super(colorArrangement);
		this.drawIt = drawImmediately; // this will switch off right now in the update method anyway
		this.ignoreGameStates = ignoreGameStates; // update will always trigger if this is set
		this.guiComponent = guiComponent;
		this.guiComponent.setTemporalPopupBox(this);
		this.temporalComponentsManager.addTemporalPopupBox(this);
		this.setPlacement(placementRectangle, null);
	}

	public void drawUntil(int gameTick) {
		this.drawUntilGameTick = gameTick;
	}
	
	public boolean shouldDrawIt() { return this.drawIt; }
	
	@Override
	public void update(int gameTick) {
		if (this.ignoreGameStates || !this.gameEventsManager.isPaused()) {
			boolean wasDrawing = this.drawIt;
			this.drawIt = (gameTick <= this.drawUntilGameTick);
			if (this.drawIt)
				guiComponent.update(gameTick);
			// tell the manager if it has changed state
			if (this.drawIt != wasDrawing)
				this.temporalComponentsManager.setDrawing(this);
		}
	}

	public boolean drawGuiComponent(Graphics2D g2) {
	    if (ReboundGamePanel.showDebugging) {
	    	g2.setColor(Color.orange);
	    	g2.fill(placementRectangle);
	    }
	    if (this.drawIt)
	    	guiComponent.drawGuiComponent(g2);
	    
		return true; 
	}

}