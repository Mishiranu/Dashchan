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

package com.mishiranu.dashchan.async;

import android.os.AsyncTask;

import chan.content.model.Posts;

import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.PostItem;

public class DeserializePostsTask extends AsyncTask<Void, Void, Boolean>
{
	private final Callback mCallback;
	private final String mChanName;
	private final String mBoardName;
	private final String mThreadNumber;
	private final boolean mFromCache;
	private final Posts mCachedPosts;
	
	private final CacheManager.StreamHolder mHolder = new CacheManager.StreamHolder();
	
	private Posts mPosts;
	private PostItem[] mPostItems;
	
	public static interface Callback
	{
		public void onDeserializePostsComplete(boolean success, Posts posts, PostItem[] postItems, boolean fromCache);
	}
	
	public DeserializePostsTask(Callback callback, String chanName, String boardName, String threadNumber,
			boolean fromCache, Posts cachedPosts)
	{
		mCallback = callback;
		mChanName = chanName;
		mBoardName = boardName;
		mThreadNumber = threadNumber;
		mFromCache = fromCache;
		mCachedPosts = cachedPosts;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		if (mCachedPosts == null)
		{
			mPosts = CacheManager.getInstance().deserializePosts(mChanName, mBoardName, mThreadNumber, mHolder);
		}
		else mPosts = mCachedPosts;
		mPostItems = ReadPostsTask.wrapPosts(mPosts, mChanName, mBoardName);
		return mPostItems != null && mPostItems.length > 0;
	}
	
	@Override
	public void onPostExecute(Boolean success)
	{
		mCallback.onDeserializePostsComplete(success, mPosts, mPostItems, mFromCache);
	}
	
	public void cancel()
	{
		cancel(true);
		if (mHolder != null) mHolder.cancel();
	}
}