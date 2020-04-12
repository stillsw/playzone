package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.ModuleManager;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.Direction;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.SlideStateCallback;
import com.stillwindsoftware.keepyabeat.model.EditRhythmXmlImpl;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.twlModel.RhythmsSearchModel;
import com.stillwindsoftware.keepyabeat.utils.RhythmEncoder.UnrecognizedFormatException;

import de.matthiasmann.twl.Widget;

/**
 * A module that is basically a layout for the player, it takes up the whole
 * screen (minus top bar) and lays out the pieces itself.
 * The main two items are the rhythms list and the rhythm player itself. The list is
 * optionally shown and is a sliding panel. When it is shown, the player makes room
 * for it and the screen is split. The other parts of the player are all placed
 * relative to the size left over.
 * @author tomas stubbs
 *
 */
public class PlayerModule extends Widget implements PauseableModule {

	private static PlayerModule instance;
	
	// flags used in the pauseable module lifecycle
	private boolean startupComplete = false;
	private boolean pauseComplete = false;
	private boolean resumeComplete = false;
	private boolean closeComplete = false;

	// data
	private PlayerState playerState = ((TwlPersistentStatesManager)TwlResourceManager.getInstance()
			.getPersistentStatesManager()).getPlayerState();

	private MutableRectangle layoutDimensions;
	private RhythmsList rhythmsList = new RhythmsList(this);
	private SlidingAnimatedPanel rhythmsListSlidingPanel;
	private PlayerGroup playerGroup;
	
	/**
	 * Called from rhythmsList right-click on a rhythm to open it, and from guiManager when
	 * something has requested it to be open (probably during undo processing)
	 * @param rhythm
	 */
	public void openRhythm(Rhythm rhythm) {
		if (rhythm != null && !playerGroup.getOpenRhythm().equals(rhythm)) {
			playerGroup.initModel(rhythm);
		}
	}
	
	/**
	 * Called by TwlGuiManager usually because undo processing needs rhythmsList open, if not yet started set a flag
	 * to make sure it opens, if already open but rhythmsList is closed set to show
	 */
	public void requestOpenRhythmsList() {
		if (!startupComplete) {
			playerState.setRhythmsListOpen(true);
		}
		else if (rhythmsListSlidingPanel.isCurrentStateHidden()) {
			rhythmsListSlidingPanel.setShow(true);
		}
	}
		
	public SlidingAnimatedPanel getRhythmsListSlidingPanel() {
		return rhythmsListSlidingPanel;
	}

	/**
	 * Called from the guiManager method of the same name to pass the question down to the rhythms list
	 * @param tag
	 * @return
	 */
	public boolean doesRhythmsSearchContainTag(Tag tag) {
		if (startupComplete) {
			RhythmsSearchModel searchModel = rhythmsList.getRhythmsSearchModel();
			return (searchModel != null && searchModel.getSearchTags().contains(tag)); 
		}
		else {
			return false;
		}
	}
	
	/**
	 * Called from guiManager method of same name to add tag back to rhythms list search during undo
	 * of delete to the tag
	 * @param tag
	 */
	public void addTagToRhythmsSearch(Tag tag) {
		rhythmsList.tagPicked(tag);
	}
	
	/**
	 * Called by TwlGuiManager usually because undo processing needs controls settings open
	 * playerGroup is listening to player state changes, so only need to change that value
	 */
	public void requestOpenSettingsControls() {
		playerState.setVolumesActive(true);
	}

	/**
	 * Let the playerGroup know it is losing focus (cancels listening) and start the rhythm editor.
	 * Called either by user pressing edit button on the current rhythm, or by right-click
	 * option in the rhythmsList. 
	 * @param rhythm
	 */
	public void invokeRhythmEditor(Rhythm rhythm) {
		try {
			// disable listening on player group
			playerGroup.giveUpFocus();
			
			// start editor, pass it callback to reattach focus to player group when it closes
			new TwlRhythmEditor(false
					, new EditRhythmXmlImpl(Function.getBlankContext(TwlResourceManager.getInstance()), rhythm)
					, new Runnable() {
							@Override
							public void run() {
								playerGroup.reattachFocus();
							}
						});
		} catch (UnrecognizedFormatException e) {
			TwlResourceManager.getInstance().log(LOG_TYPE.error, this, "PlayerModule.invokeRhythmEditor: program error in making EditRhythm");
			e.printStackTrace();
			TwlResourceManager.getInstance().getGuiManager().warnOnErrorMessage(
					CoreLocalisation.Key.UNEXPECTED_PROGRAM_ERROR, true, e);
		}
	}
	
	/**
	 * Read saved state and set the panels show/hide accordingly, called during makePanels()
	 */
	private void setPanelsFromSavedState() {
		// read from state
		boolean resumeListHidden = !playerState.isRhythmsListOpen();
		rhythmsListSlidingPanel.setResumeHidden(resumeListHidden);		
		if (!resumeListHidden) {
			rhythmsListSlidingPanel.setDisableAutoResume(true);
			playerGroup.disableAutoResume();
		}
	}
	
	private void makePanels() {
		// make a rhythms list and put it in a sliding panel
		rhythmsList.init();

		// start hidden, but set it to show
		rhythmsListSlidingPanel = new SlidingAnimatedPanel(rhythmsList, Direction.FROM_THE_LEFT, true); 
		rhythmsListSlidingPanel.setTheme("rhythmsListPanel");
		// add a slide button from top right edge
		rhythmsListSlidingPanel.addSlideButton("toggleShowRhythmListBtn", -8, 5);
		
		// need to know when it changes so can set the player state
		rhythmsListSlidingPanel.setSlideStateChangedCallback(new SlideStateCallback() {
				@Override
				public void slideStateChanged(boolean show) {
					playerState.setRhythmsListOpen(show);
					if (show) {
						// will want to show auto help the first time the rhythmslist opens
						playerGroup.showRhythmsListAutoHelp();
					}
				}
			});
		
		// set the position of both the sliding panel the list in fully open state is always 0,0 in its
		// panel, but in closed state will be out by its width or height
		// the sliding panel position is in relation to this, not to the layout dimensions
		rhythmsListSlidingPanel.setPosition(0, 0);
		
		// make a playerGroup which is the parent of all the other objects in the module
		playerGroup = new PlayerGroup(rhythmsList, rhythmsListSlidingPanel);
//		playerGroup.setPosition(0, 0);

		// add the two widgets in order, want rhythms list to draw over the rest, so add it second
		add(playerGroup);
		add(rhythmsListSlidingPanel);

		// add a callback to the rhythms list sliding panel to relayout the player group when it moves
		rhythmsListSlidingPanel.setSlideCallback(new Runnable() {
				@Override
				public void run() {
//					playerGroup.setRhythmsListSized();
					playerGroup.invalidateLayout();
				}
			});
		
		// read state for what to do with panels
		setPanelsFromSavedState();
	}

	private void doTwlStartup() {
		setTheme("playerModule");
		KeepYaBeat.root.add(this);
		setSizeByModuleManager();
	}

	/**
	 * Called during doTwlStartup and during layout
	 */
	private void setSizeByModuleManager() {
		// get the maximum possible layout from the module manager
		layoutDimensions = ModuleManager.getInstance().computeLayout(null, 
				KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight(), KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight());
		
		if ((int)layoutDimensions.getW() != getWidth() || (int)layoutDimensions.getH() != getHeight()) {
			setSize((int)layoutDimensions.getW(), (int)layoutDimensions.getH());
		}
		
		if ((int)layoutDimensions.getX() != getX() || (int)layoutDimensions.getY() != getY()) {
			setPosition((int)layoutDimensions.getX(), (int)layoutDimensions.getY());
		}
	}
	

	@Override
	public int getMinWidth() {
		return (int)layoutDimensions.getW();
	}

	@Override
	public int getMinHeight() {
		return (int)layoutDimensions.getH();
	}

	/**
	 * Called by rhythmsList itself in doLayout() to test for the need to recalc its computed size
	 * and from layout() here
	 * @return
	 */
	int getRhythmsListCalculatedWidth() {
		return Math.min(rhythmsList.getWidth(), (int) layoutDimensions.getW());
	}
	
	@Override
	protected void layout() {
		setSizeByModuleManager();

		// see if the rhythmsList is out of the picture and staying there
		boolean rhythmsListIsDeadHidden = rhythmsListSlidingPanel.isCurrentStateHidden() && !rhythmsListSlidingPanel.isTogglingNow();
		
//if (rhythmsListIsDeadHidden) {		
//System.err.println(String.format("PlayerMod.layout: rlsize=%s/%s panel=%s/%s exactlyHidden=%s rhythmsListIsCurrentlyHidden=%s"
//, rhythmsList.getWidth(), rhythmsList.getHeight(), rhythmsListSlidingPanel.getWidth(), rhythmsListSlidingPanel.getHeight()
//, rhythmsListSlidingPanel.getX()-rhythmsList.getWidth()==rhythmsList.getX()
//, rhythmsListIsDeadHidden
//)
//);
//}
		rhythmsList.adjustSize();
		rhythmsList.setSize(getRhythmsListCalculatedWidth(), 
				Math.min((int)layoutDimensions.getH(), (int) layoutDimensions.getH())); // own width, but height of module
		playerGroup.setRhythmsListSized();
		rhythmsListSlidingPanel.setSize(rhythmsList.getWidth(), rhythmsList.getHeight());
		
		// rhythmsList should be in completely hidden position when supposed to be hidden and not toggling
		// this is needed because change of theme/font will change size of the panel
		if (rhythmsListIsDeadHidden) {
			rhythmsListSlidingPanel.setAnimatedWidgetToHiddenPosition();
		}
		
		// player group is same size as the module
		playerGroup.setSize(getWidth(), getHeight());
		playerGroup.setPosition(getInnerX(), getInnerY());
	}

	/**
	 * ModuleManager uses this method to get the latest instance of this class since any previous
	 * reference it could have been closed
	 */
	public static PauseableModule getCurrentInstance() {
		return instance;
	}

	public static PauseableModule getInstance() {
		if (instance == null) {
			instance = new PlayerModule();
		}
		return instance;
	}

	//------------------------- PauseableTopLevelModule methods
	
	@Override
	public void startNew(ModuleManager moduleManager) {

		// simulate a startup if not the first time, because this module is never really closed
		if (startupComplete) {
			startupComplete = false;
			resumeComplete = false;
		}
		else {
			doTwlStartup();
			makePanels();
		}
		
		// wait one click to start resume()... giving layout a chance to happen so everything is sized properly
		TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(new PendingTask("playerModuleStartNewWaitAClickBeforeResume") {
				@Override
				protected void startTask() {
					resume();
				}				
			});
	}

	@Override
	public void finishAndClose() {
		closeComplete = false;
		pause();
	}

	@Override
	public void pause() {
		pauseComplete = false;

		if (rhythmsListSlidingPanel.isCurrentStateHidden()) {
			playerGroup.hibernate();
			rhythmsListSlidingPanel.setResumeHidden(true);
		}
		else {
			playerGroup.hibernate(new PendingTask("hibernateRhythmsList") {
				@Override
				protected void startTask() {
					rhythmsListSlidingPanel.hibernate();
				}
			});
		}
	}

	@Override
	public void resume() {
		resumeComplete = false;
		
		if (rhythmsListSlidingPanel.isResumeHidden()) {
			playerGroup.resume();
//			rhythmsListSlidingPanel.resume();
		}
		else {
			// don't just set rhythmslist to open, need the player group to wait for it,
			// so make a next task on the open and schedule it all here
			// disable auto resume first (only applies to initial startup)
//			playerGroup.enableAutoResume(false);
//System.out.println("PlayerModule.resume: calling list.resume, rhythmsListSized="+playerGroup.isRhythmsListSized());	
			PendingTask waitForList = new PendingTask("wait for rhythmslist layout") {
			
				@Override
				protected void updateComplete() {
					complete = playerGroup.isRhythmsListSized();
//					System.out.println("PlayerModule.resume.task waiting for rhythmslist to size complete="+complete);
				}
				
			};
			
			// put the resume on the next task, with a next task on that one to open player group
			waitForList.appendNextTask(new PendingTask("resume rhythmsList") {
				@Override
				protected void startTask() {		
					rhythmsListSlidingPanel.resume(new PendingTask("openPlayerGroupTask") {
						
						@Override
						protected void startTask() {
//			System.out.println("PlayerModule.resume.starttask: calling pg.resume, rhythmsListSized="+playerGroup.isRhythmsListSized());						
							playerGroup.resume();
						}
					});
				}	
			});
			TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(waitForList);
		}
		
//		// ensure the module bar is updated on opening, in case of any rhythm changes in the meantime
//		if (rhythmModuleBarContentProvider == null)
//			getModuleBarContentProvider();
//		rhythmModuleBarContentProvider.updateTexts();
	}

	@Override
	public boolean isStarted() {
		boolean resumed = isResumed();
		return resumed;
	}

	@Override
	public boolean isPaused() {
		if (!pauseComplete) {
			if (rhythmsListSlidingPanel.isCurrentStateHidden()
					&& playerGroup.isPaused()) {//&& volumesPanel.isHidden())
				pauseComplete = true;
			}
		}
		return pauseComplete;
	}

	@Override
	public boolean isResumed() {
		if (!resumeComplete) {
			if (rhythmsListSlidingPanel.isResumeComplete()
					&& playerGroup.isResumed()) {
				resumeComplete = true;
				startupComplete = true;
			}
//			else {
//System.err.println("PlayerModule.isResumed: listResumeCommplete="+rhythmsListSlidingPanel.isResumeComplete()+" playerGroupResumed="+playerGroup.isResumed()
//		+" rhythmsListSlidingPanel is hidden="+rhythmsListSlidingPanel.isHidden()+" isvisible="+rhythmsListSlidingPanel.isVisible());
//			}
		}
		
		return resumeComplete;
	}

	@Override
	public boolean isClosed() {
		closeComplete = isPaused();
		return closeComplete;
	}

	@Override
	public String getName() {
		return TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.MODULE_PLAYER);
	}

}


