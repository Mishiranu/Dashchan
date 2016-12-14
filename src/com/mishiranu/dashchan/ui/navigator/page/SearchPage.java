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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.async.ReadSearchTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.adapter.SearchAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class SearchPage extends ListPage<SearchAdapter> implements ReadSearchTask.Callback, ImageLoader.Observer {
	private ReadSearchTask readTask;
	private boolean showScaleOnSuccess;

	@Override
	protected void onCreate() {
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		UiManager uiManager = getUiManager();
		listView.setDivider(ResourceUtils.getDrawable(getActivity(), R.attr.postsDivider, 0));
		SearchAdapter adapter = new SearchAdapter(uiManager);
		initAdapter(adapter, adapter);
		ImageLoader.getInstance().observable().register(this);
		uiManager.view().setHighlightText(Collections.singleton(pageHolder.searchQuery));
		listView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		SearchExtra extra = getExtra();
		if (pageHolder.initialFromCache) {
			adapter.setGroupMode(extra.groupMode);
			if (!extra.postItems.isEmpty()) {
				adapter.setItems(extra.postItems);
				if (pageHolder.position != null) {
					pageHolder.position.apply(listView);
				}
				showScaleAnimation();
			} else {
				showScaleOnSuccess = true;
				refreshSearch(false, false);
			}
		} else {
			extra.groupMode = false;
			showScaleOnSuccess = true;
			refreshSearch(false, false);
		}
		pageHolder.setInitialSearchData(false);
	}

	@Override
	protected void onDestroy() {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
		ImageLoader.getInstance().observable().unregister(this);
		ImageLoader.getInstance().clearTasks(getPageHolder().chanName);
	}

	@Override
	public String obtainTitle() {
		return getPageHolder().searchQuery;
	}

	@Override
	public void onItemClick(View view, int position, long id) {
		PostItem postItem = getAdapter().getPostItem(position);
		if (postItem != null) {
			PageHolder pageHolder = getPageHolder();
			getUiManager().navigator().navigatePosts(pageHolder.chanName, pageHolder.boardName,
					postItem.getThreadNumber(), postItem.getPostNumber(), null, false);
		}
	}

	@Override
	public boolean onItemLongClick(View view, int position, long id) {
		PostItem postItem = getAdapter().getPostItem(position);
		return postItem != null && getUiManager().interaction().handlePostContextMenu(postItem, null, false, false);
	}

	private static final int OPTIONS_MENU_REFRESH = 0;
	private static final int OPTIONS_MENU_GROUP = 1;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_search).setIcon(obtainIcon(R.attr.actionSearch))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh);
		menu.add(0, OPTIONS_MENU_GROUP, 0, R.string.action_group).setCheckable(true);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(OPTIONS_MENU_GROUP).setChecked(getAdapter().isGroupMode());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshSearch(!getAdapter().isEmpty(), false);
				return true;
			}
			case OPTIONS_MENU_GROUP: {
				SearchAdapter adapter = getAdapter();
				boolean groupMode = !adapter.isGroupMode();
				adapter.setGroupMode(groupMode);
				getExtra().groupMode = groupMode;
				return true;
			}
		}
		return false;
	}

	@Override
	public void onAppearanceOptionChanged(int what) {
		switch (what) {
			case APPEARANCE_MENU_SPOILERS:
			case APPEARANCE_MENU_SFW_MODE: {
				notifyAllAdaptersChanged();
				break;
			}
		}
	}

	@Override
	public boolean onSearchSubmit(String query) {
		PageHolder pageHolder = getPageHolder();
		getUiManager().navigator().navigateSearch(pageHolder.chanName, pageHolder.boardName, query);
		return true;
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshSearch(true, side == PullableWrapper.Side.BOTTOM);
	}

	private void refreshSearch(boolean showPull, boolean nextPage) {
		PageHolder pageHolder = getPageHolder();
		if (readTask != null) {
			readTask.cancel();
		}
		int pageNumber = 0;
		if (nextPage) {
			SearchExtra extra = getExtra();
			if (!extra.postItems.isEmpty()) {
				pageNumber = extra.pageNumber + 1;
			}
		}
		readTask = new ReadSearchTask(this, pageHolder.chanName, pageHolder.boardName, pageHolder.searchQuery,
				pageNumber);
		readTask.executeOnExecutor(ReadSearchTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadSearchSuccess(ArrayList<PostItem> postItems, int pageNumber) {
		readTask = null;
		PullableListView listView = getListView();
		listView.getWrapper().cancelBusyState();
		SearchAdapter adapter = getAdapter();
		boolean showScale = showScaleOnSuccess;
		showScaleOnSuccess = false;
		SearchExtra extra = getExtra();
		if (pageNumber == 0 && (postItems == null || postItems.isEmpty())) {
			switchView(ViewType.ERROR, R.string.message_not_found);
			adapter.setItems(null);
			extra.postItems.clear();
		} else {
			switchView(ViewType.LIST, null);
			if (pageNumber == 0) {
				adapter.setItems(postItems);
				extra.postItems.clear();
				extra.postItems.addAll(postItems);
				extra.pageNumber = 0;
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
				if (showScale) {
					showScaleAnimation();
				}
			} else {
				HashSet<String> existingPostNumbers = new HashSet<>();
				for (PostItem postItem : extra.postItems) {
					existingPostNumbers.add(postItem.getPostNumber());
				}
				if (postItems != null) {
					for (PostItem postItem : postItems) {
						if (!existingPostNumbers.contains(postItem.getPostNumber())) {
							extra.postItems.add(postItem);
						}
					}
				}
				if (extra.postItems.size() > existingPostNumbers.size()) {
					int count = listView.getCount();
					boolean fromGroupMode = adapter.isGroupMode();
					adapter.setItems(null);
					adapter.setGroupMode(false);
					adapter.setItems(extra.postItems);
					extra.pageNumber = pageNumber;
					if (listView.getLastVisiblePosition() + 1 == count) {
						View view = listView.getChildAt(listView.getChildCount() - 1);
						if (listView.getHeight() - listView.getPaddingBottom() - view.getBottom() >= 0) {
							if (fromGroupMode) {
								final int firstNewIndex = existingPostNumbers.size();
								listView.post(() -> {
									if (isDestroyed()) {
										return;
									}
									getListView().setSelection(Math.max(firstNewIndex - 8, 0));
									getListView().post(() -> {
										if (!isDestroyed()) {
											ListScroller.scrollTo(getListView(), firstNewIndex);
										}
									});
								});
							} else {
								ListScroller.scrollTo(getListView(), existingPostNumbers.size());
							}
						}
					}
				} else {
					ClickableToast.show(getActivity(), R.string.message_search_completed);
				}
			}
		}
	}

	@Override
	public void onReadSearchFail(ErrorItem errorItem) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().isEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getActivity(), errorItem.toString());
		}
	}

	@Override
	public void onImageLoadComplete(String key, Bitmap bitmap, boolean error) {
		getUiManager().view().displayLoadedThumbnailsForPosts(getListView(), key, bitmap, error);
	}

	public static class SearchExtra implements PageHolder.ParcelableExtra {
		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public int pageNumber;
		public boolean groupMode = false;

		@Override
		public void writeToParcel(Parcel dest) {
			dest.writeInt(groupMode ? 1 : 0);
		}

		@Override
		public void readFromParcel(Parcel source) {
			groupMode = source.readInt() != 0;
		}
	}

	private SearchExtra getExtra() {
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof SearchExtra)) {
			pageHolder.extra = new SearchExtra();
		}
		return (SearchExtra) pageHolder.extra;
	}
}