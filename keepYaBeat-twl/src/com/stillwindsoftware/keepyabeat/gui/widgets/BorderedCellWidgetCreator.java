package com.stillwindsoftware.keepyabeat.gui.widgets;

import de.matthiasmann.twl.Border;
import de.matthiasmann.twl.TableBase;
import de.matthiasmann.twl.TableBase.CellWidgetCreator;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;

/**
 * A CellWidgetCreator instance is used to create and position the 
 * widget inside the table cell with a border which may change its size/position
 */
public abstract class BorderedCellWidgetCreator extends TableBase.StringCellRenderer implements CellWidgetCreator {

	private Border border;
    
	public void applyTheme(ThemeInfo themeInfo) {
		border = themeInfo.getParameterValue("border", false, Border.class);
    }

    public void positionWidget(Widget widget, int x, int y, int w, int h) {
        // this method will size and position the button
        // If the widget should be centered (like a check box) then this
        // would be done here      	
		int realW = w;
		int realH = h;
		if (border != null && realW > 0 && realH > 0) {
			realW -= border.getBorderLeft();
			realW -= border.getBorderRight();
			x += border.getBorderLeft();
			realH -= border.getBorderTop();
			realH -= border.getBorderBottom();
			y += border.getBorderTop();
		}
        widget.setPosition(x, y);
        widget.setSize(realW, realH);
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
    	return 0;
    }
}
