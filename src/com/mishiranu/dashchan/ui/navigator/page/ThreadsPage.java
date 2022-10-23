package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.DialogMenu;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.ThreadsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.SummaryLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ThreadsPage extends ListPage implements ThreadsAdapter.Callback,
		FavoritesStorage.Observer, UiManager.Observer, ReadThreadsTask.Callback {
	private static class RetainableExtra implements Retainable {
		public static final ExtraFactory<RetainableExtra> FACTORY = RetainableExtra::new;

		public final ArrayList<List<PostItem>> cachedPostItems = new ArrayList<>();
		public final PostItem.HideState.Map<String> hiddenThreads = new PostItem.HideState.Map<>();
		public int startPageNumber;
		public int boardSpeed;
		public HttpValidator validator;

		public DialogUnit.StackInstance.State dialogsState;

		@Override
		public void clear() {
			if (dialogsState != null) {
				dialogsState.dropState();
				dialogsState = null;
			}
		}
	}

	public static class ReadViewModel extends TaskViewModel.Proxy<ReadThreadsTask, ReadThreadsTask.Callback> {}

	private HidePerformer hidePerformer;

	private final UiManager.PostStateProvider postStateProvider = new UiManager.PostStateProvider() {
		@Override
		public boolean isHiddenResolve(PostItem postItem) {
			if (postItem.getHideState() == PostItem.HideState.UNDEFINED) {
				RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
				PostItem.HideState hideState = retainableExtra.hiddenThreads.get(postItem.getThreadNumber());
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
		PaddedRecyclerView recyclerView = getRecyclerView();
		if (swipeToHideThreadEnabled()) {
			setupSwipeToHideThread(recyclerView);
		}
		setupRecyclerViewAnimations(recyclerView);
		GridLayoutManager layoutManager = new GridLayoutManager(recyclerView.getContext(), 1);
		recyclerView.setLayoutManager(layoutManager);
		Page page = getPage();
		Chan chan = getChan();
		hidePerformer = new HidePerformer(context);
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		UiManager uiManager = getUiManager();
		uiManager.view().bindThreadsPostRecyclerView(recyclerView);
		ThreadsAdapter adapter = new ThreadsAdapter(context, this, page.chanName, uiManager,
				postStateProvider, getFragmentManager());
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
		recyclerView.getPullable().setPullSides(PullableWrapper.Side.BOTH);
		uiManager.observable().register(this);
		layoutManager.setSpanCount(adapter.setThreadsView(Preferences.getThreadsView()));
		adapter.setCatalogSort(Preferences.getCatalogSort());
		adapter.applyFilter(getInitSearch().currentQuery);
		FavoritesStorage.getInstance().getObservable().register(this);

		InitRequest initRequest = getInitRequest();
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		ListPosition listPosition = takeListPosition();
		if (initRequest.errorItem != null) {
			switchError(initRequest.errorItem);
		} else {
			boolean load = true;
			if (!initRequest.shouldLoad && !retainableExtra.cachedPostItems.isEmpty()) {
				load = false;
				adapter.setItems(retainableExtra.cachedPostItems,
						retainableExtra.startPageNumber == PAGE_NUMBER_CATALOG);
				if (listPosition != null) {
					listPosition.apply(recyclerView);
				}
				if (retainableExtra.dialogsState != null) {
					uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainableExtra.dialogsState);
					retainableExtra.dialogsState.dropState();
					retainableExtra.dialogsState = null;
				}
			}
			if (readViewModel.hasTaskOrValue()) {
				if (getAdapter().isRealEmpty()) {
					recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
					switchProgress();
				} else {
					ReadThreadsTask task = readViewModel.getTask();
					boolean bottom = task != null && task.getPageNumber() > retainableExtra.startPageNumber;
					recyclerView.getPullable().startBusyState(bottom
							? PullableWrapper.Side.BOTTOM : PullableWrapper.Side.TOP);
				}
			} else if (load) {
				ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(page.boardName);
				retainableExtra.cachedPostItems.clear();
				retainableExtra.startPageNumber = board.allowCatalog && Preferences.isLoadCatalog(chan)
						? PAGE_NUMBER_CATALOG : 0;
				refreshThreads(RefreshPage.CURRENT, false);
			}
		}
		readViewModel.observe(this, this);
	}

	private boolean swipeToHideThreadEnabled() {
		return Preferences.isSwipeToHideThreadEnabled();
	}

	private void setupSwipeToHideThread(RecyclerView recyclerView) {
		ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

			@Override
			public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
				int threadPosition = viewHolder.getAdapterPosition();
				if (threadPosition == RecyclerView.NO_POSITION || threadHidden(threadPosition)) {
					return 0; //disable swipe for hidden threads or if can't get thread's position
				} else {
					return super.getSwipeDirs(recyclerView, viewHolder);
				}
			}

			private boolean threadHidden(int threadPosition) {
				ThreadsAdapter adapter = getAdapter();
				PostItem post = adapter.getThread(threadPosition);
				return post.getHideState().hidden;
			}

			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
				return false;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
				int threadPosition = viewHolder.getAdapterPosition();
				if (threadPosition != RecyclerView.NO_POSITION){
					hideThreadAndNotifyAdapter(threadPosition);
				}
				else {
					getAdapter().notifyDataSetChanged(); // this will bring swiped thread view back
				}
			}

			private void hideThreadAndNotifyAdapter(int threadPosition) {
				ThreadsAdapter adapter = getAdapter();
				PostItem thread = adapter.getThread(threadPosition);
				setThreadHideState(thread, PostItem.HideState.HIDDEN);
				adapter.notifyThreadHidden(thread);
			}

			@Override
			public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
				boolean threadIsBeingSwiped = actionState == ItemTouchHelper.ACTION_STATE_SWIPE;
				if (threadIsBeingSwiped) {
					setOpacityForSwipedThread(viewHolder, dX);
				}
				super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
			}

			private void setOpacityForSwipedThread(RecyclerView.ViewHolder swipedThreadViewWidth, float swipeDeltaX) {
				View threadRootView = swipedThreadViewWidth.itemView;
				float opacity = calculateOpacityForSwipedThread(threadRootView.getWidth(), swipeDeltaX);
				threadRootView.setAlpha(opacity);
			}

			private float calculateOpacityForSwipedThread(int swipedThreadViewWidth, float swipeDeltaX){
				return (float) (1 - 1.5 * (Math.abs(swipeDeltaX) / swipedThreadViewWidth));
			}

			@Override
			public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
				resetOpacityForThread(viewHolder); //need to reset opacity of a thread view holder or it will be transparent when reused
				super.clearView(recyclerView, viewHolder);
			}

			private void resetOpacityForThread(RecyclerView.ViewHolder threadViewHolder) {
				threadViewHolder.itemView.setAlpha(1);
			}

		};

		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
		itemTouchHelper.attachToRecyclerView(recyclerView);
	}

	private void setupRecyclerViewAnimations(RecyclerView recyclerView) {
		RecyclerView.ItemAnimator animator = new DefaultItemAnimator() {

			@Override
			public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
				boolean threadWasSwiped = oldHolder.itemView.getX() != newHolder.itemView.getX();
				if (threadWasSwiped) { // if a thread was swiped to hide - animate appearance of a hidden thread view with fade-in animation instead of default
					dispatchChangeFinished(oldHolder, true);
					animateHiddenThreadFadeIn(newHolder);
					return false;
				}
				return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY);
			}

			private void animateHiddenThreadFadeIn(RecyclerView.ViewHolder hiddenThreadViewHolder) {
				View hiddenThreadRootView = hiddenThreadViewHolder.itemView;
				dispatchChangeStarting(hiddenThreadViewHolder, false);
				hiddenThreadRootView.setAlpha(0);
				hiddenThreadRootView
						.animate()
						.alpha(1)
						.setDuration(getChangeDuration())
						.withEndAction(() -> dispatchChangeFinished(hiddenThreadViewHolder, false))
						.start();
			}
		};

		recyclerView.setItemAnimator(animator);
	}

	@Override
	protected void onResume() {
		super.onResume();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
			retainableExtra.dialogsState = null;
		}
	}

	@Override
	protected void onDestroy() {
		getUiManager().dialog().closeDialogs(getAdapter().getConfigurationSet().stackInstance);
		getUiManager().observable().unregister(this);
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
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
		}
		retainableExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
	}

	@Override
	public Pair<String, String> obtainTitleSubtitle() {
		Page page = getPage();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		String title = getChan().configuration.getBoardTitle(page.boardName);
		title = StringUtils.formatBoardTitle(page.chanName, page.boardName, title);
		String subtitle = null;
		if (retainableExtra.startPageNumber > 0) {
			subtitle = getString(R.string.number_page__format, retainableExtra.startPageNumber);
		} else if (retainableExtra.startPageNumber == PAGE_NUMBER_CATALOG) {
			subtitle = getString(R.string.catalog);
		} else if (retainableExtra.boardSpeed > 0) {
			subtitle = getResources().getQuantityString(R.plurals.number_posts_per_hour__format,
					retainableExtra.boardSpeed, retainableExtra.boardSpeed);
		}
		return new Pair<>(title, subtitle);
	}

	@Override
	public void onItemClick(PostItem postItem) {
		if (postItem != null) {
			Page page = getPage();
			if (postItem.getHideState().hidden) {
				setThreadHideState(postItem, PostItem.HideState.SHOWN);
				getAdapter().notifyThreadShown(postItem);
			} else {
				getUiManager().navigator().navigatePosts(page.chanName, page.boardName,
						postItem.getThreadNumber(), null, postItem.getSubjectOrComment());
			}
		}
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		if (postItem != null) {
			showItemPopupMenu(getFragmentManager(), postItem);
			return true;
		}
		return false;
	}

	private static void showItemPopupMenu(FragmentManager fragmentManager, PostItem postItem) {
		new InstanceDialog(fragmentManager, null, provider -> {
			ThreadsPage threadsPage = extract(provider);
			Page page = threadsPage.getPage();
			DialogMenu dialogMenu = new DialogMenu(provider.getContext());
			dialogMenu.add(R.string.copy_link, () -> {
				Uri uri = threadsPage.getChan().locator.safe(true)
						.createThreadUri(page.boardName, postItem.getThreadNumber());
				if (uri != null) {
					StringUtils.copyToClipboard(provider.getContext(), uri.toString());
				}
			});
			dialogMenu.add(R.string.share_link, () -> {
				Uri uri = threadsPage.getChan().locator.safe(true)
						.createThreadUri(page.boardName, postItem.getThreadNumber());
				String subject = postItem.getSubjectOrComment();
				if (StringUtils.isEmptyOrWhitespace(subject)) {
					subject = uri.toString();
				}
				NavigationUtils.shareLink(provider.getContext(), subject, uri);
			});
			if (!postItem.getHideState().hidden) {
				dialogMenu.add(R.string.hide, () -> {
					threadsPage.setThreadHideState(postItem, PostItem.HideState.HIDDEN);
					threadsPage.getAdapter().notifyThreadHidden(postItem);
				});
			}
			return dialogMenu.create();
		});
	}

	private void setThreadHideState(PostItem postItem, PostItem.HideState hideState) {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		retainableExtra.hiddenThreads.set(postItem.getThreadNumber(), hideState);
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
		for (Preferences.CatalogSort catalogSort : Preferences.CatalogSort.values()) {
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
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		Chan chan = getChan();
		ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(page.boardName);
		this.allowSearch = board.allowSearch;
		boolean isCatalogOpen = retainableExtra.startPageNumber == PAGE_NUMBER_CATALOG;
		menu.findItem(R.id.menu_search).setTitle(board.allowSearch ? R.string.search : R.string.filter);
		menu.findItem(R.id.menu_catalog).setVisible(board.allowCatalog && !isCatalogOpen);
		menu.findItem(R.id.menu_pages).setVisible(board.allowCatalog && isCatalogOpen);
		menu.findItem(R.id.menu_sorting).setVisible(board.allowCatalog && isCatalogOpen);
		menu.findItem(Preferences.getCatalogSort().menuItemId).setChecked(true);
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
				showSummaryDialog(getFragmentManager(), page.chanName, page.boardName);
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
		for (Preferences.CatalogSort catalogSort : Preferences.CatalogSort.values()) {
			if (item.getItemId() == catalogSort.menuItemId) {
				Preferences.setCatalogSort(catalogSort);
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

	private static void showSummaryDialog(FragmentManager fragmentManager, String chanName, String boardName) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Chan chan = Chan.get(chanName);
			Context context = provider.getContext();
			AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(R.string.summary)
					.setPositiveButton(android.R.string.ok, null)
					.create();
			SummaryLayout layout = new SummaryLayout(dialog);
			if (boardName != null) {
				String title = chan.configuration.getBoardTitle(boardName);
				title = StringUtils.formatBoardTitle(chanName, boardName, title);
				layout.add(context.getString(R.string.board), title);
				String description = chan.configuration.getBoardDescription(boardName);
				if (!StringUtils.isEmpty(description)) {
					layout.add(context.getString(R.string.description), description);
				}
			}
			int pagesCount = Math.max(chan.configuration.getPagesCount(boardName), 1);
			if (pagesCount != ChanConfiguration.PAGES_COUNT_INVALID) {
				layout.add(context.getString(R.string.pages_count), Integer.toString(pagesCount));
			}
			ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(boardName);
			ChanConfiguration.Posting posting = board.allowPosting
					? chan.configuration.safe().obtainPosting(boardName, true) : null;
			if (posting != null) {
				int bumpLimit = chan.configuration.getBumpLimit(boardName);
				if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
					layout.add(context.getString(R.string.bump_limit), context.getResources()
							.getQuantityString(R.plurals.number_posts__format, bumpLimit, bumpLimit));
				}
			}
			layout.addDivider();
			if (posting != null) {
				StringBuilder builder = new StringBuilder();
				if (!posting.allowSubject) {
					builder.append("\u2022 ").append(context.getString(R.string.subjects_are_disabled)).append('\n');
				}
				if (!posting.allowName) {
					builder.append("\u2022 ").append(context.getString(R.string.names_are_disabled)).append('\n');
				} else if (!posting.allowTripcode) {
					builder.append("\u2022 ").append(context.getString(R.string.tripcodes_are_disabled)).append('\n');
				}
				if (posting.attachmentCount <= 0) {
					builder.append("\u2022 ").append(context.getString(R.string.images_are_disabled)).append('\n');
				}
				if (!posting.optionSage) {
					builder.append("\u2022 ").append(context.getString(R.string.sage_is_disabled)).append('\n');
				}
				if (posting.hasCountryFlags) {
					builder.append("\u2022 ").append(context.getString(R.string.flags_are_enabled)).append('\n');
				}
				if (posting.userIcons.size() > 0) {
					builder.append("\u2022 ").append(context.getString(R.string.icons_are_enabled)).append('\n');
				}
				if (builder.length() > 0) {
					builder.setLength(builder.length() - 1);
					layout.add(context.getString(R.string.configuration), builder);
				}
			} else {
				layout.add(context.getString(R.string.configuration), context.getString(R.string.read_only));
			}
			return dialog;
		});
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
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		refreshThreads(getAdapter().isRealEmpty() || retainableExtra.startPageNumber == PAGE_NUMBER_CATALOG
				? RefreshPage.CURRENT : side == PullableWrapper.Side.BOTTOM
				? RefreshPage.NEXT : RefreshPage.PREVIOUS, true);
	}

	private enum RefreshPage {CURRENT, PREVIOUS, NEXT, CATALOG}

	private static final int PAGE_NUMBER_CATALOG = ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG;

	private void refreshThreads(RefreshPage refreshPage) {
		refreshThreads(refreshPage, !getAdapter().isRealEmpty());
	}

	private void refreshThreads(RefreshPage refreshPage, boolean showPull) {
		int pageNumber;
		boolean append = false;
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (refreshPage == RefreshPage.CATALOG || refreshPage == RefreshPage.CURRENT &&
				retainableExtra.startPageNumber == PAGE_NUMBER_CATALOG) {
			pageNumber = PAGE_NUMBER_CATALOG;
		} else {
			int currentPageNumber = retainableExtra.startPageNumber;
			if (!retainableExtra.cachedPostItems.isEmpty()) {
				currentPageNumber += retainableExtra.cachedPostItems.size() - 1;
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
		Page page = getPage();
		Chan chan = getChan();
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		PaddedRecyclerView recyclerView = getRecyclerView();
		if (pageNumber < PAGE_NUMBER_CATALOG || pageNumber >=
				Math.max(chan.configuration.getPagesCount(page.boardName), 1)) {
			recyclerView.getPullable().cancelBusyState();
			ClickableToast.show(getString(R.string.number_page_doesnt_exist__format, pageNumber));
			readViewModel.attach(null);
			return false;
		} else {
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			HttpValidator validator = !append && retainableExtra.cachedPostItems.size() == 1
					&& retainableExtra.startPageNumber == pageNumber ? retainableExtra.validator : null;
			ReadThreadsTask task = new ReadThreadsTask(readViewModel.callback,
					chan, page.boardName, pageNumber, validator, append);
			task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
			readViewModel.attach(task);
			if (showPull) {
				recyclerView.getPullable().startBusyState(PullableWrapper.Side.TOP);
				switchList();
			} else {
				recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
				switchProgress();
			}
			return true;
		}
	}

	@Override
	public void onReadThreadsSuccess(List<PostItem> postItems, int pageNumber,
			int boardSpeed, boolean append, boolean checkModified, HttpValidator validator,
			PostItem.HideState.Map<String> hiddenThreads) {
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.getPullable().cancelBusyState();
		switchList();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (postItems != null && postItems.isEmpty()) {
			postItems = null;
		}
		if (retainableExtra.cachedPostItems.isEmpty()) {
			append = false;
		}
		if (hiddenThreads != null) {
			if (!append) {
				retainableExtra.hiddenThreads.clear();
			}
			retainableExtra.hiddenThreads.addAll(hiddenThreads);
		}
		if (postItems != null && append) {
			HashSet<String> threadNumbers = new HashSet<>();
			for (List<PostItem> pagePostItems : retainableExtra.cachedPostItems) {
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
			retainableExtra.validator = validator;
			if (!append) {
				retainableExtra.cachedPostItems.clear();
				retainableExtra.startPageNumber = pageNumber;
				retainableExtra.boardSpeed = boardSpeed;
			}
			retainableExtra.cachedPostItems.add(postItems);
			notifyTitleChanged();
			updateOptionsMenu();
			if (oldCount == 0 && !adapter.isRealEmpty()) {
				showScaleAnimation();
			}
		} else if (checkModified && postItems == null) {
			adapter.notifyNotModified();
			recyclerView.scrollToPosition(0);
		} else if (adapter.isRealEmpty()) {
			switchError(R.string.empty_response);
		} else {
			ClickableToast.show(R.string.empty_response);
		}
	}

	@Override
	public void onReadThreadsRedirect(RedirectException.Target target) {
		getRecyclerView().getPullable().cancelBusyState();
		if (!CommonUtils.equals(target.chanName, getPage().chanName)) {
			if (getAdapter().isRealEmpty()) {
				switchError(R.string.board_doesnt_exist);
			}
			showRedirectDialog(getFragmentManager(), target);
		} else {
			handleRedirect(target.chanName, target.boardName, null, null);
		}
	}

	@Override
	public void onReadThreadsFail(ErrorItem errorItem, int pageNumber) {
		getRecyclerView().getPullable().cancelBusyState();
		String message = errorItem.type == ErrorItem.Type.BOARD_NOT_EXISTS && pageNumber >= 1
				? getString(R.string.number_page_doesnt_exist__format, pageNumber) : errorItem.toString();
		if (getAdapter().isRealEmpty()) {
			switchError(message);
		} else {
			ClickableToast.show(message);
		}
	}

	private static void showRedirectDialog(FragmentManager fragmentManager, RedirectException.Target target) {
		String tag = ThreadsPage.class.getName() + ":Redirect";
		new InstanceDialog(fragmentManager, tag, provider -> {
			ThreadsPage threadsPage = extract(provider);
			String message = provider.getContext().getString(R.string.open_forum__format_sentence,
					Chan.get(target.chanName).configuration.getTitle());
			return new AlertDialog.Builder(provider.getContext())
					.setMessage(message)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, (d, which) -> threadsPage
							.handleRedirect(target.chanName, target.boardName, null, null))
					.create();
		});
	}

	@Override
	public void onReloadAttachmentItem(AttachmentItem attachmentItem) {
		getAdapter().reloadAttachment(attachmentItem);
	}
}
