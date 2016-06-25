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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.ui.adapter.HistoryAdapter;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class HistoryPage extends ListPage<HistoryAdapter>
{
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		HistoryAdapter adapter = new HistoryAdapter(activity, pageHolder.chanName);
		initAdapter(adapter);
		listView.getWrapper().setPullSides(PullableWrapper.Side.NONE);
		activity.setTitle(getString(R.string.action_history));
		if (adapter.isEmpty()) switchView(ViewType.ERROR, R.string.message_empty_history); else
		{
			showScaleAnimation();
			if (pageHolder.position != null) pageHolder.position.apply(getListView());
		}
	}
	
	@Override
	public void onItemClick(View view, int position, long id)
	{
		HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
		if (historyItem != null)
		{
			getUiManager().navigator().navigatePosts(historyItem.chanName, historyItem.boardName,
					historyItem.threadNumber, null, null, false);
		}
	}
	
	private static final int OPTIONS_MENU_CLEAR_HISTORY = 0;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_CLEAR_HISTORY, 0, R.string.action_clear_history);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case OPTIONS_MENU_CLEAR_HISTORY:
			{
				new AlertDialog.Builder(getActivity()).setMessage(R.string.message_clear_history_confirm)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						HistoryDatabase.getInstance().clearAllHistory(getPageHolder().chanName);
						getAdapter().clear();
						switchView(ViewType.ERROR, R.string.message_empty_history);
					}
				}).show();
				return true;
			}
		}
		return false;
	}
	
	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;
	private static final int CONTEXT_MENU_REMOVE_FROM_HISTORY = 2;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView)
	{
		HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
		if (historyItem != null)
		{
			menu.add(0, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
			if (!FavoritesStorage.getInstance().hasFavorite(historyItem.chanName,
					historyItem.boardName, historyItem.threadNumber))
			{
				menu.add(0, CONTEXT_MENU_ADD_FAVORITES, 0, R.string.action_add_to_favorites);
			}
			menu.add(0, CONTEXT_MENU_REMOVE_FROM_HISTORY, 0, R.string.action_remove_from_history);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView)
	{
		HistoryDatabase.HistoryItem historyItem = getAdapter().getHistoryItem(position);
		if (historyItem != null)
		{
			switch (item.getItemId())
			{
				case CONTEXT_MENU_COPY_LINK:
				{
					Uri uri = getChanLocator().safe(true).createThreadUri(historyItem.boardName,
							historyItem.threadNumber);
					if (uri != null) StringUtils.copyToClipboard(getActivity(), uri.toString());
					return true;
				}
				case CONTEXT_MENU_ADD_FAVORITES:
				{
					FavoritesStorage.getInstance().add(historyItem.chanName, historyItem.boardName,
							historyItem.threadNumber, historyItem.title, 0);
					return true;
				}
				case CONTEXT_MENU_REMOVE_FROM_HISTORY:
				{
					if (HistoryDatabase.getInstance().remove(historyItem.chanName, historyItem.boardName,
							historyItem.threadNumber))
					{
						getAdapter().remove(historyItem);
					}
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
}