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

package com.mishiranu.dashchan.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.Pair;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import chan.content.ChanLocator;
import chan.util.StringUtils;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.CardView;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.LinebreakLayout;
import com.mishiranu.dashchan.widget.SingleLayerLinearLayout;

public class ViewUnit implements SingleLayerLinearLayout.OnTemporaryDetatchListener
{
	private final UiManager mUiManager;
	private final PostDateFormatter mPostDateFormatter;
	
	private final int mThumbnailWidth;
	private final int mMultipleAttachmentInfoWidth;
	
	private final int mCommentMaxLines;
	private final int mCommentAdditionalHeight;
	private final int mCommentAdditionalLines;

	private Collection<String> mHighlightText;
	
	private static final float ALPHA_HIDDEN_POST = 0.2f;
	private static final float ALPHA_DELETED_POST = 0.5f;
	
	@SuppressLint("InflateParams")
	ViewUnit(UiManager uiManager)
	{
		Context context = uiManager.getContext();
		mUiManager = uiManager;
		mPostDateFormatter = new PostDateFormatter(context);

		Configuration configuration = context.getResources().getConfiguration();
		float density = ResourceUtils.obtainDensity(context);
		// Define header height, image width and max comment field height
		View view = LayoutInflater.from(context).inflate(R.layout.list_item_post, null);
		View head = view.findViewById(R.id.head);
		TextView comment = (TextView) view.findViewById(R.id.comment);
		View bottomBar = view.findViewById(R.id.bottom_bar);
		ViewUtils.applyScaleSize(head.findViewById(R.id.subject), head.findViewById(R.id.number),
				head.findViewById(R.id.name), head.findViewById(R.id.index), head.findViewById(R.id.date),
				head.findViewById(R.id.attachment_info), comment, bottomBar.findViewById(R.id.bottom_bar_replies),
				bottomBar.findViewById(R.id.bottom_bar_expand), bottomBar.findViewById(R.id.bottom_bar_open_thread));
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec((int) (320 * density + 0.5f), View.MeasureSpec.AT_MOST);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		view.measure(widthMeasureSpec, heightMeasureSpec);
		mCommentMaxLines = Preferences.getPostMaxLines();
		mCommentAdditionalHeight = bottomBar.getMeasuredHeight();
		mCommentAdditionalLines = (int) Math.ceil(mCommentAdditionalHeight / comment.getTextSize());
		mThumbnailWidth = head.getMeasuredHeight();
		int additionalAttachmentInfoWidthDp = 64; // approximately equal to thumbnail width + right padding 
		int minAttachmentInfoWidthDp = additionalAttachmentInfoWidthDp + 68;
		int maxAttachmentInfoWidthDp = additionalAttachmentInfoWidthDp + 84;
		int attachmentInfoWidthDp = configuration.smallestScreenWidthDp * minAttachmentInfoWidthDp / 320;
		attachmentInfoWidthDp = Math.max(Math.min(attachmentInfoWidthDp, maxAttachmentInfoWidthDp),
				minAttachmentInfoWidthDp);
		attachmentInfoWidthDp -= additionalAttachmentInfoWidthDp;
		mMultipleAttachmentInfoWidth = (int) (attachmentInfoWidthDp * density + 0.5f);
	}
	
	public void resetPages()
	{
		mHighlightText = null;
		mUiManager.dialog().closeDialogs();
	}
	
	public void setHighlightText(Collection<String> highlightText)
	{
		mHighlightText = highlightText;
	}
	
	private final CommentTextView.LinkListener mDefaultLinkListener = new CommentTextView.LinkListener()
	{
		@Override
		public void onLinkClick(CommentTextView view, String chanName, Uri uri, boolean confirmed)
		{
			mUiManager.interaction().handleLinkClick(chanName, uri, confirmed);
		}
		
		@Override
		public void onLinkLongClick(CommentTextView view, String chanName, Uri uri)
		{
			mUiManager.interaction().handleLinkLongClick(uri);
		}
	};
	
	private final CommentTextView.CommentListener mCommentListener = new CommentTextView.CommentListener()
	{
		@Override
		public void onRequestSiblingsInvalidate(CommentTextView view)
		{
			mUiManager.sendPostItemMessage(view, UiManager.MESSAGE_INVALIDATE_COMMENT_VIEW);
		}
		
		@Override
		public String onPrepareToCopy(CommentTextView view, Spannable text, int start, int end)
		{
			return mUiManager.interaction().getCopyReadyComment(text, start, end);
		}
	};
	
	public View getThreadView(PostItem postItem, View convertView, ViewGroup parent, boolean isBusy)
	{
		Context context = mUiManager.getContext();
		ColorScheme colorScheme = mUiManager.getColorScheme();
		ThreadViewHolder holder;
		if (convertView != null && !(convertView.getTag() instanceof ThreadViewHolder)) convertView = null;
		if (convertView == null)
		{
			LayoutInflater inflater = LayoutInflater.from(context);
			convertView = inflater.inflate(R.layout.list_item_card, parent, false);
			CardView cardView = (CardView) convertView.findViewById(R.id.card_view);
			inflater.inflate(R.layout.list_item_thread, cardView);
			holder = new ThreadViewHolder();
			holder.cardView = cardView;
			holder.subject = (TextView) cardView.findViewById(R.id.subject);
			holder.comment = (TextView) cardView.findViewById(R.id.comment);
			holder.description = (TextView) cardView.findViewById(R.id.thread_description);
			holder.stateSticky = (ImageView) cardView.findViewById(R.id.state_sticky);
			holder.stateClosed = (ImageView) cardView.findViewById(R.id.state_closed);
			holder.thumbnail = (AttachmentView) cardView.findViewById(R.id.thumbnail);
			holder.showOpClickView = cardView.findViewById(R.id.click_view);
			holder.showOpClickView.setOnClickListener(mThreadShowOpClickListener);
			convertView.setTag(holder);

			holder.thumbnail.setDrawTouching(true);
			holder.thumbnail.applyRoundedCorners(cardView.getBackgroundColor());
			ViewGroup.MarginLayoutParams thumbnailLayoutParams = (ViewGroup.MarginLayoutParams) holder
					.thumbnail.getLayoutParams();
			ViewUtils.applyScaleSize(holder.comment, holder.subject, holder.description,
					holder.stateSticky, holder.stateClosed);
			if (ResourceUtils.isTablet(context.getResources().getConfiguration()))
			{
				float density = ResourceUtils.obtainDensity(context);
				holder.description.setGravity(Gravity.START);
				int thumbnailSize = (int) (72f * density);
				thumbnailLayoutParams.width = thumbnailSize;
				thumbnailLayoutParams.height = thumbnailSize;
				int descriptionPaddingLeft = thumbnailSize + thumbnailLayoutParams.leftMargin +
						thumbnailLayoutParams.rightMargin;
				holder.description.setPadding(descriptionPaddingLeft, holder.description.getPaddingTop(),
						holder.description.getPaddingRight(), holder.description.getPaddingBottom());
				holder.comment.setMaxLines(8);
			}
			else
			{
				holder.description.setGravity(Gravity.END);
				holder.comment.setMaxLines(6);
			}
			int thumbnailsScale = Preferences.getThumbnailsScale();
			thumbnailLayoutParams.width = thumbnailLayoutParams.width * thumbnailsScale / 100;
			thumbnailLayoutParams.height = thumbnailLayoutParams.height * thumbnailsScale / 100;
		}
		else holder = (ThreadViewHolder) convertView.getTag();
		
		holder.postItem = postItem;
		
		holder.showOpClickView.setEnabled(true);
		holder.stateSticky.setVisibility(postItem.isSticky() ? View.VISIBLE : View.GONE);
		holder.stateClosed.setVisibility(postItem.isClosed() ? View.VISIBLE : View.GONE);
		
		String subject = postItem.getSubject();
		if (!StringUtils.isEmpty(subject))
		{
			holder.subject.setVisibility(View.VISIBLE);
			holder.subject.setText(subject);
		}
		else holder.subject.setVisibility(View.GONE);
		CharSequence comment = postItem.getThreadCommentShort(parent.getWidth(), holder.comment.getTextSize(), 8);
		colorScheme.apply(postItem.getThreadCommentShortSpans());
		if (StringUtils.isEmpty(subject) && StringUtils.isEmpty(comment)) comment = " "; // Avoid 0 height
		holder.comment.setText(comment);
		holder.comment.setVisibility(holder.comment.getText().length() > 0 ? View.VISIBLE : View.GONE);
		holder.description.setText(postItem.formatThreadCardDescription(context, false));
		
		holder.thumbnail.resetTransition();
		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		if (attachmentItems != null)
		{
			AttachmentItem attachmentItem = attachmentItems.get(0);
			boolean needShowSeveralIcon = attachmentItems.size() > 1;
			attachmentItem.displayThumbnail(holder.thumbnail, needShowSeveralIcon, isBusy, false);
			holder.thumbnailClickListener.update(0, true);
			holder.thumbnailLongClickListener.update(attachmentItem);
			holder.thumbnail.setSfwMode(Preferences.isSfwMode());
			holder.thumbnail.setVisibility(View.VISIBLE);
		}
		else
		{
			ImageLoader.getInstance().unbind(holder.thumbnail);
			holder.thumbnail.setVisibility(View.GONE);
		}
		holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
		holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);
		return convertView;
	}
	
	public View getThreadViewForGrid(PostItem postItem, View convertView, ViewGroup parent,
			HidePerformer hidePerformer, int contentHeight, boolean isBusy)
	{
		Context context = mUiManager.getContext();
		ColorScheme colorScheme = mUiManager.getColorScheme();
		ThreadViewHolder holder;
		if (convertView != null && !(convertView.getTag() instanceof ThreadViewHolder)) convertView = null;
		if (convertView == null)
		{
			LayoutInflater inflater = LayoutInflater.from(context);
			convertView = inflater.inflate(R.layout.list_item_card, parent, false);
			CardView cardView = (CardView) convertView.findViewById(R.id.card_view);
			inflater.inflate(R.layout.list_item_thread_grid, cardView);
			holder = new ThreadViewHolder();
			holder.cardView = cardView;
			holder.subject = (TextView) cardView.findViewById(R.id.subject);
			holder.comment = (TextView) cardView.findViewById(R.id.comment);
			holder.description = (TextView) cardView.findViewById(R.id.thread_description);
			holder.thumbnail = (AttachmentView) cardView.findViewById(R.id.thumbnail);
			holder.threadContent = cardView.findViewById(R.id.thread_content);
			holder.showOpClickView = cardView.findViewById(R.id.click_view);
			holder.showOpClickView.setOnClickListener(mThreadShowOpClickListener);
			convertView.setTag(holder);

			holder.thumbnail.setFitSquare(true);
			holder.thumbnail.setDrawTouching(true);
			holder.thumbnail.applyRoundedCorners(cardView.getBackgroundColor());
			ViewUtils.applyScaleSize(holder.comment, holder.subject, holder.description);
		}
		else holder = (ThreadViewHolder) convertView.getTag();

		holder.postItem = postItem;
		
		holder.showOpClickView.setEnabled(true);
		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		
		boolean hidden = postItem.isHidden(hidePerformer);
		((View) holder.threadContent.getParent()).setAlpha(hidden ? ALPHA_HIDDEN_POST : 1f);
		String subject = postItem.getSubject();
		if (!StringUtils.isEmptyOrWhitespace(subject) && !hidden)
		{
			holder.subject.setVisibility(View.VISIBLE);
			holder.subject.setText(subject.trim());
		}
		else holder.subject.setVisibility(View.GONE);
		CharSequence comment = null;
		if (hidden) comment = postItem.getHideReason();
		if (comment == null)
		{
			comment = postItem.getThreadCommentShort(parent.getWidth() / 2,
					holder.comment.getTextSize(), attachmentItems != null ? 4 : 12);
			colorScheme.apply(postItem.getThreadCommentShortSpans());
		}
		holder.comment.setText(comment);
		holder.comment.setVisibility(StringUtils.isEmpty(comment) ? View.GONE : View.VISIBLE);
		holder.description.setText(postItem.formatThreadCardDescription(context, true));
		
		holder.thumbnail.resetTransition();
		if (attachmentItems != null && !hidden)
		{
			AttachmentItem attachmentItem = attachmentItems.get(0);
			boolean needShowSeveralIcon = attachmentItems.size() > 1;
			attachmentItem.displayThumbnail(holder.thumbnail, needShowSeveralIcon, isBusy, false);
			holder.thumbnailClickListener.update(0, true);
			holder.thumbnailLongClickListener.update(attachmentItem);
			holder.thumbnail.setSfwMode(Preferences.isSfwMode());
			holder.thumbnail.setVisibility(View.VISIBLE);
		}
		else
		{
			ImageLoader.getInstance().unbind(holder.thumbnail);
			holder.thumbnail.setVisibility(View.GONE);
		}
		holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
		holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);
		
		holder.threadContent.getLayoutParams().height = contentHeight;
		return convertView;
	}
	
	public View getThreadHiddenView(PostItem postItem, View convertView, ViewGroup parent)
	{
		HiddenViewHolder holder;
		if (convertView != null && !(convertView.getTag() instanceof HiddenViewHolder)) convertView = null;
		if (convertView == null)
		{
			LayoutInflater inflater = LayoutInflater.from(parent.getContext());
			convertView = inflater.inflate(R.layout.list_item_card, parent, false);
			ViewGroup cardView = (ViewGroup) convertView.findViewById(R.id.card_view);
			inflater.inflate(R.layout.list_item_hidden, cardView);
			holder = new HiddenViewHolder();
			holder.comment = (TextView) convertView.findViewById(R.id.comment);
			convertView.setTag(holder);
			convertView.findViewById(R.id.head).setAlpha(ALPHA_HIDDEN_POST);
			convertView.findViewById(R.id.index).setVisibility(View.GONE);
			convertView.findViewById(R.id.number).setVisibility(View.GONE);
			ViewUtils.applyScaleSize(holder.comment);
			ViewUtils.applyScaleMarginLR(holder.comment);
		}
		else holder = (HiddenViewHolder) convertView.getTag();
		
		holder.postItem = postItem;
		String description = postItem.getHideReason();
		if (description == null) description = postItem.getSubjectOrComment();
		holder.comment.setText(description);
		return convertView;
	}
	
	public View getPostView(PostItem postItem, View convertView, ViewGroup parent, UiManager.DemandSet demandSet,
			UiManager.ConfigurationSet configurationSet)
	{
		Context context = mUiManager.getContext();
		ColorScheme colorScheme = mUiManager.getColorScheme();
		PostViewHolder holder;
		if (convertView != null && !(convertView.getTag() instanceof PostViewHolder)) convertView = null;
		if (convertView == null)
		{
			SingleLayerLinearLayout layout = (SingleLayerLinearLayout) LayoutInflater.from(context)
					.inflate(R.layout.list_item_post, parent, false);
			layout.setOnTemporaryDetatchListener(this);
			convertView = layout;
			holder = new PostViewHolder();
			holder.head = (LinebreakLayout) convertView.findViewById(R.id.head);
			holder.number = (TextView) convertView.findViewById(R.id.number);
			holder.name = (TextView) convertView.findViewById(R.id.name);
			holder.index = (TextView) convertView.findViewById(R.id.index);
			holder.date = (TextView) convertView.findViewById(R.id.date);
			int anchorIndex = holder.head.indexOfChild(holder.number) + 1;
			float density = ResourceUtils.obtainDensity(context);
			int size = (int) (12f * density);
			TypedArray typedArray = context.obtainStyledAttributes(STATE_ATTRS);
			for (int i = 0; i < holder.stateImages.length; i++)
			{
				ImageView imageView = new ImageView(context);
				imageView.setImageDrawable(typedArray.getDrawable(i));
				holder.head.addView(imageView, anchorIndex + i, new ViewGroup.LayoutParams(size, size));
				holder.stateImages[i] = imageView;
			}
			typedArray.recycle();
			holder.attachments = (ViewGroup) convertView.findViewById(R.id.attachments);
			holder.comment = (CommentTextView) convertView.findViewById(R.id.comment);
			holder.attachmentInfo = (TextView) convertView.findViewById(R.id.attachment_info);
			holder.thumbnail = (AttachmentView) convertView.findViewById(R.id.thumbnail);
			holder.textBarPadding = convertView.findViewById(R.id.text_bar_padding);
			holder.bottomBar = convertView.findViewById(R.id.bottom_bar);
			holder.bottomBarReplies = (TextView) convertView.findViewById(R.id.bottom_bar_replies);
			holder.bottomBarExpand = (TextView) convertView.findViewById(R.id.bottom_bar_expand);
			holder.bottomBarOpenThread = (TextView) convertView.findViewById(R.id.bottom_bar_open_thread);
			convertView.setTag(holder);
			
			holder.head.setOnTouchListener(mHeadContentTouchListener);
			holder.comment.setCommentListener(mCommentListener);
			holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
			holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);
			holder.bottomBarReplies.setOnClickListener(mRepliesBlockClickListener);
			holder.bottomBarOpenThread.setOnClickListener(mThreadLinkBlockClickListener);
			
			holder.index.setTypeface(GraphicsUtils.TYPEFACE_MEDIUM);
			int textScale = Preferences.getTextScale();
			if (textScale != 100)
			{
				ViewUtils.applyScaleSize(holder.number, holder.name, holder.index, holder.date, holder.comment,
						holder.attachmentInfo, holder.bottomBarReplies, holder.bottomBarExpand,
						holder.bottomBarOpenThread);
				ViewUtils.applyScaleSize(holder.stateImages);
				holder.head.setHorizontalSpacing(holder.head.getHorizontalSpacing() * textScale / 100);
			}
			
			holder.thumbnail.setDrawTouching(true);
			holder.thumbnail.applyRoundedCorners(getPostBackgroundColor(configurationSet));
			ViewGroup.LayoutParams thumbnailLayoutParams = holder.thumbnail.getLayoutParams();
			int thumbnailsScale = Preferences.getThumbnailsScale();
			if (thumbnailsScale != 100)
			{
				thumbnailLayoutParams.width = mThumbnailWidth * thumbnailsScale / 100;
				thumbnailLayoutParams.height = thumbnailLayoutParams.width;
			}
			else thumbnailLayoutParams.width = mThumbnailWidth;
		}
		else holder = (PostViewHolder) convertView.getTag();
		
		holder.postItem = postItem;
		holder.configurationSet = configurationSet;
		
		String chanName = postItem.getChanName();
		String boardName = postItem.getBoardName();
		String threadNumber = postItem.getThreadNumber();
		String postNumber = postItem.getPostNumber();
		holder.number.setText("#" + postNumber);
		holder.states[0] = postItem.isUserPost() && Preferences.isShowMyPosts();
		holder.states[1] = postItem.isOriginalPoster();
		holder.states[2] = postItem.isSage();
		holder.states[3] = !StringUtils.isEmpty(postItem.getEmail());
		holder.states[4] = postItem.isSticky();
		holder.states[5] = postItem.isClosed();
		holder.states[6] = postItem.isCyclical();
		holder.states[7] = postItem.isPosterWarned();
		holder.states[8] = postItem.isPosterBanned();
		for (int i = 0; i < holder.stateImages.length; i++)
		{
			holder.stateImages[i].setVisibility(holder.states[i] ? View.VISIBLE : View.GONE);
		}
		convertView.setAlpha(postItem.isDeleted() ? ALPHA_DELETED_POST : 1f);
		
		CharSequence name = postItem.getFullName();
		colorScheme.apply(postItem.getFullNameSpans());
		holder.name.setText(makeHighlightedText(name));
		holder.date.setText(postItem.getDateTime(mPostDateFormatter));
		
		String subject = postItem.getSubject();
		CharSequence comment = !StringUtils.isEmpty(configurationSet.repliesToPost)
				? postItem.getComment(configurationSet.repliesToPost) : postItem.getComment();
				colorScheme.apply(postItem.getCommentSpans());
		LinkSuffixSpan[] linkSuffixSpans = postItem.getLinkSuffixSpansAfterComment();
		if (linkSuffixSpans != null && configurationSet.userPostNumbers != null)
		{
			boolean showMyPosts = Preferences.isShowMyPosts();
			synchronized (configurationSet.userPostNumbers)
			{
				for (LinkSuffixSpan span : linkSuffixSpans)
				{
					span.setSuffix(LinkSuffixSpan.SUFFIX_USER_POST, showMyPosts && configurationSet.userPostNumbers
							.contains(span.getPostNumber()));
				}
			}
		}
		LinkSpan[] linkSpans = postItem.getLinkSpansAfterComment();
		if (linkSpans != null)
		{
			HashSet<String> referencesTo = postItem.getReferencesTo();
			for (LinkSpan linkSpan : linkSpans)
			{
				String linkPostNumber = linkSpan.getPostNumber();
				if (linkPostNumber != null)
				{
					boolean hidden = false;
					if (referencesTo != null && referencesTo.contains(linkPostNumber)
							&& holder.configurationSet.postsProvider != null)
					{
						PostItem linkPostItem = holder.configurationSet.postsProvider.findPostItem(linkPostNumber);
						hidden = linkPostItem != null && linkPostItem.isHidden(configurationSet.hidePerformer);
					}
					linkSpan.setHidden(hidden);
				}
			}
		}
		holder.comment.setSpoilersEnabled(!Preferences.isShowSpoilers());
		holder.comment.setSubjectAndComment(makeHighlightedText(subject), makeHighlightedText(comment));
		holder.comment.setAppendAdditionalPadding(demandSet.lastInList);
		holder.comment.setReplyable(configurationSet.replyable, postNumber);
		holder.comment.setLinkListener(configurationSet.linkListener != null ? configurationSet.linkListener
				: mDefaultLinkListener, chanName, boardName, threadNumber);
		holder.comment.setVisibility(subject.length() > 0 || comment.length() > 0 ? View.VISIBLE : View.GONE);

		handlePostViewIcons(holder);
		handlePostViewAttachments(holder, configurationSet, demandSet);
		holder.index.setText(postItem.getOrdinalIndexString());
		boolean showName = holder.thumbnail.getVisibility() == View.VISIBLE ||
				!postItem.isUseDefaultName() && !StringUtils.isEmpty(name);
		holder.name.setVisibility(showName ? View.VISIBLE : View.GONE);
		boolean showIndex = postItem.getOrdinalIndex() != PostItem.ORDINAL_INDEX_NONE;
		holder.index.setVisibility(showIndex ? View.VISIBLE : View.GONE);
		
		if (demandSet.selectionMode == UiManager.SELECTION_THREADSHOT)
		{
			holder.bottomBarReplies.setVisibility(View.GONE);
			holder.bottomBarExpand.setVisibility(View.GONE);
			holder.bottomBarOpenThread.setVisibility(View.GONE);
		}
		else
		{
			int replyCount = postItem.getPostReplyCount();
			if (postItem.getPostReplyCount() > 0)
			{
				holder.bottomBarReplies.setText(context.getResources().getQuantityString
						(R.plurals.text_replies_count_format, replyCount, replyCount));
				holder.bottomBarReplies.setVisibility(View.VISIBLE);
			}
			else holder.bottomBarReplies.setVisibility(View.GONE);
			holder.bottomBarExpand.setVisibility(View.GONE);
			holder.bottomBarOpenThread.setVisibility(demandSet.showOpenThreadButton ? View.VISIBLE : View.GONE);
		}
		if (configurationSet.mayCollapse)
		{
			// invalidateBottomBar will be called from these following methods
			if (mCommentMaxLines == 0 || postItem.isExpanded()) removeMaxHeight(convertView);
			else setMaxHeight(convertView, parent);
		}
		else invalidateBottomBar(holder);
		
		boolean viewsEnabled = demandSet.selectionMode == UiManager.SELECTION_DISABLED;
		holder.thumbnail.setEnabled(viewsEnabled);
		holder.comment.setEnabled(viewsEnabled);
		holder.head.setEnabled(viewsEnabled);
		holder.bottomBarReplies.setEnabled(viewsEnabled);
		holder.bottomBarReplies.setClickable(viewsEnabled);
		holder.bottomBarExpand.setEnabled(viewsEnabled);
		holder.bottomBarExpand.setClickable(viewsEnabled);
		holder.bottomBarOpenThread.setEnabled(viewsEnabled);
		holder.bottomBarOpenThread.setClickable(viewsEnabled);
		if (holder.newPostAnimator != null)
		{
			holder.newPostAnimator.cancel();
			holder.newPostAnimator = null;
		}
		if (viewsEnabled && postItem.isUnread())
		{
			switch (Preferences.getHighlightUnreadMode())
			{
				case Preferences.HIGHLIGHT_UNREAD_AUTOMATICALLY:
				{
					Animator animator = AnimationUtils.ofNewPostWithStartDelay(convertView, postItem,
							colorScheme.highlightBackgroundColor);
					animator.setDuration(500);
					animator.start();
					holder.newPostAnimator = animator;
					break;
				}
				case Preferences.HIGHLIGHT_UNREAD_MANUALLY:
				{
					convertView.setBackgroundColor(colorScheme.highlightBackgroundColor);
					break;
				}
				default:
				{
					convertView.setBackground(null);
					break;
				}
			}
		}
		else if (demandSet.selectionMode == UiManager.SELECTION_SELECTED)
		{
			convertView.setBackgroundColor(colorScheme.highlightBackgroundColor);
		}
		else convertView.setBackground(null);
		return convertView;
	}
	
	public View getPostHiddenView(PostItem postItem, View convertView, ViewGroup parent)
	{
		HiddenViewHolder holder;
		if (convertView != null && !(convertView.getTag() instanceof HiddenViewHolder)) convertView = null;
		if (convertView == null)
		{
			convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_hidden, parent, false);
			holder = new HiddenViewHolder();
			holder.index = (TextView) convertView.findViewById(R.id.index);
			holder.number = (TextView) convertView.findViewById(R.id.number);
			holder.comment = (TextView) convertView.findViewById(R.id.comment);
			convertView.setTag(holder);
			convertView.findViewById(R.id.head).setAlpha(ALPHA_HIDDEN_POST);
			ViewUtils.applyScaleSize(holder.index, holder.number, holder.comment);
			ViewUtils.applyScaleMarginLR(holder.index, holder.number, holder.comment);
		}
		else holder = (HiddenViewHolder) convertView.getTag();
		
		holder.postItem = postItem;
		holder.index.setText(postItem.getOrdinalIndexString());
		holder.number.setText("#" + postItem.getPostNumber());
		String description = postItem.getHideReason();
		if (description == null) description = postItem.getSubjectOrComment();
		holder.comment.setText(description);
		postItem.setUnread(false);
		return convertView;
	}
	
	private int getPostBackgroundColor(UiManager.ConfigurationSet configurationSet)
	{
		ColorScheme colorScheme = mUiManager.getColorScheme();
		return configurationSet.isDialog ? colorScheme.dialogBackgroundColor : colorScheme.windowBackgroundColor;
	}
	
	@SuppressLint("InflateParams")
	private void handlePostViewAttachments(PostViewHolder holder, UiManager.ConfigurationSet configurationSet,
			UiManager.DemandSet demandSet)
	{
		Context context = mUiManager.getContext();
		PostItem postItem = holder.postItem;
		holder.thumbnail.resetTransition();
		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		if (attachmentItems != null)
		{
			int size = attachmentItems.size();
			if (size >= 2 && Preferences.isAllAttachments())
			{
				ImageLoader.getInstance().unbind(holder.thumbnail);
				holder.thumbnail.setVisibility(View.GONE);
				holder.attachmentInfo.setVisibility(View.GONE);
				ArrayList<AttachmentHolder> attachmentHolders = holder.attachmentHolders;
				if (attachmentHolders == null)
				{
					attachmentHolders = new ArrayList<>();
					holder.attachmentHolders = attachmentHolders;
				}
				int holders = attachmentHolders.size();
				if (holders < size)
				{
					int postBackgroundColor = getPostBackgroundColor(configurationSet);
					int thumbnailsScale = Preferences.getThumbnailsScale();
					int textScale = Preferences.getTextScale();
					for (int i = holders; i < size; i++)
					{
						View view = LayoutInflater.from(context).inflate(R.layout.list_item_post_attachment, null);
						AttachmentHolder attachmentHolder = new AttachmentHolder();
						attachmentHolder.container = view;
						attachmentHolder.thumbnail = (AttachmentView) view.findViewById(R.id.thumbnail);
						attachmentHolder.attachmentInfo = (TextView) view.findViewById(R.id.attachment_info);
						attachmentHolder.thumbnail.setDrawTouching(true);
						attachmentHolder.thumbnail.applyRoundedCorners(postBackgroundColor);
						attachmentHolder.thumbnail.setOnClickListener(attachmentHolder.thumbnailClickListener);
						attachmentHolder.thumbnail.setOnLongClickListener(attachmentHolder.thumbnailLongClickListener);
						attachmentHolder.attachmentInfo.getLayoutParams().width = mMultipleAttachmentInfoWidth;
						ViewGroup.LayoutParams thumbnailLayoutParams = attachmentHolder.thumbnail.getLayoutParams();
						if (thumbnailsScale != 100)
						{
							thumbnailLayoutParams.width = mThumbnailWidth * thumbnailsScale / 100;
							thumbnailLayoutParams.height = thumbnailLayoutParams.width;
						}
						else
						{
							thumbnailLayoutParams.width = mThumbnailWidth;
							thumbnailLayoutParams.height = mThumbnailWidth;
						}
						if (textScale != 100) ViewUtils.applyScaleSize(attachmentHolder.attachmentInfo);
						attachmentHolders.add(attachmentHolder);
						holder.attachments.addView(view);
					}
				}
				boolean sfwMode = Preferences.isSfwMode();
				for (int i = 0; i < size; i++)
				{
					AttachmentHolder attachmentHolder = attachmentHolders.get(i);
					AttachmentItem attachmentItem = attachmentItems.get(i);
					attachmentItem.displayThumbnail(attachmentHolder.thumbnail, false, demandSet.isBusy, false);
					attachmentHolder.thumbnailClickListener.update(i, false);
					attachmentHolder.thumbnailLongClickListener.update(attachmentItem);
					attachmentHolder.thumbnail.setSfwMode(sfwMode);
					attachmentHolder.attachmentInfo.setText(attachmentItem.getDescription(AttachmentItem.FormatMode
							.THREE_LINES));
					attachmentHolder.container.setVisibility(View.VISIBLE);
				}
				for (int i = size; i < holders; i++)
				{
					AttachmentHolder attachmentHolder = attachmentHolders.get(i);
					ImageLoader.getInstance().unbind(attachmentHolder.thumbnail);
					attachmentHolder.container.setVisibility(View.GONE);
				}
				holder.attachments.setVisibility(View.VISIBLE);
				holder.attachmentViewCount = size;
			}
			else
			{
				AttachmentItem attachmentItem = attachmentItems.get(0);
				attachmentItem.displayThumbnail(holder.thumbnail, size > 1, demandSet.isBusy, false);
				holder.thumbnailClickListener.update(0, true);
				holder.thumbnailLongClickListener.update(attachmentItem);
				holder.thumbnail.setSfwMode(Preferences.isSfwMode());
				holder.thumbnail.setVisibility(View.VISIBLE);
				holder.attachmentInfo.setText(postItem.getAttachmentsDescription(context,
						AttachmentItem.FormatMode.LONG));
				holder.attachmentInfo.setVisibility(View.VISIBLE);
				holder.attachments.setVisibility(View.GONE);
				holder.attachmentViewCount = 1;
			}
		}
		else
		{
			ImageLoader.getInstance().unbind(holder.thumbnail);
			holder.thumbnail.setVisibility(View.GONE);
			holder.attachmentInfo.setVisibility(View.GONE);
			holder.attachments.setVisibility(View.GONE);
			holder.attachmentViewCount = 1;
		}
	}
	
	private void handlePostViewIcons(PostViewHolder holder)
	{
		Context context = mUiManager.getContext();
		PostItem postItem = holder.postItem;
		String chanName = postItem.getChanName();
		ArrayList<Pair<Uri, String>> icons = postItem.getIcons();
		if (icons != null && Preferences.isDisplayIcons())
		{
			if (holder.badgeImages == null) holder.badgeImages = new ArrayList<>();
			int count = holder.badgeImages.size();
			int add = icons.size() - count;
			// Create more image views for icons
			if (add > 0)
			{
				View anchorView = count > 0 ? holder.badgeImages.get(count - 1) : holder.index;
				int anchorIndex = holder.head.indexOfChild(anchorView) + 1;
				float density = ResourceUtils.obtainDensity(context);
				int size = (int) (12f * density);
				for (int i = 0; i < add; i++)
				{
					ImageView imageView = new ImageView(context);
					holder.head.addView(imageView, anchorIndex + i, new ViewGroup.LayoutParams(size, size));
					ViewUtils.applyScaleSize(imageView);
					holder.badgeImages.add(imageView);
				}
			}
			ChanLocator locator = ChanLocator.get(chanName);
			for (int i = 0; i < holder.badgeImages.size(); i++)
			{
				ImageView imageView = holder.badgeImages.get(i);
				if (i < icons.size())
				{
					imageView.setVisibility(View.VISIBLE);
					Uri uri = icons.get(i).first;
					if (uri != null)
					{
						uri = uri.isRelative() ? locator.convert(uri) : uri;
						ImageLoader.getInstance().loadImage(uri, chanName, null, false, imageView);
					}
					else
					{
						ImageLoader.getInstance().unbind(imageView);
						imageView.setImageDrawable(null);
					}
				}
				else imageView.setVisibility(View.GONE);
			}
		}
		else if (holder.badgeImages != null)
		{
			for (ImageView imageView : holder.badgeImages) imageView.setVisibility(View.GONE);
		}
	}
	
	private void invalidateBottomBar(PostViewHolder holder)
	{
		boolean repliesVisible = holder.bottomBarReplies.getVisibility() == View.VISIBLE;
		boolean expandVisible = holder.bottomBarExpand.getVisibility() == View.VISIBLE;
		boolean openThreadVisible = holder.bottomBarOpenThread.getVisibility() == View.VISIBLE;
		boolean needBar = repliesVisible || expandVisible || openThreadVisible;
		holder.bottomBarReplies.getLayoutParams().width = repliesVisible && !expandVisible && !openThreadVisible ?
				ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
		holder.bottomBarExpand.getLayoutParams().width = expandVisible && !openThreadVisible ?
				ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
		holder.bottomBar.setVisibility(needBar ? View.VISIBLE : View.GONE);
		boolean hasText = holder.comment.getVisibility() == View.VISIBLE;
		float density = ResourceUtils.obtainDensity(holder.comment);
		holder.textBarPadding.getLayoutParams().height = (int) ((needBar ? 0f : hasText ? 10f : 6f) * density);
	}
	
	private void setMaxHeight(final View view, View parent)
	{
		final PostViewHolder holder = (PostViewHolder) view.getTag();
		clearMaxHeightAnimation(holder);
		holder.comment.setMaxHeight(Integer.MAX_VALUE);
		int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.AT_MOST);
		int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
		holder.comment.measure(widthMeasureSpec, heightMeasureSpec);
		if (holder.comment.getLineCount() >= mCommentMaxLines + mCommentAdditionalLines)
		{
			holder.comment.setMaxLines(mCommentMaxLines);
			holder.bottomBarExpand.setVisibility(View.VISIBLE);
			holder.bottomBarExpand.setOnClickListener(v ->
			{
				holder.postItem.setExpanded(true);
				removeMaxHeight(view);
				int fromHeight = holder.comment.getHeight();
				AnimationUtils.measureDynamicHeight(holder.comment);
				int toHeight = holder.comment.getMeasuredHeight();
				// When button bar becomes hidden, height of view becomes smaller, so it can cause
				// little jumping of list; This is solution - start animation from item_height + bar_height 
				if (holder.bottomBar.getVisibility() == View.GONE) fromHeight += mCommentAdditionalHeight;
				if (toHeight > fromHeight)
				{
					float density = ResourceUtils.obtainDensity(holder.comment);
					float value = (toHeight - fromHeight) / density / 400;
					if (value > 1f) value = 1f; else if (value < 0.2f) value = 0.2f;
					Animator heightAnimator = AnimationUtils.ofHeight(holder.comment, fromHeight,
							ViewGroup.LayoutParams.WRAP_CONTENT, false);
					heightAnimator.setDuration((int) (200 * value));
					heightAnimator.start();
				}
			});
		}
		else
		{
			holder.comment.setMaxHeight(Integer.MAX_VALUE);
			holder.bottomBarExpand.setVisibility(View.GONE);
		}
		invalidateBottomBar(holder);
	}
	
	private void removeMaxHeight(View view)
	{
		PostViewHolder holder = (PostViewHolder) view.getTag();
		clearMaxHeightAnimation(holder);
		holder.comment.setMaxHeight(Integer.MAX_VALUE);
		holder.bottomBarExpand.setVisibility(View.GONE);
		invalidateBottomBar(holder);
	}
	
	private void clearMaxHeightAnimation(PostViewHolder holder)
	{
		holder.comment.clearAnimation();
		holder.comment.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
	}
	
	private CharSequence makeHighlightedText(CharSequence text)
	{
		if (mHighlightText != null && text != null)
		{
			Locale locale = Locale.getDefault();
			SpannableString spannable = new SpannableString(text);
			String searchable = text.toString().toLowerCase(locale);
			for (String highlight : mHighlightText)
			{
				highlight = highlight.toLowerCase(locale);
				int textIndex = -1;
				while ((textIndex = searchable.indexOf(highlight, textIndex + 1)) >= 0)
				{
					spannable.setSpan(new BackgroundColorSpan(mUiManager.getColorScheme().highlightTextColor),
							textIndex, textIndex + highlight.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				text = spannable;
			}
		}
		return text;
	}
	
	public void notifyUnbindListView(ListView listView)
	{
		for (int i = 0; i < listView.getChildCount(); i++) notifyUnbindView(listView.getChildAt(i));
	}
	
	public void notifyUnbindView(View view)
	{
		PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
		if (holder != null)
		{
			if (holder.newPostAnimator != null)
			{
				holder.newPostAnimator.cancel();
				holder.newPostAnimator = null;
			}
		}
	}
	
	@Override
	public void onTemporaryDetatch(SingleLayerLinearLayout view, boolean start)
	{
		if (start) notifyUnbindView(view);
	}
	
	public void handleListViewBusyStateChange(boolean isBusy, AbsListView listView, UiManager.DemandSet demandSet)
	{
		if (!isBusy)
		{
			int count = listView.getChildCount();
			for (int i = 0; i < count; i++)
			{
				View view = listView.getChildAt(i);
				UiManager.Holder holder = (UiManager.Holder) view.getTag();
				if (holder != null)
				{
					PostItem postItem = holder.postItem;
					if (postItem != null) displayThumbnail(holder, postItem.getAttachmentItems(), false);
				}
			}
		}
		demandSet.isBusy = isBusy;
	}
	
	private void displayThumbnail(UiManager.Holder holder, List<AttachmentItem> attachmentItems, boolean force)
	{
		if (attachmentItems != null)
		{
			int size = attachmentItems.size();
			int count = Math.min(size, holder.getAttachmentViewCount());
			boolean needShowSeveralIcon = size > 1 && count == 1;
			for (int i = 0; i < count; i++)
			{
				AttachmentItem attachmentItem = attachmentItems.get(i);
				if (!attachmentItem.isThumbnailReady())
				{
					if (force) attachmentItem.setForceLoadThumbnail();
					attachmentItem.displayThumbnail(holder.getAttachmentView(i), needShowSeveralIcon, false, false);
				}
			}
		}
	}
	
	public void displayThumbnail(View view, List<AttachmentItem> attachmentItems, boolean force)
	{
		Object tag = view.getTag();
		if (tag instanceof UiManager.Holder) displayThumbnail((UiManager.Holder) tag, attachmentItems, force);
	}
	
	public void displayThumbnail(ListView listView, int position, List<AttachmentItem> attachmentItems, boolean force)
	{
		if (position != ListView.INVALID_POSITION)
		{
			View view = listView.getChildAt(position - listView.getFirstVisiblePosition());
			if (view != null) displayThumbnail(view, attachmentItems, force);
		}
	}
	
	public int findImageIndex(ArrayList<GalleryItem> galleryItems, PostItem postItem)
	{
		if (galleryItems != null && postItem.hasAttachments())
		{
			String postNumber = postItem.getPostNumber();
			for (int i = 0; i < galleryItems.size(); i++)
			{
				if (postNumber.equals(galleryItems.get(i).postNumber)) return i;
			}
		}
		return -1;
	}
	
	public int findViewIndex(ListView listView, PostItem postItem)
	{
		ListAdapter adapter = listView.getAdapter();
		for (int i = 0; i < adapter.getCount(); i++)
		{
			PostItem adapterPostItem = (PostItem) adapter.getItem(i);
			if (adapterPostItem != null && adapterPostItem == postItem) return i;
		}
		return ListView.INVALID_POSITION;
	}
	
	public void invalidateComment(ListView listView, int position)
	{
		int first = listView.getFirstVisiblePosition();
		int count = listView.getChildCount();
		int index = position - first;
		if (index >= 0 && index < count)
		{
			View child = listView.getChildAt(index);
			PostViewHolder holder = (PostViewHolder) child.getTag();
			holder.comment.invalidate();
		}
	}
	
	boolean handlePostForDoubleClick(final View view)
	{
		final PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
		if (holder != null)
		{
			if (holder.comment.getVisibility() != View.VISIBLE || holder.comment.isSelectionEnabled()) return false;
			long t = System.currentTimeMillis();
			long timeout = holder.comment.getPreferredDoubleTapTimeout();
			if (t - holder.lastCommentClick > timeout) holder.lastCommentClick = t; else
			{
				final ListView listView = (ListView) view.getParent();
				final int position = listView.getPositionForView(view);
				holder.comment.startSelection();
				int padding = holder.comment.getAdditionalPadding();
				if (padding > 0)
				{
					final int listHeight = listView.getHeight() - listView.getPaddingTop() -
							listView.getPaddingBottom();
					listView.post(() ->
					{
						int end = holder.comment.getSelectionEnd();
						if (end >= 0)
						{
							Layout layout = holder.comment.getLayout();
							int line = layout.getLineForOffset(end);
							int count = layout.getLineCount();
							if (count - line <= 4)
							{
								listView.setSelectionFromTop(position, listHeight - view.getHeight());
							}
						}
					});
				}
			}
			return true;
		}
		else return false;
	}
	
	private final View.OnClickListener mRepliesBlockClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
			PostItem postItem = holder.postItem;
			mUiManager.dialog().displayReplies(postItem, holder.configurationSet);
		}
	};
	
	private final View.OnClickListener mThreadLinkBlockClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
			PostItem postItem = holder.postItem;
			String postNumber = postItem.getParentPostNumber() == null ? null : postItem.getPostNumber();
			mUiManager.navigator().navigatePosts(postItem.getChanName(), postItem.getBoardName(),
					postItem.getThreadNumber(), postNumber, null, false);
		}
	};
	
	private final View.OnClickListener mThreadShowOpClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			ThreadViewHolder holder = ListViewUtils.getViewHolder(v, ThreadViewHolder.class);
			mUiManager.dialog().displayThread(holder.postItem);
		}
	};
	
	private final View.OnTouchListener mHeadContentTouchListener = new View.OnTouchListener()
	{
		private static final int TYPE_NONE = 0;
		private static final int TYPE_BADGES = 1;
		private static final int TYPE_STATES = 2;
		
		private int mType;
		private float mStartX, mStartY;
		
		@Override
		public boolean onTouch(View v, MotionEvent event)
		{
			switch (event.getAction())
			{
				case MotionEvent.ACTION_DOWN:
				{
					mType = TYPE_NONE;
					float x = event.getX();
					float y = event.getY();
					PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
					LinebreakLayout head = holder.head;
					for (int i = 0; i < head.getChildCount(); i++)
					{
						View child = head.getChildAt(i);
						if (child.getVisibility() == View.VISIBLE)
						{
							int width = child.getWidth();
							int height = child.getHeight();
							int radius = (int) (Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2)) / 2f);
							int centerX = child.getLeft() + child.getWidth() / 2;
							int centerY = child.getTop() + child.getHeight() / 2;
							int distance = (int) (Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2)) / 2f);
							if (distance <= radius * 3 / 2)
							{
								mStartX = x;
								mStartY = y;
								// noinspection SuspiciousMethodCalls
								if (holder.badgeImages != null && holder.badgeImages.contains(child))
								{
									mType = TYPE_BADGES;
									return true;
								}
								// noinspection SuspiciousMethodCalls
								if (Arrays.asList(holder.stateImages).contains(child))
								{
									mType = TYPE_STATES;
									return true;
								}
							}
						}
					}
					break;
				}
				case MotionEvent.ACTION_UP:
				{
					if (mType != TYPE_NONE)
					{
						Context context = mUiManager.getContext();
						PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
						int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
						if (Math.abs(event.getX() - mStartX) <= touchSlop &&
								Math.abs(event.getY() - mStartY) <= touchSlop)
						{
							ArrayList<DialogUnit.IconData> icons = new ArrayList<>();
							String emailToCopy = null;
							switch (mType)
							{
								case TYPE_BADGES:
								{
									ArrayList<Pair<Uri, String>> postIcons = holder.postItem.getIcons();
									ChanLocator locator = ChanLocator.get(holder.postItem.getChanName());
									for (Pair<Uri, String> postIcon : postIcons)
									{
										Uri uri = postIcon.first;
										if (uri != null) uri = uri.isRelative() ? locator.convert(uri) : uri;
										icons.add(new DialogUnit.IconData(postIcon.second, uri));
									}
									break;
								}
								case TYPE_STATES:
								{
									for (int i = 0; i < holder.states.length; i++)
									{
										if (holder.states[i])
										{
											int attr = STATE_ATTRS[i];
											String title;
											if (attr == R.attr.postEmail)
											{
												title = holder.postItem.getEmail();
												if (title.startsWith("mailto:")) title = title.substring(7);
												emailToCopy = title;
											}
											else title = context.getString(STATE_TEXTS.get(attr));
											icons.add(new DialogUnit.IconData(title, attr));
										}
									}
									break;
								}
							}
							mUiManager.dialog().showPostDescriptionDialog(icons,
									holder.postItem.getChanName(), emailToCopy);
						}
						return true;
					}
					break;
				}
			}
			return false;
		}
	};
	
	private static final int[] STATE_ATTRS = {R.attr.postUserPost, R.attr.postOriginalPoster,
		R.attr.postSage, R.attr.postEmail, R.attr.postSticky, R.attr.postClosed,
		R.attr.postCyclical, R.attr.postWarned, R.attr.postBanned};
	
	private static final SparseIntArray STATE_TEXTS = new SparseIntArray();
	
	static
	{
		STATE_TEXTS.put(R.attr.postUserPost, R.string.text_my_post);
		STATE_TEXTS.put(R.attr.postOriginalPoster, R.string.text_original_poster);
		STATE_TEXTS.put(R.attr.postSage, R.string.text_sage_description);
		STATE_TEXTS.put(R.attr.postSticky, R.string.text_sticky_thread);
		STATE_TEXTS.put(R.attr.postClosed, R.string.text_closed_thread);
		STATE_TEXTS.put(R.attr.postCyclical, R.string.text_cyclical_thread);
		STATE_TEXTS.put(R.attr.postWarned, R.string.text_user_warned);
		STATE_TEXTS.put(R.attr.postBanned, R.string.text_user_banned);
	}
	
	private class AttachmentHolder
	{
		public AttachmentView thumbnail;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;
		
		public View container;
		public TextView attachmentInfo;
		
		public AttachmentHolder()
		{
			thumbnailClickListener = mUiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = mUiManager.interaction().createThumbnailLongClickListener();
		}
	}
	
	private class ThreadViewHolder extends UiManager.Holder
	{
		public AttachmentView thumbnail;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;
		
		public CardView cardView;
		public TextView subject;
		public TextView comment;
		public TextView description;
		public ImageView stateSticky, stateClosed;
		public View threadContent;
		public View showOpClickView;
		
		public ThreadViewHolder()
		{
			thumbnailClickListener = mUiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = mUiManager.interaction().createThumbnailLongClickListener();
		}
		
		@Override
		public GalleryItem.GallerySet getGallerySet()
		{
			return postItem.getThreadGallerySet();
		}
		
		@Override
		public int getAttachmentViewCount()
		{
			return 1;
		}
		
		@Override
		public AttachmentView getAttachmentView(int index)
		{
			return thumbnail;
		}
	}
	
	private class PostViewHolder extends UiManager.Holder implements CommentTextView.ListSelectionKeeper.Holder
	{
		public UiManager.ConfigurationSet configurationSet;
		public Animator newPostAnimator;
		
		public AttachmentView thumbnail;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;
		public ArrayList<AttachmentHolder> attachmentHolders;
		public int attachmentViewCount = 1;
		
		public LinebreakLayout head;
		public TextView number, name, index, date;
		public final ImageView[] stateImages = new ImageView[STATE_ATTRS.length];
		public ArrayList<ImageView> badgeImages;
		public ViewGroup attachments;
		public CommentTextView comment;
		public long lastCommentClick;
		public TextView attachmentInfo;
		public View textBarPadding;
		public View bottomBar;
		public TextView bottomBarReplies;
		public TextView bottomBarExpand;
		public TextView bottomBarOpenThread;
		
		public final boolean[] states = new boolean[STATE_ATTRS.length];
		
		public PostViewHolder()
		{
			thumbnailClickListener = mUiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = mUiManager.interaction().createThumbnailLongClickListener();
		}
		
		@Override
		public GalleryItem.GallerySet getGallerySet()
		{
			return configurationSet.gallerySet;
		}
		
		@Override
		public int getAttachmentViewCount()
		{
			return attachmentViewCount;
		}
		
		@Override
		public AttachmentView getAttachmentView(int index)
		{
			return attachmentViewCount > 1 ? attachmentHolders.get(index).thumbnail : thumbnail;
		}
		
		@Override
		public CommentTextView getCommentTextView()
		{
			return comment;
		}
	}
	
	private static class HiddenViewHolder extends UiManager.Holder
	{
		public TextView index;
		public TextView number;
		public TextView comment;
		
		@Override
		public GalleryItem.GallerySet getGallerySet()
		{
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int getAttachmentViewCount()
		{
			return 0;
		}
		
		@Override
		public AttachmentView getAttachmentView(int index)
		{
			return null;
		}
	}
}