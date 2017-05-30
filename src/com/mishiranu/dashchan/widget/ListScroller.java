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
	private static final float SCROLL_DURATION_PER_DP = 0.25f;

	private final ListView listView;
	private final int additionalScroll;
	private float scrollDurationPerPixel;

	private int toPosition = AbsListView.INVALID_POSITION;

	private ListScroller(ListView listView) {
		this.listView = listView;
		float density = ResourceUtils.obtainDensity(listView);
		additionalScroll = (int) (16f * density);
		scrollDurationPerPixel = SCROLL_DURATION_PER_DP / density;
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
			toPosition = position;
			int jumpThreshold = getJumpThreshold(listView.getContext());
			if (toPosition > first + jumpThreshold) {
				listView.setSelection(toPosition - jumpThreshold);
			} else if (toPosition < first - jumpThreshold) {
				listView.setSelection(toPosition + jumpThreshold);
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
		int first = listView.getFirstVisiblePosition();
		int last = first + childCount - 1;

		if (toPosition > first) {
			View view = listView.getChildAt(childCount - 1);
			// More attention to scrolling near the end, because additional bottom padding bug may appear
			boolean closeToEnd = last >= listView.getCount() - childCount;
			int distance = view.getHeight() + view.getTop() - listHeight + listView.getPaddingBottom();
			boolean hasScroll = last + 1 < listView.getCount();
			if (hasScroll) {
				distance += additionalScroll;
			}
			if (last >= toPosition) {
				int index = toPosition - first;
				view = listView.getChildAt(index);
				int topDistance = view.getTop() - listView.getPaddingTop();
				if (topDistance < distance || !closeToEnd) {
					distance = topDistance;
				}
			}
			if (distance > 0) {
				listSmoothScrollBy(distance);
			} else {
				toPosition = AbsListView.INVALID_POSITION;
			}
		} else {
			View view = listView.getChildAt(0);
			if (first == toPosition) {
				int distance = view.getTop() - listView.getPaddingTop();
				if (distance < 0) {
					listSmoothScrollBy(distance);
				} else {
					toPosition = AbsListView.INVALID_POSITION;
				}
			} else if (first > toPosition) {
				int distance = view.getTop() - listView.getPaddingTop() - additionalScroll;
				listSmoothScrollBy(distance);
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
