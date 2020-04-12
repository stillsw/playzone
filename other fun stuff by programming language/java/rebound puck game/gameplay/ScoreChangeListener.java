package firstReboundGame.gameplay;

public interface ScoreChangeListener 
{
	public void finishedAPlay(int scoreForPlay, int totalScore, int streakWins, boolean extraLife);
	
}