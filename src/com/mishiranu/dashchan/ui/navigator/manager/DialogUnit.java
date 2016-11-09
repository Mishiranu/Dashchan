/*
 * Copyright 2014-2016 Fukurou Mishiranu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.ui.navigator.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanManager;
import chan.content.model.Posts;
import chan.util.StringUtils;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.async.CancellableTask;
import com.mishiranu.dashchan.content.async.ReadSinglePostTask;
import com.mishiranu.dashchan.content.async.SendLocalArchiveTask;
import com.mishiranu.dashchan.content.async.SendMultifunctionalTask;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.service.AudioPlayerService;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.NavigationUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.BaseAdapterNotifier;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.DialogStack;
import com.mishiranu.dashchan.widget.ExpandedScreen;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.SafePasteEditText;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class DialogUnit implements DialogStack.Callback {
	private final UiManager uiManager;
	private final DialogStack dialogStack;
	private final ExpandedScreen expandedScreen;

	DialogUnit(UiManager uiManager, ExpandedScreen expandedScreen) {
		this.uiManager = uiManager;
		dialogStack = new DialogStack(uiManager.getContext(), expandedScreen);
		dialogStack.setCallback(this);
		this.expandedScreen = expandedScreen;
	}

	private class DialogHolder implements UiManager.Observer {
		public final DialogPostsAdapter adapter;
		public final DialogProvider dialogProvider;

		public final FrameLayout content;
		public final ListView listView;

		public DialogHolder(DialogPostsAdapter adapter, DialogProvider dialogProvider, FrameLayout content,
				ListView listView) {
			this.adapter = adapter;
			this.dialogProvider = dialogProvider;
			this.content = content;
			this.listView = listView;
		}

		public ProgressBar loadingView;

		public ListPosition position;
		public boolean cancelled = false;

		@Override
		public void onPostItemMessage(PostItem postItem, int message) {
			switch (message) {
				case UiManager.MESSAGE_INVALIDATE_VIEW: {
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
						adapter.postNotifyDataSetChanged();
					}
					break;
				}
				case UiManager.MESSAGE_INVALIDATE_COMMENT_VIEW: {
					uiManager.view().invalidateCommentView(listView, adapter.postItems.indexOf(postItem));
					break;
				}
				case UiManager.MESSAGE_PERFORM_DISPLAY_THUMBNAILS: {
					uiManager.view().displayThumbnails(listView, adapter.postItems.indexOf(postItem),
							postItem.getAttachmentItems(), true);
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
				if (loadingView == null) {
					Context context = uiManager.getContext();
					loadingView = new ProgressBar(context, null, android.R.attr.progressBarStyle);
					float density = ResourceUtils.obtainDensity(context);
					int margins = (int) (60 * density);
					FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams
							.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
					layoutParams.setMargins(margins, margins, margins, margins);
					content.addView(loadingView, layoutParams);
				}
				listView.setVisibility(View.GONE);
			} else {
				if (loadingView != null && loadingView.getVisibility() == View.VISIBLE) {
					listView.setVisibility(View.VISIBLE);
					ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(loadingView, View.ALPHA, 1f, 0f);
					alphaAnimator.setDuration(200);
					alphaAnimator.addListener(new AnimationUtils.VisibilityListener(loadingView, View.GONE));
					alphaAnimator.start();
					listView.setAlpha(0f);
					listView.animate().alpha(1f).setStartDelay(200).setDuration(200).start();
				} else {
					if (loadingView != null) {
						loadingView.setVisibility(View.GONE);
					}
					listView.setVisibility(View.VISIBLE);
				}
			}
		}
	}

	private static final int STATE_LIST = 0;
	private static final int STATE_LOADING = 1;
	private static final int STATE_ERROR = 2;

	private interface StateListener {
		public boolean onStateChanged(int state);
	}

	private abstract class DialogProvider implements Iterable<PostItem> {
		public final UiManager.ConfigurationSet configurationSet;

		public DialogProvider(UiManager.ConfigurationSet configurationSet) {
			this.configurationSet = configurationSet;
		}

		public void onRequestUpdateDemandSet(UiManager.DemandSet demandSet, int index) {}

		public void onRequestUpdate() {}

		public void onCancel() {}

		protected StateListener stateListener;

		private int queuedState = -1;
		private Runnable queuedChangeCallback = null;

		public final void setStateListener(StateListener listener) {
			stateListener = listener;
			if (queuedState != -1) {
				invokeStateChanged(queuedState, queuedChangeCallback);
				queuedState = -1;
				queuedChangeCallback = null;
			}
		}

		protected final void switchState(int state, Runnable changeCallback) {
			if (stateListener != null) {
				invokeStateChanged(state, changeCallback);
			} else {
				queuedState = state;
				queuedChangeCallback = changeCallback;
			}
		}

		private void invokeStateChanged(int state, Runnable changeCallback) {
			boolean success = stateListener.onStateChanged(state);
			if (success && changeCallback != null) {
				changeCallback.run();
			}
		}
	}

	private class SingleDialogProvider extends DialogProvider {
		private final PostItem postItem;

		public SingleDialogProvider(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
			super(configurationSet.copyEdit(false, true, null));
			this.postItem = postItem;
		}

		@Override
		public Iterator<PostItem> iterator() {
			return Collections.singletonList(postItem).iterator();
		}
	}

	private static class ThreadDialogProviderIntermediate implements CommentTextView.LinkListener,
			UiManager.PostsProvider {
		public ThreadDialogProvider provider;

		@Override
		public Iterator<PostItem> iterator() {
			return provider.iterator();
		}

		@Override
		public PostItem findPostItem(String postNumber) {
			return provider.findPostItem(postNumber);
		}

		@Override
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed) {
			provider.onLinkClick(view, chanName, uri, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {
			provider.onLinkLongClick(view, chanName, uri);
		}
	}

	private Replyable createThreadReplyable(final PostItem postItem) {
		ChanConfiguration configuration = ChanConfiguration.get(postItem.getChanName());
		boolean canReply = configuration.safe().obtainBoard(postItem.getBoardName()).allowPosting;
		if (canReply) {
			return data -> uiManager.navigator().navigatePosting(postItem.getChanName(), postItem.getBoardName(),
					postItem.getThreadNumber(), data);
		}
		return null;
	}

	private class ThreadDialogProvider extends DialogProvider implements CommentTextView.LinkListener,
			UiManager.PostsProvider {
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		public ThreadDialogProvider(PostItem postItem, ThreadDialogProviderIntermediate intermediate) {
			super(new UiManager.ConfigurationSet(createThreadReplyable(postItem), intermediate, new HidePerformer(),
					new GalleryItem.GallerySet(false), intermediate, null, false, true, false, false, null));
			intermediate.provider = this;
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
						displaySingle(postItem, configurationSet);
						return;
					}
				}
			}
			uiManager.interaction().handleLinkClick(chanName, uri, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {
			uiManager.interaction().handleLinkLongClick(uri);
		}
	}

	private class RepliesDialogProvider extends DialogProvider {
		private final PostItem postItem;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		public RepliesDialogProvider(PostItem postItem, UiManager.ConfigurationSet configurationSet,
				String repliesToPost) {
			super(configurationSet.copyEdit(false, true, repliesToPost));
			this.postItem = postItem;
			onRequestUpdate();
		}

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

	private class ListDialogProvider extends DialogProvider {
		private final HashSet<String> postNumbers;
		private final ArrayList<PostItem> postItems = new ArrayList<>();

		public ListDialogProvider(Collection<String> postNumbers, UiManager.ConfigurationSet configurationSet) {
			super(configurationSet.copyEdit(false, true, null));
			this.postNumbers = new HashSet<>(postNumbers);
			onRequestUpdate();
		}

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

	private class AsyncDialogProvider extends DialogProvider implements ReadSinglePostTask.Callback {
		private final String chanName;
		private final String boardName;
		private final String threadNumber;
		private final String postNumber;

		private ReadSinglePostTask readTask;
		private PostItem postItem;

		public AsyncDialogProvider(String chanName, String boardName, String threadNumber, String postNumber) {
			super(new UiManager.ConfigurationSet(null, null, new HidePerformer(),
					new GalleryItem.GallerySet(false), null, null, false, true, false, false, null));
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
			this.postNumber = postNumber;
			switchState(STATE_LOADING, null);
			if (StringUtils.isEmpty(postNumber)) {
				postNumber = threadNumber;
			}
			readTask = new ReadSinglePostTask(this, chanName, boardName, postNumber);
			readTask.executeOnExecutor(ReadSinglePostTask.THREAD_POOL_EXECUTOR);
		}

		@Override
		public Iterator<PostItem> iterator() {
			List<PostItem> list;
			if (postItem != null) {
				list = Collections.singletonList(postItem);
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
			this.postItem = postItem;
			List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
			if (attachmentItems != null) {
				if (postItem.getParentPostNumber() == null) {
					configurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
				}
				configurationSet.gallerySet.add(attachmentItems);
			}
			switchState(STATE_LIST, null);
		}

		@Override
		public void onReadSinglePostFail(final ErrorItem errorItem) {
			switchState(STATE_ERROR, () -> {
				Context context = uiManager.getContext();
				ClickableToast.show(context, errorItem.toString(),
						context.getString(R.string.action_open_thread), () -> uiManager.navigator()
						.navigatePosts(chanName, boardName, threadNumber, postNumber, null, false), false);
			});
		}
	}

	public void notifyDataSetChangedToAll() {
		for (View view : dialogStack) {
			DialogHolder holder = (DialogHolder) view.getTag();
			holder.notifyDataSetChanged();
		}
	}

	public void updateAdapters() {
		for (View view : dialogStack) {
			DialogHolder holder = (DialogHolder) view.getTag();
			holder.requestUpdate();
		}
	}

	public void closeDialogs() {
		closeAttachmentDialog();
		dialogStack.clear();
	}

	// Call this method after creating any windows within activity (such as Dialogs)!
	public void notifySwitchBackground() {
		dialogStack.switchBackground(true);
	}

	public void displaySingle(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
		display(new SingleDialogProvider(postItem, configurationSet));
	}

	public void displayThread(PostItem postItem) {
		display(new ThreadDialogProvider(postItem, new ThreadDialogProviderIntermediate()));
	}

	public void displayReplies(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
		display(new RepliesDialogProvider(postItem, configurationSet, postItem.getPostNumber()));
	}

	public void displayList(Collection<String> postNumbers, UiManager.ConfigurationSet configurationSet) {
		display(new ListDialogProvider(postNumbers, configurationSet));
	}

	public void displayReplyAsync(String chanName, String boardName, String threadNumber, String postNumber) {
		display(new AsyncDialogProvider(chanName, boardName, threadNumber, postNumber));
	}

	private void display(DialogProvider dialogProvider) {
		Context context = uiManager.getContext();
		FrameLayout content = new FrameLayout(context);
		ListView listView = new ListView(context);
		content.addView(listView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		DialogPostsAdapter adapter = new DialogPostsAdapter(dialogProvider, listView);
		listView.setOnItemClickListener(adapter);
		listView.setOnItemLongClickListener(adapter);
		listView.setOnScrollListener(new BusyScrollListener(adapter));
		listView.setAdapter(adapter);
		listView.setId(android.R.id.list);
		listView.setDivider(ResourceUtils.getDrawable(context, R.attr.postsDivider, 0));
		final DialogHolder holder = new DialogHolder(adapter, dialogProvider, content, listView);
		uiManager.observable().register(holder);
		listView.setTag(holder);
		content.setTag(holder);
		dialogStack.push(content);
		dialogProvider.setStateListener((state) -> {
			switch (state) {
				case STATE_LIST: {
					holder.setShowLoading(false);
					holder.requestUpdate();
					return true;
				}
				case STATE_LOADING: {
					holder.setShowLoading(true);
					return true;
				}
				case STATE_ERROR: {
					if (!holder.cancelled) {
						dialogStack.pop();
						return true;
					}
					return false;
				}
			}
			return false;
		});
	}

	@Override
	public void onPop(View view) {
		DialogHolder holder = (DialogHolder) view.getTag();
		if (holder != null) {
			uiManager.view().notifyUnbindListView(holder.listView);
			uiManager.observable().unregister(holder);
			holder.cancel();
		}
	}

	@Override
	public void onHide(View view) {
		DialogHolder holder = (DialogHolder) view.getTag();
		if (holder != null) {
			holder.position = ListPosition.obtain(holder.listView);
		}
	}

	@Override
	public void onRestore(View view) {
		DialogHolder holder = (DialogHolder) view.getTag();
		if (holder != null && holder.position != null) {
			holder.position.apply(holder.listView);
		}
	}

	private class DialogPostsAdapter extends BaseAdapter implements BusyScrollListener.Callback,
			AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
		private static final int ITEM_VIEW_TYPE_POST = 0;
		private static final int ITEM_VIEW_TYPE_HIDDEN_POST = 1;

		private final BaseAdapterNotifier notifier = new BaseAdapterNotifier(this);

		public final ArrayList<PostItem> postItems = new ArrayList<>();
		public final HashSet<String> postNumbers = new HashSet<>();

		private final DialogProvider dialogProvider;
		private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
		private final CommentTextView.ListSelectionKeeper listSelectionKeeper;

		public DialogPostsAdapter(DialogProvider dialogProvider, ListView listView) {
			this.dialogProvider = dialogProvider;
			listSelectionKeeper = new CommentTextView.ListSelectionKeeper(listView);
			updatePostItems();
		}

		private void updatePostItems() {
			postItems.clear();
			postNumbers.clear();
			for (PostItem postItem : dialogProvider) {
				postItems.add(postItem);
				postNumbers.add(postItem.getPostNumber());
			}
		}

		@Override
		public void notifyDataSetChanged() {
			updatePostItems();
			listSelectionKeeper.onBeforeNotifyDataSetChanged();
			super.notifyDataSetChanged();
			listSelectionKeeper.onAfterNotifyDataSetChanged();
		}

		public void postNotifyDataSetChanged() {
			notifier.postNotifyDataSetChanged();
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(int position) {
			return getItem(position).isHidden(dialogProvider.configurationSet.hidePerformer)
					? ITEM_VIEW_TYPE_HIDDEN_POST : ITEM_VIEW_TYPE_POST;
		}

		@Override
		public int getCount() {
			return postItems.size();
		}

		@Override
		public PostItem getItem(int position) {
			return postItems.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			PostItem postItem = getItem(position);
			if (postItem.isHidden(dialogProvider.configurationSet.hidePerformer)) {
				convertView = uiManager.view().getPostHiddenView(postItem, convertView, parent);
			} else {
				dialogProvider.onRequestUpdateDemandSet(demandSet, position);
				convertView = uiManager.view().getPostView(postItem, convertView, parent, demandSet,
						dialogProvider.configurationSet);
			}
			return convertView;
		}

		@Override
		public void setListViewBusy(boolean isBusy, AbsListView listView) {
			uiManager.view().handleListViewBusyStateChange(isBusy, listView, demandSet);
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			uiManager.interaction().handlePostClick(view, getItem(position), postItems);
		}

		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			UiManager.ConfigurationSet configurationSet = dialogProvider.configurationSet;
			return uiManager.interaction().handlePostContextMenu(getItem(position),
					configurationSet.replyable, configurationSet.allowMyMarkEdit, configurationSet.allowHiding);
		}
	}

	private Dialog attachmentDialog;

	private void closeAttachmentDialog() {
		if (attachmentDialog != null) {
			attachmentDialog.dismiss();
		}
		attachmentDialog = null;
	}

	private final DialogInterface.OnCancelListener attachmentDialogCancelListener = d -> attachmentDialog = null;

	private final DialogInterface.OnKeyListener attachmentDialogKeyListener = (dialog, keyCode, event) -> {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress()) {
			closeDialogs();
			return true;
		}
		return false;
	};

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void showAttachmentsGrid(final Context context, final List<AttachmentItem> attachmentItems,
			final int startImageIndex, final GalleryItem.GallerySet gallerySet, PostItem postItem) {
		Context styledContext = new ContextThemeWrapper(context, R.style.Theme_Gallery);
		final Dialog dialog = new Dialog(styledContext);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setOnCancelListener(attachmentDialogCancelListener);
		dialog.setOnKeyListener(attachmentDialogKeyListener);
		View.OnClickListener closeListener = v -> dialog.cancel();
		LayoutInflater inflater = LayoutInflater.from(styledContext);
		FrameLayout rootView = new FrameLayout(styledContext);
		rootView.setOnClickListener(closeListener);
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
			openAttachment(context, v, attachmentItems, index, imageIndex, gallerySet);
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
			View view = makeDialogItemView(inflater, attachmentItems.get(i), i, density, clickListener, gallerySet);
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
		layoutParams.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams
				.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
		if (C.API_LOLLIPOP) {
			layoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
		}
		View decorView = window.getDecorView();
		decorView.setBackground(null);
		decorView.setPadding(0, 0, 0, 0);
		DialogStack.bindDialogToExpandedScreen(dialog, rootView, expandedScreen);
		dialog.show();
		notifySwitchBackground();
		attachmentDialog = dialog;
		if (postItem != null) {
			uiManager.sendPostItemMessage(postItem, UiManager.MESSAGE_PERFORM_DISPLAY_THUMBNAILS);
		}
	}

	@SuppressLint("InflateParams")
	private View makeDialogItemView(LayoutInflater inflater, final AttachmentItem attachmentItem, int index,
			float density, View.OnClickListener clickListener, final GalleryItem.GallerySet gallerySet) {
		View view = inflater.inflate(R.layout.list_item_attachment, null);
		ViewUtils.makeRoundedCorners(view, (int) (2f * density + 0.5f), true);
		final AttachmentView attachmentView = (AttachmentView) view.findViewById(R.id.thumbnail);
		TextView textView = (TextView) view.findViewById(R.id.attachment_info);
		textView.setBackgroundColor(0xcc222222);
		attachmentItem.displayThumbnail(attachmentView, false, false, true);
		textView.setText(attachmentItem.getDescription(AttachmentItem.FormatMode.TWO_LINES));
		View clickView = view.findViewById(R.id.click_view);
		clickView.setOnClickListener(clickListener);
		clickView.setOnLongClickListener(v -> {
			uiManager.interaction().showThumbnailLongClickDialog(attachmentItem, attachmentView,
					false, gallerySet.getThreadTitle());
			return true;
		});
		clickView.setTag(index);
		return view;
	}

	public void openAttachmentOrDialog(Context context, View imageView, List<AttachmentItem> attachmentItems,
			int imageIndex, GalleryItem.GallerySet gallerySet, PostItem postItem) {
		if (attachmentItems.size() > 1) {
			showAttachmentsGrid(context, attachmentItems, imageIndex, gallerySet, postItem);
		} else {
			openAttachment(context, imageView, attachmentItems, 0, imageIndex, gallerySet);
		}
	}

	public void openAttachment(Context context, View imageView, List<AttachmentItem> attachmentItems, int index,
			int imageIndex, GalleryItem.GallerySet gallerySet) {
		AttachmentItem attachmentItem = attachmentItems.get(index);
		boolean canDownload = attachmentItem.canDownloadToStorage();
		String chanName = attachmentItem.getChanName();
		Uri uri = attachmentItem.getFileUri();
		int type = attachmentItem.getType();
		if (canDownload && type == AttachmentItem.TYPE_AUDIO) {
			AudioPlayerService.start(context, chanName, uri, attachmentItem.getFileName());
		} else if (canDownload && (type == AttachmentItem.TYPE_IMAGE || type == AttachmentItem.TYPE_VIDEO &&
				NavigationUtils.isOpenableVideoExtension(attachmentItem.getExtension()))) {
			NavigationUtils.openGallery(context, imageView, chanName, imageIndex, gallerySet, true, false);
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
				imageLoader.loadImage(icon.uri, chanName, null, false, imageView);
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
		alertDialog.setView(container).show();
		notifySwitchBackground();
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
		final ArrayList<String> archiveChanNames = ChanManager.getInstance().getArhiveMap().get(chanName);
		final SendMultifunctionalTask.State state = new SendMultifunctionalTask.State(SendMultifunctionalTask
				.Operation.ARCHIVE, chanName, boardName, threadNumber, null, null, false);
		state.archiveThreadTitle = threadTitle;
		if (archiveChanNames != null && (canArchiveLocal && archiveChanNames.size() > 0 ||
				archiveChanNames.size() > 1)) {
			String[] items = new String[archiveChanNames.size() + (canArchiveLocal ? 1 : 0)];
			if (canArchiveLocal) {
				items[0] = context.getString(R.string.text_local_archive);
			}
			for (int i = 0; i < archiveChanNames.size(); i++) {
				items[canArchiveLocal ? i + 1 : i] = ChanConfiguration.get(archiveChanNames.get(i)).getTitle();
			}
			new AlertDialog.Builder(context).setItems(items,
					(dialog, which) -> performSendArchiveThreadInternal(state, canArchiveLocal ? which == 0
					? null : archiveChanNames.get(which - 1) : archiveChanNames.get(which), posts))
					.setTitle(R.string.action_archive_add).show();
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

	private void showPerformSendDialog(final SendMultifunctionalTask.State state, String type, String text,
			ArrayList<String> options, final Posts posts, boolean firstTime) {
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
				if (StringUtils.equals(pair.first, type)) {
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
			editText.setText(text);
			if (text != null) {
				editText.setSelection(text.length());
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
				if (options != null && options.contains(option.first)) {
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

		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
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
			}
		}).setNegativeButton(android.R.string.cancel, null).show();
	}

	private class PerformSendCallback implements SendMultifunctionalTask.Callback, SendLocalArchiveTask.Callback,
			DialogInterface.OnCancelListener {
		private final ProgressDialog dialog;

		public PerformSendCallback(int localArchiveMax) {
			Context context = uiManager.getContext();
			dialog = new ProgressDialog(context);
			if (localArchiveMax >= 0) {
				dialog.setMessage(context.getString(R.string.message_processing_data));
				dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				dialog.setMax(localArchiveMax);
			} else {
				dialog.setMessage(context.getString(R.string.message_sending));
			}
			dialog.setCanceledOnTouchOutside(false);
			dialog.setOnCancelListener(this);
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
								archiveThreadNumber, null, state.archiveThreadTitle, false), false);
					} else {
						ToastUtils.show(context, R.string.message_completed);
					}
					break;
				}
			}
		}

		@Override
		public void onSendFail(SendMultifunctionalTask.State state, String type, String text, ArrayList<String> options,
				ErrorItem errorItem) {
			completeTask();
			ToastUtils.show(uiManager.getContext(), errorItem);
			showPerformSendDialog(state, type, text, options, null, false);
		}

		@Override
		public void onLocalArchivationProgressUpdate(int handledPostsCount) {
			dialog.setProgress(handledPostsCount);
		}

		@Override
		public void onLocalArchivationComplete(boolean success, boolean showSuccess) {
			completeTask();
			if (!success) {
				ToastUtils.show(uiManager.getContext(), R.string.message_unknown_error);
			} else if (showSuccess) {
				ToastUtils.show(uiManager.getContext(), R.string.message_completed);
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