package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
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
import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class PhotoViewPager extends ViewGroup {
	private static final int MAX_SETTLE_DURATION = 600;
	private static final int MIN_FLING_VELOCITY = 400;

	private final int flingDistance;
	private final int minimumVelocity;
	private final int maximumVelocity;
	private final int touchSlop;

	private final OverScroller scroller;
	private final EdgeEffect edgeEffect;

	private final Adapter adapter;
	private final ArrayList<PhotoView> photoViews = new ArrayList<>(3);

	private boolean active = true;
	private int innerPadding;

	public PhotoViewPager(Context context, Adapter adapter) {
		super(context);
		setWillNotDraw(false);
		float density = ResourceUtils.obtainDensity(context);
		flingDistance = (int) (24 * density);
		ViewConfiguration configuration = ViewConfiguration.get(context);
		minimumVelocity = (int) (MIN_FLING_VELOCITY * density);
		maximumVelocity = configuration.getScaledMaximumFlingVelocity();
		touchSlop = configuration.getScaledTouchSlop();
		scroller = new OverScroller(context);
		edgeEffect = new EdgeEffect(context);
		this.adapter = adapter;
		for (int i = 0; i < 3; i++) {
			View view = adapter.onCreateView(this);
			super.addView(view, -1, generateDefaultLayoutParams());
			photoViews.add(adapter.getPhotoView(view));
		}
	}

	public interface Adapter {
		View onCreateView(ViewGroup parent);
		PhotoView getPhotoView(View view);
		void onPositionChange(PhotoViewPager view, int index, View centerView, View leftView, View rightView,
				boolean manually);
		void onSwipingStateChange(PhotoViewPager view, boolean swiping);
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
	}

	@Override
	public void addView(View child, int index, LayoutParams params) {
		throw new UnsupportedOperationException();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec),
				MeasureSpec.EXACTLY);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec),
				MeasureSpec.EXACTLY);
		for (int i = 0; i < getChildCount(); i++) {
			getChildAt(i).measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int width = r - l;
		int height = b - t;
		int start = currentIndex * (width + innerPadding);
		scrollTo(start, 0);
		int current = (int) ((start + width / 2f) / (width + innerPadding));
		int left = (current - 1) * (width + innerPadding);
		for (int i = current - 1; i < current + 2; i++) {
			getChildAt((i + 3) % 3).layout(left, 0, left + width, height);
			left += width + innerPadding;
		}
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void setInnerPadding(int padding) {
		innerPadding = padding;
		requestLayout();
	}

	public void setCount(int count) {
		if (count > 0) {
			this.count = count;
			if (currentIndex >= count) {
				currentIndex = count - 1;
			}
			requestLayout();
		}
	}

	public int getCount() {
		return count;
	}

	public void setCurrentIndex(int index) {
		if (index >= 0 && index < count) {
			currentIndex = index;
			updateCurrentScrollIndex(true);
		}
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public View getCurrentView() {
		return getChildAt(currentIndex % 3);
	}

	private void updateCurrentScrollIndex(boolean manually) {
		queueScrollFinish = false;
		scroller.abortAnimation();
		onScrollFinish(manually);
	}

	private void onScrollFinish(boolean manually) {
		requestLayout();
		notifySwiping(false);
		if (previousIndex != currentIndex || manually) {
			int centerIndex = currentIndex % 3;
			int leftIndex = (currentIndex + 2) % 3;
			int rightIndex = (currentIndex + 1) % 3;
			boolean hasLeft = currentIndex > 0;
			boolean hasRight = currentIndex < count - 1;
			View centerView = getChildAt(centerIndex);
			View leftView = hasLeft ? getChildAt(leftIndex) : null;
			View rightView = hasRight ? getChildAt(rightIndex) : null;
			adapter.onPositionChange(this, currentIndex, centerView, leftView, rightView, manually);
			previousIndex = currentIndex;
		}
	}

	private int count = 1;
	private int currentIndex = 0;
	private int previousIndex = -1;

	private boolean allowMove;
	private boolean lastEventToPhotoView;

	private float startX;
	private float startY;
	private float lastX;
	private int startScrollX;
	private boolean longTapConfirmed;

	private VelocityTracker velocityTracker;

	private final Runnable longTapRunnable = () -> {
		longTapConfirmed = true;
		PhotoView photoView = photoViews.get(currentIndex % 3);
		photoView.dispatchSimpleClick(true, startX, startY);
	};

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return true;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!active) {
			return false;
		}
		PhotoView photoView = photoViews.get(currentIndex % 3);
		int action = event.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN: {
				photoView.dispatchSpecialTouchEvent(event);
				allowMove = false;
				lastEventToPhotoView = true;
				updateCurrentScrollIndex(false);
				startX = lastX = event.getX();
				startY = event.getY();
				startScrollX = getScrollX();
				longTapConfirmed = false;
				velocityTracker = VelocityTracker.obtain();
				velocityTracker.addMovement(event);
				postDelayed(longTapRunnable, ViewConfiguration.getDoubleTapTimeout());
				return true;
			}
			case MotionEvent.ACTION_MOVE: {
				float x = event.getX();
				float y = event.getY();
				float previousX = x;
				float previousY = y;
				boolean previousValid = false;
				boolean singlePointer = event.getPointerCount() == 1;
				if (event.getHistorySize() > 0) {
					previousX = event.getHistoricalX(0);
					previousY = event.getHistoricalY(0);
					// Don't track repeating movements
					if (previousX == x && previousY == y && singlePointer) {
						return true;
					}
					previousValid = true;
				}
				if (!allowMove) {
					if (Math.abs(event.getX() - startX) <= touchSlop &&
							Math.abs(event.getY() - startY) <= touchSlop) {
						// Too short movement, skip it
						return true;
					}
					removeCallbacks(longTapRunnable);
					allowMove = true;
				}
				boolean sendToPhotoView = count == 1 || photoView.isScaling();
				int currentScrollX = getScrollX();
				// Scrolling right means scroll to right view (so finger moves left)
				boolean scrollingRight = x < lastX;
				boolean canScrollLeft = photoView.canScrollLeft();
				boolean canScrollRight = photoView.canScrollRight();
				boolean canScrollPhotoView = canScrollLeft && !scrollingRight || canScrollRight && scrollingRight;
				boolean canScalePhotoView = !singlePointer;
				if (startScrollX == currentScrollX) {
					if (!sendToPhotoView) {
						if (canScrollPhotoView || canScalePhotoView) {
							sendToPhotoView = true;
						} else if (previousValid) {
							float vX = x - previousX;
							float vY = y - previousY;
							float length = (float) Math.sqrt(vX * vX + vY * vY);
							float angle = (float) (Math.acos(Math.abs(vX / length)) * 180f / Math.PI);
							// angle >= 30 means vertical movement (angle is from 0 to 90)
							if (angle >= 30f) {
								sendToPhotoView = true;
							}
						}
					}
				}
				if (sendToPhotoView) {
					startX += x - lastX;
					photoView.dispatchSpecialTouchEvent(event);
					notifySwiping(false);
				} else {
					int width = getWidth();
					int deltaX = (int) (startX - x);
					int desiredScroll = startScrollX + deltaX;
					int actualScroll = Math.max(0, Math.min((count - 1) * (width + innerPadding), desiredScroll));
					boolean canFocusPhotoView = currentScrollX < startScrollX && actualScroll >= startScrollX ||
							currentScrollX > startScrollX && actualScroll <= startScrollX;
					if (canFocusPhotoView && canScrollPhotoView) {
						// Fix scrolling to make PhotoView fill PhotoViewPager
						// to ensure sendToPhotoView = true on next touch event
						startX -= actualScroll - startScrollX;
						actualScroll = startScrollX;
					}
					if (desiredScroll > actualScroll) {
						if (!scrollingRight) {
							startX = x;
						}
						edgeEffect.onPull((float) (desiredScroll - actualScroll) / width);
						invalidate();
					} else if (desiredScroll < actualScroll) {
						if (scrollingRight) {
							startX = x;
						}
						edgeEffect.onPull((float) (actualScroll - desiredScroll) / width);
						invalidate();
					} else {
						notifySwiping(true);
					}
					scrollTo(actualScroll, 0);
					velocityTracker.addMovement(event);
					photoView.dispatchIdleMoveTouchEvent(event);
				}
				lastEventToPhotoView = sendToPhotoView;
				lastX = x;
				return true;
			}
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP: {
				if (lastEventToPhotoView || action == MotionEvent.ACTION_CANCEL) {
					photoView.dispatchSpecialTouchEvent(event);
				} else {
					MotionEvent fakeEvent = MotionEvent.obtain(event);
					fakeEvent.setAction(MotionEvent.ACTION_CANCEL);
					photoView.dispatchSpecialTouchEvent(fakeEvent);
					fakeEvent.recycle();
				}
				int index = currentIndex;
				int velocity = 0;
				if (action == MotionEvent.ACTION_UP) {
					int deltaX = (int) (startX - event.getX());
					velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
					velocity = (int) velocityTracker.getXVelocity(0);
					index = determineTargetIndex(velocity, deltaX);
					if (!allowMove && !longTapConfirmed) {
						photoView.dispatchSimpleClick(false, event.getX(), event.getY());
					}
				}
				velocityTracker.recycle();
				velocityTracker = null;
				removeCallbacks(longTapRunnable);
				smoothScrollTo(index, velocity);
				currentIndex = index;
				edgeEffect.onRelease();
				return true;
			}
			case MotionEvent.ACTION_POINTER_UP: {
				// Replace active pointer
				if (event.getActionIndex() == 0) {
					float deltaX = event.getX(1) - event.getX(0);
					float deltaY = event.getY(1) - event.getY(0);
					startX += deltaX;
					startY += deltaY;
					lastX += deltaX;
				}
			}
			default: {
				photoView.dispatchSpecialTouchEvent(event);
				return true;
			}
		}
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (!edgeEffect.isFinished() && (currentIndex == 0 || currentIndex == count - 1)) {
			int width = getWidth();
			int height = getHeight();
			canvas.save();
			if (currentIndex == 0) {
				canvas.rotate(270f);
				canvas.translate(-height, 0f);
			} else {
				canvas.rotate(90f);
				canvas.translate(0f, -((count - 1) * (width + innerPadding) + width));
			}
			edgeEffect.setSize(height, width);
			boolean invalidate = edgeEffect.draw(canvas);
			canvas.restore();
			if (invalidate) {
				invalidate();
			}
		}
	}

	private int determineTargetIndex(int velocity, int deltaX) {
		int index = currentIndex;
		int targetIndex;
		if (Math.abs(deltaX) > flingDistance && Math.abs(velocity) > minimumVelocity) {
			// First condition to ensure not scrolling through 2 pages (from 4.8 to 3, for example)
			targetIndex = deltaX * velocity > 0 ? index : velocity > 0 ? index - 1 : index + 1;
		} else {
			targetIndex = (int) (index + (float) deltaX / getWidth() + 0.5f);
		}
		return Math.max(0, Math.min(count - 1, targetIndex));
	}

	private void smoothScrollTo(int index, int velocity) {
		int startX = getScrollX();
		int endX = index * (getWidth() + innerPadding);
		int deltaX = endX - startX;
		if (startX != endX) {
			int duration;
			if (Math.abs(velocity) > minimumVelocity) {
				duration = 4 * Math.round(1000 * Math.abs((float) deltaX / velocity));
			} else {
				float pageDelta = (float) Math.abs(deltaX) / getWidth();
				duration = (int) ((pageDelta + 1) * 200);
			}
			duration = Math.min(duration, MAX_SETTLE_DURATION);
			queueScrollFinish = true;
			notifySwiping(true);
			scroller.startScroll(startX, 0, deltaX, 0, duration);
			invalidate();
		} else {
			onScrollFinish(false);
		}
	}

	private boolean swiping = false;

	private void notifySwiping(boolean swiping) {
		if (this.swiping != swiping) {
			this.swiping = swiping;
			post(swiping ? swipingStateRunnableTrue : swipingStateRunnableFalse);
		}
	}

	private final Runnable swipingStateRunnableTrue = new SwipingStateRunnable(true);
	private final Runnable swipingStateRunnableFalse = new SwipingStateRunnable(false);

	private class SwipingStateRunnable implements Runnable {
		private final boolean swiping;

		public SwipingStateRunnable(boolean swiping) {
			this.swiping = swiping;
		}

		@Override
		public void run() {
			adapter.onSwipingStateChange(PhotoViewPager.this, swiping);
		}
	}

	private boolean queueScrollFinish = false;

	@Override
	public void computeScroll() {
		if (scroller.isFinished()) {
			if (queueScrollFinish) {
				queueScrollFinish = false;
				onScrollFinish(false);
			}
		} else if (scroller.computeScrollOffset()) {
			scrollTo(scroller.getCurrX(), 0);
			invalidate();
		}
	}
}
