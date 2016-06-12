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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.mishiranu.dashchan.util.ResourceUtils;

public class SelectorBorderDrawable extends Drawable
{
	private static final int THICKNESS_DP = 2;
	
	private final Paint mPaint;
	private final float mDensity;
	
	public SelectorBorderDrawable(Context context)
	{
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setColor(Color.WHITE);
		mDensity = ResourceUtils.obtainDensity(context);
	}
	
	private boolean mSelected = false;
	
	public void setSelected(boolean selected)
	{
		mSelected = selected;
		invalidateSelf();
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		if (mSelected)
		{
			canvas.drawColor(0x44ffffff);
			Rect bounds = getBounds();
			int thickness = (int) (THICKNESS_DP * mDensity);
			canvas.drawRect(bounds.top, bounds.left, bounds.right, bounds.top + thickness, mPaint);
			canvas.drawRect(bounds.bottom - thickness, bounds.left, bounds.right, bounds.bottom, mPaint);
			canvas.drawRect(bounds.top, bounds.left, bounds.left + thickness, bounds.bottom, mPaint);
			canvas.drawRect(bounds.top, bounds.right - thickness, bounds.right, bounds.bottom, mPaint);
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
	public void setColorFilter(ColorFilter cf)
	{
		
	}
}