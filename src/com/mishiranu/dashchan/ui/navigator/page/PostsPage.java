package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.RedirectException;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.DeserializePostsTask;
import com.mishiranu.dashchan.content.async.ReadPostsTask;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.HistoryDatabase;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.SeekBarForm;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.DialogUnit;
import com.mishiranu.dashchan.ui.navigator.manager.HidePerformer;
import com.mishiranu.dashchan.ui.navigator.manager.ThreadshotPerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.SearchHelper;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PostsLayoutManager;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class PostsPage extends ListPage implements PostsAdapter.Callback, FavoritesStorage.Observer,
		UiManager.Observer, DeserializePostsTask.Callback, ReadPostsTask.Callback, ActionMode.Callback {
	private enum QueuedRefresh {
		NONE, REFRESH, RELOAD;

		public static QueuedRefresh max(QueuedRefresh queuedRefresh1, QueuedRefresh queuedRefresh2) {
			return values()[Math.max(queuedRefresh1.ordinal(), queuedRefresh2.ordinal())];
		}
	}

	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public Posts cachedPosts;
		public final ArrayList<PostItem> cachedPostItems = new ArrayList<>();
		public final HashSet<String> userPostNumbers = new HashSet<>();

		public DialogUnit.StackInstance.State dialogsState;
	}

	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public final ArrayList<ReadPostsTask.UserPostPending> userPostPendingList = new ArrayList<>();
		public final HashSet<String> expandedPosts = new HashSet<>();
		public boolean isAddedToHistory = false;
		public boolean hasNewPostDataList = false;
		public QueuedRefresh queuedRefresh = QueuedRefresh.NONE;
		public String threadTitle;
		public String newPostNumber;
		public String scrollToPostNumber;
		public Set<String> selectedItems;

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeList(userPostPendingList);
			dest.writeStringArray(CommonUtils.toArray(expandedPosts, String.class));
			dest.writeByte((byte) (isAddedToHistory ? 1 : 0));
			dest.writeByte((byte) (hasNewPostDataList ? 1 : 0));
			dest.writeString(queuedRefresh.name());
			dest.writeString(threadTitle);
			dest.writeString(newPostNumber);
			dest.writeByte((byte) (selectedItems != null ? 1 : 0));
			if (selectedItems != null) {
				dest.writeStringList(new ArrayList<>(selectedItems));
			}
		}

		public static final Creator<ParcelableExtra> CREATOR = new Creator<ParcelableExtra>() {
			@Override
			public ParcelableExtra createFromParcel(Parcel in) {
				ParcelableExtra parcelableExtra = new ParcelableExtra();
				@SuppressWarnings("unchecked")
				ArrayList<ReadPostsTask.UserPostPending> userPostPendingList = in
						.readArrayList(ParcelableExtra.class.getClassLoader());
				parcelableExtra.userPostPendingList.addAll(userPostPendingList);
				String[] data = in.createStringArray();
				if (data != null) {
					Collections.addAll(parcelableExtra.expandedPosts, data);
				}
				parcelableExtra.isAddedToHistory = in.readByte() != 0;
				parcelableExtra.hasNewPostDataList = in.readByte() != 0;
				parcelableExtra.queuedRefresh = QueuedRefresh.valueOf(in.readString());
				parcelableExtra.threadTitle = in.readString();
				parcelableExtra.newPostNumber = in.readString();
				if (in.readByte() != 0) {
					ArrayList<String> selectedItems = in.createStringArrayList();
					parcelableExtra.selectedItems = selectedItems != null
							? new HashSet<>(selectedItems) : Collections.emptySet();
				}
				return parcelableExtra;
			}

			@Override
			public ParcelableExtra[] newArray(int size) {
				return new ParcelableExtra[size];
			}
		};
	}

	private DeserializePostsTask deserializeTask;
	private ReadPostsTask readTask;

	private Replyable replyable;
	private HidePerformer hidePerformer;
	private Pair<String, Uri> originalThreadData;

	private ActionMode selectionMode;

	private LinearLayout searchController;
	private Button searchTextResult;
	private final ArrayList<Integer> searchFoundPosts = new ArrayList<>();
	private boolean searching = false;
	private int searchLastPosition;

	private int autoRefreshInterval = 30;
	private boolean autoRefreshEnabled = false;

	private final ArrayList<String> lastEditedPostNumbers = new ArrayList<>();

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
		hidePerformer = new HidePerformer();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		ChanConfiguration.Board board = getChanConfiguration().safe().obtainBoard(page.boardName);
		if (board.allowPosting) {
			replyable = data -> getUiManager().navigator().navigatePosting(page.chanName, page.boardName,
					page.threadNumber, data);
		} else {
			replyable = null;
		}
		PostsAdapter adapter = new PostsAdapter(this, page.chanName, page.boardName, uiManager,
				replyable, hidePerformer, retainExtra.userPostNumbers, recyclerView);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> adapter.configureDivider(c, position).horizontal(dividerPadding, dividerPadding)));
		recyclerView.addItemDecoration(adapter.createPostItemDecoration(context, dividerPadding));
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.BOTH);
		uiManager.observable().register(this);
		hidePerformer.setPostsProvider(adapter);

		Context darkStyledContext = new ContextThemeWrapper(context, R.style.Theme_General_Main_Dark);
		searchController = new LinearLayout(darkStyledContext);
		searchController.setOrientation(LinearLayout.HORIZONTAL);
		searchController.setGravity(Gravity.CENTER_VERTICAL);
		int buttonPadding = (int) (10f * density);
		searchTextResult = new Button(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		searchTextResult.setTextSize(11f);
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
		backButtonView.setImageResource(obtainIcon(R.attr.iconActionBack));
		backButtonView.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
		backButtonView.setOnClickListener(v -> findBack());
		searchController.addView(backButtonView, (int) (48f * density), (int) (48f * density));
		ImageView forwardButtonView = new ImageView(darkStyledContext, null, android.R.attr.borderlessButtonStyle);
		forwardButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		forwardButtonView.setImageResource(obtainIcon(R.attr.iconActionForward));
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
		parcelableExtra.hasNewPostDataList |= handleNewPostDataList();
		QueuedRefresh queuedRefresh = initRequest.shouldLoad ? QueuedRefresh.REFRESH : QueuedRefresh.NONE;
		parcelableExtra.queuedRefresh = QueuedRefresh.max(parcelableExtra.queuedRefresh, queuedRefresh);
		if (retainExtra.cachedPosts != null && retainExtra.cachedPostItems.size() > 0) {
			onDeserializePostsCompleteInternal(true, retainExtra.cachedPosts,
					new ArrayList<>(retainExtra.cachedPostItems), true);
			if (retainExtra.dialogsState != null) {
				uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainExtra.dialogsState);
				retainExtra.dialogsState = null;
			}
		} else {
			deserializeTask = new DeserializePostsTask(this, page.chanName, page.boardName,
					page.threadNumber, retainExtra.cachedPosts);
			deserializeTask.executeOnExecutor(DeserializePostsTask.THREAD_POOL_EXECUTOR);
			recyclerView.getWrapper().startBusyState(PullableWrapper.Side.BOTH);
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
		getAdapter().cleanup();
		getUiManager().dialog().closeDialogs(getAdapter().getConfigurationSet().stackInstance);
		getUiManager().observable().unregister(this);
		if (deserializeTask != null) {
			deserializeTask.cancel();
			deserializeTask = null;
		}
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
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
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.hasNewPostDataList |= handleNewPostDataList();
		if (parcelableExtra.hasNewPostDataList) {
			refreshPosts(true, false);
		}
	}

	@Override
	protected void onScrollToPost(String postNumber) {
		int position = getAdapter().findPositionByPostNumber(postNumber);
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
			boolean reload = readTask.isForceLoadFullThread();
			parcelableExtra.queuedRefresh = QueuedRefresh.max(parcelableExtra.queuedRefresh,
					reload ? QueuedRefresh.RELOAD : QueuedRefresh.REFRESH);
		}
		parcelableExtra.expandedPosts.clear();
		for (PostItem postItem : adapter) {
			if (postItem.isExpanded()) {
				parcelableExtra.expandedPosts.add(postItem.getPostNumber());
			}
		}
		parcelableExtra.selectedItems = null;
		if (selectionMode != null && !saveToStack) {
			ArrayList<PostItem> selected = adapter.getSelectedItems();
			parcelableExtra.selectedItems = new HashSet<>(selected.size());
			for (PostItem postItem : selected) {
				parcelableExtra.selectedItems.add(postItem.getPostNumber());
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
			selectionMode.setTitle(getString(R.string.text_selected_format, getAdapter().getSelectedCount()));
			return;
		}
		getUiManager().interaction().handlePostClick(view, postItem, getAdapter());
	}

	@Override
	public boolean onItemLongClick(PostItem postItem) {
		if (selectionMode != null) {
			return false;
		}
		return postItem != null && getUiManager().interaction().handlePostContextMenu(postItem,
				getAdapter().getConfigurationSet().stackInstance, replyable, true, true, false);
	}

	private static final int OPTIONS_MENU_ADD_POST = 0;
	private static final int OPTIONS_MENU_GALLERY = 1;
	private static final int OPTIONS_MENU_SELECT = 2;
	private static final int OPTIONS_MENU_REFRESH = 3;
	private static final int OPTIONS_MENU_THREAD_OPTIONS = 4;
	private static final int OPTIONS_MENU_ADD_TO_FAVORITES_TEXT = 5;
	private static final int OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT = 6;
	private static final int OPTIONS_MENU_ADD_TO_FAVORITES_ICON = 7;
	private static final int OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON = 8;
	private static final int OPTIONS_MENU_OPEN_ORIGINAL_THREAD = 9;
	private static final int OPTIONS_MENU_ARCHIVE = 10;

	private static final int THREAD_OPTIONS_MENU_RELOAD = 200;
	private static final int THREAD_OPTIONS_MENU_AUTO_REFRESH = 201;
	private static final int THREAD_OPTIONS_MENU_HIDDEN_POSTS = 202;
	private static final int THREAD_OPTIONS_MENU_CLEAR_DELETED = 203;
	private static final int THREAD_OPTIONS_MENU_SUMMARY = 204;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_ADD_POST, 0, R.string.action_add_post).setIcon(obtainIcon(R.attr.iconActionAddPost))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_search)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_GALLERY, 0, R.string.action_gallery);
		menu.add(0, OPTIONS_MENU_SELECT, 0, R.string.action_select);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh).setIcon(obtainIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
		SubMenu threadOptions = menu.addSubMenu(0, OPTIONS_MENU_THREAD_OPTIONS, 0, R.string.action_thread_options);
		menu.add(0, OPTIONS_MENU_ADD_TO_FAVORITES_TEXT, 0, R.string.action_add_to_favorites);
		menu.add(0, OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT, 0, R.string.action_remove_from_favorites);
		menu.add(0, OPTIONS_MENU_ADD_TO_FAVORITES_ICON, 0, R.string.action_add_to_favorites)
				.setIcon(obtainIcon(R.attr.iconActionAddToFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON, 0, R.string.action_remove_from_favorites)
				.setIcon(obtainIcon(R.attr.iconActionRemoveFromFavorites))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		menu.add(0, OPTIONS_MENU_OPEN_ORIGINAL_THREAD, 0, R.string.action_open_the_original);
		menu.add(0, OPTIONS_MENU_ARCHIVE, 0, R.string.action_archive_add);

		threadOptions.add(0, THREAD_OPTIONS_MENU_RELOAD, 0, R.string.action_reload);
		threadOptions.add(0, THREAD_OPTIONS_MENU_AUTO_REFRESH, 0, R.string.action_auto_refresh).setCheckable(true);
		threadOptions.add(0, THREAD_OPTIONS_MENU_HIDDEN_POSTS, 0, R.string.action_hidden_posts);
		threadOptions.add(0, THREAD_OPTIONS_MENU_CLEAR_DELETED, 0, R.string.action_clear_deleted);
		threadOptions.add(0, THREAD_OPTIONS_MENU_SUMMARY, 0, R.string.action_summary);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		Page pageHolder = getPage();
		menu.findItem(OPTIONS_MENU_ADD_POST).setVisible(replyable != null);
		boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(pageHolder.chanName, pageHolder.boardName,
				pageHolder.threadNumber);
		boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
		menu.findItem(OPTIONS_MENU_ADD_TO_FAVORITES_TEXT).setVisible(!iconFavorite && !isFavorite);
		menu.findItem(OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT).setVisible(!iconFavorite && isFavorite);
		menu.findItem(OPTIONS_MENU_ADD_TO_FAVORITES_ICON).setVisible(iconFavorite && !isFavorite);
		menu.findItem(OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON).setVisible(iconFavorite && isFavorite);
		menu.findItem(OPTIONS_MENU_OPEN_ORIGINAL_THREAD).setVisible(originalThreadData != null);
		menu.findItem(OPTIONS_MENU_ARCHIVE).setVisible(ChanManager.getInstance()
				.canBeArchived(pageHolder.chanName));
		menu.findItem(THREAD_OPTIONS_MENU_AUTO_REFRESH).setVisible(Preferences.getAutoRefreshMode()
				== Preferences.AUTO_REFRESH_MODE_SEPARATE).setEnabled(getAdapter().getItemCount() > 0)
				.setChecked(autoRefreshEnabled);
		menu.findItem(THREAD_OPTIONS_MENU_HIDDEN_POSTS).setEnabled(hidePerformer.hasLocalAutohide());
		menu.findItem(THREAD_OPTIONS_MENU_CLEAR_DELETED).setEnabled(getAdapter().hasDeletedPosts());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Page page = getPage();
		PostsAdapter adapter = getAdapter();
		switch (item.getItemId()) {
			case OPTIONS_MENU_ADD_POST: {
				getUiManager().navigator().navigatePosting(page.chanName, page.boardName,
						page.threadNumber);
				return true;
			}
			case OPTIONS_MENU_GALLERY: {
				int imageIndex = -1;
				RecyclerView recyclerView = getRecyclerView();
				View child = recyclerView.getChildAt(0);
				GalleryItem.GallerySet gallerySet = getAdapter().getConfigurationSet().gallerySet;
				if (child != null) {
					UiManager uiManager = getUiManager();
					ArrayList<GalleryItem> galleryItems = gallerySet.getItems();
					int position = recyclerView.getChildAdapterPosition(child);
					OUTER: for (int v = 0; v <= 1; v++) {
						for (PostItem postItem : adapter.iterate(v == 0, position)) {
							imageIndex = uiManager.view().findImageIndex(galleryItems, postItem);
							if (imageIndex != -1) {
								break OUTER;
							}
						}
					}
				}
				getUiManager().navigator().navigateGallery(page.chanName, gallerySet, imageIndex,
						null, GalleryOverlay.NavigatePostMode.ENABLED, true);
				return true;
			}
			case OPTIONS_MENU_SELECT: {
				selectionMode = startActionMode(this);
				return true;
			}
			case OPTIONS_MENU_REFRESH: {
				refreshPosts(true, false);
				return true;
			}
			case OPTIONS_MENU_ADD_TO_FAVORITES_TEXT:
			case OPTIONS_MENU_ADD_TO_FAVORITES_ICON: {
				FavoritesStorage.getInstance().add(page.chanName, page.boardName,
						page.threadNumber, getParcelableExtra(ParcelableExtra.FACTORY).threadTitle,
						adapter.getExistingPostsCount());
				updateOptionsMenu();
				return true;
			}
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_TEXT:
			case OPTIONS_MENU_REMOVE_FROM_FAVORITES_ICON: {
				FavoritesStorage.getInstance().remove(page.chanName, page.boardName,
						page.threadNumber);
				updateOptionsMenu();
				return true;
			}
			case OPTIONS_MENU_OPEN_ORIGINAL_THREAD: {
				String chanName = originalThreadData.first;
				Uri uri = originalThreadData.second;
				ChanLocator locator = ChanLocator.get(chanName);
				String boardName = locator.safe(true).getBoardName(uri);
				String threadNumber = locator.safe(true).getThreadNumber(uri);
				if (threadNumber != null) {
					String threadTitle = getAdapter().getItem(0).getSubjectOrComment();
					getUiManager().navigator().navigatePosts(chanName, boardName, threadNumber, null,
							threadTitle, 0);
				}
				return true;
			}
			case OPTIONS_MENU_ARCHIVE: {
				String threadTitle = null;
				if (adapter.getItemCount() > 0) {
					threadTitle = adapter.getItem(0).getSubjectOrComment();
				}
				getUiManager().dialog().performSendArchiveThread(page.chanName, page.boardName,
						page.threadNumber, threadTitle, getRetainExtra(RetainExtra.FACTORY).cachedPosts);
				return true;
			}
			case THREAD_OPTIONS_MENU_RELOAD: {
				refreshPosts(true, true);
				return true;
			}
			case THREAD_OPTIONS_MENU_AUTO_REFRESH: {
				SeekBarForm seekBarForm = new SeekBarForm(true);
				seekBarForm.setConfiguration(Preferences.MIN_AUTO_REFRESH_INTERVAL,
						Preferences.MAX_AUTO_REFRESH_INTERVAL, Preferences.STEP_AUTO_REFRESH_INTERVAL, 1f);
				seekBarForm.setValueFormat(getString(R.string.preference_auto_refresh_interval_summary_format));
				seekBarForm.setCurrentValue(autoRefreshInterval);
				seekBarForm.setSwitchValue(autoRefreshEnabled);
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.action_auto_refresh)
						.setView(seekBarForm.inflate(getContext()))
						.setPositiveButton(android.R.string.ok, (d, which) -> {
							autoRefreshEnabled = seekBarForm.getSwitchValue();
							autoRefreshInterval = seekBarForm.getCurrentValue();
							Posts posts = getRetainExtra(RetainExtra.FACTORY).cachedPosts;
							boolean changed = posts.setAutoRefreshData(autoRefreshEnabled, autoRefreshInterval);
							if (changed) {
								serializePosts();
							}
							queueNextRefresh(true);
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
				return true;
			}
			case THREAD_OPTIONS_MENU_HIDDEN_POSTS: {
				ArrayList<String> localAutohide = hidePerformer.getReadableLocalAutohide();
				final boolean[] checked = new boolean[localAutohide.size()];
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.text_remove_rules)
						.setMultiChoiceItems(CommonUtils.toArray(localAutohide, String.class),
								checked, (d, which, isChecked) -> checked[which] = isChecked)
						.setPositiveButton(android.R.string.ok, (d, which) -> {
							boolean hasDeleted = false;
							for (int i = 0, j = 0; i < checked.length; i++, j++) {
								if (checked[i]) {
									hidePerformer.removeLocalAutohide(j--);
									hasDeleted = true;
								}
							}
							if (hasDeleted) {
								adapter.invalidateHidden();
								notifyAllAdaptersChanged();
								hidePerformer.encodeLocalAutohide(getRetainExtra(RetainExtra.FACTORY).cachedPosts);
								serializePosts();
								adapter.preloadPosts(((LinearLayoutManager) getRecyclerView().getLayoutManager())
										.findFirstVisibleItemPosition());
							}
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
				return true;
			}
			case THREAD_OPTIONS_MENU_CLEAR_DELETED: {
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setMessage(R.string.message_clear_deleted_posts_warning)
						.setPositiveButton(android.R.string.ok, (d, which) -> {
							RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
							Posts cachedPosts = retainExtra.cachedPosts;
							cachedPosts.clearDeletedPosts();
							ArrayList<PostItem> deletedPostItems = adapter.clearDeletedPosts();
							if (deletedPostItems != null) {
								retainExtra.cachedPostItems.removeAll(deletedPostItems);
								for (PostItem postItem : deletedPostItems) {
									retainExtra.userPostNumbers.remove(postItem.getPostNumber());
								}
								notifyAllAdaptersChanged();
							}
							updateOptionsMenu();
							serializePosts();
						})
						.setNegativeButton(android.R.string.cancel, null)
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
				return true;
			}
			case THREAD_OPTIONS_MENU_SUMMARY: {
				RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
				int files = 0;
				int postsWithFiles = 0;
				int links = 0;
				for (PostItem postItem : getAdapter()) {
					ArrayList<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
					if (attachmentItems != null) {
						int itFiles = 0;
						for (AttachmentItem attachmentItem : attachmentItems) {
							int generalType = attachmentItem.getGeneralType();
							switch (generalType) {
								case AttachmentItem.GENERAL_TYPE_FILE:
								case AttachmentItem.GENERAL_TYPE_EMBEDDED: {
									itFiles++;
									break;
								}
								case AttachmentItem.GENERAL_TYPE_LINK: {
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
				int uniquePosters = retainExtra.cachedPosts!= null ? retainExtra.cachedPosts.getUniquePosters() : -1;
				StringBuilder builder = new StringBuilder();
				String boardName = page.boardName;
				if (boardName != null) {
					builder.append(getString(R.string.text_board)).append(": ");
					String title = getChanConfiguration().getBoardTitle(boardName);
					builder.append(StringUtils.formatBoardTitle(page.chanName, boardName, title));
					builder.append('\n');
				}
				builder.append(getString(R.string.text_files_format, files));
				builder.append('\n').append(getString(R.string.text_posts_with_files_format, postsWithFiles));
				builder.append('\n').append(getString(R.string.text_links_attachments_format, links));
				if (uniquePosters > 0) {
					builder.append('\n').append(getString(R.string.text_unique_posters_format, uniquePosters));
				}
				AlertDialog dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.action_summary)
						.setMessage(builder)
						.setPositiveButton(android.R.string.ok, null)
						.show();
				getUiManager().getConfigurationLock().lockConfiguration(dialog);
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
			case APPEARANCE_MENU_SPOILERS:
			case APPEARANCE_MENU_MY_POSTS:
			case APPEARANCE_MENU_SFW_MODE: {
				notifyAllAdaptersChanged();
				break;
			}
		}
	}

	private static final int ACTION_MENU_MAKE_THREADSHOT = 0;
	private static final int ACTION_MENU_REPLY = 1;
	private static final int ACTION_MENU_DELETE_POSTS = 2;
	private static final int ACTION_MENU_SEND_REPORT = 3;

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		Page page = getPage();
		ChanConfiguration configuration = getChanConfiguration();
		getAdapter().setSelectionModeEnabled(true);
		mode.setTitle(getString(R.string.text_selected_format, getAdapter().getSelectedCount()));
		int pasteResId = ResourceUtils.getSystemSelectionIcon(getContext(), "actionModePasteDrawable",
				"ic_menu_paste_holo_dark");
		int flags = MenuItem.SHOW_AS_ACTION_ALWAYS;
		ChanConfiguration.Board board = configuration.safe().obtainBoard(page.boardName);
		menu.add(0, ACTION_MENU_MAKE_THREADSHOT, 0, R.string.action_make_threadshot)
				.setIcon(obtainIcon(R.attr.iconActionMakeThreadshot)).setShowAsAction(flags);
		if (replyable != null) {
			menu.add(0, ACTION_MENU_REPLY, 0, R.string.action_reply).setIcon(pasteResId).setShowAsAction(flags);
		}
		if (board.allowDeleting) {
			ChanConfiguration.Deleting deleting = configuration.safe().obtainDeleting(page.boardName);
			if (deleting != null && deleting.multiplePosts) {
				menu.add(0, ACTION_MENU_DELETE_POSTS, 0, R.string.action_delete)
						.setIcon(obtainIcon(R.attr.iconActionDelete)).setShowAsAction(flags);
			}
		}
		if (board.allowReporting) {
			ChanConfiguration.Reporting reporting = configuration.safe().obtainReporting(page.boardName);
			if (reporting != null && reporting.multiplePosts) {
				menu.add(0, ACTION_MENU_SEND_REPORT, 0, R.string.action_report)
						.setIcon(obtainIcon(R.attr.iconActionReport)).setShowAsAction(flags);
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
			case ACTION_MENU_MAKE_THREADSHOT: {
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
			case ACTION_MENU_REPLY: {
				ArrayList<Replyable.ReplyData> data = new ArrayList<>();
				for (PostItem postItem : getAdapter().getSelectedItems()) {
					data.add(new Replyable.ReplyData(postItem.getPostNumber(), null));
				}
				if (data.size() > 0) {
					replyable.onRequestReply(CommonUtils.toArray(data, Replyable.ReplyData.class));
				}
				mode.finish();
				return true;
			}
			case ACTION_MENU_DELETE_POSTS: {
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				ArrayList<String> postNumbers = new ArrayList<>();
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
			case ACTION_MENU_SEND_REPORT: {
				ArrayList<PostItem> postItems = getAdapter().getSelectedItems();
				ArrayList<String> postNumbers = new ArrayList<>();
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
	public boolean onSearchSubmit(String query) {
		PostsAdapter adapter = getAdapter();
		if (adapter.getItemCount() == 0) {
			return false;
		}
		searchFoundPosts.clear();
		int listPosition = ListPosition.obtain(getRecyclerView()).position;
		searchLastPosition = 0;
		boolean positionDefined = false;
		Locale locale = Locale.getDefault();
		SearchHelper helper = new SearchHelper(Preferences.isAdvancedSearch());
		helper.setFlags("m", "r", "a", "d", "e", "n", "op");
		HashSet<String> queries = helper.handleQueries(locale, query);
		HashSet<String> fileNames = new HashSet<>();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		int newPostPosition = adapter.findPositionByPostNumber(parcelableExtra.newPostNumber);
		OUTER: for (int i = 0; i < adapter.getItemCount(); i++) {
			PostItem postItem = adapter.getItem(i);
			if (!postItem.isHidden(hidePerformer)) {
				String postNumber = postItem.getPostNumber();
				String comment = postItem.getComment().toString().toLowerCase(locale);
				int postPosition = getAdapter().findPositionByPostNumber(postNumber);
				boolean userPost = postItem.isUserPost();
				boolean reply = false;
				HashSet<String> referencesTo = postItem.getReferencesTo();
				if (referencesTo != null) {
					for (String referenceTo : referencesTo) {
						if (retainExtra.userPostNumbers.contains(referenceTo)) {
							reply = true;
							break;
						}
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
				String name = postItem.getFullName().toString().toLowerCase(locale);
				fileNames.clear();
				ArrayList<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
				if (attachmentItems != null) {
					for (AttachmentItem attachmentItem : attachmentItems) {
						String fileName = attachmentItem.getFileName();
						if (fileName != null) {
							fileNames.add(fileName.toLowerCase(locale));
							String originalName = attachmentItem.getOriginalName();
							if (originalName != null) {
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
						searchLastPosition = searchFoundPosts.size();
						positionDefined = true;
					}
					searchFoundPosts.add(i);
				}
			}
		}
		boolean found = searchFoundPosts.size() > 0;
		getAdapter().setHighlightText(found ? queries : Collections.emptyList());
		searching = true;
		if (found) {
			setCustomSearchView(searchController);
			updateOptionsMenu();
			searchLastPosition--;
			findForward();
			return true;
		} else {
			ToastUtils.show(getContext(), R.string.message_not_found);
			searchLastPosition = -1;
			updateSearchTitle();
			return false;
		}
	}

	@Override
	public void onSearchCancel() {
		if (searching) {
			searching = false;
			setCustomSearchView(null);
			updateOptionsMenu();
			getAdapter().setHighlightText(Collections.emptyList());
		}
	}

	private void showSearchDialog() {
		if (!searchFoundPosts.isEmpty()) {
			PostsAdapter adapter = getAdapter();
			HashSet<String> postNumbers = new HashSet<>();
			for (Integer position : searchFoundPosts) {
				PostItem postItem = adapter.getItem(position);
				postNumbers.add(postItem.getPostNumber());
			}
			getUiManager().dialog().displayList(adapter.getConfigurationSet(), postNumbers);
		}
	}

	private void findBack() {
		int count = searchFoundPosts.size();
		if (count > 0) {
			searchLastPosition--;
			if (searchLastPosition < 0) {
				searchLastPosition += count;
			}
			ListViewUtils.smoothScrollToPosition(getRecyclerView(), searchFoundPosts.get(searchLastPosition));
			updateSearchTitle();
		}
	}

	private void findForward() {
		int count = searchFoundPosts.size();
		if (count > 0) {
			searchLastPosition++;
			if (searchLastPosition >= count) {
				searchLastPosition -= count;
			}
			ListViewUtils.smoothScrollToPosition(getRecyclerView(), searchFoundPosts.get(searchLastPosition));
			updateSearchTitle();
		}
	}

	private void updateSearchTitle() {
		searchTextResult.setText((searchLastPosition + 1) + "/" + searchFoundPosts.size());
	}

	private boolean handleNewPostDataList() {
		Page page = getPage();
		List<PostingService.NewPostData> newPostDataList = PostingService.getNewPostDataList(getContext(),
				page.chanName, page.boardName, page.threadNumber);
		if (newPostDataList != null) {
			boolean hasNewPostDataList = false;
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
			OUTER: for (PostingService.NewPostData newPostData : newPostDataList) {
				ReadPostsTask.UserPostPending userPostPending;
				if (newPostData.newThread) {
					userPostPending = new ReadPostsTask.NewThreadUserPostPending();
				} else if (newPostData.postNumber != null) {
					userPostPending = new ReadPostsTask.PostNumberUserPostPending(newPostData.postNumber);
					// Check this post had loaded before this callback was called
					// This can be unequivocally checked only for this type of UserPostPending
					for (PostItem postItem : getAdapter()) {
						if (userPostPending.isUserPost(postItem.getPost())) {
							postItem.setUserPost(true);
							retainExtra.userPostNumbers.add(postItem.getPostNumber());
							getUiManager().sendPostItemMessage(postItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
							serializePosts();
							continue OUTER;
						}
					}
				} else {
					userPostPending = new ReadPostsTask.CommentUserPostPending(newPostData.comment);
				}
				parcelableExtra.userPostPendingList.add(userPostPending);
				hasNewPostDataList = true;
			}
			return hasNewPostDataList;
		}
		return false;
	}

	@Override
	public int onDrawerNumberEntered(int number) {
		PostsAdapter adapter = getAdapter();
		int count = adapter.getItemCount();
		boolean success = false;
		if (count > 0 && number > 0) {
			if (number <= count) {
				int position = adapter.findPositionByOrdinalIndex(number - 1);
				if (position >= 0) {
					ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
					success = true;
				}
			}
			if (!success) {
				int position = adapter.findPositionByPostNumber(Integer.toString(number));
				if (position >= 0) {
					ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
					success = true;
				} else {
					ToastUtils.show(getContext(), R.string.message_post_not_found);
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
	public void updatePageConfiguration(String postNumber) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.scrollToPostNumber = postNumber;
		if (readTask == null && deserializeTask == null) {
			if (!scrollToSpecifiedPost(false)) {
				refreshPosts(true, false);
			}
		}
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshPosts(true, false, true);
	}

	private boolean scrollToSpecifiedPost(boolean instantly) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.scrollToPostNumber != null) {
			int position = getAdapter().findPositionByPostNumber(parcelableExtra.scrollToPostNumber);
			if (position >= 0) {
				if (instantly) {
					((LinearLayoutManager) getRecyclerView().getLayoutManager())
							.scrollToPositionWithOffset(position, 0);
				} else {
					ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
				}
				parcelableExtra.scrollToPostNumber = null;
			}
		}
		return parcelableExtra.scrollToPostNumber == null;
	}

	private void onFirstPostsLoad() {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.scrollToPostNumber == null) {
			restoreListPosition();
		}
	}

	private void onAfterPostsLoad(boolean fromCache) {
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (!parcelableExtra.isAddedToHistory) {
			parcelableExtra.isAddedToHistory = true;
			HistoryDatabase.getInstance().addHistory(page.chanName, page.boardName,
					page.threadNumber, parcelableExtra.threadTitle);
		}
		if (retainExtra.cachedPosts != null) {
			Pair<String, Uri> originalThreadData = null;
			Uri archivedThreadUri = retainExtra.cachedPosts.getArchivedThreadUri();
			if (archivedThreadUri != null) {
				String chanName = ChanManager.getInstance().getChanNameByHost(archivedThreadUri.getAuthority());
				if (chanName != null) {
					originalThreadData = new Pair<>(chanName, archivedThreadUri);
				}
			}
			if ((this.originalThreadData == null) != (originalThreadData == null)) {
				this.originalThreadData = originalThreadData;
				updateOptionsMenu();
			}
		}
		if (!fromCache) {
			FavoritesStorage.getInstance().modifyPostsCount(page.chanName, page.boardName,
					page.threadNumber, getAdapter().getExistingPostsCount());
		}
		Iterator<PostItem> iterator = getAdapter().iterator();
		if (iterator.hasNext()) {
			String title = iterator.next().getSubjectOrComment();
			if (StringUtils.isEmptyOrWhitespace(title)) {
				title = null;
			}
			FavoritesStorage.getInstance().modifyTitle(page.chanName, page.boardName,
					page.threadNumber, title, false);
			if (!StringUtils.equals(StringUtils.nullIfEmpty(parcelableExtra.threadTitle), title)) {
				HistoryDatabase.getInstance().refreshTitles(page.chanName, page.boardName,
						page.threadNumber, title);
				parcelableExtra.threadTitle = title;
				notifyTitleChanged();
			}
		}
	}

	private static final Handler HANDLER = new Handler();

	private final Runnable refreshRunnable = () -> {
		if (deserializeTask == null && readTask == null) {
			refreshPosts(true, false);
		}
		queueNextRefresh(false);
	};

	private void queueNextRefresh(boolean instant) {
		HANDLER.removeCallbacks(refreshRunnable);
		int mode = Preferences.getAutoRefreshMode();
		boolean enabled = mode == Preferences.AUTO_REFRESH_MODE_SEPARATE && autoRefreshEnabled ||
				mode == Preferences.AUTO_REFRESH_MODE_ENABLED;
		if (enabled) {
			int interval = mode == Preferences.AUTO_REFRESH_MODE_SEPARATE ? autoRefreshInterval
					: Preferences.getAutoRefreshInterval();
			if (instant) {
				HANDLER.post(refreshRunnable);
			} else {
				HANDLER.postDelayed(refreshRunnable, interval * 1000);
			}
		}
	}

	private void stopRefresh() {
		HANDLER.removeCallbacks(refreshRunnable);
	}

	private void refreshPosts(boolean checkModified, boolean reload) {
		refreshPosts(checkModified, reload, getAdapter().getItemCount() > 0);
	}

	private void refreshPosts(boolean checkModified, boolean reload, boolean showPull) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (deserializeTask != null) {
			parcelableExtra.queuedRefresh = QueuedRefresh.max(parcelableExtra.queuedRefresh,
					reload ? QueuedRefresh.RELOAD : QueuedRefresh.REFRESH);
			return;
		}
		parcelableExtra.queuedRefresh = QueuedRefresh.NONE;
		if (readTask != null) {
			readTask.cancel();
		}
		Page page = getPage();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		PostsAdapter adapter = getAdapter();
		boolean partialLoading = adapter.getItemCount() > 0;
		boolean useValidator = checkModified && partialLoading && !reload;
		readTask = new ReadPostsTask(this, page.chanName, page.boardName, page.threadNumber,
				retainExtra.cachedPosts, useValidator, reload, adapter.getLastPostNumber(),
				parcelableExtra.userPostPendingList);
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
	public void onRequestPreloadPosts(ArrayList<ReadPostsTask.Patch> patches, int oldCount) {
		int threshold = ListViewUtils.getScrollJumpThreshold(getContext());
		ArrayList<PostItem> postItems = oldCount == 0 ? new ArrayList<>() : ConcurrentUtils.mainGet(() -> {
			ArrayList<PostItem> buildPostItems = new ArrayList<>();
			PostsAdapter adapter = getAdapter();
			int count = adapter.getItemCount();
			int handleOldCount = Math.min(threshold, count);
			for (int i = 0; i < handleOldCount; i++) {
				PostItem postItem = adapter.getItem(count - i - 1);
				buildPostItems.add(postItem);
			}
			return buildPostItems;
		});
		int handleNewCount = Math.min(threshold / 4, patches.size());
		int i = 0;
		for (ReadPostsTask.Patch patch : patches) {
			if (!patch.replaceAtIndex && patch.index >= oldCount) {
				postItems.add(patch.postItem);
				if (++i == handleNewCount) {
					break;
				}
			}
		}
		CountDownLatch latch = new CountDownLatch(1);
		getAdapter().preloadPosts(postItems, latch::countDown);
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException e) {
				// Uninterruptible wait, ignore exception
			}
		}
	}

	@Override
	public void onDeserializePostsComplete(boolean success, Posts posts, ArrayList<PostItem> postItems) {
		deserializeTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		if (success && postItems != null) {
			RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
			retainExtra.userPostNumbers.clear();
			for (PostItem postItem : postItems) {
				if (postItem.isUserPost()) {
					retainExtra.userPostNumbers.add(postItem.getPostNumber());
				}
			}
		}
		onDeserializePostsCompleteInternal(success, posts, postItems, false);
	}

	private void onDeserializePostsCompleteInternal(boolean success, Posts posts,
			ArrayList<PostItem> postItems, boolean fromState) {
		PostsAdapter adapter = getAdapter();
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		retainExtra.cachedPosts = null;
		retainExtra.cachedPostItems.clear();

		if (success) {
			hidePerformer.decodeLocalAutohide(posts);
			retainExtra.cachedPosts = posts;
			retainExtra.cachedPostItems.addAll(postItems);
			ArrayList<ReadPostsTask.Patch> patches = new ArrayList<>();
			for (int i = 0; i < postItems.size(); i++) {
				patches.add(new ReadPostsTask.Patch(postItems.get(i), i));
			}
			adapter.setItems(patches, fromState);
			for (PostItem postItem : adapter) {
				if (parcelableExtra.expandedPosts.contains(postItem.getPostNumber())) {
					postItem.setExpanded(true);
				}
			}
			Pair<Boolean, Integer> autoRefreshData = posts.getAutoRefreshData();
			autoRefreshEnabled = autoRefreshData.first;
			autoRefreshInterval = Math.min(Math.max(autoRefreshData.second, Preferences.MIN_AUTO_REFRESH_INTERVAL),
					Preferences.MAX_AUTO_REFRESH_INTERVAL);
			onFirstPostsLoad();
			onAfterPostsLoad(true);
			if (!fromState) {
				showScaleAnimation();
			}
			scrollToSpecifiedPost(true);
			if (parcelableExtra.queuedRefresh != QueuedRefresh.NONE || parcelableExtra.hasNewPostDataList) {
				boolean reload = parcelableExtra.queuedRefresh == QueuedRefresh.RELOAD;
				refreshPosts(true, reload);
			}
			queueNextRefresh(false);
		} else {
			refreshPosts(false, false);
		}
		updateOptionsMenu();

		if (parcelableExtra.selectedItems != null) {
			Set<String> selected = parcelableExtra.selectedItems;
			parcelableExtra.selectedItems = null;
			if (success) {
				for (String postNumber : selected) {
					PostItem postItem = adapter.findPostItem(postNumber);
					adapter.toggleItemSelected(postItem);
				}
				selectionMode = startActionMode(this);
			}
		}
	}

	@Override
	public void onReadPostsSuccess(ReadPostsTask.Result result, boolean fullThread,
			ArrayList<ReadPostsTask.UserPostPending> removedUserPostPendings) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		PostsAdapter adapter = getAdapter();
		Page page = getPage();
		if (adapter.getItemCount() == 0) {
			StatisticsStorage.getInstance().incrementThreadsViewed(page.chanName);
		}
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.hasNewPostDataList = false;
		boolean wasEmpty = adapter.getItemCount() == 0;
		final int newPostPosition = adapter.getItemCount();
		if (removedUserPostPendings != null) {
			parcelableExtra.userPostPendingList.removeAll(removedUserPostPendings);
		}
		if (fullThread) {
			// Thread was opened for the first time
			retainExtra.cachedPosts = result.posts;
			retainExtra.cachedPostItems.clear();
			retainExtra.userPostNumbers.clear();
			for (ReadPostsTask.Patch patch : result.patches) {
				retainExtra.cachedPostItems.add(patch.postItem);
				if (patch.newPost.isUserPost()) {
					retainExtra.userPostNumbers.add(patch.newPost.getPostNumber());
				}
			}
			adapter.setItems(result.patches, false);
			boolean allowCache = CacheManager.getInstance().allowPagesCache(page.chanName);
			if (allowCache) {
				for (PostItem postItem : retainExtra.cachedPostItems) {
					postItem.setUnread(true);
				}
			}
			onFirstPostsLoad();
		} else {
			if (retainExtra.cachedPosts != null) {
				// Copy data from old model to new model
				Pair<Boolean, Integer> autoRefreshData = retainExtra.cachedPosts.getAutoRefreshData();
				result.posts.setAutoRefreshData(autoRefreshData.first, autoRefreshData.second);
				result.posts.setLocalAutohide(retainExtra.cachedPosts.getLocalAutohide());
			}
			retainExtra.cachedPosts = result.posts;
			int repliesCount = 0;
			if (!result.patches.isEmpty()) {
				// Copy data from old model to new model
				for (ReadPostsTask.Patch patch : result.patches) {
					if (patch.oldPost != null) {
						if (patch.oldPost.isUserPost()) {
							patch.newPost.setUserPost(true);
						}
						if (patch.oldPost.isHidden()) {
							patch.newPost.setHidden(true);
						}
						if (patch.oldPost.isShown()) {
							patch.newPost.setHidden(false);
						}
					}
				}
				for (ReadPostsTask.Patch patch : result.patches) {
					if (patch.newPost.isUserPost()) {
						retainExtra.userPostNumbers.add(patch.newPost.getPostNumber());
					}
					if (patch.newPostAddedToEnd) {
						HashSet<String> referencesTo = patch.postItem.getReferencesTo();
						if (referencesTo != null) {
							for (String postNumber : referencesTo) {
								if (retainExtra.userPostNumbers.contains(postNumber)) {
									repliesCount++;
									break;
								}
							}
						}
					}
				}
				adapter.mergeItems(result.patches);
				retainExtra.cachedPostItems.clear();
				for (PostItem postItem : adapter) {
					retainExtra.cachedPostItems.add(postItem);
				}
				// Mark changed posts as unread
				for (ReadPostsTask.Patch patch : result.patches) {
					patch.postItem.setUnread(true);
				}
			}
			if (result.newCount > 0 || repliesCount > 0 || result.deletedCount > 0 || result.hasEdited) {
				StringBuilder message = new StringBuilder();
				if (repliesCount > 0 || result.deletedCount > 0) {
					message.append(getQuantityString(R.plurals.text_new_posts_count_short_format,
							result.newCount, result.newCount));
					if (repliesCount > 0) {
						message.append(", ").append(getQuantityString(R.plurals.text_replies_count_format,
								repliesCount, repliesCount));
					}
					if (result.deletedCount > 0) {
						message.append(", ").append(getQuantityString(R.plurals.text_deleted_count_format,
								result.deletedCount, result.deletedCount));
					}
				} else if (result.newCount > 0) {
					message.append(getQuantityString(R.plurals.text_new_posts_count_format,
							result.newCount, result.newCount));
				} else {
					message.append(getString(R.string.message_edited_posts));
				}
				if (result.newCount > 0 && newPostPosition < adapter.getItemCount()) {
					PostItem newPostItem = adapter.getItem(newPostPosition);
					getParcelableExtra(ParcelableExtra.FACTORY).newPostNumber = newPostItem.getPostNumber();
					ClickableToast.show(getContext(), message, getString(R.string.action_show), () -> {
						if (!isDestroyed()) {
							String newPostNumber = getParcelableExtra(ParcelableExtra.FACTORY).newPostNumber;
							int newPostIndex = getAdapter().findPositionByPostNumber(newPostNumber);
							if (newPostIndex >= 0) {
								ListViewUtils.smoothScrollToPosition(getRecyclerView(), newPostIndex);
							}
						}
					}, true);
				} else {
					ClickableToast.show(getContext(), message);
				}
			}
		}
		boolean updateAdapters = result.newCount > 0 || result.deletedCount > 0 || result.hasEdited;
		serializePosts();
		if (result.hasEdited) {
			lastEditedPostNumbers.clear();
			for (ReadPostsTask.Patch patch : result.patches) {
				if (!patch.newPostAddedToEnd) {
					lastEditedPostNumbers.add(patch.newPost.getPostNumber());
				}
			}
		}
		if (updateAdapters) {
			getUiManager().dialog().updateAdapters(adapter.getConfigurationSet().stackInstance);
			notifyAllAdaptersChanged();
		}
		onAfterPostsLoad(false);
		if (wasEmpty && adapter.getItemCount() > 0) {
			showScaleAnimation();
		}
		scrollToSpecifiedPost(wasEmpty);
		parcelableExtra.scrollToPostNumber = null;
		updateOptionsMenu();
	}

	@Override
	public void onReadPostsEmpty() {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		if (getAdapter().getItemCount() == 0) {
			displayDownloadError(true, getString(R.string.message_empty_response));
		} else {
			onAfterPostsLoad(false);
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
		int position = getAdapter().positionOf(postItem);
		if (position == -1) {
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
				postItem.setUserPost(!postItem.isUserPost());
				RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
				if (postItem.isUserPost()) {
					retainExtra.userPostNumbers.add(postItem.getPostNumber());
				} else {
					retainExtra.userPostNumbers.remove(postItem.getPostNumber());
				}
				getUiManager().sendPostItemMessage(postItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
				serializePosts();
				break;
			}
			case PERFORM_SWITCH_HIDE: {
				postItem.setHidden(!postItem.isHidden(hidePerformer));
				getUiManager().sendPostItemMessage(postItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
				serializePosts();
				break;
			}
			case PERFORM_HIDE_REPLIES:
			case PERFORM_HIDE_NAME:
			case PERFORM_HIDE_SIMILAR: {
				PostsAdapter adapter = getAdapter();
				adapter.cancelPreloading();
				int result;
				switch (message) {
					case PERFORM_HIDE_REPLIES: {
						result = hidePerformer.addHideByReplies(postItem);
						break;
					}
					case PERFORM_HIDE_NAME: {
						result = hidePerformer.addHideByName(postItem);
						break;
					}
					case PERFORM_HIDE_SIMILAR: {
						result = hidePerformer.addHideSimilar(postItem);
						break;
					}
					default: {
						throw new RuntimeException();
					}
				}
				if (result == HidePerformer.ADD_SUCCESS) {
					postItem.resetHidden();
					adapter.invalidateHidden();
					notifyAllAdaptersChanged();
					hidePerformer.encodeLocalAutohide(getRetainExtra(RetainExtra.FACTORY).cachedPosts);
					serializePosts();
				} else if (result == HidePerformer.ADD_EXISTS && !postItem.isHiddenUnchecked()) {
					postItem.resetHidden();
					notifyAllAdaptersChanged();
					serializePosts();
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

	private void serializePosts() {
		Page page = getPage();
		CacheManager.getInstance().serializePosts(page.chanName, page.boardName,
				page.threadNumber, getRetainExtra(RetainExtra.FACTORY).cachedPosts);
	}
}
