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

package com.mishiranu.dashchan.widget.callback;

import java.util.ArrayList;

import android.widget.AbsListView;

import com.mishiranu.dashchan.R;

public class ScrollListenerComposite implements AbsListView.OnScrollListener {
	private final ArrayList<AbsListView.OnScrollListener> listeners = new ArrayList<>();

	private ScrollListenerComposite() {}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		for (AbsListView.OnScrollListener listener : listeners) {
			listener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		for (AbsListView.OnScrollListener listener : listeners) {
			listener.onScrollStateChanged(view, scrollState);
		}
	}

	public void add(AbsListView.OnScrollListener listener) {
		listeners.add(listener);
	}

	public static ScrollListenerComposite obtain(AbsListView listView) {
		ScrollListenerComposite listener = (ScrollListenerComposite) listView.getTag(R.id.scroll_view);
		if (listener == null) {
			listener = new ScrollListenerComposite();
			listView.setTag(R.id.scroll_view, listener);
			listView.setOnScrollListener(listener);
		}
		return listener;
	}
}
