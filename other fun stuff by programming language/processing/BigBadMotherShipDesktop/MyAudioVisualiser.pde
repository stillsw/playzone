import java.awt.Rectangle;


public class MyAudioVisualiser {

	private final static float THRESHOLD = .2f;
	private final static int elements = 36;
	private final static int DFLT = 0, RECTS = 1, COLOURS = 2;

	private int drawingAreaHeight;
	private int switchIt, stopFill;
	private boolean randomSwitchColour, randomSwitchShape, randomSwitchDouble;
	private float lowThreshold, lowValue, highValue;

	public MyAudioVisualiser(int size) {
		drawingAreaHeight = size;
	}

	public void drawVisuals(int tick, float power) {
		
		calcLowThreshold(tick, power);

		// draw every few turns, and only when it's above the power threshold
		// this makes it a bit more stacatto
		if (tick % 4 == 0 && power > lowThreshold) { 		
			drawSineVisuals(tick, power);
		}
	}

	// calculate an adaptive value for lowThreshold that is the lower 1/6th of the power band
	void calcLowThreshold(int tick, float power) {
		// reset every 20 ticks 
		if (tick % 20 == 0) {
			lowValue = power;
			highValue = power;
		}
		else {
			if (power < lowValue) {
				lowValue = power;
			}
			if (power > highValue) {
				highValue = power;
			}
			
			float diff = highValue - lowValue;
			// set the low threshold at the lower 1/6 of the band currently playing
			lowThreshold = lowValue + diff / 6f;
		}
	}

	// draw the centre panel audio visuals
	void drawSineVisuals(int tick, float power) {
		doRandomSwitch(power); // switch part of the drawings so something changes every few seconds
		ellipseMode(CENTER);
		rectMode(CENTER);
		pushMatrix();

		boolean doFill = isDoFill(tick);
				
		for (int i = 0; i < elements; i++) {
			float hue = 255/elements*i; 
			if (randomSwitchColour) {
				hue = 255 - hue;
			}
			stroke(hue, 255, 255, 20);
			fill(hue, 255, 255, doFill ? 3 : 0);
			
			// translation with sine (and accummulates with each element)
			// rotate first so it's rotating around its own centre
			rotate(i*power*20);
			translate(sin(i)*(3/power), 0);

			float size = i * power * 65;			
			if (randomSwitchShape) {
				rect(0, 0, size, size);
			}
			else {
				ellipse(0, 0, size, size);
			}
			
			// rotate again, so now it's after the translate
			rotate(i*10);
			
			// once in a while do an explosion
			if (System.currentTimeMillis() % 1000 == 0) {
				stroke(255 - hue, 128);
				pushMatrix();
				translate(i*i % (width / 2),0);
				int lines = 20;
				for (int j = 0; j < lines; j++) {
				rotate(radians(360 / lines));
				rect(-.5*i, -.5*i, .5*i, .5*i);
				}
				popMatrix();
			}
		}
		
		popMatrix();
	}

	// based on a count, do a random switch of various drawing things
	void doRandomSwitch(float power) {
		if (switchIt++ > 10 && power > THRESHOLD) {
		int randomSwitch = (int) random(3);
		switch (randomSwitch) {
			case RECTS : randomSwitchShape = true; break;
			case COLOURS : randomSwitchColour = true; break;
			default : randomSwitchDouble = randomSwitchColour = randomSwitchShape = false; break;
		}

		switchIt = 0;
		}
	}

	// every so often signal to not fill the shapes	
	boolean isDoFill(int tick) {
		if (stopFill-- > 0) {
			return false;
		}
		else {
			// only want a rare event, say every 80 - 100 ticks
			if (tick % random(60, 100) == 0) {
				stopFill = 30; // next time around it will return false
			}
			
			return true; 
		}
	}
}
