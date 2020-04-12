package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.Direction;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.SlideStateCallback;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;
import com.stillwindsoftware.keepyabeat.utils.PixelUtils;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Widget;

/**
 * Superclass for PlayerGroup and RhythmEditor which both essentially play rhythms
 * and have many similar structures
 * @author tomas stubbs
 *
 */
public abstract class AbstractRhythmPlayer extends Widget implements LibraryListener {

	protected static final int MIN_RHYTHM_HEIGHT = 350;

	protected DialogLayout topWidgetsLayout;
	protected Button toggleSettingsBtn;
	protected PlayerRhythmBeatTypesList playRhythmVolumeControls;
	protected PlayControls playControls;
	protected SlidingAnimatedPanel topWidgetsSlidingPanel;
	protected SlidingAnimatedPanel canvasSlidingPanel;
	protected SlidingAnimatedPanel settingsSlidingPanel;
	protected SlidingAnimatedPanel playControlsSlidingPanel;

	// draughting 
	protected PlayerRhythmCanvas playerRhythmCanvas;
	protected PlayedRhythmDraughter rhythmDraughter;

	// data
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	protected final PlayerState playerState;
	protected final GuiManager guiManager;

	public abstract void initModel(Rhythm rhythm);
	protected abstract void makeTopWidgetsLayout();
	protected abstract void sizeAndPlaceTopWidgets();
	protected abstract void initDraughting();
	
	// flag for when the play controls have reattached from gui manager
	// and may need to re-align them on the screen
	protected boolean adjustPlayControlsPosition = false;
	protected boolean playControlsIsAutoAdjusting = false;
	
	public AbstractRhythmPlayer() {
		playerState = resourceManager.getPersistentStatesManager().getPlayerState();
		guiManager = resourceManager.getGuiManager();
	}
	/**
	 * The effective position to layout from for PlayControls and other widgets is the right-most
	 * point of rhythmsList in the case PlayerGroup or 0 for RhythmEditor. PlayerGroup must override
	 * to return the correct value.
	 * @return
	 */
	protected int getEffectiveXForControls() {
		return 0;
	}
	
	/**
	 * width has to respond to what's available when the rhythmsList slides in/out
	 */
	protected final void sizeAndPlacePlayControls() {
		// only layout if this is the parent (play controls can shift parents between player and editor)
		if (playControlsSlidingPanel.getParent() != this) {
			return;
		}
		
		// placement is at the bottom, but in the middle of the available width
		final int availableWidth = getInnerWidth() - (getEffectiveXForControls() - getX());
		playControls.adjustSize();
		playControlsSlidingPanel.adjustSize();
		
		// when reattaching play controls, the x-pos may shift, check for that and
		// move it into place with a pending task if needed
		final int desiredX = (getEffectiveXForControls() + availableWidth / 2 - playControlsSlidingPanel.getWidth() / 2);
		final int desiredY = getInnerY() + getInnerHeight() - playControlsSlidingPanel.getHeight();
		
		// first time after closing rhythm editor it will start a task to position play controls
		// but after another layout() it will drop through and the task will start and have nothing to do
		// so only do something here if the flag that is unset when adjusting completes
		if (!playControlsIsAutoAdjusting && 
				(playControls.getX() != desiredX || playControls.getY() != desiredY)) {
			
			if (adjustPlayControlsPosition) { // flag set in claimPlayControls()
				adjustPlayControlsPosition = false;
				playControlsIsAutoAdjusting = true;
				
				// because the Y pos may subtly shift between rhythm player and editor 
				// (editor is a whole screen dialog), place Y pos correctly first
				// this way the panel can slide gracefully along the X axis only
				playControlsSlidingPanel.setPosition(playControlsSlidingPanel.getX(), desiredY);
				playControls.setPosition(playControls.getX(), desiredY);				
				
				// shift it into position
				PendingTask shiftPlayControlsTask = new PendingTask("shiftPlayControls") {
					float speedX;
					float speedY;

					@Override
					protected void startTask() {
						// set up the movement speed parameters
						speedX = PixelUtils.getStepDistance(guiManager, desiredX - playControls.getX(), SlidingAnimatedPanel.DEFAULT_ELAPSED_SECONDS_TO_OPEN);
						speedY = PixelUtils.getStepDistance(guiManager, desiredY - playControls.getY(), SlidingAnimatedPanel.DEFAULT_ELAPSED_SECONDS_TO_OPEN);
					}

					@Override
					protected void updateComplete() {
						// the target values could have shifted because of Display resizes
						int updDesiredX = (getEffectiveXForControls() + availableWidth / 2 - playControlsSlidingPanel.getWidth() / 2);
						int updDesiredY = getInnerY() + getInnerHeight() - playControlsSlidingPanel.getHeight();

						// get the new values depending on direction setup
						float newDrawnX = PixelUtils.animateToTargetBySpeed(updDesiredX, playControls.getX(), speedX);
						float newDrawnY = PixelUtils.animateToTargetBySpeed(updDesiredY, playControls.getY(), speedY);
						
						// set both sliding panel and play controls positions
						playControlsSlidingPanel.setPosition((int)newDrawnX, (int)newDrawnY);
						playControls.setPosition((int)newDrawnX, (int)newDrawnY);
						
						complete = playControls.getX() == updDesiredX && playControls.getY() == updDesiredY;
						if (complete) {
							playControlsIsAutoAdjusting = false;
						}
					}
					
				};
				
				// schedule the task
				resourceManager.getPendingTasksScheduler().addTask(shiftPlayControlsTask);
			}
			else {
				// just set it correctly (if it's starting up it will slide up to spot)
				playControlsSlidingPanel.setPosition(desiredX, desiredY);
			}
		}
	}

	/**
	 * Called from sizeAndPlaceSettingsList(), RhythmEditor lays out a top bar which will want to protect
	 * from overlaying, so will override this
	 * @return
	 */
	protected int getSettingsListUpperMostY() {
		return getInnerY();
	}
	
	/**
	 * Volumes settings panel on the right above the play canvas, show a vertical scrollbar when needed (adjusts width)
	 */
	protected void sizeAndPlaceSettingsList() {
		int settingsHeight = playRhythmVolumeControls.getMinHeight();
		int settingsWidth = playRhythmVolumeControls.getMinWidth();
		if (settingsHeight > canvasSlidingPanel.getY() - getSettingsListUpperMostY()) { // not enough room for whole thing
			settingsWidth += KeepYaBeat.vScrollbarWidth;
			settingsHeight = canvasSlidingPanel.getY() - getSettingsListUpperMostY();
		}

		// once placed the settings stay as they are unless user toggles show/hide, which if resized will
		// be the wrong position, before changing anything, check the need to reposition the settings panel
		// by testing for hidden or not 
		boolean settingsHiddenAndResized = false;
		boolean settingsOpenAndResized = false;
		
		if (playRhythmVolumeControls.getWidth() != settingsWidth || playRhythmVolumeControls.getHeight() != settingsHeight) {
			// check to see if settings are hidden
			settingsHiddenAndResized = settingsSlidingPanel.isCurrentStateHidden();
			settingsOpenAndResized = !settingsHiddenAndResized;
			playRhythmVolumeControls.setSize(Math.max(settingsWidth, 0), Math.max(settingsHeight, 0));
		}

		// move the sliding panel to the correct place
		if (settingsSlidingPanel.getY() != canvasSlidingPanel.getY() - settingsHeight
				|| settingsSlidingPanel.getX() != getInnerX() + getInnerWidth() - settingsWidth) {
			settingsSlidingPanel.setPosition(getInnerX() + getInnerWidth() - settingsWidth
					, canvasSlidingPanel.getY() - settingsHeight);
			// added this adjust for the rhythm editor, which otherwise startsup with default size 
			// (20x20)
			settingsSlidingPanel.adjustSize();
		}
		
		// in case settingsHiddenAndResized hasn't detected that the widget isn't positioned correctly
		// because it was already sized correctly but the position changed because it was hidden
		// also test that it is exactly hidden if supposed to be (won't be true when it's sliding)
		if (!settingsHiddenAndResized 
				&& settingsSlidingPanel.isCurrentStateHidden() 
				&& !settingsSlidingPanel.isTogglingNow()
				&& !settingsSlidingPanel.isHidden()) { // isHidden() does most work, so leave for last condition
			
			settingsHiddenAndResized = true;
		}
		
		// put the widget to the right place according to its hidden state
		if (settingsHiddenAndResized) {
			settingsSlidingPanel.setAnimatedWidgetToHiddenPosition();
		}
		else if (settingsOpenAndResized) {
			settingsSlidingPanel.setAnimatedWidgetToOpenPosition();
		}
	}


	/**
	 * canvas size is the total width available, but the height is subject to constraints
	 * regardless of the size of the window (which could be re-sized to silly dimensions)
	 */
	protected void sizeAndPlaceCanvas() {
		playerRhythmCanvas.setSize(getInnerWidth(), Math.max(getInnerHeight() / 2, MIN_RHYTHM_HEIGHT));
		canvasSlidingPanel.setPosition(getInnerX(), getInnerY() + getInnerHeight() - playerRhythmCanvas.getHeight());
	}
	
	protected Button makeSettingsToggleButton() {
		Button toggleSettingsBtn = new Button();
		toggleSettingsBtn.setTheme("settingsBtn");
		toggleSettingsBtn.setCanAcceptKeyboardFocus(false);
		toggleSettingsBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						settingsSlidingPanel.setShow(settingsSlidingPanel.isCurrentStateHidden());
					}			
				});
		toggleSettingsBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.TOGGLE_SETTINGS));
		toggleSettingsBtn.setSize(toggleSettingsBtn.getMaxWidth(), toggleSettingsBtn.getMaxHeight());

		return toggleSettingsBtn;
	}

	protected void makeSettingsLayout() {
		settingsSlidingPanel = new SlidingAnimatedPanel(playRhythmVolumeControls, Direction.FROM_THE_RIGHT, true);
		settingsSlidingPanel.setTheme("settingsPanel");
		add(settingsSlidingPanel);
		
		// auto resume should not happen if player saved state indicates hidden
		if (!playerState.isVolumesActive()) {
			settingsSlidingPanel.setDisableAutoResume(true);
			settingsSlidingPanel.setResumeHidden(true);
		}

		// add a slide button 5 pixels from top left edge
		settingsSlidingPanel.addSlideButton("toggleShowSettingsBtn", 5, 5);
		
		// add a callback to change state variable
		settingsSlidingPanel.setSlideStateChangedCallback(new SlideStateCallback() {
			@Override
			public void slideStateChanged(boolean show) {
				playerState.setVolumesActive(show);
			}		
		});
		
		// called on every movement, once in motion would expect the table to completely laid out
		// so check for error
		settingsSlidingPanel.setSlideCallback(new Runnable() {
			private boolean resized = false;
			@Override
			public void run() {
				// should be ready to go, but if the bug that causes not all widgets to have width comes up it won't be good
				// so make a task to update the model which seems to be the only way to cure it
				// NOTE THIS IS A NASTY HACK
				if (!playRhythmVolumeControls.getTable().areAllCellWidgetRowsSized()) {
					resourceManager.log(LOG_TYPE.warning, this, 
							"AbstractRhythmPlayer.makeSettingsLayout.setSlideCallback: widgets not ready");
					
					// make sure dimensions are re-computed
					playRhythmVolumeControls.resetDimensionsComputed();

					if (!resized) { // only do this once
						PendingTask pt = new PendingTask("cause settings layout resize") {
							private long startTime;
							@Override
							protected void startTask() {
								startTime = System.currentTimeMillis();
							}
	
							@Override
							protected void updateComplete() {
								// wait 1/10 second it's invisible at that timing
								complete = System.currentTimeMillis() - startTime > 100;
								if (complete) {
									resourceManager.log(LOG_TYPE.info, this, 
											"AbstractRhythmPlayer.makeSettingsLayout.setSlideCallback: invalidating table layout because widgets not ready");
									// relayout by setting model updated
									playRhythmVolumeControls.modelUpdated();
								}
							}
							
						};
						
						resourceManager.getPendingTasksScheduler().addTask(pt);
						
						resized = true;
					}
//					table.invalidateLayout();
//					invalidateLayout();
//					modelUpdated();
				}
			}			
		});
	}

	/**
	 * Attempt to get the play controls panel from the gui manager where it is kept for sharing,
	 * including setting the draughter against the beat tracker
	 */
	protected final void claimPlayControls() {
		playControls = ((TwlGuiManager)resourceManager.getGuiManager()).getPlayControls();
		
		// found play controls, it's possible this is already the parent though
		if (playControls != null) {
			playControlsSlidingPanel = SlidingAnimatedPanel.getSlidingAnimatedPanelParent(playControls);
			
			// different parent remove the panel from its existing parent and assign here instead
			if (playControlsSlidingPanel != null && !playControlsSlidingPanel.getParent().equals(this)) {
				
				// get the x/y coords from previous parent, immediately set it to that so can slide to proper place from there
				playControlsSlidingPanel.getParent().removeChild(playControlsSlidingPanel);

				// may need to adjust position (see sizeAndPlacePlayControls())
				adjustPlayControlsPosition = true;
				
				add(playControlsSlidingPanel);
				
				// change the model (will trickle through to beat tracker if there is one)
				playControls.initModel(rhythmDraughter);
			}
			else if (playControlsSlidingPanel == null) {
				resourceManager.log(LOG_TYPE.error, this, "AbstractRhythmPlayer.claimPlayControls: got play controls from gui manager but lacks parent sliding panel");
			}
		}
	}
	
	/**
	 * Put the play controls into a sliding panel
	 */
	protected void makePlayControlsLayout() {
		// first look for it on gui manager, re-claim or create
		claimPlayControls();
		
		if (playControls == null) {
			playControls = new PlayControls(rhythmDraughter);
		
			// put it on the gui manager so beatTracker can feedback to it
			((TwlGuiManager)resourceManager.getGuiManager()).setPlayControls(playControls);

			playControlsSlidingPanel = new SlidingAnimatedPanel(playControls, Direction.UPWARDS, true);
			playControlsSlidingPanel.setTheme("playControlsSlidingPanel");
			add(playControlsSlidingPanel);
		}
	}

	/**
	 * Put the canvas into a sliding panel
	 */
	protected void makeDraughtingLayout() {
		canvasSlidingPanel = new SlidingAnimatedPanel(playerRhythmCanvas, Direction.UPWARDS, true);
		canvasSlidingPanel.setTheme("canvasPanel");
		add(canvasSlidingPanel);
	}


}
