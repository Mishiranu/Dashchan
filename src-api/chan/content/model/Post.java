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

package chan.content.model;

import java.io.Serializable;
import java.util.Collection;

import chan.annotation.Public;
import chan.util.CommonUtils;
import chan.util.StringUtils;

import com.mishiranu.dashchan.util.FlagUtils;

@Public
public final class Post implements Serializable, Comparable<Post>
{
	private static final long serialVersionUID = 1L;
	
	private static final int EXTERNAL_FLAGS_MASK = 0x0000ffff;
	
	private static final int FLAG_SAGE = 0x00000001;
	private static final int FLAG_STICKY = 0x00000002;
	private static final int FLAG_CLOSED = 0x00000004;
	private static final int FLAG_ARCHIVED = 0x00000008;
	private static final int FLAG_CYCLICAL = 0x00000010;
	private static final int FLAG_POSTER_WARNED = 0x00000020;
	private static final int FLAG_POSTER_BANNED = 0x00000040;
	private static final int FLAG_ORIGINAL_POSTER = 0x00000080;
	private static final int FLAG_DEFAULT_NAME = 0x00000100;
	
	private static final int FLAG_HIDDEN = 0x00010000;
	private static final int FLAG_SHOWN = 0x00020000;
	private static final int FLAG_DELETED = 0x00040000;
	private static final int FLAG_USER_POST = 0x00080000;
	
	private int mFlags;
	
	private String mThreadNumber;
	private String mParentPostNumber;
	private String mPostNumber;
	
	private long mTimestamp;
	private String mSubject;
	private String mComment;
	private String mEditedComment;
	private String mCommentMarkup;
	
	private String mName;
	private String mIdentifier;
	private String mTripcode;
	private String mCapcode;
	private String mEmail;
	
	private Attachment[] mAttachments;
	private Icon[] mIcons;
	
	@Public
	public Post()
	{
		
	}
	
	@Public
	public String getThreadNumber()
	{
		return mThreadNumber;
	}
	
	@Public
	public Post setThreadNumber(String threadNumber)
	{
		mThreadNumber = threadNumber;
		return this;
	}
	
	public String getParentPostNumberOrNull()
	{
		String parentPostNumber = getParentPostNumber();
		if (parentPostNumber == null || "0".equals(parentPostNumber) || parentPostNumber.equals(getPostNumber()))
		{
			return null;
		}
		return parentPostNumber;
	}
	
	@Public
	public String getParentPostNumber()
	{
		return mParentPostNumber;
	}
	
	@Public
	public Post setParentPostNumber(String parentPostNumber)
	{
		mParentPostNumber = parentPostNumber;
		return this;
	}
	
	@Public
	public String getPostNumber()
	{
		return mPostNumber;
	}
	
	@Public
	public Post setPostNumber(String postNumber)
	{
		mPostNumber = postNumber;
		return this;
	}
	
	public String getOriginalPostNumber()
	{
		String parentPostNumber = getParentPostNumberOrNull();
		return parentPostNumber != null ? parentPostNumber : getPostNumber();
	}
	
	public String getThreadNumberOrOriginalPostNumber()
	{
		String threadNumber = getThreadNumber();
		return threadNumber != null ? threadNumber : getOriginalPostNumber();
	}
	
	@Public
	public long getTimestamp()
	{
		return mTimestamp;
	}
	
	@Public
	public Post setTimestamp(long timestamp)
	{
		mTimestamp = timestamp;
		return this;
	}
	
	@Public
	public String getSubject()
	{
		return mSubject;
	}
	
	@Public
	public Post setSubject(String subject)
	{
		mSubject = subject;
		return this;
	}
	
	@Public
	public String getComment()
	{
		return mComment;
	}
	
	@Public
	public Post setComment(String comment)
	{
		mComment = comment;
		return this;
	}
	
	public String getEditedComment()
	{
		return mEditedComment;
	}
	
	public Post setEditedComment(String editedComment)
	{
		mEditedComment = editedComment;
		return this;
	}
	
	public String getWorkComment()
	{
		String editedComment = getEditedComment();
		return editedComment != null ? editedComment : getComment();
	}
	
	@Public
	public String getCommentMarkup()
	{
		return mCommentMarkup;
	}
	
	@Public
	public Post setCommentMarkup(String commentMarkup)
	{
		mCommentMarkup = commentMarkup;
		return this;
	}
	
	@Public
	public String getName()
	{
		return mName;
	}
	
	@Public
	public Post setName(String name)
	{
		mName = name;
		return this;
	}
	
	@Public
	public String getIdentifier()
	{
		return mIdentifier;
	}
	
	@Public
	public Post setIdentifier(String identifier)
	{
		mIdentifier = identifier;
		return this;
	}
	
	@Public
	public String getTripcode()
	{
		return mTripcode;
	}
	
	@Public
	public Post setTripcode(String tripcode)
	{
		mTripcode = tripcode;
		return this;
	}
	
	@Public
	public String getCapcode()
	{
		return mCapcode;
	}
	
	@Public
	public Post setCapcode(String capcode)
	{
		mCapcode = capcode;
		return this;
	}
	
	@Public
	public String getEmail()
	{
		return mEmail;
	}
	
	@Public
	public Post setEmail(String email)
	{
		mEmail = email;
		return this;
	}
	
	@Public
	public int getAttachmentsCount()
	{
		return mAttachments != null ? mAttachments.length : 0;
	}
	
	@Public
	public Attachment getAttachmentAt(int index)
	{
		return mAttachments[index];
	}
	
	@Public
	public Post setAttachments(Attachment... attachments)
	{
		mAttachments = CommonUtils.removeNullItems(attachments, Attachment.class);
		return this;
	}
	
	@Public
	public Post setAttachments(Collection<? extends Attachment> attachments)
	{
		return setAttachments(CommonUtils.toArray(attachments, Attachment.class));
	}
	
	@Public
	public int getIconsCount()
	{
		return mIcons != null ? mIcons.length : 0;
	}
	
	@Public
	public Icon getIconAt(int index)
	{
		return mIcons[index];
	}
	
	@Public
	public Post setIcons(Icon... icons)
	{
		mIcons = CommonUtils.removeNullItems(icons, Icon.class);
		return this;
	}
	
	@Public
	public Post setIcons(Collection<? extends Icon> icons)
	{
		return setIcons(CommonUtils.toArray(icons, Icon.class));
	}
	
	@Public
	public boolean isSage()
	{
		return FlagUtils.get(mFlags, FLAG_SAGE);
	}
	
	@Public
	public Post setSage(boolean sage)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_SAGE, sage);
		return this;
	}
	
	@Public
	public boolean isSticky()
	{
		return FlagUtils.get(mFlags, FLAG_STICKY);
	}
	
	@Public
	public Post setSticky(boolean sticky)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_STICKY, sticky);
		return this;
	}
	
	@Public
	public boolean isClosed()
	{
		return FlagUtils.get(mFlags, FLAG_CLOSED);
	}
	
	@Public
	public Post setClosed(boolean closed)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_CLOSED, closed);
		return this;
	}
	
	@Public
	public boolean isArchived()
	{
		return FlagUtils.get(mFlags, FLAG_ARCHIVED);
	}
	
	@Public
	public Post setArchived(boolean archived)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_ARCHIVED, archived);
		return this;
	}
	
	@Public
	public boolean isCyclical()
	{
		return FlagUtils.get(mFlags, FLAG_CYCLICAL);
	}
	
	@Public
	public Post setCyclical(boolean cyclical)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_CYCLICAL, cyclical);
		return this;
	}
	
	@Public
	public boolean isPosterWarned()
	{
		return FlagUtils.get(mFlags, FLAG_POSTER_WARNED);
	}
	
	@Public
	public Post setPosterWarned(boolean posterWarned)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_POSTER_WARNED, posterWarned);
		return this;
	}
	
	@Public
	public boolean isPosterBanned()
	{
		return FlagUtils.get(mFlags, FLAG_POSTER_BANNED);
	}
	
	@Public
	public Post setPosterBanned(boolean posterBanned)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_POSTER_BANNED, posterBanned);
		return this;
	}
	
	@Public
	public boolean isOriginalPoster()
	{
		return FlagUtils.get(mFlags, FLAG_ORIGINAL_POSTER);
	}
	
	@Public
	public Post setOriginalPoster(boolean originalPoster)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_ORIGINAL_POSTER, originalPoster);
		return this;
	}
	
	@Public
	public boolean isDefaultName()
	{
		return FlagUtils.get(mFlags, FLAG_DEFAULT_NAME);
	}
	
	@Public
	public Post setDefaultName(boolean defaultName)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_DEFAULT_NAME, defaultName);
		return this;
	}
	
	public boolean isShown()
	{
		return FlagUtils.get(mFlags, FLAG_SHOWN);
	}
	
	public boolean isHidden()
	{
		return FlagUtils.get(mFlags, FLAG_HIDDEN);
	}
	
	public Post setHidden(boolean hidden)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_HIDDEN, hidden);
		mFlags = FlagUtils.set(mFlags, FLAG_SHOWN, !hidden);
		return this;
	}
	
	public Post resetHidden()
	{
		mFlags = FlagUtils.set(mFlags, FLAG_HIDDEN & FLAG_SHOWN, false);
		return this;
	}
	
	public boolean isDeleted()
	{
		return FlagUtils.get(mFlags, FLAG_DELETED);
	}
	
	public Post setDeleted(boolean deleted)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_DELETED, deleted);
		return this;
	}
	
	public boolean isUserPost()
	{
		return FlagUtils.get(mFlags, FLAG_USER_POST);
	}
	
	public Post setUserPost(boolean userPost)
	{
		mFlags = FlagUtils.set(mFlags, FLAG_USER_POST, userPost);
		return this;
	}
	
	private static class NumbersPair
	{
		public final int postNumber;
		public final int variation;
		
		public NumbersPair(String postNumber, String variation)
		{
			this.postNumber = Integer.parseInt(postNumber);
			this.variation = variation != null ? Integer.parseInt(variation) : 0;
		}
	}
	
	private transient volatile NumbersPair mNumbersPair;
	
	private NumbersPair getNumbersPair()
	{
		if (mNumbersPair == null)
		{
			String postNumber = getPostNumber();
			String variation = null;
			int index1 = -1;
			int index2 = -1;
			for (int i = 0; i < postNumber.length(); i++)
			{
				char c = postNumber.charAt(i);
				if (c < '0' || c > '9')
				{
					if (index1 == -1) index1 = i; else if (index2 == -1)
					{
						index2 = i;
						break;
					}
				}
			}
			if (index1 >= 0)
			{
				variation = postNumber.substring(index1 + 1, index2 >= 0 ? index2 : postNumber.length());
				postNumber = postNumber.substring(0, index1);
			}
			mNumbersPair = new NumbersPair(postNumber, variation);
		}
		return mNumbersPair;
	}
	
	@Public
	@Override
	public int compareTo(Post another)
	{
		NumbersPair thisPair = getNumbersPair();
		NumbersPair anotherPair = another.getNumbersPair();
		int difference = thisPair.postNumber - anotherPair.postNumber;
		if (difference != 0) return difference;
		return thisPair.variation - anotherPair.variation;
	}
	
	public boolean contentEquals(Post o)
	{
		if (mAttachments != null && o.mAttachments != null && mAttachments.length == o.mAttachments.length)
		{
			for (int i = 0, count = mAttachments.length; i < count; i++)
			{
				Attachment a1 = mAttachments[i];
				Attachment a2 = o.mAttachments[i];
				if (a1 instanceof FileAttachment && a2 instanceof FileAttachment)
				{
					if (!((FileAttachment) a1).contentEquals((FileAttachment) a2)) return false;
				}
				else if (a1 instanceof EmbeddedAttachment && a2 instanceof EmbeddedAttachment)
				{
					if (!((EmbeddedAttachment) a1).contentEquals((EmbeddedAttachment) a2)) return false;
				}
				else return false;
			}
		}
		else if (mAttachments != null || o.mAttachments != null) return false;
		if (mIcons == o.mIcons || mIcons != null && o.mIcons != null && mIcons.length == o.mIcons.length)
		{
			for (int i = 0, count = mIcons != null ? mIcons.length : 0; i < count; i++)
			{
				if (!mIcons[i].contentEquals(o.mIcons[i])) return false;
			}
		}
		else return false;
		return (EXTERNAL_FLAGS_MASK & mFlags) == (EXTERNAL_FLAGS_MASK & o.mFlags) && StringUtils.equals(mThreadNumber,
				o.mThreadNumber) && StringUtils.equals(mParentPostNumber, o.mParentPostNumber) &&
				StringUtils.equals(mPostNumber, o.mPostNumber) && mTimestamp == o.mTimestamp &&
				StringUtils.equals(mSubject, o.mSubject) && StringUtils.equals(mComment, o.mComment) &&
				StringUtils.equals(mCommentMarkup, o.mCommentMarkup) && StringUtils.equals(mName, o.mName) &&
				StringUtils.equals(mIdentifier, o.mIdentifier) && StringUtils.equals(mTripcode, o.mTripcode) &&
				StringUtils.equals(mCapcode, o.mCapcode) && StringUtils.equals(mEmail, o.mEmail);
	}
}