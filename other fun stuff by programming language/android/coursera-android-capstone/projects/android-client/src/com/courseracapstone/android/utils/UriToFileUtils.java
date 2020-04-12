package com.courseracapstone.android.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

/**
 * Used by ImageDownloadBinder when it needs to convert a Uri chosen by the user into a file.
 * Because different methods work only on certain source types, the first method is to try to
 * get a file. If all that fails, and null is returned. Then the caller can call the create
 * temp file method, but it will then be the caller's responsibility to clear that file down again.
 * 
 * Note, of the 3 private methods, 1 & 3 are very similar, only they query different columns. 
 * Method 2 is only for KitKat+
 * 
 * @author xxx xxx
 */
public class UriToFileUtils {

	private static final String LOG_TAG = "Potlatch-"+UriToFileUtils.class.getSimpleName();
	
	/**
	 * Try the 3 methods
	 * @param data
	 * @param context
	 * @return
	 */
	public static File getFileForUri(Uri data, Context context) {

		File file = getFileMethod1(data, context);
		
		if (file == null) {
			file = getFileMethod2(data, context);
		}
		
		if (file == null) {
			file = getFileMethod3(data, context);
		}
		
		return file;
	}
	
	/**
	 * After failing to get a file via the previous method, a temp file is supplied
	 * to write it to.
	 * 
	 * @param data
	 * @param resolver
	 * @param context
	 * @param outFile
	 * @throws IOException 
	 */
	public static void writeUriToFile(Uri data, ContentResolver resolver, Context context, File outFile) throws IOException {
		
		FileOutputStream fos = null;
		InputStream is = null;
		
		try {
			try {
				fos = new FileOutputStream(outFile);
			} 
			catch (FileNotFoundException e) {
				Log.e(LOG_TAG, "writeUriToFile: failed to open fos", e);
				throw e;
			}
			
			try {
				is = resolver.openInputStream(data);
			}
			catch (FileNotFoundException e) {
				Log.e(LOG_TAG, "writeUriToFile: failed to open is", e);
				throw e;
			}
			
			// boilerplate read/write code
			int read = 0;
			byte[] bytes = new byte[1024];
			
			while ((read = is.read(bytes)) != -1) {
				fos.write(bytes, 0, read);
			}
			
		}
		finally {
			try {
				if (fos != null) {
					fos.close();
				}
			}
			catch (Exception e) {
				Log.d(LOG_TAG, "writeUriToFile: failed to close fos");
			}

			try {
				if (is != null) {
					is.close();
				}
			}
			catch (Exception e) {
				Log.d(LOG_TAG, "writeUriToFile: failed to close is");
			}
		}
		
	}
	
	
	/**
	 * https://stackoverflow.com/questions/5657411/android-getting-a-file-uri-
	 * from-a-content-uri
	 * 
	 * method 1 & 3 work for choose Gallery
	 * uri format = content://media/external/images/media/39
	 * 
	 * @param data
	 * @param context 
	 * @return
	 */
	private static File getFileMethod1(Uri data, Context context) {
		
		String filePath = null;

		if (data != null && "content".equals(data.getScheme())) {
			Cursor cursor = context.getContentResolver().query(data,
							new String[] { android.provider.MediaStore.Images.ImageColumns.DATA },
							null, null, null);
			cursor.moveToFirst();
			filePath = cursor.getString(0);
			cursor.close();
		} 

		if (filePath != null) {
			File file = new File(filePath);
			
			if (file.exists()) {
				Log.d(LOG_TAG, "getFileMethod1: returned valid file "+file);
				return new File(filePath);
			}
			else {
				Log.d(LOG_TAG, "getFileMethod1: returned NON valid file "+file);
			}
		}
		else {
			Log.d(LOG_TAG, "getFileMethod1: failed to get a file");
		}
		
		return null;
	}

	/**
	 * https://stackoverflow.com/questions/3401579/get-filename-and-path-from-
	 * uri-from-mediastore kitkat+ only
	 * 
	 * Works for choose Images->Downloads
	 * 
	 * @param contentUri
	 * @param context 
	 * @return
	 */
	private static File getFileMethod2(Uri contentUri, Context context) {// Will return "image:x*"
		
		Cursor cursor = null;
		
		try {
			if (!DocumentsContract.isDocumentUri(context, contentUri)) {
				Log.d(LOG_TAG, "getFileMethod2: not a documentsContract uri");
				return null;
			}
			
			String wholeID = DocumentsContract.getDocumentId(contentUri);

			// Split at colon, use second item in the array
			String id = wholeID.split(":")[1];

			String[] column = { MediaStore.Images.Media.DATA };

			// where id is equal to
			String sel = MediaStore.Images.Media._ID + "=?";

			cursor = context.getContentResolver().query(
					MediaStore.Images.Media.EXTERNAL_CONTENT_URI, column, sel,
					new String[] { id }, null);

			String filePath = "";

			int columnIndex = cursor.getColumnIndex(column[0]);

			if (cursor.moveToFirst()) {
				filePath = cursor.getString(columnIndex);
			}

			if (filePath != null) {
				File file = new File(filePath);
				
				if (file.exists()) {
					Log.d(LOG_TAG, "getFileMethod2: returned valid file "+file);
					return new File(filePath);
				}
				else {
					Log.d(LOG_TAG, "getFileMethod2: returned NON valid file "+file);
				}
			}
			else {
				Log.d(LOG_TAG, "getFileMethod2: failed to get a file");
			}
			
		}
		catch (Exception e) {
			Log.w(LOG_TAG, "getFileMethod2: threw an exception", e);
		}
		finally {
			if (cursor != null) {
				cursor.close();				
			}
		}
		
		return null;
	}

	/**
	 * https://stackoverflow.com/questions/3401579/get-filename-and-path-from-uri-from-mediastore
	 * 
	 * method 1 & 3 work for choose Gallery
	 * uri format = content://media/external/images/media/39
	 * 
	 * @param contentUri
	 * @param context 
	 * @return
	 */
	private static File getFileMethod3(Uri contentUri, Context context) {
		
		Cursor cursor = null;
		try {
			String[] proj = { MediaStore.Images.Media.DATA };
			cursor = context.getContentResolver().query(contentUri, proj, null,
					null, null);
			int column_index = cursor
					.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
			cursor.moveToFirst();
			String filePath = cursor.getString(column_index);
			
			if (filePath != null) {
				File file = new File(filePath);
				
				if (file.exists()) {
					Log.d(LOG_TAG, "getFileMethod3: returned valid file "+file);
					return new File(filePath);
				}
				else {
					Log.d(LOG_TAG, "getFileMethod3: returned NON valid file "+file);
				}
			}
			else {
				Log.d(LOG_TAG, "getFileMethod3: failed to get a file");
			}
			
		}
		catch (Exception e) {
			Log.w(LOG_TAG, "getFileMethod2: threw an exception", e);
		}
		finally {
			if (cursor != null) {
				cursor.close();				
			}
		}
		
		return null;
	}

//	/**
//	 * https://stackoverflow.com/questions/2975197/convert-file-uri-to-file-in-android
//	 * & https://stackoverflow.com/questions/7908193/how-to-access-downloads-folder-in-android
//	 * 
//	 * This one doesn't work at all, but left it for future ideas
//	 * 
//	 * @param contentUri
//	 * @return
//	 */
//	public String getPathMethod4(Uri contentUri) {
//		File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//		Log.d(LOG_TAG, "getPathMethod4: downloads dir = "+downloads);
//
//		String fileName = contentUri.getPath();
//		
//		if (!TextUtils.isEmpty(fileName)) {
//			File inDownloads = new File(downloads, fileName);
//			
//			if (inDownloads.exists()) {
//				return inDownloads.getAbsolutePath();
//			}
//			else {
//				return "NOT EXISTS at "+inDownloads.getAbsolutePath();
//			}
//		}
//		
//		return null;
//	}
	
}
