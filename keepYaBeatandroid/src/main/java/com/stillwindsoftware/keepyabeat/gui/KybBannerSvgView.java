package com.stillwindsoftware.keepyabeat.gui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.stillwindsoftware.keepyabeat.R;

public class KybBannerSvgView extends ImageView {

	private static final String LOG_TAG = "KYB-"+KybBannerSvgView.class.getSimpleName();
	
	private SVG mSvg;

	public KybBannerSvgView(Context context) {
		super(context);
	}

	public KybBannerSvgView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public KybBannerSvgView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setSVGFromResource(Context context, int resId) {
		try {
			mSvg = SVG.getFromResource(context, resId);
//			mSvg.setRenderDPI(context.getResources().getDisplayMetrics().density * 160);

			// svg.registerExternalFileResolver(fileResolver);
//			Bitmap newBM = Bitmap.createBitmap(915, 300, Bitmap.Config.ARGB_8888);
//			Canvas bmcanvas = new Canvas(newBM);
//			bmcanvas.drawRGB(255, 255, 255); // Clear background to white

//			svg.renderToCanvas(bmcanvas);
//			kybBanner.setImageBitmap(newBM);

		} catch (SVGParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onDraw(Canvas canvas) {
		mSvg.renderToCanvas(canvas);
	}

	/**
	 * Called when the activity gains focus
	 * @param activity
	 */
	public void resetLayout(Activity activity, int maxDp, float heightPerc) {
		// need to use the ratio of doc's own w/h to keep them relative
		float docRatio = mSvg.getDocumentHeight() / mSvg.getDocumentWidth();

		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getLayoutParams();
		// max width is set by calling activity 
		params.width = Math.min(((ViewGroup)getParent()).getWidth(), (int) (maxDp * getResources().getDisplayMetrics().density));

		params.height = (int) (params.width * docRatio);

		// max height is a percentage of the parent's height
		int maxHeight = (int) (((ViewGroup)getParent()).getHeight() * (heightPerc / 100));
		if (maxHeight < params.height) {
			// max height is less than is allowed by the width, have to reduce the width to fit the height instead
			params.height = maxHeight;
			params.width = (int) (params.height / docRatio);
		}

		// set the document width as a ratio to the available width
		float ratio = mSvg.getDocumentWidth() / params.width;
		float docW = mSvg.getDocumentWidth() * ratio;

		// height then preserves the same ratio (so is exactly right size)
		float docH = mSvg.getDocumentHeight() * ratio;
		
		// set the the doc view box to these causes it to resize to fit
		mSvg.setDocumentViewBox(0, 0, docW, docH);

		setLayoutParams(params);
		
	}
}
