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
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.manager.ViewUnit;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PostsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
		implements CommentTextView.LinkListener, UiManager.PostsProvider, HidePerformer.PostsProvider {
	public interface Callback extends ListViewUtils.ClickCallback<PostItem, RecyclerView.ViewHolder> {
		void onItemClick(View view, PostItem postItem);
		boolean onItemLongClick(PostItem postItem);

		@Override
		default boolean onItemClick(RecyclerView.ViewHolder holder,
				int position, PostItem postItem, boolean longClick) {
			if (longClick) {
				return onItemLongClick(postItem);
			} else {
				onItemClick(holder.itemView, postItem);
				return true;
			}
		}
	}

	private static final String PAYLOAD_INVALIDATE_COMMENT = "invalidateComment";

	private final UiManager uiManager;
	private final UiManager.ConfigurationSet configurationSet;
	private final UiManager.DemandSet demandSet = new UiManager.DemandSet();
	private final GalleryItem.Set gallerySet = new GalleryItem.Set(true);
	private final CommentTextView.RecyclerKeeper recyclerKeeper;

	private final ArrayList<PostNumber> postNumbers = new ArrayList<>();
	private final Map<PostNumber, PostItem> postItemsMap;
	private final HashSet<PostNumber> selected = new HashSet<>();

	private int bumpLimitOrdinalIndex = PostItem.ORDINAL_INDEX_NONE;
	private boolean selection = false;

	public PostsAdapter(Callback callback, String chanName, UiManager uiManager, Replyable replyable,
			UiManager.PostStateProvider postStateProvider, FragmentManager fragmentManager, RecyclerView recyclerView,
			Map<PostNumber, PostItem> postItemsMap) {
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(chanName, replyable, this, postStateProvider,
				gallerySet, fragmentManager, uiManager.dialog().createStackInstance(), this, callback,
				true, false, true, true, true, null);
		recyclerKeeper = new CommentTextView.RecyclerKeeper(recyclerView);
		super.registerAdapterDataObserver(recyclerKeeper);
		this.postItemsMap = postItemsMap;
		postNumbers.addAll(postItemsMap.keySet());
		Collections.sort(postNumbers);
		preloadPosts(0);
		for (PostItem postItem : postItemsMap.values()) {
			if (postItem.isOriginalPost()) {
				gallerySet.setThreadTitle(postItem.getSubjectOrComment());
			}
			gallerySet.put(postItem.getPostNumber(), postItem.getAttachmentItems());
		}
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
		return postNumbers.size();
	}

	@Override
	public int getItemViewType(int position) {
		PostItem postItem = getItem(position);
		return (configurationSet.postStateProvider.isHiddenResolve(postItem)
				? ViewUnit.ViewType.POST_HIDDEN : ViewUnit.ViewType.POST).ordinal();
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
				UiManager.DemandSet demandSet = this.demandSet;
				demandSet.selection = selection ? selected.contains(postItem.getPostNumber())
						? UiManager.Selection.SELECTED : UiManager.Selection.NOT_SELECTED : UiManager.Selection.DISABLED;
				demandSet.lastInList = position == getItemCount() - 1;
				if (payloads.isEmpty() || payloads.contains(SimpleViewHolder.EMPTY_PAYLOAD)) {
					uiManager.view().bindPostView(holder, postItem, configurationSet, demandSet);
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
				uiManager.view().bindPostHiddenView(holder, postItem, configurationSet);
				break;
			}
		}
	}

	public List<PostItem> copyItems() {
		return new ArrayList<>(postItemsMap.values());
	}

	public PostItem getItem(int position) {
		return postItemsMap.get(postNumbers.get(position));
	}

	public int positionOfPostNumber(PostNumber postNumber) {
		return Collections.binarySearch(postNumbers, postNumber);
	}

	public int positionOfOrdinalIndex(int ordinalIndex) {
		for (int i = 0; i < getItemCount(); i++) {
			PostItem postItem = getItem(i);
			if (postItem.getOrdinalIndex() == ordinalIndex) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public PostItem findPostItem(PostNumber postNumber) {
		return postItemsMap.get(postNumber);
	}

	@NonNull
	@Override
	public Iterator<PostItem> iterator() {
		return new PostsIterator(true, 0);
	}

	public GalleryItem.Set getGallerySet() {
		return gallerySet;
	}

	public UiManager.ConfigurationSet getConfigurationSet() {
		return configurationSet;
	}

	@Override
	public void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed) {
		PostItem originalPostItem = getItem(0);
		Chan chan = Chan.get(extra.chanName);
		String boardName = originalPostItem.getBoardName();
		String threadNumber = originalPostItem.getThreadNumber();
		if (extra.chanName != null && chan.locator.safe(false).isThreadUri(uri)
				&& (extra.inBoardLink || CommonUtils.equals(boardName, chan.locator.safe(false).getBoardName(uri)))
				&& CommonUtils.equals(threadNumber, chan.locator.safe(false).getThreadNumber(uri))) {
			PostNumber postNumber = chan.locator.safe(false).getPostNumber(uri);
			int position = postNumber == null ? 0 : positionOfPostNumber(postNumber);
			if (position < 0) {
				ClickableToast.show(R.string.post_is_not_found);
				return;
			}
			uiManager.dialog().displaySingle(configurationSet, getItem(position));
		} else {
			uiManager.interaction().handleLinkClick(configurationSet, uri, extra, confirmed);
		}
	}

	@Override
	public void onLinkLongClick(CommentTextView view, Uri uri, Extra extra) {
		uiManager.interaction().handleLinkLongClick(configurationSet, uri);
	}

	private void removeOldReferences(Collection<PostNumber> changedOrRemoved) {
		for (PostNumber postNumber : changedOrRemoved) {
			PostItem oldPostItem = postItemsMap.get(postNumber);
			if (oldPostItem != null) {
				gallerySet.remove(oldPostItem.getPostNumber());
				for (PostNumber referenceTo : oldPostItem.getReferencesTo()) {
					PostItem referenced = postItemsMap.get(referenceTo);
					if (referenced != null) {
						referenced.removeReferenceFrom(oldPostItem.getPostNumber());
					}
				}
			}
		}
	}

	public void insertItems(Map<PostNumber, PostItem> changed, Collection<PostNumber> removed) {
		cancelPreloading();

		removeOldReferences(changed.keySet());
		removeOldReferences(removed);
		for (PostItem postItem : changed.values()) {
			PostItem oldPostItem = postItemsMap.get(postItem.getPostNumber());
			if (oldPostItem != null) {
				for (PostNumber postNumber : oldPostItem.getReferencesFrom()) {
					if (!changed.containsKey(postNumber)) {
						postItem.addReferenceFrom(postNumber);
					}
				}
			}
		}

		postItemsMap.putAll(changed);
		postItemsMap.keySet().removeAll(removed);
		postNumbers.clear();
		postNumbers.addAll(postItemsMap.keySet());
		Collections.sort(postNumbers);

		for (PostItem postItem : changed.values()) {
			if (postItem.isOriginalPost()) {
				gallerySet.setThreadTitle(postItem.getSubjectOrComment());
			}
			gallerySet.put(postItem.getPostNumber(), postItem.getAttachmentItems());
			for (PostNumber referenceTo : postItem.getReferencesTo()) {
				PostItem referenced = postItemsMap.get(referenceTo);
				if (referenced != null) {
					referenced.addReferenceFrom(postItem.getPostNumber());
				}
			}
		}

		int ordinalIndex = 0;
		bumpLimitOrdinalIndex = PostItem.ORDINAL_INDEX_NONE;
		Chan chan = Chan.get(configurationSet.chanName);
		int bumpLimit = getItemCount() > 0 ? chan.configuration.getBumpLimitWithMode(getItem(0).getBoardName()) : -1;
		for (PostItem postItem : iterate(true, 0)) {
			if (postItem.isDeleted()) {
				postItem.setOrdinalIndex(PostItem.ORDINAL_INDEX_DELETED);
			} else {
				postItem.setOrdinalIndex(ordinalIndex++);
				if (ordinalIndex == bumpLimit && getItem(0).getBumpLimitReachedState(chan, ordinalIndex) ==
						PostItem.BumpLimitState.REACHED) {
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

	public void reloadAttachment(int position, AttachmentItem attachmentItem) {
		notifyItemChanged(position, attachmentItem);
	}

	public boolean clearDeletedPosts() {
		boolean removed = false;
		Iterator<PostItem> iterator = postItemsMap.values().iterator();
		while (iterator.hasNext()) {
			PostItem postItem = iterator.next();
			if (postItem.isDeleted()) {
				if (!removed) {
					removed = true;
					cancelPreloading();
				}
				for (PostNumber referenceTo : postItem.getReferencesTo()) {
					PostItem referenced = postItemsMap.get(referenceTo);
					if (referenced != null) {
						referenced.removeReferenceFrom(postItem.getPostNumber());
					}
				}
				gallerySet.remove(postItem.getPostNumber());
				iterator.remove();
			}
		}
		if (removed) {
			postNumbers.clear();
			postNumbers.addAll(postItemsMap.keySet());
			Collections.sort(postNumbers);
			notifyDataSetChanged();
		}
		return removed;
	}

	public boolean hasOldPosts() {
		return getItemCount() >= 2 && getItem(0).isCyclical() && getItem(1).isDeleted();
	}

	public boolean hasDeletedPosts() {
		for (PostItem postItem : this) {
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
		PostNumber postNumber = postItem.getPostNumber();
		if (selected.contains(postNumber)) {
			selected.remove(postNumber);
		} else {
			if (!configurationSet.postStateProvider.isHiddenResolve(postItem)) {
				selected.add(postNumber);
			}
		}
		int position = positionOfPostNumber(postNumber);
		notifyItemChanged(position, SimpleViewHolder.EMPTY_PAYLOAD);
	}

	public ArrayList<PostItem> getSelectedItems() {
		ArrayList<PostItem> selected = new ArrayList<>(this.selected.size());
		for (PostNumber postNumber : this.selected) {
			PostItem postItem = postItemsMap.get(postNumber);
			if (postItem != null) {
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

	private static class PreloadIterator implements Iterator<PostItem> {
		private final Iterator<PostItem> ascending;
		private final Iterator<PostItem> descending;

		private boolean lastAscending;

		public PreloadIterator(Iterator<PostItem> ascending, Iterator<PostItem> descending) {
			this.ascending = ascending;
			this.descending = descending;
		}

		@Override
		public boolean hasNext() {
			return ascending.hasNext() || descending.hasNext();
		}

		@Override
		public PostItem next() {
			if (lastAscending) {
				lastAscending = false;
				return (descending.hasNext() ? descending : ascending).next();
			} else {
				lastAscending = true;
				return (ascending.hasNext() ? ascending : descending).next();
			}
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public void preloadPosts(PostNumber fromPostNumber) {
		int position = fromPostNumber != null ? positionOfPostNumber(fromPostNumber) : -1;
		if (position >= 0) {
			preloadPosts(position);
		}
	}

	public void preloadPosts(int from) {
		if (from >= 0 && from < getItemCount()) {
			cancelPreloading();
			// Preload to both sides
			Iterator<PostItem> ascending = new PostsIterator(true, from);
			Iterator<PostItem> descending = new PostsIterator(false, from);
			preloadHandler.obtainMessage(0, 0, 0, new PreloadIterator(ascending, descending)).sendToTarget();
		}
	}

	private final Handler preloadHandler = new Handler(Looper.getMainLooper(), new PreloadCallback());

	private class PreloadCallback implements Handler.Callback {
		@Override
		public boolean handleMessage(Message msg) {
			// Take only 8ms per frame for preloading in main thread
			PreloadIterator iterator = (PreloadIterator) msg.obj;
			Chan chan = Chan.get(configurationSet.chanName);
			long time = SystemClock.elapsedRealtime();
			while (SystemClock.elapsedRealtime() - time < ConcurrentUtils.HALF_FRAME_TIME_MS && iterator.hasNext()) {
				PostItem postItem = iterator.next();
				configurationSet.postStateProvider.isHiddenResolve(postItem);
				postItem.getComment(chan);
			}
			if (iterator.hasNext()) {
				msg.getTarget().obtainMessage(0, 0, 0, iterator).sendToTarget();
			}
			return true;
		}
	}

	public void invalidateHidden() {
		cancelPreloading();
		for (PostItem postItem : this) {
			postItem.setHidden(PostItem.HideState.UNDEFINED, null);
		}
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
		return postItem != null && bumpLimitOrdinalIndex >= 0 && postItem.getOrdinalIndex() == bumpLimitOrdinalIndex;
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
