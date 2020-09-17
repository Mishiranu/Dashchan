package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.R;

public class PullableRecyclerView extends PaddedRecyclerView implements PullableWrapper.Wrapped {
	private final PullableWrapper wrapper = new PullableWrapper(this);

	public PullableRecyclerView(@NonNull Context context) {
		this(context, null);
	}

	@SuppressLint("PrivateResource")
	public PullableRecyclerView(@NonNull Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.recyclerViewStyle);
	}

	public PullableRecyclerView(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	/* init */ {
		int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		addOnItemTouchListener(new OnItemTouchListener() {
			private boolean allowIntercept = false;
			private boolean disallowIntercept = false;

			private float downX;
			private float downY;

			@Override
			public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				float x = e.getX();
				float y = e.getY();
				if (e.getAction() == MotionEvent.ACTION_DOWN) {
					allowIntercept = false;
					downX = x;
					downY = y;
				}
				if (!disallowIntercept && wrapper.onTouchEvent(e)) {
					if (!allowIntercept) {
						float movement = (float) Math.sqrt(Math.pow(downX - x, 2) + Math.pow(downY - y, 2));
						if (movement > touchSlop) {
							allowIntercept = true;
						}
					}
					return allowIntercept;
				}
				return false;
			}

			@Override
			public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				if (!disallowIntercept) {
					boolean result = wrapper.onTouchEvent(e);
					if (!result) {
						// Reset intercepted state
						removeOnItemTouchListener(this);
						addOnItemTouchListener(this);
					}
				}
			}

			@Override
			public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
				this.disallowIntercept = disallowIntercept;
			}
		});
	}

	public PullableWrapper getWrapper() {
		return wrapper;
	}

	private final Rect bounds = new Rect();

	@Override
	public boolean isScrolledToTop() {
		Rect bounds = this.bounds;
		View view = getChildCount() > 0 ? getChildAt(0) : null;
		if (view == null) {
			return true;
		} else if (getChildAdapterPosition(view) == 0) {
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
		Adapter<?> adapter = getAdapter();
		if (view == null) {
			return true;
		} else if (adapter != null && getChildAdapterPosition(view) == adapter.getItemCount() - 1) {
			getDecoratedBoundsWithMargins(view, bounds);
			return bounds.bottom <= getHeight() - getPaddingBottom();
		} else {
			return false;
		}
	}

	@Override
	public void draw(Canvas canvas) {
		wrapper.drawBefore(canvas);
		try {
			super.draw(canvas);
		} finally {
			wrapper.drawAfter(canvas);
		}
	}
}
