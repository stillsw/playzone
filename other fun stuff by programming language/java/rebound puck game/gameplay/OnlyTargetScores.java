package firstReboundGame.gameplay;

import firstReboundGame.*;

import java.awt.Color;
import java.util.*;

/*
 * This game expects just one Target .... but could be more
 * Rules for this ScoringManager
 * Puck goes into goal 
 * 		[easy = no score, medium/hard = penalty]
 * Puck leaves playing area without a goal happening
 * 		[easy/medium = no score, hard = penalty]
 * Target leaves playing area 
 * 		[easy = no score, medium/hard = penalty]
 * Target goes into goal and Puck penalties, before or after goal (depending on level Puck may just no score for goal or leaving playing area)
 * 		[score for target, debit score for puck]
 * Target goes into goal
 * 		[easy = scores]
 * 		[medium = scores only if target has bounced first, note: score multiplies by number of bounces]
 * 		[hard = scores only if puck has bounced first, note: score multiplies by number of bounces]
 */
public class OnlyTargetScores extends ScoringManager
{
	private HashMap<TargetDisk, Integer> bounces = new HashMap<TargetDisk, Integer>();
	private boolean puckHasHitTarget = false;
	private Puck thePuck;
	private boolean puckPenaltyPending = false;
	
	protected OnlyTargetScores(int gameStyle, PlayManager playManager, ColorArrangement animatedTextColours) {
		super(gameStyle, playManager, animatedTextColours);
	}

	private void incrementBounce(TargetDisk piece) {
		Integer bounce = bounces.get(piece);
		if (bounce == null) {
			bounce = new Integer(1);
			bounces.put(piece, bounce);
		}
		else {
			bounces.put(piece, new Integer(bounce.intValue() + 1));
		}
	}
	
	@Override
	public void registerBounce(TargetDisk piece, StrikeableGamePiece offPiece) {
		if (this.puckHasHitTarget) {
			// for target count from the first hit from puck till goes into goal
			// try only counting bounces from non goal pieces... otherwise tiny bounces into a goal will count
			if (!(piece instanceof Puck)) {
				if (!(StrikeableGamePiece.getTopPart(offPiece) instanceof GatedGoal)) 
					this.incrementBounce(piece);			
			}
		}
		else {
			// only the puck should be moving at this point
			// count the bounces for the puck from strike until it hits the target
			if (piece instanceof Puck && offPiece instanceof TargetDisk &&
					(!(offPiece instanceof WallEndDiskArc) && !(offPiece instanceof GoalPost))) { // only want to register hitting the actual target
																								// unfortunately other types are also TargetDisks	
				this.puckHasHitTarget = true;
				this.thePuck = (Puck)piece;
			}
			else {
				this.incrementBounce(piece);
			}
		}
		
	}

	@Override
	public boolean registerGoal(TargetDisk piece) {
		// what scored a goal, puck or target?
		if (piece instanceof Puck) {
			if (this.difficulty != ScoringManager.GAME_DIFFICULTY_EASY)
				this.registerPenalty(piece);
			this.registerStoppedMoving(piece);
			return false;
		}
		else {
			// Target goes into goal
			//		[easy = scores]
			//		[medium = scores only if target has bounced first, note: score multiplies by number of bounces]
			// 		[hard = scores only if puck AND target have bounced first, note: score multiplies by number of bounces]
			if (this.difficulty == ScoringManager.GAME_DIFFICULTY_EASY)
				this.otherPlayScores++;		
			else {
				Integer bounce = this.bounces.get(piece);
				int numTargetBounces = (bounce == null ? 0 : bounce.intValue());

				if (this.difficulty == ScoringManager.GAME_DIFFICULTY_MEDIUM) {
					this.otherPlayScores += numTargetBounces;
				}
				else { // difficult
					bounce = this.bounces.get(this.thePuck);
					int numPuckBounces = (bounce == null ? 0 : bounce.intValue());
					if (numPuckBounces > 0) {
						this.otherPlayScores += numTargetBounces + numPuckBounces;
						// only for hard level, if the puck left the play area there could be a penalty, unless a goal is scored
						if (this.puckPenaltyPending)
							this.puckPenaltyPending = false;
					}
				}
				
			}
			this.registerStoppedMoving(piece);
			return true;
		}
	}

	@Override
	public void registerLeavePlayArea(TargetDisk piece) {
		// what left the playing area, puck or target?
		if (piece instanceof Puck) {
			if (this.difficulty == ScoringManager.GAME_DIFFICULTY_HARD)
				if (playManager.allPiecesStopped()) {
					if (this.otherPlayScores == 0) // target has not scored a goal 
						this.registerPenalty(piece);
				}
				else
					this.puckPenaltyPending = true;
		}
		else {
			if (this.difficulty != ScoringManager.GAME_DIFFICULTY_EASY)
				this.registerPenalty(piece);
		}
		this.registerStoppedMoving(piece);
	}

	@Override
	protected void registerPenalty(TargetDisk piece) {
		if (piece instanceof Puck)
			this.puckPlayPenalties++;
		else
			this.otherPlayPenalties++;		
	}

	@Override
	public void registerStrike(boolean fromStartPosn) {
		if (this.inAPlay) // nothing happened to stop the last play, like puck leaving playing area or going into a goal
			this.finishAPlay();
		this.startAPlay();
	}

	@Override
	public void registerStoppedMoving(TargetDisk piece) {
		// when all pieces are stopped can finish the play
		if (playManager.allPiecesStopped())
			this.finishAPlay();
	}
	
	@Override
	public void startAPlay() {
		super.startAPlay();
		this.puckPenaltyPending = this.puckHasHitTarget = false;
	}

	@Override
	protected int calcPlayScore() {
		// play has finished, tot up the play scores into the game scores
		if (this.puckPenaltyPending) {
			this.puckPenalties++;
			this.puckPenaltyPending = false;
		}
		int playScores = this.puckPlayScores + this.otherPlayScores - this.puckPlayPenalties - this.otherPlayPenalties;
		// test streaks
		if (playScores > 0 && this.otherPlayScores > 0) { // a win: scored a goal and penalties don't eliminate it
			this.streakWins++;
			this.streakLosses = 0;
		}
		else { // failed to score
			this.streakWins = 0;
			this.streakLosses++;
		}
		
		// playScores > 0 and actually scored a goal may trigger multiples
		if (this.streakWins > 0)
			playScores *= this.streakWins; 
		
		// maintain a breakdown for stats (if wanted)
		this.puckScores += this.puckPlayScores;
		this.puckPenalties += this.puckPlayPenalties;
		this.otherScores += this.otherPlayScores;
		this.otherPenalties += this.otherPlayPenalties;
		
		// less than 0 is a 0 score
		return (playScores < 0 ? 0 : playScores * ScoringManager.GAME_SCORE_MULTIPLIER);
	}
	
	@Override
	protected int calcGameScore() {
//		int gameScores = this.puckScores + this.otherScores - this.puckPenalties - this.otherPenalties;
//		return (gameScores < 0 ? 0 : gameScores * ScoringManager.GAME_SCORE_MULTIPLIER);
		return this.overAllScore;
	}

	@Override
	public void resetPlayScores() {
		super.resetPlayScores();
		this.bounces.clear();
	}
	
	protected static void testRules() {
//		System.out.println("OnlyTargetScores.testRules: starting tests, create test objects");
		// create a puck and and 2 targets, a wall piece and an end wall piece
		Location2D zeroLoc = new Location2D(0,0);
		LineVector2D aLine = new LineVector2D(zeroLoc, new Vector2D(1, 0));
		Puck testPuck = new Puck(null, null, zeroLoc, 10, ColorArrangement.NULL_COLOURS);
		TargetDisk testTarget1 = new TargetDisk(null, null, zeroLoc, 10, ColorArrangement.NULL_COLOURS);
		TargetDisk testTarget2 = new TargetDisk(null, null, zeroLoc, 10, ColorArrangement.NULL_COLOURS);
		WallPiece wallPiece = new WallPiece(aLine, WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT, WallFactory.WALL_N, new Vector2D(0,1), WallPiece.WALL_END_TYPE_CORNER_JOIN, WallPiece.WALL_END_TYPE_CORNER_JOIN, 0, 10, false, ColorArrangement.NULL_COLOURS);
		WallEndDiskArc wallEnd = new WallEndDiskArc(zeroLoc, 10, WallFactory.WALL_N, true, false, ColorArrangement.NULL_COLOURS);
		wallEnd.setPartOf(wallPiece);
		LinearObstacle lineObstacle = new LinearObstacle(aLine, false, ColorArrangement.NULL_COLOURS, true);
		GatedGoal goal = new GatedGoal(zeroLoc, 5, GatedGoal.GATED_GOAL_NORTH, 30, 30, 0, false, Goal.GOAL_SHAPE_RECTANGULAR, false, ColorArrangement.NULL_COLOURS);
		LinearObstacle linePartOfGoal = new LinearObstacle(aLine, false, ColorArrangement.NULL_COLOURS, true);
		linePartOfGoal.setPartOf(goal);

	    PlayManager playManager = new PlayManager(PlayManager.SCHEME_REPLAY_WHEN_ALL_PIECES_STOP);
	    playManager.registerPieces(new MoveableGamePiece[] {testPuck, testTarget1, testTarget2});
	    ScoringManager scoringManager = ScoringManager.getInstance(ScoringManager.GAME_STYLE_TARGET_SCORES_PUCK_PENALISES, playManager, null); 
		
	    Striker striker = new Striker(testPuck);
//	    striker.setStrengthBar(strikerStrengthBar);
//	    striker.setAngleIndicator(strikerAngleIndicator);
	    testPuck.setStriker(striker);
		
		// Puck goes into goal 
		// 		[easy = no score, medium/hard = penalty]
		// Puck leaves playing area without a goal happening
		// 		[easy/medium = no score, hard = penalty]
		// Target leaves playing area 
		// 		[easy = no score, medium/hard = penalty]
		// Target goes into goal and Puck penalties, before or after goal (depending on level Puck may just no score for goal or leaving playing area)
		// 		[score for target, debit score for puck]
		// Target goes into goal
		// 		[easy = scores]
		// 		[medium = scores only if target has bounced first, note: score multiplies by number of bounces]
		// 		[hard = scores only if puck has bounced first, note: score multiplies by number of bounces]
	    scoringManager.startGame();
		
		System.out.println("OnlyTargetScores.testRules: starting easy tests, number of lives="+scoringManager.numberOfLives);
		
		// simulate game play is a bit challenging because each time have to tell game play manager a piece has stopped moving, and also
		// have to start a piece each time too
		int gameTick = 100;
		ReboundGamePanel.gameTick = 1000;
		// easy tests
		scoringManager.setDifficulty(ScoringManager.GAME_DIFFICULTY_EASY);
		{
			int testNum = 1;
			int scoreTotal = 0;
			{ //1
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck straight into goal... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//1a
					System.out.println("\t Sub test multiples numLives should still be default now="+scoringManager.numberOfLives+" streakWins="+scoringManager.streakWins+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{ //2
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck goes out of play... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//2a
					System.out.println("\t Sub test multiples numLives should still be default now="+scoringManager.numberOfLives+" streakWins="+scoringManager.streakWins+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{ //3
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then goes into goal... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//3a
					System.out.println("\t Sub test multiples numLives should still be default now="+scoringManager.numberOfLives+" streakWins="+scoringManager.streakWins+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{//4
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then goes out of play... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//4a
					System.out.println("\t Sub test multiples numLives should still be default now="+scoringManager.numberOfLives+" streakWins="+scoringManager.streakWins+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{//5
				int expectedResult = 100;
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck goes into goal... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//5a
					System.out.println("\t Sub test multiples numLives should still be default now="+scoringManager.numberOfLives+" streakWins="+scoringManager.streakWins+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{//6
				int expectedResult = 200; // double for streak of 2
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck goes out of play... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//6a
					System.out.println("\t Sub test multiples numLives should still be default now="+scoringManager.numberOfLives+" streakWins="+scoringManager.streakWins+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{//7
				int expectedResult = 1300; // triple score for streak of 3 + extra life bonus
				System.out.println(""+(testNum++)+". puck hits target then target scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//7a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//8
				int expectedResult = 400; // 4x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off wall and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//9
				int expectedResult = 500; // 5x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall and then a wall end and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//9a
					System.out.println("\t Sub test multiples 3 in a row should not generate extra life yet, numLives now="+scoringManager.numberOfLives);
				}
			}
			{//10
				int expectedResult = 1600; // 6x + extra life bonus
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//10a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//11
				int expectedResult = 700; //7x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//12
				int expectedResult = 800; // 8x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores and puck goes into goal... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//13
				int expectedResult = 1900; // 9x + extra life bonus
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes into goal before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//10a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//14
				int expectedResult = 1000; // 10x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes out of play ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//15
				int expectedResult = 1100; // 11x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes out of play before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//16
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then leaves play area but puck goes into goal before target leaves ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//17
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then leaves play area but puck goes out of play before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//18
				int expectedResult = 100;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and target scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//19
				int expectedResult = 200; // 2x
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and target scores and puck penalties (with goal) ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//20
				int expectedResult = 1300; // 3x + extra life bonus
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and then penalties then target bounces off pieces as earlier and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//20a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//21
				int expectedResult = 400; // 4x
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and then leaves playing area after the target scores: then target bounces off pieces as earlier and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//22
				int expectedResult = 500;  // 5x
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck stops moving... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerStoppedMoving(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//23
				int expectedResult = 1600; // 6x + extra life bonus
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece, part of goal, goal and then hits target and then leaves playing area after the target scores: then target bounces off same pieces too and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				scoringManager.registerBounce(testPuck, linePartOfGoal);
				scoringManager.registerBounce(testPuck, goal);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, lineObstacle);
				scoringManager.registerBounce(testTarget1, linePartOfGoal);
				scoringManager.registerBounce(testTarget1, goal);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//23a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			System.out.println("End of easy tests for 1 target scoreTotal="+scoreTotal+" game total="+scoringManager.finishGame());
			
		}
		
		
		
		// > 1 target
		
		
		
		// Puck goes into goal 
		// 		[easy = no score, medium/hard = penalty]
		// Puck leaves playing area without a goal happening
		// 		[easy/medium = no score, hard = penalty]
		// Target leaves playing area 
		// 		[easy = no score, medium/hard = penalty]
		// Target goes into goal and Puck penalties, before or after goal (depending on level Puck may just no score for goal or leaving playing area)
		// 		[score for target, debit score for puck]
		// Target goes into goal
		// 		[easy = scores]
		// 		[medium = scores only if target has bounced first, note: score multiplies by number of bounces]
		// 		[hard = scores only if puck has bounced first, note: score multiplies by number of bounces]
	    scoringManager.startGame();
		
		// medium tests
		System.out.println("OnlyTargetScores.testRules: starting medium tests (same as easy, with differing results)");
		scoringManager.setDifficulty(ScoringManager.GAME_DIFFICULTY_MEDIUM);
		{
			int testNum = 1;
			int scoreTotal = 0;
			{//1
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck straight into goal... expected results (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//2
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck goes out of play... expected result (+0 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//3
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then goes into goal... expected result (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//4
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then goes out of play... expected result (+0 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//4a
					System.out.println("\t Sub test no lives change yet numLives now="+scoringManager.numberOfLives+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{//5
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck goes into goal... expected result (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//5a
					System.out.println("\t Sub test multiples 5 in a row should generate lose life numLives now="+scoringManager.numberOfLives+" streakLosses="+scoringManager.streakLosses);
				}
			}
			{//6
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores without a bounce and then puck goes out of play... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//7
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores without a bounce ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//8
				int expectedResult = 100;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off wall and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//9
				int expectedResult = 400; // 2x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall and then a wall end and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//10
				int expectedResult = 3900; // 3x + extra life bonus
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//10a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//11
				int expectedResult = 1600; // 4x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//12
				int expectedResult = 1500; // 5x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores and puck goes into goal... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//13
				int expectedResult = 4800; // 6x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes into goal before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//13a
					System.out.println("\t Sub test multiples 3 in a row should generate extra life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//14
				int expectedResult = 2800; // 7x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes out of play ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//15
				int expectedResult = 3200; // 8x
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes out of play before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//16
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then leaves play area but puck goes into goal before target leaves ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//17
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then leaves play area but puck goes out of play before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//18
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and target scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//19
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and target scores and puck penalties (with goal) ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//20
				int expectedResult = 300;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and then penalties (in goal) then target bounces off pieces as earlier and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//21
				int expectedResult = 800; // 2x
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and then leaves playing area after the target scores: then target bounces off pieces as earlier and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//22
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck stops moving... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerStoppedMoving(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//23
				int expectedResult = 300;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece, part of goal, goal and then hits target and then leaves playing area after the target scores: then target bounces off same pieces too and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				scoringManager.registerBounce(testPuck, linePartOfGoal);
				scoringManager.registerBounce(testPuck, goal);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, lineObstacle);
				scoringManager.registerBounce(testTarget1, linePartOfGoal);
				scoringManager.registerBounce(testTarget1, goal);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//24
				int expectedResult = 0; // no multiple because target fails to score
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece, part of goal, goal and then hits target and then leaves playing area as does target ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				scoringManager.registerBounce(testPuck, linePartOfGoal);
				scoringManager.registerBounce(testPuck, goal);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, lineObstacle);
				scoringManager.registerBounce(testTarget1, linePartOfGoal);
				scoringManager.registerBounce(testTarget1, goal);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			System.out.println("End of medium tests for 1 target scoreTotal="+scoreTotal+" game total="+scoringManager.finishGame());
			
		}
	
		
		
	
		// > 1 target
		
		
		
	
		// Puck goes into goal 
		// 		[easy = no score, medium/hard = penalty]
		// Puck leaves playing area without a goal happening
		// 		[easy/medium = no score, hard = penalty]
		// Target leaves playing area 
		// 		[easy = no score, medium/hard = penalty]
		// Target goes into goal and Puck penalties, before or after goal (depending on level Puck may just no score for goal or leaving playing area)
		// 		[score for target, debit score for puck]
		// Target goes into goal
		// 		[easy = scores]
		// 		[medium = scores only if target has bounced first, note: score multiplies by number of bounces]
		// 		[hard = scores only if puck has bounced first, note: score multiplies by number of bounces]
	    scoringManager.startGame();
		
		// difficult tests		// difficult tests

		System.out.println("OnlyTargetScores.testRules: starting hard tests (same as medium, with differing results)");
		scoringManager.setDifficulty(ScoringManager.GAME_DIFFICULTY_HARD);
		{
			int testNum = 1;
			int scoreTotal = 0;
			{//1
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck straight into goal... expected results (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//2
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck goes out of play... expected result (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//3
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then goes into goal... expected result (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//4
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then goes out of play... expected result (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//5 // 5x
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck goes into goal... expected result (+1 to penalties) score="+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//5a
					System.out.println("\t Sub test multiples 5 in a row should generate lose life numLives now="+scoringManager.numberOfLives);
				}
			}
			{//6
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores without a bounce and then puck goes out of play... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//7
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores without a bounce ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//8
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off wall and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//9
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall and then a wall end and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//10
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//10a
				System.out.println("\t Sub test multiples 5 in a row should generate lose life numLives now="+scoringManager.numberOfLives);
			}
			{//11
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//12
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores and puck goes into goal... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//13
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes into goal before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//14
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes out of play ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//15
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then scores but puck goes out of play before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//15a
					System.out.println("\t Sub test multiples 5 in a row should generate lose life and end of game! numLives now="+scoringManager.numberOfLives);
				}
			}
			{//16
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then leaves play area but puck goes into goal before target leaves ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
				{//16a
					System.out.println("\t Sub test new game should have started numLives="+scoringManager.numberOfLives);
				}
			}
			{//17
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target bounces off a wall, a wall end, puck again, wall again and then leaves play area but puck goes out of play before target does ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerLeavePlayArea(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//18
				int expectedResult = 300;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and target scores without a bounce ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//19 
				int expectedResult = 400; // 2x
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and target scores and puck penalties (with goal) ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//20
				int expectedResult = 6800; // 3x + extra life bonus
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and then penalties (in goal) then target bounces off pieces as earlier and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerGoal(testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//21
				int expectedResult = 2800; // 4x
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece and then hits target and then leaves playing area after the target scores: then target bounces off pieces as earlier and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, testPuck);
				scoringManager.registerBounce(testTarget1, wallPiece);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//22
				int expectedResult = 0;
				System.out.println(""+(testNum++)+". puck hits target then target scores and then puck stops moving... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerStoppedMoving(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}
			{//23
				int expectedResult = 800;
				System.out.println(""+(testNum++)+". puck bounces off wall, wall end, another piece, part of goal, goal and then hits target and then leaves playing area after the target scores: then target bounces off same pieces too and then scores ... expected result "+expectedResult);
				testPuck.resetToStart(); testTarget1.resetToStart(); testTarget2.resetToStart();
				testPuck.setMoving(true);
				scoringManager.registerStrike(true); // puck hits.. this should initialise a play
				scoringManager.registerBounce(testPuck, wallPiece);
				scoringManager.registerBounce(testPuck, wallEnd);
				scoringManager.registerBounce(testPuck, lineObstacle);
				scoringManager.registerBounce(testPuck, linePartOfGoal);
				scoringManager.registerBounce(testPuck, goal);
				testTarget1.setMoving(true);
				scoringManager.registerBounce(testPuck, testTarget1);
				scoringManager.registerBounce(testTarget1, wallPiece);
				scoringManager.registerBounce(testTarget1, wallEnd);
				scoringManager.registerBounce(testTarget1, lineObstacle);
				scoringManager.registerBounce(testTarget1, linePartOfGoal);
				scoringManager.registerBounce(testTarget1, goal);
				playManager.waitToRestart(testTarget1, gameTick);
				scoringManager.registerGoal(testTarget1);
				playManager.waitToRestart(testPuck, gameTick);
				scoringManager.registerLeavePlayArea(testPuck);
				System.out.println("\t result="+scoringManager.finishAPlay()+" test "+(scoringManager.scoreForLastPlay == expectedResult ? "success" : "FAILED!"));
				scoreTotal += scoringManager.scoreForLastPlay;
				System.out.println("\t game scores : \n\t\t puck="+scoringManager.puckScores+"\n\t\t other="+scoringManager.otherScores+"\n\t\t penalty puck="+scoringManager.puckPenalties+"\n\t\t penalty other="+scoringManager.otherPenalties);
			}

			System.out.println("End of hard tests for 1 target scoreTotal="+scoreTotal+" game total="+scoringManager.finishGame());
			
		}

		
		
		
		// > 1 target
		
		
		System.out.println("OnlyTargetScores.testRules: tests finished");
		
	}

}  // end of class

