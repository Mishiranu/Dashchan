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

import android.net.Uri;

import chan.annotation.Public;
import chan.content.ChanLocator;

@Public
public final class ThreadSummary
{
	private final String mBoardName;
	private final String mThreadNumber;
	private final String mDescription;
	
	private String mThumbnailUriString;
	private int mPostsCount = -1;
	private int mViewsCount = -1;
	
	@Public
	public ThreadSummary(String boardName, String threadNumber, String description)
	{
		mBoardName = boardName;
		mThreadNumber = threadNumber;
		mDescription = description;
	}
	
	@Public
	public String getBoardName()
	{
		return mBoardName;
	}
	
	@Public
	public String getThreadNumber()
	{
		return mThreadNumber;
	}
	
	@Public
	public String getDescription()
	{
		return mDescription;
	}
	
	public Uri getRelativeThumbnailUri()
	{
		return mThumbnailUriString != null ? Uri.parse(mThumbnailUriString) : null;
	}
	
	@Public
	public Uri getThumbnailUri(ChanLocator locator)
	{
		return mThumbnailUriString != null ? locator.convert(Uri.parse(mThumbnailUriString)) : null;
	}
	
	@Public
	public ThreadSummary setThumbnailUri(ChanLocator locator, Uri thumbnailUri)
	{
		mThumbnailUriString = thumbnailUri != null ? locator.makeRelative(thumbnailUri).toString() : null;
		return this;
	}
	
	@Public
	public int getPostsCount()
	{
		return mPostsCount;
	}
	
	@Public
	public ThreadSummary setPostsCount(int postsCount)
	{
		mPostsCount = postsCount;
		return this;
	}
	
	@Public
	public int getViewsCount()
	{
		return mViewsCount;
	}
	
	@Public
	public ThreadSummary setViewsCount(int viewsCount)
	{
		mViewsCount = viewsCount;
		return this;
	}
}