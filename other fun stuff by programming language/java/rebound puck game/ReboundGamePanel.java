
// Thanks for code samples to 
// Andrew Davison, April 2005, ad@fivedots.coe.psu.ac.th

/* The game's drawing surface. It shows:
     - the game
     - the current average FPS and UPS
*/
package firstReboundGame;

import firstReboundGame.WallFactory.GoalSpec;
import firstReboundGame.gameplay.*;
import firstReboundGame.gui.*;
import firstReboundGame.sound.*;

import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;

public class ReboundGamePanel extends JPanel implements Runnable, GameEventsListener
{
	public static final int PANEL_WIDTH = 800;
	public static final int PANEL_HEIGHT = 600;
	public static final int PUCK_BOUNDARY_YPOS = (int)(PANEL_HEIGHT / 1.5);
	public static final Rectangle SCREEN_AREA = new Rectangle(PANEL_WIDTH, PANEL_HEIGHT);
	public static final int PUCK_DIAMETER = 42; 
	public static final int TARGET_DISK_DIAMETER = 48;
	public static final int PLAY_AREA_MARGIN = 70;
	private static ReboundGamePanel instance;
	public static int gameTick = 0;
	public static boolean ENABLE_MOUSE_CONTROL = true;
	public static final double DECELERATION_PER_20_TICKS = -0.1; 
	public static final double STOP_VELOCITY = .2; // if moving piece gets to this speed it is considered stopped
	public static final boolean RESTART_ONLY_WHEN_ALL_PIECES_STOP = false;
	public static final int MAX_RADIUS = (ReboundGamePanel.PUCK_DIAMETER > ReboundGamePanel.TARGET_DISK_DIAMETER 
			? (int)(ReboundGamePanel.PUCK_DIAMETER / 2) : (int)(ReboundGamePanel.TARGET_DISK_DIAMETER / 2));

	public static final double WALL_GOAL_CLIP_DEPTH = PLAY_AREA_MARGIN / 1.6;
	public static final double WALL_CLIP_DEPTH = PLAY_AREA_MARGIN / 3.0;
	public static final double WALL_GOAL_FRONT_AREA_DEPTH = WALL_CLIP_DEPTH / 1.1;
	private static final int GOAL_POST_WIDTH = MAX_RADIUS;
	public static Color darkShadowColour = new Color(45, 45, 42);
	public static Color midShadowColour = new Color(45, 45, 42, 150);
	public static Color lightShadowColour = new Color(150, 150, 128, 10);
	public static Color BACKGROUND_COLOUR = Color.black;
	public static boolean APPLY_RENDERING_HINTS_FOR_QUALITY = true;
	public static boolean MAKE_IMAGES = true;

	public static boolean showDebugging = false;
	
  private static long MAX_STATS_INTERVAL = 1000000000L;
  // private static long MAX_STATS_INTERVAL = 1000L;
    // record stats every 1 second (roughly)

  private static final int NO_DELAYS_PER_YIELD = 16;
  /* Number of frames with a delay of 0 ms before the animation thread yields
     to other running threads. */

  private static int MAX_FRAME_SKIPS = 5;   // was 2;
    // no. of frames that can be skipped in any one animation loop
    // i.e the games state is updated but not rendered

  private static int NUM_FPS = 10;
     // number of FPS values stored to get an average


  // used for gathering statistics
  private long statsInterval = 0L;    // in ns
  private long prevStatsTime;   
  private long totalElapsedTime = 0L;
  private long gameStartTime;
  private int timeSpentInGame = 0;    // in seconds

  private long frameCount = 0;
  private double fpsStore[];
  private long statsCount = 0;
  private double averageFPS = 0.0;

  private long framesSkipped = 0L;
  private long totalFramesSkipped = 0L;
  private double upsStore[];
  private double averageUPS = 0.0;


  private DecimalFormat df = new DecimalFormat("0.##");  // 2 dp
//  private DecimalFormat timedf = new DecimalFormat("0.####");  // 4 dp


  private Thread animator;           // the thread that performs the animation
  private long period;                // period between drawing in _nanosecs_

  public Puck puck;       // the puck
  private Striker striker;   // the striker
  private GameBounds gameBounds = GameBounds.getInstance();
  private StrikeableGamePiece[] staticObstacles;
  private MoveableGamePiece[] mobileObstacles;
  private PlayManager playManager;
  private ScoringManager scoringManager;
  private Shape mobileClippingZone; // restricts drawing of the playing elements to the play zone 
  private Shape staticClippingZone; // restricts drawing of the playing elements to the play zone 
  private PlayerFeedbackArea feedbackArea;
  // logic behind only drawing what needs to be drawn
  public boolean redrawEverything = true; // begin this way, after first draw this is set to false
  public boolean redrawJustIntoShading = false; 
  // manager to show temporary things, like messages and animations
  private TemporalComponentsManager temporalComponentsManager = TemporalComponentsManager.getInstance();
  // the animated text that appears in the middle of the screen when pause on or sound toggled are created as needed
  // AnimatedExtraLife and AnimatedScore created by scoring manager
  public static ColorArrangement animatedTextColours = new ColorArrangement(Color.BLUE, Color.WHITE, Color.CYAN, Color.GREEN, Color.ORANGE, Color.RED);
  // manager for game events (termination, pause, resume)
  private GameEventsManager gameEventsManager = GameEventsManager.getInstance();
  // pop up dialog, when set, control defers to it and other stuff is put into a pause
  private DialogPopupBox dialogInControl;
  private boolean aDialogIsInControl = false;
  public static ColorArrangement popupColours = new ColorArrangement(Color.gray, Color.orange);
  public static ColorArrangement guiColours = new ColorArrangement(Color.blue, Color.yellow);
  
  // used at game termination
  private int score = 0;
  private Font font;
  private FontMetrics metrics;

  // off screen rendering
  private Graphics2D dbg; 
  private Image dbImage = null;

  public ReboundGamePanel(FirstReboundGame gameTop, long period)
  {
// temp
//ScoringManager.runRuleTests();
//System.exit(0);
//    this.gameTop = gameTop;
    this.period = period;
    ReboundGamePanel.instance = this;
    // managers to handle play/drawing and scoring of the game
    this.playManager = new PlayManager(PlayManager.SCHEME_REPLAY_WHEN_ALL_PIECES_STOP);
    this.scoringManager = ScoringManager.getInstance(ScoringManager.GAME_STYLE_TARGET_SCORES_PUCK_PENALISES, this.playManager, animatedTextColours);     
    this.scoringManager.setDifficulty(ScoringManager.GAME_DIFFICULTY_EASY);

    ColorArrangement feedBackAreaColors = popupColours;
    this.feedbackArea = this.gameBounds.makePlayerFeedbackArea(GameBounds.EAST, PANEL_WIDTH, PANEL_HEIGHT, 140, PANEL_HEIGHT, 8, 3, 5, 8, feedBackAreaColors);
    this.gameBounds.setBoundsAndDisplays(new Rectangle(PLAY_AREA_MARGIN, PLAY_AREA_MARGIN, PANEL_WIDTH - PLAY_AREA_MARGIN * 2, PANEL_HEIGHT - PLAY_AREA_MARGIN * 2), true, this.scoringManager);
    // gui components in feedback area
    GuiText strikerAngleText = new GuiText(guiColours, (String)null); // text will be set each draw 
    StrikerAngleIndicator strikerAngleIndicator = new StrikerAngleIndicator(strikerAngleText, guiColours);
    this.feedbackArea.addGuiComponent(strikerAngleIndicator, 6, 0);
    this.feedbackArea.addGuiComponent(strikerAngleText, 6, 1);
    GuiText strikerStrengthText = new GuiText(guiColours, (String)null); // text will be set each draw 
    StrikerStrengthBar strikerStrengthBar = new StrikerStrengthBar(strikerStrengthText, guiColours);
    this.feedbackArea.addGuiComponent(strikerStrengthBar, 7, 0);
    this.feedbackArea.addGuiComponent(strikerStrengthText, 7, 1);
    ColorArrangement puckColours = new ColorArrangement(null, Color.GREEN, Color.CYAN, Color.GREEN);
    ColorArrangement playerLivesColours = new ColorArrangement(null, puckColours.getFillColor(), Color.red, null, null, feedBackAreaColors.getFillColor());
    PlayerLives playerLivesDisplay = new PlayerLives(ScoringManager.GAME_DEFAULT_LIVES, PUCK_DIAMETER, scoringManager, playerLivesColours);
    this.feedbackArea.addGuiComponent(playerLivesDisplay, 0, 0, 1, 3);
    GuiText scoresLabel = new GuiText(guiColours, new String[] {"Scores", "Strike:", "Game:"});
    GameScores gameScoresGui = new GameScores(scoringManager, guiColours);
    this.feedbackArea.addGuiComponent(scoresLabel, 1, 0);
    this.feedbackArea.addGuiComponent(gameScoresGui, 1, 2);
    this.feedbackArea.addGuiComponent(new DifficultyLevel(scoringManager, guiColours), 2, 0, 1, 3);
    // sound button serves as an indicator of sound, also gui control if mouse is enabled
    this.feedbackArea.addGuiComponent(new SoundButton(guiColours), 3, 0);
    if (ENABLE_MOUSE_CONTROL) {
        this.feedbackArea.addGuiComponent(new PauseButton(guiColours), 3, 1);
        this.feedbackArea.addGuiComponent(new NewGameButton(guiColours), 4, 0);
        this.feedbackArea.addGuiComponent(new ExitGameButton(guiColours), 4, 1);
	    this.addMouseListener(GuiComponentMouseManager.getInstance());
	    this.addMouseMotionListener(GuiComponentMouseManager.getInstance());
    }
    
    // preload all the sounds
    SoundManager.getInstance().preloadSounds();

    setBackground(gameBounds.getFillColor());
    gameBounds.setPuckStrikeBoundary(PUCK_BOUNDARY_YPOS, Color.RED);
    setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));

    setFocusable(true);
    requestFocus();    // the JPanel now has focus, so receives key events
    addKeyListener( new KeyAdapter() {
	  	  public void keyPressed(KeyEvent e)
		  	{ processKeyPressed(e); }
		  public void keyReleased(KeyEvent e)
		  	{ processKeyReleased(e); }
    	});

    // create game components
    Rectangle bounds = gameBounds.getBounds();
    
    // create the clipping zone for drawing (embedded goals will subtract from this in their creation)
    this.mobileClippingZone = gameBounds.getClippingVisibleZone();
    this.staticClippingZone = this.mobileClippingZone;// gameBounds.getWholeGamePlayArea();
    
    puck = new Puck(this.scoringManager, this.playManager, new Location2D(bounds.width / 2 + bounds.x, bounds.height / 1.25 + bounds.y), PUCK_DIAMETER / 2, puckColours); // start puck near bottom of panel
    striker = new Striker(puck);
    this.gameBounds.listenForStrikerChanges(striker);
    striker.setStrengthBar(strikerStrengthBar);
    striker.setAngleIndicator(strikerAngleIndicator);
    puck.setStriker(striker);
    
    // create one target disk now, this will depend on game type... maybe will have none or many of these
    Location2D pucksPlace = puck.getCurrPosn();
    double targetXpos = pucksPlace.getX();
    double targetYpos = bounds.height / 3 + bounds.y;
    
    // add all game pieces as obstacles
    ArrayList<StrikeableGamePiece> obstacles = new ArrayList<StrikeableGamePiece>();
    
    // make borders and goals as preset
    double goalWidthHeight = TARGET_DISK_DIAMETER * 2.0;
    double goalPostWidth = ReboundGamePanel.GOAL_POST_WIDTH;
    WallFactory wallFactory = WallFactory.getInstance();
    Rectangle rect = gameBounds.getBounds();
    // colours for the embedded goals have no fills... otherwise the goal area would be highlighted when struck
    ColorArrangement colorArrangement = new ColorArrangement(Color.blue, null, Color.orange, null, Color.red, Color.pink);
    double wallEndPieceRadius = ReboundGamePanel.GOAL_POST_WIDTH / 2.0;
//  wallFactory.makeObstaclesWithEmbeddedGoalsPreset1(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
  wallFactory.makeObstaclesWithEmbeddedGoalsPreset2(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
//  wallFactory.makeObstaclesWithEmbeddedGoalsPreset3(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
//	wallFactory.makeObstaclesWithEmbeddedGoalsPresetJustWalls(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
//	wallFactory.makeObstaclesWithEmbeddedGoalsPresetJustWallsAndSpaces(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
//	wallFactory.makeObstaclesWithEmbeddedGoalsPresetWallsAndSpacesAndGoals(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
//	wallFactory.makeObstaclesWithEmbeddedGoalsPresetOnlyGoals(rect, goalWidthHeight, goalPostWidth, wallEndPieceRadius, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, true, colorArrangement, obstacles);
    
    obstacles.add(puck);
    TargetDisk target = new TargetDisk(this.scoringManager, this.playManager, new Location2D(targetXpos, targetYpos), TARGET_DISK_DIAMETER / 2, new ColorArrangement(null, Color.BLUE, Color.CYAN, Color.BLUE));
    obstacles.add(target);
//    Goal goal1 = new Goal(new Location2D(PANEL_WIDTH / 3, targetYpos + 100), TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, Goal.GOAL_SHAPE_RECTANGULAR, null, Color.yellow, Color.cyan, Color.orange);
//    goal1.makePieces();
//    obstacles.add(goal1);
//    Goal goal2 = new Goal(new Location2D((PANEL_WIDTH / 3) * 2, targetYpos + 100), TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, Goal.GOAL_SHAPE_ROUND, null, Color.yellow, Color.cyan, Color.orange); 
//    goal2.makePieces();
//    obstacles.add(goal2);
    // south facing goal
//    this.addGatedGoalAsObstacle(obstacles, new GatedGoal(new Location2D((PANEL_WIDTH -100), targetYpos - 100), GOAL_POST_RADIUS, GatedGoal.GATED_CORNER_GOAL_NE, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, Goal.GOAL_SHAPE_RECTANGULAR, Color.blue, Color.yellow, Color.red, Color.orange, Color.LIGHT_GRAY));
//    this.addGatedGoalAsObstacle(obstacles, new GatedGoal(new Location2D(PANEL_WIDTH / 3, targetYpos - 100), GOAL_POST_RADIUS, GatedGoal.GATED_GOAL_FACING_NORTH, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, Goal.GOAL_SHAPE_RECTANGULAR, null, Color.yellow, Color.red, Color.orange, Color.LIGHT_GRAY));
//    this.addGatedGoalAsObstacle(obstacles, new GatedGoal(new Location2D(PANEL_WIDTH -100, targetYpos + 100), GOAL_POST_RADIUS, GatedGoal.GATED_GOAL_FACING_WEST, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, Goal.GOAL_SHAPE_RECTANGULAR, null, Color.yellow, Color.red, Color.orange, Color.LIGHT_GRAY));
//    this.addGatedGoalAsObstacle(obstacles, new GatedGoal(new Location2D(PANEL_WIDTH / 3, 500), GOAL_POST_RADIUS, GatedGoal.GATED_GOAL_FACING_EAST, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, TARGET_DISK_DIAMETER + TARGET_DISK_DIAMETER / 2, Goal.GOAL_SHAPE_RECTANGULAR, null, Color.yellow, Color.red, Color.orange, Color.LIGHT_GRAY));
    
    // create two arrays for the 2 kinds of obstacle
    ArrayList<StrikeableGamePiece> staticObs = new ArrayList<StrikeableGamePiece>();
    ArrayList<MoveableGamePiece> mobileObs = new ArrayList<MoveableGamePiece>();
    for (Iterator<StrikeableGamePiece> it = obstacles.iterator(); it.hasNext(); ) {
    	StrikeableGamePiece obs = it.next();
    	if (obs.isStatic()) 
    		staticObs.add(obs); 
    	else
    		mobileObs.add((MoveableGamePiece)obs);
    }
    staticObstacles = new StrikeableGamePiece[staticObs.size()];
    mobileObstacles = new MoveableGamePiece[mobileObs.size()];
    staticObstacles = staticObs.toArray(staticObstacles);
    mobileObstacles = mobileObs.toArray(mobileObstacles);
    playManager.registerPieces(mobileObstacles);
    ScoringManager.registerPieces(mobileObstacles, staticObstacles);
    
    // clip the out of play area for any of the static pieces
    gameBounds.makeOutOfPlayAreaImage(staticObstacles);

    // set up message font
    font = new Font("SansSerif", Font.BOLD, 12);
    metrics = this.getFontMetrics(font);

    // initialise timing elements
    fpsStore = new double[NUM_FPS];
    upsStore = new double[NUM_FPS];
    for (int i=0; i < NUM_FPS; i++) {
      fpsStore[i] = 0.0;
      upsStore[i] = 0.0;
    }
  }  // end of constructor

  public void setDialogPopupBox(DialogPopupBox dialogInControl) {
	  this.dialogInControl = dialogInControl;
	  this.aDialogIsInControl = true;
		this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_PAUSE_GAME, false);
		this.setDrawEverthing();
  }
  public void removeDialogPopupBox() {
	  this.dialogInControl = null;
	  this.aDialogIsInControl = false;
		this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_RESUME_GAME, false);
		this.setDrawEverthing();
  }
  public boolean aDialogIsInControl() { return this.aDialogIsInControl; }
  
  
//  private void addGatedGoalAsObstacle(ArrayList<StrikeableGamePiece> obstacles, GatedGoal gatedGoal) {
//	  gatedGoal.makePieces(); 
//	  obstacles.add(gatedGoal);
//	   StrikeableGamePiece[] goalPieces = gatedGoal.getObstacles();
//	   for (int i = 0; i < goalPieces.length; i++)
//		   obstacles.add(goalPieces[i]);
//  }
  public static ReboundGamePanel getInstance() { return ReboundGamePanel.instance; }
  public StrikeableGamePiece[] getStaticObstacles() { return this.staticObstacles; }
  public MoveableGamePiece[] getMobileObstacles() { return this.mobileObstacles; }

  private void processKeyPressed(KeyEvent e)
  {
	int keyCode = e.getKeyCode();
	// is a dialog in control right now?
	if (this.aDialogIsInControl && this.dialogInControl != null) {
		this.dialogInControl.processKeyPressed(keyCode);
	}
	// terminations
	// listen for esc, q, end, ctrl-c on the canvas to
	// allow a convenient exit from the full screen configuration  
	else if ((keyCode == KeyEvent.VK_ESCAPE) || (keyCode == KeyEvent.VK_Q) ||
//	     (keyCode == KeyEvent.VK_END) ||
	     ((keyCode == KeyEvent.VK_C) && e.isControlDown()) ) {
		this.gameEventsManager.requestCloseGame(ReboundGamePanel.popupColours);
	 }
	else if (keyCode == KeyEvent.VK_P) {
		if (this.gameEventsManager.isPaused())
			this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_RESUME_GAME, true);
		else
			this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_PAUSE_GAME, true);
	}
	else if (keyCode == KeyEvent.VK_S)  
	{
		// ability to toggle sound on/off
		this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_TOGGLE_SOUND, true);
	}
	else if (keyCode == KeyEvent.VK_D)  
	{
		// ability to toggle debugging of collisions
		ReboundGamePanel.showDebugging = !ReboundGamePanel.showDebugging;
	}
	else if (!this.gameEventsManager.isPaused()) {
		
		// can start a new game if not paused
		if (keyCode == KeyEvent.VK_N)  
		{
			scoringManager.requestNewGame(false, 0, ReboundGamePanel.popupColours);
		}
		// change level with number keys 1 - 3
		else if (keyCode == KeyEvent.VK_1 || keyCode == KeyEvent.VK_2 || keyCode == KeyEvent.VK_3)  
		{
			int newLevel = ScoringManager.GAME_DIFFICULTY_EASY;
			if (keyCode == KeyEvent.VK_2) newLevel = ScoringManager.GAME_DIFFICULTY_MEDIUM;
			else if (keyCode == KeyEvent.VK_3) newLevel = ScoringManager.GAME_DIFFICULTY_HARD;
			scoringManager.requestNewGame(true, newLevel, ReboundGamePanel.popupColours);
		}
		// game-play keys available only when stuff isn't moving
		else if (!this.scoringManager.inAPlay()) {
			if (keyCode == KeyEvent.VK_SLASH)  
			{
				// ability to change the strike velocity
				if (!puck.isMoving()) {
					if (!striker.isStriking())
						striker.startStriking();
					
					striker.increaseStrength();
				}
			}
			else if (keyCode == KeyEvent.VK_BACK_SLASH)  
			{
				// ability to change the strike velocity
				if (!puck.isMoving()) {
					if (!striker.isStriking())
						striker.startStriking();
					
					striker.decreaseStrength();
				}
			}
			else if (keyCode == KeyEvent.VK_ENTER)  
			{
				if (!puck.isMoving()) {
					striker.stopStriking(true);
					puck.struckByStriker();
				}
			}
			else if (keyCode == KeyEvent.VK_T)  
			{
				// ability to toggle targetting of the striker
				striker.toggleTargetting();
			}
			else if ((keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT 
					|| keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) && !puck.isMoving && !puck.isWaitingToRestart)  
			{
				// ability to change where the puck is before it's hit
				puck.changeStaticPosition(keyCode);			
			}
			else if (keyCode == KeyEvent.VK_L || keyCode == KeyEvent.VK_R)  
			{
				// ability to change the aim of the striker
				if (!puck.isMoving())
					striker.aim((keyCode == KeyEvent.VK_L ? Striker.AIM_LEFT : Striker.AIM_RIGHT));
			}
		}
	}
  }  // end of processKey()

  private void processKeyReleased(KeyEvent e)
  {
	// game-play keys
	if (!this.gameEventsManager.isPaused() && !this.scoringManager.inAGame()) {
	
		int keyCode = e.getKeyCode();
//		if (keyCode == KeyEvent.VK_SHIFT) {
//			if (!puck.isMoving()) // ignore repeated down keys when already moving
//			{
//				striker.stopStriking(true);
//				puck.struckByStriker();
//				puck.setMoving(true);
//			}
//			
//		}
	}
  }  // end of processKey()


  public void addNotify()
  // wait for the JPanel to be added to the JFrame before starting
  { super.addNotify();   // creates the peer
    startGame();         // start the thread
  }


  private void startGame()
  // initialise and start the thread 
  { 
    if (animator == null || !this.gameEventsManager.isRunning()) {
      animator = new Thread(this);
	  animator.start();
    }
  } // end of startGame()
    

  public void run()
  /* The frames of the animation are drawn inside the while loop. */
  {
    long beforeTime, afterTime, timeDiff, sleepTime;
    long overSleepTime = 0L;
    int noDelays = 0;
    long excess = 0L;

    gameStartTime = System.nanoTime();
    prevStatsTime = gameStartTime;
    beforeTime = gameStartTime;

	this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_GAME_RUNNING, false);

	while(this.gameEventsManager.isRunning()) {
	  gameUpdate();
      gameRender();
      paintScreen();

      afterTime = System.nanoTime();
      timeDiff = afterTime - beforeTime;
      sleepTime = (period - timeDiff) - overSleepTime;  

      if (sleepTime > 0) {   // some time left in this cycle
        try {
          Thread.sleep(sleepTime/1000000L);  // nano -> ms
        }
        catch(InterruptedException ex){}
        overSleepTime = (System.nanoTime() - afterTime) - sleepTime;
      }
      else {    // sleepTime <= 0; the frame took longer than the period
        excess -= sleepTime;  // store excess time value
        overSleepTime = 0L;

        if (++noDelays >= NO_DELAYS_PER_YIELD) {
          Thread.yield();   // give another thread a chance to run
          noDelays = 0;
        }
      }

      beforeTime = System.nanoTime();

      /* If frame animation is taking too long, update the game state
         without rendering it, to get the updates/sec nearer to
         the required FPS. */
      int skips = 0;
      while((excess > period) && (skips < MAX_FRAME_SKIPS)) {
        excess -= period;
	    gameUpdate();    // update state but don't render
        skips++;
      }
      framesSkipped += skips;

      storeStats();
	}

    printStats();
    System.exit(0);   // so window disappears
  } // end of run()


  private void gameUpdate() 
  { 
	  boolean isPaused = this.gameEventsManager.isPaused();
	  if (!this.aDialogIsInControl && !isPaused && this.scoringManager.inAGame()) {
		  ReboundGamePanel.gameTick++;
		  
		    for (int i = 0; i < this.mobileObstacles.length; i++) {
		    	if (playManager.canPlayPiece(mobileObstacles[i], ReboundGamePanel.gameTick)) {
		    		mobileObstacles[i].move(ReboundGamePanel.gameTick);
		    		if (!this.redrawEverything && !this.redrawJustIntoShading) {
		    			Rectangle2D movementBounds = mobileObstacles[i].getMovementBounds();
			    		double radius = (movementBounds != null ? movementBounds.getWidth() / 2 : mobileObstacles[i].getRadius());
			    		if (!this.gameBounds.gameZoneIsInPlay(mobileObstacles[i].getCurrPosn().getX(), mobileObstacles[i].getCurrPosn().getY(), radius)) 
		    				this.setDrawJustIntoShading();
		    		}
		    	}
		    }
		    
		    for (int i = 0; i < this.staticObstacles.length; i++) {
		    	staticObstacles[i].update(ReboundGamePanel.gameTick, this.mobileObstacles);
		    }
		    
	    ScoringManager.detectGoals(ReboundGamePanel.gameTick);
	    this.feedbackArea.update(gameTick);	    
	  }
	  else if (isPaused) { // just keep drawing everything, not much else happening anyway
		  this.setDrawEverthing();
		  this.gameBounds.triggerDrawFeedbackArea();
	  }

	  // update pop up messages regardless (they determine themselves to ignore the game state)
	  this.temporalComponentsManager.update(gameTick);
	  
  }  // end of gameUpdate()

  private void gameRender()
  {
    if (dbImage == null){
      dbImage = createImage(PANEL_WIDTH, PANEL_HEIGHT);
      if (dbImage == null) {
        System.out.println("dbImage is null");
        return;
      }
      else {
        dbg = (Graphics2D)dbImage.getGraphics();
      }
    }

    if (this.redrawEverything) {
	    dbg.setColor(BACKGROUND_COLOUR);
	    dbg.fillRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
    }
    // show fps etc only when debug
    if (showDebugging) {
	    dbg.setColor(Color.white);
	    String output = "Average FPS/UPS: " + df.format(averageFPS) + ", " + df.format(averageUPS);
	    FontMetrics fm = dbg.getFontMetrics();
	    Rectangle2D rect = fm.getStringBounds(output, dbg);
	    dbg.fillRect(20, 25 - (int)rect.getHeight() + fm.getDescent(), (int)rect.getWidth(), (int)rect.getHeight());
	    dbg.setColor(Color.blue);
	    dbg.setFont(font);
		dbg.drawString(output, 20, 25);  // was (10,55)
    }

	// draw the playing area
	gameBounds.draw(dbg);

    // puck and targets
    dbg.setClip(staticClippingZone);
    
    boolean struckPiece = false;
    for (int i = 0; i < this.staticObstacles.length; i++) 
		if (staticObstacles[i].isStruck())
			struckPiece = true;
		else
			if (!staticObstacles[i].deferDrawing())
    			staticObstacles[i].draw(dbg);
    // struckPiece has been registered, draw it last
    if (struckPiece)
        for (int i = 0; i < this.staticObstacles.length; i++) 
    		if (staticObstacles[i].isStruck())
       			staticObstacles[i].draw(dbg);
    
    // mobile things will travel beyond the clip zone, so apply it now
    dbg.setClip(mobileClippingZone);
    for (int i = 0; i < this.mobileObstacles.length; ) 
    	mobileObstacles[i++].draw(dbg);
    
    // draw transparent shading over anything mobile
    dbg.setClip(staticClippingZone);
    if (this.redrawEverything || this.redrawJustIntoShading)
    	gameBounds.fillShadedZone(dbg);
    dbg.setClip(null);

    // anything to draw after the shading
    for (int i = 0; i < this.staticObstacles.length; i++) 
    	staticObstacles[i].drawAfterShading(dbg);

    gameBounds.drawAfterShading(dbg);
    
    // anything to popup over the display
    this.applyRenderingHints(dbg, true);
    this.temporalComponentsManager.drawTemporalComponents(dbg);
    this.applyRenderingHints(dbg, false);

    // next time redraw only what is needed unless set to draw everything again by another event
    // the exception to this is if there's a dialog up, then fill partially transparent and draw the dialog
    if (this.aDialogIsInControl) {
		this.redrawEverything = true;
    	dbg.setColor(midShadowColour);
    	dbg.fill(this.getBounds());
    	try { 
    		this.dialogInControl.drawGuiComponent(dbg);
    	}
    	catch (NullPointerException e) {}// keys and mouse are animation loop independent, so in case this isn't here by now
    }
    else {
		this.redrawEverything = false;
    }
	this.redrawJustIntoShading = false;

//	if (!this.scoringManager.inAGame())
//      gameOverMessage(dbg);

  }  // end of gameRender()

  /*
   * How to decide to draw a game piece (this includes drawing after shading):
   * 1) Draw everything triggered (by calling ReboundGamePanel.redrawEverything) because: 
   * 	a. start of game or other event such as pop up help has been displayed
   * 	b. window has been de-iconified, (applet version browser has been de-iconified)
   * 	c. any other event signals a redraw by calling ReboundGamePanel.redrawEverything
   * 2) A piece has signaled draw me once by setting itself to be drawn next render cycle (turns itself off after draw)
   * 3) A piece has signaled draw me until a gameTick and is drawn upto that gameTick and until it gets drawn NB:
   * 	a) such as when a piece will react in time, like a goal flashing, it should calculate the gameTick when the flash will stop
   * 		and set itself to redraw till then
   * 	b) if the piece is part of another piece, it should signal to that piece to draw too
   * 4) A moving piece should create a bounding box around which anything inside will re-draw
   * 	a) applies to striker... bound box is around where it was last drawn (note drawn not updated) and where to draw next
   * 	b) same logic for mobile pieces
   * 5) If a redraw is signaled because moving piece hits part of perimeter, redraw everything, it's simpler that way
   * 6) Plan to only draw the inside playing area with the following provisos
   * 	a) piece outside the playing area, draw everything (except feedback area, unless signaled)
   * 	b) other reasons to draw everything as above 
   */
  public void setDrawEverthing() {
	  this.redrawEverything = true;
	  this.redrawJustIntoShading = false;
	  this.gameBounds.triggerDrawFeedbackArea();
  }
  
  public void setDrawJustIntoShading() {
	  this.redrawJustIntoShading = true;
  }
  
  public void applyRenderingHints(Graphics2D g2, boolean on) {
	  if (APPLY_RENDERING_HINTS_FOR_QUALITY)
		  if (on) {
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		  }
		  else {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		  }
  }
  
  private void gameOverMessage(Graphics g)
  // center the game-over message in the panel
  {
    String msg = "Game Over. Your Score: " + score;
	int x = (PANEL_WIDTH - metrics.stringWidth(msg))/2; 
	int y = (PANEL_HEIGHT - metrics.getHeight())/2;
	g.setColor(Color.red);
    g.setFont(font);
	g.drawString(msg, x, y);
  }  // end of gameOverMessage()


  private void paintScreen()
  // use active rendering to put the buffered image on-screen
  { 
    Graphics g;
    try {
      g = this.getGraphics();
      if ((g != null) && (dbImage != null))
        g.drawImage(dbImage, 0, 0, null);
      g.dispose();
    }
    catch (Exception e)
    { System.out.println("Graphics context error: " + e);  }
  } // end of paintScreen()


  private void storeStats()
  /* The statistics:
       - the summed periods for all the iterations in this interval
         (period is the amount of time a single frame iteration should take), 
         the actual elapsed time in this interval, 
         the error between these two numbers;

       - the total frame count, which is the total number of calls to run();

       - the frames skipped in this interval, the total number of frames
         skipped. A frame skip is a game update without a corresponding render;

       - the FPS (frames/sec) and UPS (updates/sec) for this interval, 
         the average FPS & UPS over the last NUM_FPSs intervals.

     The data is collected every MAX_STATS_INTERVAL  (1 sec).
  */
  { 
    frameCount++;
    statsInterval += period;

    if (statsInterval >= MAX_STATS_INTERVAL) {     // record stats every MAX_STATS_INTERVAL
      long timeNow = System.nanoTime();
      timeSpentInGame = (int) ((timeNow - gameStartTime)/1000000000L);  // ns --> secs
//      gameTop.setTimeSpent( timeSpentInGame );

      long realElapsedTime = timeNow - prevStatsTime;   // time since last stats collection
      totalElapsedTime += realElapsedTime;

//      double timingError = 
//         ((double)(realElapsedTime - statsInterval) / statsInterval) * 100.0;

      totalFramesSkipped += framesSkipped;

      double actualFPS = 0;     // calculate the latest FPS and UPS
      double actualUPS = 0;
      if (totalElapsedTime > 0) {
        actualFPS = (((double)frameCount / totalElapsedTime) * 1000000000L);
        actualUPS = (((double)(frameCount + totalFramesSkipped) / totalElapsedTime) 
                                                             * 1000000000L);
      }

      // store the latest FPS and UPS
      fpsStore[ (int)statsCount%NUM_FPS ] = actualFPS;
      upsStore[ (int)statsCount%NUM_FPS ] = actualUPS;
      statsCount = statsCount+1;

      double totalFPS = 0.0;     // total the stored FPSs and UPSs
      double totalUPS = 0.0;
      for (int i=0; i < NUM_FPS; i++) {
        totalFPS += fpsStore[i];
        totalUPS += upsStore[i];
      }

      if (statsCount < NUM_FPS) { // obtain the average FPS and UPS
        averageFPS = totalFPS/statsCount;
        averageUPS = totalUPS/statsCount;
      }
      else {
        averageFPS = totalFPS/NUM_FPS;
        averageUPS = totalUPS/NUM_FPS;
      }
/*
      System.out.println(timedf.format( (double) statsInterval/1000000000L) + " " + 
                    timedf.format((double) realElapsedTime/1000000000L) + "s " + 
			        df.format(timingError) + "% " + 
                    frameCount + "c " +
                    framesSkipped + "/" + totalFramesSkipped + " skip; " +
                    df.format(actualFPS) + " " + df.format(averageFPS) + " afps; " + 
                    df.format(actualUPS) + " " + df.format(averageUPS) + " aups" );
*/
      framesSkipped = 0;
      prevStatsTime = timeNow;
      statsInterval = 0L;   // reset
    }
  }  // end of storeStats()


  private void printStats()
  {
    System.out.println("Frame Count/Loss: " + frameCount + " / " + totalFramesSkipped);
	System.out.println("Average FPS: " + df.format(averageFPS));
	System.out.println("Average UPS: " + df.format(averageUPS));
    System.out.println("Time Spent: " + timeSpentInGame + " secs");
  }  // end of printStats()

	@Override
	public void gameNotRunning() {}
	
	@Override
	public void gamePaused() {
		this.setDrawEverthing();		
	}
	
	@Override
	public void gameResumed() {
		this.setDrawEverthing();	
	}
	
	@Override
	public void gameRunning() {}

	@Override
	public void toggleSounds() {}

}  // end of ReboundAnimationPanel class
