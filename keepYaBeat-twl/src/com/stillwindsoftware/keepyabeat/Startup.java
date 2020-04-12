package com.stillwindsoftware.keepyabeat;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.DisplayMode;

import com.stillwindsoftware.keepyabeat.twlModel.VideoMode;

/**
 * To try to get the app name working on MAC OS X
 * main() now it this class loads before anything else
 * @author tomas
 *
 */
public class Startup {

	// fix that apparently will guarantee use of high-resolution timer, has to
	// go into main before other stuff loads
	// setting daemon tells jvm ok to exit if is the only thread left running
	// (ie. game threads end)
	static {
		new Thread() {
			{
				setDaemon(true);
				start();
			}

			public void run() {
				while (true) {
					try {
						Thread.sleep(Long.MAX_VALUE);
					} catch (Throwable t) {
					}
				}
			}
		};
	}

	public static void main(String[] arg) throws LWJGLException {
		System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Keep Ya Beat"); 
		KeepYaBeat kyb = new KeepYaBeat();
		kyb.run(new VideoMode(new DisplayMode(KeepYaBeat.width.getValue(), KeepYaBeat.height.getValue()), false));
	}

}
