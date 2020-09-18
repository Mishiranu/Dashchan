package com.mishiranu.dashchan.util;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import com.mishiranu.dashchan.C;
import java.lang.reflect.Field;

public class AnimationUtils {
	public static final Interpolator ACCELERATE_DECELERATE_INTERPOLATOR = new AccelerateDecelerateInterpolator();
	public static final Interpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
	public static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

	public static void measureDynamicHeight(View view) {
		int width = view.getWidth();
		if (width <= 0) {
			View parent = (View) view.getParent();
			width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
		}
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		view.measure(widthMeasureSpec, heightMeasureSpec);
	}

	public static Animator ofHeight(View view, int from, int to, boolean needMeasure) {
		int realFrom = from;
		int realTo = to;
		boolean fromWC = realFrom == ViewGroup.LayoutParams.WRAP_CONTENT;
		boolean toWC = realTo == ViewGroup.LayoutParams.WRAP_CONTENT;
		if (fromWC || toWC) {
			if (needMeasure) {
				measureDynamicHeight(view);
			}
			int height = view.getMeasuredHeight();
			if (fromWC) {
				realFrom = height;
			}
			if (toWC) {
				realTo = height;
			}
		}
		ValueAnimator animator = ValueAnimator.ofInt(realFrom, realTo);
		HeightAnimatorListener listener = new HeightAnimatorListener(view, to);
		animator.addListener(listener);
		animator.addUpdateListener(listener);
		return animator;
	}

	private static class HeightAnimatorListener implements Animator.AnimatorListener,
			ValueAnimator.AnimatorUpdateListener {
		private final View view;
		private final int resultingHeight;

		public HeightAnimatorListener(View view, int resultingHeight) {
			this.view = view;
			this.resultingHeight = resultingHeight;
		}

		private void applyHeight(int height) {
			View view = this.view;
			view.getLayoutParams().height = height;
			view.requestLayout();
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			int height = (int) animation.getAnimatedValue();
			applyHeight(height);
		}

		@Override
		public void onAnimationStart(Animator animation) {}

		@Override
		public void onAnimationEnd(Animator animation) {
			applyHeight(resultingHeight);
		}

		@Override
		public void onAnimationCancel(Animator animation) {}

		@Override
		public void onAnimationRepeat(Animator animation) {}
	}

	public static class VisibilityListener implements Animator.AnimatorListener {
		private final View view;
		private final int visibility;

		public VisibilityListener(View view, int visibility) {
			this.view = view;
			this.visibility = visibility;
		}

		@Override
		public void onAnimationStart(Animator animation) {}

		@Override
		public void onAnimationEnd(Animator animation) {
			view.setVisibility(visibility);
		}

		@Override
		public void onAnimationCancel(Animator animation) {}

		@Override
		public void onAnimationRepeat(Animator animation) {}
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static final Field FIELD_VALUE_ANIMATOR_DURATION_SCALE;

	static {
		Field valueAnimatorDurationScaleField = null;
		if (!C.API_OREO) {
			try {
				valueAnimatorDurationScaleField = ValueAnimator.class.getDeclaredField("sDurationScale");
				valueAnimatorDurationScaleField.setAccessible(true);
			} catch (Exception e) {
				valueAnimatorDurationScaleField = null;
			}
		}
		FIELD_VALUE_ANIMATOR_DURATION_SCALE = valueAnimatorDurationScaleField;
	}

	public static boolean areAnimatorsEnabled() {
		if (C.API_OREO) {
			return ValueAnimator.areAnimatorsEnabled();
		} else {
			float durationScale = 1f;
			if (FIELD_VALUE_ANIMATOR_DURATION_SCALE != null) {
				try {
					durationScale = FIELD_VALUE_ANIMATOR_DURATION_SCALE.getFloat(null);
				} catch (Exception e) {
					// Ignore
				}
			}
			return durationScale > 0f;
		}
	}
}
