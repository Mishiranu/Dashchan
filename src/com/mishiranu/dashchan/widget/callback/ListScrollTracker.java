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

public class ListScrollTracker implements AbsListView.OnScrollListener, Runnable
{
	private final OnScrollListener mListener;

	public ListScrollTracker(OnScrollListener listener)
	{
		mListener = listener;
	}

	private boolean mScrollingDown = false;
	private int mLastTrackingItem = -1;
	private int mLastTrackingTop;
	private int mLastFirstItem = -1;
	private int mLastFirstTop;

	private boolean mPrevFirst, mPrevLast;

	private void notifyScroll(AbsListView view, int scrollY, int firstVisibleItem,
			int visibleItemCount, int totalItemCount)
	{
		boolean first = firstVisibleItem == 0;
		boolean last = firstVisibleItem + visibleItemCount == totalItemCount;
		boolean changedFirstLast = first != mPrevFirst || last != mPrevLast;
		mPrevFirst = first;
		mPrevLast = last;
		if (scrollY != 0) mScrollingDown = scrollY > 0;
		if (scrollY != 0 || changedFirstLast) mListener.onScroll(view, scrollY, totalItemCount, first, last);
	}

	public int calculateTrackingViewIndex(int visibleItemCount)
	{
		if (visibleItemCount > 2) return visibleItemCount / 2;
		else if (visibleItemCount == 2) return mScrollingDown ? 1 : 0;
		else return 0;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		if (visibleItemCount > 0)
		{
			int trackingIndex = calculateTrackingViewIndex(visibleItemCount);
			int trackingItem = firstVisibleItem + trackingIndex;
			View tracking = view.getChildAt(trackingIndex);
			if (tracking == null) return;
			int trackingTop = tracking.getTop();
			int firstVisibleTop = view.getChildAt(0).getTop();
			// Detect child height-change animation
			boolean standsStill = mLastFirstItem == firstVisibleItem && mLastFirstTop == firstVisibleTop;
			mLastFirstItem = firstVisibleItem;
			mLastFirstTop = firstVisibleTop;
			if (mLastTrackingItem == -1)
			{
				mLastTrackingItem = trackingItem;
				notifyScroll(view, 0, firstVisibleItem, visibleItemCount, totalItemCount);
			}
			else
			{
				int scrollY = 0;
				if (mLastTrackingItem != trackingItem)
				{
					int lastTrackingIndex = mLastTrackingItem - firstVisibleItem;
					// Check last tracking view is not recycled
					if (lastTrackingIndex >= 0 && lastTrackingIndex < visibleItemCount)
					{
						View lastTracking = view.getChildAt(lastTrackingIndex);
						int lastTop = lastTracking.getTop();
						scrollY = mLastTrackingTop - lastTop;
					}
					mLastTrackingItem = trackingItem;
				}
				else scrollY = mLastTrackingTop - trackingTop;
				// 100% false scroll: it's can be just child height animation, for example
				if (standsStill) scrollY = 0;
				notifyScroll(view, scrollY, firstVisibleItem, visibleItemCount, totalItemCount);
			}
			mLastTrackingTop = trackingTop;
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		if (scrollState == SCROLL_STATE_IDLE) view.postDelayed(this, 500);
		else view.removeCallbacks(this);
	}

	@Override
	public void run()
	{
		mLastTrackingItem = -1;
	}

	public interface OnScrollListener
	{
		public void onScroll(AbsListView view, int scrollY, int totalItemCount, boolean first, boolean last);
	}
}