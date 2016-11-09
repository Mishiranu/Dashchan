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

public class BusyScrollListener implements ListView.OnScrollListener, Runnable {
	public interface Callback {
		public void setListViewBusy(boolean isBusy, AbsListView listView);
	}

	private final Callback callback;
	private final Handler handler = new Handler();

	public BusyScrollListener(Callback callback) {
		this.callback = callback;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {}

	private boolean isBusy = false, queuedIsBusy;
	private AbsListView listView;

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		boolean isBusy = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
		queuedIsBusy = isBusy;
		listView = view;
		handler.removeCallbacks(this);
		if (isBusy && !this.isBusy) {
			run();
		} else if (!isBusy && this.isBusy) {
			handler.postDelayed(this, 250);
		}
	}

	@Override
	public void run() {
		if (queuedIsBusy != isBusy) {
			isBusy = queuedIsBusy;
			callback.setListViewBusy(isBusy, listView);
		}
	}
}