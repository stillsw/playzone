package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Widget;

/**
 * Model holding the data for a help tip including which tab/sheet it should
 * appear on and a widget for the arrow to point at
 * @author tomas stubbs
 */
public class HelpTipModel {

	// where the arrow should point to
	public enum HelpTipTargetPoint {
		Top, Left, Bottom, Right,
		TopLeft, BottomLeft, BottomRight, TopRight
	}
	
	// the tab/sheet to show this help tip on
	private int tabNum;
	private final boolean newTab;
	private final String localiseKey;
	private final Widget targetWidget;
	private final HelpTipTargetPoint helpTipTargetPoint;
	private final String autoHelpKey;
	
	/**
	 * Construct a help tip model
	 * @param autoHelpKey
	 * @param tabNum
	 * @param localiseKey
	 * @param targetWidget
	 * @param helpTipTargetPoint
	 */
	public HelpTipModel(String autoHelpKey, boolean newTab, String localiseKey, Widget targetWidget, HelpTipTargetPoint helpTipTargetPoint) {
		this.autoHelpKey = autoHelpKey;
		this.newTab = newTab;
		this.localiseKey = localiseKey;
		this.targetWidget = targetWidget;
		this.helpTipTargetPoint = helpTipTargetPoint;
	}

	public int getTabNum() {
		return tabNum;
	}

	public void setTabNum(int tabNum) {
		this.tabNum = tabNum;
	}

	public String getAutoHelpKey() {
		return autoHelpKey;
	}

	public boolean isNewTab() {
		return newTab;
	}

	public String getText() {
		return TwlResourceManager.getInstance().getLocalisedString(localiseKey);
	}

	public Widget getTargetWidget() {
		return targetWidget;
	}

	public HelpTipTargetPoint getHelpTipTargetPoint() {
		return helpTipTargetPoint;
	}
}
