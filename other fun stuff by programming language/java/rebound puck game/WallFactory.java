package firstReboundGame;

import java.util.ArrayList;
import java.awt.*;

/*
 * static methods for creating walls with goals embedded in them
 */
public class WallFactory
{
	/*
	 * placementIdx is an int from 0 to 30 plotting 5 positions or (incl corners) running clockwise around the square:
	 * ------------------------
	 * |0    2    4    6    8 |
	 * |                      |
	 * |30                 10 |
	 * |                      |
	 * |28                 12 |
	 * |                      |
	 * |26                 14 |
	 * |                      |
	 * |24  22   20   18   16 |
	 * ------------------------
	 * goals can go into any of the even places and a space can be defined in any of the odd numbered places to break a sequence
	 */

	public static final int PLACE_NW      = 0;
	public static final int PLACE_NNW     = 1;
	public static final int PLACE_NNNW    = 2;
	public static final int PLACE_NNNNW   = 3;
	public static final int PLACE_N       = 4;
	public static final int PLACE_NNNNE   = 5;
	public static final int PLACE_NNNE    = 6;
	public static final int PLACE_NNE     = 7;
	public static final int PLACE_NE      = 8;
	public static final int PLACE_EEN     = 9;
	public static final int PLACE_EEEN    = 10;
	public static final int PLACE_EEEEN   = 11;
	public static final int PLACE_E       = 12;
	public static final int PLACE_EEEES   = 13;
	public static final int PLACE_EEES    = 14;
	public static final int PLACE_EES     = 15;
	public static final int PLACE_SE      = 16;
	public static final int PLACE_SSE     = 17;
	public static final int PLACE_SSSE    = 18;
	public static final int PLACE_SSSSE   = 19;
	public static final int PLACE_S       = 20;
	public static final int PLACE_SSSSW   = 21;
	public static final int PLACE_SSSW    = 22;
	public static final int PLACE_SSW     = 23;
	public static final int PLACE_SW      = 24;
	public static final int PLACE_WWS     = 25;
	public static final int PLACE_WWWS    = 26;
	public static final int PLACE_WWWWS   = 27;
	public static final int PLACE_W       = 28;
	public static final int PLACE_WWWWN   = 29;
	public static final int PLACE_WWWN    = 30;
	public static final int PLACE_WWN     = 31;
	
	public static final int PLACE_EMPTY = 0;
	public static final int PLACE_GOAL = 1;
	public static final int PLACE_LINE = 2;
	
	public static final int WALL_N = 0;
	public static final int WALL_E = 1;
	public static final int WALL_S = 2;
	public static final int WALL_W = 3;
	
	private static final double PLACES_IN_SIDE = 7.0;
	private static final int LAST_PLACE = (int)(4 * (PLACES_IN_SIDE + 1));

	private static final WallFactory instance = new WallFactory();
	public static WallFactory getInstance() { return instance; }
	protected double wallThickness = ReboundGamePanel.WALL_CLIP_DEPTH * 2;
	
	private double xPosNw;
	private double xPosNe;
	private double xPosSe;
	private double xPosSw;
	private double yPosNw;
	private double yPosNe;
	private double yPosSe;
	private double yPosSw;
	private double widthPlaceOffset; // the length between each place in the side
	private double heightPlaceOffset; // the length between each place in the side
	// vectors from the wall positions to the edges of the clipping zone
	private Vector2D northToSouthV = new Vector2D(0.0, ReboundGamePanel.WALL_GOAL_CLIP_DEPTH);
	private Vector2D westToEastV = new Vector2D(ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, 0.0);
	private Vector2D southToNorthV = new Vector2D(0.0, -ReboundGamePanel.WALL_GOAL_CLIP_DEPTH);
	private Vector2D eastToWestV = new Vector2D(-ReboundGamePanel.WALL_GOAL_CLIP_DEPTH, 0.0);

	
	/*
	 * No instantiation
	 */
	private WallFactory() {}
	
	private void setGlobals(Rectangle rect) {
		xPosNw = rect.x;
		xPosNe = rect.x + rect.width;
		xPosSe = rect.x + rect.width;
		xPosSw = rect.x;
		yPosNw = rect.y;
		yPosNe = rect.y;
		yPosSe = rect.y + rect.height;
		yPosSw = rect.y + rect.height;
		widthPlaceOffset = rect.width / (PLACES_IN_SIDE + 1.0); // the length between each place in the side
											// there's one more than the number of places in the side because
											// those are evenly separated points not divisions 
		heightPlaceOffset = rect.height / (PLACES_IN_SIDE + 1.0); // the length between each place in the side		
	}

/*
 * Copy and edit the following to make another preset configuration
 * 
 * 
    GoalSpec[] goalSpecs = new GoalSpec[] {
    		wallFactory.new GoalSpec(WallFactory.PLACE_NW, goalsTotalWidth, goalsPostWidth, colorArrangement)
    		,wallFactory.new GoalSpec(WallFactory.PLACE_NE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_SE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_SW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//   		, wallFactory.new GoalSpec(WallFactory.PLACE_NNW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_NNNW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_NNNNW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_N, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_NNNNE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_NNNE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_NNE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_EEN, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_EEEN, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_EEEEN, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_E, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_EEEES, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_EEES, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_EES, goalsTotalWidth, goalsPostWidth, colorArrangement)
    		, wallFactory.new GoalSpec(WallFactory.PLACE_SSW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_SSSW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_SSSSW, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_S, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_SSSSE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_SSSE, goalsTotalWidth, goalsPostWidth, colorArrangement)
    		, wallFactory.new GoalSpec(WallFactory.PLACE_SSE, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_WWN, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_WWWN, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_WWWWN, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_W, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_WWWWS, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_WWWS, goalsTotalWidth, goalsPostWidth, colorArrangement)
//    		, wallFactory.new GoalSpec(WallFactory.PLACE_WWS, goalsTotalWidth, goalsPostWidth, colorArrangement)
    };
    int[] activeWalls = new int[] {
    		WallFactory.WALL_N
    		, WallFactory.WALL_E
    		, WallFactory.WALL_S
    		, WallFactory.WALL_W
    		}; 
    int[] spaces = new int[] {
    		WallFactory.PLACE_N 
//    		,WallFactory.PLACE_NNNNE 
//    		,WallFactory.PLACE_NNNE 
//    		,WallFactory.PLACE_NNE
//    		,WallFactory.PLACE_EEN
//    		,WallFactory.PLACE_EEES
    		,WallFactory.PLACE_SSSE
    		,WallFactory.PLACE_S
    		,WallFactory.PLACE_SSSW
//    		,WallFactory.PLACE_W
    		};//, WallFactory.PLACE_EEEES, WallFactory.PLACE_S, WallFactory.PLACE_WWWWS};
    wallFactory.makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, colorArrangement, obstacles);
 */
	
	
	
	

	/*
	 * Preset1 makes a 3 sided rectangle with goals at the two top corners. The bottom (south) side has goals next to the edge
	 * but just space between them
	 */
	public void makeObstaclesWithEmbeddedGoalsPreset1(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {
	    		this.new GoalSpec(WallFactory.PLACE_NW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_NE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SSW, goalsTotalWidth, goalsPostWidth, wallClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SSE, goalsTotalWidth, goalsPostWidth, wallClipDepth, addToPlayClipZone, colorArrangement)
	    };
	    int[] activeWalls = new int[] { WallFactory.WALL_N, WallFactory.WALL_E, WallFactory.WALL_S, WallFactory.WALL_W }; 
	    int[] spaces = new int[] {
	    		WallFactory.PLACE_SSSE
	    		,WallFactory.PLACE_S
	    		,WallFactory.PLACE_SSSW
	    		};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	/*
	 * Preset2 makes a 4 sided rectangle with goals at all corners and in the middles of all sides 
	 */
	public void makeObstaclesWithEmbeddedGoalsPreset2(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {
	    		this.new GoalSpec(WallFactory.PLACE_NW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_NE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_N, goalsTotalWidth, goalsPostWidth, wallClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_S, goalsTotalWidth, goalsPostWidth, wallClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_W, goalsTotalWidth, goalsPostWidth, wallClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_E, goalsTotalWidth, goalsPostWidth, wallClipDepth, addToPlayClipZone, colorArrangement)
	    };
	    int[] activeWalls = new int[] { WallFactory.WALL_N, WallFactory.WALL_E, WallFactory.WALL_S, WallFactory.WALL_W }; 
	    int[] spaces = new int[] {};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	/*
	 * Preset3 makes a 3 sided rectangle with goals at all corners.
	 */
	public void makeObstaclesWithEmbeddedGoalsPreset3(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {
	    		this.new GoalSpec(WallFactory.PLACE_NW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_NE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    };
	    int[] activeWalls = new int[] { WallFactory.WALL_N, WallFactory.WALL_E, WallFactory.WALL_W }; 
	    int[] spaces = new int[] {};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	/*
	 * Preset for testing
	 */
	public void makeObstaclesWithEmbeddedGoalsPresetJustWalls(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {};
	    int[] activeWalls = new int[] { 
	    		WallFactory.WALL_N
	    		, WallFactory.WALL_E
	    		, WallFactory.WALL_S
	    		, WallFactory.WALL_W 
	    		}; 
	    int[] spaces = new int[] {};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	/*
	 * Preset for testing
	 */
	public void makeObstaclesWithEmbeddedGoalsPresetJustWallsAndSpaces(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {};
	    int[] activeWalls = new int[] { 
	    		WallFactory.WALL_N
//	    		, WallFactory.WALL_E
	    		, WallFactory.WALL_S
	    		, WallFactory.WALL_W 
	    		}; 
	    int[] spaces = new int[] {
	    		WallFactory.PLACE_N
	    		, WallFactory.PLACE_NNE
	    		, WallFactory.PLACE_SSSE
	    		, WallFactory.PLACE_S
	    		, WallFactory.PLACE_SSSW
	    		};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	/*
	 * Preset for testing
	 */
	public void makeObstaclesWithEmbeddedGoalsPresetWallsAndSpacesAndGoals(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {
	    		this.new GoalSpec(WallFactory.PLACE_NW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_NNE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_W, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_WWS, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    		, this.new GoalSpec(WallFactory.PLACE_SE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    };
	    int[] activeWalls = new int[] { 
	    		WallFactory.WALL_N
//	    		, WallFactory.WALL_E
	    		, WallFactory.WALL_S
	    		, WallFactory.WALL_W 
	    		}; 
	    int[] spaces = new int[] {
	    		WallFactory.PLACE_N
	    		, WallFactory.PLACE_SSSE
	    		, WallFactory.PLACE_S
	    		, WallFactory.PLACE_SSSW
	    		};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	/*
	 * Preset for testing
	 */
	public void makeObstaclesWithEmbeddedGoalsPresetOnlyGoals(Rectangle rect, double goalsTotalWidth, double goalsPostWidth, double wallEndRadius, double wallClipDepth, double cornerClipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {
	    GoalSpec[] goalSpecs = new GoalSpec[] {
//	    		this.new GoalSpec(WallFactory.PLACE_N, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_NW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_NNE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		 this.new GoalSpec(WallFactory.PLACE_NE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_W, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_E, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_S, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_WWS, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_SE, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
//	    		, this.new GoalSpec(WallFactory.PLACE_SW, goalsTotalWidth, goalsPostWidth, cornerClipDepth, addToPlayClipZone, colorArrangement)
	    };
	    int[] activeWalls = new int[] { 
	    		}; 
	    int[] spaces = new int[] {
	    		};
	    makeObstaclesWithEmbeddedGoals(rect, goalSpecs, activeWalls, spaces, wallEndRadius, colorArrangement, obstacles);
	 		
	}
	
	
	/*
	 * Add goals according to the rectangle passed in
	 */
	public void makeObstaclesWithEmbeddedGoals(Rectangle rect, GoalSpec[] goalSpecs, int[] activeWalls, int[] spaces, double wallEndRadius, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles)
	{
		setGlobals(rect);
		int[] places = new int[33]; // one for every place around the box and an extra one for processing the last one easily
		// collect data... walls first
		boolean wallNisActive = false;
		boolean wallEisActive = false;
		boolean wallSisActive = false;
		boolean wallWisActive = false;
		if (activeWalls != null) {
			for (int i = 0; i < activeWalls.length; i++) {
				int nextSpace = -1, end = -2;				
				switch (activeWalls[i]) {
					case WALL_N: wallNisActive = true; nextSpace = PLACE_NNW; end = PLACE_NNE; break;
					case WALL_E: wallEisActive = true; nextSpace = PLACE_EEN; end = PLACE_EES; break;
					case WALL_S: wallSisActive = true; nextSpace = PLACE_SSE; end = PLACE_SSW; break;
					case WALL_W: wallWisActive = true; nextSpace = PLACE_WWS; end = PLACE_WWN; break;
				}
				if (nextSpace != -1) { // just in case got some other value
					while (nextSpace <= end)
						places[nextSpace++] = PLACE_LINE;
				}
				else // == -1
					System.out.println("WallFactory.makeObstaclesWithEmbeddedGoals: error, unexpected value in activeWalls[i] "+activeWalls[i]);
			}
		}
		// collect data... empty out any spaces
		if (spaces != null) {
			for (int i = 0; i < spaces.length; i++) 
				if (spaces[i] < PLACE_NNW || spaces[i] > PLACE_WWN)
					System.out.println("WallFactory.makeObstaclesWithEmbeddedGoals: error, unexpected value in spaces[i] ="+spaces[i]);
				else
					places[spaces[i]] = PLACE_EMPTY;
		}
		// collect data... finally overwrite with goals as req'd
		boolean firstSpotIsGoal = false;
		if (goalSpecs != null && goalSpecs.length > 0) 
			for (int i = 0; i < goalSpecs.length; i++) 
				if (goalSpecs[i].placementIdx == 0)
					firstSpotIsGoal = true;

		GoalSpec[] orderedGoalSpecs = null;
		int orderedGoalsIdx = 0;
		if (goalSpecs != null && goalSpecs.length > 0) {
			orderedGoalSpecs = new GoalSpec[(firstSpotIsGoal ? goalSpecs.length + 1 : goalSpecs.length)];
			for (int i = 0; i < goalSpecs.length; i++) 
				places[goalSpecs[i].placementIdx] = PLACE_GOAL;

			// fill the last element in the array with goal if the first is set
			if (firstSpotIsGoal)
				places[places.length -1] = PLACE_GOAL;
		}

		// should have everything ready to go now
		GoalSpec currSpec = null;
		int currLineStartPlace = 0;
		int currLineEndPlace = 0;
		boolean gotLine = false;
		boolean gotSpec = false;
		for (int i = 0; i < places.length; i++) {
			boolean isCorner = (i % 8 == 0);
			int currWall = (int)(i / 8);
			int effectiveWallForLineEnding = (isCorner ? (currWall == 0 ? 3 : currWall - 1) : currWall); // previous wall, unless this is first, then it's the last one
			boolean wasCorner = ((i - 1) % 8 == 0); // previous spot
			
// temp			
//			boolean isWidth = currWall % 2 == 0;
//			int wallIdx = (int)(i - currWall * 8);
//			if (i == 8) {
//				if (places[i] == PLACE_EMPTY) System.out.print("Place: "+i+" PLACE_EMPTY");
//				else if (places[i] == PLACE_GOAL) System.out.print("Place: "+i+" PLACE_GOAL");
//				else if (places[i] == PLACE_LINE) System.out.print("Place: "+i+" PLACE_LINE");
//				System.out.println(" (corner= "+isCorner+" currWall = "+currWall+" wallIdx = "+wallIdx+" isWidth="+isWidth+")");
//			}
//			
			// traverse the array, find lines to draw either attached to goals or on their own
			// empty or goal means finish processing the previous one
			if (places[i] == PLACE_GOAL) {
				// find the goal
				GoalSpec newSpec = null;
				int searchForIdx = i;
				boolean lastGoalRepeat = (i == places.length - 1); 
				if (lastGoalRepeat) // last element in the list (effectively)
					searchForIdx = 0; 
				for (int j = 0; j < goalSpecs.length; j++) {
					if (goalSpecs[j].placementIdx == searchForIdx) {
						newSpec = goalSpecs[j];
						// put the goals into process order, include last one if it's 
						// duplicating the first... so the left line is properly processed for it
						orderedGoalSpecs[orderedGoalsIdx++] = newSpec;
						break;
					}
				}
				
				if (!lastGoalRepeat) {
					// settings for the new spec regardless
					newSpec.leftWall = effectiveWallForLineEnding;
					newSpec.leftToEdgeV = this.getToEdgeVector(i, isCorner, true);
					newSpec.rightWall = currWall;
					// test for corner is to be correct for right wall
					newSpec.rightToEdgeV = this.getToEdgeVector(i, isCorner, false);
					newSpec.wallEndRadius = wallEndRadius;
					// default wall end types in case nothing else sets them
					newSpec.leftWallEndType = newSpec.rightWallEndType = 
						(Double.compare(wallEndRadius, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND ? WallPiece.WALL_END_TYPE_ROUNDED : WallPiece.WALL_END_TYPE_ABRUPT);
				}
				
				// only want a left line if the wall is active, this is the previous
				// wall if in a corner
				if ((effectiveWallForLineEnding == WALL_N && wallNisActive)
						||	(effectiveWallForLineEnding == WALL_E && wallEisActive)
						||	(effectiveWallForLineEnding == WALL_S && wallSisActive)
						||	(effectiveWallForLineEnding == WALL_W && wallWisActive)) {
					if (gotSpec) {
						// tell the new goal where to get the left line from
						currSpec.rightNumberOfPlaces = i - currLineStartPlace;
						newSpec.leftLineFromGoal = currSpec;
						// the left line will be given back to the left wall eventually, can set the end types accordingly
						newSpec.leftWallEndType = 
							currSpec.rightWallEndType = WallPiece.WALL_END_TYPE_GOAL;
					}
					else if (gotLine) { 
						newSpec.leftNumberOfPlaces = i - currLineStartPlace;
						if (newSpec.leftNumberOfPlaces != 0)
							newSpec.leftWallEndType = 
								(this.isCornerWithNonSpaceOtherSide(currLineStartPlace, true, places) ? WallPiece.WALL_END_TYPE_CORNER_JOIN 
										: (Double.compare(wallEndRadius, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND ? WallPiece.WALL_END_TYPE_ROUNDED : WallPiece.WALL_END_TYPE_ABRUPT));
						newSpec.leftWall = effectiveWallForLineEnding;
						newSpec.leftToEdgeV = this.getToEdgeVector(i, isCorner, true);

						gotLine = false;
					}
					else if (wasCorner) {
						// neither line nor spec.. but is on a lined place
						newSpec.leftNumberOfPlaces = 1;
						newSpec.leftWallEndType = WallPiece.WALL_END_TYPE_CORNER_JOIN;
					}
				}
//				else {
//					currLineEndPlace = currLineStartPlace = 0;
//				}
				// reset the line for the next line 
				if (!isCorner || 
						((currWall == WALL_N && wallNisActive)
							||	(currWall == WALL_E && wallEisActive)
							||	(currWall == WALL_S && wallSisActive)
							||	(currWall == WALL_W && wallWisActive))) {
						currLineStartPlace = i;
						currLineEndPlace = i + 1;
				}
				else
					currLineEndPlace = currLineStartPlace = i;

				currSpec = newSpec;
				gotSpec = true;
			}
			// lines and spaces both occupy the placing either side of the position specified
			// so, they are double length
			else if (places[i] == PLACE_LINE) {
				currLineEndPlace = i + 1;
				if (!(gotSpec || gotLine)) {
					gotLine = true;
					// starting a new line... if it is from corner space it actually starts one
					// before here, otherwise it must be coming after a space was specifically set
					// so prioritise that space over the line, which means start it from here
					if (wasCorner)
						currLineStartPlace = i - 1;
					else
						currLineStartPlace = i;
				}
			}
			else if (places[i] == PLACE_EMPTY) {
				// tell the previous spec how the line ends
				if (gotSpec) {
					if (isCorner) 
						currSpec.rightNumberOfPlaces = currLineEndPlace - currLineStartPlace;
					else if (currLineEndPlace - currLineStartPlace > 0)
						currSpec.rightNumberOfPlaces = i - 1 - currLineStartPlace;

					if (currSpec.rightNumberOfPlaces != 0)
						currSpec.rightWallEndType = 
							(this.isCornerWithNonSpaceOtherSide(i - 1, false, places) ? WallPiece.WALL_END_TYPE_CORNER_JOIN 
									: (Double.compare(wallEndRadius, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND ? WallPiece.WALL_END_TYPE_ROUNDED : WallPiece.WALL_END_TYPE_ABRUPT));

					currSpec = null;
					gotSpec = false;
				}
				// or do we have a line?
				if (gotLine) {
					// draw it! It actually ended at the previous place
					int toWhere = (isCorner ? i : i - 1);
					int currNumPlaces = toWhere - currLineStartPlace;
					if (currNumPlaces > 0) { // could equate to zero because space overrides the previous line
						// left end will be abrupt if not a corner, nor a goal (wouldn't be here) and if wallEndRadius = 0
						int leftEndType = 
							(this.isCornerWithNonSpaceOtherSide(currLineStartPlace, true, places) ? WallPiece.WALL_END_TYPE_CORNER_JOIN 
									: (Double.compare(wallEndRadius, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND ? WallPiece.WALL_END_TYPE_ROUNDED : WallPiece.WALL_END_TYPE_ABRUPT));
						// right end will be abrupt if not a corner, nor a goal (wouldn't be here) and if wallEndRadius = 0
						int rightEndType = 
							(this.isCornerWithNonSpaceOtherSide(i - 1, false, places) ? WallPiece.WALL_END_TYPE_CORNER_JOIN 
									: (Double.compare(wallEndRadius, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND ? WallPiece.WALL_END_TYPE_ROUNDED : WallPiece.WALL_END_TYPE_ABRUPT));
//System.out.println("WallFactory...make wall piece from/to="+currLineStartPlace+"/"+toWhere+" distance="+(toWhere - currLineStartPlace) );
						this.makeWallPiece(toWhere, isCorner, currNumPlaces, leftEndType, rightEndType, wallEndRadius, colorArrangement, obstacles);
					}
					gotLine = false;
				}
				currLineEndPlace = currLineStartPlace = 0;
					
			} // == PLACE_EMPTY
		} // for
		
		// finished the lines, pre-process the goals... assign lengths to sides
		// have to do this first because some goals will refer back to others
		if (orderedGoalSpecs != null) { // actually area some goals
			for (int j = 0; j < orderedGoalSpecs.length; j++) 
				this.preProcessGoal(orderedGoalSpecs[j]);
	
			// now finish the goals, assemble first
			boolean doneFirstGoal = false;
			for (int j = 0; j < orderedGoalSpecs.length; j++) {
				// avoid duplicating the first goal when it is in position 0, it will be at the last place as well
				if (firstSpotIsGoal && j == orderedGoalSpecs.length - 1 && doneFirstGoal)
					; // ignore it
				else
					this.assembleGoal(orderedGoalSpecs[j]);
				
				// NW corner goal
				if (j == 0 && orderedGoalSpecs[j].placementIdx == 0)
					doneFirstGoal = true;
			}
			// now adjust right sides of any sharers
			for (int j = 0; j < (firstSpotIsGoal ? orderedGoalSpecs.length - 1 : orderedGoalSpecs.length); j++) { // avoid duplicating last one again
				this.finishGoal(orderedGoalSpecs[j], obstacles);
			}
		}
	}
	
	private boolean isCornerWithNonSpaceOtherSide(int wallIdx, boolean lookToLeft, int[] places) {
		int effectiveWall = (int)((wallIdx) / 8);
		
		if (effectiveWall * 8 == wallIdx && lookToLeft) // first place on wall, look left
			;
		else if (effectiveWall * 8 + 7 == wallIdx && !lookToLeft) // last place on wall, look right
			;
		else { // not a corner place, return false
//			System.out.println("got wallIdx="+wallIdx+" effectiveWall="+effectiveWall+" but not a corner place");
			return false;
		}
		
		boolean foundSpace = true;
		if (lookToLeft) {
			int checkWall = (effectiveWall == 0 ? 3 : effectiveWall - 1);
			int checkIdx = checkWall * 8 + 7;
			foundSpace = (places[checkIdx] == PLACE_EMPTY);
//			System.out.println("got wallIdx="+wallIdx+" effectiveWall="+effectiveWall+" checkWall="+checkWall+" checkIdx="+checkIdx+" foundSpace="+foundSpace+" looking="+(lookToLeft? "left": "right"));
		}
		else { // look right
			int checkWall = (effectiveWall == 3 ? 0 : effectiveWall + 1);
			int checkIdx = checkWall * 8 + 1;
			foundSpace = (places[checkIdx] == PLACE_EMPTY);
//			System.out.println("got wallIdx="+wallIdx+" effectiveWall="+effectiveWall+" checkWall="+checkWall+" checkIdx="+checkIdx+" foundSpace="+foundSpace+" looking="+(lookToLeft? "left": "right"));
		}
		return !foundSpace;
	}
	
	private void assembleGoal(GoalSpec goalSpec) {
		
		EmbeddedGoal gatedGoal = new EmbeddedGoal(goalSpec);
		gatedGoal.makePieces(); 
		// goalSpec will detach the left wall from the goal and give back to the right side if it came from one
		goalSpec.setGoal(gatedGoal);
	}
	
	private void finishGoal(GoalSpec goalSpec, ArrayList<StrikeableGamePiece> obstacles) {

		goalSpec.adjustRightSide();
		StrikeableGamePiece[] goalPieces = goalSpec.assembledGoal.getObstacles();
		for (int i = 0; i < goalPieces.length; i++)
			obstacles.add(goalPieces[i]);
	}
	
	private void preProcessGoal(GoalSpec goalSpec) {
		
		int placementIdx = goalSpec.placementIdx;
		boolean isCorner = (placementIdx % 8 == 0);
		int currWall = (int)(placementIdx / 8);
		int wallIdx = (int)(placementIdx - currWall * 8);
		Location2D placement = new Location2D(0.0, 0.0);
		// set the lengths
		GoalSpec leftGoal = goalSpec.leftLineFromGoal;
		if (leftGoal != null) {
			if (Double.compare(leftGoal.rightTotalLength, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) {
				goalSpec.leftTotalLength = leftGoal.rightTotalLength / 2.0;
				leftGoal.rightTotalLength /= 2.0;
			}
		}
		else if (Double.compare(goalSpec.leftNumberOfPlaces, 0.0) != Constants.DOUBLE_COMPARE_EQUAL) {
			int effectivePlace = (int)(placementIdx == 0 ? LAST_PLACE : placementIdx);
			LineVector2D leftLine = getLineVector(effectivePlace, goalSpec.leftNumberOfPlaces);
			goalSpec.leftTotalLength = leftLine.getLength();
		}
		// because it could be the 2nd time round for a goal in the NW corner which has to come
		// round again to complete its left side
		if (!goalSpec.preProcessed) {
			if (Double.compare(goalSpec.rightNumberOfPlaces, 0.0) != Constants.DOUBLE_COMPARE_EQUAL) { 
				int effectivePlace = (int)(placementIdx + goalSpec.rightNumberOfPlaces);
				LineVector2D rightLine = getLineVector(effectivePlace, goalSpec.rightNumberOfPlaces);
				goalSpec.rightTotalLength = rightLine.getLength();
			}
	
			int orientation = -1;
			if (isCorner) {
				switch (placementIdx) {
				case PLACE_NW : orientation = EmbeddedGoal.GATED_CORNER_GOAL_NW;
								placement.setLocation(this.xPosNw, this.yPosNw);
								break;
				case PLACE_NE : orientation = EmbeddedGoal.GATED_CORNER_GOAL_NE;
								placement.setLocation(this.xPosNe, this.yPosNe);
								break;
				case PLACE_SE : orientation = EmbeddedGoal.GATED_CORNER_GOAL_SE;
								placement.setLocation(this.xPosSe, this.yPosSe);
								break;
				case PLACE_SW : orientation = EmbeddedGoal.GATED_CORNER_GOAL_SW;
								placement.setLocation(this.xPosSw, this.yPosSw);
								break;
				}
			}
			else { // not a corner
				// offsets determined by position, multiply by the index in that wall
				boolean isWidth = currWall % 2 == 0;
				double xoffset = (isWidth ? widthPlaceOffset * wallIdx: 0.0);
				double yoffset = (isWidth ? 0.0 : heightPlaceOffset * wallIdx);
				switch (currWall) {
				case WALL_N : orientation = EmbeddedGoal.GATED_GOAL_NORTH; 
							placement.setLocation(this.xPosNw + xoffset, this.yPosNw);
							break;
				case WALL_E : orientation = EmbeddedGoal.GATED_GOAL_EAST; 
							placement.setLocation(this.xPosNe, this.yPosNe + yoffset);
							break;
				case WALL_S : orientation = EmbeddedGoal.GATED_GOAL_SOUTH; 
							placement.setLocation(this.xPosSe - xoffset, this.yPosSe);
							break;
				case WALL_W : orientation = EmbeddedGoal.GATED_GOAL_WEST; 
							placement.setLocation(this.xPosSw, this.yPosSw - yoffset);
							break;
				}
			}
			goalSpec.setPlacement(placement);
			goalSpec.setOrientation(orientation);
			goalSpec.preProcessed = true;
		}
	}
	
	private LineVector2D getLineVector(int effectiveIdx, double currLinePlaces) {
		int effectiveWall = (int)((effectiveIdx -1) / 8);
		int effectiveWallIdx = (int)(effectiveIdx - effectiveWall * 8);
		boolean isWidth = effectiveWall % 2 == 0;
		if (Double.compare(currLinePlaces, 0.0) == Constants.DOUBLE_COMPARE_EQUAL)
			return null; // abandon the line it cancelled itself out
		
		double currLineLen = (currLinePlaces) * (isWidth ? widthPlaceOffset : heightPlaceOffset);

		double endXpos = 0.0;
		double endYpos = 0.0;
		// offsets determined by position to draw the line up to, which is the one before this one
		// unless the end of the line is not a corner, because then actually want it to end one before that
		double endXoffset = (isWidth ? widthPlaceOffset * effectiveWallIdx : 0.0);
		double endYoffset = (isWidth ? 0.0 : heightPlaceOffset * effectiveWallIdx);
		Vector2D vector = null;
		if (effectiveWall == WALL_N) {
			endXpos = xPosNw + endXoffset;
			endYpos = yPosNw;
			vector = new Vector2D(-currLineLen, 0.0);
		}
		else if (effectiveWall == WALL_E) { 
			endXpos = xPosNe;
			endYpos = yPosNe + endYoffset;
			vector = new Vector2D(0.0, -currLineLen);
		}
		else if (effectiveWall == WALL_S) { 
			endXpos = xPosSe - endXoffset;
			endYpos = yPosSe;
			vector = new Vector2D(currLineLen, 0.0);
		}
		else if (effectiveWall == WALL_W) { 
			endXpos = xPosSw;
			endYpos = yPosSw - endYoffset;
			vector = new Vector2D(0.0, currLineLen);
		}
		
		return new LineVector2D(new Location2D(endXpos, endYpos), vector);
	}
	
	private Vector2D getToEdgeVector(int effectiveIdx, boolean isCorner, boolean forLeftWall) {
		
		// want the edge for the index, when it's a corner, for left wall it will be behind (prev idx)
		// for right wall is ok as is
		if (isCorner && forLeftWall) effectiveIdx--;
		if (effectiveIdx == -1) effectiveIdx = 31; // for NW corner
		
		int effectiveWall = (int)(effectiveIdx / 8);
		
		Vector2D toEdgeV = southToNorthV;
		if (effectiveWall == WallFactory.WALL_E) toEdgeV = westToEastV;
		else if (effectiveWall == WallFactory.WALL_S) toEdgeV = northToSouthV; 
		else if (effectiveWall == WallFactory.WALL_W) toEdgeV = eastToWestV; 
		
		return toEdgeV;
	}
	
	private void makeWallPiece(int effectiveIdx, boolean isCorner, double currLinePlaces, int leftEndType, int rightEndType, double wallEndRadius, ColorArrangement colorArrangement, ArrayList<StrikeableGamePiece> obstacles) {

//		if (isCorner) effectiveIdx--;

		LineVector2D line = getLineVector(effectiveIdx, currLinePlaces);

		int effectiveWall = (int)((isCorner ? effectiveIdx - 1 : effectiveIdx) / 8);
		double dotNS = northToSouthV.getDotProduct(line);
		double dotWE = westToEastV.getDotProduct(line);
		int lineDirection = WallPiece.WALL_LINE_DIRECTION_LEFT_TO_RIGHT;
		
		if ((effectiveWall == WallFactory.WALL_N && Double.compare(dotWE, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND) 
			|| (effectiveWall == WallFactory.WALL_E && Double.compare(dotNS, 0.0) == Constants.DOUBLE_COMPARE_FIRST_LESS_THAN_SECOND)
			|| (effectiveWall == WallFactory.WALL_S && Double.compare(dotWE, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND) 
			|| (effectiveWall == WallFactory.WALL_W && Double.compare(dotNS, 0.0) == Constants.DOUBLE_COMPARE_FIRST_GREATER_THAN_SECOND))
			lineDirection = WallPiece.WALL_LINE_DIRECTION_RIGHT_TO_LEFT;
		
		Vector2D toEdgeV = this.getToEdgeVector(effectiveIdx, isCorner, true); 
		WallPiece wallPiece = new WallPiece(line, lineDirection, effectiveWall, toEdgeV, leftEndType, rightEndType, wallEndRadius, this.wallThickness, true, colorArrangement);
		wallPiece.makePieces(obstacles);
	}

	/*
	 * Rules for implementing the sides:
	 * 1. Any corners are considered first
	 * 2. Try also to place other goals around
	 * 3. If a goal side is set it is drawn 
	 * 		a. along the whole line unless
	 * 		b. it hits another goal where it stops (regardless of whether that goal has side set)
	 * 		c. it hits a space defined as empty where it stops
	 * 4. Any sides set to be obstacles are drawn except
	 * 		a. where there's a goal
	 * 		b. where a space has been defined
	 * 5. All specs are attempted following these rules, if there isn't space or there are
	 * 		contradictions no failures will happen, just poor looking results (GIGO)
	 */
	
	public class GoalSpec {

		private int placementIdx; // see above
		private double totalWidth; 
//		private double height; // for now all goals are square and have same height + width 
//		private int goalShape; // the width of the goal opening is totalWidth - postWidth 
		private double postWidth;
		private int leftNumberOfPlaces;
		private int rightNumberOfPlaces;
		private double leftTotalLength;
		private double rightTotalLength;
		private ColorArrangement colorArrangement;
		private Location2D placement;
		private int orientation;
		private GoalSpec leftLineFromGoal; 
		private double lengthToAddToRightSide;
		private boolean preProcessed = false;
		private double clipDepth;
		private boolean addToPlayClipZone = false;
		private EmbeddedGoal assembledGoal;
		private int rightWallEndType = WallPiece.WALL_END_TYPE_NONE;
		private int rightWall;
		private Vector2D rightToEdgeV;
		private int leftWallEndType = WallPiece.WALL_END_TYPE_NONE;
		private int leftWall;
		private Vector2D leftToEdgeV;
		private double wallEndRadius;
//		private LineVector2D leftWall;
//		private LineVector2D rightWall;

		public GoalSpec(int placementIdx, double totalWidth, double postWidth, double clipDepth, boolean addToPlayClipZone, ColorArrangement colorArrangement) {
			this.placementIdx = placementIdx; // see above
			this.totalWidth = totalWidth; 
			this.postWidth = postWidth;
			this.colorArrangement = colorArrangement;
			this.clipDepth = clipDepth;
			this.addToPlayClipZone = addToPlayClipZone;
		}
		public GoalSpec(int placementIdx, double totalWidth, double postWidth, double clipDepth, boolean addToPlayClipZone, double leftTotalLength, double rightTotalLength, ColorArrangement colorArrangement) {
			this(placementIdx, totalWidth, postWidth, clipDepth, addToPlayClipZone, colorArrangement);
			this.leftTotalLength = leftTotalLength;
			this.rightTotalLength = rightTotalLength;
		}
		
		public void setLeftLength(double len) { leftTotalLength = len; }
		public void setRightLength(double len) { rightTotalLength = len; }
		public void setPlacement(Location2D placement) { this.placement = placement; }
		public void setOrientation(int orientation) { this.orientation = orientation; }
		
		public void setGoal(EmbeddedGoal assembledGoal) {
			this.assembledGoal = assembledGoal;
			if (leftLineFromGoal != null) { // got left side from another goal, give it back now
				leftLineFromGoal.lengthToAddToRightSide = assembledGoal.getLengthPastEdgePoint(true);
				assembledGoal.detachWall(true); // left
			}
		}
		
		/*
		 * because another goal may have previously shared this one's right side in order to make its left side
		 * it then gave it back... so adjust here
		 */
		public void adjustRightSide() {
			if (Double.compare(this.lengthToAddToRightSide, 0.0) != Constants.DOUBLE_COMPARE_EQUAL) {
				assembledGoal.appendWallLength(false, this.lengthToAddToRightSide);
				
			}
		}
		
		public double getWidth() { return this.totalWidth - this.postWidth; }
		public double getPostRadius() { return this.postWidth / 2.0; }
		public double getClipDepth() { return this.clipDepth; }
		public boolean getAddToPlayClipZone() { return this.addToPlayClipZone; }
		public double getLeftLength() { return this.leftTotalLength; }
		public double getRightLength() { return this.rightTotalLength; }
		public Location2D getPlacement() { return this.placement; }
		public int getOrientation() { return this.orientation; }
		public ColorArrangement getColorArrangement() { return colorArrangement; }
		public double getWallEndRadius() { return this.wallEndRadius; }
		public int getLeftWallEndType() { return this.leftWallEndType; }
		public int getRightWallEndType() { return this.rightWallEndType; }
		public int getRightWall() { return this.rightWall; }
		public int getLeftWall() { return this.leftWall; }
		public Vector2D getRightToEdgeVector() { return this.rightToEdgeV; }
		public Vector2D getLeftToEdgeVector() { return this.leftToEdgeV; }
		public boolean getLeftWallFromGoal() { return this.leftLineFromGoal != null; }
		public String toString() {
			return "Line lengths from centre: left = "+leftTotalLength+" right = "+rightTotalLength;
		}
	}

}  // end of class
