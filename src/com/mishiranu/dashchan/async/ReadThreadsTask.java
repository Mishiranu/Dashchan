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

import android.os.AsyncTask;

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.Posts;
import chan.content.model.Threads;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpValidator;

import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.net.YouTubeTitlesReader;

public class ReadThreadsTask extends AsyncTask<Void, Void, Boolean>
{
	private final Callback mCallback;
	private final String mChanName;
	private final String mBoardName;
	private final Threads mCachedThreads;
	private final HttpValidator mValidator;
	private final int mPageNumber;
	private final boolean mAppend;
	
	private final HttpHolder mHolder = new HttpHolder();
	
	private Threads mThreads;
	private PostItem[][] mPostItems;
	private HttpValidator mResultValidator;
	private ErrorItem mErrorItem;
	
	public static interface Callback
	{
		public void onReadThreadsSuccess(Threads threads, PostItem[][] postItems, int pageNumber,
				boolean append, boolean checkModified, HttpValidator validator);
		public void onReadThreadsFail(ErrorItem errorItem, int pageNumber);
	}
	
	public ReadThreadsTask(Callback callback, String chanName, String boardName, Threads cachedThreads,
			HttpValidator validator, int pageNumber, boolean append)
	{
		mCallback = callback;
		mChanName = chanName;
		mBoardName = boardName;
		mCachedThreads = cachedThreads;
		mValidator = validator;
		mPageNumber = pageNumber;
		mAppend = append;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		try
		{
			ChanPerformer performer = ChanPerformer.get(mChanName);
			Threads threads;
			HttpValidator validator;
			try
			{
				ChanPerformer.ReadThreadsResult result = performer.onReadThreads(new ChanPerformer.ReadThreadsData
						(mBoardName, mPageNumber, mHolder, mValidator));
				threads = result != null ? result.threads : null;
				validator = result != null ? result.validator : null;
			}
			catch (LinkageError | RuntimeException e)
			{
				mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
				return false;
			}
			if (threads != null)
			{
				threads.setStartPage(mPageNumber);
				threads.removeEmpty();
				if (mAppend) threads.removeRepeats(mCachedThreads);
				Posts[][] threadsArray = threads.getThreads();
				if (threadsArray == null || threadsArray.length == 1 && (threadsArray[0] == null
						|| threadsArray[0].length == 0))
				{
					threads = null;
				}
			}
			if (validator == null) validator = mHolder.getValidator();
			mThreads = threads;
			mResultValidator = validator;
			YouTubeTitlesReader.getInstance().readAndApplyIfNecessary(mThreads, mHolder);
			mPostItems = wrapThreads(mThreads, mChanName, mBoardName);
			return true;
		}
		catch (HttpException e)
		{
			if (e.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) return true;
			if ((mPageNumber == 0 || mPageNumber == ChanPerformer.ReadThreadsData.PAGE_NUMBER_CATALOG) &&
					e.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
			{
				mErrorItem = new ErrorItem(ErrorItem.TYPE_BOARD_NOT_EXISTS);
			}
			else mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		catch (InvalidResponseException e)
		{
			mErrorItem = e.getErrorItemAndHandle();
			return false;
		}
		finally
		{
			ChanConfiguration.get(mChanName).commit();
		}
	}
	
	@Override
	public void onPostExecute(Boolean success)
	{
		if (success)
		{
			mCallback.onReadThreadsSuccess(mThreads, mPostItems, mPageNumber, mAppend,
					mValidator != null, mResultValidator);
		}
		else
		{
			mCallback.onReadThreadsFail(mErrorItem, mPageNumber);
		}
	}
	
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
	
	static PostItem[][] wrapThreads(Threads threads, String chanName, String boardName)
	{
		if (threads == null) return new PostItem[1][];
		Posts[][] threadsArray = threads.getThreads();
		PostItem[][] postItems = new PostItem[threadsArray.length][];
		Thread thread = Thread.currentThread();
		for (int i = 0; i < threadsArray.length && !thread.isInterrupted(); i++)
		{
			Posts[] page = threadsArray[i];
			if (page != null)
			{
				postItems[i] = new PostItem[page.length];
				for (int j = 0; j < page.length && !thread.isInterrupted(); j++)
				{
					postItems[i][j] = new PostItem(page[j], chanName, boardName);
				}
			}
		}
		return postItems;
	}
}