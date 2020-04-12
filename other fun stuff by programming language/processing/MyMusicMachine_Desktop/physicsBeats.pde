import org.jbox2d.util.nonconvex.*;
import org.jbox2d.dynamics.contacts.*;
import org.jbox2d.collision.*;
import org.jbox2d.common.*;
import org.jbox2d.dynamics.joints.*;
import org.jbox2d.p5.*;
import org.jbox2d.dynamics.*;

class PhysicsBeats {
  private Physics physics; 
  private Vec2 hitCamForce;
  private ArrayList<Body> camBodies = new ArrayList<Body>();
  private ArrayList<Body> bounceBodies = new ArrayList<Body>();
  private int x, y, w, h;
  private int shownBeats;
  private float camW, camH;
  private float groundY = -1f, baseOffset, groundOffset, viewBroadOffsetX, viewBroadOffsetY;
  private final float HIT_FORCE = 20f;
  private PImage camImg, bounceImg;

  /**
   * create with screen coords
   */
  public PhysicsBeats(PApplet caller, int w, int h, int maxBeats) {
    // physics init    
    physics = new Physics(caller, w, h, 0, -10, w * 2, h * 2, w, h, 100);
    physics.setFriction(0f); 

    // need a wall between each beat set
    int walls = maxBeats - 1;
    float wallThickness = 9f;
    // create the max number of cam bodies will need
    camW = (w - walls * wallThickness) / maxBeats;
    camH = camW * 1.5;

    // these sit and receive the push events
    for (int i = 0; i < maxBeats; i++) {
      float camX = i * camW + i * wallThickness;
      physics.setDensity(70f);
      physics.setRestitution(.4f);
      Body camBdy = physics.createRect(camX, y + h - camH, camX + camW, y + h);
      camBodies.add(camBdy);
      
      physics.setRestitution(.8f); // bounce more
      physics.setDensity(20f);
      // these are on top of the cams and bounce up when the cams are hit
      Body bounceBdy = physics.createRect(camX, y + h - camH - camW, camX + camW, y + h - camH);
      bounceBodies.add(bounceBdy);
      
      float wallX = camX + camW;
      physics.setDensity(0f);
      if (i < walls) {
        physics.createRect(wallX, y, wallX + wallThickness, y + h);
      }
    }
  
    camImg = loadImage("cam.png");
    bounceImg = loadImage("bounce.png");
  }

  void draw() {
    // first time in, setup constants for drawing
    if (groundY == -1f) {
      Body ground = physics.getBorder()[2]; // ground index
      groundY = physics.worldToScreen(ground.getWorldCenter()).y;
      float camMult = camH / .5;
      baseOffset = groundY - camMult;
      groundOffset = - groundY - camMult;
      viewBroadOffsetX = x + w / 2;
      viewBroadOffsetY = y + h / 2 - groundY + camMult;
    }
    
    // draw my renderer as well as the jbox one so can see all is in place
    myCustomRenderer(physics.getWorld());
  }

  void setDrawnArea(int x, int y, int w, int h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;    
  }
  
  void setHitCamForce() {
    hitCamForce = new Vec2(0f, HIT_FORCE);
  }
  
  void setShownBeats(int shownBeats) {
    this.shownBeats = shownBeats;
  }
  
  /**
   * apply an upwards force to the cam body for index
   */
  void hitCam(int i) {
    Body cam = camBodies.get(i);
    Vec2 pos = physics.worldToScreen(cam.getWorldCenter());
    // don't keep hitting it once it's moving too far off the ground, or it and/or
    // bounce body could get stuck out of reach
    if (groundY - pos.y < camH * .9f) {
      cam.applyImpulse(hitCamForce, cam.getWorldCenter());
    }
  }
  
  /**
   * sets/unsets default drawing
   */
  void setDrawDebug(PApplet caller, boolean b) {
    if (b) {
      physics.unsetCustomRenderingMethod();
    }
    else {
      physics.setCustomRenderingMethod(caller, "myCustomRenderer");
    }
  }
  
  void myCustomRenderer(World world) {
    // reset modes as they seem to get overwritten
    ellipseMode(CENTER);
    rectMode(CENTER);
    imageMode(CENTER);
    fill(255);
    
    pushMatrix();
    // move drawing to the view area centre
    translate(viewBroadOffsetX, viewBroadOffsetY);
      
    for (int i = 0; i < shownBeats; i++) {
      
      Body bdy = camBodies.get(i);
      Vec2 screenPos = physics.worldToScreen(bdy.getWorldCenter());

      pushMatrix();
      // subtract the coords for each beat (so they draw on same spot)
      translate(-screenPos.x, 0);

      pushMatrix();
      // shove them up a bit so they rotate around a point below their bases
      translate(screenPos.x, baseOffset);
      float div = 360.f / shownBeats;
      rotate(radians(div * (i + 1)));
      
//      rect(0, screenPos.y + groundOffset, camW, camH);
      image(camImg, 0, screenPos.y + groundOffset, camW, camH);
      bdy = bounceBodies.get(i);
      Vec2 bncPos = physics.worldToScreen(bdy.getWorldCenter());
//      rect(0, bncPos.y + groundOffset, camW, camW);
      image(bounceImg, 0, bncPos.y + groundOffset, camW, camW);
      
      popMatrix();
      popMatrix();
    }
    popMatrix();
    imageMode(CORNER);
 }
}
