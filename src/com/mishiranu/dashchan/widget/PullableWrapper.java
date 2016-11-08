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

import java.lang.ref.WeakReference;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class PullableWrapper implements AbsListView.OnScrollListener {
	private final Wrapped mListView;
	private final PullView mTopView, mBottomView;

	private final float mPullDeltaGain;

	public enum Side {NONE, BOTH, TOP, BOTTOM}

	public PullableWrapper(Wrapped listView) {
		mListView = listView;
		Context context = listView.getContext();
		mTopView = C.API_LOLLIPOP ? new LollipopView(listView, true) : new JellyBeanView(listView, true);
		mBottomView = C.API_LOLLIPOP ? new LollipopView(listView, false) : new JellyBeanView(listView, false);
		mPullDeltaGain = ResourceUtils.isTablet(context.getResources().getConfiguration()) ? 6f : 4f;
	}

	public void handleAttrs(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		TypedArray typedArray = mListView.getContext().obtainStyledAttributes(attrs,
				new int[] {android.R.attr.color}, defStyleAttr, defStyleRes);
		int color = typedArray.getColor(0, 0);
		typedArray.recycle();
		setColor(color);
	}

	public void setColor(int color) {
		mTopView.setColor(color);
		mBottomView.setColor(color);
	}

	public interface PullCallback {
		public void onListPulled(PullableWrapper wrapper, Side side);
	}

	public interface PullStateListener {
		public void onPullStateChanged(PullableWrapper wrapper, boolean busy);
	}

	private PullCallback mPullCallback;
	private PullStateListener mPullStateListener;

	public void setOnPullListener(PullCallback callback) {
		mPullCallback = callback;
	}

	public void setPullStateListener(PullStateListener l) {
		mPullStateListener = l;
	}

	private Side mPullSides = Side.NONE;
	private Side mBusySide = Side.NONE;

	public void setPullSides(Side sides) {
		if (sides == null) {
			sides = Side.NONE;
		}
		mPullSides = sides;
	}

	public void startBusyState(Side side) {
		startBusyState(side, false);
	}

	private PullView getSidePullView(Side side) {
		return side == Side.TOP ? mTopView : side == Side.BOTTOM ? mBottomView : null;
	}

	private boolean startBusyState(Side side, boolean useCallback) {
		if (side == null || side == Side.NONE) {
			return false;
		}
		if (mBusySide != Side.NONE || side != mPullSides && mPullSides != Side.BOTH) {
			if (side == Side.BOTH && (mBusySide == Side.TOP || mBusySide == Side.BOTTOM)) {
				PullView pullView = getSidePullView(mBusySide);
				pullView.setState(PullView.State.IDLE, mListView.getEdgeEffectShift(side == Side.TOP));
				mBusySide = Side.BOTH;
			}
			return false;
		}
		mBusySide = side;
		PullView pullView = getSidePullView(side);
		if (pullView != null) {
			pullView.setState(PullView.State.LOADING, mListView.getEdgeEffectShift(side == Side.TOP));
		}
		if (useCallback) {
			mPullCallback.onListPulled(this, side);
		}
		notifyPullStateChanged(true);
		return true;
	}

	public void cancelBusyState() {
		if (mBusySide != Side.NONE) {
			mBusySide = Side.NONE;
			mTopView.setState(PullView.State.IDLE, mListView.getEdgeEffectShift(true));
			mBottomView.setState(PullView.State.IDLE, mListView.getEdgeEffectShift(false));
			notifyPullStateChanged(false);
			mUpdateStartY = true;
		}
	}

	private void notifyPullStateChanged(boolean busy) {
		if (mPullStateListener != null) {
			mPullStateListener.onPullStateChanged(this, busy);
		}
	}

	private boolean mUpdateStartY = true;
	private float mStartY;

	private int deltaToPullStrain(float delta) {
		return (int) (mPullDeltaGain * delta / mListView.getHeight() * PullView.MAX_STRAIN);
	}

	private int pullStrainToDelta(int pullStrain) {
		return (int) (pullStrain * mListView.getHeight() / (mPullDeltaGain * PullView.MAX_STRAIN));
	}

	// Used to calculate list transition animation.
	private long mTopJumpStartTime;
	private long mBottomJumpStartTime;

	private static final int BUSY_JUMP_TIME = 200;

	public void onTouchEvent(MotionEvent ev) {
		int action = ev.getAction();
		if (action == MotionEvent.ACTION_DOWN || !mScrolledToTop && !mScrolledToBottom) {
			mStartY = ev.getY();
		} else if (mUpdateStartY) {
			int hsize = ev.getHistorySize();
			mStartY = hsize > 0 ? ev.getHistoricalY(hsize - 1) : ev.getY();
		}
		mUpdateStartY = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
		if (mBusySide == Side.NONE) {
			if (action != MotionEvent.ACTION_DOWN) {
				float dy = ev.getY() - mStartY;
				boolean resetTop = true, resetBottom = true;
				EdgeEffectHandler edgeEffectHandler = mListView.getEdgeEffectHandler();
				if (edgeEffectHandler != null) {
					edgeEffectHandler.setPullable(true, true);
					edgeEffectHandler.setPullable(false, true);
				}
				if (action == MotionEvent.ACTION_MOVE) {
					// Call getIdlePullStrain to get previous transient value
					if (dy > 0 && mScrolledToTop && (mPullSides == Side.BOTH || mPullSides == Side.TOP)) {
						resetTop = false;
						int pullStrain = mTopView.getAndResetIdlePullStrain();
						if (pullStrain > 0) {
							mStartY -= pullStrainToDelta(pullStrain);
							dy = ev.getY() - mStartY;
						}
						int padding = mListView.getEdgeEffectShift(true);
						mTopView.setState(PullView.State.PULL, padding);
						mTopView.setPullStrain(deltaToPullStrain(dy), padding);
						if (edgeEffectHandler != null) {
							edgeEffectHandler.finish(true);
							edgeEffectHandler.setPullable(true, false);
						}
					} else if (dy < 0 && mScrolledToBottom && (mPullSides == Side.BOTH || mPullSides == Side.BOTTOM)) {
						resetBottom = false;
						int pullStrain = mBottomView.getAndResetIdlePullStrain();
						if (pullStrain > 0) {
							mStartY += pullStrainToDelta(pullStrain);
							dy = ev.getY() - mStartY;
						}
						int padding = mListView.getEdgeEffectShift(false);
						mBottomView.setState(PullView.State.PULL, padding);
						mBottomView.setPullStrain(-deltaToPullStrain(dy), padding);
						if (edgeEffectHandler != null) {
							edgeEffectHandler.finish(false);
							edgeEffectHandler.setPullable(false, false);
						}
					}
				}
				int topPullStrain = mTopView.getPullStrain();
				int bottomPullStrain = mBottomView.getPullStrain();
				if (resetTop && resetBottom && (topPullStrain > 0 || bottomPullStrain > 0)) {
					if (topPullStrain > bottomPullStrain) {
						mTopJumpStartTime = mTopView.calculateJumpStartTime();
					} else {
						mBottomJumpStartTime = mBottomView.calculateJumpStartTime();
					}
				}
				if (action == MotionEvent.ACTION_UP) {
					if (topPullStrain >= PullView.MAX_STRAIN) {
						mTopJumpStartTime = System.currentTimeMillis();
						boolean success = startBusyState(Side.TOP, true);
						resetTop &= !success;
					}
					if (bottomPullStrain >= PullView.MAX_STRAIN) {
						mBottomJumpStartTime = System.currentTimeMillis();
						boolean success = startBusyState(Side.BOTTOM, true);
						resetBottom &= !success;
					}
				}
				if (resetTop) {
					mTopView.setState(PullView.State.IDLE, mListView.getEdgeEffectShift(true));
				}
				if (resetBottom) {
					mBottomView.setState(PullView.State.IDLE, mListView.getEdgeEffectShift(false));
				}
			}
		}
	}

	// States of list, can be defined only in scroll listener.
	private boolean mScrolledToTop = true, mScrolledToBottom = true;

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		try {
			mScrolledToTop = totalItemCount == 0 || firstVisibleItem == 0 &&
					view.getChildAt(0).getTop() >= mListView.getEdgeEffectShift(true);
		} catch (Exception e) {
			mScrolledToTop = false;
		}
		try {
			mScrolledToBottom = totalItemCount == 0 || firstVisibleItem + visibleItemCount == totalItemCount &&
					view.getChildAt(visibleItemCount - 1).getBottom() <= view.getHeight() -
					mListView.getEdgeEffectShift(false);
		} catch (Exception e) {
			mScrolledToBottom = false;
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {}

	private int mLastShiftValue = 0;
	private int mBeforeShiftValue = 0;
	private boolean mBeforeRestoreCanvas = false;

	public void drawBefore(Canvas canvas) {
		int top = mTopView.calculateJumpValue(mTopJumpStartTime);
		int bottom = mBottomView.calculateJumpValue(mBottomJumpStartTime);
		int shift = top > bottom ? top : -bottom;
		if (shift != 0) {
			canvas.save();
			float height = mListView.getHeight();
			float dy = (float) (Math.pow(Math.abs(pullStrainToDelta(shift)) / height, 2.5f))
					* height * Math.signum(shift);
			canvas.translate(0, dy);
			mBeforeRestoreCanvas = true;
		}
		mBeforeShiftValue = shift;
	}

	public void drawAfter(Canvas canvas) {
		if (mBeforeRestoreCanvas) {
			mBeforeRestoreCanvas = false;
			canvas.restore();
		}
		int shift = mBeforeShiftValue;
		float density = ResourceUtils.obtainDensity(mListView.getResources());
		mTopView.draw(canvas, mListView.getEdgeEffectShift(true), density);
		mBottomView.draw(canvas, mListView.getEdgeEffectShift(false), density);
		if (mLastShiftValue != shift) {
			mLastShiftValue = shift;
			mListView.invalidate();
		}
	}

	private interface PullView {
		public enum State {IDLE, PULL, LOADING}

		public static final int MAX_STRAIN = 1000;

		public void setColor(int color);
		public void setState(State state, int padding);
		public void setPullStrain(int pullStrain, int padding);
		public int getPullStrain();
		public int getAndResetIdlePullStrain();
		public void draw(Canvas canvas, int padding, float density);
		public long calculateJumpStartTime();
		public int calculateJumpValue(long jumpStartTime);
	}

	private static class JellyBeanView implements PullView {
		private static final int IDLE_FOLD_TIME = 500;
		private static final int LOADING_HALF_CYCLE_TIME = 600;

		private final WeakReference<Wrapped> mWrapped;
		private final Paint mPaint = new Paint();
		private final int mHeight;
		private final boolean mTop;

		private State mPreviousState = State.IDLE;
		private State mState = State.IDLE;

		private int mStartIdlePullStrain = 0;
		private long mTimeIdleStart = 0L;
		private long mTimeLoadingStart = 0L;
		private long mTimeLoadingToIdleStart = 0L;

		private int mPullStrain = 0;

		private int mColor;

		public JellyBeanView(Wrapped wrapped, boolean top) {
			mWrapped = new WeakReference<>(wrapped);
			mHeight = (int) (3f * ResourceUtils.obtainDensity(wrapped.getContext()) + 0.5f);
			mTop = top;
		}

		@Override
		public void setColor(int color) {
			mColor = color;
		}

		private void invalidate(int padding) {
			Wrapped wrapped = mWrapped.get();
			if (wrapped != null) {
				int offset = mTop ? padding : wrapped.getHeight() - mHeight - padding;
				invalidate(0, offset, wrapped.getWidth(), offset + mHeight);
			}
		}

		private void invalidate(int l, int t, int r, int b) {
			Wrapped wrapped = mWrapped.get();
			if (wrapped != null) {
				wrapped.invalidate(l, t, r, b);
			}
		}

		@Override
		public void setState(State state, int padding) {
			if (mState != state) {
				State prePreviousState = mPreviousState;
				mPreviousState = mState;
				mState = state;
				long time = System.currentTimeMillis();
				switch (mState) {
					case IDLE: {
						mTimeIdleStart = time;
						if (mPreviousState == State.LOADING) {
							mTimeLoadingToIdleStart = time;
						}
						mStartIdlePullStrain = mPreviousState == State.LOADING ? 0 : mPullStrain;
						mPullStrain = 0;
						break;
					}
					case PULL: {
						break;
					}
					case LOADING: {
						// May continue use old animation until it over
						boolean loadingToLoading = prePreviousState == State.LOADING &&
								mPreviousState == State.IDLE && time - mTimeIdleStart < LOADING_HALF_CYCLE_TIME;
						if (!loadingToLoading) {
							mTimeLoadingStart = mPreviousState == State.IDLE ? time + LOADING_HALF_CYCLE_TIME : time;
						}
						mTimeLoadingToIdleStart = 0L;
						break;
					}
				}
				invalidate(padding);
			}
		}

		@Override
		public void setPullStrain(int pullStrain, int padding) {
			mPullStrain = pullStrain;
			if (mPullStrain > MAX_STRAIN) {
				mPullStrain = MAX_STRAIN;
			} else if (mPullStrain < 0) {
				mPullStrain = 0;
			}
			if (mState == State.PULL) {
				invalidate(padding);
			}
		}

		@Override
		public int getPullStrain() {
			return mPullStrain;
		}

		@Override
		public int getAndResetIdlePullStrain() {
			if (mStartIdlePullStrain == 0) {
				return 0;
			}
			try {
				return (int) (MAX_STRAIN * getIdleTransientPullStrainValue(System.currentTimeMillis()));
			} finally {
				mStartIdlePullStrain = 0;
			}
		}

		private float getIdleTransientPullStrainValue(long time) {
			int foldTime = IDLE_FOLD_TIME * mStartIdlePullStrain / MAX_STRAIN;
			if (foldTime <= 0) {
				return 0f;
			}
			float value = Math.min((float) (time - mTimeIdleStart) / foldTime, 1f);
			return (1f - value) * mStartIdlePullStrain / MAX_STRAIN;
		}

		@Override
		public void draw(Canvas canvas, int padding, float density) {
			Wrapped wrapped = mWrapped.get();
			if (wrapped == null) {
				return;
			}
			Paint paint = mPaint;
			long time = System.currentTimeMillis();
			int width = wrapped.getWidth();
			int height = mHeight;
			int offset = mTop ? padding : wrapped.getHeight() - height - padding;
			State state = mState;
			State previousState = mPreviousState;
			int primaryColor = mColor;
			int secondaryColor = 0x80 << 24 | 0x00ffffff & mColor;
			boolean needInvalidate = false;

			if (state == State.PULL) {
				int size = (int) (width / 2f * Math.pow((float) mPullStrain / MAX_STRAIN, 2f));
				paint.setColor(primaryColor);
				canvas.drawRect(width / 2 - size, offset, width / 2 + size, offset + height, paint);
			}

			if (state == State.IDLE && previousState != State.LOADING) {
				float value = getIdleTransientPullStrainValue(time);
				int size = (int) (width / 2f * Math.pow(value, 4f));
				paint.setColor(primaryColor);
				canvas.drawRect(width / 2 - size, offset, width / 2 + size, offset + height, paint);
				if (value != 0) {
					needInvalidate = true;
				}
			}

			if (state == State.LOADING || mTimeLoadingToIdleStart > 0L) {
				Interpolator interpolator = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR;
				final int cycle = 2 * LOADING_HALF_CYCLE_TIME;
				final int half = LOADING_HALF_CYCLE_TIME;
				long elapsed = time - mTimeLoadingStart;
				boolean startTransient = elapsed < 0;
				if (startTransient) {
					elapsed += cycle;
				}
				int phase = (int) (elapsed % cycle);
				int partWidth;
				if (state != State.LOADING) {
					long elapsedIdle = time - mTimeLoadingToIdleStart;
					float value = Math.min((float) elapsedIdle / half, 1f);
					partWidth = (int) (width / 2f * (1f - interpolator.getInterpolation(value)));
					if (partWidth <= 0) {
						partWidth = 0;
						mTimeLoadingToIdleStart = 0L;
					}
				} else {
					partWidth = (int) (width / 2f);
				}
				if (!startTransient) {
					paint.setColor(secondaryColor);
					canvas.drawRect(0, offset, partWidth, offset + height, paint);
					canvas.drawRect(width - partWidth, offset, width, offset + height, paint);
				}
				paint.setColor(primaryColor);
				if (phase <= half) {
					float value = (float) phase / half;
					int size = (int) (width / 2f * interpolator.getInterpolation(value));
					int left = Math.min(width / 2 - size, partWidth);
					int right = Math.max(width / 2 + size, width - partWidth);
					canvas.drawRect(0, offset, left, offset + height, paint);
					canvas.drawRect(right, offset, width, offset + height, paint);
				} else {
					float value = (float) (phase - half) / half;
					int size = (int) (width / 2f * interpolator.getInterpolation(value));
					int left = width / 2 - size;
					int right = width / 2 + size;
					if (left < partWidth) {
						canvas.drawRect(left, offset, partWidth, offset + height, paint);
						canvas.drawRect(width - partWidth, offset, right, offset + height, paint);
					}
				}
				needInvalidate = true;
			}

			if (needInvalidate) {
				invalidate(0, offset, width, offset + height);
			}
		}

		@Override
		public long calculateJumpStartTime() {
			return System.currentTimeMillis() - BUSY_JUMP_TIME * (MAX_STRAIN - mPullStrain) / MAX_STRAIN;
		}

		@Override
		public int calculateJumpValue(long jumpStartTime) {
			int value = 0;
			switch (mState) {
				case PULL: {
					value = mPullStrain;
					break;
				}
				case IDLE:
				case LOADING: {
					if (jumpStartTime > 0) {
						value = (int) (MAX_STRAIN * (System.currentTimeMillis() - jumpStartTime) / BUSY_JUMP_TIME);
						value = value < MAX_STRAIN ? MAX_STRAIN - value : 0;
					}
					break;
				}
			}
			return value;
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static class LollipopView implements PullView {
		private static final int IDLE_FOLD_TIME = 100;
		private static final int LOADING_FOLD_TIME = 150;
		private static final int FULL_CYCLE_TIME = 6665;

		private static final int CIRCLE_RADIUS = 20;
		private static final int DEFAULT_CIRCLE_TARGET = 64;
		private static final float CENTER_RADIUS = 8.75f;
		private static final float STROKE_WIDTH = 2.5f;

		private final WeakReference<Wrapped> mWrapped;
		private final Paint mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path mPath = new Path();
		private final RectF mRectF = new RectF();
		private final boolean mTop;

		private final Interpolator mStartInterpolator;
		private final Interpolator mEndInterpolator;

		private State mPreviousState = State.IDLE;
		private State mState = State.IDLE;

		private int mStartFoldingPullStrain = 0;
		private long mTimeStateStart = 0L;
		private long mTimeSpinStart = 0L;
		private float mSpinOffset = 0f;

		private int mPullStrain = 0;

		public LollipopView(Wrapped wrapped, boolean top) {
			mWrapped = new WeakReference<>(wrapped);
			mTop = top;
			Path startPath = new Path();
			startPath.lineTo(0.5f, 0f);
			startPath.cubicTo(0.7f, 0f, 0.6f, 1f, 1f, 1f);
			mStartInterpolator = new PathInterpolator(startPath);
			Path endPath = new Path();
			endPath.cubicTo(0.2f, 0f, 0.1f, 1f, 0.5f, 1f);
			endPath.lineTo(1f, 1f);
			mEndInterpolator = new PathInterpolator(endPath);
			mRingPaint.setStyle(Paint.Style.STROKE);
			mRingPaint.setStrokeCap(Paint.Cap.SQUARE);
			mRingPaint.setStrokeJoin(Paint.Join.MITER);
		}

		@Override
		public void setColor(int color) {
			mCirclePaint.setColor(color);
		}

		private void invalidate(int padding) {
			Wrapped wrapped = mWrapped.get();
			if (wrapped != null) {
				float density = ResourceUtils.obtainDensity(wrapped.getContext());
				float commonShift = DEFAULT_CIRCLE_TARGET * density;
				float radius = CIRCLE_RADIUS * density;
				invalidate(wrapped, wrapped.getWidth(), radius, commonShift, padding);
			}
		}

		@SuppressWarnings("UnnecessaryLocalVariable")
		private void invalidate(Wrapped wrapped, int width, float radius, float commonShift, int padding) {
			int hw = width / 2;
			int l = hw - (int) radius - 1;
			int r = hw + (int) radius + 1;
			int t = padding - (int) (2f * radius) - 1;
			int b = padding + (int) (2f * commonShift) + 1;
			if (!mTop) {
				int height = wrapped.getHeight();
				int nt = height - t;
				int nb = height - b;
				t = nb;
				b = nt;
			}
			wrapped.invalidate(l, t, r, b);
		}

		@Override
		public void setState(State state, int padding) {
			if (mState != state) {
				State prePreviousState = mPreviousState;
				mPreviousState = mState;
				mState = state;
				long time = System.currentTimeMillis();
				switch (mState) {
					case IDLE: {
						mTimeStateStart = time;
						mStartFoldingPullStrain = mPreviousState == State.LOADING ? MAX_STRAIN : mPullStrain;
						mPullStrain = 0;
						break;
					}
					case PULL: {
						break;
					}
					case LOADING: {
						// May continue use old animation until it over
						boolean loadingToLoading = prePreviousState == State.LOADING &&
								mPreviousState == State.IDLE && time - mTimeStateStart < 50;
						if (!loadingToLoading) {
							mTimeStateStart = time;
							mStartFoldingPullStrain = mPullStrain;
							mTimeSpinStart = mPreviousState == State.IDLE ? time : time - FULL_CYCLE_TIME / 10;
							if (mPreviousState == State.IDLE) {
								mSpinOffset = 0f;
							}
						}
						break;
					}
				}
				invalidate(padding);
			}
		}

		@Override
		public void setPullStrain(int pullStrain, int padding) {
			mPullStrain = pullStrain;
			if (mPullStrain > 2 * MAX_STRAIN) {
				mPullStrain = 2 * MAX_STRAIN;
			} else if (mPullStrain < 0) {
				mPullStrain = 0;
			}
			if (mState == State.PULL) {
				invalidate(padding);
			}
		}

		@Override
		public int getPullStrain() {
			return mPullStrain > MAX_STRAIN ? MAX_STRAIN : mPullStrain;
		}

		@Override
		public int getAndResetIdlePullStrain() {
			if (mStartFoldingPullStrain == 0) {
				return 0;
			}
			try {
				return (int) (MAX_STRAIN * getIdleTransientPullStrainValue(IDLE_FOLD_TIME, System.currentTimeMillis()));
			} finally {
				mStartFoldingPullStrain = 0;
			}
		}

		private float getIdleTransientPullStrainValue(int maxFoldTime, long time) {
			int foldTime = maxFoldTime * mStartFoldingPullStrain / MAX_STRAIN;
			if (foldTime <= 0) {
				return 0f;
			}
			float value = Math.min((float) (time - mTimeStateStart) / foldTime, 1f);
			return (1f - value) * mStartFoldingPullStrain / MAX_STRAIN;
		}

		@Override
		public void draw(Canvas canvas, int padding, float density) {
			Wrapped wrapped = mWrapped.get();
			if (wrapped == null) {
				return;
			}
			Paint circlePaint = mCirclePaint;
			Paint ringPaint = mRingPaint;
			long time = System.currentTimeMillis();
			int width = wrapped.getWidth();
			int height = wrapped.getHeight();
			State state = mState;
			State previousState = mPreviousState;
			boolean needInvalidate = false;
			float commonShift = DEFAULT_CIRCLE_TARGET * density;
			float radius = CIRCLE_RADIUS * density;

			float value = 0f;
			float scale = 1f;
			boolean spin = false;

			if (state == State.PULL) {
				value = (float) mPullStrain / MAX_STRAIN;
			}

			if (state == State.IDLE) {
				if (previousState == State.LOADING) {
					value = getIdleTransientPullStrainValue(LOADING_FOLD_TIME, time);
					if (value > 0f) {
						scale = value;
						value = 1f;
						spin = true;
						needInvalidate = true;
					}
				} else {
					value = getIdleTransientPullStrainValue(IDLE_FOLD_TIME, time);
					if (value != 0f) {
						needInvalidate = true;
					}
				}
			}

			if (state == State.LOADING) {
				value = getIdleTransientPullStrainValue(IDLE_FOLD_TIME, time);
				if (value <= 1f) {
					value = 1f;
				}
				spin = true;
				if (previousState == State.IDLE) {
					scale = Math.min((float) (time - mTimeStateStart) / LOADING_FOLD_TIME, 1f);
				}
				needInvalidate = true;
			}

			if (value != 0f) {
				int shift = (int) (commonShift * value) + padding;
				if (!mTop) {
					shift = height - shift;
				}
				float centerX = width / 2f;
				float centerY = mTop ? shift - radius : shift + radius;
				boolean needRestore = false;
				if (scale != 1f && scale != 0f) {
					canvas.save();
					canvas.scale(scale, scale, centerX, centerY);
					needRestore = true;
				}
				float ringRadius = CENTER_RADIUS * density;
				ringPaint.setStrokeWidth(STROKE_WIDTH * density);
				canvas.drawCircle(centerX, centerY, radius, circlePaint);

				float arcStart;
				float arcLength;
				int ringAlpha = 0xff;
				if (spin) {
					float rotationValue = (float) ((time - mTimeSpinStart) % FULL_CYCLE_TIME) / FULL_CYCLE_TIME;
					float animationValue = rotationValue * 5f % 1f;
					float trimOffset = 0.25f * animationValue;
					float trimStart = 0.75f * mStartInterpolator.getInterpolation(animationValue) + trimOffset;
					float trimEnd = 0.75f * mEndInterpolator.getInterpolation(animationValue) + trimOffset;
					float rotation = 2f * rotationValue;
					arcStart = trimStart;
					arcLength = trimEnd - arcStart;
					arcStart += rotation + mSpinOffset;
				} else {
					float alphaThreshold = 0.95f;
					ringAlpha = (int) AnimationUtils.lerp(0x7f, 0xff, (Math.min(1f, Math.max(value, alphaThreshold))
							- alphaThreshold) / (1f - alphaThreshold));
					arcStart = value > 1f ? 0.25f + (value - 1f) * 0.5f : 0.25f * value;
					arcLength = 0.75f * (float) Math.pow(Math.min(value, 1f), 0.75f);
					if (value >= 1f) {
						mSpinOffset = (value - 1f) * 0.5f;
					}
				}
				ringPaint.setColor(0xffffff | (ringAlpha << 24));
				RectF size = mRectF;
				size.set(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius);
				drawArc(canvas, ringPaint, size, arcStart, arcLength);
				if (needRestore) {
					canvas.restore();
				}
			}

			if (needInvalidate) {
				invalidate(wrapped, width, radius, commonShift, padding);
			}
		}

		private void drawArc(Canvas canvas, Paint paint, RectF size, float start, float length) {
			if (length < 0.001f) {
				length = 0.001f;
			}
			Path path = mPath;
			path.reset();
			if (length >= 1f) {
				path.arcTo(size, 0f, 180f, false);
				path.arcTo(size, 180f, 180f, false);
			} else {
				path.arcTo(size, start * 360f - 90f, length * 360f, false);
			}
			canvas.drawPath(path, paint);
		}

		@Override
		public long calculateJumpStartTime() {
			return 0L;
		}

		@Override
		public int calculateJumpValue(long jumpStartTime) {
			return 0;
		}
	}

	public interface Wrapped extends EdgeEffectHandler.Shift {
		public Context getContext();
		public Resources getResources();
		public EdgeEffectHandler getEdgeEffectHandler();
		public void invalidate(int l, int t, int r, int b);
		public void invalidate();
		public int getWidth();
		public int getHeight();
	}
}