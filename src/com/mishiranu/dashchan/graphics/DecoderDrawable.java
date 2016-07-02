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

import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.LruCache;

public class DecoderDrawable extends Drawable implements LruCache.RemoveCallback<Integer, Bitmap>
{
	private static final Executor EXECUTOR = ConcurrentUtils.newSingleThreadPool(20000, "DecoderDrawable", null, 0);
	
	private static final int FRAGMENT_SIZE = 512;
	private static final int MIN_MAX_ENTRIES = 16;
	
	private final Bitmap mScaledBitmap;
	private final BitmapRegionDecoder mDecoder;
	
	private final LinkedHashMap<Integer, DecodeTask> mTasks = new LinkedHashMap<>();
	private final LruCache<Integer, Bitmap> mFragments = new LruCache<>(this, MIN_MAX_ENTRIES);
	
	private final int mRotation;
	private final int mWidth;
	private final int mHeight;
	
	private final Rect mRect = new Rect();
	private final Rect mDstRect = new Rect();
	private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

	private boolean mEnabled = true;
	private boolean mRecycled = false;
	
	public DecoderDrawable(Bitmap scaledBitmap, FileHolder fileHolder) throws IOException
	{
		mScaledBitmap = scaledBitmap;
		mDecoder = BitmapRegionDecoder.newInstance(fileHolder.openInputStream(), false);
		mRotation = fileHolder.getRotation();
		mWidth = fileHolder.getImageWidth();
		mHeight = fileHolder.getImageHeight();
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		if (mRecycled) return;
		Rect bounds = getBounds();
		Rect rect = mRect;
		Rect dstRect = mDstRect;
		if (!(canvas.getClipBounds(rect) && rect.intersect(bounds))) rect.set(bounds);
		int maxEntries = 0;
		int scale = 1;
		boolean drawScaled = false;
		Callback callback = getCallback();
		if (callback instanceof View)
		{
			View view = (View) callback;
			int contentWidth = view.getWidth();
			int contentHeight = view.getHeight();
			int rectWidth = rect.width();
			int rectHeight = rect.height();
			int scaledSize;
			int contentSize;
			int rectSize;
			int size;
			if (rectWidth * contentHeight > rectHeight * contentWidth)
			{
				scaledSize = mScaledBitmap.getWidth();
				contentSize = contentWidth;
				rectSize = rectWidth;
				size = mWidth;
			}
			else
			{
				scaledSize = mScaledBitmap.getHeight();
				contentSize = contentHeight;
				rectSize = rectHeight;
				size = mHeight;
			}
			scale = Integer.highestOneBit(Math.max(rectSize / contentSize, 1));
			drawScaled = scaledSize >= size / scale;
		}
		int size = FRAGMENT_SIZE * scale;
		if (mEnabled && !drawScaled)
		{
			for (int y = 0; y < mHeight; y += size)
			{
				for (int x = 0; x < mWidth; x += size)
				{
					if (rect.intersects(x, y, x + size, y + size))
					{
						int key = calculateKey(x, y, scale);
						Bitmap fragment = mFragments.get(key);
						if (fragment != null)
						{
							dstRect.set(x, y, x + scale * fragment.getWidth(), y + scale * fragment.getHeight());
							canvas.drawBitmap(fragment, null, dstRect, mPaint);
						}
						else
						{
							DecodeTask task = mTasks.get(key);
							if (task == null)
							{
								task = new DecodeTask(key, x, y, scale);
								task.executeOnExecutor(EXECUTOR);
								mTasks.put(key, task);
							}
							canvas.save();
							canvas.clipRect(x, y, x + size, y + size);
							dstRect.set(0, 0, mWidth, mHeight);
							canvas.drawBitmap(mScaledBitmap, null, dstRect, mPaint);
							canvas.restore();
						}
						maxEntries++;
					}
				}
			}
		}
		else
		{
			dstRect.set(0, 0, mWidth, mHeight);
			canvas.drawBitmap(mScaledBitmap, null, dstRect, mPaint);
		}
		maxEntries = Math.max(MIN_MAX_ENTRIES, maxEntries);
		mFragments.setMaxEntries(maxEntries);
		int cancel = mTasks.size() - maxEntries;
		if (cancel > 0)
		{
			Iterator<DecodeTask> iterator = mTasks.values().iterator();
			while (iterator.hasNext() && cancel-- > 0)
			{
				iterator.next().cancel();
				iterator.remove();
			}
		}
	}
	
	@Override
	public int getOpacity()
	{
		return PixelFormat.TRANSLUCENT;
	}
	
	@Override
	public void setAlpha(int alpha)
	{
		
	}
	
	@Override
	public void setColorFilter(ColorFilter colorFilter)
	{
		
	}
	
	@Override
	public int getIntrinsicWidth()
	{
		return mWidth;
	}
	
	@Override
	public int getIntrinsicHeight()
	{
		return mHeight;
	}
	
	@Override
	public void onRemoveEldestEntry(Integer key, Bitmap value)
	{
		value.recycle();
	}
	
	public boolean hasAlpha()
	{
		return mScaledBitmap.hasAlpha();
	}
	
	private void clear()
	{
		for (DecodeTask task : mTasks.values()) task.cancel();
		mTasks.clear();
		for (Bitmap fragment : mFragments.values()) fragment.recycle();
		mFragments.clear();
	}
	
	public void setEnabled(boolean enabled)
	{
		if (mEnabled != enabled)
		{
			mEnabled = enabled;
			if (!enabled) clear();
		}
	}
	
	public void recycle()
	{
		if (!mRecycled)
		{
			mRecycled = true;
			clear();
			mScaledBitmap.recycle();
			synchronized (this)
			{
				mDecoder.recycle();
			}
		}
	}
	
	private int calculateKey(int x, int y, int scale)
	{
		return x << 18 | y << 4 | scale;
	}
	
	private class DecodeTask extends AsyncTask<Void, Void, Bitmap>
	{
		private final int mKey;
		private final Rect mRect;
		private final BitmapFactory.Options mOptions = new BitmapFactory.Options();
		
		public DecodeTask(int key, int x, int y, int scale)
		{
			mKey = key;
			mRect = new Rect(x, y, Math.min(x + FRAGMENT_SIZE * scale, mWidth),
					Math.min(y + FRAGMENT_SIZE * scale, mHeight));
			if (mRotation != 0)
			{
				Matrix matrix = new Matrix();
				matrix.setRotate(mRotation);
				if (mRotation == 90) matrix.postTranslate(mHeight, 0);
				else if (mRotation == 270) matrix.postTranslate(0, mWidth);
				else matrix.postTranslate(mWidth, mHeight);
				RectF rectF = new RectF(mRect);
				matrix.mapRect(rectF);
				mRect.set((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
			}
			mOptions.inDither = true;
			mOptions.inSampleSize = scale;
		}
		
		@Override
		protected Bitmap doInBackground(Void... params)
		{
			try
			{
				synchronized (DecoderDrawable.this)
				{
					Bitmap bitmap = mDecoder.decodeRegion(mRect, mOptions);
					if (bitmap != null && mRotation != 0)
					{
						Matrix matrix = new Matrix();
						matrix.setRotate(-mRotation);
						Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
								matrix, false);
						bitmap.recycle();
						bitmap = newBitmap;
					}
					return bitmap;
				}
			}
			catch (Throwable t)
			{
				return null;
			}
		}
		
		public void cancel()
		{
			cancel(false);
			mOptions.mCancel = true;
		}
		
		@Override
		protected void onCancelled(Bitmap result)
		{
			if (result != null) result.recycle();
		}
		
		@Override
		protected void onPostExecute(Bitmap result)
		{
			mTasks.remove(mKey);
			if (result != null)
			{
				mFragments.put(mKey, result);
				invalidateSelf();
			}
			else recycle();
		}
	}
}