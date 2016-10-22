/*
 * Copyright 2011, 2012 Chris Banes.
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

package com.mishiranu.dashchan.widget;

import java.lang.reflect.Method;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import com.mishiranu.dashchan.graphics.TransparentTileDrawable;
import com.mishiranu.dashchan.util.AnimationUtils;

public class PhotoView extends View implements GestureDetector.OnDoubleTapListener,
		ScaleGestureDetector.OnScaleGestureListener
{
	private enum ScrollEdge {NONE, START, END, BOTH}
	private enum TouchMode {UNDEFINED, COMMON, CLOSING_START, CLOSING_END, CLOSING_BOTH}

	private static final float CLOSE_SWIPE_FACTOR = 0.25f;

	private static final int INVALID_POINTER_ID = -1;

	private final TransparentTileDrawable mTile;
	private Drawable mDrawable;
	private boolean mHasAlpha;
	private boolean mFitScreen;
	private boolean mDrawDim;
	private final Point mPreviousDimensions = new Point();
	private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

	private int[] mInitialScalingData;
	private Rect mInitialScaleClipRect;

	private float mMinimumScale, mMaximumScale, mDoubleTapScale, mInitialScale;
	private final Matrix mBaseMatrix = new Matrix();
	private final Matrix mTransformMatrix = new Matrix();
	private final Matrix mDisplayMatrix = new Matrix();
	private final RectF mWorkRect = new RectF();
	private final float[] mMatrixValues = new float[9];

	private Listener mListener;

	private final GestureDetector mGestureDetector;
	private final ScaleGestureDetector mScaleGestureDetector;

	private FlingRunnable mFlingRunnable;
	private ScrollEdge mScrollEdgeX = ScrollEdge.BOTH;
	private ScrollEdge mScrollEdgeY = ScrollEdge.BOTH;

	private TouchMode mTouchMode = TouchMode.UNDEFINED;
	private AnimatedRestoreSwipeRunnable mAnimatedRestoreSwipeRunnable;

	private int mActivePointerId = INVALID_POINTER_ID;
	private int mActivePointerIndex = 0;

	private float mLastTouchX;
	private float mLastTouchY;

	private final float mTouchSlop;
	private final float mMinimumVelocity;

	private VelocityTracker mVelocityTracker;
	private boolean mIsDragging;

	private static final Method METHOD_IN_DOUBLE_TAP_MODE;

	static
	{
		Method inDoubleTapMode;
		try
		{
			inDoubleTapMode = ScaleGestureDetector.class.getDeclaredMethod("inDoubleTapMode");
			inDoubleTapMode.setAccessible(true);
		}
		catch (Exception e)
		{
			inDoubleTapMode = null;
		}
		METHOD_IN_DOUBLE_TAP_MODE = inDoubleTapMode;
	}

	public PhotoView(Context context, AttributeSet attr)
	{
		super(context, attr);
		ViewConfiguration configuration = ViewConfiguration.get(context);
		mMinimumVelocity = configuration.getScaledMinimumFlingVelocity();
		mTouchSlop = configuration.getScaledTouchSlop();
		mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener());
		mGestureDetector.setOnDoubleTapListener(this);
		mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);
		mTile = new TransparentTileDrawable(context, true);
		initBaseMatrix(false);
	}

	private final Rect mLastLayout = new Rect();

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		if (top != mLastLayout.top || bottom != mLastLayout.bottom
				|| left != mLastLayout.left || right != mLastLayout.right)
		{
			mLastLayout.set(left, top, right, bottom);
			resetScale();
		}
		super.onLayout(changed, left, top, right, bottom);
	}

	public interface Listener
	{
		public void onClick(PhotoView photoView, boolean image, float x, float y);
		public void onVerticalSwipe(PhotoView photoView, float value);
		public boolean onClose(PhotoView photoView);
	}

	public void setListener(Listener listener)
	{
		mListener = listener;
	}

	public void recycle()
	{
		if (mDrawable != null)
		{
			mDrawable.setCallback(null);
			unscheduleDrawable(mDrawable);
			mDrawable = null;
			invalidate();
		}
	}

	public void setImage(Drawable drawable, boolean hasAlpha, boolean fitScreen, boolean keepScale)
	{
		recycle();
		mDrawable = drawable;
		mHasAlpha = hasAlpha;
		mFitScreen = fitScreen;
		mDrawDim = false;
		drawable.setCallback(this);
		Point dimensions = getDimensions();
		keepScale &= mPreviousDimensions.equals(dimensions);
		mPreviousDimensions.set(dimensions.x, dimensions.y);
		initBaseMatrix(keepScale);
	}

	public boolean hasImage()
	{
		return mDrawable != null;
	}

	public void setDrawDimForCurrentImage(boolean drawDim)
	{
		if (mDrawDim != drawDim)
		{
			mDrawDim = drawDim;
			invalidate();
		}
	}

	public void setInitialScaleAnimationData(int[] imageViewPosition, boolean cropEnabled)
	{
		mInitialScalingData = new int[] {imageViewPosition[0], imageViewPosition[1], imageViewPosition[2],
				imageViewPosition[3], cropEnabled ? 1 : 0};
	}

	public void clearInitialScaleAnimationData()
	{
		mInitialScalingData = null;
	}

	private void handleInitialScale()
	{
		int[] initialScalingData = mInitialScalingData;
		if (initialScalingData != null)
		{
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
			ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
			animator.addUpdateListener(new InitialScaleListener(centerX, centerY, viewWidth, viewHeight, cropEnabled));
			animator.setDuration(INITIAL_SCALE_TRANSITION_TIME);
			animator.start();
		}
	}

	@Override
	public void draw(Canvas canvas)
	{
		super.draw(canvas);
		updateTextureSize(canvas);
		Point dimensions = getDimensions();
		if (dimensions == null) return;
		handleInitialScale();
		boolean restoreClip = false;
		if (mInitialScaleClipRect != null)
		{
			restoreClip = true;
			canvas.save();
			canvas.clipRect(mInitialScaleClipRect);
		}
		RectF rect = initDisplayMatrixAndRect();
		int workAlpha = 0xff;
		if (isClosingTouchMode())
		{
			float value = Math.min(Math.abs(getClosingTouchModeShift(rect)) * 2f / getHeight(), 1f);
			workAlpha = (int) (workAlpha * (1f - value));
			float scale = (1f - AnimationUtils.ACCELERATE_INTERPOLATOR.getInterpolation(value)) * 0.4f + 0.6f;
			mDisplayMatrix.postScale(scale, scale, getWidth() / 2, getHeight() / 2);
			rect = initDisplayRect();
		}
		boolean restoreAlpha = false;
		if (workAlpha != 0xff)
		{
			canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), workAlpha, Canvas.ALL_SAVE_FLAG);
			restoreAlpha = true;
		}
		if (mDrawable != null)
		{
			if (mHasAlpha)
			{
				mTile.setBounds((int) (rect.left + 0.5f), (int) (rect.top + 0.5f), (int) (rect.right + 0.5f),
						(int) (rect.bottom + 0.5f));
				mTile.draw(canvas);
			}
			canvas.save();
			canvas.clipRect(0, 0, getWidth(), getHeight());
			canvas.concat(mDisplayMatrix);
			mDrawable.setBounds(0, 0, mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
			mDrawable.draw(canvas);
			canvas.restore();
		}
		if (mDrawDim)
		{
			canvas.save();
			canvas.concat(mDisplayMatrix);
			mPaint.setColor(0x44000000);
			canvas.drawRect(0, 0, dimensions.x, dimensions.y, mPaint);
			canvas.restore();
		}
		if (restoreAlpha) canvas.restore();
		if (restoreClip) canvas.restore();
	}

	@Override
	public void invalidateDrawable(Drawable drawable)
	{
		// Override this method instead of verifyDrawable because I use own matrix to concatenate canvas
		if (drawable == mDrawable) invalidate(); else super.invalidateDrawable(drawable);
	}

	private int mMaximumImageSize;
	private final Object mMaximumImageSizeLock = new Object();

	private void updateTextureSize(Canvas canvas)
	{
		if (mMaximumImageSize == 0)
		{
			int maxSize = Math.min(canvas.getMaximumBitmapWidth(), canvas.getMaximumBitmapHeight());
			mMaximumImageSize = Math.min(maxSize, 2048);
			synchronized (mMaximumImageSizeLock)
			{
				mMaximumImageSizeLock.notifyAll();
			}
		}
	}

	public int getMaximumImageSizeAsync()
	{
		if (mMaximumImageSize == 0)
		{
			synchronized (mMaximumImageSizeLock)
			{
				try
				{
					while (mMaximumImageSize == 0) mMaximumImageSizeLock.wait();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					return 0;
				}
			}
		}
		return mMaximumImageSize;
	}

	private final Point mPoint = new Point();

	private Point getDimensions()
	{
		if (mDrawable == null) return null;
		mPoint.set(mDrawable.getIntrinsicWidth(), mDrawable.getIntrinsicHeight());
		return mPoint;
	}

	@Override
	protected void onDetachedFromWindow()
	{
		cleanup();
		super.onDetachedFromWindow();
	}

	public void cleanup()
	{
		cancelFling();
	}

	@Override
	public boolean onScale(ScaleGestureDetector detector)
	{
		float factor = detector.getScaleFactor();
		// If factor <= 0, the image will be rotated, so let's apply only positive values
		if (factor > 0f) onScale(factor, detector.getFocusX(), detector.getFocusY());
		return true;
	}

	@Override
	public boolean onScaleBegin(ScaleGestureDetector detector)
	{
		return true;
	}

	@Override
	public void onScaleEnd(ScaleGestureDetector detector)
	{

	}

	private float getScale()
	{
		mTransformMatrix.getValues(mMatrixValues);
		return (float) Math.sqrt(Math.pow(mMatrixValues[Matrix.MSCALE_X], 2) +
				Math.pow(mMatrixValues[Matrix.MSKEW_Y], 2));
	}

	@Override
	public boolean onDoubleTap(MotionEvent e)
	{
		return false;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent e)
	{
		if (e.getAction() == MotionEvent.ACTION_UP && !mScaleGestureDetector.isInProgress())
		{
			float scale = getScale();
			float x = e.getX();
			float y = e.getY();
			setScale(scale < mDoubleTapScale ? mDoubleTapScale : mMinimumScale, x, y, true);
			return true;
		}
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e)
	{
		if (mListener != null)
		{
			float x = e.getX(), y = e.getY();
			RectF rect = checkMatrixBounds();
			if (rect != null && rect.contains(x, y))
			{
				mListener.onClick(this, true, x, y);
				return true;
			}
			mListener.onClick(this, false, x, y);
		}
		return false;
	}

	private void onScale(float scaleFactor, float focusX, float focusY)
	{
		if (checkTouchMode() && !mFitScreen)
		{
			float scale = getScale();
			float maxFactor = mMaximumScale / scale;
			scaleFactor = Math.min(scaleFactor, maxFactor);
			if (scaleFactor == 1f) return;
			if (scale <= mMinimumScale && scaleFactor < 1f) scaleFactor = (float) Math.pow(scaleFactor, 1f / 4f);
			float minFactor = mMinimumScale / scale / 2f;
			scaleFactor = Math.max(scaleFactor, minFactor);
			if (scaleFactor == 1f) return;
			mTransformMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
			checkMatrixBoundsAndInvalidate();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		return false;
	}

	public void dispatchSimpleClick(float x, float y)
	{
		if (mListener != null && !hasImage()) mListener.onClick(this, true, x, y);
	}

	public void dispatchSpecialTouchEvent(MotionEvent event)
	{
		if (hasImage())
		{
			//dbg.Log.d("dispatch", event.getAction(), event.getX(), event.getY());
			switch (event.getActionMasked())
			{
				case MotionEvent.ACTION_DOWN:
				{
					mTouchMode = TouchMode.UNDEFINED;
					cancelFling();
					break;
				}
				case MotionEvent.ACTION_CANCEL:
				case MotionEvent.ACTION_UP:
				{
					if (getScale() < mMinimumScale)
					{
						RectF rect = checkMatrixBounds();
						if (rect != null)
						{
							post(new AnimatedScaleRunnable(mMinimumScale, rect.centerX(), rect.centerY()));
						}
					}
					break;
				}
			}
			mGestureDetector.onTouchEvent(event);
			mScaleGestureDetector.onTouchEvent(event);
			if (METHOD_IN_DOUBLE_TAP_MODE != null && event.getAction() == MotionEvent.ACTION_DOWN)
			{
				boolean doubleTapScaling = false;
				try
				{
					doubleTapScaling = (boolean) METHOD_IN_DOUBLE_TAP_MODE.invoke(mScaleGestureDetector);
				}
				catch (Exception e)
				{

				}
				if (doubleTapScaling) checkTouchMode();
			}
			onCommonTouchEvent(event);
		}
	}

	public void dispatchIdleMoveTouchEvent(MotionEvent event)
	{
		mLastTouchY = getActiveY(event);
	}

	public boolean isClosingTouchMode()
	{
		return mTouchMode == TouchMode.CLOSING_START || mTouchMode == TouchMode.CLOSING_END ||
				mTouchMode == TouchMode.CLOSING_BOTH;
	}

	private float getClosingTouchModeShift(RectF rect)
	{
		if (mTouchMode == TouchMode.CLOSING_START) return rect.top;
		else if (mTouchMode == TouchMode.CLOSING_END) return rect.bottom - getHeight();
		else return (rect.top + rect.bottom - getHeight()) / 2f;
	}

	public boolean canScrollLeft()
	{
		return hasImage() && (mScrollEdgeX != ScrollEdge.START && mScrollEdgeX != ScrollEdge.BOTH
				|| isClosingTouchMode());
	}

	public boolean canScrollRight()
	{
		return hasImage() && (mScrollEdgeX != ScrollEdge.END && mScrollEdgeX != ScrollEdge.BOTH
				|| isClosingTouchMode());
	}

	public boolean isScaling()
	{
		return hasImage() && mScaleGestureDetector.isInProgress();
	}

	public void setScale(float scale)
	{
		setScale(scale, false);
	}

	public void setScale(float scale, boolean animate)
	{
		setScale(scale, (getRight()) / 2, (getBottom()) / 2, animate);
	}

	public void setScale(float scale, float focalX, float focalY, boolean animate)
	{
		if (scale < mMinimumScale || scale > mMaximumScale) return;
		mTouchMode = TouchMode.UNDEFINED;
		if (animate) post(new AnimatedScaleRunnable(scale, focalX, focalY)); else
		{
			mTransformMatrix.setScale(scale, scale, focalX, focalY);
			checkMatrixBoundsAndInvalidate();
		}
	}

	private void cancelFling()
	{
		if (mFlingRunnable != null)
		{
			mFlingRunnable.cancelFling();
			mFlingRunnable = null;
		}
	}

	private boolean checkTouchMode()
	{
		switch (mTouchMode)
		{
			case UNDEFINED:
			{
				mTouchMode = TouchMode.COMMON;
			}
			case COMMON:
			{
				return true;
			}
			case CLOSING_START:
			case CLOSING_END:
			case CLOSING_BOTH:
			{
				return false;
			}
		}
		throw new RuntimeException();
	}

	private void checkMatrixBoundsAndInvalidate()
	{
		checkMatrixBounds();
		invalidate();
	}

	private RectF checkMatrixBounds()
	{
		RectF rect = initDisplayMatrixAndRect();
		if (rect == null) return null;
		float width = rect.width();
		float height = rect.height();
		int viewHeight = getHeight();
		float deltaX = 0f;
		float deltaY = 0f;
		if (height <= viewHeight)
		{
			deltaY = (viewHeight - height) / 2 - rect.top;
			mScrollEdgeY = ScrollEdge.BOTH;
		}
		else if (rect.top + 0.5f >= 0)
		{
			deltaY = -rect.top;
			mScrollEdgeY = ScrollEdge.START;
		}
		else if (rect.bottom - 0.5f <= viewHeight)
		{
			deltaY = viewHeight - rect.bottom;
			mScrollEdgeY = ScrollEdge.END;
		}
		else
		{
			if (isClosingTouchMode())
			{
				notifyVerticalSwipe(0f);
				mTouchMode = TouchMode.COMMON; // Reset touch mode
			}
			mScrollEdgeY = ScrollEdge.NONE;
		}
		if (isClosingTouchMode())
		{
			notifyVerticalSwipe(deltaY);
			deltaY = 0f;
		}
		int viewWidth = getWidth();
		if (width <= viewWidth)
		{
			deltaX = (viewWidth - width) / 2 - rect.left;
			mScrollEdgeX = ScrollEdge.BOTH;
		}
		else if (rect.left >= 0)
		{
			mScrollEdgeX = ScrollEdge.START;
			deltaX = -rect.left;
		}
		else if (rect.right <= viewWidth)
		{
			deltaX = viewWidth - rect.right;
			mScrollEdgeX = ScrollEdge.END;
		}
		else mScrollEdgeX = ScrollEdge.NONE;
		mTransformMatrix.postTranslate(deltaX, deltaY);
		return rect;
	}

	private RectF initDisplayMatrixAndRect()
	{
		mDisplayMatrix.set(mBaseMatrix);
		mDisplayMatrix.postConcat(mTransformMatrix);
		return initDisplayRect();
	}

	private RectF initDisplayRect()
	{
		Point dimensions = getDimensions();
		if (dimensions != null)
		{
			mWorkRect.set(0, 0, dimensions.x, dimensions.y);
			mDisplayMatrix.mapRect(mWorkRect);
			return mWorkRect;
		}
		return null;
	}

	public void resetScale()
	{
		initBaseMatrix(false);
	}

	private void initBaseMatrix(boolean keepScale)
	{
		if (mAnimatedRestoreSwipeRunnable != null)
		{
			removeCallbacks(mAnimatedRestoreSwipeRunnable);
			mAnimatedRestoreSwipeRunnable = null;
			notifyVerticalSwipe(0f);
		}
		Point dimensions = getDimensions();
		if (dimensions == null) return;
		float viewWidth = getWidth();
		float viewHeight = getHeight();
		int imageWidth = dimensions.x;
		int imageHeight = dimensions.y;
		mBaseMatrix.reset();
		float widthScale = viewWidth / imageWidth;
		float heightScale = viewHeight / imageHeight;
		float scale = Math.min(widthScale, heightScale);
		float postScale = 1f;
		if (scale > 1f)
		{
			postScale = scale;
			scale = 1f;
		}
		else if (scale <= 0f) scale = 1f;
		mBaseMatrix.postScale(scale, scale);
		mBaseMatrix.postTranslate((viewWidth - imageWidth * scale) / 2F, (viewHeight - imageHeight * scale) / 2F);
		if (!keepScale)
		{
			mTransformMatrix.reset();
			checkMatrixBounds();
		}
		if (mFitScreen)
		{
			mMinimumScale = postScale;
			mMaximumScale = postScale;
			mInitialScale = postScale;
			mDoubleTapScale = postScale;
			setScale(postScale);
		}
		else
		{
			mMinimumScale = 1f;
			mMaximumScale = 4f / scale;
			mInitialScale = Math.min(postScale, mMaximumScale);
			mDoubleTapScale = postScale > 1f ? Math.min(postScale, mMaximumScale) : Math.min(1f / scale, 8f);
			if (!keepScale && postScale > 1f) setScale(mDoubleTapScale);
		}
		invalidate();
	}

	private float mLastVerticalSwipeValue = 0f;

	private void notifyVerticalSwipe(float deltaY)
	{
		float value = Math.min(Math.abs(deltaY / getHeight()), 1f);
		if (value < 0.001f && value > -0.001f) value = 0f;
		if (mLastVerticalSwipeValue != value)
		{
			mLastVerticalSwipeValue = value;
			if (mListener != null) mListener.onVerticalSwipe(this, value);
		}
	}

	private class AnimatedScaleRunnable implements Runnable
	{
		private static final int ZOOM_DURATION = 200;

		private final long mStartTime;
		private final Matrix mStartTransformMatrix;
		private final float mFocalX, mFocalY;
		private final float mScaleStart, mScaleEnd;

		public AnimatedScaleRunnable(float scale, float focalX, float focalY)
		{
			mFocalX = focalX;
			mFocalY = focalY;
			mStartTime = System.currentTimeMillis();
			mStartTransformMatrix = new Matrix(mTransformMatrix);
			mScaleStart = getScale();
			mScaleEnd = scale;
		}

		@Override
		public void run()
		{
			float t = (float) (System.currentTimeMillis() - mStartTime) / ZOOM_DURATION;
			boolean post = true;
			if (t >= 1f)
			{
				t = 1f;
				post = false;
			}
			t = AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation(t);
			float scale = AnimationUtils.lerp(mScaleStart, mScaleEnd, t);
			float deltaScale = scale / mScaleStart;
			mTransformMatrix.set(mStartTransformMatrix);
			mTransformMatrix.postScale(deltaScale, deltaScale, mFocalX, mFocalY);
			checkMatrixBoundsAndInvalidate();
			if (post) postOnAnimation(this);
		}
	}

	private class AnimatedRestoreSwipeRunnable implements Runnable
	{
		private static final int RESTORE_DURATION = 150;
		private static final int FINISH_DURATION_MAX = 500;
		private static final int FINISH_DURATION_MIN = RESTORE_DURATION;

		private final long mStartTime;
		private final float mDeltaY;
		private final boolean mFinish;
		private final int mDuration;

		public AnimatedRestoreSwipeRunnable(RectF rect, boolean finish, float velocity)
		{
			float height = rect.height();
			int viewHeight = getHeight();
			float deltaY = 0f;
			if (height <= viewHeight) deltaY = (viewHeight - height) / 2 - rect.top;
			else if (rect.top > 0) deltaY = -rect.top;
			else if (rect.bottom < viewHeight) deltaY = viewHeight - rect.bottom;
			if (finish)
			{
				// Fling image out of screen
				if (deltaY > 0f) deltaY = -rect.bottom; else deltaY = viewHeight - rect.top;
			}
			mStartTime = System.currentTimeMillis();
			mDeltaY = deltaY;
			mFinish = finish;
			if (finish)
			{
				int duration = (int) Math.abs(mDeltaY / velocity * 1000f);
				mDuration = Math.max(Math.min(duration, FINISH_DURATION_MAX), FINISH_DURATION_MIN);
			}
			else mDuration = RESTORE_DURATION;
		}

		private float mLastDeltaY = 0f;

		@Override
		public void run()
		{
			float t = (float) (System.currentTimeMillis() - mStartTime) / mDuration;
			boolean post = true;
			if (t >= 1f)
			{
				t = 1f;
				post = false;
			}
			if (!mFinish) t = AnimationUtils.DECELERATE_INTERPOLATOR.getInterpolation(t);
			float deltaY = AnimationUtils.lerp(0, mDeltaY, t);
			float dy = deltaY - mLastDeltaY;
			mLastDeltaY = deltaY;
			mTransformMatrix.postTranslate(0, dy);
			if (post)
			{
				invalidate();
				postOnAnimation(this);
				if (!mFinish) notifyVerticalSwipe(mDeltaY - deltaY);
			}
			else
			{
				checkMatrixBoundsAndInvalidate();
				if (!mFinish) notifyVerticalSwipe(0f);
			}
		}
	}

	private class FlingRunnable implements Runnable
	{
		private final Scroller mScroller;
		private int mCurrentX, mCurrentY;

		public FlingRunnable(Context context)
		{
			mScroller = new Scroller(context);
		}

		public void cancelFling()
		{
			mScroller.forceFinished(true);
		}

		public void fling(int viewWidth, int viewHeight, int velocityX, int velocityY)
		{
			RectF rect = checkMatrixBounds();
			if (rect == null) return;
			int startX = Math.round(-rect.left);
			int minX, maxX, minY, maxY;
			if (viewWidth < rect.width())
			{
				minX = 0;
				maxX = Math.round(rect.width() - viewWidth);
			}
			else minX = maxX = startX;
			int startY = Math.round(-rect.top);
			if (viewHeight < rect.height())
			{
				minY = 0;
				maxY = Math.round(rect.height() - viewHeight);
			}
			else minY = maxY = startY;
			mCurrentX = startX;
			mCurrentY = startY;
			if (startX != maxX || startY != maxY)
			{
				mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY);
			}
		}

		@Override
		public void run()
		{
			if (mScroller.isFinished()) return;
			if (mScroller.computeScrollOffset())
			{
				int newX = mScroller.getCurrX();
				int newY = mScroller.getCurrY();
				mTransformMatrix.postTranslate(mCurrentX - newX, mCurrentY - newY);
				invalidate();
				mCurrentX = newX;
				mCurrentY = newY;
				postOnAnimation(this);
			}
		}
	}

	public static final int INITIAL_SCALE_TRANSITION_TIME = 400;

	private class InitialScaleListener implements ValueAnimator.AnimatorUpdateListener
	{
		private final float mCenterX, mCenterY;
		private final int mViewWidth, mViewHeight;
		private final boolean mCropEnabled;

		private static final int WAIT_TIME = 100;
		private static final float TRANSFER_TIME_FACTOR = 1.5f;

		public InitialScaleListener(float centerX, float centerY, int viewWidth, int viewHeight, boolean cropEnabled)
		{
			mCenterX = centerX;
			mCenterY = centerY;
			mViewWidth = viewWidth;
			mViewHeight = viewHeight;
			mCropEnabled = cropEnabled;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation)
		{
			mBaseMatrix.getValues(mMatrixValues);
			float baseScale = mMatrixValues[Matrix.MSCALE_Y];
			Point dimensions = getDimensions();
			if (dimensions == null) return;
			float scale = (dimensions.x * mViewHeight > dimensions.y * mViewWidth) == mCropEnabled
					? (float) mViewHeight / dimensions.y : (float) mViewWidth / dimensions.x;
			scale /= baseScale;

			float t = (float) animation.getAnimatedValue();
			boolean finished = false;
			if (t >= 1f)
			{
				t = 1f;
				finished = true;
			}
			float wait = (float) WAIT_TIME / INITIAL_SCALE_TRANSITION_TIME;
			t = t >= wait ? AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR
					.getInterpolation((t - wait) / (1f - wait)) : 0f;

			if (!finished)
			{
				float ct = (float) Math.pow(t, TRANSFER_TIME_FACTOR); // Make XY transition faster
				float targetX = AnimationUtils.lerp(mCenterX, getWidth() / 2f, ct);
				float targetY = AnimationUtils.lerp(mCenterY, getHeight() / 2f, ct);
				float targetScale = AnimationUtils.lerp(scale, mInitialScale, t);
				float dx = -getWidth() / 2f + targetX;
				float dy = -getHeight() / 2f + targetY;
				mTransformMatrix.reset();
				mTransformMatrix.postTranslate(dx, dy);
				mTransformMatrix.postScale(targetScale, targetScale, targetX, targetY);
				if (mCropEnabled)
				{
					if (mInitialScaleClipRect == null) mInitialScaleClipRect = new Rect();
					int sizeXY = (int) AnimationUtils.lerp(Math.min(dimensions.x, dimensions.y),
							Math.max(dimensions.x, dimensions.y), t);
					float scaledHalfSize = targetScale * baseScale * sizeXY / 2f;
					mInitialScaleClipRect.set((int) (targetX - scaledHalfSize - 0.5f), (int) (targetY - scaledHalfSize
							- 0.5f), (int) (targetX + scaledHalfSize + 0.5f), (int) (targetY + scaledHalfSize + 0.5f));
				}
				invalidate();
			}
			else
			{
				mInitialScaleClipRect = null;
				setScale(mInitialScale);
			}
		}
	}

	private float getActiveX(MotionEvent event)
	{
		try
		{
			return event.getX(mActivePointerIndex);
		}
		catch (Exception e)
		{
			return event.getX();
		}
	}

	private float getActiveY(MotionEvent event)
	{
		try
		{
			return event.getY(mActivePointerIndex);
		}
		catch (Exception e)
		{
			return event.getY();
		}
	}

	private boolean onCommonTouchEvent(MotionEvent event)
	{
		switch (event.getActionMasked())
		{
			case MotionEvent.ACTION_DOWN:
			{
				mActivePointerId = event.getPointerId(0);
				break;
			}
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
			{
				mActivePointerId = INVALID_POINTER_ID;
				break;
			}
			case MotionEvent.ACTION_POINTER_UP:
			{
				int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >>
						MotionEvent.ACTION_POINTER_INDEX_SHIFT;
				int pointerId = event.getPointerId(pointerIndex);
				if (pointerId == mActivePointerId)
				{
					int newPointerIndex = pointerIndex == 0 ? 1 : 0;
					mActivePointerId = event.getPointerId(newPointerIndex);
					mLastTouchX = event.getX(newPointerIndex);
					mLastTouchY = event.getY(newPointerIndex);
				}
				break;
			}
			case MotionEvent.ACTION_POINTER_DOWN:
			{
				checkTouchMode();
				break;
			}
		}
		mActivePointerIndex = event.findPointerIndex(mActivePointerId != INVALID_POINTER_ID ? mActivePointerId : 0);
		switch (event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
			{
				mVelocityTracker = VelocityTracker.obtain();
				if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
				mLastTouchX = getActiveX(event);
				mLastTouchY = getActiveY(event);
				mIsDragging = false;
				break;
			}
			case MotionEvent.ACTION_MOVE:
			{
				float x = getActiveX(event);
				float y = getActiveY(event);
				float dx = x - mLastTouchX, dy = y - mLastTouchY;
				if (!mIsDragging) mIsDragging = Math.sqrt((dx * dx) + (dy * dy)) >= mTouchSlop;
				if (mIsDragging)
				{
					if (mTouchMode == TouchMode.UNDEFINED)
					{
						boolean allowClosing = mScrollEdgeY == ScrollEdge.BOTH ||
								mScrollEdgeY == ScrollEdge.START && dy > 0 || mScrollEdgeY == ScrollEdge.END && dy < 0;
						boolean closing = false;
						if (allowClosing)
						{
							float length = (float) Math.sqrt(dx * dx + dy * dy);
							float angle = (float) (Math.acos(Math.abs(dx / length)) * 180f / Math.PI);
							closing = angle >= 60;
						}
						if (closing)
						{
							switch (mScrollEdgeY)
							{
								case NONE: throw new RuntimeException();
								case START: mTouchMode = TouchMode.CLOSING_START; break;
								case END: mTouchMode = TouchMode.CLOSING_END; break;
								case BOTH: mTouchMode = TouchMode.CLOSING_BOTH; break;
							}
						}
						else mTouchMode = TouchMode.COMMON;
					}
					if (!mScaleGestureDetector.isInProgress())
					{
						if (isClosingTouchMode()) mTransformMatrix.postTranslate(0, dy * CLOSE_SWIPE_FACTOR);
						else mTransformMatrix.postTranslate(dx, dy);
						checkMatrixBoundsAndInvalidate();
					}
					mLastTouchX = x;
					mLastTouchY = y;
					if (mVelocityTracker != null) mVelocityTracker.addMovement(event);
				}
				break;
			}
			case MotionEvent.ACTION_CANCEL:
			{
				if (mVelocityTracker != null)
				{
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			{
				if (mIsDragging)
				{
					if (isClosingTouchMode())
					{
						RectF rect = checkMatrixBounds();
						if (rect != null)
						{
							int viewHeight = getHeight();
							float threshold = viewHeight * CLOSE_SWIPE_FACTOR / 2f;
							float shift = getClosingTouchModeShift(rect);
							float velocity = 0f;
							if (mVelocityTracker != null)
							{
								mVelocityTracker.addMovement(event);
								mVelocityTracker.computeCurrentVelocity(1000);
								velocity = mVelocityTracker.getYVelocity() * CLOSE_SWIPE_FACTOR;
								boolean increase = shift > 0 == velocity > 0;
								velocity = Math.abs(velocity);
								if (increase) threshold *= 1f - Math.min(velocity / 1000f, 0.9f);
								else threshold *= Math.max(velocity / 1000f, 1f);
							}
							boolean close = Math.abs(shift) >= threshold;
							if (mListener != null && close) close = mListener.onClose(this);
							if (mAnimatedRestoreSwipeRunnable != null)
							{
								removeCallbacks(mAnimatedRestoreSwipeRunnable);
								mAnimatedRestoreSwipeRunnable = null;
							}
							mAnimatedRestoreSwipeRunnable = new AnimatedRestoreSwipeRunnable(rect, close, velocity);
							post(mAnimatedRestoreSwipeRunnable);
						}
					}
					else if (mVelocityTracker != null)
					{
						mLastTouchX = getActiveX(event);
						mLastTouchY = getActiveY(event);
						mVelocityTracker.addMovement(event);
						mVelocityTracker.computeCurrentVelocity(1000);
						float vX = mVelocityTracker.getXVelocity();
						float vY = mVelocityTracker.getYVelocity();
						if (Math.max(Math.abs(vX), Math.abs(vY)) >= mMinimumVelocity)
						{
							mFlingRunnable = new FlingRunnable(getContext());
							mFlingRunnable.fling(getWidth(), getHeight(), (int) -vX, (int) -vY);
							post(mFlingRunnable);
						}
					}
				}
				if (mVelocityTracker != null)
				{
					mVelocityTracker.recycle();
					mVelocityTracker = null;
				}
				break;
			}
		}
		return true;
	}
}