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

package com.mishiranu.dashchan.ui.navigator.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadPostsTask;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.HidePerformer;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.BaseAdapterNotifier;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class PostsAdapter extends BaseAdapter implements CommentTextView.LinkListener, BusyScrollListener.Callback,
		UiManager.PostsProvider {
	private static final int ITEM_VIEW_TYPE_POST = 0;
	private static final int ITEM_VIEW_TYPE_HIDDEN_POST = 1;

	private final BaseAdapterNotifier notifier = new BaseAdapterNotifier(this);

	private final ArrayList<PostItem> postItems = new ArrayList<>();
	private final HashMap<String, PostItem> postItemsMap = new HashMap<>();
	private final HashSet<PostItem> selected = new HashSet<>();

	private final UiManager uiManager;
	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet configurationSet;
	private final CommentTextView.ListSelectionKeeper listSelectionKeeper;

	private final View bumpLimitDivider;
	private final int bumpLimit;

	private boolean selection = false;

	public PostsAdapter(Context context, String chanName, String boardName, UiManager uiManager,
			Replyable replyable, HidePerformer hidePerformer, HashSet<String> userPostNumbers, ListView listView) {
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(replyable, this, hidePerformer,
				new GalleryItem.GallerySet(true), this, userPostNumbers, true, false, true, true, null);
		listSelectionKeeper = new CommentTextView.ListSelectionKeeper(listView);
		float density = ResourceUtils.obtainDensity(context);
		FrameLayout frameLayout = new FrameLayout(context);
		frameLayout.setPadding((int) (12f * density), 0, (int) (12f * density), 0);
		View view = new View(context);
		view.setMinimumHeight((int) (2f * density));
		view.setBackgroundColor(ResourceUtils.getColor(context, R.attr.colorTextError));
		frameLayout.addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		bumpLimitDivider = frameLayout;
		bumpLimit = ChanConfiguration.get(chanName).getBumpLimitWithMode(boardName);
	}

	@Override
	public void notifyDataSetChanged() {
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
	public boolean isEnabled(int position) {
		return getItem(position) != null;
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public int getItemViewType(int position) {
		PostItem postItem = getItem(position);
		return postItem != null ? postItem.isHidden(configurationSet.hidePerformer)
				? ITEM_VIEW_TYPE_HIDDEN_POST : ITEM_VIEW_TYPE_POST : IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		PostItem postItem = getItem(position);
		if (postItem == null) {
			return bumpLimitDivider;
		}
		if (postItem.isHidden(configurationSet.hidePerformer)) {
			convertView = uiManager.view().getPostHiddenView(postItem, convertView, parent);
		} else {
			UiManager.DemandSet demandSet = this.demandSet;
			demandSet.selectionMode = selection ? selected.contains(postItem) ? UiManager.SELECTION_SELECTED
					: UiManager.SELECTION_NOT_SELECTED : UiManager.SELECTION_DISABLED;
			demandSet.lastInList = position == getCount() - 1;
			convertView = uiManager.view().getPostView(postItem, convertView, parent, demandSet, configurationSet);
		}
		return convertView;
	}

	public int indexOf(PostItem postItem) {
		return postItems.indexOf(postItem);
	}

	public int findPositionByOrdinalIndex(int ordinalIndex) {
		for (int i = 0; i < getCount(); i++) {
			PostItem postItem = getItem(i);
			if (postItem != null && postItem.getOrdinalIndex() == ordinalIndex) {
				return i;
			}
		}
		return -1;
	}

	public int findPositionByPostNumber(String postNumber) {
		for (int i = 0; i < getCount(); i++) {
			PostItem postItem = getItem(i);
			if (postItem != null && postItem.getPostNumber().equals(postNumber)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public PostItem findPostItem(String postNumber) {
		return postItemsMap.get(postNumber);
	}

	@Override
	public Iterator<PostItem> iterator() {
		return new PostsIterator(true, 0);
	}

	public int getExistingPostsCount() {
		for (int i = getCount() - 1; i >= 0; i--) {
			PostItem postItem = getItem(i);
			if (postItem != null) {
				int ordinalIndex = postItem.getOrdinalIndex();
				if (ordinalIndex >= 0) {
					return ordinalIndex + 1;
				}
			}
		}
		return 0;
	}

	public String getLastPostNumber() {
		for (int i = postItems.size() - 1; i >= 0; i--) {
			PostItem postItem = postItems.get(i);
			if (postItem != null && !postItem.isDeleted()) {
				return postItem.getPostNumber();
			}
		}
		return null;
	}

	public UiManager.ConfigurationSet getConfigurationSet() {
		return configurationSet;
	}

	@Override
	public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed) {
		PostItem originalPostItem = getItem(0);
		ChanLocator locator = ChanLocator.get(chanName);
		String boardName = originalPostItem.getBoardName();
		String threadNumber = originalPostItem.getThreadNumber();
		if (chanName != null && locator.safe(false).isThreadUri(uri)
				&& StringUtils.equals(boardName, locator.safe(false).getBoardName(uri))
				&& StringUtils.equals(threadNumber, locator.safe(false).getThreadNumber(uri))) {
			String postNumber = locator.safe(false).getPostNumber(uri);
			int position = StringUtils.isEmpty(postNumber) ? 0 : findPositionByPostNumber(postNumber);
			if (position == -1) {
				ToastUtils.show(view.getContext(), R.string.message_post_not_found);
				return;
			}
			uiManager.dialog().displaySingle(getItem(position), configurationSet);
		} else {
			uiManager.interaction().handleLinkClick(chanName, uri, confirmed);
		}
	}

	@Override
	public void onLinkLongClick(CommentTextView view, String chanName, Uri uri) {
		uiManager.interaction().handleLinkLongClick(uri);
	}

	public void setItems(ArrayList<ReadPostsTask.Patch> patches, boolean maySkipHandlingReferences) {
		postItems.clear();
		postItemsMap.clear();
		configurationSet.gallerySet.clear();
		insertItemsInternal(patches, maySkipHandlingReferences);
	}

	public void mergeItems(ArrayList<ReadPostsTask.Patch> patches) {
		insertItemsInternal(patches, false);
	}

	private void insertItemsInternal(ArrayList<ReadPostsTask.Patch> patches, boolean maySkipHandlingReferences) {
		cancelPreloading();
		postItems.remove(null);
		boolean invalidateImages = false;
		boolean invalidateReferences = false;
		int startAppendIndex = -1;

		for (ReadPostsTask.Patch patch : patches) {
			PostItem postItem = patch.postItem;
			int index = patch.index;
			if (!patch.replaceAtIndex) {
				boolean append = index == postItems.size();
				postItems.add(index, postItem);
				postItemsMap.put(postItem.getPostNumber(), postItem);
				if (append) {
					if (startAppendIndex == -1) {
						startAppendIndex = index;
						if (maySkipHandlingReferences && startAppendIndex != 0) {
							invalidateReferences = true;
							maySkipHandlingReferences = false;
						}
					}
				} else {
					invalidateImages = true;
					invalidateReferences = true;
				}
			} else {
				PostItem existingPostItem = postItems.get(index);
				postItems.set(index, postItem);
				postItemsMap.put(postItem.getPostNumber(), postItem);
				postItem.setExpanded(existingPostItem.isExpanded());
				invalidateImages = true;
				if (!invalidateReferences && !StringUtils.equals(postItem.getRawComment(),
						existingPostItem.getRawComment())) {
					// Invalidate all references if comment was changed
					invalidateReferences = true;
				} else {
					LinkedHashSet<String> referencesFrom = existingPostItem.getReferencesFrom();
					if (referencesFrom != null) {
						for (String postNumber : referencesFrom) {
							postItem.addReferenceFrom(postNumber);
						}
					}
				}
			}
		}

		int imagesStartHandlingIndex = startAppendIndex;
		if (invalidateImages) {
			configurationSet.gallerySet.clear();
			imagesStartHandlingIndex = 0;
		}
		if (imagesStartHandlingIndex >= 0) {
			for (int i = imagesStartHandlingIndex; i < postItems.size(); i++) {
				PostItem postItem = postItems.get(i);
				if (i == 0) {
					configurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
				}
				configurationSet.gallerySet.add(postItem.getAttachmentItems());
			}
		}

		int referencesStartHandleIndex = startAppendIndex;
		if (invalidateReferences) {
			for (PostItem postItem : postItems) {
				postItem.clearReferencesFrom();
			}
			referencesStartHandleIndex = 0;
		}
		if (referencesStartHandleIndex >= 0 && !maySkipHandlingReferences) {
			for (int i = referencesStartHandleIndex; i < postItems.size(); i++) {
				PostItem postItem = postItems.get(i);
				HashSet<String> referencesTo = postItem.getReferencesTo();
				if (referencesTo != null) {
					for (String postNumber : referencesTo) {
						PostItem foundPostItem = postItemsMap.get(postNumber);
						if (foundPostItem != null) {
							foundPostItem.addReferenceFrom(postItem.getPostNumber());
						}
					}
				}
			}
		}

		int ordinalIndex = 0;
		boolean appendBumpLimitDelimiter = false;
		for (int i = 0; i < postItems.size(); i++) {
			if (appendBumpLimitDelimiter) {
				appendBumpLimitDelimiter = false;
				postItems.add(i, null);
				i++;
			}
			PostItem postItem = postItems.get(i);
			if (postItem.isDeleted()) {
				postItem.setOrdinalIndex(PostItem.ORDINAL_INDEX_DELETED);
			} else {
				postItem.setOrdinalIndex(ordinalIndex++);
				if (ordinalIndex == bumpLimit && postItems.get(0).getBumpLimitReachedState(ordinalIndex)
						== PostItem.BUMP_LIMIT_REACHED) {
					appendBumpLimitDelimiter = true;
				}
			}
		}

		notifyDataSetChanged();
		preloadPosts(0);
	}

	public ArrayList<PostItem> clearDeletedPosts() {
		ArrayList<PostItem> deletedPostItems = null;
		for (int i = postItems.size() - 1; i >= 0; i--) {
			PostItem postItem = postItems.get(i);
			if (postItem != null) {
				if (postItem.isDeleted()) {
					HashSet<String> referencesTo = postItem.getReferencesTo();
					if (referencesTo != null) {
						for (String postNumber : referencesTo) {
							PostItem foundPostItem = postItemsMap.get(postNumber);
							if (foundPostItem != null) {
								foundPostItem.removeReferenceFrom(postItem.getPostNumber());
							}
						}
					}
					postItems.remove(i);
					postItemsMap.remove(postItem.getPostNumber());
					if (deletedPostItems == null) {
						deletedPostItems = new ArrayList<>();
					}
					deletedPostItems.add(postItem);
				}
			}
		}
		if (deletedPostItems != null) {
			configurationSet.gallerySet.clear();
			boolean originalPost = true;
			for (PostItem postItem : this) {
				if (originalPost) {
					configurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
					originalPost = false;
				}
				configurationSet.gallerySet.add(postItem.getAttachmentItems());
			}
			notifyDataSetChanged();
		}
		return deletedPostItems;
	}

	public boolean hasDeletedPosts() {
		for (PostItem postItem : postItems) {
			if (postItem != null && postItem.isDeleted()) {
				return true;
			}
		}
		return false;
	}

	public void setSelectionModeEnabled(boolean enabled) {
		selection = enabled;
		if (!enabled) {
			selected.clear();
		}
		notifyDataSetChanged();
	}

	public void toggleItemSelected(ListView listView, int position) {
		PostItem postItem = getItem(position);
		if (postItem != null && !postItem.isHiddenUnchecked()) {
			if (selected.contains(postItem)) {
				selected.remove(postItem);
			} else {
				selected.add(postItem);
			}
			int index = position - listView.getFirstVisiblePosition();
			getView(position, listView.getChildAt(index), listView);
		}
	}

	public ArrayList<PostItem> getSelectedItems() {
		ArrayList<PostItem> selected = new ArrayList<>(this.selected);
		Collections.sort(selected);
		return selected;
	}

	public int getSelectedCount() {
		return selected.size();
	}

	public void cancelPreloading() {
		preloadHandler.removeMessages(0);
	}

	public void preloadPosts(int from) {
		ArrayList<PostItem> preloadPostItems = new ArrayList<>();
		ArrayList<PostItem> postItems = this.postItems;
		int size = postItems.size();
		from = Math.max(0, Math.min(size, from));
		preloadPostItems.ensureCapacity(size);
		// Ordered preloading
		for (int i = from; i < size; i++) {
			PostItem postItem = postItems.get(i);
			if (postItem != null) {
				preloadPostItems.add(postItem);
			}
		}
		for (int i = 0; i < from; i++) {
			PostItem postItem = postItems.get(i);
			if (postItem != null) {
				preloadPostItems.add(postItem);
			}
		}
		cancelPreloading();
		preloadHandler.obtainMessage(0, 0, 0, preloadPostItems).sendToTarget();
	}

	public void preloadPosts(Collection<PostItem> postItems, PreloadFinishCallback callback) {
		if (postItems != null && !postItems.isEmpty()) {
			new Handler(Looper.getMainLooper(), new PreloadCallback(callback))
					.obtainMessage(0, 0, 0, postItems).sendToTarget();
		}
	}

	private final Handler preloadHandler = new Handler(Looper.getMainLooper(), new PreloadCallback(null));

	public interface PreloadFinishCallback {
		public void onFinish();
	}

	private class PreloadCallback implements Handler.Callback {
		private final PreloadFinishCallback callback;

		public PreloadCallback(PreloadFinishCallback callback) {
			this.callback = callback;
		}

		@Override
		public boolean handleMessage(Message msg) {
			// Take only 8ms per frame for preloading in main thread
			final int ms = 8;
			HidePerformer hidePerformer = configurationSet.hidePerformer;
			@SuppressWarnings("unchecked") ArrayList<PostItem> preloadList = (ArrayList<PostItem>) msg.obj;
			long time = System.currentTimeMillis();
			int i = msg.arg1;
			while (i < preloadList.size() && System.currentTimeMillis() - time < ms) {
				PostItem postItem = preloadList.get(i++);
				postItem.getComment();
				postItem.isHidden(hidePerformer);
			}
			if (i < preloadList.size()) {
				msg.getTarget().obtainMessage(0, i, 0, preloadList).sendToTarget();
			} else if (callback != null) {
				callback.onFinish();
			}
			return true;
		}
	}

	public void invalidateHidden() {
		cancelPreloading();
		for (PostItem postItem : this) {
			postItem.invalidateHidden();
		}
	}

	public void cleanup() {
		cancelPreloading();
	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView) {
		uiManager.view().handleListViewBusyStateChange(isBusy, listView, demandSet);
	}

	public Iterable<PostItem> iterate(final boolean ascending, final int from) {
		return () -> new PostsIterator(ascending, from);
	}

	private class PostsIterator implements Iterator<PostItem> {
		private final boolean ascending;
		private int position;

		private PostsIterator(boolean ascending, int from) {
			this.ascending = ascending;
			position = from;
		}

		@Override
		public boolean hasNext() {
			int count = getCount();
			return ascending ? position < count : position >= 0;
		}

		private PostItem nextInternal() {
			PostItem postItem = getItem(position);
			if (ascending) {
				position++;
			} else {
				position--;
			}
			return postItem;
		}

		@Override
		public PostItem next() {
			PostItem postItem = nextInternal();
			if (postItem == null) postItem = nextInternal(); // Bump limit divider is a null item
			return postItem;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}