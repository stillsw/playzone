package firstReboundGame.gameplay;

import firstReboundGame.gui.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

import firstReboundGame.*;
import firstReboundGame.gui.*;

public abstract class ScoringManager
{
	public static int GAME_DEFAULT_LIVES = 3; 
	public static int GAME_DEFAULT_WINS_FOR_EXTRA_LIFE = 3; 
	public static int GAME_DEFAULT_LOSSES_FOR_LOSE_LIFE = 5; 
	public static final int GAME_SCORE_MULTIPLIER = 100; // game score increments multiplied by this to look nicer (bigger)
	public static final int GAME_STYLE_TARGET_SCORES_PUCK_PENALISES = 0;
	public static final int GAME_DIFFICULTY_EASY = 1;
	public static final int GAME_DIFFICULTY_MEDIUM = 3;
	public static final int GAME_DIFFICULTY_HARD = 5;
	public static final int EXTRA_LIFE_BONUS_MULTIPLIER = 1000;
	
	// listener events
	private static final int SCORING_ACTION_EVENT_EXTRA_LIFE = 0;
	private static final int SCORING_ACTION_EVENT_LOST_LIFE = 1;
	private static final int SCORING_ACTION_EVENT_GAME_OVER = 2;
	private static final int SCORING_ACTION_EVENT_START_GAME = 3;
	private static final int SCORING_ACTION_EVENT_FINISH_GAME = 4;
	private static final int SCORING_ACTION_EVENT_CHANGE_LEVEL = 5;

	// labels and texts
	private static String NEW_GAME_CONFIRMATION_TEXT = "Quit the current game and start a new one?";
	private static String CHANGE_LEVEL_CONFIRMATION_TEXT = "Change level, quit the current game?";
	private static String DIFFICULTY_DESC_EASY = "Easy";
	private static String DIFFICULTY_DESC_MED = "Medium";
	private static String DIFFICULTY_DESC_HARD = "Hard";
	
	public static ScoringManager instance;
	private static int gameStyle;

	protected int difficulty;
	protected PlayManager playManager;
	
	// tracking of multiples
	protected int streakWins;
	protected int streakLosses;
	protected int numberOfLives = GAME_DEFAULT_LIVES;
	protected boolean lastPlayScored = true;
	
	// scores/penalties for the whole game
	protected int puckScores;
	protected int puckPenalties;
	protected int otherScores;
	protected int otherPenalties;
	protected int extraLifeBonusScores;
	protected int overAllScore;
	protected boolean inAGame = false;
	// scores/penalties for the single play within a game (a play is from puck strike till all pieces stop)
	protected int puckPlayScores;
	protected int puckPlayPenalties;
	protected int otherPlayScores;
	protected int otherPlayPenalties;
	protected int extraLifeBonusPlayScores;
	protected int scoreForLastPlay;
	protected boolean inAPlay = false;
	
	// lazy init of listeners... these are created when they are first needed
	private boolean haveAnimatedExtraLife = false;
	private boolean haveAnimatedScore = false;
	private ColorArrangement animatedTextColours;
	protected GameBounds gameBounds = GameBounds.getInstance();
	
	// listening for score events
	private ScoreActionListener[] scoreActionListeners = new ScoreActionListener[0];
	private ScoreChangeListener[] scoreChangeListeners = new ScoreChangeListener[0];
	
	protected ScoringManager(int gameStyle, PlayManager playManager, ColorArrangement animatedTextColours) {
		ScoringManager.gameStyle = gameStyle;
		this.playManager = playManager;
		this.animatedTextColours = animatedTextColours;
	}

	/*
	 * initialize and return instance with for appropriate type
	 */
	public static ScoringManager getInstance(int gameStyle, PlayManager playManager, ColorArrangement animatedTextColours) {
		if (ScoringManager.instance == null || ScoringManager.gameStyle != gameStyle) {
			if (gameStyle == ScoringManager.GAME_STYLE_TARGET_SCORES_PUCK_PENALISES)
				ScoringManager.instance = new OnlyTargetScores(gameStyle, playManager, animatedTextColours);
		}
		return ScoringManager.instance;
	}
	
	public static ScoringManager getInstance() {
		return ScoringManager.instance;
	}
	
	private static MoveableGamePiece[] mobileObstacles;
	private static GatedGoal[] goals;
	
	public static void registerPieces(MoveableGamePiece[] mobileObstacles, StrikeableGamePiece[] staticObstacles) {
		ScoringManager.mobileObstacles = mobileObstacles;
		ArrayList<GatedGoal> goalsList = new ArrayList<GatedGoal>();
		for (int i = 0; i < staticObstacles.length; i++)
			if (staticObstacles[i] instanceof GatedGoal) {
				goalsList.add((GatedGoal)staticObstacles[i]);
			}
		ScoringManager.goals = new GatedGoal[goalsList.size()];
		goalsList.toArray(ScoringManager.goals);
	}
	
	public static void detectGoals(int gameTick) {
		for (int j = 0; j < mobileObstacles.length; j++) {
			if (mobileObstacles[j].isMoving() && mobileObstacles[j] instanceof TargetDisk) {
				TargetDisk movingDisk = (TargetDisk)mobileObstacles[j];
				for (int i = 0; i < ScoringManager.goals.length; i++) {
					if (ScoringManager.goals[i].detectScoredGoal(movingDisk)) {
						movingDisk.goalScored(gameTick);
						boolean scored = instance.registerGoal(movingDisk);
						ScoringManager.goals[i].reactToGoal(movingDisk, scored, gameTick);
//						if (scored)
//							System.out.println("ScoringManager.registerGoals: scored goal by "+movingDisk);
					}
				}
			}
		}
	}
	
	public boolean inAGame() { return this.inAGame; }
	public boolean inAPlay() { return this.inAPlay; }
	public int getNumberOfLives() { return this.numberOfLives; }
	public int getDifficultyLevel() { return this.difficulty; }
	public String getDifficultyLevelDesc() {
		switch (this.difficulty) {
		case GAME_DIFFICULTY_EASY : return ScoringManager.DIFFICULTY_DESC_EASY;
		case GAME_DIFFICULTY_MEDIUM : return ScoringManager.DIFFICULTY_DESC_MED;
		case GAME_DIFFICULTY_HARD : return ScoringManager.DIFFICULTY_DESC_HARD;
		default : return null;
		}
	}
	
	public void addScoreActionListener(ScoreActionListener listener) {
		ScoreActionListener[] newScoreActionListeners = new ScoreActionListener[this.scoreActionListeners.length + 1];
		System.arraycopy(this.scoreActionListeners, 0, newScoreActionListeners, 0, this.scoreActionListeners.length);
		newScoreActionListeners[newScoreActionListeners.length - 1] = listener;
		this.scoreActionListeners = newScoreActionListeners;
	}
	
	/*
	 * Lazy init of extra life animation (to speed up start up)
	 */
	public void notifyScoreActionListeners(int eventType) {
		
		if (eventType == SCORING_ACTION_EVENT_EXTRA_LIFE && !this.haveAnimatedExtraLife) {
		    new AnimatedExtraLife(gameBounds.getBounds(), animatedTextColours, ColorArrangement.NULL_COLOURS, this);
		    this.haveAnimatedExtraLife = true;
		}
		
		for (int i = 0; i < this.scoreActionListeners.length; i++) {
			switch (eventType) {
			case SCORING_ACTION_EVENT_EXTRA_LIFE : scoreActionListeners[i].extraLife(this.numberOfLives); break;
			case SCORING_ACTION_EVENT_LOST_LIFE : scoreActionListeners[i].lostALife(this.numberOfLives); break;
			// rest still to implement
			case SCORING_ACTION_EVENT_GAME_OVER : break;
			case SCORING_ACTION_EVENT_START_GAME : scoreActionListeners[i].startedGame(this); break;
			case SCORING_ACTION_EVENT_FINISH_GAME : break;
			case SCORING_ACTION_EVENT_CHANGE_LEVEL : scoreActionListeners[i].changedLevel(this); break;
			}
		}
	}
	
	public void addScoreChangeListener(ScoreChangeListener listener) {
		ScoreChangeListener[] newScoreChangeListeners = new ScoreChangeListener[this.scoreChangeListeners.length + 1];
		System.arraycopy(this.scoreChangeListeners, 0, newScoreChangeListeners, 0, this.scoreChangeListeners.length);
		newScoreChangeListeners[newScoreChangeListeners.length - 1] = listener;
		this.scoreChangeListeners = newScoreChangeListeners;
	}
	
	/*
	 * Lazy init of score animation (to speed up start up)
	 */
	public void notifyScoreChangeListeners() {
		if (!this.haveAnimatedScore) {
		    new AnimatedScore(gameBounds.getBounds(), animatedTextColours, ColorArrangement.NULL_COLOURS, this);
		    this.haveAnimatedScore = true;
		}
		
		for (int i = 0; i < this.scoreChangeListeners.length; i++) {
			scoreChangeListeners[i].finishedAPlay(this.scoreForLastPlay, this.overAllScore, this.streakWins, (float)this.streakWins % ScoringManager.GAME_DEFAULT_WINS_FOR_EXTRA_LIFE == 0);
		}
	}
	
	public void resetGameScores() {
		this.puckScores = this.puckPenalties = this.otherScores = this.otherPenalties = this.overAllScore = this.extraLifeBonusScores = 0;
		this.streakWins = this.streakLosses = 0;
		this.numberOfLives = GAME_DEFAULT_LIVES;
		this.lastPlayScored = true;
	}
	
	public void resetPlayScores() {
		this.puckPlayScores = this.puckPlayPenalties = this.otherPlayScores = this.otherPlayPenalties = this.extraLifeBonusPlayScores = 0;
	}
	
	public void setDifficulty(int level) { this.difficulty = level; }

	// returns true if the goal scores and false if it registers as a penalty
	public abstract boolean registerGoal(TargetDisk piece);
	protected abstract void registerPenalty(TargetDisk piece);
	public abstract void registerLeavePlayArea(TargetDisk piece);
	public abstract void registerStoppedMoving(TargetDisk piece);
	public abstract void registerStrike(boolean fromStartPosn);
	public abstract void registerBounce(TargetDisk piece, StrikeableGamePiece offPiece);
	
	public void startAPlay() {
		if (!this.inAGame)
			this.startGame();
		this.resetPlayScores();
		this.inAPlay = true;
	}
	protected abstract int calcGameScore();
	protected abstract int calcPlayScore();
	
	// return a score for this play
	public final int finishAPlay() {
		if (this.inAPlay) {
			this.inAPlay = false;
			this.scoreForLastPlay = this.calcPlayScore();
			this.processStreaks();
			this.scoreForLastPlay += this.extraLifeBonusPlayScores;
			this.overAllScore += this.scoreForLastPlay;
			this.notifyScoreChangeListeners();
		}
		return this.scoreForLastPlay;
	}
	
	public static void runRuleTests() {
		OnlyTargetScores.testRules();
	}
	
	private void notifyExtraLife() {
		this.notifyScoreActionListeners(SCORING_ACTION_EVENT_EXTRA_LIFE);
	}
	
	private void notifyLoseLife() {
		this.notifyScoreActionListeners(SCORING_ACTION_EVENT_LOST_LIFE);
	}
	
	private void notifyGameOver() {
		this.notifyScoreActionListeners(SCORING_ACTION_EVENT_GAME_OVER);
	}
	
	private void processStreaks() {
		if (this.streakWins >= ScoringManager.GAME_DEFAULT_WINS_FOR_EXTRA_LIFE) {
			// don't reset streakWins, so player can continue to get multiples for many scores
			if ((float)this.streakWins % ScoringManager.GAME_DEFAULT_WINS_FOR_EXTRA_LIFE == 0) { // exactly time for another bonus
				this.numberOfLives++;
				// multiply basic score by multiplier and difficulty to determine bonus
				this.extraLifeBonusPlayScores = this.difficulty * ScoringManager.EXTRA_LIFE_BONUS_MULTIPLIER;
				this.notifyExtraLife();
			}
		}
		else if (this.streakLosses == ScoringManager.GAME_DEFAULT_LOSSES_FOR_LOSE_LIFE) {
			this.numberOfLives--;
			if (this.numberOfLives == 0) {
				this.finishGame();
				this.notifyGameOver();
			}
			else
				this.notifyLoseLife();
			this.streakLosses = 0;
		}
	}

	public void startGame() {
		this.resetGameScores();
		this.inAGame = true;
		this.notifyScoreActionListeners(SCORING_ACTION_EVENT_START_GAME);
//		this.startAPlay();
	}
	public final int finishGame() {
		if (this.inAPlay)
			this.finishAPlay();
		this.overAllScore = this.calcGameScore();
		this.inAGame = false;
		this.notifyScoreActionListeners(SCORING_ACTION_EVENT_FINISH_GAME);
		return this.overAllScore;
	}
	
	public void requestNewGame(final boolean proposeNewLevel, final int newLevel, ColorArrangement colorArrangement) {
		
		// propose new level, the new level must differ from current one or not propose it
		if (!proposeNewLevel || (proposeNewLevel && newLevel != this.difficulty)) {
		
			Action okAction = new Action() {
				public void doAction() {
					// make all movement stop... and start another game
					ScoringManager.this.playManager.resetAllPieces();
					if (proposeNewLevel) {
						// don't start a new game if only changing the level and not currently in a game, just leave it unstarted
						if (ScoringManager.this.inAGame)
							ScoringManager.this.startGame();
						else {							
							ScoringManager.this.resetGameScores();
							ScoringManager.this.resetPlayScores();
						}
						ScoringManager.this.difficulty = newLevel;
						ScoringManager.this.notifyScoreActionListeners(SCORING_ACTION_EVENT_CHANGE_LEVEL);
					}
					else
						ScoringManager.this.startGame();
				}
			};
			// need to ask permission if in a game only
			if (this.inAGame)
				new DialogPopupBox(proposeNewLevel ? CHANGE_LEVEL_CONFIRMATION_TEXT : NEW_GAME_CONFIRMATION_TEXT, okAction, null);
			else
				okAction.doAction();
		}
	}
	
}  // end of class

