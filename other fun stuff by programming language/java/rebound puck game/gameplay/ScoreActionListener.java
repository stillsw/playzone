package firstReboundGame.gameplay;

import java.awt.*;
import java.awt.geom.Point2D;

public interface ScoreActionListener 
{
	public void extraLife(int numLives);
	
	public void lostALife(int numLives);
	
	public void gameOver(ScoringManager scoringManager);
	
	public void startedGame(ScoringManager scoringManager);
	
	public void finishedGame(ScoringManager scoringManager);
	
	public void changedLevel(ScoringManager scoringManager);
	
//	public void startedAPlay();

//	public void finishedAPlay();
	
}