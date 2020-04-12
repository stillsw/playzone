package com.courseracapstone.android.accountsetup;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created with IntelliJ IDEA.
 * User: Udini
 * Date: 19/03/13
 * Time: 19:10
 */
public class PotlatchAuthenticatorService extends Service {
    @Override
    public IBinder onBind(Intent intent) {

        PotlatchAuthenticator authenticator = new PotlatchAuthenticator(this);
        return authenticator.getIBinder();
    }
}
