static final float ADVANCE_DIST = 5f;
static final int BLOTCH_TEST = 2000; // the div of the original size to produce blotches
static final float SPEED = 1.6f;
static final long LOAD_DELAY_MILLIS = 9000;
static final int MIN_RAD = 5, MAX_RAD = 15;

float low = Float.MAX_VALUE, high = 0f, beatThreshold;
long elapsed = System.currentTimeMillis();
int tick = 0, currImg = -1;

PImage[] imgs; 
ArrayList<Blotch> blotches = new ArrayList<Blotch>();
Maxim maxim;
AudioPlayer player1;

void setup() {
  imgs = loadImages("pic", ".jpg", 3);
  int w = 0, h = 0;
  for (int i = 0; i < imgs.length; i++) {
    if (imgs[i].width > w) w = imgs[i].width;
    if (imgs[i].height > h) h = imgs[i].height;
  }
  size(w, h);
  
  background(255);
  
  // init blotches to random points on the screen
  for (int i = 0; i < BLOTCH_TEST; i++) {
    blotches.add(new Blotch(random(0, width -1), random(0, height -1), 0, 0, random(MIN_RAD, MAX_RAD)));
  }
  
  // load first image
  loadNextImage();
  
  ellipseMode(CENTER);
  noStroke();
  
  maxim = new Maxim(this);
  player1 = maxim.loadFile("backbeat.wav");
  player1.setLooping(true);
  player1.setAnalysing(true);
  player1.play();
}

void draw() {
  fill(255, 190);
  rect(0, 0, width, height);
  if (tick++ % 50 == 0) {
    resetPower();
  }
  
  long now = System.currentTimeMillis();
  if (now - elapsed > LOAD_DELAY_MILLIS) {
    loadNextImage();
    elapsed = now;
  }
  
  float power = player1.getAveragePower();
  adaptToPower(power);
  
  for (int i = 0; i < blotches.size(); i++) {
    Blotch blotch = blotches.get(i);
    
    // advance returns true for a move so affects colour
    fill(advance(blotch) ? 255 : 0, 0, 0);
    
    float size = blotch.radius * (.8f + Math.abs(sin(radians((tick*i*Math.max(power, beatThreshold))/250))));
    
    pushMatrix();
    translate(blotch.nowAt.vx, blotch.nowAt.vy);
    ellipse(0, 0, size, size);
    popMatrix();
  }
}

void loadNextImage() {
  currImg++;
  currImg %= imgs.length;
  
  PImage img = imgs[currImg];
  for (int i = 0; i < blotches.size(); i++) {
    Blotch blotch = blotches.get(i);

    int p = -1;
    do {
      int t = (int) random(0, img.width * img.height - 1);
      if (brightness(img.pixels[t]) < 128) {
        p = t;
      }
    }
    while (p == -1);  
    
    blotch.placed.vx = p % img.width;
    blotch.placed.vy = (int) (p / img.width);
  }
}

void adaptToPower(float power) {
  if (power < low) {
    low = power;
  }
  if (power > high) {
    high = power;
  }
  
  beatThreshold = low + ((high - low) * .95);
}

void resetPower() {
  high = 0f;
  low = Float.MAX_VALUE;
}

boolean advance(Blotch blotch) {
  // get vector from where now to placement, use ints so don't have tiny fluctuations on floating points
  int vx = (int) (blotch.placed.vx - blotch.nowAt.vx);
  int vy = (int) (blotch.placed.vy - blotch.nowAt.vy);
  
  // already at destination, put it there exactly (because using ints above)
  if (vx == 0 && vy == 0) {
    blotch.nowAt.vx = blotch.placed.vx;
    blotch.nowAt.vy = blotch.placed.vy;
    return false;
  }
  
  float length = (float) Math.sqrt(vx * vx + vy * vy);

  // can get there in one step, put it there and return  
  if (length <= ADVANCE_DIST) {
    blotch.nowAt.vx = blotch.placed.vx;
    blotch.nowAt.vy = blotch.placed.vy;
    return true;
  }
  
  // make unit vectors
  float vxU = vx / length;
  float vyU = vy / length;
  
  blotch.nowAt.vx += vxU * ADVANCE_DIST;
  blotch.nowAt.vy += vyU * ADVANCE_DIST;
  
  return true;
}

class Vector {
  float vx, vy;
  Vector(float vx, float vy) {
    this.vx = vx;
    this.vy = vy;
  }  
}

class Blotch {
  Vector placed;
  Vector nowAt;
  float radius = 1f;
  Blotch(float nowX, float nowY, float placeX, float placeY, float radius) {
    placed = new Vector(placeX, placeY);
    nowAt = new Vector(nowX, nowY);
    this.radius = radius;
  }
}
