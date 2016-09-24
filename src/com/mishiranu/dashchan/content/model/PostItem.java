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

package com.mishiranu.dashchan.content.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;

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

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.storage.HiddenThreadsDatabase;
import com.mishiranu.dashchan.graphics.ColorScheme;
import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.text.HtmlParser;
import com.mishiranu.dashchan.text.style.LinkSpan;
import com.mishiranu.dashchan.text.style.LinkSuffixSpan;
import com.mishiranu.dashchan.text.style.NameColorSpan;
import com.mishiranu.dashchan.text.style.SpoilerSpan;
import com.mishiranu.dashchan.util.PostDateFormatter;

public class PostItem implements AttachmentItem.Binder, ChanMarkup.MarkupExtra, Comparable<PostItem>
{
	private final String mChanName;
	private final String mBoardName;
	private ArrayList<AttachmentItem> mAttachmentItems;
	private ArrayList<Pair<Uri, String>> mIcons;

	public static final int ORDINAL_INDEX_NONE = -1;
	public static final int ORDINAL_INDEX_DELETED = -2;

	private int mOrdinalIndex = ORDINAL_INDEX_NONE;

	private String mSubject;
	private CharSequence mComment;
	private CharSequence mFullName;
	private ColorScheme.Span[] mCommentSpans;
	private ColorScheme.Span[] mFullNameSpans;
	private LinkSpan[] mLinkSpans;
	private LinkSuffixSpan[] mLinkSuffixSpans;
	private PostDateFormatter.Holder mDateTimeHolder;
	private boolean mUseDefaultName;

	private HashSet<String> mReferencesTo;
	private LinkedHashSet<String> mReferencesFrom;

	private boolean mExpanded = false;

	private final Post mPost;
	private final ThreadData mThreadData;

	private static class ThreadData
	{
		public int postsCount;
		public int filesCount;
		public int postsWithFilesCount;
		public Post[] firstAndLastPosts;
		public CharSequence commentShort;
		public ColorScheme.Span[] commentShortSpans;
		public GalleryItem.GallerySet gallerySet;
	}

	private int mHidden = C.HIDDEN_UNKNOWN;
	private String mHideReason;
	private boolean mUnread = false;

	public PostItem(Post post, String chanName, String boardName)
	{
		mPost = post;
		mThreadData = null;
		mChanName = chanName;
		mBoardName = boardName;
		init();
	}

	public PostItem(Posts thread, String chanName, String boardName)
	{
		Post[] posts = thread.getPosts();
		mPost = posts[0];
		mThreadData = new ThreadData();
		mThreadData.postsCount = thread.getPostsCount();
		mThreadData.filesCount = thread.getFilesCount();
		mThreadData.postsWithFilesCount = thread.getPostsWithFilesCount();
		mThreadData.firstAndLastPosts = posts;
		mChanName = chanName;
		mBoardName = boardName;
		init();
	}

	private void init()
	{
		mAttachmentItems = AttachmentItem.obtain(this);
		if (isThreadItem())
		{
			ThreadData threadData = mThreadData;
			if (mAttachmentItems != null)
			{
				threadData.gallerySet = new GalleryItem.GallerySet(false);
				threadData.gallerySet.setThreadTitle(getSubjectOrComment());
				threadData.gallerySet.add(mAttachmentItems);
			}
			threadData.commentShort = obtainThreadComment(mPost.getWorkComment(), mChanName, this);
			threadData.commentShortSpans = ColorScheme.getSpans(threadData.commentShort);
		}
		else
		{
			String comment = mPost.getWorkComment();
			mReferencesTo = parseReferencesTo(mReferencesTo, comment);
		}
		ArrayList<Pair<Uri, String>> icons = null;
		for (int i = 0, count = mPost.getIconsCount(); i < count; i++)
		{
			Icon icon = mPost.getIconAt(i);
			if (icon != null)
			{
				if (icons == null) icons = new ArrayList<>();
				icons.add(new Pair<>(icon.getRelativeUri(), icon.getTitle()));
			}
		}
		mIcons = icons;
	}

	public Post getPost()
	{
		return mPost;
	}

	public static HashSet<String> parseReferencesTo(HashSet<String> referencesTo, String comment)
	{
		if (referencesTo != null) referencesTo.clear();
		if (comment != null)
		{
			// Fast find <a.+?>(?:>>|&gt;&gt;)(\d+)</a>
			int index1 = -1;
			while (true)
			{
				index1 = StringUtils.nearestIndexOf(comment, index1, "<a ", "<a\n", "<a\r");
				if (index1 == -1) break;
				index1 = comment.indexOf(">", index1);
				if (index1 == -1) break;
				int index2 = comment.indexOf("</a>", index1);
				if (index2 > index1++)
				{
					int start = -1;
					String text = comment.substring(index1, index2);
					int length = index2 - index1;
					if (text.startsWith(">>")) start = 2;
					else if (text.startsWith("&gt;&gt;")) start = 8;
					if (start >= 0 && start < length)
					{
						boolean number = true;
						for (int i = start; i < length; i++)
						{
							char c = text.charAt(i);
							if (c < '0' || c > '9')
							{
								number = false;
								break;
							}
						}
						if (!number) continue;
						if (referencesTo == null) referencesTo = new HashSet<>();
						referencesTo.add(text.substring(start));
					}
				}
				else break;
			}
		}
		return referencesTo;
	}

	public void addReferenceFrom(String postNumber)
	{
		if (mReferencesFrom == null) mReferencesFrom = new LinkedHashSet<>();
		mReferencesFrom.add(postNumber);
	}

	public void removeReferenceFrom(String postNumber)
	{
		if (mReferencesFrom != null)
		{
			mReferencesFrom.remove(postNumber);
		}
	}

	public void clearReferencesFrom()
	{
		if (mReferencesFrom != null)
		{
			mReferencesFrom.clear();
		}
	}

	public void setOrdinalIndex(int ordinalIndex)
	{
		mOrdinalIndex = ordinalIndex;
	}

	public void preload()
	{
		getComment();
	}

	public int getOrdinalIndex()
	{
		return mOrdinalIndex;
	}

	public String getOrdinalIndexString()
	{
		if (mOrdinalIndex >= 0) return Integer.toString(mOrdinalIndex + 1);
		if (mOrdinalIndex == ORDINAL_INDEX_DELETED) return "X";
		return null;
	}

	@Override
	public String getChanName()
	{
		return mChanName;
	}

	@Override
	public String getBoardName()
	{
		return mBoardName;
	}

	@Override
	public String getThreadNumber()
	{
		return mPost.getThreadNumberOrOriginalPostNumber();
	}

	@Override
	public String getPostNumber()
	{
		return mPost.getPostNumber();
	}

	public String getOriginalPostNumber()
	{
		return mPost.getOriginalPostNumber();
	}

	public String getParentPostNumber()
	{
		return mPost.getParentPostNumberOrNull();
	}

	@Override
	public int compareTo(PostItem another)
	{
		return mPost.compareTo(another.getPost());
	}

	/*
	 * Returns whether name is default. Call this method only after getFullName.
	 */
	public boolean isUseDefaultName()
	{
		return mUseDefaultName;
	}

	private CharSequence makeFullName()
	{
		String name = mPost.getName();
		String identifier = mPost.getIdentifier();
		String tripcode = mPost.getTripcode();
		String capcode = mPost.getCapcode();
		String defaultName = ChanConfiguration.get(getChanName()).getDefaultName(mBoardName);
		if (StringUtils.isEmptyOrWhitespace(defaultName)) defaultName = "Anonymous";
		if (StringUtils.isEmptyOrWhitespace(name)) name = defaultName; else name = name.trim();
		boolean useDefaultName = mPost.isDefaultName() || name.equals(defaultName);
		boolean hasIdentifier = !StringUtils.isEmptyOrWhitespace(identifier);
		boolean hasTripcode = !StringUtils.isEmptyOrWhitespace(tripcode);
		boolean hasCapcode = !StringUtils.isEmptyOrWhitespace(capcode);
		CharSequence fullName;
		if (hasIdentifier || hasTripcode || hasCapcode)
		{
			SpannableStringBuilder spannable = new SpannableStringBuilder();
			if (!useDefaultName) spannable.append(name);
			if (hasIdentifier)
			{
				if (spannable.length() > 0) spannable.append(' ');
				StringUtils.appendSpan(spannable, identifier, new NameColorSpan(NameColorSpan.TYPE_TRIPCODE));
			}
			if (hasTripcode)
			{
				if (spannable.length() > 0) spannable.append(' ');
				StringUtils.appendSpan(spannable, tripcode, new NameColorSpan(NameColorSpan.TYPE_TRIPCODE));
			}
			if (hasCapcode)
			{
				if (spannable.length() > 0) spannable.append(' ');
				StringUtils.appendSpan(spannable, "## " + capcode, new NameColorSpan(NameColorSpan.TYPE_CAPCODE));
			}
			fullName = spannable;
			useDefaultName = false;
		}
		else fullName = name;
		mUseDefaultName = useDefaultName;
		return fullName;
	}

	/*
	 * Returns spanned name and tripcode, guaranteed not null.
	 */
	public CharSequence getFullName()
	{
		if (mFullName == null)
		{
			synchronized (this)
			{
				if (mFullName == null)
				{
					CharSequence fullName = makeFullName();
					if (StringUtils.isEmpty(fullName)) fullName = "";
					mFullNameSpans = ColorScheme.getSpans(fullName);
					mFullName = fullName;
				}
			}
		}
		return mFullName;
	}

	public ColorScheme.Span[] getFullNameSpans()
	{
		return mFullNameSpans;
	}

	public String getEmail()
	{
		return mPost.getEmail();
	}

	public boolean isSage()
	{
		return mPost.isSage();
	}

	public boolean isSticky()
	{
		return mPost.isSticky() && getParentPostNumber() == null;
	}

	public boolean isClosed()
	{
		return (mPost.isClosed() || mPost.isArchived()) && getParentPostNumber() == null;
	}

	public boolean isCyclical()
	{
		return mPost.isCyclical() && getParentPostNumber() == null;
	}

	public boolean isOriginalPoster()
	{
		return mPost.isOriginalPoster() || getParentPostNumber() == null;
	}

	public boolean isPosterWarned()
	{
		return mPost.isPosterWarned();
	}

	public boolean isPosterBanned()
	{
		return mPost.isPosterBanned();
	}

	public boolean isDeleted()
	{
		return mPost.isDeleted();
	}

	public boolean isUserPost()
	{
		return mPost.isUserPost();
	}

	public void setUserPost(boolean userPost)
	{
		mPost.setUserPost(userPost);
	}

	public String getSubjectOrComment()
	{
		String subject = getSubject();
		if (!StringUtils.isEmpty(subject)) return subject;
		return StringUtils.cutIfLongerToLine(HtmlParser.clear(mPost.getWorkComment()), 50, true);
	}

	/*
	 * Returns thread subject, guaranteed not null.
	 */
	public String getSubject()
	{
		if (mSubject == null)
		{
			synchronized (this)
			{
				if (mSubject == null)
				{
					String subject = mPost.getSubject();
					if (subject != null)
					{
						subject = subject.replace("\r", "").replace("\n", " ").trim();
						if (subject.length() == 1 && (subject.charAt(0) == '\u202d'
								|| subject.charAt(0) == '\u202e'))
						{
							subject = "";
						}
					}
					else subject = "";
					mSubject = subject;
				}
			}
		}
		return mSubject;
	}

	private static CharSequence obtainComment(String comment, String chanName, String parentPostNumber,
											  ChanMarkup.MarkupExtra extra)
	{
		return StringUtils.isEmpty(comment) ? "" : HtmlParser.spanify(comment, ChanMarkup.get(chanName),
				StringUtils.emptyIfNull(parentPostNumber), extra);
	}

	private static CharSequence obtainThreadComment(String comment, String chanName, ChanMarkup.MarkupExtra extra)
	{
		SpannableStringBuilder commentBuilder = new SpannableStringBuilder(obtainComment(comment,
				chanName, null, extra));
		int linebreaks = 0;
		// Remove more than one linebreaks in sequence
		for (int i = commentBuilder.length() - 1; i >= 0; i--)
		{
			char c = commentBuilder.charAt(i);
			if (c == '\n') linebreaks++; else
			{
				// Remove linebreaks - 1 characters, keeping one line break
				if (linebreaks > 1) commentBuilder.delete(i + 1, i + linebreaks);
				linebreaks = 0;
			}
		}
		return commentBuilder;
	}

	public CharSequence getComment()
	{
		if (mComment == null)
		{
			synchronized (this)
			{
				if (mComment == null)
				{
					CharSequence comment = obtainComment(mPost.getWorkComment(), getChanName(),
							getParentPostNumber(), this);
					// Make empty lines take less space
					SpannableStringBuilder builder = null;
					int linebreaks = 0;
					for (int i = 0; i < comment.length(); i++)
					{
						char c = comment.charAt(i);
						if (c == '\n') linebreaks++; else
						{
							if (linebreaks > 1)
							{
								if (builder == null)
								{
									builder = new SpannableStringBuilder(comment);
									comment = builder;
								}
								builder.setSpan(new RelativeSizeSpan(0.75f), i - linebreaks, i,
										SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
							}
							linebreaks = 0;
						}
					}
					mCommentSpans = ColorScheme.getSpans(comment);
					mLinkSpans = comment instanceof Spanned ? ((Spanned) comment)
							.getSpans(0, comment.length(), LinkSpan.class) : null;
					mLinkSuffixSpans = comment instanceof Spanned ? ((Spanned) comment)
							.getSpans(0, comment.length(), LinkSuffixSpan.class) : null;
					mComment = comment;
				}
			}
		}
		return mComment;
	}

	/*
	 * Returns spanned comment (post), guaranteed not null.
	 */
	public CharSequence getComment(String repliesToPost)
	{
		SpannableString comment = new SpannableString(getComment());
		LinkSpan[] spans = comment.getSpans(0, comment.length(), LinkSpan.class);
		if (spans != null)
		{
			String commentString = comment.toString();
			repliesToPost = ">>" + repliesToPost;
			for (LinkSpan linkSpan : spans)
			{
				int start = comment.getSpanStart(linkSpan);
				if (commentString.indexOf(repliesToPost, start) == start)
				{
					int end = comment.getSpanEnd(linkSpan);
					comment.setSpan(new StyleSpan(Typeface.BOLD), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
		}
		return comment;
	}

	public ColorScheme.Span[] getCommentSpans()
	{
		return mCommentSpans;
	}

	public CharSequence getThreadCommentShort(int maxWidth, float textSize, int maxLines)
	{
		float factor = maxWidth * maxLines / textSize;
		int count = (int) (factor * 3f);
		CharSequence comment = mThreadData.commentShort;
		if (comment instanceof Spanned)
		{
			SpoilerSpan[] spoilerSpans = ((Spanned) comment).getSpans(0, comment.length(), SpoilerSpan.class);
			if (spoilerSpans != null)
			{
				boolean enabled = !Preferences.isShowSpoilers();
				for (SpoilerSpan spoilerSpan : spoilerSpans) spoilerSpan.setEnabled(enabled);
			}
		}
		return count > 0 && comment.length() > count ? comment.subSequence(0, count) : comment;
	}

	public ColorScheme.Span[] getThreadCommentShortSpans()
	{
		return mThreadData.commentShortSpans;
	}

	public String getRawComment()
	{
		return mPost.getWorkComment();
	}

	public String getCommentMarkup()
	{
		String commentMarkup = mPost.getCommentMarkup();
		String comment = mPost.getWorkComment();
		if (StringUtils.isEmpty(commentMarkup))
		{
			if (!StringUtils.isEmpty(comment))
			{
				commentMarkup = HtmlParser.unmark(comment, ChanMarkup.get(mChanName), this);
			}
			else commentMarkup = "";
		}
		return commentMarkup;
	}

	/*
	 * Must be called only after getComment.
	 */
	public LinkSuffixSpan[] getLinkSuffixSpansAfterComment()
	{
		return mLinkSuffixSpans;
	}

	/*
	 * Must be called only after getComment.
	 */
	public LinkSpan[] getLinkSpansAfterComment()
	{
		return mLinkSpans;
	}

	public ArrayList<Pair<Uri, String>> getIcons()
	{
		return mIcons;
	}

	public boolean hasAttachments()
	{
		return mAttachmentItems != null;
	}

	public ArrayList<AttachmentItem> getAttachmentItems()
	{
		return mAttachmentItems;
	}

	public String getAttachmentsDescription(Context context, AttachmentItem.FormatMode formatMode)
	{
		int count = mAttachmentItems.size();
		if (count == 1)
		{
			AttachmentItem attachmentItem = mAttachmentItems.get(0);
			return attachmentItem.getDescription(formatMode);
		}
		else
		{
			int size = 0;
			for (int i = 0; i < count; i++) size += mAttachmentItems.get(i).getSize();
			StringBuilder builder = new StringBuilder();
			if (size > 0) builder.append(AttachmentItem.formatSize(size)).append(' ');
			builder.append(context.getResources().getQuantityString(R.plurals
					.text_several_files_count_format, count, count));
			return builder.toString();
		}
	}

	public int getPostReplyCount()
	{
		return mReferencesFrom != null ? mReferencesFrom.size() : 0;
	}

	/*
	 * May return null set.
	 */
	public HashSet<String> getReferencesTo()
	{
		return mReferencesTo;
	}

	/*
	 * May return null array.
	 */
	public LinkedHashSet<String> getReferencesFrom()
	{
		return mReferencesFrom;
	}

	public GalleryItem.GallerySet getThreadGallerySet()
	{
		return mThreadData.gallerySet;
	}

	public int getThreadPostsCount()
	{
		return mThreadData.postsCount;
	}

	public PostItem[] getThreadLastPosts()
	{
		Post[] posts = mThreadData.firstAndLastPosts;
		if (posts != null && posts.length > 1)
		{
			PostItem[] postItems = new PostItem[posts.length - 1];
			int startIndex = mThreadData.postsCount - posts.length + 1;
			for (int i = 0; i < postItems.length; i++)
			{
				PostItem postItem = new PostItem(posts[i + 1], mChanName, mBoardName);
				postItem.setOrdinalIndex(startIndex > 0 ? startIndex + i : ORDINAL_INDEX_NONE);
				postItems[i] = postItem;
			}
			return postItems;
		}
		return null;
	}

	static final String CARD_DESCRIPTION_DIVIDER = "   ";

	static String makeNbsp(String s)
	{
		if (s != null) s = s.replace(' ', '\u00a0');
		return s;
	}

	public String formatThreadCardDescription(Context context, boolean repliesOnly)
	{
		StringBuilder builder = new StringBuilder();
		int originalPostFiles = mPost.getAttachmentsCount();
		int replies = mThreadData.postsCount - 1;
		int files = mThreadData.filesCount - originalPostFiles;
		int postsWithFiles = mThreadData.postsWithFilesCount - (originalPostFiles > 0 ? 1 : 0);
		boolean hasInformation = false;
		Resources resources = context.getResources();
		if (replies >= 0)
		{
			hasInformation = true;
			builder.append(makeNbsp(resources.getQuantityString(R.plurals.text_replies_count_format,
					replies, replies)));
		}
		if (!repliesOnly)
		{
			if (postsWithFiles >= 0)
			{
				if (hasInformation) builder.append(CARD_DESCRIPTION_DIVIDER); else hasInformation = true;
				builder.append(makeNbsp(resources.getString(R.string.text_thread_files_format, postsWithFiles)));
			}
			else if (files >= 0)
			{
				if (hasInformation) builder.append(CARD_DESCRIPTION_DIVIDER); else hasInformation = true;
				builder.append(makeNbsp(resources.getQuantityString(R.plurals.text_several_files_count_format,
						files, files)));
			}
			if (hasAttachments())
			{
				int size = 0;
				for (AttachmentItem attachmentItem : mAttachmentItems) size += attachmentItem.getSize();
				if (size > 0)
				{
					if (hasInformation) builder.append(CARD_DESCRIPTION_DIVIDER); else hasInformation = true;
					builder.append(AttachmentItem.formatSize(size).replace(' ', '\u00a0'));
				}
			}
		}
		if (!hasInformation) builder.append(makeNbsp(resources.getString(R.string.text_no_thread_information)));
		return builder.toString();
	}

	public long getTimestamp()
	{
		return mPost.getTimestamp();
	}

	public String getDateTime(PostDateFormatter formatter)
	{
		mDateTimeHolder = formatter.format(getTimestamp(), mDateTimeHolder);
		return mDateTimeHolder.text;
	}

	public boolean isExpanded()
	{
		return mExpanded;
	}

	public void setExpanded(boolean expanded)
	{
		mExpanded = expanded;
	}

	public boolean isThreadItem()
	{
		return mThreadData != null;
	}

	private int getHiddenStateFromModel()
	{
		if (mPost.isHidden()) return C.HIDDEN_TRUE;
		if (mPost.isShown()) return C.HIDDEN_FALSE;
		return C.HIDDEN_UNKNOWN;
	}

	public boolean isHidden(HidePerformer hidePerformer)
	{
		if (mHidden == C.HIDDEN_UNKNOWN)
		{
			synchronized (this)
			{
				int hidden = mHidden;
				if (hidden == C.HIDDEN_UNKNOWN)
				{
					String hideReason = null;
					if (mThreadData == null) hidden = getHiddenStateFromModel(); else
					{
						hidden = HiddenThreadsDatabase.getInstance().check(getChanName(), mBoardName, getPostNumber());
					}
					if (hidden == C.HIDDEN_UNKNOWN)
					{
						hideReason = hidePerformer.checkHidden(this);
						if (hideReason != null) hidden = C.HIDDEN_TRUE;
						else hidden = C.HIDDEN_FALSE;
					}
					mHideReason = hideReason;
					mHidden = hidden;
				}
			}
		}
		return mHidden == C.HIDDEN_TRUE;
	}

	/*
	 * Must be called only after isHidden.
	 */
	public boolean isHiddenUnchecked()
	{
		return mHidden == C.HIDDEN_TRUE;
	}

	public String getHideReason()
	{
		return mHideReason;
	}

	public void setHidden(boolean hidden)
	{
		mHidden = hidden ? C.HIDDEN_TRUE : C.HIDDEN_FALSE;
		mHideReason = null;
		if (!isThreadItem()) mPost.setHidden(hidden);
	}

	public void invalidateHidden()
	{
		mHidden = C.HIDDEN_UNKNOWN;
		mHideReason = null;
	}

	public void resetHidden()
	{
		invalidateHidden();
		mPost.resetHidden();
	}

	public void setUnread(boolean unread)
	{
		mUnread = unread;
	}

	public boolean isUnread()
	{
		return mUnread;
	}
}