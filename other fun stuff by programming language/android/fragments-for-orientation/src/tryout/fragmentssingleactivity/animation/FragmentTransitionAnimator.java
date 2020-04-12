package tryout.fragmentssingleactivity.animation;

import tryout.fragmentssingleactivity.R;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

/**
 * Super class for the custom Fragment transitions managed from
 * CustomManagedFragmentHolder. It's possible to use this class to do
 * the transitions, but sub-classing it is the intention, to produce
 * custom animations for in/out transitions that are generic and can 
 * be plugged in to an Activity in conjunction with a CustomManagedFragmentHolder.
 * 
 * Hides complexity that custom animations are only available on transactions
 * from Honeycomb onwards.
 * 
 * IMPORTANT: no state should be held by this class or any subclass that refers directly
 * or indirectly (eg. in any listener) to Activities/Fragments/View hierarchy as this animator will be
 * maintained across orientation changes and all of them would become stale (and therefore
 * leaked)
 */
public class FragmentTransitionAnimator {

	// constants
	public static final String LOG_TAG = "tryout-"+FragmentTransitionAnimator.class.getSimpleName();

	// to determine the behaviours to use
	public static final int BEHAVIOUR_ADD = 0;
	public static final int BEHAVIOUR_REPLACE = 1;

	// to break down params by direction
//	public static final int DIRECTION_PUSH = 0;
//	public static final int DIRECTION_PULL = 1;
	
	// to specify phases in an animation (for setting up optional params)
	// note: these will correspond to an array and are for convenience/clarity
	// more phases just increment by 1
	public static final int PHASE_1 = 0;
	public static final int PHASE_2 = 1;
	public static final int PHASE_3 = 2;
	public static final int PHASE_4 = 3;
	
	public static final int PARAM_NOT_DEFINED = -1;
	
	// param super class
	public abstract static class AnimationParam {
//		protected int direction = PARAM_NOT_DEFINED;
		protected int mPhase = PARAM_NOT_DEFINED;

		protected AnimationParam(int phase) {
//			this.direction = direction;
			this.mPhase = phase;
		}
	}
	
	// sub class for millis per inch (would only normally be for linear interpolation)
	public static class MillisPerInchParam extends AnimationParam {
		protected int mMillisPerInch = PARAM_NOT_DEFINED;
		protected int mDpi = PARAM_NOT_DEFINED;

		public MillisPerInchParam(int phase, int millisPerInch, int dpi) {
			super(phase);
			this.mMillisPerInch = millisPerInch;
			this.mDpi = dpi;
		}
	}
	
	// sub class for picking up an animation part way through
	public static class PickUpFromParam extends AnimationParam {
		// declares to the animator that interruption is allowed, if supported
		// it should mean the animation can be stopped via the
		// stopAnimation() method		
		protected boolean mInterruptible = false;
		
		public PickUpFromParam(int phase, boolean interruptible) {
			super(phase);
			this.mInterruptible = interruptible;
		}
	}
	
	// sub class for picking up an animation at the end
	// effectively putting all the pieces where they are when
	// completed, without animating anything
	// if supported, basically overrides other params
	public static class GoDirectlyToFinishParam extends AnimationParam {

		public GoDirectlyToFinishParam() {
			super(PARAM_NOT_DEFINED);
		}
	}
	
	// standard params set up here from animateIn() and animateOut() 
//	protected int mDirection = PARAM_NOT_DEFINED;
	protected AnimationParam[] mParams;
	
	// by requesting with a param that supports interruption of animation, this 
	// value is set so that a call to stopAnimation will cancel it 
	protected ValueAnimator mInterruptibleAnimation;
	
	/**
	 * Stops the running animation that was previously made available by a PickUpFromParam call 
	 * with interruptible set to true
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public final void stopAnimation() {
		if (mInterruptibleAnimation != null) {
			if (mInterruptibleAnimation.isRunning()) {
				mInterruptibleAnimation.cancel();
			}
		}
		else {
			Log.w(LOG_TAG, "stopAnimation: called but no animation set for interruption");
		}
	}
	
	/**
	 * In this class, default behaviour, no custom transition
	 * Subclasses should override customisedAnimateIn() to make custom incoming animation.
	 * @param activity
	 * @param fragmentIn
	 * @param fragmentOut
	 * @param resId normally something like R.id.fragmentcontainer
	 * @param behaviour
	 * @param tag 
	 * @param params optional params
	 * @return true if the transaction was completed
	 */
	public final boolean animateIn(FragmentActivity activity, Fragment fragmentIn, Fragment fragmentOut, int resId, int behaviour, String tag, AnimationParam ... params) {

//		mDirection = DIRECTION_PULL;
		
		mParams = params;
		
		// fancy animation only honeycomb on
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			return customisedAnimateIn(activity, fragmentIn, fragmentOut, resId, behaviour, tag);
		}
		else {
			return doDefaultInTransaction(activity, fragmentIn, resId, behaviour, tag);
		}
	}

	/**
	 * In this class, default behaviour, no custom transition
	 * Subclasses should override customisedAnimateIn() to make custom outgoing animation.
	 * Note the difference between this method and animateIn(), the fragmentIn and fragmentOut
	 * params are reversed.
	 * @param activity
	 * @param fragmentIn
	 * @param fragmentOut
	 * @param resId
	 * @param tag 
	 * @param params optional params
	 * @return true if the transaction was completed
	 */
	public final boolean animateOut(FragmentActivity activity, Fragment fragmentIn, Fragment fragmentOut, int resId, String tag, AnimationParam ... params) {

//		mDirection = DIRECTION_PUSH;

		mParams = params;

		// fancy animation only honeycomb on
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			return customisedAnimateOut(activity, fragmentIn, fragmentOut, resId, tag);
		}
		else {
			return doDefaultOutTransaction(activity, fragmentOut);
		}
	}

	/**
	 * Custom transition, just does the default, subclass to make customisation
	 * @param activity
	 * @param fragmentIn
	 * @param fragmentOut
	 * @param resId
	 * @param behaviour
	 * @param tag 
	 * @return true if the transaction was completed
	 */
	protected boolean customisedAnimateIn(FragmentActivity activity, Fragment fragmentIn, Fragment fragmentOut, int resId, int behaviour, String tag) {
		return doDefaultInTransaction(activity, fragmentIn, resId, behaviour, tag);
	}
		
	/**
	 * Custom transition, just does the default, subclass to make customisation
	 * @param activity
	 * @param fragmentIn
	 * @param fragmentOut
	 * @param resId
	 * @param tag 
	 * @return true if the transaction was completed
	 */
	protected boolean customisedAnimateOut(FragmentActivity activity, Fragment fragmentIn, Fragment fragmentOut, int resId, String tag) {
		return doDefaultOutTransaction(activity, fragmentOut);
	}
		
	/**
	 * Non customised transition, likely be called pre-Honeycomb from animateIn(). Although
	 * subclass could call it if it wasn't actually customising it for some reason.
	 * @param activity
	 * @param fragmentIn
	 * @param resId
	 * @param behaviour
	 * @param tag 
	 * @return
	 */
	protected final boolean doDefaultInTransaction(FragmentActivity activity, Fragment fragmentIn, int resId, int behaviour, String tag) {
		
		final FragmentManager fragmentManager = activity.getSupportFragmentManager();
		FragmentTransaction ft = fragmentManager.beginTransaction();

		if (behaviour == BEHAVIOUR_ADD) {
			ft.add(resId, fragmentIn, tag);
		}
		else {
			ft.replace(R.id.fragmentcontainer, fragmentIn, tag);
		}
		
		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
			.addToBackStack(null).commit();

		fragmentManager.executePendingTransactions();
		
		return true;
	}

	/**
	 * Primarily don't expect to call this directly as it's equivalent to just
	 * letting android do it (ie. from back key). However pre-Honeycomb will
	 * call this method instead of a customised animation.
	 * @param activity
	 * @param fragmentOut
	 * @return
	 */
	protected final boolean doDefaultOutTransaction(FragmentActivity activity, Fragment fragmentOut) {
		final FragmentManager fragmentManager = activity.getSupportFragmentManager();
		fragmentManager.beginTransaction()
			.remove(fragmentOut).commit();

		fragmentManager.executePendingTransactions();
		
		return true;
	}

	// convenience methods for subclasses
	
	protected final boolean isGoDirectlyToFinish() {
		if (mParams != null) {
			for (AnimationParam param : mParams) {
				if (param instanceof GoDirectlyToFinishParam) {
					return true;
				}
			}
		}
		
		return false;
	}

	protected final PickUpFromParam getPickUpPartial(int phase) {
		if (mParams != null) {
			for (AnimationParam param : mParams) {
				if (param instanceof PickUpFromParam && ((PickUpFromParam) param).mPhase == phase) {
					return (PickUpFromParam) param;
				}
			}
		}
		
		return null;
	}
	
	protected final MillisPerInchParam getMillisPerInch(int phase) {
		if (mParams != null) {
			for (AnimationParam param : mParams) {
				if (param instanceof MillisPerInchParam && ((MillisPerInchParam) param).mPhase == phase) {
					return (MillisPerInchParam) param;
				}
			}
		}
		
		return null;
	}

}
