package com.mishiranu.dashchan.widget;

import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class RecyclerScrollTracker {
	private final OnScrollListener listener;

	public RecyclerScrollTracker(OnScrollListener listener) {
		this.listener = listener;
	}

	public void attach(RecyclerView recyclerView) {
		recyclerView.addOnScrollListener(scrollListener);
		recyclerView.addOnLayoutChangeListener(layoutListener);
	}

	public void detach(RecyclerView recyclerView) {
		recyclerView.removeOnScrollListener(scrollListener);
		recyclerView.removeOnLayoutChangeListener(layoutListener);
	}

	private boolean scrollingDown = false;
	private int firstPosition = -1;
	private int firstTop = 0;

	private int slopDelta = -1;
	private int totalDelta = 0;

	private void onScrolled(RecyclerView recyclerView, int dx, int dy) {
		if (slopDelta == -1) {
			slopDelta = ViewConfiguration.get(recyclerView.getContext()).getScaledTouchSlop();
		}
		LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
		int totalItemCount = layoutManager.getItemCount();
		int firstPosition = layoutManager.findFirstVisibleItemPosition();
		int lastPosition = layoutManager.findLastVisibleItemPosition();
		boolean first;
		boolean last;
		if (firstPosition < 0 || lastPosition < 0 || recyclerView.getChildCount() == 0) {
			first = false;
			last = false;
			scrollingDown = false;
			this.firstPosition = -1;
			firstTop = 0;
		} else {
			first = firstPosition == 0;
			last = lastPosition == totalItemCount - 1;
			int firstTop = recyclerView.getChildAt(0).getTop();
			if (dx == 0 && dy == 0) {
				// Layout changed
				if (this.firstPosition != firstPosition || this.firstTop != firstTop) {
					totalDelta = 0;
					scrollingDown = this.firstPosition >= 0 && (firstPosition > this.firstPosition ||
							firstPosition == this.firstPosition && firstTop > this.firstTop);
				}
			} else if (dy != 0) {
				totalDelta += dy;
				if (Math.abs(totalDelta) > slopDelta) {
					scrollingDown = totalDelta > 0;
					totalDelta = 0;
				}
			}
			this.firstPosition = firstPosition;
			this.firstTop = firstTop;
		}
		listener.onScroll(recyclerView, scrollingDown, totalItemCount, first, last);
	}

	private boolean scrollHandled = false;

	private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
			// May call with (dx, dy) == (0, 0) during layout (e.g. after scrollToPosition)
			RecyclerScrollTracker.this.onScrolled(recyclerView, dx, dy);
			scrollHandled = true;
		}

		@Override
		public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {}
	};

	// RecyclerView won't call onScroll after scrollToPosition if first and last items weren't changed
	private final View.OnLayoutChangeListener layoutListener = (v, left, top, right, bottom,
			oldLeft, oldTop, oldRight, oldBottom) -> {
		if (scrollHandled) {
			scrollHandled = false;
		} else {
			onScrolled((RecyclerView) v, 0, 0);
		}
	};

	public interface OnScrollListener {
		void onScroll(ViewGroup view, boolean scrollingDown, int totalItemCount, boolean first, boolean last);
	}
}
