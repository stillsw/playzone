package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;
import firstReboundGame.sound.SoundManager;

import java.awt.*;
import java.awt.geom.*;

public class PlayerLives extends GuiComponentAdapter 
{
	// how big to make the little puck lives in relation to the actual puck
	private static final double DEFAULT_SIZE_DIVISOR = 2.5; 
	private static int REACT_TO_EXTRA_LIFE_TENTHS_OF_SECONDS = 18;
	private static int FLASH_REACTION_TIMES = 2; // flash on/off every this many tenths of second
	private static int SPACING = 2; // pixels between lives on feedback area
	private int numLives;
	private int startingLives;
	private int numLines;
	private int maxLivesInWidth;
	private int maxLivesInHeight;
	private double diameter;
	private boolean reactingToExtraLife = false;
	private int reactingSinceGameTick = -1;
	private boolean flashOn = false;
	private TargetDisk lifeDisk;
	private ScoringManager scoringManager;
	private GameBounds gameBounds = GameBounds.getInstance();
	
	public PlayerLives(int startingLives, int puckDiameter, ScoringManager scoringManager, ColorArrangement colorArrangement) {
		super(colorArrangement);
		double diskRadius = (puckDiameter / DEFAULT_SIZE_DIVISOR) / 2.0;
		this.diameter = diskRadius * 2;
		this.startingLives = startingLives;
		this.scoringManager = scoringManager;
		// create a dummy disk to draw as the lives
	    this.lifeDisk = new TargetDisk(null, null, new Location2D(0, 0), diskRadius, colorArrangement);
		this.listenForScoreActions();
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		this.calcLayout(startingLives);
	}

	private void listenForScoreActions() {
		scoringManager.addScoreActionListener(
				new ScoreActionAdapter() {
					public void extraLife(int numLives) {
						PlayerLives.this.calcLayout(numLives);
						PlayerLives.this.reactingToExtraLife = true;
						PlayerLives.this.reactingSinceGameTick = ReboundGamePanel.gameTick;
						PlayerLives.this.soundFileReader.playSound(SoundManager.EXTRA_LIFE);
					}
					public void lostALife(int numLives) {
						PlayerLives.this.calcLayout(numLives);
					}
				});
	}

	private void calcLayout(int numLives) {
		// only update if needed
		if (numLives != this.numLives) {
			// determine the size of the lives to draw... will they fit in the box?
			int width = (int)this.placementRectangle.getWidth();
			this.maxLivesInWidth = (int)((width + SPACING) / this.diameter);
			if (maxLivesInWidth >= this.numLives)
				this.numLines = 1;
			else {
				// too many lives to fit on one line
				int height = (int)this.placementRectangle.getHeight();
				this.maxLivesInHeight = (int)((height + SPACING) / this.diameter);
				if (this.numLives > this.maxLivesInWidth * this.maxLivesInHeight) 
					System.out.println("PlayerLives.update: can't show all the lives in space allocated, will overlap! NumLives="+this.numLives);
			}
			
			this.numLives = numLives;
		}
	}
	
	public void update(int gameTick) {
		if (this.reactingToExtraLife) {
			int tenthsOfSecondsSinceReaction = (int)((gameTick - this.reactingSinceGameTick) / (double)FirstReboundGame.DEFAULT_FPS * 10.0);
			this.reactingToExtraLife = (tenthsOfSecondsSinceReaction < REACT_TO_EXTRA_LIFE_TENTHS_OF_SECONDS);
			this.flashOn = (tenthsOfSecondsSinceReaction % FLASH_REACTION_TIMES == 0);
			this.gameBounds.triggerDrawFeedbackArea();
		}
		else if (this.flashOn) {
			this.flashOn = false;
			this.gameBounds.triggerDrawFeedbackArea();
		}
	}

	public boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
			
			// iterate the number of lives, drawing them right to left, top to bottom as we go... last one may flash on/off during reaction time
			double lineOffset = 0;
			int countThisLine = 0;
			int backgroundDiameter = (int)this.diameter + SPACING;
			int lineEndXpos = (int)(this.placementRectangle.getX() + this.placementRectangle.getWidth());
			for (int i = 1; i <= this.numLives; i++) {
				countThisLine++;
				int xpos = (int)(lineEndXpos - countThisLine * (backgroundDiameter) + SPACING);
				int ypos = (int)(this.placementRectangle.getY() + lineOffset);

				// show flashing by being on/off for the last one
				if (i != this.numLives || !this.flashOn) {
					Rectangle rect = new Rectangle(xpos, ypos, backgroundDiameter, backgroundDiameter);
					g2.setColor(this.colorArrangement.getFillColor());
					this.lifeDisk.getCurrPosn().setLocation(rect.getCenterX(), rect.getCenterY());
					this.lifeDisk.draw(g2);
				}
//				if (lifeColor != null) {
//					g2.setColor(lifeColor);
//					g2.fillOval(xpos + SPACING, ypos + SPACING, this.diameter, this.diameter);
//				}
				// for the next line
				if (countThisLine == this.maxLivesInWidth) {
					countThisLine = 0;
					lineOffset += this.diameter + SPACING;
				}
			}
			
			
			
	    }
		return true; 
	}

}