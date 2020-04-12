package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;

/**
 * Implemented by the two rhythms lists (base and import) which need to provide a shared instance
 * of target governer to the rhythm image creator
 * @author tomas stubbs
 */
public interface TargetGovernerSharer {

	public DraughtTargetGoverner getTargetGoverner();

}
