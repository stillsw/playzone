package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.gui.widgets.ColourButton;
import com.stillwindsoftware.keepyabeat.model.BeatType;
import com.stillwindsoftware.keepyabeat.model.transactions.BeatTypesAndSoundsCommand;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlColour;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.Clipboard;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.Widget;

public class GuiUtils {
    
	/**
	 * The original way to call it, now defaults to no alpha adjuster
	 * @param beatType
	 * @param colourButton
	 * @param callingWidget
	 * @param updateBeatTypeOnOk
	 */
	public static void invokeColourSelector(BeatType beatType, ColourButton colourButton, Widget callingWidget, boolean updateBeatTypeOnOk) {
		invokeColourSelector(beatType, colourButton, callingWidget, updateBeatTypeOnOk, false);
	}

	/**
	 * Optionally also get alpha adjustment
	 * @param beatType
	 * @param colourButton
	 * @param callingWidget
	 * @param updateBeatTypeOnOk
	 * @param includeAlpha
	 */
	public static void invokeColourSelector(final BeatType beatType, final ColourButton colourButton, final Widget callingWidget
			, final boolean updateBeatTypeOnOk, boolean includeAlpha) {
    	// create the colour selector widget 
        final TwlColorSelector cs = new TwlColorSelector();
        cs.setTheme("colorselector");
        if (colourButton != null) {
        	cs.setColor(colourButton.getTwlColor());
        }
        else if (beatType != null) {
        	cs.setColor(((TwlColour)beatType.getColour()).getTwlColor());
        }

        cs.setUseLabels(false);
        cs.setShowPreview(true);
        cs.setShowAlphaAdjuster(includeAlpha);

        final PopupWindow colourPopup = new PopupWindow(KeepYaBeat.root);
        colourPopup.setTheme("confirmPopup");

		CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
            public void callback(ConfirmationPopup.CallbackReason reason) {
            	if (reason == ConfirmationPopup.CallbackReason.OK) {
            		if (updateBeatTypeOnOk) {
                		// update the beattype the rest takes care of itself :)
            			boolean playerPanelIsSource = (callingWidget instanceof PlayerRhythmBeatTypesList);
            			new BeatTypesAndSoundsCommand.ChangeBeatColour(beatType, new TwlColour(cs.getColor()), playerPanelIsSource).execute();
            		}
            		else if (colourButton != null) {
            			// not updating the beat type, then change the calling widget
            			colourButton.setBackgroundColour(cs.getColor());
            			colourButton.invalidateLayout();
            		}
            	}
			}
		};
		
        ConfirmationPopup.showDialogConfirm(null, callback, callingWidget, 
        		TwlResourceManager.getInstance().getLocalisedString(
        				TwlLocalisationKeys.CHANGE_COLOUR_TITLE), 
        		ConfirmationPopup.OK_AND_CANCEL_BUTTONS, null, null, false, cs);
    }

	public static Button makeCopyToClipButton(final String copyText) {
		Button copyToClipButton = new Button();
		copyToClipButton.setTooltipContent(TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.COPY_TO_CLIPBOARD_TOOLTIP));
		copyToClipButton.setTheme("panelCopyBtn");
		copyToClipButton.addCallback(new Runnable() {
			@Override
			public void run() {
				Clipboard.setClipboard(copyText);
			}			
		});
		return copyToClipButton;
	}

}
