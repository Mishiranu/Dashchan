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

import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.content.model.ThreadSummary;
import chan.http.HttpException;
import chan.http.HttpHolder;

import com.mishiranu.dashchan.content.model.ErrorItem;

public class ReadThreadSummariesTask extends CancellableTask<Void, Long, Boolean>
{
	private final String mChanName;
	private final String mBoardName;
	private final int mType;
	private final Callback mCallback;
	private final HttpHolder mHolder = new HttpHolder();
	
	private ThreadSummary[] mThreadSummaries;
	private ErrorItem mErrorItem;
	
	public static interface Callback
	{
		public void onReadThreadSummariesSuccess(ThreadSummary[] threadSummaries);
		public void onReadThreadSummariesFail(ErrorItem errorItem);
	}
	
	public ReadThreadSummariesTask(String chanName, String boardName, int type, Callback callback)
	{
		mChanName = chanName;
		mBoardName = boardName;
		mType = type;
		mCallback = callback;
	}
	
	@Override
	protected Boolean doInBackground(Void... params)
	{
		try
		{
			ChanPerformer performer = ChanPerformer.get(mChanName);
			ThreadSummary[] threadSummaries;
			try
			{
				ChanPerformer.ReadThreadSummariesResult result = performer.onReadThreadSummaries(new ChanPerformer
						.ReadThreadSummariesData(mBoardName, mType, mHolder));
				threadSummaries = result != null ? result.threadSummaries : null;
			}
			catch (LinkageError | RuntimeException e)
			{
				mErrorItem = ExtensionException.obtainErrorItemAndLogException(e);
				return false;
			}
			if (threadSummaries != null && threadSummaries.length > 0)
			{
				mThreadSummaries = threadSummaries;
			}
			return true;
		}
		catch (HttpException | InvalidResponseException e)
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
			if (mThreadSummaries != null) mCallback.onReadThreadSummariesSuccess(mThreadSummaries);
			else mCallback.onReadThreadSummariesFail(new ErrorItem(ErrorItem.TYPE_EMPTY_RESPONSE));
		}
		else mCallback.onReadThreadSummariesFail(mErrorItem);
	}
	
	@Override
	public void cancel()
	{
		cancel(true);
		mHolder.interrupt();
	}
}