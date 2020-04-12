package firstReboundGame;

import firstReboundGame.gameplay.*;
import firstReboundGame.gui.*;
import firstReboundGame.sound.SoundManager;

import java.awt.*;
import java.util.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

public class GameBounds implements DrawableGameElement
{
	public static final int NORTH = 0;
	public static final int EAST = 1;
	public static final int SOUTH = 2;
	public static final int WEST = 3;
	public static final int CENTRE = 4;
	private Color fillColor = new Color(255, 255, 133);
	// an easier colour on the eye when just plain (ie. not making any images and keeping drawing speed afap)
	private Color plainBackgroundColor = new Color(255, 255, 204);
	private double puckStrikeBoundaryYpos;
	private Color puckStrikeBoundaryColor = Color.red;
	private Color gameBoundsColor = Color.magenta;
//	private boolean puckBoundarySet = false;
	private static GameBounds instance = new GameBounds();
	private ReboundGamePanel gamePanel;
	private Rectangle bounds;
	private Rectangle strikeBounds;
	private Rectangle wholeGamePlayArea;
	private Area windowMinusFeedbackAreas;
	private Area strikingClippedZone;
	private Area clippingVisibleZone;
	private Area playArea;
	// the area between playArea and clippingVisibleZone... not in play, but in view
	private Area outOfPlayArea;
Area debug;
	// once made this translucent gradient image is filled to make the shading after the mobile obstacles are rendered
	private Area justTheShadowsArea;
	private Area playAreaAndTheShadowsOnly;
	private BufferedImage outOfPlayShadedImage;
	private BufferedImage backGroundImage;
	
	private PlayerFeedbackArea[] playerFeedbackAreas = new PlayerFeedbackArea[4]; // hold a place for any gui areas 
																	// 4 is limit, but could increase if figure out how to lay them out
	
	// only draw feedback areas when there's a change in them, or when striker is doing something, otherwise leave previous image
	boolean drawFeedbackAreas = true;
	
	private GameBounds() {}
	
	public static GameBounds getInstance() { return instance; }
	
	/*
	 * 	return shaded image, this will be used to fill shaded areas out of play with gradient
	 * it is also clipped and transformed for that
	 */
	private BufferedImage makeShadingImage(int width, int height) {
    	BufferedImage shadingImage = new BufferedImage(2, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = shadingImage.createGraphics();        
        // fill a gradient into the image just 2 pixels wide
        GradientPaint paint = new GradientPaint(0, 0, ReboundGamePanel.darkShadowColour, 0, height, ReboundGamePanel.lightShadowColour);
        g2d.setPaint(paint);
        g2d.fillRect(0, 0, 2, height);
        // make an image the full width and fill it with the shading
        BufferedImage shadedEdgeImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = shadedEdgeImage.createGraphics();
		g2.drawImage(shadingImage, 0, 0, width, height, null);

        g2d.dispose();
        g2.dispose();
        
        return shadedEdgeImage;
    }
	
	public PlayerFeedbackArea makePlayerFeedbackArea(int direction, int panelWidth, int panelHeight, int width, int height, int rows, int columns, int border, int cellMargin, ColorArrangement colors) {
		int xpos = 0, ypos = 0;		
		if (direction == EAST) 
			xpos = panelWidth - width;
		else if (direction == SOUTH) 
			ypos = panelHeight - height;
		
		Rectangle rect = new Rectangle(xpos, ypos, width, height);
		playerFeedbackAreas[direction] = new PlayerFeedbackArea(rect, new Point2D.Double(xpos, ypos), rows, columns, border, cellMargin, colors);
		return playerFeedbackAreas[direction];
	}
	
	/*
	 * clip out the feedback areas from the graphics context so that they don't have to be redrawn 
	 */
//	public boolean clipToRevealFeedbackArea(Graphics2D g2) {
//		if (!this.drawFeedbackAreas) {
//			g2.setClip(this.windowMinusFeedbackAreas);
//			return true;
//		}
//		else
//			return false;
//	}
	
	/*
	 * effectively initialises the whole panel, feedback areas need to have been set up before this call
	 * also here the listening for feedback area changes so can redraw it as needed
	 */
	public void setBoundsAndDisplays(Rectangle bounds, boolean leaveSpaceForFeedbackAreas, ScoringManager scoringManager) { 

		this.gamePanel = ReboundGamePanel.getInstance();
		this.windowMinusFeedbackAreas = new Area(new Rectangle(0, 0, ReboundGamePanel.PANEL_WIDTH, ReboundGamePanel.PANEL_HEIGHT));
		
		if (!leaveSpaceForFeedbackAreas)
			this.bounds = bounds;
		else {
			for (int i = 0; i < playerFeedbackAreas.length; i++) {
				if (playerFeedbackAreas[i] != null) {
					Rectangle rect = playerFeedbackAreas[i].getRectangleDivider().getEnclosingRectangle();
					if (i == GameBounds.NORTH) 
						bounds.setBounds(bounds.x, bounds.y + rect.height, bounds.width, bounds.height - rect.height);
					else if (i == GameBounds.EAST) 
						bounds.setBounds(bounds.x, bounds.y, bounds.width - rect.width, bounds.height);
					else if (i == GameBounds.SOUTH) 
						bounds.setBounds(bounds.x, bounds.y, bounds.width, bounds.height - rect.height);
					else if (i == GameBounds.WEST) 
						bounds.setBounds(bounds.x + rect.width, bounds.y, bounds.width - rect.width, bounds.height);
						
					Area rectArea = new Area(rect);
					this.windowMinusFeedbackAreas.subtract(rectArea);					
				}
			}
			this.bounds = bounds;
		}
			
		if (strikeBounds == null)
			strikeBounds = bounds;

		int maxRadius = ReboundGamePanel.MAX_RADIUS;
		this.wholeGamePlayArea = new Rectangle(bounds.x - maxRadius, bounds.y - maxRadius, bounds.width + maxRadius * 2, bounds.height + maxRadius * 2);

		scoringManager.addScoreActionListener(
				new ScoreActionAdapter() {
					public void extraLife(int numLives) {
						GameBounds.this.drawFeedbackAreas = true;		
					}
					public void lostALife(int numLives) {
						GameBounds.this.drawFeedbackAreas = true;		
					}
				});
		scoringManager.addScoreChangeListener(
				new ScoreChangeListener() {
					public void finishedAPlay(int scoreForPlay, int totalScore, int streakWins, boolean extraLife) {
						GameBounds.this.drawFeedbackAreas = true;		
					}
				});
	}
	
	public void listenForStrikerChanges(Striker striker) {
		striker.addStrikerValueChangeListener(
				new StrikerValueChangeListener() {
					public void valuesChanged(int strength, double angle) {
						GameBounds.this.drawFeedbackAreas = true;		
					}
				});
	}
	
	public void triggerDrawFeedbackArea() {
		this.drawFeedbackAreas = true;
	}
	
	public boolean gameElementIsInView(DrawableGameElement gameElement, double radius)
	{
		// given the position and the radius return if the element is visible in the playing area
		Location2D point = gameElement.getCurrPosn();
		if (this.bounds.contains(point.getX(), point.getY()))
			return true;
		else
			return gameZoneIsInView(point.getX(), point.getY(), radius);
	}
	  
	/*
	 * since the actual clipping zone is bigger than the bounds by the radius of the largest
	 * disk, this only needs to check  
	 */
	public boolean gameZoneIsInView(double xPos, double yPos, double radius) {		
		return this.clippingVisibleZone.contains(xPos, yPos, radius * 2, radius * 2);
	}

	// used for detecting if the radius is fully within the bounds of play
	public boolean gameZoneIsInPlay(double xPos, double yPos, double radius) {
		// bit different to in view... to be in play the whole object must be inside the playing area
		
//		// try to find a descrepancy
//		if (this.bounds.contains(xPos - radius, yPos - radius, radius * 2, radius * 2)
//				&& ((xPos - radius < bounds.x)
//						|| (xPos + radius > bounds.x + bounds.width)
//						|| (yPos - radius < bounds.y)
//						|| (yPos + radius > bounds.y + bounds.height)))
//						System.out.println("bounds.contains returns true, but manual checking says not");
//		
		
		return this.bounds.contains(xPos - radius, yPos - radius, radius * 2, radius * 2);
	}  
	
	// used for moving the puck position before striking it
	public boolean gameZoneIsInStrikerArea(double xPos, double yPos, double radius) {
		// bit different to in view... to be in play the whole object must be inside the playing area
		return this.strikeBounds.contains(xPos - radius, yPos - radius, radius * 2, radius * 2);
	}  
	
	public void setPuckStrikeBoundary(double yPos, Color color) {
		puckStrikeBoundaryYpos = yPos;
		strikeBounds = new Rectangle(bounds.x, (int)puckStrikeBoundaryYpos, bounds.width, (int)(bounds.height + bounds.y - puckStrikeBoundaryYpos));
	}	

	public Rectangle getBounds() { return bounds; }
	public Rectangle getStrikeBounds() { return this.strikeBounds; }
//	public Area getPlayArea() { return this.playArea; }
	public Rectangle getWholeGamePlayArea() { return this.wholeGamePlayArea; }
	
	public Area getClippingVisibleZone() {
		if (clippingVisibleZone == null) {
			clippingVisibleZone = new Area(this.wholeGamePlayArea);
		}
		return this.clippingVisibleZone;
	}

	public void subtractFromClippingVisibleZone(Area newArea) {
		if (clippingVisibleZone == null) {
			int maxRadius = ReboundGamePanel.MAX_RADIUS;
			clippingVisibleZone = new Area(new Rectangle(bounds.x - maxRadius, bounds.y - maxRadius, bounds.width + maxRadius * 2, bounds.height + maxRadius * 2));
		}
		this.clippingVisibleZone.subtract(newArea);
		this.subtractFromPlayArea(newArea);
	}
	
	private void initPlayArea() {
		if (playArea == null) {
			playArea = new Area(bounds);
			playArea.intersect(this.clippingVisibleZone);
		}
	}
	
	public void subtractFromPlayArea(Area newArea) {
		initPlayArea();
		this.playArea.subtract(newArea);
	}
	
	/*
	 * return true if the radius from the point can touch the game bounds
	 * used to determine if collision with wall pieces is worth checking for 
	 */
	public boolean withinTouchOfBounds(Location2D posn, double radius) {
		// check for one pixel larger than the radius so that exactly touching does return true as well
		radius++;
		return !this.bounds.contains(posn.getX() - radius, posn.getY() - radius, radius * 2, radius * 2);
	}
	
	/*
	 * called once in set up of game to create all the clipping areas for shaded areas in the out of play area
	 */
	public void makeOutOfPlayAreaImage(StrikeableGamePiece[] pieces) {
		// initialise the playing area
		initPlayArea();
		// initialise the out of play area
		this.outOfPlayArea = new Area(this.clippingVisibleZone);
		this.outOfPlayArea.subtract(playArea);
		// actually want to develop the out of play area into a shaded zone
		// get a clip area for each of the 4 sides, to make corner pieces
		Rectangle2D outOfPlayBounds = this.outOfPlayArea.getBounds2D();
		Point2D centrePoint = new Point2D.Double(outOfPlayBounds.getCenterX(), outOfPlayBounds.getCenterY());
		int[] xPoints = new int[] {(int)outOfPlayBounds.getX(), (int)outOfPlayBounds.getX() + (int)outOfPlayBounds.getWidth(), (int)centrePoint.getX() };
		int[] yPoints = new int[] {(int)outOfPlayBounds.getY(), (int)outOfPlayBounds.getY(), (int)centrePoint.getY() };
		Area outOfPlayNorthArea = new Area(new Polygon(xPoints, yPoints, xPoints.length)); 
		outOfPlayNorthArea.intersect(this.outOfPlayArea);
		xPoints = new int[] {(int)outOfPlayBounds.getX() + (int)outOfPlayBounds.getWidth(), (int)outOfPlayBounds.getX() + (int)outOfPlayBounds.getWidth(), (int)centrePoint.getX() };
		yPoints = new int[] {(int)outOfPlayBounds.getY(), (int)outOfPlayBounds.getY() + (int)outOfPlayBounds.getHeight(), (int)centrePoint.getY() };
		Area outOfPlayEastArea = new Area(new Polygon(xPoints, yPoints, xPoints.length)); 
		outOfPlayEastArea.intersect(this.outOfPlayArea);
		xPoints = new int[] { (int)outOfPlayBounds.getX(), (int)outOfPlayBounds.getX() + (int)outOfPlayBounds.getWidth(), (int)centrePoint.getX()};
		yPoints = new int[] {(int)outOfPlayBounds.getY() + (int)outOfPlayBounds.getHeight(), (int)outOfPlayBounds.getY() + (int)outOfPlayBounds.getHeight(), (int)centrePoint.getY() };
		Area outOfPlaySouthArea = new Area(new Polygon(xPoints, yPoints, xPoints.length)); 
		outOfPlaySouthArea.intersect(this.outOfPlayArea);
		xPoints = new int[] {(int)outOfPlayBounds.getX(), (int)outOfPlayBounds.getX(), (int)centrePoint.getX() };
		yPoints = new int[] {(int)outOfPlayBounds.getY(), (int)outOfPlayBounds.getY() + (int)outOfPlayBounds.getHeight(), (int)centrePoint.getY() };
		Area outOfPlayWestArea = new Area(new Polygon(xPoints, yPoints, xPoints.length)); 
		outOfPlayWestArea.intersect(this.outOfPlayArea);
		// apply the walls and goals from the static obstacles to the out of play area
		ArrayList<EmbeddedGoal> goalsList = new ArrayList<EmbeddedGoal>();
		for (int i = 0; i < pieces.length; i++) {
			if (pieces[i].getArea() != null)
				this.outOfPlayArea.subtract(pieces[i].getArea());
			
			if (pieces[i] instanceof EmbeddedGoal) {
				EmbeddedGoal goal = (EmbeddedGoal)pieces[i];
//				Area goalArea = goal.getGoalArea();
//				if (goalArea != null)
//					this.outOfPlayArea.add(goalArea);
				// include the area beneath the goal net as visible
				Area backOfNetArea = goal.getBackOfNetArea();
				this.clippingVisibleZone.add(backOfNetArea);
				if (goal.getWallType() == EmbeddedGoal.WALL_TYPE_CORNER) {
					// make the goal areas out of play (so they don't show the outlines
					Area frontOfNetArea = goal.getFrontOfNetArea();
					if (frontOfNetArea != null)
						this.outOfPlayArea.subtract(frontOfNetArea);
					if (backOfNetArea != null)
						this.playArea.subtract(backOfNetArea);
				}
				goalsList.add(goal);
			}
		}
		outOfPlayNorthArea.intersect(this.outOfPlayArea);
		outOfPlayEastArea.intersect(this.outOfPlayArea);
		outOfPlaySouthArea.intersect(this.outOfPlayArea);
		outOfPlayWestArea.intersect(this.outOfPlayArea);
		
		outOfPlayArea.intersect(this.clippingVisibleZone);
		
        // create the gradient fill to apply to edges
		BufferedImage shadedEdgeImage = makeShadingImage(ReboundGamePanel.PANEL_WIDTH, ReboundGamePanel.MAX_RADIUS);		
		// initialise the image to draw everything to
		this.outOfPlayShadedImage = new BufferedImage(ReboundGamePanel.PANEL_WIDTH, ReboundGamePanel.PANEL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = this.outOfPlayShadedImage.createGraphics();
        // and draw it all
        this.makeShadedZone(g2, shadedEdgeImage, outOfPlayNorthArea, outOfPlayEastArea, outOfPlaySouthArea, outOfPlayWestArea, goalsList);
		// dispose of graphics context
		g2.dispose();
	}
	/*
	 * make the shaded image... the graphics context is for that image... it is not the graphics context used for the main rendering
	 * use this to build the area of the shading too, so can use it for when not displaying the image (for rendering efficiency)
	 */
	private void makeShadedZone(Graphics2D g2, BufferedImage shadedEdgeImage, Area outOfPlayNorthArea, Area outOfPlayEastArea, Area outOfPlaySouthArea, Area outOfPlayWestArea, ArrayList<EmbeddedGoal> goalsList) {
		this.justTheShadowsArea = (Area)outOfPlayNorthArea.clone(); 
		this.justTheShadowsArea.add(outOfPlayEastArea);
		this.justTheShadowsArea.add(outOfPlaySouthArea);
		this.justTheShadowsArea.add(outOfPlayWestArea);
		g2.setClip(outOfPlayNorthArea);
		// north side, no transformation
		g2.drawImage(shadedEdgeImage, this.outOfPlayArea.getBounds().x, this.outOfPlayArea.getBounds().y, null);
		AffineTransform origTransform = g2.getTransform();
		// east side translate and transform
		AffineTransform eastSide = AffineTransform.getTranslateInstance(this.outOfPlayArea.getBounds2D().getWidth(), 0.0);
		eastSide.rotate(Math.toRadians(90.0), this.outOfPlayArea.getBounds2D().getX(), this.outOfPlayArea.getBounds2D().getY());
		g2.setTransform(origTransform);
		g2.setClip(outOfPlayEastArea);
		g2.setTransform(eastSide);
		g2.drawImage(shadedEdgeImage, (int)this.outOfPlayArea.getBounds2D().getX(), (int)this.outOfPlayArea.getBounds2D().getY(), null);		
		// south side translate and transform
		AffineTransform southSide = AffineTransform.getTranslateInstance(this.outOfPlayArea.getBounds2D().getWidth(), this.outOfPlayArea.getBounds2D().getHeight());
		southSide.rotate(Math.toRadians(180.0), this.outOfPlayArea.getBounds2D().getX(), this.outOfPlayArea.getBounds2D().getY());
		g2.setTransform(origTransform);
		g2.setClip(outOfPlaySouthArea);
		g2.setTransform(southSide);
		g2.drawImage(shadedEdgeImage, (int)this.outOfPlayArea.getBounds2D().getX(), (int)this.outOfPlayArea.getBounds2D().getY(), null);
		// west side translate and transform
		AffineTransform westSide = AffineTransform.getTranslateInstance(0.0, this.outOfPlayArea.getBounds2D().getHeight());
		westSide.rotate(Math.toRadians(270.0), this.outOfPlayArea.getBounds2D().getX(), this.outOfPlayArea.getBounds2D().getY());
		g2.setTransform(origTransform);
		g2.setClip(outOfPlayWestArea);
		g2.setTransform(westSide);
		g2.drawImage(shadedEdgeImage, (int)this.outOfPlayArea.getBounds2D().getX(), (int)this.outOfPlayArea.getBounds2D().getY(), null);
		g2.setTransform(origTransform);
		// fill all the shadings in goals
		for (Iterator<EmbeddedGoal> it = goalsList.iterator(); it.hasNext(); ) {
			EmbeddedGoal goal = it.next();
			Area backOfNetArea = goal.getBackOfNetArea();
			Area frontOfNetArea = goal.getFrontOfNetArea();
			this.justTheShadowsArea.add(backOfNetArea);
			this.justTheShadowsArea.add(frontOfNetArea);
			if (backOfNetArea != null) {
				g2.setClip(backOfNetArea);
				g2.setColor(ReboundGamePanel.midShadowColour);
				g2.fill(backOfNetArea);
			}
			if (goal.getWallType() == EmbeddedGoal.WALL_TYPE_CORNER && frontOfNetArea != null) {
				g2.setClip(frontOfNetArea);
				g2.setTransform(goal.getAffineTransform());
				// the -n on the ypos just seems to correct a little rendering issue where a small white gap was left on the SE corner goal
				g2.drawImage(shadedEdgeImage, (int)goal.getCurrPosn().getX() - (int)goal.getWidth(), (int)(goal.getCurrPosn().getY() - shadedEdgeImage.getHeight()), shadedEdgeImage.getWidth(), shadedEdgeImage.getHeight(), null);
				g2.setTransform(origTransform);
			}
		}

		g2.setClip(null);
		
		this.playAreaAndTheShadowsOnly = (Area)this.justTheShadowsArea.clone();
		this.playAreaAndTheShadowsOnly.add(this.playArea);
	}  // end of makeShadedZone()
	
	
	public void draw(Graphics2D g2) {
		
	    if (!this.gamePanel.redrawEverything) { 
	    	// not drawing everything reduce clip for bounds to play area
	    	// NB. when moving piece leaves the play area redraw everything is triggered
	    	if (this.gamePanel.redrawJustIntoShading)
	    		g2.setClip(this.playAreaAndTheShadowsOnly);
	    	else
		    	g2.setClip(this.playArea);
	    }

		if (true) {//!ReboundGamePanel.MAKE_IMAGES) {
			g2.setColor(this.plainBackgroundColor);
			g2.fill(this.clippingVisibleZone);
		}
		else {
			g2.setColor(this.fillColor);
			g2.fill(this.clippingVisibleZone);
			Rectangle bgBounds = this.outOfPlayArea.getBounds();
			if (this.backGroundImage == null) {
				this.backGroundImage = new BufferedImage(bgBounds.width, bgBounds.height, BufferedImage.TYPE_INT_ARGB);
		        Graphics2D bg2 = this.backGroundImage.createGraphics();
				CubicCurve2D curve = 
					new CubicCurve2D.Float(0, 0, 
						0, (float)(bgBounds.height * 1.5), 
						bgBounds.width, - (float)(bgBounds.height * .5), 
						bgBounds.width, bgBounds.height);

				bg2.setStroke(new BasicStroke(bgBounds.height / 3));
				bg2.setColor(Color.white);
				bg2.draw(curve);
				bg2.dispose();
				
				
			}
			g2.drawImage(this.backGroundImage, bgBounds.x, bgBounds.y, null);	
			
		}
		
		g2.setClip(null);
		
		// init strikingClippedZone first time
		if (strikingClippedZone == null) {
			strikingClippedZone = new Area(strikeBounds);
			strikingClippedZone.intersect(this.playArea);
		}
		g2.setColor(puckStrikeBoundaryColor);
		g2.draw(strikingClippedZone);

		
		g2.setColor(gameBoundsColor);
		g2.draw(playArea);

	}  // end of draw()

	public void fillShadedZone(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		if (this.gamePanel.MAKE_IMAGES)
			g2.drawImage(this.outOfPlayShadedImage, 0, 0, null);
		else {
			g2.setColor(Color.LIGHT_GRAY);
			g2.fill(this.justTheShadowsArea);
		}

		if (ReboundGamePanel.showDebugging) {
			g2.setColor(Color.green);
			g2.draw(clippingVisibleZone);
		}
	}

	public void drawAfterShading(Graphics2D g2) {
		// draw feedback areas one time, until requested again
		if (this.drawFeedbackAreas) {
			for (int i = 0; i < playerFeedbackAreas.length; i++) {
				if (playerFeedbackAreas[i] != null)
					playerFeedbackAreas[i].draw(g2);
			}
			this.drawFeedbackAreas = false;
		}
//debug=this.playAreaAndTheShadowsOnly;
		if (debug != null) {
			g2.setColor(Color.red);
			g2.fill(debug);
		}
	}

	public double getRadius() {
		return 0;
	}

	public Location2D getCurrPosn() {
		return null;
	}

	public Color getFillColor() {
		return fillColor;
	}

	public Color getStrokeColor() {
		return null;
	}

	public boolean isStatic() {
		return true;
	}
	
}  // end of GameBounds class

