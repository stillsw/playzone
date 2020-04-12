package tryout.fragmentssingleactivity;

import tryout.fragmentssingleactivity.CustomManagedFragmentHolder.CustomManagedFragmentParentActivity;
import tryout.fragmentssingleactivity.animation.DropBackSlideInRightFragmentAnimator;
import tryout.fragmentssingleactivity.animation.FragmentTransitionAnimator;
import tryout.fragmentssingleactivity.animation.FragmentTransitionAnimator.GoDirectlyToFinishParam;
import tryout.fragmentssingleactivity.animation.FragmentTransitionAnimator.PickUpFromParam;
import android.annotation.TargetApi;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * // attempt to fix errors which only happen with the support library version
 * // happens on all emulators I can get to work with orientation switching,
 * plus on nexus 7 real device (4.4.2) // 1) begin portrait mode // 2) switch to
 * landscape -> throws error // doesn't happen if begin in landscape mode, then
 * can switch back and forth any amount and no problems
 * 
 * The fix attempt here is to remove xml fragment layouts, and have framelayout
 * containers for them instead so doing it all programatically here instead
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MainActivity extends FragmentActivity implements
		CustomManagedFragmentParentActivity {

	private static final String TAG = "tryout-fragsTest-main";

	// DP constants, scaled by density (160 is 1" mdpi)
	private static final int TOUCH_MARGIN_TO_LEFT_EDGE_DP = 40; // quarter inch

	private static final String BLUE_TAG = "blue-frag";
	private static final String RED_TAG = "red-frag";
	private static final int INVALID_POINTER_ID = -1;

	private CustomManagedFragmentHolder mCustomManagedFragmentHolder;
	private BlueRedFragment mBlueFragment;
	private BlueRedFragment mRedFragment;
	private String mBlueColour;
	private String mRedColour;
	private GestureDetector mGestureDetector;
	private int mMotionPointerId = INVALID_POINTER_ID;
	private float mLastTouchY;
	private float mLastTouchX;
	private float mTouchRelativeToViewLeft;
	private boolean mIsAnimatingToStartPos = false;
	private RelativeLayout mTrackedView;
	private float mTrackedOriginX;
	private boolean mIsSinglePane;
	private float mTouchMarginToTheLeft;

	private FragmentTransitionAnimator mOriginAnimator;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// colours defined in XML set them up first
		getColourParamsFromResources();

		FragmentManager fragmentManager = getSupportFragmentManager();

		// initialise the custom managed fragment holder (retained fragment)
		initNonUIDataFragment(fragmentManager);

		// add blue first always.. show a navigation button for this one only
		// (to go to the red pane)
		mIsSinglePane = findViewById(R.id.fragmentcontainer) != null;
		mBlueFragment = BlueRedFragment.newInstance(mBlueColour, mIsSinglePane);

		if (mIsSinglePane) {
			// show blue (parent)
			// if red (child) is required, it will be added in onResume()

			fragmentManager.beginTransaction()
					.replace(R.id.fragmentcontainer, mBlueFragment, BLUE_TAG)
					.commit();

		} else {
			// need both fragments
			mRedFragment = BlueRedFragment.newInstance(mRedColour, false);

			// auto added in the layout with id
			fragmentManager.beginTransaction()
					.replace(R.id.bluefragment, mBlueFragment, BLUE_TAG)
					.replace(R.id.redfragment, mRedFragment, RED_TAG).commit();

			// a complication is that if moving from single (port) to dual pane
			// and red pane (child:right) was added to the stack, if hit back it
			// returns to another instance
			// which is incorrect, so have to remove back stack
			mCustomManagedFragmentHolder.clearBackStack(fragmentManager);
		}

	}

	
	@Override
	protected void onResume() {
		super.onResume();
		
		// detect need to have red fragment in front, but it's not there yet (first resume)
		if (mIsSinglePane && mRedFragment == null && mCustomManagedFragmentHolder.isStackedInFront(RED_TAG)) { 
			goForwards(true);
		}
	}


	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		
		// fling and dragging can only be used on the red fragment in single pane layout
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			initTouchCapabilities();
		}		
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void initTouchCapabilities() {
		
		// called from onWindowFocusChanged, only want to do this once
		// need to find an anchor point for the origin in case of panel dragging
		// use red if there and if not then blue
		if (mIsSinglePane && mGestureDetector == null) { 
			// VERY likely this is always 0
			mTrackedOriginX = mBlueFragment
									.getView().findViewById(R.id.top_relative_layout).getX();

			// need a margin to the left of touches to the edge of the view being dragged
			// make it about 1/4 inch (40px @ 160dpi)
			mTouchMarginToTheLeft = getResources().getDisplayMetrics().density * TOUCH_MARGIN_TO_LEFT_EDGE_DP;
			
			mGestureDetector = new GestureDetector(this,
					new GestureDetector.SimpleOnGestureListener() {
						@Override
						public boolean onFling(MotionEvent e1, MotionEvent e2,
								float velocityX, float velocityY) {
							// seems to work both ways, detect which way the
							// movement was
							if (velocityX > 10.0f && e1.getX() < e2.getX()) {
								// method checks red is in front
								popStackedFragment(true);
								return true;
							}
							return false;
						}
					});
		}
	} 

	private void initNonUIDataFragment(FragmentManager fragmentManager) {
		mCustomManagedFragmentHolder = (CustomManagedFragmentHolder) fragmentManager
				.findFragmentByTag(CustomManagedFragmentHolder.NON_UI_FRAGMENT_TAG);

		if (mCustomManagedFragmentHolder == null) {
			// create a data holder for the two instances
			mCustomManagedFragmentHolder = new CustomManagedFragmentHolder();

			// and register the tags that will be used for the fragments
			mCustomManagedFragmentHolder.addFragment(BLUE_TAG,
					CustomManagedFragmentHolder.RULE_NO_STACKING, null);
			mCustomManagedFragmentHolder.addFragment(RED_TAG,
					CustomManagedFragmentHolder.RULE_STACKS_IN_PORTRAIT_ONLY,
					DropBackSlideInRightFragmentAnimator.class);

			fragmentManager
					.beginTransaction()
					.add(mCustomManagedFragmentHolder,
							CustomManagedFragmentHolder.NON_UI_FRAGMENT_TAG)
					.commit();
		} else {
			Toast.makeText(
					this,
					"found stored data, last blue="
							+ mCustomManagedFragmentHolder
									.getLastUpdated(BLUE_TAG)
							+ " red="
							+ mCustomManagedFragmentHolder
									.getLastUpdated(RED_TAG),
					Toast.LENGTH_SHORT).show();
		}
	}

	private void getColourParamsFromResources() {
		Resources res = getResources();
		String[] colours = res.getStringArray(R.array.blue_red_colours);
		mBlueColour = colours[res.getInteger(R.integer.blueIdx)];
		mRedColour = colours[res.getInteger(R.integer.redIdx)];
	}

	/**
	 * Called when the navigation button is pressed, only visible in single pane
	 * mode on the blue panel.
	 * 
	 * @param view
	 */
	public void goForwards(View view) {
		// touch the tag
		updatedAt(RED_TAG);
		goForwards(false);
	}

	/**
	 * Called from onResume() when need to have the red fragment on top
	 * @param view
	 * @param jumpToEndOfAnimation
	 */
	private void goForwards(boolean jumpToEndOfAnimation) {

		// create the fragment
		mRedFragment = BlueRedFragment.newInstance(mRedColour, false);

		// push it to the front using the animator defined
		mCustomManagedFragmentHolder.pushFragment(RED_TAG, this, mRedFragment,
				mBlueFragment, R.id.fragmentcontainer,
				FragmentTransitionAnimator.BEHAVIOUR_ADD
				, jumpToEndOfAnimation ? new GoDirectlyToFinishParam() : null);

	}

	/**
	 * Called when nothing below in the hierarchy has reacted to the onTouch
	 * event
	 */
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			return handleDragOnTouch(ev);
		}
		else {
			return false;
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private boolean handleDragOnTouch(MotionEvent ev) {

		// fling to right detected first, if it handles it nothing else to do
		if (mGestureDetector != null && mGestureDetector.onTouchEvent(ev)) {
			stopDragging();
			return true;
		}

		// only if top fragment is red, if null can get out quick 
		if (mRedFragment == null) {
			return false;
		}
		
		// only if action down, but also red in front
		newDragSetup(ev);

		// didn't result in tracking pointer id, nothing to do
		if (mMotionPointerId == INVALID_POINTER_ID) {
			return false;
		}

		// all set up
		switch (ev.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_MOVE: {
			makeViewFollowTouch(ev);
			break;
		}
		
		// the next 3 conditions could all wind up being the same thing - end of drag
		// excepting another pointer being the one that's lifted, in which case that's
		// ignored
	    case MotionEvent.ACTION_POINTER_UP: {
	        // Extract the index of the pointer that left the touch sensor
	        final int pointerIndex = (ev.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) 
	                >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
	        final int pointerId = ev.getPointerId(pointerIndex);
	        
	        // ignore it as it wasn't the pointer that started the drag
	        if (pointerId != mMotionPointerId) {
		        return false;
	        }
	        
	        // fall through since it's the same as UP/CANCEL now - end of drag
	    }
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			stopDragging();

			// return the view to its normal position, or pop it off
			animateToOriginOrPop();			
			break;
		}

		return true;
	}

	private void makeViewFollowTouch(MotionEvent ev) {
		// Find the index of the active pointer and fetch its position
		final int pointerIndex = ev.findPointerIndex(mMotionPointerId);
		mLastTouchX = ev.getX(pointerIndex);
		mLastTouchY = ev.getY(pointerIndex);

		// the position of the red fragment (actually its view)
		// should be relative to the touch as it moves
		// and keep its distance from the first touch unless the movement
		// goes to the left beyond the x=0 point (the view doesn't follow
		// that far)
		
		// update relation to left edge if needed (ie. if less now, take that)
		mTouchRelativeToViewLeft = Math.max(Math.min(mTouchRelativeToViewLeft, mLastTouchX)
				, mTouchMarginToTheLeft);
		
		// determine the desired x position of the view that tracks the touch
		// should never go further left than the base edge, here that's 0
		// in a more complex layout it would be the origin of fragment in the whole
		// window (we're tracking the visible RelativeLayout)
		
		// also never let the view overtake the touch, there should always be 
		// a small margin between the left edge and the point of contact
		float desiredX = Math.max(mTrackedOriginX, mLastTouchX - mTouchRelativeToViewLeft);
		
		if (Float.compare(desiredX, mTrackedOriginX) != 0) {
			// move the view to the new position
//			Log.d(TAG, String.format("move from %s to %s (origin=%s) touch=%s", mTrackedView.getX(), desiredX, mTrackedOriginX, mLastTouchX));
			mTrackedView.setX(desiredX);
			mTrackedView.invalidate();
		}
	}

	private void newDragSetup(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN && mCustomManagedFragmentHolder.isStackedInFront(RED_TAG)) {
			// is there already an animation running? if so, make sure it gets stopped
			if (mOriginAnimator != null) {
				mOriginAnimator.stopAnimation();
				mOriginAnimator = null;
			}
			
			mLastTouchX = ev.getX();
			mLastTouchY = ev.getY();
			
			mTrackedView = (RelativeLayout) mRedFragment.getView().findViewById(R.id.top_relative_layout);

			// it's possible red view isn't at the origin (could be moved/moving already)
			float viewDistFromOrigin = mTrackedView.getX() - mTrackedOriginX;
			float touchDistFromOrigin = mLastTouchX - mTrackedOriginX;
			mTouchRelativeToViewLeft = touchDistFromOrigin - viewDistFromOrigin;
			
			// don't continue unless the touch is to the right of the left edge of the view
			if (Float.compare(mTouchRelativeToViewLeft, 0f) >= 0) {
				mMotionPointerId = ev.getPointerId(0);
			}
		}
	}

	private void animateToOriginOrPop() {

		// fling was already detected at the start of the touch handling, so only
		// left to decide how to handle end drag here
		
		// which way to go, nearer left edge go back to origin, nearer right
		// pop the fragment off
		if (Float.compare(mTrackedView.getX(), mTrackedView.getWidth() / 2.0f) > 0) {
			// right of centre, pop
			popStackedFragment(true);
		}
		else {
			// left, return to origin
			// use the normal call to the animator, just supply a pickup from param
			mOriginAnimator = mCustomManagedFragmentHolder.pushFragment(RED_TAG, this, mRedFragment,
					mBlueFragment, R.id.fragmentcontainer,
					FragmentTransitionAnimator.BEHAVIOUR_ADD
					// pass a param that also allows interruption of the animation
					, new PickUpFromParam(FragmentTransitionAnimator.PHASE_2, true)); 
		}
	}

	private void stopDragging() {
		mMotionPointerId = INVALID_POINTER_ID;
	}

	/**
	 * If user presses back button and is in red (right : detail) fragment would
	 * not want to go back to that fragment next time around, it's effectively
	 * dismissed, so reset its timing Any other time back button is pressed
	 * would be exiting the app anyway so doesn't matter
	 */
	@Override
	public void onBackPressed() {
		if (!popStackedFragment(false)) {
			super.onBackPressed();
		}
	}

	protected boolean popStackedFragment(boolean isPartial) {
		// simplest (but also could check with the fragment holder by
		// isStackedInFront()
		if (mIsSinglePane && mRedFragment != null) {
			
			boolean success = (mCustomManagedFragmentHolder.popFragment(RED_TAG,
					this, mBlueFragment, mRedFragment, R.id.fragmentcontainer
					// could be partial if dragged/flung 
					, isPartial ? new PickUpFromParam(FragmentTransitionAnimator.PHASE_1, false) : null
					) != null);
			
			if (success) {
				resetTag(RED_TAG);
				mRedFragment = null;

				return true; // bypass super
			}
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	// ------------------ CustomManagedFragmentParentActivity methods

	@Override
	public void updatedAt(String tag, long time) {
		mCustomManagedFragmentHolder.setLastUpdated(tag, time);
	}

	@Override
	public void updatedAt(String tag) {
		mCustomManagedFragmentHolder.setLastUpdated(tag);
	}

	@Override
	public long getLastUpdated(String tag) {
		return mCustomManagedFragmentHolder.getLastUpdated(tag);
	}

	@Override
	public void resetTag(String tag) {
		mCustomManagedFragmentHolder.resetTag(tag);
	}

}
