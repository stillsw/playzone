
// ReboundAnimation.java
// Thanks for code samples to 
// Andrew Davison, April 2005, ad@fivedots.coe.psu.ac.th

/* Try out animation and different settings for FPS
 * Initial go at a simple game to rebound a puck from the end wall 
   -------------
   
   The display includes two textfields for showing the current time
   and number of boxes. The average FPS/UPS values are drawn in
   the game's JPanel.

   Pausing/Resuming/Quitting are controlled via the frame's window
   listener methods.

   Uses active rendering to update the JPanel.

*/

package firstReboundGame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FirstReboundGame extends JFrame implements WindowListener
{
  public static int DEFAULT_FPS = 80;

  private ReboundGamePanel wp;   // where the animation is drawn
//  private JTextField jtfBox;   // displays no.of boxes used
//  private JTextField jtfTime;  // displays time spent in game
  private GameEventsManager gameEventsManager = GameEventsManager.getInstance();

  public FirstReboundGame(long period) { 
	super("Rebound and Score Game");
    makeGUI(period);
//    this.setUndecorated(true);
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    this.setIgnoreRepaint(true);
    addWindowListener(this);
    pack();
    setResizable(false);
    setVisible(true);
  }  // end of ReboundAnimation() constructor


  private void makeGUI(long period)
  {
    Container c = getContentPane();    // default BorderLayout used

    wp = new ReboundGamePanel(this, period);
    c.add(wp, "Center");

//    JPanel ctrls = new JPanel();   // a row of textfields
//    ctrls.setLayout( new BoxLayout(ctrls, BoxLayout.X_AXIS));
//
//    jtfBox = new JTextField("Striker strength: 0");
//    jtfBox.setEditable(false);
//    ctrls.add(jtfBox);
//
//    jtfTime = new JTextField("Time Spent: 0 secs");
//    jtfTime.setEditable(false);
//    ctrls.add(jtfTime);
//
//    c.add(ctrls, "South");
  }  // end of makeGUI()


//  public void setStrikerStrength(int no)
//  {  jtfBox.setText("Striker strength: " + no);  }
//
//  public void setTimeSpent(long t)
//  {  jtfTime.setText("Time Spent: " + t + " secs"); }
//  

  // ----------------- window listener methods -------------

  public void windowActivated(WindowEvent e)  {
	  this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_RESUME_GAME, false);
  }

  public void windowDeactivated(WindowEvent e) {
	  this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_PAUSE_GAME, false);
  }

  public void windowDeiconified(WindowEvent e)  {
	  this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_RESUME_GAME, false);
  }

  public void windowIconified(WindowEvent e)  {
	  this.gameEventsManager.setGameEventAndNotifyListeners(GameEventsManager.GAME_EVENT_TYPE_PAUSE_GAME, false);
  }
  
  public void windowClosing(WindowEvent e) {
	  this.gameEventsManager.requestCloseGame(ReboundGamePanel.popupColours);
  }
  
  public void windowClosed(WindowEvent e) {}
  public void windowOpened(WindowEvent e) {}

  // ----------------------------------------------------

  public static void main(String args[])
  { 
//	System.setProperty("sun.java2d.opengl", "true");
//  System.setProperty("sun.java2d.translaccel", "true");
//  System.setProperty("sun.java2d.ddforcevram", "true");
	  
	int fps = DEFAULT_FPS;
    if (args.length != 0)
      fps = Integer.parseInt(args[0]);

    long period = (long) 1000.0/fps;
    System.out.println("fps: " + fps + "; period: " + period + " ms");

    new FirstReboundGame(period*1000000L);    // ms --> nanosecs 
  }

} // end of ReboundAnimation class


