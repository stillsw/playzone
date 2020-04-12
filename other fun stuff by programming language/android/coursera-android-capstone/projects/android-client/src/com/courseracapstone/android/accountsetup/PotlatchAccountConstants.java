package com.courseracapstone.android.accountsetup;

public class PotlatchAccountConstants {

	// note, this has to match the xml/authenticator.xml accountType
    public static final String ACCOUNT_TYPE = "capstone.potlatch"; 
    public static final String ACCOUNT_SERVER_KEY = "server"; 
    public static final String ADMIN_ACCOUNT_FLAG_KEY = "isAdmin"; 
    public static final String CURRENT_AUTHTOKEN_KEY = "currentAuthToken"; 

    // preferences and keys
    public static final String SHARED_PREFS_KEY = "potlatch"; 
    public static final String USERNAME_PREF_KEY = "username"; 
    public static final String SERVER_PREF_KEY = "server"; 
	public static final String USER_KEY = "user";
    
    public static final int DEFAULT_DEV_PORT = 8443;
    
    /**
     * Auth token types, not sure how these pan out
     */
//    public static final String AUTHTOKEN_TYPE_READ_ONLY = "Read only";
//    public static final String AUTHTOKEN_TYPE_READ_ONLY_LABEL = "Normal Access";
//mobileAdmin or ROLE_ADMIN or ROLE_CLIENT
//    public static final String AUTHTOKEN_TYPE_FULL_ACCESS = "ROLE_ADMIN";
//    public static final String AUTHTOKEN_TYPE_FULL_ACCESS_LABEL = "Admin Access";
    public static final String AUTHTOKEN_TYPE_POTLATCH = "potlatch.auth";
    public static final String AUTHTOKEN_TYPE_POTLATCH_LABEL = "Potlatch";

	protected static final String DEV_ADMIN_ACCOUNT_USER = "admin";
	protected static final String DEV_ADMIN_ACCOUNT_PWD = "admin";
	protected static final String DEV_USER_ACCOUNT_PWD = "pass";

	// constants for guis
	public static final String DIALOG_FRAGMENT_TAG = "dialog";
	public static final String PROMPT_FOR_LOGIN = "choose account";

}
