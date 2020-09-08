package com.mishiranu.dashchan.ui.navigator.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

public class PostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
		implements CommentTextView.LinkListener, UiManager.PostsProvider {
	public interface Callback {
		void onItemClick(View view, PostItem postItem);
		boolean onItemLongClick(PostItem postItem);
	}

	private enum ViewType {POST, POST_HIDDEN}

	private static final String PAYLOAD_INVALIDATE_COMMENT = "invalidateComment";

	private final Callback callback;
	private final UiManager uiManager;
	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet configurationSet;
	private final CommentTextView.RecyclerKeeper recyclerKeeper;
	private final int bumpLimit;

	private final ArrayList<PostItem> postItems = new ArrayList<>();
	private final HashMap<String, PostItem> postItemsMap = new HashMap<>();
	private final HashSet<String> selected = new HashSet<>();

	private int bumpLimitOrdinalIndex = -1;
	private boolean selection = false;

	public PostsAdapter(Callback callback, String chanName, String boardName, UiManager uiManager,
			Replyable replyable, HidePerformer hidePerformer, HashSet<String> userPostNumbers,
			RecyclerView recyclerView) {
		this.callback = callback;
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(replyable, this, hidePerformer,
				new GalleryItem.GallerySet(true), uiManager.dialog().createStackInstance(), this, userPostNumbers,
				true, false, true, true, true, null);
		recyclerKeeper = new CommentTextView.RecyclerKeeper(recyclerView);
		super.registerAdapterDataObserver(recyclerKeeper);
		bumpLimit = ChanConfiguration.get(chanName).getBumpLimitWithMode(boardName);
	}

	public RecyclerView.ItemDecoration createPostItemDecoration(Context context, int dividerPadding) {
		return new BumpLimitItemDecorator(context, dividerPadding);
	}

	@Override
	public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
		super.registerAdapterDataObserver(observer);

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
		return (postItem.isHidden(configurationSet.hidePerformer) ? ViewType.POST_HIDDEN : ViewType.POST).ordinal();
	}

	private RecyclerView.ViewHolder configureView(RecyclerView.ViewHolder holder) {
		holder.itemView.setOnClickListener(v -> callback.onItemClick(v, getItem(holder.getAdapterPosition())));
		holder.itemView.setOnLongClickListener(v -> callback.onItemLongClick(getItem(holder.getAdapterPosition())));
		return holder;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		switch (ViewType.values()[viewType]) {
			case POST: {
				return configureView(uiManager.view().createPostView(parent, configurationSet));
			}
			case POST_HIDDEN: {
				return configureView(uiManager.view().createPostHiddenView(parent, configurationSet));
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
				UiManager.DemandSet demandSet = this.demandSet;
				demandSet.selection = selection ? selected.contains(postItem.getPostNumber())
						? UiManager.Selection.SELECTED : UiManager.Selection.NOT_SELECTED : UiManager.Selection.DISABLED;
				demandSet.lastInList = position == getItemCount() - 1;
				if (payloads.contains(PAYLOAD_INVALIDATE_COMMENT)) {
					uiManager.view().bindPostViewInvalidateComment(holder);
				} else {
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

	public PostItem getItem(int position) {
		return postItems.get(position);
	}

	public int positionOf(PostItem postItem) {
		return postItems.indexOf(postItem);
	}

	public int findPositionByOrdinalIndex(int ordinalIndex) {
		for (int i = 0; i < getItemCount(); i++) {
			PostItem postItem = getItem(i);
			if (postItem.getOrdinalIndex() == ordinalIndex) {
				return i;
			}
		}
		return -1;
	}

	public int findPositionByPostNumber(String postNumber) {
		for (int i = 0; i < getItemCount(); i++) {
			PostItem postItem = getItem(i);
			if (postItem.getPostNumber().equals(postNumber)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public PostItem findPostItem(String postNumber) {
		return postItemsMap.get(postNumber);
	}

	@NonNull
	@Override
	public Iterator<PostItem> iterator() {
		return new PostsIterator(true, 0);
	}

	public int getExistingPostsCount() {
		for (int i = getItemCount() - 1; i >= 0; i--) {
			PostItem postItem = getItem(i);
			int ordinalIndex = postItem.getOrdinalIndex();
			if (ordinalIndex >= 0) {
				return ordinalIndex + 1;
			}
		}
		return 0;
	}

	public String getLastPostNumber() {
		for (int i = getItemCount() - 1; i >= 0; i--) {
			PostItem postItem = getItem(i);
			if (!postItem.isDeleted()) {
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
			uiManager.dialog().displaySingle(configurationSet, getItem(position));
		} else {
			uiManager.interaction().handleLinkClick(configurationSet, chanName, uri, confirmed);
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
		bumpLimitOrdinalIndex = -1;
		for (int i = 0; i < postItems.size(); i++) {
			PostItem postItem = postItems.get(i);
			if (postItem.isDeleted()) {
				postItem.setOrdinalIndex(PostItem.ORDINAL_INDEX_DELETED);
			} else {
				postItem.setOrdinalIndex(ordinalIndex++);
				if (ordinalIndex == bumpLimit && postItems.get(0).getBumpLimitReachedState(ordinalIndex)
						== PostItem.BumpLimitState.REACHED) {
					bumpLimitOrdinalIndex = ordinalIndex;
				}
			}
		}

		notifyDataSetChanged();
		preloadPosts(0);
	}

	public void invalidateComment(int position) {
		notifyItemChanged(position, PAYLOAD_INVALIDATE_COMMENT);
	}

	public ArrayList<PostItem> clearDeletedPosts() {
		ArrayList<PostItem> deletedPostItems = null;
		for (int i = postItems.size() - 1; i >= 0; i--) {
			PostItem postItem = postItems.get(i);
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
			if (postItem.isDeleted()) {
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

	public void toggleItemSelected(PostItem postItem) {
		String postNumber = postItem.getPostNumber();
		if (selected.contains(postNumber)) {
			selected.remove(postNumber);
		} else if (!postItem.isHiddenUnchecked()) {
			selected.add(postNumber);
		}
		int position = positionOf(postItem);
		notifyItemChanged(position, SimpleViewHolder.EMPTY_PAYLOAD);
	}

	public ArrayList<PostItem> getSelectedItems() {
		ArrayList<PostItem> selected = new ArrayList<>(this.selected.size());
		for (String postNumber : this.selected) {
			PostItem postItem = postItemsMap.get(postNumber);
			if (postNumber != null) {
				selected.add(postItem);
			}
		}
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
		preloadPostItems.addAll(postItems.subList(from, size));
		preloadPostItems.addAll(postItems.subList(0, from));
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
			long time = SystemClock.elapsedRealtime();
			int i = msg.arg1;
			while (i < preloadList.size() && SystemClock.elapsedRealtime() - time < ms) {
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

	public void setHighlightText(Collection<String> highlightText) {
		demandSet.highlightText = highlightText;
		notifyDataSetChanged();
	}

	public Iterable<PostItem> iterate(final boolean ascending, final int from) {
		return () -> new PostsIterator(ascending, from);
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		return configuration.need(!needBumpLimitDividerAbove(position + 1));
	}

	private boolean needBumpLimitDividerAbove(int position) {
		PostItem postItem = position >= 0 && position < getItemCount() ? getItem(position) : null;
		return postItem != null && postItem.getOrdinalIndex() == bumpLimitOrdinalIndex;
	}

	private class BumpLimitItemDecorator extends RecyclerView.ItemDecoration {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Rect rect = new Rect();
		private final int height;
		private final int padding;

		public BumpLimitItemDecorator(Context context, int dividerPadding) {
			paint.setColor(ResourceUtils.getColor(context, R.attr.colorTextError));
			height = (int) (2f * ResourceUtils.obtainDensity(context));
			padding = dividerPadding;
		}

		@Override
		public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
			int childCount = parent.getChildCount();
			int left = parent.getPaddingLeft();
			int right = parent.getWidth() - parent.getPaddingRight();
			for (int i = 0; i < childCount; i++) {
				View view = parent.getChildAt(i);
				int position = parent.getChildAdapterPosition(view);
				if (needBumpLimitDividerAbove(position)) {
					parent.getDecoratedBoundsWithMargins(view, rect);
					c.drawRect(left + padding, rect.top, right - padding, rect.top + height, paint);
				}
			}
		}

		@Override
		public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
				@NonNull RecyclerView.State state) {
			int position = parent.getChildAdapterPosition(view);
			if (needBumpLimitDividerAbove(position)) {
				outRect.set(0, height, 0, 0);
			} else {
				outRect.set(0, 0, 0, 0);
			}
		}
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
			int count = getItemCount();
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
			return nextInternal();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
