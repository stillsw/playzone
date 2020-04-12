package com.stillwindsoftware.keepyabeat.platform.twl;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import com.stillwindsoftware.keepyabeat.platform.PlatformResourceManager.LOG_TYPE;

/**
 * A simple class that interprets the request for help and links to the correct page
 * @author tomas stubbs
 */
public class UserBrowserProxy {

	public enum Key {
		general(TwlLocalisationKeys.GENERAL_HELP_LINK),
		home(TwlLocalisationKeys.KYB_HOME_LINK),
		tour(TwlLocalisationKeys.TOUR_HELP_LINK),
		player(TwlLocalisationKeys.PLAYER_HELP_LINK),
		editor(TwlLocalisationKeys.EDITOR_HELP_LINK),
		beatssounds(TwlLocalisationKeys.BEATS_AND_SOUNDS_HELP_LINK),
		tags(TwlLocalisationKeys.TAGS_HELP_LINK),
		shareImport(TwlLocalisationKeys.SHARE_IMPORT_HELP_LINK),
		bugReportHelp(TwlLocalisationKeys.BUG_REPORT_HELP_LINK),
		bugReport(TwlLocalisationKeys.BUG_REPORT_LINK)
		;
		
		String localisedKey;
		
		Key(String localisedKey) {
			this.localisedKey = localisedKey;
		}
		
		private String getLocalisedKey() {
			return localisedKey;
		}
	}
	
	/**
	 * Attempt to show the webpage in the browser. Errors are notified to the user.
	 * @param key
	 */
	public static void showWebPage(Key key) {
		
		TwlResourceManager resourceManager = TwlResourceManager.getInstance();
		String url = resourceManager.getLocalisedString(key.getLocalisedKey());
		
		// no localised string is indicated by returning back the key
		if (!url.equals(key.getLocalisedKey())) {
			try {
				Desktop.getDesktop().browse(URI.create(url));
			} catch (IOException e) {
				resourceManager.log(LOG_TYPE.error, null, "UserBrowserProxy.showWebPage: IOException trying to show help for "+key+" ("+url+")");
				e.printStackTrace();
				resourceManager.getGuiManager().showNotification(
						resourceManager.getLocalisedString(TwlLocalisationKeys.ERROR_IO_ON_HELP));
			}
		}
		else {
			resourceManager.log(LOG_TYPE.error, null, "UserBrowserProxy.showWebPage: program error, no local key in resource bundle for "+key);
			resourceManager.getGuiManager().showNotification(
					resourceManager.getLocalisedString(TwlLocalisationKeys.ERROR_MISSING_HELP_LINK));
		}
	}
}
