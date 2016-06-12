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

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class GalleryBackgroundDrawable extends Drawable
{
	private final View mView;
	private final float mCenterX;
	private final float mCenterY;

	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final int[] mLocation = new int[2];
	
	private ValueAnimator mAnimator;
	private int mAlpha = 0xff;
	
	public GalleryBackgroundDrawable(View view, int[] imageViewPosition, int color)
	{
		if (imageViewPosition != null)
		{
			mView = view;
			mCenterX = imageViewPosition[0] + imageViewPosition[2] / 2f;
			mCenterY = imageViewPosition[1] + imageViewPosition[3] / 2f;
		}
		else
		{
			mView = null;
			mCenterX = -1;
			mCenterY = -1;
		}
		float density = ResourceUtils.obtainDensity(view);
		int colorFrom = Math.max((int) (Color.alpha(color) - 5), 0x00) << 24 | 0x00ffffff & color;
		int colorTo = Math.min((int) (Color.alpha(color) + 5), 0xff) << 24 | 0x00ffffff & color;
		Bitmap bitmap = GraphicsUtils.generateNoise(80, (int) density, colorFrom, colorTo);
		mPaint.setShader(new BitmapShader(bitmap, BitmapShader.TileMode.REPEAT, BitmapShader.TileMode.REPEAT));
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		Paint paint = mPaint;
		Rect bounds = getBounds();
		float t;
		if (mView != null)
		{
			if (mAnimator == null)
			{
				mAnimator = ValueAnimator.ofFloat(0f, 1f);
				mAnimator.setInterpolator(AnimationUtils.ACCELERATE_INTERPOLATOR);
				mAnimator.setDuration(300);
				mAnimator.start();
				mView.getLocationOnScreen(mLocation);
			}
			t = (float) mAnimator.getAnimatedValue();
		}
		else t = 1f;
		if (t >= 1f)
		{
			paint.setAlpha(mAlpha);
			canvas.drawRect(bounds, paint);
		}
		else
		{
			int width = bounds.width();
			int height = bounds.height();
			float cx = AnimationUtils.lerp(mCenterX - mLocation[0], bounds.left + width / 2f, t / 2f);
			float cy = AnimationUtils.lerp(mCenterY - mLocation[1], bounds.top + height / 2f, t / 2f);
			float radius = AnimationUtils.lerp(0f, (float) Math.sqrt(width * width + height * height), t);
			paint.setAlpha(((int) (0xff * t)));
			canvas.drawRect(bounds, paint);
			paint.setAlpha(((int) (0xff * (1f - t) / 2f)));
			canvas.drawCircle(cx, cy, radius, paint);
			invalidateSelf();
		}
	}
	
	@Override
	public int getOpacity()
	{
		return PixelFormat.TRANSLUCENT;
	}
	
	@Override
	public int getAlpha()
	{
		return mAlpha;
	}
	
	@Override
	public void setAlpha(int alpha)
	{
		if (mAlpha != alpha)
		{
			mAlpha = alpha;
			invalidateSelf();
		}
	}
	
	@Override
	public void setColorFilter(ColorFilter cf)
	{
		
	}
}