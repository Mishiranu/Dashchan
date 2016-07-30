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
 * ********************************************************************************
 * 
 * Copyright 2014-2016 Fukurou Mishiranu
 */

package com.mishiranu.dashchan.util;

import java.lang.reflect.Field;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.mishiranu.dashchan.C;

public class DrawerToggle implements DrawerLayout.DrawerListener
{
	private final Activity mActivity;
	private final DrawerLayout mDrawerLayout;
	
	private final ArrowDrawable mArrowDrawable;
	private final SlideDrawable mSlideDrawable;
	private Drawable mHomeAsUpIndicator;
	
	public static final int MODE_DISABLED = 0;
	public static final int MODE_DRAWER = 1;
	public static final int MODE_UP = 2;
	
	private int mMode = MODE_DISABLED;
	
	public DrawerToggle(Activity activity, DrawerLayout drawerLayout)
	{
		mActivity = activity;
		mDrawerLayout = drawerLayout;
		if (C.API_LOLLIPOP)
		{
			mArrowDrawable = new ArrowDrawable(activity);
			mSlideDrawable = null;
		}
		else
		{
			mArrowDrawable = null;
			mHomeAsUpIndicator = getThemeUpIndicatorObsolete();
			mSlideDrawable = new SlideDrawable(activity);
		}
	}
	
	private static final int DRAWER_CLOSE_DURATION;
	
	static
	{
		int duration;
		try
		{
			Field baseSettleDurationField = ViewDragHelper.class.getDeclaredField("BASE_SETTLE_DURATION");
			baseSettleDurationField.setAccessible(true);
			duration = (int) baseSettleDurationField.get(null);
		}
		catch (Exception e)
		{
			duration = 256;
		}
		DRAWER_CLOSE_DURATION = duration;
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void setDrawerIndicatorMode(int mode)
	{
		if (mMode != mode)
		{
			mMode = mode;
			ActionBar actionBar = mActivity.getActionBar();
			if (mode == MODE_DISABLED)
			{
				if (C.API_JELLY_BEAN_MR2) actionBar.setHomeAsUpIndicator(null);
				actionBar.setDisplayHomeAsUpEnabled(false);
			}
			else
			{
				actionBar.setDisplayHomeAsUpEnabled(true);
				if (C.API_LOLLIPOP)
				{
					mActivity.getActionBar().setHomeAsUpIndicator(mArrowDrawable);
					boolean open = mDrawerLayout.isDrawerOpen(Gravity.START) && mArrowDrawable.mPosition == 1f;
					if (!open)
					{
						ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
						animator.setDuration(DRAWER_CLOSE_DURATION);
						animator.addUpdateListener(new StateArrowAnimatorListener(mode == MODE_DRAWER));
						animator.start();
					}
				}
				else setActionBarUpIndicatorObsolete(mode == MODE_DRAWER ? mSlideDrawable : mHomeAsUpIndicator);
			}
		}
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	public void syncState()
	{
		if (mMode != MODE_DISABLED)
		{
			if (C.API_LOLLIPOP)
			{
				mArrowDrawable.setPosition(mMode == MODE_UP || mDrawerLayout.isDrawerOpen(Gravity.START) ? 1f : 0f);
				mActivity.getActionBar().setHomeAsUpIndicator(mArrowDrawable);
			}
			else
			{
				mSlideDrawable.setPosition(mDrawerLayout.isDrawerOpen(Gravity.START) ? 1f : 0f);
				setActionBarUpIndicatorObsolete(mMode == MODE_DRAWER ? mSlideDrawable : mHomeAsUpIndicator);
			}
		}
	}
	
	public void onConfigurationChanged()
	{
		if (!C.API_LOLLIPOP) mHomeAsUpIndicator = getThemeUpIndicatorObsolete();
		syncState();
	}
	
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item != null && item.getItemId() == android.R.id.home)
		{
			if (mDrawerLayout.getDrawerLockMode(Gravity.START) != DrawerLayout.LOCK_MODE_UNLOCKED) return false;
			if (mMode == MODE_DRAWER)
			{
				if (mDrawerLayout.isDrawerVisible(Gravity.START))
				{
					mDrawerLayout.closeDrawer(Gravity.START);
				}
				else
				{
					mDrawerLayout.openDrawer(Gravity.START);
				}
				return true;
			}
			else if (mMode == MODE_UP)
			{
				if (mDrawerLayout.isDrawerVisible(Gravity.START))
				{
					mDrawerLayout.closeDrawer(Gravity.START);
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void onDrawerSlide(View drawerView, float slideOffset)
	{
		if (C.API_LOLLIPOP)
		{
			if (mMode == MODE_DRAWER) mArrowDrawable.setPosition(slideOffset);
		}
		else
		{
			float glyphOffset = mSlideDrawable.getPosition();
			if (slideOffset > 0.5f) glyphOffset = Math.max(glyphOffset, Math.max(0.f, slideOffset - 0.5f) * 2);
			else glyphOffset = Math.min(glyphOffset, slideOffset * 2);
			mSlideDrawable.setPosition(glyphOffset);
		}
	}
	
	@Override
	public void onDrawerOpened(View drawerView)
	{
		if (C.API_LOLLIPOP)
		{
			if (mMode == MODE_DRAWER) mArrowDrawable.setPosition(1f);
		}
		else mSlideDrawable.setPosition(1);
	}
	
	@Override
	public void onDrawerClosed(View drawerView)
	{
		if (C.API_LOLLIPOP)
		{
			if (mMode == MODE_DRAWER) mArrowDrawable.setPosition(0f);
		}
		else mSlideDrawable.setPosition(0);
	}
	
	@Override
	public void onDrawerStateChanged(int newState)
	{
		
	}
	
	private static final float ARROW_HEAD_ANGLE = (float) Math.toRadians(45);
	
	private class ArrowDrawable extends Drawable
	{
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path mPath = new Path();
		
		private final float mBarThickness;
		private final float mTopBottomArrowSize;
		private final float mBarSize;
		private final float mMiddleArrowSize;
		private final float mBarGap;
		private final int mSize;
		
		private boolean mVerticalMirror = false;
		private float mPosition;
		
		public ArrowDrawable(Context context)
		{
			mPaint.setAntiAlias(true);
			mPaint.setColor(0xffffffff);
			float density = ResourceUtils.obtainDensity(context);
			mSize = (int) (24f * density);
			mBarSize = 16f * density;
			mTopBottomArrowSize = 9.5f * density;
			mBarThickness = 2f * density;
			mBarGap = 3f * density;
			mMiddleArrowSize = 13.6f * density;
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setStrokeJoin(Paint.Join.ROUND);
			mPaint.setStrokeCap(Paint.Cap.SQUARE);
			mPaint.setStrokeWidth(mBarThickness);
		}
		
		public void setPosition(float position)
		{
			position = Math.min(1f, Math.max(0f, position));
			if (position == 1f) mVerticalMirror = true;
			else if (position == 0f) mVerticalMirror = false;
			mPosition = position;
			invalidateSelf();
		}
		
		@Override
		public int getIntrinsicWidth()
		{
			return mSize;
		}
		
		@Override
		public int getIntrinsicHeight()
		{
			return mSize;
		}
		
		@Override
		public void draw(Canvas canvas)
		{
			Rect bounds = getBounds();
			boolean rtl = isLayoutRtl();
			float position = mPosition;
			float arrowSize = AnimationUtils.lerp(mBarSize, mTopBottomArrowSize, position);
			float middleBarSize = AnimationUtils.lerp(mBarSize, mMiddleArrowSize, position);
			float middleBarCut = AnimationUtils.lerp(0f, mBarThickness / 2f, position);
			float rotation = AnimationUtils.lerp(0f, ARROW_HEAD_ANGLE, position);
			float canvasRotate = AnimationUtils.lerp(rtl ? 0f : -180f, rtl ? 180f : 0f, position);
			float topBottomBarOffset = AnimationUtils.lerp(mBarGap + mBarThickness, 0f, position);
			mPath.rewind();
			float arrowEdge = -middleBarSize / 2f + 0.5f;
			mPath.moveTo(arrowEdge + middleBarCut, 0f);
			mPath.rLineTo(middleBarSize - middleBarCut, 0f);
			float arrowWidth = Math.round(arrowSize * Math.cos(rotation));
			float arrowHeight = Math.round(arrowSize * Math.sin(rotation));
			mPath.moveTo(arrowEdge, topBottomBarOffset);
			mPath.rLineTo(arrowWidth, arrowHeight);
			mPath.moveTo(arrowEdge, -topBottomBarOffset);
			mPath.rLineTo(arrowWidth, -arrowHeight);
			mPath.moveTo(0f, 0f);
			mPath.close();
			canvas.save();
			canvas.rotate(canvasRotate * ((mVerticalMirror ^ rtl) ? -1f : 1f), bounds.centerX(), bounds.centerY());
			canvas.translate(bounds.centerX(), bounds.centerY());
			canvas.drawPath(mPath, mPaint);
			canvas.restore();
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
		public int getOpacity()
		{
			return PixelFormat.TRANSLUCENT;
		}
	}

	private class StateArrowAnimatorListener implements ValueAnimator.AnimatorUpdateListener
	{
		private final boolean mEnable;
		
		public StateArrowAnimatorListener(boolean enable)
		{
			mEnable = enable;
		}
		
		@Override
		public void onAnimationUpdate(ValueAnimator animation)
		{
			float value = (float) animation.getAnimatedValue();
			mArrowDrawable.setPosition(mEnable ? 1f - value : value);
		}
	}
	
	private class SlideDrawable extends Drawable
	{
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final int mSize;
		
		private float mPosition;
		
		private SlideDrawable(Context context)
		{
			mPaint.setColor(0xff979797);
			float density = ResourceUtils.obtainDensity(context);
			mSize = (int) (16f * density);
		}
		
		public void setPosition(float position)
		{
			mPosition = position;
			invalidateSelf();
		}
		
		public float getPosition()
		{
			return mPosition;
		}
		
		@Override
		public int getIntrinsicWidth()
		{
			return mSize;
		}
		
		@Override
		public int getIntrinsicHeight()
		{
			return mSize;
		}
		
		@Override
		public void draw(Canvas canvas)
		{
			Rect bounds = getBounds();
			canvas.save();
			canvas.translate(bounds.left, bounds.top);
			boolean rtl = isLayoutRtl();
			if (rtl)
			{
				canvas.translate(bounds.width(), 0);
				canvas.scale(-1, 1);
			}
			canvas.scale(bounds.width() / 48f, bounds.height() / 48f);
			canvas.translate(-16f * mPosition, 0);
			canvas.drawRect(0, 4, 30, 12, mPaint);
			canvas.drawRect(0, 22, 30, 30, mPaint);
			canvas.drawRect(0, 40, 30, 48, mPaint);
			canvas.restore();
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
		public int getOpacity()
		{
			return PixelFormat.TRANSLUCENT;
		}
	}
	
	private static final int[] THEME_ATTRS = new int[] {android.R.attr.homeAsUpIndicator};
	
	private Drawable getThemeUpIndicatorObsolete()
	{
		if (C.API_JELLY_BEAN_MR2)
		{
			TypedArray a = mActivity.getActionBar().getThemedContext().obtainStyledAttributes(null,
					THEME_ATTRS, android.R.attr.actionBarStyle, 0);
			Drawable result = a.getDrawable(0);
			a.recycle();
			return result;
		}
		else
		{
			TypedArray a = mActivity.obtainStyledAttributes(THEME_ATTRS);
			Drawable result = a.getDrawable(0);
			a.recycle();
			return result;
		}
	}
	
	private ImageView mUpIndicatorView;
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private void setActionBarUpIndicatorObsolete(Drawable upDrawable)
	{
		if (C.API_JELLY_BEAN_MR2)
		{
			mActivity.getActionBar().setHomeAsUpIndicator(upDrawable);
		}
		else
		{
			if (mUpIndicatorView == null)
			{
				View home = mActivity.findViewById(android.R.id.home);
				if (home == null) return;
				ViewGroup parent = (ViewGroup) home.getParent();
				int childCount = parent.getChildCount();
				if (childCount != 2) return;
				View first = parent.getChildAt(0);
				View second = parent.getChildAt(1);
				View up = first.getId() == android.R.id.home ? second : first;
				if (up instanceof ImageView) mUpIndicatorView = (ImageView) up;
			}
			if (mUpIndicatorView != null) mUpIndicatorView.setImageDrawable(upDrawable);
		}
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private boolean isLayoutRtl()
	{
		return C.API_JELLY_BEAN_MR1 ? mActivity.getWindow().getDecorView().getLayoutDirection()
				== View.LAYOUT_DIRECTION_RTL : false;
	}
}