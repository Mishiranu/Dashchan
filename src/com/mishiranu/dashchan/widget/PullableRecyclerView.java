package com.mishiranu.dashchan.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
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
		wrapper.handleAttrs(attrs, defStyleAttr, 0);
	}

	/* init */ {
		addOnItemTouchListener(new OnItemTouchListener() {
			private boolean disallowIntercept = false;

			@Override
			public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
				return !disallowIntercept && wrapper.onTouchEvent(e);
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
