package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.ModuleManager;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.HelpTipModel.HelpTipTargetPoint;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.Direction;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Dimension;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;

/**
 * A module that is basically a layout for the 2 data lists, beats and sounds, it takes up the whole
 * screen (minus top bar) and lays out the pieces itself.
 * @author tomas stubbs
 *
 */
public class BeatsAndSoundsModule extends Widget implements PauseableModule {

	private static BeatsAndSoundsModule instance;
	
	// flags used in the pauseable module lifecycle
//	private boolean startupComplete = false;
	private boolean pauseComplete = false;
	private boolean resumeComplete = false;
	private boolean closeComplete = false;

	private MutableRectangle layoutDimensions;
	private final BeatTypesList beatTypesList;
	private SlidingAnimatedPanel beatTypesListSlidingPanel;
	private SoundsList soundsList = new SoundsList();
	private SlidingAnimatedPanel soundsListSlidingPanel;
	protected MutableRectangle beatTypesLayout = new MutableRectangle(0f, 0f, 0f, 0f);
	protected MutableRectangle soundsLayout = new MutableRectangle(0f, 0f, 0f, 0f);
	private Button helpBtn;	
	private Dimension largeGap;
	
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	public BeatsAndSoundsModule() {
		this.beatTypesList = new BeatTypesList();
	}

	private void makePanels() {
		// make a beat types list and put it in a sliding panel
		beatTypesList.init();

		// start hidden, but set it to show
		beatTypesListSlidingPanel = new SlidingAnimatedPanel(beatTypesList, Direction.FROM_THE_LEFT, true); 
		beatTypesListSlidingPanel.setTheme("beatTypesListPanel");
		
		// set the position of both the sliding panel the list in fully open state is always 0,0 in its
		// panel, but in closed state will be out by its width or height
		// the sliding panel position is in relation to this, not to the layout dimensions
		beatTypesListSlidingPanel.setPosition(0, 0);
		
		add(beatTypesListSlidingPanel);

		// same with sounds list, except can't set position yet, it depends on the beat types list
		soundsList.init();

		// start hidden, but set it to show
		soundsListSlidingPanel = new SlidingAnimatedPanel(soundsList, Direction.FROM_THE_LEFT, true); 
		soundsListSlidingPanel.setTheme("soundsListPanel");
		
		add(soundsListSlidingPanel);
	}

	private void doTwlStartup() {
		setTheme("beatsAndSoundsModule");
		KeepYaBeat.root.add(this);
		setSizeByModuleManager();
		helpBtn = makeHelpButton();
		add(helpBtn);
	}
	
	@Override
	protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
		largeGap = themeInfo.getParameterValue("largeGap", true, Dimension.class, Dimension.ZERO);
	}

	public Button makeHelpButton() {
		Button helpBtn = new Button();
		helpBtn.setTheme("helpBtn");			
		helpBtn.setCanAcceptKeyboardFocus(false);
		helpBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						showHelp(null);
					}			
				});
		helpBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_BUTTON_TOOLTIP));
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		
		return helpBtn;
	}

	/**
	 * Show help for this page, called from the help button and also from isResumed() when complete
	 * if auto help has not been seen before
	 * @param autoHelpKey
	 */
	private void showHelp(String autoHelpKey) {
		ArrayList<HelpTipModel> helpTips = new ArrayList<HelpTipModel>();
		if (autoHelpKey != null) {
			helpTips.add(new HelpTipModel(autoHelpKey, true, TwlLocalisationKeys.HELP_BEAT_TYPES_SUMMARY_TEXT, beatTypesList.getTopboxTitleLabel(), HelpTipTargetPoint.Left));
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_SOUNDS_SUMMARY_TEXT, soundsList.getAddButton(), HelpTipTargetPoint.Top));
		}

		// next set
		helpTips.add(new HelpTipModel(null, true, TwlLocalisationKeys.HELP_BEAT_TYPES_CHANGES_TEXT, beatTypesList.scrollPane, HelpTipTargetPoint.Bottom));
		helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_SOUNDS_CHANGES_TEXT, soundsList.getTopboxTitleLabel(), HelpTipTargetPoint.Top));

		// next set
		helpTips.add(new HelpTipModel(null, true, TwlLocalisationKeys.HELP_BEAT_TYPES_FALLBACK_TEXT, beatTypesList.scrollPane, HelpTipTargetPoint.Bottom));

		// send the whole lot to the controller
		new HelpTipsController(UserBrowserProxy.Key.beatssounds
				, autoHelpKey != null ? TwlLocalisationKeys.HELP_FIRST_SEEN_BEATS_AND_SOUNDS_EXTRA_TEXT : null
				, (HelpTipModel[]) helpTips.toArray(new HelpTipModel[helpTips.size()]));
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

	@Override
	protected void layout() {
		setSizeByModuleManager();
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		helpBtn.setPosition(getInnerX() + getInnerWidth() - (largeGap.getX() + helpBtn.getWidth())
				, getInnerY() + largeGap.getY());
		
		if (setBeatTypesAndSoundsLayouts()) {
			beatTypesList.setSize((int)beatTypesLayout.getW(), (int)beatTypesLayout.getH());
			beatTypesListSlidingPanel.setPosition((int)beatTypesLayout.getX(), (int)beatTypesLayout.getY());
			soundsList.setSize((int)soundsLayout.getW(), (int)soundsLayout.getH());
			soundsListSlidingPanel.setPosition((int)soundsLayout.getX(), (int)soundsLayout.getY());
	//		beatTypesList.setSize(beatTypesList.getWidth(), (int)layoutDimensions.getH()); // own width, but height of module
	//		beatTypesListSlidingPanel.setSize(beatTypesList.getWidth(), beatTypesList.getHeight());
		}
		else {
			// not ready yet, so make sure it goes round again
			invalidateLayout();
		}
	}
	
	/**
	 * The two lists share the space, so determine the optimal sharing
	 */
	private boolean setBeatTypesAndSoundsLayouts() {
		
		// determine the width/height each list would be if no limits were placed
		// 0 returned means everything that's needed for the width calc isn't ready yet
		// abort here
		int beatsW = beatTypesList.getUnfetteredWidth();
		if (beatsW == 0) {
			return false;
		}
		int beatsH = beatTypesList.getUnfetteredHeight();
		int soundsW = soundsList.getUnfetteredWidth();
		int soundsH = soundsList.getUnfetteredHeight();
		
		// strategy A : side by side, but only if there's room for both
		boolean layoutSideBySide = false;
//		ModuleManager moduleManager = ModuleManager.getInstance();
//		if (beatsW + soundsW <= layoutDimensions.getW()) {
//			// compute the layout to set the height by the screen space and set the width for a scrollbar if needed
//			moduleManager.computeLayout(beatTypesLayout, beatsW, beatsH, Library.INT_NOT_CHANGED, Library.INT_NOT_CHANGED);
//			moduleManager.computeLayout(soundsLayout, soundsW, soundsH, Library.INT_NOT_CHANGED, Library.INT_NOT_CHANGED);
//			
//			// does the width still fit?
//			if (beatTypesLayout.getW() + soundsLayout.getW() <= layoutDimensions.getW()) {
//				// can simply place the sounds to the right of the beats
//				soundsLayout.setX(beatTypesLayout.getX()+beatTypesLayout.getW());
//				layoutSideBySide = true;
//			}
//		}
		
		// strategy B : not room next to each other, so stack beats on top of sounds
		// and divide the available space evenly 
		if (!layoutSideBySide) {
			// best to arrange it so this is always an even number
			int totalHeight = (int)layoutDimensions.getH();
			int oddCorrection = (totalHeight % 2.0 == 0 ? 0 : 1);
			
			// allocate half the height to each list
			int halfAvailableHeight = totalHeight / 2; // (deliberate truncation)
			int beatTypesHalf = halfAvailableHeight + oddCorrection;
			int soundsHalf = halfAvailableHeight;
			
			// combined heights not > than available height
			// since this is a medium res layout can assume the width fits
			if (beatsH + soundsH <= totalHeight) {
				beatTypesLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY(), beatsW, beatsH);
				soundsLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY() + beatsH, soundsW, soundsH);
			}
			else {
				// rule is :
				// if both modules want > half the space, they each get half and scrollbars
				// if one wants < half the space, it gets all it needs and the other gets what's left and a scrollbar
				if (beatTypesHalf < beatsH && soundsHalf < soundsH) { // 50:50
					beatsW += KeepYaBeat.vScrollbarWidth;
					soundsW += KeepYaBeat.vScrollbarWidth;
					beatTypesLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY(), beatsW, beatTypesHalf);
					soundsLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY() + beatTypesHalf, soundsW, soundsHalf);
				}
				else if (beatTypesHalf < beatsH) { // beats wants more
					beatsW += KeepYaBeat.vScrollbarWidth;
					beatsH = totalHeight - soundsH;
					beatTypesLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY(), beatsW, beatsH);
					soundsLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY() + beatsH, soundsW, soundsH);					
				}
				else if (soundsHalf < soundsH) { // sounds wants more
					soundsW += KeepYaBeat.vScrollbarWidth;
					soundsH = totalHeight - beatsH;
					beatTypesLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY(), beatsW, beatsH);
					soundsLayout.setXYWH(layoutDimensions.getX(), layoutDimensions.getY() + beatsH, soundsW, soundsH);					
				}
			}
		}
		
		return true;
	}

	/**
	 * ModuleManager uses this method to get the latest instance of this class since any previous
	 * reference it could have been closed
	 */
	//TODO stop doing it this way, just create an object if needed
	public static PauseableModule getCurrentInstance() {
		return instance;
	}

	public static PauseableModule getInstance() {
		if (instance == null) {
			instance = new BeatsAndSoundsModule();
		}
		return instance;
	}

	//------------------------- PauseableTopLevelModule methods

	@Override
	public void startNew(ModuleManager moduleManager) {
		doTwlStartup();
		makePanels();
		resume();
	}

	@Override
	public void finishAndClose() {
		closeComplete = false;
		pause();
	}
	
	@Override
	public void pause() {
		pauseComplete = false;
		beatTypesListSlidingPanel.hibernate();
		soundsListSlidingPanel.hibernate();
		if (helpBtn != null) {
			helpBtn.setVisible(false);
		}
	}

	@Override
	public void resume() {
		resumeComplete = false;
		
		// first time in the starting up hidden flag is set on the sliding panels, which is unset it their layout() methods
		// allow them to resume themselves there
		if (!beatTypesListSlidingPanel.isStartingUpHidden()) {
			beatTypesListSlidingPanel.resume();
		}

		if (!soundsListSlidingPanel.isStartingUpHidden()) {
			soundsListSlidingPanel.resume();
		}
		
		if (helpBtn != null) {
			helpBtn.setVisible(true);
		}
		
	}

	@Override
	public boolean isStarted() {
		boolean resumed = isResumed();
		return resumed;
	}

	@Override
	public boolean isPaused() {
		if (!pauseComplete) {
			if (beatTypesListSlidingPanel.isCurrentStateHidden()
				&& soundsListSlidingPanel.isCurrentStateHidden()) {
				pauseComplete = true;
			}
		}
		return pauseComplete;
	}

	@Override
	public boolean isResumed() {
		if (!resumeComplete) {
			if (beatTypesListSlidingPanel.isResumeComplete()
				&& soundsListSlidingPanel.isResumeComplete()) {
				resumeComplete = true;

				// show auto help if not shown before
				TwlPersistentStatesManager persistenceManager = 
						(TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();
				final String autoHelpKey = HelpTipsController.HelpPage.beatsAndSounds.name();
				if (!persistenceManager.isAutoHelpSeen(autoHelpKey)) {
					resourceManager.getPendingTasksScheduler().addTask(new PendingTask("wait for sliding panels to open") {
						@Override
						protected void updateComplete() {
							complete = beatTypesListSlidingPanel.isFullyOpen() 
									&& soundsListSlidingPanel.isFullyOpen();
							if (complete) {
								showHelp(autoHelpKey);
							}
						}
					});
				}
			}
		}
		return resumeComplete;
	}

	@Override
	public boolean isClosed() {
		closeComplete = isPaused();
		if (closeComplete) {
			instance = null;
			KeepYaBeat.root.removeChild(this);
		}
		return closeComplete;
	}

	@Override
	public String getName() {
		return resourceManager.getLocalisedString(TwlLocalisationKeys.MODULE_BEATS_SOUNDS);
	}

}


