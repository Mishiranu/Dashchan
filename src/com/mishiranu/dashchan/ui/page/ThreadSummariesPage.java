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
import android.graphics.drawable.Drawable;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CardView;
import android.widget.ListView;

import chan.content.ChanPerformer;
import chan.content.model.ThreadSummary;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.async.ReadThreadSummariesTask;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.ThreadSummaryItem;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.adapter.ThreadSummariesAdapter;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class ThreadSummariesPage extends ListPage<ThreadSummariesAdapter> implements ReadThreadSummariesTask.Callback,
		PullableListView.OnBeforeLayoutListener
{
	private ReadThreadSummariesTask mReadTask;
	private Drawable mOldListSelector;
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		listView.setDivider(null);
		listView.addOnBeforeLayoutListener(this);
		ThreadSummariesAdapter adapter = new ThreadSummariesAdapter(activity, pageHolder.chanName, getUiManager());
		initAdapter(adapter);
		listView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		mOldListSelector = listView.getSelector();
		listView.setSelector(android.R.color.transparent);
		String title = null;
		switch (pageHolder.content)
		{
			case POPULAR_THREADS:
			{
				title = getString(R.string.action_popular_threads);
				break;
			}
			default: break;
		}
		activity.setTitle(title);
		adapter.setGridMode(Preferences.isThreadsGridMode());
		mGridLayoutControl.apply();
		GlobalThreadsExtra extra = getExtra();
		if (extra.threadSummaries != null)
		{
			showScaleAnimation();
			adapter.setItems(extra.threadSummaries);
			if (pageHolder.position != null)
			{
				int position = adapter.getPositionFromInfo(extra.positionInfo);
				if (position != -1 && position != pageHolder.position.position)
				{
					// Fix position if grid mode was changed
					new ListPosition(position, pageHolder.position.y).apply(listView);
				}
				else pageHolder.position.apply(listView);
			}
		}
		else refreshGlobalThreads(false);
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
		PullableListView listView = getListView();
		listView.setSelector(mOldListSelector);
		listView.removeOnBeforeLayoutListener(this);
	}
	
	private ThreadSummaryItem getThreadSummaryItem(View clickableView, int position)
	{
		ThreadSummaryItem[] threadSummaryItems = getAdapter().getItem(position);
		ThreadSummaryItem threadSummaryItem;
		if (threadSummaryItems.length > 1)
		{
			View child = (View) clickableView.getParent();
			if (child instanceof CardView) child = (View) child.getParent();
			ViewGroup viewGroup = (ViewGroup) child.getParent();
			int index = viewGroup.indexOfChild(child);
			threadSummaryItem = threadSummaryItems[index];
		}
		else threadSummaryItem = threadSummaryItems[0];
		return threadSummaryItem;
	}
	
	@Override
	public void onItemClick(View view, int position, long id)
	{
		ThreadSummaryItem threadSummaryItem = getThreadSummaryItem(view, position);
		getUiManager().navigator().navigatePosts(getPageHolder().chanName, threadSummaryItem.getBoardName(),
				threadSummaryItem.getThreadNumber(), null, null, false);
	}
	
	private static final int OPTIONS_MENU_REFRESH = 0;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
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
				refreshGlobalThreads(!getAdapter().isEmpty());
				return true;
			}
		}
		return false;
	}
	
	private static final int CONTEXT_MENU_COPY_LINK = 0;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView)
	{
		menu.add(Menu.NONE, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView)
	{
		ThreadSummaryItem threadSummaryItem = getThreadSummaryItem(targetView, position);
		switch (item.getItemId())
		{
			case CONTEXT_MENU_COPY_LINK:
			{
				StringUtils.copyToClipboard(getActivity(), getChanLocator().createThreadUri(threadSummaryItem
						.getBoardName(), threadSummaryItem.getThreadNumber()).toString());
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
			case APPEARANCE_MENU_THREADS_GRID:
			{
				boolean gridMode = Preferences.isThreadsGridMode();
				mGridLayoutControl.applyGridMode(gridMode);
				break;
			}
			case APPEARANCE_MENU_SFW_MODE:
			{
				notifyAllAdaptersChanged();
				break;
			}
		}
	}
	
	@Override
	public void onBeforeLayout(View v, int left, int top, int right, int bottom)
	{
		int width = right - left;
		mGridLayoutControl.onListLayout(width);
	}
	
	private final ThreadsPage.GridLayoutControl mGridLayoutControl = new ThreadsPage.GridLayoutControl()
	{
		@Override
		protected ListView getListView()
		{
			return ThreadSummariesPage.this.getListView();
		}
		
		@Override
		protected boolean isGridMode()
		{
			return getAdapter().isGridMode();
		}
		
		@Override
		protected void setGridMode(boolean gridMode)
		{
			getAdapter().setGridMode(gridMode);
		}
		
		@Override
		protected String getPositionInfo(int position)
		{
			return getAdapter().getPositionInfo(position);
		}
		
		@Override
		protected int getPositionFromInfo(String positionInfo)
		{
			return getAdapter().getPositionFromInfo(positionInfo);
		}
		
		@Override
		protected void updateConfiguration(int listViewWidth)
		{
			getAdapter().updateConfiguration(listViewWidth);
		}
	};
	
	@Override
	public void onRequestStoreExtra()
	{
		PageHolder pageHolder = getPageHolder();
		ThreadSummariesAdapter adapter = getAdapter();
		GlobalThreadsExtra extra = getExtra();
		extra.positionInfo = pageHolder.position != null ? adapter.getPositionInfo(pageHolder.position.position) : null;
	}
	
	@Override
	public boolean onCheckPullPermission(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		return true;
	}

	@Override
	public void onAcceptPull(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		refreshGlobalThreads(true);
	}
	
	private void refreshGlobalThreads(boolean showPull)
	{
		PageHolder pageHolder = getPageHolder();
		if (mReadTask != null) mReadTask.cancel();
		int type = -1;
		switch (pageHolder.content)
		{
			case POPULAR_THREADS:
			{
				type = ChanPerformer.ReadThreadSummariesData.TYPE_POPULAR_THREADS;
				break;
			}
			default: break;
		}
		mReadTask = new ReadThreadSummariesTask(pageHolder.chanName, null, type, this);
		mReadTask.executeOnExecutor(ReadThreadSummariesTask.THREAD_POOL_EXECUTOR);
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
	
	public static class GlobalThreadsExtra implements PageHolder.Extra
	{
		public ThreadSummary[] threadSummaries;
		public String positionInfo;
	}
	
	private GlobalThreadsExtra getExtra()
	{
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof GlobalThreadsExtra)) pageHolder.extra = new GlobalThreadsExtra();
		return (GlobalThreadsExtra) pageHolder.extra;
	}
}