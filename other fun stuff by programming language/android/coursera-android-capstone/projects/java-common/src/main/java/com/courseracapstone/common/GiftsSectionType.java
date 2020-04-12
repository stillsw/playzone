package com.courseracapstone.common;

// enum of types for gifts, to support different screens
public enum GiftsSectionType {
	ALL_GIFTS(0),
	MY_GIFTS(1),
	ALL_GIFT_CHAINS(2),
	OBSCENE_GIFTS(3),
	ONE_CHAIN(4);
	
	private int mIdx;

	private GiftsSectionType(int idx) {
		mIdx = idx;
	}
	
	public int getIndex() {
		return mIdx;
	}
}