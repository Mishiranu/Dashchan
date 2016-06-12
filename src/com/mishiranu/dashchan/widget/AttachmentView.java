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

package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.graphics.RoundedCornersDrawable;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class AttachmentView extends ClickableView
{
	private final Rect mSource = new Rect();
	private final RectF mDestination = new RectF();
	private final Paint mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private TransparentTileDrawable mTileDrawable;
	
	private int mBackgroundColor;
	private Drawable mAdditionalOverlay;
	private boolean mCropEnabled = false;
	private boolean mFitSquare = false;
	private boolean mSfwMode = false;
	private boolean mDrawTouching = false;
	private RoundedCornersDrawable mCornersDrawable;
	
	private long mLastClickTime;

	private final float[] mWorkColorMatrix;
	private final ColorMatrix mColorMatrix1;
	private final ColorMatrix mColorMatrix2;
	
	public AttachmentView(Context context, AttributeSet attrs)
	{
		super(new ContextThemeWrapper(context, R.style.Theme_Gallery), attrs);
		setDrawingCacheEnabled(false);
		// Use old context to obtain background color.
		mBackgroundColor = ResourceUtils.getColor(context, R.attr.backgroundAttachment);
		if (C.API_LOLLIPOP)
		{
			mWorkColorMatrix = new float[] {1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0};
			mColorMatrix1 = new ColorMatrix(mWorkColorMatrix);
			mColorMatrix2 = new ColorMatrix(mWorkColorMatrix);
		}
		else
		{
			mWorkColorMatrix = null;
			mColorMatrix1 = null;
			mColorMatrix2 = null;
		}
	}
	
	public void setCropEnabled(boolean enabled)
	{
		mCropEnabled = enabled;
	}
	
	public void setFitSquare(boolean fitSquare)
	{
		mFitSquare = fitSquare;
	}
	
	public void setSfwMode(boolean sfwMode)
	{
		mSfwMode = sfwMode;
	}
	
	public void setAdditionalOverlay(int attrId, boolean invalidate)
	{
		Drawable drawable = attrId != 0 ? ResourceUtils.getDrawable(getContext(), attrId, 0) : null;
		if (mAdditionalOverlay != drawable)
		{
			mAdditionalOverlay = drawable;
		}
		if (invalidate) invalidate();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		switch (event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
			{
				if (System.currentTimeMillis() - mLastClickTime <= ViewConfiguration.getDoubleTapTimeout())
				{
					event.setAction(MotionEvent.ACTION_CANCEL);
					return false;
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
			{
				mLastClickTime = System.currentTimeMillis();
				break;
			}
		}
		return super.onTouchEvent(event);
	}
	
	@Override
	public void setBackgroundColor(int color)
	{
		mBackgroundColor = color;
	}
	
	public void setDrawTouching(boolean drawTouching)
	{
		mDrawTouching = drawTouching;
	}
	
	public void applyRoundedCorners(int backgroundColor)
	{
		float density = ResourceUtils.obtainDensity(this);
		int radius = (int) (2f * density + 0.5f);
		mCornersDrawable = new RoundedCornersDrawable(radius);
		mCornersDrawable.setColor(backgroundColor);
		if (getWidth() > 0) updateCornersBounds(getWidth(), getHeight());
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (mFitSquare)
		{
			int width = getMeasuredWidth();
			setMeasuredDimension(width, width);
		}
	}
	
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		super.onLayout(changed, left, top, right, bottom);
		updateCornersBounds(right - left, bottom - top);
		
	}
	
	private void updateCornersBounds(int width, int height)
	{
		if (mCornersDrawable != null) mCornersDrawable.setBounds(0, 0, width, height);
	}
	
	private boolean mEnqueuedTranslation = false;
	private long mImageApplyTime = 0L;
	
	public void resetTransition()
	{
		setNextTransitionEnabled(false);
	}
	
	public void enqueueTransition()
	{
		setNextTransitionEnabled(true);
	}
	
	private void setNextTransitionEnabled(boolean enabled)
	{
		mImageApplyTime = 0L;
		mEnqueuedTranslation = enabled;
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		Interpolator interpolator = AnimationUtils.DECELERATE_INTERPOLATOR;
		canvas.drawColor(mBackgroundColor);
		Bitmap bitmap = mBitmap != null && !mBitmap.isRecycled() ? mBitmap : null;
		boolean hasImage = bitmap != null;
		int vw = getWidth(), vh = getHeight();
		int dt = mImageApplyTime > 0L ? (int) (System.currentTimeMillis() - mImageApplyTime) : Integer.MAX_VALUE;
		float alpha = interpolator.getInterpolation(Math.min(dt / 200f, 1f));
		boolean invalidate = false;
		if (hasImage)
		{
			int bw = bitmap.getWidth(), bh = bitmap.getHeight();
			float scale, dx, dy;
			if (bw * vh > vw * bh ^ !mCropEnabled)
			{
				scale = (float) vh / (float) bh;
				dx = (int) ((vw - bw * scale) * 0.5f + 0.5f);
				dy = 0f;
			}
			else
			{
				scale = (float) vw / (float) bw;
				dx = 0f;
				dy = (int) ((vh - bh * scale) * 0.5f + 0.5f);
			}
			mSource.set(0, 0, bw, bh);
			mDestination.set(dx, dy, dx + bw * scale, dy + bh * scale);
			if (bitmap.hasAlpha())
			{
				int left = Math.max((int) (mDestination.left + 0.5f), 0);
				int top = Math.max((int) (mDestination.top + 0.5f), 0);
				int right = Math.min((int) (mDestination.right + 0.5f), vw);
				int bottom = Math.min((int) (mDestination.bottom + 0.5f), vh);
				if (mTileDrawable == null) mTileDrawable = new TransparentTileDrawable(getContext(), false);
				mTileDrawable.setBounds(left, top, right, bottom);
				mTileDrawable.draw(canvas);
			}
			mBitmapPaint.setAlpha((int) (0xff * alpha));
			if (C.API_LOLLIPOP)
			{
				float contrast = interpolator.getInterpolation(Math.min(dt / 300f, 1f));
				float saturation = interpolator.getInterpolation(Math.min(dt / 400f, 1f));
				if (saturation < 1f)
				{
					float[] matrix = mWorkColorMatrix;
					float contrastGain = 1f + 2f * (1f -  contrast);
					float contrastExtra = (1f - contrastGain) * 255f;
					matrix[0] = matrix[6] = matrix[12] = contrastGain;
					matrix[4] = matrix[9] = matrix[14] = contrastExtra;
					mColorMatrix2.set(matrix);
					mColorMatrix1.setSaturation(saturation);
					mColorMatrix1.postConcat(mColorMatrix2);
					mBitmapPaint.setColorFilter(new ColorMatrixColorFilter(mColorMatrix1));
				}
				else mBitmapPaint.setColorFilter(null);
				invalidate = saturation < 1f;
			}
			else invalidate = alpha < 1f;
			canvas.drawBitmap(bitmap, mSource, mDestination, mBitmapPaint);
		}
		if (mSfwMode) canvas.drawColor(mBackgroundColor & 0x00ffffff | 0xe0000000);
		if (mAdditionalOverlay != null)
		{
			if (hasImage && !mSfwMode) canvas.drawColor((int) (0x66 * alpha) << 24);
			int dw = mAdditionalOverlay.getIntrinsicWidth(), dh = mAdditionalOverlay.getIntrinsicHeight();
			int left = (vw - dw) / 2, top = (vh - dh) / 2, right = left + dw, bottom = top + dh;
			mAdditionalOverlay.setBounds(left, top, right, bottom);
			mAdditionalOverlay.draw(canvas);
		}
		if (mDrawTouching) super.draw(canvas);
		if (mCornersDrawable != null) mCornersDrawable.draw(canvas);
		if (invalidate) invalidate();
	}
	
	private Bitmap mBitmap;
	
	public void setImage(Bitmap bitmap)
	{
		mBitmap = bitmap;
		if (mEnqueuedTranslation)
		{
			mEnqueuedTranslation = false;
			mImageApplyTime = System.currentTimeMillis();
		}
		invalidate();
	}
}