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

import java.util.HashSet;

import android.content.Context;
import android.view.View;

import chan.content.ChanLocator;

import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.WeakObservable;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.ExpandedScreen;

public class UiManager {
	private final Context mContext;
	private final ViewUnit mViewUnit;
	private final DialogUnit mDialogUnit;
	private final InteractionUnit mInteractionUnit;
	private final WeakObservable<Observer> mObservable = new WeakObservable<>();

	private final LocalNavigator mLocalNavigator;
	private final ColorScheme mColorScheme;

	public UiManager(Context context, LocalNavigator localNavigator, ExpandedScreen expandedScreen) {
		mContext = context;
		mViewUnit = new ViewUnit(this);
		mDialogUnit = new DialogUnit(this, expandedScreen);
		mInteractionUnit = new InteractionUnit(this);
		mColorScheme = new ColorScheme(context);
		mLocalNavigator = localNavigator;
	}

	Context getContext() {
		return mContext;
	}

	public ColorScheme getColorScheme() {
		return mColorScheme;
	}

	public ViewUnit view() {
		return mViewUnit;
	}

	public DialogUnit dialog() {
		return mDialogUnit;
	}

	public InteractionUnit interaction() {
		return mInteractionUnit;
	}

	public LocalNavigator navigator() {
		return mLocalNavigator;
	}

	public static final int MESSAGE_INVALIDATE_VIEW = 1;
	public static final int MESSAGE_INVALIDATE_COMMENT_VIEW = 2;
	public static final int MESSAGE_PERFORM_SWITCH_USER_MARK = 3;
	public static final int MESSAGE_PERFORM_SWITCH_HIDE = 4;
	public static final int MESSAGE_PERFORM_HIDE_REPLIES = 5;
	public static final int MESSAGE_PERFORM_HIDE_NAME = 6;
	public static final int MESSAGE_PERFORM_HIDE_SIMILAR = 7;
	public static final int MESSAGE_PERFORM_DISPLAY_THUMBNAILS = 8;

	public interface Observer {
		public void onPostItemMessage(PostItem postItem, int message);
	}

	public void sendPostItemMessage(View view, int message) {
		Holder holder = ListViewUtils.getViewHolder(view, Holder.class);
		sendPostItemMessage(holder.postItem, message);
	}

	public void sendPostItemMessage(PostItem postItem, int message) {
		for (Observer observer : mObservable) {
			observer.onPostItemMessage(postItem, message);
		}
	}

	public WeakObservable<Observer> observable() {
		return mObservable;
	}

	public interface PostsProvider extends Iterable<PostItem> {
		public PostItem findPostItem(String postNumber);
	}

	public interface LocalNavigator {
		public void navigateBoardsOrThreads(String chanName, String boardName, boolean navigateTop, boolean fromCache);
		public void navigatePosts(String chanName, String boardName, String threadNumber, String postNumber,
				String threadTitle, boolean fromCache);
		public void navigateSearch(String chanName, String boardName, String searchQuery);
		public void navigateArchive(String chanName, String boardName);
		public void navigateTarget(String chanName, ChanLocator.NavigationData data, boolean fromCache);
		public void navigatePosting(String chanName, String boardName, String threadNumber,
				Replyable.ReplyData... data);
	}

	public static final int SELECTION_DISABLED = 0;
	public static final int SELECTION_NOT_SELECTED = 1;
	public static final int SELECTION_SELECTED = 2;
	public static final int SELECTION_THREADSHOT = 3;

	public static class DemandSet {
		public boolean isBusy = false;
		public boolean lastInList = false;
		public int selectionMode = SELECTION_DISABLED;
		public boolean showOpenThreadButton = false;
	}

	public static class ConfigurationSet {
		public final Replyable replyable;
		public final PostsProvider postsProvider;
		public final HidePerformer hidePerformer;
		public final GalleryItem.GallerySet gallerySet;
		public final CommentTextView.LinkListener linkListener;
		public final HashSet<String> userPostNumbers;

		public final boolean mayCollapse;
		public final boolean isDialog;
		public final boolean allowMyMarkEdit;
		public final boolean allowHiding;
		public final String repliesToPost;

		public ConfigurationSet(Replyable replyable, PostsProvider postsProvider, HidePerformer hidePerformer,
				GalleryItem.GallerySet gallerySet, CommentTextView.LinkListener linkListener,
				HashSet<String> userPostNumbers, boolean mayCollapse, boolean isDialog, boolean allowMyMarkEdit,
				boolean allowHiding, String repliesToPost) {
			this.replyable = replyable;
			this.postsProvider = postsProvider;
			this.hidePerformer = hidePerformer;
			this.gallerySet = gallerySet;
			this.linkListener = linkListener;
			this.userPostNumbers = userPostNumbers;

			this.mayCollapse = mayCollapse;
			this.isDialog = isDialog;
			this.allowMyMarkEdit = allowMyMarkEdit;
			this.allowHiding = allowHiding;
			this.repliesToPost = repliesToPost;
		}

		public ConfigurationSet copyEdit(boolean mayCollapse, boolean isDialog, String repliesToPost) {
			return new ConfigurationSet(replyable, postsProvider, hidePerformer, gallerySet, linkListener,
					userPostNumbers, mayCollapse, isDialog, allowMyMarkEdit, allowHiding, repliesToPost);
		}
	}

	public interface ThumbnailClickListener extends View.OnClickListener {
		public void update(int index, boolean mayShowDialog);
	}

	public interface ThumbnailLongClickListener extends View.OnLongClickListener {
		public void update(AttachmentItem attachmentItem);
	}

	static abstract class Holder {
		public PostItem postItem;

		public abstract GalleryItem.GallerySet getGallerySet();

		public abstract int getAttachmentViewCount();
		public abstract AttachmentView getAttachmentView(int index);
	}

	public PostItem getPostItemFromHolder(View view) {
		Holder holder = ListViewUtils.getViewHolder(view, Holder.class);
		return holder != null ? holder.postItem : null;
	}

	public void onFinish() {
		mDialogUnit.onFinish();
	}
}