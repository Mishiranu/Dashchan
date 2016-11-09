/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.graphics;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.View;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.LruCache;

public class DecoderDrawable extends Drawable {
	private static final Executor EXECUTOR = ConcurrentUtils.newSingleThreadPool(20000, "DecoderDrawable", null, 0);
	private static final Bitmap NULL_BITMAP = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

	private static final int FRAGMENT_SIZE = 512;
	private static final int MIN_MAX_ENTRIES = 16;

	private final Bitmap scaledBitmap;
	private final BitmapRegionDecoder decoder;

	private final LinkedHashMap<Integer, DecodeTask> tasks = new LinkedHashMap<>();
	private final LruCache<Integer, Bitmap> fragments = new LruCache<>((key, value) -> value.recycle(),
			MIN_MAX_ENTRIES);

	private final int rotation;
	private final int width;
	private final int height;

	private final Rect rect = new Rect();
	private final Rect dstRect = new Rect();
	private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

	private boolean enabled = true;
	private boolean recycled = false;

	public DecoderDrawable(Bitmap scaledBitmap, FileHolder fileHolder) throws IOException {
		this.scaledBitmap = scaledBitmap;
		if (!fileHolder.isRegionDecoderSupported()) {
			throw new IOException("Decoder drawable is not supported");
		}
		decoder = BitmapRegionDecoder.newInstance(fileHolder.openInputStream(), false);
		rotation = fileHolder.getRotation();
		width = fileHolder.getImageWidth();
		height = fileHolder.getImageHeight();
	}

	@Override
	public void draw(Canvas canvas) {
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
								task.executeOnExecutor(EXECUTOR);
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

	private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
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
		protected Bitmap doInBackground(Void... params) {
			try {
				synchronized (DecoderDrawable.this) {
					Bitmap bitmap = decoder.decodeRegion(rect, options);
					if (bitmap != null && rotation != 0) {
						Matrix matrix = new Matrix();
						matrix.setRotate(-rotation);
						Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
								matrix, false);
						bitmap.recycle();
						bitmap = newBitmap;
					}
					return bitmap;
				}
			} catch (Throwable t) {
				error = true;
				Log.persistent().stack(t);
				return null;
			}
		}

		@SuppressWarnings("deprecation")
		public void cancel() {
			cancel(false);
			if (!C.API_NOUGAT) {
				options.mCancel = true;
			}
		}

		@Override
		protected void onCancelled(Bitmap result) {
			if (result != null) {
				result.recycle();
			}
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			tasks.remove(key);
			if (error) {
				recycle(false);
			} else {
				if (result == null) {
					result = NULL_BITMAP;
				}
				fragments.put(key, result);
				invalidateSelf();
			}
		}
	}
}