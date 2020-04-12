package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.sound.SoundManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;

public class SoundButton extends MouseButton
{
	private static String TOOL_TIP_TEXT_SOUND_ON = "Sound On";
	private static String TOOL_TIP_TEXT_SOUND_OFF = "Sound Off";
	private GameEventsManager gameEventsManager;
//	private Area speaker;
	private SoundManager soundManager = SoundManager.getInstance();
	private boolean soundOn = soundManager.isPlayingSounds();
	private final static BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 2.0f, new float[] {2.0f}, 0.0f);
	
	public SoundButton(ColorArrangement colorArrangement) {
		super(SoundManager.getInstance().isPlayingSounds() ? SoundButton.TOOL_TIP_TEXT_SOUND_OFF : SoundButton.TOOL_TIP_TEXT_SOUND_ON, colorArrangement, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS
								| GuiComponentMouseManager.MOUSE_EVENT_STATE_GAME_PAUSED);
		this.gameEventsManager = GameEventsManager.getInstance();
		this.listenForGameEvents();
	}

	private void listenForGameEvents() {
		gameEventsManager.addGameEventsListener(
				new GameEventsAdapter() {
					public void toggleSounds() {
						SoundButton.this.soundOn = SoundButton.this.soundManager.isPlayingSounds();
						SoundButton.this.setToolTipText(SoundButton.this.soundOn ? SoundButton.TOOL_TIP_TEXT_SOUND_OFF : SoundButton.TOOL_TIP_TEXT_SOUND_ON);
						SoundButton.this.gameBounds.triggerDrawFeedbackArea(); 
					}
				});
	}
	
	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		super.mousePressed(arg0);
		this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_TOGGLE_SOUND, true);
	}

	@Override
	protected void drawButtonInterior(Graphics2D g2, Stroke originalStroke) {
		
		Ellipse2D circle = (Ellipse2D)this.buttonShape;
        int xpos = (int)circle.getCenterX(); 
        int ypos = (int)circle.getCenterY(); // make lines 8 long
        int diff = 8;
		g2.drawLine(xpos - diff, ypos, xpos, ypos - diff);
		g2.drawLine(xpos - diff, ypos, xpos, ypos + diff);
		if (this.soundOn) {
			g2.setStroke(originalStroke);
        	g2.drawLine(xpos, ypos - 2, xpos + diff, ypos - 4);
//        	g2.drawLine(xpos, ypos, xpos + diff, ypos);
        	g2.drawLine(xpos, ypos + 2, xpos + diff, ypos + 4);
        }
        else {
        	g2.setStroke(DASHED_STROKE);
        	g2.drawLine(xpos, ypos, xpos + (int)this.radius, ypos);
        }
	}

}