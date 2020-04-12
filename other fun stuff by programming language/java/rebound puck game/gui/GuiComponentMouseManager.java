package firstReboundGame.gui;

import java.awt.Cursor;
import java.awt.event.*;
import firstReboundGame.*;

/*
 * Keep the list of interested gui components to handle mouse events
 */
public class GuiComponentMouseManager implements MouseListener, MouseMotionListener
{
	// these globals are used to indicate which event is being processed, and also as bitwise
	// flags to set which events the component is interested in receiving... this is much
	// quicker than other kinds of conditional flows, and since many mouse events may fire this
	// is desirable
	public static final int MOUSE_EVENT_NONE = 0;
	public static final int MOUSE_EVENT_MOUSE_PRESSED = 1;
	public static final int MOUSE_EVENT_MOUSE_RELEASED = 2;
	public static final int MOUSE_EVENT_MOUSE_CLICKED = 4;
	public static final int MOUSE_EVENT_MOUSE_MOVED = 8;
	public static final int MOUSE_EVENT_MOUSE_DRAGGED = 16;
	public static final int MOUSE_EVENT_MOUSE_ENTERED = 32;
	public static final int MOUSE_EVENT_MOUSE_EXITED = 64;
	// pure flags - combinations
	public static final int MOUSE_EVENT_MOUSE_ENTER_AND_EXIT = MOUSE_EVENT_MOUSE_ENTERED | MOUSE_EVENT_MOUSE_EXITED;
	public static final int MOUSE_EVENT_MOUSE_PRESS_CLICK_AND_RELEASE = MOUSE_EVENT_MOUSE_PRESSED | MOUSE_EVENT_MOUSE_CLICKED | MOUSE_EVENT_MOUSE_RELEASED;
	public static final int MOUSE_EVENT_MOUSE_MOVE_AND_DRAG = MOUSE_EVENT_MOUSE_MOVED | MOUSE_EVENT_MOUSE_DRAGGED;
	// and combinations of these too
	public static final int MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS = MOUSE_EVENT_MOUSE_ENTER_AND_EXIT | MOUSE_EVENT_MOUSE_PRESS_CLICK_AND_RELEASE;
	public static final int MOUSE_EVENT_MOUSE_ALL_EVENTS = MOUSE_EVENT_MOUSE_ENTER_EXIT_AND_CLICKS | MOUSE_EVENT_MOUSE_MOVE_AND_DRAG;
	public static final int MOUSE_EVENT_MOUSE_ALL_EXCEPT_MOVE = MOUSE_EVENT_MOUSE_ALL_EVENTS & ~MOUSE_EVENT_MOUSE_MOVED;
	// mouse over behaviours 
	public static final int MOUSE_EVENT_MOUSE_OVER_ASK_FOR_CURSOR = 128;
	public static final int MOUSE_EVENT_MOUSE_OVER_NO_CURSOR = 256;
	// game state behaviour - receive events even when the game is paused (eg. pause button)
	public static final int MOUSE_EVENT_STATE_GAME_PAUSED = 512;
	public static final int MOUSE_EVENT_ASK_FOR_STATE = 1024;
	// dialog behaviour - receive events even when a dialog is in control (implies pause as well
	public static final int MOUSE_EVENT_STATE_DIALOG_IN_CONTROL = 2048 | MOUSE_EVENT_STATE_GAME_PAUSED;
	
	// cursors
	public static final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);
	public static final Cursor MOVE_CURSOR = new Cursor(Cursor.MOVE_CURSOR);
	public static final Cursor DEFAULT_CURSOR = new Cursor(Cursor.DEFAULT_CURSOR);

	private static final GuiComponentMouseManager instance = new GuiComponentMouseManager();
	private GameEventsManager gameEventsManager = GameEventsManager.getInstance();
	private ReboundGamePanel gamePanel = ReboundGamePanel.getInstance();
	private GuiComponent mouseOverComponent;
	private int mouseOverComponentFlags;
	private GuiComponent[] guiComponents = new GuiComponent[0];
	private int[] bitwiseFlags = new int[0];
	
	private GuiComponentMouseManager() {}
	
	public static GuiComponentMouseManager getInstance() { return instance; }
	
	public void addGuiComponent(GuiComponent guiComponent, int reactToEvents) {
//		System.out.println(""+guiComponent+
//				((reactToEvents & MOUSE_EVENT_MOUSE_ENTERED) == MOUSE_EVENT_MOUSE_ENTERED ? " ENTER" : "")+
//				((reactToEvents & MOUSE_EVENT_MOUSE_EXITED) == MOUSE_EVENT_MOUSE_EXITED ? " EXIT" : "")+
//				((reactToEvents & MOUSE_EVENT_MOUSE_PRESSED) == MOUSE_EVENT_MOUSE_PRESSED ? " PRESS" : "")+
//				((reactToEvents & MOUSE_EVENT_MOUSE_RELEASED) == MOUSE_EVENT_MOUSE_RELEASED ? " RELEASE" : "")+
//				((reactToEvents & MOUSE_EVENT_MOUSE_MOVED) == MOUSE_EVENT_MOUSE_MOVED ? " MOVE" : "")+
//				((reactToEvents & MOUSE_EVENT_MOUSE_DRAGGED) == MOUSE_EVENT_MOUSE_DRAGGED ? " DRAG" : "")
//		);
		{
			GuiComponent[] newGuiComponents = new GuiComponent[this.guiComponents.length + 1];
			System.arraycopy(this.guiComponents, 0, newGuiComponents, 0, this.guiComponents.length);
			newGuiComponents[newGuiComponents.length - 1] = guiComponent;
			this.guiComponents = newGuiComponents;
		}
		// and the bitwise flags array
		{
			int[] newBitwiseFlags = new int[this.guiComponents.length];
			System.arraycopy(this.bitwiseFlags, 0, newBitwiseFlags, 0, this.bitwiseFlags.length);
			newBitwiseFlags[guiComponents.length - 1] = reactToEvents;
			this.bitwiseFlags = newBitwiseFlags;
		}
	}
	
	public void removeGuiComponent(GuiComponent guiComponent) {
		if (this.mouseOverComponent == guiComponent) 
			this.mouseOverComponent = null;

		for (int i = 0; i < this.guiComponents.length; i++)
			if (this.guiComponents[i] == guiComponent) {
				this.guiComponents[i] = null;
				break;
			}
		
	}
	
	private void notifyGuiComponents(int what, MouseEvent e) {
		// only when running
		if (!gameEventsManager.isRunning())
			return;
		
		boolean mouseIsOverComponent = false;
		for (int i = 0; i < this.guiComponents.length; i++) {
			// quick exits (continue)
			// de-registered buttons just set to null, if null miss it
			if (this.guiComponents[i] == null)
				continue;
			// not interested in this type of event? (moved has to go a bit further, because may need to trigger enter/exit)
			if ((what != MOUSE_EVENT_MOUSE_MOVED && ((this.bitwiseFlags[i] & what) != what))
				|| (what == MOUSE_EVENT_MOUSE_MOVED && ((this.bitwiseFlags[i] & MOUSE_EVENT_MOUSE_ENTER_AND_EXIT) != MOUSE_EVENT_MOUSE_ENTER_AND_EXIT)))
				continue;
			
//			System.out.println("going forwards with "+e+" for "+this.guiComponents[i]);
			
			
			if (// game is either not in dialog control, or this button can receive input while that is the case (dialog buttons can)
				(!gamePanel.aDialogIsInControl() || (gamePanel.aDialogIsInControl() && ((this.bitwiseFlags[i] & MOUSE_EVENT_STATE_DIALOG_IN_CONTROL) == MOUSE_EVENT_STATE_DIALOG_IN_CONTROL)))
				// and game is either not paused, or this component can receive events during pause
				&& (!gameEventsManager.isPaused() || (gameEventsManager.isPaused() && ((this.bitwiseFlags[i] & MOUSE_EVENT_STATE_GAME_PAUSED) == MOUSE_EVENT_STATE_GAME_PAUSED)))
				&& ( // and have to ask the component if it should respond to events and it says ok, or don't ask it
					(((this.bitwiseFlags[i] & MOUSE_EVENT_ASK_FOR_STATE) == MOUSE_EVENT_ASK_FOR_STATE) && this.guiComponents[i].canRespondToMouse()) 
					|| ((this.bitwiseFlags[i] & MOUSE_EVENT_ASK_FOR_STATE) != MOUSE_EVENT_ASK_FOR_STATE))
					// and the mouse is over the component
				&& this.guiComponents[i].mouseOverComponent(e.getPoint())
				) {
				mouseIsOverComponent = true;
				if (this.mouseOverComponent != null && this.mouseOverComponent != this.guiComponents[i]) {
					if ((this.mouseOverComponentFlags & MOUSE_EVENT_MOUSE_EXITED) == MOUSE_EVENT_MOUSE_EXITED)
						this.mouseOverComponent.mouseExited(e);
					this.mouseOverComponent = null;
				}
				if (this.mouseOverComponent == null) {
					this.mouseOverComponent = this.guiComponents[i];
					this.mouseOverComponentFlags = this.bitwiseFlags[i];
					if ((this.bitwiseFlags[i] & MOUSE_EVENT_MOUSE_ENTERED) == MOUSE_EVENT_MOUSE_ENTERED)
						this.mouseOverComponent.mouseEntered(e);
				}
				switch (what) {
				case MOUSE_EVENT_MOUSE_PRESSED : this.guiComponents[i].mousePressed(e); break;
				case MOUSE_EVENT_MOUSE_CLICKED : // allow to fall through, just handle with released
				case MOUSE_EVENT_MOUSE_RELEASED : 
					this.guiComponents[i].mouseReleased(e);
					// allowing for dragging behaviours outside the component, will test if still over, and if not send mouse exit (after loop)
					// here set if the mouse is still over the component, which it is by default, unless we have to test responsiveness again
					if (((this.bitwiseFlags[i] & MOUSE_EVENT_ASK_FOR_STATE) == MOUSE_EVENT_ASK_FOR_STATE) && !this.guiComponents[i].canRespondToMouse()) {
						mouseIsOverComponent = false;
					}
					break;
				case MOUSE_EVENT_MOUSE_MOVED :
					if ((this.bitwiseFlags[i] & MOUSE_EVENT_MOUSE_MOVED) == MOUSE_EVENT_MOUSE_MOVED)
						this.guiComponents[i].mouseMoved(e); 
					break;
				case MOUSE_EVENT_MOUSE_DRAGGED : this.guiComponents[i].mouseDragged(e); break;
//				case MOUSE_EVENT_MOUSE_ENTERED : this.guiComponents[i].mouseEntered(e); break;
//				case MOUSE_EVENT_MOUSE_EXITED : this.guiComponents[i].mouseExited(e); break;
				}
			}
		}
		if (mouseIsOverComponent)
			if ((this.mouseOverComponentFlags & MOUSE_EVENT_MOUSE_OVER_ASK_FOR_CURSOR) == MOUSE_EVENT_MOUSE_OVER_ASK_FOR_CURSOR) {
				gamePanel.setCursor(this.mouseOverComponent.mouseOverGetCursor(e.getPoint()));
			}
			else
				gamePanel.setCursor(HAND_CURSOR);
		else {
			gamePanel.setCursor(DEFAULT_CURSOR);
			if (this.mouseOverComponent != null) {
				if ((this.mouseOverComponentFlags & MOUSE_EVENT_MOUSE_EXITED) == MOUSE_EVENT_MOUSE_EXITED)
					this.mouseOverComponent.mouseExited(e);
				this.mouseOverComponent = null;
			}
		}
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_PRESSED, e);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_CLICKED, e);
	}

	/*
	 * Entry and Exit to the panel don't indicate anything to the components on the panel, so don't process this way
	 * These are detected in mouse move instead
	 */
	@Override
	public void mouseEntered(MouseEvent e) {
//		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_ENTERED, e);
	}

	/*
	 * Entry and Exit to the panel don't indicate anything to the components on the panel, so don't process this way
	 * These are detected in mouse move instead
	 */
	@Override
	public void mouseExited(MouseEvent e) {
//		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_EXITED, e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
//		System.out.println("GuiComponentMouseManager.mouseReleased: "+e.getPoint());
		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_RELEASED, e);
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_DRAGGED, e);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		this.notifyGuiComponents(MOUSE_EVENT_MOUSE_MOVED, e);
	}


}
