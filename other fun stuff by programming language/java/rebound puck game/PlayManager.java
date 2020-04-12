package firstReboundGame;

import java.util.ArrayList;

public class PlayManager
{
	public static final int SCHEME_REPLAY_WHEN_STOP = 0;
	public static final int SCHEME_REPLAY_WHEN_ALL_PIECES_STOP = 1;
	
	protected static ReboundGamePanel panel;
	private int scheme;
	private MoveableGamePiece piecesMoving[];
	
	public PlayManager(int scheme) {
		this.scheme = scheme;		
		PlayManager.panel = ReboundGamePanel.getInstance();
	}
	
	public void registerPieces(MoveableGamePiece[] pieces) {
		this.piecesMoving = pieces;
	}
	
	public void resetAllPieces() {
		for (int i = 0; i < piecesMoving.length; i++) {
			if (piecesMoving[i].isWaitingToRestart()) 
				piecesMoving[i].isWaitingToRestart = false;

			piecesMoving[i].reset();
			piecesMoving[i].resetToStart();
		}
	}
	
	public void waitToRestart(MoveableGamePiece piece, int gameTick) {
		
		piece.isWaitingToRestart = true;
		piece.waitingSince = gameTick;
		piece.reset();
	}
	
	public boolean canPlayPiece(MoveableGamePiece piece, int gameTick) {

		if (!piece.isWaitingToRestart())
			return true;
		
		if (scheme == SCHEME_REPLAY_WHEN_STOP) { // means just start asap
			if (piece.waitingSince + FirstReboundGame.DEFAULT_FPS * .6 <= gameTick) { // 6/10s second
				piece.isWaitingToRestart = false;
				piece.resetToStart();
				return true;
			}
			else
				return false;
		}
		else { // have to wait for all pieces to be ready to play again
			boolean ok = true;
			long latestGameTick = 0;
			for (int i = 0; i < piecesMoving.length; i++) {
				// any piece either not waiting or still moving means don't restart any yet
				if (!(piecesMoving[i].isWaitingToRestart || !piecesMoving[i].isMoving()))
					ok = false;
				
				// collect the latest tick to wait for in case ready to restart
				if (piecesMoving[i].isWaitingToRestart() && piecesMoving[i].waitingSince > latestGameTick)
					latestGameTick = piecesMoving[i].waitingSince;
			}
			if (!ok)
				return false;

			// latest tick is already well past, so set all pieces can move
			if (latestGameTick + 80 * .8 <= gameTick) { // wait a little bit longer here, so can see all pieces stopped before they restart
				for (int i = 0; i < piecesMoving.length; i++) {
					if (piecesMoving[i].isWaitingToRestart()) {
						piecesMoving[i].isWaitingToRestart = false;
						piecesMoving[i].resetToStart();
						// redraw everything
						PlayManager.panel.setDrawEverthing();
						

					}
				}
				return true;
			}
			else // not ready, have to wait for tick	
				return false;
		}
		
	}

	/*
	 * called from scoring managers to test if a play is finished
	 */
	public boolean allPiecesStopped() {
		for (int i = 0; i < piecesMoving.length; i++) {
			// any piece either not waiting or still moving means don't restart any yet
			if (!(piecesMoving[i].isWaitingToRestart || !piecesMoving[i].isMoving()))
				return false;
		}
		return true;
	}
	
	public boolean canDrawPiece(MoveableGamePiece piece) {

		if (piece.isStatic || !piece.isWaitingToRestart())
			return true;
		
		if (scheme == PlayManager.SCHEME_REPLAY_WHEN_ALL_PIECES_STOP) { 
			return (ReboundGamePanel.SCREEN_AREA.contains(piece.getCurrPosn().getX(), piece.getCurrPosn().getY()));
		}
		else
			return false;
		
	}
}  // end of class

