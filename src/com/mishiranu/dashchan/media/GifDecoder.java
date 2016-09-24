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

package com.mishiranu.dashchan.media;

import java.io.File;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

public class GifDecoder implements Runnable
{
	private static native long init(String fileName);
	private static native void destroy(long pointer);

	private static native int getErrorCode(long pointer);
	private static native void getSummary(long pointer, int[] summary);

	private static native int draw(long pointer, Bitmap bitmap);

	private static final Handler HANDLER = new Handler(Looper.getMainLooper());

	private final long mPointer;
	private boolean mConsumed = false;

	private final int mWidth;
	private final int mHeight;
	private final Bitmap mBitmap;

	private static boolean sLoaded = false;

	public GifDecoder(File file) throws IOException
	{
		synchronized (GifDecoder.class)
		{
			if (!sLoaded)
			{
				try
				{
					System.loadLibrary("gif");
				}
				catch (LinkageError e)
				{
					throw new IOException(e);
				}
				sLoaded = true;
			}
		}
		mPointer = init(file.getAbsolutePath());
		int errorCode = getErrorCode(mPointer);
		if (errorCode != 0)
		{
			recycle();
			throw new IOException("Can't initialize decoder: CODE=" + errorCode);
		}
		int[] summary = new int[2];
		getSummary(mPointer, summary);
		mWidth = summary[0];
		mHeight = summary[1];
		mBitmap = Bitmap.createBitmap(summary[0], summary[1], Bitmap.Config.ARGB_8888);
	}

	public void recycle()
	{
		if (!mConsumed)
		{
			mConsumed = true;
			if (mBitmap != null) mBitmap.recycle();
			destroy(mPointer);
		}
	}

	@Override
	protected void finalize() throws Throwable
	{
		try
		{
			recycle();
		}
		finally
		{
			super.finalize();
		}
	}

	private Drawable mDrawable;

	public Drawable getDrawable()
	{
		if (mDrawable == null)
		{
			mDrawable = new Drawable()
			{
				private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

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
				public void setColorFilter(ColorFilter colorFilter)
				{
					mPaint.setColorFilter(colorFilter);
				}

				@Override
				public void setAlpha(int alpha)
				{
					mPaint.setAlpha(alpha);
				}

				@Override
				public int getOpacity()
				{
					return PixelFormat.TRANSPARENT;
				}

				@Override
				public void draw(Canvas canvas)
				{
					if (!mConsumed)
					{
						Rect bounds = getBounds();
						canvas.save();
						canvas.scale((float) bounds.width() / mWidth, (float) bounds.height() / mHeight);
						int delay = GifDecoder.draw(mPointer, mBitmap);
						canvas.drawBitmap(mBitmap, 0, 0, mPaint);
						canvas.restore();
						if (delay >= 0)
						{
							delay -= 20;
							if (delay > 0)
							{
								delay = Math.min(delay, 500);
								HANDLER.postDelayed(GifDecoder.this, delay);
							}
							else invalidateSelf();
						}
					}
				}
			};
		}
		return mDrawable;
	}

	@Override
	public void run()
	{
		mDrawable.invalidateSelf();
	}
}