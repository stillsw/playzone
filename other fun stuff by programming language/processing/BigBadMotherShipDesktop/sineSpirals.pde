
public class SineSpirals {
	final static float SPEED = 1.5f;
	int radius;
	int rotation;
	int hue;

	public SineSpirals(int radius) {
		this.radius = (int)(radius * .75f);
	}

	public void drawSpirals(int gameTick) {
		
		// every few ticks inc the colour
		if (gameTick % 5 == 0) {
			hue = (hue + 1) % 255;
		}

		// draw the circle in the middle, it wobbles around		
		noStroke();
		fill(hue, 255, 255, 10);
		pushMatrix();
		translate(gameTick % 10, 0);
		ellipse(0, 0, radius * 1.3, radius * 1.3);
		popMatrix();
		
		rotation += 6.5f;
		rotate(radians(rotation));

		// sine gives the y value to translate to
		float y = radius * sin(radians((gameTick * SPEED)));
		translate(0, y);
		
		// size depends on the distance from the centre
		float size = map(Math.abs(y), 0, radius, 150, 40);	

		// alpha depends on size
		fill(hue, 255, 255, 105 + (int)size);
		
		ellipse(0, 0, size, size);
		
		
	}
}
