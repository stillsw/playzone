/*
 * Copyright (c) 2008, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Matthias Mann nor the names of its contributors may
 *       be used to endorse or promote products derived from this software
 *       without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.stillwindsoftware.keepyabeat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.openal.AL;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

import com.stillwindsoftware.keepyabeat.control.ModuleManager;
import com.stillwindsoftware.keepyabeat.gui.AboutKybDialog;
import com.stillwindsoftware.keepyabeat.gui.BeatsAndSoundsModule;
import com.stillwindsoftware.keepyabeat.gui.BugReportDialog;
import com.stillwindsoftware.keepyabeat.gui.ConfirmationPopup;
import com.stillwindsoftware.keepyabeat.gui.ImportRhythmsDialog;
import com.stillwindsoftware.keepyabeat.gui.Notification;
import com.stillwindsoftware.keepyabeat.gui.PauseableModule;
import com.stillwindsoftware.keepyabeat.gui.PlayerModule;
import com.stillwindsoftware.keepyabeat.gui.SettingsDialog;
import com.stillwindsoftware.keepyabeat.gui.TagsListDialog;
import com.stillwindsoftware.keepyabeat.gui.TwlRhythmEditor;
import com.stillwindsoftware.keepyabeat.gui.VideoSettings;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.LibraryXmlImpl;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader;
import com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader.LoadProblem;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.LibraryLoadCallback;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlImage;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy;
import com.stillwindsoftware.keepyabeat.platform.twl.UserBrowserProxy.Key;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore;
import com.stillwindsoftware.keepyabeat.player.RhythmDraughtImageStore.BucketImage;
import com.stillwindsoftware.keepyabeat.twlModel.VideoMode;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.CallbackWithReason;
import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.FPSCounter;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuAction;
import de.matthiasmann.twl.MenuManager;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.input.Input;
import de.matthiasmann.twl.model.PersistentIntegerModel;
import de.matthiasmann.twl.model.SimpleChangableListModel;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLRenderer;
import de.matthiasmann.twl.theme.ThemeManager;

/**
 * Keep Ya Beat main class, written like SimpleTest.java 
 * @author Tomas Stubbs
 */
public class KeepYaBeat implements LibraryLoadCallback {

//	// fix that apparently will guarantee use of high-resolution timer, has to
//	// go into main before other stuff loads
//	// setting daemon tells jvm ok to exit if is the only thread left running
//	// (ie. game threads end)
//	static {
//		new Thread() {
//			{
//				setDaemon(true);
//				start();
//			}
//
//			public void run() {
//				while (true) {
//					try {
//						Thread.sleep(Long.MAX_VALUE);
//					} catch (Throwable t) {
//					}
//				}
//			}
//		};
//	}

	// persistent states for Display and theme are handled in this class, so there's no delays
	// waiting for resource manager to start up the persistence manager
	private static final String[] THEME_FILES = { 
		"kybLightBlueNormalFont.xml"
		, "kybLightBlueOpenDyslexicFont.xml"
		, "kybSimpleNormalFont.xml"
		, "kybSimpleOpenDyslexicFont.xml"
		};
	public static final int LIGHT_BLUE_THEME = 0;
	public static final int BASIC_THEME = 2;
	public static final int NORMAL_FONT = 0;
	public static final int OPEN_DYSLEXIC_FONT = 1;
	
	private static final String CURRENT_THEME_IDX = "currentThemeIndex";
	private static final String PREF_DISPLAY_WIDTH = "displayWidth";
	private static final String PREF_DISPLAY_HEIGHT = "displayHeight";
	private static final int MIN_WIDTH = 620;
	private static final int MIN_HEIGHT = 465;
	static PersistentIntegerModel width;
	static PersistentIntegerModel height;

//	public static void main(String[] arg) throws LWJGLException {
//		KeepYaBeat kyb = new KeepYaBeat();
//		kyb.run(new VideoMode(new DisplayMode(KeepYaBeat.width.getValue(), KeepYaBeat.height.getValue()), false));
//	}

	public static final int TABLE_HEADER_HEIGHT = 18;
	
    // scrollbar width from theme, if height is larger than screen, add this to width so vertical
    // bar doesn't make horizontal bar show
    public static int vScrollbarWidth = 0;
    public static int hScrollbarHeight = 0;

	protected final DisplayMode desktopMode;
	protected boolean closeRequested;
	protected static ThemeManager theme;
	protected LWJGLRenderer renderer;
	protected GUI gui;
	private boolean isApplet = false;
	private VideoSettings settings;
	protected VideoSettings.CallbackReason vidDlgCloseReason;
	protected PersistentIntegerModel curThemeIdx;
	public static RootPane root;

	// frames panels manager
	private ModuleManager moduleManager;
	private TwlResourceManager resourceManager;

	public KeepYaBeat() {
		desktopMode = Display.getDisplayMode();
		
		// get optimum dimensions for default based on 80% of the screen size but 800x600 if that's bigger
		int optWidth = Math.max((int) (desktopMode.getWidth() * .8f), 800);
		int optHeight = Math.max((int) (desktopMode.getHeight() * .8f), 600);
		
		curThemeIdx = new PersistentIntegerModel(
				AppletPreferences.userNodeForPackage(KeepYaBeat.class), CURRENT_THEME_IDX, 0, THEME_FILES.length, 0);
		width = new PersistentIntegerModel(
				AppletPreferences.userNodeForPackage(KeepYaBeat.class), PREF_DISPLAY_WIDTH, MIN_WIDTH, Integer.MAX_VALUE, optWidth);
		height = new PersistentIntegerModel(
				AppletPreferences.userNodeForPackage(KeepYaBeat.class), PREF_DISPLAY_HEIGHT, MIN_HEIGHT, Integer.MAX_VALUE, optHeight);

//		System.err.println("****************** WARNING: theme switching is in for dev only (Ctrl+T)");
	}
	
	public static int getWidth() {
		return width.getValue();
	}

	public static int getHeight() {
		return height.getValue();
	}

	public static ThemeManager getTheme() {
		return theme;
	}

	private void loadTheme() throws IOException {
		renderer.syncViewportSize();

//		long startTime = System.nanoTime();
		// NOTE: this code destroys the old theme manager (including it's cache
		// context)
		// after loading the new theme with a new cache context.
		// This allows easy reloading of a theme for development.
		// If you want fast theme switching without reloading then use the
		// existing
		// cache context for loading the new theme and don't destroy the old
		// theme.
		ThemeManager newTheme = ThemeManager.createThemeManager(
				KeepYaBeat.class.getResource(THEME_FILES[curThemeIdx
						.getValue()]), renderer);
		// long duration = System.nanoTime() - startTime;
		// System.out.println("Loaded theme in " + (duration/1000) + " us");

		if (theme != null) {
			theme.destroy();
		}
		theme = newTheme;

		gui.setSize();
		gui.applyTheme(theme);
		vScrollbarWidth = theme.findThemeInfo("vscrollbar").getParameter("minWidth", 0);
		hScrollbarHeight = theme.findThemeInfo("hscrollbar").getParameter("minHeight", 0);
		gui.setBackground(theme.getImageNoWarning("gui.background"));
		
		invalidateVisibleChildren(gui);

		// fire listeners at the end of the next render cycle, this resizes everything
		gui.invokeLater(new Runnable() {
			@Override
			public void run() {
				resourceManager.getListenerSupport().fireAllItemsChanged();
			}
		});
	}

	private void createDisplay(VideoMode mode) throws LWJGLException {
		// resource manager not loaded yet, so hard-code initial title, this will be reset asap
		Display.setTitle("");
		Display.setFullscreen(mode.fullscreen);
		
		Display.setDisplayMode(mode.mode);
		Display.create(); // had previously, but is incompatible with drawing
							// the polygons on the desktop....
							// Display.create(new PixelFormat(0, 0, 0));
		Display.setVSyncEnabled(true);
		Display.setResizable(true);

		setViewport(Display.getDisplayMode().getWidth(), Display.getDisplayMode().getHeight());
	}

	/**
	 * Called in initial set up and also if the display is resized
	 */
	private void setViewport(int w, int h) {
		// taken from the polygon demo
		GL11.glViewport(0, 0, w, h);

		/*
		 * Set our projection matrix. This doesn't have to be done each time we
		 * draw, but usually a new projection needs to be set when the viewport
		 * is resized.
		 */

		float ratio = (float) w / h;
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glFrustum(-ratio, ratio, -1, 1, 2, 64);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();

		// Ensure correct display of polygons
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DITHER);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthRange(0, 1);
		GL11.glDepthFunc(GL11.GL_LESS);
		GL11.glShadeModel(GL11.GL_SMOOTH);
	}
	
	/**
	 * Called during render loop when resize detected
	 */
	private void displayResized() {
		int w = Math.max(Display.getWidth(), MIN_WIDTH), h = Math.max(Display.getHeight(), MIN_HEIGHT);
		// makes the window bounce open, which looks a bit funny
//		if (Display.getWidth() < MIN_WIDTH || Display.getHeight() < MIN_HEIGHT) {
//			try {
//				int x = Display.getX();
//				int y = Display.getY();
//				Display.setDisplayMode(new DisplayMode(w, h));
//				Display.setLocation(x, y);
//			} catch (LWJGLException e) {
//				e.printStackTrace();
//			}
//		}

		if (resourceManager != null) {
			resourceManager.log(LOG_TYPE.info, this, String.format("KeepYaBeat.displayResized: w=%s h=%s", w, h));
		}
		
		// set the size for next time
		width.setValue(w);
		height.setValue(h);

		// module layout space is a rect stored by module manager, reset it before any relayouts
		// no need if it hasn't been initialised yet though, on Windows it seems the display is marked as
		// resized on startup, so this won't yet be there
		if (ModuleManager.getInstance() != null) {
			ModuleManager.getInstance().resetModuleLayoutSpace();
		}
		
		setViewport(w, h);
		renderer.setViewport(0, 0, w, h);

		invalidateVisibleChildren(gui);

		// fire listeners at the end of the next render cycle, this resizes everything
		gui.invokeLater(new Runnable() {
			@Override
			public void run() {
				resourceManager.getListenerSupport().fireAllItemsChanged();
			}
		});
	}
	
	/**
	 * Walk the widget tree and invalidate anything that's currently visible
	 * Called from displayResized
	 * @param widget
	 */
	private void invalidateVisibleChildren(Widget widget) {
		for (int i = 0; i < widget.getNumChildren(); i++) {
			// popups are children of gui
			Widget child = widget.getChild(i);
			if (child.isVisible()) {
				if (widget instanceof MenuManager || child.getClass().getName().contains("TooltipWindow")) {
					child.adjustSize();
				}
				child.invalidateLayout();
				invalidateVisibleChildren(child);
			}
		}
	}
	
	@Override
	public void loadCompletedOk(Library library) {

		// startup persistent states manager causes logging to be set to previous setting
		resourceManager.getPersistentStatesManager();
		
		// user may have set a non-default language, load its message bundle if so
		resourceManager.switchLanguageIfUserSelection();

		gui.invokeLater(new Runnable() {
			@Override
			public void run() {
				moduleManager = ModuleManager.getNewInstance(resourceManager);
				root.setGoodToGo(true, null);
			}
		});
	}

	@Override
	public void loadFailed(final LibraryXmlLoader libraryXmlLoader) {
		
		try {
			// startup persistent states manager causes logging to be set to previous setting
			resourceManager.getPersistentStatesManager();
			
			// user may have set a non-default language, load its message bundle if so
			resourceManager.switchLanguageIfUserSelection();
		} catch (Exception e) {
			// Not able to switch language (unusual but not impossible it's needed
			// not there will just result in default language used for reporting bugs)
			e.printStackTrace();
		}

		gui.invokeLater(new Runnable() {
			@Override
			public void run() {
				root.setGoodToGo(libraryXmlLoader.getLoadProblemSeverity() != LibraryXmlLoader.ERROR_SEVERITY_ABORT, libraryXmlLoader);
			}
		});
	}

	// @SuppressWarnings("SleepWhileInLoop")
	public void mainLoop() throws LWJGLException, IOException {

		LibraryXmlLoader xmlLoader = new LibraryXmlLoader();
		resourceManager = TwlResourceManager.setNewInstance(xmlLoader);
		resourceManager.log(LOG_TYPE.info, this, "KeepYaBeat.mainLoop: config;"
				+"\n\t os.name="+System.getProperty("os.name")
				+"\n\t java.vendor="+System.getProperty("java.vendor")
				+"\n\t java.version="+System.getProperty("java.version")
				+"\n\t user.home="+System.getProperty("user.home")
				+"\n\t user.name="+System.getProperty("user.name")
				);
		resourceManager.initSounds();
		resourceManager.setLibrary(new LibraryXmlImpl(resourceManager)); 
		resourceManager.log(LOG_TYPE.info, this, "KeepYaBeat.mainLoop: version "+resourceManager.getVersionInfo());
		
		// set title and also attempt to set mac system menu/about title
		String appTitle = resourceManager.getLocalisedString(TwlLocalisationKeys.APP_TITLE);
		Display.setTitle(appTitle);
		
		// try to set the icons
		try {
			Display.setIcon(IconLoader.load());
		} catch (Exception e) {
			e.printStackTrace();
		}

		root = new RootPane();
		root.setSize(width.getValue(), height.getValue()); // if there are load errors, the confirm dialog needs this size to be set so it can layout correctly
		renderer = new LWJGLRenderer();
		renderer.setUseSWMouseCursors(true);
		// use my slightly modified version of TWL's input class, this traps Ctrl+P to take a screen shot
		gui = new GUI(root, renderer, new KybLWJGLInput());

		loadTheme();

		// get the xml library loading in a separate thread
		try {
			if (resourceManager.isLibraryExists()) {
				resourceManager.loadXmlLibrary(this);				
			}
			else {
				resourceManager.seedNewLibrary(this, (LibraryXmlImpl) resourceManager.getLibrary());
			}
		} catch (Exception e) {
			loadFailed(xmlLoader);
		}

		while (!Display.isCloseRequested() && !closeRequested) {
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

			render();

			try {
				gui.update();
				
				// following gui update all twl objects have moved, so run the scheduler
				if (root.goodToGo) {
					resourceManager.getPendingTasksScheduler().update();
				}
			} catch (Exception e) {
				resourceManager.log(LOG_TYPE.error, this, "KeepYaBeat.mainLoop: uncaught error "+e.getMessage());
				e.printStackTrace();
				resourceManager.getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.UNEXPECTED_PROGRAM_ERROR, true, e);
			}
			
			Display.update();

			if (root.reduceLag) {
				TestUtils.reduceInputLag();
			}

			if (!isApplet
					&& vidDlgCloseReason == VideoSettings.CallbackReason.ACCEPT) {
				settings.storeSettings();
				VideoMode vm = settings.getSelectedVideoMode();
				gui.destroy();
				renderer.getActiveCacheContext().destroy();
				Display.destroy();
				createDisplay(vm);
				loadTheme();
			}
			vidDlgCloseReason = null;

			if (!Display.isActive()) {
				gui.clearKeyboardState();
				gui.clearMouseState();
			}

			if (!Display.isVisible()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException unused) {
					Thread.currentThread().interrupt();
				}
			}
		}
		
		if (resourceManager != null) {
			resourceManager.releaseResources();
		}
		// dropping out of main loop
	}

	/**
	 * Render the current frame
	 */
	private void render() {
		// clear the screen
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT); // Clear
																			// screen
																			// and
																			// z-buffer
		// this would be the place to draw into the background
		
		// detect resize
		if (Display.wasResized()) {
			displayResized();
		}
	} 

	public void run(VideoMode mode) {
		try {
			createDisplay(mode);

			mainLoop();
		} catch (Throwable ex) {
			TestUtils.showErrMsg(ex);
		}

		Display.destroy();
		AL.destroy();
		System.exit(0);
	}

	public class RootPane extends Widget {
		public static final int MENU_BAR_RESERVED_HEIGHT = 40;
		
		private Label moduleName;
		private Label moduleExtra;
		private Button undoBtn;
		private ComboBox<String> moreBtn;
		private ToggleButton playerModuleBtn;
		private ToggleButton beatsSoundsModuleBtn;
//		private final BoxLayout btnBox;
		private DialogLayout menuBar;
		boolean reduceLag = true;
		private final FPSCounter fpsCounter;
		private boolean initComplete = false;
		private boolean gotImagesFromTheme = false;
		private boolean goodToGo = false;
		private DrawingSurface eventRecipient;
		
		// images loaded from theme
		private TwlImage beatImg55;
		private TwlImage beatImg25;
		private TwlImage beatImg11;
		private TwlImage beatImg3;
		private TwlImage beatImg55Overlay;
		private TwlImage beatImg25Overlay;
		private TwlImage beatImg11Overlay;
		private TwlImage beatImg3Overlay;
		private TwlImage beatImg1;
		private TwlImage selectedImg55;
		private TwlImage selectedImg25;
		private TwlImage selectedImg11;
		private TwlImage zoomInIcon22;
		private TwlImage beatDivisionHoriLarge;
		private TwlImage beatDivisionLeft3x3;
		private TwlImage beatDivisionRight3x3;
		private TwlImage beatDivision1x1;
		private TwlImage numbersBgd32;
		private TwlImage numbersBgd7;
		private TwlImage subZoneImg25Overlay;
		private TwlImage subZoneImg55Overlay;
		private TwlImage fraction_1_4_25high;
		private TwlImage fraction_1_3_25high;
		private TwlImage fraction_1_2_25high;
		private TwlImage fraction_2_3_25high;
		private TwlImage fraction_3_4_25high;
		private TwlImage fractionLine;
		private TwlImage fractionHoveredLine;			
		private TwlImage progressMarkerImg;
		private TwlImage playedBeatRipple;
		private TwlImage explodeBeatImg72;

		public RootPane() {
			setTheme("rootModule");
			
//			setupMenuBar();

			fpsCounter = new FPSCounter();
			fpsCounter.setFramesToCount(10);

			// the rest added once the library is loaded ok
			add(fpsCounter);
			
			// see goodToGo()
			initComplete = true;
		}

		public void setupMenuBar() {
			// panel to hold the top menu buttons
			menuBar = new DialogLayout();
			menuBar.setTheme("menuBar");

			// module name is on the left
			moduleName = new Label();
			moduleName.setTheme("whiteLabel");
			
			moduleExtra = new Label();
			moduleExtra.setTheme("largeTitlesLabel");
			
			// the buttons are held in box
//			btnBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
//			btnBox.setTheme("buttonBox");

			undoBtn = new Button() {
				@Override
				public Object getTooltipContent() {
					return resourceManager.getUndoableCommandController().getUndoDesc();
				}
			};

			undoBtn.addCallback(new Runnable() {
						public void run() {
							resourceManager.getUndoableCommandController().undoLastCommand();
							undoBtn.setTooltipContent(resourceManager.getUndoableCommandController().getUndoDesc());
						}
					});
					
			undoBtn.setTheme("menuUndoBtn");
			undoBtn.setCanAcceptKeyboardFocus(false);
			undoBtn.setEnabled(false); // start disabled
//			btnBox.add(undoBtn);

			// put a callback on the controller to enable/disable the undo button 
			resourceManager.getUndoableCommandController().setStackChangeCallback(new Runnable() {
						@Override
						public void run() {
							undoBtn.setEnabled(resourceManager.getUndoableCommandController().hasCommands());
						}			
					});

			// add a button that toggles the rhythm player
			playerModuleBtn = addButton(resourceManager.getLocalisedString(TwlLocalisationKeys.APP_MENU_PLAYER)
					, resourceManager.getLocalisedString(TwlLocalisationKeys.APP_MENU_PLAYER_TOOLTIP),
					new Runnable() {
						public void run() {
							setEnableMenuButtons(false);
							moduleManager.toggleTopLevelModule(PlayerModule.getInstance());
						}
					});

			// add a button that toggles the beats and sounds 
			beatsSoundsModuleBtn = addButton(resourceManager.getLocalisedString(TwlLocalisationKeys.APP_MENU_BEATS_SOUNDS)
					, resourceManager.getLocalisedString(TwlLocalisationKeys.APP_MENU_BEATS_SOUNDS_TOOLTIP),
					new Runnable() {
						public void run() {
							setEnableMenuButtons(false);
							moduleManager.toggleTopLevelModule(BeatsAndSoundsModule.getInstance());
						}
					});

			SimpleChangableListModel<String> comboModel = new SimpleChangableListModel<String>();
			comboModel.addElement(resourceManager.getLocalisedString(TwlLocalisationKeys.APP_MENU_MORE));
			moreBtn = new ComboBox<String>(comboModel) {
				@Override
				protected boolean openPopup() {
					popupMoreOptions();
					return true;
				}							
			};
			
			moreBtn.setTheme("combobox");
			moreBtn.setSelected(0);
			moreBtn.setTooltipContent(TwlLocalisationKeys.APP_MENU_MORE_TOOLTIP);
			moreBtn.setCanAcceptKeyboardFocus(false);
//			btnBox.add(moreBtn);
			
			Label spacer1 = new Label(), spacer2 = new Label(), spacer3 = new Label();
			spacer1.setTheme("moduleBarBtnSpacer");
			spacer2.setTheme("moduleBarBtnSpacer");
			spacer3.setTheme("moduleBarBtnSpacer");
			
			menuBar.setHorizontalGroup(
					menuBar.createSequentialGroup()
								.addWidget(moduleName)
								.addWidget(moduleExtra)
								.addGap()
								.addWidget(undoBtn)
								.addGap(DialogLayout.MEDIUM_GAP)
								.addWidget(spacer1)
								.addGap(DialogLayout.SMALL_GAP)
								.addWidget(playerModuleBtn)
								.addGap(DialogLayout.SMALL_GAP)
								.addWidget(spacer2)
								.addGap(DialogLayout.SMALL_GAP)
								.addWidget(beatsSoundsModuleBtn)
								.addGap(DialogLayout.SMALL_GAP)
								.addWidget(spacer3)
								.addGap(DialogLayout.MEDIUM_GAP)
								.addWidget(moreBtn));
			menuBar.setVerticalGroup(menuBar.createParallelGroup()
					.addWidget(moduleName)
					.addWidget(moduleExtra)
					.addWidget(undoBtn)
					.addWidget(spacer1)
					.addWidget(playerModuleBtn)
					.addWidget(spacer2)
					.addWidget(beatsSoundsModuleBtn)
					.addWidget(spacer3)
					.addWidget(moreBtn));	
			
			add(menuBar);
		}
		
		/**
		 * Called from the task that opens/closes modules in module manager to set the menu button on/off
		 * @param module
		 * @param active
		 */
		public void setMenuButtonActive(PauseableModule module, boolean active) {
			if (module instanceof PlayerModule) {
				playerModuleBtn.setActive(active);
			}
			else if (module instanceof BeatsAndSoundsModule) {
				beatsSoundsModuleBtn.setActive(active);
			}
		}
		
		/**
		 * Called from module manager once it completes its module open/close tasks.
		 * And from this class as as soon as one is pressed.
		 */
		public void setEnableMenuButtons(boolean set) {
			playerModuleBtn.setEnabled(set);
			beatsSoundsModuleBtn.setEnabled(set);
		}
		
		public FPSCounter getFpsCounter() {
			return fpsCounter;
		}

		/**
		 * Load the images into the store for all modules that need them
		 */
		@Override
		protected void applyTheme(ThemeInfo themeInfo) {
			super.applyTheme(themeInfo);
			// in case of theme switch
			if (gotImagesFromTheme) {
				resourceManager.log(LOG_TYPE.info, this, "KeepYaBeat.root.applyTheme: reload image store, clearing images");
				RhythmDraughtImageStore imageStore = resourceManager.getRhythmDraughtImageStore();
				imageStore.destroy(false);
			}

			// load up the images from the theme (could be after switch)
			beatImg55 = new TwlImage(themeInfo.getImage("beatImg55"));
			beatImg25 = new TwlImage(themeInfo.getImage("beatImg25"));
			beatImg11 = new TwlImage(themeInfo.getImage("beatImg11"));
			beatImg3 = new TwlImage(themeInfo.getImage("beatImg3"));
			beatImg55Overlay = new TwlImage(themeInfo.getImage("beatImg55Overlay"));
			beatImg25Overlay = new TwlImage(themeInfo.getImage("beatImg25Overlay"));
			beatImg11Overlay = new TwlImage(themeInfo.getImage("beatImg11Overlay"));
			beatImg3Overlay = new TwlImage(themeInfo.getImage("beatImg3Overlay"));
			beatImg1 = new TwlImage(themeInfo.getImage("beatImg1"));
			selectedImg55 = new TwlImage(themeInfo.getImage("selectedImg55"));
			selectedImg25 = new TwlImage(themeInfo.getImage("selectedImg25"));
			selectedImg11 = new TwlImage(themeInfo.getImage("selectedImg11"));
			zoomInIcon22 = new TwlImage(themeInfo.getImage("zoomInIcon22"));
			beatDivisionHoriLarge = new TwlImage(themeInfo.getImage("beatDivisionHoriLarge"));
			beatDivisionLeft3x3 = new TwlImage(themeInfo.getImage("beatDivisionLeft3x3"));
			beatDivisionRight3x3 = new TwlImage(themeInfo.getImage("beatDivisionRight3x3"));
			beatDivision1x1 = new TwlImage(themeInfo.getImage("beatDivision1x1"));
			numbersBgd32 = new TwlImage(themeInfo.getImage("numbersBgd32"));
			numbersBgd7 = new TwlImage(themeInfo.getImage("numbersBgd7"));
			subZoneImg25Overlay = new TwlImage(themeInfo.getImage("subZoneImg25Overlay"));
			subZoneImg55Overlay = new TwlImage(themeInfo.getImage("subZoneImg55Overlay"));
			fraction_1_4_25high = new TwlImage(themeInfo.getImage("fraction_1_4_25high"));
			fraction_1_3_25high = new TwlImage(themeInfo.getImage("fraction_1_3_25high"));
			fraction_1_2_25high = new TwlImage(themeInfo.getImage("fraction_1_2_25high"));
			fraction_2_3_25high = new TwlImage(themeInfo.getImage("fraction_2_3_25high"));
			fraction_3_4_25high = new TwlImage(themeInfo.getImage("fraction_3_4_25high"));
			fractionLine = new TwlImage(themeInfo.getImage("fractionLine"));
			fractionHoveredLine = new TwlImage(themeInfo.getImage("fractionHoveredLine"));			
			progressMarkerImg = new TwlImage(themeInfo.getImage("progressMarkerImg"));
			playedBeatRipple = new TwlImage(themeInfo.getImage("playedBeatRipple"));
			explodeBeatImg72 = new TwlImage(themeInfo.getImage("explodeBeatImg72"));
			
			// again, could be after theme switch, in which case the method setGoodToGo called from loading
			// library is all done, have to load them here
			if (gotImagesFromTheme) {
				loadImageStore();
			}
			else {
				gotImagesFromTheme = true;
			}
		}

		/**
		 * TODO not this way, can simply not add stuff until ready... review all this
		 * Called when the library is loaded... but that's in another thread, so
		 * check init is completed and wait for it if not
		 */
		void setGoodToGo(boolean canGoOn, LibraryXmlLoader libraryXmlLoader) {
			while (!initComplete || !gotImagesFromTheme) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}

			goodToGo = canGoOn;

			if (libraryXmlLoader != null && libraryXmlLoader.getLoadProblemSeverity() > LibraryXmlLoader.ERROR_SEVERITY_NONE) {

				// create a set to remove duplicate messages
				HashSet<String> errors = new HashSet<String>();
				for (Iterator<LoadProblem> it = libraryXmlLoader.getLoadProblems(); it.hasNext();) {
					LoadProblem problem = it.next();
					String[] params = problem.getParams();
					String message = resourceManager.getLocalisedString(problem.getCoreId());
					if (params != null && params.length > 0) {
						message = String.format(message, (Object[])params);
					}
					errors.add(message);
				}
				
				Label[] errorLabels = new Label[errors.size()+1];
				int i = 0;
				errorLabels[i++] = (new Label(resourceManager.getLocalisedString(TwlLocalisationKeys.PROBLEMS_LOADING_DATA_LABEL)));
				for (String error : errors) {
					errorLabels[i++] = new Label(error);
				}
				

				CallbackWithReason<ConfirmationPopup.CallbackReason> callback = new CallbackWithReason<ConfirmationPopup.CallbackReason>() {
					public void callback(ConfirmationPopup.CallbackReason reason) {
						if (goodToGo) {
							// go to loaded ok and round again, seen the messages
							loadCompletedOk(TwlResourceManager.getInstance().getLibrary());
						}
						else {
							System.exit(-1);
						}
					}
				};
				
				// some error encountered, show it
				ConfirmationPopup.showDialogConfirm(new ConfirmationPopup.ConfirmPopupWindow(true), null, callback, this, null
		        		, resourceManager.getLocalisedString(TwlLocalisationKeys.PROBLEMS_LOADING_DATA_TITLE) 
		        		, ConfirmationPopup.OK_BUTTON | ConfirmationPopup.EXTRA_BUTTON
		        		, makeReportBugButton(), null, null
		        		, true, null, ConfirmationPopup.OK_BUTTON, true, errorLabels);
			}
			
			// tell module manager to restore last open module
			else if (goodToGo) {
				loadImageStore();
				
				resourceManager.log(LOG_TYPE.debug, this, "KeepYaBeat.root.setGoodToGo: restore last open module");
				ModuleManager.getInstance().restoreLastOpenModule();

//				setupMenuBar();
			}
		}

		public Button makeReportBugButton() {
	    	Button offerReportBugBtn = new Button(((TwlResourceManager)resourceManager)
	    			.getLocalisedString(TwlLocalisationKeys.MENU_REPORT_BUG));
	    	offerReportBugBtn.setTheme("genericBtn");
	    	offerReportBugBtn.addCallback(new Runnable() {
					@Override
					public void run() {
						new BugReportDialog();
					}
		    	});
	    	
	    	return offerReportBugBtn;
		}
		
		// constants for the next method only
		private static final int BEAT_IMAGE_55_MIN_SIZE = 36;
		private static final int BEAT_IMAGE_25_MIN_SIZE = 16;
		private static final int BEAT_IMAGE_11_MIN_SIZE = 7;
		private static final int BEAT_IMAGE_3_MIN_SIZE = 3;
		private static final int BEAT_IMAGE_1_MIN_SIZE = 1;
		
		/**
		 * Only called when good to go, and after applyTheme() has got the images from the theme
		 */
		private void loadImageStore() {
			RhythmDraughtImageStore imageStore = resourceManager.getRhythmDraughtImageStore();
			resourceManager.log(LOG_TYPE.debug, this, "KeepYaBeat.root.loadImageStore: load images to image store");

			// load the bucket images first to store a base image for each
			imageStore.loadImages(
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT, beatImg55, BEAT_IMAGE_55_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT, beatImg25, BEAT_IMAGE_25_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT, beatImg11, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT, beatImg3, BEAT_IMAGE_3_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT, beatImg1, BEAT_IMAGE_1_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_BEAT, beatImg11, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_BEAT, beatImg3, BEAT_IMAGE_3_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_BEAT, beatImg1, BEAT_IMAGE_1_MIN_SIZE),
//					// enlarged sub beats are used for both dragging in a sub zone and for the sub-beats themselves in there
//					new BucketImage(RhythmDraughtImageStore.DraughtImageType.ENLARGED_SUB_BEAT, beatImg25, 25),
//					new BucketImage(RhythmDraughtImageStore.DraughtImageType.ENLARGED_SUB_BEAT, beatImg11, 11),
				
				// overlay images are drawn over the beat after other stuff (numbers/subbeats)
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT_OVERLAY, beatImg55Overlay, BEAT_IMAGE_55_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT_OVERLAY, beatImg25Overlay, BEAT_IMAGE_25_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT_OVERLAY, beatImg11Overlay, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FULL_BEAT_OVERLAY, beatImg3Overlay, BEAT_IMAGE_3_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_BEAT_OVERLAY, beatImg11Overlay, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_BEAT_OVERLAY, beatImg3Overlay, BEAT_IMAGE_3_MIN_SIZE),
				// overlay images for selection are drawn over the normal overlay for selected beats
				// same images used for full/sub/subzone
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SELECTED_FULL_BEAT_OVERLAY, selectedImg55, BEAT_IMAGE_55_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SELECTED_FULL_BEAT_OVERLAY, selectedImg25, BEAT_IMAGE_25_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SELECTED_FULL_BEAT_OVERLAY, selectedImg11, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SELECTED_SUB_BEAT_OVERLAY, selectedImg11, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SELECTED_SUB_ZONE_OVERLAY, selectedImg55, BEAT_IMAGE_55_MIN_SIZE),
				// for numbers background use the beat image for bucket 25 if using the larger font
				// which has a width of 32 to draw the number "22"
				// smaller numbers use the 3 width bucket
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.NUMBERS_BACKGRD, numbersBgd32, 22),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.NUMBERS_BACKGRD, numbersBgd7, 7),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_ZONE_ACTIVE, beatImg55, BEAT_IMAGE_55_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_ZONE_ACTIVE, beatImg25, BEAT_IMAGE_25_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_ZONE_ACTIVE, beatImg11, BEAT_IMAGE_11_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_ZONE_OVERLAY, subZoneImg25Overlay, BEAT_IMAGE_25_MIN_SIZE),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.SUB_ZONE_OVERLAY, subZoneImg55Overlay, BEAT_IMAGE_55_MIN_SIZE),
				// fractions width of widest (2/3) is 21
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_14, fraction_1_4_25high, 21),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_13, fraction_1_3_25high, 21),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_12, fraction_1_2_25high, 21),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_23, fraction_2_3_25high, 21),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_34, fraction_3_4_25high, 21),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_LINE, fractionLine, 6),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.FRACTION_HOVERED_LINE, fractionHoveredLine, 6),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.ZOOM_IN_ICON, zoomInIcon22, 22),
				
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.BEAT_DIVISION_HORI, beatDivisionHoriLarge, 11),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.BEAT_DIVISION_HORI, beatDivision1x1, 1),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.BEAT_DIVISION_LEFT, beatDivisionLeft3x3, 3),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.BEAT_DIVISION_RIGHT, beatDivisionRight3x3, 3),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.PROGRESS_INDICATOR, progressMarkerImg, 1), // will always find it, it draws at image size anyway
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.PLAYED_BEAT_EFFECT, playedBeatRipple, 10),
				new BucketImage(RhythmDraughtImageStore.DraughtImageType.EXPLODE_BEAT_EFFECT, explodeBeatImg72, 1));

			resourceManager.log(LOG_TYPE.debug, this, "KeepYaBeat.root.loadImageStore: load images to image store done");
		}
		
		ToggleButton addButton(String text, String toolTip, Runnable cb) {
			ToggleButton btn = new ToggleButton(text);
			btn.setTheme("moduleBarBtn");
			btn.setActive(false);
			btn.addCallback(cb);
			btn.setCanAcceptKeyboardFocus(false);
			btn.setTooltipContent(toolTip);
			invalidateLayout();
			return btn;
		}
		
		/**
		 * Canvas or other can set it self up to receive events
		 * allowing it to get first access to key events and screen mouse
		 * events, not just those inside its area.
		 * @param eventRecipient
		 */
		public void setEventRecipient(DrawingSurface eventRecipient) {
			resourceManager.log(LOG_TYPE.verbose, this, "KeepTheBeat.root.setEventRecipient: "+eventRecipient);
			this.eventRecipient = eventRecipient;
		}

		/**
		 * Capture key presses so can handle back event with escape
		 */
		@Override
		protected boolean handleEvent(Event evt) {
			boolean handledEvent = false;

			if (!handledEvent && eventRecipient != null) {
				if (evt.isKeyPressedEvent() && evt.hasKeyChar() && evt.getKeyCode() == Event.KEY_ESCAPE) {
					handledEvent = eventRecipient.triggeredBackEvent();
				}
			}
			
			if (handledEvent) {
				return true;
			}
			else {
				return super.handleEvent(evt);
			}
		}
		
		@Override
		protected void layout() {
			// fps counter bottom right
			fpsCounter.adjustSize();
			fpsCounter.setPosition(getInnerWidth() - fpsCounter.getWidth(),
					getInnerHeight() - fpsCounter.getHeight());

			
			
			if (goodToGo) {
				if (undoBtn != null) {
					undoBtn.setSize(undoBtn.getMaxWidth(), undoBtn.getMaxHeight());
				}

				if (menuBar != null) {
					moduleName.adjustSize();
					menuBar.setSize(getInnerWidth(), MENU_BAR_RESERVED_HEIGHT);
					menuBar.setPosition(getInnerX(), getInnerY());
				}
			}
		}

		// @Override
		// protected void afterAddToGUI(GUI gui) {
		// super.afterAddToGUI(gui);
		// validateLayout();
		// }
		//
		// @Override
		// protected boolean handleEvent(Event evt) {
		// if(evt.getType() == Event.Type.KEY_PRESSED &&
		// evt.getKeyCode() == Event.KEY_L &&
		// (evt.getModifiers() & Event.MODIFIER_CTRL) != 0 &&
		// (evt.getModifiers() & Event.MODIFIER_SHIFT) != 0) {
		// reduceLag ^= true;
		// System.out.println("reduceLag = " + reduceLag);
		// }
		//
		// return super.handleEvent(evt);
		// }

		/**
		 * Called by the module manager when loading/unloading a module
		 * @param name
		 */
		public void setModuleName(String name) {
			//moduleName.setText(name);
			moduleExtra.setText("");
		}
		
		/**
		 * Called by any module (currently only player group) to add
		 * large title next to module name (so for rhythm name)
		 * @param text
		 */
		public void setModuleExtra(String text) {
			moduleExtra.setText(text);
		}

		/**
		 * Called from the More... button, shows a popup menu of further options
		 */
		void popupMoreOptions() {
	        // add a menu of options
			Menu menu = new Menu() ;
			menu.setTheme("menumanager");

			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_GENERAL_HELP), new Runnable() {
				@Override
				public void run() {
					UserBrowserProxy.showWebPage(Key.general);
				}
			}));
			
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MODULE_TAGS), new Runnable() {
				@Override
				public void run() {
					TagsListDialog.popupTagsList(null, null);
				}
			}));
			
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MODULE_SETTINGS), new Runnable() {
				@Override
				public void run() {
					new SettingsDialog();
				}
			}));
	        
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_IMPORT_RHYTHMS), new Runnable() {
				@Override
				public void run() {
					new ImportRhythmsDialog();
				}
			}));
	        
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_REPORT_BUG), new Runnable() {
				@Override
				public void run() {
					new BugReportDialog();
				}
			}));
	        
			menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_ABOUT_KYB), new Runnable() {
				@Override
				public void run() {
					new AboutKybDialog();
				}
			}));
	        
			// social menu action has a background image instead of text, although it doesn't show, supply
			// blank anyway in case 
			menu.add(new MenuAction("       ", new Runnable() {
					@Override
					public void run() {
						UserBrowserProxy.showWebPage(Key.home);
					}
				}) {
					@Override
				    protected Widget createMenuWidget(MenuManager mm, int level) {
						Button b = (Button) super.createMenuWidget(mm, level);
						b.setTheme("socialMenuBtn");
				        return b;
				    }
			});
	        
			menu.openPopupMenu(menuBar);
		}

		/**
		 * Checks for a popup open that is NOT the rhythmEditor owner (since itself opens in a popup window)
		 * @return
		 */
		public boolean isNonRhythmEditorPopupOpen() {
			if (hasOpenPopups()) {
				mainLoop:
				for (int i = 0; i < getGUI().getNumChildren(); i++) {
					// popups are children of gui
					Widget child = getGUI().getChild(i);
					
					if (child instanceof PopupWindow) {
						// is one of them the owner of rhythm editor, if so ignore it
						for (int j = 0; j < child.getNumChildren(); j++) {
							Widget grandChild = child.getChild(j);
							if (grandChild instanceof TwlRhythmEditor) {
								continue mainLoop;
							}
						}
						
						// got this far, must be another popup
						return true;
					}
				}
			}

			return false;
		}
		
		/**
		 * Called from TwlGuiManager to show a message as a notification
		 * @param message
		 */
		public void showNotification(String message) {
			Widget owner = this;
			
			// check for another owner
			if (hasOpenPopups()) {
				for (int i = 0; i < getGUI().getNumChildren(); i++) {
					// popups are children of gui
					Widget child = getGUI().getChild(i);
					if (child instanceof PopupWindow) {
						owner = child;
					}
				}
			}

			new Notification(owner, message);
		}
	
		/**
		 * Themes are on the even numbers, the odd numbers in between represent the open dyslexic fonts.
		 * So, detect the current font before deciding which index to switch to
		 * @param which
		 */
		public void chooseTheme(int which) {
        	int fontAdjust = curThemeIdx.getValue() % 2;
            final int newThemeIdx = which * 2 + fontAdjust;

            if (newThemeIdx != curThemeIdx.getValue()) {
				gui.invokeLater(new Runnable() {
		            public void run() {
		                curThemeIdx.setValue(newThemeIdx);
		                try {
		                    loadTheme();
		                } catch(IOException ex) {
		                    ex.printStackTrace();
		                }
		            }
		        });		
            }
		}
		
		/**
		 * Passes 0 for normal font and 1 for open dyslexic font.
		 * @param fontIdx
		 */
		public void chooseFont(final int fontIdx) {
			gui.invokeLater(new Runnable() {
	            public void run() {
	            	// current value less the mod gets the base, add the requested font to that
	            	int baseTheme = curThemeIdx.getValue() - curThemeIdx.getValue() % 2;
	                curThemeIdx.setValue(baseTheme + fontIdx);
	                try {
	                    loadTheme();
	                } catch(IOException ex) {
	                    ex.printStackTrace();
	                }
	            }
	        });		
		}
		
		/*
		 * Resets the display size and theme to defaults (display won't show up till next next load)
		 */
		public void resetDefaultDisplayAndTheme() {
			AppletPreferences.userNodeForPackage(KeepYaBeat.class).remove(PREF_DISPLAY_WIDTH);
			AppletPreferences.userNodeForPackage(KeepYaBeat.class).remove(PREF_DISPLAY_HEIGHT);
			
			gui.invokeLater(new Runnable() {
	            public void run() {
	            	curThemeIdx.setValue(0);
	                try {
	                    loadTheme();
	                } catch(IOException ex) {
	                    ex.printStackTrace();
	                }
	            }
	        });		
		}
		
		/**
		 * The base theme names
		 * @return
		 */
		public String[] getAvailableThemeNames() {
			return new String[] {
					resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_THEME_LIGHT_BLUE_LABEL)	
					, resourceManager.getLocalisedString(TwlLocalisationKeys.SETTINGS_THEME_BASIC_LABEL)	
			};
		}
		
		/**
		 * Returns the base theme index (ie. 0 for light blue, 1 for basic)
		 * Used for setting the selected index on the combo based on the above theme names method
		 * @return
		 */
		public int getCurrentTheme() {
			return curThemeIdx.getValue() / 2;
		}
		
		/**
		 * Theme files for open dyslexic font are indexed with odd numbers
		 * @return
		 */
		public boolean usingOpenDyslexicFont() {
			return curThemeIdx.getValue() % 2 == 1;
		}
		
		/**
		 * Called in twl persistence manager's getDump()
		 * @return
		 */
		public String getCurrentThemeXmlFileName() {
			return THEME_FILES[curThemeIdx.getValue()];
		}

		public Button getUndoButton() {
			return undoBtn;
		}

		public Widget getMoreButton() {
			return moreBtn;
		}
	}

	/*
	 * Copyright (c) 2008-2010, Matthias Mann
	 *
	 * All rights reserved.
	 *
	 * Redistribution and use in source and binary forms, with or without
	 * modification, are permitted provided that the following conditions are met:
	 *
	 *     * Redistributions of source code must retain the above copyright notice,
	 *       this list of conditions and the following disclaimer.
	 *     * Redistributions in binary form must reproduce the above copyright
	 *       notice, this list of conditions and the following disclaimer in the
	 *       documentation and/or other materials provided with the distribution.
	 *     * Neither the name of Matthias Mann nor the names of its contributors may
	 *       be used to endorse or promote products derived from this software
	 *       without specific prior written permission.
	 *
	 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
	 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
	 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
	 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
	 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
	 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
	 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
	 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
	 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
	 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
	 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
	 * @author Mattias Mann
	 * @author tomas stubbs - just added a tiny bit to trap Ctrl+P for taking a screenshot
	 */
	private class KybLWJGLInput implements Input {

	    private boolean wasActive;

	    public boolean pollInput(GUI gui) {
	        boolean active = Display.isActive();
	        if(wasActive && !active) {
	            wasActive = false;
	            return false;
	        }
	        wasActive = active;
	        
	        if(Keyboard.isCreated()) {
	            while(Keyboard.next()) {
	            	int eventKey = Keyboard.getEventKey();
	            	char eventChar = Keyboard.getEventCharacter();
	            	boolean eventPressed = Keyboard.getEventKeyState();

	            	if (eventPressed 
	            		&& (eventKey == Keyboard.KEY_P || eventKey == Keyboard.KEY_T)
            			&& (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {

	            		if (eventKey == Keyboard.KEY_P) {
		            		// trap Ctrl+P and don't forward to gui
		            		resourceManager.takeScreenShot();
	            		}
//	            		else if (eventKey == Keyboard.KEY_T) {
//	            			// toggle theme
//	            			toggleTheme();
//	            		}
	            	}
	            	else {
	            		gui.handleKey(eventKey, eventChar, eventPressed);
	            	}
	            }
	        }
	        if(Mouse.isCreated()) {
	            while(Mouse.next()) {
	                gui.handleMouse(
	                        Mouse.getEventX(), gui.getHeight() - Mouse.getEventY() - 1,
	                        Mouse.getEventButton(), Mouse.getEventButtonState());

	                int wheelDelta = Mouse.getEventDWheel();
	                if(wheelDelta != 0) {
	                    gui.handleMouseWheel(wheelDelta / 120);
	                }
	            }
	        }
	        return true;
	    }

//		/**
//		 * For now called by Ctrl+T
//		 */
//		private void toggleTheme() {
//			gui.invokeLater(new Runnable() {
//	            public void run() {
//	                curThemeIdx.setValue((curThemeIdx.getValue() + 1) % THEME_FILES.length);
//	                try {
//	                    loadTheme();
//	                } catch(IOException ex) {
//	                    ex.printStackTrace();
//	                }
//	            }
//	        });		
//		}
//	
	}


}
