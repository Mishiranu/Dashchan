package com.mishiranu.dashchan.graphics;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

public class BaseDrawable extends Drawable {
	@Override
	public void draw(@NonNull Canvas canvas) {}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {}

	@SuppressWarnings({"deprecation", "RedundantSuppression"})
	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
