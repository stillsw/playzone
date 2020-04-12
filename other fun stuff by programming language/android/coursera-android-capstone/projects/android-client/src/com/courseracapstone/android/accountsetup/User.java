package com.courseracapstone.android.accountsetup;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Encapsulates the user that is logged in, and is kept on the PotlatchApplication.
 * Only needs one of these, it is updated as needed.
 * 
 * @author xxx xxx
 *
 */
public class User implements Parcelable {
	
	private static final String USERNAME = "un";
	private static final String SERVER = "server";
	private static final String AUTH_TOKEN = "token";
	private static final String IS_ADMIN = "is admin";
	private String mUsername;
	private String mServer;
	private String mAuthToken;
	private boolean mIsAdmin;
	
	public User() {};

	public User(String username, String server, String authToken, boolean isAdmin) {
		this.mUsername = username;
		this.mServer = server;
		this.mAuthToken = authToken;
		this.mIsAdmin = isAdmin;
	}

	public User(Parcel in) {
		Bundle b = in.readBundle();
		mUsername = b.getString(USERNAME);
		mServer = b.getString(SERVER);
		mAuthToken = b.getString(AUTH_TOKEN);
		mIsAdmin = b.getBoolean(IS_ADMIN);
	}
	
	public void setUser(String username, String server, String authToken, boolean isAdmin) {
		this.mUsername = username;
		this.mServer = server;
		this.mAuthToken = authToken;
		this.mIsAdmin = isAdmin;
	}

	public String getUsername() {
		return mUsername;
	}

	public void setUsername(String username) {
		this.mUsername = username;
	}

	public String getServer() {
		return mServer;
	}

	public void setServer(String server) {
		this.mServer = server;
	}

	public String getAuthToken() {
		return mAuthToken;
	}

	public void setAuthToken(String authToken) {
		this.mAuthToken = authToken;
	}

	public boolean isAdmin() {
		return mIsAdmin;
	}

	public void setAdmin(boolean isAdmin) {
		this.mIsAdmin = isAdmin;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		Bundle b = new Bundle();
		b.putString(USERNAME, mUsername);
		b.putString(SERVER, mServer);
		b.putString(AUTH_TOKEN, mAuthToken);
		b.putBoolean(IS_ADMIN, mIsAdmin);
		dest.writeBundle(b);
	}
	
	public static final Parcelable.Creator<User> CREATOR = new Parcelable.Creator<User>() {
        public User createFromParcel(Parcel in) {
            return new User(in); 
        }

        public User[] newArray(int size) {
            return new User[size];
        }
    };
    
	@Override
    public String toString() {
    	return String.format("name=[%s],server=[%s],authtoken=[%s],isadmin=[%s]"
    			, mUsername, mServer, mAuthToken, mIsAdmin);
    };
}
