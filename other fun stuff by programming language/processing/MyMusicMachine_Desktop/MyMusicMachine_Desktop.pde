//The MIT License (MIT) - See Licence.txt for details

//Copyright (c) 2013 Mick Grierson, Matthew Yee-King, Marco Gillies

static final int BPM_START = 120, BPM_MIN = 60, BPM_MAX = 800, 
                 NUM_BEATS_START = 16, NUM_BEATS_MIN = 1, NUM_BEATS_MAX = 24,
                 NUM_TRACKS = 4, REQUEST_FPS = 60,
                 PHYS_W = 900, PHYS_H = 300; // force/mass etc are dependent on world/screen size, so use constant values all devices
static final float SYNTH_START_VOL = .25f, SYNTH_MIN_VOL = 0f, SYNTH_MAX_VOL = .8f;

Maxim maxim;
AudioPlayer[] samples;
boolean[][] tracks;
WavetableSynth[] synths;
int[][] notes = new int[3][16];
int[] transpose = new int[] { 1, 12, 24 };
float[] fc = new float[3], res = new float[3], attack = new float[3], release = new float[3], filterAttack = new float[3];
Slider[] dt = new Slider[3], dg = new Slider[3], a = new Slider[3], r = new Slider[3], f = new Slider[3], 
q = new Slider[3], fa = new Slider[3], o = new Slider[3], vol = new Slider[3];
MultiSlider[] seq = new MultiSlider[3];
RadioButtons synthSelRadio;

int playhead;

int currentBeat;
int maxButtonWidth;
int buttonWidth;
int buttonHeight;
int beatButtonsStartX;

PImage backgroundImage; 

Slider bpmSlider, numBeatsSlider;
Toggle drawPhysicsToggle, visualiseSynth;
boolean isVisualiseSynth = false;
color widgetFills;
int bpm = BPM_START;
int fpb = 0; // frames per beat : calc whenever bpm or fps changes
int numBeats = NUM_BEATS_START;
int tick;
int selectedSynth = 0;

// gather fps stats for bpm accuracy
static final long NANOS_PER_SECOND = 1000000000L;
float trueFps = REQUEST_FPS; // changes as soon as draw() starts
float[] fpsTests = new float[100];
int fpsInd = 0;
boolean doneFullTest = false;
long lastTest = 0L;

// physics beats display
PhysicsBeats physBeats;

MyAudioVisualiser audioVisualiser;

void setup() {
  size(1000, 768);

  colorMode(HSB, 255);
  widgetFills = color(150, 190, 255);
  
  physBeats = new PhysicsBeats(this, PHYS_W, PHYS_H, NUM_BEATS_MAX);
  physBeats.setHitCamForce();
  physBeats.setDrawnArea(width / 2, 0, width / 2, height / 2);
  audioVisualiser = new MyAudioVisualiser(width / 6);

  currentBeat = 0;
  maxButtonWidth = width/NUM_BEATS_START;
  buttonHeight = height/12;

  maxim = new Maxim(this);
  samples = new AudioPlayer[NUM_TRACKS];
  int si = 0;
  loadSound("clap_high.wav", si++);
  loadSound("clap_low.wav", si++);
  loadSound("tamborine.wav", si++);
  loadSound("maracas.wav", si);

  backgroundImage = loadImage("drumMachineBackgrd.png");
  frameRate(REQUEST_FPS);
  
  int sx = 590, sy = 390;
  bpmSlider = new Slider("", bpm, BPM_MIN, BPM_MAX, sx, sy, 300, 20, HORIZONTAL);
  bpmSlider.setInactiveColor(widgetFills);
  sy += 30;
  numBeatsSlider = new Slider("nb", numBeats, NUM_BEATS_MIN, NUM_BEATS_MAX, sx, sy, 300, 20, HORIZONTAL);
  numBeatsSlider.setInactiveColor(widgetFills);
  setBpm(bpm);
  setNumBeats(numBeats);
  
  // synths
  synths = new WavetableSynth[3];
  sx = 60;
  sy = 50;
  initSynth(0, sx, sy);
  initSynth(1, sx, sy);
  initSynth(2, sx, sy);
  
  synthSelRadio = new RadioButtons (3, sx, 15, 120, 20, HORIZONTAL);
  synthSelRadio.setNames(new String[] { "Synth 1", "Synth 2", "Synth 3" });
  color notSelected = color(125, 100, 100, 0);
  synthSelRadio.setColours(widgetFills, notSelected);
  synthSelRadio.set("Synth 1");
  
  // control drawing of default physics with toggle 
  drawPhysicsToggle = new Toggle("", 530, 12, 10, 10);
  drawPhysicsToggle.set(false);
  drawPhysicsToggle.setActiveColor(widgetFills);
  drawPhysicsToggle.setInactiveColor(notSelected);
  physBeats.setDrawDebug(this, drawPhysicsToggle.get());

  visualiseSynth = new Toggle("", 530, 32, 10, 10);
  visualiseSynth.setActiveColor(widgetFills);
  visualiseSynth.setInactiveColor(notSelected);
  visualiseSynth.set(isVisualiseSynth);
}

void initSynth(int si, int sx, int sy) {
  synths[si] = maxim.createWavetableSynth(514);
  synths[si].play();
  synths[si].volume(SYNTH_START_VOL);
  synths[si].setAnalysing(true);

  dt[si] = new Slider("delay (t)", 1, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  dt[si].setInactiveColor(widgetFills);
  dg[si] = new Slider("delay (a)", 1, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  dg[si].setInactiveColor(widgetFills);
  a[si] = new Slider("attack", 1, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  a[si].setInactiveColor(widgetFills);
  r[si] = new Slider("release", 20, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  r[si].setInactiveColor(widgetFills);
  f[si] = new Slider("filter", 20, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  f[si].setInactiveColor(widgetFills);
  q[si] = new Slider("res", 20, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  q[si].setInactiveColor(widgetFills);
  fa[si] = new Slider("filterAmp", 20, 0, 100, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  fa[si].setInactiveColor(widgetFills);
  o[si] = new Slider("transpose", transpose[si], 1, 80, sx, sy, 200, 20, HORIZONTAL); sy += 20;
  o[si].setInactiveColor(widgetFills);
  vol[si] = new Slider("volume", SYNTH_START_VOL, SYNTH_MIN_VOL, SYNTH_MAX_VOL, sx, sy, 200, 20, HORIZONTAL); sy += 24;
  vol[si].setInactiveColor(widgetFills);
  seq[si] = new MultiSlider(notes[selectedSynth].length, 0, 256, 15, 302, 400/15, 150, UPWARDS);
  seq[si].setInactiveColor(widgetFills);
}

void loadSound(String name, int si) {
  samples[si] = maxim.loadFile(name);
  samples[si].setLooping(false);
  samples[si].setAnalysing(true);
}

void draw() {
  adjustFpsStats();
  rectMode(CORNER);
  fill(0, 10);
  rect(0, 0, width, height);

  // translate to the place to draw visuals
  pushMatrix();
  translate(width * .75f, height * .25f);
  audioVisualiser.drawVisuals(tick++, getPowerAverage());
  popMatrix();

  image(backgroundImage, 0, 0);
  
  // synch access to boolean arrays because any change while accessing can throw index out of bounds
  synchronized (numBeatsSlider) {
    // when < 16 beats, the width of each doesn't get any bigger, so the placement of the array changes
    pushMatrix();
    translate(beatButtonsStartX, 0);
    
    // draw a moving square showing where the sequence is 
    fill(150, 190, 255);
    rect(currentBeat*buttonWidth, 500, buttonWidth, buttonHeight*tracks.length);
  
    // draw a red box on each playing track at the current beat
    for (int i = 0; i < numBeats; i++) {
      fill(255);
      text(i + 1, i*buttonWidth + buttonWidth / 2, 500 - 20);
  
      fill(1, 190, 255);
      for (int j = 0; j < tracks.length; j++) {
        if (tracks[j][i]) {
          rect(i*buttonWidth, 500+(j*buttonHeight), buttonWidth, buttonHeight);
        }
      }
    }

    // lines around the beats
    stroke(255);
    for (int i = 0; i < 5; i++)
      line(0, 500+(i*height/12), buttonWidth*numBeats, 500+(i*height/12));
    for (int i = 0; i < numBeats + 1; i++)
      line(i*buttonWidth, 500, i*buttonWidth, 500+(4*height/12));

    popMatrix(); // beat array placement

    // now the synths
    for (int i = 0; i < synths.length; i++) {
      synths[i].setFrequency(mtof[notes[i][tick%16]+30]);
    }

    if (mousePressed) {
      dt[selectedSynth].mouseDragged();
      dg[selectedSynth].mouseDragged();
      a[selectedSynth].mouseDragged();
      r[selectedSynth].mouseDragged();
      f[selectedSynth].mouseDragged();
      q[selectedSynth].mouseDragged();
      fa[selectedSynth].mouseDragged();
      o[selectedSynth].mouseDragged();
      seq[selectedSynth].mouseDragged();
      vol[selectedSynth].mouseDragged();
    }
    
    // process settings from synths[selectedSynth] sliders  
    if (f[selectedSynth].get() != 0)  {   fc[selectedSynth]=f[selectedSynth].get()*100; 
                                          synths[selectedSynth].setFilter(fc[selectedSynth], res[selectedSynth]); }
    if (dt[selectedSynth].get() != 0) {   synths[selectedSynth].setDelayTime((float) dt[selectedSynth].get()/50); }
    if (dg[selectedSynth].get() != 0) {   synths[selectedSynth].setDelayFeedback((int)dg[selectedSynth].get()/100); }
    if (q[selectedSynth].get() != 0)  {   res[selectedSynth]=q[selectedSynth].get() / 50; 
                                          synths[selectedSynth].setFilter(fc[selectedSynth], res[selectedSynth]); }
    if (a[selectedSynth].get() != 0)  {   attack[selectedSynth]=a[selectedSynth].get()*10; }
    if (r[selectedSynth].get() != 0)  {   release[selectedSynth]=r[selectedSynth].get()*10; }
    if (fa[selectedSynth].get() != 0) {   filterAttack[selectedSynth]=fa[selectedSynth].get()*10; }
    if (o[selectedSynth].get() != 0)  {   transpose[selectedSynth]=(int) Math.floor(o[selectedSynth].get()); }
    synths[selectedSynth].volume(vol[selectedSynth].get());
  
    dt[selectedSynth].display();
    dg[selectedSynth].display();
    a[selectedSynth].display();
    r[selectedSynth].display();
    f[selectedSynth].display();
    q[selectedSynth].display();
    fa[selectedSynth].display(); 
    o[selectedSynth].display();
    vol[selectedSynth].display();
    seq[selectedSynth].display();
    
    synthSelRadio.display();
    
    advancePlayhead();    
  }

  if (mousePressed) {
    updateBpmIfGui(true);
    updateNumBeatsIfGui(true);
  }
  
  // physics beats
  physBeats.draw();

  rectMode(CORNER);
  bpmSlider.display();
  numBeatsSlider.display();
  rectMode(CORNER);
  drawPhysicsToggle.display();
  textAlign(LEFT, CENTER);
  text("Show Physics", 545, 16);  
  visualiseSynth.display();
  text("Show Synth", 545, 36);  
}

void myCustomRenderer(World world) {
  // doing this from the draw method instead
//  physBeats.myCustomRenderer(world);
}

float getPowerAverage() {
  float tot = 0f;
  for (int i = 0; i < samples.length; i++) {
    if (samples[i].isPlaying()) {
      tot += samples[i].getAveragePower();
    }
  }
  
  if (isVisualiseSynth) {
    tot += (synths[selectedSynth].getAveragePower() - 1.5); // synth power is too much compared to others
    return tot / samples.length + 1;
  }
  else {
    return tot / samples.length;
  }
}

void updateBpmIfGui(boolean isDraw) {
  if (isDraw) {
    bpmSlider.mouseDragged();
  }
  else {
    bpmSlider.mousePressed();
  }  
  
  int newBpm = (int) bpmSlider.get();
  if (newBpm != bpm) {
    setBpm(newBpm);
  }
}

void setBpm(int b) {
  bpm = b;
  bpmSlider.setName(String.format("BPM (%s)", bpm));
  calcFpb();
}

void updateNumBeatsIfGui(boolean isDraw) {
  if (isDraw) {
    numBeatsSlider.mouseDragged();
  }
  else {
    numBeatsSlider.mousePressed();
  }  
  
  int n = (int) numBeatsSlider.get();
  if (n != numBeats) {
    setNumBeats(n);
    numBeatsSlider.set(n); // because it inits with 1 and then is reset using this method
  }
}

void setNumBeats(int n) {
  numBeats = n;
  buttonWidth = Math.min(width/numBeats, maxButtonWidth);
  numBeatsSlider.setName(String.format("Beats (%s)", numBeats));
  
  // where the array of beats starts drawing
  beatButtonsStartX = (width - (buttonWidth * numBeats)) / 2;
  
  // initialize the arrays
  if (tracks == null) {
    tracks = new boolean[4][numBeats];
  }
  else {
    // existing arrays need resizing, synch here for thread safety
    synchronized (numBeatsSlider) {
      for (int i = 0; i < tracks.length; i++) {
        boolean[] trackArr = tracks[i];
        boolean[] r = new boolean[numBeats];
        
        System.arraycopy(trackArr, 0, r, 0, Math.min(trackArr.length, r.length));
        tracks[i] = r;
      }
    }
  }
  
  // current beat must always be within the array
  if (currentBeat >= numBeats)
    currentBeat = 0;
    
  physBeats.setShownBeats(numBeats);    
}

void mouseReleased() {
  if (drawPhysicsToggle.mouseReleased()) {
    physBeats.setDrawDebug(this, drawPhysicsToggle.get());
  }
  
  if (visualiseSynth.mouseReleased()) {
    isVisualiseSynth = visualiseSynth.get();
  }
  
  if (synthSelRadio.mouseReleased()) {
    selectedSynth = synthSelRadio.get();
  }
  
  for (int i=0;i<notes.length;i++) {
    notes[selectedSynth][i]=(int) (Math.floor((seq[selectedSynth].get(i)/256)*12+transpose[selectedSynth])); 
  }
}

void mousePressed() {
  updateBpmIfGui(false);
  updateNumBeatsIfGui(false);
  dt[selectedSynth].mousePressed();
  dg[selectedSynth].mousePressed();
  a[selectedSynth].mousePressed();
  r[selectedSynth].mousePressed();
  f[selectedSynth].mousePressed();
  q[selectedSynth].mousePressed();
  o[selectedSynth].mousePressed();
  fa[selectedSynth].mousePressed();
  seq[selectedSynth].mousePressed();
    
  // synch here again, can't have drag update while anything happens here
  synchronized (numBeatsSlider) { // numbeats/width
    if (mouseX < beatButtonsStartX || mouseX > beatButtonsStartX + buttonWidth*numBeats) {
      return; // not in range, get out
    }
  
    int index = (int) Math.floor(((mouseX - beatButtonsStartX)*numBeats/(buttonWidth*numBeats)));   
    int track = (int) Math.floor((mouseY-500)*(12/(float)height));
  
    if (track >= 0 && track < tracks.length && index >= 0 && index < numBeats) {
      tracks[track][index] = !tracks[track][index];
    }
  }
}

/**
 * does as the example, but instead of advancing a beat every 4 frames, it calculates
 * it from the bpm and fps
 */
void advancePlayhead() {
  if (playhead > fpb) {
    playhead = 0;
  }
  
  if(playhead++ == 0){
    // move to the next beat ready for next time
    currentBeat++;
    if (currentBeat >= numBeats) {
      currentBeat = 0;      
    }

    // hit the cam for the beat on the physics beats
    physBeats.hitCam(currentBeat);

    for (int i = 0; i < tracks.length; i++) {
      if (tracks[i][currentBeat]) {
        samples[i].cue(0);
        samples[i].play();
      }
    }
  }
}

/**
 * called when fps or bpm changes
 */
void calcFpb() {
  float bps = bpm / 60f; // beats per second
  fpb = (int) (trueFps / bps); // frames per beat
}

/**
 * keep a bunch of stats on fps and adapt it as it goes
 */
void adjustFpsStats() {

  long now = System.nanoTime();  
  
  // first time in, setup default and time for next draw() and get out
  if (lastTest == 0L) {
    lastTest = now;
    trueFps = REQUEST_FPS; // roughly accurate on desktop, perhaps not on android
    calcFpb();
    return;
  }
  
  // how long since last
  long elapsedNanos = now - lastTest;
  lastTest = now;
  float fps = NANOS_PER_SECOND / elapsedNanos;
  fpsTests[fpsInd++] = fps;
  boolean isComplete = fpsInd == fpsTests.length; 
  if (isComplete) {
    fpsInd = 0;
    doneFullTest = true;
    
    // work out a true value for fps based on the data accummulated
    float tot = 0f;
    for (float test : fpsTests) {
      tot += test;
    }
    trueFps = tot / fpsTests.length;
    calcFpb(); // calc how many frames per beat
  }
  // use the latest number because don't yet have a full test
  else if (!doneFullTest) {
    trueFps = fps;
    calcFpb();
  }

}

