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
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.DisplayMetrics;

import com.mishiranu.dashchan.app.MainApplication;
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
		DisplayMetrics metrics = MainApplication.getInstance().getResources().getDisplayMetrics();
		int scale = Math.max(1, (int) Math.min((float) rect.width() / metrics.widthPixels,
				(float) rect.height() / metrics.heightPixels));
		int size = FRAGMENT_SIZE * scale;
		if (mEnabled && (mScaledBitmap.getWidth() > size / 2 || mScaledBitmap.getHeight() > size / 2))
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
								task = new DecodeTask(x, y, scale);
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
			synchronized (this)
			{
				mScaledBitmap.recycle();
			}
			mDecoder.recycle();
		}
	}
	
	private int calculateKey(int x, int y, int scale)
	{
		return x << 18 | y << 4 | scale;
	}
	
	private class DecodeTask extends AsyncTask<Void, Void, Bitmap>
	{
		private final BitmapFactory.Options mOptions = new BitmapFactory.Options();
		private final Rect mRect;
		
		public DecodeTask(int x, int y, int scale)
		{
			mRect = new Rect(x, y, Math.min(x + FRAGMENT_SIZE * scale, mWidth),
					Math.min(y + FRAGMENT_SIZE * scale, mHeight));
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
					return mDecoder.decodeRegion(mRect, mOptions);
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
			int key = calculateKey(mRect.left, mRect.top, mOptions.inSampleSize);
			mTasks.remove(key);
			if (result != null)
			{
				mFragments.put(key, result);
				invalidateSelf();
			}
			else recycle();
		}
	}
}