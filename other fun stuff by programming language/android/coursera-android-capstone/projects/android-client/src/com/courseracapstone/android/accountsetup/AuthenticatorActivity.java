package com.courseracapstone.android.accountsetup;

import java.net.SocketTimeoutException;

import org.apache.http.conn.ConnectTimeoutException;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.courseracapstone.android.LoginActivity;
import com.courseracapstone.android.R;
import com.courseracapstone.common.PotlatchConstants;

/**
 * The Authenticator activity.
 * Called by the Authenticator and in charge of identifying the user.
 * 
 * @author xxx xxx - heavily modified from example by udinic
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity {

	private final String LOG_TAG = "Potlatch-"+getClass().getSimpleName();

	// arguments to call this activity with
    public final static String ARG_ACCOUNT_TYPE = "ACCOUNT_TYPE";
    public final static String ARG_AUTH_TYPE = "AUTH_TYPE";
    public final static String ARG_ACCOUNT_NAME = "ACCOUNT_NAME";
    public final static String ARG_IS_ADDING_NEW_ACCOUNT = "IS_ADDING_ACCOUNT";
	public static final String ARG_SERVER = "SERVER_NAME";
	public static final String ARG_IS_ADMIN = "IS_ADMIN";

    public static final String KEY_ERROR_MESSAGE = "ERR_MSG";

    public final static String PARAM_USER_PASS = "USER_PASS";

    private AccountManager mAccountManager;
    private String mAuthTokenType;
    
	private PotlatchServerAuthenticate mServerAuthenticate;
	private boolean mIsAddingNewAccount;
	private boolean mIsAdmin;
	private String mServer;
	private EditText mAccountNameVw;
	private EditText mServerDomainVw;
	private CheckBox mLinkExisting;
	private CheckBox mUseDev;
	private CheckBox mChooseTester;
	private EditText mPasswordVw;
	private EditText mRepeatPasswordVw;
	private CheckBox mAdminVw;
	private String mCurrentAccountName;
	private Spinner mDevAccNameSpinner;
	private ImageButton mSubmit;
	private ProgressBar mProgressBar;
	private Button mChangeUserBtn;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.potlatch_login);
        
        Log.d(LOG_TAG, "onCreate");
        
        getActionBar().setDisplayUseLogoEnabled(true);
        
        mServerAuthenticate = new PotlatchServerAuthenticate(this);    
        mAccountManager = AccountManager.get(getBaseContext());

        mIsAddingNewAccount = getIntent().getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false);
        mIsAdmin = getIntent().getBooleanExtra(ARG_IS_ADMIN, false);
        mServer = getIntent().getStringExtra(ARG_SERVER);
        
        mCurrentAccountName = getIntent().getStringExtra(ARG_ACCOUNT_NAME);
        mAuthTokenType = getIntent().getStringExtra(ARG_AUTH_TYPE);
        if (mAuthTokenType == null)
            mAuthTokenType = PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH;

        mAccountNameVw = (EditText)findViewById(R.id.accountName);
        if (mCurrentAccountName != null) {
        	mAccountNameVw.setText(mCurrentAccountName);
        }

        mSubmit = (ImageButton) findViewById(R.id.fabbutton);
        mSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        
        OnCheckedChangeListener checkChangeListener = new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (buttonView.equals(mChooseTester) && !isChecked) {
					mAdminVw.setChecked(false);
				}
				layoutViews();
			}
		};
        mUseDev = (CheckBox) findViewById(R.id.useDev);
        mUseDev.setOnCheckedChangeListener(checkChangeListener);
        mLinkExisting = (CheckBox) findViewById(R.id.linkExisting);
        mLinkExisting.setOnCheckedChangeListener(checkChangeListener);
        mChooseTester = (CheckBox) findViewById(R.id.chooseTestUser);
        mChooseTester.setOnCheckedChangeListener(checkChangeListener);

        mAdminVw = (CheckBox) findViewById(R.id.isDevAdmin);
        mDevAccNameSpinner = (Spinner) findViewById(R.id.accountNameSpinner);
		mDevAccNameSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int pos,long id) {
				mAdminVw.setChecked(
						PotlatchAccountConstants.DEV_ADMIN_ACCOUNT_USER
						.equals(parent.getItemAtPosition(pos).toString()));
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
		});

        // other fields
        mServerDomainVw = (EditText)findViewById(R.id.serverDomain);
        mPasswordVw = (EditText)findViewById(R.id.accountPassword);
		mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
		mRepeatPasswordVw = (EditText)findViewById(R.id.accountRepeatPassword);
		
		// password change
		if (!mIsAddingNewAccount) {
			mChangeUserBtn = (Button) findViewById(R.id.chooseFromExisting);
			mChangeUserBtn.setVisibility(View.VISIBLE);
			mChangeUserBtn.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View v) {
	        		Log.d(LOG_TAG, "choose another user");
	        		Intent i = new Intent(AuthenticatorActivity.this, LoginActivity.class);
	        		i.putExtra(PotlatchAccountConstants.PROMPT_FOR_LOGIN, true);
	        		startActivity(i);
	        		
	        		// don't allow a back press to navigate back here
        			finish();
	            }
	        });
		}

        layoutViews();
    }

    /**
     * Called during onCreate() and also when the use development server checkbox is clicked
     * @param useDev 
     */
    protected void layoutViews() {
    	
    	boolean useDev = mUseDev.isChecked();
    	
    	Resources res = getResources();
    	
    	// show the current setup
    	if (mIsAddingNewAccount) {

    		if (mLinkExisting.isChecked() || mChooseTester.isChecked()) {
        		mRepeatPasswordVw.setVisibility(View.GONE);
    		}
    		else {
        		mRepeatPasswordVw.setVisibility(View.VISIBLE);
    		}
    		
    		if (useDev) {
    			mServerDomainVw.setHint(res.getString(R.string.login_dev_server_hint));
    			mAdminVw.setVisibility(View.VISIBLE);
    			mChooseTester.setVisibility(View.VISIBLE);
    			
    			if (mChooseTester.isChecked()) {
        			mPasswordVw.setVisibility(View.GONE);
        			mAccountNameVw.setVisibility(View.GONE);
        			mDevAccNameSpinner.setVisibility(View.VISIBLE);
        			mAdminVw.setEnabled(false);
    				mAdminVw.setChecked(
    						PotlatchAccountConstants.DEV_ADMIN_ACCOUNT_USER
    						.equals(mDevAccNameSpinner.getSelectedItem().toString()));
    			}
    			else {
        			mPasswordVw.setVisibility(View.VISIBLE);
        			mAccountNameVw.setVisibility(View.VISIBLE);
        			mDevAccNameSpinner.setVisibility(View.GONE);
        			mAdminVw.setEnabled(true);
    			}
    			
    			// nothing entered in dev server yet, help out with the one we have
    			// in keystore
    			if (TextUtils.isEmpty(mServerDomainVw.getText())) {
    				mServerDomainVw.setText(R.string.dev_server_we_know_about);
    			}
    		}
    		else {
    			mServerDomainVw.setHint(res.getString(R.string.login_server_domain_hint));
    			mAccountNameVw.setVisibility(View.VISIBLE);
    			mAdminVw.setVisibility(View.GONE);
    			mDevAccNameSpinner.setVisibility(View.GONE);
    			mChooseTester.setVisibility(View.GONE);
    		}
    	}
    	else {
    		// only the current setup and password is required
    		getActionBar().setTitle(R.string.password_change_title);
    		
    		mLinkExisting.setVisibility(View.GONE);    		
    		mUseDev.setVisibility(View.GONE);
    		mRepeatPasswordVw.setVisibility(View.GONE);
    		mServerDomainVw.setEnabled(false);
    		mServerDomainVw.setText(mServer);
    		mAccountNameVw.setEnabled(false);   		
			mAdminVw.setVisibility(View.GONE);
//			mAdminVw.setChecked(mIsAdmin);
   			mDevAccNameSpinner.setVisibility(View.GONE);
			mChooseTester.setVisibility(View.GONE);
    	}
    	
	}

	/**
	 * Called to disable the buttons whenever one of them is pressed
	 * and to re-enable if there's a user cancellation that brings them back
	 * @param enabled
	 */
	private void setButtons(boolean enabled) {
		mSubmit.setEnabled(enabled);
		if (mChangeUserBtn != null) {
			mChangeUserBtn.setEnabled(enabled);
		}
		
		mProgressBar.setVisibility(enabled ? View.INVISIBLE : View.VISIBLE);
	}
	
    /**
     * Assemble the values from the various fields and kick off the async task
     * that will attempt to get an auth token
     */
	public void submit() {

		final String server = mServerDomainVw.getText().toString();
		
		String username = null;
		String password = null;
		
		username = mAccountNameVw.getText().toString();
		password = mPasswordVw.getText().toString();
		
		if (mUseDev.isChecked() && mChooseTester.isChecked()) {
			// use dev settings
			username = mDevAccNameSpinner.getSelectedItem().toString();
			password = mAdminVw.isChecked() 
					? PotlatchAccountConstants.DEV_ADMIN_ACCOUNT_PWD
					: PotlatchAccountConstants.DEV_USER_ACCOUNT_PWD;
		}
		else {
			// user entered values
			username = mAccountNameVw.getText().toString();
			password = mPasswordVw.getText().toString();
		}
		
		// validate the input before go on
		final Resources res = getResources();

		if (TextUtils.isEmpty(server) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
			
			Toast.makeText(this, res.getString(R.string.login_submit_valid_error)
					, Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (mIsAddingNewAccount && !mLinkExisting.isChecked() && !mChooseTester.isChecked()
				&& !password.equals(mRepeatPasswordVw.getText().toString())) {
			
			Toast.makeText(this, res.getString(R.string.login_submit_passwords_error)
					, Toast.LENGTH_SHORT).show();
			return;
		}
		else if (mIsAddingNewAccount) {
			// make sure the account name isn't already used on this device
			Account[] accounts = mAccountManager.getAccountsByType(PotlatchAccountConstants.ACCOUNT_TYPE);
			for (Account a : accounts) {
				if (a.name.equals(username)) {
					Toast.makeText(this, res.getString(R.string.login_submit_dupe_name_error, 
							mAccountManager.getUserData(a,
									PotlatchAccountConstants.ACCOUNT_SERVER_KEY))
							, Toast.LENGTH_SHORT).show();
					return;
				}
			}

		}
		
		// prevent further presses
		setButtons(false);
		
		final String userName = username;
		final String userPass = password;
        final String accountType = getIntent().getStringExtra(ARG_ACCOUNT_TYPE);
	    final String tokenUrl = "https://"+server+PotlatchConstants.TOKEN_PATH;
    	final boolean isAdmin = mIsAdmin || (mUseDev.isChecked() && mAdminVw.isChecked());

        new AsyncTask<String, Void, Intent>() {

            @Override
            protected Intent doInBackground(String... params) {

                Log.d(LOG_TAG, "Start authenticating");

                String authtoken = null;
                Bundle data = new Bundle();
                try {
                	// make sure can connect 
                	if (!validateServer(tokenUrl, res)) {
                		throw new Exception("validateServer error");
                	}
                	
                	if (mIsAddingNewAccount && !mLinkExisting.isChecked() && !mChooseTester.isChecked()) {
                		String signUpUrl = "https://"+server+PotlatchConstants.POTLATCH_SIGN_UP_PATH;
                		authtoken = mServerAuthenticate.userSignUp(signUpUrl, tokenUrl, 
                				userName, userPass, mAuthTokenType, isAdmin);
                	}
                	else {
                		authtoken = mServerAuthenticate.userSignIn(tokenUrl, 
                				userName, userPass, mAuthTokenType, isAdmin);
                	}
                	
                    Log.d(LOG_TAG, "returned with authtoken="+authtoken);

                    data.putString(AccountManager.KEY_ACCOUNT_NAME, userName);
                    data.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
                    data.putString(AccountManager.KEY_AUTHTOKEN, authtoken);
                    data.putString(PotlatchAccountConstants.CURRENT_AUTHTOKEN_KEY, authtoken);
                    data.putString(PARAM_USER_PASS, userPass);
                    data.putString(PotlatchAccountConstants.ACCOUNT_SERVER_KEY, server);
                    data.putBoolean(PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY, isAdmin);

                } catch (ConnectTimeoutException | SocketTimeoutException e) {
                    Log.w(LOG_TAG, "failed to get authtoken: timed out");
                    data.putString(KEY_ERROR_MESSAGE, res.getString(R.string.login_submit_connect_error));
                }
	            catch (Exception e) {
	                Log.e(LOG_TAG, "failed to get authtoken", e);
	                data.putString(KEY_ERROR_MESSAGE, e.getMessage());
	            }

                final Intent res = new Intent();
                res.putExtras(data);
                return res;
            }

            @Override
            protected void onPostExecute(Intent intent) {
                if (intent.hasExtra(KEY_ERROR_MESSAGE)) {
                    Toast.makeText(getBaseContext(), intent.getStringExtra(KEY_ERROR_MESSAGE), Toast.LENGTH_SHORT).show();
                    // allow retry
                    setButtons(true);
                } 
                else {
                    finishLogin(intent);
                }
            }
        }.execute();
    }

	private boolean validateServer(final String server, Resources res) throws Exception {
		// validate the server, not so easy, but at least remove obvious errors
		if (server.contains(" ") || server.contains(",") || server.contains("..")
				|| server.startsWith(".") || server.endsWith(".")
				|| server.startsWith(":") || server.endsWith(":")) {
			return false;
		}
		else {
			return true;
		}
	}

    private void finishLogin(Intent intent) {
        Log.d(LOG_TAG, "finishLogin");

        String accountName = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accountPassword = intent.getStringExtra(PARAM_USER_PASS);
        final Account account = new Account(accountName, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));

        
        if (mIsAddingNewAccount) {
            Log.d(LOG_TAG, "new account, addAccountExplicitly");
            String authtoken = intent.getStringExtra(AccountManager.KEY_AUTHTOKEN);
            String authtokenType = mAuthTokenType;

            // Creating the account on the device and setting the auth token we got
            // (Not setting the auth token will cause another call to the server to authenticate the user)
            Bundle userData = new Bundle();
            userData.putString(PotlatchAccountConstants.ACCOUNT_SERVER_KEY
            		, intent.getStringExtra(PotlatchAccountConstants.ACCOUNT_SERVER_KEY));
            userData.putString(PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY
            		, Boolean.toString(
            			intent.getBooleanExtra(PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY, false)));

            mAccountManager.addAccountExplicitly(account, accountPassword, userData);
            mAccountManager.setAuthToken(account, authtokenType, authtoken);
        }
        else {
            Log.d(LOG_TAG, "not new account, setPassword");
            mAccountManager.setPassword(account, accountPassword);
        }

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

}
