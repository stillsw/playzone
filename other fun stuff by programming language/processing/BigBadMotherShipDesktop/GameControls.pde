public class GameControls {

	private final static float THRESHOLD = .2f;
	private final static String SCORE = "Score : ";
	public final static int buttonBoxSize = 40, buttonBoxMargin = 5;
	private final color buttonStroke = color(128, 128, 128);

	private boolean playing = false;
	private Rectangle playBtn, optionsBox, firingZone;

	private Maxim maxim;
	private AudioPlayer spaceshipSound, alarmSound, music, groundFire, spaceshipFire, shipHit;
	private float speedAdjust=1.0;

	// indicates a shot has been fired and the mothership is now in alert mode
	private boolean isAlarmed = false;
	private int[] aimStart = new int[2];
	private int score;

	public GameControls(PApplet processing) {
		// init sounds
		maxim = new Maxim(processing);
		spaceshipSound = maxim.loadFile("spaceship.wav");
		spaceshipSound.setLooping(true);
		alarmSound = maxim.loadFile("spaceship-alarmed.wav");
		alarmSound.setLooping(true);
		music = maxim.loadFile("music-loop.wav");
		music.setLooping(true);
		music.setAnalysing(true);
		music.volume(.5f);
		music.play();
		groundFire = maxim.loadFile("ground-fire.wav");
		groundFire.setLooping(false);
		spaceshipFire = maxim.loadFile("spaceship-fire.wav");
		spaceshipFire.setLooping(false);
		shipHit = maxim.loadFile("spaceship-hit.wav");
		shipHit.setLooping(false);
		// init options box and buttons
		optionsBox = new Rectangle(0, 0, width, buttonBoxSize);	
		int optSize = buttonBoxSize - buttonBoxMargin * 2;
		playBtn = new Rectangle(width - buttonBoxSize, buttonBoxMargin, optSize, optSize);
		// radio buttons from the right inwards
		optSize -= buttonBoxMargin * 2;
		// text stuff
		textSize(buttonBoxSize / 2);
		
		int firingZoneH = height / 4;
		firingZone = new Rectangle(0, height - firingZoneH, width, firingZoneH); 
	}

	public Rectangle getSkyArea() {
		return new Rectangle(0, 0, width, height - firingZone.height);
	}
	
	public void drawControls() {
		clear();
		drawOptions();

		// give a hint if not currently playing
		if (!playing) {
			textAlign(CENTER, CENTER);
			text("Big Bad Mothership\n\nPlay to begin\nDrag on ground to aim and fire", width / 2, height / 2);
		}
	}

	// treats as 2 panels, one where the buttons are at the top and the main one under it
	private void clear() {
		rectMode(CORNER);
		// options panel
		fill(255, 5);
		strokeWeight(1);
		stroke(buttonStroke);
		rect(optionsBox.x, optionsBox.y, optionsBox.width, optionsBox.height);
		//rect(firingZone.x, firingZone.y, firingZone.width, firingZone.height);
	}

	// top buttons
	private void drawOptions() {
		// fill is still background colour, rectmode still corner
		rect(playBtn.x, playBtn.y, playBtn.width, playBtn.height);
		
		stroke(buttonStroke); 
		fill(255);

		// start from the right and work backwards
		if (playing) {
		// stop button (toggles)
		rect(playBtn.x + buttonBoxMargin, playBtn.y + buttonBoxMargin
			, playBtn.width - buttonBoxMargin * 2, playBtn.height - buttonBoxMargin * 2);
		}
		else {
		// play button
		triangle(playBtn.x + buttonBoxMargin, playBtn.y + buttonBoxMargin
			, playBtn.x + playBtn.width - buttonBoxMargin, playBtn.y + playBtn.height / 2
			, playBtn.x + buttonBoxMargin, playBtn.y + playBtn.height - buttonBoxMargin
		);
		}
		
		ellipseMode(CORNER);
		
		// texts
								if (playing) {
			float textY = buttonBoxMargin + playBtn.height / 2;
			textAlign(LEFT, CENTER);
									text(SCORE + score, buttonBoxMargin, textY);
								}
	}

	/**
	 * returns if the game is currently playing
	 */
	 
	public boolean isPlaying() {
		return playing;
	}

	/**
	 * gets the average one or both players
	 */
	public float getAveragePower() {
		if (!playing) {
			return 0f;
		}
		
		return music.getAveragePower();
	}
	
	/**
	 * returns true if the controls did something with the click
	 */
	public boolean processClick() {
		
		if (playBtn.contains(mouseX, mouseY)) {
			playing = !playing;
			if (playing) {
				spaceshipSound.play();
			}
			else {	 
				spaceshipSound.stop();
				stopGame();
			}
			
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * re-initialize a new game
	 */
	public void startNewGame() {
		stopGame();
		aimStart[0] = aimStart[1] = -1;
		score = 0;
	}

	/**
	 * most stuff is stopped simply by setting playing to false
	 * anything else goes here
	 */
	private void stopGame() {
		isAlarmed = false;
		spaceshipSound.speed(1);
		alarmSound.stop();
	}
	
	/**
	 * aiming is reset and the isAlarmed flag is set
	 */
	public void shotFired() {
		if (!isAlarmed) {
			isAlarmed = true;
			spaceshipSound.speed(1.2);
			alarmSound.play();
		}
		aimStart[0] = aimStart[1] = -1;
		groundFire.cue(0);
		groundFire.play();
	}
	
	public void spaceshipShotBullet() {
		spaceshipFire.cue(0);
		spaceshipFire.play();
	}
	
	/**
	 * set when a shot is fired
	 */
	public boolean isAlarmed() {
		return isAlarmed;
	}
	
	/**
	 * tested in mouseDragged/mouseReleased to see if either time to start aiming
	 * or to fire a shot
	 * also tested during drawing to see if a line is needed from touch
	 */
	public boolean isAiming() {
		return aimStart[0] != -1 && aimStart[1] != -1;
	}
	
	/**
	 * called in mouseDragged to init aiming provided the touch is in the firing zone (lower part of screen)
	 */
	public boolean startAimingIfAllowed(int touchX, int touchY) {
		if (firingZone.contains(touchX, touchY)) {
			aimStart[0] = touchX;
			aimStart[1] = touchY;
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	 * the aim start position
	 */
	public int[] getAimStart() {
		return aimStart;
	}
	
	/**
	 * add to score, means there's a hit
	 */
	public void incScore() {
		score++;
		shipHit.cue(0);
		shipHit.play();
	}
	
	public Rectangle getFiringZone() {
		return firingZone;
	}
}

