package com.courseracapstone.android.accountsetup;

import static android.accounts.AccountManager.KEY_BOOLEAN_RESULT;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.courseracapstone.common.PotlatchConstants;

/**
 * The authenticator that handles the addAccount() and getAuthToken() calls
 * 
 * @author Udini, xxx xxx modified a little
 */
public class PotlatchAuthenticator extends AbstractAccountAuthenticator {

	private final String LOG_TAG = "Potlatch-"+getClass().getSimpleName();
    private final Context mContext;
    
	private PotlatchServerAuthenticate mServerAuthenticate;

    public PotlatchAuthenticator(Context context) {
        super(context);
        this.mContext = context;
        mServerAuthenticate = new PotlatchServerAuthenticate(context);    
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) throws NetworkErrorException {
        Log.d(LOG_TAG, "addAccount");

        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_TYPE, accountType);
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADDING_NEW_ACCOUNT, true);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {

        Log.d(LOG_TAG, "getAuthToken");

        // If the caller requested an authToken type we don't support, then
        // return an error
        if (!authTokenType.equals(PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
            return result;
        }

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        final AccountManager am = AccountManager.get(mContext);

        String authToken = am.peekAuthToken(account, authTokenType);

        Log.d(LOG_TAG, "getAuthToken: peekAuthToken returned - " + authToken);

        String server = am.getUserData(account, PotlatchAccountConstants.ACCOUNT_SERVER_KEY);
	    String tokenUrl = "https://"+server+PotlatchConstants.TOKEN_PATH;
	    boolean isAdmin = am.getUserData(account, PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY).equals("true");

        // Lets give another try to authenticate the user
        if (TextUtils.isEmpty(authToken)) {
            final String password = am.getPassword(account);
            if (password != null) {
                try {
            	    Log.d(LOG_TAG, "getAuthToken: re-auth with password, url="+tokenUrl);

                    authToken = mServerAuthenticate.userSignIn(tokenUrl, account.name, password, authTokenType, isAdmin);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // If we get an authToken - we return it
        if (!TextUtils.isEmpty(authToken)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
            
            return result;
        }

        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials. We do that by creating
        // an intent to display our AuthenticatorActivity.
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_TYPE, account.type);
        intent.putExtra(AuthenticatorActivity.ARG_AUTH_TYPE, authTokenType);
        intent.putExtra(AuthenticatorActivity.ARG_ACCOUNT_NAME, account.name);
        intent.putExtra(AuthenticatorActivity.ARG_SERVER, 
        		am.getUserData(account, PotlatchAccountConstants.ACCOUNT_SERVER_KEY));
        intent.putExtra(AuthenticatorActivity.ARG_IS_ADMIN, 
        		am.getUserData(account, PotlatchAccountConstants.ADMIN_ACCOUNT_FLAG_KEY).equals("true"));
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }


    @Override
    public String getAuthTokenLabel(String authTokenType) {
        if (PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH.equals(authTokenType))
            return PotlatchAccountConstants.AUTHTOKEN_TYPE_POTLATCH_LABEL;
        else
            return authTokenType + " (Label)";
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
        final Bundle result = new Bundle();
        result.putBoolean(KEY_BOOLEAN_RESULT, false);
        return result;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return null;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        return null;
    }
}
