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

package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.view.View;
import android.widget.ListView;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

public class ListScroller implements Runnable {
	private static final int SCROLL_DURATION = 250;
	private static final int MIN_SCROLL_DURATION = 10;

	private final ListView listView;
	private final int additionalScroll;

	private int scrollDuration;
	private int toPosition;
	private int currentPosition;

	private boolean idleState;
	private long idleStart;
	private int idleTop;

	private ListScroller(ListView listView) {
		this.listView = listView;
		additionalScroll = (int) (16f * ResourceUtils.obtainDensity(listView));
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
		listView.removeCallbacks(this);
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
			int delta = Math.abs(toPosition - first);
			scrollDuration = delta > 0 ? Math.max(SCROLL_DURATION / delta, MIN_SCROLL_DURATION) : SCROLL_DURATION;
			currentPosition = -1;
			idleState = false;
			listView.post(this);
		} else {
			listView.setSelection(position);
		}
	}

	@Override
	public void run() {
		int childCount = listView.getChildCount();
		if (childCount == 0) {
			return;
		}
		boolean post = false;
		int postInterval = 0;
		int listHeight = listView.getHeight();
		int first = listView.getFirstVisiblePosition();
		boolean idle = false;
		if (toPosition > first) {
			int last = first + childCount - 1;
			if (last != currentPosition) {
				currentPosition = last;
				View view = listView.getChildAt(childCount - 1);
				// More attention to scrolling near the end, because additional bottom padding bug may appear
				boolean closeToEnd = last >= listView.getCount() - childCount;
				int distance = view.getHeight() + view.getTop() - listHeight + listView.getPaddingBottom();
				int duration = scrollDuration;
				// Fix jamming
				if (distance > listView.getHeight() / 2) {
					currentPosition = -1;
				}
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
						duration = scrollDuration * (index + 1);
					} else {
						post = hasScroll;
					}
				} else {
					post = hasScroll;
				}
				listView.smoothScrollBy(distance, duration);
				if (!closeToEnd) {
					postInterval = duration * 2 / 3;
				}
			} else {
				idle = true;
				post = true;
			}
		} else {
			if (first != currentPosition || first == toPosition) {
				currentPosition = first;
				View view = listView.getChildAt(0);
				if (first == toPosition) {
					int distance = view.getTop() - listView.getPaddingTop();
					if (distance < 0) {
						listView.smoothScrollBy(distance, scrollDuration);
					}
					if (distance + additionalScroll < 0) {
						post = true;
					}
					postInterval = scrollDuration;
				} else if (first > toPosition) {
					int distance = view.getTop() - listView.getPaddingTop() - additionalScroll;
					// Fix jamming
					if (distance < -listView.getHeight() / 2) {
						currentPosition = -1;
					}
					listView.smoothScrollBy(distance, scrollDuration);
					post = true;
				}
			} else {
				idle = true;
				post = true;
			}
		}
		// Fix infinite loops
		if (idle) {
			long time = System.currentTimeMillis();
			if (!idleState) {
				idleState = true;
				idleStart = time;
				idleTop = listView.getChildAt(0).getTop();
			} else {
				if (time - idleStart > SCROLL_DURATION && idleTop == listView.getChildAt(0).getTop()) {
					post = false;
				}
			}
		} else {
			idleState = false;
		}
		if (post) {
			if (postInterval > 0) {
				listView.postDelayed(this, postInterval);
			} else {
				listView.post(this);
			}
		}
	}
}