package com.courseracapstone.android.accountsetup;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;

import retrofit.client.ApacheClient;
import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.FormUrlEncodedTypedOutput;
import android.content.Context;
import android.util.Log;

import com.courseracapstone.android.R;
import com.courseracapstone.android.https.EasyHttpClient;
import com.courseracapstone.common.PotlatchConstants;
import com.google.common.io.BaseEncoding;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Handles the login communication
 */
public class PotlatchServerAuthenticate {
	private final static String LOG_TAG = "Potlatch-"+PotlatchServerAuthenticate.class.getSimpleName();
	private Context mContext;

    public PotlatchServerAuthenticate(Context context) {
		mContext = context;
	}
    
    /**
     * Similar to the sign in method below, this one first attempts to sign up just using basic authentication,
     * and then forwards to the sign in with the same details if the user was successfully created
     * 
     * @param signUpUrl
     * @param tokenUrl
     * @param username
     * @param pass
     * @param authType
     * @param isAdmin
     * @return
     * @throws Exception
     */
    public String userSignUp(String signUpUrl, String tokenUrl, String username, String pass, String authType, boolean isAdmin) throws Exception {
		Log.d(LOG_TAG, String.format("userSignUp: signupUrl=%s", signUpUrl));
    	
        DefaultHttpClient httpClient = new EasyHttpClient();// new AcceptSelfSignedCertificatesHttpClient(mContext);
        Client client = new ApacheClient(httpClient);
        
        String clientId = isAdmin ? PotlatchConstants.ADMIN_CLIENT_ID : PotlatchConstants.NORMAL_CLIENT_ID
        		, clientSecret = "";
        
		FormUrlEncodedTypedOutput to = new FormUrlEncodedTypedOutput();
		to.addField("username", username);
		to.addField("password", pass);
		to.addField("client_id", clientId);
		to.addField("client_secret", clientSecret);

        String base64Auth = BaseEncoding.base64().encode(new String(clientId + ":" + clientSecret).getBytes());
		List<Header> headers = new ArrayList<Header>();
		headers.add(new Header("Authorization", "Basic " + base64Auth));

		// Create the actual password grant request using the data above
		Request req = new Request("POST", signUpUrl, headers, to);

    
		Response resp;
		try {
			resp = client.execute(req);
		} 
		catch (IllegalArgumentException e) {
			Log.e(LOG_TAG, "signup: client.execute illegal argument, probably server name is invalid (eg. impossible ip address)");
			throw new Exception(mContext.getResources().getString(R.string.login_submit_valid_server_error));
		}
		catch (HttpHostConnectException e) {
			Log.e(LOG_TAG, "signup: client.execute refused connection");
			// NOTE: same exception as when testing for 401 below, not sure when would get through here and fail there...
			throw new Exception(mContext.getResources().getString(R.string.signup_submit_forbidden_resp));
		}
		catch (Exception e) {
			Log.e(LOG_TAG, "signup: client.execute caused error", e);
			throw e;
		}
		
		// Make sure the server responded with 200 OK
		if (resp.getStatus() < 200 || resp.getStatus() > 299) {
			// bad request is user already exists
			if (resp.getStatus() == 400) {
				throw new Exception(mContext.getResources().getString(R.string.sign_submit_dupe_error));
			}
			// If not, we probably have bad credentials
			else if (resp.getStatus() == 401) {
				throw new Exception(mContext.getResources().getString(R.string.signup_submit_forbidden_resp));
			}
			else {
				// some more general error
				throw new Exception(mContext.getResources().getString(R.string.login_submit_invalid_resp, resp.getStatus(), resp.getReason()));
			}
		} 
    
		// all good, forward to sign in and get the auth token
		return userSignIn(tokenUrl, username, pass, authType, isAdmin);
    }
    
    /**
     * Sign in with oauth2 
     * @param tokenUrl
     * @param username
     * @param pass
     * @param authType
     * @param isAdmin
     * @return
     * @throws Exception
     */
    public String userSignIn(String tokenUrl, String username, String pass, String authType, boolean isAdmin) throws Exception {
		Log.d(LOG_TAG, String.format("userSignIn: url=%s un=%s, authType=%s"
				, tokenUrl, username, authType));

        DefaultHttpClient httpClient = new EasyHttpClient();// new AcceptSelfSignedCertificatesHttpClient(mContext);
        Client client = new ApacheClient(httpClient);
        
//        HttpPost httpPost = new HttpPost(tokenUrl);
        String clientId = isAdmin ? PotlatchConstants.ADMIN_CLIENT_ID : PotlatchConstants.NORMAL_CLIENT_ID
        		, clientSecret = "";
        
		FormUrlEncodedTypedOutput to = new FormUrlEncodedTypedOutput();
		to.addField("username", username);
		to.addField("password", pass);
		to.addField("client_id", clientId);
		to.addField("client_secret", clientSecret);
		to.addField("grant_type", "password");

        String base64Auth = BaseEncoding.base64().encode(new String(clientId + ":" + clientSecret).getBytes());
		List<Header> headers = new ArrayList<Header>();
		headers.add(new Header("Authorization", "Basic " + base64Auth));

		// Create the actual password grant request using the data above
		Request req = new Request("POST", tokenUrl, headers, to);
		
		// Request the password grant.
		Response resp;
		try {
			resp = client.execute(req);
		} 
		catch (IllegalArgumentException e) {
			Log.e(LOG_TAG, "userSignIn: client.execute illegal argument, probably server name is invalid (eg. impossible ip address)");
			throw new Exception(mContext.getResources().getString(R.string.login_submit_valid_server_error));
		}
		catch (HttpHostConnectException e) {
			Log.e(LOG_TAG, "userSignIn: client.execute refused connection, probably invalid user/pwd");
			// NOTE: same exception as when testing for 401 below, not sure when would get through here and fail there...
			throw new Exception(mContext.getResources().getString(R.string.login_submit_forbidden_resp));
		}
		catch (Exception e) {
			Log.e(LOG_TAG, "userSignIn: client.execute caused error", e);
			throw e;
		}
		
        String authtoken = null;
		
		// Make sure the server responded with 200 OK
		if (resp.getStatus() < 200 || resp.getStatus() > 299) {
			// If not, we probably have bad credentials
			if (resp.getStatus() == 401) {
				throw new Exception(mContext.getResources().getString(R.string.login_submit_forbidden_resp));
			}
			else if (resp.getStatus() == 400) { // bad request happens when password no longer works
				throw new Exception(mContext.getResources().getString(R.string.login_submit_badrequest_resp));
			}
			else {
				// some more general error
				throw new Exception(mContext.getResources().getString(R.string.login_submit_invalid_resp, resp.getStatus(), resp.getReason()));
			}
		} 
		else {
			// Extract the string body from the response
			String body = CharStreams.toString( new InputStreamReader(resp.getBody().in(), "UTF-8"));
	        //String body = IOUtils.toString(resp.getBody().in());
			
			// Extract the access_token (bearer token) from the response so that we
	        // can add it to future requests.
			authtoken = new Gson().fromJson(body, JsonObject.class).get("access_token").getAsString();
            Log.d(LOG_TAG, "got auth token : "+authtoken);
		}
        
        return authtoken;
    }

}
