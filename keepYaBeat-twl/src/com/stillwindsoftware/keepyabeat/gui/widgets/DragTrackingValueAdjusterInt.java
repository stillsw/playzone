package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.control.UndoableCommand.DeferredActionUndoableCommand;
import com.stillwindsoftware.keepyabeat.platform.MultiEditModel;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.ValueAdjusterInt;
import de.matthiasmann.twl.model.SimpleIntegerModel;

/**
 * There are 3 ways changes are received;
 * 1. user clicks into field and types
 * 2. user drags along widget
 * 3. user clicks +/- 
 * For the three methods, these are the ways they initiate/complete
 * 1. onEditStart/onEditEnd(covers Cancelled too)
 * 2. onDragStart/onDragEnd/Canceled 
 * 3. doIncrement/Decrement (note: callback fires before this)
 * @author tomas
 */
public class DragTrackingValueAdjusterInt extends ValueAdjusterInt implements MultiEditModel {
	// while dragging or editing is happening updates are too fast, defer
	// xml save until finished (the command takes care of the implementation of that)
	private boolean multiEdit = false;
	private DeferredActionUndoableCommand deferredCommand;

	public DragTrackingValueAdjusterInt(SimpleIntegerModel numBeatsModel) {
		super(numBeatsModel);
	}

	@Override
	public boolean isMultiEdit() {
		return multiEdit;
	}

	/**
	 * The callback on the model tests if multiEdit is set by onDragStart/onEditStart
	 * and calls this method with the command to use. The corresponding end event then
	 * finalises the command by calling its deferred action.
	 * @param deferredCommand
	 */
	public void startMultiEdit(DeferredActionUndoableCommand deferredCommand) {
		this.deferredCommand = deferredCommand;
	}

	public void endMultiEdit(boolean cancelled) {
		if (!cancelled) {
			TwlResourceManager.getInstance().log(LOG_TYPE.debug, this, "DragTrackingValueAdjusterInt.endMultiEdit: executing the deferred action");
			if (deferredCommand == null) {
				TwlResourceManager.getInstance().log(LOG_TYPE.debug, this, "DragTrackingValueAdjusterInt.endMultiEdit: deferredCommand is unexpectedly null");
			}
			else {
				deferredCommand.executeDeferredAction();
			}
		}
		deferredCommand = null;
		multiEdit = false;
	}

	public DeferredActionUndoableCommand getDeferredCommand() {
		return deferredCommand;
	}

	/**
	 *  override to remove the ugly java format exception
	 */
	@Override
	protected String validateEdit(String text) {
		super.validateEdit(text);
		return null;
	}

	// and to detect the actual value entered
	@Override
	protected String onEditStart() {
		multiEdit = true;
		return super.onEditStart();
	}

	@Override
	protected boolean onEditEnd(String text) {
		boolean retValue = super.onEditEnd(text);

		// false means the edit is not accepted, so don't reset anything
		endMultiEdit(!retValue);
		return retValue;
	}

	@Override
	protected void onDragStart() {
		multiEdit = true;
		super.onDragStart();
	}

	@Override
	protected void onDragCancelled() {
		super.onDragCancelled();
		endMultiEdit(true);
	}

	@Override
	protected void onDragEnd() {
		super.onDragEnd();
		endMultiEdit(false);
	}

}

