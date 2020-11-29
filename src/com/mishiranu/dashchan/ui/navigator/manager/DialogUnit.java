package com.mishiranu.dashchan.ui.navigator.manager;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.Pair;
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
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadSinglePostTask;
import com.mishiranu.dashchan.content.async.SendLocalArchiveTask;
import com.mishiranu.dashchan.content.async.SendMultifunctionalTask;
import com.mishiranu.dashchan.content.async.TaskViewModel;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.InstanceDialog;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.DialogStack;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.InsetsLayout;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PostsLayoutManager;
import com.mishiranu.dashchan.widget.ProgressDialog;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import com.mishiranu.dashchan.widget.ThemeEngine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
			private final List<DialogProvider.Factory<?>> factories;
			private final AttachmentDialog attachmentDialog;
			private final PostNumber postContextMenu;

			public State(List<DialogProvider.Factory<?>> factories, AttachmentDialog attachmentDialog,
					PostNumber postContextMenu) {
				this.factories = factories;
				this.attachmentDialog = attachmentDialog;
				this.postContextMenu = postContextMenu;
			}

			public void dropState() {
				for (DialogProvider.Factory<?> factory : factories) {
					factory.release();
				}
			}
		}

		private final DialogStack<DialogFactory> dialogStack;
		private Pair<AttachmentDialog, Dialog> attachmentDialog;
		private Pair<PostNumber, Dialog> postContextMenu;

		private StackInstance(DialogStack<DialogFactory> dialogStack) {
			this.dialogStack = dialogStack;
		}

		public State collectState() {
			ArrayList<DialogProvider.Factory<?>> factories = new ArrayList<>();
			for (Pair<DialogFactory, View> pair : dialogStack) {
				if (pair.second != null) {
					pair.first.delegate.saveState(pair.second);
				}
				factories.add(pair.first.delegate.factory);
				pair.first.delegate.factory.use();
			}
			return new State(factories, attachmentDialog != null ? attachmentDialog.first : null,
					postContextMenu != null ? postContextMenu.first : null);
		}
	}

	DialogUnit(UiManager uiManager) {
		this.uiManager = uiManager;
	}

	public StackInstance createStackInstance() {
		return new StackInstance(new DialogStack<>(uiManager.getContext()));
	}

	private static class DialogFactory implements DialogStack.ViewFactory<DialogFactory> {
		public final TypedDialogFactory<?> delegate;

		public DialogFactory(DialogProvider.Factory<?> factory,
				UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
			delegate = new TypedDialogFactory<>(factory, uiManager, configurationSet);
		}

		@Override
		public View createView(DialogStack<DialogFactory> dialogStack) {
			return delegate.createView(dialogStack);
		}

		@Override
		public void destroyView(View view, boolean remove) {
			delegate.destroyView(view, remove);
		}

		@Override
		public boolean isScrolledToTop(View view) {
			return delegate.isScrolledToTop(view);
		}

		@Override
		public boolean isScrolledToBottom(View view) {
			return delegate.isScrolledToBottom(view);
		}
	}

	private static class TypedDialogFactory<T> {
		private final DialogProvider<T> provider;
		private final DialogProvider.Factory<T> factory;

		public TypedDialogFactory(DialogProvider.Factory<T> factory,
				UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
			factory.use();
			this.provider = factory.create(uiManager, configurationSet);
			this.factory = factory;
		}

		public View createView(DialogStack<DialogFactory> dialogStack) {
			Context context = provider.uiManager.getContext();
			float density = ResourceUtils.obtainDensity(context);
			FrameLayout content = new FrameLayout(context);
			content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
			PaddedRecyclerView recyclerView = new PaddedRecyclerView(context);
			content.addView(recyclerView, FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.WRAP_CONTENT);
			if (!C.API_MARSHMALLOW) {
				@SuppressWarnings("deprecation")
				Runnable setAnimationCacheEnabled = () -> recyclerView.setAnimationCacheEnabled(false);
				setAnimationCacheEnabled.run();
			}
			recyclerView.setMotionEventSplittingEnabled(false);
			recyclerView.setClipToPadding(false);
			recyclerView.setVerticalScrollBarEnabled(true);
			FrameLayout progress = new FrameLayout(content.getContext());
			content.addView(progress, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
			progress.setPadding(0, (int) (60f * density), 0, (int) (60f * density));
			progress.setVisibility(View.GONE);
			ProgressBar progressBar = new ProgressBar(progress.getContext());
			progress.addView(progressBar, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
			((FrameLayout.LayoutParams) progressBar.getLayoutParams()).gravity = Gravity.CENTER;
			ThemeEngine.applyStyle(progressBar);
			int dividerPadding = (int) (12f * density);
			recyclerView.setLayoutManager(new PostsLayoutManager(recyclerView.getContext()));
			provider.uiManager.view().bindThreadsPostRecyclerView(recyclerView);
			DialogPostsAdapter<T> adapter = new DialogPostsAdapter<>(provider.uiManager, provider, recyclerView);
			recyclerView.setAdapter(adapter);
			recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
					(c, position) -> c.need(true).horizontal(dividerPadding, dividerPadding)));
			DialogHolder<T> holder = new DialogHolder<>(adapter, provider, recyclerView, progress);
			provider.uiManager.observable().register(holder);
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

		public void destroyView(View view, boolean remove) {
			saveState(view);
			if (remove) {
				factory.release();
			}
			DialogHolder<?> holder = (DialogHolder<?>) view.getTag();
			provider.uiManager.observable().unregister(holder);
			holder.cancel();
		}

		public void saveState(View view) {
			DialogHolder<?> holder = (DialogHolder<?>) view.getTag();
			factory.listPosition = ListPosition.obtain(holder.recyclerView, null);
		}

		public boolean isScrolledToTop(View view) {
			DialogHolder<?> holder = (DialogHolder<?>) view.getTag();
			if (holder.recyclerView.getVisibility() == View.VISIBLE) {
				return holder.recyclerView.computeVerticalScrollOffset() == 0;
			}
			return true;
		}

		public boolean isScrolledToBottom(View view) {
			DialogHolder<?> holder = (DialogHolder<?>) view.getTag();
			if (holder.recyclerView.getVisibility() == View.VISIBLE) {
				return holder.recyclerView.computeVerticalScrollOffset() +
						holder.recyclerView.computeVerticalScrollExtent() >=
						holder.recyclerView.computeVerticalScrollRange();
			}
			return true;
		}
	}

	private static class DialogHolder<T> implements UiManager.Observer {
		public final DialogPostsAdapter<T> adapter;
		public final DialogProvider<T> dialogProvider;

		public final RecyclerView recyclerView;
		public final View progress;

		public DialogHolder(DialogPostsAdapter<T> adapter, DialogProvider<T> dialogProvider,
				RecyclerView recyclerView, View progress) {
			this.adapter = adapter;
			this.dialogProvider = dialogProvider;
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

		@Override
		public void onReloadAttachmentItem(AttachmentItem attachmentItem) {
			dialogProvider.onReloadAttachmentItem(attachmentItem);
			adapter.reloadAttachment(attachmentItem);
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

	private static abstract class DialogProvider<T> implements UiManager.Observer, Iterable<PostItem>,
			ListViewUtils.ClickCallback<PostItem, RecyclerView.ViewHolder> {
		public interface ConfigurationSetProvider<T> {
			UiManager.ConfigurationSet create(T dialogProvider);
		}

		public static abstract class Factory<T> {
			public ListPosition listPosition;
			public int useCount;

			public abstract DialogProvider<T> create(UiManager uiManager, UiManager.ConfigurationSet configurationSet);
			public void destroy() {}

			public final void use() {
				useCount++;
			}

			public final void release() {
				useCount--;
				if (useCount == 0) {
					destroy();
				}
			}
		}

		public final UiManager uiManager;
		public final UiManager.ConfigurationSet configurationSet;

		public DialogProvider(UiManager uiManager, ConfigurationSetProvider<T> configurationSetProvider) {
			T self = getThis();
			if (this != self) {
				throw new IllegalStateException();
			}
			this.uiManager = uiManager;
			this.configurationSet = configurationSetProvider.create(self);
		}

		protected abstract T getThis();

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
		public boolean onItemClick(RecyclerView.ViewHolder holder, int position, PostItem postItem, boolean longClick) {
			if (longClick) {
				uiManager.interaction().handlePostContextMenu(configurationSet, postItem);
			} else {
				uiManager.interaction().handlePostClick(holder.itemView,
						configurationSet.postStateProvider, postItem, this);
			}
			return true;
		}
	}

	private static class SingleDialogProvider extends DialogProvider<SingleDialogProvider> {
		public static class Factory extends DialogProvider.Factory<SingleDialogProvider> {
			private final PostItem postItem;

			private Factory(PostItem postItem) {
				this.postItem = postItem;
			}

			@Override
			public SingleDialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new SingleDialogProvider(uiManager, dialogProvider -> configurationSet
						.copy(dialogProvider, false, true, null), postItem);
			}
		}

		private final PostItem postItem;

		private SingleDialogProvider(UiManager uiManager,
				ConfigurationSetProvider<SingleDialogProvider> configurationSetProvider, PostItem postItem) {
			super(uiManager, configurationSetProvider);
			this.postItem = postItem;
		}

		@Override
		protected SingleDialogProvider getThis() {
			return this;
		}

		@NonNull
		@Override
		public Iterator<PostItem> iterator() {
			return Collections.singletonList(postItem).iterator();
		}
	}

	private static class ThreadDialogProvider extends DialogProvider<ThreadDialogProvider>
			implements CommentTextView.LinkListener, UiManager.PostsProvider {
		public static class Factory extends DialogProvider.Factory<ThreadDialogProvider> {
			public final PostItem postItem;

			public Factory(PostItem postItem) {
				this.postItem = postItem;
			}

			@Override
			public ThreadDialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				String chanName = configurationSet.chanName;
				Replyable replyable = (click, data) -> {
					Chan chan = Chan.get(chanName);
					ChanConfiguration.Board board = chan.configuration.safe().obtainBoard(postItem.getBoardName());
					if (click && board.allowPosting) {
						uiManager.navigator().navigatePosting(chanName,
								postItem.getBoardName(), postItem.getThreadNumber(), data);
					}
					return board.allowPosting;
				};
				GalleryItem.Set gallerySet = new GalleryItem.Set(false);
				return new ThreadDialogProvider(uiManager, dialogProvider -> new UiManager
						.ConfigurationSet(configurationSet.chanName, replyable, dialogProvider,
						UiManager.PostStateProvider.DEFAULT, gallerySet, configurationSet.fragmentManager,
						configurationSet.stackInstance, dialogProvider, dialogProvider,
						false, true, false, false, false, null), postItem, gallerySet);
			}
		}

		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private ThreadDialogProvider(UiManager uiManager,
				ConfigurationSetProvider<ThreadDialogProvider> configurationSetProvider,
				PostItem postItem, GalleryItem.Set gallerySet) {
			super(uiManager, configurationSetProvider);
			if (!postItem.isThreadItem()) {
				throw new RuntimeException("Not thread item");
			}
			postItem.setOrdinalIndex(0);
			postItem.clearReferencesFrom();
			postItems.add(postItem);
			List<PostItem> childPostItems = postItem.getThreadPosts(Chan.get(configurationSet.chanName));
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
			gallerySet.setThreadTitle(this.postItems.get(0).getSubjectOrComment());
			for (PostItem childPostItem : postItems) {
				gallerySet.put(childPostItem.getPostNumber(), childPostItem.getAttachmentItems());
			}
		}

		@Override
		protected ThreadDialogProvider getThis() {
			return this;
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
		public void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed) {
			PostItem originalPostItem = postItems.get(0);
			String boardName = originalPostItem.getBoardName();
			String threadNumber = originalPostItem.getThreadNumber();
			Chan chan = Chan.get(configurationSet.chanName);
			if (extra.chanName != null && chan.locator.safe(false).isThreadUri(uri)
					&& (extra.inBoardLink || CommonUtils.equals(boardName, chan.locator.safe(false).getBoardName(uri)))
					&& CommonUtils.equals(threadNumber, chan.locator.safe(false).getThreadNumber(uri))) {
				PostNumber postNumber = chan.locator.safe(false).getPostNumber(uri);
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
			uiManager.interaction().handleLinkClick(configurationSet, uri, extra, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, Uri uri, Extra extra) {
			uiManager.interaction().handleLinkLongClick(configurationSet, uri);
		}
	}

	private static class RepliesDialogProvider extends DialogProvider<RepliesDialogProvider> {
		public static class Factory extends DialogProvider.Factory<RepliesDialogProvider> {
			private final PostItem postItem;

			public Factory(PostItem postItem) {
				this.postItem = postItem;
			}

			@Override
			public RepliesDialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new RepliesDialogProvider(uiManager, dialogProvider -> configurationSet
						.copy(dialogProvider, false, true, postItem.getPostNumber()), postItem);
			}
		}

		private final PostItem postItem;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private RepliesDialogProvider(UiManager uiManager,
				ConfigurationSetProvider<RepliesDialogProvider> configurationSetProvider, PostItem postItem) {
			super(uiManager, configurationSetProvider);
			this.postItem = postItem;
			onRequestUpdate();
		}

		@Override
		protected RepliesDialogProvider getThis() {
			return this;
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

	private static class ListDialogProvider extends DialogProvider<ListDialogProvider> {
		public static class Factory extends DialogProvider.Factory<ListDialogProvider> {
			private final HashSet<PostNumber> postNumbers;

			public Factory(Collection<PostNumber> postNumbers) {
				this.postNumbers = new HashSet<>(postNumbers);
			}

			@Override
			public ListDialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				return new ListDialogProvider(uiManager, dialogProvider -> configurationSet
						.copy(dialogProvider, false, true, null), postNumbers);
			}
		}

		private final HashSet<PostNumber> postNumbers;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		private ListDialogProvider(UiManager uiManager,
				ConfigurationSetProvider<ListDialogProvider> configurationSetProvider,
				HashSet<PostNumber> postNumbers) {
			super(uiManager, configurationSetProvider);
			this.postNumbers = postNumbers;
			onRequestUpdate();
		}

		@Override
		protected ListDialogProvider getThis() {
			return this;
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

	private static class AsyncDialogProvider extends DialogProvider<AsyncDialogProvider>
			implements UiManager.PostsProvider {
		public static class Factory extends DialogProvider.Factory<AsyncDialogProvider>
				implements ReadSinglePostTask.Callback {
			private final WeakObservable<Runnable> observable = new WeakObservable<>();

			private final String chanName;
			private final String boardName;
			private final String threadNumber;
			private final PostNumber postNumber;

			private PostItem postItem;
			private ErrorItem errorItem;
			private ReadSinglePostTask readTask;

			public Factory(String chanName, String boardName, String threadNumber, PostNumber postNumber) {
				this.chanName = chanName;
				this.boardName = boardName;
				this.threadNumber = threadNumber;
				this.postNumber = postNumber;
			}

			@Override
			public AsyncDialogProvider create(UiManager uiManager, UiManager.ConfigurationSet configurationSet) {
				GalleryItem.Set gallerySet = new GalleryItem.Set(false);
				if (postItem == null && errorItem == null && readTask == null) {
					readTask = new ReadSinglePostTask(this, Chan.get(chanName), boardName, threadNumber, postNumber);
					readTask.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				}
				return new AsyncDialogProvider(uiManager, dialogProvider -> new UiManager
						.ConfigurationSet(configurationSet.chanName, null, dialogProvider,
						UiManager.PostStateProvider.DEFAULT, gallerySet, configurationSet.fragmentManager,
						configurationSet.stackInstance, null, dialogProvider,
						false, true, false, false, false, null), this,
						chanName, boardName, threadNumber, postNumber, gallerySet);
			}

			private void notifyObservers() {
				// Error will cause observer to unregister
				ArrayList<Runnable> observers = null;
				for (Runnable runnable : observable) {
					if (observers == null) {
						observers = new ArrayList<>(1);
					}
					observers.add(runnable);
				}
				if (observers != null) {
					for (Runnable runnable : observers) {
						runnable.run();
					}
				}
			}

			@Override
			public void onReadSinglePostSuccess(PostItem postItem) {
				readTask = null;
				this.postItem = postItem;
				notifyObservers();
			}

			@Override
			public void onReadSinglePostFail(ErrorItem errorItem) {
				readTask = null;
				this.errorItem = errorItem;
				notifyObservers();
			}

			@Override
			public void destroy() {
				if (readTask != null) {
					readTask.cancel();
					readTask = null;
				}
			}
		}

		private final Factory factory;
		private final String chanName;
		private final String boardName;
		private final String threadNumber;
		private final PostNumber postNumber;
		private final GalleryItem.Set gallerySet;

		private AsyncDialogProvider(UiManager uiManager,
				ConfigurationSetProvider<AsyncDialogProvider> configurationSetProvider, Factory factory,
				String chanName, String boardName, String threadNumber, PostNumber postNumber,
				GalleryItem.Set gallerySet) {
			super(uiManager, configurationSetProvider);
			this.factory = factory;
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
			this.gallerySet = gallerySet;
			if (factory.postItem != null) {
				updatePostItem();
			} else if (factory.errorItem != null) {
				ConcurrentUtils.HANDLER.post(updateErrorItem);
			} else {
				switchState(State.LOADING, null);
				factory.observable.register(takeResult);
			}
		}

		@Override
		protected AsyncDialogProvider getThis() {
			return this;
		}

		@Override
		public PostItem findPostItem(PostNumber postNumber) {
			return factory.postItem != null && factory.postItem.getPostNumber().equals(postNumber)
					? factory.postItem : null;
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
			ConcurrentUtils.HANDLER.removeCallbacks(updateErrorItem);
			factory.observable.unregister(takeResult);
		}

		private void updatePostItem() {
			PostItem postItem = factory.postItem;
			List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
			if (attachmentItems != null) {
				if (postItem.isOriginalPost()) {
					gallerySet.setThreadTitle(postItem.getSubjectOrComment());
				}
				gallerySet.put(postItem.getPostNumber(), attachmentItems);
			}
		}

		private void updateErrorItem() {
			ErrorItem errorItem = factory.errorItem;
			switchState(State.ERROR, () -> ClickableToast.show(errorItem.toString(), null,
					new ClickableToast.Button(R.string.open_thread, false, () -> uiManager.navigator()
							.navigatePosts(chanName, boardName, threadNumber, postNumber, null))));
		}

		private void takeResult() {
			if (factory.postItem != null) {
				updatePostItem();
				switchState(State.LIST, null);
			} else {
				updateErrorItem();
			}
		}

		private final Runnable updateErrorItem = this::updateErrorItem;
		private final Runnable takeResult = this::takeResult;
	}

	public void notifyDataSetChangedToAll(StackInstance stackInstance) {
		for (View view : stackInstance.dialogStack.getVisibleViews()) {
			DialogHolder<?> holder = (DialogHolder<?>) view.getTag();
			holder.notifyDataSetChanged();
		}
	}

	public void updateAdapters(StackInstance stackInstance) {
		for (View view : stackInstance.dialogStack.getVisibleViews()) {
			DialogHolder<?> holder = (DialogHolder<?>) view.getTag();
			holder.requestUpdate();
		}
	}

	public void closeDialogs(StackInstance stackInstance) {
		if (stackInstance.postContextMenu != null) {
			stackInstance.postContextMenu.second.dismiss();
			stackInstance.postContextMenu = null;
		}
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

	private void display(UiManager.ConfigurationSet configurationSet, DialogProvider.Factory<?> factory) {
		configurationSet.stackInstance.dialogStack.push(new DialogFactory(factory, uiManager, configurationSet));
		uiManager.callback().onDialogStackOpen();
	}

	public void restoreState(UiManager.ConfigurationSet configurationSet, StackInstance.State state) {
		if (!state.factories.isEmpty()) {
			ArrayList<DialogFactory> dialogFactories = new ArrayList<>();
			for (DialogProvider.Factory<?> factory : state.factories) {
				DialogFactory dialogFactory = new DialogFactory(factory, uiManager, configurationSet);
				configurationSet = dialogFactory.delegate.provider.configurationSet;
				dialogFactories.add(dialogFactory);
			}
			configurationSet.stackInstance.dialogStack.addAll(dialogFactories);
			uiManager.callback().onDialogStackOpen();
		}
		if (state.attachmentDialog != null) {
			showAttachmentsGrid(configurationSet,
					state.attachmentDialog.attachmentItems, state.attachmentDialog.startImageIndex,
					state.attachmentDialog.navigatePostMode, state.attachmentDialog.gallerySet);
		}
		if (state.postContextMenu != null && configurationSet.postsProvider != null) {
			PostItem postItem = configurationSet.postsProvider.findPostItem(state.postContextMenu);
			if (postItem != null) {
				uiManager.interaction().handlePostContextMenu(configurationSet, postItem);
			}
		}
	}

	private static class DialogPostsAdapter<T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private static final String PAYLOAD_INVALIDATE_COMMENT = "invalidateComment";

		private final UiManager uiManager;
		private final DialogProvider<T> dialogProvider;
		private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
		private final RecyclerView.AdapterDataObserver updateObserver;
		private final CommentTextView.RecyclerKeeper recyclerKeeper;

		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public final HashSet<PostNumber> postNumbers = new HashSet<>();

		public DialogPostsAdapter(UiManager uiManager, DialogProvider<T> dialogProvider, RecyclerView recyclerView) {
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
					? ViewUnit.ViewType.POST_HIDDEN : ViewUnit.ViewType.POST).ordinal();
		}

		private PostItem getItem(int position) {
			return postItems.get(position);
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return uiManager.view().createView(parent, ViewUnit.ViewType.values()[viewType]);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			onBindViewHolder(holder, position, Collections.emptyList());
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
				@NonNull List<Object> payloads) {
			PostItem postItem = getItem(position);
			switch (ViewUnit.ViewType.values()[holder.getItemViewType()]) {
				case POST: {
					if (payloads.isEmpty()) {
						dialogProvider.onRequestUpdateDemandSet(demandSet, position);
						uiManager.view().bindPostView(holder, postItem, dialogProvider.configurationSet, demandSet);
					} else {
						if (payloads.contains(PAYLOAD_INVALIDATE_COMMENT)) {
							uiManager.view().bindPostViewInvalidateComment(holder);
						}
						for (Object object : payloads) {
							if (object instanceof AttachmentItem) {
								uiManager.view().bindPostViewReloadAttachment(holder, (AttachmentItem) object);
							}
						}
					}
					break;
				}
				case POST_HIDDEN: {
					uiManager.view().bindPostHiddenView(holder, postItem, dialogProvider.configurationSet);
					break;
				}
			}
		}

		public void invalidateComment(int position) {
			notifyItemChanged(position, PAYLOAD_INVALIDATE_COMMENT);
		}

		public void reloadAttachment(AttachmentItem attachmentItem) {
			for (int i = 0; i < postItems.size(); i++) {
				PostItem postItem = postItems.get(i);
				if (postItem.getPostNumber().equals(attachmentItem.getPostNumber())) {
					notifyItemChanged(i, attachmentItem);
					break;
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void showAttachmentsGrid(UiManager.ConfigurationSet configurationSet, List<AttachmentItem> attachmentItems,
			int startImageIndex, GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
		Context context = uiManager.getContext();
		Dialog dialog = new Dialog(context, R.style.Theme_Gallery);
		Context styledContext = dialog.getContext();
		Pair<StackInstance.AttachmentDialog, Dialog> attachmentDialog = new Pair<>(new StackInstance
				.AttachmentDialog(attachmentItems, startImageIndex, navigatePostMode, gallerySet), dialog);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setOnKeyListener((d, keyCode, event) -> {
			if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
				closeDialogs(configurationSet.stackInstance);
				return true;
			}
			return false;
		});
		View.OnClickListener closeListener = v -> dialog.cancel();
		LayoutInflater inflater = LayoutInflater.from(styledContext);
		InsetsLayout rootView = new InsetsLayout(styledContext);
		rootView.setOnClickListener(closeListener);
		ScrollView scrollView = new ScrollView(styledContext) {
			@Override
			public void draw(Canvas canvas) {
				super.draw(canvas);
				if (C.API_LOLLIPOP) {
					ViewUtils.drawSystemInsetsOver(this, canvas, InsetsLayout.isTargetGesture29(this));
				}
			}
		};
		if (C.API_LOLLIPOP) {
			scrollView.setWillNotDraw(false);
		}
		scrollView.setVerticalScrollBarEnabled(false);
		scrollView.setClipToPadding(false);
		rootView.setOnApplyInsetsTarget(scrollView);
		rootView.addView(scrollView, new InsetsLayout.LayoutParams(InsetsLayout.LayoutParams.WRAP_CONTENT,
				InsetsLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
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
			openAttachment(v, configurationSet.chanName,
					attachmentItems, index, imageIndex, navigatePostMode, gallerySet);
		};
		Chan chan = Chan.get(configurationSet.chanName);
		Configuration configuration = context.getResources().getConfiguration();
		boolean tablet = ResourceUtils.isTablet(configuration);
		boolean tabletLarge = ResourceUtils.isTabletLarge(configuration);
		float density = ResourceUtils.obtainDensity(context);
		int total = 0;
		LinearLayout linearLayout = null;
		int padding = (int) (8f * density);
		int size = (int) ((tabletLarge ? 180f : tablet ? 160f : 120f) * density);
		int columns = tablet ? 3 : 2;
		HashMap<AttachmentItem, AttachmentView> attachmentViews = new HashMap<>();
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
			if (C.API_LOLLIPOP) {
				textView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			}
			attachmentItem.configureAndLoad(attachmentView, chan, false, true);
			textView.setText(attachmentItem.getDescription(AttachmentItem.FormatMode.TWO_LINES));
			View clickView = view.findViewById(R.id.attachment_click);
			clickView.setOnClickListener(clickListener);
			clickView.setOnLongClickListener(v -> {
				uiManager.interaction().showThumbnailLongClickDialog(configurationSet,
						attachmentItem, attachmentView, gallerySet.getThreadTitle());
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
			attachmentViews.put(attachmentItem, attachmentView);
		}
		dialog.setContentView(rootView);
		Window window = dialog.getWindow();
		WindowManager.LayoutParams layoutParams = window.getAttributes();
		layoutParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
		int[] attrs = new int[] {android.R.attr.windowAnimationStyle, android.R.attr.backgroundDimAmount};
		TypedArray typedArray = styledContext.obtainStyledAttributes(null, attrs, android.R.attr.dialogTheme, 0);
		try {
			layoutParams.windowAnimations = typedArray.getResourceId(0, 0);
			layoutParams.dimAmount = typedArray.getFloat(1, 0.6f);
		} finally {
			typedArray.recycle();
		}
		if (C.API_LOLLIPOP) {
			window.setStatusBarColor(0x00000000);
			window.setNavigationBarColor(0x00000000);
			ViewUtils.setWindowLayoutFullscreen(window);
		}
		ThemeEngine.markDecorAsDialog(window.getDecorView());
		UiManager.Observer observer = new UiManager.Observer() {
			@Override
			public void onReloadAttachmentItem(AttachmentItem attachmentItem) {
				AttachmentView attachmentView = attachmentViews.get(attachmentItem);
				if (attachmentView != null) {
					attachmentItem.configureAndLoad(attachmentView, Chan.get(configurationSet.chanName), false, true);
				}
			}
		};
		if (configurationSet.stackInstance.attachmentDialog != null) {
			configurationSet.stackInstance.attachmentDialog.second.dismiss();
			configurationSet.stackInstance.attachmentDialog = null;
		}
		configurationSet.stackInstance.attachmentDialog = attachmentDialog;
		uiManager.observable().register(observer);
		dialog.setOnDismissListener(dialogInterface -> {
			if (configurationSet.stackInstance.attachmentDialog == attachmentDialog) {
				configurationSet.stackInstance.attachmentDialog = null;
			}
			uiManager.observable().unregister(observer);
		});
		dialog.show();
	}

	public void openAttachmentOrDialog(UiManager.ConfigurationSet configurationSet, View imageView,
			List<AttachmentItem> attachmentItems, int imageIndex,
			GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
		if (attachmentItems.size() > 1) {
			showAttachmentsGrid(configurationSet, attachmentItems, imageIndex, navigatePostMode, gallerySet);
		} else {
			openAttachment(imageView, configurationSet.chanName,
					attachmentItems, 0, imageIndex, navigatePostMode, gallerySet);
		}
	}

	public void openAttachment(View imageView, String chanName, List<AttachmentItem> attachmentItems, int index,
			int imageIndex, GalleryOverlay.NavigatePostMode navigatePostMode, GalleryItem.Set gallerySet) {
		Context context = uiManager.getContext();
		AttachmentItem attachmentItem = attachmentItems.get(index);
		boolean canDownload = attachmentItem.canDownloadToStorage();
		Chan chan = Chan.get(chanName);
		Uri uri = attachmentItem.getFileUri(chan);
		AttachmentItem.Type type = attachmentItem.getType();
		if (canDownload && type == AttachmentItem.Type.AUDIO) {
			AudioPlayerService.start(context, chanName, uri, attachmentItem.getFileName(chan));
		} else if (canDownload && (type == AttachmentItem.Type.IMAGE || type == AttachmentItem.Type.VIDEO &&
				NavigationUtils.isOpenableVideoExtension(attachmentItem.getExtension()))) {
			uiManager.navigator().navigateGallery(chanName, gallerySet, imageIndex,
					imageView, navigatePostMode, false);
		} else {
			NavigationUtils.handleUri(context, chanName, uri, NavigationUtils.BrowserType.EXTERNAL);
		}
	}

	void handlePostContextMenu(UiManager.ConfigurationSet configurationSet, PostNumber postNumber,
			boolean show, AlertDialog dialog) {
		if (show) {
			if (configurationSet.stackInstance.postContextMenu != null) {
				configurationSet.stackInstance.postContextMenu.second.dismiss();
			}
			configurationSet.stackInstance.postContextMenu = new Pair<>(postNumber, dialog);
		} else {
			if (configurationSet.stackInstance.postContextMenu != null &&
					configurationSet.stackInstance.postContextMenu.second == dialog) {
				configurationSet.stackInstance.postContextMenu = null;
			}
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

	public void showPostDescriptionDialog(FragmentManager fragmentManager,
			Collection<IconData> icons, String chanName, String emailToCopy) {
		showPostDescriptionDialogStatic(fragmentManager, icons, chanName, emailToCopy);
	}

	private static void showPostDescriptionDialogStatic(FragmentManager fragmentManager,
			Collection<IconData> icons, String chanName, String emailToCopy) {
		new InstanceDialog(fragmentManager, null, provider -> createPostDescriptionDialog(provider.getContext(),
				icons, chanName, emailToCopy));
	}

	private static AlertDialog createPostDescriptionDialog(Context context,
			Collection<IconData> icons, String chanName, String emailToCopy) {
		float density = ResourceUtils.obtainDensity(context);
		ImageLoader imageLoader = ImageLoader.getInstance();
		Chan chan = Chan.get(chanName);
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
				imageLoader.loadImage(chan, icon.uri, false, imageView);
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
				textView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			} else {
				textView.setPadding((int) (10f * density), 0, 0, 0); // 20f = 8f + 2f
				ViewUtils.setTextSizeScaled(textView, 16);
				textView.setAllCaps(true);
			}
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(context).setPositiveButton(android.R.string.ok, null);
		if (!StringUtils.isEmpty(emailToCopy)) {
			builder.setNeutralButton(R.string.copy_email,
					(dialog, which) -> StringUtils.copyToClipboard(context, emailToCopy));
		}
		builder.setView(container);
		return builder.create();
	}

	public void performSendDeletePosts(FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber, List<PostNumber> postNumbers) {
		Chan chan = Chan.get(chanName);
		ChanConfiguration.Deleting deleting = chan.configuration.safe().obtainDeleting(boardName);
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
		showPerformSendDialog(fragmentManager, state, null, Preferences.getPassword(chan), null, null, true);
	}

	public void performSendReportPosts(FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber, List<PostNumber> postNumbers) {
		Chan chan = Chan.get(chanName);
		ChanConfiguration.Reporting reporting = chan.configuration.safe().obtainReporting(boardName);
		if (reporting == null) {
			return;
		}
		SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.REPORT, chanName, boardName, threadNumber, reporting.types,
				reporting.options, reporting.comment);
		state.postNumbers = postNumbers;
		showPerformSendDialog(fragmentManager, state, null, null, null, null, true);
	}

	public void performSendArchiveThread(FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber, String threadTitle, Collection<Post> posts) {
		performSendArchiveThread(uiManager.getContext(), fragmentManager,
				chanName, boardName, threadNumber, threadTitle, posts);
	}

	private static void performSendArchiveThread(Context context, FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber, String threadTitle, Collection<Post> posts) {
		Chan chan = Chan.get(chanName);
		boolean canArchiveLocal = !chan.configuration.getOption(ChanConfiguration.OPTION_LOCAL_MODE);
		List<String> archiveChanNames = ChanManager.getInstance().getArchiveChanNames(chanName);
		SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.ARCHIVE, chanName, boardName, threadNumber, null, null, false);
		state.archiveThreadTitle = threadTitle;
		if (canArchiveLocal && archiveChanNames.size() > 0 || archiveChanNames.size() > 1) {
			new InstanceDialog(fragmentManager, null, provider -> {
				String[] chanNameItems = new String[archiveChanNames.size() + (canArchiveLocal ? 1 : 0)];
				String[] items = new String[archiveChanNames.size() + (canArchiveLocal ? 1 : 0)];
				if (canArchiveLocal) {
					items[0] = provider.getContext().getString(R.string.local_archive);
				}
				for (int i = 0; i < archiveChanNames.size(); i++) {
					Chan archiveChan = Chan.get(archiveChanNames.get(i));
					chanNameItems[canArchiveLocal ? i + 1 : i] = archiveChan.name;
					items[canArchiveLocal ? i + 1 : i] = archiveChan.configuration.getTitle();
				}
				return new AlertDialog.Builder(provider.getContext())
						.setTitle(R.string.archive__verb)
						.setItems(items, (d, which) -> performSendArchiveThreadInternal(provider.getContext(),
								provider.getFragmentManager(), state, chanNameItems[which], posts))
						.create();
			});
		} else if (canArchiveLocal) {
			performSendArchiveThreadInternal(context, fragmentManager, state, null, posts);
		}
	}

	public static final String OPTION_THUMBNAILS = "thumbnails";
	public static final String OPTION_FILES = "files";

	private static void performSendArchiveThreadInternal(Context context, FragmentManager fragmentManager,
			SendMultifunctionalTask.State state, String archiveChanName, Collection<Post> posts) {
		ChanConfiguration.Archivation archivation;
		if (archiveChanName == null) {
			archivation = new ChanConfiguration.Archivation();
			archivation.options.add(new Pair<>(OPTION_THUMBNAILS, context.getString(R.string.save_thumbnails)));
			archivation.options.add(new Pair<>(OPTION_FILES, context.getString(R.string.save_files)));
		} else {
			Chan archiveChan = Chan.get(archiveChanName);
			archivation = archiveChan.configuration.safe().obtainArchivation();
		}
		if (archivation == null) {
			return;
		}
		state.archiveChanName = archiveChanName;
		state.options = archivation.options;
		showPerformSendDialog(fragmentManager, state, null, null, null, posts, true);
	}

	private static void showPerformSendDialog(FragmentManager fragmentManager,
			SendMultifunctionalTask.State state, String defaultType, String defaultText, List<String> defaultOptions,
			Collection<Post> posts, boolean firstTime) {
		new InstanceDialog(fragmentManager, null, provider -> createPerformSendDialog(provider,
				state, defaultType, defaultText, defaultOptions, posts, firstTime));
	}

	private static Dialog createPerformSendDialog(InstanceDialog.Provider provider,
			SendMultifunctionalTask.State state, String defaultType,
			String defaultText, List<String> defaultOptions, Collection<Post> posts, boolean firstTime) {
		Context context = provider.getContext();
		RadioGroup radioGroup;
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
			if (!firstTime) {
				return provider.createDismissDialog();
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

		return builder.setPositiveButton(android.R.string.ok, (d, which) -> {
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
					ClickableToast.show(R.string.cache_is_unavailable);
				} else {
					startLocalArchiveProcess(provider.getFragmentManager(), state.chanName,
							state.boardName, state.threadNumber, posts,
							options.contains(OPTION_THUMBNAILS), options.contains(OPTION_FILES));
				}
			} else {
				startMultifunctionalProcess(provider.getFragmentManager(), state, type, text, options);
			}
		}).setNegativeButton(android.R.string.cancel, null).create();
	}

	public static class MultifunctionalViewModel extends
			TaskViewModel.Proxy<SendMultifunctionalTask, SendMultifunctionalTask.Callback> {}

	private static void startMultifunctionalProcess(FragmentManager fragmentManager,
			SendMultifunctionalTask.State state, String type, String text, List<String> options) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = provider.getContext();
			ProgressDialog dialog = new ProgressDialog(context, null);
			dialog.setMessage(context.getString(R.string.sending__ellipsis));
			MultifunctionalViewModel viewModel = provider.getViewModel(MultifunctionalViewModel.class);
			if (!viewModel.hasTaskOrValue()) {
				SendMultifunctionalTask task = new SendMultifunctionalTask(viewModel.callback,
						state, type, text, options);
				task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(task);
			}
			viewModel.observe(provider.getLifecycleOwner(), new SendMultifunctionalTask.Callback() {
				@Override
				public void onSendSuccess(String archiveBoardName, String archiveThreadNumber) {
					provider.dismiss();
					switch (state.operation) {
						case DELETE:
						case REPORT: {
							ClickableToast.show(R.string.request_has_been_sent_successfully);
							break;
						}
						case ARCHIVE: {
							if (archiveThreadNumber != null) {
								String chanName = state.archiveChanName;
								FavoritesStorage.getInstance().add(chanName, archiveBoardName,
										archiveThreadNumber, state.archiveThreadTitle);
								UiManager uiManager = UiManager.extract(provider);
								ClickableToast.show(context.getString(R.string.completed), null,
										new ClickableToast.Button(R.string.open_thread, false,
												() -> uiManager.navigator().navigatePosts(chanName, archiveBoardName,
														archiveThreadNumber, null, state.archiveThreadTitle)));
							} else {
								ClickableToast.show(R.string.completed);
							}
							break;
						}
					}
				}

				@Override
				public void onSendFail(ErrorItem errorItem) {
					provider.dismiss();
					ClickableToast.show(errorItem);
					showPerformSendDialog(provider.getFragmentManager(), state, type, text, options, null, false);
				}
			});
			return dialog;
		});
	}

	private static final SendLocalArchiveTask.DownloadResult DOWNLOAD_RESULT_ERROR = binder -> {};

	public static class LocalArchiveViewModel extends TaskViewModel<SendLocalArchiveTask,
			SendLocalArchiveTask.DownloadResult> implements SendLocalArchiveTask.Callback {
		public final MutableLiveData<Integer> progress = new MutableLiveData<>();

		@Override
		public void onLocalArchivationProgressUpdate(int handledPostsCount) {
			progress.setValue(handledPostsCount);
		}

		@Override
		public void onLocalArchivationComplete(SendLocalArchiveTask.DownloadResult result) {
			handleResult(result != null ? result : DOWNLOAD_RESULT_ERROR);
		}
	}

	private static void startLocalArchiveProcess(FragmentManager fragmentManager,
			String chanName, String boardName, String threadNumber, Collection<Post> posts,
			boolean saveThumbnails, boolean saveFiles) {
		new InstanceDialog(fragmentManager, null, provider -> {
			Context context = provider.getContext();
			ProgressDialog dialog = new ProgressDialog(context, "%d / %d");
			dialog.setMessage(context.getString(R.string.processing_data__ellipsis));
			dialog.setMax(posts.size());
			LocalArchiveViewModel viewModel = provider.getViewModel(LocalArchiveViewModel.class);
			if (!viewModel.hasTaskOrValue()) {
				SendLocalArchiveTask task = new SendLocalArchiveTask(viewModel, Chan.get(chanName),
						boardName, threadNumber, posts, saveThumbnails, saveFiles);
				task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
				viewModel.attach(task);
			}
			viewModel.observe(provider.getLifecycleOwner(), result -> {
				provider.dismiss();
				if (result == DOWNLOAD_RESULT_ERROR) {
					ClickableToast.show(R.string.unknown_error);
				} else {
					UiManager uiManager = UiManager.extract(provider);
					result.run(uiManager.callback().getDownloadBinder());
				}
			});
			viewModel.progress.observe(provider.getLifecycleOwner(), dialog::setValue);
			return dialog;
		});
	}
}
