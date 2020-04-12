package firstReboundGame;

import firstReboundGame.sound.*;
import java.util.*;
import java.awt.*;
import java.awt.geom.*;

/*
 * superclass of all the pieces in the game, including puck, striker, targets, walls, obstacles
 */
public abstract class GamePiece implements DrawableGameElement
{
	protected final GameBounds gameBounds = GameBounds.getInstance(); 
	protected static final ReboundGamePanel panel = ReboundGamePanel.getInstance();
	protected Rectangle bounds = gameBounds.getBounds(); // playing area 
	protected Location2D startPos;
	protected boolean isWaitingToRestart;
	protected long waitingSince;  
	protected ColorArrangement colors;
	private SoundManager soundFileReader = SoundManager.getInstance();
	  
	public GamePiece(Location2D placement, ColorArrangement colors)
	{
		this.startPos = placement;
		this.colors = colors;
	} // end of constructor
	
	public boolean isWaitingToRestart() {	return this.isWaitingToRestart; }
	public Location2D getCurrPosn() {	return this.startPos; }
	public Color getFillColor() { return this.colors.getFillColor(); }
	public Color getStrokeColor() { return this.colors.getStrokeColor(); }

	public void setToWaitToRestart() {
		this.isWaitingToRestart = true;
		this.waitingSince = System.currentTimeMillis();
	}
	  
	public abstract double getRadius();

	protected void playSound(int soundIdx) {
		soundFileReader.playSound(soundIdx);
	}

}  // end of class

