package com.mishiranu.dashchan.ui.navigator.page;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Pair;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
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
import com.mishiranu.dashchan.content.WatcherNotifications;
import com.mishiranu.dashchan.content.async.CallbackProxy;
import com.mishiranu.dashchan.content.async.ExtractPostsTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.database.PagesDatabase;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.PostingService;
import com.mishiranu.dashchan.content.service.WatcherService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.content.storage.StatisticsStorage;
import com.mishiranu.dashchan.ui.DrawerForm;
import com.mishiranu.dashchan.ui.InstanceDialog;
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
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PostsLayoutManager;
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
		UiManager.Observer, ExtractPostsTask.Callback, WatcherService.Session.Callback, ActionMode.Callback {
	private static class RetainableExtra implements Retainable {
		public static final ExtraFactory<RetainableExtra> FACTORY = RetainableExtra::new;

		public PagesDatabase.Cache cache;
		public PagesDatabase.Cache.State cacheState;
		public boolean initialExtract = true;
		public boolean eraseExtract;
		public final HashMap<PostNumber, PostItem> postItems = new HashMap<>();
		public final PostItem.HideState.Map<PostNumber> hiddenPosts = new PostItem.HideState.Map<>();
		public final HashSet<PostNumber> userPosts = new HashSet<>();
		public byte[] threadExtra;
		public ErrorItem errorItem;

		public Uri archivedThreadUri;
		public int uniquePosters;

		public List<PostNumber> searchPostNumbers = Collections.emptyList();
		public boolean searching = false;
		public int searchLastIndex;

		public DialogUnit.StackInstance.State dialogsState;

		public boolean shouldExtract() {
			return cache == null || !cache.state.equals(cacheState);
		}

		@Override
		public void clear() {
			if (dialogsState != null) {
				dialogsState.dropState();
				dialogsState = null;
			}
		}
	}

	private static class ParcelableExtra implements Parcelable {
		public static final ExtraFactory<ParcelableExtra> FACTORY = ParcelableExtra::new;

		public final HashSet<PostNumber> expandedPosts = new HashSet<>();
		public final HashSet<PostNumber> unreadPosts = new HashSet<>();
		public boolean isAddedToHistory = false;
		public String threadTitle;
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
			dest.writeString(threadTitle);
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
				parcelableExtra.threadTitle = source.readString();
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

	public static class ExtractViewModel extends TaskViewModel.Proxy<ExtractPostsTask, ExtractPostsTask.Callback> {}

	public static class ReadViewModel extends ViewModel {
		private WatcherService.Session session;
		private final MutableLiveData<Pair<CallbackProxy<WatcherService.Session.Callback>,
				Boolean>> result = new MutableLiveData<>();

		private boolean visibleRefresh;
		public boolean visibleReadResult;

		public void init(WatcherService.Client client, String chanName, String boardName, String threadNumber) {
			if (session == null) {
				WatcherService.Session.Callback callback;
				callback = CallbackProxy.create(WatcherService.Session.Callback.class, result -> {
					boolean visible = visibleRefresh;
					visibleRefresh = false;
					this.result.setValue(new Pair<>(result, visible));
				});
				session = client.newSession(chanName, boardName, threadNumber, callback);
			}
		}

		public void refresh(boolean reload, boolean visible, int checkInterval) {
			if (session != null && session.refresh(reload, checkInterval)) {
				visibleRefresh = visible;
			}
		}

		public boolean hasTaskOrValue() {
			if (session != null && session.hasTask() && visibleRefresh) {
				return true;
			}
			Pair<CallbackProxy<WatcherService.Session.Callback>, Boolean> result = this.result.getValue();
			return result != null && result.second;
		}

		public void notifyExtracted() {
			if (session != null) {
				session.notifyExtracted();
			}
		}

		public void notifyEraseStarted() {
			if (session != null) {
				session.notifyEraseStarted();
			}
		}

		public void observe(LifecycleOwner owner, WatcherService.Session.Callback callback) {
			result.observe(owner, result -> {
				if (result != null) {
					this.result.setValue(null);
					visibleReadResult = result.second;
					result.first.invoke(callback);
				}
			});
		}

		@Override
		protected void onCleared() {
			session.destroy();
			session = null;
		}
	}

	private SearchWorker searchWorker;

	private Replyable replyable;
	private HidePerformer hidePerformer;

	private ActionMode selectionMode;

	private View searchControlView;
	private View searchProcessView;
	private Button searchResultText;

	private Set<PostNumber> lastNewPostNumbers = Collections.emptySet();
	private Set<PostNumber> lastEditedPostNumbers = Collections.emptySet();

	private final UiManager.PostStateProvider postStateProvider = new UiManager.PostStateProvider() {
		@Override
		public boolean isHiddenResolve(PostItem postItem) {
			if (postItem.getHideState() == PostItem.HideState.UNDEFINED) {
				RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
				PostItem.HideState hideState = retainableExtra.hiddenPosts.get(postItem.getPostNumber());
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
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			return retainableExtra.userPosts.contains(postNumber);
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
		PaddedRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new PostsLayoutManager(recyclerView.getContext()));
		Page page = getPage();
		UiManager uiManager = getUiManager();
		uiManager.view().bindThreadsPostRecyclerView(recyclerView);
		float density = ResourceUtils.obtainDensity(context);
		int dividerPadding = (int) (12f * density);
		hidePerformer = new HidePerformer(context);
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
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
				replyable, postStateProvider, getFragmentManager(), recyclerView, retainableExtra.postItems);
		recyclerView.setAdapter(adapter);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> adapter.configureDivider(c, position).horizontal(dividerPadding, dividerPadding)));
		recyclerView.addItemDecoration(adapter.createPostItemDecoration(context, dividerPadding));
		recyclerView.getPullable().setPullSides(PullableWrapper.Side.BOTH);
		recyclerView.addOnScrollListener(scrollListener);
		uiManager.observable().register(this);
		FavoritesStorage.getInstance().getObservable().register(this);
		hidePerformer.setPostsProvider(adapter);

		Context toolbarContext = getToolbarContext();
		LinearLayout searchControlLayout = new LinearLayout(toolbarContext);
		this.searchControlView = searchControlLayout;
		searchControlLayout.setOrientation(LinearLayout.HORIZONTAL);
		searchControlLayout.setGravity(Gravity.CENTER_VERTICAL);
		int buttonPadding = (int) (10f * density);
		searchResultText = new Button(toolbarContext, null, android.R.attr.borderlessButtonStyle);
		ViewUtils.setTextSizeScaled(searchResultText, 11);
		if (!C.API_LOLLIPOP) {
			searchResultText.setTypeface(null, Typeface.BOLD);
		}
		searchResultText.setPadding((int) (14f * density), 0, (int) (14f * density), 0);
		searchResultText.setMinimumWidth(0);
		searchResultText.setMinWidth(0);
		searchResultText.setOnClickListener(v -> showSearchDialog());
		searchControlLayout.addView(searchResultText, LinearLayout.LayoutParams.WRAP_CONTENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		ImageView backButtonView = new ImageView(toolbarContext, null, android.R.attr.borderlessButtonStyle);
		backButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		backButtonView.setImageDrawable(getActionBarIcon(R.attr.iconActionBack));
		backButtonView.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
		backButtonView.setOnClickListener(v -> findNext(-1));
		searchControlLayout.addView(backButtonView, (int) (48f * density), (int) (48f * density));
		ImageView forwardButtonView = new ImageView(toolbarContext, null, android.R.attr.borderlessButtonStyle);
		forwardButtonView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		forwardButtonView.setImageDrawable(getActionBarIcon(R.attr.iconActionForward));
		forwardButtonView.setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding);
		forwardButtonView.setOnClickListener(v -> findNext(1));
		searchControlLayout.addView(forwardButtonView, (int) (48f * density), (int) (48f * density));
		if (C.API_LOLLIPOP) {
			for (int i = 0, last = searchControlLayout.getChildCount() - 1; i <= last; i++) {
				View view = searchControlLayout.getChildAt(i);
				if (i == 0) {
					ViewUtils.setNewMarginRelative(view, (int) (-6f * density), null, null, null);
				}
				if (i == last) {
					ViewUtils.setNewMarginRelative(view, null, null, (int) (6f * density), null);
				} else {
					ViewUtils.setNewMarginRelative(view, null, null, (int) (-6f * density), null);
				}
			}
		}
		FrameLayout searchProcessLayout = new FrameLayout(toolbarContext);
		ProgressBar searchProgress = new ProgressBar(toolbarContext, null, android.R.attr.progressBarStyleSmall);
		if (C.API_LOLLIPOP) {
			int color = ResourceUtils.getColor(toolbarContext, android.R.attr.textColorPrimary);
			searchProgress.setIndeterminateTintList(ColorStateList.valueOf(color));
		}
		searchProcessLayout.addView(searchProgress, (int) (20f * density), (int) (20f * density));
		((FrameLayout.LayoutParams) searchProgress.getLayoutParams()).gravity = Gravity.CENTER;
		if (C.API_LOLLIPOP) {
			ViewUtils.setNewMarginRelative(searchProgress, (int) (12f * density), 0, (int) (16f * density), 0);
		} else {
			ViewUtils.setNewMarginRelative(searchProgress, (int) (8f * density), 0, (int) (12f * density), 0);
		}
		searchProcessView = searchProcessLayout;

		InitRequest initRequest = getInitRequest();
		ExtractViewModel extractViewModel = getViewModel(ExtractViewModel.class);
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		readViewModel.init(uiManager.callback().getWatcherClient(), page.chanName, page.boardName, page.threadNumber);
		if (initRequest.threadTitle != null && parcelableExtra.threadTitle == null) {
			parcelableExtra.threadTitle = initRequest.threadTitle;
		}
		if (initRequest.postNumber != null) {
			parcelableExtra.scrollToPostNumber = initRequest.postNumber;
		}
		boolean hasNewPosts = consumeNewPostData();
		boolean load = initRequest.shouldLoad || hasNewPosts;
		if (initRequest.errorItem != null && !load) {
			switchError(initRequest.errorItem);
		} else {
			boolean extract = true;
			if (retainableExtra.cache != null && retainableExtra.postItems.size() > 0) {
				extract = false;
				onExtractPostsCompleteInternal(true, null);
				String searchSubmitQuery = getInitSearch().submitQuery;
				if (searchSubmitQuery == null) {
					retainableExtra.searching = false;
				}
				if (retainableExtra.searching && !retainableExtra.searchPostNumbers.isEmpty()) {
					setCustomSearchView(searchControlView);
					updateSearchTitle();
				}
				decodeThreadExtra();
				if (retainableExtra.dialogsState != null) {
					uiManager.dialog().restoreState(adapter.getConfigurationSet(), retainableExtra.dialogsState);
					retainableExtra.dialogsState.dropState();
					retainableExtra.dialogsState = null;
				}
			} else {
				retainableExtra.cache = null;
				if (!retainableExtra.postItems.isEmpty()) {
					throw new IllegalStateException();
				}
				retainableExtra.initialExtract = true;
				retainableExtra.searching = false;
			}
			boolean progress = false;
			if (extractViewModel.hasTaskOrValue()) {
				progress = true;
			} else if (extract) {
				extractPostsWithoutIndication(PagesDatabase.Cleanup.NONE);
				progress = true;
			}
			if (readViewModel.hasTaskOrValue()) {
				progress = true;
			} else if (load) {
				refreshPostsWithoutIndication(false);
				progress = true;
			}
			if (progress) {
				if (adapter.getItemCount() == 0) {
					recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
					switchProgress();
				} else {
					recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTTOM);
				}
			}
		}
		extractViewModel.observe(this, this);
		readViewModel.observe(this, this);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
			retainableExtra.dialogsState = null;
		}
		queueNextRefresh(true);
	}

	@Override
	protected void onResume() {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
			retainableExtra.dialogsState = null;
		}
	}

	@Override
	protected void onDestroy() {
		stopRefresh();
		if (selectionMode != null) {
			selectionMode.finish();
			selectionMode = null;
		}
		getAdapter().cancelPreloading();
		getUiManager().dialog().closeDialogs(getAdapter().getConfigurationSet().stackInstance);
		getUiManager().observable().unregister(this);
		if (searchWorker != null) {
			searchWorker.cancel();
			searchWorker = null;
		}
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
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.dialogsState != null) {
			retainableExtra.dialogsState.dropState();
		}
		retainableExtra.dialogsState = adapter.getConfigurationSet().stackInstance.collectState();
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
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
		getUiManager().interaction().handlePostContextMenu(getAdapter().getConfigurationSet(), postItem);
		return true;
	}

	private void setPostUserPost(PostItem postItem, boolean userPost) {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (userPost) {
			retainableExtra.userPosts.add(postItem.getPostNumber());
		} else {
			retainableExtra.userPosts.remove(postItem.getPostNumber());
		}
		CommonDatabase.getInstance().getPosts().setFlags(true, getPage().chanName, postItem.getBoardName(),
				postItem.getThreadNumber(), postItem.getPostNumber(),
				retainableExtra.hiddenPosts.get(postItem.getPostNumber()), userPost);
	}

	private void setPostHideState(PostItem postItem, PostItem.HideState hideState) {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		retainableExtra.hiddenPosts.set(postItem.getPostNumber(), hideState);
		CommonDatabase.getInstance().getPosts().setFlags(true, getPage().chanName, postItem.getBoardName(),
				postItem.getThreadNumber(), postItem.getPostNumber(),
				hideState, retainableExtra.userPosts.contains(postItem.getPostNumber()));
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
		SubMenu contentsMenu = menu.addSubMenu(0, R.id.menu_contents, 0, R.string.contents);
		contentsMenu.getItem().setIcon(getActionBarIcon(R.attr.iconActionSync))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		contentsMenu.add(0, R.id.menu_refresh, 0, R.string.refresh);
		contentsMenu.add(0, R.id.menu_reload, 0, R.string.reload);
		contentsMenu.add(0, R.id.menu_erase, 0, R.string.erase);
		contentsMenu.add(0, R.id.menu_clear_old, 0, R.string.clear_old);
		contentsMenu.add(0, R.id.menu_clear_deleted, 0, R.string.clear_deleted);
		menu.add(0, R.id.menu_summary, 0, R.string.summary);
		menu.add(0, R.id.menu_hidden_posts, 0, R.string.hidden_posts);
		menu.addSubMenu(0, R.id.menu_appearance, 0, R.string.appearance);
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
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		Page page = getPage();
		PostsAdapter adapter = getAdapter();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		menu.findItem(R.id.menu_add_post).setVisible(replyable != null && replyable.onRequestReply(false));
		menu.findItem(R.id.menu_erase).setVisible(adapter.getItemCount() > 0);
		menu.findItem(R.id.menu_clear_old).setVisible(adapter.hasOldPosts());
		menu.findItem(R.id.menu_clear_deleted).setVisible(adapter.hasDeletedPosts());
		menu.findItem(R.id.menu_hidden_posts).setVisible(hidePerformer.hasLocalFilters());
		boolean isFavorite = FavoritesStorage.getInstance().hasFavorite(page.chanName, page.boardName,
				page.threadNumber);
		boolean iconFavorite = ResourceUtils.isTabletOrLandscape(getResources().getConfiguration());
		menu.findItem(R.id.menu_star_text).setVisible(!iconFavorite && !isFavorite);
		menu.findItem(R.id.menu_unstar_text).setVisible(!iconFavorite && isFavorite);
		menu.findItem(R.id.menu_star_icon).setVisible(iconFavorite && !isFavorite);
		menu.findItem(R.id.menu_unstar_icon).setVisible(iconFavorite && isFavorite);
		menu.findItem(R.id.menu_open_original_thread)
				.setVisible(Chan.getPreferred(null, retainableExtra.archivedThreadUri).name != null);
		boolean canBeArchived = !ChanManager.getInstance().getArchiveChanNames(page.chanName).isEmpty() ||
				!getChan().configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
		menu.findItem(R.id.menu_archive).setVisible(canBeArchived);
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
				PaddedRecyclerView recyclerView = getRecyclerView();
				View child = recyclerView.getChildAt(0);
				GalleryItem.Set gallerySet = adapter.getGallerySet();
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
			case R.id.menu_reload: {
				refreshPosts(true);
				return true;
			}
			case R.id.menu_erase: {
				showEraseDialog(getFragmentManager());
				return true;
			}
			case R.id.menu_clear_old: {
				extractPosts(PagesDatabase.Cleanup.OLD);
				return true;
			}
			case R.id.menu_clear_deleted: {
				showClearDeletedDialog(getFragmentManager());
				return true;
			}
			case R.id.menu_summary: {
				showSummaryDialog(getFragmentManager());
				return true;
			}
			case R.id.menu_hidden_posts: {
				List<String> localFilters = hidePerformer.getReadableLocalFilters(getContext());
				showHiddenPostsDialog(getFragmentManager(), localFilters);
				return true;
			}
			case R.id.menu_star_text:
			case R.id.menu_star_icon: {
				ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
				FavoritesStorage.getInstance().add(page.chanName, page.boardName,
						page.threadNumber, parcelableExtra.threadTitle);
				updateOptionsMenu();
				return true;
			}
			case R.id.menu_unstar_text:
			case R.id.menu_unstar_icon: {
				FavoritesStorage.getInstance().remove(page.chanName, page.boardName, page.threadNumber);
				updateOptionsMenu();
				return true;
			}
			case R.id.menu_open_original_thread: {
				RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
				Chan chan = Chan.getPreferred(null, retainableExtra.archivedThreadUri);
				if (chan.name != null) {
					Uri uri = retainableExtra.archivedThreadUri;
					String boardName = chan.locator.safe(true).getBoardName(uri);
					String threadNumber = chan.locator.safe(true).getThreadNumber(uri);
					if (threadNumber != null) {
						String threadTitle = adapter.getItem(0).getSubjectOrComment();
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
					getUiManager().dialog().performSendArchiveThread(getFragmentManager(),
							page.chanName, page.boardName, page.threadNumber, threadTitle, posts);
				}
				return true;
			}
		}
		return false;
	}

	private static void showEraseDialog(FragmentManager fragmentManager) {
		new InstanceDialog(fragmentManager, null, provider -> new AlertDialog
				.Builder(provider.getContext())
				.setTitle(R.string.erase)
				.setMessage(R.string.thread_will_be_deleted_from_cache__sentence)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					PostsPage postsPage = extract(provider);
					postsPage.extractPosts(PagesDatabase.Cleanup.ERASE);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.create());
	}

	private static void showClearDeletedDialog(FragmentManager fragmentManager) {
		new InstanceDialog(fragmentManager, null, provider -> new AlertDialog
				.Builder(provider.getContext())
				.setTitle(R.string.clear_deleted)
				.setMessage(R.string.deleted_posts_will_be_deleted__sentence)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					PostsPage postsPage = extract(provider);
					postsPage.extractPosts(PagesDatabase.Cleanup.DELETED);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.create());
	}

	private static void showSummaryDialog(FragmentManager fragmentManager) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = provider.getContext();
			PostsPage postsPage = extract(provider);
			Page page = postsPage.getPage();
			RetainableExtra retainableExtra = postsPage.getRetainableExtra(RetainableExtra.FACTORY);
			int files = 0;
			int postsWithFiles = 0;
			int links = 0;
			for (PostItem postItem : postsPage.getAdapter()) {
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
			AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(R.string.summary)
					.setPositiveButton(android.R.string.ok, null)
					.create();
			SummaryLayout layout = new SummaryLayout(dialog);
			String boardName = page.boardName;
			if (boardName != null) {
				String title = Chan.get(page.chanName).configuration.getBoardTitle(boardName);
				title = StringUtils.formatBoardTitle(page.chanName, boardName, title);
				layout.add(context.getString(R.string.board), title);
			}
			layout.add(context.getString(R.string.files__genitive), Integer.toString(files));
			layout.add(context.getString(R.string.posts_with_files__genitive), Integer.toString(postsWithFiles));
			layout.add(context.getString(R.string.links_attachments__genitive), Integer.toString(links));
			if (retainableExtra.uniquePosters > 0) {
				layout.add(context.getString(R.string.unique_posters__genitive),
						Integer.toString(retainableExtra.uniquePosters));
			}
			return dialog;
		});
	}

	private static void showHiddenPostsDialog(FragmentManager fragmentManager, List<String> localFilters) {
		boolean[] checked = new boolean[localFilters.size()];
		new InstanceDialog(fragmentManager, null, provider -> new AlertDialog
				.Builder(provider.getContext())
				.setTitle(R.string.remove_rules)
				.setMultiChoiceItems(CommonUtils.toArray(localFilters, String.class),
						checked, (d, which, isChecked) -> checked[which] = isChecked)
				.setPositiveButton(android.R.string.ok, (d, which) -> {
					PostsPage postsPage = extract(provider);
					boolean hasDeleted = false;
					for (int i = 0, j = 0; i < checked.length; i++, j++) {
						if (checked[i]) {
							postsPage.hidePerformer.removeLocalFilter(j--);
							hasDeleted = true;
						}
					}
					if (hasDeleted) {
						PostsAdapter adapter = postsPage.getAdapter();
						adapter.invalidateHidden();
						postsPage.notifyAllAdaptersChanged();
						postsPage.encodeAndStoreThreadExtra();
						adapter.preloadPosts(((LinearLayoutManager) postsPage.getRecyclerView()
								.getLayoutManager()).findFirstVisibleItemPosition());
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.create());
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
		PostsAdapter adapter = getAdapter();
		switch (item.getItemId()) {
			case R.id.menu_make_threadshot: {
				ArrayList<PostItem> postItems = adapter.getSelectedItems();
				if (postItems.size() > 0) {
					Page page = getPage();
					String threadTitle = adapter.getItem(0).getSubjectOrComment();
					new ThreadshotPerformer(getFragmentManager(), page.chanName, page.boardName, page.threadNumber,
							threadTitle, postItems, getRecyclerView().getWidth());
				}
				mode.finish();
				return true;
			}
			case R.id.menu_reply: {
				ArrayList<Replyable.ReplyData> data = new ArrayList<>();
				for (PostItem postItem : adapter.getSelectedItems()) {
					data.add(new Replyable.ReplyData(postItem.getPostNumber(), null));
				}
				if (data.size() > 0) {
					replyable.onRequestReply(true, CommonUtils.toArray(data, Replyable.ReplyData.class));
				}
				mode.finish();
				return true;
			}
			case R.id.menu_delete: {
				ArrayList<PostItem> postItems = adapter.getSelectedItems();
				ArrayList<PostNumber> postNumbers = new ArrayList<>();
				for (PostItem postItem : postItems) {
					if (!postItem.isDeleted()) {
						postNumbers.add(postItem.getPostNumber());
					}
				}
				if (postNumbers.size() > 0) {
					Page page = getPage();
					getUiManager().dialog().performSendDeletePosts(getFragmentManager(),
							page.chanName, page.boardName, page.threadNumber, postNumbers);
				}
				mode.finish();
				return true;
			}
			case R.id.menu_report: {
				ArrayList<PostItem> postItems = adapter.getSelectedItems();
				ArrayList<PostNumber> postNumbers = new ArrayList<>();
				for (PostItem postItem : postItems) {
					if (!postItem.isDeleted()) {
						postNumbers.add(postItem.getPostNumber());
					}
				}
				if (postNumbers.size() > 0) {
					Page page = getPage();
					getUiManager().dialog().performSendReportPosts(getFragmentManager(),
							page.chanName, page.boardName, page.threadNumber, postNumbers);
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
			return true;
		}
		List<PostItem> postItems = adapter.copyItems();
		if (searchWorker != null) {
			searchWorker.cancel();
		}
		searchWorker = new SearchWorker(postStateProvider, getChan(), postItems, query,
				lastEditedPostNumbers, lastNewPostNumbers, this::onSearchResult);
		setCustomSearchView(searchProcessView);
		return false;
	}

	@Override
	public void onSearchCancel() {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (retainableExtra.searching) {
			retainableExtra.searching = false;
			setCustomSearchView(null);
			updateOptionsMenu();
			getAdapter().setHighlightText(Collections.emptyList());
		}
	}

	private void onSearchResult(List<PostNumber> foundPostNumbers, Set<String> queries) {
		searchWorker = null;
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		retainableExtra.searchPostNumbers = foundPostNumbers;
		retainableExtra.searching = true;
		if (foundPostNumbers.isEmpty()) {
			setCustomSearchView(null);
			getAdapter().setHighlightText(Collections.emptyList());
			ClickableToast.show(R.string.not_found);
			updateSearchTitle();
		} else {
			setCustomSearchView(searchControlView);
			getAdapter().setHighlightText(queries);
			int listPosition = ((LinearLayoutManager) getRecyclerView().getLayoutManager())
					.findFirstVisibleItemPosition();
			clearSearchFocus();
			PostNumber postNumber = listPosition >= 0 ? getAdapter().getItem(listPosition).getPostNumber() : null;
			int index = 0;
			if (postNumber != null) {
				for (int i = 0; i < foundPostNumbers.size(); i++) {
					if (foundPostNumbers.get(i).compareTo(postNumber) >= 0) {
						index = i;
						break;
					}
				}
			}
			retainableExtra.searchLastIndex = index;
			findNext(index);
		}
	}

	private void showSearchDialog() {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (!retainableExtra.searchPostNumbers.isEmpty()) {
			getUiManager().dialog().displayList(getAdapter().getConfigurationSet(), retainableExtra.searchPostNumbers);
		}
	}

	private void findNext(int addIndex) {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		int count = retainableExtra.searchPostNumbers.size();
		if (count > 0) {
			retainableExtra.searchLastIndex = (retainableExtra.searchLastIndex + addIndex + count) % count;
			int position = getAdapter().positionOfPostNumber(retainableExtra.searchPostNumbers
					.get(retainableExtra.searchLastIndex));
			if (position >= 0) {
				ListViewUtils.smoothScrollToPosition(getRecyclerView(), position);
			}
			updateSearchTitle();
		}
	}

	private void updateSearchTitle() {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		searchResultText.setText((retainableExtra.searchLastIndex + 1) + "/" +
				retainableExtra.searchPostNumbers.size());
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
					ClickableToast.show(R.string.post_is_not_found);
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
		if (!scrollToPostFromExtra(false)) {
			if (!hasReadTask()) {
				refreshPosts(false);
			}
		}
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshPostsWithoutIndication(false);
	}

	private boolean scrollToPostFromExtra(boolean instantly) {
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		if (parcelableExtra.scrollToPostNumber != null) {
			int position = getAdapter().positionOfPostNumber(parcelableExtra.scrollToPostNumber);
			if (position >= 0) {
				PaddedRecyclerView recyclerView = getRecyclerView();
				if (instantly) {
					((LinearLayoutManager) recyclerView.getLayoutManager())
							.scrollToPositionWithOffset(position, 0);
				} else {
					ListViewUtils.smoothScrollToPosition(recyclerView, position);
				}
				parcelableExtra.scrollToPostNumber = null;
				return true;
			}
		}
		return false;
	}

	private void decodeThreadExtra() {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		boolean localFiltersDecoded = false;
		if (retainableExtra.threadExtra != null) {
			try (JsonSerial.Reader reader = JsonSerial.reader(retainableExtra.threadExtra)) {
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
				retainableExtra.threadExtra = null;
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
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		retainableExtra.threadExtra = extra;
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

	private void extractPosts(PagesDatabase.Cleanup cleanup) {
		startProgressIfNecessary();
		extractPostsWithoutIndication(cleanup);
	}

	private void extractPostsWithoutIndication(PagesDatabase.Cleanup cleanup) {
		Page page = getPage();
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		if (cleanup == PagesDatabase.Cleanup.ERASE) {
			retainableExtra.eraseExtract = true;
		}
		if (cleanup == PagesDatabase.Cleanup.NONE && Preferences.getCyclicalRefreshMode() ==
				Preferences.CyclicalRefreshMode.FULL_LOAD_CLEANUP) {
			cleanup = PagesDatabase.Cleanup.OLD;
		}
		if (retainableExtra.eraseExtract) {
			cleanup = PagesDatabase.Cleanup.ERASE;
			ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
			readViewModel.notifyEraseStarted();
		}
		ExtractViewModel extractViewModel = getViewModel(ExtractViewModel.class);
		ExtractPostsTask task = new ExtractPostsTask(extractViewModel.callback, retainableExtra.cache,
				getChan(), page.boardName, page.threadNumber, retainableExtra.initialExtract, cleanup);
		task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
		extractViewModel.attach(task);
	}

	private int getAutoRefreshInterval() {
		return Preferences.getAutoRefreshInterval() * 1000;
	}

	private final Runnable refreshRunnable = () -> {
		int interval = getAutoRefreshInterval();
		if (interval > 0 && !hasReadTask()) {
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			if (!retainableExtra.eraseExtract) {
				ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
				readViewModel.refresh(false, false, interval);
			}
		}
		queueNextRefresh(false);
	};

	private void queueNextRefresh(boolean instant) {
		ConcurrentUtils.HANDLER.removeCallbacks(refreshRunnable);
		int interval = getAutoRefreshInterval();
		if (interval > 0) {
			if (instant) {
				ConcurrentUtils.HANDLER.post(refreshRunnable);
			} else {
				ConcurrentUtils.HANDLER.postDelayed(refreshRunnable, interval);
			}
		}
	}

	private void stopRefresh() {
		ConcurrentUtils.HANDLER.removeCallbacks(refreshRunnable);
	}

	private void refreshPosts(boolean reload) {
		startProgressIfNecessary();
		refreshPostsWithoutIndication(reload);
	}

	private void refreshPostsWithoutIndication(boolean reload) {
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		readViewModel.refresh(reload, true, 0);
	}

	private boolean hasExtractTask() {
		ExtractViewModel extractViewModel = getViewModel(ExtractViewModel.class);
		return extractViewModel.hasTaskOrValue();
	}

	private boolean hasReadTask() {
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		return readViewModel.hasTaskOrValue();
	}

	private void startProgressIfNecessary() {
		if (!hasExtractTask() && !hasReadTask()) {
			PaddedRecyclerView recyclerView = getRecyclerView();
			if (getAdapter().getItemCount() == 0) {
				recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTH);
				switchProgress();
			} else {
				recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTTOM);
				switchList();
			}
		}
	}

	private void cancelProgressIfNecessary() {
		if (!hasExtractTask() && !hasReadTask()) {
			PaddedRecyclerView recyclerView = getRecyclerView();
			recyclerView.getPullable().cancelBusyState();
			switchList();
			RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
			ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
			if (retainableExtra.errorItem != null) {
				ErrorItem errorItem = retainableExtra.errorItem;
				retainableExtra.errorItem = null;
				showOrSwitchError(errorItem);
			}
			if (parcelableExtra.scrollToPostNumber != null) {
				scrollToPostFromExtra(recyclerView.getChildCount() == 0);
				// Forget about the request on fail
				parcelableExtra.scrollToPostNumber = null;
			}
		}
	}

	private void handleError(ErrorItem errorItem) {
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		retainableExtra.errorItem = errorItem;
		if (!hasExtractTask() && !hasReadTask()) {
			retainableExtra.errorItem = null;
			showOrSwitchError(errorItem);
		} else {
			retainableExtra.errorItem = errorItem;
		}
	}

	private void showOrSwitchError(ErrorItem errorItem) {
		if (getAdapter().getItemCount() == 0) {
			switchError(errorItem);
		} else {
			ClickableToast.show(errorItem);
		}
	}

	private static class LastToast {
		public String id;
		public int newCount;
		public int deletedCount;
		public boolean hasEdited;
		public int replyCount;
		public PostNumber postNumber;

		public boolean update(boolean toastVisible, int newCount, int deletedCount, boolean hasEdited, int replyCount) {
			if (toastVisible) {
				this.newCount += newCount;
				this.deletedCount += deletedCount;
				this.hasEdited |= hasEdited;
				this.replyCount += replyCount;
			} else {
				this.newCount = newCount;
				this.deletedCount = deletedCount;
				this.hasEdited = hasEdited;
				this.replyCount = replyCount;
			}
			return this.newCount > 0 || this.deletedCount > 0 || this.hasEdited || this.replyCount > 0;
		}
	}

	private final LastToast lastToast = new LastToast();

	@Override
	public void onExtractPostsComplete(ExtractPostsTask.Result result, boolean cancelled) {
		Page page = getPage();
		WatcherNotifications.cancelReplies(getContext(),
				page.chanName, page.boardName, page.threadNumber, result.replyPosts);
		if (cancelled) {
			return;
		}

		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		PaddedRecyclerView recyclerView = getRecyclerView();
		PostsAdapter adapter = getAdapter();
		boolean updateAdapters = false;
		ListPosition listPositionFromState = null;
		Pair<PostNumber, Integer> keepPositionPair = null;
		boolean initial = retainableExtra.initialExtract;
		retainableExtra.initialExtract = false;
		boolean erase = retainableExtra.eraseExtract;
		retainableExtra.eraseExtract = false;
		boolean wasEmpty = adapter.getItemCount() == 0;

		if (result != null) {
			if (erase && result.cache.isEmpty()) {
				FavoritesStorage.getInstance().remove(page.chanName, page.boardName, page.threadNumber);
				closePage();
				return;
			}
			if (result.cache.isNewThreadOnce()) {
				parcelableExtra.unreadPosts.addAll(result.postItems.keySet());
				StatisticsStorage.getInstance().incrementThreadsViewed(getPage().chanName);
			} else {
				parcelableExtra.unreadPosts.addAll(result.newPosts);
				parcelableExtra.unreadPosts.addAll(result.deletedPosts);
				parcelableExtra.unreadPosts.addAll(result.editedPosts);
			}
			retainableExtra.cache = result.cache;
			if (retainableExtra.cacheState == null) {
				retainableExtra.cacheState = retainableExtra.cache.state;
			}
			if (result.cacheChanged) {
				retainableExtra.archivedThreadUri = result.archivedThreadUri;
				retainableExtra.uniquePosters = result.uniquePosters;
			}
			if (!result.postItems.isEmpty() || !result.removedPosts.isEmpty()) {
				if (adapter.getItemCount() > 0) {
					ListPosition listPosition = ListPosition.obtain(recyclerView,
							position -> !adapter.getItem(position).isDeleted());
					if (listPosition == null) {
						listPosition = ListPosition.obtain(recyclerView, null);
					}
					keepPositionPair = transformListPositionToPair(listPosition);
				}
				adapter.insertItems(result.postItems, result.removedPosts);
				updateAdapters = true;
			}
			if (result.flags != null) {
				retainableExtra.hiddenPosts.clear();
				retainableExtra.hiddenPosts.addAll(result.flags.hiddenPosts);
				retainableExtra.userPosts.clear();
				retainableExtra.userPosts.addAll(result.flags.userPosts);
			}
			if (result.stateExtra != null) {
				listPositionFromState = transformPairToListPosition(decodeThreadState(result.stateExtra.state));
				retainableExtra.threadExtra = result.stateExtra.extra;
				decodeThreadExtra();
			}

			int newCount = result.newPosts.size();
			int deletedCount = result.deletedPosts.size();
			boolean hasEdited = !result.editedPosts.isEmpty();
			int replyCount = result.replyPosts.size();
			boolean toastVisible = ClickableToast.isShowing(lastToast.id);
			if (lastToast.update(toastVisible, newCount, deletedCount, hasEdited, replyCount)) {
				updateAdapters = true;
				String message;
				if (lastToast.replyCount > 0 || lastToast.deletedCount > 0) {
					message = getResources().getQuantityString(R.plurals.number_new__format,
							lastToast.newCount, lastToast.newCount);
					if (lastToast.replyCount > 0) {
						message = getString(R.string.__enumeration_format, message,
								getResources().getQuantityString(R.plurals.number_replies__format,
										lastToast.replyCount, lastToast.replyCount));
					}
					if (lastToast.deletedCount > 0) {
						message = getString(R.string.__enumeration_format, message,
								getResources().getQuantityString(R.plurals.number_deleted__format,
										lastToast.deletedCount, lastToast.deletedCount));
					}
				} else if (lastToast.newCount > 0) {
					message = getResources().getQuantityString(R.plurals.number_new_posts__format,
							lastToast.newCount, lastToast.newCount);
				} else {
					message = getString(R.string.some_posts_have_been_edited);
				}

				if (lastToast.newCount > 0) {
					PostNumber showPostNumber;
					if (toastVisible && lastToast.postNumber != null) {
						showPostNumber = lastToast.postNumber;
					} else {
						showPostNumber = Collections.min(result.newPosts);
						adapter.preloadPosts(showPostNumber);
						lastToast.postNumber = showPostNumber;
					}
					lastToast.id = ClickableToast.show(message, lastToast.id,
							new ClickableToast.Button(R.string.show, true, () -> {
								if (isRunning()) {
									int newPostIndex = adapter.positionOfPostNumber(showPostNumber);
									if (newPostIndex >= 0) {
										ListViewUtils.smoothScrollToPosition(getRecyclerView(), newPostIndex);
									}
								}
							}));
				} else {
					lastToast.id = ClickableToast.show(message, lastToast.id, null);
				}

				if (deletedCount > 0 || hasEdited) {
					HashSet<PostNumber> editedPostNumbers = toastVisible
							? new HashSet<>(lastEditedPostNumbers) : new HashSet<>();
					editedPostNumbers.addAll(result.deletedPosts);
					editedPostNumbers.addAll(result.editedPosts);
					lastEditedPostNumbers = editedPostNumbers;
				}
				if (newCount > 0) {
					HashSet<PostNumber> newPostNumbers = toastVisible
							? new HashSet<>(lastNewPostNumbers) : new HashSet<>();
					newPostNumbers.addAll(result.newPosts);
					lastNewPostNumbers = newPostNumbers;
				}
				retainableExtra.errorItem = null;
			}

			ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
			readViewModel.notifyExtracted();
		}

		if (updateAdapters) {
			getUiManager().dialog().updateAdapters(getAdapter().getConfigurationSet().stackInstance);
			notifyAllAdaptersChanged();
			ListPosition listPosition = transformPairToListPosition(keepPositionPair);
			if (listPosition != null) {
				listPosition.apply(recyclerView);
			}
		}
		if (result != null) {
			if (initial && result.postItems.isEmpty()) {
				if (!hasReadTask()) {
					refreshPostsWithoutIndication(false);
				}
			} else {
				if (wasEmpty && !result.postItems.isEmpty()) {
					recyclerView.getPullable().cancelBusyState();
					switchList();
					recyclerView.getPullable().startBusyState(PullableWrapper.Side.BOTTOM);
					showScaleAnimation();
				}
				if (retainableExtra.shouldExtract()) {
					extractPostsWithoutIndication(PagesDatabase.Cleanup.NONE);
				}
			}
			onExtractPostsCompleteInternal(wasEmpty, listPositionFromState);
		} else {
			cancelProgressIfNecessary();
			handleError(new ErrorItem(ErrorItem.Type.UNKNOWN));
		}
	}

	private void onExtractPostsCompleteInternal(boolean firstLayout, ListPosition listPositionFromState) {
		PostsAdapter adapter = getAdapter();
		ListPosition listPosition = takeListPosition();
		if (listPosition == null) {
			listPosition = listPositionFromState;
		}
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		Page page = getPage();

		if (firstLayout && (parcelableExtra.scrollToPostNumber == null ||
				!scrollToPostFromExtra(true)) && listPosition != null) {
			listPosition.apply(getRecyclerView());
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
			for (PostNumber postNumber : selected) {
				PostItem postItem = adapter.findPostItem(postNumber);
				if (postItem != null) {
					adapter.toggleItemSelected(postItem);
				}
			}
			selectionMode = startActionMode(this);
		}

		updateOptionsMenu();
		cancelProgressIfNecessary();
	}

	@Override
	public void onReadPostsSuccess(PagesDatabase.Cache.State cacheState, ConsumeReplies consumeReplies) {
		ReadViewModel readViewModel = getViewModel(ReadViewModel.class);
		RetainableExtra retainableExtra = getRetainableExtra(RetainableExtra.FACTORY);
		retainableExtra.cacheState = cacheState;
		if ((readViewModel.visibleReadResult || getAutoRefreshInterval() > 0) &&
				!hasExtractTask() && retainableExtra.shouldExtract()) {
			consumeReplies.consume();
			if (!readViewModel.visibleReadResult) {
				startProgressIfNecessary();
			}
			extractPostsWithoutIndication(PagesDatabase.Cleanup.NONE);
		} else {
			cancelProgressIfNecessary();
		}
		queueNextRefresh(false);
	}

	@Override
	public void onReadPostsRedirect(RedirectException.Target target) {
		cancelProgressIfNecessary();
		queueNextRefresh(false);
		handleRedirect(target.chanName, target.boardName, target.threadNumber, target.postNumber);
	}

	@Override
	public void onReadPostsFail(ErrorItem errorItem) {
		cancelProgressIfNecessary();
		handleError(errorItem);
		ParcelableExtra parcelableExtra = getParcelableExtra(ParcelableExtra.FACTORY);
		parcelableExtra.scrollToPostNumber = null;
		queueNextRefresh(false);
	}

	private Runnable postNotifyDataSetChanged;

	@Override
	public void onPostItemMessage(PostItem postItem, UiManager.Message message) {
		int position = getAdapter().positionOfPostNumber(postItem.getPostNumber());
		if (position < 0) {
			return;
		}
		PaddedRecyclerView recyclerView = getRecyclerView();
		switch (message) {
			case POST_INVALIDATE_ALL_VIEWS: {
				if (postNotifyDataSetChanged == null) {
					postNotifyDataSetChanged = getAdapter()::notifyDataSetChanged;
				}
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
						result = hidePerformer.addHideByName(getChan(), postItem);
						break;
					}
					case PERFORM_HIDE_SIMILAR: {
						result = hidePerformer.addHideSimilar(getChan(), postItem);
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
				adapter.preloadPosts(((LinearLayoutManager) recyclerView.getLayoutManager())
						.findFirstVisibleItemPosition());
				break;
			}
			case PERFORM_GO_TO_POST: {
				// Avoid concurrent modification
				recyclerView.post(() -> getUiManager().dialog()
						.closeDialogs(getAdapter().getConfigurationSet().stackInstance));
				ListViewUtils.smoothScrollToPosition(recyclerView, position);
				break;
			}
		}
	}

	@Override
	public void onReloadAttachmentItem(AttachmentItem attachmentItem) {
		PostsAdapter adapter = getAdapter();
		int position = adapter.positionOfPostNumber(attachmentItem.getPostNumber());
		if (position >= 0) {
			adapter.reloadAttachment(position, attachmentItem);
		}
	}

	private static class SearchWorker implements Runnable {
		public interface Callback {
			void onResult(List<PostNumber> foundPostNumbers, Set<String> queries);
		}

		private final UiManager.PostStateProvider postStateProvider;
		private final Chan chan;
		private final List<PostItem> postItems;
		private final Set<PostNumber> editedPostNumbers;
		private final Set<PostNumber> newPostNumbers;
		private final Callback callback;

		private final SearchHelper helper;
		private final Set<String> queries;
		private final HashSet<String> fileNames = new HashSet<>();
		private final ArrayList<PostNumber> foundPostNumbers = new ArrayList<>();

		private int start = 0;

		public SearchWorker(UiManager.PostStateProvider postStateProvider, Chan chan, List<PostItem> postItems,
				String query, Set<PostNumber> editedPostNumbers, Set<PostNumber> newPostNumbers, Callback callback) {
			this.postStateProvider = postStateProvider;
			this.chan = chan;
			this.postItems = postItems;
			this.newPostNumbers = newPostNumbers;
			this.editedPostNumbers = editedPostNumbers;
			this.callback = callback;
			helper = new SearchHelper(Preferences.isAdvancedSearch());
			helper.setFlags("m", "r", "a", "d", "e", "n", "op");
			queries = helper.handleQueries(Locale.getDefault(), query);
			ConcurrentUtils.HANDLER.post(this);
		}

		@Override
		public void run() {
			long time = SystemClock.elapsedRealtime();
			Locale locale = Locale.getDefault();
			HashSet<String> fileNames = this.fileNames;
			OUTER: while (true) {
				if (SystemClock.elapsedRealtime() - time >= ConcurrentUtils.HALF_FRAME_TIME_MS) {
					ConcurrentUtils.HANDLER.post(this);
					break;
				}
				int index = start++;
				if (index >= postItems.size()) {
					Collections.sort(foundPostNumbers);
					callback.onResult(foundPostNumbers, queries);
					break;
				}
				PostItem postItem = postItems.get(index);
				if (!postStateProvider.isHiddenResolve(postItem)) {
					PostNumber postNumber = postItem.getPostNumber();
					String comment = postItem.getComment(chan).toString().toLowerCase(locale);
					boolean userPost = postStateProvider.isUserPost(postNumber);
					boolean reply = false;
					for (PostNumber referenceTo : postItem.getReferencesTo()) {
						if (postStateProvider.isUserPost(referenceTo)) {
							reply = true;
							break;
						}
					}
					boolean hasAttachments = postItem.hasAttachments();
					boolean deleted = postItem.isDeleted();
					boolean edited = editedPostNumbers.contains(postNumber);
					boolean newPost = newPostNumbers.contains(postNumber);
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
						foundPostNumbers.add(postNumber);
					}
				}
			}
		}

		public void cancel() {
			ConcurrentUtils.HANDLER.removeCallbacks(this);
		}
	}
}
