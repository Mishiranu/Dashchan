package com.mishiranu.dashchan.text.style;

import android.annotation.SuppressLint;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;
import androidx.annotation.NonNull;

// TypefaceSpan("sans-serif-light") + RelativeSizeSpan(SCALE)
@SuppressLint("ParcelCreator")
public class HeadingSpan extends TypefaceSpan {
	private static final float SCALE = 5f / 4f;

	public HeadingSpan() {
		super("sans-serif-light");
	}

	@Override
	public void updateDrawState(@NonNull TextPaint paint) {
		super.updateDrawState(paint);
		applyScale(paint);
	}

	@Override
	public void updateMeasureState(@NonNull TextPaint paint) {
		super.updateMeasureState(paint);
		applyScale(paint);
	}

	private void applyScale(TextPaint paint) {
		paint.setTextSize((int) (paint.getTextSize() * SCALE + 0.5f));
	}
}
