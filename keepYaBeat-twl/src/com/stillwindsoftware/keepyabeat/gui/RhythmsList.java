package com.stillwindsoftware.keepyabeat.gui;

import org.lwjgl.input.Mouse;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.Callback;
import com.stillwindsoftware.keepyabeat.gui.TagsPickList.PickedTagReceiver;
import com.stillwindsoftware.keepyabeat.gui.widgets.NameAndTagsBox;
import com.stillwindsoftware.keepyabeat.gui.widgets.NameAndTagsBox.TagListButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmImage;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmImage.RhythmImageValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmSetNameConfirmation;
import com.stillwindsoftware.keepyabeat.gui.widgets.TagList;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.Tags;
import com.stillwindsoftware.keepyabeat.model.transactions.CloneRhythmCommand;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.model.transactions.RhythmCommandAdaptor;
import com.stillwindsoftware.keepyabeat.platform.PersistentStatesManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlFont;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.DrawNumbersPlacement;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.RhythmAlignment;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.SubZoneActionable;
import com.stillwindsoftware.keepyabeat.twlModel.RhythmsSearchModel;
import com.stillwindsoftware.keepyabeat.twlModel.RhythmsTableModel;

import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuAction;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Timer;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLFont;

/**
 * Shows the rhythms and a search box that allows tag selection too.
 * Popup menu actions for open/edit/delete/duplicate.
 * Popup tooltip shows detailed rhythm.
 * @author tomas stubbs
 */
public class RhythmsList extends StandardTableList implements LibraryListener, PickedTagReceiver, TargetGovernerSharer {

	private static final long MENU_ACTION_TOOLTIP_DELAY = 1000L;
	public static final int MINIMAL_SUB_BEATS_WIDTH = 5;

	// don't start tooltip timer within a second of the time a menu action happens
	private long menuActionedAt = -1L;	
	private Timer tooltipTimer;
	private RhythmTooltip rhythmTooltip;
	
	private RhythmsSearchModel rhythmsSearchModel = new RhythmsSearchModel();

	// all the rows in the list will share a single instance of a target governer, so it 
	// caches all the images etc which are loaded only once
	// set by the list in applyTheme() once it has the images to create it, and called from the 
	// rhythmimagecellcreator when it needs to create an image
	private DraughtTargetGoverner targetGoverner, tooltipTargetGoverner;

	// convenience fields
	private PersistentStatesManager persistenceManager;
    private TwlGuiManager guiManager;
    private int screenHeight;
    
    // owner, for opening rhythms
    private final PlayerModule playerModule;
    
	public RhythmsList(PlayerModule playerModule) {
		this.playerModule = playerModule;
		persistenceManager = resourceManager.getPersistentStatesManager();
	    guiManager = (TwlGuiManager) resourceManager.getGuiManager();
	    screenHeight = guiManager.getScreenHeight();
	}
	
	public RhythmsSearchModel getRhythmsSearchModel() {
		return rhythmsSearchModel;
	}

	@Override
	protected void doLayout() {
		// make sure dimensions are computed
		if (getWidth() != playerModule.getRhythmsListCalculatedWidth()) {
			resetDimensionsComputed();
		}
		super.doLayout();
	}

	@Override
	protected void afterAddToGUI(GUI gui) {
		super.afterAddToGUI(gui);
		if (rhythmsSearchModel != null && table != null) {
			rhythmsSearchModel.addListener(table);
		}
		
		// listen to changes in beat types too, in case of a colour change
		resourceManager.getListenerSupport().addListener(resourceManager.getLibrary().getBeatTypes(), this);
		resourceManager.getListenerSupport().addListener(resourceManager.getLibrary().getTags(), this);
	}

	@Override
	protected void beforeRemoveFromGUI(GUI gui) {
		super.beforeRemoveFromGUI(gui);
		rhythmsSearchModel.removeListener(table);
		resourceManager.getListenerSupport().removeListener(this);
	}
	
	/**
	 * Override superclass method to create the search box
	 */
    @Override
	protected void initTopBox() {
    	topBox = new NameAndTagsBox();
    	((NameAndTagsBox)topBox).init(true, this);
    	final EditField searchText = ((NameAndTagsBox)topBox).getNameField();
    	searchText.addCallback(new EditField.Callback() {			
				@Override
				public void callback(int key) {
					rhythmsSearchModel.setSearchRhythmText(searchText.getText());
					topBox.invalidateLayout();
				}
			});
    	
    	// initialise the data from state
    	String savedSearchText = persistenceManager.getRhythmSearchText();
    	String[] savedTagKeys = persistenceManager.getRhythmSearchTagKeys();
    	
    	if (savedSearchText != null && !savedSearchText.isEmpty()) {
    		searchText.setText(savedSearchText);
    	}
    	
    	if (savedTagKeys != null) {
        	Tags tags = resourceManager.getLibrary().getTags();

        	for (int i = 0; i < savedTagKeys.length; i++) {
    			Tag tag = (Tag) tags.lookup(savedTagKeys[i]);
    			if (tag != null) {
    				// has to still be in the library
    				tagPicked(tag);
    			}
    		}
    	}
    }

	/**
     * When the tags pick list is invoked and a tag is selected, it will invoke this method
     * The NameAndTagsBox class needs this method to add to its list and invalidate it too, 
     * so the pairing of a calling class completes that one, it can't function correctly without
     * this interaction.
     * @param tag
     */
	@Override
	public void tagPicked(final Tag tag) {
		rhythmsSearchModel.addSearchTag(tag);
		final TagListButton tagButton = new TagListButton(tag);
		tagButton.setTheme("selectedTagBtn");
		final TagList tagsList = ((NameAndTagsBox)topBox).getTagsList();
		tagsList.addTagButton(tagButton);
		topBox.invalidateLayout();
		tagButton.addCallback(new Runnable() {
			@Override
			public void run() {
				rhythmsSearchModel.removeSearchTag(tag);
				tagsList.removeTagButton(tagButton);
				topBox.invalidateLayout();
			}			
		});
	}

	/**
	 * Called by the NameAndTagsBox class to pop up a list
	 */
	@Override 
	public TagsPickList getTagsPickList() {
		return new TagsPickList(rhythmsSearchModel, this);
	}
	
	@Override
	protected String getLayoutThemeName() {
		return "rhythmsList";
	}
	
	/**
	 * Delete selected rhythm
	 * @param rhythm
	 */
	private void deleteSelectedRhythm(Rhythm rhythm) {
		new RhythmCommandAdaptor.RhythmDeleteCommand(rhythm).execute();
	}
	
	/**
	 * Clone selected rhythm
	 * @param rhythm
	 */
	private void cloneSelectedRhythm(Rhythm rhythm) {
		new CloneRhythmCommand(rhythm).execute();
	}
	
	/**
	 * Open selected rhythm
	 * @param rhythm
	 */
	private void openSelectedRhythm(Rhythm rhythm) {
		playerModule.openRhythm(rhythm);
		if (persistenceManager.isHideListOnOpenRhythm()) {
			playerModule.getRhythmsListSlidingPanel().setShow(false);
		}
	}

	private void setNameSelectedRhythm(Rhythm rhythm) {
		new RhythmSetNameConfirmation(rhythm, this).saveChangesIfConfirmed();
	}
	
	/**
	 * Edit selected rhythm
	 * @param rhythm
	 */
	private void editSelectedRhythm(Rhythm rhythm) {
		playerModule.invokeRhythmEditor(rhythm);
	}

	/**
	 * Needs the images to create the target governer that will cache images and control drawing
	 * to the canvases that show the rhythms
	 */
	@Override
	protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
		TwlFont fullBeatsFontSmall = new TwlFont(themeInfo.getParameterValue("fullBeatsFontSmall", false, LWJGLFont.class));

		// make a sharing target governer
		targetGoverner = new DraughtTargetGoverner(resourceManager, true, MINIMAL_SUB_BEATS_WIDTH, SubZoneActionable.NEVER);

		// additional settings
		targetGoverner.setRhythmAlignment(RhythmAlignment.LEFT);
		targetGoverner.setMaxBeatsAtNormalWidth(6);
		targetGoverner.setVerticalRhythmMargins(1.0f, 1.0f);

		// and a special one for tooltips (can't share because will likely be using different
		// image buckets
		tooltipTargetGoverner = new DraughtTargetGoverner(resourceManager, false, SubZoneActionable.NEVER);

		// additional settings
		tooltipTargetGoverner.setRhythmAlignment(RhythmAlignment.MIDDLE);
		tooltipTargetGoverner.setMaxBeatsAtNormalWidth(6);
		
		// where to draw numbers for the beats and what font to use (alternatives if the first doesn't fit)
		tooltipTargetGoverner.setDrawNumbers(DrawNumbersPlacement.INSIDE_TOP, 12.0f, 1.0f, fullBeatsFontSmall);
		
	}

	@Override
	public DraughtTargetGoverner getTargetGoverner() {
		return targetGoverner;
	}

	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(RhythmImageValue.class, new RhythmImage.RhythmImageCellWidgetCreator(tableModel, this));
		super.installTableCellRenderers();
	}

	private void stopTooltipAndTimer() {
		if (tooltipTimer != null) {
			stopTooltip();
			tooltipTimer.stop();
			tooltipTimer = null;
		}
	}

    /**
     * Starts a timer for the tooltip, restarts if already running. 
     * Start is inhibited within a delay of the last menu action.
     */
    private void startTimer(final Rhythm rhythm) {
    	if (System.currentTimeMillis() - menuActionedAt > MENU_ACTION_TOOLTIP_DELAY) { 
			if (tooltipTimer == null) {
				tooltipTimer = new Timer(getGUI());
				tooltipTimer.setDelay(getGUI().getTooltipDelay());
				tooltipTimer.setCallback(new Runnable() {
					@Override
					public void run() {
						rhythmTooltip = new RhythmTooltip(rhythm, tooltipTargetGoverner, true);
						KeepYaBeat.root.add(rhythmTooltip);
					}		
				});
			}
			else {
				tooltipTimer.stop();
			}
			
			tooltipTimer.start();
    	}
    }
    
    private void stopTooltip() {
		if (rhythmTooltip != null) {
			if (KeepYaBeat.root.getChildIndex(rhythmTooltip) != -1) {
				KeepYaBeat.root.removeChild(rhythmTooltip);
			}
		}		
    }

	/**
	 * The table is specialised to listen for mouse moves to enable tooltip to show.
	 */
	@Override
	protected void initScrollPaneToModel() {
        tableModel = new RhythmsTableModel(rhythmsSearchModel);

        table = new SimpleDataSizedTable(tableModel) {
        	@Override
        	protected boolean handleEvent(Event evt) {
        		if (evt.isMouseEvent()) {
        			
    				int tableMouseY = screenHeight - Mouse.getY() - table.getY();
    				int row = table.getRowFromPosition(tableMouseY + scrollPane.getScrollPositionY());

    				if (row == -1) {
    					stopTooltipAndTimer();
    				}
    				else {
	        			switch (evt.getType()) {
	        			case MOUSE_ENTERED :
	        			case MOUSE_MOVED :
	        				stopTooltipAndTimer();
	        				Rhythm rhythm = rhythmsSearchModel.get(row);
	        				if (rhythm != null) {
		        				startTimer(rhythm);
	        				}
	        				break;
	        			case MOUSE_EXITED : 
	    					stopTooltipAndTimer();
	    					break;
	        			default :
	        				break;
	        			}
    				}
        		}
        		// table also must handle mouse moves to get the double/right clicks
        		return super.handleEvent(evt); 
        	}

        };

        // the search table already listens to changes in rhythms, so get notification from it
		resourceManager.getListenerSupport().addListener(rhythmsSearchModel, this);
		// multi row selection
		initScrollPaneToModel(table, true, true, "rhythmsTable", "rhythmsScrollpane");
		
	}

	/**
	 * Specialised callback for this list, because of the double-click and the tooltip behaviours
	 * can't just use the superclass version which would only call the generic showPopupMenu()
	 */
	@Override
	protected Callback getTableCallback() {
		return new Callback() {
			@Override
			public void mouseDoubleClicked(int row, int column) {
				stopTooltipAndTimer();
				Rhythm rhythm = rhythmsSearchModel.get(row);
				if (rhythm != null) {
					openSelectedRhythm(rhythm);
				}
			}
			public void mouseRightClick(int row, int column, Event evt) {
				stopTooltipAndTimer();
				Rhythm rhythm = rhythmsSearchModel.get(row);
				if (rhythm != null) {
					selectionModel.setSelection(row, row);
					showPopupMenu(rhythm);
				}
			}
		};
	}

	/**
	 * Bit different to the superclass showPopupMenu() because the callback is specialised, see initScrollPaneToModel()
	 * @param rhythm
	 */
	private void showPopupMenu(final Rhythm rhythm) {
		Menu menu = new Menu();
		menu.setTheme("menumanager");

		menu.addListener(new Menu.Listener() {
				@Override
				public void menuOpening(Menu menu) {}			
				@Override
				public void menuOpened(Menu menu) {}
				@Override
				public void menuClosed(Menu menu) {
					// inhibit tooltip too soon
					menuActionedAt = System.currentTimeMillis();
				}
			});
		
		menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_OPEN_RHYTHM), new Runnable() {
			@Override
			public void run() {
				openSelectedRhythm(rhythm);
			}
		}));
		
		menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_CHANGE_RHYTHM_NAME_TAGS), new Runnable() {
			@Override
			public void run() {
				setNameSelectedRhythm(rhythm);
			}
		}));
		
		menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_TOOLTIP_EDIT_RHYTHM), new Runnable() {
			@Override
			public void run() {
				editSelectedRhythm(rhythm);
			}
		}));
		
		menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_DELETE_RHYTHM), new Runnable() {
			@Override
			public void run() {
				deleteSelectedRhythm(rhythm);
			}
		}));
		
		menu.add(new MenuAction(resourceManager.getLocalisedString(TwlLocalisationKeys.MENU_CLONE_RHYTHM), new Runnable() {
			@Override
			public void run() {
				cloneSelectedRhythm(rhythm);
			}
		}));

		doOpenPopupMenu(menu);
	}

	@Override
	public void itemChanged() {
		if (targetGoverner != null) {
			targetGoverner.initSharedInstance(MINIMAL_SUB_BEATS_WIDTH);
		}
		table.invalidateLayout();
		invalidateLayout();
	}

    /**
     * A tooltip that shows a larger view of the rhythm
     */


}

    
    



