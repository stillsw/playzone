package com.stillwindsoftware.keepyabeat.gui;

import java.util.Locale;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;

import com.stillwindsoftware.keepyabeat.R;

public class WelcomeScreenActivity extends KybActivity {

	private static final String LOG_TAG = "KYB-"+WelcomeScreenActivity.class.getSimpleName();
	private static final int BANNER_MAX_DP = 720;
	private static final float BANNER_MAX_HEIGHT_PERC = 40.f;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// no action bar, but still have the standard android notification area
		// older devices may ignore this, but it's not of great consequence anyway
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
		setContentView(R.layout.welcome_layout);

		Spinner langSpinner = (Spinner) findViewById(R.id.languages_spinner);
		defaultLanguage(langSpinner);
		
		// create a listener to reset the language
		langSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				LocaleUtils.resetLanguage(WelcomeScreenActivity.this, position, mLangCode);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});

        ((CheckBox)findViewById(R.id.show_again)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean b) {
                mResourceManager.getPersistentStatesManager().setShowWelcomeDialog(b);
            }
        });


        // set up the banner

        KybBannerSvgView kybBanner = (KybBannerSvgView) findViewById(R.id.welcome_banner);
        kybBanner.setSVGFromResource(this, R.raw.kyb_banner);
        
//        Toast.makeText(this, "height vertSpace="+getResources()..getDimension(R.dimen.welcome_screen_vert_space)+" (density="+getResources().getDisplayMetrics().density+") dp="+
//         (getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density), Toast.LENGTH_LONG).show();
        
//		ImageView imageView = (ImageView) findViewById(R.id.welcome_banner);
//		imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
//		
//		try {
//			SVG svg = SVG.getFromResource(this, R.raw.kyb_banner);
//			Drawable drawable = new PictureDrawable(svg.renderToPicture());
			
//			svg.registerExternalFileResolver(fileResolver);
//			 Bitmap  newBM = Bitmap.createBitmap(915, 300, Bitmap.Config.ARGB_8888);
//			 Canvas  bmcanvas = new Canvas(newBM);
//			 bmcanvas.drawRGB(255, 255, 255);  // Clear background to white
//
//			 svg.renderToCanvas(bmcanvas);
//			 imageView.setImageDrawable(drawable);
//		
//		} catch (SVGParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//      
//      kybBanner.setImageDrawable(svg.createPictureDrawable());
//      mProgressBar = (ProgressBar) view.findViewById (R.id.progress_bar);
//      LinearLayout layout = new LinearLayout(getActivity());
//      SVGImageView svgImageView = new SVGImageView(getActivity());
//      svgImageView.setImageResource(R.raw.kyb_banner);
//      layout.addView(svgImageView,
//                     new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
//      return layout;

	}

	/**
	 * Get the language from prefs and default the spinner based on what that is and what's available
	 * @param langSpinner
	 */
	private void defaultLanguage(Spinner langSpinner) {
		
		int spinnerIdx = 0; // first language in dialog_simple_list is English

		// search the language codes defined for the app and if find a match update the spinner index
		String[] langCodes = getResources().getStringArray(R.array.language_codes);
		for (int i = 0; i < langCodes.length; i++) {
			if (mLangCode.equals(new Locale(langCodes[i], "", "").getLanguage())) {
				spinnerIdx = i;
				break;
			}
		}
		
		// set the spinner index
		langSpinner.setSelection(spinnerIdx);
	}
	
//	public static int getDefaultLanguageIdx(Activity activity, SharedPreferences prefs) {
//
//		String chosenLang = prefs.getString(SettingsManager.LANGUAGE, null);
//		
//		// no value saved look for default locale
//		if (chosenLang == null) {
//			Resources standardResources = activity.getBaseContext().getResources();
//			chosenLang = standardResources.getConfiguration().locale.getLanguage();
//		}
//		
//		int spinnerIdx = 0; // first language in dialog_simple_list is English
//
//		// search the language codes defined for the app and if find a match update the spinner index
//		String[] langCodes = activity.getResources().getStringArray(R.array.language_codes);
//		for (int i = 0; i < langCodes.length; i++) {
//			if (chosenLang.equals(new Locale(langCodes[i], "", "").getLanguage())) {
//				spinnerIdx = i;
//				break;
//			}
//		}
//		
//		return spinnerIdx;
//	}
	
	
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		
		if (hasFocus) {
			// size the banner
	        KybBannerSvgView kybBanner = (KybBannerSvgView) findViewById(R.id.welcome_banner);
	        kybBanner.resetLayout(this, BANNER_MAX_DP, BANNER_MAX_HEIGHT_PERC);
		}
	}

	/**
	 * onClick in the layout to show the online tour
	 * @param btn
	 */
	public void showTour(View btn) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(getResources().getString(R.string.tourHelpLink)));
        startActivity(intent);
	}

	/**
	 * onClick in the layout to dismiss
	 * @param btn
	 */
	public void okPressed(View btn) {
		finish();
	}

	/**
	 * Doesn't receive messages, so ignore this
	 */
	@Override
	protected String getReceiverFilterName() {
		return null;
	}

	@Override
	protected void registerKybReceiver() {
		// do nothing
	}

	@Override
	protected void unregisterKybReceiver() {
		// do nothing
	}

}
