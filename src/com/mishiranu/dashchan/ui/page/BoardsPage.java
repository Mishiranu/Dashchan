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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import chan.content.ChanConfiguration;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.adapter.BoardsAdapter;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class BoardsPage extends ListPage<BoardsAdapter> implements ReadBoardsTask.Callback
{
	private ReadBoardsTask mReadTask;
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		if (C.API_LOLLIPOP) listView.setDivider(null);
		BoardsAdapter adapter = new BoardsAdapter(pageHolder.chanName);
		initAdapter(adapter);
		adapter.update();
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		boolean hasUserBoards = getChanConfiguration().getOption(ChanConfiguration.OPTION_READ_USER_BOARDS);
		activity.setTitle(getString(hasUserBoards ? R.string.action_general_boards : R.string.action_boards));
		if (listView.getCount() != 0)
		{
			showScaleAnimation();
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
	private static final int OPTIONS_MENU_MAKE_HOME_PAGE = 1;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
		PageHolder pageHolder = getPageHolder();
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
		menu.add(0, OPTIONS_MENU_MAKE_HOME_PAGE, 0, R.string.action_make_home_page);
		menu.findItem(OPTIONS_MENU_MAKE_HOME_PAGE).setVisible(Preferences.getDefaultBoardName(pageHolder.chanName)
				!= null);
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
			case OPTIONS_MENU_MAKE_HOME_PAGE:
			{
				Preferences.setDefaultBoardName(getPageHolder().chanName, null);
				item.setVisible(false);
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
		String boardName = getAdapter().getItem(position).boardName;
		if (boardName != null)
		{
			menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
			if (!FavoritesStorage.getInstance().hasFavorite(pageHolder.chanName, boardName, null))
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
					invalidateDrawerItems(false, true);
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
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		refreshBoards(true);
	}
	
	private void refreshBoards(boolean showPull)
	{
		if (mReadTask != null) mReadTask.cancel();
		mReadTask = new ReadBoardsTask(getPageHolder().chanName, this);
		mReadTask.executeOnExecutor(ReadBoardsTask.THREAD_POOL_EXECUTOR);
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
	public void onReadBoardsSuccess(BoardCategory[] boardCategories)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		JSONArray jsonArray = null;
		if (boardCategories != null && boardCategories.length > 0)
		{
			try
			{
				for (BoardCategory boardCategory : boardCategories)
				{
					Board[] boards = boardCategory.getBoards();
					if (boards != null && boards.length > 0)
					{
						JSONObject jsonObject = new JSONObject();
						jsonObject.put(BoardsAdapter.KEY_TITLE, StringUtils.emptyIfNull(boardCategory.getTitle()));
						JSONArray boardsArray = new JSONArray();
						for (Board board : boards) boardsArray.put(board.getBoardName());
						jsonObject.put(BoardsAdapter.KEY_BOARDS, boardsArray);
						if (jsonArray == null) jsonArray = new JSONArray();
						jsonArray.put(jsonObject);
					}
				}
			}
			catch (JSONException e)
			{
				
			}
		}
		ChanConfiguration configuration = getChanConfiguration();
		configuration.storeBoards(jsonArray);
		configuration.commit();
		getAdapter().update();
		getListView().setSelection(0);
	}
	
	@Override
	public void onReadBoardsFail(ErrorItem errorItem)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().getCount() == 0) switchView(ViewType.ERROR, errorItem.toString());
		else ClickableToast.show(getActivity(), errorItem.toString());
	}
}