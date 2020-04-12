package com.courseracapstone.common;

import java.util.HashMap;

public class PotlatchConstants {

	// used when making a folder on android for capturing photos to
	public static final String APP_SHORT_NAME = "potlatch";
	public static final String MIME_PNG = "image/png";
	public static final String MIME_JPG = "image/jpeg";

	// the image io utilities on the server need a format param, not mime type
	public static final HashMap<String, String> MIME_FORMATS = new HashMap<String, String>();
	static {
		MIME_FORMATS.put(MIME_JPG, "jpg");
		MIME_FORMATS.put("image/gif", "gif");
		MIME_FORMATS.put(MIME_PNG, "png");
	}
	
	// values for gift inappropriate/obscene flags... retrofit got very upset when these were booleans
	public static final long RESPONSE_FLAG_OFF = 0L, RESPONSE_FLAG_ON = 1L;
	public static final long DEFAULT_UPDATE_INVERVAL = 5000 * 60; // 5 mins 
	
	// gift section types 
	public static final int NUM_GIFT_SECTION_MENU_TYPES = 4;
	
	// 3 kinds of response to a gift, plus one for admin removing it
	public static final int GIFT_RESPONSE_TYPE_TOUCHED_BY = 0
			, GIFT_RESPONSE_TYPE_INAPPROPRIATE_CONTENT = 1
			, GIFT_RESPONSE_TYPE_OBSCENE_CONTENT = 2
			, GIFT_REMOVED_BY_ADMIN = 3;

	public static final int GIFT_CHAIN_ORDERING_UNDER_THE_TOP = 1;
	
	// used by the application to limit requests, keep it small
	public static final String MAX_REQUEST_SIZE = "3MB";
	// used by android to limit file uploads
	public static final int MAX_IMAGE_SIZE = 2500000;
	
	public static final String ID_PARAMETER = "id";	
	public static final String DATA_PARAMETER = "data";
	public static final String TITLE_PARAMETER = "title";
	public static final String PAGE_PARAMETER = "page";
	public static final String PAGE_SIZE_PARAMETER = "pagesize";
	public static final String CHAIN_PARAMETER = "chainId";	
	public static final String MILLIS_PARAMETER = "millis";
	public static final String USER_NAME_PARAMETER = "username";
	public static final String PASSWORD_PARAMETER = "password";
	public static final String CLIENT_ID_PARAMETER = "client_id";
	public static final String FORMAT_PARAMETER = "format";
	public static final String GIFTS_LIST_TYPE_PARAMETER = "gifttype";
	public static final String TOKEN_PATH = "/oauth/token";

	// The path where we expect the PotlatchSvc to live
	public static final String POTLATCH_SVC_PATH = "/potlatch";

	// paths used in requests
	
	public static final String POTLATCH_GIFTS_USER_TYPES_PATH = POTLATCH_SVC_PATH + "/search/{page}/{pagesize}";
	public static final String POTLATCH_GIFTS_ADMIN_TYPE_PATH = "/potlatchadmin/search/{page}/{pagesize}";
	public static final String POTLATCH_PAGED_PATH = POTLATCH_SVC_PATH + "/page/{page}/{pagesize}";
	public static final String POTLATCH_TITLE_SEARCH_PATH = POTLATCH_PAGED_PATH + "/search/findByTitle";
	public static final String POTLATCH_CHAIN_SEARCH_PATH = POTLATCH_PAGED_PATH + "/search/findByChainId";
	public static final String POTLATCH_ID_PATH = POTLATCH_SVC_PATH + "/{id}";
	public static final String POTLATCH_DATA_PATH = POTLATCH_ID_PATH + "/data";
	public static final String POTLATCH_THUMBNAIL_DATA_PATH = POTLATCH_DATA_PATH + "/thumbnail";
	public static final String POTLATCH_SET_DATA_PATH = POTLATCH_DATA_PATH + "/{format}";
	public static final String POTLATCH_TOUCH_PATH = POTLATCH_ID_PATH + "/touched";
	public static final String POTLATCH_UNTOUCH_PATH = POTLATCH_ID_PATH + "/untouched";
	public static final String POTLATCH_INAPPROPRIATE_PATH = POTLATCH_ID_PATH + "/inappropriate";
	public static final String POTLATCH_NOT_INAPPROPRIATE_PATH = POTLATCH_ID_PATH + "/notinappropriate";
	public static final String POTLATCH_OBSCENE_PATH = POTLATCH_ID_PATH + "/obscene";
	public static final String POTLATCH_NOT_OBSCENE_PATH = POTLATCH_ID_PATH + "/notobscene";
	public static final String POTLATCH_REMOVE_OWN_GIFT_PATH = POTLATCH_ID_PATH + "/remove";
	public static final String POTLATCH_FILTER_CONTENT_PATH = POTLATCH_SVC_PATH + "/filter";
	public static final String POTLATCH_UNFILTER_CONTENT_PATH = POTLATCH_SVC_PATH + "/unfilter";
	public static final String POTLATCH_GET_INTERVAL_PATH = POTLATCH_SVC_PATH + "/interval";
	public static final String POTLATCH_UPDATE_INTERVAL_PATH = POTLATCH_GET_INTERVAL_PATH + "/{millis}";
	public static final String POTLATCH_USER_NOTICES_PATH = POTLATCH_SVC_PATH + "/notices";
	public static final String POTLATCH_TOP_GIVERS_PATH = POTLATCH_SVC_PATH + "/topGivers/{page}/{pagesize}";
	
	// the following only for admin users
	public static final String POTLATCH_FLAGGED_GIFTS_PATH = "/potlatchadmin/flagged";
	public static final String POTLATCH_REMOVE_GIFT_PATH = "/potlatchadmin/{id}" + "/remove";
	// and a path only for signing up for a new account
	public static final String POTLATCH_SIGN_UP_PATH = "/potlatchsignup";
	
	// test users, so shared for everywhere that needs them (server, server client test, android test)
	public static final String[] TEST_USER_NAMES = new String[] { "admin", "Bill Gatsby", "Larry the Lamb", "Hansel Pretzel" };
	public static final String[] TEST_USER_PWDS = new String[] { "admin", "pass", "pass", "pass" };
	public static final int ADMIN_TEST_USER_IDX = 0;

	// security
	public static final String ADMIN_CLIENT_ID = "mobileAdmin";
	public static final String NORMAL_CLIENT_ID = "mobile";
}
