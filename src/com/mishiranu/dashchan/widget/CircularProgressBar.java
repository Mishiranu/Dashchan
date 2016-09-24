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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class CircularProgressBar extends View
{
	private static final int INDETERMINATE_LOLLIPOP_TIME = 6665;
	private static final int PROGRESS_TRANSIENT_TIME = 500;
	private static final int VISIBILITY_TRANSIENT_TIME = 500;

	private static final int TRANSIENT_NONE = 0;
	private static final int TRANSIENT_INDETERMINATE_PROGRESS = 1;
	private static final int TRANSIENT_PROGRESS_INDETERMINATE = 2;

	private final Paint mPaint;
	private final Path mPath = new Path();
	private final RectF mRectF = new RectF();

	private final Drawable mIndeterminateDrawable;
	private final int mIndeterminateDuration;

	private final Interpolator mLollipopStartInterpolator;
	private final Interpolator mLollipopEndInterpolator;

	private final long mStartTime = System.currentTimeMillis();

	private int mTransientState = TRANSIENT_NONE;
	private final float[] mCircularData = C.API_LOLLIPOP ? new float[2] : null;
	private final float[] mTransientData = C.API_LOLLIPOP ? new float[2] : null;
	private long mTimeTransientStart;

	private boolean mIndeterminate = true;
	private boolean mVisible = false;
	private long mTimeVisibilitySet;

	private float mProgress = 0f;
	private float mTransientProgress = 0;
	private long mTimeProgressChange;

	public CircularProgressBar(Context context)
	{
		this(context, null);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public CircularProgressBar(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeCap(Paint.Cap.SQUARE);
		mPaint.setStrokeJoin(Paint.Join.MITER);
		if (C.API_LOLLIPOP)
		{
			Path startPath = new Path();
			startPath.lineTo(0.5f, 0f);
			startPath.cubicTo(0.7f, 0f, 0.6f, 1f, 1f, 1f);
			mLollipopStartInterpolator = new PathInterpolator(startPath);
			Path endPath = new Path();
			endPath.cubicTo(0.2f, 0f, 0.1f, 1f, 0.5f, 1f);
			endPath.lineTo(1f, 1f);
			mLollipopEndInterpolator = new PathInterpolator(endPath);
			mIndeterminateDrawable = null;
			mIndeterminateDuration = 0;
		}
		else
		{
			mLollipopStartInterpolator = null;
			mLollipopEndInterpolator = null;
			TypedArray typedArray = context.obtainStyledAttributes(android.R.style.Widget_Holo_ProgressBar_Large,
					new int[] {android.R.attr.indeterminateDrawable, android.R.attr.indeterminateDuration});
			mIndeterminateDrawable = typedArray.getDrawable(0);
			mIndeterminateDuration = typedArray.getInteger(1, 3500);
			typedArray.recycle();
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		float density = ResourceUtils.obtainDensity(this);
		int size = (int) (72f * density + 0.5f);
		int width = size + getPaddingLeft() + getPaddingRight();
		int height = size + getPaddingTop() + getPaddingBottom();
		setMeasuredDimension(resolveSizeAndState(width, widthMeasureSpec, 0),
				resolveSizeAndState(height, heightMeasureSpec, 0));
	}

	private float getValue(long currentTime, long fromTime, float max)
	{
		return ((currentTime - fromTime) % max) / max;
	}

	private boolean mQueuedVisible;

	private final Runnable mSetVisibleRunnable = () ->
	{
		mVisible = mQueuedVisible;
		mTimeVisibilitySet = System.currentTimeMillis();
		invalidate();
	};

	public void setVisible(boolean visible, boolean forced)
	{
		removeCallbacks(mSetVisibleRunnable);
		if (mVisible != visible)
		{
			mQueuedVisible = visible;
			long delta = System.currentTimeMillis() - mTimeVisibilitySet;
			if (delta < 10)
			{
				mVisible = visible;
				mTimeVisibilitySet = 0L;
				invalidate();
			}
			else if (!visible && !forced && delta < VISIBILITY_TRANSIENT_TIME)
			{
				postDelayed(mSetVisibleRunnable, VISIBILITY_TRANSIENT_TIME - delta);
			}
			else mSetVisibleRunnable.run();
		}
	}

	public void cancelVisibilityTransient()
	{
		removeCallbacks(mSetVisibleRunnable);
		mTimeVisibilitySet = 0L;
		mVisible = mQueuedVisible;
		invalidate();
	}

	private boolean calculateLollipopProgress()
	{
		float progress = calculateTransientProgress();
		float arcStart = (progress - 1f) / 4f;
		float arcLength = progress - arcStart;
		mCircularData[0] = arcStart;
		mCircularData[1] = arcLength;
		return progress != mProgress;
	}

	private void calculateLollipopIndeterminate(long currentTime)
	{
		float rotationValue = getValue(currentTime, mStartTime, INDETERMINATE_LOLLIPOP_TIME);
		float animationValue = rotationValue * 5f % 1f;
		float trimOffset = 0.25f * animationValue;
		float trimStart = 0.75f * mLollipopStartInterpolator.getInterpolation(animationValue) + trimOffset;
		float trimEnd = 0.75f * mLollipopEndInterpolator.getInterpolation(animationValue) + trimOffset;
		float rotation = 2f * rotationValue;
		float arcStart = trimStart;
		float arcLength = trimEnd - arcStart;
		arcStart += rotation;
		mCircularData[0] = arcStart % 1f;
		mCircularData[1] = arcLength;
	}

	private boolean calculateLollipopTransient(float arcStart, float arcLength,
			float desiredStart, float desiredLength, long interval)
	{
		boolean finished = false;
		long timeDelta = System.currentTimeMillis() - mTimeTransientStart;
		if (timeDelta >= interval)
		{
			timeDelta = interval;
			finished = true;
		}
		float value = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation((float) timeDelta / interval);
		arcStart = arcStart + (desiredStart - arcStart) * value;
		arcLength = arcLength + (desiredLength - arcLength) * value;
		mCircularData[0] = arcStart % 1f;
		mCircularData[1] = arcLength;
		return finished;
	}

	private boolean calculateLollipopTransientIndeterminateProgress()
	{
		float arcStart = mTransientData[0];
		float arcLength = mTransientData[1];
		float desiredStart = 0.75f;
		float desiredLength = 0.25f;
		if (arcStart >= desiredStart - 0.15f || arcLength >= desiredLength) arcStart -= 1f;
		int interval = (int) (800f * (desiredStart - arcStart));
		return calculateLollipopTransient(arcStart, arcLength, desiredStart, desiredLength, interval);
	}

	private boolean calculateLollipopTransientProgressIndeterminate()
	{
		float arcStart = mTransientData[0];
		float arcLength = mTransientData[1];
		calculateLollipopIndeterminate(mTimeTransientStart + 1000L);
		float desiredStart = mCircularData[0];
		float desiredLength = mCircularData[1];
		if (arcStart >= desiredStart || arcLength >= desiredLength) arcStart -= 1f;
		return calculateLollipopTransient(arcStart, arcLength, desiredStart, desiredLength, 1000L);
	}

	public void setIndeterminate(boolean indeterminate)
	{
		if (mIndeterminate != indeterminate)
		{
			long time = System.currentTimeMillis();
			if (C.API_LOLLIPOP)
			{
				boolean visible = mVisible && time - mTimeVisibilitySet > 50;
				if (indeterminate)
				{
					if (mTransientState == TRANSIENT_INDETERMINATE_PROGRESS)
					{
						calculateLollipopTransientIndeterminateProgress();
					}
					else
					{
						calculateLollipopProgress();
					}
					if (visible) mTransientState = TRANSIENT_PROGRESS_INDETERMINATE;
				}
				else
				{
					if (mTransientState == TRANSIENT_PROGRESS_INDETERMINATE)
					{
						calculateLollipopTransientProgressIndeterminate();
					}
					else
					{
						calculateLollipopIndeterminate(time);
					}
					if (visible) mTransientState = TRANSIENT_INDETERMINATE_PROGRESS;
					mTimeProgressChange = time;
				}
				mTransientData[0] = mCircularData[0];
				mTransientData[1] = mCircularData[1];
			}
			if (!indeterminate)
			{
				mTransientProgress = 0f;
				mProgress = 0f;
			}
			mTimeTransientStart = time;
			invalidate();
			mIndeterminate = indeterminate;
		}
	}

	private float calculateTransientProgress()
	{
		long time = System.currentTimeMillis() - mTimeProgressChange;
		float end = mProgress;
		if (time > PROGRESS_TRANSIENT_TIME) return end;
		float start = mTransientProgress;
		return start + (end - start) * time / PROGRESS_TRANSIENT_TIME;
	}

	public void setProgress(int progress, int max, boolean ignoreTransient)
	{
		float value = (float) progress / max;
		mTransientProgress = ignoreTransient ? value : calculateTransientProgress();
		mTimeProgressChange = System.currentTimeMillis();
		if (value < 0f) value = 0f; if (value > 1f) value = 1f;
		mProgress = value;
		invalidate();
	}

	private void drawArc(Canvas canvas, Paint paint, float start, float length)
	{
		if (length < 0.001f) length = 0.001f;
		Path path = mPath;
		path.reset();
		if (length >= 1f)
		{
			path.arcTo(mRectF, 0f, 180f, false);
			path.arcTo(mRectF, 180f, 180f, false);
		}
		else path.arcTo(mRectF, start * 360f - 90f, length * 360f, false);
		canvas.drawPath(path, paint);
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		int width = getWidth(), height = getHeight();
		long time = System.currentTimeMillis();
		int size = Math.min(width, height);
		boolean invalidate = false;

		boolean transientVisibility = time - mTimeVisibilitySet < VISIBILITY_TRANSIENT_TIME;
		float visibilityValue = transientVisibility ? (float) (time - mTimeVisibilitySet) /
				VISIBILITY_TRANSIENT_TIME : 1f;
		visibilityValue = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation(visibilityValue);
		int alpha = (int) (mVisible ? 0xff * visibilityValue : 0xff * (1f - visibilityValue));
		if (transientVisibility) invalidate = true;

		if (C.API_LOLLIPOP)
		{
			float arcStart;
			float arcLength;
			if (mTransientState != TRANSIENT_NONE)
			{
				boolean finished = true;
				if (mTransientState == TRANSIENT_INDETERMINATE_PROGRESS)
				{
					finished = calculateLollipopTransientIndeterminateProgress();
				}
				else if (mTransientState == TRANSIENT_PROGRESS_INDETERMINATE)
				{
					finished = calculateLollipopTransientProgressIndeterminate();
				}
				arcStart = mCircularData[0];
				arcLength = mCircularData[1];
				if (finished)
				{
					mTransientState = TRANSIENT_NONE;
					mTransientProgress = 0f;
					mTimeProgressChange = time;
				}
				invalidate = true;
			}
			else if (mIndeterminate)
			{
				calculateLollipopIndeterminate(time);
				arcStart = mCircularData[0];
				arcLength = mCircularData[1];
				invalidate = true;
			}
			else
			{
				invalidate |= calculateLollipopProgress();
				arcStart = mCircularData[0];
				arcLength = mCircularData[1];
			}
			boolean useAlpha = true;
			if (mVisible)
			{
				arcStart -= 0.25f * (1f - visibilityValue);
				arcLength = arcLength * alpha / 0xff;
				useAlpha = false;
			}
			else if (mIndeterminate || mProgress < 1f || mTransientProgress < 0.75f)
			{
				// Note, that visibilityValue always changes from 0 to 1, instead of alpha
				float newArcLength = arcLength * (1f - visibilityValue);
				arcStart += arcLength - newArcLength;
				arcLength = newArcLength;
				if (!mIndeterminate) arcStart += 0.25f * visibilityValue;
				useAlpha = false;
			}
			if (alpha > 0x00)
			{
				canvas.save();
				canvas.translate(width / 2f, height / 2f);
				int radius = (int) (size * 38f / 48f / 2f + 0.5f);
				mRectF.set(-radius, -radius, radius, radius);
				Paint paint = mPaint;
				paint.setStrokeWidth(size / 48f * 4f);
				paint.setColor(Color.argb(useAlpha ? alpha : 0xff, 0xff, 0xff, 0xff));
				drawArc(canvas, paint, arcStart, arcLength);
				canvas.restore();
			}
		}
		else
		{
			int interval = 200;
			float transientValue = time - mTimeTransientStart >= interval ? 1f
					: getValue(time, mTimeTransientStart, interval);
			if (transientValue < 1f) invalidate = true;
			int increasingAlpha = (int) (transientValue * alpha);
			int decreasingAlpha = (int) ((1f - transientValue) * alpha);
			int indeterminateAlpha;
			int progressAlpha;
			if (mIndeterminate)
			{
				indeterminateAlpha = increasingAlpha;
				progressAlpha = decreasingAlpha;
			}
			else
			{
				indeterminateAlpha = decreasingAlpha;
				progressAlpha = increasingAlpha;
			}
			if (indeterminateAlpha > 0x00)
			{
				int dWidth = mIndeterminateDrawable.getIntrinsicWidth();
				int dHeight = mIndeterminateDrawable.getIntrinsicHeight();
				int left = (width - dWidth) / 2;
				int top = (height - dHeight) / 2;
				mIndeterminateDrawable.setAlpha(indeterminateAlpha);
				mIndeterminateDrawable.setBounds(left, top, left + dWidth, top + dHeight);
				mIndeterminateDrawable.setLevel((int) (getValue(time, mStartTime, mIndeterminateDuration) * 10000));
				mIndeterminateDrawable.draw(canvas);
				invalidate = true;
			}
			if (progressAlpha > 0x00)
			{
				canvas.save();
				canvas.translate(width / 2f, height / 2f);
				int radius = (int) (size / 2f * 0.75f + 0.5f);
				mRectF.set(-radius, -radius, radius, radius);
				Paint paint = mPaint;
				paint.setStrokeWidth(size * 0.065f);
				paint.setColor(Color.argb(0x80 * progressAlpha / 0xff, 0x80, 0x80, 0x80));
				drawArc(canvas, paint, 0f, 1f);
				float progress = calculateTransientProgress();
				if (progress > 0f)
				{
					paint.setColor(Color.argb(0x80 * progressAlpha / 0xff, 0xff, 0xff, 0xff));
					drawArc(canvas, paint, 0f, progress);
				}
				canvas.restore();
				if (progress != mProgress) invalidate = true;
			}
			if (alpha == 0x00 && mVisible) invalidate = true;
		}

		if (invalidate && (alpha > 0x00 || mVisible)) invalidate();
	}
}