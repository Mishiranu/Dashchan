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

package com.mishiranu.dashchan.widget.callback;

import android.view.View;
import android.widget.AbsListView;

public class ListScrollTracker implements AbsListView.OnScrollListener, Runnable {
	private final OnScrollListener listener;

	public ListScrollTracker(OnScrollListener listener) {
		this.listener = listener;
	}

	private boolean scrollingDown = false;
	private int lastTrackingItem = -1;
	private int lastTrackingTop;
	private int lastFirstItem = -1;
	private int lastFirstTop;

	private boolean prevFirst, prevLast;

	private void notifyScroll(AbsListView view, int scrollY, int firstVisibleItem,
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
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
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
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (scrollState == SCROLL_STATE_IDLE) {
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
		public void onScroll(AbsListView view, int scrollY, int totalItemCount, boolean first, boolean last);
	}
}