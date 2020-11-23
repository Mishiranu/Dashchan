package com.mishiranu.dashchan.ui.navigator.manager;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.ImageLoader;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.AttachmentItem;
import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.ui.gallery.GalleryOverlay;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.PostDateFormatter;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.AttachmentView;
import com.mishiranu.dashchan.widget.CardView;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.LinebreakLayout;
import com.mishiranu.dashchan.widget.PostLinearLayout;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ThreadDescriptionView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ViewUnit {
	private final UiManager uiManager;
	private final PostDateFormatter postDateFormatter;
	private final List<CommentTextView.ExtraButton> extraButtons;
	private final Lazy<PostViewHolder.Dimensions> postDimensions = new Lazy<>();

	private static final float ALPHA_HIDDEN_POST = 0.2f;
	private static final float ALPHA_DELETED_POST = 0.5f;

	@SuppressLint("InflateParams")
	ViewUnit(UiManager uiManager) {
		Context context = uiManager.getContext();
		this.uiManager = uiManager;
		postDateFormatter = new PostDateFormatter(context);

		extraButtons = Arrays
				.asList(new CommentTextView.ExtraButton(context.getString(R.string.quote__verb),
						R.attr.iconActionPaste, (view, text, click) -> {
					PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
					UiManager.ConfigurationSet configurationSet = holder.getConfigurationSet();
					if (configurationSet.replyable != null && configurationSet.replyable.onRequestReply(false)) {
						if (click) {
							configurationSet.replyable.onRequestReply(true, new Replyable
									.ReplyData(holder.getPostItem().getPostNumber(), text.toPreparedString(view)));
						}
						return true;
					}
					return false;
				}), new CommentTextView.ExtraButton(context.getString(R.string.web_browser),
						R.attr.iconActionForward, (view, text, click) -> {
					Uri uri = extractUri(text.toString());
					if (uri != null) {
						if (click) {
							PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
							UiManager.ConfigurationSet configurationSet = holder.getConfigurationSet();
							CommentTextView.LinkListener linkListener = configurationSet.linkListener != null
									? configurationSet.linkListener : defaultLinkListener;
							linkListener.onLinkClick(view, uri, CommentTextView.LinkListener.Extra.EMPTY, true);
						}
						return true;
					}
					return false;
				}), new CommentTextView.ExtraButton(context.getString(R.string.add_theme),
						R.attr.iconActionAddRule, (view, text, click) -> {
					ThemeEngine.Theme theme = ThemeEngine.fastParseThemeFromText(context, text.toString());
					if (theme != null) {
						if (click) {
							uiManager.navigator().navigateSetTheme(theme);
						}
						return true;
					}
					return false;
				}));
	}

	private static Uri extractUri(String text) {
		String fixedText = StringUtils.fixParsedUriString(text);
		if (text.equals(fixedText)) {
			if (!text.matches("[a-z]+:.*")) {
				text = "http://" + text.replaceAll("^/+", "");
			}
			Uri uri = Uri.parse(text);
			if (uri != null) {
				if (StringUtils.isEmpty(uri.getAuthority())) {
					uri = uri.buildUpon().scheme("http").build();
				}
				String host = uri.getHost();
				if (host != null && host.matches(".+\\..+") && Chan.getFallback().locator.isWebScheme(uri)) {
					return uri;
				}
			}
		}
		return null;
	}

	private final CommentTextView.LinkListener defaultLinkListener = new CommentTextView.LinkListener() {
		@Override
		public void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed) {
			UiManager.Holder holder = ListViewUtils.getViewHolder(view, UiManager.Holder.class);
			uiManager.interaction().handleLinkClick(holder.getConfigurationSet(), uri, extra, confirmed);
		}

		@Override
		public void onLinkLongClick(CommentTextView view, Uri uri, Extra extra) {
			UiManager.Holder holder = ListViewUtils.getViewHolder(view, UiManager.Holder.class);
			uiManager.interaction().handleLinkLongClick(holder.getConfigurationSet(), uri);
		}
	};

	private void onSpanStateChanged(CommentTextView view) {
		uiManager.sendPostItemMessage(view, UiManager.Message.INVALIDATE_COMMENT_VIEW);
	}

	private final CommentTextView.SpanStateListener spanStateListener = this::onSpanStateChanged;

	private final CommentTextView.PrepareToCopyListener prepareToCopyListener =
			(view, text, start, end) -> InteractionUnit.getCopyReadyComment(text, start, end);

	public enum ViewType {THREAD, THREAD_HIDDEN, THREAD_CARD, THREAD_CARD_HIDDEN, THREAD_CARD_CELL, POST, POST_HIDDEN}
	private enum ThreadViewType {LIST, CARD, CELL}

	private final ListViewUtils.UnlimitedRecycledViewPool threadsPostsViewPool =
			new ListViewUtils.UnlimitedRecycledViewPool();

	public void bindThreadsPostRecyclerView(RecyclerView recyclerView) {
		recyclerView.setRecycledViewPool(threadsPostsViewPool);
		((LinearLayoutManager) recyclerView.getLayoutManager()).setRecycleChildrenOnDetach(true);
	}

	public RecyclerView.ViewHolder createView(ViewGroup parent, ViewType viewType) {
		switch (viewType) {
			case THREAD: {
				return new ThreadViewHolder(parent, uiManager, ThreadViewType.LIST);
			}
			case THREAD_HIDDEN: {
				return new HiddenViewHolder(parent, false, true);
			}
			case THREAD_CARD: {
				return new ThreadViewHolder(parent, uiManager, ThreadViewType.CARD);
			}
			case THREAD_CARD_HIDDEN: {
				return new HiddenViewHolder(parent, true, true);
			}
			case THREAD_CARD_CELL: {
				return new ThreadViewHolder(parent, uiManager, ThreadViewType.CELL);
			}
			case POST: {
				return new PostViewHolder(parent, uiManager, postDimensions);
			}
			case POST_HIDDEN: {
				return new HiddenViewHolder(parent, false, false);
			}
			default: {
				throw new IllegalArgumentException();
			}
		}
	}

	public void bindThreadView(RecyclerView.ViewHolder viewHolder,
			PostItem postItem, UiManager.ConfigurationSet configurationSet) {
		Context context = uiManager.getContext();
		ColorScheme colorScheme = ThemeEngine.getColorScheme(context);
		ThreadViewHolder holder = (ThreadViewHolder) viewHolder;
		Chan chan = Chan.get(configurationSet.chanName);
		holder.configure(postItem, configurationSet);

		boolean bumpLimitReached = postItem.getBumpLimitReachedState(chan, 0) == PostItem.BumpLimitState.REACHED;
		PostState.Predicate.Data stateData = new PostState.Predicate.Data(postItem, configurationSet, bumpLimitReached);
		for (int i = 0; i < PostState.THREAD_ITEM_STATES.size(); i++) {
			boolean visible = PostState.THREAD_ITEM_STATES.get(i).predicate.apply(stateData);
			holder.stateImages[i].setVisibility(visible ? View.VISIBLE : View.GONE);
		}

		String subject = postItem.getSubject();
		if (!StringUtils.isEmpty(subject)) {
			holder.subject.setVisibility(View.VISIBLE);
			holder.subject.setText(subject);
		} else {
			holder.subject.setVisibility(View.GONE);
		}
		int parentWidth = (int) (ResourceUtils.obtainDensity(holder.itemView) *
				holder.itemView.getResources().getConfiguration().screenWidthDp);
		CharSequence comment = postItem.getThreadCommentShort(parentWidth, holder.comment.getTextSize(), 8);
		colorScheme.apply(postItem.getThreadCommentShortSpans());
		if (StringUtils.isEmpty(subject) && StringUtils.isEmpty(comment)) {
			// Avoid 0 height
			comment = " ";
		}
		holder.comment.setText(comment);
		holder.comment.setVisibility(holder.comment.getText().length() > 0 ? View.VISIBLE : View.GONE);
		holder.description.clear();
		postItem.formatThreadCardDescription(context.getResources(), false, holder.description::append);

		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		if (attachmentItems != null) {
			AttachmentItem attachmentItem = attachmentItems.get(0);
			boolean needShowMultipleIcon = attachmentItems.size() > 1;
			attachmentItem.configureAndLoad(holder.thumbnail, chan, needShowMultipleIcon, false);
			holder.thumbnailClickListener.update(0, true, GalleryOverlay.NavigatePostMode.DISABLED);
			holder.thumbnailLongClickListener.update(attachmentItem);
			holder.thumbnail.setSfwMode(Preferences.isSfwMode());
			holder.thumbnail.setVisibility(View.VISIBLE);
		} else {
			ImageLoader.getInstance().cancel(holder.thumbnail);
			holder.thumbnail.resetImage(null);
			holder.thumbnail.setVisibility(View.GONE);
		}
		holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
		holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);
	}

	public void bindThreadCellView(RecyclerView.ViewHolder viewHolder,
			PostItem postItem, UiManager.ConfigurationSet configurationSet, boolean small, int contentHeight) {
		Context context = uiManager.getContext();
		ColorScheme colorScheme = ThemeEngine.getColorScheme(context);
		ThreadViewHolder holder = (ThreadViewHolder) viewHolder;
		Chan chan = Chan.get(configurationSet.chanName);
		holder.configure(postItem, configurationSet);

		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		boolean hidden = postItem.getHideState().hidden;
		((View) holder.threadContent.getParent()).setAlpha(hidden ? ALPHA_HIDDEN_POST : 1f);
		String subject = postItem.getSubject();
		if (!StringUtils.isEmptyOrWhitespace(subject) && !hidden) {
			holder.subject.setVisibility(View.VISIBLE);
			holder.subject.setSingleLine(!small);
			SpannableStringBuilder builder = new SpannableStringBuilder(subject.trim());
			if (!small) {
				builder.setSpan(new RelativeSizeSpan(4f / 3f), 0, builder.length(),
						SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new TypefaceSpan("sans-serif-light"), 0, builder.length(),
						SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			holder.subject.setText(builder);
		} else {
			holder.subject.setVisibility(View.GONE);
		}
		CharSequence comment = null;
		if (hidden) {
			comment = postItem.getHideReason();
		} else if (!small || attachmentItems == null) {
			int parentWidth = (int) (ResourceUtils.obtainDensity(holder.itemView) *
					holder.itemView.getResources().getConfiguration().screenWidthDp);
			comment = postItem.getThreadCommentShort(parentWidth / 2,
					holder.comment.getTextSize(), attachmentItems != null ? 4 : 12);
			colorScheme.apply(postItem.getThreadCommentShortSpans());
		}
		holder.comment.setText(comment);
		holder.comment.setVisibility(StringUtils.isEmpty(comment) ? View.GONE : View.VISIBLE);
		holder.description.clear();
		postItem.formatThreadCardDescription(context.getResources(), true, holder.description::append);

		if (attachmentItems != null && !hidden) {
			AttachmentItem attachmentItem = attachmentItems.get(0);
			boolean needShowMultipleIcon = attachmentItems.size() > 1;
			attachmentItem.configureAndLoad(holder.thumbnail, chan, needShowMultipleIcon, false);
			holder.thumbnailClickListener.update(0, true, GalleryOverlay.NavigatePostMode.DISABLED);
			holder.thumbnailLongClickListener.update(attachmentItem);
			holder.thumbnail.setSfwMode(Preferences.isSfwMode());
			holder.thumbnail.setVisibility(View.VISIBLE);
		} else {
			ImageLoader.getInstance().cancel(holder.thumbnail);
			holder.thumbnail.resetImage(null);
			holder.thumbnail.setVisibility(View.GONE);
		}
		holder.thumbnail.setOnClickListener(holder.thumbnailClickListener);
		holder.thumbnail.setOnLongClickListener(holder.thumbnailLongClickListener);

		holder.threadContent.getLayoutParams().height = contentHeight;
	}

	public void bindThreadViewReloadAttachment(RecyclerView.ViewHolder viewHolder, AttachmentItem attachmentItem) {
		ThreadViewHolder holder = (ThreadViewHolder) viewHolder;
		List<AttachmentItem> attachmentItems = holder.getPostItem().getAttachmentItems();
		if (attachmentItems != null && !attachmentItems.isEmpty() && attachmentItems.get(0) == attachmentItem) {
			Chan chan = Chan.get(holder.getConfigurationSet().chanName);
			attachmentItem.startLoad(holder.thumbnail, chan, true);
		}
	}

	public void bindThreadHiddenView(RecyclerView.ViewHolder viewHolder,
			PostItem postItem, UiManager.ConfigurationSet configurationSet) {
		HiddenViewHolder holder = (HiddenViewHolder) viewHolder;
		holder.configure(postItem, configurationSet);
		String description = postItem.getHideReason();
		if (description == null) {
			description = postItem.getSubjectOrComment();
		}
		holder.comment.setText(description);
	}

	public void bindPostView(RecyclerView.ViewHolder viewHolder,
			PostItem postItem, UiManager.ConfigurationSet configurationSet, UiManager.DemandSet demandSet) {
		ColorScheme colorScheme = ThemeEngine.getColorScheme(uiManager.getContext());
		PostViewHolder holder = (PostViewHolder) viewHolder;
		Chan chan = Chan.get(configurationSet.chanName);
		holder.resetAnimations();
		holder.configure(postItem, configurationSet);
		holder.selection = demandSet.selection;

		String boardName = postItem.getBoardName();
		String threadNumber = postItem.getThreadNumber();
		PostNumber postNumber = postItem.getPostNumber();
		boolean bumpLimitReached = false;
		PostItem.BumpLimitState bumpLimitReachedState = postItem.getBumpLimitReachedState(chan, 0);
		if (bumpLimitReachedState == PostItem.BumpLimitState.REACHED) {
			bumpLimitReached = true;
		} else if (bumpLimitReachedState == PostItem.BumpLimitState.NEED_COUNT &&
				configurationSet.postsProvider != null) {
			int postsCount = 0;
			for (PostItem itPostItem : configurationSet.postsProvider) {
				if (!itPostItem.isDeleted()) {
					postsCount++;
				}
			}
			bumpLimitReached = postItem.getBumpLimitReachedState(chan, postsCount) == PostItem.BumpLimitState.REACHED;
		}
		holder.number.setText("#" + postNumber);
		PostState.Predicate.Data stateData = new PostState.Predicate.Data(postItem, configurationSet, bumpLimitReached);
		for (int i = 0; i < PostState.POST_ITEM_STATES.size(); i++) {
			boolean visible = PostState.POST_ITEM_STATES.get(i).predicate.apply(stateData);
			holder.stateImages[i].setVisibility(visible ? View.VISIBLE : View.GONE);
		}
		viewHolder.itemView.setAlpha(postItem.isDeleted() ? ALPHA_DELETED_POST : 1f);

		CharSequence name = postItem.getFullName(chan);
		colorScheme.apply(postItem.getFullNameSpans());
		holder.name.setText(makeHighlightedText(demandSet.highlightText, name));
		holder.date.setText(postItem.getDateTime(postDateFormatter));

		String subject = postItem.getSubject();
		CharSequence comment = configurationSet.repliesToPost != null
				? postItem.getComment(chan, configurationSet.repliesToPost) : postItem.getComment(chan);
				colorScheme.apply(postItem.getCommentSpans());
		LinkSuffixSpan[] linkSuffixSpans = postItem.getLinkSuffixSpansAfterComment();
		if (linkSuffixSpans != null) {
			boolean showMyPosts = Preferences.isShowMyPosts();
			for (LinkSuffixSpan span : linkSuffixSpans) {
				span.setSuffix(LinkSuffixSpan.SUFFIX_USER_POST, showMyPosts &&
						configurationSet.postStateProvider.isUserPost(span.getPostNumber()));
			}
		}
		LinkSpan[] linkSpans = postItem.getLinkSpansAfterComment();
		if (linkSpans != null) {
			for (LinkSpan linkSpan : linkSpans) {
				if (linkSpan.postNumber != null) {
					boolean hidden = false;
					if (postItem.getReferencesTo().contains(linkSpan.postNumber)
							&& configurationSet.postsProvider != null) {
						PostItem linkPostItem = configurationSet.postsProvider.findPostItem(linkSpan.postNumber);
						if (linkPostItem != null) {
							hidden = configurationSet.postStateProvider.isHiddenResolve(linkPostItem);
						}
					}
					linkSpan.setHidden(hidden);
				}
			}
		}
		holder.comment.setSpoilersEnabled(!Preferences.isShowSpoilers());
		holder.comment.setSubjectAndComment(makeHighlightedText(demandSet.highlightText, subject),
				makeHighlightedText(demandSet.highlightText, comment));
		holder.comment.setLinkListener(configurationSet.linkListener != null
				? configurationSet.linkListener : defaultLinkListener,
				configurationSet.chanName, boardName, threadNumber);
		holder.comment.setVisibility(subject.length() > 0 || comment.length() > 0 ? View.VISIBLE : View.GONE);
		holder.comment.bindSelectionPaddingView(demandSet.lastInList ? holder.textSelectionPadding : null);

		handlePostViewIcons(holder);
		handlePostViewAttachments(holder);
		holder.index.setText(postItem.getOrdinalIndexString());
		boolean showName = holder.thumbnail.getVisibility() == View.VISIBLE ||
				!postItem.isUseDefaultName() && !StringUtils.isEmpty(name);
		holder.name.setVisibility(showName ? View.VISIBLE : View.GONE);
		boolean showIndex = postItem.getOrdinalIndex() != PostItem.ORDINAL_INDEX_NONE;
		holder.index.setVisibility(showIndex ? View.VISIBLE : View.GONE);

		if (demandSet.selection == UiManager.Selection.THREADSHOT) {
			holder.bottomBarReplies.setVisibility(View.GONE);
			holder.bottomBarExpand.setVisibility(View.GONE);
			holder.bottomBarOpenThread.setVisibility(View.GONE);
		} else {
			int replyCount = postItem.getPostReplyCount();
			if (postItem.getPostReplyCount() > 0) {
				holder.bottomBarReplies.setText(holder.itemView.getResources().getQuantityString
						(R.plurals.number_replies__format, replyCount, replyCount));
				holder.bottomBarReplies.setVisibility(View.VISIBLE);
			} else {
				holder.bottomBarReplies.setVisibility(View.GONE);
			}
			holder.bottomBarExpand.setVisibility(View.GONE);
			holder.bottomBarOpenThread.setVisibility(demandSet.showOpenThreadButton ? View.VISIBLE : View.GONE);
		}
		boolean resetLimit = true;
		if (configurationSet.mayCollapse && !configurationSet.postStateProvider.isExpanded(postNumber)) {
			int maxLines = Preferences.getPostMaxLines();
			if (maxLines > 0) {
				resetLimit = false;
				holder.comment.setLinesLimit(maxLines, holder.dimensions.commentAdditionalHeight);
			}
		}
		if (resetLimit) {
			holder.comment.setLinesLimit(0, 0);
		}
		holder.bottomBarExpand.setVisibility(View.GONE);
		holder.invalidateBottomBar();

		boolean viewsEnabled = demandSet.selection == UiManager.Selection.DISABLED;
		holder.thumbnail.setEnabled(viewsEnabled);
		holder.comment.setEnabled(viewsEnabled);
		holder.head.setEnabled(viewsEnabled);
		holder.bottomBarReplies.setEnabled(viewsEnabled);
		holder.bottomBarReplies.setClickable(viewsEnabled);
		holder.bottomBarExpand.setEnabled(viewsEnabled);
		holder.bottomBarExpand.setClickable(viewsEnabled);
		holder.bottomBarOpenThread.setEnabled(viewsEnabled);
		holder.bottomBarOpenThread.setClickable(viewsEnabled);
		holder.installBackground();
	}

	public void bindPostViewInvalidateComment(RecyclerView.ViewHolder viewHolder) {
		PostViewHolder holder = (PostViewHolder) viewHolder;
		holder.comment.invalidateAllSpans();
	}

	public void bindPostViewReloadAttachment(RecyclerView.ViewHolder viewHolder, AttachmentItem attachmentItem) {
		PostViewHolder holder = (PostViewHolder) viewHolder;
		List<AttachmentItem> attachmentItems = holder.getPostItem().getAttachmentItems();
		AttachmentView attachmentView = null;
		if (attachmentItems != null) {
			int index = attachmentItems.indexOf(attachmentItem);
			if (index >= 0) {
				if (holder.attachmentViewCount >= 2) {
					attachmentView = holder.attachmentHolders.get(index).thumbnail;
				} else {
					attachmentView = holder.thumbnail;
				}
			}
		}
		if (attachmentView != null) {
			Chan chan = Chan.get(holder.getConfigurationSet().chanName);
			attachmentItem.startLoad(attachmentView, chan, true);
		}
	}

	public void bindPostHiddenView(RecyclerView.ViewHolder viewHolder,
			PostItem postItem, UiManager.ConfigurationSet configurationSet) {
		HiddenViewHolder holder = (HiddenViewHolder) viewHolder;
		holder.configure(postItem, configurationSet);
		holder.index.setText(postItem.getOrdinalIndexString());
		holder.number.setText("#" + postItem.getPostNumber());
		String description = postItem.getHideReason();
		if (description == null) {
			description = postItem.getSubjectOrComment();
		}
		holder.comment.setText(description);
		configurationSet.postStateProvider.setRead(postItem.getPostNumber());
	}

	private static int getPostBackgroundColor(Context context, UiManager.ConfigurationSet configurationSet) {
		ColorScheme colorScheme = ThemeEngine.getColorScheme(context);
		return configurationSet.isDialog ? colorScheme.dialogBackgroundColor : colorScheme.windowBackgroundColor;
	}

	@SuppressLint("InflateParams")
	private void handlePostViewAttachments(PostViewHolder holder) {
		PostItem postItem = holder.getPostItem();
		UiManager.ConfigurationSet configurationSet = holder.getConfigurationSet();
		Context context = uiManager.getContext();
		Chan chan = Chan.get(configurationSet.chanName);
		List<AttachmentItem> attachmentItems = postItem.getAttachmentItems();
		if (attachmentItems != null && !attachmentItems.isEmpty()) {
			int size = attachmentItems.size();
			if (size >= 2 && Preferences.isAllAttachments()) {
				holder.thumbnail.resetImage(null);
				holder.thumbnail.setVisibility(View.GONE);
				holder.attachmentInfo.setVisibility(View.GONE);
				ArrayList<AttachmentHolder> attachmentHolders = holder.attachmentHolders;
				if (attachmentHolders == null) {
					attachmentHolders = new ArrayList<>();
					holder.attachmentHolders = attachmentHolders;
				}
				int holders = attachmentHolders.size();
				if (holders < size) {
					int postBackgroundColor = getPostBackgroundColor(uiManager.getContext(), configurationSet);
					float thumbnailsScale = Preferences.getThumbnailsScale();
					float textScale = Preferences.getTextScale();
					for (int i = holders; i < size; i++) {
						View view = LayoutInflater.from(context).inflate(R.layout.list_item_post_attachment, null);
						AttachmentHolder attachmentHolder = new AttachmentHolder();
						attachmentHolder.container = view;
						attachmentHolder.thumbnail = view.findViewById(R.id.thumbnail);
						attachmentHolder.attachmentInfo = view.findViewById(R.id.attachment_info);
						attachmentHolder.thumbnail.setDrawTouching(true);
						attachmentHolder.thumbnail.applyRoundedCorners(postBackgroundColor);
						attachmentHolder.thumbnail.setOnClickListener(attachmentHolder.thumbnailClickListener);
						attachmentHolder.thumbnail.setOnLongClickListener(attachmentHolder.thumbnailLongClickListener);
						attachmentHolder.attachmentInfo.getLayoutParams().width =
								holder.dimensions.multipleAttachmentInfoWidth;
						ViewGroup.LayoutParams thumbnailLayoutParams = attachmentHolder.thumbnail.getLayoutParams();
						if (thumbnailsScale != 1f) {
							thumbnailLayoutParams.width = (int) (holder.dimensions.thumbnailWidth * thumbnailsScale);
							thumbnailLayoutParams.height = thumbnailLayoutParams.width;
						} else {
							thumbnailLayoutParams.width = holder.dimensions.thumbnailWidth;
							thumbnailLayoutParams.height = holder.dimensions.thumbnailWidth;
						}
						if (textScale != 1f) {
							ViewUtils.applyScaleSize(textScale, attachmentHolder.attachmentInfo);
						}
						attachmentHolders.add(attachmentHolder);
						holder.attachments.addView(view);
					}
				}
				boolean sfwMode = Preferences.isSfwMode();
				for (int i = 0; i < size; i++) {
					AttachmentHolder attachmentHolder = attachmentHolders.get(i);
					AttachmentItem attachmentItem = attachmentItems.get(i);
					attachmentItem.configureAndLoad(attachmentHolder.thumbnail, chan, false, false);
					attachmentHolder.thumbnailClickListener.update(i, false, configurationSet.isDialog
							? GalleryOverlay.NavigatePostMode.MANUALLY : GalleryOverlay.NavigatePostMode.ENABLED);
					attachmentHolder.thumbnailLongClickListener.update(attachmentItem);
					attachmentHolder.thumbnail.setSfwMode(sfwMode);
					attachmentHolder.attachmentInfo.setText(attachmentItem.getDescription(AttachmentItem.FormatMode
							.THREE_LINES));
					attachmentHolder.container.setVisibility(View.VISIBLE);
				}
				for (int i = size; i < holders; i++) {
					AttachmentHolder attachmentHolder = attachmentHolders.get(i);
					ImageLoader.getInstance().cancel(attachmentHolder.thumbnail);
					attachmentHolder.thumbnail.resetImage(null);
					attachmentHolder.container.setVisibility(View.GONE);
				}
				holder.attachments.setVisibility(View.VISIBLE);
				holder.attachmentViewCount = size;
			} else {
				AttachmentItem attachmentItem = attachmentItems.get(0);
				attachmentItem.configureAndLoad(holder.thumbnail, chan, size > 1, false);
				holder.thumbnailClickListener.update(0, true, configurationSet.isDialog
						? GalleryOverlay.NavigatePostMode.MANUALLY : GalleryOverlay.NavigatePostMode.ENABLED);
				holder.thumbnailLongClickListener.update(attachmentItem);
				holder.thumbnail.setSfwMode(Preferences.isSfwMode());
				holder.thumbnail.setVisibility(View.VISIBLE);
				holder.attachmentInfo.setText(postItem.getAttachmentsDescription(context.getResources(),
						AttachmentItem.FormatMode.LONG));
				holder.attachmentInfo.setVisibility(View.VISIBLE);
				holder.attachments.setVisibility(View.GONE);
				holder.attachmentViewCount = 1;
			}
		} else {
			ImageLoader.getInstance().cancel(holder.thumbnail);
			holder.thumbnail.resetImage(null);
			holder.thumbnail.setVisibility(View.GONE);
			holder.attachmentInfo.setVisibility(View.GONE);
			holder.attachments.setVisibility(View.GONE);
			holder.attachmentViewCount = 1;
		}
	}

	private void handlePostViewIcons(PostViewHolder holder) {
		PostItem postItem = holder.getPostItem();
		UiManager.ConfigurationSet configurationSet = holder.getConfigurationSet();
		Context context = uiManager.getContext();
		Chan chan = Chan.get(configurationSet.chanName);
		List<Post.Icon> icons = postItem.getIcons();
		if (!icons.isEmpty() && Preferences.isDisplayIcons()) {
			if (holder.badgeImages == null) {
				holder.badgeImages = new ArrayList<>();
			}
			int count = holder.badgeImages.size();
			int add = icons.size() - count;
			// Create more image views for icons
			if (add > 0) {
				View anchorView = count > 0 ? holder.badgeImages.get(count - 1) : holder.index;
				int anchorIndex = holder.head.indexOfChild(anchorView) + 1;
				float density = ResourceUtils.obtainDensity(context);
				int size = (int) (12f * density);
				float textScale = Preferences.getTextScale();
				for (int i = 0; i < add; i++) {
					ImageView imageView = new ImageView(context);
					holder.head.addView(imageView, anchorIndex + i, new ViewGroup.LayoutParams(size, size));
					if (textScale != 1f) {
						ViewUtils.applyScaleSize(textScale, imageView);
					}
					holder.badgeImages.add(imageView);
				}
			}
			for (int i = 0; i < holder.badgeImages.size(); i++) {
				ImageView imageView = holder.badgeImages.get(i);
				if (i < icons.size()) {
					imageView.setVisibility(View.VISIBLE);
					Uri uri = icons.get(i).uri;
					if (uri != null) {
						uri = uri.isRelative() ? chan.locator.convert(uri) : uri;
						ImageLoader.getInstance().loadImage(chan, uri, false, imageView);
					} else {
						ImageLoader.getInstance().cancel(imageView);
						imageView.setTag(null);
						imageView.setImageDrawable(null);
					}
				} else {
					ImageLoader.getInstance().cancel(imageView);
					imageView.setVisibility(View.GONE);
				}
			}
		} else if (holder.badgeImages != null) {
			for (ImageView imageView : holder.badgeImages) {
				imageView.setTag(null);
				imageView.setVisibility(View.GONE);
			}
		}
	}

	private CharSequence makeHighlightedText(Collection<String> highlightText, CharSequence text) {
		if (!highlightText.isEmpty() && text != null) {
			Locale locale = Locale.getDefault();
			SpannableString spannable = new SpannableString(text);
			String searchable = text.toString().toLowerCase(locale);
			ColorScheme colorScheme = ThemeEngine.getColorScheme(uiManager.getContext());
			for (String highlight : highlightText) {
				highlight = highlight.toLowerCase(locale);
				int textIndex = -1;
				while ((textIndex = searchable.indexOf(highlight, textIndex + 1)) >= 0) {
					spannable.setSpan(new BackgroundColorSpan(colorScheme.highlightTextColor),
							textIndex, textIndex + highlight.length(), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
				text = spannable;
			}
		}
		return text;
	}

	boolean handlePostForDoubleClick(final View view) {
		final PostViewHolder holder = ListViewUtils.getViewHolder(view, PostViewHolder.class);
		if (holder != null) {
			if (holder.comment.getVisibility() != View.VISIBLE || holder.comment.isSelectionMode()) {
				return false;
			}
			long t = SystemClock.elapsedRealtime();
			long timeout = holder.comment.getPreferredDoubleTapTimeout();
			if (t - holder.lastCommentClick > timeout) {
				holder.lastCommentClick = t;
			} else {
				final RecyclerView recyclerView = (RecyclerView) view.getParent();
				final int position = recyclerView.getChildAdapterPosition(view);
				holder.comment.startSelection();
				int padding = holder.comment.getSelectionPadding();
				if (padding > 0) {
					final int listHeight = recyclerView.getHeight() - recyclerView.getPaddingTop() -
							recyclerView.getPaddingBottom();
					recyclerView.post(() -> {
						int end = holder.comment.getSelectionEnd();
						if (end >= 0) {
							Layout layout = holder.comment.getLayout();
							int line = layout.getLineForOffset(end);
							int count = layout.getLineCount();
							if (count - line <= 4) {
								((LinearLayoutManager) recyclerView.getLayoutManager())
										.scrollToPositionWithOffset(position, listHeight - view.getHeight());
							}
						}
					});
				}
			}
			return true;
		} else {
			return false;
		}
	}

	private final View.OnClickListener repliesBlockClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
			uiManager.dialog().displayReplies(holder.getConfigurationSet(), holder.getPostItem());
		}
	};

	private final View.OnClickListener threadLinkBlockClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
			PostItem postItem = holder.getPostItem();
			PostNumber postNumber = postItem.isOriginalPost() ? null : postItem.getPostNumber();
			uiManager.navigator().navigatePosts(holder.getConfigurationSet().chanName, postItem.getBoardName(),
					postItem.getThreadNumber(), postNumber, null);
		}
	};

	private final View.OnClickListener threadShowOriginalPostClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			ThreadViewHolder holder = ListViewUtils.getViewHolder(v, ThreadViewHolder.class);
			uiManager.dialog().displayThread(holder.getConfigurationSet(), holder.getPostItem());
		}
	};

	private final View.OnTouchListener headContentTouchListener = new View.OnTouchListener() {
		private static final int TYPE_NONE = 0;
		private static final int TYPE_BADGES = 1;
		private static final int TYPE_STATES = 2;

		private int type;
		private float startX, startY;

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN: {
					type = TYPE_NONE;
					float x = event.getX();
					float y = event.getY();
					PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
					LinebreakLayout head = holder.head;
					for (int i = 0; i < head.getChildCount(); i++) {
						View child = head.getChildAt(i);
						if (child.getVisibility() == View.VISIBLE) {
							int width = child.getWidth();
							int height = child.getHeight();
							int radius = (int) (Math.sqrt(Math.pow(width, 2) + Math.pow(height, 2)) / 2f);
							int centerX = child.getLeft() + child.getWidth() / 2;
							int centerY = child.getTop() + child.getHeight() / 2;
							int distance = (int) (Math.sqrt(Math.pow(centerX - x, 2) + Math.pow(centerY - y, 2)) / 2f);
							if (distance <= radius * 3 / 2) {
								startX = x;
								startY = y;
								// noinspection SuspiciousMethodCalls
								if (holder.badgeImages != null && holder.badgeImages.contains(child)) {
									type = TYPE_BADGES;
									return true;
								}
								// noinspection SuspiciousMethodCalls
								if (Arrays.asList(holder.stateImages).contains(child)) {
									type = TYPE_STATES;
									return true;
								}
							}
						}
					}
					break;
				}
				case MotionEvent.ACTION_UP: {
					if (type != TYPE_NONE) {
						Context context = uiManager.getContext();
						PostViewHolder holder = ListViewUtils.getViewHolder(v, PostViewHolder.class);
						PostItem postItem = holder.getPostItem();
						UiManager.ConfigurationSet configurationSet = holder.getConfigurationSet();
						int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
						if (Math.abs(event.getX() - startX) <= touchSlop &&
								Math.abs(event.getY() - startY) <= touchSlop) {
							ArrayList<DialogUnit.IconData> icons = new ArrayList<>();
							String emailToCopy = null;
							switch (type) {
								case TYPE_BADGES: {
									Chan chan = Chan.get(configurationSet.chanName);
									List<Post.Icon> postIcons = postItem.getIcons();
									for (Post.Icon postIcon : postIcons) {
										Uri uri = postIcon.uri;
										if (uri != null) {
											uri = uri.isRelative() ? chan.locator.convert(uri) : uri;
										}
										icons.add(new DialogUnit.IconData(postIcon.title, uri));
									}
									break;
								}
								case TYPE_STATES: {
									for (int i = 0; i < PostState.POST_ITEM_STATES.size(); i++) {
										if (holder.stateImages[i].getVisibility() == View.VISIBLE) {
											PostState postState = PostState.POST_ITEM_STATES.get(i);
											String title = postState.titleProvider
													.get(uiManager.getContext(), postItem);
											icons.add(new DialogUnit.IconData(title, postState.iconAttrResId));
										}
									}
									break;
								}
							}
							uiManager.dialog().showPostDescriptionDialog(configurationSet.fragmentManager,
									icons, configurationSet.chanName, emailToCopy);
						}
						return true;
					}
					break;
				}
			}
			return false;
		}
	};

	private enum PostState {
		USER_POST(R.attr.iconPostUserPost, R.string.my_post,
				data -> Preferences.isShowMyPosts() && data.configurationSet.postStateProvider
						.isUserPost(data.postItem.getPostNumber())),
		ORIGINAL_POSTER(R.attr.iconPostOriginalPoster, R.string.original_poster,
				data -> data.postItem.isOriginalPoster()),
		SAGE(R.attr.iconPostSage, R.string.doesnt_bring_up_thread,
				data -> data.postItem.isSage() || data.bumpLimitReached),
		EMAIL(R.attr.iconPostEmail,
				(context, postItem) -> {
					String email = postItem.getEmail();
					if (email != null && email.startsWith("mailto:")) {
						email = email.substring(7);
					}
					return email;
				},
				data -> !StringUtils.isEmpty(data.postItem.getEmail())),
		STICKY(R.attr.iconPostSticky, R.string.sticky_thread, data -> data.postItem.isSticky()),
		CLOSED(R.attr.iconPostClosed, R.string.thread_is_closed, data -> data.postItem.isClosed()),
		CYCLICAL(R.attr.iconPostCyclical, R.string.cyclical_thread, data -> data.postItem.isCyclical()),
		WARNED(R.attr.iconPostWarned, R.string.user_is_warned, data -> data.postItem.isPosterWarned()),
		BANNED(R.attr.iconPostBanned, R.string.user_is_banned, data -> data.postItem.isPosterBanned());

		public interface TitleProvider {
			String get(Context context, PostItem postItem);
		}

		public interface Predicate {
			class Data {
				public final PostItem postItem;
				public final UiManager.ConfigurationSet configurationSet;
				public final boolean bumpLimitReached;

				public Data(PostItem postItem, UiManager.ConfigurationSet configurationSet, boolean bumpLimitReached) {
					this.postItem = postItem;
					this.configurationSet = configurationSet;
					this.bumpLimitReached = bumpLimitReached;
				}
			}

			boolean apply(Data data);
		}

		public static final List<PostState> POST_ITEM_STATES = Arrays
				.asList(USER_POST, ORIGINAL_POSTER, SAGE, EMAIL, STICKY, CLOSED, CYCLICAL, WARNED, BANNED);

		public static final List<PostState> THREAD_ITEM_STATES = Arrays
				.asList(SAGE, STICKY, CLOSED, CYCLICAL);

		public final int iconAttrResId;
		public final TitleProvider titleProvider;
		public final Predicate predicate;

		PostState(int iconAttrResId, int titleResId, Predicate predicate) {
			this(iconAttrResId, (c, p) -> c.getString(titleResId), predicate);
		}

		PostState(int iconAttrResId, TitleProvider titleProvider, Predicate predicate) {
			this.iconAttrResId = iconAttrResId;
			this.titleProvider = titleProvider;
			this.predicate = predicate;
		}
	}

	private class AttachmentHolder {
		public AttachmentView thumbnail;
		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;

		public View container;
		public TextView attachmentInfo;

		public AttachmentHolder() {
			thumbnailClickListener = uiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = uiManager.interaction().createThumbnailLongClickListener();
		}
	}

	private static CardView createCardLayout(ViewGroup parent) {
		ThemeEngine.Theme theme = ThemeEngine.getTheme(parent.getContext());
		CardView cardView = new CardView(parent.getContext());
		cardView.setBackgroundColor(theme.card);
		FrameLayout content = new FrameLayout(cardView.getContext());
		ViewUtils.setSelectableItemBackground(content);
		cardView.addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cardView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		return cardView;
	}

	private static void fillStateImages(ViewGroup parent, int anchorIndex,
			ImageView[] images, List<PostState> states, float topDp, float startDp, float endDp) {
		float density = ResourceUtils.obtainDensity(parent);
		int size = (int) (12f * density + 0.5f);
		int top = (int) (topDp * density + 0.5f);
		int start = (int) (startDp * density + 0.5f);
		int end = (int) (endDp * density + 0.5f);
		boolean rtl = ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL;
		int left = rtl ? end : start;
		int right = rtl ? start : end;
		int[] attrs = new int[states.size()];
		for (int i = 0; i < attrs.length; i++) {
			attrs[i] = states.get(i).iconAttrResId;
		}
		TypedArray typedArray = parent.getContext().obtainStyledAttributes(attrs);
		for (int i = 0; i < images.length; i++) {
			ImageView imageView = new ImageView(parent.getContext());
			imageView.setImageDrawable(typedArray.getDrawable(i));
			parent.addView(imageView, anchorIndex + i, new ViewGroup.LayoutParams(size, size));
			ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
			if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
				ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
				marginLayoutParams.topMargin = top;
				marginLayoutParams.leftMargin = left;
				marginLayoutParams.rightMargin = right;
			}
			images[i] = imageView;
		}
		typedArray.recycle();
		if (C.API_LOLLIPOP && images.length > 0) {
			ColorStateList tint = ColorStateList.valueOf(ThemeEngine.getTheme(images[0].getContext()).meta);
			for (ImageView image : images) {
				image.setImageTintList(tint);
			}
		}
	}

	private static class Lazy<T> {
		public interface Provider<T> {
			T createLazy();
		}

		private T data;

		public T get(Provider<T> provider) {
			if (data == null) {
				data = provider.createLazy();
			}
			return data;
		}
	}

	private static class BasePostViewHolder extends RecyclerView.ViewHolder
			implements UiManager.Holder, ListViewUtils.ClickCallback<Void, BasePostViewHolder> {
		private WeakReference<PostItem> postItem;
		private WeakReference<UiManager.ConfigurationSet> configurationSet;

		public BasePostViewHolder(View itemView) {
			super(itemView);
		}

		protected void onConfigure(PostItem postItem, UiManager.ConfigurationSet configurationSet) {}

		public final void configure(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
			this.postItem = new WeakReference<>(postItem);
			this.configurationSet = new WeakReference<>(configurationSet);
			onConfigure(postItem, configurationSet);
		}

		@Override
		public final PostItem getPostItem() {
			return Objects.requireNonNull(postItem.get());
		}

		@Override
		public final UiManager.ConfigurationSet getConfigurationSet() {
			return Objects.requireNonNull(configurationSet.get());
		}

		@Override
		public final boolean onItemClick(BasePostViewHolder holder, int position, Void item, boolean longClick) {
			return getConfigurationSet().clickCallback.onItemClick(holder, position, getPostItem(), longClick);
		}
	}

	private static class ThreadViewHolder extends BasePostViewHolder {
		private final CardView cardView;
		public final AttachmentView thumbnail;
		public final TextView subject;
		public final TextView comment;
		public final ThreadDescriptionView description;
		public final ImageView[] stateImages;
		public final View threadContent;
		public final View showOriginalPost;

		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;

		public ThreadViewHolder(ViewGroup parent, UiManager uiManager, ThreadViewType threadViewType) {
			super(threadViewType != ThreadViewType.LIST ? createCardLayout(parent)
					: LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_thread, parent, false));

			if (threadViewType != ThreadViewType.LIST) {
				cardView = (CardView) itemView;
				ViewGroup cardContent = (ViewGroup) cardView.getChildAt(0);
				LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
				inflater.inflate(threadViewType == ThreadViewType.CELL ? R.layout.list_item_thread_cell
						: R.layout.list_item_thread_card, cardContent);
				ListViewUtils.bind(this, cardContent, true, null, this);
			} else {
				cardView = null;
				ViewUtils.setSelectableItemBackground(itemView);
				ListViewUtils.bind(this, itemView, true, null, this);
			}

			thumbnail = itemView.findViewById(R.id.thumbnail);
			subject = itemView.findViewById(R.id.subject);
			comment = itemView.findViewById(R.id.comment);
			description = itemView.findViewById(R.id.thread_description);
			threadContent = itemView.findViewById(R.id.thread_content);
			ViewGroup showOriginalPost = itemView.findViewById(R.id.show_original_post);
			this.showOriginalPost = showOriginalPost;
			(threadViewType == ThreadViewType.CELL ? description : showOriginalPost)
					.setOnClickListener(uiManager.view().threadShowOriginalPostClickListener);

			thumbnailClickListener = uiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = uiManager.interaction().createThumbnailLongClickListener();

			float density = ResourceUtils.obtainDensity(itemView);
			float textScale = Preferences.getTextScale();
			int descriptionSpacingDp = 8;
			thumbnail.setDrawTouching(true);
			description.setTextColor(ThemeEngine.getTheme(description.getContext()).meta);
			description.setTextSizeSp(11f * textScale);
			description.setSpacing((int) (descriptionSpacingDp * density));
			if (threadViewType == ThreadViewType.CELL) {
				thumbnail.setFitSquare(true);
				ViewUtils.applyScaleSize(textScale, comment, subject);
				stateImages = null;
				description.setToEnd(true);
			} else {
				stateImages = new ImageView[PostState.THREAD_ITEM_STATES.size()];
				fillStateImages(showOriginalPost, threadViewType == ThreadViewType.CARD ? 1 : 0,
						stateImages, PostState.THREAD_ITEM_STATES, 0.5f,
						threadViewType == ThreadViewType.CARD ? descriptionSpacingDp : 0,
						threadViewType == ThreadViewType.CARD ? 0 : descriptionSpacingDp);
				ViewGroup.MarginLayoutParams thumbnailLayoutParams =
						(ViewGroup.MarginLayoutParams) thumbnail.getLayoutParams();
				ViewUtils.applyScaleSize(textScale, comment, subject);
				ViewUtils.applyScaleSize(textScale, stateImages);
				if (ResourceUtils.isTablet(itemView.getResources().getConfiguration()) &&
						threadViewType == ThreadViewType.CARD) {
					description.setToEnd(false);
					int thumbnailSize = (int) (72f * density);
					thumbnailLayoutParams.width = thumbnailSize;
					thumbnailLayoutParams.height = thumbnailSize;
					int descriptionPaddingLeft = thumbnailSize + thumbnailLayoutParams.leftMargin +
							thumbnailLayoutParams.rightMargin;
					description.setPadding(descriptionPaddingLeft, description.getPaddingTop(),
							description.getPaddingRight(), description.getPaddingBottom());
					comment.setMaxLines(8);
				} else {
					description.setToEnd(threadViewType != ThreadViewType.LIST);
					comment.setMaxLines(6);
				}
				float thumbnailsScale = Preferences.getThumbnailsScale();
				if (thumbnailsScale != 1f) {
					thumbnailLayoutParams.width = (int) (thumbnailLayoutParams.width * thumbnailsScale);
					thumbnailLayoutParams.height = (int) (thumbnailLayoutParams.height * thumbnailsScale);
				}
			}
		}

		@Override
		protected void onConfigure(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
			int thumbnailBackground = cardView != null ? cardView.getBackgroundColor()
					: getPostBackgroundColor(itemView.getContext(), configurationSet);
			thumbnail.applyRoundedCorners(thumbnailBackground);
		}
	}

	private static class NewPostAnimation implements Runnable, ValueAnimator.AnimatorUpdateListener {
		private final PostLinearLayout layout;
		private final UiManager.PostStateProvider postStateProvider;
		private final PostNumber postNumber;
		private final ColorDrawable drawable;

		private ValueAnimator animator;
		private boolean applied = false;

		public NewPostAnimation(PostLinearLayout layout, UiManager.PostStateProvider postStateProvider,
				PostNumber postNumber, int color) {
			this.layout = layout;
			this.postStateProvider = postStateProvider;
			this.postNumber = postNumber;
			drawable = new ColorDrawable(color);
			layout.setSecondaryBackground(drawable);
			layout.postDelayed(this, 500);
		}

		@Override
		public void run() {
			int color = drawable.getColor();
			animator = ValueAnimator.ofObject(new ArgbEvaluator(), color, color & 0x00ffffff);
			animator.addUpdateListener(this);
			animator.setDuration(500);
			animator.start();
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			if (!applied) {
				applied = true;
				postStateProvider.setRead(postNumber);
			}
			drawable.setColor((int) animation.getAnimatedValue());
		}

		public void cancel() {
			layout.removeCallbacks(this);
			if (animator != null) {
				animator.cancel();
				animator = null;
			}
		}
	}

	private static class PostViewHolder extends BasePostViewHolder implements
			Lazy.Provider<PostViewHolder.Dimensions>, CommentTextView.RecyclerKeeper.Holder,
			View.OnAttachStateChangeListener, CommentTextView.LimitListener, View.OnClickListener {
		public static class Dimensions {
			public final int thumbnailWidth;
			public final int multipleAttachmentInfoWidth;
			public final int commentAdditionalHeight;

			public Dimensions(int thumbnailWidth, int multipleAttachmentInfoWidth, int commentAdditionalHeight) {
				this.thumbnailWidth = thumbnailWidth;
				this.multipleAttachmentInfoWidth = multipleAttachmentInfoWidth;
				this.commentAdditionalHeight = commentAdditionalHeight;
			}
		}

		public final Dimensions dimensions;
		public final PostLinearLayout layout;
		public final LinebreakLayout head;
		public final TextView number;
		public final TextView name;
		public final TextView index;
		public final TextView date;
		public final ViewGroup attachments;
		public final AttachmentView thumbnail;
		public final TextView attachmentInfo;
		public final CommentTextView comment;
		public final View textSelectionPadding;
		public final View textBarPadding;
		public final View bottomBar;
		public final TextView bottomBarReplies;
		public final TextView bottomBarExpand;
		public final TextView bottomBarOpenThread;

		public ArrayList<AttachmentHolder> attachmentHolders;
		public int attachmentViewCount = 1;
		public ArrayList<ImageView> badgeImages;
		public final ImageView[] stateImages = new ImageView[PostState.POST_ITEM_STATES.size()];
		public final int highlightBackgroundColor;

		public final UiManager.ThumbnailClickListener thumbnailClickListener;
		public final UiManager.ThumbnailLongClickListener thumbnailLongClickListener;

		public UiManager.Selection selection;
		public Animator expandAnimator;
		public NewPostAnimation newPostAnimation;
		public long lastCommentClick;

		public PostViewHolder(ViewGroup parent, UiManager uiManager, Lazy<Dimensions> dimensions) {
			super(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_post, parent, false));

			layout = (PostLinearLayout) itemView;
			layout.addOnAttachStateChangeListener(this);
			ViewUtils.setSelectableItemBackground(layout);
			head = itemView.findViewById(R.id.head);
			number = itemView.findViewById(R.id.number);
			name = itemView.findViewById(R.id.name);
			index = itemView.findViewById(R.id.index);
			date = itemView.findViewById(R.id.date);
			fillStateImages(head, head.indexOfChild(number) + 1, stateImages, PostState.POST_ITEM_STATES, 0, 0, 0);
			attachments = itemView.findViewById(R.id.attachments);
			thumbnail = itemView.findViewById(R.id.thumbnail);
			attachmentInfo = itemView.findViewById(R.id.attachment_info);
			comment = itemView.findViewById(R.id.comment);
			textSelectionPadding = itemView.findViewById(R.id.text_selection_padding);
			textBarPadding = itemView.findViewById(R.id.text_bar_padding);
			bottomBar = itemView.findViewById(R.id.bottom_bar);
			bottomBarReplies = itemView.findViewById(R.id.bottom_bar_replies);
			bottomBarExpand = itemView.findViewById(R.id.bottom_bar_expand);
			bottomBarOpenThread = itemView.findViewById(R.id.bottom_bar_open_thread);
			highlightBackgroundColor = ThemeEngine.getColorScheme(itemView.getContext()).highlightBackgroundColor;

			thumbnailClickListener = uiManager.interaction().createThumbnailClickListener();
			thumbnailLongClickListener = uiManager.interaction().createThumbnailLongClickListener();
			ListViewUtils.bind(this, itemView, true, null, this);

			head.setOnTouchListener(uiManager.view().headContentTouchListener);
			comment.setLimitListener(this);
			comment.setSpanStateListener(uiManager.view().spanStateListener);
			comment.setPrepareToCopyListener(uiManager.view().prepareToCopyListener);
			comment.setExtraButtons(uiManager.view().extraButtons);
			thumbnail.setOnClickListener(thumbnailClickListener);
			thumbnail.setOnLongClickListener(thumbnailLongClickListener);
			bottomBarReplies.setOnClickListener(uiManager.view().repliesBlockClickListener);
			bottomBarExpand.setOnClickListener(this);
			bottomBarOpenThread.setOnClickListener(uiManager.view().threadLinkBlockClickListener);

			index.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			if (C.API_LOLLIPOP) {
				bottomBarReplies.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
				bottomBarExpand.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
				bottomBarOpenThread.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			}
			float textScale = Preferences.getTextScale();
			if (textScale != 1f) {
				ViewUtils.applyScaleSize(textScale, number, name, index, date, comment, attachmentInfo,
						bottomBarReplies, bottomBarExpand, bottomBarOpenThread);
				ViewUtils.applyScaleSize(textScale, stateImages);
				head.setHorizontalSpacing((int) (head.getHorizontalSpacing() * textScale));
			}

			this.dimensions = dimensions.get(this);
			thumbnail.setDrawTouching(true);
			ViewGroup.LayoutParams thumbnailLayoutParams = thumbnail.getLayoutParams();
			float thumbnailsScale = Preferences.getThumbnailsScale();
			if (thumbnailsScale != 1f) {
				thumbnailLayoutParams.width = (int) (this.dimensions.thumbnailWidth * thumbnailsScale);
				thumbnailLayoutParams.height = thumbnailLayoutParams.width;
			} else {
				thumbnailLayoutParams.width = this.dimensions.thumbnailWidth;
			}
		}

		@Override
		public Dimensions createLazy() {
			float density = ResourceUtils.obtainDensity(itemView);
			Configuration configuration = itemView.getResources().getConfiguration();
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec
					((int) (320 * density + 0.5f), View.MeasureSpec.AT_MOST);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			itemView.measure(widthMeasureSpec, heightMeasureSpec);
			int commentAdditionalHeight = bottomBar.getMeasuredHeight();
			int thumbnailWidth = head.getMeasuredHeight();
			// Approximately equals to thumbnail width + right padding
			int additionalAttachmentInfoWidthDp = 64;
			int minAttachmentInfoWidthDp = additionalAttachmentInfoWidthDp + 68;
			int maxAttachmentInfoWidthDp = additionalAttachmentInfoWidthDp + 84;
			int attachmentInfoWidthDp = configuration.smallestScreenWidthDp * minAttachmentInfoWidthDp / 320;
			attachmentInfoWidthDp = Math.max(Math.min(attachmentInfoWidthDp, maxAttachmentInfoWidthDp),
					minAttachmentInfoWidthDp);
			attachmentInfoWidthDp -= additionalAttachmentInfoWidthDp;
			int multipleAttachmentInfoWidth = (int) (attachmentInfoWidthDp * density + 0.5f);
			return new Dimensions(thumbnailWidth, multipleAttachmentInfoWidth, commentAdditionalHeight);
		}

		public void installBackground() {
			if (ViewCompat.isAttachedToWindow(itemView)) {
				installBackgroundUnchecked();
			}
		}

		private void installBackgroundUnchecked() {
			if (newPostAnimation != null) {
				newPostAnimation.cancel();
				newPostAnimation = null;
			}
			PostItem postItem = getPostItem();
			UiManager.ConfigurationSet configurationSet = getConfigurationSet();
			if (selection == UiManager.Selection.DISABLED &&
					!configurationSet.postStateProvider.isRead(postItem.getPostNumber())) {
				switch (Preferences.getHighlightUnreadMode()) {
					case AUTOMATICALLY: {
						newPostAnimation = new NewPostAnimation(layout,
								configurationSet.postStateProvider, postItem.getPostNumber(),
								highlightBackgroundColor);
						break;
					}
					case MANUALLY: {
						layout.setSecondaryBackgroundColor(highlightBackgroundColor);
						break;
					}
					case NEVER: {
						layout.setSecondaryBackground(null);
						break;
					}
					default: {
						throw new IllegalStateException();
					}
				}
			} else if (selection == UiManager.Selection.SELECTED) {
				layout.setSecondaryBackgroundColor(highlightBackgroundColor);
			} else {
				layout.setSecondaryBackground(null);
			}
		}

		public void resetAnimations() {
			if (expandAnimator != null) {
				expandAnimator.cancel();
				expandAnimator = null;
				comment.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
			}
			if (newPostAnimation != null) {
				newPostAnimation.cancel();
				newPostAnimation = null;
			}
		}

		public void invalidateBottomBar() {
			boolean repliesVisible = bottomBarReplies.getVisibility() == View.VISIBLE;
			boolean expandVisible = bottomBarExpand.getVisibility() == View.VISIBLE;
			boolean openThreadVisible = bottomBarOpenThread.getVisibility() == View.VISIBLE;
			boolean needBar = repliesVisible || expandVisible || openThreadVisible;
			bottomBarReplies.getLayoutParams().width = repliesVisible && !expandVisible && !openThreadVisible ?
					ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
			bottomBarExpand.getLayoutParams().width = expandVisible && !openThreadVisible ?
					ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
			bottomBar.setVisibility(needBar ? View.VISIBLE : View.GONE);
			boolean hasText = comment.getVisibility() == View.VISIBLE;
			float density = ResourceUtils.obtainDensity(textBarPadding);
			textBarPadding.getLayoutParams().height = (int) ((needBar ? 0f : hasText ? 10f : 6f) * density);
		}

		@Override
		public void onViewAttachedToWindow(View v) {
			installBackgroundUnchecked();
		}

		@Override
		public void onViewDetachedFromWindow(View v) {
			resetAnimations();
		}

		@Override
		public void onApplyLimit(boolean limited) {
			if (limited != (bottomBarExpand.getVisibility() == View.VISIBLE)) {
				bottomBarExpand.setVisibility(limited ? View.VISIBLE : View.GONE);
				invalidateBottomBar();
			}
		}

		@Override
		public void onClick(View v) {
			PostItem postItem = getPostItem();
			PostNumber postNumber = postItem.getPostNumber();
			UiManager.ConfigurationSet configurationSet = getConfigurationSet();
			if (v == bottomBarExpand && !configurationSet.postStateProvider.isExpanded(postNumber)) {
				configurationSet.postStateProvider.setExpanded(postNumber);
				comment.setLinesLimit(0, 0);
				bottomBarExpand.setVisibility(View.GONE);
				int bottomBarHeight = bottomBar.getHeight();
				invalidateBottomBar();
				if (expandAnimator != null) {
					expandAnimator.cancel();
				}
				int fromHeight = comment.getHeight();
				AnimationUtils.measureDynamicHeight(comment);
				int toHeight = comment.getMeasuredHeight();
				if (bottomBarHeight > 0 && bottomBar.getVisibility() == View.GONE) {
					// When button bar becomes hidden, height of the view becomes smaller, so it can cause
					// a short list jump; Solution - start the animation from fromHeight + bottomBarHeight
					fromHeight += bottomBarHeight;
				}
				if (toHeight > fromHeight) {
					float density = ResourceUtils.obtainDensity(comment);
					float value = (toHeight - fromHeight) / density / 400;
					if (value > 1f) {
						value = 1f;
					} else if (value < 0.2f) {
						value = 0.2f;
					}
					Animator animator = AnimationUtils.ofHeight(comment, fromHeight,
							ViewGroup.LayoutParams.WRAP_CONTENT, false);
					this.expandAnimator = animator;
					animator.setDuration((int) (200 * value));
					animator.start();
				}
			}
		}

		@Override
		protected void onConfigure(PostItem postItem, UiManager.ConfigurationSet configurationSet) {
			thumbnail.applyRoundedCorners(getPostBackgroundColor(itemView.getContext(), configurationSet));
		}

		@Override
		public CommentTextView getCommentTextView() {
			return comment;
		}
	}

	private static class HiddenViewHolder extends BasePostViewHolder {
		public final TextView index;
		public final TextView number;
		public final TextView comment;

		private static View createBaseView(ViewGroup parent, boolean card) {
			if (card) {
				CardView cardView = createCardLayout(parent);
				ViewGroup cardContent = (ViewGroup) cardView.getChildAt(0);
				LayoutInflater.from(cardView.getContext()).inflate(R.layout.list_item_hidden, cardContent);
				return cardView;
			} else {
				return LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_hidden, parent, false);
			}
		}

		public HiddenViewHolder(ViewGroup parent, boolean card, boolean thread) {
			super(createBaseView(parent, card));

			index = itemView.findViewById(R.id.index);
			number = itemView.findViewById(R.id.number);
			comment = itemView.findViewById(R.id.comment);
			itemView.findViewById(R.id.head).setAlpha(ALPHA_HIDDEN_POST);

			float textScale = Preferences.getTextScale();
			ViewUtils.applyScaleSize(textScale, index, number, comment);
			ViewUtils.applyScaleMarginLR(textScale, index, number, comment);
			index.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			if (thread) {
				index.setVisibility(View.GONE);
				number.setVisibility(View.GONE);
			}
			if (card) {
				CardView cardView = (CardView) itemView;
				ViewGroup cardContent = (ViewGroup) cardView.getChildAt(0);
				ListViewUtils.bind(this, cardContent, true, null, this);
			} else {
				ViewUtils.setSelectableItemBackground(itemView);
				ListViewUtils.bind(this, itemView, true, null, this);
			}
		}
	}
}
