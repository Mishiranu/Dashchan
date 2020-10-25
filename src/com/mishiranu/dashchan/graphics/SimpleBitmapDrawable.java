package com.mishiranu.dashchan.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.NonNull;

public class SimpleBitmapDrawable extends BaseDrawable {
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
	public void draw(@NonNull Canvas canvas) {
		canvas.drawBitmap(bitmap, null, getBounds(), paint);
	}

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
