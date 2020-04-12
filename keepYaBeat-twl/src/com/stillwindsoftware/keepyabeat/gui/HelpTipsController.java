package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.gui.HelpTipModel.HelpTipTargetPoint;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlPersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.DialogLayout.Group;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Event.Type;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.ResizableFrame;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;

/**
 * The owner of the help system, it is the single child of a popup window and has no 
 * background, the screen will show a central help dialog with controls for help (prev/next/etc)
 * and 1 or more tips for the screen. These will optionally have targets for arrows to be drawn
 * to objects around the screen.
 * @author tomas stubbs
 *
 */
public class HelpTipsController extends Widget {

	public enum HelpPage {
		general
		,player
		,rhythmsList
		,rhythmEditor
		,beatsAndSounds
		,tags
		,sharer
		,importer
		,bugReport
		};

	// the data for help tips, could be in more than one set (tab set)
	private final HelpTipModel[] helpTips;
	private final boolean allowClickOutsideClose;
	private final Runnable closeCallback;
	
	private int currentTab = 0;
	
	private PopupWindow popup;
	private ResizableFrame controlFrame;
	private boolean userMovedIt = false; // don't recentre if user moves the frame
	private boolean positioningHappening = true;
	private Button prevBtn;
	private Button nextBtn;
	private Label pageLabel = null;
	private int numPages = 0;

	private TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	private TwlPersistentStatesManager persistenceManager = (TwlPersistentStatesManager) resourceManager.getPersistentStatesManager();

	/**
	 * Overloaded constructor that does not include a close callback (this is the usual method) 
	 * @param helpKey
	 * @param extraHelpLocalisedKey
	 * @param helpTips
	 * @throws IllegalArgumentException
	 */
	public HelpTipsController(UserBrowserProxy.Key helpKey, String extraHelpLocalisedKey, HelpTipModel ... helpTips) throws IllegalArgumentException {
		this(helpKey, extraHelpLocalisedKey, extraHelpLocalisedKey == null, null, helpTips);
	}

	/**
	 * Constructs a help tips controller which will create the widgets that
	 * show the help arranged in tab sets. It is up to the caller to make sure the
	 * tips are in order of tab sets.
	 * @param helpKey
	 * @param helpTips
	 */
	public HelpTipsController(UserBrowserProxy.Key helpKey, String extraHelpLocalisedKey, boolean allowClickToClose
			, Runnable closeCallback, HelpTipModel ... helpTips) {
		this.closeCallback = closeCallback;
		this.helpTips = helpTips;
		
		// when there's extra help to show there will be a close button
		allowClickOutsideClose = allowClickToClose;
		
		// this is actually a parent widget with no background, so no theme needed
		setTheme("confirmPopupTintBackground");
		
		// order tips into their tabs
		if (helpTips != null) {
			orderTips();
		}
		
		// make the frame that contains the user interactive controls
		makeControlFrame(extraHelpLocalisedKey, helpKey);
		
		// show the first set of tips
		if (helpTips != null) {
			showTips(currentTab);
		}
		
		// make the popup window that blocks the display for the page underneath
		makePopupWindow();
	}

	/**
	 * Evaluate the tips, they are in order and specified new tabs when required
	 */
	private void orderTips() {
		if (helpTips != null) {
			int tipTab = -1; // first one will specify new page
			for (HelpTipModel helpTipModel : helpTips) {
				if (helpTipModel.isNewTab()) {
					tipTab++;
				}
				
				// set the tab num on the tip
				helpTipModel.setTabNum(tipTab);
			}
		}
		else {
			resourceManager.log(LOG_TYPE.error, this, "HelpTipsController.orderTips: null helpTips");
		}
	}

	/**
	 * Assemble the tips for the tab, displaying them as defined in the models.
	 * Set the prev/next buttons, and place everything.
	 * @param forTab
	 * @param  
	 */
	private void showTips(int forTab) {
		currentTab = forTab;
		
		// remove the current tips showing
		for (int i = getNumChildren() - 1; i >= 0; i--) {
			if (getChild(i) instanceof HelpTip) {
				removeChild(i);
			}
		}
		
		boolean hasMoreTabs = false;
		
		// add tips for the selected tab
		for (HelpTipModel helpTipModel : helpTips) {
			if (helpTipModel.getTabNum() == forTab) {
				add(new HelpTip(helpTipModel));
			}
			else if (helpTipModel.getTabNum() > forTab) {
				hasMoreTabs = true;
			}
		}
		
		if (prevBtn != null && nextBtn != null) {
			prevBtn.setEnabled(forTab > 0);
			nextBtn.setEnabled(hasMoreTabs);

			pageLabel.setText(
					String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_PAGE_LABEL)
					, currentTab + 1, numPages + 1));
		}

	}

	/**
	 * Make the control frame
	 */
	private void makeControlFrame(String extraHelpLocalisedKey, UserBrowserProxy.Key helpKey) {
		controlFrame = new ResizableFrame() {
			@Override
			protected void positionChanged() {
				super.positionChanged();
				if (!positioningHappening) {
					userMovedIt = true;
				}
			}
		};
		controlFrame.setTheme("resizableframe-title");
		controlFrame.setTitle(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_TIPS_TITLE));
		add(controlFrame);
		
		Label extraHelp = new Label(resourceManager.getLocalisedString(
				extraHelpLocalisedKey != null
					? extraHelpLocalisedKey : TwlLocalisationKeys.HELP_NO_CLOSE_EXTRA_TEXT));
		
		// prev and next buttons depend on > 1 set of help tips
		boolean showNextPrevBtns = false;
		
		// look for a tip with sheet != 0, assumed order of all 0 sheet followed by next etc
		if (helpTips != null) {
			for (HelpTipModel helpTip : helpTips) {
				if (helpTip.getTabNum() > numPages) {
					showNextPrevBtns = true;
					numPages = helpTip.getTabNum();
				}				
			}
		}
		
		if (showNextPrevBtns) {
			pageLabel = new Label(
					String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_PAGE_LABEL)
							, currentTab + 1, numPages + 1));
			nextBtn = new Button();
			nextBtn.setTheme("nextBtn");
			nextBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_NEXT_TOOLTIP));
			nextBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						showTips(currentTab + 1);
					}
				});

			prevBtn = new Button();
			prevBtn.setTheme("prevBtn");
			prevBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_PREV_TOOLTIP));
			prevBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						showTips(currentTab - 1);
					}
				});
			
		}
		
		// help and close buttons show at the bottom
		Button helpBtn = makeOnlineHelpButton(helpKey);

		Button closeBtn = null;
		if (!allowClickOutsideClose) {
			closeBtn = new Button();
			closeBtn.setTheme("okBtn");
			closeBtn.getAnimationState().setAnimationState(StateKey.get("defaulted"), true);
			closeBtn.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.HELP_CLOSE_TOOLTIP));
			closeBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						popup.closePopup();
					}
				});
		}
		
		DialogLayout dlgLayout = new DialogLayout();
		dlgLayout.setTheme("confirmDialog");
		
		Group hGroup = dlgLayout.createParallelGroup();
		Group vGroup = dlgLayout.createSequentialGroup();
		
		if (extraHelp != null) {
			hGroup.addWidget(extraHelp);
			vGroup.addWidget(extraHelp);
		}
		
		if (showNextPrevBtns) {
			hGroup.addGroup(dlgLayout.createSequentialGroup().addWidget(prevBtn).addGap().addWidget(pageLabel).addGap().addWidget(nextBtn));
			vGroup.addGroup(dlgLayout.createParallelGroup(prevBtn, pageLabel, nextBtn));
		}
		
		Group lastButtonsHGroup = dlgLayout.createSequentialGroup().addWidget(helpBtn).addGap(); 
		if (closeBtn != null) {
			lastButtonsHGroup.addWidget(closeBtn);
			vGroup.addGroup(dlgLayout.createParallelGroup(helpBtn, closeBtn));
		}
		else {
			vGroup.addWidget(helpBtn);
		}
		hGroup.addGroup(lastButtonsHGroup);
		
		dlgLayout.setHorizontalGroup(hGroup);
		dlgLayout.setVerticalGroup(vGroup);
		
		controlFrame.add(dlgLayout);
	}

	private Button makeOnlineHelpButton(final UserBrowserProxy.Key helpKey) {
		Button helpBtn = new Button(TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.HELP_ONLINE_LABEL));
		helpBtn.setTheme("genericPanelBtn");

		helpBtn.setCanAcceptKeyboardFocus(false);
		helpBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						UserBrowserProxy.showWebPage(helpKey);
					}			
				});
		helpBtn.setTooltipContent(TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.HELP_ONLINE_TOOLTIP));
		helpBtn.setSize(helpBtn.getMaxWidth(), helpBtn.getMaxHeight());
		
		return helpBtn;
	}

	/**
	 * Creates a popup window and adds this controller to it as its only child
	 */
	private void makePopupWindow() {
		popup = new PopupWindow(KeepYaBeat.root) {
			@Override
			protected void layout() {
				setSize(KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight());
				setPosition(0, 0);
				HelpTipsController.this.setPosition(0, 0);
				HelpTipsController.this.setSize(KeepYaBeat.root.getInnerWidth(), KeepYaBeat.root.getInnerHeight());
			}

			@Override
			protected boolean handleEventPopup(Event evt) {
				if (allowClickOutsideClose) {
					if (evt.isMouseEvent() && evt.getType() == Type.MOUSE_CLICKED) {
						closePopup();
						return true;
					}
				}
				return super.handleEventPopup(evt);
			}

			@Override
			public void closePopup() {
				super.closePopup();
				// maybe there's a callback to run
				if (closeCallback != null) {
					closeCallback.run();
				}
			}
		};
		
		popup.setCloseOnClickedOutside(false);
		popup.setTheme("");
		popup.add(this);
		popup.openPopupCentered();
	}

	/**
	 * Close on return key (as though the close btn default action)
	 */
	@Override
	protected boolean handleEvent(Event evt) {
		if (evt.isKeyPressedEvent() && evt.hasKeyChar() && evt.getKeyCode() == Event.KEY_RETURN) {
			popup.closePopup();
			return true;
		}
		return super.handleEvent(evt);
	}
	
	@Override
	protected void layout() {
		// put each of the tips at their specific locations
		for (int i = 0; i < getNumChildren(); i++) {
			if (getChild(i) instanceof HelpTip) {
				((HelpTip) getChild(i)).putAtPosition();
			}
		}
		
		// centre the frame provided the user didn't move it themselves
		controlFrame.adjustSize();
		if (!userMovedIt) {
			positioningHappening = true;
			controlFrame.setPosition(getInnerX() + getInnerWidth() / 2 - controlFrame.getWidth() / 2
					, getInnerY() + getInnerHeight() / 2 - controlFrame.getHeight() / 2);
			positioningHappening = false;
		}
	}

	/**
	 * A help tip label widget that has a pointer background that points
	 * in the direction indicated 
	 * @author tomas stubbs
	 */
	private class HelpTip extends Label {

		// the model for this tip
		private final HelpTipModel helpTipModel;
		
		// the offset from 0 along which axis the arrow points at
		// defined by the theme, negative goes from right on horizontal axis
		// and bottom on vertical.
		private int xOffset;
		private int yOffset;
		
		/**
		 * Creates a help tip with the model
		 * @param helpTipModel
		 */
		public HelpTip(HelpTipModel helpTipModel) {
			super(helpTipModel.getText());
			setTheme("helpTipTarget".concat(helpTipModel.getHelpTipTargetPoint().name()));
			this.helpTipModel = helpTipModel;
			if (helpTipModel.getAutoHelpKey() != null) {
				persistenceManager.setAutoHelpSeen(helpTipModel.getAutoHelpKey());
			}
		}

		@Override
		protected void applyThemeLabel(ThemeInfo themeInfo) {
			super.applyThemeLabel(themeInfo);
			xOffset = themeInfo.getParameter("xOffset", 0);
			yOffset = themeInfo.getParameter("yOffset", 0);
		}

		/**
		 * Places the help tip relative to its target widget, and incorporating the offsets
		 * from theme
		 */
		public void putAtPosition() {
			Widget target = helpTipModel.getTargetWidget();
			adjustSize();
			if (target == null) {
				return;
			}
			
//			Location2Df realPos = getRealPos(target);
			if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.Top)) {
				// arrow point is near top left corner, make that the centre of the widget
				// on x-axis and bottom on y-axis
				setPosition(target.getX() + target.getWidth() / 2 - xOffset
						, target.getY() + target.getHeight());
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.Bottom)) {
				// arrow point is near bottom left corner, make that the centre of the widget
				// on x-axis and top on y-axis
				setPosition(target.getX() + target.getWidth() / 2 - xOffset
						, target.getY() - getHeight());
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.Left)) {
				// arrow point is near top left corner, make that the centre of the widget
				// on y-axis and right on x-axis
				setPosition(target.getX() + target.getWidth()
						, target.getY() + target.getHeight() / 2 - yOffset);
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.Right)) {
				// arrow point is near top right corner, make that the centre of the widget
				// on y-axis and left on x-axis
				setPosition(target.getX() - getWidth()
						, target.getY() + target.getHeight() / 2 - yOffset);
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.TopRight)) {
				// arrow point is at top right corner, make that the centre of the widget
				// on x-axis and bottom on y-axis
				setPosition(target.getX() + target.getWidth() / 2 - getWidth()
						, target.getY() + target.getHeight());
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.TopLeft)) {
				// arrow point is at top left corner, make that the centre of the widget
				// on x-axis and bottom on y-axis
				setPosition(target.getX() + target.getWidth() / 2
						, target.getY() + target.getHeight());
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.BottomLeft)) {
				// arrow point is at bottom left corner, make that the centre of the widget
				// on x-axis and top on y-axis
				setPosition(target.getX() + target.getWidth() / 2
						, target.getY() - getHeight());
			}
			else if (helpTipModel.getHelpTipTargetPoint().equals(HelpTipTargetPoint.BottomRight)) {
				// arrow point is at bottom right corner, make that the centre of the widget
				// on x-axis and top on y-axis
				setPosition(target.getX() + target.getWidth() / 2 - getWidth()
						, target.getY() - getHeight());
			}
		}

	}
	
}
