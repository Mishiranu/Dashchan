package com.mishiranu.dashchan.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;

public class PhotoView extends View implements ScaleGestureDetector.OnScaleGestureListener {
	private enum ScrollEdge {NONE, START, END, BOTH}
	private enum TouchMode {UNDEFINED, COMMON, CLOSING_START, CLOSING_END, CLOSING_BOTH}

	private static final float CLOSE_SWIPE_FACTOR = 0.25f;

	private static final int INVALID_POINTER_ID = -1;

	private final TransparentTileDrawable tile;
	private Drawable drawable;
	private boolean hasAlpha;
	private boolean fitScreen;
	private boolean drawDim;
	private final Point previousDimensions = new Point();
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

	private int[] initialScalingData;
	private ValueAnimator initialScalingAnimator;
	private Rect initialScaleClipRect;

	private float minimumScale;
	private float maximumScale;
	private float doubleTapScale;
	private float initialScale;

	private final Matrix baseMatrix = new Matrix();
	private final Matrix transformMatrix = new Matrix();
	private final Matrix displayMatrix = new Matrix();
	private final RectF workRect = new RectF();
	private final float[] matrixValues = new float[9];

	private Listener listener;

	private final GestureDetector gestureDetector;
	private final ScaleGestureDetector scaleGestureDetector;

	private FlingRunnable flingRunnable;
	private ScrollEdge scrollEdgeX = ScrollEdge.BOTH;
	private ScrollEdge scrollEdgeY = ScrollEdge.BOTH;

	private TouchMode touchMode = TouchMode.UNDEFINED;
	private AnimatedRestoreSwipeRunnable animatedRestoreSwipeRunnable;

	private int activePointerId = INVALID_POINTER_ID;
	private int activePointerIndex = 0;

	private float lastTouchX;
	private float lastTouchY;

	private final float touchSlop;
	private final float minimumVelocity;

	private boolean isDoubleTapDown;
	private boolean isQuickScale;

	private VelocityTracker velocityTracker;
	private boolean isDragging;
	private boolean isParentDragging;

	public PhotoView(Context context, AttributeSet attr) {
		super(context, attr);
		ViewConfiguration configuration = ViewConfiguration.get(context);
		minimumVelocity = configuration.getScaledMinimumFlingVelocity();
		touchSlop = configuration.getScaledTouchSlop();
		gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDoubleTapEvent(MotionEvent e) {
				return PhotoView.this.onDoubleTapEvent(e);
			}

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				return ViewCompat.isAttachedToWindow(PhotoView.this) && PhotoView.this.onSingleTapConfirmed(e);
			}

			@Override
			public void onLongPress(MotionEvent e) {
				if (!isDragging && !isParentDragging && ViewCompat.isAttachedToWindow(PhotoView.this)) {
					PhotoView.this.onLongPress(e);
				}
			}
		});
		scaleGestureDetector = new ScaleGestureDetector(getContext(), this);
		tile = new TransparentTileDrawable(context, true);
		initBaseMatrix(false);
	}

	private final Rect lastLayout = new Rect();

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		if (top != lastLayout.top || bottom != lastLayout.bottom
				|| left != lastLayout.left || right != lastLayout.right) {
			lastLayout.set(left, top, right, bottom);
			resetScale();
		}
		super.onLayout(changed, left, top, right, bottom);
	}

	public interface Listener {
		void onClick(PhotoView photoView, boolean image, float x, float y);
		void onLongClick(PhotoView photoView, float x, float y);
		void onVerticalSwipe(PhotoView photoView, boolean down, float value);
		boolean onClose(PhotoView photoView, boolean down);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void recycle() {
		if (drawable != null) {
			drawable.setCallback(null);
			unscheduleDrawable(drawable);
			drawable = null;
			invalidate();
			post(() -> {
				// Check drawable wasn't set immediately after recycle call
				if (drawable == null) {
					// Disallow scale keeping
					previousDimensions.set(0, 0);
					cancelRestoreVerticalSwipe(false);
					notifyVerticalSwipe(0f, false);
				}
			});
		}
	}

	public void setImage(Drawable drawable, boolean hasAlpha, boolean fitScreen, boolean keepScale) {
		recycle();
		this.drawable = drawable;
		this.hasAlpha = hasAlpha;
		this.fitScreen = fitScreen;
		drawDim = false;
		drawable.setCallback(this);
		Point dimensions = getDimensions();
		keepScale &= previousDimensions.equals(dimensions);
		previousDimensions.set(dimensions.x, dimensions.y);
		initBaseMatrix(keepScale);
	}

	public boolean hasImage() {
		return drawable != null;
	}

	public void setDrawDimForCurrentImage(boolean drawDim) {
		if (this.drawDim != drawDim) {
			this.drawDim = drawDim;
			invalidate();
		}
	}

	public void setInitialScaleAnimationData(int[] imageViewPosition, boolean cropEnabled) {
		initialScalingData = new int[] {imageViewPosition[0], imageViewPosition[1], imageViewPosition[2],
				imageViewPosition[3], cropEnabled ? 1 : 0};
	}

	public void clearInitialScaleAnimationData() {
		initialScalingData = null;
		if (initialScalingAnimator != null) {
			initialScalingAnimator.cancel();
			initialScalingAnimator = null;
			initialScaleClipRect = null;
			setScale(initialScale);
		}
	}

	private void handleInitialScale() {
		int[] initialScalingData = this.initialScalingData;
		if (initialScalingData != null) {
			clearInitialScaleAnimationData();
			int[] location = new int[2];
			getLocationOnScreen(location);
			int x = initialScalingData[0] - location[0];
			int y = initialScalingData[1] - location[1];
			int viewWidth = initialScalingData[2];
			int viewHeight = initialScalingData[3];
			float centerX = x + viewWidth / 2f;
			float centerY = y + viewHeight / 2f;
			boolean cropEnabled = initialScalingData[4] != 0;
			initialScalingAnimator = ValueAnimator.ofFloat(0f, 1f);
			initialScalingAnimator.addUpdateListener(new InitialScaleListener(centerX, centerY,
					viewWidth, viewHeight, cropEnabled));
			initialScalingAnimator.setDuration(INITIAL_SCALE_TRANSITION_TIME);
			initialScalingAnimator.start();
		}
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		updateTextureSize(canvas);
		Point dimensions = getDimensions();
		if (dimensions == null) {
			return;
		}
		handleInitialScale();
		boolean restoreClip = false;
		if (initialScaleClipRect != null) {
			restoreClip = true;
			canvas.save();
			canvas.clipRect(initialScaleClipRect);
		}
		RectF rect = initDisplayMatrixAndRect();
		int workAlpha = 0xff;
		if (isClosingTouchMode()) {
			float value = Math.min(Math.abs(getClosingTouchModeShift(rect)) * 2f / getHeight(), 1f);
			workAlpha = (int) (workAlpha * (1f - value));
			float scale = (1f - AnimationUtils.ACCELERATE_INTERPOLATOR.getInterpolation(value)) * 0.4f + 0.6f;
			displayMatrix.postScale(scale, scale, getWidth() / 2f, getHeight() / 2f);
			rect = initDisplayRect();
		}
		boolean restoreAlpha = false;
		if (workAlpha != 0xff) {
			if (C.API_LOLLIPOP) {
				canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), workAlpha);
			} else {
				@SuppressWarnings({"deprecation", "unused"})
				int ignored = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), workAlpha, Canvas.ALL_SAVE_FLAG);
			}
			restoreAlpha = true;
		}
		if (drawable != null) {
			if (hasAlpha) {
				tile.setBounds((int) (rect.left + 0.5f), (int) (rect.top + 0.5f), (int) (rect.right + 0.5f),
						(int) (rect.bottom + 0.5f));
				tile.draw(canvas);
			}
			canvas.save();
			canvas.clipRect(0, 0, getWidth(), getHeight());
			canvas.concat(displayMatrix);
			drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
			drawable.draw(canvas);
			canvas.restore();
		}
		if (drawDim) {
			canvas.save();
			canvas.concat(displayMatrix);
			paint.setColor(0x44000000);
			canvas.drawRect(0, 0, dimensions.x, dimensions.y, paint);
			canvas.restore();
		}
		if (restoreAlpha) {
			canvas.restore();
		}
		if (restoreClip) {
			canvas.restore();
		}
	}

	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		// Override this method instead of verifyDrawable because I use own matrix to concatenate canvas
		if (drawable == this.drawable) {
			invalidate();
		} else {
			super.invalidateDrawable(drawable);
		}
	}

	private int maximumImageSize;
	private final Object maximumImageSizeLock = new Object();

	private void updateTextureSize(Canvas canvas) {
		if (maximumImageSize == 0) {
			int maxSize = Math.min(canvas.getMaximumBitmapWidth(), canvas.getMaximumBitmapHeight());
			maximumImageSize = Math.min(maxSize, 2048);
			synchronized (maximumImageSizeLock) {
				maximumImageSizeLock.notifyAll();
			}
		}
	}

	public int getMaximumImageSizeAsync() throws InterruptedException {
		if (maximumImageSize == 0) {
			synchronized (maximumImageSizeLock) {
				while (maximumImageSize == 0) {
					maximumImageSizeLock.wait();
				}
			}
		}
		return maximumImageSize;
	}

	private final Point point = new Point();

	private Point getDimensions() {
		if (drawable == null) {
			return null;
		}
		point.set(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
		return point;
	}

	@Override
	protected void onDetachedFromWindow() {
		cleanup();
		super.onDetachedFromWindow();
	}

	public void cleanup() {
		cancelFling();
	}

	@Override
	public boolean onScale(ScaleGestureDetector detector) {
		boolean isQuickScale = this.isQuickScale;
		if (!isQuickScale || detector.getPreviousSpan() > 2 * touchSlop) {
			float factor = detector.getScaleFactor();
			if (factor > 0f) {
				if (isQuickScale) {
					factor = Math.max(0.75f, Math.min(factor, 1.25f));
				}
				onScale(factor, detector.getFocusX(), detector.getFocusY());
			}
		}
		return true;
	}

	@Override
	public boolean onScaleBegin(ScaleGestureDetector detector) {
		return true;
	}

	@Override
	public void onScaleEnd(ScaleGestureDetector detector) {}

	private float getScale() {
		transformMatrix.getValues(matrixValues);
		return (float) Math.sqrt(Math.pow(matrixValues[Matrix.MSCALE_X], 2) +
				Math.pow(matrixValues[Matrix.MSKEW_Y], 2));
	}

	private boolean onDoubleTapEvent(MotionEvent e) {
		if (e.getAction() == MotionEvent.ACTION_DOWN) {
			isDoubleTapDown = true;
		} else if (e.getAction() == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress()) {
			float scale = getScale();
			float x = e.getX();
			float y = e.getY();
			setScale(scale < doubleTapScale ? doubleTapScale : minimumScale, x, y, true);
			return true;
		}
		return false;
	}

	private boolean onSingleTapConfirmed(MotionEvent e) {
		if (listener != null) {
			float x = e.getX(), y = e.getY();
			RectF rect = checkMatrixBounds();
			if (rect != null && rect.contains(x, y)) {
				listener.onClick(this, true, x, y);
				return true;
			}
			listener.onClick(this, false, x, y);
		}
		return false;
	}

	private void onLongPress(MotionEvent e) {
		if (listener != null) {
			listener.onLongClick(this, e.getX(), e.getY());
		}
	}

	private void onScale(float scaleFactor, float focusX, float focusY) {
		if (checkTouchMode() && !fitScreen) {
			float scale = getScale();
			float maxFactor = maximumScale / scale;
			scaleFactor = Math.min(scaleFactor, maxFactor);
			if (scaleFactor == 1f) {
				return;
			}
			if (scale <= minimumScale && scaleFactor < 1f) {
				scaleFactor = (float) Math.pow(scaleFactor, 1f / 4f);
			}
			float minFactor = minimumScale / scale / 2f;
			scaleFactor = Math.max(scaleFactor, minFactor);
			if (scaleFactor == 1f) {
				return;
			}
			transformMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
			checkMatrixBoundsAndInvalidate();
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return false;
	}

	public void dispatchSimpleClick(boolean longClick, float x, float y) {
		if (listener != null && !hasImage() && ViewCompat.isAttachedToWindow(this)) {
			if (longClick) {
				listener.onLongClick(this, x, y);
			} else {
				listener.onClick(this, true, x, y);
			}
		}
	}

	public void dispatchSpecialTouchEvent(MotionEvent event) {
		if (hasImage()) {
			int action = event.getActionMasked();
			switch (action) {
				case MotionEvent.ACTION_DOWN: {
					isParentDragging = false;
					touchMode = TouchMode.UNDEFINED;
					cancelFling();
					break;
				}
				case MotionEvent.ACTION_CANCEL:
				case MotionEvent.ACTION_UP: {
					if (getScale() < minimumScale) {
						RectF rect = checkMatrixBounds();
						if (rect != null) {
							post(new AnimatedScaleRunnable(minimumScale, rect.centerX(), rect.centerY()));
						}
					}
					break;
				}
			}
			isDoubleTapDown = false;
			gestureDetector.onTouchEvent(event);
			scaleGestureDetector.onTouchEvent(event);
			if (action == MotionEvent.ACTION_DOWN && C.API_KITKAT) {
				isQuickScale = isDoubleTapDown && scaleGestureDetector.isQuickScaleEnabled();
				if (isQuickScale) {
					checkTouchMode();
				}
			}
			onCommonTouchEvent(event);
		}
	}

	public void dispatchIdleMoveTouchEvent(MotionEvent event) {
		lastTouchY = getActiveY(event);
		isParentDragging = true;
	}

	public boolean isClosingTouchMode() {
		return touchMode == TouchMode.CLOSING_START || touchMode == TouchMode.CLOSING_END ||
				touchMode == TouchMode.CLOSING_BOTH;
	}

	private float getClosingTouchModeShift(RectF rect) {
		if (touchMode == TouchMode.CLOSING_START) {
			return rect.top;
		} else if (touchMode == TouchMode.CLOSING_END) {
			return rect.bottom - getHeight();
		} else {
			return (rect.top + rect.bottom - getHeight()) / 2f;
		}
	}

	public boolean canScrollLeft() {
		return hasImage() && (scrollEdgeX != ScrollEdge.START && scrollEdgeX != ScrollEdge.BOTH
				|| isClosingTouchMode());
	}

	public boolean canScrollRight() {
		return hasImage() && (scrollEdgeX != ScrollEdge.END && scrollEdgeX != ScrollEdge.BOTH
				|| isClosingTouchMode());
	}

	public boolean isScaling() {
		return hasImage() && scaleGestureDetector.isInProgress();
	}

	public void setScale(float scale) {
		setScale(scale, false);
	}

	public void setScale(float scale, boolean animate) {
		setScale(scale, getRight() / 2f, getBottom() / 2f, animate);
	}

	public void setScale(float scale, float focalX, float focalY, boolean animate) {
		if (scale < minimumScale || scale > maximumScale) {
			return;
		}
		touchMode = TouchMode.UNDEFINED;
		if (animate) {
			post(new AnimatedScaleRunnable(scale, focalX, focalY));
		} else {
			transformMatrix.setScale(scale, scale, focalX, focalY);
			checkMatrixBoundsAndInvalidate();
		}
	}

	private void cancelFling() {
		if (flingRunnable != null) {
			flingRunnable.cancelFling();
			flingRunnable = null;
		}
	}

	private boolean checkTouchMode() {
		switch (touchMode) {
			case UNDEFINED: {
				touchMode = TouchMode.COMMON;
			}
			case COMMON: {
				return true;
			}
			case CLOSING_START:
			case CLOSING_END:
			case CLOSING_BOTH: {
				return false;
			}
		}
		throw new RuntimeException();
	}

	private void checkMatrixBoundsAndInvalidate() {
		checkMatrixBounds();
		invalidate();
	}

	private RectF checkMatrixBounds() {
		RectF rect = initDisplayMatrixAndRect();
		if (rect == null) {
			return null;
		}
		float width = rect.width();
		float height = rect.height();
		int viewHeight = getHeight();
		float deltaX = 0f;
		float deltaY = 0f;
		if (height <= viewHeight + 0.5f) {
			deltaY = (viewHeight - height) / 2 - rect.top;
			scrollEdgeY = ScrollEdge.BOTH;
		} else if (rect.top + 0.5f >= 0) {
			deltaY = -rect.top;
			scrollEdgeY = ScrollEdge.START;
		} else if (rect.bottom - 0.5f <= viewHeight) {
			deltaY = viewHeight - rect.bottom;
			scrollEdgeY = ScrollEdge.END;
		} else {
			if (isClosingTouchMode()) {
				notifyVerticalSwipe(0f, false);
				touchMode = TouchMode.COMMON; // Reset touch mode
			}
			scrollEdgeY = ScrollEdge.NONE;
		}
		if (isClosingTouchMode()) {
			notifyVerticalSwipe(-deltaY, false);
			deltaY = 0f;
		}
		int viewWidth = getWidth();
		if (width <= viewWidth) {
			deltaX = (viewWidth - width) / 2 - rect.left;
			scrollEdgeX = ScrollEdge.BOTH;
		} else if (rect.left >= 0) {
			scrollEdgeX = ScrollEdge.START;
			deltaX = -rect.left;
		} else if (rect.right <= viewWidth) {
			deltaX = viewWidth - rect.right;
			scrollEdgeX = ScrollEdge.END;
		} else {
			scrollEdgeX = ScrollEdge.NONE;
		}
		transformMatrix.postTranslate(deltaX, deltaY);
		return rect;
	}

	private RectF initDisplayMatrixAndRect() {
		displayMatrix.set(baseMatrix);
		displayMatrix.postConcat(transformMatrix);
		return initDisplayRect();
	}

	private RectF initDisplayRect() {
		Point dimensions = getDimensions();
		if (dimensions != null) {
			workRect.set(0, 0, dimensions.x, dimensions.y);
			displayMatrix.mapRect(workRect);
			return workRect;
		}
		return null;
	}

	public void resetScale() {
		initBaseMatrix(false);
	}

	private void initBaseMatrix(boolean keepScale) {
		cancelRestoreVerticalSwipe(true);
		Point dimensions = getDimensions();
		if (dimensions == null) {
			return;
		}
		float viewWidth = getWidth();
		float viewHeight = getHeight();
		int imageWidth = dimensions.x;
		int imageHeight = dimensions.y;
		baseMatrix.reset();
		float widthScale = viewWidth / imageWidth;
		float heightScale = viewHeight / imageHeight;
		float scale = Math.min(widthScale, heightScale);
		float postScale = 1f;
		if (scale > 1f) {
			postScale = scale;
			scale = 1f;
		} else if (scale <= 0f) {
			scale = 1f;
		}
		baseMatrix.postScale(scale, scale);
		baseMatrix.postTranslate((viewWidth - imageWidth * scale) / 2F, (viewHeight - imageHeight * scale) / 2F);
		if (!keepScale) {
			transformMatrix.reset();
			checkMatrixBounds();
		}
		if (fitScreen) {
			minimumScale = postScale;
			maximumScale = postScale;
			initialScale = postScale;
			doubleTapScale = postScale;
			setScale(postScale);
		} else {
			minimumScale = 1f;
			maximumScale = 4f / scale;
			initialScale = Math.min(postScale, maximumScale);
			doubleTapScale = postScale > 1f ? Math.min(postScale, maximumScale) : Math.min(1f / scale, 8f);
			if (!keepScale && postScale > 1f) {
				setScale(doubleTapScale);
			}
		}
		invalidate();
	}

	private float lastVerticalSwipeDeltaY = 0f;

	private void notifyVerticalSwipe(float deltaY, boolean restore) {
		if (lastVerticalSwipeDeltaY != deltaY) {
			float value = Math.min(Math.abs(deltaY / getHeight()) * 4f, 1f);
			if (value < 0.001f && value > -0.001f) {
				value = 0f;
			}
			lastVerticalSwipeDeltaY = deltaY;
			if (listener != null) {
				listener.onVerticalSwipe(this, deltaY >= 0 != restore, value);
			}
		}
	}

	private void cancelRestoreVerticalSwipe(boolean notify) {
		if (animatedRestoreSwipeRunnable != null) {
			removeCallbacks(animatedRestoreSwipeRunnable);
			animatedRestoreSwipeRunnable = null;
			if (notify) {
				notifyVerticalSwipe(0f, false);
			}
		}
	}

	private void startRestoreVerticalSwipe(RectF rect, boolean close, float velocity) {
		cancelRestoreVerticalSwipe(false);
		if (rect == null) {
			rect = checkMatrixBounds();
		}
		animatedRestoreSwipeRunnable = new AnimatedRestoreSwipeRunnable(rect, close, velocity);
		post(animatedRestoreSwipeRunnable);
	}

	private class AnimatedScaleRunnable implements Runnable {
		private static final int ZOOM_DURATION = 200;

		private final long startTime;
		private final Matrix startTransformMatrix;
		private final float focalX, focalY;
		private final float scaleStart, scaleEnd;

		public AnimatedScaleRunnable(float scale, float focalX, float focalY) {
			this.focalX = focalX;
			this.focalY = focalY;
			startTime = SystemClock.elapsedRealtime();
			startTransformMatrix = new Matrix(transformMatrix);
			scaleStart = getScale();
			scaleEnd = scale;
		}

		@Override
		public void run() {
			float t = (float) (SystemClock.elapsedRealtime() - startTime) / ZOOM_DURATION;
			boolean post = true;
			if (t >= 1f) {
				t = 1f;
				post = false;
			}
			t = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation(t);
			float scale = AnimationUtils.lerp(scaleStart, scaleEnd, t);
			float deltaScale = scale / scaleStart;
			transformMatrix.set(startTransformMatrix);
			transformMatrix.postScale(deltaScale, deltaScale, focalX, focalY);
			checkMatrixBoundsAndInvalidate();
			if (post) {
				postOnAnimation(this);
			}
		}
	}

	private class AnimatedRestoreSwipeRunnable implements Runnable {
		private static final int RESTORE_DURATION = 150;
		private static final int FINISH_DURATION_MAX = 500;
		private static final int FINISH_DURATION_MIN = RESTORE_DURATION;

		private final long startTime;
		private final float deltaY;
		private final boolean finish;
		private final int duration;

		public AnimatedRestoreSwipeRunnable(RectF rect, boolean finish, float velocity) {
			float height = rect.height();
			int viewHeight = getHeight();
			float deltaY = 0f;
			if (height <= viewHeight) {
				deltaY = (viewHeight - height) / 2 - rect.top;
			} else if (rect.top > 0) {
				deltaY = -rect.top;
			} else if (rect.bottom < viewHeight) {
				deltaY = viewHeight - rect.bottom;
			}
			if (finish) {
				// Fling image out of screen
				if (deltaY > 0f) {
					deltaY = -rect.bottom;
				} else {
					deltaY = viewHeight - rect.top;
				}
			}
			startTime = SystemClock.elapsedRealtime();
			this.deltaY = deltaY;
			this.finish = finish;
			if (finish) {
				int duration = (int) Math.abs(deltaY / velocity * 1000f);
				this.duration = Math.max(Math.min(duration, FINISH_DURATION_MAX), FINISH_DURATION_MIN);
			} else {
				duration = RESTORE_DURATION;
			}
		}

		private float lastDeltaY = 0f;

		@Override
		public void run() {
			float t = (float) (SystemClock.elapsedRealtime() - startTime) / duration;
			boolean post = true;
			if (t >= 1f) {
				t = 1f;
				post = false;
			}
			if (!finish) {
				t = AnimationUtils.DECELERATE_INTERPOLATOR.getInterpolation(t);
			}
			float deltaY = AnimationUtils.lerp(0, this.deltaY, t);
			float dy = deltaY - lastDeltaY;
			lastDeltaY = deltaY;
			transformMatrix.postTranslate(0, dy);
			if (post) {
				invalidate();
				postOnAnimation(this);
				if (!finish) {
					notifyVerticalSwipe(this.deltaY - deltaY, true);
				}
			} else {
				checkMatrixBoundsAndInvalidate();
				if (!finish) {
					notifyVerticalSwipe(0f, true);
				}
			}
		}
	}

	private class FlingRunnable implements Runnable {
		private final Scroller scroller;
		private int currentX, currentY;

		public FlingRunnable(Context context) {
			scroller = new Scroller(context);
		}

		public void cancelFling() {
			scroller.forceFinished(true);
		}

		public void fling(int viewWidth, int viewHeight, int velocityX, int velocityY) {
			RectF rect = checkMatrixBounds();
			if (rect == null) {
				return;
			}
			int startX = Math.round(-rect.left);
			int minX, maxX, minY, maxY;
			if (viewWidth < rect.width()) {
				minX = 0;
				maxX = Math.round(rect.width() - viewWidth);
			} else {
				minX = maxX = startX;
			}
			int startY = Math.round(-rect.top);
			if (viewHeight < rect.height()) {
				minY = 0;
				maxY = Math.round(rect.height() - viewHeight);
			} else {
				minY = maxY = startY;
			}
			currentX = startX;
			currentY = startY;
			if (startX != maxX || startY != maxY) {
				scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
			}
		}

		@Override
		public void run() {
			if (scroller.isFinished()) {
				return;
			}
			if (scroller.computeScrollOffset()) {
				int newX = scroller.getCurrX();
				int newY = scroller.getCurrY();
				transformMatrix.postTranslate(currentX - newX, currentY - newY);
				invalidate();
				currentX = newX;
				currentY = newY;
				postOnAnimation(this);
			}
		}
	}

	public static final int INITIAL_SCALE_TRANSITION_TIME = 400;

	private class InitialScaleListener implements ValueAnimator.AnimatorUpdateListener {
		private final float centerX, centerY;
		private final int viewWidth, viewHeight;
		private final boolean cropEnabled;

		private static final int WAIT_TIME = 100;
		private static final float TRANSFER_TIME_FACTOR = 1.5f;

		public InitialScaleListener(float centerX, float centerY, int viewWidth, int viewHeight, boolean cropEnabled) {
			this.centerX = centerX;
			this.centerY = centerY;
			this.viewWidth = viewWidth;
			this.viewHeight = viewHeight;
			this.cropEnabled = cropEnabled;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			baseMatrix.getValues(matrixValues);
			float baseScale = matrixValues[Matrix.MSCALE_Y];
			Point dimensions = getDimensions();
			if (dimensions == null) {
				return;
			}
			float scale = (dimensions.x * viewHeight > dimensions.y * viewWidth) == cropEnabled
					? (float) viewHeight / dimensions.y : (float) viewWidth / dimensions.x;
			scale /= baseScale;

			float t = (float) animation.getAnimatedValue();
			boolean finished = false;
			if (t >= 1f) {
				t = 1f;
				finished = true;
			}
			float wait = (float) WAIT_TIME / INITIAL_SCALE_TRANSITION_TIME;
			t = t >= wait ? AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR
					.getInterpolation((t - wait) / (1f - wait)) : 0f;

			if (!finished) {
				float ct = (float) Math.pow(t, TRANSFER_TIME_FACTOR); // Make XY transition faster
				float targetX = AnimationUtils.lerp(centerX, getWidth() / 2f, ct);
				float targetY = AnimationUtils.lerp(centerY, getHeight() / 2f, ct);
				float targetScale = AnimationUtils.lerp(scale, initialScale, t);
				float dx = -getWidth() / 2f + targetX;
				float dy = -getHeight() / 2f + targetY;
				transformMatrix.reset();
				transformMatrix.postTranslate(dx, dy);
				transformMatrix.postScale(targetScale, targetScale, targetX, targetY);
				if (cropEnabled) {
					if (initialScaleClipRect == null) {
						initialScaleClipRect = new Rect();
					}
					int sizeXY = (int) AnimationUtils.lerp(Math.min(dimensions.x, dimensions.y),
							Math.max(dimensions.x, dimensions.y), t);
					float scaledHalfSize = targetScale * baseScale * sizeXY / 2f;
					initialScaleClipRect.set((int) (targetX - scaledHalfSize - 0.5f), (int) (targetY - scaledHalfSize
							- 0.5f), (int) (targetX + scaledHalfSize + 0.5f), (int) (targetY + scaledHalfSize + 0.5f));
				}
				invalidate();
			} else {
				initialScalingAnimator = null;
				initialScaleClipRect = null;
				setScale(initialScale);
			}
		}
	}

	private float getActiveX(MotionEvent event) {
		try {
			return event.getX(activePointerIndex);
		} catch (Exception e) {
			return event.getX();
		}
	}

	private float getActiveY(MotionEvent event) {
		try {
			return event.getY(activePointerIndex);
		} catch (Exception e) {
			return event.getY();
		}
	}

	private boolean onCommonTouchEvent(MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				activePointerId = event.getPointerId(0);
				break;
			}
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				activePointerId = INVALID_POINTER_ID;
				break;
			}
			case MotionEvent.ACTION_POINTER_UP: {
				int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
						MotionEvent.ACTION_POINTER_INDEX_SHIFT;
				int pointerId = event.getPointerId(pointerIndex);
				if (pointerId == activePointerId) {
					int newPointerIndex = pointerIndex == 0 ? 1 : 0;
					activePointerId = event.getPointerId(newPointerIndex);
					lastTouchX = event.getX(newPointerIndex);
					lastTouchY = event.getY(newPointerIndex);
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_DOWN: {
				checkTouchMode();
				break;
			}
		}
		activePointerIndex = event.findPointerIndex(activePointerId != INVALID_POINTER_ID ? activePointerId : 0);
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				velocityTracker = VelocityTracker.obtain();
				if (velocityTracker != null) {
					velocityTracker.addMovement(event);
				}
				lastTouchX = getActiveX(event);
				lastTouchY = getActiveY(event);
				isDragging = false;
				break;
			}
			case MotionEvent.ACTION_MOVE: {
				float x = getActiveX(event);
				float y = getActiveY(event);
				float dx = x - lastTouchX;
				float dy = y - lastTouchY;
				float length = (float) Math.sqrt(dx * dx + dy * dy);
				if (!isDragging) {
					isDragging = length >= touchSlop;
				}
				if (isDragging) {
					if (touchMode == TouchMode.UNDEFINED) {
						boolean allowClosing = scrollEdgeY == ScrollEdge.BOTH ||
								scrollEdgeY == ScrollEdge.START && dy > 0 || scrollEdgeY == ScrollEdge.END && dy < 0;
						boolean closing = false;
						if (allowClosing) {
							float angle = (float) (Math.acos(Math.abs(dx / length)) * 180f / Math.PI);
							closing = angle >= 60;
						}
						if (closing) {
							switch (scrollEdgeY) {
								case NONE: {
									throw new RuntimeException();
								}
								case START: {
									touchMode = TouchMode.CLOSING_START;
									break;
								}
								case END: {
									touchMode = TouchMode.CLOSING_END;
									break;
								}
								case BOTH: {
									touchMode = TouchMode.CLOSING_BOTH;
									break;
								}
							}
						} else {
							touchMode = TouchMode.COMMON;
						}
					}
					if (!scaleGestureDetector.isInProgress()) {
						if (isClosingTouchMode()) {
							transformMatrix.postTranslate(0, dy * CLOSE_SWIPE_FACTOR);
						} else {
							transformMatrix.postTranslate(dx, dy);
						}
						checkMatrixBoundsAndInvalidate();
					}
					lastTouchX = x;
					lastTouchY = y;
					if (velocityTracker != null) {
						velocityTracker.addMovement(event);
					}
				}
				break;
			}
			case MotionEvent.ACTION_CANCEL: {
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
				break;
			}
			case MotionEvent.ACTION_UP: {
				if (isDragging) {
					if (isClosingTouchMode()) {
						RectF rect = checkMatrixBounds();
						if (rect != null) {
							int viewHeight = getHeight();
							float threshold = viewHeight * CLOSE_SWIPE_FACTOR / 2f;
							float shift = getClosingTouchModeShift(rect);
							float velocity = 0f;
							if (velocityTracker != null) {
								velocityTracker.addMovement(event);
								velocityTracker.computeCurrentVelocity(1000);
								velocity = velocityTracker.getYVelocity() * CLOSE_SWIPE_FACTOR;
								boolean increase = shift > 0 == velocity > 0;
								velocity = Math.abs(velocity);
								if (increase) {
									threshold *= 1f - Math.min(velocity / 1000f, 0.9f);
								} else {
									threshold *= Math.max(velocity / 1000f, 1f);
								}
							}
							boolean close = Math.abs(shift) >= threshold;
							if (listener != null && close) {
								close = listener.onClose(this, shift >= 0);
							}
							startRestoreVerticalSwipe(rect, close, velocity);
						}
					} else if (velocityTracker != null) {
						lastTouchX = getActiveX(event);
						lastTouchY = getActiveY(event);
						velocityTracker.addMovement(event);
						velocityTracker.computeCurrentVelocity(1000);
						float vX = velocityTracker.getXVelocity();
						float vY = velocityTracker.getYVelocity();
						if (Math.max(Math.abs(vX), Math.abs(vY)) >= minimumVelocity) {
							flingRunnable = new FlingRunnable(getContext());
							flingRunnable.fling(getWidth(), getHeight(), (int) -vX, (int) -vY);
							post(flingRunnable);
						}
					}
				}
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
				break;
			}
		}
		return true;
	}
}
