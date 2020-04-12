package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.platform.PointerEvent;

import de.matthiasmann.twl.Event;

public class TwlMouseEvent implements PointerEvent {

	private PointerEventType eventType;
	private int x;
	private int y;

	public TwlMouseEvent(Event evt) {
		this(evt, 0, 0);
	}

	/**
	 * Create an event with relative mouse position to that passed
	 * @param evt
	 * @param relativeX
	 * @param relativeY
	 */
	public TwlMouseEvent(Event evt, int relativeX, int relativeY) {
		switch (evt.getType()) {
		case MOUSE_BTNUP : eventType = PointerEvent.PointerEventType.ButtonUp; break;
		case MOUSE_BTNDOWN : eventType = PointerEvent.PointerEventType.ButtonDown; break;
		case MOUSE_DRAGGED : eventType = PointerEvent.PointerEventType.Dragged; break;
		case MOUSE_MOVED : eventType = PointerEvent.PointerEventType.Moved; break;
		case MOUSE_ENTERED : eventType = PointerEvent.PointerEventType.Entered; break;
		case MOUSE_EXITED : eventType = PointerEvent.PointerEventType.Exited; break;
		case MOUSE_CLICKED : eventType = PointerEvent.PointerEventType.Clicked; break;
		default : System.out.println("TwlMouseEvent.<init>: unexpected mouse event type="+evt.getType());
		}
		
		x = evt.getMouseX() - relativeX;
		y = evt.getMouseY() - relativeY;
	}
	
	@Override
	public PointerEventType getType() {
		return eventType;
	}

	@Override
	public int getPointerX() {
		return x;
	}

	@Override
	public int getPointerY() {
		return y;
	}

}
