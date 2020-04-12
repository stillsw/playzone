package tryout.fragmentssingleactivity.animation;

import tryout.fragmentssingleactivity.R;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewStub;
import android.view.animation.LinearInterpolator;
import android.widget.RelativeLayout;

/**
 * Transition animator that works on two fragments, the outgoing is dropped back
 * and the incoming slides in from the right over it.
 * These animations depend on >= Honeycomb, the superclass takes care of ensuring
 * that.
 * 
 * Note: to work correctly, the 2nd fragment must be 'added' in its transaction
 * so that it obscures the 1st fragment as it slides over it
 * 
 * Prerequisites: 
 * 1) A RelativeLayout with the id of R.id.top_relative_layout should exist for the outgoing
 * fragment, otherwise nothing at all will happen.
 * 
 * 2) For the darkening effect to the fragment that is dropping back
 * a ViewStub with the id of R.id.darklayer_stub is expected to be found on the Activity's container
 * and it should also inflate to R.id.darklayer which must exist in the project.
 * Without the ViewStub it will still work, only there will be no darkening.
 */
public class DropBackSlideInRightFragmentAnimator extends FragmentTransitionAnimator {
// test a change
	private static final String TAG = "tryout-DropBackSlideInRightFragmentAnimator";

	/**
	 * Has 2 phases:
	 * 1) drop back current fragment (out)
	 * 2) slide in new fragment (in)
	 * 
	 * Supports params:
	 * 
	 * GoDirectlyToFinishParam : overrides everything else, just puts both fragments as they would be at
	 * the end of the transition. (don't call this before the first fragment is laid out ... eg. onResume() is good)
	 * 
	 * PartialPickupParam : in 2nd phase only (slide in). In other words, 
	 * to mimic the view sliding in from part of that movement, supply a PartialPickUpParam which also
	 * supports interruption from the caller.
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected boolean customisedAnimateIn(FragmentActivity activity
			, final Fragment fragmentIn, Fragment fragmentOut, final int resId, int behaviour, final String tag) {

		// set everything up, detect views
		
		final FragmentManager fragmentManager = activity.getSupportFragmentManager();
		final Resources res = activity.getResources();

		// get the dark layer if there is one perhaps it's been inflated before, 
		// otherwise assuming view stub in the container for the activity 
		final View darkLayer = getDarkLayer(activity);
		
		// get the top layout in the XML UI defined for the outgoing fragment's view
		// this is the group that will be dropped back
		final RelativeLayout outGroup = (RelativeLayout) fragmentOut.getView().findViewById(R.id.top_relative_layout);		
		boolean includeDarkLayer = darkLayer != null && outGroup != null;

		// detect param to not animate but just show as at the end, finish with this
		if (isGoDirectlyToFinish()) {
			
			// put final values on out going fragment's views
			setViewDroppedBackNotAnimated(darkLayer, outGroup, 
					res.getFraction(R.fraction.fragment_drop_back_alpha, 1, 1)
					, res.getFraction(R.fraction.fragment_drop_back_scale, 1, 1));

			// make the transaction
			addFragmentIn(fragmentIn, resId, tag, fragmentManager);
			
			return true;
		}

		// existence of partial param in phase 2 (only phase supported) indicates bypassing
		// phase 1... the drop back and darken, plus the actual transaction to add the fragment
		
		PickUpFromParam pickUpPartialParam = getPickUpPartial(PHASE_2);
		
		// do phase 1, at end set up a listener to start phase 2
		if (pickUpPartialParam == null) {
			
			// the required relative layout wasn't found, a problem to continue
			if (outGroup == null) {
				Log.e(TAG, "customisedAnimateIn: missing R.layout.top_relative_layout");
				throw new IllegalArgumentException("Outgoing fragment must conform to design (missing layout item)");
			}

			// all good, do phase 1

			// get params for animations
			final int dropbackDuration = res.getInteger(R.integer.fragment_drop_back_duration);
			final float scaleBack = res.getFraction(R.fraction.fragment_drop_back_scale, 1, 1);

			// set up scale animator
	        PropertyValuesHolder scaleX =  PropertyValuesHolder.ofFloat("scaleX", 1.f, scaleBack);
	        PropertyValuesHolder scaleY =  PropertyValuesHolder.ofFloat("scaleY", 1.f, scaleBack);
	        ObjectAnimator scaleViewAnimator = ObjectAnimator.ofPropertyValuesHolder(outGroup, scaleX, scaleY);
	        scaleViewAnimator.setDuration(dropbackDuration);
	        
			// animate as a set, if there's no dark layer that isn't included though
	        AnimatorSet animatorSet = new AnimatorSet();
	        animatorSet.setInterpolator(new LinearInterpolator());

			// setup dark layer animator		
			if (includeDarkLayer) {
		        ObjectAnimator darkLayerAnimator = ObjectAnimator.ofFloat(darkLayer, "alpha", 0.f
												, res.getFraction(R.fraction.fragment_drop_back_alpha, 1, 1));
				darkLayerAnimator.setDuration(dropbackDuration);
				
				// maybe has already been through this and is currently gone
				darkLayer.setVisibility(View.VISIBLE);

				// get the animations to play together
		        animatorSet.playTogether(darkLayerAnimator, scaleViewAnimator);
			}
			
			// or just scale if no dark layer
			else {
				animatorSet.play(scaleViewAnimator);
			}

			animatorSet.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					// make the transaction
					addFragmentIn(fragmentIn, resId, tag, fragmentManager);
					
					// layout needs to complete before can start sliding in the next phase
					// listen for that
					final RelativeLayout inGroup = (RelativeLayout) fragmentIn.getView().findViewById(R.id.top_relative_layout);
					inGroup.addOnLayoutChangeListener(new OnLayoutChangeListener() {
						@Override
						public void onLayoutChange(View v, int left, int top, int right,
								int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
							
							// as soon as have a width the view is ready
							if (right - left > 0) {
								inGroup.removeOnLayoutChangeListener(this);
								slideInFromRightPhase(fragmentIn, res, false, false); // not interruptible
							}
						}
					});
				}
			});
			
	        animatorSet.start();
		}
		
		// have pickUpPartialParam - so skipped phase 1 (drop back)
		// instead of calling slide in from the listener at end of phase 1, call it directly
		else {
			slideInFromRightPhase(fragmentIn, res, true, pickUpPartialParam.mInterruptible);
		}
		
		return true;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void slideInFromRightPhase(final Fragment fragmentIn, Resources res, boolean isPartialPickup, boolean isInterruptible) {
		
		// don't do any of the rest, just animate from where the view is proportionally to the end of the slide
		RelativeLayout inGroup = (RelativeLayout) fragmentIn.getView().findViewById(R.id.top_relative_layout);
		
		// assuming this view is always supposed to be at 0 (relative to its parent)
		// when at normal position. I think this applies even if it's a right hand pane or something like that, because
		// its parent is the fragment's container, this view is only a child of that
		
		float x = inGroup.getX();
		float w = inGroup.getWidth();
		int slideInMs = res.getInteger(R.integer.fragment_slide_in_duration);

		// get distance to finish the movement, same as its position if partial, or its width if not
		float distToGo = isPartialPickup ? x : w;
		
		// calc slide in time from normal duration and delta of distance left
		int durationRemaining = isPartialPickup ? (int) (slideInMs * (distToGo / w)) : slideInMs;

		ObjectAnimator mover = ObjectAnimator.ofFloat(inGroup, "X", distToGo, 0f);
		mover.setDuration(durationRemaining);
		mover.setInterpolator(new LinearInterpolator()); // causes linear (default is accelerate/decelerate)
		mover.start();

		// allow interruption
		if (isInterruptible) {
			mInterruptibleAnimation = mover;
		}
	}

	private void addFragmentIn(final Fragment fragmentIn, final int resId,
			final String tag, final FragmentManager fragmentManager) {
		
		fragmentManager.beginTransaction()
			.add(resId, fragmentIn, tag)
			.addToBackStack(null).commit();

		fragmentManager.executePendingTransactions();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setViewDroppedBackNotAnimated(final View darkLayer,
			final RelativeLayout topGroup, final float darkAlpha, final float scaleBack) {
		
		if (darkLayer != null) {
			darkLayer.setAlpha(darkAlpha);
		}

		topGroup.setScaleX(scaleBack);
		topGroup.setScaleY(scaleBack);
	}

	private View getDarkLayer(final Activity activity) {
		
		return activity.findViewById(R.id.darklayer) == null  
				? ((ViewStub) activity.findViewById(R.id.darklayer_stub)).inflate()
				: activity.findViewById(R.id.darklayer);
	}

	/**
	 * Supports params in 1st phase only (slide out), and only for partial pickup. In other words, 
	 * to mimic the view sliding in from part of that movement, supply a PartialPickUpParam.
	 * A MillisPerInchParam could also be defined for it to change speed. Right now, that is used
	 * for matching the outgoing speed to drag speed as the user flings the fragment out to the right.
	 */
	@Override
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected boolean customisedAnimateOut(FragmentActivity activity
			, Fragment fragmentIn, final Fragment fragmentOut, int resId, String tag) {

		// get the activity and resources needed
		final FragmentManager fragmentManager = activity.getSupportFragmentManager();
		final Resources res = activity.getResources();

		// get the top layout in the XML UI defined for the fragment's view
		final RelativeLayout inGroup = (RelativeLayout) fragmentIn.getView().findViewById(R.id.top_relative_layout);
		
		// the required relative layout wasn't found, a problem to continue
		if (inGroup == null) {
			Log.e(TAG, "customisedAnimateOut: missing R.layout.top_relative_layout");
			throw new IllegalArgumentException("Incoming fragment must conform to design (missing layout item)");
		}

		// get params for animations
		final float darkAlpha = res.getFraction(R.fraction.fragment_drop_back_alpha, 1, 1);
		final float scaleBack = res.getFraction(R.fraction.fragment_drop_back_scale, 1, 1);
		
		// get the dark layer if there is one
		// perhaps it's been inflated before, if not assuming view stub in the container for the activity 
		final View darkLayer = getDarkLayer(activity);

		// need the view being revealed behind to be in the dropped back state
		setViewDroppedBackNotAnimated(darkLayer, inGroup, darkAlpha, scaleBack);
		
		// animate from where the view is proportionally to the end of the slide
		final RelativeLayout outGroup = (RelativeLayout) fragmentOut.getView().findViewById(R.id.top_relative_layout);
		
		// assuming a this view is always based at 0 (relative to its parent)
		// when at normal position. I think this applies even if it's a right hand pane or something like that, because
		// its parent is the fragment's container, this view is only a child of that
		
		final float x = outGroup.getX();
		final float w = outGroup.getWidth();

		// get distance to finish the movement, assuming fully open is 0, then it's always
		// width - position (regardless of partial pickup)
		float distToGo = w - x;

		// duration is defined in xml
		int slideOutMs = res.getInteger(R.integer.fragment_slide_in_duration); // default is same duration as slide in
		
		// there could be a speed param which overrides the duration from xml
		MillisPerInchParam millisPerInchParam = getMillisPerInch(PHASE_1);		
		if (millisPerInchParam != null) {
			// calculate how many ms to complete the whole movement at this speed
			float inches = w / millisPerInchParam.mDpi;
			slideOutMs = (int) (millisPerInchParam.mMillisPerInch * inches + .5f);
		}
		
		// calc slide in time from normal duration and delta of distance left
		// possible partial param in phase 1 (only phase supported)
		PickUpFromParam pickUpPartialParam = getPickUpPartial(PHASE_1);
		int durationRemaining = pickUpPartialParam != null ? (int) (slideOutMs * (distToGo / w)) : slideOutMs;
				
		ObjectAnimator mover = ObjectAnimator.ofFloat(outGroup, "X", x, (float)w);
		mover.setDuration(durationRemaining);
		mover.setInterpolator(new LinearInterpolator()); // causes linear (default is accelerate/decelerate)

		// add a listener to remove the fragment and do the 2nd phase animation when this one ends
		mover.addListener(new AnimatorListenerAdapter() {
			
			@Override
			public void onAnimationEnd(Animator animation) {

				// do the transition
				fragmentManager.beginTransaction()
					.remove(fragmentOut).commit();
				
				fragmentManager.executePendingTransactions();
				
				// and immediately pop the back stack 
				// this is a remove transaction, don't want it to also be a back stack entry
				fragmentManager.popBackStackImmediate();
				
				// get params for animations
				bringDropBackViewForwards(res, scaleBack, inGroup, darkLayer);
			}
		});

		mover.start();		
		
		return true;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void bringDropBackViewForwards(Resources res,
			final float scaleBack, final RelativeLayout inGroup,
			final View darkLayer) {
		
		int dropbackDuration = res.getInteger(R.integer.fragment_drop_back_duration);

		// set up scale animator
		PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", scaleBack, 1.f);
		PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", scaleBack, 1.f);
		ObjectAnimator scaleViewAnimator = ObjectAnimator.ofPropertyValuesHolder(inGroup, scaleX, scaleY);
		scaleViewAnimator.setDuration(dropbackDuration);
		
		// animate as a set, if there's no dark layer that isn't included though
		AnimatorSet animatorSet = new AnimatorSet();
		animatorSet.setInterpolator(new LinearInterpolator());

		// setup dark layer animator		
		if (darkLayer != null) {
		    ObjectAnimator darkLayerAnimator = ObjectAnimator.ofFloat(darkLayer, "alpha"
											, res.getFraction(R.fraction.fragment_drop_back_alpha, 1, 1), 0.f);
			darkLayerAnimator.setDuration(dropbackDuration);
			
			// maybe has already been through this and is currently gone
			darkLayer.setVisibility(View.VISIBLE);

			// remove the dark layer at the end
			ObjectAnimator darkLayerHider = ObjectAnimator.ofInt(darkLayer, "visible", View.GONE);
			darkLayerHider.setDuration(1);
			darkLayerHider.setStartDelay(dropbackDuration);
			
			// get the animations to play together
		    animatorSet.playTogether(darkLayerAnimator, darkLayerHider, scaleViewAnimator);
		}
		
		// or just scale if no dark layer
		else {
			animatorSet.play(scaleViewAnimator);
		}
		
		animatorSet.start();
	}

}
