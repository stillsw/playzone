package com.courseracapstone.android.utils;

import android.content.res.Resources;

import com.courseracapstone.android.R;
import com.courseracapstone.common.GiftsSectionType;

/**
 * Ad-hoc utilities
 * 
 * @author xxx xxx
 *
 */
public class Utils {

	public static String getGiftSectionTitle(Resources res, GiftsSectionType type) {
		return res.getStringArray(R.array.gifts_section_titles)[type.getIndex()];
	}

	public static GiftsSectionType getGiftSectionTypeForIndex(int idx) {
		
		switch (idx) {
		case 0 : return GiftsSectionType.ALL_GIFTS;
		case 1 : return GiftsSectionType.MY_GIFTS;
		case 2 : return GiftsSectionType.ALL_GIFT_CHAINS;
		case 3 : return GiftsSectionType.OBSCENE_GIFTS;
		case 4 : return GiftsSectionType.ONE_CHAIN;
		default : return null;
		}
	}
}
