package com.stillwindsoftware.keepyabeat.gui;

import com.stillwindsoftware.keepyabeat.control.PendingTask;
import com.stillwindsoftware.keepyabeat.geometry.MutableRectangle;
import com.stillwindsoftware.keepyabeat.platform.twl.TwlResourceManager;
import com.stillwindsoftware.keepyabeat.utils.PixelUtils;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Widget;

/**
 * A widget that slides in/out a selected edge of the screen.
 * Uses pending tasks, either call show/hide or request the pending task that will do that
 * for embedding in other animations.
 * @author Tomas Stubbs
 */
public class SlidingAnimatedPanel extends Widget {

	public static float DEFAULT_ELAPSED_SECONDS_TO_OPEN = 0.2f;
	public static float DEFAULT_ELAPSED_SECONDS_TO_CLOSE = 0.1f;

	public interface SlideStateCallback {
		public void slideStateChanged(boolean show);
	}
	
	public enum Direction {
		DOWNWARDS(0,-1),
		FROM_THE_RIGHT(1,0),
		UPWARDS(0,1),
		FROM_THE_LEFT(-1,0);
        
        final int x;
        final int y;
        Direction(int x, int y) {
            this.x = x;
            this.y = y;
        }
    };
    
    // unless set by a calling class, the name is panel
//    private String name = PlatformResourceManager.LOCALISED_STRING_PREFIX+"panel";
    private Widget animatedWidget;
	// slide button and position
    private Button slideButton; 
	private int slideBtnX, slideBtnY;
	
    // movement settings
    private Direction direction;
    private int alwaysShowX;
    private int alwaysShowY;
    
    // hibernate/resume 
    private int resumeAlwaysShowX;
    private int resumeAlwaysShowY;
    private boolean resumeHidden = false;
    private boolean startingUpHidden;
    private boolean disableAutoResume = false;
    private boolean isLifeCycleChange = false;
    // maintain a flag of state rather than testing positions, which could be unreliable when size/positions of
    // this panel or animated widget changes
    private boolean isCurrentStateHidden = true;
    // flag indicates a toggle slide task is running and stays true till completed
    private boolean isTogglingNow = false;
    
    // callback to execute every time the animated widget's position changes in sliding in/out
	private Runnable slideCallback;
	// callback to execute once when slide state changes (starts to change)
	// note this callback is not called during hibernate/resume
	private SlideStateCallback slideStateCallback;
    
    /**
     * Create a control button to slide in/out which sits on the edge of the animatedWidget
     */
    public SlidingAnimatedPanel(Widget animatedWidget, Direction direction, boolean startHidden) {
    	this.direction  = direction;
        this.setAnimatedWidget(animatedWidget, startHidden);
    }

    /**
     * Widget is likely to be requested in the hidden state, so it can slide into view
     * @param animatedWidget
     * @param startHidden
     */
    protected void setAnimatedWidget(Widget animatedWidget, boolean startHidden) {
    	if(animatedWidget == null) {
            throw new IllegalArgumentException("animatedWidget");
        }
		this.animatedWidget = animatedWidget;
    	this.startingUpHidden = startHidden;

        setClip(true);
        add(animatedWidget);
    }

    /**
     * Add a button to open/close at the specified location. Negative numbers mean from the 
     * opposite edge. eg. x -10 means 10 left from the right edge. Make sure to call
     * this after setting the animated widget so that it lies on top
     * @param buttonTheme
     * @param x
     * @param y
     */
    public void addSlideButton(String buttonTheme, int x, int y) {
    	slideButton = new Button();
    	slideButton.setTheme(buttonTheme);
    	slideButton.setCanAcceptKeyboardFocus(false); // don't want it to have that selected box on clicking
    	slideButton.addCallback(new Runnable() {
				@Override
				public void run() {
					setShow(isHidden());
				}			
			});
    	slideBtnX = x;
    	slideBtnY = y;
    	add(slideButton);
    	slideButton.setVisible(false); // start invisible, shows when fully open 
    }
    
    /**
     * Player group uses this to get a help target
     * @return
     */
    public Button getSlideButton() {
		return slideButton;
	}

	/**
     * Set a callback that will be executed on every movement of the panel's animated widget
     * (see ToggleSlidingPanelTask below)
     * @param callback
     */
    public void setSlideCallback(Runnable callback) {
    	this.slideCallback = callback;
    }
    
    public void setSlideStateChangedCallback(SlideStateCallback slideStateCallback) {
    	this.slideStateCallback = slideStateCallback;
    }
    
	/**
	 * Uses pending tasks to show, if want to chain another task, don't use this
	 * but instead grab the task and set a next task on it. See shrinkAndWrap() below
	 * it does exactly that.
	 * @param show
	 */
	public void setShow(boolean show) {		
//if (debug) {
//	System.out.println("SlidingAnimatedPanel.setShow#1: show="+show);
//	try {
//		throw new Exception();
//	} catch (Exception e) {
//		
//		e.printStackTrace();
//	}
//}
		TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(getPendingTaskToToggleShow(show));
    }

	/**
	 * Overloaded version that allows appending of another task to the task to show/hide
	 * @param show
	 * @param nextTask
	 */
	public void setShow(boolean show, PendingTask nextTask) {
//if (debug) {
//	System.out.println("SlidingAnimatedPanel.setShow#2: show="+show);
//}
		ToggleSlidingPanelPendingTask toggleTask = getPendingTaskToToggleShow(show);
		toggleTask.appendNextTask(nextTask);
		TwlResourceManager.getInstance().getPendingTasksScheduler().addTask(toggleTask);
	}
	
	/**
	 * Gets the task that can show/hide the panel, for chaining tasks together.
	 * Otherwise just use setShow()
	 * @param show
	 * @return
	 */
    ToggleSlidingPanelPendingTask getPendingTaskToToggleShow(boolean show) {
    	return new ToggleSlidingPanelPendingTask(show);
    }

    public Direction getHideDirection() {
        return direction;
    }

    public int getAlwaysShowX() {
		return alwaysShowX;
	}

	public void setAlwaysShowX(int alwaysShowX) {
		this.alwaysShowX = alwaysShowX;
	}

	public int getAlwaysShowY() {
		return alwaysShowY;
	}

	public void setAlwaysShowY(int alwaysShowY) {
		this.alwaysShowY = alwaysShowY;
	}

	/**
	 * When it's set to invisible always return true, otherwise determine its position to see if it's hidden
	 * @return
	 */
	boolean isHidden() {
		if (isVisible()) {
	        final int x = animatedWidget.getX();
	        final int y = animatedWidget.getY();
	        return (x == getInnerX() + direction.x*(animatedWidget.getWidth()-alwaysShowX) &&
	                y == getInnerY() + direction.y*(animatedWidget.getHeight()-alwaysShowY))
	                || startingUpHidden; // before first paint if set to start hidden this will be true
		}
		else {
			return true;
		}
    }

    @Override
    public int getMinWidth() {
        return animatedWidget.getMinWidth();
    }

    /**
     * The limit to the height is not to go under the module bar
     */
    @Override
    public int getMinHeight() {
        return animatedWidget.getMinHeight();
    }

    @Override
    public int getPreferredInnerWidth() {
        return animatedWidget.getPreferredWidth();
    }

    @Override
    public int getPreferredInnerHeight() {
        return animatedWidget.getPreferredHeight();
    }
    
//private boolean debug = false;    
    @Override
    protected void layout() {
//debug = (animatedWidget instanceof PlayerRhythmBeatTypesList); 
//if (debug) {
//	if (getWidth() != animatedWidget.getWidth() || getHeight() != animatedWidget.getHeight())
//		System.out.println("SAP.layout: startingUpHidden="+startingUpHidden+" w/h="+getWidth()+"/"+getHeight()+
//			" widget w/h="+animatedWidget.getWidth()+"/"+animatedWidget.getHeight()+" isHidden="+isHidden()+" currentStateHidden="+this.isCurrentStateHidden());
//}
    	
    	setSize(animatedWidget.getWidth(), animatedWidget.getHeight());
    	
    	if (slideButton != null) {
	    	slideButton.setSize(slideButton.getMaxWidth(), slideButton.getMaxHeight());
	    	int x = getX() + (slideBtnX >= 0 ? slideBtnX : getWidth() - slideButton.getWidth() + slideBtnX);
	    	int y = getY() + (slideBtnY >= 0 ? slideBtnY : getHeight() - slideButton.getHeight() + slideBtnY);
	    	
	    	slideButton.setPosition(x, y);
    	}
    	
    	// change of horizontal size is happening on paintWidget() so don't want layout() to be determining
    	// the size of the widget, it's late that way
//        animatedWidget.setSize(getInnerWidth(), getInnerHeight());

    	// first time and once only 
    	// begin hidden if animatedWidget was set that way (usual)
    	// call resume() from here, provided not resuming hidden, and this hasn't been disabled (for manual reasons)
    	if (startingUpHidden && animatedWidget.getWidth() != 0 && animatedWidget.getHeight() != 0) {
//if (debug) { 
//	System.out.println("SAP.layout: putting startup hidden for PlayerRhythmBeatTypesList to hidden place "
//			+" xy="+(animatedWidget.getX() + direction.x*(animatedWidget.getWidth()))
//			+","+ (animatedWidget.getY() + direction.y*(animatedWidget.getHeight())));
//}
			// standard list has a method to indicate table width is fully calculated 
			if ((animatedWidget instanceof StandardTableList 
					&& ((StandardTableList)animatedWidget).isLayoutInitComplete())
				|| !(animatedWidget instanceof StandardTableList)) {

				startingUpHidden = false;

				// hide it
				setAnimatedWidgetToHiddenPosition();
				
				// check it is supposed to show
				if (!disableAutoResume && !resumeHidden) {
//if (debug) 
//	System.out.println("\tdoing auto resume for panel, widget="+animatedWidget);				
					resume();
				}
//				else {
//if (debug) 
//	System.out.println("\tauto resume CANCELLED for panel, widget="+animatedWidget);				
//				}

			}
			
    	}            
    }

    /**
     * Called from layout() during the startingUpHidden phase (ie. preliminary layout)
     * And also maybe called when a hidden widget resizes, perhaps when its model changes
     * and then needs to be placed exactly into its proper hidden spot again
     * (see PlayerGroup.sizeAndPlaceSettingsList())
     */
    public void setAnimatedWidgetToHiddenPosition() {
		animatedWidget.setPosition(getX() + direction.x*(animatedWidget.getWidth()), getY() + direction.y*(animatedWidget.getHeight()));
    }
    
    /**
     * Called when a widget resizes, perhaps when its model changes
     * and then needs to be placed exactly into its proper open spot again
     * (see PlayerGroup.sizeAndPlaceSettingsList())
     */
    public void setAnimatedWidgetToOpenPosition() {
		animatedWidget.setPosition(getX(), getY());
    }
    
//    @Override
//    protected void positionChanged() {
////		setRectSize();
//    }

//    private void changeHorizontalSize() {    
//        int x = getInnerX();
//        int w = getInnerWidth();
//        int xChange = 0;
//        int wChange = 0;
//        boolean shrinking = false;
//        
//        if (targetW != w) {
//        	if (targetW > w) 
//        		wChange = Math.min(sizeSpeedChange, targetW - w);
//        	else {
//        		shrinking = true;
//        		wChange = -Math.min(sizeSpeedChange, w - targetW);
//        	}
//        }
//        
//        if (targetX != x) {
//        	if (targetX > x)
//        		xChange = Math.abs(wChange);
//        	else
//        		xChange = -Math.abs(wChange);       	
//        }
//        
//        if (xChange != 0)
//        	setPosition(x + xChange, getInnerY());
//    	
//        if (wChange != 0)
//        	setSize(w + wChange, getInnerHeight());
//
//        animatedWidget.setSize(animatedWidget.getWidth() + wChange, getInnerHeight());
//        // try to adjust size of one child too, particularly the table that's a couple of layers down
//        Widget child = animatedWidget.getChild(0);
//        if (child != null && child instanceof ScrollPane) {
//	        child.setSize(child.getWidth() + wChange, child.getHeight());
//	        child = child.getChild(0);
//	        if (child != null) {
//		        child.setSize(child.getWidth() + wChange, child.getHeight());
//		        child = child.getChild(0);
//		        if (child != null && child instanceof Table) {
//			        child.setSize(child.getWidth() + wChange, child.getHeight());
//		        }
//	        }
//        }
//        setRectSize();
//        
//    	changeHorizontally = !(targetX == getInnerX() && targetW == getInnerWidth());
//    	if (!changeHorizontally) {
//    		if (shrinking && fillStateShrunkCallback != null)
//    			fillStateShrunkCallback.run();
//    		else if (!shrinking && fillStateFilledCallback != null)
//    			fillStateFilledCallback.run();
//    	}
//    }
    
//    /**
//     * Put the animatedWidget in the same relationship as is prior to setting, for when a sliding panel
//     * is itself moving for some reason
//     */
//    @Override
//	public boolean setPosition(int x, int y) {
////    	int diffX = getX() - animatedWidget.getX();
////    	int diffY = getY() - animatedWidget.getY();
////    	animatedWidget.setPosition(x - diffX, y - diffY);
//		return super.setPosition(x, y);
//	}

	public int getHiddenHeight() {
    	return (animatedWidget.getY() <= getY() ? getY() - animatedWidget.getY() : animatedWidget.getY() - getY());
    }
    
    public boolean isStartingUpHidden() {
		return startingUpHidden;
	}

    /**
     * Resume is automatic from the layout method if startingUpHidden is set, unless this is set to true
     * @param disableAutoResume
     */
    public void setDisableAutoResume(boolean disableAutoResume) {
		this.disableAutoResume = disableAutoResume;
	}

	/**
     * Fully Open means it's in the place that it would be when opening is complete, but when resuming from scratch 
     * (ie. first time) the widget hasn't yet been placed into the closed place so the following condition would
     * return true unless a check is also made for the startingUpHidden flag. This is only unset on the first
     * layout after the widget has been put into the hidden position.
     * @return
     */
	boolean isFullyOpen() {
    	boolean isFullyOpen = animatedWidget.getX() == getX() && animatedWidget.getY() == getY() && !startingUpHidden;
    	return isFullyOpen;
    }
    
	/**
	 * State is set when setShow() task completes, clients should use this method to determine the
	 * state of the widget
	 * @return
	 */
    public boolean isCurrentStateHidden() {
		return isCurrentStateHidden;
	}

    /**
     * Set by a running task that is changing the open state of the panel, unset when complete.
     * Use this to test when need to know if there's been a request to change state but it hasn't yet completed. 
     * @return
     */
	public boolean isTogglingNow() {
		return isTogglingNow;
	}

	/**
     * Cause the panel to completely disappear (it wouldn't if alwaysShow is set to non 0)
     * @param closeIt true if want to hide it now, false if just setting it up (see shrinkAndWrap)
     */
    public void hibernate() {
    	hibernate(null);
    }
        
    public void hibernate(PendingTask nextTask) {
    	isLifeCycleChange = true;
        resumeAlwaysShowX = alwaysShowX;
        resumeAlwaysShowY = alwaysShowY;
        resumeHidden = isHidden();
    	
        alwaysShowX = 0;
        alwaysShowY = 0;
        
    	if (nextTask == null) {
        	setShow(false);
    	}
    	else {
    		setShow(false, nextTask);
    	}
    }
        
	/**
     * More severe than hibernate, but similarly needs to disappear elegantly
     * Provides callback to do something when finished
     */
    public void shrinkAndWrap(final Runnable callback) {

		if (!isHidden()) {
			if (callback == null) {
				hibernate();
			}
			else {
				hibernate(new PendingTask("runcallback") {
						@Override
						protected void startTask() {
							callback.run();
						}
					});
			}			
		}
		else if (callback != null) {
			// just run the callback
			callback.run();
		}
    }
    
    /**
     * Resume from hibernation state
     */
    public void resume() {
    	resume(null);
    }
    
    /**
     * Resume from hibernation state, then run the next task
     */
    public void resume(PendingTask nextTask) {
    	alwaysShowX = resumeAlwaysShowX;
    	alwaysShowY = resumeAlwaysShowY;
    	
    	if (!resumeHidden) {
        	isLifeCycleChange = true;
    		if (nextTask == null) {
    			setShow(true);
    		}
    		else {
    			setShow(true, nextTask);
    		}
    	}
    }
    
    /**
     * Resume means to put back in the state it was at hibernation, so it's complete if it was hidden
     * and is now, or was open and is now fully open again
     * @return
     */
    public boolean isResumeComplete() {
    	return (isHidden() && (resumeHidden || isStartingUpHidden())) || (!resumeHidden && isFullyOpen());
    }
    
    public boolean isResumeHidden() {
		return resumeHidden;
	}

	public void setResumeHidden(boolean resumeHidden) {
		this.resumeHidden = resumeHidden;
	}
	
	/**
	 * Utility method to return the sliding animated panel parent of a widget, if it has one
	 * @param widget
	 * @return
	 */
	public static SlidingAnimatedPanel getSlidingAnimatedPanelParent(Widget widget) {
		// walk up the tree to find it
		Widget parent = widget.getParent();
				
		while (parent != null && !parent.equals(widget.getGUI())) {
			if (parent instanceof SlidingAnimatedPanel) {
				return (SlidingAnimatedPanel)parent;
			}
			parent = parent.getParent();
		}
		
		return null;
	}

	/**
     * A simple task that changes the panel's state. It completes when the change is finished, meaning
     * the animation to the chosen state is done.
     *
     */
    public class ToggleSlidingPanelPendingTask extends PendingTask {

    	private boolean show;

    	// during animation, where it's going
        private MutableRectangle targetDimension;
        private MutableRectangle animationSpeed;
		// distance is the difference between fully open and hidden, but
		// have to also take into account if anything is set for alwaysShow
		// ie. a piece of the widget that is showing even when in hidden state			
		float xDist = animatedWidget.getWidth() - alwaysShowX;
		float yDist = animatedWidget.getHeight() - alwaysShowY;
    	
		public ToggleSlidingPanelPendingTask(boolean show) {
			// identify the panel easier for pending task bugs
			super(String.format("sliding panel (%s) %s"
					, (show ? "open" : "close")
					, SlidingAnimatedPanel.this.getTheme())); 
			this.show = show;
		}
		
		/**
		 * Allow the task to reveal if it is sliding the panel containing the test widget
		 * @param testWidget
		 * @return
		 */
		public boolean isTogglingWidget(Widget testWidget) {
			return testWidget.equals(animatedWidget);
		}
		
//private boolean debug = SlidingAnimatedPanel.this.getTheme().equals("settingsPanel");
//private String what = "PlayerRhythmBeatTypesList";
		/**
		 * Setup the target for movement to show or hide, calculates the distance
		 * then sets up the animation steps needed to get there
		 * Only changing location at this point... size isn't changing
		 */
		@Override
		protected void startTask() {
			if (show && !isVisible()) {
				setVisible(true);
			}
			// set the panel's flag to indicate it's going
			isTogglingNow = true;
			setTargetDimension();
//if (debug) {
//	System.out.println("SlidingAnimatedPanel.toggleTask.startTask. show "+what+", target="+targetDimension
//			+" widgetwidth="+animatedWidget.getWidth()+" startX="+animatedWidget.getX());
//}
			if (show) {
				// speed is distance by seconds and direction dictates +/-
				animationSpeed = new MutableRectangle(
						direction.x * PixelUtils.getStepDistance(TwlResourceManager.getInstance().getGuiManager(), xDist, DEFAULT_ELAPSED_SECONDS_TO_OPEN) *-1, 
						direction.y * PixelUtils.getStepDistance(TwlResourceManager.getInstance().getGuiManager(), yDist, DEFAULT_ELAPSED_SECONDS_TO_OPEN) *-1,
						.0f, .0f);				

			}
			else {
				animationSpeed = new MutableRectangle(
						direction.x * PixelUtils.getStepDistance(TwlResourceManager.getInstance().getGuiManager(), xDist, DEFAULT_ELAPSED_SECONDS_TO_CLOSE), 
						direction.y * PixelUtils.getStepDistance(TwlResourceManager.getInstance().getGuiManager(), yDist, DEFAULT_ELAPSED_SECONDS_TO_CLOSE),
						.0f, .0f);

				if (slideButton != null) {
					slideButton.setVisible(false);
				}
			}
			
			
		}

		private void setTargetDimension() {
			if (show) {
				// target is the open place
				targetDimension = new MutableRectangle(getInnerX(), getInnerY(), 0f, 0f);				
			}
			else {
				// for hiding, target is the hidden place
				targetDimension = new MutableRectangle(getInnerX() + xDist * direction.x, getInnerY() + yDist * direction.y, 0f, 0f);
			}
		}
		
		/**
		 * Animate towards the target
		 */
		@Override
		protected void updateComplete() {
			// need to reset target dimension in case the sliding panel has moved its position
			// eg. playerGroup.topWidgetsPanel moves depending on the rhythmsList position
			setTargetDimension();
			
			// get the new values depending on direction setup
			float newDrawnX = PixelUtils.animateToTargetBySpeed(targetDimension.getX(), animatedWidget.getX(), animationSpeed.getX());
			float newDrawnY = PixelUtils.animateToTargetBySpeed(targetDimension.getY(), animatedWidget.getY(), animationSpeed.getY());

			animatedWidget.setPosition((int)newDrawnX, (int)newDrawnY);
			
			// run the callback if set
			if (slideCallback != null) {
				slideCallback.run();
			}
//if (debug) {
//	System.out.println("update resume task for "+what+", setPos xy="+newDrawnX+","+newDrawnY
//			+"target xy="+targetDimension.getX()+","+targetDimension.getY());
//}
			
			// make sure layout is called again
			animatedWidget.invalidateLayout();
			
			complete = PixelUtils.floatsMatch(newDrawnX, targetDimension.getX())
						&& PixelUtils.floatsMatch(newDrawnY, targetDimension.getY());

			if (complete) {
//if (debug) {
//	System.out.println("update resume task for "+what+", completed");
//}
				// reset flag (set on hibernate or resume) or run any callback
				if (isLifeCycleChange) {		
					isLifeCycleChange = false;
				}
				else if (slideStateCallback != null) {
					slideStateCallback.slideStateChanged(show);
				}
				
				// change the state for external tests (eg. toggle buttons)
				isCurrentStateHidden = !show;
				// it won't be drawn at least if it's not showing
				if (isCurrentStateHidden) {
					setVisible(false);
				}
				isTogglingNow = false;
			}
			
			if (complete && isFullyOpen() && slideButton != null) {
				slideButton.setVisible(true);
			}
		}
		
    }
}
