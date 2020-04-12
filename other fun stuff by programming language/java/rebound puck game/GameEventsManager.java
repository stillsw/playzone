package firstReboundGame;

import firstReboundGame.gameplay.*;
import firstReboundGame.gui.*;
import firstReboundGame.sound.SoundManager;

/*
 * Manager for general events to the whole game... gameOver means the whole game is done and ready to leave the program, 
 * not that a series of game plays is complete
 */
public class GameEventsManager
{
	private static String GAME_EXIT_CONFIRM = "Exit Game?";
	public static final int GAME_EVENT_TYPE_GAME_RUNNING = 0;
	public static final int GAME_EVENT_TYPE_GAME_NOT_RUNNING = 1;
	public static final int GAME_EVENT_TYPE_PAUSE_GAME = 2;
	public static final int GAME_EVENT_TYPE_RESUME_GAME = 3;
	public static final int GAME_EVENT_TYPE_TOGGLE_SOUND = 4;
	
	private static GameEventsManager instance;
	private GameBounds gameBounds = GameBounds.getInstance();
	
	private boolean isPaused = false;
	private boolean isPausedByPlayer = false;
	private boolean isRunning = false;
	
	// lazy init of listeners... these are created when they are first needed
	private boolean haveAnimatedPause = false;
	private boolean haveAnimatedSound = false;
	private ColorArrangement animatedTextColours;
	
	// listening for game events
	private GameEventsListener[] gameEventsListeners = new GameEventsListener[0];
	
	private GameEventsManager(ColorArrangement animatedTextColours) {
		this.animatedTextColours = animatedTextColours;
	}
	
	public static GameEventsManager getInstance() {
		if (instance == null) 
			instance = new GameEventsManager(ReboundGamePanel.animatedTextColours);
		return GameEventsManager.instance;
	}
	
	public boolean isPaused() { return this.isPaused; }
	public boolean isRunning() { return this.isRunning; }
	
	public void addGameEventsListener(GameEventsListener listener) {
		GameEventsListener[] newGameEventsListeners = new GameEventsListener[this.gameEventsListeners.length + 1];
		System.arraycopy(this.gameEventsListeners, 0, newGameEventsListeners, 0, this.gameEventsListeners.length);
		newGameEventsListeners[newGameEventsListeners.length - 1] = listener;
		this.gameEventsListeners = newGameEventsListeners;
	}
	
	/*
	 * Sets the game event and lets interested parties know about it
	 * playerAction indicates if the player initiated the event with something like a key press, as opposed to a
	 * system generated event, such as window iconified
	 *
	 * Lazy init of pause & sound animation (to speed up start up)
	 */
	public void setGameEventAndNotifyListeners(int eventType, boolean playerAction) {

		if (eventType == GAME_EVENT_TYPE_PAUSE_GAME && playerAction && !this.haveAnimatedPause) {
		    new AnimatedPauseMessage(gameBounds.getBounds(), animatedTextColours, ColorArrangement.NULL_COLOURS);
		    this.haveAnimatedPause = true;
		}
		
		if (eventType == GAME_EVENT_TYPE_TOGGLE_SOUND && !this.haveAnimatedSound) {
		    new AnimatedSoundMessage(gameBounds.getBounds(), animatedTextColours, ColorArrangement.NULL_COLOURS);
		    this.haveAnimatedSound = true;
		}
		
		
		boolean notifyAction = true; // don't propagate the event when paused stays paused because it's a system event
		switch (eventType) {
		case GAME_EVENT_TYPE_GAME_RUNNING : this.isRunning = true; break;
		case GAME_EVENT_TYPE_GAME_NOT_RUNNING : this.isRunning = false; break;
		case GAME_EVENT_TYPE_PAUSE_GAME : 
			if (this.isPaused) // paused already
				notifyAction = false;
			else
				this.isPaused = true;
			
			if (!this.isPausedByPlayer) // don't overwrite player pause with system pause
				this.isPausedByPlayer = playerAction;
			break;
		case GAME_EVENT_TYPE_RESUME_GAME : 
			if (playerAction || !this.isPausedByPlayer) // resume when player specifically requests it, unless it was only paused by system
				this.isPausedByPlayer = this.isPaused = false; 
			else
				notifyAction = false;
			break;
		case GAME_EVENT_TYPE_TOGGLE_SOUND : SoundManager.getInstance().toggleSound(); break;
		}
		
		if (notifyAction)
			for (int i = 0; i < this.gameEventsListeners.length; i++) {
				switch (eventType) {
				case GAME_EVENT_TYPE_GAME_RUNNING :	gameEventsListeners[i].gameRunning(); break;
				case GAME_EVENT_TYPE_GAME_NOT_RUNNING : gameEventsListeners[i].gameNotRunning(); break;
				case GAME_EVENT_TYPE_PAUSE_GAME : gameEventsListeners[i].gamePaused(); break;
				case GAME_EVENT_TYPE_RESUME_GAME : gameEventsListeners[i].gameResumed(); break;
				case GAME_EVENT_TYPE_TOGGLE_SOUND : gameEventsListeners[i].toggleSounds(); break;
				}
			}
	}
	
	public void requestCloseGame(ColorArrangement colorArrangement) {
		
		Action okAction = new Action() {
			public void doAction() {
				// stop the whole game to exit
				GameEventsManager.this.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_GAME_NOT_RUNNING, true);
			}
		};
		// ask for confirmation only if in a game
		if (ScoringManager.getInstance().inAGame())
			new DialogPopupBox(GAME_EXIT_CONFIRM, okAction, null);
		else
			okAction.doAction();

	}

}  // end of class

