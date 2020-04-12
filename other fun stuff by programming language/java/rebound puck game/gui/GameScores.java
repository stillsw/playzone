package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

import java.awt.*;
import java.awt.geom.*;

public class GameScores extends GuiComponentAdapter
{
	private ScoringManager scoringManager;
	private int scoreForPlay;
	private int totalScore;
	
	public GameScores(ScoringManager scoringManager, ColorArrangement colorArrangement) {
		super(colorArrangement);
		this.scoringManager = scoringManager;
		this.listenForScoreChanges();
		this.listenForScoreActions();
	}
	
	private void listenForScoreChanges() {
		scoringManager.addScoreChangeListener(
				new ScoreChangeListener() {
					public void finishedAPlay(int scoreForPlay, int totalScore, int streakWins, boolean extraLife) {
						GameScores.this.scoreForPlay = scoreForPlay;
						GameScores.this.totalScore = totalScore;
					}
				});
	}
	
	private void listenForScoreActions() {
		scoringManager.addScoreActionListener(
				new ScoreActionAdapter() {
					public void startedGame(ScoringManager scoringManager) {
						GameScores.this.scoreForPlay = 0;
						GameScores.this.totalScore = 0;
					}
					public void changedLevel(ScoringManager scoringManager) {
						GameScores.this.scoreForPlay = 0;
						GameScores.this.totalScore = 0;
					}
				});
	}
		
	public boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
			g2.setColor(this.colorArrangement.getStrokeColor());
	    	int ypos = (int)(this.placementRectangle.getY() + this.placementRectangle.getHeight());
			g2.drawString(""+this.scoreForPlay, (int)this.placementRectangle.getX(), ypos - g2.getFontMetrics().getAscent());
			g2.drawString(""+this.totalScore, (int)this.placementRectangle.getX(), ypos);
	
//			g2.drawString("angle="+getStrikeAngle(), currXpos +10, currYpos -10);
//			g2.drawString(""+useStrength, strengthRect.x - 5, strengthRect.y + strengthRect.height + 15);
	    }
		return true; 
	}

}