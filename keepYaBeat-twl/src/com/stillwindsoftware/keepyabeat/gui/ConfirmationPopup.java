package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.geometry.Location2Df;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.AnimationState;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.ResizableFrame;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.Fixed;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;
import de.matthiasmann.twl.utils.CallbackSupport;

/**
 * Convenience class for standardised layout of modal dialog
 * @author Tomas Stubbs
 */
public class ConfirmationPopup extends DialogLayout {

	public static final int NO_BUTTON = 0;
	public static final int OK_BUTTON = 2;
	public static final int CANCEL_BUTTON = 4;
	public static final int DISGARD_BUTTON = 8;
	public static final int OK_AND_CANCEL_BUTTONS = OK_BUTTON | CANCEL_BUTTON;
	public static final int OK_AND_DISGARD_BUTTONS = OK_BUTTON | DISGARD_BUTTON;
	public static final int CANCEL_AND_DISGARD_BUTTONS = CANCEL_BUTTON | DISGARD_BUTTON;
	public static final int OK_CANCEL_AND_DISGARD_BUTTONS = OK_BUTTON | CANCEL_BUTTON | DISGARD_BUTTON;
	public static final int EXTRA_BUTTON = 256;

	public enum CallbackReason {
		OK,
		CANCEL,
		DISGARD,
		EXTRA_BUTTON
	};

	private enum ButtonCallbackType {
		POPUP
		//SLIDING,
		//SLIDING_POPUP
	};

	private boolean showOK;
	private boolean showCancel;
	private boolean showDisgard;
	private boolean showExtraButton;
	private boolean completed = false;
	private Validation validation;
	private Label errorText;
	private boolean firstError = true;
	private ConfirmPopupWindow popup;
	
	// the widgets may be add to their own dialog layout in a scrollpane 
	private ScrollPane scrollPane = null;
	private DialogLayout widgetsLayout = null;
	
	// try to allocate focus to first editfield 
	private Widget keyBoardFocusWidget = null;
	private DefaultableButton defaultButton;
	// main purpose of keeping the original default button is to reset it when the focus moves away from
	// the buttons, however this seems confusing because it looks like tab is moving in funny ways
	// for this reason keyboardFocusLost() on the DefaultableButton is commented out 
//	private int originalDefaultBtnId;
	private ArrayList<DefaultableButton> buttons = new ArrayList<DefaultableButton>();
	private ButtonCallbackType buttonCallbackType;

	private CallbackWithReason<?>[] callbacks;

    private TwlResourceManager resourceManager = TwlResourceManager.getInstance();

	/**
	 * A version that has the popup inside a popup window of some kind (see convenience static methods below for creation)
	 * @param popup
	 * @param includeButtons
	 * @param widgets
	 */
	public ConfirmationPopup(ConfirmPopupWindow popup, int includeButtons, Widget extraButton
			, String[] extraButtonTexts, Runnable extraBtnCallback, boolean makeScrollingRegion, Widget ... widgets) {
		this.buttonCallbackType = ButtonCallbackType.POPUP;
		setUp(includeButtons, extraButton, extraButtonTexts, extraBtnCallback, makeScrollingRegion, widgets);
		this.popup = popup;
	}

	private class DefaultableButton extends Button {
		private int buttonId;
		private Runnable callback;

		private DefaultableButton(int buttonId, String theme, Runnable callback) {
			this.buttonId = buttonId;
			setTheme(theme);
			this.callback = callback;
			addCallback(callback);
			buttons.add(this);
		}
		
		@Override
		protected void keyboardFocusGained() {
			setDefaultButton(this);
			super.keyboardFocusGained();
		}

		private void setDefaultState(boolean on) {
			getAnimationState().setAnimationState(StateKey.get("defaulted"), on);
		}
	}
	
	private void setUp(int includeButtons, Widget extraButton, String[] extraButtonTexts, Runnable extraBtnCallback
			, boolean makeScrollingRegion, Widget ... widgets) {
		showOK = (includeButtons & OK_BUTTON) == OK_BUTTON;
		showCancel = (includeButtons & CANCEL_BUTTON) == CANCEL_BUTTON;
		showDisgard = (includeButtons & DISGARD_BUTTON) == DISGARD_BUTTON;
		showExtraButton = (includeButtons & EXTRA_BUTTON) == EXTRA_BUTTON;
		TwlResourceManager resourceManager = TwlResourceManager.getInstance();

		DefaultableButton btnOK = null;
		DefaultableButton btnCancel = null;
		DefaultableButton btnDisgard = null;
		DefaultableButton btnExtra = null;

		if (showExtraButton) {
			if (extraButton == null) {
				btnExtra = new DefaultableButton(EXTRA_BUTTON, "genericConfirmBtn" 	
						// if there's a callback for the extra button (might want to complete an action unrelated to 
						// closing the popup... ie. popup something in addition), use it, otherwise provide standard
						, (extraBtnCallback != null
							? extraBtnCallback
							: getButtonCallback(CallbackReason.EXTRA_BUTTON)));
				
				defaultButton = btnExtra;
	
				// override the default text/tooltip
				if (extraButtonTexts != null) {
					btnExtra.setText(extraButtonTexts[0]);
					if (extraButtonTexts.length > 1) {
						btnExtra.setTooltipContent(extraButtonTexts[1]);
					}
				}
			}
		}

		if (showDisgard) {
			btnDisgard = new DefaultableButton(DISGARD_BUTTON, "genericConfirmBtn", getButtonCallback(CallbackReason.DISGARD));
			defaultButton = btnDisgard;
			btnDisgard.setText(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DISCARD_LABEL));
			btnDisgard.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_DISCARD_TOOLTIP));
		}

		if (showCancel) {
			btnCancel = new DefaultableButton(CANCEL_BUTTON, "btnCancel", getButtonCallback(CallbackReason.CANCEL));
			defaultButton = btnCancel;
			btnCancel.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_CANCEL_TOOLTIP));
		}

		if (showOK) {
			btnOK = new DefaultableButton(OK_BUTTON, "btnOK", getButtonCallback(CallbackReason.OK));
			defaultButton = btnOK;
			btnOK.setTooltipContent(resourceManager.getLocalisedString(TwlLocalisationKeys.CONFIRM_OK_TOOLTIP));
		}

		// horizontal groups
		Group hGroup = createParallelGroup();
		Group widgetsHGroup = null;
		DialogLayout widgetsSourceLayout = this;

		// vertical groups
		Group vGroup = createSequentialGroup();
		Group widgetsVGroup = null;

		// the widgets may be add to their own dialog layout in a scrollpane 
		if (makeScrollingRegion) {
			widgetsLayout = new DialogLayout();
			widgetsLayout.setTheme("confirmDlgScrollDialog");
			// overriding the pref inner methods this way causes the the scrollpane to adjust to contents
			// not sure why it doesn't work this way anyway
			scrollPane = new ScrollPane(widgetsLayout);
			scrollPane.setFixed(Fixed.HORIZONTAL);
			scrollPane.setTheme("confirmDlgScrollpane");

			// add the scrollpane to the hori group
			hGroup.addWidget(scrollPane);
			vGroup.addWidget(scrollPane);
			
			// widgets are added to their own group
			widgetsHGroup = widgetsLayout.createParallelGroup();
			widgetsVGroup = widgetsLayout.createSequentialGroup();
			widgetsLayout.setHorizontalGroup(widgetsHGroup);
			widgetsLayout.setVerticalGroup(widgetsVGroup);
			widgetsSourceLayout = widgetsLayout;
		}
		else {
			// add widgets directly to non-scrolling group
			widgetsHGroup = hGroup;
			widgetsVGroup = createSequentialGroup();
			vGroup.addGroup(widgetsVGroup);		
		}

		boolean widgetGotFocus = false;
		// while adding widgets to their group, try to find any editfields to give focus to
		for(Widget w : widgets) {
			if (w != null) {
				// try to give focus to the first editfield 
				if (!widgetGotFocus) {
					if (w instanceof EditField) {
						keyBoardFocusWidget = w;
						widgetGotFocus = true;
					}
					else if (w instanceof DialogLayout) {
						for (int i = 0; i < w.getNumChildren(); i++) {
							Widget child = w.getChild(i);
							if (child instanceof EditField) {
								keyBoardFocusWidget = child;
								widgetGotFocus = true;
								break;
							}
						}
					}
				}

				widgetsHGroup.addGroup(widgetsSourceLayout.createSequentialGroup().addGap().addWidget(w).addGap());				
			}
		}
		errorText = new Label();
		hGroup.addGroup(createSequentialGroup().addWidget(errorText));
		
		if (showOK || showCancel || showDisgard || showExtraButton) {
			// before add to the group, add to the dialog to guarantee the tab ordering
			if (showExtraButton) add(extraButton == null ? btnExtra : extraButton);
			if (showDisgard) add(btnDisgard);
			if (showOK) add(btnOK);
			if (showCancel) add(btnCancel);
			
			// add to the group
			Group buttonsGrp = createSequentialGroup();
			if (showExtraButton) buttonsGrp.addWidget(extraButton == null ? btnExtra : extraButton);
			if (showDisgard) buttonsGrp.addWidget(btnDisgard);
			buttonsGrp.addGap();
			buttonsGrp.addGap(DialogLayout.LARGE_GAP); // make sure there's a minimum
			if (showOK) buttonsGrp.addWidget(btnOK).addGap(DialogLayout.MEDIUM_GAP);
			if (showCancel) buttonsGrp.addWidget(btnCancel);
			hGroup.addGroup(buttonsGrp);
		}

		for(Widget w : widgets) {
			if (w != null)
				widgetsVGroup.addWidget(w);
		}

		vGroup.addGroup(createParallelGroup().addWidget(errorText));
		
		if (showOK || showCancel || showDisgard || showExtraButton) {
			vGroup.addGap(DialogLayout.MEDIUM_GAP, DialogLayout.MEDIUM_GAP, Short.MAX_VALUE);
			Group buttonsGrp = createParallelGroup();
			if (showDisgard) buttonsGrp.addWidget(btnDisgard);
			if (showExtraButton) buttonsGrp.addWidget(extraButton == null ? btnExtra : extraButton);
			if (showOK) buttonsGrp.addWidget(btnOK);
			if (showCancel) buttonsGrp.addWidget(btnCancel);
			vGroup.addGroup(buttonsGrp);
		}
		
		setHorizontalGroup(hGroup);
		setVerticalGroup(vGroup);
		
		if (defaultButton != null) {
			setDefaultButton(defaultButton.buttonId);
		}
		
		// scrollpane will hide everything unless adjust it
		if (widgetsLayout != null) {
			widgetsLayout.adjustSize();
			scrollPane.adjustSize();
		}
	}

	public boolean fireDefaultButton() {
		if (defaultButton != null) {
			// grab the focus allows any valueAdjuster fields to set their values
			defaultButton.requestKeyboardFocus();
			defaultButton.callback.run();
			return true;
		}
		else {
			return false;
		}
	}
	
	public boolean focusOnDefaultButton() {
		if (defaultButton != null) {
			// grab the focus allows any valueAdjuster fields to set their values
			return defaultButton.requestKeyboardFocus();
		}
		else {
			return false;
		}
	}

	private void setDefaultButton(DefaultableButton defaultBtn) {
		if (defaultButton != null) {
			// make sure state is unset
			defaultButton.setDefaultState(false);
		}
		
		defaultButton = defaultBtn;
		defaultButton.setDefaultState(true);
	}
	
	/**
	 * Set button to use as default using the identifier, called at the end of setup, and optionally
	 * by the caller to set a default.
	 * @param defaultButton
	 */
	public void setDefaultButton(int defaultButtonInt) {

		if (defaultButton != null) {
			// make sure state is unset
			defaultButton.setDefaultState(false);
			defaultButton = null;
		}
		
		for (int i = 0; i < buttons.size(); i++) {
			DefaultableButton button = buttons.get(i);
			if (button.buttonId == defaultButtonInt) {
				defaultButton = button;
				defaultButton.setDefaultState(true);
			}
		}
	}

	public Widget getKeyBoardFocusWidget() {
		return keyBoardFocusWidget;
	}

	// protect against recalling in a loop (because of adjustSize elsewhere)
	private long doLayoutCalledAt = -1;
	private int layoutLoops = 0;
	
	/**
	 * Make sure the frame resizes if this layout changes (otherwise a scrollpane may get bigger and the dialog
	 * won't be large enough to show it)
	 */
	@Override
	protected void doLayout() {
		super.doLayout();
		Widget frame = getParent();
		if (frame != null && !popup.resizingHappening && !popup.positioningHappening) {
			// detect loop
			long now = System.currentTimeMillis();
			if (now - doLayoutCalledAt < 100) {
				// 100ms since last call
				layoutLoops++;
				if (layoutLoops > 5) {
					// too many
					resourceManager.log(LOG_TYPE.warning, this, "ConfirmationPopup.doLayout: too many loops don't keep going");
					doLayoutCalledAt = -1;
					layoutLoops = 0;
					return;
				}
			}
			else {
				doLayoutCalledAt = now;
				layoutLoops = 0;
			}
			
			popup.initSizeDone = false;
			popup.initCentreDone = false;
			popup.invalidateLayout();
		}
	}

	public void setValidation(Validation validation) {
		this.validation = validation;
	}

	/**
	 * validate without hitting ok button, possibly only one widget
	 * @param byWidget
	 */
	public void triggerValidation(Widget byWidget) {
		boolean valid = true;
		valid = validation.isValid(byWidget);// no widget means do all validation
		if (valid) {
			if (errorText.hasText())
				errorText.setText("");
		}
		else {
			setErrorText();
		}
	}

	public boolean isCompleted() {
		return completed;
	}

	public void close(Runnable callback) {
		getButtonCallback(CallbackReason.CANCEL).parentCloseBehaviour(callback);
	}

	public void addCallback(CallbackWithReason<CallbackReason> cb) {
		callbacks = CallbackSupport.addCallbackToList(callbacks, cb, CallbackWithReason.class);
	}

	public void removeCallback(CallbackWithReason<CallbackReason> cb) {
		callbacks = CallbackSupport.removeCallbackFromList(callbacks, cb);
	}

	protected void fireCallback(CallbackReason reason) {
		CallbackSupport.fireCallbacks(callbacks, reason);
	}

	/**
	 * A factory method that instantiates the appropriate type of button callback
	 * (they have different behaviours depending on popup/sliding panel or combination
	 * @param reason
	 * @return
	 */
	private ButtonCallback getButtonCallback(CallbackReason reason) {
		if (buttonCallbackType == ButtonCallbackType.POPUP)
			return new PopupButtonCallback(reason);
		else
			return null;
	}

	class PopupButtonCallback extends ButtonCallback {
		protected PopupButtonCallback(CallbackReason reason) {
			super(reason);
		}
		@Override
		protected void parentCloseBehaviour(Runnable callback) {
			popup.closePopup();
			popup.removeAllChildren();
			super.parentCloseBehaviour(callback);
		}
	}

	/**
	 * A utility class used as callback for the "accept" and "cancel" buttons.
	 */
	abstract class ButtonCallback implements Runnable {
		final CallbackReason reason;

		protected ButtonCallback(CallbackReason reason) {
			this.reason = reason;
		}

		protected void parentCloseBehaviour(Runnable callback) {
			completed = true;
			if (callback != null) {
				callback.run();
			}
		}

		private void fireCallBackAndClosePopup(CallbackReason reason) {
			try {
				fireCallback(reason);
			} finally {
				parentCloseBehaviour(null);
			}
		}

		public void run() {
			if (reason == CallbackReason.CANCEL || reason == CallbackReason.DISGARD || validation == null) {
				fireCallBackAndClosePopup(reason);
			}
			else {
				boolean valid = true;
				valid = validation.isValid(null, reason);// no widget means do all validation
				if (valid)
					fireCallBackAndClosePopup(reason);
				else {
					setErrorText();
				}
			}
		}

	}

	private void setErrorText() {
		errorText.setText(validation.getErrorText());
		if (firstError) {
	 		errorText.setTheme("errorText");
			AnimationState state = errorText.getAnimationState();
			state.setAnimationState(StateKey.get("error"), true);
			// increase size of popup window to show the text
			adjustSize();
			firstError = false;
		}
	}
	
	public static ConfirmationPopup showDialogConfirm(Validation validation
			, CallbackWithReason<CallbackReason> callback
			,  Widget focusWidget
			, String title
			, int includeButtons
			, String[] extraButtonTexts // only applies if there is an extra button shown, 1st is text on the button, 2nd is tooltip
			, Runnable extraBtnCallback
			, boolean makeScrollingRegion
			, Widget ... widgets) {

		return showDialogConfirm(new ConfirmPopupWindow(true), validation, callback, focusWidget, null, title,
				includeButtons, extraButtonTexts, extraBtnCallback, true, null, NO_BUTTON, makeScrollingRegion, widgets);
	}

	public static ConfirmationPopup showDialogConfirm(Validation validation
			, CallbackWithReason<CallbackReason> callback
			,  Widget focusWidget
			, String title
			, int includeButtons
			, String[] extraButtonTexts // only applies if there is an extra button shown, 1st is text on the button, 2nd is tooltip
			, Runnable extraBtnCallback
			, Location2Df openAt
			, boolean makeScrollingRegion
			, Widget ... widgets) {

		return showDialogConfirm(new ConfirmPopupWindow(false), validation, callback, focusWidget, null, title,
				includeButtons, extraButtonTexts, extraBtnCallback, false, openAt, NO_BUTTON, makeScrollingRegion, widgets);
	}

	/**
	 * Given a popup find any child that in a confirmationPopup 
	 * @param popup
	 * @return
	 */
	private static ConfirmationPopup getConfirmationPopupChild(ConfirmPopupWindow popup) {
		Widget child = popup.getChild(0); // resizeable frame, should have 2nd child as Conf dialog
		for (int i = 0; i < child.getNumChildren(); i++) {
			if (child.getChild(i) instanceof ConfirmationPopup) {
				return (ConfirmationPopup)child.getChild(i);
			}
		}
		
		return null;
	}
	
	/**
	 * Finds the child which is expected to be a ConfirmationPopup and if there's a default button
	 * set it runs its callback and returns true. Called from the PopupWindow handleEventPopup(), it's
	 * static so any popup passed as parent can also call it. 
	 * @return
	 */
	public static boolean executeDefaultButtonOnKeyReturn(ConfirmPopupWindow popup) {

		ConfirmationPopup confirmDlg = getConfirmationPopupChild(popup);
		if (confirmDlg != null) {
			return confirmDlg.fireDefaultButton();
		}
		else {
			TwlResourceManager.getInstance().log(LOG_TYPE.error, null, "ConfirmationPopup.executeDefaultButtonOnKeyReturn: no confirm dlg found with default button set");
			return false;
		}
	}
	
	/**
	 * Finds the child which is expected to be a ConfirmationPopup and attempts to cycle to the next child
	 * @param popup
	 * @param forwards
	 * @return
	 */
	public static boolean executeTabPress(ConfirmPopupWindow popup, boolean forwards) {
		ConfirmationPopup confirmDlg = getConfirmationPopupChild(popup);
		if (confirmDlg != null) {
			if (forwards) {
				confirmDlg.focusNextChild();
			}
			else {
				confirmDlg.focusPrevChild();
			}
			return true;
		}		
		else {
			return false;
		}
	}

	/**
	 * The original way to call it, before adding the ability to specify an extra button
	 * @param confirmPopup
	 * @param validation
	 * @param callback
	 * @param focusWidget
	 * @param validatingWidget
	 * @param title
	 * @param includeButtons
	 * @param extraButtonTexts
	 * @param extraBtnCallback
	 * @param tintBackground
	 * @param openCentred
	 * @param openAt
	 * @param defaultButton
	 * @param widgets
	 * @return
	 */
	public static ConfirmationPopup showDialogConfirm(ConfirmPopupWindow confirmPopup
			, Validation validation
			, CallbackWithReason<CallbackReason> callback
			, Widget focusWidget
			, ValidatingWidget validatingWidget
			, String title
			, int includeButtons
			, String[] extraButtonTexts // only applies if there is an extra button shown, 1st is text on the button, 2nd is tooltip
			, Runnable extraBtnCallback
			, boolean openCentred
			, final Location2Df openAt
			, int defaultButton
			, boolean makeScrollingRegion
			, Widget ... widgets) {
		return showDialogConfirm(confirmPopup, validation, callback, focusWidget, validatingWidget, title, includeButtons, null, extraButtonTexts, extraBtnCallback
				, openCentred, openAt, defaultButton, makeScrollingRegion, widgets);
	}

	/**
	 * Convenience method to open a popup window containing a moveable frame with a confirm dialog panel inside it
	 * @param confirmPopup
	 * @param validation
	 * @param callback
	 * @param focusWidget
	 * @param validatingWidget
	 * @param title
	 * @param includeButtons
	 * @param extraButton
	 * @param extraButtonTexts
	 * @param extraBtnCallback
	 * @param tintBackground
	 * @param openCentred
	 * @param openAt
	 * @param defaultButton
	 * @param widgets
	 * @return
	 */
	public static ConfirmationPopup showDialogConfirm(final ConfirmPopupWindow confirmPopup
												, Validation validation
												, CallbackWithReason<CallbackReason> callback
												, Widget focusWidget
												, ValidatingWidget validatingWidget
												, String title
												, int includeButtons
												, Widget extraButton
												, String[] extraButtonTexts // only applies if there is an extra button shown, 1st is text on the button, 2nd is tooltip
												, Runnable extraBtnCallback
												, boolean openCentred
												, final Location2Df openAt
												, int defaultButton
												, boolean makeScrollingRegion
												, Widget ... widgets) {

		// frame that will be moveable inside the popup
		ResizableFrame moveablePopup = new ResizableFrame() {
			@Override
			protected void positionChanged() {
				super.positionChanged();
				confirmPopup.framePositionChanged();
			}
			@Override
			protected void sizeChanged() {
				super.sizeChanged();
				confirmPopup.frameSizeChanged();
			}
		};
		
		moveablePopup.setTheme("resizableframe-title");
		moveablePopup.setTitle(title);

		confirmPopup.add(moveablePopup);

		ConfirmationPopup confirmDlg = 
				new ConfirmationPopup(confirmPopup, includeButtons, extraButton, extraButtonTexts, extraBtnCallback, makeScrollingRegion, widgets);
		confirmDlg.setTheme("confirmDialog");
		
		if (defaultButton != NO_BUTTON) {
			confirmDlg.setDefaultButton(defaultButton);
		}

		if (validation != null)
			confirmDlg.setValidation(validation);

		if (callback != null)
			confirmDlg.addCallback(callback);

		if (focusWidget != null)
			confirmPopup.setFocusOnCloseWidget(focusWidget);

		if (validatingWidget != null)
			validatingWidget.setConfirmationDialog(confirmDlg);

		if (validation != null && validation.getErrorText() != null) {
			confirmDlg.setErrorText();
		}

		// the dialog layout is added to the moveable frame
		moveablePopup.add(confirmDlg);

		// and the popup is opened to show everything, closing happens inside the ConfirmationDialog button callbacks
		if (openCentred) {
			confirmPopup.openPopupCentered();			
		}
		else if (openAt != null) {
			confirmPopup.openPopup();
			moveablePopup.setPosition((int)openAt.getX(), (int)openAt.getY());
		}

		if (confirmDlg.getKeyBoardFocusWidget() != null) {
			confirmDlg.getKeyBoardFocusWidget().requestKeyboardFocus();
		}
		
		return confirmDlg;
	}

	public static void showAlert(Widget focusWidget, String text) {
		showAlert(focusWidget, text, TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.ALERT_TITLE));
	}

	public static void showAlert(Widget focusWidget, String text, String title) {
		showDialogConfirm(null, null, focusWidget, title
				, ConfirmationPopup.OK_BUTTON, null, null, true, new Label(text));
	}

	public abstract static class Validation {
		protected String errorText;
		protected CallbackReason buttonReason;

		// allow per widget validation
		public abstract boolean isValid(Widget byWidget);
		public boolean isValid(Widget byWidget, CallbackReason buttonReason) {
			this.buttonReason = buttonReason;
			return isValid(byWidget);
		}

		// allow presetting of error text
		public void setErrorText(String errorText) {
			this.errorText = errorText;
		}
		public String getErrorText() {
			return errorText;
		}
	}

	/**
	 * Subclass this and set the reason in the callback method so can query it after the fact
	 * @author tomas
	 *
	 */
	public abstract static class CallBackKeepReason implements CallbackWithReason<CallbackReason> {
		protected CallbackReason reason;
		public CallbackReason getReason() {
			return reason;
		}
	}

	/**
	 * Sub class of PopupWindow for exclusive use of ConfirmationPopup
	 * @author tomas stubbs
	 */
	public static class ConfirmPopupWindow extends PopupWindow {
		
		private boolean centreChild;
		// flags ensure only does it the first time, unless reset (after data changes)
		private boolean initCentreDone = false;
		private boolean positioningHappening = true;
		private boolean initSizeDone = false;
		private boolean resizingHappening = true;
		private boolean userMovedIt = false;
		private boolean allowClickOrEscapeToClose = false;
		private Widget focusOnCloseWidget;

		/**
		 * Default owner to root pane
		 * @param centreChild
		 */
		public ConfirmPopupWindow(boolean centreChild) {
			this(KeepYaBeat.root, centreChild, true);
		}

		/**
		 * Specific owner
		 * @param owner
		 * @param centreChild
		 */
		public ConfirmPopupWindow(Widget owner, boolean centreChild, boolean enforceDialogClose) {
			super(owner);
			setTheme("confirmPopupTintBackground");
			
			this.centreChild = centreChild;
			
			if (enforceDialogClose) {
				setCloseOnClickedOutside(false);
				setCloseOnEscape(false);
			}
			else {
				allowClickOrEscapeToClose = true;
			}
		}

		/**
		 * Called when the frame moves, so check if it's user or layout movement
		 */
		private void framePositionChanged() {
			if (!positioningHappening) {
				userMovedIt = true;
			}
		}

		/**
		 * Called when the frame resizes, so check if it's layout doing it or something changed inside it that
		 * made it resize itself
		 */
		private void frameSizeChanged() {
			if (!resizingHappening) {
				initSizeDone = false; 
				if (!userMovedIt) {
					initCentreDone = false;
				}
			}
		}

		public void setFocusOnCloseWidget(Widget focusWidget) {
			this.focusOnCloseWidget = focusWidget;
		}

		/**
		 * Called when the Display size is reset, so cause everything to relayout
		 */
		@Override
		public void invalidateLayout() {
			userMovedIt = false;
			super.invalidateLayout();
		}

		/**
		 * Various flagging etc to detect whether the user moved the frame or it resized itself
		 * and then positioning it if required
		 */
		@Override
		protected void layout() {
			int maxWidth = KeepYaBeat.root.getInnerWidth();
			int maxHeight = KeepYaBeat.root.getInnerHeight();
			
			// popup window is max size available
			setSize(maxWidth, maxHeight);
			setPosition(0, 0);
			// a popup window only has one child
			ResizableFrame frame = (ResizableFrame) getChild(0);

			// don't do anything until completely ready (frame has a size and children are there)
			if (frame != null && frame.getWidth() > 0 && frame.getHeight() > 0 && frame.getNumChildren() > 1) {

				if (!initSizeDone) {
					resizingHappening = true;
	
					// check for size of confirmationPopup, may have to cut it down
					// note it's the 2nd child (first is the title label)
					ConfirmationPopup confirmDlg = (ConfirmationPopup) frame.getChild(1);
					
					// set everything to max first and adjust them
					frame.setMaxSize(Short.MAX_VALUE, Short.MAX_VALUE);
					confirmDlg.setMaxSize(Short.MAX_VALUE, Short.MAX_VALUE);			
					if (confirmDlg.scrollPane != null) {
						confirmDlg.scrollPane.setMaxSize(Short.MAX_VALUE, Short.MAX_VALUE);
						confirmDlg.scrollPane.adjustSize();
					}
					confirmDlg.adjustSize();
					frame.adjustSize();
	
					// all done unless the frame is now bigger than the available space
					// but can only do something about it if it has a scrolling region (which can be shrunk)
					if ((frame.getWidth() > maxWidth || frame.getHeight() > maxHeight) && confirmDlg.scrollPane != null) {
						// the minimum size of the frame is its own size less the dlg inside it
						int minFrameW = (frame.getWidth() - confirmDlg.getWidth());
						int minFrameH = (frame.getHeight() - confirmDlg.getHeight());
						
						// the max size for everything else is the total space less the frame min
						// (note: this could be negative if the Display is resized very small, ie. less than min frame size)
						int maxNonFrameW = maxWidth - minFrameW;
						int maxNonFrameH = maxHeight - minFrameH;
						
						// min size for the dialog is its size less any scrollpane
						int minDlgW = confirmDlg.getWidth() - confirmDlg.scrollPane.getWidth();
						int minDlgH = confirmDlg.getHeight() - confirmDlg.scrollPane.getHeight();
	
						// the minimum size of the scrollpane is 1... ie. allow total shrink, but not to 0 which will cause
						// TWL to grab all it can
						int minScrollPaneSize = 1; 
						
						// the max size of the scrollpane is the greater of min size (just set as 1) and
						// maxNonFrame less minDlg
						int maxScrollPaneW = Math.max(maxNonFrameW - minDlgW, minScrollPaneSize);
						int maxScrollPaneH = Math.max(maxNonFrameH - minDlgH, minScrollPaneSize);
						
						// set max on the scrollpane and re-adjust everything
						confirmDlg.scrollPane.setMaxSize(maxScrollPaneW, maxScrollPaneH);
						
						confirmDlg.scrollPane.adjustSize();
						confirmDlg.adjustSize();
						frame.adjustSize();
					}
					else if (confirmDlg.getHeight() > frame.getHeight()) {
						confirmDlg.setSize(confirmDlg.getWidth(), frame.getHeight());
					}

					resizingHappening = false;
					initSizeDone = true;
				}
			
				positioningHappening = true;
				if (centreChild && !initCentreDone) {
					frame.setPosition(maxWidth / 2 - frame.getWidth() / 2,
							maxHeight / 2 - frame.getHeight() / 2);
					initCentreDone = true;
				}
				
				// may have to ensure it didn't go offscreen (from resize) either from centering or otherwise
				if (frame.getX() + frame.getWidth() > maxWidth
						|| frame.getY() + frame.getHeight() > maxHeight
						|| frame.getX() < KeepYaBeat.root.getInnerX()
						|| frame.getY() < KeepYaBeat.root.getInnerY()) {
						
					frame.setPosition(
							Math.max(Math.min(frame.getX()
									, KeepYaBeat.root.getInnerX() + maxWidth - frame.getWidth()), KeepYaBeat.root.getInnerX())
							, Math.max(Math.min(frame.getY()
									, KeepYaBeat.root.getInnerY() + maxHeight - frame.getHeight()), KeepYaBeat.root.getInnerY()));
				}
				positioningHappening = false;
			}
		}

		@Override
		protected boolean handleEventPopup(Event evt) {
			boolean handledEvent = false;
			if (evt.isKeyPressedEvent() && evt.getKeyCode() == Event.KEY_TAB) {
				boolean shiftDown = (((evt.getModifiers() & Event.MODIFIER_LSHIFT) != 0)
						|| ((evt.getModifiers() & Event.MODIFIER_RSHIFT) != 0));
				
				handledEvent = executeTabPress(this, !shiftDown);
			}
			else if (evt.isKeyPressedEvent() && evt.hasKeyChar() && evt.getKeyCode() == Event.KEY_RETURN) {
				handledEvent = executeDefaultButtonOnKeyReturn(this);
			}
			else if (allowClickOrEscapeToClose
					&& (evt.isMouseEvent() && evt.getType() == Event.Type.MOUSE_BTNDOWN
							|| (evt.isKeyPressedEvent() && evt.getKeyCode() == Event.KEY_ESCAPE))) {
				closePopup();
				handledEvent = true;
			}

			if (handledEvent) {
				return true;
			}
			else {
				return super.handleEventPopup(evt);
			}
		}

		@Override
		protected void beforeRemoveFromGUI(GUI gui) {
			super.beforeRemoveFromGUI(gui);
			if (focusOnCloseWidget != null) {
				focusOnCloseWidget.requestKeyboardFocus();
			}
		}

	}


}
