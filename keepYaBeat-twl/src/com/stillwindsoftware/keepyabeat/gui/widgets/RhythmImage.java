package com.stillwindsoftware.keepyabeat.gui.widgets;

import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.gui.SimpleDataSizedTable.DataSizingTableModel;
import com.stillwindsoftware.keepyabeat.gui.TargetGovernerSharer;
import com.stillwindsoftware.keepyabeat.model.Rhythm;
import com.stillwindsoftware.keepyabeat.platform.GuiManager;
import com.stillwindsoftware.keepyabeat.platform.PlatformEventListener;
import com.stillwindsoftware.keepyabeat.platform.PlatformFont;
import com.stillwindsoftware.keepyabeat.platform.PlatformImage;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.BaseRhythmDraughter;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DrawingSurface;
import com.stillwindsoftware.keepyabeat.twlModel.ImportRhythmsTableModel;
import com.stillwindsoftware.keepyabeat.twlModel.RhythmsTableModel;

import de.matthiasmann.twl.Border;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.TableBase.CellWidgetCreator;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;

/**
 * subclass of widget to show a rhythm's image
 */
public class RhythmImage extends Widget implements DrawingSurface {

	private DataSizingTableModel rhythmsModel;

	// shared target governer for all images in the list
	private DraughtTargetGoverner targetGoverner;
	private BaseRhythmDraughter rhythmDraughter;
	private boolean draughterIsInitialised = false;
	private MutableRectangle dimension = new MutableRectangle(0f, 0f, 0f, 0f);
	private GuiManager guiManager = TwlResourceManager.getInstance().getGuiManager();
	
    public RhythmImage(DataSizingTableModel rhythmsModel, DraughtTargetGoverner targetGoverner) {
        this.rhythmsModel = rhythmsModel;
        this.targetGoverner = targetGoverner;
        setTheme("");
    }

	void setData(RhythmImageValue data) {
        // set up the draughter
        Rhythm rhythm = (rhythmsModel instanceof RhythmsTableModel 
        		? ((RhythmsTableModel) rhythmsModel).getRhythm(data.getValue())
        		: ((ImportRhythmsTableModel) rhythmsModel).getRhythm(data.getValue()));
        if (rhythmDraughter == null) {
        	rhythmDraughter = new BaseRhythmDraughter(rhythm, false);
        }
        else {
        	// already have one, just initModel with the new rhythm
        	rhythmDraughter.initModel(rhythm, false);
        }

        // set it to have to reinit if the data changes
        draughterIsInitialised = false;
    }
    
    /**
     * Position widget is what gives the position for a table widget, using that call in the cell creator
     * rather than layout, which reports a 0 width for the first row and first call
     * @param x
     * @param y
     * @param w
     * @param h
     */
	public void positionWidgetCallback(int x, int y, int w, int h) {

		if (w != 0 && h != 0) {
			dimension.setXYWH(x, y, w, h);
			
			// have what's needed to create the draughter, set it all up and do a first arrange
			if (draughterIsInitialised) {
				// already set up, just rearrange since layout is invalid
				rhythmDraughter.arrangeRhythm();
			}
			else {
				rhythmDraughter.initTarget(targetGoverner, this);
				rhythmDraughter.initDrawing();
				draughterIsInitialised = true;
			}
		}
	}

	@Override
	protected void paint(GUI gui) {
		rhythmDraughter.drawRhythm();
	}

	@Override
	public MutableRectangle getDrawingRectangle() {
		return dimension;
	}

	@Override
	public void drawImage(PlatformImage image, float x, float y, float w, float h) {
		image.draw((int)(this.getX() + x), (int)(this.getY() + y), (int)w, (int)h);
	}

	/**
	 * Same considerations for clipping
	 */
	@Override
	public void drawImage(PlatformImage image, float x, float y, float w, float h, float clipx, float clipy, float clipw, float cliph) {

		if (getY() < guiManager.getScreenHeight()) {
			guiManager.pushClip(this.getX() + clipx, this.getY() + clipy, clipw, cliph);
			drawImage(image, x, y, w, h);
			guiManager.popClip();
		}
	}

	@Override
	public void drawText(PlatformFont font, int number, float x, float y) {}
	@Override
	public void registerForEvents(PlatformEventListener eventListener) {}
	@Override
	public boolean triggeredBackEvent() {return false;}

	/**
     * A CellWidgetCreator instance is used to create and position the 
     * widget inside the table cell.
     */
    public static class RhythmImageCellWidgetCreator implements CellWidgetCreator {
        private int cellHeight;
        private int cellWidth;
    	private Border border;
        private RhythmImageValue data;
    	private DataSizingTableModel rhythmsModel;
    	private TargetGovernerSharer rhythmsList;

        public RhythmImageCellWidgetCreator(DataSizingTableModel rhythmsModel, TargetGovernerSharer rhythmsList) {
			this.rhythmsModel = rhythmsModel;
			this.rhythmsList = rhythmsList;
		}

		public void applyTheme(ThemeInfo themeInfo) {
        	cellHeight = themeInfo.getParameter("minHeight", 50);
        	cellWidth = themeInfo.getParameter("maxWidth", 200);
    		border = themeInfo.getParameterValue("border", false, Border.class);
        }

        public String getTheme() {
        	return "rhythmimagecellrenderer";
        }

        /**
         * Update or create the widget.
         *
         * @param existingWidget null on first call per cell or the previous
         *   widget when an update has been send to that cell.
         * @return the widget to use for this cell
         */
        public Widget updateWidget(Widget existingWidget) {
        	RhythmImage rhythmImage = (RhythmImage)existingWidget;
            if(rhythmImage == null) {
            	rhythmImage = new RhythmImage(rhythmsModel, rhythmsList.getTargetGoverner());
            }

            rhythmImage.setData(data);
            return rhythmImage;
        }

        public void positionWidget(Widget widget, int x, int y, int w, int h) {
    		((RhythmImage)widget).positionWidgetCallback(x, y, w, h);
        	int realW = Math.min(w, cellWidth);
        	realW -= (border.getBorderLeft() + border.getBorderRight());
            widget.setPosition((int)(x + (w - realW) / 2.0f), y);
            widget.setSize(realW, h);
        }

        public void setCellData(int row, int column, Object data) {
            // we have to remember the cell data for the next call of updateWidget
        	this.data = (RhythmImageValue)data;
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
     * This is a very simple model class which will store the currently selected entry.
     */
    public static class RhythmImageValue {
    	private int value;
    	
        public RhythmImageValue() {
        }

		public void setValue(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

    }

	@Override
	public void drawText(PlatformFont font, String text, float x, float y) {
		// not used here
	}

	@Override
	public void pushTransform(float rotation, float transX, float transY) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void popTransform() {
		// TODO Auto-generated method stub
		
	}


}

