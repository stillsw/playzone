package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.platform.twl.TwlFont;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.EditRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.DrawNumbersPlacement;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.RhythmAlignment;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.SubZoneActionable;

import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLFont;

/**
 * Extends the PlayerRhythmCanvas to provide beat edit functionality. Firstly, to allow
 * selection on beats in the handleEvent() method, and then also beat dragging and
 * modified placement; Editing rhythms focuses the layout more on the rhythm itself.
 * @author tomas stubbs
 *
 */
public class EditRhythmCanvas extends PlayerRhythmCanvas {

	public EditRhythmCanvas(EditRhythmDraughter rhythmDraughter) {
		super(rhythmDraughter);
	}

	@Override
	protected void applyThemeMakeTargetGoverner(ThemeInfo themeInfo) {
		if (targetGoverner == null) {
			// make a non-sharing target governer
			targetGoverner = new DraughtTargetGoverner(TwlResourceManager.getInstance(), false, SubZoneActionable.WITH_SUB_BEATS_OR_POINTER_IN_BEAT);
	
			// additional settings
			targetGoverner.setRhythmAlignment(RhythmAlignment.MIDDLE);
			targetGoverner.setHorizontalRhythmMargins(10, 10);
			targetGoverner.setVerticalRhythmMargins(45, 75);
			targetGoverner.setMaxBeatsAtNormalWidth(10);
			
			targetGoverner.setPlayed(true);
		}
		
		TwlFont fullBeatsFontNormal = new TwlFont(themeInfo.getParameterValue("fullBeatsFontNormal", false, LWJGLFont.class));
		TwlFont fullBeatsFontSmall = new TwlFont(themeInfo.getParameterValue("fullBeatsFontSmall", false, LWJGLFont.class));

		// where to draw numbers for the beats and what font to use (alternatives if the first doesn't fit)
		targetGoverner.setDrawNumbers(DrawNumbersPlacement.INSIDE_TOP, DrawNumbersPlacement.INSIDE_TOP.getDefaultMargin()
				, 3.0f, fullBeatsFontNormal, fullBeatsFontSmall);
				
		// add a font for help tips
		targetGoverner.setHelpTipsFont(new TwlFont(themeInfo.getParameterValue("helpTipsFont", false, LWJGLFont.class)));
	}

	@Override
	protected void rearrangeRhythm() {
		// just rearrange, don't set first time flag
		rhythmDraughter.arrangeRhythm();
	}
}
