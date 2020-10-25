package com.mishiranu.dashchan.media;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.graphics.BaseDrawable;
import java.io.File;
import java.io.IOException;

public class GifDecoder implements Runnable {
	private static native long init(String fileName);
	private static native void destroy(long pointer);

	private static native int getErrorCode(long pointer);
	private static native void getSummary(long pointer, int[] summary);

	private static native int draw(long pointer, Bitmap bitmap);

	private static final Handler HANDLER = new Handler(Looper.getMainLooper());

	private final long pointer;
	private boolean consumed = false;

	private final int width;
	private final int height;
	private final Bitmap bitmap;

	private static boolean loaded = false;

	public GifDecoder(File file) throws IOException {
		synchronized (GifDecoder.class) {
			if (!loaded) {
				try {
					System.loadLibrary("gif");
				} catch (LinkageError e) {
					throw new IOException(e);
				}
				loaded = true;
			}
		}
		pointer = init(file.getAbsolutePath());
		int errorCode = getErrorCode(pointer);
		if (errorCode != 0) {
			recycle();
			throw new IOException("Can't initialize decoder: CODE=" + errorCode);
		}
		int[] summary = new int[2];
		getSummary(pointer, summary);
		width = summary[0];
		height = summary[1];
		bitmap = Bitmap.createBitmap(summary[0], summary[1], Bitmap.Config.ARGB_8888);
	}

	public void recycle() {
		if (!consumed) {
			consumed = true;
			if (bitmap != null) {
				bitmap.recycle();
			}
			destroy(pointer);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			recycle();
		} finally {
			super.finalize();
		}
	}

	private Drawable drawable;

	public Drawable getDrawable() {
		if (drawable == null) {
			drawable = new BaseDrawable() {
				private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

				@Override
				public int getIntrinsicWidth() {
					return width;
				}

				@Override
				public int getIntrinsicHeight() {
					return height;
				}

				@Override
				public void setColorFilter(ColorFilter colorFilter) {
					paint.setColorFilter(colorFilter);
				}

				@Override
				public void setAlpha(int alpha) {
					paint.setAlpha(alpha);
				}

				@Override
				public void draw(@NonNull Canvas canvas) {
					if (!consumed) {
						Rect bounds = getBounds();
						canvas.save();
						canvas.scale((float) bounds.width() / width, (float) bounds.height() / height);
						int delay = GifDecoder.draw(pointer, bitmap);
						canvas.drawBitmap(bitmap, 0, 0, paint);
						canvas.restore();
						if (delay >= 0) {
							delay -= 20;
							if (delay > 0) {
								delay = Math.min(delay, 500);
								HANDLER.postDelayed(GifDecoder.this, delay);
							} else {
								invalidateSelf();
							}
						}
					}
				}
			};
		}
		return drawable;
	}

	@Override
	public void run() {
		drawable.invalidateSelf();
	}
}
