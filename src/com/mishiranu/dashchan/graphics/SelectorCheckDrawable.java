package com.mishiranu.dashchan.graphics;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import com.mishiranu.dashchan.util.AnimationUtils;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SelectorCheckDrawable extends Drawable {
	private static final int DURATION = 200;

	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path path = new Path();
	private final PathMeasure pathMeasure = new PathMeasure();

	private long start;
	private boolean selected = false;

	public SelectorCheckDrawable() {
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.SQUARE);
		paint.setStrokeJoin(Paint.Join.MITER);
		paint.setColor(Color.WHITE);
	}

	@Override
	public void draw(Canvas canvas) {
		long dt = SystemClock.elapsedRealtime() - start;
		float value = start == 0L ? 1f : dt < 0 ? 0f : Math.min((float) dt / DURATION, 1f);
		value = AnimationUtils.DECELERATE_INTERPOLATOR.getInterpolation(value);
		Rect bounds = getBounds();
		canvas.save();
		canvas.translate(bounds.left, bounds.top);
		canvas.drawColor(Color.argb((int) ((selected ? value : 1f - value) * 0x80), 0, 0, 0));
		int size;
		int width = bounds.width();
		int height = bounds.height();
		if (width > height) {
			canvas.translate((width - height) / 2f, 0);
			size = height;
		} else if (height > width) {
			canvas.translate(0, (height - width) / 2f);
			size = width;
		} else {
			size = width;
		}
		final float strokeSize = 0.03f;
		paint.setStrokeWidth(strokeSize * size);

		path.moveTo(0.39f * size, 0.5f * size);
		path.rLineTo(0.08f * size, 0.08f * size);
		path.rLineTo(0.14f * size, -0.14f * size);
		pathMeasure.setPath(path, false);
		path.rewind();
		float length = pathMeasure.getLength();
		if (selected) {
			pathMeasure.getSegment(0f, value * length, path, true);
		} else {
			pathMeasure.getSegment(value * length, length, path, true);
		}
		canvas.drawPath(path, paint);
		path.rewind();

		float append = selected ? (1f - value) * 90f : value * -90f - 180f;
		paint.setStrokeWidth(strokeSize * size);
		path.arcTo(0.3f * size, 0.3f * size, 0.7f * size, 0.7f * size, 270f + append, -180f, true);
		path.arcTo(0.3f * size, 0.3f * size, 0.7f * size, 0.7f * size, 90f + append, -180f, false);
		pathMeasure.setPath(path, false);
		path.rewind();
		length = pathMeasure.getLength();
		if (selected) {
			pathMeasure.getSegment(0f, value * length, path, true);
		} else {
			pathMeasure.getSegment(value * length, length, path, true);
		}
		canvas.drawPath(path, paint);
		path.rewind();

		canvas.restore();
		if (value < 1f) {
			invalidateSelf();
		}
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter cf) {}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected, boolean animate) {
		if (this.selected != selected) {
			if (animate) {
				start = SystemClock.elapsedRealtime();
			} else {
				start = 0L;
			}
			this.selected = selected;
			invalidateSelf();
		}
	}
}
