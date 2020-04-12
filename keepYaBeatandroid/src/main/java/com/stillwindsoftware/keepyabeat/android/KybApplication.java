package com.stillwindsoftware.keepyabeat.android;

import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.ads.consent.DebugGeography;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.stillwindsoftware.keepyabeat.BuildConfig;
import com.stillwindsoftware.keepyabeat.android.DataLoaderFragment.DataInstaller;
import com.stillwindsoftware.keepyabeat.gui.KybActivity;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.platform.SettingsManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Maintains any state that is needed for the application.
 * For instance, whether the Welcome Screen has been displayed (would be displayed every time the user re-enters the app 
 * if requested to do so in PlayRhythmsActivity which is called first).
 */
public class KybApplication extends Application implements IabBroadcastReceiver.IabBroadcastListener {

	private static final String LOG_TAG = "KYB-"+KybApplication.class.getSimpleName();
    private static final String ADMOB_PUB_ID = "pub-hidden";
    private static final String ADMOB_APP_ID = "ca-app-" + ADMOB_PUB_ID + "~9919165740";
    public static final String ZTE_DEVICE = "AACFDD7BE9D3A73CD6A7F3412885C0EB";
    public static final String NEXUS_7_DEVICE = "DC0C4EA1C6127311CAC9C8F9ECC1898C";

    // by default assume it's to be shown (will cause test for preference at least)
	private boolean mShowWelcomeScreen = true;
	
	// by default need to do a data load (until first run of main activity... for now at least)
	private boolean mIsInitialised = false;
	// the database installation will get a handle on the data installer for callback methods
	private DataInstaller mDataInstaller;

	// core kyb functionality needs a resource manager, this one is lightweight
	// and doesn't hold much state, just passes calls through where needed
	// it can easily be disposed and recreated as required (see onLowMemory()/onTrimMemory())
	private AndroidResourceManager mResourceManager;
    // catch uncaught exceptions that were not possible to catch another way
    private Thread.UncaughtExceptionHandler mDefaultExceptionHandler;

    // in-app billing
    private boolean mIsPremium = false, // in-app billing, always false at start, activity queries the server sets if premium bought
                    mIsListeningToBilling = false;
    private String[] mIabObfuscatedPublicKeyPieces = new String[]{ // to hide the key, made 28 pieces, then swapped each pair, so they're in order: 2, 1, 4, 3... etc
                "", "", "EAjJK/", "", "pieces hidden"};
    // in-app billing, taken from TrivialDrive sample
    private IabHelper mHelper;
    private IabBroadcastReceiver mBroadcastReceiver;
    private IabHelper.QueryInventoryFinishedListener mGotInventoryListener;
    private IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener;
    // (arbitrary) request code for the purchase flow
    private static final int RC_REQUEST = 10001;
    private static final String sPremiumPayload = ""; // not sure if there's a cross-device way to have this work, as don't have any server app
    private boolean mAwaitingPurchaseThankYou = false;
    private boolean mIsAdsInitialised = false;
    private boolean mIsAwaitingConsentInfo = true;
    private boolean mHasConsentToShowAds = false;
    private boolean mIsConsentToShowAdsNeeded = true; // will be unset if location not in eea
    private boolean mIsConsentForNonPersonalizedAds = false;

    public KybApplication() {
		AndroidResourceManager.logd(LOG_TAG, "init");
	}

	public synchronized AndroidResourceManager getResourceManager() {
		if (mResourceManager == null) {
			mResourceManager = new AndroidResourceManager(this);
		}
		return mResourceManager;
	}

	@Override
	public void onLowMemory() {
		AndroidResourceManager.logd(LOG_TAG, "onLowMemory: system needs resources, releasing...");
		if (mResourceManager != null) {
			mResourceManager.releaseResources();
		}
		super.onLowMemory();
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onTrimMemory(int level) {
		if (mResourceManager != null) {
			mResourceManager.releaseResources(level);
		}
		super.onTrimMemory(level);
	}

	/**
	 * Called from the initial activity to detect to show welcome screen
	 * @return
	 */
	public boolean isShowWelcomeScreen() {
		return mShowWelcomeScreen;
	}

	/**
	 * Called either because the welcome screen has been displayed or because it's not wanted
	 */
	public void setShowWelcomeScreenDone() {
		this.mShowWelcomeScreen = false;
	}

    /**
     * Called from playRhythmsActivity.initDataLoader() to test if the data has been initialized.
     * First tests what version code (taken from gradle build config) has been installed (if any)
     * and if no further load is needed it unsets it.
     * Take care to keep the version numbers up to date in Gradle, if this test fails the db won't
     * get upgraded if it needs it.
     * @return
     */
	public boolean isInitialised() {

        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            int versionCode = packageInfo.versionCode;
            String versionName = packageInfo.versionName;

            SettingsManager settings = (SettingsManager) mResourceManager.getPersistentStatesManager();
            int installedVersion = settings.getGradleVersionCodeInstalled();

            mIsInitialised = installedVersion >= versionCode;

            AndroidResourceManager.logd(LOG_TAG, String.format("isInitialised: package info version=%s (%s) settings version=%s (initialized set to %s)"
                    , versionCode, versionName, installedVersion, mIsInitialised));
        }
        catch (PackageManager.NameNotFoundException e) {
            AndroidResourceManager.loge(LOG_TAG, "isInitialised: couldn't determine package info");
        }

		return mIsInitialised;
	}

	public void setInitialised(boolean isInitialised) {
		this.mIsInitialised = isInitialised;
		mDataInstaller = null;

        if (isInitialised) {
            PackageManager packageManager = getPackageManager();
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
                int versionCode = packageInfo.versionCode;
                String versionName = packageInfo.versionName;

                SettingsManager settings = (SettingsManager) mResourceManager.getPersistentStatesManager();
                settings.setGradleVersionCodeInstalled(versionCode);

                AndroidResourceManager.logd(LOG_TAG, String.format("setInitialised: storing installation version=%s (%s)"
                        , versionCode, versionName));
            }
            catch (PackageManager.NameNotFoundException e) {
                AndroidResourceManager.loge(LOG_TAG, "setInitialised: couldn't determine package info");
            }
        }
    }

	public DataInstaller getDataInstaller() {
		return mDataInstaller;
	}

	/**
	 * Called from the DataLoaderFragment's async load task during <init>
	 * @param dataInstaller
	 */
	public void setDataInstaller(DataInstaller dataInstaller) {
		this.mDataInstaller = dataInstaller;
	}

    @Override
    public void onCreate() {
        super.onCreate();

        // store the default handler for uncaught exceptions
        mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {

                // propagate the error on if it isn't one that's been handled elsewhere

                if (ex instanceof KybActivity.CaughtUnstoppableException) {
                    AndroidResourceManager.loge(LOG_TAG, "uncaughtException: trapped in the application, it's handled already so don't log or bring up a force close dialog. ex="+ex);
                    // not sure if this will work in all versions... but this is what 4.4.4 r1 default handler does in its finally clause
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
                else if (mDefaultExceptionHandler != null) {
                    mDefaultExceptionHandler.uncaughtException(thread, ex);
                }
            }
        });


    }

    public boolean isPremium() {
        return mIsPremium;
    }

    private String getBillingKey() {

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < mIabObfuscatedPublicKeyPieces.length; i += 2) {
            String piece = mIabObfuscatedPublicKeyPieces[i];
            builder.append(piece);
            piece = mIabObfuscatedPublicKeyPieces[i-1];
            builder.append(piece);
        }

        String s = builder.toString();
        return s;
    }

    /**
     * Called from KybActivity.onCreate() if premium is not set, but only want to do anything the first time in
     */
    public void listenForBillingChanges() {

        if (mIsListeningToBilling) {
            return;                     // already doing it
        }

        mIsListeningToBilling = true;   // don't do this again

        String bk = getBillingKey();
        AndroidResourceManager.logd(LOG_TAG, "listenForBillingChanges: create helper");
        mHelper = new IabHelper(this, bk);

        if (BuildConfig.ALLOW_CONSUME_PREMIUM) { // ie. debug build
            mHelper.enableDebugLogging(true);
        }

        // Listener that's called when we finish querying the items and subscriptions we own
        mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                // Is it a failure?
                if (result.isFailure()) {
                    AndroidResourceManager.logw(LOG_TAG, "onQueryInventoryFinished: Failed to query inventory: " + result);
                    return;
                }

                AndroidResourceManager.logd(LOG_TAG, "onQueryInventoryFinished: Query inventory was successful.");

                // Check for items we own. Notice that for each purchase, we check
                // the developer payload to see if it's correct! See
                // verifyDeveloperPayload().
                Purchase premiumPurchase = inventory.getPurchase(getPremiumId());

                if (BuildConfig.ALLOW_CONSUME_PREMIUM) { // ie. debug build
                    mTestHoldPremiumPurchase = premiumPurchase;         // need to keep the ref for consume
                }

                if (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase)) {
                    premiumBought(false);
                }
                else {
                    AndroidResourceManager.logd(LOG_TAG, "onQueryInventoryFinished: done, no premium bought");
                }
            }
        };

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                AndroidResourceManager.logd(LOG_TAG, "onIabSetupFinished:");

                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    AndroidResourceManager.logw(LOG_TAG, "onIabSetupFinished: Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                // We register the receiver here instead of as a <receiver> in the Manifest
                // The receiver must be registered after
                // IabHelper is setup, but before first call to getPurchases().
                mBroadcastReceiver = new IabBroadcastReceiver(KybApplication.this);
                IntentFilter broadcastFilter = new IntentFilter(IabBroadcastReceiver.ACTION);
                registerReceiver(mBroadcastReceiver, broadcastFilter);

                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                AndroidResourceManager.logd(LOG_TAG, "onIabSetupFinished: Setup successful. Querying inventory.");
                try {
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    AndroidResourceManager.logw(LOG_TAG, "onIabSetupFinished: Error querying inventory. Another async operation in progress.");
                }
            }
        });

        // test for consent to show ads (EU needs this)
        ConsentInformation consentInformation = ConsentInformation.getInstance(this);
        if (!BuildConfig.SHOW_REAL_ADS) { // ie. debug build
            consentInformation.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
            consentInformation.addTestDevice(NEXUS_7_DEVICE);
            consentInformation.addTestDevice(ZTE_DEVICE);
            String deviceId = md5(Settings.Secure.getString(getContentResolver(),
                                                        Settings.Secure.ANDROID_ID)).toUpperCase();
            if (!deviceId.equals(ZTE_DEVICE)) {
                AndroidResourceManager.logd(LOG_TAG, "listenForBillingChanges: spoofing in EU for consent processing");
                consentInformation.setDebugGeography(DebugGeography.DEBUG_GEOGRAPHY_EEA);   // to be able to test consent and ads showing/not showing
            }
            else if (!deviceId.equals(ZTE_DEVICE)) {
                AndroidResourceManager.logd(LOG_TAG, "listenForBillingChanges: not spoofing in EU on unknown test device="+deviceId);
            }
        }
        String[] webProperties = { ADMOB_PUB_ID }; // publisher admob id
        consentInformation.requestConsentInfoUpdate(webProperties, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                handleConsentStatus(consentStatus);
            }

            @Override
            public void onFailedToUpdateConsentInfo(String s) {
                mIsAwaitingConsentInfo = false; // User's consent state failed to update.
                AndroidResourceManager.loge(LOG_TAG, String.format("onFailedToUpdateConsentInfo: Error querying consent status = %s", s));
            }
        });
    }

    private static final String md5(final String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++) {
                String h = Integer.toHexString(0xFF & messageDigest[i]);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
        }
        return "";
    }
    public void handleConsentStatus(ConsentStatus consentStatus) {
        // logic from https://developers.google.com/admob/android/eu-consent
        ConsentInformation consentInformation = ConsentInformation.getInstance(this);

        boolean isConsentingPersonalizedAds = consentStatus.equals(ConsentStatus.PERSONALIZED);
        mIsConsentForNonPersonalizedAds = consentStatus.equals(ConsentStatus.NON_PERSONALIZED);
        boolean isConsentUnknown = consentStatus.equals(ConsentStatus.UNKNOWN);
        boolean isLocationEeaOrUnknown = consentInformation.isRequestLocationInEeaOrUnknown();

        AndroidResourceManager.logd(LOG_TAG, String.format("handleConsentStatus: personal=%s, non-personal=%s, unknown=%s, location eea or unknown=%s",
                isConsentingPersonalizedAds, mIsConsentForNonPersonalizedAds, isConsentUnknown, isLocationEeaOrUnknown));

        mIsAwaitingConsentInfo = false; // User's consent state successfully updated (used to let front-end know can query consent from this class)
        mHasConsentToShowAds = isConsentingPersonalizedAds || mIsConsentForNonPersonalizedAds;
        mIsConsentToShowAdsNeeded = isLocationEeaOrUnknown;

        if (hasConsentToShowAdsOrNotRequired()) { // tests these 2 flags

            AndroidResourceManager.logd(LOG_TAG, "handleConsentStatus: can show ads, init in background (unless already init'd before");

            if (!mIsAdsInitialised) {
                initAdsInBackground();
            }
        }
    }

    void initAdsInBackground() {
        // start as a background task to init the ads, it takes too long at startup
        mResourceManager.getGuiManager().runAsyncProtectedTaskThenUiTask(
                new ParameterizedCallback(true) {
                    @Override
                    public Object runForResult() {

                        MobileAds.initialize(KybApplication.this, ADMOB_APP_ID);
                        return null;
                    }
                },
                new ParameterizedCallback(false) {
                    @Override
                    public void run() {
                        mIsAdsInitialised = true;
                    }
                }
        );
    }

    private void premiumBought(boolean fromMenuOption) {
        mIsPremium = true;

        if (!BuildConfig.ALLOW_CONSUME_PREMIUM) { // ie. NOT debug build
            stopListeningForBillingChanges();
        }

        KybActivity latestActivity = mResourceManager.getLatestActivity();
        AndroidResourceManager.logd(LOG_TAG, "premiumBought: premium bought, stop listening and update UI, activity="+latestActivity+" fromMenuOption="+fromMenuOption);
        if (latestActivity != null) {
            latestActivity.updateUI(fromMenuOption);
        }
        else { // in all likelihood there's no activity here, so set a flag for the activity when it resumes
            mAwaitingPurchaseThankYou = fromMenuOption;
        }
    }

    private boolean verifyDeveloperPayload(Purchase premiumPurchase) {
        return premiumPurchase.getDeveloperPayload().equals(sPremiumPayload);
    }

    @NonNull
    private String getPremiumId() {
        // obfuscate the string
        StringBuilder premium = new StringBuilder();
        premium.append('p');
        premium.append('r');
        premium.append('e');
        premium.append('m');
        premium.append('i');
        premium.append('u');
        premium.append('m');
        return premium.toString();
    }

    @Override
    public void receivedBroadcast() {
        // Received a broadcast notification that the inventory of items has changed
        AndroidResourceManager.logd(LOG_TAG, "receivedBroadcast: Querying inventory.");
        try {
            mHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            AndroidResourceManager.logw(LOG_TAG, "receivedBroadcast: Error querying inventory. Another async operation in progress.");
        }

    }

    /**
     * Seems odd in Android, but want to make sure the receiver is removed just in case can do that while app is being removed
     * @throws Throwable
     */
    @Override
    protected void finalize() throws Throwable {
        stopListeningForBillingChanges();
        super.finalize();
    }

    private void stopListeningForBillingChanges() {
        try {
            if (mBroadcastReceiver != null) {
                unregisterReceiver(mBroadcastReceiver);
            }

            if (mHelper != null) {
                mHelper.disposeWhenFinished();
                mHelper = null;
            }
        } catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "stopListeningForBillingChanges: exception while closing down billing processing", e);
        }
    }


    public void sellPremium(KybActivity kybActivity) {
        if (mPurchaseFinishedListener == null) {
            mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
                public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
                    // if we were disposed of in the meantime, quit.
                    if (mHelper == null) return;

                    if (result.isFailure()) {
                        AndroidResourceManager.logw(LOG_TAG, "onIabPurchaseFinished: failure");
                        return;
                    }

                    AndroidResourceManager.logd(LOG_TAG, "onIabPurchaseFinished: success, purchase: " + purchase);

                    if (!verifyDeveloperPayload(purchase)) {
                        AndroidResourceManager.logw(LOG_TAG, "onIabPurchaseFinished: Authenticity verification failed.");
                        return;
                    }

                    if (purchase.getSku().equals(getPremiumId())) {
                        // bought the premium upgrade!
                        AndroidResourceManager.logd(LOG_TAG, "Purchase is premium upgrade");
                        premiumBought(true);
                    }
                }
            };
        }

        try {
            mHelper.launchPurchaseFlow(kybActivity, getPremiumId(), RC_REQUEST,
                    mPurchaseFinishedListener, sPremiumPayload);
            AndroidResourceManager.logw(LOG_TAG, "sellPremium: purchase flow launched");
        } catch (IabHelper.IabAsyncInProgressException e) {
            AndroidResourceManager.logw(LOG_TAG, "sellPremium: Error querying inventory. Another async operation in progress.");
        } catch (Exception e) {
            AndroidResourceManager.logw(LOG_TAG, "sellPremium: Error querying inventory", e);
        }
    }


    public boolean handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        if (mHelper == null) return false;

        // Pass on the activity result to the helper for handling
        if (mHelper.handleActivityResult(requestCode, resultCode, data)) {
            return true;
        }
        else {
            AndroidResourceManager.logd(LOG_TAG, "handleOnActivityResult: not handled by IABUtil.");
            return false;
        }
    }

    /* only here to allow re-tests, which is debug only */
    private Purchase mTestHoldPremiumPurchase;
    public void consumePremium() {
        if (!BuildConfig.ALLOW_CONSUME_PREMIUM) { // ie. NOT debug build
            AndroidResourceManager.loge(LOG_TAG, "consumePremium: should never be called in release build!!");
            return;
        }

        if (mTestHoldPremiumPurchase == null) {
            AndroidResourceManager.loge(LOG_TAG, "consumePremium: can't do it, no purchase in memory");
            return;
        }

        if (mHelper == null) {
            AndroidResourceManager.loge(LOG_TAG, "consumePremium: can't do it, no helper present");
            return;
        }

        try {
            mHelper.consume(mTestHoldPremiumPurchase);
            mTestHoldPremiumPurchase = null;
        } catch (IabException e) {
            AndroidResourceManager.loge(LOG_TAG, "consumePremium: Error querying inventory", e);
        }
    }

    public boolean takeAwaitingPurchaseThankYou() {
        boolean b = mAwaitingPurchaseThankYou;
        mAwaitingPurchaseThankYou = false; // don't show again
        return b;
    }

    public boolean isAwaitingConsentInfoRequest() {
        return mIsAwaitingConsentInfo;
    }

    public boolean hasConsentToShowAdsOrNotRequired() {
        return mHasConsentToShowAds || !mIsConsentToShowAdsNeeded;
    }

    public boolean isConsentForNonPersonalizedAds() {
        return mIsConsentForNonPersonalizedAds;
    }
}
