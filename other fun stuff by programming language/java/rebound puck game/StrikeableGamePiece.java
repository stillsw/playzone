package firstReboundGame;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

public abstract class StrikeableGamePiece extends GamePiece
{
	// ref the striking game piece (not the Striker which is only something that hits a puck)
	protected static final int STRIKE_COLOUR_DELAY = FirstReboundGame.DEFAULT_FPS / 3; // so 1/3 second at the moment
	protected MoveableGamePiece struckBy;
	protected boolean isStatic = true;
	protected int updateTick;
	protected int struckUntilGameTick = StrikeableGamePiece.STRIKE_COLOUR_DELAY * -2; // make sure it's less than what would cause a colour change in getColor
	protected Rectangle2D pieceBounds;
	protected Area area; // the area shape of the piece, only some of the subclasses implement this so far
	// part of is used to call up the chain so parent pieces are notified when child piece is struck
	protected StrikeableGamePiece partOf;
	// parts are used to chain down the draw
	protected StrikeableGamePiece[] parts = new StrikeableGamePiece[0];
	protected boolean partOfPerimeter;
	// redraw either once, or until the game tick indicated
	protected boolean redraw = true;
	protected int redrawTillTick;

	public StrikeableGamePiece(Location2D placement, boolean partOfPerimeter, ColorArrangement colorArrangement) {
		super(placement, colorArrangement);
		this.partOfPerimeter = partOfPerimeter;
	} // end of constructor

	public boolean isPartOfPerimeter() { return this.partOfPerimeter; }
	
	public void setStruckBy(MoveableGamePiece struckBy, int gameTick) {	
		this.struckBy = struckBy;
		this.struckUntilGameTick = gameTick + StrikeableGamePiece.STRIKE_COLOUR_DELAY;
		this.redrawTill(this.struckUntilGameTick + 1); // make sure get the redraw after the struck is turned off
		if (this.partOf != null) // striking goes right up the chain
			((StrikeableGamePiece)this.partOf).setStruckBy(struckBy, gameTick);
//		System.out.println(""+this+" struck by "+struckBy+" part of "+this.partOf);
	}
	
	public boolean deferDrawing() { return (this.partOf != null && StrikeableGamePiece.parentHasArea(this)); }
	
	public void setPartOf(StrikeableGamePiece partOf) { 
		this.partOf = partOf;
		partOf.addToParts(this);
	}
	public void addToParts(StrikeableGamePiece part) {
		int newSize = this.parts.length + 1;
		StrikeableGamePiece[] newParts = new StrikeableGamePiece[newSize];
		int i = 0;
		for (; i < this.parts.length; i++) {
//			System.out.println("assigning old part to newParts i="+i);
			newParts[i] = this.parts[i];
		}
//		System.out.println("assigning new part to newParts i="+(newParts.length -1));
		newParts[newParts.length - 1] = part; // last place is the new one
		this.parts = newParts;
	}
	
	/*
	 * recurse up the hierarchy to find the top piece that all parts are of
	 */
	public static StrikeableGamePiece getTopPart(StrikeableGamePiece part) {
		StrikeableGamePiece partIn = part.partOf;
		if (partIn == null)
			return part;
		else
			return getTopPart(partIn);
	}
  
	/*
	 * recurse up the hierarchy to find a parent that has an area so can draw
	 */
	public static boolean parentHasArea(StrikeableGamePiece part) {
		StrikeableGamePiece partIn = part.partOf;
		if (partIn == null)
			return false;
		else if (partIn.area != null)
			return true;
		else 
			return parentHasArea(partIn);
	}
  
	public Area getArea() { return this.area; }
	public MoveableGamePiece getStruckBy() { return this.struckBy; }
	public int getStruckUntilGameTick() { return this.struckUntilGameTick; }
	public boolean isStatic() { return isStatic; } // overridden by MoveableGamePiece
	
	public void appendArea(StrikeableGamePiece nextPiece) {
		this.appendArea(nextPiece.getArea());
	}
	
	public void appendArea(Area newArea) {
		if (newArea != null)
			if (this.area != null)
				this.area.add(newArea);
			else
				this.area = newArea;
	}
	
	public boolean isStruck() { return (struckUntilGameTick >= this.updateTick); }
	
	protected void reset() {};
	
	public Color getFillColor() { 
		if (this.isStruck())
			return this.colors.getStruckFillColor();
		else
			return this.colors.getFillColor(); 
	}
	public Color getStrokeColor() { 
		if (this.isStruck())
			return this.colors.getStruckStrokeColor();
		else
			return this.colors.getStrokeColor();
	}

	public void update(int gameTick) {
//		System.out.println("StrikeableGamePiece.update: called for "+this);
		this.updateTick = gameTick;
	}
	
	/*
	 * this variant allows checking for collision of the moving pieces with the bounds of the piece
	 * and so trigger a redraw
	 */
	public void update(int gameTick, MoveableGamePiece[] mobileObstacles) {
		this.update(gameTick);
		
		// only if this is not part of another piece check for redraw
		if (this.partOf == null && !panel.redrawEverything) {
			// bounds not initialised, try to create first
			if (this.pieceBounds == null)
				this.makeBounds();
			
			if (this.pieceBounds == null)
				System.out.println("StrikeableGamePiece.update: cannot check for redraw, no pieceBounds defined "+this);
			else
			    for (int i = 0; i < mobileObstacles.length; i++) {
			    	Rectangle2D movementBounds = mobileObstacles[i].getMovementBounds();
		    		this.redraw = (this.redraw || (movementBounds != null && movementBounds.intersects(this.pieceBounds)));
		    		// when it's the puck, and being dragged, the movementBounds has the radius of the aimers, which is much bigger
		    		double radius = (movementBounds != null ? movementBounds.getWidth() / 2 : mobileObstacles[i].getRadius());
		    		if (this.redraw && this.partOfPerimeter && this.radiusOverlapsOutOfPlay(mobileObstacles[i].getCurrPosn(), radius)) {
//							System.out.println("StrikeableGamePiece.update: something hit me, redraw everything "+this);
	    				panel.setDrawJustIntoShading();
		    			break;
		    		}
			    }
		}
	}

	/*
	 * subclasses (notable gated goal) should override to see if the radius is past their goal line
	 */
	protected boolean radiusOverlapsOutOfPlay(Location2D posn, double radius) {
		return false;
	}
	
	protected void makeBounds() {
		if (this.area != null) {
			this.pieceBounds = this.area.getBounds2D();
		}
	}
	
	protected void redrawTill(int gameTick) {
		this.redraw = true;
		this.redrawTillTick = gameTick;
	}
		
	protected boolean drawPiece(Graphics2D g2) {
		// need to draw the parts?
		boolean deferDrawing = this.deferDrawing();
		if (this.area == null && !deferDrawing)
			for (int i = 0; i < this.parts.length; ) {
				this.parts[i++].draw(g2);
			}
		
		return !deferDrawing;
	}  

	/*
	 * wraps the logic for when to draw a piece
	 */
	public final void draw(Graphics2D g2) {
		if (this.redraw || panel.redrawEverything) {
			this.drawPiece(g2);
		}
	} 

	// subclasses may override to draw something after the last shading has been done
	protected void drawPieceAfterShading(Graphics2D g2) {
		if (ReboundGamePanel.showDebugging && this.pieceBounds != null) {
			g2.setColor(Color.red);
		    Composite originalComposite = g2.getComposite();
		    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6F));
		    g2.fill(this.pieceBounds);
		    g2.setComposite(originalComposite);
		}
	}
	
	// completes the logic for when to draw a piece
	public final void drawAfterShading(Graphics2D g2) {
		if (this.redraw || panel.redrawEverything || panel.redrawJustIntoShading) {
			this.drawPieceAfterShading(g2);
			
			if (this.updateTick > this.redrawTillTick) {
				this.redraw = false;
//				System.out.println("have drawn piece now set to not draw again "+this);
			}
		}
	}

	
}  // end of class

