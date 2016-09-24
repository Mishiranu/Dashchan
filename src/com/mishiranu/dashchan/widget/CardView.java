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

package com.mishiranu.dashchan.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ResourceUtils;

public class CardView extends FrameLayout
{
	private static final Implementation IMPLEMENTATION = C.API_LOLLIPOP
			? new CardViewLollipop() : new CardViewJellyBean();

	private final boolean mInitialized;

	public CardView(Context context)
	{
		this(context, null);
	}

	public CardView(Context context, AttributeSet attrs)
	{
		this(context, attrs, 0);
	}

	public CardView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
		float density = ResourceUtils.obtainDensity(context);
		float size = 1f * density + 0.5f;
		IMPLEMENTATION.initialize(this, context, mBackgroundColor, size);
		mInitialized = true;
	}

	private final int[] mMeasureSpecs = new int[2];

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		mMeasureSpecs[0] = widthMeasureSpec;
		mMeasureSpecs[1] = heightMeasureSpec;
		IMPLEMENTATION.measure(this, mMeasureSpecs);
		super.onMeasure(mMeasureSpecs[0], mMeasureSpecs[1]);
	}

	private int mBackgroundColor;

	private void setBackgroundColorInternal(int color)
	{
		mBackgroundColor = color;
		if (mInitialized) IMPLEMENTATION.setBackgroundColor(this, color);
	}

	@SuppressWarnings("deprecation")
	@Override
	@Deprecated
	public void setBackgroundDrawable(Drawable background)
	{
		if (background instanceof ColorDrawable)
		{
			int color = ((ColorDrawable) background).getColor();
			setBackgroundColorInternal(color);
			return;
		}
		super.setBackgroundDrawable(background);
	}

	@Override
	public void setBackgroundColor(int color)
	{
		setBackgroundColorInternal(color);
	}

	public int getBackgroundColor()
	{
		return mBackgroundColor;
	}

	private final static double COS_45 = Math.cos(Math.toRadians(45));
	private final static float SHADOW_MULTIPLIER = 1.5f;

	public static float calculateVerticalPadding(float maxShadowSize, float cornerRadius)
	{
		return (float) (maxShadowSize * SHADOW_MULTIPLIER + (1 - COS_45) * cornerRadius);
	}

	public static float calculateHorizontalPadding(float maxShadowSize, float cornerRadius)
	{
		return (float) (maxShadowSize + (1 - COS_45) * cornerRadius);
	}

	private interface Implementation
	{
		public void initialize(CardView cardView, Context context, int backgroundColor, float size);
		public void measure(CardView cardView, int[] measureSpecs);
		public void setBackgroundColor(CardView cardView, int color);
	}

	private static class CardViewJellyBean implements CardView.Implementation
	{
		@SuppressWarnings("deprecation")
		@Override
		public void initialize(CardView cardView, Context context, int backgroundColor, float size)
		{
			RoundRectDrawableWithShadow background = new RoundRectDrawableWithShadow(context.getResources(),
					backgroundColor, size);
			cardView.setBackgroundDrawable(background);
			Rect shadowPadding = new Rect();
			background.getMaxShadowAndCornerPadding(shadowPadding);
			cardView.setMinimumHeight((int) Math.ceil(background.getMinHeight()));
			cardView.setMinimumWidth((int) Math.ceil(background.getMinWidth()));
			cardView.setPadding(shadowPadding.left, shadowPadding.top, shadowPadding.right, shadowPadding.bottom);
		}

		@Override
		public void measure(CardView cardView, int[] measureSpecs)
		{
			RoundRectDrawableWithShadow background = (RoundRectDrawableWithShadow) cardView.getBackground();
			int widthMode = CardView.MeasureSpec.getMode(measureSpecs[0]);
			switch (widthMode)
			{
				case CardView.MeasureSpec.EXACTLY:
				case CardView.MeasureSpec.AT_MOST:
				{
					int minWidth = (int) Math.ceil(background.getMinWidth());
					measureSpecs[0] = CardView.MeasureSpec.makeMeasureSpec(Math.max(minWidth,
							CardView.MeasureSpec.getSize(measureSpecs[0])), widthMode);
					break;
				}
				case CardView.MeasureSpec.UNSPECIFIED: break;
			}
			int heightMode = CardView.MeasureSpec.getMode(measureSpecs[1]);
			switch (heightMode)
			{
				case CardView.MeasureSpec.EXACTLY:
				case CardView.MeasureSpec.AT_MOST:
				{
					int minHeight = (int) Math.ceil(background.getMinHeight());
					measureSpecs[1] = CardView.MeasureSpec.makeMeasureSpec(Math.max(minHeight,
							CardView.MeasureSpec.getSize(measureSpecs[1])), heightMode);
					break;
				}
				case CardView.MeasureSpec.UNSPECIFIED: break;
			}
		}

		@Override
		public void setBackgroundColor(CardView cardView, int color)
		{
			((RoundRectDrawableWithShadow) cardView.getBackground()).setColor(color);
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static class CardViewLollipop implements CardView.Implementation
	{
		@SuppressWarnings("deprecation")
		@Override
		public void initialize(CardView cardView, Context context, int backgroundColor, float size)
		{
			RoundRectDrawable backgroundDrawable = new RoundRectDrawable(backgroundColor, size);
			cardView.setBackgroundDrawable(backgroundDrawable);
			cardView.setClipToOutline(true);
			cardView.setElevation(size);
			backgroundDrawable.setPadding(size);
			float elevation = backgroundDrawable.getPadding();
			float radius = backgroundDrawable.getRadius();
			int hPadding = (int) Math.ceil(calculateHorizontalPadding(elevation, radius));
			int vPadding = (int) Math.ceil(calculateVerticalPadding(elevation, radius));
			cardView.setPadding(hPadding, vPadding, hPadding, vPadding);
		}

		@Override
		public void measure(CardView cardView, int[] measureSpecs)
		{

		}

		@Override
		public void setBackgroundColor(CardView cardView, int color)
		{
			((RoundRectDrawable) (cardView.getBackground())).setColor(color);
		}
	}

	private static class RoundRectDrawable extends Drawable
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
			float vInset = calculateVerticalPadding(mPadding, mRadius);
			float hInset = calculateHorizontalPadding(mPadding, mRadius);
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

	private static class RoundRectDrawableWithShadow extends Drawable
	{
		private final int mInsetShadow;

		private Paint mPaint;
		private Paint mCornerShadowPaint;
		private Paint mEdgeShadowPaint;

		private final RectF mCardBounds;
		private float mCornerRadius;
		private Path mCornerShadowPath;

		private float mRawMaxShadowSize;
		private float mShadowSize;
		private float mRawShadowSize;

		private boolean mDirty = true;
		private boolean mPrintedShadowClipWarning = false;

		public RoundRectDrawableWithShadow(Resources resources, int backgroundColor, float size)
		{
			float density = ResourceUtils.obtainDensity(resources);
			mInsetShadow = (int) (1f * density + 0.5f);
			mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
			mPaint.setColor(backgroundColor);
			mCornerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
			mCornerShadowPaint.setStyle(Paint.Style.FILL);
			mCornerRadius = (int) (size + .5f);
			mCardBounds = new RectF();
			mEdgeShadowPaint = new Paint(mCornerShadowPaint);
			mEdgeShadowPaint.setAntiAlias(false);
			setShadowSize(size, size);
		}

		private int toEven(float value)
		{
			int i = (int) (value + .5f);
			if (i % 2 == 1) return i - 1;
			return i;
		}

		@Override
		public void setAlpha(int alpha)
		{
			mPaint.setAlpha(alpha);
			mCornerShadowPaint.setAlpha(alpha);
			mEdgeShadowPaint.setAlpha(alpha);
		}

		@Override
		protected void onBoundsChange(Rect bounds)
		{
			super.onBoundsChange(bounds);
			mDirty = true;
		}

		public void setShadowSize(float shadowSize, float maxShadowSize)
		{
			shadowSize = toEven(shadowSize);
			maxShadowSize = toEven(maxShadowSize);
			if (shadowSize > maxShadowSize)
			{
				shadowSize = maxShadowSize;
				if (!mPrintedShadowClipWarning) mPrintedShadowClipWarning = true;
			}
			if (mRawShadowSize == shadowSize && mRawMaxShadowSize == maxShadowSize) return;
			mRawShadowSize = shadowSize;
			mRawMaxShadowSize = maxShadowSize;
			mShadowSize = (int) (shadowSize * SHADOW_MULTIPLIER + mInsetShadow + .5f);
			mDirty = true;
			invalidateSelf();
		}

		@Override
		public boolean getPadding(Rect padding)
		{
			int vOffset = (int) Math.ceil(calculateVerticalPadding(mRawMaxShadowSize, mCornerRadius));
			int hOffset = (int) Math.ceil(calculateHorizontalPadding(mRawMaxShadowSize, mCornerRadius));
			padding.set(hOffset, vOffset, hOffset, vOffset);
			return true;
		}

		@Override
		public void setColorFilter(ColorFilter cf)
		{
			mPaint.setColorFilter(cf);
			mCornerShadowPaint.setColorFilter(cf);
			mEdgeShadowPaint.setColorFilter(cf);
		}

		@Override
		public int getOpacity()
		{
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public void draw(Canvas canvas)
		{
			if (mDirty)
			{
				buildComponents(getBounds());
				mDirty = false;
			}
			canvas.translate(0, mRawShadowSize / 2);
			drawShadow(canvas);
			canvas.translate(0, -mRawShadowSize / 2);
			canvas.drawRoundRect(mCardBounds, mCornerRadius, mCornerRadius, mPaint);
		}

		private void drawShadow(Canvas canvas)
		{
			final float edgeShadowTop = -mCornerRadius - mShadowSize;
			final float inset = mCornerRadius + mInsetShadow + mRawShadowSize / 2;
			final boolean drawHorizontalEdges = mCardBounds.width() - 2 * inset > 0;
			final boolean drawVerticalEdges = mCardBounds.height() - 2 * inset > 0;
			int saved = canvas.save();
			canvas.translate(mCardBounds.left + inset, mCardBounds.top + inset);
			canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
			if (drawHorizontalEdges)
			{
				canvas.drawRect(0, edgeShadowTop, mCardBounds.width() - 2 * inset, -mCornerRadius, mEdgeShadowPaint);
			}
			canvas.restoreToCount(saved);
			saved = canvas.save();
			canvas.translate(mCardBounds.right - inset, mCardBounds.bottom - inset);
			canvas.rotate(180f);
			canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
			if (drawHorizontalEdges)
			{
				canvas.drawRect(0, edgeShadowTop, mCardBounds.width() - 2 * inset, -mCornerRadius + mShadowSize,
						mEdgeShadowPaint);
			}
			canvas.restoreToCount(saved);
			saved = canvas.save();
			canvas.translate(mCardBounds.left + inset, mCardBounds.bottom - inset);
			canvas.rotate(270f);
			canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
			if (drawVerticalEdges)
			{
				canvas.drawRect(0, edgeShadowTop, mCardBounds.height() - 2 * inset, -mCornerRadius, mEdgeShadowPaint);
			}
			canvas.restoreToCount(saved);
			saved = canvas.save();
			canvas.translate(mCardBounds.right - inset, mCardBounds.top + inset);
			canvas.rotate(90f);
			canvas.drawPath(mCornerShadowPath, mCornerShadowPaint);
			if (drawVerticalEdges)
			{
				canvas.drawRect(0, edgeShadowTop, mCardBounds.height() - 2 * inset, -mCornerRadius, mEdgeShadowPaint);
			}
			canvas.restoreToCount(saved);
		}

		private void buildShadowCorners()
		{
			final int shadowStartColor = 0x37000000;
			final int shadowEndColor = 0x03000000;
			RectF innerBounds = new RectF(-mCornerRadius, -mCornerRadius, mCornerRadius, mCornerRadius);
			RectF outerBounds = new RectF(innerBounds);
			outerBounds.inset(-mShadowSize, -mShadowSize);
			if (mCornerShadowPath == null) mCornerShadowPath = new Path();
			else mCornerShadowPath.reset();
			mCornerShadowPath.setFillType(Path.FillType.EVEN_ODD);
			mCornerShadowPath.moveTo(-mCornerRadius, 0);
			mCornerShadowPath.rLineTo(-mShadowSize, 0);
			mCornerShadowPath.arcTo(outerBounds, 180f, 90f, false);
			mCornerShadowPath.arcTo(innerBounds, 270f, -90f, false);
			mCornerShadowPath.close();
			float startRatio = mCornerRadius / (mCornerRadius + mShadowSize);
			mCornerShadowPaint.setShader(new RadialGradient(0, 0, mCornerRadius + mShadowSize,
					new int[] {shadowStartColor, shadowStartColor, shadowEndColor},
					new float[] {0f, startRatio, 1f}, Shader.TileMode.CLAMP));
			mEdgeShadowPaint.setShader(new LinearGradient(0, -mCornerRadius + mShadowSize, 0,
					-mCornerRadius - mShadowSize, new int[] {shadowStartColor, shadowStartColor, shadowEndColor},
					new float[] {0f, .5f, 1f}, Shader.TileMode.CLAMP));
			mEdgeShadowPaint.setAntiAlias(false);
		}

		private void buildComponents(Rect bounds)
		{
			final float verticalOffset = mRawMaxShadowSize * SHADOW_MULTIPLIER;
			mCardBounds.set(bounds.left + mRawMaxShadowSize, bounds.top + verticalOffset,
					bounds.right - mRawMaxShadowSize, bounds.bottom - verticalOffset);
			buildShadowCorners();
		}

		public void getMaxShadowAndCornerPadding(Rect into)
		{
			getPadding(into);
		}

		public float getMinWidth()
		{
			final float content = 2 * Math.max(mRawMaxShadowSize, mCornerRadius + mInsetShadow + mRawMaxShadowSize / 2);
			return content + (mRawMaxShadowSize + mInsetShadow) * 2;
		}

		public float getMinHeight()
		{
			final float content = 2 * Math.max(mRawMaxShadowSize, mCornerRadius + mInsetShadow +
					mRawMaxShadowSize * SHADOW_MULTIPLIER / 2);
			return content + (mRawMaxShadowSize * SHADOW_MULTIPLIER + mInsetShadow) * 2;
		}

		public void setColor(int color)
		{
			mPaint.setColor(color);
			invalidateSelf();
		}
	}
}