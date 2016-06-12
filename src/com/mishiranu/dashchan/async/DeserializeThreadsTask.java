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

import chan.content.model.Threads;

import com.mishiranu.dashchan.content.CacheManager;
import com.mishiranu.dashchan.content.model.PostItem;

public class DeserializeThreadsTask extends CancellableTask<Void, Void, Boolean>
{
	private final Callback mCallback;
	private final String mChanName;
	private final String mBoardName;
	private final Threads mCachedThreads;
	
	private final CacheManager.StreamHolder mHolder = new CacheManager.StreamHolder();
	
	private Threads mThreads;
	private PostItem[][] mPostItems;
	
	public static interface Callback
	{
		public void onDeserializeThreadsComplete(boolean success, Threads threads, PostItem[][] postItems);
	}
	
	public DeserializeThreadsTask(Callback callback, String chanName, String boardName, Threads cachedThreads)
	{
		mCallback = callback;
		mChanName = chanName;
		mBoardName = boardName;
		mCachedThreads = cachedThreads;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		if (mCachedThreads == null)
		{
			mThreads = CacheManager.getInstance().deserializeThreads(mChanName, mBoardName, mHolder);
		}
		else mThreads = mCachedThreads;
		mPostItems = ReadThreadsTask.wrapThreads(mThreads, mChanName, mBoardName);
		return mPostItems != null && mPostItems.length > 0 && mPostItems[0] != null;
	}
	
	@Override
	public void onPostExecute(Boolean success)
	{
		mCallback.onDeserializeThreadsComplete(success, mThreads, mPostItems);
	}
	
	@Override
	public void cancel()
	{
		cancel(true);
		if (mHolder != null) mHolder.cancel();
	}
}