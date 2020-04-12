package firstReboundGame.gui;

import java.awt.geom.Rectangle2D;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

public class AnimatedExtraLife extends AnimatedText
{
	private ScoringManager scoringManager;
	
	public AnimatedExtraLife(Rectangle2D placementRectangle, ColorArrangement colorArrangement, ColorArrangement boxColorArrangement, ScoringManager scoringManager) {
		super(false, placementRectangle, colorArrangement, boxColorArrangement, "Extra Life Bonus!");
		this.scoringManager = scoringManager;
		this.listenForScoreActions();
	}

	private void listenForScoreActions() {
		scoringManager.addScoreActionListener(
				new ScoreActionAdapter() {
					public void extraLife(int numLives) {
						int drawTillTick = temporalComponentsManager.getGameTickSecondsFromNow(AnimatedText.SECONDS_TO_ANIMATE);
						AnimatedExtraLife.this.temporalPopupBox.drawUntil(drawTillTick);
					}
				});
	}
	
}