package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.KeepYaBeat;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener.LibraryListListener;
import com.stillwindsoftware.keepyabeat.platform.CoreLocalisation;
import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.AnimationState;
import de.matthiasmann.twl.Border;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.AutoScrollable;
import de.matthiasmann.twl.ScrollPane.CustomPageSize;
import de.matthiasmann.twl.ScrollPane.Scrollable;
import de.matthiasmann.twl.Table;
import de.matthiasmann.twl.TableBase;
import de.matthiasmann.twl.TableBase.CellRenderer;
import de.matthiasmann.twl.TableBase.CellWidgetCreator;
import de.matthiasmann.twl.TableBase.KeyboardSearchHandler;
import de.matthiasmann.twl.TableBase.StringCellRenderer;
import de.matthiasmann.twl.TableSelectionManager;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.TreeTable;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.AbstractTableModel;
import de.matthiasmann.twl.model.TableModel;
import de.matthiasmann.twl.renderer.Font;
import de.matthiasmann.twl.renderer.Image;
import de.matthiasmann.twl.renderer.Renderer;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLFont;
import de.matthiasmann.twl.utils.CallbackSupport;
import de.matthiasmann.twl.utils.SizeSequence;
import de.matthiasmann.twl.utils.SparseGrid;
import de.matthiasmann.twl.utils.SparseGrid.Entry;
import de.matthiasmann.twl.utils.TypeMapping;

/**
 * Base class for Table and TreeTable.
 *
 * It does not have a {@link TableSelectionManager} by default. To make the
 * table entries selectable you need to install a selection manager:
 * {@link #setSelectionManager(de.matthiasmann.twl.TableSelectionManager) } or
 * {@link #setDefaultSelectionManager() }
 *
 * @see Table
 * @see TreeTable
 * @author Matthias Mann
 */
public class SimpleDataSizedTable extends Widget 
		implements Scrollable, AutoScrollable, CustomPageSize, TableModel.ChangeListener, LibraryListListener {

    public interface Callback {
        public void mouseDoubleClicked(int row, int column);
        public void mouseRightClick(int row, int column, Event evt);
    }

    protected final StringCellRenderer stringCellRenderer;
    protected final RemoveCellWidgets removeCellWidgetsFunction;
    protected final InsertCellWidgets insertCellWidgetsFunction;
    protected final CellWidgetContainer cellWidgetContainer;
    
    protected final TypeMapping<CellRenderer> cellRenderers;
    protected final SparseGrid widgetGrid;
    protected final ColumnSizeSequence columnModel;
    protected boolean hasCellWidgetCreators;
    protected CellRenderer[] columnDefaultCellRenderer;
    protected TwlTableRowSelectionManager selectionManager;
    protected KeyboardSearchHandler keyboardSearchHandler;
    protected Callback[] callbacks;

    protected Image imageRowBackground;
    protected Image imageRowOverlay;
    protected ThemeInfo tableBaseThemeInfo;

    protected int numRows;
    protected int numColumns;
    protected int rowHeight = 32;
    protected boolean updateAllCellWidgets;
    protected boolean updateAllColumnWidth;

    protected int scrollPosX;
    protected int scrollPosY;

    protected int firstVisibleRow;
    protected int firstVisibleColumn;
    protected int lastVisibleRow;
    protected int lastVisibleColumn;
    protected boolean firstRowPartialVisible;
    protected boolean lastRowPartialVisible;

    protected static final int LAST_MOUSE_Y_OUTSIDE = Integer.MIN_VALUE;
    
    protected int lastMouseY = LAST_MOUSE_Y_OUTSIDE;
    protected int lastMouseRow = -1;
    protected int lastMouseColumn = -1;

	private DataSizingTableModel model;
	private ColumnWidth[] columnDataWidths;
	private boolean dataWidthsInitialised = false;

    protected SimpleDataSizedTable(DataSizingTableModel model) {
        this.cellRenderers = new TypeMapping<CellRenderer>();
        this.stringCellRenderer = new StringCellRenderer();
        this.widgetGrid = new SparseGrid(32);
        this.removeCellWidgetsFunction = new RemoveCellWidgets();
        this.insertCellWidgetsFunction = new InsertCellWidgets();
        this.columnModel = new ColumnSizeSequence();
        this.columnDefaultCellRenderer = new CellRenderer[8];
        this.cellWidgetContainer = new CellWidgetContainer();

        super.insertChild(cellWidgetContainer, 0);
        setCanAcceptKeyboardFocus(true);
        
        setModel(model);
    }

    public TableModel getModel() {
        return model;
    }

    public void setModel(DataSizingTableModel model) {
        if(this.model != null) {
            this.model.removeChangeListener(this);
        }
        this.model = model;
        if(this.model != null) {
            numRows = model.getNumRows();
            numColumns = model.getNumColumns();
            this.model.addChangeListener(this);
        } else {
            numRows = 0;
            numColumns = 0;
        }
        modelAllChanged();
    }
    
    /**
     * Set the data widths of columns that need it, only set once until the data changes
     * see modelAllChanged()
     */
    public void setColumnTextWidths() {
        if (!dataWidthsInitialised) {
	    	for (int i = 0; i < columnDataWidths.length; i++) {
	    		ColumnWidth columnWidth = columnDataWidths[i];
	    		if (columnWidth == null) {
	    			// this will read theme info to get the mpm and possibly a font
	    			columnWidth = new ColumnWidth(i);
	    			columnDataWidths[i] = columnWidth;
	    		}
	    		if (model.isColumnDataSized(i)) {
	    			Font useFont = columnWidth.getFont();
	    			if (useFont == null) {
	    				useFont = KeepYaBeat.getTheme().getDefaultFont();
	    			}
	    			int textWidth = model.getColumnTextWidth(i, useFont);
	    			columnWidth.setTextWidth(textWidth);
	//    			System.out.println("SimpleDataSizedTable.setColumnTextWidths: column="+i+" textwidth="+textWidth);
	    		}
	    	}

	    	dataWidthsInitialised = true;
        }
    }

    public boolean isDataWidthsInitialised() {
		return dataWidthsInitialised;
	}

	/**
     * Returns the font used for the column (if for any reason one can't be found it uses the
     * default font)
     * @param column
     * @return
     */
    public Font getColumnFont(int column) {
    	Font retFont = null;
    	
    	if (columnDataWidths != null && column < columnDataWidths.length && column >= 0) {
    		ColumnWidth columnWidth = columnDataWidths[column];
    		if (columnWidth != null && model.isColumnDataSized(column)) {
    			retFont = columnWidth.getFont();
    		}
    	}
    	
		if (retFont == null) {
			retFont = KeepYaBeat.getTheme().getDefaultFont();
		}

		return retFont;
    }
    
    /**
     * Called from ColumnSizeSequence (see below)
     * @param index
     * @return
     */
    protected int computePreferredColumnWidth(int index) {
    	if (columnDataWidths == null || columnDataWidths.length == 0 || columnDataWidths[index] == null) {
    		return 0;
    	}
    	else {
    		return columnDataWidths[index].getColumnWidth();
    	}
	}


    protected void modelAllChanged() {

    	columnDataWidths = new ColumnWidth[numColumns];
		dataWidthsInitialised = false;

		if(selectionManager != null) {
            selectionManager.modelChanged();
        }
        
        updateAll();
    }

    @Override
    protected void layout() {
		final int innerWidth = getInnerWidth();
		final int innerHeight = Math.max(0, getInnerHeight());

		cellWidgetContainer.setPosition(getInnerX(), getInnerY());
		cellWidgetContainer.setSize(innerWidth, innerHeight);
		
		// data widths for the columns are set in updateAllColumnWidth()
		if(updateAllColumnWidth) {
		    updateAllColumnWidth();
		}

		if(updateAllCellWidgets) {
		    updateAllCellWidgets();
		}

		final int scrollEndX = scrollPosX + innerWidth;
		final int scrollEndY = scrollPosY + innerHeight;

		int startRow = Math.min(numRows-1, Math.max(0, getRowFromPosition(scrollPosY)));
		int startColumn = Math.min(numColumns-1, Math.max(0, getColumnFromPosition(scrollPosX)));
		int endRow = Math.min(numRows-1, Math.max(startRow, getRowFromPosition(scrollEndY)));
		int endColumn = Math.min(numColumns-1, Math.max(startColumn, getColumnFromPosition(scrollEndX)));

		if(numRows > 0) {
		    firstRowPartialVisible = getRowStartPosition(startRow) < scrollPosY;
		    lastRowPartialVisible = getRowEndPosition(endRow) > scrollEndY;
		} else {
		    firstRowPartialVisible = false;
		    lastRowPartialVisible = false;
		}

		if(!widgetGrid.isEmpty()) {
		    if(startRow > firstVisibleRow) {
		        widgetGrid.iterate(firstVisibleRow, 0, startRow-1, numColumns, removeCellWidgetsFunction);
		    }
		    if(endRow < lastVisibleRow) {
		        widgetGrid.iterate(endRow+1, 0, lastVisibleRow, numColumns, removeCellWidgetsFunction);
		    }

		    widgetGrid.iterate(startRow, 0, endRow, numColumns, insertCellWidgetsFunction);
		}

		firstVisibleRow = startRow;
		firstVisibleColumn = startColumn;
		lastVisibleRow = endRow;
		lastVisibleColumn = endColumn;
    }

    protected Object getCellData(int row, int column) {
        return model.getCell(row, column);
    }

    protected Object getTooltipContentFromRow(int row, int column) {
        return model.getTooltipContent(row, column);
    }
    
    public void rowsInserted(int idx, int count) {
//            numRows = model.getNumRows();
//            modelRowsInserted(idx, count);
    }
    public void rowsDeleted(int idx, int count) {
//            checkRowRange(idx, count);
//            numRows = model.getNumRows();
//            modelRowsDeleted(idx, count);
    }
    public void rowsChanged(int idx, int count) {
//            modelRowsChanged(idx, count);
    }
    public void columnDeleted(int idx, int count) {
//            checkColumnRange(idx, count);
//            numColumns = model.getNumColumns();
//            modelColumnsDeleted(count, count);
    }
    public void columnInserted(int idx, int count) {
//            numColumns = model.getNumColumns();
//            modelColumnsInserted(count, count);
    }
    public void columnHeaderChanged(int column) {
//            modelColumnHeaderChanged(column);
    }
    public void cellChanged(int row, int column) {
//            modelCellChanged(row, column);
    }
    public void allChanged() {
        numRows = model.getNumRows();
        numColumns = model.getNumColumns();
        modelAllChanged();
    }

	@Override
	public void itemChanged() {
		allChanged();
	}    

    public TableSelectionManager getSelectionManager() {
        return selectionManager;
    }

    public void setSelectionManager(TwlTableRowSelectionManager selectionManager) {
        if(this.selectionManager != selectionManager) {
            if(this.selectionManager != null) {
                this.selectionManager.setAssociatedTable((SimpleDataSizedTable)null);
            }
            this.selectionManager = selectionManager;
            if(this.selectionManager != null) {
                this.selectionManager.setAssociatedTable(this);
            }
        }
    }

//    /**
//     * Installs a multi row selection manager.
//     *
//     * @see TableRowSelectionManager
//     * @see DefaultTableSelectionModel
//     */
//    public void setDefaultSelectionManager() {
//        setSelectionManager(new TwlTableRowSelectionManager());
//    }
//
    public KeyboardSearchHandler getKeyboardSearchHandler() {
        return keyboardSearchHandler;
    }

    public void setKeyboardSearchHandler(KeyboardSearchHandler keyboardSearchHandler) {
        this.keyboardSearchHandler = keyboardSearchHandler;
    }

    public void addCallback(Callback callback) {
        callbacks = CallbackSupport.addCallbackToList(callbacks, callback, Callback.class);
    }

    public void removeCallback(Callback callback) {
        callbacks = CallbackSupport.removeCallbackFromList(callbacks, callback);
    }
    
    public int getNumRows() {
        return numRows;
    }

    public int getNumColumns() {
        return numColumns;
    }

    public int getRowFromPosition(int y) {
        if(y >= 0) {
            return Math.min(numRows-1, y / rowHeight);
        }
        return -1;
    }

    public int getRowStartPosition(int row) {
        checkRowIndex(row);
        return row * rowHeight;
    }

    public int getRowHeight(int row) {
        checkRowIndex(row);
        return rowHeight;
    }

    public int getRowEndPosition(int row) {
        checkRowIndex(row);
        return (row+1) * rowHeight;
    }

    public int getColumnFromPosition(int x) {
        if(x >= 0) {
            int column = columnModel.getIndex(x);
            return column;
        }
        return -1;
    }

    public int getColumnStartPosition(int column) {
        checkColumnIndex(column);
        return columnModel.getPosition(column);
    }

    public int getColumnWidth(int column) {
        checkColumnIndex(column);
        return columnModel.getSize(column);
    }

    public int getColumnEndPosition(int column) {
        checkColumnIndex(column);
        return columnModel.getPosition(column + 1);
    }

    public void setColumnWidth(int column, int width) {
        checkColumnIndex(column);
//TODO: setColumnWidth
//        columnHeaders[column].setColumnWidth(width);    // store passed width
        if(columnModel.update(column)) {
            invalidateLayout();
        }
    }

    public void scrollToRow(int row) {
        ScrollPane scrollPane = ScrollPane.getContainingScrollPane(this);
        if(scrollPane != null && numRows > 0) {
            scrollPane.validateLayout();
            int rowStart = getRowStartPosition(row);
            int rowEnd = getRowEndPosition(row);
            int height = rowEnd - rowStart;
            scrollPane.scrollToAreaY(rowStart, height, height/2);
        }
    }

    public int getNumVisibleRows() {
        int rows = lastVisibleRow - firstVisibleRow;
        if(!lastRowPartialVisible) {
            rows++;
        }
        return rows;
    }
    
    @Override
    public int getPreferredInnerWidth() {
        if(getInnerWidth() == 0) {
            return columnModel.computePreferredWidth();
        }
        if(updateAllColumnWidth) {
            updateAllColumnWidth();
        }
        return (numColumns > 0) ? getColumnEndPosition(numColumns-1) : 0;
    }

    @Override
    public int getPreferredInnerHeight() {
        return ((numRows > 0) ? getRowEndPosition(numRows-1) : 0);
    }

    public void registerCellRenderer(Class<?> dataClass, CellRenderer cellRenderer) {
        if(dataClass == null) {
            throw new NullPointerException("dataClass");
        }
        cellRenderers.put(dataClass, cellRenderer);

        if(cellRenderer instanceof CellWidgetCreator) {
            hasCellWidgetCreators = true;
        }

        // only call it when we already have a theme
        if(tableBaseThemeInfo != null) {
            applyCellRendererTheme(cellRenderer);
        }
    }

    public void setScrollPosition(int scrollPosX, int scrollPosY) {
        if(this.scrollPosX != scrollPosX || this.scrollPosY != scrollPosY) {
            this.scrollPosX = scrollPosX;
            this.scrollPosY = scrollPosY;
            invalidateLayoutLocally();
        }
    }

    public void adjustScrollPosition(int row) {
        checkRowIndex(row);
        ScrollPane scrollPane = ScrollPane.getContainingScrollPane(this);
        int numVisibleRows = getNumVisibleRows();
        if(numVisibleRows >= 1 && scrollPane != null) {
            if(row < firstVisibleRow || (row == firstVisibleRow && firstRowPartialVisible)) {
                int pos = getRowStartPosition(row);
                scrollPane.setScrollPositionY(pos);
            } else if(row > lastVisibleRow || (row == lastVisibleRow && lastRowPartialVisible)) {
                int innerHeight = Math.max(0, getInnerHeight());
                int pos = getRowEndPosition(row);
                pos = Math.max(0, pos - innerHeight);
                scrollPane.setScrollPositionY(pos);
            }
        }
    }

    public int getAutoScrollDirection(Event evt, int autoScrollArea) {
        int areaY = getInnerY();
        int areaHeight = getInnerHeight();
        int mouseY = evt.getMouseY();
        if(mouseY >= areaY && mouseY < (areaY + areaHeight)) {
            mouseY -= areaY;
            if((mouseY <= autoScrollArea) || (areaHeight - mouseY) <= autoScrollArea) {
                // do a 2nd check in case the auto scroll areas overlap
                if(mouseY < areaHeight/2) {
                    return -1;
                } else {
                    return +1;
                }
            }
        }
        return 0;
    }

    public int getPageSizeX(int availableWidth) {
        return availableWidth;
    }

    public int getPageSizeY(int availableHeight) {
        return availableHeight;
    }

    protected final void checkRowIndex(int row) {
        if(row < 0 || row >= numRows) {
            throw new IndexOutOfBoundsException("row");
        }
    }

    protected final void checkColumnIndex(int column) {
        if(column < 0 || column >= numColumns) {
            throw new IndexOutOfBoundsException("column");
        }
    }

    protected final void checkRowRange(int idx, int count) {
        if(idx < 0 || count < 0 || count > numRows || idx > (numRows - count)) {
            throw new IllegalArgumentException("row");
        }
    }

    protected final void checkColumnRange(int idx, int count) {
        if(idx < 0 || count < 0 || count > numColumns || idx > (numColumns - count)) {
            throw new IllegalArgumentException("column");
        }
    }
    
    @Override
    protected void applyTheme(ThemeInfo themeInfo) {
        super.applyTheme(themeInfo);
        applyThemeSimpleDataSizedTable(themeInfo);
        updateAll();
    }

    protected void applyThemeSimpleDataSizedTable(ThemeInfo themeInfo) {
        this.tableBaseThemeInfo = themeInfo;
        this.imageRowBackground = themeInfo.getImage("row.background");
        this.imageRowOverlay = themeInfo.getImage("row.overlay");
        this.rowHeight = themeInfo.getParameter("rowHeight", 32);
        
        for(CellRenderer cellRenderer : cellRenderers.getUniqueValues()) {
            applyCellRendererTheme(cellRenderer);
        }
        applyCellRendererTheme(stringCellRenderer);
        updateAllColumnWidth = true;
    }

    protected void applyCellRendererTheme(CellRenderer cellRenderer) {
        String childThemeName = cellRenderer.getTheme();
        assert !isAbsoluteTheme(childThemeName);
        ThemeInfo childTheme = tableBaseThemeInfo.getChildTheme(childThemeName);
        if(childTheme != null) {
            cellRenderer.applyTheme(childTheme);
        }
    }

    @Override
    public void removeAllChildren() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void childAdded(Widget child) {
        // ignore
    }

    @Override
    protected void childRemoved(Widget exChild) {
        // ignore
    }

    protected int getOffsetX() {
        return getInnerX() - scrollPosX;
    }

    protected int getOffsetY() {
        return getInnerY() - scrollPosY;
    }

    @Override
    protected void positionChanged() {
        super.positionChanged();
        if(keyboardSearchHandler != null) {
            keyboardSearchHandler.updateInfoWindowPosition();
        }
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
//        updateAllColumnWidth = true;

        if(keyboardSearchHandler != null) {
            keyboardSearchHandler.updateInfoWindowPosition();
        }
    }

    @Override
    protected Object getTooltipContentAt(int mouseX, int mouseY) {
        // use cached row/column
        if(lastMouseRow >= 0 && lastMouseRow < getNumRows() &&
                lastMouseColumn >= 0 && lastMouseColumn < getNumColumns()) {
            Object tooltip = getTooltipContentFromRow(lastMouseRow, lastMouseColumn);
            if(tooltip != null) {
                return tooltip;
            }
        }
        return super.getTooltipContentAt(mouseX, mouseY);
    }

    @Override
    protected void paintWidget(GUI gui) {
        if(firstVisibleRow < 0 || firstVisibleRow >= numRows) {
            return;
        }
        
        final int innerX = getInnerX();
        final int innerY = getInnerY();
        final int innerWidth = getInnerWidth();
        final int innerHeight = getInnerHeight();
        final int offsetX = getOffsetX();
        final int offsetY = getOffsetY();
        final Renderer renderer = gui.getRenderer();

        renderer.clipEnter(innerX, innerY, innerWidth, innerHeight);
        try {
 //           final AnimationState animState = getAnimationState();
            final int leadRow;
//            final int leadColumn;
            final boolean isCellSelection;

            if(selectionManager != null) {
                leadRow = selectionManager.getLeadRow();
//                leadColumn = selectionManager.getLeadColumn();
                isCellSelection = selectionManager.getSelectionGranularity() ==
                        TableSelectionManager.SelectionGranularity.CELLS;
            } else {
                leadRow = -1;
//                leadColumn = -1;
                isCellSelection = false;
            }

            if(imageRowBackground != null) {
                paintRowImage(imageRowBackground, leadRow);
            }


            int rowStartPos = getRowStartPosition(firstVisibleRow);
            for(int row=firstVisibleRow ; row<=lastVisibleRow ; row++) {
                final int rowEndPos = getRowEndPosition(row);
                final int curRowHeight = rowEndPos - rowStartPos;
                final int curY = offsetY + rowStartPos;
                final boolean isRowSelected = !isCellSelection && isRowSelected(row);
                
                int colStartPos = getColumnStartPosition(firstVisibleColumn);
                for(int col=firstVisibleColumn ; col<=lastVisibleColumn ;) {
                    int colEndPos = getColumnEndPosition(col);
                    final CellRenderer cellRenderer = getCellRenderer(row, col);
                    final boolean isCellSelected = isRowSelected || isCellSelected(row, col);

                    int curX = offsetX + colStartPos;
                    int colSpan = 1;

                    if(cellRenderer != null) {
                        colSpan = cellRenderer.getColumnSpan();
                        if(colSpan > 1) {
                            colEndPos = getColumnEndPosition(Math.max(numColumns-1, col+colSpan-1));
                        }

                        Widget cellRendererWidget = cellRenderer.getCellRenderWidget(
                                curX, curY, colEndPos - colStartPos, curRowHeight, isCellSelected);

                        if(cellRendererWidget != null) {
                            if(cellRendererWidget.getParent() != this) {
                                insertCellRenderer(cellRendererWidget);
                            }
                            paintChild(gui, cellRendererWidget);
                        }
                    }

                    col += Math.max(1, colSpan);
                    colStartPos = colEndPos;
                }

                rowStartPos = rowEndPos;
            }

            if(imageRowOverlay != null) {
                paintRowImage(imageRowOverlay, leadRow);
            }

        } finally {
            renderer.clipLeave();
        }
    }

    private void paintRowImage(Image img, int leadRow) {
        final AnimationState animState = getAnimationState();
        final int x = getOffsetX();
        final int width = columnModel.getEndPosition();
        final int offsetY = getOffsetY();
        
        int rowStartPos = getRowStartPosition(firstVisibleRow);
        for(int row=firstVisibleRow ; row<=lastVisibleRow ; row++) {
            final int rowEndPos = getRowEndPosition(row);
            final int curRowHeight = rowEndPos - rowStartPos;
            final int curY = offsetY + rowStartPos;

            animState.setAnimationState(TableBase.STATE_ROW_SELECTED, isRowSelected(row));
            animState.setAnimationState(TableBase.STATE_LEAD_ROW, row == leadRow);
            animState.setAnimationState(TableBase.STATE_ROW_ODD, (row & 1) == 1);
            img.draw(animState, x, curY, width, curRowHeight);

            rowStartPos = rowEndPos;
        }
    }

    protected void insertCellRenderer(Widget widget) {
        int posX = widget.getX();
        int posY = widget.getY();
        widget.setVisible(false);
        super.insertChild(widget, super.getNumChildren());
        widget.setPosition(posX, posY);
    }

    protected boolean isRowSelected(int row) {
        if(selectionManager != null) {
            return selectionManager.isRowSelected(row);
        }
        return false;
    }

    protected boolean isCellSelected(int row, int column) {
        if(selectionManager != null) {
            return selectionManager.isCellSelected(row, column);
        }
        return false;
    }

    /**
     * Sets the default cell renderer for the specified column
     * The column numbers are not affected by model changes.
     * 
     * @param column the column, must eb &gt;= 0
     * @param cellRenderer the CellRenderer to use or null to restore the global default
     */
    public void setColumnDefaultCellRenderer(int column, CellRenderer cellRenderer) {
        if(column >= columnDefaultCellRenderer.length) {
            CellRenderer[] tmp = new CellRenderer[Math.max(column+1, numColumns)];
            System.arraycopy(columnDefaultCellRenderer, 0, tmp, 0, columnDefaultCellRenderer.length);
            columnDefaultCellRenderer = tmp;
        }

        columnDefaultCellRenderer[column] = cellRenderer;
    }
    
    /**
     * Returns the default cell renderer for the specified column
     * @param column the column, must eb &gt;= 0
     * @return the previously set CellRenderer or null if non was set
     */
    public CellRenderer getColumnDefaultCellRenderer(int column) {
        if(column < columnDefaultCellRenderer.length) {
            return columnDefaultCellRenderer[column];
        }
        return null;
    }
    
    protected CellRenderer getCellRendererNoDefault(Object data) {
        Class<? extends Object> dataClass = data.getClass();
        return cellRenderers.get(dataClass);
    }
    
    protected CellRenderer getDefaultCellRenderer(int col) {
        CellRenderer cellRenderer = getColumnDefaultCellRenderer(col);
        if(cellRenderer == null) {
            cellRenderer = stringCellRenderer;
        }
        return cellRenderer;
    }
    
    protected CellRenderer getCellRenderer(Object data, int col) {
        CellRenderer cellRenderer = getCellRendererNoDefault(data);
        if(cellRenderer == null) {
            cellRenderer = getDefaultCellRenderer(col);
        }
        return cellRenderer;
    }

    protected CellRenderer getCellRenderer(int row, int col) {
        final Object data = getCellData(row, col);
        if(data != null) {
            CellRenderer cellRenderer = getCellRenderer(data, col);
            cellRenderer.setCellData(row, col, data);
            return cellRenderer;
        }
        return null;
    }

    protected int computeRowHeight(int row) {
        int height = 0;
        for(int column = 0; column < numColumns; column++) {
            CellRenderer cellRenderer = getCellRenderer(row, column);
            if(cellRenderer != null) {
                height = Math.max(height, cellRenderer.getPreferredHeight());
                column += Math.max(cellRenderer.getColumnSpan() - 1, 0);
            }
        }
        return height;
    }

    protected void removeCellWidget(Widget widget) {
        int idx = cellWidgetContainer.getChildIndex(widget);
        if(idx >= 0) {
            cellWidgetContainer.removeChild(idx);
        }
    }

    void insertCellWidget(int row, int column, WidgetEntry widgetEntry) {
        CellWidgetCreator cwc = (CellWidgetCreator)getCellRenderer(row, column);
        Widget widget = widgetEntry.widget;

        if(widget != null) {
            try {
            	if (cwc == null) {
            		// should not happen, but is probably the source of the NPE that shows up now and again
            		throw new Exception("CellWidgetCreator unexpectedly null");
            	}
            	
				if(widget.getParent() != cellWidgetContainer) {
				    cellWidgetContainer.insertChild(widget, cellWidgetContainer.getNumChildren());
				}

				int x = getColumnStartPosition(column);
				int w = getColumnEndPosition(column) - x;
				int y = getRowStartPosition(row);
				int h = getRowEndPosition(row) - y;

				cwc.positionWidget(widget, x + getOffsetX(), y + getOffsetY(), w, h);
			
            } catch (Exception e) {
				TwlResourceManager.getInstance().log(LOG_TYPE.error, this, String.format(
						"StandardDataSizedTable.insertCellWidget: error %s cwc=%s widget=%s container=%s"
						, e.getMessage(), cwc == null ? "null" : cwc, widget, cellWidgetContainer));
				e.printStackTrace();
				TwlResourceManager.getInstance().getGuiManager().warnOnErrorMessage(CoreLocalisation.Key.INTERMITTENT_BUG_ERROR, true, e);
			}
        }
    }

    protected void updateCellWidget(int row, int column) {
        WidgetEntry we = (WidgetEntry)widgetGrid.get(row, column);
        Widget oldWidget = (we != null) ? we.widget : null;
        Widget newWidget = null;

        CellRenderer cellRenderer = getCellRenderer(row, column);
        if(cellRenderer instanceof CellWidgetCreator) {
            CellWidgetCreator cellWidgetCreator = (CellWidgetCreator)cellRenderer;
            if(we != null && we.creator != cellWidgetCreator) {
                // the cellWidgetCreator has changed for this cell
                // discard the old widget
                removeCellWidget(oldWidget);
                oldWidget = null;
            }
            newWidget = cellWidgetCreator.updateWidget(oldWidget);
            if(newWidget != null) {
                if(we == null) {
                    we = new WidgetEntry();
                    widgetGrid.set(row, column, we);
                }
                we.widget = newWidget;
                we.creator = cellWidgetCreator;
            }
        }

        if(newWidget == null && we != null) {
            widgetGrid.remove(row, column);
        }
        
        if(oldWidget != null && newWidget != oldWidget) {
            removeCellWidget(oldWidget);
        }
    }

    protected void updateAllCellWidgets() {
        if(!widgetGrid.isEmpty() || hasCellWidgetCreators) {
            for(int row=0 ; row<numRows ; row++) {
                for(int col=0 ; col<numColumns ; col++) {
                    updateCellWidget(row, col);
                }
            }
        }

        updateAllCellWidgets = false;
    }

    protected void removeAllCellWidgets() {
        cellWidgetContainer.removeAllChildren();
    }

    /**
     * Because of a bug where sometimes the player settings list has widgets of 0 length 
     * check on the first row that no widgets have 0 length. Called from StandardTableList.isLayoutInitComplete()
     * @return
     */
    public boolean areAllCellWidgetRowsSized() {
        if(widgetGrid.isEmpty() || !hasCellWidgetCreators) {
			TwlResourceManager.getInstance().log(LOG_TYPE.info, this, "SimpleDataSizedTable.areAllCellWidgetRowsSized: not tested empty="+widgetGrid.isEmpty()+" hasCreators="+hasCellWidgetCreators);
        	return false;
        }
        else {
            for(int col=0 ; col<numColumns ; col++) {
                WidgetEntry we = (WidgetEntry)widgetGrid.get(0, col);
                if (we == null || we.widget == null || we.widget.getWidth() == 0) {
                	return false;
                }
            }
            return true;
        }
    }
    
    protected int getRowUnderMouse(int y) {
        y -= getOffsetY();
        int row = getRowFromPosition(y);
        return row;
    }

    protected int getColumnUnderMouse(int x) {
        x -= getOffsetX();
        int col = columnModel.getIndex(x);
        return col;
    }

    @Override
    protected boolean handleEvent(Event evt) {
        if(evt.isKeyEvent() &&
                keyboardSearchHandler != null &&
                keyboardSearchHandler.isActive() &&
                keyboardSearchHandler.handleKeyEvent(evt)) {
            return true;
        }
        
        if(super.handleEvent(evt)) {
            return true;
        }

        if(evt.isMouseEvent()) {
            return handleMouseEvent(evt);
        }

        if(evt.isKeyEvent() &&
                keyboardSearchHandler != null &&
                keyboardSearchHandler.handleKeyEvent(evt)) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean handleKeyStrokeAction(String action, Event event) {
        if(!super.handleKeyStrokeAction(action, event)) {
            if(selectionManager == null) {
                return false;
            }
            if(!selectionManager.handleKeyStrokeAction(action, event)) {
                return false;
            }
        }
        // remove focus from children
        requestKeyboardFocus(null);
        return true;
    }

    protected boolean handleMouseEvent(Event evt) {
        final Event.Type evtType = evt.getType();

        final int row = getRowUnderMouse(evt.getMouseY());
        final int column = getColumnUnderMouse(evt.getMouseX());

        if (row != lastMouseRow || column != lastMouseColumn) {
        	resetTooltip();
        	lastMouseRow = row;
        	lastMouseColumn = column;
        }
        
        if(selectionManager != null) {
            selectionManager.handleMouseEvent(row, column, evt);
        }
        
        if(evtType == Event.Type.MOUSE_CLICKED && evt.getMouseClickCount() == 2) {
            if(callbacks != null) {
                for(Callback cb : callbacks) {
                    cb.mouseDoubleClicked(row, column);
                }
            }
        }

        if(evtType == Event.Type.MOUSE_BTNUP && (
        		evt.getMouseButton() == Event.MOUSE_RBUTTON
        		)) {
            if(callbacks != null) {
                for(Callback cb : callbacks) {
                    cb.mouseRightClick(row, column, evt);
                }
            }
        }

        // let ScrollPane handle mouse wheel
        return evtType != Event.Type.MOUSE_WHEEL;
    }

    protected void updateAllColumnWidth() {
    	setColumnTextWidths();

        if(getInnerWidth() > 0) {
            columnModel.initializeAll(numColumns);
            updateAllColumnWidth = false;
        }
    }

    protected void updateAll() {
    	
        if(!widgetGrid.isEmpty()) {
            removeAllCellWidgets();
            widgetGrid.clear();
        }

        updateAllCellWidgets = true;
        updateAllColumnWidth = true;
        invalidateLayout();
    }


//    protected void modelRowChanged(int row) {
//        for(int col=0 ; col<numColumns ; col++) {
//            updateCellWidget(row, col);
//        }
//        invalidateLayoutLocally();
//    }
//
//    protected void modelRowsChanged(int idx, int count) {
//        checkRowRange(idx, count);
//        for(int i=0 ; i<count ; i++) {
//            for(int col=0 ; col<numColumns ; col++) {
//                updateCellWidget(idx+i, col);
//            }
//        }
//        invalidateLayoutLocally();
//    }
//
//    protected void modelCellChanged(int row, int column) {
//        checkRowIndex(row);
//        checkColumnIndex(column);
//        updateCellWidget(row, column);
//        invalidateLayout();
//    }

//    protected void modelRowsInserted(int row, int count) {
//        checkRowRange(row, count);
//
//        if(!widgetGrid.isEmpty() || hasCellWidgetCreators) {
//            removeAllCellWidgets();
//            widgetGrid.insertRows(row, count);
//
//            for(int i=0 ; i<count ; i++) {
//                for(int col=0 ; col<numColumns ; col++) {
//                    updateCellWidget(row+i, col);
//                }
//            }
//        }
//        // invalidateLayout() before sp.setScrollPositionY() as this may cause a
//        // call to invalidateLayoutLocally() which is redundant.
//        invalidateLayout();
//        if(row < getRowFromPosition(scrollPosY)) {
//            ScrollPane sp = ScrollPane.getContainingScrollPane(this);
//            if(sp != null) {
//                int rowsStart = getRowStartPosition(row);
//                int rowsEnd = getRowEndPosition(row + count - 1);
//                sp.setScrollPositionY(scrollPosY + rowsEnd - rowsStart);
//            }
//        }
//        if(selectionManager != null) {
//            selectionManager.rowsInserted(row, count);
//        }
//    }
//
//    protected void modelRowsDeleted(int row, int count) {
//        if(row+count <= getRowFromPosition(scrollPosY)) {
//            ScrollPane sp = ScrollPane.getContainingScrollPane(this);
//            if(sp != null) {
//                int rowsStart = getRowStartPosition(row);
//                int rowsEnd = getRowEndPosition(row + count - 1);
//                sp.setScrollPositionY(scrollPosY - rowsEnd + rowsStart);
//            }
//        }
//
//        if(!widgetGrid.isEmpty()) {
//            widgetGrid.iterate(row, 0, row+count-1, numColumns, removeCellWidgetsFunction);
//            widgetGrid.removeRows(row, count);
//        }
//        if(selectionManager != null) {
//            selectionManager.rowsDeleted(row, count);
//        }
//        invalidateLayout();
//    }
//
//    protected void modelColumnsInserted(int column, int count) {
////TODO: modelColumnsInserted
//System.out.println("modelColumnsInserted: not sure if this is needed");    	
////        checkColumnRange(column, count);
////        ColumnHeader[] newColumnHeaders = new ColumnHeader[numColumns];
////        System.arraycopy(columnHeaders, 0, newColumnHeaders, 0, column);
////        System.arraycopy(columnHeaders, column, newColumnHeaders, column+count,
////                numColumns - (column+count));
////        for(int i=0 ; i<count ; i++) {
////            newColumnHeaders[column+i] = createColumnHeader(column+i);
////        }
////        columnHeaders = newColumnHeaders;
////        updateColumnHeaderNumbers();
////
////        columnModel.insert(column, count);
////
////        if(!widgetGrid.isEmpty() || hasCellWidgetCreators) {
////            removeAllCellWidgets();
////            widgetGrid.insertColumns(column, count);
////
////            for(int row=0 ; row<numRows ; row++) {
////                for(int i=0 ; i<count ; i++) {
////                    updateCellWidget(row, column + i);
////                }
////            }
////        }
////        if(column < getColumnStartPosition(scrollPosX)) {
////            ScrollPane sp = ScrollPane.getContainingScrollPane(this);
////            if(sp != null) {
////                int columnsStart = getColumnStartPosition(column);
////                int columnsEnd = getColumnEndPosition(column + count - 1);
////                sp.setScrollPositionX(scrollPosX + columnsEnd - columnsStart);
////            }
////        }
////        invalidateLayout();
//    }

    protected class ColumnSizeSequence extends SizeSequence {
        @Override
        protected void initializeSizes(int index, int count) {
            for(int i=0 ; i<count ; i++) {
                int width = computePreferredColumnWidth(index+i);
                table[index+i] = width;
            }
        }
        protected boolean update(int index) {
            int width;
            width = computePreferredColumnWidth(index);
            return setSize(index, width);
        }
        int computePreferredWidth() {
            int count = getNumColumns();
            int sum = 0;
            for(int i=0 ; i<count ; i++) {
                int width = computePreferredColumnWidth(i);
                sum += width;
            }
            return sum;
        }
    }

    class RemoveCellWidgets implements SparseGrid.GridFunction {
        public void apply(int row, int column, Entry e) {
            WidgetEntry widgetEntry = (WidgetEntry)e;
            Widget widget = widgetEntry.widget;
            if(widget != null) {
                removeCellWidget(widget);
            }
        }
    }

    class InsertCellWidgets implements SparseGrid.GridFunction {
        public void apply(int row, int column, Entry e) {
            insertCellWidget(row, column, (WidgetEntry)e);
        }
    }

    static class WidgetEntry extends SparseGrid.Entry {
        Widget widget;
        CellWidgetCreator creator;
    }

    static class CellWidgetContainer extends Widget {
        CellWidgetContainer() {
            setTheme("");
            setClip(true);
        }

        @Override
        protected void childInvalidateLayout(Widget child) {
            // always ignore
        }

        @Override
        protected void sizeChanged() {
            // always ignore
        }

        @Override
        protected void childAdded(Widget child) {
            // always ignore
        }

        @Override
        protected void childRemoved(Widget exChild) {
            // always ignore
        }

        @Override
        protected void allChildrenRemoved() {
            // always ignore
        }
    }
    
    public static abstract class DataSizingTableModel extends AbstractTableModel {
    	public abstract boolean isColumnDataSized(int column);
    	public abstract int getColumnTextWidth(int column, Font useFont);
        public String getColumnHeaderText(int column) {
            return "!";
        }
    }
    
    /**
     * Settings for each column from the xml, and from the data
     */
    class ColumnWidth {
    	private int prefWidth = 0;
    	private int minWidth = 0;
    	private int maxWidth = 32767;
    	// for non-text sized columns that's enough already
    	// when text is involved, also have to take into account the border around it, and widget padding/artifacts
    	private int textWidth = 0;
    	// border for the cell (around any widget that's in there)
    	private Border border;
    	// any more horizontal space needed for the widget, eg. combo needs both text padding and button 
    	private int widgetExtraWidth = 0;
    	// only needed when the cell concerned uses a different font to the default
    	private LWJGLFont font; 
    	
    	/**
    	 * Reads the xml to get all the extra data for how to lay this column out (see fields above)
    	 * @param column
    	 */
    	ColumnWidth(int column) {
            loadColumnThemeInfo(column);
    	}

        private void loadColumnThemeInfo(int column) {
            if(tableBaseThemeInfo != null) {
            	ThemeInfo columnWidthsTheme = tableBaseThemeInfo.getChildTheme("columnWidths");
            	if (columnWidthsTheme != null) {
            		ThemeInfo widthTheme = columnWidthsTheme.getChildTheme(Integer.toString(column));
            		if (widthTheme != null) {
            			Object obj = widthTheme.getParameterValue("width", false);
            			if (obj != null) {
            				DialogLayout.Gap gap = null;
            				if (obj instanceof DialogLayout.Gap) {
                				gap = (DialogLayout.Gap) obj;
            				}
            				else if (obj instanceof Integer) {
                				gap = new DialogLayout.Gap(((Integer)obj).intValue());
            				}
            				
            				if (gap != null) {
            	            	prefWidth = gap.preferred;
            	            	minWidth = gap.min;
            	            	maxWidth = gap.max;
            				}
            			}
            			obj = widthTheme.getParameterValue("border", false);
            			if (obj != null && obj instanceof Border) {
            				border = (Border) obj;
            			}
            			obj = widthTheme.getParameterValue("extraWidth", false);
            			if (obj != null && obj instanceof Integer) {
            				widgetExtraWidth = (Integer) obj;
            			}            			
            			obj = widthTheme.getParameterValue("font", false);
            			if (obj != null && obj instanceof LWJGLFont) {
            				font = (LWJGLFont) obj;
            				TwlResourceManager.getInstance().log(LOG_TYPE.warning, this, "SimpleDataSizedTable.ColumnWidth.loadColumnThemeInfo: font not used="+font);
            			}
            		}
            	}
            }
        }

		public Font getFont() {
			return font;
		}

		public void setTextWidth(int textWidth) {
			this.textWidth = textWidth;
		}
		
		/**
		 * Compares the various values set for the column and returns the perfect width
		 * @return
		 */
		public int getColumnWidth() {
			int textBasedWidth = textWidth + widgetExtraWidth;
			if (border != null) {
				textBasedWidth += border.getBorderLeft();
				textBasedWidth += border.getBorderRight();
			}
			return Math.min(
						Math.max(minWidth // don't return less than the min
								, Math.max(prefWidth // in practice, prefWidth should only be set for non-text based columns
										, textBasedWidth))
					, maxWidth); // don't go wider than the max
		}
    	
    }

}
