package com.mishiranu.dashchan.text.style;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.text.style.UpdateAppearance;

import androidx.annotation.NonNull;

import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.util.ResourceUtils;

@SuppressLint("ParcelCreator")
public class EmphasisSpan extends MetricAffectingSpan implements UpdateAppearance, ColorScheme.Span {
	private static final float SCALE = 5f / 4f;
	private int foregroundColor = -1;

	public EmphasisSpan() {};
	public EmphasisSpan(int color) {
		foregroundColor = color;
	}

	@Override
	public void applyColorScheme(ColorScheme colorScheme) {
		if (colorScheme != null) {
			foregroundColor = colorScheme.emphasisColor;
		}
	}

	@Override
	public void updateDrawState(@NonNull TextPaint paint) {
		paint.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
		if (paint.getColor() != Color.TRANSPARENT) {
			paint.setColor(foregroundColor);
		}
		applyScale(paint);
	}

	@Override
	public void updateMeasureState(@NonNull TextPaint paint) {
		updateDrawState(paint);
	}

	private void applyScale(TextPaint paint) {
		paint.setTextSize((int) (paint.getTextSize() * SCALE + 0.5f));
	}
}
