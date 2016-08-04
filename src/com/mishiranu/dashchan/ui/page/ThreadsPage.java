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

import java.net.HttpURLConnection;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.model.Threads;
import chan.http.HttpValidator;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.app.service.PostingService;
import com.mishiranu.dashchan.async.ReadThreadsTask;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HiddenThreadsDatabase;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.DrawerManager;
import com.mishiranu.dashchan.ui.UiManager;
import com.mishiranu.dashchan.ui.adapter.ThreadsAdapter;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class ThreadsPage extends ListPage<ThreadsAdapter> implements FavoritesStorage.Observer, UiManager.Observer,
		ReadThreadsTask.Callback, PullableListView.OnBeforeLayoutListener
{
	private ReadThreadsTask mReadTask;
	
	private Drawable mOldListSelector;
	
	private int mLastPage;
	
	@Override
	protected void onCreate()
	{
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		UiManager uiManager = getUiManager();
		listView.setDivider(null);
		listView.addOnBeforeLayoutListener(this);
		ThreadsAdapter adapter = new ThreadsAdapter(activity, pageHolder.chanName, pageHolder.boardName, uiManager);
		initAdapter(adapter, adapter);
		listView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		mOldListSelector = listView.getSelector();
		listView.setSelector(android.R.color.transparent);
		uiManager.observable().register(this);
		ThreadsExtra extra = getExtra();
		adapter.applyAttributesBeforeFill(extra.headerExpanded, extra.catalogSortIndex,
				Preferences.isThreadsGridMode());
		mGridLayoutControl.apply();
		ChanConfiguration.Board board = getChanConfiguration().safe().obtainBoard(pageHolder.boardName);
		mLastPage = board.allowCatalog && Preferences.isLoadCatalog(pageHolder.chanName) ? PAGE_NUMBER_CATALOG : 0;
		if (pageHolder.initialFromCache && extra.cachedThreads != null && extra.cachedPostItems.size() > 0)
		{
			mLastPage = extra.cachedThreads.getLastPage();
			getAdapter().setItems(CommonUtils.toArray(extra.cachedPostItems, PostItem[].class),
					extra.cachedThreads.getStartPage(), extra.cachedThreads);
			showScaleAnimation();
			if (pageHolder.position != null)
			{
				int position = getAdapter().getPositionFromInfo(extra.positionInfo);
				if (position != -1 && position != pageHolder.position.position)
				{
					// Fix position if grid mode was changed
					new ListPosition(position, pageHolder.position.y).apply(listView);
				}
				else pageHolder.position.apply(listView);
			}
		}
		else refreshThreads(RefreshPage.CURRENT, false);
		FavoritesStorage.getInstance().getObservable().register(this);
		String boardTitle = getChanConfiguration().getBoardTitle(pageHolder.boardName);
		updateTitle(boardTitle);
		pageHolder.setInitialThreadsData(false);
	}
	
	@Override
	protected void onDestroy()
	{
		getUiManager().observable().unregister(this);
		if (mReadTask != null)
		{
			mReadTask.cancel();
			mReadTask = null;
		}
		ImageLoader.getInstance().clearTasks(getPageHolder().chanName);
		FavoritesStorage.getInstance().getObservable().unregister(this);
		PullableListView listView = getListView();
		listView.setSelector(mOldListSelector);
		listView.removeOnBeforeLayoutListener(this);
	}
	
	@Override
	protected void onHandleNewPostDatas()
	{
		PageHolder pageHolder = getPageHolder();
		PostingService.NewPostData newPostData = PostingService.obtainNewThreadData(getActivity(), pageHolder.chanName,
				pageHolder.boardName);
		if (newPostData != null)
		{
			getUiManager().navigator().navigatePosts(newPostData.chanName, newPostData.boardName,
					newPostData.threadNumber, newPostData.postNumber, null, false);
		}
	}
	
	private void updateTitle(String title)
	{
		PageHolder pageHolder = getPageHolder();
		if (title != null) invalidateDrawerItems(true, true);
		getActivity().setTitle(StringUtils.formatBoardTitle(pageHolder.chanName, pageHolder.boardName, title));
	}
	
	@Override
	public void onItemClick(View view, int position, long id)
	{
		ThreadsAdapter adapter = getAdapter();
		PostItem postItem = getUiManager().getPostItemFromHolder(view);
		if (postItem != null)
		{
			PageHolder pageHolder = getPageHolder();
			if (postItem.isHiddenUnchecked())
			{
				HiddenThreadsDatabase.getInstance().set(pageHolder.chanName, pageHolder.boardName,
						postItem.getThreadNumber(), false);
				postItem.invalidateHidden();
				adapter.notifyDataSetChanged();
			}
			else
			{
				getUiManager().navigator().navigatePosts(pageHolder.chanName, pageHolder.boardName,
						postItem.getThreadNumber(), null, postItem.getSubjectOrComment(), false);
			}
		}
	}
	
	private boolean mAllowSearch = false;
	
	private static final int OPTIONS_MENU_REFRESH = 0;
	private static final int OPTIONS_MENU_CATALOG = 1;
	private static final int OPTIONS_MENU_PAGES = 2;
	private static final int OPTIONS_MENU_ARCHIVE = 3;
	private static final int OPTIONS_MENU_NEW_THREAD = 4;
	private static final int OPTIONS_MENU_ADD_TO_FAVORITES_TEXT = 5;
	private static final int OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT = 6;
	private static final int OPTIONS_MENU_ADD_TO_FAVORITES_ICON = 7;
	private static final int OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON = 8;
	private static final int OPTIONS_MENU_MAKE_HOME_PAGE = 9;
	
	@Override
	public void onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.actionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_search)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_CATALOG, 0, R.string.action_catalog);
		menu.add(0, OPTIONS_MENU_PAGES, 0, R.string.action_pages);
		menu.add(0, OPTIONS_MENU_ARCHIVE, 0, R.string.action_archive_view);
		menu.add(0, OPTIONS_MENU_NEW_THREAD, 0, R.string.action_new_thread);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
		menu.add(0, OPTIONS_MENU_ADD_TO_FAVORITES_TEXT, 0, R.string.action_add_to_favorites);
		menu.add(0, OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT, 0, R.string.action_remove_from_favorites);
		menu.add(0, OPTIONS_MENU_ADD_TO_FAVORITES_ICON, 0, R.string.action_add_to_favorites)
				.setIcon(obtainIcon(R.attr.actionAddToFavorites)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON, 0, R.string.action_remove_from_favorites)
				.setIcon(obtainIcon(R.attr.actionRemoveFromFavorites)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_MAKE_HOME_PAGE, 0, R.string.action_make_home_page);
	}
	
	@Override
	public void onPrepareOptionsMenu(Menu menu)
	{
		PageHolder pageHolder = getPageHolder();
		ChanConfiguration configuration = getChanConfiguration();
		ChanConfiguration.Board board = configuration.safe().obtainBoard(pageHolder.boardName);
		boolean search = board.allowSearch;
		boolean catalog = board.allowCatalog;
		boolean catalogSearch = catalog && board.allowCatalogSearch;
		boolean canSearch = search || catalogSearch;
		mAllowSearch = canSearch;
		menu.findItem(OPTIONS_MENU_SEARCH).setTitle(canSearch ? R.string.action_search : R.string.action_filter);
		menu.findItem(OPTIONS_MENU_CATALOG).setVisible(catalog && mLastPage != PAGE_NUMBER_CATALOG);
		menu.findItem(OPTIONS_MENU_PAGES).setVisible(catalog && mLastPage == PAGE_NUMBER_CATALOG);
		menu.findItem(OPTIONS_MENU_ARCHIVE).setVisible(board.allowArchive);
		menu.findItem(OPTIONS_MENU_NEW_THREAD).setVisible(board.allowPosting);
		boolean singleBoardMode = configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE);
		boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(pageHolder.chanName,
				pageHolder.boardName, null);
		boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
		menu.findItem(OPTIONS_MENU_ADD_TO_FAVORITES_TEXT).setVisible(!iconFavorite && !isFavorite && !singleBoardMode);
		menu.findItem(OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT).setVisible(!iconFavorite && isFavorite);
		menu.findItem(OPTIONS_MENU_ADD_TO_FAVORITES_ICON).setVisible(iconFavorite && !isFavorite && !singleBoardMode);
		menu.findItem(OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON).setVisible(iconFavorite && isFavorite);
		menu.findItem(OPTIONS_MENU_MAKE_HOME_PAGE).setVisible(!singleBoardMode &&
				!StringUtils.equals(pageHolder.boardName, Preferences.getDefaultBoardName(pageHolder.chanName)));
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		PageHolder pageHolder = getPageHolder();
		switch (item.getItemId())
		{
			case OPTIONS_MENU_REFRESH:
			{
				refreshThreads(RefreshPage.CURRENT);
				return true;
			}
			case OPTIONS_MENU_CATALOG:
			{
				loadThreadsPage(PAGE_NUMBER_CATALOG, false);
				return true;
			}
			case OPTIONS_MENU_PAGES:
			{
				loadThreadsPage(0, false);
				return true;
			}
			case OPTIONS_MENU_ARCHIVE:
			{
				getUiManager().navigator().navigateArchive(pageHolder.chanName, pageHolder.boardName);
				return true;
			}
			case OPTIONS_MENU_NEW_THREAD:
			{
				getUiManager().navigator().navigatePosting(pageHolder.chanName, pageHolder.boardName, null);
				return true;
			}
			case OPTIONS_MENU_ADD_TO_FAVORITES_TEXT:
			case OPTIONS_MENU_ADD_TO_FAVORITES_ICON:
			{
				FavoritesStorage.getInstance().add(pageHolder.chanName, pageHolder.boardName);
				return true;
			}
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT:
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON:
			{
				FavoritesStorage.getInstance().remove(pageHolder.chanName, pageHolder.boardName, null);
				return true;
			}
			case OPTIONS_MENU_MAKE_HOME_PAGE:
			{
				Preferences.setDefaultBoardName(pageHolder.chanName, pageHolder.boardName);
				item.setVisible(false);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action)
	{
		switch (action)
		{
			case FavoritesStorage.ACTION_ADD:
			case FavoritesStorage.ACTION_REMOVE:
			{
				PageHolder pageHolder = getPageHolder();
				if (favoriteItem.equals(pageHolder.chanName, pageHolder.boardName, null))
				{
					updateOptionsMenu(false);
				}
				break;
			}
		}
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_SHARE_LINK = 1;
	private static final int CONTEXT_MENU_HIDE = 2;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView)
	{
		PostItem postItem = getUiManager().getPostItemFromHolder(targetView);
		if (postItem != null)
		{
			menu.add(Menu.NONE, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
			menu.add(Menu.NONE, CONTEXT_MENU_SHARE_LINK, 0, R.string.action_share_link);
			if (!postItem.isHiddenUnchecked()) menu.add(Menu.NONE, CONTEXT_MENU_HIDE, 0, R.string.action_hide);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView)
	{
		PageHolder pageHolder = getPageHolder();
		ThreadsAdapter adapter = getAdapter();
		PostItem postItem = getUiManager().getPostItemFromHolder(targetView);
		if (postItem != null)
		{
			switch (item.getItemId())
			{
				case CONTEXT_MENU_COPY_LINK:
				{
					Uri uri = getChanLocator().safe(true).createThreadUri(pageHolder.boardName,
							postItem.getThreadNumber());
					if (uri != null) StringUtils.copyToClipboard(getActivity(), uri.toString());
					return true;
				}
				case CONTEXT_MENU_SHARE_LINK:
				{
					Context context = getActivity();
					NavigationUtils.share(context, pageHolder.chanName, pageHolder.boardName,
							postItem.getThreadNumber(), null, postItem.getSubjectOrComment(), null);
					return true;
				}
				case CONTEXT_MENU_HIDE:
				{
					HiddenThreadsDatabase.getInstance().set(pageHolder.chanName, pageHolder.boardName,
							postItem.getThreadNumber(), true);
					postItem.invalidateHidden();
					adapter.notifyDataSetChanged();
					return true;
				}
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
			case APPEARANCE_MENU_THREADS_GRID:
			{
				boolean gridMode = Preferences.isThreadsGridMode();
				mGridLayoutControl.applyGridMode(gridMode);
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
	
	private final GridLayoutControl mGridLayoutControl = new GridLayoutControl();
	
	private class GridLayoutControl implements Runnable
	{
		private int mCurrentWidth;
		private ListPosition mListPosition;
		private String mPositionInfo;
		
		public void applyGridMode(boolean gridMode)
		{
			ListView listView = getListView();
			ListPosition listPosition = ListPosition.obtain(listView);
			String positionInfo = getAdapter().getPositionInfo(listPosition.position);
			getAdapter().setGridMode(gridMode);
			Preferences.setThreadsGridMode(gridMode);
			int position = getAdapter().getPositionFromInfo(positionInfo);
			if (position != -1) new ListPosition(position, listPosition.y).apply(listView);
		}
		
		public void apply()
		{
			ListView listView = getListView();
			listView.removeCallbacks(this);
			mListPosition = null;
			mPositionInfo = null;
			if (listView.getWidth() > 0) run(); else listView.post(this);
		}
		
		public void onListLayout(int width)
		{
			if (mCurrentWidth != width)
			{
				mCurrentWidth = width;
				ListView listView = getListView();
				listView.removeCallbacks(this);
				boolean gridMode = getAdapter().isGridMode();
				mListPosition = gridMode ? ListPosition.obtain(listView) : null;
				mPositionInfo = gridMode ? getAdapter().getPositionInfo(mListPosition.position) : null;
				listView.post(this);
			}
		}
		
		@Override
		public void run()
		{
			ListView listView = getListView();
			listView.removeCallbacks(this);
			getAdapter().updateConfiguration(listView.getWidth());
			if (mPositionInfo != null)
			{
				int position = getAdapter().getPositionFromInfo(mPositionInfo);
				if (position != -1 && position != mListPosition.position)
				{
					// Fix list position due to rows count changing
					new ListPosition(position, mListPosition.y).apply(listView);
				}
			}
			else mCurrentWidth = listView.getWidth();
		}
	}
	
	@Override
	public boolean onStartSearch(String query)
	{
		if (mAllowSearch)
		{
			PageHolder pageHolder = getPageHolder();
			getUiManager().navigator().navigateSearch(pageHolder.chanName, pageHolder.boardName, query);
			return false;
		}
		else return super.onStartSearch(query);
	}
	
	@Override
	public int onDrawerNumberEntered(int number)
	{
		int result = 0;
		if (number >= 0)
		{
			// loadDesiredThreadsPage will leave error message, if number is incorrect
			result |= DrawerManager.RESULT_REMOVE_ERROR_MESSAGE;
			if (loadThreadsPage(number, false)) result |= DrawerManager.RESULT_SUCCESS;
		}
		return result;
	}
	
	@Override
	public void onRequestStoreExtra()
	{
		PageHolder pageHolder = getPageHolder();
		ThreadsAdapter adapter = getAdapter();
		ThreadsExtra extra = getExtra();
		extra.headerExpanded = adapter.isHeaderExpanded();
		extra.catalogSortIndex = adapter.getCatalogSortIndex();
		extra.positionInfo = pageHolder.position != null ? adapter.getPositionInfo(pageHolder.position.position) : null;
	}
	
	@Override
	public void onSearchTextChange(String newText)
	{
		getAdapter().applyFilter(newText);
	}
	
	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side)
	{
		refreshThreads(getAdapter().isRealEmpty() || mLastPage == PAGE_NUMBER_CATALOG ? RefreshPage.CURRENT
				: side == PullableWrapper.Side.BOTTOM ? RefreshPage.NEXT : RefreshPage.PREVIOUS, true);
	}
	
	private enum RefreshPage {CURRENT, PREVIOUS, NEXT, CATALOG}
	
	private static final int PAGE_NUMBER_CATALOG = ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG;
	
	private void refreshThreads(RefreshPage refreshPage)
	{
		refreshThreads(refreshPage, !getAdapter().isRealEmpty());
	}
	
	private void refreshThreads(RefreshPage refreshPage, boolean showPull)
	{
		if (mReadTask != null) mReadTask.cancel();
		int page;
		boolean append = false;
		if (refreshPage == RefreshPage.CATALOG || refreshPage == RefreshPage.CURRENT &&
				mLastPage == PAGE_NUMBER_CATALOG)
		{
			page = PAGE_NUMBER_CATALOG;
		}
		else
		{
			boolean pageByPage = Preferences.isPageByPage();
			int currentPage = mLastPage;
			if (pageByPage) 
			{
				page = refreshPage == RefreshPage.NEXT ? currentPage + 1 : refreshPage == RefreshPage.PREVIOUS
						? currentPage - 1 : currentPage;
				if (page < 0) page = 0;
			}
			else
			{
				page = refreshPage == RefreshPage.NEXT && currentPage >= 0 ? currentPage + 1 : 0;
				if (page != 0) append = true;
			}
		}
		loadThreadsPage(page, append, showPull);
	}
	
	private boolean loadThreadsPage(int page, boolean append)
	{
		return loadThreadsPage(page, append, !getAdapter().isRealEmpty());
	}
	
	private boolean loadThreadsPage(int page, boolean append, boolean showPull)
	{
		if (mReadTask != null) mReadTask.cancel();
		PageHolder pageHolder = getPageHolder();
		if (page < PAGE_NUMBER_CATALOG || page >= Math.max(getChanConfiguration()
				.getPagesCount(pageHolder.boardName), 1))
		{
			getListView().getWrapper().cancelBusyState();
			ToastUtils.show(getActivity(), getString(R.string.message_page_not_exist_format, page));
			return false;
		}
		else
		{
			HttpValidator validator = !append && mLastPage == page && !getAdapter().isEmpty()
					? getExtra().validator : null;
			mReadTask = new ReadThreadsTask(this, pageHolder.chanName, pageHolder.boardName, getExtra().cachedThreads,
					validator, page, append);
			mReadTask.executeOnExecutor(ReadThreadsTask.THREAD_POOL_EXECUTOR);
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
			return true;
		}
	}
	
	@Override
	public void onReadThreadsSuccess(Threads threads, PostItem[][] postItems, int pageNumber,
			boolean append, boolean checkModified, HttpValidator validator)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		ThreadsAdapter adapter = getAdapter();
		if (threads != null)
		{
			mLastPage = pageNumber;
			int oldCount = adapter.getCount();
			ThreadsExtra extra = getExtra();
			if (append) adapter.appendItems(postItems[0], pageNumber, threads);
			else adapter.setItems(postItems, pageNumber, threads);
			PageHolder pageHolder = getPageHolder();
			String title = getChanConfiguration().getBoardTitle(pageHolder.boardName);
			if (title != null) updateTitle(title);
			ListView listView = getListView();
			if (!append)
			{
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
			}
			else if (listView.getChildCount() > 0)
			{
				if (listView.getLastVisiblePosition() + 1 == oldCount)
				{
					View view = listView.getChildAt(listView.getChildCount() - 1);
					if (listView.getHeight() - listView.getPaddingBottom() - view.getBottom() >= 0)
					{
						ListScroller.scrollTo(getListView(), oldCount);
					}
				}
			}
			extra.validator = validator;
			if (append && extra.cachedThreads != null)
			{
				extra.cachedPostItems.add(postItems[0]);
				extra.cachedThreads.addNextPage(threads);
			}
			else
			{
				extra.cachedPostItems.clear();
				extra.cachedPostItems.add(postItems[0]);
				extra.cachedThreads = threads;
			}
			if (oldCount == 0 && !adapter.isRealEmpty()) showScaleAnimation();
		}
		else if (checkModified)
		{
			adapter.notifyNotModified();
			getListView().post(() ->
			{
				ListView listView = getListView();
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
			});
		}
		else if (adapter.isRealEmpty())
		{
			switchView(ViewType.ERROR, R.string.message_empty_response);
		}
		else ClickableToast.show(getActivity(), R.string.message_empty_response);
	}
	
	@Override
	public void onReadThreadsFail(ErrorItem errorItem, int pageNumber)
	{
		mReadTask = null;
		getListView().getWrapper().cancelBusyState();
		if (getAdapter().isRealEmpty()) switchView(ViewType.ERROR, errorItem.toString()); else
		{
			if (pageNumber > 0 && errorItem.httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND)
			{
				ClickableToast.show(getActivity(), getString(R.string.message_page_not_exist_format, pageNumber));
			}
			else ClickableToast.show(getActivity(), errorItem.toString());
		}
	}
	
	@Override
	public void onPostItemMessage(PostItem postItem, int message)
	{
		switch (message)
		{
			case UiManager.MESSAGE_PERFORM_LOAD_THUMBNAIL:
			{
				UiManager uiManager = getUiManager();
				ThreadsAdapter adapter = getAdapter();
				ListView listView = getListView();
				View thumbnailedView = null;
				OUTER: for (int i = 0; i < listView.getChildCount(); i++)
				{
					View view = listView.getChildAt(i);
					if (adapter.isGridMode())
					{
						if (view instanceof ViewGroup)
						{
							ViewGroup viewGroup = (ViewGroup) view;
							for (int j = 0; j < viewGroup.getChildCount(); j++)
							{
								View child = viewGroup.getChildAt(j);
								if (child.getVisibility() == View.VISIBLE)
								{
									PostItem childPostItem = uiManager.getPostItemFromHolder(child);
									if (childPostItem == postItem)
									{
										thumbnailedView = child;
										break OUTER;
									}
								}
							}
						}
					}
					else
					{
						PostItem childPostItem = uiManager.getPostItemFromHolder(view);
						if (childPostItem == postItem)
						{
							thumbnailedView = view;
							break;
						}
					}
				}
				if (thumbnailedView != null)
				{
					uiManager.view().displayThumbnail(thumbnailedView, postItem.getAttachmentItems(), true);
				}
				break;
			}
		}
	}
	
	public static class ThreadsExtra implements PageHolder.ParcelableExtra
	{
		public Threads cachedThreads;
		public final ArrayList<PostItem[]> cachedPostItems = new ArrayList<>();
		public HttpValidator validator;
		
		public boolean headerExpanded = false;
		public int catalogSortIndex = -1;
		public String positionInfo;
		
		@Override
		public void writeToParcel(Parcel dest)
		{
			dest.writeInt(headerExpanded ? 1 : 0);
			dest.writeInt(catalogSortIndex);
			dest.writeString(positionInfo);
		}
		
		@Override
		public void readFromParcel(Parcel source)
		{
			headerExpanded = source.readInt() != 0;
			catalogSortIndex = source.readInt();
			positionInfo = source.readString();
		}
	}
	
	private ThreadsExtra getExtra()
	{
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof ThreadsExtra)) pageHolder.extra = new ThreadsExtra();
		return (ThreadsExtra) pageHolder.extra;
	}
}