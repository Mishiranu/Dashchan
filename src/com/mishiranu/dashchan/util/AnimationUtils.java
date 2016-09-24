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

package com.mishiranu.dashchan.util;

import java.lang.reflect.Field;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.mishiranu.dashchan.content.model.PostItem;

public class AnimationUtils
{
	public static final Interpolator ACCELERATE_DECELERATE_INTERPOLATOR = new AccelerateDecelerateInterpolator();
	public static final Interpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
	public static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

	private static final Field FIELD_START_DELAY;
	private static final Field FIELD_DURATION_SCALE;

	static
	{
		Field fieldStartDelay;
		Field fieldDurationScale;
		try
		{
			fieldStartDelay = ValueAnimator.class.getDeclaredField("mStartDelay");
			fieldStartDelay.setAccessible(true);
			fieldDurationScale = ValueAnimator.class.getDeclaredField("sDurationScale");
			fieldDurationScale.setAccessible(true);
		}
		catch (Exception e)
		{
			fieldStartDelay = null;
			fieldDurationScale = null;
		}
		FIELD_START_DELAY = fieldStartDelay;
		FIELD_DURATION_SCALE = fieldDurationScale;
	}

	public static float getAnimatorDurationScale()
	{
		try
		{
			return FIELD_DURATION_SCALE.getFloat(null);
		}
		catch (Exception e)
		{
			return 1f;
		}
	}

	public static void measureDynamicHeight(View view)
	{
		int width = view.getWidth();
		if (width <= 0)
		{
			View parent = (View) view.getParent();
			width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
		}
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		view.measure(widthMeasureSpec, heightMeasureSpec);
	}

	public static Animator ofHeight(View view, int from, int to, boolean needMeasure)
	{
		int realFrom = from;
		int realTo = to;
		boolean fromWC = realFrom == ViewGroup.LayoutParams.WRAP_CONTENT;
		boolean toWC = realTo == ViewGroup.LayoutParams.WRAP_CONTENT;
		if (fromWC || toWC)
		{
			if (needMeasure) measureDynamicHeight(view);
			int height = view.getMeasuredHeight();
			if (fromWC) realFrom = height;
			if (toWC) realTo = height;
		}
		ValueAnimator animator = ValueAnimator.ofInt(realFrom, realTo);
		HeightAnimatorListener listener = new HeightAnimatorListener(view, to);
		animator.addListener(listener);
		animator.addUpdateListener(listener);
		return animator;
	}

	public static Animator ofNewPostWithStartDelay(View view, PostItem postItem, int color)
	{
		ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), color, color & 0x00ffffff);
		final long delay = 500;
		animator.setStartDelay(delay);
		if (FIELD_START_DELAY != null)
		{
			try
			{
				FIELD_START_DELAY.setLong(animator, delay);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		animator.addUpdateListener(new NewPostAnimatorListener(view, postItem, color));
		return animator;
	}

	private static class HeightAnimatorListener implements Animator.AnimatorListener,
			ValueAnimator.AnimatorUpdateListener
	{
		private final View mView;
		private final int mResultingHeight;

		public HeightAnimatorListener(View view, int resultingHeight)
		{
			mView = view;
			mResultingHeight = resultingHeight;
		}

		private void applyHeight(int height)
		{
			View view = mView;
			view.getLayoutParams().height = height;
			view.requestLayout();
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation)
		{
			int height = (int) animation.getAnimatedValue();
			applyHeight(height);
		}

		@Override
		public void onAnimationStart(Animator animation)
		{

		}

		@Override
		public void onAnimationEnd(Animator animation)
		{
			applyHeight(mResultingHeight);
		}

		@Override
		public void onAnimationCancel(Animator animation)
		{

		}

		@Override
		public void onAnimationRepeat(Animator animation)
		{

		}
	}

	public static class VisibilityListener implements Animator.AnimatorListener
	{
		private final View mView;
		private final int mVisibility;

		public VisibilityListener(View view, int visibility)
		{
			mView = view;
			mVisibility = visibility;
		}

		@Override
		public void onAnimationStart(Animator animation)
		{

		}

		@Override
		public void onAnimationEnd(Animator animation)
		{
			mView.setVisibility(mVisibility);
		}

		@Override
		public void onAnimationCancel(Animator animation)
		{

		}

		@Override
		public void onAnimationRepeat(Animator animation)
		{

		}
	}

	private static class NewPostAnimatorListener implements ValueAnimator.AnimatorUpdateListener
	{
		private final ColorDrawable mDrawable;
		private final PostItem mPostItem;
		private boolean mApplied = false;

		public NewPostAnimatorListener(View view, PostItem postItem, int color)
		{
			mDrawable = new ColorDrawable(color);
			view.setBackground(mDrawable);
			mPostItem = postItem;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation)
		{
			if (!mApplied)
			{
				mApplied = true;
				mPostItem.setUnread(false);
			}
			mDrawable.setColor((int) animation.getAnimatedValue());
		}
	}

	public static float lerp(float a, float b, float t)
	{
		return a + (b - a) * t;
	}
}