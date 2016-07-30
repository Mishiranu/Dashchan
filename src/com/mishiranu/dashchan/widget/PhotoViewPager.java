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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EdgeEffect;
import android.widget.OverScroller;

import com.mishiranu.dashchan.util.ResourceUtils;

public class PhotoViewPager extends ViewGroup
{
	private static final int MAX_SETTLE_DURATION = 600;
	private static final int MIN_FLING_VELOCITY = 400;

	private final int mFlingDistance;
	private final int mMinimumVelocity;
	private final int mMaximumVelocity;
	private final int mTouchSlop;
	
	private final OverScroller mScroller;
	private final EdgeEffect mEdgeEffect;
	
	private final Adapter mAdapter;
	private final ArrayList<PhotoView> mPhotoViews = new ArrayList<>(3);
	
	private int mInnerPadding;
	
	public PhotoViewPager(Context context, Adapter adapter)
	{
		super(context);
		setWillNotDraw(false);
		float density = ResourceUtils.obtainDensity(context);
		mFlingDistance = (int) (24 * density);
		ViewConfiguration configuration = ViewConfiguration.get(context);
		mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
		mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
		mTouchSlop = configuration.getScaledTouchSlop();
		mScroller = new OverScroller(context);
		mEdgeEffect = new EdgeEffect(context);
		mAdapter = adapter;
		for (int i = 0; i < 3; i++)
		{
			View view = adapter.onCreateView(this);
			super.addView(view, -1, generateDefaultLayoutParams());
			mPhotoViews.add(adapter.getPhotoView(view));
		}
	}
	
	public interface Adapter
	{
		public View onCreateView(ViewGroup parent);
		public PhotoView getPhotoView(View view);
		public void onPositionChange(PhotoViewPager view, int index, View currentView, View leftView, View rightView,
				boolean manually);
		public void onSwipingStateChange(PhotoViewPager view, boolean swiping);
	}
	
	@Override
	protected LayoutParams generateDefaultLayoutParams()
	{
		return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
	}
	
	@Override
	public void addView(View child, int index, LayoutParams params)
	{
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec),
				MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),
				MeasureSpec.EXACTLY);
		for (int i = 0; i < getChildCount(); i++)
		{
			getChildAt(i).measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		int width = r - l;
		int height = b - t;
		int start = mCurrentIndex * (width + mInnerPadding);
		scrollTo(start, 0);
		int current = (int) ((start + width / 2f) / (width + mInnerPadding));
		int left = (current - 1) * (width + mInnerPadding);
		for (int i = current - 1; i < current + 2; i++)
		{
			getChildAt((i + 3) % 3).layout(left, 0, left + width, height);
			left += width + mInnerPadding;
		}
	}
	
	public void setInnerPadding(int padding)
	{
		mInnerPadding = padding;
		requestLayout();
	}
	
	public void setCount(int count)
	{
		if (count > 0)
		{
			mCount = count;
			if (mCurrentIndex >= count) mCurrentIndex = count - 1;
			requestLayout();
		}
	}
	
	public int getCount()
	{
		return mCount;
	}
	
	public void setCurrentIndex(int index)
	{
		if (index >= 0 && index < mCount)
		{
			mCurrentIndex = index;
			updateCurrentScrollIndex(true);
		}
	}
	
	public int getCurrentIndex()
	{
		return mCurrentIndex;
	}
	
	public View getCurrentView()
	{
		return getChildAt(mCurrentIndex % 3);
	}
	
	private void updateCurrentScrollIndex(boolean manually)
	{
		mQueueScrollFinish = false;
		mScroller.abortAnimation();
		onScrollFinish(manually);
	}
	
	private void onScrollFinish(boolean manually)
	{
		requestLayout();
		notifySwiping(false);
		if (mPreviousIndex != mCurrentIndex || manually)
		{
			int currentIndex = mCurrentIndex % 3;
			int leftIndex = (mCurrentIndex + 2) % 3;
			int rightIndex = (mCurrentIndex + 1) % 3;
			boolean hasLeft = mCurrentIndex > 0;
			boolean hasRight = mCurrentIndex < mCount - 1;
			View currentView = getChildAt(currentIndex);
			View leftView = hasLeft ? getChildAt(leftIndex) : null;
			View rightView = hasRight ? getChildAt(rightIndex) : null;
			mAdapter.onPositionChange(this, mCurrentIndex, currentView, leftView, rightView, manually);
			mPreviousIndex = mCurrentIndex;
		}
	}
	
	private int mCount = 1;
	private int mCurrentIndex = 0;
	private int mPreviousIndex = -1;
	
	private boolean mAllowMove;
	private boolean mLastEventToPhotoView;
	
	private float mStartX;
	private float mStartY;
	private float mLastX;
	private int mStartScrollX;
	
	private VelocityTracker mVelocityTracker;
	
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev)
	{
		return true;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		PhotoView photoView = mPhotoViews.get(mCurrentIndex % 3);
		int action = event.getActionMasked();
		switch (action)
		{
			case MotionEvent.ACTION_DOWN:
			{
				photoView.dispatchSpecialTouchEvent(event);
				mAllowMove = false;
				mLastEventToPhotoView = true;
				updateCurrentScrollIndex(false);
				mStartX = mLastX = event.getX();
				mStartY = event.getY();
				mStartScrollX = getScrollX();
				mVelocityTracker = VelocityTracker.obtain();
				mVelocityTracker.addMovement(event);
				return true;
			}
			case MotionEvent.ACTION_MOVE:
			{
				float x = event.getX();
				float y = event.getY();
				float previousX = x;
				float previousY = y;
				boolean previousValid = false;
				boolean singlePointer = event.getPointerCount() == 1;
				if (event.getHistorySize() > 0)
				{
					previousX = event.getHistoricalX(0);
					previousY = event.getHistoricalY(0);
					// Don't track repeating movements
					if (previousX == x && previousY == y && singlePointer) return true;
					previousValid = true;
				}
				if (!mAllowMove)
				{
					if (Math.abs(event.getX() - mStartX) <= mTouchSlop &&
							Math.abs(event.getY() - mStartY) <= mTouchSlop)
					{
						// Too short movement, skip it
						return true;
					}
					mAllowMove = true;
				}
				boolean sendToPhotoView = mCount == 1 || photoView.isScaling();
				int currentScrollX = getScrollX();
				// Scrolling right means scroll to right view (so finger moves left)
				boolean scrollingRight = x < mLastX;
				boolean canScrollLeft = photoView.canScrollLeft();
				boolean canScrollRight = photoView.canScrollRight();
				boolean canScrollPhotoView = canScrollLeft && !scrollingRight || canScrollRight && scrollingRight;
				boolean canScalePhotoView = !singlePointer;
				if (mStartScrollX == currentScrollX)
				{
					if (!sendToPhotoView)
					{
						if (canScrollPhotoView || canScalePhotoView) sendToPhotoView = true; else if (previousValid)
						{
							float vX = x - previousX;
							float vY = y - previousY;
							float length = (float) Math.sqrt(vX * vX + vY * vY);
							float angle = (float) (Math.acos(Math.abs(vX / length)) * 180f / Math.PI);
							// angle >= 30 means vertical movement (angle is from 0 to 90)
							if (angle >= 30f) sendToPhotoView = true;
						}
					}
				}
				if (sendToPhotoView)
				{
					mStartX += x - mLastX;
					photoView.dispatchSpecialTouchEvent(event);
					notifySwiping(false);
				}
				else
				{
					int width = getWidth();
					int deltaX = (int) (mStartX - x);
					int desiredScroll = mStartScrollX + deltaX;
					int actualScroll = Math.max(0, Math.min((mCount - 1) * (width + mInnerPadding), desiredScroll));
					boolean canFocusPhotoView = currentScrollX < mStartScrollX && actualScroll >= mStartScrollX ||
							currentScrollX > mStartScrollX && actualScroll <= mStartScrollX;
					if (canFocusPhotoView && canScrollPhotoView)
					{
						// Fix scrolling to make PhotoView fill PhotoViewPager
						// to ensure sendToPhotoView = true on next touch event
						mStartX -= actualScroll - mStartScrollX;
						actualScroll = mStartScrollX;
					}
					if (desiredScroll > actualScroll)
					{
						if (!scrollingRight) mStartX = x;
						mEdgeEffect.onPull((float) (desiredScroll - actualScroll) / width);
						invalidate();
					}
					else if (desiredScroll < actualScroll)
					{
						if (scrollingRight) mStartX = x;
						mEdgeEffect.onPull((float) (actualScroll - desiredScroll) / width);
						invalidate();
					}
					else notifySwiping(true);
					scrollTo(actualScroll, 0);
					mVelocityTracker.addMovement(event);
					photoView.dispatchIdleMoveTouchEvent(event);
				}
				mLastEventToPhotoView = sendToPhotoView;
				mLastX = x;
				return true;
			}
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
			{
				if (mLastEventToPhotoView || action == MotionEvent.ACTION_CANCEL)
				{
					photoView.dispatchSpecialTouchEvent(event);
				}
				else
				{
					MotionEvent fakeEvent = MotionEvent.obtain(event);
					fakeEvent.setAction(MotionEvent.ACTION_CANCEL);
					photoView.dispatchSpecialTouchEvent(fakeEvent);
					fakeEvent.recycle();
				}
				int index = mCurrentIndex;
				int velocity = 0;
				if (action == MotionEvent.ACTION_UP)
				{
					int deltaX = (int) (mStartX - event.getX());
					mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    velocity = (int) mVelocityTracker.getXVelocity(0);
					index = determineTargetIndex(velocity, deltaX);
					if (!mAllowMove) photoView.dispatchSimpleClick(event.getX(), event.getY());
				}
				mVelocityTracker.recycle();
				mVelocityTracker = null;
				smoothScrollTo(index, velocity);
				mCurrentIndex = index;
				mEdgeEffect.onRelease();
				return true;
			}
			case MotionEvent.ACTION_POINTER_UP:
			{
				// Replace active pointer
				if (event.getActionIndex() == 0)
				{
					float deltaX = event.getX(1) - event.getX(0);
					float deltaY = event.getY(1) - event.getY(0);
					mStartX += deltaX;
					mStartY += deltaY;
					mLastX += deltaX;
				}
			}
			default:
			{
				photoView.dispatchSpecialTouchEvent(event);
				return true;
			}
		}
	}
	
	@Override
	public void draw(Canvas canvas)
	{
		super.draw(canvas);
		if (!mEdgeEffect.isFinished() && (mCurrentIndex == 0 || mCurrentIndex == mCount - 1))
		{
			int width = getWidth();
			int height = getHeight();
			canvas.save();
			if (mCurrentIndex == 0)
			{
				canvas.rotate(270f);
				canvas.translate(-height, 0f);
			}
			else
			{
				canvas.rotate(90f);
				canvas.translate(0f, -((mCount - 1) * (width + mInnerPadding) + width));
			}
			mEdgeEffect.setSize(height, width);
			boolean invalidate = mEdgeEffect.draw(canvas);
			canvas.restore();
			if (invalidate) invalidate();
		}
	}
	
	private int determineTargetIndex(int velocity, int deltaX)
	{
		int index = mCurrentIndex;
		int targetIndex;
		if (Math.abs(deltaX) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity)
		{
			// First condition to ensure not scrolling through 2 pages (from 4.8 to 3, for example)
			targetIndex = deltaX * velocity > 0 ? index : velocity > 0 ? index - 1 : index + 1;
		}
		else
		{
			targetIndex = (int) (index + (float) deltaX / getWidth() + 0.5f);
		}
		return Math.max(0, Math.min(mCount - 1, targetIndex));
	}
	
	private void smoothScrollTo(int index, int velocity)
	{
		int startX = getScrollX();
		int endX = index * (getWidth() + mInnerPadding);
		int deltaX = endX - startX;
		if (startX != endX)
		{
			int duration;
			if (Math.abs(velocity) > mMinimumVelocity)
			{
				duration = 4 * Math.round(1000 * Math.abs((float) deltaX / velocity));
			}
			else
			{
				float pageDelta = (float) Math.abs(deltaX) / getWidth();
				duration = (int) ((pageDelta + 1) * 200);
			}
			duration = Math.min(duration, MAX_SETTLE_DURATION);
			mQueueScrollFinish = true;
			notifySwiping(true);
			mScroller.startScroll(startX, 0, deltaX, 0, duration);
			invalidate();
		}
		else onScrollFinish(false);
	}
	
	private boolean mSwiping = false;
	
	private void notifySwiping(boolean swiping)
	{
		if (mSwiping != swiping)
		{
			mSwiping = swiping;
			post(swiping ? mSwipingStateRunnableTrue : mSwipingStateRunnableFalse);
		}
	}
	
	private final Runnable mSwipingStateRunnableTrue = new SwipingStateRunnable(true);
	private final Runnable mSwipingStateRunnableFalse = new SwipingStateRunnable(false);
	
	private class SwipingStateRunnable implements Runnable
	{
		private final boolean mSwiping;
		
		public SwipingStateRunnable(boolean swiping)
		{
			mSwiping = swiping;
		}
		
		@Override
		public void run()
		{
			mAdapter.onSwipingStateChange(PhotoViewPager.this, mSwiping);
		}
	}
	
	private boolean mQueueScrollFinish = false;
	
	@Override
	public void computeScroll()
	{
		if (mScroller.isFinished())
		{
			if (mQueueScrollFinish)
			{
				mQueueScrollFinish = false;
				onScrollFinish(false);
			}
		}
		else if (mScroller.computeScrollOffset())
		{
			scrollTo(mScroller.getCurrX(), 0);
			invalidate();
		}
	}
}