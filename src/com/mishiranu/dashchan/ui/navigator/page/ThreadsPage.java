package com.mishiranu.dashchan.ui.navigator.page;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
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
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.RedirectException;
import chan.http.HttpValidator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.async.ReadThreadsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HiddenThreadsDatabase;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.navigator.DrawerForm;
import com.mishiranu.dashchan.ui.navigator.adapter.ThreadsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.ListScroller;
import com.mishiranu.dashchan.widget.PullableListView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class ThreadsPage extends ListPage<ThreadsAdapter> implements FavoritesStorage.Observer,
		ImageLoader.Observer, ReadThreadsTask.Callback, PullableListView.OnBeforeLayoutListener {
	private ReadThreadsTask readTask;

	private Drawable oldListSelector;

	@Override
	protected void onCreate() {
		Activity activity = getActivity();
		PullableListView listView = getListView();
		PageHolder pageHolder = getPageHolder();
		UiManager uiManager = getUiManager();
		listView.setDivider(null);
		listView.addOnBeforeLayoutListener(this);
		ThreadsAdapter adapter = new ThreadsAdapter(activity, pageHolder.chanName, pageHolder.boardName, uiManager);
		initAdapter(adapter, adapter);
		ImageLoader.getInstance().observable().register(this);
		listView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		oldListSelector = listView.getSelector();
		listView.setSelector(android.R.color.transparent);
		ThreadsExtra extra = getExtra();
		adapter.applyAttributesBeforeFill(extra.headerExpanded, extra.catalogSortIndex,
				Preferences.isThreadsGridMode());
		gridLayoutControl.apply();
		ChanConfiguration.Board board = getChanConfiguration().safe().obtainBoard(pageHolder.boardName);
		if (pageHolder.initialFromCache && !extra.cachedPostItems.isEmpty()) {
			getAdapter().setItems(extra.cachedPostItems, extra.startPageNumber, extra.boardSpeed);
			showScaleAnimation();
			if (pageHolder.position != null) {
				int position = getAdapter().getPositionFromInfo(extra.positionInfo);
				if (position != -1 && position != pageHolder.position.position) {
					// Fix position if grid mode was changed
					new ListPosition(position, pageHolder.position.y).apply(listView);
				} else {
					pageHolder.position.apply(listView);
				}
			}
		} else {
			extra.cachedPostItems.clear();
			extra.startPageNumber = board.allowCatalog && Preferences.isLoadCatalog(pageHolder.chanName)
					? PAGE_NUMBER_CATALOG : 0;
			refreshThreads(RefreshPage.CURRENT, false);
		}
		FavoritesStorage.getInstance().getObservable().register(this);
		pageHolder.setInitialThreadsData(false);
	}

	@Override
	protected void onDestroy() {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
		ImageLoader.getInstance().observable().unregister(this);
		ImageLoader.getInstance().clearTasks(getPageHolder().chanName);
		FavoritesStorage.getInstance().getObservable().unregister(this);
		PullableListView listView = getListView();
		listView.setSelector(oldListSelector);
		listView.removeOnBeforeLayoutListener(this);
	}

	@Override
	protected void onHandleNewPostDatas() {
		PageHolder pageHolder = getPageHolder();
		PostingService.NewPostData newPostData = PostingService.obtainNewThreadData(getActivity(), pageHolder.chanName,
				pageHolder.boardName);
		if (newPostData != null) {
			getUiManager().navigator().navigatePosts(newPostData.chanName, newPostData.boardName,
					newPostData.threadNumber, newPostData.postNumber, null, 0);
		}
	}

	@Override
	public String obtainTitle() {
		PageHolder pageHolder = getPageHolder();
		String title = getChanConfiguration().getBoardTitle(pageHolder.boardName);
		return StringUtils.formatBoardTitle(pageHolder.chanName, pageHolder.boardName, title);
	}

	@Override
	public void onItemClick(View view, int position, long id) {
		ThreadsAdapter adapter = getAdapter();
		PostItem postItem = getUiManager().getPostItemFromHolder(view);
		if (postItem != null) {
			PageHolder pageHolder = getPageHolder();
			if (postItem.isHiddenUnchecked()) {
				HiddenThreadsDatabase.getInstance().set(pageHolder.chanName, pageHolder.boardName,
						postItem.getThreadNumber(), false);
				postItem.invalidateHidden();
				adapter.notifyDataSetChanged();
			} else {
				getUiManager().navigator().navigatePosts(pageHolder.chanName, pageHolder.boardName,
						postItem.getThreadNumber(), null, postItem.getSubjectOrComment(), 0);
			}
		}
	}

	private boolean allowSearch = false;

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
	public void onCreateOptionsMenu(Menu menu) {
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
	public void onPrepareOptionsMenu(Menu menu) {
		PageHolder pageHolder = getPageHolder();
		ThreadsExtra extra = getExtra();
		ChanConfiguration configuration = getChanConfiguration();
		ChanConfiguration.Board board = configuration.safe().obtainBoard(pageHolder.boardName);
		boolean search = board.allowSearch;
		boolean catalog = board.allowCatalog;
		boolean catalogSearch = catalog && board.allowCatalogSearch;
		boolean canSearch = search || catalogSearch;
		allowSearch = canSearch;
		boolean isCatalogOpen = extra.startPageNumber == PAGE_NUMBER_CATALOG;
		menu.findItem(OPTIONS_MENU_SEARCH).setTitle(canSearch ? R.string.action_search : R.string.action_filter);
		menu.findItem(OPTIONS_MENU_CATALOG).setVisible(catalog && !isCatalogOpen);
		menu.findItem(OPTIONS_MENU_PAGES).setVisible(catalog && isCatalogOpen);
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
	public boolean onOptionsItemSelected(MenuItem item) {
		PageHolder pageHolder = getPageHolder();
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshThreads(RefreshPage.CURRENT);
				return true;
			}
			case OPTIONS_MENU_CATALOG: {
				loadThreadsPage(PAGE_NUMBER_CATALOG, false);
				return true;
			}
			case OPTIONS_MENU_PAGES: {
				loadThreadsPage(0, false);
				return true;
			}
			case OPTIONS_MENU_ARCHIVE: {
				getUiManager().navigator().navigateArchive(pageHolder.chanName, pageHolder.boardName, 0);
				return true;
			}
			case OPTIONS_MENU_NEW_THREAD: {
				getUiManager().navigator().navigatePosting(pageHolder.chanName, pageHolder.boardName, null);
				return true;
			}
			case OPTIONS_MENU_ADD_TO_FAVORITES_TEXT:
			case OPTIONS_MENU_ADD_TO_FAVORITES_ICON: {
				FavoritesStorage.getInstance().add(pageHolder.chanName, pageHolder.boardName);
				return true;
			}
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT:
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON: {
				FavoritesStorage.getInstance().remove(pageHolder.chanName, pageHolder.boardName, null);
				return true;
			}
			case OPTIONS_MENU_MAKE_HOME_PAGE: {
				Preferences.setDefaultBoardName(pageHolder.chanName, pageHolder.boardName);
				item.setVisible(false);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, int action) {
		switch (action) {
			case FavoritesStorage.ACTION_ADD:
			case FavoritesStorage.ACTION_REMOVE: {
				PageHolder pageHolder = getPageHolder();
				if (favoriteItem.equals(pageHolder.chanName, pageHolder.boardName, null)) {
					updateOptionsMenu();
				}
				break;
			}
		}
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_SHARE_LINK = 1;
	private static final int CONTEXT_MENU_HIDE = 2;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, int position, View targetView) {
		PostItem postItem = getUiManager().getPostItemFromHolder(targetView);
		if (postItem != null) {
			menu.add(Menu.NONE, CONTEXT_MENU_COPY_LINK, 0, R.string.action_copy_link);
			menu.add(Menu.NONE, CONTEXT_MENU_SHARE_LINK, 0, R.string.action_share_link);
			if (!postItem.isHiddenUnchecked()) {
				menu.add(Menu.NONE, CONTEXT_MENU_HIDE, 0, R.string.action_hide);
			}
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item, int position, View targetView) {
		PageHolder pageHolder = getPageHolder();
		ThreadsAdapter adapter = getAdapter();
		PostItem postItem = getUiManager().getPostItemFromHolder(targetView);
		if (postItem != null) {
			switch (item.getItemId()) {
				case CONTEXT_MENU_COPY_LINK: {
					Uri uri = getChanLocator().safe(true).createThreadUri(pageHolder.boardName,
							postItem.getThreadNumber());
					if (uri != null) {
						StringUtils.copyToClipboard(getActivity(), uri.toString());
					}
					return true;
				}
				case CONTEXT_MENU_SHARE_LINK: {
					Context context = getActivity();
					Uri uri = ChanLocator.get(pageHolder.chanName).safe(true)
							.createThreadUri(pageHolder.boardName, postItem.getThreadNumber());
					String subject = postItem.getSubjectOrComment();
					if (StringUtils.isEmptyOrWhitespace(subject)) {
						subject = uri.toString();
					}
					NavigationUtils.shareLink(context, subject, uri);
					return true;
				}
				case CONTEXT_MENU_HIDE: {
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
	public void onAppearanceOptionChanged(int what) {
		switch (what) {
			case APPEARANCE_MENU_SPOILERS:
			case APPEARANCE_MENU_SFW_MODE: {
				notifyAllAdaptersChanged();
				break;
			}
			case APPEARANCE_MENU_THREADS_GRID: {
				boolean gridMode = Preferences.isThreadsGridMode();
				gridLayoutControl.applyGridMode(gridMode);
				break;
			}
		}
	}

	@Override
	public void onBeforeLayout(View v, int left, int top, int right, int bottom) {
		int width = right - left;
		gridLayoutControl.onListLayout(width);
	}

	private final GridLayoutControl gridLayoutControl = new GridLayoutControl();

	private class GridLayoutControl implements Runnable {
		private int currentWidth;
		private ListPosition listPosition;
		private String positionInfo;

		public void applyGridMode(boolean gridMode) {
			ListView listView = getListView();
			ListPosition listPosition = ListPosition.obtain(listView);
			String positionInfo = getAdapter().getPositionInfo(listPosition.position);
			getAdapter().setGridMode(gridMode);
			Preferences.setThreadsGridMode(gridMode);
			int position = getAdapter().getPositionFromInfo(positionInfo);
			if (position != -1) {
				new ListPosition(position, listPosition.y).apply(listView);
			}
		}

		public void apply() {
			ListView listView = getListView();
			listView.removeCallbacks(this);
			listPosition = null;
			positionInfo = null;
			if (listView.getWidth() > 0) {
				run();
			} else {
				listView.post(this);
			}
		}

		public void onListLayout(int width) {
			if (currentWidth != width) {
				currentWidth = width;
				ListView listView = getListView();
				listView.removeCallbacks(this);
				boolean gridMode = getAdapter().isGridMode();
				listPosition = gridMode ? ListPosition.obtain(listView) : null;
				positionInfo = gridMode ? getAdapter().getPositionInfo(listPosition.position) : null;
				listView.post(this);
			}
		}

		@Override
		public void run() {
			ListView listView = getListView();
			listView.removeCallbacks(this);
			getAdapter().updateConfiguration(listView.getWidth());
			if (positionInfo != null) {
				int position = getAdapter().getPositionFromInfo(positionInfo);
				if (position != -1 && position != listPosition.position) {
					// Fix list position due to rows count changing
					new ListPosition(position, listPosition.y).apply(listView);
				}
			} else {
				currentWidth = listView.getWidth();
			}
		}
	}

	@Override
	public boolean onSearchSubmit(String query) {
		if (allowSearch) {
			PageHolder pageHolder = getPageHolder();
			getUiManager().navigator().navigateSearch(pageHolder.chanName, pageHolder.boardName, query, 0);
			return true;
		}
		return false;
	}

	@Override
	public int onDrawerNumberEntered(int number) {
		int result = 0;
		if (number >= 0) {
			// loadDesiredThreadsPage will leave error message, if number is incorrect
			result |= DrawerForm.RESULT_REMOVE_ERROR_MESSAGE;
			if (loadThreadsPage(number, false)) {
				result |= DrawerForm.RESULT_SUCCESS;
			}
		}
		return result;
	}

	@Override
	public void onRequestStoreExtra() {
		PageHolder pageHolder = getPageHolder();
		ThreadsAdapter adapter = getAdapter();
		ThreadsExtra extra = getExtra();
		extra.headerExpanded = adapter.isHeaderExpanded();
		extra.catalogSortIndex = adapter.getCatalogSortIndex();
		extra.positionInfo = pageHolder.position != null ? adapter.getPositionInfo(pageHolder.position.position) : null;
	}

	@Override
	public void onSearchQueryChange(String query) {
		getAdapter().applyFilter(query);
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		ThreadsExtra extra = getExtra();
		refreshThreads(getAdapter().isRealEmpty() || extra.startPageNumber == PAGE_NUMBER_CATALOG ? RefreshPage.CURRENT
				: side == PullableWrapper.Side.BOTTOM ? RefreshPage.NEXT : RefreshPage.PREVIOUS, true);
	}

	private enum RefreshPage {CURRENT, PREVIOUS, NEXT, CATALOG}

	private static final int PAGE_NUMBER_CATALOG = ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG;

	private void refreshThreads(RefreshPage refreshPage) {
		refreshThreads(refreshPage, !getAdapter().isRealEmpty());
	}

	private void refreshThreads(RefreshPage refreshPage, boolean showPull) {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
		int pageNumber;
		boolean append = false;
		ThreadsExtra extra = getExtra();
		if (refreshPage == RefreshPage.CATALOG || refreshPage == RefreshPage.CURRENT &&
				extra.startPageNumber == PAGE_NUMBER_CATALOG) {
			pageNumber = PAGE_NUMBER_CATALOG;
		} else {
			int currentPageNumber = extra.startPageNumber;
			if (!extra.cachedPostItems.isEmpty()) {
				currentPageNumber += extra.cachedPostItems.size() - 1;
			}
			boolean pageByPage = Preferences.isPageByPage();
			if (pageByPage) {
				pageNumber = refreshPage == RefreshPage.NEXT ? currentPageNumber + 1
						: refreshPage == RefreshPage.PREVIOUS ? currentPageNumber - 1 : currentPageNumber;
				if (pageNumber < 0) {
					pageNumber = 0;
				}
			} else {
				pageNumber = refreshPage == RefreshPage.NEXT && currentPageNumber >= 0 ? currentPageNumber + 1 : 0;
				if (pageNumber != 0) {
					append = true;
				}
			}
		}
		loadThreadsPage(pageNumber, append, showPull);
	}

	private boolean loadThreadsPage(int pageNumber, boolean append) {
		return loadThreadsPage(pageNumber, append, !getAdapter().isRealEmpty());
	}

	private boolean loadThreadsPage(int pageNumber, boolean append, boolean showPull) {
		if (readTask != null) {
			readTask.cancel();
		}
		PageHolder pageHolder = getPageHolder();
		if (pageNumber < PAGE_NUMBER_CATALOG || pageNumber >= Math.max(getChanConfiguration()
				.getPagesCount(pageHolder.boardName), 1)) {
			getListView().getWrapper().cancelBusyState();
			ToastUtils.show(getActivity(), getString(R.string.message_page_not_exist_format, pageNumber));
			return false;
		} else {
			ThreadsExtra extra = getExtra();
			HttpValidator validator = !append && extra.cachedPostItems.size() == 1
					&& extra.startPageNumber == pageNumber ? extra.validator : null;
			readTask = new ReadThreadsTask(this, pageHolder.chanName, pageHolder.boardName, pageNumber,
					validator, append);
			readTask.executeOnExecutor(ReadThreadsTask.THREAD_POOL_EXECUTOR);
			if (showPull) {
				getListView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
				switchView(ViewType.LIST, null);
			} else {
				getListView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
				switchView(ViewType.PROGRESS, null);
			}
			return true;
		}
	}

	@Override
	public void onReadThreadsSuccess(ArrayList<PostItem> postItems, int pageNumber,
			int boardSpeed, boolean append, boolean checkModified, HttpValidator validator) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		ThreadsExtra extra = getExtra();
		if (postItems != null && postItems.isEmpty()) {
			postItems = null;
		}
		if (extra.cachedPostItems.isEmpty()) {
			append = false;
		}
		if (postItems != null && append) {
			HashSet<String> threadNumbers = new HashSet<>();
			for (ArrayList<PostItem> pagePostItems : extra.cachedPostItems) {
				for (PostItem postItem : pagePostItems) {
					threadNumbers.add(postItem.getPostNumber());
				}
			}
			for (int i = postItems.size() - 1; i >= 0; i--) {
				if (threadNumbers.contains(postItems.get(i).getThreadNumber())) {
					postItems.remove(i);
				}
			}
		}
		ThreadsAdapter adapter = getAdapter();
		if (postItems != null && !postItems.isEmpty()) {
			int oldCount = adapter.getCount();
			if (append) {
				adapter.appendItems(postItems, pageNumber, extra.boardSpeed);
			} else {
				adapter.setItems(Collections.singleton(postItems), pageNumber, boardSpeed);
			}
			notifyTitleChanged();
			ListView listView = getListView();
			if (!append) {
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
			} else if (listView.getChildCount() > 0) {
				if (listView.getLastVisiblePosition() + 1 == oldCount) {
					View view = listView.getChildAt(listView.getChildCount() - 1);
					if (listView.getHeight() - listView.getPaddingBottom() - view.getBottom() >= 0) {
						ListScroller.scrollTo(getListView(), oldCount);
					}
				}
			}
			extra.validator = validator;
			if (!append) {
				extra.cachedPostItems.clear();
				extra.startPageNumber = pageNumber;
				extra.boardSpeed = boardSpeed;
			}
			extra.cachedPostItems.add(postItems);
			if (oldCount == 0 && !adapter.isRealEmpty()) {
				showScaleAnimation();
			}
		} else if (checkModified && postItems == null) {
			adapter.notifyNotModified();
			getListView().post(() -> {
				ListView listView = getListView();
				ListViewUtils.cancelListFling(listView);
				listView.setSelection(0);
			});
		} else if (adapter.isRealEmpty()) {
			switchView(ViewType.ERROR, R.string.message_empty_response);
		} else {
			ClickableToast.show(getActivity(), R.string.message_empty_response);
		}
	}

	@Override
	public void onReadThreadsRedirect(RedirectException.Target target) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		if (!StringUtils.equals(target.chanName, getPageHolder().chanName)) {
			if (getAdapter().isRealEmpty()) {
				switchView(ViewType.ERROR, R.string.message_empty_response);
			}
			String message = getString(R.string.message_open_chan_confirm_confirm,
					ChanConfiguration.get(target.chanName).getTitle());
			new AlertDialog.Builder(getActivity()).setMessage(message)
					.setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok,
					(dialog, which) -> handleRedirect(target.chanName, target.boardName, null, null)).show();
		} else {
			handleRedirect(target.chanName, target.boardName, null, null);
		}
	}

	@Override
	public void onReadThreadsFail(ErrorItem errorItem, int pageNumber) {
		readTask = null;
		getListView().getWrapper().cancelBusyState();
		String message = errorItem.type == ErrorItem.TYPE_BOARD_NOT_EXISTS && pageNumber >= 1
				? getString(R.string.message_page_not_exist_format, pageNumber) : errorItem.toString();
		if (getAdapter().isRealEmpty()) {
			switchView(ViewType.ERROR, message);
		} else {
			ClickableToast.show(getActivity(), message);
		}
	}

	@Override
	public void onImageLoadComplete(String key, Bitmap bitmap, boolean error) {
		UiManager uiManager = getUiManager();
		ThreadsAdapter adapter = getAdapter();
		ListView listView = getListView();
		for (int i = 0; i < listView.getChildCount(); i++) {
			View view = listView.getChildAt(i);
			if (adapter.isGridMode()) {
				if (view instanceof ViewGroup) {
					ViewGroup viewGroup = (ViewGroup) view;
					for (int j = 0; j < viewGroup.getChildCount(); j++) {
						View child = viewGroup.getChildAt(j);
						if (child.getVisibility() == View.VISIBLE) {
							uiManager.view().displayLoadedThumbnailsForView(child, key, bitmap, error);
						}
					}
				}
			} else {
				uiManager.view().displayLoadedThumbnailsForView(view, key, bitmap, error);
			}
		}
	}

	public static class ThreadsExtra implements PageHolder.ParcelableExtra {
		public final ArrayList<ArrayList<PostItem>> cachedPostItems = new ArrayList<>();
		public int startPageNumber;
		public int boardSpeed;
		public HttpValidator validator;

		public boolean headerExpanded = false;
		public int catalogSortIndex = -1;
		public String positionInfo;

		@Override
		public void writeToParcel(Parcel dest) {
			dest.writeInt(headerExpanded ? 1 : 0);
			dest.writeInt(catalogSortIndex);
			dest.writeString(positionInfo);
		}

		@Override
		public void readFromParcel(Parcel source) {
			headerExpanded = source.readInt() != 0;
			catalogSortIndex = source.readInt();
			positionInfo = source.readString();
		}
	}

	private ThreadsExtra getExtra() {
		PageHolder pageHolder = getPageHolder();
		if (!(pageHolder.extra instanceof ThreadsExtra)) {
			pageHolder.extra = new ThreadsExtra();
		}
		return (ThreadsExtra) pageHolder.extra;
	}
}
