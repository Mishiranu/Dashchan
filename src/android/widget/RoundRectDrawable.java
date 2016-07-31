/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * 
 * ************************************************************************
 * 
 * Copyright 2014-2016 Fukurou Mishiranu
 */

package android.widget;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;

public class RoundRectDrawable extends Drawable
{
	private float mRadius;
	private final Paint mPaint;
	private final RectF mBoundsF;
	private final Rect mBoundsI;
	private float mPadding;
	
	public RoundRectDrawable(int backgroundColor, float radius)
	{
		mRadius = radius;
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
		mPaint.setColor(backgroundColor);
		mBoundsF = new RectF();
		mBoundsI = new Rect();
	}
	
	public void setPadding(float padding)
	{
		if (padding == mPadding) return;
		mPadding = padding;
		updateBounds(null);
		invalidateSelf();
	}
	
	public float getPadding()
	{
		return mPadding;
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		canvas.drawRoundRect(mBoundsF, mRadius, mRadius, mPaint);
	}
	
	private void updateBounds(Rect bounds)
	{
		if (bounds == null) bounds = getBounds();
		mBoundsF.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
		mBoundsI.set(bounds);
		float vInset = RoundRectDrawableWithShadow.calculateVerticalPadding(mPadding, mRadius);
		float hInset = RoundRectDrawableWithShadow.calculateHorizontalPadding(mPadding, mRadius);
		mBoundsI.inset((int) Math.ceil(hInset), (int) Math.ceil(vInset));
		mBoundsF.set(mBoundsI);
	}
	
	@Override
	protected void onBoundsChange(Rect bounds)
	{
		super.onBoundsChange(bounds);
		updateBounds(bounds);
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void getOutline(Outline outline)
	{
		outline.setRoundRect(mBoundsI, mRadius);
	}
	
	void setRadius(float radius)
	{
		if (radius == mRadius) return;
		mRadius = radius;
		updateBounds(null);
		invalidateSelf();
	}
	
	@Override
	public void setAlpha(int alpha)
	{
		
	}
	
	@Override
	public void setColorFilter(ColorFilter cf)
	{
		
	}
	
	@Override
	public int getOpacity()
	{
		return PixelFormat.TRANSLUCENT;
	}
	
	public float getRadius()
	{
		return mRadius;
	}
	
	public void setColor(int color)
	{
		mPaint.setColor(color);
		invalidateSelf();
	}
}