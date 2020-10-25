package com.mishiranu.dashchan.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.util.ResourceUtils;

public class SelectorBorderDrawable extends BaseDrawable {
	private static final int THICKNESS_DP = 2;

	private final Paint paint;
	private final float density;

	public SelectorBorderDrawable(Context context) {
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(Color.WHITE);
		density = ResourceUtils.obtainDensity(context);
	}

	private boolean selected = false;

	public void setSelected(boolean selected) {
		this.selected = selected;
		invalidateSelf();
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		if (selected) {
			canvas.drawColor(0x44ffffff);
			Rect bounds = getBounds();
			int thickness = (int) (THICKNESS_DP * density);
			canvas.drawRect(bounds.top, bounds.left, bounds.right, bounds.top + thickness, paint);
			canvas.drawRect(bounds.bottom - thickness, bounds.left, bounds.right, bounds.bottom, paint);
			canvas.drawRect(bounds.top, bounds.left, bounds.left + thickness, bounds.bottom, paint);
			canvas.drawRect(bounds.top, bounds.right - thickness, bounds.right, bounds.bottom, paint);
		}
	}
}
