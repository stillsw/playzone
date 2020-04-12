package com.courseracapstone.android.service;

import android.util.Log;

/**
 * Keeps running until stopped manually by a call to stopRunning()
 * To be extra sure, callers should test isUseable() before allocating 
 * a process
 * 
 * @author xxx xxx
 *
 */
public abstract class ContinuousRunner implements Runnable {

	private final String LOG_TAG = "Potlatch-"+this.getClass().getSimpleName();
	
	protected DownloadBinder mDownloadBinder;
	private volatile boolean mIsRunning = true;
	// keep a ref to the thread so can interrupt
	private Thread mThread;
	
	public ContinuousRunner(DownloadBinder downloadBinder) {
		mDownloadBinder = downloadBinder;
	}

	/**
	 * When the binder is killed, it will also stop this thread by 
	 * calling this method 
	 */
	public void stopRunning() {
		mIsRunning = false;
		if (mThread != null) {
			mThread.interrupt();
		}
	}

	/**
	 * Allows whatever is happening in the task to interrupt and continue
	 * (because interrupt without stopping just keeps going after logging)
	 */
	public void resetByInterrupting() {
		if (mThread != null) {
			mThread.interrupt();
		}
	}
	
	/**
	 * Allow caller to make sure this thread is still active, if for some
	 * reason it has failed another can be created in the binder's thread pool
	 * @return
	 */
	public boolean isUseable() {
		return mIsRunning && mThread != null;
	}
	
	@Override
	public void run() {
		mThread = Thread.currentThread();
		
		while (mIsRunning) {
			try {
				doRepetitiveTask();
			}
			catch (InterruptedException e) {
				// it's allowed for other processes to interrupt a sleeping task to get
				// it going again, when stopRunning() is called, the interrupt will
				// cause the end of the thread
			}
			catch (Exception e) {
				Log.e(LOG_TAG, "Unexpected error in run thread, recovering="+mIsRunning, e);
			}	
		}

		Log.d(LOG_TAG, "Thread is exiting normally");
		mThread = null;
	}

	protected abstract void doRepetitiveTask() throws InterruptedException;

}
