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

import java.util.ArrayList;

import android.widget.AbsListView;

public class ScrollListenerComposite implements AbsListView.OnScrollListener
{
	private final ArrayList<AbsListView.OnScrollListener> mListeners = new ArrayList<>();

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		for (AbsListView.OnScrollListener listener : mListeners)
		{
			listener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		for (AbsListView.OnScrollListener listener : mListeners) listener.onScrollStateChanged(view, scrollState);
	}

	public void add(AbsListView.OnScrollListener listener)
	{
		mListeners.add(listener);
	}
}