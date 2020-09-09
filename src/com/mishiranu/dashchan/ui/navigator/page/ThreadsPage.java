package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.RedirectException;
import chan.http.HttpValidator;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadThreadsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HiddenThreadsDatabase;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.ThreadsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class ThreadsPage extends ListPage implements ThreadsAdapter.Callback,
		FavoritesStorage.Observer, ReadThreadsTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public final ArrayList<ArrayList<PostItem>> cachedPostItems = new ArrayList<>();
		public int startPageNumber;
		public int boardSpeed;
		public HttpValidator validator;

		public DialogUnit.StackInstance.State dialogsState;
	}

	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public boolean headerExpanded = false;
		public int catalogSortIndex = -1;

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeByte((byte) (headerExpanded ? 1 : 0));
			dest.writeInt(catalogSortIndex);
		}

		public static final Creator<ParcelableExtra> CREATOR = new Creator<ParcelableExtra>() {
			@Override
			public ParcelableExtra createFromParcel(Parcel in) {
				ParcelableExtra parcelableExtra = new ParcelableExtra();
				parcelableExtra.headerExpanded = in.readByte() != 0;
				parcelableExtra.catalogSortIndex = in.readInt();
				return parcelableExtra;
			}

			@Override
			public ParcelableExtra[] newArray(int size) {
				return new ParcelableExtra[size];
			}
		};
	}

	private ReadThreadsTask readTask;

	private ThreadsAdapter getAdapter() {
		return (ThreadsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		Context context = getContext();
		PullableRecyclerView recyclerView = getRecyclerView();
		GridLayoutManager gridLayoutManager = new GridLayoutManager(recyclerView.getContext(), 1);
		recyclerView.setLayoutManager(gridLayoutManager);
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		UiManager uiManager = getUiManager();
		ThreadsAdapter adapter = new ThreadsAdapter(context, this, page.chanName, page.boardName, uiManager,
				parcelableExtra.headerExpanded, parcelableExtra.catalogSortIndex);
		recyclerView.setAdapter(adapter);
		gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				return adapter.getSpanSize(position);
			}
		});
		recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
			@Override
			public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
					@NonNull RecyclerView.State state) {
				int column = ((GridLayoutManager.LayoutParams) view.getLayoutParams()).getSpanIndex();
				adapter.applyItemPadding(view, parent.getChildAdapterPosition(view), column, outRect);
			}
		});
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		gridLayoutManager.setSpanCount(adapter.setGridMode(Preferences.isThreadsGridMode()));
		ChanConfiguration.Board board = getChanConfiguration().safe().obtainBoard(page.boardName);
		InitRequest initRequest = getInitRequest();
		if (initRequest.shouldLoad || retainExtra.cachedPostItems.isEmpty()) {
			retainExtra.cachedPostItems.clear();
			retainExtra.startPageNumber = board.allowCatalog && Preferences.isLoadCatalog(page.chanName)
					? PAGE_NUMBER_CATALOG : 0;
			refreshThreads(RefreshPage.CURRENT, false);
		} else  {
			adapter.setItems(retainExtra.cachedPostItems, retainExtra.startPageNumber, retainExtra.boardSpeed);
			restoreListPosition();
			if (retainExtra.dialogsState != null) {
				uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainExtra.dialogsState);
				retainExtra.dialogsState = null;
			}
		}
		FavoritesStorage.getInstance().getObservable().register(this);
	}

	@Override
	protected void onDestroy() {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
		getUiManager().dialog().closeDialogs(getAdapter().getConfigurationSet().stackInstance);
		FavoritesStorage.getInstance().getObservable().unregister(this);
	}

	@Override
	protected void onNotifyAllAdaptersChanged() {
		getUiManager().dialog().notifyDataSetChangedToAll(getAdapter().getConfigurationSet().stackInstance);
	}

	@Override
	protected void onHandleNewPostDataList() {
		Page page = getPage();
		PostingService.NewPostData newPostData = PostingService.obtainNewThreadData(getContext(),
				page.chanName, page.boardName);
		if (newPostData != null) {
			getUiManager().navigator().navigatePosts(newPostData.chanName, newPostData.boardName,
					newPostData.threadNumber, newPostData.postNumber, null, 0);
		}
	}

	@Override
	protected void onRequestStoreExtra(boolean saveToStack) {
		ThreadsAdapter adapter = getAdapter();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.headerExpanded = adapter.isHeaderExpanded();
		parcelableExtra.catalogSortIndex = adapter.getCatalogSortIndex();
	}

	@Override
	public String obtainTitle() {
		Page page = getPage();
		String title = getChanConfiguration().getBoardTitle(page.boardName);
		return StringUtils.formatBoardTitle(page.chanName, page.boardName, title);
	}

	@Override
	public void onItemClick(PostItem postItem) {
		if (postItem != null) {
			Page page = getPage();
			if (postItem.isHiddenUnchecked()) {
				HiddenThreadsDatabase.getInstance().set(page.chanName, page.boardName,
						postItem.getThreadNumber(), false);
				postItem.invalidateHidden();
				getAdapter().notifyDataSetChanged();
			} else {
				getUiManager().navigator().navigatePosts(page.chanName, page.boardName,
						postItem.getThreadNumber(), null, postItem.getSubjectOrComment(), 0);
			}
		}
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		if (postItem != null) {
			Page page = getPage();
			DialogMenu dialogMenu = new DialogMenu(getContext());
			dialogMenu.add(R.string.action_copy_link, () -> {
				Uri uri = getChanLocator().safe(true).createThreadUri(page.boardName, postItem.getThreadNumber());
				if (uri != null) {
					StringUtils.copyToClipboard(getContext(), uri.toString());
				}
			});
			dialogMenu.add(R.string.action_share_link, () -> {
				Uri uri = ChanLocator.get(page.chanName).safe(true)
						.createThreadUri(page.boardName, postItem.getThreadNumber());
				String subject = postItem.getSubjectOrComment();
				if (StringUtils.isEmptyOrWhitespace(subject)) {
					subject = uri.toString();
				}
				NavigationUtils.shareLink(getContext(), subject, uri);
			});
			if (!postItem.isHiddenUnchecked()) {
				dialogMenu.add(R.string.action_hide, () -> {
					HiddenThreadsDatabase.getInstance().set(page.chanName, page.boardName,
							postItem.getThreadNumber(), true);
					postItem.invalidateHidden();
					getAdapter().notifyDataSetChanged();
				});
			}
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	private boolean allowSearch = false;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_refresh, 0, R.string.action_refresh)
				.setIcon(getActionBarIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_search, 0, R.string.action_search)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_catalog, 0, R.string.action_catalog);
		menu.add(0, R.id.menu_pages, 0, R.string.action_pages);
		menu.add(0, R.id.menu_archive, 0, R.string.action_archive_view);
		menu.add(0, R.id.menu_new_thread, 0, R.string.action_new_thread);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.action_appearance);
		menu.add(0, R.id.menu_star_text, 0, R.string.action_add_to_favorites);
		menu.add(0, R.id.menu_unstar_text, 0, R.string.action_remove_from_favorites);
		menu.add(0, R.id.menu_star_icon, 0, R.string.action_add_to_favorites)
				.setIcon(getActionBarIcon(R.attr.iconActionAddToFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_unstar_icon, 0, R.string.action_remove_from_favorites)
				.setIcon(getActionBarIcon(R.attr.iconActionRemoveFromFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_make_home_page, 0, R.string.action_make_home_page);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ChanConfiguration configuration = getChanConfiguration();
		ChanConfiguration.Board board = configuration.safe().obtainBoard(page.boardName);
		boolean search = board.allowSearch;
		boolean catalog = board.allowCatalog;
		boolean catalogSearch = catalog && board.allowCatalogSearch;
		boolean canSearch = search || catalogSearch;
		allowSearch = canSearch;
		boolean isCatalogOpen = retainExtra.startPageNumber == PAGE_NUMBER_CATALOG;
		menu.findItem(R.id.menu_search).setTitle(canSearch ? R.string.action_search : R.string.action_filter);
		menu.findItem(R.id.menu_catalog).setVisible(catalog && !isCatalogOpen);
		menu.findItem(R.id.menu_pages).setVisible(catalog && isCatalogOpen);
		menu.findItem(R.id.menu_archive).setVisible(board.allowArchive);
		menu.findItem(R.id.menu_new_thread).setVisible(board.allowPosting);
		boolean singleBoardMode = configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE);
		boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(page.chanName, page.boardName, null);
		boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
		menu.findItem(R.id.menu_star_text).setVisible(!iconFavorite && !isFavorite && !singleBoardMode);
		menu.findItem(R.id.menu_unstar_text).setVisible(!iconFavorite && isFavorite);
		menu.findItem(R.id.menu_star_icon).setVisible(iconFavorite && !isFavorite && !singleBoardMode);
		menu.findItem(R.id.menu_unstar_icon).setVisible(iconFavorite && isFavorite);
		menu.findItem(R.id.menu_make_home_page).setVisible(!singleBoardMode &&
				!StringUtils.equals(page.boardName, Preferences.getDefaultBoardName(page.chanName)));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Page page = getPage();
		switch (item.getItemId()) {
			case R.id.menu_refresh: {
				refreshThreads(RefreshPage.CURRENT);
				return true;
			}
			case R.id.menu_catalog: {
				loadThreadsPage(PAGE_NUMBER_CATALOG, false);
				return true;
			}
			case R.id.menu_pages: {
				loadThreadsPage(0, false);
				return true;
			}
			case R.id.menu_archive: {
				getUiManager().navigator().navigateArchive(page.chanName, page.boardName, 0);
				return true;
			}
			case R.id.menu_new_thread: {
				getUiManager().navigator().navigatePosting(page.chanName, page.boardName, null);
				return true;
			}
			case R.id.menu_star_text:
			case R.id.menu_star_icon: {
				FavoritesStorage.getInstance().add(page.chanName, page.boardName);
				return true;
			}
			case R.id.menu_unstar_text:
			case R.id.menu_unstar_icon: {
				FavoritesStorage.getInstance().remove(page.chanName, page.boardName, null);
				return true;
			}
			case R.id.menu_make_home_page: {
				Preferences.setDefaultBoardName(page.chanName, page.boardName);
				item.setVisible(false);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onFavoritesUpdate(FavoritesStorage.FavoriteItem favoriteItem, FavoritesStorage.Action action) {
		switch (action) {
			case ADD:
			case REMOVE: {
				Page page = getPage();
				if (favoriteItem.equals(page.chanName, page.boardName, null)) {
					updateOptionsMenu();
				}
				break;
			}
		}
	}

	@Override
	public void onAppearanceOptionChanged(int what) {
		switch (what) {
			case R.id.menu_spoilers:
			case R.id.menu_sfw_mode: {
				notifyAllAdaptersChanged();
				break;
			}
			case R.id.menu_threads_grid: {
				GridLayoutManager gridLayoutManager = (GridLayoutManager) getRecyclerView().getLayoutManager();
				gridLayoutManager.setSpanCount(getAdapter().setGridMode(Preferences.isThreadsGridMode()));
				break;
			}
		}
	}

	@Override
	public boolean onSearchSubmit(String query) {
		if (allowSearch) {
			Page page = getPage();
			getUiManager().navigator().navigateSearch(page.chanName, page.boardName, query, 0);
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
	public void onSearchQueryChange(String query) {
		getAdapter().applyFilter(query);
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		refreshThreads(getAdapter().isRealEmpty() || retainExtra.startPageNumber == PAGE_NUMBER_CATALOG
				? RefreshPage.CURRENT : side == PullableWrapper.Side.BOTTOM
				? RefreshPage.NEXT : RefreshPage.PREVIOUS, true);
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
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (refreshPage == RefreshPage.CATALOG || refreshPage == RefreshPage.CURRENT &&
				retainExtra.startPageNumber == PAGE_NUMBER_CATALOG) {
			pageNumber = PAGE_NUMBER_CATALOG;
		} else {
			int currentPageNumber = retainExtra.startPageNumber;
			if (!retainExtra.cachedPostItems.isEmpty()) {
				currentPageNumber += retainExtra.cachedPostItems.size() - 1;
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
		Page page = getPage();
		if (pageNumber < PAGE_NUMBER_CATALOG || pageNumber >= Math.max(getChanConfiguration()
				.getPagesCount(page.boardName), 1)) {
			getRecyclerView().getWrapper().cancelBusyState();
			ToastUtils.show(getContext(), getString(R.string.message_page_not_exist_format, pageNumber));
			return false;
		} else {
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			HttpValidator validator = !append && retainExtra.cachedPostItems.size() == 1
					&& retainExtra.startPageNumber == pageNumber ? retainExtra.validator : null;
			readTask = new ReadThreadsTask(this, page.chanName, page.boardName, pageNumber, validator, append);
			readTask.executeOnExecutor(ReadThreadsTask.THREAD_POOL_EXECUTOR);
			if (showPull) {
				getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
				switchView(ViewType.LIST, null);
			} else {
				getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
				switchView(ViewType.PROGRESS, null);
			}
			return true;
		}
	}

	@Override
	public void onReadThreadsSuccess(ArrayList<PostItem> postItems, int pageNumber,
			int boardSpeed, boolean append, boolean checkModified, HttpValidator validator) {
		readTask = null;
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (postItems != null && postItems.isEmpty()) {
			postItems = null;
		}
		if (retainExtra.cachedPostItems.isEmpty()) {
			append = false;
		}
		if (postItems != null && append) {
			HashSet<String> threadNumbers = new HashSet<>();
			for (ArrayList<PostItem> pagePostItems : retainExtra.cachedPostItems) {
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
			int oldCount = adapter.getItemCount();
			boolean needScroll = false;
			int childCount = recyclerView.getChildCount();
			if (childCount > 0) {
				View child = recyclerView.getChildAt(childCount - 1);
				int position = recyclerView.getChildViewHolder(child).getAdapterPosition();
				needScroll = position + 1 == oldCount &&
						recyclerView.getHeight() - recyclerView.getPaddingBottom() - child.getBottom() >= 0;
			}
			if (append) {
				adapter.appendItems(postItems, pageNumber, retainExtra.boardSpeed);
			} else {
				adapter.setItems(Collections.singleton(postItems), pageNumber, boardSpeed);
			}
			notifyTitleChanged();
			if (!append) {
				ListViewUtils.cancelListFling(recyclerView);
				recyclerView.scrollToPosition(0);
			} else if (needScroll) {
				ListViewUtils.smoothScrollToPosition(recyclerView, oldCount);
			}
			retainExtra.validator = validator;
			if (!append) {
				retainExtra.cachedPostItems.clear();
				retainExtra.startPageNumber = pageNumber;
				retainExtra.boardSpeed = boardSpeed;
			}
			retainExtra.cachedPostItems.add(postItems);
			if (oldCount == 0 && !adapter.isRealEmpty()) {
				showScaleAnimation();
			}
		} else if (checkModified && postItems == null) {
			adapter.notifyNotModified();
			ListViewUtils.cancelListFling(recyclerView);
			recyclerView.scrollToPosition(0);
		} else if (adapter.isRealEmpty()) {
			switchView(ViewType.ERROR, R.string.message_empty_response);
		} else {
			ClickableToast.show(getContext(), R.string.message_empty_response);
		}
	}

	@Override
	public void onReadThreadsRedirect(RedirectException.Target target) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		if (!StringUtils.equals(target.chanName, getPage().chanName)) {
			if (getAdapter().isRealEmpty()) {
				switchView(ViewType.ERROR, R.string.message_empty_response);
			}
			String message = getString(R.string.message_open_chan_confirm_confirm,
					ChanConfiguration.get(target.chanName).getTitle());
			AlertDialog dialog = new AlertDialog.Builder(getContext())
					.setMessage(message)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok,
							(d, which) -> handleRedirect(target.chanName, target.boardName, null, null))
					.show();
			getUiManager().getConfigurationLock().lockConfiguration(dialog);
		} else {
			handleRedirect(target.chanName, target.boardName, null, null);
		}
	}

	@Override
	public void onReadThreadsFail(ErrorItem errorItem, int pageNumber) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		String message = errorItem.type == ErrorItem.Type.BOARD_NOT_EXISTS && pageNumber >= 1
				? getString(R.string.message_page_not_exist_format, pageNumber) : errorItem.toString();
		if (getAdapter().isRealEmpty()) {
			switchView(ViewType.ERROR, message);
		} else {
			ClickableToast.show(getContext(), message);
		}
	}
}
