package firstReboundGame.gui;

import java.awt.geom.Rectangle2D;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

public class AnimatedScore extends AnimatedText
{
	private ScoringManager scoringManager;
	
	public AnimatedScore(Rectangle2D placementRectangle, ColorArrangement colorArrangement, ColorArrangement boxColorArrangement, ScoringManager scoringManager) {
		super(false, placementRectangle, colorArrangement, boxColorArrangement, null);
		this.scoringManager = scoringManager;
		this.listenForScoreChanges();
	}

	private void listenForScoreChanges() {
		scoringManager.addScoreChangeListener(
				new ScoreChangeListener() {
					public void finishedAPlay(int scoreForPlay, int totalScore, int streakWins, boolean extraLife) {
						if (!extraLife && streakWins > 1) {
							AnimatedScore.this.setText(""+streakWins+"x Bonus!");
							int drawTillTick = temporalComponentsManager.getGameTickSecondsFromNow(AnimatedText.SECONDS_TO_ANIMATE);
							AnimatedScore.this.temporalPopupBox.drawUntil(drawTillTick);
						}
					}
				});
	}
	
}