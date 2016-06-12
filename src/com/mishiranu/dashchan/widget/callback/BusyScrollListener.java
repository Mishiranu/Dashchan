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

import android.os.Handler;
import android.widget.AbsListView;
import android.widget.ListView;

public class BusyScrollListener implements ListView.OnScrollListener, Runnable
{
	public static interface Callback
	{
		void setBusy(boolean isBusy, AbsListView view);
	}
	
	private final Callback mCallback;
	private final Handler mHandler = new Handler();
	
	public BusyScrollListener(Callback callback)
	{
		mCallback = callback;
	}
	
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		
	}
	
	private boolean mIsBusy = false, mQueuedIsBusy;
	private AbsListView mBoundView;
	
	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		boolean isBusy = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
		mQueuedIsBusy = isBusy;
		mBoundView = view;
		mHandler.removeCallbacks(this);
		if (isBusy && !mIsBusy) run(); else if (!isBusy && mIsBusy) mHandler.postDelayed(this, 250);
	}
	
	@Override
	public void run()
	{
		if (mQueuedIsBusy != mIsBusy)
		{
			mIsBusy = mQueuedIsBusy;
			mCallback.setBusy(mIsBusy, mBoundView);
		}
	}
}