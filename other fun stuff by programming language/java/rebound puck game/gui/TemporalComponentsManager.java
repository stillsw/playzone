package firstReboundGame.gui;

import java.awt.Graphics2D;

import firstReboundGame.*;

/*
 * manage the display of temporaral components - those that will show for a short while
 */
public class TemporalComponentsManager
{
	private static final TemporalComponentsManager instance = new TemporalComponentsManager();
	
	private TemporalPopupBox[] popUps = new TemporalPopupBox[0];
	private boolean[] drawInds = new boolean[0];
	private ReboundGamePanel panel = ReboundGamePanel.getInstance();
	
	private TemporalComponentsManager() {
//		System.out.println("TemporalComponentsManager.<init>: loaded");
	}
	
	public static TemporalComponentsManager getInstance() { return instance; }
	
	public int getGameTickSecondsFromNow(double seconds) {
		return panel.gameTick + (int)(seconds * FirstReboundGame.DEFAULT_FPS);
	}
	
	public void addTemporalPopupBox(TemporalPopupBox popUpBox) {
		{
			TemporalPopupBox[] newPopUps = new TemporalPopupBox[this.popUps.length + 1];
			System.arraycopy(this.popUps, 0, newPopUps, 0, this.popUps.length);
			newPopUps[newPopUps.length - 1] = popUpBox;
			this.popUps = newPopUps;
		}
		// and the draw indicators array
		{
			boolean[] newDrawInds = new boolean[this.popUps.length];
			System.arraycopy(this.drawInds, 0, newDrawInds, 0, this.drawInds.length);
			newDrawInds[popUps.length - 1] = popUpBox.shouldDrawIt();
			this.drawInds = newDrawInds;
		}
	}
	
	/*
	 * Instead of de-registering anything, keep it (it could reset itself to draw again later)
	 * just make it draw or not as it determines itself
	 */
	public void setDrawing(TemporalPopupBox popUpBox) {
//		System.out.println("TemporalComponentsManager.setDrawing: got message from popUp to draw "+popUpBox.shouldDrawIt()+" "+popUpBox);
		for (int i = 0; i < this.popUps.length; i++) {
			if (popUpBox == this.popUps[i])
				drawInds[i] = popUpBox.shouldDrawIt();
		}
	}

	public void update(int gameTick) {
		for (int i = 0; i < this.popUps.length; i++) {
			this.popUps[i].update(gameTick);
		}
	}
	
	/*
	 * while the temporal component is set to be drawn, keep calling its draw method
	 */
	public void drawTemporalComponents(Graphics2D g2) {
//		panel.applyRenderingHints(g2, true);
		for (int i = 0; i < this.popUps.length; i++) {
			if (drawInds[i])
				popUps[i].drawGuiComponent(g2);
		}
//		panel.applyRenderingHints(g2, false);
	}
	
}
