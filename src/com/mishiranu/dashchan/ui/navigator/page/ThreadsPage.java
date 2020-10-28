package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.RedirectException;
import chan.http.HttpValidator;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadThreadsTask;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.ThreadsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.SummaryLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ThreadsPage extends ListPage implements ThreadsAdapter.Callback,
		FavoritesStorage.Observer, ReadThreadsTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public final ArrayList<List<PostItem>> cachedPostItems = new ArrayList<>();
		public final PostItem.HideState.Map<String> hiddenThreads = new PostItem.HideState.Map<>();
		public int startPageNumber;
		public int boardSpeed;
		public HttpValidator validator;

		public DialogUnit.StackInstance.State dialogsState;
	}

	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public ThreadsAdapter.CatalogSort catalogSort = ThreadsAdapter.CatalogSort.UNSORTED;

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(catalogSort.name());
		}

		public static final Creator<ParcelableExtra> CREATOR = new Creator<ParcelableExtra>() {
			@Override
			public ParcelableExtra createFromParcel(Parcel source) {
				ParcelableExtra parcelableExtra = new ParcelableExtra();
				parcelableExtra.catalogSort = ThreadsAdapter.CatalogSort.valueOf(source.readString());
				return parcelableExtra;
			}

			@Override
			public ParcelableExtra[] newArray(int size) {
				return new ParcelableExtra[size];
			}
		};
	}

	private HidePerformer hidePerformer;

	private ReadThreadsTask readTask;

	private final UiManager.PostStateProvider postStateProvider = new UiManager.PostStateProvider() {
		@Override
		public boolean isHiddenResolve(PostItem postItem) {
			if (postItem.getHideState() == PostItem.HideState.UNDEFINED) {
				RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
				PostItem.HideState hideState = retainExtra.hiddenThreads.get(postItem.getThreadNumber());
				if (hideState != PostItem.HideState.UNDEFINED) {
					postItem.setHidden(hideState, null);
				} else {
					String hideReason = hidePerformer.checkHidden(getChan(), postItem);
					if (hideReason != null) {
						postItem.setHidden(PostItem.HideState.HIDDEN, hideReason);
					} else {
						postItem.setHidden(PostItem.HideState.SHOWN, null);
					}
				}
			}
			return postItem.getHideState().hidden;
		}
	};

	private ThreadsAdapter getAdapter() {
		return (ThreadsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		Context context = getContext();
		PullableRecyclerView recyclerView = getRecyclerView();
		GridLayoutManager layoutManager = new GridLayoutManager(recyclerView.getContext(), 1);
		recyclerView.setLayoutManager(layoutManager);
		Page page = getPage();
		Chan chan = getChan();
		hidePerformer = new HidePerformer(context);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		UiManager uiManager = getUiManager();
		ThreadsAdapter adapter = new ThreadsAdapter(context, this, page.chanName, uiManager,
				postStateProvider, parcelableExtra.catalogSort);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
			@Override
			public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
					@NonNull RecyclerView.State state) {
				int column = ((GridLayoutManager.LayoutParams) view.getLayoutParams()).getSpanIndex();
				adapter.applyItemPadding(view, parent.getChildAdapterPosition(view), column, outRect);
			}
		});
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), adapter::configureDivider));
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		layoutManager.setSpanCount(adapter.setThreadsView(Preferences.getThreadsView()));
		adapter.applyFilter(getInitSearch().currentQuery);
		InitRequest initRequest = getInitRequest();
		if (initRequest.shouldLoad || retainExtra.cachedPostItems.isEmpty()) {
			ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(page.boardName);
			retainExtra.cachedPostItems.clear();
			retainExtra.startPageNumber = board.allowCatalog && Preferences.isLoadCatalog(chan)
					? PAGE_NUMBER_CATALOG : 0;
			refreshThreads(RefreshPage.CURRENT, false);
		} else  {
			adapter.setItems(retainExtra.cachedPostItems, retainExtra.startPageNumber == PAGE_NUMBER_CATALOG);
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
		PostingService.NewPostData newPostData = PostingService.consumeNewThreadData(getContext(),
				page.chanName, page.boardName);
		if (newPostData != null) {
			getUiManager().navigator().navigatePosts(newPostData.key.chanName, newPostData.key.boardName,
					newPostData.key.threadNumber, null, null);
		}
	}

	@Override
	protected void onRequestStoreExtra(boolean saveToStack) {
		ThreadsAdapter adapter = getAdapter();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
	}

	@Override
	public Pair<String, String> obtainTitleSubtitle() {
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		String title = getChan().configuration.getBoardTitle(page.boardName);
		title = StringUtils.formatBoardTitle(page.chanName, page.boardName, title);
		String subtitle = null;
		if (retainExtra.startPageNumber > 0) {
			subtitle = getString(R.string.number_page__format, retainExtra.startPageNumber);
		} else if (retainExtra.startPageNumber == PAGE_NUMBER_CATALOG) {
			subtitle = getString(R.string.catalog);
		} else if (retainExtra.boardSpeed > 0) {
			subtitle = getResources().getQuantityString(R.plurals.number_posts_per_hour__format,
					retainExtra.boardSpeed, retainExtra.boardSpeed);
		}
		return new Pair<>(title, subtitle);
	}

	@Override
	public void onItemClick(PostItem postItem) {
		if (postItem != null) {
			Page page = getPage();
			if (postItem.getHideState().hidden) {
				setThreadHideState(postItem, PostItem.HideState.SHOWN);
				getAdapter().notifyDataSetChanged();
			} else {
				getUiManager().navigator().navigatePosts(page.chanName, page.boardName,
						postItem.getThreadNumber(), null, postItem.getSubjectOrComment());
			}
		}
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		if (postItem != null) {
			Page page = getPage();
			DialogMenu dialogMenu = new DialogMenu(getContext());
			dialogMenu.add(R.string.copy_link, () -> {
				Uri uri = getChan().locator.safe(true).createThreadUri(page.boardName, postItem.getThreadNumber());
				if (uri != null) {
					StringUtils.copyToClipboard(getContext(), uri.toString());
				}
			});
			dialogMenu.add(R.string.share_link, () -> {
				Uri uri = getChan().locator.safe(true)
						.createThreadUri(page.boardName, postItem.getThreadNumber());
				String subject = postItem.getSubjectOrComment();
				if (StringUtils.isEmptyOrWhitespace(subject)) {
					subject = uri.toString();
				}
				NavigationUtils.shareLink(getContext(), subject, uri);
			});
			if (!postItem.getHideState().hidden) {
				dialogMenu.add(R.string.hide, () -> {
					setThreadHideState(postItem, PostItem.HideState.HIDDEN);
					getAdapter().notifyDataSetChanged();
				});
			}
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	private void setThreadHideState(PostItem postItem, PostItem.HideState hideState) {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.hiddenThreads.set(postItem.getThreadNumber(), hideState);
		CommonDatabase.getInstance().getThreads().setFlagsAsync(getPage().chanName,
				postItem.getBoardName(), postItem.getThreadNumber(), hideState);
		postItem.setHidden(hideState, null);
	}

	private boolean allowSearch = false;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_refresh, 0, R.string.refresh)
				.setIcon(getActionBarIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_search, 0, R.string.search)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_catalog, 0, R.string.catalog);
		menu.add(0, R.id.menu_pages, 0, R.string.pages);
		SubMenu sorting = menu.addSubMenu(0, R.id.menu_sorting, 0, R.string.sorting);
		for (ThreadsAdapter.CatalogSort catalogSort : ThreadsAdapter.CatalogSort.values()) {
			sorting.add(R.id.menu_sorting, catalogSort.menuItemId, 0, catalogSort.titleResId);
		}
		sorting.setGroupCheckable(R.id.menu_sorting, true, true);
		menu.add(0, R.id.menu_archive, 0, R.string.archive);
		menu.add(0, R.id.menu_new_thread, 0, R.string.new_thread);
		menu.add(0, R.id.menu_summary, 0, R.string.summary);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.appearance);
		SubMenu viewOptions = menu.addSubMenu(0, R.id.menu_threads_view, 0, R.string.threads_view);
		for (Preferences.ThreadsView threadsView : Preferences.ThreadsView.values()) {
			viewOptions.add(R.id.menu_threads_view, threadsView.menuItemId, 0, threadsView.titleResId);
		}
		viewOptions.setGroupCheckable(R.id.menu_threads_view, true, true);
		menu.add(0, R.id.menu_star_text, 0, R.string.add_to_favorites);
		menu.add(0, R.id.menu_unstar_text, 0, R.string.remove_from_favorites);
		menu.add(0, R.id.menu_star_icon, 0, R.string.add_to_favorites)
				.setIcon(getActionBarIcon(R.attr.iconActionAddToFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_unstar_icon, 0, R.string.remove_from_favorites)
				.setIcon(getActionBarIcon(R.attr.iconActionRemoveFromFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_make_home_page, 0, R.string.make_home_page);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		Chan chan = getChan();
		ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(page.boardName);
		boolean search = board.allowSearch;
		boolean catalog = board.allowCatalog;
		boolean catalogSearch = catalog && board.allowCatalogSearch;
		boolean allowSearch = search || catalogSearch;
		this.allowSearch = allowSearch;
		boolean isCatalogOpen = retainExtra.startPageNumber == PAGE_NUMBER_CATALOG;
		menu.findItem(R.id.menu_search).setTitle(allowSearch ? R.string.search : R.string.filter);
		menu.findItem(R.id.menu_catalog).setVisible(catalog && !isCatalogOpen);
		menu.findItem(R.id.menu_pages).setVisible(catalog && isCatalogOpen);
		menu.findItem(R.id.menu_sorting).setVisible(catalog && isCatalogOpen);
		menu.findItem(parcelableExtra.catalogSort.menuItemId).setChecked(true);
		menu.findItem(R.id.menu_archive).setVisible(board.allowArchive);
		menu.findItem(R.id.menu_new_thread).setVisible(board.allowPosting);
		menu.findItem(Preferences.getThreadsView().menuItemId).setChecked(true);
		boolean singleBoardMode = chan.configuration.getOption(ChanConfiguration.OPTION_SINGLE_BOARD_MODE);
		boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(page.chanName, page.boardName, null);
		boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
		menu.findItem(R.id.menu_star_text).setVisible(!iconFavorite && !isFavorite && !singleBoardMode);
		menu.findItem(R.id.menu_unstar_text).setVisible(!iconFavorite && isFavorite);
		menu.findItem(R.id.menu_star_icon).setVisible(iconFavorite && !isFavorite && !singleBoardMode);
		menu.findItem(R.id.menu_unstar_icon).setVisible(iconFavorite && isFavorite);
		menu.findItem(R.id.menu_make_home_page).setVisible(!singleBoardMode &&
				!CommonUtils.equals(page.boardName, Preferences.getDefaultBoardName(chan)));
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
				getUiManager().navigator().navigateArchive(page.chanName, page.boardName);
				return true;
			}
			case R.id.menu_new_thread: {
				getUiManager().navigator().navigatePosting(page.chanName, page.boardName, null);
				return true;
			}
			case R.id.menu_summary: {
				Chan chan = getChan();
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.summary)
						.setPositiveButton(android.R.string.ok, null)
						.create();
				SummaryLayout layout = new SummaryLayout(dialog);
				String boardName = page.boardName;
				if (boardName != null) {
					String title = chan.configuration.getBoardTitle(boardName);
					title = StringUtils.formatBoardTitle(page.chanName, boardName, title);
					layout.add(getString(R.string.board), title);
					String description = chan.configuration.getBoardDescription(boardName);
					if (!StringUtils.isEmpty(description)) {
						layout.add(getString(R.string.description), description);
					}
				}
				int pagesCount = Math.max(chan.configuration.getPagesCount(boardName), 1);
				if (pagesCount != ChanConfiguration.PAGES_COUNT_INVALID) {
					layout.add(getString(R.string.pages_count), Integer.toString(pagesCount));
				}
				ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(boardName);
				ChanConfiguration.Posting posting = board.allowPosting
						? chan.configuration.safe().obtainPosting(boardName, true) : null;
				if (posting != null) {
					int bumpLimit = chan.configuration.getBumpLimit(boardName);
					if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
						layout.add(getString(R.string.bump_limit), getResources()
								.getQuantityString(R.plurals.number_posts__format, bumpLimit, bumpLimit));
					}
				}
				layout.addDivider();
				if (posting != null) {
					StringBuilder builder = new StringBuilder();
					if (!posting.allowSubject) {
						builder.append("\u2022 ").append(getString(R.string.subjects_are_disabled)).append('\n');
					}
					if (!posting.allowName) {
						builder.append("\u2022 ").append(getString(R.string.names_are_disabled)).append('\n');
					} else if (!posting.allowTripcode) {
						builder.append("\u2022 ").append(getString(R.string.tripcodes_are_disabled)).append('\n');
					}
					if (posting.attachmentCount <= 0) {
						builder.append("\u2022 ").append(getString(R.string.images_are_disabled)).append('\n');
					}
					if (!posting.optionSage) {
						builder.append("\u2022 ").append(getString(R.string.sage_is_disabled)).append('\n');
					}
					if (posting.hasCountryFlags) {
						builder.append("\u2022 ").append(getString(R.string.flags_are_enabled)).append('\n');
					}
					if (posting.userIcons.size() > 0) {
						builder.append("\u2022 ").append(getString(R.string.icons_are_enabled)).append('\n');
					}
					if (builder.length() > 0) {
						builder.setLength(builder.length() - 1);
						layout.add(getString(R.string.configuration), builder);
					}
				} else {
					layout.add(getString(R.string.configuration), getString(R.string.read_only));
				}
				dialog.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
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
		for (ThreadsAdapter.CatalogSort catalogSort : ThreadsAdapter.CatalogSort.values()) {
			if (item.getItemId() == catalogSort.menuItemId) {
				ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
				parcelableExtra.catalogSort = catalogSort;
				getAdapter().setCatalogSort(catalogSort);
				return true;
			}
		}
		for (Preferences.ThreadsView threadsView : Preferences.ThreadsView.values()) {
			if (item.getItemId() == threadsView.menuItemId) {
				Preferences.setThreadsView(threadsView);
				GridLayoutManager gridLayoutManager = (GridLayoutManager) getRecyclerView().getLayoutManager();
				gridLayoutManager.setSpanCount(getAdapter().setThreadsView(threadsView));
				getAdapter().notifyDataSetChanged();
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
		}
	}

	@Override
	public boolean onSearchSubmit(String query) {
		if (allowSearch) {
			// Collapse search view
			getRecyclerView().post(() -> {
				Page page = getPage();
				getUiManager().navigator().navigateSearch(page.chanName, page.boardName, query);
			});
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
		Chan chan = getChan();
		if (pageNumber < PAGE_NUMBER_CATALOG || pageNumber >=
				Math.max(chan.configuration.getPagesCount(page.boardName), 1)) {
			getRecyclerView().getWrapper().cancelBusyState();
			ToastUtils.show(getContext(), getString(R.string.number_page_doesnt_exist__format, pageNumber));
			return false;
		} else {
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			HttpValidator validator = !append && retainExtra.cachedPostItems.size() == 1
					&& retainExtra.startPageNumber == pageNumber ? retainExtra.validator : null;
			readTask = new ReadThreadsTask(this, chan, page.boardName, pageNumber, validator, append);
			readTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
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
	public void onReadThreadsSuccess(List<PostItem> postItems, int pageNumber,
			int boardSpeed, boolean append, boolean checkModified, HttpValidator validator,
			PostItem.HideState.Map<String> hiddenThreads) {
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
		if (hiddenThreads != null) {
			if (!append) {
				retainExtra.hiddenThreads.clear();
			}
			retainExtra.hiddenThreads.addAll(hiddenThreads);
		}
		if (postItems != null && append) {
			HashSet<String> threadNumbers = new HashSet<>();
			for (List<PostItem> pagePostItems : retainExtra.cachedPostItems) {
				for (PostItem postItem : pagePostItems) {
					threadNumbers.add(postItem.getThreadNumber());
				}
			}
			boolean newList = false;
			for (int i = postItems.size() - 1; i >= 0; i--) {
				if (threadNumbers.contains(postItems.get(i).getThreadNumber())) {
					if (!newList) {
						postItems = new ArrayList<>(postItems);
						newList = true;
					}
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
				adapter.appendItems(postItems);
			} else {
				adapter.setItems(Collections.singleton(postItems), pageNumber == PAGE_NUMBER_CATALOG);
			}
			if (!append) {
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
			notifyTitleChanged();
			updateOptionsMenu();
			if (oldCount == 0 && !adapter.isRealEmpty()) {
				showScaleAnimation();
			}
		} else if (checkModified && postItems == null) {
			adapter.notifyNotModified();
			recyclerView.scrollToPosition(0);
		} else if (adapter.isRealEmpty()) {
			switchView(ViewType.ERROR, R.string.empty_response);
		} else {
			ClickableToast.show(getContext(), R.string.empty_response);
		}
	}

	@Override
	public void onReadThreadsRedirect(RedirectException.Target target) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		if (!CommonUtils.equals(target.chanName, getPage().chanName)) {
			if (getAdapter().isRealEmpty()) {
				switchView(ViewType.ERROR, R.string.empty_response);
			}
			String message = getString(R.string.open_forum__format_sentence,
					Chan.get(target.chanName).configuration.getTitle());
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
				? getString(R.string.number_page_doesnt_exist__format, pageNumber) : errorItem.toString();
		if (getAdapter().isRealEmpty()) {
			switchView(ViewType.ERROR, message);
		} else {
			ClickableToast.show(getContext(), message);
		}
	}
}
