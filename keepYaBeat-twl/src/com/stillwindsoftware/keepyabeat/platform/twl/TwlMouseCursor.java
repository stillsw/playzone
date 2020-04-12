package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.platform.PointerCursor;

import de.matthiasmann.twl.renderer.MouseCursor;

public class TwlMouseCursor implements PointerCursor {

	private MouseCursor mouseCursor;

	public TwlMouseCursor(MouseCursor mouseCursor) {
		this.mouseCursor = mouseCursor;
	}

	public MouseCursor getMouseCursor() {
		return mouseCursor;
	}
	
}
