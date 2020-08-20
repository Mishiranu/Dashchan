package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class PaddedRecyclerView extends RecyclerView implements EdgeEffectHandler.Shift {
	private static final long FAST_SCROLLER_TRANSITION_IN = 100;
	private static final long FAST_SCROLLER_TRANSITION_OUT = 200;
	private static final long FAST_SCROLLER_TRANSITION_OUT_DELAY = 1000;

	private final EdgeEffectHandler edgeEffectHandler = EdgeEffectHandler.bind(this, this);
	private EdgeEffectHandler.Shift shift;

	private final Drawable thumbDrawable;
	private final Drawable trackDrawable;
	private final int minTrackSize;

	private boolean allowFastScrolling;
	private boolean regularScrolling;
	private boolean fastScrolling;
	private Float fastScrollingStartOffset;
	private float fastScrollingStartY;
	private float fastScrollingCurrentY;
	private long showFastScrollingStart;
	private boolean showFastScrolling;
	private boolean fastScrollerEnabled;
	private boolean disallowFastScrollerIntercept;

	public PaddedRecyclerView(@NonNull Context context) {
		super(context);
	}

	public PaddedRecyclerView(@NonNull Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PaddedRecyclerView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	/* init */ {
		float density = ResourceUtils.obtainDensity(this);
		Drawable thumbDrawable = ResourceUtils.getDrawable(getContext(), android.R.attr.fastScrollThumbDrawable, 0);
		this.thumbDrawable = C.API_LOLLIPOP ? thumbDrawable : ListViewUtils
				.colorizeListThumbDrawable4(getContext(), thumbDrawable);
		trackDrawable = ResourceUtils.getDrawable(getContext(), android.R.attr.fastScrollTrackDrawable, 0);
		minTrackSize = (int) (16f * density);

		addOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				updateFastScroller(allowFastScrolling, newState != RecyclerView.SCROLL_STATE_IDLE, fastScrolling);
			}
		});
		addOnItemTouchListener(new OnItemTouchListener() {
			@Override
			public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				return handleTouchEvent(e, true);
			}

			@Override
			public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				handleTouchEvent(e, false);
			}

			@Override
			public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
				disallowFastScrollerIntercept = disallowIntercept;
				if (disallowIntercept && fastScrolling) {
					updateFastScroller(allowFastScrolling, regularScrolling, false);
					invalidate();
				}
			}
		});
		addItemDecoration(new ItemDecoration() {
			@Override
			public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull State state) {
				onDrawFastScroller(c);
			}
		});
	}

	public void setFastScrollerEnabled(boolean fastScrollerEnabled) {
		if (this.fastScrollerEnabled != fastScrollerEnabled) {
			this.fastScrollerEnabled = fastScrollerEnabled;
			requestLayout();
		}
	}

	@SuppressWarnings({"unused", "RedundantSuppression"}) // Overrides hidden Android API protected method
	protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b) {
		if (!allowFastScrolling) {
			if (b - t == getHeight()) {
				t += getEdgeEffectShift(EdgeEffectHandler.Side.TOP);
				b -= getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
			}
			scrollBar.setBounds(l, t, r, b);
			scrollBar.draw(canvas);
		}
	}

	public void setEdgeEffectShift(EdgeEffectHandler.Shift shift) {
		this.shift = shift;
	}

	public EdgeEffectHandler getEdgeEffectHandler() {
		return edgeEffectHandler;
	}

	@Override
	public int getEdgeEffectShift(EdgeEffectHandler.Side side) {
		return shift != null ? shift.getEdgeEffectShift(side) : obtainEdgeEffectShift(side);
	}

	public final int obtainEdgeEffectShift(EdgeEffectHandler.Side side) {
		return side == EdgeEffectHandler.Side.TOP ? getPaddingTop() : getPaddingBottom();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);

		Adapter<?> adapter = getAdapter();
		boolean allowFastScrolling = fastScrollerEnabled && adapter != null && adapter.getItemCount() >= 15;
		updateFastScroller(allowFastScrolling, regularScrolling, fastScrolling);

		// OVER_SCROLL_IF_CONTENT_SCROLLS it not supported, see https://issuetracker.google.com/issues/37076456
		boolean hasOverScroll = computeVerticalScrollRange() > computeVerticalScrollExtent();
		setOverScrollMode(hasOverScroll ? OVER_SCROLL_ALWAYS : OVER_SCROLL_NEVER);
	}

	private final Runnable invalidateRunnable = this::invalidate;

	private void updateFastScroller(boolean allowFastScrolling, boolean regularScrolling, boolean fastScrolling) {
		boolean oldShow = this.allowFastScrolling && (this.regularScrolling || this.fastScrolling);
		boolean newShow = allowFastScrolling && (regularScrolling || fastScrolling);
		this.regularScrolling = regularScrolling;
		this.fastScrolling = fastScrolling;
		this.allowFastScrolling = allowFastScrolling;
		long time = SystemClock.elapsedRealtime();
		long passed = time - showFastScrollingStart;
		if (oldShow != newShow) {
			removeCallbacks(invalidateRunnable);
			long start;
			if (newShow && passed < FAST_SCROLLER_TRANSITION_OUT + FAST_SCROLLER_TRANSITION_OUT_DELAY) {
				start = passed <= FAST_SCROLLER_TRANSITION_OUT_DELAY ? 0L :
						time - (long) ((float) (FAST_SCROLLER_TRANSITION_OUT_DELAY +
								FAST_SCROLLER_TRANSITION_OUT - passed) /
								FAST_SCROLLER_TRANSITION_OUT * FAST_SCROLLER_TRANSITION_IN);
			} else if (!newShow && passed < FAST_SCROLLER_TRANSITION_IN) {
				start = time - (long) ((float) (FAST_SCROLLER_TRANSITION_IN - passed) /
						FAST_SCROLLER_TRANSITION_IN * FAST_SCROLLER_TRANSITION_OUT) -
						FAST_SCROLLER_TRANSITION_OUT_DELAY;
			} else {
				if (!newShow) {
					postDelayed(invalidateRunnable, FAST_SCROLLER_TRANSITION_OUT_DELAY);
				}
				start = time;
			}
			showFastScrollingStart = start;
			showFastScrolling = newShow;
			invalidate();
		} else if (!allowFastScrolling && passed < FAST_SCROLLER_TRANSITION_OUT_DELAY) {
			removeCallbacks(invalidateRunnable);
			showFastScrollingStart = time - FAST_SCROLLER_TRANSITION_OUT_DELAY;
			invalidate();
		}
	}

	private float calculateOffset() {
		float result;
		int height = getHeight() - getEdgeEffectShift(EdgeEffectHandler.Side.TOP) -
				getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
		if (fastScrollingStartOffset != null) {
			result = fastScrollingStartOffset + (fastScrollingCurrentY - fastScrollingStartY) /
					(height - thumbDrawable.getIntrinsicHeight());
		} else {
			result = (fastScrollingCurrentY - thumbDrawable.getIntrinsicHeight() / 2f) /
					(height - thumbDrawable.getIntrinsicHeight());
		}
		return Math.max(0, Math.min(result, 1));
	}

	private float getCurrentOffset() {
		int offset = computeVerticalScrollOffset();
		int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
		return Math.max(0, Math.min(range > 0 ? (float) offset / range : 0f, 1));
	}

	private void scroll(float offset) {
		Adapter<?> adapter = getAdapter();
		int count = adapter != null ? adapter.getItemCount() : 0;
		if (count > 0) {
			if (offset < 1f) {
				LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
				int first = layoutManager.findFirstCompletelyVisibleItemPosition();
				int last = layoutManager.findLastCompletelyVisibleItemPosition();
				int childCount;
				if (first >= 0 && last >= first) {
					childCount = last - first + 1;
				} else {
					childCount = getChildCount();
				}
				int position = (int) ((count - childCount) * offset + 0.5f);
				layoutManager.scrollToPositionWithOffset(position, 0);
			} else {
				scrollToPosition(count - 1);
			}
		}
	}

	private boolean handleTouchEvent(MotionEvent event, boolean intercept) {
		if (intercept && disallowFastScrollerIntercept) {
			return false;
		}
		int top = getEdgeEffectShift(EdgeEffectHandler.Side.TOP);
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (!allowFastScrolling) {
				return false;
			}
			boolean rtl = C.API_JELLY_BEAN_MR1 && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
			int trackWidth = Math.max(minTrackSize, Math.max(thumbDrawable.getIntrinsicWidth(),
					trackDrawable.getIntrinsicWidth()));
			boolean atThumbVertical = rtl ? event.getX() <= trackWidth : event.getX() >= getWidth() - trackWidth;
			if (atThumbVertical) {
				requestDisallowInterceptTouchEvent(true);
				int height = getHeight() - top - getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
				float offset = getCurrentOffset();
				int thumbHeight = thumbDrawable.getIntrinsicHeight();
				int thumbY = (int) ((height - thumbHeight) * offset);
				boolean atThumb = event.getY() >= top + thumbY && event.getY() <= top + thumbY + thumbHeight;
				fastScrollingStartOffset = atThumb ? offset : null;
				fastScrollingStartY = event.getY() - top;
				fastScrollingCurrentY = event.getY() - top;
				scroll(offset);
				updateFastScroller(allowFastScrolling, regularScrolling, true);
				invalidate();
				return true;
			}
		} else if (fastScrolling) {
			boolean cancel = !allowFastScrolling || event.getAction() == MotionEvent.ACTION_UP ||
					event.getAction() == MotionEvent.ACTION_CANCEL;
			if (allowFastScrolling) {
				fastScrollingCurrentY = event.getY() - top;
				scroll(calculateOffset());
				updateFastScroller(allowFastScrolling, regularScrolling, fastScrolling);
			}
			if (cancel) {
				requestDisallowInterceptTouchEvent(false);
				updateFastScroller(allowFastScrolling, regularScrolling, false);
				invalidate();
			}
			return true;
		}
		return false;
	}

	private static final int[] STATE_PRESSED = {android.R.attr.state_enabled, android.R.attr.state_pressed};
	private static final int[] STATE_NORMAL = {android.R.attr.state_enabled};

	private void onDrawFastScroller(Canvas canvas) {
		long time = SystemClock.elapsedRealtime();
		long passed = time - showFastScrollingStart;
		boolean shouldInvalidate = showFastScrolling && passed < FAST_SCROLLER_TRANSITION_IN ||
				!showFastScrolling && passed >= FAST_SCROLLER_TRANSITION_OUT_DELAY &&
						passed < FAST_SCROLLER_TRANSITION_OUT_DELAY + FAST_SCROLLER_TRANSITION_OUT;
		float stateValue = showFastScrolling ? (float) passed / FAST_SCROLLER_TRANSITION_IN
				: 1f - (float) (passed - FAST_SCROLLER_TRANSITION_OUT_DELAY) / FAST_SCROLLER_TRANSITION_OUT;
		stateValue = Math.max(0, Math.min(stateValue, 1));

		if (stateValue > 0f) {
			boolean rtl = C.API_JELLY_BEAN_MR1 && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
			int maxWidth = Math.max(thumbDrawable.getIntrinsicWidth(), trackDrawable.getIntrinsicHeight());
			int translateX = (int) (maxWidth * (1f - stateValue) + 0.5f);
			int top = getEdgeEffectShift(EdgeEffectHandler.Side.TOP);
			int height = getHeight() - top - getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
			float offset = fastScrolling ? calculateOffset() : getCurrentOffset();
			int thumbHeight = thumbDrawable.getIntrinsicHeight();
			int thumbY = (int) ((height - thumbHeight) * offset);

			boolean thumbBitmap = thumbDrawable.getCurrent() instanceof BitmapDrawable;
			int trackTop = top + (thumbBitmap ? thumbHeight / 2 : 0);
			int trackBottom = top + height - (thumbBitmap ? thumbHeight / 2 : 0);
			trackDrawable.setState(fastScrolling ? STATE_PRESSED : STATE_NORMAL);
			int trackExtra = (maxWidth - trackDrawable.getIntrinsicWidth()) / 2;
			if (rtl) {
				trackDrawable.setBounds(trackExtra - translateX, trackTop,
						trackExtra + trackDrawable.getIntrinsicWidth() - translateX, trackBottom);
			} else {
				trackDrawable.setBounds(getWidth() - trackExtra - trackDrawable.getIntrinsicWidth() + translateX,
						trackTop, getWidth() - trackExtra + translateX, trackBottom);
			}
			trackDrawable.draw(canvas);
			int thumbExtra = (maxWidth - thumbDrawable.getIntrinsicWidth()) / 2;
			thumbDrawable.setState(fastScrolling ? STATE_PRESSED : STATE_NORMAL);
			if (rtl) {
				thumbDrawable.setBounds(thumbExtra - translateX, top + thumbY,
						thumbExtra + thumbDrawable.getIntrinsicWidth() - translateX, top + thumbY + thumbHeight);
			} else {
				thumbDrawable.setBounds(getWidth() - thumbExtra - thumbDrawable.getIntrinsicWidth() + translateX,
						top + thumbY, getWidth() - thumbExtra + translateX, top + thumbY + thumbHeight);
			}
			thumbDrawable.draw(canvas);
		}

		if (shouldInvalidate) {
			invalidate();
		}
	}
}
