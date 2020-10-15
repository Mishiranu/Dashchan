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
import chan.util.CommonUtils;
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
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
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
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DialogUnit {
	private final UiManager uiManager;

	public static class StackInstance {
		private static class AttachmentDialog {
			public final List<AttachmentItem> attachmentItems;
			public final int startImageIndex;
			public final GalleryOverlay.NavigatePostMode navigatePostMode;
			public final GalleryItem.Set gallerySet;

			private AttachmentDialog(List<AttachmentItem> attachmentItems, int startImageIndex,
					GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
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
			factory.listPosition = ListPosition.obtain(holder.recyclerView, null);
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
						for (PostNumber referenceFrom : postItem.getReferencesFrom()) {
							if (adapter.postNumbers.contains(referenceFrom)) {
								notify = true;
								break;
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
		boolean onStateChanged(State state);
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
						intermediate, UiManager.PostStateProvider.DEFAULT, new GalleryItem.Set(false),
						configurationSet.stackInstance, intermediate, false, true, false, false, false, null);
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
			public PostItem findPostItem(PostNumber postNumber) {
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
			List<PostItem> childPostItems = postItem.getThreadPosts();
			if (!childPostItems.isEmpty()) {
				for (int i = 0; i < childPostItems.size(); i++) {
					PostItem childPostItem = childPostItems.get(i);
					postItems.add(childPostItem);
					for (PostNumber postNumber : childPostItem.getReferencesTo()) {
						for (int j = 0; j < i + 1; j++) {
							PostItem foundPostItem = postItems.get(j);
							if (postNumber.equals(foundPostItem.getPostNumber())) {
								foundPostItem.addReferenceFrom(childPostItem.getPostNumber());
							}
						}
					}
				}
			}
			configurationSet.gallerySet.setThreadTitle(this.postItems.get(0).getSubjectOrComment());
			for (PostItem childPostItem : postItems) {
				configurationSet.gallerySet.put(childPostItem.getPostNumber(), childPostItem.getAttachmentItems());
			}
		}

		@Override
		public PostItem findPostItem(PostNumber postNumber) {
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
					&& CommonUtils.equals(boardName, locator.safe(false).getBoardName(uri))
					&& CommonUtils.equals(threadNumber, locator.safe(false).getThreadNumber(uri))) {
				PostNumber postNumber = locator.safe(false).getPostNumber(uri);
				if (postNumber != null) {
					for (PostItem postItem : postItems) {
						if (postNumber.equals(postItem.getPostNumber())) {
							uiManager.dialog().displaySingle(configurationSet, postItem);
							return;
						}
					}
				} else {
					uiManager.dialog().displaySingle(configurationSet, originalPostItem);
					return;
				}
			}
			uiManager.interaction().handleLinkClick(configurationSet, chanName, uri, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {
			uiManager.interaction().handleLinkLongClick(uri);
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
			Set<PostNumber> referencesFrom = postItem.getReferencesFrom();
			if (!referencesFrom.isEmpty()) {
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
			private final HashSet<PostNumber> postNumbers;

			public Factory(Collection<PostNumber> postNumbers) {
				this.postNumbers = new HashSet<>(postNumbers);
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new ListDialogProvider(configurationSet.copy(false, true, null), postNumbers);
			}
		}

		private final HashSet<PostNumber> postNumbers;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private ListDialogProvider(UiManager.ConfigurationSet configurationSet, HashSet<PostNumber> postNumbers) {
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
			private final PostNumber postNumber;

			private PostItem postItem;

			public Factory(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
				this.chanName = chanName;
				this.boardName = boardName;
				this.threadNumber = threadNumber;
				this.postNumber = postNumber;
			}

			@Override
			public DialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				configurationSet = new UiManager.ConfigurationSet(null, null, UiManager.PostStateProvider.DEFAULT,
						new GalleryItem.Set(false), configurationSet.stackInstance, null,
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
		private final PostNumber postNumber;

		private ReadSinglePostTask readTask;

		private AsyncDialogProvider(UiManager uiManager, UiManager.ConfigurationSet configurationSet, Factory factory,
				String chanName, String boardName, String threadNumber, PostNumber postNumber) {
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
				readTask = new ReadSinglePostTask(this, chanName, boardName, threadNumber, postNumber);
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
				if (postItem.isOriginalPost()) {
					configurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
				}
				configurationSet.gallerySet.put(postItem.getPostNumber(), attachmentItems);
			}
			switchState(State.LIST, null);
		}

		@Override
		public void onReadSinglePostFail(final ErrorItem errorItem) {
			switchState(State.ERROR, () -> {
				Context context = uiManager.getContext();
				ClickableToast.show(context, errorItem.toString(),
						context.getString(R.string.open_thread), false, () -> uiManager.navigator()
						.navigatePosts(chanName, boardName, threadNumber, postNumber, null));
			});
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

	public void displaySingle(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		display(configurationSet, new SingleDialogProvider.Factory(postItem));
	}

	public void displayThread(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		display(configurationSet, new ThreadDialogProvider.Factory(postItem));
	}

	public void displayReplies(UiManager.ConfigurationSet configurationSet, PostItem postItem) {
		display(configurationSet, new RepliesDialogProvider.Factory(postItem));
	}

	public void displayList(UiManager.ConfigurationSet configurationSet, Collection<PostNumber> postNumbers) {
		display(configurationSet, new ListDialogProvider.Factory(postNumbers));
	}

	public void displayReplyAsync(UiManager.ConfigurationSet configurationSet,
			String chanName, String boardName, String threadNumber, PostNumber postNumber) {
		display(configurationSet, new AsyncDialogProvider.Factory(chanName, boardName, threadNumber, postNumber));
	}

	private void display(UiManager.ConfigurationSet configurationSet, DialogProvider.Factory factory) {
		DialogProvider provider = factory.create(uiManager, configurationSet);
		configurationSet.stackInstance.dialogStack.push(new DialogFactory(provider, factory));
		uiManager.callback().onDialogStackOpen();
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
			uiManager.callback().onDialogStackOpen();
		}
		if (state.attachmentDialog != null) {
			showAttachmentsGrid(configurationSet.stackInstance, state.attachmentDialog.attachmentItems,
					state.attachmentDialog.startImageIndex, state.attachmentDialog.navigatePostMode,
					state.attachmentDialog.gallerySet);
		}
	}

	private static class DialogPostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
			implements ListViewUtils.ClickCallback<Void, RecyclerView.ViewHolder> {
		private enum ViewType {POST, POST_HIDDEN}

		private static final String PAYLOAD_INVALIDATE_COMMENT = "invalidateComment";

		private final UiManager uiManager;
		private final DialogProvider dialogProvider;
		private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
		private final RecyclerView.AdapterDataObserver updateObserver;
		private final CommentTextView.RecyclerKeeper recyclerKeeper;

		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public final HashSet<PostNumber> postNumbers = new HashSet<>();

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
			PostItem postItem = getItem(position);
			return (dialogProvider.configurationSet.postStateProvider.isHiddenResolve(postItem)
					? ViewType.POST_HIDDEN : ViewType.POST).ordinal();
		}

		private PostItem getItem(int position) {
			return postItems.get(position);
		}

		@Override
		public boolean onItemClick(RecyclerView.ViewHolder holder, int position, Void nothing, boolean longClick) {
			PostItem postItem = postItems.get(position);
			UiManager.ConfigurationSet configurationSet = dialogProvider.configurationSet;
			if (longClick) {
				boolean userPost = configurationSet.postStateProvider.isUserPost(postItem.getPostNumber());
				return uiManager.interaction().handlePostContextMenu(postItem, configurationSet.replyable, userPost,
						configurationSet.allowMyMarkEdit, configurationSet.allowHiding, configurationSet.allowGoToPost);
			} else {
				uiManager.interaction().handlePostClick(holder.itemView,
						configurationSet.postStateProvider, postItem, postItems);
				return true;
			}
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (ViewType.values()[viewType]) {
				case POST: {
					return ListViewUtils.bind(uiManager.view().createPostView(parent,
							dialogProvider.configurationSet), true, null, this);
				}
				case POST_HIDDEN: {
					return ListViewUtils.bind(uiManager.view().createPostHiddenView(parent,
							dialogProvider.configurationSet), true, null, this);
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
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void showAttachmentsGrid(StackInstance stackInstance, List<AttachmentItem> attachmentItems,
			int startImageIndex, GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
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
				uiManager.interaction().showThumbnailLongClickDialog(attachmentItem,
						attachmentView, false, gallerySet.getThreadTitle());
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
	}

	public void openAttachmentOrDialog(StackInstance stackInstance, View imageView,
			List<AttachmentItem> attachmentItems, int imageIndex,
			GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
		if (attachmentItems.size() > 1) {
			showAttachmentsGrid(stackInstance, attachmentItems, imageIndex, navigatePostMode, gallerySet);
		} else {
			openAttachment(imageView, attachmentItems, 0, imageIndex, navigatePostMode, gallerySet);
		}
	}

	public void openAttachment(View imageView, List<AttachmentItem> attachmentItems, int index,
			int imageIndex, GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
		Context context = uiManager.getContext();
		AttachmentItem attachmentItem = attachmentItems.get(index);
		boolean canDownload = attachmentItem.canDownloadToStorage();
		String chanName = attachmentItem.getChanName();
		Uri uri = attachmentItem.getFileUri();
		AttachmentItem.Type type = attachmentItem.getType();
		if (canDownload && type == AttachmentItem.Type.AUDIO) {
			AudioPlayerService.start(context, chanName, uri, attachmentItem.getFileName());
		} else if (canDownload && (type == AttachmentItem.Type.IMAGE || type == AttachmentItem.Type.VIDEO &&
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

	public void showPostDescriptionDialog(Collection<IconData> icons, String chanName, final String emailToCopy) {
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
				if (C.API_LOLLIPOP) {
					imageView.setImageTintList(ResourceUtils.getColorStateList(imageView.getContext(),
							android.R.attr.textColorSecondary));
				}
			}
			TextView textView = new TextView(context, null, android.R.attr.textAppearanceListItem);
			linearLayout.addView(textView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
			textView.setSingleLine(true);
			textView.setText(icon.title);
			if (C.API_LOLLIPOP) {
				textView.setPadding((int) (26f * density), 0, 0, 0); // 26f = 24f + 2f
				ViewUtils.setTextSizeScaled(textView, 14);
				textView.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			} else {
				textView.setPadding((int) (10f * density), 0, 0, 0); // 20f = 8f + 2f
				ViewUtils.setTextSizeScaled(textView, 16);
				textView.setAllCaps(true);
			}
		}
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(context).setPositiveButton(android.R.string.ok, null);
		if (!StringUtils.isEmpty(emailToCopy)) {
			alertDialog.setNeutralButton(R.string.copy_email,
					(dialog, which) -> StringUtils.copyToClipboard(uiManager.getContext(), emailToCopy));
		}
		AlertDialog dialog = alertDialog.setView(container).show();
		uiManager.getConfigurationLock().lockConfiguration(dialog);
	}

	public void performSendDeletePosts(String chanName, String boardName, String threadNumber,
			List<PostNumber> postNumbers) {
		ChanConfiguration.Deleting deleting = ChanConfiguration.get(chanName).safe().obtainDeleting(boardName);
		if (deleting == null) {
			return;
		}
		Context context = uiManager.getContext();
		ArrayList<Pair<String, String>> options = null;
		if (deleting.optionFilesOnly) {
			options = new ArrayList<>();
			options.add(new Pair<>(SendMultifunctionalTask.OPTION_FILES_ONLY,
					context.getString(R.string.files_only)));
		}
		SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.DELETE, chanName, boardName, threadNumber, null, options, deleting.password);
		state.postNumbers = postNumbers;
		showPerformSendDialog(state, null, Preferences.getPassword(chanName), null, null, true);
	}

	public void performSendReportPosts(String chanName, String boardName, String threadNumber,
			List<PostNumber> postNumbers) {
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

	public void performSendArchiveThread(String chanName, String boardName, String threadNumber,
			String threadTitle, Collection<Post> posts) {
		Context context = uiManager.getContext();
		boolean canArchiveLocal = !ChanConfiguration.get(chanName)
				.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
		List<String> archiveChanNames = ChanManager.getInstance().getArchiveChanNames(chanName);
		SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.ARCHIVE, chanName, boardName, threadNumber, null, null, false);
		state.archiveThreadTitle = threadTitle;
		if (canArchiveLocal && archiveChanNames.size() > 0 || archiveChanNames.size() > 1) {
			String[] items = new String[archiveChanNames.size() + (canArchiveLocal ? 1 : 0)];
			if (canArchiveLocal) {
				items[0] = context.getString(R.string.local_archive);
			}
			for (int i = 0; i < archiveChanNames.size(); i++) {
				items[canArchiveLocal ? i + 1 : i] = ChanConfiguration.get(archiveChanNames.get(i)).getTitle();
			}
			AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(R.string.archive__verb)
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
			Collection<Post> posts) {
		Context context = uiManager.getContext();
		ChanConfiguration.Archivation archivation;
		if (archiveChanName == null) {
			archivation = new ChanConfiguration.Archivation();
			archivation.options.add(new Pair<>(OPTION_THUMBNAILS, context.getString(R.string.save_thumbnails)));
			archivation.options.add(new Pair<>(OPTION_FILES, context.getString(R.string.save_files)));
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

	private void showPerformSendDialog(SendMultifunctionalTask.State state, String defaultType,
			String defaultText, List<String> defaultOptions, Collection<Post> posts, boolean firstTime) {
		Context context = uiManager.getContext();
		final RadioGroup radioGroup;
		if (state.types != null && state.types.size() > 0) {
			radioGroup = new RadioGroup(context);
			radioGroup.setOrientation(RadioGroup.VERTICAL);
			int check = 0;
			for (Pair<String, String> pair : state.types) {
				RadioButton button = new RadioButton(context);
				ThemeEngine.applyStyle(button);
				button.setText(pair.second);
				button.setId(radioGroup.getChildCount());
				if (CommonUtils.equals(pair.first, defaultType)) {
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
				ThemeEngine.applyStyle(checkBox);
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
			ScrollView scrollView = new ScrollView(context);
			scrollView.addView(linearLayout, ScrollView.LayoutParams.MATCH_PARENT,
					ScrollView.LayoutParams.WRAP_CONTENT);
			int resId = 0;
			switch (state.operation) {
				case DELETE: {
					resId = R.string.delete;
					break;
				}
				case REPORT: {
					resId = R.string.report;
					break;
				}
				case ARCHIVE: {
					resId = R.string.archive__verb;
					break;
				}
			}
			builder.setTitle(resId);
			builder.setView(scrollView);
		} else {
			if (!firstTime){
				return;
			}
			int resId = 0;
			switch (state.operation) {
				case DELETE: {
					resId = R.string.confirm_deleting__sentence;
					break;
				}
				case REPORT: {
					resId = R.string.confirm_reporting__sentence;
					break;
				}
				case ARCHIVE: {
					resId = R.string.confirm_archivation__sentence;
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
				if (posts.isEmpty()) {
					ToastUtils.show(uiManager.getContext(), R.string.cache_is_unavailable);
				} else {
					SendLocalArchiveTask task = new SendLocalArchiveTask(state.chanName, state.boardName,
							state.threadNumber, posts, options.contains(OPTION_THUMBNAILS),
							options.contains(OPTION_FILES), new PerformSendCallback(posts.size()));
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
				dialog.setMessage(context.getString(R.string.processing_data__ellipsis));
				dialog.setMax(localArchiveMax);
			} else {
				dialog = new ProgressDialog(context, null);
				dialog.setMessage(context.getString(R.string.sending__ellipsis));
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
				case DELETE:
				case REPORT: {
					ToastUtils.show(context, R.string.request_has_been_sent_successfully);
					break;
				}
				case ARCHIVE: {
					if (archiveThreadNumber != null) {
						FavoritesStorage.getInstance().add(state.archiveChanName, archiveBoardName,
								archiveThreadNumber, state.archiveThreadTitle, 0);
						ClickableToast.show(context, context.getString(R.string.completed),
								context.getString(R.string.open_thread), false, () -> uiManager.navigator()
										.navigatePosts(state.archiveChanName, archiveBoardName,
												archiveThreadNumber, null, state.archiveThreadTitle));
					} else {
						ToastUtils.show(context, R.string.completed);
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
			return uiManager.callback().getDownloadBinder();
		}

		@Override
		public void onLocalArchivationProgressUpdate(int handledPostsCount) {
			dialog.setValue(handledPostsCount);
		}

		@Override
		public void onLocalArchivationComplete(boolean success) {
			completeTask();
			if (!success) {
				ToastUtils.show(uiManager.getContext(), R.string.unknown_error);
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
