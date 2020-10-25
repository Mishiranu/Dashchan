package com.mishiranu.dashchan.graphics;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;

public class ChanIconDrawable extends BaseDrawable {
	private final Drawable drawable;
	private final Paint paint;

	private PorterDuffColorFilter colorFilter;
	private ColorStateList tint;
	private PorterDuff.Mode tintMode;

	public ChanIconDrawable(Drawable drawable) {
		this.drawable = drawable;
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(0xffffffff);
	}

	public ChanIconDrawable newInstance() {
		return new ChanIconDrawable(drawable);
	}

	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		super.setBounds(left, top, right, bottom);
		drawable.setBounds(left, top, right, bottom);
	}

	@Override
	public void setTintList(ColorStateList tint) {
		super.setTintList(tint);

		colorFilter = null;
		this.tint = tint;
		invalidateSelf();
	}

	@Override
	public void setTintMode(PorterDuff.Mode tintMode) {
		super.setTintMode(tintMode);

		colorFilter = null;
		this.tintMode = tintMode;
		invalidateSelf();
	}

	@Override
	public int getIntrinsicWidth() {
		return drawable.getIntrinsicWidth();
	}

	@Override
	public int getIntrinsicHeight() {
		return drawable.getIntrinsicHeight();
	}

	@Override
	public int getMinimumWidth() {
		return drawable.getMinimumWidth();
	}

	@Override
	public int getMinimumHeight() {
		return drawable.getMinimumHeight();
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		if (colorFilter == null && tint != null) {
			colorFilter = new PorterDuffColorFilter(tint.getColorForState(getState(), tint.getDefaultColor()),
					tintMode != null ? tintMode : PorterDuff.Mode.SRC_IN);
		}
		if (colorFilter == null) {
			drawable.draw(canvas);
		} else {
			Rect bounds = getBounds();
			paint.setColorFilter(colorFilter);
			canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, paint);
			drawable.draw(canvas);
			canvas.restore();
		}
	}

	@SuppressWarnings("deprecation")
	@Deprecated
	@Override
	public int getOpacity() {
		return drawable.getOpacity();
	}
}
