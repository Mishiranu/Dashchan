package com.mishiranu.dashchan.graphics;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import androidx.annotation.NonNull;

public class RoundedCornersDrawable extends BaseDrawable {
	private final Path path = new Path();
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int radius;

	public RoundedCornersDrawable(int radius) {
		this.radius = radius;
	}

	public RoundedCornersDrawable(int radius, int color) {
		this(radius);
		setColor(color);
	}

	public void setColor(int color) {
		paint.setColor(color);
	}

	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		Rect bounds = getBounds();
		if (bounds.left != left || bounds.top != top || bounds.right != right || bounds.bottom != bottom) {
			Path path = this.path;
			path.rewind();
			float radius = this.radius;
			float shift = ((float) Math.sqrt(2) - 1f) * radius * 4f / 3f;
			path.moveTo(left, top);
			path.rLineTo(radius, 0);
			path.rCubicTo(-shift, 0, -radius, radius - shift, -radius, radius);
			path.close();
			path.moveTo(right, top);
			path.rLineTo(-radius, 0);
			path.rCubicTo(shift, 0, radius, radius - shift, radius, radius);
			path.close();
			path.moveTo(left, bottom);
			path.rLineTo(radius, 0);
			path.rCubicTo(-shift, 0, -radius, shift - radius, -radius, -radius);
			path.close();
			path.moveTo(right, bottom);
			path.rLineTo(-radius, 0);
			path.rCubicTo(shift, 0, radius, shift - radius, radius, -radius);
			path.close();
		}
		super.setBounds(left, top, right, bottom);
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		canvas.drawPath(path, paint);
	}
}
