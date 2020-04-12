package com.courseracapstone.android;

import android.content.AsyncTaskLoader;
import android.content.Context;

import com.courseracapstone.android.service.DownloadBinder;
import com.courseracapstone.common.PagedCollection;

/**
 * A custom AsyncLoader which will load a paged collection.
 * 
 * @author xxx xxx
 *
 * @param <D>
 */
public class PagedCollectionAsyncLoader<D> extends AsyncTaskLoader<PagedCollection<D>> {

	protected DownloadBinder mDownloadBinder;
	
	public PagedCollectionAsyncLoader(Context context, DownloadBinder downloadBinder) {
		super(context);
		mDownloadBinder = downloadBinder;
	}


	@Override
	public PagedCollection<D> loadInBackground() {
		// TODO Auto-generated method stub
		return null;
	}

}
