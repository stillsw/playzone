package firstReboundGame;

public interface GameEventsListener 
{
	public void toggleSounds();
	public void gamePaused();
	public void gameResumed();
	public void gameRunning();
	public void gameNotRunning();
	
	
}