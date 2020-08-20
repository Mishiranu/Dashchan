package com.mishiranu.dashchan.widget;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class RecyclerScrollTracker extends RecyclerView.OnScrollListener implements Runnable {
	private final OnScrollListener listener;

	public RecyclerScrollTracker(OnScrollListener listener) {
		this.listener = listener;
	}

	private boolean scrollingDown = false;
	private int lastTrackingItem = -1;
	private int lastTrackingTop;
	private int lastFirstItem = -1;
	private int lastFirstTop;

	private boolean prevFirst, prevLast;

	private void notifyScroll(ViewGroup view, int scrollY, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		boolean first = firstVisibleItem == 0;
		boolean last = firstVisibleItem + visibleItemCount == totalItemCount;
		boolean changedFirstLast = first != prevFirst || last != prevLast;
		prevFirst = first;
		prevLast = last;
		if (scrollY != 0) {
			scrollingDown = scrollY > 0;
		}
		if (scrollY != 0 || changedFirstLast) {
			listener.onScroll(view, scrollY, totalItemCount, first, last);
		}
	}

	public int calculateTrackingViewIndex(int visibleItemCount) {
		if (visibleItemCount > 2) {
			return visibleItemCount / 2;
		} else if (visibleItemCount == 2) {
			return scrollingDown ? 1 : 0;
		} else {
			return 0;
		}
	}

	@Override
	public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
		RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
		View first = recyclerView.getChildCount() > 0 ? recyclerView.getChildAt(0) : null;
		int firstVisibleItem = first != null ? recyclerView.getChildViewHolder(first).getAdapterPosition() : 0;
		int totalItemCount = adapter != null ? adapter.getItemCount() : 0;
		onScrollInternal(recyclerView, firstVisibleItem, recyclerView.getChildCount(), totalItemCount);
	}

	private void onScrollInternal(ViewGroup view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (visibleItemCount > 0) {
			int trackingIndex = calculateTrackingViewIndex(visibleItemCount);
			int trackingItem = firstVisibleItem + trackingIndex;
			View tracking = view.getChildAt(trackingIndex);
			if (tracking == null) {
				return;
			}
			int trackingTop = tracking.getTop();
			int firstVisibleTop = view.getChildAt(0).getTop();
			// Detect child height-change animation
			boolean standsStill = lastFirstItem == firstVisibleItem && lastFirstTop == firstVisibleTop;
			lastFirstItem = firstVisibleItem;
			lastFirstTop = firstVisibleTop;
			if (lastTrackingItem == -1) {
				lastTrackingItem = trackingItem;
				notifyScroll(view, 0, firstVisibleItem, visibleItemCount, totalItemCount);
			} else {
				int scrollY = 0;
				if (lastTrackingItem != trackingItem) {
					int lastTrackingIndex = lastTrackingItem - firstVisibleItem;
					// Check last tracking view is not recycled
					if (lastTrackingIndex >= 0 && lastTrackingIndex < visibleItemCount) {
						View lastTracking = view.getChildAt(lastTrackingIndex);
						int lastTop = lastTracking.getTop();
						scrollY = lastTrackingTop - lastTop;
					}
					lastTrackingItem = trackingItem;
				} else {
					scrollY = lastTrackingTop - trackingTop;
				}
				// 100% false scroll: it can be just a child's height animation, for example
				if (standsStill) {
					scrollY = 0;
				}
				notifyScroll(view, scrollY, firstVisibleItem, visibleItemCount, totalItemCount);
			}
			lastTrackingTop = trackingTop;
		}
	}

	@Override
	public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
		onScrollStateChangedInternal(recyclerView, newState == RecyclerView.SCROLL_STATE_IDLE);
	}

	private void onScrollStateChangedInternal(ViewGroup view, boolean idle) {
		if (idle) {
			view.postDelayed(this, 500);
		} else {
			view.removeCallbacks(this);
		}
	}

	@Override
	public void run() {
		lastTrackingItem = -1;
	}

	public interface OnScrollListener {
		public void onScroll(ViewGroup view, int scrollY, int totalItemCount, boolean first, boolean last);
	}
}
