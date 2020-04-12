package com.stillwindsoftware.keepyabeat.gui;

import java.util.ArrayList;

import com.stillwindsoftware.keepyabeat.gui.widgets.NameAndTagsBox.TagListButton;
import com.stillwindsoftware.keepyabeat.gui.widgets.TagList;
import com.stillwindsoftware.keepyabeat.model.Tag;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlLocalisationKeys;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.ThemeInfo;
import de.matthiasmann.twl.Widget;

/**
 * A list of tags laid out in a flow pattern. Can be used to show a list of tags
 * and is also the basis for the TagsPickList
 * @author tomas stubbs
 */
public class TagsFlowList extends Widget implements TagList {

	private Label noTagsLabel;
	private int buttonsGap;
	
	/**
	 * Provide an initial set of tags to show, these become buttons
	 * in the init process
	 * @param showTags
	 */
	public TagsFlowList(ArrayList<Tag> showTags) {
		initLayout(showTags);
	}

	@Override
	protected void applyTheme(ThemeInfo themeInfo) {
		super.applyTheme(themeInfo);
		buttonsGap = themeInfo.getParameter("buttonsGap", 0);
	}

	@Override
	protected void layout() {
		super.layout();
		computeDimensions();
	}

	/**
	 * How big to make this depends on the number of children and the available screen space
	 */
	private void computeDimensions() {
		// don't put any bordering on the popup or the scrollbar, it will just mess this up
		Widget parent = getParent();
		int parentX = parent.getInnerX();
		int parentY = parent.getInnerY();
//		System.out.println("parent x,y,w,h="+parent.getInnerX()+","+parent.getInnerY()+","+parent.getInnerWidth()+","+parent.getInnerHeight());
		
		if (noTagsLabel != null) {
//			System.out.println("adjust size label before = "+noTagsLabel.getWidth());
			noTagsLabel.adjustSize();
//			System.out.println("adjust size label after = "+noTagsLabel.getHeight());
			// no bigger than max no smaller than min
			int width = Math.min(noTagsLabel.getWidth() + getBorderHorizontal(), getMaxWidth());
			int height = noTagsLabel.getHeight() + getBorderVertical();
//			setSize(width, height);
			noTagsLabel.setPosition(parentX + (int)(width / 2.0f - noTagsLabel.getWidth() / 2.0f), 
					parentY + (int)(height / 2.0f - noTagsLabel.getHeight() / 2.0f));
			adjustSize();
		}
		else {
			// assemble the lines as an arraylist of arraylists
			ArrayList<ArrayList<Button>> lines = new ArrayList<ArrayList<Button>>();
			// make and add first line
			ArrayList<Button> currentLine = new ArrayList<Button>();
			lines.add(currentLine);

			// sum widths for this line
			int sumWidthsForLine = 0;
			int maxLineWidth = getMaxWidth() - getBorderHorizontal();
			int buttonHeight = 0;
			
			// read all the buttons 
			for (int i = 0; i < getNumChildren(); i++) {
				Button tagBtn = (Button) getChild(i);
				tagBtn.adjustSize();
				buttonHeight = tagBtn.getHeight(); // store for lines calc later

				// will adding this button go over the line length, add another line
				// unless it's the first button anyway, in which case have to add it and let it truncate
				if (tagBtn.getWidth() + sumWidthsForLine + // widths for this line so far 
						buttonsGap * currentLine.size() // plus number of gaps
								> maxLineWidth 			// greater than max line
						&& currentLine.size() != 0) {  	// provided there are already buttons on the line
					// advance a line
					currentLine = new ArrayList<Button>();
					lines.add(currentLine);
					sumWidthsForLine = 0;					
				}

				// for the unlikely case where the button is longer than the max line width, chop it
				if (tagBtn.getWidth() > maxLineWidth) {
					tagBtn.setSize(maxLineWidth, buttonHeight);
					String newText = tagBtn.getText();
					for (int j = newText.length(); j >= 0; j--) {
						newText = newText.substring(0, j) + "...";
						if (tagBtn.getFont().computeTextWidth(newText) + tagBtn.getBorderHorizontal() <= maxLineWidth) {
							tagBtn.setText(newText);
							break;
						}
					}
				}

				// add the button to the current line and its width to total for the line
				currentLine.add(tagBtn);
				sumWidthsForLine += tagBtn.getWidth();
			}

			// keep the widest width
			int widestLineWidth = 0;
			
			// got the lines, read through putting each button into its position
			for (int i = 0; i < lines.size(); i++) {
				currentLine = lines.get(i);
				
				// placement of Y is the parent popup + (complete line height * number of lines before it)
				int lineY = parentY + getBorderTop() + i * (buttonHeight + buttonsGap);
				sumWidthsForLine = 0; // start summing the widths
				
				for (int j = 0; j < currentLine.size(); j++) {
					Button tagBtn = currentLine.get(j);
					
					// place the button, x is parent popup + left border + gap * number of buttons before it + sum widths
					int buttonX = parentX + getBorderLeft() + j * (buttonsGap) + sumWidthsForLine; 
					tagBtn.setPosition(buttonX, lineY);
					
					// add the button's width to the sum for the line
					sumWidthsForLine += tagBtn.getWidth();
				}
				
				// finished a line, test for widest width
				int lineWidth = sumWidthsForLine + (currentLine.size()-1) * buttonsGap;
				widestLineWidth = Math.max(widestLineWidth, lineWidth);
			}
			
			// width is widest line provided not wider than max
			int width = Math.min(maxLineWidth, widestLineWidth) + getBorderHorizontal();

			// height is number of lines + gaps
			int linesHeight = lines.size() * buttonHeight + (lines.size()-1) * buttonsGap;
			int height = linesHeight + getBorderVertical();

			setSize(width, height);
		}
	}

	/**
	 * Pick list sub-class overrides this to do something on click
	 * @param tag
	 * @return
	 */
	protected Runnable makeTagButtonClickedCallback(TagListButton tagButton, Tag tag) {
		return null;
	}
	
	/**
	 * Sub class to get a different theme for the buttons
	 * @return
	 */
	protected String getButtonTheme() {
		return "tagsListTagBtn";
	}
	
	/**
	 * Sub classes show different message (for lov picking, 'no more tags', otherwise 'no tags')
	 * @return
	 */
	protected String getNoTagsMessage() {
		return TwlResourceManager.getInstance().getLocalisedString(TwlLocalisationKeys.NO_TAGS_MESSAGE);
	}
	
	/**
	 * Sub class to not show anything if empty list
	 * @return
	 */
	protected void showEmptyLabel() {
		noTagsLabel = new Label(getNoTagsMessage());
		noTagsLabel.setTheme("label");
		add(noTagsLabel);
	}
	
	protected void removeEmptyLabel() {
		if (noTagsLabel != null && getChildIndex(noTagsLabel) != -1) {
			removeChild(noTagsLabel);
		}
	}
	
	/**
	 * If have tags in list, create a button for each and add them.
	 * no tags, just put out a message.
	 */
	private void initLayout(ArrayList<Tag> showTags) {
		if (showTags.isEmpty()) {
			showEmptyLabel();
		}
		else {
			for (int i = 0; i < showTags.size(); i++) {
				addTag(showTags.get(i));
			}
		}
	}

	@Override
	public void addTagButton(TagListButton tagButton) {
		removeEmptyLabel();
        add(tagButton);
        invalidateLayout();		
	}

	@Override
	public void removeTagButton(TagListButton tagButton) {
        removeChild(tagButton);
        if (getNumChildren() == 0) {
        	showEmptyLabel();
        }
        invalidateLayout();		
	}

	@Override
	public void addTag(Tag tag) {
		TagListButton tagButton = new TagListButton(tag);
        tagButton.setTheme(getButtonTheme());
        Runnable callback = makeTagButtonClickedCallback(tagButton, tag);
        if (callback != null) {
        	tagButton.addCallback(callback);
        }
        
        addTagButton(tagButton);
	}

	@Override
	public void tagChanged(Tag tag) {
		for (int i = 0; i < getNumChildren(); i++) {
			Widget child = getChild(i);
			if (child instanceof TagListButton) {
				if (((TagListButton)child).getTag().getKey().equals(tag.getKey())) {
					// name changed
					((TagListButton) child).setText(tag.getName());
					break;
				}
			}
		}
	}

	@Override
	public void tagRemoved(Tag tag) {
		for (int i = 0; i < getNumChildren(); i++) {
			Widget child = getChild(i);
			if (child instanceof TagListButton) {
				if (((TagListButton)child).getTag().getKey().equals(tag.getKey())) {
					// deleted
					removeTagButton((TagListButton) child);
					break;
				}
			}
		}
	}

}
