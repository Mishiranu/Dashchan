package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import org.xmlpull.v1.XmlPullParser;

public class PaddedRecyclerView extends RecyclerView implements EdgeEffectHandler.Shift, PullableWrapper.Wrapped {
	private static final long FAST_SCROLLER_TRANSITION_IN = 100;
	private static final long FAST_SCROLLER_TRANSITION_OUT = 200;
	private static final long FAST_SCROLLER_TRANSITION_OUT_DELAY = 1000;

	private final EdgeEffectHandler edgeEffectHandler = EdgeEffectHandler.bind(this, this);
	private EdgeEffectHandler.Shift shift;
	private PullableWrapper pullableWrapper;

	private final Drawable thumbDrawable;
	private final Drawable trackDrawable;
	private final int touchSlop;
	private final int minTrackSize;

	private boolean fastScrollerEnabled;
	private boolean fastScrollerAllowed;
	private boolean regularScrolling;
	private boolean fastScrolling;

	private boolean fastScrollingDown;
	private Float fastScrollingStartOffset;
	private float fastScrollingStartY;
	private float fastScrollingCurrentY;

	private long showFastScrollingStart;
	private boolean showFastScrolling;

	private static AttributeSet createDefaultAttributeSet(Context context) {
		try {
			XmlPullParser parser = context.getResources().getXml(R.xml.scrollbars);
			parser.next();
			parser.nextTag();
			return Xml.asAttributeSet(parser);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public PaddedRecyclerView(@NonNull Context context) {
		this(context, createDefaultAttributeSet(context));
		setVerticalScrollBarEnabled(false);
		setHorizontalScrollBarEnabled(false);
	}

	public PaddedRecyclerView(@NonNull Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PaddedRecyclerView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	/* init */ {
		ThemeEngine.Theme theme = ThemeEngine.getTheme(getContext());
		edgeEffectHandler.setColor(theme.accent);

		float density = ResourceUtils.obtainDensity(this);
		Drawable thumbDrawable = ResourceUtils.getDrawable(getContext(), android.R.attr.fastScrollThumbDrawable, 0);
		this.thumbDrawable = C.API_LOLLIPOP ? thumbDrawable : ListViewUtils
				.colorizeListThumbDrawable4(getContext(), thumbDrawable);
		if (C.API_LOLLIPOP) {
			int[][] states = {{android.R.attr.state_enabled, android.R.attr.state_pressed},
					{android.R.attr.state_enabled}};
			int[] colors = {theme.accent, theme.controlNormal21};
			thumbDrawable.setTintList(new ColorStateList(states, colors));
		}
		trackDrawable = ResourceUtils.getDrawable(getContext(), android.R.attr.fastScrollTrackDrawable, 0);
		touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		minTrackSize = (int) (16f * density);

		setRecycledViewPool(new ListViewUtils.UnlimitedRecycledViewPool());
		addOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				boolean regularScrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
				updateFastScroller(false, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, fastScrolling);
			}
		});
		addOnItemTouchListener(new OnItemTouchListener() {
			@Override
			public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				return handleTouchEvent(e);
			}

			@Override
			public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				handleTouchEvent(e);
			}

			@Override
			public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
				if (disallowIntercept) {
					fastScrollingDown = false;
					if (fastScrolling) {
						updateFastScroller(true, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, false);
					}
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
			fastScrollingDown = false;
			updateFastScroller(true, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, false);
		}
	}

	private boolean isFastScrollerAvailable() {
		return fastScrollerEnabled && fastScrollerAllowed;
	}

	@SuppressWarnings("unused") // Overrides hidden Android API protected method
	protected void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar, int l, int t, int r, int b) {
		if (!isFastScrollerAvailable()) {
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
		return getClipToPadding() ? 0 : side == EdgeEffectHandler.Side.TOP ? getPaddingTop() : getPaddingBottom();
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);

		int range = computeVerticalScrollRange();
		int extent = computeVerticalScrollExtent();
		boolean allowFastScrolling = extent > 0 && range >= 2 * extent;
		updateFastScroller(false, fastScrollerEnabled, allowFastScrolling, regularScrolling, fastScrolling);
		// OVER_SCROLL_IF_CONTENT_SCROLLS it not supported, see https://issuetracker.google.com/issues/37076456
		setOverScrollMode(range > extent ? OVER_SCROLL_ALWAYS : OVER_SCROLL_NEVER);
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);

		if (!hasWindowFocus) {
			fastScrollingDown = false;
			if (fastScrolling) {
				updateFastScroller(true, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, false);
			}
		}
	}

	private final Runnable invalidateRunnable = this::invalidate;

	private void updateFastScroller(boolean immediately, boolean fastScrollerEnabled, boolean fastScrollerAllowed,
			boolean regularScrolling, boolean fastScrolling) {
		boolean oldShow = this.fastScrollerAllowed && this.fastScrollerEnabled &&
				(this.regularScrolling || this.fastScrolling);
		boolean newShow = fastScrollerAllowed && fastScrollerEnabled &&
				(regularScrolling || fastScrolling);
		this.fastScrollerEnabled = fastScrollerEnabled;
		this.fastScrollerAllowed = fastScrollerAllowed;
		this.regularScrolling = regularScrolling;
		this.fastScrolling = fastScrolling;
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
				if (immediately) {
					start = time - (long) ((float) (FAST_SCROLLER_TRANSITION_IN - passed) /
							FAST_SCROLLER_TRANSITION_IN * FAST_SCROLLER_TRANSITION_OUT) -
							FAST_SCROLLER_TRANSITION_IN - FAST_SCROLLER_TRANSITION_OUT_DELAY;
				} else {
					start = time - passed;
					postDelayed(invalidateRunnable, FAST_SCROLLER_TRANSITION_IN - passed +
							FAST_SCROLLER_TRANSITION_OUT_DELAY);
				}
			} else {
				if (!newShow) {
					postDelayed(invalidateRunnable, FAST_SCROLLER_TRANSITION_OUT_DELAY);
				}
				start = newShow ? time : time - FAST_SCROLLER_TRANSITION_IN;
			}
			showFastScrollingStart = start;
			showFastScrolling = newShow;
			invalidate();
		} else if (!isFastScrollerAvailable() && passed < FAST_SCROLLER_TRANSITION_OUT_DELAY) {
			removeCallbacks(invalidateRunnable);
			showFastScrollingStart = time - FAST_SCROLLER_TRANSITION_IN - FAST_SCROLLER_TRANSITION_OUT_DELAY;
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
		LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
		int count = layoutManager.getItemCount();
		if (count > 0) {
			if (offset < 1f) {
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

	private boolean handleTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();
		int top = getEdgeEffectShift(EdgeEffectHandler.Side.TOP);
		float currentY = event.getY() - top;
		boolean fastScrollerAvailable = isFastScrollerAvailable();
		if (action == MotionEvent.ACTION_DOWN) {
			fastScrollingDown = false;
			if (!fastScrollerAvailable) {
				return false;
			}
			boolean rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
			int trackWidth = Math.max(minTrackSize, Math.max(thumbDrawable.getIntrinsicWidth(),
					trackDrawable.getIntrinsicWidth()));
			boolean atThumbVertical = rtl ? event.getX() <= trackWidth : event.getX() >= getWidth() - trackWidth;
			if (atThumbVertical) {
				int height = getHeight() - top - getEdgeEffectShift(EdgeEffectHandler.Side.BOTTOM);
				float offset = getCurrentOffset();
				int thumbHeight = thumbDrawable.getIntrinsicHeight();
				int thumbY = (int) ((height - thumbHeight) * offset);
				boolean atThumb = event.getY() >= top + thumbY && event.getY() <= top + thumbY + thumbHeight;
				fastScrollingDown = true;
				fastScrollingStartOffset = atThumb ? offset : null;
				fastScrollingStartY = currentY;
				if (!ViewUtils.isGestureNavigationOverlap(this, rtl, !rtl)) {
					fastScrollingCurrentY = currentY;
					getParent().requestDisallowInterceptTouchEvent(true);
					updateFastScroller(false, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, true);
					if (!atThumb) {
						scroll(calculateOffset());
					}
					return true;
				}
			}
		} else if (fastScrollingDown) {
			boolean finish = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
			if (!fastScrolling && fastScrollerAvailable) {
				boolean fastScrolling = false;
				if (finish) {
					fastScrolling = action != MotionEvent.ACTION_CANCEL;
				} else if (Math.abs(currentY - fastScrollingStartY) > touchSlop) {
					getParent().requestDisallowInterceptTouchEvent(true);
					fastScrolling = true;
				}
				if (fastScrolling) {
					updateFastScroller(false, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, true);
					if (pullableWrapper != null) {
						pullableWrapper.onTouchEventOrNull(null);
					}
				}
			}
			if (fastScrolling) {
				boolean cancel = !fastScrollerAvailable || finish;
				if (fastScrollerAvailable) {
					fastScrollingCurrentY = currentY;
					scroll(calculateOffset());
				}
				if (cancel) {
					updateFastScroller(false, fastScrollerEnabled, fastScrollerAllowed, regularScrolling, false);
				}
				return true;
			}
		}
		return false;
	}

	private static final int[] STATE_PRESSED = {android.R.attr.state_enabled, android.R.attr.state_pressed};
	private static final int[] STATE_NORMAL = {android.R.attr.state_enabled};

	private void onDrawFastScroller(Canvas canvas) {
		long time = SystemClock.elapsedRealtime();
		long passed = time - showFastScrollingStart;
		boolean shouldInvalidate;
		float stateValue;
		if (!showFastScrolling && passed >= FAST_SCROLLER_TRANSITION_IN) {
			passed -= FAST_SCROLLER_TRANSITION_IN;
			shouldInvalidate = passed >= FAST_SCROLLER_TRANSITION_OUT_DELAY &&
					passed < FAST_SCROLLER_TRANSITION_OUT_DELAY + FAST_SCROLLER_TRANSITION_OUT;
			stateValue = 1f - (float) (passed - FAST_SCROLLER_TRANSITION_OUT_DELAY)
					/ FAST_SCROLLER_TRANSITION_OUT;
		} else {
			shouldInvalidate = true;
			stateValue = (float) passed / FAST_SCROLLER_TRANSITION_IN;
		}
		stateValue = Math.max(0, Math.min(stateValue, 1));

		if (stateValue > 0f) {
			boolean rtl = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
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

	public PullableWrapper getPullable() {
		if (pullableWrapper == null) {
			PullableWrapper wrapper = new PullableWrapper(this);
			this.pullableWrapper = wrapper;
			addOnItemTouchListener(new OnItemTouchListener() {
				private boolean intercepted = false;
				private float downY;

				@Override
				public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
					float y = e.getY();
					if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
						intercepted = false;
						downY = y;
					}
					if (wrapper.onTouchEventOrNull(e)) {
						if (!intercepted && Math.abs(downY - y) > touchSlop) {
							intercepted = true;
						}
						return intercepted;
					}
					return false;
				}

				@Override
				public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
					boolean result = wrapper.onTouchEventOrNull(e);
					if (intercepted && !result) {
						intercepted = false;
						// Reset intercepted state
						removeOnItemTouchListener(this);
						addOnItemTouchListener(this);
					}
				}

				@Override
				public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
					if (disallowIntercept && intercepted) {
						intercepted = false;
						wrapper.onTouchEventOrNull(null);
					}
				}
			});
		}
		return pullableWrapper;
	}

	@Override
	public void draw(Canvas canvas) {
		if (pullableWrapper != null) {
			pullableWrapper.drawBefore(canvas);
			try {
				super.draw(canvas);
			} finally {
				pullableWrapper.drawAfter(canvas);
			}
		} else {
			super.draw(canvas);
		}
	}

	private final Rect bounds = new Rect();

	@Override
	public boolean isScrolledToTop() {
		Rect bounds = this.bounds;
		View view = getChildCount() > 0 ? getChildAt(0) : null;
		if (view == null) {
			return true;
		} else if (getChildLayoutPosition(view) == 0) {
			getDecoratedBoundsWithMargins(view, bounds);
			return bounds.top >= getPaddingTop();
		} else {
			return false;
		}
	}

	@Override
	public boolean isScrolledToBottom() {
		Rect bounds = this.bounds;
		int childCount = getChildCount();
		View view = childCount > 0 ? getChildAt(childCount - 1) : null;
		if (view == null) {
			return true;
		} else if (getChildLayoutPosition(view) == getLayoutManager().getItemCount() - 1) {
			getDecoratedBoundsWithMargins(view, bounds);
			return bounds.bottom <= getHeight() - getPaddingBottom();
		} else {
			return false;
		}
	}
}
