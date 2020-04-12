package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.gui.StandardTableList;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.BeatTypeXmlImpl;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.Sound;
import com.stillwindsoftware.keepyabeat.model.Sound.SoundStatus;
import com.stillwindsoftware.keepyabeat.model.SoundXmlImpl;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmBeatTypeCommand;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.SoundResource;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlListModel;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.ListModel;
import de.matthiasmann.twl.model.SimpleChangableListModel;
import de.matthiasmann.twl.model.TableSingleSelectionModel;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;

public class SoundCombo extends ComboBox<SoundXmlImpl> implements Runnable {
	private SoundComboClient soundComboClient;
	private int row = -1;
	private boolean populatedFromData = false;
	private TableSingleSelectionModel tableSelectionModel;
	private Widget parent;
	private static SoundsXmlImpl sounds = (SoundsXmlImpl) TwlResourceManager.getInstance().getLibrary().getSounds();
	private static TwlListModel<SoundsXmlImpl, SoundXmlImpl> soundsListModel = new TwlListModel<SoundsXmlImpl, SoundXmlImpl>(sounds);
	private static ListModel<SoundXmlImpl> internalSoundsListModel = new SimpleChangableListModel<SoundXmlImpl>(sounds.getInternalSounds());

	/**
	 * Construct sound combo using the default list model (all sounds)
	 * @param parent
	 * @param tableSelectionModel
	 */
    public SoundCombo(Widget parent, TableSingleSelectionModel tableSelectionModel) {
    	this(parent, tableSelectionModel, false);
    }

    /**
     * onlyInternalSounds determines the list model to use, default is all
     * @param parent
     * @param tableSelectionModel
     * @param onlyInternalSounds
     */
    public SoundCombo(Widget parent, TableSingleSelectionModel tableSelectionModel, boolean onlyInternalSounds) {
    	if (onlyInternalSounds) {
    		setModel(internalSoundsListModel);
    	}
    	else {
        	setModel(soundsListModel);
    	}
    	this.parent = parent;
		this.tableSelectionModel = tableSelectionModel;
        setTheme("soundCombo");  
        this.setComputeWidthFromModel(true);
        addCallback(this);
        this.setNoSelectionIsError(true);
    	setTooltipContent(TwlResourceManager.getInstance()
    			.getLocalisedString(TwlLocalisationKeys.CHANGE_SOUND_TOOLTIP));
    }
    
	@Override
	public void run() {
		int selected = getSelected();
		TwlResourceManager.getInstance().log(LOG_TYPE.verbose, this, 
				String.format("SoundCombo.callback: row=%s fired, populatedFromData previously=%s selected=%s"
						, row, populatedFromData, selected));
		
		SoundXmlImpl comboClientSound = soundComboClient.getSound();
		if (selected != -1) {
			populatedFromData = (getModel().getEntry(selected).equals(comboClientSound));
			// need to change the data if the combo has been changed manually
			if (!populatedFromData) {
				soundComboClient.soundChanged(getModel().getEntry(selected));
			}
		}
		else {
			// set selected to the index of the sound in the list model backing the combo
			setSelected(getListIndexOf(comboClientSound, getModel()));
			populatedFromData = false;
		}
		
		parent.requestKeyboardFocus();
	}

	/**
	 * Walk the list model to find the index of the sound
	 * @param sound
	 * @return
	 */
	public static int getListIndexOf(SoundXmlImpl sound, ListModel<SoundXmlImpl> listModel) {

		for (int i = 0; i < listModel.getNumEntries(); i++) {
			if (listModel.getEntry(i).equals(sound)) {
				return i;
			}
		}
		
		return -1;
		
	}
	
    @Override
	protected void keyboardFocusGained() {
		super.keyboardFocusGained();
		if (tableSelectionModel != null) {
			tableSelectionModel.setSelection(row, row);
		}
	}

	@Override
	protected void handleRightClick() {
		StandardTableList.handleRightClickOnRow(this, row);
	}

	void setData(final SoundComboValue data) {

        this.row = data.getValue();
        this.soundComboClient = data.getSoundComboClient();
        
        // make sure the list model is up to date
        try {
			setSelected(getListIndexOf(soundComboClient.getSound(), getModel()));
		} catch (IllegalArgumentException e) {
    		BeatType beatType = soundComboClient.getBeatType();
    		Sound mainSound = beatType.getSound();
    		TwlResourceManager.getInstance().log(LOG_TYPE.error, this, 
    				String.format("SoundCombo.setData: tried to setSelected and got error (selected=%s) beatType %s sound %s "
    						, getListIndexOf(data.getSoundComboClient().getSound(), getModel())
    						, beatType.getName(), mainSound == null ? null : mainSound.getKey()));
			e.printStackTrace();
		}
        
        int selected = getSelected();
        
        // there shouldn't be a case where no sound is selected, it means the sound is missing from the library
        // but can happen when someone tampers with the library file
        if (selected == -1) {
        	BeatTypeXmlImpl beatType = soundComboClient.getBeatType();
    		Sound mainSound = beatType.getSound();
    		TwlResourceManager.getInstance().log(LOG_TYPE.error, this, 
    				String.format("SoundCombo.setData: missing sound in sound combo for beatType %s sound %s (key=%s)"
    						, beatType.getName(), mainSound == null ? null : mainSound.getKey(), beatType.getSoundKeyFromLoad()));

    		// not sure why, but sometimes the sound is there and it doesn't find it, perhaps the order of listening means
    		// the model isn't yet updated?
    		if (mainSound != null && sounds.lookup(mainSound.getKey()) != null || mainSound == null && sounds.lookup(beatType.getSoundKeyFromLoad()) != null) {
        		TwlResourceManager.getInstance().log(LOG_TYPE.error, this, "SoundCombo.setData: the sound is in the library, so have it re-layout");
        		TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(new PendingTask("redo sound combo set data after all upto date") {
					@Override
					protected void updateComplete() {
						complete = true;
						if (complete) {
							TwlResourceManager.getInstance().log(LOG_TYPE.error, this, ("SoundCombo.setData.PendingTask.complete: going around again"));
							setData(data);
						}
					}
				});
    		}
        }
    }

	@Override
	protected void layout() {
		super.layout();

		// error will show red label
		Sound comboSound = soundComboClient.getSound();
    	SoundResource sr = comboSound == null ? null : comboSound.getSoundResource();
     	getAnimationState().setAnimationState(StateKey.get("error")
     			, sr == null || sr.getStatus() != SoundStatus.LOADED_OK);        
	}

	/**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class SoundComboCellWidgetCreator extends BorderedCellWidgetCreator {
        private SoundComboValue data;
    	private TableSingleSelectionModel tableSelectionModel;
    	private Widget parent;
        
		public SoundComboCellWidgetCreator(Widget parent, TableSingleSelectionModel tableSelectionModel) {
			this.tableSelectionModel = tableSelectionModel;
			this.parent = parent;
		}

        public String getTheme() {
        	return "soundcombocellrenderer";
        }

        /**
         * Update or create the Sound Combo widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	SoundCombo soundCombo = (SoundCombo)existingWidget;
            if(soundCombo == null) {
            	 // sound combo with list model depending on type of list
            	soundCombo = new SoundCombo(parent, tableSelectionModel, data.getSoundComboClient() instanceof BeatTypeFallbackSoundClient);
            }
            
            soundCombo.setData(data);
            return soundCombo;
        }

        public void setCellData(int row, int column, Object data) {
            // we have to remember the cell data for the next call of updateWidget
           this.data = (SoundComboValue)data;
        }

    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class SoundComboValue {
    	private SoundComboClient soundComboClient;
    	private int value;

    	public SoundComboValue() {
         }
    	
    	public void setValues(int value, SoundComboClient soundComboClient) {
    		this.value = value;
        	this.soundComboClient = soundComboClient;
         }
    	
    	SoundComboClient getSoundComboClient() {
    		return soundComboClient;
    	}
    	
    	int getValue() {
    		return value;
    	}
    }
    
    /**
     * Interface which permits different classes to use the sound combo widget.
     * Concrete sub-classes below it.
     */
    public interface SoundComboClient {
    	public SoundXmlImpl getSound();
    	public void soundChanged(SoundXmlImpl newSound);
    	public BeatTypeXmlImpl getBeatType();
    }
    
    public static class BeatTypeSoundClient implements SoundComboClient {

    	private final BeatTypeXmlImpl beatType;
    	
		public BeatTypeSoundClient(BeatTypeXmlImpl beatType) {
			this.beatType = beatType;
		}

		@Override
		public SoundXmlImpl getSound() {
			return (SoundXmlImpl) beatType.getSound();
		}

		@Override
		public void soundChanged(SoundXmlImpl newSound) {
			new BeatTypesAndSoundsCommand.ChangeBeatSound(beatType, newSound, null).execute();
		}

		@Override
		public BeatTypeXmlImpl getBeatType() {
			return beatType;
		}    	
    }
    
    public static class BeatTypeFallbackSoundClient implements SoundComboClient {

    	private final BeatTypeXmlImpl beatType;
    	
		public BeatTypeFallbackSoundClient(BeatTypeXmlImpl beatType) {
			this.beatType = beatType;
		}

		@Override
		public SoundXmlImpl getSound() {
			return (SoundXmlImpl) beatType.getFallbackSound();
		}

		@Override
		public void soundChanged(SoundXmlImpl newSound) {
			new BeatTypesAndSoundsCommand.ChangeBeatFallbackSound(beatType, newSound).execute();
		}    	

		@Override
		public BeatTypeXmlImpl getBeatType() {
			return beatType;
		}    	
    }
    
    public static class RhythmBeatTypeSoundClient implements SoundComboClient {

    	private final RhythmBeatType rBeatType;
    	
		public RhythmBeatTypeSoundClient(RhythmBeatType rBeatType) {
			this.rBeatType = rBeatType;
		}

		@Override
		public SoundXmlImpl getSound() {
			return (SoundXmlImpl) rBeatType.getSound();
		}

		@Override
		public void soundChanged(SoundXmlImpl newSound) {
			new RhythmBeatTypeCommand.ChangeBeatSound(rBeatType, newSound).execute();
		}
    	
		@Override
		public BeatTypeXmlImpl getBeatType() {
			return (BeatTypeXmlImpl) rBeatType.getBeatType();
		}    	
    }

}
	
