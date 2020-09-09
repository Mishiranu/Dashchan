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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class CircularProgressBar extends View {
	private static final int INDETERMINATE_LOLLIPOP_TIME = 6665;
	private static final int PROGRESS_TRANSIENT_TIME = 500;
	private static final int VISIBILITY_TRANSIENT_TIME = 500;

	private enum Transient {NONE, INDETERMINATE_PROGRESS, PROGRESS_INDETERMINATE}

	private final Paint paint;
	private final Path path = new Path();
	private final RectF rectF = new RectF();

	private final Drawable indeterminateDrawable;
	private final int indeterminateDuration;

	private final Interpolator lollipopStartInterpolator;
	private final Interpolator lollipopEndInterpolator;

	private final long startTime = SystemClock.elapsedRealtime();

	private Transient transientState = Transient.NONE;
	private final float[] circularData = C.API_LOLLIPOP ? new float[2] : null;
	private final float[] transientData = C.API_LOLLIPOP ? new float[2] : null;
	private long timeTransientStart;

	private boolean indeterminate = true;
	private boolean visible = false;
	private long timeVisibilitySet;

	private float progress = 0f;
	private float transientProgress = 0;
	private long timeProgressChange;

	public CircularProgressBar(Context context) {
		this(context, null);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public CircularProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.SQUARE);
		paint.setStrokeJoin(Paint.Join.MITER);
		if (C.API_LOLLIPOP) {
			Path startPath = new Path();
			startPath.lineTo(0.5f, 0f);
			startPath.cubicTo(0.7f, 0f, 0.6f, 1f, 1f, 1f);
			lollipopStartInterpolator = new PathInterpolator(startPath);
			Path endPath = new Path();
			endPath.cubicTo(0.2f, 0f, 0.1f, 1f, 0.5f, 1f);
			endPath.lineTo(1f, 1f);
			lollipopEndInterpolator = new PathInterpolator(endPath);
			indeterminateDrawable = null;
			indeterminateDuration = 0;
		} else {
			lollipopStartInterpolator = null;
			lollipopEndInterpolator = null;
			TypedArray typedArray = context.obtainStyledAttributes(android.R.style.Widget_Holo_ProgressBar_Large,
					new int[] {android.R.attr.indeterminateDrawable, android.R.attr.indeterminateDuration});
			indeterminateDrawable = typedArray.getDrawable(0);
			indeterminateDuration = typedArray.getInteger(1, 3500);
			typedArray.recycle();
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		float density = ResourceUtils.obtainDensity(this);
		int size = (int) (72f * density + 0.5f);
		int width = size + getPaddingLeft() + getPaddingRight();
		int height = size + getPaddingTop() + getPaddingBottom();
		setMeasuredDimension(resolveSizeAndState(width, widthMeasureSpec, 0),
				resolveSizeAndState(height, heightMeasureSpec, 0));
	}

	private float getValue(long currentTime, long fromTime, float max) {
		return ((currentTime - fromTime) % max) / max;
	}

	private boolean queuedVisible;

	private final Runnable setVisibleRunnable = () -> {
		visible = queuedVisible;
		timeVisibilitySet = SystemClock.elapsedRealtime();
		invalidate();
	};

	public void setVisible(boolean visible, boolean forced) {
		removeCallbacks(setVisibleRunnable);
		if (this.visible != visible) {
			queuedVisible = visible;
			long delta = SystemClock.elapsedRealtime() - timeVisibilitySet;
			if (delta < 10) {
				this.visible = visible;
				timeVisibilitySet = 0L;
				invalidate();
			} else if (!visible && !forced && delta < VISIBILITY_TRANSIENT_TIME) {
				postDelayed(setVisibleRunnable, VISIBILITY_TRANSIENT_TIME - delta);
			} else {
				setVisibleRunnable.run();
			}
		}
	}

	public void cancelVisibilityTransient() {
		removeCallbacks(setVisibleRunnable);
		timeVisibilitySet = 0L;
		visible = queuedVisible;
		invalidate();
	}

	private boolean calculateLollipopProgress() {
		float progress = calculateTransientProgress();
		float arcStart = (progress - 1f) / 4f;
		float arcLength = progress - arcStart;
		circularData[0] = arcStart;
		circularData[1] = arcLength;
		return progress != this.progress;
	}

	private void calculateLollipopIndeterminate(long currentTime) {
		float rotationValue = getValue(currentTime, startTime, INDETERMINATE_LOLLIPOP_TIME);
		float animationValue = rotationValue * 5f % 1f;
		float trimOffset = 0.25f * animationValue;
		float trimStart = 0.75f * lollipopStartInterpolator.getInterpolation(animationValue) + trimOffset;
		float trimEnd = 0.75f * lollipopEndInterpolator.getInterpolation(animationValue) + trimOffset;
		float rotation = 2f * rotationValue;
		float arcStart = trimStart;
		float arcLength = trimEnd - arcStart;
		arcStart += rotation;
		circularData[0] = arcStart % 1f;
		circularData[1] = arcLength;
	}

	private boolean calculateLollipopTransient(float arcStart, float arcLength,
			float desiredStart, float desiredLength, long interval) {
		boolean finished = false;
		long timeDelta = SystemClock.elapsedRealtime() - timeTransientStart;
		if (timeDelta >= interval) {
			timeDelta = interval;
			finished = true;
		}
		float value = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation((float) timeDelta / interval);
		arcStart = arcStart + (desiredStart - arcStart) * value;
		arcLength = arcLength + (desiredLength - arcLength) * value;
		circularData[0] = arcStart % 1f;
		circularData[1] = arcLength;
		return finished;
	}

	private boolean calculateLollipopTransientIndeterminateProgress() {
		float arcStart = transientData[0];
		float arcLength = transientData[1];
		float desiredStart = 0.75f;
		float desiredLength = 0.25f;
		if (arcStart >= desiredStart - 0.15f || arcLength >= desiredLength) {
			arcStart -= 1f;
		}
		int interval = (int) (800f * (desiredStart - arcStart));
		return calculateLollipopTransient(arcStart, arcLength, desiredStart, desiredLength, interval);
	}

	private boolean calculateLollipopTransientProgressIndeterminate() {
		float arcStart = transientData[0];
		float arcLength = transientData[1];
		calculateLollipopIndeterminate(timeTransientStart + 1000L);
		float desiredStart = circularData[0];
		float desiredLength = circularData[1];
		if (arcStart >= desiredStart || arcLength >= desiredLength) {
			arcStart -= 1f;
		}
		return calculateLollipopTransient(arcStart, arcLength, desiredStart, desiredLength, 1000L);
	}

	public void setIndeterminate(boolean indeterminate) {
		if (this.indeterminate != indeterminate) {
			long time = SystemClock.elapsedRealtime();
			if (C.API_LOLLIPOP) {
				boolean visible = this.visible && time - timeVisibilitySet > 50;
				if (indeterminate) {
					if (transientState == Transient.INDETERMINATE_PROGRESS) {
						calculateLollipopTransientIndeterminateProgress();
					} else {
						calculateLollipopProgress();
					}
					if (visible) {
						transientState = Transient.PROGRESS_INDETERMINATE;
					}
				} else {
					if (transientState == Transient.PROGRESS_INDETERMINATE) {
						calculateLollipopTransientProgressIndeterminate();
					} else {
						calculateLollipopIndeterminate(time);
					}
					if (visible) {
						transientState = Transient.INDETERMINATE_PROGRESS;
					}
					timeProgressChange = time;
				}
				transientData[0] = circularData[0];
				transientData[1] = circularData[1];
			}
			if (!indeterminate) {
				transientProgress = 0f;
				progress = 0f;
			}
			timeTransientStart = time;
			invalidate();
			this.indeterminate = indeterminate;
		}
	}

	private float calculateTransientProgress() {
		long time = SystemClock.elapsedRealtime() - timeProgressChange;
		float end = progress;
		if (time > PROGRESS_TRANSIENT_TIME) {
			return end;
		}
		float start = transientProgress;
		return start + (end - start) * time / PROGRESS_TRANSIENT_TIME;
	}

	public void setProgress(int progress, int max, boolean ignoreTransient) {
		float value = (float) progress / max;
		transientProgress = ignoreTransient ? value : calculateTransientProgress();
		timeProgressChange = SystemClock.elapsedRealtime();
		if (value < 0f) {
			value = 0f;
		} else if (value > 1f) {
			value = 1f;
		}
		this.progress = value;
		invalidate();
	}

	private void drawArc(Canvas canvas, Paint paint, float start, float length) {
		if (length < 0.001f) {
			length = 0.001f;
		}
		Path path = this.path;
		path.reset();
		if (length >= 1f) {
			path.arcTo(rectF, 0f, 180f, false);
			path.arcTo(rectF, 180f, 180f, false);
		} else {
			path.arcTo(rectF, start * 360f - 90f, length * 360f, false);
		}
		canvas.drawPath(path, paint);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		int width = getWidth(), height = getHeight();
		long time = SystemClock.elapsedRealtime();
		int size = Math.min(width, height);
		boolean invalidate = false;

		boolean transientVisibility = time - timeVisibilitySet < VISIBILITY_TRANSIENT_TIME;
		float visibilityValue = transientVisibility ? (float) (time - timeVisibilitySet) /
				VISIBILITY_TRANSIENT_TIME : 1f;
		visibilityValue = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation(visibilityValue);
		int alpha = (int) (visible ? 0xff * visibilityValue : 0xff * (1f - visibilityValue));
		if (transientVisibility) {
			invalidate = true;
		}

		if (C.API_LOLLIPOP) {
			float arcStart;
			float arcLength;
			if (transientState != Transient.NONE) {
				boolean finished = true;
				if (transientState == Transient.INDETERMINATE_PROGRESS) {
					finished = calculateLollipopTransientIndeterminateProgress();
				} else if (transientState == Transient.PROGRESS_INDETERMINATE) {
					finished = calculateLollipopTransientProgressIndeterminate();
				}
				arcStart = circularData[0];
				arcLength = circularData[1];
				if (finished) {
					transientState = Transient.NONE;
					transientProgress = 0f;
					timeProgressChange = time;
				}
				invalidate = true;
			} else if (indeterminate) {
				calculateLollipopIndeterminate(time);
				arcStart = circularData[0];
				arcLength = circularData[1];
				invalidate = true;
			} else {
				invalidate |= calculateLollipopProgress();
				arcStart = circularData[0];
				arcLength = circularData[1];
			}
			boolean useAlpha = true;
			if (visible) {
				arcStart -= 0.25f * (1f - visibilityValue);
				arcLength = arcLength * alpha / 0xff;
				useAlpha = false;
			} else if (indeterminate || progress < 1f || transientProgress < 0.75f) {
				// Note, that visibilityValue always changes from 0 to 1, instead of alpha
				float newArcLength = arcLength * (1f - visibilityValue);
				arcStart += arcLength - newArcLength;
				arcLength = newArcLength;
				if (!indeterminate) {
					arcStart += 0.25f * visibilityValue;
				}
				useAlpha = false;
			}
			if (alpha > 0x00) {
				canvas.save();
				canvas.translate(width / 2f, height / 2f);
				int radius = (int) (size * 38f / 48f / 2f + 0.5f);
				rectF.set(-radius, -radius, radius, radius);
				Paint paint = this.paint;
				paint.setStrokeWidth(size / 48f * 4f);
				paint.setColor(Color.argb(useAlpha ? alpha : 0xff, 0xff, 0xff, 0xff));
				drawArc(canvas, paint, arcStart, arcLength);
				canvas.restore();
			}
		} else {
			int interval = 200;
			float transientValue = time - timeTransientStart >= interval ? 1f
					: getValue(time, timeTransientStart, interval);
			if (transientValue < 1f) {
				invalidate = true;
			}
			int increasingAlpha = (int) (transientValue * alpha);
			int decreasingAlpha = (int) ((1f - transientValue) * alpha);
			int indeterminateAlpha;
			int progressAlpha;
			if (indeterminate) {
				indeterminateAlpha = increasingAlpha;
				progressAlpha = decreasingAlpha;
			} else {
				indeterminateAlpha = decreasingAlpha;
				progressAlpha = increasingAlpha;
			}
			if (indeterminateAlpha > 0x00) {
				int dWidth = indeterminateDrawable.getIntrinsicWidth();
				int dHeight = indeterminateDrawable.getIntrinsicHeight();
				int left = (width - dWidth) / 2;
				int top = (height - dHeight) / 2;
				indeterminateDrawable.setAlpha(indeterminateAlpha);
				indeterminateDrawable.setBounds(left, top, left + dWidth, top + dHeight);
				indeterminateDrawable.setLevel((int) (getValue(time, startTime, indeterminateDuration) * 10000));
				indeterminateDrawable.draw(canvas);
				invalidate = true;
			}
			if (progressAlpha > 0x00) {
				canvas.save();
				canvas.translate(width / 2f, height / 2f);
				int radius = (int) (size / 2f * 0.75f + 0.5f);
				rectF.set(-radius, -radius, radius, radius);
				Paint paint = this.paint;
				paint.setStrokeWidth(size * 0.065f);
				paint.setColor(Color.argb(0x80 * progressAlpha / 0xff, 0x80, 0x80, 0x80));
				drawArc(canvas, paint, 0f, 1f);
				float progress = calculateTransientProgress();
				if (progress > 0f) {
					paint.setColor(Color.argb(0x80 * progressAlpha / 0xff, 0xff, 0xff, 0xff));
					drawArc(canvas, paint, 0f, progress);
				}
				canvas.restore();
				if (progress != this.progress) {
					invalidate = true;
				}
			}
			if (alpha == 0x00 && visible) {
				invalidate = true;
			}
		}

		if (invalidate && (alpha > 0x00 || visible)) {
			invalidate();
		}
	}
}
