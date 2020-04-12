/* credits
 * Explosion images by Andy Clifton : https://wiki.allegro.cc/index.php?title=Graphics_Depot
 * Spaceship sounds, normal by timgormly : http://www.freesound.org/people/timgormly/sounds/162809/
 *   alarmed by CaCtUs2003 : http://www.freesound.org/people/CaCtUs2003/sounds/101891/
 * Background music by zagi2 : http://www.freesound.org/people/zagi2/sounds/220113/
 * Zap sounds, ground fire by jnr hacksaw : http://www.freesound.org/people/jnr%20hacksaw/sounds/11221/
 *    spaceship fire by Glitchedtones : http://www.freesound.org/people/Glitchedtones/sounds/217409/
 * Spaceship hit by tommccann : http://www.freesound.org/people/tommccann/sounds/235968/
 */

import org.jbox2d.util.nonconvex.*;
import org.jbox2d.dynamics.contacts.*;
import org.jbox2d.testbed.*;
import org.jbox2d.collision.*;
import org.jbox2d.common.*;
import org.jbox2d.dynamics.joints.*;
import org.jbox2d.p5.*;
import org.jbox2d.dynamics.*;

final static float POWER_SHOT_THRESHOLD = .2f, BULLET_SIZE = 5f;
final static int TICKS_FOR_ENEMY_SHOT = 20;

PImage mothershipImg, tipImg, skyImg;
PImage[] explosionImgs;
GameControls gameControls;
MyAudioVisualiser audoVisualiser;
SineSpirals spirals;
Physics physics; 
CollisionDetector detector; 
Body motherShip;
int motherShipSize, gameTick, ticksSinceEnemyShot;
float maxAimLen;
// keep track of any bodies that must be removed after collision
// it will try to remove them during draw
ArrayList<Body> removeBodies = new ArrayList<Body>();
ArrayList<Body> bullets = new ArrayList<Body>();
float[] vector = new float[2]; // temporary holder for vectors to be passed in method calls
Rectangle ground;
Explosion[] explosions = new Explosion[10]; // allow for max 10 concurrent explosions

void setup() {
  initScreenSize();
  
  imageMode(CENTER);
  colorMode(HSB, 255);
  background(0);

  motherShipSize = (int) (Math.min(width, height) / 5);
  maxAimLen = motherShipSize / 8; // motherShipSize is already dependent on window size

  mothershipImg = loadImage("catseyes.png");
  tipImg = loadImage("scrapyard-ground.png");
  explosionImgs = loadImages("explosion/expl", ".png", 6);
  // init explosions for reuse
  for (int i = 0; i < explosions.length; i++) {
    explosions[i] = new Explosion();
  }
  
  gameControls = new GameControls(this);  
  ground = gameControls.getFiringZone();
  audoVisualiser = new MyAudioVisualiser(motherShipSize);
  // the mothership's basic image is a sine based spiral
  spirals = new SineSpirals(motherShipSize);
    
  // make a random starfield based on size of the sky area
  Rectangle skyArea = gameControls.getSkyArea();
  skyImg = createImage(skyArea.width, skyArea.height, ARGB);
  skyImg.loadPixels();
  for (int i = 0; i < skyImg.pixels.length / 500; i++) {
    skyImg.pixels[(int)random(0, skyImg.pixels.length - 1)] = color(255, 60); 
  }
  skyImg.updatePixels();

  // physics init
  physics = new Physics(this, width, height, 0, 0, width*2, height*2, width, height, 100);
  physics.setCustomRenderingMethod(this, "myCustomRenderer");
  // set up collision callbacks
  detector = new CollisionDetector(physics, this);
}

void draw() {
  fill(0, 20);
  rectMode(CENTER);
  rect(width / 2, height / 2, width, height);
  
  imageMode(CORNER);
  drawStarfield();
  image(tipImg, ground.x, ground.y, ground.width, ground.height);
  imageMode(CENTER);
  
  gameControls.drawControls();
  
  // advance any timers and execute actions based on them
  doTimedEvents();
  
  // remove bodies that have collided during detection or timedEvents step
  attemptBodyRemovals();
  
  // draw my renderer as well as the jbox one so can see all is in place
  //myCustomRenderer(physics.getWorld());
}

void drawStarfield() {
  int x = (gameTick / 10) % width;
  // draw 2 images next to each other so get infinite scrolling
  image(skyImg, x, 0);
  image(skyImg, x - width, 0);  
}

/**
 * timed events for game, called during draw()
 */
void doTimedEvents() {
  gameTick++;
  if (gameControls.isPlaying()) {
    
    float power = gameControls.getAveragePower();
    
    // only on a beat (detect power > threshold), and after enough time has elapsed
    // allow mothership to fire a shot at a bullet
    if (ticksSinceEnemyShot++ > TICKS_FOR_ENEMY_SHOT && power > POWER_SHOT_THRESHOLD) {
      ticksSinceEnemyShot = 0;
      motherShipRetaliation();
    }
    
    // play any explosions 
    synchronized (explosions) {
      for(int i = 0; i < explosions.length; i++) {
        int tickDiff = gameTick - explosions[i].tick;
        if (tickDiff >= 0 && tickDiff < explosionImgs.length) {
          image(explosionImgs[tickDiff], explosions[i].x, explosions[i].y);
        }
      }
    }
  }
}

/**
 * rarely and only on a beat, the mothership can fire at a bullet and destroy it
 */
void motherShipRetaliation() {
  synchronized(bullets) {
    if (!bullets.isEmpty()) {
      // take the first bullet from the list
      Body bullet = bullets.remove(0);
      
      Vec2 vMs = physics.worldToScreen(motherShip.getWorldCenter());
      Vec2 vB = physics.worldToScreen(bullet.getWorldCenter());
      
      stroke(255); 
      strokeWeight(5);
      line(vMs.x, vMs.y, vB.x, vB.y);

      // draw an explosion
      pushMatrix();
      translate(vB.x, vB.y);
      noStroke();
      ellipseMode(CENTER);
      for (int i = 1; i <= 255; i++) {
        fill(255, 10 * i);
        float size = width / (i * 5);
        ellipse(0, 0, size, size);
      }
      popMatrix();
      
      // have the bullet removed 
      scheduleBodyRemoval(bullet);
      
      // play the sound
      gameControls.spaceshipShotBullet();
    }
  }
}

/**
 * Takes 2 coordinates and returns a vector of the required length
 * in the float array passed in (to save on object creation)
 */
void setVecLength(float x1, float y1, float x2, float y2, float setToLen, float[] vec) {

  float vx = x2 - x1;
  float vy = y2 - y1;
  float length = (float) Math.sqrt(vx * vx + vy * vy);
  
  // make unit vectors
  float vxU = vx / length;
  float vyU = vy / length;
  
  vec[0] = vxU * setToLen;
  vec[1] = vyU * setToLen;
}

/**
 * takes 2 vectors and returns 0 if the first is shorter or 1 if the second is
 */
int minVectorLen(float vx1, float vy1, float vx2, float vy2) {
  if (vx1 * vx1 + vy1 * vy1 < vx2 * vx2 + vy2 * vy2) {
    return 0;
  }
  else {
    return 1;
  }
}

/**
 * called when a bullet hits something and needs to be removed
 */
void scheduleBodyRemoval(Body body) {
  synchronized(removeBodies) {
    // don't add if already there
    if (removeBodies.indexOf(body) == -1) {     
      removeBodies.add(body);
      synchronized(bullets) {
        bullets.remove(body);
      }
    }
  }
}

/**
 * called during draw, trap any exceptions it generates and exit without removing
 * so it gets a chance next time again
 * this is a hack to get rid of bullets that collide... I haven't found how yet to really remove
 * them, so move them away from the screen
 */
void attemptBodyRemovals() {
  synchronized(removeBodies) {
    for (Body body : removeBodies) {
      body.setPosition(new Vec2(-10000000, -10000000));
    }
  }
}

void mouseDragged() {
  if (gameControls.isPlaying() && !gameControls.isAiming()) {
    if (gameControls.startAimingIfAllowed(mouseX, mouseY)) {
//      println("start aim");
    } 
  }
}

void mouseReleased() {
  if (gameControls.isPlaying() && gameControls.isAiming()) {
    
    // might have to start the mothership moving if it's the first shot
    if (!gameControls.isAlarmed()) {
      Vec2 push = new Vec2(2000, 0);
      motherShip.applyImpulse(push, motherShip.getWorldCenter());
    }
    
    // create a bullet at the aim coordinate (either at touch or at max aim length)
    int[] coords = gameControls.getAimStart();
    float vx2, vy2, aimEndX, aimEndY;
    synchronized (vector) { // synch to prevent vector being used for drawing at the same moment
      setVecLength(coords[0], coords[1], mouseX, mouseY, maxAimLen, vector);
      vx2 = vector[0];
      vy2 = vector[1];
    }
    
    if (minVectorLen(coords[0] - mouseX, coords[1] - mouseY, vx2, vy2) == 0) {
      // line to touch is shortest
      aimEndX = mouseX;
      aimEndY = mouseY;
    }
    else {
      aimEndX = coords[0] + vx2;
      aimEndY = coords[1] + vy2;
    }
    
    Body bullet = physics.createCircle(aimEndX, aimEndY, BULLET_SIZE / 2);
    Vec2 impulse = physics.screenToWorld(new Vec2(coords[0], coords[1]));
    impulse = impulse.sub(bullet.getWorldCenter());
    impulse = impulse.mul(.5);
    bullet.applyImpulse(impulse, bullet.getWorldCenter());

    // add the bullet to the list so it can be accessed for ship firing back (see motherShipRetaliation())
    synchronized(bullets) {
      bullets.add(bullet);
    }
    
    // but don't let the mothership fire at it straight away...
    ticksSinceEnemyShot = 0;

    // reset the coords and aiming
    gameControls.shotFired();    
  }
}

void mouseClicked() {
  boolean wasPlaying = gameControls.isPlaying();
  if (gameControls.processClick()) {
    if (!wasPlaying && gameControls.isPlaying()) {
      startNewGame();
    }
    
    else if (wasPlaying && !gameControls.isPlaying()) {
      stopGame();
    }
  }
}

void startNewGame() {
  if (motherShip == null) {
    // values for the mothership
    physics.setBullet(false);
    physics.setDensity(1000); // not moveable by bullets
    physics.setRestitution(1f); // bounce off the walls without losing force
    physics.setFriction(0f); 
  
    motherShip = physics.createCircle(width/2, 100, motherShipSize / 2);
    
    // reset values for bullets
    physics.setBullet(true);
    physics.setDensity(10f); 
  }

  motherShip.setPosition(physics.screenToWorld(new Vec2(width / 2, height / 4)));
  
  // re-init game controls
  gameControls.startNewGame();
}

void stopGame() {
  if (motherShip != null) {
    physics.removeBody(motherShip);
    motherShip = null;
  }
}

// this function renders the physics scene.
// this can either be called automatically from the physics
// engine if we enable it as a custom renderer or 
// we can call it from draw
void myCustomRenderer(World world) {

  // only when playing is there a mothership
  if (motherShip != null) {
    // reset modes as they seem to get overwritten
    ellipseMode(CENTER);
    rectMode(CENTER);
    Vec2 screenMsPos = physics.worldToScreen(motherShip.getWorldCenter());
    pushMatrix();
    translate(screenMsPos.x, screenMsPos.y);
    
    // draw image for face on mothership
    image(mothershipImg, 0, 0, motherShipSize, motherShipSize);

    // after first shot is fired, the mothership is in an alarm state
    // show visualiser for effects based on track(s)
    if (gameControls.isAlarmed()) {
      audoVisualiser.drawVisuals(gameTick, gameControls.getAveragePower());
    }
    
    // draw the mothership as spirals
    spirals.drawSpirals(gameTick);    

    popMatrix();

    // draw any bullets currently on screen
    stroke(255, 128);
    fill(255);
    synchronized(bullets) {
      for (Body bullet : bullets) {
        Vec2 screenBul = physics.worldToScreen(bullet.getWorldCenter());
        pushMatrix();
        translate(screenBul.x, screenBul.y);
        rotate(-radians(physics.getAngle(bullet)));
        ellipse(0, 0, BULLET_SIZE, BULLET_SIZE);
        popMatrix();
      }
    }

    // draw an aim?
    if (gameControls.isAiming()) {
      int[] coords = gameControls.getAimStart();
      
      // there can be 2 end points, because the touch may be further than
      // the max length allowed for a shot, first test the length to
      // the touch positions in the float[] vector
      float vx2, vy2;
      synchronized (vector) { // synch to prevent vector being used for shot firing at the same moment
        setVecLength(coords[0], coords[1], mouseX, mouseY, maxAimLen, vector);
        vx2 = vector[0];
        vy2 = vector[1];
      }
      
      // which is the shorter distance, 0 = v1, 1 = v2
      strokeWeight(3);
      stroke(255);
      if (minVectorLen(coords[0] - mouseX, coords[1] - mouseY, vx2, vy2) == 0) {
        // line to touch is shortest
        line(mouseX, mouseY, coords[0], coords[1]);
      }
      else {
        // vector has the shortest, draw 2 lines now
        // one is faint for the complete aim, the 2nd shows the max force
        line(coords[0] + vx2, coords[1] + vy2, coords[0], coords[1]);
        stroke(255, 100);
        line(mouseX, mouseY, coords[0], coords[1]);
      }
      
      // turn off stroke in case draw happens
      noStroke();
    }
  }
}

// This method gets called automatically when 
// there is a collision
void collision(Body b1, Body b2, float impulse) {
  
  boolean isWall = (b1.getMass() == 0 || b2.getMass() == 0);
  boolean isMotherShip = (b1.equals(motherShip) || b2.equals(motherShip));
  
  if (isMotherShip) {
    if (isWall) {
    }
    else {
      // increment the score and cause an explosion
      gameControls.incScore();
      
      // now need the coordinates of the bullet
      Body bullet = (b1.equals(motherShip) ? b2 : b1);
      
      synchronized (explosions) {
        // look for a free slot
        for(int i = 0; i < explosions.length; i++) {
          if (explosions[i].tick < gameTick + explosions.length) {
            explosions[i].tick = gameTick + 1;
            Vec2 pos = physics.worldToScreen(bullet.getWorldCenter());
            explosions[i].x = pos.x;
            explosions[i].y = pos.y;
          }
        }
      }
    }
  }
  else if (isWall) {
    //println("bullet hit a wall");
    // remove whichever is not the wall
    scheduleBodyRemoval(b1.getMass() == 0 ? b2 : b1);
  }  
  else {
    //println("something hit a bullet");
  }
}

/**
 * utility class to encapsulate an explosion
 */
class Explosion {
  float x, y;
  int tick = -10; // lower number than 0 minus explosions array length
}

