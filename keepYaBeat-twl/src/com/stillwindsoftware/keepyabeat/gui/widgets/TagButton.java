package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable;
import com.stillwindsoftware.keepyabeat.gui.StandardTableList;
import com.stillwindsoftware.keepyabeat.model.TagsXmlImpl;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Border;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.TableBase.CellWidgetCreator;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.Font;

/**
 * subclass of Button to contain the listeners
 */
public class TagButton extends Label {
	private int row = -1;
	private TagsXmlImpl tags = (TagsXmlImpl) TwlResourceManager.getInstance().getLibrary().getTags();
	
    public TagButton() {
       setTheme("tagsListTagBtn");
    }
    
    void setData(TagButtonValue data) {
        this.row = data.getValue();
        setText(tags.get(data.getValue()).getName());
    }
    
	@Override
	protected boolean handleEvent(Event evt) {
    	// all button down events need to set the selection on the list if there is one
    	if (evt.getType() == Event.Type.MOUSE_BTNDOWN) {
    		StandardTableList.handleMouseClickOnRow(this, evt, row);
    	}
    	
		return super.handleEvent(evt);
	}

	/**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell. This class is also responsible to connect
     * all listeners so that updates to/from it can happen.
     */
    public static class TagButtonCellWidgetCreator implements CellWidgetCreator {
        private int cellHeight;
        private int cellWidth;
    	private Border border;
        private TagButtonValue data;
        private SimpleDataSizedTable dataSizedTable;
        
        /**
         * Store the sizing table so that each cell can be sized to its own text
         * @param dataSizedTable
         */
        public TagButtonCellWidgetCreator(SimpleDataSizedTable dataSizedTable) {
        	this.dataSizedTable = dataSizedTable;
		}

		public void applyTheme(ThemeInfo themeInfo) {
        	cellHeight = themeInfo.getParameter("minHeight", 50);
        	cellWidth = themeInfo.getParameter("maxWidth", 1000);
    		border = themeInfo.getParameterValue("border", false, Border.class);
        }

        public String getTheme() {
        	return "tagcellrenderer";
        }

        /**
         * Update or create the PlayButton widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	TagButton tagBtn = (TagButton)existingWidget;
            if(tagBtn == null) {
                tagBtn = new TagButton();
            }

            tagBtn.setData(data);
            return tagBtn;
        }

        public void positionWidget(Widget widget, int x, int y, int w, int h) {

        	Font columnFont = dataSizedTable.getColumnFont(0); // it's the 1st and only column
        	String text = ((TagButton)widget).getText();
        	int textWidth = Math.max(columnFont.computeTextWidth(text), 0);
        	textWidth += border.getBorderLeft();
        	textWidth += border.getBorderRight();

        	textWidth = Math.min(textWidth, cellWidth);
        	
            widget.setPosition(x, y);
            widget.setSize(textWidth, h);
        }

        public void setCellData(int row, int column, Object data) {
            // we have to remember the cell data for the next call of updateWidget
           this.data = (TagButtonValue)data;
        }

        public Widget getCellRenderWidget(int x, int y, int width, int height, boolean isSelected) {
            // this cell does not render anything itself
            return null;
        }

        public int getColumnSpan() {
            // no column spanning
            return 1;
        }

        public int getPreferredHeight() {
        	return cellHeight;
        }
    }

    /**
     * This is a very simple model class which will store the currently selected
     * entry.
     */
    public static class TagButtonValue {
    	private int value;
    	
        public TagButtonValue() {
        }

        public void setValue(int value) {
        	this.value = value;
        }

		public int getValue() {
			return value;
		}
    }


}

