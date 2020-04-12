package com.stillwindsoftware.keepyabeat.gui.widgets;

import org.lwjgl.input.Mouse;

import com.stillwindsoftware.keepyabeat.gui.TagsPickList;
import com.stillwindsoftware.keepyabeat.gui.TagsPickList.PickedTagReceiver;
import com.stillwindsoftware.keepyabeat.model.Library;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.model.Tags;
import com.stillwindsoftware.keepyabeat.model.transactions.LibraryListener;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DialogLayout;
import de.matthiasmann.twl.EditField;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Event.Type;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.PopupWindow;
import de.matthiasmann.twl.ScrollPane;
import de.matthiasmann.twl.ScrollPane.Fixed;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.renderer.AnimationState.StateKey;

/**
 * Lays out a name and tags box that can invoke a popup list of values to add tags to the list
 * Used by rhythms list for its top search box, and also when saving a rhythm from there or from
 * play/edit rhythm modules.
 * @author tomas stubbs
 *
 */
public class NameAndTagsBox extends DialogLayout {

	// make sure of a unique id for hashing
	protected long id = System.nanoTime();
	
	protected EditField nameField;
	protected TagList tagsList;
	protected ScrollPane tagsListScrollPane;
	private PickedTagReceiver pickedTagReceiver;

	// label that goes in the empty list
	protected Button tagsSearchLabel;

	protected final TwlResourceManager resourceManager = TwlResourceManager.getInstance();
	
	public NameAndTagsBox() {
	}
	
	public void init(boolean showSearchImg, PickedTagReceiver pickedTagReceiver) {
		this.pickedTagReceiver = pickedTagReceiver;
		initLayout(showSearchImg);
	}

	public EditField getNameField() {
		return nameField;
	}

	public TagList getTagsList() {
		return tagsList;
	}

	/**
	 * By default this widget is embedded and takes sizing from parent widget
	 * Sub-classes that need to set other params, like set name which is a popup,
	 * can override to supply a different theme.
	 * @return
	 */
	protected String getThemeName() {
		return "nameAndTagsBox";
	}
	
	/**
	 * rhythmSetNameConfirmation has a different text because it's part of the search box
	 * @return
	 */
	protected String getTagsSearchLabelText() {
		return resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_SEARCH_LABEL);
	}
	
	/**
	 * Default implementation is a BoxLayout, sub-classes can override.
	 * eg. RhythmSetNameConfirmation uses a TagsFlowList
	 */
	protected void initTagsList() {
        // tags search, have a box layout inside a scrolling region that only scrolls horizontally
        tagsList = new TagsBoxLayout();
	}
	
	private void initLayout(boolean showSearchImg) {
		setTheme(getThemeName());

        nameField = new EditField();
        nameField.setTheme("changeName");
        
        // search img 
        Label searchImg = new Label();
        searchImg.setTheme("searchBtn");
       
        tagsSearchLabel = new Button(getTagsSearchLabelText());
        tagsSearchLabel.setTheme("tagsSearchLabel");
        tagsSearchLabel.getAnimationState().setAnimationState(StateKey.get("reverseVideo"), true);// so it's white
        tagsSearchLabel.setTooltipContent(
        		resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_SEARCH_BTN_TOOLTIP));
        tagsSearchLabel.addCallback(new Runnable() {
			@Override
			public void run() {
				popupTagsPickList();
			}
        });

        initTagsList();

        tagsListScrollPane = new ScrollPane((Widget)tagsList) {
			@Override
			protected boolean handleEvent(Event evt) {
				// catch mouse click to pop up tags lov
				if (evt.isMouseEvent() && evt.getType() == Type.MOUSE_CLICKED) {
					popupTagsPickList();
				}
				return evt.isMouseEventNoWheel();
			}
        };
        tagsListScrollPane.setTheme("tagsScrollpane");
        tagsListScrollPane.setTooltipContent(
        		resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_SEARCH_BTN_TOOLTIP));

        // 2 ways this can show, box layout is all on one line that scrolls horizontally
        // flow layout is multi-line-able and so allow scrolling vertically
        if (tagsList instanceof BoxLayout) {
        	tagsListScrollPane.setFixed(Fixed.VERTICAL);
        }
        else {
        	tagsListScrollPane.setFixed(Fixed.HORIZONTAL);
        }

        // search img 
        Button tagsSearchBtn = new Button();
        tagsSearchBtn.setTheme("tagSearchBtn");
        tagsSearchBtn.setTooltipContent(
        		resourceManager.getLocalisedString(TwlLocalisationKeys.TAGS_SEARCH_BTN_TOOLTIP));
        tagsSearchBtn.addCallback(new Runnable() {
			@Override
			public void run() {
				popupTagsPickList();
			}
        });
        
        // enclosing groups
        Group hGroup = createParallelGroup();
        Group vGroup = createSequentialGroup();
        
        hGroup.addGroup(createSequentialGroup(
        		createParallelGroup(nameField, tagsListScrollPane),
        		(showSearchImg ? createParallelGroup(searchImg, tagsSearchBtn) : createParallelGroup(tagsSearchBtn))
        		));		

        if (showSearchImg) {
	    	vGroup.addGroup(createParallelGroup()
	    			.addGroup(createSequentialGroup(nameField, tagsListScrollPane))
	    			.addGroup(createSequentialGroup(searchImg).addGap(DialogLayout.SMALL_GAP).addWidget(tagsSearchBtn).addGap()));
        }
        else {
	    	vGroup.addGroup(createParallelGroup()
	    			.addGroup(createSequentialGroup(nameField, tagsListScrollPane))
	    			.addGroup(createSequentialGroup().addGap().addWidget(tagsSearchBtn)));
        }
        
        setHorizontalGroup(hGroup);
        setVerticalGroup(vGroup);
        
	}

	private void popupTagsPickList() {
		final TagsPickList tagsPickList = pickedTagReceiver.getTagsPickList();
		tagsPickList.setTheme("tagsPickListFlow");
		// make a scrollpane to put the list in
		final ScrollPane sPane = new ScrollPane(tagsPickList);
		sPane.setTheme("tagsPickScrollpane");
		sPane.setFixed(Fixed.HORIZONTAL);

		PopupWindow tagsLov = new PopupWindow((Widget) pickedTagReceiver) {
			@Override
			protected void layout() {
				super.layout();
				// get it to register the correct size
				tagsPickList.adjustSize();
				int width = tagsPickList.getWidth();
				int height = tagsPickList.getHeight();
				
				// too high will show a scrollbar, so make the width bigger to accommodate it
				boolean heightExceedsMax = tagsPickList.getMaxHeight() < height;
				if (heightExceedsMax) {
					width += sPane.getVerticalScrollbar().getWidth();
					height = tagsPickList.getMaxHeight();
				}
				
				// set the size of the popup accordingly, the scrollpane adjusts automatically 
				// to the extra space for the scrollbar
			    int screenHeight = resourceManager.getGuiManager().getScreenHeight();
			    int screenWidth = resourceManager.getGuiManager().getScreenWidth();
			    
				setSize(width + getBorderHorizontal(), Math.min(height + getBorderVertical(), screenHeight));
				
				// position it at the mouse if room
				setPosition(
						Math.min(Math.max(((Widget) pickedTagReceiver).getInnerX()
									, Mouse.getX() - getWidth() / 2),
								screenWidth - getWidth()),
						Math.min(screenHeight - Mouse.getY(), screenHeight - getHeight()));							
			}
		};
		tagsLov.setTheme("tagsPickPopup");
		
		tagsLov.add(sPane);
		tagsLov.setCloseOnClickedOutside(true);
		tagsLov.setCloseOnEscape(true);
		tagsLov.openPopup();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NameAndTagsBox other = (NameAndTagsBox) obj;
		if (id != other.id)
			return false;
		return true;
	}

	/**
	 * Sub class of button that is associated with a tag
	 */
	public static class TagListButton extends Button {

		private Tag tag;

		public TagListButton(Tag tag) {
			this.tag = tag;
			
			// tag buttons may show which are in an import library (ie. in the RhythmTooltip for an import rhythm)
			// in which case the name may differ from the same tag in the local library)
			TwlResourceManager resourceManager = TwlResourceManager.getInstance();
			Library baseLib = resourceManager.getLibrary();
			
			// tag belongs to base library, just set name
			if (tag.getLibrary().equals(baseLib)) {
				this.setText(tag.getName());
			}
			else {
				// look for base tag with same key as import tag
				Tags baseTags = baseLib.getTags();
				Tag baseTag = (Tag) baseTags.lookup(tag.getKey());

				// found one and the name is different, set name to conflict string
				if (baseTag != null && !baseTag.getName().equalsIgnoreCase(tag.getName())) {
					this.setText(String.format(
    					resourceManager.getLocalisedString(TwlLocalisationKeys.IMPORT_RHYTHM_TAG_NAME_CONFLICT_TEXT)
    						, tag.getName(), baseTag.getName()));
				}
				else {
					// either no base tag, or it's the same
					this.setText(tag.getName());
				}
			}

			
		}

		public Tag getTag() {
			return tag;
		}
	}
	
	private class TagsBoxLayout extends BoxLayout implements TagList, LibraryListener {

		private TagsBoxLayout() {
			super(BoxLayout.Direction.HORIZONTAL);
	        setTheme("tagsBox");
		}

		@Override
		protected void beforeRemoveFromGUI(GUI gui) {
			super.beforeRemoveFromGUI(gui);
			resourceManager.getListenerSupport().removeListener(this);
		}

		@Override
		protected void afterAddToGUI(GUI gui) {
	        // listen to changes in tags
	        resourceManager.getListenerSupport().addListener(resourceManager.getLibrary().getTags(), this);
			super.afterAddToGUI(gui);
			addOrRemoveTagsSearchBtn();
		}
		
		protected void addOrRemoveTagsSearchBtn() {
			if (getNumChildren() == 0) {
				add(tagsSearchLabel);
			}
			else if (getChildIndex(tagsSearchLabel) != -1 && getNumChildren() > 1) {
				removeChild(tagsSearchLabel);				
			}
		}

		@Override
		public void addTagButton(TagListButton tagButton) {
			add(tagButton);
			tagButton.setCanAcceptKeyboardFocus(false);
			addOrRemoveTagsSearchBtn();
		}

		@Override
		public void removeTagButton(TagListButton tagButton) {
			removeChild(tagButton);
			addOrRemoveTagsSearchBtn();
		}

		@Override
		public void addTag(Tag tag) {
			// do nothing, means the calling class is handling it all
			// see RhythmsList.tagPicked()... it creates buttons itself
			// and calls addTagButton()
		}

		
		@Override
		public void itemChanged() {
			Tags tags = resourceManager.getLibrary().getTags();
			
			for (int i = getNumChildren()-1; i >= 0; i--) {
				Widget child = getChild(i);
				if (child instanceof TagListButton) {
					Tag tag = ((TagListButton)child).getTag();
					
					if (tags.lookup(tag.getKey()) == null) {
						// deleted
						removeChild(child);
						addOrRemoveTagsSearchBtn();
					}
					else if (!tag.getName().equals(((TagListButton) child).getText())) {
						// name changed
						((TagListButton) child).setText(tag.getName());
					}
				}
			}
			
			invalidateLayout();
		}

		// callback methods, called from the list owner... in this case the list is already listening
		// and these conditions are taken care of in itemChanged() 

		@Override
		public void tagChanged(Tag tag) {
		}

		@Override
		public void tagRemoved(Tag tag) {
		}
		
	}
	

}
