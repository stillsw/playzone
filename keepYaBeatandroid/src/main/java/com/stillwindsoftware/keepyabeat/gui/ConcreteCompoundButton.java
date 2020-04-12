package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;

/**
 * CompoundButton is abstract, otherwise there would be no need for this class, as it does nothing!
 */
public class ConcreteCompoundButton extends CompoundButton {

	public ConcreteCompoundButton(Context context) {
		super(context);
	}

	public ConcreteCompoundButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ConcreteCompoundButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

}
