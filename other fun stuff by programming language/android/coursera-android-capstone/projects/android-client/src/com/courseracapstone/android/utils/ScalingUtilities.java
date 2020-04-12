/*
 * Copyright (c) 2010, Sony Ericsson Mobile Communication AB. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this
 *      list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following disclaimer in the documentation
 *      and/or other materials provided with the distribution.
 *    * Neither the name of the Sony Ericsson Mobile Communication AB nor the names
 *      of its contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.courseracapstone.android.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

/**
 * Class containing static utility methods for bitmap decoding and scaling
 *
 * @author Andreas Agvard (andreas.agvard@sonyericsson.com)
 * 
 * @author xxx xxx : added decoding from file, with dimensions and with proportional size
 */
public class ScalingUtilities {
	private static final String LOG_TAG = "Potlatch-"+ScalingUtilities.class.getSimpleName();

	
    public static Bitmap createScaledBitmap(File file, float proportion, ScalingLogic scalingLogic, Options options) {
    	
        int dstWidth = (int) (options.outWidth * proportion);
        int dstHeight = (int) (options.outHeight * proportion);
        
        Log.d(LOG_TAG, String.format("createScaledBitmap.#fileByProportion: src w/h=%s/%s, dst=%s/%s, mime=%s",
        		options.outWidth, options.outHeight, dstWidth, dstHeight, options.outMimeType));
        
        return createScaledBitmap(file, dstWidth, dstHeight, scalingLogic, options);
    }
	
    public static Bitmap createScaledBitmap(File file, int dstWidth, int dstHeight, ScalingLogic scalingLogic) {

        Options options = getScalingOptionsForFile(file);
        if (options == null) {
        	// reported it already
        	return null;
        }

		return createScaledBitmap(file, dstWidth, dstHeight, scalingLogic, options);

    }

	private static Bitmap createScaledBitmap(File file, int dstWidth,
			int dstHeight, ScalingLogic scalingLogic, Options options) {
		
		// add sampling size to options
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth,
                dstHeight, scalingLogic);
		
		// go round again, for real this time
        FileInputStream fis;
		try {
			fis = new FileInputStream(file.getAbsoluteFile());
		} 
		catch (FileNotFoundException e) {
			Log.e(LOG_TAG, "createScaledBitmap: no file", e);
			return null;
		}		

        try {
			return BitmapFactory.decodeStream(fis, null, options);
		} 
        finally {
			try {
				fis.close();
			} 
			catch (IOException e) {
				Log.w(LOG_TAG, "createScaledBitmap: error closing file inputstream", e);
			}
		}
	}

	public static Options getScalingOptionsForFile(File file) {
		Options options = new Options();
        options.inJustDecodeBounds = true;
        
        FileInputStream testFis;
		try {
			testFis = new FileInputStream(file.getAbsoluteFile());
		} 
		catch (FileNotFoundException e) {
			Log.e(LOG_TAG, "getScalingOptionsForFile: couldn't open file inputstream", e);
			return null;
		}
		
        BitmapFactory.decodeStream(testFis, null, options);
        options.inJustDecodeBounds = false;

        try {
        	testFis.close();
		} 
        catch (IOException e) {
			Log.w(LOG_TAG, "getScalingOptionsForFile: error closing file inputstream", e);
		}
		return options;
	}

    public static Bitmap createScaledBitmap(ContentResolver resolver, Uri uri, int dstWidth, int dstHeight, ScalingLogic scalingLogic) {

        Options options = getScalingOptionsForContentUri(resolver, uri);
        if (options == null) {
        	// reported it already
        	return null;
        }

		return createScaledBitmap(resolver, uri, dstWidth, dstHeight, scalingLogic, options);

    }

	public static Options getScalingOptionsForContentUri(ContentResolver resolver, Uri uri) {
		Options options = new Options();
        options.inJustDecodeBounds = true;
        
        InputStream testFis;
		try {
			testFis = resolver.openInputStream(uri);
		} 
		catch (FileNotFoundException e) {
			Log.e(LOG_TAG, "getScalingOptionsForContentUri: couldn't open content inputstream", e);
			return null;
		}
		
        BitmapFactory.decodeStream(testFis, null, options);
        options.inJustDecodeBounds = false;

        try {
        	testFis.close();
		} 
        catch (IOException e) {
			Log.w(LOG_TAG, "getScalingOptionsForContentUri: error closing content inputstream", e);
		}
		return options;
	}

	private static Bitmap createScaledBitmap(ContentResolver resolver, Uri uri, int dstWidth,
			int dstHeight, ScalingLogic scalingLogic, Options options) {
		
		// add sampling size to options
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth,
                dstHeight, scalingLogic);
		
		// go round again, for real this time
        InputStream fis;
		try {
			fis = resolver.openInputStream(uri);
		} 
		catch (FileNotFoundException e) {
			Log.e(LOG_TAG, "createScaledBitmap: no file", e);
			return null;
		}		

        try {
			return BitmapFactory.decodeStream(fis, null, options);
		} 
        finally {
			try {
				fis.close();
			} 
			catch (IOException e) {
				Log.w(LOG_TAG, "createScaledBitmap: error closing content inputstream", e);
			}
		}
	}

	public static int getFileLenFromUri(ContentResolver resolver, Uri uri) {
		
        InputStream fis = null;
		try {
			fis = resolver.openInputStream(uri);
			return fis.available();
		} 
		catch (Exception e) {
			Log.e(LOG_TAG, "getFileLenFromUri: failed to report available.len", e);
			return -1;
		}		
        finally {
			try {
				if (fis != null) {
					fis.close();
				}
			} 
			catch (IOException e) {
				Log.w(LOG_TAG, "getFileLenFromUri: error closing content inputstream", e);
			}
		}
	}

    /**
     * Utility function for decoding an image resource. The decoded bitmap will
     * be optimized for further scaling to the requested destination dimensions
     * and scaling logic.
     *
     * @param res The resources object containing the image data
     * @param resId The resource id of the image data
     * @param dstWidth Width of destination area
     * @param dstHeight Height of destination area
     * @param scalingLogic Logic to use to avoid image stretching
     * @return Decoded bitmap
     */
    public static Bitmap decodeResource(Resources res, int resId, int dstWidth, int dstHeight,
            ScalingLogic scalingLogic) {
        Options options = new Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, dstWidth,
                dstHeight, scalingLogic);
        Bitmap unscaledBitmap = BitmapFactory.decodeResource(res, resId, options);

        return unscaledBitmap;
    }

    /**
     * Utility function for creating a scaled version of an existing bitmap
     *
     * @param unscaledBitmap Bitmap to scale
     * @param dstWidth Wanted width of destination bitmap
     * @param dstHeight Wanted height of destination bitmap
     * @param scalingLogic Logic to use to avoid image stretching
     * @return New scaled bitmap object
     */
    public static Bitmap createScaledBitmap(Bitmap unscaledBitmap, int dstWidth, int dstHeight,
            ScalingLogic scalingLogic) {
        Rect srcRect = calculateSrcRect(unscaledBitmap.getWidth(), unscaledBitmap.getHeight(),
                dstWidth, dstHeight, scalingLogic);
        Rect dstRect = calculateDstRect(unscaledBitmap.getWidth(), unscaledBitmap.getHeight(),
                dstWidth, dstHeight, scalingLogic);
        Bitmap scaledBitmap = Bitmap.createBitmap(dstRect.width(), dstRect.height(),
                Config.ARGB_8888);
        Canvas canvas = new Canvas(scaledBitmap);
        canvas.drawBitmap(unscaledBitmap, srcRect, dstRect, new Paint(Paint.FILTER_BITMAP_FLAG));

        return scaledBitmap;
    }

    /**
     * ScalingLogic defines how scaling should be carried out if source and
     * destination image has different aspect ratio.
     *
     * CROP: Scales the image the minimum amount while making sure that at least
     * one of the two dimensions fit inside the requested destination area.
     * Parts of the source image will be cropped to realize this.
     *
     * FIT: Scales the image the minimum amount while making sure both
     * dimensions fit inside the requested destination area. The resulting
     * destination dimensions might be adjusted to a smaller size than
     * requested.
     */
    public static enum ScalingLogic {
        CROP, FIT
    }

    /**
     * Calculate optimal down-sampling factor given the dimensions of a source
     * image, the dimensions of a destination area and a scaling logic.
     *
     * @param srcWidth Width of source image
     * @param srcHeight Height of source image
     * @param dstWidth Width of destination area
     * @param dstHeight Height of destination area
     * @param scalingLogic Logic to use to avoid image stretching
     * @return Optimal down scaling sample size for decoding
     */
    public static int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight,
            ScalingLogic scalingLogic) {
        if (scalingLogic == ScalingLogic.FIT) {
            final float srcAspect = (float)srcWidth / (float)srcHeight;
            final float dstAspect = (float)dstWidth / (float)dstHeight;

            if (srcAspect > dstAspect) {
                return srcWidth / dstWidth;
            } else {
                return srcHeight / dstHeight;
            }
        } else {
            final float srcAspect = (float)srcWidth / (float)srcHeight;
            final float dstAspect = (float)dstWidth / (float)dstHeight;

            if (srcAspect > dstAspect) {
                return srcHeight / dstHeight;
            } else {
                return srcWidth / dstWidth;
            }
        }
    }

    /**
     * Calculates source rectangle for scaling bitmap
     *
     * @param srcWidth Width of source image
     * @param srcHeight Height of source image
     * @param dstWidth Width of destination area
     * @param dstHeight Height of destination area
     * @param scalingLogic Logic to use to avoid image stretching
     * @return Optimal source rectangle
     */
    public static Rect calculateSrcRect(int srcWidth, int srcHeight, int dstWidth, int dstHeight,
            ScalingLogic scalingLogic) {
        if (scalingLogic == ScalingLogic.CROP) {
            final float srcAspect = (float)srcWidth / (float)srcHeight;
            final float dstAspect = (float)dstWidth / (float)dstHeight;

            if (srcAspect > dstAspect) {
                final int srcRectWidth = (int)(srcHeight * dstAspect);
                final int srcRectLeft = (srcWidth - srcRectWidth) / 2;
                return new Rect(srcRectLeft, 0, srcRectLeft + srcRectWidth, srcHeight);
            } else {
                final int srcRectHeight = (int)(srcWidth / dstAspect);
                final int scrRectTop = (int)(srcHeight - srcRectHeight) / 2;
                return new Rect(0, scrRectTop, srcWidth, scrRectTop + srcRectHeight);
            }
        } else {
            return new Rect(0, 0, srcWidth, srcHeight);
        }
    }

    /**
     * Calculates destination rectangle for scaling bitmap
     *
     * @param srcWidth Width of source image
     * @param srcHeight Height of source image
     * @param dstWidth Width of destination area
     * @param dstHeight Height of destination area
     * @param scalingLogic Logic to use to avoid image stretching
     * @return Optimal destination rectangle
     */
    public static Rect calculateDstRect(int srcWidth, int srcHeight, int dstWidth, int dstHeight,
            ScalingLogic scalingLogic) {
        if (scalingLogic == ScalingLogic.FIT) {
            final float srcAspect = (float)srcWidth / (float)srcHeight;
            final float dstAspect = (float)dstWidth / (float)dstHeight;

            if (srcAspect > dstAspect) {
                return new Rect(0, 0, dstWidth, (int)(dstWidth / srcAspect));
            } else {
                return new Rect(0, 0, (int)(dstHeight * srcAspect), dstHeight);
            }
        } else {
            return new Rect(0, 0, dstWidth, dstHeight);
        }
    }

}
