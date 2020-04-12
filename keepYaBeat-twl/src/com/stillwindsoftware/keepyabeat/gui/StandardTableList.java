package com.stillwindsoftware.keepyabeat.gui;

import org.lwjgl.input.Mouse;

import com.stillwindsoftware.keepyabeat.control.ModuleManager;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.Callback;
import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.LibraryXmlImpl;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.twlModel.DecoratedText;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Menu;
import de.matthiasmann.twl.MenuManager;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.Fixed;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.TableSelectionModel;
import de.matthiasmann.twl.model.TableSingleSelectionModel;

/**
 * An abstract superclass for lists that have standardised table layout (such as beat types list and sounds list)
 * They have the following features in common:
 * Tabular layout of data inside a scrolling area
 * A set of buttons at the bottom (create/update/delete) - (provided the library can be written to)
 * An extra button on the left that is dependent on the module
 * A sliding panel as a parent
 * @author tomas stubbs
 *
 */
public abstract class StandardTableList extends DialogLayout {

	protected DialogLayout topBox;
    protected ScrollPane scrollPane;
    protected SimpleDataSizedTable table;
    protected Button addBtn;
    protected Label topBoxLabel;
    protected TableSelectionModel selectionModel;
    protected DataSizingTableModel tableModel;
    private final boolean libraryIsReadOnly;
    private int maxWidth = Library.INT_NOT_CHANGED;
    private int maxHeight = Library.INT_NOT_CHANGED;
	
	protected MutableRectangle dimensions;
	// flag set when doLayout has set the dimensions so only does it once, sub-classes may override 
	// and will have to reset when the data changes
	private boolean dimensionsComputed = false;
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	protected StandardTableList() {
		libraryIsReadOnly = ((LibraryXmlImpl)resourceManager.getLibrary()).isReadOnlyMode();
	}

	/**
	 * Initialize panels and parent sliding panel etc
	 * This method should be called immediately on creation
	 * It isn't in a constructor because the ModuleManager way is to call startNew(), so 
	 * keep the sub class module version working as is (for now)
	 */
	public void init() {
		initScrollPaneToModel();
		initLayout();
	}
	
	public void setMaxWidth(int maxWidth) {
		this.maxWidth = maxWidth;
	}

	public void setMaxHeight(int maxHeight) {
		this.maxHeight = maxHeight;
	}

	/**
	 * Used particularly by SlidingAnimatedPanel when using startingUpHidden and needs to place this dialog off-screen
	 * to slide in. It needs an accurate size to do that so tests this method first.
	 * @return
	 */
	public boolean isLayoutInitComplete() {
		return table.isDataWidthsInitialised() && table.getPreferredInnerWidth() != 0 && table.getInnerWidth() > 0;
	}
	
	/**
	 * Allow access for AbstractRhythmPlayer to check the settings table's cells are all laid out correctly once
	 * it starts to slide in
	 * @return
	 */
	public SimpleDataSizedTable getTable() {
		return table;
	}

	/**
	 * The width this object would like to have to show its table exactly with no scrollbar
	 * Only return non-zero once the table is ready to declare its width
	 * @return
	 */
	public int getUnfetteredWidth() {
		int tableWidth = table.getPreferredInnerWidth();
		if (tableWidth == 0) {
			return tableWidth;
		}
		
		tableWidth += getBorderHorizontal();
		return Math.max(super.getMinWidth(), tableWidth);
	}

	/**
	 * The height this object would like to have to show its table exactly with no scrollbar
	 * @return
	 */
	public int getUnfetteredHeight() {
		int heightMaxedTable = 0;
		if (table.getModel().getNumRows() > 0)
			heightMaxedTable += table.getRowEndPosition(table.getModel().getNumRows()-1); // the point where the last row ends
		heightMaxedTable += getBorderVertical();
		if (topBox != null) {
			heightMaxedTable += topBox.getHeight();
			heightMaxedTable += getDefaultGap().getY()*2; // gap between the 2 groups (*2 not sure why, but fixes bug
														  // where vertical scrollbar appears when change to basic theme)
		}
		return heightMaxedTable;
	}
	
	boolean computeDimensions() {
		int unfetteredWidth = getUnfetteredWidth();
		if (unfetteredWidth > 0) {
			dimensions = ModuleManager.getInstance().computeLayout(dimensions, unfetteredWidth, getUnfetteredHeight(), maxWidth, maxHeight);
			if ((int)dimensions.getW() != getWidth() || (int)dimensions.getH() != getHeight()) {
				setSize(Math.max((int)dimensions.getW(), 0), Math.max((int)dimensions.getH(), 0));
			}
			return true;
		}
		else {
			// not ready yet
			return false;
		}
	}
	
	/**
	 * Set to true when doLayout manages to set the dimensions, sub-classes can call this method to ensure a re-calc of dimensions happens
	 * (usually by overriding doLayout())
	 */
	protected void resetDimensionsComputed() {
		this.dimensionsComputed = false;
	}

	@Override
	protected void doLayout() {
		if (!dimensionsComputed || dimensions == null) {
			dimensionsComputed = computeDimensions();
		}
		super.doLayout();
		
		// if not ready yet to complete layout make sure it will try again
		if (!dimensionsComputed || !isLayoutInitComplete()) {
			resourceManager.log(LOG_TYPE.info, this, 
					String.format("StandardTableList.doLayout: invalidating layout not ready (%s)",
							getClass().getSimpleName()));
			invalidateLayout();
		}
	}

	/**
	 * Layout should call computeDimensions first to get the width/height sorted
	 */
	@Override
	public int getMinWidth() {
		if (dimensions == null)
			return 0;
		else
			return (int)dimensions.getW();
	}
	
	/**
	 * Layout should call computeDimensions first to get the width/height sorted
	 */
	@Override
	public int getMinHeight() {
		if (dimensions == null)
			return 0;
		else
			return (int)dimensions.getH();
	}

	@Override
	protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
	}

	/**
	 * Allow for an extra layout above the scrollpane
	 */
	protected void initTopBox() {}
	protected String getTopboxTitle() {
		return null;
	}
	
	/**
	 * Put the whole layout into whichever widget is passed, frame or popup
	 * @param attachTo
	 */
	protected void initLayout() {
        setTheme(getLayoutThemeName());

        // enclosing groups
        Group hGroup = createParallelGroup();
        Group vGroup = createSequentialGroup();

        // init widget to appear at the top, if there is one
        initTopBox();

        if (topBox != null) {        	
            
        	topBoxLabel = null;
        	String topBoxTitle = getTopboxTitle();
        	if (topBoxTitle != null) {
        		topBoxLabel = new Label(topBoxTitle);
        		topBoxLabel.setTheme("topBoxTitlesLabel");
        	}
        	
            // nothing can happen if readonly
            if (!libraryIsReadOnly) {
            	initAddButton();
                if (addBtn != null) {
                	addBtn.setTheme("panelNewBtn");
                }
            }

            Button helpBtn = makeHelpButton();
            
            if (helpBtn != null || addBtn != null || topBoxLabel != null) {
                // buttons group contains optional buttons
                Group hBtnsGroup = topBox.createSequentialGroup();
    	        Group vBtnsGroup = topBox.createParallelGroup(); 

    	        if (topBoxLabel != null) {
                    hBtnsGroup.addGap(DialogLayout.LARGE_GAP);
    	        	hBtnsGroup.addWidget(topBoxLabel);
    	        	vBtnsGroup.addWidget(topBoxLabel);
                    hBtnsGroup.addGap(DialogLayout.LARGE_GAP);
    	        }
                hBtnsGroup.addGap();
                if (helpBtn != null) {
                	hBtnsGroup.addWidget(helpBtn);
                	vBtnsGroup.addWidget(helpBtn);
                }
                if (addBtn != null) {
                	hBtnsGroup.addWidget(addBtn);
                	vBtnsGroup.addWidget(addBtn);
                }

                topBox.setHorizontalGroup(hBtnsGroup);
            	topBox.setVerticalGroup( // add a sequential group to create a gap that aligns bottom edge with top box
            							// note this will line up after border is added, so any bottom border will create 
            							// a gap between the bottom edge and the button bottom edges
            			topBox.createSequentialGroup().addGap().addGroup(vBtnsGroup));
            }

    		hGroup.addWidget(topBox);
            vGroup.addWidget(topBox);
        }

        // add the scrollpane
		hGroup.addGroup(createSequentialGroup(scrollPane));
        vGroup.addGroup(createParallelGroup(scrollPane));

        setHorizontalGroup(hGroup);
        setVerticalGroup(vGroup);
	}

	/**
	 * Override to install required renderers
	 */
	protected void installTableCellRenderers() {
        // install Matthias' renderer that can recognize and display errors/warnings
        table.registerCellRenderer(DecoratedText.class, new DecoratedTextRenderer());
	}
	
	/**
	 * Overloaded to allow only row selections
	 */
	protected void initScrollPaneToModel(SimpleDataSizedTable table, String tableTheme, String scrollPaneTheme) {
		initScrollPaneToModel(table, true, false, tableTheme, scrollPaneTheme);
	}
	
	/**
	 * Overloaded to allow multi selections
	 * @param table
	 * @param tableTheme
	 * @param scrollPaneTheme
	 */
	protected void initScrollPaneToModel(SimpleDataSizedTable table, boolean allowSelection, boolean isMultiSelect, String tableTheme, String scrollPaneTheme) {

		table.setTheme(tableTheme);

		if (allowSelection) {
	    	TwlTableRowSelectionManager selectionManager = (isMultiSelect ? new TwlTableRowSelectionManager()
	    														: new TwlTableRowSelectionManager(new TableSingleSelectionModel()));
	
	    	selectionModel = selectionManager.getSelectionModel();
	        table.setSelectionManager(selectionManager);
		}
		
    	// call method to invoke popup menu
    	table.addCallback(getTableCallback());
        
        installTableCellRenderers();
        
        scrollPane = makeScrollPane(table, scrollPaneTheme);
	}
	
	/**
	 * Allow sub-classes to change
	 * @param table
	 * @param scrollPaneTheme
	 * @return
	 */
	protected ScrollPane makeScrollPane(SimpleDataSizedTable table, String scrollPaneTheme) {
        ScrollPane scrollPane = new ScrollPane(table);
        scrollPane.setTheme(scrollPaneTheme);
        scrollPane.setFixed(Fixed.HORIZONTAL);
        return scrollPane;
	}
	
	protected Callback getTableCallback() {
		return new Callback() {
			@Override
			public void mouseDoubleClicked(int row, int column) {}

			public void mouseRightClick(int row, int column, Event evt) {
				showPopupMenu(row);
			}
		};
	}
	
	/**
	 * Subclasses can override to get the required behaviour, called from the standard callback on the table 
	 * see initScrollPaneToModel(), and also from widgets that need to trap right-click themselves through
	 * the static method handleMouseClickOnRow()
	 * @param row
	 */
	protected void showPopupMenu(int row) {
		if (selectionModel != null) {
			selectionModel.setSelection(row, row);
		}
	}

	/**
	 * Called to actually do the menu opening, to make it close to the mouse without going over the 
	 * edge of the screen.
	 * @param menu
	 */
	protected void doOpenPopupMenu(Menu menu) {
		int screenWidth = resourceManager.getGuiManager().getScreenWidth();
		int screenHeight = resourceManager.getGuiManager().getScreenHeight();
		int mouseX = Mouse.getX();
		int mouseY = screenHeight - Mouse.getY();
		MenuManager menuManager = menu.openPopupMenu(this, mouseX, mouseY);
		Widget popupMenu = menuManager.getChild(0);
		if (popupMenu != null) {
			if (popupMenu.getX()+popupMenu.getWidth() > screenWidth
					|| popupMenu.getY()+popupMenu.getHeight() > screenHeight) {
				popupMenu.setPosition(
						Math.min(popupMenu.getX(), screenWidth - popupMenu.getWidth()),
						Math.min(popupMenu.getY(), screenHeight - popupMenu.getHeight())
						);
			}
		}
	}
	
	/**
	 * Called the static method handleMouseClickOnRow() which is called from widgets that handle their own mouse events
	 * @param row
	 */
	protected void rowSelected(int row) {
		if (selectionModel != null) {
			selectionModel.setSelection(row, row);
		}
	}
	
	/**
	 * Standard code for any custom widget that may be in a table list, if it is 
	 * show the popup menu
	 * for example see SoundCombo.handleRightClick() 
	 */ 
	public static void handleRightClickOnRow(Widget widget, int row) {
		StandardTableList parentList = getStandardTableListParent(widget);
		if (parentList != null) {
			parentList.showPopupMenu(row);
		}				
	}
	
	/**
	 * Standard code for any custom widget that may be in a table list, if it is and there is a selection 
	 * model set, then button down will cause the row to be selected. Also, if it's a right-click, the popup
	 * menu will be invoked by calling handleRightClickOnRow()
	 * for example see ColourButton.handleEvent()
	 * @param widget
	 * @param evt
	 * @param row
	 */
	public static void handleMouseClickOnRow(Widget widget, Event evt, int row) {
    	// all button down events need to set the selection on the list if there is one
    	if (evt.getType() == Event.Type.MOUSE_BTNDOWN) {
    		if (evt.getMouseButton() == Event.MOUSE_RBUTTON
    				) {
    			handleRightClickOnRow(widget, row);
    		}
    		else {
    			StandardTableList parentList = getStandardTableListParent(widget);
    			if (parentList != null) {
					parentList.rowSelected(row);
				}
    		}
    	}
	}

	/**
	 * Standard code for any custom widget that may be in a table list, if it is and there an error reported on the row. 
	 * returns true for example see ColourButton.setData()
	 * @param widget
	 * @param row
	 * @return
	 */
	public static boolean rowContainsError(Widget widget, int row) {
		StandardTableList parentList = getStandardTableListParent(widget);
		if (parentList != null) {
			return parentList.rowContainsError(row);
		}
		else {
			return false;
		}
	}

	/**
	 * Utility method to return the standard list parent of a widget, if it has one
	 * @param widget
	 * @return
	 */
	public static StandardTableList getStandardTableListParent(Widget widget) {
		// test for parent being a table list, walk up the tree to find it
		Widget parent = widget.getParent();
				
		while (parent != null && !parent.equals(widget.getGUI())) {
			if (parent instanceof StandardTableList) {
				return (StandardTableList)parent;
			}
			parent = parent.getParent();
		}
		
		return null;
	}

	public boolean rowContainsError(int row) {
		return false;
	}

	/**
	 * Sub-classes can override to access the text to show in a tooltip, for custom widgets
	 * returns true for example see ColourButton.layout()
	 * @param row
	 * @return
	 */
	public String getTooltipText(int row) {
		return null;
	}
	
	protected Button addButton(String theme, String tooltipLocalKey, Runnable cb) {
        Button btn = new Button();
        btn.setTheme(theme);
        btn.addCallback(cb);
        
        if (tooltipLocalKey != null) {
        	btn.setTooltipContent(((TwlResourceManager)resourceManager).getLocalisedString(tooltipLocalKey));
        }
        
//        add(btn);
        return btn;
    }

	protected abstract String getLayoutThemeName();
	protected void initAddButton() {};
	protected Button makeHelpButton() { return null; };

	/**
	 * The real work is in initScrollPaneToModel(AbstractTableModel tableModel, Runnable selectionChangeRunnable, String tableTheme, String scrollPaneTheme)
	 * set the parameters up and call it from the implementation of this method
	 */
	protected abstract void initScrollPaneToModel();

}


