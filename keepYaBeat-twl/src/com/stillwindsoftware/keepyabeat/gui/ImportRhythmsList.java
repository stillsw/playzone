package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.gui.widgets.ImportRhythmsCheckBoxButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.ImportRhythmsCheckBoxButton.ImportRhythmsCheckBoxButtonValue;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmImage;
import com.stillwindsoftware.keepyabeat.gui.widgets.RhythmImage.RhythmImageValue;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlFont;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlGuiManager;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.DrawNumbersPlacement;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.RhythmAlignment;
import com.stillwindsoftware.keepyabeat.player.DraughtTargetGoverner.SubZoneActionable;
import com.stillwindsoftware.keepyabeat.twlModel.ImportRhythmsTableModel;
import com.stillwindsoftware.keepyabeat.utils.ImportRhythm;
import com.stillwindsoftware.keepyabeat.utils.RhythmSharer;

import de.matthiasmann.twl.ComboBox;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.SimpleChangableListModel;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLFont;

/**
 * Shows the rhythms loaded for possible import with a checkbox for each to select
 * Popup tooltip shows detailed rhythm.
 * @author tomas stubbs
 */
public class ImportRhythmsList extends StandardTableList implements TargetGovernerSharer {

	private RhythmSharer sharer;
	private DraughtTargetGoverner targetGoverner, tooltipTargetGoverner;

	private ComboBox<String> importModeCombo;
	
	// convenience fields
	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
    private TwlGuiManager guiManager = (TwlGuiManager) resourceManager.getGuiManager();
    
	public ImportRhythmsList(RhythmSharer sharer) {
		this.sharer = sharer;
	}
	
	/**
	 * Dialog contained in a confirmation popup, limit size of the list to most of the height of the screen
	 */
	@Override
	boolean computeDimensions() {
		setMaxWidth((int)(guiManager.getScreenWidth() * .95f));
		setMaxHeight((int)(guiManager.getScreenHeight() - 110)); // leave space for dialog bits and pieces

		return super.computeDimensions();
	}

	@Override
	protected void doLayout() {
		// make sure dimensions are computed
		resetDimensionsComputed();
		super.doLayout();
		
		// find the popup window to make sure it resizes if it's too big for the window
		Widget parent = getParent();
		
		while (parent != null && !parent.equals(getGUI())) {
			if (parent instanceof ConfirmationPopup.ConfirmPopupWindow) {
				ConfirmationPopup.ConfirmPopupWindow popup = (ConfirmationPopup.ConfirmPopupWindow) parent;
				boolean overSize = popup.getChild(0).getHeight() > guiManager.getScreenHeight();
				if (overSize) {
					popup.getChild(0).setSize(popup.getChild(0).getWidth(), Math.min(popup.getChild(0).getHeight(), popup.getHeight()));
				}
			}
			parent = parent.getParent();
		}

	}
	
	/**
	 * Override superclass method to create the label and mode combo
	 */
    @Override
	protected void initTopBox() {
		topBox = new DialogLayout();
		topBox.setTheme("topBox");
		Label fileSavedLabel = new Label(
				String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_SELECTION_LABEL)
						, (sharer.isContainsExistingRhythms() 
								? resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_SELECTION_WARNING) : "")));
		
		// construct list of import options
        final SimpleChangableListModel<String> importOptions = new SimpleChangableListModel<String>();
        // only selected is always first, and default (0)
        importOptions.addElement(resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_MODE_ONLY_SELECTED));
        // all, with optional overwrite warning, (1)
        importOptions.addElement(String.format(resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_MODE_ALL)
				, (sharer.isContainsExistingRhythms() 
						? resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_MODE_ALL_WARNING) : "")));
        // all non-existing, optionally (3)
        if (sharer.isContainsExistingRhythms()) {
        	importOptions.addElement(resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHMS_MODE_NON_EXISTING));
        }
        
        importModeCombo = new ComboBox<String>(importOptions);
		importModeCombo.addCallback(new Runnable() {
	            public void run() {
	            	ArrayList<ImportRhythm> importRhythmsModel = sharer.getRhythmsFromInput();
	            	for (int i = 0; i < importRhythmsModel.size(); i++) {
	            		// 0 selected only, so don't set anything
	            		if (importModeCombo.getSelected() != 0) {
		            		importRhythmsModel.get(i).setSelected(
	            					importModeCombo.getSelected() == 1 // all
	            						? true
	            						: !importRhythmsModel.get(i).isAlreadyInLibrary()  // all non-existing
	            				);
	            		}
	            	}

	            	// cause checkboxes to update
	            	((ImportRhythmsTableModel)tableModel).dataChanged();
	            }
	        });

        importModeCombo.setComputeWidthFromModel(true);
		importModeCombo.setSelected(0);

		topBox.setHorizontalGroup(topBox.createParallelGroup()
				.addWidget(fileSavedLabel)
				.addGroup(topBox.createSequentialGroup(importModeCombo).addGap()));
    	topBox.setVerticalGroup(topBox.createSequentialGroup(fileSavedLabel, importModeCombo));
    }

	@Override
	protected void initLayout() {
        setTheme(getLayoutThemeName());

        // enclosing groups
        Group hGroup = createParallelGroup();
        Group vGroup = createSequentialGroup();

        initTopBox();
        
		hGroup.addWidget(topBox);
        vGroup.addWidget(topBox);

        // add the scrollpane
		hGroup.addGroup(createSequentialGroup(scrollPane));
        vGroup.addGroup(createParallelGroup(scrollPane));

        setHorizontalGroup(hGroup);
        setVerticalGroup(vGroup);
	}

	@Override
	protected String getLayoutThemeName() {
		return "importRhythmsList";
	}
	
	/**
	 * Needs the images to create the target governer that will cache images and control drawing
	 * to the canvases that show the rhythms
	 */
	@Override
	protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
		TwlFont fullBeatsFontSmall = new TwlFont(themeInfo.getParameterValue("fullBeatsFontSmall", false, LWJGLFont.class));

		// make a sharing target governer, give it a minimal width for sub beats
		targetGoverner = new DraughtTargetGoverner(resourceManager, true, RhythmsList.MINIMAL_SUB_BEATS_WIDTH, SubZoneActionable.NEVER);

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

	public DraughtTargetGoverner getTooltipTargetGoverner() {
		return tooltipTargetGoverner;
	}

	public ComboBox<String> getImportModeCombo() {
		return importModeCombo;
	}

	@Override
	protected void installTableCellRenderers() {
        table.registerCellRenderer(RhythmImageValue.class, new RhythmImage.RhythmImageCellWidgetCreator(tableModel, this));
        table.registerCellRenderer(ImportRhythmsCheckBoxButtonValue.class
        		, new ImportRhythmsCheckBoxButton.ImportRhythmsCheckBoxButtonCellWidgetCreator(sharer.getRhythmsFromInput(), this));
		super.installTableCellRenderers();
	}

	/**
	 * The table model disables any selection
	 */
	@Override
	protected void initScrollPaneToModel() {
        tableModel = new ImportRhythmsTableModel(sharer.getRhythmsFromInput(), this);

        table = new SimpleDataSizedTable(tableModel);

		// no selection
		initScrollPaneToModel(table, false, false, "rhythmsTable", "rhythmsScrollpane");
	}

}

    
    



