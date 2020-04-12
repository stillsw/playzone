package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.control.UndoableCommandController;
import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.BeatTypesListOfValues.BeatTypeSelectReceiver;
import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup.ConfirmPopupWindow;
import com.stillwindsoftware.keepyabeat.gui.HelpTipModel.HelpTipTargetPoint;
import com.stillwindsoftware.keepyabeat.gui.SlidingAnimatedPanel.Direction;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton.ColourButtonValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton.DragBeatTypeColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.DragTrackingValueAdjusterInt;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmSetNameConfirmation.SaveNewEditConfirmation;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypes;
import com.stillwindsoftware.keepyabeat.model.EditRhythm;
import com.stillwindsoftware.keepyabeat.model.EditRhythm.RhythmState;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor.RhythmSaveEditCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmEditCommand;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.GuiManager.BeatTypeSelectedCallback;
import com.stillwindsoftware.keepyabeat.platform.RhythmEditor;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.player.BeatEditActionHandler;
import com.stillwindsoftware.keepyabeat.player.EditRhythmDraughter;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.Color;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.DialogLayout.Group;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.SimpleIntegerModel;

/**
 * Front end to the rhythm editing to provide all the services needed by the core such as mouse
 * events (such as dragging, right-button clicks) and to act as a dialog edit session. It also contains
 * the canvas for drawing on, and the widgets needed (play controls, volume settings, beat types pick list)
 * @author tomas stubbs
 */
public class TwlRhythmEditor extends AbstractRhythmPlayer implements RhythmEditor {

	// passed in constructor
	private Runnable closeCallback;
	private EditRhythm editRhythm;
	// uyy number of undos allowed
	private UndoableCommandController undoableCommandController = new UndoableCommandController(TwlResourceManager.getInstance(),
			TwlResourceManager.getInstance().getUndoHistoryLimit(), -1);

	private PopupWindow popup; // the dialog container for this

	private boolean startingElementsLaidOut = false;
	protected boolean isCancellingChanges = false; // only set when user confirms to cancel changes

	// the top bar where the name/buttons are
	private DialogLayout topBarLayout;
	private Button okBtn;
	private Button dragBeatTypeBtn;
	private Button quickEditBtn;
	// button created only when showing help, to be the target for the sub-beats help
	private Button zoomHelpTargetBtn;
	
	/**
	 * Construct a new rhythm editor, if it's starting up as first module everything slides
	 * into place like rhythm player. But if it's over another module everything is in place already
	 * (the underlying screen is kept there)
	 * Called from 3 places:
	 * a) rhythmsList edit rhythm popup menu option
	 * b) player top widgets panel edit button
	 * c) auto startup when edit is not completed so coming here first called from ModuleManager.restoreLastOpenModule()
	 */
	public TwlRhythmEditor(boolean isFromStartup, EditRhythm editRhythm, Runnable closeCallback) {
		this.closeCallback = closeCallback;
		setTheme("rhythmEditor");

		// unless from startup already, store the rhythm in persistent state
		if (!isFromStartup) {
			((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()).setEditRhythm(editRhythm);
		}
		
		initModel(editRhythm);
		initDraughting();
		makeDraughtingLayout();
		makeTopWidgetsLayout();
		makeSettingsLayout();
		makePlayControlsLayout();
		
		// show auto help if not shown before
		TwlPersistentStatesManager persistenceManager = (TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();
		final String autoHelpKey = HelpTipsController.HelpPage.rhythmEditor.name();
		if (!persistenceManager.isAutoHelpSeen(autoHelpKey)) {
			resourceManager.getPendingTasksScheduler().addTask(new PendingTask("wait for sliding panels to open") {
				@Override
				protected void updateComplete() {
					complete = canvasSlidingPanel.isFullyOpen() && topWidgetsSlidingPanel.isFullyOpen() && playControlsSlidingPanel.isFullyOpen();
					if (complete) {
						showHelp(autoHelpKey);
					}
				}
			});
		}
	
		// put this into a blocking dialog popup
		constructDialog();
		
		// register with gui manager
		resourceManager.getGuiManager().setRhythmEditor(this);
		
		// listen for changes
		setListeners(true);
	}

	/**
	 * Show help for this page, called from the help button and also from constructor
	 * if auto help has not been seen before
	 * @param autoHelpKey
	 */
	private void showHelp(String autoHelpKey) {
		ArrayList<HelpTipModel> helpTips = new ArrayList<HelpTipModel>();
		if (autoHelpKey != null) {
			helpTips.add(new HelpTipModel(autoHelpKey, true, TwlLocalisationKeys.HELP_EDITOR_DIALOG_BUTTONS_TEXT, okBtn, HelpTipTargetPoint.TopRight));
		}

		// next set
		helpTips.add(new HelpTipModel(null, true, TwlLocalisationKeys.HELP_EDITOR_DRAG_BEAT_TYPE_TEXT, dragBeatTypeBtn, HelpTipTargetPoint.Top));

		// next set needs the zoom help target
		MutableRectangle zoomSpot = ((EditRhythmDraughter)rhythmDraughter).getZoomIconRectangle(0);
		// it's possible to return null if there isn't room for an image 
		if (zoomSpot != null) {
			zoomHelpTargetBtn = new Button();
			zoomHelpTargetBtn.setTheme("zoomHelp");
			zoomHelpTargetBtn.setSize((int)zoomSpot.getW(), (int)zoomSpot.getH());
			zoomHelpTargetBtn.setPosition((int)zoomSpot.getX()+playerRhythmCanvas.getX(), (int)zoomSpot.getY()+playerRhythmCanvas.getY());
			add(zoomHelpTargetBtn);
			
			helpTips.add(new HelpTipModel(null, false, TwlLocalisationKeys.HELP_EDITOR_DRAG_SUB_BEAT_TEXT, zoomHelpTargetBtn, HelpTipTargetPoint.Bottom));
		}
		
		// next set
		helpTips.add(new HelpTipModel(null, true, TwlLocalisationKeys.HELP_EDITOR_MAGIC_WAND_TEXT, quickEditBtn, HelpTipTargetPoint.Top));

		// send the whole lot to the controller
		new HelpTipsController(UserBrowserProxy.Key.editor
				, autoHelpKey != null ? TwlLocalisationKeys.HELP_FIRST_SEEN_EDITOR_EXTRA_TEXT : null
				, autoHelpKey == null
				, new Runnable() {
						@Override
						public void run() {
							if (zoomHelpTargetBtn != null) {
								removeChild(zoomHelpTargetBtn);
								zoomHelpTargetBtn = null;
							}
						}
					}
				, (HelpTipModel[]) helpTips.toArray(new HelpTipModel[helpTips.size()]));
	}

	/**
	 * listen to changes to the rhythm and to beat types, because colour change on volume controls
	 * is not saved to the rhythm, but is global to beat types
	 * @param on
	 */
	private void setListeners(boolean on) {
		if (on) {
			resourceManager.getListenerSupport().addListener(resourceManager.getLibrary().getBeatTypes(), this);
			resourceManager.getListenerSupport().addListener(editRhythm, this);		
			// listen to changes to player state
			resourceManager.getListenerSupport().addListener(
					((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()), this);
		}
		else {
			resourceManager.getListenerSupport().removeListener(this);
		}
	}

	/**
	 * Maintains its own undo stack
	 */
	@Override
	public UndoableCommandController getUndoableCommandController() {
		return undoableCommandController;
	}

	@Override
	public EditRhythm getEditRhythm() {
		return editRhythm;
	}

	/**
	 * Need to draw dragged/flying beats to the whole screen (not clipped by canvas)
	 */
	@Override
	protected void paintChildren(GUI gui) {
		super.paintChildren(gui);
		if (rhythmDraughter != null) {
			rhythmDraughter.drawAfterSubZone();
		}
	}

	/**
	 * Protect top section where the confirm/cancel buttons are
	 */
	@Override
	protected int getSettingsListUpperMostY() {
		return topBarLayout.getHeight(); 
	}

	@Override
	protected void layout() {
		// once canvas and volume settings list is laid out no need to repeat it
		if (!startingElementsLaidOut) {
			if (playRhythmVolumeControls.isLayoutInitComplete()) {
				sizeAndPlacePlayControls();
				sizeAndPlaceSettingsList();
				// time to resume the canvas
				if (canvasSlidingPanel.isStartingUpHidden()) {
			        canvasSlidingPanel.resume();
				}
				
				startingElementsLaidOut = true;
			}
		}
		else {
			// always resize the settings and controls, beat edits can change the values in the list
			// this is the safest way to ensure it is always laid out correctly
			// also now allow Display resizing
			sizeAndPlacePlayControls();
			sizeAndPlaceSettingsList();
		}
	}

    public void constructDialog() {
        // popup is size of the desktop and it centres the frame
        popup = new PopupWindow(KeepYaBeat.root) {
			@Override
			protected void layout() {
				setSize(KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight());
				TwlRhythmEditor.this.setSize(getInnerWidth(), getInnerHeight());
				sizeAndPlaceTopWidgets();
				sizeAndPlaceCanvas();
			}

			/**
			 * Send back event directly to the canvas, it will pass it to the draughter
			 */
			@Override
			protected boolean handleEventPopup(Event evt) {
				boolean handledEvent = false;
				
				if (!handledEvent && playerRhythmCanvas != null) {
					if (evt.isKeyPressedEvent() && evt.hasKeyChar() && evt.getKeyCode() == Event.KEY_ESCAPE) {
						handledEvent = playerRhythmCanvas.triggeredBackEvent();
					}
				}

				if (handledEvent) {
					return true;
				}
				else {
					return super.handleEventPopup(evt);
				}
			}
			
        };

        popup.setTheme("");
        popup.add(this);
        popup.setCloseOnClickedOutside(false);
        popup.setCloseOnEscape(false);
        popup.openPopup();
    }

	@Override
	public void initModel(Rhythm rhythm) {
		this.editRhythm = (EditRhythm) rhythm;		
	}

	@Override
	protected void sizeAndPlaceTopWidgets() {
		topWidgetsLayout.setSize(getInnerWidth(), Math.max(getInnerHeight() - playerRhythmCanvas.getHeight(), 0));
		topWidgetsSlidingPanel.adjustSize();
		topWidgetsSlidingPanel.setPosition(getInnerX(), getInnerY());	
	}

	@Override
	protected void initDraughting() {
		// the draughter
		rhythmDraughter = new EditRhythmDraughter(editRhythm); 

		// drawing canvas area
		playerRhythmCanvas = new EditRhythmCanvas((EditRhythmDraughter) rhythmDraughter);
		
		// init the settings list
		playRhythmVolumeControls = new PlayerRhythmBeatTypesList(rhythmDraughter.getRhythmBeatTypes());
		playRhythmVolumeControls.init();
	}

	/**
	 * Local (temporary for this dialog) undo stack button
	 * @return
	 */
	protected Button makeUndoButton() {
		final Button undoBtn = new Button() {
					@Override
					public Object getTooltipContent() {
						return undoableCommandController.getUndoDesc();
					}
				};
		undoBtn.setTheme("undoBtn");
		undoBtn.setCanAcceptKeyboardFocus(false);
		undoBtn.setEnabled(false); // start disabled
		undoBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						undoableCommandController.undoLastCommand();
						undoBtn.setTooltipContent(undoableCommandController.getUndoDesc());
					}			
				});
		undoBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.TOGGLE_SETTINGS));
		undoBtn.setSize(undoBtn.getMaxWidth(), undoBtn.getMaxHeight());

		return undoBtn;
	}

	/**
	 * Make a layout for the top set of widgets (buttons, labels)
	 * and put it into a sliding panel
	 */
	@Override
	protected void makeTopWidgetsLayout() {

		// start with the panel that shows the name, and ok/cancel buttons (top bar)
		topBarLayout = new DialogLayout();
		topBarLayout.setTheme("rhythmEditorTopBarLayout");

		Label rhythmNameLabel = new Label();
		rhythmNameLabel.setTheme("whiteLabel");
		rhythmNameLabel.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.EDIT_RHYTHM_LABEL));

		Label rhythmName = new Label();
		rhythmName.setTheme("rhythmName");
		rhythmName.setText(editRhythm.getName());
		
		//Button
		okBtn = makeOkButton();
		Button cancelBtn = makeCancelButton();
		
		topBarLayout.setHorizontalGroup(topBarLayout.createSequentialGroup()
				.addWidget(rhythmNameLabel)
				.addWidget(rhythmName)
				.addGap() // add a gap that takes up all the available space in between
				.addWidget(okBtn)
				.addWidget(cancelBtn));

		topBarLayout.setVerticalGroup(topBarLayout.createParallelGroup()
				.addWidget(rhythmNameLabel)
				.addWidget(rhythmName)
				.addWidget(okBtn)
				.addWidget(cancelBtn));
		
		Button toggleSettingsBtn = makeSettingsToggleButton();
		final Button undoBtn = makeUndoButton();

		// put a callback on the controller to enable/disable the undo button 
		undoableCommandController.setStackChangeCallback(new Runnable() {
					@Override
					public void run() {
						undoBtn.setEnabled(undoableCommandController.hasCommands());
					}			
				});
		
		quickEditBtn = makeQuickEditButton();
		dragBeatTypeBtn = makeDragBeatTypeButton();
		Button helpBtn = makeHelpButton();

		topWidgetsLayout = new DialogLayout();
		topWidgetsLayout.setTheme("topWidgetsLayout");
		
        // enclosing groups
        Group hGroup = topWidgetsLayout.createParallelGroup();
        Group vGroup = topWidgetsLayout.createSequentialGroup();

        hGroup.addWidget(topBarLayout);
        
        // quick edit button is laid out to the left, so group it with gap on the right
        hGroup.addGroup(topWidgetsLayout.createSequentialGroup()
				.addGap(DialogLayout.SMALL_GAP)
				.addWidget(dragBeatTypeBtn)
				.addWidget(quickEditBtn)
				.addWidget(undoBtn)
				.addWidget(helpBtn)
				.addGap()
				.addWidget(toggleSettingsBtn)
				.addGap(DialogLayout.LARGE_GAP));

        // each group in vertical group is a horizontal line going down the layout
        // so the addGap() between the 1st and 2nd groups increases the gap between the top line
        // and the next line of widgets
        vGroup
	        .addWidget(topBarLayout)
			.addGap(DialogLayout.LARGE_GAP)
			.addGroup(topWidgetsLayout.createParallelGroup()
				.addWidget(toggleSettingsBtn)
				.addWidget(undoBtn)
				.addWidget(quickEditBtn)
				.addWidget(dragBeatTypeBtn)
				.addWidget(helpBtn)
				);
    
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
	 * Called by ok/cancel
	 */
	private void closeEditor() {
		popup.removeChild(this);
		popup.closePopup();
		
		// stop listening
		setListeners(false);
		
		// run callback if there is one
		if (closeCallback != null) {
			closeCallback.run();
		}
		
		// de-reference gui manager (this will stop the ability to access undoable commands
		// cancel/undo whole stack must already be completed by now)
		resourceManager.getGuiManager().setRhythmEditor(null);
		
		// remove resources, clear saved prefs/data
		undoableCommandController.setStackChangeCallback(null);
		((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()).setEditRhythm(null);
	}

	/**
	 * Test for any changes to the rhythm or in the undo stack, and if there are any popup confirmation
	 */
	private void cancelPressed() {

		if (undoableCommandController.hasCommands() 
			|| editRhythm.getCurrentState() == RhythmState.Changed
			|| editRhythm.getCurrentState() == RhythmState.Unsaved) {
			
			CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
	            public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
	            		// before closing, undo anything in the stack
	            		// set the flag so any commands that are beat edits don't
	            		// bother to execute (the changes to editRhythm are being negated anyway)
	            		isCancellingChanges  = true;
	            		undoableCommandController.undoWholeStackNoGui(); 
	            		closeEditor();
	            	}
	            }
	        };
					
	        ConfirmationPopup.showDialogConfirm(null, callback, TwlRhythmEditor.this
	        		, resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_CANCEL_EDITS_TITLE)
	        		, ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false
	        		, new Label(resourceManager.getLocalisedString((TwlLocalisationKeys.CONFIRM_CANCEL_EDITS_BODY))));
		}
		else {
			// no changes, just close
    		closeEditor();
		}
	}
	
	/**
	 * Show dialog to enter quick edit of rhythm length options
	 */
	private void quickEditPressed() {

		// button for beat type selector, defaults to the selected beat type
		BeatType playerStateBeatType = playerState.getBeatType();
		BeatTypes beatTypes = resourceManager.getLibrary().getBeatTypes();
		if (playerStateBeatType == null || beatTypes.lookup(playerStateBeatType.getKey()) == null) {
			playerStateBeatType = (BeatType) beatTypes.lookup(BeatTypes.DefaultBeatTypes.normal.getKey());
			playerState.setBeatType(playerStateBeatType);
		}
		final ColourButtonValue data = new ColourButtonValue();
		data.setValues(-1, playerStateBeatType);
		final ColourButton beatTypeColourButton = new ColourButton(data);
    	// override the default theme in its constructor
		beatTypeColourButton.setTheme("beatTypeColourSelectorButton");
		
        // need a popup window to be able to also pop up the beat types list of values 
        final ConfirmPopupWindow confirmPopupWindow = new ConfirmPopupWindow(true);

		beatTypeColourButton.addCallback(new Runnable() {
			@Override
			public void run() {
				((TwlGuiManager)resourceManager.getGuiManager()).popupBeatTypeLov(new BeatTypeSelectedCallback() {
					@Override
					public void beatTypeSelected(BeatType beatType) {
						// beat type different
						if (!beatType.equals(beatTypeColourButton.getBeatType())) {
							ColourButton.ColourButtonValue cbValue = new ColourButton.ColourButtonValue();
							cbValue.setValues(-1, beatType);
							beatTypeColourButton.setData(cbValue);
							beatTypeColourButton.setBackgroundColour(Color.parserColor(beatType.getColour().getHexString()));
							beatTypeColourButton.requestKeyboardFocus();
						}
					}
				}, confirmPopupWindow, beatTypeColourButton);
			}
        });
		
		// model for value adjuster
		final int initValue = rhythmDraughter.getBeatTree().getFullBeats().size();
		SimpleIntegerModel numBeatsModel = new SimpleIntegerModel(Rhythms.MIN_BEAT, Rhythms.MAX_RHYTHM_LEN, initValue);
		
		final DragTrackingValueAdjusterInt numBeatsAdjuster = new DragTrackingValueAdjusterInt(numBeatsModel);
		numBeatsAdjuster.setTheme("numBeatsAdjuster");

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
            public void callback(ConfirmationPopup.CallbackReason reason) {
            	if (reason == ConfirmationPopup.CallbackReason.OK) {
            		
            		// change num beats if needed
            		if (initValue != numBeatsAdjuster.getValue()) {						
						new RhythmEditCommand.ChangeNumberOfFullBeats(numBeatsAdjuster.getValue(), 
								beatTypeColourButton.getBeatType()).execute();
						
						rhythmDraughter.rhythmUpdated();
            		}
            	}
            }
        };
        
        ConfirmationPopup.showDialogConfirm(confirmPopupWindow, null, callback, TwlRhythmEditor.this, null
        		, resourceManager.getLocalisedString(TwlLocalisationKeys.QUICK_EDIT_RHYTHM_TITLE)
        		, ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, true, null
        		, ConfirmationPopup.NO_BUTTON, false, beatTypeColourButton, numBeatsAdjuster);
		
	}
	
	/**
	 * Allow choice of beat type from lov and then pass the type to the editor
	 */
	private void dragNewBeatPressed() {
		// similarly to when pressing the button on the quick edit dialog, but here the lov
		// is immediately displayed and positioned by the mouse
		final BeatTypesListOfValues beatTypesLov = new BeatTypesListOfValues(new Runnable() {
			@Override
			public void run() {
				Location2Df pointerLoc = resourceManager.getGuiManager().getPointerLocation();
				rhythmDraughter.getBeatActionHandler().handlePointerRelease(pointerLoc.getX(), pointerLoc.getY());
			}			
		}); 
		final PopupWindow lovPopupWindow = new PopupWindow(KeepYaBeat.root) {

			@Override
			protected void layout() {
				super.layout();
				// once finished sizing it can lay it out
				if (beatTypesLov.isLayoutInitComplete()) {
					Location2Df pointerLoc = resourceManager.getGuiManager().getPointerLocation();
					int xPos = (int) pointerLoc.getX();
					int yPos = (int) pointerLoc.getY();

					beatTypesLov.adjustSize();
					
					int furthestX = TwlRhythmEditor.this.getInnerX() + TwlRhythmEditor.this.getInnerWidth() - beatTypesLov.getWidth();
					int furthestY = TwlRhythmEditor.this.getInnerY() + TwlRhythmEditor.this.getInnerHeight() - beatTypesLov.getHeight();

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

				// it's a drag button widget
				ColourButton.DragBeatTypeColourButton dragButton = (DragBeatTypeColourButton) beatSelectionWidget;
				// get the mouse pos 
				Location2Df mousePos = resourceManager.getGuiManager().getPointerLocation();
				// and get a rectangle of the position of the beat type button
				MutableRectangle drawnDimension = 
						new MutableRectangle(dragButton.getX(), dragButton.getY(), dragButton.getWidth(), dragButton.getHeight());
				
				BeatEditActionHandler actionHandler = (BeatEditActionHandler) rhythmDraughter.getBeatActionHandler();
				actionHandler.dragNewBeat(beatType, dragButton.getMouseButtonDownLoc()
						, mousePos.getX(), mousePos.getY(), drawnDimension);
				
				lovPopupWindow.closePopup();
			}
		});

		beatTypesLov.init();

		lovPopupWindow.setTheme("confirmPopup");
		lovPopupWindow.add(beatTypesLov);
		lovPopupWindow.openPopup();

	}
	
	/**
	 * Existing rhythm is saved directly, new one pops up a confirmation box to enter
	 * name/tags
	 */
	private void okPressed() {
		if (editRhythm.getCurrentState() == RhythmState.New || editRhythm.getCurrentState() == RhythmState.Unsaved) {
			new SaveNewEditConfirmation(editRhythm, this, new Runnable() {
					@Override
					public void run() {
						closeEditor();
					}		
				})
			.saveChangesIfConfirmed();
		}
		else {
			new RhythmSaveEditCommand(editRhythm).execute();
			closeEditor();
		}
	}
	
	private Button makeOkButton() {
		Button okBtn = new Button();
		okBtn.setTheme("okBtn");
		okBtn.setCanAcceptKeyboardFocus(false);
		okBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						okPressed();
					}			
				});
		okBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_SAVE_TOOLTIP));

		return okBtn;
	}

	private Button makeQuickEditButton() {
		Button qeBtn = new Button();
		qeBtn.setTheme("quickEditRhythmBtn");
		qeBtn.setCanAcceptKeyboardFocus(false);
		qeBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						quickEditPressed();
					}			
				});
		qeBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.QUICK_EDIT_TOOLTIP));

		return qeBtn;
	}
	
	private Button makeDragBeatTypeButton() {
		Button dragBeatTypeBtn = new Button();
		dragBeatTypeBtn.setTheme("newBtn");
		dragBeatTypeBtn.setCanAcceptKeyboardFocus(false);
		dragBeatTypeBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						dragNewBeatPressed();
					}			
				});
		dragBeatTypeBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.ADD_OR_CHANGE_BEAT_TYPE));
		dragBeatTypeBtn.setSize(dragBeatTypeBtn.getMaxWidth(), dragBeatTypeBtn.getMaxHeight());
		
		return dragBeatTypeBtn;
	}

	private Button makeCancelButton() {
		Button cancelBtn = new Button();
		cancelBtn.setTheme("cancelBtn");
		cancelBtn.setCanAcceptKeyboardFocus(false);
		cancelBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						cancelPressed();
					}			
				});
		cancelBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CANCEL_SAVE_TOOLTIP));

		return cancelBtn;
	}

	@Override
	public PendingTask openRhythmBeatTypes() {
		if (settingsSlidingPanel != null && settingsSlidingPanel.isHidden()) {
			return settingsSlidingPanel.getPendingTaskToToggleShow(true);
		}
		else {
			return null;
		}
	}

	@Override
	public EditRhythmDraughter getEditRhythmDraughter() {
		return (EditRhythmDraughter) rhythmDraughter;
	}

	@Override
	public void itemChanged() {
		rhythmDraughter.updateBeatsData(true);
		rhythmDraughter.arrangeRhythm();
		refreshRhythmBeatTypes();
		
		// read settings state 
		boolean settingsActive = playerState.isVolumesActive();
		if (settingsSlidingPanel != null && settingsSlidingPanel.isCurrentStateHidden() != !settingsActive) {
			settingsSlidingPanel.setShow(settingsActive);
		}

	}

	/**
	 * For the EditRhythmDraughter to inform the RhythmEditor that changes may have
	 * occurred to the rhythm beat types and it should be refreshed
	 */
	@Override
	public void refreshRhythmBeatTypes() {
		playRhythmVolumeControls.modelUpdated(rhythmDraughter.getRhythmBeatTypes());
		playControls.itemChanged();		
	}

	/**
	 * Only set when user confirms to cancel
	 * Used by RhythmEditCommands to avoid unnecessary undos
	 */
	@Override
	public boolean isCancelled() {
		return isCancellingChanges;
	}

	@Override
	public void resetRhythmAfterError() {
		// TODO do something here
		// not implemented in twl because it's here to trap an error that never usually happens
		resourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_SAVE_ERROR, true, null);
	}

//	@Override
//	public boolean isBlocking() {
//		return true;
////		(rhythmEditorModule != null 
////				? ((TwlRhythmEditor)rhythmEditorModule).hasOpenPopups()
////				: KeepTheBeat.root.hasOpenPopups());
//	}

}
