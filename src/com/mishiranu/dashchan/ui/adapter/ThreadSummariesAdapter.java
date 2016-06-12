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

package com.mishiranu.dashchan.ui.adapter;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import chan.content.model.ThreadSummary;

import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ThreadSummaryItem;
import com.mishiranu.dashchan.ui.UiManager;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class ThreadSummariesAdapter extends BaseAdapter implements BusyScrollListener.Callback
{
	private static final int ITEM_VIEW_TYPE_THREAD = 0;
	private static final int ITEM_VIEW_TYPE_THREAD_GRID = 1;
	
	private final Context mContext;
	private final String mChanName;
	private final UiManager mUiManager;
	
	private final ArrayList<ThreadSummaryItem> mThreadSummaryItems = new ArrayList<>(); 
	
	private boolean mBusy = false;
	
	private boolean mGridMode = false;
	private int mGridRowCount = 1;
	private int mGridItemContentHeight;
	
	public ThreadSummariesAdapter(Context context, String chanName, UiManager uiManager)
	{
		mContext = context;
		mChanName = chanName;
		mUiManager = uiManager;
	}
	
	public void setGridMode(boolean gridMode)
	{
		if (mGridMode != gridMode)
		{
			mGridMode = gridMode;
			if (mThreadSummaryItems.size() > 0) notifyDataSetChanged();
		}
	}
	
	public boolean isGridMode()
	{
		return mGridMode;
	}
	
	public String getPositionInfo(int position)
	{
		return Integer.toString(mGridMode ? position * mGridRowCount : position);
	}
	
	public int getPositionFromInfo(String positionInfo)
	{
		if (positionInfo != null)
		{
			int index = Integer.parseInt(positionInfo);
			return mGridMode ? index / mGridRowCount : index;
		}
		return -1;
	}
	
	public void updateConfiguration(int listViewWidth)
	{
		Pair<Integer, Integer> configuration = ThreadsAdapter.obtainGridConfiguration(mContext, listViewWidth,
				mGridRowCount, mGridItemContentHeight);
		if (configuration != null)
		{
			mGridRowCount = configuration.first;
			mGridItemContentHeight = configuration.second;
			if (mGridMode && mThreadSummaryItems.size() > 0) notifyDataSetChanged();
		}
	}
	
	@Override
	public int getViewTypeCount()
	{
		return 2;
	}
	
	@Override
	public int getItemViewType(int position)
	{
		return mGridMode ? ITEM_VIEW_TYPE_THREAD_GRID : ITEM_VIEW_TYPE_THREAD;
	}
	
	@Override
	public int getCount()
	{
		int count = mThreadSummaryItems.size();
		if (mGridMode)
		{
			int rowCount = mGridRowCount;
			count = (count + rowCount - 1) / rowCount;
		}
		return count;
	}
	
	private ThreadSummaryItem[] mFillArray;
	
	@Override
	public ThreadSummaryItem[] getItem(int position)
	{
		int rowCount = mGridMode ? mGridRowCount : 1;
		if (mFillArray == null || mFillArray.length != rowCount) mFillArray = new ThreadSummaryItem[rowCount];
		for (int i = 0; i < rowCount; i++) mFillArray[i] = null;
		int from = position * rowCount;
		int to = Math.min(from + rowCount, mThreadSummaryItems.size());
		for (int i = from, j = 0; i < to; i++, j++) mFillArray[j] = mThreadSummaryItems.get(i);
		return mFillArray;
	}
	
	@Override
	public long getItemId(int position)
	{
		return 0;
	}
	
	@SuppressLint("InflateParams")
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		if (mGridMode)
		{
			convertView = mGridBuilder.getView(mContext, mUiManager, convertView, parent, getItem(position), position,
					getCount(), mGridRowCount);
		}
		else
		{
			ThreadSummaryItem threadSummaryItem = getItem(position)[0];
			convertView = mUiManager.view().getThreadView(threadSummaryItem, convertView, parent, mChanName, mBusy);
			ViewUtils.applyCardHolderPadding(convertView, null, position == 0, position == getCount() - 1, false);
		}
		return convertView;
	}
	
	private final ThreadsAdapter.GridBuilder<ThreadSummaryItem> mGridBuilder = new ThreadsAdapter
			.GridBuilder<ThreadSummaryItem>()
	{
		@Override
		public View getViewChild(ThreadSummaryItem threadSummaryItem, View convertViewChild, ViewGroup parent)
		{
			return mUiManager.view().getThreadViewForGrid(threadSummaryItem, convertViewChild,
					parent, mChanName, mGridItemContentHeight, mBusy);
		}
	};
	
	public void setItems(ThreadSummary[] threadSummaries)
	{
		mThreadSummaryItems.clear();
		if (threadSummaries != null)
		{
			for (ThreadSummary threadSummary : threadSummaries)
			{
				mThreadSummaryItems.add(new ThreadSummaryItem(threadSummary, mChanName));
			}
		}
		notifyDataSetChanged();
	}
	
	@Override
	public void setBusy(boolean isBusy, AbsListView view)
	{
		if (!isBusy)
		{
			int count = view.getChildCount();
			for (int i = 0; i < count; i++)
			{
				View v = view.getChildAt(i);
				int position = view.getPositionForView(v);
				ThreadSummaryItem[] threadSummaryItems = getItem(position);
				for (int j = 0; j < threadSummaryItems.length; j++)
				{
					ThreadSummaryItem threadSummaryItem = threadSummaryItems[j];
					if (threadSummaryItem != null)
					{
						List<AttachmentItem> attachmentItems = threadSummaryItem.getAttachmentItems();
						if (attachmentItems != null)
						{
							mUiManager.view().displayThumbnail(mGridMode ? ((ViewGroup) v).getChildAt(j) : v,
									attachmentItems, false);
						}
					}
				}
			}
		}
		mBusy = isBusy;
	}
}