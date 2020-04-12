package firstReboundGame.gui;

import firstReboundGame.*;
import firstReboundGame.gameplay.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.*;

public class DifficultyLevel extends GuiComponentAdapter
{
	protected static final TemporalComponentsManager temporalComponentsManager = TemporalComponentsManager.getInstance();
	private static String LABEL = "Level : ";
	private ScoringManager scoringManager;
	private int difficultyLevel;
	private String difficultyDesc;
	private Rectangle2D coloursRect;
	private Rectangle2D easyRect;
	private Rectangle2D medRect;
	private Rectangle2D hardRect;
	private Point2D coloursRectOriginToPanel;
	private static String toolTipText = "Change Level";
	private Rectangle2D toolTipBounds;
	private boolean mouseJustEntered = false;
	private int showToolTipFromGameTick = -1;
	private boolean showToolTip = false;

	
	public DifficultyLevel(ScoringManager scoringManager, ColorArrangement colorArrangement) {
		super(colorArrangement);
		this.scoringManager = scoringManager;
		this.difficultyLevel = scoringManager.getDifficultyLevel();
		this.difficultyDesc = scoringManager.getDifficultyLevelDesc();
		this.listenForScoreActions();
		// set bitwise flags to receive just mouse clicks
		// when over the puck ask the puck which cursor to use (if in the middle want the move cursor not the hand cursor)
		// require that the mouse manager asks the puck to determine if it should receive the mouse event ('cos it doesn't do anything when moving)
		GuiComponentMouseManager.getInstance().addGuiComponent(this, GuiComponentMouseManager.MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS);
		
	}
	
	private void listenForScoreActions() {
		scoringManager.addScoreActionListener(
				new ScoreActionAdapter() {
					public void startedGame(ScoringManager scoringManager) {
						DifficultyLevel.this.difficultyLevel = scoringManager.getDifficultyLevel();
						DifficultyLevel.this.difficultyDesc = scoringManager.getDifficultyLevelDesc();
						DifficultyLevel.this.gameBounds.triggerDrawFeedbackArea();
					}
					public void changedLevel(ScoringManager scoringManager) {
						DifficultyLevel.this.difficultyLevel = scoringManager.getDifficultyLevel();
						DifficultyLevel.this.difficultyDesc = scoringManager.getDifficultyLevelDesc();
						DifficultyLevel.this.gameBounds.triggerDrawFeedbackArea();
					}
				});
	}

	@Override
	public void setPlacement(Rectangle2D placementRectangle, Point2D placementFromPanel) {
		super.setPlacement(placementRectangle, placementFromPanel);
		// construct the rects to show in colour
		double xpos = this.placementRectangle.getX();
		double ypos = this.placementRectangle.getCenterY(); // half size
		this.coloursRect = new Rectangle2D.Double(xpos, ypos, this.placementRectangle.getWidth(), this.placementRectangle.getHeight() / 2);
		double width = this.placementRectangle.getWidth() / 3;
		this.easyRect = new Rectangle2D.Double(xpos, ypos, width, this.coloursRect.getHeight());
		this.medRect = new Rectangle2D.Double(xpos + width, ypos, width, this.coloursRect.getHeight());
		this.hardRect = new Rectangle2D.Double(xpos + width * 2, ypos, width, this.coloursRect.getHeight());
		this.coloursRectOriginToPanel = this.translatePointToPanel(this.coloursRect.getX(), this.coloursRect.getY());
	}
	
	@Override
	public boolean mouseOverComponent(Point2D mouse) {
		this.mouseIsOver = (mouse.getX() >= coloursRectOriginToPanel.getX() 
				&& mouse.getX() <= coloursRectOriginToPanel.getX() + this.coloursRect.getWidth()
				&& mouse.getY() >= coloursRectOriginToPanel.getY()
				&& mouse.getY() <= coloursRectOriginToPanel.getY() + this.coloursRect.getHeight());
		return this.mouseIsOver;
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		// called by the mouse manager code that has already called mouseOverComponent... so it's in there somewhere
		double width = this.easyRect.getWidth();
		double mouseXRelative = e.getX() - this.coloursRectOriginToPanel.getX();
		int newLevel = this.difficultyLevel;
		if (mouseXRelative > width * 2) newLevel = ScoringManager.GAME_DIFFICULTY_HARD;
		else if (mouseXRelative > width) newLevel = ScoringManager.GAME_DIFFICULTY_MEDIUM;
		else newLevel = ScoringManager.GAME_DIFFICULTY_EASY;
		scoringManager.requestNewGame(true, newLevel, ReboundGamePanel.popupColours);
	
	}

	//------------------ following methods to show tooltip are borrowed from MouseButton, any design changes need to be made there first

	@Override
	public void update(int gameTick) {
		super.update(gameTick);
		if (this.mouseJustEntered) {
			this.showToolTipFromGameTick = temporalComponentsManager.getGameTickSecondsFromNow(MouseButton.DELAY_SECONDS_BEFORE_TOOLTIP);
			this.mouseJustEntered = false;
		}
		if (gameTick > this.showToolTipFromGameTick) {
			this.showToolTip = this.mouseIsOver;
			if (!this.showToolTip)
				this.showToolTipFromGameTick = -1;
			this.gameBounds.triggerDrawFeedbackArea(); 
		}
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		this.gameBounds.triggerDrawFeedbackArea(); // just to be safe always allow drawing
		// treat a click as though the mouse just entered, ie. to restart the timing for tooltip
		this.mouseJustEntered = true;
		if (this.showToolTip) {
			this.showToolTip = false;
			this.showToolTipFromGameTick = -1;
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		super.mouseEntered(arg0);
		this.mouseJustEntered = true;
	}
	
	@Override
	public void mouseExited(MouseEvent arg0) {
		super.mouseExited(arg0); 
		if (this.showToolTip) {
			this.showToolTip = false;
			this.showToolTipFromGameTick = -1;
			this.gameBounds.triggerDrawFeedbackArea(); 
		}
	}
	
	
	@Override
	public boolean drawGuiComponent(Graphics2D g2) {
		if (super.drawGuiComponent(g2)) {
			g2.setColor(Color.GREEN);
			g2.fill(this.easyRect);
			g2.setColor(Color.YELLOW);
			g2.fill(this.medRect);
			g2.setColor(Color.BLACK);
			g2.fill(this.hardRect);
			g2.setColor(this.colorArrangement.getStrokeColor());
			g2.draw(this.coloursRect);
	    	int ypos = (int)(this.coloursRect.getY() - g2.getFontMetrics().getDescent());
			g2.drawString(LABEL + this.difficultyDesc, (int)this.coloursRect.getX(), ypos);
	
        	Stroke oriStroke = g2.getStroke();
			g2.setStroke(new BasicStroke(3));
			if (this.difficultyLevel == ScoringManager.GAME_DIFFICULTY_EASY)
				g2.draw(this.easyRect);
			else if (this.difficultyLevel == ScoringManager.GAME_DIFFICULTY_MEDIUM)
				g2.draw(this.medRect);
			else if (this.difficultyLevel == ScoringManager.GAME_DIFFICULTY_HARD)
				g2.draw(this.hardRect);
			
	    	g2.setStroke(oriStroke);

	    	if (this.showToolTip && DifficultyLevel.toolTipText != null) {
		    	int border = 2;
		     	FontRenderContext fontRenderContext = g2.getFontRenderContext();
		    	Font font = new Font("sansserif", Font.PLAIN, 12);
		    	TextLayout textLayout = new TextLayout(DifficultyLevel.toolTipText, font, fontRenderContext);
	    		if (this.toolTipBounds == null) {
			    	Rectangle2D rect = textLayout.getBounds();
			    	this.toolTipBounds = new Rectangle2D.Double(this.coloursRect.getX(), this.coloursRect.getY() + this.coloursRect.getHeight(), rect.getWidth() + border * 2, rect.getHeight() + border * 2);
	    		}
	    		g2.setColor(Color.LIGHT_GRAY);
	    		g2.fill(this.toolTipBounds);
	    		g2.setColor(Color.DARK_GRAY);
	    		g2.draw(this.toolTipBounds);
	    		textLayout.draw(g2, (float)this.toolTipBounds.getX() + border, (float)(this.toolTipBounds.getY() - border + this.toolTipBounds.getHeight()));
	    	}
	    	
	    }
		return true; 
	}

}