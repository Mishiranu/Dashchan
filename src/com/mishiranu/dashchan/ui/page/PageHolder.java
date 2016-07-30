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

package com.mishiranu.dashchan.ui.page;

import android.os.Parcel;
import android.os.Parcelable;

import chan.util.StringUtils;

import com.mishiranu.dashchan.preference.Preferences;
import com.mishiranu.dashchan.widget.ListPosition;

public class PageHolder implements Parcelable
{
	public enum Content {THREADS, POSTS, SEARCH, ARCHIVE, ALL_BOARDS, USER_BOARDS, HISTORY}
	
	boolean inStack = true;
	public final Content content;
	public final String chanName, boardName, threadNumber, searchQuery;
	public String threadTitle;
	public final long creationTime;
	public ListPosition position;
	public Extra extra;
	
	public boolean initialFromCache;
	public String initialPostNumber;
	
	public PageHolder(Content content, String chanName, String boardName, String threadNumber, String threadTitle,
			String searchQuery)
	{
		this(content, chanName, boardName, threadNumber, threadTitle, searchQuery, System.currentTimeMillis());
	}
	
	public PageHolder(Content content, String chanName, String boardName, String threadNumber, String threadTitle,
			String searchQuery, long creationTime)
	{
		this.content = content;
		this.chanName = chanName;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.threadTitle = threadTitle;
		this.searchQuery = searchQuery;
		this.creationTime = creationTime;
	}
	
	public PageHolder setInitialThreadsData(boolean fromCache)
	{
		initialFromCache = fromCache;
		return this;
	}
	
	public PageHolder setInitialPostsData(boolean fromCache, String postNumber)
	{
		initialFromCache = fromCache;
		initialPostNumber = postNumber;
		return this;
	}
	
	public PageHolder setInitialSearchData(boolean fromCache)
	{
		initialFromCache = fromCache;
		return this;
	}
	
	public boolean isThreadsOrPosts()
	{
		return content == Content.THREADS || content == Content.POSTS;
	}
	
	public boolean canDestroyIfNotInStack()
	{
		return content == Content.SEARCH || content == Content.ARCHIVE || content == Content.ALL_BOARDS
				|| content == Content.HISTORY;
	}
	
	public boolean canRemoveFromStackIfDeep()
	{
		if (content == Content.ALL_BOARDS)
		{
			String boardName = Preferences.getDefaultBoardName(chanName);
			return boardName != null;
		}
		return content == Content.SEARCH || content == Content.ARCHIVE || content == Content.USER_BOARDS
				|| content == Content.HISTORY;
	}
	
	public boolean is(String chanName, String boardName, String threadNumber, Content content)
	{
		if (this.content != content) return false;
		if (!StringUtils.equals(this.chanName, chanName)) return false;
		boolean compareContentTypeOnly1 = false;
		boolean compareContentTypeOnly2 = false;
		switch (this.content)
		{
			case SEARCH:
			case ALL_BOARDS:
			case USER_BOARDS:
			case HISTORY:
			{
				compareContentTypeOnly1 = true;
				break;
			}
			default: break;
		}
		switch (content)
		{
			case SEARCH:
			case ALL_BOARDS:
			case USER_BOARDS:
			case HISTORY:
			{
				compareContentTypeOnly2 = true;
				break;
			}
			default: break;
		}
		if (compareContentTypeOnly1 && compareContentTypeOnly2) return this.content == content;
		if (compareContentTypeOnly1 || compareContentTypeOnly2) return false;
		return StringUtils.equals(this.boardName, boardName) && StringUtils.equals(this.threadNumber, threadNumber);
	}
	
	@Override
	public int describeContents()
	{
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeInt(inStack ? 1 : 0);
		dest.writeString(content.name());
		dest.writeString(chanName);
		dest.writeString(boardName);
		dest.writeString(threadNumber);
		dest.writeString(threadTitle);
		dest.writeString(searchQuery);
		dest.writeLong(creationTime);
		ListPosition.writeToParcel(dest, position);
		if (extra instanceof ParcelableExtra)
		{
			dest.writeString(extra.getClass().getName());
			((ParcelableExtra) extra).writeToParcel(dest);
		}
		else dest.writeString(null);
	}
	
	public static final Creator<PageHolder> CREATOR = new Creator<PageHolder>()
	{
		@Override
		public PageHolder createFromParcel(Parcel source)
		{
			boolean inStack = source.readInt() != 0;
			Content content = Content.valueOf(source.readString());
			String chanName = source.readString();
			String boardName = source.readString();
			String threadNumber = source.readString();
			String threadTitle = source.readString();
			String searchQuery = source.readString();
			long creationTime = source.readLong();
			PageHolder pageHolder = new PageHolder(content, chanName, boardName, threadNumber, threadTitle,
					searchQuery, creationTime);
			pageHolder.inStack = inStack;
			pageHolder.position = ListPosition.readFromParcel(source);
			String extraClassName = source.readString();
			if (extraClassName != null)
			{
				try
				{
					@SuppressWarnings("unchecked")
					Class<ParcelableExtra> extraClass = (Class<ParcelableExtra>) Class.forName(extraClassName);
					pageHolder.extra = extraClass.newInstance();
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
				((ParcelableExtra) pageHolder.extra).readFromParcel(source);
			}
			return pageHolder;
		}
		
		@Override
		public PageHolder[] newArray(int size)
		{
			return new PageHolder[size];
		}
	};
	
	public interface Extra
	{
		
	}
	
	public interface ParcelableExtra extends Extra
	{
		public void writeToParcel(Parcel dest);
		public void readFromParcel(Parcel source);
	}
}