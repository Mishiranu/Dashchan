package com.mishiranu.dashchan.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class SimpleBitmapDrawable extends Drawable {
	private final Bitmap bitmap;
	private final int width;
	private final int height;
	private final boolean allowRecycle;

	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

	public SimpleBitmapDrawable(Bitmap bitmap, int width, int height, boolean allowRecycle) {
		this.bitmap = bitmap;
		this.width = width;
		this.height = height;
		this.allowRecycle = allowRecycle;
	}

	public SimpleBitmapDrawable(Bitmap bitmap, boolean allowRecycle) {
		this(bitmap, bitmap.getWidth(), bitmap.getHeight(), allowRecycle);
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawBitmap(bitmap, null, getBounds(), paint);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {}

	@Override
	public int getIntrinsicWidth() {
		return width;
	}

	@Override
	public int getIntrinsicHeight() {
		return height;
	}

	public void recycle() {
		if (allowRecycle) {
			bitmap.recycle();
		}
	}
}
