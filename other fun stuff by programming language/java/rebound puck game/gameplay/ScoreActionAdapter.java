package firstReboundGame.gameplay;

/*
 * convenience adapter to save messy classes to implement the listener
 */
public class ScoreActionAdapter implements ScoreActionListener
{

	@Override
	public void extraLife(int numLives) {
	}
	@Override
	public void lostALife(int numLives) {
	}

	@Override
	public void finishedGame(ScoringManager scoringManager) {
	}

	@Override
	public void gameOver(ScoringManager scoringManager) {
	}


	@Override
	public void startedGame(ScoringManager scoringManager) {
	}
	@Override
	public void changedLevel(ScoringManager scoringManager) {
		// TODO Auto-generated method stub
		
	}
}