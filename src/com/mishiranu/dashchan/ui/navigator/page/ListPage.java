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

package com.mishiranu.dashchan.ui.navigator.page;

import android.app.Activity;
import android.content.res.Resources;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;

import com.mishiranu.dashchan.graphics.ActionIconSet;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public abstract class ListPage<Adapter extends BaseAdapter> implements PullableWrapper.PullCallback,
		BusyScrollListener.Callback {
	public static final int OPTIONS_MENU_APPEARANCE = -1;
	public static final int OPTIONS_MENU_SEARCH = -2;
	public static final int OPTIONS_MENU_SEARCH_VIEW = -3;

	public static final int APPEARANCE_MENU_CHANGE_THEME = 100;
	public static final int APPEARANCE_MENU_EXPANDED_SCREEN = 101;
	public static final int APPEARANCE_MENU_SPOILERS = 102;
	public static final int APPEARANCE_MENU_MY_POSTS = 103;
	public static final int APPEARANCE_MENU_DRAWER = 104;
	public static final int APPEARANCE_MENU_THREADS_GRID = 105;
	public static final int APPEARANCE_MENU_SFW_MODE = 106;

	private enum State {INIT, LOCKED, RESUMED, PAUSED, CONSUMED}

	public enum ViewType {LIST, PROGRESS, ERROR}

	private Activity activity;
	private Callback callback;
	private PageHolder pageHolder;
	private PullableListView listView;
	private UiManager uiManager;
	private ActionIconSet actionIconSet;

	private Adapter adapter;
	private BusyScrollListener.Callback busyScrollListenerCallback;
	private State state = State.INIT;

	public final void init(Activity activity, Callback callback, PageHolder pageHolder, PullableListView listView,
			UiManager uiManager, ActionIconSet actionIconSet) {
		if (state == State.INIT) {
			state = State.LOCKED;
			this.activity = activity;
			this.callback = callback;
			this.pageHolder = pageHolder;
			this.listView = listView;
			this.uiManager = uiManager;
			this.actionIconSet = actionIconSet;
			listView.setDivider(ResourceUtils.getDrawable(activity, android.R.attr.listDivider, 0));
			ListScroller.cancel(listView);
			onCreate();
			if (this.adapter == null) {
				throw new IllegalStateException("Adapter wasn't initialized");
			}
			state = State.RESUMED;
			performResume();
		}
	}

	protected final Activity getActivity() {
		return activity;
	}

	protected final Resources getResources() {
		return activity.getResources();
	}

	protected final String getString(int resId) {
		return activity.getString(resId);
	}

	protected final String getString(int resId, Object... formatArgs) {
		return activity.getString(resId, formatArgs);
	}

	protected final String getQuantityString(int resId, int quantity, Object... formatArgs) {
		return getResources().getQuantityString(resId, quantity, formatArgs);
	}

	protected final PageHolder getPageHolder() {
		return pageHolder;
	}

	protected final UiManager getUiManager() {
		return uiManager;
	}

	protected final Adapter getAdapter() {
		return adapter;
	}

	protected final ChanLocator getChanLocator() {
		return ChanLocator.get(pageHolder.chanName);
	}

	protected final ChanConfiguration getChanConfiguration() {
		return ChanConfiguration.get(pageHolder.chanName);
	}

	protected final PullableListView getListView() {
		return listView;
	}

	protected final void notifyAllAdaptersChanged() {
		adapter.notifyDataSetChanged();
		uiManager.dialog().notifyDataSetChangedToAll();
	}

	protected final int obtainIcon(int attr) {
		if (actionIconSet != null) {
			return actionIconSet.getId(attr);
		} else {
			return 0;
		}
	}

	protected final void initAdapter(Adapter adapter, BusyScrollListener.Callback callback) {
		if (state == State.LOCKED) {
			this.adapter = adapter;
			busyScrollListenerCallback = callback;
			uiManager.view().notifyUnbindListView(listView);
			listView.setAdapter(adapter);
		} else {
			throw new IllegalStateException("Adapter can be initialized only in onCreate method");
		}
	}

	protected final void setCustomSearchView(View view) {
		callback.setCustomSearchView(view);
	}

	protected final void notifyTitleChanged() {
		callback.notifyTitleChanged();
	}

	protected final void updateOptionsMenu(boolean recreate) {
		if (state == State.RESUMED || state == State.PAUSED) {
			callback.updateOptionsMenu(recreate);
		}
	}

	protected final void switchView(ViewType viewType, String message) {
		callback.switchView(viewType, message);
	}

	protected final void switchView(ViewType viewType, int message) {
		callback.switchView(viewType, message != 0 ? getString(message) : null);
	}

	protected final void showScaleAnimation() {
		callback.showScaleAnimation();
	}

	protected final void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber) {
		callback.handleRedirect(chanName, boardName, threadNumber, postNumber);
	}

	protected void onCreate() {}

	protected void onResume() {}

	protected void onPause() {}

	protected void onDestroy() {}

	protected void onHandleNewPostDatas() {}

	public String obtainTitle() {
		return null;
	}

	public void onItemClick(View view, int position, long id) {}

	public boolean onItemLongClick(View view, int position, long id) {
		return false;
	}

	public void onCreateOptionsMenu(Menu menu) {}

	public void onPrepareOptionsMenu(Menu menu) {}

	public boolean onOptionsItemSelected(MenuItem item) {
		return false;
	}

	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView) {}

	public boolean onContextItemSelected(MenuItem item, int position, View targetView) {
		return false;
	}

	public void onAppearanceOptionChanged(int what) {}

	public void onSearchQueryChange(String query) {}

	public boolean onSearchSubmit(String query) {
		return false;
	}

	public void onSearchCancel() {}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView) {
		if (busyScrollListenerCallback != null) {
			busyScrollListenerCallback.setListViewBusy(isBusy, listView);
		}
	}

	public int onDrawerNumberEntered(int number) {
		return 0;
	}

	public void onRequestStoreExtra() {}

	public void updatePageConfiguration(String postNumber, String threadTitle) {}

	public final boolean isDestroyed() {
		return state == State.CONSUMED;
	}

	private void performResume() {
		onResume();
		onHandleNewPostDatas();
	}

	public final void resume() {
		if (state == State.PAUSED) {
			state = State.RESUMED;
			setListViewBusy(false, listView); // Refresh list view contents
			performResume();
		}
	}

	public final void pause() {
		if (state == State.RESUMED) {
			state = State.PAUSED;
			onPause();
		}
	}

	public final void cleanup() {
		if (state == State.RESUMED || state == State.PAUSED) {
			if (state == State.RESUMED) {
				onPause();
			}
			state = State.CONSUMED;
			onDestroy();
		}
	}

	public final void handleNewPostDatasNow() {
		if (state == State.RESUMED) {
			onHandleNewPostDatas();
		}
	}

	public interface Callback {
		public void notifyTitleChanged();
		public void updateOptionsMenu(boolean recreate);
		public void setCustomSearchView(View view);
		public void switchView(ViewType viewType, String message);
		public void showScaleAnimation();
		public void handleRedirect(String chanName, String boardName, String threadNumber, String postNumber);
	}
}