package com.stillwindsoftware.keepyabeat.platform.twl;

import java.util.Map.Entry;
import java.util.TreeMap;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.ModuleManager;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.BeatTypesListOfValues;
import com.stillwindsoftware.keepyabeat.gui.BeatTypesListOfValues.BeatTypeSelectReceiver;
import com.stillwindsoftware.keepyabeat.gui.BeatsAndSoundsModule;
import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup;
import com.stillwindsoftware.keepyabeat.gui.PlayControls;
import com.stillwindsoftware.keepyabeat.gui.PlayControls.PlayingState;
import com.stillwindsoftware.keepyabeat.gui.PlayerModule;
import com.stillwindsoftware.keepyabeat.gui.TagsListDialog;
import com.stillwindsoftware.keepyabeat.model.Beat;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformAnimationState;
import com.stillwindsoftware.keepyabeat.platform.PlatformAnimationState.PlatformAnimationStateKey;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.RhythmEditor;
import com.stillwindsoftware.keepyabeat.player.BeatEditActionHandler;
import com.stillwindsoftware.keepyabeat.player.EditedBeat.EditedFullBeat;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuAction;
import de.matthiasmann.twl.MenuManager;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.OptionBooleanModel;
import de.matthiasmann.twl.model.SimpleIntegerModel;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;

public class TwlGuiManager implements GuiManager {

    public static final PlatformAnimationStateKey STATE_REVERSE_VIDEO = 
    		new TwlAnimationState.TwlAnimationStateKey(StateKey.get("reverseVideo"));
    public static final float REVERSE_VIDEO_THRESHOLD = 50.0f;
    // on startup the FPS might not be worked out yet, and if a module is starting it will query
    // the value, assume it's low because startup renders seem to be slow
	private static final float MIN_FPS = 25.0f;
	// the speed of a swipe up that is the minimum to indicate a swipe up
	private static final float MIN_SWIPE_UP_VECTOR_Y = -8.0f;
	// the max value per frame
	private static final float MAX_SWIPE_UP_VECTOR_Y = -12.0f;

    private static TwlGuiManager instance;
	private MutableRectangle innerDrawingDimensions;
	private Location2Df mouseLocation = new Location2Df(0,0);
	private RhythmEditor rhythmEditorModule;
	
	// keep the active instance of player controls so can feedback to it
	private PlayControls playControls;
	
	public static TwlGuiManager getInstance() {
		if (instance == null) {
			instance = new TwlGuiManager();
		}
		
		return instance;
	}
	
//	@Override
//	public PendingTask assertPlayerOpen(Rhythm rhythm) {
//		System.out.println("twlguimanager.assertPlayerOpen not yet implemented");
//		return null;
//	}
//
//	@Override
//	public PendingTask assertQuickEditOpen(Rhythm rhythm) {
//		System.out.println("twlguimanager.assertquickeditopen not yet implemented");
//		return null;
////		return PlayerPanel.getInstance().assertQuickEditOpen();
//	}

	@Override
	public PendingTask assertVolumesOpen(final Rhythm rhythm) {

		// test for rhythm editor still active
		// just work directly with the it, otherwise have to open player module
		if (rhythmEditorModule != null) {
			return rhythmEditorModule.openRhythmBeatTypes();
		}
		else {
			// if already open and top of list, nothing to do
			final PlayerModule playerModule = (PlayerModule) PlayerModule.getInstance();
			if (ModuleManager.getInstance().isTopOfTheStack(playerModule)) {
				playerModule.openRhythm(rhythm);
				playerModule.requestOpenSettingsControls();
				return null;
			}
			else {
				PendingTask togglePlayerModuleTask = ModuleManager.getInstance().toggleTopLevelModule(playerModule, false);
				togglePlayerModuleTask.appendNextTask(new PendingTask("open player controls task") {
					@Override
					protected void startTask() {
						playerModule.openRhythm(rhythm);
						playerModule.requestOpenSettingsControls();
					}
				});
				
				return togglePlayerModuleTask;
			}
		}
	}

	@Override
	public PendingTask assertBeatsAndSoundsOpen() {
		if (BeatsAndSoundsModule.getInstance() != null && ModuleManager.getInstance().isTopOfTheStack(BeatsAndSoundsModule.getInstance())) {
			return null;
		}
		else {
			return ModuleManager.getInstance().toggleTopLevelModule(BeatsAndSoundsModule.getInstance(), false);
		}
	}

	@Override
	public boolean isPlayerVisible() {
		//TODO later when player module can co-exist with another module, this won't be enough
		// this check detects that the player is initialised and then the top in the module stack
		return PlayerModule.getInstance() != null && ModuleManager.getInstance().isTopOfTheStack(PlayerModule.getInstance());
	}

	@Override
	public void openPlayerWithRhythm(final Rhythm openRhythm) {
		// test if player is already top of the stack (visible and active)
		if (isPlayerVisible()) {
			((PlayerModule)PlayerModule.getInstance()).openRhythm(openRhythm);
		}
		else {
			// otherwise open it and put the open rhythm
			PendingTask openModuleTask = ModuleManager.getInstance().toggleTopLevelModule(PlayerModule.getInstance(), false);
			openModuleTask.appendNextTask(new PendingTask("open rhythm task") {
				@Override
				protected void startTask() {
					((PlayerModule)PlayerModule.getInstance()).openRhythm(openRhythm);
				}
			});
			TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(openModuleTask);
		}
	}

	/**
	 * Providing player module is running, check to see if the tag is in the search list.
	 * Called during delete tag command so that it can reinstate a search tag for undo.
	 */
	@Override
	public boolean doesRhythmsSearchContainTag(Tag tag) {
		if (ModuleManager.getInstance().isRunningTopLevelModule(PlayerModule.getInstance())) {
			return ((PlayerModule)PlayerModule.getInstance()).doesRhythmsSearchContainTag(tag);
		}
		else {
			return false;
		}
	}

	/**
	 * Called during undo of a tag delete, only when the tag was found to be in the rhythms search
	 * list (see doesRhythmsSearchContainTag()) 
	 */
	@Override
	public void addTagToRhythmsSearch(Tag tag) {
		if (ModuleManager.getInstance().isRunningTopLevelModule(PlayerModule.getInstance())) {
			if (!((PlayerModule)PlayerModule.getInstance()).doesRhythmsSearchContainTag(tag)) {
				((PlayerModule)PlayerModule.getInstance()).addTagToRhythmsSearch(tag);
			}
			else {
				TwlResourceManager.getInstance().log(LOG_TYPE.warning, this, 
						"TwlGuiManager.addTagToRhythmsSearch: Attempt to add tag to rhythmsList search that is already there");
			}
		}
	}

	/**
	 * Called during undo of tag change/delete which can only happen from the master tags list, so
	 * safe always to show it
	 */
	@Override
	public void showMasterTagsList() {
		TagsListDialog.popupTagsList(null, null);
	}

	/**
	 * Called from both showPlayerPopupMenu() and showRhythmEditorPopupMenu(), supplies a Menu
	 * which makes sure the menu is fully visible.
	 * @param mm
	 */
	private Menu makePopupMenu() {
		Menu menu = new Menu() {
				@Override
			    public MenuManager openPopupMenu(Widget parent, int x, int y) {
			        MenuManager mm = super.openPopupMenu(parent, x, y);
			        if (mm.getNumChildren() == 1) { // should be the menu popup (which is not visible type)
			        	Widget popup = mm.getChild(0);
			        	// reposition so that it's always within screen
						boolean reposition = false;
						int xpos = popup.getX();
			        	if (mm.getInnerX() + popup.getX() + popup.getWidth() > getScreenWidth()) {
			        		reposition = true;
			        		xpos = Math.max(
			        				Math.min(mm.getInnerX() + mm.getInnerWidth() - popup.getWidth(), popup.getX())
			        				, mm.getInnerX());
			        	}
			        	
						int ypos = popup.getY();
			        	if (mm.getInnerY() + popup.getY() + popup.getHeight() > getScreenHeight()) {
			        		reposition = true;
			        		ypos = Math.max(
			        				Math.min(mm.getInnerY() + mm.getInnerHeight() - popup.getHeight(), popup.getY())
			        				, mm.getInnerY());
			        	}
			        	
			        	if (reposition) {
			        		popup.setPosition(xpos, ypos);
			        	}
			        }
			        return mm;
			    }
			};
			menu.setTheme("menumanager");
			return menu;
	}
		
	@Override
	public void showPlayerPopupMenu(final Rhythm rhythm) {
		if (isPlayerVisible()) {
			Menu menu = makePopupMenu();
	
			menu.add(new MenuAction(
					TwlResourceManager.getInstance()
					.getLocalisedString(TwlLocalisationKeys.MENU_TOOLTIP_EDIT_RHYTHM)
						, new Runnable() {
								@Override
								public void run() {
									((PlayerModule)PlayerModule.getInstance()).invokeRhythmEditor(rhythm);
								}
							}));					
	
			menu.openPopupMenu((PlayerModule)PlayerModule.getInstance(), Mouse.getX(), getScreenHeight() - Mouse.getY());
		}
	}

	@Override
	public void openRhythmsList() {
		// if already open and top of list, nothing to do
		final PlayerModule playerModule = (PlayerModule) PlayerModule.getInstance();
		if (ModuleManager.getInstance().isTopOfTheStack(playerModule)) {
			playerModule.requestOpenRhythmsList();
		}
		else {
			PendingTask togglePlayerModuleTask = ModuleManager.getInstance().toggleTopLevelModule(playerModule, false);
			togglePlayerModuleTask.appendNextTask(new PendingTask("open rhythms list task") {
				@Override
				protected void startTask() {
					playerModule.requestOpenRhythmsList();
				}
			});
			TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(togglePlayerModuleTask);
		}
	}

	/**
	 * TWL only so to be able to feedback for the next 3 methods
	 * (stopPlay(), playIterationsChanged(), setPanelDisplayNumber())
	 * And also so can change parent of playControls when the user navigates from player to editor
	 * @param playControls
	 */
	public void setPlayControls(PlayControls playControls) {
		this.playControls = playControls;
	}

	public PlayControls getPlayControls() {
		return playControls;
	}

	@Override
	public void stopPlay() {
		if (playControls != null) {
			playControls.stopPlay();
		}
		else {
			TwlResourceManager.getInstance().log(LOG_TYPE.warning, this, "TwlGuiManager.stopPlay: no playControls to feedback to");
		}
	}

	@Override
	public boolean isRhythmPlaying() {
		return (playControls != null && playControls.getPlayingState() != PlayingState.stopped);
	}

	@Override
	public void playIterationsChanged(int iterations) {
		if (playControls != null) {
			playControls.playIterationsChanged(iterations);
		}
		else {
			TwlResourceManager.getInstance().log(LOG_TYPE.warning, this, "TwlGuiManager.playIterationsChanged: no playControls to feedback to");
		}
	}

	@Override
	public RhythmEditor getRhythmEditor() {
		return rhythmEditorModule;
	}

	@Override
	public void setRhythmEditor(RhythmEditor rhythmEditorModule) {
		this.rhythmEditorModule = rhythmEditorModule;
	}

	@Override
	public UndoableCommandController getRhythmEditorUndoableCommandController() {
		if (rhythmEditorModule == null) {
			return null;
		}
		else {
			return rhythmEditorModule.getUndoableCommandController();
		}
	}

	@Override
	public void showRhythmEditorPopupMenu(final BeatEditActionHandler beatActionHandler, TreeMap<String, Runnable> menuOptionsMap) {

		if (rhythmEditorModule != null) {
			Menu menu = makePopupMenu();
	
			Entry<String, Runnable> menuOption;
			while ((menuOption = menuOptionsMap.pollFirstEntry()) != null) {
				// substring to remove the single digit that guarantees ordering
				menu.add(new MenuAction(menuOption.getKey().substring(1), menuOption.getValue()));
			}

			if (menu.getNumElements() != 0) {
				// make sure the selection is cleared when the menu closes
				menu.addListener(new Menu.Listener() {
					@Override
					public void menuOpening(Menu menu) {}			
					@Override
					public void menuOpened(Menu menu) {
					}
					@Override
					public void menuClosed(Menu menu) {
						beatActionHandler.clearSelection();
					}
				});
				
				menu.openPopupMenu((Widget)rhythmEditorModule, Mouse.getX(), getScreenHeight() - Mouse.getY());
			}
			else {
				// no options, cancel selection
				TwlResourceManager.getInstance().log(LOG_TYPE.warning, this, "TwlGuiManager.showRhythmEditorPopupMenu: no context menu options supplied");
				beatActionHandler.clearSelection();
			}
		}
	}

	@Override
	public void setPanelDisplayNumber(int num) {
		TwlResourceManager.getInstance().log(LOG_TYPE.error, this, "TwlGuiManager.setPanelDisplayNumber: no not implemented");
	}

	@Override
	public void setExclusiveOperationInProcess() {
		ModuleManager.getInstance().setExclusiveOperationInProcess();
	}
	
	@Override
	public void unsetExclusiveOperationInProcess() {
		//added for android, not found to be needed here (yet)
	}

	@Override
	public boolean isExclusiveOperationInProcess() {
		return ModuleManager.getInstance().isExclusiveOperationInProcess();
	}

	@Override
	public void runInGuiThread(Runnable runner) {
		KeepYaBeat.root.getGUI().invokeLater(runner);
	}

	/**
	 * Not used at the moment, perhaps core code will need it once android version is working
	 */
	@Override
	public MutableRectangle getInnerDrawingDimensions() {
		if (innerDrawingDimensions == null) {
			innerDrawingDimensions = new MutableRectangle(KeepYaBeat.root.getInnerX(), KeepYaBeat.root.getInnerY(), KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight());
		}
		else {
			innerDrawingDimensions.setXYWH(KeepYaBeat.root.getInnerX(), KeepYaBeat.root.getInnerY(), KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight());
		}
		return innerDrawingDimensions;
	}

	@Override
	public int getScreenWidth() {
		return KeepYaBeat.getWidth();
	}

	@Override
	public int getScreenHeight() {
		return KeepYaBeat.getHeight();
	}

	@Override
	public Location2Df getPointerLocation() {
		mouseLocation.setLocation(Mouse.getX(), KeepYaBeat.getHeight() - Mouse.getY());
		return mouseLocation;
	}

	@Override
	public Location2Df getPointerRelativeLocation(MutableRectangle toRectangle) {

		return Location2Df.getTransportLocation(Mouse.getX() - toRectangle.getX()
				, KeepYaBeat.getHeight() - Mouse.getY() - toRectangle.getY());
	}

	public PlatformAnimationStateKey getAnimationStateKey(String keyName) {
		return new TwlAnimationState.TwlAnimationStateKey(StateKey.get(keyName));
	}

	public PlatformAnimationState getAnimationState() {
		return new TwlAnimationState();
	}

	public void pushTransform(float rotation, float transX, float transY) {
		// make rotation transformations
		GL11.glPushMatrix();
		GL11.glTranslatef(transX, transY, 0.0f);
		GL11.glRotatef(rotation, 0.0f, 0.0f, 1.0f);
		GL11.glTranslatef(-transX, -transY, 0.0f);
	}

	public void popTransform() {
		GL11.glPopMatrix();
	}
	
	public void pushTintColour(float r, float g, float b, float a) {
		KeepYaBeat.root.getGUI().getRenderer().pushGlobalTintColor(r, g, b, a);		
	}

	public void popTintColour() {
		KeepYaBeat.root.getGUI().getRenderer().popGlobalTintColor();		
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		KeepYaBeat.root.getGUI().getRenderer().clipEnter((int)x, (int)y, (int)w, (int)h);
	}

	@Override
	public void popClip() {
		KeepYaBeat.root.getGUI().getRenderer().clipLeave();
	}


	/**
	 * Overloaded version that takes a coreId
	 * @param coreId
	 * @param title
	 * @param isExit
	 */
	private void showMessage(final CoreLocalisation.Key coreId, final String title, final boolean isExit, boolean offerReportBug) {
		showMessage(TwlResourceManager.getInstance().getLocalisedString(coreId), title, isExit, offerReportBug);
	}

	/**
	 * Shows a message with title in gui thread, optionally exits after user presses ok
	 * @param message
	 * @param title
	 * @param isExit
	 */
	private void showMessage(final String message, final String title, final boolean isExit, final boolean offerReportBug) {

		runInGuiThread(new Runnable() {
			@Override
			public void run() {
				CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
		            public void callback(ConfirmationPopup.CallbackReason reason) {
		            	if (isExit) {
		            	    // serious unexpected error needs to abort 
		            		System.exit(-1);
		            	}
		            }
		        };

			    Label errorMsg = new Label(message);

			    Button offerReportBugBtn = null;
			    int buttonsToShow = ConfirmationPopup.OK_BUTTON;
			    if (offerReportBug) {
			    	buttonsToShow = ConfirmationPopup.OK_BUTTON | ConfirmationPopup.EXTRA_BUTTON;
			    	offerReportBugBtn = KeepYaBeat.root.makeReportBugButton();
			    }
			    
				ConfirmationPopup.showDialogConfirm(new ConfirmationPopup.ConfirmPopupWindow(true), null, callback, KeepYaBeat.root, null
		        		, title
		        		, buttonsToShow
		        		, offerReportBugBtn, null, null
		        		, true, null, ConfirmationPopup.OK_BUTTON, true, errorMsg);
			}
	    });
	}

	@Override
	public void abortWithMessage(CoreLocalisation.Key coreId) {
		showMessage(coreId, TwlResourceManager.getInstance()
				.getLocalisedString(TwlLocalisationKeys.ABORT_ERROR_TITLE), true, true);
	}

	@Override
	public void warnOnErrorMessage(CoreLocalisation.Key coreId, boolean offerReportBug, Exception e) {
		showMessage(coreId, TwlResourceManager.getInstance()
				.getLocalisedString(TwlLocalisationKeys.UNEXPECTED_ERROR_TITLE), false, offerReportBug);
	}

	@Override
	public void warnOnErrorMessage(String message, String title, boolean offerReportBug, Exception e) {
		showMessage(message, title, false, offerReportBug);
	}

	@Override
	public float getExpectedFps() {
		float fps = MIN_FPS;
		fps = Math.max(MIN_FPS, Float.parseFloat(KeepYaBeat.root.getFpsCounter().getText()));
		return fps;
	}

	@Override
	public float getMinSwipeUpY() {
		return MIN_SWIPE_UP_VECTOR_Y;
	}

	@Override
	public float getMaxSwipeUpY() {
		return MAX_SWIPE_UP_VECTOR_Y;
	}

	@Override
	public void popupBeatTypeLov(final BeatTypeSelectedCallback callback) {
		popupBeatTypeLov(callback, KeepYaBeat.root, null);
	}

	/**
	 * provide an owner for the popup
	 * @param callback
	 * @param owner
	 */
	public void popupBeatTypeLov(final BeatTypeSelectedCallback callback, Widget owner, final Widget focusOnCloseWidget) {
		final BeatTypesListOfValues beatTypesLov = new BeatTypesListOfValues();
//		final PopupWindow confirmPopup = ConfirmationPopup.getConfirmPopup(true);
		final PopupWindow lovPopupWindow = new PopupWindow(owner) {

			@Override
			public void closePopup() {
				super.closePopup();
				if (focusOnCloseWidget != null) {
					focusOnCloseWidget.requestKeyboardFocus();
				}
			}

			@Override
			protected void layout() {
				super.layout();
				// once finished sizing it can lay it out
				if (beatTypesLov.isLayoutInitComplete()) {
					Location2Df pointerLoc = getPointerLocation();
					int xPos = (int) pointerLoc.getX();
					int yPos = (int) pointerLoc.getY();

					beatTypesLov.adjustSize();
					
					int furthestX = KeepYaBeat.root.getInnerX() + KeepYaBeat.root.getInnerWidth() - beatTypesLov.getWidth();
					int furthestY = KeepYaBeat.root.getInnerY() + KeepYaBeat.root.getInnerHeight() - beatTypesLov.getHeight();

					xPos = Math.min(xPos, furthestX);
					yPos = Math.min(yPos, furthestY);
					beatTypesLov.setPosition(xPos, yPos);
				}
				else {
					// make sure layout is called again
					invalidateLayout();
				}
			}
			
		};

		/**
		 * Add a receiver to listen for the selection
		 */
		beatTypesLov.setBeatTypeSelectReceiver(new BeatTypeSelectReceiver() {

			@Override
			public void beatTypeSelected(BeatType beatType, Widget beatSelectionWidget) {
				lovPopupWindow.closePopup();
				callback.beatTypeSelected(beatType);
			}
		});

		beatTypesLov.init();

		lovPopupWindow.setTheme("confirmPopup");
		lovPopupWindow.add(beatTypesLov);
		lovPopupWindow.openPopup();
	}

	/**
	 * Create a confirm popup with the allowed values for a fullbeat
	 */
	@Override
	public void showChangeFullBeatValueDialog(EditedFullBeat editedFullBeat, final FullBeatValueChangedCallback valueChangedCallback) {
		BoxLayout box = new BoxLayout(BoxLayout.Direction.VERTICAL);
		box.setTheme("fullBeatValueOptionsBox");
		
		TwlResourceManager resourceManager = TwlResourceManager.getInstance();
		
		// couldn't understand how to get the enum boolean model to work, so convert from an Integer model
		// and back again
		final int value = editedFullBeat.getValueAsDivision();
		
		final int FULL = 48
		, QUARTER = 12
		, THIRD = 16
		, HALF = 24
		, TWO_THIRDS = 32
		, THREE_QUARTERS = 36;

		final SimpleIntegerModel optionModel = new SimpleIntegerModel(12, 48, value);

		final ToggleButton fullRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_LENGTH_FULL_LABEL));
		fullRadio.setTheme("radiobutton");
		fullRadio.setModel(new OptionBooleanModel(optionModel, FULL));
		box.add(fullRadio);
		
		final ToggleButton quarterRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_LENGTH_QUARTER_LABEL));
		quarterRadio.setTheme("radiobutton");
		quarterRadio.setModel(new OptionBooleanModel(optionModel, QUARTER));
		box.add(quarterRadio);
		
		final ToggleButton thirdRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_LENGTH_THIRD_LABEL));
		thirdRadio.setTheme("radiobutton");
		thirdRadio.setModel(new OptionBooleanModel(optionModel, THIRD));
		box.add(thirdRadio);
		
		final ToggleButton halfRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_LENGTH_HALF_LABEL));
		halfRadio.setTheme("radiobutton");
		halfRadio.setModel(new OptionBooleanModel(optionModel, HALF));
		box.add(halfRadio);
		
		final ToggleButton twoThirdsRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_LENGTH_TWO_THIRDS_LABEL));
		twoThirdsRadio.setTheme("radiobutton");
		twoThirdsRadio.setModel(new OptionBooleanModel(optionModel, TWO_THIRDS));
		box.add(twoThirdsRadio);
		
		final ToggleButton threeQuartersRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.BEAT_LENGTH_THREE_QUARTERS_LABEL));
		threeQuartersRadio.setTheme("radiobutton");
		threeQuartersRadio.setModel(new OptionBooleanModel(optionModel, THREE_QUARTERS));
		box.add(threeQuartersRadio);
		
		// check for a change to the value if ok was pressed
		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
            public void callback(ConfirmationPopup.CallbackReason reason) {
            	if (reason == ConfirmationPopup.CallbackReason.OK) {
            		// test for a change
            		if (optionModel.getValue() != value) {
            			valueChangedCallback.valueSelected(optionModel.getValue());
            		}
            	}
            }
        };

        ConfirmationPopup.showDialogConfirm(null, callback, null, // no widget from here 
        		value == Beat.SubBeatDivision.NUM_DIVISIONS_PER_BEAT
				? resourceManager.getLocalisedString(CoreLocalisation.Key.MENU_MAKE_IRREGULAR_BEAT) 
				: resourceManager.getLocalisedString(CoreLocalisation.Key.MENU_CHANGE_BEAT_VALUE), 
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, box);
	}

	@Override
	public boolean isBlocking() {
		return KeepYaBeat.root.isNonRhythmEditorPopupOpen();
	}

	@Override
	public void showNotification(String message) {
		KeepYaBeat.root.showNotification(message);	
	}

	@Override
	public void releaseResources() {
		// for android, do nothing
	}

	@Override
	public boolean isNavigateOnUndo() {
		return true; // for android it doesn't
	}

	@Override
	public void runAsyncProtectedTaskThenUiTask(ParameterizedCallback... callbacks) {
        // validate options
        if (callbacks.length > 1 && callbacks[1].isForResult()) {
            // don't bother to log, this is a program bug and should never happen in prod
            throw new IllegalArgumentException("runAsyncProtectedTaskThenUiTask: 2nd callback must not request a result object (must init with false)");
        }

        Object result = null;

        if (callbacks[0].isForResult()) {
            result = callbacks[0].runForResult();
        }
        else {
            callbacks[0].run();
        }

        if (callbacks.length > 1) {
            if (result != null) {
                callbacks[1].setParam(result);
            }

            callbacks[1].run();
        }
	}

	@Override
	public boolean isPointerDown() {
		// always the case for twl
		return true;
	}

	@Override
	public boolean isUiThread() {
		return true; // not correct, but will lead to greater protection of the UI thread
					 // however, core code should ensure this is a secondary check since
					 // it's intended for Android only (see interface method decl)
	}

}
