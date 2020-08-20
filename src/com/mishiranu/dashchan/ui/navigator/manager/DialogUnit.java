package com.mishiranu.dashchan.ui.navigator.manager;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.model.Posts;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.CancellableTask;
import com.mishiranu.dashchan.content.async.ReadSinglePostTask;
import com.mishiranu.dashchan.content.async.SendLocalArchiveTask;
import com.mishiranu.dashchan.content.async.SendMultifunctionalTask;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.DialogStack;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PostsLayoutManager;
import com.mishiranu.dashchan.widget.ProgressDialog;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class DialogUnit {
	private final UiManager uiManager;

	public static class StackInstance {
		private static class AttachmentDialog {
			public final List<AttachmentItem> attachmentItems;
			public final int startImageIndex;
			public final GalleryOverlay.NavigatePostMode navigatePostMode;
			public final GalleryItem.GallerySet gallerySet;

			private AttachmentDialog(List<AttachmentItem> attachmentItems, int startImageIndex,
					GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.GallerySet gallerySet) {
				this.attachmentItems = attachmentItems;
				this.startImageIndex = startImageIndex;
				this.navigatePostMode = navigatePostMode;
				this.gallerySet = gallerySet;
			}
		}

		public static class State {
			private final ArrayList<DialogProvider.Factory> factories;
			private final AttachmentDialog attachmentDialog;

			public State(ArrayList<DialogProvider.Factory> factories, AttachmentDialog attachmentDialog) {
				this.factories = factories;
				this.attachmentDialog = attachmentDialog;
			}
		}

		private final DialogStack<DialogFactory> dialogStack;
		private Pair<AttachmentDialog, Dialog> attachmentDialog;

		private StackInstance(DialogStack<DialogFactory> dialogStack) {
			this.dialogStack = dialogStack;
		}

		public State collectState() {
			ArrayList<DialogProvider.Factory> factories = new ArrayList<>();
			for (Pair<DialogFactory, View> pair : dialogStack) {
				if (pair.second != null) {
					pair.first.saveState(pair.second);
				}
				factories.add(pair.first.factory);
			}
			return new State(factories, attachmentDialog != null ? attachmentDialog.first : null);
		}
	}

	DialogUnit(UiManager uiManager) {
		this.uiManager = uiManager;
	}

	public StackInstance createStackInstance() {
		return new StackInstance(new DialogStack<>(uiManager.getContext()));
	}

	private class DialogFactory implements DialogStack.ViewFactory<DialogFactory> {
		public final DialogProvider provider;
		public final DialogProvider.Factory factory;

		private DialogFactory(DialogProvider provider, DialogProvider.Factory factory) {
			this.provider = provider;
			this.factory = factory;
		}

		@Override
		public View createView(DialogStack<DialogFactory> dialogStack) {
			Context context = uiManager.getContext();
			View content = LayoutInflater.from(context).inflate(R.layout.dialog_posts, null);
			RecyclerView recyclerView = content.findViewById(android.R.id.list);
			View progress = content.findViewById(R.id.progress);
			float density = ResourceUtils.obtainDensity(context);
			int dividerPadding = (int) (12f * density);
			recyclerView.setLayoutManager(new PostsLayoutManager(recyclerView.getContext()));
			DialogPostsAdapter adapter = new DialogPostsAdapter(uiManager, provider, recyclerView);
			recyclerView.setAdapter(adapter);
			recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
					(c, position) -> c.need(true).horizontal(dividerPadding, dividerPadding)));
			final DialogHolder holder = new DialogHolder(adapter, provider, content, recyclerView, progress);
			uiManager.observable().register(holder);
			content.setTag(holder);
			if (factory.listPosition != null) {
				factory.listPosition.apply(recyclerView);
				factory.listPosition = null;
			}
			provider.setStateListener((state) -> {
				switch (state) {
					case LIST: {
						holder.setShowLoading(false);
						holder.requestUpdate();
						return true;
					}
					case LOADING: {
						holder.setShowLoading(true);
						return true;
					}
					case ERROR: {
						if (!holder.cancelled) {
							dialogStack.pop();
							return true;
						}
						return false;
					}
				}
				return false;
			});
			return content;
		}

		@Override
		public void destroyView(View view) {
			saveState(view);
			DialogHolder holder = (DialogHolder) view.getTag();
			uiManager.view().notifyUnbindListView(holder.recyclerView);
			uiManager.observable().unregister(holder);
			holder.cancel();
		}

		public void saveState(View view) {
			DialogHolder holder = (DialogHolder) view.getTag();
			factory.listPosition = ListPosition.obtain(holder.recyclerView);
		}
	}

	private static class DialogHolder implements UiManager.Observer {
		public final DialogPostsAdapter adapter;
		public final DialogProvider dialogProvider;

		public final View content;
		public final RecyclerView recyclerView;
		public final View progress;

		public DialogHolder(DialogPostsAdapter adapter, DialogProvider dialogProvider, View content,
				RecyclerView recyclerView, View progress) {
			this.adapter = adapter;
			this.dialogProvider = dialogProvider;
			this.content = content;
			this.recyclerView = recyclerView;
			this.progress = progress;
		}

		public boolean cancelled = false;

		private Runnable postNotifyDataSetChanged;

		@Override
		public void onPostItemMessage(PostItem postItem, UiManager.Message message) {
			dialogProvider.onPostItemMessage(postItem, message);
			switch (message) {
				case POST_INVALIDATE_ALL_VIEWS: {
					boolean notify = adapter.postItems.contains(postItem);
					if (!notify) {
						// Must notify adapter to update links to shown/hidden posts
						LinkedHashSet<String> referencesFrom = postItem.getReferencesFrom();
						if (referencesFrom != null) {
							for (String referenceFrom : referencesFrom) {
								if (adapter.postNumbers.contains(referenceFrom)) {
									notify = true;
									break;
								}
							}
						}
					}
					if (notify) {
						if (postNotifyDataSetChanged == null) {
							postNotifyDataSetChanged = adapter::notifyDataSetChanged;
						}
						recyclerView.removeCallbacks(postNotifyDataSetChanged);
						recyclerView.post(postNotifyDataSetChanged);
					}
					break;
				}
				case INVALIDATE_COMMENT_VIEW: {
					int position = adapter.postItems.indexOf(postItem);
					if (position >= 0) {
						adapter.invalidateComment(position);
					}
					break;
				}
			}
		}

		public void requestUpdate() {
			if (cancelled) {
				return;
			}
			dialogProvider.onRequestUpdate();
			adapter.notifyDataSetChanged();
		}

		public void cancel() {
			if (cancelled) {
				return;
			}
			dialogProvider.onCancel();
			cancelled = true;
		}

		public void notifyDataSetChanged() {
			if (cancelled) {
				return;
			}
			adapter.notifyDataSetChanged();
		}

		public void setShowLoading(boolean loading) {
			if (cancelled) {
				return;
			}
			if (loading) {
				progress.setVisibility(View.VISIBLE);
				recyclerView.setVisibility(View.GONE);
			} else {
				recyclerView.setVisibility(View.VISIBLE);
				if (progress.getVisibility() == View.VISIBLE) {
					ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(progress, View.ALPHA, 1f, 0f);
					alphaAnimator.setDuration(200);
					alphaAnimator.addListener(new AnimationUtils.VisibilityListener(progress, View.GONE));
					alphaAnimator.start();
					recyclerView.setAlpha(0f);
					recyclerView.animate().alpha(1f).setStartDelay(200).setDuration(200).start();
				}
			}
		}
	}

	private enum State {LIST, LOADING, ERROR}

	private interface StateListener {
		public boolean onStateChanged(State state);
	}

	private static abstract class DialogProvider implements UiManager.Observer, Iterable<PostItem> {
		public static abstract class Factory {
			public ListPosition listPosition;

			public abstract DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet);
		}

		public final UiManager.ConfigurationSet configurationSet;

		public DialogProvider(UiManager.ConfigurationSet configurationSet) {
			this.configurationSet = configurationSet;
		}

		public void onRequestUpdateDemandSet(UiManager.DemandSet demandSet, int index) {}

		public void onRequestUpdate() {}

		public void onCancel() {}

		protected StateListener stateListener;

		private State queuedState = null;
		private Runnable queuedChangeCallback = null;

		public final void setStateListener(StateListener listener) {
			stateListener = listener;
			if (queuedState != null) {
				invokeStateChanged(queuedState, queuedChangeCallback);
				queuedState = null;
				queuedChangeCallback = null;
			}
		}

		protected final void switchState(State state, Runnable changeCallback) {
			if (stateListener != null) {
				invokeStateChanged(state, changeCallback);
			} else {
				queuedState = state;
				queuedChangeCallback = changeCallback;
			}
		}

		private void invokeStateChanged(State state, Runnable changeCallback) {
			boolean success = stateListener.onStateChanged(state);
			if (success && changeCallback != null) {
				changeCallback.run();
			}
		}

		@Override
		public void onPostItemMessage(PostItem postItem, UiManager.Message message) {}
	}

	private static class SingleDialogProvider extends DialogProvider {
		public static class Factory extends DialogProvider.Factory {
			private final PostItem postItem;

			private Factory(PostItem postItem) {
				this.postItem = postItem;
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new SingleDialogProvider(postItem, configurationSet.copy(false, true, null));
			}
		}

		private final PostItem postItem;

		private SingleDialogProvider(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
			super(configurationSet);
			this.postItem = postItem;
		}

		@NonNull
		@Override
		public Iterator<PostItem> iterator() {
			return Collections.singletonList(postItem).iterator();
		}
	}

	private static class ThreadDialogProvider extends DialogProvider implements CommentTextView.LinkListener,
			UiManager.PostsProvider {
		public static class Factory extends DialogProvider.Factory {
			public final PostItem postItem;

			public Factory(PostItem postItem) {
				this.postItem = postItem;
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				Intermediate intermediate = new Intermediate();
				ChanConfiguration configuration = ChanConfiguration.get(postItem.getChanName());
				boolean canReply = configuration.safe().obtainBoard(postItem.getBoardName()).allowPosting;
				Replyable replyable = null;
				if (canReply) {
					replyable = data -> uiManager.navigator().navigatePosting(postItem.getChanName(),
							postItem.getBoardName(), postItem.getThreadNumber(), data);
				}
				configurationSet = new UiManager.ConfigurationSet(replyable,
						intermediate, new HidePerformer(), new GalleryItem.GallerySet(false),
						configurationSet.stackInstance, intermediate, null, false, true, false, false, false, null);
				return new ThreadDialogProvider(uiManager, configurationSet, intermediate, postItem);
			}
		}

		private static class Intermediate implements CommentTextView.LinkListener, UiManager.PostsProvider {
			public WeakReference<ThreadDialogProvider> provider;

			@NonNull
			@Override
			public Iterator<PostItem> iterator() {
				return provider.get().iterator();
			}

			@Override
			public PostItem findPostItem(String postNumber) {
				return provider.get().findPostItem(postNumber);
			}

			@Override
			public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed) {
				provider.get().onLinkClick(view, chanName, uri, confirmed);
			}

			@Override
			public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {
				provider.get().onLinkLongClick(view, chanName, uri);
			}
		}

		private final UiManager uiManager;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private ThreadDialogProvider(UiManager uiManager, UiManager.ConfigurationSet configurationSet,
				Intermediate intermediate, PostItem postItem) {
			super(configurationSet);
			intermediate.provider = new WeakReference<>(this);
			this.uiManager = uiManager;
			if (!postItem.isThreadItem()) {
				throw new RuntimeException("Not thread item");
			}
			postItem.setOrdinalIndex(0);
			postItem.clearReferencesFrom();
			postItems.add(postItem);
			PostItem[] postItems = postItem.getThreadLastPosts();
			if (postItems != null) {
				for (int i = 0; i < postItems.length; i++) {
					postItem = postItems[i];
					this.postItems.add(postItem);
					HashSet<String> referencesTo = postItem.getReferencesTo();
					if (referencesTo != null) {
						for (String postNumber : referencesTo) {
							for (int j = 0; j < i + 1; j++) {
								PostItem foundPostItem = this.postItems.get(j);
								if (postNumber.equals(foundPostItem.getPostNumber())) {
									foundPostItem.addReferenceFrom(postItem.getPostNumber());
								}
							}
						}
					}
				}
			}
			configurationSet.gallerySet.setThreadTitle(this.postItems.get(0).getSubjectOrComment());
			for (int i = 0; i < this.postItems.size(); i++) {
				configurationSet.gallerySet.add(this.postItems.get(i).getAttachmentItems());
			}
		}

		@Override
		public PostItem findPostItem(String postNumber) {
			for (PostItem postItem : postItems) {
				if (postNumber.equals(postItem.getPostNumber())) {
					return postItem;
				}
			}
			return null;
		}

		@NonNull
		@Override
		public Iterator<PostItem> iterator() {
			return postItems.iterator();
		}

		@Override
		public void onRequestUpdateDemandSet(UiManager.DemandSet demandSet, int index) {
			demandSet.showOpenThreadButton = index == 0;
		}

		@Override
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed) {
			PostItem originalPostItem = postItems.get(0);
			String boardName = originalPostItem.getBoardName();
			String threadNumber = originalPostItem.getThreadNumber();
			ChanLocator locator = ChanLocator.get(chanName);
			if (chanName != null && locator.safe(false).isThreadUri(uri)
					&& StringUtils.equals(boardName, locator.safe(false).getBoardName(uri))
					&& StringUtils.equals(threadNumber, locator.safe(false).getThreadNumber(uri))) {
				String postNumber = locator.safe(false).getPostNumber(uri);
				if (postNumber == null) {
					postNumber = locator.safe(false).getThreadNumber(uri);
				}
				for (PostItem postItem : postItems) {
					if (postNumber.equals(postItem.getPostNumber())) {
						uiManager.dialog().displaySingle(configurationSet, postItem);
						return;
					}
				}
			}
			uiManager.interaction().handleLinkClick(configurationSet, chanName, uri, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {
			uiManager.interaction().handleLinkLongClick(configurationSet, uri);
		}
	}

	private static class RepliesDialogProvider extends DialogProvider {
		public static class Factory extends DialogProvider.Factory {
			private final PostItem postItem;

			public Factory(PostItem postItem) {
				this.postItem = postItem;
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new RepliesDialogProvider(configurationSet
						.copy(false, true, postItem.getPostNumber()), postItem);
			}
		}

		private final PostItem postItem;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private RepliesDialogProvider(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
			super(configurationSet);
			this.postItem = postItem;
			onRequestUpdate();
		}

		@NonNull
		@Override
		public Iterator<PostItem> iterator() {
			return postItems.iterator();
		}

		@Override
		public void onRequestUpdate() {
			super.onRequestUpdate();
			postItems.clear();
			LinkedHashSet<String> referencesFrom = postItem.getReferencesFrom();
			if (referencesFrom != null) {
				for (PostItem postItem : configurationSet.postsProvider) {
					if (referencesFrom.contains(postItem.getPostNumber())) {
						postItems.add(postItem);
					}
				}
			}
		}
	}

	private static class ListDialogProvider extends DialogProvider {
		public static class Factory extends DialogProvider.Factory {
			private final HashSet<String> postNumbers;

			public Factory(Collection<String> postNumbers) {
				this.postNumbers = new HashSet<>(postNumbers);
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new ListDialogProvider(configurationSet.copy(false, true, null), postNumbers);
			}
		}

		private final HashSet<String> postNumbers;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private ListDialogProvider(UiManager.ConfigurationSet configurationSet, HashSet<String> postNumbers) {
			super(configurationSet);
			this.postNumbers = postNumbers;
			onRequestUpdate();
		}

		@NonNull
		@Override
		public Iterator<PostItem> iterator() {
			return postItems.iterator();
		}

		@Override
		public void onRequestUpdate() {
			super.onRequestUpdate();
			postItems.clear();
			for (PostItem postItem : configurationSet.postsProvider) {
				if (postNumbers.contains(postItem.getPostNumber())) {
					postItems.add(postItem);
				}
			}
		}
	}

	private static class AsyncDialogProvider extends DialogProvider implements ReadSinglePostTask.Callback {
		public static class Factory extends DialogProvider.Factory {
			private final String chanName;
			private final String boardName;
			private final String threadNumber;
			private final String postNumber;

			private PostItem postItem;

			public Factory(String chanName, String boardName, String threadNumber, String postNumber) {
				this.chanName = chanName;
				this.boardName = boardName;
				this.threadNumber = threadNumber;
				this.postNumber = postNumber;
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				configurationSet = new UiManager.ConfigurationSet(null, null, new HidePerformer(),
						new GalleryItem.GallerySet(false), configurationSet.stackInstance, null, null,
						false, true, false, false, false, null);
				return new AsyncDialogProvider(uiManager, configurationSet, this,
						chanName, boardName, threadNumber, postNumber);
			}
		}

		private final UiManager uiManager;
		private final Factory factory;
		private final String chanName;
		private final String boardName;
		private final String threadNumber;
		private final String postNumber;

		private ReadSinglePostTask readTask;

		private AsyncDialogProvider(UiManager uiManager, UiManager.ConfigurationSet configurationSet, Factory factory,
				String chanName, String boardName, String threadNumber, String postNumber) {
			super(configurationSet);
			this.uiManager = uiManager;
			this.factory = factory;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
			if (factory.postItem != null) {
				onReadSinglePostSuccess(factory.postItem);
			} else {
				switchState(State.LOADING, null);
				if (StringUtils.isEmpty(postNumber)) {
					postNumber = threadNumber;
				}
				readTask = new ReadSinglePostTask(this, chanName, boardName, postNumber);
				readTask.executeOnExecutor(ReadSinglePostTask.THREAD_POOL_EXECUTOR);
			}
		}

		@NonNull
		@Override
		public Iterator<PostItem> iterator() {
			List<PostItem> list;
			if (factory.postItem != null) {
				list = Collections.singletonList(factory.postItem);
			} else {
				list = Collections.emptyList();
			}
			return list.iterator();
		}

		@Override
		public void onRequestUpdateDemandSet(UiManager.DemandSet demandSet, int index) {
			demandSet.showOpenThreadButton = true;
		}

		@Override
		public void onCancel() {
			super.onCancel();
			if (readTask != null) {
				readTask.cancel();
				readTask = null;
			}
		}

		@Override
		public void onReadSinglePostSuccess(PostItem postItem) {
			factory.postItem = postItem;
			List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
			if (attachmentItems != null) {
				if (postItem.getParentPostNumber() == null) {
					configurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
				}
				configurationSet.gallerySet.add(attachmentItems);
			}
			switchState(State.LIST, null);
		}

		@Override
		public void onReadSinglePostFail(final ErrorItem errorItem) {
			switchState(State.ERROR, () -> {
				Context context = uiManager.getContext();
				ClickableToast.show(context, errorItem.toString(),
						context.getString(R.string.action_open_thread), () -> uiManager.navigator()
						.navigatePosts(chanName, boardName, threadNumber, postNumber, null, 0), false);
			});
		}

		@Override
		public void onPostItemMessage(PostItem postItem, UiManager.Message message) {
			if (factory.postItem == postItem) {
				switch (message) {
					case PERFORM_SWITCH_HIDE: {
						if (postItem.isHiddenUnchecked()) {
							postItem.setHidden(false);
							uiManager.sendPostItemMessage(postItem, UiManager.Message.POST_INVALIDATE_ALL_VIEWS);
						}
						break;
					}
				}
			}
		}
	}

	public void notifyDataSetChangedToAll(StackInstance stackInstance) {
		for (View view : stackInstance.dialogStack.getVisibleViews()) {
			DialogHolder holder = (DialogHolder) view.getTag();
			holder.notifyDataSetChanged();
		}
	}

	public void updateAdapters(StackInstance stackInstance) {
		for (View view : stackInstance.dialogStack.getVisibleViews()) {
			DialogHolder holder = (DialogHolder) view.getTag();
			holder.requestUpdate();
		}
	}

	public void closeDialogs(StackInstance stackInstance) {
		if (stackInstance.attachmentDialog != null) {
			stackInstance.attachmentDialog.second.dismiss();
			stackInstance.attachmentDialog = null;
		}
		stackInstance.dialogStack.clear();
	}

	// Call this method after creating any windows within activity (such as Dialogs)!
	public void notifySwitchBackground(StackInstance stackInstance) {
		stackInstance.dialogStack.switchBackground(true);
	}

	public void displaySingle(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		display(configurationSet, new SingleDialogProvider.Factory(postItem));
	}

	public void displayThread(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		display(configurationSet, new ThreadDialogProvider.Factory(postItem));
	}

	public void displayReplies(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		display(configurationSet, new RepliesDialogProvider.Factory(postItem));
	}

	public void displayList(UiManager.ConfigurationSet configurationSet, Collection<String> postNumbers) {
		display(configurationSet, new ListDialogProvider.Factory(postNumbers));
	}

	public void displayReplyAsync(UiManager.ConfigurationSet configurationSet,
			String chanName, String boardName, String threadNumber, String postNumber) {
		display(configurationSet, new AsyncDialogProvider.Factory(chanName, boardName, threadNumber, postNumber));
	}

	private void display(UiManager.ConfigurationSet configurationSet, DialogProvider.Factory factory) {
		DialogProvider provider = factory.create(uiManager, configurationSet);
		configurationSet.stackInstance.dialogStack.push(new DialogFactory(provider, factory));
	}

	public void restoreState(UiManager.ConfigurationSet configurationSet, StackInstance.State state) {
		if (!state.factories.isEmpty()) {
			ArrayList<DialogFactory> dialogFactories = new ArrayList<>();
			for (DialogProvider.Factory factory : state.factories) {
				DialogProvider provider = factory.create(uiManager, configurationSet);
				dialogFactories.add(new DialogFactory(provider, factory));
				configurationSet = provider.configurationSet;
			}
			configurationSet.stackInstance.dialogStack.addAll(dialogFactories);
		}
		if (state.attachmentDialog != null) {
			showAttachmentsGrid(configurationSet.stackInstance, state.attachmentDialog.attachmentItems,
					state.attachmentDialog.startImageIndex, state.attachmentDialog.navigatePostMode,
					state.attachmentDialog.gallerySet);
		}
	}

	private static class DialogPostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private enum ViewType {POST, POST_HIDDEN}

		private static final String PAYLOAD_INVALIDATE_COMMENT = "invalidateComment";

		private final UiManager uiManager;
		private final DialogProvider dialogProvider;
		private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
		private final RecyclerView.AdapterDataObserver updateObserver;
		private final CommentTextView.RecyclerKeeper recyclerKeeper;

		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public final HashSet<String> postNumbers = new HashSet<>();

		public DialogPostsAdapter(UiManager uiManager, DialogProvider dialogProvider, RecyclerView recyclerView) {
			this.uiManager = uiManager;
			this.dialogProvider = dialogProvider;
			updateObserver = new RecyclerView.AdapterDataObserver() {
				@Override
				public void onChanged() {
					postItems.clear();
					postNumbers.clear();
					for (PostItem postItem : dialogProvider) {
						postItems.add(postItem);
						postNumbers.add(postItem.getPostNumber());
					}
				}
			};
			recyclerKeeper = new CommentTextView.RecyclerKeeper(recyclerView);
			super.registerAdapterDataObserver(updateObserver);
			super.registerAdapterDataObserver(recyclerKeeper);
			updateObserver.onChanged();
		}

		@Override
		public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
			super.registerAdapterDataObserver(observer);

			// Move observer to the end
			super.unregisterAdapterDataObserver(updateObserver);
			super.registerAdapterDataObserver(updateObserver);
			// Move observer to the end
			super.unregisterAdapterDataObserver(recyclerKeeper);
			super.registerAdapterDataObserver(recyclerKeeper);
		}

		@Override
		public int getItemCount() {
			return postItems.size();
		}

		@Override
		public int getItemViewType(int position) {
			return (getItem(position).isHidden(dialogProvider.configurationSet.hidePerformer)
					? ViewType.POST_HIDDEN : ViewType.POST).ordinal();
		}

		private PostItem getItem(int position) {
			return postItems.get(position);
		}

		private RecyclerView.ViewHolder configureView(RecyclerView.ViewHolder holder) {
			holder.itemView.setOnClickListener(v -> onItemClick(v, getItem(holder.getAdapterPosition())));
			holder.itemView.setOnLongClickListener(v -> onItemLongClick(getItem(holder.getAdapterPosition())));
			return holder;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (ViewType.values()[viewType]) {
				case POST: {
					return configureView(uiManager.view().createPostView(parent,
							dialogProvider.configurationSet));
				}
				case POST_HIDDEN: {
					return configureView(uiManager.view().createPostHiddenView(parent,
							dialogProvider.configurationSet));
				}
				default: {
					throw new IllegalStateException();
				}
			}
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			onBindViewHolder(holder, position, Collections.emptyList());
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
				@NonNull List<Object> payloads) {
			PostItem postItem = getItem(position);
			switch (ViewType.values()[holder.getItemViewType()]) {
				case POST: {
					if (payloads.contains(PAYLOAD_INVALIDATE_COMMENT)) {
						uiManager.view().bindPostViewInvalidateComment(holder);
					} else {
						dialogProvider.onRequestUpdateDemandSet(demandSet, position);
						uiManager.view().bindPostView(holder, postItem, demandSet);
					}
					break;
				}
				case POST_HIDDEN: {
					uiManager.view().bindPostHiddenView(holder, postItem);
					break;
				}
			}
		}

		public void invalidateComment(int position) {
			notifyItemChanged(position, PAYLOAD_INVALIDATE_COMMENT);
		}

		private void onItemClick(View view, PostItem postItem) {
			uiManager.interaction().handlePostClick(view, postItem, postItems);
		}

		private boolean onItemLongClick(PostItem postItem) {
			UiManager.ConfigurationSet configurationSet = dialogProvider.configurationSet;
			return uiManager.interaction().handlePostContextMenu(postItem,
					configurationSet.stackInstance, configurationSet.replyable,
					configurationSet.allowMyMarkEdit, configurationSet.allowHiding, configurationSet.allowGoToPost);
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void showAttachmentsGrid(StackInstance stackInstance, List<AttachmentItem> attachmentItems,
			int startImageIndex, GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.GallerySet gallerySet) {
		if (stackInstance.attachmentDialog != null) {
			stackInstance.attachmentDialog.second.dismiss();
			stackInstance.attachmentDialog = null;
		}
		Context context = uiManager.getContext();
		Context styledContext = new ContextThemeWrapper(context, R.style.Theme_Gallery);
		final Dialog dialog = new Dialog(styledContext);
		Pair<StackInstance.AttachmentDialog, Dialog> attachmentDialog = new Pair<>(new StackInstance
				.AttachmentDialog(attachmentItems, startImageIndex, navigatePostMode, gallerySet), dialog);
		stackInstance.attachmentDialog = attachmentDialog;
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setOnKeyListener((d, keyCode, event) -> {
			if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
				closeDialogs(stackInstance);
				return true;
			}
			return false;
		});
		dialog.setOnDismissListener(dialogInterface -> {
			if (stackInstance.attachmentDialog == attachmentDialog) {
				stackInstance.attachmentDialog = null;
			}
		});
		View.OnClickListener closeListener = v -> dialog.cancel();
		LayoutInflater inflater = LayoutInflater.from(styledContext);
		FrameLayout rootView = new FrameLayout(styledContext);
		rootView.setOnClickListener(closeListener);
		rootView.setFitsSystemWindows(true);
		ScrollView scrollView = new ScrollView(styledContext);
		scrollView.setVerticalScrollBarEnabled(false);
		rootView.addView(scrollView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		LinearLayout container = new LinearLayout(styledContext);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setMotionEventSplittingEnabled(false);
		container.setOnClickListener(closeListener);
		scrollView.addView(container, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		View.OnClickListener clickListener = v -> {
			int index = (int) v.getTag();
			int imageIndex = startImageIndex;
			for (int i = 0; i < index; i++) {
				if (attachmentItems.get(i).isShowInGallery()) {
					imageIndex++;
				}
			}
			openAttachment(v, attachmentItems, index, imageIndex, navigatePostMode, gallerySet);
		};
		Configuration configuration = context.getResources().getConfiguration();
		boolean tablet = ResourceUtils.isTablet(configuration);
		boolean tabletLarge = ResourceUtils.isTabletLarge(configuration);
		float density = ResourceUtils.obtainDensity(context);
		int total = 0;
		LinearLayout linearLayout = null;
		int padding = (int) (8f * density);
		int size = (int) ((tabletLarge ? 180f : tablet ? 160f : 120f) * density);
		int columns = tablet ? 3 : 2;
		for (int i = 0; i < attachmentItems.size(); i++) {
			int column = total++ % columns;
			if (column == 0) {
				boolean first = linearLayout == null;
				linearLayout = new LinearLayout(styledContext);
				linearLayout.setOrientation(LinearLayout.HORIZONTAL);
				linearLayout.setMotionEventSplittingEnabled(false);
				container.addView(linearLayout, ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT);
				if (first) {
					linearLayout.setPadding(0, padding, 0, padding);
				} else {
					linearLayout.setPadding(0, 0, 0, padding);
				}
			}
			AttachmentItem attachmentItem = attachmentItems.get(i);
			@SuppressLint("InflateParams")
			View view = inflater.inflate(R.layout.list_item_attachment, null);
			ViewUtils.makeRoundedCorners(view, (int) (2f * density + 0.5f), true);
			AttachmentView attachmentView = view.findViewById(R.id.thumbnail);
			TextView textView = view.findViewById(R.id.attachment_info);
			textView.setBackgroundColor(0xcc222222);
			attachmentItem.configureAndLoad(attachmentView, false, true);
			textView.setText(attachmentItem.getDescription(AttachmentItem.FormatMode.TWO_LINES));
			View clickView = view.findViewById(R.id.attachment_click);
			clickView.setOnClickListener(clickListener);
			clickView.setOnLongClickListener(v -> {
				uiManager.interaction().showThumbnailLongClickDialog(stackInstance,
						attachmentItem, attachmentView, false, gallerySet.getThreadTitle());
				return true;
			});
			clickView.setTag(i);
			int totalSize = size;
			if (column == 0) {
				view.setPadding(padding, 0, padding, 0);
				totalSize += 2 * padding;
			} else {
				view.setPadding(0, 0, padding, 0);
				totalSize += padding;
			}
			linearLayout.addView(view, totalSize, size);
		}
		dialog.setContentView(rootView);
		Window window = dialog.getWindow();
		WindowManager.LayoutParams layoutParams = window.getAttributes();
		layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
		layoutParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
		if (C.API_LOLLIPOP) {
			layoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
		}
		View decorView = window.getDecorView();
		decorView.setBackground(null);
		decorView.setPadding(0, 0, 0, 0);
		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		dialog.show();
		notifySwitchBackground(stackInstance);
	}

	public void openAttachmentOrDialog(StackInstance stackInstance, View imageView,
			List<AttachmentItem> attachmentItems, int imageIndex,
			GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.GallerySet gallerySet) {
		if (attachmentItems.size() > 1) {
			showAttachmentsGrid(stackInstance, attachmentItems, imageIndex, navigatePostMode, gallerySet);
		} else {
			openAttachment(imageView, attachmentItems, 0, imageIndex, navigatePostMode, gallerySet);
		}
	}

	public void openAttachment(View imageView, List<AttachmentItem> attachmentItems, int index,
			int imageIndex, GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.GallerySet gallerySet) {
		Context context = uiManager.getContext();
		AttachmentItem attachmentItem = attachmentItems.get(index);
		boolean canDownload = attachmentItem.canDownloadToStorage();
		String chanName = attachmentItem.getChanName();
		Uri uri = attachmentItem.getFileUri();
		int type = attachmentItem.getType();
		if (canDownload && type == AttachmentItem.TYPE_AUDIO) {
			AudioPlayerService.start(context, chanName, uri, attachmentItem.getFileName());
		} else if (canDownload && (type == AttachmentItem.TYPE_IMAGE || type == AttachmentItem.TYPE_VIDEO &&
				NavigationUtils.isOpenableVideoExtension(attachmentItem.getExtension()))) {
			uiManager.navigator().navigateGallery(chanName, gallerySet, imageIndex,
					imageView, navigatePostMode, false);
		} else {
			NavigationUtils.handleUri(context, chanName, uri, NavigationUtils.BrowserType.EXTERNAL);
		}
	}

	public static class IconData {
		private final String title;
		private final int attrId;
		private final Uri uri;

		public IconData(String title, int attrId) {
			this.title = title;
			this.attrId = attrId;
			this.uri = null;
		}

		public IconData(String title, Uri uri) {
			this.title = title;
			this.attrId = 0;
			this.uri = uri;
		}
	}

	public void showPostDescriptionDialog(StackInstance stackInstance,
			Collection<IconData> icons, String chanName, final String emailToCopy) {
		Context context = uiManager.getContext();
		float density = ResourceUtils.obtainDensity(context);
		ImageLoader imageLoader = ImageLoader.getInstance();
		LinearLayout container = new LinearLayout(context);
		container.setOrientation(LinearLayout.VERTICAL);
		if (C.API_LOLLIPOP) {
			container.setPadding(0, (int) (12f * density), 0, 0);
		}
		for (IconData icon : icons) {
			LinearLayout linearLayout = new LinearLayout(context);
			container.addView(linearLayout, LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			linearLayout.setOrientation(LinearLayout.HORIZONTAL);
			linearLayout.setGravity(Gravity.CENTER_VERTICAL);
			linearLayout.setMinimumHeight((int) (40f * density));
			linearLayout.setPadding((int) (18f * density), 0, (int) (8f * density), 0); // 18f = 16f + 2f
			ImageView imageView = new ImageView(context);
			linearLayout.addView(imageView, (int) (20f * density), (int) (20f * density));
			if (icon.uri != null) {
				imageLoader.loadImage(chanName, icon.uri, false, imageView);
			} else {
				imageView.setImageResource(ResourceUtils.getResourceId(context, icon.attrId, 0));
			}
			TextView textView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			linearLayout.addView(textView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
			textView.setSingleLine(true);
			textView.setText(icon.title);
			if (C.API_LOLLIPOP) {
				textView.setPadding((int) (26f * density), 0, 0, 0); // 26f = 24f + 2f
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
				textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			} else {
				textView.setPadding((int) (10f * density), 0, 0, 0); // 20f = 8f + 2f
				textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
				textView.setAllCaps(true);
			}
		}
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(context).setPositiveButton(android.R.string.ok, null);
		if (!StringUtils.isEmpty(emailToCopy)) {
			alertDialog.setNeutralButton(R.string.action_copy_email,
					(dialog, which) -> StringUtils.copyToClipboard(uiManager.getContext(), emailToCopy));
		}
		AlertDialog dialog = alertDialog.setView(container).show();
		uiManager.getConfigurationLock().lockConfiguration(dialog);
		notifySwitchBackground(stackInstance);
	}

	public void performSendDeletePosts(String chanName, String boardName, String threadNumber,
			ArrayList<String> postNumbers) {
		ChanConfiguration.Deleting deleting = ChanConfiguration.get(chanName).safe().obtainDeleting(boardName);
		if (deleting == null) {
			return;
		}
		Context context = uiManager.getContext();
		ArrayList<Pair<String, String>> options = null;
		if (deleting.optionFilesOnly) {
			options = new ArrayList<>();
			options.add(new Pair<>(SendMultifunctionalTask.OPTION_FILES_ONLY,
					context.getString(R.string.action_files_only)));
		}
		SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.DELETE, chanName, boardName, threadNumber, null, options, deleting.password);
		state.postNumbers = postNumbers;
		showPerformSendDialog(state, null, Preferences.getPassword(chanName), null, null, true);
	}

	public void performSendReportPosts(String chanName, String boardName, String threadNumber,
			ArrayList<String> postNumbers) {
		ChanConfiguration.Reporting reporting = ChanConfiguration.get(chanName).safe().obtainReporting(boardName);
		if (reporting == null) {
			return;
		}
		SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.REPORT, chanName, boardName, threadNumber, reporting.types,
				reporting.options, reporting.comment);
		state.postNumbers = postNumbers;
		showPerformSendDialog(state, null, null, null, null, true);
	}

	public void performSendArchiveThread(String chanName, String boardName, String threadNumber, String threadTitle,
			final Posts posts) {
		Context context = uiManager.getContext();
		final boolean canArchiveLocal = !ChanConfiguration.get(chanName)
				.getOption(ChanConfiguration.OPTION_HIDDEN_DISALLOW_ARCHIVATION);
		final List<String> archiveChanNames = ChanManager.getInstance().getArchiveChanNames(chanName);
		final SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.ARCHIVE, chanName, boardName, threadNumber, null, null, false);
		state.archiveThreadTitle = threadTitle;
		if (canArchiveLocal && archiveChanNames.size() > 0 || archiveChanNames.size() > 1) {
			String[] items = new String[archiveChanNames.size() + (canArchiveLocal ? 1 : 0)];
			if (canArchiveLocal) {
				items[0] = context.getString(R.string.text_local_archive);
			}
			for (int i = 0; i < archiveChanNames.size(); i++) {
				items[canArchiveLocal ? i + 1 : i] = ChanConfiguration.get(archiveChanNames.get(i)).getTitle();
			}
			AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(R.string.action_archive_add)
					.setItems(items, (d, which) -> performSendArchiveThreadInternal(state, canArchiveLocal
							? which == 0 ? null : archiveChanNames.get(which - 1) : archiveChanNames.get(which), posts))
					.show();
			uiManager.getConfigurationLock().lockConfiguration(dialog);
		} else if (canArchiveLocal) {
			performSendArchiveThreadInternal(state, canArchiveLocal ? null : archiveChanNames.get(0), posts);
		}
	}

	public static final String OPTION_THUMBNAILS = "thumbnails";
	public static final String OPTION_FILES = "files";

	private void performSendArchiveThreadInternal(SendMultifunctionalTask.State state, String archiveChanName,
			Posts posts) {
		Context context = uiManager.getContext();
		ChanConfiguration.Archivation archivation;
		if (archiveChanName == null) {
			archivation = new ChanConfiguration.Archivation();
			archivation.options.add(new Pair<>(OPTION_THUMBNAILS, context.getString(R.string.text_save_thumbnails)));
			archivation.options.add(new Pair<>(OPTION_FILES, context.getString(R.string.text_save_files)));
		} else {
			archivation = ChanConfiguration.get(archiveChanName).safe().obtainArchivation();
		}
		if (archivation == null) {
			return;
		}
		state.archiveChanName = archiveChanName;
		state.options = archivation.options;
		showPerformSendDialog(state, null, null, null, posts, true);
	}

	private CancellableTask<?, ?, ?> sendTask;

	private void showPerformSendDialog(final SendMultifunctionalTask.State state,
			String defaultType, String defaultText, ArrayList<String> defaultOptions,
			final Posts posts, boolean firstTime) {
		Context context = uiManager.getContext();
		final RadioGroup radioGroup;
		if (state.types != null && state.types.size() > 0) {
			radioGroup = new RadioGroup(context);
			radioGroup.setOrientation(RadioGroup.VERTICAL);
			int check = 0;
			for (Pair<String, String> pair : state.types) {
				RadioButton button = new RadioButton(context);
				button.setText(pair.second);
				button.setId(radioGroup.getChildCount());
				if (StringUtils.equals(pair.first, defaultType)) {
					check = button.getId();
				}
				radioGroup.addView(button);
			}
			radioGroup.check(check);
		} else {
			radioGroup = null;
		}

		final EditText editText;
		if (state.commentField) {
			editText = new SafePasteEditText(context);
			editText.setSingleLine(true);
			editText.setText(defaultText);
			if (defaultText != null) {
				editText.setSelection(defaultText.length());
			}
			if (state.operation == SendMultifunctionalTask.Operation.DELETE) {
				editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
			}
		} else {
			editText = null;
		}

		final LinearLayout checkBoxGroup;
		if (state.options != null && state.options.size() > 0) {
			checkBoxGroup = new LinearLayout(context);
			checkBoxGroup.setOrientation(RadioGroup.VERTICAL);
			for (Pair<String, String> option : state.options) {
				CheckBox checkBox = new CheckBox(context);
				checkBox.setText(option.second);
				checkBox.setTag(option.first);
				if (defaultOptions != null && defaultOptions.contains(option.first)) {
					checkBox.setChecked(true);
				}
				checkBoxGroup.addView(checkBox);
			}
		} else {
			checkBoxGroup = null;
		}

		float density = ResourceUtils.obtainDensity(context);
		LinearLayout linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.VERTICAL);
		if (radioGroup != null) {
			linearLayout.addView(radioGroup);
		}
		if (editText != null) {
			linearLayout.addView(editText);
		}
		if (checkBoxGroup != null) {
			linearLayout.addView(checkBoxGroup);
		}
		if (radioGroup != null && editText != null) {
			((LinearLayout.LayoutParams) editText.getLayoutParams()).topMargin = (int) (8f * density);
		}
		if ((radioGroup != null || editText != null) && checkBoxGroup != null) {
			((LinearLayout.LayoutParams) checkBoxGroup.getLayoutParams()).topMargin = (int) (8f * density);
		}
		int padding = context.getResources().getDimensionPixelSize(R.dimen.dialog_padding_view);
		linearLayout.setPadding(padding, padding, padding, padding);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		if (linearLayout.getChildCount() > 0) {
			int resId = 0;
			switch (state.operation) {
				case DELETE: {
					resId = R.string.action_delete;
					break;
				}
				case REPORT: {
					resId = R.string.action_report;
					break;
				}
				case ARCHIVE: {
					resId = R.string.action_archive_add;
					break;
				}
			}
			builder.setTitle(resId);
			builder.setView(linearLayout);
		} else {
			if (!firstTime){
				return;
			}
			int resId = 0;
			switch (state.operation) {
				case DELETE:{
					resId = R.string.message_confirm_deletion;
					break;
				}
				case REPORT:{
					resId = R.string.message_confirm_reporting;
					break;
				}
				case ARCHIVE:{
					resId = R.string.message_confirm_archivation;
					break;
				}
			}
			builder.setMessage(resId);
		}

		AlertDialog dialog = builder.setPositiveButton(android.R.string.ok, (d, which) -> {
			String type = radioGroup != null ? state.types.get(radioGroup.getCheckedRadioButtonId()).first : null;
			String text = editText != null ? editText.getText().toString() : null;
			ArrayList<String> options = null;
			if (checkBoxGroup != null) {
				options = new ArrayList<>();
				for (int i = 0; i < checkBoxGroup.getChildCount(); i++) {
					CheckBox checkBox = (CheckBox) checkBoxGroup.getChildAt(i);
					if (checkBox.isChecked()) {
						options.add((String) checkBox.getTag());
					}
				}
			}
			if (state.operation == SendMultifunctionalTask.Operation.ARCHIVE && state.archiveChanName == null) {
				if (posts == null || posts.length() == 0) {
					ToastUtils.show(uiManager.getContext(), R.string.message_cache_unavailable);
				} else {
					SendLocalArchiveTask task = new SendLocalArchiveTask(state.chanName, state.boardName,
							state.threadNumber, posts, options.contains(OPTION_THUMBNAILS),
							options.contains(OPTION_FILES), new PerformSendCallback(posts.length()));
					task.executeOnExecutor(SendMultifunctionalTask.THREAD_POOL_EXECUTOR);
					sendTask = task;
				}
			} else {
				SendMultifunctionalTask task = new SendMultifunctionalTask(state, type, text, options,
						new PerformSendCallback(-1));
				task.executeOnExecutor(SendMultifunctionalTask.THREAD_POOL_EXECUTOR);
				sendTask = task;
			}
		}).setNegativeButton(android.R.string.cancel, null).show();
		uiManager.getConfigurationLock().lockConfiguration(dialog);
	}

	private class PerformSendCallback implements SendMultifunctionalTask.Callback, SendLocalArchiveTask.Callback,
			DialogInterface.OnCancelListener {
		private final ProgressDialog dialog;

		public PerformSendCallback(int localArchiveMax) {
			Context context = uiManager.getContext();
			if (localArchiveMax >= 0) {
				dialog = new ProgressDialog(context, "%d / %d");
				dialog.setMessage(context.getString(R.string.message_processing_data));
				dialog.setMax(localArchiveMax);
			} else {
				dialog = new ProgressDialog(context, null);
				dialog.setMessage(context.getString(R.string.message_sending));
			}
			dialog.setCanceledOnTouchOutside(false);
			dialog.setOnCancelListener(this);
			uiManager.getConfigurationLock().lockConfiguration(dialog);
			dialog.show();
		}

		private void completeTask() {
			sendTask = null;
			ViewUtils.dismissDialogQuietly(dialog);
		}

		@Override
		public void onCancel(DialogInterface dialog) {
			cancelSendTasks();
			completeTask();
		}

		@Override
		public void onSendSuccess(final SendMultifunctionalTask.State state, final String archiveBoardName,
				final String archiveThreadNumber) {
			completeTask();
			Context context = uiManager.getContext();
			switch (state.operation) {
				case DELETE: {
					ToastUtils.show(context, R.string.message_request_sent);
					break;
				}
				case REPORT: {
					ToastUtils.show(context, R.string.message_report_sent);
					break;
				}
				case ARCHIVE: {
					if (archiveThreadNumber != null) {
						FavoritesStorage.getInstance().add(state.archiveChanName, archiveBoardName,
								archiveThreadNumber, state.archiveThreadTitle, 0);
						ClickableToast.show(context, context.getString(R.string.message_completed),
								context.getString(R.string.action_open_thread),
								() -> uiManager.navigator().navigatePosts(state.archiveChanName, archiveBoardName,
								archiveThreadNumber, null, state.archiveThreadTitle, 0), false);
					} else {
						ToastUtils.show(context, R.string.message_completed);
					}
					break;
				}
			}
		}

		@Override
		public void onSendFail(SendMultifunctionalTask.State state, String type, String text,
				ArrayList<String> options, ErrorItem errorItem) {
			completeTask();
			ToastUtils.show(uiManager.getContext(), errorItem);
			showPerformSendDialog(state, type, text, options, null, false);
		}

		@Override
		public DownloadService.Binder getDownloadBinder() {
			DownloadService.Binder[] result = {null};
			uiManager.download(binder -> result[0] = binder);
			return result[0];
		}

		@Override
		public void onLocalArchivationProgressUpdate(int handledPostsCount) {
			dialog.setValue(handledPostsCount);
		}

		@Override
		public void onLocalArchivationComplete(boolean success) {
			completeTask();
			if (!success) {
				ToastUtils.show(uiManager.getContext(), R.string.message_unknown_error);
			}
		}
	}

	private void cancelSendTasks() {
		if (sendTask != null) {
			sendTask.cancel();
			sendTask = null;
		}
	}

	void onFinish() {
		cancelSendTasks();
	}
}
