package com.mishiranu.dashchan.content.model;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Pair;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.storage.HiddenThreadsDatabase;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.text.style.NameColorSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.PostDateFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

public class PostItem implements AttachmentItem.Binder, ChanMarkup.MarkupExtra, Comparable<PostItem> {
	private final String chanName;
	private final String boardName;
	private ArrayList<AttachmentItem> attachmentItems;
	private ArrayList<Pair<Uri, String>> icons;

	public static final int ORDINAL_INDEX_NONE = -1;
	public static final int ORDINAL_INDEX_DELETED = -2;

	private int ordinalIndex = ORDINAL_INDEX_NONE;

	private String subject;
	private CharSequence comment;
	private CharSequence fullName;
	private ColorScheme.Span[] commentSpans;
	private ColorScheme.Span[] fullNameSpans;
	private LinkSpan[] linkSpans;
	private LinkSuffixSpan[] linkSuffixSpans;
	private PostDateFormatter.Holder dateTimeHolder;
	private boolean useDefaultName;

	private HashSet<String> referencesTo;
	private LinkedHashSet<String> referencesFrom;

	private boolean expanded = false;

	private final Post post;
	private final ThreadData threadData;

	private static class ThreadData {
		public int postsCount;
		public int filesCount;
		public int postsWithFilesCount;
		public Post[] firstAndLastPosts;
		public CharSequence commentShort;
		public ColorScheme.Span[] commentShortSpans;
		public GalleryItem.GallerySet gallerySet;
	}

	public enum HideState {UNDEFINED, HIDDEN, SHOWN}

	private HideState hideState = HideState.UNDEFINED;
	private String hideReason;
	private boolean unread = false;

	public PostItem(Post post, String chanName, String boardName) {
		this.post = post;
		threadData = null;
		this.chanName = chanName;
		this.boardName = boardName;
		init();
	}

	public PostItem(Posts thread, String chanName, String boardName) {
		Post[] posts = thread.getPosts();
		this.post = posts[0];
		threadData = new ThreadData();
		threadData.postsCount = thread.getPostsCount();
		threadData.filesCount = thread.getFilesCount();
		threadData.postsWithFilesCount = thread.getPostsWithFilesCount();
		threadData.firstAndLastPosts = posts;
		this.chanName = chanName;
		this.boardName = boardName;
		init();
	}

	private void init() {
		attachmentItems = AttachmentItem.obtain(this);
		if (isThreadItem()) {
			ThreadData threadData = this.threadData;
			if (attachmentItems != null) {
				threadData.gallerySet = new GalleryItem.GallerySet(false);
				threadData.gallerySet.setThreadTitle(getSubjectOrComment());
				threadData.gallerySet.add(attachmentItems);
			}
			threadData.commentShort = obtainThreadComment(post.getWorkComment(), chanName, this);
			threadData.commentShortSpans = ColorScheme.getSpans(threadData.commentShort);
		} else {
			String comment = post.getWorkComment();
			referencesTo = parseReferencesTo(referencesTo, comment);
		}
		ArrayList<Pair<Uri, String>> icons = null;
		for (int i = 0, count = post.getIconsCount(); i < count; i++) {
			Icon icon = post.getIconAt(i);
			if (icon != null) {
				if (icons == null) {
					icons = new ArrayList<>();
				}
				icons.add(new Pair<>(icon.getRelativeUri(), icon.getTitle()));
			}
		}
		this.icons = icons;
	}

	public Post getPost() {
		return post;
	}

	public static HashSet<String> parseReferencesTo(HashSet<String> referencesTo, String comment) {
		if (referencesTo != null) {
			referencesTo.clear();
		}
		if (comment != null) {
			// Fast find <a.+?>(?:>>|&gt;&gt;)(\d+)</a>
			int index1 = -1;
			while (true) {
				index1 = StringUtils.nearestIndexOf(comment, index1, "<a ", "<a\n", "<a\r");
				if (index1 == -1) {
					break;
				}
				index1 = comment.indexOf(">", index1);
				if (index1 == -1) {
					break;
				}
				int index2 = comment.indexOf("</a>", index1);
				if (index2 > index1++) {
					int start = -1;
					String text = comment.substring(index1, index2);
					int length = index2 - index1;
					if (text.startsWith(">>")) {
						start = 2;
					} else if (text.startsWith("&gt;&gt;")) {
						start = 8;
					}
					if (start >= 0 && start < length) {
						boolean number = true;
						for (int i = start; i < length; i++) {
							char c = text.charAt(i);
							if (c < '0' || c > '9') {
								number = false;
								break;
							}
						}
						if (!number) {
							continue;
						}
						if (referencesTo == null) {
							referencesTo = new HashSet<>();
						}
						referencesTo.add(text.substring(start));
					}
				} else {
					break;
				}
			}
		}
		return referencesTo;
	}

	public void addReferenceFrom(String postNumber) {
		if (referencesFrom == null) {
			referencesFrom = new LinkedHashSet<>();
		}
		referencesFrom.add(postNumber);
	}

	public void removeReferenceFrom(String postNumber) {
		if (referencesFrom != null) {
			referencesFrom.remove(postNumber);
		}
	}

	public void clearReferencesFrom() {
		if (referencesFrom != null) {
			referencesFrom.clear();
		}
	}

	public void setOrdinalIndex(int ordinalIndex) {
		this.ordinalIndex = ordinalIndex;
	}

	public void preload() {
		getComment();
	}

	public int getOrdinalIndex() {
		return ordinalIndex;
	}

	public String getOrdinalIndexString() {
		if (ordinalIndex >= 0) {
			return Integer.toString(ordinalIndex + 1);
		}
		if (ordinalIndex == ORDINAL_INDEX_DELETED) {
			return "X";
		}
		return null;
	}

	@Override
	public String getChanName() {
		return chanName;
	}

	@Override
	public String getBoardName() {
		return boardName;
	}

	@Override
	public String getThreadNumber() {
		return post.getThreadNumberOrOriginalPostNumber();
	}

	@Override
	public String getPostNumber() {
		return post.getPostNumber();
	}

	public String getOriginalPostNumber() {
		return post.getOriginalPostNumber();
	}

	public String getParentPostNumber() {
		return post.getParentPostNumberOrNull();
	}

	@Override
	public int compareTo(PostItem another) {
		return post.compareTo(another.getPost());
	}

	// Returns whether name is default. Call this method only after getFullName.
	public boolean isUseDefaultName() {
		return useDefaultName;
	}

	private CharSequence makeFullName() {
		String name = post.getName();
		String identifier = post.getIdentifier();
		String tripcode = post.getTripcode();
		String capcode = post.getCapcode();
		String defaultName = ChanConfiguration.get(getChanName()).getDefaultName(boardName);
		if (StringUtils.isEmptyOrWhitespace(defaultName)) {
			defaultName = "Anonymous";
		}
		if (StringUtils.isEmptyOrWhitespace(name)) {
			name = defaultName;
		} else {
			name = name.trim();
		}
		boolean useDefaultName = post.isDefaultName() || name.equals(defaultName);
		boolean hasIdentifier = !StringUtils.isEmptyOrWhitespace(identifier);
		boolean hasTripcode = !StringUtils.isEmptyOrWhitespace(tripcode);
		boolean hasCapcode = !StringUtils.isEmptyOrWhitespace(capcode);
		CharSequence fullName;
		if (hasIdentifier || hasTripcode || hasCapcode) {
			SpannableStringBuilder spannable = new SpannableStringBuilder();
			if (!useDefaultName) {
				spannable.append(name);
			}
			if (hasIdentifier) {
				if (spannable.length() > 0) {
					spannable.append(' ');
				}
				StringUtils.appendSpan(spannable, identifier, new NameColorSpan(NameColorSpan.TYPE_TRIPCODE));
			}
			if (hasTripcode) {
				if (spannable.length() > 0) {
					spannable.append(' ');
				}
				StringUtils.appendSpan(spannable, tripcode, new NameColorSpan(NameColorSpan.TYPE_TRIPCODE));
			}
			if (hasCapcode) {
				if (spannable.length() > 0) {
					spannable.append(' ');
				}
				StringUtils.appendSpan(spannable, "## " + capcode, new NameColorSpan(NameColorSpan.TYPE_CAPCODE));
			}
			fullName = spannable;
			useDefaultName = false;
		} else {
			fullName = name;
		}
		this.useDefaultName = useDefaultName;
		return fullName;
	}

	// Returns spanned name and tripcode, guaranteed not null.
	public CharSequence getFullName() {
		if (fullName == null) {
			synchronized (this) {
				if (fullName == null) {
					CharSequence fullName = makeFullName();
					if (StringUtils.isEmpty(fullName)) {
						fullName = "";
					}
					fullNameSpans = ColorScheme.getSpans(fullName);
					this.fullName = fullName;
				}
			}
		}
		return fullName;
	}

	public ColorScheme.Span[] getFullNameSpans() {
		return fullNameSpans;
	}

	public String getEmail() {
		return post.getEmail();
	}

	public boolean isSage() {
		return post.isSage() && getParentPostNumber() != null;
	}

	public boolean isSticky() {
		return post.isSticky() && getParentPostNumber() == null;
	}

	public boolean isClosed() {
		return (post.isClosed() || post.isArchived()) && getParentPostNumber() == null;
	}

	public boolean isCyclical() {
		return post.isCyclical() && getParentPostNumber() == null;
	}

	public boolean isOriginalPoster() {
		return post.isOriginalPoster() || getParentPostNumber() == null;
	}

	public boolean isPosterWarned() {
		return post.isPosterWarned();
	}

	public boolean isPosterBanned() {
		return post.isPosterBanned();
	}

	public enum BumpLimitState {NOT_REACHED, REACHED, NEED_COUNT}

	public BumpLimitState getBumpLimitReachedState(int postsCount) {
		if (getParentPostNumber() != null || isSticky() || isCyclical()) {
			return BumpLimitState.NOT_REACHED;
		}
		if (post.isBumpLimitReached()) {
			return BumpLimitState.REACHED;
		}
		if (threadData != null) {
			postsCount = threadData.postsCount;
		}
		if (postsCount > 0) {
			ChanConfiguration configuration = ChanConfiguration.get(getChanName());
			int bumpLimit = configuration.getBumpLimitWithMode(getBoardName());
			if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
				return postsCount >= bumpLimit ? BumpLimitState.REACHED : BumpLimitState.NOT_REACHED;
			}
		}
		return threadData != null ? BumpLimitState.NOT_REACHED : BumpLimitState.NEED_COUNT;
	}

	public boolean isDeleted() {
		return post.isDeleted();
	}

	public boolean isUserPost() {
		return post.isUserPost();
	}

	public void setUserPost(boolean userPost) {
		post.setUserPost(userPost);
	}

	public String getSubjectOrComment() {
		String subject = getSubject();
		if (!StringUtils.isEmpty(subject)) {
			return subject;
		}
		return StringUtils.cutIfLongerToLine(HtmlParser.clear(post.getWorkComment()), 50, true);
	}

	// Returns thread subject, guaranteed not null.
	public String getSubject() {
		if (subject == null) {
			synchronized (this) {
				if (subject == null) {
					String subject = post.getSubject();
					if (subject != null) {
						subject = subject.replace("\r", "").replace("\n", " ").trim();
						if (subject.length() == 1 && (subject.charAt(0) == '\u202d'
								|| subject.charAt(0) == '\u202e')) {
							subject = "";
						}
					} else {
						subject = "";
					}
					this.subject = subject;
				}
			}
		}
		return subject;
	}

	private static CharSequence obtainComment(String comment, String chanName, String parentPostNumber,
											  ChanMarkup.MarkupExtra extra) {
		return StringUtils.isEmpty(comment) ? "" : HtmlParser.spanify(comment, ChanMarkup.get(chanName),
				StringUtils.emptyIfNull(parentPostNumber), extra);
	}

	private static CharSequence obtainThreadComment(String comment, String chanName, ChanMarkup.MarkupExtra extra) {
		SpannableStringBuilder commentBuilder = new SpannableStringBuilder(obtainComment(comment,
				chanName, null, extra));
		int linebreaks = 0;
		// Remove more than one linebreaks in sequence
		for (int i = commentBuilder.length() - 1; i >= 0; i--) {
			char c = commentBuilder.charAt(i);
			if (c == '\n') {
				linebreaks++;
			} else {
				if (linebreaks > 1) {
					// Remove linebreaks - 1 characters, keeping one line break
					commentBuilder.delete(i + 1, i + linebreaks);
				}
				linebreaks = 0;
			}
		}
		return commentBuilder;
	}

	public CharSequence getComment() {
		if (comment == null) {
			synchronized (this) {
				if (comment == null) {
					CharSequence comment = obtainComment(post.getWorkComment(), getChanName(),
							getParentPostNumber(), this);
					// Make empty lines take less space
					SpannableStringBuilder builder = null;
					int linebreaks = 0;
					for (int i = 0; i < comment.length(); i++) {
						char c = comment.charAt(i);
						if (c == '\n') {
							linebreaks++;
						} else {
							if (linebreaks > 1) {
								if (builder == null) {
									builder = new SpannableStringBuilder(comment);
									comment = builder;
								}
								builder.setSpan(new RelativeSizeSpan(0.75f), i - linebreaks, i,
										SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
							}
							linebreaks = 0;
						}
					}
					commentSpans = ColorScheme.getSpans(comment);
					linkSpans = comment instanceof Spanned ? ((Spanned) comment)
							.getSpans(0, comment.length(), LinkSpan.class) : null;
					linkSuffixSpans = comment instanceof Spanned ? ((Spanned) comment)
							.getSpans(0, comment.length(), LinkSuffixSpan.class) : null;
					this.comment = comment;
				}
			}
		}
		return comment;
	}

	// Returns spanned comment (post), guaranteed not null.
	public CharSequence getComment(String repliesToPost) {
		SpannableString comment = new SpannableString(getComment());
		LinkSpan[] spans = comment.getSpans(0, comment.length(), LinkSpan.class);
		if (spans != null) {
			String commentString = comment.toString();
			repliesToPost = ">>" + repliesToPost;
			for (LinkSpan linkSpan : spans) {
				int start = comment.getSpanStart(linkSpan);
				if (commentString.indexOf(repliesToPost, start) == start) {
					int end = comment.getSpanEnd(linkSpan);
					comment.setSpan(new StyleSpan(Typeface.BOLD), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		}
		return comment;
	}

	public ColorScheme.Span[] getCommentSpans() {
		return commentSpans;
	}

	public CharSequence getThreadCommentShort(int maxWidth, float textSize, int maxLines) {
		float factor = maxWidth * maxLines / textSize;
		int count = (int) (factor * 3f);
		CharSequence comment = threadData.commentShort;
		if (comment instanceof Spanned) {
			SpoilerSpan[] spoilerSpans = ((Spanned) comment).getSpans(0, comment.length(), SpoilerSpan.class);
			if (spoilerSpans != null) {
				boolean enabled = !Preferences.isShowSpoilers();
				for (SpoilerSpan spoilerSpan : spoilerSpans) {
					spoilerSpan.setEnabled(enabled);
				}
			}
		}
		return count > 0 && comment.length() > count ? comment.subSequence(0, count) : comment;
	}

	public ColorScheme.Span[] getThreadCommentShortSpans() {
		return threadData.commentShortSpans;
	}

	public String getRawComment() {
		return post.getWorkComment();
	}

	public String getCommentMarkup() {
		String commentMarkup = post.getCommentMarkup();
		String comment = post.getWorkComment();
		if (StringUtils.isEmpty(commentMarkup)) {
			if (!StringUtils.isEmpty(comment)) {
				commentMarkup = HtmlParser.unmark(comment, ChanMarkup.get(chanName), this);
			} else {
				commentMarkup = "";
			}
		}
		return commentMarkup;
	}

	// Must be called only after getComment.
	public LinkSuffixSpan[] getLinkSuffixSpansAfterComment() {
		return linkSuffixSpans;
	}

	// Must be called only after getComment.
	public LinkSpan[] getLinkSpansAfterComment() {
		return linkSpans;
	}

	public ArrayList<Pair<Uri, String>> getIcons() {
		return icons;
	}

	public boolean hasAttachments() {
		return attachmentItems != null;
	}

	public ArrayList<AttachmentItem> getAttachmentItems() {
		return attachmentItems;
	}

	public String getAttachmentsDescription(Context context, AttachmentItem.FormatMode formatMode) {
		int count = attachmentItems.size();
		if (count == 1) {
			AttachmentItem attachmentItem = attachmentItems.get(0);
			return attachmentItem.getDescription(formatMode);
		} else {
			int size = 0;
			for (int i = 0; i < count; i++) {
				size += attachmentItems.get(i).getSize();
			}
			StringBuilder builder = new StringBuilder();
			if (size > 0) {
				builder.append(AttachmentItem.formatSize(size)).append(' ');
			}
			builder.append(context.getResources().getQuantityString(R.plurals
					.number_files__format, count, count));
			return builder.toString();
		}
	}

	public int getPostReplyCount() {
		return referencesFrom != null ? referencesFrom.size() : 0;
	}

	// May return null set.
	public HashSet<String> getReferencesTo() {
		return referencesTo;
	}

	// May return null set.
	public LinkedHashSet<String> getReferencesFrom() {
		return referencesFrom;
	}

	public GalleryItem.GallerySet getThreadGallerySet() {
		return threadData.gallerySet;
	}

	public int getThreadPostsCount() {
		return threadData.postsCount;
	}

	public PostItem[] getThreadLastPosts() {
		Post[] posts = threadData.firstAndLastPosts;
		if (posts != null && posts.length > 1) {
			PostItem[] postItems = new PostItem[posts.length - 1];
			int startIndex = threadData.postsCount - posts.length + 1;
			for (int i = 0; i < postItems.length; i++) {
				PostItem postItem = new PostItem(posts[i + 1], chanName, boardName);
				postItem.setOrdinalIndex(startIndex > 0 ? startIndex + i : ORDINAL_INDEX_NONE);
				postItems[i] = postItem;
			}
			return postItems;
		}
		return null;
	}

	static final String CARD_DESCRIPTION_DIVIDER = "   ";

	static String makeNbsp(String s) {
		if (s != null) {
			s = s.replace(' ', '\u00a0');
		}
		return s;
	}

	public String formatThreadCardDescription(Context context, boolean repliesOnly) {
		StringBuilder builder = new StringBuilder();
		int originalPostFiles = post.getAttachmentsCount();
		int replies = threadData.postsCount - 1;
		int files = threadData.filesCount - originalPostFiles;
		int postsWithFiles = threadData.postsWithFilesCount - (originalPostFiles > 0 ? 1 : 0);
		boolean hasInformation = false;
		Resources resources = context.getResources();
		if (replies >= 0) {
			hasInformation = true;
			builder.append(makeNbsp(resources.getQuantityString(R.plurals.number_replies__format,
					replies, replies)));
		}
		if (!repliesOnly) {
			if (postsWithFiles >= 0) {
				if (hasInformation) {
					builder.append(CARD_DESCRIPTION_DIVIDER);
				} else {
					hasInformation = true;
				}
				builder.append(makeNbsp(resources.getString(R.string.number_with_files__format, postsWithFiles)));
			} else if (files >= 0) {
				if (hasInformation) {
					builder.append(CARD_DESCRIPTION_DIVIDER);
				} else {
					hasInformation = true;
				}
				builder.append(makeNbsp(resources.getQuantityString(R.plurals.number_files__format,
						files, files)));
			}
			if (hasAttachments()) {
				int size = 0;
				for (AttachmentItem attachmentItem : attachmentItems) {
					size += attachmentItem.getSize();
				}
				if (size > 0) {
					if (hasInformation) {
						builder.append(CARD_DESCRIPTION_DIVIDER);
					} else {
						hasInformation = true;
					}
					builder.append(AttachmentItem.formatSize(size).replace(' ', '\u00a0'));
				}
			}
		}
		if (!hasInformation) {
			builder.append(makeNbsp(resources.getString(R.string.no_information)));
		}
		return builder.toString();
	}

	public long getTimestamp() {
		return post.getTimestamp();
	}

	public String getDateTime(PostDateFormatter formatter) {
		long time = getTimestamp();
		if (time > 0L) {
			dateTimeHolder = formatter.format(getTimestamp(), dateTimeHolder);
			return dateTimeHolder.text;
		} else {
			return null;
		}
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public boolean isThreadItem() {
		return threadData != null;
	}

	private HideState getHiddenStateFromModel() {
		if (post.isHidden()) {
			return HideState.HIDDEN;
		}
		if (post.isShown()) {
			return HideState.SHOWN;
		}
		return HideState.UNDEFINED;
	}

	public interface HidePerformer {
		public String checkHidden(PostItem postItem);
	}

	public boolean isHidden(HidePerformer hidePerformer) {
		if (hideState == HideState.UNDEFINED) {
			synchronized (this) {
				HideState hideState = this.hideState;
				if (hideState == HideState.UNDEFINED) {
					String hideReason = null;
					if (threadData == null) {
						hideState = getHiddenStateFromModel();
					} else {
						hideState = HiddenThreadsDatabase.getInstance()
								.check(getChanName(), boardName, getThreadNumber());
					}
					if (hideState == HideState.UNDEFINED) {
						hideReason = hidePerformer.checkHidden(this);
						if (hideReason != null) {
							hideState = HideState.HIDDEN;
						} else {
							hideState = HideState.SHOWN;
						}
					}
					this.hideState = hideState;
					this.hideReason = hideReason;
				}
			}
		}
		return hideState == HideState.HIDDEN;
	}

	// Must be called only after isHidden.
	public boolean isHiddenUnchecked() {
		return hideState == HideState.HIDDEN;
	}

	public String getHideReason() {
		return hideReason;
	}

	public void setHidden(boolean hidden) {
		this.hideState = hidden ? HideState.HIDDEN : HideState.SHOWN;
		hideReason = null;
		if (!isThreadItem()) {
			post.setHidden(hidden);
		}
	}

	public void invalidateHidden() {
		hideState = HideState.UNDEFINED;
		hideReason = null;
	}

	public void resetHidden() {
		invalidateHidden();
		post.resetHidden();
	}

	public void setUnread(boolean unread) {
		this.unread = unread;
	}

	public boolean isUnread() {
		return unread;
	}
}
