package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.sound.SoundManager;

import java.awt.geom.*;

public class AnimatedSoundMessage extends AnimatedText
{
	private GameEventsManager gameEventsManager;
	private SoundManager soundManager = SoundManager.getInstance();
	private static final String SOUND_ON_MSG = "Sound On"; 
	private static final String SOUND_OFF_MSG = "Sound Off"; 
	private long shownTime;
	
	
	public AnimatedSoundMessage(Rectangle2D placementRectangle, ColorArrangement colorArrangement, ColorArrangement boxColorArrangement) {
		super(true, placementRectangle, colorArrangement, boxColorArrangement, null);
		// colours don't change for this one
		this.strokeColor = this.colorArrangement.getStrokeColor();
		this.fillColor = this.colorArrangement.getFillColor();
		this.gameEventsManager = GameEventsManager.getInstance();
		this.listenForGameEvents();
	}
	
	private void listenForGameEvents() {
		gameEventsManager.addGameEventsListener(
				new GameEventsAdapter() {
					public void toggleSounds() {
						AnimatedSoundMessage.this.setText(AnimatedSoundMessage.this.soundManager.isPlayingSounds() ? SOUND_ON_MSG : SOUND_OFF_MSG);
						AnimatedSoundMessage.this.shownTime = System.currentTimeMillis();
						AnimatedSoundMessage.this.temporalPopupBox.drawUntil((int)Constants.VERY_LARGE_NUMBER); // indefinitely, let update switch it off
					}
				});
	}
	
	/*
	 * Have to override because like pause, the gametick is not updated in the animation loop
	 * and sound on is independent... implement using system timer instead
	 */
	@Override
	public void update(int gameTick) {
		// don't cycle through colours, just show the message
		long systemTime = System.currentTimeMillis();
		if (systemTime - this.shownTime > SECONDS_TO_ANIMATE * 1000) {
			AnimatedSoundMessage.this.temporalPopupBox.drawUntil(-1); // switch off
		}
		
	}
	
	
	
}