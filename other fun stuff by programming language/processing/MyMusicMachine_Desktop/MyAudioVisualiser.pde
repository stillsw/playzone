import java.awt.Rectangle;


public class MyAudioVisualiser {

  private final static float THRESHOLD = .2f;
  private final static int elements = 36;

  private int drawingAreaHeight;
  private float lowThreshold, lowValue, highValue;

  public MyAudioVisualiser(int size) {
    drawingAreaHeight = size;
  }

  public void drawVisuals(int tick, float power) {
    
    calcLowThreshold(tick, power);

    // draw every few turns, and only when it's above the power threshold
    // this makes it a bit more stacatto
//    if (tick % 4 == 0) {// && power > lowThreshold) {     
      drawSineVisuals(tick, power);
//    }
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

  void drawSineVisuals(int tick, float power) {
    ellipseMode(CENTER);
    pushMatrix();

    for (int i = 0; i <= elements; i++) {
      float hue = 255/elements*i; 
      stroke(hue, 255, 255, 40);
      fill(hue, 255, 255, 12);
      
      rotate(i*power*20);
      translate(sin(i)*(1.2f/power), 0);

      float size = drawingAreaHeight / elements * i;
      ellipse(0, 0, size, size);
    }
    popMatrix();
  }
boolean printOnce = true;
}
