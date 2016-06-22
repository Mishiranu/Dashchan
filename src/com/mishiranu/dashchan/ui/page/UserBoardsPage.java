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

import chan.content.model.Board;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadUserBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.adapter.UserBoardsAdapter;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class UserBoardsPage extends ListPage<UserBoardsAdapter> implements ReadUserBoardsTask.Callback
{
	private ReadUserBoardsTask mReadTask;
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		UserBoardsAdapter adapter = new UserBoardsAdapter(pageHolder.chanName);
		initAdapter(adapter);
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		activity.setTitle(getString(R.string.action_user_boards));
		UserBoardsExtra extra = getExtra();
		if (getExtra().boards != null)
		{
			showScaleAnimation();
			adapter.setItems(extra.boards);
			if (pageHolder.position != null) pageHolder.position.apply(getListView());
		}
		else refreshBoards(false);
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
		String boardName = getAdapter().getItem(position).boardName;
		if (boardName != null)
		{
			getUiManager().navigator().navigateBoardsOrThreads(getPageHolder().chanName, boardName, false, false);
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
				refreshBoards(!getAdapter().isEmpty());
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
		String boardName = getAdapter().getItem(position).boardName;
		if (boardName != null)
		{
			menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
			if (!FavoritesStorage.getInstance().hasFavorite(getPageHolder().chanName, boardName, null))
			{
				menu.add(0, CONTEXT_MENU_ADD_FAVORITES, 0, R.string.action_add_to_favorites);
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView)
	{
		String boardName = getAdapter().getItem(position).boardName;
		if (boardName != null)
		{
			switch (item.getItemId())
			{
				case CONTEXT_MENU_COPY_LINK:
				{
					Uri uri = getChanLocator().safe(true).createBoardUri(boardName, 0);
					if (uri != null) StringUtils.copyToClipboard(getActivity(), uri.toString());
					return true;
				}
				case CONTEXT_MENU_ADD_FAVORITES:
				{
					FavoritesStorage.getInstance().add(getPageHolder().chanName, boardName);
					return true;
				} 
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
		refreshBoards(true);
	}
	
	private void refreshBoards(boolean showPull)
	{
		if (mReadTask != null) mReadTask.cancel();
		mReadTask = new ReadUserBoardsTask(getPageHolder().chanName, this);
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
	public void onReadUserBoardsSuccess(Board[] boards)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		getExtra().boards = boards;
		getAdapter().setItems(boards);
		getListView().setSelection(0);
	}
	
	@Override
	public void onReadUserBoardsFail(ErrorItem errorItem)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().getCount() == 0) switchView(ViewType.ERROR, errorItem.toString());
		else ClickableToast.show(getActivity(), errorItem.toString());
	}
	
	public static class UserBoardsExtra implements PageHolder.Extra
	{
		public Board[] boards;
	}
	
	private UserBoardsExtra getExtra()
	{
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof UserBoardsExtra)) pageHolder.extra = new UserBoardsExtra();
		return (UserBoardsExtra) pageHolder.extra;
	}
}