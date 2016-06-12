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

import java.util.List;

import android.content.Context;
import android.net.Uri;

import chan.content.model.ThreadSummary;

import com.mishiranu.dashchan.R;

public class ThreadSummaryItem implements AttachmentItem.Binder
{
	private final ThreadSummary mThreadSummary;
	private final String mChanName;
	private final List<AttachmentItem> mAttachmentItems;
	
	public ThreadSummaryItem(ThreadSummary threadSummary, String chanName)
	{
		mThreadSummary = threadSummary;
		mChanName = chanName;
		Uri thumbnailUri = threadSummary.getRelativeThumbnailUri();
		mAttachmentItems = AttachmentItem.obtain(this, thumbnailUri);
	}
	
	@Override
	public String getChanName()
	{
		return mChanName;
	}
	
	@Override
	public String getBoardName()
	{
		return mThreadSummary.getBoardName();
	}
	
	@Override
	public String getThreadNumber()
	{
		return mThreadSummary.getThreadNumber();
	}
	
	@Override
	public String getPostNumber()
	{
		return null;
	}
	
	public String getDescription()
	{
		return mThreadSummary.getDescription();
	}
	
	public int getPostsCount()
	{
		return mThreadSummary.getPostsCount();
	}
	
	public int getViewsCount()
	{
		return mThreadSummary.getViewsCount();
	}
	
	public List<AttachmentItem> getAttachmentItems()
	{
		return mAttachmentItems;
	}
	
	public String formatThreadCardDescription(Context context, boolean repliesOnly)
	{
		StringBuilder builder = new StringBuilder();
		int replies = getPostsCount() - 1;
		int views = getViewsCount();
		boolean hasInformation = false;
		if (replies >= 0)
		{
			hasInformation = true;
			builder.append(PostItem.makeNbsp(context.getResources().getQuantityString
					(R.plurals.text_replies_count_format, replies, replies)));
		}
		if (!repliesOnly)
		{
			if (views >= 0)
			{
				if (hasInformation) builder.append(PostItem.CARD_DESCRIPTION_DIVIDER); else hasInformation = true;
				builder.append(PostItem.makeNbsp(context.getResources().getQuantityString
						(R.plurals.text_views_count_format, views, views)));
			}
		}
		if (!hasInformation) builder.append(PostItem.makeNbsp(context.getString(R.string.text_no_thread_information)));
		return builder.toString();
	}
}