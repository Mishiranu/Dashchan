package com.mishiranu.dashchan.content.model;

import android.content.res.Resources;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import androidx.annotation.NonNull;
import chan.content.Chan;
import chan.content.ChanConfiguration;
import chan.content.ChanMarkup;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.text.style.MediumSpan;
import com.mishiranu.dashchan.text.style.NameColorSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.PostDateFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class PostItem implements AttachmentItem.Master, ChanMarkup.MarkupExtra, Comparable<PostItem>,
		Preferences.CatalogSort.Comparable {
	public enum HideState {
		UNDEFINED(false),
		HIDDEN(true),
		SHOWN(false);

		public final boolean hidden;

		HideState(boolean hidden) {
			this.hidden = hidden;
		}

		public static class Map<T> {
			private final HashMap<T, Boolean> map = new HashMap<>();

			public HideState get(T key) {
				Boolean hidden = map.get(key);
				return hidden == null ? UNDEFINED : hidden ? HIDDEN : SHOWN;
			}

			public void set(T key, HideState hideState) {
				switch (hideState) {
					case HIDDEN: {
						map.put(key, true);
						break;
					}
					case SHOWN: {
						map.put(key, false);
						break;
					}
					default: {
						map.remove(key);
						break;
					}
				}
			}

			public void addAll(Map<T> map) {
				this.map.putAll(map.map);
			}

			public void clear() {
				map.clear();
			}

			public int size() {
				return map.size();
			}
		}
	}

	private final Post post;
	private final ThreadData threadData;
	private final String boardName;
	private final String threadNumber;
	private final PostNumber originalPostNumber;
	private final List<AttachmentItem> attachmentItems;

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

	private final Set<PostNumber> referencesTo;
	private Set<PostNumber> referencesFrom;

	private HideState hideState = HideState.UNDEFINED;
	private String hideReason;

	private static class ThreadData {
		public static class Base {
			public final int postsCount;
			public final int filesCount;
			public final int postsWithFilesCount;
			public final List<Post> posts;

			public Base(int postsCount, int filesCount, int postsWithFilesCount, List<Post> posts) {
				this.postsCount = postsCount;
				this.filesCount = filesCount;
				this.postsWithFilesCount = postsWithFilesCount;
				this.posts = posts;
			}
		}

		public final Base base;
		public final CharSequence commentShort;
		public final ColorScheme.Span[] commentShortSpans;
		public final GalleryItem.Set gallerySet;

		public ThreadData(Base base, CharSequence commentShort, ColorScheme.Span[] commentShortSpans,
				GalleryItem.Set gallerySet) {
			this.base = base;
			this.commentShort = commentShort;
			this.commentShortSpans = commentShortSpans;
			this.gallerySet = gallerySet;
		}
	}

	public static PostItem createPost(Post post, Chan chan,
			String boardName, String threadNumber, PostNumber originalPostNumber) {
		return new PostItem(post, null, chan, boardName, threadNumber, originalPostNumber);
	}

	public static PostItem createThread(List<Post> posts, int postsCount, int filesCount, int postsWithFilesCount,
			Chan chan, String boardName, String threadNumber) {
		Post post = posts.get(0);
		ThreadData.Base threadData = new ThreadData.Base(postsCount, filesCount, postsWithFilesCount, posts);
		return new PostItem(post, threadData, chan, boardName, threadNumber, post.number);
	}

	private PostItem(Post post, ThreadData.Base threadDataBase, Chan chan,
			String boardName, String threadNumber, PostNumber originalPostNumber) {
		this.post = post;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.originalPostNumber = originalPostNumber;
		attachmentItems = AttachmentItem.obtain(this, post, chan.locator);
		if (threadDataBase != null) {
			CharSequence commentShort = obtainThreadComment(post.comment, chan.markup, this);
			ColorScheme.Span[] commentShortSpans = ColorScheme.getSpans(commentShort);
			GalleryItem.Set gallerySet = null;
			if (attachmentItems != null) {
				gallerySet = new GalleryItem.Set(false);
				gallerySet.setThreadTitle(getSubjectOrComment());
				gallerySet.put(post.number, attachmentItems);
			}
			threadData = new ThreadData(threadDataBase, commentShort, commentShortSpans, gallerySet);
			referencesTo = Collections.emptySet();
		} else {
			threadData = null;
			Set<PostNumber> referencesTo = collectReferences(null, post.comment);
			this.referencesTo = referencesTo != null ? referencesTo : Collections.emptySet();
		}
	}

	public Post getPost() {
		return post;
	}

	public static Set<PostNumber> collectReferences(Set<PostNumber> references, String comment) {
		if (!StringUtils.isEmpty(comment)) {
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
						if (references == null) {
							references = new TreeSet<>();
						}
						references.add(new PostNumber(Integer.parseInt(text.substring(start)), 0));
					}
				} else {
					break;
				}
			}
		}
		return references;
	}

	public void setOrdinalIndex(int ordinalIndex) {
		this.ordinalIndex = ordinalIndex;
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
	public String getBoardName() {
		return boardName;
	}

	@Override
	public String getThreadNumber() {
		return threadNumber;
	}

	@Override
	public PostNumber getPostNumber() {
		return post.number;
	}

	public PostNumber getOriginalPostNumber() {
		return originalPostNumber;
	}

	public boolean isOriginalPost() {
		return originalPostNumber.equals(post.number);
	}

	@Override
	public int compareTo(PostItem another) {
		return post.compareTo(another.getPost());
	}

	// Returns whether name is default. Call this method only after getFullName.
	public boolean isUseDefaultName() {
		return useDefaultName;
	}

	private CharSequence makeFullName(ChanConfiguration configuration) {
		String name = post.name;
		String identifier = post.identifier;
		String tripcode = post.tripcode;
		String capcode = post.capcode;
		String defaultName = configuration.getDefaultName(boardName);
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

	@NonNull
	public CharSequence getFullName(Chan chan) {
		if (fullName == null) {
			CharSequence fullName = makeFullName(chan.configuration);
			if (StringUtils.isEmpty(fullName)) {
				fullName = "";
			}
			fullNameSpans = ColorScheme.getSpans(fullName);
			this.fullName = fullName;
		}
		return fullName;
	}

	public ColorScheme.Span[] getFullNameSpans() {
		return fullNameSpans;
	}

	public String getEmail() {
		return post.email;
	}

	public boolean isSage() {
		return post.isSage() && !isOriginalPost();
	}

	public boolean isSticky() {
		return post.isSticky() && isOriginalPost();
	}

	public boolean isClosed() {
		return (post.isClosed() || post.isArchived()) && isOriginalPost();
	}

	public boolean isCyclical() {
		return post.isCyclical() && isOriginalPost();
	}

	public boolean isOriginalPoster() {
		return post.isOriginalPoster() || isOriginalPost();
	}

	public boolean isPosterWarned() {
		return post.isPosterWarned();
	}

	public boolean isPosterBanned() {
		return post.isPosterBanned();
	}

	public enum BumpLimitState {NOT_REACHED, REACHED, NEED_COUNT}

	public BumpLimitState getBumpLimitReachedState(Chan chan, int postsCount) {
		if (!isOriginalPost() || isSticky() || isCyclical()) {
			return BumpLimitState.NOT_REACHED;
		}
		if (post.isBumpLimitReached()) {
			return BumpLimitState.REACHED;
		}
		if (threadData != null) {
			postsCount = threadData.base.postsCount;
		}
		if (postsCount > 0) {
			int bumpLimit = chan.configuration.getBumpLimitWithMode(getBoardName());
			if (bumpLimit != ChanConfiguration.BUMP_LIMIT_INVALID) {
				return postsCount >= bumpLimit ? BumpLimitState.REACHED : BumpLimitState.NOT_REACHED;
			}
		}
		return threadData != null ? BumpLimitState.NOT_REACHED : BumpLimitState.NEED_COUNT;
	}

	public boolean isDeleted() {
		return post.deleted;
	}

	@NonNull
	public String getSubjectOrComment() {
		String subject = getSubject();
		if (!StringUtils.isEmpty(subject)) {
			return subject;
		}
		return StringUtils.cutIfLongerToLine(HtmlParser.clear(post.comment), 50, true);
	}

	@NonNull
	public String getSubject() {
		if (subject == null) {
			String subject = post.subject;
			if (!StringUtils.isEmpty(subject)) {
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
		return subject;
	}

	private static CharSequence obtainComment(String comment, ChanMarkup markup,
			String threadNumber, PostNumber originalPostNumber, ChanMarkup.MarkupExtra extra) {
		return StringUtils.isEmpty(comment) ? "" : HtmlParser.spanify(comment, markup.getMarkup(),
				threadNumber, originalPostNumber, extra);
	}

	private static CharSequence obtainThreadComment(String comment, ChanMarkup markup, ChanMarkup.MarkupExtra extra) {
		SpannableStringBuilder commentBuilder = new SpannableStringBuilder(obtainComment(comment,
				markup, null, null, extra));
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

	@NonNull
	public CharSequence getComment(Chan chan) {
		if (comment == null) {
			CharSequence comment = obtainComment(post.comment, chan.markup,
					getThreadNumber(), getOriginalPostNumber(), this);
			comment = StringUtils.reduceEmptyLines(comment);
			commentSpans = ColorScheme.getSpans(comment);
			linkSpans = comment instanceof Spanned ? ((Spanned) comment)
					.getSpans(0, comment.length(), LinkSpan.class) : null;
			linkSuffixSpans = comment instanceof Spanned ? ((Spanned) comment)
					.getSpans(0, comment.length(), LinkSuffixSpan.class) : null;
			this.comment = comment;
		}
		return comment;
	}

	@NonNull
	public CharSequence getComment(Chan chan, PostNumber repliesToPost) {
		SpannableString comment = new SpannableString(getComment(chan));
		LinkSpan[] spans = comment.getSpans(0, comment.length(), LinkSpan.class);
		if (spans != null) {
			String commentString = comment.toString();
			String reference = ">>" + repliesToPost;
			for (LinkSpan linkSpan : spans) {
				int start = comment.getSpanStart(linkSpan);
				if (commentString.indexOf(reference, start) == start) {
					int end = comment.getSpanEnd(linkSpan);
					comment.setSpan(new MediumSpan(), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
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

	public String getCommentMarkup(Chan chan) {
		if (!StringUtils.isEmpty(post.commentMarkup)) {
			return post.commentMarkup;
		} else if (!StringUtils.isEmpty(post.comment)) {
			return HtmlParser.unmark(post.comment, chan.markup.getMarkup(), this);
		} else {
			return "";
		}
	}

	// Must be called only after getComment.
	public LinkSuffixSpan[] getLinkSuffixSpansAfterComment() {
		return linkSuffixSpans;
	}

	// Must be called only after getComment.
	public LinkSpan[] getLinkSpansAfterComment() {
		return linkSpans;
	}

	public List<Post.Icon> getIcons() {
		return post.icons;
	}

	public boolean hasAttachments() {
		return attachmentItems != null;
	}

	public List<AttachmentItem> getAttachmentItems() {
		return attachmentItems;
	}

	public String getAttachmentsDescription(Resources resources, AttachmentItem.FormatMode formatMode) {
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
				builder.append(StringUtils.formatFileSize(size, true)).append(' ');
			}
			builder.append(resources.getQuantityString(R.plurals.number_files__format, count, count));
			return builder.toString();
		}
	}

	public void addReferenceFrom(PostNumber postNumber) {
		if (referencesFrom == null) {
			referencesFrom = new TreeSet<>();
		}
		referencesFrom.add(postNumber);
	}

	public void removeReferenceFrom(PostNumber postNumber) {
		if (referencesFrom != null) {
			referencesFrom.remove(postNumber);
		}
	}

	public void clearReferencesFrom() {
		if (referencesFrom != null) {
			referencesFrom.clear();
		}
	}

	public Set<PostNumber> getReferencesTo() {
		return referencesTo;
	}

	public Set<PostNumber> getReferencesFrom() {
		return referencesFrom != null ? referencesFrom : Collections.emptySet();
	}

	public int getPostReplyCount() {
		return referencesFrom != null ? referencesFrom.size() : 0;
	}

	public GalleryItem.Set getThreadGallerySet() {
		return threadData.gallerySet;
	}

	@Override
	public int getThreadPostsCount() {
		return threadData.base.postsCount;
	}

	public List<PostItem> getThreadPosts(Chan chan) {
		int count = threadData.base.posts.size();
		if (count >= 2) {
			int startIndex = threadData.base.postsCount - count + 1;
			ArrayList<PostItem> postItems = new ArrayList<>(count - 1);
			for (Post post : threadData.base.posts.subList(1, count)) {
				PostItem postItem = createPost(post, chan, boardName, threadNumber, originalPostNumber);
				postItem.setOrdinalIndex(startIndex > 0 ? startIndex++ : ORDINAL_INDEX_NONE);
				postItems.add(postItem);
			}
			return postItems;
		}
		return Collections.emptyList();
	}

	public interface DescriptionBuilder {
		void append(String value);
	}

	public void formatThreadCardDescription(Resources resources, boolean repliesOnly, DescriptionBuilder builder) {
		int originalPostFiles = post.attachments.size();
		int replies = threadData.base.postsCount - 1;
		int files = threadData.base.filesCount - originalPostFiles;
		int postsWithFiles = threadData.base.postsWithFilesCount - (originalPostFiles > 0 ? 1 : 0);
		boolean hasInformation = false;
		if (replies >= 0) {
			hasInformation = true;
			builder.append(resources.getQuantityString(R.plurals.number_replies__format, replies, replies));
		}
		if (!repliesOnly) {
			if (postsWithFiles >= 0) {
				hasInformation = true;
				builder.append(resources.getString(R.string.number_with_files__format, postsWithFiles));
			} else if (files >= 0) {
				hasInformation = true;
				builder.append(resources.getQuantityString(R.plurals.number_files__format, files, files));
			}
			if (hasAttachments()) {
				int size = 0;
				for (AttachmentItem attachmentItem : attachmentItems) {
					size += attachmentItem.getSize();
				}
				if (size > 0) {
					hasInformation = true;
					builder.append(StringUtils.formatFileSize(size, true));
				}
			}
		}
		if (!hasInformation) {
			builder.append(resources.getString(R.string.no_information));
		}
	}

	@Override
	public long getTimestamp() {
		return post.timestamp;
	}

	public String getDateTime(PostDateFormatter formatter) {
		long time = getTimestamp();
		if (time > 0L) {
			dateTimeHolder = formatter.formatDateTime(getTimestamp(), dateTimeHolder);
			return dateTimeHolder.text;
		} else {
			return null;
		}
	}

	public boolean isThreadItem() {
		return threadData != null;
	}

	public HideState getHideState() {
		return hideState;
	}

	public String getHideReason() {
		return hideReason;
	}

	public void setHidden(HideState hideState, String hideReason) {
		this.hideState = hideState;
		this.hideReason = hideReason;
	}
}
