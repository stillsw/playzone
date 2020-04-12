package com.stillwindsoftware.keepyabeat.gui;

import org.lwjgl.input.Mouse;

import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Event.Type;
import de.matthiasmann.twl.Timer;
import de.matthiasmann.twl.Widget;

/**
 * Shows a small message at the bottom of the screen that persists for a short time
 * unless the mouse is inside, in which case it waits till the mouse leaves 
 * @author tomas stubbs
 */
public class Notification extends Button {

	private static final int NOTIFICATION_DURATION_MILLIS = 3000;
	
	private final Widget owner;
	private Timer timer;
	private boolean mouseIsInside = false;
	private GuiManager guiManager = TwlResourceManager.getInstance().getGuiManager();
	
	/**
	 * Could be owned by root or another popup
	 * @param owner
	 * @param message
	 */
	public Notification(Widget owner, String message) {//, MutableRectangle availableSpace
		this.owner = owner;
		this.setTheme("notification");
		owner.add(this);
		setText(message);
		startTimer();
	}

	@Override
	protected void layout() {
		adjustSize();
		super.layout();
		setPosition((int)((guiManager.getScreenWidth() / 2.0f) - getWidth() / 2.0f),
				(int)(guiManager.getScreenHeight() - getHeight() * 2));
	}

	
    @Override
	protected boolean handleEvent(Event evt) {
    	if (evt.isMouseEvent()) {
    		mouseIsInside = !(evt.getType() == Type.MOUSE_EXITED);
    	}
    	
		return evt.isMouseEventNoWheel();
	}

	/**
     * Starts a timer for the notification to end, if the mouse is inside at that moment go around again
     */
    private void startTimer() {
		if (timer == null) { // expect always null
			timer = new Timer(getGUI());
			timer.setDelay(NOTIFICATION_DURATION_MILLIS);
			timer.setContinuous(true);
			TwlResourceManager.getInstance().log(LOG_TYPE.debug, this, 
					"Notification.startTimer: notification raised for message="+this.getText()+" duration="+NOTIFICATION_DURATION_MILLIS);
			timer.setCallback(new Runnable() {
				@Override
				public void run() {
					int mouseX = Mouse.getX();
					int mouseY = Mouse.getY();// - KeepYaBeat.root.getHeight();
					
					if (mouseIsInside) {
						TwlResourceManager.getInstance().log(LOG_TYPE.info, this, 
								"Notification.startTimer.<callback>: notification expired but mouse inside, continue");
					}
					else {
						TwlResourceManager.getInstance().log(LOG_TYPE.info, this, 
								"Notification.startTimer.<callback>: notification expired stop timer and remove notification (mouse at "+mouseX+"/"+mouseY+")");
						if (timer != null) { // strange but avoid anything happening to the timer causing NPE
							timer.stop();
							timer = null;
						}
						owner.removeChild(Notification.this);
					}
				}		
			});
		}
		else {
			timer.stop();
		}
		
		timer.start();
    }
    
}
