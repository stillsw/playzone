package tryout.fragmentssingleactivity;

import tryout.fragmentssingleactivity.CustomManagedFragmentHolder.CustomManagedFragmentParentActivity;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class BlueRedFragment extends Fragment {

	private static final String TAG = "tryout-fragsTest-frag";

	// argument keys
	private static final String ARG_COLOUR = "colour";
	private static final String ARG_SHOW_NAV = "nav";
	
	// tag assigned by the caller or from xml inflation, for matching
	private String mTag;
	private String mHexColour;
	
	private CustomManagedFragmentParentActivity mCaller;
	private TextView mTxt;
	private boolean mShowNav = false;
	
    /**
     * Create a new instance with params
     * @param showNavigation 
     */
    public static BlueRedFragment newInstance(String colour, boolean showNavigation) {
    	BlueRedFragment fragment = new BlueRedFragment();

        Bundle args = new Bundle();
        
        args.putString(ARG_COLOUR, colour);
        args.putBoolean(ARG_SHOW_NAV, showNavigation);
        fragment.setArguments(args);

        return fragment;
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.either, container, false);
		
		mTxt = (TextView) v.findViewById(R.id.tv);
		Button btn = (Button) v.findViewById(R.id.button);
		btn.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				long time = SystemClock.elapsedRealtime();
				mTxt.setText("now it's "+time);
				mCaller.updatedAt(mTag, time);
			}
		});
		
		v.setBackgroundColor(Color.parseColor(mHexColour));
		
		// arg is supplied for showing navigation button or not
		// the button itself is defined in the xml and on the onClick will be handled by the activity
		if (mShowNav) {
			Button navBtn = (Button)v.findViewById(R.id.navBtn);
			if (navBtn != null) {
				navBtn.setVisibility(View.VISIBLE);
			}
		}

		return v;
	}

	/**
	 * Manually initialised Fragments have arguments set up in newInstance()
	 */
	private void getParams() {
		mTag = getTag();
		
		Bundle bundle = getArguments();
		
		if (bundle != null) {
			mHexColour = bundle.getString(ARG_COLOUR);
			mShowNav = bundle.getBoolean(ARG_SHOW_NAV, false);
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mCaller = (CustomManagedFragmentParentActivity) activity;

		// get mCallerAllocIdx and colour from arguments
		getParams();
	}

	@Override
	public void onResume() {
		super.onResume();
		restoreSettings();
	}

	/**
	 * Called from onResume() to restore the text and background colour
	 */
	private void restoreSettings() {
		if (mCaller == null) {
			Log.w(TAG, "restore settings called caller is null");
			return;
		}
		
		long time = mCaller.getLastUpdated(mTag);
		if (time != Long.MIN_VALUE) {
			mTxt.setText("restored to "+time);
		} 		
	}
	
	
}