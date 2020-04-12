package com.courseracapstone.android;

import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.courseracapstone.android.accountsetup.PotlatchAccountConstants;
import com.courseracapstone.android.utils.ScalingUtilities;
import com.courseracapstone.android.utils.ScalingUtilities.ScalingLogic;

public class LoginActivity extends AbstractPotlatchActivity {

	private final String LOG_TAG = "Potlatch-"+getClass().getSimpleName();
	private Button mSubmitButton;
	private Spinner mAccNameSpinner;
	private AccountManager mAccountManager;
	private Account[] mAccounts;
	private ProgressBar mProgressBar;
	private boolean mIsWelcomeLayout;
	private ImageButton mFabButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PotlatchApplication app = (PotlatchApplication)getApplication();
		mAccountManager = app.getAccountManager();

		if (getIntent().getBooleanExtra(PotlatchAccountConstants.PROMPT_FOR_LOGIN, false)) {
			// called from somewhere else in the app, because the user has
			// asked to login as someone else, or they have to choose from 
			// the list of installed users when the one they were using got removed

			setContentView(R.layout.login_choose_accounts_layout);
			
			Resources res = getResources();
			// change the title
			getActionBar().setTitle(res.getString(R.string.choose_accounts_dialog));
			
			mAccounts = app.getAppAccounts(mAccountManager);			
			
			mAccNameSpinner = (Spinner) findViewById(R.id.accountNameSpinner);
			TextView loginDetsVw = (TextView) findViewById(R.id.login_details);

			SharedPreferences prefs = getSharedPreferences(PotlatchAccountConstants.SHARED_PREFS_KEY, Context.MODE_PRIVATE);
			String currentUser = prefs.getString(PotlatchAccountConstants.USERNAME_PREF_KEY, null);
			String currentServer = prefs.getString(PotlatchAccountConstants.SERVER_PREF_KEY, null);
			int selectedInd = 0;
			
			List<String> list = new ArrayList<String>();
			for (Account a : mAccounts) {
				String server = mAccountManager.getUserData(a, PotlatchAccountConstants.ACCOUNT_SERVER_KEY);
				String adminData = mAccountManager.getUserData(a, PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY);
				String adminStr = adminData != null && adminData.equals("true")
						? String.format("(%s) ", res.getString(R.string.administrator_label))
						: "";
						
				list.add(String.format("%s %s@%s", a.name, adminStr, server));
				
				if (a.name.equals(currentUser) && currentServer.equals(server)) {
					selectedInd = list.size() -1;

					if (loginDetsVw != null) {
						loginDetsVw.setText(res.getString(R.string.current_login_label, a.name, adminStr, server));
					}				
				}
			}
			
			ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this,
					android.R.layout.simple_spinner_item, list);
			
			dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			mAccNameSpinner.setAdapter(dataAdapter);
			mAccNameSpinner.setSelection(selectedInd);

			mSubmitButton = (Button) findViewById(R.id.setup_account_btn);
		}
		else {
			setContentView(R.layout.welcome_layout);
			mIsWelcomeLayout = true;

			makeWelcomeBackground();
			mFabButton = (ImageButton) findViewById(R.id.fabbutton);
		}

		mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
		
		// test for a connection
		setOnline(isNetworkOnlineOtherwiseRegisterForBroadcast());
	}
	
	private void setOnline(boolean online) {
		setButtons(online);
		
		findViewById(R.id.not_online).setVisibility(online ? View.INVISIBLE : View.VISIBLE);
	}
	
	/**
	 * must have not had it, and now got it
	 */
	@Override
	protected void onGainInternet() {
		super.onGainInternet();
		
		setOnline(true);
	}
	
	/**
	 * Called on click for login choose from accounts layout 
	 * 
	 * @param btn
	 */
	public void chooseLogin(View btn) {
		// the network was available, otherwise the button couldn't be pressed, but it's
		// been lost in the meantime
		if (isNetworkOnlineOtherwiseRegisterForBroadcast()) { 
			setButtons(false);
			// get token will invoke the account manager which will invoke
			// the gifts activity when done
			getToken(mAccountManager, mAccounts[mAccNameSpinner.getSelectedItemPosition()]);
		}
		else {
			// show a toast that the action can't be done now
			Toast.makeText(this, getResources().getText(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Called on click when user presses setup new account
	 * 
	 * @param btn
	 */
	public void setupLogin(View btn) {
		// the network was available, otherwise the button couldn't be pressed, but it's
		// been lost in the meantime
		if (isNetworkOnlineOtherwiseRegisterForBroadcast()) { 
			setButtons(false);
			
			mAccountManager.addAccount(PotlatchAccountConstants.ACCOUNT_TYPE
					, PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH // using the same key for auth token and account
					, null
					, null
					, this
					, getAccountManagerCallback(null, null) // don't have a server, nor special callback
					, new Handler());
		}
		else {
			// show a toast that the action can't be done now
			Toast.makeText(this, getResources().getText(R.string.not_online_action_toast_msg), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Called from chooseLogin when the user has pressed the login to an existing user
	 * button, need to get the auth token from the account manager with it, on 
	 * successful completion, it starts the gifts activity
	 * 
	 * @param am
	 * @param account
	 */
	protected void getToken(AccountManager am, Account account) {
		String server = mAccountManager.getUserData(
				mAccounts[mAccNameSpinner.getSelectedItemPosition()]
				, PotlatchAccountConstants.ACCOUNT_SERVER_KEY);

		am.getAuthToken(account
				, PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH
				, null
				, this
				, getAccountManagerCallback(server, null)
				, new Handler());
	}

	/**
	 * Called to disable the buttons whenever one of them is pressed
	 * and to re-enable if there's a user cancellation that brings them back
	 * @param enabled
	 */
	protected void setButtons(boolean enabled) {
		if (mSubmitButton != null) {
			mSubmitButton.setEnabled(enabled);			
		}
		if (mFabButton != null) {
			mFabButton.setEnabled(enabled);
		}
		 
		boolean showProg = !enabled
				&& !mIsWaitingForInternet 
				&& isNetworkOnlineOtherwiseRegisterForBroadcast();

		if (mProgressBar != null) {
			mProgressBar.setVisibility(showProg ? View.VISIBLE : View.INVISIBLE);
		}
	}

	@Override
	protected void fabButtonPressed() {
		if (mIsWelcomeLayout) {
			setupLogin(null);
		}
		else {
			chooseLogin(null);
		}
	}
	
	private void makeWelcomeBackground() {
		
		// if it's the welcome page, scale the image to the max dimensions
		// Android's scaling don't do what I'm looking for, which is scale a square
		// image to the max of w/h and then place at top/left... so port/land will both
		// crop either bottom or right

		FrameLayout frame = (FrameLayout) findViewById(R.id.welcome_image);
		if (frame == null) {
			Log.w(LOG_TAG, "missing image view in the layout");
			return;
		}
		
		Resources res = getResources();
		DisplayMetrics metrics = res.getDisplayMetrics();
		int maxWh = Math.max(metrics.widthPixels, metrics.heightPixels);
		// add to that enough to cover the nav bar in either direction
		Log.d(LOG_TAG, "maxWh="+maxWh);
		maxWh += (int)(48 * metrics.density);
		Log.d(LOG_TAG, "after add 48* maxWh="+maxWh);
		
		
		final Bitmap totem = 
				ScalingUtilities.decodeResource(res, R.drawable.totem, maxWh, maxWh, ScalingLogic.CROP);

		if (totem != null) {
			ImageView imageVw = new ImageView(this) {
				@Override
				protected void onDraw(Canvas canvas) {
					canvas.drawBitmap(totem, 0, 0, null);
				}
			};
			
			frame.addView(imageVw, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));			
		}
	}
}
