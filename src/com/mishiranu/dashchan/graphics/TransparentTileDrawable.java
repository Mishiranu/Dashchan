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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class TransparentTileDrawable extends Drawable
{
	private static final int COLOR_MIN = 0xe0;
	private static final int COLOR_MAX = 0xf0;

	private final Paint mPaint;

	public TransparentTileDrawable(Context context, boolean large)
	{
		mPaint = new Paint();
		float density = ResourceUtils.obtainDensity(context);
		Bitmap bitmap = GraphicsUtils.generateNoise(large ? 80 : 40, (int) density, COLOR_MIN << 24 | 0x00ffffff,
				COLOR_MAX << 24 | 0x00ffffff);
		mPaint.setShader(new BitmapShader(bitmap, BitmapShader.TileMode.REPEAT, BitmapShader.TileMode.REPEAT));
	}

	@Override
	public void draw(Canvas canvas)
	{
		canvas.drawRect(getBounds(), mPaint);
	}

	@Override
	public int getOpacity()
	{
		return PixelFormat.OPAQUE;
	}

	@Override
	public int getAlpha()
	{
		return mPaint.getAlpha();
	}

	@Override
	public void setAlpha(int alpha)
	{
		mPaint.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter cf)
	{

	}
}