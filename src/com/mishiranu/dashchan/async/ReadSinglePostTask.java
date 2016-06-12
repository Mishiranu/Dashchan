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

import java.net.HttpURLConnection;
import java.util.Collections;

import android.os.AsyncTask;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.net.YouTubeTitlesReader;

public class ReadSinglePostTask extends AsyncTask<Void, Void, PostItem>
{
	private final Callback mCallback;
	private final String mBoardName;
	private final String mChanName;
	private final String mPostNumber;
	
	private final HttpHolder mHolder = new HttpHolder();
	private ErrorItem mErrorItem;
	
	public static interface Callback
	{
		public void onReadSinglePostSuccess(PostItem postItem);
		public void onReadSinglePostFail(ErrorItem errorItem);
	}
	
	public ReadSinglePostTask(Callback callback, String chanName, String boardName, String postNumber)
	{
		mCallback = callback;
		mBoardName = boardName;
		mChanName = chanName;
		mPostNumber = postNumber;
	}
	
	@Override
	protected PostItem doInBackground(Void... params)
	{
		long startTime = System.currentTimeMillis();
		try
		{
			ChanPerformer performer = ChanPerformer.get(mChanName);
			Post post;
			try
			{
				ChanPerformer.ReadSinglePostResult result = performer.onReadSinglePost(new ChanPerformer
						.ReadSinglePostData(mBoardName, mPostNumber, mHolder));
				post = result != null ? result.post : null;
			}
			catch (LinkageError | RuntimeException e)
			{
				mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
				return null;
			}
			YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(Collections.singletonList(post), mHolder);
			return new PostItem(post, mChanName, mBoardName);
		}
		catch (Exception e)
		{
			if (e instanceof HttpException)
			{
				mErrorItem = ((HttpException) e).getErrorItemAndHandle();
				if (mErrorItem.httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND)
				{
					mErrorItem = new ErrorItem(ErrorItem.TYPE_POST_NOT_FOUND);
				}
			}
			else if (e instanceof InvalidResponseException)
			{
				mErrorItem = ((InvalidResponseException) e).getErrorItemAndHandle();
			}
			CommonUtils.sleepMaxTime(startTime, 500);
			return null;
		}
		finally
		{
			ChanConfiguration.get(mChanName).commit();
		}
	}
	
	@Override
	protected void onPostExecute(PostItem result)
	{
		if (result != null) mCallback.onReadSinglePostSuccess(result);
		else mCallback.onReadSinglePostFail(mErrorItem);
	}
	
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
}