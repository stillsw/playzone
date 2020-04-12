package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.gui.HelpTipModel.HelpTipTargetPoint;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.Direction;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmSetNameConfirmation;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.transactions.Function;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.DialogLayout.Group;
import de.matthiasmann.twl.TableSelectionManager;
import de.matthiasmann.twl.model.TableSelectionModel;

/**
 * sub level for playerModule, contains the playerPanel (for rhythms)
 * and all the buttons etc to do with playing
 * @author tomas stubbs
 */
public class PlayerGroup extends AbstractRhythmPlayer {

	private Button newRhythmBtn;
	private Button saveRhythmBtn;
	private Button editRhythmBtn;
	private Button helpBtn;
	private Button shareBtn;
	private Button toggleListBtn;
	private RhythmsList rhythmsList;
	private SlidingAnimatedPanel rhythmsListSlidingPanel;

	// life cycle flags
	private boolean pauseComplete;
	private boolean resumeComplete; 
	private boolean isRhythmsListSized = false;

	protected Rhythms rhythms = resourceManager.getLibrary().getRhythms();
	protected BeatTypes beatTypes = resourceManager.getLibrary().getBeatTypes();
	protected Rhythm rhythm;

	/**
	 * Construct a PlayerGroup, needs a reference to the rhythmsListPanel because
	 * it has to create a button to toggle opening it
	 * @param rhythmsList 
	 * @param rhythmsListPanel
	 */
	PlayerGroup(RhythmsList rhythmsList, final SlidingAnimatedPanel rhythmsListSlidingPanel) {
		setTheme("playerGroup");

		// keep reference to the list so can lay everything out with reference to it
		this.rhythmsList = rhythmsList;
		this.rhythmsListSlidingPanel = rhythmsListSlidingPanel;
		
		makeTopWidgetsLayout();
		// initialise rhythm from state, passes either a valid rhythm or null to initModel()
		initModel((Rhythm) rhythms.lookup(playerState.getRhythmKey()));
		initDraughting();
		makeDraughtingLayout();
		makeSettingsLayout();
		makePlayControlsLayout();
	}

	public Rhythm getOpenRhythm() {
		return rhythm;
	}

	private void makeRhythmListToggleButton() {
		toggleListBtn = new Button();
		toggleListBtn.setTheme("searchListBtn");
		toggleListBtn.setCanAcceptKeyboardFocus(false);
		toggleListBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						rhythmsListSlidingPanel.setShow(rhythmsListSlidingPanel.isCurrentStateHidden());
					}			
				});
		toggleListBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.TOGGLE_RHYTHMS_LIST));
		toggleListBtn.setSize(toggleListBtn.getMaxWidth(), toggleListBtn.getMaxHeight());
	}
	
	private void makeSaveRhythmButton() {
		saveRhythmBtn = new Button();
		saveRhythmBtn.setTheme("saveBtn");
		saveRhythmBtn.setCanAcceptKeyboardFocus(false);
		saveRhythmBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						new RhythmSetNameConfirmation(rhythm, PlayerGroup.this).saveChangesIfConfirmed();
					}			
				});
		saveRhythmBtn.setSize(saveRhythmBtn.getMaxWidth(), saveRhythmBtn.getMaxHeight());
	}
	
	private void makeShareButton() {
		shareBtn = new Button();
		shareBtn.setTheme("shareBtn");
		shareBtn.setCanAcceptKeyboardFocus(false);
		shareBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.SHARE_RHYTHMS_TOOLTIP));
		shareBtn.setSize(shareBtn.getMaxWidth(), shareBtn.getMaxHeight());

		// must be disabled at beginning, nothing selected in the rhythms list yet
		shareBtn.setEnabled(false);
		TableSelectionManager selectionManager = rhythmsList.getTable().getSelectionManager();
		final TableSelectionModel selectionModel = (selectionManager == null ? null : selectionManager.getSelectionModel());
		
		// enable the button depending on having selections
		if (selectionModel != null) {
			selectionModel.addSelectionChangeListener(new Runnable() {
					@Override
					public void run() {
						shareBtn.setEnabled(selectionModel.hasSelection());
					}
				});
			
			// action sharing
			shareBtn.addCallback(new Runnable() {
				@Override
				public void run() {
					new ExportSelectedRhythmsDialog(selectionModel, rhythmsList);
				}			
			});
		}
		else {
			resourceManager.log(LOG_TYPE.error, this, "PlayerGroup.makeShareButton: rhythmsList returns no selectionModel");
		}
	}

	private void makeEditRhythmButton() {
		editRhythmBtn = new Button();
		editRhythmBtn.setTheme("editBtn");
		editRhythmBtn.setCanAcceptKeyboardFocus(false);
		editRhythmBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						PlayerModule parent = (PlayerModule) getParent();
						parent.invokeRhythmEditor(rhythm);
					}			
				});
		editRhythmBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_TOOLTIP_EDIT_RHYTHM));
		editRhythmBtn.setSize(editRhythmBtn.getMaxWidth(), editRhythmBtn.getMaxHeight());
	}
	
	private void makeNewRhythmButton() {
		newRhythmBtn = new Button();
		newRhythmBtn.setTheme("newRhythmBtn");
		newRhythmBtn.setCanAcceptKeyboardFocus(false);
		newRhythmBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						initModel(rhythms.makeNewRhythm(Function.getBlankContext(resourceManager)));
					}			
				});
		newRhythmBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.NEW_RHYTHM));
		newRhythmBtn.setSize(newRhythmBtn.getMaxWidth(), newRhythmBtn.getMaxHeight());
	}
	
	/**
	 * Update the volume controls and other widgets with any changes
	 * @param fromUpdate true when the listener has fired and a change is being propagated
	 * false when called from initModel() which means a new rhythm is loading
	 */
	public void updateWidgetsIfNeeded(boolean fromUpdate) {
		if (fromUpdate) {
			rhythmDraughter.rhythmUpdated();
			playRhythmVolumeControls.modelUpdated();
			playControls.itemChanged();
		}
		
		boolean isNew = rhythms.lookup(rhythm.getKey()) == null;
		
		// button should be disabled when the rhythm is already new, 
		// (which it is if it's not in the rhythm's list)
		newRhythmBtn.setEnabled(!isNew);
		saveRhythmBtn.setTooltipContent(
				resourceManager.getLocalisedString((isNew ? TwlLocalisationKeys.SAVE_NEW_RHYTHM : TwlLocalisationKeys.SAVE_RHYTHM_LABEL)));

		KeepYaBeat.root.setModuleExtra(rhythm.getName());

		// read settings state 
		boolean settingsActive = playerState.isVolumesActive();
		if (settingsSlidingPanel != null && settingsSlidingPanel.isCurrentStateHidden() != !settingsActive) {
			settingsSlidingPanel.setShow(settingsActive);
		}

	}

	/**
	 * Called by PlayerModule in the callback for rhythmsList state changing to open,
	 * detect if auto-help has been seen on the rhythmsList and if not, show it
	 * but only if th auto-help for the whole screen (player group) has been shown already... otherwise
	 * it's going to be included in that showing anyway
	 */
	public void showRhythmsListAutoHelp() {
		TwlPersistentStatesManager persistenceManager = (TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();
		final String autoHelpKey = HelpTipsController.HelpPage.rhythmsList.name();
		if (!persistenceManager.isAutoHelpSeen(autoHelpKey) 
				&& persistenceManager.isAutoHelpSeen(HelpTipsController.HelpPage.player.name())) {
			ArrayList<HelpTipModel> helpTips = new ArrayList<HelpTipModel>();
			addRhythmsListAutoHelp(helpTips);
			
			// send the whole lot to the controller
			new HelpTipsController(UserBrowserProxy.Key.player, null
					, (HelpTipModel[]) helpTips.toArray(new HelpTipModel[helpTips.size()]));

		}		
	}
	
	/**
	 * Called from showRhythmsListAutoHelp() and 
	 * showHelp() - either itself called by pressing help button, or from autohelp on opening
	 * @param helpTips
	 */
	private void addRhythmsListAutoHelp(ArrayList<HelpTipModel> helpTips) {
		// rhythms list is open, so show more detailed help first, add autohelp key to it so it doesn't show automatically
		// next time rhythms list opens
		helpTips.add(new HelpTipModel(HelpTipsController.HelpPage.rhythmsList.name(), true, TwlLocalisationKeys.HELP_SHARE_BUTTON_TEXT, shareBtn, HelpTipTargetPoint.TopLeft));
		helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_SEARCH_RHYTHMS_TEXT, rhythmsList.topBox, HelpTipTargetPoint.Top));
		helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_IMPORT_RHYTHMS_FROM_MENU_TEXT, KeepYaBeat.root.getMoreButton(), HelpTipTargetPoint.TopRight));
	}

	/**
	 * Show help for this page, called from the help button and also from isResumed() when complete
	 * if auto help has not been seen before
	 * @param autoHelpKey
	 */
	private void showHelp(String autoHelpKey) {
		ArrayList<HelpTipModel> helpTips = new ArrayList<HelpTipModel>();
		if (autoHelpKey != null) {
			helpTips.add(new HelpTipModel(autoHelpKey, true, TwlLocalisationKeys.HELP_HELP_BUTTONS_TEXT, helpBtn, HelpTipTargetPoint.Top));
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_PLAY_CONTROLS_TEXT, playControls.getHelpTarget(), HelpTipTargetPoint.Bottom));
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_UNDO_BUTTON_TEXT, KeepYaBeat.root.getUndoButton(), 
					rhythmsListSlidingPanel.isCurrentStateHidden() 
						? HelpTipTargetPoint.Top : HelpTipTargetPoint.TopRight));
		}

		if (rhythmsListSlidingPanel.isFullyOpen()) {
			addRhythmsListAutoHelp(helpTips);
		}
		
		// 2nd set (3rd if auto help)
		helpTips.add(new HelpTipModel(null, true, TwlLocalisationKeys.HELP_VOLUME_CONTROLS_TEXT
				, settingsSlidingPanel.isFullyOpen() ? playRhythmVolumeControls : toggleSettingsBtn
				, settingsSlidingPanel.isFullyOpen() ? HelpTipTargetPoint.Top : HelpTipTargetPoint.TopRight));
		helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_OPEN_PREFS_FOR_ANIMATION_TEXT, KeepYaBeat.root.getMoreButton(), HelpTipTargetPoint.TopRight));
		if (rhythmsListSlidingPanel.isCurrentStateHidden()) {
			// also show open rhythms help if it's hidden
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_OPEN_RHYTHMS_LIST_TEXT, toggleListBtn, HelpTipTargetPoint.Top));
		}
		
		// last set
		helpTips.add(new HelpTipModel(null, true, TwlLocalisationKeys.HELP_EDIT_SAVE_NEW_RHYTHM_BUTTONS_TEXT, editRhythmBtn, 
				rhythmsListSlidingPanel.isFullyOpen() ? HelpTipTargetPoint.Right : HelpTipTargetPoint.Top));
		if (settingsSlidingPanel.isFullyOpen()) { 
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_PANEL_CLOSE_BUTTONS_TEXT
						, settingsSlidingPanel.getSlideButton(), HelpTipTargetPoint.Top));
		}
		else {
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_PANEL_CLOSE_BUTTONS_TEXT
					, toggleSettingsBtn, HelpTipTargetPoint.TopRight));
		}

		// send the whole lot to the controller
		new HelpTipsController(UserBrowserProxy.Key.player
				, autoHelpKey != null ? TwlLocalisationKeys.HELP_FIRST_SEEN_EXTRA_TEXT : null
				, (HelpTipModel[]) helpTips.toArray(new HelpTipModel[helpTips.size()]));
	}

	@Override
	public void itemChanged() {
		// update the widgets on the panel that could be out of sync if there's an undo
		// and the volumes data which only gets its own updates via rhythm draughter's beat types
		updateModelIfNeeded();
		updateWidgetsIfNeeded(true);
		invalidateLayout();
	}

	/**
	 * After an update, the saved rhythm might not exist anymore, or the data cache might be out of step
	 * if that's the case call initModel() again
	 */
	private void updateModelIfNeeded() {
		if (rhythms.lookup(rhythm.getKey()) == null 
				&& !rhythm.getName().equals(resourceManager.getLocalisedString(CoreLocalisation.Key.NEW_RHYTHM_NAME))) {
			initModel(null);
		}
		else if (rhythmDraughter != null && rhythm.getBeatDataCacheNano() != rhythmDraughter.getDataCachedNano()) {
			TwlResourceManager.getInstance().log(LOG_TYPE.debug, this, "PlayerGroup.updateModelIfNeeded: data cache out of step, re-init player for same rhythm");
			initModel(rhythm);
		}
	}

	/**
	 * Called from rhythmsList layout(), letting this class' layout() know can go ahead and do its thing 
	 */
	public void setRhythmsListSized() {
		this.isRhythmsListSized = true;
	}

	public boolean isRhythmsListSized() {
		return isRhythmsListSized;
	}

	@Override
	protected int getEffectiveXForControls() {
		return rhythmsList.getX() + rhythmsList.getWidth();
	}

	@Override
	protected void layout() {
		if (isRhythmsListSized) {
	 		// layout on the sliding panel will put the rhythmsList widget where it should be, 
			// particularly needed if hidden at the start otherwise this panel will be laid out as if it isn't hidden
			// the ordering is significant, because this class is actually added to the parent first so that the 
			// rhythmsList can overlay it, but that means layout() is called on this one first... 
			// so keep invalidating the layout to cause recall, until rhythmsList is sized (called from its own layout())
	
			sizeAndPlaceCanvas();
			sizeAndPlaceTopWidgets();
			sizeAndPlaceSettingsList();
			sizeAndPlacePlayControls();
		}
		else {
//			System.out.println("playerGroup.layout: can't size yet!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");			
//			invalidateLayout();
		}
	}
	
	/**
	 * width has to respond to what's available when the rhythmsList slides in/out
	 */
    @Override
	protected void sizeAndPlaceTopWidgets() {
		int effectiveX = getEffectiveXForControls();
		topWidgetsLayout.adjustSize();
		topWidgetsLayout.setSize(getInnerWidth() - effectiveX - getInnerX(), topWidgetsLayout.getHeight());
		topWidgetsSlidingPanel.adjustSize();
		topWidgetsSlidingPanel.setPosition(effectiveX, getY());	
	}

	/**
	 * Make a layout for the top set of widgets (buttons, labels)
	 * and put it into a sliding panel
	 */
	@Override
	protected void makeTopWidgetsLayout() {

		makeRhythmListToggleButton();
		//Button 
		toggleSettingsBtn = makeSettingsToggleButton();
		// need access to these buttons 
		makeEditRhythmButton();
		makeSaveRhythmButton();
		makeNewRhythmButton();
		helpBtn = makeHelpButton();
		makeShareButton();
		
		topWidgetsLayout = new DialogLayout();
		topWidgetsLayout.setTheme("topWidgetsLayout");
		
        // enclosing groups
        Group hGroup = topWidgetsLayout.createParallelGroup();
        Group vGroup = topWidgetsLayout.createSequentialGroup();
        
        // horizontal group of buttons, sequential line with gaps added
        Group hButtonsSequentialGroup = topWidgetsLayout.createSequentialGroup()
				.addWidget(toggleListBtn)
				.addWidget(shareBtn)
//				.addGap(DialogLayout.LARGE_GAP)
				.addWidget(editRhythmBtn)
//				.addGap(DialogLayout.LARGE_GAP)
				.addWidget(saveRhythmBtn)
//				.addGap(DialogLayout.LARGE_GAP)
				.addWidget(newRhythmBtn)
				.addWidget(helpBtn)
				.addGap()
				.addWidget(toggleSettingsBtn);
        
        hGroup.addGroup(hButtonsSequentialGroup);
        
        // each group in vertical group is a horizontal line going down the layout
        // so the addGap() between the 1st and 2nd groups increases the gap between the top line
        // and the next line of widgets
        vGroup
			.addGroup(topWidgetsLayout.createParallelGroup()
				.addWidget(toggleListBtn)
				.addWidget(shareBtn)
				.addWidget(editRhythmBtn)
				.addWidget(saveRhythmBtn)
				.addWidget(newRhythmBtn)
				.addWidget(helpBtn)
				.addWidget(toggleSettingsBtn));
    
        topWidgetsLayout.setHorizontalGroup(hGroup);
        topWidgetsLayout.setVerticalGroup(vGroup);

        topWidgetsSlidingPanel = new SlidingAnimatedPanel(topWidgetsLayout, Direction.DOWNWARDS, true);
        topWidgetsSlidingPanel.setTheme("topWidgetsPanel");
        add(topWidgetsSlidingPanel);
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
	 * Initialise with the passed rhythm, if none start a new default one
	 * (note, new rhythm is not saved to the library)
	 */
	@Override
	public void initModel(Rhythm rhythm) {
		// either none there, get a new rhythm or it's gone
		if (rhythm == null) {
			this.rhythm = rhythms.makeNewRhythm(Function.getBlankContext(resourceManager));
		}
		else {
			// rhythm passed use that, only passed when updating, so update the rhythm draughter too
			this.rhythm = rhythm;
		}

		// first time in there's no draughter yet, only on updates
		if (rhythmDraughter != null) {
			rhythmDraughter.initModel(this.rhythm, true);
			playerRhythmCanvas.invalidateLayout();
			
			playRhythmVolumeControls.modelUpdated(rhythmDraughter.getRhythmBeatTypes());
			settingsSlidingPanel.invalidateLayout();
			playControls.initModel(rhythmDraughter);
		}
		
		// store it in state only if it's changed
		if (!this.rhythm.getKey().equals(playerState.getRhythmKey())) {
			playerState.setRhythmKey(this.rhythm.getKey());
		}
		
		// set widgets
		updateWidgetsIfNeeded(false);
	}
	
	@Override
	protected void initDraughting() {
		// the draughter
		rhythmDraughter = new PlayedRhythmDraughter(rhythm); 

		// drawing canvas area
		playerRhythmCanvas = new PlayerRhythmCanvas(rhythmDraughter);
		
		// init the settings list
		playRhythmVolumeControls = new PlayerRhythmBeatTypesList(rhythmDraughter.getRhythmBeatTypes());
		playRhythmVolumeControls.init();
	}
	
	/**
	 * listen to changes to the rhythm and to beat types, because colour change on volume controls
	 * is not saved to the rhythm, but is global to beat types
	 * @param on
	 */
	private void setListeners(boolean on) {
		if (on) {
			resourceManager.getListenerSupport().addListener(beatTypes, this);
			resourceManager.getListenerSupport().addListener(rhythms, this);		
			// listen to changes to player state
			resourceManager.getListenerSupport().addListener(
					((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()), this);
		}
		else {
			resourceManager.getListenerSupport().removeListener(this);
		}
	}
	
	/**
	 * Player module is controlling the resume because the rhythmsList is resuming first,
	 * disable auto resume on the panels as they will be resumed as a nextTask after that
	 * (no effect after first layout)
	 */
	void disableAutoResume() {
		if (topWidgetsSlidingPanel.isStartingUpHidden() && canvasSlidingPanel.isStartingUpHidden()) {
			topWidgetsSlidingPanel.setDisableAutoResume(true);
			canvasSlidingPanel.setDisableAutoResume(true);
			settingsSlidingPanel.setDisableAutoResume(true);
			playControlsSlidingPanel.setDisableAutoResume(true);
		}
	}
	
	/**
	 * Called from the parent PlayerModule during its own resume
	 */
	void resume() {
		resumeComplete = false;
		
		// first time in the starting up hidden flag is set on the sliding panels, which is unset it their layout() methods
		// allow them to resume themselves there
		if (!topWidgetsSlidingPanel.isStartingUpHidden()) {
			topWidgetsSlidingPanel.resume();
		}
		
		if (!canvasSlidingPanel.isStartingUpHidden()) {
			canvasSlidingPanel.resume();
		}
		
		if (!settingsSlidingPanel.isStartingUpHidden()) {
			settingsSlidingPanel.resume();
		}

		if (!playControlsSlidingPanel.isStartingUpHidden()) {
			playControlsSlidingPanel.resume();
		}

		// ensure the name is put into the module bar
		KeepYaBeat.root.setModuleExtra(rhythm.getName());

		reattachFocus();
	}
	
	/**
	 * Called on resume and also after the rhythm editor closes and calls back
	 */
	public void reattachFocus() {
		// make sure all is current, act exactly as though there's been some update 
		// (which there might have been during hibernation)
		itemChanged();
		
		// listen to changes
		setListeners(true);

		// have the canvas receive events
		KeepYaBeat.root.setEventRecipient(playerRhythmCanvas);
		
		// re-attach play controls and beat tracker
		claimPlayControls();
	}
	
	/**
	 * Called when rhythm editor is invoked by PlayerModule.invokeRhythmEditor 
	 */
	public void giveUpFocus() {
		// stop listening
		setListeners(false);

		// stop receiving events
		KeepYaBeat.root.setEventRecipient(null);
	}
	
	/**
	 * Called from the parent PlayerModule during its own hibernate
	 */
	void hibernate() {
		hibernate(null);
	}
	
	/**
	 * Overloaded to support closing rhythms list panel after this one is done
	 * @param nextTask
	 */
	void hibernate(PendingTask nextTask) {
		resumeComplete = false;
		// stop listening to changes
		setListeners(false);
		// stop the canvas receiving events
		KeepYaBeat.root.setEventRecipient(null);

		if (nextTask != null) {
			topWidgetsSlidingPanel.hibernate(nextTask);
		}
		else {
			topWidgetsSlidingPanel.hibernate();
		}

		canvasSlidingPanel.hibernate();
		settingsSlidingPanel.hibernate();
		playControlsSlidingPanel.hibernate();
	}
	
	public boolean isPaused() {
		if (!pauseComplete) {
//System.out.println("testing for playergroup isPaused topWidgets="+topWidgetsSlidingPanel.isHidden()
//		+" canvas="+canvasSlidingPanel.isHidden());
			if (topWidgetsSlidingPanel.isCurrentStateHidden() 
				&& canvasSlidingPanel.isCurrentStateHidden()
				&& settingsSlidingPanel.isCurrentStateHidden()
				&& playControlsSlidingPanel.isCurrentStateHidden()) {
				pauseComplete = true;
			}
		}
		return pauseComplete;
	}

//	private void printOutIsResumedVars(SlidingAnimatedPanel panel, String which) {
//		System.out.println("for "+which+" resumeHidden="+panel.isResumeHidden()+
//				" isHidden="+panel.isHidden()
//				+" isFullyOpen="+panel.isFullyOpen()
//				+" isStartingUpHidden="+panel.isStartingUpHidden()
//				+" condition 1="+(panel.isResumeHidden() && panel.isHidden())
//				+" condition 2="+(!panel.isResumeHidden() && panel.isFullyOpen())
//				);
//	}
	
	public boolean isResumed() {
		if (!resumeComplete) {
//System.out.println("testing for playergroup isResumed topWidgets="+topWidgetsSlidingPanel.isResumeComplete()
//		+" canvas="+canvasSlidingPanel.isResumeComplete());
			if (topWidgetsSlidingPanel.isResumeComplete()
				&& canvasSlidingPanel.isResumeComplete()
				&& settingsSlidingPanel.isResumeComplete()
				&& playControlsSlidingPanel.isResumeComplete()) {
				resumeComplete = true;
				
				// show auto help if not shown before
				TwlPersistentStatesManager persistenceManager = (TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();
				final String autoHelpKey = HelpTipsController.HelpPage.player.name();
				if (!persistenceManager.isAutoHelpSeen(autoHelpKey)) {
					resourceManager.getPendingTasksScheduler().addTask(new PendingTask("wait for sliding panels to open") {
						@Override
						protected void updateComplete() {
							complete = topWidgetsSlidingPanel.isFullyOpen() && playControlsSlidingPanel.isFullyOpen();
							if (complete) {
								showHelp(autoHelpKey);
							}
						}
					});
				}
			}
//			else {
//				System.out.println("PlayerGroup.isResumed: topWidgetsSlidingPanel complete="+topWidgetsSlidingPanel.isResumeComplete());		
//				System.out.println("PlayerGroup.isResumed: canvasSlidingPanel complete="+canvasSlidingPanel.isResumeComplete());		
//				System.out.println("PlayerGroup.isResumed: settingsSlidingPanel complete="+settingsSlidingPanel.isResumeComplete());		
//				System.out.println("PlayerGroup.isResumed: playControlsSlidingPanel complete="+playControlsSlidingPanel.isResumeComplete());		
//				printOutIsResumedVars(topWidgetsSlidingPanel, "topWidgets");
//				printOutIsResumedVars(settingsSlidingPanel, "settings");
//			}
		}
		return resumeComplete;
	}

}
