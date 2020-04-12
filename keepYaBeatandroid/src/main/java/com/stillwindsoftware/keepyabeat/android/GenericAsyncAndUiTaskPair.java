package com.stillwindsoftware.keepyabeat.android;

import android.os.AsyncTask;
import android.util.Log;

import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.platform.AndroidResourceManager;
import com.stillwindsoftware.keepyabeat.utils.ParameterizedCallback;

/**
 * A class that allows 2 callbacks to be put together so that the first is run in the background, and the 2nd is run
 * in the ui thread once the 1st is done. Either can be parametized, and the 2nd can also receive its parameter from
 * the 1st.
 */
public class GenericAsyncAndUiTaskPair extends AsyncTask<ParameterizedCallback, Void, ParameterizedCallback> {

    private static final String LOG_TAG = "KYB-"+GenericAsyncAndUiTaskPair.class.getSimpleName();

    private final AndroidGuiManager mGuiManager;

    public GenericAsyncAndUiTaskPair(AndroidGuiManager guiManager) {
        mGuiManager = guiManager;
    }

    @Override
    protected ParameterizedCallback doInBackground(ParameterizedCallback... callbacks) {

        // validate options
        if (callbacks.length > 1 && callbacks[1].isForResult()) {
            // don't bother to log, this is a program bug and should never happen in prod
            throw new IllegalArgumentException("GenericAsyncAndUiTaskPair: 2nd callback must not request a result object (must init with false)");
        }

        // since db access will be happening, don't allow more than one of these to run in the background at once
        if (mGuiManager.isExclusiveOperationInProcess()) {
            final String msg = "doInBackground: exclusive operation already in process, if legit a better control is needed";
            AndroidResourceManager.loge(LOG_TAG, msg);
//            throw new IllegalStateException(msg);
        }

        mGuiManager.setExclusiveOperationInProcess();

        Object result = null;
        try {
            if (callbacks[0].isForResult()) {
                result = callbacks[0].runForResult();
            }
            else {
                callbacks[0].run();
            }
        }
        catch (Exception e) {
            if (mGuiManager.getResourceManager().isInTransaction()) {
                AndroidResourceManager.loge(LOG_TAG, "doInBackgroundon: produced exception, and already in a transaction, undo it", e);
                mGuiManager.getResourceManager().rollbackTransaction();
            }
            else {
                AndroidResourceManager.loge(LOG_TAG, "doInBackground: produced exception", e);
            }
        }
        finally {
            mGuiManager.unsetExclusiveOperationInProcess(); // always unset
        }

        if (callbacks.length > 1) {
            if (result != null) {
                callbacks[1].setParam(result);
            }
            return callbacks[1];
        }
        else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(ParameterizedCallback callback) {
        if (callback != null) {
            try {
                callback.run();
            }
            catch (Exception e) {
                if (mGuiManager.getResourceManager().isInTransaction()) {
                    AndroidResourceManager.loge(LOG_TAG, "onPostExecute: callback produced exception, and already in a transaction, undo it", e);
                    mGuiManager.getResourceManager().rollbackTransaction();
                }
                else {
                    AndroidResourceManager.loge(LOG_TAG, "onPostExecute: callback produced exception", e);
                }
            }
        }
    }

}


