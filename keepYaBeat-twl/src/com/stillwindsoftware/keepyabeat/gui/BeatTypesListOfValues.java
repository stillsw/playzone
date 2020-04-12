package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton.ColourButtonValue;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.twlModel.BeatTypesLovTableModel;

import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Widget;

/**
 * List of beat types for picking one, by TwlRhythmEditor in the QuickEdit dialog to select
 * a beat type for adding new beats. Also called by TwlRhythmEditor when adding a single beat
 * which is then dragged onto the rhythm from this list.
 * @author tomas stubbs
 *
 */
public class BeatTypesListOfValues extends StandardTableList implements LibraryListener { 

	private Runnable dragStoppedCallback;
	
	/**
	 * when passed true, drag buttons are created instead of the default normal lov selection buttons
	 * @param createDragButtons
	 */
	public BeatTypesListOfValues(Runnable dragStoppedCallback) {
		this.dragStoppedCallback = dragStoppedCallback;
	}

	/**
	 * default is to act purely as an lov (click selects)
	 */
	public BeatTypesListOfValues() {
		this(null);
	}

	/**
	 * When used for a drag source, add a title that provides a tip for that, so need a top box
	 */
	@Override
	protected void initTopBox() {
		if (dragStoppedCallback != null) {
			topBox = new DialogLayout();
			topBox.setTheme("topBox");
		}
			
	}

	@Override
	protected String getTopboxTitle() {
		return (TwlResourceManager.getInstance())
				.getLocalisedString(TwlLocalisationKeys.DRAG_BEAT_TYPES_LOV_TITLE);
	}

	/**
	 * Any class which needs to receive the chosen beat type from the lov should implement this interface
	 * and pass it into the constructor, in turn it will be passed to the ColourButtonCellWidgetCreator and to the 
	 * ColourButton itself for notifying when a beat type is chosen
	 */
	public static interface BeatTypeSelectReceiver {
		/**
		 * Passes the beat type that was selected and the widget that was used in performing that selection,
		 * this allows the coordinates of the widget (probably a colourButton) to be passed to the rhythmEditor
		 * when dragging a new beat type.
		 * @param beatType
		 * @param beatSelectionWidget
		 */
		public void beatTypeSelected(BeatType beatType, Widget beatSelectionWidget);
	}
	
	private BeatTypeSelectReceiver beatTypeSelectReceiver;

	public void setBeatTypeSelectReceiver(BeatTypeSelectReceiver beatTypeSelectReceiver) {
		this.beatTypeSelectReceiver = beatTypeSelectReceiver;
	}

	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		TwlResourceManager.getInstance().getListenerSupport().removeListener(this);
		super.beforeRemoveFromGUI(gui);
	}

	@Override
	protected String getLayoutThemeName() {
		return "beatTypesLov";
	}
	
	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(ColourButtonValue.class
        		, new ColourButton.ColourButtonCellWidgetCreator(this, true, beatTypeSelectReceiver, dragStoppedCallback));
		super.installTableCellRenderers();
	}


	@Override
	protected void initScrollPaneToModel() {
        tableModel = new BeatTypesLovTableModel();
        table = new SimpleDataSizedTable(tableModel);

		initScrollPaneToModel(table, "beatTypesLovTable", "beatTypesLovScrollpane");
		TwlResourceManager.getInstance().getListenerSupport().addListener(TwlResourceManager.getInstance().getLibrary().getBeatTypes(), this);
	}

	@Override
	public void itemChanged() {
		table.itemChanged();
		invalidateLayout();
	}

	@Override
	protected void doLayout() {
		// make sure dimensions are computed
		resetDimensionsComputed();
		super.doLayout();
	}


}

    
    



