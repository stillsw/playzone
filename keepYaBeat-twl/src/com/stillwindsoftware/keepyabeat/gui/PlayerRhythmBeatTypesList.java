package com.stillwindsoftware.keepyabeat.gui;

import java.util.TreeSet;

import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton.ColourButtonValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo;
import com.stillwindsoftware.keepyabeat.gui.widgets.SoundCombo.SoundComboValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.ToggleDisplayNumbersButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.ToggleDisplayNumbersButton.DisplayNumbersButtonValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.VolumeSlider;
import com.stillwindsoftware.keepyabeat.gui.widgets.VolumeSlider.VolumeSliderValue;
import com.stillwindsoftware.keepyabeat.model.BeatTypesXmlImpl;
import com.stillwindsoftware.keepyabeat.model.RhythmBeatType;
import com.stillwindsoftware.keepyabeat.model.SoundsXmlImpl;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.twlModel.PlayBeatTypesTableModel;

import de.matthiasmann.twl.Widget;

/**
 * The panel which contains the table of beat types used in the rhythm.
 * Also has the controls to change volume/colour/sound
 * @author tomas stubbs
 *
 */
public class PlayerRhythmBeatTypesList extends StandardTableList implements LibraryListener { // DialogLayout {

    private TreeSet<RhythmBeatType> rhythmBeatTypes;

	public PlayerRhythmBeatTypesList(TreeSet<RhythmBeatType> rhythmBeatTypes) {
		this.rhythmBeatTypes = rhythmBeatTypes;
	}
	
	/**
	 * When there's a change of model (new rhythm loaded), this class notifies the tableModel
	 * when it is informed by the caller.
	 * @param rhythmBeatTypes
	 */
	public void modelUpdated(TreeSet<RhythmBeatType> rhythmBeatTypes) {
		((PlayBeatTypesTableModel)tableModel).modelUpdated(rhythmBeatTypes);
		// make sure the scrollpane re-lays out
		resetDimensionsComputed();
		invalidateLayout();
//System.out.println("PlayerRhythmBeatTypesList.modelUpdated#1: invalidating layout");			
	}
	
	/**
	 * When there's an update to the model, this class notifies the tableModel
	 * when it is informed by the caller.
	 */
	public void modelUpdated() {
		((PlayBeatTypesTableModel)tableModel).modelUpdated();
		// make sure the scrollpane re-lays out
		resetDimensionsComputed();
		invalidateLayout();
//try {
//	System.out.println("PlayerRhythmBeatTypesList.modelUpdated#2: invalidating layout");
//	throw new Exception ();
//} catch (Exception e) {
//	// TODO Auto-generated catch block
//	e.printStackTrace();
//}			
	}
	
	@Override
	protected void doLayout() {
		super.doLayout();
		// need the parent to resize AFTER this is done, but only when it's already been initialised
		// otherwise it's interpreted as an auto-resume by the startingUpHidden code in SlidingAnimatedPanel.layout() 
		Widget parent = getParent();

		while (parent != null && !parent.equals(getGUI())) {
			if (parent instanceof SlidingAnimatedPanel) {
				if (!((SlidingAnimatedPanel)parent).isStartingUpHidden()) {
//System.out.println("PlayerRhythmBeatTypesList.doLayout: not starting hidden so invalidate sliding panel$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");			
					parent.invalidateLayout();
				}
				break;
			}
			parent = parent.getParent();
		}
		
		
//		// should be ready to go, but if the bug that causes not all widgets to have width comes up it won't be good
//		if (!table.areAllCellWidgetRowsSized()) {
//			PlatformResourceManager.getInstance().log(LOG_TYPE.error, this, StringUtils.justClassName(getClass().getName())+".doLayout: invalidating table layout because widgets not ready");
////			table.invalidateLayout();
////			invalidateLayout();
//			modelUpdated();
//		}
	}

	/**
	 * Test for the sound used being loaded ok
	 */
	@Override
	public boolean rowContainsError(int row) {
		return ((PlayBeatTypesTableModel)tableModel).rowContainsError(row);
	}

	@Override
	public String getTooltipText(int row) {
		return (String) ((PlayBeatTypesTableModel)tableModel).getTooltipContent(row, 0); // column isn't used
	}

	@Override
	protected String getLayoutThemeName() {
		return "playSettingsList";
	}
	
	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(ColourButtonValue.class, new ColourButton.ColourButtonCellWidgetCreator(this, true));
        table.registerCellRenderer(VolumeSliderValue.class, new VolumeSlider.VolumeSliderCellWidgetCreator());
        table.registerCellRenderer(SoundComboValue.class, new SoundCombo.SoundComboCellWidgetCreator(this, null));
        table.registerCellRenderer(DisplayNumbersButtonValue.class, new ToggleDisplayNumbersButton.DisplayNumbersCellWidgetCreator(null));
		super.installTableCellRenderers();
	}


	@Override
	protected void initScrollPaneToModel() {
        tableModel = new PlayBeatTypesTableModel(rhythmBeatTypes);
        table = new SimpleDataSizedTable(tableModel);

        BeatTypesXmlImpl beatTypes = (BeatTypesXmlImpl) TwlResourceManager.getInstance().getLibrary().getBeatTypes();
        beatTypes.addListener(table);
        ((SoundsXmlImpl)beatTypes.getLibrary().getSounds()).addListener(this);
        beatTypes.addListener(this);

        // don't create a selection manager
		initScrollPaneToModel(table, false, false, "playSettingsTable", "playSettingsScrollpane");
	}

	@Override
	public void itemChanged() {
		resetDimensionsComputed();
		invalidateLayout();
	}

}

    
    



