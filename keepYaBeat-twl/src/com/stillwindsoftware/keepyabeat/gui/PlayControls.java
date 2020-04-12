package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.gui.widgets.DragTrackingValueAdjusterInt;
import com.stillwindsoftware.keepyabeat.model.PlayerState;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Rhythms;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor.RhythmChangeBpmCommand;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.PlayedRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTracker;
import com.stillwindsoftware.keepyabeat.player.backend.IBeatTrackerCommunication;
import com.stillwindsoftware.keepyabeat.player.backend.TwlBeatTrackerCommunication;

import de.matthiasmann.twl.AnimationState;
import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.OptionBooleanModel;
import de.matthiasmann.twl.model.SimpleIntegerModel;

/**
 * The panel that owns contains the play/pause, stop, bpm, repeat controls for whatever is playing
 * @author tomas stubbs
 *
 */
public class PlayControls extends BoxLayout implements LibraryListener {

	public enum PlayingState {
		playing,
		paused,
		stopped
	}
	
	private PlayingState playingState = PlayingState.stopped;
	
    private Button playBtn;
    private Button stopBtn;
    private Button bpmBtn;
    private Button repeatsBtn;
	
    // data
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private PlayerState playerState = ((TwlPersistentStatesManager)resourceManager
			.getPersistentStatesManager()).getPlayerState();
    private Rhythm rhythm;
    private PlayedRhythmDraughter playedRhythmDraughter;
    // beat tracking for playback, created when play is hit
    private IBeatTrackerCommunication beatTrackerComm;
    
    // for tapping out bpm changes
    private long lastTapNano = -1L;
    		
	/**
	 * constructor 
	 */
	public PlayControls(PlayedRhythmDraughter playedRhythmDraughter) {
		super(BoxLayout.Direction.HORIZONTAL);
		setTheme("playControlsPanel");
		initModel(playedRhythmDraughter);
		layoutPlayOptions();
		// listen to changes to player state
		resourceManager.getListenerSupport().addListener(
				((TwlPersistentStatesManager)resourceManager.getPersistentStatesManager()), this);
	}

	/**
	 * Called from the constructor the first time, but also by player module when the rhythm changes.
	 * Handles the interfacing of rhythm with BeatTracker
	 * @param rhythm
	 */
	public void initModel(PlayedRhythmDraughter playedRhythmDraughter) {
		this.playedRhythmDraughter = playedRhythmDraughter;
		this.rhythm = playedRhythmDraughter.getRhythm();
		if (beatTrackerComm != null) {
			beatTrackerComm.setModel(playedRhythmDraughter);
		}
		
		// first time the widgets aren't set up, they get initial values in creation
		if (playBtn != null) {
			updateWidgetsIfNeeded(); 
		}
	}
	
	public void stopPlay() {
		playingState = PlayingState.stopped;
		stopBtn.setEnabled(false);
		playBtn.getAnimationState().setAnimationState(AnimationState.StateKey.get("playing"), false);
		beatTrackerComm.stopPlay();
		setRepeatNTimesText(playerState.getNTimes(), -1);
	}

	public PlayingState getPlayingState() {
		return playingState;
	}

	/**
	 * Call the dialog which will adjust the BPM
	 */
	private void adjustBpm() {

		// reset the nano for first tap
		lastTapNano = -1L;
		
		final Button tapButton = new Button();
		tapButton.setTheme("playerOptionsBpmTapper");
		tapButton.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.BPM_TAPPER_TOOLTIP));
		
		// model with callback for value adjuster
		SimpleIntegerModel bpmModel = new SimpleIntegerModel(Rhythms.MIN_BPM, Rhythms.MAX_BPM, rhythm.getBpm());
		final DragTrackingValueAdjusterInt bpmAdjuster = new DragTrackingValueAdjusterInt(bpmModel) {
				// override because want to remove the ugly java format exception
				@Override
				protected String validateEdit(String text) {
					super.validateEdit(text);
					// when tapping then enter a number, don't want to tap again and have min number set
					lastTapNano = -1L; 
					return null;
				}
				@Override
				protected void doDecrement() {
					setValue(getValue() - Rhythms.BPM_INCREMENTS);
				}
				@Override
				protected void doIncrement() {
					setValue(getValue() + Rhythms.BPM_INCREMENTS);
				}
				@Override
				protected void onDragUpdate(int dragDelta) {
					// default is a bit slow
					super.onDragUpdate(dragDelta*3);
				}
			};
		bpmAdjuster.setTheme("playerOptionsBPMAdjuster");
		bpmAdjuster.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.VALUE_ADJUSTER_TOOLTIP));

		// when the tap button is pressed have it update the adjuster
		tapButton.addCallback(new Runnable() {

			@Override
			public void run() {
				long nanoTime = System.nanoTime();
				
				// first tap do nothing
				if (lastTapNano != -1L) {
					float diff = nanoTime - lastTapNano;
					float diffSeconds = diff / BeatTracker.NANOS_PER_SECOND;

					int newBpm = (int) (60.0f / diffSeconds);
					newBpm = Math.min(Math.max(newBpm, Rhythms.MIN_BPM), Rhythms.MAX_BPM);
					bpmAdjuster.setValue(newBpm);
				}

				lastTapNano = nanoTime;
			}
		});

		// detect change to bpm and update the model if it's different
		// take care not to propagate a change if what happened was an undo, this would mean the state
		// is already the same value as the adjuster which was updated via the layout manager's listening
		bpmModel.addCallback(new Runnable() {
			@Override
			public void run() {
				// adjust beatTracker if there's one running so play follows bpm
				if (beatTrackerComm != null) {
					beatTrackerComm.updateTracking(bpmAdjuster.getValue());
				}
			}
		});

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
	            public void callback(ConfirmationPopup.CallbackReason reason) {
	            	if (reason == ConfirmationPopup.CallbackReason.OK) {
	            		new RhythmChangeBpmCommand(rhythm, bpmAdjuster, rhythm.getBpm()).execute();
	            	}
	            	else {
	            		// cancel, so put bpm back to rhythm's original value
	            		if (beatTrackerComm != null) {
	            			beatTrackerComm.updateTracking(rhythm.getBpm());
	            		}
	            	}
	            }
	        };

        ConfirmationPopup.showDialogConfirm(null, callback, this, 
        		resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_BPM_TITLE), 
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, tapButton, bpmAdjuster);

	}

	/**
	 * Offer a dialog with 3 options, either infinite, no repeat (once) or n times
	 */
	private void chooseRepeatOption() {
		BoxLayout box = new BoxLayout(BoxLayout.Direction.VERTICAL);
		box.setTheme("playerOptionsRepeatBox");
		
		// couldn't understand how to get the enum boolean model to work, so convert from an Integer model
		// and back again
		int modelInitValue = 0;
		switch (playerState.getRepeatOption()) {
		case Infinite : modelInitValue = 0; break;
		case NoRepeat: modelInitValue = 1; break;
		case RepeatNtimes : modelInitValue = 2; break;
		}

		final int INFINITE = 0;
		final int ONCE = 1;
		final int N_TIMES = 2;
		final SimpleIntegerModel optionModel = new SimpleIntegerModel(0, 2, modelInitValue);
		
		// infinite
		final ToggleButton infiniteRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.INFINITE_LABEL));
		infiniteRadio.setTheme("radiobutton");
		infiniteRadio.setModel(new OptionBooleanModel(optionModel, INFINITE));
		box.add(infiniteRadio);
		
		// none
		final ToggleButton onceRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.NO_REPEAT_LABEL));
		onceRadio.setTheme("radiobutton");
		onceRadio.setModel(new OptionBooleanModel(optionModel, ONCE));
		box.add(onceRadio);
		
		// n times
		final ToggleButton nTimesRadio = new ToggleButton(resourceManager.getLocalisedString(TwlLocalisationKeys.REPEAT_N_LABEL));
		nTimesRadio.setTheme("radiobutton");
		nTimesRadio.setModel(new OptionBooleanModel(optionModel, N_TIMES));
		box.add(nTimesRadio);

		// model with callback for value adjuster
		final SimpleIntegerModel nTimesModel = new SimpleIntegerModel(2, 99, playerState.getNTimes());
		final DragTrackingValueAdjusterInt nTimesAdjuster = new DragTrackingValueAdjusterInt(nTimesModel);
		nTimesAdjuster.setTheme("playerOptionsRepeatAdjuster");
		nTimesAdjuster.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.VALUE_ADJUSTER_TOOLTIP));
		nTimesAdjuster.setEnabled(optionModel.getValue() == N_TIMES);
		box.add(nTimesAdjuster);
		
		// enable or disable the nTimes adjuster depending on the selection
		optionModel.addCallback(new Runnable() {
			@Override
			public void run() {
				nTimesAdjuster.setEnabled(optionModel.getValue() == N_TIMES);
			}
		});

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
            public void callback(ConfirmationPopup.CallbackReason reason) {
            	if (reason == ConfirmationPopup.CallbackReason.OK) {
            		// change the repeats, if there's a looper already it needs to be updated too
            		RepeatOption selectedOption = null;
            		switch (optionModel.getValue()) {
            		case INFINITE: selectedOption = RepeatOption.Infinite; break;
            		case ONCE: selectedOption = RepeatOption.NoRepeat; break;
            		case N_TIMES: selectedOption = RepeatOption.RepeatNtimes; break;
            		}
            		
            		// only react to changes
            		if (playerState.getRepeatOption() != selectedOption) {
                		playerState.setRepeatOption(selectedOption);
                		if (selectedOption == RepeatOption.RepeatNtimes) {
                			playerState.setNTimes(nTimesModel.getValue());
                		}
            		}
            		else if (selectedOption == RepeatOption.RepeatNtimes 
            				&& nTimesModel.getValue() != playerState.getNTimes()) {
            			// in case all that changed was the n times
            			playerState.setNTimes(nTimesModel.getValue());
            		}
            	}
            }
        };

        ConfirmationPopup.showDialogConfirm(null, callback, this, 
        		resourceManager.getLocalisedString(TwlLocalisationKeys.CHANGE_REPEATS_TITLE), 
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, box);
	}

	/**
	 * Panel that contains the play controls (play/pause/stop etc)
	 */
	private void layoutPlayOptions() {
		// 3 boxes inside an outer box
		// box 1 is for play/pause and stop btns
		BoxLayout playPauseStopBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
		playPauseStopBox.setTheme("playPauseStopBox");
		add(playPauseStopBox);
		
		playBtn = new Button();
		playPauseStopBox.add(playBtn);
		playBtn.setTheme("playControlsPlayBtn");
		playBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.PLAY_PAUSE_TOOLTIP));
		playBtn.setCanAcceptKeyboardFocus(false);
		playBtn.addCallback(new Runnable() {
				@Override
				public void run() {
					if (playingState == PlayingState.stopped) {
						playingState = PlayingState.playing;
						stopBtn.setEnabled(true);
						setRepeatNTimesText(playerState.getNTimes(), 0);
					}
					else {
						if (playingState == PlayingState.playing) {
							playingState = PlayingState.paused;
						}
						else {
							playingState = PlayingState.playing;
						}
					}
					playBtn.getAnimationState().setAnimationState(AnimationState.StateKey.get("playing"), playingState == PlayingState.playing);
//					displayNumbersLabel.setVisible(true);
					if (beatTrackerComm == null) {
						beatTrackerComm = new TwlBeatTrackerCommunication(playedRhythmDraughter);
					}
				
					beatTrackerComm.togglePlayPause(playingState == PlayingState.playing);
				}
			});

		stopBtn = new Button();
		playPauseStopBox.add(stopBtn);
		stopBtn.setCanAcceptKeyboardFocus(false);
		stopBtn.setTheme("playControlsStopBtn");
		stopBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.STOP_PLAY_TOOLTIP));
		stopBtn.setEnabled(false);
		stopBtn.addCallback(new Runnable() {
				@Override
				public void run() {
					stopPlay();
				}
			});

		// box 2 only has bpm 
		BoxLayout bpmBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
		bpmBox.setTheme("playBpmBox");
		add(bpmBox);

		bpmBtn = new Button(Integer.toString(rhythm.getBpm()));
		bpmBox.add(bpmBtn);
		bpmBtn.setCanAcceptKeyboardFocus(false);
		bpmBtn.setTheme("playControlsBpmBtn");
		bpmBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.SET_BPM_TOOLTIP));
		bpmBtn.addCallback(new Runnable() {
			@Override
			public void run() {
				adjustBpm();
			}
		});

		// box 3 only has repeats btn 
		BoxLayout repeatsBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
		repeatsBox.setTheme("playRepeatsBox");
		add(repeatsBox);

		// cycle repeat options, overlay depends on animation state
		repeatsBtn = new Button();
		repeatsBox.add(repeatsBtn);
		repeatsBtn.setTheme("playControlsRepeatBtn");
		repeatsBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.SET_REPEAT_TOOLTIP));
		setCycleRepeatState();
		repeatsBtn.addCallback(new Runnable() {
			@Override
			public void run() {
				chooseRepeatOption();
			}			
		});
		
	}
	
	/**
	 * Called when clicking on the button, but also as part of undo processing when undoing a setting of repeat nTimes
	 * (see PlayerStateCommand)
	 */
	public void setCycleRepeatState() {
		RepeatOption repeatOption = playerState.getRepeatOption();
		repeatsBtn.getAnimationState().setAnimationState(AnimationState.StateKey.get("noRepeat"), repeatOption == RepeatOption.NoRepeat);
		repeatsBtn.getAnimationState().setAnimationState(AnimationState.StateKey.get("repeatN"), repeatOption == RepeatOption.RepeatNtimes);
		
		setRepeatNTimesText(playerState.getNTimes(), (beatTrackerComm == null ? -1 : beatTrackerComm.getRhythmIterations()));
	}

	/**
	 * Called from these 4 places:
	 * 1. setRepeatState() when user has changed that manually
	 * 2. playIterationsChanged() when that has been called via guiManager and beatTracker during
	 * playing
	 * 3. from the playBtn callback when starting to play first time
	 * 4. stopPlay()
	 * @param it
	 */
	private void setRepeatNTimesText(int nTimes, int it) {
		// display number?
		if (playerState.getRepeatOption() == RepeatOption.RepeatNtimes) {
			if (playingState != PlayingState.stopped && it < nTimes) {
				repeatsBtn.setText(Integer.toString(it+1)+"/"+nTimes);
			}
			else {
				repeatsBtn.setText(Integer.toString(nTimes));
			}
		}
		else {
			repeatsBtn.setText("");
		}
	}
	
	/**
	 * The iteration of the rhythm being played
	 * @param it
	 */
	public void playIterationsChanged(int it) {
		setRepeatNTimesText(playerState.getNTimes(), it);
	}
	

	@Override
	public void destroy() {
		// stop listening 
		resourceManager.getListenerSupport().removeListener(this);
		super.destroy();
	}

	/**
	 * Update the volume controls and other widgets with any changes
	 */
	private void updateWidgetsIfNeeded() {
		if (Integer.parseInt(bpmBtn.getText()) != rhythm.getBpm()) {
			bpmBtn.setText(Integer.toString(rhythm.getBpm()));
		}

		// reread from the state
		setCycleRepeatState();

	}
	
	@Override
	public void itemChanged() {
		// update the widgets on the panel that could be out of sync if there's an undo
		// and the volumes data which only gets its own updates via rhythm draughter's beat types
		updateWidgetsIfNeeded();
	}

	public Widget getHelpTarget() {
		return stopBtn;
	}
}