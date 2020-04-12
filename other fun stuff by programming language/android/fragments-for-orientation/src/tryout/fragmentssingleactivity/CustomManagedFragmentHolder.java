package tryout.fragmentssingleactivity;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import tryout.fragmentssingleactivity.animation.FragmentTransitionAnimator;
import tryout.fragmentssingleactivity.animation.FragmentTransitionAnimator.AnimationParam;
import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;

/**
 * Keeps track of fragments that are registered with it by an Activity to :
 * 1) Enable orientation shifts in a way that supports fragments that are stand alone
 *    in one orientation and not in another (typically, small screen layouts that
 *    are single pane in portrait but dual pane in landscape)
 * 2) Allow multiple fragments to be managed, and also any depth of stacking (in fragment
 *    transaction back stack)
 * 3) Plug in support for animated transitions between fragments that merge with the above
 *    functionality. In other words if a fragment is added on top of another in portrait
 *    it can be popped off the stack with an animation in portrait only, in landscape
 *    it has no back stack and there is no animated behaviour at all.
 * Note: not all fragments that are used in the Activity have to be managed here (in principal
 * at least).
 * 
 * The class could be extended to include data state between orientation changes. 
 */
// Suppresses lint warning, because it's not a UI fragment and won't be instantiated
// by android.
@SuppressLint("ValidFragment")
public class CustomManagedFragmentHolder extends Fragment {
	
	public static final String NON_UI_FRAGMENT_TAG = "tryout-CustomManagedFragmentHolder";
	
	// stacking rules (by fragment, in meta data)
	public static final int RULE_STACKS_IN_PORTRAIT_ONLY = 0;
	public static final int RULE_STACKS_IN_LANDSCAPE_ONLY = 1;
	public static final int RULE_STACKS_ALWAYS = 2; // regardless of orientation
	public static final int RULE_NO_STACKING = 3; // probably base activity fragment 

	// constants for direction of movement
	private static final int TO_LEFT_OR_UP = 0; // use to indicate movement towards zero
	private static final int TO_RIGHT_OR_DOWN = 1; // use to indicate movement away from zero
	
	interface CustomManagedFragmentParentActivity {
		public void updatedAt(String tag);
		public void updatedAt(String tag, long time);
		public long getLastUpdated(String tag);
		public void resetTag(String tag);
	}
	
	// the mappings of fragments with meta data for each
	private HashMap<String, FragmentMetaData> mFragmentMetaDatas;

	/**
	 * Constructor to prepare for managing any number of fragments
	 */
	CustomManagedFragmentHolder() {
		mFragmentMetaDatas = new HashMap<String, FragmentMetaData>();
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	/**
	 * Typically called during Activity.onCreate() to ensure the correct fragment is shown according to 
	 * the rules defined and whether any stacking fragments have previously been set as last updated fragment.
	 * 
	 * This method should be called AFTER fragment meta data are added with addFragment().
	 * 
	 * Currently, the most basic implementation:
	 * 1) if any stacking fragment is the last updated, and it stacks in the current orientation returns true
	 * 2) no support for >1 stacking fragments so far
	 * 
	 * @param tag
	 * @return true if the fragment was found and should be pushed to the front
	 */
	boolean isStackedInFront(String tag) {
		
		// find the last acted on fragment
		FragmentMetaData lastFmd = null;
		long lastActionTime = Long.MIN_VALUE;
		
		for (Map.Entry<String, FragmentMetaData> entry : mFragmentMetaDatas.entrySet()) {
			if (entry.getValue().mLastUpdated > lastActionTime) {
				lastFmd = entry.getValue();
				lastActionTime = lastFmd.mLastUpdated;
			}
		}

		if (lastFmd != null) {
			
			// found one, so now have to see if it stacks in the current orientation
			return lastFmd.mRule == RULE_STACKS_ALWAYS
					|| (lastFmd.mRule == RULE_STACKS_IN_PORTRAIT_ONLY 
						&& getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
					|| (lastFmd.mRule == RULE_STACKS_IN_LANDSCAPE_ONLY 
						&& getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
		}
		
		return false;
	}
	
	/**
	 * Called from the Activity.onCreate() when the back stack might include extra entries that no longer apply
	 * and they need to be removed.
	 * For instance, in portrait mode a stacked fragment was applied, and now changed to landscape it is not
	 * stacked anymore but incorporated in the standard layout. In this case there will be a back stack entry from
	 * the previous portrait layout that must be removed to prevent a back key showing a spurious screen.
	 * 
	 * Notes:
	 * 1) Currently only the basic implementation where a single layered fragment may be added to a layout at a time.
	 * 2) This should be revisited if necessary to add support for multiple fragments.
	 * 3) Ad-hoc implementations can always implement more complex scenarios manually and still use this class for
	 *    it's management benefits. In such cases clearing the back stack would be done by the Activity and this
	 *    method would not be used.
	 * 
	 * @param fragmentManager
	 */
	void clearBackStack(FragmentManager fragmentManager) {
		int bs = fragmentManager.getBackStackEntryCount();
		if (bs > 0) {
			for (int i = bs - 1; i >= 0; i--) {
				Log.d(NON_UI_FRAGMENT_TAG, String.format("backstack entries=%s pop=%s"
						, fragmentManager.getBackStackEntryCount()
						, fragmentManager.popBackStackImmediate()));
			}
		}
	}

	/**
	 * Add the fragment identified by its tag (ie. must have one) to the mappings being managed.
	 * Note, the fragment is not stored, only its data is used.
	 * If a mapping already exists for the Tag, the mapping is recycled with the new data.
	 * @param tag
	 * @param rule One of the constants of this class
	 * @param transitionAnimatorClass the class to instantiate if/when needed
	 */
	void addFragment(String tag, int rule, Class<? extends FragmentTransitionAnimator> transitionAnimatorClass) {
//		String tag = fragment.getTag();
		if (tag == null) {
			throw new IllegalArgumentException("Fragment must have a tag set");
		}
		
		FragmentMetaData fmd = mFragmentMetaDatas.get(tag);
		if (fmd == null) {
			fmd = new FragmentMetaData(tag, rule, transitionAnimatorClass);
			mFragmentMetaDatas.put(tag, fmd);
		}
		
		fmd.mRule = rule;
	}

	/**
	 * Called by the Activity when it wants to bring a fragment to the front. 
	 * No checking is made that it conforms to any rules, just find the animator from the meta data and 
	 * attempt to open it
	 * @param tag
	 * @param activity
	 * @param fragmentIn
	 * @param fragmentOut
	 * @param resId
	 * @param behaviour
	 * @param params optional params
	 * @return the animator, in case the caller needs it to interrupt an animation 
	 */
	FragmentTransitionAnimator pushFragment(String tag, FragmentActivity activity, Fragment fragmentIn, Fragment fragmentOut, int resId, int behaviour, AnimationParam ... params) {
		FragmentMetaData fmd = mFragmentMetaDatas.get(tag);
		if (fmd != null && fmd.mTransitionAnimatorClass != null) {
			Constructor<?> c = fmd.mTransitionAnimatorClass.getConstructors()[0];
			try {
				FragmentTransitionAnimator animator = (FragmentTransitionAnimator) c.newInstance();
				animator.animateIn(activity, fragmentIn, fragmentOut, resId, behaviour, tag, params);
				return animator;
				
			} catch (Exception e) {
				Log.e(NON_UI_FRAGMENT_TAG, "pushFragment: failed to instantiate and run animator", e);
			}
		}
		else {
			Log.w(NON_UI_FRAGMENT_TAG, String.format("pushFragment: no mapping for the tag=%s or no animator supplied", tag));
		}
		
		return null;
	}
	
	/**
	 * Called by the Activity when it wants to remove the fragment in front. 
	 * No checking is made that it conforms to any rules, just find the animator from the meta data and 
	 * attempt to close it
	 * @param tag
	 * @param activity
	 * @param fragmentIn
	 * @param fragmentOut
	 * @param resId
	 * @param params optional params
	 * @return the animator, in case the caller needs it to interrupt an animation 
	 */
	FragmentTransitionAnimator popFragment(String tag, FragmentActivity activity, Fragment fragmentIn, Fragment fragmentOut, int resId, AnimationParam ... params) {
		FragmentMetaData fmd = mFragmentMetaDatas.get(tag);
		if (fmd != null && fmd.mTransitionAnimatorClass != null) {
			Constructor<?> c = fmd.mTransitionAnimatorClass.getConstructors()[0];
			try {
				FragmentTransitionAnimator animator = (FragmentTransitionAnimator) c.newInstance();
				animator.animateOut(activity, fragmentIn, fragmentOut, resId, tag, params);
				return animator;
				
			} catch (Exception e) {
				Log.e(NON_UI_FRAGMENT_TAG, "popFragment: failed to instantiate and run animator", e);
			}
		}
		else {
			Log.w(NON_UI_FRAGMENT_TAG, String.format("popFragment: no mapping for the tag=%s or no animator supplied", tag));
		}
		
		return null;
	}
	
	/**
	 * Calculates how long the duration needs to be to complete the movement.
	 * millisPerInch depends on how many inches and how fast want it to travel... decision components:
	 * 1) The animator being used, what it does, how it's supposed to look/behave
	 * 2) The view (activity/fragment) that's using it
	 * 3) The device size
	 * eg. slide in/out animator used by a full width fragment on a 4" wide device
	 *     might decide, animator can get the millisPerInch automatically from a width bucket : eg. values-w600dp/
	 *     BUT what if same animator is being used by partial width fragment? Needs also to be able to
	 *     accept a modulated value from that.
	 * @param direction : One of TO_LEFT_OR_UP or TO_RIGHT_OR_DOWN
	 * @param displacement : Distance away from the usual start point for this movement 
	 * @param totalMovement : Total distance of the movement from the usual start point
	 * @param millisPerInch : How fast want to move independent of density
	 * @param densityScale : proportion relative to 160dpi 
	 * @return
	 */
	//TODO find a need for this method! (not using it for fling, at least not yet)
	static int calcDurationMillis(int direction, float displacement, float totalMovement, float millisPerInch, int densityScale) {
		
		// example : nexus 7 = relative layout = 758 (3.55")
		// 300 millis to travel 3.55" = 300 / 3.55" = 84.5 millisPerInch
		
		// so example aiming for 300ms duration on a nexus 7	: eg. nexus 7
		// totalMovement is a number of px						: w = 758 (size of the relative layout)
		// need number of inches = w / (density*baseline (dpi))	: 758 / 213 = 3.55"
		// completeDurationMillis = in * millisPerInch			: 3.55 * 84.5 = 300 (approx)
		
		float inches = totalMovement / (densityScale * 160);
		float completeDurationMillis = inches   * millisPerInch;
		
		if (Float.compare(displacement, 0f) == 0) {
			// no more calculation needed if the thing is not displaced
			return (int) (completeDurationMillis + 0.5f);
		}
		
		// assume a legal direction (one of the constants here)
		float distToGo = (direction == TO_LEFT_OR_UP ? displacement : totalMovement - displacement);
		float deltaDistance = distToGo / totalMovement;
		return (int) (completeDurationMillis / deltaDistance + 0.5f);
	}
	
	
	/**
	 * Get the last updated time for the fragment identified by the tag
	 * @param tag
	 * @return the time the fragment was last recorded as updated
	 */
	long getLastUpdated(String tag) {
		try {
			return mFragmentMetaDatas.get(tag).mLastUpdated;
		}
		catch (NullPointerException e) {
			// do nothing
			Log.w(NON_UI_FRAGMENT_TAG, "getLastUpdated: no mapping for the tag", e);
			return Long.MIN_VALUE;
		}
	}

	/**
	 * Set the last updated time for the fragment identified by the tag
	 * Version that gets its timing from elapsed real time on the 
	 * system clock
	 * @param tag
	 * @return the time the fragment was last recorded as updated
	 */
	void setLastUpdated(String tag) {
		setLastUpdated(tag, SystemClock.elapsedRealtime());
	}

	/**
	 * Set the last updated time for the fragment identified by the tag
	 * @param tag
	 * @param lastUpdated a timing in long form (arbitrary: could be anything, even simple increment)
	 * @return the time the fragment was last recorded as updated
	 */
	void setLastUpdated(String tag, long lastUpdated) {
		try {
			mFragmentMetaDatas.get(tag).mLastUpdated = lastUpdated;
		}
		catch (NullPointerException e) {
			// do nothing
			Log.w(NON_UI_FRAGMENT_TAG, "setLastUpdated: no mapping for the tag", e);
		}
	}

	/**
	 * Clears whatever the update time was for the fragment and sets it to min value again
	 * @param tag
	 */
	void resetTag(String tag) {
		setLastUpdated(tag, Long.MIN_VALUE);
	}
	
	/**
	 * Data about each fragment and rules for its management
	 * Such as when it should be added on the back stack and when it is 
	 * not (because it is displayed simultaneously with other fragments)
	 */
	static class FragmentMetaData {
		// the tag set on the fragment that identifies it
//		private String mTag;
		private int mRule = Integer.MIN_VALUE;
		private long mLastUpdated = Long.MIN_VALUE;
		private Class<? extends FragmentTransitionAnimator> mTransitionAnimatorClass;
		
		public FragmentMetaData(String tag, int rule, Class<? extends FragmentTransitionAnimator> transitionAnimatorClass) {
//			this.mTag = tag;
			this.mRule = rule;
			this.mTransitionAnimatorClass = transitionAnimatorClass;
		}
	}

}
