package com.mishiranu.dashchan.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import java.lang.ref.WeakReference;

public class PullableWrapper {
	private final Wrapped listView;
	private final PullView topView;
	private final PullView bottomView;

	private final float pullDeltaGain;

	public enum Side {NONE, BOTH, TOP, BOTTOM}

	public PullableWrapper(Wrapped listView) {
		this.listView = listView;
		Context context = listView.getContext();
		topView = C.API_LOLLIPOP ? new LollipopView(listView, true) : new JellyBeanView(listView, true);
		bottomView = C.API_LOLLIPOP ? new LollipopView(listView, false) : new JellyBeanView(listView, false);
		pullDeltaGain = ResourceUtils.isTablet(context.getResources().getConfiguration()) ? 6f : 4f;
		setColor(ThemeEngine.getTheme(listView.getContext()).accent);
	}

	public void setColor(int color) {
		topView.setColor(color);
		bottomView.setColor(color);
	}

	public interface PullCallback {
		void onListPulled(PullableWrapper wrapper, Side side);
	}

	public interface PullStateListener {
		void onPullStateChanged(PullableWrapper wrapper, boolean busy);
	}

	private PullCallback pullCallback;
	private PullStateListener pullStateListener;

	public void setOnPullListener(PullCallback callback) {
		this.pullCallback = callback;
	}

	public void setPullStateListener(PullStateListener listener) {
		this.pullStateListener = listener;
	}

	private Side pullSides = Side.NONE;
	private Side busySide = Side.NONE;

	public void setPullSides(Side sides) {
		if (sides == null) {
			sides = Side.NONE;
		}
		pullSides = sides;
	}

	public void startBusyState(Side side) {
		startBusyState(side, false);
	}

	private PullView getSidePullView(Side side) {
		return side == Side.TOP ? topView : side == Side.BOTTOM ? bottomView : null;
	}

	private boolean startBusyState(Side side, boolean useCallback) {
		if (side == null || side == Side.NONE) {
			return false;
		}
		if (busySide != Side.NONE || side != pullSides && pullSides != Side.BOTH) {
			if (side == Side.BOTH && (busySide == Side.TOP || busySide == Side.BOTTOM)) {
				PullView pullView = getSidePullView(busySide);
				pullView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(side == Side.TOP
						? EdgeEffectHandler.Side.TOP : EdgeEffectHandler.Side.BOTTOM));
				busySide = Side.BOTH;
			}
			return false;
		}
		busySide = side;
		PullView pullView = getSidePullView(side);
		if (pullView != null) {
			pullView.setState(PullView.State.LOADING, listView.getEdgeEffectShift(side == Side.TOP
					? EdgeEffectHandler.Side.TOP : EdgeEffectHandler.Side.BOTTOM));
		}
		if (useCallback) {
			pullCallback.onListPulled(this, side);
		}
		notifyPullStateChanged(true);
		return true;
	}

	public void cancelBusyState() {
		if (busySide != Side.NONE) {
			busySide = Side.NONE;
			topView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(EdgeEffectHandler.Side.TOP));
			bottomView.setState(PullView.State.IDLE, listView.getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM));
			notifyPullStateChanged(false);
			updateStartY = true;
		}
	}

	private void notifyPullStateChanged(boolean busy) {
		if (pullStateListener != null) {
			pullStateListener.onPullStateChanged(this, busy);
		}
	}

	private boolean updateStartY = true;
	private float startY;

	private int deltaToPullStrain(float delta) {
		return (int) (pullDeltaGain * delta / listView.getHeight() * PullView.MAX_STRAIN);
	}

	private int pullStrainToDelta(int pullStrain) {
		return (int) (pullStrain * listView.getHeight() / (pullDeltaGain * PullView.MAX_STRAIN));
	}

	// Used to calculate list transition animation.
	private long topJumpStartTime;
	private long bottomJumpStartTime;

	private static final int BUSY_JUMP_TIME = 200;

	public boolean onTouchEventOrNull(MotionEvent ev) {
		boolean pull = false;
		int action = ev != null ? ev.getAction() : MotionEvent.ACTION_CANCEL;
		if (action == MotionEvent.ACTION_DOWN || !listView.isScrolledToTop() && !listView.isScrolledToBottom()) {
			startY = ev != null ? ev.getY() : 0;
		} else if (updateStartY) {
			int hsize = ev != null ? ev.getHistorySize() : 0;
			startY = hsize > 0 ? ev.getHistoricalY(hsize - 1) : ev.getY();
		}
		updateStartY = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
		if (busySide == Side.NONE) {
			EdgeEffectHandler edgeEffectHandler = listView.getEdgeEffectHandler();
			if (action == MotionEvent.ACTION_DOWN) {
				if (edgeEffectHandler != null) {
					edgeEffectHandler.setPullable(EdgeEffectHandler.Side.TOP, true);
					edgeEffectHandler.setPullable(EdgeEffectHandler.Side.BOTTOM, true);
				}
			} else {
				float dy = ev != null ? ev.getY() - startY : 0;
				boolean resetTop = true;
				boolean resetBottom = true;
				if (action == MotionEvent.ACTION_MOVE) {
					// Call getIdlePullStrain to get previous transient value
					if (dy > 0 && listView.isScrolledToTop() &&
							(pullSides == Side.BOTH || pullSides == Side.TOP)) {
						pull = true;
						resetTop = false;
						int pullStrain = topView.getAndResetIdlePullStrain();
						if (pullStrain > 0) {
							startY -= pullStrainToDelta(pullStrain);
							dy = ev.getY() - startY;
						}
						int padding = listView.getEdgeEffectShift(EdgeEffectHandler.Side.TOP);
						topView.setState(PullView.State.PULL, padding);
						topView.setPullStrain(deltaToPullStrain(dy), padding);
						if (edgeEffectHandler != null) {
							edgeEffectHandler.finish(EdgeEffectHandler.Side.TOP);
							edgeEffectHandler.setPullable(EdgeEffectHandler.Side.TOP, false);
						}
					} else if (dy < 0 && listView.isScrolledToBottom() &&
							(pullSides == Side.BOTH || pullSides == Side.BOTTOM)) {
						pull = true;
						resetBottom = false;
						int pullStrain = bottomView.getAndResetIdlePullStrain();
						if (pullStrain > 0) {
							startY += pullStrainToDelta(pullStrain);
							dy = ev.getY() - startY;
						}
						int padding = listView.getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
						bottomView.setState(PullView.State.PULL, padding);
						bottomView.setPullStrain(-deltaToPullStrain(dy), padding);
						if (edgeEffectHandler != null) {
							edgeEffectHandler.finish(EdgeEffectHandler.Side.BOTTOM);
							edgeEffectHandler.setPullable(EdgeEffectHandler.Side.BOTTOM, false);
						}
					}
				}
				int topPullStrain = topView.getPullStrain();
				int bottomPullStrain = bottomView.getPullStrain();
				if (resetTop && resetBottom && (topPullStrain > 0 || bottomPullStrain > 0)) {
					if (topPullStrain > bottomPullStrain) {
						topJumpStartTime = topView.calculateJumpStartTime();
					} else {
						bottomJumpStartTime = bottomView.calculateJumpStartTime();
					}
				}
				if (action == MotionEvent.ACTION_UP) {
					if (topPullStrain >= PullView.MAX_STRAIN) {
						topJumpStartTime = SystemClock.elapsedRealtime();
						boolean success = startBusyState(Side.TOP, true);
						resetTop &= !success;
					}
					if (bottomPullStrain >= PullView.MAX_STRAIN) {
						bottomJumpStartTime = SystemClock.elapsedRealtime();
						boolean success = startBusyState(Side.BOTTOM, true);
						resetBottom &= !success;
					}
				}
				if (resetTop) {
					topView.setState(PullView.State.IDLE,
							listView.getEdgeEffectShift(EdgeEffectHandler.Side.TOP));
				}
				if (resetBottom) {
					bottomView.setState(PullView.State.IDLE,
							listView.getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM));
				}
			}
		}
		return pull;
	}

	private int lastShiftValue = 0;
	private int beforeShiftValue = 0;
	private boolean beforeRestoreCanvas = false;

	public void drawBefore(Canvas canvas) {
		int top = topView.calculateJumpValue(topJumpStartTime);
		int bottom = bottomView.calculateJumpValue(bottomJumpStartTime);
		int shift = top > bottom ? top : -bottom;
		if (shift != 0) {
			canvas.save();
			float height = listView.getHeight();
			float dy = (float) (Math.pow(Math.abs(pullStrainToDelta(shift)) / height, 2.5f))
					* height * Math.signum(shift);
			canvas.translate(0, dy);
			beforeRestoreCanvas = true;
		}
		beforeShiftValue = shift;
	}

	public void drawAfter(Canvas canvas) {
		if (beforeRestoreCanvas) {
			beforeRestoreCanvas = false;
			canvas.restore();
		}
		int shift = beforeShiftValue;
		topView.draw(canvas, listView.getEdgeEffectShift(EdgeEffectHandler.Side.TOP));
		bottomView.draw(canvas, listView.getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM));
		if (lastShiftValue != shift) {
			lastShiftValue = shift;
			listView.invalidate();
		}
	}

	private interface PullView {
		enum State {IDLE, PULL, LOADING}

		int MAX_STRAIN = 1000;

		void setColor(int color);
		void setState(State state, int padding);
		void setPullStrain(int pullStrain, int padding);
		int getPullStrain();
		int getAndResetIdlePullStrain();
		void draw(Canvas canvas, int padding);
		long calculateJumpStartTime();
		int calculateJumpValue(long jumpStartTime);
	}

	private static class JellyBeanView implements PullView {
		private static final int IDLE_FOLD_TIME = 500;
		private static final int LOADING_HALF_CYCLE_TIME = 600;

		private final WeakReference<Wrapped> wrapped;
		private final Paint paint = new Paint();
		private final int height;
		private final boolean top;

		private State previousState = State.IDLE;
		private State state = State.IDLE;

		private int startIdlePullStrain = 0;
		private long timeIdleStart = 0L;
		private long timeLoadingStart = 0L;
		private long timeLoadingToIdleStart = 0L;

		private int pullStrain = 0;

		private int color;

		public JellyBeanView(Wrapped wrapped, boolean top) {
			this.wrapped = new WeakReference<>(wrapped);
			this.height = (int) (3f * ResourceUtils.obtainDensity(wrapped.getContext()) + 0.5f);
			this.top = top;
		}

		@Override
		public void setColor(int color) {
			this.color = color;
		}

		private void invalidate(int padding) {
			Wrapped wrapped = this.wrapped.get();
			if (wrapped != null) {
				int offset = top ? padding : wrapped.getHeight() - height - padding;
				invalidate(0, offset, wrapped.getWidth(), offset + height);
			}
		}

		private void invalidate(int l, int t, int r, int b) {
			Wrapped wrapped = this.wrapped.get();
			if (wrapped != null) {
				wrapped.invalidate(l, t, r, b);
			}
		}

		@Override
		public void setState(State state, int padding) {
			if (this.state != state) {
				State prePreviousState = previousState;
				previousState = this.state;
				this.state = state;
				long time = SystemClock.elapsedRealtime();
				switch (this.state) {
					case IDLE: {
						timeIdleStart = time;
						if (previousState == State.LOADING) {
							timeLoadingToIdleStart = time;
						}
						startIdlePullStrain = previousState == State.LOADING ? 0 : pullStrain;
						pullStrain = 0;
						break;
					}
					case PULL: {
						break;
					}
					case LOADING: {
						// May continue use old animation until it over
						boolean loadingToLoading = prePreviousState == State.LOADING &&
								previousState == State.IDLE && time - timeIdleStart < LOADING_HALF_CYCLE_TIME;
						if (!loadingToLoading) {
							timeLoadingStart = previousState == State.IDLE ? time + LOADING_HALF_CYCLE_TIME : time;
						}
						timeLoadingToIdleStart = 0L;
						break;
					}
				}
				invalidate(padding);
			}
		}

		@Override
		public void setPullStrain(int pullStrain, int padding) {
			this.pullStrain = pullStrain;
			if (this.pullStrain > MAX_STRAIN) {
				this.pullStrain = MAX_STRAIN;
			} else if (this.pullStrain < 0) {
				this.pullStrain = 0;
			}
			if (state == State.PULL) {
				invalidate(padding);
			}
		}

		@Override
		public int getPullStrain() {
			return pullStrain;
		}

		@Override
		public int getAndResetIdlePullStrain() {
			if (startIdlePullStrain == 0) {
				return 0;
			}
			try {
				return (int) (MAX_STRAIN * getIdleTransientPullStrainValue(SystemClock.elapsedRealtime()));
			} finally {
				startIdlePullStrain = 0;
			}
		}

		private float getIdleTransientPullStrainValue(long time) {
			int foldTime = IDLE_FOLD_TIME * startIdlePullStrain / MAX_STRAIN;
			if (foldTime <= 0) {
				return 0f;
			}
			float value = Math.min((float) (time - timeIdleStart) / foldTime, 1f);
			return (1f - value) * startIdlePullStrain / MAX_STRAIN;
		}

		@Override
		public void draw(Canvas canvas, int padding) {
			Wrapped wrapped = this.wrapped.get();
			if (wrapped == null) {
				return;
			}
			Paint paint = this.paint;
			long time = SystemClock.elapsedRealtime();
			int width = wrapped.getWidth();
			int height = this.height;
			int offset = top ? padding : wrapped.getHeight() - height - padding;
			State state = this.state;
			State previousState = this.previousState;
			int primaryColor = color;
			int secondaryColor = 0x80 << 24 | 0x00ffffff & color;
			boolean needInvalidate = false;

			if (state == State.PULL) {
				int size = (int) (width / 2f * Math.pow((float) pullStrain / MAX_STRAIN, 2f));
				paint.setColor(primaryColor);
				canvas.drawRect(width / 2f - size, offset, width / 2f + size, offset + height, paint);
			}

			if (state == State.IDLE && previousState != State.LOADING) {
				float value = getIdleTransientPullStrainValue(time);
				int size = (int) (width / 2f * Math.pow(value, 4f));
				paint.setColor(primaryColor);
				canvas.drawRect(width / 2f - size, offset, width / 2f + size, offset + height, paint);
				if (value != 0) {
					needInvalidate = true;
				}
			}

			if (state == State.LOADING || timeLoadingToIdleStart > 0L) {
				Interpolator interpolator = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR;
				final int cycle = 2 * LOADING_HALF_CYCLE_TIME;
				final int half = LOADING_HALF_CYCLE_TIME;
				long elapsed = time - timeLoadingStart;
				boolean startTransient = elapsed < 0;
				if (startTransient) {
					elapsed += cycle;
				}
				int phase = (int) (elapsed % cycle);
				int partWidth;
				if (state != State.LOADING) {
					long elapsedIdle = time - timeLoadingToIdleStart;
					float value = Math.min((float) elapsedIdle / half, 1f);
					partWidth = (int) (width / 2f * (1f - interpolator.getInterpolation(value)));
					if (partWidth <= 0) {
						partWidth = 0;
						timeLoadingToIdleStart = 0L;
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
			return SystemClock.elapsedRealtime() - BUSY_JUMP_TIME * (MAX_STRAIN - pullStrain) / MAX_STRAIN;
		}

		@Override
		public int calculateJumpValue(long jumpStartTime) {
			int value = 0;
			switch (state) {
				case PULL: {
					value = pullStrain;
					break;
				}
				case IDLE:
				case LOADING: {
					if (jumpStartTime > 0) {
						value = (int) (MAX_STRAIN * (SystemClock.elapsedRealtime() - jumpStartTime) / BUSY_JUMP_TIME);
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
		private static final int SHADOW_SIZE = 4;
		private static final int SHADOW_SHIFT = 2;
		private static final float CENTER_RADIUS = 8.75f;
		private static final float STROKE_WIDTH = 2.5f;

		private final WeakReference<Wrapped> wrapped;
		private final boolean top;
		private final int radius;
		private final int commonShift;
		private final int shadowSize;
		private final int shadowShift;
		private final float ringRadius;
		private final float strokeWidth;

		private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();
		private final RectF rectF = new RectF();
		private final Bitmap shadow;

		private final Interpolator startInterpolator;
		private final Interpolator endInterpolator;

		private State previousState = State.IDLE;
		private State state = State.IDLE;

		private int startFoldingPullStrain = 0;
		private long timeStateStart = 0L;
		private long timeSpinStart = 0L;
		private float spinOffset = 0f;

		private int pullStrain = 0;

		public LollipopView(Wrapped wrapped, boolean top) {
			this.wrapped = new WeakReference<>(wrapped);
			this.top = top;
			float density = ResourceUtils.obtainDensity(wrapped.getContext());
			radius = (int) (CIRCLE_RADIUS * density);
			commonShift = (int) (DEFAULT_CIRCLE_TARGET * density);
			shadowSize = (int) (SHADOW_SIZE * density);
			shadowShift = (int) (SHADOW_SHIFT * density);
			ringRadius = CENTER_RADIUS * density;
			strokeWidth = STROKE_WIDTH * density;
			Path startPath = new Path();
			startPath.lineTo(0.5f, 0f);
			startPath.cubicTo(0.7f, 0f, 0.6f, 1f, 1f, 1f);
			startInterpolator = new PathInterpolator(startPath);
			Path endPath = new Path();
			endPath.cubicTo(0.2f, 0f, 0.1f, 1f, 0.5f, 1f);
			endPath.lineTo(1f, 1f);
			endInterpolator = new PathInterpolator(endPath);
			ringPaint.setStyle(Paint.Style.STROKE);
			ringPaint.setStrokeCap(Paint.Cap.SQUARE);
			ringPaint.setStrokeJoin(Paint.Join.MITER);
			int bitmapSize = 2 * radius + shadowSize + shadowShift;
			shadow = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(shadow);
			Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			shadowPaint.setColor(0x7f000000);
			shadowPaint.setMaskFilter(new BlurMaskFilter(shadowSize, BlurMaskFilter.Blur.NORMAL));
			canvas.drawCircle(bitmapSize / 2f, bitmapSize / 2f,
					C.API_Q ? radius - shadowSize : radius - shadowSize / 2f, shadowPaint);
		}

		@Override
		public void setColor(int color) {
			circlePaint.setColor(color);
		}

		private void invalidate(int padding) {
			Wrapped wrapped = this.wrapped.get();
			if (wrapped != null) {
				invalidate(wrapped, wrapped.getWidth(), padding);
			}
		}

		@SuppressWarnings("UnnecessaryLocalVariable")
		private void invalidate(Wrapped wrapped, int width, int padding) {
			int hw = width / 2;
			int radius = this.radius;
			int shadowSize = this.shadowSize;
			int shadowShift = this.shadowShift;
			int l = hw - radius - shadowSize - 1;
			int r = hw + radius + shadowSize + 1;
			int t = padding - 2 * radius - shadowSize + shadowShift - 1;
			int b = padding + 2 * commonShift + shadowSize + shadowShift + 1;
			if (!top) {
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
			if (this.state != state) {
				State prePreviousState = previousState;
				previousState = this.state;
				this.state = state;
				long time = SystemClock.elapsedRealtime();
				switch (this.state) {
					case IDLE: {
						if (previousState == State.LOADING) {
							int pullStrain = (int) (MAX_STRAIN *
									getIdleTransientPullStrainValue(IDLE_FOLD_TIME, time));
							startFoldingPullStrain = Math.max(MAX_STRAIN, pullStrain);
						} else {
							startFoldingPullStrain = pullStrain;
						}
						timeStateStart = time;
						pullStrain = 0;
						break;
					}
					case PULL: {
						break;
					}
					case LOADING: {
						// May continue use old animation until it over
						boolean loadingToLoading = prePreviousState == State.LOADING &&
								previousState == State.IDLE && time - timeStateStart < 50;
						if (!loadingToLoading) {
							timeStateStart = time;
							startFoldingPullStrain = pullStrain;
							timeSpinStart = previousState == State.IDLE ? time : time - FULL_CYCLE_TIME / 10;
							if (previousState == State.IDLE) {
								spinOffset = 0f;
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
			this.pullStrain = pullStrain;
			if (this.pullStrain > 2 * MAX_STRAIN) {
				this.pullStrain = 2 * MAX_STRAIN;
			} else if (this.pullStrain < 0) {
				this.pullStrain = 0;
			}
			if (state == State.PULL) {
				invalidate(padding);
			}
		}

		@Override
		public int getPullStrain() {
			return Math.min(pullStrain, MAX_STRAIN);
		}

		@Override
		public int getAndResetIdlePullStrain() {
			if (startFoldingPullStrain == 0) {
				return 0;
			}
			try {
				return (int) (MAX_STRAIN * getIdleTransientPullStrainValue(IDLE_FOLD_TIME,
						SystemClock.elapsedRealtime()));
			} finally {
				startFoldingPullStrain = 0;
			}
		}

		private float getIdleTransientPullStrainValue(int maxFoldTime, long time) {
			int foldTime = maxFoldTime * startFoldingPullStrain / MAX_STRAIN;
			if (foldTime <= 0) {
				return 0f;
			}
			float value = Math.min((float) (time - timeStateStart) / foldTime, 1f);
			return (1f - value) * startFoldingPullStrain / MAX_STRAIN;
		}

		@Override
		public void draw(Canvas canvas, int padding) {
			Wrapped wrapped = this.wrapped.get();
			if (wrapped == null) {
				return;
			}
			Paint circlePaint = this.circlePaint;
			Paint ringPaint = this.ringPaint;
			long time = SystemClock.elapsedRealtime();
			int width = wrapped.getWidth();
			int height = wrapped.getHeight();
			State state = this.state;
			State previousState = this.previousState;
			boolean needInvalidate = false;

			float value = 0f;
			float scale = 1f;
			boolean spin = false;

			if (state == State.PULL) {
				value = (float) pullStrain / MAX_STRAIN;
			}

			if (state == State.IDLE) {
				if (previousState == State.LOADING) {
					value = getIdleTransientPullStrainValue(LOADING_FOLD_TIME, time);
					if (value > 0f) {
						scale = Math.min(1f, value);
						value = Math.max(1f, value);
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
					scale = Math.min((float) (time - timeStateStart) / LOADING_FOLD_TIME, 1f);
				}
				needInvalidate = true;
			}

			if (value != 0f) {
				int shift = (int) (commonShift * value) + padding;
				if (!top) {
					shift = height - shift;
				}
				float centerX = width / 2f;
				float centerY = top ? shift - radius : shift + radius;
				boolean needRestore = false;
				if (scale != 1f && scale != 0f) {
					canvas.save();
					canvas.scale(scale, scale, centerX, centerY);
					needRestore = true;
				}
				ringPaint.setStrokeWidth(strokeWidth);
				canvas.drawBitmap(shadow, centerX - shadow.getWidth() / 2f,
						centerY - shadow.getHeight() / 2f + shadowShift, circlePaint);
				canvas.drawCircle(centerX, centerY, radius, circlePaint);

				float arcStart;
				float arcLength;
				int ringAlpha = 0xff;
				if (spin) {
					float rotationValue = (float) ((time - timeSpinStart) % FULL_CYCLE_TIME) / FULL_CYCLE_TIME;
					float animationValue = rotationValue * 5f % 1f;
					float trimOffset = 0.25f * animationValue;
					float trimStart = 0.75f * startInterpolator.getInterpolation(animationValue) + trimOffset;
					float trimEnd = 0.75f * endInterpolator.getInterpolation(animationValue) + trimOffset;
					float rotation = 2f * rotationValue;
					arcStart = trimStart;
					arcLength = trimEnd - arcStart;
					arcStart += rotation + spinOffset;
				} else {
					float alphaThreshold = 0.95f;
					ringAlpha = (int) AnimationUtils.lerp(0x7f, 0xff, (Math.min(1f, Math.max(value, alphaThreshold))
							- alphaThreshold) / (1f - alphaThreshold));
					arcStart = value > 1f ? 0.25f + (value - 1f) * 0.5f : 0.25f * value;
					arcLength = 0.75f * (float) Math.pow(Math.min(value, 1f), 0.75f);
					if (value >= 1f) {
						spinOffset = (value - 1f) * 0.5f;
					}
				}
				ringPaint.setColor(0xffffff | (ringAlpha << 24));
				RectF size = rectF;
				size.set(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius);
				drawArc(canvas, ringPaint, size, arcStart, arcLength);
				if (needRestore) {
					canvas.restore();
				}
			}

			if (needInvalidate) {
				invalidate(wrapped, width, padding);
			}
		}

		private void drawArc(Canvas canvas, Paint paint, RectF size, float start, float length) {
			if (length < 0.001f) {
				length = 0.001f;
			}
			Path path = this.path;
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
		Context getContext();
		Resources getResources();
		EdgeEffectHandler getEdgeEffectHandler();
		boolean isScrolledToTop();
		boolean isScrolledToBottom();
		void invalidate(int l, int t, int r, int b);
		void invalidate();
		int getWidth();
		int getHeight();
	}
}
