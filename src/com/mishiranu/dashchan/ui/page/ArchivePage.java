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

import android.app.Activity;
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
import com.mishiranu.dashchan.async.ReadThreadSummariesTask;
import com.mishiranu.dashchan.async.ReadUserBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.adapter.ArchiveAdapter;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class ArchivePage extends ListPage<ArchiveAdapter> implements ReadThreadSummariesTask.Callback
{
	private ReadThreadSummariesTask mReadTask;
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		if (C.API_LOLLIPOP) listView.setDivider(null);
		ArchiveAdapter adapter = new ArchiveAdapter(activity);
		initAdapter(adapter);
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		activity.setTitle(getString(R.string.action_archive_view) + ": " + StringUtils
				.formatBoardTitle(pageHolder.chanName, pageHolder.boardName, null));
		ArchiveExtra extra = getExtra();
		if (getExtra().threadSummaries != null)
		{
			showScaleAnimation();
			adapter.setItems(extra.threadSummaries);
			if (pageHolder.position != null) pageHolder.position.apply(getListView());
		}
		else refreshThreads(false);
	}
	
	@Override
	protected void onDestroy()
	{
		if (mReadTask != null)
		{
			mReadTask.cancel();
			mReadTask = null;
		}
	}
	
	@Override
	public void onItemClick(View view, int position, long id)
	{
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		if (threadNumber != null)
		{
			PageHolder pageHolder = getPageHolder();
			getUiManager().navigator().navigatePosts(pageHolder.chanName, pageHolder.boardName, threadNumber,
					null, null, false);
		}
	}
	
	private static final int OPTIONS_MENU_REFRESH = 0;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case OPTIONS_MENU_REFRESH:
			{
				refreshThreads(!getAdapter().isEmpty());
				return true;
			}
		}
		return false;
	}
	
	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView)
	{
		PageHolder pageHolder = getPageHolder();
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
		if (!FavoritesStorage.getInstance().hasFavorite(pageHolder.chanName, pageHolder.boardName, threadNumber))
		{
			menu.add(0, CONTEXT_MENU_ADD_FAVORITES, 0, R.string.action_add_to_favorites);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView)
	{
		PageHolder pageHolder = getPageHolder();
		String threadNumber = getAdapter().getItem(position).getThreadNumber();
		switch (item.getItemId())
		{
			case CONTEXT_MENU_COPY_LINK:
			{
				Uri uri = getChanLocator().safe(true).createThreadUri(pageHolder.boardName, threadNumber);
				if (uri != null) StringUtils.copyToClipboard(getActivity(), uri.toString());
				return true;
			}
			case CONTEXT_MENU_ADD_FAVORITES:
			{
				FavoritesStorage.getInstance().add(pageHolder.chanName, pageHolder.boardName, threadNumber, null, 0);
				return true;
			} 
		}
		return false;
	}
	
	@Override
	public void onSearchTextChange(String newText)
	{
		getAdapter().applyFilter(newText);
	}
	
	@Override
	public boolean onCheckPullPermission(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		return true;
	}
	
	@Override
	public void onAcceptPull(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		refreshThreads(true);
	}
	
	private void refreshThreads(boolean showPull)
	{
		if (mReadTask != null) mReadTask.cancel();
		PageHolder pageHolder = getPageHolder();
		mReadTask = new ReadThreadSummariesTask(pageHolder.chanName, pageHolder.boardName,
				ChanPerformer.ReadThreadSummariesData.TYPE_ARCHIVED_THREADS, this);
		mReadTask.executeOnExecutor(ReadUserBoardsTask.THREAD_POOL_EXECUTOR);
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
	public void onReadThreadSummariesSuccess(ThreadSummary[] threadSummaries)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		getExtra().threadSummaries = threadSummaries;
		getAdapter().setItems(threadSummaries);
		getListView().setSelection(0);
	}
	
	@Override
	public void onReadThreadSummariesFail(ErrorItem errorItem)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().getCount() == 0) switchView(ViewType.ERROR, errorItem.toString());
		else ClickableToast.show(getActivity(), errorItem.toString());
	}
	
	public static class ArchiveExtra implements PageHolder.Extra
	{
		public ThreadSummary[] threadSummaries;
	}
	
	private ArchiveExtra getExtra()
	{
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof ArchiveExtra)) pageHolder.extra = new ArchiveExtra();
		return (ArchiveExtra) pageHolder.extra;
	}
}