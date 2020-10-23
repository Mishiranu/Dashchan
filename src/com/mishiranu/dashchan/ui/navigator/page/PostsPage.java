package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.content.RedirectException;
import chan.text.JsonSerial;
import chan.text.ParseException;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ExtractPostsTask;
import com.mishiranu.dashchan.content.async.ReadPostsTask;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PendingUserPost;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.ThreadshotPerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.SearchHelper;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PostsLayoutManager;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import com.mishiranu.dashchan.widget.SummaryLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PostsPage extends ListPage implements PostsAdapter.Callback, FavoritesStorage.Observer,
		UiManager.Observer, ExtractPostsTask.Callback, ReadPostsTask.Callback, ActionMode.Callback {
	private enum QueuedRefresh {
		NONE, REFRESH, RELOAD;

		public static QueuedRefresh max(QueuedRefresh queuedRefresh1, QueuedRefresh queuedRefresh2) {
			return values()[Math.max(queuedRefresh1.ordinal(), queuedRefresh2.ordinal())];
		}
	}

	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public PagesDatabase.Cache cache;
		public final HashMap<PostNumber, PostItem> postItems = new HashMap<>();
		public final PostItem.HideState.Map<PostNumber> hiddenPosts = new PostItem.HideState.Map<>();
		public final HashSet<PostNumber> userPosts = new HashSet<>();
		public boolean removeDeleted;
		public byte[] threadExtra;

		public Uri archivedThreadUri;
		public int uniquePosters;

		public final ArrayList<Integer> searchFoundPosts = new ArrayList<>();
		public boolean searching = false;
		public int searchLastPosition;

		public DialogUnit.StackInstance.State dialogsState;
	}

	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public final HashSet<PostNumber> expandedPosts = new HashSet<>();
		public final HashSet<PostNumber> unreadPosts = new HashSet<>();
		public boolean isAddedToHistory = false;
		public QueuedRefresh queuedRefresh = QueuedRefresh.NONE;
		public String threadTitle;
		public PostNumber newPostNumber;
		public PostNumber scrollToPostNumber;
		public Set<PostNumber> selectedPosts;

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(expandedPosts.size());
			for (PostNumber number : expandedPosts) {
				number.writeToParcel(dest, flags);
			}
			dest.writeInt(unreadPosts.size());
			for (PostNumber number : unreadPosts) {
				number.writeToParcel(dest, flags);
			}
			dest.writeByte((byte) (isAddedToHistory ? 1 : 0));
			dest.writeString(queuedRefresh.name());
			dest.writeString(threadTitle);
			dest.writeByte((byte) (newPostNumber != null ? 1 : 0));
			if (newPostNumber != null) {
				newPostNumber.writeToParcel(dest, flags);
			}
			dest.writeByte((byte) (scrollToPostNumber != null ? 1 : 0));
			if (scrollToPostNumber != null) {
				scrollToPostNumber.writeToParcel(dest, flags);
			}
			dest.writeInt(selectedPosts != null ? selectedPosts.size() : -1);
			if (selectedPosts != null) {
				for (PostNumber number : selectedPosts) {
					number.writeToParcel(dest, flags);
				}
			}
		}

		public static final Creator<ParcelableExtra> CREATOR = new Creator<ParcelableExtra>() {
			@Override
			public ParcelableExtra createFromParcel(Parcel source) {
				ParcelableExtra parcelableExtra = new ParcelableExtra();
				int expandedPostsCount = source.readInt();
				for (int i = 0; i < expandedPostsCount; i++) {
					parcelableExtra.expandedPosts.add(PostNumber.CREATOR.createFromParcel(source));
				}
				int unreadPostsCount = source.readInt();
				for (int i = 0; i < unreadPostsCount; i++) {
					parcelableExtra.unreadPosts.add(PostNumber.CREATOR.createFromParcel(source));
				}
				parcelableExtra.isAddedToHistory = source.readByte() != 0;
				parcelableExtra.queuedRefresh = QueuedRefresh.valueOf(source.readString());
				parcelableExtra.threadTitle = source.readString();
				if (source.readByte() != 0) {
					parcelableExtra.newPostNumber = PostNumber.CREATOR.createFromParcel(source);
				}
				if (source.readByte() != 0) {
					parcelableExtra.scrollToPostNumber = PostNumber.CREATOR.createFromParcel(source);
				}
				int selectedPostsCount = source.readInt();
				if (selectedPostsCount >= 0) {
					HashSet<PostNumber> selectedPosts = new HashSet<>(selectedPostsCount);
					for (int i = 0; i < selectedPostsCount; i++) {
						selectedPosts.add(PostNumber.CREATOR.createFromParcel(source));
					}
					parcelableExtra.selectedPosts = selectedPosts;
				}
				return parcelableExtra;
			}

			@Override
			public ParcelableExtra[] newArray(int size) {
				return new ParcelableExtra[size];
			}
		};
	}

	private ExtractPostsTask extractTask;
	private ReadPostsTask readTask;

	private Replyable replyable;
	private HidePerformer hidePerformer;

	private ActionMode selectionMode;

	private LinearLayout searchController;
	private Button searchTextResult;

	private final ArrayList<PostNumber> lastEditedPostNumbers = new ArrayList<>();

	private final UiManager.PostStateProvider postStateProvider = new UiManager.PostStateProvider() {
		@Override
		public boolean isHiddenResolve(PostItem postItem) {
			if (postItem.getHideState() == PostItem.HideState.UNDEFINED) {
				RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
				PostItem.HideState hideState = retainExtra.hiddenPosts.get(postItem.getPostNumber());
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

		@Override
		public boolean isUserPost(PostNumber postNumber) {
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			return retainExtra.userPosts.contains(postNumber);
		}

		@Override
		public boolean isExpanded(PostNumber postNumber) {
			ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
			return parcelableExtra.expandedPosts.contains(postNumber);
		}

		@Override
		public void setExpanded(PostNumber postNumber) {
			ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
			parcelableExtra.expandedPosts.add(postNumber);
		}

		@Override
		public boolean isRead(PostNumber postNumber) {
			ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
			return !parcelableExtra.unreadPosts.contains(postNumber);
		}

		@Override
		public void setRead(PostNumber postNumber) {
			ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
			parcelableExtra.unreadPosts.remove(postNumber);
		}
	};

	private PostsAdapter getAdapter() {
		return (PostsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		Context context = getContext();
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new PostsLayoutManager(recyclerView.getContext()));
		Page page = getPage();
		UiManager uiManager = getUiManager();
		float density = ResourceUtils.obtainDensity(context);
		int dividerPadding = (int) (12f * density);
		hidePerformer = new HidePerformer(context);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		replyable = (click, data) -> {
			ChanConfiguration.Board board = getChan().configuration.safe().obtainBoard(page.boardName);
			if (click && board.allowPosting) {
				getUiManager().navigator().navigatePosting(page.chanName, page.boardName,
						page.threadNumber, data);
			}
			return board.allowPosting;
		};
		PostsAdapter adapter = new PostsAdapter(this, page.chanName, uiManager,
				replyable, postStateProvider, recyclerView, retainExtra.postItems);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> adapter.configureDivider(c, position).horizontal(dividerPadding, dividerPadding)));
		recyclerView.addItemDecoration(adapter.createPostItemDecoration(context, dividerPadding));
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		recyclerView.addOnScrollListener(scrollListener);
		uiManager.observable().register(this);
		hidePerformer.setPostsProvider(adapter);

		Context darkStyledContext = new ContextThemeWrapper(context, R.style.Theme_Main_Dark);
		searchController = new LinearLayout(darkStyledContext);
		searchController.setOrientation(LinearLayout.HORIZONTAL);
		searchController.setGravity(Gravity.CENTER_VERTICAL);
		int buttonPadding = (int) (10f * density);
		searchTextResult = new Button(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		ViewUtils.setTextSizeScaled(searchTextResult, 11);
		if (!C.API_LOLLIPOP) {
			searchTextResult.setTypeface(null, Typeface.BOLD);
		}
		searchTextResult.setPadding((int) (14f * density), 0, (int) (14f * density), 0);
		searchTextResult.setMinimumWidth(0);
		searchTextResult.setMinWidth(0);
		searchTextResult.setOnClickListener(v -> showSearchDialog());
		searchController.addView(searchTextResult, LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		ImageView backButtonView = new ImageView(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		backButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		backButtonView.setImageDrawable(getActionBarIcon(R.attr.iconActionBack));
		backButtonView.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
		backButtonView.setOnClickListener(v -> findBack());
		searchController.addView(backButtonView, (int) (48f * density), (int) (48f * density));
		ImageView forwardButtonView = new ImageView(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		forwardButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		forwardButtonView.setImageDrawable(getActionBarIcon(R.attr.iconActionForward));
		forwardButtonView.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
		forwardButtonView.setOnClickListener(v -> findForward());
		searchController.addView(forwardButtonView, (int) (48f * density), (int) (48f * density));
		if (C.API_LOLLIPOP) {
			for (int i = 0, last = searchController.getChildCount() - 1; i <= last; i++) {
				View view = searchController.getChildAt(i);
				LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
				if (i == 0) {
					layoutParams.leftMargin = (int) (-6f * density);
				}
				if (i == last) {
					layoutParams.rightMargin = (int) (6f * density);
				} else {
					layoutParams.rightMargin = (int) (-6f * density);
				}
			}
		}

		InitRequest initRequest = getInitRequest();
		if (initRequest.threadTitle != null && parcelableExtra.threadTitle == null) {
			parcelableExtra.threadTitle = initRequest.threadTitle;
		}
		if (initRequest.postNumber != null) {
			parcelableExtra.scrollToPostNumber = initRequest.postNumber;
		}
		FavoritesStorage.getInstance().getObservable().register(this);
		boolean hasNewPosts = consumeNewPostData();
		QueuedRefresh queuedRefresh = initRequest.shouldLoad || hasNewPosts
				? QueuedRefresh.REFRESH : QueuedRefresh.NONE;
		parcelableExtra.queuedRefresh = QueuedRefresh.max(parcelableExtra.queuedRefresh, queuedRefresh);
		if (retainExtra.cache != null && retainExtra.postItems.size() > 0) {
			String searchSubmitQuery = getInitSearch().submitQuery;
			if (searchSubmitQuery == null) {
				retainExtra.searching = false;
			}
			if (retainExtra.searching && !retainExtra.searchFoundPosts.isEmpty()) {
				setCustomSearchView(searchController);
				updateSearchTitle();
			}
			decodeThreadExtra();
			onExtractPostsCompleteInternal(true, true, true, null);
			if (retainExtra.dialogsState != null) {
				uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainExtra.dialogsState);
				retainExtra.dialogsState = null;
			}
		} else {
			retainExtra.cache = null;
			if (!retainExtra.postItems.isEmpty()) {
				throw new IllegalStateException();
			}
			retainExtra.searching = false;
			extractPosts(true, false);
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	protected void onResume() {
		queueNextRefresh(true);
	}

	@Override
	protected void onPause() {
		stopRefresh();
	}

	@Override
	protected void onDestroy() {
		if (selectionMode != null) {
			selectionMode.finish();
			selectionMode = null;
		}
		getAdapter().cancelPreloading();
		getUiManager().dialog().closeDialogs(getAdapter().getConfigurationSet().stackInstance);
		getUiManager().observable().unregister(this);
		cancelTasks();
		getRecyclerView().removeOnScrollListener(scrollListener);
		if (AndroidUtils.hasCallbacks(ConcurrentUtils.HANDLER, storePositionRunnable)) {
			ConcurrentUtils.HANDLER.removeCallbacks(storePositionRunnable);
			storePositionRunnable.run();
		}
		FavoritesStorage.getInstance().getObservable().unregister(this);
		setCustomSearchView(null);
	}

	@Override
	protected void onNotifyAllAdaptersChanged() {
		getUiManager().dialog().notifyDataSetChangedToAll(getAdapter().getConfigurationSet().stackInstance);
	}

	@Override
	protected void onHandleNewPostDataList() {
		if (consumeNewPostData()) {
			refreshPosts(false);
		}
	}

	@Override
	protected void onScrollToPost(PostNumber postNumber) {
		int position = getAdapter().positionOfPostNumber(postNumber);
		if (position >= 0) {
			getUiManager().dialog().closeDialogs(getAdapter().getConfigurationSet().stackInstance);
			ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
		}
	}

	@Override
	protected void onRequestStoreExtra(boolean saveToStack) {
		PostsAdapter adapter = getAdapter();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (readTask != null && !saveToStack) {
			boolean reload = readTask.isLoadFullThread();
			parcelableExtra.queuedRefresh = QueuedRefresh.max(parcelableExtra.queuedRefresh,
					reload ? QueuedRefresh.RELOAD : QueuedRefresh.REFRESH);
		}
		parcelableExtra.selectedPosts = null;
		if (selectionMode != null && !saveToStack) {
			ArrayList<PostItem> selected = adapter.getSelectedItems();
			parcelableExtra.selectedPosts = new HashSet<>(selected.size());
			for (PostItem postItem : selected) {
				parcelableExtra.selectedPosts.add(postItem.getPostNumber());
			}
		}
	}

	@Override
	public String obtainTitle() {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (!StringUtils.isEmptyOrWhitespace(parcelableExtra.threadTitle)) {
			return parcelableExtra.threadTitle;
		} else {
			Page page = getPage();
			return StringUtils.formatThreadTitle(page.chanName, page.boardName, page.threadNumber);
		}
	}

	@Override
	public void onItemClick(View view, PostItem postItem) {
		if (selectionMode != null) {
			getAdapter().toggleItemSelected(postItem);
			selectionMode.setTitle(ResourceUtils.getColonString(getResources(), R.string.selected,
					getAdapter().getSelectedCount()));
			return;
		}
		getUiManager().interaction().handlePostClick(view, postStateProvider, postItem, getAdapter());
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		if (selectionMode != null) {
			return false;
		}
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		boolean userPost = retainExtra.userPosts.contains(postItem.getPostNumber());
		return postItem != null && getUiManager().interaction()
				.handlePostContextMenu(getChan(), postItem, replyable, userPost, true, true, false);
	}

	private void setPostUserPost(PostItem postItem, boolean userPost) {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (userPost) {
			retainExtra.userPosts.add(postItem.getPostNumber());
		} else {
			retainExtra.userPosts.remove(postItem.getPostNumber());
		}
		CommonDatabase.getInstance().getPosts().setFlags(true, getPage().chanName, postItem.getBoardName(),
				postItem.getThreadNumber(), postItem.getPostNumber(),
				retainExtra.hiddenPosts.get(postItem.getPostNumber()), userPost);
	}

	private void setPostHideState(PostItem postItem, PostItem.HideState hideState) {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.hiddenPosts.set(postItem.getPostNumber(), hideState);
		CommonDatabase.getInstance().getPosts().setFlags(true, getPage().chanName, postItem.getBoardName(),
				postItem.getThreadNumber(), postItem.getPostNumber(),
				hideState, retainExtra.userPosts.contains(postItem.getPostNumber()));
		postItem.setHidden(hideState, null);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, R.id.menu_add_post, 0, R.string.reply)
				.setIcon(getActionBarIcon(R.attr.iconActionAddPost))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_search, 0, R.string.search)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, R.id.menu_gallery, 0, R.string.gallery);
		menu.add(0, R.id.menu_select, 0, R.string.select);
		menu.add(0, R.id.menu_refresh, 0, R.string.refresh)
				.setIcon(getActionBarIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.appearance);
		SubMenu threadOptions = menu.addSubMenu(0, R.id.menu_thread_options, 0, R.string.thread_options);
		menu.add(0, R.id.menu_star_text, 0, R.string.add_to_favorites);
		menu.add(0, R.id.menu_unstar_text, 0, R.string.remove_from_favorites);
		menu.add(0, R.id.menu_star_icon, 0, R.string.add_to_favorites)
				.setIcon(getActionBarIcon(R.attr.iconActionAddToFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_unstar_icon, 0, R.string.remove_from_favorites)
				.setIcon(getActionBarIcon(R.attr.iconActionRemoveFromFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, R.id.menu_open_original_thread, 0, R.string.open_original);
		menu.add(0, R.id.menu_archive, 0, R.string.archive__verb);

		threadOptions.add(0, R.id.menu_reload, 0, R.string.reload);
		threadOptions.add(0, R.id.menu_hidden_posts, 0, R.string.hidden_posts);
		threadOptions.add(0, R.id.menu_clear, 0, R.string.clear_deleted);
		threadOptions.add(0, R.id.menu_summary, 0, R.string.summary);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		menu.findItem(R.id.menu_add_post).setVisible(replyable != null && replyable.onRequestReply(false));
		boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(page.chanName, page.boardName,
				page.threadNumber);
		boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
		menu.findItem(R.id.menu_star_text).setVisible(!iconFavorite && !isFavorite);
		menu.findItem(R.id.menu_unstar_text).setVisible(!iconFavorite && isFavorite);
		menu.findItem(R.id.menu_star_icon).setVisible(iconFavorite && !isFavorite);
		menu.findItem(R.id.menu_unstar_icon).setVisible(iconFavorite && isFavorite);
		menu.findItem(R.id.menu_open_original_thread)
				.setVisible(Chan.getPreferred(null, retainExtra.archivedThreadUri).name != null);
		boolean canBeArchived = !ChanManager.getInstance().getArchiveChanNames(page.chanName).isEmpty() ||
				!getChan().configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
		menu.findItem(R.id.menu_archive).setVisible(canBeArchived);
		menu.findItem(R.id.menu_hidden_posts).setEnabled(hidePerformer.hasLocalFilters());
		menu.findItem(R.id.menu_clear).setEnabled(getAdapter().hasDeletedPosts());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Page page = getPage();
		PostsAdapter adapter = getAdapter();
		switch (item.getItemId()) {
			case R.id.menu_add_post: {
				getUiManager().navigator().navigatePosting(page.chanName, page.boardName,
						page.threadNumber);
				return true;
			}
			case R.id.menu_gallery: {
				int imageIndex = -1;
				RecyclerView recyclerView = getRecyclerView();
				View child = recyclerView.getChildAt(0);
				GalleryItem.Set gallerySet = getAdapter().getConfigurationSet().gallerySet;
				if (child != null) {
					int position = recyclerView.getChildAdapterPosition(child);
					OUTER: for (int v = 0; v <= 1; v++) {
						for (PostItem postItem : adapter.iterate(v == 0, position)) {
							imageIndex = gallerySet.findIndex(postItem);
							if (imageIndex >= 0) {
								break OUTER;
							}
						}
					}
				}
				getUiManager().navigator().navigateGallery(page.chanName, gallerySet, imageIndex,
						null, GalleryOverlay.NavigatePostMode.ENABLED, true);
				return true;
			}
			case R.id.menu_select: {
				selectionMode = startActionMode(this);
				return true;
			}
			case R.id.menu_refresh: {
				refreshPosts(false);
				return true;
			}
			case R.id.menu_star_text:
			case R.id.menu_star_icon: {
				FavoritesStorage.getInstance().add(page.chanName, page.boardName,
						page.threadNumber, getParcelableExtra(ParcelableExtra.FACTORY).threadTitle,
						adapter.getExistingPostsCount());
				updateOptionsMenu();
				return true;
			}
			case R.id.menu_unstar_text:
			case R.id.menu_unstar_icon: {
				FavoritesStorage.getInstance().remove(page.chanName, page.boardName,
						page.threadNumber);
				updateOptionsMenu();
				return true;
			}
			case R.id.menu_open_original_thread: {
				RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
				Chan chan = Chan.getPreferred(null, retainExtra.archivedThreadUri);
				if (chan.name != null) {
					Uri uri = retainExtra.archivedThreadUri;
					String boardName = chan.locator.safe(true).getBoardName(uri);
					String threadNumber = chan.locator.safe(true).getThreadNumber(uri);
					if (threadNumber != null) {
						String threadTitle = getAdapter().getItem(0).getSubjectOrComment();
						getUiManager().navigator().navigatePosts(chan.name, boardName, threadNumber, null, threadTitle);
					}
				}
				return true;
			}
			case R.id.menu_archive: {
				String threadTitle = null;
				ArrayList<Post> posts = new ArrayList<>();
				for (PostItem postItem : adapter) {
					if (threadTitle == null) {
						threadTitle = StringUtils.emptyIfNull(postItem.getSubjectOrComment());
					}
					posts.add(postItem.getPost());
				}
				if (!posts.isEmpty()) {
					getUiManager().dialog().performSendArchiveThread(page.chanName, page.boardName,
							page.threadNumber, threadTitle, posts);
				}
				return true;
			}
			case R.id.menu_reload: {
				refreshPosts(true);
				return true;
			}
			case R.id.menu_hidden_posts: {
				List<String> localFilters = hidePerformer.getReadableLocalFilters(getContext());
				final boolean[] checked = new boolean[localFilters.size()];
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.remove_rules)
						.setMultiChoiceItems(CommonUtils.toArray(localFilters, String.class),
								checked, (d, which, isChecked) -> checked[which] = isChecked)
						.setPositiveButton(android.R.string.ok, (d, which) -> {
							boolean hasDeleted = false;
							for (int i = 0, j = 0; i < checked.length; i++, j++) {
								if (checked[i]) {
									hidePerformer.removeLocalFilter(j--);
									hasDeleted = true;
								}
							}
							if (hasDeleted) {
								adapter.invalidateHidden();
								notifyAllAdaptersChanged();
								encodeAndStoreThreadExtra();
								adapter.preloadPosts(((LinearLayoutManager) getRecyclerView().getLayoutManager())
										.findFirstVisibleItemPosition());
							}
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
				return true;
			}
			case R.id.menu_clear: {
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setMessage(R.string.deleted_posts_will_be_deleted__sentence)
						.setPositiveButton(android.R.string.ok, (d, which) -> {
							RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
							retainExtra.removeDeleted = true;
							if (extractTask == null && readTask == null) {
								extractPosts(false, false);
								getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTTOM);
							}
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
				return true;
			}
			case R.id.menu_summary: {
				RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
				int files = 0;
				int postsWithFiles = 0;
				int links = 0;
				for (PostItem postItem : getAdapter()) {
					List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
					if (attachmentItems != null) {
						int itFiles = 0;
						for (AttachmentItem attachmentItem : attachmentItems) {
							AttachmentItem.GeneralType generalType = attachmentItem.getGeneralType();
							switch (generalType) {
								case FILE:
								case EMBEDDED: {
									itFiles++;
									break;
								}
								case LINK: {
									links++;
									break;
								}
							}
						}
						if (itFiles > 0) {
							postsWithFiles++;
							files += itFiles;
						}
					}
				}
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.summary)
						.setPositiveButton(android.R.string.ok, null)
						.create();
				SummaryLayout layout = new SummaryLayout(dialog);
				String boardName = page.boardName;
				if (boardName != null) {
					String title = getChan().configuration.getBoardTitle(boardName);
					title = StringUtils.formatBoardTitle(page.chanName, boardName, title);
					layout.add(getString(R.string.board), title);
				}
				layout.add(getString(R.string.files__genitive), Integer.toString(files));
				layout.add(getString(R.string.posts_with_files__genitive), Integer.toString(postsWithFiles));
				layout.add(getString(R.string.links_attachments__genitive), Integer.toString(links));
				if (retainExtra.uniquePosters > 0) {
					layout.add(getString(R.string.unique_posters__genitive),
							Integer.toString(retainExtra.uniquePosters));
				}
				dialog.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
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
				if (favoriteItem.equals(page.chanName, page.boardName, page.threadNumber)) {
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
			case R.id.menu_my_posts:
			case R.id.menu_sfw_mode: {
				notifyAllAdaptersChanged();
				break;
			}
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		Page page = getPage();
		Chan chan = getChan();
		getAdapter().setSelectionModeEnabled(true);
		mode.setTitle(ResourceUtils.getColonString(getResources(),
				R.string.selected, getAdapter().getSelectedCount()));
		ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(page.boardName);
		menu.add(0, R.id.menu_make_threadshot, 0, R.string.make_threadshot)
				.setIcon(getActionBarIcon(R.attr.iconActionMakeThreadshot))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		if (replyable != null && replyable.onRequestReply(false)) {
			menu.add(0, R.id.menu_reply, 0, R.string.reply)
					.setIcon(getActionBarIcon(R.attr.iconActionPaste))
					.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		}
		if (board.allowDeleting) {
			ChanConfiguration.Deleting deleting = chan.configuration.safe().obtainDeleting(page.boardName);
			if (deleting != null && deleting.multiplePosts) {
				menu.add(0, R.id.menu_delete, 0, R.string.delete)
						.setIcon(getActionBarIcon(R.attr.iconActionDelete))
						.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			}
		}
		if (board.allowReporting) {
			ChanConfiguration.Reporting reporting = chan.configuration.safe().obtainReporting(page.boardName);
			if (reporting != null && reporting.multiplePosts) {
				menu.add(0, R.id.menu_report, 0, R.string.report)
						.setIcon(getActionBarIcon(R.attr.iconActionReport))
						.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			}
		}
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_make_threadshot: {
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				if (postItems.size() > 0) {
					Page page = getPage();
					String threadTitle = getAdapter().getConfigurationSet().gallerySet.getThreadTitle();
					new ThreadshotPerformer(getRecyclerView(), getUiManager(), page.chanName, page.boardName,
							page.threadNumber, threadTitle, postItems);
				}
				mode.finish();
				return true;
			}
			case R.id.menu_reply: {
				ArrayList<Replyable.ReplyData> data = new ArrayList<>();
				for (PostItem postItem : getAdapter().getSelectedItems()) {
					data.add(new Replyable.ReplyData(postItem.getPostNumber(), null));
				}
				if (data.size() > 0) {
					replyable.onRequestReply(true, CommonUtils.toArray(data, Replyable.ReplyData.class));
				}
				mode.finish();
				return true;
			}
			case R.id.menu_delete: {
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				ArrayList<PostNumber> postNumbers = new ArrayList<>();
				for (PostItem postItem : postItems) {
					if (!postItem.isDeleted()) {
						postNumbers.add(postItem.getPostNumber());
					}
				}
				if (postNumbers.size() > 0) {
					Page page = getPage();
					getUiManager().dialog().performSendDeletePosts(page.chanName, page.boardName,
							page.threadNumber, postNumbers);
				}
				mode.finish();
				return true;
			}
			case R.id.menu_report: {
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				ArrayList<PostNumber> postNumbers = new ArrayList<>();
				for (PostItem postItem : postItems) {
					if (!postItem.isDeleted()) {
						postNumbers.add(postItem.getPostNumber());
					}
				}
				if (postNumbers.size() > 0) {
					Page page = getPage();
					getUiManager().dialog().performSendReportPosts(page.chanName, page.boardName,
							page.threadNumber, postNumbers);
				}
				mode.finish();
				return true;
			}
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		getAdapter().setSelectionModeEnabled(false);
		selectionMode = null;
	}

	@Override
	public SearchSubmitResult onSearchSubmit(String query) {
		PostsAdapter adapter = getAdapter();
		if (adapter.getItemCount() == 0) {
			return SearchSubmitResult.COLLAPSE;
		}
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		retainExtra.searchFoundPosts.clear();
		int listPosition = ((LinearLayoutManager) getRecyclerView().getLayoutManager())
				.findFirstVisibleItemPosition();
		retainExtra.searchLastPosition = 0;
		boolean positionDefined = false;
		Chan chan = getChan();
		Locale locale = Locale.getDefault();
		SearchHelper helper = new SearchHelper(Preferences.isAdvancedSearch());
		helper.setFlags("m", "r", "a", "d", "e", "n", "op");
		HashSet<String> queries = helper.handleQueries(locale, query);
		HashSet<String> fileNames = new HashSet<>();
		int newPostPosition = -1;
		if (parcelableExtra.newPostNumber != null) {
			newPostPosition = adapter.positionOfPostNumber(parcelableExtra.newPostNumber);
		}
		OUTER: for (int i = 0; i < adapter.getItemCount(); i++) {
			PostItem postItem = adapter.getItem(i);
			if (!postStateProvider.isHiddenResolve(postItem)) {
				PostNumber postNumber = postItem.getPostNumber();
				String comment = postItem.getComment(chan).toString().toLowerCase(locale);
				int postPosition = getAdapter().positionOfPostNumber(postNumber);
				boolean userPost = retainExtra.userPosts.contains(postNumber);
				boolean reply = false;
				for (PostNumber referenceTo : postItem.getReferencesTo()) {
					if (retainExtra.userPosts.contains(referenceTo)) {
						reply = true;
						break;
					}
				}
				boolean hasAttachments = postItem.hasAttachments();
				boolean deleted = postItem.isDeleted();
				boolean edited = lastEditedPostNumbers.contains(postNumber);
				boolean newPost = newPostPosition >= 0 && postPosition >= newPostPosition;
				boolean originalPoster = postItem.isOriginalPoster();
				if (!helper.checkFlags("m", userPost, "r", reply, "a", hasAttachments, "d", deleted, "e", edited,
						"n", newPost, "op", originalPoster)) {
					continue;
				}
				for (String lowQuery : helper.getExcluded()) {
					if (comment.contains(lowQuery)) {
						continue OUTER;
					}
				}
				String subject = postItem.getSubject().toLowerCase(locale);
				String name = postItem.getFullName(chan).toString().toLowerCase(locale);
				fileNames.clear();
				List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
				if (attachmentItems != null) {
					for (AttachmentItem attachmentItem : attachmentItems) {
						String fileName = attachmentItem.getFileName(chan);
						if (!StringUtils.isEmpty(fileName)) {
							fileNames.add(fileName.toLowerCase(locale));
							String originalName = attachmentItem.getOriginalName();
							if (!StringUtils.isEmpty(originalName)) {
								fileNames.add(originalName.toLowerCase(locale));
							}
						}
					}
				}
				boolean found = false;
				if (helper.hasIncluded()) {
					QUERIES: for (String lowQuery : helper.getIncluded()) {
						if (comment.contains(lowQuery)) {
							found = true;
							break;
						} else if (subject.contains(lowQuery)) {
							found = true;
							break;
						} else if (name.contains(lowQuery)) {
							found = true;
							break;
						} else {
							for (String fileName : fileNames) {
								if (fileName.contains(lowQuery)) {
									found = true;
									break QUERIES;
								}
							}
						}
					}
				} else {
					found = true;
				}
				if (found) {
					if (!positionDefined && i > listPosition) {
						retainExtra.searchLastPosition = retainExtra.searchFoundPosts.size();
						positionDefined = true;
					}
					retainExtra.searchFoundPosts.add(i);
				}
			}
		}
		boolean found = !retainExtra.searchFoundPosts.isEmpty();
		getAdapter().setHighlightText(found ? queries : Collections.emptyList());
		retainExtra.searching = true;
		if (found) {
			setCustomSearchView(searchController);
			retainExtra.searchLastPosition--;
			findForward();
			return SearchSubmitResult.ACCEPT;
		} else {
			setCustomSearchView(null);
			ToastUtils.show(getContext(), R.string.not_found);
			retainExtra.searchLastPosition = -1;
			updateSearchTitle();
			return SearchSubmitResult.DISCARD;
		}
	}

	@Override
	public void onSearchCancel() {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (retainExtra.searching) {
			retainExtra.searching = false;
			setCustomSearchView(null);
			updateOptionsMenu();
			getAdapter().setHighlightText(Collections.emptyList());
		}
	}

	private void showSearchDialog() {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (!retainExtra.searchFoundPosts.isEmpty()) {
			PostsAdapter adapter = getAdapter();
			HashSet<PostNumber> postNumbers = new HashSet<>();
			for (Integer position : retainExtra.searchFoundPosts) {
				PostItem postItem = adapter.getItem(position);
				postNumbers.add(postItem.getPostNumber());
			}
			getUiManager().dialog().displayList(adapter.getConfigurationSet(), postNumbers);
		}
	}

	private void findBack() {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		int count = retainExtra.searchFoundPosts.size();
		if (count > 0) {
			retainExtra.searchLastPosition--;
			if (retainExtra.searchLastPosition < 0) {
				retainExtra.searchLastPosition += count;
			}
			ListViewUtils.smoothScrollToPosition(getRecyclerView(),
					retainExtra.searchFoundPosts.get(retainExtra.searchLastPosition));
			updateSearchTitle();
		}
	}

	private void findForward() {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		int count = retainExtra.searchFoundPosts.size();
		if (count > 0) {
			retainExtra.searchLastPosition++;
			if (retainExtra.searchLastPosition >= count) {
				retainExtra.searchLastPosition -= count;
			}
			ListViewUtils.smoothScrollToPosition(getRecyclerView(),
					retainExtra.searchFoundPosts.get(retainExtra.searchLastPosition));
			updateSearchTitle();
		}
	}

	private void updateSearchTitle() {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		searchTextResult.setText((retainExtra.searchLastPosition + 1) + "/" + retainExtra.searchFoundPosts.size());
	}

	private boolean consumeNewPostData() {
		Page page = getPage();
		return PostingService.consumeNewPostData(getContext(), page.chanName, page.boardName, page.threadNumber);
	}

	@Override
	public int onDrawerNumberEntered(int number) {
		PostsAdapter adapter = getAdapter();
		int count = adapter.getItemCount();
		boolean success = false;
		if (count > 0 && number > 0) {
			if (number <= count) {
				int position = adapter.positionOfOrdinalIndex(number - 1);
				if (position >= 0) {
					ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
					success = true;
				}
			}
			if (!success) {
				int position = adapter.positionOfPostNumber(new PostNumber(number, 0));
				if (position >= 0) {
					ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
					success = true;
				} else {
					ToastUtils.show(getContext(), R.string.post_is_not_found);
				}
			}
		}
		int result = DrawerForm.RESULT_REMOVE_ERROR_MESSAGE;
		if (success) {
			result |= DrawerForm.RESULT_SUCCESS;
		}
		return result;
	}

	@Override
	public void updatePageConfiguration(PostNumber postNumber) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.scrollToPostNumber = postNumber;
		if (extractTask == null && readTask == null) {
			if (!scrollToPostFromExtra(false)) {
				refreshPosts(false);
			}
		}
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshPosts(false, true);
	}

	private boolean scrollToPostFromExtra(boolean instantly) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.scrollToPostNumber != null) {
			int position = getAdapter().positionOfPostNumber(parcelableExtra.scrollToPostNumber);
			if (position >= 0) {
				if (instantly) {
					((LinearLayoutManager) getRecyclerView().getLayoutManager())
							.scrollToPositionWithOffset(position, 0);
				} else {
					ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
				}
				parcelableExtra.scrollToPostNumber = null;
				return true;
			}
		}
		return false;
	}

	private void decodeThreadExtra() {
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		boolean localFiltersDecoded = false;
		if (retainExtra.threadExtra != null) {
			try (JsonSerial.Reader reader = JsonSerial.reader(retainExtra.threadExtra)) {
				reader.startObject();
				while (!reader.endStruct()) {
					switch (reader.nextName()) {
						case "filters": {
							hidePerformer.decodeLocalFilters(reader);
							localFiltersDecoded = true;
							break;
						}
						default: {
							reader.skip();
							break;
						}
					}
				}
			} catch (ParseException e) {
				Log.persistent().stack(e);
				retainExtra.threadExtra = null;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		if (!localFiltersDecoded) {
			try {
				hidePerformer.decodeLocalFilters(null);
			} catch (ParseException e) {
				Log.persistent().stack(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void encodeAndStoreThreadExtra() {
		byte[] extra = null;
		if (hidePerformer.hasLocalFilters()) {
			try (JsonSerial.Writer writer = JsonSerial.writer()) {
				writer.startObject();
				writer.name("filters");
				hidePerformer.encodeLocalFilters(writer);
				writer.endObject();
				extra = writer.build();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		retainExtra.threadExtra = extra;
		Page page = getPage();
		CommonDatabase.getInstance().getThreads().setStateExtra(true,
				page.chanName, page.boardName, page.threadNumber, false, null, true, extra);
	}

	private Pair<PostNumber, Integer> decodeThreadState(byte[] state) {
		PostNumber positionPostNumber = null;
		int positionOffset = 0;
		if (state != null) {
			try (JsonSerial.Reader reader = JsonSerial.reader(state)) {
				reader.startObject();
				while (!reader.endStruct()) {
					switch (reader.nextName()) {
						case "position": {
							reader.startObject();
							while (!reader.endStruct()) {
								switch (reader.nextName()) {
									case "number": {
										positionPostNumber = PostNumber.parseNullable(reader.nextString());
										break;
									}
									case "offset": {
										positionOffset = reader.nextInt();
										break;
									}
									default: {
										reader.skip();
										break;
									}
								}
							}
							break;
						}
						default: {
							reader.skip();
							break;
						}
					}
				}
			} catch (ParseException e) {
				Log.persistent().stack(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return positionPostNumber != null ? new Pair<>(positionPostNumber, positionOffset) : null;
	}

	private final Runnable storePositionRunnable = () -> {
		ListPosition listPosition = ListPosition.obtain(getRecyclerView(), null);
		byte[] state = null;
		if (listPosition != null) {
			try (JsonSerial.Writer writer = JsonSerial.writer()) {
				writer.startObject();
				writer.name("position");
				writer.startObject();
				writer.name("number");
				writer.value(getAdapter().getItem(listPosition.position).getPostNumber().toString());
				writer.name("offset");
				writer.value(listPosition.offset);
				writer.endObject();
				writer.endObject();
				state = writer.build();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		Page page = getPage();
		CommonDatabase.getInstance().getThreads().setStateExtra(true,
				page.chanName, page.boardName, page.threadNumber, true, state, false, null);
	};

	private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
		@Override
		public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
			ConcurrentUtils.HANDLER.removeCallbacks(storePositionRunnable);
			ConcurrentUtils.HANDLER.postDelayed(storePositionRunnable, 2000L);
		}
	};

	private Pair<PostNumber, Integer> transformListPositionToPair(ListPosition listPosition) {
		PostNumber postNumber = listPosition != null
				? getAdapter().getItem(listPosition.position).getPostNumber() : null;
		return postNumber != null ? new Pair<>(postNumber, listPosition.offset) : null;
	}

	private ListPosition transformPairToListPosition(Pair<PostNumber, Integer> positionPair) {
		if (positionPair != null) {
			int position = getAdapter().positionOfPostNumber(positionPair.first);
			return position >= 0 ? new ListPosition(position, positionPair.second) : null;
		} else {
			return null;
		}
	}

	private void cancelTasks() {
		if (extractTask != null) {
			extractTask.cancel();
			extractTask = null;
		}
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
	}

	private void extractPosts(boolean initial, boolean newThread) {
		cancelTasks();
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		boolean removeDeleted = retainExtra.removeDeleted;
		retainExtra.removeDeleted = false;
		extractTask = new ExtractPostsTask(this, retainExtra.cache,
				getChan(), page.boardName, page.threadNumber, initial, newThread, removeDeleted);
		extractTask.executeOnExecutor(ExtractPostsTask.THREAD_POOL_EXECUTOR);
	}

	private final Runnable refreshRunnable = () -> {
		if (extractTask == null && readTask == null) {
			refreshPosts(false);
		}
		queueNextRefresh(false);
	};

	private void queueNextRefresh(boolean instant) {
		ConcurrentUtils.HANDLER.removeCallbacks(refreshRunnable);
		if (Preferences.getAutoRefreshMode() == Preferences.AUTO_REFRESH_MODE_ENABLED) {
			if (instant) {
				ConcurrentUtils.HANDLER.post(refreshRunnable);
			} else {
				ConcurrentUtils.HANDLER.postDelayed(refreshRunnable, Preferences.getAutoRefreshInterval() * 1000);
			}
		}
	}

	private void stopRefresh() {
		ConcurrentUtils.HANDLER.removeCallbacks(refreshRunnable);
	}

	private void refreshPosts(boolean reload) {
		refreshPosts(reload, getAdapter().getItemCount() > 0);
	}

	private void refreshPosts(boolean reload, boolean showPull) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (extractTask != null) {
			parcelableExtra.queuedRefresh = QueuedRefresh.max(parcelableExtra.queuedRefresh,
					reload ? QueuedRefresh.RELOAD : QueuedRefresh.REFRESH);
			return;
		}
		parcelableExtra.queuedRefresh = QueuedRefresh.NONE;
		cancelTasks();
		Page page = getPage();
		readTask = new ReadPostsTask(this, getChan(), page.boardName, page.threadNumber,
				reload, PostingService.getPendingUserPosts(page.chanName, page.boardName, page.threadNumber));
		readTask.executeOnExecutor(ReadPostsTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTTOM);
			switchView(ViewType.LIST, null);
		} else {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onExtractPostsComplete(ExtractPostsTask.Result result) {
		extractTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		PostsAdapter adapter = getAdapter();
		boolean updateAdapters = false;
		ListPosition listPositionFromState = null;
		Pair<PostNumber, Integer> keepPositionPair = null;

		if (result != null) {
			if (result.newThread) {
				parcelableExtra.unreadPosts.addAll(result.postItems.keySet());
			} else {
				parcelableExtra.unreadPosts.addAll(result.newPosts);
				parcelableExtra.unreadPosts.addAll(result.deletedPosts);
				parcelableExtra.unreadPosts.addAll(result.editedPosts);
			}
			retainExtra.cache = result.cache;
			if (result.cacheChanged) {
				retainExtra.archivedThreadUri = result.archivedThreadUri;
				retainExtra.uniquePosters = result.uniquePosters;
			}
			if (!result.postItems.isEmpty() || !result.removedPosts.isEmpty()) {
				if (adapter.getItemCount() > 0) {
					ListPosition listPosition = ListPosition.obtain(getRecyclerView(),
							position -> !adapter.getItem(position).isDeleted());
					if (listPosition == null) {
						listPosition = ListPosition.obtain(getRecyclerView(), null);
					}
					keepPositionPair = transformListPositionToPair(listPosition);
				}
				adapter.insertItems(result.postItems, result.removedPosts);
				updateAdapters = true;
			}
			if (result.flags != null) {
				retainExtra.hiddenPosts.clear();
				retainExtra.hiddenPosts.addAll(result.flags.hiddenPosts);
				retainExtra.userPosts.clear();
				retainExtra.userPosts.addAll(result.flags.userPosts);
			}
			if (result.stateExtra != null) {
				listPositionFromState = transformPairToListPosition(decodeThreadState(result.stateExtra.state));
				retainExtra.threadExtra = result.stateExtra.extra;
				decodeThreadExtra();
			}

			if (!result.newPosts.isEmpty() || !result.deletedPosts.isEmpty() ||
					!result.editedPosts.isEmpty() || result.replyCount > 0) {
				updateAdapters = true;
				int newCount = result.newPosts.size();
				int deletedCount = result.deletedPosts.size();
				String message;
				if (result.replyCount > 0 || deletedCount > 0) {
					message = getQuantityString(R.plurals.number_new__format, newCount, newCount);
					if (result.replyCount > 0) {
						message = getString(R.string.__enumeration_format, message,
								getQuantityString(R.plurals.number_replies__format,
										result.replyCount, result.replyCount));
					}
					if (deletedCount > 0) {
						message = getString(R.string.__enumeration_format, message,
								getQuantityString(R.plurals.number_deleted__format, deletedCount, deletedCount));
					}
				} else if (newCount > 0) {
					message = getQuantityString(R.plurals.number_new_posts__format, newCount, newCount);
				} else {
					message = getString(R.string.some_posts_have_been_edited);
				}
				if (newCount > 0) {
					parcelableExtra.newPostNumber = Collections.min(result.newPosts);
					adapter.preloadPosts(parcelableExtra.newPostNumber);
					ClickableToast.show(getContext(), message, getString(R.string.show), true, () -> {
						if (!isDestroyed()) {
							PostNumber newPostNumber = parcelableExtra.newPostNumber;
							if (newPostNumber != null) {
								int newPostIndex = adapter.positionOfPostNumber(newPostNumber);
								if (newPostIndex >= 0) {
									ListViewUtils.smoothScrollToPosition(getRecyclerView(), newPostIndex);
								}
							}
						}
					});
				} else {
					ClickableToast.show(getContext(), message);
				}

				if (deletedCount > 0 || !result.editedPosts.isEmpty()) {
					lastEditedPostNumbers.clear();
					lastEditedPostNumbers.addAll(result.deletedPosts);
					lastEditedPostNumbers.addAll(result.editedPosts);
				}
			}
		}

		if (updateAdapters) {
			getUiManager().dialog().updateAdapters(getAdapter().getConfigurationSet().stackInstance);
			notifyAllAdaptersChanged();
			ListPosition listPosition = transformPairToListPosition(keepPositionPair);
			if (listPosition != null) {
				listPosition.apply(getRecyclerView());
			}
		}
		onExtractPostsCompleteInternal(result != null, result != null && result.initial, false,
				listPositionFromState);
	}

	private void onExtractPostsCompleteInternal(boolean success, boolean initial, boolean fromState,
			ListPosition listPositionFromState) {
		PostsAdapter adapter = getAdapter();
		ListPosition listPosition = takeListPosition();
		if (listPosition == null) {
			listPosition = listPositionFromState;
		}
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		Page page = getPage();

		if (success) {
			if (initial) {
				if (parcelableExtra.scrollToPostNumber != null) {
					scrollToPostFromExtra(true);
				} else if (listPosition != null) {
					listPosition.apply(getRecyclerView());
				}
				if (!fromState) {
					showScaleAnimation();
				}
			} else {
				FavoritesStorage.getInstance().modifyPostsCount(page.chanName, page.boardName,
						page.threadNumber, getAdapter().getExistingPostsCount());
				scrollToPostFromExtra(getRecyclerView().getChildCount() == 0);
				// Forget about the request on fail
				parcelableExtra.scrollToPostNumber = null;
			}

			if (!parcelableExtra.isAddedToHistory) {
				parcelableExtra.isAddedToHistory = true;
				CommonDatabase.getInstance().getHistory().addHistoryAsync(page.chanName,
						page.boardName, page.threadNumber, parcelableExtra.threadTitle);
			}
			Iterator<PostItem> iterator = getAdapter().iterator();
			if (iterator.hasNext()) {
				String title = iterator.next().getSubjectOrComment();
				if (StringUtils.isEmptyOrWhitespace(title)) {
					title = null;
				}
				FavoritesStorage.getInstance().updateTitle(page.chanName, page.boardName,
						page.threadNumber, title, false);
				if (!CommonUtils.equals(StringUtils.nullIfEmpty(parcelableExtra.threadTitle), title)) {
					CommonDatabase.getInstance().getHistory()
							.updateTitleAsync(page.chanName, page.boardName, page.threadNumber, title);
					parcelableExtra.threadTitle = title;
					notifyTitleChanged();
				}
			}

			if (parcelableExtra.selectedPosts != null) {
				Set<PostNumber> selected = parcelableExtra.selectedPosts;
				parcelableExtra.selectedPosts = null;
				if (success) {
					for (PostNumber postNumber : selected) {
						PostItem postItem = adapter.findPostItem(postNumber);
						if (postItem != null) {
							adapter.toggleItemSelected(postItem);
						}
					}
					selectionMode = startActionMode(this);
				}
			}

			if (retainExtra.removeDeleted) {
				extractPosts(false, false);
				getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTTOM);
			} else if (parcelableExtra.queuedRefresh != QueuedRefresh.NONE ||
					initial && retainExtra.postItems.isEmpty()) {
				boolean reload = parcelableExtra.queuedRefresh == QueuedRefresh.RELOAD;
				refreshPosts(reload);
			}
			queueNextRefresh(false);
			updateOptionsMenu();
		} else if (initial) {
			refreshPosts(true);
		} else {
			onReadPostsFail(new ErrorItem(ErrorItem.Type.UNKNOWN));
		}
	}

	@Override
	public void onReadPostsSuccess(boolean newThread, boolean shouldExtract,
			Set<PendingUserPost> removedPendingUserPosts) {
		readTask = null;
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (newThread) {
			StatisticsStorage.getInstance().incrementThreadsViewed(page.chanName);
		}
		if (!removedPendingUserPosts.isEmpty()) {
			PostingService.consumePendingUserPosts(page.chanName, page.boardName, page.threadNumber,
					removedPendingUserPosts);
		}
		if (shouldExtract || retainExtra.removeDeleted) {
			extractPosts(false, newThread);
		} else {
			getRecyclerView().getWrapper().cancelBusyState();
			switchView(ViewType.LIST, null);
			queueNextRefresh(false);
		}
	}

	@Override
	public void onReadPostsRedirect(RedirectException.Target target) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		handleRedirect(target.chanName, target.boardName, target.threadNumber, target.postNumber);
	}

	@Override
	public void onReadPostsFail(ErrorItem errorItem) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		displayDownloadError(true, errorItem.toString());
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.scrollToPostNumber = null;
	}

	private void displayDownloadError(boolean show, String message) {
		if (show && getAdapter().getItemCount() > 0) {
			ClickableToast.show(getContext(), message);
			return;
		}
		switchView(ViewType.ERROR, message);
	}

	private Runnable postNotifyDataSetChanged;

	@Override
	public void onPostItemMessage(PostItem postItem, UiManager.Message message) {
		int position = getAdapter().positionOfPostNumber(postItem.getPostNumber());
		if (position < 0) {
			return;
		}
		switch (message) {
			case POST_INVALIDATE_ALL_VIEWS: {
				if (postNotifyDataSetChanged == null) {
					postNotifyDataSetChanged = getAdapter()::notifyDataSetChanged;
				}
				RecyclerView recyclerView = getRecyclerView();
				recyclerView.removeCallbacks(postNotifyDataSetChanged);
				recyclerView.post(postNotifyDataSetChanged);
				break;
			}
			case INVALIDATE_COMMENT_VIEW: {
				getAdapter().invalidateComment(position);
				break;
			}
			case PERFORM_SWITCH_USER_MARK: {
				setPostUserPost(postItem, !postStateProvider.isUserPost(postItem.getPostNumber()));
				getUiManager().sendPostItemMessage(postItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
				break;
			}
			case PERFORM_SWITCH_HIDE: {
				setPostHideState(postItem, !postItem.getHideState().hidden
						? PostItem.HideState.HIDDEN : PostItem.HideState.SHOWN);
				getUiManager().sendPostItemMessage(postItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
				break;
			}
			case PERFORM_HIDE_REPLIES:
			case PERFORM_HIDE_NAME:
			case PERFORM_HIDE_SIMILAR: {
				PostsAdapter adapter = getAdapter();
				adapter.cancelPreloading();
				HidePerformer.AddResult result;
				switch (message) {
					case PERFORM_HIDE_REPLIES: {
						result = hidePerformer.addHideByReplies(postItem);
						break;
					}
					case PERFORM_HIDE_NAME: {
						result = hidePerformer.addHideByName(getContext(), getChan(), postItem);
						break;
					}
					case PERFORM_HIDE_SIMILAR: {
						result = hidePerformer.addHideSimilar(getContext(), getChan(), postItem);
						break;
					}
					default: {
						throw new RuntimeException();
					}
				}
				if (result == HidePerformer.AddResult.SUCCESS) {
					setPostHideState(postItem, PostItem.HideState.UNDEFINED);
					adapter.invalidateHidden();
					notifyAllAdaptersChanged();
					encodeAndStoreThreadExtra();
				} else if (result == HidePerformer.AddResult.EXISTS && !postItem.getHideState().hidden) {
					setPostHideState(postItem, PostItem.HideState.UNDEFINED);
					notifyAllAdaptersChanged();
				}
				adapter.preloadPosts(((LinearLayoutManager) getRecyclerView().getLayoutManager())
						.findFirstVisibleItemPosition());
				break;
			}
			case PERFORM_GO_TO_POST: {
				PullableRecyclerView recyclerView = getRecyclerView();
				// Avoid concurrent modification
				recyclerView.post(() -> getUiManager().dialog()
						.closeDialogs(getAdapter().getConfigurationSet().stackInstance));
				ListViewUtils.smoothScrollToPosition(recyclerView, position);
				break;
			}
		}
	}
}
