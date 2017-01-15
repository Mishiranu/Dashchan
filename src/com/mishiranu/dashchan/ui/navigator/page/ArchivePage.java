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

import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import chan.content.ChanPerformer;
import chan.content.model.ThreadSummary;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadThreadSummariesTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.ArchiveAdapter;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class ArchivePage extends ListPage<ArchiveAdapter> implements ReadThreadSummariesTask.Callback {
	private ReadThreadSummariesTask readTask;
	private boolean showScaleOnSuccess;

	@Override
	protected void onCreate() {
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		if (C.API_LOLLIPOP) {
			listView.setDivider(null);
		}
		ArchiveAdapter adapter = new ArchiveAdapter();
		initAdapter(adapter, null);
		listView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		ArchiveExtra extra = getExtra();
		if (getExtra().threadSummaries != null) {
			showScaleAnimation();
			adapter.setItems(extra.threadSummaries);
			if (pageHolder.position != null) {
				pageHolder.position.apply(getListView());
			}
		} else {
			showScaleOnSuccess = true;
			refreshThreads(false, false);
		}
	}

	@Override
	protected void onDestroy() {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
	}

	@Override
	public String obtainTitle() {
		PageHolder pageHolder = getPageHolder();
		return getString(R.string.action_archive_view) + ": " + StringUtils.formatBoardTitle(pageHolder.chanName,
				pageHolder.boardName, null);
	}

	@Override
	public void onItemClick(View view, int position, long id) {
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		if (threadNumber != null) {
			PageHolder pageHolder = getPageHolder();
			getUiManager().navigator().navigatePosts(pageHolder.chanName, pageHolder.boardName, threadNumber,
					null, null, 0);
		}
	}

	private static final int OPTIONS_MENU_REFRESH = 0;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshThreads(!getAdapter().isEmpty(), false);
				return true;
			}
		}
		return false;
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView) {
		PageHolder pageHolder = getPageHolder();
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
		if (!FavoritesStorage.getInstance().hasFavorite(pageHolder.chanName, pageHolder.boardName, threadNumber)) {
			menu.add(0, CONTEXT_MENU_ADD_FAVORITES, 0, R.string.action_add_to_favorites);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView) {
		PageHolder pageHolder = getPageHolder();
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		switch (item.getItemId()) {
			case CONTEXT_MENU_COPY_LINK: {
				Uri uri = getChanLocator().safe(true).createThreadUri(pageHolder.boardName, threadNumber);
				if (uri != null) {
					StringUtils.copyToClipboard(getActivity(), uri.toString());
				}
				return true;
			}
			case CONTEXT_MENU_ADD_FAVORITES: {
				FavoritesStorage.getInstance().add(pageHolder.chanName, pageHolder.boardName, threadNumber, null, 0);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSearchQueryChange(String query) {
		getAdapter().applyFilter(query);
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshThreads(true, side == PullableWrapper.Side.BOTTOM);
	}

	private void refreshThreads(boolean showPull, boolean nextPage) {
		if (readTask != null) {
			readTask.cancel();
		}
		PageHolder pageHolder = getPageHolder();
		int pageNumber = 0;
		if (nextPage) {
			ArchiveExtra extra = getExtra();
			if (extra.threadSummaries != null) {
				pageNumber = extra.pageNumber + 1;
			}
		}
		readTask = new ReadThreadSummariesTask(pageHolder.chanName, pageHolder.boardName, pageNumber,
				ChanPerformer.ReadThreadSummariesData.TYPE_ARCHIVED_THREADS, this);
		readTask.executeOnExecutor(ReadThreadSummariesTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadThreadSummariesSuccess(ThreadSummary[] threadSummaries, int pageNumber) {
		readTask = null;
		PullableListView listView = getListView();
		listView.getWrapper().cancelBusyState();
		boolean showScale = showScaleOnSuccess;
		showScaleOnSuccess = false;
		if (pageNumber == 0 && threadSummaries == null) {
			if (getAdapter().isEmpty()) {
				switchView(ViewType.ERROR, R.string.message_empty_response);
			} else {
				ClickableToast.show(getActivity(), R.string.message_empty_response);
			}
		} else {
			switchView(ViewType.LIST, null);
			ArchiveExtra extra = getExtra();
			if (pageNumber == 0) {
				getAdapter().setItems(threadSummaries);
				extra.threadSummaries = threadSummaries;
				extra.pageNumber = 0;
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
				if (showScale) {
					showScaleAnimation();
				}
			} else {
				threadSummaries = ReadThreadSummariesTask.concatenate(extra.threadSummaries, threadSummaries);
				int oldCount = extra.threadSummaries.length;
				if (threadSummaries.length > oldCount) {
					getAdapter().setItems(threadSummaries);
					extra.threadSummaries = threadSummaries;
					extra.pageNumber = pageNumber;
					if (listView.getLastVisiblePosition() + 1 == oldCount) {
						View view = listView.getChildAt(listView.getChildCount() - 1);
						if (listView.getHeight() - listView.getPaddingBottom() - view.getBottom() >= 0) {
							ListScroller.scrollTo(getListView(), oldCount);
						}
					}
				}
			}
		}
	}

	@Override
	public void onReadThreadSummariesFail(ErrorItem errorItem) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().isEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getActivity(), errorItem.toString());
		}
	}

	public static class ArchiveExtra implements PageHolder.Extra {
		public ThreadSummary[] threadSummaries;
		public int pageNumber;
	}

	private ArchiveExtra getExtra() {
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof ArchiveExtra)) {
			pageHolder.extra = new ArchiveExtra();
		}
		return (ArchiveExtra) pageHolder.extra;
	}
}