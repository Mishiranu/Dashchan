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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

import android.content.Context;
import android.net.Uri;
import android.os.Process;
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
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.async.ReadPostsTask;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.widget.BaseAdapterNotifier;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.callback.BusyScrollListener;

public class PostsAdapter extends BaseAdapter implements CommentTextView.LinkListener, BusyScrollListener.Callback,
		UiManager.PostsProvider
{
	private static final int ITEM_VIEW_TYPE_POST = 0;
	private static final int ITEM_VIEW_TYPE_HIDDEN_POST = 1;

	private final BaseAdapterNotifier mNotifier = new BaseAdapterNotifier(this);

	private final ArrayList<PostItem> mPostItems = new ArrayList<>();
	private final HashMap<String, PostItem> mPostItemsMap = new HashMap<>();
	private final HashSet<PostItem> mSelected = new HashSet<>();

	private final UiManager mUiManager;
	private final UiManager.DemandSet mDemandSet = new UiManager.DemandSet();
	private final UiManager.ConfigurationSet mConfigurationSet;
	private final CommentTextView.ListSelectionKeeper mListSelectionKeeper;

	private final View mBumpLimitDivider;
	private final int mBumpLimit;

	private boolean mSelection = false;

	public PostsAdapter(Context context, String chanName, String boardName, UiManager uiManager,
			Replyable replyable, HidePerformer hidePerformer, HashSet<String> userPostNumbers, ListView listView)
	{
		mUiManager = uiManager;
		mConfigurationSet = new UiManager.ConfigurationSet(replyable, this, hidePerformer,
				new GalleryItem.GallerySet(true), this, userPostNumbers, true, false, true, true, null);
		mListSelectionKeeper = new CommentTextView.ListSelectionKeeper(listView);
		float density = ResourceUtils.obtainDensity(context);
		FrameLayout frameLayout = new FrameLayout(context);
		frameLayout.setPadding((int) (12f * density), 0, (int) (12f * density), 0);
		View view = new View(context);
		view.setMinimumHeight((int) (2f * density));
		view.setBackgroundColor(ResourceUtils.getColor(context, R.attr.colorTextError));
		frameLayout.addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		mBumpLimitDivider = frameLayout;
		mBumpLimit = ChanConfiguration.get(chanName).getBumpLimitWithMode(boardName);
	}

	@Override
	public void notifyDataSetChanged()
	{
		mListSelectionKeeper.onBeforeNotifyDataSetChanged();
		super.notifyDataSetChanged();
		mListSelectionKeeper.onAfterNotifyDataSetChanged();
	}

	public void postNotifyDataSetChanged()
	{
		mNotifier.postNotifyDataSetChanged();
	}

	@Override
	public int getViewTypeCount()
	{
		return 2;
	}

	@Override
	public int getCount()
	{
		return mPostItems.size();
	}

	@Override
	public PostItem getItem(int position)
	{
		return mPostItems.get(position);
	}

	@Override
	public long getItemId(int position)
	{
		return 0;
	}

	@Override
	public boolean isEnabled(int position)
	{
		return getItem(position) != null;
	}

	@Override
	public boolean areAllItemsEnabled()
	{
		return false;
	}

	@Override
	public int getItemViewType(int position)
	{
		PostItem postItem = getItem(position);
		return postItem != null ? postItem.isHidden(mConfigurationSet.hidePerformer)
				? ITEM_VIEW_TYPE_HIDDEN_POST : ITEM_VIEW_TYPE_POST : IGNORE_ITEM_VIEW_TYPE;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		PostItem postItem = getItem(position);
		if (postItem == null) return mBumpLimitDivider;
		if (postItem.isHidden(mConfigurationSet.hidePerformer))
		{
			convertView = mUiManager.view().getPostHiddenView(postItem, convertView, parent);
		}
		else
		{
			UiManager.DemandSet demandSet = mDemandSet;
			demandSet.selectionMode = mSelection ? mSelected.contains(postItem) ? UiManager.SELECTION_SELECTED
					: UiManager.SELECTION_NOT_SELECTED : UiManager.SELECTION_DISABLED;
			demandSet.lastInList = position == getCount() - 1;
			convertView = mUiManager.view().getPostView(postItem, convertView, parent, demandSet, mConfigurationSet);
		}
		return convertView;
	}

	public int indexOf(PostItem postItem)
	{
		return mPostItems.indexOf(postItem);
	}

	public int findPositionByOrdinalIndex(int ordinalIndex)
	{
		for (int i = 0; i < getCount(); i++)
		{
			PostItem postItem = getItem(i);
			if (postItem != null && postItem.getOrdinalIndex() == ordinalIndex) return i;
		}
		return -1;
	}

	public int findPositionByPostNumber(String postNumber)
	{
		for (int i = 0; i < getCount(); i++)
		{
			PostItem postItem = getItem(i);
			if (postItem != null && postItem.getPostNumber().equals(postNumber)) return i;
		}
		return -1;
	}

	@Override
	public PostItem findPostItem(String postNumber)
	{
		return mPostItemsMap.get(postNumber);
	}

	@Override
	public Iterator<PostItem> iterator()
	{
		return new PostsIterator(true, 0);
	}

	public int getExistingPostsCount()
	{
		for (int i = getCount() - 1; i >= 0; i--)
		{
			PostItem postItem = getItem(i);
			if (postItem != null)
			{
				int ordinalIndex = postItem.getOrdinalIndex();
				if (ordinalIndex >= 0) return ordinalIndex + 1;
			}
		}
		return 0;
	}

	public String getLastPostNumber()
	{
		for (int i = mPostItems.size() - 1; i >= 0; i--)
		{
			PostItem postItem = mPostItems.get(i);
			if (postItem != null && !postItem.isDeleted()) return postItem.getPostNumber();
		}
		return null;
	}

	public GalleryItem.GallerySet getGallerySet()
	{
		return mConfigurationSet.gallerySet;
	}

	@Override
	public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed)
	{
		PostItem originalPostItem = getItem(0);
		ChanLocator locator = ChanLocator.get(chanName);
		String boardName = originalPostItem.getBoardName();
		String threadNumber = originalPostItem.getThreadNumber();
		if (chanName != null && locator.safe(false).isThreadUri(uri)
				&& StringUtils.equals(boardName, locator.safe(false).getBoardName(uri))
				&& StringUtils.equals(threadNumber, locator.safe(false).getThreadNumber(uri)))
		{
			String postNumber = locator.safe(false).getPostNumber(uri);
			int position = StringUtils.isEmpty(postNumber) ? 0 : findPositionByPostNumber(postNumber);
			if (position == -1)
			{
				ToastUtils.show(view.getContext(), R.string.message_post_not_found);
				return;
			}
			mUiManager.dialog().displaySingle(getItem(position), mConfigurationSet);
		}
		else mUiManager.interaction().handleLinkClick(chanName, uri, confirmed);
	}

	@Override
	public void onLinkLongClick(CommentTextView view, String chanName, Uri uri)
	{
		mUiManager.interaction().handleLinkLongClick(uri);
	}

	public void setItems(ArrayList<ReadPostsTask.Patch> patches, boolean maySkipHandlingReferences)
	{
		mPostItems.clear();
		mPostItemsMap.clear();
		mConfigurationSet.gallerySet.clear();
		insertItemsInternal(patches, maySkipHandlingReferences);
	}

	public void mergeItems(ArrayList<ReadPostsTask.Patch> patches)
	{
		insertItemsInternal(patches, false);
	}

	private void insertItemsInternal(ArrayList<ReadPostsTask.Patch> patches, boolean maySkipHandlingReferences)
	{
		cancelPreloading();
		mPostItems.remove(null);
		boolean invalidateImages = false;
		boolean invalidateReferences = false;
		int startAppendIndex = -1;

		for (ReadPostsTask.Patch patch : patches)
		{
			PostItem postItem = patch.postItem;
			int index = patch.index;
			if (!patch.replaceAtIndex)
			{
				boolean append = index == mPostItems.size();
				mPostItems.add(index, postItem);
				mPostItemsMap.put(postItem.getPostNumber(), postItem);
				if (append)
				{
					if (startAppendIndex == -1)
					{
						startAppendIndex = index;
						if (maySkipHandlingReferences && startAppendIndex != 0)
						{
							invalidateReferences = true;
							maySkipHandlingReferences = false;
						}
					}
				}
				else
				{
					invalidateImages = true;
					invalidateReferences = true;
				}
			}
			else
			{
				PostItem existingPostItem = mPostItems.get(index);
				mPostItems.set(index, postItem);
				mPostItemsMap.put(postItem.getPostNumber(), postItem);
				postItem.setExpanded(existingPostItem.isExpanded());
				invalidateImages = true;
				if (!invalidateReferences && !StringUtils.equals(postItem.getRawComment(),
						existingPostItem.getRawComment()))
				{
					// Invalidate all references if comment was changed
					invalidateReferences = true;
				}
				else
				{
					LinkedHashSet<String> referencesFrom = existingPostItem.getReferencesFrom();
					if (referencesFrom != null)
					{
						for (String postNumber : referencesFrom) postItem.addReferenceFrom(postNumber);
					}
				}
			}
		}

		int imagesStartHandlingIndex = startAppendIndex;
		if (invalidateImages)
		{
			mConfigurationSet.gallerySet.clear();
			imagesStartHandlingIndex = 0;
		}
		if (imagesStartHandlingIndex >= 0)
		{
			for (int i = imagesStartHandlingIndex; i < mPostItems.size(); i++)
			{
				PostItem postItem = mPostItems.get(i);
				if (i == 0) mConfigurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
				mConfigurationSet.gallerySet.add(postItem.getAttachmentItems());
			}
		}

		int referencesStartHandleIndex = startAppendIndex;
		if (invalidateReferences)
		{
			for (PostItem postItem : mPostItems) postItem.clearReferencesFrom();
			referencesStartHandleIndex = 0;
		}
		if (referencesStartHandleIndex >= 0 && !maySkipHandlingReferences)
		{
			for (int i = referencesStartHandleIndex; i < mPostItems.size(); i++)
			{
				PostItem postItem = mPostItems.get(i);
				HashSet<String> referencesTo = postItem.getReferencesTo();
				if (referencesTo != null)
				{
					for (String postNumber : referencesTo)
					{
						PostItem foundPostItem = mPostItemsMap.get(postNumber);
						if (foundPostItem != null) foundPostItem.addReferenceFrom(postItem.getPostNumber());
					}
				}
			}
		}

		mPreloadList.ensureCapacity(mPostItems.size());
		for (PostItem postItem : mPostItems) mPreloadList.add(postItem);

		int ordinalIndex = 0;
		boolean appendBumpLimitDelimiter = false;
		for (int i = 0; i < mPostItems.size(); i++)
		{
			if (appendBumpLimitDelimiter)
			{
				appendBumpLimitDelimiter = false;
				mPostItems.add(i, null);
				i++;
			}
			PostItem postItem = mPostItems.get(i);
			if (postItem.isDeleted()) postItem.setOrdinalIndex(PostItem.ORDINAL_INDEX_DELETED); else
			{
				postItem.setOrdinalIndex(ordinalIndex++);
				if (ordinalIndex == mBumpLimit) appendBumpLimitDelimiter = true;
			}
		}

		preparePreloading(0);
		notifyDataSetChanged();
		startPreloading();
	}

	public ArrayList<PostItem> clearDeletedPosts()
	{
		ArrayList<PostItem> deletedPostItems = null;
		for (int i = mPostItems.size() - 1; i >= 0; i--)
		{
			PostItem postItem = mPostItems.get(i);
			if (postItem != null)
			{
				if (postItem.isDeleted())
				{
					HashSet<String> referencesTo = postItem.getReferencesTo();
					if (referencesTo != null)
					{
						for (String postNumber : referencesTo)
						{
							PostItem foundPostItem = mPostItemsMap.get(postNumber);
							if (foundPostItem != null) foundPostItem.removeReferenceFrom(postItem.getPostNumber());
						}
					}
					mPostItems.remove(i);
					mPostItemsMap.remove(postItem.getPostNumber());
					if (deletedPostItems == null) deletedPostItems = new ArrayList<>();
					deletedPostItems.add(postItem);
				}
			}
		}
		if (deletedPostItems != null)
		{
			mConfigurationSet.gallerySet.clear();
			boolean originalPost = true;
			for (PostItem postItem : this)
			{
				if (originalPost)
				{
					mConfigurationSet.gallerySet.setThreadTitle(postItem.getSubjectOrComment());
					originalPost = false;
				}
				mConfigurationSet.gallerySet.add(postItem.getAttachmentItems());
			}
			notifyDataSetChanged();
		}
		return deletedPostItems;
	}

	public boolean hasDeletedPosts()
	{
		for (PostItem postItem : mPostItems)
		{
			if (postItem != null && postItem.isDeleted()) return true;
		}
		return false;
	}

	public void setSelectionModeEnabled(boolean enabled)
	{
		mSelection = enabled;
		if (!enabled) mSelected.clear();
		notifyDataSetChanged();
	}

	public void toggleItemSelected(ListView listView, int position)
	{
		PostItem postItem = getItem(position);
		if (postItem != null && !postItem.isHiddenUnchecked())
		{
			if (mSelected.contains(postItem)) mSelected.remove(postItem); else mSelected.add(postItem);
			int index = position - listView.getFirstVisiblePosition();
			getView(position, listView.getChildAt(index), listView);
		}
	}

	public ArrayList<PostItem> getSelectedItems()
	{
		ArrayList<PostItem> selected = new ArrayList<>(mSelected);
		Collections.sort(selected);
		return selected;
	}

	public int getSelectedCount()
	{
		return mSelected.size();
	}

	public void preloadPosts(int from)
	{
		cancelPreloading();
		preparePreloading(from);
		startPreloading();
	}

	private void preparePreloading(int from)
	{
		ArrayList<PostItem> preloadList = mPreloadList;
		ArrayList<PostItem> postItems = mPostItems;
		int size = postItems.size();
		from = Math.max(0, Math.min(size, from));
		preloadList.ensureCapacity(size);
		// Ordered preloading
		for (int i = from; i < size; i++)
		{
			PostItem postItem = postItems.get(i);
			if (postItem != null) preloadList.add(postItem);
		}
		for (int i = 0; i < from; i++)
		{
			PostItem postItem = postItems.get(i);
			if (postItem != null) preloadList.add(postItem);
		}
	}

	private void startPreloading()
	{
		// Ensure that cancelPreloading was called before!
		mPreloadThread = new Thread(mPreloadRunnable);
		mPreloadThread.start();
	}

	public void cancelPreloading()
	{
		Thread thread = mPreloadThread;
		if (thread != null)
		{
			// Also will set mPreloadThread field to null
			thread.interrupt();
			try
			{
				thread.join();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
		mPreloadList.clear();
	}

	private volatile Thread mPreloadThread;
	private final ArrayList<PostItem> mPreloadList = new ArrayList<>();

	private final Runnable mPreloadRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			Thread thread = Thread.currentThread();
			try
			{
				Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
				ArrayList<PostItem> preloadList = mPreloadList;
				HidePerformer hidePerformer = mConfigurationSet.hidePerformer;
				for (int i = 0; i < preloadList.size(); i++)
				{
					PostItem postItem = preloadList.get(i);
					postItem.getComment();
					postItem.isHidden(hidePerformer);
					if (thread.isInterrupted()) break;
				}
			}
			finally
			{
				mPreloadThread = null;
			}
		}
	};

	public void invalidateHidden()
	{
		cancelPreloading();
		for (PostItem postItem : this) postItem.invalidateHidden();
	}

	public void cleanup()
	{
		cancelPreloading();
	}

	@Override
	public void setListViewBusy(boolean isBusy, AbsListView listView)
	{
		mUiManager.view().handleListViewBusyStateChange(isBusy, listView, mDemandSet);
	}

	public Iterable<PostItem> iterate(final boolean ascending, final int from)
	{
		return () -> new PostsIterator(ascending, from);
	}

	private class PostsIterator implements Iterator<PostItem>
	{
		private final boolean mAscending;
		private int mPosition;

		private PostsIterator(boolean ascending, int from)
		{
			mAscending = ascending;
			mPosition = from;
		}

		@Override
		public boolean hasNext()
		{
			int count = getCount();
			return mAscending ? mPosition < count : mPosition >= 0;
		}

		private PostItem nextInternal()
		{
			PostItem postItem = getItem(mPosition);
			if (mAscending) mPosition++; else mPosition--;
			return postItem;
		}

		@Override
		public PostItem next()
		{
			PostItem postItem = nextInternal();
			if (postItem == null) postItem = nextInternal(); // Bump limit divider is a null item
			return postItem;
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}
}