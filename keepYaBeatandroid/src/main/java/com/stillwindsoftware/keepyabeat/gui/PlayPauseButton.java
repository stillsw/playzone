package com.stillwindsoftware.keepyabeat.gui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;

import com.stillwindsoftware.keepyabeat.R;
import com.stillwindsoftware.keepyabeat.model.PlayerState.RepeatOption;
import com.stillwindsoftware.keepyabeat.platform.AndroidGuiManager;
import com.stillwindsoftware.keepyabeat.player.backend.BeatTrackerBinder;

import static com.stillwindsoftware.keepyabeat.model.xml.LibraryXmlLoader.EMPTY_STRING;

public class PlayPauseButton extends android.support.v7.widget.AppCompatImageButton {

	@SuppressWarnings("unused")
	private static final String LOG_TAG = "KYB-"+PlayPauseButton.class.getSimpleName();

    public PlayPauseButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public PlayPauseButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PlayPauseButton(Context context) {
		super(context);
	}

    public void updateImage(boolean isPlaying) {
        setImageResource(isPlaying
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
    }
}
