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

package com.mishiranu.dashchan.ui.page;

import java.util.Collections;

import android.app.Activity;
import android.os.Parcel;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadSearchTask;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.UiManager;
import com.mishiranu.dashchan.ui.adapter.SearchAdapter;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class SearchPage extends ListPage<SearchAdapter> implements ReadSearchTask.Callback
{
	private ReadSearchTask mReadTask;
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		UiManager uiManager = getUiManager();
		listView.setDivider(ResourceUtils.getDrawable(activity, R.attr.postsDivider, 0));
		SearchAdapter adapter = new SearchAdapter(activity, uiManager);
		initAdapter(adapter);
		uiManager.view().setHighlightText(Collections.singleton(pageHolder.searchQuery));
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		activity.setTitle(pageHolder.searchQuery);
		SearchExtra extra = getExtra();
		if (pageHolder.initialFromCache)
		{
			adapter.setGroupMode(extra.groupMode);
			if (extra.postItems != null)
			{
				adapter.setItems(extra.postItems);
				if (pageHolder.position != null) pageHolder.position.apply(listView);
				showScaleAnimation();
			}
			else refreshSearch(false);
		}
		else
		{
			extra.groupMode = false;
			refreshSearch(false);
		}
		pageHolder.setInitialSearchData(false);
	}
	
	@Override
	protected void onDestroy()
	{
		if (mReadTask != null)
		{
			mReadTask.cancel();
			mReadTask = null;
		}
		ImageLoader.getInstance().clearTasks(getPageHolder().chanName);
	}
	
	@Override
	public void onItemClick(View view, int position, long id)
	{
		PostItem postItem = getAdapter().getPostItem(position);
		if (postItem != null)
		{
			PageHolder pageHolder = getPageHolder();
			getUiManager().navigator().navigatePosts(pageHolder.chanName, pageHolder.boardName,
					postItem.getThreadNumber(), postItem.getPostNumber(), null, false);
		}
	}
	
	@Override
	public boolean onItemLongClick(View view, int position, long id)
	{
		PostItem postItem = getAdapter().getPostItem(position);
		if (postItem != null)
		{
			return getUiManager().interaction().handlePostContextMenu(postItem, null, false, false);
		}
		return false;
	}

	private static final int OPTIONS_MENU_REFRESH = 0;
	private static final int OPTIONS_MENU_GROUP = 1;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_search).setIcon(obtainIcon(R.attr.actionSearch))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh);
		menu.add(0, OPTIONS_MENU_GROUP, 0, R.string.action_group).setCheckable(true);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu)
	{
		menu.findItem(OPTIONS_MENU_GROUP).setChecked(getAdapter().isGroupMode());
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case OPTIONS_MENU_REFRESH:
			{
				refreshSearch();
				return true;
			}
			case OPTIONS_MENU_GROUP:
			{
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
	public void onAppearanceOptionChanged(int what)
	{
		switch (what)
		{
			case APPEARANCE_MENU_SPOILERS:
			case APPEARANCE_MENU_SFW_MODE:
			{
				notifyAllAdaptersChanged();
				break;
			}
		}
	}
	
	@Override
	public boolean onStartSearch(String query)
	{
		PageHolder pageHolder = getPageHolder();
		getUiManager().navigator().navigateSearch(pageHolder.chanName, pageHolder.boardName, query);
		return false;
	}
	
	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		refreshSearch(true);
	}
	
	private void refreshSearch()
	{
		refreshSearch(!getAdapter().isEmpty());
	}
	
	private void refreshSearch(boolean showPull)
	{
		PageHolder pageHolder = getPageHolder();
		if (mReadTask != null) mReadTask.cancel();
		mReadTask = new ReadSearchTask(this, pageHolder.chanName, pageHolder.boardName, pageHolder.searchQuery);
		mReadTask.executeOnExecutor(ReadSearchTask.THREAD_POOL_EXECUTOR);
		if (showPull)
		{
			getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		}
		else
		{
			getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}
	
	@Override
	public void onReadSearchSuccess(PostItem[] postItems)
	{
		mReadTask = null;
		SearchAdapter adapter = getAdapter();
		boolean empty = adapter.isEmpty();
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		adapter.setItems(postItems);
		getListView().setSelection(0);
		getExtra().postItems = postItems;
		if (empty && !adapter.isEmpty()) showScaleAnimation();
	}
	
	@Override
	public void onReadSearchFail(ErrorItem errorItem)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.ERROR, errorItem.toString());
		getAdapter().setItems(null);
	}
	
	public static class SearchExtra implements PageHolder.ParcelableExtra
	{
		public PostItem[] postItems;
		public boolean groupMode = false;
		
		@Override
		public void writeToParcel(Parcel dest)
		{
			dest.writeInt(groupMode ? 1 : 0);
		}
		
		@Override
		public void readFromParcel(Parcel source)
		{
			groupMode = source.readInt() != 0;
		}
	}
	
	private SearchExtra getExtra()
	{
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof SearchExtra)) pageHolder.extra = new SearchExtra();
		return (SearchExtra) pageHolder.extra;
	}
}