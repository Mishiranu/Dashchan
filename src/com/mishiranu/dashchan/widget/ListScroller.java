/*
 * Copyright 2014-2017 Fukurou Mishiranu
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

import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.callback.ScrollListenerComposite;

import java.lang.reflect.Method;

public class ListScroller implements AbsListView.OnScrollListener {
	private static final float MIN_SCROLL_DURATION_PER_DP = 0.1f;
	private static final float MAX_SCROLL_DURATION_PER_DP = 0.5f;

	private final ListView listView;
	private final int additionalScroll;
	private float minScrollDurationPerPixel;
	private float maxScrollDurationPerPixel;

	private int toPosition = AbsListView.INVALID_POSITION;
	private int deltaPosition;

	private ListScroller(ListView listView) {
		this.listView = listView;
		float density = ResourceUtils.obtainDensity(listView);
		additionalScroll = (int) (16f * density);
		minScrollDurationPerPixel = MIN_SCROLL_DURATION_PER_DP / density;
		maxScrollDurationPerPixel = MAX_SCROLL_DURATION_PER_DP / density;
		ScrollListenerComposite.obtain(listView).add(this);
	}

	public static int getJumpThreshold(Context context) {
		return context.getResources().getConfiguration().screenHeightDp / 40;
	}

	private static ListScroller getListScroller(ListView listView, boolean mayCreate) {
		ListScroller listScroller = (ListScroller) listView.getTag(R.id.seek_bar);
		if (listScroller == null && mayCreate) {
			listScroller = new ListScroller(listView);
			listView.setTag(R.id.seek_bar, listScroller);
		}
		return listScroller;
	}

	public static void cancel(ListView listView) {
		ListScroller listScroller = getListScroller(listView, false);
		if (listScroller != null) {
			listScroller.cancel();
		}
	}

	private void cancel() {
		toPosition = AbsListView.INVALID_POSITION;
		ListViewUtils.cancelListFling(listView);
	}

	public static void scrollTo(ListView listView, int position) {
		getListScroller(listView, true).scrollTo(position);
	}

	private void scrollTo(int position) {
		cancel();
		float scale = AnimationUtils.getAnimatorDurationScale();
		if (scale > 0f) {
			int first = listView.getFirstVisiblePosition();
			if (toPosition == AbsListView.INVALID_POSITION) {
				deltaPosition = 0;
			}
			toPosition = position;
			int jumpThreshold = getJumpThreshold(listView.getContext());
			if (toPosition > first + jumpThreshold) {
				listView.setSelection(toPosition - jumpThreshold);
				deltaPosition += jumpThreshold;
			} else if (toPosition < first - jumpThreshold) {
				listView.setSelection(toPosition + jumpThreshold);
				deltaPosition += jumpThreshold;
			} else {
				deltaPosition += Math.abs(toPosition - first);
			}
			handleScroll();
		} else {
			listView.setSelection(position);
		}
	}

	private void handleScroll() {
		int childCount = listView.getChildCount();
		if (childCount == 0) {
			toPosition = AbsListView.INVALID_POSITION;
			return;
		}

		int listHeight = listView.getHeight();
		int firstViewIndex = listView.getFirstVisiblePosition();
		int lastViewIndex = firstViewIndex + childCount - 1;
		int visiblePaddingTop = listView.getPaddingTop();

		View firstVisibleView = null;
		int firstVisibleViewIndex = 0;
		for (int i = 0; i < childCount; i++) {
			firstVisibleView = listView.getChildAt(i);
			if (firstVisibleView.getBottom() >= visiblePaddingTop) {
				firstVisibleViewIndex = firstViewIndex + i;
				break;
			}
		}

		if (toPosition > firstVisibleViewIndex) {
			View lastView = listView.getChildAt(childCount - 1);
			// More attention to scrolling near the end, because additional bottom padding bug may appear
			boolean closeToEnd = lastViewIndex >= listView.getCount() - childCount;
			int distance = lastView.getHeight() + lastView.getTop() - listHeight + listView.getPaddingBottom();
			boolean hasScroll = lastViewIndex + 1 < listView.getCount();
			if (hasScroll) {
				distance += additionalScroll;
			}
			if (lastViewIndex >= toPosition) {
				int index = toPosition - firstViewIndex;
				View positionView = listView.getChildAt(index);
				int topDistance = positionView.getTop() - visiblePaddingTop;
				if (topDistance < distance || !closeToEnd) {
					distance = topDistance;
				}
			}
			if (distance > 0) {
				listSmoothScrollBy(distance);
			} else {
				toPosition = AbsListView.INVALID_POSITION;
			}
		} else if (toPosition < firstVisibleViewIndex) {
			View firstView = listView.getChildAt(0);
			int distance;
			if (toPosition < firstViewIndex) {
				// Scroll additionally so first view will be a bit overscrolled and become second
				distance = firstView.getTop() - visiblePaddingTop - additionalScroll;
			} else {
				distance = firstView.getTop() - visiblePaddingTop;
			}
			listSmoothScrollBy(distance);
		} else {
			int distance = firstVisibleView.getTop() - visiblePaddingTop;
			if (distance < 0) {
				listSmoothScrollBy(distance);
			} else {
				toPosition = AbsListView.INVALID_POSITION;
			}
		}
	}

	private static final Method METHOD_SMOOTH_SCROLL_BY_LINEAR;

	static {
		Method smoothScrollByLinear;
		try {
			smoothScrollByLinear = AbsListView.class.getDeclaredMethod("smoothScrollBy",
					int.class, int.class, boolean.class);
			smoothScrollByLinear.setAccessible(true);
		} catch (Exception e) {
			smoothScrollByLinear = null;
		}
		METHOD_SMOOTH_SCROLL_BY_LINEAR = smoothScrollByLinear;
	}

	private void listSmoothScrollBy(int distance) {
		float threshold = getJumpThreshold(listView.getContext());
		float minItems = threshold / 5f;
		float scrollDurationPerPixel = AnimationUtils.lerp(maxScrollDurationPerPixel, minScrollDurationPerPixel,
				Math.min(Math.max((deltaPosition - minItems) / (threshold - minItems), 0f), 1f));
		int duration = (int) (Math.abs(distance) * scrollDurationPerPixel);
		if (METHOD_SMOOTH_SCROLL_BY_LINEAR != null) {
			try {
				METHOD_SMOOTH_SCROLL_BY_LINEAR.invoke(listView, distance, duration, true);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			listView.smoothScrollBy(distance, duration);
		}
	}

	private final Runnable handleScrollRunnable = this::handleScroll;

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
			toPosition = AbsListView.INVALID_POSITION;
		} else if (toPosition != AbsListView.INVALID_POSITION && scrollState == SCROLL_STATE_IDLE) {
			listView.post(handleScrollRunnable);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}
}
