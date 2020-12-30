package com.mishiranu.dashchan.graphics;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import androidx.annotation.NonNull;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.async.ExecutorTask;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.LruCache;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;

public class DecoderDrawable extends BaseDrawable {
	private static final Executor EXECUTOR = ConcurrentUtils.newSingleThreadPool(20000, "DecoderDrawable", null);
	private static final Bitmap NULL_BITMAP = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

	private static final int FRAGMENT_SIZE = 512;
	private static final int MIN_MAX_ENTRIES = 16;

	private final Bitmap scaledBitmap;
	private final BitmapRegionDecoder decoder;

	private final LinkedHashMap<Integer, DecodeTask> tasks = new LinkedHashMap<>();
	private final LruCache<Integer, Bitmap> fragments = new LruCache<>(MIN_MAX_ENTRIES, (k, v) -> v.recycle());

	private final int width;
	private final int height;
	private final int rotation;
	private final Float gammaCorrection;

	private final Rect rect = new Rect();
	private final Rect dstRect = new Rect();
	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

	private boolean enabled = true;
	private boolean recycled = false;

	public DecoderDrawable(Bitmap scaledBitmap, FileHolder fileHolder) throws IOException {
		this.scaledBitmap = scaledBitmap;
		if (!fileHolder.isImageRegionDecoderSupported()) {
			throw new IOException("Decoder drawable is not supported");
		}
		decoder = BitmapRegionDecoder.newInstance(fileHolder.openInputStream(), false);
		width = fileHolder.getImageWidth();
		height = fileHolder.getImageHeight();
		rotation = fileHolder.getImageRotation();
		gammaCorrection = fileHolder.getImageGammaCorrectionForSkia();
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		Rect bounds = getBounds();
		Rect rect = this.rect;
		Rect dstRect = this.dstRect;
		if (!(canvas.getClipBounds(rect) && rect.intersect(bounds))) {
			rect.set(bounds);
		}
		int maxEntries = 0;
		int scale = 1;
		boolean drawScaled = false;
		if (!recycled) {
			Callback callback = getCallback();
			if (callback instanceof View) {
				View view = (View) callback;
				int contentWidth = view.getWidth();
				int contentHeight = view.getHeight();
				int rectWidth = rect.width();
				int rectHeight = rect.height();
				int scaledSize;
				int contentSize;
				int rectSize;
				int size;
				if (rectWidth * contentHeight > rectHeight * contentWidth) {
					scaledSize = scaledBitmap.getWidth();
					contentSize = contentWidth;
					rectSize = rectWidth;
					size = width;
				} else {
					scaledSize = scaledBitmap.getHeight();
					contentSize = contentHeight;
					rectSize = rectHeight;
					size = height;
				}
				scale = Integer.highestOneBit(Math.max(rectSize / contentSize, 1));
				drawScaled = scaledSize >= size / scale;
			}
		} else {
			drawScaled = true;
		}
		int size = FRAGMENT_SIZE * scale;
		if (enabled && !drawScaled) {
			for (int y = 0; y < height; y += size) {
				for (int x = 0; x < width; x += size) {
					if (rect.intersects(x, y, x + size, y + size)) {
						int key = calculateKey(x, y, scale);
						Bitmap fragment = fragments.get(key);
						boolean drawScaledFragment = false;
						if (fragment != null) {
							if (fragment != NULL_BITMAP) {
								dstRect.set(x, y, x + scale * fragment.getWidth(), y + scale * fragment.getHeight());
								canvas.drawBitmap(fragment, null, dstRect, paint);
							} else {
								drawScaledFragment = true;
							}
						} else {
							DecodeTask task = tasks.get(key);
							if (task == null) {
								task = new DecodeTask(key, x, y, scale);
								task.execute(EXECUTOR);
								tasks.put(key, task);
							}
							drawScaledFragment = true;
						}
						if (drawScaledFragment) {
							canvas.save();
							canvas.clipRect(x, y, x + size, y + size);
							dstRect.set(0, 0, width, height);
							canvas.drawBitmap(scaledBitmap, null, dstRect, paint);
							canvas.restore();
						}
						maxEntries++;
					}
				}
			}
		} else {
			dstRect.set(0, 0, width, height);
			canvas.drawBitmap(scaledBitmap, null, dstRect, paint);
		}
		maxEntries = Math.max(MIN_MAX_ENTRIES, maxEntries);
		fragments.setMaxEntries(maxEntries);
		int cancel = tasks.size() - maxEntries;
		if (cancel > 0) {
			Iterator<DecodeTask> iterator = tasks.values().iterator();
			while (iterator.hasNext() && cancel-- > 0) {
				iterator.next().cancel();
				iterator.remove();
			}
		}
	}

	@Override
	public int getIntrinsicWidth() {
		return width;
	}

	@Override
	public int getIntrinsicHeight() {
		return height;
	}

	public boolean hasAlpha() {
		return scaledBitmap.hasAlpha();
	}

	private void clear() {
		for (DecodeTask task : tasks.values()) {
			task.cancel();
		}
		tasks.clear();
		for (Bitmap fragment : fragments.values()) {
			fragment.recycle();
		}
		fragments.clear();
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled != enabled) {
			this.enabled = enabled;
			if (!enabled) {
				clear();
			}
		}
	}

	public void recycle() {
		recycle(true);
	}

	private void recycle(boolean recycleScaled) {
		if (!recycled) {
			recycled = true;
			clear();
			synchronized (this) {
				decoder.recycle();
			}
		}
		if (recycleScaled) {
			scaledBitmap.recycle();
		}
	}

	private int calculateKey(int x, int y, int scale) {
		return x << 18 | y << 4 | scale;
	}

	private class DecodeTask extends ExecutorTask<Void, Bitmap> {
		private final int key;
		private final Rect rect;
		private final BitmapFactory.Options options = new BitmapFactory.Options();

		private boolean error = false;

		public DecodeTask(int key, int x, int y, int scale) {
			this.key = key;
			rect = new Rect(x, y, Math.min(x + FRAGMENT_SIZE * scale, width),
					Math.min(y + FRAGMENT_SIZE * scale, height));
			if (rotation != 0) {
				Matrix matrix = new Matrix();
				matrix.setRotate(rotation);
				if (rotation == 90) {
					matrix.postTranslate(height, 0);
				} else if (rotation == 270) {
					matrix.postTranslate(0, width);
				} else {
					matrix.postTranslate(width, height);
				}
				RectF rectF = new RectF(rect);
				matrix.mapRect(rectF);
				rect.set((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
			}
			options.inSampleSize = scale;
		}

		@Override
		protected Bitmap run() {
			try {
				synchronized (DecoderDrawable.this) {
					Bitmap bitmap = decoder.decodeRegion(rect, options);
					bitmap = GraphicsUtils.applyRotation(bitmap, rotation);
					if (gammaCorrection != null) {
						bitmap = GraphicsUtils.applyGammaCorrection(bitmap, gammaCorrection);
					}
					return bitmap;
				}
			} catch (Throwable t) {
				error = true;
				t.printStackTrace();
				return null;
			}
		}

		@SuppressWarnings("deprecation")
		public void cancel() {
			super.cancel();
			if (!C.API_NOUGAT) {
				options.mCancel = true;
			}
		}

		@Override
		protected void onCancel(Bitmap bitmap) {
			if (bitmap != null) {
				bitmap.recycle();
			}
		}

		@Override
		protected void onComplete(Bitmap bitmap) {
			tasks.remove(key);
			if (error) {
				recycle(false);
			} else {
				if (bitmap == null) {
					bitmap = NULL_BITMAP;
				}
				fragments.put(key, bitmap);
				invalidateSelf();
			}
		}
	}
}
