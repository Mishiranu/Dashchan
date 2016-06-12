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

public class ListScroller implements Runnable
{
	private static final int SCROLL_DURATION = 250;
	private static final int MIN_SCROLL_DURATION = 10;
	
	private final ListView mListView;
	private final int mAdditionalScroll;
	
	private int mScrollDuration;
	private int mToPosition;
	private int mCurrentPosition;
	
	private boolean mIdleState;
	private long mIdleStart;
	private int mIdleTop;
	
	private ListScroller(ListView listView)
	{
		mListView = listView;
		mAdditionalScroll = (int) (16f * ResourceUtils.obtainDensity(listView));
	}
	
	public static int getJumpThreshold(Context context)
	{
		return context.getResources().getConfiguration().screenHeightDp / 40;
	}
	
	private static ListScroller getListScroller(ListView listView, boolean mayCreate)
	{
		ListScroller listScroller = (ListScroller) listView.getTag(R.id.seek_bar);
		if (listScroller == null && mayCreate)
		{
			listScroller = new ListScroller(listView);
			listView.setTag(R.id.seek_bar);
		}
		return listScroller;
	}
	
	public static void cancel(ListView listView)
	{
		ListScroller listScroller = getListScroller(listView, false);
		if (listScroller != null) listScroller.cancel();
	}
	
	private void cancel()
	{
		mListView.removeCallbacks(this);
		ListViewUtils.cancelListFling(mListView);
	}
	
	public static void scrollTo(ListView listView, int position)
	{
		getListScroller(listView, true).scrollTo(position);
	}
	
	private void scrollTo(int position)
	{
		cancel();
		float scale = AnimationUtils.getAnimatorDurationScale();
		if (scale > 0f)
		{
			int first = mListView.getFirstVisiblePosition();
			mToPosition = position;
			int jumpThreshold = getJumpThreshold(mListView.getContext());
			if (mToPosition > first + jumpThreshold) mListView.setSelection(mToPosition - jumpThreshold);
			else if (mToPosition < first - jumpThreshold) mListView.setSelection(mToPosition + jumpThreshold);
			int delta = Math.abs(mToPosition - first);
			mScrollDuration = delta > 0 ? Math.max(SCROLL_DURATION / delta, MIN_SCROLL_DURATION) : SCROLL_DURATION;
			mCurrentPosition = -1;
			mIdleState = false;
			mListView.post(this);
		}
		else mListView.setSelection(position);
	}
	
	@Override
	public void run()
	{
		int childCount = mListView.getChildCount();
		if (childCount == 0) return;
		boolean post = false;
		int postInterval = 0;
		int listHeight = mListView.getHeight();
		int first = mListView.getFirstVisiblePosition();
		boolean idle = false;
		if (mToPosition > first)
		{
			int last = first + childCount - 1;
			if (last != mCurrentPosition)
			{
				mCurrentPosition = last;
				View view = mListView.getChildAt(childCount - 1);
				// More attention to scrolling near the end, because additional bottom padding bug may appear
				boolean closeToEnd = last >= mListView.getCount() - childCount;
				int distance = view.getHeight() + view.getTop() - listHeight + mListView.getPaddingBottom();
				int duration = mScrollDuration;
				// Fix jamming
				if (distance > mListView.getHeight() / 2) mCurrentPosition = -1;
				boolean hasScroll = last + 1 < mListView.getCount();
				if (hasScroll) distance += mAdditionalScroll;
				if (last >= mToPosition)
				{
					int index = mToPosition - first;
					view = mListView.getChildAt(index);
					int topDistance = view.getTop() - mListView.getPaddingTop();
					if (topDistance < distance || !closeToEnd)
					{
						distance = topDistance;
						duration = mScrollDuration * (index + 1);
					}
					else post = hasScroll;
				}
				else post = hasScroll;
				mListView.smoothScrollBy(distance, duration);
				if (!closeToEnd) postInterval = duration * 2 / 3;
			}
			else
			{
				idle = true;
				post = true;
			}
		}
		else
		{
			if (first != mCurrentPosition || first == mToPosition)
			{
				mCurrentPosition = first;
				View view = mListView.getChildAt(0);
				if (first == mToPosition)
				{
					int distance = view.getTop() - mListView.getPaddingTop();
					if (distance < 0) mListView.smoothScrollBy(distance, mScrollDuration);
					if (distance + mAdditionalScroll < 0) post = true;
					postInterval = mScrollDuration;
				}
				else if (first > mToPosition)
				{
					int distance = view.getTop() - mListView.getPaddingTop() - mAdditionalScroll;
					// Fix jamming
					if (distance < -mListView.getHeight() / 2) mCurrentPosition = -1;
					mListView.smoothScrollBy(distance, mScrollDuration);
					post = true;
				}
			}
			else
			{
				idle = true;
				post = true;
			}
		}
		// Fix infinite loops
		if (idle)
		{
			long time = System.currentTimeMillis();
			if (!mIdleState)
			{
				mIdleState = true;
				mIdleStart = time;
				mIdleTop = mListView.getChildAt(0).getTop();
			}
			else
			{
				if (time - mIdleStart > SCROLL_DURATION && mIdleTop == mListView.getChildAt(0).getTop()) post = false;
			}
		}
		else mIdleState = false;
		if (post)
		{
			if (postInterval > 0) mListView.postDelayed(this, postInterval); else mListView.post(this);
		}
	}
}