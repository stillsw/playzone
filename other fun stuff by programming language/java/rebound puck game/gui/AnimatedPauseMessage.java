package firstReboundGame.gui;

import firstReboundGame.*;

import java.awt.geom.*;

public class AnimatedPauseMessage extends AnimatedText
{
	protected static final double SECONDS_TO_CYCLE = .3; 
	private GameEventsManager gameEventsManager;
	private static final String PAUSED_MSG = "Paused."; 
	private long lastColourChangeTime;
	StringBuffer stringBuffer = new StringBuffer();
	
	
	public AnimatedPauseMessage(Rectangle2D placementRectangle, ColorArrangement colorArrangement, ColorArrangement boxColorArrangement) {
		super(true, placementRectangle, colorArrangement, boxColorArrangement, null);
		this.keepXposFromFirstTime = true;
		// colours don't change for this one
		this.strokeColor = this.colorArrangement.getStrokeColor();
		this.fillColor = this.colorArrangement.getFillColor();
		this.gameEventsManager = GameEventsManager.getInstance();
		this.listenForGameEvents();
	}
	
	private void listenForGameEvents() {
		gameEventsManager.addGameEventsListener(
				new GameEventsAdapter() {
					public void gamePaused() {
//						System.out.println("paused by player");
						AnimatedPauseMessage.this.setText(PAUSED_MSG);
						AnimatedPauseMessage.this.lastColourChangeTime = -1; // trigger a colour change in the update method
						AnimatedPauseMessage.this.temporalPopupBox.drawUntil((int)Constants.VERY_LARGE_NUMBER); // indefinitely
					}
					public void gameResumed() {
//						System.out.println("resumed by player");
						AnimatedPauseMessage.this.setText(null);
						AnimatedPauseMessage.this.temporalPopupBox.drawUntil(ReboundGamePanel.gameTick - 1); // stop
					}
				});
	}
	
	/*
	 * Have to override because pause functionality is very specific, the gametick is not updated in the animation loop
	 * while pausing, so implement using system timer instead
	 */
	@Override
	public void update(int gameTick) {
		// will cycle through the 6 colours, set which one to show next
		long systemTime = System.currentTimeMillis();
		if (systemTime - this.lastColourChangeTime > SECONDS_TO_CYCLE * 1000) {
			this.lastColourChangeTime = systemTime;
			if (this.cycle++ > 1)
				this.cycle = -1;
			this.stringBuffer.setLength(0);
			this.stringBuffer.append(AnimatedPauseMessage.PAUSED_MSG);
			for (int i = 0; i < this.cycle; i++) this.stringBuffer.append('.');
			this.setText(this.stringBuffer.toString());
		}
		
	}
	
	
	
}